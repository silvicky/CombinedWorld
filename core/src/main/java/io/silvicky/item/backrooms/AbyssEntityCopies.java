package io.silvicky.item.backrooms;

import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PositionPath;
import net.minecraft.world.entity.UpdateInterval;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static io.silvicky.item.common.Util.LOGGER;

/**
 * The client-side copies of entities that live in another band.
 *
 * <p>A client entity occupies exactly one position, and the window shows up to three bands. So an entity
 * visible in three bands must be three client entities. This class fabricates them: a copy is a tuple of
 * (id, uuid, source entity, position) with no {@code Entity} object of its own, and every packet describing it
 * is derived from the source at the moment it is sent.
 *
 * <p><b>Why this cannot defer to vanilla tracking.</b> {@code ChunkMap.TrackedEntity} offers an entity only to
 * players in its own level; the three visible bands are three levels. So the whole tracking relationship -
 * add, move, metadata, remove - is synthesised here. That is the difference from the horizontal shuffle, where
 * everything stays in one level and vanilla's tracking does the work.
 *
 * <p><b>What is not reimplemented.</b> Physics, AI, collision and damage stay in the real levels, run by
 * vanilla, normally. A copy reports what happened; it never decides anything.
 *
 * <h2>Why deltas are forwarded and absolutes are converted</h2>
 *
 * A movement packet carries an offset from a position the client already holds, and an offset is invariant
 * under a translation: the band offset cancels. So a delta packet needs no edit at all, and only the base the
 * client holds has to be remembered - which is what {@link #lastServer} are for. An
 * absolute packet (spawn, position sync) is in client space and is built that way.
 *
 * <h2>Why metadata is diffed here and not by {@code ServerEntity}</h2>
 *
 * {@code SynchedEntityData.packDirty()} clears the dirty flag before returning:
 *
 * <pre>
 *   if (!this.isDirty) return null;
 *   this.isDirty = false;
 *   ... for each item: if (item.isDirty()) { collect; item.setClean(); }
 * </pre>
 *
 * Dirty state is single-consumer. An entity in a foreign band is also tracked by the players in its own level,
 * whose own {@code ServerEntity} packs those values first; a second one belonging to the copy would then send
 * nothing, or steal the update from the real tracker. So this class snapshots
 * {@code getEntityData().getNonDefaultValues()} and compares, which reads without clearing.
 */
public final class AbyssEntityCopies
{
    /** One copy: a view of a source entity, at one position of the window. */
    private static final class Copy
    {
        final int id;
        final UUID uuid;
        final int position;
        final Entity source;

        /** Where the client believes it is, in server space - the base its next delta is applied to. */
        Vec3 lastServer;
        float lastYRot;
        float lastXRot;
        /** The Y offset this copy was built with. A change invalidates every delta against it. */
        int offset;
        /** The metadata last sent, compared rather than packed so the real tracker keeps its dirty flag. */
        List<SynchedEntityData.DataValue<?>> lastData;
        /** The velocity last sent, so a motion packet is not repeated every tick. */
        Vec3 lastMotion;
        /** Set once the death event has been sent, so the animation is not restarted every tick. */
        boolean deathSent;
        /** The passenger ids last sent, so a change can be told from a repeat. */
        List<Integer> lastPassengers;
        /** The leash holder's copy id last sent, or null when the copy is not leashed. */
        Integer lastLeash;

        Copy(int id, UUID uuid, int position, Entity source)
        {
            this.id = id;
            this.uuid = uuid;
            this.position = position;
            this.source = source;
        }
    }

    /** Keyed by (position, source id), so a copy's identity is stable while its band stays in the window. */
    private record Key(int position, int sourceId) {}

    private static final Map<ServerPlayer, Map<Key, Copy>> tables = new ConcurrentHashMap<>();

    /** The int ids currently handed out, so a duplicate can be caught rather than silently overwriting. */
    private static final Map<ServerPlayer, Map<Integer, Copy>> byId = new ConcurrentHashMap<>();

    private AbyssEntityCopies() {}

    // ---------------------------------------------------------------------------------------------------
    // Tick
    // ---------------------------------------------------------------------------------------------------

