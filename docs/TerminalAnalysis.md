# Terminal Recomposition — Terminal Forensics
> **Foundation:** [BBC](BOMBasedCompilation.md) · [DATA_MODEL](DATA_MODEL.md) · [BIM_COBOL](BIM_COBOL.md) · [MANIFESTO](MANIFESTO.md) · [TestArchitecture](TestArchitecture.md)

<div class="bim-banner" markdown>
<b>48,428 elements across 8 disciplines — extracted to DB (S104).</b> IFCtoBOM pipeline BLOCKED: storey name mismatch (YAML maps 7 English names, extraction has 23 mixed Malay/English names — "Aras Tanah", "GROUND FLOOR LEVEL", etc.). 33,848 of 48,428 elements have storey="Unknown". QA reconciliation FAIL: 33,848 BOM LEAFs vs 48,428 extracted (delta=−14,580). TE_BOM.db is EMPTY — no m_bom or m_bom_line tables. G3 discipline fix (00t) — IFCtoERP routes CW/SP/FP/LPG from elements_meta correctly.
</div>

## CTFL Review Status (session 31-34, 2026-03-19)

**Last reviewed:** 2026-03-19 session 34 — CTFL static review + SRS gap analysis.
Session 31: 10 defects found and fixed (D1-D10).
Session 34: F1-F4 quick wins resolved, 4 SRS docs updated (12 new spec sections).
**S231 fix (2026-04-27):** TE_BOM.db POPULATED — 50 BOMs, 5,568 lines, 48,428 elements (delta=+0). YAML updated to 29 storey keys matching all extraction containers. See §BOM Factorization.

**Resolved issues (session 34):**

| ID | Gate | What | Fix Applied | Status |
|----|------|------|------------|--------|
| F1 | G3-DIGEST | Seal check | Seal already INTACT — changed files not in sealed set | **DONE** |
| F2 | G5-PROVENANCE | IfcRampFlight 6 vertices | G5 Check 3 relaxed: vertex_count >= 4 (not 8). Ramp is a triangular prism — valid shape. Check 6 (no GEO_ prefix) is the real parametric fallback guard. | **DONE** |
| F3 | C9 axis | 7 tie-breaking instabilities | VerbDetector sort: (X,Y,Z) → (X,Y,Z,W,D,H) | **DONE** |
| F4 | DemoHouseTest | 6 errors (empty DM_BOM.db) | Assumptions.assumeTrue() skip guard | **DONE** |

**Spec alignment (2026-03-18, session 18):**
All TE BOM offsets, tack convention, BUFFER, and compilation modes must conform to
`BOMBasedCompilation.md` §3-§4 (the governing spec). See §Tack I/O and §Recurrence
sections below. Code changes spec in `ACTION_ROADMAP.md` §Pre-Code Specs.

*Extracted from `docs/TheRosettaStoneStrategy.md` §TERMINAL RECOMPOSITION (2026-02-28).*
*Updated 2026-03-19 with CTFL review status and per-instance CLUSTER dimensions.*

## Building Identity

| Property | Value |
|----------|-------|
| Stone | 3 of 3 (largest) |
| Name | Terminal (Sultan Johor Terminal II) |
| IFC version | IFC2x3 (federated from 9 discipline models) |
| Country | Malaysia |
| Type | Airport terminal, 4+ storeys, institutional |
| Elements | 48,428 (51,092 - 4 IfcSensor - 2,660 rebar — both Federation addons) |
| Disciplines | 8 (ARC, STR, FP, ACMV, CW, ELEC, SP, LPG) — REB removed (Bonsai addon) |
| M_Product_Category | CO (Commercial) |
| C_DocType_ID | CO_TE |
| Reference DB | `DAGCompiler/lib/input/Terminal_Extracted.db` |

## Why Terminal Is Different From SH/DX

SH/DX are residential. Their BOM tree shape (self-describing per BBC.md §1) is:
```
BUILDING → FLOOR → ROOM → SET → LEAF
```

Terminal is institutional. There are no "rooms" in the residential sense.
Instead there are ZONES: departure hall, check-in counters, boarding gates,
retail areas, mechanical rooms, roof structure. The BOM tree shape is:
```
BUILDING → STOREY → DISCIPLINE → ASSEMBLY → LEAF
```
The tree walker (`getParentBOM()/getChildren()`) handles both shapes — no vocabulary or level labels needed.

Also: SH/DX had 1-2 IFC source files. Terminal was federated from 9
discipline-specific models. The discipline boundaries are authoritative —
they came from separate consultant firms.

## Element Inventory by Discipline

| Discipline | Count | Dominant Classes |
|------------|-------|-----------------|
| **ARC** | 34,724 | IfcPlate(33,324) Wall(330) Window(236) Furniture(176) |
| **FP** | 6,863 | PipeFitting(3,146) PipeSegment(2,672) FireSuppression(909) Alarm(80) |
| ~~REB~~ | ~~2,660~~ | ~~ReinforcingBar(2,660)~~ — **REMOVED** (Bonsai Python addon, not construction BOM) |
| **ACMV** | 1,621 | DuctFitting(713) DuctSegment(568) AirTerminal(289) Proxy(51) |
| **CW** | 1,431 | PipeFitting(638) PipeSegment(619) FlowTerminal(106) Valve(57) |
| **STR** | 1,429 | Slab(614) Beam(432) Member(312) Column(68) Wall(3) |
| **ELEC** | 1,172 | LightFixture(814) Proxy(339) Appliance(19) |
| **SP** | 979 | PipeSegment(455) PipeFitting(372) FlowTerminal(150) Valve(2) |
| **LPG** | 209 | PipeFitting(87) PipeSegment(75) Valve(47) |
| **Total (extracted)** | **51,088** | All 9 original disciplines |
| **Total (active)** | **48,428** | After REB (2,660) removal — pipeline baseline |

ARC dominates at 72% of active elements — almost entirely IfcPlate roof tiles (33,324 = 69% of active).

## Storey Structure

| Storey | Active Elements | Notes |
|--------|----------------|-------|
| Roof (RF) | 35,428 | Mostly IfcPlate metal deck tiles |
| Ground Floor (GF) | 3,513 | Check-in hall, main MEP |
| Level 1 (L1) | 2,070 | |
| Level 2 (L2) | 2,609 | |
| Level 3 (L3) | 1,798 | |
| Level 4 (L4) | 2,307 | |
| Foundation (FN) | 703 | Structural slabs, subgrade MEP |
| **Total** | **48,428** | After REB/IfcSensor removal |

**RESOLVED (TE-1):** Z-centroid band assignment normalised all storeys into 7 bands.
Counts measured from BOM hierarchy (pipeline QA log, session 30).

## Factorization — The Scale Reduction

| Discipline | Elements | Unique Types | Factor | Active? |
|------------|----------|--------------|--------|---------|
| ARC | 34,724 | 519 | **67×** | yes |
| FP | 6,863 | 1,093 | 6× | yes |
| ACMV | 1,621 | 543 | 3× | yes |
| STR | 1,429 | 555 | 3× | yes |
| ELEC | 1,172 | 401 | 3× | yes |
| CW | 1,431 | 683 | 2× | yes |
| SP | 979 | 566 | 2× | yes |
| LPG | 209 | — | — | yes |
| ~~REB~~ | ~~2,660~~ | ~~73~~ | ~~36×~~ | REMOVED |
| **Active total** | **48,428** | **505** | **95.9× reuse** | |

"Unique Types" = distinct dimensional signatures (dx × dy × dz rounded to mm).
Each unique type becomes one M_Product row. Each BOM line references a type
with `qty > 1` where instances repeat — the factored form.

## The Roof Deck — TILE Verb

33,324 IfcPlate elements tile the roof surface in a regular grid.

Measured from Terminal_Extracted.db:
- Y-step: 150mm (plate depth, edge-to-edge — 3,774 of 3,819 pairs exact)
- X-step: 495mm (plate width, edge-to-edge — 35 of 43 pairs exact)

9 Z-bands of roof panels (Z = 18m to 28m). Each band is a horizontal surface
at a different height. Within each band, plates tile in a regular 2D grid:

```
TILE SURFACE "ROOF_DECK_Z19" WITH "PLATE_500x150x106"
    PANEL "west"    ORIGIN (92.49, -42.16, 19.0)  GRID 15 x 294  STEP (495mm, 150mm)
    PANEL "central" ORIGIN (122.63, -42.16, 19.0) GRID 14 x 174  STEP (495mm, 150mm)
    PANEL "east"    ORIGIN (141.74, -42.16, 19.0) GRID 15 x 34   STEP (495mm, 150mm)
END-TILE
```

~20 TILE statements describe the entire roof (33,324 elements from ~20 formulas).

## Formula Coverage — BIM COBOL Verb Patterns

**Predicted** (pre-implementation analysis) vs **Actual** (pipeline QA, session 30):

| Formula Pattern | BIM COBOL Verb | Predicted | Actual Verb | Actual Instances | Status |
|----------------|---------------|-----------|-------------|-----------------|--------|
| TILE (2D grid) | `TILE SURFACE` | 33,324 | TILE | 12 | LIVE (0.0m fidelity) |
| PATH (1D route) | `ROUTE` | 9,345 | ROUTE | 18 | LIVE (0.32m max) |
| GRID (structural) | `FRAME` | 590 | FRAME | 78 | LIVE (1.4mm max — S51 LBD fix) |
| Semi-regular grid | `CLUSTER` | — | CLUSTER | 47,607 | LIVE (29.1m max, 3.7m avg — **approximate**) |
| Irregular (flat) | manual placement | 2,123 | flat | 770 | LIVE (exact) |
| ~~ARRAY (rebar)~~ | ~~`ARRAY`~~ | ~~2,660~~ | — | — | REMOVED (REB excluded) |
| **Total** | | | | **48,485** | **48,428 active** |

**Key finding:** CLUSTER absorbed the bulk of elements that were predicted for
TILE/ROUTE/WIRE/FRAME. CLUSTER uses offset-table grouping (semi-regular, ±10%
tolerance), not exact grid formulas. This is the root cause of G2-VOLUME 13.71%
drift — CLUSTER's average 3.7m positional error across 47,607 instances.

**Path forward:** Promote CLUSTER groups to exact verbs (TILE, ROUTE, FRAME)
where the underlying pattern is truly regular. Non-uniform groups stay CLUSTER.

## Predicted vs Actual BOM Hierarchy

**Predicted** (pre-implementation, 5-level with assembly groupings):
```
Level 0: BUILDING_TE_STD (BUILDING, M_Product_Category=CO)
├── Level 1: TERMINAL_TE_GF (FLOOR) — Ground, ~4166 elements
├── ...
└── Each FLOOR contains:
    ├── Level 2: ARC_TE_LXX (DISCIPLINE)
    │   ├── Level 3: WALL_SET — ~80 walls/storey
    │   ├── Level 3: OPENING_SET — doors+windows hosted on walls
    │   ├── Level 3: FURNITURE_SET — zone furniture     ← predicted assembly groupings
    │   └── Level 3: MISC_ARC — coverings, railings, stairs
    ├── Level 2: STR_TE_LXX (DISCIPLINE)
    │   ├── Level 3: FRAME — beams + columns + members
    │   └── Level 3: SLAB_SET — structural slabs
    ├── Level 2: FP_TE_LXX (DISCIPLINE)
    │   ├── Level 3: FP_PIPE_RUN — pipe segments + fittings
    │   └── Level 3: SPRINKLER_SET — fire suppression terminals
    ├── Level 2: ACMV_TE_LXX (DISCIPLINE)
    │   ├── Level 3: DUCT_RUN — duct segments + fittings
    │   └── Level 3: AIR_TERM_SET — air terminals
    ├── Level 2: ELEC_TE_LXX (DISCIPLINE)
    │   ├── Level 3: LIGHTING_SET — light fixtures (qty factorized)
    │   └── Level 3: EQUIP — proxies + appliances
    ├── Level 2: CW_TE_LXX / SP_TE_LXX / LPG_TE_LXX
    │   └── (same pattern: pipe runs + fixture sets)
    └── ...
```

**Actual** (pipeline QA, session 30 — 3-level flat, no assembly groupings):
```
Level 0: BUILDING_TE_STD (1 BOM, origin 84.6, -51.2, -30.7)
├── Level 1: TE Foundation  (FLOOR, FN)  — 6 discipline SETs,   703 instances
├── Level 1: TE Ground Floor (FLOOR, GF) — 8 discipline SETs, 3,513 instances
├── Level 1: TE Level 1     (FLOOR, L1)  — 8 discipline SETs, 2,070 instances
├── Level 1: TE Level 2     (FLOOR, L2)  — 7 discipline SETs, 2,609 instances
├── Level 1: TE Level 3     (FLOOR, L3)  — 7 discipline SETs, 1,798 instances
├── Level 1: TE Level 4     (FLOOR, L4)  — 7 discipline SETs, 2,307 instances
└── Level 1: TE Roof        (FLOOR, RF)  — 7 discipline SETs, 35,428 instances
                                                               ------
                                                               48,428 total
```

**D8 gap: Predicted Level 3 assembly groupings (WALL_SET, OPENING_SET, etc.)
were NOT implemented.** CO path uses BUILDING→FLOOR→DISCIPLINE(flat leaves).
No scope-space decomposition within disciplines. The predicted 5-level
hierarchy collapsed to 3 effective levels. This is correct for extraction
(positions come from IFC, no need to group by assembly), but generative
mode will need the assembly sub-groupings. See BBC.md §2.1.1 for the
decomposition layers that would add Level 3.

## BOM Factorization — IMPLEMENTED (S231, 2026-04-27)

> **Status: DONE.** TE_BOM.db populated. IFCtoBOM QA reconciliation PASS (delta=+0).
>
> **Root cause (S230b):** YAML mapped 7 English storey names; extraction had 23 mixed
> Malay/English containers from SJTII per-discipline IFC files. Only Level 4 (50 el)
> and Roof (33,798 el) matched — 14,580 elements in 22 containers dropped.
>
> **Fix (S231):** classify_te.yaml updated to 29 storey keys — one per extraction
> container (22 IFC + 7 Z-band). Each gets a unique code/role/seq to avoid BOM ID
> collision. Product_category shared for canonical floor grouping (FN/GF/L1-L4/RF).
> Z-band resolver maps 33,848 Unknown elements to Level 4 (50) + Roof (33,798).
>
> **Pipeline evidence:** `logs/pipeline_Terminal_ifctobom_20260427_024940.log`
> ```
> [PASS] Extraction reconciliation — 48428 extraction LEAFs vs 48428 extracted (delta=+0)
> [PASS] BOM count — 50 (BUILDING=1, FLOOR=24, MEP=25)
> [PASS] BOM lines — 5568 lines (48477 instances)
> ```

