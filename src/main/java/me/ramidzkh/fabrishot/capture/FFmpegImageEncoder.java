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

import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_CAP_EXPERIMENTAL;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_FLAG_QSCALE;
import static org.bytedeco.ffmpeg.global.avcodec.FF_COMPLIANCE_EXPERIMENTAL;
import static org.bytedeco.ffmpeg.global.avcodec.av_packet_alloc;
import static org.bytedeco.ffmpeg.global.avcodec.av_packet_free;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_alloc_context3;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_find_encoder_by_name;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_free_context;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_open2;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_parameters_from_context;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_receive_packet;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_send_frame;
import static org.bytedeco.ffmpeg.global.avformat.AVIO_FLAG_WRITE;
import static org.bytedeco.ffmpeg.global.avformat.av_interleaved_write_frame;
import static org.bytedeco.ffmpeg.global.avformat.av_write_trailer;
import static org.bytedeco.ffmpeg.global.avformat.avformat_alloc_output_context2;
import static org.bytedeco.ffmpeg.global.avformat.avformat_free_context;
import static org.bytedeco.ffmpeg.global.avformat.avformat_new_stream;
import static org.bytedeco.ffmpeg.global.avformat.avformat_write_header;
import static org.bytedeco.ffmpeg.global.avformat.avio_closep;
import static org.bytedeco.ffmpeg.global.avformat.avio_open;
import static org.bytedeco.ffmpeg.global.avutil.AVERROR_EOF;
import static org.bytedeco.ffmpeg.global.avutil.AV_LOG_ERROR;
import static org.bytedeco.ffmpeg.global.avutil.AV_LOG_INFO;
import static org.bytedeco.ffmpeg.global.avutil.AV_NOPTS_VALUE;
import static org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_NONE;
import static org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGBA;
import static org.bytedeco.ffmpeg.global.avutil.av_dict_free;
import static org.bytedeco.ffmpeg.global.avutil.av_dict_set;
import static org.bytedeco.ffmpeg.global.avutil.av_frame_alloc;
import static org.bytedeco.ffmpeg.global.avutil.av_frame_free;
import static org.bytedeco.ffmpeg.global.avutil.av_free;
import static org.bytedeco.ffmpeg.global.avutil.av_get_pix_fmt;
import static org.bytedeco.ffmpeg.global.avutil.av_image_fill_arrays;
import static org.bytedeco.ffmpeg.global.avutil.av_image_get_buffer_size;
import static org.bytedeco.ffmpeg.global.avutil.av_log_set_level;
import static org.bytedeco.ffmpeg.global.avutil.av_make_q;
import static org.bytedeco.ffmpeg.global.avutil.av_malloc;
import static org.bytedeco.ffmpeg.global.avutil.av_rescale_q;
import static org.bytedeco.ffmpeg.global.avutil.av_strerror;
import static org.bytedeco.ffmpeg.global.swscale.SWS_BILINEAR;
import static org.bytedeco.ffmpeg.global.swscale.sws_freeContext;
import static org.bytedeco.ffmpeg.global.swscale.sws_getContext;
import static org.bytedeco.ffmpeg.global.swscale.sws_scale;
import static org.bytedeco.ffmpeg.presets.avutil.AVERROR_EAGAIN;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import me.ramidzkh.fabrishot.config.Config;
import me.ramidzkh.fabrishot.config.FileFormat;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bytedeco.ffmpeg.avcodec.AVCodec;
import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avformat.AVIOContext;
import org.bytedeco.ffmpeg.avformat.AVStream;
import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.ffmpeg.swscale.SwsContext;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.DoublePointer;

// Skip JavaCV and hit FFmpeg directly.
public final class FFmpegImageEncoder {

  private static final Logger LOGGER = LogManager.getLogger(FFmpegImageEncoder.class);
  private static final int ERROR_BUFFER_SIZE = 256;
  private static final int ALIGNMENT = 1;
  private static final int TIME_BASE_NUMERATOR = 1;
  private static final int TIME_BASE_DENOMINATOR = 25;
  private static final int FF_QP2LAMBDA = 118;
  private static final String MUXER_IMAGE2 = "image2";

  private FFmpegImageEncoder() {}
  public static void write(NativeImage image, Path file, FileFormat format) throws IOException {
    encode(
        image.getPointer(),
        image.getWidth(),
        image.getHeight(),
        file,
        EncoderProfile.create(format, image.getWidth(), image.getHeight()),
        format);
  }

