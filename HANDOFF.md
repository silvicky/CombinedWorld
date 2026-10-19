# Handoff — the Abyss (vertical concatenation)

Date: 2026-10-19. Branch `expwy`. Everything below is verified against the tree unless marked otherwise.

## State

**Committed** (`69b4da2`, on top of `e63bff0`): the whole abyss foundation — `AbyssPattern`,
`AbyssPatterns`, `AbyssWindow`, `AbyssChunkComposer`, the three demos (`abyss:stack`, `abyss:cycle`,
`backrooms:spiral`), `SpiralRule`, both dimension types, `StagedDimensions`.

**Uncommitted** (this session's later work, 5 files, +669/−90):

| File | What changed |
|---|---|
| `AbyssWindow.java` | slot-resolved inbound conversion (`resolveY`), `posSlots`, `nearestClientY`, `imageYs`, `actionLevel`, `sendBlockUpdate` |
| `PlayerMixin.java` | reach measured in the window frame (`item_storage$toClient`, boxes at nearest image) |
| `ServerCommonPacketListenerImplMixin.java` | block updates sent at **every image**; all outbound Y/section offsets |
| `ServerGamePacketListenerImplMixin.java` | both `movedDist` sites + movement statistics in the window frame |
| `ServerPlayerGameModeMixin.java` | **new body**: cross-level dispatch of break and use |

**Untracked:** `ABYSS.md` (the spec) and `.codewhale/` (harness state — do not commit).

## Verified

`gradlew compileJava --offline` → BUILD SUCCESSFUL, `:core` and `:backrooms`, after the last edit.
**Nothing has been run since.** All runtime claims below are expectations, not observations.

## Where the work stopped

The last report was: cross-band **same-level** break works (`backrooms:spiral`), cross-band
**different-level** still fails (`abyss:stack`).

Diagnosis: `handleBlockBreakAction` / `handleUseItemOn` begin with
`player.isWithinBlockInteractionRange(pos, 1.0)`. That reaches `PlayerMixin`, which used
`AbyssWindow.nearestClientY`, which filtered images to the player's own level — so a block in the band above
resolved a whole band away, and the action returned at "too far" **before dispatch ran**.

Two edits made for it, unverified:

1. `nearestClientY` now considers **every** band (it is a measurement in the client's frame, and the client
   draws all bands as one dimension), gated only by `sourceY <= y < sourceY + BAND_HEIGHT`.
2. `actionLevel`'s Y-scan fallback dispatches only when **exactly one** band contains the Y, since every band
   spans 0..63 and the scan is otherwise ambiguous.

## Invariants that must not be broken

- `slot = position - windowStart` must stay in **0..2**. Recentre on every crossing; a player in slot −1/3 is
  in a band that is not being sent (this caused "cannot see the layer above until I approach it").
- The crossing is one transition under `seamTeleports`: `setWindow` → `resendChunks` → `flushChunks` →
  `sendTeleport`. The flag must span all of it; `window()` re-derives from `player.level()` otherwise.
- A band height is locked by the single window dimension type: `WINDOW_HEIGHT / WINDOW_LAYERS = 64`.
- Composition must emit exactly `WINDOW_SECTIONS` (12) sections; the count is not in the packet.
- Light masks are indexed `slot * SECTIONS_PER_BAND + i + 1`; the `+1` is the section below `minY`.

## Open questions (need a decision, not code)

1. **Entity mapping.** `EntityPositionRemapper` shifts X/Z only. Does an entity visible in several bands get
   sent once, or once per visible band? That decides whether Y goes into the remapper at all. You deferred
   this until the rules are agreed.
2. **Upward crossing.** `travel` still tests `local >= BAND_HEIGHT`, which a 64-tall level cannot reach
   (`y` caps at 63). You said this was fixed; the tree still shows the old test, so either the fix is
   elsewhere or it has not landed — worth confirming before building on it.
3. **Cross-level side effects.** The dispatch swaps `ServerPlayerGameMode.level` only. Anything reading
   `player.level()` during the action (spawn logic, some block-edit hooks) still sees the player's band.
   Swapping the player's level is deliberately not done: it drags entity and chunk tracking with it.

## Known gaps

- Block entities are not composed (`BlockEntityInfo` is package-private, private constructor).
- `ClientboundChunksBiomesPacket` is not composed and would overwrite a window column.
- Tickets are shared between players whose windows overlap (`addTicket` dedupes on type + level).
- Light that arrives after a column was sent is not re-sent (band renders dark, not absent).
- Only the player's own band simulates; the visible bands above and below are loaded, not ticking.

## Next actions

1. Run `abyss:stack`, break and place a block in the band above. If it still fails, log one line in
   `handleBlockBreakAction` HEAD with `pos`, `posSlots.get(player).get(pos)` and
   `actionLevel(player,pos)` — that separates "reach rejected" from "dispatch wrong".
2. If placement fails but breaking works, check `BlockPlaceContext`'s target: it is a `c2s(s2c(pos).relative)`
   round trip, which cancels, but the *placed* block's update must reach the target band via the exit hook.
3. Commit `ABYSS.md` and the five modified files once cross-level works.
