package com.allynav.debug.inspector.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import com.allynav.debug.inspector.core.DatabaseInspector;
import com.allynav.debug.inspector.core.InspectorCore;
import com.allynav.debug.inspector.core.InspectorRepository;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class InspectorActivity extends InspectorBaseActivity {
    private enum Page { HTTP, WEB_SOCKET, SERIAL, DATABASE }

    private final Handler handler = new Handler();
    private final Runnable searchRefresh = this::refresh;
    private Page page = Page.HTTP;
    private UiRowAdapter adapter;
    private EditText search;
    private Spinner filter;
    private TextView empty;
    private Button pause;
    private int loadGeneration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.inspector_activity);
        adapter = new UiRowAdapter(this);
        ListView list = findViewById(android.R.id.list);
        empty = findViewById(android.R.id.empty);
        list.setEmptyView(empty);
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> open(adapter.getItem(position)));

        search = findViewById(R.id.inspector_search);
        filter = findViewById(R.id.inspector_filter);
        pause = findViewById(R.id.inspector_pause);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                handler.removeCallbacks(searchRefresh);
                handler.postDelayed(searchRefresh, 250);
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        findViewById(R.id.tab_http).setOnClickListener(v -> select(Page.HTTP));
        findViewById(R.id.tab_websocket).setOnClickListener(v -> select(Page.WEB_SOCKET));
        findViewById(R.id.tab_serial).setOnClickListener(v -> select(Page.SERIAL));
        findViewById(R.id.tab_database).setOnClickListener(v -> select(Page.DATABASE));
        findViewById(R.id.inspector_refresh).setOnClickListener(v -> refresh());
        pause.setOnClickListener(v -> togglePause());
        findViewById(R.id.inspector_clear).setOnClickListener(v -> confirmClear());
        filter.setOnItemSelectedListener(new SimpleItemSelectedListener(this::refresh));
        select(Page.HTTP);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePause();
        refresh();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(searchRefresh);
        super.onDestroy();
    }

    private void select(Page value) {
        page = value;
        search.setHint(value == Page.DATABASE ? R.string.inspector_database : R.string.inspector_search_hint);
        String[] options;
        if (value == Page.HTTP) options = new String[]{getString(R.string.inspector_all), "2xx", "4xx/5xx", getString(R.string.inspector_error)};
        else if (value == Page.WEB_SOCKET) options = new String[]{getString(R.string.inspector_all), getString(R.string.inspector_request), getString(R.string.inspector_response), getString(R.string.inspector_error)};
        else if (value == Page.SERIAL) options = new String[]{getString(R.string.inspector_all), "TX", "RX", "ERROR"};
        else options = new String[]{getString(R.string.inspector_all)};
        filter.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, options));
        setTabSelected(R.id.tab_http, value == Page.HTTP);
        setTabSelected(R.id.tab_websocket, value == Page.WEB_SOCKET);
        setTabSelected(R.id.tab_serial, value == Page.SERIAL);
        setTabSelected(R.id.tab_database, value == Page.DATABASE);
        refresh();
    }

    private void setTabSelected(int id, boolean selected) {
        TextView tab = findViewById(id);
        tab.setSelected(selected);
        tab.setTextColor(getResources().getColor(selected ? R.color.inspector_primary : R.color.inspector_text_secondary));
        tab.setTypeface(null, selected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
    }

    private void refresh() {
        if (!InspectorCore.isInitialized()) {
            empty.setText(R.string.inspector_open_failed);
            return;
        }
        final int generation = ++loadGeneration;
        final Page requested = page;
        final String query = search.getText().toString();
        final int filterIndex = filter.getSelectedItemPosition();
        empty.setText(R.string.inspector_loading);
        adapter.replace(new ArrayList<UiRow>());
        new AsyncTask<Void, Void, LoadResult>() {
            @Override protected LoadResult doInBackground(Void... ignored) {
                try { return new LoadResult(load(requested, query, filterIndex), null); }
                catch (RuntimeException error) { return new LoadResult(new ArrayList<UiRow>(), error.toString()); }
            }
            @Override protected void onPostExecute(LoadResult result) {
                if (generation != loadGeneration || isFinishing()) return;
                adapter.replace(result.rows);
                if (result.error == null) empty.setText(R.string.inspector_empty);
                else empty.setText(result.error);
            }
        }.execute();
    }

    private List<UiRow> load(Page requested, String query, int filterIndex) {
        List<UiRow> rows = new ArrayList<>();
        InspectorRepository repository = InspectorCore.repository();
        if (requested == Page.HTTP) {
            for (InspectorRepository.HttpRecord record : repository.listHttp(query, 200)) {
                if (filterIndex == 1 && (record.statusCode < 200 || record.statusCode >= 300)) continue;
                if (filterIndex == 2 && record.statusCode < 400) continue;
                if (filterIndex == 3 && record.error == null) continue;
                Uri uri = Uri.parse(record.url == null ? "" : record.url);
                String path = uri.getEncodedPath();
                if (path == null || path.isEmpty()) path = "/";
                if (uri.getEncodedQuery() != null) path += "?" + uri.getEncodedQuery();
                int bytes = bodySize(record.responseRawBody);
                rows.add(new UiRow(UiRow.HTTP, record.id, record.method + "  " + path, uri.getHost(),
                        time(record.startedAtMillis) + "   " + record.durationMillis + " ms" + size(bytes, false),
                        null, null, status(record), httpTone(record)));
            }
        } else if (requested == Page.WEB_SOCKET) {
            for (InspectorRepository.WebSocketRecord record : repository.listWebSocket(query, 300)) {
                boolean sent = "SENT".equals(record.direction);
                boolean received = "RECEIVED".equals(record.direction);
                if (filterIndex == 1 && !sent) continue;
                if (filterIndex == 2 && !received) continue;
                if (filterIndex == 3 && !"FAILURE".equals(record.type)) continue;
                String detail = record.payloadText + (record.detail == null ? "" : "\n" + record.detail);
                String badge = sent ? "TX" : received ? "RX" : "WS";
                int tone = "FAILURE".equals(record.type) ? UiRow.TONE_ERROR : sent ? UiRow.TONE_INFO : received ? UiRow.TONE_SUCCESS : UiRow.TONE_IDLE;
                rows.add(new UiRow(UiRow.WEB_SOCKET, record.id, webSocketType(record.type),
                        record.connectionId, time(record.timestampMillis) + size(record.originalSize, record.truncated), detail, null, badge, tone));
            }
        } else if (requested == Page.SERIAL) {
            for (InspectorRepository.SerialRecord record : repository.listSerial(query, 300)) {
                if (filterIndex > 0) {
                    String expected = filterIndex == 1 ? "TX" : filterIndex == 2 ? "RX" : "ERROR";
                    if (!expected.equals(record.direction)) continue;
                }
                String detail = "TEXT\n" + safe(record.payloadText) + "\n\nHEX\n" + safe(record.payloadHex);
                if (record.error != null) detail += "\n\nERROR\n" + record.error;
                int tone = "ERROR".equals(record.direction) ? UiRow.TONE_ERROR : "TX".equals(record.direction) ? UiRow.TONE_INFO : UiRow.TONE_SUCCESS;
                rows.add(new UiRow(UiRow.SERIAL, record.id, record.portId,
                        firstLine(record.payloadText), time(record.timestampMillis) + size(record.originalSize, record.truncated), detail, null,
                        record.direction, tone));
            }
        } else {
            String lower = query.toLowerCase(Locale.US);
            for (DatabaseInspector.DatabaseFile database : InspectorCore.databaseInspector().databases()) {
                if (!lower.isEmpty() && !(database.name + database.path).toLowerCase(Locale.US).contains(lower)) continue;
                rows.add(new UiRow(UiRow.DATABASE, database.path, database.name, database.path,
                        database.discovered ? getString(R.string.inspector_discovered) : getString(R.string.inspector_registered),
                        null, database.path, "DB", UiRow.TONE_INFO));
            }
        }
        return rows;
    }

    private void open(UiRow row) {
        if (row.kind == UiRow.DATABASE) {
            startActivity(DatabaseTablesActivity.intent(this, row.title, row.path));
        } else {
            startActivity(InspectorDetailActivity.intent(this, row));
        }
    }

    private void togglePause() {
        if (InspectorCore.isPaused()) InspectorCore.resume(); else InspectorCore.pause();
        updatePause();
    }

    private void updatePause() {
        pause.setText(InspectorCore.isPaused() ? R.string.inspector_resume : R.string.inspector_pause);
    }

    private void confirmClear() {
        new AlertDialog.Builder(this).setMessage(R.string.inspector_confirm_clear)
                .setNegativeButton(R.string.inspector_cancel, null)
                .setPositiveButton(R.string.inspector_confirm, (dialog, which) -> {
                    InspectorCore.clear();
                    handler.postDelayed(this::refresh, 250);
                }).show();
    }

    private String status(InspectorRepository.HttpRecord record) {
        return record.error == null ? String.valueOf(record.statusCode) : "ERR";
    }

    private static int httpTone(InspectorRepository.HttpRecord record) {
        if (record.error != null || record.statusCode >= 500) return UiRow.TONE_ERROR;
        if (record.statusCode >= 400) return UiRow.TONE_WARNING;
        if (record.statusCode >= 300) return UiRow.TONE_INFO;
        if (record.statusCode >= 200) return UiRow.TONE_SUCCESS;
        return UiRow.TONE_IDLE;
    }

    private static int bodySize(com.allynav.debug.inspector.api.BodyData body) {
        return body == null ? 0 : (int) Math.min(Integer.MAX_VALUE, body.getOriginalLength());
    }

    private String size(int value, boolean truncated) {
        return "  " + value + " B" + (truncated ? "  " + getString(R.string.inspector_truncated) : "");
    }

    private static String firstLine(String value) {
        if (value == null) return "";
        int newline = value.indexOf('\n');
        return newline < 0 ? value : value.substring(0, newline);
    }

    private static String safe(String value) { return value == null ? "" : value; }
    private static String time(long millis) { return new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(new Date(millis)); }

    private String webSocketType(String value) {
        if ("CONNECTING".equals(value)) return getString(R.string.inspector_ws_connecting);
        if ("OPEN".equals(value)) return getString(R.string.inspector_ws_open);
        if ("MESSAGE".equals(value)) return getString(R.string.inspector_ws_message);
        if ("CLOSING".equals(value)) return getString(R.string.inspector_ws_closing);
        if ("CLOSED".equals(value)) return getString(R.string.inspector_ws_closed);
        if ("FAILURE".equals(value)) return getString(R.string.inspector_ws_failure);
        return value;
    }

    private String webSocketDirection(String value) {
        if ("SENT".equals(value)) return getString(R.string.inspector_sent);
        if ("RECEIVED".equals(value)) return getString(R.string.inspector_received);
        return "";
    }

    private static final class LoadResult {
        final List<UiRow> rows;
        final String error;
        LoadResult(List<UiRow> rows, String error) { this.rows = rows; this.error = error; }
    }
}
