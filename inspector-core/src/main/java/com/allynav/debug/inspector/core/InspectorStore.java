package com.allynav.debug.inspector.core;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.allynav.debug.inspector.api.BodyData;
import com.allynav.debug.inspector.api.HttpExchange;
import com.allynav.debug.inspector.api.InspectorConfig;
import com.allynav.debug.inspector.api.RetentionPolicy;
import com.allynav.debug.inspector.api.SerialEvent;
import com.allynav.debug.inspector.api.WebSocketEvent;

final class InspectorStore {
    private final InspectorDatabaseHelper helper;
    private final InspectorConfig config;

    InspectorStore(InspectorDatabaseHelper helper, InspectorConfig config) {
        this.helper = helper;
        this.config = config;
    }

    void insertHttp(HttpExchange exchange) {
        RetentionPolicy policy = config.getRetentionPolicy();
        ContentValues values = new ContentValues();
        values.put("id", exchange.getId());
        values.put("started_at", exchange.getStartedAtMillis());
        values.put("duration", exchange.getDurationMillis());
        values.put("method", exchange.getMethod());
        values.put("url", exchange.getUrl());
        values.put("status", exchange.getStatusCode());
        values.put("protocol", exchange.getProtocol());
        values.put("error", exchange.getError());
        String requestHeaders = DataCodec.headersToJson(exchange.getRequestHeaders(), config.getRedactedHeaderNames());
        String responseHeaders = DataCodec.headersToJson(exchange.getResponseHeaders(), config.getRedactedHeaderNames());
        values.put("request_headers", requestHeaders);
        values.put("response_headers", responseHeaders);
        long stored = stringBytes(exchange.getMethod()) + stringBytes(exchange.getUrl()) +
                stringBytes(requestHeaders) + stringBytes(responseHeaders) + stringBytes(exchange.getError());
        stored += putBody(values, "request_raw", DataCodec.truncate(exchange.getRequestRawBody(), policy.getMaxHttpBodyBytes()));
        stored += putBody(values, "request_plain", DataCodec.truncate(exchange.getRequestTransformedBody(), policy.getMaxHttpBodyBytes()));
        stored += putBody(values, "response_raw", DataCodec.truncate(exchange.getResponseRawBody(), policy.getMaxHttpBodyBytes()));
        stored += putBody(values, "response_plain", DataCodec.truncate(exchange.getResponseTransformedBody(), policy.getMaxHttpBodyBytes()));
        values.put("session_id", exchange.getSessionId());
        values.put("correlation_id", exchange.getCorrelationId());
        values.put("tags", DataCodec.tagsToJson(exchange.getTags()));
        values.put("stored_bytes", stored);
        helper.getWritableDatabase().insertOrThrow("http_events", null, values);
        cleanup();
    }

    void insertWebSocket(WebSocketEvent event) {
        byte[] original = event.getPayload();
        byte[] storedPayload = DataCodec.truncate(original, config.getRetentionPolicy().getMaxEventPayloadBytes());
        ContentValues values = new ContentValues();
        values.put("id", event.getId());
        values.put("timestamp", event.getTimestampMillis());
        values.put("connection_id", event.getConnectionId());
        values.put("type", event.getType().name());
        values.put("direction", event.getDirection().name());
        values.put("payload", storedPayload);
        values.put("payload_text", event.isText() ? DataCodec.decode(storedPayload, "UTF-8") : DataCodec.hex(storedPayload));
        values.put("is_text", event.isText() ? 1 : 0);
        values.put("original_size", original.length);
        values.put("truncated", original.length > storedPayload.length ? 1 : 0);
        values.put("close_code", event.getCloseCode());
        values.put("detail", event.getDetail());
        values.put("session_id", event.getSessionId());
        values.put("correlation_id", event.getCorrelationId());
        values.put("stored_bytes", storedPayload.length + stringBytes(event.getConnectionId()) +
                stringBytes(event.getDetail()));
        helper.getWritableDatabase().insertOrThrow("websocket_events", null, values);
        cleanup();
    }

