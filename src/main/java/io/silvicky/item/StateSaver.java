package io.silvicky.item;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;

import java.util.*;

import static io.silvicky.item.common.Util.*;
import static java.lang.String.format;

public class StateSaver extends SavedData
{
    private final LinkedList<StorageInfo> nbtList;
    private final LinkedList<PositionInfo> posList;
    public final HashMap<Identifier,HashMap<String,PositionInfoNew>> posMap;
    public final HashMap<String,HashMap<String,StorageInfoNew>> savedMap;
    public final HashMap<Identifier, EndDragonFight.Data> dragonFight;
    public final HashMap<Identifier, WarpRestrictionInfo> restrictionInfoHashMap;
    public final HashMap<Identifier, Long> seed;
    public final HashMap<String, Integer> gamemode;
    private final HashMap<Identifier, BlockPos> spawn;
    public final HashMap<Identifier, LevelData.RespawnData> worldSpawn;
    public final HashMap<Identifier, HashMap<String, ServerPlayer.RespawnConfig>> respawn;
    public final HashMap<Identifier, Integer> entityVisibility;
    public final HashMap<String, HashMap<String,Long>> playerVisibility;
    public final HashMap<Identifier,String> chunkTransformer;
    public static final Codec<Pair<ItemStack,Byte>> SLOT_CODEC=Codec.pair(ItemStack.CODEC.orElse(ItemStack.EMPTY),Codec.BYTE.fieldOf(SLOT).codec());
    private static final Codec<StateSaver> CODEC= RecordCodecBuilder.create((instance)->
            instance.group
                    (
                        StorageInfo.CODEC.listOf().xmap(LinkedList::new, list->list).fieldOf(SAVED).orElse(new LinkedList<>()).forGetter((stateSaver)->
                                stateSaver.nbtList),
                        PositionInfo.CODEC.listOf().xmap(LinkedList::new,list->list).fieldOf("pos").orElse(new LinkedList<>()).forGetter((stateSaver)->
                                stateSaver.posList),
                        Codec.unboundedMap(Identifier.CODEC, Codec.unboundedMap(Codec.STRING,PositionInfoNew.CODEC).xmap(HashMap::new, map->map)).xmap(HashMap::new, map->map).fieldOf("pos_map").orElse(new HashMap<>()).forGetter((stateSaver)->
                                stateSaver.posMap),
                        Codec.unboundedMap(Codec.STRING, Codec.unboundedMap(Codec.STRING,StorageInfoNew.CODEC).xmap(HashMap::new,map->map)).xmap(HashMap::new,map->map).fieldOf(SAVED_MAP).orElse(new HashMap<>()).forGetter((stateSaver)->
                                stateSaver.savedMap),
                        Codec.unboundedMap(Identifier.CODEC, EndDragonFight.Data.CODEC).xmap(HashMap::new, map->map).fieldOf("dragon").orElse(new HashMap<>()).forGetter((stateSaver ->
                                stateSaver.dragonFight)),
                        Codec.unboundedMap(Identifier.CODEC, Codec.LONG).xmap(HashMap::new, map->map).fieldOf("seed").orElse(new HashMap<>()).forGetter((stateSaver ->
                                stateSaver.seed)),
                        Codec.unboundedMap(Identifier.CODEC, WarpRestrictionInfo.CODEC).xmap(HashMap::new, map->map).fieldOf("restriction").orElse(new HashMap<>()).forGetter((stateSaver ->
                                stateSaver.restrictionInfoHashMap)),
                        Codec.unboundedMap(Codec.STRING, Codec.INT).xmap(HashMap::new,map->map).fieldOf("gamemode").orElse(new HashMap<>()).forGetter((stateSaver ->
                                stateSaver.gamemode)),
                        Codec.unboundedMap(Identifier.CODEC, BlockPos.CODEC).xmap(HashMap::new, map->map).fieldOf("spawn").orElse(new HashMap<>()).forGetter((stateSaver ->
                                stateSaver.spawn)),
                        Codec.unboundedMap(Identifier.CODEC, LevelData.RespawnData.CODEC).xmap(HashMap::new, map->map).fieldOf("world_spawn").orElse(new HashMap<>()).forGetter((stateSaver ->
                                stateSaver.worldSpawn)),
                        Codec.unboundedMap(Identifier.CODEC, Codec.unboundedMap(Codec.STRING, ServerPlayer.RespawnConfig.CODEC).xmap(HashMap::new, map->map)).xmap(HashMap::new, map->map).fieldOf("respawn").orElse(new HashMap<>()).forGetter((stateSaver ->
                                stateSaver.respawn)),
                        Codec.unboundedMap(Identifier.CODEC, Codec.INT).xmap(HashMap::new, map->map).fieldOf("entity_visibility").orElse(new HashMap<>()).forGetter((stateSaver ->
                                stateSaver.entityVisibility)),
                        Codec.unboundedMap(Codec.STRING, Codec.unboundedMap(Codec.STRING,Codec.LONG).xmap(HashMap::new,map->map)).xmap(HashMap::new,map->map).fieldOf("player_visibility").orElse(new HashMap<>()).forGetter((stateSaver->
                                stateSaver.playerVisibility)),
                        Codec.unboundedMap(Identifier.CODEC, Codec.STRING).xmap(HashMap::new,map->map).fieldOf("chunk_transformer").orElse(new HashMap<>()).forGetter((stateSaver->
                                stateSaver.chunkTransformer))
                    ).apply(instance,StateSaver::new));
    private StateSaver(LinkedList<StorageInfo> nbtList,
                      LinkedList<PositionInfo> posList,
                       HashMap<Identifier,HashMap<String,PositionInfoNew>> posMap,
                       HashMap<String,HashMap<String,StorageInfoNew>> savedMap,
                      HashMap<Identifier, EndDragonFight.Data> dragonFight,
                      HashMap<Identifier,Long> seed,
                      HashMap<Identifier,WarpRestrictionInfo> restrictionInfoHashMap,
                      HashMap<String, Integer> gamemode,
                      HashMap<Identifier, BlockPos> spawn,
                      HashMap<Identifier, LevelData.RespawnData> worldSpawn,
                      HashMap<Identifier, HashMap<String, ServerPlayer.RespawnConfig>> respawn,
                       HashMap<Identifier,Integer> entityVisibility,
                       HashMap<String,HashMap<String,Long>> playerVisibility,
                       HashMap<Identifier,String> chunkTransformer)
    {
        this.nbtList=nbtList;
        this.posList=posList;
        this.posMap=posMap;
        this.savedMap=savedMap;
        this.dragonFight=dragonFight;
        this.seed=seed;
        this.restrictionInfoHashMap=restrictionInfoHashMap;
        this.gamemode=gamemode;
        this.spawn=spawn;
        this.worldSpawn=worldSpawn;
        this.respawn=respawn;
        this.entityVisibility=entityVisibility;
        this.playerVisibility=playerVisibility;
        this.chunkTransformer=chunkTransformer;
    }
    private StateSaver()
    {
        this(new LinkedList<>(),
                new LinkedList<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>()
        );
    }
    private static final SavedDataType<StateSaver> type = new SavedDataType<>(
            MOD_ID,
            StateSaver::new,
            CODEC,
            DataFixTypes.PLAYER
    );

