package com.allynav.debug.inspector.api;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class BodyData {
    private final byte[] bytes;
    private final String contentType;
    private final String charsetName;
    private final long originalLength;
    private final boolean truncated;
    private final String transformError;

    private BodyData(Builder builder) {
        bytes = builder.bytes == null ? new byte[0] : Arrays.copyOf(builder.bytes, builder.bytes.length);
        contentType = builder.contentType == null ? "" : builder.contentType;
        charsetName = builder.charsetName == null ? StandardCharsets.UTF_8.name() : builder.charsetName;
        originalLength = builder.originalLength < 0 ? bytes.length : builder.originalLength;
        truncated = builder.truncated || originalLength > bytes.length;
        transformError = builder.transformError;
    }

    public byte[] getBytes() {
        return Arrays.copyOf(bytes, bytes.length);
    }

    public String asText() {
        try {
            return new String(bytes, Charset.forName(charsetName));
        } catch (Exception ignored) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    public String getContentType() {
        return contentType;
    }

    public String getCharsetName() {
        return charsetName;
    }

    public long getOriginalLength() {
        return originalLength;
    }

    public boolean isTruncated() {
        return truncated;
    }

    public String getTransformError() {
        return transformError;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static BodyData utf8(String text, String contentType) {
        return builder().bytes(text == null ? new byte[0] : text.getBytes(StandardCharsets.UTF_8))
                .contentType(contentType).charsetName(StandardCharsets.UTF_8.name()).build();
    }

    public static final class Builder {
        private byte[] bytes;
        private String contentType;
        private String charsetName;
        private long originalLength = -1;
        private boolean truncated;
        private String transformError;

        public Builder bytes(byte[] value) {
            bytes = value == null ? null : Arrays.copyOf(value, value.length);
            return this;
        }

        public Builder contentType(String value) {
            contentType = value;
            return this;
        }

        public Builder charsetName(String value) {
            charsetName = value;
            return this;
        }

        public Builder originalLength(long value) {
            originalLength = value;
            return this;
        }

        public Builder truncated(boolean value) {
            truncated = value;
            return this;
        }

        public Builder transformError(String value) {
            transformError = value;
            return this;
        }

        public BodyData build() {
            return new BodyData(this);
        }
    }
}
