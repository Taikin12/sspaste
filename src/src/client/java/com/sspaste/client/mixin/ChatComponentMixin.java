package com.sspaste.client.mixin;

import com.sspaste.client.SSPasteUrls;
import com.sspaste.client.SSPasteClient;
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
	private Component sspaste$addImageHover(Component message) {
		if (message == null) {
			return null;
		}
		if (SSPasteClient.config() != null && !SSPasteClient.config().showChatImagePreviews) {
			return message;
		}
		if (!SSPasteUrls.hasImageUrl(message.getString())) {
			return message;
		}
		return SSPasteUrls.transform(message);
	}
}
