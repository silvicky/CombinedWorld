package io.silvicky.item;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.silvicky.item.common.Util;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.entity.boss.dragon.EnderDragonFight;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.*;

import java.util.*;

import static io.silvicky.item.common.Util.*;

public class StateSaver extends PersistentState {
    //TODO why list???
    public final LinkedList<StorageInfo> nbtList;
    public final LinkedList<PositionInfo> posList;
    public final HashMap<Identifier, EnderDragonFight.Data> dragonFight;
    public final HashMap<Identifier, WarpRestrictionInfo> restrictionInfoHashMap;
    public final HashMap<Identifier, Long> seed;
    public final HashMap<String, Integer> gamemode;
    private final HashMap<Identifier, BlockPos> spawn;
    public final HashMap<Identifier, WorldProperties.SpawnPoint> worldSpawn;
    public final HashMap<Identifier, HashMap<String, ServerPlayerEntity.Respawn>> respawn;
    private static final Codec<StateSaver> CODEC= RecordCodecBuilder.create((instance)->
            instance.group
                    (
                        StorageInfo.CODEC.listOf().xmap(LinkedList::new, list->list).fieldOf(SAVED).orElse(new LinkedList<>()).forGetter((stateSaver)->
                                stateSaver.nbtList),
                        PositionInfo.CODEC.listOf().xmap(LinkedList::new,list->list).fieldOf("pos").orElse(new LinkedList<>()).forGetter((stateSaver)->
                                stateSaver.posList),
                        Codec.unboundedMap(Identifier.CODEC, EnderDragonFight.Data.CODEC).xmap(HashMap::new,map->map).fieldOf("dragon").orElse(new HashMap<>()).forGetter((stateSaver ->
                                stateSaver.dragonFight)),
                        Codec.unboundedMap(Identifier.CODEC, Codec.LONG).xmap(HashMap::new,map->map).fieldOf("seed").orElse(new HashMap<>()).forGetter((stateSaver ->
                                stateSaver.seed)),
                        Codec.unboundedMap(Identifier.CODEC, WarpRestrictionInfo.CODEC).xmap(HashMap::new,map->map).fieldOf("restriction").orElse(new HashMap<>()).forGetter((stateSaver ->
                                stateSaver.restrictionInfoHashMap)),
                        Codec.unboundedMap(Codec.STRING, Codec.INT).xmap(HashMap::new,map->map).fieldOf("gamemode").orElse(new HashMap<>()).forGetter((stateSaver ->
                                stateSaver.gamemode)),
                        Codec.unboundedMap(Identifier.CODEC, BlockPos.CODEC).xmap(HashMap::new,map->map).fieldOf("spawn").orElse(new HashMap<>()).forGetter((stateSaver ->
                                stateSaver.spawn)),
                        Codec.unboundedMap(Identifier.CODEC, WorldProperties.SpawnPoint.CODEC).xmap(HashMap::new,map->map).fieldOf("world_spawn").orElse(new HashMap<>()).forGetter((stateSaver ->
                                stateSaver.worldSpawn)),
                        Codec.unboundedMap(Identifier.CODEC, Codec.unboundedMap(Codec.STRING,ServerPlayerEntity.Respawn.CODEC).xmap(HashMap::new,map->map)).xmap(HashMap::new, map->map).fieldOf("respawn").orElse(new HashMap<>()).forGetter((stateSaver ->
                                stateSaver.respawn))
                    ).apply(instance,StateSaver::new));
    private StateSaver(LinkedList<StorageInfo> nbtList,
                      LinkedList<PositionInfo> posList,
                      HashMap<Identifier,EnderDragonFight.Data> dragonFight,
                      HashMap<Identifier,Long> seed,
                      HashMap<Identifier,WarpRestrictionInfo> restrictionInfoHashMap,
                      HashMap<String, Integer> gamemode,
                      HashMap<Identifier,BlockPos> spawn,
                      HashMap<Identifier, WorldProperties.SpawnPoint> worldSpawn,
                      HashMap<Identifier, HashMap<String, ServerPlayerEntity.Respawn>> respawn)
    {
        this.nbtList=nbtList;
        this.posList=posList;
        this.dragonFight=dragonFight;
        this.seed=seed;
        this.restrictionInfoHashMap=restrictionInfoHashMap;
        this.gamemode=gamemode;
        this.spawn=spawn;
        this.worldSpawn=worldSpawn;
        this.respawn=respawn;
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
                new HashMap<>()
        );
    }
    private static final PersistentStateType<StateSaver> type = new PersistentStateType<>(
            MOD_ID,
            StateSaver::new,
            CODEC,
            DataFixTypes.PLAYER
    );
    private void update()
    {
        for(Map.Entry<Identifier, BlockPos> posEntry:spawn.entrySet())
        {
            worldSpawn.put(getDimensionId(posEntry.getKey()), WorldProperties.SpawnPoint.create(RegistryKey.of(RegistryKeys.WORLD,posEntry.getKey()),posEntry.getValue(),0,0));
        }
        spawn.clear();
    }
    public static StateSaver getServerState(MinecraftServer server) {
        return getServerState(Objects.requireNonNull(server.getWorld(World.OVERWORLD)));
    }
    //DO NOT USE THIS UNLESS DURING CONSTRUCTION OF OVERWORLD
    public static StateSaver getServerState(ServerWorld world) {
        PersistentStateManager persistentStateManager = world.getPersistentStateManager();
        StateSaver state = persistentStateManager.getOrCreate(type);
        state.markDirty();
        state.update();
        return state;
    }
    public static class StorageInfo {
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
        public static final Codec<Pair<ItemStack,Byte>> SLOT_CODEC=Codec.pair(ItemStack.CODEC.orElse(null),Codec.BYTE.fieldOf(SLOT).codec());
        public static final Codec<StorageInfo> CODEC= RecordCodecBuilder.create((instance) ->
                instance.group
                        (
                                Codec.STRING.fieldOf(PLAYER).forGetter((info)->info.player),
                                Codec.STRING.fieldOf(DIMENSION).forGetter((info)->info.dimension),
                                SLOT_CODEC.listOf().xmap(Util::listToArrayList, list->list).fieldOf(INVENTORY).orElse(new ArrayList<>()).forGetter((info)->info.inventory),
                                SLOT_CODEC.listOf().xmap(Util::listToArrayList, list->list).fieldOf(ENDER).orElse(new ArrayList<>()).forGetter((info)->info.ender),
                                Codec.INT.fieldOf("xp").orElse(0).forGetter((info)->info.xp),
                                Codec.FLOAT.fieldOf("hp").orElse(20f).forGetter((info)->info.hp),
                                Codec.INT.fieldOf("food").orElse(20).forGetter((info)->info.food),
                                Codec.FLOAT.fieldOf("food2").orElse(5.0f).forGetter((info)->info.food2),
                                Codec.INT.fieldOf("air").orElse(300).forGetter((info)->info.air),
                                Codec.INT.fieldOf("gamemode").orElse(0).forGetter((info)->info.gamemode)

                        ).apply(instance, StorageInfo::new));
    }

    public static class PositionInfo {
        public String player;
        public String dimension;
        public String rdim;
        public Vec3d pos;

        public PositionInfo(String player, String dimension, String rdim, Vec3d pos) {
            this.player = player;
            this.dimension = dimension;
            this.rdim = rdim;
            this.pos = pos;
        }

        public static final Codec<Vec3d> VEC_3_D_CODEC=RecordCodecBuilder.create((instance)->
                instance.group(
                        Codec.DOUBLE.fieldOf("x").forGetter((v3d)->v3d.x),
                        Codec.DOUBLE.fieldOf("y").forGetter((v3d)->v3d.y),
                        Codec.DOUBLE.fieldOf("z").forGetter((v3d)->v3d.z)
                ).apply(instance,Vec3d::new)
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
    }
}