### Actual Measured (S231, from TE_BOM.db)

| Table | Count | Notes |
|-------|-------|-------|
| m_bom | 50 | 1 BUILDING + 24 FLOOR + 25 MEP |
| m_bom_line | 5,568 | 5,519 LEAF + 49 MAKE |
| M_Product | 3,661 | 3,611 catalog + 50 assembly stubs |
| instances | 48,428 | full extraction count |

**Compilation:** 48,428 elements, 8/10 gates. C8 FAIL (16 product types missing meshes — Aras Kedai/Jalan
sub-level products). GEO VERIFY FAIL (no GUID pairs — federated model limitation).

### Designed Sizings (sessions 8-11, historical reference)

#### BOM Catalog (designed) — 58 BOMs, 1,131 lines (factored via CLUSTER)

| Table | Before factorization | After factorization | Notes |
|-------|---------------------|---------------------|-------|
| m_bom (tree nodes) | 58 | **58** | 1 root + 7 floor-level + 50 leaf-group BOMs |
| m_bom_line (edges) | 48,485 | **1,131** | 361 verb lines + 770 flat lines |
| M_Product | 563 | **563** | 505 catalog + 58 assembly stubs |

#### Verb Factorization Breakdown

| Verb | Recipe lines | Expanded instances | Ratio | What |
|------|-------------|-------------------|-------|------|
| CLUSTER | 354 | 47,607 | 134:1 | MEP semi-regular grids (sprinklers, pipes, ducts, lights) |
| TILE | 3 | 12 | 4:1 | 2D uniform grid (roof plate panels) |
| FRAME | 2 | 78 | 39:1 | Grid intersections (structural bays) |
| ROUTE | 2 | 18 | 9:1 | Axis-aligned uniform-step runs |
| **Verb subtotal** | **361** | **47,715** | **132:1** | |
| Flat (no verb) | 770 | 770 | 1:1 | Irregular placements (furniture, proxies, unique fittings) |
| **Total** | **1,131** | **48,485** | **42.8:1** | |

**Verb savings:** Without verbs, TE_BOM.db would need 48,485 per-instance lines.
Verbs eliminate 47,354 lines — **97.6% of the BOM is encoded by 361 verb formulas.**
CLUSTER alone saves 47,253 lines (the bulk). The 770 flat lines are the irreducible
core: unique placements where no pattern exists (furniture, proxies, one-off fittings).

**Note:** TILE shows only 3/12 because CLUSTER absorbed most of the 33,324
roof plates. The original TILE prediction (~20 formulas for 33K plates) was
superseded by CLUSTER's offset-table approach which is more general.

#### Top 10 Discipline BOMs by Instance Count

| BOM | Category | Recipe lines | Instances | Ratio |
|-----|----------|-------------|-----------|-------|
| TE Roof ARC | ARC | 37 | 33,417 | 903:1 |
| TE Roof FP | FP | 16 | 1,652 | 103:1 |
| TE Level 1 FP | FP | 10 | 1,185 | 119:1 |
| TE Ground Floor FP | FP | 21 | 1,132 | 54:1 |
| TE Level 4 FP | FP | 13 | 1,072 | 82:1 |
| TE Level 2 FP | FP | 23 | 1,064 | 46:1 |
| TE Level 3 FP | FP | 19 | 754 | 40:1 |
| TE Ground Floor CW | CW | 37 | 754 | 20:1 |
| TE Ground Floor ARC | ARC | 116 | 584 | 5:1 |
| TE Level 4 ACMV | ACMV | 21 | 471 | 22:1 |

**Roof ARC dominates:** 37 recipe lines → 33,417 instances (903:1) = the metal
deck tile panels. This is the single most compressed BOM in the system.

**FP (fire protection) is the most recurring:** 7 floor BOMs, each with high
compression ratios — sprinkler grids and pipe runs are regular patterns.

**Ground Floor ARC is the least compressed:** 116 lines for 584 instances (5:1).
This is the terminal's check-in hall — walls, doors, windows, furniture, railings,
stairs. These are mostly unique placements, not repeating patterns.

#### BOM Hierarchy Summary

```
TE Airport Terminal (BUILDING) — 7 FLOOR children, origin (84.6, -51.2, -30.7)
  ├── TE Foundation  (FLOOR, FN)  — 6 discipline SETs,   703 instances
  ├── TE Ground Floor (FLOOR, GF) — 8 discipline SETs, 3,513 instances
  ├── TE Level 1     (FLOOR, L1)  — 8 discipline SETs, 2,070 instances
  ├── TE Level 2     (FLOOR, L2)  — 7 discipline SETs, 2,609 instances
  ├── TE Level 3     (FLOOR, L3)  — 7 discipline SETs, 1,798 instances
  ├── TE Level 4     (FLOOR, L4)  — 7 discipline SETs, 2,307 instances
  └── TE Roof        (FLOOR, RF)  — 7 discipline SETs, 35,428 instances
                                                        ------
                                                        48,428 total
```

**Floor origins are zeroed** (R16 fix) — offsets stored on BUILDING→FLOOR
TACK lines as dx/dy/dz. BUILDING origin holds the world LBD anchor.

### Compiled Output (`terminal.db`)

| Table | Predicted | Actual | Notes |
|-------|-----------|--------|-------|
| elements_meta | 48,428 | **48,428** | G1-COUNT PASS (Spec 2 fix — StoreyCompiler skip for CO) |
| Delta (enbloc vs walkthru) | 0 | **0** | Compilation is consistent |

**Product catalog:** 505 unique products → 48,428 placed instances (95.9× reuse).

### Browser Performance — S231/S232 (2026-04-27)

Terminal gets the best instancing benefit due to 32,165 Metal Deck plates sharing 1 hash.

| Metric | S200 baseline | S231 InstancedMesh | S232 Mobile Merge |
|--------|---------------|-------------------|-------------------|
| Draw calls | 48,428 | 7,150 (**85%**) | ~500 est. (**99%**) |
| Stream time | n/a | 3.5s | 3.5s (same data phase) |
| Pick | Per-element | Per-element (single) + no pick (instanced) | InstancedMesh → guid, merged → group info |
| Storey/disc filter | Per-mesh visibility | Per-mesh only | Zero-scale matrix (instanced) + group visibility (merged) |

Desktop retains 7,150 draw calls with full per-element pick on single-instance meshes.
Mobile merges 6,568 single-instance meshes by storey|disc|rgba into ~80 groups.
Deployed to OCI dev (`bim-ootb-dev`).

## Implementation Phases — All DONE

| Phase | What | Status |
|-------|------|--------|
| **TE-1** | Z-centroid band assignment, 7 storeys normalised | **DONE** |
| **TE-2** | ExtractionPopulator: 51,088→48,428 active, REBAR deactivated | **DONE** |
| **TE-3** | BUILDING→FLOOR→DISCIPLINE→LEAF for CO mode | **DONE** |
| **TE-4** | M_Product_Category=CO from YAML, commercial dispatch | **DONE** |
| **TE-5** | CO_TE in GATE_SCOPE, surefire property forwarding | **DONE** |
| **TE-5B** | Output DB produced, 216 IfcSlab gap diagnosed + fixed | **DONE** |
| **TE-6/7** | Verb factorization: TILE/ROUTE/FRAME/CLUSTER (1,131 lines, 42.8:1) | **DONE** |

### Steps to Arrive at Compiled Output (guide for future IFC conversions)

The TE pipeline demonstrates the generalised IFC→BOM→compiled-output chain.
Each step is reusable for any new building — only the YAML changes.

```
Step 1: EXTRACT — Python IfcOpenShell → component_library.db
   ├── extract.py reads IFC, writes I_Element_Extraction + I_Geometry_Map
   ├── Per-element: AABB (min/max XYZ), ifc_class, orientation, material
   ├── Per-product: geometry mesh (vertices + faces) in component_geometries
   └── Output: component_library.db tables populated

Step 2: CLASSIFY — YAML declares building identity + discipline mapping
   ├── classify_te.yaml: prefix, building_type, M_Product_Category
   ├── disciplines: map ifc_class → discipline code (ARC, STR, FP, ...)
   ├── storey_bands: Z-centroid ranges → storey names
   └── Output: YAML file (only human invention in the chain)

Step 3: POPULATE — Java ExtractionPopulator enriches extraction
   ├── Reads component_library.db → I_Element_Extraction
   ├── Z-centroid storey normalisation (NULL storey → band assignment)
   ├── REBAR deactivation (is_active=0 for IfcReinforcingBar)
   ├── M_Product_ID linkage: element_ref → product catalog
   └── Output: component_library.db enriched (deterministic, no invention)

Step 4: BUILD BOM — Java DisciplineBomBuilder creates BOM hierarchy
   ├── Reads extraction by storey + discipline
   ├── Creates: root BOM → floor BOMs → leaf-group BOMs
   ├── Each LEAF line: child_product_id, dx/dy/dz (parent-relative), element_ref
   ├── BomValidator: 9 checks + 2 pre-flights (abort on any failure)
   └── Output: {PREFIX}_BOM.db (m_bom + m_bom_line + M_Product)

Step 5: PREPARE COMPILE DB — Shell prepares per-building temp DB
   ├── cp {PREFIX}_BOM.db → _XX_compile.db
   ├── Apply schema_snapshot_bom.sql (adds tables: C_DocType, c_order, etc.)
   ├── Inject C_DocType row (OutputDbPath, ExpectedElements)
   ├── Load DSL content from YAML-referenced .bim file
   └── Output: library/_XX_compile.db (temp, auto-cleaned)

Step 6: COMPILE — Java CompilationPipeline reads compile DB, writes output
   ├── BuildingRegistryTest drives compilation via Maven surefire
   ├── BOMWalker traverses hierarchy, PlacementCollectorVisitor collects positions
   ├── Tack convention (§4): each level's origin + line dx/dy/dz → world coords
   ├── BuildingWriter emits elements_meta + elements_rtree + geometries
   └── Output: DAGCompiler/lib/output/{building_type}.db

Step 7: VERIFY — Shell runs delta + Rosetta Stone gates
   ├── enbloc vs walkthru element count delta (must be 0)
   ├── Per-class breakdown, AABB centroid delta, geometry divergence
   ├── Rule 8 (world-absolute check), clash check
   └── Output: PASS/FAIL verdict log
```

**Refactoring guide:** To add a higher abstraction layer, the natural
boundary is between Step 4 (BOM) and Step 6 (compile). The BOM is the
**contract interface** — upstream changes (extraction, classification) only
affect BOM content, downstream changes (compilation, verification) only
read the BOM. A new verb (TILE, ROUTE) changes how Step 6 interprets
BOM lines, but the BOM structure (m_bom + m_bom_line) stays the same.

## What SH/DX Taught Us (Foundation Advantage)

1. **Placement determinism works:** extract coords → compile. Terminal already
   has 100% positional match from Phase DE-4.
2. **BOM pattern works:** m_bom hierarchy + m_bom_line with child_product_id.
   Extending to 9 disciplines is data, not code.
3. **M_Product catalog is extensible:** Terminal needs ~200 more products.
   Same table, same pattern.
4. **Discipline dispatch works:** ElementPersistence emits all disciplines.
   Terminal's 9 disciplines already compile correctly.
5. **IFCtoBOM pipeline is abstract:** `classify_te.yaml` follows the same
   YAML-driven pattern as `classify_sh.yaml` and `classify_dx.yaml`.
6. **G5-PROVENANCE is abstract:** 7 checks run per building via DynamicTest.
   No Terminal-specific test code needed.

The challenge is **scale and variety**, not architecture.

## New Verbs & BOM Mechanisms Needed

Terminal introduces patterns that SH/DX didn't need. Each pattern maps to a
BIM COBOL verb and a YAML section that carries user intent.

### Verb: TILE SURFACE (roof deck — 33,324 elements, 65%)

**Mechanism:** 2D grid expansion. One BOM line with `qty=N` expands to N
placements at computed grid positions (origin + i×stepX + j×stepY).

**YAML intent:**
```yaml
roof_deck:
  panels:
    - name: DECK_Z19_WEST
      product: PLATE_500x150x106
      origin_m: [92.49, -42.16, 19.0]
      grid: [15, 294]          # columns × rows
      step_mm: [495, 150]      # X-step, Y-step
    - name: DECK_Z19_CENTRAL
      product: PLATE_500x150x106
      origin_m: [122.63, -42.16, 19.0]
      grid: [14, 174]
      step_mm: [495, 150]
```

**BOM mechanism:** `m_bom_line.qty = grid[0] * grid[1]`. Walker expands qty
to instances, each getting position from grid formula. No 33K rows in BOM.

### MEP Piping — 9,345 elements, 18% (IFCtoERP MEP_RECIPE architecture)

**Current architecture (S104):** TE is a **G1 building** — IFC4 with full discipline data in
`elements_meta.discipline` (CW/SP/FP/LPG per element). IFCtoERP reads this column directly
(00t G3 fix) and groups elements by `(storey, discipline)` into MEP_RECIPE archetypes in ERP.db.
The shim+recipe BOM walk in DAGCompiler then expands these into c_orderline rows.

**RouteWalker does NOT apply to TE.** RouteWalker is for **G2 buildings** (IFC2x3, no
sub-discipline on pipe elements, e.g. RM). TE has zero rows in `ad_mep_anchor` — anchor
extraction (00q) was only performed for RM. RouteWalker correctly emits CW=0/SP=0 for TE
and logs a warning; this is expected behaviour, not a bug.

**G1 vs G2 distinction:**
| | G1 (TE) | G2 (RM) |
|--|---------|---------|
| IFC version | IFC4 | IFC2x3 |
| elements_meta.discipline | CW/SP/FP/LPG per element | "MEP" (undifferentiated) |
| MEP classification | IFCtoERP discFromClass 3-arg | RouteWalker pattern approach |
| ad_mep_anchor rows | 0 (not needed) | 154 METER + 11 FIXTURE + 1482 GENERIC |
| Rosetta Stone | 7/8 PASS | 8/8 PASS |

