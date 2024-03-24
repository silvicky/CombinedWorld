package io.silvicky.item;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

import java.util.Iterator;
import java.util.Objects;

import static io.silvicky.item.ItemStorage.LOGGER;

public class InventoryManager {
    public static final String DIMENSION="dimension";
    public static final String PLAYER="dimension";
    public static final String INVENTORY="inventory";
    public static final String MC="minecraft";
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
        Identifier id=player.getServerWorld().getRegistryKey().getValue();
        String curDim=getDimensionId(player.getServerWorld());
        sav.putString(DIMENSION,curDim);
        NbtList pi=new NbtList();
        player.getInventory().writeNbt(pi);
        sav.put(INVENTORY,pi);
        stateSaver.nbtList.add(sav);
        player.getInventory().readNbt(new NbtList());
    }
    public static void load(MinecraftServer server, ServerPlayerEntity player, ServerWorld targetDimension)
    {
        StateSaver stateSaver=StateSaver.getServerState(server);
        Iterator<NbtElement> iterator=stateSaver.nbtList.iterator();
        while (iterator.hasNext())
        {
            NbtCompound n=(NbtCompound) iterator.next();
            String tarDim=getDimensionId(targetDimension);
            if(n.getString("player").equals(player.getUuidAsString())&&n.getString(DIMENSION).equals(tarDim))
            {
                LOGGER.info("Fetched!");
                player.getInventory().readNbt((NbtList) n.get("inventory"));
                iterator.remove();
            }
        }
    }
}
