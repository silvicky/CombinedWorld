package io.silvicky.item_br;

import io.silvicky.item_br.worldgen.BackroomsWorldGens;
import io.silvicky.item_br.worldgen.abyss.AbyssWorlds;
import io.silvicky.item_br.worldgen.road2.Road2CustomRule;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import static io.silvicky.item.command.world.StagedDimensions.register;

import static io.silvicky.item.common.Util.LOGGER;

public class ItemStorageBackrooms implements ModInitializer {
	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.
		LOGGER.info("Loading CombinedWorld Backrooms Addon...");
		BackroomsWorldGens.register();
		//Abyss demo: five layers contributed when a save is written, then created on the following start.
		//Deliberately not staged at SERVER_STARTED - see AbyssWorlds for why the holders have to be resolved
		//against the registry access the save is encoded with.
		register(new AbyssWorlds());
		//Road2CustomRule now holds one region cache per world, keyed by RandomState, so it has to be
		//released on shutdown - otherwise that map would pin every level's ChunkMap for the life of the JVM.
		ServerLifecycleEvents.SERVER_STOPPED.register(_ -> Road2CustomRule.clearCaches());
		//Report loaded chunks, outstanding tickets and heap once a minute. An 8 GB heap was exhausted with
		//9,980 chunks held in a custom dimension, and the only evidence available was a stack trace naming
		//whichever map rehashed last - which shows where allocation failed, never what caused it.
		//ServerTickEvents.END_SERVER_TICK.register(server -> ChunkDiagnostics.report(server));
    }
}