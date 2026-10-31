package com.picpaste.client.mixin;

import com.picpaste.client.ScreenshotClipboard;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.File;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NativeImage.class)
public abstract class NativeImageMixin {
	@Inject(method = "writeToFile(Ljava/io/File;)V", at = @At("TAIL"))
	private void picpaste$copyScreenshot(File file, CallbackInfo ci) {
		ScreenshotClipboard.onSaved(file);
	}
}
