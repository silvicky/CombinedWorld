package io.silvicky.item.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.command.argument.DimensionArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

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
                        .then(argument(DIMENSION, DimensionArgumentType.dimension())
                                .executes(context -> listPlayers(context.getSource(),DimensionArgumentType.getDimensionArgument(context,DIMENSION)))));
    }
    public static int listPlayers(ServerCommandSource source, ServerWorld dimension)
    {
        int cnt=0;
        StringBuilder tot= new StringBuilder();
        List<ServerPlayerEntity> players=source.getServer().getPlayerManager().getPlayerList();
        for(ServerPlayerEntity player:players)
        {
            if(getDimensionId(player.getServerWorld()).equals(getDimensionId(dimension)))
            {
                cnt++;
                if(cnt!=1)tot.append(", ");
                tot.append(player.getName().getString());
            }
        }
        int finalCnt = cnt;
        source.sendFeedback(()-> Text.literal("There are now "+ finalCnt +" players in the world of "+getDimensionId(dimension)+" : "+tot),false);
        return Command.SINGLE_SUCCESS;
    }
}
