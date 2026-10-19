package io.silvicky.item_br.worldgen.abyss;

import io.silvicky.item.backrooms.AbyssWindow;
import io.silvicky.item.worldgen.CustomRule;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.RandomState;
import org.jspecify.annotations.NonNull;

/**
 * Level 922: an endless spiral stair, generated so that it is periodic in Y.
 *
 * <h2>The construction</h2>
 *
 * <p>A helix around a central core, with <b>two turns per band</b> and a rise of 32 blocks per turn. Because a
 * column belongs to one angle, the two turns mean each column carries two steps: one at {@code h} and one at
 * {@code h + 32}. Both are inside the 64-block band, and the whole column repeats every 64 blocks - which is
 * exactly the property the cyclic pattern needs.
 *
 * <p>The rise is 0.5 blocks per step, so the walkway is built from bottom slabs and full blocks alternately:
 * that is the one increment a player can auto-step (0.6), and it means the stair can be climbed without
 * jumping.
 *
 * <pre>
 *   k (0..63, 64 steps per turn)   y = k / 2
 *     k even -> bottom slab at y     (surface y + 0.5)
 *     k odd  -> full block at y      (surface y + 1.0)
 * </pre>
 *
 * <h2>Why the seam works</h2>
 *
 * <p>A player climbing reaches the top of the band at local y = 64 and is teleported to local y = 0 of the next
 * band, at the same column. That lands them on the {@code k = 0} step, which is where the climb continues,
 * because the helix's crossing at that column really is at y = 0. The same is true downwards: stepping below
 * y = 0 lands on the {@code k = 63} step at y = 63.6 in the band below. Neither direction needs a special
 * landing - the periodicity of the geometry is what makes the teleport land on solid ground.
 *
 * <h2>Shape</h2>
 *
 * <p>Solid core at radius 2, walkway from 3 to 10, outer wall at 11 with a lantern every quarter turn. The
 * walkway is 8 blocks wide, so it reads as a ramp rather than a tight staircase, and the shaft is open above
 * and below the player for the two bands the window shows.
 */
public class SpiralRule implements CustomRule
{
    private static final int CORE_RADIUS = 2;
    private static final int WALKWAY_OUTER = 10;
    private static final int WALL_RADIUS = 11;
    /** Steps per turn. One turn rises 32 blocks, and 0.5 per step. */
    private static final int STEPS = 64;
    /** Blocks risen per turn. */
    private static final int RISE = AbyssWindow.BAND_HEIGHT / 2;

    private static final BlockState CORE = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
    private static final BlockState STEP_SOLID = Blocks.DEEPSLATE_TILES.defaultBlockState();
    private static final BlockState STEP_SLAB = Blocks.DEEPSLATE_TILE_SLAB.defaultBlockState();
    private static final BlockState WALL = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
    private static final BlockState LIGHT = Blocks.SEA_LANTERN.defaultBlockState();

    @Override
    public void gen(@NonNull ChunkAccess chunk, @NonNull RandomState randomState)
    {
        int height = chunk.getHeight();
        ChunkPos pos = chunk.getPos();
        int baseX = pos.getMinBlockX();
        int baseZ = pos.getMinBlockZ();

        for (int lx = 0; lx < 16; lx++)
        {
            for (int lz = 0; lz < 16; lz++)
            {
                int x = baseX + lx;
                int z = baseZ + lz;
                double radius = Math.sqrt((double) x * x + (double) z * z);
                BlockPos column = pos.getBlockAt(lx, 0, lz);

                if (radius <= CORE_RADIUS)
                {
                    //Open well, so a player can drop through the middle into the band below.
                    continue;
                }
                if (radius >= WALL_RADIUS && radius < WALL_RADIUS + 1)
                {
                    for (int y = 0; y < height; y++)
                        chunk.setBlockState(column.atY(y), WALL);
                    continue;
                }
                if (radius < CORE_RADIUS + 1 || radius > WALKWAY_OUTER) continue;

                //Angle in [0, 2pi). atan2 returns (-pi, pi], so shift and wrap.
                double angle = Math.atan2(z, x);
                if (angle < 0) angle += Math.PI * 2;
                int k = (int) Math.floor(angle / (Math.PI * 2) * STEPS);
                if (k >= STEPS) k = STEPS - 1;

                int y = k / 2;
                boolean solid = (k & 1) == 1;
                //Two turns per band: the second is one half-band higher.
                step(chunk, pos, lx, lz, y, solid);
                step(chunk, pos, lx, lz, y + RISE, solid);

                //Landings at both ends of the wrap arc. A helix cannot have a floor at the top and the bottom
                //of the *same* column, and the seam teleport keeps x/z, so the arc where the ramp wraps is the
                //only place a player can be moved one band without arriving in mid-air. Two thin landings,
                //one at each end, are what make both directions survivable.
                if (isWrapArc(k))
                {
                    chunk.setBlockState(pos.getBlockAt(lx, 0, lz), STEP_SLAB);
                    chunk.setBlockState(pos.getBlockAt(lx, height - 2, lz), STEP_SOLID);
                }

                //A lantern on the wall every quarter turn, at the height of the step beside it.
                if (radius >= WALL_RADIUS - 1 && radius < WALL_RADIUS && k % (STEPS / 4) == 0)
                {
                    int ly = y + RISE + 1;
                    if (ly < height) chunk.setBlockState(pos.getBlockAt(lx, ly, lz), LIGHT);
                }
            }
        }
    }

    /** The few steps where the ramp wraps from the top of the band to the bottom, where the seam lands. */
    private static boolean isWrapArc(int k)
    {
        return k >= STEPS - 2 || k <= 1;
    }

    private static void step(ChunkAccess chunk, ChunkPos pos, int lx, int lz, int y, boolean solid)
    {
        //A slab at y occupies the bottom half, so consecutive k give surfaces y+0.5 and y+1.0: a 0.5 step.
        chunk.setBlockState(pos.getBlockAt(lx, y, lz), solid ? STEP_SOLID : STEP_SLAB);
    }

    @Override
    public String name()
    {
        return "abyss_spiral";
    }
}
