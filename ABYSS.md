# The Abyss — vertical concatenation of dimensions

**Status:** working demo, not a finished feature. This document describes what the code does today, what it
deliberately does not do, and which invariants the implementation depends on. Where something is broken or
unfinished it says so.

**Reading order:** §1–§3 are the model and can be read without code. §4 onward is implementation and assumes
you know Minecraft's chunk pipeline (see `CONVENTION.md` Part 2 for the horizontal shuffle, which this borrows
heavily from).

---

## 1. What this is

The Abyss shows a player **several dimensions stacked vertically, as if they were one dimension**. The player
physically stands in one real level; the client is shown a single dimension whose Y axis is a concatenation of
slices taken from several levels.

It is the vertical counterpart of the Backrooms shuffle. That one rewrites **which chunk** a client sees at a
given (x, z). This one rewrites **which level** a client sees at a given Y. Both are per player, both are done
at the packet boundary, and neither changes what the server actually simulates.

Three things are deliberately *not* changed:

- chunk columns (x, z) are untouched — chunk tracking, view distance and the batch sender all run vanilla;
- entity simulation, block ticking, redstone and mob spawning all happen in the real levels, normally;
- the save format. Every band is an ordinary dimension with an ordinary region file.

## 2. Vocabulary

| Term | Meaning |
|---|---|
| **Band** | One slice of the window: `(level, sourceY)`. Names a real level and the Y at which the slice starts inside it. |
| **Pattern** | An ordered list of bands, plus a band height and a `cyclic` flag. This is the thing a server owner configures. |
| **Window** | The three consecutive bands a given player is currently shown. |
| **Position** | An absolute index into the pattern. This is state — it is *not* derived from the player's level. |
| **Window start** | The pattern index shown in slot 0. `position - windowStart` is the player's **slot**. |
| **Slot** | 0 (bottom), 1 (middle), 2 (top). The only three values the client renders. |
| **Offset** | `clientY - serverY` for a player. Changes only when the window changes. |

The distinction between *position* and *level* is the whole design. An earlier version derived the band from
`levelIndex(player.level())`, which made three things impossible: the same level appearing twice in one window,
a user-chosen order, and a cyclic pattern with no end.

## 3. Coordinate model

The client's Y is the window's Y:

```
clientY = serverY + WINDOW_MIN_Y + slot * BAND_HEIGHT - sourceY(band)
slot    = position - windowStart
```

with, today, `WINDOW_MIN_Y = 0`, `BAND_HEIGHT = 64`, `WINDOW_LAYERS = 3`, `WINDOW_HEIGHT = 192`.

Worked example, `abyss:stack`, player standing on layer 1's floor:

```
position 1, windowStart 0, slot 1
clientY = 0 + 0 + 1*64 - 0 = 64        (one band above the window floor)
```

They fall through the floor into layer 0. The crossing moves them to layer 0 at server y ≈ 63.9, and recentres
so `windowStart` becomes -1 clamped to 0, `position` 0, `slot` 0:

```
clientY = 63.9 + 0 + 0*64 - 0 = 63.9   (continuous: they fell one block)
```

The offset changed by −64 and the server Y changed by +64; they cancel, and the client sees a one-block move.

### 3.1 Why recentring is mandatory, not cosmetic

The window renders slots 0..2 only. A player is required to be in **slot 1** so that there is terrain visible
both above and below them. If a crossing updated `position` without moving `windowStart`, the player would sit
in slot −1 or 3 — a band the client is not being sent. There is no later mechanism that fixes this: the closing
move is a teleport into a slot nobody renders, and the next crossing cannot happen because the player is
standing in geometry that was never composed for them.

Two visible symptoms came from violating this, in opposite directions:

- keeping the window still at a crossing → "I cannot see the layer above until I approach it" (player in slot 3);
- sliding the window *and* recentring at the crossing → world and player shift by a band at the same moment, and
  because the composition is re-sent over many ticks, the client ends up looking at old terrain from a new
  position ("the sliding direction is wrong").

The rule that survives both: **a crossing is one atomic transition** — window update, composition re-send, and
position packet, in that order, under one flag.

## 4. Patterns

### 4.1 Shape

```java
record Band(ResourceKey<Level> level, int sourceY)
new AbyssPattern(String name, int bandHeight, boolean cyclic, List<Band> bands)
```

