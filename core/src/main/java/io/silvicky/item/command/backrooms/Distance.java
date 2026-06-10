package io.silvicky.item.command.backrooms;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import io.silvicky.item.StateSaver;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;

import static io.silvicky.item.StateSaver.getServerState;
import static io.silvicky.item.common.Util.DIMENSION;
import static io.silvicky.item.common.Util.LEVEL;
import static java.lang.String.format;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class Distance
{
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher)
    {
        dispatcher.register(
                literal("distance")
                        .requires(context-> context.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS)))
                        .executes(context->help(context.getSource()))
                        .then(argument(DIMENSION, DimensionArgument.dimension())
                                .executes(ctx->getDimensionLevel(ctx.getSource(), DimensionArgument.getDimension(ctx,DIMENSION)))
                                .then(argument(LEVEL, IntegerArgumentType.integer())
                                        .then(literal("sim")
                                                .executes(ctx->setDimensionLevelSim(ctx.getSource(), DimensionArgument.getDimension(ctx,DIMENSION), IntegerArgumentType.getInteger(ctx, LEVEL))))
                                        .then(literal("view")
                                                .executes(ctx->setDimensionLevelView(ctx.getSource(), DimensionArgument.getDimension(ctx,DIMENSION), IntegerArgumentType.getInteger(ctx, LEVEL))))))
        );
    }
    private static int help(CommandSourceStack source)
    {
        source.sendSuccess(()-> Component.literal("Usage:"),false);
        source.sendSuccess(()-> Component.literal("/distance <dimension> <level> (view|sim)"),false);
        source.sendSuccess(()-> Component.literal("Get or set distance setting of dimension(s)."),false);
        return Command.SINGLE_SUCCESS;
    }
    private static int getDimensionLevel(CommandSourceStack source, ServerLevel dimension)
    {
        StateSaver stateSaver=StateSaver.getServerState(dimension.getServer());
        source.sendSuccess(()-> Component.literal(format("view: %d, sim: %d",
                stateSaver.ext.view.getOrDefault(dimension.dimension().identifier(),dimension.getServer().getPlayerList().getViewDistance()),
                stateSaver.ext.sim.getOrDefault(dimension.dimension().identifier(),dimension.getServer().getPlayerList().getSimulationDistance()))),false);
        return Command.SINGLE_SUCCESS;
    }
    private static int setDimensionLevelSim(CommandSourceStack source, ServerLevel dimension, int level)
    {
        getServerState(source.getServer()).ext.sim.put(dimension.dimension().identifier(), level);
        source.sendSuccess(()-> Component.literal("Done."),false);
        return Command.SINGLE_SUCCESS;
    }
    private static int setDimensionLevelView(CommandSourceStack source, ServerLevel dimension, int level)
    {
        getServerState(source.getServer()).ext.view.put(dimension.dimension().identifier(), level);
        source.sendSuccess(()-> Component.literal("Done."),false);
        return Command.SINGLE_SUCCESS;
    }
}
