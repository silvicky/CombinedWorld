package io.silvicky.item.worldgen;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.RandomState;
import org.jspecify.annotations.NonNull;

public class ExampleDecayRule implements DecayRule
{
    private static final Identifier key=Identifier.parse("silvicky:example_decay");

    @Override
    public boolean decay(@NonNull ChunkPos pos, @NonNull RandomState randomState)
    {
        int dis=Math.max(Math.abs(pos.x()),Math.abs(pos.z()));
        return randomState.getOrCreateRandomFactory(key).at(pos.x(),0,pos.z()).nextInt(64)<dis-64;
    }

    @Override
    public String name()
    {
        return "example";
    }
}