`sourceY` lets a band take its slice from the middle of a taller level, so one 256-tall level can supply four
different bands. It must be a multiple of 16: a band is a whole number of sections.

`cyclic` changes only the index arithmetic:

- acyclic — `band(i)` is `null` outside `[0, length)`, so the stack has a bottom and a top;
- cyclic — `band(i)` wraps with `Math.floorMod`, so the pattern has no end and `position` may fall forever in
  either direction.

### 4.2 Validation

`AbyssPattern`'s constructor rejects:

- `bandHeight` ≠ `WINDOW_HEIGHT / WINDOW_LAYERS`. One window dimension type means exactly one band height; a
  pattern with another value would produce chunks that cannot fit the client's section count.
- a `sourceY` that is not a multiple of 16;
- an empty band list.

These are registration-time failures with a message naming the pattern, not runtime surprises.

### 4.3 Where a player belongs

`indexOf(level, y)` finds the band whose slice contains `y`, preferring the first match, and falls back to the
first band of that level. It is used only when a player arrives from outside — a join, a warp, a teleport — not
during normal travel. See §6.

### 4.4 Cyclic geometry is the pattern's problem, not the window's

The window will happily concatenate a level that does not repeat, but a seam then shows a break: the top of one
band does not continue into the bottom of the next. Two properties make a cyclic pattern correct:

1. **Y-periodicity.** The geometry must satisfy `block(x, y, z) == block(x, y + P, z)` for `P = bandHeight`.
2. **A landing at the wrap.** A player crossing from the top of band *k* arrives at the *bottom* of band *k+1*
   at the same x/z. If the two bands are the same level, that column cannot have a floor at both ends — so the
   geometry has to provide a place to be.

Both demos solve this differently; see §9.

## 5. The window lifecycle

### 5.1 Creation

`window(player)` returns the stored window if its band's level still matches `player.level().dimension()`, and
otherwise (re)creates it from `indexOf`. The mismatch test is skipped while `seamTeleports` contains the player:
between `setWindow` and the teleport completing, the stored window deliberately disagrees with the player
object's level, and anything re-deriving it in that gap would discard the move. That gap caused a real bug —
the window was rebuilt mid-transition and the player's `position` was reset to the first band of the level.

### 5.2 Tick order

`AbyssWindow.tick(server)` runs at **`START_SERVER_TICK`**, before player ticks:

1. drain the chunk-load queue (§7.3);
2. for each player: top up tickets, initial full send, travel, periodic healing.

The early position matters: every chunk packet sent during the same tick is composed with the offset that is
already current, so nothing can cross a window change carrying the old one.

### 5.3 Crossing (the only thing that changes composition)

`travel` runs when the player's local Y leaves `[0, BAND_HEIGHT)`:

```
local      = serverY - sourceY(band)
serverY'   = local ± BAND_HEIGHT + sourceY(target)
```

The transition, in order:

```
seamTeleports.add(player)            // spans the whole transition
setWindow(nextWindow)                // recentre: windowStart = position - 1, clamped
if (bands changed) {
    resendChunks(player)             // queue every tracked column
    flushChunks(player)              // send one batch now, nearest-first
}
sendTeleport(player, target, y)      // the position packet
seamTeleports.remove(player)
```

Ordering rationale:

- **Composition before position.** The client is never told to stand somewhere whose terrain has not been queued.
  `PlayerChunkSender` sorts by distance, so the flushed batch is the terrain around the player; the rest streams
  in behind them.
- **Flag spans everything.** The stored window is inconsistent with `player.level()` for part of this block.
- **No slide without a crossing.** A window shift unattached to a player movement would translate the whole world
  with no physical event justifying it, and cannot be made seamless while chunk packets are throttled. The
  standalone slide was removed after it produced exactly that glitch.

`clampStart` keeps `windowStart` inside `[0, length - WINDOW_LAYERS]` for acyclic patterns and off both ends for
cyclic ones. At an acyclic end the player may legitimately end up in slot 0 or 2; there is nothing further to
show in that direction.

## 6. Composition

`AbyssChunkComposer.compose(player, pos)` builds the `ClientboundLevelChunkWithLightPacket`. It is called from
the outbound hook, per player, and replaces the packet entirely.

### 6.1 Sections

The client decodes exactly `WINDOW_SECTIONS` (12) sections, because the section count is **not** in the packet —
the client uses its own `ClientLevel` section count. So:

