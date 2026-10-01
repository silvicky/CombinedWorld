package io.silvicky.item.backrooms;

import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.PositionAndRotation;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.protocol.game.VecDeltaCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.PositionPath;
import net.minecraft.world.entity.PositionStep;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.vehicle.minecart.NewMinecartBehavior;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Puts an entity's real server position into the player's own map, on the way out.
 *
 * <p><b>The rule, and it is the same one the rest of the mod follows: the client is shown the player's
 * map.</b> An entity at S position {@code p} is sent at {@code s2cTransform(p)}. Nothing here decides where
 * an entity <em>is</em> - collision, pushing and physics stay in S, exactly as vanilla runs them, and this
 * class only reports the outcome faithfully. That is deliberate: an entity shoved by the player really did
 * move in S, and the honest thing is to show where it went, not to invent a different move.
 *
 * <h2>Why deltas are the hard part</h2>
 *
 * Most entity movement is not sent as a position. It is sent as an <b>offset</b> from a position the client
 * already holds - {@link ClientboundMoveEntityPacket.Pos} and {@code .PosRot} carry a {@link VecDelta}, and
 * the client computes {@code clientBase + delta}. So the packet never states where the entity is, and we
 * cannot simply convert a position that is not in it.
 *
 * The saving grace is that <b>an offset is invariant under a translation</b>. Writing {@code C = S + o} for
 * the chunk offset {@code o}, and assuming the client's held position is really {@code S_base + o_base}:
 *
 * <pre>
 *   client computes :  C_base + (S_now - S_base)
 *   we want         :  S_now + o_now
 *   they agree when :  o_base == o_now
 * </pre>
 *
 * So <b>a delta packet is already correct whenever the entity has not crossed a page boundary</b>, and must be
 * forwarded completely untouched. This is the same reason a constant offset leaves vanilla's chunk-distance
 * tests alone; see {@link ChunkRemap}.
 *
 * When the entity <em>has</em> crossed, the correction is {@code o_now - o_base} - a whole number of pages,
 * so at least 16 blocks and often far more. {@link VecDelta} stores each axis as a {@code short} at 1/4096 of
 * a block, giving a range of about &plusmn;8 blocks. <b>The correction cannot be encoded at all.</b> That is
 * the whole reason a pushed entity appears in the wrong place: the client is handed an offset that is valid
 * inside a page and is silently applied across one.
 *
 * The fix is therefore not to adjust the offset but to <b>change the packet's kind</b>: when the offset has
 * changed, the position is sent absolutely. Rare, and the only correct option.
 *
 * <h2>What is tracked, and why it is not optional</h2>
 *
 * To know whether the offset changed, we need the base the client is holding. That is not in the packet, so
 * it is remembered here per entity, as <em>both</em> the server and the client position last sent. Storing
 * the server position too is what makes the test exact:
 *
 * <pre>
 *   offset actually applied to the base = C_base - S_base
 *   offset about to be applied           = C_now  - S_now
 *   unchanged iff these are equal
 * </pre>
 *
 * The invariant "the client holds {@code s2c(S_base)}" is maintained by induction: it starts true at the
 * spawn packet, an untouched delta preserves it, and an absolute packet re-establishes it. Entries are
 * dropped when the entity is removed, and overwritten when one is added, since entity ids are reused.
 */
public final class EntityPositionRemapper
{
    /** Both halves of one entity's last announced position. */
    private record Sent(Vec3 server, Vec3 client) {}

    private final Map<Integer, Sent> last = new ConcurrentHashMap<>();

    /** Entities dropped or respawned, so their remembered base cannot go stale. */
    public void forget(IntList entityIds)
    {
        for (int id : entityIds) last.remove(id);
    }

    /**
     * Forgets every entity.
     *
     * <p>For when the client is about to forget all of them anyway - a respawn, which empties the entity list
     * and rebuilds it. Worth doing even though ids are not immediately reused: a surviving entry would be read
     * as proof that an offset still holds, for an entity that has nothing to do with it.
     */
    public void reset()
    {
        last.clear();
    }

    public void forget(int entityId)
    {
        last.remove(entityId);
    }

    /**
     * The chunk offset in effect at a server position, as whole blocks. Null when that page is not in this
     * player's map, in which case nothing can be said about where the entity appears.
     */
    public @Nullable long[] offsetAt(Vec3 serverPos, VecTransformer transformer) throws ChunkUnusedException
    {
        ChunkPos from = ChunkPos.containing(BlockPos.containing(serverPos.x, serverPos.y, serverPos.z));
        ChunkPos to = transformer.s2cTransform(from);
        return new long[] {(long) to.x() * 16L - (long) from.x() * 16L,
                (long) to.z() * 16L - (long) from.z() * 16L};
    }

    /** Remembers where an entity now stands, on both sides. Call after anything that carries a position. */
    public void record(int entityId, Vec3 serverPos, Vec3 clientPos)
    {
        last.put(entityId, new Sent(serverPos, clientPos));
    }

