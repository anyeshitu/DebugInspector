package com.allynav.debug.inspector.ui;

final class UiRow {
    static final int HTTP = 1;
    static final int WEB_SOCKET = 2;
    static final int SERIAL = 3;
    static final int DATABASE = 4;
    static final int TABLE = 5;
    static final int DATABASE_ROW = 6;

    static final int TONE_IDLE = 0;
    static final int TONE_SUCCESS = 1;
    static final int TONE_INFO = 2;
    static final int TONE_WARNING = 3;
    static final int TONE_ERROR = 4;

    final int kind;
    final String id;
    final String title;
    final String subtitle;
    final String meta;
    final String detail;
    final String path;
    final String badge;
    final int tone;

    UiRow(int kind, String id, String title, String subtitle, String meta, String detail, String path) {
        this(kind, id, title, subtitle, meta, detail, path, "", TONE_IDLE);
    }

    UiRow(int kind, String id, String title, String subtitle, String meta, String detail, String path,
          String badge, int tone) {
        this.kind = kind;
        this.id = id;
        this.title = title;
        this.subtitle = subtitle;
        this.meta = meta;
        this.detail = detail;
        this.path = path;
        this.badge = badge;
        this.tone = tone;
    }
}
