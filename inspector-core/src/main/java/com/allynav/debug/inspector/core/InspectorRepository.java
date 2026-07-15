package com.allynav.debug.inspector.core;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.allynav.debug.inspector.api.BodyData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class InspectorRepository {
    private final InspectorDatabaseHelper helper;

    InspectorRepository(InspectorDatabaseHelper helper) {
        this.helper = helper;
    }

    public List<HttpRecord> listHttp(String query, int limit) {
        String normalized = query == null ? "" : query.trim();
        String selection = normalized.isEmpty() ? null :
                "method LIKE ? OR url LIKE ? OR CAST(status AS TEXT) LIKE ? OR " +
                        "CAST(request_raw AS TEXT) LIKE ? OR CAST(request_plain AS TEXT) LIKE ? OR " +
                        "CAST(response_raw AS TEXT) LIKE ? OR CAST(response_plain AS TEXT) LIKE ?";
        String[] args = null;
        if (selection != null) {
            String pattern = "%" + normalized + "%";
            args = new String[]{pattern, pattern, pattern, pattern, pattern, pattern, pattern};
        }
        Cursor cursor = helper.getReadableDatabase().query("http_events", null, selection, args,
                null, null, "started_at DESC", String.valueOf(safeLimit(limit)));
        try {
            List<HttpRecord> records = new ArrayList<>();
            while (cursor.moveToNext()) records.add(readHttp(cursor));
            return records;
        } finally {
            cursor.close();
        }
    }

    public HttpRecord getHttp(String id) {
        Cursor cursor = helper.getReadableDatabase().query("http_events", null, "id = ?",
                new String[]{id}, null, null, null, "1");
        try {
            return cursor.moveToFirst() ? readHttp(cursor) : null;
        } finally {
            cursor.close();
        }
    }

    public List<WebSocketRecord> listWebSocket(String query, int limit) {
        String normalized = query == null ? "" : query.trim();
        String selection = normalized.isEmpty() ? null : "connection_id LIKE ? OR type LIKE ? OR payload_text LIKE ? OR detail LIKE ?";
        String[] args = null;
        if (selection != null) {
            String pattern = "%" + normalized + "%";
            args = new String[]{pattern, pattern, pattern, pattern};
        }
        Cursor cursor = helper.getReadableDatabase().query("websocket_events", null, selection, args,
                null, null, "timestamp DESC", String.valueOf(safeLimit(limit)));
        try {
            List<WebSocketRecord> records = new ArrayList<>();
            while (cursor.moveToNext()) records.add(new WebSocketRecord(
                    text(cursor, "id"), number(cursor, "timestamp"), text(cursor, "connection_id"),
                    text(cursor, "type"), text(cursor, "direction"), blob(cursor, "payload"),
                    text(cursor, "payload_text"), integer(cursor, "is_text") == 1,
                    integer(cursor, "original_size"), integer(cursor, "truncated") == 1,
                    integer(cursor, "close_code"), text(cursor, "detail")));
            return records;
        } finally {
            cursor.close();
        }
    }

    public List<SerialRecord> listSerial(String query, int limit) {
        String normalized = query == null ? "" : query.trim();
        String selection = normalized.isEmpty() ? null : "port_id LIKE ? OR direction LIKE ? OR payload_text LIKE ? OR payload_hex LIKE ? OR error LIKE ?";
        String[] args = null;
        if (selection != null) {
            String pattern = "%" + normalized + "%";
            args = new String[]{pattern, pattern, pattern, pattern, pattern};
        }
        Cursor cursor = helper.getReadableDatabase().query("serial_events", null, selection, args,
                null, null, "timestamp DESC", String.valueOf(safeLimit(limit)));
        try {
            List<SerialRecord> records = new ArrayList<>();
            while (cursor.moveToNext()) records.add(new SerialRecord(
                    text(cursor, "id"), number(cursor, "timestamp"), text(cursor, "port_id"),
                    text(cursor, "direction"), blob(cursor, "payload"), text(cursor, "payload_text"),
                    text(cursor, "payload_hex"), integer(cursor, "original_size"),
                    integer(cursor, "truncated") == 1, nullableInteger(cursor, "baud_rate"),
                    text(cursor, "configuration"), text(cursor, "error")));
            return records;
        } finally {
            cursor.close();
        }
    }

    public int count(String table) {
        if (!"http_events".equals(table) && !"websocket_events".equals(table) && !"serial_events".equals(table)) {
            throw new IllegalArgumentException("Unsupported table");
        }
        Cursor cursor = helper.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM " + table, null);
        try {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        } finally {
            cursor.close();
        }
    }

    private static HttpRecord readHttp(Cursor cursor) {
        return new HttpRecord(text(cursor, "id"), number(cursor, "started_at"), number(cursor, "duration"),
                text(cursor, "method"), text(cursor, "url"), integer(cursor, "status"),
                text(cursor, "protocol"), text(cursor, "error"), text(cursor, "request_headers"),
                text(cursor, "response_headers"), readBody(cursor, "request_raw"),
                readBody(cursor, "request_plain"), readBody(cursor, "response_raw"),
                readBody(cursor, "response_plain"), text(cursor, "session_id"), text(cursor, "correlation_id"));
    }

    private static BodyData readBody(Cursor cursor, String prefix) {
        int index = cursor.getColumnIndex(prefix);
        if (index < 0 || cursor.isNull(index)) return null;
        return BodyData.builder().bytes(cursor.getBlob(index))
                .contentType(text(cursor, prefix + "_type"))
                .charsetName(text(cursor, prefix + "_charset"))
                .originalLength(number(cursor, prefix + "_original_size"))
                .truncated(integer(cursor, prefix + "_truncated") == 1)
                .transformError(text(cursor, prefix + "_error")).build();
    }

    private static int safeLimit(int limit) {
        return Math.max(1, Math.min(limit <= 0 ? 100 : limit, 500));
    }

    private static String text(Cursor cursor, String name) {
        int index = cursor.getColumnIndex(name);
        return index < 0 || cursor.isNull(index) ? null : cursor.getString(index);
    }

    private static byte[] blob(Cursor cursor, String name) {
        int index = cursor.getColumnIndex(name);
        return index < 0 || cursor.isNull(index) ? new byte[0] : cursor.getBlob(index);
    }

    private static int integer(Cursor cursor, String name) {
        int index = cursor.getColumnIndex(name);
        return index < 0 || cursor.isNull(index) ? 0 : cursor.getInt(index);
    }

    private static Integer nullableInteger(Cursor cursor, String name) {
        int index = cursor.getColumnIndex(name);
        return index < 0 || cursor.isNull(index) ? null : cursor.getInt(index);
    }

    private static long number(Cursor cursor, String name) {
        int index = cursor.getColumnIndex(name);
        return index < 0 || cursor.isNull(index) ? 0 : cursor.getLong(index);
    }

    public static final class HttpRecord {
        public final String id;
        public final long startedAtMillis;
        public final long durationMillis;
        public final String method;
        public final String url;
        public final int statusCode;
        public final String protocol;
        public final String error;
        public final String requestHeadersJson;
        public final String responseHeadersJson;
        public final BodyData requestRawBody;
        public final BodyData requestPlainBody;
        public final BodyData responseRawBody;
        public final BodyData responsePlainBody;
        public final String sessionId;
        public final String correlationId;

        HttpRecord(String id, long startedAtMillis, long durationMillis, String method, String url,
                   int statusCode, String protocol, String error, String requestHeadersJson,
                   String responseHeadersJson, BodyData requestRawBody, BodyData requestPlainBody,
                   BodyData responseRawBody, BodyData responsePlainBody, String sessionId, String correlationId) {
            this.id = id;
            this.startedAtMillis = startedAtMillis;
            this.durationMillis = durationMillis;
            this.method = method;
            this.url = url;
            this.statusCode = statusCode;
            this.protocol = protocol;
            this.error = error;
            this.requestHeadersJson = requestHeadersJson;
            this.responseHeadersJson = responseHeadersJson;
            this.requestRawBody = requestRawBody;
            this.requestPlainBody = requestPlainBody;
            this.responseRawBody = responseRawBody;
            this.responsePlainBody = responsePlainBody;
            this.sessionId = sessionId;
            this.correlationId = correlationId;
        }
    }

    public static final class WebSocketRecord {
        public final String id;
        public final long timestampMillis;
        public final String connectionId;
        public final String type;
        public final String direction;
        public final byte[] payload;
        public final String payloadText;
        public final boolean text;
        public final int originalSize;
        public final boolean truncated;
        public final int closeCode;
        public final String detail;

        WebSocketRecord(String id, long timestampMillis, String connectionId, String type, String direction,
                        byte[] payload, String payloadText, boolean text, int originalSize, boolean truncated,
                        int closeCode, String detail) {
            this.id = id; this.timestampMillis = timestampMillis; this.connectionId = connectionId;
            this.type = type; this.direction = direction; this.payload = payload.clone();
            this.payloadText = payloadText; this.text = text; this.originalSize = originalSize;
            this.truncated = truncated; this.closeCode = closeCode; this.detail = detail;
        }
    }

    public static final class SerialRecord {
        public final String id;
        public final long timestampMillis;
        public final String portId;
        public final String direction;
        public final byte[] payload;
        public final String payloadText;
        public final String payloadHex;
        public final int originalSize;
        public final boolean truncated;
        public final Integer baudRate;
        public final String configuration;
        public final String error;

        SerialRecord(String id, long timestampMillis, String portId, String direction, byte[] payload,
                     String payloadText, String payloadHex, int originalSize, boolean truncated,
                     Integer baudRate, String configuration, String error) {
            this.id = id; this.timestampMillis = timestampMillis; this.portId = portId;
            this.direction = direction; this.payload = payload.clone(); this.payloadText = payloadText;
            this.payloadHex = payloadHex; this.originalSize = originalSize; this.truncated = truncated;
            this.baudRate = baudRate; this.configuration = configuration; this.error = error;
        }
    }
}
