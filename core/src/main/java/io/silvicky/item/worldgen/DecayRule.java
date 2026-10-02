package io.silvicky.item.worldgen;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.RandomState;
import org.jspecify.annotations.NonNull;

/**
 * Decides whether a chunk should be left ungenerated.
 *
 * <p><b>Why a position rather than a chunk.</b> A chunk is not the only thing the generator is asked about.
 * The sampling methods - {@code getBaseHeight}, {@code getBaseColumn}, and the two heightmap queries - are
 * called with bare {@code x, z}, and the mob and structure lookups are called with a {@code BlockPos}. Handing
 * those callers a {@code ChunkAccess} they do not have would mean either building one per query or refusing to
 * decay them at all. A {@link ChunkPos} is the one thing all of them share: it is already inside the
 * {@code ChunkAccess} for the callers that have one, and it is one shift away for the rest.
 *
 * <p>It is also the honest granularity. Every rule here decides per chunk, so a rule that could be asked about
 * a single column would have to invent an answer for the seam between two chunks - inside one, outside the
 * other - that no per-chunk decision can give.
 */
public interface DecayRule
{
    /**
     * Whether the chunk at {@code pos} should be skipped.
     *
     * <p>{@code randomState} is the world's own noise state, so a rule can dither its boundary instead of
     * cutting a hard line. It is the same instance the generator generates terrain with, which keeps a rule's
     * randomness tied to the seed rather than to however many times it happened to be consulted.
     *
     * @param pos         the chunk being considered, in either coordinate system - the rule should not care,
     *                    and comparing against a fixed distance is the usual use
     * @param randomState the world's noise state, for rules that want a ragged boundary
     */
    boolean decay(@NonNull ChunkPos pos, @NonNull RandomState randomState);

    String name();
}
