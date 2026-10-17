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

import java.util.LinkedHashMap;
import java.util.Map;
import me.ramidzkh.fabrishot.Fabrishot;
import me.ramidzkh.fabrishot.config.Config;
import me.ramidzkh.fabrishot.config.Config.JpgColorSampling;
import me.ramidzkh.fabrishot.config.Config.TiffCompression;
import me.ramidzkh.fabrishot.config.FileFormat;
import net.minecraft.network.chat.Component;

final class EncoderProfile {
  private static final String RGBA = "rgba";
  private static final String MUXER_IMAGE2 = "image2";
  private static final String MUXER_AVIF = "avif";
  private static final String WEBP_LOSSLESS_PIXEL_FORMAT = "bgra";
  private static final String WEBP_LOSSY_PIXEL_FORMAT = "bgra";
  private static final int WEBP_LOSSLESS_QUALITY = 100;

  private final String codec;
  private final String pixelFormat;
  private final String muxer;
  private final Map<String, String> options;
  private final int quality;

  private EncoderProfile(
      String codec, String pixelFormat, String muxer, Map<String, String> options, int quality) {
    this.codec = codec;
    this.pixelFormat = pixelFormat;
    this.muxer = muxer;
    this.options = Map.copyOf(options);
    this.quality = quality;
  }

  static EncoderProfile create(FileFormat format, int width, int height) {
    long pixels = (long) width * height;

    return switch (format) {
      case PNG -> png();
      case JPG -> jpg(pixels);
      case WEBP -> webp(pixels);
      case TIFF -> tiff(pixels);
      case AVIF -> avif();
    };
  }

  private static EncoderProfile png() {
    Map<String, String> options = new LinkedHashMap<>();
    options.put("compression_level", String.valueOf(Config.PNG_COMPRESSION));
    String prediction = Config.PNG_PREDICTION.ffmpegValue();

    if (prediction != null) {
      options.put("pred", prediction);
    }

    return new EncoderProfile("png", RGBA, MUXER_IMAGE2, options, 0);
  }

  private static EncoderProfile jpg(long pixels) {
    JpgColorSampling sampling = Config.JPG_COLOR_SAMPLING;

    if (sampling == JpgColorSampling.YUV444 && pixels > FormatLimits.JPEG_YUV444_MAX_PIXELS) {
      notifyFallback("jpg_yuv444", JpgColorSampling.YUV420.name(), "4:4:4 above 140 Mpx");
      sampling = JpgColorSampling.YUV420;
    }

    return new EncoderProfile(
        "mjpeg", sampling.pixelFormat(), MUXER_IMAGE2, Map.of(), Config.JPG_QUALITY);
  }

  private static EncoderProfile webp(long pixels) {
    boolean lossless = Config.WEBP_LOSSLESS;

    if (!lossless && pixels > FormatLimits.WEBP_LOSSY_MAX_PIXELS) {
      notifyFallback("webp_lossy", "lossless", "lossy above 100 Mpx");
      lossless = true;
    }

    Map<String, String> options = new LinkedHashMap<>();
    options.put("lossless", lossless ? "1" : "0");
    options.put("quality", String.valueOf(lossless ? WEBP_LOSSLESS_QUALITY : Config.WEBP_QUALITY));

    return new EncoderProfile(
        "libwebp",
        lossless ? WEBP_LOSSLESS_PIXEL_FORMAT : WEBP_LOSSY_PIXEL_FORMAT,
        MUXER_IMAGE2,
        options,
        0);
  }

  private static EncoderProfile tiff(long pixels) {
    TiffCompression compression = Config.TIFF_COMPRESSION;

    if (compression == TiffCompression.LZW
        && pixels * FormatLimits.BYTES_PER_PIXEL >= FormatLimits.TIFF_LZW_MAX_BYTES) {
      notifyFallback("tiff_lzw", TiffCompression.DEFLATE.name(), "LZW from 2^29 bytes on");
      compression = TiffCompression.DEFLATE;
    }

    return new EncoderProfile(
        "tiff", RGBA, MUXER_IMAGE2, Map.of("compression_algo", compression.ffmpegValue()), 0);
  }

  private static EncoderProfile avif() {
    Map<String, String> options = new LinkedHashMap<>();
    options.put("usage", "allintra");
    options.put("still-picture", "1");
    options.put("row-mt", "1");
    options.put("cpu-used", String.valueOf(Config.AVIF_CPU_USED));
    options.put("crf", String.valueOf(Config.AVIF_CRF));
    options.put("tune", Config.AVIF_TUNE.ffmpegValue());

    return new EncoderProfile(
        "libaom-av1", Config.AVIF_COLOR_SAMPLING.pixelFormat(), MUXER_AVIF, options, 0);
  }

  private static void notifyFallback(String key, String target, String reason) {
    Fabrishot.printNoticeOnce(
        "encoder_fallback." + key,
        Component.translatable(
            "fabrishot.notice.encoder_fallback",
            Component.literal(target),
            Component.literal(reason)));
  }

  String codec() {
    return this.codec;
  }

  String pixelFormat() {
    return this.pixelFormat;
  }

  String muxer() {
    return this.muxer;
  }

  Map<String, String> options() {
    return this.options;
  }

  // 0 = no QSCALE flag, otherwise the quality value.
  int quality() {
    return this.quality;
  }
}
