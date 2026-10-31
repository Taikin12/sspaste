package com.picpaste.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;

public final class PicPasteHistory {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Type LIST_TYPE = new TypeToken<List<Entry>>() {
	}.getType();
	private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir();
	private static final Path JSON_PATH = CONFIG_DIR.resolve("picpaste-history.json");
	private static final Path HTML_PATH = CONFIG_DIR.resolve("picpaste-history.html");
	private static final int MAX_ENTRIES = 200;
	private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
	private static final List<Entry> ENTRIES = new ArrayList<>();

	public record Entry(String time, String host, String url, String deleteUrl, String deleteKey) {
	}

	private PicPasteHistory() {
	}

	public static Path htmlPath() {
		return HTML_PATH;
	}

	public static Path jsonPath() {
		return JSON_PATH;
	}

	public static void load() {
		boolean failed = false;
		synchronized (ENTRIES) {
			ENTRIES.clear();
			if (Files.exists(JSON_PATH)) {
				try {
					List<Entry> loaded = GSON.fromJson(
							Files.readString(JSON_PATH, StandardCharsets.UTF_8), LIST_TYPE);
					if (loaded != null) {
						ENTRIES.addAll(loaded);
					}
				} catch (Exception e) {
					failed = true;
					PicPasteClient.LOGGER.warn("Could not read {}", JSON_PATH, e);
					try {
						Files.copy(JSON_PATH, CONFIG_DIR.resolve("picpaste-history.json.bak"));
					} catch (IOException ignored) {
					}
				}
			}
			if (ENTRIES.size() > MAX_ENTRIES) {
				ENTRIES.subList(MAX_ENTRIES, ENTRIES.size()).clear();
			}
		}
		if (!failed) {
			save();
		}
	}

	public static void add(UploadResult result) {
		if (result == null || result.url() == null || result.url().isBlank()) {
			return;
		}
		Entry entry = new Entry(
				LocalDateTime.now().format(TIME_FORMAT),
				result.host(),
				result.url(),
				result.deleteUrl(),
				result.deleteKey());
		synchronized (ENTRIES) {
			ENTRIES.add(0, entry);
			while (ENTRIES.size() > MAX_ENTRIES) {
				ENTRIES.remove(ENTRIES.size() - 1);
			}
		}
		save();
	}

	public static void save() {
		List<Entry> copy;
		synchronized (ENTRIES) {
			copy = new ArrayList<>(ENTRIES);
		}
		try {
			Files.createDirectories(CONFIG_DIR);
			Files.writeString(JSON_PATH, GSON.toJson(copy) + System.lineSeparator(), StandardCharsets.UTF_8);
			Files.writeString(HTML_PATH, buildHtml(copy), StandardCharsets.UTF_8);
		} catch (IOException e) {
			PicPasteClient.LOGGER.warn("Could not write upload history", e);
		}
	}

	private static String buildHtml(List<Entry> entries) {
		StringBuilder html = new StringBuilder();
		html.append("<!doctype html>\n<html lang=\"ja\">\n<head>\n<meta charset=\"utf-8\">\n");
		html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n");
		html.append("<title>PicPaste アップロード履歴</title>\n");
		html.append("<style>\n");
		html.append(":root{color-scheme:light;--bg:#f7f7f8;--fg:#1a1a1a;--muted:#666;--line:#d0d0d5;--accent:#1e88e5;--danger:#c62828;}\n");
		html.append("body{margin:0;background:var(--bg);color:var(--fg);font-family:system-ui,-apple-system,'Segoe UI','Noto Sans JP',sans-serif;line-height:1.6;}\n");
		html.append("main{max-width:1100px;margin:0 auto;padding:32px 20px 64px;}\n");
		html.append("h1{font-size:1.6rem;margin:0 0 8px;}\n");
		html.append("p{margin:8px 0;}\n");
		html.append(".warn{border-left:4px solid var(--danger);background:#fff1f1;padding:10px 12px;}\n");
		html.append("table{width:100%;border-collapse:collapse;background:#fff;margin-top:16px;}\n");
		html.append("th,td{border-bottom:1px solid var(--line);padding:10px 12px;text-align:left;vertical-align:top;font-size:.92rem;}\n");
		html.append("th{background:#eceff3;font-weight:600;white-space:nowrap;}\n");
		html.append("td.url{word-break:break-all;}\n");
		html.append("a{color:var(--accent);}\n");
		html.append(".del{color:var(--danger);font-weight:600;}\n");
		html.append(".muted{color:var(--muted);}\n");
		html.append("code{background:#eef1f5;padding:1px 4px;border-radius:4px;word-break:break-all;}\n");
		html.append("</style>\n</head>\n<body>\n<main>\n");
		html.append("<h1>PicPaste アップロード履歴</h1>\n");
		html.append("<p class=\"warn\">削除リンクは秘密情報です。このファイルや <code>picpaste-history.json</code> を他人と共有しないでください。</p>\n");
		html.append("<p>新しい順に最大 ").append(MAX_ENTRIES).append(" 件を保存しています。JSON: <code>")
				.append(escape(JSON_PATH.toString())).append("</code></p>\n");

		if (entries.isEmpty()) {
			html.append("<p>まだアップロード履歴がありません。</p>\n");
		} else {
			html.append("<table>\n<thead><tr><th>日時</th><th>ホスト</th><th>画像URL</th><th>削除</th></tr></thead>\n<tbody>\n");
			for (Entry entry : entries) {
				html.append("<tr><td>").append(escape(entry.time())).append("</td><td>")
						.append(escape(entry.host())).append("</td><td class=\"url\"><a href=\"")
						.append(escape(entry.url())).append("\" target=\"_blank\" rel=\"noreferrer\">")
						.append(escape(entry.url())).append("</a></td><td>");

				if ("gyazo".equals(entry.host())) {
					if (entry.deleteUrl() != null && !entry.deleteUrl().isBlank()) {
						html.append("<a href=\"").append(escape(entry.deleteUrl()))
								.append("\" target=\"_blank\" rel=\"noreferrer\">Gyazoで開く</a>");
					} else {
						html.append("Gyazoで開く");
					}
					html.append("<br><span class=\"muted\">削除はGyazoの画面から行ってください。</span>");
				} else if (entry.deleteUrl() != null && !entry.deleteUrl().isBlank()) {
					html.append("<a class=\"del\" href=\"").append(escape(entry.deleteUrl()))
							.append("\" target=\"_blank\" rel=\"noreferrer\">削除</a>");
					if (entry.deleteKey() != null && !entry.deleteKey().isBlank()) {
						html.append("<br>キー: <code>").append(escape(entry.deleteKey())).append("</code>");
					}
				} else if (entry.deleteKey() != null && !entry.deleteKey().isBlank()) {
					html.append("キー: <code>").append(escape(entry.deleteKey())).append("</code>");
				} else {
					html.append("削除リンクなし");
				}
				html.append("</td></tr>\n");
			}
			html.append("</tbody>\n</table>\n");
		}
		html.append("</main>\n</body>\n</html>\n");
		return html.toString();
	}

	private static String escape(String value) {
		if (value == null) {
			return "";
		}
		return value.replace("&", "&amp;")
				.replace("<", "&lt;")
				.replace(">", "&gt;")
				.replace("\"", "&quot;")
				.replace("'", "&#39;");
	}
}
