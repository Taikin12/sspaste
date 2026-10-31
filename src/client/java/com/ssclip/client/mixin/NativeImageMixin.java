package com.ssclip.client.mixin;

import com.ssclip.client.ScreenshotClipboard;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.File;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NativeImage.class)
public abstract class NativeImageMixin {
	@Inject(method = "writeToFile(Ljava/io/File;)V", at = @At("TAIL"))
	private void ssclip$copyScreenshot(File file, CallbackInfo ci) {
		ScreenshotClipboard.onSaved(file);
	}
}
