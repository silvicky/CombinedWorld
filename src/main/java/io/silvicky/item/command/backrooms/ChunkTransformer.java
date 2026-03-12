package io.silvicky.item.command.backrooms;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.silvicky.item.backrooms.VecTransformer;
import io.silvicky.item.command.suggestion.TransformerSuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;

import static io.silvicky.item.StateSaver.getServerState;
import static io.silvicky.item.common.Util.*;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class ChunkTransformer
{
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher)
    {
        dispatcher.register(
                literal("ctrans")
                        .requires(context-> context.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS)))
                        .executes(context->help(context.getSource()))
                        .then(argument(DIMENSION, DimensionArgument.dimension())
                                .executes(ctx->getDimensionTrans(ctx.getSource(), DimensionArgument.getDimension(ctx,DIMENSION)))
                                        .then(argument(LEVEL, StringArgumentType.word())
                                                .suggests(new TransformerSuggestionProvider())
                                                .executes(ctx->setDimensionTrans(ctx.getSource(), DimensionArgument.getDimension(ctx,DIMENSION), StringArgumentType.getString(ctx,LEVEL)))))
                        );
    }
    private static int help(CommandSourceStack source)
    {
        source.sendSuccess(()-> Component.literal("Usage:"),false);
        source.sendSuccess(()-> Component.literal("/ctrans <dimension> <level>"),false);
        source.sendSuccess(()-> Component.literal("Get or set chunk transformer of dimension(s)."),false);
        return Command.SINGLE_SUCCESS;
    }
    private static int getDimensionTrans(CommandSourceStack source, ServerLevel dimension)
    {
        source.sendSuccess(()-> Component.literal(getServerState(source.getServer()).chunkTransformer.getOrDefault(dimension.dimension.identifier(),"nop")),false);
        return Command.SINGLE_SUCCESS;
    }
    private static int setDimensionTrans(CommandSourceStack source, ServerLevel dimension, String level)
    {
        if(!VecTransformer.registry.containsKey(level))level="nop";
        getServerState(source.getServer()).chunkTransformer.put(dimension.dimension().identifier(), level);
        source.sendSuccess(()-> Component.literal("Done."),false);
        return Command.SINGLE_SUCCESS;
    }
}
