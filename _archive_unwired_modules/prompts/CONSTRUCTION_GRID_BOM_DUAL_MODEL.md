# Construction Grid ⊗ BOM — the Dual-Model Authoring Substrate (spec, opened 2026-06-25)

## ⚠ DO NOT REMOVE — scope + standing rules
Spec for the Modeller's deepest layer: **a building is TWO coupled models — a Construction-Grid datum model and a
BOM layer bound to it.** Drop/edit the grid → the BOM re-folds. This formalises the "2D Grid Red Pill" flow
(ground grid first → adjust X/Y → walls/rooms/openings → raise Z → storeys appear → Z-runners auto-insert) into a
signed, foldable substrate, and folds in the **§SHELL-N-ZSPAN** layering (shell + typical-storey×n + measured
Z-spanning leaves).
- **NON-INVENT (PRIME RULE):** every layer/relationship below is **DERIVED from a MEASURED property** of the real
  model (captured yaw, measured Z-span, placement datum order, column cadence) — **never from an IFC class name, a
  name token, or a hand-curated template.** Where the source IFC carries the fact (e.g. `IfcRelFillsElement`),
  recover it at extraction; where it does not, derive it from measurement and stamp it as an **enrichment property**
  with honest provenance (`derived:*`), never `ifc_extract`.
- **Oracle** for any geometry claim = the **pristine `extracted.db` / raw IFC extraction** (frame-invariant rosetta),
  NOT the cooked `output.db` (which carries compile-introduced perturbations). `output.db` is only a secondary
  recipe-suffices cross-check. Read the log after every run; the §-log/rosetta is the proof, not "curl is live."
  See [[prompts/SPATIAL_DEPENDENCY_GRAPH.md]] §ORACLE — this doc's §SHELL-N-ZSPAN feeds that graph's `instanced-by`/Z
  edges; its generative half (datum-order, typical×n, grid-as-author) is the layer ABOVE the graph engine.
- **Acceptance discipline (carried from the orientation lane):** every classifier here must grep-clean of element
  class/role names — `grep -E "Ifc(Stair|Lift|Elevator|Riser|Wall|Door)|STAIR|LIFT|RISER"` over the Z-span / shell
  classifiers must hit ZERO. The trigger is the **measurement**, not the name.

