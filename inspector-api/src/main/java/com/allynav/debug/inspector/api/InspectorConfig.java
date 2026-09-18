package com.allynav.debug.inspector.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class InspectorConfig {
    private final RetentionPolicy retentionPolicy;
    private final EntryConfig entryConfig;
    private final Set<String> redactedHeaderNames;
    private final List<HttpBodyTransformer> bodyTransformers;
    private final List<DatabaseRegistration> databases;
    private final boolean captureEnabled;
    private final UiLanguage uiLanguage;
    private final boolean alwaysReadResponseBody;
    private final List<String> skippedPaths;
    private final List<Pattern> skippedPathPatterns;
    private final Set<String> skippedDomains;
    private final List<Pattern> skippedDomainPatterns;

    private InspectorConfig(Builder builder) {
        retentionPolicy = builder.retentionPolicy;
        entryConfig = builder.entryConfig;
        redactedHeaderNames = Collections.unmodifiableSet(new LinkedHashSet<>(builder.redactedHeaderNames));
        bodyTransformers = Collections.unmodifiableList(new ArrayList<>(builder.bodyTransformers));
        databases = Collections.unmodifiableList(new ArrayList<>(builder.databases));
        captureEnabled = builder.captureEnabled;
        uiLanguage = builder.uiLanguage;
        alwaysReadResponseBody = builder.alwaysReadResponseBody;
        skippedPaths = Collections.unmodifiableList(new ArrayList<>(builder.skippedPaths));
        skippedPathPatterns = Collections.unmodifiableList(new ArrayList<>(builder.skippedPathPatterns));
        skippedDomains = Collections.unmodifiableSet(new LinkedHashSet<>(builder.skippedDomains));
        skippedDomainPatterns = Collections.unmodifiableList(new ArrayList<>(builder.skippedDomainPatterns));
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

    public boolean isAlwaysReadResponseBody() {
        return alwaysReadResponseBody;
    }

    public List<String> getSkippedPaths() {
        return skippedPaths;
    }

    public List<Pattern> getSkippedPathPatterns() {
        return skippedPathPatterns;
    }

    public Set<String> getSkippedDomains() {
        return skippedDomains;
    }

    public List<Pattern> getSkippedDomainPatterns() {
        return skippedDomainPatterns;
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
        private boolean alwaysReadResponseBody;
        private final List<String> skippedPaths = new ArrayList<>();
        private final List<Pattern> skippedPathPatterns = new ArrayList<>();
        private final Set<String> skippedDomains = new LinkedHashSet<>();
        private final List<Pattern> skippedDomainPatterns = new ArrayList<>();

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

        public Builder alwaysReadResponseBody(boolean enabled) {
            alwaysReadResponseBody = enabled;
            return this;
        }

        /** 配置需要忽略记录的 URL 路径，保留调用方数组并避免原地修改。 */
        public Builder skipPaths(String... paths) {
            if (paths != null) {
                for (String path : paths) {
                    if (path != null && !path.trim().isEmpty()) skippedPaths.add(path.trim());
                }
            }
            return this;
        }

        public Builder skipPathPatterns(Pattern... patterns) {
            if (patterns != null) {
                for (Pattern pattern : patterns) if (pattern != null) skippedPathPatterns.add(pattern);
            }
            return this;
        }

        /** Chucker 兼容别名：正则路径规则与字符串路径规则共用 skipPaths 名称。 */
        public Builder skipPaths(Pattern... patterns) {
            return skipPathPatterns(patterns);
        }

        public Builder skipDomains(String... domains) {
            if (domains != null) {
                for (String domain : domains) {
                    if (domain != null && !domain.trim().isEmpty()) skippedDomains.add(domain.trim().toLowerCase(Locale.US));
                }
            }
            return this;
        }

        public Builder skipDomainPatterns(Pattern... patterns) {
            if (patterns != null) {
                for (Pattern pattern : patterns) if (pattern != null) skippedDomainPatterns.add(pattern);
            }
            return this;
        }

        /** Chucker 兼容别名：允许直接以正则表达式配置域名忽略规则。 */
        public Builder skipDomains(Pattern... patterns) {
            return skipDomainPatterns(patterns);
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
