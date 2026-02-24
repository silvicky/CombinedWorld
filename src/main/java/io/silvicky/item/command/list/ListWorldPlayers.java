package io.silvicky.item.command.list;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import io.silvicky.item.command.suggestion.WorldSuggestionProvider;
import net.minecraft.command.argument.DimensionArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

import java.util.List;

import static io.silvicky.item.common.Util.*;
import static java.lang.String.format;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class ListWorldPlayers {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher)
    {
        dispatcher.register(
                literal("listworldplayers")
                        .executes(context->help(context.getSource()))
                                .then(argument(DIMENSION, DimensionArgumentType.dimension())
                                        .suggests(new WorldSuggestionProvider())
                                        .executes(context -> listPlayers(context.getSource(),DimensionArgumentType.getDimensionArgument(context,DIMENSION)))));
    }
    private static int help(ServerCommandSource source)
    {
        source.sendFeedback(()-> Text.literal("Usage: /listworldplayers <dimension>"),false);
        source.sendFeedback(()-> Text.literal("Get players in the world of that dimension."),false);
        return Command.SINGLE_SUCCESS;
    }

    public static int listPlayers(ServerCommandSource source, ServerWorld dimension)
    {
        List<String> players= getListOfPlayers(source.getServer(),getDimensionId(dimension));
        source.sendFeedback(()-> Text.literal(format("There are now %d players in the world of %s: %s",players.size(),getDimensionId(dimension),listToString(players))),false);
        return Command.SINGLE_SUCCESS;
    }
}
