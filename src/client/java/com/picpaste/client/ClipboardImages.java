package com.picpaste.client;

import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.imageio.ImageIO;

public final class ClipboardImages {
	private static final DataFlavor PNG_FLAVOR = createPngFlavor();

	private ClipboardImages() {
	}

	public static void write(BufferedImage image) {
		try {
			if (GraphicsEnvironment.isHeadless()) {
				return;
			}
			Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
			ImageTransferable transferable = new ImageTransferable(image);
			clipboard.setContents(transferable, transferable);
		} catch (Throwable t) {
			PicPasteClient.LOGGER.debug("Could not write an image to the clipboard", t);
		}
	}

	private static DataFlavor createPngFlavor() {
		try {
			return new DataFlavor("image/png; class=java.io.InputStream");
		} catch (ClassNotFoundException e) {
			return null;
		}
	}

	public static Optional<BufferedImage> read() {
		try {
			if (GraphicsEnvironment.isHeadless()) {
				return Optional.empty();
			}

			Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();

			// Plain text wins, so ordinary Ctrl+V keeps working.
			if (hasText(clipboard)) {
				return Optional.empty();
			}

			for (DataFlavor flavor : clipboard.getAvailableDataFlavors()) {
				if (!isImageFlavor(flavor)) {
					continue;
				}
				try {
					BufferedImage image = toImage(clipboard.getData(flavor));
					if (image != null) {
						return Optional.of(image);
					}
				} catch (Exception ignored) {
					// Try the next flavor.
				}
			}

			if (clipboard.isDataFlavorAvailable(DataFlavor.javaFileListFlavor)) {
				Object data = clipboard.getData(DataFlavor.javaFileListFlavor);
				if (data instanceof List<?> files) {
					for (Object item : files) {
						if (item instanceof File file) {
							try {
								BufferedImage image = ImageIO.read(file);
								if (image != null) {
									return Optional.of(image);
								}
							} catch (IOException ignored) {
								// Not an image file.
							}
						}
					}
				}
			}
		} catch (Throwable t) {
			PicPasteClient.LOGGER.debug("Could not read an image from the clipboard", t);
		}
		return Optional.empty();
	}

	private static boolean hasText(Clipboard clipboard) throws Exception {
		if (!clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
			return false;
		}
		Object data = clipboard.getData(DataFlavor.stringFlavor);
		return data instanceof String text && !text.isBlank();
	}

	private static boolean isImageFlavor(DataFlavor flavor) {
		if (DataFlavor.imageFlavor.equals(flavor)) {
			return true;
		}
		String mime = flavor.getMimeType();
		return mime != null && mime.toLowerCase(Locale.ROOT).startsWith("image/");
	}

	private static BufferedImage toImage(Object data) throws IOException {
		if (data instanceof BufferedImage image) {
			return image;
		}
		if (data instanceof InputStream input) {
			try (input) {
				return ImageIO.read(input);
			}
		}
		if (data instanceof byte[] bytes) {
			return ImageIO.read(new ByteArrayInputStream(bytes));
		}
		if (data instanceof java.awt.Image image) {
			BufferedImage copy = new BufferedImage(image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_ARGB);
			java.awt.Graphics2D graphics = copy.createGraphics();
			try {
				graphics.drawImage(image, 0, 0, null);
			} finally {
				graphics.dispose();
			}
			return copy;
		}
		return null;
	}

	private static final class ImageTransferable implements Transferable, ClipboardOwner {
		private final BufferedImage image;
		private byte[] png;

		private ImageTransferable(BufferedImage image) {
			this.image = image;
		}

		@Override
		public DataFlavor[] getTransferDataFlavors() {
			return PNG_FLAVOR == null
					? new DataFlavor[]{DataFlavor.imageFlavor}
					: new DataFlavor[]{DataFlavor.imageFlavor, PNG_FLAVOR};
		}

		@Override
		public boolean isDataFlavorSupported(DataFlavor flavor) {
			return DataFlavor.imageFlavor.equals(flavor) || (PNG_FLAVOR != null && PNG_FLAVOR.equals(flavor));
		}

		@Override
		public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
			if (DataFlavor.imageFlavor.equals(flavor)) {
				return image;
			}
			if (PNG_FLAVOR != null && PNG_FLAVOR.equals(flavor)) {
				if (png == null) {
					ByteArrayOutputStream out = new ByteArrayOutputStream();
					if (!ImageIO.write(image, "png", out)) {
						throw new IOException("no PNG writer available");
					}
					png = out.toByteArray();
				}
				return new ByteArrayInputStream(png);
			}
			throw new UnsupportedFlavorException(flavor);
		}

		@Override
		public void lostOwnership(Clipboard clipboard, Transferable contents) {
		}
	}
}
