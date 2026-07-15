package com.allynav.debug.inspector.api;

public final class RetentionPolicy {
    private final long retentionMillis;
    private final long maxStoreBytes;
    private final int maxHttpBodyBytes;
    private final int maxEventPayloadBytes;
    private final int maxDatabaseExportRows;

    private RetentionPolicy(Builder builder) {
        retentionMillis = positive(builder.retentionMillis, "retentionMillis");
        maxStoreBytes = positive(builder.maxStoreBytes, "maxStoreBytes");
        maxHttpBodyBytes = positive(builder.maxHttpBodyBytes, "maxHttpBodyBytes");
        maxEventPayloadBytes = positive(builder.maxEventPayloadBytes, "maxEventPayloadBytes");
        maxDatabaseExportRows = positive(builder.maxDatabaseExportRows, "maxDatabaseExportRows");
    }

    private static long positive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be greater than zero");
        }
        return value;
    }

    private static int positive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be greater than zero");
        }
        return value;
    }

    public long getRetentionMillis() {
        return retentionMillis;
    }

    public long getMaxStoreBytes() {
        return maxStoreBytes;
    }

    public int getMaxHttpBodyBytes() {
        return maxHttpBodyBytes;
    }

    public int getMaxEventPayloadBytes() {
        return maxEventPayloadBytes;
    }

    public int getMaxDatabaseExportRows() {
        return maxDatabaseExportRows;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private long retentionMillis = InspectorDefaults.RETENTION_MILLIS;
        private long maxStoreBytes = InspectorDefaults.MAX_STORE_BYTES;
        private int maxHttpBodyBytes = InspectorDefaults.MAX_HTTP_BODY_BYTES;
        private int maxEventPayloadBytes = InspectorDefaults.MAX_EVENT_PAYLOAD_BYTES;
        private int maxDatabaseExportRows = InspectorDefaults.MAX_DATABASE_EXPORT_ROWS;

        public Builder retentionMillis(long value) {
            retentionMillis = value;
            return this;
        }

        public Builder maxStoreBytes(long value) {
            maxStoreBytes = value;
            return this;
        }

        public Builder maxHttpBodyBytes(int value) {
            maxHttpBodyBytes = value;
            return this;
        }

        public Builder maxEventPayloadBytes(int value) {
            maxEventPayloadBytes = value;
            return this;
        }

        public Builder maxDatabaseExportRows(int value) {
            maxDatabaseExportRows = value;
            return this;
        }

        public RetentionPolicy build() {
            return new RetentionPolicy(this);
        }
    }
}
