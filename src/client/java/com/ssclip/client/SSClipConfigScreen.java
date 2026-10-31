package com.ssclip.client;

import java.net.URI;
import java.nio.file.Path;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

public final class SSClipConfigScreen {
	private static final URI GYAZO_APPS_URL = URI.create("https://gyazo.com/oauth/applications");
	private static final URI IMGBB_API_URL = URI.create("https://api.imgbb.com/");
	private static final URI GZO_API_URL = URI.create("https://gzo.ai/docs/api");

	private SSClipConfigScreen() {
	}

	public static Screen create(Screen parent) {
		SSClipConfig config = SSClipClient.config();
		if (config == null) {
			return null;
		}

		ConfigBuilder builder = ConfigBuilder.create()
				.setParentScreen(parent)
				.setTitle(Component.translatable("ssclip.config.title"))
				.setSavingRunnable(config::save);
		ConfigEntryBuilder eb = builder.entryBuilder();

		ConfigCategory general = builder.getOrCreateCategory(Component.translatable("ssclip.config.category.general"));
		general.addEntry(eb.startBooleanToggle(Component.translatable("ssclip.config.copy_screenshots"),
						config.copyScreenshotToClipboard)
				.setDefaultValue(true)
				.setSaveConsumer(v -> config.copyScreenshotToClipboard = v)
				.setTooltip(Component.translatable("ssclip.config.copy_screenshots.tooltip"))
				.build());
		general.addEntry(eb.startBooleanToggle(Component.translatable("ssclip.config.show_feedback"),
						config.showChatFeedback)
				.setDefaultValue(true)
				.setSaveConsumer(v -> config.showChatFeedback = v)
				.setTooltip(Component.translatable("ssclip.config.show_feedback.tooltip"))
				.build());
		general.addEntry(eb.startBooleanToggle(Component.translatable("ssclip.config.show_chat_images"),
						config.showChatImagePreviews)
				.setDefaultValue(true)
				.setSaveConsumer(v -> config.showChatImagePreviews = v)
				.setTooltip(Component.translatable("ssclip.config.show_chat_images.tooltip"))
				.build());
		general.addEntry(eb.startIntSlider(Component.translatable("ssclip.config.max_size"),
						config.maxUploadMegabytes, 1, 200)
				.setDefaultValue(10)
				.setSaveConsumer(v -> config.maxUploadMegabytes = v)
				.setTooltip(Component.translatable("ssclip.config.max_size.tooltip"))
				.build());
		general.addEntry(eb.startIntSlider(Component.translatable("ssclip.config.preview_width"),
						config.chatImagePreviewWidth, 160, 640)
				.setDefaultValue(160)
				.setSaveConsumer(v -> config.chatImagePreviewWidth = v)
				.setTooltip(Component.translatable("ssclip.config.preview_width.tooltip"))
				.build());
		general.addEntry(eb.startIntSlider(Component.translatable("ssclip.config.preview_height"),
						config.chatImagePreviewHeight, 90, 360)
				.setDefaultValue(100)
				.setSaveConsumer(v -> config.chatImagePreviewHeight = v)
				.setTooltip(Component.translatable("ssclip.config.preview_height.tooltip"))
				.build());
		general.addEntry(eb.startIntSlider(Component.translatable("ssclip.config.texture_width"),
						config.chatImageTextureWidth, 320, 1920)
				.setDefaultValue(1280)
				.setSaveConsumer(v -> config.chatImageTextureWidth = v)
				.setTooltip(Component.translatable("ssclip.config.texture_width.tooltip"))
				.build());
		general.addEntry(eb.startIntSlider(Component.translatable("ssclip.config.texture_height"),
						config.chatImageTextureHeight, 180, 1080)
				.setDefaultValue(720)
				.setSaveConsumer(v -> config.chatImageTextureHeight = v)
				.setTooltip(Component.translatable("ssclip.config.texture_height.tooltip"))
				.build());
		general.addEntry(eb.startTextDescription(
						Component.translatable("ssclip.config.history",
								fileLink(SSClipHistory.htmlPath())))
				.build());
		general.addEntry(eb.startTextDescription(
						Component.translatable("ssclip.config.history.note"))
				.build());

		ConfigCategory upload = builder.getOrCreateCategory(Component.translatable("ssclip.config.category.upload"));
		upload.addEntry(eb.startSelector(Component.translatable("ssclip.config.uploader"),
						new String[]{"auto", "catbox", "gyazo", "imgbb", "gzo", "imgur"}, config.uploader)
				.setDefaultValue("catbox")
				.setSaveConsumer(v -> config.uploader = v)
				.setNameProvider(v -> Component.translatable("ssclip.config.uploader." + v))
				.setTooltip(Component.translatable("ssclip.config.uploader.tooltip"))
				.build());

		upload.addEntry(eb.startStrField(Component.translatable("ssclip.config.gyazo_token"), config.gyazoAccessToken)
				.setDefaultValue("")
				.setSaveConsumer(v -> config.gyazoAccessToken = v == null ? "" : v.trim())
				.setTooltip(Component.translatable("ssclip.config.gyazo_token.tooltip"))
				.build());
		upload.addEntry(eb.startTextDescription(
						Component.translatable("ssclip.config.gyazo_token.link",
								link(GYAZO_APPS_URL, "ssclip.config.gyazo_token.link_hover")))
				.build());
		upload.addEntry(eb.startTextDescription(
						Component.translatable("ssclip.config.gyazo_token.note"))
				.build());

		upload.addEntry(eb.startStrField(Component.translatable("ssclip.config.imgbb_key"), config.imgbbApiKey)
				.setDefaultValue("")
				.setSaveConsumer(v -> config.imgbbApiKey = v == null ? "" : v.trim())
				.setTooltip(Component.translatable("ssclip.config.imgbb_key.tooltip"))
				.build());
		upload.addEntry(eb.startTextDescription(
						Component.translatable("ssclip.config.imgbb_key.link",
								link(IMGBB_API_URL, "ssclip.config.imgbb_key.link_hover")))
				.build());
		upload.addEntry(eb.startTextDescription(
						Component.translatable("ssclip.config.imgbb_key.note"))
				.build());

		upload.addEntry(eb.startTextDescription(
						Component.translatable("ssclip.config.gzo.note",
								link(GZO_API_URL, "ssclip.config.gzo.link_hover")))
				.build());

		upload.addEntry(eb.startStrField(Component.translatable("ssclip.config.client_id"), config.imgurClientId)
				.setDefaultValue("")
				.setSaveConsumer(v -> config.imgurClientId = v == null ? "" : v.trim())
				.setTooltip(Component.translatable("ssclip.config.client_id.tooltip"))
				.build());
		upload.addEntry(eb.startTextDescription(
						Component.translatable("ssclip.config.client_id.note"))
				.build());
		return builder.build();
	}

	private static Component fileLink(Path path) {
		return Component.literal(path.getFileName().toString())
				.withStyle(ChatFormatting.AQUA, ChatFormatting.UNDERLINE)
				.withStyle(style -> style
						.withClickEvent(new ClickEvent.OpenFile(path))
						.withHoverEvent(new HoverEvent.ShowText(
								Component.translatable("ssclip.config.history.link_hover"))));
	}

	private static Component link(URI uri, String hoverKey) {
		return Component.literal(uri.toString())
				.withStyle(ChatFormatting.AQUA, ChatFormatting.UNDERLINE)
				.withStyle(style -> style
						.withClickEvent(new ClickEvent.OpenUrl(uri))
						.withHoverEvent(new HoverEvent.ShowText(Component.translatable(hoverKey))));
	}
}
