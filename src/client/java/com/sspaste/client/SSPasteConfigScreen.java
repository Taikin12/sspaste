package com.sspaste.client;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

public final class SSPasteConfigScreen {
	private static final URI GYAZO_APPS_URL = URI.create("https://gyazo.com/oauth/applications");
	private static final URI IMGBB_API_URL = URI.create("https://api.imgbb.com/");
	private static final URI GZO_API_URL = URI.create("https://gzo.ai/docs/api");

	private SSPasteConfigScreen() {
	}

	public static Screen create(Screen parent) {
		SSPasteConfig config = SSPasteClient.config();
		if (config == null) {
			return null;
		}

		ConfigBuilder builder = ConfigBuilder.create()
				.setParentScreen(parent)
				.setTitle(Component.translatable("sspaste.config.title"))
				.setSavingRunnable(config::save);
		ConfigEntryBuilder eb = builder.entryBuilder();

		ConfigCategory general = builder.getOrCreateCategory(Component.translatable("sspaste.config.category.general"));
		general.addEntry(eb.startBooleanToggle(Component.translatable("sspaste.config.copy_screenshots"),
						config.copyScreenshotToClipboard)
				.setDefaultValue(true)
				.setSaveConsumer(v -> config.copyScreenshotToClipboard = v)
				.setTooltip(Component.translatable("sspaste.config.copy_screenshots.tooltip"))
				.build());
		general.addEntry(eb.startBooleanToggle(Component.translatable("sspaste.config.show_feedback"),
						config.showChatFeedback)
				.setDefaultValue(true)
				.setSaveConsumer(v -> config.showChatFeedback = v)
				.setTooltip(Component.translatable("sspaste.config.show_feedback.tooltip"))
				.build());
		general.addEntry(eb.startBooleanToggle(Component.translatable("sspaste.config.show_chat_images"),
						config.showChatImagePreviews)
				.setDefaultValue(true)
				.setSaveConsumer(v -> config.showChatImagePreviews = v)
				.setTooltip(Component.translatable("sspaste.config.show_chat_images.tooltip"))
				.build());
		general.addEntry(eb.startIntSlider(Component.translatable("sspaste.config.max_size"),
						config.maxUploadMegabytes, 1, 200)
				.setDefaultValue(10)
				.setSaveConsumer(v -> config.maxUploadMegabytes = v)
				.setTooltip(Component.translatable("sspaste.config.max_size.tooltip"))
				.build());
		general.addEntry(eb.startIntSlider(Component.translatable("sspaste.config.preview_width"),
						config.chatImagePreviewWidth, 160, 640)
				.setDefaultValue(160)
				.setSaveConsumer(v -> config.chatImagePreviewWidth = v)
				.setTooltip(Component.translatable("sspaste.config.preview_width.tooltip"))
				.build());
		general.addEntry(eb.startIntSlider(Component.translatable("sspaste.config.preview_height"),
						config.chatImagePreviewHeight, 90, 360)
				.setDefaultValue(100)
				.setSaveConsumer(v -> config.chatImagePreviewHeight = v)
				.setTooltip(Component.translatable("sspaste.config.preview_height.tooltip"))
				.build());
		general.addEntry(eb.startIntSlider(Component.translatable("sspaste.config.texture_width"),
						config.chatImageTextureWidth, 320, 1920)
				.setDefaultValue(1280)
				.setSaveConsumer(v -> config.chatImageTextureWidth = v)
				.setTooltip(Component.translatable("sspaste.config.texture_width.tooltip"))
				.build());
		general.addEntry(eb.startIntSlider(Component.translatable("sspaste.config.texture_height"),
						config.chatImageTextureHeight, 180, 1080)
				.setDefaultValue(720)
				.setSaveConsumer(v -> config.chatImageTextureHeight = v)
				.setTooltip(Component.translatable("sspaste.config.texture_height.tooltip"))
				.build());

		ConfigCategory upload = builder.getOrCreateCategory(Component.translatable("sspaste.config.category.upload"));
		upload.addEntry(eb.startSelector(Component.translatable("sspaste.config.uploader"),
						new String[]{"auto", "catbox", "gyazo", "imgbb", "gzo", "imgur"}, config.uploader)
				.setDefaultValue("catbox")
				.setSaveConsumer(v -> config.uploader = v)
				.setNameProvider(v -> Component.translatable("sspaste.config.uploader." + v))
				.setTooltip(Component.translatable("sspaste.config.uploader.tooltip"))
				.build());

		upload.addEntry(eb.startStrField(Component.translatable("sspaste.config.gyazo_token"), config.gyazoAccessToken)
				.setDefaultValue("")
				.setSaveConsumer(v -> config.gyazoAccessToken = v == null ? "" : v.trim())
				.setTooltip(Component.translatable("sspaste.config.gyazo_token.tooltip"))
				.build());
		upload.addEntry(eb.startTextDescription(
						Component.translatable("sspaste.config.gyazo_token.link",
								link(GYAZO_APPS_URL, "sspaste.config.gyazo_token.link_hover")))
				.build());
		upload.addEntry(eb.startTextDescription(
						Component.translatable("sspaste.config.gyazo_token.note"))
				.build());

		upload.addEntry(eb.startStrField(Component.translatable("sspaste.config.imgbb_key"), config.imgbbApiKey)
				.setDefaultValue("")
				.setSaveConsumer(v -> config.imgbbApiKey = v == null ? "" : v.trim())
				.setTooltip(Component.translatable("sspaste.config.imgbb_key.tooltip"))
				.build());
		upload.addEntry(eb.startTextDescription(
						Component.translatable("sspaste.config.imgbb_key.link",
								link(IMGBB_API_URL, "sspaste.config.imgbb_key.link_hover")))
				.build());
		upload.addEntry(eb.startTextDescription(
						Component.translatable("sspaste.config.imgbb_key.note"))
				.build());

		upload.addEntry(eb.startTextDescription(
						Component.translatable("sspaste.config.gzo.note",
								link(GZO_API_URL, "sspaste.config.gzo.link_hover")))
				.build());

		upload.addEntry(eb.startStrField(Component.translatable("sspaste.config.client_id"), config.imgurClientId)
				.setDefaultValue("")
				.setSaveConsumer(v -> config.imgurClientId = v == null ? "" : v.trim())
				.setTooltip(Component.translatable("sspaste.config.client_id.tooltip"))
				.build());
		upload.addEntry(eb.startTextDescription(
						Component.translatable("sspaste.config.client_id.note"))
				.build());
		ConfigCategory history = builder.getOrCreateCategory(
				Component.translatable("sspaste.config.category.history"));
		history.addEntry(eb.startTextDescription(
						Component.translatable("sspaste.config.history",
								fileLink(SSPasteHistory.htmlPath())))
				.build());
		history.addEntry(eb.startTextDescription(
						Component.translatable("sspaste.config.history.note"))
				.build());
		List<SSPasteHistory.Entry> entries = SSPasteHistory.entries();
		if (entries.isEmpty()) {
			history.addEntry(eb.startTextDescription(
							Component.translatable("sspaste.config.history.empty"))
					.build());
		} else {
			int limit = Math.min(20, entries.size());
			for (int i = 0; i < limit; i++) {
				history.addEntry(eb.startTextDescription(historyLine(entries.get(i))).build());
			}
		}

		return builder.build();
	}

