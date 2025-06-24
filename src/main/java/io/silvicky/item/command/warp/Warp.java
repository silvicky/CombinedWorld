package io.silvicky.item.command.warp;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.silvicky.item.StateSaver;
import net.minecraft.command.argument.DimensionArgumentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import static io.silvicky.item.InventoryManager.*;
import static io.silvicky.item.common.Util.*;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class Warp {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher)
    {
        dispatcher.register(
                literal("warp")
                        .executes(context->help(context.getSource()))
                                .then(argument(DIMENSION, DimensionArgumentType.dimension())
                                        .executes(context -> warp(context.getSource(),context.getSource().getPlayer(),DimensionArgumentType.getDimensionArgument(context, DIMENSION)))));
    }
    private static int help(ServerCommandSource source)
    {
        source.sendFeedback(()-> Text.literal("Usage: /warp <dimension>"),false);
        source.sendFeedback(()-> Text.literal("Go to that world."),false);
        source.sendFeedback(()-> Text.literal("Only works when executed by a player."),false);
        return Command.SINGLE_SUCCESS;
    }
    public static int warp(ServerCommandSource source, ServerPlayerEntity player, ServerWorld dimension) throws CommandSyntaxException
    {
        return warp(source,player,dimension,false);
    }
    public static int warp(ServerCommandSource source, ServerPlayerEntity player, ServerWorld dimension, boolean silent) throws CommandSyntaxException
    {
        if(player==null) throw ERR_NOT_BY_PLAYER.create();
        ServerWorld from=player.getWorld();
        MinecraftServer server=source.getServer();
        StateSaver stateSaver=StateSaver.getServerState(server);
        StateSaver.WarpRestrictionInfo info=stateSaver.restrictionInfoHashMap.get(Identifier.of(getDimensionId(dimension)));
        if(info!=null&&!source.hasPermissionLevel(info.level))
        {
            throw ERR_WARP_FORBIDDEN.create(info.reason);
        }
        if(!getDimensionId(dimension).equals(getDimensionId(from)))
        {
            LOGGER.info(player.getName().getString()+" goes to "+ getDimensionId(dimension));
            if(!dimension.getRegistryKey().getValue().getNamespace().equals(from.getRegistryKey().getValue().getNamespace()))
            {
                player.clearStatusEffects();
                save(server,player);
                try{load(server,player,dimension);}
                catch(Exception e)
                {
                    loadInventory(player,from, StateSaver.getServerState(server));
                    throw e;
                }
            }
            else
            {
                directWarp(server,player,dimension);
            }
            if(!silent)source.sendFeedback(()-> Text.literal("Teleported to "+ getDimensionId(dimension)+"!"),false);
        }
        else
        {
            if(!silent)source.sendFeedback(()->Text.literal("Nothing happened."),false);
        }
        return Command.SINGLE_SUCCESS;
    }
}
