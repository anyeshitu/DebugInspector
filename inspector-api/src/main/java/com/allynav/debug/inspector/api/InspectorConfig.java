package com.allynav.debug.inspector.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class InspectorConfig {
    private final RetentionPolicy retentionPolicy;
    private final EntryConfig entryConfig;
    private final Set<String> redactedHeaderNames;
    private final List<HttpBodyTransformer> bodyTransformers;
    private final List<DatabaseRegistration> databases;
    private final boolean captureEnabled;
    private final UiLanguage uiLanguage;

    private InspectorConfig(Builder builder) {
        retentionPolicy = builder.retentionPolicy;
        entryConfig = builder.entryConfig;
        redactedHeaderNames = Collections.unmodifiableSet(new LinkedHashSet<>(builder.redactedHeaderNames));
        bodyTransformers = Collections.unmodifiableList(new ArrayList<>(builder.bodyTransformers));
        databases = Collections.unmodifiableList(new ArrayList<>(builder.databases));
        captureEnabled = builder.captureEnabled;
        uiLanguage = builder.uiLanguage;
    }

    public RetentionPolicy getRetentionPolicy() {
        return retentionPolicy;
    }

    public EntryConfig getEntryConfig() {
        return entryConfig;
    }

    public Set<String> getRedactedHeaderNames() {
        return redactedHeaderNames;
    }

    public List<HttpBodyTransformer> getBodyTransformers() {
        return bodyTransformers;
    }

    public List<DatabaseRegistration> getDatabases() {
        return databases;
    }

    public boolean isCaptureEnabled() {
        return captureEnabled;
    }

    public UiLanguage getUiLanguage() {
        return uiLanguage;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private RetentionPolicy retentionPolicy = RetentionPolicy.builder().build();
        private EntryConfig entryConfig = EntryConfig.builder().build();
        private final Set<String> redactedHeaderNames = new LinkedHashSet<>();
        private final List<HttpBodyTransformer> bodyTransformers = new ArrayList<>();
        private final List<DatabaseRegistration> databases = new ArrayList<>();
        private boolean captureEnabled = true;
        private UiLanguage uiLanguage = UiLanguage.SIMPLIFIED_CHINESE;

        public Builder retentionPolicy(RetentionPolicy policy) {
            retentionPolicy = require(policy, "retentionPolicy");
            return this;
        }

        public Builder entryConfig(EntryConfig config) {
            entryConfig = require(config, "entryConfig");
            return this;
        }

        public Builder redactHeaders(String... names) {
            if (names != null) {
                for (String name : names) {
                    if (name != null && !name.trim().isEmpty()) {
                        redactedHeaderNames.add(name.trim().toLowerCase(Locale.US));
                    }
                }
            }
            return this;
        }

        public Builder addBodyTransformer(HttpBodyTransformer transformer) {
            bodyTransformers.add(require(transformer, "transformer"));
            return this;
        }

        public Builder addDatabase(DatabaseRegistration registration) {
            databases.add(require(registration, "registration"));
            return this;
        }

        public Builder captureEnabled(boolean enabled) {
            captureEnabled = enabled;
            return this;
        }

        public Builder uiLanguage(UiLanguage language) {
            uiLanguage = require(language, "uiLanguage");
            return this;
        }

        public InspectorConfig build() {
            return new InspectorConfig(this);
        }

        private static <T> T require(T value, String name) {
            if (value == null) {
                throw new IllegalArgumentException(name + " is required");
            }
            return value;
        }
    }
}
