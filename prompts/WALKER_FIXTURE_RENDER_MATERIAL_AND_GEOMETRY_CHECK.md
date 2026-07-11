<!-- Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com> · SPDX-License-Identifier: MIT -->
# WALKER FIXTURE RENDER — material/LOD + geometry-chaos investigation, and its DiscWalk-baseline question (2026-07-11)

```
# ⚠ DO NOT REMOVE
SCOPE: bim-ootb `modeller.html` (`_renderDiscWalk`, ~line 3619) + `disc_walker.js` (`place()`, the
z-coordinate source). This is an INVESTIGATION task — user's own words: "dont fix, assign a
dedicated prompt to check." Read this whole file, investigate, report — do NOT fix anything unless
Task 2 finds an unambiguous, narrow rendering-only bug (see Task 2's own guard). PUSH PAUSE LIFTED
for this repo (established this session) — commit locally, verify on localhost, then push/PR per
the standing auto-merge convention, IF (and only if) something gets fixed. A pure investigation
report needs no push.
```

## What triggered this
Live on the deployed guide, https://red1oon.github.io/BIMCompiler/ModellerGuide/#generate-walkers-fill-the-arc
— `img/modeller/walk-fixtures.png` (Duplex, ELEC discipline, "267 placed across 5 storeys · 0
routed"). User's own words reviewing it: **"not LOD400 non material glass, and geometry hell."**
Visual review of the actual image (not just the caption) confirms: dozens of tall, uniformly khaki-
colored box columns, several appearing to extend well above the building's roofline in a way that
doesn't read as a plausible ceiling-fixture layout — genuinely looks chaotic, not just "honest
placeholder boxes" (which the guide's own prose already explains and defends as a deliberate
non-invent choice — that defense covers the BOX SHAPE, it does not cover implausible HEIGHT/SCALE).