    /**
     * One tick of copy bookkeeping for one player: add what became visible, remove what stopped being visible,
     * update the rest.
     *
     * <p>Runs on the server thread, from {@code AbyssWindow.tickPlayer}, after {@code travel}. So a crossing's
     * new window is what the copies are built from, in the same tick the player is moved.
     */
    public static void tick(ServerPlayer player)
    {
        if (player.connection == null)
        {
            drop(player);
            return;
        }
        AbyssWindow.Window window = AbyssWindow.window(player);
        MinecraftServer server = player.level().getServer();
        if (window == null || server == null)
        {
            drop(player);
            return;
        }

        Map<Key, Copy> table = tables.computeIfAbsent(player, p -> new LinkedHashMap<>());
        Map<Integer, Copy> ids = byId.computeIfAbsent(player, p -> new HashMap<>());

        int ownSlot = AbyssWindow.slot(player);

        //The own band's entities are vanilla's business, but not knowing which of them the server thinks are
        //in view makes a "the entity vanished from my band" report impossible to read. This is a presence set,
        //not ownership: nothing here sends anything.
        observeOwnBand(player, window, ownSlot, server);

        Map<Key, Copy> wanted = new HashMap<>();

        //The window's shape for this tick, so a spawn or removal in the log can be read without guessing which
        //band was where. Printed only when it changes: the composition is constant for long stretches.
        String shape = describe(window, ownSlot);
        if (!shape.equals(lastShape.get(player)))
        {
            lastShape.put(player, shape);
            LOGGER.info("[abyss] window for {} is {} (position {}, start {}, own slot {})",
                    name(player), shape, window.position(), window.windowStart(), ownSlot);
        }

        for (int slot = 0; slot < AbyssWindow.WINDOW_LAYERS; slot++)
        {
            if (slot == ownSlot) continue;
            int position = window.windowStart() + slot;
            AbyssPattern.Band band = window.pattern().band(position);
            if (band == null) continue;
            ServerLevel level = server.getLevel(band.level());
            if (level == null) continue;

            for (Entity entity : visible(player, level, band))
            {
                if (!CopyIdentity.usable(entity.getId())) continue;
                Key key = new Key(position, entity.getId());
                Copy copy = table.get(key);
                if (copy != null && copy.source == entity)
                {
                    wanted.put(key, copy);
                    continue;
                }
                //A different entity at the same key means ids were reused: the old copy must go first, or the
                //client keeps an entity whose metadata and motion belong to something else.
                if (copy != null) remove(player, table, ids, key, copy, "entity id reused by " + describe(entity));
                Copy added = add(player, table, ids, key, position, slot, band, entity);
                if (added != null) wanted.put(key, added);
            }
        }

        //Everything wanted and gone. Iterating a copy of the key set, because remove mutates the table.
        for (Key key : new ArrayList<>(table.keySet()))
        {
            if (wanted.containsKey(key)) continue;
            Copy copy = table.get(key);
            if (copy != null) remove(player, table, ids, key, copy, copy.source.isRemoved() ? "entity removed" : "left the view");
        }

        for (Map.Entry<Key, Copy> entry : wanted.entrySet())
        {
            Copy copy = entry.getValue();
            int slot = copy.position - window.windowStart();
            AbyssPattern.Band band = window.pattern().band(copy.position);
            if (band == null) continue;
            update(player, copy, slot, band);
            //Hierarchy changes are rare and cheap to test, so they are checked every tick rather than plumbed
            //through the movement path.
            updateHierarchy(player, copy, wanted);
        }
    }

    /** What the own band currently offers this player, so appearance and disappearance can be logged. */
    private static final Map<ServerPlayer, Set<Integer>> ownSeen = new ConcurrentHashMap<>();

    /**
     * Which band the {@link #ownSeen} set describes.
     *
     * <p>Without this the set outlives the band it was taken in, and the first observation after a crossing
     * compares the new band's entities against the old band's - which printed a burst of `left own band ...
     * (gone)` lines for entities that had merely moved to a different band. The `(gone)` came from looking the id
     * up in the new level, where of course it is not.
     */
    private static final Map<ServerPlayer, String> ownSeenBand = new ConcurrentHashMap<>();

    private static void observeOwnBand(ServerPlayer player, AbyssWindow.Window window, int ownSlot,
                                       MinecraftServer server)
    {
        AbyssPattern.Band band = window.pattern().band(window.windowStart() + ownSlot);
        ServerLevel level = band == null ? null : server.getLevel(band.level());
        if (band == null || level == null)
        {
            ownSeen.remove(player);
            ownSeenBand.remove(player);
            return;
        }

        String key = band.level().identifier() + "@" + band.sourceY();
        Set<Integer> before = ownSeenBand.get(player) == null ? null : ownSeen.get(player);
        if (!key.equals(ownSeenBand.put(player, key)))
        {
            //A different band: there is nothing to compare against, so this observation only seeds the set.
            before = null;
        }

        Set<Integer> now = new HashSet<>();
        for (Entity entity : visible(player, level, band, true))
        {
            if (entity == player) continue;
            now.add(entity.getId());
        }

        ownSeen.put(player, now);
        if (before == null) return;
        for (int id : now)
        {
            if (before.contains(id)) continue;
            Entity entity = level.getEntity(id);
            if (entity != null)
                LOGGER.info("[abyss] {} entered own band {} of {} (y={}, offset {})",
                        describe(entity), band.level().identifier().getPath(), name(player),
                        Math.round(player.getY()), AbyssWindow.bandOffset(player));
        }
        for (int id : before)
        {
            if (now.contains(id)) continue;
            Entity entity = level.getEntity(id);
            LOGGER.info("[abyss] left own band {} of {}: {}", band.level().identifier().getPath(), name(player),
                    entity == null ? ("entity " + id + " (gone)") : describe(entity));
        }
    }

    /** The window's composition as one line: which band is in which slot, and which slot is the player's. */
    private static String describe(AbyssWindow.Window window, int ownSlot)
    {
        StringBuilder out = new StringBuilder();
        for (int slot = 0; slot < AbyssWindow.WINDOW_LAYERS; slot++)
        {
            if (slot > 0) out.append(" | ");
            AbyssPattern.Band band = window.pattern().band(window.windowStart() + slot);
            out.append(slot).append(':');
            if (band == null) out.append("(end)");
            else out.append(band.level().identifier().getPath()).append('@').append(band.sourceY());
            if (slot == ownSlot) out.append("*");
        }
        return out.toString();
    }

    /** One entity, in a form that identifies it in a log without a whole toString. */
    private static String describe(Entity entity)
    {
        return entity.getType().toString() + "#" + entity.getId()
               + " at " + Math.round(entity.getX()) + "," + Math.round(entity.getY()) + "," + Math.round(entity.getZ());
    }

    private static String name(ServerPlayer player)
    {
        return player.getGameProfile().name();
    }

