package io.silvicky.item_br.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.function.BiConsumer;

public enum Road2Blocks implements AbstractBlock<Road2Blocks> {
    EDGE((b,c)->{
        c.setBlockState(b, Blocks.CONCRETE.white().defaultBlockState());
        c.setBlockState(b.above(), Blocks.COBBLESTONE.defaultBlockState());
    }),
    DASH((b,c)->c.setBlockState(b, Blocks.CONCRETE.white().defaultBlockState())),
    ROAD((b,c)->c.setBlockState(b, Blocks.CONCRETE.black().defaultBlockState())),
    WALL((b,c)->c.setBlockState(b, Blocks.CONCRETE.red().defaultBlockState()));
    public final BiConsumer<BlockPos, ChunkAccess> action;

    Road2Blocks(BiConsumer<BlockPos, ChunkAccess> action) {
        this.action = action;
    }

    @Override
    public void apply(BlockPos blockPos, ChunkAccess chunkAccess) {
        action.accept(blockPos, chunkAccess);
    }
}