- every band is written, `SECTIONS_PER_BAND` (4) sections at a time, in ascending slot order;
- sections are copied from `band.sourceY / 16` onward;
- a band with no loaded chunk is padded with **air**, and the column is recorded as incomplete (§7.3).

### 6.2 Heightmaps

Recomputed from the composed sections at `ceillog2(WINDOW_HEIGHT + 1)` bits. They cannot be copied from a band:
the bit width depends on the height, and a band's array is packed for its own height. Air sections are skipped
whole, which is what keeps this cheap.

### 6.3 Light

Masks are `BitSet`s indexed by `sectionY - minLightSection`, and `minLightSection` is `minY/16 - 1` on every
level. A band's bit *i* is therefore already relative to its own light engine, so composing is:

```
windowIndex = slot * SECTIONS_PER_BAND + i + 1
```

The `+1` is the section below `minY`. This is independent of `min_y` and of `sourceY`, which is why a slice from
the middle of a tall level needs no special handling.

**Known gap:** light *data* is carried, light *updates* are only re-masked (see §8), and a band whose light has
not been computed yet composes as "empty mask" — correct, dark, and never re-sent when the light arrives.

## 7. Chunks, loading and healing

### 7.1 Tickets

The bands nobody stands in must stay loaded or composition finds no chunk. `PLAYER_LOADING` is used:

- no timeout (`ENDER_PEARL` has a 40-tick timeout, which unloaded the lower bands two seconds after the player
  stopped moving — that was a long-standing "adjacent layer missing" cause);
- loads without simulating.

Tickets are released explicitly when the window moves or the player leaves, because they never expire.

**Known gap:** `TicketStorage.addTicket` treats same type + level as a duplicate. Two players sharing a band
share one ticket, so one player's window moving away can release a chunk the other still needs.

### 7.2 View radius

`viewDistance + 2` chunks around the player's chunk, in every band of the window. The whole visible square is
composed from all three bands, so the radius is the view, not the player's column.

### 7.3 Late bands

`ServerChunkEvents.CHUNK_LOAD` fires **off the server thread** — inside the chunk-status task chain. The handler
therefore only records `(level, chunkPos)`; the server thread drains it at the start of the next tick and
re-queues the host column. Touching `PlayerChunkSender.pendingChunks` from the event thread lost inserts, and a
lost insert is a chunk the client never receives.

A column sent while a band was still generating is also recorded in `incomplete`, swept once a second: anything
now complete is re-sent, anything out of view is forgotten.

### 7.4 Offsets history

`offsets` keeps the current and previous offset per player. A packet written before a window change can be read
after it, so inbound conversion tries both frames and picks the one that puts the resolved position nearest the
player (§8.2).

## 8. Packets

Everything is per player and runs before the shared `modified` cache, because a broadcast packet is one object
handed to every tracking player.

### 8.1 Outbound

| Packet | What happens |
|---|---|
| `ClientboundLevelChunkWithLightPacket` | **Replaced** by the composition. Never forwarded. |
| `ClientboundLoginPacket` / `ClientboundRespawnPacket` | Spawn info rewritten: dimension **type** → `silvicky:abyss_window`, dimension **key** → `abyss:window`. |
| `ClientboundRespawnPacket` during a seam | **Cancelled.** A seam is a teleport inside one dimension; vanilla's respawn rebuilds `LocalPlayer` and flashes the loading screen even for an unchanged dimension. |
| `ClientboundBlockUpdatePacket`, `ClientboundSectionBlocksUpdatePacket`, `ClientboundLevelEventPacket`, generic `BlockPos` loop, bundle sub-packets | Position/section shifted by the band offset. |
| `ClientboundPlayerPositionPacket` | Absolute Y shifted by the band offset; relative Y untouched. |
| `ClientboundLightUpdatePacket` | Masks shifted by `slot * SECTIONS_PER_BAND`. |
| `ClientboundSoundPacket`, `ClientboundLevelParticlesPacket` | X/Z through the shuffle, Y shifted by the band offset. |
| `ClientboundExplodePacket`, `ClientboundDamageEventPacket`, `ClientboundPlayerLookAtPacket` | Position shifted. |
| `ClientboundTrackedWaypointPacket` | Block-position waypoints shifted. |
| `ClientboundSetChunkCacheCenterPacket`, chunk forget/biomes/debug | Chunk coordinates only — the shuffle, never the band. |
| Entity packets (`AddEntity`, move, teleport, …) | X/Z only, via `EntityPositionRemapper`. **Known gap:** Y is not shifted, so an entity in another band is a whole band off. Deltas are correct because they cancel. |

