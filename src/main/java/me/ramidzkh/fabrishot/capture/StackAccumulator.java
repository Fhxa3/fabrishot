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
import java.nio.IntBuffer;
import me.ramidzkh.fabrishot.config.StackMode;
import org.lwjgl.system.MemoryUtil;

// Capture frames in memory and blend into a single frame.
// With the recursive low memory mode they keep a Q16 running mean instead, which halves the
// accumulator memory at the cost of a little precision and speed.
final class StackAccumulator {

  private static final int OPAQUE = 0xFF000000;
  private static final long PHI = 0x9E3779B97F4A7C15L;
  private static final long MIX_A = 0xBF58476D1CE4E5B9L;
  private static final long MIX_B = 0x94D049BB133111EBL;

  private final int width;
  private final int height;
  private final int pixelCount;
  private final StackMode mode;
  private final boolean recursiveMean;

  private final int[] redSum;
  private final int[] greenSum;
  private final int[] blueSum;
  private int[] extreme;

  private final char[] meanRed;
  private final char[] meanGreen;
  private final char[] meanBlue;

  private int frameCount;

  StackAccumulator(int width, int height, StackMode mode, boolean recursiveMean) {
    this.width = width;
    this.height = height;
    this.pixelCount = Math.multiplyExact(width, height);
    this.mode = mode;
    this.recursiveMean = recursiveMean && mode.isMean();

    if (this.recursiveMean) {
      this.redSum = null;
      this.greenSum = null;
      this.blueSum = null;
      this.meanRed = new char[pixelCount];
      this.meanGreen = new char[pixelCount];
      this.meanBlue = new char[pixelCount];
    } else {
      this.meanRed = null;
      this.meanGreen = null;
      this.meanBlue = null;

      if (mode.isMean()) {
        this.redSum = new int[pixelCount];
        this.greenSum = new int[pixelCount];
        this.blueSum = new int[pixelCount];
      } else {
        this.redSum = null;
        this.greenSum = null;
        this.blueSum = null;
      }
    }
  }

  int frameCount() {
    return frameCount;
  }

  void accumulate(NativeImage image) {
    if (image.getWidth() != width || image.getHeight() != height) {
      String message =
          "Expected a "
              + width
              + "x"
              + height
              + " frame, got "
              + image.getWidth()
              + "x"
              + image.getHeight();
      throw new IllegalArgumentException(message);
    }

    IntBuffer source = MemoryUtil.memIntBuffer(image.getPointer(), pixelCount);

    if (recursiveMean) {
      accumulateMean(source);
    } else {
      switch (mode) {
        case LINEAR_MEAN -> accumulateLinear(source);
        case MAXIMUM -> accumulateExtreme(source, true);
        case MINIMUM -> accumulateExtreme(source, false);
      }
    }

    frameCount++;
  }

  NativeImage finish() {
    if (frameCount == 0) {
      throw new IllegalStateException("Cannot finish an empty stack");
    }

    NativeImage image = new NativeImage(NativeImage.Format.RGBA, width, height, false);
    IntBuffer target = MemoryUtil.memIntBuffer(image.getPointer(), pixelCount);

    if (recursiveMean) {
      finishMean(target);
    } else {
      switch (mode) {
        case LINEAR_MEAN -> {
          byte[] encode = Tables.LINEAR_TO_SRGB;

          for (int i = 0; i < pixelCount; i++) {
            target.put(
                i,
                pack(
                    encode[redSum[i] / frameCount] & 0xFF,
                    encode[greenSum[i] / frameCount] & 0xFF,
                    encode[blueSum[i] / frameCount] & 0xFF));
          }
        }
        case MAXIMUM, MINIMUM -> target.put(extreme);
      }
    }

    return image;
  }

  private void accumulateLinear(IntBuffer source) {
    char[] decode = Tables.SRGB_TO_LINEAR;

    for (int i = 0; i < pixelCount; i++) {
      int pixel = source.get(i);
      redSum[i] += decode[pixel & 0xFF];
      greenSum[i] += decode[(pixel >>> 8) & 0xFF];
      blueSum[i] += decode[(pixel >>> 16) & 0xFF];
    }
  }

