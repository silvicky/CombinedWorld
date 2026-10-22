# Entities in the Abyss

**Status:** implemented for the demos; verified on a local client, not yet on a remote one.

The copy mechanism described below is in `AbyssEntityCopies` and `CopyIdentity`. Sections 4-8 and 10
(own-band offset, identity, which band is copied, ticking, the sync loop, inbound) are implemented, including
equipment, passengers, leashes, death and hurt events, and motion. Player copies are implemented too (section 9)
with one caveat recorded there: the copy's profile is rebuilt with the patched UUID, because
`Entity.recreateFromPacket` takes a player entity's UUID from its profile rather than from the add packet.

Still a known limitation: metadata that itself carries a position (section 14).

**Companion to:** `ABYSS.md`, which describes the vertical window (bands, patterns, positions, slots, the
composition of chunks and the crossing transition). Read the sections on vocabulary, the window lifecycle and
crossings first; this document assumes them.

**What it decides:** how an entity that exists once, on the server, is shown to a client as many entities in
a stacked world, and how a client's action on one of those copies reaches the real entity.

---

## 1. The model in one page

A client entity has exactly one position. The window shows up to three bands at once. Therefore an entity
that is visible in three bands must be **three client entities**.

So the mod fabricates **copies**: for each visible window position whose band differs from the player's own,
every entity in that band's view gets a synthetic client entity -- a fresh int id, a patched UUID, and a
position offset by the slot's band. The real entity is untouched and remains the only state that is
simulated, saved, or authoritative.

A copy is **virtual**: there is no `Entity` object for it on the server. It is a tuple

```
(copy id, copy uuid, source entity, window position)
```

and everything about it is derived from the source entity at the moment a packet is built. This is what makes
the whole feature reversible: stop sending the packets and no trace remains, because nothing was stored
anywhere that persists.

The player's own band needs no copies. The player is physically in that level, so vanilla's own entity
tracking already sends it, with the real ids; it only needs the window's Y offset applied on the way out
(which is the bug this document starts by fixing -- see section 4).

---

## 2. Corrections to the source note

The design note ("On the mapping of entities in abyss") reaches the right architecture. Six of its statements
are wrong or incomplete, and each one changes the implementation.

**2.1 "Game uses UUID or so to represent entities."** It uses an **int id**, and the UUID only appears in one
packet.

```
ClientboundAddEntityPacket      : int id  +  UUID uuid        (the only packet carrying a UUID)
ClientboundRemoveEntitiesPacket : IntList entityIds
ClientboundSetEntityMotionPacket: int id
ClientboundEntityPositionSyncPacket: int id
ClientboundSetEntityDataPacket  : int id
ClientboundAnimatePacket        : int id
ClientboundSoundEntityPacket    : int id
ClientboundUpdateAttributesPacket: int getEntityId()
ClientboundSetPassengersPacket  : int vehicle, int[] passengers
ClientboundSetEntityLinkPacket  : int sourceId, int destId
```

A copy therefore needs **both** identities, and the two collisions fail differently:

```java
// EntityLookup.add - client side
UUID uuid = entity.getUUID();
if (this.byUuid.containsKey(uuid)) {
    LOGGER.warn("Duplicate entity UUID {}: {}", uuid, entity);
} else {
    this.byUuid.put(uuid, entity);
    this.byId.put(entity.getId(), entity);     // <-- no duplicate check at all
}
```

A duplicate UUID means the copy is **silently not added**. A duplicate int id silently **overwrites** another
entity in `byId`, with no warning, and every subsequent update reaches the wrong entity.

**2.2 "Just like the horizontal one" -- no.** In the horizontal shuffle everything stays inside one
`ServerLevel`, so vanilla's `ChunkMap` tracking does the work and the mod only rewrites coordinates. In the
abyss the three visible bands are three different levels, and `ChunkMap.TrackedEntity` only offers an entity
to players in **its own** level. Vanilla will never send a foreign band's entities to this player. The mod must
synthesise the whole tracking relationship -- add, update, remove -- for every copy. This is the largest single
piece of work in the feature and it has no counterpart in the horizontal case.

