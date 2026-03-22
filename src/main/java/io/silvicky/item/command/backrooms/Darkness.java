package io.silvicky.item.command.backrooms;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.HexColorArgument;
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

public class Darkness
{
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher)
    {
        dispatcher.register(
                literal("darkness")
                        .requires(context-> context.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS)))
                        .executes(context->help(context.getSource()))
                        .then(argument(DIMENSION, DimensionArgument.dimension())
                                .executes(ctx->getDimensionLevel(ctx.getSource(), DimensionArgument.getDimension(ctx,DIMENSION)))
                                .then(argument(LEVEL, HexColorArgument.hexColor())
                                        .executes(ctx->setDimensionLevel(ctx.getSource(), DimensionArgument.getDimension(ctx,DIMENSION), HexColorArgument.getHexColor(ctx, LEVEL)))))
        );
    }
    private static int help(CommandSourceStack source)
    {
        source.sendSuccess(()-> Component.literal("Usage:"),false);
        source.sendSuccess(()-> Component.literal("/darkness <dimension> <level>"),false);
        source.sendSuccess(()-> Component.literal("Get or set darkness setting of dimension(s)."),false);
        return Command.SINGLE_SUCCESS;
    }
    private static int getDimensionLevel(CommandSourceStack source, ServerLevel dimension)
    {
        source.sendSuccess(()-> Component.literal(format("%06X",getServerState(source.getServer()).darkness.getOrDefault(dimension.dimension().identifier(), 0)&0xFFFFFF)),false);
        return Command.SINGLE_SUCCESS;
    }
    private static int setDimensionLevel(CommandSourceStack source, ServerLevel dimension, int level)
    {
        getServerState(source.getServer()).darkness.put(dimension.dimension().identifier(), level);
        source.sendSuccess(()-> Component.literal("Done."),false);
        return Command.SINGLE_SUCCESS;
    }
}
