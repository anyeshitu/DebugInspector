package com.allynav.debug.inspector.api;

public final class InspectorDefaults {
    public static final long RETENTION_MILLIS = 24L * 60L * 60L * 1000L;
    public static final long MAX_STORE_BYTES = 100L * 1024L * 1024L;
    public static final int MAX_HTTP_BODY_BYTES = 250 * 1024;
    public static final int MAX_EVENT_PAYLOAD_BYTES = 64 * 1024;
    public static final int MAX_DATABASE_EXPORT_ROWS = 10_000;

    private InspectorDefaults() {
    }
}
