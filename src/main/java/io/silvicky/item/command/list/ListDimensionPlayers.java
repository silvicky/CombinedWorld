package io.silvicky.item.command.list;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

import static io.silvicky.item.common.Util.*;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class ListDimensionPlayers {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher)
    {
        dispatcher.register(
                literal("listdimensionplayers")
                        .executes(context->help(context.getSource()))
                                .then(argument(DIMENSION, DimensionArgument.dimension())
                                        .executes(context -> listPlayers(context.getSource(), DimensionArgument.getDimension(context,DIMENSION)))));
    }
    private static int help(CommandSourceStack source)
    {
        source.sendSuccess(()-> Component.literal("Usage: /listdimensionplayers <dimension>"),false);
        source.sendSuccess(()-> Component.literal("Get players in that dimension."),false);
        return Command.SINGLE_SUCCESS;
    }
    private static int listPlayers(CommandSourceStack source, ServerLevel dimension)
    {
        List<String> players=new ArrayList<>();
        for(ServerPlayer player:source.getServer().getPlayerList().getPlayers())
        {
            if(player.
                    level().dimension().identifier().equals(dimension.dimension().identifier()))
            {
                players.add(player.getName().getString());
            }
        }
        source.sendSuccess(()-> Component.literal("There are now "+ players.size() +" players in "+dimension.dimension().identifier()+" : "+listToString(players)),false);
        return Command.SINGLE_SUCCESS;
    }
}
