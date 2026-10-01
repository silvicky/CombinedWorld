package io.silvicky.item.backrooms;

import net.minecraft.server.network.ServerCommonPacketListenerImpl;

/**
 * Gives other mixins access to the per-connection {@link EntityPositionRemapper}.
 *
 * <p>Needed because a Mixin class does not exist at runtime - its methods are merged into the target - so
 * there is no such thing as casting a connection to the mixin class to reach it. Declaring the accessor as
 * an interface the mixin implements is how that is done here, and it is the same shape as
 * {@link PositionedAccess}.
 *
 * <p>The remapper has to be reachable because a connection outlives the player attached to it: {@code
 * PlayerList#respawn} builds a new {@code ServerPlayer} and hands it the dead player's connection. Anything
 * remembered per player therefore has to be clearable when the player changes.
 */
public interface EntityRemapAccess
{
    /** This connection's remapper, or null if it is not a game connection and has none. */
    EntityPositionRemapper item_storage$entityRemapper();

    /**
     * Forgets every remembered entity.
     *
     * <p>Called when the player object is replaced. The client's entity list is emptied by the respawn packet
     * and rebuilt from scratch, so every remembered base describes entities this client no longer has - and
     * entity ids are reused, so a stale entry is worse than a missing one: it would be read as proof that an
     * offset still holds for an unrelated entity.
     */
    void item_storage$resetEntityRemapper();
}