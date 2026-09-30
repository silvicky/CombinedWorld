package io.silvicky.item.backrooms;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.world.level.ChunkPos;

import java.util.Map;
import java.util.function.Consumer;

/**
 * How server chunk coordinates map onto the coordinates the client is shown.
 *
 * <p>Three cases, in increasing cost:
 * <ul>
 *   <li>{@link Kind#IDENTITY} - nothing to remap. Every chunk-tracking injection in this mod becomes a
 *       no-op and vanilla runs its own path unchanged.</li>
 *   <li>{@link Kind#TRANSLATION} - a constant {@code (dx, dz)} offset, used by the linear transformer.
 *       Vanilla's containment test is built entirely from {@code abs(a - b)}
 *       ({@code ChunkTrackingView.isWithinDistance} subtracts and then takes an absolute value, and
 *       {@code Positioned.minX()} is {@code center.x() - viewDistance - 1}), so shifting <em>both</em> the
 *       point and the centre by the same offset leaves every answer unchanged. A translation therefore
 *       produces exactly vanilla's chunk set and needs no enumeration of its own.</li>
 *   <li>{@link Kind#MAPPING} - a general per-chunk table, required by the xor and random transformers,
 *       which really do move individual chunks to unrelated places.</li>
 * </ul>
 *
 * <p>The enumerated window differs by case, and this is deliberate:
 * <ul>
 *   <li>Identity and translation use vanilla's own enumeration unchanged - {@code center +/- (viewDistance + 1)}
 *       filtered by {@code border = false}, matching {@code ChunkTrackingView.Positioned#forEach}.</li>
 *   <li>A general mapping keeps the original enumeration: every key of the transformer's own table, filtered
 *       by {@code border = true}. That wider area is <em>required</em>, not a fudge, and has two separate
 *       reasons. First, xor and random permute chunks, so the server chunks whose images cover the client's
 *       view are neither a square nor centred on the player. Second, the extra ring is in-flight margin: the
 *       player must never be able to walk into a chunk the server has not sent yet, because vanilla's own
 *       {@code center +/- (viewDistance + 1)} box is exactly the bounding box of its <em>border</em> test,
 *       while {@code forEach} only ever emits up to {@code viewDistance}. Trimming to vanilla's emitted set
 *       is what produces visible glitches, so this window is deliberately not configurable.</li>
 * </ul>
 */
public final class ChunkRemap
{
    public enum Kind { IDENTITY, TRANSLATION, MAPPING }

    /**
     * The mapping window's filter has to admit the border ring, or the outermost chunks of the transformer's
     * table are dropped. Not configurable: trimming this makes the player outrun the sent chunks and the
     * client walks into territory the server never sent.
     */
    private static final boolean MAPPING_BORDER = true;

    /** No remapping: use vanilla's own chunk tracking. */
    public static final ChunkRemap IDENTITY = new ChunkRemap(Kind.IDENTITY, 0, 0, null);

    private final Kind kind;
    private final int dx;
    private final int dz;
    private final Map<ChunkPos,ChunkPos> map;

    private ChunkRemap(Kind kind, int dx, int dz, Map<ChunkPos,ChunkPos> map)
    {
        this.kind = kind;
        this.dx = dx;
        this.dz = dz;
        this.map = map;
    }

    public static ChunkRemap identity()
    {
        return IDENTITY;
    }

    public static ChunkRemap translation(int dx, int dz)
    {
        return (dx == 0 && dz == 0) ? IDENTITY : new ChunkRemap(Kind.TRANSLATION, dx, dz, null);
    }

    public static ChunkRemap mapping(Map<ChunkPos,ChunkPos> map)
    {
        return map == null ? IDENTITY : new ChunkRemap(Kind.MAPPING, 0, 0, map);
    }

    public Kind kind()
    {
        return kind;
    }

    /** True when this remap leaves chunk tracking exactly as vanilla would have it. */
    public boolean isIdentity()
    {
        return kind == Kind.IDENTITY;
    }

    /** True when the transform is a constant offset, i.e. no per-chunk table is needed. */
    public boolean isTranslation()
    {
        return kind == Kind.TRANSLATION;
    }

    /**
     * True when this remap must enumerate its own chunk set. False for identity and translation, both of
     * which yield vanilla's set exactly and so can simply let vanilla do the walking.
     */
    public boolean needsEnumeration()
    {
        return kind == Kind.MAPPING;
    }

    public int offsetX()
    {
        return dx;
    }

    public int offsetZ()
    {
        return dz;
    }

    public Map<ChunkPos,ChunkPos> map()
    {
        return map;
    }

