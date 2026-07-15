package com.allynav.debug.inspector.core;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.io.File;

final class InspectorDatabaseHelper extends SQLiteOpenHelper {
    static final String DATABASE_NAME = "debug_inspector.db";
    private static final int VERSION = 1;
    private final File databaseFile;

    InspectorDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, VERSION);
        databaseFile = context.getDatabasePath(DATABASE_NAME);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.execSQL("PRAGMA auto_vacuum = INCREMENTAL");
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE http_events (" +
                "id TEXT PRIMARY KEY, started_at INTEGER NOT NULL, duration INTEGER NOT NULL," +
                "method TEXT NOT NULL, url TEXT NOT NULL, status INTEGER NOT NULL, protocol TEXT," +
                "error TEXT, request_headers TEXT, response_headers TEXT," +
                bodyColumns("request_raw") + "," + bodyColumns("request_plain") + "," +
                bodyColumns("response_raw") + "," + bodyColumns("response_plain") + "," +
                "session_id TEXT, correlation_id TEXT, tags TEXT, stored_bytes INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX idx_http_time ON http_events(started_at DESC)");
        db.execSQL("CREATE INDEX idx_http_session ON http_events(session_id, correlation_id)");
        db.execSQL("CREATE INDEX idx_http_status ON http_events(status, method)");

        db.execSQL("CREATE TABLE websocket_events (" +
                "id TEXT PRIMARY KEY, timestamp INTEGER NOT NULL, connection_id TEXT NOT NULL," +
                "type TEXT NOT NULL, direction TEXT NOT NULL, payload BLOB, payload_text TEXT," +
                "is_text INTEGER NOT NULL, original_size INTEGER NOT NULL, truncated INTEGER NOT NULL," +
                "close_code INTEGER, detail TEXT, session_id TEXT, correlation_id TEXT," +
                "stored_bytes INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX idx_ws_time ON websocket_events(timestamp DESC)");
        db.execSQL("CREATE INDEX idx_ws_connection ON websocket_events(connection_id, timestamp DESC)");

        db.execSQL("CREATE TABLE serial_events (" +
                "id TEXT PRIMARY KEY, timestamp INTEGER NOT NULL, port_id TEXT NOT NULL," +
                "direction TEXT NOT NULL, payload BLOB, payload_text TEXT, payload_hex TEXT," +
                "charset_name TEXT, original_size INTEGER NOT NULL, truncated INTEGER NOT NULL," +
                "baud_rate INTEGER, configuration TEXT, error TEXT, session_id TEXT, correlation_id TEXT," +
                "stored_bytes INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX idx_serial_time ON serial_events(timestamp DESC)");
        db.execSQL("CREATE INDEX idx_serial_port ON serial_events(port_id, direction, timestamp DESC)");
    }

    private static String bodyColumns(String prefix) {
        return prefix + " BLOB," + prefix + "_type TEXT," + prefix + "_charset TEXT," +
                prefix + "_original_size INTEGER," + prefix + "_truncated INTEGER," + prefix + "_error TEXT";
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        throw new IllegalStateException("No migration from " + oldVersion + " to " + newVersion);
    }

    File databaseFile() {
        return databaseFile;
    }
}
