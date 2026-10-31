package com.picpaste.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;

public class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<Screen> getModConfigScreenFactory() {
		if (!FabricLoader.getInstance().isModLoaded("cloth-config")) {
			return parent -> null;
		}
		return PicPasteConfigScreen::create;
	}
}
