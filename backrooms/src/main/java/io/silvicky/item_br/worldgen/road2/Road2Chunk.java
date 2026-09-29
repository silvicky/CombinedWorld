package io.silvicky.item_br.worldgen.road2;

import io.silvicky.item_br.worldgen.SimpleChunk;

public class Road2Chunk extends SimpleChunk<Road2Blocks>
{
    private static final Road2Blocks[] VALUES=Road2Blocks.values();

    static
    {
        //SimpleChunk stores ordinals+1 and reads them back through this array, so the two have to agree.
        for (int i = 0; i < VALUES.length; i++) {
            if (VALUES[i].id()!=i) {
                throw new IllegalStateException("Road2Blocks id() must match declaration order, but "
                        +VALUES[i]+".id()="+VALUES[i].id()+" at index "+i);
            }
        }
    }

    public Road2Chunk()
    {
        super(VALUES);
    }
}
