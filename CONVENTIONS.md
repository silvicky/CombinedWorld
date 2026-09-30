# Coordinate conventions

This file is the answer to a question that has cost the most time in this project: **when a hook receives a
position, is that position in the server's world or in the view the client renders?**

That distinction is not expressible in the type system — every position is a `Vec3` or a `BlockPos`
either way — so it was being carried in someone's head, and every time it was lost a position got converted
twice. Most of the bugs in this mod's history came from that single ambiguity.

**Everything in this file is a convention, not a fact the compiler checks.** When you change something that
touches a position, check this file afterwards and correct it. A wrong line here is worse than a missing
one, because it will be trusted.

---

## The two fields

| | meaning | where it exists |
|---|---|---|
| **S** (server) | the world the server simulates | everywhere on the server: entity positions, chunk positions given to worldgen, block lookups, collision AABBs, the keys of `s2cMap` / `c2sMap` |
| **C** (client) | the view the client renders | only on the wire, and inside the chunk-tracking remap |

A C position must never be used for a world lookup. A position arriving on a packet is C until something
converts it; a position read out of the server's own state is already S.

## The rule

**Conversion happens at exactly two boundaries, once per direction.**

| boundary | class | direction |
|---|---|---|
| packets in | `ConnectionMixin.genericsFtw` | C → S |
| packets out | `ServerCommonPacketListenerImplMixin.send` | S → C |

Nothing that runs *after* a packet has been decoded may convert again. By then the value is S.

The practical test for any site: **could this value have arrived on a packet?**

- yes → it may still be C, and converting is legitimate
- no, it came from the server's own state (an entity, a chunk map, a computed `BlockPos`) → it is S, and
  converting is a **double transform**

## Markers in the code

Every transform site carries one of these, so the whole surface can be grepped:

| marker | meaning |
|---|---|
| `[S]` | this value is server world coordinates — do **not** transform it again |
| `[C]` | this value is client view coordinates |
| `[S>C]` | converts server to client |
| `[C>S]` | converts client to server |

```sh
rg '\[S>C\]|\[C>S\]|\[S\]|\[C\]'          # audit the whole surface
```

A conversion applied to a value marked `[S]` is a double transform — **unless** the value it is compared
against is in the other field, in which case the conversion is what makes them comparable. That exception
is real and has bitten us; see the collision section.

---

## The trap: two operands, two fields

This is the mistake that cost the most, because it looks correct.

A comparison is only meaningful if both operands are in the same field. It is wrong to convert only one,
and it is equally wrong to convert each operand by *its own* mapping. Two positions in different chunks
can have completely different offsets, so converting each separately means comparing a wall displaced by
`O_a` against a player displaced by `O_b`. The result is correct for neither.

**Convert once, apply that one vector to every operand.** A single rigid translation preserves relative
geometry exactly, which is what makes the comparison meaningful.

```java
// wrong: each operand mapped on its own
box.move(s2c(box) - box).intersects(shape.move(s2c(shape) - shape))

// right: one offset, applied to everything
Vec3 offset = s2c(boxCentre) - boxCentre;
box.move(offset).intersects(shape.move(offset))
```

### Why `isometry` matters

Mapping two points and subtracting the results is only the same as subtracting them in server space if the
transform is affine on the segment between them. `NopTransformer` (identity) and `LinearTransformer`
(constant offset) satisfy this, so those transforms are harmless no-ops in that position. `XorTransformer`
and `RandomTransformer` do **not**:

- **XOR** folds space on tile boundaries, and it folds at *chunk* granularity while
  `VecTransformer.s2cTransform(Vec3)` maps a block by mapping its chunk and keeping the in-chunk offset.
  The result is a reflection on chunks that is not a reflection on blocks: adjacent chunks get swapped
  while their offsets are preserved, so chunk order reverses but block order does not. A 3-block step
  measures as 29.
- **RANDOM** gives every chunk an independent offset, so the error is unbounded. Measured with
  `IsometryTest`: **RANDOM corrupts movement distance by up to 60,385×**, and XOR by up to 170×.

`IsometryTest` asserts this. Run it before trusting any arithmetic that maps two points and subtracts.

---

## The trap: a collage is not a world

`RandomTransformer` assigns each client chunk an **independent** server chunk. What the client draws is
therefore a collage: chunk A's contents at client position `p_A`, chunk B's at `p_B`, where A and B are
unrelated places in the world.

There is **no rigid motion** carrying A's contents to `p_A` and B's to `p_B`, because those contents have
no relationship to each other. So collision across a chunk seam is geometrically meaningless under RANDOM,
and no coordinate transform can fix it. Mapping the player into the view is arithmetically correct and
still lets a player be stopped by geometry belonging to a chunk two or more away.

