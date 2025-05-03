package io.silvicky.item.command.list;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.command.argument.DimensionArgumentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

import static io.silvicky.item.InventoryManager.DIMENSION;
import static io.silvicky.item.InventoryManager.getDimensionId;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class ListWorldPlayers {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher)
    {
        dispatcher.register(
                literal("listworldplayers")
                        .executes(context->help(context.getSource()))
                                .then(argument(DIMENSION, DimensionArgumentType.dimension())
                                        .executes(context -> listPlayers(context.getSource(),DimensionArgumentType.getDimensionArgument(context,DIMENSION)))));
    }
    private static int help(ServerCommandSource source)
    {
        source.sendFeedback(()-> Text.literal("Usage: /listworldplayers <dimension>"),false);
        source.sendFeedback(()-> Text.literal("Get players in the world of that dimension."),false);
        return Command.SINGLE_SUCCESS;
    }
    public static List<String> getListOfPlayers(MinecraftServer server, String dimension)
    {
        List<ServerPlayerEntity> players=server.getPlayerManager().getPlayerList();
        ArrayList<String> ret=new ArrayList<>();
        for(ServerPlayerEntity player:players)
        {
            if(getDimensionId(player.getServerWorld()).equals(dimension))
            {
                ret.add(player.getName().getString());
            }
        }
        return ret;
    }
    public static <T> String listToString(List<T> list)
    {
        StringBuilder tot= new StringBuilder();
        boolean first=true;
        for(T t:list)
        {
            if(!first)tot.append(", ");
            tot.append(t.toString());
        }
        return tot.toString();
    }
    public static int listPlayers(ServerCommandSource source, ServerWorld dimension)
    {
        List<String> players=getListOfPlayers(source.getServer(),getDimensionId(dimension));
        source.sendFeedback(()-> Text.literal("There are now "+ players.size() +" players in the world of "+getDimensionId(dimension)+" : "+listToString(players)),false);
        return Command.SINGLE_SUCCESS;
    }
}
