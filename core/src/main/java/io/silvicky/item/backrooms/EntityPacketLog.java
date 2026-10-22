package io.silvicky.item.backrooms;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

import static io.silvicky.item.common.Util.LOGGER;

/**
 * One line per entity packet that the window actually moved, for entities in the player's <b>own</b> band.
 *
 * <p>The copy system had logging from the start and the own band did not, which is backwards: vanilla sends the
 * own band's entities, so a fault there is a fault in something the mod does not print. An "entity in the wrong
 * band" report was consequently unreadable - there was no evidence either way about whether the packet existed,
 * what position it carried, or which offset was applied.
 *
 * <p>Adds and removals are rare and always printed. Movement is printed when the applied offset differs from the
 * one last used for that entity - the crossing case, which is the one that interests us - and otherwise once per
 * entity per second, so a walking mob cannot flood the log.
 */
public final class EntityPacketLog
{
    /** Last time each entity's movement was printed, so repeats are throttled. */
    private static final Map<ServerPlayer, Map<Integer, Long>> lastPrint = new HashMap<>();

    /** The offset each entity was last moved with, so a change can be told from a repeat. */
    private static final Map<ServerPlayer, Map<Integer, Integer>> lastOffset = new HashMap<>();

    private EntityPacketLog() {}

    /**
     * Records one entity packet after conversion.
     *
     * @param serverPos the entity's position in its own level, or null when the packet does not carry one
     * @param clientPos the same position as the client is being told it, or null
     */
    public static void record(ServerPlayer player, Packet<?> packet, int offset,
                              Vec3 serverPos, Vec3 clientPos)
    {
        if (player.connection == null || !AbyssWindow.handles(player)) return;

        if (packet instanceof ClientboundAddEntityPacket add)
        {
            LOGGER.info("[abyss] own-band add id {} {} for {}: server {} -> client {} (offset {}, level {})",
                    add.getId(), add.getType(), name(player), fmt(serverPos), fmt(clientPos), offset,
                    player.level().dimension().identifier());
            return;
        }

        if (packet instanceof ClientboundRemoveEntitiesPacket remove)
        {
            LOGGER.info("[abyss] own-band remove {} for {}", remove.entityIds(), name(player));
            return;
        }

        if (packet instanceof ClientboundSetEntityDataPacket data)
        {
            //Metadata is what makes some entities visible at all - an item frame with no item is blank - so a
            //missing metadata packet and a missing entity can look the same from the outside.
            if (LOGGER.isDebugEnabled())
                LOGGER.debug("[abyss] own-band metadata id {} for {}", data.id(), name(player));
            return;
        }

        Integer id = id(packet);
        if (id == null) return;

        boolean absolute = packet instanceof ClientboundEntityPositionSyncPacket
                           || packet instanceof ClientboundTeleportEntityPacket;

        Integer previous = lastOffset.computeIfAbsent(player, p -> new HashMap<>()).put(id, offset);
        if (absolute || previous == null || previous != offset)
        {
            LOGGER.info("[abyss] own-band {} id {} for {}: server {} -> client {} (offset {} -> {})",
                    absolute ? "position" : "move", id, name(player), fmt(serverPos), fmt(clientPos),
                    previous == null ? "?" : previous, offset);
            return;
        }

        Map<Integer, Long> prints = lastPrint.computeIfAbsent(player, p -> new HashMap<>());
        long now = System.currentTimeMillis();
        Long was = prints.get(id);
        if (was != null && now - was < 1000L) return;
        prints.put(id, now);
        LOGGER.info("[abyss] own-band move id {} for {}: server {} -> client {} (offset {})",
                id, name(player), fmt(serverPos), fmt(clientPos), offset);
    }

    /** Forgets a player's history. */
    public static void forget(ServerPlayer player)
    {
        lastPrint.remove(player);
        lastOffset.remove(player);
    }

    /** The entity id a packet names, or null for one that carries none. */
    private static Integer id(Packet<?> packet)
    {
        if (packet instanceof ClientboundMoveEntityPacket move) return move.entityId;
        if (packet instanceof ClientboundEntityPositionSyncPacket sync) return sync.id();
        if (packet instanceof ClientboundTeleportEntityPacket teleport) return teleport.id();
        return null;
    }

    private static String name(ServerPlayer player)
    {
        return player.getGameProfile().name();
    }

    private static String fmt(Vec3 pos)
    {
        if (pos == null) return "?";
        return "(" + Math.round(pos.x * 100.0) / 100.0
               + ", " + Math.round(pos.y * 100.0) / 100.0
               + ", " + Math.round(pos.z * 100.0) / 100.0 + ")";
    }
}
