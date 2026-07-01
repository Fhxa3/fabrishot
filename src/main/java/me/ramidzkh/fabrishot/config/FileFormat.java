package me.ramidzkh.fabrishot.config;

import java.util.Locale;

public enum FileFormat {

    PNG(false),
    JPG(false),
    WEBP(true),
    TIFF(true),
    AVIF(true),
    ;

    private final boolean needsFfmpeg;

    FileFormat(boolean needsFfmpeg) {
        this.needsFfmpeg = needsFfmpeg;
    }

    public boolean needsFfmpeg() {
        return needsFfmpeg;
    }

    public String extension() {
        return "." + name().toLowerCase(Locale.ROOT);
    }
}
