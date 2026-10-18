package io.silvicky.item;

import io.silvicky.item.backrooms.AbyssWindow;
import io.silvicky.item.cfg.JSONConfig;
import io.silvicky.item.command.backrooms.*;
import io.silvicky.item.command.list.ListDimensionPlayers;
import io.silvicky.item.command.list.ListGroupPlayers;
import io.silvicky.item.command.list.ListWorldPlayers;
import io.silvicky.item.command.utility.LocatePlayer;
import io.silvicky.item.command.utility.PackMe;
import io.silvicky.item.command.warp.BanWarp;
import io.silvicky.item.command.warp.Evacuate;
import io.silvicky.item.command.warp.Warp;
import io.silvicky.item.command.warp.WarpTp;
import io.silvicky.item.command.world.CreateWorld;
import io.silvicky.item.command.warp.DefaultMode;
import io.silvicky.item.command.world.DeleteWorld;
import io.silvicky.item.command.world.ExportWorld;
import io.silvicky.item.command.world.ImportWorld;
import io.silvicky.item.worldgen.WorldGens;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import static io.silvicky.item.common.Util.LOGGER;
public class ItemStorage implements ModInitializer {
	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.
		LOGGER.info("Loading CombinedWorld...");
		WorldGens.register();
		JSONConfig.config();
		CommandRegistrationCallback.EVENT.register((dispatcher, _, _) -> Warp.register(dispatcher));
		CommandRegistrationCallback.EVENT.register((dispatcher, _, _) -> ListWorldPlayers.register(dispatcher));
		CommandRegistrationCallback.EVENT.register((dispatcher, _, _) -> ListDimensionPlayers.register(dispatcher));
		CommandRegistrationCallback.EVENT.register((dispatcher, _, _) -> ListGroupPlayers.register(dispatcher));
		CommandRegistrationCallback.EVENT.register((dispatcher, _, _) -> ImportWorld.register(dispatcher));
		CommandRegistrationCallback.EVENT.register((dispatcher, _, _) -> CreateWorld.register(dispatcher));
		CommandRegistrationCallback.EVENT.register((dispatcher, _, _) -> DeleteWorld.register(dispatcher));
		CommandRegistrationCallback.EVENT.register((dispatcher, _, _) -> DefaultMode.register(dispatcher));
		CommandRegistrationCallback.EVENT.register((dispatcher, _, _) -> ExportWorld.register(dispatcher));
		CommandRegistrationCallback.EVENT.register((dispatcher, _, _) -> WarpTp.register(dispatcher));
		CommandRegistrationCallback.EVENT.register((dispatcher, _, _) -> BanWarp.register(dispatcher));
		CommandRegistrationCallback.EVENT.register((dispatcher, _, _) -> Evacuate.register(dispatcher));
		CommandRegistrationCallback.EVENT.register((dispatcher, _, _) -> PackMe.register(dispatcher));
		CommandRegistrationCallback.EVENT.register((dispatcher, _, _) -> Visibility.register(dispatcher));
		CommandRegistrationCallback.EVENT.register((dispatcher, _, _) -> Silence.register(dispatcher));
        CommandRegistrationCallback.EVENT.register((dispatcher, _, _) -> ChunkTransformer.register(dispatcher));
        CommandRegistrationCallback.EVENT.register((dispatcher, _, _) -> Darkness.register(dispatcher));
		CommandRegistrationCallback.EVENT.register((dispatcher, _, _) -> Distance.register(dispatcher));
		CommandRegistrationCallback.EVENT.register((dispatcher, _, _) -> Noclip.register(dispatcher));
		CommandRegistrationCallback.EVENT.register((dispatcher, _, _) -> NoclipVoid.register(dispatcher));
		CommandRegistrationCallback.EVENT.register((dispatcher, _, _) -> LocatePlayer.register(dispatcher));
		//Abyss window demo: housekeeping at the start of the server tick, so a slide's position packet precedes
		//every chunk packet of that tick. Chunk loads are recorded off-thread by the event below and drained
		//here - see AbyssWindow.loadedColumns.
		ServerTickEvents.START_SERVER_TICK.register(AbyssWindow::tick);
		//A chunk in a window layer finished loading. The column it belongs to may already have been sent - with
		//that band padded as air, because composition saw it was not loaded yet - so ask for it again.
		//This handler runs off the server thread and only records; the drain happens in tick.
		ServerChunkEvents.CHUNK_LOAD.register((level, chunk, newlyGenerated) -> AbyssWindow.onLayerChunkLoaded(level, chunk));
    }
}