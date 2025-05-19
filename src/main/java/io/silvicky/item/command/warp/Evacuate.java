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

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static io.silvicky.item.InventoryManager.DIMENSION;
import static io.silvicky.item.InventoryManager.getDimensionId;
import static io.silvicky.item.command.warp.Warp.warp;
import static io.silvicky.item.command.warp.WarpTp.TARGET;
import static io.silvicky.item.command.world.ImportWorld.loadFakePlayer;
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
        int cnt=0;
        for(ServerPlayerEntity player:source.getServer().getPlayerManager().getPlayerList())
        {
            if(getDimensionId(player.getServerWorld()).equals(getDimensionId(src)))
            {
                warp(source,player,dest,true);
                cnt++;
            }
        }
        if(!online)
        {
            Path playerData=source.getServer().getSavePath(WorldSavePath.PLAYERDATA);
            for(File i:playerData.toFile().listFiles())
            {
                try
                {
                    ServerPlayerEntity player=loadFakePlayer(i.toPath());
                    //TODO
                }
                catch (IOException e)
                {
                    throw new RuntimeException(e);
                }
            }

        }
        int finalCnt = cnt;
        source.sendFeedback(()->Text.literal("Evacuated "+ finalCnt +" players."),false);
        return Command.SINGLE_SUCCESS;
    }
}