	private static Component historyLine(SSPasteHistory.Entry entry) {
		MutableComponent line = Component.empty()
				.append(Component.literal(entry.time() + "  ").withStyle(ChatFormatting.GRAY))
				.append(Component.literal(entry.host() + "  ").withStyle(ChatFormatting.WHITE))
				.append(urlLink(Component.translatable("sspaste.config.history.image"),
						entry.url(), "sspaste.config.history.image_hover", ChatFormatting.AQUA));
		if (entry.deleteUrl() != null && !entry.deleteUrl().isBlank()) {
			boolean gyazo = "gyazo".equals(entry.host());
			line.append(Component.literal("  "));
			line.append(urlLink(
					Component.translatable(gyazo
							? "sspaste.config.history.gyazo"
							: "sspaste.config.history.delete"),
					entry.deleteUrl(),
					gyazo ? "sspaste.config.history.gyazo_hover" : "sspaste.config.history.delete_hover",
					gyazo ? ChatFormatting.AQUA : ChatFormatting.RED));
		} else if (entry.deleteKey() != null && !entry.deleteKey().isBlank()) {
			line.append(Component.literal("  "))
					.append(Component.translatable("sspaste.config.history.key", entry.deleteKey())
							.withStyle(ChatFormatting.DARK_GRAY));
		} else {
			line.append(Component.literal("  "))
					.append(Component.translatable("sspaste.config.history.none")
							.withStyle(ChatFormatting.DARK_GRAY));
		}
		return line;
	}

	private static Component urlLink(Component label, String url, String hoverKey, ChatFormatting color) {
		try {
			URI uri = URI.create(url);
			return label.copy()
					.withStyle(color, ChatFormatting.UNDERLINE)
					.withStyle(style -> style
							.withClickEvent(new ClickEvent.OpenUrl(uri))
							.withHoverEvent(new HoverEvent.ShowText(Component.translatable(hoverKey))));
		} catch (IllegalArgumentException e) {
			return label.copy().withStyle(ChatFormatting.DARK_GRAY);
		}
	}

	private static Component fileLink(Path path) {
		return Component.literal(path.getFileName().toString())
				.withStyle(ChatFormatting.AQUA, ChatFormatting.UNDERLINE)
				.withStyle(style -> style
						.withClickEvent(new ClickEvent.OpenFile(path))
						.withHoverEvent(new HoverEvent.ShowText(
								Component.translatable("sspaste.config.history.link_hover"))));
	}

	private static Component link(URI uri, String hoverKey) {
		return Component.literal(uri.toString())
				.withStyle(ChatFormatting.AQUA, ChatFormatting.UNDERLINE)
				.withStyle(style -> style
						.withClickEvent(new ClickEvent.OpenUrl(uri))
						.withHoverEvent(new HoverEvent.ShowText(Component.translatable(hoverKey))));
	}
}
