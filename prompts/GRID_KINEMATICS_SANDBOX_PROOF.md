# SPEC — 3D Grid kinematics: a tiered sandbox to prove the maths, not the render

```
# ⚠ DO NOT REMOVE
SCOPE: this file covers ONLY the 3D authoring-grid system (bonsai_grid.js, grid_kinematics.js,
bonsai_gridmove.js, sdg_cascade.js's stretchRide, and the modeller.html wiring around them). Nothing else —
not seam-healing, not mesh-fit, not any other lane. Read GRID_SMART_ELEMENT_SCOPE.md first (the fix that
prompted this: a real mass-sweep bug, fixed, but verified so far mostly by whole-building E2E + a screenshot
check that turned out to be UNINFORMATIVE — see §0). SPEC ONLY — nothing here is built yet.
```

## §0 — Why this exists (read before writing any test)

Two real, back-to-back lessons from the session that produced `GRID_SMART_ELEMENT_SCOPE.md`, both pointing
the same direction:

1. **DISC Walker's own failure mode, cited directly by the user:** an AI-driven geometry effort finished with
   elements ending up OUTSIDE the building entirely — "AI is geometry blind." The lesson isn't "be more
   careful looking at renders" — it's that visual/spatial judgment by an AI reading a 3D scene is
   *structurally* unreliable, not just occasionally wrong.
2. **Confirmed the same day, on this exact grid work:** a "sight check" screenshot, framed tight on the two
   elements a fix was supposed to move, came back as a **flat gray, fully uninformative frame** — the camera
   landed inside the geometry. Not subtly wrong — completely blank. If accepted uncritically, a blank frame
   is indistinguishable from "nothing moved" or "everything's fine," which is worse than no check at all.

**The actual proof that DID hold up** was pure arithmetic, not a screenshot: pulling live bbox centres out of
the scene graph as numbers and checking `|elementY − grabY| ≤ radius` directly. That's the model this spec
generalizes — every claim about the grid system must be checkable as a number against a hand-computable
expectation, with the render treated as, at best, a decoration, never evidence.

**Baseline verified before writing this spec (2026-07-09), not assumed:** the Duplex data every tier below
would build on is genuinely LOD400 — `RealGeometry.buildGeometryIndex` resolves 253/253 real element
geometries (0 unresolved, 0 null-hash), and `arc_editable.js`'s `buildSeedOps` hard-fail path was directly
tested (not just read): deliberately breaking one element's `geometry_hash` link produced `hardfail=1`,
`total_ops=252` (not 253), and that element genuinely absent from the committed ops — a real refuse, not a
silent box substitute. `WalkerDoctrine.md §11`'s "no non-LOD400 content presented as real, hard fail no
fallback" holds on this baseline as tested, not merely as documented.

**The standing failure this spec exists to prevent:** testing this system only end-to-end (open a real
building → drag → check the final committed op "looks about right") and *assuming* every intermediate step
worked because the end state passed. A wrong intermediate step and a compensating second wrong step can
still add up to a plausible-looking final number. Each stage below must be proven **on its own**, with
inputs simple enough that a human can independently hand-compute the expected output before running anything.

## §1 — The pipeline, broken into provable stages (read `bonsai_gridmove.js`/`grid_kinematics.js` again
before assuming this list is complete — it reflects what exists as of 2026-07-09)

```
Grid.xs/ys (authored)
   │
   ▼
gridLines()                    -- id/axis/pos mapping
   │
   ▼
elementData()                  -- NEW (this session): class allowlist + grab-locality filter
   │                              (bonsai_gridmove.js only -- modeller-adapter layer)
   ▼
attachGridToElements()         -- grid_kinematics.js's OWN classification: ATTACH / EDGE_LEFT /
   │                              EDGE_RIGHT / SPAN / INTERIOR, per element per axis (PURE, no
   │                              THREE/DOM/DB -- see its own header)
   ▼
dragGrid(gridId, delta)        -- PURE recompose: emits {featureId, action, axis, delta|newScale, ...}
   │
   ▼
stretchRide(commands, ...)     -- sdg_cascade.js: hosted-opening override (strip rider's own command,
   │                              induce ONE rigid move off REAL rel_fills_host edges)
   ▼
applyOverrides(commands, ...)  -- bonsai_gridmove.js: green/orange opt-out filter
   │
   ▼
commit() → GEOM_GRID_MOVE op   -- signed, committed to kernel_ops
   │
   ▼
worker fold                    -- turns the committed commands into ACTUAL mesh vertex/transform changes
   │                              (bonsai_kernel_worker.js / bonsai_library.js foldInsert path)
   ▼
rendered mesh                  -- what a screenhot shows -- LAST, LEAST reliable checkpoint, not first
```

