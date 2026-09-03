# DONE
# S142 — DX + SH Output Quality: TACK Fix + CLUSTER Resolution

**Spec:** `internal/TACK_FIX_SPEC.md`
**Prior work:** `prompts/done/128_dx_te_ifc_rebuild.md`, `prompts/done/124_cluster_reprocessor.md`
**Prereq:** S141 DONE (abstract product catalog mapping — GAP-A closed)

You are a coder for bim-compiler. One bounded task.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** Query the database. Copy patterns you find. Compute positions via verbs. Never invent.

## Problem

DX (1119 el, unk=915) and SH (58 el, unk=26) pass 8/8 gates but output is
trivially correct — CLUSTER verb copies world coordinates en bloc into BOM.db.
This round-trips perfectly (zero drift) but is not *compilation*: the BOM
lines carry centroid-relative offsets instead of LBD-to-LBD per BBC.md §4.
The output works only because EN-BLOC skips tack evaluation.

**Three code paths compute dx/dy/dz wrong (TACK_FIX_SPEC.md §1.2):**

| File | Line | Current (WRONG) | Correct (LBD) |
|------|------|-----------------|----------------|
| `ScopeBomBuilder.java` | 137-139 | `e.centroidX() - ox` | `e.minX() - setMinX` |
| `FloorRoomBomBuilder.java` | 105, 130 | `0.0, 0.0, 0.0` (hardcoded) | Measured room/floor LBD offset |
| `VerbDetector.java` | 385-395 | `e.centroidX() - gMinCentroidX` | `e.minX() - gMinX` |

**Status:** FIX-1 and FIX-3 confirmed in code. FIX-2 partial. Need to verify
all three are actually active and producing correct LBD offsets in output.

## What "unk=915" means for DX

915 elements unassigned to storey — not in IFC spatial containment.
Root cause: IFC2x3 `rel_contained_in_space` missing for most MEP elements.
DuplexAnalysis.md §5 documents the mirror partition (485 paired + 129 shared).

## Read first

1. `internal/TACK_FIX_SPEC.md` — full spec, three FIX definitions
2. `docs/DuplexAnalysis.md` — mirror algorithm, 3-tier partition
3. `PROGRESS.md` §Current State
4. `prompts/done/128_dx_te_ifc_rebuild.md` — prior DX rebuild (gate status, delta)
5. `prompts/done/124_cluster_reprocessor.md` — CLUSTER diagnostic

## Original Tasks (1-4) — ALL RESOLVED

See Session Findings below. Tasks 1-2 were already done in prior sessions.
Tasks 3-4 verified — gates pass but output quality is unchanged because
the real problem is CLUSTER dominance, not offset arithmetic.

## Remaining Tasks — Pattern Classification + Product Naming

### Task A: Create `ad_verb_pattern` table (pattern grouping list)

Instead of VerbDetector's blind cascade (TILE→ROUTE→FRAME→CLUSTER), add a
classification table that routes product groups to the correct detector.

**Table: `ad_verb_pattern`** (in ERP.db, migration DV035)

```sql
CREATE TABLE ad_verb_pattern (
    pattern_id     INTEGER PRIMARY KEY AUTOINCREMENT,
    product_type   TEXT NOT NULL,       -- from M_Product.product_type (FURNITURE, WINDOW, etc.)
    ifc_class      TEXT,                -- optional: IfcFurnishingElement, IfcWindow, etc.
    expected_verb  TEXT NOT NULL,       -- LINE, TILE, ROUTE, FRAME, CLUSTER
    group_by       TEXT DEFAULT 'axis', -- axis (align check), spatial (proximity), none
    priority       INTEGER DEFAULT 10,
    is_active      INTEGER DEFAULT 1,
    notes          TEXT
);
```

**Seed from CLUSTER diagnostics (mined from [PATRN] VERB log):**

| product_type | ifc_class | expected_verb | group_by | evidence |
|---|---|---|---|---|
| FURNITURE | IfcFurnishingElement | LINE | wall_axis | SH/DX cabinets: Y=1 unique, X varies |
| WINDOW | IfcWindow | LINE | facade_axis | SH windows: irregular X on facade |
| CURTAIN_WALL | IfcCurtainWall | STACK | Z | SH curtain: X=2 Y=1, Z spread |
| FITTING | IfcFlowFitting | CLUSTER | none | DX bends/elbows: 3D scatter, Z>3m |
| PIPE | IfcFlowSegment | CLUSTER | none | DX pipes: handled by RouteWalker in ERP path |