**2.3 "A copy for each band corresponding to the dimension the entity belongs to."** Key copies by **window
position**, not by dimension. A pattern may name the same level in several positions: `backrooms:spiral` is one
cyclic band, so positions `p`, `p+1`, `p+2` are all `backrooms:level922`. Keyed by dimension you would produce
one copy, and the mob would appear in one of the three stacked bands instead of all three.

**2.4 "Since entities belong to the server world, they would never cross seams."** True of the simulation, and
the decision to leave it that way stands. But it does not follow that a copy's position is stable: a copy's
offset is a function of its **slot**, and slots change. See section 6.3 -- under the current
recentre-on-every-crossing policy, both foreign bands are replaced at every crossing, so copies are re-added
rather than re-offset.

**2.5 "This should not be a real problem since sent player entity is not a player."** It is a problem, and it
is in the client, verified in the decompiled 26.3 client:

```java
private @Nullable Entity createEntityFromPacket(final ClientboundAddEntityPacket packet) {
   if (packet.getType() == EntityTypes.PLAYER) {
      PlayerInfo playerInfo = this.getPlayerInfo(packet.getUUID());
      if (playerInfo == null) {
         LOGGER.warn("Server attempted to add player prior to sending player info (Player id {})", ...);
         return null;
      }
      return new RemotePlayer(this.level, playerInfo.getProfile());
   }
   ...
}
```

A player copy with a patched UUID is **refused** unless the client has a player-info entry for that UUID. So
player copies require an accompanying `ClientboundPlayerInfoUpdatePacket`, and a matching removal. See section
9. The user's intuition that a `RemotePlayer` is "not a player" is right about the object it creates; it does
not remove the precondition.

**2.6 "Client action on any copy applies to the underlying server entity."** The intent is right, the
mechanism cannot be a packet rewrite. Vanilla resolves the id in the player's **own** level:

```java
// handleAttack / handleInteract, 26.3
ServerLevel level = this.player.level();
Entity target = level.getEntityOrPart(packet.entityId());
```

A copy id resolves to nothing there, so rewriting it to the real id still finds nothing when the target lives
in another band. Both handlers must be wrapped, the action dispatched into the target's level, and the packet
cancelled. See section 10.

**2.7 "Simple enumeration and check of validity is OK."** Enumeration is right for the periodic validity sweep.
The per-tick add/update/remove path needs maps in both directions, because it runs per player per tick over
every visible entity.

**2.8 One thing the note gets exactly right, and worth stating as an invariant.** "Their movements are always
intact in case of relative, and offset in case of absolute ones." That is the same argument
`EntityPositionRemapper` makes for X/Z pages: a delta is `S_now - S_base` and survives any translation, so a
delta packet needs no edit; only absolute packets (add, position sync, teleport) carry a position to convert.
The one addition: the **base is per copy**, so the sync loop must remember what it last told the client, exactly
as `EntityPositionRemapper.last` does.

---

## 3. Decisions

Settled with the maintainer. Each is a constraint on the implementation, not a suggestion.

1. **Identity is (fresh int id, patched UUID)**, both unique per copy. Section 5.
2. **Keying is by window position.** `(pattern, position, entity) -> copy`, at most three per entity.
3. **Adjacent bands tick.** The window's tickets change from `PLAYER_LOADING` to `PLAYER_SIMULATION`, so mobs,
   items, redstone and block entities in the bands above and below run normally. Section 7.
4. **Interaction with copies is allowed.** With (3) in place there is nothing frozen left to work around: a
   hit on a copy damages, knocks back and kills the real entity, in its own band, live.
5. **All entity types are copied.** No filtering by type. The demos contain no natural entities; test entities
   are placed by hand.
