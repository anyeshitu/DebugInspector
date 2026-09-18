package com.allynav.debug.inspector.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
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
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import com.allynav.debug.inspector.core.DatabaseInspector;
import com.allynav.debug.inspector.core.InspectorCore;
import com.allynav.debug.inspector.core.InspectorRepository;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.LinkedHashSet;

public final class InspectorActivity extends InspectorBaseActivity {
    private static final String FEATURE_PREFIX = "com.allynav.debug.inspector.feature.";
    private enum Page { HTTP, WEB_SOCKET, SERIAL, DATABASE }

    private final Handler handler = new Handler();
    private final Runnable searchRefresh = this::refresh;
    private final EnumSet<Page> enabledPages = EnumSet.noneOf(Page.class);
    private Page page;
    private UiRowAdapter adapter;
    private EditText search;
    private Spinner filter;
    private TextView empty;
    private Button pause;
    private Button exclude;
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
        exclude = findViewById(R.id.inspector_exclude);
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
        exclude.setOnClickListener(v -> showExcludeDialog());
        findViewById(R.id.inspector_clear).setOnClickListener(v -> confirmClear());
        filter.setOnItemSelectedListener(new SimpleItemSelectedListener(this::refresh));
        configureFeatures();
        if (enabledPages.isEmpty()) {
            showNoFeatures();
        } else {
            select(enabledPages.iterator().next());
        }
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

    private void configureFeatures() {
        Bundle metadata = null;
        try {
            ApplicationInfo info = getPackageManager().getApplicationInfo(
                    getPackageName(), PackageManager.GET_META_DATA);
            metadata = info.metaData;
        } catch (PackageManager.NameNotFoundException ignored) {
            // The running package always exists; keep all features disabled if package metadata is unavailable.
        }
        setFeature(Page.HTTP, R.id.tab_http, metadata, "HTTP");
        setFeature(Page.WEB_SOCKET, R.id.tab_websocket, metadata, "WEBSOCKET");
        setFeature(Page.SERIAL, R.id.tab_serial, metadata, "SERIAL");
        setFeature(Page.DATABASE, R.id.tab_database, metadata, "DATABASE");
        // HTTP-only 等单功能接入不显示孤立标签栏；只有多个页面时才需要导航。
        findViewById(R.id.inspector_tabs).setVisibility(enabledPages.size() > 1 ? View.VISIBLE : View.GONE);
    }

    private void setFeature(Page feature, int tabId, Bundle metadata, String key) {
        boolean enabled = metadata != null && metadata.getBoolean(FEATURE_PREFIX + key, false);
        findViewById(tabId).setVisibility(enabled ? View.VISIBLE : View.GONE);
        if (enabled) enabledPages.add(feature);
    }

    private void showNoFeatures() {
        page = null;
        search.setVisibility(View.GONE);
        filter.setVisibility(View.GONE);
        exclude.setVisibility(View.GONE);
        findViewById(R.id.inspector_refresh).setVisibility(View.GONE);
        adapter.replace(new ArrayList<UiRow>());
        empty.setText(R.string.inspector_no_features);
    }

    private void select(Page value) {
        if (value == null || !enabledPages.contains(value)) return;
        page = value;
        search.setHint(value == Page.DATABASE ? R.string.inspector_database : R.string.inspector_search_hint);
        exclude.setVisibility(value == Page.HTTP ? View.VISIBLE : View.GONE);
        String[] options;
        if (value == Page.HTTP) options = new String[]{getString(R.string.inspector_http_filter_all), "2xx", "4xx/5xx", getString(R.string.inspector_http_filter_errors)};
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
        if (page == null) {
            showNoFeatures();
            return;
        }
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
                if (isExcluded(record.url)) continue;
                if (filterIndex == 1 && (record.statusCode < 200 || record.statusCode >= 300)) continue;
                // 4xx/5xx 只匹配标准客户端和服务端错误状态，避免把未知状态混入错误筛选。
                if (filterIndex == 2 && (record.statusCode < 400 || record.statusCode > 599)) continue;
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

    private boolean isExcluded(String url) {
        String target = url == null ? "" : url.toLowerCase(Locale.US);
        for (String keyword : excludedKeywords()) {
            if (!keyword.isEmpty() && target.contains(keyword.toLowerCase(Locale.US))) return true;
        }
        return false;
    }

    private Set<String> excludedKeywords() {
        Set<String> result = new LinkedHashSet<>();
        String stored = getPreferences(MODE_PRIVATE).getString("http_exclude_keywords", "");
        if (stored == null) return result;
        for (String value : stored.split("\\n")) {
            String keyword = value.trim();
            if (!keyword.isEmpty()) result.add(keyword);
        }
        return result;
    }

    private void showExcludeDialog() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(8), dp(20), 0);
        TextView tip = new TextView(this);
        tip.setText(R.string.inspector_exclude_remove);
        tip.setTextColor(getResources().getColor(R.color.inspector_muted));
        panel.addView(tip);
        LinearLayout keywords = new LinearLayout(this);
        keywords.setOrientation(LinearLayout.VERTICAL);
        panel.addView(keywords);
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint(R.string.inspector_exclude_hint);
        panel.addView(input);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.inspector_exclude_title)
                .setView(panel)
                .setNegativeButton(R.string.inspector_cancel, null)
                .setPositiveButton(R.string.inspector_exclude_add, null)
                .create();
        final Runnable[] rebuildHolder = new Runnable[1];
        rebuildHolder[0] = () -> {
            keywords.removeAllViews();
            Set<String> current = excludedKeywords();
            if (current.isEmpty()) {
                TextView emptyView = new TextView(this);
                emptyView.setText(R.string.inspector_exclude_empty);
                emptyView.setTextColor(getResources().getColor(R.color.inspector_muted));
                keywords.addView(emptyView);
            } else {
                for (String keyword : current) {
                    TextView item = new TextView(this);
                    item.setText("×  " + keyword);
                    item.setTextSize(15);
                    item.setTextColor(getResources().getColor(R.color.inspector_primary));
                    item.setPadding(0, dp(8), 0, dp(8));
                    item.setOnClickListener(v -> {
                        Set<String> updated = excludedKeywords();
                        updated.remove(keyword);
                        saveExcludedKeywords(updated);
                        rebuildHolder[0].run();
                        refresh();
                    });
                    keywords.addView(item);
                }
            }
        };
        dialog.setOnShowListener(ignored -> {
            rebuildHolder[0].run();
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String keyword = input.getText().toString().trim();
                if (!keyword.isEmpty()) {
                    Set<String> updated = excludedKeywords();
                    updated.add(keyword);
                    saveExcludedKeywords(updated);
                    input.setText("");
                    rebuildHolder[0].run();
                    refresh();
                }
            });
        });
        dialog.show();
    }

    private void saveExcludedKeywords(Set<String> values) {
        getPreferences(MODE_PRIVATE).edit()
                .putString("http_exclude_keywords", joinKeywords(values)).apply();
    }

    private static String joinKeywords(Set<String> values) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (result.length() > 0) result.append('\n');
            result.append(value);
        }
        return result.toString();
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
    private int dp(int value) {
        // 弹窗内间距统一按屏幕密度换算，避免高密度设备上的关键字列表过于拥挤。
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
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
