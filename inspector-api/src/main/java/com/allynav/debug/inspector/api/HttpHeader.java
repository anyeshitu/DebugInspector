package com.allynav.debug.inspector.api;

public final class HttpHeader {
    private final String name;
    private final String value;

    public HttpHeader(String name, String value) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("name is required");
        }
        this.name = name;
        this.value = value == null ? "" : value;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }
}
