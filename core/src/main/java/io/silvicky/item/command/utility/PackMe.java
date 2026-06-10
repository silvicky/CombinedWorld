package io.silvicky.item.command.utility;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

import java.util.List;

import static io.silvicky.item.common.Util.*;
import static net.minecraft.commands.Commands.literal;
public class PackMe
{
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher)
    {
        dispatcher.register(
                literal("packme")
                        .requires(source -> source.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS)))
                        .executes(context->help(context.getSource()))
                        .then(literal("inv")
                                .executes(context -> packInv(context.getSource().getPlayerOrException())))
                        .then(literal("all")
                                .executes(context -> packAll(context.getSource().getPlayerOrException()))));
    }
    private static int help(CommandSourceStack source)
    {
        source.sendSuccess(()-> Component.literal("Usage: /packme (inv|all)"),false);
        source.sendSuccess(()-> Component.literal("Pack your inventory into chests."),false);
        return Command.SINGLE_SUCCESS;
    }
    private static int packInv(ServerPlayer player)
    {
        try
        {
            stackToInventory(player.getInventory(), enId(pack(deId(inventoryToStack(player.getInventory())),INVENTORY_ITEMS)));
        }
        catch (Exception e){e.printStackTrace();}
        return Command.SINGLE_SUCCESS;
    }
    private static int packAll(ServerPlayer player)
    {
        try
        {
            List<ItemStack> tmp=pack(deId(inventoryToStack(player.getInventory())),INVENTORY_ITEMS);
            tmp.addAll(pack(deId(enderToStack(player.getEnderChestInventory())),ENDER_ITEMS));
            player.getEnderChestInventory().clearContent();
            stackToInventory(player.getInventory(), enId(tmp));
        }
        catch (Exception e){e.printStackTrace();}
        return Command.SINGLE_SUCCESS;
    }
}