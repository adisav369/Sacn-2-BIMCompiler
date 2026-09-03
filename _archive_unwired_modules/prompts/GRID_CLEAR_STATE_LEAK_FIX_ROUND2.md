# ✅ 2026-07-04 DONE — bim-ootb PR TBD (`lane/clear-state-leak-round2`)
# ⚠ DO NOT REMOVE — Scope guard
# Continuation of GRID_CLEAR_STATE_LEAK_FIX.md's decision (B) "narrow reset" — that fix covered ONLY
# str_walker_outliner.js's own `ready`/`__dwBuf`. RESUME_SESSION_2026-07-04_GATE_BACKPROP.md §OPEN item 4
# flagged 3 modules as UNAUDITED for the same leak class. This doc records that audit (now done, all 3
# confirmed real) + the fix (same pattern as round 1: each module gets its own onClear(), wired into
# #b-clear). Read the §-log after every run.

## AUDIT FINDINGS (all 3 confirmed real by direct inspection, not assumed)
1. **`window.swXEdges`** (`str_walker_outliner.js` `_openBuffer`, ~line 148) — cached cross-edges
   (abuts/anchored/spans/fills/aggregates) for the OPENED building. Round-1's `onClear()` reset
   `ready`/`__dwBuf`/`__dwName` but MISSED this — a one-line gap from the existing fix, not a new module.
2. **`window.__dwWalks` / `__dwChains` / `__dwAssembly` / `__dwTrunk`** (`modeller.html`) — per-discipline
   walk placements/chains/assemblies/trunk-routes. `#b-clear` already empties the THREE group (so `dwRoot`'s
   MESHES are already gone from the scene — no visual resurrection), but these 4 dictionaries survive.
   `_redrawAllDiscWalks()` (called on storey-scrub) unconditionally re-renders every key still present, and
   `_discGate()` folds them into clash/gate computation regardless of which building is open — so a stale
   discipline placement from a PREVIOUS building could silently resurrect as a phantom mesh and get folded
   into a DIFFERENT building's gate result, with **no error signal at all** (worse than the round-1 STR bug,
   which at least threw a loud rejected-toast on collision).
3. **`bom_tree_outliner.js`'s closure-local `State.seed`/`State.building`** — set on every Open, never reset.
   `category().tree()` degrades gracefully to `[]` when `State.seed` is null (no crash), but the BOM-graph
   tab kept showing the PREVIOUS building's full tree after Clear + opening a different building — a real
   display/data-integrity gap (a reparent drag on the stale tree would sign a `BOM_REPARENT` against the
   wrong building's GUIDs; `bom_tree.js`'s reparent guards against a hard crash but the wrong-building intent
   was never surfaced).

## QUANTIFIED — is this just theoretical?
Verified with a real open+walk+clear+reopen sequence (SampleCastle → Walk ALL Disciplines → Clear →
SampleHouse), not asserted: before Clear, `__dwWalks` held 4 real disciplines (ACMV/ELEC/PLB/FP),
`swXEdges.abuts` held 10947 real edges, BOM tree was seeded — all real, non-empty state. See
`witness_grid_clear_leak_round2.js` G2.

## THE FIX (same pattern as round 1 — no new architecture, no user decision needed)
Each module gets its own `onClear()` (mirroring `STRWalkerOutliner.onClear()`), all three called from
`#b-clear`'s `onclick` in `modeller.html`:
- `str_walker_outliner.js` `onClear()` — added `window.swXEdges = null;`.
- `bom_tree_outliner.js` — new `onClear()`: resets `State.seed/State.building/State.seq`, exported as
  `window.BOMTreeOutliner.onClear`.
- `modeller.html` — new `_clearAllDiscWalks()`: resets `__dwWalks/__dwChains/__dwAssembly/__dwTrunk` to `{}`,
  bumps `__dwInstVer` (the ref-map cache-invalidation counter §I5 already relies on).

This is a continuation of the ALREADY-APPROVED decision (B) narrow-reset pattern, not a fresh design fork —
applying the SAME per-module `onClear()` shape to 3 modules now PROVEN (not hypothetical) to have the same
bug. `DiscWalker`'s own engine module (`disc_walker.js`) was checked and is clean (rules-DB state only,
building data passed per-call each time) — the leak lives in `modeller.html`'s globals, not the walker engine.

## WITNESS
`modeller/tests/witness_grid_clear_leak_round2.js` — 5/5 PASS (real .db-open SampleCastle → real Walk ALL
Disciplines populates all 3 states → real click on `#b-clear` → all 3 reset + all 3 §-tags logged → real
open of a DIFFERENT building (SampleHouse) shows zero bleed-through). No regression: `witness_sdg_gate.js`
11/11, `witness_sdg_cascade.js` 7/7, `witness_stretch_ride.js` 9/9, `witness_e2e_stretch_ride.js` 9/9,
`witness_e2e_walk_ifcopen.js` 18/18, `witness_e2e_gridstretch.js` 7/7, `witness_e2e_gridstretch_multi.js`
21/21, `witness_sdg_gate_smoke.js` 6/6 (playwright), `witness_sdg_cascade_smoke.js` 6/6 (playwright).