6. **Players are copied too**, under the same rules, with the player-info requirement of section 9.
7. **Hierarchies are remapped**, which is trivial because the identity mapping is a constant function of
   position: a copied vehicle and its copied passengers get the same tag. Section 8.5.
8. **Inbound actions dispatch to the real entity in its own level.** Section 10.
9. **Every copy emits sounds and particles.** Accepted consequence: a mob heard from three bands is three times
   as loud. Section 11.
10. **The slide cost is accepted.** Copies are rebuilt on a crossing rather than re-offset in place. Section 12.
11. **Horizontal reach of copies equals the block window's**: `viewDistance + 2` chunks.
12. **Metadata that encodes positions is handled for item frames and end crystals**; other types are a known
    limitation. Section 14.
13. **Nothing is persisted.** Copies exist only in the outbound packet path. Section 13.
14. **Scope of the first implementation** and its verification order: section 15.

---

## 4. The own band (the bug to fix first)

Entities in the player's own band are already sent by vanilla with their real ids. What is wrong is the
coordinate: `EntityPositionRemapper` converts X/Z pages and never touches Y, so an entity in the player's own
band is drawn `bandOffset` blocks away from where it is -- 64 blocks low in the middle slot.

The fix is small and separate from the copy machinery:

- the outbound entity path (`ServerCommonPacketListenerImplMixin`, the `isEntityPacket` branch) must add
  `AbyssWindow.bandOffset(player)` to every absolute Y it converts, exactly as block updates already do;
- delta packets still need nothing, for the reason in 2.8;
- the player's **own** band offset is normally invariant across a crossing: `bandOffset = slot*64 - sourceY(band)`
  and the own band keeps both its `sourceY` and its slot (the crossing moves the player's local Y by a full band
  instead). So own-band entities normally need no re-sync on a crossing. The exception is the end of an acyclic
  pattern, where `clampStart` can change the slot on a crossing (see below); there the offset does change and
  own-band entities need the same absolute re-sync the copies get.

Do not assume the own band is slot 1. It is, for interior positions of an acyclic pattern, but `clampStart`
leaves the player in slot 0 or 2 at the ends of a pattern. Everything must read the slot from the window.

---

## 5. Identity

### 5.1 The tag

One small integer per window position, constant for as long as that position is in the window:

```java
static int tag(int position) { return 1 + Math.floorMod(position, 3); }   // 1..3, never 0
```

Three consecutive positions give three distinct tags. Tag `0` is reserved to mean "the real identity", used by
the own band, which vanilla sends.

```java
static int    copyId(int position, int serverId)   { return (tag(position) << 28) | serverId; }
static UUID   copyUuid(int position, UUID source)  { return new UUID(source.getMostSignificantBits()
                                                                      ^ ((long) tag(position) << 32),
                                                                      source.getLeastSignificantBits()); }
```

### 5.2 Why the id is not derived by XOR from the real id

`(tag << 28) | serverId` is injective for `serverId < 2^28` and cannot collide with a real id, which is
allocated by a monotonically increasing counter (`Level.getNextEntityId`) and is always a small positive int.
Deriving `serverId ^ tag` would collide with real ids once the counter reached the masked bits, which on a
long-lived server is a real possibility.

Guard: if `serverId >= 2^28`, log once and skip the copy. 268 million entity creations in one server lifetime
is not plausible, but a silent wrong id is worse than a missing copy.

### 5.3 Why the UUID patches the high half

`copyUuid` touches bits 32-33 of the most significant long. That leaves alone:

- the version nibble (bits 48-51) and the variant bits (62-63), so a patched v4 UUID is still a well-formed v4
  UUID;
- the entire least significant long, which is where Floodgate puts a Bedrock player's XUID
  (`new UUID(0, xuid)`). Patching the low half would collapse distinct Bedrock players onto one another.

