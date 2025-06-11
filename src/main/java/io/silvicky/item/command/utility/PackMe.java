package io.silvicky.item.command.utility;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;

import static net.minecraft.server.command.CommandManager.literal;
import static io.silvicky.item.common.Util.*;
public class PackMe
{
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher)
    {
        dispatcher.register(
                literal("packme")
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(context->help(context.getSource()))
                        .then(literal("inv")
                                .executes(context -> packInv(context.getSource().getPlayerOrThrow())))
                        .then(literal("all")
                                .executes(context -> packAll(context.getSource().getPlayerOrThrow()))));
    }
    private static int help(ServerCommandSource source)
    {
        source.sendFeedback(()-> Text.literal("Usage: /packme (inv|all)"),false);
        source.sendFeedback(()-> Text.literal("Pack your inventory into chests."),false);
        return Command.SINGLE_SUCCESS;
    }
    public static int packInv(ServerPlayerEntity player)
    {
        try
        {
            stackToInventory(player.getInventory(), enId(pack(deId(inventoryToStack(player.getInventory())),INVENTORY_ITEMS)));
        }
        catch (Exception e){e.printStackTrace();}
        return Command.SINGLE_SUCCESS;
    }
    public static int packAll(ServerPlayerEntity player)
    {
        try
        {
            List<ItemStack> tmp=pack(deId(inventoryToStack(player.getInventory())),INVENTORY_ITEMS);
            tmp.addAll(pack(deId(enderToStack(player.getEnderChestInventory())),ENDER_ITEMS));
            player.getEnderChestInventory().clear();
            stackToInventory(player.getInventory(), enId(tmp));
        }
        catch (Exception e){e.printStackTrace();}
        return Command.SINGLE_SUCCESS;
    }
}