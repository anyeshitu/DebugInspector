package com.allynav.debug.inspector.api;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

public final class SerialEvent {
    public enum Direction { TX, RX, ERROR }

    private final String id;
    private final long timestampMillis;
    private final String portId;
    private final Direction direction;
    private final byte[] payload;
    private final String charsetName;
    private final Integer baudRate;
    private final String configuration;
    private final String error;
    private final String sessionId;
    private final String correlationId;

    private SerialEvent(Builder builder) {
        id = UUID.randomUUID().toString();
        timestampMillis = builder.timestampMillis == 0 ? System.currentTimeMillis() : builder.timestampMillis;
        portId = required(builder.portId, "portId");
        direction = builder.direction;
        payload = builder.payload == null ? new byte[0] : Arrays.copyOf(builder.payload, builder.payload.length);
        charsetName = builder.charsetName == null ? StandardCharsets.UTF_8.name() : builder.charsetName;
        baudRate = builder.baudRate;
        configuration = builder.configuration;
        error = builder.error;
        sessionId = builder.sessionId;
        correlationId = builder.correlationId;
    }

    private static String required(String value, String name) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(name + " is required");
        return value;
    }

    public String getId() { return id; }
    public long getTimestampMillis() { return timestampMillis; }
    public String getPortId() { return portId; }
    public Direction getDirection() { return direction; }
    public byte[] getPayload() { return Arrays.copyOf(payload, payload.length); }
    public String getCharsetName() { return charsetName; }
    public String payloadAsText() { try { return new String(payload, Charset.forName(charsetName)); } catch (Exception ignored) { return new String(payload, StandardCharsets.UTF_8); } }
    public Integer getBaudRate() { return baudRate; }
    public String getConfiguration() { return configuration; }
    public String getError() { return error; }
    public String getSessionId() { return sessionId; }
    public String getCorrelationId() { return correlationId; }

    public static Builder builder(String portId, Direction direction) { return new Builder(portId, direction); }

    public static final class Builder {
        private long timestampMillis;
        private final String portId;
        private final Direction direction;
        private byte[] payload;
        private String charsetName;
        private Integer baudRate;
        private String configuration;
        private String error;
        private String sessionId;
        private String correlationId;

        private Builder(String portId, Direction direction) {
            this.portId = portId;
            if (direction == null) throw new IllegalArgumentException("direction is required");
            this.direction = direction;
        }

        public Builder timestampMillis(long value) { timestampMillis = value; return this; }
        public Builder payload(byte[] value) { payload = value == null ? null : Arrays.copyOf(value, value.length); return this; }
        public Builder charsetName(String value) { charsetName = value; return this; }
        public Builder baudRate(Integer value) { baudRate = value; return this; }
        public Builder configuration(String value) { configuration = value; return this; }
        public Builder error(String value) { error = value; return this; }
        public Builder sessionId(String value) { sessionId = value; return this; }
        public Builder correlationId(String value) { correlationId = value; return this; }
        public SerialEvent build() { return new SerialEvent(this); }
    }
}
