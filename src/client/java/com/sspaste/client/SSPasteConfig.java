package com.sspaste.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;

public final class SSPasteConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("sspaste.json");
	private static final Path RECENT_PATH = FabricLoader.getInstance().getConfigDir().resolve("ssclip.json");
	private static final Path LEGACY_PATH = FabricLoader.getInstance().getConfigDir().resolve("picpaste.json");
	private static final Path OLDER_PATH = FabricLoader.getInstance().getConfigDir().resolve("chatimageuploader.json");
	private static final Path OLDEST_PATH = FabricLoader.getInstance().getConfigDir().resolve("chatimgur.json");

	public String uploader = "catbox";
	public String imgurClientId = "";
	public String gyazoAccessToken = "";
	public String imgbbApiKey = "";
	public int maxUploadMegabytes = 10;
	public boolean showChatFeedback = true;
	public boolean copyScreenshotToClipboard = true;
	public boolean showChatImagePreviews = true;
	public int chatImagePreviewWidth = 160;
	public int chatImagePreviewHeight = 100;
	public int chatImageTextureWidth = 0;
	public int chatImageTextureHeight = 0;

	public static Path path() {
		return PATH;
	}

	public static SSPasteConfig load() {
		SSPasteConfig config = new SSPasteConfig();

		if (!Files.exists(PATH)) {
			Path source = Files.exists(RECENT_PATH) ? RECENT_PATH
					: (Files.exists(LEGACY_PATH) ? LEGACY_PATH
					: (Files.exists(OLDER_PATH) ? OLDER_PATH
					: (Files.exists(OLDEST_PATH) ? OLDEST_PATH : null)));
			if (source != null) {
				try {
					Files.createDirectories(PATH.getParent());
					Files.copy(source, PATH);
				} catch (IOException e) {
					SSPasteClient.LOGGER.warn("Could not migrate {}", source, e);
				}
			}
		}

		if (Files.exists(PATH)) {
			try {
				SSPasteConfig loaded = GSON.fromJson(Files.readString(PATH, StandardCharsets.UTF_8), SSPasteConfig.class);
				if (loaded != null) {
					config = loaded;
				}
			} catch (Exception e) {
				SSPasteClient.LOGGER.warn("Could not read {}; using defaults", PATH, e);
			}
		}

		config.migrate();
		config.normalize();
		config.save();
		return config;
	}

	private void migrate() {
		if (chatImageTextureWidth <= 0 || chatImageTextureHeight <= 0) {
			if (chatImagePreviewWidth == 640 && chatImagePreviewHeight == 400) {
				chatImagePreviewWidth = 160;
				chatImagePreviewHeight = 100;
			}
			chatImageTextureWidth = 1280;
			chatImageTextureHeight = 720;
		} else if (chatImagePreviewWidth == 320 && chatImagePreviewHeight == 200) {
			chatImagePreviewWidth = 160;
			chatImagePreviewHeight = 100;
		}
	}

	public void save() {
		normalize();
		try {
			Files.createDirectories(PATH.getParent());
			Files.writeString(PATH, GSON.toJson(this) + System.lineSeparator(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			SSPasteClient.LOGGER.warn("Could not write {}", PATH, e);
		}
	}

	private void normalize() {
		if (uploader == null || uploader.isBlank()) {
			uploader = "catbox";
		}
		uploader = uploader.trim().toLowerCase(Locale.ROOT);
		if (!uploader.equals("auto") && !uploader.equals("imgur") && !uploader.equals("gyazo")
				&& !uploader.equals("imgbb") && !uploader.equals("gzo") && !uploader.equals("catbox")) {
			uploader = "auto";
		}

		if (imgurClientId == null) {
			imgurClientId = "";
		}
		imgurClientId = imgurClientId.trim();
		if (gyazoAccessToken == null) {
			gyazoAccessToken = "";
		}
		gyazoAccessToken = gyazoAccessToken.trim();
		if (imgbbApiKey == null) {
			imgbbApiKey = "";
		}
		imgbbApiKey = imgbbApiKey.trim();
		maxUploadMegabytes = Math.clamp(maxUploadMegabytes, 1, 200);
		chatImagePreviewWidth = Math.clamp(chatImagePreviewWidth, 160, 1280);
		chatImagePreviewHeight = Math.clamp(chatImagePreviewHeight, 90, 720);
		chatImageTextureWidth = Math.clamp(chatImageTextureWidth, 320, 1920);
		chatImageTextureHeight = Math.clamp(chatImageTextureHeight, 180, 1080);
	}

	private static String env(String... names) {
		for (String name : names) {
			String value = System.getenv(name);
			if (value != null && !value.isBlank()) {
				return value.trim();
			}
		}
		return "";
	}

	public String clientId() {
		if (!imgurClientId.isBlank()) {
			return imgurClientId;
		}
		return env("SSPASTE_CLIENT_ID", "SSCLIP_CLIENT_ID", "PICPASTE_CLIENT_ID", "CHATIMAGEUPLOADER_CLIENT_ID", "CHATIMGUR_CLIENT_ID", "IMGUR_CLIENT_ID");
	}

	public boolean hasImgurClientId() {
		return !clientId().isBlank();
	}

	public String gyazoToken() {
		if (!gyazoAccessToken.isBlank()) {
			return gyazoAccessToken;
		}
		return env("SSPASTE_GYAZO_TOKEN", "SSCLIP_GYAZO_TOKEN", "PICPASTE_GYAZO_TOKEN", "CHATIMAGEUPLOADER_GYAZO_TOKEN", "CHATIMGUR_GYAZO_TOKEN", "GYAZO_ACCESS_TOKEN");
	}

	public String imgbbKey() {
		if (!imgbbApiKey.isBlank()) {
			return imgbbApiKey;
		}
		return env("SSPASTE_IMGBB_API_KEY", "SSCLIP_IMGBB_API_KEY", "PICPASTE_IMGBB_API_KEY", "CHATIMAGEUPLOADER_IMGBB_API_KEY", "CHATIMGUR_IMGBB_API_KEY", "IMGBB_API_KEY");
	}

	public String resolvedUploader() {
		if (uploader.equals("imgur")) {
			return "imgur";
		}
		if (uploader.equals("gyazo")) {
			return "gyazo";
		}
		if (uploader.equals("imgbb")) {
			return "imgbb";
		}
		if (uploader.equals("gzo")) {
			return "gzo";
		}
		if (uploader.equals("catbox")) {
			return "catbox";
		}
		if (hasImgurClientId()) {
			return "imgur";
		}
		if (!gyazoToken().isBlank()) {
			return "gyazo";
		}
		if (!imgbbKey().isBlank()) {
			return "imgbb";
		}
		return "catbox";
	}

	public long maxUploadBytes() {
		return maxUploadMegabytes * 1024L * 1024L;
	}
}
