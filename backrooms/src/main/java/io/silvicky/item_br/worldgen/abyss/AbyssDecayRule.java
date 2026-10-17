package io.silvicky.item_br.worldgen.abyss;

import io.silvicky.item.worldgen.DecayRule;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.RandomState;
import org.jspecify.annotations.NonNull;

public class AbyssDecayRule implements DecayRule
{
    @Override
    public boolean decay(@NonNull ChunkPos pos, @NonNull RandomState randomState)
    {
        int dis=Math.max(Math.abs(pos.x()),Math.abs(pos.z()));
        return dis<4;
    }

    @Override
    public String name()
    {
        return "abyss";
    }
}
