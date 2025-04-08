package io.silvicky.item;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
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

import java.util.Iterator;

import static io.silvicky.item.ItemStorage.LOGGER;
import static io.silvicky.item.cfg.JSONConfig.useStorage;

public class InventoryManager {
    public static final String DIMENSION="dimension";
    public static final String PLAYER="player";
    public static final String INVENTORY="inventory";
    public static final String ENDER="ender";
    public static final String XP="xp";
    public static final String HP="hp";
    public static final String FOOD="food";
    public static final String AIR="air";
    public static final String FOOD2="food2";
    public static final String GAMEMODE="gamemode";
    public static final String REAL_DIMENSION="rdim";
    public static final String POS="pos";
    public static final String OVERWORLD="overworld";
    public static final String NETHER="the_nether";
    public static final String END="the_end";
    public static final String MC="minecraft";
    public static String getDimensionId(ServerWorld world)
    {
        String id=world.getRegistryKey().getValue().toString();
        if(id.endsWith(NETHER))id=id.substring(0,id.length()-10)+OVERWORLD;
        if(id.endsWith(END))id=id.substring(0,id.length()-7)+OVERWORLD;
        return id;
    }
    public static NbtCompound V3dToNbt(Vec3d v)
    {
        NbtCompound ret=new NbtCompound();
        ret.putDouble("x",v.x);
        ret.putDouble("y",v.y);
        ret.putDouble("z",v.z);
        return ret;
    }
    public static Vec3d NbtToV3d(NbtCompound n)
    {
        return new Vec3d(n.getDouble("x").get(),n.getDouble("y").get(),n.getDouble("z").get());
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
        NbtCompound pos=new NbtCompound();
        pos.putString(PLAYER, player.getUuidAsString());
        pos.putString(DIMENSION,getDimensionId(player.getServerWorld()));
        pos.putString(REAL_DIMENSION,player.getServerWorld().getRegistryKey().getValue().toString());
        pos.put(POS,V3dToNbt(player.getPos()));
        stateSaver.posList.add(pos);
    }
    public static void saveInventory(MinecraftServer server,ServerPlayerEntity player,StateSaver stateSaver)
    {
        NbtCompound sav=new NbtCompound();
        sav.putString(PLAYER, player.getUuidAsString());
        sav.putString(DIMENSION,player.getServerWorld().getRegistryKey().getValue().getNamespace());
        NbtList pi=new NbtList();
        player.getInventory().writeNbt(pi);
        sav.put(INVENTORY,pi);
        sav.put(ENDER,player.getEnderChestInventory().toNbtList(server.getRegistryManager()));
        sav.putInt(XP,player.totalExperience);
        sav.putFloat(HP,player.getHealth());
        sav.putInt(FOOD,player.getHungerManager().getFoodLevel());
        sav.putFloat(FOOD2,player.getHungerManager().getSaturationLevel());
        sav.putInt(AIR,player.getAir());
        sav.putInt(GAMEMODE,player.interactionManager.getGameMode().getIndex());
        stateSaver.nbtList.add(sav);
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
        Iterator<NbtElement> iterator=stateSaver.posList.iterator();
        NbtCompound n=null;
        while (iterator.hasNext())
        {
            NbtCompound nt=(NbtCompound) iterator.next();
            if(!(nt.getString(PLAYER).equals(player.getUuidAsString())&&nt.getString(DIMENSION).equals(getDimensionId(targetDimension))))continue;
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
            //FabricDimensions.teleport(player, targetDimension, target);
            player.teleportTo(target);
        }
        else
        {
            String dim=n.getString(REAL_DIMENSION).get();
            ServerWorld sw2=server.getWorld(RegistryKey.of(RegistryKey.ofRegistry(targetDimension.getRegistryKey().getRegistry()),
                    Identifier.of(targetDimension.getRegistryKey().getValue().getNamespace(),
                            dim.substring(dim.indexOf(":")+1))));
            if(sw2==null)
            {
                LOGGER.error("A dimension named "+dim+" is NOT FOUND!");
                return false;
            }
            Vec3d v3d=NbtToV3d((NbtCompound) n.get(POS));
            BlockPos sp=new BlockPos((int) Math.floor(v3d.x), (int) Math.floor(v3d.y), (int) Math.floor(v3d.z));
            //sp=sp.withY(sw2.getTopY());
            //sp=transLoc(sp,sw2);
            TeleportTarget.PostDimensionTransition postDimensionTransition=TeleportTarget.NO_OP;

            TeleportTarget target = new TeleportTarget(sw2,sp.toCenterPos(), Vec3d.ZERO, 0f, 0f,postDimensionTransition);
            //FabricDimensions.teleport(player, sw2, target);
            player.teleportTo(target);
        }
        return true;
    }
    public static void loadInventory(MinecraftServer server,ServerPlayerEntity player,ServerWorld targetDimension,StateSaver stateSaver)
    {
        Iterator<NbtElement> iterator=stateSaver.nbtList.iterator();
        NbtCompound n=null;
        while (iterator.hasNext())
        {
            NbtCompound nt=(NbtCompound) iterator.next();
            if(!(nt.getString(PLAYER).equals(player.getUuidAsString())
                    &&nt.getString(DIMENSION).equals(targetDimension.getRegistryKey().getValue().getNamespace())))
                    continue;
            iterator.remove();
            LOGGER.info("Fetched inventory!");
            if(n!=null)LOGGER.warn("Duplicated data found! Discarding old data, but this should not happen...");
            n=nt;
        }
        if(n!=null)
        {
            player.getInventory().readNbt((NbtList) n.get(INVENTORY));
            player.getEnderChestInventory().readNbtList((NbtList) n.get(ENDER),server.getRegistryManager());
            player.setExperiencePoints(n.getInt(XP).get());
            player.setHealth(n.getFloat(HP).get());
            player.getHungerManager().setFoodLevel(n.getInt(FOOD).get());
            player.getHungerManager().setSaturationLevel(n.getFloat(FOOD2).get());
            player.setAir(n.getInt(AIR).get());
            player.interactionManager.changeGameMode(GameMode.byIndex(n.getInt(GAMEMODE).get()));
            player.networkHandler.sendPacket(new GameStateChangeS2CPacket(GameStateChangeS2CPacket.GAME_MODE_CHANGED, n.getInt(GAMEMODE).get()));
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
        if(useStorage)saveInventory(server,player,stateSaver);
    }
    public static boolean load(MinecraftServer server, ServerPlayerEntity player, ServerWorld targetDimension)
    {
        StateSaver stateSaver=StateSaver.getServerState(server);
        if(useStorage)loadInventory(server,player,targetDimension,stateSaver);
        return loadPos(server, player, targetDimension, stateSaver);
    }
    public static boolean directWarp(MinecraftServer server,ServerPlayerEntity player,ServerWorld targetDimension)
    {
        StateSaver stateSaver=StateSaver.getServerState(server);
        savePos(player,stateSaver);
        return loadPos(server, player, targetDimension, stateSaver);
    }
}
