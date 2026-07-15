package com.allynav.debug.inspector.core;

import com.allynav.debug.inspector.api.BodyData;
import com.allynav.debug.inspector.api.HttpHeader;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class DataCodec {
    static final String REDACTED = "**";

    private DataCodec() {
    }

    static String headersToJson(List<HttpHeader> headers, Set<String> redactedNames) {
        JSONArray array = new JSONArray();
        for (HttpHeader header : headers == null ? Collections.<HttpHeader>emptyList() : headers) {
            JSONObject item = new JSONObject();
            try {
                item.put("name", header.getName());
                String key = header.getName().toLowerCase(Locale.US);
                item.put("value", redactedNames.contains(key) ? REDACTED : header.getValue());
                array.put(item);
            } catch (JSONException ignored) {
                // JSONObject values above are strings and should not fail.
            }
        }
        return array.toString();
    }

    static String tagsToJson(Map<String, String> tags) {
        JSONObject object = new JSONObject();
        if (tags != null) {
            for (Map.Entry<String, String> entry : tags.entrySet()) {
                try {
                    object.put(entry.getKey(), entry.getValue());
                } catch (JSONException ignored) {
                    // String values are supported by JSONObject.
                }
            }
        }
        return object.toString();
    }

    static BodyData truncate(BodyData body, int maxBytes) {
        if (body == null) {
            return null;
        }
        byte[] input = body.getBytes();
        if (input.length <= maxBytes) {
            return body;
        }
        return BodyData.builder()
                .bytes(Arrays.copyOf(input, maxBytes))
                .contentType(body.getContentType())
                .charsetName(body.getCharsetName())
                .originalLength(Math.max(body.getOriginalLength(), input.length))
                .truncated(true)
                .transformError(body.getTransformError())
                .build();
    }

    static byte[] truncate(byte[] bytes, int maxBytes) {
        if (bytes == null) {
            return new byte[0];
        }
        return bytes.length <= maxBytes ? Arrays.copyOf(bytes, bytes.length) : Arrays.copyOf(bytes, maxBytes);
    }

    static String decode(byte[] bytes, String charsetName) {
        try {
            return new String(bytes == null ? new byte[0] : bytes, Charset.forName(charsetName));
        } catch (Exception ignored) {
            return new String(bytes == null ? new byte[0] : bytes, StandardCharsets.UTF_8);
        }
    }

    static String hex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder(bytes.length * 3 - 1);
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) builder.append(' ');
            builder.append(String.format(Locale.US, "%02X", bytes[i] & 0xFF));
        }
        return builder.toString();
    }
}
