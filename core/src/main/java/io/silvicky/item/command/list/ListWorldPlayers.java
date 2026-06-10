package io.silvicky.item.command.list;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import io.silvicky.item.command.suggestion.WorldSuggestionProvider;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;

import java.util.List;

import static io.silvicky.item.common.Util.*;
import static java.lang.String.format;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class ListWorldPlayers {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher)
    {
        dispatcher.register(
                literal("listworldplayers")
                        .executes(context->help(context.getSource()))
                                .then(argument(DIMENSION, DimensionArgument.dimension())
                                        .suggests(new WorldSuggestionProvider())
                                        .executes(context -> listPlayers(context.getSource(), DimensionArgument.getDimension(context,DIMENSION)))));
    }
    private static int help(CommandSourceStack source)
    {
        source.sendSuccess(()-> Component.literal("Usage: /listworldplayers <dimension>"),false);
        source.sendSuccess(()-> Component.literal("Get players in the world of that dimension."),false);
        return Command.SINGLE_SUCCESS;
    }

    private static int listPlayers(CommandSourceStack source, ServerLevel dimension)
    {
        List<String> players= getListOfPlayers(source.getServer(),getDimensionId(dimension));
        source.sendSuccess(()-> Component.literal(format("There are now %d players in the world of %s: %s",players.size(),getDimensionId(dimension),listToString(players))),false);
        return Command.SINGLE_SUCCESS;
    }
}
