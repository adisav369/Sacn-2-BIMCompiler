# ✅ 2026-07-04 DONE — bim-ootb PR TBD (`lane/cross-edges-real-aabb`)
# ⚠ DO NOT REMOVE — Scope guard
# Resolves RESUME_SESSION_2026-07-04_GATE_BACKPROP.md item 3 — the cross_edges.js abuts frame-consistency
# gap flagged (not fixed) in PR #647's §RECON-2. The FIRST audit pass this session (pure-node, no browser)
# reached the WRONG conclusion ("furniture-only, SampleCastle shows 0 disagreement") — it never called
# `registerRealGeometry` (browser-only wiring), so it silently measured the plain raw-bbox+yaw fold, not the
# actual production render. Corrected via a real headless-browser measurement before writing any fix code.
# Read the §-log after every run.

## THE REAL BUG (measured via the actual browser Open path, not assumed)
`cross_edges.js`'s `_readBoxes()` computed every element's AABB as `element_transforms.center_xyz ± bbox_xyz/2`
— a coarse, DB-native convention. `bonsai_library.js`'s `foldInsert` (the ACTUAL live render, `_gateBoxes()`'s
source of truth) uses the REAL per-element vertex blob (`component_geometries`/`base_geometries`) instead,
which is measurably more accurate. The two disagree — not narrowly, not furniture-only:

- **SampleCastle** (100% real-geometry resolve rate, 3225/3225 elements): **924 of 9817 (9.4%)** abuts pairs
  disagreed pre-fix — real architectural elements (walls/slabs), not an edge case. Root fact: 341/648
  `IfcWall` instances alone have a real per-element anchor-vs-centroid offset >5mm.
- **SampleHouse**: both of its 2 editable-editable abuts pairs were FALSE POSITIVES — the live render shows
  them genuinely separated by ~215mm and ~391mm, not touching at all (the OLD cross_edges.js was reporting
  them as a real face-touch relationship that doesn't exist).

**Practical impact**: the EXISTING `related()` clash-exclusion (`_gateRel()` in modeller.html, live since
§GATE-1) consumes this SAME edge set to decide "these two elements are EXPECTED to touch, never flag a
clash." A false-positive abuts edge could silently suppress a real clash; the missing real edges (walls to
their floor slab, wall-wall corners) meant `abuts-realign` (PR #647) could never fire on the pairs that
actually needed it.

## THE FIX
`_readBoxes(db, geoDb)` now computes the TRUE world AABB from the real per-element vertex blob when
resolvable — `real_geometry.js`'s own documented ground truth: `world = center_xyz + R(rotation_z)·rawVert`,
applied to every raw vertex (envelope of the ROTATED+TRANSLATED mesh, not a simple centre-shift) — reusing
`RealGeometry.buildGeometryIndex` (a sibling "pure over the DB" module, same design philosophy as
cross_edges.js) rather than re-deriving the decode/dedup logic. Falls back to the coarse
`element_transforms` bbox when no real blob resolves (RealGeometry absent, no `geometry_hash`, missing blob,
degenerate mesh, or a genuinely 3-axis-rotated element — every building measured so far has
`rotation_x=rotation_y=0`, so only yaw is implemented, matching arc_editable.js's own conservative choice).

**Gotcha caught + fixed during implementation**: the first version resolved `window.RealGeometry` at
IIFE-load time (a module-scope `var`) — but `modeller.html`'s `<script>` tag loads `cross_edges.js` BEFORE
`real_geometry.js`, so that capture was permanently `undefined` even though `window.RealGeometry` existed
moments later. Caught by the browser witness (abuts count stayed at the pre-fix value even with
`window.RealGeometry` confirmed present) — fixed by resolving it lazily, at call time.

**Regression found + fixed**: `witness_sdg_gate_smoke.js`'s "clean move raises no flag" leg picked an
arbitrary editable element to lift — it happened to be a wall that DOES really rest on the floor slab
(previously invisible to the buggy graph). Post-fix, lifting it correctly fires `abuts-realign` (real
behavior, not a bug — the wall really was pulled off its real floor contact). Fixed the smoke test to pick
an element with zero real abuts edges for that leg, keeping the "most-separated pair" logic for the clash leg.

## WITNESS
New `modeller/tests/witness_cross_edges_real_aabb.js` (puppeteer, drives the real Open flow — the ONLY way
to see `registerRealGeometry`'s real render, pure-node witnesses can't): 9/9 across SampleHouse (47 real
editable-pair abuts edges found, 0 live-render disagreement, the 2 old false positives gone) + SampleCastle
(13282 pairs, 0 live-render disagreement, down from 924 pre-fix — the remaining ~75 near-boundary cases are
confirmed floating-point noise within 0.0007mm of the 30mm tolerance, given 1mm slack in the assertion).
No regression: `witness_sdg_gate.js` 11/11, `witness_sdg_cascade.js` 7/7, `witness_stretch_ride.js` 9/9,
`witness_e2e_stretch_ride.js` 9/9, `witness_e2e_walk_ifcopen.js` 18/18, `witness_e2e_gridstretch.js` 7/7,
`witness_e2e_gridstretch_multi.js` 21/21, `witness_grid_clear_leak_round2.js` 5/5,
`witness_sdg_gate_smoke.js` 6/6 (fixed), `witness_sdg_cascade_smoke.js` 6/6 (playwright, unaffected).
Performance: SampleCastle (3225 elements) `deriveAdjacency` = 155ms; Terminal (48k elements, no split-geoDb
wired yet, falls back to the coarse bbox exactly as before) = 1.4s, unchanged from pre-fix.

## NOT DONE (honest scope boundary)
- **Terminal's split-file geoDb** (`Terminal_meta.db`/`Terminal_geo.db`) is NOT wired into `cross_edges.js`'s
  callers yet — `_readBoxes(db, geoDb)` supports it (mirrors `real_geometry.js`'s own `geoDb || db`
  convention), but `str_walker_outliner.js`'s `CrossEdges.deriveAll(db)` call site doesn't pass one, so
  Terminal gracefully degrades to the pre-fix coarse-bbox behaviour (safe, no regression, just no
  improvement yet for that one building). A clearly-scoped follow-up, not urgent (Terminal's own gate
  wasn't the item 3 concern; SampleCastle/SampleHouse were).
- 3-axis rotation (`rotation_x`/`rotation_y` nonzero) falls back to the coarse bbox — no building measured
  so far has this, so it's an honest non-invent boundary, not a gap in the fix.
