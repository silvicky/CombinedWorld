package io.silvicky.item.backrooms;

import net.minecraft.server.level.ServerPlayer;

public interface PositionedAccess
{
    void item_storage$setPlayer(ServerPlayer player);
    ServerPlayer item_storage$getPlayer();
    /**
     * The remap for this view, always derived from the player's <em>current</em> transformer.
     *
     * <p>Not a stored snapshot. A view outlives many changes to the mapping it was built from - most
     * importantly a reshuffle, which replaces the whole map without changing the player's real chunk, so
     * vanilla never rebuilds the view to trigger a refresh. A snapshot taken at that moment goes stale,
     * and a stale view names server chunks the live map has forgotten, which is what makes the server
     * discard chunk packets and drop the client into the void. Implementations cache and re-derive on
     * {@link VecTransformer#mapVersion()} so this stays cheap.
     */
    ChunkRemap item_storage$getRemap();
}
