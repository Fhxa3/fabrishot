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

import com.mojang.blaze3d.systems.RenderSystem;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import me.ramidzkh.fabrishot.capture.ColorSpaceHelper;
import me.ramidzkh.fabrishot.capture.FlashbackDetector;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.api.Requirement;
import me.shedaniel.clothconfig2.gui.entries.EnumListEntry;
import me.shedaniel.clothconfig2.gui.entries.IntegerListEntry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ClothConfigBridge implements ConfigScreenFactory<Screen> {

    @Override
    public Screen create(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setTitle(Component.translatable("fabrishot.config.title"))
                .setSavingRunnable(Config::save)
                .setParentScreen(parent);
        ConfigEntryBuilder entryBuilder = builder.entryBuilder();
        ConfigCategory category = builder.getOrCreateCategory(Component.translatable("fabrishot.config.category"));

        // ── Basic settings ──

        category.addEntry(entryBuilder.startStrField(Component.translatable("fabrishot.config.custom_file_name"), Config.CUSTOM_FILE_NAME)
                .setTooltip(Component.translatable("fabrishot.config.custom_file_name.tooltip"))
                .setDefaultValue("huge_%time%")
                .setSaveConsumer(b -> Config.CUSTOM_FILE_NAME = b)
                .build());

        category.addEntry(entryBuilder.startBooleanToggle(Component.translatable("fabrishot.config.override_screenshot_key"), Config.OVERRIDE_SCREENSHOT_KEY)
                .setDefaultValue(false)
                .setSaveConsumer(b -> Config.OVERRIDE_SCREENSHOT_KEY = b)
                .build());

        category.addEntry(entryBuilder.startBooleanToggle(Component.translatable("fabrishot.config.hide_hud"), Config.HIDE_HUD)
                .setDefaultValue(false)
                .setSaveConsumer(b -> Config.HIDE_HUD = b)
                .build());

        category.addEntry(entryBuilder.startBooleanToggle(Component.translatable("fabrishot.config.save_file"), Config.SAVE_FILE)
                .setDefaultValue(true)
                .setSaveConsumer(b -> Config.SAVE_FILE = b)
                .build());

        // ── Resolution ──

        IntegerListEntry width = entryBuilder.startIntField(Component.translatable("fabrishot.config.width"), Config.CAPTURE_WIDTH)
                .setDefaultValue(3840)
                .setMin(1)
                .setMax(Math.min(65535, RenderSystem.getDevice().getDeviceInfo().limits().maxTextureSize()))
                .setSaveConsumer(i -> Config.CAPTURE_WIDTH = i)
                .build();
        category.addEntry(width);

        IntegerListEntry height = entryBuilder.startIntField(Component.translatable("fabrishot.config.height"), Config.CAPTURE_HEIGHT)
                .setDefaultValue(2160)
                .setMin(1)
                .setMax(Math.min(65535, RenderSystem.getDevice().getDeviceInfo().limits().maxTextureSize()))
                .setSaveConsumer(i -> Config.CAPTURE_HEIGHT = i)
                .build();
        category.addEntry(height);

        category.addEntry(new ScalingPresetEntry(220, width, height));

        // ── Delay ──

        category.addEntry(entryBuilder.startIntField(Component.translatable("fabrishot.config.delay"), Config.CAPTURE_DELAY)
                .setTooltip(Component.translatable("fabrishot.config.delay.tooltip"))
                .setDefaultValue(3)
                .setMin(3)
                .setSaveConsumer(i -> Config.CAPTURE_DELAY = i)
                .build());

        // ── Format & encoding ──

        EnumListEntry<FileFormat> formatSelector = entryBuilder.startEnumSelector(Component.translatable("fabrishot.config.file_format"), FileFormat.class, Config.CAPTURE_FILE_FORMAT)
                .setDefaultValue(FileFormat.PNG)
                .setSaveConsumer(t -> Config.CAPTURE_FILE_FORMAT = t)
                .build();
        category.addEntry(formatSelector);

        // Flashback status indicator
        category.addEntry(entryBuilder.startTextDescription(flashbackStatus()).build());

        // PNG: compression level
        category.addEntry(entryBuilder.startIntSlider(Component.translatable("fabrishot.config.png_compression"), Config.PNG_COMPRESSION_LEVEL, 0, 9)
                .setTooltip(Component.translatable("fabrishot.config.png_compression.tooltip"))
                .setDefaultValue(6)
                .setSaveConsumer(i -> Config.PNG_COMPRESSION_LEVEL = i)
                .setDisplayRequirement(Requirement.isValue(formatSelector, FileFormat.PNG))
                .build());

        // JPEG: quality
        category.addEntry(entryBuilder.startIntSlider(Component.translatable("fabrishot.config.jpg_quality"), Config.JPG_QUALITY, 2, 31)
                .setTooltip(Component.translatable("fabrishot.config.jpg_quality.tooltip"))
                .setDefaultValue(2)
                .setSaveConsumer(i -> Config.JPG_QUALITY = i)
                .setDisplayRequirement(Requirement.isValue(formatSelector, FileFormat.JPG))
                .build());

        // JPEG: color sampling
        category.addEntry(entryBuilder.startEnumSelector(Component.translatable("fabrishot.config.jpg_color_sampling"), JpgColorSampling.class, Config.JPG_COLOR_SAMPLING)
                .setTooltip(Component.translatable("fabrishot.config.jpg_color_sampling.tooltip"))
                .setDefaultValue(JpgColorSampling.YUV444)
                .setSaveConsumer(s -> Config.JPG_COLOR_SAMPLING = s)
                .setDisplayRequirement(Requirement.isValue(formatSelector, FileFormat.JPG))
                .build());

        // WebP: lossless (above quality)
        var webpLossless = entryBuilder.startBooleanToggle(Component.translatable("fabrishot.config.webp_lossless"), Config.WEBP_LOSSLESS)
                .setTooltip(Component.translatable("fabrishot.config.webp_lossless.tooltip"))
                .setDefaultValue(false)
                .setSaveConsumer(b -> Config.WEBP_LOSSLESS = b)
                .setDisplayRequirement(Requirement.isValue(formatSelector, FileFormat.WEBP))
                .build();
        category.addEntry(webpLossless);

        // WebP: quality (hidden when lossless is on)
        category.addEntry(entryBuilder.startIntSlider(Component.translatable("fabrishot.config.webp_quality"), Config.WEBP_QUALITY, 0, 100)
                .setTooltip(Component.translatable("fabrishot.config.webp_quality.tooltip"))
                .setDefaultValue(80)
                .setSaveConsumer(i -> Config.WEBP_QUALITY = i)
                .setDisplayRequirement(Requirement.all(
                        Requirement.isValue(formatSelector, FileFormat.WEBP),
                        Requirement.isFalse(webpLossless)))
                .build());

        // TIFF: compression
        category.addEntry(entryBuilder.startEnumSelector(Component.translatable("fabrishot.config.tiff_compression"), TiffCompression.class, Config.TIFF_COMPRESSION)
                .setTooltip(Component.translatable("fabrishot.config.tiff_compression.tooltip"))
                .setDefaultValue(TiffCompression.LZW)
                .setSaveConsumer(c -> Config.TIFF_COMPRESSION = c)
                .setDisplayRequirement(Requirement.isValue(formatSelector, FileFormat.TIFF))
                .build());

        // ── Colour space ──

        EnumListEntry<ColorSpaceMode> colorSpaceMode = entryBuilder.startEnumSelector(Component.translatable("fabrishot.config.color_space_mode"), ColorSpaceMode.class, Config.COLOR_SPACE_MODE)
                .setTooltip(Component.translatable("fabrishot.config.color_space_mode.tooltip"))
                .setDefaultValue(ColorSpaceMode.SRGB)
                .setSaveConsumer(m -> Config.COLOR_SPACE_MODE = m)
                .build();
        category.addEntry(colorSpaceMode);

        // Iris status (visible only when FOLLOW_IRIS)
        category.addEntry(entryBuilder.startTextDescription(irisStatus())
                .setDisplayRequirement(Requirement.isValue(colorSpaceMode, ColorSpaceMode.FOLLOW_IRIS))
                .build());

        // Custom colour space (visible only when CUSTOM)
        category.addEntry(entryBuilder.startEnumSelector(Component.translatable("fabrishot.config.custom_color_space"), FabriColorSpace.class, Config.CUSTOM_COLOR_SPACE)
                .setDefaultValue(FabriColorSpace.SRGB)
                .setSaveConsumer(s -> Config.CUSTOM_COLOR_SPACE = s)
                .setDisplayRequirement(Requirement.isValue(colorSpaceMode, ColorSpaceMode.CUSTOM))
                .build());

        return builder.build();
    }

    private static Component flashbackStatus() {
        if (FlashbackDetector.AVAILABLE) {
            return Component.translatable("fabrishot.config.flashback.available");
        }
        return Component.translatable("fabrishot.config.flashback.unavailable");
    }

    private static Component irisStatus() {
        if (!FabricLoader.getInstance().isModLoaded("iris")) {
            return Component.translatable("fabrishot.config.iris.not_installed");
        }
        var cs = ColorSpaceHelper.resolve();
        return Component.translatable("fabrishot.config.iris.detected", Component.literal(cs.name()));
    }
}
