/*
 * MIT License
 *
 * Copyright (c) 2021 Ramid Khan
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

import me.ramidzkh.fabrishot.Fabrishot;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.LogManager;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class Config {

    public static String CUSTOM_FILE_NAME = "huge_%time%";
    public static boolean OVERRIDE_SCREENSHOT_KEY = false;
    public static boolean HIDE_HUD = false;
    public static boolean SAVE_FILE = true;
    public static int CAPTURE_WIDTH = 3840;
    public static int CAPTURE_HEIGHT = 2160;
    public static int CAPTURE_DELAY = 3;
    public static FileFormat CAPTURE_FILE_FORMAT = FileFormat.PNG;

    // Encoding parameters
    public static int PNG_COMPRESSION_LEVEL = 6;
    public static int JPG_QUALITY = 2;
    public static JpgColorSampling JPG_COLOR_SAMPLING = JpgColorSampling.YUV444;
    public static int WEBP_QUALITY = 80;
    public static boolean WEBP_LOSSLESS = false;
    public static TiffCompression TIFF_COMPRESSION = TiffCompression.LZW;

    // Colour space
    public static ColorSpaceMode COLOR_SPACE_MODE = ColorSpaceMode.SRGB;
    public static FabriColorSpace CUSTOM_COLOR_SPACE = FabriColorSpace.SRGB;

    // AVIF encoding
    public static int AVIF_CPU_USED = 4;
    public static int AVIF_CRF = 24;
    public static AvifTune AVIF_TUNE = AvifTune.SSIM;

    private static final Path CONFIG = FabricLoader.getInstance().getConfigDir().resolve("fabrishot.properties");

    static {
        try (BufferedReader reader = Files.newBufferedReader(CONFIG)) {
            Properties properties = new Properties();
            properties.load(reader);

            Config.CUSTOM_FILE_NAME = properties.getProperty("custom_file_name", "huge_%time%");
            Config.OVERRIDE_SCREENSHOT_KEY = Boolean.parseBoolean(properties.getProperty("override_screenshot_key"));
            Config.HIDE_HUD = Boolean.parseBoolean(properties.getProperty("hide_hud"));
            Config.SAVE_FILE = Boolean.parseBoolean(properties.getProperty("save_file"));
            Config.CAPTURE_WIDTH = Integer.parseInt(properties.getProperty("width"));
            Config.CAPTURE_HEIGHT = Integer.parseInt(properties.getProperty("height"));
            Config.CAPTURE_DELAY = Integer.parseInt(properties.getProperty("delay"));
            Config.CAPTURE_FILE_FORMAT = FileFormat.valueOf(properties.getProperty("file_format"));
            Config.PNG_COMPRESSION_LEVEL = Integer.parseInt(properties.getProperty("png_compression", "6"));
            Config.JPG_QUALITY = Integer.parseInt(properties.getProperty("jpg_quality", "2"));
            Config.JPG_COLOR_SAMPLING = JpgColorSampling.valueOf(properties.getProperty("jpg_color_sampling", "YUV444"));
            Config.WEBP_QUALITY = Integer.parseInt(properties.getProperty("webp_quality", "80"));
            Config.WEBP_LOSSLESS = Boolean.parseBoolean(properties.getProperty("webp_lossless"));
            Config.TIFF_COMPRESSION = TiffCompression.valueOf(properties.getProperty("tiff_compression", "LZW"));
            Config.COLOR_SPACE_MODE = ColorSpaceMode.valueOf(properties.getProperty("color_space_mode", "SRGB"));
            Config.CUSTOM_COLOR_SPACE = FabriColorSpace.valueOf(properties.getProperty("custom_color_space", "SRGB"));
            Config.AVIF_CPU_USED = Integer.parseInt(properties.getProperty("avif_cpu_used", "4"));
            Config.AVIF_CRF = Integer.parseInt(properties.getProperty("avif_crf", "24"));
            Config.AVIF_TUNE = AvifTune.valueOf(properties.getProperty("avif_tune", "SSIM"));
        } catch (Exception ignored) {
            save();
        }
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
        properties.put("png_compression", String.valueOf(Config.PNG_COMPRESSION_LEVEL));
        properties.put("jpg_quality", String.valueOf(Config.JPG_QUALITY));
        properties.put("jpg_color_sampling", String.valueOf(Config.JPG_COLOR_SAMPLING));
        properties.put("webp_quality", String.valueOf(Config.WEBP_QUALITY));
        properties.put("webp_lossless", String.valueOf(Config.WEBP_LOSSLESS));
        properties.put("tiff_compression", String.valueOf(Config.TIFF_COMPRESSION));
        properties.put("color_space_mode", String.valueOf(Config.COLOR_SPACE_MODE));
        properties.put("custom_color_space", String.valueOf(Config.CUSTOM_COLOR_SPACE));
        properties.put("avif_cpu_used", String.valueOf(Config.AVIF_CPU_USED));
        properties.put("avif_crf", String.valueOf(Config.AVIF_CRF));
        properties.put("avif_tune", String.valueOf(Config.AVIF_TUNE));

        try (BufferedWriter writer = Files.newBufferedWriter(CONFIG)) {
            properties.store(writer, "Fabrishot screenshot config");
        } catch (IOException exception) {
            LogManager.getLogger(Fabrishot.class).error(exception.getMessage(), exception);
        }
    }
}
