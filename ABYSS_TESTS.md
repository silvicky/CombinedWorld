# Manual test cases

The cases that cannot be settled by reading the code, by `tools/SpawnProbe.java`, or by a headless server: they
need a real client, and most of them need real latency.

Numbering is stable — quote the ID (`R-03`, `S-01`) when reporting a failure, so it points at one paragraph. Fill
in the **Result** column as you go; leave it blank until the case has actually been run.

**Priority:** P1 = blocks release or covers the change under test; P2 = should be run before a release;
P3 = worth checking when convenient.

## Before you start

**Where the statements appear.** A world hosted by the client writes `backrooms/run/logs/latest.log`; a
dedicated server writes `logs/latest.log` in its own directory. Everything tagged `[abyss]` comes from this mod.

**Most `[abyss]` lines are `debug`.** Every per-event diagnostic — `spawn copy`, `drop copy`, `move copy`,
`metadata of copy`, `motion of copy`, `hit point`, `carries`, `leashed to`, `death event sent`, `re-offset copy`,
`own-band …`, `bundle for`, `update … state …`, `place attempt`, `place result`, `place across seam`,
`break/use dispatched`, `attack dispatched`, `attack target`, `attack on copy completed`, `interact dispatched`,
`window for`, `window derived`, `offset for`, `teleport acknowledged`, `speed check rebased` — is logged at
`debug`, so it is **absent at the default level**.

Anything under a `Log:` heading below that names one of those lines therefore needs debug logging on, or has to
be judged from *Expected* alone. In practice that is most cases: enable debug for a test pass, and leave it off
for normal play.

**What is visible without debug** — deliberately, because each one is either rare or means something went wrong:

- `seam crossed …; <n> ms` (info, once per crossing)
- `respawn in … moved from … to …`, `fell out of … the window no longer applies`, `arrival clamped …`,
  `no floor near the respawn point …` (info, once per death)
- `place across seam refused: no band at position …` (warn)
- `no place to stand …`, `could not find free space …` (warn, respawn could not find a floor)
- `not entity-ticking for …`, `simulation ticket for … was missing …` (warn, the band is not simulating)
- `window was out of step with …`, `copy id … collides with a live copy`, `no add packet for …`,
  `attack/interact on unknown copy id …` (warn, a bookkeeping defect)
- `entity copy pass failed for …` (error)
- `loaded N patterns from …`, `wrote … built-in patterns` (info, once per start)

So a log taken during normal play should contain only the lines above: **anything else at info is a regression**,
and worth reporting as such — the point of the split is that a busy log now means something.

**Two clients are needed** for `E-07` and `R-06`. For the rest, one client is enough and the second player can
be a second window on the same machine.

**Creative mode** is assumed (`/gamemode creative`) for the block and entity cases; `R-02` and `S-01` are about
death and movement and can be creative or survival, as noted.

**The demos.** `abyss:stack` is five acyclic bands (`abyss:layer0..4`), `abyss:cycle` is the same five cyclic,
`backrooms:spiral` is a single cyclic band. Every band level is 64 tall. The bands contain **shafts** — columns
with no floor at all, punched through a whole layer — which several cases below depend on.

**Getting into a band.** Use the mod's own `/warp` (run it with no arguments to see its parameter list). Note
which band you are in: `F3` shows the configured `window_dimension`, not the band level, so use
`/listdimensionplayers`, or with debug on the log's `window for <player> is ...` line.

## Index