Attribute packets (stats, health, …) never carry a coordinate and are untouched.

### 8.2 Inbound

`AbyssWindow.toServerY` resolves the frame:

```
for each candidate offset (current, previous):
    value = clientY - offset
    keep the one with the smallest |value - player.getY()|
```

Reach is ~6 blocks and a frame difference is a band, so the choice is unambiguous. Every inbound position —
move, vehicle, accept-teleport, interact-entity, use-item-on (block *and* location), break/place, generic
`BlockPos` loop — goes through it.

### 8.3 Movement accounting

The server's Y jumps a band at a crossing; the player's does not. Two places had to be corrected:

- `movedDist` (`handlePlayerPositionChange`, both ordinal sites) → `AbyssWindow.movementDistanceSq`, horizontal
  from the shuffle, vertical as the real delta;
- `checkMovementStatistics` → `AbyssWindow.playerMove`, same split.

Without these a remote client reports "moved too quickly" at every seam and the correction loop begins. The
local client does not run the same path, which is why it only shows up over a real connection.

## 9. The demos

All three are registered in code and written to `config/AbyssPatterns.json` on first start.

### 9.1 `abyss:stack` — five layers, acyclic

`abyss:layer0..4`, band height 64, `sourceY 0`. Each layer has its own palette (floor, shell, pillar, light) and
its own pillar pattern: corner pillar, cross, forest, ring, obelisk. A 7×7-chunk shaft at the origin, produced by
`AbyssDecayRule`, runs through every layer, so the stack is travelled by falling.

Closed floors and ceilings: the stack has a bottom (layer 0) and a top (layer 4).

### 9.2 `abyss:cycle` — the same five layers, cyclic

`abyss_cycle:layer0..4`, cyclic, so band 4 is followed by band 0 forever.

Separate levels from the stack, because the wrap is built into the geometry:

- layer 4 (`wrapOpen`) replaces part of its ceiling with a **slatted landing** — slabs on a checkerboard at
  `BAND_HEIGHT - 4`. It catches a player arriving from below, and the gaps let a falling player keep going, so
  the wrap is not a one-way valve.
- layer 0 already has its floor shaft and a solid ceiling, which is correct: you arrive at the *bottom* of layer 4
  from the *top* of layer 0, and leave the wrap by dropping back down the same shaft.

### 9.3 `backrooms:spiral` — one level, cyclic

`backrooms:level922`, a single band, cyclic. This is the cheapest possible configuration: all three slots show
the same level, so the composition is band-invariant and **a crossing re-sends nothing at all**.

`SpiralRule` builds a helix with two turns per band and 32 blocks of rise per turn, so every column carries a
step at `h` and one at `h + 32`, and the whole column repeats every 64 blocks. Steps alternate bottom slab
(`y`, surface `y + 0.5`) and full block (`y`, surface `y + 1.0`) — a 0.5 rise per step, which the player
auto-steps. Solid wall at radius 11 with a lantern every quarter turn; open well at radius ≤ 2 so a player can
drop through; slatted landings at both ends of the wrap arc.

## 10. Configuration

`config/AbyssPatterns.json`, read once at `SERVER_STARTING`:

```json
[
  {
    "name": "abyss:stack",
    "band_height": 64,
    "cyclic": false,
    "bands": [
      { "level": "abyss:layer0", "source_y": 0 },
      { "level": "abyss:layer1", "source_y": 0 }
    ]
  }
]
```

- The file is written from the registered patterns **only when it does not exist**. Editing it requires deleting
  it first, or writing the whole array yourself.
- `name` is the key `AbyssPatterns.forLevel` searches by; the first registered pattern containing the player's
  level wins, so order is meaningful.
- A malformed file is logged and ignored; the built-in patterns stay registered.
- `source_y` defaults to 0 if omitted.

## 11. Levels

The demo levels are staged by a `StagedDimensions` provider: they are added to `world_gen_settings` at save time,
using the registry access the save is encoded with, and become real levels on the next start. (Staging them at
`SERVER_STARTED` fails — a `Holder` belongs to one registry instance, and the save is encoded against another.)

