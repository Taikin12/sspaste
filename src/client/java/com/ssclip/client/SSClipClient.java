package com.ssclip.client;

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

public final class SSClipClient implements ClientModInitializer {
	public static final String MOD_ID = "ssclip";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final AtomicBoolean BUSY = new AtomicBoolean();
	private static final ExecutorService UPLOADER = Executors.newSingleThreadExecutor(r -> {
		Thread thread = new Thread(r, "ssclip-upload");
		thread.setDaemon(true);
		return thread;
	});

	private static SSClipConfig config;
	private static boolean catboxNoticeShown;

	@Override
	public void onInitializeClient() {
		config = SSClipConfig.load();
		SSClipHistory.load();

		ClientPlayConnectionEvents.DISCONNECT.register(
				(handler, client) -> client.execute(SSClipTextures::clear));

		String host = config.resolvedUploader();
		if (host.equals("catbox")) {
			LOGGER.info("SSclip ready (catbox.moe, no token required)");
		} else {
			LOGGER.info("SSclip ready ({})", host);
		}
	}

	public static SSClipConfig config() {
		return config;
	}

	public static boolean tryPasteImage(ChatScreen screen) {
		if (BUSY.get()) {
			feedback(Component.translatable("ssclip.busy").withStyle(ChatFormatting.YELLOW));
			return true;
		}

		Optional<BufferedImage> found = ClipboardImages.read();
		if (found.isEmpty()) {
			return false;
		}
		if (!BUSY.compareAndSet(false, true)) {
			feedback(Component.translatable("ssclip.busy").withStyle(ChatFormatting.YELLOW));
			return true;
		}

		BufferedImage image = found.get();
		long maxBytes = config == null ? 10L * 1024L * 1024L : config.maxUploadBytes();
		SSClipConfig cfg = config;

		feedback(Component.translatable("ssclip.uploading").withStyle(ChatFormatting.GRAY));

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
		SSClipHistory.add(result);

		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.gui.screen() == screen) {
			screen.insertText(result.url(), false);
			if (showFeedback()) {
				minecraft.gui.hud.setOverlayMessage(
						Component.translatable("ssclip.uploaded_overlay").withStyle(ChatFormatting.GREEN),
						false);
			}
		} else {
			minecraft.keyboardHandler.setClipboard(result.url());
			Component message = Component.literal("[SSclip] ")
					.withStyle(ChatFormatting.AQUA)
					.append(Component.literal(result.url())
							.withStyle(ChatFormatting.GREEN, ChatFormatting.UNDERLINE)
							.withStyle(style -> style
									.withClickEvent(new ClickEvent.CopyToClipboard(result.url()))
									.withHoverEvent(new HoverEvent.ShowText(
											Component.translatable("ssclip.click_to_copy")))));
			minecraft.gui.hud.getChat().addClientSystemMessage(message);
		}

		if (result.host().equals("catbox") && !catboxNoticeShown && showFeedback()) {
			catboxNoticeShown = true;
			minecraft.gui.hud.getChat().addClientSystemMessage(
					Component.translatable("ssclip.catbox_note").withStyle(ChatFormatting.DARK_GRAY));
		}
	}

	private static void onFailed(String reason) {
		BUSY.set(false);
		feedback(Component.translatable("ssclip.failed", reason).withStyle(ChatFormatting.RED));
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