| ID | Case | P | Result |
|---|---|---|---|
| R-01 | Normal death stays in the band | P1 | |
| R-02 | Void death stays in the band | P1 | |
| R-03 | Death in a shaft uses the death spot | P2 | |
| R-04 | A spawn point in the same band is kept | P2 | |
| R-05 | Respawn lands standing, never in a block or mid-air | P1 | |
| R-06 | Respawn watched from the band above | P2 | |
| R-07 | Repeated deaths do not drift | P3 | |
| S-01 | Seam crossing under latency | P1 | |
| S-02 | No "moved too quickly" kick | P1 | |
| S-03 | Crossing during a window recentre | P2 | |
| S-04 | Jumping back and forth on the seam | P2 | |
| S-05 | Falling out of the bottom of an acyclic pattern | P3 | |
| B-01 | Placing blocks across the seam | P1 | |
| B-02 | Standing on a block just below the seam | P1 | |
| B-03 | Breaking a block across the seam | P2 | |
| E-01 | An entity in an adjacent band is at the right Y | P1 | |
| E-02 | A copy disappears when it leaves the view | P1 | |
| E-03 | Attacking a copied mob | P1 | |
| E-04 | A moving mob in another band | P2 | |
| E-05 | An entity outside its band's block range | P2 | |
| E-06 | Metadata changes propagate to a copy | P2 | |
| E-07 | Player copies, with two clients | P2 | |
| E-08 | Passengers and leashes | P3 | |
| C-01 | `window_dimension` per pattern | P2 | |
| C-02 | View and simulation distance per dimension | P2 | |
| C-03 | The spiral shows one mob three times | P2 | |
| C-04 | A band with a lot of entities | P3 | |
| G-01 | The metadata log stays quiet | P2 | |
| G-02 | `backrooms/spiral` cycle behaviour | P3 | |
| G-03 | Warp permission | P2 | |
| G-04 | A failed warp leaves the player usable | P2 | |

---

# R — Respawn

The change under test: after a death in a band, the player must come back **in that band**, standing on a
floor. Before the fix, every death put the player on the floor of the band above, because a band's respawn
position falls back to the overworld's and the window read the resulting Y as an upward crossing.

The log line to look for, once per death:

```
[abyss] respawn in <band path> moved from <vec> to BlockPos{x=…,y=…,z=…} (<min>..<max>)
```

`<band path>` is the **path only**, so `layer2` rather than `abyss:layer2`. `<min>..<max>` is the band's slice
in its own level, which for every demo band is `0..63`. The `y` of the target must be inside the slice, and the
`BlockPos` must be somewhere a player can stand.

### R-01 — Normal death stays in the band

**Priority:** P1
**Setup:** `/gamemode survival`. Warp into `abyss:stack` band 2 (`abyss:layer2`). Confirm you are in position 2
— `/listdimensionplayers`, or with debug on, `window for <you> is 0:layer0@0 | 1:layer1@0 | 2:layer2@0* ...`.
**Steps:**
1. Make sure the floor under you is normal terrain, not a shaft.
2. `/kill` (or let a mob kill you).
3. Respawning.
4. Read the position: `F3`, or look at the block you are standing on.

**Expected:**
- You respawn in **layer2**, on its floor, in the air at y=4 or on whatever surface is under the spawn column.
- You are **not** on the floor of layer3, and not in the ceiling (y=60..63).
- You are standing, not falling.

**Log:** exactly one `[abyss] respawn in layer2 moved from … to … (0..63)`, with `y` in `4..59`.
If the line is missing entirely, the position was already standable and was left alone — acceptable only if
you had set a spawn point there (`R-04`).

**Notes:** this is the case the change exists for. If it fails, capture the line and the `F3` position.

### R-02 — Void death stays in the band

**Priority:** P1
**Setup:** survival, in a band. The bottom of an acyclic pattern is the place to do it: warp to `abyss:stack`
band 0 and find a shaft, or dig down through the floor.
**Steps:**
1. Fall until you die (void damage, or fall damage — either is fine).
2. Respawn.

**Expected:** you respawn in the band you died in, standing on its floor — **not** at y=0 of some other band,
and not on the floor of the band above.

**Log:**
- `[abyss] <you> fell out of abyss:stack at y=<…>; the window no longer applies` — expected if you fell out of
  the bottom of the pattern.
- one `[abyss] respawn in layer0 moved from … to … (0..63)`.

**Notes:** this pair is the regression from an earlier build, where falling out of the bottom was "recovered"
to y=0.01 and the void could never kill you.

### R-03 — Death in a shaft uses the death spot

**Priority:** P2
**Setup:** find a shaft column in a band — no floor for a whole layer. Creative is handy: stand in the shaft
at some height, then switch to survival.
**Steps:**
1. Die in the shaft (any cause).
2. Respawn.

**Expected:** you respawn at the place you died, on the nearest standable ground, rather than falling through
the shaft again.

**Log:** `[abyss] no floor near the respawn point in layerN; using where the player died`, then the usual
`respawn in … moved from …` line.

