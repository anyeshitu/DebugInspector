package com.allynav.debug.inspector.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class HttpExchange {
    private final String id;
    private final long startedAtMillis;
    private final long durationMillis;
    private final String method;
    private final String url;
    private final List<HttpHeader> requestHeaders;
    private final List<HttpHeader> responseHeaders;
    private final BodyData requestRawBody;
    private final BodyData requestTransformedBody;
    private final BodyData responseRawBody;
    private final BodyData responseTransformedBody;
    private final int statusCode;
    private final String protocol;
    private final String error;
    private final String sessionId;
    private final String correlationId;
    private final Map<String, String> tags;

    private HttpExchange(Builder builder) {
        id = builder.id == null ? UUID.randomUUID().toString() : builder.id;
        startedAtMillis = builder.startedAtMillis == 0 ? System.currentTimeMillis() : builder.startedAtMillis;
        durationMillis = Math.max(0, builder.durationMillis);
        method = required(builder.method, "method");
        url = required(builder.url, "url");
        requestHeaders = immutable(builder.requestHeaders);
        responseHeaders = immutable(builder.responseHeaders);
        requestRawBody = builder.requestRawBody;
        requestTransformedBody = builder.requestTransformedBody;
        responseRawBody = builder.responseRawBody;
        responseTransformedBody = builder.responseTransformedBody;
        statusCode = builder.statusCode;
        protocol = builder.protocol == null ? "" : builder.protocol;
        error = builder.error;
        sessionId = builder.sessionId;
        correlationId = builder.correlationId;
        tags = Collections.unmodifiableMap(new LinkedHashMap<>(builder.tags));
    }

    private static String required(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    private static List<HttpHeader> immutable(List<HttpHeader> value) {
        return Collections.unmodifiableList(new ArrayList<>(value));
    }

    public String getId() { return id; }
    public long getStartedAtMillis() { return startedAtMillis; }
    public long getDurationMillis() { return durationMillis; }
    public String getMethod() { return method; }
    public String getUrl() { return url; }
    public List<HttpHeader> getRequestHeaders() { return requestHeaders; }
    public List<HttpHeader> getResponseHeaders() { return responseHeaders; }
    public BodyData getRequestRawBody() { return requestRawBody; }
    public BodyData getRequestTransformedBody() { return requestTransformedBody; }
    public BodyData getResponseRawBody() { return responseRawBody; }
    public BodyData getResponseTransformedBody() { return responseTransformedBody; }
    public int getStatusCode() { return statusCode; }
    public String getProtocol() { return protocol; }
    public String getError() { return error; }
    public String getSessionId() { return sessionId; }
    public String getCorrelationId() { return correlationId; }
    public Map<String, String> getTags() { return tags; }

    public static Builder builder(String method, String url) {
        return new Builder(method, url);
    }

    public static final class Builder {
        private String id;
        private long startedAtMillis;
        private long durationMillis;
        private final String method;
        private final String url;
        private final List<HttpHeader> requestHeaders = new ArrayList<>();
        private final List<HttpHeader> responseHeaders = new ArrayList<>();
        private BodyData requestRawBody;
        private BodyData requestTransformedBody;
        private BodyData responseRawBody;
        private BodyData responseTransformedBody;
        private int statusCode = -1;
        private String protocol;
        private String error;
        private String sessionId;
        private String correlationId;
        private final Map<String, String> tags = new LinkedHashMap<>();

        private Builder(String method, String url) {
            this.method = method;
            this.url = url;
        }

        public Builder id(String value) { id = value; return this; }
        public Builder startedAtMillis(long value) { startedAtMillis = value; return this; }
        public Builder durationMillis(long value) { durationMillis = value; return this; }
        public Builder requestHeaders(List<HttpHeader> value) { requestHeaders.clear(); if (value != null) requestHeaders.addAll(value); return this; }
        public Builder responseHeaders(List<HttpHeader> value) { responseHeaders.clear(); if (value != null) responseHeaders.addAll(value); return this; }
        public Builder requestRawBody(BodyData value) { requestRawBody = value; return this; }
        public Builder requestTransformedBody(BodyData value) { requestTransformedBody = value; return this; }
        public Builder responseRawBody(BodyData value) { responseRawBody = value; return this; }
        public Builder responseTransformedBody(BodyData value) { responseTransformedBody = value; return this; }
        public Builder statusCode(int value) { statusCode = value; return this; }
        public Builder protocol(String value) { protocol = value; return this; }
        public Builder error(String value) { error = value; return this; }
        public Builder sessionId(String value) { sessionId = value; return this; }
        public Builder correlationId(String value) { correlationId = value; return this; }
        public Builder tag(String key, String value) { if (key != null && value != null) tags.put(key, value); return this; }
        public HttpExchange build() { return new HttpExchange(this); }
    }
}
