package io.silvicky.item_br;

import io.silvicky.item_br.worldgen.BackroomsWorldGens;
import io.silvicky.item_br.worldgen.road2.Road2CustomRule;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import static io.silvicky.item.common.Util.LOGGER;

public class ItemStorageBackrooms implements ModInitializer {
	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.
		LOGGER.info("Loading CombinedWorld Backrooms Addon...");
		BackroomsWorldGens.register();
		//Road2CustomRule now holds one region cache per world, keyed by RandomState, so it has to be
		//released on shutdown - otherwise that map would pin every level's ChunkMap for the life of the JVM.
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> Road2CustomRule.clearCaches());
    }
}