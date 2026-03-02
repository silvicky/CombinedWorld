package io.silvicky.item.command.backrooms;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.silvicky.item.command.suggestion.GroupSuggestionProvider;
import net.minecraft.command.argument.DimensionArgumentType;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.command.argument.HexColorArgumentType;
import net.minecraft.command.permission.Permission;
import net.minecraft.command.permission.PermissionLevel;
import net.minecraft.server.PlayerConfigEntry;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

import java.util.Collection;

import static io.silvicky.item.StateSaver.getServerState;
import static io.silvicky.item.backrooms.EntityVisibilityManager.*;
import static io.silvicky.item.cfg.JSONConfig.playerVisibilityRange;
import static io.silvicky.item.command.warp.WarpTp.profileListToPlayer;
import static io.silvicky.item.common.Util.*;
import static java.lang.String.format;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class Visibility
{
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher)
    {
        dispatcher.register(
                literal("visibility")
                        .requires(context-> context.getPermissions().hasPermission(new Permission.Level(PermissionLevel.GAMEMASTERS)))
                        .executes(context->help(context.getSource()))
                        .then(literal("world")
                                .then(argument(DIMENSION, DimensionArgumentType.dimension())
                                        .executes(ctx->getDimensionLevel(ctx.getSource(),DimensionArgumentType.getDimensionArgument(ctx,DIMENSION)))
                                        .then(argument(LEVEL, HexColorArgumentType.hexColor())
                                                .executes(ctx->setDimensionLevel(ctx.getSource(),DimensionArgumentType.getDimensionArgument(ctx,DIMENSION),HexColorArgumentType.getArgbColor(ctx, LEVEL))))))
                        .then(literal("player")
                                .then(argument(NAMESPACE, StringArgumentType.word())
                                        .suggests(new GroupSuggestionProvider())
                                        .then(argument(PLAYER, GameProfileArgumentType.gameProfile())
                                                .executes(ctx->getPlayerLevel(ctx.getSource(),StringArgumentType.getString(ctx,NAMESPACE),GameProfileArgumentType.getProfileArgument(ctx,PLAYER)))
                                                .then(argument(LEVEL, LongArgumentType.longArg(0,playerVisibilityRange-1))
                                                        .executes(ctx->setPlayerLevel(ctx.getSource(),StringArgumentType.getString(ctx,NAMESPACE),GameProfileArgumentType.getProfileArgument(ctx,PLAYER),LongArgumentType.getLong(ctx,LEVEL)))))))
                        .then(literal("init")
                                .then(argument(NAMESPACE, StringArgumentType.word())
                                        .suggests(new GroupSuggestionProvider())
                                        .executes(ctx->initPlayerLevel(ctx.getSource(),StringArgumentType.getString(ctx,NAMESPACE)))))
                        );
    }
    private static int help(ServerCommandSource source)
    {
        source.sendFeedback(()-> Text.literal("Usage:"),false);
        source.sendFeedback(()-> Text.literal("/visibility world <dimension> <level>"),false);
        source.sendFeedback(()-> Text.literal("Get or set entity visibility of dimension(s)."),false);
        source.sendFeedback(()-> Text.literal("/visibility player <namespace> <player> <level>"),false);
        source.sendFeedback(()-> Text.literal("Get or set player visibility field of group."),false);
        source.sendFeedback(()-> Text.literal("/visibility init <namespace>"),false);
        source.sendFeedback(()-> Text.literal("Reset player visibility fields of group."),false);
        return Command.SINGLE_SUCCESS;
    }
    private static int getDimensionLevel(ServerCommandSource source, ServerWorld dimension)
    {
        source.sendFeedback(()->Text.literal(format("%06X",getServerState(source.getServer()).entityVisibility.getOrDefault(dimension.getRegistryKey().getValue(), 0)&0xFFFFFF)),false);
        return Command.SINGLE_SUCCESS;
    }
    private static int setDimensionLevel(ServerCommandSource source, ServerWorld dimension, int level)
    {
        getServerState(source.getServer()).entityVisibility.put(dimension.getRegistryKey().getValue(), level);
        source.sendFeedback(()->Text.literal("Done."),false);
        return Command.SINGLE_SUCCESS;
    }
    private static int getPlayerLevel(ServerCommandSource source, String namespace, Collection<PlayerConfigEntry> profileList) throws CommandSyntaxException
    {
        ServerPlayerEntity player=profileListToPlayer(source.getServer(),profileList);
        source.sendFeedback(()->Text.literal(Long.toString(getPlayerVisibility(source.getServer(),namespace,player.getUuidAsString()))),false);
        return Command.SINGLE_SUCCESS;
    }
    private static int setPlayerLevel(ServerCommandSource source, String namespace, Collection<PlayerConfigEntry> profileList, long level) throws CommandSyntaxException
    {
        ServerPlayerEntity player=profileListToPlayer(source.getServer(),profileList);
        setPlayerVisibility(source.getServer(),namespace,player.getUuidAsString(),level);
        source.sendFeedback(()->Text.literal("Done."),false);
        return Command.SINGLE_SUCCESS;
    }
    private static int initPlayerLevel(ServerCommandSource source, String namespace)
    {
        initPlayerVisibility(source.getServer(),namespace);
        source.sendFeedback(()->Text.literal("Done."),false);
        return Command.SINGLE_SUCCESS;
    }
}
