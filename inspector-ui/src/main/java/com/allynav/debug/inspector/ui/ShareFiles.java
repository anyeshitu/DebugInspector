package com.allynav.debug.inspector.ui;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import java.io.File;

final class ShareFiles {
    private static final String DIRECTORY = "debug-inspector-exports";

    private ShareFiles() {
    }

    static File exportDirectory(Context context) {
        File directory = new File(context.getCacheDir(), DIRECTORY);
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IllegalStateException("Unable to create export directory");
        }
        return directory;
    }

    static void share(Context context, File file, String mimeType) {
        Uri uri = new Uri.Builder().scheme("content")
                .authority(context.getPackageName() + ".debuginspector.files")
                .appendPath(file.getName()).build();
        Intent intent = new Intent(Intent.ACTION_SEND).setType(mimeType)
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setClipData(ClipData.newRawUri(file.getName(), uri));
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.inspector_share_title)));
    }
}
