package io.silvicky.item.command.world;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Dynamic;
import io.silvicky.item.StateSaver;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.storage.LevelStorageSource;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import static io.silvicky.item.InventoryManager.save;
import static io.silvicky.item.common.Util.*;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class ImportWorld {
    public static HolderLookup.Provider wrapper;
    public static HashMap<ResourceKey<LevelStem>, LevelStem> newDimensions=new HashMap<>();
    public static HashSet<ResourceKey<LevelStem>> deletedDimensions=new HashSet<>();
    private static StateSaver stateSaver;
    private static ArrayList<Identifier> identifiers;
    private static boolean firstType=true;
    private static Identifier id;
    private static MinecraftServer server;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher)
    {
        dispatcher.register(
                literal("importworld")
                        .requires(source -> source.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.OWNERS)))
                        .executes(context->help(context.getSource()))
                        .then(argument(DIMENSION_ID, IdentifierArgument.id())
                                .executes(context -> importWorld(context.getSource(), Paths.get(FabricLoader.getInstance().getGameDir().toString(),"imported"), IdentifierArgument.getId(context, DIMENSION_ID)))
                                .then(argument(DIMENSION_PATH, StringArgumentType.greedyString())
                                        .executes(context -> importWorld(context.getSource(), Paths.get(StringArgumentType.getString(context, DIMENSION_PATH)) , IdentifierArgument.getId(context, DIMENSION_ID))))));
    }
    private static int help(CommandSourceStack source)
    {
        source.sendSuccess(()-> Component.literal("Usage: /importworld <id> [<path>]"),false);
        source.sendSuccess(()-> Component.literal("Import the world in <path>(default <game_root>/imported) and give it id of <id>."),false);
        source.sendSuccess(()-> Component.literal("Namespace of <id> mustn't be used in other dimensions to prevent collision."),false);
        source.sendSuccess(()-> Component.literal("If <id> ends with overworld/the_nether/the_end, world would be imported as a vanilla triplet."),false);
        source.sendSuccess(()-> Component.literal("Otherwise, world would be imported as a singlet and only overworld would be imported."),false);
        source.sendSuccess(()-> Component.literal("Currently only vanilla worlds are supported."),false);
        source.sendSuccess(()-> Component.literal("After importing, restart the whole game to apply changes."),false);
        return Command.SINGLE_SUCCESS;
    }
    private static void rollbackSeed()
    {
        for(Identifier i:identifiers)stateSaver.seed.remove(i);
    }
    private static void rollbackDragon()
    {
        for(Identifier i:identifiers)stateSaver.dragonFight.remove(i);
        rollbackSeed();
    }
    private static void rollbackPlayer()
    {
        stateSaver.posMap.remove(id);
        stateSaver.savedMap.remove(id.getNamespace());
        rollbackDragon();
    }

    private static void rollbackWorld()
    {
        Path target = server.getWorldPath(LevelResource.ROOT).resolve("dimensions").resolve(id.getNamespace());
        try{
            deleteFolder(target);}
        catch (Exception e)
        {
            e.printStackTrace();
            LOGGER.warn("Failed to delete the copied save files, please check manually!");
        }
        rollbackPlayer();
    }

    private static int importWorld(CommandSourceStack source, Path path, Identifier idTmp) throws CommandSyntaxException
    {
        if(firstType)
        {
            firstType=false;
            source.sendSuccess(()-> Component.literal("Hello, admin! This command can import a world(currently vanilla only), and although it has been tested, it is still strongly suggested that you backup your save first. Also you need to read the result carefully. Type this command without arguments to see the help. Type this command again if you already understand what you are doing."),false);
            return Command.SINGLE_SUCCESS;
        }
        server=source.getServer();
        stateSaver=StateSaver.getServerState(server);
        id=getDimensionId(idTmp);
        for(ServerLevel i:server.getAllLevels())
        {
            if(i.dimension().identifier().equals(id)) throw ERR_DIMENSION_EXIST.create();
        }
        for(ResourceKey<LevelStem> i:newDimensions.keySet())
        {
            if(i.identifier().equals(id)) throw ERR_DIMENSION_EXIST.create();
        }
        for(ServerLevel i:server.getAllLevels())
        {
            if(i.dimension().identifier().getNamespace().equals(id.getNamespace())) throw ERR_NAMESPACE_EXIST.create();
        }
        for(ResourceKey<LevelStem> i:newDimensions.keySet())
        {
            if(i.identifier().getNamespace().equals(id.getNamespace())) throw ERR_NAMESPACE_EXIST.create();
        }
        identifiers=new ArrayList<>();
        identifiers.add(id);
        final boolean isSinglet= !id.getPath().endsWith(OVERWORLD);
        Identifier idNether=null;
        Identifier idEnd=null;
        if(!isSinglet)
        {
            String tmp1 = id.getPath().substring(0, id.getPath().length() - OVERWORLD.length());
            idNether = Identifier.fromNamespaceAndPath(id.getNamespace(), tmp1 + NETHER);
            idEnd = Identifier.fromNamespaceAndPath(id.getNamespace(), tmp1 + END);
            identifiers.add(idNether);
            identifiers.add(idEnd);
        }
        if(!(path.toFile().exists()&&path.toFile().isDirectory())) throw ERR_FOLDER_NOT_EXIST.create();
        Path levelDat=null;
        for(File i:path.toFile().listFiles())
        {
            if(i.getPath().endsWith("level.dat"))
            {
                levelDat=i.toPath();
                break;
            }
        }
        if(levelDat==null) throw ERR_LEVEL_NOT_EXIST.create();
        Dynamic<?> levelDynamic;
        try{levelDynamic= LevelStorageSource.readLevelDataTagFixed(levelDat, DataFixers.getDataFixer());}
        catch(Exception e)
        {
            e.printStackTrace();
            throw ERR_FAIL_TO_READ_LEVEL.create();
        }
        int gamemode=0;
        try
        {
            gamemode=levelDynamic.get("GameType").asInt(0);
        }
        catch(Exception ignored){}
        WorldGenSettings worldGenSettings;
        try
        {
            Dynamic<?> dynamic2= RegistryOps.injectRegistryContext(levelDynamic,wrapper);
            worldGenSettings= WorldGenSettings.CODEC.parse(dynamic2.get("WorldGenSettings").orElseEmptyMap()).getOrThrow();
        }
        catch(Exception e)
        {
            e.printStackTrace();
            throw ERR_WORLD_GEN.create();
        }
        source.sendSuccess(()-> Component.literal("Fetched WorldGenSettings."),false);
        long seed= worldGenSettings.options().seed();
        stateSaver.seed.put(id,seed);
        stateSaver.gamemode.put(id.getNamespace(),gamemode);
        if(!isSinglet)
        {
            stateSaver.seed.put(idNether, seed);
            stateSaver.seed.put(idEnd, seed);
        }
        source.sendSuccess(()-> Component.literal("Seed configured."),false);
        try
        {
            EndDragonFight.Data dragon= EndDragonFight.Data.CODEC.parse(levelDynamic.get("DragonFight").orElseEmptyMap()).getOrThrow();
            if((!isSinglet)&&worldGenSettings.dimensions().dimensions().get(LevelStem.END).type().is(BuiltinDimensionTypes.END))
            {
                stateSaver.dragonFight.put(idEnd, dragon);
                source.sendSuccess(() -> Component.literal("Configured dragon fight."), false);
            }
        }
        catch(Exception e)
        {
            e.printStackTrace();
            source.sendSuccess(()-> Component.literal("Failed to fetch dragon fight... but not a big deal."),false);
        }
        try
        {
            LevelData.RespawnData spawn= LevelData.RespawnData.CODEC.parse(levelDynamic.get("spawn").orElseEmptyMap()).getOrThrow();
            String spawnWorldPath=spawn.dimension().identifier().getPath();
            Identifier spawnWorld;
            if(spawnWorldPath.endsWith(END))spawnWorld=idEnd;
            else if(spawnWorldPath.endsWith(NETHER))spawnWorld=idNether;
            else spawnWorld=id;
            stateSaver.worldSpawn.put(id, LevelData.RespawnData.of(ResourceKey.create(Registries.DIMENSION,spawnWorld),spawn.pos(),spawn.yaw(),spawn.pitch()));
            source.sendSuccess(() -> Component.literal("Configured spawn point."), false);
        }
        catch(Exception e)
        {
            e.printStackTrace();
            source.sendSuccess(()-> Component.literal("Failed to fetch spawn point... but not a big deal."),false);
        }
        Path playerData=path.resolve("playerdata");
        try
        {
            int cnt=0;
            for (File i : playerData.toFile().listFiles())
            {
                if (!i.getPath().endsWith(".dat")) continue;

                CompoundTag nbtCompound=NbtIo.readCompressed(i.toPath(), NbtAccounter.unlimitedHeap());
                nbtCompound= DataFixTypes.PLAYER.updateToCurrentVersion(DataFixers.getDataFixer(),nbtCompound, NbtUtils.getDataVersion(nbtCompound,-1));
                ServerPlayer serverPlayerEntity= loadFakePlayer(nbtCompound,server);
                String dimension=nbtCompound.getStringOr("Dimension","minecraft:overworld");
                Identifier identifier= Identifier.parse(dimension);
                if(!identifier.getNamespace().equals("minecraft"))continue;
                dimension=identifier.getPath();
                Identifier fakeDimension=null;
                switch (dimension) {
                    case END -> {
                        if (!isSinglet) fakeDimension = idEnd;
                    }
                    case NETHER -> {
                        if (!isSinglet) fakeDimension = idNether;
                    }
                    case OVERWORLD -> fakeDimension = id;
                }
                if(fakeDimension!=null)
                {
                    save(server,serverPlayerEntity,fakeDimension);
                    cnt++;
                }
            }
            int finalCnt = cnt;
            source.sendSuccess(()-> Component.literal("Fetched "+ finalCnt +" player data."),false);
        }
        catch(Exception e)
        {
            e.printStackTrace();
            rollbackPlayer();
            throw ERR_PLAYER.create();
        }
        source.sendSuccess(()-> Component.literal("We're'bout to copy the save files, you can go to have a rest now..."),false);
        try
        {
            Path target = server.getWorldPath(LevelResource.ROOT).resolve("dimensions").resolve(id.getNamespace());
            deleteFolder(target);
            target.toFile().mkdirs();
            Path targetOverworld=target.resolve(id.getPath());
            if(!isSinglet)
            {
                Path targetNether=target.resolve(idNether.getPath());
                Path targetEnd=target.resolve(idEnd.getPath());
                Path sourceNether=path.resolve("DIM-1");
                Path sourceEnd=path.resolve("DIM1");
                copyFolder(sourceNether,targetNether);
                copyFolder(sourceEnd,targetEnd);
            }
            targetOverworld.toFile().mkdirs();
            copyFolder(path.resolve(DATA),targetOverworld.resolve(DATA));
            copyFolder(path.resolve(POI),targetOverworld.resolve(POI));
            copyFolder(path.resolve(ENTITIES),targetOverworld.resolve(ENTITIES));
            copyFolder(path.resolve(REGION),targetOverworld.resolve(REGION));
            source.sendSuccess(()-> Component.literal("Copied save files."),false);
        }
        catch(Exception e)
        {
            e.printStackTrace();
            rollbackWorld();
            throw ERR_SAVE.create();
        }
        for(Map.Entry<ResourceKey<LevelStem>, LevelStem> entry:worldGenSettings.dimensions().dimensions().entrySet())
        {
            ResourceKey<LevelStem> registryKey=null;
            String imported=entry.getKey().identifier().getPath();
            switch (imported) {
                case OVERWORLD -> registryKey = ResourceKey.create(Registries.LEVEL_STEM, id);
                case NETHER -> {if(!isSinglet)registryKey = ResourceKey.create(Registries.LEVEL_STEM, idNether);}
                case END -> {if(!isSinglet)registryKey = ResourceKey.create(Registries.LEVEL_STEM, idEnd);}
            }
            if(registryKey!=null)newDimensions.put(registryKey,entry.getValue());
        }
        source.sendSuccess(()-> Component.literal("Dimension options stored."),false);
        source.sendSuccess(()-> Component.literal("Now you can restart to apply all changes."),false);
        if(!source.getServer().isDedicatedServer())source.sendSuccess(()-> Component.literal("DO NOT ENTER THIS WORLD AGAIN BEFORE RESTARTING YOUR GAME OR YOUR SAVE WOULD BE DESTROYED!!!"),false);
        return Command.SINGLE_SUCCESS;
    }
}

