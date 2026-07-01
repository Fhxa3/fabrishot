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

public final class Png {

    public static int compressionLevel = 9;

    private Png() {
    }

    static void load(Properties p) {
        compressionLevel = Integer.parseInt(p.getProperty("png_compression", "9"));
    }

    static void save(Properties p) {
        p.put("png_compression", String.valueOf(compressionLevel));
    }

    public static void configureRecorder(FFmpegFrameRecorder recorder, int components) {
        recorder.setVideoCodec(avcodec.AV_CODEC_ID_PNG);
        recorder.setPixelFormat(avutil.AV_PIX_FMT_RGB24);
        recorder.setVideoOption("compression_level", String.valueOf(compressionLevel));
        recorder.setVideoOption("pred", "5");
    }

    @SuppressWarnings("rawtypes")
    static void addEntries(ConfigCategory category, ConfigEntryBuilder entryBuilder,
                           EnumListEntry<FileFormat> formatSelector) {
        category.addEntry(entryBuilder.startIntSlider(
                        Component.translatable("fabrishot.config.png_compression"), compressionLevel, 0, 9)
                .setTooltip(Component.translatable("fabrishot.config.png_compression.tooltip"))
                .setDefaultValue(6)
                .setSaveConsumer(i -> compressionLevel = i)
                .setDisplayRequirement(Requirement.isValue(formatSelector, FileFormat.PNG))
                .build());
    }
}