| Dimension type | Height | Used by |
|---|---|---|
| `silvicky:abyss_layer` | 64, `min_y 0` | every band level |
| `silvicky:abyss_window` | 192, `min_y 0` | what the client is told |

Both are ceilinged, skylight-less, `ambient_light 0.1` with the nether's visual attribute set
(`cardinal_light: nether`, `sky_light_level 4.0`, nether fog distances and colours), fixed time, no monsters.

## 12. Constraints and known gaps

**Hard constraints**

- **One band height per world.** The client's level shape comes from a single dimension type at login. A different
  `band_height` needs a different `silvicky:abyss_window*` type plus a way to pick it — the pattern already
  carries the value, nothing else does.
- **`sourceY` in sections.** Mid-section slices would have to be reconstructed block by block.
- **2 MiB per chunk.** `ClientboundLevelChunkPacketData`'s buffer is capped; a 12-section window is far below it,
  a 192-section one was uncomfortably close.

**Known gaps**

- **Upward crossing threshold.** `travel` fires on `local >= BAND_HEIGHT`. A 64-tall level caps block placement at
  y 63, so this triggers only when the player's feet reach y ≥ 64 — which happens when they stand on the highest
  block (e.g. on top of a closed layer's ceiling). Walking up a ramp inside a band never crosses. Making it a
  real "top of band" threshold, with a matching `targetY` that lands on the next band's floor, is a design
  decision that has not been made.
- **Block entities** are not composed. `BlockEntityInfo` is package-private with a private constructor; the demo
  layers contain none.
- **Entity Y** is not translated on the wire (`EntityPositionRemapper` handles X/Z only), so an entity in another
  band is mislocated. Deltas are correct.
- **Biome republish** (`ClientboundChunksBiomesPacket`) is not composed and would overwrite a window column.
- **Tickets are shared** between players whose windows overlap (see §7.1).
- **Light that arrives late** is not re-sent (see §6.3).
- **Simulation stops at a band boundary.** Only the player's own band ticks; the bands visible above and below
  are loaded but not simulated. That is intended for scenery, and wrong if a band must keep running while
  unoccupied.

## 13. Failure modes and what they looked like

| Symptom | Cause |
|---|---|
| "Adjacent layer not rendered, refreshes when I approach" | `PLAYER_LOADING` tickets were `ENDER_PEARL` (40-tick timeout); the bands unloaded when the player stood still. |
| Same, intermittently | `CHUNK_LOAD` handler mutating `PlayerChunkSender.pendingChunks` off the server thread. |
| "Cannot see the layer above until I approach it" | Crossing kept `windowStart`, so the player sat in slot 3 — outside the rendered window. |
| "World slides the wrong way at a seam" | Recentring and a chunk re-send of 289 columns, with the position packet racing the resend. |
| "Moved too quickly" on remote clients only | Server Y jumps a band; `movedDist` measured in server space. |
| Registry error on save | Holders staged at `SERVER_STARTED` instead of resolved against the save's registry access. |
| `min_y + height cannot be higher than 2032` | A 3072-tall window type; `DimensionType` caps `min_y + height`, so tall windows must start below 0. |

## 14. File map

```
core/src/main/java/io/silvicky/item/backrooms/
  AbyssPattern.java          bands, patterns, indexOf, validation
  AbyssPatterns.java         registry + config/AbyssPatterns.json
  AbyssWindow.java           state, tick, travel, offsets, tickets, resend
  AbyssChunkComposer.java    sections, heightmaps, light → one packet

core/src/main/java/io/silvicky/item/mixin/
  ServerCommonPacketListenerImplMixin   outbound translation, composition, login/respawn
  ConnectionMixin                       inbound translation
  ServerGamePacketListenerImplMixin     movement distance and statistics
  ServerPlayerMixin                     window state cleanup on respawn/disconnect

backrooms/src/main/java/io/silvicky/item_br/worldgen/abyss/
  AbyssWorlds.java           patterns + staged levels
  AbyssLayerRule.java        the five stack layers (and their cycle variants)
  SpiralRule.java            level 922, 64-periodic helix
  AbyssDecayRule.java        the shaft

backrooms/src/main/resources/data/silvicky/dimension_type/
  abyss_layer.json           64-tall band
  abyss_window.json          192-tall window
```
