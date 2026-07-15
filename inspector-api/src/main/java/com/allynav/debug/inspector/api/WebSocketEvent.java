package com.allynav.debug.inspector.api;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

public final class WebSocketEvent {
    public enum Type { CONNECTING, OPEN, MESSAGE, CLOSING, CLOSED, FAILURE }
    public enum Direction { NONE, SENT, RECEIVED }

    private final String id;
    private final long timestampMillis;
    private final String connectionId;
    private final Type type;
    private final Direction direction;
    private final byte[] payload;
    private final boolean text;
    private final int closeCode;
    private final String detail;
    private final String sessionId;
    private final String correlationId;

    private WebSocketEvent(Builder builder) {
        id = UUID.randomUUID().toString();
        timestampMillis = builder.timestampMillis == 0 ? System.currentTimeMillis() : builder.timestampMillis;
        connectionId = required(builder.connectionId, "connectionId");
        type = builder.type;
        direction = builder.direction;
        payload = builder.payload == null ? new byte[0] : Arrays.copyOf(builder.payload, builder.payload.length);
        text = builder.text;
        closeCode = builder.closeCode;
        detail = builder.detail;
        sessionId = builder.sessionId;
        correlationId = builder.correlationId;
    }

    private static String required(String value, String name) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(name + " is required");
        return value;
    }

    public String getId() { return id; }
    public long getTimestampMillis() { return timestampMillis; }
    public String getConnectionId() { return connectionId; }
    public Type getType() { return type; }
    public Direction getDirection() { return direction; }
    public byte[] getPayload() { return Arrays.copyOf(payload, payload.length); }
    public boolean isText() { return text; }
    public int getCloseCode() { return closeCode; }
    public String getDetail() { return detail; }
    public String getSessionId() { return sessionId; }
    public String getCorrelationId() { return correlationId; }

    public static Builder builder(String connectionId, Type type) { return new Builder(connectionId, type); }

    public static final class Builder {
        private long timestampMillis;
        private final String connectionId;
        private final Type type;
        private Direction direction = Direction.NONE;
        private byte[] payload;
        private boolean text;
        private int closeCode;
        private String detail;
        private String sessionId;
        private String correlationId;

        private Builder(String connectionId, Type type) {
            this.connectionId = connectionId;
            if (type == null) throw new IllegalArgumentException("type is required");
            this.type = type;
        }

        public Builder timestampMillis(long value) { timestampMillis = value; return this; }
        public Builder direction(Direction value) { direction = value == null ? Direction.NONE : value; return this; }
        public Builder textPayload(String value) { payload = value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8); text = true; return this; }
        public Builder binaryPayload(byte[] value) { payload = value == null ? null : Arrays.copyOf(value, value.length); text = false; return this; }
        public Builder closeCode(int value) { closeCode = value; return this; }
        public Builder detail(String value) { detail = value; return this; }
        public Builder sessionId(String value) { sessionId = value; return this; }
        public Builder correlationId(String value) { correlationId = value; return this; }
        public WebSocketEvent build() { return new WebSocketEvent(this); }
    }
}
