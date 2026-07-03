# Hospital Analysis — Large-Scale Healthcare Rosetta Stone
> **Foundation:** [BBC](BOMBasedCompilation.md) · [DATA_MODEL](DATA_MODEL.md) · [TestArchitecture](TestArchitecture.md) · [EYES_SRS](EYES_SRS.md)

<div class="bim-banner" markdown>
<b>62,291 elements across 7 discipline IFCs — largest building in the fleet by element count and footprint.</b>
Multi-wing hospital with helipad: 102m × 164m × 45m (7 occupied levels + mechanical).
MEP fully recovered (2,187 → 41,126) via GUID-prefixed per-discipline extraction (S136).
</div>

## Building Identity

| Property | Value |
|----------|-------|
| Source IFCs | 7 discipline files (IFC4): ARC, STR, MECH, PLB, ELE, SPR, FIRE |
| Footprint | 102m × 164m (16,748 m²) |
| Height | 45.3m over 7 occupied levels + mechanical |
| Levels | L1–L7 + L7A (Ceiling and TOS variants per level) |
| Elements | 62,291 (post GUID-dedup, per-discipline extraction) |
| Assemblies | 9,527 (rel_aggregates) |
| Door/Window placements | 506 (rel_fills_host) |
| Reference DB | `DAGCompiler/lib/input/Hospital_extracted.db` (127 MB, LFS) |
| M_Product_Category | CO (Commercial / Institutional) |
| Helipad | Yes (visible in ARC geometry — rooftop) |

## Element Inventory by Source Discipline

| Source IFC | GUID Prefix | Elements | Dominant Classes |
|-----------|-------------|----------|-----------------|
| ARC | `ARC_` | 14,379 | Member(7,122) Plate(2,211) Proxy(1,770) Wall(1,440) Door(440) |
| MECH | `MECH_` | 19,670 | FlowSegment(8,732) FlowFitting(8,230) Proxy(2,246) Valve(462) |
| SPR | `SPR_` | 13,490 | FlowSegment(6,228) FlowFitting(5,900) FlowTerminal(1,354) Valve(8) |
| PLB | `PLB_` | 9,121 | FlowSegment(4,308) FlowFitting(4,231) Proxy(582) |
| ELE | `ELE_` | 2,798 | FlowTerminal(1,410) Proxy(1,125) FlowController(113) |
| STR | `STR_` | 2,827 | (structural members supplemental to ARC IFC) |
| FIRE | `FIRE_` | 6 | Proxy(6) — fire alarm panel/devices only |
| **Total** | | **62,291** | |

## Storey Structure

| Storey | Elements | Notes |
|--------|----------|-------|
| Level 1 | 8,215 | Ground clinical floor — A&E, main entry, reception |
| Level 2 | 7,905 | Clinical wards (incl. Ceiling 1,351 + TOS 400) |
| Level 3 | 12,484 | Largest floor — OT, ICU (incl. Ceiling 1,874 + TOS 464) |
| Level 4 | 11,726 | Clinical (incl. TOS 357) |
| Level 5 | 9,815 | Clinical / specialist (incl. Ceiling 1,539 + TOS 406) |
| Level 6 | 2,231 | Upper plant / admin (incl. Ceiling 447 + TOS 306) |
| Level 7 | 191 | Roof plant / mechanical (TOS 79) |
| Level 7A | 218 | Helipad level (TOS 63) |
| Unknown | 9,427 | Uncontained MEP — pipe runs not assigned to storey |
| **Total** | **62,212** | (79 across all variants) |

**Note:** 9,427 elements with storey="Unknown" are MEP runs (pipes, ducts) that cross storey
boundaries or are routed in ceiling/plenum space not tagged to a storey in the IFC. This is
normal for large MEP models. Our pipeline assigns them to the nearest storey by Z centroid.

## Cross-Discipline Fixture Stacking (S136 Finding)

**Symptom:** Toilet seats appear double-stacked in the 3D viewport.

**Diagnosis — IFC authoring origin, not our pipeline.**

