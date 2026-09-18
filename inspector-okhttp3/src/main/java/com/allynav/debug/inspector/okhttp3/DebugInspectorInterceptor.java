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
import java.util.Locale;
import java.util.regex.Pattern;

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
        if (shouldSkip(request, config)) {
            // 忽略规则只跳过采集，必须原样返回宿主网络结果。
            return chain.proceed(request);
        }
        int limit = config.getRetentionPolicy().getMaxHttpBodyBytes();
        List<HttpHeader> requestHeaders = headers(request.headers());
        BodyData requestRaw = isOneShotOrDuplex(request.body()) ? null : captureRequestBody(request.body(), limit);
        BodyData requestPlain = transform(config, request, null, requestHeaders,
                requestRaw, HttpTransformContext.Direction.REQUEST);

        try {
            Response response = chain.proceed(request);
            List<HttpHeader> responseHeaders = headers(response.headers());
            BodyData responseRaw = captureResponseBody(response, limit, config.isAlwaysReadResponseBody());
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

    private static BodyData captureResponseBody(Response response, int limit, boolean alwaysReadResponseBody) {
        ResponseBody body = response.body();
        if (body == null) return null;
        try {
            // OkHttp 3.4.1 的 peekBody 不消费宿主响应；即使宿主未读取响应，也能安全保留受限副本。
            // alwaysReadResponseBody 保留为兼容配置入口，仍严格遵守单条正文大小上限。
            // 开启兼容模式时主动读到 EOF；默认仍只读取上限加一字节，避免无界内存占用。
            long peekLimit = alwaysReadResponseBody ? Long.MAX_VALUE : (long) limit + 1L;
            ResponseBody peeked = response.peekBody(peekLimit);
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

    private static boolean shouldSkip(Request request, InspectorConfig config) {
        String path = request.url().encodedPath();
        if (path == null || path.isEmpty()) path = "/";
        for (String skipped : config.getSkippedPaths()) {
            if (path.equals(skipped)) return true;
        }
        for (Pattern pattern : config.getSkippedPathPatterns()) {
            if (pattern.matcher(path).matches()) return true;
        }

        String host = request.url().host();
        if (host == null) host = "";
        host = host.toLowerCase(Locale.US);
        if (config.getSkippedDomains().contains(host)) return true;
        for (Pattern pattern : config.getSkippedDomainPatterns()) {
            if (pattern.matcher(host).matches()) return true;
        }
        return false;
    }

    private static boolean isOneShotOrDuplex(RequestBody body) {
        if (body == null) return false;
        // 兼容新旧 OkHttp：3.4.1 没有这些方法，宿主升级后通过反射避免消费一次性正文。
        return invokeBoolean(body, "isOneShot") || invokeBoolean(body, "isDuplex");
    }

    private static boolean invokeBoolean(RequestBody body, String methodName) {
        try {
            java.lang.reflect.Method method = body.getClass().getMethod(methodName);
            Object value = method.invoke(body);
            return value instanceof Boolean && (Boolean) value;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static BodyData transform(InspectorConfig config, Request request, Response response,
                                      List<HttpHeader> headers, BodyData raw,
                                      HttpTransformContext.Direction direction) {
        if (raw == null) return null;
        // Retrofit 2.11 将 Invocation 放在 OkHttp 的类型化 tag 中；同时兼容旧版 OkHttp 的无类型 tag。
        HttpTransformContext context = new HttpTransformContext(direction, request.method(),
                request.url().toString(), response == null ? -1 : response.code(), headers,
                raw.getContentType(), hostTag(request));
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

    private static Object hostTag(Request request) {
        try {
            Class<?> invocationClass = Class.forName("retrofit2.Invocation");
            java.lang.reflect.Method typedTag = request.getClass().getMethod("tag", Class.class);
            return typedTag.invoke(request, invocationClass);
        } catch (Exception ignored) {
            // OkHttp 3.4 只有无类型 tag；旧宿主仍可通过该路径提供上下文。
            return request.tag();
        }
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
