package io.silvicky.item;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.boss.dragon.EnderDragonFight;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.PersistentStateType;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;

import static io.silvicky.item.InventoryManager.DIMENSION;
import static io.silvicky.item.InventoryManager.PLAYER;

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
        public static final Codec<Pair<ItemStack,Byte>> SLOT_CODEC=Codec.pair(ItemStack.CODEC,Codec.BYTE.fieldOf("Slot").codec());

        public static final Codec<StorageInfo> CODEC= RecordCodecBuilder.create((instance) ->
                instance.group
                        (
                                Codec.STRING.fieldOf(PLAYER).forGetter((info)->info.player),
                                Codec.STRING.fieldOf(DIMENSION).forGetter((info)->info.dimension),
                                SLOT_CODEC.listOf().xmap(ArrayList::new, list->list).fieldOf("inventory").orElse(new ArrayList<>()).forGetter((info)->info.inventory),
                                SLOT_CODEC.listOf().xmap(ArrayList::new, list->list).fieldOf("ender").orElse(new ArrayList<>()).forGetter((info)->info.ender),
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
}
