package io.silvicky.item;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Pair;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.EnderChestInventory;
import net.minecraft.item.ItemStack;
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
import static io.silvicky.item.command.warp.Warp.ERR_DIMENSION_NOT_FOUND;
import static io.silvicky.item.command.warp.Warp.ERR_ITEM;
import static io.silvicky.item.cfg.JSONConfig.useStorage;

public class InventoryManager {
    public static final String DIMENSION="dimension";
    public static final String PLAYER="player";
    public static final String OVERWORLD="overworld";
    public static final String NETHER="the_nether";
    public static final String END="the_end";
    public static final int playerInventorySize=41;
    public static String getDimensionId(ServerWorld world)
    {
        return getDimensionId(world.getRegistryKey().getValue().toString());
    }
    public static String getDimensionId(String id)
    {
        if(id.endsWith(NETHER))id=id.substring(0,id.length()-NETHER.length())+OVERWORLD;
        if(id.endsWith(END))id=id.substring(0,id.length()-END.length())+OVERWORLD;
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
    public static ArrayList<Pair<ItemStack,Byte>> inventoryToStack(PlayerInventory inventory)
    {
        ArrayList<Pair<ItemStack,Byte>> ret=new ArrayList<>();
        for (int i = 0; i <playerInventorySize; i++) {
            if (!inventory.getStack(i).isEmpty()) {
                ret.add(new Pair<>(inventory.getStack(i),(byte)i));
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
        inventory.clear();

        for (Pair<ItemStack, Byte> pair : stack) {
            int j = pair.getSecond();
            ItemStack itemStack = pair.getFirst();
            if (j < playerInventorySize) {
                inventory.setStack(j, itemStack);
            }
        }
    }
    public static void stackToEnder(EnderChestInventory inventory,List<Pair<ItemStack,Byte>> stack)
    {
        inventory.clear();

        for (Pair<ItemStack, Byte> pair : stack) {
            int j = pair.getSecond();
            if (j < inventory.size()) {
                inventory.setStack(j, pair.getFirst());
            }
        }
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
        targetDimension=toOverworld(server,targetDimension);
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
            try{stackToInventory(player.getInventory(),n.inventory);
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