    static ArrayList<Pair<ItemStack,Byte>> listToArrayList(List<Pair<ItemStack,Byte>> src)
    {
        ArrayList<Pair<ItemStack,Byte>> ret=new ArrayList<>();
        for(Pair<ItemStack,Byte> i:src)
        {
            if (i!=null
                &&i.getFirst()!=null
                &&!i.getFirst().isEmpty()
                &&i.getSecond()!=null) ret.add(i);
        }
        return ret;
    }

    private void update()
    {
        for(Map.Entry<Identifier, BlockPos> posEntry:spawn.entrySet())
        {
            worldSpawn.put(getDimensionId(posEntry.getKey()),
                    LevelData.RespawnData.of(ResourceKey.create(Registries.DIMENSION,posEntry.getKey()),posEntry.getValue(),0,0));
        }
        spawn.clear();
        for(PositionInfo positionInfo:posList)
        {
            posMap.computeIfAbsent(Identifier.parse(positionInfo.dimension), i->new HashMap<>())
                    .put(positionInfo.player, new PositionInfoNew(
                            Identifier.parse(positionInfo.rdim),
                            positionInfo.pos,
                            Vec3.ZERO,
                            0,
                            0
                            ));
        }
        posList.clear();
        for(StorageInfo storageInfo:nbtList)
        {
            savedMap.computeIfAbsent(storageInfo.dimension,i->new HashMap<>())
                    .put(storageInfo.player,new StorageInfoNew(
                            storageInfo.inventory,
                            storageInfo.ender,
                            storageInfo.xp,
                            storageInfo.hp,
                            storageInfo.food,
                            storageInfo.food2,
                            storageInfo.air,
                            storageInfo.gamemode
                    ));
        }
        nbtList.clear();
    }
    public static StateSaver getServerState(MinecraftServer server) {
        return getServerState(Objects.requireNonNull(server.getLevel(Level.OVERWORLD)));
    }
    //DO NOT USE THIS UNLESS DURING CONSTRUCTION OF OVERWORLD
    public static StateSaver getServerState(ServerLevel world) {
        DimensionDataStorage persistentStateManager = world.getDataStorage();
        StateSaver state = persistentStateManager.computeIfAbsent(type);
        state.setDirty();
        state.update();
        return state;
    }
    private static class StorageInfo {
        public String player;
        public String dimension;
        public ArrayList<Pair<ItemStack,Byte>> inventory;
        public ArrayList<Pair<ItemStack,Byte>> ender;
        public int xp;
        public float hp;
        public int food;
        public float food2;
        public int air;
        public int gamemode;

