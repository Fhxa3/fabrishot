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

package me.ramidzkh.fabrishot;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import me.ramidzkh.fabrishot.capture.CaptureTask;
import me.ramidzkh.fabrishot.capture.StackingCaptureTask;
import me.ramidzkh.fabrishot.config.Config;
import me.ramidzkh.fabrishot.event.ScreenshotSaveCallback;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.util.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Fabrishot {

    public static final KeyMapping SCREENSHOT_BINDING = new KeyMapping(
            "key.fabrishot.screenshot",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F9,
            KeyMapping.Category.MISC);

    public static final KeyMapping STACKING_BINDING = new KeyMapping(
            "key.fabrishot.stack_screenshot",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F10,
            KeyMapping.Category.MISC);

    private static CaptureTask task;
    private static StackingCaptureTask stackTask;

    private static void printFileLink(Path path) {
        Minecraft minecraft = Minecraft.getInstance();

        Component fileText = Component.literal(path.toFile().getName())
                .withStyle(ChatFormatting.UNDERLINE)
                .withStyle(style -> style.withClickEvent(new ClickEvent.OpenFile(path)));
        minecraft.execute(() -> {
            Component message = Component.translatable("screenshot.success", fileText);

            minecraft.gui.hud.getChat().addClientSystemMessage(message);
            minecraft.getNarrator().saySystemQueued(message);
        });
    }

    public static void initialize() {
        KeyMappingHelper.registerKeyMapping(SCREENSHOT_BINDING);
        KeyMappingHelper.registerKeyMapping(STACKING_BINDING);
        ScreenshotSaveCallback.EVENT.register(Fabrishot::printFileLink);

        // Clean up any leftover stack temp files on shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Path tempDir = Minecraft.getInstance().gameDirectory.toPath()
                    .resolve(".fabrishot").resolve("stack_temp");
            try {
                if (Files.exists(tempDir)) {
                    try (var files = Files.list(tempDir)) {
                        files.forEach(f -> {
                            try { Files.deleteIfExists(f); } catch (IOException ignored) {}
                        });
                    }
                    Files.deleteIfExists(tempDir);
                }
            } catch (IOException ignored) {
            }
        }));
    }

    public static void startCapture() {
        if (task == null && stackTask == null) {
            task = new CaptureTask(getScreenshotFile(Minecraft.getInstance()));
            refresh();
        }
    }

    public static void startStackCapture() {
        if (stackTask == null && task == null) {
            stackTask = StackingCaptureTask.create(getScreenshotFile(Minecraft.getInstance()));
            refresh();
        }
    }

    public static void onRenderPreOrPost() {
        if (task != null && task.onRenderTick()) {
            task = null;
            refresh();
        }

        if (stackTask != null && stackTask.onRenderTick()) {
            stackTask = null;
            refresh();
        }
    }

    private static void refresh() {
        var framebuffer = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        if (framebuffer == null) return;

        Window window = Minecraft.getInstance().getWindow();
        framebuffer.resize(window.getScreenWidth(), window.getScreenHeight());
    }

    private static Path getScreenshotFile(Minecraft client) {
        Path dir = client.gameDirectory.toPath().resolve("screenshots");

        try {
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }

        String world = null;

        if (client.getSingleplayerServer() != null) {
            world = client.getSingleplayerServer().getWorldData().getLevelName();
        } else if (client.getCurrentServer() != null) {
            world = client.getCurrentServer().name;
        }

        Path file;
        String prefix = Config.CUSTOM_FILE_NAME
                .replace("%time%", Util.getFilenameFormattedDateTime())
                .replace("%world%", world != null ? world : "no_world");

        // loop though suffixes while the file exists
        int i = 1;

        do {
            file = dir.resolve(prefix + (i++ == 1 ? "" : "_" + i) + Config.CAPTURE_FILE_FORMAT.extension());
        } while (Files.exists(file));

        return file;
    }

    public static boolean isInCapture() {
        return task != null || stackTask != null;
    }
}