The patched UUID is never equal to any real UUID, and two copies of one entity at different positions differ.
Collisions between two *different* entities' patched UUIDs require their real UUIDs to differ in exactly those
bits; with a few hundred visible entities that is astronomically unlikely, and the validity sweep in 5.4
catches it if it ever happens.

### 5.4 Validity

Per window rebuild, and once per second thereafter, walk the live copies and check:

- the source entity still exists, is not removed, and is still inside the view;
- its two identities are still unique (no duplicate id or UUID anywhere in the copy table).

Anything that fails is removed and, if still visible, re-added.

---

## 6. Which band is copied

### 6.1 Own band

Vanilla. Real ids, real UUIDs, positions converted by the outbound path of section 4. No copy is created for
the own **position** -- not for the own *level*: in the spiral, the own level is also visible in the two other
positions, and those two do get copies of the same entities.

### 6.2 Foreign bands

For every other visible position, every entity in that band's view inside the horizontal radius gets a copy.
"View" means the player's chunk-tracking view: the same set of columns whose chunks are composed into the
window.

### 6.3 What a crossing does to copies

With the current policy (`ABYSS.md` 5.3, recentre so the player is always in the middle slot), a crossing from
`p` to `p+1` changes the window from `(p-1, p, p+1)` to `(p, p+1, p+2)`:

| band | before | after |
|---|---|---|
| `p-1` | slot 0, copies | leaves the window: copies removed |
| `p` | slot 1, vanilla | slot 0: copies **added** |
| `p+1` | slot 2, copies | slot 1, vanilla: copies **removed** |
| `p+2` | absent | slot 2: copies **added** |

So **no copy survives a crossing**. Every crossing removes two bands' worth and adds two bands' worth. This is
the reason decision 10 accepts the cost rather than optimising it: at two crossings per second while falling,
that is four band-populations per second, and each is a burst of add and remove packets.

The tag function is still defined on the position rather than the slot, for two reasons: it is the constant
function that makes hierarchy remapping free (2.7, 8.5), and it stays correct under the alternative
edge-slot policy, where copies *would* survive crossings and only their offset would change.

---

## 7. Ticking

The window's chunks are currently held by `PLAYER_LOADING`:

```
FLAG_PERSIST=1  FLAG_LOADING=2  FLAG_SIMULATION=4  FLAG_KEEP_DIMENSION_ACTIVE=8
PLAYER_LOADING    = register("player_loading",    0L,  2)   // loading only
PLAYER_SIMULATION = register("player_simulation", 0L, 12)   // loading + simulation + keep active
```

`keepWindowLoaded` must switch to `PLAYER_SIMULATION` for the window's bands, so the bands above and below
run: no statues, no items that never despawn, and interaction (decision 4) has something live to act on.

Consequences to accept and document:

- **Cost.** Three levels simulate a player-sized radius around the same column. This multiplies the entity,
  block-entity and random-tick cost of the window, not its chunk cost (which is already paid).
- **Despawn.** Items and mobs in adjacent bands age and despawn normally. A player who lingers at a seam will
  watch the band above them clear out. That is correct behaviour for a real world, and the alternative
  (frozen bands) was rejected.
- **Natural spawning is unaffected.** Mob spawning is driven by players *in that level*; adjacent bands have
  no real player, so nothing spawns there. Structures, spawners and hand-placed entities still run.
- **Redstone** in adjacent bands runs, which is the main argument for this decision: a contraption across a
  seam should not stop when you walk to the band below it.

---

## 8. The copy sync loop (outbound)

Runs on the server thread, once per tick, per player, before the window's own transition (so a crossing sees a
consistent table). Kept in a class of its own, `AbyssEntityCopies`, with the window state read from
`AbyssWindow`.

### 8.1 Discover what should be visible

For each foreign position, query the band level for entities in the view: one AABB covering the horizontal
extent of the player's chunk view and the band's Y slice, consumed with a distance filter on chunk columns. This
is proportional to the entities present, not to the volume.