**Flow:** VerbDetector.detect() reads `ad_verb_pattern` at pipeline start
(cached like ProductResolver). Per product group, looks up expected_verb.
If match found → try that detector first. If no match → existing cascade.
CLUSTER fallback always available as safety net.

**Feedback loop:** `[PATRN] VERB CLUSTER fallback:` log entries identify new
candidates for `ad_verb_pattern` rows. Mine the log, add rows, re-run.

### Task B: Add LINE verb to VerbDetector

**Pattern:** Elements arrayed along a single axis with non-uniform spacing.
Two of three axes are constant (within tolerance). Positions enumerated.

**Format:** `LINE:axis:pos1,pos2,pos3,...` (positions in metres, LBD-relative)

**Detection:** For a same-product group:
1. Check if Y and Z are constant (within 5mm) → LINE along X
2. Check if X and Z are constant → LINE along Y
3. Check if X and Y are constant → LINE along Z (STACK)
4. If none → fall through to existing cascade

**Captures:** Kitchen cabinets, facade windows, curtain wall panels, stacked items.

**Insert in cascade:** After TILE, before ROUTE (LINE is simpler than ROUTE).

### Task C: S141 incomplete — M_Product.Name population

S141 created ProductResolver and source_element_ref but never populated
M_Product.Name with the human-readable IFC family name. **Fixed in this session:**

- `ProductRegistrar.ensureProducts()`: Name now populated from source_element_ref
  via `deriveName()` (strips dimension suffix, keeps IFC family name)
- `VerbDetector.detect()`: CLUSTER diagnostics now on `[PATRN]` channel
  (was `[FINE] VERB` — required changing global log level to see)
- `IFCtoBOMPipeline.java`: note about bim.log.level=FINE for additional diagnostics

**Verified:** SH 8/8, DX 8/8. Products now read: `Furniture_Piano`,
`M_Base Cabinet-Double Door & 2 Drawer`, `M_Bed-Standard`, `Chair - Dining`.

**BOM DB is for ARC/STR spatial arrangement only.** ERP.db is for DISC
(MEP discipline). See DISC_VALIDATION_DB_SRS §10.4.4.

## Gate

- SH 8/8, DX 8/8 (no regression)
- `ad_verb_pattern` table seeded with mined rows from CLUSTER diagnostics
- LINE verb detects 1D array patterns (cabinets, windows)
- CLUSTER count decreases after LINE verb + pattern classification
- M_Product.Name carries IFC family name (not abstract product_id)

---

# SESSION FINDINGS (2026-04-04)

## F1: All three TACK-FIX code paths are already LBD

| FIX | Status | Evidence |
|-----|--------|----------|
| FIX-1 (ScopeBomBuilder) | DONE | Delegates to VerbFactorizer which uses `e.minX() - parentMinX` throughout (lines 157-163 factored, 194-196 unfactored) |
| FIX-2 (FloorRoomBomBuilder) | DONE — class deleted | Replaced by BomHierarchyBuilder (commit `f0b6c900`). Uses `childLbd[0] - storeyLbd[0]` (line 80) and `storeyLbd[0] - bldgMinX` (line 94) |
| FIX-3 (VerbDetector.detectCluster) | DONE | Uses `ExtractionElement::minX` for group origin (line 454) and `e.minX() - gMinX` for offsets (line 463) |

**TACK_FIX_SPEC.md is stale** — describes the pre-fix state. All three violations are resolved.

**Exception:** `detectSpray()` (lines 397-431) still uses `centroidX/centroidY` for grid matching and origin computation. Not in the spec, but inconsistent with the LBD convention used by TILE/FRAME/ROUTE/CLUSTER.

## F2: The real problem is CLUSTER dominance, not offset convention

**CLUSTER is the LMP violation.** It stores exact per-instance world offsets and replays them verbatim — copy-paste, not compilation. The TACK-FIX changed offset arithmetic but CLUSTER is lossless either way, so the output is functionally unchanged.

### SH verb distribution (58 elements, 37 products)
```
BOM verbs: 42 PLACE, 2 CLUSTER (8 instances), 1 TILE (4), 1 ROUTE (4)
Unfactored: 52 lines (qty=1 each) — most groups < MIN_GROUP(4)
```
SH is "too perfect" because it's mostly unfactored qty=1 lines. There's almost nothing to compile — it's a flat copy of extraction data with LBD offsets.