  private void accumulateExtreme(IntBuffer source, boolean maximum) {
    if (extreme == null) {
      extreme = new int[pixelCount];

      for (int i = 0; i < pixelCount; i++) {
        extreme[i] = source.get(i) | OPAQUE;
      }

      return;
    }

    for (int i = 0; i < pixelCount; i++) {
      int current = extreme[i];
      int next = source.get(i);
      int red =
          maximum
              ? Math.max(current & 0xFF, next & 0xFF)
              : Math.min(current & 0xFF, next & 0xFF);
      int green =
          maximum
              ? Math.max((current >>> 8) & 0xFF, (next >>> 8) & 0xFF)
              : Math.min((current >>> 8) & 0xFF, (next >>> 8) & 0xFF);
      int blue =
          maximum
              ? Math.max((current >>> 16) & 0xFF, (next >>> 16) & 0xFF)
              : Math.min((current >>> 16) & 0xFF, (next >>> 16) & 0xFF);
      extreme[i] = pack(red, green, blue);
    }
  }

  private void accumulateMean(IntBuffer source) {
    if (frameCount == 0) {
      initializeMean(source);
      return;
    }

    long reciprocal = (1L << 32) / (frameCount + 1);
    char[] decode = Tables.SRGB_TO_LINEAR;

    for (int i = 0; i < pixelCount; i++) {
      int pixel = source.get(i);
      long random = mix64(i * PHI + frameCount);
      int red = updateMean(meanRed[i], decode[pixel & 0xFF], reciprocal, (int) random & 0xFFFF);
      int green =
          updateMean(
              meanGreen[i],
              decode[(pixel >>> 8) & 0xFF],
              reciprocal,
              (int) (random >>> 16) & 0xFFFF);
      int blue =
          updateMean(
              meanBlue[i],
              decode[(pixel >>> 16) & 0xFF],
              reciprocal,
              (int) (random >>> 32) & 0xFFFF);
      meanRed[i] = (char) red;
      meanGreen[i] = (char) green;
      meanBlue[i] = (char) blue;
    }
  }

  private void initializeMean(IntBuffer source) {
    char[] decode = Tables.SRGB_TO_LINEAR;

    for (int i = 0; i < pixelCount; i++) {
      int pixel = source.get(i);
      meanRed[i] = decode[pixel & 0xFF];
      meanGreen[i] = decode[(pixel >>> 8) & 0xFF];
      meanBlue[i] = decode[(pixel >>> 16) & 0xFF];
    }
  }

  private static int updateMean(int mean, int sample, long reciprocal, int random) {
    long product = (long) (sample - mean) * reciprocal;
    int delta = (int) (product >> 32);
    int fraction = (int) (product >> 16) & 0xFFFF;

    if (random < fraction) {
      delta++;
    }

    return mean + delta;
  }

  private void finishMean(IntBuffer target) {
    byte[] encode = Tables.LINEAR_TO_SRGB;

    for (int i = 0; i < pixelCount; i++) {
      target.put(
          i,
          pack(
              encode[meanRed[i]] & 0xFF,
              encode[meanGreen[i]] & 0xFF,
              encode[meanBlue[i]] & 0xFF));
    }
  }

  private static long mix64(long value) {
    long z = value + PHI;
    z = (z ^ (z >>> 30)) * MIX_A;
    z = (z ^ (z >>> 27)) * MIX_B;
    return z ^ (z >>> 31);
  }

  private static int pack(int red, int green, int blue) {
    return OPAQUE | (blue << 16) | (green << 8) | red;
  }

  // Q16 lookup tables.
  private static final class Tables {

    private static final char[] SRGB_TO_LINEAR = buildSrgbToLinear();
    private static final byte[] LINEAR_TO_SRGB = buildLinearToSrgb();

    private Tables() {}

    private static char[] buildSrgbToLinear() {
      char[] table = new char[256];

      for (int i = 0; i < table.length; i++) {
        table[i] = (char) Math.round(65535.0 * srgbToLinear(i / 255.0));
      }

      return table;
    }

    private static byte[] buildLinearToSrgb() {
      byte[] table = new byte[65536];

      for (int i = 0; i < table.length; i++) {
        table[i] = (byte) Math.round(255.0 * linearToSrgb(i / 65535.0));
      }

      return table;
    }

    private static double srgbToLinear(double value) {
      return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }

    private static double linearToSrgb(double value) {
      return value <= 0.0031308 ? value * 12.92 : 1.055 * Math.pow(value, 1.0 / 2.4) - 0.055;
    }
  }
}