Diff against the table:

- present now, absent before -> **add**;
- absent now, present before, or the source is removed/dead -> **remove**;
- present in both -> **update**, if anything changed.

### 8.2 Add

Build the vanilla add packet for the source entity and then replace its three identity/position fields. Building
the vanilla one first is deliberate: `ClientboundAddEntityPacket(Entity, ServerEntity)` derives the `data` field
(object data: block state for a falling block, direction for a frame, ...) and the delta movement, and
re-deriving those by hand is how per-type bugs get in.

```java
ClientboundAddEntityPacket vanilla = new ClientboundAddEntityPacket(entity, serverEntity);
// read id, uuid, x, y, z, xRot, yRot, type, data, deltaMovement, yHeadRot from it (reflection),
// then rebuild:
new ClientboundAddEntityPacket(copyId, copyUuid, x, y + offset, z, xRot, yRot, type, data, delta, yHeadRot);
```

The `ServerEntity` passed in is a throwaway (a no-op `Synchronizer`); it exists only to derive `data`.
Constructing one stores state and sends nothing -- `sendChanges` is what sends -- but confirm that before
relying on it, and fall back to deriving `data` per type if the constructor turns out to have side effects.

For a player copy, the player-info entry must be sent **before** the add, or the client discards it (2.5).

The table records what was sent -- server position and client position -- because deltas are encoded against
it, exactly as `EntityPositionRemapper` records both halves.

### 8.3 Update: movement

Two packet kinds, chosen exactly as `EntityPositionRemapper` chooses:

```java
VecDeltaCodec codec = new VecDeltaCodec();
codec.setBase(lastSentServer);                    // the base the client holds, in server space
VecDelta delta = codec.tryEncode(serverPos);      // null when the delta cannot be represented
```

- delta encodable -> `ClientboundMoveEntityPacket.Pos` / `.PosRot(copyId, delta, ...)`, forwarded as is. The
  client's own position is in client space and the delta is a difference, so it needs no conversion;
- not encodable, or a forced full update is due -> `ClientboundEntityPositionSyncPacket(copyId,
  PositionPath.of(clientPos), yRot, xRot, onGround)`, with the position converted.

Converted means: X/Z through `VecTransformer.s2cTransform` (identity in the abyss demos, which compose chunks
at the same column), Y through the slot offset.

Mirror vanilla's cadence rather than inventing one: a full sync every `ServerEntity.FORCED_POS_UPDATE_PERIOD`
ticks and whenever the delta would be too large (`VecDeltaCodec.isDeltaTooBig`), so a copy behaves like an
ordinary tracked entity.

### 8.4 Update: metadata

`ClientboundSetEntityDataPacket(copyId, changedValues)`.

**`ServerEntity` cannot be reused for this**, and the reason is worth recording because it looks like an obvious
shortcut:

```java
// SynchedEntityData.packDirty(), verified in 26.3
if (!this.isDirty) return null;
this.isDirty = false;                       // <-- global, single consumer
... for each item: if (item.isDirty()) { collect; item.setClean(); }
```

Dirty state is consumed by the first reader. An entity in a foreign band is also tracked by players in its own
level, whose `ServerEntity` will pack the dirty values first; a second `ServerEntity` belonging to the copy
would then send nothing, or a copy would steal updates from the real tracker. So copies must diff snapshots:

```java
List<DataValue<?>> now = entity.getEntityData().getNonDefaultValues();
// compare against the last list sent; send the entries that differ
```

`getNonDefaultValues()` reads without clearing. The packet constructor
`ClientboundSetEntityDataPacket(int, List<DataValue<?>>)` is public, so no reflection is needed here.

### 8.5 Update: passengers and links

Both packets are id-to-id:

```java
ClientboundSetPassengersPacket(int vehicle, int[] passengers)
ClientboundSetEntityLinkPacket(int sourceId, int destId)
```

