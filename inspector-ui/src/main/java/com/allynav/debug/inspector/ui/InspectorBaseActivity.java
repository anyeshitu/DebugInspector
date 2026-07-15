package com.allynav.debug.inspector.ui;

import android.app.Activity;
import android.content.Context;

abstract class InspectorBaseActivity extends Activity {
    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(InspectorUi.localizedContext(newBase));
    }
}
