package io.silvicky.item.command.warp;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.silvicky.item.StateSaver;
import io.silvicky.item.command.suggestion.GroupSuggestionProvider;
import io.silvicky.item.command.suggestion.WorldSuggestionProvider;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

import static io.silvicky.item.common.Util.*;
import static java.lang.String.format;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class BanWarp {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher)
    {
        dispatcher.register(
                literal("banwarp")
                        .requires(context-> context.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS)))
                        .executes(context->help(context.getSource()))
                        .then(literal("ban")
                                .then(argument(DIMENSION, DimensionArgument.dimension())
                                        .suggests(new WorldSuggestionProvider())
                                        .executes(ctx->banWarp(ctx.getSource(), DimensionArgument.getDimension(ctx,DIMENSION)))
                                        .then(argument(LEVEL, IntegerArgumentType.integer())
                                                .executes(ctx->banWarp(ctx.getSource(), DimensionArgument.getDimension(ctx,DIMENSION),IntegerArgumentType.getInteger(ctx, LEVEL)))
                                                .then(argument(REASON, StringArgumentType.greedyString())
                                                        .executes(ctx->banWarp(ctx.getSource(), DimensionArgument.getDimension(ctx,DIMENSION),IntegerArgumentType.getInteger(ctx, LEVEL),StringArgumentType.getString(ctx, REASON)))))))
                        .then(literal("bangroup")
                                .then(argument(NAMESPACE,StringArgumentType.word())
                                        .suggests(new GroupSuggestionProvider())
                                        .executes(ctx->banGroup(ctx.getSource(),StringArgumentType.getString(ctx,NAMESPACE)))
                                        .then(argument(LEVEL, IntegerArgumentType.integer())
                                                .executes(ctx->banGroup(ctx.getSource(),StringArgumentType.getString(ctx,NAMESPACE),IntegerArgumentType.getInteger(ctx, LEVEL)))
                                                .then(argument(REASON, StringArgumentType.greedyString())
                                                        .executes(ctx->banGroup(ctx.getSource(),StringArgumentType.getString(ctx,NAMESPACE),IntegerArgumentType.getInteger(ctx, LEVEL),StringArgumentType.getString(ctx, REASON)))))))
                        .then(literal("banall")
                                .executes(ctx->banAll(ctx.getSource()))
                                .then(argument(LEVEL, IntegerArgumentType.integer())
                                        .executes(ctx->banAll(ctx.getSource(),IntegerArgumentType.getInteger(ctx, LEVEL)))
                                        .then(argument(REASON, StringArgumentType.greedyString())
                                                .executes(ctx->banAll(ctx.getSource(),IntegerArgumentType.getInteger(ctx, LEVEL),StringArgumentType.getString(ctx, REASON))))))
                        .then(literal("lift")
                                .then(argument(DIMENSION, DimensionArgument.dimension())
                                        .suggests(new WorldSuggestionProvider())
                                        .executes(ctx->liftBan(ctx.getSource(), DimensionArgument.getDimension(ctx,DIMENSION)))))
                        .then(literal("liftgroup")
                                .then(argument(NAMESPACE,StringArgumentType.word())
                                        .suggests(new GroupSuggestionProvider())
                                        .executes(ctx->liftGroup(ctx.getSource(),StringArgumentType.getString(ctx,NAMESPACE)))))
                        .then(literal("liftall")
                                .executes(ctx->liftAll(ctx.getSource())))
                        .then(literal("list")
                                .executes(ctx->list(ctx.getSource()))));
    }
    private static int help(CommandSourceStack source)
    {
        source.sendSuccess(()-> Component.literal("Usage:"),false);
        source.sendSuccess(()-> Component.literal("/banwarp ((ban <dimension>)|(bangroup <namespace>)|ban) [<level>] [<reason>]"),false);
        source.sendSuccess(()-> Component.literal("Ban warp to world/group/all."),false);
        source.sendSuccess(()-> Component.literal("/banwarp ((lift <dimension>)|(liftgroup <namespace>)|lift)"),false);
        source.sendSuccess(()-> Component.literal("Lift ban on world/group/all."),false);
        source.sendSuccess(()-> Component.literal("/banwarp list"),false);
        source.sendSuccess(()-> Component.literal("List ban."),false);
        return Command.SINGLE_SUCCESS;
    }
    private static int banWarp(CommandSourceStack source, ServerLevel dimension)
    {
        return banWarp(source,dimension,StateSaver.WarpRestrictionInfo.INFINITE);
    }
    private static int banWarp(CommandSourceStack source, ServerLevel dimension, int level)
    {
        return banWarp(source,dimension,level,StateSaver.WarpRestrictionInfo.DEFAULT_REASON);
    }
    private static int banWarp(CommandSourceStack source, ServerLevel dimension, int level, String reason)
    {
        return banWarp(source,dimension,level,reason,false);
    }
    public static int banWarp(CommandSourceStack source, ServerLevel dimension, int level, String reason, boolean silent)
    {
        StateSaver stateSaver=StateSaver.getServerState(source.getServer());
        HashMap<Identifier,StateSaver.WarpRestrictionInfo> restrictionInfoHashMap=stateSaver.restrictionInfoHashMap;
        restrictionInfoHashMap.put(getDimensionId(dimension),new StateSaver.WarpRestrictionInfo(reason,level));
        if(!silent)source.sendSuccess(()-> Component.literal("Banned warp to world of "+dimension.dimension().identifier()+" for level lower than "+level+", reason: "+reason),false);
        return Command.SINGLE_SUCCESS;
    }
    private static int liftBan(CommandSourceStack source, ServerLevel dimension)
    {
        return liftBan(source,dimension,false);
    }
    private static int liftBan(CommandSourceStack source, ServerLevel dimension, boolean silent)
    {
        StateSaver stateSaver=StateSaver.getServerState(source.getServer());
        HashMap<Identifier,StateSaver.WarpRestrictionInfo> restrictionInfoHashMap=stateSaver.restrictionInfoHashMap;
        restrictionInfoHashMap.remove(getDimensionId(dimension));
        if(!silent)source.sendSuccess(()-> Component.literal("Lifted ban on warp to world of "+dimension.dimension().identifier()),false);
        return Command.SINGLE_SUCCESS;
    }
    private static int liftAll(CommandSourceStack source)
    {
        StateSaver stateSaver=StateSaver.getServerState(source.getServer());
        HashMap<Identifier,StateSaver.WarpRestrictionInfo> restrictionInfoHashMap=stateSaver.restrictionInfoHashMap;
        restrictionInfoHashMap.clear();
        source.sendSuccess(()-> Component.literal("Lifted all ban on warp."),false);
        return Command.SINGLE_SUCCESS;
    }
    private static int liftGroup(CommandSourceStack source, String namespace)
    {
        for(ServerLevel world:source.getServer().getAllLevels())
            if(world.dimension().identifier().getNamespace().equals(namespace))
                liftBan(source,world,true);
        source.sendSuccess(()-> Component.literal("Lifted ban on warp to "+namespace),false);
        return Command.SINGLE_SUCCESS;
    }
    private static int banAll(CommandSourceStack source)
    {
        return banAll(source, StateSaver.WarpRestrictionInfo.INFINITE);
    }
    private static int banAll(CommandSourceStack source, int level)
    {
        return banAll(source,level, StateSaver.WarpRestrictionInfo.DEFAULT_REASON);
    }
    private static int banAll(CommandSourceStack source, int level, String reason)
    {
        for(ServerLevel world:source.getServer().getAllLevels())banWarp(source,world,level,reason,true);
        source.sendSuccess(()-> Component.literal("Banned all warp for level lower than "+level+", reason: "+reason),false);
        return Command.SINGLE_SUCCESS;
    }
    private static int banGroup(CommandSourceStack source, String namespace)
    {
        return banGroup(source, namespace, StateSaver.WarpRestrictionInfo.INFINITE);
    }
    private static int banGroup(CommandSourceStack source, String namespace, int level)
    {
        return banGroup(source,namespace,level,StateSaver.WarpRestrictionInfo.DEFAULT_REASON);
    }
    private static int banGroup(CommandSourceStack source, String namespace, int level, String reason)
    {
        for(ServerLevel world:source.getServer().getAllLevels())
            if(world.dimension().identifier().getNamespace().equals(namespace))
                banWarp(source,world,level,reason,true);
        source.sendSuccess(()-> Component.literal("Banned warp to "+namespace+" for level lower than "+level+", reason: "+reason),false);
        return Command.SINGLE_SUCCESS;
    }
    private static int list(CommandSourceStack source)
    {
        StateSaver stateSaver=StateSaver.getServerState(source.getServer());
        HashMap<Identifier,StateSaver.WarpRestrictionInfo> restrictionInfoHashMap=stateSaver.restrictionInfoHashMap;
        for(Map.Entry<Identifier, StateSaver.WarpRestrictionInfo> entry:restrictionInfoHashMap.entrySet())
        {
            source.sendSuccess(()-> Component.literal(format("%s: %s",entry.getKey(),entry.getValue())),false);
        }
        source.sendSuccess(()-> Component.literal("Done."),false);
        return Command.SINGLE_SUCCESS;
    }
}
