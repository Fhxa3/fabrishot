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

import net.fabricmc.loader.api.FabricLoader;

/**
 * Detects whether Flashback is installed and its JavaCV/FFmpeg dependencies are available.
 */
public final class FlashbackDetector {

    /**
     * Cached result: true if Flashback is loaded AND the JavaCV FFmpegFrameRecorder class is on the classpath.
     */
    public static final boolean AVAILABLE;

    static {
        boolean available = false;
        if (FabricLoader.getInstance().isModLoaded("flashback")) {
            try {
                // Double-check: the JavaCV classes must actually be loadable at runtime.
                // Flashback bundles them; this guards against version mismatches.
                Class.forName("org.bytedeco.javacv.FFmpegFrameRecorder");
                available = true;
            } catch (ClassNotFoundException ignored) {
                // JavaCV not available — Flashback may be a version without FFmpeg
            }
        }
        AVAILABLE = available;
    }

    private FlashbackDetector() {
    }
}
