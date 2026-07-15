package com.allynav.debug.inspector;

import android.content.Context;

import com.allynav.debug.inspector.api.DatabaseRegistry;
import com.allynav.debug.inspector.api.HttpReporter;
import com.allynav.debug.inspector.api.InspectorConfig;
import com.allynav.debug.inspector.api.SerialReporter;
import com.allynav.debug.inspector.api.WebSocketReporter;
import com.allynav.debug.inspector.core.InspectorCore;
import com.allynav.debug.inspector.ui.InspectorUi;

public final class DebugInspector {
    private DebugInspector() {
    }

    public static boolean initialize(Context context) {
        return initialize(context, InspectorConfig.builder().build());
    }

    public static boolean initialize(Context context, InspectorConfig config) {
        boolean initialized = InspectorCore.initialize(context, config);
        InspectorUi.install(context, config);
        return initialized;
    }

    public static boolean isInitialized() { return InspectorCore.isInitialized(); }
    public static void open(Context context) { InspectorUi.open(context); }
    public static HttpReporter http() { return InspectorCore.http(); }
    public static WebSocketReporter webSocket() { return InspectorCore.webSocket(); }
    public static SerialReporter serial() { return InspectorCore.serial(); }
    public static DatabaseRegistry databases() { return InspectorCore.databases(); }
    public static void pause() { InspectorCore.pause(); }
    public static void resume() { InspectorCore.resume(); }
    public static boolean isPaused() { return InspectorCore.isPaused(); }
    public static void clear() { InspectorCore.clear(); }
    public static long droppedEvents() { return InspectorCore.droppedEvents(); }
    public static boolean notificationPermissionGranted(Context context) { return InspectorUi.notificationPermissionGranted(context); }
}