In Revit multi-discipline hospital projects, bathroom fixtures are independently placed in
both the ARC and PLB discipline models using the same Revit family. This is deliberate:
- ARC team needs toilet/basin counts for HTM room data sheet compliance
- PLB team needs the same fixture at the same position for drain/vent pipe sizing

Our `--guid-prefix` extraction correctly stores both. The pipeline is clean. The BOM is
where deduplication must happen — not extraction.

| Fixture | ARC count | PLB count | Near-pairs (<5cm XY) | Z offset | Visual result |
|---------|-----------|-----------|---------------------|----------|--------------|
| Toilet-Wall-Mounted | 147 | 136 | 120 | **69mm** | Obvious stack |
| Sink_Wall-Mounted | 119 | 128 | 92 | **27mm** | Subtle (sub-perception at normal zoom) |

**Why sinks look cleaner:** 27mm vs 69mm. Both stack — sinks are below typical human
perception threshold in a 3D viewport at ward-floor zoom level. Same root cause.

**Only 3 raw disciplines (ARC / STR / MEP):**
`infer_discipline()` in `extract.py` maps from IFC class, not source IFC file:
- `IfcBuildingElementProxy` → ARC (default) — PLB toilets/sinks land here
- `IfcFlowSegment / IfcFlowFitting` → MEP — MECH, PLB, SPR pipe elements all merge here

The true sub-discipline (MECH vs PLB vs SPR) survives only in the GUID prefix (`MECH_xxx`,
`PLB_xxx`, `SPR_xxx`). The `guid_prefix` field in `classify_hosp.yaml` discipline block
restores it at BOM compile time — it is the only mechanism that separates MEP sub-trades.

**TODO (BOM level — separate session):** Cross-discipline fixture deduplication rule.
When ARC and PLB both claim the same physical fixture (same XY within 5cm, Z within 10cm,
same element_name), the compiled BOM quantity takeoff must count it **once**, from the PLB
BOM subtree (PLB is the authoritative procurement discipline for sanitary fixtures). ARC
retains the element for spatial compliance checking but its BOM line is marked `qty_for_takeoff=0`.
See: `docs/DISC_VALIDATE_SRS.md §TODO-FIXTURE-DEDUP`.

## GUID Extraction Note (S136)

The original merge (INSERT OR IGNORE, no prefix) lost 97% of MEP:

| | Before S136 | After S136 |
|--|-------------|-----------|
| Total elements | 20,260 | **62,291** |
| MEP elements | 2,187 | **41,126** |

**Root cause:** The four MEP discipline IFCs (MECH/PLB/ELE/SPR) were authored with
overlapping GUID ranges. When merged without disambiguation, INSERT OR IGNORE silently
dropped ~40K elements. Fixed by `tools/extract.py --guid-prefix DISC --append`.

**Not scrubbed:** No IFC file modification needed. The "37K imported, 19K stored" gap in
MECH is ifcopenshell's `by_type()` returning subtypes (IfcPipeSegment ⊂ IfcFlowSegment —
each element appears twice across two class iterations). INSERT OR IGNORE deduplicates
correctly. The 62,291 figure is the true unique-element count.

## BOM/ERP Regime Conduciveness

### Strengths

**1. Deep assembly hierarchy.**
9,527 rel_aggregates relationships — the deepest in the fleet. Hospital MEP is assembled
into risers, branches, sub-branches, and terminal groups. This maps directly to our
BUILDING → FLOOR → ASSEMBLY → LEAF hierarchy. The recursive BOM walker handles it natively.

**2. Full discipline separation.**
Each discipline file is cleanly separated (IFC consultant model practice). This aligns
perfectly with the Three Concerns model (WHAT/HOW/WHERE). MECH, PLB, ELE, SPR each become
a distinct BOM subtree under the floor BOM.

**3. Rich MEP vocabulary for ERP.**
MECH 8,732 pipe segments + 8,230 fittings, SPR 6,228 pipes + 5,900 fittings. These are
real procurement items — each unique dimensional signature (dx × dy × dz) is a distinct
M_Product row. Factorization will be significant (many repeated pipe runs per floor).