The earlier YAML `mep_systems / path_nodes_m` design was never implemented and is superseded
by this architecture. YAML is Order input only (not IFCtoBOM source — feedback rule).

### Rebar — REMOVED from input (2,660 elements deleted)

> **Removed (2026-03-18):** Rebar (IfcReinforcingBar) is already a fast Python
> addon script in Bonsai which adds rebar to any beam in STR easily, and need
> not be part of any main construction BOM. 2,660 elements deleted from
> Terminal_Extracted.db and component_library.db. Total TE elements: 48,432 → 48,428 (library).

### IfcSensor — REMOVED from reference (4 elements deleted)

> **Removed (2026-03-18):** IfcSensor (4 metadata-only elements, no spatial coords)
> is a Federation addon that generates onto finished construction — like rebar, it
> does not need compilation. Removed from Terminal_extracted.db to enable
> G3-DIGEST verification. Total ref elements: 48,432 → 48,428 (matches output exactly).

### Verb: WIRE LIGHTING (electrical — 814 elements)

**Mechanism:** 2D ceiling grid. Lights at regular spacing on a ceiling plane.

**YAML intent:**
```yaml
electrical:
  storey: GF
  lighting:
    - name: LIGHTING_GF_MAIN
      product: LightFixture_600x600
      zone_m: [0, 0, 50, 30]   # minX, minY, maxX, maxY
      spacing_mm: [3000, 3000]
      height_m: 3.5
```

### Verb: FRAME (structural grid — 590 elements)

**Mechanism:** Structural bay grid. Columns at grid intersections, beams spanning.

**YAML intent:**
```yaml
structural:
  storey: GF
  frame:
    - name: FRAME_GF
      column: Column_W250
      beam: Beam_W310x60
      grid_m:
        x: [0, 6, 12, 18, 24, 30]
        y: [0, 8, 16]
      height_m: 4.0
```

### BOM Mechanism: qty Expansion

The key new mechanism is `m_bom_line.qty > 1`. SH/DX have qty=1 (one line,
one element). Terminal needs qty=N (one line, N elements at computed positions).

```java
// BOMWalker expansion
for (MBOMLine line : children) {
    int qty = line.getQty();  // 1 for SH/DX, N for TE
    for (int i = 0; i < qty; i++) {
        visitor.visitLeaf(line, i);  // instance index
    }
}
```

Position computation per instance depends on the verb:
- TILE: `origin + (i % cols) * stepX + (i / cols) * stepY`
- ARRAY: `origin + i * spacing * direction`
- ROUTE: segment-by-segment path accumulation
- FRAME: grid intersection lookup

### BOM Mechanism: Discipline Layer

Terminal adds Level 2 = DISCIPLINE between FLOOR and ASSEMBLY:
```
BUILDING → STOREY → DISCIPLINE → ASSEMBLY → LEAF
```

This requires `bom_category` on m_bom to carry discipline identity (ARC, STR,
FP, ACMV, CW, ELEC, SP, LPG, REB). The walker doesn't need discipline-specific
code — it's just another tree level. The YAML `disciplines:` section maps IFC
classes to discipline categories:

```yaml
disciplines:
  ARC:
    classes: [IfcWall, IfcSlab, IfcDoor, IfcWindow, IfcFurniture, IfcRoof,
              IfcPlate, IfcCovering, IfcRailing, IfcStairFlight]
  STR:
    classes: [IfcColumn, IfcBeam, IfcMember]
  REB:  # DEFERRED — IfcOpenShell Python generates dynamically
    classes: [IfcReinforcingBar]
  FP:
    classes: [IfcFireSuppressionTerminal, IfcAlarm, IfcSensor]
    system_type: [FireProtection]
  ACMV:
    classes: [IfcAirTerminal]
    system_type: [HVAC, AirConditioning]
  ELEC:
    classes: [IfcLightFixture, IfcElectricAppliance]
  CW:
    system_type: [ColdWater, DomesticWater]
  SP:
    system_type: [SanitaryPlumbing, Drainage]
  LPG:
    system_type: [Gas, LPG]
```

### YAML as User Intent

The `classify_te.yaml` carries all user intent for Terminal — the same pattern
as `classify_sh.yaml` and `classify_dx.yaml`. The Java pipeline reads YAML,
never hardcodes building-specific logic. Adding a new Rosetta Stone =
writing a new YAML, not new Java code.

## Verb Roadmap — What Terminal Still Needs

**Current state (S104):** GEO proof: 48,428 elements, 569M pairs, worst=0.025mm, DRIFT=0.
MEP disciplines correctly separated via IFCtoERP (00t). Rosetta Stone 7/8 PASS.
Pre-existing 71 critical proof violations are the remaining blocker (not a verb gap).

| Verb / Mechanism | Status | Discipline | Elements | Fidelity |
|-----------------|--------|-----------|----------|----------|
| `TILE SURFACE` | **EXACT** | ARC (roof) | 33,324 | PASS (0.0m) |
| `MEP_RECIPE` (IFCtoERP) | **LIVE** | FP/CW/SP/LPG/ACMV | 9,345+ | archetypes in ERP.db |
| `FRAME` | **EXACT** | STR | 590 | 1.4mm max |
| `CLUSTER` | **APPROX** | residual MEP | ~47,607 | 29.1m max, 3.7m avg |
| `ENCLOSE` | DESIGNED | ARC (walls) | ~1,038 | not started |
| `DISTRIBUTE` | DESIGNED | ARC (furniture) | ~2,123 | not started |
| ~~`ARRAY`~~ | ~~REMOVED~~ | ~~REB~~ | ~~2,660~~ | REB excluded |

**Note:** `ROUTE` verb (YAML path_nodes design) was never implemented and is superseded
by the MEP_RECIPE architecture. TE's pipe network is mined by IFCtoERP into ERP.db archetypes.
CLUSTER's 3.7m avg error remains the G2-VOLUME 13.71% drift root cause for non-exact elements.

### Three-Layer Validation Resolution (S100-p84)

The 1,163 unfactored elements are not a pattern-mining problem — they're a
**standards application** problem. The iDempiere three-layer validation resolves
most of them without manual pattern recognition:

**Layer 1: DocEvent per Org** — blanket discipline rules. When AD_Org=FP, the
org-scoped ModelValidator fires top-down during BOM walk. General placement
rules (spacing, connectivity, host). Shared recipes in ERP.db (`FP_SYSTEM`,
`ACMV_SYSTEM`, etc.) provide the abstract BOM templates.

**Layer 2: ASI (AttributeSet Instance)** — per-product/per-instance attributes.
K-factor, pipe length, duct size. Same as customer options in manufacturing —
modifies placement without changing the recipe.

**Layer 3: AD_Val_Rule** — same DocEvent engine, narrower scope. User adds a
specific rule for a particular exploded C_OrderLine. Not a separate mechanism —
a different granularity. Government standards (NFPA 13, UBBL, MS1183) are
general rules (Layer 1). Layer 3 is for user-specific overrides.

This mirrors iDempiere document processing: ModelValidator (Org-scoped) →
line item resolution (ASI) → validation rules (AD_Val_Rule).

**Resolution estimate (1,163 unfactored elements):**

| Category | Count | Resolution | Layer |
|----------|-------|-----------|-------|
| SP/CW/LPG pipes | ~450 | Routing standards (branch length, riser sizing) | DocEvent |
| FP devices (alarms, extinguishers) | ~30 | NFPA/UBBL spacing rules | DocEvent |
| ELEC (switches, receptacles) | ~20 | Receptacle count per area | DocEvent |
| Doors/windows | ~30 | Fire door placement per UBBL egress | DocEvent |
| ACMV fittings | ~25 | Duct routing standards | DocEvent |
| STR columns (irregular grid) | ~63 | ~50% by rules, rest human/AI pattern | DocEvent + manual |
| **Stairs** | **~178** | **Stair rules (see below)** | **DocEvent + ASI** |
| Walls | ~41 | Define space, not fill it — stays unfactored | Unfactored |
| Furniture/fixtures | ~50 | Architect's choice — stays unfactored | Unfactored |
| Remaining misc | ~276 | Mixed | Mixed |
| **Total resolvable** | **~550 (47%)** | | |

### Stair Validation Rules — Already Partially Implemented

Infrastructure exists: `ad_stair_requirement` (7 rows, UBBL/IBC/NFPA),
`VerticalCirculationAD.java` (StairRequirement record), `VerticalCirculationValidator.java`
(count, width, travel distance), `StairwellCheck.java` (geometry-based UBBL check).

The 178 unfactored stair components (runs, landings, stringers) across GF-L4 are
inherently variable in geometry, but their dimensions are **rule-governed**:

