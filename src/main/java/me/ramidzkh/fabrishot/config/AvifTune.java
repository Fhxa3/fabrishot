package me.ramidzkh.fabrishot.config;

/**
 * Tuning modes for the libaom-av1 encoder used by AVIF output.
 */
public enum AvifTune {
    SSIM,
    PSNR;

    /**
     * Returns the FFmpeg {@code tune} option value.
     */
    public String ffmpegValue() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
