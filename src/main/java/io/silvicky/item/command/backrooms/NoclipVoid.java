package io.silvicky.item.command.backrooms;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import io.silvicky.item.StateSaver;
import io.silvicky.item.common.WeightedSelector;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;

import java.util.Map;

import static io.silvicky.item.StateSaver.getServerState;
import static io.silvicky.item.common.Util.*;
import static java.lang.String.format;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class NoclipVoid
{
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher)
    {
        dispatcher.register(
                literal("noclipvoid")
                        .requires(context-> context.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS)))
                        .executes(context->help(context.getSource()))
                        .then(argument(DIMENSION, DimensionArgument.dimension())
                                .executes(ctx-> getWeights(ctx.getSource(), DimensionArgument.getDimension(ctx,DIMENSION)))
                                .then(argument(TARGET, DimensionArgument.dimension())
                                        .then(argument(LEVEL, IntegerArgumentType.integer())
                                                .executes(ctx->setWeight(ctx.getSource(), DimensionArgument.getDimension(ctx,DIMENSION), DimensionArgument.getDimension(ctx,TARGET), IntegerArgumentType.getInteger(ctx, LEVEL))))))
        );
    }
    private static int help(CommandSourceStack source)
    {
        source.sendSuccess(()-> Component.literal("Usage:"),false);
        source.sendSuccess(()-> Component.literal("/noclipvoid <dimension> [<target> <level>]"),false);
        source.sendSuccess(()-> Component.literal("Set void noclipping weight from <dimension> to <level> (total 65536), or list all weights from <dimension>."),false);
        return Command.SINGLE_SUCCESS;
    }
    private static int getWeights(CommandSourceStack source, ServerLevel dimension)
    {
        StateSaver stateSaver=StateSaver.getServerState(dimension.getServer());
        for(Map.Entry<Identifier,Integer> entry:stateSaver.ext.noclip.getOrDefault(dimension.dimension.identifier(),new WeightedSelector<>()).asMap().entrySet())
        {
            source.sendSuccess(()-> Component.literal(format("%s -> %d", entry.getKey(),entry.getValue())),false);
        }
        source.sendSuccess(()-> Component.literal("Done."),false);
        return Command.SINGLE_SUCCESS;
    }
    private static int setWeight(CommandSourceStack source, ServerLevel dimension, ServerLevel target, int level)
    {
        getServerState(source.getServer()).ext.noclip.computeIfAbsent(dimension.dimension().identifier(),_->new WeightedSelector<>()).put(target.dimension().identifier(), level);
        source.sendSuccess(()-> Component.literal("Done."),false);
        return Command.SINGLE_SUCCESS;
    }
}
