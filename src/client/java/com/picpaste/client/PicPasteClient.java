package com.picpaste.client;

import java.awt.image.BufferedImage;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PicPasteClient implements ClientModInitializer {
	public static final String MOD_ID = "picpaste";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final AtomicBoolean BUSY = new AtomicBoolean();
	private static final ExecutorService UPLOADER = Executors.newSingleThreadExecutor(r -> {
		Thread thread = new Thread(r, "picpaste-upload");
		thread.setDaemon(true);
		return thread;
	});

	private static PicPasteConfig config;
	private static boolean catboxNoticeShown;

	@Override
	public void onInitializeClient() {
		config = PicPasteConfig.load();

		ClientPlayConnectionEvents.DISCONNECT.register(
				(handler, client) -> client.execute(PicPasteTextures::clear));

		String host = config.resolvedUploader();
		if (host.equals("catbox")) {
			LOGGER.info("Chat Image Uploader ready (catbox.moe, no token required)");
		} else {
			LOGGER.info("Chat Image Uploader ready ({})", host);
		}
	}

	public static PicPasteConfig config() {
		return config;
	}

	public static boolean tryPasteImage(ChatScreen screen) {
		if (BUSY.get()) {
			feedback(Component.translatable("picpaste.busy").withStyle(ChatFormatting.YELLOW));
			return true;
		}

		Optional<BufferedImage> found = ClipboardImages.read();
		if (found.isEmpty()) {
			return false;
		}
		if (!BUSY.compareAndSet(false, true)) {
			feedback(Component.translatable("picpaste.busy").withStyle(ChatFormatting.YELLOW));
			return true;
		}

		BufferedImage image = found.get();
		long maxBytes = config == null ? 10L * 1024L * 1024L : config.maxUploadBytes();
		PicPasteConfig cfg = config;

		feedback(Component.translatable("picpaste.uploading").withStyle(ChatFormatting.GRAY));

		UPLOADER.submit(() -> {
			try {
				byte[] png = ImageUploader.encodeForUpload(image, maxBytes);
				UploadResult result = ImageUploader.upload(png, cfg);
				Minecraft.getInstance().execute(() -> onUploaded(screen, result));
			} catch (Throwable t) {
				LOGGER.warn("Image upload failed", t);
				String reason = t.getMessage() == null || t.getMessage().isBlank()
						? t.getClass().getSimpleName()
						: t.getMessage();
				Minecraft.getInstance().execute(() -> onFailed(reason));
			}
		});

		return true;
	}

	private static void onUploaded(ChatScreen screen, UploadResult result) {
		BUSY.set(false);

		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.gui.screen() == screen) {
			screen.insertText(result.url(), false);
			if (showFeedback()) {
				minecraft.gui.hud.setOverlayMessage(
						Component.translatable("picpaste.uploaded_overlay").withStyle(ChatFormatting.GREEN),
						false);
			}
		} else {
			minecraft.keyboardHandler.setClipboard(result.url());
			Component message = Component.literal("[PicPaste] ")
					.withStyle(ChatFormatting.AQUA)
					.append(Component.literal(result.url())
							.withStyle(ChatFormatting.GREEN, ChatFormatting.UNDERLINE)
							.withStyle(style -> style
									.withClickEvent(new ClickEvent.CopyToClipboard(result.url()))
									.withHoverEvent(new HoverEvent.ShowText(
											Component.translatable("picpaste.click_to_copy")))));
			minecraft.gui.hud.getChat().addClientSystemMessage(message);
		}

		if (result.host().equals("catbox") && !catboxNoticeShown && showFeedback()) {
			catboxNoticeShown = true;
			minecraft.gui.hud.getChat().addClientSystemMessage(
					Component.translatable("picpaste.catbox_note").withStyle(ChatFormatting.DARK_GRAY));
		}
	}

	private static void onFailed(String reason) {
		BUSY.set(false);
		feedback(Component.translatable("picpaste.failed", reason).withStyle(ChatFormatting.RED));
	}

	private static boolean showFeedback() {
		return config == null || config.showChatFeedback;
	}

	private static void feedback(Component message) {
		if (showFeedback()) {
			Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(message);
		}
	}
}
