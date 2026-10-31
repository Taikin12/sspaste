package com.sspaste.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

public final class SSPasteTextures {
	private static final int MAX_BYTES = 8 * 1024 * 1024;
	private static final int DEFAULT_WIDTH = 1280;
	private static final int DEFAULT_HEIGHT = 720;
	private static final int MAX_ENTRIES = 16;
	private static final int MAX_HTML_BYTES = 512 * 1024;
	private static final String BROWSER_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
			+ "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36 SS Paste/2.3";
	private static final List<String> IMAGE_EXTENSIONS = List.of(".png", ".jpg", ".jpeg", ".gif", ".webp");
	private static final Pattern OG_IMAGE_PATTERN = Pattern.compile(
			"<meta[^>]+(?:property|name)\\s*=\\s*[\"'](?:og:image(?::secure_url)?|twitter:image)[\"']"
					+ "[^>]+content\\s*=\\s*[\"']([^\"']+)[\"']",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern OG_IMAGE_REVERSED_PATTERN = Pattern.compile(
			"<meta[^>]+content\\s*=\\s*[\"']([^\"']+)[\"'][^>]+"
					+ "(?:property|name)\\s*=\\s*[\"'](?:og:image(?::secure_url)?|twitter:image)[\"']",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern IMGUR_PAGE = Pattern.compile("/(?:gallery/|a/)?([A-Za-z0-9]{5,10})");
	private static final Pattern GYAZO_PAGE = Pattern.compile("/([A-Za-z0-9]{5,64})");
	private static final Pattern GZO_PAGE = Pattern.compile("/i/([A-Za-z0-9]{4,32})");

	private static final HttpClient HTTP = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();
	private static final ExecutorService LOADER = Executors.newFixedThreadPool(2, runnable -> {
		Thread thread = new Thread(runnable, "sspaste-preview");
		thread.setDaemon(true);
		return thread;
	});
	private static final Map<String, Entry> ENTRIES = new LinkedHashMap<>();
	private static final Map<String, Gallery> GALLERIES = new LinkedHashMap<>();
	private static final int MAX_GALLERIES = 64;
	private static final AtomicInteger NEXT_ID = new AtomicInteger();

	private SSPasteTextures() {
	}

	public static final class Entry {
		private final String key;
		private final String url;
		private final int maxWidth;
		private final int maxHeight;
		private volatile Identifier id;
		private volatile int width;
		private volatile int height;
		private volatile boolean ready;
		private volatile boolean failed;
		private volatile boolean released;

		private Entry(String key, String url, int maxWidth, int maxHeight) {
			this.key = key;
			this.url = url;
			this.maxWidth = maxWidth;
			this.maxHeight = maxHeight;
		}

		public Identifier id() {
			return id;
		}

		public int width() {
			return width;
		}

		public int height() {
			return height;
		}

		public boolean isReady() {
			return ready && !released;
		}
	}

	public static final class Gallery {
		private final String key;
		private final String url;
		private final int maxWidth;
		private final int maxHeight;
		private final List<Entry> entries = new CopyOnWriteArrayList<>();
		private volatile boolean resolved;
		private volatile boolean failed;
		private volatile int totalCount = 1;

		private Gallery(String key, String url, int maxWidth, int maxHeight) {
			this.key = key;
			this.url = url;
			this.maxWidth = maxWidth;
			this.maxHeight = maxHeight;
		}

		public List<Entry> entries() {
			return entries;
		}

		public int totalCount() {
			return totalCount;
		}

		public boolean isResolved() {
			return resolved;
		}
	}

	public static Gallery getGallery(String url) {
		SSPasteConfig config = SSPasteClient.config();
		int maxWidth = config == null ? DEFAULT_WIDTH : config.chatImageTextureWidth;
		int maxHeight = config == null ? DEFAULT_HEIGHT : config.chatImageTextureHeight;
		maxWidth = Math.clamp(maxWidth, 320, 1920);
		maxHeight = Math.clamp(maxHeight, 180, 1080);

		String key = url + "|" + maxWidth + "x" + maxHeight;
		Gallery gallery;
		boolean submit = false;
		synchronized (GALLERIES) {
			Gallery existing = GALLERIES.get(key);
			if (existing != null) {
				existing.entries.removeIf(entry -> entry.released);
				if (!existing.entries.isEmpty() || !existing.resolved) {
					return existing;
				}
				GALLERIES.remove(key);
			}
			gallery = new Gallery(key, url, maxWidth, maxHeight);
			GALLERIES.put(key, gallery);
			if (GALLERIES.size() > MAX_GALLERIES) {
				Iterator<String> iterator = GALLERIES.keySet().iterator();
				while (iterator.hasNext() && GALLERIES.size() > MAX_GALLERIES) {
					String oldest = iterator.next();
					if (!oldest.equals(key)) {
						iterator.remove();
					}
				}
			}
			submit = true;
		}
		if (submit) {
			LOADER.submit(() -> resolveGallery(gallery));
		}
		return gallery;
	}

	private static void resolveGallery(Gallery gallery) {
		try {
			List<String> links = resolveImgurGallery(gallery.url);
			if (links != null && !links.isEmpty()) {
				gallery.totalCount = links.size();
				int count = Math.min(4, links.size());
				for (int i = 0; i < count; i++) {
					addEntry(gallery, links.get(i));
				}
			} else {
				gallery.totalCount = 1;
				addEntry(gallery, gallery.url);
			}
			gallery.resolved = true;
		} catch (Throwable t) {
			gallery.failed = true;
			SSPasteClient.LOGGER.debug("Could not resolve gallery {}", gallery.url, t);
		}
	}

	private static void addEntry(Gallery gallery, String url) {
		String key = url + "|" + gallery.maxWidth + "x" + gallery.maxHeight;
		Entry entry;
		boolean submit = false;
		synchronized (ENTRIES) {
			Entry existing = ENTRIES.get(key);
			if (existing != null) {
				entry = existing;
			} else {
				entry = new Entry(key, url, gallery.maxWidth, gallery.maxHeight);
				ENTRIES.put(key, entry);
				submit = true;
			}
		}
		if (!gallery.entries.contains(entry)) {
			gallery.entries.add(entry);
		}
		if (submit) {
			LOADER.submit(() -> load(entry));
		}
	}

	private static List<String> resolveImgurGallery(String url) {
		URI uri;
		try {
			uri = URI.create(url);
		} catch (IllegalArgumentException e) {
			return null;
		}
		String host = uri.getHost();
		String path = uri.getPath();
		if (host == null || path == null) {
			return null;
		}
		String lowerHost = host.toLowerCase(Locale.ROOT);
		if (!lowerHost.equals("imgur.com") && !lowerHost.endsWith(".imgur.com")) {
			return null;
		}
		Matcher galleryMatcher = Pattern.compile("/gallery/([A-Za-z0-9]+)").matcher(path);
		Matcher albumMatcher = Pattern.compile("/a/([A-Za-z0-9]+)").matcher(path);
		List<String> endpoints = new ArrayList<>();
		if (galleryMatcher.matches()) {
			endpoints.add("https://api.imgur.com/3/gallery/" + galleryMatcher.group(1));
			endpoints.add("https://api.imgur.com/3/album/" + galleryMatcher.group(1));
		} else if (albumMatcher.matches()) {
			endpoints.add("https://api.imgur.com/3/album/" + albumMatcher.group(1));
		} else {
			return null;
		}

		String authorization = imgurAuthorization();
		if (authorization == null) {
			return null;
		}
		for (String endpoint : endpoints) {
			try {
				HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
						.timeout(Duration.ofSeconds(15))
						.header("Authorization", authorization)
						.header("User-Agent", BROWSER_USER_AGENT)
						.header("Accept", "application/json")
						.GET()
						.build();
				HttpResponse<String> response = HTTP.send(request,
						HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
				if (response.statusCode() != 200) {
					continue;
				}
				JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
				if (!root.has("success") || !root.get("success").getAsBoolean()) {
					continue;
				}
				if (!root.has("data") || !root.get("data").isJsonObject()) {
					continue;
				}
				JsonObject data = root.getAsJsonObject("data");
				if (!data.has("images") || !data.get("images").isJsonArray()) {
					continue;
				}
				JsonArray images = data.getAsJsonArray("images");
				List<String> links = new ArrayList<>();
				for (JsonElement element : images) {
					if (!element.isJsonObject()) {
						continue;
					}
					JsonObject image = element.getAsJsonObject();
					if (!image.has("link") || image.get("link").isJsonNull()) {
						continue;
					}
					String link = image.get("link").getAsString();
					if (link.startsWith("http://")) {
						link = "https://" + link.substring(7);
					}
					if (SSPasteUrls.isDirectImageUrl(link)) {
						links.add(link);
					}
				}
				if (!links.isEmpty()) {
					return links;
				}
			} catch (Throwable t) {
				SSPasteClient.LOGGER.debug("Could not fetch Imgur gallery {}", endpoint, t);
			}
		}
		return null;
	}

	private static String imgurAuthorization() {
		SSPasteConfig config = SSPasteClient.config();
		if (config == null) {
			return null;
		}
		String clientId = config.clientId();
		return clientId.isBlank() ? null : "Client-ID " + clientId;
	}

	public static void clear() {
		Map<String, Entry> copy;
		synchronized (ENTRIES) {
			copy = new LinkedHashMap<>(ENTRIES);
			ENTRIES.clear();
		}
		synchronized (GALLERIES) {
			GALLERIES.clear();
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null || minecraft.getTextureManager() == null) {
			return;
		}
		for (Entry entry : copy.values()) {
			if (entry.id != null) {
				minecraft.getTextureManager().release(entry.id);
			}
		}
	}

	private static void load(Entry entry) {
		try {
			List<String> candidates = resolveCandidates(entry.url);
			for (String candidate : candidates) {
				BufferedImage image = downloadImage(candidate);
				if (image == null) {
					continue;
				}
				BufferedImage scaled = scaleToFit(image, entry.maxWidth, entry.maxHeight);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				if (!ImageIO.write(scaled, "png", out)) {
					continue;
				}
				NativeImage nativeImage = NativeImage.read(out.toByteArray());
				Minecraft.getInstance().execute(
						() -> register(entry, nativeImage, scaled.getWidth(), scaled.getHeight()));
				return;
			}
			entry.failed = true;
		} catch (Throwable t) {
			entry.failed = true;
			SSPasteClient.LOGGER.debug("Could not load chat image {}", entry.url, t);
		}
	}

	private static List<String> resolveCandidates(String url) {
		if (SSPasteUrls.isDirectImageUrl(url)) {
			return List.of(url);
		}

		List<String> candidates = new ArrayList<>();
		String og = resolveOgImage(url);
		if (og != null) {
			candidates.add(og);
		}
		candidates.addAll(patternCandidates(url));

		List<String> unique = new ArrayList<>();
		for (String candidate : candidates) {
			if (candidate != null && !unique.contains(candidate)) {
				unique.add(candidate);
			}
		}
		return unique.subList(0, Math.min(6, unique.size()));
	}

	private static String resolveOgImage(String pageUrl) {
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(pageUrl))
					.timeout(Duration.ofSeconds(15))
					.header("User-Agent", BROWSER_USER_AGENT)
					.header("Accept", "text/html,application/xhtml+xml")
					.GET()
					.build();
			HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
			if (response.statusCode() != 200) {
				return null;
			}
			byte[] bytes;
			try (InputStream input = response.body()) {
				bytes = input.readNBytes(MAX_HTML_BYTES);
			}
			String html = new String(bytes, StandardCharsets.UTF_8);
			String raw = firstMatch(html, OG_IMAGE_PATTERN);
			if (raw == null) {
				raw = firstMatch(html, OG_IMAGE_REVERSED_PATTERN);
			}
			if (raw == null) {
				return null;
			}
			raw = unescapeHtml(raw);
			URI resolved = URI.create(pageUrl).resolve(raw);
			String imageUrl = resolved.toString();
			if (imageUrl.startsWith("http://")) {
				imageUrl = "https://" + imageUrl.substring(7);
			}
			return SSPasteUrls.isDirectImageUrl(imageUrl) ? imageUrl : null;
		} catch (Throwable t) {
			SSPasteClient.LOGGER.debug("Could not resolve preview page {}", pageUrl, t);
			return null;
		}
	}

	private static String firstMatch(String text, Pattern pattern) {
		Matcher matcher = pattern.matcher(text);
		return matcher.find() ? matcher.group(1) : null;
	}

	private static List<String> patternCandidates(String url) {
		URI uri;
		try {
			uri = URI.create(url);
		} catch (IllegalArgumentException e) {
			return List.of();
		}
		String host = uri.getHost();
		String path = uri.getPath();
		if (host == null || path == null) {
			return List.of();
		}
		String lowerHost = host.toLowerCase(Locale.ROOT);
		String base = null;

		if (lowerHost.equals("imgur.com") || lowerHost.endsWith(".imgur.com")) {
			Matcher matcher = IMGUR_PAGE.matcher(path);
			if (matcher.matches()) {
				base = "https://i.imgur.com/" + matcher.group(1);
			}
		} else if (lowerHost.equals("gyazo.com") || lowerHost.endsWith(".gyazo.com")) {
			Matcher matcher = GYAZO_PAGE.matcher(path);
			if (matcher.matches()) {
				base = "https://i.gyazo.com/" + matcher.group(1);
			}
		} else if (lowerHost.equals("gzo.ai") || lowerHost.endsWith(".gzo.ai")) {
			Matcher matcher = GZO_PAGE.matcher(path);
			if (matcher.matches()) {
				base = "https://gzo.ai/i/" + matcher.group(1);
			}
		}

		if (base == null) {
			return List.of();
		}
		List<String> candidates = new ArrayList<>();
		for (String extension : IMAGE_EXTENSIONS) {
			candidates.add(base + extension);
		}
		return candidates;
	}

	private static String unescapeHtml(String value) {
		return value.replace("&amp;", "&")
				.replace("&quot;", "\"")
				.replace("&#39;", "'")
				.replace("&lt;", "<")
				.replace("&gt;", ">");
	}

	private static BufferedImage downloadImage(String url) {
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(url))
					.timeout(Duration.ofSeconds(20))
					.header("User-Agent", BROWSER_USER_AGENT)
					.header("Accept", "image/*")
					.GET()
					.build();
			HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
			if (response.statusCode() != 200) {
				return null;
			}
			byte[] bytes;
			try (InputStream input = response.body()) {
				bytes = input.readNBytes(MAX_BYTES + 1);
			}
			if (bytes.length > MAX_BYTES) {
				return null;
			}
			return ImageIO.read(new ByteArrayInputStream(bytes));
		} catch (Throwable t) {
			SSPasteClient.LOGGER.debug("Could not load preview image {}", url, t);
			return null;
		}
	}

	private static void register(Entry entry, NativeImage nativeImage, int width, int height) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null || minecraft.getTextureManager() == null) {
			nativeImage.close();
			entry.failed = true;
			return;
		}
		synchronized (ENTRIES) {
			if (ENTRIES.get(entry.key) != entry) {
				nativeImage.close();
				return;
			}
		}

		Identifier id = Identifier.fromNamespaceAndPath("sspaste",
				"chat/" + Integer.toHexString(NEXT_ID.incrementAndGet())
						+ "-" + Integer.toHexString(entry.url.hashCode()));
		LinearDynamicTexture texture = new LinearDynamicTexture(() -> "sspaste chat image", nativeImage);
		minecraft.getTextureManager().register(id, texture);

		entry.id = id;
		entry.width = width;
		entry.height = height;
		entry.ready = true;
		evict(minecraft, entry);
	}

	private static void evict(Minecraft minecraft, Entry justAdded) {
		List<Identifier> release = new ArrayList<>();
		synchronized (ENTRIES) {
			int ready = 0;
			for (Entry entry : ENTRIES.values()) {
				if (entry.ready) {
					ready++;
				}
			}
			if (ready <= MAX_ENTRIES) {
				return;
			}

			Iterator<Map.Entry<String, Entry>> iterator = ENTRIES.entrySet().iterator();
			while (iterator.hasNext() && ready > MAX_ENTRIES) {
				Entry entry = iterator.next().getValue();
				if (entry.ready && entry != justAdded) {
					iterator.remove();
					entry.released = true;
					entry.ready = false;
					if (entry.id != null) {
						release.add(entry.id);
					}
					ready--;
				}
			}
		}
		for (Identifier id : release) {
			minecraft.getTextureManager().release(id);
		}
	}

	private static BufferedImage scaleToFit(BufferedImage source, int maxWidth, int maxHeight) {
		double factor = Math.min(1.0,
				Math.min((double) maxWidth / source.getWidth(), (double) maxHeight / source.getHeight()));
		if (factor >= 1.0) {
			return source;
		}

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
}
