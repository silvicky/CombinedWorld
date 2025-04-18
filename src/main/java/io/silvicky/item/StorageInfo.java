package io.silvicky.item;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;

import static io.silvicky.item.InventoryManager.*;

public class StorageInfo {
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
                            SLOT_CODEC.listOf().xmap(ArrayList::new, list->list).optionalFieldOf("inventory", new ArrayList<>()).forGetter((info)->info.inventory),
                            SLOT_CODEC.listOf().xmap(ArrayList::new, list->list).optionalFieldOf("ender", new ArrayList<>()).forGetter((info)->info.ender),
                            Codec.INT.optionalFieldOf("xp",0).forGetter((info)->info.xp),
                            Codec.FLOAT.optionalFieldOf("hp",20f).forGetter((info)->info.hp),
                            Codec.INT.optionalFieldOf("food",20).forGetter((info)->info.food),
                            Codec.FLOAT.optionalFieldOf("food2",5.0f).forGetter((info)->info.food2),
                            Codec.INT.optionalFieldOf("air",300).forGetter((info)->info.air),
                            Codec.INT.optionalFieldOf("gamemode",0).forGetter((info)->info.gamemode)

                    ).apply(instance,StorageInfo::new));
}