The tag makes this free: every entity in the copied hierarchy gets the **same position's** tag, so

```java
copyOf(position, otherEntity) = copyId(position, otherEntity.getId())
```

is the whole mapping -- decision 7's "constant function" observation. Two consequences:

- both packet types are built by us, not forwarded, because our player is not in the source level. Their int
  fields are private and final, so they need either reflection (the pattern the mod already uses for packet
  fields) or access-widener `mutable` entries;
- a passenger whose vehicle is outside the view has no copy; drop the pairing rather than send a link to an id
  the client does not have.

### 8.6 Remove

`ClientboundRemoveEntitiesPacket(IntList copyIds)`, batched per tick. For player copies, also
`ClientboundPlayerInfoRemovePacket(List<UUID> patchedUuids)`.

Removal happens when: the entity leaves the view, the entity is removed or dies, its band leaves the window, or
the player leaves the world (the whole table is dropped).

### 8.7 Why not a real `ServerEntity` per copy

Evaluated and rejected, for the reason in 8.4: `ServerEntity` is built around `packDirty()` and a tracking set,
both of which are single-consumer. Reusing it would corrupt the real entity's metadata stream. The public
constructor and the three-method `Synchronizer` interface make it tempting; the dirty flag makes it unsafe.

---

## 9. Player copies

A player copy is an ordinary copy whose source is a `ServerPlayer`, plus one extra obligation (2.5):

```java
// before the add packet
new ClientboundPlayerInfoUpdatePacket(EnumSet.of(ADD_PLAYER, UPDATE_LISTED),
        List.of(new Entry(patchedUuid, profile, false /* listed */, latency, gameMode,
                          displayName, showHat, listOrder, chatSession)));
```

`Entry` is public with all nine components, so this is constructible. `listed = false` keeps copies out of the
tab list -- a client must know about the profile to create the entity, but the extra rows would be visible
noise. The name tag and skin come from the profile, so a copy looks exactly like the player.

**The profile must carry the patched UUID, not the real one.** `Entity.recreateFromPacket` sets the id and the
position but never the UUID:

```java
public void recreateFromPacket(ClientboundAddEntityPacket packet) {
   int id = packet.getId(); ... snapTo(x, y, z, yRot, xRot); setId(id);
}
```

and a `RemotePlayer` takes its UUID from the profile it is constructed with. So reusing the real profile would
give every copy the real UUID, and the client's `EntityLookup.add` refuses the second one. The copy is therefore
built with `new GameProfile(copyUuid, realName, realProperties)` -- name and skin preserved, UUID patched, so
the copy is consistent with the add packet it is sent with.

On removal, `ClientboundPlayerInfoRemovePacket`.

Two known consequences, both acceptable:

- the patched UUID has no meaning outside this client. Chat, advancements and scoreboard entries are keyed by
  the real UUID and are unaffected;
- `ClientboundPlayerInfoUpdatePacket` also drives tab completion of player names; `listed = false` is what
  keeps copies out of it.

---

## 10. Inbound

### 10.1 The lookup problem

Both interaction handlers resolve the target in the player's own level (2.6), so neither a rewritten id nor a
new packet is sufficient. Verified 26.3:

```java
handleInteract: ServerLevel level = this.player.level(); Entity target = level.getEntityOrPart(packet.entityId());
handleAttack:   ServerLevel level = this.player.level(); Entity target = level.getEntityOrPart(packet.entityId());
```

So each handler is wrapped. The flow, mirroring `ServerPlayerGameModeMixin.item_storage$dispatch`:

```java
copy = copies.lookup(packet.entityId());          // null -> run vanilla unchanged
if (copy == null) { original.call(packet); return; }

// resolve the real target and its band
Entity target = copy.source();
ServerLevel band = (ServerLevel) target.level();

// dispatch: swap the player's level for the duration, publish it for code that reads player.level()
swap player.level -> band  (access widener: Entity.level is private, non-final)
try { original.call(rebuilt packet with the real id); }
finally { restore player.level; }
```

