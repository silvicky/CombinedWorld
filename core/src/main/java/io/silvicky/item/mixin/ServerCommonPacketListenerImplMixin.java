package io.silvicky.item.mixin;

import io.netty.channel.ChannelFutureListener;
import io.silvicky.item.backrooms.ChunkUnusedException;
import io.silvicky.item.backrooms.EntityPositionRemapper;
import io.silvicky.item.backrooms.EntityRemapAccess;
import io.silvicky.item.backrooms.EntityVisibilityManager;
import io.silvicky.item.backrooms.VecTransformer;
import io.silvicky.item.common.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.PositionAndRotation;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PositionPath;
import net.minecraft.world.entity.PositionStep;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.waypoints.TrackedWaypoint;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

@Mixin(ServerCommonPacketListenerImpl.class)
public class ServerCommonPacketListenerImplMixin implements EntityRemapAccess
{
    @Shadow
    @Final
    protected Connection connection;
    @Unique
    private static Set<Packet<?>> modified= Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    /**
     * Per-connection, because it is per-player: what each client has been told about each entity.
     *
     * <p>Entity packets mostly carry an <i>offset</i> rather than a position, and an offset cannot be
     * converted without knowing the position it is relative to - which is a position this client holds and
     * the packet does not state. So the base has to be remembered per player. It cannot be shared between
     * players, because two players have different maps and therefore different bases.
     *
     * <p>See {@link EntityPositionRemapper} for why an offset is usually correct as-is and only has to be
     * replaced when the entity crosses a page boundary.
     */
    @Unique
    private final EntityPositionRemapper item_storage$entityRemapper=new EntityPositionRemapper();
    @Override
    public EntityPositionRemapper item_storage$entityRemapper(){return item_storage$entityRemapper;}
    @Override
    public void item_storage$resetEntityRemapper(){item_storage$entityRemapper.reset();}
    /**
     * Set while a replacement entity packet is being sent, so the re-entry does not convert it again.
     *
     * <p>An instance field, not a static one, so it cannot be tripped by another player's send on another
     * thread. A static flag here would be a race between two players.
     */
    @Unique
    private boolean replacing;

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V",at=@At("HEAD"), cancellable = true)
    public void inject1(Packet<?> packet, @Nullable ChannelFutureListener channelFutureListener, CallbackInfo ci)
    {
        if(connection.getReceiving() == PacketFlow.CLIENTBOUND)return;
        //Our own replacement, on its way out. It was built from a packet that had already been converted.
        if(replacing)return;
        ServerCommonPacketListenerImpl instance=(ServerCommonPacketListenerImpl) (Object)this;
        if(!(instance instanceof ServerGamePacketListenerImpl gamePacketListener))return;
        VecTransformer vecTransformer=VecTransformer.getInstance(gamePacketListener.getPlayer());
        //Entity packets are handled before the `modified` cache and are exempt from it.
        //
        //That cache exists because the handlers below EDIT the packet in place, and a broadcast packet is one
        //object handed to every tracking player in turn - so without a guard the second player would be sent
        //the first player's already-converted copy. The cache is the wrong shape for entity packets, because
        //converting them is not a property of the packet but of the player it is going to. Two players have
        //two different maps, two different offsets, and two different remembered bases, so the same packet
        //object legitimately has two different correct answers. Caching by object identity would hand both
        //players the first one's answer. So entity packets skip the cache and are converted per player.
        if (isEntityPacket(packet))
        {
            try
            {
                Packet<?> translated=item_storage$entityRemapper
                                             .translate(packet,vecTransformer,
                                                        gamePacketListener.getPlayer().level());
                if(translated==null)
                {
                    //Its page is not in this player's map, so there is nowhere to put it. Dropping is right:
                    //the alternative is sending it at a position the player has never been shown.
                    ci.cancel();
                    return;
                }
                //Same object back means no change was needed - the common case, an offset packet inside one
                //page - and the original can simply go out.
                if(translated==packet)return;
                //Otherwise a replacement was built. It is a new object, so it re-enters this method; the flag
                //stops that from recursing.
                ci.cancel();
                replacing=true;
                try
                {
                    instance.send(translated,channelFutureListener);
                }
                finally
                {
                    replacing=false;
                }
            }
            catch (ChunkUnusedException e)
            {
                ci.cancel();
            }
            return;
        }
        //A sound cannot be corrected where it lies, and that is not a quirk of this method - it is how
        //vanilla built the packet.
        //
        //ClientboundSoundPacket has no setters and every field is final, so there is nowhere to write a new
        //position into and a replacement is the only option. What makes it necessary rather than merely
        //convenient is that the packet is shared: ServerLevel.playSeededSound builds ONE and hands it to
        //PlayerList.broadcast, which sends that same object to every player in earshot without copying it. Two
        //players have two different maps and therefore two different correct positions for one sound, and a
        //shared object can only hold one of them. So this is per player, for the same reason the entity
        //packets above are - and for the same reason it must sit BEFORE the `modified` cache, which would
        //otherwise hand the second player the first player's already-translated copy.
        if (packet instanceof ClientboundSoundPacket playSoundS2CPacket)
        {
            try
            {
                double x=playSoundS2CPacket.getX();
                double y=playSoundS2CPacket.getY();
                double z=playSoundS2CPacket.getZ();
                //Y is passed as 0 on purpose, the way the particle and look-at branches do it: the transform
                //moves whole chunks in x and z and has nothing to say about height. The height is then carried
                //over untouched, because a sound's vertical distance decides how much of its volume the client
                //attenuates away - it is real information, not something to push through the transform and hope.
                Vec3 pos=vecTransformer.s2cTransform(new Vec3(x,0,z));
                //Already where this player sees it. The common case on a level with no shuffle, and worth the
                //check because sounds are frequent and this way an unchanged one allocates nothing.
                if(pos.x==x && pos.z==z)return;
                //The public constructor takes doubles and does the /8 scaling itself, so this is also the only
                //place the 1/8-block precision of the packet is applied. Handing it getX() and friends cannot
                //drift: the transform is a translation by whole chunks, so it is exact in double, and the
                //truncation that follows is the same one vanilla would have done on the original coordinates.
                ClientboundSoundPacket translated=new ClientboundSoundPacket(
                        playSoundS2CPacket.getSound(),
                        playSoundS2CPacket.getSource(),
                        pos.x,
                        y,
                        pos.z,
                        playSoundS2CPacket.getVolume(),
                        playSoundS2CPacket.getPitch(),
                        playSoundS2CPacket.getSeed());
                ci.cancel();
                replacing=true;
                try
                {
                    instance.send(translated,channelFutureListener);
                }
                finally
                {
                    replacing=false;
                }
            }
            catch (ChunkUnusedException e)
            {
                //The sound's page is not in this player's map, so there is nowhere to put it. Dropping it is
                //right for the same reason it is for an entity: the alternative is a sound playing at a place
                //this player has never been shown.
                ci.cancel();
            }
            return;
        }
        //A waypoint is a record and TrackedWaypoint's own fields are private, so this one cannot be corrected
        //where it lies either. It is grouped here with the sound rather than below with the packets that are
        //edited in place, because that is the real distinction: everything above has to be rebuilt, everything
        //below can be written into.
        //
        //Unlike a sound this packet is not shared - WaypointTransmitter builds a fresh one for each receiver -
        //so putting it behind the `modified` cache would not actually misplace anything today. It is kept out
        //of that cache anyway, since a per-player answer is what this is and nothing should depend on the
        //current send sites staying per-player.
        if (packet instanceof ClientboundTrackedWaypointPacket waypointS2CPacket)
        {
            Optional<UUID> identifier=waypointS2CPacket.waypoint().id().left();
            Object position=waypointPosition(waypointS2CPacket.waypoint());
            //Only a position-carrying waypoint can announce something. The other two kinds are let through
            //untouched: an azimuth there is nothing hidden in - it is an angle and a UUID, nothing else - and
            //an empty body only untracks, which a stale marker deserves.
            if(position!=null&&identifier.isPresent())
            {
                //The waypoint only carries a UUID. Look the source up to learn its kind, because the rule for
                //a player differs from the one for a monster or a villager. A cross-dimension source is found
                //by getEntityInAnyDimension. One that is gone entirely is let through: its UNTRACK can still
                //clear a stale marker, and there is nothing alive left to hide.
                Entity source=gamePacketListener.getPlayer().level().getEntityInAnyDimension(identifier.get());
                if(source!=null && !EntityVisibilityManager.isVisible(gamePacketListener.getPlayer(),source.getType(),identifier.get()))
                {
                    //A marker for something the player cannot see is exactly the leak this setting exists to
                    //stop, and the entity body is already dropped the same way, so the packet goes with it.
                    ci.cancel();
                    return;
                }
            }
            TrackedWaypoint translated;
            try
            {
                translated=s2cTransformWaypoint(waypointS2CPacket.waypoint(),vecTransformer);
            }
            catch (ChunkUnusedException e)
            {
                //The waypoint's page is not in this player's map, so there is nowhere to put it. Dropping is
                //right for the same reason it is for an entity: the alternative is a marker sitting on a place
                //this player has never been shown.
                translated=null;
            }
            //Either there was no waypoint to translate, or it cannot be translated faithfully.
            if(translated==null){ci.cancel();return;}
            //Already where this player sees it - a waypoint that carries no position at all.
            if(translated==waypointS2CPacket.waypoint())return;
            //The record is rebuilt rather than edited. Its Operation is a private enum, but it never has to be
            //named: it is read straight off the packet and handed to the canonical constructor, which is public.
            ClientboundTrackedWaypointPacket replacement=
                    new ClientboundTrackedWaypointPacket(waypointS2CPacket.operation(),translated);
            ci.cancel();
            replacing=true;
            try
            {
                instance.send(replacement,channelFutureListener);
            }
            finally
            {
                replacing=false;
            }
            return;
        }
        if (modified.contains(packet)) return;
        modified.add(packet);
        try
        {
            if (packet instanceof ClientboundBundlePacket bundleS2CPacket)
            {
                for (Packet<?> packet1 : bundleS2CPacket.subPackets())
                {
                    for (Field field : packet1.getClass().getDeclaredFields())
                    {
                        if (BlockPos.class.isAssignableFrom(field.getType()))
                        {
                            field.setAccessible(true);
                            try
                            {
                                BlockPos pos = (BlockPos) field.get(packet1);
                                field.set(packet1, vecTransformer.s2cTransform(pos));
                            }
                            catch (IllegalAccessException e)
                            {
                                throw new RuntimeException(e);
                            }
                        }
                    }
                }
                return;
            }
            if (packet instanceof ClientboundForgetLevelChunkPacket)
            {
                //this is processed elsewhere
                return;
            }
            if(packet instanceof ClientboundExplodePacket clientboundExplodePacket)
            {
                clientboundExplodePacket.center=vecTransformer.s2cTransform(clientboundExplodePacket.center);
                return;
            }
            if (packet instanceof ClientboundLightUpdatePacket lightUpdateS2CPacket)
            {
                ChunkPos pos = vecTransformer.s2cTransform(new ChunkPos(lightUpdateS2CPacket.x, lightUpdateS2CPacket.z));
                lightUpdateS2CPacket.x = pos.x();
                lightUpdateS2CPacket.z = pos.z();
                return;
            }
            if (packet instanceof ClientboundChunksBiomesPacket(
                    List<ClientboundChunksBiomesPacket.ChunkBiomeData> chunkBiomeData
            ))
            {
                for (ClientboundChunksBiomesPacket.ChunkBiomeData serialized : chunkBiomeData)
                {
                    serialized.pos = vecTransformer.s2cTransform(serialized.pos);
                }
                return;
            }
            if (packet instanceof ClientboundDebugChunkValuePacket chunkValueDebugS2CPacket)
            {
                chunkValueDebugS2CPacket.chunkPos = vecTransformer.s2cTransform(chunkValueDebugS2CPacket.chunkPos);
                return;
            }
            if (packet instanceof ClientboundSetChunkCacheCenterPacket chunkRenderDistanceCenterS2CPacket)
            {
                ChunkPos pos = vecTransformer.s2cTransform(new ChunkPos(chunkRenderDistanceCenterS2CPacket.x, chunkRenderDistanceCenterS2CPacket.z));
                chunkRenderDistanceCenterS2CPacket.x = pos.x();
                chunkRenderDistanceCenterS2CPacket.z = pos.z();
                return;
            }
            if (packet instanceof ClientboundLevelParticlesPacket particleS2CPacket)
            {
                Vec3 pos = vecTransformer.s2cTransform(new Vec3(particleS2CPacket.x, 0, particleS2CPacket.z));
                particleS2CPacket.x = pos.x;
                particleS2CPacket.z = pos.z;
                return;
            }
            if (packet instanceof ClientboundSectionBlocksUpdatePacket clientboundSectionBlocksUpdatePacket)
            {
                clientboundSectionBlocksUpdatePacket.sectionPos = vecTransformer.s2cTransform(clientboundSectionBlocksUpdatePacket.sectionPos);
                return;
            }
            if (packet instanceof ClientboundPlayerPositionPacket clientboundPlayerPositionPacket)
            {
                if (!clientboundPlayerPositionPacket.relatives().contains(Relative.X))
                {
                    clientboundPlayerPositionPacket.change().position = vecTransformer.s2cTransform(clientboundPlayerPositionPacket.change().position);
                }
                return;
            }
            if (packet instanceof ClientboundPlayerLookAtPacket clientboundPlayerLookAtPacket)
            {
                Vec3 pos = vecTransformer.s2cTransform(new Vec3(clientboundPlayerLookAtPacket.x, 0, clientboundPlayerLookAtPacket.z));
                clientboundPlayerLookAtPacket.x = pos.x;
                clientboundPlayerLookAtPacket.z = pos.z;
                return;
            }
            if (packet instanceof ClientboundLevelChunkWithLightPacket clientboundLevelChunkWithLightPacket)
            {
                ChunkPos pos = vecTransformer.s2cTransform(new ChunkPos(clientboundLevelChunkWithLightPacket.x, clientboundLevelChunkWithLightPacket.z));
                clientboundLevelChunkWithLightPacket.x = pos.x();
                clientboundLevelChunkWithLightPacket.z = pos.z();
                return;
            }
            if (packet instanceof ClientboundDamageEventPacket clientboundDamageEventPacket)
            {
                if (clientboundDamageEventPacket.sourcePosition.isPresent())
                    clientboundDamageEventPacket.sourcePosition = Optional.of(vecTransformer.s2cTransform(clientboundDamageEventPacket.sourcePosition.get()));
                return;
            }
            for (Field field : packet.getClass().getDeclaredFields())
            {
                if (BlockPos.class.isAssignableFrom(field.getType()))
                {
                    field.setAccessible(true);
                    try
                    {
                        BlockPos pos = (BlockPos) field.get(packet);
                        field.set(packet, vecTransformer.s2cTransform(pos));
                    }
                    catch (IllegalAccessException e)
                    {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        catch (ChunkUnusedException e)
        {
            //If this is a block update for a pruned chunk, log it prominently — this is the ghost-block cause.
            if (packet instanceof ClientboundBlockUpdatePacket blockUpdate)
            {
                ChunkPos chunk=ChunkPos.containing(blockUpdate.getPos());
                Util.LOGGER.warn("[remap-drop] ClientboundBlockUpdatePacket for {} dropped — chunk {} not in player's view",
                        blockUpdate.getPos(), chunk);
            }
            else if (packet instanceof ClientboundSectionBlocksUpdatePacket sectionUpdate)
            {
                SectionPos sec=sectionUpdate.sectionPos;
                ChunkPos chunk=new ChunkPos(sec.x(), sec.z());
                Util.LOGGER.warn("[remap-drop] ClientboundSectionBlocksUpdatePacket for section {} dropped — chunk {} not in player's view",
                        sec, chunk);
            }
            else
            {
                Util.LOGGER.warn("[remap-drop] {} dropped — ChunkUnusedException: {}", packet.getClass().getSimpleName(), e.getMessage());
            }
            ci.cancel();
        }
    }

    /**
     * The position a waypoint is pointing at, or null when it does not point at one.
     *
     * <p>{@link TrackedWaypoint} is an abstract class with four private subclasses, and none of them has a
     * public reader for its contents - {@code icon()} and {@code id()} are all it exposes. So the coordinate
     * has to be read reflectively, and it is picked out <em>by type</em> rather than by field name. Field
     * names are not remapped, so matching on one would break the moment the mappings move; this is the same
     * trick the BlockPos fallback at the bottom of this method uses.
     *
     * <p>Only {@link Vec3i} and {@link ChunkPos} are looked for, and each of the four subclasses declares at
     * most one field. The two that hold neither are the AZIMUTH one, which holds a bare angle with no position
     * in it, and the EMPTY one, which holds nothing at all. Those are already correct as they stand, and
     * null here is what says so.
     */
    @Unique
    private static @Nullable Object waypointPosition(TrackedWaypoint waypoint)
    {
        for (Field field : waypoint.getClass().getDeclaredFields())
        {
            if (Modifier.isStatic(field.getModifiers()))continue;
            if (field.getType()!=Vec3i.class && field.getType()!=ChunkPos.class)continue;
            field.setAccessible(true);
            try
            {
                return field.get(waypoint);
            }
            catch (IllegalAccessException e)
            {
                throw new RuntimeException(e);
            }
        }
        return null;
    }

    /**
     * The same waypoint, pointing where this player sees its target.
     *
     * @return the original object when there was nothing to move, a new one when the position moved, or null
     *         when the waypoint cannot be translated faithfully and the packet has to be dropped.
     */
    @Unique
    private static @Nullable TrackedWaypoint s2cTransformWaypoint(TrackedWaypoint waypoint, VecTransformer vecTransformer) throws ChunkUnusedException
    {
        Object position=waypointPosition(waypoint);
        if(position==null)return waypoint;
        //The id is an Either, and every server-side waypoint is a UUID - WaypointTransmitter always uses the
        //source entity's. But the public factories below only take a UUID, so a name-identified waypoint could
        //not be rebuilt faithfully even if one ever turned up here. Returning null rather than inventing a
        //position is the honest answer, and the caller drops the packet.
        Optional<UUID> identifier=waypoint.id().left();
        if(identifier.isEmpty())return null;
        if (position instanceof Vec3i blockPos)
        {
            //A block position. s2cTransform hands back a BlockPos, and a BlockPos IS a Vec3i, so it goes
            //straight back in - and staying in integer space end to end is why this needs none of the
            //rounding care the sound packet's 1/8-block coordinates did.
            return TrackedWaypoint.setPosition(identifier.get(),waypoint.icon(),
                    vecTransformer.s2cTransform(new BlockPos(blockPos.getX(),blockPos.getY(),blockPos.getZ())));
        }
        if (position instanceof ChunkPos chunkPos)
        {
            return TrackedWaypoint.setChunk(identifier.get(),waypoint.icon(),vecTransformer.s2cTransform(chunkPos));
        }
        return waypoint;
    }

    /**
     * Whether this packet places an entity, and so has to go through {@link EntityPositionRemapper}.
     *
     * <p>Deliberately an explicit list rather than a broad {@code instanceof} on some interface. Several
     * entity packets carry no position at all - {@code ClientboundMoveEntityPacket.Rot} is rotation only,
     * {@code SetEntityMotionPacket} is velocity, {@code SetEntityLinkPacket} and {@code RemoveEntitiesPacket}
     * are ids - and sending those through the remapper would only risk touching something that has no
     * position to move. Being wrong in the other direction, a packet that does carry a position but is not
     * listed, would go out untranslated, so the list is worth keeping honest.
     */
    @Unique
    private static boolean isEntityPacket(Packet<?> packet)
    {
        return packet instanceof ClientboundMoveEntityPacket
                ||packet instanceof ClientboundEntityPositionSyncPacket
                ||packet instanceof ClientboundTeleportEntityPacket
                ||packet instanceof ClientboundAddEntityPacket
                ||packet instanceof ClientboundMoveVehiclePacket
                ||packet instanceof ClientboundMoveMinecartPacket
                ||packet instanceof ClientboundRemoveEntitiesPacket;
    }
}
