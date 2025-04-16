package io.silvicky.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.PersistentStateType;
import net.minecraft.world.World;

import java.util.ArrayList;

public class StateSaver extends PersistentState {
    public static final String POS="pos";
    public static final String SAVED="saved";
    public ArrayList<StorageInfo> nbtList;
    public ArrayList<PositionInfo> posList;
    public static final Codec<StateSaver> CODEC= RecordCodecBuilder.create((instance)->
            instance.group
                    (
                        StorageInfo.CODEC.listOf().xmap(ArrayList::new, list->list).optionalFieldOf(SAVED, new ArrayList<>()).forGetter((stateSaver)->
                                stateSaver.nbtList),
                        PositionInfo.CODEC.listOf().xmap(ArrayList::new,list->list).optionalFieldOf(POS, new ArrayList<>()).forGetter((stateSaver)->
                                stateSaver.posList)
                    ).apply(instance,StateSaver::new));
    public StateSaver(ArrayList<StorageInfo> nbtList,ArrayList<PositionInfo> posList){this.nbtList=nbtList;this.posList=posList;}
    public StateSaver(){this(new ArrayList<>(),new ArrayList<>());}
    private static final PersistentStateType<StateSaver> type = new PersistentStateType<>(
            ItemStorage.MOD_ID,
            StateSaver::new,
            CODEC,
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
