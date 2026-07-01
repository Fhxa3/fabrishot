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
import me.shedaniel.clothconfig2.gui.entries.BooleanListEntry;
import me.shedaniel.clothconfig2.gui.entries.EnumListEntry;
import net.minecraft.network.chat.Component;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameRecorder;

public final class Webp {

    public static int quality = 90;
    public static boolean lossless = false;

    private Webp() {
    }

    static void load(Properties p) {
        quality = Integer.parseInt(p.getProperty("webp_quality", "90"));
        lossless = Boolean.parseBoolean(p.getProperty("webp_lossless"));
    }

    static void save(Properties p) {
        p.put("webp_quality", String.valueOf(quality));
        p.put("webp_lossless", String.valueOf(lossless));
    }

    public static void configureRecorder(FFmpegFrameRecorder recorder) {
        recorder.setVideoCodec(avcodec.AV_CODEC_ID_WEBP);
        recorder.setPixelFormat(avutil.AV_PIX_FMT_BGRA);
        recorder.setVideoOption("lossless", lossless ? "1" : "0");
        // In lossless mode quality controls encoding effort, not visual quality — always max it
        recorder.setVideoQuality(lossless ? 100 : quality);
    }

    @SuppressWarnings("rawtypes")
    static void addEntries(ConfigCategory category, ConfigEntryBuilder entryBuilder,
                           EnumListEntry<FileFormat> formatSelector) {
        var losslessEntry = entryBuilder.startBooleanToggle(
                        Component.translatable("fabrishot.config.webp_lossless"), lossless)
                .setTooltip(Component.translatable("fabrishot.config.webp_lossless.tooltip"))
                .setDefaultValue(false)
                .setSaveConsumer(b -> lossless = b)
                .setDisplayRequirement(Requirement.isValue(formatSelector, FileFormat.WEBP))
                .build();
        category.addEntry(losslessEntry);

        category.addEntry(entryBuilder.startIntSlider(
                        Component.translatable("fabrishot.config.webp_quality"), quality, 0, 100)
                .setTooltip(Component.translatable("fabrishot.config.webp_quality.tooltip"))
                .setDefaultValue(90)
                .setSaveConsumer(i -> quality = i)
                .setDisplayRequirement(Requirement.all(
                        Requirement.isValue(formatSelector, FileFormat.WEBP),
                        Requirement.isFalse(losslessEntry)))
                .build());
    }
}
