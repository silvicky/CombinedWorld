package io.silvicky.item_br.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.Arrays;

/**
 * Block states for one chunk column, stored as a flat array of enum ordinals rather than a
 * {@code HashMap<BlockPos, T>}.
 *
 * <p>The map version cost about 6.3 MB per region (a {@code BlockPos} and a {@code HashMap.Node} per block,
 * plus a table array per chunk), which is what filled the heap. Ordinals in a {@code byte[]} indexed by
 * {@code (y - baseY) * 256 + lz * 16 + lx} cost about 0.55 MB per region - 11x smaller - and remove all
 * per-block allocation from the region render.
 *
 * <p>The array is only as tall as the layers actually used, so a plain road chunk costs 256 bytes and only
 * the few chunks holding a {@code TALL_LIGHT} column pay for the 17 extra layers.
 *
 * <p>Every position reaching here is routed by {@link ChunkGenCache#setBlockState} through
 * {@code ChunkPos.containing}, so {@code x & 15} / {@code z & 15} are always the in-chunk coordinates and
 * are correct for negative world coordinates too.
 */
public class SimpleChunk<T extends AbstractBlock<T>>
{
    /** 16*16 block columns in a chunk. */
    private static final int AREA=256;

    /** Lowest layer the initial array covers. Road surfaces are at y 0..gapHeight, so 0 is the floor. */
    private static final int BASE_Y=0;

    /** Ordinals offset by one, so 0 means "no block". Always a whole number of layers long. */
    private byte[] states=new byte[AREA];

    private int baseY=BASE_Y;

    private final T[] values;

    /**
     * @param values every block in declaration order, so that {@code values()[block.id()]} resolves back to
     *               the block. Callers should validate that once at class-init rather than per chunk.
     */
    public SimpleChunk(T[] values)
    {
        this.values=values;
    }

    private int layers()
    {
        return states.length/AREA;
    }

    /**
     * Retained size of this chunk, for cache-memory reporting.
     */
    public long retainedBytes()
    {
        return states.length + 32L;
    }

    public void setBlockState(BlockPos pos, T block)
    {
        if (block==null) return;
        set((pos.getX()&15), pos.getY(), (pos.getZ()&15), (byte)(block.id()+1), block);
    }

    private void set(int lx, int y, int lz, byte id, T block)
    {
        int layer=y-baseY;
        if (layer<0) {
            growDown(-layer);
            layer=y-baseY;
        } else if (layer>=layers()) {
            growUp(layer+1);
        }
        int i=layer*AREA+lz*16+lx;
        byte cur=states[i];
        //Highest-ranked block wins, where rank is the enum's natural order. This rule used to live in
        //Road2Chunk; enum compareTo is ordinal order, so comparing stored ids is equivalent and avoids
        //a lookup. Skip when the incumbent is already >= the newcomer.
        if (cur!=0 && block.compareTo(values[cur-1])<=0) return;
        states[i]=id;
    }

    private void growDown(int extra)
    {
        byte[] bigger=new byte[(layers()+extra)*AREA];
        System.arraycopy(states,0,bigger,extra*AREA,states.length);
        baseY-=extra;
        states=bigger;
    }

    private void growUp(int needed)
    {
        if (needed>layers()) states=Arrays.copyOf(states,needed*AREA);
    }

    public T getBlockState(BlockPos pos)
    {
        int layer=pos.getY()-baseY;
        if (layer<0 || layer>=layers()) return null;
        byte id=states[layer*AREA+(pos.getZ()&15)*16+(pos.getX()&15)];
        return id==0?null:values[id-1];
    }

    public void apply(ChunkAccess chunk)
    {
        //Mutable, because every apply used to allocate a BlockPos per block.
        BlockPos.MutableBlockPos pos=new BlockPos.MutableBlockPos();
        int n=layers();
        for (int layer=0; layer<n; layer++) {
            int y=baseY+layer;
            int off=layer*AREA;
            for (int i=0; i<AREA; i++) {
                byte id=states[off+i];
                if (id==0) continue;
                pos.set(i&15, y, i>>4);
                values[id-1].apply(pos, chunk);
            }
        }
    }

    public void combine(SimpleChunk<T> other)
    {
        int n=other.layers();
        for (int layer=0; layer<n; layer++) {
            int y=other.baseY+layer;
            int off=layer*AREA;
            for (int i=0; i<AREA; i++) {
                byte id=other.states[off+i];
                if (id==0) continue;
                set(i&15, y, i>>4, id, other.values[id-1]);
            }
        }
    }
}
