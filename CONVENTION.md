# How the coordinate shuffle works

**Who this is for:** anyone. You do not need to know how Minecraft works to read Part 1. If you *do* write
code for this mod, Part 2 has the details, and Part 3 lists the things we are still arguing about.

**What this mod does:** the world is real, but each player looks at a *shuffled copy* of it. Nothing about
the real world changes. What changes is which part of the world each player is shown, and 闂?this is the
important part 闂?**the rules of the game are judged on the shuffled copy, not the real one.**

---

# Part 1 闂?Plain language

## 1.1 Two maps of the same island

Imagine a treasure island. There are two maps of it:

- **The real map (S).** The island is genuinely there. The server knows exactly where the gold is, where the
  water is, where the walls are. This map never lies and never moves.
- **Your map (C).** Every player gets their own copy, and the copy is shuffled around. On your map the
  treasure might be at (100, 200) even though it is really at (900, 200).

The server still owns the real island. Blocks still exist where they really are. But the *player* is looking
at a shuffled map, and so is the server whenever it checks something about that player.

## 1.2 The map is shuffled one page at a time

The island is split into pages, and one page is 16闂?6 blocks. We call a page a **chunk**.

**Each page is shuffled on its own.** Page 4 might be moved 500 blocks east while page 3 stays exactly
where it was.

This is the single most important thing to understand, because it causes everything that feels odd. Two
blocks that are neighbours on the real map are **not** necessarily neighbours on your map. You can be
standing right next to a wall on your map, and the "wall" next to you is a piece of land from a completely
different part of the world.

Nobody can change this. It is how the shuffle is meant to work.

## 1.2.1 Why the shuffle is invisible in front of you and changes behind you

This is the whole point of the shuffle, and it is worth saying plainly.

**The world in front of you looks normal and stays normal.** As you walk, the server keeps filling in the
pages you are looking at, so there is always ground ahead of you. You do not see flicker, gaps, or the
seams between pages. Walking forward feels like walking through an ordinary world.

**The world behind you is not like that.** You cannot backtrack, and the way you find out is by turning
around. Once you cross a page border, the pages behind you are given *different* pages of the world. You
walk away from a corridor, turn round, and the corridor is not there any more - there is a stretch of
world you have never seen instead. Walk forward again and it is different again, and it will be different
every time. That is what makes backtracking impossible: you can never retrace your steps, because the steps
you took no longer lead anywhere.

There is no hole and no flicker. When a page behind you changes, the new terrain simply takes the old
terrain's place, so it looks like the world carried on being generated while your back was turned. That is
the Backrooms effect, and it is deliberately unsettling rather than obviously broken: nothing ever flickers
or vanishes in front of you, so the only evidence that anything happened is that the place you just left
is somewhere else now.

## 1.3 The rule that matters most: the game is judged on *your* map

This is the part that surprises people, including us at first.

When the server asks "is that allowed?", it does not use the real map. It uses **your** map.

Say you swing your pickaxe. The server does not check "did they reach the ore on the real map?" It checks
"did they reach the ore on *your* map?" 闂?the same one you were looking at when you decided to swing.

Same for everything else:

| The server is checking闂?| 闂傚倸鍊搁崐鎼佸磹閻戣姤鍤勯柛顐ｆ礀绾惧鏌曟繛鐐珔缁炬儳娼￠弻娑樷攽閸℃浠遍梺鍝勵儐閻楁濡甸崟顖ｆ晜闁糕檧鏅滈埛?it checks it on |
|---|---|
| How far you walked | your map |
| How far you can reach to mine or place a block | your map |
| Whether you walked into a wall | your map |
| Whether you are standing near a mob | your map |

So the shuffle is not just a picture shown to you. It is the frame of reference the whole game is played
in, for you.

**The catch:** because pages move independently, a distance measured on your map is not the same as the
distance you actually walked. Walk across a page boundary and the shuffle can make it look like you moved a
very long way. This is expected. It is why walking sometimes feels strange at page edges.

## 1.4 The four kinds of shuffle

Players can be given different kinds of shuffle. A server owner picks one per world.

| Name | What it does | Simple picture |
|---|---|---|
| `nop` | No shuffle at all | The map is an exact copy |
| `linear` | Slides the whole map by a fixed amount | The map is slid sideways, all at once |
| `random` | Shuffles each page to a random place | Every page is thrown somewhere random |
| `xor` | Scrambles the page numbers with a bit trick | Pages swap around in a fixed pattern |

`linear` keeps the island recognisable 闂?it is the same island, just moved. `random` is the most dramatic
and the hardest to play, because neighbours stop being neighbours.

## 1.5 When does a player get a new shuffle?

A player gets a **brand new shuffle** when they arrive somewhere the map has no answer for 闂?a world they
have not been placed in before, or a place their current shuffle does not cover.

They do **not** get a new shuffle just because they were teleported. Landing somewhere that is already on
their map is a correction or a rescue, not a new arrival, and the map they already have is the right one to
keep. See 闂?.6.2.

The reason to shuffle at all is fairness: if a player got the same shuffle twice, they could remember it. They
would start to work out how the real map and the shuffled map line up 闂?and then they would know where the
gold is in the *real* world, which is the thing the shuffle is meant to hide.

> **This is why where a page gets placed must be random, not just "somewhere free".** A page finder that
> always scans its candidates in the same order will hand out the same corner slot every time, so a
> reshuffled world would put every player's page in a predictable spot 闂?and a player could work backwards
> from where their own page went to where everything else went. This is not hypothetical: a version of
> `RandomTransformer` dropped the shuffle, and a playtest log caught it immediately because two fresh
> shuffles both landed the player on exactly `realChunk + (-7, -7)`. The candidates are shuffled with a
> per-player `Random` before the free slot is picked. See 闂?.4.1.

---

# Part 2 闂?The technical detail

Everything below assumes you have read Part 1. Names in `backticks` are real class, method or field names in
the code.

## 2.1 The two coordinate systems, formally