Companions: [[MODELLING_FROM_BOM_CASCADE]] (the 4-piece stack + 2D×3D grid lattice — this spec sharpens piece 2
into a first-class model), [[CONSTRUCTION_VERB_BOM_GRAMMAR]] (WALL/SLAB/ROOF/OPENING verbs that re-fold on grid
edit), [[RESUME_GIGO_FACING_TEST]] (the measure-don't-whitelist doctrine this generalises), [[FUSED_4D5D_WEDGE_LANE]]
(the op-log the grid edits sign into). Prior art (read before coding): `deploy/dev/grid_dims.js`
(`detectGrids` from column cadence), `grid_overlay.js` (2D/3D + Z-level grids, `buildZGrids`), `grid_scissors.js`
(per-cut grid reposition, all 3 axes), `grid_drag.js` (gridline drag-edit), `bom_extract.js` (storey→disc→class
derivation + structural envelope + cadence).

## THESIS (the one line)
**The model isn't drawn on a grid — the grid IS a model, and the BOM is a second model bound to it; every datum
edit (stretch a bay, raise/add a storey) is a signed op the BOM folds from.** Revit draws elements that reference
gridlines; here the grid is a portable, foldable datum lattice and the BOM hangs off it, so "add a floor" or
"foundation 2 m wider" is ONE op whose fold re-derives every dependent measurement above — geometry, schedule, cost.

## THE TWO MODELS (and their coupling)
| | **Model 1 — Construction Grid (the WHERE-datum)** | **Model 2 — BOM Layer (the WHAT/HOW)** |
|---|---|---|
| Substance | 2D plan lattice (X/Y gridlines, bay spacings) × 3D level lattice (Z storey planes, floor-to-floor) | `BUILDING → SHELL → [TYPICAL_STOREY ×n] → DISCIPLINE → ASSEMBLY → LEAF` cascade |
| Origin | **datum** — set by the first placement (see Datum-Order below), holds world LBD anchor | relative offsets (tack chain) measured against the grid |
| Edit | drag a gridline / raise a level / change n | re-fold: every bound child re-derives its position/extent |
| Today | DETECTED read-only overlay from columns (`grid_dims.detectGrids`) + Z grids (`buildZGrids`) + per-cut reposition (`grid_scissors`) | flat storey→disc→class tree (`bom_extract`), no n-factorization, no Z-span layer |
| Target | AUTHORING substrate: dropped first (or set on building-drop), draggable, **signed** | binds to grid datums; grid edit ⇒ BOM fold |

**Coupling rule:** a BOM leaf's coordinate is a **function of grid datums**, not a free world XYZ. So editing the
grid is the ONLY way to move structure, and the move is a re-fold — never a vertex transform (stretch ≠ scale,
per [[MODELLING_FROM_BOM_CASCADE]] §2D×3D). This is why "two models" matters: the grid is the small, editable,
portable thing; the BOM is the large derived thing.

## DATUM-ORDER DEDUCTION — sequence as a first-class signal (NEW)
Beyond measured *geometry* (yaw, Z-span), placement **order/time** is a measured signal we can deduce structure from:
- **"Is a BOM occurring at the same time? If later → it is a subsequent BOM set."** Concurrent placement ⇒ same
  set/layer; later placement that rests ON earlier geometry ⇒ a dependent child set. Construction order = the
  dependency DAG. (Folds straight into the 4D schedule: the op-log's `WHEN` already orders ops.)
- **Foundation-first = the dynamic datum.** The FIRST-level placement (the ground slab / footing) sets the XY
  datum + origin; **everything above is measured relative to it.** We do NOT assume a fixed prefab template — we
  read the first placement and let it *determine dynamically all measurements above it.* (Prefab thinking, but
  dynamic: the "assumed overall design" is replaced by "the design implied by what was laid down first.")
- **Provenance:** the deduced dependency edge is an **enrichment property** (`derived:datum_order`), never invented
  — it is read from the real op sequence / placement Z-stacking, and is falsifiable (B rests on A iff B.minZ ≈
  A.maxZ within tol AND B placed after A).

## §SHELL-N-ZSPAN — the three derived layers (greenlit 2026-06-25)
All three are derivable from data already in `bom_extract.js` (envelope, storeyHeights, cadence, per-class AABB) —
no new extraction, no invention. Measured on Terminal (48,428 elems) this session; values below are real.
1. **SHELL (outer envelope, n-independent skin).** The structural-only envelope (`ENV_CLASSES` =
   column/wall/slab/beam/roof/footing/curtainwall) promoted to the root BOM node: roof + ground slab + perimeter
   structure. Already computed in `bom_extract` — formalise as the first drop.
2. **TYPICAL_STOREY × n (the Z-factorization).** Detect repeated storeys by **signature** (uniform floor-to-floor
   + matching per-discipline class-count vectors within tol) → collapse storeys 2..k into `TYPICAL_FLOOR × n`.
   This is the **Z-axis analogue of the roof TILE verb** (qty=N), and it is ROBUST to dirty storey labels (Terminal
   has 23 mixed Malay/English names + 33,848 "Unknown" — similarity is measured, not read from the `storey` field).
   `n` = qty on the TYPICAL_FLOOR order line.
3. **Z-SPANNING leaves (vertical circulation / risers / vertical structure).** Classify a leaf as Z-spanning iff
   its **measured** Z-extent crosses ≥2 storey bands. Proven abstract on Terminal — the family self-identifies by
   height, with NO class whitelist:
   | class | measured height | really is |
   |---|---|---|
   | IfcPipeSegment | up to 40 m | risers |
   | IfcColumn | 45 m | full-height vertical structure |
   | IfcWall | 44 m | curtain-wall facade |
   | IfcRailing / IfcStairFlight | 4–11 m | stair circulation |
   These parent to **(SHELL, n)**, with extent = **f(n)**. Collapse n→1 → span→0 → **they auto-vanish** ("single
   storey left → stairs/lifts/risers gone"). Derived, not coded.

## ASI / OrderLine / Validation — why the earlier attempt now coheres
The prior "Validation of Attributes in OrderLine/Product/ASI format" lacked the semantic that makes it land: it is a
**dependency edge**, not arbitrary attribute checking.
- `n` = **qty** on the TYPICAL_FLOOR `C_OrderLine` (a storey is a product repeated n times — identical to TILE qty=N).
- A Z-spanning leaf's extent = **`M_AttributeSetInstance`** attribute `span = f(n)` (riser length = n × storeyHeight;
  stair flights = n − 1).
- **"Validation of Attributes"** = the rule asserting the linkage: `assert riser.length == n × storeyHeight`,
  `assert stairFlights == n − 1`. The validation IS the dependency between the shell's qty and the Z-spanning ASI.
  This is the three-layer validation (DocEvent / ASI / AD_Val_Rule) of `TerminalAnalysis §S100-p84`, now anchored.

## THE GRID-FIRST AUTHORING FLOW (the "Red Pill", made primary)
Today's grid is detected FROM a finished model (read-only). Invert it into the authoring loop:
1. **Drop the ground grid** (or, on whole-building drop, the 3D grid is set to the building's cadence immediately —
   `grid_dims.detectGrids` already derives X/Y spacings from columns; reuse as the seed lattice).
2. **Adjust X/Y** — drag gridlines (`grid_drag`) → SHELL footprint re-folds (slab corners on grid; walls' baselines
   on grid).
3. **Play the next layer** — walls / rooms / openings fold onto the grid (construction verbs,
   [[CONSTRUCTION_VERB_BOM_GRAMMAR]]); openings are void-children of walls (can't divorce).
4. **Raise Z / set n** — storeys appear by replicating TYPICAL_STOREY; **Z-runners (stairs/lifts/risers) auto-insert**
   because their BOM reference is span = f(n) (they exist *because* n>1).
5. Every step is a **signed op** in `kernel_ops`; geometry is the fold of the log over the (grid, BOM) pair.

## ENRICHING IFC — the properties our model adds
IFC carries placement, voids/fills, aggregates, psets — but NOT: grid-datum binding, typical-storey multiplicity (n),
measured Z-span classification, or datum-order dependency. Our model **stamps these as enrichment properties** so any
model (even a thin/loose IFC, or a non-building) can be expressed in BOM format:
| enrichment property | derived from (measured) | provenance tag |
|---|---|---|
| `grid_binding` (which X/Y/Z datum a leaf is anchored to) | nearest gridline / level plane within tol | `derived:grid` |
| `storey_multiplicity n` | typical-storey signature match | `derived:typical_storey` |
| `z_span_layer` (is it a Z-runner; span=f(n)) | measured Z-extent vs band count | `derived:zspan` |
| `datum_dependency` (rests-on / subsequent-set) | placement order + Z-stacking | `derived:datum_order` |
| `host_ref` (opening↔wall) | **recovered** `IfcRelFillsElement` (present in source!) | `ifc_extract` (recovery) |
Never overwrite authored IFC; these are additive, falsifiable, and round-trip back out (the IFC-export worker can
emit them as a custom Pset so the enriched model is portable).

## THE UNIFYING DOCTRINE (one rule, three axes)
**Derive the relationship/layer from a MEASURED property, never from the IFC class name.** Same rule, three axes:
- **Facing (XY-rotation):** captured yaw, not a `hasFront` whitelist → bridge-general. [[RESUME_GIGO_FACING_TEST]]
- **Vertical layer (Z):** measured Z-span, not an `IfcStair/Lift/Riser` whitelist → shopfloor/bridge-general.
- **Sequence (T):** datum order, not an assumed prefab template → dynamic.
Every "2-layers-away / grandchild" relationship (a riser is a child of *shell+n*, not of a storey) is itself
DERIVED — exactly as set-membership is from real `IfcRelAggregates` and facing from real captured yaw.

## MAPPING TO EXISTING CODE
- `grid_dims.js` — `detectGrids`/`generateDimensions` already mine X/Y cadence from columns → seed for Model 1.
- `grid_overlay.js` — `buildZGrids` draws Z-level planes → the 3D level lattice surface; `grid_drag.js` = the edit verb.
- `grid_scissors.js` — per-cut reposition (all 3 axes) → the "raise Z, see the storey" interaction substrate.
- `bom_extract.js` — structural envelope (SHELL), `storeyHeights` (n detection), `cadence` (grid seed), per-class
  AABB (Z-span test) all ALREADY computed; the work is factorization + the Z-span classifier + emitting n/span.

## WITNESSES (spec-first; oracle = pristine extracted.db / raw extraction; no invention)
- **W-SHELL-ENVELOPE** — SHELL structural envelope from `bom_extract` == structural-only AABB of the raw extraction
  (Terminal/SC), Δ ≤ tol. Proves the shell layer is the real skin, not all-elements.
- **W-TYPICAL-N** — typical-storey factorization round-trips: `expand(TYPICAL_FLOOR × n)` reproduces each real
  storey's per-class counts (within signature tol); `n` matches the measured repeated-band count. RED if a storey
  is force-merged that isn't actually similar.
- **W-ZSPAN-ABSTRACT** — the Z-span classifier (a) tags every measured Z-runner (pipe risers/columns/facade/stair)
  on Terminal, (b) **grep-clean of class names**, (c) collapse n→1 ⇒ Z-span set empties. RED if any class name
  appears in the classifier or a Z-runner is missed.
- **W-DATUM-ORDER** — on a real building, "B rests-on A" edges match Z-stacking + op order (falsifiable); foundation
  is the deduced datum root. RED if a dependency is asserted without a measured rests-on.
- **W-GRID-REFOLD** — drag one X gridline → SHELL + bound walls re-fold (length changes, thickness held), folded
  output == compiler re-run at the new datum. The stretch≠scale proof, on the grid model.

## STATUS / NEXT
Ideation→spec (no code yet; spec-first per PRIME RULE). Grounded this session in real source IFC (SH/Terminal/Bridge)
+ Terminal extraction measurements. **Order of build (each gated on its witness, oracle = pristine extracted.db / raw extraction):**
1. `bom_extract` enrichment: emit SHELL node + `storeyHeights`-based **n detection** (W-TYPICAL-N) + **Z-span
   classifier** (W-ZSPAN-ABSTRACT) — cheapest, all data already present.
2. Datum-order deduction (W-DATUM-ORDER) — read placement order / Z-stacking; stamp `datum_dependency`.
3. Grid-as-author: promote `grid_*` overlay to a signed, draggable datum the BOM binds to (W-GRID-REFOLD), gated on
   construction verbs being IN the BOM ([[CONSTRUCTION_VERB_BOM_GRAMMAR]] — the frozen middle).
**Parallel, separate lane:** the orientation abstraction (kill `hasFront`/`_inheritHostRotation`, recover
`rel_fills_host`) — doctrinally one with this (measure-don't-whitelist) but independently shippable; it is the
door-facing fix already decided (full chain + deploy).
