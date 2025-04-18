package io.silvicky.item;

import com.mojang.datafixers.util.Pair;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.EnderChestInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static io.silvicky.item.ItemStorage.LOGGER;
import static io.silvicky.item.cfg.JSONConfig.useStorage;

public class InventoryManager {
    public static final String DIMENSION="dimension";
    public static final String PLAYER="player";
    public static final String OVERWORLD="overworld";
    public static final String NETHER="the_nether";
    public static final String END="the_end";
    public static String getDimensionId(ServerWorld world)
    {
        String id=world.getRegistryKey().getValue().toString();
        if(id.endsWith(NETHER))id=id.substring(0,id.length()-10)+OVERWORLD;
        if(id.endsWith(END))id=id.substring(0,id.length()-7)+OVERWORLD;
        return id;
    }

    public static BlockPos transLoc(BlockPos sp,ServerWorld sw)
    {
        while((!sw.getBlockState(sp).isAir())||(!sw.getBlockState(sp.up()).isAir()))sp=sp.down();
        while(sw.getBlockState(sp.down()).isAir()&&sp.getY()>sw.getBottomY())sp=sp.down();
        if(sp.getY()==sw.getBottomY())
        {
            sp=sp.withY(sw.getLogicalHeight());
            LOGGER.warn("Spawn point not found!");
        }
        return sp;
    }
    public static void savePos(ServerPlayerEntity player, StateSaver stateSaver)
    {
        stateSaver.posList.add(new PositionInfo
                (
                        player.getUuidAsString(),
                        getDimensionId(player.getServerWorld()),
                        player.getServerWorld().getRegistryKey().getValue().toString(),
                        player.getPos()
                ));
    }
    public static ArrayList<Pair<ItemStack,Byte>> inventoryToStack(PlayerInventory inventory)
    {
        ArrayList<Pair<ItemStack,Byte>> ret=new ArrayList<>();
        for (int i = 0; i < inventory.main.size(); i++) {
            if (!inventory.main.get(i).isEmpty()) {
                ret.add(new Pair<>(inventory.main.get(i),(byte)i));
            }
        }
        return ret;
    }
    public static ArrayList<Pair<ItemStack,Byte>> enderToStack(EnderChestInventory inventory)
    {
        ArrayList<Pair<ItemStack,Byte>> ret=new ArrayList<>();
        for(int i = 0; i < inventory.size(); ++i) {
            ItemStack itemStack = inventory.getStack(i);
            if (!itemStack.isEmpty()) {
                ret.add(new Pair<>(itemStack,(byte) i));
            }
        }
        return ret;
    }
    public static void stackToInventory(PlayerInventory inventory,List<Pair<ItemStack,Byte>> stack)
    {
        inventory.main.clear();

        for (Pair<ItemStack, Byte> pair : stack) {
            int j = pair.getSecond();
            ItemStack itemStack = pair.getFirst();
            if (j < inventory.main.size()) {
                inventory.setStack(j, itemStack);
            }
        }
    }
    public static void stackToEnder(EnderChestInventory inventory,List<Pair<ItemStack,Byte>> stack)
    {
        for(int i = 0; i < inventory.size(); ++i) {
            inventory.setStack(i, ItemStack.EMPTY);
        }

        for (Pair<ItemStack, Byte> pair : stack) {
            int j = pair.getSecond();
            if (j < inventory.size()) {
                inventory.setStack(j, pair.getFirst());
            }
        }
    }
    public static void saveInventory(ServerPlayerEntity player,StateSaver stateSaver)
    {
        stateSaver.nbtList.add(new StorageInfo
                (
                        player.getUuidAsString(),
                        player.getServerWorld().getRegistryKey().getValue().getNamespace(),
                        inventoryToStack(player.getInventory()),
                        enderToStack(player.getEnderChestInventory()),
                        player.totalExperience,
                        player.getHealth(),
                        player.getHungerManager().getFoodLevel(),
                        player.getHungerManager().getSaturationLevel(),
                        player.getAir(),
                        player.interactionManager.getGameMode().getIndex()
                ));
        player.getInventory().clear();
        player.getEnderChestInventory().clear();
        player.setExperiencePoints(0);
        player.setHealth(20.0F);
        player.getHungerManager().setFoodLevel(20);
        player.getHungerManager().setSaturationLevel(5.0F);
        player.interactionManager.changeGameMode(GameMode.SURVIVAL);
        player.networkHandler.sendPacket(new GameStateChangeS2CPacket(GameStateChangeS2CPacket.GAME_MODE_CHANGED, GameMode.SURVIVAL.getIndex()));
    }
    public static boolean loadPos(MinecraftServer server,ServerPlayerEntity player,ServerWorld targetDimension,StateSaver stateSaver)
    {
        targetDimension=toOverworld(server,targetDimension);
        Iterator<PositionInfo> iterator=stateSaver.posList.iterator();
        PositionInfo n=null;
        while (iterator.hasNext())
        {
            PositionInfo nt=iterator.next();
            if(!(nt.player.equals(player.getUuidAsString())&&nt.dimension.equals(getDimensionId(targetDimension))))continue;
            iterator.remove();
            LOGGER.info("Fetched position data!");
            if(n!=null)LOGGER.warn("Duplicated data found! Discarding old data, but this should not happen...");
            n=nt;
        }
        if(n==null)
        {
            LOGGER.info("Entering a new world... Good luck to the pioneer!");
            BlockPos sp=transLoc(targetDimension.getSpawnPos().withY(targetDimension.getLogicalHeight()-1),targetDimension);
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
                return false;
            }
            Vec3d v3d=n.pos;
            BlockPos sp=new BlockPos((int) Math.floor(v3d.x), (int) Math.floor(v3d.y), (int) Math.floor(v3d.z));

            TeleportTarget.PostDimensionTransition postDimensionTransition=TeleportTarget.NO_OP;

            TeleportTarget target = new TeleportTarget(sw2,sp.toCenterPos(), Vec3d.ZERO, 0f, 0f,postDimensionTransition);
            player.teleportTo(target);
        }
        return true;
    }
    public static void loadInventory(ServerPlayerEntity player,ServerWorld targetDimension,StateSaver stateSaver)
    {
        Iterator<StorageInfo> iterator=stateSaver.nbtList.iterator();
        StorageInfo n=null;
        while (iterator.hasNext())
        {
            StorageInfo nt=iterator.next();
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
            stackToInventory(player.getInventory(),n.inventory);
            stackToEnder(player.getEnderChestInventory(),n.ender);
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
    public static ServerWorld toOverworld(MinecraftServer server,ServerWorld world)
    {
        String overworldId=getDimensionId(world);
        ServerWorld sw=server.getWorld(RegistryKey.of(RegistryKey.ofRegistry(world.getRegistryKey().getRegistry()),
                Identifier.of(world.getRegistryKey().getValue().getNamespace(),
                        overworldId.substring(overworldId.indexOf(":")+1))));
        return (sw!=null?sw:world);
    }
    public static void save(MinecraftServer server, ServerPlayerEntity player)
    {
        StateSaver stateSaver=StateSaver.getServerState(server);
        savePos(player,stateSaver);
        if(useStorage)saveInventory(player,stateSaver);
    }
    public static boolean load(MinecraftServer server, ServerPlayerEntity player, ServerWorld targetDimension)
    {
        StateSaver stateSaver=StateSaver.getServerState(server);
        if(useStorage)loadInventory(player,targetDimension,stateSaver);
        return loadPos(server, player, targetDimension, stateSaver);
    }
    public static boolean directWarp(MinecraftServer server,ServerPlayerEntity player,ServerWorld targetDimension)
    {
        StateSaver stateSaver=StateSaver.getServerState(server);
        savePos(player,stateSaver);
        return loadPos(server, player, targetDimension, stateSaver);
    }
}
