/*
 * MIT License
 *
 * Copyright (c) 2026 Fhxa3
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

package me.ramidzkh.fabrishot.mixins;

import com.mojang.blaze3d.platform.InputConstants;
import me.ramidzkh.fabrishot.Fabrishot;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.PreeditEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Block all keyboard input except Esc, so people don't accidentally hit something and miss the shot they wanted.
@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {

  @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
  private void blockKeyPress(
      long handle, int action, KeyEvent event, CallbackInfo callbackInfo) {
    if (!Fabrishot.isStackCaptureInProgress()) {
      return;
    }

    if (action == InputConstants.PRESS && event.isEscape()) {
      Fabrishot.interruptStackCapture();
    }

    callbackInfo.cancel();
  }

  @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
  private void blockCharTyped(long handle, CharacterEvent event, CallbackInfo callbackInfo) {
    if (Fabrishot.isStackCaptureInProgress()) {
      callbackInfo.cancel();
    }
  }

  @Inject(method = "textInput", at = @At("HEAD"), cancellable = true)
  private void blockTextInput(long handle, String text, CallbackInfo callbackInfo) {
    if (Fabrishot.isStackCaptureInProgress()) {
      callbackInfo.cancel();
    }
  }

  @Inject(method = "textEditing", at = @At("HEAD"), cancellable = true)
  private void blockTextEditing(long handle, PreeditEvent event, CallbackInfo callbackInfo) {
    if (Fabrishot.isStackCaptureInProgress()) {
      callbackInfo.cancel();
    }
  }
}
