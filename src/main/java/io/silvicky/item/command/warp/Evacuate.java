package io.silvicky.item.command.warp;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.argument.DimensionArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;
import net.minecraft.world.PlayerSaveHandler;

import java.io.File;
import java.nio.file.Path;

import static io.silvicky.item.common.Util.*;
import static io.silvicky.item.command.warp.Warp.warp;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class Evacuate
{
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher)
    {
        dispatcher.register(
                literal("evacuate")
                        .requires(ctx-> ctx.hasPermissionLevel(2))
                                .executes(context->help(context.getSource()))
                                .then(literal("online")
                                        .then(argument(DIMENSION, DimensionArgumentType.dimension())
                                                .then(argument(TARGET, DimensionArgumentType.dimension())
                                                        .executes(context -> evacuate(context.getSource(),DimensionArgumentType.getDimensionArgument(context,DIMENSION),DimensionArgumentType.getDimensionArgument(context,TARGET),true)))))
                                .then(literal("all")
                                        .then(argument(DIMENSION, DimensionArgumentType.dimension())
                                                .then(argument(TARGET, DimensionArgumentType.dimension())
                                                        .executes(context -> evacuate(context.getSource(),DimensionArgumentType.getDimensionArgument(context,DIMENSION),DimensionArgumentType.getDimensionArgument(context,TARGET),false))))));
    }
    private static int help(ServerCommandSource source)
    {
        source.sendFeedback(()-> Text.literal("Usage: /evacuate (online|all) <dimension> <target>"),false);
        source.sendFeedback(()-> Text.literal("Warp all players in world of <dimension> into <target>"),false);
        return Command.SINGLE_SUCCESS;
    }
    public static int evacuate(ServerCommandSource source, ServerWorld src, ServerWorld dest, boolean online) throws CommandSyntaxException
    {
        if(getDimensionId(dest).equals(getDimensionId(src)))
        {
            source.sendFeedback(()->Text.literal("Nothing happened."),false);
            return Command.SINGLE_SUCCESS;
        }
        int cntOnline=0;
        int cntOffline=0;
        for(ServerPlayerEntity player:source.getServer().getPlayerManager().getPlayerList())
        {
            if(getDimensionId(player.getWorld()).equals(getDimensionId(src)))
            {
                warp(source,player,dest,true);
                cntOnline++;
            }
        }
        if(!online)
        {
            Path playerData=source.getServer().getSavePath(WorldSavePath.PLAYERDATA);
            for(File i:playerData.toFile().listFiles())
            {
                if(i.getPath().endsWith("_old"))continue;
                try
                {
                    ServerPlayerEntity player=loadFakePlayer(i.toPath(),source.getServer());
                    if(source.getServer().getPlayerManager().getPlayer(player.getUuid())!=null)continue;
                    if(getDimensionId(player.getWorld()).equals(getDimensionId(src)))
                    {
                        warp(source,player,dest,true);
                        source.getServer().saveHandler.savePlayerData(player);
                        cntOffline++;
                    }
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            }
        }
        int finalCnt = cntOnline+cntOffline;
        int finalCntOnline = cntOnline;
        int finalCntOffline=cntOffline;
        source.sendFeedback(()->Text.literal(String.format("Evacuated %d players, %d online, %d offline.",finalCnt,finalCntOnline,finalCntOffline)),false);
        return Command.SINGLE_SUCCESS;
    }
}