    /**
     * Whether an offset-only packet can be forwarded untouched.
     *
     * <p>Two things have to hold, and both are necessary:
     *
     * <ol>
     *   <li><b>The offset has not changed</b> since the base the client holds. Comparing the two applied
     *       offsets, {@code C - S}, does that directly. With no remembered base the answer is no, because
     *       we cannot show the offset is still valid, and a wrong absolute packet is recoverable while a
     *       wrong offset is not.</li>
     *   <li><b>The base is the one the server encoded against.</b> This is the subtle one.
     *       {@code ServerEntity} keeps its own delta base and can advance it <em>without sending
     *       anything</em> - a riding entity resets its base every tick, for instance, since the vehicle packet
     *       carries the position instead. When that happens the client's held position and the server's base
     *       have quietly diverged, and the delta in the packet no longer means
     *       {@code S_now - S_base}. Forwarding it would apply an offset measured from somewhere the client
     *       has never been. So the delta is decoded against the base actually remembered here and its result
     *       compared with where the entity really is; if they disagree, the base has drifted.</li>
     * </ol>
     */
    private boolean deltaIsForwardable(int entityId, Vec3 serverNow, Vec3 clientNow, VecDelta delta)
    {
        Sent sent = last.get(entityId);
        if (sent == null) return false;

        double baseX = sent.client().x - sent.server().x;
        double baseZ = sent.client().z - sent.server().z;
        double nowX = clientNow.x - serverNow.x;
        double nowZ = clientNow.z - serverNow.z;
        //Offsets are whole pages, so the smallest real difference is 16 blocks. Half a block is far below
        //that and far above the 1/8192 the client's own quantisation can drift by, so this cannot mistake
        //quantisation noise for a page change nor a page change for noise.
        if (Math.abs(baseX - nowX) > 0.5 || Math.abs(baseZ - nowZ) > 0.5) return false;

        //Condition two: does the delta, read against the base we remember, arrive where the entity is?
        VecDeltaCodec codec = new VecDeltaCodec();
        codec.setBase(sent.server());
        Vec3 decoded = delta.decode(codec).endPosition();
        double tolerance = 1.0 / 512.0;
        return Math.abs(decoded.x - serverNow.x) < tolerance
                && Math.abs(decoded.y - serverNow.y) < tolerance
                && Math.abs(decoded.z - serverNow.z) < tolerance;
    }

    /**
     * Translates one entity packet.
     *
     * @return the packet to actually send: the original when it needs no change, a replacement when it does,
     *         or {@code null} to drop it because its page is not in this player's map.
     */
    public @Nullable Packet<?> translate(Packet<?> packet, VecTransformer transformer, ServerLevel level)
            throws ChunkUnusedException
    {
        // --- movement as an offset: the case that needs real work ---------------------------------------
        if (packet instanceof ClientboundMoveEntityPacket move)
        {
            //Rotation-only carries no position, so there is nothing to place.
            if (!move.hasPosition()) return packet;

            Entity entity = move.getEntity(level);
            if (entity == null) return packet;
            Vec3 serverNow = entity.trackingPosition();
            Vec3 clientNow = transformer.s2cTransform(serverNow);

            //The common case, and it costs one map lookup plus a decode: the offset has not moved and the
            //delta still means what it says, so it is already the right answer and must not be touched.
            if (deltaIsForwardable(entity.getId(), serverNow, clientNow, move.getPositionDelta()))
            {
                record(entity.getId(), serverNow, clientNow);
                return packet;
            }

            record(entity.getId(), serverNow, clientNow);
            return absoluteSync(move, entity.getId(), clientNow);
        }

        // --- movement as an absolute position --------------------------------------------------------------
        if (packet instanceof ClientboundEntityPositionSyncPacket sync)
        {
            PositionPath clientPath = transformPath(sync.position(), transformer);
            record(sync.id(), sync.position().endPosition(), clientPath.endPosition());
            return new ClientboundEntityPositionSyncPacket(sync.id(), clientPath,
                    sync.yRot(), sync.xRot(), sync.onGround());
        }

        if (packet instanceof ClientboundTeleportEntityPacket teleport)
        {
            PositionMoveRotation change = teleport.change();
            Set<Relative> relatives = teleport.relatives();
            Vec3 client = teleportPosition(change.position(), relatives, transformer);
            record(teleport.id(), change.position(), client);
            return new ClientboundTeleportEntityPacket(teleport.id(),
                    new PositionMoveRotation(client, change.deltaMovement(), change.yRot(), change.xRot()),
                    relatives, teleport.onGround());
        }

        // --- spawn -----------------------------------------------------------------------------------------
        if (packet instanceof ClientboundAddEntityPacket add)
        {
            Vec3 serverPos = new Vec3(add.getX(), add.getY(), add.getZ());
            Vec3 clientPos = transformer.s2cTransform(serverPos);
            //Entity ids are reused, so a spawn must overwrite rather than merge with a stale entry.
            record(add.getId(), serverPos, clientPos);
            add.x = clientPos.x;
            add.z = clientPos.z;
            return add;
        }

        // --- riding -----------------------------------------------------------------------------------------
        if (packet instanceof ClientboundMoveVehiclePacket vehicle)
        {
            //No entity id on this one, so the position is converted but not remembered as a base. That only
            //costs the vehicle an occasional absolute packet; it cannot put it in the wrong place.
            PositionAndRotation to = vehicle.movingTo();
            Vec3 clientPos = transformer.s2cTransform(to.position());
            vehicle.movingTo = new PositionAndRotation.Immutable(clientPos, to.yRot(), to.xRot());
            return vehicle;
        }

        // --- minecarts: a list of absolute steps ----------------------------------------------------------
        if (packet instanceof ClientboundMoveMinecartPacket minecart)
        {
            List<NewMinecartBehavior.MinecartStep> steps = new ArrayList<>(minecart.lerpSteps().size());
            Vec3 lastServer = null;
            Vec3 lastClient = null;
            for (NewMinecartBehavior.MinecartStep step : minecart.lerpSteps())
            {
                Vec3 serverPos = step.position();
                Vec3 clientPos = transformer.s2cTransform(serverPos);
                //Movement, not position: left in S on purpose. See the class comment.
                steps.add(new NewMinecartBehavior.MinecartStep(clientPos, step.movement(),
                        step.yRot(), step.xRot(), step.weight()));
                lastServer = serverPos;
                lastClient = clientPos;
            }
            if (lastServer != null) record(minecart.entityId(), lastServer, lastClient);
            return new ClientboundMoveMinecartPacket(minecart.entityId(), steps);
        }

        // --- disappearance ---------------------------------------------------------------------------------
        if (packet instanceof ClientboundRemoveEntitiesPacket remove)
        {
            forget(remove.entityIds());
            return packet;
        }

        //Deliberately not handled, because they carry no position:
        //  ClientboundMoveEntityPacket.Rot     - rotation only, hasPosition() is false
        //  ClientboundSetEntityMotionPacket    - velocity. Chunk offsets are whole pages, so a velocity is
        //                                         only meaningful within one page; carrying it across one is
        //                                         exactly the ill-defined case, and guessing would make
        //                                         client-side prediction disagree with the server.
        //  ClientboundRemoveEntitiesPacket     - ids only
        //  ClientboundSetEntityLinkPacket      - ids only
        //  ClientboundTakeItemEntityPacket     - ids and a count
        return packet;
    }

