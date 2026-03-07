package io.silvicky.item.command.warp;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.silvicky.item.StateSaver;
import io.silvicky.item.command.suggestion.WorldSuggestionProvider;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;

import static io.silvicky.item.InventoryManager.loadPos;
import static io.silvicky.item.common.Util.*;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class Warp {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher)
    {
        dispatcher.register(
                literal("warp")
                        .executes(context->help(context.getSource()))
                                .then(argument(DIMENSION, DimensionArgument.dimension())
                                        .suggests(new WorldSuggestionProvider())
                                        .executes(context -> warp(context.getSource(),context.getSource().getPlayer(), DimensionArgument.getDimension(context, DIMENSION)))));
    }
    private static int help(CommandSourceStack source)
    {
        source.sendSuccess(()-> Component.literal("Usage: /warp <dimension>"),false);
        source.sendSuccess(()-> Component.literal("Go to that world."),false);
        source.sendSuccess(()-> Component.literal("Only works when executed by a player."),false);
        return Command.SINGLE_SUCCESS;
    }
    public static int warp(CommandSourceStack source, ServerPlayer player, ServerLevel dimension) throws CommandSyntaxException
    {
        return warp(source,player,dimension,false);
    }
    public static int warp(CommandSourceStack source, ServerPlayer player, ServerLevel dimension, boolean silent) throws CommandSyntaxException
    {
        if(player==null) throw ERR_NOT_BY_PLAYER.create();
        ServerLevel from=player.level();
        MinecraftServer server=source.getServer();
        StateSaver stateSaver=StateSaver.getServerState(server);
        StateSaver.WarpRestrictionInfo info=stateSaver.restrictionInfoHashMap.get(getDimensionId(dimension));
        if(info!=null&&(info.level>4||!source.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.byId(info.level)))))
        {
            throw ERR_WARP_FORBIDDEN.create(info.reason);
        }
        if(!getDimensionId(dimension).equals(getDimensionId(from)))
        {
            LOGGER.info(player.getName().getString()+" goes to "+ getDimensionId(dimension));
            if(!dimension.dimension().identifier().getNamespace().equals(from.dimension().identifier().getNamespace()))
            {
                player.removeAllEffects();
            }
            loadPos(server, player, dimension, stateSaver);
            if(!silent)source.sendSuccess(()-> Component.literal("Teleported to "+ getDimensionId(dimension)+"!"),false);
        }
        else
        {
            if(!silent)source.sendSuccess(()-> Component.literal("Nothing happened."),false);
        }
        return Command.SINGLE_SUCCESS;
    }
}
