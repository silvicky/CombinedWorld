package io.silvicky.item.worldgen;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.RandomState;
import org.jspecify.annotations.NonNull;

public class ExampleDecayRule implements DecayRule
{
    private static final Identifier key=Identifier.parse("silvicky:example_decay");

    @Override
    public boolean decay(@NonNull ChunkAccess chunk, @NonNull RandomState randomState)
    {
        int dis=Math.max(Math.abs(chunk.getPos().x()),Math.abs(chunk.getPos().z()));
        return randomState.getOrCreateRandomFactory(key).at(chunk.getPos().x(),0,chunk.getPos().z()).nextInt(64)<dis-64;
    }

    @Override
    public String name()
    {
        return "example";
    }
}
