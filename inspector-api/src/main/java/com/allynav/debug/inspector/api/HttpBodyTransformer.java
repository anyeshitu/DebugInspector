package com.allynav.debug.inspector.api;

public interface HttpBodyTransformer {
    boolean supports(HttpTransformContext context);

    TransformResult transform(HttpTransformContext context, BodyData rawBody) throws Exception;
}
