package com.allynav.debug.inspector.ui;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

public final class InspectorFileProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        File file;
        try { file = resolve(uri); }
        catch (FileNotFoundException error) { return new MatrixCursor(projection == null
                ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection, 0); }
        String[] columns = projection == null ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
        MatrixCursor cursor = new MatrixCursor(columns, 1);
        MatrixCursor.RowBuilder row = cursor.newRow();
        for (String column : columns) {
            if (OpenableColumns.DISPLAY_NAME.equals(column)) row.add(file.getName());
            else if (OpenableColumns.SIZE.equals(column)) row.add(file.length());
            else row.add(null);
        }
        return cursor;
    }

    @Override public String getType(Uri uri) {
        String segment = uri.getLastPathSegment();
        String name = segment == null ? "" : segment.toLowerCase();
        if (name.endsWith(".json")) return "application/json";
        if (name.endsWith(".csv")) return "text/csv";
        return "text/plain";
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Read-only provider");
        return ParcelFileDescriptor.open(resolve(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException("Read only"); }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }

    private File resolve(Uri uri) throws FileNotFoundException {
        if (getContext() == null || uri.getLastPathSegment() == null) throw new FileNotFoundException("Invalid URI");
        File root = ShareFiles.exportDirectory(getContext());
        File file = new File(root, uri.getLastPathSegment());
        try {
            String rootPath = root.getCanonicalPath() + File.separator;
            if (!file.getCanonicalPath().startsWith(rootPath) || !file.isFile()) throw new FileNotFoundException("File unavailable");
            return file;
        } catch (IOException error) {
            throw new FileNotFoundException(error.toString());
        }
    }
}
