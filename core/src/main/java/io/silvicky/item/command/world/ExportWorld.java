package io.silvicky.item.command.world;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.silvicky.item.StateSaver;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.dimension.LevelStem;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static io.silvicky.item.command.world.ImportWorld.wrapper;
import static io.silvicky.item.common.Util.*;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class ExportWorld {
    private static Identifier id;
    private static boolean firstType = true;
    private static StateSaver stateSaver;
    private static MinecraftServer server;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                literal("exportworld")
                        .requires(source -> source.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.OWNERS)))
                        .executes(context -> help(context.getSource()))
                        .then(argument(DIMENSION_ID, DimensionArgument.dimension())
                                .suggests(new io.silvicky.item.command.suggestion.WorldSuggestionProvider())
                                .executes(context -> exportWorld(context.getSource(), DimensionArgument.getDimension(context, DIMENSION_ID), Paths.get(FabricLoader.getInstance().getGameDir().toString(), "exported")))
                                .then(argument(DIMENSION_PATH, StringArgumentType.greedyString())
                                        .executes(context -> exportWorld(context.getSource(), DimensionArgument.getDimension(context, DIMENSION_ID), Paths.get(StringArgumentType.getString(context, DIMENSION_PATH)))))));
    }

    private static int help(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Usage: /exportworld <id>"), false);
        source.sendSuccess(() -> Component.literal("Export world of <id> into <game_root>/exported. (Overwrites existing export.)"), false);
        source.sendSuccess(() -> Component.literal("It creates a standalone vanilla save, but only includes players who are recorded in CombinedWorld's state."), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int exportWorld(CommandSourceStack source, ServerLevel world, Path exportPath) {
        if (firstType) {
            firstType = false;
            source.sendSuccess(() -> Component.literal("Hello, admin! This command can export a world. It is still strongly suggested that you backup your save first. Also you need to read the result carefully. Type this command without arguments to see the help. Type this command again if you already understand what you are doing."), false);
            return Command.SINGLE_SUCCESS;
        }
        source.sendSuccess(() -> Component.literal("Running..."), false);
        id = getDimensionId(world);
        final boolean isSinglet = !id.getPath().endsWith(OVERWORLD);
        Identifier idNether = isSinglet ? null : Identifier.fromNamespaceAndPath(id.getNamespace(), id.getPath().substring(0, id.getPath().length() - OVERWORLD.length()) + NETHER);
        Identifier idEnd = isSinglet ? null : Identifier.fromNamespaceAndPath(id.getNamespace(), id.getPath().substring(0, id.getPath().length() - OVERWORLD.length()) + END);
        server = source.getServer();
        stateSaver = StateSaver.getServerState(server);

        new Thread(() -> {
            try {
                ServerLevel src = server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
                if (src == null) throw new IllegalStateException("Dimension is not loaded: " + id);
                Path exported = exportPath == null ? FabricLoader.getInstance().getGameDir().resolve("exported") : exportPath;
                try {
                    deleteFolder(exported);
                } catch (Exception ignored) {
                }
                exported.toFile().mkdirs();

                Path sourceBase = server.getWorldPath(LevelResource.ROOT).resolve("dimensions").resolve(id.getNamespace());
                Path sourceOverworld = sourceBase.resolve(id.getPath());
                Path dimensions = exported.resolve("dimensions").resolve("minecraft");
                dimensions.toFile().mkdirs();
                copyFolder(sourceOverworld, dimensions.resolve("overworld"));
                if (!isSinglet) {
                    Path sourceNether = sourceBase.resolve(idNether.getPath());
                    Path sourceEnd = sourceBase.resolve(idEnd.getPath());
                    copyFolder(sourceNether, dimensions.resolve("the_nether"));
                    copyFolder(sourceEnd, dimensions.resolve("the_end"));
                }
                source.sendSuccess(() -> Component.literal("Copied dimension save files."), false);

                // Level data: use the current world's level.dat as a template and patch the parts import actually uses.
                Path hostLevelDat = server.getWorldPath(LevelResource.ROOT).resolve("level.dat");
                CompoundTag hostRoot = NbtIo.readCompressed(hostLevelDat, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
                CompoundTag data = hostRoot.getCompoundOrEmpty("Data");
                int gamemode = stateSaver.gamemode.getOrDefault(id.getNamespace(), server.getDefaultGameType().getId());
                data.putInt("GameType", gamemode);

                Identifier storedSpawnDimension;
                LevelData.RespawnData spawn = stateSaver.worldSpawn.get(id);
                if (spawn == null) spawn = src.getRespawnData();
                storedSpawnDimension = spawn.dimension().identifier();
                Identifier vanillaDim;
                if (storedSpawnDimension.equals(idNether)) vanillaDim = Identifier.fromNamespaceAndPath("minecraft", "the_nether");
                else if (storedSpawnDimension.equals(idEnd)) vanillaDim = Identifier.fromNamespaceAndPath("minecraft", "the_end");
                else vanillaDim = Identifier.fromNamespaceAndPath("minecraft", "overworld");
                LevelData.RespawnData vanillaSpawn = LevelData.RespawnData.of(
                        ResourceKey.create(Registries.DIMENSION, vanillaDim),
                        spawn.pos(),
                        spawn.yaw(),
                        spawn.pitch()
                );
                Tag spawnTag = LevelData.RespawnData.CODEC.encodeStart(NbtOps.INSTANCE, vanillaSpawn).getOrThrow();
                data.put("spawn", spawnTag);
                if (stateSaver.seed.containsKey(id)) data.putLong("Seed", stateSaver.seed.get(id));
                hostRoot.put("Data", data);
                NbtIo.writeCompressed(hostRoot, exported.resolve("level.dat"));
                source.sendSuccess(() -> Component.literal("Wrote level.dat."), false);

                // World generation settings: reuse the source dimensions, but make their resource keys vanilla.
                WorldGenSettings sourceSettings = server.getWorldGenSettings();
                Map<ResourceKey<LevelStem>, LevelStem> sourceDimensions = sourceSettings.dimensions().dimensions();
                Map<ResourceKey<LevelStem>, LevelStem> exportedDimensions = new HashMap<>();
                ResourceKey<LevelStem> sourceOverworldKey = ResourceKey.create(Registries.LEVEL_STEM, id);
                LevelStem sourceOverworldStem = sourceDimensions.get(sourceOverworldKey);
                if (sourceOverworldStem == null) throw new IllegalStateException("Missing LevelStem for " + id);
                exportedDimensions.put(ResourceKey.create(Registries.LEVEL_STEM, Identifier.parse("minecraft:overworld")), sourceOverworldStem);
                LevelStem sourceNetherStem;
                LevelStem sourceEndStem;
                if (!isSinglet) {
                    sourceNetherStem = sourceDimensions.get(ResourceKey.create(Registries.LEVEL_STEM, idNether));
                    sourceEndStem = sourceDimensions.get(ResourceKey.create(Registries.LEVEL_STEM, idEnd));
                } else {
                    sourceNetherStem = sourceDimensions.get(ResourceKey.create(Registries.LEVEL_STEM, Identifier.fromNamespaceAndPath("minecraft", "the_nether")));
                    sourceEndStem = sourceDimensions.get(ResourceKey.create(Registries.LEVEL_STEM, Identifier.fromNamespaceAndPath("minecraft", "the_end")));
                }
                if (sourceNetherStem == null || sourceEndStem == null) throw new IllegalStateException("Missing LevelStem for triplet.");
                exportedDimensions.put(ResourceKey.create(Registries.LEVEL_STEM, Identifier.parse("minecraft:the_nether")), sourceNetherStem);
                exportedDimensions.put(ResourceKey.create(Registries.LEVEL_STEM, Identifier.parse("minecraft:the_end")), sourceEndStem);
                long seed = stateSaver.seed.getOrDefault(id, src.getSeed());
                WorldOptions worldOptions = new WorldOptions(seed, sourceSettings.options().generateStructures(), sourceSettings.options().generateBonusChest());
                WorldGenSettings exportedSettings = new WorldGenSettings(worldOptions, new WorldDimensions(exportedDimensions));
                Path worldGenPath = exported.resolve("data").resolve("minecraft");
                worldGenPath.toFile().mkdirs();
                RegistryOps<Tag> ops=RegistryOps.create(NbtOps.INSTANCE,wrapper);
                Tag encodedSettings = WorldGenSettings.CODEC.encodeStart(ops, exportedSettings).getOrThrow();
                CompoundTag worldGenRoot = new CompoundTag();
                int hostDataVersion = hostRoot.getCompoundOrEmpty("Data").getInt("DataVersion").orElse(0);
                worldGenRoot.putInt("DataVersion", hostDataVersion);
                worldGenRoot.put("data", encodedSettings);
                NbtIo.writeCompressed(worldGenRoot, worldGenPath.resolve("world_gen_settings.dat"));
                source.sendSuccess(() -> Component.literal("Wrote world_gen_settings.dat."), false);

                // Players: rebuild vanilla player files from CombinedWorld's stored inventory data.
                Path playersData = exported.resolve("players").resolve("data");
                playersData.toFile().mkdirs();
                HashMap<String, StateSaver.StorageInfoNew> players = stateSaver.savedMap.getOrDefault(id.getNamespace(), new HashMap<>());
                int cnt = 0;
                int failed = 0;
                for (Map.Entry<String, StateSaver.StorageInfoNew> entry : players.entrySet()) {
                    try {//TODO the vanilla player file should take precedence
                        UUID uuid = UUID.fromString(entry.getKey());
                        StateSaver.StorageInfoNew storage = entry.getValue();
                        StateSaver.PositionInfoNew posInfo = null;
                        HashMap<String, StateSaver.PositionInfoNew> playerPosMap = stateSaver.posMap.get(id);
                        if (playerPosMap != null) posInfo = playerPosMap.get(entry.getKey());

                        ServerLevel spawnLevel = src;
                        String sourceDimension = id.toString();
                        if (posInfo != null) {
                            sourceDimension = posInfo.dimension().toString();
                            ServerLevel maybe = server.getLevel(ResourceKey.create(Registries.DIMENSION, posInfo.dimension()));
                            if (maybe != null) spawnLevel = maybe;
                        }
                        Identifier dimensionIdentifier = Identifier.parse(sourceDimension);
                        if (dimensionIdentifier.equals(idNether)) dimensionIdentifier = Identifier.parse("minecraft:the_nether");
                        else if (dimensionIdentifier.equals(idEnd)) dimensionIdentifier = Identifier.parse("minecraft:the_end");
                        else if (dimensionIdentifier.equals(id)) dimensionIdentifier = Identifier.parse("minecraft:overworld");
                        else if (!dimensionIdentifier.getNamespace().equals("minecraft")) dimensionIdentifier = Identifier.parse("minecraft:overworld");

                        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(uuid, "exported"), false);
                        ServerPlayer fake = new ServerPlayer(server, spawnLevel, cookie.gameProfile(), cookie.clientInformation());
                        stackToInventory(fake.getInventory(), storage.inventory());
                        stackToInventory(fake.getEnderChestInventory(), storage.ender());
                        fake.setExperiencePoints(storage.xp());
                        fake.setHealth(storage.hp());
                        fake.getFoodData().setFoodLevel(storage.food());
                        fake.getFoodData().setSaturation(storage.saturation());
                        fake.setAirSupply(storage.air());
                        fake.gameMode.changeGameModeForPlayer(GameType.byId(storage.gamemode()));
                        if (posInfo != null) {
                            fake.setPos(posInfo.pos().x, posInfo.pos().y, posInfo.pos().z);
                            fake.setYRot(posInfo.yaw());
                            fake.setXRot(posInfo.pitch());
                        }
                        ProblemReporter.ScopedCollector logging = new ProblemReporter.ScopedCollector(fake.problemPath(), LOGGER);
                        TagValueOutput output = TagValueOutput.createWithContext(logging, fake.registryAccess());
                        fake.saveWithoutId(output);
                        CompoundTag playerTag = output.buildResult();
                        playerTag.putString("Dimension", dimensionIdentifier.toString());
                        playerTag.store("UUID", net.minecraft.core.UUIDUtil.CODEC, uuid);
                        playerTag.putInt("DataVersion", hostDataVersion);
                        NbtIo.writeCompressed(playerTag, playersData.resolve(uuid + ".dat"));
                        cnt++;
                    } catch (Exception e) {
                        e.printStackTrace();
                        failed++;
                    }
                }
                int finalCnt = cnt;
                int finalFailed = failed;
                source.sendSuccess(() -> Component.literal(String.format("Exported %d player data, %d failed", finalCnt, finalFailed)), false);
                source.sendSuccess(() -> Component.literal("Exported world to: " + exported), false);
            } catch (Exception e) {
                e.printStackTrace();
                source.sendFailure(Component.literal(e.toString()));
            }
        }).start();
        return Command.SINGLE_SUCCESS;
    }
}
