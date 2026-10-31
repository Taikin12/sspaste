package com.picpaste.client;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Locale;
import javax.imageio.ImageIO;

public final class ScreenshotClipboard {
	private ScreenshotClipboard() {
	}

	public static void onSaved(File file) {
		PicPasteConfig config = PicPasteClient.config();
		if (config != null && !config.copyScreenshotToClipboard) {
			return;
		}
		if (file == null) {
			return;
		}

		String name = file.getName().toLowerCase(Locale.ROOT);
		File parent = file.getParentFile();
		if (!name.endsWith(".png") || parent == null || !parent.getName().equalsIgnoreCase("screenshots")) {
			return;
		}

		try {
			BufferedImage image = ImageIO.read(file);
			if (image != null) {
				ClipboardImages.write(image);
			}
		} catch (Exception e) {
			PicPasteClient.LOGGER.debug("Could not copy screenshot to the clipboard: {}", file, e);
		}
	}
}
