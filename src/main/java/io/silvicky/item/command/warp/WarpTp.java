package io.silvicky.item.command.warp;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import io.silvicky.item.command.suggestion.WorldSuggestionProvider;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.portal.TeleportTransition;

import java.util.Collection;

import static io.silvicky.item.command.warp.Warp.warp;
import static io.silvicky.item.common.Util.*;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class WarpTp {
    public static SimpleCommandExceptionType NOT_BY_PLAYER=new SimpleCommandExceptionType(Component.literal("Not by player."));
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher)
    {
        dispatcher.register(
                literal("warptp")
                        .requires(source -> source.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS)))
                        .executes(context->help(context.getSource()))
                        .then(argument(DIMENSION, DimensionArgument.dimension())
                                .suggests(new WorldSuggestionProvider())
                                .then(argument(CORD, Vec3Argument.vec3())
                                        .executes(context -> warpTp(context.getSource(), DimensionArgument.getDimension(context,DIMENSION), Vec3Argument.getVec3(context, CORD)))))
                        .then(argument(TARGET, EntityArgument.entity())
                                .executes(context -> warpTp(context.getSource(), EntityArgument.getEntity(context, TARGET))))
                        .then(argument(PLAYER, GameProfileArgument.gameProfile())
                                .then(argument(DIMENSION, DimensionArgument.dimension())
                                        .suggests(new WorldSuggestionProvider())
                                        .executes(context -> warpTp(context.getSource(), GameProfileArgument.getGameProfiles(context, PLAYER), DimensionArgument.getDimension(context,DIMENSION)))
                                        .then(argument(CORD, Vec3Argument.vec3())
                                                .executes(context -> warpTp(context.getSource(), GameProfileArgument.getGameProfiles(context, PLAYER), DimensionArgument.getDimension(context,DIMENSION), Vec3Argument.getVec3(context, CORD)))))
                                .then(argument(TARGET, EntityArgument.entity())
                                        .executes(context -> warpTp(context.getSource(), GameProfileArgument.getGameProfiles(context, PLAYER), EntityArgument.getEntity(context, TARGET)))))
        );
    }
    private static int help(CommandSourceStack source)
    {
        source.sendSuccess(()-> Component.literal("Usage:"),false);
        source.sendSuccess(()-> Component.literal("/warptp [<player>] <dimension> [<position>]"),false);
        source.sendSuccess(()-> Component.literal("/warptp [<player>] <target>"),false);
        source.sendSuccess(()-> Component.literal("Warp <player>(or yourself) to world of <dimension>(and subsequent teleport)"),false);
        return Command.SINGLE_SUCCESS;
    }
    public static ServerPlayer profileListToPlayer(MinecraftServer server, Collection<NameAndId> profileList) throws CommandSyntaxException {
        if(profileList.size()!=1) throw ERR_NOT_ONE_PLAYER.create();
        NameAndId profile=profileList.stream().toList().getFirst();
        return server.getPlayerList().getPlayerByName(profile.name());
    }
    private static int warpTp(CommandSourceStack source, Collection<NameAndId> profileList, ServerLevel dimension) throws CommandSyntaxException
    {
        return warp(source,profileListToPlayer(source.getServer(),profileList), dimension);
    }
    private static int warpTp(CommandSourceStack source, Collection<NameAndId> profileList, ServerLevel dimension, Vec3 target) throws CommandSyntaxException
    {
        return warpTp(profileListToPlayer(source.getServer(),profileList),dimension,target);
    }
    private static int warpTp(ServerPlayer player, ServerLevel dimension, Vec3 target)
    {
        TeleportTransition tpTarget = new TeleportTransition(dimension, target, Vec3.ZERO, 0f, 0f, TeleportTransition.DO_NOTHING);
        player.teleport(tpTarget);
        return Command.SINGLE_SUCCESS;
    }
    private static int warpTp(CommandSourceStack source, ServerLevel dimension, Vec3 target) throws CommandSyntaxException
    {
        if(source.getPlayer()==null)
        {
            throw NOT_BY_PLAYER.create();
        }
        return warpTp(source.getPlayer(),dimension,target);
    }
    private static int warpTp(CommandSourceStack source, Entity entity) throws CommandSyntaxException
    {
        return warpTp(source, (ServerLevel) entity.level(),entity.position());
    }
    private static int warpTp(CommandSourceStack source, Collection<NameAndId> profileList, Entity entity) throws CommandSyntaxException
    {
        return warpTp(source,profileList, (ServerLevel) entity.level(),entity.position());
    }
}
