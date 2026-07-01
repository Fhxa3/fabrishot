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
import me.ramidzkh.fabrishot.config.Config;
import me.ramidzkh.fabrishot.config.FileFormat;
import me.ramidzkh.fabrishot.config.JpgColorSampling;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.FFmpegLogCallback;
import org.bytedeco.javacv.Frame;

/**
 * Encodes a single {@link NativeImage} frame via JavaCV's FFmpeg backend.
 * <p>
 * Requires Flashback (which bundles JavaCV + FFmpeg native libraries) to be present at runtime.
 * All encoding goes through a temporary file that is atomically renamed on success,
 * preventing partially-written output on failure.
 */
public final class FfmpegWriter {

    private static final Logger LOGGER = LogManager.getLogger(FfmpegWriter.class);

    static {
        // CRITICAL: must be called before any recorder is created.
        // Without this, avcodec_open2 failures produce no diagnostic output — just error -22.
        FFmpegLogCallback.set();
    }

    private FfmpegWriter() {
    }

    /**
     * Encodes the given image via FFmpeg.
     *
     * @param image  the framebuffer capture, never freed before this method returns
     * @param file   destination path; a temporary sibling is used during encoding
     * @param format the target file format, determines codec and encoder options
     * @throws IOException if FFmpeg encoding fails
     */
    public static void write(NativeImage image, Path file, FileFormat format) throws IOException {
        int width = image.getWidth();
        int height = image.getHeight();
        int components = image.format().components();
        int dataSize = width * height * components;

        Path tmpFile = file.resolveSibling(file.getFileName() + ".tmp");

        // Copy pixel data out of the NativeImage while it is still alive.
        // We use a defensive copy so the recorder can hold the buffer safely.
        BytePointer data = new BytePointer(dataSize);
        // Wrap the NativeImage's native pointer without allocating; address is protected,
        // so we use an anonymous subclass initializer to set it.
        final long nativeAddr = image.getPointer();
        Pointer src = new Pointer() {{
            address = nativeAddr;
        }};
        Pointer.memcpy(data, src, dataSize);

        Frame frame = new Frame(width, height, 8, components);
        frame.image[0] = data.asByteBuffer();

        FFmpegFrameRecorder recorder = null;
        boolean encoded = false;

        try {
            recorder = new FFmpegFrameRecorder(tmpFile.toFile(), width, height);
            recorder.setFormat("image2");
            configureForFormat(recorder, format, components);
            ColorSpaceHelper.apply(recorder, ColorSpaceHelper.resolve());

            recorder.start();
            recorder.record(frame);
            encoded = true;
        } catch (FFmpegFrameRecorder.Exception e) {
            throw new IOException("Failed to encode image via FFmpeg", e);
        } finally {
            // Always stop + release the recorder, even on failure
            if (recorder != null) {
                try {
                    recorder.stop();
                } catch (FFmpegFrameRecorder.Exception ex) {
                    LOGGER.warn("Error stopping FFmpeg recorder", ex);
                }
                try {
                    recorder.release();
                } catch (FFmpegFrameRecorder.Exception ex) {
                    LOGGER.warn("Error releasing FFmpeg recorder", ex);
                }
            }
            // Free the defensive copy; src merely views NativeImage memory (address=0 prevents any dealloc)
            data.close();
            src.close();

            if (!encoded) {
                try {
                    Files.deleteIfExists(tmpFile);
                } catch (IOException ex) {
                    LOGGER.warn("Failed to delete incomplete temp file {}", tmpFile, ex);
                }
            }
        }

        // Only move to the final destination after the recorder is fully stopped and released
        Files.move(tmpFile, file, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Selects codec and sets encoder options based on the target format.
     */
    private static void configureForFormat(FFmpegFrameRecorder recorder, FileFormat format, int components) {
        switch (format) {
            case PNG -> {
                recorder.setVideoCodec(avcodec.AV_CODEC_ID_PNG);
                recorder.setPixelFormat(components == 4 ? avutil.AV_PIX_FMT_RGBA : avutil.AV_PIX_FMT_RGB24);
                recorder.setVideoOption("compression_level", String.valueOf(Config.PNG_COMPRESSION_LEVEL));
            }
            case JPG -> {
                recorder.setVideoCodec(avcodec.AV_CODEC_ID_MJPEG);
                recorder.setPixelFormat(colorSamplingToPixelFormat(Config.JPG_COLOR_SAMPLING));
                recorder.setVideoOption("q:v", String.valueOf(Config.JPG_QUALITY));
            }
            case WEBP -> {
                recorder.setVideoCodec(avcodec.AV_CODEC_ID_WEBP);
                recorder.setPixelFormat(avutil.AV_PIX_FMT_BGRA);
                recorder.setVideoOption("lossless", Config.WEBP_LOSSLESS ? "1" : "0");
                recorder.setVideoQuality(Config.WEBP_QUALITY);
            }
            case TIFF -> {
                recorder.setVideoCodec(avcodec.AV_CODEC_ID_TIFF);
                recorder.setPixelFormat(components == 4 ? avutil.AV_PIX_FMT_RGBA : avutil.AV_PIX_FMT_RGB24);
                recorder.setVideoOption("compression_algo", Config.TIFF_COMPRESSION.ffmpegValue());
            }
        }
    }

    private static int colorSamplingToPixelFormat(JpgColorSampling sampling) {
        return switch (sampling) {
            case YUV444 -> avutil.AV_PIX_FMT_YUVJ444P;
            case YUV422 -> avutil.AV_PIX_FMT_YUVJ422P;
            case YUV420 -> avutil.AV_PIX_FMT_YUVJ420P;
        };
    }
}
