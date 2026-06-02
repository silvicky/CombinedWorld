package io.silvicky.item.command.world;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Dynamic;
import io.silvicky.item.StateSaver;
import io.silvicky.item.common.Util;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldGenSettings;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static io.silvicky.item.command.world.ImportWorld.newDimensions;
import static io.silvicky.item.command.world.ImportWorld.wrapper;
import static io.silvicky.item.common.Util.*;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class CreateWorld
{
    private static boolean firstType=true;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher)
    {
        dispatcher.register(
                literal("createworld")
                        .requires(source -> source.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.OWNERS)))
                        .executes(context->help(context.getSource()))
                        .then(argument(DIMENSION_ID, IdentifierArgument.id())
                                .executes(context -> createWorld(context.getSource(), Paths.get(FabricLoader.getInstance().getGameDir().toString(),"imported","data","minecraft","world_gen_settings.dat"), IdentifierArgument.getId(context, DIMENSION_ID)))
                                .then(argument(DIMENSION_PATH, StringArgumentType.greedyString())
                                        .executes(context -> createWorld(context.getSource(), Paths.get(StringArgumentType.getString(context, DIMENSION_PATH)) , IdentifierArgument.getId(context, DIMENSION_ID))))));
    }
    private static int help(CommandSourceStack source)
    {
        source.sendSuccess(()-> Component.literal("Usage: /createworld <id> [<path>]"),false);
        source.sendSuccess(()-> Component.literal("Create world from <path>(default <game_root>/imported/data/minecraft/world_gen_settings.dat) and give it id of <id>."),false);
        source.sendSuccess(()-> Component.literal("If <id> ends with overworld/the_nether/the_end, world would be imported as a vanilla triplet."),false);
        source.sendSuccess(()-> Component.literal("Otherwise, world would be imported as a singlet and only overworld would be imported."),false);
        source.sendSuccess(()-> Component.literal("Currently only vanilla worlds of the same version are supported."),false);
        source.sendSuccess(()-> Component.literal("After importing, restart the whole game to apply changes."),false);
        return Command.SINGLE_SUCCESS;
    }
    private static int createWorld(CommandSourceStack source, Path worldGenDat, Identifier idTmp) throws CommandSyntaxException
    {
        if(firstType)
        {
            firstType=false;
            source.sendSuccess(()-> Component.literal("Hello, admin! This command can create a world(currently vanilla only), and although it has been tested, it is still strongly suggested that you backup your save first. Also you need to read the result carefully. Type this command without arguments to see the help. Type this command again if you already understand what you are doing."),false);
            return Command.SINGLE_SUCCESS;
        }
        MinecraftServer server = source.getServer();
        StateSaver stateSaver = StateSaver.getServerState(server);
        Identifier id = Util.getDimensionId(idTmp);
        for(ServerLevel i: server.getAllLevels())
        {
            if(i.dimension().identifier().equals(id)) throw ERR_DIMENSION_EXIST.create();
        }
        for(ResourceKey<LevelStem> i:newDimensions.keySet())
        {
            if(i.identifier().equals(id)) throw ERR_DIMENSION_EXIST.create();
        }
        final boolean isSinglet= !id.getPath().endsWith(OVERWORLD);
        Identifier idNether=null;
        Identifier idEnd=null;
        if(!isSinglet)
        {
            String tmp1 = id.getPath().substring(0, id.getPath().length() - OVERWORLD.length());
            idNether = Identifier.fromNamespaceAndPath(id.getNamespace(), tmp1 + NETHER);
            idEnd = Identifier.fromNamespaceAndPath(id.getNamespace(), tmp1 + END);
        }
        WorldGenSettings worldGenSettings;
        try
        {
            CompoundTag nbtCompound=NbtIo.readCompressed(worldGenDat, NbtAccounter.unlimitedHeap());
            nbtCompound= DataFixTypes.WORLD_GEN_SETTINGS.updateToCurrentVersion(DataFixers.getDataFixer(),nbtCompound, NbtUtils.getDataVersion(nbtCompound,-1));
            Dynamic<?> dynamic = new Dynamic<>(NbtOps.INSTANCE,nbtCompound);
            Dynamic<?> dynamic2= RegistryOps.injectRegistryContext(dynamic,wrapper);
            worldGenSettings= WorldGenSettings.CODEC.parse(dynamic2.get("data").orElseEmptyMap()).getOrThrow();
        }
        catch(Exception e)
        {
            e.printStackTrace();
            throw ERR_WORLD_GEN.create();
        }
        source.sendSuccess(()-> Component.literal("Fetched WorldGenSettings."),false);
        long seed= worldGenSettings.options().seed();
        stateSaver.seed.put(id,seed);
        if(!isSinglet)
        {
            stateSaver.seed.put(idNether, seed);
            stateSaver.seed.put(idEnd, seed);
        }
        source.sendSuccess(()-> Component.literal("Seed configured."),false);
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

