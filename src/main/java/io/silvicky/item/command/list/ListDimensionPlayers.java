package io.silvicky.item.command.list;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.command.argument.DimensionArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

import static io.silvicky.item.common.Util.*;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class ListDimensionPlayers {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher)
    {
        dispatcher.register(
                literal("listdimensionplayers")
                        .executes(context->help(context.getSource()))
                                .then(argument(DIMENSION, DimensionArgumentType.dimension())
                                        .executes(context -> listPlayers(context.getSource(),DimensionArgumentType.getDimensionArgument(context,DIMENSION)))));
    }
    private static int help(ServerCommandSource source)
    {
        source.sendFeedback(()-> Text.literal("Usage: /listdimensionplayers <dimension>"),false);
        source.sendFeedback(()-> Text.literal("Get players in that dimension."),false);
        return Command.SINGLE_SUCCESS;
    }
    public static int listPlayers(ServerCommandSource source, ServerWorld dimension)
    {
        List<String> players=new ArrayList<>();
        for(ServerPlayerEntity player:source.getServer().getPlayerManager().getPlayerList())
        {
            if(player.
                    getServerWorld().getRegistryKey().getValue().equals(dimension.getRegistryKey().getValue()))
            {
                players.add(player.getName().getString());
            }
        }
        source.sendFeedback(()-> Text.literal("There are now "+ players.size() +" players in "+dimension.getRegistryKey().getValue()+" : "+listToString(players)),false);
        return Command.SINGLE_SUCCESS;
    }
}