**Notes:** if that line is absent and instead you see
`[abyss] no place to stand in layerN (0..63); respawning free at …`, the death spot itself was not standable
(you were mid-fall in the shaft) and the search fell back one step further. That is the known limitation: you
land free at the top of the slice and fall. Report it only if the death spot was genuinely standable.

### R-04 — A spawn point in the same band is kept

**Priority:** P2
**Setup:** in a band, on solid ground.
**Steps:**
1. `/spawnpoint` at a spot you can recognise (or place a bed and sleep if beds are enabled).
2. Walk some distance away and die.
3. Respawn.

**Expected:** you respawn **at the spawn point you set**, not at the band's default floor. The position is in
the air block above the spawn block, standing.

**Log:** no `[abyss] respawn in …` line is expected here — a position that is already inside the slice and
standable is left exactly as it was.

**Notes:** a spawn point set in a **different slice of the same level** is deliberately overruled by the band
you died in, per the mod's "same band you died in" convention. If your world has one level used twice in a
pattern, test that case and decide whether the behaviour is the one you want.

### R-05 — Respawn lands standing, never in a block or mid-air

**Priority:** P1
**Setup:** any band.
**Steps:**
1. Die in a normal spot.
2. On respawn, before moving, look straight down and check the block under your feet.
3. Repeat 3–4 times in different columns, including one under a low ceiling if you can find one.

**Expected:** every time, you are on a surface: the block under your feet is solid and the block you occupy
and the one above are free. Never inside the ceiling shell, never in mid-air.

**Log:** the `y` of the target in `respawn in …` is at most 62 — a band is 0..63 and a player needs head room,
so feet at 63 would be invalid.

**Notes:** this is the defect that the first attempt at the fix introduced: it clamped to y=63, which is inside
the ceiling, and then accepted the first non-solid block, which was mid-air.

### R-06 — Respawn watched from the band above

**Priority:** P2
**Setup:** two clients. Player A in `abyss:layer2`, player B in `abyss:layer3` — B can see A as a copy below.
**Steps:**
1. A dies. Both watch the moment of respawn.
2. A respawns.

**Expected:**
- B's view of A jumps straight to the respawn point, with no intermediate position in the wrong band.
- A does not flicker, is not teleported twice, and does not briefly appear at the old position.