    /**
     * Builds an absolute position packet standing in for an offset packet.
     *
     * <p>{@link ClientboundEntityPositionSyncPacket} rather than {@link ClientboundTeleportEntityPacket},
     * because it is the packet vanilla itself falls back to when an offset will not do
     * ({@code ServerEntity#createMovePacket}), and because it carries no teleport bookkeeping the client
     * would otherwise have to undo.
     */
    private static ClientboundEntityPositionSyncPacket absoluteSync(ClientboundMoveEntityPacket move, int entityId,
                                                                     Vec3 clientPos)
    {
        return new ClientboundEntityPositionSyncPacket(entityId, PositionPath.of(clientPos),
                move.getYRot(), move.getXRot(), move.isOnGround());
    }

    /**
     * Converts a teleport's position, honouring the relative axes.
     *
     * <p>A relative axis is an offset from what the client already holds, so it must be left alone - it is
     * already in the player's map, being relative to a position in it. An absolute axis needs converting, and
     * all absolute axes share one chunk offset, so any absolute axis is enough to find it.
     *
     * <p>If every axis is relative there is no absolute position to work from, and the packet is returned
     * unchanged: a pure offset needs no conversion, for the reason given in the class comment.
     */
    private Vec3 teleportPosition(Vec3 serverPos, Set<Relative> relatives, VecTransformer transformer)
            throws ChunkUnusedException
    {
        boolean relX = relatives.contains(Relative.X);
        boolean relZ = relatives.contains(Relative.Z);
        if (relX && relZ) return serverPos;

        //Find an absolute axis to read the offset from. Y is not remapped - the shuffle is horizontal - so it
        //cannot serve, but Z can whenever X is relative.
        Vec3 reference = relX ? new Vec3(serverPos.x, serverPos.y, serverPos.z) : serverPos;
        long[] offset = offsetAt(reference, transformer);

        double x = relX ? serverPos.x : serverPos.x + offset[0];
        double z = relZ ? serverPos.z : serverPos.z + offset[1];
        return new Vec3(x, serverPos.y, z);
    }

    /**
     * Converts every point of a position path.
     *
     * <p>A path has several interpolation points and the client walks through all of them, so converting only
     * the end point would leave it interpolating through coordinates that mean nothing.
     */
    private PositionPath transformPath(PositionPath path, VecTransformer transformer) throws ChunkUnusedException
    {
        if (path instanceof PositionPath.Linear linear)
        {
            return PositionPath.of(transformer.s2cTransform(linear.endPosition()));
        }
        PositionPath.Stepped stepped = (PositionPath.Stepped) path;
        List<PositionStep> steps = new ArrayList<>(stepped.steps().size());
        for (PositionStep step : stepped.steps())
        {
            steps.add(new PositionStep(transformer.s2cTransform(step.position()), step.tickOffset()));
        }
        return PositionPath.stepped(steps);
    }
}