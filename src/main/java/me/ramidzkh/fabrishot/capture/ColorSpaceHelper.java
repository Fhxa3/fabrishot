package me.ramidzkh.fabrishot.capture;

import me.ramidzkh.fabrishot.config.ColorSpaceMode;
import me.ramidzkh.fabrishot.config.Config;
import me.ramidzkh.fabrishot.config.FabriColorSpace;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bytedeco.javacv.FFmpegFrameRecorder;

/**
 * Resolves the target colour space and applies the corresponding metadata
 * to the FFmpeg recorder so the output file is correctly tagged.
 */
public final class ColorSpaceHelper {

    private static final Logger LOGGER = LogManager.getLogger(ColorSpaceHelper.class);

    /** Cached Iris colour space (null = not installed or not yet read). */
    private static volatile FabriColorSpace cachedIrisColorSpace;
    private static volatile boolean irisProbed;

    private ColorSpaceHelper() {
    }

    /**
     * Reads the colour space that should be used for the current screenshot,
     * based on the configured {@link ColorSpaceMode}.
     */
    public static FabriColorSpace resolve() {
        return switch (Config.COLOR_SPACE_MODE) {
            case FOLLOW_IRIS -> {
                FabriColorSpace iris = detectIrisColorSpace();
                yield iris != null ? iris : FabriColorSpace.SRGB;
            }
            case CUSTOM -> Config.CUSTOM_COLOR_SPACE;
            case SRGB -> FabriColorSpace.SRGB;
        };
    }

    /**
     * Applies colour metadata ({@code color_primaries}, {@code color_trc},
     * {@code colorspace}) to the recorder so the encoded file carries the
     * correct colour space information.
     */
    public static void apply(FFmpegFrameRecorder recorder, FabriColorSpace cs) {
        String primaries = primariesFor(cs);
        String trc = trcFor(cs);
        String space = spaceFor(cs);

        if (primaries != null) {
            recorder.setVideoOption("color_primaries", primaries);
        }
        if (trc != null) {
            recorder.setVideoOption("color_trc", trc);
        }
        if (space != null) {
            recorder.setVideoOption("colorspace", space);
        }
    }

    // ---- Iris reflection ----

    private static FabriColorSpace detectIrisColorSpace() {
        if (irisProbed) {
            return cachedIrisColorSpace;
        }
        irisProbed = true;

        if (!FabricLoader.getInstance().isModLoaded("iris")) {
            return null;
        }

        try {
            Class<?> videoSettingsClass = Class.forName("net.irisshaders.iris.gui.option.IrisVideoSettings");
            Object colorSpace = videoSettingsClass.getField("colorSpace").get(null);
            if (colorSpace instanceof Enum<?> e) {
                cachedIrisColorSpace = FabriColorSpace.valueOf(e.name());
                return cachedIrisColorSpace;
            }
        } catch (ClassNotFoundException e) {
            LOGGER.debug("Iris video settings class not found (different Iris version?)", e);
        } catch (NoSuchFieldException e) {
            LOGGER.debug("Iris colorSpace field not found", e);
        } catch (IllegalAccessException e) {
            LOGGER.debug("Cannot access Iris colorSpace field", e);
        } catch (IllegalArgumentException e) {
            LOGGER.debug("Unknown Iris colour space value", e);
        } catch (Exception e) {
            LOGGER.warn("Unexpected error detecting Iris colour space", e);
        }
        return null;
    }

    // ---- FFmpeg colour metadata mappings ----

    private static String primariesFor(FabriColorSpace cs) {
        return switch (cs) {
            case SRGB, DISPLAY_P3 -> "bt709";
            case DCI_P3 -> "smpte432";
            case REC2020 -> "bt2020";
            case ADOBE_RGB -> "bt470bg";
        };
    }

    private static String trcFor(FabriColorSpace cs) {
        return switch (cs) {
            case SRGB, DISPLAY_P3 -> "iec61966_2_1";
            case DCI_P3 -> "smpte2084";
            case REC2020 -> "bt2020_10";
            case ADOBE_RGB -> "gamma22";
        };
    }

    private static String spaceFor(FabriColorSpace cs) {
        return switch (cs) {
            case SRGB, DCI_P3, DISPLAY_P3, ADOBE_RGB -> "bt709";
            case REC2020 -> "bt2020_ncl";
        };
    }
}
