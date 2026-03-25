package io.silvicky.item;

import io.silvicky.item.cfg.JSONConfig;
import io.silvicky.item.command.backrooms.*;
import io.silvicky.item.command.list.ListDimensionPlayers;
import io.silvicky.item.command.list.ListGroupPlayers;
import io.silvicky.item.command.list.ListWorldPlayers;
import io.silvicky.item.command.utility.PackMe;
import io.silvicky.item.command.warp.*;
import io.silvicky.item.command.world.DeleteWorld;
import io.silvicky.item.command.world.ImportWorld;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import static io.silvicky.item.common.Util.LOGGER;
public class ItemStorage implements ModInitializer {
	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.
		LOGGER.info("Loading CombinedWorld...");
		JSONConfig.config();
		CommandRegistrationCallback.EVENT.register((dispatcher, _, _) -> Warp.register(dispatcher));
		CommandRegistrationCallback.EVENT.register((dispatcher, _, _) -> ListWorldPlayers.register(dispatcher));
		CommandRegistrationCallback.EVENT.register((dispatcher, _, _) -> ListDimensionPlayers.register(dispatcher));
		CommandRegistrationCallback.EVENT.register((dispatcher, _, _) -> ListGroupPlayers.register(dispatcher));
		CommandRegistrationCallback.EVENT.register((dispatcher, _, _) -> ImportWorld.register(dispatcher));
		CommandRegistrationCallback.EVENT.register((dispatcher, _, _) -> DeleteWorld.register(dispatcher));
		CommandRegistrationCallback.EVENT.register((dispatcher, _, _) -> DefaultMode.register(dispatcher));
		//CommandRegistrationCallback.EVENT.register((dispatcher, _, _) -> ExportWorld.register(dispatcher));
		CommandRegistrationCallback.EVENT.register((dispatcher, _, _) -> WarpTp.register(dispatcher));
		CommandRegistrationCallback.EVENT.register((dispatcher, _, _) -> BanWarp.register(dispatcher));
		CommandRegistrationCallback.EVENT.register((dispatcher, _, _) -> Evacuate.register(dispatcher));
		CommandRegistrationCallback.EVENT.register((dispatcher, _, _) -> PackMe.register(dispatcher));
		CommandRegistrationCallback.EVENT.register((dispatcher, _, _) -> Visibility.register(dispatcher));
		CommandRegistrationCallback.EVENT.register((dispatcher, _, _) -> Silence.register(dispatcher));
        CommandRegistrationCallback.EVENT.register((dispatcher, _, _) -> ChunkTransformer.register(dispatcher));
        CommandRegistrationCallback.EVENT.register((dispatcher, _, _) -> Darkness.register(dispatcher));
		CommandRegistrationCallback.EVENT.register((dispatcher, _, _) -> Distance.register(dispatcher));
    }
}