# Ontological BOM Extraction — Analysis (lane open 2026-06-23)

## ⚠ DO NOT REMOVE — scope + standing rules
Analyse, then (only on instruction) make BOM extraction ONTOLOGICAL: derive the BOM from the IFC relationship
graph (meaning), not from geometric heuristics. NON-INVENT — every BOM parent/child/qty must trace to an IFC
relationship or an extracted value, never a guess. Oracle for any fix = the Java-compiled `output.db` (≡
`extracted.db` by RosettaStone); the JS drop is the candidate, NEVER the yardstick. Read the log after every run.
Read the Java + the extractor before changing either. This doc is analysis; do not implement without instruction.

## THE QUESTION
"How to extract BOMs ontologically." A BOM is a recipe — parent → N children × qty, recursively
(building→floor→room→set→leaf). **Ontological extraction = project the IFC's own relationship graph onto that
recipe.** The opposite (what fails) is GEOMETRIC extraction — inferring the recipe from positions/dimensions
(bbox containment, position-pattern clustering). The IFC already states the structure; we should read it, not
re-derive it from coordinates.

## GROUND TRUTH (measured + code-cited this session — do not re-derive)
### A. The extractor ALREADY captures the ontology graph
`DAGCompiler/python/extractIFCtoDB.py` writes the relationship graph: `spatial_structure`
(IfcBuilding→IfcBuildingStorey→IfcSpace with `parent_guid`, L117/759-780), `rel_contained_in_space`
(element→space, L174/1049), `rel_aggregates` (parent→child decomposition, L179/1171-1193), `material_layers`,
and reads `IfcRelDefinesByType` (L939). So a CURRENT extraction is ontology-bearing.

### B. The Java BOM-build is HALF ontological today
- **Hierarchy (building→storey→room/space): ONTOLOGY-DRIVEN.** `ScopeBomBuilder.java:232-249` joins
  `spatial_structure` + `rel_contained_in_space`; room/SET membership is GUID membership in
  `rel_contained_in_space` (`ScopeBomBuilder.java:100-107`) — no point-in-room geometry. `BomHierarchyBuilder`
  links storeys via the IFC-discovered child BOMs. Storey from `elements_meta.storey` (`ExtractionPopulator.java
  :290-299`), Z-band only as a NULL fallback.
- **Instance grouping / qty (verb factoring): GEOMETRY-DRIVEN — the one non-ontological step.**
  `VerbFactorizer.java:118-122` groups by resolved `mProductId` (dims/alias), then `VerbDetector.java:195-287`
  detects TILE/LINE/ROUTE/FRAME/CLUSTER **from centroids/dims**. It does NOT use `IfcRelDefinesByType` (the IFC's
  own "same type" relation) and does NOT use `IfcRelVoidsElement`/`IfcRelFillsElement` (opening↔host↔filler).
- **Fallback when the graph is ABSENT: graceful degradation to flat geometry.** `ScopeBomBuilder` catches the
  missing-table SQLException and returns empty (L258-260); `StructuralBomBuilder` leaves `parentToChildren` empty
  (L115-116) → every element becomes a flat LEAF under a Z-band FLOOR, qty via geometric CLUSTER. No rooms, no
  assemblies. **This is exactly the SC path.**

### C. Why SC diverged (root, proven)
SC's shipped extraction (`deploy/buildings/SampleCastle_extracted.db`) is FLAT — it has `elements_meta` +
`element_transforms` only; `spatial_structure`, `rel_contained_in_space`, `rel_aggregates`, `material_layers` are
ALL MISSING (made by an older/thinner extractor). So the ontology-driven hierarchy got nothing → degraded to flat
leaves + Z-band floors + geometric verb factoring. The measured drop-vs-extraction divergence (WINDOW 77 vs 259,
WALL 508 vs 879, DOOR 387 vs 205, COVERING 1594 vs 1214; positions metres off) is the signature of that
degradation, NOT a drop bug. SH/DX pass because their extractions carry the graph.

## THE ONTOLOGY → BOM MAP (the target; each row is a relationship, not a coordinate)
| BOM construct | IFC relationship (ontology) | current source | gap |
|---|---|---|---|
| BUILDING → FLOOR | `IfcRelAggregates` (Building▸Storey) / `spatial_structure.parent_guid` | ✅ graph | re-extract SC |
| FLOOR → ROOM/SET | `IfcRelAggregates` (Storey▸Space) + `spatial_structure` | ✅ graph | re-extract SC |
| element → ROOM | `IfcRelContainedInSpatialStructure` (`rel_contained_in_space`) | ✅ graph | re-extract SC |
| ASSEMBLY → parts | `IfcRelAggregates` (element decomposition) | ✅ `rel_aggregates` | re-extract SC |
| line qty=N grouping | **`IfcRelDefinesByType`** (instances of one IfcXxxType) | ❌ geometry (product_id+pattern) | LOGIC |
| opening / door / window ↔ host wall | **`IfcRelVoidsElement` + `IfcRelFillsElement`** | ❌ not used (geometry) | LOGIC |
| MEP run / system membership | **`IfcSystem` / `IfcRelAssignsToGroup`** | ❌ not used | LOGIC |
| layer build-up (covering/wall) | `material_layers` (`IfcMaterialLayerSet`) | partial | LOGIC |