The rebuilt packet needs no field mutation: both packets have public constructors taking the id
(`ServerboundAttackPacket(int)`, `ServerboundInteractPacket(int, InteractionHand, Vec3, boolean)`), so build a
new one. That is cleaner than the access-widener route the mod uses for `ServerboundInteractPacket.location`,
and it is why the existing `location` widening covers only the *hit point*.

Swapping the player's level is the part that needs the most care: damage, knockback, death, sound and particles
all derive from `player.level()`, so the swap must cover the whole call, and must be restored in a `finally`.
If this proves unstable, the fallback is to reimplement the small interaction surface directly rather than
delegate to vanilla's handler.

### 10.2 The hit point

`ServerboundInteractPacket.location` is a point in the client's frame. It must be converted with the **copy's**
offset, not the player's own band offset -- the same rule as `toServerClick` for blocks: one offset for the
whole click, or the block and the point end up a band apart and vanilla rejects the packet as physically
impossible.

### 10.3 Reach

Already solved. `PlayerMixin` redirects `isWithinEntityInteractionRange(AABB, double)` and `isWithinAttackRange`
and measures through `nearestClientY`, so a copy in an adjacent band is reachable when the player's own eye is
near its *client* image. Nothing new is needed -- but the wrap must not run the reach test a second time in
server space, or a legitimate cross-band hit is refused after the dispatch has already decided it is valid.

---

## 11. Sounds and particles

Decision 9: every copy emits. `ClientboundSoundEntityPacket` is id-keyed, so emitting for all copies means
building one packet per copy id.

Accepted consequence: a mob in an adjacent band is heard at one third the distance it appears to be, and a
player standing at a seam hears everything twice or three times. If this turns out to be intolerable in
practice, the cheapest correction is volume attenuation by slot distance, not suppression -- suppression
produces holes where a walking mob goes silent as it crosses a seam.

Entity-positioned particles (`ClientboundLevelParticlesPacket` at an entity position) follow the same rule.

---

## 12. Slides and cost

Per 6.3, a crossing rebuilds both foreign bands. Budget, per crossing:

- removes: every copy in the two bands that leave their foreign slots;
- adds: every copy in the two bands that enter them, plus player-info entries for player copies;
- metadata: one full snapshot per added copy.

At two crossings per second during a fall, that is four band-populations per second. This is accepted
(decision 10) because it is bounded, it coincides with a moment the player is already waiting a round trip for
the seam teleport, and the alternative is a redesign of the recentring policy.

Recorded for later: the entity work gives a second, independent reason to revisit that policy. Under an
edge-slot scheme (recentre only when the player would leave the window), most crossings move nothing -- no
teleport, no chunk re-composition, no entity churn -- and every other crossing costs half of what it does now.

---

## 13. Persistence

Nothing. Copies are not entities, are never added to a level, and are never saved. The save format is
unchanged. Removing this feature leaves no residue.

The only durable state a copy creates is client-side, and it dies with the client's connection.

---

## 14. Known gaps

- **Metadata that encodes positions.** `SynchedEntityData` for some types carries positions or block
  positions: item frame item rotation, end crystal beam target, fishing hook, area effect cloud. Item frames
  and end crystals are handled by converting those specific accessors (decision 12); anything else is a
  limitation, and the symptom will be a copy whose metadata points at a band the player is not looking at.
- **Entities that are invisible to their own level.** An entity in a foreign band that its own level's players
  cannot see (spectral, invisibility) is still copied; visibility metadata is forwarded, so the client applies
  it. Fine, but it means a copy is not "what another player would see" -- it is the entity with metadata.
- **Vehicles.** A copy of a boat with a copied passenger is consistent, but the *client* also simulates
  passenger interpolation locally for the local player only; remote passengers are drawn from the packets.
  Expect minor jitter on a moving vehicle seen from another band, and do not chase it.