    /** A position as three rounded components, which is all a log needs. */
    private static String fmt(Vec3 pos)
    {
        return "(" + Math.round(pos.x * 100.0) / 100.0
               + ", " + Math.round(pos.y * 100.0) / 100.0
               + ", " + Math.round(pos.z * 100.0) / 100.0 + ")";
    }

    /** The last window shape printed per player, so the line above appears on changes only. */
    private static final Map<ServerPlayer, String> lastShape = new ConcurrentHashMap<>();

    /**
     * Which entities a band contributes to this player's window.
     *
     * <p>Only entities inside the band's own Y slice count: a band is a slice of a level, and a level may be
     * taller than one band or hold more than one slice, so an entity standing elsewhere in the same level is
     * not visible through this band.
     */
    private static List<Entity> visible(ServerPlayer player, ServerLevel level, AbyssPattern.Band band)
    {
        return visible(player, level, band, true);
    }

    /**
     * @param includePlayers whether other players count. The copy loop wants them (a player seen from the next
     *                       band is the whole point of stacking dimensions); the only caller that excludes them
     *                       is nothing any more, so both paths pass true and the flag is kept for the own-band
     *                       observers, which log what vanilla is already sending.
     */
    private static List<Entity> visible(ServerPlayer player, ServerLevel level, AbyssPattern.Band band,
                                        boolean includePlayers)
    {
        double viewDistance = viewRadius(player) * 16.0;
        Vec3 centre = player.position();
        AABB box = new AABB(centre.x - viewDistance, band.sourceY(), centre.z - viewDistance,
                            centre.x + viewDistance, band.sourceY() + AbyssWindow.BAND_HEIGHT,
                            centre.z + viewDistance);

        return level.getEntities((Entity) null, box, entity ->
        {
            //**Deliberately not `isAlive`.** A dying mob is removed some twenty ticks after it dies, and vanilla
            //keeps tracking it for that whole time so the client can play the death animation. Filtering on
            //`isAlive` here made a copied mob vanish the instant it died, which is why killing one in the
            //adjacent band looked like nothing happened. Only a removed entity stops being visible.
            if (entity.isRemoved()) return false;
            if (!entity.broadcastToPlayer(player)) return false;
            AABB bounds = entity.getBoundingBox();
            if (!AbyssWindow.inSlice(band, bounds.minY, bounds.maxY)) return false;

            //Vanilla's own visibility test, in the frame it is meaningful in: chunk tracking plus the type's
            //tracking range. The X/Z distance only - the entity is a band away in Y by construction, and
            //measuring that as distance would hide everything. The player's column is the same in every band.
            ChunkPos pos = entity.chunkPosition();
            if (!player.getChunkTrackingView().contains(pos)) return false;
            double dx = entity.getX() - centre.x;
            double dz = entity.getZ() - centre.z;
            double range = Math.min(entity.getType().clientTrackingRange() * 16.0, viewDistance);
            return dx * dx + dz * dz <= range * range;
        });
    }

    // ---------------------------------------------------------------------------------------------------
    // Add, update, remove
    // ---------------------------------------------------------------------------------------------------

    private static @Nullable Copy add(ServerPlayer player, Map<Key, Copy> table, Map<Integer, Copy> ids,
                                      Key key, int position, int slot, AbyssPattern.Band band, Entity entity)
    {
        int id = CopyIdentity.copyId(position, entity.getId());
        Copy existing = ids.get(id);
        if (existing != null)
        {
            //Should be impossible with distinct positions and distinct source ids. Logged rather than thrown:
            //a missing copy is survivable, a server crash on a tick is not.
            LOGGER.warn("[abyss] copy id {} for position {} collides with a live copy; skipping {}",
                        id, position, entity);
            return null;
        }

        Copy copy = new Copy(id, CopyIdentity.copyUuid(position, entity.getUUID()), position, entity);
        int offset = AbyssWindow.slotOffsetY(slot, band);
        Vec3 server = entity.trackingPosition();
        Vec3 client = toClient(player, server, offset);

        //A player copy needs a player-info entry with its patched UUID <b>before</b> the add packet, because the
        //client refuses to instantiate a player entity whose UUID it has no profile for:
        //
        //    if (packet.getType() == EntityTypes.PLAYER) {
        //        PlayerInfo playerInfo = this.getPlayerInfo(packet.getUUID());
        //        if (playerInfo == null) { LOGGER.warn("Server attempted to add player prior to sending player
        //            info ..."); return null; }
        //        return new RemotePlayer(this.level, playerInfo.getProfile());
        //    }
        //
        //The profile is rebuilt with the patched UUID rather than reused, because `Entity.recreateFromPacket` does
        //not set the entity's UUID - a `RemotePlayer` takes it from the profile. Reusing the real profile would
        //give every copy the real UUID, and the client's `EntityLookup.add` refuses the second one. Name and
        //properties (the skin) are carried over, so the copy looks like the player.
        //Everything that can fail is computed **before** the first packet is sent.
        //
        //The crash that prompted this went: spawn packet out, then `sendPayload` threw, so the copy was never
        //registered - the client was left holding an entity the server had no record of, and the next tick sent a
        //second add with the same UUID, which the client refuses. A ghost entity, plus a log that says nothing.
        //Reading the metadata first makes that shape impossible for any per-entity failure: nothing is announced
        //until the whole description has been built.
        List<SynchedEntityData.DataValue<?>> data = nonDefaultData(entity);
        Packet<?> spawn = spawnPacket(entity, id, copy.uuid, client);
        if (spawn == null)
        {
            LOGGER.warn("[abyss] no add packet for {}; copy {} not made", describe(entity), id);
            return null;
        }

        if (entity instanceof ServerPlayer subject) sendPlayerInfo(player, copy, subject);
        AbyssWindow.sendPreTranslated(player, spawn);
        sendPayload(player, id, entity, data);

        copy.lastServer = server;
        copy.lastYRot = entity.getYRot();
        copy.lastXRot = entity.getXRot();
        copy.offset = offset;
        copy.lastData = data;
        copy.lastMotion = entity.getDeltaMovement();

        table.put(key, copy);
        ids.put(id, copy);
        //After the copy is in the table, because the hierarchy packets have to be able to resolve the other
        //members' copies at this same position.
        sendHierarchy(player, copy, table);
        LOGGER.info("[abyss] spawn copy {} (uuid {}) for {} -> {} slot {} offset {} | server {} client {}",
                id, copy.uuid, describe(entity), band.level().identifier().getPath(), slot, offset,
                fmt(server), fmt(client));
        return copy;
    }

