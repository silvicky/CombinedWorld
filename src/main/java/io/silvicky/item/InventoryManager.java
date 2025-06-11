package io.silvicky.item;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.network.packet.s2c.play.GameStateChangeS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.TeleportTarget;

import java.util.Iterator;

import static io.silvicky.item.common.Util.*;
import static io.silvicky.item.cfg.JSONConfig.useStorage;

public class InventoryManager {

    public static void savePos(ServerPlayerEntity player, StateSaver stateSaver)
    {
        savePos(player,stateSaver,player.getServerWorld().getRegistryKey().getValue().toString());
    }
    public static void savePos(ServerPlayerEntity player, StateSaver stateSaver, String fakeDimension)
    {
        stateSaver.posList.add(new StateSaver.PositionInfo
                (
                        player.getUuidAsString(),
                        getDimensionId(fakeDimension),
                        fakeDimension,
                        player.getPos()
                ));
    }

    public static void saveInventory(ServerPlayerEntity player,StateSaver stateSaver)
    {
        saveInventory(player,stateSaver,false,player.getServerWorld().getRegistryKey().getValue().toString());
    }
    public static void saveInventory(ServerPlayerEntity player,StateSaver stateSaver,boolean tmp,String fakeDimension)
    {
        stateSaver.nbtList.add(new StateSaver.StorageInfo
                (
                        player.getUuidAsString(),
                        fakeDimension.substring(0,fakeDimension.indexOf(':')),
                        inventoryToStack(player.getInventory()),
                        enderToStack(player.getEnderChestInventory()),
                        player.totalExperience,
                        player.getHealth(),
                        player.getHungerManager().getFoodLevel(),
                        player.getHungerManager().getSaturationLevel(),
                        player.getAir(),
                        player.interactionManager.getGameMode().getIndex()
                ));
        if(tmp)return;
        player.getInventory().clear();
        player.getEnderChestInventory().clear();
        player.setExperiencePoints(0);
        player.setHealth(20.0F);
        player.getHungerManager().setFoodLevel(20);
        player.getHungerManager().setSaturationLevel(5.0F);
        player.interactionManager.changeGameMode(GameMode.SURVIVAL);
        player.networkHandler.sendPacket(new GameStateChangeS2CPacket(GameStateChangeS2CPacket.GAME_MODE_CHANGED, GameMode.SURVIVAL.getIndex()));
    }
    public static void loadPos(MinecraftServer server,ServerPlayerEntity player,ServerWorld targetDimension,StateSaver stateSaver) throws CommandSyntaxException {
        targetDimension= toOverworld(server,targetDimension);
        Iterator<StateSaver.PositionInfo> iterator=stateSaver.posList.iterator();
        StateSaver.PositionInfo n=null;
        while (iterator.hasNext())
        {
            StateSaver.PositionInfo nt=iterator.next();
            if(!(nt.player.equals(player.getUuidAsString())&&nt.dimension.equals(getDimensionId(targetDimension))))continue;
            iterator.remove();
            LOGGER.info("Fetched position data!");
            if(n!=null)LOGGER.warn("Duplicated data found! Discarding old data, but this should not happen...");
            n=nt;
        }
        if(n==null)
        {
            LOGGER.info("Entering a new world... Good luck to the pioneer!");
            BlockPos sp= transLoc(targetDimension.getSpawnPos().withY(targetDimension.getLogicalHeight()-1),targetDimension);
            TeleportTarget.PostDimensionTransition postDimensionTransition=TeleportTarget.NO_OP;
            TeleportTarget target = new TeleportTarget(targetDimension,sp.toCenterPos(), Vec3d.ZERO, 0f, 0f,postDimensionTransition);
            player.teleportTo(target);
        }
        else
        {
            String dim=n.rdim;
            ServerWorld sw2=server.getWorld(RegistryKey.of(RegistryKey.ofRegistry(targetDimension.getRegistryKey().getRegistry()),
                    Identifier.of(targetDimension.getRegistryKey().getValue().getNamespace(),
                            dim.substring(dim.indexOf(":")+1))));
            if(sw2==null)
            {
                LOGGER.error("A dimension named "+dim+" is NOT FOUND!");
                throw ERR_DIMENSION_NOT_FOUND.create();
            }
            Vec3d v3d=n.pos;
            BlockPos sp=new BlockPos((int) Math.floor(v3d.x), (int) Math.floor(v3d.y), (int) Math.floor(v3d.z));

            TeleportTarget.PostDimensionTransition postDimensionTransition=TeleportTarget.NO_OP;

            TeleportTarget target = new TeleportTarget(sw2,sp.toCenterPos(), Vec3d.ZERO, 0f, 0f,postDimensionTransition);
            player.teleportTo(target);
        }
    }
    public static void loadInventory(ServerPlayerEntity player,ServerWorld targetDimension,StateSaver stateSaver) throws CommandSyntaxException {
        Iterator<StateSaver.StorageInfo> iterator=stateSaver.nbtList.iterator();
        StateSaver.StorageInfo n=null;
        while (iterator.hasNext())
        {
            StateSaver.StorageInfo nt=iterator.next();
            if(!(nt.player.equals(player.getUuidAsString())
                    &&nt.dimension.equals(targetDimension.getRegistryKey().getValue().getNamespace())))
                    continue;
            iterator.remove();
            LOGGER.info("Fetched inventory!");
            if(n!=null)LOGGER.warn("Duplicated data found! Discarding old data, but this should not happen...");
            n=nt;
        }
        if(n!=null)
        {
            try{
                stackToInventory(player.getInventory(),n.inventory);
            stackToEnder(player.getEnderChestInventory(),n.ender);}
            catch(Exception e)
            {
                LOGGER.error(e.getMessage());
                stateSaver.nbtList.add(n);
                throw ERR_ITEM.create();
            }
            player.setExperiencePoints(n.xp);
            player.setHealth(n.hp);
            player.getHungerManager().setFoodLevel(n.food);
            player.getHungerManager().setSaturationLevel(n.food2);
            player.setAir(n.air);
            player.interactionManager.changeGameMode(GameMode.byIndex(n.gamemode));
            player.networkHandler.sendPacket(new GameStateChangeS2CPacket(GameStateChangeS2CPacket.GAME_MODE_CHANGED, n.gamemode));
        }
        else
        {
            player.getInventory().clear();
            player.getEnderChestInventory().clear();
            player.setExperiencePoints(0);
            player.setHealth(20);
            player.getHungerManager().setFoodLevel(20);
            player.getHungerManager().setSaturationLevel(20);
            player.setAir(300);
            player.interactionManager.changeGameMode(GameMode.SURVIVAL);
            player.networkHandler.sendPacket(new GameStateChangeS2CPacket(GameStateChangeS2CPacket.GAME_MODE_CHANGED, GameMode.SURVIVAL.getIndex()));
        }
    }

    public static void save(MinecraftServer server, ServerPlayerEntity player)
    {
        save(server,player,false,player.getServerWorld().getRegistryKey().getValue().toString());
    }
    public static void save(MinecraftServer server, ServerPlayerEntity player,boolean tmp,String fakeDimension)
    {
        StateSaver stateSaver=StateSaver.getServerState(server);
        savePos(player,stateSaver,fakeDimension);
        if(useStorage)saveInventory(player,stateSaver,tmp,fakeDimension);
    }
    public static void load(MinecraftServer server, ServerPlayerEntity player, ServerWorld targetDimension) throws CommandSyntaxException {
        StateSaver stateSaver=StateSaver.getServerState(server);
        if(useStorage)
        {
            loadInventory(player,targetDimension,stateSaver);
        }
        loadPos(server, player, targetDimension, stateSaver);
    }
    public static void directWarp(MinecraftServer server,ServerPlayerEntity player,ServerWorld targetDimension) throws CommandSyntaxException {
        StateSaver stateSaver=StateSaver.getServerState(server);
        savePos(player,stateSaver);
        loadPos(server, player, targetDimension, stateSaver);
    }
}
