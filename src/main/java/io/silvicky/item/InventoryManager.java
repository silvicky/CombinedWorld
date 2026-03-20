package io.silvicky.item;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.portal.TeleportTransition;

import java.util.HashMap;

import static io.silvicky.item.cfg.JSONConfig.useStorage;
import static io.silvicky.item.common.Util.*;

public class InventoryManager {

    public static void savePos(ServerPlayer player, StateSaver stateSaver)
    {
        savePos(player,stateSaver,player.level().dimension().identifier());
    }
    public static void savePos(ServerPlayer player, StateSaver stateSaver, Identifier fakeDimension)
    {
        stateSaver.posMap
                .computeIfAbsent(getDimensionId(fakeDimension),i->new HashMap<>())
                .put(player.getStringUUID(),new StateSaver.PositionInfoNew(
                        fakeDimension,
                        player.position(),
                        player.getDeltaMovement(),
                        player.getYRot(),
                        player.getXRot()));
    }

    public static void saveInventory(ServerPlayer player, StateSaver stateSaver)
    {
        saveInventory(player,stateSaver,player.level().dimension().identifier());
    }
    public static void saveInventory(ServerPlayer player, StateSaver stateSaver, Identifier fakeDimension)
    {
        stateSaver.savedMap.computeIfAbsent(fakeDimension.getNamespace(),i->new HashMap<>())
                .put(player.getStringUUID(),new StateSaver.StorageInfoNew
                        (
                        inventoryToStack(player.getInventory()),
                        enderToStack(player.getEnderChestInventory()),
                        player.totalExperience,
                        player.getHealth(),
                        player.getFoodData().getFoodLevel(),
                        player.getFoodData().getSaturationLevel(),
                        player.getAirSupply(),
                        player.gameMode.getGameModeForPlayer().getId()
                ));
    }
    public static void loadPos(MinecraftServer server, ServerPlayer player, ServerLevel targetDimension, StateSaver stateSaver) throws CommandSyntaxException {
        targetDimension= toOverworld(server,targetDimension);
        StateSaver.PositionInfoNew n=stateSaver.posMap
                .computeIfAbsent(getDimensionId(targetDimension.dimension().identifier()), i->new HashMap<>())
                .get(player.getStringUUID());
        if(n==null)
        {
            LOGGER.info("Entering a new world... Good luck to the pioneer!");
            BlockPos sp=player.adjustSpawnLocation(targetDimension,targetDimension.getRespawnData().pos());
            TeleportTransition.PostTeleportTransition postDimensionTransition= TeleportTransition.DO_NOTHING;
            TeleportTransition target = new TeleportTransition(targetDimension,sp.getCenter(), Vec3.ZERO, 0f, 0f,postDimensionTransition);
            if(player.connection !=null)player.teleport(target);
            else fakeTeleportTo(player,target,stateSaver);
        }
        else
        {
            ServerLevel sw2=server.getLevel(ResourceKey.create(Registries.DIMENSION, n.dimension()));
            if(sw2==null)
            {
                LOGGER.error("A dimension named "+n.dimension()+" is NOT FOUND!");
                throw ERR_DIMENSION_NOT_FOUND.create();
            }
            TeleportTransition target = new TeleportTransition(sw2,n.pos(), n.velocity(), n.yaw(), n.pitch(), TeleportTransition.DO_NOTHING);
            if(player.connection !=null)player.teleport(target);
            else fakeTeleportTo(player,target,stateSaver);
        }
    }
    public static void loadInventory(ServerPlayer player, ServerLevel targetDimension, StateSaver stateSaver) throws CommandSyntaxException {
        StateSaver.StorageInfoNew n=stateSaver.savedMap
                .computeIfAbsent(targetDimension.dimension().identifier().getNamespace(), i->new HashMap<>())
                .get(player.getStringUUID());
        if(n!=null)
        {
            try{
                stackToInventory(player.getInventory(),n.inventory());
            stackToEnder(player.getEnderChestInventory(),n.ender());}
            catch(Exception e)
            {
                LOGGER.error(e.getMessage());
                throw ERR_ITEM.create();
            }
            player.setExperiencePoints(n.xp());
            player.setHealth(n.hp());
            player.getFoodData().setFoodLevel(n.food());
            player.getFoodData().setSaturation(n.saturation());
            player.setAirSupply(n.air());
            player.gameMode.changeGameModeForPlayer(GameType.byId(n.gamemode()));
            if(player.connection !=null)player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.CHANGE_GAME_MODE, n.gamemode()));
        }
        else
        {
            player.getInventory().clearContent();
            player.getEnderChestInventory().clearContent();
            player.setExperiencePoints(0);
            player.setHealth(20);
            player.getFoodData().setFoodLevel(20);
            player.getFoodData().setSaturation(20);
            player.setAirSupply(300);
            int gamemode=stateSaver.gamemode.getOrDefault(targetDimension.dimension().identifier().getNamespace(),0);
            player.gameMode.changeGameModeForPlayer(GameType.byId(gamemode));
            if(player.connection !=null)player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.CHANGE_GAME_MODE, gamemode));
        }
    }
    public static void save(MinecraftServer server, ServerPlayer player, Identifier fakeDimension)
    {
        StateSaver stateSaver=StateSaver.getServerState(server);
        savePos(player,stateSaver,fakeDimension);
        if(useStorage)saveInventory(player,stateSaver,fakeDimension);
    }
}
