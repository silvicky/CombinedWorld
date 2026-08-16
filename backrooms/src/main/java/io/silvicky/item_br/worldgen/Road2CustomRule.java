package io.silvicky.item_br.worldgen;

import com.mojang.serialization.MapCodec;
import io.silvicky.item.worldgen.CustomRuleAdv;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.RandomState;
import org.jspecify.annotations.NonNull;

public class Road2CustomRule implements CustomRuleAdv
{
    public static final MapCodec<Road2CustomRule> CODEC= MapCodec.unit(Road2CustomRule::new);

    public static final Identifier ID=Identifier.parse("silvicky:road2");

    private ChunkGenCache cache=null;

    @Override
    public void gen(@NonNull ChunkAccess chunk, @NonNull RandomState randomState)
    {
        if(cache==null)
        {
            ServerLevel level = (ServerLevel) (chunk.levelHeightAccessor);
            cache=new Road2Cache(level,randomState);
        }
        cache.apply(chunk);
    }

    @Override
    public MapCodec<? extends CustomRuleAdv> codec()
    {
        return CODEC;
    }
}
