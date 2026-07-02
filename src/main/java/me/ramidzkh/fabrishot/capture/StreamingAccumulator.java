/*
 * MIT License
 *
 * Copyright (c) 2025 Ramid Khan
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
import me.ramidzkh.fabrishot.config.StackMode;
import me.ramidzkh.fabrishot.event.FramebufferCaptureCallback;
import me.ramidzkh.fabrishot.event.ScreenshotSaveCallback;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Accumulates pixel data frame-by-frame without writing to disk.
 * Supports AVERAGE (running sum), MAXIMUM, and MINIMUM modes.
 * MEDIAN is not supported — it requires all frames simultaneously.
 */
public final class StreamingAccumulator {

    private static final Logger LOGGER = LogManager.getLogger(StreamingAccumulator.class);

    private final int pixelCount;
    private final StackMode mode;
    private final Path output;
    private final int width;
    private final int height;
    private int frameCount;

    // For AVERAGE: running sum per channel
    private int[] sumR;
    private int[] sumG;
    private int[] sumB;

    // For MAX / MIN: running result
    private int[] running;

    private StreamingAccumulator(int width, int height, StackMode mode, Path output) {
        this.width = width;
        this.height = height;
        this.pixelCount = width * height;
        this.mode = mode;
        this.output = output;

        if (mode == StackMode.AVERAGE) {
            sumR = new int[pixelCount];
            sumG = new int[pixelCount];
            sumB = new int[pixelCount];
        }
        // MAX/MIN: running is allocated on first frame
    }

    public static StreamingAccumulator create(int width, int height, StackMode mode, Path output) {
        return new StreamingAccumulator(width, height, mode, output);
    }

    /**
     * Feeds one frame into the accumulator. The NativeImage is not retained.
     */
    public void accumulate(NativeImage image) {
        int[] src = image.getPixelsABGR();

        FramebufferCaptureCallback.EVENT.invoker().onCapture(image);

        switch (mode) {
            case AVERAGE -> accumulateAverage(src);
            case MAXIMUM -> accumulateMinMax(src, true);
            case MINIMUM -> accumulateMinMax(src, false);
            default -> throw new IllegalStateException("Streaming not supported for " + mode);
        }

        frameCount++;
    }

    /**
     * Produces the final blended image and writes it to disk.
     */
    public void finish() throws IOException {
        if (frameCount == 0) {
            LOGGER.error("No frames accumulated");
            return;
        }

        NativeImage result = new NativeImage(NativeImage.Format.RGB, width, height, false);

        switch (mode) {
            case AVERAGE -> finishAverage(result);
            case MAXIMUM, MINIMUM -> finishMinMax(result);
        }

        try (result) {
            FramebufferWriter.write(result, output);
        }

        ScreenshotSaveCallback.EVENT.invoker().onSaved(output);
        LOGGER.info("Streaming composite complete ({} frames): {}", frameCount, output);
    }

    // ─── AVERAGE ─────────────────────────────────────────────────

    private void accumulateAverage(int[] src) {
        for (int i = 0; i < pixelCount; i++) {
            int abgr = src[i];
            sumR[i] += (abgr >> 16) & 0xFF;
            sumG[i] += (abgr >> 8) & 0xFF;
            sumB[i] += abgr & 0xFF;
        }
    }

    private void finishAverage(NativeImage result) {
        int[] pixels = new int[pixelCount];
        for (int i = 0; i < pixelCount; i++) {
            int r = (sumR[i] / frameCount) & 0xFF;
            int g = (sumG[i] / frameCount) & 0xFF;
            int b = (sumB[i] / frameCount) & 0xFF;
            pixels[i] = (0xFF << 24) | (r << 16) | (g << 8) | b;
        }
        writePixels(result, pixels);
    }

    // ─── MAX / MIN ───────────────────────────────────────────────

    private void accumulateMinMax(int[] src, boolean max) {
        if (running == null) {
            running = src.clone();
            return;
        }
        for (int i = 0; i < pixelCount; i++) {
            running[i] = max ? maxPixel(running[i], src[i]) : minPixel(running[i], src[i]);
        }
    }

    private void finishMinMax(NativeImage result) {
        writePixels(result, running);
    }

    private static int maxPixel(int a, int b) {
        return (0xFF << 24)
                | (Math.max((a >> 16) & 0xFF, (b >> 16) & 0xFF) << 16)
                | (Math.max((a >> 8) & 0xFF, (b >> 8) & 0xFF) << 8)
                | Math.max(a & 0xFF, b & 0xFF);
    }

    private static int minPixel(int a, int b) {
        return (0xFF << 24)
                | (Math.min((a >> 16) & 0xFF, (b >> 16) & 0xFF) << 16)
                | (Math.min((a >> 8) & 0xFF, (b >> 8) & 0xFF) << 8)
                | Math.min(a & 0xFF, b & 0xFF);
    }

    // ─── Output ──────────────────────────────────────────────────

    private static void writePixels(NativeImage image, int[] pixels) {
        int pixelCount = image.getWidth() * image.getHeight();
        long ptr = image.getPointer();
        for (int i = 0; i < pixelCount; i++) {
            int abgr = pixels[i];
            long off = (long) i * 3;
            org.lwjgl.system.MemoryUtil.memPutByte(ptr + off, (byte) ((abgr >> 16) & 0xFF));
            org.lwjgl.system.MemoryUtil.memPutByte(ptr + off + 1, (byte) ((abgr >> 8) & 0xFF));
            org.lwjgl.system.MemoryUtil.memPutByte(ptr + off + 2, (byte) (abgr & 0xFF));
        }
    }
}
