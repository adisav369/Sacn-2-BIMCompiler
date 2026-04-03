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

## Fleet Position

| Building | Elements | Type | MEP | FP Network |
|----------|----------|------|-----|-----------|
| SJTII_Terminal | 48,428 | Airport | Yes | 6,863 |
| **Hospital** | **62,291** | **Healthcare** | **41,126** | **13,490 (SPR)** |
| Clinic | ~15K | Healthcare (small) | Partial | No |
| BimWhale_Advanced | ~29K | Commercial | No | No |

Hospital is the fleet's first large-scale healthcare model and the first with a dedicated
sprinkler network of this scale (13,490 SPR elements vs TE's 979 SP elements).
It will become the primary FP/MEP test model once onboarded.
