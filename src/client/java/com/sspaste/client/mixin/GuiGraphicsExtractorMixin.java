package com.sspaste.client.mixin;

import com.sspaste.client.SSPasteTextures;
import com.sspaste.client.SSPasteUrls;
import com.sspaste.client.SSPasteClient;
import com.sspaste.client.SSPasteConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiGraphicsExtractorMixin {
	private static final int MAX_IMAGES = 4;

	@Inject(
			method = "componentHoverEffect(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Style;II)V",
			at = @At("HEAD"),
			cancellable = true)
	private void sspaste$renderImageTooltip(Font font, Style style, int mouseX, int mouseY, CallbackInfo ci) {
		SSPasteConfig config = SSPasteClient.config();
		if (config != null && !config.showChatImagePreviews) {
			return;
		}

		String url = SSPasteUrls.hoveredImageUrl(style);
		if (url == null) {
			return;
		}

		SSPasteTextures.Gallery gallery = SSPasteTextures.getGallery(url);
		if (gallery == null) {
			return;
		}

		List<SSPasteTextures.Entry> ready = new ArrayList<>();
		for (SSPasteTextures.Entry entry : gallery.entries()) {
			if (entry.isReady() && entry.id() != null) {
				ready.add(entry);
			}
		}
		if (ready.isEmpty()) {
			return;
		}

		int count = Math.min(MAX_IMAGES, ready.size());
		int maxDisplayWidth = config == null ? 160 : config.chatImagePreviewWidth;
		int maxDisplayHeight = config == null ? 100 : config.chatImagePreviewHeight;
		int columns = count == 1 ? 1 : 2;
		int rows = (count + columns - 1) / columns;
		int gap = 4;
		int cellWidth = maxDisplayWidth;
		int cellHeight = maxDisplayHeight;
		int gridWidth = columns * cellWidth + (columns - 1) * gap;
		int gridHeight = rows * cellHeight + (rows - 1) * gap;

		GuiGraphicsExtractor graphics = (GuiGraphicsExtractor) (Object) this;
		int x = mouseX + 12;
		int y = mouseY - 12;
		if (x + gridWidth + 8 > graphics.guiWidth()) {
			x = graphics.guiWidth() - gridWidth - 8;
		}
		if (y + gridHeight + 8 > graphics.guiHeight()) {
			y = graphics.guiHeight() - gridHeight - 8;
		}
		x = Math.max(4, x);
		y = Math.max(4, y);

		graphics.nextStratum();
		graphics.fill(x - 4, y - 4, x + gridWidth + 4, y + gridHeight + 4, 0xF0100010);
		graphics.fill(x - 4, y - 4, x + gridWidth + 4, y - 3, 0xFF7F3FBF);
		graphics.fill(x - 4, y + gridHeight + 3, x + gridWidth + 4, y + gridHeight + 4, 0xFF7F3FBF);
		graphics.fill(x - 4, y - 4, x - 3, y + gridHeight + 4, 0xFF7F3FBF);
		graphics.fill(x + gridWidth + 3, y - 4, x + gridWidth + 4, y + gridHeight + 4, 0xFF7F3FBF);

		for (int i = 0; i < count; i++) {
			SSPasteTextures.Entry entry = ready.get(i);
			int column = i % columns;
			int row = i / columns;
			int cellX = x + column * (cellWidth + gap);
			int cellY = y + row * (cellHeight + gap);

			double scale = Math.min(1.0, Math.min((double) cellWidth / entry.width(),
					(double) cellHeight / entry.height()));
			int drawWidth = Math.max(1, (int) Math.round(entry.width() * scale));
			int drawHeight = Math.max(1, (int) Math.round(entry.height() * scale));
			int drawX = cellX + (cellWidth - drawWidth) / 2;
			int drawY = cellY + (cellHeight - drawHeight) / 2;
			graphics.blit(RenderPipelines.GUI_TEXTURED, entry.id(), drawX, drawY, 0.0F, 0.0F,
					drawWidth, drawHeight, entry.width(), entry.height(), entry.width(), entry.height());
		}

		int extra = Math.max(0, gallery.totalCount() - count);
		if (extra > 0) {
			Component label = Component.literal("+" + extra);
			int textWidth = font.width(label);
			int textX = x + gridWidth - textWidth - 4;
			int textY = y + gridHeight - font.lineHeight - 2;
			graphics.fill(textX - 2, textY - 1, textX + textWidth + 2, textY + font.lineHeight, 0xC0000000);
			graphics.text(font, label, textX, textY, 0xFFFFFFFF);
		}

		ci.cancel();
	}
}
