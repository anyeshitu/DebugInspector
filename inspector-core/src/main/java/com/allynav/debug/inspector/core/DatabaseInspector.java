package com.allynav.debug.inspector.core;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.allynav.debug.inspector.api.DatabaseRegistration;
import com.allynav.debug.inspector.api.DatabaseRegistry;

import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DatabaseInspector {
    private final Context context;
    private final DatabaseRegistry registry;

    DatabaseInspector(Context context, DatabaseRegistry registry) {
        this.context = context.getApplicationContext();
        this.registry = registry;
    }

    public List<DatabaseFile> databases() {
        Map<String, DatabaseFile> result = new LinkedHashMap<>();
        String[] names = context.databaseList();
        if (names != null) {
            for (String name : names) {
                if (InspectorDatabaseHelper.DATABASE_NAME.equals(name)
                        || name.startsWith(InspectorDatabaseHelper.DATABASE_NAME + "-")
                        || name.endsWith("-wal") || name.endsWith("-shm") || name.endsWith("-journal")) continue;
                add(result, new DatabaseFile(name, context.getDatabasePath(name).getAbsolutePath(), true));
            }
        }
        for (DatabaseRegistration registration : registry.registrations()) {
            add(result, new DatabaseFile(registration.getDisplayName(), registration.getAbsolutePath(), false));
        }
        return Collections.unmodifiableList(new ArrayList<>(result.values()));
    }

    public List<TableInfo> tables(String path) {
        SQLiteDatabase db = open(path);
        Cursor cursor = db.rawQuery("SELECT name,type FROM sqlite_master " +
                "WHERE type IN ('table','view') AND name NOT LIKE 'sqlite_%' AND name != 'android_metadata' ORDER BY name", null);
        try {
            List<TableInfo> tables = new ArrayList<>();
            while (cursor.moveToNext()) {
                String name = cursor.getString(0);
                tables.add(new TableInfo(name, cursor.getString(1), count(db, name)));
            }
            return tables;
        } finally {
            cursor.close();
            db.close();
        }
    }

    public RowPage rows(String path, String table, String filterColumn, String filterValue, int limit, int offset) {
        SQLiteDatabase db = open(path);
        int safeLimit = Math.max(1, Math.min(limit, 200));
        int safeOffset = Math.max(0, offset);
        StringBuilder sql = new StringBuilder("SELECT * FROM ").append(quote(table));
        List<String> args = new ArrayList<>();
        if (filterColumn != null && !filterColumn.isEmpty() && filterValue != null && !filterValue.isEmpty()) {
            sql.append(" WHERE CAST(").append(quote(filterColumn)).append(" AS TEXT) LIKE ?");
            args.add("%" + filterValue + "%");
        }
        sql.append(" LIMIT ? OFFSET ?");
        args.add(String.valueOf(safeLimit + 1));
        args.add(String.valueOf(safeOffset));
        Cursor cursor = db.rawQuery(sql.toString(), args.toArray(new String[0]));
        try {
            List<String> columns = new ArrayList<>();
            Collections.addAll(columns, cursor.getColumnNames());
            List<List<String>> rows = new ArrayList<>();
            while (cursor.moveToNext() && rows.size() < safeLimit) {
                List<String> row = new ArrayList<>(cursor.getColumnCount());
                for (int i = 0; i < cursor.getColumnCount(); i++) row.add(cursorValue(cursor, i));
                rows.add(row);
            }
            boolean hasMore = cursor.getCount() > safeLimit;
            return new RowPage(columns, rows, safeOffset, hasMore);
        } finally {
            cursor.close();
            db.close();
        }
    }

    public ExportResult export(String path, String table, String filterColumn, String filterValue,
                               File output, boolean json, int maxRows) throws IOException {
        int limit = Math.max(1, maxRows);
        int offset = 0;
        int written = 0;
        boolean truncated = false;
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(output), StandardCharsets.UTF_8))) {
            List<String> columns = null;
            if (json) writer.write("[");
            while (written < limit) {
                RowPage page = rows(path, table, filterColumn, filterValue, Math.min(200, limit - written), offset);
                if (columns == null) {
                    columns = page.columns;
                    if (!json) writeCsvRow(writer, columns);
                }
                for (List<String> row : page.rows) {
                    if (json) {
                        if (written > 0) writer.write(',');
                        writer.write('{');
                        for (int i = 0; i < columns.size(); i++) {
                            if (i > 0) writer.write(',');
                            writer.write(JSONObject.quote(columns.get(i)));
                            writer.write(':');
                            writer.write(row.get(i) == null ? "null" : JSONObject.quote(row.get(i)));
                        }
                        writer.write('}');
                    } else {
                        writeCsvRow(writer, row);
                    }
                    written++;
                }
                offset += page.rows.size();
                if (!page.hasMore || page.rows.isEmpty()) break;
                if (written >= limit) truncated = true;
            }
            if (json) writer.write("]");
        }
        return new ExportResult(output, written, truncated);
    }

    private static void writeCsvRow(BufferedWriter writer, List<String> values) throws IOException {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) writer.write(',');
            String value = values.get(i);
            if (value != null) {
                writer.write('"');
                writer.write(value.replace("\"", "\"\""));
                writer.write('"');
            }
        }
        writer.newLine();
    }

    private static String cursorValue(Cursor cursor, int index) {
        if (cursor.isNull(index)) return null;
        if (cursor.getType(index) == Cursor.FIELD_TYPE_BLOB) return "base64:" + android.util.Base64.encodeToString(cursor.getBlob(index), android.util.Base64.NO_WRAP);
        return cursor.getString(index);
    }

    private static SQLiteDatabase open(String path) {
        File file = new File(path);
        if (!file.isFile()) throw new IllegalArgumentException("Database does not exist: " + path);
        return SQLiteDatabase.openDatabase(file.getAbsolutePath(), null,
                SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
    }

    private static long count(SQLiteDatabase db, String table) {
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + quote(table), null);
        try { return cursor.moveToFirst() ? cursor.getLong(0) : 0; }
        finally { cursor.close(); }
    }

    private static String quote(String identifier) {
        if (identifier == null || identifier.isEmpty()) throw new IllegalArgumentException("identifier is required");
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private static void add(Map<String, DatabaseFile> values, DatabaseFile file) {
        try { values.put(new File(file.path).getCanonicalPath(), file); }
        catch (IOException ignored) { values.put(new File(file.path).getAbsolutePath(), file); }
    }

    public static final class DatabaseFile {
        public final String name;
        public final String path;
        public final boolean discovered;
        DatabaseFile(String name, String path, boolean discovered) { this.name = name; this.path = path; this.discovered = discovered; }
    }

    public static final class TableInfo {
        public final String name;
        public final String type;
        public final long rowCount;
        TableInfo(String name, String type, long rowCount) { this.name = name; this.type = type; this.rowCount = rowCount; }
    }

    public static final class RowPage {
        public final List<String> columns;
        public final List<List<String>> rows;
        public final int offset;
        public final boolean hasMore;
        RowPage(List<String> columns, List<List<String>> rows, int offset, boolean hasMore) {
            this.columns = Collections.unmodifiableList(columns);
            List<List<String>> copy = new ArrayList<>();
            for (List<String> row : rows) copy.add(Collections.unmodifiableList(new ArrayList<>(row)));
            this.rows = Collections.unmodifiableList(copy); this.offset = offset; this.hasMore = hasMore;
        }
    }

    public static final class ExportResult {
        public final File file;
        public final int rows;
        public final boolean truncated;
        ExportResult(File file, int rows, boolean truncated) { this.file = file; this.rows = rows; this.truncated = truncated; }
    }
}
