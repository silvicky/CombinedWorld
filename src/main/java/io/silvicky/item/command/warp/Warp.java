package io.silvicky.item.command.warp;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.Message;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import io.silvicky.item.StateSaver;
import net.minecraft.command.argument.DimensionArgumentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Objects;
import java.util.function.Function;

import static io.silvicky.item.InventoryManager.*;
import static io.silvicky.item.ItemStorage.LOGGER;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class Warp {
    public static final SimpleCommandExceptionType ERR_DIMENSION_NOT_FOUND=new SimpleCommandExceptionType(new LiteralMessage("Target dimension NOT FOUND!"));
    public static final SimpleCommandExceptionType ERR_ITEM=new SimpleCommandExceptionType(new LiteralMessage("Item stack error(from version change, contact your admin)!"));
    public static final SimpleCommandExceptionType ERR_NOT_BY_PLAYER=new SimpleCommandExceptionType(new LiteralMessage("This command must be executed by a player."));
    public static final SimpleCommandExceptionType ERR_NOT_ONE_PLAYER=new SimpleCommandExceptionType(new LiteralMessage("Amount of player selected must be exactly one."));
    public static final DynamicCommandExceptionType ERR_WARP_FORBIDDEN=new DynamicCommandExceptionType(new Function<>() {
        /**
         * Applies this function to the given argument.
         *
         * @param o the function argument
         * @return the function result
         */
        @Override
        public Message apply(Object o) {
            return new LiteralMessage("Warp forbidden! Reason: "+o);
        }
    });
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher)
    {
        dispatcher.register(
                literal("warp")
                        .executes(context->help(context.getSource()))
                                .then(argument(DIMENSION, DimensionArgumentType.dimension())
                                        .executes(context -> warp(context.getSource(),context.getSource().getPlayer(),DimensionArgumentType.getDimensionArgument(context,DIMENSION)))));
    }
    private static int help(ServerCommandSource source)
    {
        source.sendFeedback(()-> Text.literal("Usage: /warp <dimension>"),false);
        source.sendFeedback(()-> Text.literal("Go to that world."),false);
        source.sendFeedback(()-> Text.literal("Only works when executed by a player."),false);
        return Command.SINGLE_SUCCESS;
    }
    public static int warp(ServerCommandSource source, ServerPlayerEntity player, ServerWorld dimension) throws CommandSyntaxException
    {
        if(player==null) throw ERR_NOT_BY_PLAYER.create();
        ServerWorld from=player.getServerWorld();
        MinecraftServer server=source.getServer();
        StateSaver stateSaver=StateSaver.getServerState(server);
        StateSaver.WarpRestrictionInfo info=stateSaver.restrictionInfoHashMap.get(Identifier.of(getDimensionId(dimension)));
        if(info!=null&&!source.hasPermissionLevel(info.level))
        {
            throw ERR_WARP_FORBIDDEN.create(info.reason);
        }
        if(!getDimensionId(dimension).equals(getDimensionId(from)))
        {
            LOGGER.info(player.getName().getString()+" goes to "+getDimensionId(dimension));
            if(!dimension.getRegistryKey().getValue().getNamespace().equals(from.getRegistryKey().getValue().getNamespace()))
            {
                player.clearStatusEffects();
                save(server,player);
                try{load(server,player,dimension);}
                catch(Exception e)
                {
                    loadInventory(player,from, StateSaver.getServerState(server));
                    throw e;
                }
            }
            else
            {
                directWarp(server,player,dimension);
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
