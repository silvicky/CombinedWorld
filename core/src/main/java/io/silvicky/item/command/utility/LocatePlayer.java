package io.silvicky.item.command.utility;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.players.NameAndId;

import java.util.Collection;

import static io.silvicky.item.command.warp.WarpTp.profileListToPlayer;
import static io.silvicky.item.common.Util.PLAYER;
import static java.lang.String.format;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class LocatePlayer
{
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher)
    {
        dispatcher.register(
                literal("locateplayer")
                        .requires(context-> context.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS)))
                        .executes(context->help(context.getSource()))
                        .then(argument(PLAYER, GameProfileArgument.gameProfile())
                                .executes(ctx->locatePlayer(ctx.getSource(),GameProfileArgument.getGameProfiles(ctx,PLAYER))))
        );
    }
    private static int help(CommandSourceStack source)
    {
        source.sendSuccess(()-> Component.literal("Usage:"),false);
        source.sendSuccess(()-> Component.literal("/locateplayer <player>"),false);
        source.sendSuccess(()-> Component.literal("Locate a player."),false);
        return Command.SINGLE_SUCCESS;
    }
    private static int locatePlayer(CommandSourceStack source, Collection<NameAndId> profileList) throws CommandSyntaxException
    {
        ServerPlayer player=profileListToPlayer(source.getServer(),profileList);
        source.sendSuccess(()-> Component.literal(format("%s, %s", player.level().dimension.identifier(), player.position())),false);
        return Command.SINGLE_SUCCESS;
    }
}
