# ⚠ DO NOT REMOVE — SCOPE & DISCIPLINE
**Scope:** Add the **RED/ORANGE conformity gate** to modeller edits — the spine's "planner's gate" (MRP exception
messages). After a move/ride, CHECK the result: RED = hard violation the edit BROKE, ORANGE = soft accept/ignore.
This turns the modeller from a *mover* into a *planner*. **Read the log after every run.** Honour until ✅ DONE.
**Repo:** code = bim-ootb worktree `/tmp/wt-*`; spec = this file (bim-compiler, prompts/ is gitignored = on-disk only).
**NON-INVENT:** the gate reads only MEASURED post-move AABBs + the RECOVERED edges; fabricates no rule. It flags
what the EDIT changed (delta-based), never pre-existing as-extracted overlaps. Builds on §ARC-1 + §SDG-CASCADE.

## WHY (vision gap, grounded 2026-06-29)
Grep confirmed: **zero conformity check in the modeller edit path**. You can drag a wall through another wall, or
move a door out of its opening, and nothing flags it. The SDG spine's whole thesis = "MRP with geometry on its
edges **+ a planner's gate**": RED = hard stop (door-crush / wall clash / UBBL), ORANGE = soft accept/ignore =
the MRP exception message. A ride you can't validate is just motion. User chose this as the next slice.

## §GATE-1 SPEC — RED/ORANGE conformity on a committed edit
Fires AFTER the §SDG-CASCADE ride in `commitMove` (the SAME seam), over the moved set (host + riders). DELTA-based:
compare BEFORE vs AFTER AABBs so it flags only what the edit changed (an already-overlapping as-extracted corner is
NOT a violation — the building shipped that way). All geometry; reuses the `cross_edges` overlap convention:
`ov[k] = min(maxK_a,maxK_b) − max(minK_a,minK_b)` ; all 3 ov[k]>0 ⇒ volumetric interpenetration; penetration
depth = `min_k ov[k]`. Face-touch (ov≈0) = expected adjacency, NOT a clash.

### RED (hard — the edit broke a hard constraint)
1. **wall/element CLASH** — a moved element now volumetrically interpenetrates a NON-related element by `> CLASH_TOL`
   AND deeper than before (new or worsened). EXCLUDE pairs that are legitimately overlapping by relation:
   hosted-by (door inside wall is expected), abuts/anchored (face-touch). depth reported.
2. **door OUT OF HOST** — a hosted filling whose centre left its host's footprint (was inside the host AABB before,
   outside after). Catches "moved the door off its wall". (Moving the HOST rides the door → stays in → no RED.)

### ORANGE (soft — accept or ignore, the exception message)
3. **CLEARANCE tight** — a moved element came within `CLEARANCE` face-gap of a non-related element (not
   interpenetrating) AND the gap DECREASED. Default `CLEARANCE` = measured residential standard (~0.5m, from the
   rules DB where a discipline pair has one) else a geometric default; the value is a PARAMETER, never invented.

### Output + UX
`SdgGate.evaluate(before, after, moved, rel, opts) → {red:[{kind,a,b,depth}], orange:[{kind,a,b,gap}]}` (pure).
`commitMove` logs `§GATE red=N orange=M` per edit; RED → toast + highlight the offending pair red; ORANGE → toast
accept/ignore. RED does NOT auto-revert (the signed op stands — the gate REPORTS, the user decides; matches the
spine's "no auto-converge, user-gated"). A later slice can offer one-click revert.

## §GATE-1 WITNESS — W-SDG-GATE (claim FIRST, node, REAL SampleHouse)
Each scenario asserts the canvas-visible fact (no compute-then-pass):
1. **clean move → no flag** — nudge a wall a small amount into open space ⇒ red=0 orange=0.
2. **RED clash** — move a wall hard INTO another wall ⇒ red contains a clash{a,b,depth>CLASH_TOL}; the pair is the
   two walls; depth ≈ the interpenetration.
3. **delta-honest** — an as-extracted pre-existing overlap (corner walls) with NO move between them ⇒ NOT flagged
   (gate flags the edit's delta, not the building's shipped geometry).
4. **RED door-out-of-host** — move a DOOR alone far from its wall ⇒ red contains door-out{filling,host}; moving the
   HOST instead (door rides) ⇒ no door-out (regression tie to §SDG-CASCADE).
5. **ORANGE clearance** — move a wall to within < CLEARANCE of a neighbour (gap shrinks, no overlap) ⇒ orange
   contains clearance{a,b,gap<CLEARANCE}; same move when already far ⇒ no orange.
6. **non-invent** — gate reads only the passed AABBs + edges; no rule fabricated; CLEARANCE is an explicit param.

Browser §-smoke (headless): move a wall into a neighbour → `§GATE red=` logged + the pair highlights red.

## STATUS — §GATE-1 ✅ DONE+WITNESSED 2026-06-29 (bim-ootb PR #574, sw v17→v18, auto-merge armed)
- [x] `sdg_gate.js` pure `evaluate(before, after, moved, rel, opts) → {red, orange}` (dual-export); reuses
      cross_edges overlap convention; delta-based; hosted-by/abuts = expected contact; hostOf → door-out.
- [x] wired into `commitMove`: `_gateBoxes()` snapshots AABBs before, `_runGate` after the cascade; `_gateRel()`
      builds related/hostOf from swXEdges + the §ARC-1 bridge.
- [x] RED/ORANGE toast (MRP exception message) + emissive highlight of offending pair + §GATE log + status tag.
      REPORTS only — the signed op stands, user decides (no auto-revert).
- [x] **W-SDG-GATE 6/6** (node, real SH: clean→none; RED clash; delta-honest pre-existing overlap ignored;
      door-out vs ride-the-host; ORANGE clearance band; non-invent param). A4 cross-check: cascade rides ALL a
      host's fillings (else the gate correctly flags the left-behind one).
- [x] **§GATE-SMOKE 6/6** (headless: clean no flag; drive element onto another → 3 RED clashes + toast).
- [x] cascade-smoke regression 6/6 (door-rides path unaffected, no false flag). sw v18. test hooks
      window.__commitMove + window.__lastGate.

## NEXT (after the gate)
- one-click REVERT of a RED edit (today: reports, op stands) · UBBL rules (corridor width / door-swing) as named
  RED checks (currently clash + door-out + clearance) · ORANGE backprop (suggest the fix hop-by-hop) · gate the
  STRETCH engine (roadmap slice 2) the same way · scale: _gateBoxes is O(all); moved-vs-all is O(moved×all) — fine
  for SH/Duplex, add an rtree/sweep prune for Terminal-scale.

## NEXT (after the gate)
- one-click revert of a RED edit · UBBL rules (corridor/door-swing) as named RED checks · ORANGE backprop
  (suggest the fix hop-by-hop) · gate the STRETCH engine (slice 2 of the roadmap) the same way.
