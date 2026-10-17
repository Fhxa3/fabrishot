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

import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.javacv.FFmpegLogCallback;

// Find Flashback and ensure its shaded FFmpeg libraries can be load.

public final class FlashbackDetector {

  private static final Logger LOGGER = LogManager.getLogger(FlashbackDetector.class);

  /** Whether FFmpeg encoding through Flashback is available. */
  public static final boolean AVAILABLE = detect();

  private FlashbackDetector() {}

  private static boolean detect() {
    if (!FabricLoader.getInstance().isModLoaded("flashback")) {
      LOGGER.info("Flashback is not installed, using the built-in encoder for PNG and JPG");
      return false;
    }

    try {
      Class.forName("org.bytedeco.javacv.FFmpegFrameRecorder");
      Loader.load(org.bytedeco.ffmpeg.global.avutil.class);
      Loader.load(org.bytedeco.ffmpeg.global.avcodec.class);
      Loader.load(org.bytedeco.ffmpeg.global.avformat.class);
      Loader.load(org.bytedeco.ffmpeg.global.swscale.class);
      // Use the JavaCV callback, because original crashed at avcodec_open2.
      FFmpegLogCallback.set();
      LOGGER.info(
          "Flashback detected, FFmpeg {} is available for screenshots",
          avutil.av_version_info().getString());
      return true;
    } catch (Throwable throwable) {
      LOGGER.warn(
          "Flashback is installed but its FFmpeg libraries could not be loaded, "
              + "using the built-in encoder for PNG and JPG",
          throwable);
      return false;
    }
  }
}