## Confirmed from source before dispatching (don't re-derive)
- `_renderDiscWalk` (`modeller/modeller.html` ~3619-3700) uses `matN = new THREE.MeshStandardMaterial
  ({ color: base, emissive: base, ... })` — ONE flat color per DISCIPLINE (`DW_COLOR[disc]`), not per-
  element, no `material_rgba` read anywhere in this function, no transparency logic at all. This is a
  COMPLETELY SEPARATE material path from the ARC-seed fix landed today (`arc_editable.js` →
  `bonsai_library.js` → `bonsai_kernel.js`, bim-ootb PR #735) — that fix never touched this function,
  so "no material/glass" on walker-placed fixtures is not a regression from today's work, it's a
  pre-existing, separate code path. Whether it's ACCEPTABLE as-is or should also get material
  treatment is a real open question (see Task 1).
- The same function already has real LOD400/LOD300/LOD200 fallback logic (`_lod400` resolved via
  `window.RealGeometry.resolveHashes` against `mesh.db`, falling back to `window._dwPrimGeo`'s
  measured-box or catalog-matched geometry) — a `lod400N`/`lod300N`/`lod200N` honesty tally already
  exists in the code. So "not LOD400" may be an accurate, already-instrumented fact for THIS
  building/discipline combination (Duplex ELEC may genuinely have no resolvable mesh hash) rather
  than a bug — Task 1 should read the actual tally, not guess.
- **The x/y/z position each box is drawn at comes directly from the placement object
  (`p.x, p.y, p.z`), unmodified by this render function** — `_renderDiscWalk` only builds the matrix
  (`m.makeRotationZ(p.yaw || 0); m.setPosition(p.x, p.y, p.z)`), it does not compute or clamp height.
  **This is the load-bearing fact for the DiscWalk-baseline question below**: if the columns
  genuinely tower above the roof, that height comes from `disc_walker.js`'s own placement math, not
  from anything in the renderer — meaning this could be a real DEFECT IN THE PLACEMENT DATA, not a
  rendering artifact.

## Task 1 — is the geometry chaos a render bug or a placement-data bug? (answer this FIRST, it gates everything else)
1. Reproduce live: open Duplex, walk ELEC (`?strwalk` or whatever the guide's own screenshot process
   used — check `modeller/tests/` for the guide-shot generator per the naming pattern established in
   `DISC_WALKER_BRANCH_CLOSEOUT.md` Task 2, which already found and fixed a similar guide-camera bug
   this session — read that file's findings first, this may be the SAME family of issue).
2. Dump the raw placement data for the tallest-looking columns (`window.__dwWalks['ELEC']`, filter by
   `p.z`) — get real z-values, not a visual guess. Compare against the building's actual known
   envelope height (Duplex's real storey heights, from `elements_meta`/`spatial_structure`).
3. **If the z-values themselves are implausible** (e.g. a fixture "ceiling height" placed several
   metres above the real roof z) — this is a `disc_walker.js place()` bug, in the PLACEMENT MATH
   itself, not a render bug. Name exactly which placement rule/z-offset computation produces it, cite
   the line, do not fix (per this file's scope — report only).
4. **If the z-values are actually correct** (i.e. real, sensible ceiling-mount heights) and the
   VISUAL impression of "towering columns" is a camera-framing/scale illusion (e.g. the screenshot's
   camera is unusually close/low, exaggerating box height relative to the building) — that's a
   guide-screenshot framing issue, not a data or render defect. Say so plainly, and note a re-shoot
   (different camera angle/distance) would fix it without touching any code.
5. Report which of the two (data bug vs. framing illusion) it actually is, with the real numbers as
   evidence — do not guess from the image alone (an eyeballed screenshot is not proof either way,
   the dumped `z` values are).

## Task 2 — is this hampering DiscWalk, i.e. is DiscWalk resting on a bad baseline?
**Only meaningful to answer once Task 1 determines whether the z-values are actually wrong.** If they
are:
1. Check whether this is ELEC-specific or systemic — Duplex's own already-witnessed disciplines (PLB,
   FP per `DISC_WALK_ROOM_TYPE_AWARE.md`/`ROOM_INTELLIGENCE_SCOREBOARD.md` row 12, which found real,
   trusted PLB/FP signal) presumably render sensibly (their own guide screenshots, if any, should be
   checked too — do the same z-sanity dump for PLB/FP placements on the same building). If ELEC alone
   is broken and PLB/FP are fine, this is a narrow, disc-specific placement bug, not a systemic
   DiscWalk-baseline problem — say so, and this significantly changes how urgent it is.
2. If it turns out to be systemic (multiple disciplines show implausible z), that's a much bigger
   finding — it would mean recent work resting on disc_walker's placement output (today's Plant-Room/
   DiscWalk correlation work in `DISCWALK_PLANT_ROOM_INDUSTRIAL_TAXONOMY.md` Task 2, and the whole
   density/clash witness suite re-verified this session, #722/#741/#743) may need re-scrutiny — name
   this explicitly if found, don't downplay it, but also don't assume it without the actual evidence
   from Task 1.
3. **Do not fix disc_walker.js in this pass** even if a bug is found — per this file's scope, report
   the exact defect (rule/line/computation) for a dedicated follow-up, same discipline as
   `DISC_WALKER_BRANCH_CLOSEOUT.md`'s "you report, you don't merge" pattern that worked well this
   session.

## Explicitly out of scope
- Building material/LOD400 treatment for walker-placed fixtures (making `_renderDiscWalk` read real
  `material_rgba` like the ARC-seed path does) — that's a real, separate, larger feature request (the
  guide's own text already calls this "on the roadmap"), not a bug fix. Name it as a finding, don't
  build it here.
- The ARC-seed glass/material work — already done (PR #735), don't re-touch.
- Any Plant Room/taxonomy work — that's `DISCWALK_PLANT_ROOM_INDUSTRIAL_TAXONOMY.md`'s lane, separate.

## DONE WHEN
Task 1 answered with real evidence (dumped z-values + envelope comparison, not a screenshot guess) —
render bug, placement-data bug, or framing illusion, named precisely. Task 2 answered: is it
ELEC-specific or systemic, and if a real placement defect exists, exactly where (rule/line), reported
not fixed. Findings appended to this file. If Task 1 concludes it's a pure framing illusion, name
that plainly too — a "no real bug, here's why it photographs badly" verdict is a valid, complete
answer, not an incomplete one.