## Also this session (RESUME_SESSION_2026-07-04_GATE_BACKPROP.md §OPEN, other items)
- **Item 6 (watchdog note)**: playwright IS available this session (via the npx cache, `NODE_PATH=<cache
  dir> node modeller/tests/witness_sdg_gate_smoke.js` / `witness_sdg_cascade_smoke.js`) — both re-ran GREEN
  (6/6 each), confirming zero drift across #644/#646/#647/#648.
- **Item 3 (cross_edges.js abuts frame-consistency gap)**: audited with a direct numeric comparison
  (raw `element_transforms` frame vs ARC-seeded/`foldInsert` scene frame) on SampleCastle (9817 editable-pair
  abuts edges, real wall-wall corners — the building type the original recon note asked for) vs SampleHouse
  (the original 2 furniture pairs). **Root cause found, precisely scoped — NOT fixed, needs an architecture
  call, see below.**
  - SampleCastle: **0 real disagreements** across all 9817 pairs (only ~60 pairs sit within 0.001mm of the
    30mm tolerance boundary — floating-point boundary noise present in ANY hard threshold, not a frame gap).
    SampleCastle's editable substrate has **zero IfcFurniture elements at all** (confirmed by class dump:
    Slab/Covering/Window/WallStandardCase/Door/Wall/BuildingElementPart/Railing/Stair/BuildingElementProxy
    only) — so it can't exhibit the bug, not because it's immune.
  - SampleHouse: both of its 2 editable abuts pairs involve `IfcFurniture` and BOTH show a LARGE real gap
    (260mm and 65mm past tolerance — not boundary noise).
  - **Root cause**: `bonsai_library.js`'s own comment (§ARC-ANCHOR, W-MV-PARITY fix PR #613) already names
    this exact mechanic: `element_transforms.center_xyz` is the IFC **placement ANCHOR**, not the geometric
    AABB centre; for an element that gets a real LOD-300 catalog-geometry match with a non-zero
    `anchorOffset`, the TRUE world-space AABB centre is `center + R·anchorOffset` — `foldInsert` in
    `bonsai_library.js` already applies this correction when rendering the live scene, but `cross_edges.js`'s
    `_readBoxes()` reads `center_x/y/z` RAW from `element_transforms` and treats it as the AABB centre
    directly, with no anchor correction. For elements that stay LOD-200 (raw-bbox, unmatched — the common
    case, 38/39 in SampleHouse) this is harmless (`anchorOffset` is effectively 0). SampleHouse's ONE LOD-300
    match (of 39 elements) happens to be a furniture piece that ALSO has 2 real abuts edges — hence the hit.
  - **Why not fixed here**: correcting `cross_edges.js` means either (a) giving it a dependency on the
    ARC-seed/catalog-match pipeline (`arc_editable.js`/`bonsai_library.js`) it was deliberately built WITHOUT
    ("DERIVED AT RUNTIME from the pristine bbox substrate... NOT baked", its own file header) — a real
    architecture change with consumers beyond the gate (bom-graph adjacency lens), or (b) re-deriving/
    correcting abuts membership at `_gateRel()`'s consumption point instead, using the ALREADY-available
    scene-frame boxes (`_gateBoxes()`) to re-check touch tolerance before trusting a `cross_edges.js` edge —
    smaller blast radius but changes `_gateRel()`'s contract. This is a real fork, not mine to pick silently
    (mirrors item 2's "not mine to invent" framing) — flagging both options for the next session/user call.
  - **Practical risk today**: LOW severity, NARROW scope — only affects abuts pairs where at least one side
    is a real LOD-300 catalog-matched element with non-zero `anchorOffset` (a small, currently-furniture-only
    fraction observed). The bug's OBSERVED direction (raw says touching+excluded, scene shows deep
    interpenetration) means a real problematic overlap could be silently excluded from the clash check rather
    than flagged — worth fixing eventually, not urgent given the narrow hit rate measured.
  - Investigation scripts (scratch, not committed): compare-frame + class-breakdown scripts used to produce
    the numbers above are in this session's scratchpad, not part of the shipped diff — reproducible via the
    same `ArcEditable.seedArc` + `CrossEdges.deriveAll` + `Library.foldInsert` harness `witness_sdg_gate.js`
    already uses (see that file's `base[fid]` construction for the exact pattern).
- **Item 2 (accept/ignore UI for ORANGE) and item 5 (cyclic backprop / discrete swap-by-interface)**: still
  genuinely unbuilt, still real UX/architecture calls — not touched this session, per the resume doc's own
  framing ("not mine to invent").