### DX verb distribution (1119 elements, 775 lines)
```
BOM verbs: 1075 PLACE, 8 CLUSTER (44 instances), 0 TILE, 0 ROUTE, 0 FRAME, 0 SPRAY
Unfactored: 767 lines — ALL factored groups are CLUSTER
```
DX has ZERO formula verbs. Every factored group fell through to CLUSTER.

### DX unchanged from prior session
`unk=915` still 915 (IFC2x3 missing `rel_contained_in_space` for MEP — not solvable by TACK-FIX). CHECK PLACEMENT still 91 violations. TACK-FIX did not change DX output quality.

## F3: CLUSTER fallback diagnostics now on [PATRN] channel

**Fixed:** VerbDetector CLUSTER diagnostics moved from `BIMLogger.fine("VERB",...)` to `BIMLogger.pattern("VERB",...)`. Now visible at default log level with `bim.pattern.debug=true` (default ON).

### SH CLUSTER fallbacks (2 groups)
```
Windows_Sgl_Plain:1810x1210mm qty=4
  tile=[X spacing non-uniform (4 positions)]
  frame=[incomplete grid 4/8]
  route=[chain failure (no axis-aligned legs)]

Curtain Wall:Curtain_Wall-Exterior_Glazing qty=4
  tile=[need >=2 unique positions per axis (X=2 Y=1)]
  frame=[need >=2 gridlines per axis (X=2 Y=1)]
  route=[Z spread 2.721m (>0.5m)]
```
**Windows:** 4 on a facade, irregular X spacing. LINE along facade axis would capture.
**Curtain wall:** 4 panels, X=2 Y=1, stacked in Z. LINE/STACK along Z would capture.

### DX CLUSTER fallbacks (8 groups)
```
Base Cabinet qty=8 (x2 rooms, A103+B103)
  tile=[X spacing non-uniform (7 positions)]
  route=[chain failure (no axis-aligned legs)]

Upper Cabinet qty=4 (x2 rooms)
  tile=[need >=2 unique positions per axis (X=4 Y=1)]
  route=[chain failure (no axis-aligned legs)]

Bend PVC qty=4, Elbow Generic qty=7
  tile=[spacing non-uniform]
  route=[Z spread >3m]

Pipe Mechanical qty=5, qty=4
  tile=[spacing non-uniform]
  route=[chain failure (no axis-aligned legs)]
```
**Furniture:** Kitchen cabinets along walls. Y=1 unique position = 1D array. LINE verb.
**MEP:** 3D scatter across floors. Genuinely irregular — CLUSTER acceptable, or defer to RouteWalker (IFCtoERP path). MEP is DISC, not ARC/STR BOM.

## F4: S141 incomplete — M_Product.Name was not populated

S141 created ProductResolver + source_element_ref bridge but `Value = Name = product_id`
everywhere. **Fixed:** `ProductRegistrar.deriveName()` strips dimension suffix from
source_element_ref to get IFC family name. BOM DB now reads:

| product_id | Name |
|---|---|
| PIANO_1372x600x1170 | Furniture_Piano |
| CHAIR_443x427x1227 | Chair - Dining |
| COUCH_2287x977x958 | Furniture_Couch_Viper |
| FURNITURE_1000x625x860 | M_Base Cabinet-Double Door & 2 Drawer |
| DOOR_EXT_1860x2110 | Doors_ExtDbl_Flush |

## F5: DX composition YAML is correct
`classify_dx.yaml` has `composition: type: MIRRORED_PAIR` with mirror axis/position/rotation. No `.bim` or DSL. Clean.

## F6: MEP exclusion from spatial BOM (the real fix)

904 MEP elements were in the DX spatial BOM (77% of all lines). They belong to the DISC
path (IFCtoERP → RouteWalker → shim alignment → ERP.db). Fixed:

- `StructuralBomBuilder.java`: reads `config.disciplines().get("MEP")` IFC classes, skips them
- `CompositionBomBuilder.java`: same MEP filter in mirror partition loop
- `IFCtoBOMPipeline.java`: reconciliation count subtracts MEP elements

**DX Before vs After:**
| Metric | Before | After |
|--------|--------|-------|
| BOM lines | 775 | 165 |
| MEP in BOM | 626 (77%) | 0 |
| CLUSTER | 8 | **0** |
| LINE/LINE_MULTI | 0 | 4 (24 instances) |