- **S (server).** A position in the real world. Everything the server simulates, saves, or looks up lives
  here. For example: `player.position()`, `player.chunkPosition()`, the fields `firstGoodX/Y/Z` and
  `lastGoodX/Y/Z` (vanilla's own record of where the player is), the keys of the `s2cMap` and `c2sMap`
  tables, and the saved positions in `StateSaver.posMap`.

  Anything that does not belong to a player 闂?blocks, items, mobs, chunks 闂?is always S. The server runs
  those consistently, exactly as vanilla does, with nothing from the players interfering.

- **C (client).** A position on the player's shuffled map.

  > The design intent, in the author's words: *"the convention maximize the consistency in the C-space, that
  > is, not only the player sees itself as in C-space, but also the server uses mapping to simulate C-space
  > 闂?server knows real S-space positions, but it pretends everything to be in C-space when parsing gameplay
  > stuff, and allows or bans players to do things according to their C-space, not S-space."*
  >
  > *"Obviously, a real consistency is not possible. This is intended."*

**So the rule is:** a site that converts a position into C-space *before making a gameplay decision* is doing
the right thing 闂?even though the value it read was S. The vanilla answer is not automatically the correct
one; it is the S-space answer to a question that is supposed to be asked in C-space.

## 2.2 How a block position is converted

`VecTransformer` (in `core/.../backrooms/VecTransformer.java`) defines the conversion once, for chunk
positions. Everything else is derived from it.

The rule: **find which page (chunk) this position is on, move that page, and keep the position within the
page exactly where it was.**

```java
// the chunk-level version, which each transformer implements
public abstract ChunkPos s2cTransform(ChunkPos pos);

// the block-level version, derived from the above
public BlockPos s2cTransform(BlockPos pos) {
    ChunkPos chunkPos = ChunkPos.containing(pos);
    ChunkPos mapped  = s2cTransform(chunkPos);
    return pos.offset(mapped.getWorldPosition())
              .subtract(chunkPos.getWorldPosition());
}
```

So a block at the bottom-left corner of its page stays at the bottom-left corner of the page after the
page moves. The offset inside the page never changes. Only the page moves.

**Why this matters:** it means the conversion is a *step function*. Inside a page it is one value; the
instant you cross into the next page it jumps to a completely different value. There is no smooth
transition, and there cannot be one 闂?that is what "each page moves independently" means in maths.

Both directions exist: `s2cTransform` (S 闂?C) and `c2sTransform` (C 闂?S).

## 2.3 Where S and C meet on the wire

A position changes field in exactly **two** places, and both are packet boundaries.

**`ConnectionMixin.genericsFtw` 闂?C 闂?S.** Runs on the *netty* thread (the network thread), before any
game handler sees the packet. It rewrites the packet's own fields. Named packets handled directly:

| Packet | What is converted |
|---|---|
| `ServerboundUseItemOnPacket` | the block clicked, and the exact hit point |
| `ServerboundMovePlayerPacket` | `x` and `z` (only when the packet carries a position) |
| `ServerboundMoveVehiclePacket` | the position |
| `ServerboundAcceptTeleportationPacket` | `x`, `y`, `z` |
| `ServerboundInteractPacket` | the target location |

Anything else with a `BlockPos` field is caught by a **reflective sweep** 闂?the code walks the packet's
fields and converts any `BlockPos` it finds. `ServerboundSetStructureBlockPacket` is deliberately skipped,
with a note that it has two positions.

A `modified` set (`WeakHashMap`) makes sure each packet object is converted at most once.

**`ServerCommonPacketListenerImplMixin.send` 闂?S 闂?C.** Runs on the *server* thread, on the way out. It has
a long explicit list of outgoing packet types, then the same reflective sweep.
`ClientboundForgetLevelChunkPacket` is skipped because `ChunkTrackingViewMixin` handles it.

If a position cannot be converted 闂?`ChunkUnusedException` 闂?**the packet is thrown away and the send is
cancelled**, printing `Packet discarded: 闂傚倸鍊搁崐鎼佸磹閻戣姤鍤勯柛顐ｆ礀绾惧鏌曟繛鐐珔缁炬儳娼￠弻娑樷攽閸℃浠遍梺? The player simply does not receive it.

**Everything else in this mod is not a field change.** It is an S value being moved into C for the duration
of a decision, then mapped back before the world is touched. That is the pattern, and it is not a bug.

## 2.4 The four transformers

Held per player, in `VecTransformer.instances`, keyed by the `ServerPlayer`.
`VecTransformer.getInstance` reads `StateSaver.chunkTransformer` for the player's dimension to decide which
class to build. If anything throws, it falls back to `nop`.

| Name | `s2cTransform` | `tick()` | `getRemap()` | Notes |
|---|---|---|---|---|
| `nop` | returns the position unchanged | does nothing | `IDENTITY` | `getS2c()` returns `null` 闂?no table is built at all |
| `linear` | `pos + (xOffset, zOffset)` | does nothing | `TRANSLATION` | Offsets drawn from `linearChunkRange` |
| `xor` | `x ^ xXor`, `z ^ zXor` | adds loading tickets | `MAPPING` | Builds a real table in `getS2c()` |
| `random` | table lookup in `s2cMap` | rebuilds the table, adds tickets | `MAPPING` | Throws `ChunkUnusedException` on a miss |

`random` is the only one that can **fail to convert**: if the chunk is not in the table it throws. `nop`,
`linear` and `xor` always succeed.

Returning `null` from `getS2c()` is safe only because nothing dereferences it for `nop`. The one caller that
does 闂?`XorTransformer.addLoadingTicket`, which iterates `getS2c().keySet()` 闂?sits on a class that overrides
the method. If you ever add a caller, give it `getRemap()` instead.

`random` maintains the table in `onChunkPosChanged`, which runs when the player crosses into a new chunk.
It keeps a square of pages of side `viewDistance + 2` around the player's position, dropping pages that fall
outside or that the player cannot see, and adding pages that are newly needed.

### 2.4.1 `random`: the placement pool

`random` puts a page somewhere in a pool of free server positions centred on the player's own chunk. Two
rules govern that pool, and both were arrived at the hard way.

**The pool must be at least as large as the view it feeds.** The view needs `(2*(viewDistance+2)+1)^2`
client positions. The pool used to be a flat `闂傚倸鍊搁崐椋庣矆娓氣偓楠炲鏁撻悩顔瑰亾閸愵厾鐤€闁圭偓鍔х徊浠嬧€﹂崘宸桨闁汇儲鐛ChunkRange` 闂?225 at the default of 7 闂?which happens to
be enough at `viewDistance` 4 (169) and is *not* enough at 6 (289). Past that point the allocator started
overwriting live entries, which broke the correspondence between `s2cMap` and `c2sMap` and cascaded from
there. The radius is now pinned to `max(randomChunkRange, viewDistance+2)`, so a server owner who sets
`randomChunkRange` too low gets it ignored rather than a broken world.

**The free slot must be picked at random.** Scanning candidates in a fixed order always returns the same
corner, which makes the whole shuffle predictable and defeats the point of reshuffling (闂?.5). The
candidates are shuffled with a `Random` owned by the transformer instance 闂?not the shared static one in
`VecTransformer`, which is for drawing offsets and would have handed two players the same shuffle.

**A dry pool is reported, never guessed at.** If every candidate is taken, `request` counts it in `dry=`,
logs it, and leaves that page unmapped rather than reusing an occupied slot. The old fallback returned a
hardcoded position, which overwrote whatever lived there. A hole in the view is recoverable; a corrupted
`s2cMap`/`c2sMap` pair is not. `dry=` should stay `0` in the diagnostic.
### 2.4.2 The visibility rule: what is kept and what is dropped

`onChunkPosChanged` runs on every chunk border crossing and does two things: drop what should no longer
exist, then add what should. **Both ask the same question**, through one method, `retained`:

```java
private boolean retained(ChunkPos c, ChunkPos newC, Vec3 eyeC)
{
    int distance = Util.chunkPosDistance(c, newC);
    if (distance <= frustumExemptRadius) return true;   // the 3x3 you stand in
    if (distance > viewDistance + 2)            return false;  // outside the view
    if (eyeC == null)                            return true;   // unknown facing hides nothing
    return Util.isVisible(eyeC, player.getYRot(), c);          // in front?
}
```

Kept means mapped, sent to the client, and **visible**. Dropped means unmapped and never sent, so there is
nothing there to see. That is the entire visibility mechanism 閳?there is no fog and no hiding packet.

**This was broken for as long as the frustum existed, and the symptom was that the shuffle was completely
invisible.** The prune dropped the pages behind the player, then the request loop asked for the entire
square regardless and put every one of them straight back, so `isVisible` decided nothing. The map was
always the full `(2*(viewDistance+2)+1)^2` entries, and a playtest log showed `map=169` on every line
regardless of which way the player faced.

So the two halves must never disagree, which is why there is one method rather than two tests. **If you
change one, change it here.** `map=` in the diagnostic is the confirmation: it should now sit well below
169 and change as the player turns.

#### The 3x3 exemption, and why it is not tunable

`frustumExemptRadius` is 1, and must stay 1. When the player walks from page A into page B their bounding
box straddles both, and if A were dropped the collision lookups reaching back into it would fail to
convert 閳?the player collides with newly-mapped terrain instead of walking into it. Verified many times to
be necessary.

The cost is that this 3x3 is always visible: mapped means sent, and sent means seen. That is the trade 閳?
correct collision, paid for with a visible 3x3 patch at the player's feet. Anything that wants the shuffle
to look more invisible has to work around that, not by shrinking this number.

#### Re-filling happens on border crossings, not on turning

One consequence worth knowing: `retained` is only consulted when the player **crosses a page border**, not
when they turn. So turning around to look behind shows empty space until the next border crossing, and it
is that crossing which fills it 閳?with different pages than last time. That is 鎼?.2.1 working as intended.
If the area behind should instead reappear on rotation, the trigger has to move from `updateChunkPos`
into `tick`.

`VecTransformer.getRandom(range)` returns a value in `[-range, +range]`.

## 2.5 Collision

Collision is where the shuffle is applied most, because the server must test the player against the blocks
**the player can see** 闂?which are on the C map, not the S map.

**`BlockCollisionsMixin`** (in `core/.../mixin/`):

| inject | Target | What it does |
|---|---|---|
| `inject1` | `getChunk(II)` inside `computeNext` | Converts the page coordinate C 闂?S before the chunk is fetched |
| `inject2` | `BlockPos.set(III)` | Converts the block position C 闂?S |
| `inject3` | the constructor | Moves the query's AABB S 闂?C, so the scan happens in the player's view |
| `inject4` | `joinIsNotEmpty` inside `computeNext` | Moves that shape S 闂?C |

**`CollisionGetterMixin`** redirects `distToCenterSqr` in `findSupportingBlock`, converting **both**
operands.

**`ServerGamePacketListenerImplMixin.inject7` / `inject8`** move the player's AABB and the block shape S 闂?C
during `isEntityCollidingWithAnythingNew`.

**`EntityMixin.inject3`** moves the box S 闂?C for `isInWall`, scans in C, then converts every candidate block
back C 闂?S before the actual world lookup.

**`LevelMixin.inject1`** replaces `getEntities` completely: moves the query AABB S 闂?C, walks sections in C,
converts each `SectionPos` C 闂?S to reach `sectionStorage`, and moves each candidate's bounding box S 闂?C
before intersecting.

**`PlayerMixin.inject1` / `inject2`** move the eye position and the block position S 闂?C in
`isWithinBlockInteractionRange`, so the reach test compares two C-space points.

> The author's note: *"Collision is quite troublesome since player can cross chunk, maybe I have solved it
> without dropping it, but all in all quite hard."*

## 2.6 Movement

`ServerGamePacketListenerImplMixin` 闂?the most involved file. It runs *after* `ConnectionMixin` has already
converted the packet, which is correct: the questions it asks are C-space questions.

| inject | Target | What it does |
|---|---|---|
| `inject1` | `handleUseItemOn`, `Vec3.subtract` | Reach test. Converts both operands. On failure returns `VecTransformer.INF` |
| `inject3` | `handlePlayerPositionChange`, `movedDist` store 0 | `playerMove(firstGood*, target)`, squared |
| `inject4` | `handlePlayerPositionChange`, `movedDist` store 1 | `playerMove(player.position(), target)`, squared |
| `inject5` | `teleport`, after `teleportSetPosition` | Keeps the shuffle if the destination is mapped, initialises one if not (闂?.6.2). Also logs, see 闂?.6.3 |
| `inject6` | `handlePlayerPositionChange`, argument to `ServerPlayer.move` | Converts target and `lastGood*` |
| `inject7` | `isEntityCollidingWithAnythingNew`, argument to `Shapes.create` | Moves the player AABB S 闂?C |
| `inject8` | same method, argument to `joinIsNotEmpty` | Moves the block shape S 闂?C |
| `inject9` | `handlePlayerPositionChange`, `checkMovementStatistics` | Converts `position()` and `start*` |

`inject3` and `inject4` all four delegate to `VecTransformer.playerMove`, which owns the C-space
measurement **and** the one exception to it. Read 闂?.6.1 before changing either 闂?this is where the
reported void-teleport loop lived.

`inject3`, `inject4` and `inject9` read `firstGoodX/Y/Z` or `lastGoodX/Y/Z`, which vanilla keeps as the
server's own record of the player. Those are S values. Converting them into C is what makes the measured
distance the distance **the player** moved 闂?which is the distance the rule is about.

**On the `//what is this??` line**, which used to zero the vertical part of the difference when the player is
nearly level: it is gone. `inject4` no longer inlines the arithmetic; it calls
`VecTransformer.movementDistanceSq`, which uses the full 3D difference. See 闂?.6.1.

> Copied from vanilla. The author's words when it was there: *"It's a fucking weird piece of code in the game
> itself, and I honestly copied it, maybe later I should check this shit, it is clearly wrong!"*

**On the `1e9` returns:** `movedDist` is a squared distance, so `1e9` reads as "the player moved 31,623
blocks this tick". Vanilla reacts by logging "moved wrongly" and snapping the player back to the server
position. This is intentional 闂?it avoids a crash 闂?but a better approach may replace it.
`movementDistanceSq` still returns it, for the same reason, when a chunk cannot be converted at all.

### 2.6.1 Measuring movement: the C displacement, unchanged

**The player's movement is the C-space displacement between the two positions. Nothing is subtracted from it.**

This section exists because an earlier version of this document insisted otherwise, the code followed, and
it broke walking on every chunk border. Read it before touching `playerMove`.

#### Why nothing is subtracted

Work it through for a single border crossing. The client is at `x=15.9` in its own chunk `Q`, and steps to
`x=16.2`, which is inside chunk `Q+1`. Let `A` be the server chunk whose image is `Q`, and `B` the one whose
image is `Q+1`. Under `random` those can be six chunks apart on the real map.

```
measured in C:  (Q+1)*16 + 0.2  -  (Q*16 + 15.9)  =  0.3 blocks
```

That is the step the player took, and it stays 0.3 however far apart `A` and `B` happen to be. The C-space
difference does not care, because the two client positions really are 0.3 blocks apart in the player's own
map. **That is the point of the convention: the game is judged on the world the player can see.**

The 96-block jump between `A` and `B` is real, but it belongs to the **server** coordinates. It is what
`firstGoodX` and friends look like on a crossing, and it is the server's business. Folding it into a
measurement taken in the other frame is not "removing an artefact" 闂?it adds the entire server-side
discontinuity to the player's step. `PlayerMoveTest` measures the result: a 0.3-block step reported as
**115 blocks**, against a vanilla limit of 10.

#### How that presented

`movedDist` is a squared distance and vanilla rejects anything over `100`, which is 10 blocks. A crossing
therefore produced a 115-block "move", vanilla logged "moved too quickly!", corrected the player, and called
`teleport()` 闂?on *every single chunk border crossing*. The log showed the corrections clustered exactly on
the crossings, which is what finally identified it. A player walking back and forth over a border gets
corrected every time, and the world appears to be fighting them.

#### The one case that does need handling

A chunk the map does not cover. There is no C-space answer to give at all 闂?undefined, not enormous. The
real movement is used, and counted in `unmapped=`. The old code returned `1e9` here, and since `1e9` is
always a teleport and a teleport always reshuffled, a momentary gap in the mapping became an infinite loop.

#### Two warnings, both learned the hard way

- **The check and the movement must come from one method.** An earlier version fixed only the check and left
  the movement actually applied to the player as the raw C difference, handing them a 125-block fling at
  every border. It passed the check it had just relaxed, and then threw them across the world.
- **A test that models a rule, rather than a rule, will confirm a wrong one.** The first version of
  `PlayerMoveTest` implemented the same seam formula the production code had, and passed 闂?it verified the
  arithmetic of a rule that was wrong. `PoolExhaustionTest` had the same shape earlier: it hardcoded
  `Math.max(randomChunkRange, viewDistance+2)`, labelled it "the shipped formula", and asserted that was
  fine, but the shipped code has no such `Math.max`. **When a test is the only evidence for a claim, it is
  not evidence.** Cross-check a number from the game before believing one from a model.

### 2.6.2 Forced teleportation: absolute and relative

**The absolute half is implemented. The relative half is a TODO.**

#### Absolute teleporting 闂?done

An absolute teleport names a *place*, so the world has to honour both maps, and the shuffle follows from
whether that place is already on the player's map:

| where the player lands | what happens to the shuffle |
|---|---|
| a chunk that **is already mapped** | **nothing. The current shuffle is kept.** |
| a chunk with **no position yet** | a position is **initialised for that chunk alone**, shuffle kept |
| neither, because the pool is full | last resort: a new shuffle, logged as such |

`inject5` implements this, using `VecTransformer.isMapped` and `ensureMapped`.

> **Do not "simplify" this back to "reshuffle on every teleport".** That is what it used to do, and it is
> what turned one bad frame into a teleport every tick. The teleport this hook mostly sees is the server
> correcting a player back to where it already thinks they are, or void recovery picking them up. Both land
> on somewhere already mapped. Neither is a new arrival. But the old code redrew the entire world, so the
> player's surroundings were replaced with whatever the new shuffle pointed at 闂?often ungenerated, often
> empty 闂?and they fell, and void recovery picked them up, and it repeated. A playtest log showed six
> redraws in two seconds with the player never moving at all.

Note the middle row: initialising a position for the one chunk that needs it, rather than redrawing
everything. Rebuilding the whole map to satisfy one destination would move every other chunk the player can
see, for no reason.

#### Relative teleporting 闂?TODO, not started

Relative movement is not implemented at all. Vanilla applies pushes in S and the mod does not intervene.

The rule as agreed: a relative push is a statement about **C only**. Apply the push on the player's own map,
because that is the only map they can observe it on. Pushing a player 6 blocks should move them 6 blocks in
their own view, whatever the corresponding S-space delta turns out to be.

The asymmetry with absolute is the point: an absolute teleport names a place, so the server can say where it
is on both maps. A relative one names only an amount.

Where the work would go:

| piece | where | note |
|---|---|---|
| pistons, explosions, currents, impulses | not hooked at all | vanilla applies them in S today |
| the movement rule itself | `VecTransformer.playerMove` | already the right shape: S in, C out |

#### Open questions when the relative half gets picked up

- For a relative push that carries the player across a chunk border, is the push measured in C before or
  after the seam is taken out? 闂?.6.1 has machinery for the seam but nothing here has decided which applies.
- Does a relative push that lands in an unmapped chunk initialise a mapping, as the absolute case does?
- `refreshInstance` no longer fires on a mapped teleport, so 闂?.5's "new shuffle on arriving somewhere
  unknown" is now driven purely by `ensureMapped` failing. Whether that is enough to stop a player learning
  the real layout over a long session is untested.



### 2.6.3 Reading the diagnostic

Every chunk entrance logs one line, and every teleport logs another. Both go to the `item-storage` logger.

```
[remap] Steve | S [12, -5] -> [13, -5] | C [-3, 41] -> [4, 38] | dS=(1,0) dC=(7,-3) | view=+6 pool=+7 map=169 dry=0 unmapped=0 fail=0
[remap] Steve | teleport -> [12, -5] is already on the map, shuffle kept
```

Read it like this:

| field | what it means |
|---|---|
| `S a -> b` | the chunk the player was in, and the one they are in now, on the **real** map |
| `C a -> b` | the same two chunks on the **player's own** map |
| `dS` | how far they actually walked. Always 0 or 1 闂?that is what crossing a border means |
| `dC` | how far their own map says they moved. **Do not trust this on its own 闂?see below** |
| `seams` | how many moves have been measured across a seam so far |
| `map=` | how many chunks are currently mapped. Steady at `169` means the view is full and healthy |
| `dry=` | times the placement pool ran dry. Should stay `0`; see 闂?.4.1 |
| `unmapped=` | moves judged with no C-space answer because a chunk was missing. Used to be `1e9`, i.e. a teleport |
| `fail=` | moves that hit a real error. Should stay `0`; non-zero means something is broken, not merely unmapped |
| `resent=` | pages announced to the client since the previous line. **The proof the mechanic is happening** |

> 闂?**`dC` badly understates the shuffle, and an earlier version of this document told you to read it as
> the thing to look at. That was wrong.** A playtest log showed `dC=(0,1)` and `dC=(0,-1)` on consecutive
> lines while the entire mapping was being replaced underneath the player. The reason is that the
> placement pool is anchored to the player's *real* chunk, so after a reshuffle the player's own C position
> is re-drawn from a pool centred near their real coordinates and barely moves 闂?even though every chunk
> around them has been swapped for a different part of the world.

What to read instead:

- `dS` of 0 or 1 per axis 闂?a genuine border crossing. This one is reliable.
- **`dS=(0,0)` on its own** 闂?this is not information at all. It is the first line a *brand new*
  transformer prints, because there is no previous chunk to compare against. It always appears immediately
  after a `teleport -> new shuffle` line, and it is a useful marker that a reshuffle just happened.
- **`map=` dropping or `dry=` climbing** 闂?the mapping is in trouble.
- **Several `teleport -> new shuffle` lines in a row, with nothing the player did** 闂?the loop. It should
  be impossible now (闂?.6.2): a mapped destination logs `shuffle kept` instead. If `new shuffle` still
  repeats, `ensureMapped` is failing and `dry=` or `fail=` will be climbing alongside it.
- `unmapped=` climbing in a `dS=(0,0)` line 闂?moves were judged with no C-space answer because a chunk was
  missing from the map. That used to be reported as `1e9`, which vanilla reads as a 31,623-block leap, so
  every one of them was a teleport. See 闂?.6.1.
- `Packet discarded: ClientboundLevelChunkWithLightPacket` 闂?the tracking view is asking for chunks the
  server can no longer place. That was the actual void cause; see 闂?.7.1.

- `resent=` steady at `0` on ordinary crossings — the client is not being told about the re-roll, so the
  shuffle is invisible again and the world the player is looking at has quietly drifted away from the
  world the server is simulating. This is the most useful number on the line; see §2.7.2.


### 2.6.4 Entity reach: judging it in the player's map, like blocks

**The rule:** the player reaches for things in the map they are looking at. So an entity's distance is
measured with the player's eye in **C** and the entity's box in **C**, exactly as a block's distance is.

Until now it was not. Block reach went through `PlayerMixin.inject1`/`inject2` and
`ServerGamePacketListenerImplMixin.inject1`, all of which convert into C. Entity reach had no equivalent
hook, so the same question was asked in two different spaces depending on what you were clicking. Both
results were absurd: standing right next to a cow and not being able to touch it, or touching something on
the far side of the world.

Three hooks in `PlayerMixin` do this now, one per vanilla method that asks the question:

| vanilla method | who calls it | hook |
|---|---|---|
| `isWithinEntityInteractionRange(AABB, double)` | `handleInteract`, and spectator targeting | `inject3` moves the eye, `inject4` moves the box |
| `isWithinAttackRange(ItemStack, AABB, double)` | `handleAttack` | `inject5` moves both ends |

`isWithinEntityInteractionRange(Entity, double)` has no hook of its own and does not need one. It is a
one-line delegation to the `AABB` overload, so hooking that covers both. Worth remembering if vanilla
ever changes that delegation.

**The descriptor on the method name is load-bearing.** `Player` has two overloads called
`isWithinEntityInteractionRange`, and only the `AABB` one contains the calls being redirected. Naming
the method without its descriptor makes Mixin search both, find nothing in the `Entity` overload, and
fail the injection at startup under this config's `defaultRequire` of 1. The hook names carry
descriptors for exactly this reason.

**A box is a volume, and a volume can straddle a page.** A mob is smaller than a page, so its two corners
almost always land on the same page and the box is simply translated, which is exact. When an entity really
does straddle two pages that are mapped to unrelated places there is no single right answer — the player
is looking at a collage. The six-argument `AABB` constructor takes the component-wise min and max, so
the result is the bounding box of the two images. That is deliberately the generous direction: it can make
reach slightly too long, never too short, so a mob at a seam stays hittable instead of becoming impossible
to reach. Shrinking it would look like the safer choice and be the worse one.

**A page the player cannot see is refused, not measured.** `inject4` returns `Double.MAX_VALUE` and
`inject5` returns `false`. That is the same answer `inject2` gives for an unreachable block, and for
the same reason: geometry that is not part of the player's world has no position in it to measure to. The
refusal is a large finite number rather than infinity because `inject5` takes a square root of it.

**`AttackRange`'s arithmetic is not duplicated.** `effectiveMinRange` and `effectiveMaxRange` are
public, and the hitbox margin is a record component, so `inject5` makes the same comparison vanilla
would have, with both ends in C. If those ever stop being public this hook has to be reworked, and that is
the first thing to check when it mysteriously stops applying.

**This does not solve the reach problem, it makes it honest.** With both ends in C, and any two *distinct* C
pages at least 16 blocks apart, a reach crossing a page boundary spends at least 16 blocks of its 3-block
budget on the shuffle alone. So whether you can touch something is now genuinely decided by the shuffle
rather than by where the thing is. That is a design question about collage worlds, not a defect, and it is
still not decided.

### 2.6.5 Entities: offsets instead of positions

Entities are sent to the client the same way blocks are — S becomes C, so the player sees an entity where their
map says it is. But entity movement is mostly sent as an **offset**, not a position, and that is where this
gets genuinely hard.

**The problem.** `ClientboundMoveEntityPacket.Pos` and `.PosRot` carry a `VecDelta`: not "where the entity is"
but "how far it moved from where you already think it is". The client adds it to a position it holds. So there
is no position in the packet to convert, and the base it is measured from lives on the client.

**Why it mostly works anyway.** An offset is unchanged by a translation. Writing `C = S + o` for a page's
offset, and assuming the client really holds `S_base + o_base`:

    client computes :  C_base + (S_now - S_base)
    we want         :  S_new + o_now
    they agree when :  o_base == o_new

So **an offset packet is already correct whenever the entity has not crossed a page boundary**, and has to be
forwarded untouched. This is the same reason a constant offset leaves vanilla's own chunk-distance tests
alone; see §2.4.2. Only a crossing is wrong, and there the client puts the entity in the C-space neighbouring
page instead of the page it actually belongs to — which is exactly the reported symptom: the entity is visible,
is somewhere else, and cannot be hit there.

**Why it cannot be fixed by adjusting the offset.** The correction is `o_new - o_base`, a whole number of
pages: at least 16 blocks, usually far more. `VecDelta` stores each axis as a `short` at 1/4096 of a block, so
it can express about ±8 blocks. **The correction does not fit.** The only correct move is to change what kind
of packet is sent: when the offset has changed, the position is sent absolutely, as a
`ClientboundEntityPositionSyncPacket`. `EntityDeltaTest` measures all of this against vanilla's own
`VecDeltaCodec` rather than asserting it.

**Knowing whether the offset changed needs the base, so the base is remembered.** `EntityPositionRemapper`
holds, per entity, both the server position and the client position last sent. Then the offset in force is
read straight off `C - S`, at the base and at the new position, and compared. Two conditions must hold before
an offset packet is forwarded:

1. **The offset has not changed.** The smallest real difference is one page, so the tolerance is half a block —
   far above the 1/8192 the client's own quantisation drifts by, far below 16.
2. **The base is the one the server encoded against.** This is the subtle one. `ServerEntity` keeps its own
   delta base and can advance it *without sending anything* — a riding entity resets its base every tick,
   because the vehicle packet carries the position instead. When that happens the client and the server have
   silently diverged, and the delta no longer means `S_now - S_base`. So the delta is decoded against the base
   remembered here and the result compared with where the entity really is. If they disagree, the base has
   drifted and an absolute packet is sent.

The invariant "the client holds `s2c(S_base)`" is maintained by induction: true at the spawn packet, an
untouched offset preserves it, an absolute packet re-establishes it. Entries are dropped on
`ClientboundRemoveEntitiesPacket` and overwritten on spawn, since entity ids are reused.

**Entity packets are converted per player, which is why they skip the `modified` cache.** That cache exists
because the other handlers edit packets in place, and a broadcast is one object handed to every tracking
player in turn — without a guard the second player would be sent the first player's converted copy. For entity
packets the cache is the wrong shape: conversion is a property of the *recipient*, not the packet. Two
players have two different maps, two different offsets and two different bases, so one packet object
legitimately has two different correct answers. Handing both players the first one's answer would be wrong, so
these are converted per player and the cache does not apply. The re-entry flag guarding the replacement send is
an instance field rather than a static one, or two players on two threads would trip each other.

**What is deliberately not translated.** `ClientboundSetEntityMotionPacket` carries velocity, not position. A
page offset is a whole number of pages, so a velocity only means anything inside one page, and carrying it
across one is the ill-defined case the design accepts. Guessing would make client-side prediction disagree
with the server, which is worse than not translating. `ClientboundMoveEntityPacket.Rot` is rotation only, and
`RemoveEntitiesPacket` / `SetEntityLinkPacket` / `TakeItemEntityPacket` are ids.

**This makes the picture honest, not complete.** Entity collisions still resolve in S, because vanilla
computes them there and rewriting physics is not this mod's business. So an entity shoved by the player really
does move in S, and this reports that faithfully rather than inventing a different move. The asymmetry —
collision judged in S, position shown in C — is inherent to shuffling a world underneath a running simulation,
and the position shown is always the true one.

## 2.7 Chunk tracking 闂?which pages the client is sent

`ChunkMapMixin.inject2` hands the view its player 闂?`ChunkTrackingViewPositionedMixin` overrides
`contains` and `forEach` 闂?`ChunkRemap` does the filtering 闂?`ChunkMapMixin.inject1` replaces the
border test 闂?`ChunkTrackingViewMixin.inject1` replaces `difference` and rewrites the forget-packet
consumer so the chunk is converted before being sent.

The remap is held as a `ChunkRemap`, which has three shapes, because the old code built a
`(2*viewDistance+5)^2` entry map on **every** tracking-view update and then walked that whole map on every
containment question:

| Shape | Used by | Cost |
|---|---|---|
| `IDENTITY` | `nop`, and any dimension with no transformer configured | nothing 闂?vanilla runs untouched |
| `TRANSLATION` | `linear` | two ints |
| `MAPPING` | `xor`, `random` | the per-chunk table, only for these two |

Two results worth knowing:

- A constant offset does not change the *distance* between two chunks, so for `IDENTITY` and `TRANSLATION`
  vanilla's own answers to "is this chunk in the view?" and "is this chunk on the border?" are already the
  right ones. That is what `ChunkMapMixin.inject1` relies on when it short-circuits, and it is why
  `getPlayers` 闂?which asks the border question once per chunk, per player, per tracking update 闂?costs
  nothing extra under `nop`.
- The `MAPPING` table is deliberately built over a **wider** area than the view, and `ChunkRemap.forEach`
  keeps the whole border ring. `xor` and `random` scatter chunks, so the client's square view is not the
  image of a square on the server; the server has to send everything whose image lands in the view. The ring
  doubles as in-flight margin so a walking player never outruns the chunks being sent. Do not trim it.

### 2.7.1 The view must never hold a snapshot of the mapping

**This was the cause of the void, and it is not the seam.** Keep this in mind before changing anything
about how the view gets its remap.

`ChunkMap.inject2` is the only place the mod touches the tracking view, and it runs on
`applyChunkTrackingView`. Vanilla calls that **only when it decides the view has changed**, which it
decides with this test:

```java
if (view instanceof Positioned p) {
    if (p.center().equals(player.chunkPosition()) && p.viewDistance() == vd) return;   // early out
}
applyChunkTrackingView(player, ChunkTrackingView.of(pos, vd));
```

So the view is rebuilt when the player's **real** chunk changes or the view distance changes. That is all.

But `refreshInstance` (闂?.5) replaces the player's entire mapping without touching their real chunk. Nothing
about the player's situation has changed as far as vanilla is concerned, so **vanilla never rebuilds the
view**. A remap captured at `inject2` time is therefore still describing the *previous* shuffle.

The consequence is a disagreement between two halves of the same feature:

| who | asks about | using |
|---|---|---|
| the view, deciding which chunks to send | a **stale** copy of the mapping from the last shuffle |
| the outgoing path, converting each packet | the **live** mapping |

The view names server chunks the live mapping has forgotten. `s2cTransform` throws `ChunkUnusedException`,
`ServerCommonPacketListenerImplMixin` throws the packet away, and the log fills with
`Packet discarded: ClientboundLevelChunkWithLightPacket`. The client stops receiving the chunks it is
standing in, falls through unloaded space, void recovery teleports it, `refreshInstance` fires, the view is
stale again. The loop.

A playtest log showed eight whole chunk-with-light packets discarded in one tick, including two for server
chunks that could only have come from the *previous* position's placement pool.

**The fix:** the view is given the **player**, not a remap, and derives the remap itself from whichever
transformer that player currently has, re-deriving when `VecTransformer.mapVersion()` moves. The cache key is
the **transformer reference as well as the version** 闂?a reshuffled player gets a brand new transformer whose
version restarts at zero, so two transformers routinely carry the same number and only the reference tells
them apart. `StaleRemapTest` builds exactly that collision and checks the reference catches it.

A cache hit is two comparisons, so this is cheaper than it sounds. Use `peekInstance`, not `getInstance`:
this runs on every containment query, and a containment query must never be the thing that builds a
transformer.

### 2.7.2 The re-roll has to be announced by hand, because nothing else will

**This is the other half of 2.7.1, and it is the same fact. Read them together.**

2.7.1 says vanilla never rebuilds the view when a re-roll happens. The obvious next question is: so how does
the client ever hear about the re-roll? The answer is **it does not**, and that is not a gap in the mod, it
is what vanilla does.

The client learns a chunk exists in exactly one way: its server position is put into
`PlayerChunkSender.pendingChunks`. There are only two things that ever do that, and neither of them fires
when a page is re-rolled:

| who | when | fires on a re-roll? |
|---|---|---|
| `ChunkMap#applyChunkTrackingView` | runs the add/remove diff, adding whatever is new | **no** - 2.7.1: it is not even called |
| `ChunkMap#onChunkReadyToSend` | a chunk has just finished generating | **no** - the re-rolled-to chunk is an old one, generated long ago |

So a re-rolled page gets a new server position, the server simulates the new page, and the client is still
holding the old page's terrain at that position. Nothing is broken in the mapping. The two sides simply
disagree, silently.

That one fact produced three separate reports, and they are worth listing together because they looked like
unrelated bugs for a long time:

- **"remap is seldom seen"** - it was never seen, ever. The shuffle was working the whole time.
- **"mismatch after travelling a long time"** - the same thing, noticed once the disagreement got big
  enough to stand on.
- **"cannot break blocks, but only some of them"** - the player is aiming at terrain the server has already
  thrown away. The click is converted with the *current* mapping, so it breaks a real block, just not the
  one the client is showing them, and the client's own view of it never changes.

**The fix:** announce each page as it is placed. `RandomTransformer.showPage` marks the chunk pending to
send, and is called from `put` - which is the one place a page acquires a client position. Three things make
this safe and cheap:

- `pendingChunks` is a `LongSet`, so re-announcing a page the client already has is one hash insert and
  changes nothing. It self-heals rather than duplicating.
- A page whose chunk is not loaded yet returns `null` and is left alone. `onChunkReadyToSend` picks it up
  when it does load, which is the correct retry, so nothing is lost.
- **No forget is sent.** The new chunk packet replaces the old terrain in place, so the page comes back
  holding different terrain. Sending a forget instead would make it vanish first, and a hole behind the
  player is a hole the player can fall through.

**Do not try to route this through the diff.** It is tempting - make the view change so vanilla notices -
but it cannot work without reintroducing the void, because for the diff to see a re-roll the previous view
would have to describe the *old* mapping, which is exactly the snapshot 2.7.1 had to remove. Those two
requirements are contradictory in a single object. Splitting them (live for `contains`/`forEach`,
snapshotted for `difference`) would work, and would also mean a 169-chunk re-send on every teleport.
Announcing the pages directly is the smaller change.

**The cost is real and worth watching.** One border crossing re-rolls roughly one edge of the square, so
about 13 to 26 full chunk-with-light packets per crossing, and a crossing happens every 16 blocks walked.
`PlayerChunkSender` batches them across ticks, so there is no burst, but this is the price of the mechanic
being visible at all. The `resent=` field on the `[remap]` line counts the pages announced since the last
line: roughly one edge is right, `0` means the client is being told nothing again, and a number in the
hundreds means something re-rolled the whole world and should not happen on an ordinary crossing.

## 2.8 Block placement

`BlockPlaceContextMixin` round-trips the placed-block position S 闂?C 闂?S, which reduces to the identity.

## 2.9 Two other things in that file, not about coordinates

`EntityMixin` also has `inject1` and `inject2` on `Entity.move`, rewriting `checkTo` and `newPosition`.
Both are an S 闂?C 闂?S round trip around the entity's own position, which is the identity for any
one-to-one mapping.

> **Checked, and fine.** An earlier version of this document claimed `checkTo` and `checkDistance` no
> longer exist in 26.3's `move()`, and that the file therefore fails to apply. That was wrong. Reading the
> local variable table out of the real 26.3 `Entity.class` shows all four locals this file captures are
> present: `checkTo`, `checkDistance`, `movement`, `newPosition`. `inject3`'s redirect target
> (`BlockPos.betweenClosedStream(AABB)` inside `isInWall`) is also present. All three injects apply.

`ServerPlayerGameModeMixin` is unrelated to coordinates: if `player.connection == null`, it forces the
game-mode change to succeed. That is for the fake player created while loading.

## 2.10 Threading, and why local testing cannot see half of it

> **Read this before believing any timing claim in this document, including "stable in testing".**
>
> On an integrated server - a single-player world, or a client on the same machine - latency is essentially
> zero. Every race in this mod is a race between the netty thread and the server thread, and their ordering
> is decided by how long a packet takes to arrive. At zero latency the two run so close together that the bad
> interleaving almost never happens. On a weak connection the same packet can arrive hundreds of
> milliseconds later, and an interleaving that never reproduces on a LAN reproduces every time.
>
> This has bitten this project before: glitches that were "fixed" and then came back, and behaviour verified
> locally that failed in front of real players. **A clean local run is not evidence that a timing issue is
> fixed.** It is only evidence that it did not fire on a machine with no latency.

**What this means in practice.** Anything that depends on a mapping staying put between two moments on
different threads is a latency-sensitive bug. The known one is an interaction, and it is two conversions:

1. the click arrives and is converted client-map to server-map on the **netty** thread
2. the server applies it, and the outcome is converted back on the **server** thread
3. if the chunk was re-rolled in between, the two conversions disagree - the server breaks the right block
   and tells the client a *different* block changed, so the block the player hit reappears

The hold is `remapPinMillis` in `ItemStorage.json`, **default 10000 ms**. It has to cover the full round
trip, so it is deliberately far larger than any plausible LAN figure. Over-holding costs a little of the
mechanic: a recently-touched page keeps its position slightly longer than it otherwise would. Under-holding
brings the reverting-block glitch back, which is much worse. A zero or negative value is floored to 1000 ms
rather than trusted.

**Which space the pin is in, and why it is easy to get wrong.** The pin table is keyed by **server** chunk,
because that is what `remove` and `findFreeServerPos` have in hand when they ask whether a page is frozen.
But `pinAt` is handed a position in the **player's map**, so the chunk it reads off that position is a
**client** chunk. Those must be translated through `c2sMap` before pinning.

Getting this wrong is worse than a no-op, which is why it is written down. Both maps are keyed by `ChunkPos`
over the same range, so a client chunk is frequently a real key of `s2cMap` too - just for some unrelated
page. The freeze then lands on *that* page instead of the one the player hit, which means:

- the page being interacted with is not protected, so the block-reverting glitch is not actually fixed, and
- an unrelated page is frozen for the whole hold, so it cannot be re-rolled, which quietly removes pages
  from the mechanic for no visible reason

The second effect is what made the symptom look like "some blocks break and some do not": the pages that
happened to get frozen by accident were the ones that worked. `RandomTransformer.pinAt` now reads
`c2sMap.get(clientChunk)` and pins the result; `javap` on the built class shows the `Map.get` and the call
to `pin`.

**Safe regardless of latency**, because they never span a round trip: the movement measurement in C-space,
the chunk tracking set, the visibility rule, and the mapping itself. These are the parts that can be
verified locally with real confidence.

- `ConnectionMixin.genericsFtw` runs on the **netty** thread and calls `VecTransformer.getInstance`, which
  mutates `instances`. That map is a `ConcurrentHashMap`, and `getInstance` builds-and-stores through
  `computeIfAbsent`, so two threads cannot both build a transformer for the same player. It was a
  `WeakHashMap` before, which is not safe to touch from two threads at all.
- **`instances` still leaks, and nothing was done about it.** The mod registers no disconnect hook, so no
  entry is ever removed. A weak key would not have helped either: every transformer holds a strong reference
  to its own player, so an entry can never become unreachable. Every player who has ever joined stays in the
  map, holding themselves, their transformer, and - for `random` and `xor` - their whole chunk map. The fix
  is a disconnect event that removes the entry. See Part 3.
- `ServerCommonPacketListenerImplMixin.send` runs on the **server** thread and mutates packet objects.
- `VecTransformer.tick` is called from `ChunkMapMixin.inject3` during `updateChunkTracking`, and mutates
  `s2cMap` / `c2sMap` (both `ConcurrentHashMap` under `random`) while the netty thread may be reading them.
  **A high-latency client widens this window**, because its packets arrive long after the tick that would
  have reconciled them.

## 2.11 Performance

commit `51b635d`:

- This mod used **0.06%** of the CPU, across two profiles.
- Its largest class in the heap was `Road2Chunk` at 1.5 MB, ranked 72nd out of 48,072.
- The memory exhaustion was **not** ours: vanilla ran out of memory doing the same test. The cause was
  vanilla's own chunk-saving queue 闂?about 4 million queued write tasks holding 17 million unsaved NBT tags,
  with 97.1% of `int[]` allocations coming from `PalettedContainer.pack`.

So the problems in this mod are **correctness** problems, not speed problems. And they are invisible in a
CPU profile, because the mod barely registers there. That is worth remembering before hunting for a
performance fix in a CPU profile.

---

# Part 3 闂?Open questions

Things we have read in the code and could not settle.

### 闂?Fixed, and worth remembering what went wrong

An earlier version of this document said *"a C position is never a valid argument to a world lookup"*. That
was wrong, and acting on it broke working code.

Because of that wrong rule, `ServerGamePacketListenerImplMixin.inject1`, `inject3`, `inject4`, `inject6` and
`inject9` were changed to return vanilla's own values and stop converting into C-space. That contradicted
闂?.3 and 闂?.6 闂?those five hooks are how the game gets judged in the player's map. The same wrong rule was
used to replace the per-operand transforms in collision with a single shared offset, which would have made
the world rigid instead of shuffled.

All of that has been put back to the version in git at commit `51b635d`. The rule that caused it is
remembered here so nobody re-derives it: **the vanilla answer is the S-space answer to a question that is
supposed to be asked in C-space.** Under `random`, two blocks that are neighbours on the real map are
genuinely not neighbours on the player's map, and the game is *meant* to judge them that way. Non-isometry
and a broken-looking collage are the design, not defects.

A second wrong rule came out of the same session, and it is worth writing down because it looked so
reasonable: *"a C position is never a valid argument to a world lookup"*. Under it, the movement hooks were
changed to fall back to measuring in **S** whenever the C number was inconvenient. That is backwards 闂?the
player walks in C, C is their map, and the measurement has to stay there. The right move is to keep the
C-space difference and take out the shuffle's contribution (闂?.6.1). Worse, that version fixed only the
*check* and left the movement actually applied to the player as the raw C difference, handing them a
125-block fling at every chunk border. It passed the check it had just relaxed, and then threw them across
the world. **The check and the movement must come from one method**, so they cannot disagree.

The void loop that followed was not that at all 闂?see 闂?.7.1.

### Answered

1. **The `//what is this??` line** 闂?copied from vanilla, and probably wrong. Check it later. It is gone
   anyway: `inject4` now calls `playerMove`, which uses the full 3D difference.
2. **The `1e9` fallbacks** 闂?intentional, to avoid a crash. A better way may replace them.
3. **A new shuffle on every teleport** 闂?intentional, so players cannot learn the real map. See 闂?.5. A
   version that reused the transformer for same-dimension teleports was written and has been removed.
4. **`EntityMixin` applies cleanly.** An earlier note claimed its captured locals no longer existed in 26.3.
   They do 闂?see 闂?.9.
5. **The void teleport loop** 闂?found and fixed. It was the stale tracking view (闂?.7.1), not the movement
   seam. The seam was a real and separate bug, and it is fixed too (闂?.6.1); fixing it alone left the void
   loop running, which is why the log said the loop was still there.
6. **The placement pool losing its randomness** 闂?found and fixed (闂?.4.1). A rewrite of the allocator had
   dropped the candidate shuffle, which made every reshuffle put the player's page in the same corner.
7. **`1e9` for an unmapped chunk was the loop's engine** 闂?found and fixed (闂?.6.1). A move whose endpoint
   sat in a chunk the map did not cover reported `1e9`, which vanilla always reads as a 31,623-block leap,
   so it snapped the player back and teleported; that teleport redrew the shuffle; and the redraw could
   leave the new destination unmapped too. A playtest log showed six teleports in two seconds with the
   player never moving. A missing mapping is now reported as the player's real movement, and counted
   in `unmapped=` so it is visible instead of silent.
8. **Reshuffling on a mapped teleport** 闂?found and fixed (闂?.6.2). See also 闂?.5; that is now a "keep the
   shuffle" rule, not a "new shuffle every teleport" rule.
9. **The "seam" subtraction in playerMove was wrong and broke every border crossing** - found and fixed
   (2.6.1). It reported a 0.3-block step as 115 blocks, so vanilla corrected the player on every single
   crossing. The corrections clustered on the crossings in the log, which is what identified it.
   **PlayerMoveTest had passed while asserting it**, because the test implemented the same wrong formula
   the code did.
10. **The re-roll was never sent to the client** — found and fixed §2.7.2. Three reports that looked like
    unrelated bugs were one bug: remap was seldom seen, the world drifted out of agreement with the
    server after a long walk, and blocks stopped responding. The client was never told, because vanilla
    rebuilds the tracking view only when the player's real chunk or the view distance changes, and a
    re-roll changes neither. Pages are now announced as they are placed, and `resent=` on the
    `[remap]` line is the number to watch.
11. **The block-revert pin was pinning the wrong chunk** — found and fixed §2.10.
    `pinAt` was handed a position in the player's map and pinned that chunk directly, but the pin
    table is keyed by server chunk. Since both maps are keyed by `ChunkPos` over the same range, the
    freeze usually landed on an unrelated page instead of the one being broken, so the block-reverting
    glitch was not actually fixed and some pages were quietly frozen against re-rolling. This is very
    likely what produced "some blocks break and some do not": the pages that happened to get frozen by
    accident were the ones that worked.


### Still open

7. **`VecTransformer.INF`** is the position `(1e9, 1e9, 1e9)`. It is returned from `PlayerMixin.inject1`
   and `ServerGamePacketListenerImplMixin.inject1` when a page cannot be found. A *position* is being
   returned where a *reach* is expected, so the reach test compares against 1e9 and passes for anything. We
   have not checked whether this is actually reachable.

8. **The placement pool was smaller than its own view.** `request`/`init` drew from a pool of
   `(2*randomChunkRange+1)闂傚倸鍊搁崐鐑芥倿閿曞倸纾跨€规洖娲﹀畷鏌ユ煙閻楀牊绶茬紒鈧?server positions 闂?225 at the default `randomChunkRange` of 7 闂?while the view
   it has to fill is `(2*(viewDistance+2)+1)闂傚倸鍊搁崐鐑芥倿閿曞倸纾跨€规洖娲﹀畷鏌ユ煙閻楀牊绶茬紒鈧? At the current `viewDistance` of 4 that is 169, so it fits.
   Raise the view distance to 6 and the view needs 289, which no longer fits, and the allocator started
   overwriting live entries and losing track of chunks (`RandomAllocatorTest`). Now pinned to
   `max(randomChunkRange, viewDistance+2)`, so it cannot recur at any view distance, and a dry pool is
   counted in `dry=` rather than silently corrupting the map. **This was not the cause of the reported void
   loop** 闂?闂?.7.1 was 闂?but it would have become one.

9. **The reflective sweeps** use `getDeclaredFields`, which sees only the fields declared on the packet's
   own class 闂?not fields it inherits. It will also quietly convert any `BlockPos` field on a packet nobody
   thought about.


10. **Entity reach is now mapped too** — fixed, see §2.6.4. It used to be judged in S while block reach
    was judged in C, which is the exact asymmetry §2.3 exists to prevent.
11. **Entity movement is sent as offsets, not positions** — fixed, see §2.6.5. An offset cannot be
    converted, because the position it is measured from is a position the client holds and the packet
    does not state. It mostly needs no conversion — an offset is invariant under a translation — so the
    bug only appeared when an entity crossed a page boundary, where the correction is a whole number of
    pages and so cannot be expressed in a delta at all. Reported as "an entity I can see but cannot hit".
12. **Reach effectively stops at page borders — for blocks and now entities alike.** With both ends in
    C, and any two *distinct* C pages at least 16 blocks apart, a reach that crosses a page boundary spends
    at least 16 blocks of its whole reach budget on the shuffle alone. So whether you can touch something is
    now decided by the shuffle rather than by where the thing actually is, and the same click can be allowed
    or refused purely by which pages its two ends landed on.

    This was measured for blocks at a 5.5-block reach, where a target 0.7 blocks away on the real map has
    to land at almost exactly one page of separation in C to stay reachable, which is a few percent of the
    available offsets. Entity reach is 3 blocks, which is tighter still, and mobs are small enough to sit
    inside a single page, so they are the case most often affected. See §2.6.4.

    This may be intended for a collage world and it may be unplayable. That is a design question, not a
    defect, and the answer has not been decided. The seam machinery from §2.6.1 could be applied to
    reach, but reach has no trajectory to discount, so doing so would be a *decision* to measure reach
    against a hypothetical un-shuffled world, rather than a consequence of the convention. Not done.

13. **Two leaks, both pre-existing, neither fixed.** See 闂?.10.

    - **`VecTransformer.instances` grows forever.** No entry is ever removed, and the mod registers no
      disconnect hook. A weak key could not fix it either, because each transformer strongly references its own
      player. Every player who ever joins is pinned, with their chunk map.
    - **Loading tickets are only ever added.** `XorTransformer.addLoadingTicket` and
      `RandomTransformer.addLoadingTicket` call `addTicketWithRadius` with no matching removal. Combined with
      闂?.5 闂?a fresh transformer on *every* teleport, each re-adding its whole table 闂?a player who teleports
      a lot accumulates a large multiple of one view's worth of tickets. This was measured, not guessed.

14. **The chunk-tracking `ChunkRemap` is now load-bearing for correctness, not just speed.** It was
    verified equivalent to the old per-chunk map over 80 combinations (`MappingEquivalence`), but since the
    view re-derives its remap from it whenever the map changes (闂?.7.1), a bug in `ChunkRemap` is now a
    correctness bug rather than a performance one. It deserves a real playtest.

### Set aside

- **Entity mapping** — *"never working properly since I cannot understand some protocol classes"*. Positions
  and offsets are now translated, see 
§
2.6.5. What is still open is the velocity case:
  ClientboundSetEntityMotionPacket is left in S on purpose, because a page offset is a whole number of
  pages and a velocity does not survive one. That is a decision to revisit, not an oversight.
- **Collision** 闂?considered solved, but known to be hard, because the player can cross a page while a query
  is running.
