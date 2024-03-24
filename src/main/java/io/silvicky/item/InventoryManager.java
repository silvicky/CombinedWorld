package io.silvicky.item;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

import java.util.Iterator;

import static io.silvicky.item.ItemStorage.LOGGER;

public class InventoryManager {
    public static final String DIMENSION="dimension";
    public static final String PLAYER="player";
    public static final String INVENTORY="inventory";
    public static final String ENDER="ender";
    public static final String XP="xp";
    public static final String MC="minecraft";
    public static final String RECIPE="recipe";
    public static String getDimensionId(ServerWorld world)
    {
        Identifier id=world.getRegistryKey().getValue();
        if(id.getNamespace().equals(MC))return world.getServer().getOverworld().getRegistryKey().getValue().toString();
        else return id.toString();

    }
    public static void save(MinecraftServer server, ServerPlayerEntity player)
    {
        StateSaver stateSaver=StateSaver.getServerState(server);
        NbtCompound sav=new NbtCompound();
        sav.putString(PLAYER, player.getUuidAsString());
        sav.putString(DIMENSION,getDimensionId(player.getServerWorld()));
        NbtList pi=new NbtList();
        player.getInventory().writeNbt(pi);
        sav.put(INVENTORY,pi);
        sav.put(ENDER,player.getEnderChestInventory().toNbtList());
        sav.putInt(XP,player.totalExperience);
        //sav.put(RECIPE,player.getRecipeBook().toNbt());
        stateSaver.nbtList.add(sav);
        player.getInventory().clear();
        player.getEnderChestInventory().clear();
        player.setExperiencePoints(0);
        //player.getRecipeBook().readNbt(new NbtCompound(),server.getRecipeManager());
    }
    public static void load(MinecraftServer server, ServerPlayerEntity player, ServerWorld targetDimension)
    {
        StateSaver stateSaver=StateSaver.getServerState(server);
        Iterator<NbtElement> iterator=stateSaver.nbtList.iterator();
        while (iterator.hasNext())
        {
            NbtCompound n=(NbtCompound) iterator.next();
            String tarDim=getDimensionId(targetDimension);
            if(n.getString(PLAYER).equals(player.getUuidAsString())&&n.getString(DIMENSION).equals(tarDim))
            {
                LOGGER.info("Fetched!");
                if(n.contains(INVENTORY))player.getInventory().readNbt((NbtList) n.get(INVENTORY));
                else player.getInventory().clear();
                if(n.contains(ENDER))player.getEnderChestInventory().readNbtList((NbtList) n.get(ENDER));
                else player.getEnderChestInventory().clear();
                if(n.contains(XP))player.setExperiencePoints(n.getInt(XP));
                else player.setExperiencePoints(0);
                //player.getRecipeBook().readNbt((NbtCompound) n.get(RECIPE),server.getRecipeManager());
                iterator.remove();
                break;
            }
        }
    }
}
