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

package me.ramidzkh.fabrishot;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import me.ramidzkh.fabrishot.capture.CaptureTask;
import me.ramidzkh.fabrishot.capture.StackingCaptureTask;
import me.ramidzkh.fabrishot.config.Config;
import me.ramidzkh.fabrishot.config.FileFormat;
import me.ramidzkh.fabrishot.event.ScreenshotSaveCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
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

  public static final KeyMapping STACKING_BINDING =
      new KeyMapping(
          "key.fabrishot.stack_screenshot",
          InputConstants.Type.KEYBOARD,
          InputConstants.KEY_F10,
          KeyMapping.Category.MISC);

  private static final long MAX_READBACK_BYTES = Integer.MAX_VALUE;  //Some backends refuse to map buffers larger than 2 GiB or excessively large texture dimensions.

  private static final int DEFAULT_BYTES_PER_PIXEL = 4;

  private static final int MANY_FRAMES_WARNING = 256;

  private static final long STACK_MEMORY_MARGIN = 256L * 1024L * 1024L;

  private static final Pattern ILLEGAL_FILE_NAME_CHARACTERS =
      Pattern.compile("[\\\\/:*?\"<>|\\x00-\\x1f]");

  private static final Logger LOGGER = LogManager.getLogger(Fabrishot.class);

  private static CaptureTask task;

  private static volatile StackingCaptureTask stackTask;

  private static PendingStackCapture pendingStackCapture; // Render thread only.

  private static final Set<String> SHOWN_NOTICES = ConcurrentHashMap.newKeySet();

  private static final Set<Path> PENDING_SCREENSHOT_FILES = ConcurrentHashMap.newKeySet(); // Set screenshot name from previous configuration.

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
    KeyMappingHelper.registerKeyMapping(STACKING_BINDING);
    ScreenshotSaveCallback.EVENT.register(Fabrishot::printFileLink);
    ClientTickEvents.END_CLIENT_TICK.register(Fabrishot::onClientTick);
  }

  private static void onClientTick(Minecraft minecraft) {
    if (minecraft.level == null) {
      return;
    }

    FileFormat reset = Config.consumeResetFormat();

    if (reset != null) {
      printNotice(
          Component.translatable(
              "fabrishot.notice.format_fallback", Component.literal(reset.name())));
    }
  }

  public static void startCapture() {
    if (task != null || stackTask != null) {
      return;
    }

    Minecraft minecraft = Minecraft.getInstance();
    Component error = validateCaptureSize();

    if (error != null) {
      printError(error);
      return;
    }

    FileFormat format = Config.CAPTURE_FILE_FORMAT;
    Path file = getScreenshotFile(minecraft, format);
    task = new CaptureTask(file, format);
    resizeForCapture(file, () -> task = null);
  }

  public static void startStackCapture() {
    if (task != null) {
      return;
    }

    Minecraft minecraft = Minecraft.getInstance();

    if (stackTask != null) {
      if (stackTask.isCapturing() || pendingStackCapture != null) {
        printNotice(Component.translatable("fabrishot.notice.stack_busy"));
        return;
      }

      PendingStackCapture pending = prepareStackCapture(minecraft);

      if (pending != null) {
        pendingStackCapture = pending;
        printNotice(Component.translatable("fabrishot.notice.stack_queued"));
      }

      return;
    }

    PendingStackCapture pending = prepareStackCapture(minecraft);

    if (pending != null) {
      beginStackCapture(pending);
    }
  }

  // A mess of warnings.
  private static PendingStackCapture prepareStackCapture(Minecraft minecraft) {
    Component error = validateCaptureSize();

    if (error != null) {
      printError(error);
      return null;
    }

    error = validateStackMemory();

    if (error != null) {
      printError(error);
      return null;
    }

    warnAboutManyFrames();

    FileFormat format = Config.CAPTURE_FILE_FORMAT;

    return new PendingStackCapture(
        getScreenshotFile(minecraft, format),
        format,
        Config.CAPTURE_WIDTH,
        Config.CAPTURE_HEIGHT);
  }

  private static void beginStackCapture(PendingStackCapture pending) {
    Component error = validateStackMemory(pending.width(), pending.height());

    if (error != null) {
      printError(error);
      releaseScreenshotFile(pending.file());
      return;
    }

    Minecraft minecraft = Minecraft.getInstance();
    KeyMapping.releaseAll();
    minecraft.mouseHandler.setIgnoreFirstMove();

    stackTask =
        new StackingCaptureTask(
            pending.file(), pending.format(), pending.width(), pending.height());
    resizeForCapture(pending.file(), () -> stackTask = null);
  }

  private static void startPendingStackCapture() {
    PendingStackCapture pending = pendingStackCapture;

    if (stackTask != null || pending == null) {
      return;
    }

    pendingStackCapture = null;
    beginStackCapture(pending);
  }

  private static void warnAboutManyFrames() {
    if (Config.STACK_COUNT > MANY_FRAMES_WARNING) {
      printNoticeOnce(
          "stack_frames",
          Component.translatable("fabrishot.notice.stack_frames", Config.STACK_COUNT));
    }
  }

  private static void resizeForCapture(Path file, Runnable clearTask) {
    int width = captureWidth();
    int height = captureHeight();

    try {
      refresh();
    } catch (RuntimeException exception) {
      // The device could not allocate a capture sized framebuffer, so keep the window size.
      LOGGER.error(
          "Could not resize the framebuffer to {}x{}", width, height, exception);
      clearTask.run();
      releaseScreenshotFile(file);

      try {
        refresh();
      } catch (RuntimeException rollbackException) {
        LOGGER.error("Could not restore the window sized framebuffer", rollbackException);
      }

      printError(
          Component.translatable("fabrishot.error.capture_failed", width, height));
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

  private static Component validateStackMemory() {
    return validateStackMemory(Config.CAPTURE_WIDTH, Config.CAPTURE_HEIGHT);
  }

  private static Component validateStackMemory(int width, int height) {
    long pixels = (long) width * height;
    long bytesPerPixel =
        stackBytesPerPixel() + 4L * StackingCaptureTask.MAX_IN_FLIGHT_FRAMES;
    long required = pixels * bytesPerPixel;

    Runtime runtime = Runtime.getRuntime();
    long maxHeap = runtime.maxMemory();
    long usedHeap = runtime.totalMemory() - runtime.freeMemory();
    long safeAvailable = Math.min(maxHeap / 2, maxHeap - usedHeap - STACK_MEMORY_MARGIN);
    safeAvailable = Math.max(0L, safeAvailable);

    if (required <= safeAvailable) {
      return null;
    }

    long requiredMb = required / (1024L * 1024L);
    long availableMb = safeAvailable / (1024L * 1024L);
    return Component.translatable(
        "fabrishot.error.stack_memory", width, height, requiredMb, availableMb);
  }

  private static long stackBytesPerPixel() {
    if (Config.STACK_MODE == null || !Config.STACK_MODE.isMean()) {
      return 4L;
    }

    return Config.STACK_LOW_MEMORY ? 6L : 12L;
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

  public static void printNotice(Component message) {
    Minecraft minecraft = Minecraft.getInstance();
    minecraft.execute(() -> minecraft.gui.hud.getChat().addClientSystemMessage(message));
  }
  // Print information to the chat unless it has already been printed.
  public static void printNoticeOnce(String key, Component message) {
    if (SHOWN_NOTICES.add(key)) {
      printNotice(message);
    }
  }

  public static void onRenderPreOrPost() {
    if (task != null && task.onRenderTick()) {
      task = null;
      refresh();
    }

    StackingCaptureTask stacking = stackTask;

    if (stacking != null) {
      boolean wasCapturing = stacking.isCapturing();

      if (stacking.onRenderTick()) {
        stackTask = null;
      }

      if (wasCapturing && !stacking.isCapturing()) {
        refresh();
      }
    }

    startPendingStackCapture();
  }

  private static void refresh() {
    Minecraft minecraft = Minecraft.getInstance();
    Window window = minecraft.getWindow();

    minecraft.gameRenderer.resize(window.getWidth(), window.getHeight());
  }

  private static Path getScreenshotFile(Minecraft client, FileFormat format) {
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

    // Prevent the user pressed the screenshot key madly to cause multiple images writing to the same file.
    int i = 1;

    while (true) {
      Path file = dir.resolve(prefix + (i == 1 ? "" : "_" + i) + format.extension());

      if (!Files.exists(file) && PENDING_SCREENSHOT_FILES.add(file)) {
        return file;
      }

      i++;
    }
  }

  public static void releaseScreenshotFile(Path file) {
    PENDING_SCREENSHOT_FILES.remove(file); //Release reservation
  }

  public static boolean isInCapture() {
    StackingCaptureTask stacking = stackTask;

    return task != null || (stacking != null && stacking.isCapturing());
  }

  public static int captureWidth() {
    StackingCaptureTask stacking = stackTask;

    return stacking != null && stacking.isCapturing()
        ? stacking.width()
        : Config.CAPTURE_WIDTH;
  }

  public static int captureHeight() {
    StackingCaptureTask stacking = stackTask;

    return stacking != null && stacking.isCapturing()
        ? stacking.height()
        : Config.CAPTURE_HEIGHT;
  }

  public static boolean isStackCaptureInProgress() {
    StackingCaptureTask stacking = stackTask;

    return stacking != null && stacking.isCapturing();
  }

  public static boolean hasPendingStackCapture() {
    return pendingStackCapture != null;
  }

  public static void interruptStackCapture() {
    PendingStackCapture pending = pendingStackCapture;

    if (pending != null) {
      pendingStackCapture = null;
      releaseScreenshotFile(pending.file());
      LOGGER.info("Cancelled the queued stacking capture for {}", pending.file());
    }

    StackingCaptureTask stacking = stackTask;

    if (stacking != null) {
      stacking.interrupt();
    }
  }

  private record PendingStackCapture(Path file, FileFormat format, int width, int height) {}
}
