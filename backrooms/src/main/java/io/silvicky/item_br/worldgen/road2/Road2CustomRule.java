package io.silvicky.item_br.worldgen.road2;

import com.mojang.serialization.MapCodec;
import io.silvicky.item.worldgen.CustomRuleAdv;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.RandomState;
import org.jspecify.annotations.NonNull;

import java.util.concurrent.CompletableFuture;

public class Road2CustomRule implements CustomRuleAdv
{
    public static final MapCodec<Road2CustomRule> CODEC= MapCodec.unit(Road2CustomRule::new);

    public static final Identifier ID=Identifier.parse("silvicky:road2");

    private Road2CacheManager cache=null;

    @Override
    public CompletableFuture<ChunkAccess> gen(@NonNull ChunkAccess chunk, @NonNull RandomState randomState)
    {
        if(cache==null)
        {
            cache=new Road2CacheManager(randomState);
        }
        return cache.generate(chunk);
    }

    @Override
    public MapCodec<? extends CustomRuleAdv> codec()
    {
        return CODEC;
    }
}
