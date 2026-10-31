package com.picpaste.client.mixin;

import com.picpaste.client.PicPasteUrls;
import com.picpaste.client.PicPasteClient;
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
	private Component picpaste$addImageHover(Component message) {
		if (message == null) {
			return null;
		}
		if (PicPasteClient.config() != null && !PicPasteClient.config().showChatImagePreviews) {
			return message;
		}
		if (!PicPasteUrls.hasImageUrl(message.getString())) {
			return message;
		}
		return PicPasteUrls.transform(message);
	}
}