    void insertSerial(SerialEvent event) {
        byte[] original = event.getPayload();
        byte[] storedPayload = DataCodec.truncate(original, config.getRetentionPolicy().getMaxEventPayloadBytes());
        ContentValues values = new ContentValues();
        values.put("id", event.getId());
        values.put("timestamp", event.getTimestampMillis());
        values.put("port_id", event.getPortId());
        values.put("direction", event.getDirection().name());
        values.put("payload", storedPayload);
        values.put("payload_text", DataCodec.decode(storedPayload, event.getCharsetName()));
        values.put("payload_hex", DataCodec.hex(storedPayload));
        values.put("charset_name", event.getCharsetName());
        values.put("original_size", original.length);
        values.put("truncated", original.length > storedPayload.length ? 1 : 0);
        if (event.getBaudRate() != null) values.put("baud_rate", event.getBaudRate());
        values.put("configuration", event.getConfiguration());
        values.put("error", event.getError());
        values.put("session_id", event.getSessionId());
        values.put("correlation_id", event.getCorrelationId());
        values.put("stored_bytes", storedPayload.length + stringBytes(event.getPortId()) +
                stringBytes(event.getConfiguration()) + stringBytes(event.getError()));
        helper.getWritableDatabase().insertOrThrow("serial_events", null, values);
        cleanup();
    }

    void clear() {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("http_events", null, null);
            db.delete("websocket_events", null, null);
            db.delete("serial_events", null, null);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    void cleanup() {
        SQLiteDatabase db = helper.getWritableDatabase();
        RetentionPolicy policy = config.getRetentionPolicy();
        long cutoff = System.currentTimeMillis() - policy.getRetentionMillis();
        db.delete("http_events", "started_at < ?", new String[]{String.valueOf(cutoff)});
        db.delete("websocket_events", "timestamp < ?", new String[]{String.valueOf(cutoff)});
        db.delete("serial_events", "timestamp < ?", new String[]{String.valueOf(cutoff)});

        long total = totalStoredBytes(db);
        boolean deleted = false;
        while (total > policy.getMaxStoreBytes() || helper.databaseFile().length() > policy.getMaxStoreBytes()) {
            Oldest oldest = findOldest(db);
            if (oldest == null) break;
            db.delete(oldest.table, "id = ?", new String[]{oldest.id});
            total -= oldest.bytes;
            deleted = true;
        }
        if (deleted) db.execSQL("PRAGMA incremental_vacuum");
    }

    private static long putBody(ContentValues values, String prefix, BodyData body) {
        if (body == null) return 0;
        byte[] bytes = body.getBytes();
        values.put(prefix, bytes);
        values.put(prefix + "_type", body.getContentType());
        values.put(prefix + "_charset", body.getCharsetName());
        values.put(prefix + "_original_size", body.getOriginalLength());
        values.put(prefix + "_truncated", body.isTruncated() ? 1 : 0);
        values.put(prefix + "_error", body.getTransformError());
        return bytes.length;
    }

    private static int stringBytes(String value) {
        return value == null ? 0 : value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    }

    private static long totalStoredBytes(SQLiteDatabase db) {
        Cursor cursor = db.rawQuery("SELECT " +
                "COALESCE((SELECT SUM(stored_bytes) FROM http_events),0) + " +
                "COALESCE((SELECT SUM(stored_bytes) FROM websocket_events),0) + " +
                "COALESCE((SELECT SUM(stored_bytes) FROM serial_events),0)", null);
        try {
            return cursor.moveToFirst() ? cursor.getLong(0) : 0;
        } finally {
            cursor.close();
        }
    }

    private static Oldest findOldest(SQLiteDatabase db) {
        Cursor cursor = db.rawQuery("SELECT table_name,id,event_time,stored_bytes FROM (" +
                "SELECT 'http_events' table_name,id,started_at event_time,stored_bytes FROM http_events UNION ALL " +
                "SELECT 'websocket_events',id,timestamp,stored_bytes FROM websocket_events UNION ALL " +
                "SELECT 'serial_events',id,timestamp,stored_bytes FROM serial_events) " +
                "ORDER BY event_time ASC LIMIT 1", null);
        try {
            return cursor.moveToFirst() ? new Oldest(cursor.getString(0), cursor.getString(1), cursor.getLong(3)) : null;
        } finally {
            cursor.close();
        }
    }

    private static final class Oldest {
        final String table;
        final String id;
        final long bytes;

        Oldest(String table, String id, long bytes) {
            this.table = table;
            this.id = id;
            this.bytes = bytes;
        }
    }
}