        public StorageInfo(String player, String dimension, ArrayList<Pair<ItemStack,Byte>> inventory, ArrayList<Pair<ItemStack,Byte>> ender, int xp, float hp, int food, float food2, int air, int gamemode) {
            this.player = player;
            this.dimension = dimension;
            this.inventory = inventory;
            this.ender = ender;
            this.xp = xp;
            this.hp = hp;
            this.food = food;
            this.food2 = food2;
            this.air = air;
            this.gamemode = gamemode;
        }
        public static final Codec<StorageInfo> CODEC= RecordCodecBuilder.create((instance) ->
                instance.group
                        (
                                Codec.STRING.fieldOf(PLAYER).forGetter((info)->info.player),
                                Codec.STRING.fieldOf(DIMENSION).forGetter((info)->info.dimension),
                                SLOT_CODEC.listOf().xmap(StateSaver::listToArrayList, list->list).fieldOf(INVENTORY).orElse(new ArrayList<>()).forGetter((info)->info.inventory),
                                SLOT_CODEC.listOf().xmap(StateSaver::listToArrayList, list->list).fieldOf(ENDER).orElse(new ArrayList<>()).forGetter((info)->info.ender),
                                Codec.INT.fieldOf("xp").orElse(0).forGetter((info)->info.xp),
                                Codec.FLOAT.fieldOf("hp").orElse(20f).forGetter((info)->info.hp),
                                Codec.INT.fieldOf("food").orElse(20).forGetter((info)->info.food),
                                Codec.FLOAT.fieldOf("food2").orElse(5.0f).forGetter((info)->info.food2),
                                Codec.INT.fieldOf("air").orElse(300).forGetter((info)->info.air),
                                Codec.INT.fieldOf("gamemode").orElse(0).forGetter((info)->info.gamemode)

                        ).apply(instance, StorageInfo::new));
    }
    public record StorageInfoNew(
            ArrayList<Pair<ItemStack,Byte>> inventory,
            ArrayList<Pair<ItemStack,Byte>> ender,
            int xp,
            float hp,
            int food,
            float saturation,
            int air,
            int gamemode
    ) {
        public static final Codec<StorageInfoNew> CODEC= RecordCodecBuilder.create((instance) ->
                instance.group
                        (
                                SLOT_CODEC.listOf().xmap(StateSaver::listToArrayList, list->list).fieldOf(INVENTORY).forGetter((info)->info.inventory),
                                SLOT_CODEC.listOf().xmap(StateSaver::listToArrayList, list->list).fieldOf(ENDER).forGetter((info)->info.ender),
                                Codec.INT.fieldOf("xp").forGetter((info)->info.xp),
                                Codec.FLOAT.fieldOf("hp").forGetter((info)->info.hp),
                                Codec.INT.fieldOf("food").forGetter((info)->info.food),
                                Codec.FLOAT.fieldOf("food2").forGetter((info)->info.saturation),
                                Codec.INT.fieldOf("air").forGetter((info)->info.air),
                                Codec.INT.fieldOf("gamemode").forGetter((info)->info.gamemode)

                        ).apply(instance, StorageInfoNew::new));
    }
    public record PositionInfoNew(Identifier dimension, Vec3 pos, Vec3 velocity, float yaw, float pitch)
    {
        public static final Codec<PositionInfoNew> CODEC= RecordCodecBuilder.create((instance) ->
                instance.group
                        (
                                Identifier.CODEC.fieldOf("dimension").forGetter(info->info.dimension),
                                Vec3.CODEC.fieldOf("pos").forGetter(info->info.pos),
                                Vec3.CODEC.fieldOf("velocity").forGetter(info->info.velocity),
                                Codec.FLOAT.fieldOf("yaw").forGetter(info->info.yaw),
                                Codec.FLOAT.fieldOf("pitch").forGetter(info->info.pitch)
                        ).apply(instance, PositionInfoNew::new));
    }
    private static class PositionInfo {
        public String player;
        public String dimension;
        public String rdim;
        public Vec3 pos;

