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
import me.ramidzkh.fabrishot.event.ScreenshotSaveCallback;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.stb.STBIWriteCallback;
import org.lwjgl.stb.STBImageWrite;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class FramebufferWriter {
    private static final Logger LOGGER = LogManager.getLogger(FramebufferWriter.class);

    public static void write(NativeImage image, Path file) throws IOException {
        var format = Config.CAPTURE_FILE_FORMAT;

        if (FlashbackDetector.AVAILABLE) {
            // FFmpeg available: route all formats through it for unified quality control
            FfmpegWriter.write(image, file, format);
        } else {
            // No FFmpeg: PNG/JPG use STB built-in; formats that need FFmpeg fall back to PNG
            if (format.needsFfmpeg()) {
                LOGGER.warn("Flashback mod is required for {} format — falling back to PNG", format);
                format = FileFormat.PNG;
                // Fix file extension: the original path was created with the wrong extension
                String name = file.getFileName().toString();
                int dot = name.lastIndexOf('.');
                if (dot > 0) {
                    file = file.resolveSibling(name.substring(0, dot) + FileFormat.PNG.extension());
                }
            }

            try (FileChannel fc = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 WriteCallback callback = new WriteCallback(fc)) {
                switch (format) {
                    case PNG -> STBImageWrite.nstbi_write_png_to_func(callback.address(), 0L, image.getWidth(), image.getHeight(), image.format().components(), image.getPointer(), 0);
                    case JPG -> STBImageWrite.nstbi_write_jpg_to_func(callback.address(), 0L, image.getWidth(), image.getHeight(), image.format().components(), image.getPointer(), 90);
                }

                if (callback.exception != null) {
                    throw callback.exception;
                }
            }
        }

        ScreenshotSaveCallback.EVENT.invoker().onSaved(file);
    }

    private static class WriteCallback extends STBIWriteCallback implements AutoCloseable, Closeable {
        private final WritableByteChannel channel;
        private IOException exception;

        private WriteCallback(WritableByteChannel channel) {
            this.channel = channel;
        }

        @Override
        public void invoke(long context, long data, int size) {
            if (this.exception != null) {
                return;
            }

            ByteBuffer buf = STBIWriteCallback.getData(data, size);

            try {
                this.channel.write(buf);
            } catch (IOException e) {
                this.exception = e;
            }
        }

        @Override
        public void close() {
            this.free();
        }
    }
}
