package io.silvicky.item.command.warp;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.silvicky.item.StateSaver;
import net.minecraft.command.argument.DimensionArgumentType;
import net.minecraft.command.permission.Permission;
import net.minecraft.command.permission.PermissionLevel;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.HashMap;

import static io.silvicky.item.common.Util.*;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class BanWarp {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher)
    {
        dispatcher.register(
                literal("banwarp")
                        .requires(context-> context.getPermissions().hasPermission(new Permission.Level(PermissionLevel.GAMEMASTERS)))
                        .executes(context->help(context.getSource()))
                        .then(literal("ban")
                                .then(argument(DIMENSION,DimensionArgumentType.dimension())
                                        .executes(ctx->banWarp(ctx.getSource(),DimensionArgumentType.getDimensionArgument(ctx,DIMENSION)))
                                        .then(argument(LEVEL, IntegerArgumentType.integer())
                                                .executes(ctx->banWarp(ctx.getSource(),DimensionArgumentType.getDimensionArgument(ctx,DIMENSION),IntegerArgumentType.getInteger(ctx, LEVEL)))
                                                .then(argument(REASON, StringArgumentType.greedyString())
                                                        .executes(ctx->banWarp(ctx.getSource(),DimensionArgumentType.getDimensionArgument(ctx,DIMENSION),IntegerArgumentType.getInteger(ctx, LEVEL),StringArgumentType.getString(ctx, REASON)))))))
                        .then(literal("bangroup")
                                .then(argument(DIMENSION,DimensionArgumentType.dimension())
                                        .executes(ctx->banGroup(ctx.getSource(),DimensionArgumentType.getDimensionArgument(ctx,DIMENSION)))
                                        .then(argument(LEVEL, IntegerArgumentType.integer())
                                                .executes(ctx->banGroup(ctx.getSource(),DimensionArgumentType.getDimensionArgument(ctx,DIMENSION),IntegerArgumentType.getInteger(ctx, LEVEL)))
                                                .then(argument(REASON, StringArgumentType.greedyString())
                                                        .executes(ctx->banGroup(ctx.getSource(),DimensionArgumentType.getDimensionArgument(ctx,DIMENSION),IntegerArgumentType.getInteger(ctx, LEVEL),StringArgumentType.getString(ctx, REASON)))))))
                        .then(literal("banall")
                                .executes(ctx->banAll(ctx.getSource()))
                                .then(argument(LEVEL, IntegerArgumentType.integer())
                                        .executes(ctx->banAll(ctx.getSource(),IntegerArgumentType.getInteger(ctx, LEVEL)))
                                        .then(argument(REASON, StringArgumentType.greedyString())
                                                .executes(ctx->banAll(ctx.getSource(),IntegerArgumentType.getInteger(ctx, LEVEL),StringArgumentType.getString(ctx, REASON))))))
                        .then(literal("lift")
                                .then(argument(DIMENSION,DimensionArgumentType.dimension())
                                        .executes(ctx->liftBan(ctx.getSource(),DimensionArgumentType.getDimensionArgument(ctx,DIMENSION)))))
                        .then(literal("liftgroup")
                                .then(argument(DIMENSION,DimensionArgumentType.dimension())
                                        .executes(ctx->liftGroup(ctx.getSource(),DimensionArgumentType.getDimensionArgument(ctx,DIMENSION)))))
                        .then(literal("liftall")
                                .executes(ctx->liftAll(ctx.getSource()))));
    }
    private static int help(ServerCommandSource source)
    {
        source.sendFeedback(()-> Text.literal("Usage:"),false);
        source.sendFeedback(()-> Text.literal("/banwarp (ban|bangroup) <dimension> [<level>] [<reason>]"),false);
        source.sendFeedback(()-> Text.literal("Ban warp to world/group of <dimension>."),false);
        source.sendFeedback(()-> Text.literal("/banwarp (lift|liftgroup) <dimension>"),false);
        source.sendFeedback(()-> Text.literal("Lift ban on world/group of <dimension>."),false);
        source.sendFeedback(()-> Text.literal("/banwarp banall [<level>] [<reason>]"),false);
        source.sendFeedback(()-> Text.literal("/banwarp liftall"),false);
        source.sendFeedback(()-> Text.literal("Ban/lift all."),false);
        return Command.SINGLE_SUCCESS;
    }
    public static int banWarp(ServerCommandSource source,ServerWorld dimension)
    {
        return banWarp(source,dimension,StateSaver.WarpRestrictionInfo.INFINITE);
    }
    public static int banWarp(ServerCommandSource source,ServerWorld dimension,int level)
    {
        return banWarp(source,dimension,level,StateSaver.WarpRestrictionInfo.DEFAULT_REASON);
    }
    public static int banWarp(ServerCommandSource source,ServerWorld dimension,int level,String reason)
    {
        return banWarp(source,dimension,level,reason,false);
    }
    public static int banWarp(ServerCommandSource source, ServerWorld dimension,int level,String reason,boolean silent)
    {
        StateSaver stateSaver=StateSaver.getServerState(source.getServer());
        HashMap<Identifier,StateSaver.WarpRestrictionInfo> restrictionInfoHashMap=stateSaver.restrictionInfoHashMap;
        restrictionInfoHashMap.put(Identifier.of(getDimensionId(dimension)),new StateSaver.WarpRestrictionInfo(reason,level));
        if(!silent)source.sendFeedback(()->Text.literal("Banned warp to world of "+dimension.getRegistryKey().getValue()+" for level lower than "+level+", reason: "+reason),false);
        return Command.SINGLE_SUCCESS;
    }
    public static int liftBan(ServerCommandSource source, ServerWorld dimension)
    {
        return liftBan(source,dimension,false);
    }
    public static int liftBan(ServerCommandSource source, ServerWorld dimension,boolean silent)
    {
        StateSaver stateSaver=StateSaver.getServerState(source.getServer());
        HashMap<Identifier,StateSaver.WarpRestrictionInfo> restrictionInfoHashMap=stateSaver.restrictionInfoHashMap;
        restrictionInfoHashMap.remove(Identifier.of(getDimensionId(dimension)));
        if(!silent)source.sendFeedback(()->Text.literal("Lifted ban on warp to world of "+dimension.getRegistryKey().getValue()),false);
        return Command.SINGLE_SUCCESS;
    }
    public static int liftAll(ServerCommandSource source)
    {
        StateSaver stateSaver=StateSaver.getServerState(source.getServer());
        HashMap<Identifier,StateSaver.WarpRestrictionInfo> restrictionInfoHashMap=stateSaver.restrictionInfoHashMap;
        restrictionInfoHashMap.clear();
        source.sendFeedback(()->Text.literal("Lifted all ban on warp."),false);
        return Command.SINGLE_SUCCESS;
    }
    public static int liftGroup(ServerCommandSource source, ServerWorld dimension)
    {
        for(ServerWorld world:source.getServer().getWorlds())
            if(world.getRegistryKey().getValue().getNamespace().equals(dimension.getRegistryKey().getValue().getNamespace()))
                liftBan(source,world,true);
        source.sendFeedback(()->Text.literal("Lifted ban on warp to "+dimension.getRegistryKey().getValue().getNamespace()),false);
        return Command.SINGLE_SUCCESS;
    }
    public static int banAll(ServerCommandSource source)
    {
        return banAll(source, StateSaver.WarpRestrictionInfo.INFINITE);
    }
    public static int banAll(ServerCommandSource source,int level)
    {
        return banAll(source,level, StateSaver.WarpRestrictionInfo.DEFAULT_REASON);
    }
    public static int banAll(ServerCommandSource source,int level,String reason)
    {
        for(ServerWorld world:source.getServer().getWorlds())banWarp(source,world,level,reason,true);
        source.sendFeedback(()->Text.literal("Banned all warp for level lower than "+level+", reason: "+reason),false);
        return Command.SINGLE_SUCCESS;
    }
    public static int banGroup(ServerCommandSource source,ServerWorld dimension)
    {
        return banGroup(source,dimension, StateSaver.WarpRestrictionInfo.INFINITE);
    }
    public static int banGroup(ServerCommandSource source,ServerWorld dimension,int level)
    {
        return banGroup(source,dimension,level,StateSaver.WarpRestrictionInfo.DEFAULT_REASON);
    }
    public static int banGroup(ServerCommandSource source,ServerWorld dimension,int level,String reason)
    {
        for(ServerWorld world:source.getServer().getWorlds())
            if(world.getRegistryKey().getValue().getNamespace().equals(dimension.getRegistryKey().getValue().getNamespace()))
                banWarp(source,world,level,reason,true);
        source.sendFeedback(()->Text.literal("Banned warp to "+dimension.getRegistryKey().getValue().getNamespace()+" for level lower than "+level+", reason: "+reason),false);
        return Command.SINGLE_SUCCESS;
    }
}
