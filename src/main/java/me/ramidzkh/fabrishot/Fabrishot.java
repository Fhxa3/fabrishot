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

package me.ramidzkh.fabrishot;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import me.ramidzkh.fabrishot.capture.CaptureTask;
import me.ramidzkh.fabrishot.config.Config;
import me.ramidzkh.fabrishot.event.ScreenshotSaveCallback;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Fabrishot {

  public static final KeyMapping SCREENSHOT_BINDING =
      new KeyMapping(
          "key.fabrishot.screenshot",
          InputConstants.Type.KEYBOARD,
          InputConstants.KEY_F9,
          KeyMapping.Category.MISC);

  private static final long MAX_READBACK_BYTES = Integer.MAX_VALUE;  //Some backends refuse to map buffers larger than 2 GiB or excessively large texture dimensions.

  private static final int DEFAULT_BYTES_PER_PIXEL = 4;

  private static final Pattern ILLEGAL_FILE_NAME_CHARACTERS =
      Pattern.compile("[\\\\/:*?\"<>|\\x00-\\x1f]");

  private static final Logger LOGGER = LogManager.getLogger(Fabrishot.class);

  private static CaptureTask task;

  private static void printFileLink(Path path) {
    Minecraft minecraft = Minecraft.getInstance();

    Component fileText =
        Component.literal(path.toFile().getName())
            .withStyle(ChatFormatting.UNDERLINE)
            .withStyle(style -> style.withClickEvent(new ClickEvent.OpenFile(path)));
    minecraft.execute(
        () -> {
          Component message = Component.translatable("screenshot.success", fileText);

          minecraft.gui.hud.getChat().addClientSystemMessage(message);
          minecraft.getNarrator().saySystemQueued(message);
        });
  }

  public static void initialize() {
    KeyMappingHelper.registerKeyMapping(SCREENSHOT_BINDING);
    ScreenshotSaveCallback.EVENT.register(Fabrishot::printFileLink);
  }

  public static void startCapture() {
    if (task != null) {
      return;
    }

    Minecraft minecraft = Minecraft.getInstance();
    Component error = validateCaptureSize();

    if (error != null) {
      printError(error);
      return;
    }

    task = new CaptureTask(getScreenshotFile(minecraft));

    try {
      refresh();
    } catch (RuntimeException exception) {
      // The device could not allocate a capture sized framebuffer, so keep the window size.
      LOGGER.error(
          "Could not resize the framebuffer to {}x{}",
          Config.CAPTURE_WIDTH,
          Config.CAPTURE_HEIGHT,
          exception);
      task = null;

      try {
        refresh();
      } catch (RuntimeException rollbackException) {
        LOGGER.error("Could not restore the window sized framebuffer", rollbackException);
      }

      printError(
          Component.translatable(
              "fabrishot.error.capture_failed", Config.CAPTURE_WIDTH, Config.CAPTURE_HEIGHT));
    }
  }

  /** Returns the resolution limits of this device. */
  public static int maxCaptureSide() {
    return RenderSystem.getDevice().getDeviceInfo().limits().maxTextureSize();
  }

  public static long maxCapturePixels() {
    var limits = RenderSystem.getDevice().getDeviceInfo().limits();
    long readbackBytes = Math.min(MAX_READBACK_BYTES, limits.maxMemoryAllocationSize());
    return readbackBytes / bytesPerPixel();
  }

  private static Component validateCaptureSize() {
    int width = Config.CAPTURE_WIDTH;
    int height = Config.CAPTURE_HEIGHT;

    if (width < 1 || height < 1) {
      return Component.translatable("fabrishot.error.invalid_size", width, height);
    }

    int maxSide = maxCaptureSide();

    if (width > maxSide || height > maxSide) {
      return Component.translatable("fabrishot.error.side_too_large", width, height, maxSide);
    }

    long maxPixels = maxCapturePixels();

    if ((long) width * height > maxPixels) {
      return Component.translatable(
          "fabrishot.error.too_many_pixels", width, height, maxPixels / 1_000_000L);
    }

    return null;
  }

  private static int bytesPerPixel() {
    var colorTexture = Minecraft.getInstance().gameRenderer.mainRenderTarget().getColorTexture();
    return colorTexture == null ? DEFAULT_BYTES_PER_PIXEL : colorTexture.getFormat().blockSize();
  }

  public static void printError(Component message) {
    Minecraft minecraft = Minecraft.getInstance();
    var styled = message.copy().withStyle(ChatFormatting.RED);
    minecraft.execute(() -> minecraft.gui.hud.getChat().addClientSystemMessage(styled));
  }

  public static void onRenderPreOrPost() {
    if (task != null && task.onRenderTick()) {
      task = null;
      refresh();
    }
  }

  private static void refresh() {
    Minecraft minecraft = Minecraft.getInstance();
    Window window = minecraft.getWindow();

    minecraft.gameRenderer.resize(window.getWidth(), window.getHeight());
  }

  private static Path getScreenshotFile(Minecraft client) {
    Path dir = client.gameDirectory.toPath().resolve("screenshots");

    try {
      if (!Files.exists(dir)) {
        Files.createDirectories(dir);
      }
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }

    String world = null;

    if (client.getSingleplayerServer() != null) {
      world = client.getSingleplayerServer().getWorldData().getLevelName();
    } else if (client.getCurrentServer() != null) {
      world = client.getCurrentServer().name;
    }

    // Strip anything that could escape the screenshots directory or break the file name.
    String prefix =
        ILLEGAL_FILE_NAME_CHARACTERS
            .matcher(
                Config.CUSTOM_FILE_NAME
                    .replace("%time%", Util.getFilenameFormattedDateTime())
                    .replace("%world%", world != null ? world : "no_world"))
            .replaceAll("_");

    if (prefix.isBlank()) {
      prefix = "huge";
    }

    Path file;

    // loop through suffixes while the file exists
    int i = 1;

    do {
      file =
          dir.resolve(prefix + (i++ == 1 ? "" : "_" + i) + Config.CAPTURE_FILE_FORMAT.extension());
    } while (Files.exists(file));

    return file;
  }

  public static boolean isInCapture() {
    return task != null;
  }
}
