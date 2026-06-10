package io.silvicky.item_br;

import io.silvicky.item_br.worldgen.BackroomsWorldGens;
import net.fabricmc.api.ModInitializer;

import static io.silvicky.item.common.Util.LOGGER;

public class ItemStorageBackrooms implements ModInitializer {
	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.
		LOGGER.info("Loading CombinedWorld Backrooms Addon...");
		BackroomsWorldGens.register();
    }
}