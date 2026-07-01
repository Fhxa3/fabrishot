package me.ramidzkh.fabrishot.config;

/**
 * TIFF compression algorithms available through FFmpeg's tiff encoder.
 */
public enum TiffCompression {
    PACKBITS,
    RAW,
    LZW,
    DEFLATE;

    /**
     * Returns the FFmpeg {@code compression_algo} option value.
     */
    public String ffmpegValue() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
