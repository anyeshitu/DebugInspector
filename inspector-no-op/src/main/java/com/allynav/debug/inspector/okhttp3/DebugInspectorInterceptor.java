package com.allynav.debug.inspector.okhttp3;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Response;

public final class DebugInspectorInterceptor implements Interceptor {
    public DebugInspectorInterceptor() {
    }

    public DebugInspectorInterceptor(String sessionId, String correlationId) {
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        return chain.proceed(chain.request());
    }
}
