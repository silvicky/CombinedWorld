package io.silvicky.item;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.dimension.v1.FabricDimensions;
import net.minecraft.command.argument.DimensionArgumentType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.GameStateChangeS2CPacket;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.TeleportTarget;

import java.util.Objects;
import java.util.Optional;

import static io.silvicky.item.InventoryManager.*;
import static io.silvicky.item.ItemStorage.LOGGER;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class Warp {
    public static final String DIMENSION="dimension";
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher)
    {
        dispatcher.register(
                literal("warp")
                        .then(argument(DIMENSION, DimensionArgumentType.dimension())
                            .executes(context -> warp(context.getSource(),DimensionArgumentType.getDimensionArgument(context,"dimension")))));
    }
    public static int warp(ServerCommandSource source, ServerWorld dimension)
    {
        ServerPlayerEntity player=source.getPlayer();
        LOGGER.info(Objects.requireNonNull(source.getPlayer()).getName().getString()+" goes to "+getDimensionId(dimension));
        ServerWorld spw=source.getServer().getWorld(player.getSpawnPointDimension());
        BlockPos sp=player.getSpawnPointPosition();
        if(spw==null||sp==null){spw=source.getServer().getOverworld();sp=source.getServer().getOverworld().getSpawnPos();}
        if(!getDimensionId(dimension).equals(getDimensionId(source.getWorld())))
        {
            LOGGER.info("Changed inventory!");
            save(source.getServer(),player);
            load(source.getServer(),player,dimension);
        }
        if(source.getServer().getPermissionLevel(player.getGameProfile())<2)
        {
            player.interactionManager.changeGameMode(GameMode.SURVIVAL);
            player.networkHandler.sendPacket(new GameStateChangeS2CPacket(GameStateChangeS2CPacket.GAME_MODE_CHANGED, GameMode.SURVIVAL.getId()));
        }
        if(getDimensionId(spw).equals(getDimensionId(dimension)))
        {
            Optional<Vec3d> v=PlayerEntity.findRespawnPosition(spw,sp,0,false,true);
            if(v.isPresent())
            {
                TeleportTarget target = new TeleportTarget(v.get(), new Vec3d(1, 1, 1), 0f, 0f);
                FabricDimensions.teleport(player, spw, target);
                return Command.SINGLE_SUCCESS;
            }
        }
        sp=dimension.getSpawnPos();
        while(dimension.getBlockState(sp).isAir())sp=sp.down();
        sp=sp.up();
        TeleportTarget target = new TeleportTarget(new Vec3d(sp.getX(), sp.getY(), sp.getZ()), new Vec3d(1, 1, 1), 0f, 0f);
        FabricDimensions.teleport(player, dimension, target);
        return Command.SINGLE_SUCCESS;
    }
}
