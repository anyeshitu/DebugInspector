package com.allynav.debug.inspector.okhttp3;

import org.junit.Test;

import java.io.IOException;

import okhttp3.Connection;
import okhttp3.Interceptor;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;

import static org.junit.Assert.assertSame;

public final class DebugInspectorInterceptorTest {
    @Test
    public void uninitializedRuntimePassesThroughWithoutChanges() throws Exception {
        Request request = new Request.Builder().url("https://example.test/").build();
        Response expected = new Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(204).message("No Content").build();
        Interceptor.Chain chain = new Interceptor.Chain() {
            @Override public Request request() { return request; }
            @Override public Response proceed(Request ignored) { return expected; }
            @Override public Connection connection() { return null; }
        };
        assertSame(expected, new DebugInspectorInterceptor().intercept(chain));
    }
}
