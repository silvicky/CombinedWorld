package io.silvicky.item.command.world;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.serialization.Dynamic;
import io.silvicky.item.StateSaver;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.datafixer.Schemas;
import net.minecraft.entity.boss.dragon.EnderDragonFight;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;
import net.minecraft.util.WorldSavePath;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.dimension.DimensionTypes;
import net.minecraft.world.level.WorldGenSettings;
import net.minecraft.world.level.storage.LevelStorage;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Stream;

import static io.silvicky.item.InventoryManager.*;
import static io.silvicky.item.ItemStorage.LOGGER;
import static java.nio.file.Files.copy;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class ImportWorld {
    public static RegistryWrapper.WrapperLookup wrapper;
    public static HashMap<RegistryKey<DimensionOptions>,DimensionOptions> newDimensions=new HashMap<>();
    public static HashSet<RegistryKey<DimensionOptions>> deletedDimensions=new HashSet<>();
    private static StateSaver stateSaver;
    private static ArrayList<Identifier> identifiers;
    private static boolean firstType=true;
    private static Identifier id;
    public static final String DIMENSION_ID="dimension_id";
    public static final String DIMENSION_PATH="path";
    public static final String DATA="data";
    public static final String POI="poi";
    public static final String REGION="region";
    public static final String ENTITIES="entities";
    private static MinecraftServer server;
    public static final SimpleCommandExceptionType ERR_DIMENSION_EXIST=new SimpleCommandExceptionType(new LiteralMessage("A dimension with such ID already exists!"));
    public static final SimpleCommandExceptionType ERR_NAMESPACE_EXIST=new SimpleCommandExceptionType(new LiteralMessage("Currently we only accept new namespaces, otherwise collision happens."));
    public static final SimpleCommandExceptionType ERR_FOLDER_NOT_EXIST=new SimpleCommandExceptionType(new LiteralMessage("A folder with that path does not exist!"));
    public static final SimpleCommandExceptionType ERR_LEVEL_NOT_EXIST=new SimpleCommandExceptionType(new LiteralMessage("No level.dat was found!"));
    public static final SimpleCommandExceptionType ERR_FAIL_TO_READ_LEVEL=new SimpleCommandExceptionType(new LiteralMessage("Failed to read level.dat!"));
    public static final SimpleCommandExceptionType ERR_WORLD_GEN=new SimpleCommandExceptionType(new LiteralMessage("Failed to fetch WorldGenSettings!"));
    public static final SimpleCommandExceptionType ERR_PLAYER=new SimpleCommandExceptionType(new LiteralMessage("Failed to fetch player data!"));
    public static final SimpleCommandExceptionType ERR_SAVE=new SimpleCommandExceptionType(new LiteralMessage("Failed to copy save files!"));
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher)
    {
        dispatcher.register(
                literal("importworld")
                        .requires(source -> source.hasPermissionLevel(4))
                        .executes(context->help(context.getSource()))
                        .then(argument(DIMENSION_ID, IdentifierArgumentType.identifier())
                                .executes(context -> importWorld(context.getSource(), Paths.get(FabricLoader.getInstance().getGameDir().toString(),"imported"),IdentifierArgumentType.getIdentifier(context,DIMENSION_ID)))
                                .then(argument(DIMENSION_PATH, StringArgumentType.greedyString())
                                        .executes(context -> importWorld(context.getSource(), Paths.get(StringArgumentType.getString(context,DIMENSION_PATH)) ,IdentifierArgumentType.getIdentifier(context,DIMENSION_ID))))));
    }
    private static int help(ServerCommandSource source)
    {
        source.sendFeedback(()-> Text.literal("Usage: /importworld <id> [<path>]"),false);
        source.sendFeedback(()-> Text.literal("Import the world in <path>(default <game_root>/imported) and give it id of <id>."),false);
        source.sendFeedback(()-> Text.literal("Namespace of <id> mustn't be used in other dimensions to prevent collision."),false);
        source.sendFeedback(()-> Text.literal("If <id> ends with overworld/the_nether/the_end, world would be imported as a vanilla triplet."),false);
        source.sendFeedback(()-> Text.literal("Otherwise, world would be imported as a singlet and only overworld would be imported."),false);
        source.sendFeedback(()-> Text.literal("Currently only vanilla worlds are supported."),false);
        source.sendFeedback(()-> Text.literal("After importing, restart the whole game to apply changes."),false);
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
        stateSaver.posList.removeIf(positionInfo -> positionInfo.dimension.equals(id.toString()));
        stateSaver.nbtList.removeIf(storageInfo -> storageInfo.dimension.equals(id.getNamespace()));
        rollbackDragon();
    }
    public static void deleteFolder(Path path) throws IOException {
        if(!path.toFile().exists())return;
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
    public static void copyFolder(Path src, Path dest) throws IOException {
        try (Stream<Path> stream = Files.walk(src)) {
            stream.forEach(source -> {
                try {
                    copy(source, dest.resolve(src.relativize(source)),REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }
    private static void rollbackWorld()
    {
        Path target = server.getSavePath(WorldSavePath.ROOT).resolve("dimensions").resolve(id.getNamespace());
        try{deleteFolder(target);}
        catch (Exception e)
        {
            e.printStackTrace();
            LOGGER.warn("Failed to delete the copied save files, please check manually!");
        }
        rollbackPlayer();
    }
    private static void rollbackLevel()
    {
        newDimensions.entrySet().removeIf(entry -> identifiers.contains(entry.getKey().getValue()));
        rollbackWorld();
    }
    public static ServerPlayerEntity loadFakePlayer(NbtCompound compound, MinecraftServer server)
    {
        ConnectedClientData connectedClientData = ConnectedClientData.createDefault(new GameProfile(compound.get("UUID", Uuids.INT_STREAM_CODEC).orElseThrow(), "tmp"), false);
        ServerWorld world=server.getWorld(RegistryKey.of(RegistryKeys.WORLD,Identifier.of(compound.getString("Dimension",server.getOverworld().getRegistryKey().getValue().toString()))));
        if(world==null)world=server.getOverworld();
        ServerPlayerEntity serverPlayerEntity = new ServerPlayerEntity(
                server, world, connectedClientData.gameProfile(), connectedClientData.syncedOptions()
        );
        serverPlayerEntity.readNbt(compound);
        serverPlayerEntity.readGameModeNbt(compound);
        return serverPlayerEntity;
    }
    public static ServerPlayerEntity loadFakePlayer(Path path, MinecraftServer server) throws IOException
    {
        NbtCompound nbtCompound=NbtIo.readCompressed(path, NbtSizeTracker.ofUnlimitedBytes());
        nbtCompound= DataFixTypes.PLAYER.update(Schemas.getFixer(),nbtCompound, NbtHelper.getDataVersion(nbtCompound,-1));
        return loadFakePlayer(nbtCompound,server);
    }
    public static int importWorld(ServerCommandSource source, Path path, Identifier idTmp) throws CommandSyntaxException
    {
        if(firstType)
        {
            firstType=false;
            source.sendFeedback(()-> Text.literal("Hello, admin! This command can import a world(currently vanilla only), and although it has been tested, it is still strongly suggested that you backup your save first. Also you need to read the result carefully. Type this command without arguments to see the help. Type this command again if you already understand what you are doing."),false);
            return Command.SINGLE_SUCCESS;
        }
        server=source.getServer();
        stateSaver=StateSaver.getServerState(server);
        id=Identifier.of(getDimensionId(idTmp.toString()));
        for(ServerWorld i:server.getWorlds())
        {
            if(i.getRegistryKey().getValue().equals(id)) throw ERR_DIMENSION_EXIST.create();
        }
        for(RegistryKey<DimensionOptions> i:newDimensions.keySet())
        {
            if(i.getValue().equals(id)) throw ERR_DIMENSION_EXIST.create();
        }
        for(ServerWorld i:server.getWorlds())
        {
            if(i.getRegistryKey().getValue().getNamespace().equals(id.getNamespace())) throw ERR_NAMESPACE_EXIST.create();
        }
        for(RegistryKey<DimensionOptions> i:newDimensions.keySet())
        {
            if(i.getValue().getNamespace().equals(id.getNamespace())) throw ERR_NAMESPACE_EXIST.create();
        }
        identifiers=new ArrayList<>();
        identifiers.add(id);
        final boolean isSinglet= !id.getPath().endsWith(OVERWORLD);
        Identifier idNether=null;
        Identifier idEnd=null;
        if(!isSinglet)
        {
            String tmp1 = id.getPath().substring(0, id.getPath().length() - OVERWORLD.length());
            idNether = Identifier.of(id.getNamespace(), tmp1 + NETHER);
            idEnd = Identifier.of(id.getNamespace(), tmp1 + END);
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
        try{levelDynamic= LevelStorage.readLevelProperties(levelDat, Schemas.getFixer());}
        catch(Exception e)
        {
            e.printStackTrace();
            throw ERR_FAIL_TO_READ_LEVEL.create();
        }
        WorldGenSettings worldGenSettings;
        try
        {
            Dynamic<?> dynamic2= RegistryOps.withRegistry(levelDynamic,wrapper);
            worldGenSettings= WorldGenSettings.CODEC.parse(dynamic2.get("WorldGenSettings").orElseEmptyMap()).getOrThrow();
        }
        catch(Exception e)
        {
            e.printStackTrace();
            throw ERR_WORLD_GEN.create();
        }
        source.sendFeedback(()-> Text.literal("Fetched WorldGenSettings."),false);
        long seed= worldGenSettings.generatorOptions().getSeed();
        stateSaver.seed.put(id,seed);
        if(!isSinglet)
        {
            stateSaver.seed.put(idNether, seed);
            stateSaver.seed.put(idEnd, seed);
        }
        source.sendFeedback(()-> Text.literal("Seed configured."),false);
        try
        {
            EnderDragonFight.Data dragon=EnderDragonFight.Data.CODEC.parse(levelDynamic.get("DragonFight").orElseEmptyMap()).getOrThrow();
            if((!isSinglet)&&worldGenSettings.dimensionOptionsRegistryHolder().dimensions().get(DimensionOptions.END).dimensionTypeEntry().matchesKey(DimensionTypes.THE_END))
            {
                stateSaver.dragonFight.put(idEnd, dragon);
                source.sendFeedback(() -> Text.literal("Configured dragon fight."), false);
            }
        }
        catch(Exception e)
        {
            e.printStackTrace();
            source.sendFeedback(()-> Text.literal("Failed to fetch dragon fight... but not a big deal."),false);
        }
        Path playerData=path.resolve("playerdata");
        try
        {
            int cnt=0;
            for (File i : playerData.toFile().listFiles())
            {
                if (!i.getPath().endsWith(".dat")) continue;

                NbtCompound nbtCompound=NbtIo.readCompressed(i.toPath(), NbtSizeTracker.ofUnlimitedBytes());
                nbtCompound= DataFixTypes.PLAYER.update(Schemas.getFixer(),nbtCompound, NbtHelper.getDataVersion(nbtCompound,-1));
                ServerPlayerEntity serverPlayerEntity=loadFakePlayer(nbtCompound,server);
                String dimension=nbtCompound.getString("Dimension","minecraft:overworld");
                Identifier identifier=Identifier.of(dimension);
                if(!identifier.getNamespace().equals("minecraft"))continue;
                dimension=identifier.getPath();
                String fakeDimension=null;
                switch (dimension) {
                    case END -> {
                        if (!isSinglet) fakeDimension = idEnd.toString();
                    }
                    case NETHER -> {
                        if (!isSinglet) fakeDimension = idNether.toString();
                    }
                    case OVERWORLD -> fakeDimension = id.toString();
                }
                if(fakeDimension!=null)
                {
                    save(server,serverPlayerEntity,true,fakeDimension);
                    cnt++;
                }
            }
            int finalCnt = cnt;
            source.sendFeedback(()-> Text.literal("Fetched "+ finalCnt +" player data."),false);
        }
        catch(Exception e)
        {
            e.printStackTrace();
            rollbackPlayer();
            throw ERR_PLAYER.create();
        }
        source.sendFeedback(()-> Text.literal("We're'bout to copy the save files, you can go to have a rest now..."),false);
        try
        {
            Path target = server.getSavePath(WorldSavePath.ROOT).resolve("dimensions").resolve(id.getNamespace());
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
            source.sendFeedback(()-> Text.literal("Copied save files."),false);
        }
        catch(Exception e)
        {
            e.printStackTrace();
            rollbackWorld();
            throw ERR_SAVE.create();
        }
        for(Map.Entry<RegistryKey<DimensionOptions>,DimensionOptions> entry:worldGenSettings.dimensionOptionsRegistryHolder().dimensions().entrySet())
        {
            RegistryKey<DimensionOptions> registryKey=null;
            String imported=entry.getKey().getValue().getPath();
            switch (imported) {
                case OVERWORLD -> registryKey = RegistryKey.of(RegistryKeys.DIMENSION, id);
                case NETHER -> {if(!isSinglet)registryKey = RegistryKey.of(RegistryKeys.DIMENSION, idNether);}
                case END -> {if(!isSinglet)registryKey = RegistryKey.of(RegistryKeys.DIMENSION, idEnd);}
            }
            if(registryKey!=null)newDimensions.put(registryKey,entry.getValue());
        }
        source.sendFeedback(()-> Text.literal("Dimension options stored."),false);
        source.sendFeedback(()-> Text.literal("Now you can restart to apply all changes."),false);
        if(!source.getServer().isDedicated())source.sendFeedback(()-> Text.literal("DO NOT ENTER THIS WORLD AGAIN BEFORE RESTARTING YOUR GAME OR YOUR SAVE WOULD BE DESTROYED!!!"),false);
        return Command.SINGLE_SUCCESS;
    }
}

