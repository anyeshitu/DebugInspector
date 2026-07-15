package com.allynav.debug.inspector.api;

public final class EntryConfig {
    private final boolean notificationEnabled;
    private final boolean shortcutEnabled;
    private final boolean shakeEnabled;

    private EntryConfig(Builder builder) {
        notificationEnabled = builder.notificationEnabled;
        shortcutEnabled = builder.shortcutEnabled;
        shakeEnabled = builder.shakeEnabled;
    }

    public boolean isNotificationEnabled() {
        return notificationEnabled;
    }

    public boolean isShortcutEnabled() {
        return shortcutEnabled;
    }

    public boolean isShakeEnabled() {
        return shakeEnabled;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private boolean notificationEnabled = true;
        private boolean shortcutEnabled = true;
        private boolean shakeEnabled;

        public Builder notificationEnabled(boolean enabled) {
            notificationEnabled = enabled;
            return this;
        }

        public Builder shortcutEnabled(boolean enabled) {
            shortcutEnabled = enabled;
            return this;
        }

        public Builder shakeEnabled(boolean enabled) {
            shakeEnabled = enabled;
            return this;
        }

        public EntryConfig build() {
            return new EntryConfig(this);
        }
    }
}
