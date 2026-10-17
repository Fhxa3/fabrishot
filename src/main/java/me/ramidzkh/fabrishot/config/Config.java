/*
 * MIT License
 *
 * Copyright (c) 2021 Ramid Khan
 * Copyright (c) 2026 Fhxa3 (modifications)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package me.ramidzkh.fabrishot.config;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;
import me.ramidzkh.fabrishot.capture.FlashbackDetector;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Config {

  public static String CUSTOM_FILE_NAME = "huge_%time%";
  public static boolean OVERRIDE_SCREENSHOT_KEY = false;
  public static boolean HIDE_HUD = false;
  public static boolean SAVE_FILE = true;
  public static int CAPTURE_WIDTH = 3840;
  public static int CAPTURE_HEIGHT = 2160;
  public static int CAPTURE_DELAY = 3;
  public static FileFormat CAPTURE_FILE_FORMAT = FileFormat.PNG;
  public static int PNG_COMPRESSION = 6;
  public static PngPrediction PNG_PREDICTION = PngPrediction.DEFAULT;
  public static int JPG_QUALITY = 5;
  public static JpgColorSampling JPG_COLOR_SAMPLING = JpgColorSampling.YUV420;
  public static int WEBP_QUALITY = 90;
  public static boolean WEBP_LOSSLESS = false;
  public static TiffCompression TIFF_COMPRESSION = TiffCompression.DEFLATE;
  public static int AVIF_CPU_USED = 4;
  public static int AVIF_CRF = 24;
  public static AvifTune AVIF_TUNE = AvifTune.SSIM;
  public static AvifColorSampling AVIF_COLOR_SAMPLING = AvifColorSampling.YUV420;
  public static boolean DEBUG_ENCODE_STATS = false;

  private static final Logger LOGGER = LogManager.getLogger(Config.class);

  private static final Path CONFIG =
      FabricLoader.getInstance().getConfigDir().resolve("fabrishot.properties");

  private static FileFormat resetFormat;

  static {
    Properties properties = new Properties();

    try (BufferedReader reader = Files.newBufferedReader(CONFIG)) {
      properties.load(reader);
    } catch (IOException exception) {
      LOGGER.warn("Could not read {}, using the default configuration", CONFIG, exception);
      save();
    }

    Config.CUSTOM_FILE_NAME = properties.getProperty("custom_file_name", Config.CUSTOM_FILE_NAME);
    Config.OVERRIDE_SCREENSHOT_KEY =
        readBoolean(properties, "override_screenshot_key", Config.OVERRIDE_SCREENSHOT_KEY);
    Config.HIDE_HUD = readBoolean(properties, "hide_hud", Config.HIDE_HUD);
    Config.SAVE_FILE = readBoolean(properties, "save_file", Config.SAVE_FILE);
    Config.CAPTURE_WIDTH = readPositiveInt(properties, "width", Config.CAPTURE_WIDTH);
    Config.CAPTURE_HEIGHT = readPositiveInt(properties, "height", Config.CAPTURE_HEIGHT);
    Config.CAPTURE_DELAY = readPositiveInt(properties, "delay", Config.CAPTURE_DELAY);
    Config.CAPTURE_FILE_FORMAT = readFileFormat(properties);
    Config.PNG_COMPRESSION =
        readClampedInt(properties, "png_compression", Config.PNG_COMPRESSION, 0, 9);
    Config.PNG_PREDICTION =
        readEnum(properties, "png_prediction", PngPrediction.class, Config.PNG_PREDICTION);
    Config.JPG_QUALITY = readClampedInt(properties, "jpg_quality", Config.JPG_QUALITY, 2, 31);
    Config.JPG_COLOR_SAMPLING =
        readEnum(
            properties, "jpg_color_sampling", JpgColorSampling.class, Config.JPG_COLOR_SAMPLING);
    Config.WEBP_QUALITY = readClampedInt(properties, "webp_quality", Config.WEBP_QUALITY, 0, 100);
    Config.WEBP_LOSSLESS = readBoolean(properties, "webp_lossless", Config.WEBP_LOSSLESS);
    Config.TIFF_COMPRESSION =
        readEnum(properties, "tiff_compression", TiffCompression.class, Config.TIFF_COMPRESSION);
    Config.AVIF_CPU_USED = readClampedInt(properties, "avif_cpu_used", Config.AVIF_CPU_USED, 0, 8);
    Config.AVIF_CRF = readClampedInt(properties, "avif_crf", Config.AVIF_CRF, 0, 63);
    Config.AVIF_TUNE = readEnum(properties, "avif_tune", AvifTune.class, Config.AVIF_TUNE);
    Config.AVIF_COLOR_SAMPLING =
        readEnum(
            properties, "avif_color_sampling", AvifColorSampling.class, Config.AVIF_COLOR_SAMPLING);
    Config.DEBUG_ENCODE_STATS =
        readBoolean(properties, "debug_encode_stats", Config.DEBUG_ENCODE_STATS);
  }

  //Return the format.
  public static FileFormat consumeResetFormat() {
    FileFormat format = Config.resetFormat;
    Config.resetFormat = null;
    return format;
  }

  private static boolean readBoolean(Properties properties, String key, boolean defaultValue) {
    String value = properties.getProperty(key);

    if (value == null) {
      return defaultValue;
    }

    String trimmed = value.trim();

    if (trimmed.equalsIgnoreCase("true")) {
      return true;
    }

    if (trimmed.equalsIgnoreCase("false")) {
      return false;
    }

    LOGGER.warn("Invalid value '{}' for {}, using {}", value, key, defaultValue);
    return defaultValue;
  }

  private static int readPositiveInt(Properties properties, String key, int defaultValue) {
    String value = properties.getProperty(key);

    if (value == null) {
      return defaultValue;
    }

    try {
      return Math.max(1, Integer.parseInt(value.trim()));
    } catch (NumberFormatException exception) {
      LOGGER.warn("Invalid value '{}' for {}, using {}", value, key, defaultValue);
      return defaultValue;
    }
  }

  private static int readClampedInt(
      Properties properties, String key, int defaultValue, int min, int max) {
    String value = properties.getProperty(key);

    if (value == null) {
      return defaultValue;
    }

    try {
      int parsed = Integer.parseInt(value.trim());
      int clamped = Math.clamp(parsed, min, max);

      if (clamped != parsed) {
        LOGGER.warn(
            "Value '{}' for {} is outside of [{}, {}], using {}", value, key, min, max, clamped);
      }

      return clamped;
    } catch (NumberFormatException exception) {
      LOGGER.warn("Invalid value '{}' for {}, using {}", value, key, defaultValue);
      return defaultValue;
    }
  }

  private static <T extends Enum<T>> T readEnum(
      Properties properties, String key, Class<T> type, T defaultValue) {
    String value = properties.getProperty(key);

    if (value == null) {
      return defaultValue;
    }

    try {
      return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      LOGGER.warn("Invalid value '{}' for {}, using {}", value, key, defaultValue);
      return defaultValue;
    }
  }

  private static FileFormat readFileFormat(Properties properties) {
    String value = properties.getProperty("file_format");

    if (value == null) {
      return Config.CAPTURE_FILE_FORMAT;
    }

    FileFormat format;

    try {
      format = FileFormat.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      LOGGER.warn(
          "Invalid value '{}' for file_format, using {}", value, Config.CAPTURE_FILE_FORMAT);
      return Config.CAPTURE_FILE_FORMAT;
    }

    if (format.needsFfmpeg() && !FlashbackDetector.AVAILABLE) {
      LOGGER.warn("The {} format needs Flashback, the file format was reset to PNG", format);
      Config.resetFormat = format;
      return FileFormat.PNG;
    }

    return format;
  }

  static void save() {
    Properties properties = new Properties();
    properties.put("custom_file_name", Config.CUSTOM_FILE_NAME);
    properties.put("override_screenshot_key", String.valueOf(Config.OVERRIDE_SCREENSHOT_KEY));
    properties.put("hide_hud", String.valueOf(Config.HIDE_HUD));
    properties.put("save_file", String.valueOf(Config.SAVE_FILE));
    properties.put("width", String.valueOf(Config.CAPTURE_WIDTH));
    properties.put("height", String.valueOf(Config.CAPTURE_HEIGHT));
    properties.put("delay", String.valueOf(Config.CAPTURE_DELAY));
    properties.put("file_format", String.valueOf(Config.CAPTURE_FILE_FORMAT));
    properties.put("png_compression", String.valueOf(Config.PNG_COMPRESSION));
    properties.put("png_prediction", String.valueOf(Config.PNG_PREDICTION));
    properties.put("jpg_quality", String.valueOf(Config.JPG_QUALITY));
    properties.put("jpg_color_sampling", String.valueOf(Config.JPG_COLOR_SAMPLING));
    properties.put("webp_quality", String.valueOf(Config.WEBP_QUALITY));
    properties.put("webp_lossless", String.valueOf(Config.WEBP_LOSSLESS));
    properties.put("tiff_compression", String.valueOf(Config.TIFF_COMPRESSION));
    properties.put("avif_cpu_used", String.valueOf(Config.AVIF_CPU_USED));
    properties.put("avif_crf", String.valueOf(Config.AVIF_CRF));
    properties.put("avif_tune", String.valueOf(Config.AVIF_TUNE));
    properties.put("avif_color_sampling", String.valueOf(Config.AVIF_COLOR_SAMPLING));
    properties.put("debug_encode_stats", String.valueOf(Config.DEBUG_ENCODE_STATS));

    try (BufferedWriter writer = Files.newBufferedWriter(CONFIG)) {
      properties.store(writer, "Fabrishot screenshot config");
    } catch (IOException exception) {
      LOGGER.error("Could not save {}", CONFIG, exception);
    }
  }

  public enum PngPrediction {
    FAST("none"),
    DEFAULT(null),
    SMALLEST("mixed");

    private final String prediction;

    PngPrediction(String prediction) {
      this.prediction = prediction;
    }

    public String ffmpegValue() {
      return this.prediction;
    }
  }

  public enum JpgColorSampling {
    YUV444("yuvj444p"),
    YUV422("yuvj422p"),
    YUV420("yuvj420p");

    private final String pixelFormat;

    JpgColorSampling(String pixelFormat) {
      this.pixelFormat = pixelFormat;
    }

    public String pixelFormat() {
      return this.pixelFormat;
    }
  }

  public enum TiffCompression {
    RAW,
    LZW,
    DEFLATE,
    PACKBITS;

    public String ffmpegValue() {
      return name().toLowerCase(Locale.ROOT);
    }
  }

  public enum AvifTune {
    SSIM,
    PSNR;

    public String ffmpegValue() {
      return name().toLowerCase(Locale.ROOT);
    }
  }

  public enum AvifColorSampling {
    YUV444("yuv444p"),
    YUV422("yuv422p"),
    YUV420("yuv420p");

    private final String pixelFormat;

    AvifColorSampling(String pixelFormat) {
      this.pixelFormat = pixelFormat;
    }

    public String pixelFormat() {
      return this.pixelFormat;
    }
  }
}
