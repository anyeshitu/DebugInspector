package com.allynav.debug.inspector.core;

import com.allynav.debug.inspector.api.BodyData;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class HttpExportFormatter {
    public enum Format { TEXT, CURL, JSON, HAR }

    private HttpExportFormatter() {
    }

    public static String format(InspectorRepository.HttpRecord record, Format format) {
        if (record == null) throw new IllegalArgumentException("record is required");
        switch (format == null ? Format.TEXT : format) {
            case CURL: return curl(record);
            case JSON: return json(record).toString();
            case HAR: return har(record).toString();
            case TEXT:
            default: return text(record);
        }
    }

    private static String text(InspectorRepository.HttpRecord record) {
        StringBuilder builder = new StringBuilder();
        builder.append(record.method).append(' ').append(record.url).append('\n');
        builder.append("Status: ").append(record.statusCode).append('\n');
        builder.append("Duration: ").append(record.durationMillis).append(" ms\n\n");
        appendSection(builder, "Request headers", prettyJson(record.requestHeadersJson));
        appendBody(builder, "Request encrypted/raw", record.requestRawBody);
        appendBody(builder, "Request plaintext/transformed", record.requestPlainBody);
        appendSection(builder, "Response headers", prettyJson(record.responseHeadersJson));
        appendBody(builder, "Response encrypted/raw", record.responseRawBody);
        appendBody(builder, "Response plaintext/transformed", record.responsePlainBody);
        if (record.error != null) appendSection(builder, "Error", record.error);
        return builder.toString();
    }

    private static String curl(InspectorRepository.HttpRecord record) {
        StringBuilder builder = new StringBuilder("curl -X ").append(shell(record.method))
                .append(" ").append(shell(record.url));
        try {
            JSONArray headers = new JSONArray(record.requestHeadersJson == null ? "[]" : record.requestHeadersJson);
            for (int i = 0; i < headers.length(); i++) {
                JSONObject header = headers.getJSONObject(i);
                builder.append(" -H ").append(shell(header.optString("name") + ": " + header.optString("value")));
            }
        } catch (JSONException ignored) {
        }
        if (record.requestRawBody != null && record.requestRawBody.getBytes().length > 0) {
            builder.append(" --data-raw ").append(shell(record.requestRawBody.asText()));
        }
        return builder.toString();
    }

    private static JSONObject json(InspectorRepository.HttpRecord record) {
        JSONObject root = new JSONObject();
        try {
            root.put("id", record.id);
            root.put("startedAt", record.startedAtMillis);
            root.put("durationMs", record.durationMillis);
            root.put("method", record.method);
            root.put("url", record.url);
            root.put("status", record.statusCode);
            root.put("protocol", record.protocol);
            root.put("error", record.error == null ? JSONObject.NULL : record.error);
            root.put("requestHeaders", new JSONArray(record.requestHeadersJson == null ? "[]" : record.requestHeadersJson));
            root.put("responseHeaders", new JSONArray(record.responseHeadersJson == null ? "[]" : record.responseHeadersJson));
            root.put("requestEncryptedRaw", bodyJson(record.requestRawBody));
            root.put("requestPlaintextTransformed", bodyJson(record.requestPlainBody));
            root.put("responseEncryptedRaw", bodyJson(record.responseRawBody));
            root.put("responsePlaintextTransformed", bodyJson(record.responsePlainBody));
        } catch (JSONException ignored) {
        }
        return root;
    }

    private static JSONObject har(InspectorRepository.HttpRecord record) {
        JSONObject root = new JSONObject();
        JSONObject log = new JSONObject();
        JSONArray entries = new JSONArray();
        JSONObject entry = new JSONObject();
        try {
            root.put("log", log);
            log.put("version", "1.2");
            log.put("creator", new JSONObject().put("name", "DebugInspector").put("version", "0.1.0"));
            log.put("entries", entries);
            entries.put(entry);
            entry.put("startedDateTime", record.startedAtMillis);
            entry.put("time", record.durationMillis);
            entry.put("request", new JSONObject()
                    .put("method", record.method)
                    .put("url", record.url)
                    .put("headers", new JSONArray(record.requestHeadersJson == null ? "[]" : record.requestHeadersJson))
                    .put("postData", bodyJson(record.requestRawBody)));
            entry.put("response", new JSONObject()
                    .put("status", record.statusCode)
                    .put("headers", new JSONArray(record.responseHeadersJson == null ? "[]" : record.responseHeadersJson))
                    .put("content", bodyJson(record.responseRawBody)));
            entry.put("_debugInspector", new JSONObject()
                    .put("requestPlaintextTransformed", bodyJson(record.requestPlainBody))
                    .put("responsePlaintextTransformed", bodyJson(record.responsePlainBody)));
        } catch (JSONException ignored) {
        }
        return root;
    }

    private static JSONObject bodyJson(BodyData body) throws JSONException {
        if (body == null) return new JSONObject().put("present", false);
        return new JSONObject().put("present", true).put("contentType", body.getContentType())
                .put("charset", body.getCharsetName()).put("text", body.asText())
                .put("originalLength", body.getOriginalLength()).put("storedLength", body.getBytes().length)
                .put("truncated", body.isTruncated())
                .put("transformError", body.getTransformError() == null ? JSONObject.NULL : body.getTransformError());
    }

    private static void appendBody(StringBuilder builder, String title, BodyData body) {
        if (body == null) return;
        String suffix = body.isTruncated() ? " [truncated]" : "";
        if (body.getTransformError() != null) suffix += " [" + body.getTransformError() + "]";
        appendSection(builder, title + suffix, body.asText());
    }

    private static void appendSection(StringBuilder builder, String title, String content) {
        builder.append("==== ").append(title).append(" ====\n");
        builder.append(content == null ? "" : content).append("\n\n");
    }

    private static String prettyJson(String value) {
        try { return new JSONArray(value == null ? "[]" : value).toString(2); }
        catch (JSONException ignored) { return value == null ? "" : value; }
    }

    private static String shell(String value) {
        return "'" + (value == null ? "" : value).replace("'", "'\\''") + "'";
    }
}
