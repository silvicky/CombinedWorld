package io.silvicky.item;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.PersistentStateType;
import net.minecraft.world.World;

import java.util.function.Supplier;

public class StateSaver extends PersistentState {
    public static final String POS="pos";
    public static final String SAVED="saved";
    public NbtList nbtList;
    public NbtList posList;
    public StateSaver(){nbtList=new NbtList();posList=new NbtList();}
    public StateSaver(NbtList nbtList,NbtList posList){this.nbtList=nbtList;this.posList=posList;}

    //@Override
    public static NbtCompound writeNbt(NbtList nbtList,NbtList posList) {
        NbtCompound nbt=new NbtCompound();
        nbt.put(SAVED,nbtList);
        nbt.put(POS,posList);
        return nbt;
    }
    public static StateSaver createFromNbt(NbtCompound tag) {
        StateSaver state = new StateSaver();
        state.nbtList = (NbtList) tag.get(SAVED);
        state.posList = (NbtList) tag.get(POS);
        return state;
    }

    private static final PersistentStateType<StateSaver> type = new PersistentStateType<>(
            ItemStorage.MOD_ID,
            new Supplier<StateSaver>() {
                /**
                 * Gets a result.
                 *
                 * @return a result
                 */
                @Override
                public StateSaver get() {
                    return new StateSaver();
                }
            }, // If there's no 'StateSaverAndLoader' yet create one
            new Codec<StateSaver>() {
                @Override
                public <T> DataResult<Pair<StateSaver, T>> decode(DynamicOps<T> ops, T input) {
                    return createFromNbt(input);
                }

                @Override
                public <T> DataResult<T> encode(StateSaver input, DynamicOps<T> ops, T prefix) {
                    return new DataResult<T>() {
                    }
                    //return writeNbt(input.nbtList,input.posList);
                }
            }, // If there is a 'StateSaverAndLoader' NBT, parse it with 'createFromNbt'
            null // Supposed to be an 'DataFixTypes' enum, but we can just pass null
    );

    public static StateSaver getServerState(MinecraftServer server) {
        // (Note: arbitrary choice to use 'World.OVERWORLD' instead of 'World.END' or 'World.NETHER'.  Any work)
        PersistentStateManager persistentStateManager = server.getWorld(World.OVERWORLD).getPersistentStateManager();

        // The first time the following 'getOrCreate' function is called, it creates a brand new 'StateSaverAndLoader' and
        // stores it inside the 'PersistentStateManager'. The subsequent calls to 'getOrCreate' pass in the saved
        // 'StateSaverAndLoader' NBT on disk to our function 'StateSaverAndLoader::createFromNbt'.
        StateSaver state = persistentStateManager.getOrCreate(type);

        // If state is not marked dirty, when Minecraft closes, 'writeNbt' won't be called and therefore nothing will be saved.
        // Technically it's 'cleaner' if you only mark state as dirty when there was actually a change, but the vast majority
        // of mod writers are just going to be confused when their data isn't being saved, and so it's best just to 'markDirty' for them.
        // Besides, it's literally just setting a bool to true, and the only time there's a 'cost' is when the file is written to disk when
        // there were no actual change to any of the mods state (INCREDIBLY RARE).
        state.markDirty();

        return state;
    }
}
