# STR RouteWalking — spec (the structural walker, mirror of MEP RouteWalker)

```
# ⚠ DO NOT REMOVE
SCOPE: Spec STR as a WALKED discipline (like MEP RouteWalker) — generate/strengthen the structural system FROM the
ARC substrate + the proven graph edges, under a SPATIAL + REGULATORY handler set that mirrors MEP RW's clash+gradient.
ANCHOR: prompts/RESUME_GRAPH_MODELLER_INTEGRATION.md §VISION-LOCK ("STR is a WALKER that strengthens the ARC").
NON-INVENT: skeleton = f(grid) (deterministic, RS-checkable); systems = generative (oracle'd vs extracted STR, NOT
bit-exact); every member SIZE cites a regulatory rule (span/load) — never an invented constant. Read the §-log.
WITNESS-FIRST: the W-STR-* claims below are written BEFORE code. Oracle = pristine extracted.db / raw extraction.
SAFETY/MEASUREMENT LAYER ABOVE THIS WALK: prompts/WALKER_GUARDS_ROSETTASTONE_SPEC.md (universal guard pass +
RosettaStone walk-back + calibrated confidence — the answer to "does the walk generalise?"). STR is its first walker.
```

## §0 THE PONDER, ANSWERED EMPIRICALLY (Terminal `deploy/buildings/Terminal_extracted.db`)
"Can STR 'walk' based on ARC, like MEP RouteWalking?" — **YES, and Terminal proves it in two complementary modes.**

| STR class | count | walk mode | evidence (measured, this DB) |
|---|---:|---|---|
| `IfcColumn` | 158 | **skeleton — deterministic** | land on **16 X-gridlines × 11 Y-gridlines** (~24 cols on each major bay line); position = f(grid) |
| `IfcBeam` | 432 | **skeleton — deterministic** | span between grid columns (the proven `spans` edge: bbox crosses two datums) |
| `IfcMember` | 442 | skeleton/system | space-frame struts between nodes |
| `IfcPlate` | 33,324 | **system — generative** | **32,203 share the EXACT bbox 0.5×0.2 m** = one unit tessellated = `instanced-by n` over a surface |

So STR is **MORE walkable than MEP**, not less: MEP needs hand-seeded `ad_mep_anchor` points, but STR's anchors —
**grid intersections, gridlines, slab/roof surfaces** — derive DIRECTLY from ARC + the already-proven edges
(`datum_plane` emergent grid, `anchored-to`, `spans`, `instanced-by n`). The structural walker reuses the SDG substrate.

**Membership fork (was open in the triage) — RESOLVED by this evidence:** STR is a true WALKER (editable BOM = ARC
only; STR regenerates). The space-frame *cannot* be both "dropped verbatim" and "reproduced by a generic walk" — its
33K plates are a generative DESIGN, not a verbatim leaf set. So: **BOM membership becomes "ARC only"; STR moves to the
walker/routed side beside MEP; extracted STR = ORACLE.** (Skeleton is f(grid), so it stays bit-reproducible regardless.)

## §1 BIM-WORLD STANDARDS THIS IS GROUNDED IN (research, not invention — cite, don't guess)
The "STR generated from ARC" workflow is established practice; the walker formalises what these standards already do:
- **IFC structural model = a walkable topology.** `IfcStructuralAnalysisModel` is a graph of `IfcStructuralCurveMember`
  (1D: beams/columns) + `IfcStructuralSurfaceMember` (2D: slabs/plates/walls) joined at `IfcStructuralPointConnection`/
  `IfcStructuralCurveConnection`, loaded by `IfcStructuralLoadGroup`. Nodes + members + loads = exactly MEP's
  anchor + pattern + flow graph. `IfcGrid`/`IfcGridAxis`/`IfcGridPlacement` is the first-class spatial substrate
  members are placed against — our emergent `datum_plane` is the non-invent equivalent.
