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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;
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

  private static final Logger LOGGER = LogManager.getLogger(Config.class);

  private static final Path CONFIG =
      FabricLoader.getInstance().getConfigDir().resolve("fabrishot.properties");

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

  private static FileFormat readFileFormat(Properties properties) {
    String value = properties.getProperty("file_format");

    if (value == null) {
      return Config.CAPTURE_FILE_FORMAT;
    }

    try {
      return FileFormat.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      LOGGER.warn(
          "Invalid value '{}' for file_format, using {}", value, Config.CAPTURE_FILE_FORMAT);
      return Config.CAPTURE_FILE_FORMAT;
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

    try (BufferedWriter writer = Files.newBufferedWriter(CONFIG)) {
      properties.store(writer, "Fabrishot screenshot config");
    } catch (IOException exception) {
      LOGGER.error("Could not save {}", CONFIG, exception);
    }
  }
}
