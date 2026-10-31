package com.ssclip.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import java.util.function.Supplier;
import net.minecraft.client.renderer.texture.DynamicTexture;

public final class LinearDynamicTexture extends DynamicTexture {
	public LinearDynamicTexture(Supplier<String> label, NativeImage image) {
		super(label, image);
		var cache = RenderSystem.getSamplerCache();
		if (cache != null) {
			var linear = cache.getClampToEdge(FilterMode.LINEAR);
			if (linear != null) {
				this.sampler = linear;
			}
		}
	}
}
