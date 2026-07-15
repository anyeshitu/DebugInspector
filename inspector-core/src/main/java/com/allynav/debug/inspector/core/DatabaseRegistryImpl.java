package com.allynav.debug.inspector.core;

import com.allynav.debug.inspector.api.DatabaseRegistration;
import com.allynav.debug.inspector.api.DatabaseRegistry;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class DatabaseRegistryImpl implements DatabaseRegistry {
    private final Map<String, DatabaseRegistration> registrations = new LinkedHashMap<>();

    @Override
    public synchronized void register(DatabaseRegistration registration) {
        if (registration == null) throw new IllegalArgumentException("registration is required");
        registrations.put(canonical(registration.getAbsolutePath()), registration);
    }

    @Override
    public synchronized List<DatabaseRegistration> registrations() {
        return Collections.unmodifiableList(new ArrayList<>(registrations.values()));
    }

    private static String canonical(String path) {
        try {
            return new File(path).getCanonicalPath();
        } catch (IOException ignored) {
            return new File(path).getAbsolutePath();
        }
    }
}
