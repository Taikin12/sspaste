package com.picpaste.client.mixin;

import com.picpaste.client.PicPasteClient;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
	@Inject(method = "keyPressed(Lnet/minecraft/client/input/KeyEvent;)Z", at = @At("HEAD"), cancellable = true)
	private void picpaste$pasteImage(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
		if (!event.isPaste()) {
			return;
		}
		if (PicPasteClient.tryPasteImage((ChatScreen) (Object) this)) {
			cir.setReturnValue(true);
		}
	}
}
