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
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.allynav.debug.inspector.core.DatabaseInspector;
import com.allynav.debug.inspector.core.InspectorCore;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public final class DatabaseRowsActivity extends InspectorBaseActivity {
    private static final int PAGE_SIZE = 50;
    private static final int REQUEST_EXPORT_FOLDER = 7101;
    private static final String STATE_PENDING_EXPORT_JSON = "pending_export_json";
    private static final String EXTRA_PATH = "path";
    private static final String EXTRA_TABLE = "table";
    private String path;
    private String table;
    private int offset;
    private TextView empty;
    private HorizontalScrollView gridScroll;
    private LinearLayout grid;
    private TextView pageLabel;
    private Spinner filterColumn;
    private EditText filterValue;
    private Button previous;
    private Button next;
    private boolean pendingExportJson;

    static Intent intent(Context context, String path, String table) {
        return new Intent(context, DatabaseRowsActivity.class).putExtra(EXTRA_PATH, path).putExtra(EXTRA_TABLE, table);
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.inspector_database_rows_activity);
        path = getIntent().getStringExtra(EXTRA_PATH);
        table = getIntent().getStringExtra(EXTRA_TABLE);
        if (savedInstanceState != null) pendingExportJson = savedInstanceState.getBoolean(STATE_PENDING_EXPORT_JSON);
        ((TextView) findViewById(R.id.rows_title)).setText(table);
        filterColumn = findViewById(R.id.rows_filter_column);
        filterValue = findViewById(R.id.rows_filter_value);
        pageLabel = findViewById(R.id.rows_page);
        previous = findViewById(R.id.rows_previous);
        next = findViewById(R.id.rows_next);
        empty = findViewById(android.R.id.empty);
        gridScroll = findViewById(R.id.rows_grid_scroll);
        grid = findViewById(R.id.rows_grid);
        findViewById(R.id.rows_back).setOnClickListener(v -> finish());
        findViewById(R.id.rows_apply_filter).setOnClickListener(v -> { offset = 0; load(); });
        previous.setOnClickListener(v -> { offset = Math.max(0, offset - PAGE_SIZE); load(); });
        next.setOnClickListener(v -> { offset += PAGE_SIZE; load(); });
        findViewById(R.id.rows_export_csv).setOnClickListener(v -> chooseExportFolder(false));
        findViewById(R.id.rows_export_json).setOnClickListener(v -> chooseExportFolder(true));
        load();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(STATE_PENDING_EXPORT_JSON, pendingExportJson);
        super.onSaveInstanceState(outState);
    }

    private void chooseExportFolder(boolean json) {
        pendingExportJson = json;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_EXPORT_FOLDER);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_EXPORT_FOLDER || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        final Uri folder = data.getData();
        try {
            int takeFlags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            if (takeFlags != 0) getContentResolver().takePersistableUriPermission(folder, takeFlags);
        } catch (SecurityException ignored) {
            // Some providers grant a one-shot tree permission only.
        }
        final boolean json = pendingExportJson;
        final String fileName = safeFileName(table) + (json ? ".json" : ".csv");
        new AlertDialog.Builder(this)
                .setTitle(R.string.inspector_export_confirm_title)
                .setMessage(getString(R.string.inspector_export_confirm_message, fileName))
                .setNegativeButton(R.string.inspector_cancel, null)
                .setPositiveButton(R.string.inspector_confirm, (dialog, which) -> exportToFolder(folder, json, fileName))
                .show();
    }

    private void load() {
        final String column = selectedColumn();
        final String value = filterValue.getText().toString();
        empty.setText(R.string.inspector_loading);
        empty.setVisibility(View.VISIBLE);
        gridScroll.setVisibility(View.GONE);
        grid.removeAllViews();
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
                if (filterColumn.getCount() == 0) {
                    List<String> columns = new ArrayList<>();
                    columns.add(getString(R.string.inspector_all));
                    columns.addAll(result.page.columns);
                    filterColumn.setAdapter(new ArrayAdapter<>(DatabaseRowsActivity.this,
                            android.R.layout.simple_spinner_dropdown_item, columns));
                }
                renderGrid(result.page);
                pageLabel.setText(getString(R.string.inspector_page_value, offset / PAGE_SIZE + 1));
                previous.setEnabled(offset > 0);
                next.setEnabled(result.page.hasMore);
            }
        }.execute();
    }

    private void renderGrid(DatabaseInspector.RowPage page) {
        grid.removeAllViews();
        if (page.rows.isEmpty()) {
            empty.setText(R.string.inspector_no_rows);
            empty.setVisibility(View.VISIBLE);
            gridScroll.setVisibility(View.GONE);
            return;
        }
        int[] widths = columnWidths(page);
        addGridRow(page.columns, widths, true, -1);
        for (int rowIndex = 0; rowIndex < page.rows.size(); rowIndex++) {
            addGridRow(page.rows.get(rowIndex), widths, false, rowIndex);
        }
        empty.setVisibility(View.GONE);
        gridScroll.setVisibility(View.VISIBLE);
        gridScroll.scrollTo(0, 0);
    }

    private int[] columnWidths(DatabaseInspector.RowPage page) {
        TextPaint paint = new TextPaint();
        paint.setTextSize(sp(13));
        int[] widths = new int[page.columns.size()];
        for (int column = 0; column < page.columns.size(); column++) {
            float measured = paint.measureText(page.columns.get(column));
            for (List<String> row : page.rows) {
                String value = row.get(column);
                measured = Math.max(measured, paint.measureText(value == null ? "NULL" : singleLine(value)));
            }
            widths[column] = Math.max(dp(96), Math.min(dp(420), (int) Math.ceil(measured) + dp(32)));
        }
        int totalWidth = 0;
        for (int width : widths) totalWidth += width + dp(1);
        int availableWidth = getResources().getDisplayMetrics().widthPixels;
        if (totalWidth < availableWidth && widths.length > 0) {
            int extraPerColumn = (availableWidth - totalWidth) / widths.length;
            for (int column = 0; column < widths.length; column++) widths[column] += extraPerColumn;
        }
        return widths;
    }

    private void addGridRow(List<String> values, int[] widths, boolean header, int rowIndex) {
        LinearLayout rowView = new LinearLayout(this);
        rowView.setOrientation(LinearLayout.HORIZONTAL);
        rowView.setGravity(Gravity.CENTER_VERTICAL);
        rowView.setMinimumHeight(dp(header ? 44 : 48));
        if (header) rowView.setBackgroundColor(getResources().getColor(R.color.inspector_table_header));
        else rowView.setBackgroundResource(rowIndex % 2 == 0
                ? R.drawable.inspector_table_row_even : R.drawable.inspector_table_row_odd);

        for (int column = 0; column < values.size(); column++) {
            if (column > 0) rowView.addView(verticalDivider());
            String value = values.get(column);
            TextView cell = new TextView(this);
            cell.setGravity(Gravity.CENTER_VERTICAL);
            cell.setPadding(dp(12), dp(8), dp(12), dp(8));
            cell.setSingleLine(true);
            cell.setEllipsize(TextUtils.TruncateAt.END);
            cell.setText(value == null ? "NULL" : singleLine(value));
            cell.setTextColor(getResources().getColor(value == null
                    ? R.color.inspector_muted : header ? R.color.inspector_primary : R.color.inspector_text));
            cell.setTextSize(header ? 13 : 12);
            if (header) cell.setTypeface(null, Typeface.BOLD);
            rowView.addView(cell, new LinearLayout.LayoutParams(widths[column], LinearLayout.LayoutParams.MATCH_PARENT));
        }

        if (!header) {
            final List<String> record = values;
            final String detail = rowText(currentColumns(), record);
            rowView.setClickable(true);
            rowView.setFocusable(true);
            rowView.setOnClickListener(v -> startActivity(InspectorDetailActivity.intent(this,
                    new UiRow(UiRow.DATABASE_ROW, String.valueOf(offset + rowIndex),
                            table + " #" + (offset + rowIndex + 1), "", "", detail, path,
                            "ROW", UiRow.TONE_IDLE))));
            rowView.setOnLongClickListener(v -> { copy(detail); return true; });
        }
        grid.addView(rowView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        grid.addView(horizontalDivider());
    }

    private List<String> currentColumns() {
        List<String> columns = new ArrayList<>();
        for (int i = 1; i < filterColumn.getCount(); i++) columns.add(String.valueOf(filterColumn.getItemAtPosition(i)));
        return columns;
    }

    private View verticalDivider() {
        View view = new View(this);
        view.setBackgroundColor(getResources().getColor(R.color.inspector_divider));
        view.setLayoutParams(new LinearLayout.LayoutParams(dp(1), LinearLayout.LayoutParams.MATCH_PARENT));
        return view;
    }

    private View horizontalDivider() {
        View view = new View(this);
        view.setBackgroundColor(getResources().getColor(R.color.inspector_divider));
        view.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
        return view;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private float sp(int value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }

    private static String singleLine(String value) {
        return value.replace('\r', ' ').replace('\n', ' ');
    }

    private void exportToFolder(final Uri folder, final boolean json, final String fileName) {
        final String column = selectedColumn();
        final String value = filterValue.getText().toString();
        new FolderExportTask(this, folder, json, fileName, path, table, column, value,
                InspectorCore.config().getRetentionPolicy().getMaxDatabaseExportRows()).execute();
    }

    private static void copyFile(File source, ContentResolver resolver, Uri target) throws Exception {
        try (InputStream input = new FileInputStream(source);
             OutputStream output = resolver.openOutputStream(target, "w")) {
            if (output == null) throw new IllegalStateException("Unable to open export document");
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            output.flush();
        }
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

    private static String safeFileName(String value) { return value.replaceAll("[^A-Za-z0-9._-]", "_"); }

    private static final class Result {
        final DatabaseInspector.RowPage page; final String error;
        Result(DatabaseInspector.RowPage page, String error) { this.page = page; this.error = error; }
    }
    private static final class FolderExportResult {
        final DatabaseInspector.ExportResult value; final String error;
        FolderExportResult(DatabaseInspector.ExportResult value, String error) { this.value = value; this.error = error; }
    }

    private static final class FolderExportTask extends AsyncTask<Void, Void, FolderExportResult> {
        private final WeakReference<DatabaseRowsActivity> activityReference;
        private final File exportDirectory;
        private final ContentResolver contentResolver;
        private final Uri folder;
        private final boolean json;
        private final String fileName;
        private final String path;
        private final String table;
        private final String column;
        private final String filterValue;
        private final int maxRows;

        FolderExportTask(DatabaseRowsActivity activity, Uri folder, boolean json, String fileName,
                         String path, String table, String column, String filterValue, int maxRows) {
            this.activityReference = new WeakReference<>(activity);
            this.exportDirectory = ShareFiles.exportDirectory(activity.getApplicationContext());
            this.contentResolver = activity.getApplicationContext().getContentResolver();
            this.folder = folder;
            this.json = json;
            this.fileName = fileName;
            this.path = path;
            this.table = table;
            this.column = column;
            this.filterValue = filterValue;
            this.maxRows = maxRows;
        }

        @Override protected FolderExportResult doInBackground(Void... ignored) {
            File temp = null;
            try {
                temp = new File(exportDirectory, "." + fileName + ".tmp");
                DatabaseInspector.ExportResult result = InspectorCore.databaseInspector().export(path, table,
                        column, filterValue, temp, json, maxRows);
                String mime = json ? "application/json" : "text/csv";
                Uri parent = DocumentsContract.buildDocumentUriUsingTree(folder,
                        DocumentsContract.getTreeDocumentId(folder));
                Uri target = DocumentsContract.createDocument(contentResolver, parent, mime, fileName);
                if (target == null) throw new IllegalStateException("Unable to create export document");
                copyFile(temp, contentResolver, target);
                return new FolderExportResult(result, null);
            } catch (Exception error) {
                return new FolderExportResult(null, error.toString());
            } finally {
                if (temp != null && temp.exists()) temp.delete();
            }
        }

        @Override protected void onPostExecute(FolderExportResult result) {
            DatabaseRowsActivity activity = activityReference.get();
            if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
            if (result.value == null) {
                Toast.makeText(activity, activity.getString(R.string.inspector_export_failed, result.error),
                        Toast.LENGTH_LONG).show();
                return;
            }
            String suffix = result.value.truncated ? activity.getString(R.string.inspector_export_truncated) : "";
            Toast.makeText(activity, activity.getString(R.string.inspector_export_saved,
                    fileName, result.value.rows, suffix), Toast.LENGTH_LONG).show();
        }
    }
}
