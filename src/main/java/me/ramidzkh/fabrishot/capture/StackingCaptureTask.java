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
import me.ramidzkh.fabrishot.Fabrishot;
import me.ramidzkh.fabrishot.config.Config;
import me.ramidzkh.fabrishot.mixins.HudAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.io.IOException;
import java.nio.file.Path;

public class StackingCaptureTask {

    private final Path file;
    private StreamingAccumulator acc;
    private boolean hudHidden;
    private int tick;
    private int taken;
    private volatile int saved;
    private volatile boolean compositing;
    private volatile boolean capturing = true;

    private StackingCaptureTask(Path file) {
        this.file = file;
    }

    public static StackingCaptureTask create(Path file) {
        return new StackingCaptureTask(file);
    }

    public boolean isCompositing() {
        return compositing;
    }

    public boolean isCapturing() {
        return capturing;
    }

    public int getTakenCount() {
        return taken;
    }

    public int getSavedCount() {
        return saved;
    }

    public int getStackCount() {
        return Config.STACK_COUNT;
    }

    public boolean onRenderTick() {
        Minecraft client = Minecraft.getInstance();
        HudAccessor hud = (HudAccessor) client.gui.hud;

        // All frames dispatched — wait for streaming to finish
        if (taken >= Config.STACK_COUNT) {
            if (compositing) {
                if (saved >= Config.STACK_COUNT) {
                    client.gui.hud.setOverlayMessage(
                            Component.translatable("fabrishot.stack.compositing"), false);
                } else {
                    client.gui.hud.setOverlayMessage(
                            Component.translatable("fabrishot.stack.saving", saved, Config.STACK_COUNT), false);
                }
                return false;
            }
            return true;
        }

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

        Screenshot.takeScreenshot(client.gameRenderer.mainRenderTarget(), image -> {
            Util.ioPool().execute(() -> {
                try {
                    processFrame(image);
                } catch (IOException e) {
                    compositing = false;
                    throw new RuntimeException(e);
                } finally {
                    image.close();
                }
            });
        });

        tick = 0;
        taken++;

        if (taken >= Config.STACK_COUNT) {
            compositing = true;
            capturing = false;
            hud.setHudHidden(hudHidden);
            Fabrishot.refresh();
            return false;
        }

        return false;
    }

    private synchronized void processFrame(NativeImage image) throws IOException {
        if (acc == null) {
            acc = StreamingAccumulator.create(
                    image.getWidth(), image.getHeight(),
                    Config.STACK_MODE, file);
        }
        acc.accumulate(image);
        saved++;

        if (saved >= Config.STACK_COUNT) {
            acc.finish();
            acc = null;
            compositing = false;
            Minecraft.getInstance().execute(() ->
                    Minecraft.getInstance().gui.hud.setOverlayMessage(
                            Component.translatable("fabrishot.stack.done"), false));
        }
    }
}
