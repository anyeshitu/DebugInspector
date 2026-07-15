package com.allynav.debug.inspector.sample;

import android.util.Base64;

import com.allynav.debug.inspector.api.BodyData;
import com.allynav.debug.inspector.api.HttpBodyTransformer;
import com.allynav.debug.inspector.api.HttpTransformContext;
import com.allynav.debug.inspector.api.TransformResult;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

final class SampleCryptoTransformer implements HttpBodyTransformer {
    @Override
    public boolean supports(HttpTransformContext context) {
        return context.getContentType().contains("json");
    }

    @Override
    public TransformResult transform(HttpTransformContext context, BodyData rawBody) throws Exception {
        JSONObject source = new JSONObject(rawBody.asText());
        String field = context.getDirection() == HttpTransformContext.Direction.REQUEST ? "para" : "data";
        String encoded = source.optString(field);
        if (encoded.isEmpty()) return TransformResult.utf8(rawBody.asText(), "application/json");
        byte[] decoded = Base64.decode(encoded, Base64.NO_WRAP);
        return TransformResult.utf8(new String(decoded, StandardCharsets.UTF_8), "application/json");
    }
}