## TWO GAPS, IN ORDER
**Gap 1 — DATA (cheap, unblocks SC + every flat building).** Re-extract `Ifc2x3_SampleCastle.ifc`
(`…/DAGCompiler/lib/input/IFC/Ifc2x3_SampleCastle.ifc`) with the CURRENT `extractIFCtoDB.py` so the extraction
carries `spatial_structure`/`rel_contained_in_space`/`rel_aggregates`. Then the already-ontological hierarchy path
builds SC's rooms/floors the way it does SH. This alone likely closes most of the count divergence (rooms appear,
elements stop collapsing into Z-band flat leaves). **Verify caveat:** IFC2x3 (SC) vs IFC4 (SH) — confirm the
extractor's relationship queries resolve on 2x3 (IfcRelContainedInSpatialStructure/IfcRelAggregates exist in 2x3;
IfcSpace boundaries differ). This is the first thing to test.

**Gap 2 — LOGIC (the genuinely ontological upgrade; the part that "maybe ontological", per user).** The ONE
geometry-driven step — instance grouping/qty — should become ontology-first:
- group qty by **`IfcRelDefinesByType`** (true type identity) before/instead of dims+position pattern;
- attach openings to their host via **`IfcRelVoidsElement`/`IfcRelFillsElement`** (a window is NOT a free leaf —
  it FILLS a void in a wall), which is likely why SC over/under-counts WINDOW/DOOR/COVERING;
- assign MEP runs via **`IfcSystem`** rather than ROUTE geometry.
Geometry (TILE/LINE/CLUSTER) stays as a LAST-RESORT factoriser, flagged as such, only when the ontology is silent.
This needs the extractor to ALSO emit those relations (voids/fills, type guids, system membership) — check which
are already captured and add the missing ones NON-INVENT.

## VALIDATION (no collusion)
1. Re-extract SC (Gap 1) → inspect: does `spatial_structure`/`rel_contained_in_space` now populate? rooms?
2. Compile SC through the Java (`run.sh SC`-equivalent — SC is NOT yet a compile target; SH/DX/TE are) →
   produces the first SC `output.db`. THIS is the independent oracle (≡ re-extracted.db by RosettaStone).
3. Memory-drop `SC::BUILDING_SC_STD` (BOM-only) → `witness_drop_vs_compiler.js` vs SC `output.db`. Pass = ≤1mm
   per-class congruence, as SH (0.07mm) and DX (≈2mm) already show. extracted.db stays out of the witness.
4. Generalise: once the path is ontology-clean, `PER_BLDG` can be manifest-driven over all `*_BOM.db` — but each
   building is only PROVEN once it has a Java-compiled oracle.

## OPEN DECISIONS (for the user — do not pick unilaterally)
- **D1:** Start with Gap 1 (re-extract SC) and re-measure before any logic change? (Recommended — may close most of
  the gap with zero code, and isolates how much is DATA vs LOGIC.)
- **D2:** Is SC to become a registered Java compile target (to mint its `output.db` oracle)? Without it, SC can be
  rendered but never PROVEN.
- **D3:** Scope of Gap 2 — type-based qty only, or also voids/fills (openings) and systems (MEP)? Each needs the
  extractor to emit the relation first.

