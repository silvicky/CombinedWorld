package io.silvicky.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.datafixer.DataFixTypes;
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
    public ArrayList<StorageInfo> nbtList;
    public ArrayList<PositionInfo> posList;
    public HashMap<Identifier, EnderDragonFight.Data> dragonFight;
    public static final Codec<StateSaver> CODEC= RecordCodecBuilder.create((instance)->
            instance.group
                    (
                        StorageInfo.CODEC.listOf().xmap(ArrayList::new, list->list).optionalFieldOf("saved", new ArrayList<>()).forGetter((stateSaver)->
                                stateSaver.nbtList),
                        PositionInfo.CODEC.listOf().xmap(ArrayList::new,list->list).optionalFieldOf("pos", new ArrayList<>()).forGetter((stateSaver)->
                                stateSaver.posList),
                        Codec.unboundedMap(Identifier.CODEC, EnderDragonFight.Data.CODEC).xmap(HashMap::new,map->map).optionalFieldOf("dragon", new HashMap<>()).forGetter((stateSaver ->
                                stateSaver.dragonFight))
                    ).apply(instance,StateSaver::new));
    public StateSaver(ArrayList<StorageInfo> nbtList,ArrayList<PositionInfo> posList,HashMap<Identifier,EnderDragonFight.Data> dragonFight){this.nbtList=nbtList;this.posList=posList;this.dragonFight=dragonFight;}
    public StateSaver(){this(new ArrayList<>(),new ArrayList<>(),new HashMap<>());}
    private static final PersistentStateType<StateSaver> type = new PersistentStateType<>(
            ItemStorage.MOD_ID,
            StateSaver::new,
            CODEC,
            DataFixTypes.LEVEL
    );

    public static StateSaver getServerState(MinecraftServer server) {
        return getServerState(Objects.requireNonNull(server.getWorld(World.OVERWORLD)));
    }
    public static StateSaver getServerState(ServerWorld world) {
        PersistentStateManager persistentStateManager = world.getPersistentStateManager();
        StateSaver state = persistentStateManager.getOrCreate(type);
        state.markDirty();
        return state;
    }
}
