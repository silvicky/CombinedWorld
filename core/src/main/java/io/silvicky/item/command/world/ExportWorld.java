package io.silvicky.item.command.world;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.silvicky.item.InventoryManager;
import io.silvicky.item.StateSaver;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
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
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.dimension.LevelStem;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

import static io.silvicky.item.command.world.ImportWorld.wrapper;
import static io.silvicky.item.common.Util.*;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class ExportWorld {
    private static Identifier id;
    private static Identifier idNether;
    private static Identifier idEnd;
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
        idNether = isSinglet ? null : Identifier.fromNamespaceAndPath(id.getNamespace(), id.getPath().substring(0, id.getPath().length() - OVERWORLD.length()) + NETHER);
        idEnd = isSinglet ? null : Identifier.fromNamespaceAndPath(id.getNamespace(), id.getPath().substring(0, id.getPath().length() - OVERWORLD.length()) + END);
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

                // Players: walk this save's own player files instead of trusting our state alone. A player
                // whose file points into this world has this world's data in it, so keep the tag and only
                // rewrite Dimension; everyone else's file holds another world's data, so rebuild it from
                // the state we stored for this namespace.
                Path playersData = exported.resolve("players").resolve("data");
                playersData.toFile().mkdirs();
                HashMap<String, StateSaver.StorageInfoNew> storedPlayers = stateSaver.savedMap.getOrDefault(id.getNamespace(), new HashMap<>());
                HashMap<String, StateSaver.PositionInfoNew> storedPositions = stateSaver.posMap.get(id);
                HashSet<String> walked = new HashSet<>();
                int kept = 0;
                int rebuilt = 0;
                int failed = 0;
                File[] playerFiles = server.getWorldPath(LevelResource.PLAYER_DATA_DIR).toFile().listFiles();
                if (playerFiles != null) {
                    for (File file : playerFiles) {
                        String fileName = file.getName();
                        if (!fileName.endsWith(".dat")) continue;
                        String uuidString = fileName.substring(0, fileName.length() - ".dat".length());
                        UUID uuid;
                        try {
                            uuid = UUID.fromString(uuidString);
                        } catch (IllegalArgumentException e) {
                            continue;
                        }
                        walked.add(uuidString);
                        try {
                            CompoundTag playerTag = NbtIo.readCompressed(file.toPath(), NbtAccounter.unlimitedHeap());
                            playerTag = DataFixTypes.PLAYER.updateToCurrentVersion(DataFixers.getDataFixer(), playerTag, NbtUtils.getDataVersion(playerTag, -1));
                            Identifier current = Identifier.parse(playerTag.getStringOr("Dimension", "minecraft:overworld"));
                            Identifier vanilla = vanillaDimension(current);
                            if (vanilla != null) {
                                playerTag.putString("Dimension", vanilla.toString());
                                NbtIo.writeCompressed(playerTag, playersData.resolve(fileName));
                                kept++;
                                continue;
                            }
                            if (!storedPlayers.containsKey(uuidString)) continue; //never visited this world, there is nothing to export
                            StateSaver.PositionInfoNew posInfo = storedPositions == null ? null : storedPositions.get(uuidString);
                            NbtIo.writeCompressed(buildPlayerTag(uuid, posInfo, src, hostDataVersion), playersData.resolve(fileName));
                            rebuilt++;
                        } catch (Exception e) {
                            e.printStackTrace();
                            failed++;
                        }
                    }
                }
                // Players of an imported world may never have joined this server, so they have no file above.
                for (String uuidString : storedPlayers.keySet()) {
                    if (walked.contains(uuidString)) continue;
                    try {
                        UUID uuid = UUID.fromString(uuidString);
                        StateSaver.PositionInfoNew posInfo = storedPositions == null ? null : storedPositions.get(uuidString);
                        NbtIo.writeCompressed(buildPlayerTag(uuid, posInfo, src, hostDataVersion), playersData.resolve(uuid + ".dat"));
                        rebuilt++;
                    } catch (Exception e) {
                        e.printStackTrace();
                        failed++;
                    }
                }
                int finalKept = kept;
                int finalRebuilt = rebuilt;
                int finalFailed = failed;
                source.sendSuccess(() -> Component.literal(String.format("Exported %d player data (%d kept from vanilla, %d rebuilt from state), %d failed", finalKept + finalRebuilt, finalKept, finalRebuilt, finalFailed)), false);
                source.sendSuccess(() -> Component.literal("Exported world to: " + exported), false);
            } catch (Exception e) {
                e.printStackTrace();
                source.sendFailure(Component.literal(e.toString()));
            }
        }).start();
        return Command.SINGLE_SUCCESS;
    }

    /** Vanilla id this world's dimension maps to, or null when the dimension is not part of this world. */
    private static Identifier vanillaDimension(Identifier dimension) {
        if (dimension.equals(id)) return Identifier.parse("minecraft:overworld");
        if (dimension.equals(idNether)) return Identifier.parse("minecraft:the_nether");
        if (dimension.equals(idEnd)) return Identifier.parse("minecraft:the_end");
        return null;
    }

    /** Rebuilds a vanilla player file from the storage CombinedWorld keeps for this namespace. */
    private static CompoundTag buildPlayerTag(UUID uuid, StateSaver.PositionInfoNew posInfo, ServerLevel fallbackLevel, int dataVersion) throws CommandSyntaxException {
        ServerLevel spawnLevel = fallbackLevel;
        Identifier dimension = Identifier.parse("minecraft:overworld");
        if (posInfo != null) {
            Identifier vanilla = vanillaDimension(posInfo.dimension());
            if (vanilla != null) dimension = vanilla;
            ServerLevel maybe = server.getLevel(ResourceKey.create(Registries.DIMENSION, posInfo.dimension()));
            if (maybe != null) spawnLevel = maybe;
        }

        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(uuid, "exported"), false);
        ServerPlayer fake = new ServerPlayer(server, spawnLevel, cookie.gameProfile(), cookie.clientInformation());
        InventoryManager.loadInventory(fake, spawnLevel, stateSaver);
        if (posInfo != null) {
            fake.setPos(posInfo.pos().x, posInfo.pos().y, posInfo.pos().z);
            fake.setYRot(posInfo.yaw());
            fake.setXRot(posInfo.pitch());
        }
        ProblemReporter.ScopedCollector logging = new ProblemReporter.ScopedCollector(fake.problemPath(), LOGGER);
        TagValueOutput output = TagValueOutput.createWithContext(logging, fake.registryAccess());
        fake.saveWithoutId(output);
        CompoundTag playerTag = output.buildResult();
        playerTag.putString("Dimension", dimension.toString());
        playerTag.store("UUID", UUIDUtil.CODEC, uuid);
        playerTag.putInt("DataVersion", dataVersion);
        return playerTag;
    }
}
