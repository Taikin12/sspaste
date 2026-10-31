package com.sspaste.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;

public final class ImageUploader {
	private static final HttpClient HTTP = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(15))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();

	private ImageUploader() {
	}

	public static UploadResult upload(byte[] png, SSPasteConfig config) throws IOException, InterruptedException {
		String host = config.resolvedUploader();
		if (host.equals("imgur")) {
			return uploadImgur(png, config);
		}
		if (host.equals("gyazo")) {
			return uploadGyazo(png, config);
		}
		if (host.equals("imgbb")) {
			return uploadImgbb(png, config);
		}
		if (host.equals("gzo")) {
			return uploadGzo(png);
		}
		return uploadCatbox(png);
	}

	public static byte[] encodeForUpload(BufferedImage source, long maxBytes) throws IOException {
		BufferedImage image = scaleToFit(toArgb(source), 4096, 4096);
		byte[] png = encodePng(image);

		int attempts = 0;
		while (png.length > maxBytes && image.getWidth() > 16 && image.getHeight() > 16 && attempts < 24) {
			double ratio = Math.sqrt((double) maxBytes / png.length);
			double factor = Math.min(0.9, Math.max(0.3, ratio * 0.95));
			image = scale(image, factor);
			png = encodePng(image);
			attempts++;
		}

		if (png.length > maxBytes) {
			throw new IOException("image is still larger than " + formatBytes(maxBytes)
					+ " after downscaling (" + formatBytes(png.length) + ")");
		}
		return png;
	}

	private static String formatBytes(long bytes) {
		if (bytes >= 1024L * 1024L) {
			return String.format(java.util.Locale.ROOT, "%.1f MiB", bytes / (1024.0 * 1024.0));
		}
		return String.format(java.util.Locale.ROOT, "%.0f KiB", bytes / 1024.0);
	}

	private static UploadResult uploadImgur(byte[] png, SSPasteConfig config) throws IOException, InterruptedException {
		String clientId = config.clientId();
		if (clientId.isBlank()) {
			throw new IOException("imgurClientId is not set in " + SSPasteConfig.path());
		}
		String authorization = "Client-ID " + clientId;

		String boundary = "----sspaste" + UUID.randomUUID().toString().replace("-", "");
		byte[] body = multipart(boundary, Map.of(), "image", "minecraft.png", "image/png", png);

		HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.imgur.com/3/image"))
				.timeout(Duration.ofSeconds(120))
				.header("Authorization", authorization)
				.header("User-Agent", "sspaste/1.0 (Minecraft mod)")
				.header("Content-Type", "multipart/form-data; boundary=" + boundary)
				.POST(HttpRequest.BodyPublishers.ofByteArray(body))
				.build();

		HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		JsonObject json;
		try {
			json = JsonParser.parseString(response.body()).getAsJsonObject();
		} catch (Exception e) {
			throw new IOException("Imgur returned HTTP " + response.statusCode() + ": " + truncate(response.body()));
		}

		boolean success = json.has("success") && json.get("success").getAsBoolean();
		if (!success || response.statusCode() >= 300) {
			throw new IOException("Imgur: " + imgurError(json, response.statusCode()));
		}

		JsonObject data = json.getAsJsonObject("data");
		String link = data.get("link").getAsString();
		String deleteHash = data.has("deletehash") && !data.get("deletehash").isJsonNull()
				? data.get("deletehash").getAsString()
				: null;
		String deleteUrl = deleteHash == null ? null : "https://imgur.com/delete/" + deleteHash;
		return new UploadResult("imgur", link, deleteUrl, deleteHash);
	}

	private static UploadResult uploadGyazo(byte[] png, SSPasteConfig config) throws IOException, InterruptedException {
		String token = config.gyazoToken();
		if (token.isBlank()) {
			throw new IOException("gyazoAccessToken is not set in " + SSPasteConfig.path());
		}

		String boundary = "----sspaste" + UUID.randomUUID().toString().replace("-", "");
		byte[] body = multipart(boundary, Map.of(), "imagedata", "minecraft.png", "image/png", png);

		HttpRequest request = HttpRequest.newBuilder(URI.create("https://upload.gyazo.com/api/upload"))
				.timeout(Duration.ofSeconds(120))
				.header("Authorization", "Bearer " + token)
				.header("User-Agent", "sspaste/1.0 (Minecraft mod)")
				.header("Content-Type", "multipart/form-data; boundary=" + boundary)
				.POST(HttpRequest.BodyPublishers.ofByteArray(body))
				.build();

		HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		JsonObject json;
		try {
			json = JsonParser.parseString(response.body()).getAsJsonObject();
		} catch (Exception e) {
			throw new IOException("Gyazo returned HTTP " + response.statusCode() + ": " + truncate(response.body()));
		}

		if (response.statusCode() >= 300 || !json.has("url") || json.get("url").isJsonNull()) {
			String error = json.has("error") && !json.get("error").isJsonNull()
					? json.get("error").getAsString()
					: "HTTP " + response.statusCode();
			throw new IOException("Gyazo: " + truncate(error));
		}

		String imageId = json.has("image_id") && !json.get("image_id").isJsonNull()
				? json.get("image_id").getAsString()
				: null;
		String permalink = json.has("permalink_url") && !json.get("permalink_url").isJsonNull()
				? json.get("permalink_url").getAsString()
				: null;
		return new UploadResult("gyazo", json.get("url").getAsString(), permalink, imageId);
	}

	private static UploadResult uploadImgbb(byte[] png, SSPasteConfig config) throws IOException, InterruptedException {
		String key = config.imgbbKey();
		if (key.isBlank()) {
			throw new IOException("imgbbApiKey is not set in " + SSPasteConfig.path());
		}

		String boundary = "----sspaste" + UUID.randomUUID().toString().replace("-", "");
		byte[] body = multipart(boundary, Map.of(), "image", "minecraft.png", "image/png", png);
		String endpoint = "https://api.imgbb.com/1/upload?key="
				+ java.net.URLEncoder.encode(key, StandardCharsets.UTF_8);

		HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
				.timeout(Duration.ofSeconds(120))
				.header("User-Agent", "sspaste/1.0 (Minecraft mod)")
				.header("Content-Type", "multipart/form-data; boundary=" + boundary)
				.POST(HttpRequest.BodyPublishers.ofByteArray(body))
				.build();

		HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		JsonObject json;
		try {
			json = JsonParser.parseString(response.body()).getAsJsonObject();
		} catch (Exception e) {
			throw new IOException("ImgBB returned HTTP " + response.statusCode() + ": " + truncate(response.body()));
		}

		JsonObject data = json.has("data") && json.get("data").isJsonObject()
				? json.getAsJsonObject("data")
				: null;
		String url = data != null && data.has("url") && !data.get("url").isJsonNull()
				? data.get("url").getAsString()
				: null;
		if (response.statusCode() >= 300 || url == null) {
			String error = json.has("error") && !json.get("error").isJsonNull()
					? json.get("error").getAsString()
					: "HTTP " + response.statusCode();
			throw new IOException("ImgBB: " + truncate(error));
		}
		String deleteUrl = data != null && data.has("delete_url") && !data.get("delete_url").isJsonNull()
				? data.get("delete_url").getAsString()
				: null;
		return new UploadResult("imgbb", url, deleteUrl, null);
	}

	private static UploadResult uploadGzo(byte[] png) throws IOException, InterruptedException {
		String boundary = "----sspaste" + UUID.randomUUID().toString().replace("-", "");
		Map<String, String> fields = new LinkedHashMap<>();
		fields.put("ttl", "86400");
		byte[] body = multipart(boundary, fields, "image", "minecraft.png", "image/png", png);

		HttpRequest request = HttpRequest.newBuilder(URI.create("https://gzo.ai/3/image"))
				.timeout(Duration.ofSeconds(120))
				.header("User-Agent", "sspaste/1.0 (Minecraft mod)")
				.header("Accept", "application/json")
				.header("Content-Type", "multipart/form-data; boundary=" + boundary)
				.POST(HttpRequest.BodyPublishers.ofByteArray(body))
				.build();

		HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		JsonObject json;
		try {
			json = JsonParser.parseString(response.body()).getAsJsonObject();
		} catch (Exception e) {
			throw new IOException("gzo.ai returned HTTP " + response.statusCode() + ": " + truncate(response.body()));
		}

		JsonObject data = json.has("data") && json.get("data").isJsonObject()
				? json.getAsJsonObject("data")
				: null;
		String url = data != null && data.has("direct_url") && !data.get("direct_url").isJsonNull()
				? data.get("direct_url").getAsString()
				: null;
		if (response.statusCode() >= 300 || url == null) {
			String error = json.has("error") && !json.get("error").isJsonNull()
					? json.get("error").getAsString()
					: "HTTP " + response.statusCode();
			throw new IOException("gzo.ai: " + truncate(error));
		}
		if (url.startsWith("/")) {
			url = "https://gzo.ai" + url;
		}
		String deleteUrl = data != null && data.has("delete_url") && !data.get("delete_url").isJsonNull()
				? data.get("delete_url").getAsString()
				: null;
		if (deleteUrl != null && deleteUrl.startsWith("/")) {
			deleteUrl = "https://gzo.ai" + deleteUrl;
		}
		return new UploadResult("gzo", url, deleteUrl, null);
	}

	private static UploadResult uploadCatbox(byte[] png) throws IOException, InterruptedException {
		String boundary = "----sspaste" + UUID.randomUUID().toString().replace("-", "");
		Map<String, String> fields = new LinkedHashMap<>();
		fields.put("reqtype", "fileupload");
		byte[] body = multipart(boundary, fields, "fileToUpload", "minecraft.png", "image/png", png);

		HttpRequest request = HttpRequest.newBuilder(URI.create("https://catbox.moe/user/api.php"))
				.timeout(Duration.ofSeconds(120))
				.header("User-Agent", "sspaste/1.0 (Minecraft mod)")
				.header("Content-Type", "multipart/form-data; boundary=" + boundary)
				.POST(HttpRequest.BodyPublishers.ofByteArray(body))
				.build();

		HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		String url = response.body() == null ? "" : response.body().trim();

		if (response.statusCode() >= 300 || !url.startsWith("http")) {
			throw new IOException("catbox: " + truncate(url.isBlank() ? "HTTP " + response.statusCode() : url));
		}
		return new UploadResult("catbox", url, null, null);
	}

	private static String imgurError(JsonObject json, int statusCode) {
		if (json.has("data") && json.get("data").isJsonObject()) {
			JsonObject data = json.getAsJsonObject("data");
			if (data.has("error") && !data.get("error").isJsonNull()) {
				return data.get("error").getAsString();
			}
		}
		return "HTTP " + statusCode;
	}

	private static byte[] multipart(String boundary, Map<String, String> fields, String fileField,
			String filename, String contentType, byte[] file) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream(file.length + 1024);

		for (Map.Entry<String, String> field : fields.entrySet()) {
			write(out, "--" + boundary + "\r\n");
			write(out, "Content-Disposition: form-data; name=\"" + field.getKey() + "\"\r\n\r\n");
			write(out, field.getValue() + "\r\n");
		}

		write(out, "--" + boundary + "\r\n");
		write(out, "Content-Disposition: form-data; name=\"" + fileField + "\"; filename=\"" + filename + "\"\r\n");
		write(out, "Content-Type: " + contentType + "\r\n\r\n");
		out.write(file);
		write(out, "\r\n--" + boundary + "--\r\n");

		return out.toByteArray();
	}

	private static void write(ByteArrayOutputStream out, String text) throws IOException {
		out.write(text.getBytes(StandardCharsets.UTF_8));
	}

	private static BufferedImage toArgb(BufferedImage source) {
		if (source.getType() == BufferedImage.TYPE_INT_ARGB) {
			return source;
		}
		BufferedImage copy = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = copy.createGraphics();
		try {
			graphics.drawImage(source, 0, 0, null);
		} finally {
			graphics.dispose();
		}
		return copy;
	}

	private static BufferedImage scaleToFit(BufferedImage source, int maxWidth, int maxHeight) {
		double factor = Math.min(1.0, Math.min((double) maxWidth / source.getWidth(), (double) maxHeight / source.getHeight()));
		return factor >= 1.0 ? source : scale(source, factor);
	}

	private static BufferedImage scale(BufferedImage source, double factor) {
		int width = Math.max(1, (int) Math.round(source.getWidth() * factor));
		int height = Math.max(1, (int) Math.round(source.getHeight() * factor));
		BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = scaled.createGraphics();
		try {
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			graphics.drawImage(source, 0, 0, width, height, null);
		} finally {
			graphics.dispose();
		}
		return scaled;
	}

	private static byte[] encodePng(BufferedImage image) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		if (!ImageIO.write(image, "png", out)) {
			throw new IOException("no PNG writer available");
		}
		return out.toByteArray();
	}

	private static String truncate(String text) {
		if (text == null) {
			return "";
		}
		String oneLine = text.replace('\n', ' ').replace('\r', ' ').trim();
		return oneLine.length() <= 200 ? oneLine : oneLine.substring(0, 200) + "…";
	}
}
