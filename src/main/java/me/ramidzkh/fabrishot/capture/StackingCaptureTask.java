/*
 * MIT License
 *
 * Copyright (c) 2026 Fhxa3
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

package me.ramidzkh.fabrishot.capture;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import me.ramidzkh.fabrishot.Fabrishot;
import me.ramidzkh.fabrishot.config.Config;
import me.ramidzkh.fabrishot.config.FileFormat;
import me.ramidzkh.fabrishot.config.StackMode;
import me.ramidzkh.fabrishot.event.FramebufferCaptureCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// Captures a series of frames and composites them into one screenshot.

public class StackingCaptureTask {

  private static final Logger LOGGER = LogManager.getLogger(StackingCaptureTask.class);

  public static final int MAX_IN_FLIGHT_FRAMES = 2;
  private static final long STALLED_TIMEOUT_MILLIS = 60_000L;
  private static final long COMPOSITE_TIMEOUT_MILLIS = 300_000L;

  private final Path file;
  private final FileFormat format;
  private final int width;
  private final int height;
  private final int targetFrames;
  private final int intervalTicks;
  private final StackMode mode;
  private final boolean recursiveMean;

  private final Object accumulatorLock = new Object();
  private final AtomicInteger processedFrames = new AtomicInteger();
  private final AtomicBoolean finalizing = new AtomicBoolean();
  private final AtomicBoolean failureReported = new AtomicBoolean();

  // Render thread only.
  private int tick;
  private int taken;
  private boolean hudHidden;
  private boolean hudInitialized;
  private int lastProcessedFrames;
  private long lastProgressMillis;

  // Shared between the render thread and the stacking worker thread.
  private StackAccumulator accumulator;
  private volatile ExecutorService serializer;
  private volatile boolean interrupted;
  private volatile boolean abandoned;
  private volatile Component failure;
  private volatile boolean capturing = true;
  private volatile boolean captureDone;
  private volatile boolean finished;
  private volatile int dispatched;
  private volatile long compositeStartedMillis;

  public StackingCaptureTask(Path file, FileFormat format, int width, int height) {
    this.file = file;
    this.format = format;
    this.width = width;
    this.height = height;
    this.targetFrames =
        Math.clamp(Config.STACK_COUNT, Config.STACK_COUNT_MIN, Config.STACK_COUNT_MAX);
    this.intervalTicks =
        Math.clamp(
            Config.STACK_INTERVAL_TICKS, Config.STACK_INTERVAL_MIN, Config.STACK_INTERVAL_MAX);
    StackMode mode = Config.STACK_MODE;
    this.mode = mode;
    this.recursiveMean = Config.STACK_LOW_MEMORY && mode.isMean();
    this.lastProgressMillis = Util.getMillis();
    LOGGER.info(
        "Capturing {} stacking frames with a {} frame interval", targetFrames, intervalTicks);
  }

  public int width() {
    return width;
  }

  public int height() {
    return height;
  }

  public boolean onRenderTick() {
    Minecraft minecraft = Minecraft.getInstance();

    if (capturing) {
      captureTick(minecraft, minecraft.gui.hud);
    } else if (!finished) {
      if (finalizing.get()) {
        watchForStalledComposite();
      } else {
        watchForStalledFrames();
      }
    }

    return finished;
  }

  // Prevent a lost callback from hanging the task indefinitely.
  private void watchForStalledFrames() {
    if (abandoned || failure != null) {
      abandoned = true;
      dispatched = processedFrames.get();
      requestFinalize();
      return;
    }

    int processed = processedFrames.get();
    long now = Util.getMillis();

    if (processed != lastProcessedFrames) {
      lastProcessedFrames = processed;
      lastProgressMillis = now;
      return;
    }

    if (now - lastProgressMillis < STALLED_TIMEOUT_MILLIS) {
      return;
    }

    lastProgressMillis = now;
    LOGGER.warn(
        "Gave up waiting for {} of {} stacking frames", dispatched - processed, dispatched);
    reportFailure(Component.translatable("fabrishot.error.stack_timeout"));
    abandoned = true;
    dispatched = processed;
    requestFinalize();
  }

  // Releases the owning capture slot when a composite worker never returns. 
  private void watchForStalledComposite() {
    long started = compositeStartedMillis;

    if (started == 0L || Util.getMillis() - started < COMPOSITE_TIMEOUT_MILLIS) {
      return;
    }

    LOGGER.error(
        "The stacking composite did not finish within {} ms, releasing its capture slot",
        COMPOSITE_TIMEOUT_MILLIS);
    reportFailure(Component.translatable("fabrishot.error.stack_timeout"));
    finished = true;
  }

  private void waitForBackpressure(Hud hud) {
    int processed = processedFrames.get();
    long now = Util.getMillis();

    if (processed != lastProcessedFrames) {
      lastProcessedFrames = processed;
      lastProgressMillis = now;
      return;
    }

    if (now - lastProgressMillis >= STALLED_TIMEOUT_MILLIS) {
      finishCapture(hud, Component.translatable("fabrishot.error.stack_timeout"));
    }
  }

  private void reportFailure(Component message) {
    failure = message;

    if (failureReported.compareAndSet(false, true)) {
      Fabrishot.printError(message);
    }
  }

  public boolean isCapturing() {
    return capturing;
  }

  public void interrupt() {
    interrupted = true;
  }

  private void captureTick(Minecraft minecraft, Hud hud) {
    if (!hudInitialized) {
      hudHidden = hud.isHidden();
      hudInitialized = true;
      // Release the key that started the capture after its input event has finished.
      KeyMapping.releaseAll();

      if (Config.HIDE_HUD && !hudHidden) {
        hud.toggle();
      }
    }

    if (failure != null) {
      finishCapture(hud, failure);
      return;
    }

    if (interrupted || taken >= targetFrames) {
      finishCapture(hud, null);
      return;
    }

    if (taken - processedFrames.get() >= MAX_IN_FLIGHT_FRAMES) {
      waitForBackpressure(hud);
      return;
    }

    int delay = taken == 0 ? Config.CAPTURE_DELAY : intervalTicks;

    if (tick < delay) {
      tick++;
      return;
    }

    captureFrame(minecraft, hud);
  }

  private void captureFrame(Minecraft minecraft, Hud hud) {
    try {
      Screenshot.takeScreenshot(minecraft.gameRenderer.mainRenderTarget(), this::submitFrame);
    } catch (RuntimeException exception) {
      LOGGER.error(
          "Could not capture stacking frame {} of {}", taken + 1, targetFrames, exception);
      finishCapture(
          hud, Component.translatable("fabrishot.error.capture_failed", width, height));
      return;
    }

    taken++;
    tick = 0;

    if (taken >= targetFrames) {
      finishCapture(hud, null);
    }
  }

  private void finishCapture(Hud hud, Component failureOverride) {
    if (!capturing) {
      return;
    }

    Component failureToReport = failure != null ? failure : failureOverride;
    capturing = false;

    if (failureToReport != null) {
      reportFailure(failureToReport);
      abandoned = true;
    }

    if (hudInitialized && hud.isHidden() != hudHidden) {
      hud.toggle();
    }

    dispatched = failureToReport == null ? taken : processedFrames.get();
    captureDone = true;
    lastProgressMillis = Util.getMillis();
    LOGGER.info("Captured {} of {} stacking frames", taken, targetFrames);

    requestFinalize();
  }

  private void submitFrame(NativeImage image) {
    if (finished || abandoned) {
      image.close();
      return;
    }

    try {
      executor().execute(() -> processFrame(image));
    } catch (RuntimeException exception) {
      image.close();
      frameFailed(exception);
    }
  }

  private void processFrame(NativeImage image) {
    if (finished || abandoned) {
      image.close();
      return;
    }

    try {
      synchronized (accumulatorLock) {
        if (finished || abandoned) {
          return;
        }

        if (accumulator == null) {
          accumulator =
              new StackAccumulator(image.getWidth(), image.getHeight(), mode, recursiveMean);
        }

        accumulator.accumulate(image);
      }
    } catch (OutOfMemoryError error) {
      LOGGER.error("Ran out of memory while accumulating a stacking frame", error);
      reportFailure(Component.translatable("fabrishot.error.stack_oom"));
      abandoned = true;
    } catch (RuntimeException exception) {
      LOGGER.error("Could not accumulate a stacking frame", exception);
    } finally {
      image.close();
      processedFrames.incrementAndGet();
      tryFinalize();
    }
  }

  private void frameFailed(RuntimeException exception) {
    if (finished || abandoned) {
      return;
    }

    LOGGER.error("Could not queue a stacking frame for processing", exception);
    processedFrames.incrementAndGet();
    tryFinalize();
  }

  private void requestFinalize() {
    try {
      executor().execute(this::tryFinalize);
    } catch (RuntimeException exception) {
      LOGGER.warn("Could not schedule the stacking composite on the worker thread", exception);
      tryFinalize();
    }
  }

  private Executor executor() {
    ExecutorService current = serializer;

    if (current == null) {
      current =
          Executors.newSingleThreadExecutor(
              runnable -> {
                Thread thread = new Thread(runnable, "Fabrishot-Stacking");
                thread.setDaemon(true);
                return thread;
              });
      serializer = current;
    }

    return current;
  }

  private void tryFinalize() {
    if (!captureDone || finished) {
      return;
    }

    if (processedFrames.get() < dispatched) {
      return;
    }

    if (!finalizing.compareAndSet(false, true)) {
      return;
    }

    compositeStartedMillis = Util.getMillis();

    try {
      composite();
    } catch (OutOfMemoryError error) {
      LOGGER.error("Ran out of memory while compositing the stacking screenshot", error);
      reportFailure(Component.translatable("fabrishot.error.stack_oom"));
      abandoned = true;
    } catch (RuntimeException exception) {
      LOGGER.error("Could not composite the stacking screenshot", exception);
      Fabrishot.printError(
          Component.translatable("fabrishot.error.save_failed", file.getFileName()));
    } finally {
      Fabrishot.releaseScreenshotFile(file);

      if (serializer != null) {
        serializer.shutdown();
      }

      finished = true;
    }
  }

  private void composite() {
    StackAccumulator stack;

    synchronized (accumulatorLock) {
      stack = accumulator;
      accumulator = null;
    }

    if (stack == null || stack.frameCount() == 0) {
      if (failure == null) {
        LOGGER.warn("The stacking capture was cancelled, no frames were taken");
        Fabrishot.printError(Component.translatable("fabrishot.stack.cancelled"));
      }

      return;
    }

    NativeImage image = stack.finish();

    try (image) {
      FramebufferCaptureCallback.EVENT.invoker().onCapture(image);

      if (Config.SAVE_FILE) {
        FramebufferWriter.write(image, file, format);
      }
    } catch (IOException exception) {
      LOGGER.error("Could not save the stacking screenshot to {}", file, exception);
      Fabrishot.printError(
          Component.translatable("fabrishot.error.save_failed", file.getFileName()));
    }

    LOGGER.info("Finished the stack composite of {} frames for {}", stack.frameCount(), file);
  }
}