    /**
     * Client-facing coordinate for a server chunk, or null when this position is not part of the view.
     */
    public ChunkPos toClient(ChunkPos pos)
    {
        return switch (kind) {
            case IDENTITY -> pos;
            case TRANSLATION -> new ChunkPos(pos.x() + dx, pos.z() + dz);
            case MAPPING -> map.get(pos);
        };
    }

    public boolean isWithinDistance(ChunkTrackingView.Positioned view, ChunkPos pos, boolean border)
    {
        return switch (kind) {
            //Translation invariant, so vanilla's own answer applies to the server coordinates.
            case IDENTITY, TRANSLATION -> withinDistance(pos, view, border);
            case MAPPING -> {
                ChunkPos mapped = map.get(pos);
                ChunkPos mappedCenter = map.get(view.center());
                yield mapped != null && mappedCenter != null
                        && ChunkTrackingView.isWithinDistance(mapped.x(), mapped.z(), view.viewDistance(),
                                                          mappedCenter.x(), mappedCenter.z(), border);
            }
        };
    }

    private static boolean withinDistance(ChunkPos pos, ChunkTrackingView.Positioned view, boolean border)
    {
        ChunkPos center = view.center();
        return ChunkTrackingView.isWithinDistance(pos.x(), pos.z(), view.viewDistance(),
                                                  center.x(), center.z(), border);
    }

    /**
     * Feeds every server chunk the client should know about to {@code consumer}. Identity and translation
     * delegate straight to vanilla, which is both correct for them and the cheapest option available.
     */
    public void forEach(ChunkTrackingView.Positioned view, Consumer<ChunkPos> consumer)
    {
        if (kind != Kind.MAPPING)
        {
            view.forEach(consumer);
            return;
        }
        //Walk the transformer's own table, border ring included. xor and random build that table over a
        //deliberately wider area than vanilla, because their mapped view is not a square centred on the
        //player: the client needs chunks whose images cover its whole view, and the inverse image of a
        //scattered view is neither square nor player-centred. The ring also acts as in-flight margin, so
        //the player never walks into a chunk the server has not sent yet. Do not trim it.
        for (ChunkPos pos : map.keySet())
        {
            if (isWithinDistance(view, pos, MAPPING_BORDER)) consumer.accept(pos);
        }
    }

    /**
     * The chunks in {@code view} but not {@code other} go to {@code removed}, and vice versa. Keys are
     * packed so the common case allocates longs rather than a {@code ChunkPos} per slot in the window.
     */
    public void difference(ChunkTrackingView.Positioned view, ChunkTrackingView.Positioned other,
                           Consumer<ChunkPos> added, Consumer<ChunkPos> removed)
    {
        if (kind != Kind.MAPPING)
        {
            LongSet mine = new LongOpenHashSet();
            view.forEach(pos -> mine.add(pos.pack()));
            LongSet theirs = new LongOpenHashSet();
            other.forEach(pos -> theirs.add(pos.pack()));
            diffKeys(mine, theirs, added, removed);
            return;
        }
        //A general mapping can send one server chunk to different client positions over time, so the
        //comparison has to be on the mapped value, not just on presence of the key. The consumers still
        //receive the *server* position, exactly as before: it is the forget-packet consumer that applies
        //the mapping, so handing it an already-mapped position would transform the offset twice.
        Long2ObjectMap<ChunkPos> mine = collectMapped(view);
        Long2ObjectMap<ChunkPos> theirs = collectMapped(other);
        for (Long2ObjectMap.Entry<ChunkPos> entry : mine.long2ObjectEntrySet())
        {
            ChunkPos mapped = theirs.get(entry.getLongKey());
            if (mapped == null || !mapped.equals(entry.getValue())) removed.accept(ChunkPos.unpack(entry.getLongKey()));
        }
        for (Long2ObjectMap.Entry<ChunkPos> entry : theirs.long2ObjectEntrySet())
        {
            ChunkPos mapped = mine.get(entry.getLongKey());
            if (mapped == null || !mapped.equals(entry.getValue())) added.accept(ChunkPos.unpack(entry.getLongKey()));
        }
    }

    private void diffKeys(LongSet mine, LongSet theirs, Consumer<ChunkPos> added, Consumer<ChunkPos> removed)
    {
        for (long key : mine)
        {
            if (!theirs.contains(key)) removed.accept(ChunkPos.unpack(key));
        }
        for (long key : theirs)
        {
            if (!mine.contains(key)) added.accept(ChunkPos.unpack(key));
        }
    }

    private Long2ObjectMap<ChunkPos> collectMapped(ChunkTrackingView.Positioned view)
    {
        Long2ObjectMap<ChunkPos> out = new Long2ObjectOpenHashMap<>();
        forEach(view, pos -> {
            ChunkPos client = toClient(pos);
            if (client != null) out.put(pos.pack(), client);
        });
        return out;
    }
}
