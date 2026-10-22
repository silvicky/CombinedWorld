package io.silvicky.item.backrooms;

import java.util.UUID;

/**
 * The two identities of a copy: a fresh int id and a patched UUID.
 *
 * <p><b>Both are needed, and they are different problems.</b> The wire addresses an entity by int id almost
 * everywhere - {@code RemoveEntities}, {@code SetEntityMotion}, {@code EntityPositionSync}, {@code SetEntityData},
 * {@code SoundEntity}, {@code Animate}, {@code UpdateAttributes}, and both fields of {@code SetPassengers} and
 * {@code SetEntityLink}. Only {@code ClientboundAddEntityPacket} carries a UUID, and it carries it <em>beside</em>
 * the id. So a copy that is given a patched UUID and keeps the source's int id is not distinguishable from the
 * source on the wire.
 *
 * <p>The two collisions also fail differently. On the client,
 * {@code EntityLookup.add(UUID)} refuses a duplicate UUID outright:
 *
 * <pre>
 *   if (this.byUuid.containsKey(uuid)) LOGGER.warn("Duplicate entity UUID ...");
 *   else { this.byUuid.put(uuid, entity); this.byId.put(entity.getId(), entity); }
 * </pre>
 *
 * so a duplicate UUID means the copy is silently absent. A duplicate int id, on the other hand, overwrites
 * whatever was in {@code byId} with no check at all - one copy shadows another and every later update reaches
 * the wrong entity. Both have to be unique; neither check protects the other.
 */
public final class CopyIdentity
{
    /**
     * Server ids are allocated by a monotonically increasing counter and are small, so 28 bits is room to
     * spare. The bound is what makes the id layout below injective.
     */
    public static final int MAX_SOURCE_ID = 1 << 28;

    /** How many bits of the int id the tag occupies, above {@link #MAX_SOURCE_ID}. */
    private static final int TAG_SHIFT = 28;

    private CopyIdentity() {}

    /**
     * Which copy of an entity this is: 1, 2 or 3, derived from the band's position in the pattern.
     *
     * <p>Keyed on the <b>position</b>, not on the slot, deliberately. A tag that is a constant function of
     * position makes remapping a hierarchy free - a copied vehicle and its copied passenger are both
     * {@code tag(position)}, so {@code copyId(position, otherId)} is the whole rule - and it stays correct if
     * the window ever stops recentring on every crossing. Two copies never share a tag, because the window
     * shows three consecutive positions.
     *
     * <p>Tag 0 is never produced: it means "the real identity", which is what the player's own band uses.
     */
    public static int tag(int position)
    {
        return 1 + Math.floorMod(position, AbyssWindow.WINDOW_LAYERS);
    }

    /**
     * Whether an entity can be copied at all.
     *
     * <p>An id beyond the mask would collide with a real id on the client, which is exactly the silent
     * overwrite this class exists to avoid. 268 million entity creations in one server lifetime is not
     * plausible, so it is treated as a state to log rather than to design around.
     */
    public static boolean usable(int sourceId)
    {
        return sourceId >= 0 && sourceId < MAX_SOURCE_ID;
    }

    /**
     * The copy's int id.
     *
     * <p>Not {@code sourceId ^ tag}: that collides with real ids once the entity counter reaches the tag bits,
     * and on a long-lived server it will. Packing the tag above the source id is injective for
     * {@code sourceId < 2^28} and cannot reach any id vanilla allocates.
     */
    public static int copyId(int position, int sourceId)
    {
        return (tag(position) << TAG_SHIFT) | sourceId;
    }

    /** The source id a copy's id refers to, for packets arriving from a client. */
    public static int sourceId(int copyId)
    {
        return copyId & (MAX_SOURCE_ID - 1);
    }

    /** The tag a copy's id carries, or 0 if it is not a copy id at all. */
    public static int tagOfCopyId(int copyId)
    {
        return copyId >>> TAG_SHIFT;
    }

    /**
     * The copy's UUID.
     *
     * <p>Only bits 32 and 33 of the most significant half are touched, which leaves alone:
     *
     * <ul>
     *   <li>the version nibble (bits 48-51) and the variant bits (62-63), so a patched v4 UUID is still a
     *       well-formed v4 UUID;</li>
     *   <li>the entire least significant half, which is where Floodgate puts a Bedrock player's XUID
     *       ({@code new UUID(0, xuid)}). Patching the low half would collapse distinct Bedrock players onto
     *       one another - the same reasoning the design note gives, and it holds.</li>
     * </ul>
     *
     * <p>Three tags and so three distinct results per entity. A collision between two <em>different</em>
     * entities' copies needs their real UUIDs to differ in exactly those bits, which is unlikely enough to log
     * rather than to design around; {@code AbyssEntityCopies} treats a duplicate id as the authoritative check.
     */
    public static UUID copyUuid(int position, UUID source)
    {
        return new UUID(source.getMostSignificantBits() ^ ((long) tag(position) << 32),
                        source.getLeastSignificantBits());
    }
}
