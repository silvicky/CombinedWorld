package io.silvicky.item.mixin;

import io.netty.channel.ChannelFutureListener;
import io.silvicky.item.backrooms.ChunkUnusedException;
import io.silvicky.item.backrooms.AbyssEntityCopies;
import io.silvicky.item.backrooms.AbyssChunkComposer;
import io.silvicky.item.backrooms.AbyssPatterns;
import io.silvicky.item.backrooms.AbyssPattern;
import io.silvicky.item.backrooms.AbyssWindow;
import io.silvicky.item.backrooms.EntityPositionRemapper;
import io.silvicky.item.backrooms.EntityPacketLog;
import io.silvicky.item.backrooms.EntityRemapAccess;
import io.silvicky.item.backrooms.EntityVisibilityManager;
import io.silvicky.item.backrooms.VecTransformer;
import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet;
import it.unimi.dsi.fastutil.shorts.ShortSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.PositionAndRotation;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PositionPath;
import net.minecraft.world.entity.PositionStep;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
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

import static io.silvicky.item.common.Util.LOGGER;

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
        ServerPlayer player=gamePacketListener.getPlayer();
        VecTransformer vecTransformer=VecTransformer.getInstance(player);

        //==============================================================================================
        //Abyss window demo. The client is told this dimension is three bands tall, while the player physically
        //stands in one band, so the two coordinate systems differ by a whole number of bands. The band height is the
        //pattern's (see AbyssGeometry), so nothing here assumes one. Everything here is per player (the window is per
        //player) and therefore has to run before the shared `modified` cache below.
        //==============================================================================================
        //A packet this mod built already holds client coordinates. Converting it again is not idempotent, and
        //doing so moved a block update into the wrong band - the reason a downward break looked like a dead
        //click while an upward one survived.
        if (AbyssWindow.isTranslated()) return;

        //Only for a crossing that leaves the composition unchanged, which is where nothing at all has to be sent:
        //the columns are the same columns, their content is identical, and the client already holds every one of them.
        //
        //On a crossing that *does* change the composition, only the nine columns around the player are kept: those
        //were re-sent with the new composition before this teleport, and a forget arriving after them would delete the
        //ground the player is standing on - which is what a falling player dropped through, and the bounce was the
        //server correcting it. The rest of the view keeps vanilla's forget, because it is re-sent over the following
        //second and the old composition is worse than nothing there.
        if (packet instanceof ClientboundForgetLevelChunkPacket forget && AbyssWindow.chunksUnmapped(player))
        {
            if (AbyssWindow.isViewKeepingSeam(player))
            {
                ci.cancel();
                return;
            }
            if (AbyssWindow.isSeamTeleport(player) && AbyssWindow.isNearSeamColumn(player, forget.pos()))
            {
                ci.cancel();
                return;
            }
        }

        //What a crossing actually puts on the wire, so the crossing's own line can say where the rest of a hitch is.
        //The mod's replacements are excluded above (they are the same packets, counted once); this counts the rest.
        if (AbyssWindow.isSeamTeleport(player))
        {
            if (packet instanceof ClientboundLevelChunkWithLightPacket)
                AbyssWindow.noteSeamTraffic(player, AbyssWindow.KIND_CHUNK, 1);
            else if (packet instanceof ClientboundRemoveEntitiesPacket removed)
                AbyssWindow.noteSeamTraffic(player, AbyssWindow.KIND_ENTITY_REMOVE, removed.entityIds().size());
            else if (packet instanceof ClientboundAddEntityPacket)
                AbyssWindow.noteSeamTraffic(player, AbyssWindow.KIND_ENTITY_ADD, 1);
            else if (packet instanceof ClientboundBundlePacket bundle)
                for (Packet<? super ClientGamePacketListener> sub : bundle.subPackets())
                {
                    if (sub instanceof ClientboundAddEntityPacket)
                        AbyssWindow.noteSeamTraffic(player, AbyssWindow.KIND_ENTITY_ADD, 1);
                    else if (sub instanceof ClientboundRemoveEntitiesPacket removed)
                        AbyssWindow.noteSeamTraffic(player, AbyssWindow.KIND_ENTITY_REMOVE, removed.entityIds().size());
                }
        }

        if (AbyssWindow.handles(player))
        {
            if (packet instanceof ClientboundLevelChunkWithLightPacket chunkPacket)
            {
                //Replaced, never forwarded. The client decodes exactly WINDOW_SECTIONS sections from the
                //buffer, and a band's own chunk only carries SECTIONS_PER_BAND of them.
                ClientboundLevelChunkWithLightPacket composed=
                        AbyssChunkComposer.compose(player, new ChunkPos(chunkPacket.x(), chunkPacket.z()));
                ci.cancel();
                if (composed==null)return;
                replacing=true;
                try{instance.send(composed,channelFutureListener);}
                finally{replacing=false;}
                return;
            }
        }
        //Login and respawn are decided by the dimension the packet itself announces, not by where the player
        //object currently is: a dimension change sends the respawn before anything else can be relied on, and
        //the client builds its whole level - section count, height, light range - from this one field.
        if (packet instanceof ClientboundLoginPacket login)
        {
            CommonPlayerSpawnInfo info=abyssSpawnInfo(login.commonPlayerSpawnInfo(),player);
            if (info!=null)
            {
                ci.cancel();
                replacing=true;
                try
                {
                    instance.send(new ClientboundLoginPacket(login.playerId(),login.hardcore(),
                            abyssLevels(login.levels()),login.maxPlayers(),login.chunkRadius(),
                            login.simulationDistance(),login.reducedDebugInfo(),login.showDeathScreen(),
                            login.doLimitedCrafting(),info,login.onlineMode(),login.enforcesSecureChat()),
                            channelFutureListener);
                }
                finally{replacing=false;}
                return;
            }
        }
        if (packet instanceof ClientboundRespawnPacket respawn)
        {
            //A seam is a teleport inside one dimension, so the client is not told about it. Vanilla's respawn
            //packet rebuilds the LocalPlayer and shows the level-loading screen even when the announced
            //dimension is unchanged, and there is nothing in it the client still needs: the key and type are
            //the constants it already has, and the position comes in its own packet.
            if (AbyssWindow.isSeamTeleport(player))
            {
                ci.cancel();
                return;
            }
            CommonPlayerSpawnInfo info=abyssSpawnInfo(respawn.commonPlayerSpawnInfo(),player);
            if (info!=null)
            {
                ci.cancel();
                replacing=true;
                try{instance.send(new ClientboundRespawnPacket(info,respawn.dataToKeep()),channelFutureListener);}
                finally{replacing=false;}
                return;
            }
        }
        //Entity packets are handled before the `modified` cache and are exempt from it.
        //
        //That cache exists because the handlers below EDIT the packet in place, and a broadcast packet is one
        //object handed to every tracking player in turn - so without a guard the second player would be sent
        //the first player's already-converted copy. The cache is the wrong shape for entity packets, because
        //converting them is not a property of the packet but of the player it is going to. Two players have
        //two different maps, two different offsets, and two different remembered bases, so the same packet
        //object legitimately has two different correct answers. Caching by object identity would hand both
        //players the first one's answer. So entity packets skip the cache and are converted per player.
        //Entity spawns do not arrive as add packets. Vanilla sends them inside a bundle:
        //
        //    // ServerEntity.addPairing
        //    List<Packet<? super ClientGamePacketListener>> packets = new ArrayList<>();
        //    this.sendPairingData(player, packets::add);
        //    player.connection.send(new ClientboundBundlePacket(packets));
        //
        //and the client runs `subPacket.handle(this)` for each member, so a bundle is only a container. The
        //outbound hook saw a `ClientboundBundlePacket`, which is not an entity packet, and forwarded it whole -
        //so no add packet was ever converted and every entity in the window was announced at its raw server Y.
        //For the band at offset 0 that is accidentally right, which is why only the other bands looked broken:
        //an entity in a band 64 above the window origin was drawn 64 blocks below where it stood, at the very
        //bottom of the window, i.e. not in its own band at all. It also means the horizontal shuffle has never
        //applied to an entity spawn.
        if (packet instanceof ClientboundBundlePacket bundle)
        {
            List<Packet<? super ClientGamePacketListener>> converted = new ArrayList<>();
            boolean changed = false;
            for (Packet<? super ClientGamePacketListener> sub : bundle.subPackets())
            {
                Packet<?> result = sub;
                if (isEntityPacket(sub))
                {
                    ServerPlayer bundlePlayer = gamePacketListener.getPlayer();
                    int bundleOffset = AbyssWindow.handles(bundlePlayer)
                            ? AbyssWindow.bandOffset(bundlePlayer) : 0;
                    //The band's own slice, which is what decides whether the entity's height is displayable at
                    //all. See EntityPositionRemapper.addY.
                    AbyssPattern.Band bundleBand = AbyssWindow.band(bundlePlayer);
                    int bundleSource = bundleBand == null ? 0 : bundleBand.sourceY();
                    Vec3 before = entityPosition(sub);
                    try
                    {
                        Packet<?> translated = item_storage$entityRemapper
                                .translate(sub, vecTransformer, bundlePlayer.level(), bundleOffset, bundleSource,
                                        AbyssWindow.geometry(bundlePlayer).bandHeight());
                        if (translated == null)
                        {
                            //Debug, not info: an entity parked outside its band's slice drops one packet per
                            //tick, for as long as it stays there. The transition is already reported by the
                            //reconcile in AbyssEntityCopies ("hid N own-band entities ..."), which fires once.
                            if (LOGGER.isDebugEnabled())
                                LOGGER.debug("[abyss] dropped bundled entity packet {} for {} at {} (band slice {}..{})",
                                        sub.type(), bundlePlayer.getGameProfile().name(), before,
                                        bundleSource, bundleSource + AbyssWindow.geometry(bundlePlayer).bandHeight());
                            //Tell the presence table, or nothing ever brings this entity back when it descends
                            //into the band: vanilla sends a spawn once and does not know it was discarded.
                            if (sub instanceof ClientboundAddEntityPacket droppedAdd)
                                AbyssEntityCopies.dropped(bundlePlayer, droppedAdd.getId());
                            result = null;
                        }
                        else
                        {
                            if (translated instanceof ClientboundAddEntityPacket added)
                                AbyssEntityCopies.delivered(bundlePlayer, added.getId());
                            if (translated instanceof ClientboundRemoveEntitiesPacket removed)
                                AbyssEntityCopies.removed(bundlePlayer,
                                        removed.entityIds().toIntArray());
                            EntityPacketLog.record(bundlePlayer, sub, bundleOffset, before,
                                    entityPosition(translated));
                            result = translated;
                        }
                    }
                    catch (ChunkUnusedException e)
                    {
                        result = null;
                    }
                }
                if (result != sub) changed = true;
                if (result != null) converted.add((Packet<? super ClientGamePacketListener>) result);
            }
            if (!changed) return;
            //What the bundle contained, and what it became. A spawn is a *sequence* - add, metadata, attributes,
            //equipment, passengers - and a missing member looks exactly like a missing entity from the outside, so
            //the sequence is worth one line rather than a log of only the things that happened. Guarded because
            //`describeBundle` walks every sub-packet, and this runs once per bundle.
            if (LOGGER.isDebugEnabled())
                LOGGER.debug("[abyss] bundle for {}: {} -> {}",
                        gamePacketListener.getPlayer().getGameProfile().name(),
                        describeBundle(bundle.subPackets()), describeBundle(converted));

            //**Adds go first.** The client resolves both ends of a hierarchy packet by id at the moment it is
            //handled:
            //
            //    handleSetEntityPassengersPacket:
            //        Entity vehicle = this.level.getEntity(packet.getVehicle());
            //        if (vehicle == null) { LOGGER.warn("Received passengers for unknown entity"); }
            //        else { vehicle.ejectPassengers(); for (int id : passengers) if (getEntity(id) != null) startRiding(...); }
            //
            //So a passenger packet that arrives before either add is dropped with that warning, and an add that
            //arrives after it silently loses the rider. Vanilla's own bundles happen to order adds before
            //hierarchy packets; a rebuilt one must guarantee it, and this is where the guarantee lives.
            List<Packet<? super ClientGamePacketListener>> ordered =
                    new ArrayList<>(converted.size());
            for (Packet<? super ClientGamePacketListener> sub : converted)
                if (sub instanceof ClientboundAddEntityPacket) ordered.add(sub);
            for (Packet<? super ClientGamePacketListener> sub : converted)
                if (!(sub instanceof ClientboundAddEntityPacket)) ordered.add(sub);

            ci.cancel();
            replacing = true;
            try
            {
                instance.send(new ClientboundBundlePacket(ordered), channelFutureListener);
            }
            finally
            {
                replacing = false;
            }
            return;
        }

        //Hierarchy packets carry no position, so nothing is converted - but they are the only evidence that an
        //assembled entity (a rider on a vehicle, a leashed mob) is assembled, and their *absence* is what a
        //"the panda is standing next to the boat" report actually looks like. Logged for the own band only, which
        //is the path vanilla sends and the mod cannot otherwise see; copies get their own line when built.
        if (packet instanceof ClientboundSetPassengersPacket passengers)
        {
            LOGGER.debug("[abyss] own-band passengers for {}: vehicle {} carries {}",
                    gamePacketListener.getPlayer().getGameProfile().name(),
                    passengers.getVehicle(), java.util.Arrays.toString(passengers.getPassengers()));
            return;
        }
        if (packet instanceof ClientboundSetEntityLinkPacket link)
        {
            LOGGER.debug("[abyss] own-band link for {}: {} leashed to {}",
                    gamePacketListener.getPlayer().getGameProfile().name(),
                    link.getSourceId(), link.getDestId());
            return;
        }

        if (isEntityPacket(packet))
        {
            try
            {
                ServerPlayer entityPlayer = gamePacketListener.getPlayer();
                int yOffset = AbyssWindow.handles(entityPlayer) ? AbyssWindow.bandOffset(entityPlayer) : 0;
                AbyssPattern.Band ownBand = AbyssWindow.band(entityPlayer);
                int sourceY = ownBand == null ? 0 : ownBand.sourceY();
                Vec3 before = entityPosition(packet);
                Packet<?> translated=item_storage$entityRemapper
                                             .translate(packet,vecTransformer,
                                                        entityPlayer.level(),
                                                        yOffset,
                                                        sourceY,
                                                        AbyssWindow.geometry(entityPlayer).bandHeight());
                if(translated==null)
                {
                    //Its page is not in this player's map, or its height is outside every band, so there is
                    //Dropping is right: the alternative is sending it at a height the player has never seen, or in
                    //a band that is not the entity's own - from which it could be reached and even walk into a
                    //seam. Debug rather than info, because an entity parked outside its band drops one packet per
                    //tick for as long as it stays there; the transition is reported once by the reconcile.
                    if (LOGGER.isDebugEnabled())
                        LOGGER.debug("[abyss] dropped entity packet {} for {} at {} (band slice {}..{})",
                                packet.type(), entityPlayer.getGameProfile().name(), before,
                                sourceY, sourceY + AbyssWindow.geometry(entityPlayer).bandHeight());
                    //A dropped spawn means the client has nothing, and nothing will send it again; a dropped move
                    //means the client is holding it at a stale height. Both are reconciled once per tick by
                    //AbyssEntityCopies, which needs to know which case this was.
                    if (packet instanceof ClientboundAddEntityPacket droppedAdd)
                        AbyssEntityCopies.dropped(entityPlayer, droppedAdd.getId());
                    ci.cancel();
                    return;
                }
                if (translated instanceof ClientboundAddEntityPacket added)
                    AbyssEntityCopies.delivered(entityPlayer, added.getId());
                if (translated instanceof ClientboundRemoveEntitiesPacket removed)
                    AbyssEntityCopies.removed(entityPlayer, removed.entityIds().toIntArray());
                EntityPacketLog.record(entityPlayer, packet, yOffset, before, entityPosition(translated));
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
                //Y is passed as 0 to the transform on purpose, the way the particle and look-at branches do
                //it: the shuffle moves whole chunks in x and z and has nothing to say about height. The
                //window, however, does have something to say about height - the whole dimension is shifted by
                //a band - so that part is added back separately.
                Vec3 pos=vecTransformer.s2cTransform(new Vec3(x,0,z));
                if(AbyssWindow.handles(player)) y=AbyssWindow.toClientY(player,y);
                //Already where this player sees it. The common case on a level with no shuffle, and worth the
                //check because sounds are frequent and this way an unchanged one allocates nothing.
                if(pos.x==x && pos.z==z && y==playSoundS2CPacket.getY())return;
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
                translated=s2cTransformWaypoint(waypointS2CPacket.waypoint(),vecTransformer,player);
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
                if (!AbyssWindow.handles(player))
                {
                    if (!pos.equals(converted))
                    {
                        ClientboundBlockUpdatePacket replacement = new ClientboundBlockUpdatePacket(converted, blockUpdate.getBlockState());
                        ci.cancel();
                        replacing = true;
                        try { instance.send(replacement, channelFutureListener); }
                        finally { replacing = false; }
                    }
                    return;
                }

                //A window can show this block more than once, so every image has to be updated. Sending only
                //the player's own band is what made a break in an adjacent band look like it did nothing: the
                //server broke the right block, and the client was told about a different copy of it.
                List<Double> images = AbyssWindow.imageYs(player, player.level().dimension(), pos.getY());
                ci.cancel();
                replacing = true;
                try
                {
                    for (double imageY : images)
                    {
                        BlockPos image = converted.offset(0, (int) Math.round(imageY) - converted.getY(), 0);
                        instance.send(new ClientboundBlockUpdatePacket(image, blockUpdate.getBlockState()),
                                       channelFutureListener);
                    }
                }
                finally { replacing = false; }
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

                //The states live in the section the packet was built from, and the public constructor reads
                //them from there, so the same section has to be handed over with every new position.
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

                if (AbyssWindow.handles(player))
                {
                    //One packet per copy of this level in the window, and none for a slot showing another
                    //level - that is what used to blank untouched terrain.
                    ci.cancel();
                    replacing = true;
                    try
                    {
                        for (SectionPos image : AbyssWindow.imageSections(player, original))
                            instance.send(new ClientboundSectionBlocksUpdatePacket(image, changes, section),
                                          channelFutureListener);
                    }
                    finally { replacing = false; }
                    return;
                }

                SectionPos converted = vecTransformer.s2cTransform(original);
                if (original.equals(converted)) return;

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
                if (AbyssWindow.handles(player)) converted = AbyssWindow.toClient(player, converted);
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
                        if (AbyssWindow.handles(player)) converted = AbyssWindow.toClient(player, converted);
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
                        SectionPos sectionPos = vecTransformer.s2cTransform((SectionPos) secField.get(sbu));
                        if (AbyssWindow.handles(player)) AbyssWindow.toClient(player, sectionPos);
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
                        if (AbyssWindow.handles(player)) converted = AbyssWindow.toClient(player, converted);
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
        //Broadcast packets with position data - MUST be before the modified-packet cache.
        //These packets are the SAME OBJECT sent to multiple tracking players.
        //REBUILD each packet per player (new instance), do NOT mutate in place.
        //======================================================================
        if(packet instanceof ClientboundExplodePacket clientboundExplodePacket)
        {
            try
            {
                Vec3 center = clientboundExplodePacket.center();
                Vec3 converted = vecTransformer.s2cTransform(center);
                if (AbyssWindow.handles(player)) converted = AbyssWindow.toClient(player, converted);
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
                //Standalone light updates come from the layer's own light engine, so their masks are
                //layer-section indices and have to be moved up into the band the layer occupies.
                ClientboundLightUpdatePacketData lightData = lightUpdateS2CPacket.lightData();
                if (AbyssWindow.handles(player))
                    lightData = AbyssChunkComposer.shift(lightData, AbyssWindow.slot(player) * AbyssWindow.geometry(player).sectionsPerBand());
                if (!chunkPos.equals(converted) || lightData != lightUpdateS2CPacket.lightData())
                {
                    //Rebuild with converted chunk position, preserving lightData
                    ClientboundLightUpdatePacket replacement = new ClientboundLightUpdatePacket(
                            converted.x(), converted.z(), lightData);
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
                double y = AbyssWindow.handles(player)
                        ? AbyssWindow.toClientY(player, particlePacket.y()) : particlePacket.y();
                if (!pos.equals(converted) || y != particlePacket.y())
                {
                    ClientboundLevelParticlesPacket replacement = new ClientboundLevelParticlesPacket(
                            particlePacket.particle(), particlePacket.overrideLimiter(), particlePacket.alwaysShow(),
                            converted.x, y, converted.z,
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
                    Field yField = ClientboundPlayerLookAtPacket.class.getDeclaredField("y");
                    Field zField = ClientboundPlayerLookAtPacket.class.getDeclaredField("z");
                    xField.setAccessible(true);
                    yField.setAccessible(true);
                    zField.setAccessible(true);
                    double x = xField.getDouble(lookAtPacket);
                    double z = zField.getDouble(lookAtPacket);
                    //The original carries a Y and the old code dropped it, rebuilding with 0. That was
                    //already wrong for a position target above or below the player, and in a window it would
                    //also be a band out, so it is read and carried now.
                    double y = yField.getDouble(lookAtPacket);
                    Vec3 pos = new Vec3(x, 0, z);
                    Vec3 converted = vecTransformer.s2cTransform(pos);
                    double clientY = AbyssWindow.handles(player) ? AbyssWindow.toClientY(player, y) : y;
                    if (!pos.equals(converted) || clientY != y)
                    {
                        //Rebuild with converted position, preserving fromAnchor
                        ClientboundPlayerLookAtPacket replacement = new ClientboundPlayerLookAtPacket(
                                lookAtPacket.getFromAnchor(), converted.x, clientY, converted.z);
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
                    if (AbyssWindow.handles(player)) converted = AbyssWindow.toClient(player, converted);
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
                //The layer's Y and the window's Y differ by a whole number of layers. A relative Y is already
                //a delta in the client's own frame, so only an absolute one is shifted.
                if (AbyssWindow.handles(player) && !clientboundPlayerPositionPacket.relatives().contains(Relative.Y))
                {
                    clientboundPlayerPositionPacket.change().position =
                            AbyssWindow.toClient(player, clientboundPlayerPositionPacket.change().position);
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
                        BlockPos converted = vecTransformer.s2cTransform(pos);
                        if (AbyssWindow.handles(player)) converted = AbyssWindow.toClient(player, converted);
                        field.set(packet, converted);
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
     * The same spawn info, with the window's dimension type and key, when the announced level is an abyss layer.
     *
     * <p>This is the one place the client is told the window exists. Both the key and the type become
     * constants, so every layer looks like the same dimension to the client: the name never changes, and a
     * seam teleport has nothing to announce. The types are datapack entries and therefore in the synced
     * registry; the key is only ever compared for identity, never looked up.
     *
     * <p>Decided from {@code info.dimension()}, so a login or respawn into any other level is left alone even
     * if the player object has already been moved into an abyss layer by the time the packet is sent.
     *
     * @return the rewritten info, or null when this is not an abyss level or the window type is missing. Null
     *         means the packet goes out unchanged: the client then builds a 64-tall level and the composed
     *         chunks will not fit, which is wrong but diagnosable, whereas dropping the login packet would not
     *         be.
     */
    @Unique
    private static @Nullable CommonPlayerSpawnInfo abyssSpawnInfo(CommonPlayerSpawnInfo info, ServerPlayer player)
    {
        AbyssPattern pattern = AbyssPatterns.forLevel(info.dimension());
        if (pattern == null) return null;
        try
        {
            var types=player.level().getServer().registryAccess().lookupOrThrow(Registries.DIMENSION_TYPE);
            //The type follows the pattern's band height, not a world-wide constant: the client builds its section
            //array and light range from this, so a 1024-tall band has to be announced with a 3072-tall window type.
            var window=types.getOrThrow(ResourceKey.create(Registries.DIMENSION_TYPE,pattern.geometry().windowType()));
            //The key comes from the pattern that owns this level, so two patterns can present different names.
            return new CommonPlayerSpawnInfo(window,AbyssWindow.windowKey(info.dimension()),info.seed(),info.gameType(),
                    info.previousGameType(),info.isDebug(),info.isFlat(),info.lastDeathLocation(),
                    info.portalCooldown(),info.seaLevel());
        }
        catch (Exception e)
        {
            LOGGER.warn("[abyss] window dimension type is missing for a {} band; the client will be told the layer height",
                    pattern.geometry().bandHeight(), e);
            return null;
        }
    }

    /**
     * The login's level list, with the window key added.
     *
     * <p>The client keeps this list and uses it to answer "do I have this level": the announced dimension has
     * to be in it or the client treats the spawn info as unknown. The layer keys stay, because the server does
     * still move between them and other packets may name them.
     */
    @Unique
    private static Set<ResourceKey<Level>> abyssLevels(Set<ResourceKey<Level>> levels)
    {
        Set<ResourceKey<Level>> result=new java.util.HashSet<>(levels);
        //One key per pattern, since each names its own window. A pattern whose key is already present contributes
        //nothing, which is why this adds rather than returning early on a single match.
        for (AbyssPatterns.PatternKey view : AbyssPatterns.windowKeys())
            result.add(ResourceKey.create(Registries.DIMENSION, view.dimension()));
        return result;
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
    private static @Nullable TrackedWaypoint s2cTransformWaypoint(TrackedWaypoint waypoint, VecTransformer vecTransformer,
                                                                  ServerPlayer player) throws ChunkUnusedException
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
            //rounding care the sound packet's 1/8-block coordinates did. The window adds its band here.
            BlockPos converted=vecTransformer.s2cTransform(new BlockPos(blockPos.getX(),blockPos.getY(),blockPos.getZ()));
            if (AbyssWindow.handles(player)) converted=AbyssWindow.toClient(player,converted);
            return TrackedWaypoint.setPosition(identifier.get(),waypoint.icon(),converted);
        }
        if (position instanceof ChunkPos chunkPos)
        {
            return TrackedWaypoint.setChunk(identifier.get(),waypoint.icon(),vecTransformer.s2cTransform(chunkPos));
        }
        return waypoint;
    }

    /**
     * The members of a bundle as one line: packet kinds, with entity ids where a packet names one.
     *
     * <p>Exists because the interesting failures here are about *absent* members - an add without its metadata, a
     * vehicle without its passenger packet - and an absence cannot be seen in a log of things that happened.
     */
    @Unique
    private static String describeBundle(Iterable<? extends Packet<?>> packets)
    {
        StringBuilder out = new StringBuilder("[");
        for (Packet<?> sub : packets)
        {
            if (out.length() > 1) out.append(", ");
            String name = sub.getClass().getSimpleName();
            if (sub instanceof ClientboundAddEntityPacket add) name += ":" + add.getId();
            else if (sub instanceof ClientboundSetEntityDataPacket data) name += ":" + data.id();
            else if (sub instanceof ClientboundUpdateAttributesPacket attributes) name += ":" + attributes.getEntityId();
            out.append(name);
        }
        return out.append(']').toString();
    }

    /**
     * The position an entity packet carries, or null when it carries none.
     *
     * <p>Only for logging; the conversion itself happens in {@link EntityPositionRemapper}. A delta packet is
     * relative, so the absolute position it implies is not in it and there is nothing to print.
     */
    @Unique
    private static Vec3 entityPosition(Packet<?> packet)
    {
        if (packet instanceof ClientboundAddEntityPacket add)
            return new Vec3(add.getX(), add.getY(), add.getZ());
        if (packet instanceof ClientboundEntityPositionSyncPacket sync)
            return sync.position().endPosition();
        if (packet instanceof ClientboundTeleportEntityPacket teleport)
            return teleport.change().position();
        return null;
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
