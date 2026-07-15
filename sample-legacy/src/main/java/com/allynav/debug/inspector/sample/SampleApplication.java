package com.allynav.debug.inspector.sample;

import android.app.Application;
import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

import com.allynav.debug.inspector.DebugInspector;
import com.allynav.debug.inspector.api.DatabaseRegistration;
import com.allynav.debug.inspector.api.EntryConfig;
import com.allynav.debug.inspector.api.InspectorConfig;

public final class SampleApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        seedDatabase();
        InspectorConfig config = InspectorConfig.builder()
                .redactHeaders("Auth-Token", "Authorization")
                .addBodyTransformer(new SampleCryptoTransformer())
                .addDatabase(new DatabaseRegistration(getString(R.string.sample_database), getDatabasePath("sample.db").getAbsolutePath()))
                .entryConfig(EntryConfig.builder().notificationEnabled(true)
                        .shortcutEnabled(true).shakeEnabled(false).build())
                .build();
        DebugInspector.initialize(this, config);
    }

    private void seedDatabase() {
        SQLiteDatabase database = openOrCreateDatabase("sample.db", MODE_PRIVATE, null);
        database.execSQL("CREATE TABLE IF NOT EXISTS work_record (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, status TEXT, created_at INTEGER)");
        database.delete("work_record", null, null);
        insert(database, getString(R.string.sample_work_a), getString(R.string.sample_complete));
        insert(database, getString(R.string.sample_work_b), getString(R.string.sample_waiting));
        database.close();
    }

    private static void insert(SQLiteDatabase database, String name, String status) {
        ContentValues values = new ContentValues();
        values.put("name", name);
        values.put("status", status);
        values.put("created_at", System.currentTimeMillis());
        database.insert("work_record", null, values);
    }
}