        public PositionInfo(String player, String dimension, String rdim, Vec3 pos) {
            this.player = player;
            this.dimension = dimension;
            this.rdim = rdim;
            this.pos = pos;
        }

        public static final Codec<Vec3> VEC_3_D_CODEC=RecordCodecBuilder.create((instance)->
                instance.group(
                        Codec.DOUBLE.fieldOf("x").forGetter((v3d)->v3d.x),
                        Codec.DOUBLE.fieldOf("y").forGetter((v3d)->v3d.y),
                        Codec.DOUBLE.fieldOf("z").forGetter((v3d)->v3d.z)
                ).apply(instance, Vec3::new)
                );
        public static final Codec<PositionInfo> CODEC= RecordCodecBuilder.create((instance) ->
                instance.group
                        (
                                Codec.STRING.fieldOf(PLAYER).forGetter((info)->info.player),
                                Codec.STRING.fieldOf(DIMENSION).forGetter((info)->info.dimension),
                                Codec.STRING.fieldOf("rdim").forGetter((info)->info.rdim),
                                VEC_3_D_CODEC.fieldOf("pos").forGetter((info)->info.pos)

                        ).apply(instance, PositionInfo::new));
    }
    public static class WarpRestrictionInfo
    {
        public String reason;
        public static final String DEFAULT_REASON="no reason specified";
        public int level;
        public static final int INFINITE=Integer.MAX_VALUE;
        public WarpRestrictionInfo(String reason,int level){this.reason=reason;this.level=level;}
        public static final Codec<WarpRestrictionInfo> CODEC= RecordCodecBuilder.create((instance) ->
                instance.group
                        (
                                Codec.STRING.fieldOf(REASON).orElse(DEFAULT_REASON).forGetter((info)->info.reason),
                                Codec.INT.fieldOf(LEVEL).orElse(INFINITE).forGetter((info)->info.level)

                        ).apply(instance, WarpRestrictionInfo::new));

        @Override
        public String toString()
        {
            return format("%d, %s",level,reason);
        }
    }
}
