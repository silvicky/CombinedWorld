package io.silvicky.item.command.backrooms;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.silvicky.item.backrooms.EntityVisibilityLevel;
import io.silvicky.item.command.suggestion.EntityVisibilityLevelSuggestionProvider;
import net.minecraft.command.argument.DimensionArgumentType;
import net.minecraft.command.permission.Permission;
import net.minecraft.command.permission.PermissionLevel;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

import static io.silvicky.item.StateSaver.getServerState;
import static io.silvicky.item.common.Util.DIMENSION;
import static io.silvicky.item.common.Util.LEVEL;
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
                                        .then(argument(LEVEL, StringArgumentType.word())
                                                .suggests(new EntityVisibilityLevelSuggestionProvider())
                                                .executes(ctx->setDimensionLevel(ctx.getSource(),DimensionArgumentType.getDimensionArgument(ctx,DIMENSION),StringArgumentType.getString(ctx, LEVEL))))))
                        );
    }
    private static int help(ServerCommandSource source)
    {
        source.sendFeedback(()-> Text.literal("Usage:"),false);
        source.sendFeedback(()-> Text.literal("/visibility world <dimension> <level>"),false);
        source.sendFeedback(()-> Text.literal("Get or set entity visibility of dimension."),false);
        source.sendFeedback(()-> Text.literal("/visibility player <namespace> <player> <level>"),false);
        source.sendFeedback(()-> Text.literal("Get or set player visibility field of group."),false);
        source.sendFeedback(()-> Text.literal("/visibility init <namespace>"),false);
        source.sendFeedback(()-> Text.literal("Reset player visibility fields of group."),false);
        return Command.SINGLE_SUCCESS;
    }
    private static int getDimensionLevel(ServerCommandSource source, ServerWorld dimension)
    {
        source.sendFeedback(()->Text.literal(getServerState(source.getServer()).entityVisibility.getOrDefault(dimension.getRegistryKey().getValue(), EntityVisibilityLevel.NORMAL).toString()),false);
        return Command.SINGLE_SUCCESS;
    }
    private static int setDimensionLevel(ServerCommandSource source, ServerWorld dimension, String levelName)
    {
        getServerState(source.getServer()).entityVisibility.put(dimension.getRegistryKey().getValue(), EntityVisibilityLevel.getByName(levelName));
        source.sendFeedback(()->Text.literal("Done."),false);
        return Command.SINGLE_SUCCESS;
    }
}