- **Load path / gravity flow (the directed "walk").** Load flows `slab → joist → girder → column → foundation` — a
  directed dependency graph. "**Strengthen the ARC**" = guarantee a CONTINUOUS load path, each member sized to its load.
  (This is the spine's MRP/backprop framing applied to structure.)
- **Tributary-area sizing.** Each column/beam carries the load of its tributary area (mid-span to mid-span) → section size.
- **Span/depth + deflection rules (the regulatory tables).** Rule-of-thumb depths: steel girder ≈ span/20, RC beam ≈
  span/12, slab ≈ span/28–36; deflection limits L/360 (live), L/240 (total); max unbraced length. Codified in
  **Eurocode** (EN 1990–1999: EN1993 steel, EN1992 concrete), **AISC 360** (steel), **ACI 318** (concrete),
  **ASCE 7 / IBC** (loads), and locally **UBBL** (already this project's ARC min-dim source). Min column size, min
  cover (fire), min reinforcement = code minimums.
- **Lateral system.** Bracing / shear walls / moment frames sized to wind + seismic, tied to building height & plan aspect.
- **Commercial precedent that STR-from-ARC is normal:** Revit **Structural "Beam Systems"** auto-fill a bay bounded by
  beams/grids with parallel joists at a spacing (a literal STR walker); Revit structural framing snaps columns to grid
  intersections; **Tekla**, **Grasshopper + Karamba3D**, **Dlubal RFEM**, **Autodesk Generative Design** all
  parametrically generate/optimise structure over an architectural envelope; **space-frame/gridshell** generation
  tessellates a surface into a node+strut lattice (the Terminal canopy).
- **LOD (BIMForum Level of Development).** Structure is routinely auto-generated at **LOD 200** (generic, grid-located,
  approximate size) and refined to LOD 300+. The walker's output target = LOD 200–300 generic-but-conforming structure.

## §2 THE HANDLER SET — mirror of MEP RouteWalker (the user's ask: "spatial + regulatory rules, handlers like MEP RW")
MEP RW pipeline (`RouteWalker.java`): A1 pattern-select → A2 anchor-load → A3 pair+apply per storey → A4 ARC-clash →
A5 product-resolve+emit. STR RW keeps the SAME five stages; only the anchor/pattern/rule CONTENT differs:

| MEP RW stage | STR RW analogue |
|---|---|
| **A1 pattern** (`ad_mep_pattern`: from→to node, direction_axis, piece_type, offset_rule, gradient) | **`ad_str_pattern`** framing topology: `COLUMN@grid-node → GIRDER@primary-line → JOIST-infill@spacing → SLAB`; or `SURFACE → TESSELLATE(unit, n)` for space-frames |
| **A2 anchors** (`ad_mep_anchor` seeded METER/FIXTURE) | **anchors DERIVED from ARC + SDG** (no hand-seed): grid intersections (column), gridlines (girder span), slab/roof surfaces (system), wall centerlines (load-bearing). Provenance `derived:grid`/`derived:surface` |
| **A3 pair+apply** (nearest unmatched, 0<d<50m, per storey) | **span pairing**: col-to-col along a gridline = girder; girder-to-girder = joist bay; surface boundary = tessellation domain. Per storey (skeleton) / per surface (system) |
| **A4 ARC clash** (skip if pipe AABB ∩ ARC box) | **FIT/CLASH (spatial)** — see §2A |
| **A5 product+emit** (`M_Product_ID`, c_orderline) | **section resolution** (member size class from load/span → product) + signed op emit, provenance `derived:str-walk` |

### §2A SPATIAL handlers (WHERE the structure goes) — the geometry rules
1. **grid-anchor** — a column sits at a grid intersection (reuse `anchored-to`; emergent `datum_plane`). RED if no node.
2. **span-geometry** — a girder spans between two adjacent column nodes on a gridline (reuse `spans`; cross-section held).
3. **surface-tessellation** — a space-frame / cladding system = `unit × n` tiled over an ARC surface (`instanced-by n`);
   collapse n→1 ⇒ the system vanishes (proven §SHELL-N-ZSPAN). Unit + surface measured, never invented.
4. **Z-continuity** — columns stack across storeys (share the X/Y datum up the Z lattice); risers/cores = f(n).
5. **fit-within-ARC** — a column may not land in a doorway / circulation void; a girder soffit must clear ceiling height;
   member AABB must sit in an ARC void, not overlap an opening. (Direct mirror of MEP A4 clash-vs-ARC envelope.)
6. **clash-vs-MEP** — a walked member must not occupy a routed MEP run (shared clash bus with RouteWalker).

### §2B REGULATORY handlers (HOW MUCH structure = "strengthen") — the code rules, each CITED not guessed
1. **load-path continuity** — every gravity & lateral load must reach the foundation via a continuous member chain.
   RED if a load has no path (e.g. a beam landing on nothing after an ARC edit removed its column).
2. **tributary-load sizing** — member section = f(tributary area × load); cite the load case (ASCE 7 / EN 1991).
3. **span/depth + deflection** — girder/slab depth from the span table; reject (RED) or upsize (ORANGE) if span exceeds
   the depth's limit or deflection > L/360. Cite the rule (EN1993/AISC or EN1992/ACI).
4. **bracing / max-unbraced-length** — lateral system required past a height/aspect threshold; ORANGE to add bracing.
5. **code minimums** — min column size, fire cover, min reinforcement (Eurocode / UBBL-STR). RED on violation.
Every regulatory output stamps `provenance=derived:regulatory` + the rule id; **zero invented numbers** (the door lesson).

### §2C SIGNAL TAXONOMY (reuse the spine's RED/ORANGE/GREEN exactly)
- **RED** (hard stop): no load path · over-span past code · column-in-doorway · fire-cover violation. The walk halts/flags.
- **ORANGE** (soft, accept/ignore, signable): "add an intermediate column" · "upsize girder to depth d" · "add bracing".
- **GREEN**: conforming, no action.

## §3 THE RE-WALK LOOP (the wedge — why this beats a static structural model)
Edit ARC → STR re-walks into the changed envelope, as ONE signed op chain (non-invent, deterministic replay):
```
drag a bay wider (ARC grid edit, GEOM_GRID_MOVE)
  → grid datums move (emergent datum_plane re-measured)
  → STR skeleton re-walks: columns re-anchor to new nodes, girders re-span (cross-section held), joists re-infill
  → REGULATORY pass: new spans checked vs depth table → ORANGE "upsize girder" / RED "over-span, add column"
  → space-frame re-tessellates over the new surface extent (unit held, n re-counts)
  → MEP re-routes against the moved STR+ARC (existing RouteWalker)
  → 4D/5D re-roll quantities
every step a signed op in kernel_ops; geometry is the fold of the log.
```

## §4 WITNESS CLAIMS (spec-first; oracle = pristine extracted.db / raw extraction, NEVER cooked output.db)
- **W-STR-WALK-SKELETON ✅ DONE 2026-06-26 (5/5)** — `deploy/dev/str_walker.js` + `scripts/witness_str_walk_skeleton.js`.
  On REAL `Terminal_extracted.db`, 158 `IfcColumn` walk deterministically onto an emergent **18×10** grid (gapTol
  resolves at ≤0.5m — measured by sweep, coarser MERGES real lines): C1 grid-emerges (8.8×/15.8× compression), C2
  on-grid (max residual 0.329m, **mean 5.2cm**, 0 off-grid), C3 count-parity (158), C4 deterministic (walked (x,y) ∈
  gridlines, re-run identical), C5 non-invent (every line = mean(real coords), every walked traces srcGuid+derived:grid).
  Proves the skeleton walk is deterministic & RS-clean. Pure module, GUID prefix `SW2D-`, edits no existing file.
- **W-STR-WALK-SPANS ✅ DONE 2026-06-26 (4/4)** — `swWalkGirders` in `str_walker.js` + `scripts/witness_str_walk_spans.js`.
  On REAL Terminal: 108 girders walk between adjacent grid columns; C1 girders-walk, C2 spans-edge (every girder spans
  2 distinct datums, span==separation, 1.6–39.7m), C3 **beams-on-grid INDEPENDENT ORACLE = 71.5%** (309/432 extracted
  `IfcBeam` sit on the column-derived gridline; beams never built the grid ⇒ non-circular; 123 off-grid = 40–60m trusses
  + secondary, reported not dropped), C4 non-invent (endpoints=real columns, span=measured gap, derived:str-walk).
- **W-STR-TESSELLATE ✅ DONE 2026-06-26 (5/5)** — `swDeriveTessellation`/`swWalkTessellation` in `str_walker.js` +
  `scripts/witness_str_tessellate.js`. The 33,324-plate space-frame as `unit × n` over the canopy. C1 unit-emerges
  (modal 0.5×0.2×0.11, **96.6%** share), C2 surface-shell (thin: local 1m-cell z-spread 0.07m ≪ global 8.7m; **91.4%**
  footprint coverage — a 2-manifold shell curving in x AND y; walker's z=f(x) is an honestly-stated mid-surface
  simplification), C3 **count-reconstruct 1.3%** (predicted 33,753 vs 33,324 from interior band-density×nBands —
  generative, non-circular), C4 **extent=f(N)** (walk(1)=1 collapse, walk(half)=16,662, walk(full)=33,232 = 0.3%), C5
  non-invent (every unit derived:str-walk + measured dims; tail 1,121=3.4% reported; NEVER bit-exact). The GENERATIVE
  half — completes the skeleton(deterministic)+systems(generative) pair.
- **W-STR-REGULATORY ✅ DONE 2026-06-26 (5/5)** — `swCheckGirder`/`swConforms`/`SW_SPAN_RULES` in `str_walker.js` +
  `scripts/witness_str_regulatory.js`. Span/depth + deflection → RED/ORANGE, each CITING Eurocode (EN 1993 steel /
  EN 1992 RC, AISC/ACI cross-ref; UBBL adopts MS EN). C1 rule-cited (provenance derived:regulatory + source), C2
  **conforms-real INDEPENDENT VALIDATION = 95.8%** (414/432 real Terminal beams satisfy steel L/20; 18 truss-like
  ratio>30 flagged not dropped), C3 RED-over-span (25m girder → "add a column or use a truss"), C4 ORANGE-upsize (8m
  @0.30m depth → upsize to 0.40m; 0.50m → GREEN), C5 non-invent (walked girders 8 RED / 100 GREEN — the 8 = long-span
  needing trusses; ALL thresholds in SW_SPAN_RULES, grep-clean, zero magic numbers).
- **W-STR-FIT-CLASH** — a walked column landing in an ARC doorway/circulation raises a spatial clash (mirror MEP A4).
- **W-STR-REWALK ✅ DONE 2026-06-26 (5/5) = THE WEDGE** — `swReWalk` in `str_walker.js` + `scripts/witness_str_rewalk.js`.
  On real Terminal, one bay-widen (Δ=3.11m) → **41 signed ops**: C1 grid-moves (GEOM_GRID_MOVE + 25 columns re-anchored
  by exactly Δ), C2 girders-respan (14 girders, |Δspan|==Δ, cross-section held), C3 **exception-wedge (target girder
  GREEN→RED @19.0m, cited "add a column or use a truss")**, C4 signed-replay (GRID_MOVE heads the chain, STR_RESPAN
  replays to after-state, deterministic), C5 non-invent (every op has provenance, every exception cited, the ONLY new
  number is the user Δ). "Model + exception fold from ONE signed log" — the differentiator no competitor has.
- **W-STR-ABSTRACT** — grep the walker for IFC-class/role hardcoding → must be ZERO (the door doctrine: measure, don't
  whitelist). The walker keys off graph edges + measured geometry + cited rules, never per-class verbs.

## §5 NON-INVENT BOUNDARY (state it loudly — this is where a structural walker could cheat)
- **Skeleton (columns/girders):** position = f(measured grid). DETERMINISTIC, RS-checkable, provenance `derived:grid`.
- **Systems (space-frame/joists/cladding):** GENERATIVE conforming lattice — oracle'd vs extracted STR by
  count/coverage/unit within tol, provenance `derived:str-walk`. **Never claimed 0.000 mm**; never tagged `ifc_extract`.
- **Member sizing:** from cited code rule (span/load), provenance `derived:regulatory` + rule id. **No magic numbers.**
- The extracted STR is the **ORACLE** the walker is graded against — it is NOT copied verbatim into the editable BOM.

## §6 DEPENDENCIES / REUSE (build on what ships — don't rebuild)
- SDG edges (`datum_plane`, `anchored-to`, `spans`, `instanced-by n`) — ALREADY in `extractIFCtoDB.py`, witnessed on
  house + bridge. The STR anchors ARE these edges. **Build the pattern+regulatory layer, not the edge layer.**
- MEP `RouteWalker.java` / `routewalker.js` — the structural pipeline mirrors its 5 stages; share the ARC-clash bus.
- `GridKinematicEngine` / `GEOM_GRID_MOVE` — the ARC edit that triggers the re-walk already ships.
- `kernel_ops.js` — every walked member is a signed op (deterministic replay).
- NEXT, in order: (1) ✅ **W-STR-WALK-SKELETON DONE** — emergent-grid column walk (above); (2) ✅ **W-STR-WALK-SPANS
  DONE** — girders between adjacent grid columns + 71.5% beam-on-grid oracle (above); (3) `ad_str_pattern` schema + seed
  the framing pattern (col→girder→joist→slab) mirroring `seedMepPatterns`, persist the walk; (4) ✅ **W-STR-REGULATORY
  DONE** — span/depth+deflection RED/ORANGE, Eurocode-cited, 95.8% real-beam validation (above); (5) ✅ **W-STR-TESSELLATE
  DONE** — space-frame unit×n, count-reconstruct 1.3%, extent=f(N) (above); (6) ✅ **W-STR-REWALK DONE = THE WEDGE** —
  ARC edit → 41 signed ops, GREEN→RED exception surfaced (above). **ENGINE COMPLETE (5 slices, 24/24 witnessed).**
  Remaining = INTEGRATION, not engine.
  **(7) BRIDGE ✅ DONE 2026-06-26 (W-STR-BRIDGE 5/5)** — `deploy/dev/str_walker_bridge.js` + `scripts/witness_str_bridge.js`:
  `swbInit(db)` builds walker state from a building's STR columns; `swbOnGridMove(gridMoveParams, commit)` re-walks +
  commits the cascade via the REAL `kernel_ops.commitOp` (proven against a sql.js DB — 40 real rows: 25 STR_REANCHOR +
  14 STR_RESPAN + 1 STR_SIGNAL, no double GRID_MOVE, provenance folded into params, signal cited); `swbTabData()` feeds
  the Outliner STR walker tab. Commit is dependency-injected (browser passes `KernelOps.commitOp`).
  **(7b) LIVE WIRING ✅ DONE 2026-06-26 — bim-ootb PR #531** (`lane/str-walker-modeller`): `str_walker.js`+`bridge`+new
  `str_walker_outliner.js` loaded in `modeller.html` (flag `?strwalk`); STR Outliner category + 🏗 open button; wraps
  `Bonsai.gridmove.commit` → grid drag re-walks STR + commits the signed cascade (STR_REANCHOR/RESPAN/SIGNAL) + surfaces
  a cited exception (the wedge). `sw.js` v727→v728 + precache. **§STRWALK-SMOKE 8/8 headless.** ⚠ **Also fixed a P0 it
  exposed**: PR #530's `?bomtree` hook referenced top-level `const qs` in its TDZ → "Cannot access 'qs'…" threw on EVERY
  modeller load (killing `__sceneReady`) on origin/main; hoisted `const qs` above the hooks (no-flag/?bomtree/?strwalk all
  recover sceneReady=true). LESSON: local `~/bim-ootb` was 11 commits STALE (missing `bom_tree.js`) → masked the bug;
  always test against `origin/main`, not the local checkout. Bridge gained a datum-snap (authoring grid ≠ walker datum).
  **(8) GENERALISE ✅ DONE 2026-06-26 — W-STR-GENERAL-SC 6/6** (`swDeriveSemiGrid` in `str_walker.js` +
  `scripts/witness_str_general_sc.js`). The SAME engine on **SC (Schependomlaan, IFC2x3 residential)**, faithful to the
  convention — **DROP ARC ONLY, then APPLY THE WALK** (extracted STR = oracle, never input): C1 SEMI-GRID **13×12 from
  879 wall centerlines** (zero STR input — the spec's "missing 4th handle", non-circular), C2 **70.1% of STR beams sit
  on the ARC-derived grid** (STR walks FROM ARC), C3 same cited rule 77.6% conform, C4 **0 plates → tessellation=null,
  fabricates nothing** (non-invent keystone), C5 **wall-bearing** (0 ARC columns → the walk imposes NO column frame —
  a DIFFERENT structural system than column-framed Terminal, surfaced not hidden), C6 abstract/grep-clean (same fns ran
  on Terminal+SC). The Terminal witnesses seeded the grid from STR columns (mildly circular); SC's ARC-driven SEMI-GRID
  is the purer, convention-faithful form. **For live SC the bridge's `swbInit` should pick `swDeriveSemiGrid` (ARC walls)
  when ARC columns are absent (wall-bearing) vs `swDeriveGrid` (column-framed) — a follow-up.**
  (3) `ad_str_pattern` persistence.
- **FUTURE — CERTIFIED-STR PATTERN LIBRARY (user 2026-06-26):** a smaller building (SC) carries *lesser-spec'd* STR, so
  a later session should **extract CERTIFIED STR from smaller buildings (and others) to correlate a PATTERN OF COMMON
  USAGE** — i.e. mine `ad_str_pattern` rows (framing topology + member sizing by building type/size/span-band) the way
  `ad_mep_pattern` was mined for MEP. The walk then defaults to the common-usage pattern for a building's class when the
  oracle is thin. This is item (3) generalised: not one Terminal pattern, but a building-type-keyed library.
```
