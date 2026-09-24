package io.silvicky.item_br.worldgen.road2;

import io.silvicky.item_br.worldgen.AbstractBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.function.BiConsumer;

public enum Road2Blocks implements AbstractBlock<Road2Blocks> {
    EDGE((b,c)->{
        c.setBlockState(b, Blocks.CONCRETE.white().defaultBlockState());
        c.setBlockState(b.above(), Blocks.COBBLESTONE.defaultBlockState());
    }),
    EMERGENCY_ROAD((b,c)->c.setBlockState(b, Blocks.CONCRETE.gray().defaultBlockState())),
    EMERGENCY_EDGE((b,c)->c.setBlockState(b, Blocks.CONCRETE.white().defaultBlockState())),
    ROAD((b,c)->c.setBlockState(b, Blocks.CONCRETE.black().defaultBlockState())),
    DASH((b,c)->c.setBlockState(b, Blocks.CONCRETE.white().defaultBlockState())),
    GRASS((b,c)->{
        c.setBlockState(b, Blocks.CONCRETE.black().defaultBlockState());
        c.setBlockState(b.above(), Blocks.GRASS_BLOCK.defaultBlockState());
    }),
    HIDDEN_LIGHT((b, c)->{
        c.setBlockState(b, Blocks.CONCRETE.black().defaultBlockState());
        c.setBlockState(b.above(), Blocks.GLOWSTONE.defaultBlockState());
    }),
    TALL_LIGHT((b, c)->{
        c.setBlockState(b, Blocks.CONCRETE.black().defaultBlockState());
        for(int i=1;i<16;i++)c.setBlockState(b.above(i), Blocks.IRON_BLOCK.defaultBlockState());
        c.setBlockState(b.above(16), Blocks.GLOWSTONE.defaultBlockState());//TODO can we access neighboring chunks?
    }),
    INTERNAL_EDGE((b,c)->{
        c.setBlockState(b, Blocks.CONCRETE.white().defaultBlockState());
        c.setBlockState(b.above(), Blocks.COBBLESTONE.defaultBlockState());
    }),
    WALL((b,c)->{
        c.setBlockState(b, Blocks.CONCRETE.red().defaultBlockState());
        c.setBlockState(b.above(), Blocks.CONCRETE.red().defaultBlockState());
    });
    public final BiConsumer<BlockPos, ChunkAccess> action;

    Road2Blocks(BiConsumer<BlockPos, ChunkAccess> action) {
        this.action = action;
    }

    @Override
    public void apply(BlockPos blockPos, ChunkAccess chunkAccess) {
        action.accept(blockPos, chunkAccess);
    }
}
