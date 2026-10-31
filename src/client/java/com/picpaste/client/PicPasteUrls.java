package com.picpaste.client;

import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;

public final class PicPasteUrls {
	public static final String HOVER_KEY = "picpaste.image_hover";

	private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s<>\"']+");
	private static final Set<String> IMAGE_HOSTS = Set.of(
			"i.imgur.com", "imgur.com",
			"i.gyazo.com", "gyazo.com",
			"i.ibb.co", "ibb.co",
			"gzo.ai",
			"files.catbox.moe", "catbox.moe");

	private PicPasteUrls() {
	}

	public static boolean hasImageUrl(String text) {
		if (text == null || text.isEmpty()) {
			return false;
		}
		Matcher matcher = URL_PATTERN.matcher(text);
		while (matcher.find()) {
			if (isImageUrl(trimUrl(matcher.group()))) {
				return true;
			}
		}
		return false;
	}

	public static Component transform(Component component) {
		if (component == null) {
			return null;
		}

		Style style = component.getStyle();
		ComponentContents contents = component.getContents();
		MutableComponent result;

		if (contents instanceof PlainTextContents plain) {
			result = transformText(plain.text(), style);
		} else if (contents instanceof TranslatableContents translatable) {
			Object[] args = translatable.getArgs();
			Object[] newArgs = new Object[args.length];
			boolean changed = false;

			for (int i = 0; i < args.length; i++) {
				Object arg = args[i];
				if (arg instanceof Component child) {
					Component transformed = transform(child);
					newArgs[i] = transformed;
					changed |= transformed != child;
				} else if (arg instanceof String text && hasImageUrl(text)) {
					newArgs[i] = transformText(text, Style.EMPTY);
					changed = true;
				} else {
					newArgs[i] = arg;
				}
			}

			if (changed) {
				String fallback = translatable.getFallback();
				result = fallback == null
						? Component.translatable(translatable.getKey(), newArgs)
						: Component.translatableWithFallback(translatable.getKey(), fallback, newArgs);
				result.setStyle(style);
			} else {
				result = component.copy();
			}
		} else {
			result = component.copy();
		}

		for (Component sibling : component.getSiblings()) {
			result.append(transform(sibling));
		}
		return result;
	}

	public static String hoveredImageUrl(Style style) {
		if (style == null || style.getHoverEvent() == null) {
			return null;
		}
		if (!(style.getHoverEvent() instanceof HoverEvent.ShowText showText)) {
			return null;
		}
		if (!(showText.value().getContents() instanceof TranslatableContents translatable)) {
			return null;
		}
		if (!HOVER_KEY.equals(translatable.getKey())) {
			return null;
		}
		Object[] args = translatable.getArgs();
		return args.length > 0 && args[0] instanceof String url ? url : null;
	}

	private static MutableComponent transformText(String text, Style style) {
		Matcher matcher = URL_PATTERN.matcher(text);
		MutableComponent result = Component.literal("").setStyle(style);
		boolean found = false;
		int last = 0;

		while (matcher.find()) {
			String url = trimUrl(matcher.group());
			if (!isImageUrl(url)) {
				continue;
			}
			found = true;
			if (matcher.start() > last) {
				result.append(Component.literal(text.substring(last, matcher.start())).setStyle(style));
			}
			if (hoveredImageUrl(style) == null) {
				result.append(imageComponent(url, style));
			} else {
				result.append(Component.literal(url).setStyle(style));
			}
			last = matcher.end();
		}

		if (!found) {
			return Component.literal(text).setStyle(style);
		}
		if (last < text.length()) {
			result.append(Component.literal(text.substring(last)).setStyle(style));
		}
		return result;
	}

	private static MutableComponent imageComponent(String url, Style style) {
		Style imageStyle = style.withHoverEvent(new HoverEvent.ShowText(
				Component.translatable(HOVER_KEY, url)));
		if (style.getClickEvent() == null) {
			imageStyle = imageStyle.withClickEvent(new ClickEvent.OpenUrl(URI.create(url)));
		}
		return Component.literal(url).setStyle(imageStyle);
	}

	private static String trimUrl(String raw) {
		String url = raw;
		while (!url.isEmpty()) {
			char last = url.charAt(url.length() - 1);
			if (last == ')' || last == ']' || last == '}' || last == ',' || last == ';') {
				url = url.substring(0, url.length() - 1);
			} else if (last == '.' && !hasImageExtension(url)) {
				url = url.substring(0, url.length() - 1);
			} else {
				break;
			}
		}
		return url;
	}

	private static boolean isImageUrl(String url) {
		if (url == null || url.isEmpty()) {
			return false;
		}
		URI uri;
		try {
			uri = URI.create(url);
		} catch (IllegalArgumentException e) {
			return false;
		}
		if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
			return false;
		}
		String host = uri.getHost().toLowerCase(Locale.ROOT);
		if (!isAllowedImageHost(host)) {
			return false;
		}
		String path = uri.getPath();
		return hasImageExtension(path) || isViewerPath(host, path);
	}

	public static boolean isDirectImageUrl(String url) {
		if (url == null || url.isEmpty()) {
			return false;
		}
		URI uri;
		try {
			uri = URI.create(url);
		} catch (IllegalArgumentException e) {
			return false;
		}
		if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
			return false;
		}
		return isAllowedImageHost(uri.getHost().toLowerCase(Locale.ROOT)) && hasImageExtension(uri.getPath());
	}

	public static boolean isAllowedImageHost(String host) {
		if (host == null) {
			return false;
		}
		String lower = host.toLowerCase(Locale.ROOT);
		for (String allowed : IMAGE_HOSTS) {
			if (lower.equals(allowed) || lower.endsWith("." + allowed)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isViewerPath(String host, String path) {
		if (path == null) {
			return false;
		}
		if (host.equals("imgur.com") || host.endsWith(".imgur.com")) {
			return path.matches("/(?:gallery/|a/)?[A-Za-z0-9]{5,10}");
		}
		if (host.equals("gyazo.com") || host.endsWith(".gyazo.com")) {
			return path.matches("/[A-Za-z0-9]{5,64}");
		}
		if (host.equals("gzo.ai") || host.endsWith(".gzo.ai")) {
			return path.matches("/i/[A-Za-z0-9]{4,32}");
		}
		if (host.equals("ibb.co") || host.endsWith(".ibb.co")) {
			return path.matches("/[A-Za-z0-9]{5,20}");
		}
		return false;
	}

	private static boolean hasImageExtension(String path) {
		if (path == null) {
			return false;
		}
		String lower = path.toLowerCase(Locale.ROOT);
		return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
				|| lower.endsWith(".gif") || lower.endsWith(".webp");
	}
}