This is a property of the design, not a bug to be fixed. The ways out, in order of how much randomness they
keep:

| option | randomness | collision |
|---|---|---|
| per-chunk random | most | incorrect at every seam |
| per-chunk random with bounded jitter | some | visible ledges at seams |
| per-region translation | more | correct except at region borders |
| single global translation | least (`p → p + T`) | always correct |

A same-chunk box is fine: every relevant block shares that chunk's offset. A box straddling a boundary
collects shapes carrying two different offsets, which is the incoherent case.

---

## The boundary, in detail

`ConnectionMixin.genericsFtw` runs on the **netty** thread, before the packet reaches any handler. It
rewrites the packet's own fields. After that returns, everything downstream sees S.

Handled explicitly: `ServerboundUseItemOnPacket`, `ServerboundMovePlayerPacket`,
`ServerboundMoveVehiclePacket`, `ServerboundAcceptTeleportationPacket`, `ServerboundInteractPacket`. Every
other packet with a `BlockPos` field goes through a reflective sweep — see the site tag
`c2s/reflect:<PacketClass>` in the audit, which is how we find out which ones actually reach it.

`ServerCommonPacketListenerImplMixin.send` is the mirror image: the packet is S on the way in and C on the
way out.

## Sites that are NOT bugs

Recorded so nobody re-litigates them.

- **`TrackedEntityMixin.updatePlayer`** — the delta goes on the wire, so it must be in the view's field.
  Both operands are the server's own state, so both are converted. The local it overwrites is irrelevant
  because the whole value is replaced. `[S>C]` is correct.
- **`CollisionGetterMixin` / `PlayerMixin`** — both operands of a distance are converted, so the distance is
  unchanged. Converting only one would corrupt the test. `[S>C]` on both.
- **`BlockCollisionsMixin.inject1`/`inject2`** — the lookup walks client-view coordinates and converts each
  back with the *single* offset from the constructor. That is the one place conversion belongs.

## Sites that WERE bugs

Recorded because the shape recurs.

- **`ServerGamePacketListenerImplMixin` movement hooks** — `handleUseItemOn`'s reach, both `movedDist`
  stores, and the movement-statistics redirect all mapped two S-field points and subtracted. Confirmed by
  measurement: 375/375 samples equalled the value `ConnectionMixin` had already converted.
- **`EntityMixin`** (deleted) — `move` and `isInWall` wrapped the entity's own position in an
  `s2c`→`c2s` round trip, which is the identity for any bijective map. It also targeted locals
  (`checkTo`, `checkDistance`) that no longer exist in 26.3's `move()`.
- **`BlockCollisionsMixin.inject4`** — rewrote an argument to an *internal* emptiness test. The shape handed
  back to the caller is unaffected, so the only effect was to make the AND test compare two unrelated
  positions. Now an identity.
- **`ChunkRemap.forEach` for MAPPING** — walks the transformer's table with the border ring included. Do
  not trim it: the ring is in-flight margin, so the player never walks into a chunk the server has not
  sent yet.

---

## Audit

`TransformAudit` measures the convention at runtime rather than trusting the comments, because reading the
call graph has repeatedly been wrong. It records the raw client coordinate, the server coordinate it
converts to, and the player's own server position, then classifies every site:

| verdict | meaning |
|---|---|
| `double` | input is already S — converting is a double transform |
| `server_state` | came from the entity's own position, so definitionally S |
| `single` | input is still C — converting is correct |
| `unknown` | not derivable from the movement packet; read the call graph |

A site reporting only `double`/`server_state` **whose counterpart is also S** should have its transform
removed. A site reporting only `single` is doing its job. `isEntityCollidingWithAnythingNew/aabb` reports
`double`/`server_state` and is nonetheless correct, because its block shapes are C — that is the collage
case above, not a bug.

`/spark profiler` output, for reference: under a pregeneration stress test the mod is **0.06%** of sampled
CPU, largest heap class 1.5 MB. If a problem is in this mod, it will not show up as CPU time.

---

## Open questions

These are not settled. They are written down so they are not silently decided by whoever touches them last.

1. **What is `randomChunkRange` supposed to bound?** Scattered distance, or total pool size? The code treats
   it as a floor under a computed window (`max(randomChunkRange, viewDistance + 4)`). If it was meant as a
   hard cap on scatter, that is a semantic change nobody asked for.
2. **Is the collage intentional?** Should the client see one region of the world, or a shuffled patchwork?
   This decides whether per-chunk randomness is compatible with collision at all.
3. **Is `getPreMoveCollisions`' entity-shape branch in scope?** It never passes through
   `BlockCollisionsMixin`, and has been flagged repeatedly without anyone saying whether it matters.
