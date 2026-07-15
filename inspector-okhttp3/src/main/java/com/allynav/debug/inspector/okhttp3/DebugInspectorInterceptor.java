package com.allynav.debug.inspector.okhttp3;

import com.allynav.debug.inspector.api.BodyData;
import com.allynav.debug.inspector.api.HttpBodyTransformer;
import com.allynav.debug.inspector.api.HttpExchange;
import com.allynav.debug.inspector.api.HttpHeader;
import com.allynav.debug.inspector.api.HttpTransformContext;
import com.allynav.debug.inspector.api.InspectorConfig;
import com.allynav.debug.inspector.api.TransformResult;
import com.allynav.debug.inspector.core.InspectorCore;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Headers;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSink;
import okio.Okio;
import okio.Sink;
import okio.Timeout;

public final class DebugInspectorInterceptor implements Interceptor {
    private final String sessionId;
    private final String correlationId;

    public DebugInspectorInterceptor() {
        this(null, null);
    }

    public DebugInspectorInterceptor(String sessionId, String correlationId) {
        this.sessionId = sessionId;
        this.correlationId = correlationId;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        if (!InspectorCore.isInitialized() || InspectorCore.isPaused()) {
            return chain.proceed(request);
        }

        long startedAt = System.currentTimeMillis();
        InspectorConfig config = InspectorCore.config();
        int limit = config.getRetentionPolicy().getMaxHttpBodyBytes();
        List<HttpHeader> requestHeaders = headers(request.headers());
        BodyData requestRaw = captureRequestBody(request.body(), limit);
        BodyData requestPlain = transform(config, request, null, requestHeaders,
                requestRaw, HttpTransformContext.Direction.REQUEST);

        try {
            Response response = chain.proceed(request);
            List<HttpHeader> responseHeaders = headers(response.headers());
            BodyData responseRaw = captureResponseBody(response, limit);
            BodyData responsePlain = transform(config, request, response, responseHeaders,
                    responseRaw, HttpTransformContext.Direction.RESPONSE);
            report(request, response, startedAt, requestHeaders, responseHeaders,
                    requestRaw, requestPlain, responseRaw, responsePlain, null);
            return response;
        } catch (IOException error) {
            report(request, null, startedAt, requestHeaders, new ArrayList<HttpHeader>(),
                    requestRaw, requestPlain, null, null, error.toString());
            throw error;
        } catch (RuntimeException error) {
            report(request, null, startedAt, requestHeaders, new ArrayList<HttpHeader>(),
                    requestRaw, requestPlain, null, null, error.toString());
            throw error;
        }
    }

    private void report(Request request, Response response, long startedAt,
                        List<HttpHeader> requestHeaders, List<HttpHeader> responseHeaders,
                        BodyData requestRaw, BodyData requestPlain,
                        BodyData responseRaw, BodyData responsePlain, String error) {
        try {
            HttpExchange.Builder builder = HttpExchange.builder(request.method(), request.url().toString())
                    .startedAtMillis(startedAt)
                    .durationMillis(System.currentTimeMillis() - startedAt)
                    .requestHeaders(requestHeaders)
                    .responseHeaders(responseHeaders)
                    .requestRawBody(requestRaw)
                    .requestTransformedBody(requestPlain)
                    .responseRawBody(responseRaw)
                    .responseTransformedBody(responsePlain)
                    .sessionId(sessionId)
                    .correlationId(correlationId)
                    .error(error);
            if (response != null) {
                builder.statusCode(response.code()).protocol(String.valueOf(response.protocol()));
            }
            InspectorCore.http().report(builder.build());
        } catch (RuntimeException ignored) {
            // Capture must never replace the host network result.
        }
    }

    private static BodyData captureRequestBody(RequestBody body, int limit) {
        if (body == null) return null;
        MediaType mediaType = body.contentType();
        LimitingSink sink = new LimitingSink(limit);
        BufferedSink bufferedSink = Okio.buffer(sink);
        String error = null;
        try {
            body.writeTo(bufferedSink);
            bufferedSink.flush();
        } catch (Exception exception) {
            error = exception.toString();
        } finally {
            try { bufferedSink.close(); } catch (IOException ignored) { }
        }
        return body(sink.bytes(), mediaType, sink.totalBytes(), sink.totalBytes() > limit, error);
    }

    private static BodyData captureResponseBody(Response response, int limit) {
        ResponseBody body = response.body();
        if (body == null) return null;
        try {
            ResponseBody peeked = response.peekBody((long) limit + 1L);
            byte[] bytes = peeked.bytes();
            long declared = body.contentLength();
            long original = declared >= 0 ? declared : bytes.length;
            boolean truncated = bytes.length > limit || original > limit;
            if (bytes.length > limit) {
                byte[] limited = new byte[limit];
                System.arraycopy(bytes, 0, limited, 0, limit);
                bytes = limited;
            }
            return body(bytes, body.contentType(), original, truncated, null);
        } catch (Exception error) {
            return body(new byte[0], body.contentType(), Math.max(0, body.contentLength()), false, error.toString());
        }
    }

    private static BodyData transform(InspectorConfig config, Request request, Response response,
                                      List<HttpHeader> headers, BodyData raw,
                                      HttpTransformContext.Direction direction) {
        if (raw == null) return null;
        HttpTransformContext context = new HttpTransformContext(direction, request.method(),
                request.url().toString(), response == null ? -1 : response.code(), headers,
                raw.getContentType(), request.tag());
        for (HttpBodyTransformer transformer : config.getBodyTransformers()) {
            try {
                if (!transformer.supports(context)) continue;
                TransformResult result = transformer.transform(context, raw);
                return result == null ? null : result.getTransformedBody();
            } catch (Exception error) {
                return BodyData.builder().bytes(new byte[0]).contentType(raw.getContentType())
                        .charsetName(raw.getCharsetName()).transformError(error.toString()).build();
            }
        }
        return null;
    }

    private static BodyData body(byte[] bytes, MediaType mediaType, long originalLength,
                                 boolean truncated, String error) {
        Charset charset = StandardCharsets.UTF_8;
        if (mediaType != null) {
            try { charset = mediaType.charset(StandardCharsets.UTF_8); }
            catch (Exception ignored) { }
        }
        return BodyData.builder().bytes(bytes)
                .contentType(mediaType == null ? "" : mediaType.toString())
                .charsetName(charset.name()).originalLength(originalLength)
                .truncated(truncated).transformError(error).build();
    }

    private static List<HttpHeader> headers(Headers source) {
        List<HttpHeader> result = new ArrayList<>(source.size());
        for (int i = 0; i < source.size(); i++) result.add(new HttpHeader(source.name(i), source.value(i)));
        return result;
    }

    private static final class LimitingSink implements Sink {
        private final Buffer buffer = new Buffer();
        private final long maxBytes;
        private long totalBytes;

        LimitingSink(long maxBytes) {
            this.maxBytes = maxBytes;
        }

        @Override
        public void write(Buffer source, long byteCount) throws IOException {
            long remaining = Math.max(0, maxBytes - buffer.size());
            long copy = Math.min(remaining, byteCount);
            if (copy > 0) buffer.write(source, copy);
            long discard = byteCount - copy;
            if (discard > 0) source.skip(discard);
            totalBytes += byteCount;
        }

        byte[] bytes() {
            return buffer.clone().readByteArray();
        }

        long totalBytes() {
            return totalBytes;
        }

        @Override public void flush() { }
        @Override public Timeout timeout() { return Timeout.NONE; }
        @Override public void close() { }
    }
}