**4. Multi-wing heterogeneity.**
The screenshot shows clearly differentiated wings (main tower, podium block, annex, rooftop
plant). This means BOMs per wing are structurally different — a real test of our
building_bom_id → floor_bom_id composition chain. Mirrors real-world hospital contracting
where wings have separate sub-contracts.

**5. Helipad as discrete BOM unit.**
Level 7A / helipad is architecturally isolated (7A TOS = 63 elements, Level 7A = 155).
Clean candidate for a static_children BOM entry — a self-contained assembly with its own
procurement list (lighting, markings, drainage, tie-downs).

### Challenges

**1. No IfcSpace containment.**
Zero IfcSpace records. Hospital rooms are not modeled as IFC spatial zones. This means
auto-discover cannot resolve room BOMs by containment — it must fall back to Z-band storey
assignment. Floor-level BOM granularity only (not room-level) until IfcSpace is authored.

**2. 9,427 uncontained MEP elements.**
Pipe/duct runs with storey="Unknown". Z-centroid banding will assign them to the correct
level statistically, but the assignment is an inference, not an IFC authority. Acceptable
for BOM quantity takeoff but not for room-level spatial proofs.

**3. FIRE IFC is vestigial (6 elements).**
The fire alarm system is barely modeled — just 6 proxy elements. The actual fire suppression
system is in SPR (13,490 elements) which is the sprinkler network. FIRE discipline cannot
support a standalone BOM. Merge FIRE proxies into the SPR BOM subtree.

