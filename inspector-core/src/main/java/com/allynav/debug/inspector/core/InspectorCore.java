package com.allynav.debug.inspector.core;

import android.content.Context;

import com.allynav.debug.inspector.api.DatabaseRegistration;
import com.allynav.debug.inspector.api.DatabaseRegistry;
import com.allynav.debug.inspector.api.HttpExchange;
import com.allynav.debug.inspector.api.HttpReporter;
import com.allynav.debug.inspector.api.InspectorConfig;
import com.allynav.debug.inspector.api.SerialEvent;
import com.allynav.debug.inspector.api.SerialReporter;
import com.allynav.debug.inspector.api.WebSocketEvent;
import com.allynav.debug.inspector.api.WebSocketReporter;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class InspectorCore {
    private static volatile Runtime runtime;

    private InspectorCore() {
    }

    public static synchronized boolean initialize(Context context, InspectorConfig config) {
        if (context == null) throw new IllegalArgumentException("context is required");
        if (config == null) throw new IllegalArgumentException("config is required");
        if (runtime != null) return false;
        runtime = new Runtime(context.getApplicationContext(), config);
        return true;
    }

    public static boolean isInitialized() {
        return runtime != null;
    }

    public static InspectorConfig config() {
        Runtime current = runtime;
        return current == null ? InspectorConfig.builder().captureEnabled(false).build() : current.config;
    }

    public static HttpReporter http() {
        Runtime current = runtime;
        return current == null ? Disabled.HTTP : current.httpReporter;
    }

    public static WebSocketReporter webSocket() {
        Runtime current = runtime;
        return current == null ? Disabled.WEB_SOCKET : current.webSocketReporter;
    }

    public static SerialReporter serial() {
        Runtime current = runtime;
        return current == null ? Disabled.SERIAL : current.serialReporter;
    }

    public static DatabaseRegistry databases() {
        Runtime current = runtime;
        return current == null ? Disabled.DATABASES : current.registry;
    }

    public static InspectorRepository repository() {
        Runtime current = requireRuntime();
        return current.repository;
    }

    public static DatabaseInspector databaseInspector() {
        return requireRuntime().databaseInspector;
    }

    public static void pause() {
        Runtime current = runtime;
        if (current != null) current.paused.set(true);
    }

    public static void resume() {
        Runtime current = runtime;
        if (current != null) current.paused.set(false);
    }

    public static boolean isPaused() {
        Runtime current = runtime;
        return current != null && current.paused.get();
    }

    public static void clear() {
        Runtime current = runtime;
        if (current != null) current.submit(current.store::clear);
    }

    public static long droppedEvents() {
        Runtime current = runtime;
        return current == null ? 0 : current.dropped.get();
    }

    private static Runtime requireRuntime() {
        Runtime current = runtime;
        if (current == null) throw new IllegalStateException("DebugInspector is not initialized");
        return current;
    }

    private static final class Runtime {
        final InspectorConfig config;
        final InspectorStore store;
        final InspectorRepository repository;
        final DatabaseRegistryImpl registry = new DatabaseRegistryImpl();
        final DatabaseInspector databaseInspector;
        final AtomicBoolean paused;
        final AtomicLong dropped = new AtomicLong();
        final ThreadPoolExecutor writer;
        final HttpReporter httpReporter;
        final WebSocketReporter webSocketReporter;
        final SerialReporter serialReporter;

        Runtime(Context context, InspectorConfig config) {
            this.config = config;
            paused = new AtomicBoolean(!config.isCaptureEnabled());
            InspectorDatabaseHelper helper = new InspectorDatabaseHelper(context);
            store = new InspectorStore(helper, config);
            repository = new InspectorRepository(helper);
            httpReporter = exchange -> {
                if (exchange != null && active()) submit(() -> store.insertHttp(exchange));
            };
            webSocketReporter = event -> {
                if (event != null && active()) submit(() -> store.insertWebSocket(event));
            };
            serialReporter = event -> {
                if (event != null && active()) submit(() -> store.insertSerial(event));
            };
            for (DatabaseRegistration registration : config.getDatabases()) registry.register(registration);
            databaseInspector = new DatabaseInspector(context, registry);
            writer = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<>(512), new WriterThreadFactory());
            submit(store::cleanup);
        }

        boolean active() {
            return !paused.get();
        }

        void submit(Runnable runnable) {
            try {
                writer.execute(() -> {
                    try { runnable.run(); }
                    catch (RuntimeException ignored) { dropped.incrementAndGet(); }
                });
            } catch (RejectedExecutionException ignored) {
                dropped.incrementAndGet();
            }
        }
    }

    private static final class WriterThreadFactory implements ThreadFactory {
        @Override public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "DebugInspector-Writer");
            thread.setDaemon(true);
            return thread;
        }
    }

    private static final class Disabled {
        static final HttpReporter HTTP = new HttpReporter() { @Override public void report(HttpExchange exchange) { } };
        static final WebSocketReporter WEB_SOCKET = new WebSocketReporter() { @Override public void report(WebSocketEvent event) { } };
        static final SerialReporter SERIAL = new SerialReporter() { @Override public void report(SerialEvent event) { } };
        static final DatabaseRegistry DATABASES = new DatabaseRegistry() {
            @Override public void register(DatabaseRegistration registration) { }
            @Override public List<DatabaseRegistration> registrations() { return Collections.emptyList(); }
        };
    }
}
