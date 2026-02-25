package io.silvicky.item.command.list;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.silvicky.item.command.suggestion.GroupSuggestionProvider;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

import static io.silvicky.item.common.Util.NAMESPACE;
import static io.silvicky.item.common.Util.listToString;
import static java.lang.String.format;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class ListGroupPlayers {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher)
    {
        dispatcher.register(
                literal("listgroupplayers")
                        .executes(context->help(context.getSource()))
                                .then(argument(NAMESPACE, StringArgumentType.word())
                                        .suggests(new GroupSuggestionProvider())
                                        .executes(context -> listPlayers(context.getSource(),StringArgumentType.getString(context,NAMESPACE)))));
    }
    private static int help(ServerCommandSource source)
    {
        source.sendFeedback(()-> Text.literal("Usage: /listgroupplayers <namespace>"),false);
        source.sendFeedback(()-> Text.literal("Get players in the group."),false);
        return Command.SINGLE_SUCCESS;
    }
    private static int listPlayers(ServerCommandSource source, String group)
    {
        List<String> players=new ArrayList<>();
        for(ServerPlayerEntity player:source.getServer().getPlayerManager().getPlayerList())
        {
            if(player.getEntityWorld().getRegistryKey().getValue().getNamespace().equals(group))
            {
                players.add(player.getName().getString());
            }
        }
        source.sendFeedback(()-> Text.literal(format("There are now %d players in %s: %s",players.size(),group,listToString(players))),false);
        return Command.SINGLE_SUCCESS;
    }
}
