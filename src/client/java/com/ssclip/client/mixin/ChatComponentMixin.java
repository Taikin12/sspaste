package com.ssclip.client.mixin;

import com.ssclip.client.SSClipUrls;
import com.ssclip.client.SSClipClient;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ChatComponent.class)
public class ChatComponentMixin {
	@ModifyVariable(
			method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
			at = @At("HEAD"),
			argsOnly = true)
	private Component ssclip$addImageHover(Component message) {
		if (message == null) {
			return null;
		}
		if (SSClipClient.config() != null && !SSClipClient.config().showChatImagePreviews) {
			return message;
		}
		if (!SSClipUrls.hasImageUrl(message.getString())) {
			return message;
		}
		return SSClipUrls.transform(message);
	}
}
