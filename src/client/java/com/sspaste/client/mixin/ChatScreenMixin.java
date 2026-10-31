package com.sspaste.client.mixin;

import com.sspaste.client.SSPasteClient;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
	@Inject(method = "keyPressed(Lnet/minecraft/client/input/KeyEvent;)Z", at = @At("HEAD"), cancellable = true)
	private void sspaste$pasteImage(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
		if (!event.isPaste()) {
			return;
		}
		if (SSPasteClient.tryPasteImage((ChatScreen) (Object) this)) {
			cir.setReturnValue(true);
		}
	}
}