**Nine stages. Today's actual test coverage, honestly assessed:** stages 1-2 got real, isolated node
witnesses this session (`_localityRadius`, class allowlist — see the `witness_gridmove_smart_scope.js`
S1/S3 checks). Stages 3, 4, 6 have never been tested in isolation — only ever exercised as a byproduct of a
full browser E2E run, where a wrong stage-4 command and a compensating stage-6 rider could both be wrong in
ways that still land on a passing final assertion. Stage 5 (stretchRide) has SOME isolated coverage
(`witness_sdg_cascade.js`). Stage 8 (the fold) is the biggest gap: **no test today proves the worker fold
turns a KNOWN, hand-authored `GEOM_GRID_MOVE.commands` array into the EXACT expected final vertex
positions, independent of whether `dragGrid()` computed those commands correctly** — a wrong fold and a
wrong command-generator could currently mask each other in the existing E2E suite.

## §2 — The sandbox: FOUR tiers, cheapest/most-controllable first, never skip one and trust the next

### Tier 1 — Pure math, zero browser, zero THREE, zero DOM (the cheapest, most rigorous tier)

`grid_kinematics.js` states its own contract: "Pure-math engine... Never touches Three.js, databases, or
DOM." **Take that literally — it should be `require()`-able directly in plain node with hand-built arrays,
no puppeteer, no modeller.html, no sql.js.** Build a fixture: 2-4 elements at ROUND, hand-pickable
coordinates (e.g. a wall centred at x=2 with halfExtent=2, a gridline at x=4) and hand-compute, on paper,
before running anything:
- What relation (`ATTACH`/`EDGE_LEFT`/`EDGE_RIGHT`/`SPAN`) `_findBestGrid` SHOULD return, given
  `ATTACH_TOL=0.5`/`EDGE_TOL=0.1` (already-fixed constants, exported — read them, don't guess).
- For a given `dragGrid(gridId, delta)` call, the EXACT `newScale`/`translateDelta`/`delta` a correct
  implementation must produce (this is highschool-algebra-level arithmetic on a 2-4 element fixture — if it
  takes more than pen and paper to predict, the fixture is too complex for this tier).
Assert exact equality (or a tight, justified epsilon for floats) against the hand-computed numbers, not
"looks close" or "roughly right."

### Tier 2 — `bonsai_gridmove.js` adapter, hand-built fakes (no real browser, no real building)

Test the NEW (this session's) `elementData()` filters in isolation from BOTH Tier 1's engine and any real
building: a hand-built fake `window.Bonsai.group()` (a few fake mesh-like objects with known
`userData.featureId`/bbox), a fake `window.Bonsai.oplog.db` (an in-memory sql.js db with a few hand-inserted
`kernel_ops` rows carrying known `ifc_class` values), and a hand-set `window.Bonsai.grid.xs/ys`. Assert:
- `_buildClassByFid()` returns EXACTLY the map you inserted, no more, no less.
- `_localityRadius(axis)` returns EXACTLY `1.5 × (the one gap you defined)`, for a hand-picked grid with a
  KNOWN, single, unambiguous max gap (don't test with more than 2-3 lines — verifying "which gap is biggest"
  in a bigger fixture defeats the point of this tier).
- `elementData()`'s combined output (class filter AND locality filter together) matches an EXACT expected
  fid list, computed by hand from the fixture, not inferred from running the code once and eyeballing it.

### Tier 3 — Real building data, Tier 1's engine, still zero browser

Load ONE real building's real `element_transforms` via `sql.js` (already this session's own established
pattern — no new tooling). Feed the REAL positions directly into Tier 1's pure engine (bypass
`bonsai_gridmove.js`, bypass THREE, bypass the browser entirely). Pick a SMALL, deliberately-chosen sample
(3-5 real elements near a real gridline coordinate) and hand-verify (spreadsheet or calculator, not "the
code says so") what SHOULD classify as ATTACH/SPAN/EDGE for that sample, before asserting. This tier is
where the ACTUAL Duplex mass-sweep number (`~54` real elements, confirmed this session) should be
independently re-derived from a query + hand arithmetic, not just cited from the earlier browser-based
finding — cross-checking the earlier discovery, not re-trusting it uncritically.

### Tier 4 — Real browser E2E (the existing witness suite: `witness_e2e_gridstretch.js` etc.)

Kept as-is — proves the pieces actually WIRE TOGETHER (button click → real drag → real commit → real
fold → real rendered mesh) and that the whole user-facing flow works. **Explicitly the LEAST rigorous tier
for NUMERIC geometry correctness**, not the primary proof — a passing Tier 4 run with a broken Tier 1/2/3
underneath it is exactly the masking risk §0 describes. Screenshots captured here are for HUMAN reference
only (e.g. attaching to a PR for a person to glance at) — per §0's blank-frame result, an AI must not treat
them as verification evidence, full stop.

**If a screenshot is captured at all in this tier, use an ORTHOGRAPHIC PLAN (top-down) or ELEVATION view,
never the default 3D perspective camera.** `project_2d_views_roadmap.md`'s own product direction already
names WHY: "drag grid lines → write Δ to DB → recompile" is explicitly conceived as a 2D-plan interaction —
grid coordinates ARE a flat X/Y (or axis/elevation) concept, and a flat orthographic view cannot produce the
"camera lands inside the geometry" degenerate blank frame a tight 3D perspective zoom just did (§0). This
does not upgrade a screenshot to evidence — it only makes the ONE thing a screenshot is honestly useful for
(a human glancing at it) less likely to be worthless. `modeller.html` does not currently expose a plan/
elevation camera mode for this purpose — check for one before assuming it needs to be built new (the
Sketch/Route tools already enter a fixed plan view per `e2e_harness.js`'s own `§F2-FRAMING` comments — that
existing camera state may already be directly reusable here, not a new mechanism).

## §3 — Stage 8 (the worker fold) — the identified gap, explicitly named, not yet built

No tier above proves that a hand-authored `GEOM_GRID_MOVE` op with a KNOWN `commands` array folds to the
EXACT expected final vertex positions, independent of whether `dragGrid()` produced those commands
correctly. Check first whether `bonsai_kernel_worker.js`'s fold logic is ALSO pure/isolatable (same kind of
contract-reading `grid_kinematics.js`'s own header made possible for Tier 1) before assuming a Tier-4-only
browser test is the only way to reach it. If it is isolatable, it becomes a Tier 1-style sandbox on its own
(hand-author a `commands` array, hand-compute the expected resulting AABB/vertex positions, fold it, compare
exactly) — genuinely the highest-value single addition this spec names, since it is the ONE stage with zero
isolated coverage today.

## §4 — DONE WHEN

- Tier 1: `grid_kinematics.js`'s `_findBestGrid`/`dragGrid` proven against ≥3 hand-computed fixtures
  covering ATTACH, SPAN, and at least one EDGE case, pure node, no browser.
- Tier 2: this session's `elementData()` filters (class allowlist, locality) proven against hand-built fakes,
  exact expected fid lists, pure node.
- Tier 3: Duplex's real ~54-element mass-sweep number independently re-derived from a fresh query + hand
  arithmetic on a small real sample, not re-cited from the earlier browser finding.
- §3's worker-fold gap: at minimum, a decision recorded on whether it's isolatable Tier-1-style, with a
  sandbox built if so.
- No regression: the existing Tier-4 suite (`witness_e2e_gridstretch.js`, `witness_e2e_stretch_ride.js`,
  `witness_e2e_grid_greenorange.js`, `witness_e2e_dm_gridundo.js`, `witness_e2e_gridstretch_multi.js`,
  `witness_grid_insert.js`, `witness_gridmove_smart_scope.js`) all still green throughout — this spec ADDS
  lower-level proof, it does not replace or weaken what already passes.

## §5 — NOT YET BUILT

Nothing in this file is implemented. This is the spec only, per the user's explicit "should be spec'd
before" pattern already established this session (see `GRID_SMART_ELEMENT_SCOPE.md`'s own §0/§1 split).
