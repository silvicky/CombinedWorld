package io.silvicky.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.boss.dragon.EnderDragonFight;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.PersistentStateType;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;

public class StateSaver extends PersistentState {
    public final ArrayList<StorageInfo> nbtList;
    public final ArrayList<PositionInfo> posList;
    public final HashMap<Identifier, EnderDragonFight.Data> dragonFight;
    public final HashMap<Identifier, Long> seed;
    public static final Codec<StateSaver> CODEC= RecordCodecBuilder.create((instance)->
            instance.group
                    (
                        StorageInfo.CODEC.listOf().xmap(ArrayList::new, list->list).fieldOf("saved").orElse(new ArrayList<>()).forGetter((stateSaver)->
                                stateSaver.nbtList),
                        PositionInfo.CODEC.listOf().xmap(ArrayList::new,list->list).fieldOf("pos").orElse(new ArrayList<>()).forGetter((stateSaver)->
                                stateSaver.posList),
                        Codec.unboundedMap(Identifier.CODEC, EnderDragonFight.Data.CODEC).xmap(HashMap::new,map->map).fieldOf("dragon").orElse(new HashMap<>()).forGetter((stateSaver ->
                                stateSaver.dragonFight)),
                        Codec.unboundedMap(Identifier.CODEC, Codec.LONG).xmap(HashMap::new,map->map).fieldOf("seed").orElse(new HashMap<>()).forGetter((stateSaver ->
                                stateSaver.seed))
                    ).apply(instance,StateSaver::new));
    public StateSaver(ArrayList<StorageInfo> nbtList,ArrayList<PositionInfo> posList,HashMap<Identifier,EnderDragonFight.Data> dragonFight,HashMap<Identifier,Long> seed){this.nbtList=nbtList;this.posList=posList;this.dragonFight=dragonFight;this.seed=seed;}
    public StateSaver(){this(new ArrayList<>(),new ArrayList<>(),new HashMap<>(),new HashMap<>());}
    private static final PersistentStateType<StateSaver> type = new PersistentStateType<>(
            ItemStorage.MOD_ID,
            StateSaver::new,
            CODEC,
            null
    );

    public static StateSaver getServerState(MinecraftServer server) {
        return getServerState(Objects.requireNonNull(server.getWorld(World.OVERWORLD)));
    }
    //DO NOT USE THIS UNLESS DURING CONSTRUCTION OF OVERWORLD
    public static StateSaver getServerState(ServerWorld world) {
        PersistentStateManager persistentStateManager = world.getPersistentStateManager();
        StateSaver state = persistentStateManager.getOrCreate(type);
        state.markDirty();
        return state;
    }
}
