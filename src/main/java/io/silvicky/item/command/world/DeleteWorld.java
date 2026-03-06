package io.silvicky.item.command.world;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.silvicky.item.StateSaver;
import io.silvicky.item.command.suggestion.WorldSuggestionProvider;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;
import java.util.*;

import static io.silvicky.item.command.warp.BanWarp.banWarp;
import static io.silvicky.item.command.warp.Evacuate.evacuate;
import static io.silvicky.item.command.world.ImportWorld.*;
import static io.silvicky.item.common.Util.*;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;
public class DeleteWorld {
    private static Identifier id;
    private static boolean firstType=true;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher)
    {
        dispatcher.register(
                literal("deleteworld")
                        .requires(source -> source.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.OWNERS)))
                        .executes(context->help(context.getSource()))
                        .then(argument(DIMENSION_ID, IdentifierArgument.id())
                                .suggests(new WorldSuggestionProvider())
                                .then(argument(TARGET, DimensionArgument.dimension())
                                        .suggests(new WorldSuggestionProvider())
                                        .executes(context -> deleteWorld(context.getSource(), IdentifierArgument.getId(context, DIMENSION_ID), DimensionArgument.getDimension(context,TARGET))))));
    }
    private static int help(CommandSourceStack source)
    {
        source.sendSuccess(()-> Component.literal("Usage: /deleteworld <id> <target>"),false);
        source.sendSuccess(()-> Component.literal("Delete a world."),false);
        source.sendSuccess(()-> Component.literal("If <id> is a member of a triplet, all the triplet would be deleted."),false);
        source.sendSuccess(()-> Component.literal("Any player (even offline) would be evacuated into <target>."),false);
        source.sendSuccess(()-> Component.literal("During the first stage, no actual change would be done until restart, when the dimension entries are deleted."),false);
        source.sendSuccess(()-> Component.literal("During the second stage, actual change would be done and no restart is needed."),false);
        return Command.SINGLE_SUCCESS;
    }
    public static boolean notifyEvacuation(CommandSourceStack source, Identifier id)
    {
        List<String> players=getListOfPlayers(source.getServer(), id);
        if(!players.isEmpty())
        {
            source.sendSuccess(()-> Component.literal("Some players are still in that world:"),false);
            source.sendSuccess(()-> Component.literal(listToString(players)),false);
            source.sendSuccess(()-> Component.literal("Please evacuate them, otherwise undefined behavior might be observed."),false);
            return true;
        }
        else return false;
    }
    private static int deleteWorld(CommandSourceStack source, Identifier idTmp, ServerLevel safeZone) throws CommandSyntaxException
    {
        if(firstType)
        {
            firstType=false;
            source.sendSuccess(()-> Component.literal("Hello, admin! This command can delete a world. The world will be lost forever(a long time). It is still strongly suggested that you backup your save first. Also you need to read the result carefully. Type this command without arguments to see the help. Type this command again if you already understand what you are doing."),false);
            return Command.SINGLE_SUCCESS;
        }
        id=getDimensionId(idTmp);
        if(id.getNamespace().equals("minecraft"))
        {
            source.sendSuccess(()-> Component.literal("how dare you..."),false);
            return Command.SINGLE_SUCCESS;
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
        MinecraftServer server=source.getServer();
        StateSaver stateSaver = StateSaver.getServerState(server);
        ServerLevel src=server.getLevel(ResourceKey.create(Registries.DIMENSION,id));
        if(src!=null)
        {
            if(getDimensionId(id).equals(getDimensionId(safeZone)))
            {
                source.sendSuccess(()-> Component.literal("Target dimension cannot be the same as the one to be deleted."),false);
                return Command.SINGLE_SUCCESS;
            }
            banWarp(source,src,StateSaver.WarpRestrictionInfo.INFINITE,"To be deleted.",true);
            evacuate(source,src,safeZone,false);
            deletedDimensions.add(ResourceKey.create(Registries.LEVEL_STEM,id));
            if(!isSinglet)
            {
                deletedDimensions.add(ResourceKey.create(Registries.LEVEL_STEM,idNether));
                deletedDimensions.add(ResourceKey.create(Registries.LEVEL_STEM,idEnd));
            }
            source.sendSuccess(()-> Component.literal("Dimension options deleted."),false);
            source.sendSuccess(()-> Component.literal("First stage is done. Restart the game and type the same command again to continue."),false);
            if(!source.getServer().isDedicatedServer())source.sendSuccess(()-> Component.literal("DO NOT ENTER THIS WORLD AGAIN BEFORE RESTARTING YOUR GAME OR YOUR SAVE WOULD BE DESTROYED!!!"),false);
            return Command.SINGLE_SUCCESS;
        }
        try
        {
            stateSaver.seed.remove(id);
            stateSaver.restrictionInfoHashMap.remove(id);
            if (!isSinglet) {
                stateSaver.seed.remove(idNether);
                stateSaver.seed.remove(idEnd);
                stateSaver.restrictionInfoHashMap.remove(idNether);
                stateSaver.restrictionInfoHashMap.remove(idEnd);
            }
            source.sendSuccess(() -> Component.literal("Seeds deleted."), false);
        }
        catch(Exception e)
        {
            e.printStackTrace();
            source.sendSuccess(()-> Component.literal("ERR: Failed to delete seed! Please manually delete them."),false);
        }
        try
        {
            if(!isSinglet)
            {
                if(stateSaver.dragonFight.remove(idEnd)!=null)
                {
                    source.sendSuccess(()-> Component.literal("Dragon fight deleted."),false);
                }
            }
        }
        catch(Exception e)
        {
            e.printStackTrace();
            source.sendSuccess(()-> Component.literal("ERR: Failed to delete dragon fight! Please manually delete them."),false);
        }
        try
        {
            Set<String> removedPlayers = stateSaver.posMap.getOrDefault(id,new HashMap<>()).keySet();
            int cnt = removedPlayers.size();
            for(String i:removedPlayers)stateSaver.savedMap.getOrDefault(id.getNamespace(),new HashMap<>()).remove(i);
            source.sendSuccess(() -> Component.literal("Deleted " + cnt + " player data."), false);
        }
        catch(Exception e)
        {
            e.printStackTrace();
            source.sendSuccess(()-> Component.literal("ERR: Failed to delete player data! Please manually delete them."),false);
        }
        try
        {
            Path target = server.getWorldPath(LevelResource.ROOT).resolve("dimensions").resolve(id.getNamespace());
            Path targetOverworld=target.resolve(id.getPath());
            deleteFolder(targetOverworld);
            if(!isSinglet)
            {
                Path targetNether=target.resolve(idNether.getPath());
                Path targetEnd=target.resolve(idEnd.getPath());
                deleteFolder(targetNether);
                deleteFolder(targetEnd);
            }
            target.toFile().delete();
            source.sendSuccess(()-> Component.literal("Deleted save files."),false);
        }
        catch(Exception e)
        {
            e.printStackTrace();
            source.sendSuccess(()-> Component.literal("ERR: Failed to delete save files! Please manually delete them."),false);
        }
        source.sendSuccess(()-> Component.literal("Done."),false);
        return Command.SINGLE_SUCCESS;
    }
}
