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

import java.util.Properties;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.api.Requirement;
import me.shedaniel.clothconfig2.gui.entries.EnumListEntry;
import net.minecraft.network.chat.Component;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameRecorder;

public final class Jpg {

    /**
     * JPEG chroma subsampling mode — controls colour resolution vs file size.
     */
    public enum ColorSampling {
        YUV444("4:4:4"),
        YUV422("4:2:2"),
        YUV420("4:2:0");

        private final String label;

        ColorSampling(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        int ffmpegPixelFormat() {
            return switch (this) {
                case YUV444 -> avutil.AV_PIX_FMT_YUVJ444P;
                case YUV422 -> avutil.AV_PIX_FMT_YUVJ422P;
                case YUV420 -> avutil.AV_PIX_FMT_YUVJ420P;
            };
        }
    }

    public static int quality = 5;
    public static ColorSampling colorSampling = ColorSampling.YUV420;

    private Jpg() {
    }

    static void load(Properties p) {
        quality = Integer.parseInt(p.getProperty("jpg_quality", "5"));
        colorSampling = ColorSampling.valueOf(p.getProperty("jpg_color_sampling", "YUV420"));
    }

    static void save(Properties p) {
        p.put("jpg_quality", String.valueOf(quality));
        p.put("jpg_color_sampling", String.valueOf(colorSampling));
    }

    public static void configureRecorder(FFmpegFrameRecorder recorder) {
        recorder.setVideoCodec(avcodec.AV_CODEC_ID_MJPEG);
        recorder.setPixelFormat(colorSampling.ffmpegPixelFormat());
        recorder.setVideoOption("huffman", "1");
        recorder.setVideoQuality(quality);
    }

    @SuppressWarnings("rawtypes")
    static void addEntries(ConfigCategory category, ConfigEntryBuilder entryBuilder,
                           EnumListEntry<FileFormat> formatSelector) {
        category.addEntry(entryBuilder.startIntSlider(
                        Component.translatable("fabrishot.config.jpg_quality"), quality, 2, 31)
                .setTooltip(Component.translatable("fabrishot.config.jpg_quality.tooltip"))
                .setDefaultValue(5)
                .setSaveConsumer(i -> quality = i)
                .setDisplayRequirement(Requirement.isValue(formatSelector, FileFormat.JPG))
                .build());

        category.addEntry(entryBuilder.startEnumSelector(
                        Component.translatable("fabrishot.config.jpg_color_sampling"),
                        ColorSampling.class, colorSampling)
                .setTooltip(Component.translatable("fabrishot.config.jpg_color_sampling.tooltip"))
                .setDefaultValue(ColorSampling.YUV420)
                .setSaveConsumer(s -> colorSampling = s)
                .setDisplayRequirement(Requirement.isValue(formatSelector, FileFormat.JPG))
                .build());
    }
}
