package com.allynav.debug.inspector.ui;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.ListView;
import android.widget.TextView;

import com.allynav.debug.inspector.core.DatabaseInspector;
import com.allynav.debug.inspector.core.InspectorCore;

import java.util.ArrayList;
import java.util.List;

public final class DatabaseTablesActivity extends InspectorBaseActivity {
    private static final String EXTRA_NAME = "name";
    private static final String EXTRA_PATH = "path";
    private String path;
    private UiRowAdapter adapter;
    private TextView empty;

    static Intent intent(Context context, String name, String path) {
        return new Intent(context, DatabaseTablesActivity.class).putExtra(EXTRA_NAME, name).putExtra(EXTRA_PATH, path);
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.inspector_database_tables_activity);
        path = getIntent().getStringExtra(EXTRA_PATH);
        ((TextView) findViewById(R.id.database_title)).setText(getIntent().getStringExtra(EXTRA_NAME));
        findViewById(R.id.database_back).setOnClickListener(v -> finish());
        findViewById(R.id.database_refresh).setOnClickListener(v -> load());
        ListView list = findViewById(android.R.id.list);
        empty = findViewById(android.R.id.empty);
        adapter = new UiRowAdapter(this);
        list.setEmptyView(empty);
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            UiRow row = adapter.getItem(position);
            startActivity(DatabaseRowsActivity.intent(this, path, row.title));
        });
        load();
    }

    private void load() {
        empty.setText(R.string.inspector_loading);
        adapter.replace(new ArrayList<UiRow>());
        new AsyncTask<Void, Void, Result>() {
            @Override protected Result doInBackground(Void... ignored) {
                try {
                    List<UiRow> rows = new ArrayList<>();
                    for (DatabaseInspector.TableInfo table : InspectorCore.databaseInspector().tables(path)) {
                        String type = "view".equals(table.type) ? getString(R.string.inspector_view) : getString(R.string.inspector_table);
                        rows.add(new UiRow(UiRow.TABLE, table.name, table.name, type,
                                getString(R.string.inspector_rows, table.rowCount), null, path,
                                "view".equals(table.type) ? "VIEW" : "TBL", UiRow.TONE_INFO));
                    }
                    return new Result(rows, null);
                } catch (RuntimeException error) { return new Result(new ArrayList<UiRow>(), error.toString()); }
            }
            @Override protected void onPostExecute(Result result) {
                adapter.replace(result.rows);
                if (result.error == null) empty.setText(R.string.inspector_no_tables);
                else empty.setText(result.error);
            }
        }.execute();
    }

    private static final class Result {
        final List<UiRow> rows; final String error;
        Result(List<UiRow> rows, String error) { this.rows = rows; this.error = error; }
    }
}
