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

package me.ramidzkh.fabrishot.capture;

import com.mojang.blaze3d.platform.NativeImage;
import me.ramidzkh.fabrishot.config.Config;
import me.ramidzkh.fabrishot.mixins.HudAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class StackingCaptureTask {

    private final Path file;
    private final Path tempDir;
    private final List<Path> tempFiles = new ArrayList<>();
    private boolean hudHidden;
    private int tick;
    private int taken;
    private int saved;

    private StackingCaptureTask(Path file) {
        this.file = file;
        this.tempDir = Minecraft.getInstance().gameDirectory.toPath()
                .resolve(".fabrishot").resolve("stack_temp");
    }

    public static StackingCaptureTask create(Path file) {
        return new StackingCaptureTask(file);
    }

    public boolean onRenderTick() {
        Minecraft client = Minecraft.getInstance();
        HudAccessor hud = (HudAccessor) client.gui.hud;

        if (tick == 0 && taken == 0) {
            hudHidden = hud.isHudHidden();
            hud.setHudHidden(hudHidden | Config.HIDE_HUD);
        }

        client.gui.hud.setOverlayMessage(
                Component.translatable("fabrishot.stack.progress", taken + 1, Config.STACK_COUNT), false);

        int needed = taken == 0 ? Config.CAPTURE_DELAY : Config.STACK_INTERVAL_TICKS;
        if (tick < needed) {
            tick++;
            return false;
        }

        int frameIndex = taken;
        Screenshot.takeScreenshot(client.gameRenderer.mainRenderTarget(), image -> {
            Util.ioPool().execute(() -> {
                try (image) {
                    saveTempFrame(image, frameIndex);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        });

        tick = 0;
        taken++;

        if (taken >= Config.STACK_COUNT) {
            hud.setHudHidden(hudHidden);
            client.gui.hud.setOverlayMessage(Component.empty(), false);
            return true;
        }

        return false;
    }

    private void saveTempFrame(NativeImage image, int index) throws IOException {
        Path tempFile = tempDir.resolve(String.format("frame_%04d.png", index));
        Files.createDirectories(tempDir);
        image.writeToFile(tempFile);

        synchronized (tempFiles) {
            tempFiles.add(tempFile);
            saved++;

            if (saved >= Config.STACK_COUNT) {
                var sorted = new ArrayList<>(tempFiles);
                sorted.sort(null);
                StackingCompositor.composite(tempDir, sorted, file,
                        Config.CAPTURE_FILE_FORMAT, Config.STACK_MODE);
            }
        }
    }
}
