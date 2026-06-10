package io.silvicky.item.worldgen;

import net.minecraft.world.level.chunk.ChunkAccess;
import org.jspecify.annotations.NonNull;

import java.util.Random;

public class ExampleDecayRule implements DecayRule
{
    private final Random random=new Random();
    @Override
    public boolean decay(@NonNull ChunkAccess chunk)
    {
        int dis=Math.max(Math.abs(chunk.getPos().x()),Math.abs(chunk.getPos().z()));
        return random.nextInt(64)<dis-64;
    }

    @Override
    public String name()
    {
        return "example";
    }
}
