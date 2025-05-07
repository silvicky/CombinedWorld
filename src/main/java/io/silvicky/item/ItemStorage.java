package io.silvicky.item;

import io.silvicky.item.cfg.JSONConfig;
import io.silvicky.item.command.list.ListDimensionPlayers;
import io.silvicky.item.command.list.ListGroupPlayers;
import io.silvicky.item.command.list.ListWorldPlayers;
import io.silvicky.item.command.warp.BanWarp;
import io.silvicky.item.command.warp.Warp;
import io.silvicky.item.command.warp.WarpTp;
import io.silvicky.item.command.world.DeleteWorld;
import io.silvicky.item.command.world.ExportWorld;
import io.silvicky.item.command.world.ImportWorld;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ItemStorage implements ModInitializer {
	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final String MOD_ID = "ItemStorage";
    public static final Logger LOGGER = LoggerFactory.getLogger("item-storage");
	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.
		LOGGER.info("Loading CombinedWorld...");
		JSONConfig.config();
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> Warp.register(dispatcher));
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> ListWorldPlayers.register(dispatcher));
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> ListDimensionPlayers.register(dispatcher));
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> ListGroupPlayers.register(dispatcher));
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> ImportWorld.register(dispatcher));
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> DeleteWorld.register(dispatcher));
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> ExportWorld.register(dispatcher));
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> WarpTp.register(dispatcher));
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> BanWarp.register(dispatcher));
		ServerPlayerEvents.AFTER_RESPAWN.register(OnRespawn::respawn);
	}
}