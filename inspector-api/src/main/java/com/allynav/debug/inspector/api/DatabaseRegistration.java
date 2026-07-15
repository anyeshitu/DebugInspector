package com.allynav.debug.inspector.api;

import java.io.File;

public final class DatabaseRegistration {
    private final String displayName;
    private final String absolutePath;

    public DatabaseRegistration(String displayName, String path) {
        if (displayName == null || displayName.trim().isEmpty()) {
            throw new IllegalArgumentException("displayName is required");
        }
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("path is required");
        }
        this.displayName = displayName.trim();
        absolutePath = new File(path).getAbsolutePath();
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getAbsolutePath() {
        return absolutePath;
    }
}
