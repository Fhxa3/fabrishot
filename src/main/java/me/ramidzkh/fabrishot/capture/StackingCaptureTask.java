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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
    private volatile int lastLogPercent = 0;
    private volatile boolean interrupted = false;
    private volatile int targetTotal = Config.STACK_COUNT;

    private static final Logger LOGGER = LogManager.getLogger(StackingCaptureTask.class);

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

        int total = targetTotal;

        // Handle interruption — jump to compositing if we have frames
        if (interrupted && !compositing) {
            if (targetTotal == 0) {
                // Nothing captured, abort cleanly
                compositing = false;
                capturing = false;
                return true;
            }
            compositing = true;
            capturing = false;
            hud.setHudHidden(hudHidden);
            Fabrishot.refresh();
        }

        // All frames dispatched — wait for streaming to finish
        if (taken >= total) {
            if (compositing) {
                if (saved >= total) {
                    // Safety net: finalize if processFrame missed the race
                    maybeFinalize();
                    if (!compositing) {
                        return true; // maybeFinalize completed the work
                    }
                } else {
                    client.gui.hud.setOverlayMessage(
                            Component.translatable("fabrishot.stack.saving", saved, total), false);
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
                Component.translatable("fabrishot.stack.progress", taken + 1, total), false);

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

        // Log capture progress at 10% intervals
        int capturePercent = taken * 100 / total;
        if (capturePercent >= lastLogPercent + 10 && capturePercent <= 100) {
            LOGGER.info("Stack capture progress: {}% ({}/{})", capturePercent, taken, total);
            lastLogPercent = (capturePercent / 10) * 10;
        }

        if (taken >= total) {
            compositing = true;
            capturing = false;
            hud.setHudHidden(hudHidden);
            lastLogPercent = 0; // reset for save phase logging
            Fabrishot.refresh();
            return false;
        }

        return false;
    }

    public void interrupt() {
        if (!capturing || compositing) return;

        this.interrupted = true;
        this.targetTotal = taken;
        this.capturing = false;

        // Try to finalize if all in-flight frames are already processed
        maybeFinalize();

        Minecraft client = Minecraft.getInstance();
        HudAccessor hud = (HudAccessor) client.gui.hud;
        hud.setHudHidden(hudHidden);

        Fabrishot.refresh();
    }

    private synchronized void processFrame(NativeImage image) throws IOException {
        if (acc == null) {
            acc = StreamingAccumulator.create(
                    image.getWidth(), image.getHeight(),
                    Config.STACK_MODE, file);
        }
        acc.accumulate(image);
        saved++;

        // targetTotal is set to Config.STACK_COUNT initially, updated to taken on interrupt.
        // Reading it directly avoids the race of checking a separate interrupted flag.
        int total = targetTotal;

        // Log composite progress at 10% intervals
        int savePercent = saved * 100 / total;
        if (savePercent >= lastLogPercent + 10 && savePercent <= 100) {
            LOGGER.info("Stack composite progress: {}% ({}/{})", savePercent, saved, total);
            lastLogPercent = (savePercent / 10) * 10;
        }

        if (saved >= total) {
            acc.finish();
            acc = null;
            compositing = false;
            Minecraft.getInstance().execute(() ->
                    Minecraft.getInstance().gui.hud.setOverlayMessage(
                            Component.translatable("fabrishot.stack.done"), false));
            LOGGER.info("Stack composite complete: {} frames merged and saved", total);
        }
    }

    /**
     * Synchronized safety net: finalizes the composite if all frames are accumulated
     * but processFrame missed the completion due to a race on the interrupt flag.
     */
    private synchronized void maybeFinalize() {
        if (!compositing || acc == null) return;
        if (saved < targetTotal) return;

        try {
            acc.finish();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        acc = null;
        compositing = false;
        interrupted = false;
        Minecraft.getInstance().execute(() ->
                Minecraft.getInstance().gui.hud.setOverlayMessage(
                        Component.translatable("fabrishot.stack.done"), false));
        LOGGER.info("Stack composite complete: {} frames merged and saved", targetTotal);
    }
}
