package com.allynav.debug.inspector.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.allynav.debug.inspector.api.BodyData;
import com.allynav.debug.inspector.core.HttpExportFormatter;
import com.allynav.debug.inspector.core.InspectorCore;
import com.allynav.debug.inspector.core.InspectorRepository;

import org.json.JSONArray;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class InspectorDetailActivity extends InspectorBaseActivity {
    private static final String EXTRA_KIND = "kind";
    private static final String EXTRA_ID = "id";
    private static final String EXTRA_TITLE = "title";
    private static final String EXTRA_DETAIL = "detail";

    private static final int TAB_OVERVIEW = 0;
    private static final int TAB_REQUEST = 1;
    private static final int TAB_RESPONSE = 2;

    private Spinner format;
    private LinearLayout content;
    private InspectorRepository.HttpRecord httpRecord;
    private String plainDetail = "";
    private int selectedTab = TAB_OVERVIEW;

    static Intent intent(Context context, UiRow row) {
        return new Intent(context, InspectorDetailActivity.class)
                .putExtra(EXTRA_KIND, row.kind).putExtra(EXTRA_ID, row.id)
                .putExtra(EXTRA_TITLE, row.title).putExtra(EXTRA_DETAIL, row.detail);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.inspector_detail_activity);
        content = findViewById(R.id.detail_content);
        format = findViewById(R.id.detail_format);
        ((TextView) findViewById(R.id.detail_title)).setText(getIntent().getStringExtra(EXTRA_TITLE));
        findViewById(R.id.detail_back).setOnClickListener(v -> finish());
        findViewById(R.id.detail_copy).setOnClickListener(v -> copy(exportText()));
        findViewById(R.id.detail_share).setOnClickListener(v -> share(exportText()));
        findViewById(R.id.detail_tab_overview).setOnClickListener(v -> selectTab(TAB_OVERVIEW));
        findViewById(R.id.detail_tab_request).setOnClickListener(v -> selectTab(TAB_REQUEST));
        findViewById(R.id.detail_tab_response).setOnClickListener(v -> selectTab(TAB_RESPONSE));

        if (getIntent().getIntExtra(EXTRA_KIND, 0) == UiRow.HTTP) {
            String[] formats = {getString(R.string.inspector_format_text), getString(R.string.inspector_format_curl),
                    getString(R.string.inspector_format_json), getString(R.string.inspector_format_har)};
            format.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, formats));
            selectTab(TAB_OVERVIEW);
            loadHttp(getIntent().getStringExtra(EXTRA_ID));
        } else {
            findViewById(R.id.detail_tabs).setVisibility(View.GONE);
            findViewById(R.id.detail_format_row).setVisibility(View.GONE);
            plainDetail = getIntent().getStringExtra(EXTRA_DETAIL);
            renderPlain(plainDetail);
        }
    }

    private void selectTab(int tab) {
        selectedTab = tab;
        setTabState(R.id.detail_tab_overview, tab == TAB_OVERVIEW);
        setTabState(R.id.detail_tab_request, tab == TAB_REQUEST);
        setTabState(R.id.detail_tab_response, tab == TAB_RESPONSE);
        render();
    }

    private void setTabState(int id, boolean selected) {
        TextView view = findViewById(id);
        view.setSelected(selected);
        view.setTextColor(getResources().getColor(selected ? R.color.inspector_primary : R.color.inspector_text_secondary));
        view.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
    }

    private void loadHttp(String id) {
        content.removeAllViews();
        TextView loading = textView(getString(R.string.inspector_loading), 14, R.color.inspector_muted, false);
        loading.setPadding(dp(16), dp(24), dp(16), dp(24));
        content.addView(loading);
        new AsyncTask<Void, Void, InspectorRepository.HttpRecord>() {
            @Override protected InspectorRepository.HttpRecord doInBackground(Void... ignored) {
                return InspectorCore.repository().getHttp(id);
            }
            @Override protected void onPostExecute(InspectorRepository.HttpRecord result) {
                httpRecord = result;
                render();
            }
        }.execute();
    }

    private void render() {
        if (httpRecord == null) return;
        content.removeAllViews();
        if (selectedTab == TAB_REQUEST) {
            addSection(getString(R.string.inspector_request_headers), pretty(httpRecord.requestHeadersJson));
            addBody(getString(R.string.inspector_request_encrypted), httpRecord.requestRawBody);
            addBody(getString(R.string.inspector_request_plaintext), httpRecord.requestPlainBody);
        } else if (selectedTab == TAB_RESPONSE) {
            addSection(getString(R.string.inspector_response_headers), pretty(httpRecord.responseHeadersJson));
            addBody(getString(R.string.inspector_response_encrypted), httpRecord.responseRawBody);
            addBody(getString(R.string.inspector_response_plaintext), httpRecord.responsePlainBody);
            if (httpRecord.error != null) addSection(getString(R.string.inspector_error), httpRecord.error);
        } else {
            addHeading(getString(R.string.inspector_overview));
            addKeyValue(getString(R.string.inspector_url), httpRecord.url);
            addKeyValue(getString(R.string.inspector_method), httpRecord.method);
            addKeyValue(getString(R.string.inspector_status), httpRecord.error == null ? String.valueOf(httpRecord.statusCode) : "ERR");
            addKeyValue(getString(R.string.inspector_protocol), value(httpRecord.protocol));
            addKeyValue(getString(R.string.inspector_started), new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(new Date(httpRecord.startedAtMillis)));
            addKeyValue(getString(R.string.inspector_duration), httpRecord.durationMillis + " ms");
            addKeyValue(getString(R.string.inspector_session), value(httpRecord.sessionId));
            addKeyValue(getString(R.string.inspector_correlation), value(httpRecord.correlationId));
            if (httpRecord.error != null) addKeyValue(getString(R.string.inspector_error), httpRecord.error);
        }
    }

    private void addBody(String title, BodyData body) {
        if (body == null) {
            addSection(title, getString(R.string.inspector_no_body));
            return;
        }
        StringBuilder metadata = new StringBuilder();
        metadata.append(value(body.getContentType())).append("  |  ")
                .append(body.getOriginalLength()).append(" B  |  ").append(value(body.getCharsetName()));
        if (body.isTruncated()) metadata.append("  |  ").append(getString(R.string.inspector_truncated));
        if (body.getTransformError() != null) metadata.append("  |  ").append(body.getTransformError());
        addHeading(title);
        TextView meta = textView(metadata.toString(), 12, R.color.inspector_muted, false);
        meta.setPadding(dp(16), 0, dp(16), dp(8));
        content.addView(meta);
        addPayload(body.asText());
    }

    private void addSection(String title, String body) {
        addHeading(title);
        addPayload(body);
    }

    private void addHeading(String title) {
        TextView view = textView(title, 14, R.color.inspector_primary, true);
        view.setPadding(dp(16), dp(18), dp(16), dp(8));
        content.addView(view);
    }

    private void addPayload(String body) {
        TextView view = textView(body == null ? "" : body, 13, R.color.inspector_text, false);
        view.setTypeface(Typeface.MONOSPACE);
        view.setTextIsSelectable(true);
        view.setLineSpacing(0, 1.15f);
        view.setPadding(dp(16), dp(8), dp(16), dp(16));
        content.addView(view);
        addDivider();
    }

    private void addKeyValue(String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(16), dp(8), dp(16), dp(8));
        TextView key = textView(label, 13, R.color.inspector_text_secondary, false);
        TextView data = textView(value, 13, R.color.inspector_text, false);
        data.setTextIsSelectable(true);
        row.addView(key, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.28f));
        row.addView(data, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.72f));
        content.addView(row);
        addDivider();
    }

    private void addDivider() {
        View divider = new View(this);
        divider.setBackgroundColor(getResources().getColor(R.color.inspector_divider));
        content.addView(divider, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
    }

    private void renderPlain(String text) {
        content.removeAllViews();
        addPayload(text == null ? "" : text);
    }

    private TextView textView(String text, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(getResources().getColor(color));
        if (bold) view.setTypeface(null, Typeface.BOLD);
        return view;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private String exportText() {
        if (httpRecord == null) return plainDetail == null ? "" : plainDetail;
        HttpExportFormatter.Format selected;
        switch (format.getSelectedItemPosition()) {
            case 1: selected = HttpExportFormatter.Format.CURL; break;
            case 2: selected = HttpExportFormatter.Format.JSON; break;
            case 3: selected = HttpExportFormatter.Format.HAR; break;
            default: return localizedHttpText(httpRecord);
        }
        return HttpExportFormatter.format(httpRecord, selected);
    }

    private String localizedHttpText(InspectorRepository.HttpRecord record) {
        StringBuilder builder = new StringBuilder();
        builder.append(record.method).append(' ').append(record.url).append('\n');
        builder.append(getString(R.string.inspector_status)).append(": ").append(record.statusCode).append('\n');
        builder.append(getString(R.string.inspector_duration)).append(": ").append(record.durationMillis).append(" ms\n\n");
        section(builder, getString(R.string.inspector_request_headers), pretty(record.requestHeadersJson));
        bodySection(builder, getString(R.string.inspector_request_encrypted), record.requestRawBody);
        bodySection(builder, getString(R.string.inspector_request_plaintext), record.requestPlainBody);
        section(builder, getString(R.string.inspector_response_headers), pretty(record.responseHeadersJson));
        bodySection(builder, getString(R.string.inspector_response_encrypted), record.responseRawBody);
        bodySection(builder, getString(R.string.inspector_response_plaintext), record.responsePlainBody);
        if (record.error != null) section(builder, getString(R.string.inspector_error), record.error);
        return builder.toString();
    }

    private void bodySection(StringBuilder builder, String title, BodyData body) {
        if (body == null) return;
        if (body.isTruncated()) title += " [" + getString(R.string.inspector_truncated) + "]";
        if (body.getTransformError() != null) title += " [" + body.getTransformError() + "]";
        section(builder, title, body.asText());
    }

    private static void section(StringBuilder builder, String title, String value) {
        builder.append("==== ").append(title).append(" ====\n").append(value == null ? "" : value).append("\n\n");
    }

    private static String pretty(String value) {
        try { return new JSONArray(value == null ? "[]" : value).toString(2); }
        catch (Exception ignored) { return value == null ? "" : value; }
    }

    private static String value(String value) {
        return value == null || value.isEmpty() ? "-" : value;
    }

    private void copy(String value) {
        ClipboardManager manager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        manager.setPrimaryClip(ClipData.newPlainText(getString(R.string.inspector_name), value));
        Toast.makeText(this, R.string.inspector_copied, Toast.LENGTH_SHORT).show();
    }

    private void share(String value) {
        Intent intent = new Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, getIntent().getStringExtra(EXTRA_TITLE))
                .putExtra(Intent.EXTRA_TEXT, value);
        startActivity(Intent.createChooser(intent, getString(R.string.inspector_share_title)));
    }
}
