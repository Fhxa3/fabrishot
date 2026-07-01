package me.ramidzkh.fabrishot.config;

/**
 * Determines how the output colour space is chosen.
 */
public enum ColorSpaceMode {
    /** Automatically follow Iris's current colour space setting. */
    FOLLOW_IRIS,
    /** Use a manually-specified colour space (see {@link FabriColorSpace}). */
    CUSTOM,
    /** Force standard sRGB (safe default). */
    SRGB
}
