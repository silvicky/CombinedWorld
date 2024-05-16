package io.silvicky.item;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.command.argument.DimensionArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

import java.util.Objects;

import static io.silvicky.item.InventoryManager.*;
import static io.silvicky.item.ItemStorage.LOGGER;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class Warp {
    public static final SimpleCommandExceptionType ERR_DIMENSION_NOT_FOUND=new SimpleCommandExceptionType(new LiteralMessage("Target dimension NOT FOUND!"));
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher)
    {
        dispatcher.register(
                literal("warp")
                        .then(argument(DIMENSION, DimensionArgumentType.dimension())
                            .executes(context -> warp(context.getSource(),DimensionArgumentType.getDimensionArgument(context,"dimension")))));
    }
    public static int warp(ServerCommandSource source, ServerWorld dimension) throws CommandSyntaxException {
        ServerPlayerEntity player=source.getPlayer();
        assert player != null;
        if(!getDimensionId(dimension).equals(getDimensionId(source.getWorld())))
        {
            LOGGER.info(Objects.requireNonNull(source.getPlayer()).getName().getString()+" goes to "+getDimensionId(dimension));
            if(!dimension.getRegistryKey().getValue().getNamespace().equals(source.getWorld().getRegistryKey().getValue().getNamespace()))
            {
                player.clearStatusEffects();
                save(source.getServer(),player);
                if(!load(source.getServer(),player,dimension))
                {
                    loadInventory(source.getServer(),player,source.getWorld(),StateSaver.getServerState(source.getServer()));
                    throw ERR_DIMENSION_NOT_FOUND.create();
                }
            }
            else
            {
                if(!directWarp(source.getServer(),player,dimension))throw ERR_DIMENSION_NOT_FOUND.create();
            }
            source.sendFeedback(()-> Text.literal("Teleported to "+getDimensionId(dimension)+"!"),false);
        }
        else
        {
            source.sendFeedback(()->Text.literal("Nothing happened."),false);
        }
        return Command.SINGLE_SUCCESS;
    }
}
