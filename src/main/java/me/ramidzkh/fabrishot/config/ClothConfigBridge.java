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

package me.ramidzkh.fabrishot.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import java.util.Optional;
import java.util.function.Supplier;
import me.ramidzkh.fabrishot.Fabrishot;
import me.ramidzkh.fabrishot.capture.FlashbackDetector;
import me.ramidzkh.fabrishot.capture.FormatLimits;
import me.ramidzkh.fabrishot.config.Config.AvifColorSampling;
import me.ramidzkh.fabrishot.config.Config.AvifTune;
import me.ramidzkh.fabrishot.config.Config.JpgColorSampling;
import me.ramidzkh.fabrishot.config.Config.PngPrediction;
import me.ramidzkh.fabrishot.config.Config.TiffCompression;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.api.Requirement;
import me.shedaniel.clothconfig2.api.ValueHolder;
import me.shedaniel.clothconfig2.gui.entries.BooleanListEntry;
import me.shedaniel.clothconfig2.gui.entries.IntegerListEntry;
import me.shedaniel.clothconfig2.gui.entries.SelectionListEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ClothConfigBridge implements ConfigScreenFactory<Screen> {

  private static Optional<Component> tooManyPixelsError(
      IntegerListEntry width, IntegerListEntry height) {
    long pixels = (long) width.getValue() * height.getValue();
    long maxPixels = Fabrishot.maxCapturePixels();

    if (pixels <= maxPixels) {
      return Optional.empty();
    }

    return Optional.of(
        Component.translatable(
            "fabrishot.error.too_many_pixels",
            width.getValue(),
            height.getValue(),
            maxPixels / 1_000_000L));
  }

  private static Optional<Component> captureSizeError(
      IntegerListEntry width, IntegerListEntry height, SelectionListEntry<FileFormat> format) {
    Optional<Component> readback = tooManyPixelsError(width, height);
    
    if (readback.isPresent()) {
      return readback;
    }
    
    String reason =
        FormatLimits.reasonFor(
            format.getValue(), width.getValue(), height.getValue(), FlashbackDetector.AVAILABLE);

    if (reason == null) {
      return Optional.empty();
    }
    
    return Optional.of(
        Component.translatable(
            "fabrishot.error.format_too_many_pixels",
            width.getValue(),
            height.getValue(),
            Component.literal(format.getValue().name()),
            Component.literal(reason)));
  }

  @Override
  public Screen create(Screen parent) {
    ConfigBuilder builder =
        ConfigBuilder.create()
            .setTitle(Component.translatable("fabrishot.config.title"))
            .setSavingRunnable(Config::save)
            .setParentScreen(parent);
    ConfigEntryBuilder entryBuilder = builder.entryBuilder();
    ConfigCategory category =
        builder.getOrCreateCategory(Component.translatable("fabrishot.config.category"));

    // Check to prevent excessively high resolution.
    SelectionListEntry<FileFormat> formatSelector =
        entryBuilder
            .startSelector(
                Component.translatable("fabrishot.config.file_format"),
                FileFormat.valuesFor(FlashbackDetector.AVAILABLE),
                Config.CAPTURE_FILE_FORMAT)
            .setDefaultValue(FileFormat.PNG)
            .setSaveConsumer(format -> Config.CAPTURE_FILE_FORMAT = format)
            .build();

    category.addEntry(
        entryBuilder
            .startStrField(
                Component.translatable("fabrishot.config.custom_file_name"),
                Config.CUSTOM_FILE_NAME)
            .setTooltip(Component.translatable("fabrishot.config.custom_file_name.tooltip"))
            .setDefaultValue("huge_%time%")
            .setSaveConsumer(b -> Config.CUSTOM_FILE_NAME = b)
            .build());

    category.addEntry(
        entryBuilder
            .startBooleanToggle(
                Component.translatable("fabrishot.config.override_screenshot_key"),
                Config.OVERRIDE_SCREENSHOT_KEY)
            .setDefaultValue(false)
            .setSaveConsumer(b -> Config.OVERRIDE_SCREENSHOT_KEY = b)
            .build());

    category.addEntry(
        entryBuilder
            .startBooleanToggle(
                Component.translatable("fabrishot.config.hide_hud"), Config.HIDE_HUD)
            .setDefaultValue(false)
            .setSaveConsumer(b -> Config.HIDE_HUD = b)
            .build());

    category.addEntry(
        entryBuilder
            .startBooleanToggle(
                Component.translatable("fabrishot.config.save_file"), Config.SAVE_FILE)
            .setDefaultValue(true)
            .setSaveConsumer(b -> Config.SAVE_FILE = b)
            .build());

    IntegerListEntry width =
        entryBuilder
            .startIntField(Component.translatable("fabrishot.config.width"), Config.CAPTURE_WIDTH)
            .setDefaultValue(3840)
            .setMin(1)
            .setMax(Fabrishot.maxCaptureSide())
            .setSaveConsumer(i -> Config.CAPTURE_WIDTH = i)
            .build();
    category.addEntry(width);

    IntegerListEntry height =
        entryBuilder
            .startIntField(Component.translatable("fabrishot.config.height"), Config.CAPTURE_HEIGHT)
            .setDefaultValue(2160)
            .setMin(1)
            .setMax(Fabrishot.maxCaptureSide())
            .setSaveConsumer(i -> Config.CAPTURE_HEIGHT = i)
            .build();
    category.addEntry(height);

    Supplier<Optional<Component>> sizeError = () -> captureSizeError(width, height, formatSelector);
    width.setErrorSupplier(sizeError);
    height.setErrorSupplier(sizeError);

    category.addEntry(new ScalingPresetEntry(220, width, height));

    category.addEntry(
        entryBuilder
            .startIntField(Component.translatable("fabrishot.config.delay"), Config.CAPTURE_DELAY)
            .setTooltip(Component.translatable("fabrishot.config.delay.tooltip"))
            .setDefaultValue(3)
            .setMin(3)
            .setSaveConsumer(i -> Config.CAPTURE_DELAY = i)
            .build());

    category.addEntry(
        entryBuilder
            .startTextDescription(
                Component.translatable(
                    FlashbackDetector.AVAILABLE
                        ? "fabrishot.config.flashback.available"
                        : "fabrishot.config.flashback.unavailable"))
            .build());

    category.addEntry(formatSelector);

    if (FlashbackDetector.AVAILABLE) {
      addPngEntries(category, entryBuilder, formatSelector);
      addJpgEntries(category, entryBuilder, formatSelector);
      addWebpEntries(category, entryBuilder, formatSelector);
      addTiffEntries(category, entryBuilder, formatSelector);
      addAvifEntries(category, entryBuilder, formatSelector);
    }

    category.addEntry(
        entryBuilder
            .startBooleanToggle(
                Component.translatable("fabrishot.config.debug_encode_stats"),
                Config.DEBUG_ENCODE_STATS)
            .setTooltip(Component.translatable("fabrishot.config.debug_encode_stats.tooltip"))
            .setDefaultValue(false)
            .setSaveConsumer(b -> Config.DEBUG_ENCODE_STATS = b)
            .build());

    return builder.build();
  }

  // I was gonna split out the format-specific options, but honestly this doesn't look that shit.
  private static void addPngEntries(
      ConfigCategory category, ConfigEntryBuilder entryBuilder, ValueHolder<FileFormat> format) {
    category.addEntry(
        entryBuilder
            .startIntSlider(
                Component.translatable("fabrishot.config.png_compression"),
                Config.PNG_COMPRESSION,
                0,
                9)
            .setTooltip(Component.translatable("fabrishot.config.png_compression.tooltip"))
            .setDefaultValue(6)
            .setSaveConsumer(i -> Config.PNG_COMPRESSION = i)
            .setDisplayRequirement(Requirement.isValue(format, FileFormat.PNG))
            .build());

    category.addEntry(
        entryBuilder
            .startEnumSelector(
                Component.translatable("fabrishot.config.png_prediction"),
                PngPrediction.class,
                Config.PNG_PREDICTION)
            .setTooltip(Component.translatable("fabrishot.config.png_prediction.tooltip"))
            .setDefaultValue(PngPrediction.DEFAULT)
            .setSaveConsumer(p -> Config.PNG_PREDICTION = p)
            .setDisplayRequirement(Requirement.isValue(format, FileFormat.PNG))
            .build());
  }

  private static void addJpgEntries(
      ConfigCategory category, ConfigEntryBuilder entryBuilder, ValueHolder<FileFormat> format) {
    category.addEntry(
        entryBuilder
            .startIntSlider(
                Component.translatable("fabrishot.config.jpg_quality"), Config.JPG_QUALITY, 2, 31)
            .setTooltip(Component.translatable("fabrishot.config.jpg_quality.tooltip"))
            .setDefaultValue(5)
            .setSaveConsumer(i -> Config.JPG_QUALITY = i)
            .setDisplayRequirement(Requirement.isValue(format, FileFormat.JPG))
            .build());

    category.addEntry(
        entryBuilder
            .startEnumSelector(
                Component.translatable("fabrishot.config.jpg_color_sampling"),
                JpgColorSampling.class,
                Config.JPG_COLOR_SAMPLING)
            .setTooltip(Component.translatable("fabrishot.config.jpg_color_sampling.tooltip"))
            .setDefaultValue(JpgColorSampling.YUV420)
            .setSaveConsumer(s -> Config.JPG_COLOR_SAMPLING = s)
            .setDisplayRequirement(Requirement.isValue(format, FileFormat.JPG))
            .build());
  }

  private static void addWebpEntries(
      ConfigCategory category, ConfigEntryBuilder entryBuilder, ValueHolder<FileFormat> format) {
    BooleanListEntry lossless =
        entryBuilder
            .startBooleanToggle(
                Component.translatable("fabrishot.config.webp_lossless"), Config.WEBP_LOSSLESS)
            .setTooltip(Component.translatable("fabrishot.config.webp_lossless.tooltip"))
            .setDefaultValue(false)
            .setSaveConsumer(b -> Config.WEBP_LOSSLESS = b)
            .setDisplayRequirement(Requirement.isValue(format, FileFormat.WEBP))
            .build();
    category.addEntry(lossless);

    category.addEntry(
        entryBuilder
            .startIntSlider(
                Component.translatable("fabrishot.config.webp_quality"),
                Config.WEBP_QUALITY,
                0,
                100)
            .setTooltip(Component.translatable("fabrishot.config.webp_quality.tooltip"))
            .setDefaultValue(90)
            .setSaveConsumer(i -> Config.WEBP_QUALITY = i)
            .setDisplayRequirement(
                Requirement.all(
                    Requirement.isValue(format, FileFormat.WEBP), Requirement.isFalse(lossless)))
            .build());
  }

  private static void addTiffEntries(
      ConfigCategory category, ConfigEntryBuilder entryBuilder, ValueHolder<FileFormat> format) {
    category.addEntry(
        entryBuilder
            .startEnumSelector(
                Component.translatable("fabrishot.config.tiff_compression"),
                TiffCompression.class,
                Config.TIFF_COMPRESSION)
            .setTooltip(Component.translatable("fabrishot.config.tiff_compression.tooltip"))
            .setDefaultValue(TiffCompression.DEFLATE)
            .setSaveConsumer(c -> Config.TIFF_COMPRESSION = c)
            .setDisplayRequirement(Requirement.isValue(format, FileFormat.TIFF))
            .build());
  }

  private static void addAvifEntries(
      ConfigCategory category, ConfigEntryBuilder entryBuilder, ValueHolder<FileFormat> format) {
    category.addEntry(
        entryBuilder
            .startIntSlider(
                Component.translatable("fabrishot.config.avif_cpu_used"),
                Config.AVIF_CPU_USED,
                0,
                8)
            .setTooltip(Component.translatable("fabrishot.config.avif_cpu_used.tooltip"))
            .setDefaultValue(4)
            .setSaveConsumer(i -> Config.AVIF_CPU_USED = i)
            .setDisplayRequirement(Requirement.isValue(format, FileFormat.AVIF))
            .build());

    category.addEntry(
        entryBuilder
            .startIntSlider(
                Component.translatable("fabrishot.config.avif_crf"), Config.AVIF_CRF, 0, 63)
            .setTooltip(Component.translatable("fabrishot.config.avif_crf.tooltip"))
            .setDefaultValue(24)
            .setSaveConsumer(i -> Config.AVIF_CRF = i)
            .setDisplayRequirement(Requirement.isValue(format, FileFormat.AVIF))
            .build());

    category.addEntry(
        entryBuilder
            .startEnumSelector(
                Component.translatable("fabrishot.config.avif_tune"),
                AvifTune.class,
                Config.AVIF_TUNE)
            .setTooltip(Component.translatable("fabrishot.config.avif_tune.tooltip"))
            .setDefaultValue(AvifTune.SSIM)
            .setSaveConsumer(t -> Config.AVIF_TUNE = t)
            .setDisplayRequirement(Requirement.isValue(format, FileFormat.AVIF))
            .build());

    category.addEntry(
        entryBuilder
            .startEnumSelector(
                Component.translatable("fabrishot.config.avif_color_sampling"),
                AvifColorSampling.class,
                Config.AVIF_COLOR_SAMPLING)
            .setTooltip(Component.translatable("fabrishot.config.avif_color_sampling.tooltip"))
            .setDefaultValue(AvifColorSampling.YUV420)
            .setSaveConsumer(s -> Config.AVIF_COLOR_SAMPLING = s)
            .setDisplayRequirement(Requirement.isValue(format, FileFormat.AVIF))
            .build());
  }
}
