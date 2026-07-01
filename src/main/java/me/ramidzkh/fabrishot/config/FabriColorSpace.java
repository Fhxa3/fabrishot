package me.ramidzkh.fabrishot.config;

/**
 * Wide-gamut colour spaces supported for output embedding.
 * Maps to the commonly-available FFmpeg colour metadata constants.
 */
public enum FabriColorSpace {
    SRGB,
    DCI_P3,
    DISPLAY_P3,
    REC2020,
    ADOBE_RGB
}
