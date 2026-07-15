package com.allynav.debug.inspector.ui;

import android.view.View;
import android.widget.AdapterView;

final class SimpleItemSelectedListener implements AdapterView.OnItemSelectedListener {
    private final Runnable callback;

    SimpleItemSelectedListener(Runnable callback) {
        this.callback = callback;
    }

    @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { callback.run(); }
    @Override public void onNothingSelected(AdapterView<?> parent) { }
}
