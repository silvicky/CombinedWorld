package io.silvicky.item.command.backrooms;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.silvicky.item.command.suggestion.GroupSuggestionProvider;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.commands.arguments.HexColorArgument;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.players.NameAndId;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;

import java.util.Collection;

import static io.silvicky.item.StateSaver.getServerState;
import static io.silvicky.item.backrooms.EntityVisibilityManager.*;
import static io.silvicky.item.cfg.JSONConfig.playerVisibilityRange;
import static io.silvicky.item.command.warp.WarpTp.profileListToPlayer;
import static io.silvicky.item.common.Util.*;
import static java.lang.String.format;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class Visibility
{
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher)
    {
        dispatcher.register(
                literal("visibility")
                        .requires(context-> context.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS)))
                        .executes(context->help(context.getSource()))
                        .then(literal("world")
                                .then(argument(DIMENSION, DimensionArgument.dimension())
                                        .executes(ctx->getDimensionLevel(ctx.getSource(), DimensionArgument.getDimension(ctx,DIMENSION)))
                                        .then(argument(LEVEL, HexColorArgument.hexColor())
                                                .executes(ctx->setDimensionLevel(ctx.getSource(), DimensionArgument.getDimension(ctx,DIMENSION), HexColorArgument.getHexColor(ctx, LEVEL))))))
                        .then(literal("player")
                                .then(argument(NAMESPACE, StringArgumentType.word())
                                        .suggests(new GroupSuggestionProvider())
                                        .then(argument(PLAYER, GameProfileArgument.gameProfile())
                                                .executes(ctx->getPlayerLevel(ctx.getSource(),StringArgumentType.getString(ctx,NAMESPACE), GameProfileArgument.getGameProfiles(ctx,PLAYER)))
                                                .then(argument(LEVEL, LongArgumentType.longArg(0,playerVisibilityRange-1))
                                                        .executes(ctx->setPlayerLevel(ctx.getSource(),StringArgumentType.getString(ctx,NAMESPACE), GameProfileArgument.getGameProfiles(ctx,PLAYER),LongArgumentType.getLong(ctx,LEVEL)))))))
                        .then(literal("init")
                                .then(argument(NAMESPACE, StringArgumentType.word())
                                        .suggests(new GroupSuggestionProvider())
                                        .executes(ctx->initPlayerLevel(ctx.getSource(),StringArgumentType.getString(ctx,NAMESPACE)))))
                        );
    }
    private static int help(CommandSourceStack source)
    {
        source.sendSuccess(()-> Component.literal("Usage:"),false);
        source.sendSuccess(()-> Component.literal("/visibility world <dimension> <level>"),false);
        source.sendSuccess(()-> Component.literal("Get or set entity visibility of dimension(s)."),false);
        source.sendSuccess(()-> Component.literal("/visibility player <namespace> <player> <level>"),false);
        source.sendSuccess(()-> Component.literal("Get or set player visibility field of group."),false);
        source.sendSuccess(()-> Component.literal("/visibility init <namespace>"),false);
        source.sendSuccess(()-> Component.literal("Reset player visibility fields of group."),false);
        return Command.SINGLE_SUCCESS;
    }
    private static int getDimensionLevel(CommandSourceStack source, ServerLevel dimension)
    {
        source.sendSuccess(()-> Component.literal(format("%06X",getServerState(source.getServer()).entityVisibility.getOrDefault(dimension.dimension().identifier(), 0)&0xFFFFFF)),false);
        return Command.SINGLE_SUCCESS;
    }
    private static int setDimensionLevel(CommandSourceStack source, ServerLevel dimension, int level)
    {
        getServerState(source.getServer()).entityVisibility.put(dimension.dimension().identifier(), level);
        source.sendSuccess(()-> Component.literal("Done."),false);
        return Command.SINGLE_SUCCESS;
    }
    private static int getPlayerLevel(CommandSourceStack source, String namespace, Collection<NameAndId> profileList) throws CommandSyntaxException
    {
        ServerPlayer player=profileListToPlayer(source.getServer(),profileList);
        source.sendSuccess(()-> Component.literal(Long.toString(getPlayerVisibility(source.getServer(),namespace,player.getStringUUID()))),false);
        return Command.SINGLE_SUCCESS;
    }
    private static int setPlayerLevel(CommandSourceStack source, String namespace, Collection<NameAndId> profileList, long level) throws CommandSyntaxException
    {
        ServerPlayer player=profileListToPlayer(source.getServer(),profileList);
        setPlayerVisibility(source.getServer(),namespace,player.getStringUUID(),level);
        source.sendSuccess(()-> Component.literal("Done."),false);
        return Command.SINGLE_SUCCESS;
    }
    private static int initPlayerLevel(CommandSourceStack source, String namespace)
    {
        initPlayerVisibility(source.getServer(),namespace);
        source.sendSuccess(()-> Component.literal("Done."),false);
        return Command.SINGLE_SUCCESS;
    }
}
