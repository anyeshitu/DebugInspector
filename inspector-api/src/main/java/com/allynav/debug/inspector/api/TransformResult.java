package com.allynav.debug.inspector.api;

public final class TransformResult {
    private final BodyData transformedBody;

    private TransformResult(BodyData transformedBody) {
        if (transformedBody == null) {
            throw new IllegalArgumentException("transformedBody is required");
        }
        this.transformedBody = transformedBody;
    }

    public static TransformResult of(BodyData body) {
        return new TransformResult(body);
    }

    public static TransformResult utf8(String text, String contentType) {
        return of(BodyData.utf8(text, contentType));
    }

    public BodyData getTransformedBody() {
        return transformedBody;
    }
}
