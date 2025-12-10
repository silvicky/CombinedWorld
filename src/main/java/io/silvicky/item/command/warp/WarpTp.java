package io.silvicky.item.command.warp;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.argument.DimensionArgumentType;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.command.argument.Vec3ArgumentType;
import net.minecraft.command.permission.Permission;
import net.minecraft.command.permission.PermissionLevel;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerConfigEntry;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.TeleportTarget;

import java.util.Collection;

import static io.silvicky.item.command.warp.Warp.warp;
import static io.silvicky.item.common.Util.*;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class WarpTp {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher)
    {
        dispatcher.register(
                literal("warptp")
                        .requires(source -> source.getPermissions().hasPermission(new Permission.Level(PermissionLevel.GAMEMASTERS)))
                        .executes(context->help(context.getSource()))
                        .then(argument(DIMENSION, DimensionArgumentType.dimension())
                                .then(argument(CORD, Vec3ArgumentType.vec3())
                                        .executes(context -> warpTp(context.getSource(),DimensionArgumentType.getDimensionArgument(context,DIMENSION),Vec3ArgumentType.getVec3(context, CORD)))))
                        .then(argument(TARGET, EntityArgumentType.entity())
                                .executes(context -> warpTp(context.getSource(),EntityArgumentType.getEntity(context, TARGET))))
                        .then(argument(PLAYER, GameProfileArgumentType.gameProfile())
                                .then(argument(DIMENSION, DimensionArgumentType.dimension())
                                        .executes(context -> warpTp(context.getSource(),GameProfileArgumentType.getProfileArgument(context, PLAYER),DimensionArgumentType.getDimensionArgument(context,DIMENSION)))
                                        .then(argument(CORD, Vec3ArgumentType.vec3())
                                                .executes(context -> warpTp(context.getSource(),GameProfileArgumentType.getProfileArgument(context, PLAYER),DimensionArgumentType.getDimensionArgument(context,DIMENSION),Vec3ArgumentType.getVec3(context, CORD)))))
                                .then(argument(TARGET, EntityArgumentType.entity())
                                        .executes(context -> warpTp(context.getSource(),GameProfileArgumentType.getProfileArgument(context, PLAYER),EntityArgumentType.getEntity(context, TARGET)))))
        );
    }
    private static int help(ServerCommandSource source)
    {
        source.sendFeedback(()-> Text.literal("Usage:"),false);
        source.sendFeedback(()-> Text.literal("/warptp [<player>] <dimension> [<position>]"),false);
        source.sendFeedback(()-> Text.literal("/warptp [<player>] <target>"),false);
        source.sendFeedback(()-> Text.literal("Warp <player>(or yourself) to world of <dimension>(and subsequent teleport)"),false);
        return Command.SINGLE_SUCCESS;
    }
    public static ServerPlayerEntity profileListToPlayer(MinecraftServer server, Collection<PlayerConfigEntry> profileList) throws CommandSyntaxException {
        if(profileList.size()!=1) throw ERR_NOT_ONE_PLAYER.create();
        PlayerConfigEntry profile=profileList.stream().toList().getFirst();
        return server.getPlayerManager().getPlayer(profile.name());
    }
    public static int warpTp(ServerCommandSource source, Collection<PlayerConfigEntry> profileList, ServerWorld dimension) throws CommandSyntaxException
    {
        return warp(source,profileListToPlayer(source.getServer(),profileList), dimension);
    }
    public static int warpTp(ServerCommandSource source, Collection<PlayerConfigEntry> profileList, ServerWorld dimension, Vec3d target) throws CommandSyntaxException
    {
        return warpTp(source,profileListToPlayer(source.getServer(),profileList),dimension,target);
    }
    public static int warpTp(ServerCommandSource source, ServerPlayerEntity player, ServerWorld dimension, Vec3d target) throws CommandSyntaxException
    {
        warp(source,player,dimension);
        TeleportTarget tpTarget = new TeleportTarget(dimension, target, Vec3d.ZERO, 0f, 0f,TeleportTarget.NO_OP);
        player.teleportTo(tpTarget);
        return Command.SINGLE_SUCCESS;
    }
    public static int warpTp(ServerCommandSource source, ServerWorld dimension, Vec3d target) throws CommandSyntaxException
    {
        return warpTp(source,source.getPlayer(),dimension,target);
    }
    public static int warpTp(ServerCommandSource source, Entity entity) throws CommandSyntaxException
    {
        return warpTp(source, (ServerWorld) entity.getEntityWorld(),entity.getEntityPos());
    }
    public static int warpTp(ServerCommandSource source, Collection<PlayerConfigEntry> profileList, Entity entity) throws CommandSyntaxException
    {
        return warpTp(source,profileList, (ServerWorld) entity.getEntityWorld(),entity.getEntityPos());
    }
}
