# SPEC — Grid-drag Green/Orange pre-drag preview (opt-out, non-default)

```
# ⚠ DO NOT REMOVE
SCOPE: extracted from prompts/GRID_PREDRAG_PREVIEW_SAVE_COMPLETEIT.md §A (full design-dialogue context there —
read it first if picking up cold). Independent of MODELLER_SAVE_COMPLETEIT.md — can be built in parallel, no
shared dependency either direction. Watchdog-tracked, see closing note.
```

## SEMANTICS (exact, don't drift)
- **Orange = default.** The dragged wall and everything inside its span follows proportionately. Openings
  (doors/windows) shift position to stay well-spaced but keep their OWN dimensions fixed — already shipped via
  `sdg_cascade.js:37` `stretchRide()`; the `door-crush` RED (`DOOR_WIDTH_CRUSH_GATE.md`) only catches the host
  shrinking past the opening's fixed size, it is not evidence that dimensions get distorted day-to-day.
- **Green = "I am good, not following your drag" — opt-out, non-default.** Ctrl+click an orange element to
  flip it green (excluded from this drag); ctrl+click again toggles back to orange. Bidirectional, live,
  per-element, scoped to the current drag session only.
- **Deferred, not this build:** a future config for which polarity is default per user. Hardcode default-orange.

## WHY THIS MATTERS (answers a real open question, don't re-litigate)
This is the resolved answer to `RESUME_SESSION_2026-07-04_GATE_BACKPROP.md` §OPEN item 2 ("accept/ignore UI for
ORANGE suggestions"), for the `abuts-realign` class specifically: that ORANGE only fires when proportional-follow
math pulls a real touching pair apart UNEXPECTEDLY. Pre-declaring intent (green/orange) before the drag commits
means the mismatch mostly never occurs. It does NOT retire generic `clearance` ORANGE or RED clash — those still
need `MODELLER_SAVE_COMPLETEIT.md`'s Save-time validation as the safety net.

## EXISTING BUILDING BLOCKS (verified 2026-07-05, cite before reusing)
- `modeller/grid_kinematics.js:70-101` `attachGridToElements()` + `_governed` map — classification is
  delta-independent, runs fine before a drag commits.
- `modeller/modeller.html:1495-1512` `gmTint(commands)` — live tint ALREADY ships during drag (blue=TRANSLATE,
  orange=SCALE), fed by `bonsai_gridmove.js:35-44` `computeCommands()`.
- **Known gap to fix as part of this build:** `stretchRide` (hosted-opening override) is applied only in
  `commit()` (`bonsai_gridmove.js:68-74`), NOT in the live `gmTint` preview — a door/window can show the WRONG
  tint mid-drag today. Fix this before adding the new green state, or the opening's live color will lie.
- `modeller/modeller.html:752` `_emis(mesh, hex)` — generic highlight, any hex. Adding GREEN is direct reuse.

## NET-NEW WORK
1. Fix the `stretchRide`-in-live-preview gap above.
2. Add a GREEN tint state to the live preview (today only blue/orange exist).
3. A per-drag-session override set (element id → excluded boolean), seeded empty (all orange), toggled by
   ctrl+click, consumed by `computeCommands()`/`gmTint()` to skip movement for excluded elements.
4. `commitGridMove()` must honor the same override set at commit — preview and actual commit must agree.

## DONE WHEN
1. Live preview shows correct green/orange/blue per element BEFORE and DURING a grid drag, including openings
   (fixed by item 1 above).
2. Ctrl+click toggles an element green↔orange live, both directions witnessed.
3. `commitGridMove()` respects the override set — a green element genuinely does not move on commit.
4. No regression: `witness_sdg_cascade.js`, `witness_stretch_ride.js`, `witness_e2e_stretch_ride.js`,
   `witness_e2e_gridstretch.js` all still green.

## WATCHDOG NOTE
Tracked from `prompts/FRONTEND_LANE_MASTER.md §NEW BACKLOG`. Closing session's `# DONE` appendix needs a `§`
log line per claim above — no log line, not done.