    /**
     * Announces a copied player's profile under its patched UUID.
     *
     * <p>Only the ADD_PLAYER action is sent. The other actions describe things a copy cannot change (game mode,
     * latency, display name); re-sending them would keep the tab list entry looking alive, and the entry is
     * deliberately unlisted.
     */
    private static void sendPlayerInfo(ServerPlayer player, Copy copy, ServerPlayer subject)
    {
        GameProfile real = subject.getGameProfile();
        GameProfile profile = new GameProfile(copy.uuid, real.name(), real.properties());
        ClientboundPlayerInfoUpdatePacket.Entry entry = new ClientboundPlayerInfoUpdatePacket.Entry(
                copy.uuid,
                profile,
                false,                                  //not in the tab list: a copy is not another player
                subject.connection == null ? 0 : subject.connection.latency(),
                subject.gameMode(),
                subject.getTabListDisplayName(),
                subject.isModelPartShown(PlayerModelPart.HAT),
                subject.getTabListOrder(),
                subject.getChatSession() == null ? null : subject.getChatSession().asData());

        ClientboundPlayerInfoUpdatePacket packet = new ClientboundPlayerInfoUpdatePacket(
                EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER), List.of(subject));
        //The packet can only be built from ServerPlayers, so it is built from the real one and its entry list
        //replaced with ours - the widener makes that field mutable.
        packet.entries = List.of(entry);
        AbyssWindow.sendPreTranslated(player, packet);
        LOGGER.info("[abyss] player info for copy {} ({}) of {}", copy.id, copy.uuid, real.name());
    }

    private static void removePlayerInfo(ServerPlayer player, Copy copy)
    {
        if (!(copy.source instanceof ServerPlayer)) return;
        AbyssWindow.sendPreTranslated(player,
                new ClientboundPlayerInfoRemovePacket(List.of(copy.uuid)));
    }

    /**
     * The spawn packet, with the copy's identity and client-space position but the source's own per-type data.
     *
     * <p>Built by asking the entity for its own packet and then replacing the parts that are about identity and
     * place. That is deliberate: {@code data} (object data - a falling block's state, a frame's direction, a
     * projectile's owner) is computed by an override of {@code getAddEntityPacket} for each type, and deriving
     * it here would be a per-type table that silently rots.
     *
     * <p>The {@code ServerEntity} passed in is a throwaway. Its constructor only records state - it sends
     * nothing; {@code sendChanges} is what sends - so this reads the entity's current position and rotation and
     * keeps no reference.
     */
    private static @Nullable Packet<?> spawnPacket(Entity entity, int id, UUID uuid, Vec3 client)
    {
        ServerEntity scratch = new ServerEntity(asServerLevel(entity), entity, UpdateInterval.NEVER, false,
                                               NO_OP_SYNCHRONIZER);
        if (!(entity.getAddEntityPacket(scratch) instanceof ClientboundAddEntityPacket vanilla)) return null;

        return new ClientboundAddEntityPacket(id, uuid,
                                              client.x, client.y, client.z,
                                              entity.getXRot(), entity.getYRot(),
                                              entity.getType(), vanilla.getData(),
                                              entity.getDeltaMovement(), entity.getYHeadRot());
    }

    /**
     * An entity's non-default synced data, never null.
     *
     * <p><b>{@code getNonDefaultValues()} returns null when every value is still at its default</b> - not an empty
     * list:
     *
     * <pre>
     *   List&lt;DataValue&lt;?&gt;&gt; result = null;
     *   for (DataItem&lt;?&gt; dataItem : this.itemsById) {
     *       if (!dataItem.isSetToDefault()) { if (result == null) result = new ArrayList&lt;&gt;(); result.add(...); }
     *   }
     *   return result;
     * </pre>
     *
     * <p>So "no metadata to send" is spelled {@code null}, and reading it as a list crashed the server tick the
     * first time a copied entity happened to have all-default metadata - a boat, in the report that found this. An
     * item frame has non-default values, which is why the earlier tests passed.
     *
     * <p>An empty list is used instead, so {@code lastData} is always comparable and always non-null.
     */
    private static List<SynchedEntityData.DataValue<?>> nonDefaultData(Entity entity)
    {
        entity.updateDataBeforeSync();
        List<SynchedEntityData.DataValue<?>> data = entity.getEntityData().getNonDefaultValues();
        return data == null ? List.of() : data;
    }

    /**
     * Metadata and attributes: what {@code ServerEntity.sendPairingData} sends besides the spawn packet.
     *
     * @param data the metadata, already read by the caller so a failure here cannot leave a half-made copy
     */
    private static void sendPayload(ServerPlayer player, int id, Entity entity,
                                    List<SynchedEntityData.DataValue<?>> data)
    {
        if (!data.isEmpty()) AbyssWindow.sendPreTranslated(player, new ClientboundSetEntityDataPacket(id, data));

        if (entity instanceof LivingEntity living)
        {
            Collection<AttributeInstance> attributes = living.getAttributes().getSyncableAttributes();
            if (!attributes.isEmpty())
                AbyssWindow.sendPreTranslated(player, new ClientboundUpdateAttributesPacket(id, attributes));

            //Equipment. Vanilla builds this the same way in `ServerEntity.sendPairingData`: every slot that holds
            //something, in slot order. An empty list is not sent, because the client's default is empty already.
            List<Pair<EquipmentSlot, ItemStack>> slots = new ArrayList<>();
            for (EquipmentSlot equipmentSlot : EquipmentSlot.values())
            {
                ItemStack stack = living.getItemBySlot(equipmentSlot);
                if (!stack.isEmpty()) slots.add(Pair.of(equipmentSlot, stack.copy()));
            }
            if (!slots.isEmpty())
                AbyssWindow.sendPreTranslated(player, new ClientboundSetEquipmentPacket(id, slots));
        }
    }

    /**
     * Riders and leashes, for one copy.
     *
     * <p>Both packets are id-to-id, which is why the tag is a constant function of position: every member of a
     * hierarchy at the same position gets the same tag, so {@code copyId(position, otherId)} resolves the partner
     * without a table.
     *
     * <p><b>Only partners that actually have a copy are linked.</b> Sending an id the client does not hold is
     * worse than sending nothing: the client either drops the packet or, for passengers, replaces the vehicle's
     * whole passenger list with ids it cannot resolve, and the riders disappear.
     */
    private static void sendHierarchy(ServerPlayer player, Copy copy, Map<Key, Copy> table)
    {
        Entity entity = copy.source;

        //Passengers.
        List<Integer> passengers = new ArrayList<>();
        for (Entity passenger : entity.getPassengers())
        {
            Copy child = table.get(new Key(copy.position, passenger.getId()));
            if (child != null) passengers.add(child.id);
        }
        //**Forced after an add.** The client resolves both ends of this packet by id at the moment it handles it,
        //and drops it - "Received passengers for unknown entity" - when the vehicle is not there yet, or silently
        //loses a rider whose add has not arrived. The add loop and this call are in the same tick, but not
        //necessarily in that order: a vehicle listed after its passenger is added before it. So a copy that has
        //just been added states its passenger list once unconditionally, and only then starts tracking changes.
        boolean force = copy.lastPassengers == null;
        List<Integer> previousPassengers = copy.lastPassengers;
        if (force || !passengers.equals(previousPassengers))
        {
            copy.lastPassengers = passengers;
            //An empty list still has to be sent when the copy *had* riders, or they stay on the client forever.
            if (!passengers.isEmpty() || (previousPassengers != null && !previousPassengers.isEmpty()))
            {
                ClientboundSetPassengersPacket packet = new ClientboundSetPassengersPacket(entity);
                packet.vehicle = copy.id;
                packet.passengers = passengers.stream().mapToInt(Integer::intValue).toArray();
                AbyssWindow.sendPreTranslated(player, packet);
                LOGGER.info("[abyss] copy {} of {} carries {}", copy.id, describe(entity), passengers);
            }
        }

        //Leash. The packet is addressed to the leashed entity, so it is sent for this copy when this entity is
        //the one on the lead.
        Integer leashId = null;
        if (entity instanceof Leashable leashable)
        {
            Entity holder = leashable.getLeashHolder();
            if (holder != null)
            {
                Copy holderCopy = table.get(new Key(copy.position, holder.getId()));
                if (holderCopy != null) leashId = holderCopy.id;
            }
        }
        if (!Objects.equals(leashId, copy.lastLeash))
        {
            copy.lastLeash = leashId;
            if (leashId != null)
            {
                ClientboundSetEntityLinkPacket packet = new ClientboundSetEntityLinkPacket(
                        entity, ((Leashable) entity).getLeashHolder());
                packet.sourceId = copy.id;
                packet.destId = leashId;
                AbyssWindow.sendPreTranslated(player, packet);
                LOGGER.info("[abyss] copy {} of {} leashed to copy {}", copy.id, describe(entity), leashId);
            }
        }
    }

    private static void update(ServerPlayer player, Copy copy, int slot, AbyssPattern.Band band)
    {
        Entity entity = copy.source;
        if (entity.isRemoved()) return;

        //The death animation belongs to the level the entity died in, and that level broadcasts it to its own
        //trackers - which does not include this player. So the copy's death has to be announced here, exactly
        //once, before the entity is removed and the copy with it.
        if (!entity.isAlive() && !copy.deathSent)
        {
            AbyssWindow.sendPreTranslated(player, AbyssEntityCopies.eventPacket(entity, copy.id, DEATH_EVENT));
            copy.deathSent = true;
            LOGGER.info("[abyss] death event sent for copy {} of {}", copy.id, describe(entity));
        }

        int offset = AbyssWindow.slotOffsetY(slot, band);
        Vec3 server = entity.trackingPosition();
        Vec3 client = toClient(player, server, offset);

        //A new slot means a new offset, and every delta the client holds is relative to the old one. So the
        //position is restated absolutely rather than patched.
        boolean offsetChanged = offset != copy.offset;
        boolean movementChanged = !server.equals(copy.lastServer);
        boolean rotationChanged = entity.getYRot() != copy.lastYRot || entity.getXRot() != copy.lastXRot;

        if (offsetChanged)
        {
            int before = copy.offset;
            AbyssWindow.sendPreTranslated(player, new ClientboundEntityPositionSyncPacket(
                    copy.id, PositionPath.of(client), entity.getYRot(), entity.getXRot(), entity.onGround()));
            copy.lastServer = server;
            copy.offset = offset;
            LOGGER.info("[abyss] re-offset copy {} for {}: slot {} offset {} -> {} | client {}",
                    copy.id, describe(entity), slot, before, offset, fmt(client));
        }
        else if (movementChanged)
        {
            //Encoded against the base the client holds, in server space - the delta is the same in either frame
            //because the offset cancels.
            VecDeltaCodec codec = new VecDeltaCodec();
            codec.setBase(copy.lastServer);
            VecDelta delta = codec.tryEncode(server);

            if (delta != null)
            {
                AbyssWindow.sendPreTranslated(player, rotationChanged
                        ? new ClientboundMoveEntityPacket.PosRot(copy.id, delta, pack(entity.getYRot()),
                                                                pack(entity.getXRot()), entity.onGround())
                        : new ClientboundMoveEntityPacket.Pos(copy.id, delta, entity.onGround()));
                copy.lastServer = server;
                if (LOGGER.isDebugEnabled())
                    LOGGER.debug("[abyss] move copy {} for {} by delta -> client {}",
                            copy.id, describe(entity), fmt(client));
            }
            else
            {
                //The step is larger than a delta can express (8 blocks an axis, at 1/4096). Vanilla's own
                //fallback is an absolute sync, and it is the only correct option.
                AbyssWindow.sendPreTranslated(player, new ClientboundEntityPositionSyncPacket(
                        copy.id, PositionPath.of(client), entity.getYRot(), entity.getXRot(), entity.onGround()));
                copy.lastServer = server;
                LOGGER.info("[abyss] copy {} for {} jumped too far for a delta; synced absolutely to {}",
                        copy.id, describe(entity), fmt(client));
            }
        }
        else if (rotationChanged)
        {
            AbyssWindow.sendPreTranslated(player,
                    new ClientboundMoveEntityPacket.Rot(copy.id, pack(entity.getYRot()),
                                                        pack(entity.getXRot()), entity.onGround()));
        }

        copy.lastYRot = entity.getYRot();
        copy.lastXRot = entity.getXRot();

        //Metadata: a snapshot comparison, never packDirty. See the class comment.
        List<SynchedEntityData.DataValue<?>> data = nonDefaultData(entity);
        if (!data.equals(copy.lastData))
        {
            AbyssWindow.sendPreTranslated(player, new ClientboundSetEntityDataPacket(copy.id, data));
            copy.lastData = data;
            //Debug: metadata changes often (item frame rotation, health, pose), so this is not an info-level event.
            if (LOGGER.isDebugEnabled())
                LOGGER.debug("[abyss] metadata of copy {} for {} changed to {}", copy.id, describe(entity), data);
        }

        //Velocity. Vanilla sends this when the source's motion changes, to its own level's trackers; a copy
        //that never gets one drifts on interpolation alone, which is what a dropped item looked like it was
        //doing. The motion itself needs no conversion - it is a rate, and a translation does not change one.
        Vec3 motion = entity.getDeltaMovement();
        if (!motion.equals(copy.lastMotion))
        {
            AbyssWindow.sendPreTranslated(player, new ClientboundSetEntityMotionPacket(copy.id, motion));
            copy.lastMotion = motion;
            if (LOGGER.isDebugEnabled())
                LOGGER.debug("[abyss] motion of copy {} for {} -> {}", copy.id, describe(entity), motion);
        }
    }

    /**
     * The lead and the leash, for one copy. Called from {@link #update} so a change is noticed.
     *
     * <p>Delegates to {@link #sendHierarchy} with the table, which is where the partner lookups happen.
     */
    private static void updateHierarchy(ServerPlayer player, Copy copy, Map<Key, Copy> table)
    {
        sendHierarchy(player, copy, table);
    }

    private static void remove(ServerPlayer player, Map<Key, Copy> table, Map<Integer, Copy> ids,
                               Key key, Copy copy, String why)
    {
        table.remove(key);
        ids.remove(copy.id);
        if (player.connection != null)
        {
            IntList one = new IntArrayList();
            one.add(copy.id);
            AbyssWindow.sendPreTranslated(player, new ClientboundRemoveEntitiesPacket(one));
            removePlayerInfo(player, copy);
        }
        LOGGER.info("[abyss] drop copy {} at position {} for {} ({})",
                copy.id, copy.position, describe(copy.source), why);
    }

    // ---------------------------------------------------------------------------------------------------
    // Teardown
    // ---------------------------------------------------------------------------------------------------

    /** Forgets everything about a player, and tells the client to drop the copies it was given. */
    public static void drop(ServerPlayer player)
    {
        Map<Key, Copy> table = tables.remove(player);
        byId.remove(player);
        ownSeen.remove(player);
        ownSeenBand.remove(player);
        lastShape.remove(player);
        EntityPacketLog.forget(player);
        if (table == null || table.isEmpty() || player.connection == null) return;

        IntList ids = new IntArrayList(table.size());
        for (Copy copy : table.values())
        {
            ids.add(copy.id);
            removePlayerInfo(player, copy);
        }
        AbyssWindow.sendPreTranslated(player, new ClientboundRemoveEntitiesPacket(ids));
        LOGGER.info("[abyss] dropped all {} copies of {}", ids.size(), name(player));
    }

    // ---------------------------------------------------------------------------------------------------
    // The own band
    // ---------------------------------------------------------------------------------------------------

    /**
     * Re-places every entity in the player's <b>own</b> band after the window's offset changed.
     *
     * <p>This is the bug that made an entity appear in the neighbouring band after a few crossings. The own
     * band's offset is <em>not</em> constant: it is {@code slot*64 - sourceY}, and at the ends of an acyclic
     * pattern {@code clampStart} pins the window start, so the player's slot changes on a crossing. In a
     * five-band stack the offsets are
     *
     * <pre>
     *   position 0 -> slot 0 -> offset  0     position 3 -> slot 1 -> offset 64
     *   position 1 -> slot 1 -> offset 64    position 4 -> slot 2 -> offset 128
     *   position 2 -> slot 1 -> offset 64
     * </pre>
     *
     * so 1 -> 0 and 3 -> 4 and 4 -> 3 all move the own band's offset by a whole band. Vanilla has no reason to
     * announce its entities again, and a stationary one - an item frame is the obvious case - never sends a
     * movement packet that could carry the correction. It stays drawn one band away, which reads as "the entity
     * moved to the neighbouring band".
     *
     * <p>A delta is invariant under this shift, so only one absolute position per entity is needed. The real ids
     * are used: these entities are vanilla-tracked, and the client already holds them.
     */
    public static void repositionOwnBand(ServerPlayer player)
    {
        AbyssWindow.Window window = AbyssWindow.window(player);
        if (window == null || window.band() == null || player.connection == null) return;
        MinecraftServer server = player.level().getServer();
        if (server == null) return;
        ServerLevel level = server.getLevel(window.band().level());
        if (level == null) return;

        int offset = AbyssWindow.bandOffset(player);
        int count = 0;
        for (Entity entity : visible(player, level, window.band(), true))
        {
            if (entity == player) continue;
            Vec3 client = toClient(player, entity.trackingPosition(), offset);
            AbyssWindow.sendPreTranslated(player, new ClientboundEntityPositionSyncPacket(
                    entity.getId(), PositionPath.of(client), entity.getYRot(), entity.getXRot(),
                    entity.onGround()));
            count++;
            if (LOGGER.isDebugEnabled())
                LOGGER.debug("[abyss] re-placed own-band {} -> client {}", describe(entity), fmt(client));
        }
        LOGGER.info("[abyss] own band re-placed for {}: {} entities at offset {}", name(player), count, offset);
    }

    // ---------------------------------------------------------------------------------------------------
    // Inbound
    // ---------------------------------------------------------------------------------------------------

    /**
     * A copy an action from the client is aimed at: the real entity, the band it lives in, and the Y offset its
     * copy was drawn with.
     */
    public record Target(Entity entity, ServerLevel band, int offset) {}

    /** Whether an id is one of ours, regardless of whether it currently resolves. */
    public static boolean looksLikeCopy(int copyId)
    {
        return CopyIdentity.tagOfCopyId(copyId) != 0;
    }

    /**
     * One line describing whether an entity is actually being ticked, and if not, which gate is closed.
     *
     * <p>`tickCount` is bumped by {@code Entity.commonTick}, which {@code ServerLevel.tickNonPassenger} calls before
     * {@code tick()} - so a value of 0 on a living entity means it has <b>never been ticked, not once since it
     * spawned</b>, and every other field here is an attempt to say why. They are reported separately because they
     * fail separately:
     *
     * <ul>
     *   <li>{@code simLevel} - the raw simulation-tracker level of the entity's chunk. {@code <= 31} is
     *       ENTITY_TICKING; this is the number tickets feed.</li>
     *   <li>{@code inRange} - {@code DistanceManager.inEntityTickingRange}, which is that same level through
     *       {@code ChunkLevel.isEntityTicking}.</li>
     *   <li>{@code canTick} - {@code ServerLevel.isPositionEntityTicking}, which is {@code inRange} <b>and</b>
     *       {@code entityManager.canPositionTick(pos)}. The second half is whether the entity section itself is
     *       registered as ticking; a chunk can be in range while its section is not, and then the entity is in
     *       the world but not in {@code entityTickList}, so it never receives a tick at all.</li>
     * </ul>
     */
    public static String tickState(Entity entity)
    {
        if (!(entity.level() instanceof ServerLevel level)) return "tickCount=" + entity.tickCount + " level=?";
        ChunkPos pos = entity.chunkPosition();
        long packed = pos.pack();
        var distance = level.getChunkSource().chunkMap.getDistanceManager();
        int simLevel = distance.getChunkLevel(packed, true);
        boolean loaded = level.getChunkSource().getChunkNow(pos.x(), pos.z()) != null;
        //Whether a ticket of each kind is present at all, which separates "no ticket" from "a ticket that does
        //not simulate" - the two ways simLevel can stay at 33.
        List<String> tickets = AbyssWindow.ticketsAt(level, pos);
        return "tickCount=" + entity.tickCount
               + ", chunk=" + pos.x() + "," + pos.z()
               + ", loaded=" + loaded
               + ", simLevel=" + simLevel
               + ", inRange=" + distance.inEntityTickingRange(packed)
               + ", canTick=" + level.isPositionEntityTicking(entity.blockPosition())
               + ", tickets=" + tickets
               + ", level=" + level.dimension().identifier().getPath();
    }

    /**
     * Resolves a client-side entity id to the copy it names.
     *
     * <p>Null for a real id: an entity in the player's own band is handled by vanilla and needs nothing from
     * here.
     */
    public static @Nullable Target target(ServerPlayer player, int copyId)
    {
        if (CopyIdentity.tagOfCopyId(copyId) == 0) return null;
        Map<Integer, Copy> ids = byId.get(player);
        Copy copy = ids == null ? null : ids.get(copyId);
        if (copy == null || copy.source.isRemoved()) return null;
        if (!(copy.source.level() instanceof ServerLevel band)) return null;
        return new Target(copy.source, band, copy.offset);
    }

    /**
     * A point the client reported in the copy's frame, in the band's own coordinates.
     *
     * <p>The copy was drawn at {@code client = server + offset}, so this is the inverse. The horizontal half goes
     * through the shuffle; the vertical half is the same offset for the whole click, which is the rule that keeps
     * a block and its hit point in one frame.
     */
    public static Vec3 toServer(ServerPlayer player, Target target, Vec3 clientPoint)
    {
        try
        {
            Vec3 shuffled = VecTransformer.getInstance(player).c2sTransform(clientPoint);
            Vec3 out = new Vec3(shuffled.x, shuffled.y - target.offset(), shuffled.z);
            if (LOGGER.isDebugEnabled())
                LOGGER.debug("[abyss] hit point {} in copy frame -> {} in {} (offset {})",
                        fmt(clientPoint), fmt(out), target.band().dimension().identifier().getPath(),
                        target.offset());
            return out;
        }
        catch (Exception e)
        {
            return new Vec3(clientPoint.x, clientPoint.y - target.offset(), clientPoint.z);
        }
    }

    /**
     * Announces a hurt animation for a copy.
     *
     * <p>Not an {@code EntityEvent}: in 26.3, event id 2 is {@code LivingEntity.onKineticHit} (the mace), and the
     * red flash is {@code hurtTime}, which the client sets in {@code LivingEntity.handleDamageEvent(DamageSource)}.
     * That is reached through {@code ClientboundDamageEventPacket} - so this is the packet to send, addressed to the
     * copy's id.
     *
     * <p>Only a hurt animation: the damage itself was already applied to the real entity by the dispatched attack.
     * The client's own health bar for the copy follows the metadata stream, which is separate.
     */
    public static void sendHurt(ServerPlayer player, int copyId, ServerLevel band, Entity source)
    {
        DamageSource attack = band.damageSources().playerAttack(player);
        AbyssWindow.sendPreTranslated(player, new ClientboundDamageEventPacket(
                copyId, attack.typeHolder(), player.getId(), -1, java.util.Optional.empty()));
        LOGGER.info("[abyss] hurt flash sent for copy {} of {}", copyId, describe(source));
    }

    /** Vanilla's death animation event id ({@code EntityEvent.DEATH}). */
    private static final byte DEATH_EVENT = 3;

    /**
     * An entity event packet addressed to a copy.
     *
     * <p>The packet has only a constructor taking an {@code Entity}, so it is built from the source and its id
     * field replaced - the widener makes that field mutable. Fabricating an {@code Entity} with the copy's id
     * instead would mean an entity that does not exist, and the constructor reads nothing else from the argument.
     */
    private static Packet<?> eventPacket(Entity source, int copyId, byte event)
    {
        ClientboundEntityEventPacket packet = new ClientboundEntityEventPacket(source, event);
        packet.entityId = copyId;
        return packet;
    }

    // ---------------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------------

    /** A server position as the client should be told it: horizontal map unchanged, Y by the band's slot. */
    private static Vec3 toClient(ServerPlayer player, Vec3 server, int offset)
    {
        try
        {
            Vec3 shuffled = VecTransformer.getInstance(player).s2cTransform(server);
            return new Vec3(shuffled.x, shuffled.y + offset, shuffled.z);
        }
        catch (Exception e)
        {
            //No answer for this position - the shuffle has no page for it. The offset is still correct, and a
            //copy at the wrong column is better than a missing one: the band itself is composed at the same
            //column in the demos, so this is the identity case anyway.
            return new Vec3(server.x, server.y + offset, server.z);
        }
    }

    private static int viewRadius(ServerPlayer player)
    {
        if (player.getChunkTrackingView() instanceof net.minecraft.server.level.ChunkTrackingView.Positioned view)
            return view.viewDistance() + 2;
        return 10;
    }

    private static byte pack(float degrees)
    {
        return Mth.packDegrees(degrees);
    }

    private static ServerLevel asServerLevel(Entity entity)
    {
        return (ServerLevel) entity.level();
    }

    /** Never called: the throwaway {@code ServerEntity} exists only to compute an add packet's per-type data. */
    private static final ServerEntity.Synchronizer NO_OP_SYNCHRONIZER = new ServerEntity.Synchronizer()
    {
        @Override
        public void sendToTrackingPlayers(Packet<? super ClientGamePacketListener> packet) {}

        @Override
        public void sendToTrackingPlayersAndSelf(Packet<? super ClientGamePacketListener> packet) {}

        @Override
        public void sendToTrackingPlayersFiltered(Packet<? super ClientGamePacketListener> packet,
                                                  java.util.function.Predicate<ServerPlayer> predicate) {}
    };
}
