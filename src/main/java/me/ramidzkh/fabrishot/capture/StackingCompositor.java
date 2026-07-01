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

package me.ramidzkh.fabrishot.capture;

import com.mojang.blaze3d.platform.NativeImage;
import me.ramidzkh.fabrishot.config.FileFormat;
import me.ramidzkh.fabrishot.config.StackMode;
import me.ramidzkh.fabrishot.event.ScreenshotSaveCallback;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class StackingCompositor {

    private static final Logger LOGGER = LogManager.getLogger(StackingCompositor.class);

    private StackingCompositor() {
    }

    public static void composite(Path tempDir, List<Path> tempFiles, Path output,
                                  FileFormat format, StackMode mode) {
        int count = tempFiles.size();
        if (count == 0) {
            LOGGER.error("No frames to composite");
            return;
        }

        if (count == 1) {
            try {
                Files.copy(tempFiles.get(0), output, StandardCopyOption.REPLACE_EXISTING);
                ScreenshotSaveCallback.EVENT.invoker().onSaved(output);
            } catch (IOException e) {
                LOGGER.error("Failed to copy single frame for stack output", e);
            }
            return;
        }

        List<NativeImage> images = new ArrayList<>(count);
        try {
            for (Path f : tempFiles) {
                images.add(NativeImage.read(Files.newInputStream(f)));
            }

            int width = images.get(0).getWidth();
            int height = images.get(0).getHeight();
            int[] blended = blend(images, width * height, mode);

            NativeImage result = new NativeImage(NativeImage.Format.RGB, width, height, false);
            writeRGB(result, blended);

            try (result) {
                FramebufferWriter.write(result, output);
            }

            LOGGER.info("Stack composite complete: {}", output);

        } catch (IOException e) {
            LOGGER.error("Composite failed", e);
        } finally {
            images.forEach(NativeImage::close);
            tempFiles.forEach(f -> { try { Files.deleteIfExists(f); } catch (IOException ignored) {} });
            try { Files.deleteIfExists(tempDir); } catch (IOException ignored) {}
        }
    }

    // ─── Blend dispatch ─────────────────────────────────────────

    private static int[] blend(List<NativeImage> images, int pixelCount, StackMode mode) {
        return switch (mode) {
            case AVERAGE -> blendAverage(images, pixelCount);
            case MAXIMUM -> blendMinMax(images, pixelCount, true);
            case MINIMUM -> blendMinMax(images, pixelCount, false);
            case MEDIAN  -> blendMedian(images, pixelCount);
        };
    }

    // ─── AVERAGE ─────────────────────────────────────────────────

    private static int[] blendAverage(List<NativeImage> images, int pixelCount) {
        int count = images.size();
        float[][] accum = new float[pixelCount][3];

        for (NativeImage img : images) {
            int[] src = img.getPixelsABGR();
            for (int i = 0; i < pixelCount; i++) {
                accum[i][0] += r(src[i]);
                accum[i][1] += g(src[i]);
                accum[i][2] += b(src[i]);
            }
        }

        int[] result = new int[pixelCount];
        for (int i = 0; i < pixelCount; i++) {
            result[i] = abgr(255,
                    clamp((int) (accum[i][0] / count)),
                    clamp((int) (accum[i][1] / count)),
                    clamp((int) (accum[i][2] / count)));
        }
        return result;
    }

    // ─── MAXIMUM / MINIMUM ──────────────────────────────────────

    private static int[] blendMinMax(List<NativeImage> images, int pixelCount, boolean max) {
        int[] result = images.get(0).getPixelsABGR().clone();

        for (int f = 1; f < images.size(); f++) {
            int[] src = images.get(f).getPixelsABGR();
            for (int i = 0; i < pixelCount; i++) {
                result[i] = max ? maxPixel(result[i], src[i]) : minPixel(result[i], src[i]);
            }
        }
        return result;
    }

    private static int maxPixel(int a, int b) {
        return abgr(255,
                Math.max(r(a), r(b)),
                Math.max(g(a), g(b)),
                Math.max(b(a), b(b)));
    }

    private static int minPixel(int a, int b) {
        return abgr(255,
                Math.min(r(a), r(b)),
                Math.min(g(a), g(b)),
                Math.min(b(a), b(b)));
    }

    // ─── MEDIAN ─────────────────────────────────────────────────

    private static int[] blendMedian(List<NativeImage> images, int pixelCount) {
        int count = images.size();
        int[][] all = new int[count][];
        for (int f = 0; f < count; f++) {
            all[f] = images.get(f).getPixelsABGR();
        }

        int[] result = new int[pixelCount];
        int[] vals = new int[count];
        int mid = count / 2;

        for (int i = 0; i < pixelCount; i++) {
            result[i] = abgr(255,
                    medianChannel(all, i, vals, mid, 16),
                    medianChannel(all, i, vals, mid, 8),
                    medianChannel(all, i, vals, mid, 0));
        }
        return result;
    }

    private static int medianChannel(int[][] frames, int pixelIdx, int[] buf, int mid, int shift) {
        for (int f = 0; f < frames.length; f++) {
            buf[f] = (frames[f][pixelIdx] >> shift) & 0xFF;
        }
        Arrays.sort(buf);
        return buf[mid];
    }

    // ─── ABGR helpers ───────────────────────────────────────────

    private static int abgr(int a, int r, int g, int b) { return (a << 24) | (r << 16) | (g << 8) | b; }
    private static int r(int abgr) { return (abgr >> 16) & 0xFF; }
    private static int g(int abgr) { return (abgr >> 8) & 0xFF; }
    private static int b(int abgr) { return abgr & 0xFF; }
    private static int clamp(int v) { return v < 0 ? 0 : Math.min(v, 255); }

    // ─── Write to NativeImage (RGB format, 3 bytes/pixel) ──────

    private static void writeRGB(NativeImage image, int[] pixels) {
        int pixelsCount = image.getWidth() * image.getHeight();
        long ptr = image.getPointer();
        for (int i = 0; i < pixelsCount; i++) {
            int abgr = pixels[i];
            long off = (long) i * 3;
            MemoryUtil.memPutByte(ptr + off, (byte) r(abgr));
            MemoryUtil.memPutByte(ptr + off + 1, (byte) g(abgr));
            MemoryUtil.memPutByte(ptr + off + 2, (byte) b(abgr));
        }
    }
}