  static void encode(
      long rgbaAddress, int width, int height, Path file, EncoderProfile profile, FileFormat format)
      throws IOException {
    av_log_set_level(Config.DEBUG_ENCODE_STATS ? AV_LOG_INFO : AV_LOG_ERROR);

    Path output = file.resolveSibling(file.getFileName() + ".tmp");

    try {
      encodeInto(rgbaAddress, width, height, output, profile, format);
      Files.move(output, file, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException | RuntimeException exception) {
      deleteTemporary(output);
      throw exception;
    }
  }

  private static void encodeInto(
      long rgbaAddress,
      int width,
      int height,
      Path output,
      EncoderProfile profile,
      FileFormat format)
      throws IOException {
    long pixels = (long) width * height;
    boolean stats = Config.DEBUG_ENCODE_STATS;
    long startNanos = System.nanoTime();

    int pixelFormat = av_get_pix_fmt(profile.pixelFormat());

    if (pixelFormat == AV_PIX_FMT_NONE) {
      throw new IOException("Unknown FFmpeg pixel format '" + profile.pixelFormat() + "'");
    }

    // PNG and TIFF can just stay as-is; the rest need converting.
    boolean zeroCopy = pixelFormat == AV_PIX_FMT_RGBA;
    long conversionBytes = zeroCopy ? 0L : conversionBufferSize(pixelFormat, width, height);

    AVCodec codec = avcodec_find_encoder_by_name(profile.codec());

    if (codec == null || codec.isNull()) {
      throw new IOException("The FFmpeg encoder '" + profile.codec() + "' is not available");
    }

    AVCodecContext codecContext = null;
    AVFrame frame = null;
    AVFrame sourceFrame = null;
    AVPacket packet = null;
    AVFormatContext formatContext = null;
    AVIOContext ioContext = null;
    SwsContext swsContext = null;
    BytePointer converted = null;

    try {
      codecContext = avcodec_alloc_context3(codec);

      if (codecContext == null || codecContext.isNull()) {
        throw new IOException("Could not allocate an FFmpeg codec context");
      }

      codecContext.width(width);
      codecContext.height(height);
      codecContext.pix_fmt(pixelFormat);
      codecContext.time_base(av_make_q(TIME_BASE_NUMERATOR, TIME_BASE_DENOMINATOR));
      codecContext.gop_size(1);
      codecContext.max_b_frames(0);
      codecContext.thread_count(0);

      if ((codec.capabilities() & AV_CODEC_CAP_EXPERIMENTAL) != 0) {
        codecContext.strict_std_compliance(FF_COMPLIANCE_EXPERIMENTAL);
      }

      if (profile.quality() > 0) {
        codecContext.global_quality(profile.quality() * FF_QP2LAMBDA);
        codecContext.flags(codecContext.flags() | AV_CODEC_FLAG_QSCALE);
      }

      AVDictionary options = new AVDictionary();

      for (Map.Entry<String, String> option : profile.options().entrySet()) {
        av_dict_set(options, option.getKey(), option.getValue(), 0);
      }

      long openStartNanos = System.nanoTime();
      int result = avcodec_open2(codecContext, codec, options);
      av_dict_free(options);
      long openMillis = millisSince(openStartNanos);

      if (result < 0) {
        throw new IOException(
            "avcodec_open2 failed for " + profile.codec() + ": " + errorString(result));
      }

      long convertStartNanos = System.nanoTime();
      frame = av_frame_alloc();

      if (frame == null || frame.isNull()) {
        throw new IOException("Could not allocate an FFmpeg frame");
      }

      BytePointer source =
          new AddressBytePointer(rgbaAddress, pixels * FormatLimits.BYTES_PER_PIXEL);

      if (zeroCopy) {
        av_image_fill_arrays(
            frame.data(), frame.linesize(), source, pixelFormat, width, height, ALIGNMENT);
      } else {
        converted = new BytePointer(av_malloc(conversionBytes)).capacity(conversionBytes);

        if (converted.isNull()) {
          throw new IOException(
              "Could not allocate a " + conversionBytes + " byte conversion buffer");
        }

        sourceFrame = av_frame_alloc();

        if (sourceFrame == null || sourceFrame.isNull()) {
          throw new IOException("Could not allocate an FFmpeg frame");
        }

        av_image_fill_arrays(
            sourceFrame.data(),
            sourceFrame.linesize(),
            source,
            AV_PIX_FMT_RGBA,
            width,
            height,
            ALIGNMENT);
        av_image_fill_arrays(
            frame.data(), frame.linesize(), converted, pixelFormat, width, height, ALIGNMENT);
        sourceFrame.format(AV_PIX_FMT_RGBA).width(width).height(height);
        swsContext =
            sws_getContext(
                width,
                height,
                AV_PIX_FMT_RGBA,
                width,
                height,
                pixelFormat,
                SWS_BILINEAR,
                null,
                null,
                (DoublePointer) null);

        if (swsContext == null || swsContext.isNull()) {
          throw new IOException(
              "Could not create a libswscale context for " + profile.pixelFormat());
        }

        sws_scale(
            swsContext,
            sourceFrame.data(),
            sourceFrame.linesize(),
            0,
            height,
            frame.data(),
            frame.linesize());
      }

      // avcodec_send_frame rejects the frame when these are left unset.
      frame.format(pixelFormat).width(width).height(height);
      frame.pts(0);

      if (profile.quality() > 0) {
        frame.quality(profile.quality() * FF_QP2LAMBDA);
      }

      long convertMillis = millisSince(convertStartNanos);

      formatContext = new AVFormatContext(null);
      result =
          avformat_alloc_output_context2(formatContext, null, profile.muxer(), output.toString());

      if (result < 0 || formatContext.oformat() == null) {
        throw new IOException(
            "Could not create an FFmpeg '"
                + profile.muxer()
                + "' output context: "
                + errorString(result));
      }

      AVStream stream = avformat_new_stream(formatContext, null);

      if (stream == null || stream.isNull()) {
        throw new IOException("Could not create an FFmpeg output stream");
      }

      avcodec_parameters_from_context(stream.codecpar(), codecContext);
      stream.time_base(codecContext.time_base());

      // Flashback's FFmpeg movenc (avif, mp4) derefs the output context while writing the header, so the file has to be open and attached before we call avformat_write_header.
      ioContext = new AVIOContext(null);
      result = avio_open(ioContext, output.toString(), AVIO_FLAG_WRITE);

      if (result < 0) {
        throw new IOException("Could not open " + output + ": " + errorString(result));
      }

      formatContext.pb(ioContext);
      AVDictionary headerOptions = new AVDictionary();

      if (MUXER_IMAGE2.equals(profile.muxer())) {
        // Prevent any troublemaker from using '%d' as the file name.
        av_dict_set(headerOptions, "update", "1", 0);
      }

      result = avformat_write_header(formatContext, headerOptions);
      av_dict_free(headerOptions);

      if (result < 0) {
        throw new IOException("Could not write the FFmpeg header: " + errorString(result));
      }

      long encodeStartNanos = System.nanoTime();
      packet = av_packet_alloc();

      if (packet == null || packet.isNull()) {
        throw new IOException("Could not allocate an FFmpeg packet");
      }

      result = avcodec_send_frame(codecContext, frame);

      if (result < 0) {
        throw new IOException("avcodec_send_frame failed: " + errorString(result));
      }

      int packets = drainPackets(codecContext, formatContext, stream, packet, false);
      result = avcodec_send_frame(codecContext, null);

      if (result < 0) {
        throw new IOException("Flushing the encoder failed: " + errorString(result));
      }

      packets += drainPackets(codecContext, formatContext, stream, packet, true);
      long encodeMillis = millisSince(encodeStartNanos);

      if (packets == 0) {
        throw new IOException(
            "The " + profile.codec() + " encoder produced no data for " + width + "x" + height);
      }

      long trailerStartNanos = System.nanoTime();
      result = av_write_trailer(formatContext);

      if (result < 0) {
        throw new IOException("av_write_trailer failed: " + errorString(result));
      }

      long trailerMillis = millisSince(trailerStartNanos);
      avio_closep(ioContext);
      ioContext = null;

      long fileSize = Files.size(output);

      if (fileSize == 0) {
        // For very large images, LZW encoder reports success but writes nothing.
        throw new IOException(
            "The " + profile.codec() + " encoder wrote an empty " + output.getFileName());
      }

      if (stats) {
        logStatistics(
            format,
            profile,
            width,
            height,
            pixels,
            fileSize,
            millisSince(startNanos),
            openMillis,
            convertMillis,
            encodeMillis,
            trailerMillis);
      }
    } finally {
      if (converted != null) {
        av_free(converted);
      }

      if (swsContext != null) {
        sws_freeContext(swsContext);
      }

      if (sourceFrame != null) {
        av_frame_free(sourceFrame);
      }

      if (frame != null) {
        av_frame_free(frame);
      }

      if (packet != null) {
        av_packet_free(packet);
      }

      if (ioContext != null && !ioContext.isNull()) {
        avio_closep(ioContext);
      }

      if (formatContext != null) {
        avformat_free_context(formatContext);
      }

      if (codecContext != null) {
        avcodec_free_context(codecContext);
      }
    }
  }

  private static long conversionBufferSize(int pixelFormat, int width, int height)
      throws IOException {
    long upperBound =
        (long) width * height * FormatLimits.BYTES_PER_PIXEL + (long) ALIGNMENT * height;

    if (upperBound > Integer.MAX_VALUE - 64L) {
      throw new IOException("A conversion buffer of " + upperBound + " bytes is too large");
    }

    int size = av_image_get_buffer_size(pixelFormat, width, height, ALIGNMENT);

    if (size < 0) {
      throw new IOException(
          "av_image_get_buffer_size failed for " + width + "x" + height + ": " + size);
    }

    return size;
  }

  private static int drainPackets(
      AVCodecContext codecContext,
      AVFormatContext formatContext,
      AVStream stream,
      AVPacket packet,
      boolean flushing)
      throws IOException {
    int packets = 0;
    int result;

    while ((result = avcodec_receive_packet(codecContext, packet)) >= 0) {
      packet.stream_index(stream.index());

      if (packet.pts() != AV_NOPTS_VALUE) {
        packet.pts(av_rescale_q(packet.pts(), codecContext.time_base(), stream.time_base()));
      }

      if (packet.dts() != AV_NOPTS_VALUE) {
        packet.dts(av_rescale_q(packet.dts(), codecContext.time_base(), stream.time_base()));
      }

      int written = av_interleaved_write_frame(formatContext, packet);

      if (written < 0) {
        throw new IOException("av_interleaved_write_frame failed: " + errorString(written));
      }

      packets++;
    }

    // AVERROR(EAGAIN) need use the value FFmpeg was built with.
    if (result != AVERROR_EOF && (flushing || result != AVERROR_EAGAIN())) {
      throw new IOException(
          "avcodec_receive_packet failed while "
              + (flushing ? "flushing" : "encoding")
              + ": "
              + errorString(result));
    }

    return packets;
  }

  private static final class AddressBytePointer extends BytePointer {
    private AddressBytePointer(long address, long size) {
      this.address = address;
      this.position = 0;
      this.limit = size;
      this.capacity = size;
    }
  }

  private static long millisSince(long startNanos) {
    return (System.nanoTime() - startNanos) / 1_000_000L;
  }

  private static String errorString(int error) {
    byte[] buffer = new byte[ERROR_BUFFER_SIZE];
    int length = av_strerror(error, buffer, buffer.length);

    if (length < 0) {
      return "error " + error;
    }

    String message = new String(buffer, 0, length, StandardCharsets.UTF_8).trim();

    // Cuz FFmpeg errors don't come with descriptions, so we gotta keep the error codes.
    // 骗你的其实是我看起来爽
    if (message.isEmpty()) {
      return "error " + error;
    }

    return message + " (error " + error + ")";
  }

  private static void deleteTemporary(Path temporary) {
    try {
      Files.deleteIfExists(temporary);
    } catch (IOException exception) {
      LOGGER.warn("Could not delete the temporary file {}", temporary, exception);
    }
  }

  private static void logStatistics(
      FileFormat format,
      EncoderProfile profile,
      int width,
      int height,
      long pixels,
      long fileSize,
      long totalMillis,
      long openMillis,
      long convertMillis,
      long encodeMillis,
      long trailerMillis) {
    double megapixels = pixels / 1_000_000.0;
    double megapixelsPerSecond = totalMillis > 0L ? megapixels * 1000.0 / totalMillis : 0.0;

    LOGGER.info(
        "Encoded {}x{} ({} Mpx) as {} with {} [{}] and {} in {} ms "
            + "(open2 {} ms, convert {} ms, encode {} ms, trailer {} ms), {} bytes, {} Mpx/s",
        width,
        height,
        megapixels,
        format,
        profile.codec(),
        profile.pixelFormat(),
        profile.options(),
        totalMillis,
        openMillis,
        convertMillis,
        encodeMillis,
        trailerMillis,
        fileSize,
        megapixelsPerSecond);
  }
}
