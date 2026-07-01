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

public final class Avif {

    /**
     * Chroma subsampling modes for AVIF output.
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
                case YUV444 -> avutil.AV_PIX_FMT_YUV444P;
                case YUV422 -> avutil.AV_PIX_FMT_YUV422P;
                case YUV420 -> avutil.AV_PIX_FMT_YUV420P;
            };
        }
    }

    /**
     * Tuning modes for the libaom-av1 encoder used by AVIF output.
     */
    public enum Tune {
        SSIM,
        PSNR;

        public String ffmpegValue() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static int cpuUsed = 4;
    public static int crf = 24;
    public static Tune tune = Tune.SSIM;
    public static ColorSampling colorSampling = ColorSampling.YUV420;

    private Avif() {
    }

    static void load(Properties p) {
        cpuUsed = Integer.parseInt(p.getProperty("avif_cpu_used", "4"));
        crf = Integer.parseInt(p.getProperty("avif_crf", "24"));
        tune = Tune.valueOf(p.getProperty("avif_tune", "SSIM"));
        colorSampling = ColorSampling.valueOf(p.getProperty("avif_color_sampling", "YUV420"));
    }

    static void save(Properties p) {
        p.put("avif_cpu_used", String.valueOf(cpuUsed));
        p.put("avif_crf", String.valueOf(crf));
        p.put("avif_tune", String.valueOf(tune));
        p.put("avif_color_sampling", String.valueOf(colorSampling));
    }

    public static void configureRecorder(FFmpegFrameRecorder recorder) {
        recorder.setVideoCodec(avcodec.AV_CODEC_ID_AV1);
        recorder.setPixelFormat(colorSampling.ffmpegPixelFormat());
        // Single-image AVIF encoding
        recorder.setVideoOption("usage", "allintra");
        recorder.setVideoOption("still-picture", "1");
        recorder.setVideoBitrate(0);
        recorder.setVideoOption("enable-intrabc", "1");
        recorder.setVideoOption("enable-palette", "1");
        recorder.setVideoOption("row-mt", "1");
        // User-configurable
        recorder.setVideoOption("cpu-used", String.valueOf(cpuUsed));
        recorder.setVideoOption("crf", String.valueOf(crf));
        recorder.setVideoOption("tune", tune.ffmpegValue());
    }

    @SuppressWarnings("rawtypes")
    static void addEntries(ConfigCategory category, ConfigEntryBuilder entryBuilder,
                           EnumListEntry<FileFormat> formatSelector) {
        category.addEntry(entryBuilder.startIntSlider(
                        Component.translatable("fabrishot.config.avif_cpu_used"), cpuUsed, 0, 8)
                .setTooltip(Component.translatable("fabrishot.config.avif_cpu_used.tooltip"))
                .setDefaultValue(4)
                .setSaveConsumer(i -> cpuUsed = i)
                .setDisplayRequirement(Requirement.isValue(formatSelector, FileFormat.AVIF))
                .build());

        category.addEntry(entryBuilder.startIntSlider(
                        Component.translatable("fabrishot.config.avif_crf"), crf, 0, 63)
                .setTooltip(Component.translatable("fabrishot.config.avif_crf.tooltip"))
                .setDefaultValue(24)
                .setSaveConsumer(i -> crf = i)
                .setDisplayRequirement(Requirement.isValue(formatSelector, FileFormat.AVIF))
                .build());

        category.addEntry(entryBuilder.startEnumSelector(
                        Component.translatable("fabrishot.config.avif_tune"), Tune.class, tune)
                .setTooltip(Component.translatable("fabrishot.config.avif_tune.tooltip"))
                .setDefaultValue(Tune.SSIM)
                .setSaveConsumer(t -> tune = t)
                .setDisplayRequirement(Requirement.isValue(formatSelector, FileFormat.AVIF))
                .build());

        category.addEntry(entryBuilder.startEnumSelector(
                        Component.translatable("fabrishot.config.avif_color_sampling"),
                        ColorSampling.class, colorSampling)
                .setTooltip(Component.translatable("fabrishot.config.avif_color_sampling.tooltip"))
                .setDefaultValue(ColorSampling.YUV420)
                .setSaveConsumer(s -> colorSampling = s)
                .setDisplayRequirement(Requirement.isValue(formatSelector, FileFormat.AVIF))
                .build());
    }
}
