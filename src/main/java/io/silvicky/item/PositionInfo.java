package io.silvicky.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.math.Vec3d;

import static io.silvicky.item.InventoryManager.*;

public class PositionInfo {
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

                    ).apply(instance,PositionInfo::new));
}
