package io.silvicky.item.backrooms;

import com.mojang.serialization.Codec;

public enum EntityVisibilityLevel
{
    NORMAL((byte) 0),
    LIMITED((byte) 1),
    NO_PLAYER((byte) 2),
    NONE((byte) 3);
    final byte id;
    EntityVisibilityLevel(byte id){this.id=id;}
    private static EntityVisibilityLevel getById(byte id)
    {
        for(EntityVisibilityLevel level:EntityVisibilityLevel.values())
        {
            if(level.id==id)return level;
        }
        return NORMAL;
    }
    private byte getId(){return id;}
    public static final Codec<EntityVisibilityLevel> CODEC= Codec.BYTE.xmap(EntityVisibilityLevel::getById,EntityVisibilityLevel::getId);

}
