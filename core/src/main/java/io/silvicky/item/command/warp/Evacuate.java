package io.silvicky.item.command.warp;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.silvicky.item.command.suggestion.WorldSuggestionProvider;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;

import java.io.File;
import java.nio.file.Path;

import static io.silvicky.item.command.warp.Warp.warp;
import static io.silvicky.item.common.Util.*;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class Evacuate
{
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher)
    {
        dispatcher.register(
                literal("evacuate")
                        .requires(ctx-> ctx.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS)))
                                .executes(context->help(context.getSource()))
                                        .then(argument(DIMENSION, DimensionArgument.dimension())
                                                .suggests(new WorldSuggestionProvider())
                                                .then(argument(TARGET, DimensionArgument.dimension())
                                                        .suggests(new WorldSuggestionProvider())
                                                        .then(literal("online")
                                                                .executes(context -> evacuate(context.getSource(), DimensionArgument.getDimension(context,DIMENSION), DimensionArgument.getDimension(context,TARGET),true)))
                                                        .then(literal("all")
                                                                .executes(context -> evacuate(context.getSource(), DimensionArgument.getDimension(context,DIMENSION), DimensionArgument.getDimension(context,TARGET),false))))));
    }
    private static int help(CommandSourceStack source)
    {
        source.sendSuccess(()-> Component.literal("Usage: /evacuate <dimension> <target> (online|all)"),false);
        source.sendSuccess(()-> Component.literal("Warp all players in world of <dimension> into <target>"),false);
        return Command.SINGLE_SUCCESS;
    }
    public static int evacuate(CommandSourceStack source, ServerLevel src, ServerLevel dest, boolean online) throws CommandSyntaxException
    {
        if(getDimensionId(dest).equals(getDimensionId(src)))
        {
            source.sendSuccess(()-> Component.literal("Nothing happened."),false);
            return Command.SINGLE_SUCCESS;
        }
        int cntOnline=0;
        int cntOffline=0;
        int cntFailed=0;
        for(ServerPlayer player:source.getServer().getPlayerList().getPlayers())
        {
            if(getDimensionId(player.level()).equals(getDimensionId(src)))
            {
                warp(source,player,dest,true);
                cntOnline++;
            }
        }
        if(!online)
        {
            Path playerData=source.getServer().getWorldPath(LevelResource.PLAYER_DATA_DIR);
            for(File i:playerData.toFile().listFiles())
            {
                if(i.getPath().endsWith("_old"))continue;
                try
                {
                    ServerPlayer player=loadFakePlayer(i.toPath(),source.getServer());
                    if(source.getServer().getPlayerList().getPlayer(player.getUUID())!=null)continue;
                    if(getDimensionId(player.level()).equals(getDimensionId(src)))
                    {
                        warp(source,player,dest,true);
                        source.getServer().playerDataStorage.save(player);
                        cntOffline++;
                    }
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                    cntFailed++;
                }
            }
        }
        int finalCnt = cntOnline+cntOffline;
        int finalCntOnline = cntOnline;
        int finalCntOffline=cntOffline;
        int finalCntFailed=cntFailed;
        source.sendSuccess(()-> Component.literal(String.format("Evacuated %d players, %d online, %d offline. %d Failed.",finalCnt,finalCntOnline,finalCntOffline,finalCntFailed)),false);
        return Command.SINGLE_SUCCESS;
    }
}
