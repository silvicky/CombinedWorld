package io.silvicky.item;

import net.fabricmc.fabric.api.dimension.v1.FabricDimensions;
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

public class InventoryManager {
    public static final String DIMENSION="dimension";
    public static final String PLAYER="player";
    public static final String INVENTORY="inventory";
    public static final String ENDER="ender";
    public static final String XP="xp";
    public static final String HP="hp";
    public static final String FOOD="food";
    public static final String FOOD2="food2";
    public static final String GAMEMODE="gamemode";
    public static final String REAL_DIMENSION="rdim";
    public static final String POS="pos";
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
        return new Vec3d(n.getDouble("x"),n.getDouble("y"),n.getDouble("z"));
    }
    public static void save(MinecraftServer server, ServerPlayerEntity player)
    {
        StateSaver stateSaver=StateSaver.getServerState(server);
        NbtCompound sav=new NbtCompound();
        sav.putString(PLAYER, player.getUuidAsString());
        sav.putString(DIMENSION,getDimensionId(player.getServerWorld()));
        sav.putString(REAL_DIMENSION,player.getServerWorld().getRegistryKey().getValue().toString());
        sav.put(POS,V3dToNbt(player.getPos()));
        NbtList pi=new NbtList();
        player.getInventory().writeNbt(pi);
        sav.put(INVENTORY,pi);
        sav.put(ENDER,player.getEnderChestInventory().toNbtList(server.getRegistryManager()));
        sav.putInt(XP,player.totalExperience);
        sav.putFloat(HP,player.getHealth());
        sav.putInt(FOOD,player.getHungerManager().getFoodLevel());
        sav.putFloat(FOOD2,player.getHungerManager().getSaturationLevel());
        sav.putInt(GAMEMODE,player.interactionManager.getGameMode().getId());
        stateSaver.nbtList.add(sav);
        player.getInventory().clear();
        player.getEnderChestInventory().clear();
        player.setExperiencePoints(0);
        player.setHealth(20.0F);
        player.getHungerManager().setFoodLevel(20);
        player.getHungerManager().setSaturationLevel(5.0F);
        player.interactionManager.changeGameMode(GameMode.SURVIVAL);
        player.networkHandler.sendPacket(new GameStateChangeS2CPacket(GameStateChangeS2CPacket.GAME_MODE_CHANGED, GameMode.SURVIVAL.getId()));
    }
    public static void load(MinecraftServer server, ServerPlayerEntity player, ServerWorld targetDimension)
    {
        String overworldId=getDimensionId(targetDimension);
        ServerWorld sw=server.getWorld(RegistryKey.of(RegistryKey.ofRegistry(targetDimension.getRegistryKey().getRegistry()),
                Identifier.of(targetDimension.getRegistryKey().getValue().getNamespace(),
                        overworldId.substring(overworldId.indexOf(":")+1))));
        targetDimension=(sw!=null?sw:targetDimension);
        StateSaver stateSaver=StateSaver.getServerState(server);
        Iterator<NbtElement> iterator=stateSaver.nbtList.iterator();
        boolean hit=false;
        while (iterator.hasNext())
        {
            NbtCompound n=(NbtCompound) iterator.next();
            String tarDim=getDimensionId(targetDimension);
            if(n.getString(PLAYER).equals(player.getUuidAsString())&&n.getString(DIMENSION).equals(tarDim))
            {
                LOGGER.info("Fetched!");
                if(n.contains(INVENTORY))player.getInventory().readNbt((NbtList) n.get(INVENTORY));
                else player.getInventory().clear();
                if(n.contains(ENDER))player.getEnderChestInventory().readNbtList((NbtList) n.get(ENDER),server.getRegistryManager());
                else player.getEnderChestInventory().clear();
                if(n.contains(XP))player.setExperiencePoints(n.getInt(XP));
                else player.setExperiencePoints(0);
                if(n.contains(HP))player.setHealth(n.getFloat(HP));
                else player.setHealth(20.0F);
                if(n.contains(FOOD))player.getHungerManager().setFoodLevel(n.getInt(FOOD));
                else player.getHungerManager().setFoodLevel(20);
                if(n.contains(FOOD2))player.getHungerManager().setSaturationLevel(n.getFloat(FOOD2));
                else player.getHungerManager().setSaturationLevel(5.0F);
                if(n.contains(GAMEMODE))
                {
                    player.interactionManager.changeGameMode(GameMode.byId(n.getInt(GAMEMODE)));
                    player.networkHandler.sendPacket(new GameStateChangeS2CPacket(GameStateChangeS2CPacket.GAME_MODE_CHANGED, n.getInt(GAMEMODE)));
                }
                else
                {
                    player.interactionManager.changeGameMode(GameMode.SURVIVAL);
                    player.networkHandler.sendPacket(new GameStateChangeS2CPacket(GameStateChangeS2CPacket.GAME_MODE_CHANGED,GameMode.SURVIVAL.getId()));
                }
                if(n.contains(REAL_DIMENSION)&&n.contains(POS))
                {
                    TeleportTarget target = new TeleportTarget(NbtToV3d((NbtCompound) n.get(POS)), Vec3d.ZERO, 0f, 0f);
                    String dim=n.getString(REAL_DIMENSION);
                    Iterator<ServerWorld> i=server.getWorlds().iterator();
                    ServerWorld sw2=server.getWorld(RegistryKey.of(RegistryKey.ofRegistry(targetDimension.getRegistryKey().getRegistry()),
                            Identifier.of(targetDimension.getRegistryKey().getValue().getNamespace(),
                                    overworldId.substring(overworldId.indexOf(":")+1))));
                    if(sw2==null)
                    {
                        LOGGER.error("A dimension named "+dim+" is NOT FOUND!");
                    }
                    FabricDimensions.teleport(player, sw2, target);
                }
                else
                {
                    BlockPos sp=targetDimension.getSpawnPos();
                    while(targetDimension.getBlockState(sp).isAir())sp=sp.down();
                    sp=sp.up();
                    TeleportTarget target = new TeleportTarget(sp.toCenterPos(), Vec3d.ZERO, 0f, 0f);
                    FabricDimensions.teleport(player, targetDimension, target);
                }
                hit=true;
                iterator.remove();
                break;
            }
        }
        if(!hit)
        {
            LOGGER.info("Entering a new world... Good luck to the pioneer!");
            BlockPos sp=targetDimension.getSpawnPos();
            while(targetDimension.getBlockState(sp).isAir())sp=sp.down();
            sp=sp.up();
            TeleportTarget target = new TeleportTarget(sp.toCenterPos(), Vec3d.ZERO, 0f, 0f);
            FabricDimensions.teleport(player, targetDimension, target);
        }
    }
}
