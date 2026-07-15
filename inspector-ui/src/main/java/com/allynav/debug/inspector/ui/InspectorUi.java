package com.allynav.debug.inspector.ui;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;

import com.allynav.debug.inspector.api.InspectorConfig;
import com.allynav.debug.inspector.api.UiLanguage;

import java.util.Locale;

public final class InspectorUi {
    private static volatile UiLanguage language = UiLanguage.SIMPLIFIED_CHINESE;
    private InspectorUi() {
    }

    public static void install(Context context, InspectorConfig config) {
        language = config.getUiLanguage();
        Context localized = localizedContext(context.getApplicationContext());
        EntryController.install(localized, config.getEntryConfig());
    }

    public static void open(Context context) {
        Intent intent = new Intent(context, InspectorActivity.class);
        if (!(context instanceof android.app.Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    public static boolean notificationPermissionGranted(Context context) {
        return EntryController.notificationPermissionGranted(context);
    }

    static Context localizedContext(Context context) {
        if (language == UiLanguage.SYSTEM) return context;
        Locale locale = language == UiLanguage.ENGLISH ? Locale.ENGLISH : Locale.SIMPLIFIED_CHINESE;
        Configuration configuration = new Configuration(context.getResources().getConfiguration());
        configuration.setLocale(locale);
        return context.createConfigurationContext(configuration);
    }
}
