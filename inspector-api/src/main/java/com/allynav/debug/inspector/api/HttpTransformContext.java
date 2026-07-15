package com.allynav.debug.inspector.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class HttpTransformContext {
    public enum Direction { REQUEST, RESPONSE }

    private final Direction direction;
    private final String method;
    private final String url;
    private final int statusCode;
    private final List<HttpHeader> headers;
    private final String contentType;
    private final Object hostTag;

    public HttpTransformContext(Direction direction, String method, String url, int statusCode,
                                List<HttpHeader> headers, String contentType, Object hostTag) {
        this.direction = direction;
        this.method = method == null ? "" : method;
        this.url = url == null ? "" : url;
        this.statusCode = statusCode;
        this.headers = Collections.unmodifiableList(new ArrayList<>(headers == null
                ? Collections.<HttpHeader>emptyList() : headers));
        this.contentType = contentType == null ? "" : contentType;
        this.hostTag = hostTag;
    }

    public Direction getDirection() { return direction; }
    public String getMethod() { return method; }
    public String getUrl() { return url; }
    public int getStatusCode() { return statusCode; }
    public List<HttpHeader> getHeaders() { return headers; }
    public String getContentType() { return contentType; }
    public Object getHostTag() { return hostTag; }
}
