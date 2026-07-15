package com.allynav.debug.inspector;

import android.content.Context;

import com.allynav.debug.inspector.api.DatabaseRegistration;
import com.allynav.debug.inspector.api.DatabaseRegistry;
import com.allynav.debug.inspector.api.HttpExchange;
import com.allynav.debug.inspector.api.HttpReporter;
import com.allynav.debug.inspector.api.InspectorConfig;
import com.allynav.debug.inspector.api.SerialEvent;
import com.allynav.debug.inspector.api.SerialReporter;
import com.allynav.debug.inspector.api.WebSocketEvent;
import com.allynav.debug.inspector.api.WebSocketReporter;

import java.util.Collections;
import java.util.List;

public final class DebugInspector {
    private static final HttpReporter HTTP = new HttpReporter() { @Override public void report(HttpExchange exchange) { } };
    private static final WebSocketReporter WEB_SOCKET = new WebSocketReporter() { @Override public void report(WebSocketEvent event) { } };
    private static final SerialReporter SERIAL = new SerialReporter() { @Override public void report(SerialEvent event) { } };
    private static final DatabaseRegistry DATABASES = new DatabaseRegistry() {
        @Override public void register(DatabaseRegistration registration) { }
        @Override public List<DatabaseRegistration> registrations() { return Collections.emptyList(); }
    };

    private DebugInspector() {
    }

    public static boolean initialize(Context context) { return false; }
    public static boolean initialize(Context context, InspectorConfig config) { return false; }
    public static boolean isInitialized() { return false; }
    public static void open(Context context) { }
    public static HttpReporter http() { return HTTP; }
    public static WebSocketReporter webSocket() { return WEB_SOCKET; }
    public static SerialReporter serial() { return SERIAL; }
    public static DatabaseRegistry databases() { return DATABASES; }
    public static void pause() { }
    public static void resume() { }
    public static boolean isPaused() { return false; }
    public static void clear() { }
    public static long droppedEvents() { return 0; }
    public static boolean notificationPermissionGranted(Context context) { return false; }
}