**4. Storey name duplication in spatial_structure.**
56 storey records for ~20 unique level names — each discipline IFC re-defines the same
storeys with different GUIDs. spatial_structure.guid is PRIMARY KEY so INSERT OR IGNORE
keeps the ARC version. The storey TEXT in elements_meta is correct (set at extraction time
from the element's containment, not from spatial_structure). No pipeline impact, but
the spatial_structure table is not authoritative for this building.

## Fire Protection (FP) — Route/Walker Testing Bed

**This is the most promising FP test model in the fleet.**

### Why

The Terminal (SJTII_TE) has 6,863 FP elements — but it is a transit hub, not a healthcare
facility. NFPA 13 hazard classifications differ: terminal = LIGHT/ORDINARY. A hospital is:
- **ICU/OT = LIGHT hazard** (NFPA 13 §19 — low combustible load)
- **Kitchen/laundry = ORDINARY Group 2** (higher density, NFPA 13 §20)
- **Pharmacy/store = ORDINARY Group 1**
- **Helipad fuel zone = EXTRA hazard** (NFPA 15)

Four hazard classes in one building = the most demanding compliance proof surface we have.

### SPR Network Facts (extracted)

| Class | Count | Role |
|-------|-------|------|
| IfcFlowSegment | 6,228 | Pipe mains and branches |
| IfcFlowFitting | 5,900 | Tees, elbows, reducers |
| IfcFlowTerminal | 1,354 | Sprinkler heads |
| IfcValve | 8 | Zone control valves |
| **Total SPR** | **13,490** | |

**1,354 sprinkler heads** across 7 levels of real clinical space. Contrast: the current
`RouteSprinklersVerb` tests use TB_LKTN `bilik_utama` (3.1×3.1m, 1 head). Hospital
gives us per-floor head-density proofs against real clinical room grids.

### Route/Walker Opportunities

**W-HOSP-FP-1: Head count per floor.**
For each of L1–L7, count SPR FlowTerminals and verify against floor area (m² from rtree).
Assert: head_density ≥ 1 head per N m² per NFPA 13 hazard class. This becomes the first
multi-storey sprinkler witness in the suite.

**W-HOSP-FP-2: Pipe network connectivity.**
9,527 assemblies + 5,900 fittings form a tree. Walk from zone valve (IfcValve) → branch
pipe → head. Assert: every FlowTerminal is reachable from at least one IfcValve within
the same storey. This is a graph-walk proof, not a geometry proof — pure DB.

**W-HOSP-FP-3: Helipad EXTRA hazard zone.**
Level 7A has 155+63=218 elements including SPR coverage. NFPA 15 requires foam-water
deluge for rooftop fuel zones. Assert: at least one FlowTerminal exists within AABB of
helipad and spacing ≤ 2.3m (EXTRA hazard). First NFPA 15 witness in the suite.

**W-HOSP-FP-4: MECH/SPR co-routing.**
MECH (HVAC) and SPR pipes occupy the same ceiling plenum. Extract MECH FlowSegments and
SPR FlowSegments on the same storey, compute AABB overlap count. Assert: overlapping
segment AABBs < N% (clearance proof — FP pipes must not be blocked by HVAC mains).

**W-HOSP-FP-5: Zone valve coverage.**
8 IfcValves in SPR = 8 zone control valves for 1,354 heads. NFPA 13 §8.16.1 requires
each zone ≤ 5,000 ft² (464 m²) or ≤ 100 heads. Assert: 1,354 / 8 = ~170 heads/zone →
must restructure into sub-zones. This is a known real-world defect in many hospital IFCs —
proving it from the DB is a powerful QA witness.

### Walker Architecture Fit

`RouteSprinklersVerb` currently reads `ad_room_boundary` for room AABB. Hospital has no
IfcSpace, so room AABB comes from the rtree of contained elements (floor + wall bounding
hull). The Walker would need to:

1. Accept storey (not room) as the spatial unit — already handled by Z-band
2. Partition heads by storey AABB → per-floor compliance proof
3. Support HAZARD class per storey (L1 = LIGHT, kitchen area = ORDINARY) — new enum needed

This is a 2-3 session upgrade path, not a fundamental re-architecture. The verb already
handles SPACING and HAZARD args. The Hospital gives us the motivation and the data.

## Onboarding Readiness

| Criterion | Status |
|-----------|--------|
| Extracted DB | DONE (62,291 elements, 127MB, LFS) |
| Discipline separation | DONE (7 GUID-prefixed disciplines) |
| Storey structure | DONE (L1–L7A, 20 unique storey names) |
| IfcSpace containment | ABSENT — Z-band fallback only |
| YAML (classify_hosp.yaml) | NOT YET |
| DSL (dsl_hosp.bim) | NOT YET |
| Pipeline gate | NOT YET |

**Next session:** Run `./scripts/onboard_ifc.sh --prefix HOSP --type Hospital --name "Hospital" --base CO --ifc DAGCompiler/lib/input/IFC/UNMERGED/Hospital_IFC4_ARC.ifc --skip-extract`

The `--skip-extract` flag skips Step 2 since the DB already exists. Step 3 (YAML/DSL
skeleton) will auto-generate from the existing DB. The ARC IFC is used only for the recon
step; the actual reference DB is the pre-built multi-discipline extraction.

## Engineering Reading — Key Metrics Interpreted

### Assembly Depth (9,527 rel_aggregates) — Good

Deep = faithful to procurement reality. A hospital MECH riser is assembled into:
sub-branch → branch → floor trunk → building riser. Each level is a real subcontract
scope, delivery unit, or inspection milestone. Our BOMWalker handles any depth natively
(recursive `getChildren()` — no hardcoded level count). The depth is not complexity overhead;
it IS the BOM. Compare TE at 0 rel_aggregates — Terminal MEP is flat (no assembly decomposition
authored). Hospital gives us the first genuinely deep assembly hierarchy to test the walker.

### IfcSpace = 0 — Rooms Not Declared (Not Missing)

Zero IfcSpace does NOT mean "rooms exist but aren't named." It means the enclosed volumes
were never authored as IFC spatial zones. The 1,468 IfcWall elements exist — the geometry
of the room boundaries is there — but no IFC entity claims the enclosed air volume.

**Consequence:** FINISH_WALLS_SRS.md cannot run. Its COMPLETE verb requires IfcSpace AABB
as the spatial input for wall placement. There is nothing to place against.

**Fix (separate session):** DECLARE ROOMS — infer IfcSpace boundaries from wall topology
(convex hull of enclosing wall segments per storey), register synthetic IfcSpace records in
`spatial_structure` and `elements_rtree`, then FINISH_WALLS can operate normally.
See: `docs/FINISH_WALLS_SRS.md §11` (Hospital reference case).

This is a one-session task that unlocks both FINISH_WALLS and room-level BOM granularity
for the Hospital. Until then, the pipeline works at floor-level granularity only.

### 62K Uniqueness — The DB Stress Test

TE's 50K elements are dominated by 33,324 identical IfcPlate roof tiles — factorised to a
handful of geometry hashes. Hospital's 62K has only 21,196 unique geometry hashes = 2.9×
reuse ratio (vs TE's ~64× on the plate population alone). The hospital is genuinely diverse:
valve variants, fitting angles, terminal types, clinical equipment proxies.

**What this proves about our approach:** A Bonsai user without our GPU-instanced library
loads each of the 62K elements as a full mesh object into Blender's scene graph — memory
pressure rises linearly with element count, performance degrades. Our approach stores one
geometry blob per `geometry_hash` in `base_geometries` and renders by transform reference.
Even at 2.9× reuse, 62K elements render from 21K unique meshes. At higher reuse (TE roof),
the ratio is extreme. **S231 browser instancing:** `InstancedMesh` reduced Hospital draw calls
from 63,182 to 22,800 (64% reduction, 5.5s stream). The Hospital proves instancing matters
even for low-reuse buildings — 17,870 single meshes + 4,930 instanced groups.
**S232 mobile merge (2026-04-27):** those 17,870 single meshes further merged by
storey|disc|rgba on mobile — estimated ~600 draw calls. Storey/disc filter via zero-scale
matrix on InstancedMesh instances. Pick returns guid for instanced, group info for merged.

### HO_ vs TE_ MEP — Different Vocabulary, Not Classic

Hospital MEP is structurally different from Terminal MEP — different hazard classes,
different product specs, different regulatory frameworks:

| Dimension | Terminal (TE_) | Hospital (HO_) |
|-----------|---------------|----------------|
| Dominant MEP | CW, LPG, airport fixtures | MECH HVAC, SPR, clinical PLB |
| Pipe material | Galvanised / carbon steel | Stainless (sterile), copper (potable) |
| Sprinkler type | Standard LIGHT hazard | Clean-room rated, EXTRA (helipad) |
| Gas | LPG for airport kitchens | Medical O₂, N₂, medical air (medical gas system) |
| HVAC | Airport comfort cooling | Positive-pressure clean rooms, OT 25 ACH |
| Regulatory standard | NFPA 13 LIGHT/ORD | NFPA 13/99, HTM 03-01, ASHRAE 170 |

The IFC classes are the same (`IfcFlowSegment`, `IfcPipeFitting`). The M_Product_Category
vocabulary is completely different. `HO_MECH_ICU` is not `TE_ACMV`.

**The user's dream is a real BOM decomposition:** Because the source IFCs are already
discipline-separated, the compiler can offer:
- HO_MECH_ICU — ICU mechanical (positive pressure, HEPA, 25 ACH)
- HO_MECH_WARD — Standard ward HVAC (comfort cooling, 6 ACH)
- HO_SPR_STERILE — Stainless sprinkler heads for sterile zones
- HO_PLB_CLINICAL — Copper potable plumbing for clinical washbasins
- HO_MED_GAS — Medical gas (O₂, N₂, medical air) manifold and drops

Each is a separate BOM subtree under its floor → discipline node. The ERP.db classifies
them under `AD_Org_ID=MECH` (or SPR/PLB) with `M_Product_Category=HO_*`. A procurement
officer can pull "all ICU HVAC on Level 3" as a single BOM query — that is our compiler's
demonstrable edge over any generic quantity surveyor tool.

## Fleet Position

| Building | Elements | Type | MEP | FP Network |
|----------|----------|------|-----|-----------|
| Terminal | 48,428 | Airport | Yes | 6,863 |
| **Hospital** | **62,291** | **Healthcare** | **41,126** | **13,490 (SPR)** |
| Clinic | ~15K | Healthcare (small) | Partial | No |
| BimWhale_Advanced | ~29K | Commercial | No | No |

Hospital is the fleet's first large-scale healthcare model and the first with a dedicated
sprinkler network of this scale (13,490 SPR elements vs TE's 979 SP elements).
It will become the primary FP/MEP test model once onboarded.

*Copyright (c) 2025-2026 Redhuan D. Oon. MIT Licensed.*
