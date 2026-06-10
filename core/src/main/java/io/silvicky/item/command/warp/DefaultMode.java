package io.silvicky.item.command.warp;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.silvicky.item.command.suggestion.GroupSuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.GameModeArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.level.GameType;

import static io.silvicky.item.StateSaver.getServerState;
import static io.silvicky.item.common.Util.*;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class DefaultMode
{
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher)
    {
        dispatcher.register(
                literal("defaultmode")
                        .requires(context-> context.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS)))
                        .executes(context->help(context.getSource()))
                        .then(argument(NAMESPACE, StringArgumentType.word())
                                .suggests(new GroupSuggestionProvider())
                                .executes(ctx->getDimensionLevel(ctx.getSource(), StringArgumentType.getString(ctx,NAMESPACE)))
                                .then(argument(LEVEL, GameModeArgument.gameMode())
                                        .executes(ctx->setDimensionLevel(ctx.getSource(), StringArgumentType.getString(ctx,NAMESPACE), GameModeArgument.getGameMode(ctx, LEVEL)))))
        );
    }
    private static int help(CommandSourceStack source)
    {
        source.sendSuccess(()-> Component.literal("Usage:"),false);
        source.sendSuccess(()-> Component.literal("/defaultmode <namespace> <level>"),false);
        source.sendSuccess(()-> Component.literal("Get or set default game mode of group."),false);
        return Command.SINGLE_SUCCESS;
    }
    private static int getDimensionLevel(CommandSourceStack source, String dimension)
    {
        source.sendSuccess(()-> Component.literal(GameType.byId(getServerState(source.getServer()).gamemode.getOrDefault(dimension, 0)).getName()),false);
        return Command.SINGLE_SUCCESS;
    }
    private static int setDimensionLevel(CommandSourceStack source, String dimension, GameType level)
    {
        getServerState(source.getServer()).gamemode.put(dimension, level.getId());
        source.sendSuccess(()-> Component.literal("Done."),false);
        return Command.SINGLE_SUCCESS;
    }
}