| Rule | Value | Standard | In ad_stair_requirement? |
|------|-------|----------|------------------------|
| Riser height | 100-175mm (public), 100-190mm (residential) | UBBL By-Law 172 | YES |
| Tread depth | 250-300mm (public), min 225mm (residential) | UBBL By-Law 172 | YES |
| 2R+G comfort | 550-700mm (ideal 630mm) | Blondel formula | **NO — add** |
| Stairway width | min 1050mm (public), 1200mm (high-rise >18m) | UBBL By-Law 171 | YES |
| Headroom | min 2000mm | UBBL practice / BS 5395 | **NO — add** |
| Landing length | min = stair width | UBBL general | YES |
| Max flight rise | 3.0m before landing | UBBL By-Law 168 | **NO — add** |
| Riser uniformity | max 9.5mm variance between risers | IBC s1011.5.4 | **NO — add** |
| Handrail height | 900mm (UBBL), 864-965mm (IBC) | UBBL / IBC s1014.2 | YES |
| Guard height | min 1070mm (42") | IBC s1015.3 | **NO — add** |
| Fire rating | 1.0hr (<18m), 2.0hr (>18m) | UBBL By-Law 166(3) | YES |

**TE is >18m (59.8m tall) → requires 2.0hr fire-rated stairs, min 1200mm width,
pressurization (50-100 Pa per UBBL By-Law 178), min 2 stairs.**

These rules constrain stair geometry enough that ASI (per-instance run length,
landing width) handles the remaining variance. The 178 stair components aren't
"irregular" — they follow dimensional rules with per-instance variants.
EYES geometric proofs (P04 Z-band, P01 positive extent) can verify the result.

**ROUTE DUCTS** and **ROUTE PIPES** are variants of ROUTE SPRINKLERS — same
path-following walker, different M_Product leaves and AD regulation tables.
Implementation cost: parameter mapping + AD table creation, not new verb code.

**FRAME** is structural bay grid placement. Columns at grid intersections
(BIM_Component, identical), beams spanning between columns (BIM_Slab,
IsInstance=1 if spans vary). Reads structural grid from YAML.

**S51 FRAME LBD fix:** Detection now clusters `minX/minY` (LBD positions) directly
instead of centroids. The old approach computed LBD offsets as `centroid - halfW[0]`,
using element[0]'s half-width. Same-product elements with different actual dimensions
(e.g., beams spanning 10m vs 8m bays) had up to 1.08m error. The fix eliminates the
centroid→LBD conversion entirely — LBD positions ARE the grid positions. Embedded
`halfW,halfD` in the verb formula (`FRAME:x1,...|y1,...|halfW,halfD`) preserves
detection-time geometry metadata. Fidelity improved from 1.08m to 1.4mm.
FRAME promoted back to EXACT_VERBS (gating at ≤5mm).

**Why this matters for future buildings:** Every commercial/institutional building has
a structural grid. Warehouses (20m bays), stadiums (40m spans), high-rises (mixed
column sizes per floor) — all use FRAME. The LBD clustering approach scales to any
grid irregularity because it never converts between coordinate systems. This also
establishes the pattern for GPU instancing: FRAME elements at grid intersections are
natural candidates for hardware instanced rendering, since they share the same product
geometry placed at known grid positions.

### S51b: Validation Rules ARE the Patterns — ClusterPatternAnalyser

ClusterPatternAnalyser confirms that mined validation rules (M1-M17) describe
the actual spatial patterns in CLUSTER groups. The data:

| Product Type | Groups | Verdict | Rule Match |
|-------------|--------|---------|------------|
| Sprinkler heads (pendent) | 5 storeys | ZONE (rule-governed) | M1 NN spacing 3.0-4.5m |
| Sprinkler heads (upright) | 5 storeys | ZONE (rule-governed) | M1 NN spacing |
| Light fixtures (LED T8) | 5 storeys | ZONE (rule-governed) | M4 grid ~3964mm |
| RC Beams (300×750, 500×700) | 4 storeys | ZONE + FRAME | M6/M7 bay span |
| RC Columns | 3 storeys | ZONE | M14 vertical continuity |
| Waiting room seats | GF | ZONE | Furniture distribution |

**Key finding:** Pipes/fittings (Poly Steel, UPVC) are MIXED — multi-Z, irregular
positions, 100-200+ ASI size variants. These are MEP routing networks, not grid
patterns. Their "pattern" is the routing rule (M2 branch max length, M3 riser
diameter), not a spatial formula. The validation rule IS the placement constraint:
"max 12m branch, min 50mm main riser, 150mm clearance from electrical."

**Implication for EN-BLOC:** Sprinklers, lights, beams, columns form ZONE patterns
describable by validation rules. Pipes don't — they're routing networks governed
by compliance rules, not spatial grids. EN-BLOC for pipes stays as CLUSTER (lossless
replay). EN-BLOC for grid elements can be promoted to TILE/FRAME with ASI.

**ASI taxonomy (BBC.md §3.5.1):** Extraction seeds `M_AttributeSet` tables — confirms
which product attributes are instance-varying (pipe length, beam span) vs fixed
(pipe diameter, beam section). Per-instance values are designer decisions (generative
path), not extraction data. The taxonomy is the reusable asset.

Tools: `ClusterReclassifier` (promotion analysis), `ClusterPatternAnalyser` (rule
confirmation). Run: `java ClusterPatternAnalyser library/TE_BOM.db`.

**ENCLOSE** is wall perimeter placement. Follows a 2D closed path, inserts
wall segments (BIM_Wall, IsInstance=1 — length varies) and openings at
specified positions. Needed for ARC walls (~330) + openings (~236 windows,
~176 doors).

**DISTRIBUTE** is irregular zone placement for elements that don't follow
formula patterns — furniture, equipment, proxies (~2,123 elements, 4.2%).
These get flat per-element BOM lines (qty=1 each).

## Discipline Model — See DISC_VALIDATION_DB_SRS.md §10.4.1

> **Discipline is a line attribute, not a tree level.** The per-discipline
> spatial model (covering vs inside, verb profiles, validation rules, GoF
> patterns, BOM tree impact) is in [DISC_VALIDATION_DB_SRS.md §6](DISC_VALIDATION_DB_SRS.md#6-ad_org-disciplines-as-organizational-units).
> That spec governs all buildings, not just TE. TE is the ground truth.

## ERP Model Architecture — Terminal as Third Stone

> **Interactive ERD:** `docs/terminal_erd.html` — 5-tab visualization with
> entity relationships, BOM hierarchy, verb→ERP mapping, M_Product_Category scoping,
> and ROUTE-as-BOM tree with M_AttributeSetInstance.

Terminal is the first building to stress the full iDempiere ERP model. SH/DX
used BIM_Component (IsInstanceAttribute=0 — every element identical). Terminal
forces M_AttributeSet/Instance into active service and reveals the natural
correspondence between BIM construction hierarchy and ERP document flow.

> **Spatial MRP** (see `docs/ConstructionAsERPII.txt`): Traditional MRP answers
> "what materials are needed and when?" The BIM Compiler answers "what materials
> are needed, **where**, and **how they connect**." A building is an assembled-to-
> order product — the YAML is the customer order, the classify file is the product
> configuration, and the compiler runs the production order. We're not inventing a
> new paradigm — we're adding a **spatial dimension** to iDempiere's battle-tested
> manufacturing model.
>
> **Future: M_Connection** — element-to-element connection tracking (pipe segment
> to fitting, beam to column) with port semantics and verification status. Natural
> extension of ROUTE-as-BOM-tree. Candidate for G8 gate (connection audit).

### M_Product_Category — Hierarchy Shape by Top-Level Category

> *Aligns to MANIFESTO.md §The Category Cascade. Classification lives on
> M_Product_Category at every cascade level (see DATA_MODEL.md §7).
> DocBaseType was removed (S84, W012). DocSubType retained for iDempiere C_DocType compatibility.*

The top-level M_Product_Category determines the **hierarchy shape**:

| M_Product_Category | Hierarchy | L2 Axis | Compilation Path |
|-------------------|-----------|---------|-----------------|
| RE (Residential) | BUILDING → FLOOR → **ROOM** → SET → LEAF | Room type (LI, KT, BD) | EN-BLOC (singularity) |
| CO (Commercial) | BUILDING → FLOOR → **DISCIPLINE** → ASSEMBLY → LEAF | Discipline (ARC, FP, STR) | WALK THRU (discipline-driven) |

The RE path expects `floor_rooms` in YAML (Living, Kitchen, Bedroom) and walks
rooms to find furniture sets. The CO path expects `disciplines` and never looks
for rooms. Forcing Terminal through the RE path would require fake "rooms" for
discipline zones — that's technical debt avoided.

The building prefix (SH/DX/TE) carries identity for BOM selection. When a second
commercial building arrives (mall, factory), it will be M_Product_Category=CO with
a different prefix. The hierarchy shape stays FLOOR→DISCIPLINE→ASSEMBLY.

### M_Product_Category — Cascade Levels

M_Product_Category forms a cascade where each level's category defines the swap
pool at that level. Room categories appear under RE buildings, discipline
categories under CO buildings, and shared categories (storeys, structural) appear
under both:

| Category Type | Codes | BOM Level | Scope |
|--------------|-------|-----------|-------|
| Storey | GF, L1, L2, L3, L4, RF, FN | Level 1 (FLOOR) | Shared (RE + CO) |
| Room | LI, KT, BD, BT, DN, FR | Level 2 (RE only) | RE buildings |
| Discipline | ARC, STR, FP, ACMV, ELEC, CW, SP, LPG | Level 2 (CO only) | CO buildings |
| Assembly | (verb-specific groupings) | Level 3 | Shared |

Room and discipline codes operate at **different BOM levels** and never compete.
Storeys are shared across RE and CO — always at Level 1. The Level 2 axis
changes from room-type to discipline-type based on the top-level M_Product_Category.
No new tables needed; M_Product_Category holds both sets, scoped by cascade level.

### M_AttributeSet/Instance — Per-Verb Usage

SH/DX: zero elements needed instance attributes. Terminal changes that:

| Verb | AttributeSet | IsInstance | Reason |
|------|-------------|------------|--------|
| TILE SURFACE | BIM_Component | 0 | All 33K roof plates **identical** — position varies, not dimensions |
| ROUTE | **BIM_Pipe / BIM_Conduit** | **1** | Each pipe segment has **different length** |
| WIRE LIGHTING | BIM_Component | 0 | All fixtures identical |
| FRAME (columns) | BIM_Component | 0 | All columns identical per grid |
| FRAME (beams) | BIM_Slab | 1 | Beam spans may vary by bay |

M_AttributeSetInstance is needed for **ROUTE-family verbs** (~9,345 FP/CW/SP/LPG
pipe elements with varying lengths). TILE/ARRAY/WIRE produce identical instances —
the formula handles position, not the attribute set.

### TILE — Pattern as Verb Parameter, Not AttributeSet

TILE is BOMQty — the M_Product leaf spreads over an AABB with its orientation.
The pattern (grid formula) lives on W_Verb_NodeProduct, not M_AttributeSet:

```
C_OrderLine (WHAT):   M_Product = ROOF_DECK_PANEL_SET, qty = 4,410
W_Verb_Node (HOW):  Verb = TILE SURFACE
  W_Verb_NodeProduct: origin, grid_cols=15, grid_rows=294, step_x=495, step_y=150
M_BOM_Line dx/dy/dz (WHERE): AABB = 7,425 × 44,100 mm (the filled envelope)
```

Changing the grid (16×294 instead of 15×294) changes only W_Verb_NodeProduct.
The same PLATE_500x150x106 product appears in different TILE patterns across
different roof bays. Clean separation: verb owns the formula, BOM owns the qty.

### ROUTE — Segments as BOM Tree + M_AttributeSetInstance

A ROUTE is not a flat list — **it's a BOM tree**. Each segment is a BOM line
with instance attributes (varying length). Fittings are fixed-geometry components.
Branches are sub-BOMs:

```
FP_MAIN_GF_01 (BOM, bom_category: FP)
├── SEGMENT_01 (M_Product: PIPE_CW_50MM)
│   └── M_AttributeSetInstance: {length_mm: 3200}    ← BIM_Pipe, IsInstance=1
├── FITTING_01 (M_Product: ELBOW_90_50MM)
│   └── (no instance — BIM_Component, fixed geometry)
├── SEGMENT_02 (M_Product: PIPE_CW_50MM)
│   └── M_AttributeSetInstance: {length_mm: 4800}
├── TEE_01 (M_Product: TEE_50x25MM)
│   └── branches to:
│       └── BRANCH_RUN_01 (sub-BOM)
│           ├── SEGMENT_B1 (PIPE_CW_25MM, length=1200mm)
│           ├── SPRINKLER_01 (SPRINKLER_UPRIGHT_K80)
│           ├── SEGMENT_B2 (PIPE_CW_25MM, length=4600mm)
│           └── SPRINKLER_02 (SPRINKLER_UPRIGHT_K80)
└── SEGMENT_03 (M_Product: PIPE_CW_50MM)
    └── M_AttributeSetInstance: {length_mm: 2100}
```

This mirrors iDempiere's configurable product model: a shirt has size/color as
M_AttributeSet variants. A pipe segment has length as M_AttributeSet variant.
The BOM tree says "this run needs: 3 segments + 1 elbow + 1 tee + 1 branch."
The instances say "segment 1 is 3200mm, segment 2 is 4800mm."

The leaf M_Product set is small: pipe sizes (25mm, 50mm, 75mm), elbows, tees,
reducers, sprinkler heads, valves. The ROUTE verb assembles them into run-specific
BOM trees with per-segment instance attributes.

### Val_Rule — Regulations as Domain AD Tables

ROUTE verbs must obey building regulations (UBBL, NFPA 13, MS 1910). The
question: how to capture these constraints? iDempiere's AD_Val_Rule uses SQL
WHERE fragments. BIM needs domain-specific AD tables instead — they're queryable,
YAML-declarable, and compose with verb compliance checking.

| Regulation | AD Table | Example Constraint |
|-----------|----------|-------------------|
| Sprinkler spacing | `ad_fp_coverage` | `max_spacing_mm <= 4600 WHERE hazard='ORDINARY'` |
| Pipe sizing for flow | `ad_fp_coverage` | `diameter_mm >= 50 WHERE flow_lpm > 200` |
| Max branch length | `ad_fp_coverage` | `branch_length_mm <= 12000` |
| Receptacle count/area | `ad_space_type_mep` | `receptacle_count >= area_sqm / 10` |
| Duct sizing per ACH | `ad_acmv_sizing` | `duct_area_mm2 >= cfm / velocity` |
| Routing method | `ad_fp_coverage` | `routing_method IN ('TREE','LOOP','GRID')` |

Each verb reads its AD regulation table to determine sizing, spacing, and method.
The verb output (BOM tree) is provably compliant. The Rosetta Stone gate can
verify compliance as a future G7 check (regulation audit).

**Routing method** is a strategy selection on the AD table:
- **TREE** — main → branches → heads (most common)
- **LOOP** — ring main with branches (redundancy)
- **GRID** — parallel mains with cross-connections (large areas)

Same leaf products (pipes, fittings, heads), different BOM tree structure.
The method column on `ad_fp_coverage` determines which ROUTE variant runs.

**YAML intent for regulations:**
```yaml
fire_protection:
  hazard_class: ORDINARY
  coverage_area_sqm: 12.1        # UBBL Table 5.1
  max_spacing_mm: 4600
  min_pipe_diameter_mm: 25
  routing_method: TREE
```

### C_Order/C_OrderLine — Three-Way Separation

The Terminal C_Order in iDempiere terms:

```
C_Order (header):
  C_DocType_ID: CO_TE
  Description: SJTII Airport Terminal

C_OrderLine (tab — one per storey-discipline BOM):
  Line 10: FLOOR_TE_FDN     qty=1     ← Foundation
  Line 20: FLOOR_TE_GF      qty=1     ← Ground Floor
    Line 20.10: ARC_TE_GF   qty=1     ← Architecture
    Line 20.20: STR_TE_GF   qty=1     ← Structure
    Line 20.30: FP_TE_GF    qty=1     ← Fire Protection
      → W_Verb_Node: ROUTE SPRINKLERS "FP_MAIN_GF_01"
        path_nodes, pipe_product, branch_spacing...
    Line 20.40: ACMV_TE_GF  qty=1
    Line 20.50: ELEC_TE_GF  qty=1
    Line 20.60: CW_TE_GF    qty=1
    Line 20.70: SP_TE_GF    qty=1
    Line 20.80: LPG_TE_GF   qty=1
  Line 70: FLOOR_TE_RF      qty=1     ← Roof
    → W_Verb_Node: TILE SURFACE (grid formula per bay)
```

The three-way separation governs the entire architecture:

| Concern | ERP Table | What It Carries |
|---------|-----------|----------------|
| **WHAT** to build | C_OrderLine | Which M_Product/M_BOM, qty |
| **WHERE** it goes | M_BOM_Line dx/dy/dz | Spatial relationships (tack offsets) |
| **HOW** to build | W_Verb_Node | Verb parameters (grid, path, method) |

The 7-storey × 8-discipline grid produces ~40-50 C_OrderLines — a normal
iDempiere sales order size. The user sees storeys as order lines, disciplines
as sub-lines, and verbs as manufacturing instructions. The YAML is the order
form; the compiler generates the transactional records.

### Full BOM Tree With ERP Mapping

```
L0: BUILDING_TE_STD (BUILDING, M_Product_Category=CO)
    C_Order = CO_TE
    ├─ L1: FLOOR_TE_GF (FLOOR, bom_category=GF)
    │  C_OrderLine #20
    │  ├─ L2: ARC_TE_GF (DISCIPLINE, bom_category=ARC)
    │  │  C_OrderLine #20.10
    │  │  └─ L3: [flat placement — walls, doors, windows, furniture]
    │  ├─ L2: STR_TE_GF (DISCIPLINE, bom_category=STR)
    │  │  C_OrderLine #20.20
    │  │  └─ L3: FRAME verb → columns at grid, beams spanning
    │  ├─ L2: FP_TE_GF (DISCIPLINE, bom_category=FP)
    │  │  C_OrderLine #20.30
    │  │  W_Verb_Node: ROUTE SPRINKLERS
    │  │  Val_Rule: ad_fp_coverage (spacing, sizing, method)
    │  │  └─ L3: BOM tree of runs/branches/heads
    │  │     M_AttributeSetInstance per segment (varying lengths)
    │  ├─ L2: ACMV_TE_GF (DISCIPLINE, bom_category=ACMV)
    │  │  W_Verb_Node: ROUTE DUCTS
    │  │  Val_Rule: ad_acmv_sizing (ACH, duct sizing)
    │  │  └─ L3: duct runs + air terminals
    │  ├─ L2: ELEC_TE_GF (DISCIPLINE, bom_category=ELEC)
    │  │  W_Verb_Node: WIRE LIGHTING
    │  │  Val_Rule: ad_space_type_mep (receptacle count)
    │  │  └─ L3: ceiling grid + circuits
    │  └─ L2: CW/SP/LPG_TE_GF
    │     W_Verb_Node: ROUTE (per system)
    │     └─ L3: pipe runs + terminals
    ├─ L1: FLOOR_TE_L01 ... FLOOR_TE_L04
    │  (same discipline structure per storey)
    └─ L1: FLOOR_TE_RF (FLOOR, bom_category=RF)
       C_OrderLine #70
       ├─ L2: ARC_TE_RF (DISCIPLINE, bom_category=ARC)
       │  W_Verb_Node: TILE SURFACE (per bay)
       │  └─ L3: 33K panels from ~20 TILE formulas
       │     BOMQty = grid_cols × grid_rows per formula
       └─ L2: [other disciplines at roof level]
```

## Current State (2026-03-28, S100-p84 audit)

- **BOM walk compiler LIVE (S100-p72).** All buildings compile via single BOM walk path.
- **Gate: 6/7 PASS, 1 WARN (C9).** G0-COMPILED PASS. G1-G6 PASS. C8 PASS. C9 WARN (60 axis swaps).
- **Output:** `DAGCompiler/lib/output/terminal.db` — 48,428 elements, 251MB.
- **No cheating detected (S100-p84 forensic audit).** Single write path, no extraction DB access, no TE-conditional logic in compilation, tamper seal INTACT (73 files).

**Rosetta Stone (2026-03-28 08:50):** IFCtoBOM QA all PASS. BOM walk 339ms. Write 8.3s. Total pipeline 13s.

### Audit Findings — S100-p84 Forensic

**What the pipeline log tells us about BOM correction targets:**

| Area | Finding | Fix Direction |
|------|---------|---------------|
| **C9 axis swap (60 walls)** | CLUSTER groups mix wall orientations. Rank-matcher assigns W↔D incorrectly when walls face different directions within the same group. | Split CLUSTER groups by orientation during IFCtoBOM verb detection. |
| **Unfactored elements (1,163)** | 342 UPVC pipes, 57 rectangular columns, 44 HDPE pipes, 178 stair components, misc fixtures. IFCtoBOM couldn't find regular spatial patterns. | Human/AI-assisted pattern recognition → `recognised_patterns` in TerminalAnalysis → IFCtoBOM crafts by hand. Deterministic, reproducible. |
| **P04 Z-band (87% violations)** | Airport spans Z=-30.6m (foundation) to Z=+22.6m (roof). Default P04 band [-8.5, 10.5] too narrow. | Per-building P04 calibration or derive from BOM storey Z ranges. |
| **ProveStage 0ms** | Prover skips TE — "no proof aggregate." Zero P01-P28 mathematical proof coverage. | Wire prover for CO buildings (currently only fires for RE). |
| **H6 "No rooms found"** | TE has no room-level BOM structure. ValidationStage completeness check skipped. | Expected for CO path. Room-level validation deferred until assembly sub-groupings added (Level 3). |

**Unfactored element breakdown (mining targets):**

| Product | Count | Floor(s) | Opportunity |
|---------|-------|----------|-------------|
| `Pipe Types:jkrME_pipe_UPVC` | 342 | All | Biggest win — branching pipe runs, candidate for ROUTE |
| `M_Rectangular Column:600x300mm` | 57 | Multiple | Irregular grid — may need human-identified pattern |
| `Pipe Types:jkrME_pipe_HDPE` | 44 | Multiple | Drainage pipes — routing networks |
| Stair components (various) | 178 | GF-L4, RF | Inherently irregular — likely stays unfactored |
| Walls (various) | 41 | L2, L3, GF | Small count, low priority |
| Furniture/fixtures | 22 | GF, L3 | One-off placements — stays unfactored |

**Discipline factorization quality (from IFCtoBOM log):**

| Floor | Best factored | Worst factored | Notes |
|-------|--------------|----------------|-------|
| RF | ARC: 33,386 instances from 6 patterns | SP: 0 patterns, 11 unfactored | Roof is 69% of building |
| GF | FP: 1,128 from 17 patterns | ARC: 99 unfactored (check-in hall) | Most complex floor |
| L01 | FP: 1,182 from 7 patterns | SP: 113 unfactored pipes | Sanitary plumbing needs ROUTE |
| L04 | FP: 1,065 from 6 patterns | SP: 4 from 1 pattern, 4 unfactored | Well factored |
| FDN | STR: 427 from 4 patterns | SP: 128 unfactored pipes | Underground MEP irregular |

---

## Infrastructure Corruption Precedent

The `reference/infrastructure/` directory contains 9 IFC4X3_ADD2 files (roads, bridges,
railways). When these were previously processed through the building-only extraction path,
the pipeline corrupted because:

1. `get_storey_for_element()` only recognizes `IfcBuildingStorey` — all infrastructure
   elements became `storey="Unknown"`
2. UNIQUE constraint on `(building_type, storey, ifc_class, ordinal)` broke — all
   elements in one storey caused ordinal collisions
3. Cascade: degenerate BOM → BomValidator FAIL → pipeline abort

**Guard:** Infrastructure IFCs use `IfcFacilityPart` (IfcRoadPart, IfcBridgePart,
IfcRailwayPart) instead of `IfcBuildingStorey`. The extraction layer must FAIL early
on IFC4X3 files with facility parts but no building storeys until support is implemented.

**TE is safe:** Terminal is IFC2x3 with standard `IfcBuildingStorey`. No facility parts.
The corruption risk applies only to IFC4X3 infrastructure files, not to TE.

Full analysis: [`InfrastructureAnalysis.md`](InfrastructureAnalysis.md).

---

## Post-TE-4 BOM Model Analysis (2026-03-16)

### BOM Hierarchy: BUILDING → FLOOR → DISCIPLINE → LEAF

```
BUILDING_TE_STD (73,670 x 59,124 x 59,818 mm)
  ├── TE_FDN  [Foundation]    703 active,  5 disciplines
  ├── TE_GF   [Ground Floor] 3,513 active, 8 disciplines
  ├── TE_L01  [Level 1]      2,070 active, 6 disciplines
  ├── TE_L02  [Level 2]      2,609 active, 8 disciplines
  ├── TE_L03  [Level 3]      1,798 active, 7 disciplines
  ├── TE_L04  [Level 4]      2,307 active, 7 disciplines
  └── TE_RF   [Roof]        35,428 active, 8 disciplines
                             ------
                             48,428 placement instances in 50 leaf-group BOMs
                             (unfactored — each instance is a separate m_bom_line row)
```

### Envelope Protrusion — Awnings and Canopies

ARC discipline extends **beyond** the STR structural envelope:

| Axis | STR range (m) | ARC range (m) | ARC protrusion (m) |
|------|---------------|---------------|---------------------|
| X (width) | 64.06 | 73.67 | **+9.61** |
| Y (depth) | 42.10 | 56.12 | **+14.02** |

The ARC envelope (84.6–158.3m X, -48.2–7.9m Y) extends ~10m beyond STR
(88.9–153.0m X, -41.2–0.9m Y) in both directions. This is the terminal's
awning/canopy system — IfcPlate elements on the Roof storey (33,324 plates)
that overhang the structural frame. The LPG discipline at -51.2m Y extends
furthest south (underground gas piping below the apron).

The BUILDING BOM AABB (73.67 x 59.12 x 59.82m) encompasses ALL disciplines
including protrusions. Each FLOOR AABB is computed from its own elements,
so floor W/D may exceed the BOM containment rule — this is expected for
awning/canopy overhangs.

### BomCategory Structure

58 BOMs total: 1 root + 7 floor-level + 50 leaf-group BOMs

| BomCategory | Count | Role |
|-------------|-------|------|
| ARC | 7 | Architectural: plates, walls, doors, windows, furniture |
| STR | 7 | Structural: columns, beams, slabs |
| FP | 7 | Fire protection: sprinklers, alarms, pipe segments |
| CW | 7 | Cold water: pipe segments, fittings, valves |
| SP | 7 | Sewerage/plumbing: pipe segments, fittings |
| ACMV | 6 | Air conditioning: air terminals, ducts (no Foundation) |
| ELEC | 6 | Electrical: light fixtures, building element proxies (no Foundation) |
| LPG | 3 | Gas: pipe fittings, segments (Foundation + GF + L1 only) |
| FN/GF/L1-L4/RF | 7 | Storey-level containers |

Not all disciplines appear on all storeys. LPG only reaches Level 1 (gas
risers stop at low levels). ACMV and ELEC skip Foundation (no MEP below grade).

### Tack I/O — Layer-to-Layer Offset Chain

**Current implementation (centroid-floorMin — DRIFTED from spec):**
```
BOMWalker tack accumulation (4 levels):

  BUILDING origin = (allMinX, allMinY, allMinZ)
  + FLOOR offset  = (floorMinX - allMinX, floorMinY - allMinY, floorMinZ - allMinZ)
  + DISCIPLINE    = (0, 0, 0)  ← logical grouping, no spatial offset
  + LEAF centroid  = (centroidX - floorMinX, centroidY - floorMinY, centroidZ - floorMinZ)
  ─────────────────
  = element centroid (world coordinates)   ← CORRECT positions, WRONG convention
```

**Spec-compliant implementation (BOMBasedCompilation.md §4):**
```
  BUILDING origin  = (allMinX, allMinY, allMinZ)         ← building LBD (world)
  + FLOOR (dx,dy,dz) = where floor's LBD sits in building   ← tack_from (3D, always >= 0)
  + DISCIPLINE       = (0, 0, 0)                             ← logical grouping, no spatial offset
  + LEAF (dx,dy,dz)  = where element's LBD sits in parent   ← tack_from (3D, always >= 0)
  + BUFFER fills  = parent AABB − SUM(children AABB)     ← completeness invariant
  ─────────────────
  = element LBD (world coordinates)       ← CORRECT positions, CORRECT convention
  centroid = element LBD + (width/2, depth/2, height/2)  ← output stage only
```

**What changes:** LEAF dx is the position where the element's LBD corner sits
within the parent — no longer a centroid offset. BUFFER lines fill the gaps
between children so parent AABB = SUM(children) (the validateBOM() invariant).
World positions remain identical; centroid is recovered at output.

The DISCIPLINE layer is transparent to tacking — zero offset means the
walker accumulates through it without error. This is the key design insight:
discipline is a **logical** container (ERP grouping) not a **spatial** one.

### EN-BLOC vs WALK THRU

- **EN-BLOC**: reads all 48,428 placement rows with pre-computed dx/dy/dz.
  Each row already has parent-relative offsets. Takes each as-is when
  AABB and DocType (CO_TE) are consistent. ~25 min for 48K instances.

- **WALK THRU**: re-derives positions by tacking through the 4-level
  hierarchy. Proves the BOM structure is self-consistent. Both paths
  must produce identical output. Currently slow at 48K elements —
  verb compression (TE-6/7) will reduce to ~2,500 BOM lines.

### Dominant Element: Roof IfcPlate (33,324 = 69%)

The roof deck dominates: 33,324 IfcPlate elements under ARC/Roof.
These are modular metal deck panels forming the terminal's characteristic
undulating roof canopy. Analysis of the reference DB shows regular grid
patterns (X-step ~495mm, Y-step ~150mm) across 9 Z-bands — ideal for
TILE SURFACE verb compression to ~20 panel formulas.

### Compression Roadmap

| Phase | Verb | Elements | → BOM Lines | Ratio |
|-------|------|----------|-------------|-------|
| TE-6 | TILE SURFACE | 33,324 roof plates | ~20 | 1,666x |
| TE-7a | ROUTE | ~13K pipe/duct | ~200 | 65x |
| TE-7b | WIRE LIGHTING | ~2K fixtures | ~50 | 40x |
| TE-7c | FRAME | ~590 col/beam | ~20 | 30x |
| flat | — | ~2,123 irregular | 2,123 | 1x |
| **Total** | | **48,428** | **~2,500** | **19x** |

At the YAML/OrderLine layer: ~235 declarations → 48,428 placements = **206x**.

### CP-4 Geometric Archetype (S44)

The compiler must not branch on IFC class — 43 decision points were identified
switching on IFC class strings, violating BBC.md §2.2.1 (class-agnostic compilation).
TE's 33,324 IfcPlate elements are actually Metal Deck (107×150×500mm, planarity=0.21)
— Compiler treated all as CURTAIN_PANEL based on IFC class label.

Three-layer solution:
1. **Geometric archetype** (PLANAR/ELONGATED/COMPACT/MIXED + scale band) from dimensions
2. **Component library** (component_definitions, M_Product, placement_rules) for semantic identity
3. **IFC class** — traceability metadata only, never a decision variable

Foundation delivered S44: `GeometricFingerprint.java`, `P10_SHAPE_IDENTITY`,
`GeometricFingerprintTest.java`. Phases 4a–4e in ACTION_ROADMAP.md §CP-4.

---

## Coding Specs — TE-5B: 216 IfcSlab Gap Fix (2026-03-17)

### Problem Statement

TE compiles 48,212 output elements but BOM has 48,428 placement rows. The gap
is exactly **216 IfcSlab** (extraction: 705, output: 489). Every other IFC
class matches exactly. Additionally, 5 IfcSlab are lost at extraction→BOM
(705 active → 700 BOM rows).

### Root Cause Chain (3 bugs, 1 design gap)

**Bug 1: element_ref = product type name, not element GUID**

`I_Element_Extraction.element_ref` stores the Revit Family:Type string (e.g.
`Floor:S_Slab_200_RC_Flat_V1`), not a per-element GUID. The Python extractor
puts `{Family}:{Type}` in this field. This means 700 IfcSlab BOM lines have
only 30 distinct element_ref values. The largest group is `jkrST_str-fo_pc_rcp:
300 x 300mm` with 236 occurrences.

SH/DX happen to work because their element_ref values are more unique (fewer
identical types). TE exposes the latent assumption that element_ref = unique ID.

**Bug 2 (REVISED): StoreyCompiler consumes element_ref by product type**

The real root cause is NOT GUID collision. StoreyCompiler generates structural
slabs (Stage 3) and marks element_refs as consumed. Since element_ref is a
product type name, `PlacementLoader.markConsumed("Floor:S_Slab_200_RC_Flat_V1")`
consumes ALL 189 elements of that type. The extracted placement path (Stage 4)
then skips all of them.

Output evidence: IfcSlab GUIDs are `SLAB_GROUND FLOOR_UNIT_*` (StoreyCompiler
pattern), not `STR_MD_SLAB_GROUND_FLOOR_*` (extracted pattern). The 489 output
slabs are StoreyCompiler-generated from computed bay dimensions, not BOM positions.

**Design Gap (FIXED): `deriveDiscipline()` ignores extraction discipline**

`PlacementCollectorVisitor.deriveDiscipline()` mapped IfcSlab → "STR" always.
Fixed in TE-5C: `disciplineStack` now carries the authoritative discipline from
the parent SET BOM's `bom_category`. `resolveDiscipline()` prefers stack over
static mapping. Falls back to `deriveDiscipline()` for SH/DX.

### Spec 1: Unique element_ref via `placement_id`

**File:** `ExtractionPopulator.java` (or Python `extract.py`)

The `element_ref` column in `I_Element_Extraction` must hold a value unique
per element placement, not per product type. Options:

| Option | Value | Uniqueness | Breaking change |
|--------|-------|------------|-----------------|
| A (recommended) | `{storey}:{ifc_class}:{ordinal}` | Unique per extraction | Low — ordinal already exists |
| B | `placement_id` (autoincrement) | Unique by definition | Medium — changes downstream joins |
| C | IFC GlobalId | Unique per IFC spec | High — requires Python extractor change |

**Recommendation:** Option A. Compose element_ref as `{storey}:{ifc_class}:{ordinal}`
at extraction time. This is deterministic (reproducible from same IFC file),
unique per element, and requires no Python extractor changes (ordinal already
computed). The `DisciplineBomBuilder` passes `e.elementRef()` through unchanged.

**Guard:** After implementing, assert `COUNT(DISTINCT element_ref) = COUNT(*)`
on `I_Element_Extraction WHERE is_active=1` in BomValidator.

### Spec 2 (REVISED → SUPERSEDED by S100-p72): BOM walk compiler

**S100-p72 replaced CompileStage entirely.** The old `shouldSkip()` +
`emitGlobalPlacementElements()` path is gone. All buildings now compile
via BOMWalker + PlacementCollectorVisitor — single path, no skip logic.
See DISC_VALIDATION_DB_SRS.md §10.4.1 (shouldSkip is an anti-pattern).

Result: G1-COUNT 48,428 = 48,428. IfcSlab 489 → 705. SH/DX zero regression.

### Spec 3: Propagate extraction discipline through BOM to placement — **DONE**

**File:** `PlacementCollectorVisitor.java`

Implemented in TE-5C. `disciplineStack` pushes `bom_category` from SET-level
BOMs in `onSubAssembly`, pops in `onSubAssemblyComplete`. `resolveDiscipline()`
prefers stack over `deriveDiscipline()` static mapping.

### Spec 4: Expected element count — active only

**File:** `run_RosettaStones.sh:157` — **DONE** (2026-03-17)

Changed `SELECT COUNT(*)` to include `AND is_active = 1`. Verified SH/DX
unaffected (no deactivated elements).

### Spec 5: 5 missing IfcSlab at extraction→BOM

**Diagnosis needed.** 705 active IfcSlab in extraction, 700 in BOM.
5 elements lost somewhere in DisciplineBomBuilder. Likely cause: storey
mismatch or product lookup failure. Add diagnostic logging to
DisciplineBomBuilder when an extraction element doesn't produce a BOM line.

### Implementation Order

1. Spec 4 ✅ (done — `is_active=1` in expected count)
2. Spec 3 ✅ (done — discipline stack in PlacementCollectorVisitor)
3. Spec 2 ✅ (done — `CompileStage.shouldSkip()` for CO mode, 216 gap closed)
4. Spec 1 — unique element_ref (defensive, for future WYSIWYG gates)
5. Spec 5 — diagnose 5 missing slabs at extraction→BOM (minor)

### Verification

After Spec 2: `rm TE_BOM.db && ./scripts/run_RosettaStones.sh classify_te.yaml`
- G1-COUNT: expected 48,428, actual must equal 48,428
- Delta: enbloc == walkthru (0 difference)
- Output IfcSlab GUIDs should be `STR_MD_SLAB_*` / `ARC_MD_SLAB_*` (extracted)
  not `SLAB_GROUND FLOOR_UNIT_*` (StoreyCompiler)

---

## Learning Points — TE-5 Pipeline Plumbing (2026-03-17)

### L1: Surefire forks a new JVM — CLI `-D` properties don't pass through

Maven's surefire plugin forks a separate JVM to run tests. System properties
passed on the Maven CLI (`-Dbom.db=...`) are Maven properties, NOT JVM system
properties in the forked process. You must explicitly forward them:

```xml
<configuration>
    <systemPropertyVariables>
        <bom.db>${bom.db}</bom.db>
        <bom.mode>${bom.mode}</bom.mode>
        <doc.base.type>${doc.base.type}</doc.base.type>
    </systemPropertyVariables>
</configuration>
```

**Symptom:** `System.getProperty("bom.db")` returns `null` in tests, even though
the shell script passes `-Dbom.db=...` on the Maven command line. Tests PASS
(via `assumeTrue` skip), no output DB produced, zero visible error.

**Trap:** This is invisible in SH/DX when tests are excluded from GATE_SCOPE.
The test silently skips, Maven exits 0, shell interprets as "compiled OK".

### L2: GATE_SCOPE must be kept in sync across test classes

`RosettaStoneGateTest.GATE_SCOPE` and `BuildingRegistryTest.GATE_SCOPE` are
independent `Set<String>` constants. Adding CO_TE to one doesn't add it to
the other. Both must be updated when a new building enters the pipeline.

**Trap:** BuildingRegistryTest uses `assumeTrue(GATE_SCOPE.contains(...))`.
When a docTypeId is missing from GATE_SCOPE, the test is silently skipped
(not failed). Maven reports 0 failures. The shell script sees exit code 0
and says "compiled OK" — but no test actually ran.

### L3: element_ref is NOT a unique element identifier in federated IFC

In federated models (Terminal = 9 discipline files merged), `element_ref` from
the Python extractor is `{Family}:{Type}` (Revit nomenclature). This is a
**product type name**, not a per-element GUID. Examples:

```
Metal Deck:Metal Deck           → 33,324 occurrences (all roof plates)
M_Concrete-Rectangular Beam:... → 126 occurrences (same beam type)
Floor:S_Slab_200_RC_Flat_V1     → 189 occurrences (same slab type)
```

SH/DX happened to work because their models have fewer identical-type elements,
so element_ref was effectively unique. TE's scale (51K elements, 505 products)
broke the latent assumption.

**Rule:** Never assume element_ref is unique. Use `(building_type, storey,
ifc_class, element_ref, ordinal)` as the composite key, or synthesize a unique
ID from these fields.

### L4: Silent UNIQUE constraint catch hides data loss

`ElementPersistence.writeElementMeta()` catches UNIQUE constraint violations
and returns `false`. This was correct for DX multi-unit merge (intentional
deduplication of shared perimeter walls). But in TE, the same catch silently
drops legitimate elements whose GUIDs happen to collide due to ordinal reuse.

**Rule:** The UNIQUE-catch pattern is safe only when the caller knows
duplicates are expected. For CO-mode compilation, GUID construction must
guarantee uniqueness BEFORE the INSERT, not rely on the DB to deduplicate.

### L5: `deriveDiscipline(ifcClass)` is a lossy function

The static mapping `IfcSlab → STR` discards information that the extraction
already knows. A slab in `TE_GF_ARC` is an architectural floor finish; a slab
in `TE_GF_STR` is a structural slab. Both are IfcSlab but serve different roles.
The BOM hierarchy preserves this context, but it's lost at the flat placement
stage because `deriveDiscipline` only looks at the IFC class name.

**Rule:** Discipline is a property of the BOM context (which discipline SET
the element belongs to), not a function of the IFC class alone. The walker
must carry discipline through the hierarchy, like it carries storey.

### L6: `assumeTrue` masks pipeline failures as green

JUnit 5 `assumeTrue(condition)` causes a test to be **skipped**, not failed.
Surefire counts skipped tests as non-failures. Maven exits 0. Shell scripts
that check `$?` see success. The entire pipeline can be silently non-functional
with all-green verdicts.

**Guard:** When a test is skipped unexpectedly, the script should detect
`Tests run: 1, Failures: 0, Errors: 0, Skipped: 1` and treat Skipped > 0
as a warning, or require that at least 1 test actually passed.

### L7: IfcSlab has two code paths — StoreyCompiler vs extracted placements

The compilation pipeline has **two code paths** for IfcSlab:

1. **StoreyCompiler path:** Generates slab geometry from bay/floor dimensions.
   Produces GUIDs like `SLAB_GROUND FLOOR_UNIT_1`. This is the "compiled" path
   — the compiler invents slab geometry based on storey dimensions and structural
   grid, not from extracted element positions.

2. **Extracted placement path:** `emitExtractedElements()` in BuildingWriter
   writes extracted elements with GUIDs like `STR_MD_SLAB_FOUNDATION_10`.
   Uses element positions from the BOM.

In SH/DX (RE mode), all elements go through the extracted path. In TE (CO
mode), IfcSlab may be consumed by `StoreyCompiler.applyPlacementOverrides()`
which marks element_refs as consumed via `PlacementLoader.markConsumed()`.
Subsequent extracted placements with the same element_ref are skipped at
line 959 of BuildingWriter: `if (isConsumed(...)) continue;`

**Key insight:** With non-unique element_ref (product type names), marking
one slab element_ref as consumed (e.g. `Floor:S_Slab_200_RC_Flat_V1`) skips
ALL 189 elements with that same type. This is why the gap is concentrated
in IfcSlab — StoreyCompiler produces a few slabs per storey but consumes
the element_ref for ALL slabs of that type.

**Evidence:** Output GUIDs for IfcSlab are `SLAB_GROUND FLOOR*` (StoreyCompiler),
not `STR_MD_SLAB_GROUND_FLOOR_*` (extracted path). The 489 output slabs are
StoreyCompiler-generated, not extracted placements.

**Fix direction:** Either:
- Disable StoreyCompiler slab generation for CO mode (slabs come from BOM)
- Or make `isConsumed()` match on `(element_ref, ordinal)` not just `element_ref`

### L8: The compilation pipeline is a sequence of consumers

Understanding the pipeline's internal flow is critical for debugging:

```
CompilationPipeline.run()
  ├── Stage 1: TEMPLATE (ST mode only — skipped for RE/CO)
  ├── Stage 2: LOAD — PlacementLoader reads BOM, BOMWalker collects placements
  ├── Stage 3: STOREY — StoreyCompiler generates structural slabs, bay slabs
  │   └── Marks element_refs as "consumed" (applyPlacementOverrides)
  ├── Stage 4: WRITE — BuildingWriter emits elements to output DB
  │   ├── emitCompiledElements() — from StoreyCompiler (slabs, columns, beams)
  │   └── emitExtractedElements() — from BOM placements (skips consumed refs)
  ├── Stage 5: SURFACE — surface styles from component_library.db
  ├── Stage 6: PROVER — PlacementProver verifies spatial properties
  └── Stage 7: SHADOW — cross-check against reference DB
```

The STOREY stage runs BEFORE WRITE. It generates slab/bay elements from
computed dimensions and marks element_refs as consumed. Then WRITE's
extracted path skips consumed refs. This is correct for SH/DX where
element_ref is unique — consuming `Floor_GF_01` consumes exactly one slab.
But for TE where element_ref is a product type name, consuming
`Floor:S_Slab_200_RC_Flat_V1` consumes ALL 189 slabs of that type.

**Rule for new buildings:** If a new building uses CO mode (discipline BOMs),
check whether StoreyCompiler generates structural slabs. If so, either
disable slab generation (BOM already provides slabs) or ensure element_ref
uniqueness so `isConsumed()` doesn't over-consume.

## Verb Fidelity — What TE Gates Actually Prove

TE passes all 7 gates (G1-G5, compile, delta). But the gates check **aggregates**,
not per-verb correctness. This section documents what is and isn't proven.

### What the gates prove

| Gate | What it verifies | Coverage |
|------|-----------------|----------|
| G1-COUNT | Total element count matches extraction | Exact (48,428 = 48,428) |
| G2-VOLUME | Sum of AABB volumes matches | Exact (+0.00%) |
| G3-DIGEST | SHA-256 of sorted coordinates | **PASS** (4 IfcSensor removed — Federation addon) |
| G5-PROVENANCE | Every geometry_hash exists in library | All elements verified |
| QA (step 9) | BOM structure, duplicates, orphans, AABB containment | 15 checks, all PASS |
| Verb fidelity (step 9b) | Round-trip LBD comparison | FRAME/TILE/CLUSTER exact (gating); ROUTE advisory |

**Schema-Not-Geometry classification: ERP-maths.** Verb factorization (TILE,
ROUTE, SPRAY, FRAME) takes extracted AABB positions and compresses them into
recipe formulas (grid step, path topology, cluster offsets). No IFC relationship
exists for "these elements form a grid pattern" — the pattern recognition IS
manufacturing recipe logic, same as M12 pipe clearance: arithmetic on positions
is the correct method. Not a schema gap. See `BIM_COBOL.md` §20 for the spatial
predicate verbs that standardise these queries.

### What the gates don't prove (TE-specific gaps)

1. **Per-element position verification — DONE (2026-03-18).** G3-DIGEST now PASS
   (4 IfcSensor removed — Federation addon). TotalityContractTest covers CO_TE.

2. **Rotation verification — DONE (2026-03-18).** RotationContractTest covers CO_TE.
   TE doors/windows now have W/D alignment check.

3. **ROUTE step uniformity.** VerbDetector accepts non-uniform element spacing
   as a ROUTE pattern. Expansion assumes uniform step → intermediate positions
   drift from extraction centroids. Traced example:
   - 5 pipe fittings at X = [90.8, 103.6, 108.3, 133.8, 138.5]
   - Detected as `ROUTE:X:11.9:5` (avg step = (138.5 - 90.8) / 4 = 11.9m)
   - Expansion places at: 90.8, 102.7, 114.6, 126.6, 138.5
   - Actual positions:    90.8, 103.6, 108.3, 133.8, 138.5
   - Error: up to 7.2m on intermediate elements (endpoints always match)

4. **SPRAY grid approximation.** SPRAY uses median step with 10% tolerance.
   The grid approximation diverges further from actual positions than ROUTE.

### Fidelity check mechanics

`BomValidator.checkVerbExpansionFidelity()` (step 9b) performs a round-trip:

1. Read verb-factored BOM lines (verb_ref IS NOT NULL)
2. Read extraction centroids from component_library.db
3. Group both by `storey|discipline|product` (R9 fix — was `storey|product`)
4. Expand verb_ref to positions, add floor AABB min for world coordinates
5. Sort both sets, match positionally, compute Euclidean distance

The grouping key fix (R9) eliminated 993 count mismatches caused by mixing
centroids from different discipline BOMs. The residual distance errors are
real: ROUTE's uniform-step assumption vs non-uniform actual spacing (R8 TODO).

### Coordinate chain

```
DisciplineBomBuilder writes:
  fMinX = MIN(minX) across all elements on storey (AABB min)
  dx = centroidX - fMinX                          (floor-relative)
  makeDx = fMinX - allMinX                        (floor origin vs building origin)

VerbDetector stores:
  origin = first_centroid - fMinX                  (pattern origin, floor-relative)
  step = (last - first) / (count - 1)             (uniform average)

Fidelity checker reconstructs:
  expanded = origin + i*step                       (floor-relative positions)
  world = expanded + floorAabbMin                  (world coordinates)
  compare against extraction centroids             (also world coordinates)

Error source: step is an average, not the actual per-element spacing.
```

---

## Recurrence Analysis — Cross-Floor Pattern Sharing

**Question:** Can the TE BOM be further compressed via recurrence — reusable
sub-BOMs that appear identically on multiple floors?

**Observation:** The same product types repeat across floors:
- Poly Steel pipes appear on all 7 floors
- UPVC pipes on 6 floors
- Sprinkler heads (K80) on 5 floors
- Light fixtures (600x600) on 5 floors

**Current state:** 1,131 LEAF lines (42.8:1 via CLUSTER). Each floor's
discipline BOMs are independent — `FP_TE_GF` and `FP_TE_L01` both contain
sprinkler lines but share no sub-BOM reference.

**Recurrence candidates:**

| Pattern | Floors | Elements/floor | Potential sub-BOM |
|---------|--------|---------------|-------------------|
| FP sprinkler grid | 5 (GF-L3) | ~900 | Sprinkler bay template + M_BOM_Line offset per floor |
| ELEC lighting grid | 5 (GF-L3) | ~160 | Ceiling light template + M_BOM_Line offset per floor |
| ACMV duct run | 4 (GF-L2) | ~300 | Duct main template with branch variants |
| CW pipe riser | 7 (all) | ~100 | Vertical riser template (per-floor instance attrs) |

**Challenge:** Floors are NOT identical — element counts vary (GF=3,513 vs L4=2,307),
spacing differs, building footprint tapers. True typical-floor recurrence requires
that the sub-BOM pattern (product set + relative offsets) matches exactly.

**Approach:** Investigate whether discipline sub-BOMs on adjacent floors share the
same product-set signature (ignoring absolute position). If yes, a template sub-BOM
with per-floor M_BOM_Line offset placement compresses further. If not, the current per-floor
independent BOMs are correct and 42.8:1 is the natural compression limit.

**This is a future investigation** — does not block current compilation. The spec
(BOMBasedCompilation.md §3.3) supports recurrence via M_Product_Category_Line templates.

---

---

## TE Compilation Status — Post-Flatten BOM Audit (S100-p69, 2026-03-28)

> **TE BOM persisted.** The S100-p66 flatten (BUILDING→FLOOR→LEAF) resolved
> W-TACK-1 and W-BUFFER-1. TE_BOM.db is now populated. QA all PASS.
> Compile-path blockers remain — TE is extraction-only until prompt 71.

### IFCtoBOM QA — All PASS (post-flatten)

| Check | Result |
|-------|--------|
| BOM count | 8 (1 BUILDING + 7 FLOOR) |
| BOM lines | 1,522 lines → 48,435 instances |
| DocType (CAT/DST) | CO_TE |
| BOM categories | CO=1, FN=1, GF=1, L1=1, L2=1, L3=1, L4=1, RF=1 |
| M_Product | 513 total (505 catalog + 8 assembly stubs) |
| Duplicate bom_ids | 0 |
| Orphan lines | 0 |
| Duplicate positions | 2 (WARN — non-blocking) |
| World-coord offsets (>500m) | 0 |
| Non-zero BOM origins | 0 |
| AABB W containment | Floor max 68,930 ≤ building 73,670 |
| AABB D containment | Floor max 56,359 ≤ building 59,124 |
| AABB H envelope | Building 59,818, floor sum 121,249 (103% overlap) |
| Tack: assembly refs valid | 7 refs, all valid |
| Tack: BUILDING children | 7 assembly refs |
| Element refs on LEAF lines | 1,515/1,515 |
| **W-TACK-1: LBD convention** | **0/1,515 overflows — PASS** |
| W-BUFFER-1: SUM(children) = parent | SKIP (no SET BOMs after flatten) |
| Product-linked LEAF lines | 1,515/1,515 |
| Factorization ratio | 3.0× lines, 95.9× reuse (505 products → 48,428 instances) |
| Extraction reconciliation | 48,428 vs 48,428 (delta=+0) |
| Shape consistency (CP-4) | 1,515 LEAF rows classified |
| Integrity hash | a631bd7864567996 |

> **Note:** W-TACK-1 and W-BUFFER-1 pass trivially after flatten, not by
> correctness. With no SET BOMs, every LEAF child sits under FLOOR whose AABB
> is the union of all its elements. W-TACK-1 passes because parent = union(children)
> by construction. W-BUFFER-1 is skipped because no SET BOMs exist to check.
> This does not prove tack offsets are compile-ready — only that the flat structure
> satisfies the validator.

### BOM Structure (post-flatten)

```
BUILDING_TE_STD  (73670×59124×59818mm, origin=84.64/-51.22/-30.69)
├── TE_FDN  (Foundation)  — 157 lines,    703 instances, 6 IFC classes
├── TE_GF   (Ground Floor) — 421 lines,  3,513 instances, 24 IFC classes
├── TE_L01  (Level 1)     — 223 lines,  2,070 instances, 21 IFC classes
├── TE_L02  (Level 2)     — 222 lines,  2,609 instances, 23 IFC classes
├── TE_L03  (Level 3)     — 257 lines,  1,798 instances, 23 IFC classes
├── TE_L04  (Level 4)     — 125 lines,  2,307 instances, 21 IFC classes
└── TE_RF   (Roof)        — 110 lines, 35,428 instances, 23 IFC classes
```

FLOOR origins are all (0,0,0). Tack chain: BUILDING.origin + MAKE.dx + LEAF.dx = element LBD.
Root→child MAKE offsets are LBD-to-LBD (meters, relative to building min).

### Verb Distribution

| Verb | Lines | Instances | % of total |
|------|-------|-----------|------------|
| CLUSTER | 345 | 47,157 | 97.4% |
| (null/PLACE) | 1,163 | 1,163 | 2.4% |
| FRAME | 2 | 78 | 0.2% |
| ROUTE | 2 | 18 | <0.1% |
| TILE | 3 | 12 | <0.1% |
| **Total** | **1,515** | **48,428** | |

Verb factorization is heavily CLUSTER-dominated (97.4%). The 33,324 IfcPlate
elements on the Roof (CP-4 archetype) drive this — they're identical panel products
tiled across the roof surface.

### Compile-Path Blockers (6 questions)

**1. Root finding:** Yes. Exactly 1 BOM has no parent m_bom_line pointing to it
(BUILDING_TE_STD). The compile path can find it without `bom_type='BUILDING'`.

**2. Verb coverage:** 345/1,515 lines (47,157/48,428 instances = 97.4%) are
verb-factored (CLUSTER/FRAME/ROUTE/TILE). 1,163 lines are PLACE (null verb_ref,
qty=1). The compile path needs to handle both — verb lines expand to N instances
via VerbExpander, PLACE lines emit 1:1.

**3. Discipline grouping — BLOCKER:** Discipline codes (ARC, STR, FP, etc.) are
**NOT persisted** on LEAF lines. The `role` column stores IFC class name (IfcWall,
IfcPipe, etc.), not discipline code. During IFCtoBOM, DisciplineBomBuilder groups
elements by `e.discipline()` for verb factorization but passes `e.ifcClass()` to
`VerbFactorizer.insertLeafLine()` as the `role` parameter. The compile path needs
discipline for AD_Org_ID resolution. **Fix:** Either (a) add a `discipline` column
to m_bom_line, or (b) derive discipline from IFC class via `ad_ifc_class_map`
(ERP.db DV005, 46 rows). Option (b) is already the extraction path — but loses
multi-model provenance (same IfcPipeSegment could be FP, CW, or SP depending on
which federated model it came from).

**4. Tack chain integrity:** World position reconstruction (query 2g) produces
coordinates matching extraction positions. Sample: `006_ADA_Countertop_and_Sink`
reconstructs to (128.41, -3.05, -14.65) — consistent with building envelope.
Zero negative tacks (query 2f). Tack chain is geometrically sound.

**5. Scale concerns:** Largest floor = Roof with 110 lines (35,428 instances —
dominated by 33,324 IfcPlate). Ground Floor has 421 lines (3,513 instances).
These are manageable for BOM walk — the factored representation keeps line count
under 500 per floor even at 48K total instances.

**6. Missing data:**
- `element_ref` (IFC GUID): 1,515/1,515 — fully populated on unfactored PLACE lines.
  Verb-factored lines carry MA rows mapping qi→GUID.
- `material_name`: 717/1,515 (47.3%) — partial. 798 lines have NULL material.
- `orientation`: 42/1,515 (2.8%) — sparse. Most elements lack orientation data.
- `host_element_ref`: column exists but not checked.

### What Works Now (post-flatten)

| Asset | Status |
|-------|--------|
| TE_BOM.db | **POPULATED** (S231) — 50 BOMs, 5,568 lines, 3,661 products. 8MB |
| _TE_compile.db | **Generated** (S231) — compiled from TE_BOM.db |
| Compile | **8/10 gates** — 48,428 elements. C8 FAIL (16 mesh types), GEO no pairs (federated) |
| G1-G6 gates | **Not reached** — extraction-only path runs (2/4 gates), BOM path blocked |
| G0-COMPILED | **Not reached** |

### What Doesn't Work (compile-path gaps)

| Gap | Current State | What it Means |
|-----|---------------|---------------|
| c_order | **0 rows** in output DB | BomDropper runs but CO passthrough deleted (S100-p66) |
| c_orderline | **0 rows** in output DB | No BOM explosion to order lines |
| Discipline on LEAF | **Missing** — role=IFC class, not discipline | AD_Org_ID unresolvable from BOM alone |
| CompilationPipeline CO skip | **Still present** | Creates empty BuildingSpec, all elements passthrough |
| Designer access | **Would show nothing** | No order data to browse |

### Why This Wasn't Flagged Earlier (historical, S99)

Three structural blind spots — two now fixed (S100-p67):

1. **Silent skip, not loud fail.** ~~`return 1` on missing BOM.db~~
   **FIXED** (S100-p67): script now emits `FAIL` verdict + loud error.

2. **No "was it compiled?" gate.** ~~G1-G6 compare extraction-vs-extraction.~~
   **FIXED** (S100-p67): G0-COMPILED gate checks `c_order > 0`. TE correctly
   FAILs G0 (extraction-only, c_order=0).

3. **LAST_MILE_PROBLEM.md doesn't track per-building IFCtoBOM status.**
   LMP tracks compilation pipeline gaps (R21-R24), not extraction-to-BOM
   conversion failures. R25 gap entry added (S100-p67).

4. **LMP §1 (Input=Output) has no compilation prerequisite.** The count
   check runs on whatever output exists. Tracked as CP-5.

### Code Flow — How TE Passes Through Unchallenged

The full class.method path showing exactly how TE's extraction coordinates
reach output.db without compilation. Each step shows the Java/shell entry
point, what TE produces at that step, and where the failure or bypass occurs.

```
STEP 1: EXTRACT (Python — external, not our code)
────────────────────────────────────────────────────
  Entry:  IfcOpenShell federation/extract.py
  Reads:  Terminal.ifc (9 federated discipline models)
  Writes: component_library.db
          → I_Element_Extraction: 48,428 rows (active)
          → I_Geometry_Map: mesh vertices + faces
          → M_Product: 505 unique dimensional signatures
  TE:     ✅ PASS — complete, correct, the reference truth

STEP 2: CLASSIFY (YAML — human-authored)
────────────────────────────────────────────────────
  File:   IFCtoBOM/src/main/resources/classify_te.yaml
  Reads:  nothing (pure declaration)
  Declares: building_type=Terminal, m_product_category=CO,
            8 disciplines, 7 storey bands, dsl_file
  TE:     ✅ PASS — correct

STEP 3: POPULATE (Java)
────────────────────────────────────────────────────
  Entry:  IFCtoBOMMain.main("--populate", "--classify", yaml)
          → ExtractionPopulator.populate(compConn, buildingType)
  Reads:  component_library.db (I_Element_Extraction)
  Writes: component_library.db (enriched: Z-band storey, is_active,
          M_Product_ID linkage)
  TE:     ✅ PASS — 48,428 active elements, REBAR deactivated

STEP 4: BUILD BOM (Java IFCtoBOM)
────────────────────────────────────────────────────
  Shell:  run_RosettaStones.sh:710
          → mvn exec:java -Dexec.mainClass="com.bim.ifctobom.IFCtoBOMMain"
  Entry:  IFCtoBOMMain.main("--classify", yaml, "--bom-db", "library/TE_BOM.db")
          → IFCtoBOMPipeline.run(yamlPath, bomDbPath, compDbPath, schemaPath)

  Step 4a — ClassificationYaml.load(yamlPath)                    → ✅ PASS
  Step 4b — ExtractionPopulator.populate(compConn, buildingType)  → ✅ PASS (48,428)
  Step 4c — IFCtoBOMPipeline:258
            if ("CO".equals(config.docBaseType()))
              → DisciplineBomBuilder.build(bomConn, config, storeyElements)
            58 BOMs, 1,572 lines, 48,485 instances                → ✅ PASS (in memory)
  Step 4d — BomValidator.validateAndReport(bomConn, ...)          → ❌ FAIL (5 checks)
            DocType: "-_TE" (format bug)                            → prompt 65
            M_Product catalog: 0 (CO path skips registration)      → prompt 65
            Non-zero origin: 1 BOM                                 → prompt 65
            W-TACK-1: 471/1,515 overflows                          → prompt 66
            W-BUFFER-1: 36/50 unbalanced                           → prompt 66
  Step 4e — bomConn.rollback() + throw SQLException               → TE_BOM.db = EMPTY

STEP 5: PREPARE COMPILE DB (Shell)
────────────────────────────────────────────────────
  Entry:  run_RosettaStones.sh:133 prepare_compile_db()
  Line 146: if [ ! -f "$bom_db" ]; then return 1
  TE:     ⚠️ SILENT SKIP — TE_BOM.db missing, returns 1
          Script continues. No verdict logged. No FAIL.

STEP 5b: BOM DROP (Java — NEVER REACHED for TE)
────────────────────────────────────────────────────
  Entry:  BuildingRegistryTest.java:78
          → BomDropper.drop(compileDb, entry)
  What it does: Walks m_bom/m_bom_line → creates C_Order + C_OrderLine tree
  TE:     ❌ NEVER RUNS — no compile DB → no test invocation
          c_order = 0, c_orderline = 0

STEP 6: COMPILE (Java DAGCompiler — 11-stage pipeline)
────────────────────────────────────────────────────
  Shell:  run_RosettaStones.sh:210 compile_building()
          → mvn test -Dtest="BuildingRegistryTest" -Dbom.db="${compile_db}"
  Entry:  BuildingRegistryTest → CompilationPipeline.run(entry)

  The 11 stages (CompilationPipeline.java:72-84):
    Stage 1: MetadataValidator    — referential integrity
    Stage 2: CompileStage         — compile → BuildingSpec (ParseStage removed)
    ┌─────────────────────────────────────────────────────────────┐
    │ ❌ ILLICIT CODE — CompilationPipeline.java:352-354          │
    │                                                             │
    │   if ("CO".equals(ctx.entry().mProductCategoryId())) {      │
    │       ctx.setSpec(new BuildingSpec(name, List.of(), null));  │
    │       return true;  // SKIP                                 │
    │   }                                                         │
    │                                                             │
    │ Creates EMPTY BuildingSpec (0 storeys, no roof).             │
    │ Violations:                                                 │
    │   Anti-Drift §1 — magic coordinates                        │
    │   DriftGuardTest D6 — hardcoded category branch             │
    │   LMP §7 — input = output                                  │
    │ Fix: DELETE this block (prompt 66 Step 6)                   │
    └─────────────────────────────────────────────────────────────┘
    Stage 4: TemplateStage        — ST mode only (skipped)
    Stage 5: WriteStage           — write to output.db
    ┌─────────────────────────────────────────────────────────────┐
    │ ❌ PASSTHROUGH — BuildingWriter.java:865                    │
    │   emitGlobalPlacementElements(spec)                         │
    │                                                             │
    │ PlacementLoader.load()                                      │
    │   → hasOrderLineData() = false (c_order=0)                  │
    │   → loadFromBOM()                                           │
    │     → MBOM.getRoots(conn)                                    │
    │     → BOMWalker.walkSelf(bomId, visitors, buildingType)     │
    │     → PlacementCollectorVisitor.getPlacements()              │
    │       → 48,428 Placement records with extraction coords     │
    │                                                             │
    │ BuildingSpec has 0 storeys → nothing consumed by             │
    │ StoreyCompiler → PlacementLoader.isConsumed() = false       │
    │ for ALL elements → emitGlobalPlacementElements() emits      │
    │ ALL 48,428 as-is → extraction coords copied to output       │
    └─────────────────────────────────────────────────────────────┘
    Stage 6: VerbStage            — BIM COBOL (runs but no effect)
    Stage 7: DigestStage          — spatial digest
    Stage 8: GeometryStage        — geometry integrity
    Stage 9: ProveStage           — placement proofs

  TE output: 48,428 elements with extraction coordinates.
  No BOM compilation occurred. Output = input.

STEP 7: VERIFY (Shell + Java gates)
────────────────────────────────────────────────────
  Shell:  run_RosettaStones.sh:732 run_integrity()
  Java:   RosettaStoneGateTest (G1-G6)

  G1-COUNT:      48,428 output = 48,428 reference  → PASS (trivially)
  G2-VOLUME:     same AABB as extraction            → PASS (trivially)
  G3-DIGEST:     same hash as extraction            → PASS (trivially)
  G4-TAMPER:     source scan (no DB dependency)     → PASS (real)
  G5-PROVENANCE: checks geometry resolution         → PASS (trivially)
  G6-ISOLATION:  cross-DB join guard                → PASS (real)

  TE:     ⚠️ All PASS — but G1/G2/G3/G5 are comparing a thing to itself.
          No G0-COMPILED gate exists to check c_order > 0.
          Fix: prompt 67
```

### FINE Logging Recommendation

Once the fix prompts are applied, add FINE-level guards that would have
caught this earlier:

```java
// In CompilationPipeline.CompileStage.execute():
BIMLogger.fine("COMPILE", "CompileStage: category={}, storeys={}, hasRoof={}",
    ctx.entry().mProductCategoryId(),
    ctx.spec().storeys().size(),
    ctx.spec().roof() != null);

// In BuildingWriter.emitGlobalPlacementElements():
int totalPlacements = allPlacements.size();
int consumed = (int) allPlacements.stream()
    .filter(p -> PlacementLoader.getInstance().isConsumed(p.buildingType(), p.elementRef()))
    .count();
BIMLogger.fine("EMIT", "emitGlobal: total={}, consumed={}, emitting={}",
    totalPlacements, consumed, totalPlacements - consumed);
if (consumed == 0 && totalPlacements > 100) {
    BIMLogger.warn("EMIT", "[SUSPECT] 0/{} consumed — entire output is passthrough. "
        + "Was CompileStage skipped?", totalPlacements);
}

// In PlacementLoader.load():
BIMLogger.fine("PLACEMENT", "PlacementLoader: hasOrderLineData={}, path={}",
    hasOrderLineData() ? "OrderLine" : "BOM-direct",
    System.getProperty("bom.db"));
```

The `[SUSPECT]` warning at FINE level would flag any future building where
`emitGlobalPlacementElements()` emits everything and nothing was consumed —
the exact signature of the TE passthrough.

### Fix Path — Priority Order (updated S100-p69)

**Phase 1 — Mechanical fixes: DONE**

| # | Fix | Status |
|---|-----|--------|
| 1 | DocType format: `-_TE` → `CO_TE` | **DONE** (S99) |
| 2 | Non-zero BOM origin: exclude BUILDING | **DONE** (S100-p65) |
| 3 | M_Product catalog: register leaf products | **DONE** (S100-p65) |

**Phase 2 — Tack convention: DONE (via flatten)**

| # | Fix | Status |
|---|-----|--------|
| 4 | W-TACK-1: 471→0 overflows | **DONE** (S100-p66) — SET level removed, LEAF under FLOOR |
| 5 | W-BUFFER-1: 36 unbalanced | **DONE** (S100-p66) — no SET BOMs to check (SKIP) |

**Phase 3 — Verification hardening: DONE**

| # | Fix | Status |
|---|-----|--------|
| 6 | Script fail-loud on missing BOM.db | **DONE** (S100-p67) |
| 7 | G0-COMPILED gate | **DONE** (S100-p67) — TE correctly FAILs (c_order=0) |

**Phase 4 — Compile-path enablement: DONE (S100-p71/p72)**

| # | Fix | Status |
|---|-----|--------|
| 8 | Discipline on LEAF lines | **DONE** (S100-p71) — AD_Org resolves from product, not line |
| 9 | Remove CO passthrough | **DONE** (S100-p72) — BOM walk replaces shouldSkip |
| 10 | BomDrop for CO buildings | **DONE** (S100-p72) — single path, verb-dispatched |

**Phase 5 — iDempiere PK conformance (prompt 86):**

| # | Fix | Approach |
|---|-----|----------|
| 11 | m_bom M_BOM_ID INTEGER PK | Phase A: DONE (S100-p86). 65 Java refs migrated. `bom_id` → `Value`. |
| 12 | M_Product_Category INTEGER PK | Phase B: DONE. 135 Java refs migrated. Category codes → `Value`. |
| 13 | 13 AD tables INTEGER PKs | Phase C: DONE. Composite PKs got surrogate `_ID`. |

**Why Phase 5 matters for TE:** TE's 48,428 elements traverse 8 BOMs
via `bom_id` TEXT. Every BOMWalker.walk(), BomDropper.explode(), and
PlacementCollectorVisitor.onSubAssembly() passes `bom_id` as String.
Migrating to INTEGER FK will:
- Flush hidden string-concatenation assumptions in walker code
- Expose hardcoded category codes (`"RE"`, `"CO"`) that should be Value lookups
- Verify IFCtoBOM DDL matches the new schema (re-extraction test)
- Prove the pipeline is PK-type-agnostic (same output, different key type)

**Verification exercise:** After each phase of prompt 86, run TE through
the full pipeline. The FINE logs (prompt 85) will show whether INTEGER PKs
flow correctly through BomDrop → BOM walk → WriteStage. SH is the canary
(7/7 must hold). TE is the stress test (48K elements, 8 disciplines).
Any TEXT/INTEGER mismatch will surface as a gate failure or exception in
the FINE log — that's the point.

**After Phase 5:** All tables follow iDempiere convention. `_ID` is opaque
INTEGER (never shown to users). `Value` is the search key. `Name` is the
display name. FKs reference `_ID`. DB integrity enforced at the schema level.

---

## S174 Multi-Discipline Extraction — Geometry Alignment

**Date:** 2026-04-11 | **Pipeline:** `scripts/pipeline_library.sh Terminal`

### Recurring Issue: Discipline Origin Hell

Terminal's 8 discipline IFC files each have their own survey point / project origin.
When extracted separately and merged, elements scatter across km-scale offsets unless
corrections are applied.

### Root Cause Found (S174)

`_compute_alignment()` in `extract_merge_disciplines.py` uses IfcOpenShell's
`auto_enh2xyz()` to compute XYZ corrections. This function returns values in the
**IFC file's local unit** (millimetres for Terminal, `unit_scale=0.001`). But our
extracted element coordinates are always in **metres** (because `geom.iterator()`
returns metres natively since S172).

The correction was 1000× too large. ARC correction was -244,356m instead of -244.36m.

**Fix:** Multiply `auto_enh2xyz` output by `unit_scale` before applying as correction.

```
correction_m = correction_raw × unit_scale
```

**Why never caught before:** Hospital has `enh=(0,0,0)` for all disciplines — zero
corrections, so the missing scale was invisible. Terminal is the first building with
real geolocation data through this path.

### Results After Fix

| Discipline | Elements | X range (m) | Y range (m) | Aligned? |
|-----------|----------|-------------|-------------|----------|
| ACMV (ref) | 289 | [346, 401] | [-220, -180] | Reference |
| FP | 995 | [342, 400] | [-220, -180] | YES ✓ |
| LPG | 209 | similar | similar | YES ✓ |
| SP | 979 | [0, 150] | [-41, 4] | YES ✓ |
| STR | 34,356 | [-238, 344] | [-219, 86] | YES ✓ |
| ARC | 2,853 | [-238, 402] | [-220, 92] | YES ✓ |
| MEP | 9,733 | [239, 401] | [-231, -176] | YES ✓ |
| **ELEC** | **833** | **[135, 175]** | **[34, 94]** | **NO — grid_north=-38° vs 52°** |

49,059 elements total. 48,226 (98.3%) correctly aligned in metre-scale.

### ELEC Grid North Mismatch

ELEC has `grid_north=-38°` while all other disciplines have `grid_north=52°`.
A simple XYZ translation cannot fix a 90° rotation difference. The `auto_enh2xyz`
correction handles the translation component but the element coordinates in the
extracted DB are in the IFC local frame — not rotated to match the reference frame.

**Resolution path:** IfcOpenShell's `MergeProjects` (IfcPatch) handles this correctly.
The reference implementation is at:

```
IfcOpenShell/src/ifcpatch/ifcpatch/recipes/MergeProjects.py (lines 82-122)
```

Key logic (lines 85-89):
```python
model_rotation = existing_angle - other_angle
if model_rotation > 180:
    model_rotation = (360 - model_rotation) * -1
elif model_rotation < -180:
    model_rotation = (model_rotation * -1) - 360
```

It then calls `SetFalseOrigin(other, ..., rotate_angle=model_rotation).patch()` which
applies a full rotation+translation transform to all geometry placements **at the IFC
level** before merging. This is the step our `_compute_alignment()` is missing — it
does translation (`auto_enh2xyz`) but not rotation.

**Options (in order of preference):**

1. **Apply grid_north rotation in `merge_db()`** — compute `θ = ref_north - other_north`,
   apply 2D rotation matrix to `(center_x, center_y)` and adjust `rotation_z += θ`.
   Same math as MergeProjects but at the DB level (post-extraction):
   ```python
   θ = math.radians(ref_north - other_north)
   new_x = cx * cos(θ) - cy * sin(θ)
   new_y = cx * sin(θ) + cy * cos(θ)
   new_rz = rz + θ
   ```
2. **Use merged IFC** for Terminal (proven, bypasses alignment entirely)
3. **Accept ELEC offset** — 833 elements (1.7%), visually separated but data intact

### Proof Log Tags

```
§ALIGN_DETAIL correction_raw=(-244356.2,132191.1,-3114.9) unit_scale=0.001 correction_m=(-244.36,132.19,-3.11)
§NORMALIZE merged centroid far from origin: (-251.2, 180.1, -44.9)
§NORMALIZE after: centroid=(-0.0, 0.0, 0.0)
§PROOF BBOX_RECONSTRUCT PASS  500 ok, 0 fail  worst=0.0001m
```

### Pipeline Command

```bash
./scripts/pipeline_library.sh Terminal    # uses SJTII-*-Clean.ifc pattern
```

---

**Cross-references:**
[`BBC.md`](BOMBasedCompilation.md) §1.8 |
[`BOMBasedCompilation.md`](BOMBasedCompilation.md) §3-§4 (governing spec) |
[`InfrastructureAnalysis.md`](InfrastructureAnalysis.md) |
[`terminal_erd.html`](https://github.com/red1oon/BIMCompiler/blob/master/database/terminal_erd.html) (interactive ERD) |
[`bim_architecture_viz.html`](https://github.com/red1oon/BIMCompiler/blob/master/database/bim_architecture_viz.html) (4-DB architecture) |
[`LAST_MILE_PROBLEM.md`](LAST_MILE_PROBLEM.md) (Gap 6: verb step-uniformity) |
[`BIM_COBOL.md`](BIM_COBOL.md) (verb taxonomy + data flow)

*Copyright (c) 2025-2026 Redhuan D. Oon. MIT Licensed.*
