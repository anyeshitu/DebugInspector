package com.allynav.debug.inspector.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.allynav.debug.inspector.core.DatabaseInspector;
import com.allynav.debug.inspector.core.InspectorCore;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class DatabaseRowsActivity extends InspectorBaseActivity {
    private static final int PAGE_SIZE = 50;
    private static final String EXTRA_PATH = "path";
    private static final String EXTRA_TABLE = "table";
    private String path;
    private String table;
    private int offset;
    private UiRowAdapter adapter;
    private TextView empty;
    private TextView pageLabel;
    private Spinner filterColumn;
    private EditText filterValue;
    private Button previous;
    private Button next;
    private DatabaseInspector.RowPage currentPage;

    static Intent intent(Context context, String path, String table) {
        return new Intent(context, DatabaseRowsActivity.class).putExtra(EXTRA_PATH, path).putExtra(EXTRA_TABLE, table);
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.inspector_database_rows_activity);
        path = getIntent().getStringExtra(EXTRA_PATH);
        table = getIntent().getStringExtra(EXTRA_TABLE);
        ((TextView) findViewById(R.id.rows_title)).setText(table);
        filterColumn = findViewById(R.id.rows_filter_column);
        filterValue = findViewById(R.id.rows_filter_value);
        pageLabel = findViewById(R.id.rows_page);
        previous = findViewById(R.id.rows_previous);
        next = findViewById(R.id.rows_next);
        empty = findViewById(android.R.id.empty);
        adapter = new UiRowAdapter(this);
        ListView list = findViewById(android.R.id.list);
        list.setEmptyView(empty);
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) ->
                startActivity(InspectorDetailActivity.intent(this, adapter.getItem(position))));
        list.setOnItemLongClickListener((parent, view, position, id) -> { copy(adapter.getItem(position).detail); return true; });
        findViewById(R.id.rows_back).setOnClickListener(v -> finish());
        findViewById(R.id.rows_apply_filter).setOnClickListener(v -> { offset = 0; load(); });
        previous.setOnClickListener(v -> { offset = Math.max(0, offset - PAGE_SIZE); load(); });
        next.setOnClickListener(v -> { offset += PAGE_SIZE; load(); });
        findViewById(R.id.rows_export_csv).setOnClickListener(v -> export(false));
        findViewById(R.id.rows_export_json).setOnClickListener(v -> export(true));
        load();
    }

    private void load() {
        final String column = selectedColumn();
        final String value = filterValue.getText().toString();
        empty.setText(R.string.inspector_loading);
        adapter.replace(new ArrayList<UiRow>());
        new AsyncTask<Void, Void, Result>() {
            @Override protected Result doInBackground(Void... ignored) {
                try { return new Result(InspectorCore.databaseInspector().rows(path, table, column, value, PAGE_SIZE, offset), null); }
                catch (RuntimeException error) { return new Result(null, error.toString()); }
            }
            @Override protected void onPostExecute(Result result) {
                if (result.page == null) {
                    empty.setText(result.error);
                    return;
                }
                currentPage = result.page;
                if (filterColumn.getCount() == 0) {
                    List<String> columns = new ArrayList<>();
                    columns.add(getString(R.string.inspector_all));
                    columns.addAll(result.page.columns);
                    filterColumn.setAdapter(new ArrayAdapter<>(DatabaseRowsActivity.this,
                            android.R.layout.simple_spinner_dropdown_item, columns));
                }
                List<UiRow> rows = new ArrayList<>();
                for (int r = 0; r < result.page.rows.size(); r++) {
                    String detail = rowText(result.page.columns, result.page.rows.get(r));
                    rows.add(new UiRow(UiRow.DATABASE_ROW, String.valueOf(offset + r),
                            "#" + (offset + r + 1), rowSummary(result.page.columns, result.page.rows.get(r)), "", detail, path,
                            "ROW", UiRow.TONE_IDLE));
                }
                adapter.replace(rows);
                empty.setText(R.string.inspector_no_rows);
                pageLabel.setText(getString(R.string.inspector_page_value, offset / PAGE_SIZE + 1));
                previous.setEnabled(offset > 0);
                next.setEnabled(result.page.hasMore);
            }
        }.execute();
    }

    private void export(boolean json) {
        final String column = selectedColumn();
        final String value = filterValue.getText().toString();
        new AsyncTask<Void, Void, ExportResult>() {
            @Override protected ExportResult doInBackground(Void... ignored) {
                try {
                    File directory = ShareFiles.exportDirectory(DatabaseRowsActivity.this);
                    File file = new File(directory, safeFileName(table) + (json ? ".json" : ".csv"));
                    DatabaseInspector.ExportResult result = InspectorCore.databaseInspector().export(path, table,
                            column, value, file, json, InspectorCore.config().getRetentionPolicy().getMaxDatabaseExportRows());
                    return new ExportResult(result, null);
                } catch (Exception error) { return new ExportResult(null, error.toString()); }
            }
            @Override protected void onPostExecute(ExportResult result) {
                if (result.value == null) {
                    Toast.makeText(DatabaseRowsActivity.this, getString(R.string.inspector_export_failed, result.error), Toast.LENGTH_LONG).show();
                    return;
                }
                String suffix = result.value.truncated ? getString(R.string.inspector_export_truncated) : "";
                Toast.makeText(DatabaseRowsActivity.this, getString(R.string.inspector_export_complete, result.value.rows, suffix), Toast.LENGTH_SHORT).show();
                ShareFiles.share(DatabaseRowsActivity.this, result.value.file, json ? "application/json" : "text/csv");
            }
        }.execute();
    }

    private String selectedColumn() {
        return filterColumn.getSelectedItemPosition() <= 0 ? null : String.valueOf(filterColumn.getSelectedItem());
    }

    private void copy(String value) {
        ClipboardManager manager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        manager.setPrimaryClip(ClipData.newPlainText(table, value));
        Toast.makeText(this, R.string.inspector_copied, Toast.LENGTH_SHORT).show();
    }

    private static String rowText(List<String> columns, List<String> row) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) builder.append('\n');
            builder.append(columns.get(i)).append(" = ").append(row.get(i) == null ? "NULL" : row.get(i));
        }
        return builder.toString();
    }

    private static String rowSummary(List<String> columns, List<String> row) {
        StringBuilder builder = new StringBuilder();
        int count = Math.min(3, columns.size());
        for (int i = 0; i < count; i++) {
            if (i > 0) builder.append("  |  ");
            builder.append(columns.get(i)).append('=').append(row.get(i) == null ? "NULL" : row.get(i));
        }
        if (columns.size() > count) builder.append("  ...");
        return builder.toString();
    }
    private static String safeFileName(String value) { return value.replaceAll("[^A-Za-z0-9._-]", "_"); }

    private static final class Result {
        final DatabaseInspector.RowPage page; final String error;
        Result(DatabaseInspector.RowPage page, String error) { this.page = page; this.error = error; }
    }
    private static final class ExportResult {
        final DatabaseInspector.ExportResult value; final String error;
        ExportResult(DatabaseInspector.ExportResult value, String error) { this.value = value; this.error = error; }
    }
}
