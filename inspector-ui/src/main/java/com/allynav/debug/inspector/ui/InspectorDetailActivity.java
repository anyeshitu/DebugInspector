package com.allynav.debug.inspector.ui;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.allynav.debug.inspector.api.BodyData;
import com.allynav.debug.inspector.core.InspectorCore;
import com.allynav.debug.inspector.core.InspectorRepository;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
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
    private static final int REQUEST_SAVE_FOLDER = 7201;

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
        ((TextView) findViewById(R.id.detail_title)).setText(getIntent().getStringExtra(EXTRA_TITLE));
        findViewById(R.id.detail_back).setOnClickListener(v -> finish());
        findViewById(R.id.detail_save).setOnClickListener(v -> chooseSaveFolder());
        findViewById(R.id.detail_share).setOnClickListener(v -> shareTextFile());
        findViewById(R.id.detail_tab_overview).setOnClickListener(v -> selectTab(TAB_OVERVIEW));
        findViewById(R.id.detail_tab_request).setOnClickListener(v -> selectTab(TAB_REQUEST));
        findViewById(R.id.detail_tab_response).setOnClickListener(v -> selectTab(TAB_RESPONSE));
        ((TextView) findViewById(R.id.detail_tab_overview)).setText("OVERVIEW");
        ((TextView) findViewById(R.id.detail_tab_request)).setText("REQUEST");
        ((TextView) findViewById(R.id.detail_tab_response)).setText("RESPONSE");

        if (getIntent().getIntExtra(EXTRA_KIND, 0) == UiRow.HTTP) {
            selectTab(TAB_OVERVIEW);
            loadHttp(getIntent().getStringExtra(EXTRA_ID));
        } else {
            findViewById(R.id.detail_tabs).setVisibility(View.GONE);
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
            addSection(getString(R.string.inspector_request_headers), formatHeaders(httpRecord.requestHeadersJson));
            addBodies(getString(R.string.inspector_request_encrypted),
                    getString(R.string.inspector_request_plaintext), getString(R.string.inspector_request_body),
                    httpRecord.requestRawBody, httpRecord.requestPlainBody);
        } else if (selectedTab == TAB_RESPONSE) {
            addSection(getString(R.string.inspector_response_headers), formatHeaders(httpRecord.responseHeadersJson));
            addBodies(getString(R.string.inspector_response_encrypted),
                    getString(R.string.inspector_response_plaintext), getString(R.string.inspector_response_body),
                    httpRecord.responseRawBody, httpRecord.responsePlainBody);
            if (httpRecord.error != null) addSection(getString(R.string.inspector_error), httpRecord.error);
        } else {
            addKeyValue("URL", httpRecord.url);
            addKeyValue("Method", httpRecord.method);
            addKeyValue("Protocol", value(httpRecord.protocol));
            addKeyValue("Status", httpRecord.error == null ? "Complete" : "Failed");
            addKeyValue("Response", httpRecord.error == null ? statusText(httpRecord.statusCode) : "-");
            addKeyValue("SSL", isHttps(httpRecord) ? "Yes" : "No");
            addKeyValue("Request time", overviewTime(httpRecord.startedAtMillis));
            addKeyValue("Response time", overviewTime(httpRecord.startedAtMillis + httpRecord.durationMillis));
            addKeyValue("Duration", httpRecord.durationMillis + " ms");
            addKeyValue("Request size", bytes(bodySize(httpRecord.requestRawBody)));
            addKeyValue("Response size", bytes(bodySize(httpRecord.responseRawBody)));
            addKeyValue("Total size", bytes(bodySize(httpRecord.requestRawBody) + bodySize(httpRecord.responseRawBody)));
            if (httpRecord.sessionId != null && !httpRecord.sessionId.isEmpty()) addKeyValue(getString(R.string.inspector_session), httpRecord.sessionId);
            if (httpRecord.correlationId != null && !httpRecord.correlationId.isEmpty()) addKeyValue(getString(R.string.inspector_correlation), httpRecord.correlationId);
            if (httpRecord.error != null) addKeyValue(getString(R.string.inspector_error), httpRecord.error);
        }
    }

    private void addBodies(String encryptedTitle, String plaintextTitle, String bodyTitle,
                           BodyData raw, BodyData transformed) {
        if (hasTransformation(raw, transformed)) {
            addBody(encryptedTitle, raw);
            addBody(plaintextTitle, transformed);
        } else {
            addBody(bodyTitle, raw == null ? transformed : raw);
        }
    }

    private static boolean hasTransformation(BodyData raw, BodyData transformed) {
        if (transformed == null || raw == null) return false;
        // 只有内容确实发生变化才拆分展示；未加密响应即使转换器返回错误，也只显示一个正文区块。
        return !java.util.Arrays.equals(raw.getBytes(), transformed.getBytes());
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
        String payload = prettyBody(body.asText());
        addHeadingWithCopy(title, payload, body.asText());
        TextView meta = textView(metadata.toString(), 12, R.color.inspector_muted, false);
        meta.setPadding(dp(16), 0, dp(16), dp(8));
        content.addView(meta);
        addPayload(payload);
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

    private void addHeadingWithCopy(String title, String payload, String rawPayload) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(8), dp(8), 0);
        TextView heading = textView(title, 14, R.color.inspector_primary, true);
        heading.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.addView(heading, new LinearLayout.LayoutParams(0, dp(48), 1));
        ImageButton button = new ImageButton(this, null, 0, R.style.Inspector_IconActionButton);
        button.setImageResource(R.drawable.inspector_ic_content_copy_24);
        button.setContentDescription(getString(R.string.inspector_copy));
        button.setOnClickListener(v -> copyBody(title, payload));
        button.setOnLongClickListener(v -> {
            chooseBodyCopy(title, payload, rawPayload);
            return true;
        });
        row.addView(button, new LinearLayout.LayoutParams(dp(48), dp(48)));
        content.addView(row);
    }

    private void chooseBodyCopy(String title, String formattedPayload, String rawPayload) {
        String[] options = {
                getString(R.string.inspector_format_text),
                getString(R.string.inspector_format_raw)
        };
        new AlertDialog.Builder(this)
                .setTitle(R.string.inspector_choose_copy_format)
                .setItems(options, (dialog, which) -> copyBody(title,
                        which == 1 ? rawPayload : formattedPayload))
                .show();
    }

    private void copyBody(String title, String payload) {
        // 正文单独复制时保留接口上下文，发送给他人后仍能定位对应请求。
        String url = httpRecord == null ? "" : value(httpRecord.url);
        String text = getString(R.string.inspector_copy_url_label) + ": " + url
                + "\n" + title + "\n\n" + (payload == null ? "" : payload);
        copy(text);
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

    private void chooseSaveFolder() {
        if (getIntent().getIntExtra(EXTRA_KIND, 0) == UiRow.HTTP && httpRecord == null) {
            Toast.makeText(this, R.string.inspector_loading, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_SAVE_FOLDER);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_SAVE_FOLDER || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        final Uri folder = data.getData();
        try {
            int takeFlags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            if (takeFlags != 0) getContentResolver().takePersistableUriPermission(folder, takeFlags);
        } catch (SecurityException ignored) {
            // Some providers grant a one-shot tree permission only.
        }
        final String fileName = saveFileName();
        new AlertDialog.Builder(this)
                .setTitle(R.string.inspector_export_confirm_title)
                .setMessage(getString(R.string.inspector_save_confirm_message, fileName))
                .setNegativeButton(R.string.inspector_cancel, null)
                .setPositiveButton(R.string.inspector_confirm, (dialog, which) -> saveToFolder(folder, fileName, detailText()))
                .show();
    }

    private String saveFileName() {
        if (getIntent().getIntExtra(EXTRA_KIND, 0) == UiRow.HTTP) {
            String endpoint = httpRecord == null ? "request" : endpointName(httpRecord.url);
            return "http-" + endpoint + "-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + ".txt";
        }
        return "debug-detail-" + new SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(new Date()) + ".txt";
    }

    private static String endpointName(String url) {
        String endpoint = "";
        try {
            Uri uri = Uri.parse(url == null ? "" : url);
            java.util.List<String> segments = uri.getPathSegments();
            if (!segments.isEmpty()) endpoint = segments.get(segments.size() - 1);
        } catch (Exception ignored) {
            // Fall back to plain string parsing for malformed or partially captured URLs.
        }
        if (endpoint == null || endpoint.isEmpty()) endpoint = endpointNameFromRawUrl(url);
        endpoint = sanitizeFileNamePart(endpoint);
        return endpoint.isEmpty() ? "request" : endpoint;
    }

    private static String endpointNameFromRawUrl(String url) {
        if (url == null) return "";
        int end = url.length();
        int query = url.indexOf('?');
        if (query >= 0 && query < end) end = query;
        int fragment = url.indexOf('#');
        if (fragment >= 0 && fragment < end) end = fragment;
        String value = url.substring(0, end);
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        int slash = value.lastIndexOf('/');
        return slash >= 0 ? value.substring(slash + 1) : value;
    }

    private static String sanitizeFileNamePart(String value) {
        if (value == null) return "";
        StringBuilder builder = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char c = value.charAt(index);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '-' || c == '_' || c == '.') {
                builder.append(c);
            } else {
                builder.append('_');
            }
        }
        return builder.toString();
    }

    private String detailText() {
        if (getIntent().getIntExtra(EXTRA_KIND, 0) == UiRow.HTTP && httpRecord != null) {
            return localizedHttpText(httpRecord);
        }
        return plainDetail == null ? "" : plainDetail;
    }

    private void saveToFolder(Uri folder, String fileName, String value) {
        new SaveTextTask(this, folder, fileName, value).execute();
    }


    private void shareTextFile() {
        if (getIntent().getIntExtra(EXTRA_KIND, 0) == UiRow.HTTP && httpRecord == null) {
            Toast.makeText(this, R.string.inspector_loading, Toast.LENGTH_SHORT).show();
            return;
        }
        new ShareTextFileTask(this, saveFileName(), detailText()).execute();
    }
    private String localizedHttpText(InspectorRepository.HttpRecord record) {
        StringBuilder builder = new StringBuilder();
        section(builder, "Overview", overviewText(record));
        section(builder, "Request", requestText(record));
        section(builder, "Response", responseDetailText(record));
        return builder.toString();
    }

    private String overviewText(InspectorRepository.HttpRecord record) {
        long requestSize = bodySize(record.requestRawBody);
        long responseSize = bodySize(record.responseRawBody);
        StringBuilder builder = new StringBuilder();
        line(builder, "URL", record.url);
        line(builder, "Method", record.method);
        line(builder, "Protocol", value(record.protocol));
        line(builder, "Status", record.error == null ? "Complete" : "Failed");
        line(builder, "Response", record.error == null ? statusText(record.statusCode) : "-");
        line(builder, "SSL", isHttps(record) ? "Yes" : "No");
        line(builder, "Request time", overviewTime(record.startedAtMillis));
        line(builder, "Response time", overviewTime(record.startedAtMillis + record.durationMillis));
        line(builder, "Duration", record.durationMillis + " ms");
        line(builder, "Request size", bytes(requestSize));
        line(builder, "Response size", bytes(responseSize));
        line(builder, "Total size", bytes(requestSize + responseSize));
        if (record.sessionId != null && !record.sessionId.isEmpty()) line(builder, getString(R.string.inspector_session), record.sessionId);
        if (record.correlationId != null && !record.correlationId.isEmpty()) line(builder, getString(R.string.inspector_correlation), record.correlationId);
        if (record.error != null) line(builder, getString(R.string.inspector_error), record.error);
        return builder.toString();
    }

    private String requestText(InspectorRepository.HttpRecord record) {
        StringBuilder builder = new StringBuilder();
        section(builder, getString(R.string.inspector_request_headers), formatHeaders(record.requestHeadersJson));
        bodySections(builder, getString(R.string.inspector_request_encrypted),
                getString(R.string.inspector_request_plaintext), getString(R.string.inspector_request_body),
                record.requestRawBody, record.requestPlainBody);
        return builder.toString();
    }

    private String responseDetailText(InspectorRepository.HttpRecord record) {
        StringBuilder builder = new StringBuilder();
        section(builder, getString(R.string.inspector_response_headers), formatHeaders(record.responseHeadersJson));
        bodySections(builder, getString(R.string.inspector_response_encrypted),
                getString(R.string.inspector_response_plaintext), getString(R.string.inspector_response_body),
                record.responseRawBody, record.responsePlainBody);
        if (record.error != null) section(builder, getString(R.string.inspector_error), record.error);
        return builder.toString();
    }

    private static void line(StringBuilder builder, String label, String value) {
        builder.append(label).append(": ").append(value == null ? "" : value).append('\n');
    }

    private void bodySections(StringBuilder builder, String encryptedTitle, String plaintextTitle,
                              String bodyTitle, BodyData raw, BodyData transformed) {
        if (hasTransformation(raw, transformed)) {
            bodySection(builder, encryptedTitle, raw);
            bodySection(builder, plaintextTitle, transformed);
        } else {
            bodySection(builder, bodyTitle, raw == null ? transformed : raw);
        }
    }

    private void bodySection(StringBuilder builder, String title, BodyData body) {
        if (body == null) return;
        if (body.isTruncated()) title += " [" + getString(R.string.inspector_truncated) + "]";
        if (body.getTransformError() != null) title += " [" + body.getTransformError() + "]";
        section(builder, title, prettyBody(body.asText()));
    }

    private static void section(StringBuilder builder, String title, String value) {
        builder.append("==== ").append(title).append(" ====\n").append(value == null ? "" : value).append("\n\n");
    }

    private static String formatHeaders(String value) {
        if (value == null || value.isEmpty()) return "";
        try {
            JSONArray headers = new JSONArray(value);
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < headers.length(); i++) {
                JSONObject header = headers.getJSONObject(i);
                if (builder.length() > 0) builder.append('\n');
                builder.append(header.optString("name")).append(": ").append(header.optString("value"));
            }
            return builder.toString();
        } catch (Exception ignored) {
            return value;
        }
    }

    private static String prettyBody(String value) {
        if (value == null) return "";
        String trimmed = value.trim();
        try {
            if (trimmed.startsWith("{")) return new JSONObject(trimmed).toString(2);
            if (trimmed.startsWith("[")) return new JSONArray(trimmed).toString(2);
        } catch (Exception ignored) {
            // Keep malformed or truncated JSON exactly as captured.
        }
        return value;
    }

    private static long bodySize(BodyData body) {
        return body == null ? 0L : body.getOriginalLength();
    }

    private static String bytes(long value) {
        return value + " B";
    }

    private static boolean isHttps(InspectorRepository.HttpRecord record) {
        return record.url != null && record.url.startsWith("https://");
    }

    private static String overviewTime(long millis) {
        return new SimpleDateFormat("EEE MMM dd HH:mm:ss 'GMT'XXX yyyy", Locale.ENGLISH).format(new Date(millis));
    }

    private static String statusText(int statusCode) {
        if (statusCode <= 0) return "-";
        String reason = reasonPhrase(statusCode);
        return reason.isEmpty() ? String.valueOf(statusCode) : statusCode + " " + reason;
    }

    private static String reasonPhrase(int statusCode) {
        switch (statusCode) {
            case 100: return "Continue";
            case 101: return "Switching Protocols";
            case 200: return "OK";
            case 201: return "Created";
            case 202: return "Accepted";
            case 204: return "No Content";
            case 301: return "Moved Permanently";
            case 302: return "Found";
            case 304: return "Not Modified";
            case 400: return "Bad Request";
            case 401: return "Unauthorized";
            case 403: return "Forbidden";
            case 404: return "Not Found";
            case 405: return "Method Not Allowed";
            case 408: return "Request Timeout";
            case 409: return "Conflict";
            case 422: return "Unprocessable Entity";
            case 429: return "Too Many Requests";
            case 500: return "Internal Server Error";
            case 502: return "Bad Gateway";
            case 503: return "Service Unavailable";
            case 504: return "Gateway Timeout";
            default: return "";
        }
    }

    private static String value(String value) {
        return value == null || value.isEmpty() ? "-" : value;
    }

    private void copy(String value) {
        ClipboardManager manager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        manager.setPrimaryClip(ClipData.newPlainText(getString(R.string.inspector_name), value));
        Toast.makeText(this, R.string.inspector_copied, Toast.LENGTH_SHORT).show();
    }

    private static final class SaveTextTask extends AsyncTask<Void, Void, Boolean> {
        private final WeakReference<InspectorDetailActivity> activityReference;
        private final ContentResolver contentResolver;
        private final Uri folder;
        private final String fileName;
        private final String value;
        private String error;

        SaveTextTask(InspectorDetailActivity activity, Uri folder, String fileName, String value) {
            activityReference = new WeakReference<>(activity);
            contentResolver = activity.getApplicationContext().getContentResolver();
            this.folder = folder;
            this.fileName = fileName;
            this.value = value;
        }

        @Override protected Boolean doInBackground(Void... ignored) {
            try {
                Uri parent = DocumentsContract.buildDocumentUriUsingTree(folder,
                        DocumentsContract.getTreeDocumentId(folder));
                Uri target = DocumentsContract.createDocument(contentResolver, parent, "text/plain", fileName);
                if (target == null) throw new IllegalStateException("Unable to create export document");
                try (OutputStream output = contentResolver.openOutputStream(target, "w")) {
                    if (output == null) throw new IllegalStateException("Unable to open export document");
                    output.write(value.getBytes(StandardCharsets.UTF_8));
                    output.flush();
                }
                return true;
            } catch (Exception exception) {
                error = exception.toString();
                return false;
            }
        }

        @Override protected void onPostExecute(Boolean success) {
            InspectorDetailActivity activity = activityReference.get();
            if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
            if (!success) {
                Toast.makeText(activity, activity.getString(R.string.inspector_export_failed, error),
                        Toast.LENGTH_LONG).show();
                return;
            }
            Toast.makeText(activity, activity.getString(R.string.inspector_save_complete, fileName),
                    Toast.LENGTH_LONG).show();
        }
    }
    private static final class ShareTextFileTask extends AsyncTask<Void, Void, File> {
        private final WeakReference<InspectorDetailActivity> activityReference;
        private final File exportDirectory;
        private final String fileName;
        private final String value;
        private String error;

        ShareTextFileTask(InspectorDetailActivity activity, String fileName, String value) {
            activityReference = new WeakReference<>(activity);
            exportDirectory = ShareFiles.exportDirectory(activity.getApplicationContext());
            this.fileName = fileName;
            this.value = value;
        }

        @Override protected File doInBackground(Void... ignored) {
            File file = new File(exportDirectory, fileName);
            try (FileOutputStream output = new FileOutputStream(file)) {
                output.write(value.getBytes(StandardCharsets.UTF_8));
                output.flush();
                return file;
            } catch (Exception exception) {
                error = exception.toString();
                return null;
            }
        }

        @Override protected void onPostExecute(File file) {
            InspectorDetailActivity activity = activityReference.get();
            if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
            if (file == null) {
                Toast.makeText(activity, activity.getString(R.string.inspector_share_failed, error),
                        Toast.LENGTH_LONG).show();
                return;
            }
            ShareFiles.share(activity, file, "text/plain");
        }
    }
}