- **World border and interaction bounds.** Vanilla checks the world border in the target's level; bands are
  ordinary levels and share the server's border, so this is consistent. It has not been exercised.
- **Entity count.** The cost is `O(entities in view) x 2 bands` per player per tick. A busy demo with a few
  hundred entities in the view is the point at which the fixed per-tick diff should be replaced by an
  event-driven one (hook the add/remove of `ServerLevel`, rather than scanning).

---

## 15. Implementation order

Each step is independently testable, and each should be verified before the next begins.

1. **Own band Y offset** (section 4). Place one entity in the player's own band. It must appear at the player's
   own level, not 64 blocks below. No copies involved.
2. **Copies of one adjacent band, static.** Copy only `LivingEntity`, only slot 0. Place a mob one band below:
   it must appear below, at the right Y, and vanish when it leaves the view.
3. **Movement.** The same mob, now pushed or walking in its own level. Delta packets must track it. Walk to a
   seam so a `Pos` packet arrives while the mob crosses a page boundary (X/Z) to exercise the absolute
   fallback.
4. **Both adjacent bands, cyclic.** `abyss:cycle` and `backrooms:spiral`. In the spiral the same mob must appear
   three times, 64 apart.
5. **Crossing.** Fall through two seams. Copies must be removed and re-added, with no leftover entity in the
   client's world (check with F3+B and by looking for ghost hitboxes).
6. **Inbound: attack.** Hit a copied mob in an adjacent band in creative. It must die in its own band, with the
   death particles in the right band, and the copy must disappear.
7. **Inbound: interact** with a mob that has an interaction (villager, item frame if enabled). Same standard.
8. **Player copies.** A second client in another band. Must appear, be named correctly, stay out of the tab
   list, and disappear on disconnect.
9. **Hierarchies.** A mob on a boat, a leashed mob. Consistent copies, no floating or desynchronised riders.
10. **Metadata.** A sheep (colour, sheared), an item frame, an end crystal. All must reflect changes made in the
    real band.

The demos:
- `abyss:stack` and `abyss:cycle`: mobs placed by hand in `layer0..4`, at least one per band, one crossing a
  seam.
- `backrooms:spiral`: one mob, required to appear three times.

---

## 16. Planned file map

| File | Role |
|---|---|
| `abyss/AbyssEntityCopies.java` | The copy table, the per-tick diff, add/update/remove. New. |
| `abyss/CopyIdentity.java` | `tag`, `copyId`, `copyUuid`, validity checks. New. |
| `abyss/AbyssWindow.java` | Ticket type change (7); expose the view's chunk columns for discovery. |
| `mixin/ServerCommonPacketListenerImplMixin.java` | Own-band Y offset on the entity path (4). |
| `mixin/ServerGamePacketListenerImplMixin.java` | Wrap `handleInteract`, `handleAttack` (10). |
| `mixin/ServerEntityMixin.java` or `ChunkMapMixin` | Only if the own-band Y conversion cannot be done in the outbound hook; prefer the hook. |
| `core/src/main/resources/1.accesswidener` | `Entity.level` (mutable), and the private int fields of the two link packets if reflection is not used. |

---

## 17. Open questions

Not blocking; recorded so they are decided deliberately rather than by accident.

1. **Should a copied player be attackable across a band?** Vanilla allows it in one level; the same rule applied
   literally means a hit through a seam. Consistent with decision 4, but PvP across a seam is a policy question
   for a server owner, not a correctness one.
2. **Should the copy of an invisible entity copy invisibility?** Metadata forwarding does it automatically.
   Deciding otherwise would need a filter, and a filter is a per-type table.
3. **At what entity count should the per-tick diff become event-driven?** See section 14, last item. Measure
   before deciding; the answer depends on how many entities a real Abyss level holds.