**Log (A's connection):** one `respawn in layer2 moved from …`, one `teleport acknowledged`. No
`seam crossed` line at respawn time — a respawn is not a crossing, and a `seam crossed` immediately after
respawn is the symptom of the original bug.

### R-07 — Repeated deaths do not drift

**Priority:** P3
**Setup:** survival, one band.
**Steps:**
1. Die and respawn five times without changing anything.
2. Each time, note the `y` you respawn at.

**Expected:** the same result every time. No walk downward or upward across attempts, no alternating between
two bands.

**Log:** five `respawn in …` lines, all naming the same band and a `y` inside `4..59`.

---

# S — Seam crossing under real latency

The seam is where one band ends and the next begins. Crossing it is a server-side teleport of 64 blocks in Y
while the client interpolates, so it is the case most likely to behave differently at 5 ms than at 100 ms.
`CROSS_MARGIN = 0.05` is the hysteresis that keeps a player from oscillating at the boundary.

### S-01 — Seam crossing under latency

**Priority:** P1
**Setup:** a client with realistic latency — connect over the network, not `localhost`. Warp into
`abyss:stack` band 0 and stand near the seam (the boundary is at the top of the band).
**Steps:**
1. Walk and jump up across the seam into band 1, ten times.
2. Walk back down across it ten times.
3. Include one jump exactly on the boundary, and one where you stop moving while straddling it.

**Expected:**
- Each crossing is one smooth transition; no bounce back and forth.
- You do not get pushed back down after arriving, and you do not have to walk twice to make it stick.
- The band you see after crossing is the one you arrived in.

**Log (per crossing):**

```
[abyss] offset for <you> <old> -> <new> (position …, start …, slot …)
[abyss] seam crossed abyss:layer0 -> abyss:layer1 at client y=…; local … -> … (onGround …, window … -> …); <n> ms
[abyss] window for <you> is 0:layer0@0 | 1:layer1@0* | 2:layer2@0 (position 1, start 0, own slot 1)
```

**Notes:** count the lines against the number of crossings. Two `seam crossed` lines for one physical crossing
means the hysteresis is not holding at that latency — record the client `y` of both.

### S-02 — No "moved too quickly" kick

**Priority:** P1
**Setup:** the same remote client. This is the case that localhost cannot show: the server's Y jumps a band
while `movedDist` is measured in server space.
**Steps:**
1. Cross seams repeatedly, at speed — sprint-jump through several in a row.
2. Do it while moving diagonally and while falling.

**Expected:** no `moved too quickly!` kick and no rubber-band correction back to the pre-crossing position.

**Log:** no `moved wrongly!` / `moved too quickly!` lines from the server. Any occurrence is a failure of this
case and should be reported with the `seam crossed` line that precedes it.

### S-03 — Crossing during a window recentre

**Priority:** P2
**Setup:** remote client, a band whose window needs recentring when you cross.
**Steps:**
1. Cross a seam while turning the camera quickly.
2. Cross a seam immediately after landing from a fall.

**Expected:** no floating point, no "world slides" effect, no missing chunks in front of you after arrival,
and the position you end at matches the position the server thinks you are at (walk a step and see if you
snap).

**Log:** one `seam crossed` and one `teleport acknowledged`; a `window was out of step with …; re-derived, no
teleport` warning is acceptable once, but repeating it on every crossing is not.

### S-04 — Jumping back and forth on the seam

**Priority:** P2
**Setup:** stand at the seam so that a jump crosses upward and landing brings you back down.
**Steps:**
1. Jump repeatedly on the spot for 20 jumps.

**Expected:** the crossing fires on each jump and each landing, alternating bands cleanly — never twice in the
same direction, never a jump that crosses and then immediately crosses back without you returning.

**Log:** alternating `layer0 -> layer1` and `layer1 -> layer0` lines, one per jump/landing, with no duplicates.

### S-05 — Falling out of the bottom of an acyclic pattern

**Priority:** P3
**Setup:** `abyss:stack` band 0, survival.
**Steps:**
1. Drop through a shaft or dig out the floor and fall well past the bottom of the band.
2. Do not use commands; let the void take you.

**Expected:** you fall indefinitely (the window no longer applies) and the void kills you. You are **not**
teleported back to the floor of the band.

**Log:** `[abyss] <you> fell out of abyss:stack at y=<…>; the window no longer applies`, once.

---

# B — Blocks and the seam

Cross-band block interaction is the interaction case with the most history: placement is derived from the
*clicked* band, and block updates are captured at send time. Both are regression-prone.

### B-01 — Placing blocks across the seam

**Priority:** P1
**Setup:** creative, standing in a band right below the seam, looking up at the ceiling of your band or into
the band above.
**Steps:**
1. Place blocks on the ceiling of your own band, on the floor of the band above if reachable, and directly at
   the seam height (y=63 / y=64 boundary).
2. Place a block, break it, and place another in the same spot.
3. Place a block while standing at the seam itself, at your feet.

**Expected:**
- Every placement lands where you clicked, including at the boundary height.
- No `height limit is 63` refusal.
- A block placed at your feet while you stand on the seam appears, and stays.
- Nothing vanishes and reappears.

**Log:** `[abyss] place attempt: item <item> at hit <pos> face <face> in <band> (y bounds <..>)-(<..>) | own band <band>, target <band>, player y=<…>`
then a `place result: <item> at <pos> in <band> -> <Success[…]|Fail[]>` line, and an
`[abyss] update <band> at server <pos> -> client <pos> state <block>` for each change. A `Fail[]` where the
target spot is clearly free is a failure of this case. `[abyss] place across seam: …` is the normal line for a
placement that lands in a level other than yours; `[abyss] place across seam refused: no band at position …`
means the mod could not decide which band the click belonged to, which is a failure.

### B-02 — Standing on a block just below the seam

**Priority:** P1
**Setup:** creative (so you can build), place a single block just below the seam and stand on it.
**Steps:**
1. Stand on the block, exactly at the seam, and jump.
2. Land back on the same block. Repeat ten times.
3. Try placing another block at your feet while standing there.

**Expected:** the block under you never disappears, you are never squeezed off it, and the position the server
believes matches the client's — a step forward should not snap you back.

**Log:** no repeated `update … state air` then back to the block for the same position within a tick or two.
A block that blinks in the log is the symptom: the block is being un-placed and re-placed, and the squeeze
follows from it.

**Notes:** an earlier build's version of this case was blamed on block placement; it was established to be a
collision/mistaken-position problem instead. Watch whether the log shows the player's band changing at the
moment the block blinks.

### B-03 — Breaking a block across the seam

**Priority:** P2
**Setup:** creative, a block in the band above that you can reach from yours.
**Steps:**
1. Break a block in the neighbouring band.
2. Break a block in your own band at the same time.

**Expected:** both break, the drop (if any) appears, and any entity nearby updates — see `E-06` for the entity
side of this.

**Log:** `[abyss] break dispatched to <band> at <pos> (player in <band>, y=<…>)` followed by an
`update … state air` for the same position. If two `update` lines are sent for one break, note it. The same
generic line covers block use, printed as `use dispatched`.

---

# E — Entities and copies

A visible band is composed for the client, so an entity in an adjacent band is sent as a **copy**: a patched
entity id and UUID, positioned at the band's offset from yours. The copies are the most intricate part of the
mod, and most of what can go wrong is visible in the client rather than in the log.

### E-01 — An entity in an adjacent band is at the right Y

**Priority:** P1
**Setup:** `abyss:stack`, a glow item frame or any mob placed in band 1 while you stand in band 0. Item frames
are the easiest to judge because they are small and static.
**Steps:**
1. Stand in band 0 and look at an item frame in band 1 — either above you or below, depending on the band.
2. Compare its apparent height with the geometry (it should sit 64 blocks away, in the neighbouring band's
   space, not at your own height).

**Expected:** the frame (or mob) appears at the correct height in the neighbouring band, attached to the right
wall, not floating at your own level and not 64 blocks off.

**Log:** `[abyss] spawn copy <id> (uuid <uuid>) for <entity> -> <band> slot <n> offset <n> | server (<x>, <y>, <z>) client (<x>, <y>, <z>)`,
where the `server` and `client` coordinates differ by exactly the offset.

**Notes:** this also covers the case that was "layer 1 is invisible from layer 1": if a band's own entities do
not show while you are in that band, report it as a failure of `E-01` and say which band.

### E-02 — A copy disappears when it leaves the view

**Priority:** P1
**Setup:** as `E-01`.
**Steps:**
1. Look at the copy, then walk away until it is out of your view.
2. Come back.

**Expected:** the copy disappears when it leaves the view and reappears at the same place when you return.
No ghost hitboxes (`F3+B`), no duplicate copies, and no copy left behind after it disappears.

**Log:** `[abyss] drop copy <id> at position <n> for <entity> (left the view)` when leaving, and a fresh
`spawn copy` when returning.

### E-03 — Attacking a copied mob

**Priority:** P1
**Setup:** creative, a mob in an adjacent band that you can reach across the seam.
**Steps:**
1. Hit the copy.
2. Repeat with a mob in your own band for comparison.

**Expected:**
- The mob dies (or takes damage) **in its own band**.
- The copy vanishes; no copy is left standing.
- Death particles appear in the right band.
- Creative works instantly; in survival the hit registers rather than passing through.

**Log:** `[abyss] hurt flash sent for copy <id> of <entity>`, `[abyss] attack dispatched [<thread>]: copy <id> -> <entity> in <band> (offset <n>, player y=<…>, own band <band>)`, and
`[abyss] attack on copy <id> completed: <entity> health <a> -> <b> (alive <bool>)` — then `drop copy … (entity removed)`.

**Notes:** the "cannot hurt a mob across a band" defect came from dispatching the attack on the netty thread; the
thread is named in the `attack dispatched [...]` line and must be `Server thread`. A hit that does nothing, with
`attack dispatched` but no `completed`, is that defect returning. `[abyss] attack on unknown copy id …` means the
copy table and the client disagree about what you hit — report it with the preceding `spawn copy`/`drop copy` lines.
For a mob that has an interaction (villager, item frame), the equivalent line is
`[abyss] interact dispatched [<thread>]: copy <id> -> <entity> in <band> (hit <pos> in copy frame -> <pos>)`.

### E-04 — A moving mob in another band

**Priority:** P2
**Setup:** debugging enabled for the mod, a mob in an adjacent band that walks (a zombie chasing something, or
a mob being pushed).
**Steps:**
1. Watch the mob move from your band.
2. Compare its motion with the same mob in its own band, seen by a second player if available.

**Expected:** the copy tracks the mob smoothly. Minor jitter on a vehicle seen from another band is documented
and not a failure; a copy that lags by a block or that stops when the mob turns is.

**Log:** `[abyss] move copy … by delta -> client …` (debug) for normal movement, and
`[abyss] copy <id> for <entity> jumped too far for a delta; synced absolutely to …` when a mob moves more
than a delta can carry. Frequent absolute syncs are worth reporting.

### E-05 — An entity outside its band's block range

**Priority:** P2
**Setup:** a mob at y=65 (or above the band's ceiling) inside a band — push one up, or spawn one with
`/summon` at that height.
**Steps:**
1. Watch the mob.
2. Look at it from the adjacent band, and from its own band.

**Expected:** the mob is **dropped** from both views while it is outside its band's block range, and it cannot
enter the seam. It should reappear when it falls back into the band.

**Log:** no copy spawn for it while it is out of range; no `seam crossed` caused by it.

**Notes:** this is a deliberately unresolved inconsistency rather than a bug — but it should never produce a
visible entity hanging in the seam.

### E-06 — Metadata changes propagate to a copy

**Priority:** P2
**Setup:** a sheep and an item frame in an adjacent band; debugging on.
**Steps:**
1. Dye the sheep, shear it.
2. Rotate the item in the frame.
3. Watch both from your band.

**Expected:** colour, sheared state and frame rotation all update in the copy.

**Log:** `[abyss] metadata of copy <id> for <entity> changed to …` (debug) for each change.

**Notes:** position-carrying metadata beyond item frames and end crystals (fishing hook, area effect cloud) is
a documented gap — see `ABYSS_ENTITIES.md` §14.

### E-07 — Player copies, with two clients

**Priority:** P2
**Setup:** two clients, in different bands, with one player able to see the other across a seam.
**Steps:**
1. Have B stand in band 1 while A is in band 0, in view.
2. Check A's tab list while B is invisible-band to A.
3. Have B walk, then disconnect.

**Expected:**
- B's copy appears where B really is, with B's name.
- The copy stays **out of the tab list** while it is a copy.
- The copy disappears on disconnect, and no ghost remains.
- Reconnecting does not leave a duplicate.

**Log:** `[abyss] player info for copy <id> (<uuid>) of <player>`, `spawn copy …`, and
`[abyss] dropped all <n> copies of <player>` on disconnect.

### E-08 — Passengers and leashes

**Priority:** P3
**Setup:** a mob on a boat, and a leashed mob, in an adjacent band.
**Steps:**
1. Look at both from your band.
2. Move the boat, and lead the leashed mob around.

**Expected:** riders stay attached and sit at the right height; the leash is drawn to the right holder; no
floating or detached rider. Minor interpolation jitter on a moving vehicle is documented and not a failure.

**Log:** `[abyss] copy <id> of <entity> carries [<ids>]` on spawn, and
`[abyss] copy <id> of <entity> leashed to copy <id>` for the leash.

---

# C — Per-band configuration

### C-01 — `window_dimension` per pattern

**Priority:** P2
**Setup:** `backrooms/run/config/AbyssPatterns.json`, which is a JSON array of patterns; each pattern may carry
`window_dimension`.
**Steps:**
1. Note the `window_dimension` of the pattern you are in — `abyss:cycle` uses `abyss:cycling`,
   `backrooms:spiral` uses `backrooms:level_922xxx`, `abyss:stack` uses the default `abyss:window`.
2. Warp into that pattern and open `F3`.
3. Switch to a pattern with a different value and check again.

**Expected:** the dimension shown in `F3` is the pattern's configured key, and it changes when you change
pattern. A pattern with no `window_dimension` shows `abyss:window`.

**Log:** `[abyss] loaded N patterns from .\config\AbyssPatterns.json` at start, naming the count you expect.

**Notes:** the key is per **pattern**, not global. If setting it at the top level of the file appears to work,
that is a coincidence worth checking against this case.

### C-02 — View and simulation distance per dimension

**Priority:** P2
**Setup:** two patterns configured with different distances, or use `/distance` to set one band's distances.
**Steps:**
1. Enter a band with a small view distance; note how far you can see and how far entities move.
2. Warp to a band with a large one.

**Expected:** the view distance and simulation distance change with the band; chunks outside the new radius
are dropped and inside ones loaded. The `F3` render distance should match the configured value.

**Log:** no `[abyss] simulation ticket for … was missing at …; re-adding with radius …` warnings unless a band
was genuinely unloaded; `[abyss] not entity-ticking for …; a mob there will not walk or take damage` means
simulation did **not** apply to that band, which would be a failure.

### C-03 — The spiral shows one mob three times

**Priority:** P2
**Setup:** `backrooms:spiral`, one band level used three times in the window; one mob in it.
**Steps:**
1. Stand in the spiral with the mob in view.
2. Look up and down.

**Expected:** the same mob appears **three times**, 64 blocks apart, once in each visible copy of the band.
Each copy is a separate entity on the client, and hitting any one of them affects the real mob.

**Log:** three `spawn copy …` lines for the one entity, at offsets 0, 64, 128, and a
`hit point … in copy frame -> … in <band> (offset …)` (debug) when you hit one.

### C-04 — A band with a lot of entities

**Priority:** P3
**Setup:** fill an adjacent band with a few hundred entities (`/summon` in a loop, or a mob farm).
**Steps:**
1. Watch the tick time (`/debug` or the server's `spark` profiler) with the band in view.
2. Walk away and back.

**Expected:** no visible stutter, no dropped copies, no collisions.

**Log:** `[abyss] copy id <id> for position <n> collides with a live copy; skipping <entity>` or
`[abyss] no add packet for <entity>; copy <id> not made` would indicate the copy table is under pressure;
neither should appear in normal play.

**Notes:** the copy loop is `O(entities in view) × 2 bands` per player per tick; this case exists to find the
count at which that becomes a problem, per `ABYSS_ENTITIES.md` §14.

---

# G — Regression

Older fixes that are easy to undo, and one that has no test yet.

### G-01 — The metadata log stays quiet

**Priority:** P2
**Setup:** any band, normal play.
**Steps:**
1. Play for a few minutes with entities in view — item frames, mobs.
2. Check the log.

**Expected:** no repeating metadata spam. Metadata changes may be logged at `debug` only.

**Notes:** this was explicitly requested to be silenced; a returning `metadata of copy` line at `info` is a
failure.

### G-02 — `backrooms:spiral` cycle behaviour

**Priority:** P3
**Setup:** `backrooms:spiral`, survival.
**Steps:**
1. Cross the seam upward repeatedly — the spiral wraps, so you should return to the same band.
2. Cross downward the same number of times.

**Expected:** you return to where you started. No drift, no accumulating offset, and the window stays
consistent (`position` and `start` in the `window for` line return to their original values).

**Log:** `window for …` lines; after a full cycle the position and start should match the first.

### G-03 — Warp permission

**Priority:** P2
**Setup:** a non-op player, and a band whose minimum privilege has been set with `/banwarp`.
**Steps:**
1. As the non-op, try `/warp` into the restricted band.
2. As an op, do the same.

**Expected:** the non-op is refused with a message and is **not** moved; the op succeeds.

**Notes:** `ServerPlayerMixin` has a standing `//TODO Check if warp is allowed`. Until that is implemented this
case is expected to fail for the non-op; run it so the failure is known rather than assumed, and record the
actual message.

### G-04 — A failed warp leaves the player usable

**Priority:** P2
**Setup:** a warp target that fails — a dimension that does not exist, or one deleted while you are aiming at
it (`/deleteworld`).
**Steps:**
1. Attempt the warp.
2. Check your inventory, your position, and whether you can move.

**Expected:** whatever the outcome, you are left in a usable state: in one world, with your inventory intact,
able to move and to warp again.

**Notes:** `ServerPlayerMixin` and `Util` both carry `//TODO Any way to rollback?` — a half-applied warp is a
known unfinished area. Record what actually happens, including any items lost.
