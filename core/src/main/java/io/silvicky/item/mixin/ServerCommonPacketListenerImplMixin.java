package io.silvicky.item.mixin;

import io.netty.channel.ChannelFutureListener;
import io.silvicky.item.backrooms.ChunkUnusedException;
import io.silvicky.item.backrooms.EntityPositionRemapper;
import io.silvicky.item.backrooms.EntityRemapAccess;
import io.silvicky.item.backrooms.EntityVisibilityManager;
import io.silvicky.item.backrooms.VecTransformer;
import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet;
import it.unimi.dsi.fastutil.shorts.ShortSet;
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
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
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
        //Block update / section update packets are shared across players but need per-player conversion.
        //Move them BEFORE the `modified` cache, otherwise the first player's conversion is cached and
        //all other players receive the wrong coordinates (the same bug entity packets would have without
        //their special handling). These packets have no setters, so they must be rebuilt per player.
        if (packet instanceof ClientboundBlockUpdatePacket blockUpdate)
        {
            try
            {
                BlockPos pos = blockUpdate.getPos();
                BlockPos converted = vecTransformer.s2cTransform(pos);
                if (!pos.equals(converted))
                {
                    ClientboundBlockUpdatePacket replacement = new ClientboundBlockUpdatePacket(converted, blockUpdate.getBlockState());
                    ci.cancel();
                    replacing = true;
                    try { instance.send(replacement, channelFutureListener); }
                    finally { replacing = false; }
                }
            }
            catch (ChunkUnusedException e)
            {
                //Target chunk not in this player's view - drop the packet silently.
                ci.cancel();
            }
            return;
        }
        if (packet instanceof ClientboundSectionBlocksUpdatePacket sectionUpdate)
        {
            //One object, broadcast to every tracking player - ChunkHolder builds it once per section and
            //hands the same reference to each recipient. So it may not be edited in place: the next player
            //would convert the converted position a second time, and editing it back afterwards would race
            //netty, which serialises on its own thread after send() has already returned. Rebuild it.
            try
            {
                Field posField = ClientboundSectionBlocksUpdatePacket.class.getDeclaredField("positions");
                posField.setAccessible(true);
                short[] positions = (short[]) posField.get(sectionUpdate);

                Field secField = ClientboundSectionBlocksUpdatePacket.class.getDeclaredField("sectionPos");
                secField.setAccessible(true);
                SectionPos original = (SectionPos) secField.get(sectionUpdate);
                SectionPos converted = vecTransformer.s2cTransform(original);
                if (original.equals(converted)) return;

                //The states live in the section the packet was built from, and the public constructor reads
                //them from there, so the same section has to be handed over with the new position.
                LevelChunk chunk = gamePacketListener.getPlayer().level()
                        .getChunk(original.chunk().x(), original.chunk().z());
                if (chunk == null)
                {
                    ci.cancel();
                    return;
                }
                LevelChunkSection section = chunk.getSection(
                        chunk.getSectionIndexFromSectionY(original.getY()));

                ShortSet changes = new ShortOpenHashSet(positions.length);
                for (short packed : positions) changes.add(packed);

                ClientboundSectionBlocksUpdatePacket replacement =
                        new ClientboundSectionBlocksUpdatePacket(converted, changes, section);
                ci.cancel();
                replacing = true;
                try { instance.send(replacement, channelFutureListener); }
                finally { replacing = false; }
            }
            catch (ChunkUnusedException e)
            {
                ci.cancel();
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
            return;
        }
        //Level event packets (fluid flow, explosions, sounds) carry a BlockPos and are broadcast.
        //Move before cache so each player gets their own conversion.
        if (packet instanceof ClientboundLevelEventPacket levelEvent)
        {
            try
            {
                BlockPos pos = levelEvent.getPos();
                BlockPos converted = vecTransformer.s2cTransform(pos);
                if (!pos.equals(converted))
                {
                    ClientboundLevelEventPacket replacement = new ClientboundLevelEventPacket(
                            levelEvent.getType(), converted, levelEvent.getData(), levelEvent.isGlobalEvent());
                    ci.cancel();
                    replacing = true;
                    try { instance.send(replacement, channelFutureListener); }
                    finally { replacing = false; }
                }
            }
            catch (ChunkUnusedException e)
            {
                ci.cancel();
            }
            return;
        }
        //Bundle packets can contain multiple block updates / level events.
        //Must be before cache so each sub-packet gets per-player conversion.
        if (packet instanceof ClientboundBundlePacket bundleS2CPacket)
        {
            //We need to convert each sub-packet. Since they're shared objects, we rebuild the bundle
            //with converted sub-packets.
            java.util.List<Packet<ClientGamePacketListener>> convertedSubPackets = new java.util.ArrayList<>();
            boolean anyChanged = false;
            for (Packet<?> packet1 : bundleS2CPacket.subPackets())
            {
                Packet<ClientGamePacketListener> toSend = (Packet<ClientGamePacketListener>) packet1;
                //If it's a packet type we know needs conversion, convert it
                if (packet1 instanceof ClientboundBlockUpdatePacket bu)
                {
                    try
                    {
                        BlockPos pos = bu.getPos();
                        BlockPos converted = vecTransformer.s2cTransform(pos);
                        if (!pos.equals(converted))
                        {
                            toSend = new ClientboundBlockUpdatePacket(converted, bu.getBlockState());
                            anyChanged = true;
                        }
                    }
                    catch (ChunkUnusedException ignored) { toSend = null; }
                }
                else if (packet1 instanceof ClientboundSectionBlocksUpdatePacket sbu)
                {
                    //Cannot rebuild this one from outside - its other fields are private and it has no
                    //public constructor taking a position. Drop it from the bundle rather than leave it
                    //converted: section updates are an optimisation, and the individual block updates that
                    //precede them carry the same change. See the note on the shared section packet above.
                    try
                    {
                        Field secField = ClientboundSectionBlocksUpdatePacket.class.getDeclaredField("sectionPos");
                        secField.setAccessible(true);
                        vecTransformer.s2cTransform((SectionPos) secField.get(sbu));
                        anyChanged = true;
                        toSend = null;
                    }
                    catch (ChunkUnusedException ignored) { toSend = null; }
                    catch (Exception ignored) {}
                }
                else if (packet1 instanceof ClientboundLevelEventPacket le)
                {
                    try
                    {
                        BlockPos pos = le.getPos();
                        BlockPos converted = vecTransformer.s2cTransform(pos);
                        if (!pos.equals(converted))
                        {
                            toSend = new ClientboundLevelEventPacket(
                                    le.getType(), converted, le.getData(), le.isGlobalEvent());
                            anyChanged = true;
                        }
                    }
                    catch (ChunkUnusedException ignored) { toSend = null; }
                }
                if (toSend != null) convertedSubPackets.add(toSend);
            }
            if (anyChanged)
            {
                ClientboundBundlePacket replacement = new ClientboundBundlePacket((Iterable) convertedSubPackets);
                ci.cancel();
                replacing = true;
                try { instance.send(replacement, channelFutureListener); }
                finally { replacing = false; }
            }
            return;
        }
        //======================================================================
        //Broadcast packets with position data — MUST be before `modified` cache.
        //These packets are the SAME OBJECT sent to multiple tracking players.
        //REBUILD each packet per player (new instance), do NOT mutate in place.
        //======================================================================
        if(packet instanceof ClientboundExplodePacket clientboundExplodePacket)
        {
            try
            {
                Vec3 center = clientboundExplodePacket.center();
                Vec3 converted = vecTransformer.s2cTransform(center);
                if (!center.equals(converted))
                {
                    ClientboundExplodePacket replacement = new ClientboundExplodePacket(
                            converted, clientboundExplodePacket.radius(), clientboundExplodePacket.blockCount(),
                            clientboundExplodePacket.playerKnockback(), clientboundExplodePacket.explosionParticle(),
                            clientboundExplodePacket.explosionSound(), clientboundExplodePacket.blockParticles(),
                            clientboundExplodePacket.playSound());
                    ci.cancel();
                    replacing = true;
                    try { instance.send(replacement, channelFutureListener); }
                    finally { replacing = false; }
                }
            }
            catch (ChunkUnusedException e){ci.cancel();}
            return;
        }
        if (packet instanceof ClientboundLightUpdatePacket lightUpdateS2CPacket)
        {
            try
            {
                ChunkPos chunkPos = new ChunkPos(lightUpdateS2CPacket.x(), lightUpdateS2CPacket.z());
                ChunkPos converted = vecTransformer.s2cTransform(chunkPos);
                if (!chunkPos.equals(converted))
                {
                    //Rebuild with converted chunk position, preserving lightData
                    ClientboundLightUpdatePacket replacement = new ClientboundLightUpdatePacket(
                            converted.x(), converted.z(), lightUpdateS2CPacket.lightData());
                    ci.cancel();
                    replacing = true;
                    try { instance.send(replacement, channelFutureListener); }
                    finally { replacing = false; }
                }
            }
            catch (ChunkUnusedException e){ci.cancel();}
            return;
        }
        if (packet instanceof ClientboundChunksBiomesPacket chunkBiomesPacket)
        {
            try
            {
                java.util.List<ClientboundChunksBiomesPacket.ChunkBiomeData> convertedData = new java.util.ArrayList<>();
                boolean anyChanged = false;
                for (ClientboundChunksBiomesPacket.ChunkBiomeData data : chunkBiomesPacket.chunkBiomeData())
                {
                    ChunkPos converted = vecTransformer.s2cTransform(data.pos());
                    if (!data.pos().equals(converted))
                    {
                        convertedData.add(new ClientboundChunksBiomesPacket.ChunkBiomeData(converted, data.buffer()));
                        anyChanged = true;
                    }
                    else
                    {
                        convertedData.add(data);
                    }
                }
                if (anyChanged)
                {
                    ClientboundChunksBiomesPacket replacement = new ClientboundChunksBiomesPacket(convertedData);
                    ci.cancel();
                    replacing = true;
                    try { instance.send(replacement, channelFutureListener); }
                    finally { replacing = false; }
                }
            }
            catch (ChunkUnusedException e){ci.cancel();}
            return;
        }
        if (packet instanceof ClientboundDebugChunkValuePacket debugPacket)
        {
            try
            {
                ChunkPos chunkPos = debugPacket.chunkPos();
                ChunkPos converted = vecTransformer.s2cTransform(chunkPos);
                if (!chunkPos.equals(converted))
                {
                    ClientboundDebugChunkValuePacket replacement = new ClientboundDebugChunkValuePacket(
                            converted, debugPacket.update());
                    ci.cancel();
                    replacing = true;
                    try { instance.send(replacement, channelFutureListener); }
                    finally { replacing = false; }
                }
            }
            catch (ChunkUnusedException e){ci.cancel();}
            return;
        }
        if (packet instanceof ClientboundLevelParticlesPacket particlePacket)
        {
            try
            {
                Vec3 pos = new Vec3(particlePacket.x(), 0, particlePacket.z());
                Vec3 converted = vecTransformer.s2cTransform(pos);
                if (!pos.equals(converted))
                {
                    ClientboundLevelParticlesPacket replacement = new ClientboundLevelParticlesPacket(
                            particlePacket.particle(), particlePacket.overrideLimiter(), particlePacket.alwaysShow(),
                            converted.x, particlePacket.y(), converted.z,
                            particlePacket.xDist(), particlePacket.yDist(), particlePacket.zDist(),
                            particlePacket.xMaxSpeed(), particlePacket.yMaxSpeed(), particlePacket.zMaxSpeed(),
                            particlePacket.count(), particlePacket.randomizationType());
                    ci.cancel();
                    replacing = true;
                    try { instance.send(replacement, channelFutureListener); }
                    finally { replacing = false; }
                }
            }
            catch (ChunkUnusedException e){ci.cancel();}
            return;
        }
        if (packet instanceof ClientboundPlayerLookAtPacket lookAtPacket)
        {
            try
            {
                //Position-based look-at (not entity-based). Fields are private, use reflection.
                Field atEntityField = ClientboundPlayerLookAtPacket.class.getDeclaredField("atEntity");
                atEntityField.setAccessible(true);
                boolean atEntity = atEntityField.getBoolean(lookAtPacket);
                if (!atEntity)
                {
                    Field xField = ClientboundPlayerLookAtPacket.class.getDeclaredField("x");
                    Field zField = ClientboundPlayerLookAtPacket.class.getDeclaredField("z");
                    xField.setAccessible(true);
                    zField.setAccessible(true);
                    double x = xField.getDouble(lookAtPacket);
                    double z = zField.getDouble(lookAtPacket);
                    Vec3 pos = new Vec3(x, 0, z);
                    Vec3 converted = vecTransformer.s2cTransform(pos);
                    if (!pos.equals(converted))
                    {
                        //Rebuild with converted position, preserving fromAnchor
                        ClientboundPlayerLookAtPacket replacement = new ClientboundPlayerLookAtPacket(
                                lookAtPacket.getFromAnchor(), converted.x, converted.y, converted.z);
                        ci.cancel();
                        replacing = true;
                        try { instance.send(replacement, channelFutureListener); }
                        finally { replacing = false; }
                    }
                }
            }
            catch (ChunkUnusedException e){ci.cancel();}
            catch (Exception e){throw new RuntimeException(e);}
            return;
        }
        if (packet instanceof ClientboundDamageEventPacket damagePacket)
        {
            try
            {
                if (damagePacket.sourcePosition().isPresent())
                {
                    Vec3 pos = damagePacket.sourcePosition().get();
                    Vec3 converted = vecTransformer.s2cTransform(pos);
                    if (!pos.equals(converted))
                    {
                        ClientboundDamageEventPacket replacement = new ClientboundDamageEventPacket(
                                damagePacket.entityId(), damagePacket.sourceType(), damagePacket.sourceCauseId(),
                                damagePacket.sourceDirectId(), Optional.of(converted));
                        ci.cancel();
                        replacing = true;
                        try { instance.send(replacement, channelFutureListener); }
                        finally { replacing = false; }
                    }
                }
            }
            catch (ChunkUnusedException e){ci.cancel();}
            return;
        }
        if (modified.contains(packet)) return;
        modified.add(packet);
        try
        {
            if (packet instanceof ClientboundForgetLevelChunkPacket)
            {
                //this is processed elsewhere
                return;
            }
            if (packet instanceof ClientboundSetChunkCacheCenterPacket chunkRenderDistanceCenterS2CPacket)
            {
                ChunkPos pos = vecTransformer.s2cTransform(new ChunkPos(chunkRenderDistanceCenterS2CPacket.getX(), chunkRenderDistanceCenterS2CPacket.getZ()));
                //Class has no setters, must reconstruct. But this packet is sent to specific player, not broadcast.
                //Since it's after the cache, it's fine to mutate in place via reflection.
                try
                {
                    Field xField = ClientboundSetChunkCacheCenterPacket.class.getDeclaredField("x");
                    Field zField = ClientboundSetChunkCacheCenterPacket.class.getDeclaredField("z");
                    xField.setAccessible(true);
                    zField.setAccessible(true);
                    xField.setInt(chunkRenderDistanceCenterS2CPacket, pos.x());
                    zField.setInt(chunkRenderDistanceCenterS2CPacket, pos.z());
                }
                catch (Exception e)
                {
                    throw new RuntimeException(e);
                }
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
            if (packet instanceof ClientboundLevelChunkWithLightPacket clientboundLevelChunkWithLightPacket)
            {
                ChunkPos pos = vecTransformer.s2cTransform(new ChunkPos(clientboundLevelChunkWithLightPacket.x, clientboundLevelChunkWithLightPacket.z));
                clientboundLevelChunkWithLightPacket.x = pos.x();
                clientboundLevelChunkWithLightPacket.z = pos.z();
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
            //The target is on a page this player is not currently shown, so there is nowhere to put it.
            //That is a normal condition and not an error - the visibility rule prunes the pages behind the
            //player by design. Drop it and say nothing.
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