## CONSTRUCTION VERBS vs PLACEMENT VERBS — the frozen-middle gap (authoring view, 2026-06-23)
The ontology question above is the EXTRACTION view. This is its AUTHORING-side restatement: the Modeller is a
tool for crafting the **STR/ARC** shell (wall / slab / roof — *stretch along axis, snap to grid, drag*) while
**FFE leaves are fixed** (you place a dining set, you don't elongate it). That split is **already the compiler's
architecture** — not a wish:
- **STR/ARC is genuinely parametric** in the compiler: `WallSpec` (record: `start, end, WallThickness, height,
  openings` — `builder/WallSpec.java:33`) generates the extruded solid from a baseline; `WallGenerator` builds
  walls from SpaceType rules + room topology (`opens_to`, shared edges); `HipRoofMesh`/`GableRoofMesh` generate
  from `pitch_deg/ridge_axis/ridge_length_mm/overhang_mm` (`mesh/HipRoofMesh.java:55`); slabs from
  `extend_x_m/extend_y_m/thickness_m/ceiling_z_m` (`dsl/SlabSpecAD.java:18`); all sweep a `ProfileRegistry`
  profile; `StructuralPlacer.extractGridlines` is a structural grid; `PartyWallConstraint` is an inter-element
  constraint. **FFE is correctly catalog-placement** (`LibraryFactory`/`LibraryPlacementSpec`, place mesh by
  `geometry_hash`).
- **The Modeller already has the authoring substrate** (`bim-ootb/viewer/`): `bonsai_outliner.js` (answers the
  roadmap's open-q (a) — the Outliner EXISTS), `bonsai_sketch.js`, `bonsai_library.js`, and a `grid_*` family
  with `grid_drag`/`grid_kinematics`/`grid_recompose`/`grid_dim_chains`/`bonsai_gridmove` = the "snap to a grid
  you drag, dependents re-flow" machinery.

**THE GAP (precise):** the parametric construction lives at the two ENDS (compiler generator + Modeller grid
editor) but the BOM/catalog MIDDLE is **geometry-frozen**. The compiler generates walls parametrically *at
compile time from rules/topology*; the drop freezes the result to boxes (`allocated_*_mm` + a *placement*
`verb_ref`). There is **no construction verb in the BOM** — checked `bom/walker/*`; the only parametric mention
*disclaims* it (`InterimWorkshop.java:19`, MEP). Consequence: a dropped wall comes back as a frozen box (movable,
but you can't drag its baseline / change thickness / re-flow it when its gridline moves); and a wall crafted in
the Modeller has no canonical parametric row for the compiler to read back *as a `WallSpec`*.

**THE FIX — a new verb CLASS, not new plumbing.** Today's 8 verbs (TILE/LINE/ROUTE/FRAME/STACK/CLUSTER/SPRAY/
SINGLE) are ALL *placement of fixed instances* (the FFE side). The missing category is **CONSTRUCTION verbs**:
"sweep this profile along this baseline to this height, bound to grid datum G." The compiler can already CONSUME
them (`WallSpec` etc.); the BOM just doesn't CARRY them and the drop doesn't RECONSTRUCT them. Plumbing already
exists: `verb_ref TEXT` (the carrier), `lod_parametric_mesh_param` (roof param-bag precedent), `host_element_ref`
(for openings binding), `material_name/rgba`. So this is a **`verb_ref` grammar extension**, parallel to the
TILE/CLUSTER grammar VerbFactorizer already writes.

### Construction-verb BOM row sketch (PROPOSED — non-invent; each field maps to a compiler param)
A construction leaf is a `M_BOMLine` whose `verb_ref` names a construction verb; the compiler's existing
generator consumes it instead of placing a mesh. Profiles/thickness reference existing registries (no free-form).
| verb_ref grammar (proposed) | maps to compiler | grid binding |
|---|---|---|
| `WALL:sx,sy,sz:ex,ey,ez:thk:h` (+ child `OPENING` rows via `host_element_ref`) | `WallSpec(start,end,WallThickness,height,openings)` | endpoints reference gridline ids (drag gridline → re-emit endpoints) |
| `SLAB:ext_x,ext_y:thk:ceil_z` (footprint polygon if non-rect via points) | `SlabSpecAD.SlabEntry(extendX,extendY,thickness,ceilingZ)` | footprint corners snap to grid |
| `ROOF:pitch_deg:ridge_axis:ridge_len:overhang` (+ footprint) | `HipRoofMesh`/`GableRoofMesh` `lod_parametric_mesh_param` | footprint snaps to wall tops |
| `OPENING:u,v:w,h` (param on host wall) | `OpeningSpec` (already on `WallSpec.openings`) | `host_element_ref` → wall row = the void/fill bind |
Notes: `thk` is the `WallThickness` enum token (4 values only — `topology/WallThickness.java:17`), NOT free-form,
so the row stays NON-INVENT. The grid-binding column requires the extractor to emit `IfcGrid` datums (today only
`StructuralPlacer` reads gridlines internally) — that's the one genuinely new extractor output; until then
endpoints are absolute and "drag re-flow" degrades to per-element move (honest fallback, like CLUSTER).

### Sequencing implication for the roadmap
- The **Outliner should split the tree by verb class**: *constructions* (editable parametric STR/ARC — afford
  baseline/thickness/grid edits) vs *placed leaves* (fixed FFE — afford move/hide/swap only). They afford
  different edits, so the tree must distinguish them. `bonsai_outliner.js` already exists to extend.
- Order: (1) DATA/coverage (the 362-drop, IFC2BOM) and descriptive drop-well stay first — they're the proven
  side; (2) the construction-verb grammar is the LOGIC upgrade that turns the assembler into a modeller — do it
  after the drop-well generalises, and prove it the same way (drop a WALL row → compile via `WallSpec` →
  `witness_drop_vs_compiler.js` ≤1mm). Geometry-frozen boxes stay the LAST-RESORT (CLUSTER-equivalent) when no
  construction verb is authored — preserving the non-invent guarantee.

## WHERE WE ARE
Analysis only. No code changed. Prior lane (drop fidelity) is ✅ DONE/LIVE: SH/DX round-trip to the compiler
(per-instance dims + verb expansion; W-DROP-VS-COMPILER hard gate; bim-ootb sw v706). See
`prompts/RESUME_FACING_GATE.md`. SC `PER_BLDG` wiring was added then REVERTED (test only).
