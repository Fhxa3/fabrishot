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

import me.ramidzkh.fabrishot.config.FileFormat;

public final class FormatLimits {

  //These limits are measured values. If you're not sure, better not mess with them.
  // Unless FFmpeg gets updated.
  // Or you find some mysterious magic workaround.
  public static final int BYTES_PER_PIXEL = 4;
  private static final long FFMPEG_SIZE_BUDGET = Integer.MAX_VALUE / 8L;
  public static final long STB_PNG_MAX_PIXELS = 536_000_000L;
  public static final int JPEG_MAX_SIDE = 65500;
  public static final long JPEG_YUV444_MAX_PIXELS = 140_000_000L;
  public static final long TIFF_LZW_MAX_BYTES = 1L << 29;
  public static final int WEBP_MAX_SIDE = 16383;
  public static final long WEBP_LOSSY_MAX_PIXELS = 100_000_000L;

  public static final String FFMPEG_PIXEL_REASON = "FFmpeg limit of 268435455 pixels";
  public static final String JPEG_SIDE_REASON = "JPEG limit of 65500 pixels per side";
  public static final String WEBP_SIDE_REASON = "WebP limit of 16383 pixels per side";
  public static final String STB_PIXEL_REASON = "built-in encoder limit of 536000000 pixels";

  public static final String FLASHBACK_REASON = "Flashback is required";

  private FormatLimits() {}
  
  public static boolean fitsFfmpeg(int width, int height) {
    return (width + 128L) * (height + 128L) < FFMPEG_SIZE_BUDGET;
  }

  public static String reasonFor(
      FileFormat format, int width, int height, boolean flashbackAvailable) {
    long pixels = (long) width * height;
    boolean fitsFfmpeg = fitsFfmpeg(width, height);

    return switch (format) {
      case PNG -> {
        if ((flashbackAvailable && fitsFfmpeg) || pixels <= STB_PNG_MAX_PIXELS) {
          yield null;
        }
        yield STB_PIXEL_REASON;
      }

      case JPG -> {
        if (width > JPEG_MAX_SIDE || height > JPEG_MAX_SIDE) {
          yield JPEG_SIDE_REASON;
        }
        yield null;
      }

      case WEBP -> {
        if (!flashbackAvailable) {
          yield FLASHBACK_REASON;
        }
        if (width > WEBP_MAX_SIDE || height > WEBP_MAX_SIDE) {
          yield WEBP_SIDE_REASON;
        }
        yield fitsFfmpeg ? null : FFMPEG_PIXEL_REASON;
      }

      case TIFF, AVIF -> {
        if (!flashbackAvailable) {
          yield FLASHBACK_REASON;
        }
        yield fitsFfmpeg ? null : FFMPEG_PIXEL_REASON;
      }
    };
  }
}
