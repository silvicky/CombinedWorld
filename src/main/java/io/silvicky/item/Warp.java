package io.silvicky.item;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.dimension.v1.FabricDimensions;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.impl.game.minecraft.launchwrapper.FabricServerTweaker;
import net.minecraft.block.EndPortalBlock;
import net.minecraft.command.argument.DimensionArgumentType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.packet.s2c.play.GameStateChangeS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.FabricUtil;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

import static io.silvicky.item.ItemStorage.LOGGER;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class Warp {
    public static final String ov="minecraft:overworld";
    public static final String mc="minecraft";
    public static final String dimen="dimension";
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher)
    {
        dispatcher.register(
                literal("warp")
                        .then(argument(dimen, DimensionArgumentType.dimension())
                            .executes(context -> warp(context.getSource(),DimensionArgumentType.getDimensionArgument(context,"dimension")))));
    }
    public static int warp(ServerCommandSource source, ServerWorld dimension)
    {
        ServerPlayerEntity player=source.getPlayer();
        RegistryKey<World> registryKey=dimension.getRegistryKey();
        String namespace=dimension.getRegistryKey().getValue().getNamespace();
        if(namespace.equals(mc))registryKey=ServerWorld.OVERWORLD;
        String id=dimension.getRegistryKey().getValue().getPath();
        LOGGER.info(Objects.requireNonNull(source.getPlayer()).getName().getString()+" goes to "+namespace+":"+id);
        RegistryKey<World> spw = player.getSpawnPointDimension();
        BlockPos sp=player.getSpawnPointPosition();
        if(spw==null||sp==null){spw=ServerWorld.OVERWORLD;sp=source.getServer().getOverworld().getSpawnPos();}
        String tarDim=dimension.getRegistryKey().getValue().toString();
        if(dimension.getRegistryKey().getValue().getNamespace().equals(mc)){tarDim=ov;dimension=source.getServer().getOverworld();}
        if(!(source.getWorld().getRegistryKey().getValue().equals(registryKey.getValue())||(source.getWorld().getRegistryKey().getValue().getNamespace().equals("minecraft")&&registryKey.getValue().getNamespace().equals("minecraft"))))
        {
            InventoryManager.save(source.getServer(),player);
            InventoryManager.load(source.getServer(),player,dimension);
        }
        if(source.getServer().getPermissionLevel(player.getGameProfile())<2)
        {
            player.interactionManager.changeGameMode(GameMode.SURVIVAL);
            player.networkHandler.sendPacket(new GameStateChangeS2CPacket(GameStateChangeS2CPacket.GAME_MODE_CHANGED, GameMode.SURVIVAL.getId()));
        }
        if(spw.getValue().toString().equals(tarDim)||(spw.getValue().getNamespace().equals(mc)&&tarDim.equals(ov)))
        {
            Optional<Vec3d> v=PlayerEntity.findRespawnPosition(source.getServer().getWorld(spw),sp,0,false,true);
            if(v.isPresent())
            {
                TeleportTarget target = new TeleportTarget(v.get(), new Vec3d(1, 1, 1), 0f, 0f);
                source.getServer().getPlayerManager().respawnPlayer(player,true);
                FabricDimensions.teleport(player, source.getServer().getWorld(spw), target);
                return Command.SINGLE_SUCCESS;
            }
        }
        sp=dimension.getSpawnPos();
        TeleportTarget target = new TeleportTarget(new Vec3d(sp.getX(), sp.getY(), sp.getZ()), new Vec3d(1, 1, 1), 0f, 0f);
        FabricDimensions.teleport(player, dimension, target);
        return Command.SINGLE_SUCCESS;
    }
}
