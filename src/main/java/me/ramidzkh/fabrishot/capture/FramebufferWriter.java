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

import com.mojang.blaze3d.platform.NativeImage;
import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import me.ramidzkh.fabrishot.Fabrishot;
import me.ramidzkh.fabrishot.config.FileFormat;
import me.ramidzkh.fabrishot.event.ScreenshotSaveCallback;
import net.minecraft.network.chat.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.stb.STBIWriteCallback;
import org.lwjgl.stb.STBImageWrite;

// If FFmpeg supports it, just do it; if not, fall back.
// But I can't write fallbacks for the other formats, so it just throws an error.
public class FramebufferWriter {

  private static final Logger LOGGER = LogManager.getLogger(FramebufferWriter.class);

  private static final int STB_JPG_QUALITY = 90;

  public static void write(NativeImage image, Path file, FileFormat format) throws IOException {
    if (image.isClosed()) {
      LOGGER.warn("Not writing {}, the image was already closed by a callback", file);
      return;
    }

    int width = image.getWidth();
    int height = image.getHeight();
    String reason = FormatLimits.reasonFor(format, width, height, FlashbackDetector.AVAILABLE);

    if (reason != null) {
      if (format.needsFfmpeg() && !FlashbackDetector.AVAILABLE) {
        throw missingFlashback(format);
      }

      throw tooLarge(format, width, height, reason);
    }

    if (FlashbackDetector.AVAILABLE && canEncodeWithFfmpeg(format, width, height)) {
      try {
        FFmpegImageEncoder.write(image, file, format);
      } catch (IOException exception) {
        if (format.needsFfmpeg()) {
          throw exception;
        }

        LOGGER.warn(
            "Could not encode the {}x{} {} screenshot with FFmpeg, using the built-in encoder",
            width,
            height,
            format,
            exception);
        writeWithStb(image, file, format);
      }
    } else {
      LOGGER.info(
          "Writing the {}x{} {} screenshot with the built-in encoder", width, height, format);
      writeWithStb(image, file, format);
    }

    ScreenshotSaveCallback.EVENT.invoker().onSaved(file);
  }

  private static boolean canEncodeWithFfmpeg(FileFormat format, int width, int height) {
    if (!FormatLimits.fitsFfmpeg(width, height)) {
      return false;
    }

    return switch (format) {
      case PNG, TIFF, AVIF -> true;
      case JPG -> width <= FormatLimits.JPEG_MAX_SIDE && height <= FormatLimits.JPEG_MAX_SIDE;
      case WEBP -> width <= FormatLimits.WEBP_MAX_SIDE && height <= FormatLimits.WEBP_MAX_SIDE;
    };
  }

  private static void writeWithStb(NativeImage image, Path file, FileFormat format)
      throws IOException {
    try (FileChannel channel =
            FileChannel.open(
                file,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
        WriteCallback callback = new WriteCallback(channel)) {
      int written =
          switch (format) {
            case PNG ->
                STBImageWrite.nstbi_write_png_to_func(
                    callback.address(),
                    0L,
                    image.getWidth(),
                    image.getHeight(),
                    image.format().components(),
                    image.getPointer(),
                    0);
            case JPG ->
                STBImageWrite.nstbi_write_jpg_to_func(
                    callback.address(),
                    0L,
                    image.getWidth(),
                    image.getHeight(),
                    image.format().components(),
                    image.getPointer(),
                    STB_JPG_QUALITY);
            default -> 0;
          };

      if (callback.exception != null) {
        throw callback.exception;
      }

      if (written == 0) {
        throw new IOException("Could not write " + file + " as " + format);
      }
    }
  }

  private static IOException tooLarge(FileFormat format, int width, int height, String reason) {
    Fabrishot.printError(
        Component.translatable(
            "fabrishot.error.too_many_pixels_for_format",
            width,
            height,
            Component.literal(format.name()),
            Component.literal(reason)));
    LOGGER.warn("Not saving a {}x{} screenshot as {}: {}", width, height, format, reason);
    return new IOException(width + "x" + height + " is too large for " + format + ": " + reason);
  }

  private static IOException missingFlashback(FileFormat format) {
    LOGGER.warn("The {} format needs Flashback's FFmpeg encoder, which is not available", format);
    return new IOException(format + " needs Flashback's FFmpeg encoder");
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
