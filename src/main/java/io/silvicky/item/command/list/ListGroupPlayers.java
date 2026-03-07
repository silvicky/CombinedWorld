package io.silvicky.item.command.list;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.silvicky.item.command.suggestion.GroupSuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

import static io.silvicky.item.common.Util.NAMESPACE;
import static io.silvicky.item.common.Util.listToString;
import static java.lang.String.format;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class ListGroupPlayers {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher)
    {
        dispatcher.register(
                literal("listgroupplayers")
                        .executes(context->help(context.getSource()))
                                .then(argument(NAMESPACE, StringArgumentType.word())
                                        .suggests(new GroupSuggestionProvider())
                                        .executes(context -> listPlayers(context.getSource(),StringArgumentType.getString(context,NAMESPACE)))));
    }
    private static int help(CommandSourceStack source)
    {
        source.sendSuccess(()-> Component.literal("Usage: /listgroupplayers <namespace>"),false);
        source.sendSuccess(()-> Component.literal("Get players in the group."),false);
        return Command.SINGLE_SUCCESS;
    }
    private static int listPlayers(CommandSourceStack source, String group)
    {
        List<String> players=new ArrayList<>();
        for(ServerPlayer player:source.getServer().getPlayerList().getPlayers())
        {
            if(player.level().dimension().identifier().getNamespace().equals(group))
            {
                players.add(player.getName().getString());
            }
        }
        source.sendSuccess(()-> Component.literal(format("There are now %d players in %s: %s",players.size(),group,listToString(players))),false);
        return Command.SINGLE_SUCCESS;
    }
}
