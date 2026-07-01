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

package me.ramidzkh.fabrishot.config;

import java.util.Locale;
import java.util.Properties;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.api.Requirement;
import me.shedaniel.clothconfig2.gui.entries.EnumListEntry;
import net.minecraft.network.chat.Component;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameRecorder;

public final class Tiff {

    /**
     * TIFF compression algorithms available through FFmpeg's tiff encoder.
     */
    public enum Compression {
        PACKBITS,
        RAW,
        LZW,
        DEFLATE;

        public String ffmpegValue() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Chroma subsampling / pixel format for TIFF output.
     */
    public enum ColorSampling {
        RGB24("RGB 4:4:4"),
        YUV444("YUV 4:4:4");

        private final String label;

        ColorSampling(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public static Compression compression = Compression.LZW;
    public static ColorSampling colorSampling = ColorSampling.RGB24;

    private Tiff() {
    }

    static void load(Properties p) {
        compression = Compression.valueOf(p.getProperty("tiff_compression", "LZW"));
        colorSampling = ColorSampling.valueOf(p.getProperty("tiff_color_sampling", "RGB24"));
    }

    static void save(Properties p) {
        p.put("tiff_compression", String.valueOf(compression));
        p.put("tiff_color_sampling", String.valueOf(colorSampling));
    }

    public static void configureRecorder(FFmpegFrameRecorder recorder, int components) {
        recorder.setVideoCodec(avcodec.AV_CODEC_ID_TIFF);
        recorder.setPixelFormat(switch (colorSampling) {
            case RGB24 -> components == 4 ? avutil.AV_PIX_FMT_RGBA : avutil.AV_PIX_FMT_RGB24;
            case YUV444 -> avutil.AV_PIX_FMT_YUV444P;
        });
        recorder.setVideoOption("compression_algo", compression.ffmpegValue());
    }

    @SuppressWarnings("rawtypes")
    static void addEntries(ConfigCategory category, ConfigEntryBuilder entryBuilder,
                           EnumListEntry<FileFormat> formatSelector) {
        category.addEntry(entryBuilder.startEnumSelector(
                        Component.translatable("fabrishot.config.tiff_compression"),
                        Compression.class, compression)
                .setTooltip(Component.translatable("fabrishot.config.tiff_compression.tooltip"))
                .setDefaultValue(Compression.LZW)
                .setSaveConsumer(c -> compression = c)
                .setDisplayRequirement(Requirement.isValue(formatSelector, FileFormat.TIFF))
                .build());

        category.addEntry(entryBuilder.startEnumSelector(
                        Component.translatable("fabrishot.config.tiff_color_sampling"),
                        ColorSampling.class, colorSampling)
                .setTooltip(Component.translatable("fabrishot.config.tiff_color_sampling.tooltip"))
                .setDefaultValue(ColorSampling.RGB24)
                .setSaveConsumer(s -> colorSampling = s)
                .setDisplayRequirement(Requirement.isValue(formatSelector, FileFormat.TIFF))
                .build());
    }
}
