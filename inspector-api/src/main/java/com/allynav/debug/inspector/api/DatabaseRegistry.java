package com.allynav.debug.inspector.api;

import java.util.List;

public interface DatabaseRegistry {
    void register(DatabaseRegistration registration);

    List<DatabaseRegistration> registrations();
}
