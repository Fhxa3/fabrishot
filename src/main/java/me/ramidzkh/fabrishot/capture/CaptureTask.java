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

package me.ramidzkh.fabrishot.capture;

import java.io.IOException;
import java.nio.file.Path;
import me.ramidzkh.fabrishot.Fabrishot;
import me.ramidzkh.fabrishot.config.Config;
import me.ramidzkh.fabrishot.config.FileFormat;
import me.ramidzkh.fabrishot.event.FramebufferCaptureCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CaptureTask {

  private static final Logger LOGGER = LogManager.getLogger(CaptureTask.class);

  private final Path file;
  private final FileFormat format;
  private boolean hudHidden;
  private int frame;

  public CaptureTask(Path file, FileFormat format) {
    this.file = file;
    this.format = format;
  }

  public boolean onRenderTick() {
    Minecraft minecraft = Minecraft.getInstance();
    Hud hud = minecraft.gui.hud;

    if (frame == 0) {
      hudHidden = hud.isHidden();

      if (Config.HIDE_HUD && !hudHidden) {
        hud.toggle();
      }

      frame++;
      return false;
    } else if (frame < Config.CAPTURE_DELAY) {
      frame++;
      return false;
    } else {
      try {
        Screenshot.takeScreenshot(
            minecraft.gameRenderer.mainRenderTarget(),
            image -> {
              try {
                Util.ioPool()
                    .execute(
                        () -> {
                          try (image) {
                            FramebufferCaptureCallback.EVENT.invoker().onCapture(image);

                            if (Config.SAVE_FILE) {
                              FramebufferWriter.write(image, file, format);
                            }
                          } catch (IOException exception) {
                            LOGGER.error("Could not save the screenshot to {}", file, exception);
                            Fabrishot.printError(
                                Component.translatable(
                                    "fabrishot.error.save_failed", file.getFileName()));
                          } finally {
                            Fabrishot.releaseScreenshotFile(file);
                          }
                        });
              } catch (RuntimeException exception) {
                Fabrishot.releaseScreenshotFile(file);
                image.close();
                throw exception;
              }
            });
      } catch (RuntimeException exception) {
        // Warn when an operation cannot allocate a buffer.
        LOGGER.error(
            "Could not capture a {}x{} screenshot",
            Config.CAPTURE_WIDTH,
            Config.CAPTURE_HEIGHT,
            exception);
        Fabrishot.releaseScreenshotFile(file);
        Fabrishot.printError(
            Component.translatable(
                "fabrishot.error.capture_failed", Config.CAPTURE_WIDTH, Config.CAPTURE_HEIGHT));
      }

      if (hud.isHidden() != hudHidden) {
        hud.toggle();
      }

      return true;
    }
  }
}