White-box PATTERN log confirms chain:
```
[PATRN] COMPOSITION  904 MEP elements excluded → DISC path (IFCtoERP)
[PATRN] STR          Storey 'Unknown': 904 MEP elements excluded → DISC path
[PATRN] VERB         LINE_MULTI: Base Cabinet qty=8 → 2 sub-groups
[PATRN] VERB         LINE: Upper Cabinet qty=4 → axis=X
```

**Chain of events (correctly wired):**
1. IFCtoBOM → `_BOM.db` ARC/STR only (spatial arrangement, formula verbs)
2. IFCtoERP → `ERP.db` DISC MEP (RouteWalker, shim alignment, joint pieces)
3. Compilation → reads both, produces output.db

## Code changes this session

1. `VerbDetector.java`: LINE verb + LINE_MULTI spatial sub-grouping + CLUSTER→PATTERN log + ad_verb_pattern hint loading
2. `ProductRegistrar.java`: `deriveName()` + Name column in BOM DB INSERT (S141 gap)
3. `StructuralBomBuilder.java`: MEP IFC class exclusion from structural BOM
4. `CompositionBomBuilder.java`: MEP IFC class exclusion from mirror partition
5. `IFCtoBOMPipeline.java`: reconciliation count excludes MEP + VerbDetector.loadPatternHints() wiring
6. `migration/DV035_verb_pattern.sql`: `ad_verb_pattern` table + 9 seed rows
7. `BIM.properties`: unchanged (log level stays INFO, pattern stays ON)

## F7: DISC identity chain is NOT wired for DX MEP (discovered late in session)

`library/ERP.db` M_Product has NO `M_Product_Category_ID` column. The spec says
`M_Product → M_Product_Category → AD_Org_ID` (DISC_VALIDATION_DB_SRS §6.4) but
this chain doesn't exist yet for DX MEP products.

What exists:
- `_import_joint_piece_types`: 899 rows for DX, all `discipline=CW`
- `AD_Org` table has discipline orgs (FP, ELEC, ACMV, CW, SP, LPG)
- `M_Product_Category` table exists but no FK from M_Product

What's missing:
- `M_Product.M_Product_Category_ID` column in `library/ERP.db`
- Category assignment for MEP products (ELBOW → Plumbing Fitting, PIPE → Pipe Segment)
- 5 IfcFlowController products (smoke detectors, backflow valves) not in `_import_joint_piece_types`
- DX only has CW discipline — no FP/ELEC/SP split (residential duplex is mostly plumbing)

ERP.db is minimalist abstract with shim BOMs. RouteWalker resolves the rest,
picking and assembling minute pieces. Previously solved for RM and TE — now
must prove on DX at small scale first.

Note: root `ERP.db` (16KB) is NOT the real one. `library/ERP.db` (39MB) is the
full catalog with M_Product, M_BOM, AD_Org, _import_joint_piece_types, etc.

---

# NEXT SESSION

**Wire `M_Product_Category_ID` on ERP.db `M_Product` for MEP products so the
`M_Product → M_Product_Category → AD_Org_ID` discipline chain resolves per
DISC_VALIDATION_DB_SRS §6.4; close the 5 IfcFlowController gap in
`_import_joint_piece_types`; prove on DX (small scale) before RM/TE.**

## Read first (next session)

1. `docs/DISC_VALIDATION_DB_SRS.md` §6.4 (M_Product → Category → AD_Org chain)
2. `docs/DISC_VALIDATION_DB_SRS.md` §6.12.2 (shim + joint piece architecture)
3. `prompts/done/00c_ifctoerp_joint_extraction.txt` (shim concept)
4. `prompts/done/00r_disc_bom_wire.txt` (RouteWalker wiring)
5. `PROGRESS.md` §S142 entry
6. This prompt — all findings above

## Key files (next session)

- `library/ERP.db` — the real catalog (NOT root `ERP.db`)
- `IFCtoBOM/src/main/java/com/bim/ifctobom/ProductRegistrar.java` — writes M_Product
- `DAGCompiler/.../library/FireSuppressionPlacer.java` — pattern for DISC placers
- `DAGCompiler/.../dsl/RouteExecutor.java` — dispatch point for discipline walks
- `IFCtoBOM/src/main/resources/classify_dx.yaml` — `discipline_counts: MEP: 904`

## Reconciliation target

904 MEP elements in extraction. IFCtoERP produces 162 runs, 621 recipe lines,
899 CW joint pieces. 5 IfcFlowController unaccounted. After wiring:
- Every MEP M_Product has M_Product_Category_ID → AD_Org_ID
- `discipline_counts: MEP: 904` reconciles against IFCtoERP output
- DX 8/8 gates unchanged
