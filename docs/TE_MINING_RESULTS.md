# Terminal Mining Results — §7.4 AD_Val_Rule Seed Data
> **Foundation:** [BBC](BOMBasedCompilation.md) · [DATA_MODEL](DATA_MODEL.md) · [BIM_COBOL](BIM_COBOL.md) · [SystemContract](SystemContract.md) · [TestArchitecture](TestArchitecture.md)

**Date:** 2026-03-18 (session 21)
**Source:** `component_library.db` / `I_Element_Extraction` WHERE building_type='SJTII_Terminal'
**Element count:** 48,428

---

## Discipline Inventory

| Discipline | Elements | Unique IFC classes |
|-----------|----------|-------------------|
| ARC | 34,724 | 14 |
| FP | 6,863 | 8 |
| ACMV | 1,621 | 4 |
| CW | 1,431 | 6 |
| STR | 1,429 | 5 |
| ELEC | 1,172 | 3 |
| SP | 979 | 4 |
| LPG | 209 | 3 |

---

## M1: Sprinkler Head Spacing (FP)

**Source:** 909 IfcFireSuppressionTerminal heads
**Query:** NN distance per storey (planar XY, < 6000mm threshold)
**Pairs found:** 5,055

| Metric | Value |
|--------|-------|
| Min | 0.0 mm (co-located heads) |
| Avg | 4,011 mm |
| Max | 6,000 mm (threshold) |

**Distribution (500mm buckets):**

| Bucket (mm) | Pairs | Interpretation |
|-------------|-------|---------------|
| 0 | 21 | Co-located (stacked risers or MEP junctions) |
| 500 | 103 | Very close — branch pipe connections |
| 1000 | 80 | |
| 1500 | 150 | |
| 2000 | 192 | |
| 2500 | 313 | Approaching minimum |
| **3000** | **713** | **NFPA 13 LH minimum zone (3000-3600mm)** |
| **3500** | **820** | **Primary cluster — code-compliant spacing** |
| **4000** | **507** | **Standard spacing range** |
| **4500** | **1,200** | **Peak cluster — dominant grid** |
| 5000 | 382 | Extended spacing (high ceilings) |
| 5500 | 574 | Near-maximum |

**Conclusion:** Two clear clusters:
1. **3000-4000mm** (2,040 pairs) — standard NFPA 13 Light Hazard spacing
2. **4500mm** (1,200 pairs) — dominant grid pitch (likely 4.5m × 4.5m grid)

**Proposed AD_Val_Rule:** `NFPA13_LH_SPACING`
- `min_spacing_mm = 3000` (NFPA 13 §8.6.2.2.1 Light Hazard)
- `max_spacing_mm = 4600` (NFPA 13 max coverage area ÷ min dimension)
- `tolerance_mm = 100` (construction tolerance)

---

## M4: Light Fixture Grid (ELEC)

**Source:** 814 IfcLightFixture
**Query:** NN distance per storey (planar XY, < 6000mm threshold)
**Pairs found:** 5,101

| Metric | Value |
|--------|-------|
| Min | 0.0 mm |
| Avg | 3,964 mm |
| Max | 5,999 mm |

**Conclusion:** Similar distribution to sprinklers — average grid pitch ~4000mm.
Light fixtures are typically aligned to ceiling grid (600mm × 600mm module).

**Proposed AD_Val_Rule:** `IES_LIGHT_SPACING`
- `max_spacing_mm = 5000` (ensure adequate lux coverage)
- Category: advisory (WARN, not BLOCK)

---

## M5: Light Fixture Ceiling Offset (ELEC)

**Z-height distribution per storey:**

| Storey | Count | Min Z (m) | Avg Z (m) | Max Z (m) | Avg Height (mm) |
|--------|-------|-----------|-----------|-----------|-----------------|
| Ground Floor | 230 | 2.231 | 2.783 | 3.613 | 100.5 |
| Level 1 | 115 | 6.733 | 6.913 | 6.915 | 92.7 |
| Level 2 | 172 | 10.215 | 10.872 | 10.915 | 89.3 |
| Level 3 | 153 | 11.834 | 14.768 | 14.915 | 90.3 |
| Level 4 | 81 | 15.495 | 15.678 | 17.934 | 534.7 |
| Roof | 63 | 18.115 | 20.650 | 23.807 | 117.1 |

**Conclusion:** Consistent per storey (Level 1-3 show tight clustering).
Level 4 has high variance (534.7mm avg height → may include pendant fixtures).
Ground Floor has wider spread (2.2-3.6m) — double-height areas.

---

## M6: Column Grid (STR)

**Source:** IfcColumn elements
**Query:** NN distance per storey (planar XY, < 15000mm threshold)
**Pairs found:** 758

**Distribution (1000mm buckets):**

| Bucket (mm) | Pairs | Interpretation |
|-------------|-------|---------------|
| 0 | 30 | Adjacent columns (paired/cluster) |
| 1000-4000 | 35 | Short spans (canopy, transfer beams) |
| **5000** | **62** | **Secondary grid** |
| 6000-7000 | 99 | |
| **8000-9000** | **183** | **Primary structural bay** |
| 10000-11000 | 70 | |
| **12000** | **91** | **Large bay spacing** |
| 13000-14000 | 188 | Extended bays |

**Conclusion:** Multi-modal distribution reflecting Terminal's structural system:
- **Primary bay:** ~8000-9000mm (183 pairs) — standard structural grid
- **Secondary bay:** ~5000mm (62 pairs) — cross-bay stiffening
- **Large bay:** ~12000-14000mm (279 pairs) — departure hall long spans

**Proposed AD_Val_Rule:** `STR_COLUMN_CONTINUITY`
- Tier 3 (vertical continuity): max XY drift ≤ 25mm across storeys
- Bay spacing itself is not a validation rule — it's design intent

---

## M8: Roof Plate Dimensions (ARC)

| IFC Class | Count | Avg Width (mm) | Avg Depth (mm) | Avg Height (mm) |
|-----------|-------|----------------|----------------|-----------------|
| IfcPlate | 33,324 | 495.5 | 149.6 | 106.0 |
| IfcRoof | 2 | 5,713.9 | 15,999.9 | 828.3 |
| IfcSlab | 91 | 6,975.4 | 6,905.4 | 105.0 |

**Conclusion:** IfcPlate at ~495×150mm confirms the TILE verb parameters
(already verified: TILE(15×294, 495mm step) = 0.0mm fidelity).

---

## M12: ELEC × SP Clearance (Cross-Discipline)

### Attempt 1: AABB clearance (WRONG — false positives)

| Storey | Min Clearance X (mm) | Min Clearance Y (mm) | Pairs |
|--------|---------------------|---------------------|-------|
| Ground Floor | 0 | 0 | 67,938 |
| Level 1 | 0 | 0 | 24,150 |
| Level 2 | 0 | 0 | 62,750 |
| Level 3 | 0 | 0 | 23,400 |
| Level 4 | 19.1 | 0 | 784 |
| Roof | 17.2 | 0 | 803 |

AABB produces 0mm everywhere — false positives from bounding box overlap on
elongated MEP elements. Useless for clearance detection.

### Attempt 2: ERP-maths clearance (CORRECT — uses product dimensions)

**Method:** Centreline-to-centreline distance minus half cross-section of each
element. Cross-section = MIN(width, depth) — the shortest XY dimension of the
AABB, which is the pipe/conduit diameter for elongated elements.

```
clearance = centreline_2D_distance - radius_a - radius_b

where:
  centreline = (minX + maxX) / 2, (minY + maxY) / 2
  radius     = MIN(width, depth) / 2    ← cross-section, not AABB envelope
```

**This is ERP maths, not geometry.** We compute from M_Product dimensions and
placement positions — the same data the BOM already carries. No mesh, no Bonsai
viewport, no Blender. Works identically for Rosetta Stone verification AND for
BIM Designer generative placement. Ready-made out-of-the-box.

| Storey | Min (mm) | Avg (mm) | Pairs | Overlap (<0) | Under 150mm |
|--------|---------|---------|-------|-------------|-------------|
| Ground Floor | -13.2 | 3,459 | 3,923 | 1 | 21 |
| Level 1 | -150.3 | 3,083 | 980 | 6 | 7 |
| Level 2 | -106.7 | 3,600 | 6,417 | 4 | 7 |
| Level 3 | 260.3 | 3,491 | 2,200 | 0 | 0 |
| Level 4 | 679.3 | 3,612 | 106 | 0 | 0 |
| Roof | 681.8 | 3,272 | 280 | 0 | 0 |

**Ground Floor detail (200mm buckets, <2000mm only):**

| Bucket (mm) | Pairs | Interpretation |
|-------------|-------|---------------|
| 0 | 24 | Just clear — touching but not overlapping |
| 200 | 20 | Marginal clearance |
| 400 | 18 | |
| 600 | 25 | |
| 800 | 46 | |
| 1000 | 84 | Standard MEP zone separation |
| 1200 | 88 | |
| 1400 | 108 | |
| 1600 | 166 | |
| 1800 | 158 | |

**Findings:**
1. **11 true overlaps** across all floors (negative clearance) — genuine clash candidates
2. **35 pairs under 150mm** on lower floors — NEC 300.4 minimum zone
3. **Level 3+ has zero violations** — MEP spacing is generous on upper floors
4. Average clearance ~3.4m — most ELEC/SP elements are well separated

**Element cross-section profiles used:**

| Discipline | IFC Class | Count | Avg Cross-Section (mm) |
|-----------|-----------|-------|----------------------|
| ELEC | IfcLightFixture | 814 | 367 |
| ELEC | IfcBuildingElementProxy | 339 | 115 |
| SP | IfcPipeSegment | 455 | 343 |
| SP | IfcPipeFitting | 372 | 106 |
| SP | IfcFlowTerminal | 150 | 351 |

**Proposed AD_Val_Rule:** `NEC_ELEC_SP_CLEARANCE`
- `min_clearance_mm = 150` (NEC 300.4 minimum)
- `tolerance_mm = 25` (construction tolerance)
- Tier 2 (cross-discipline)
- Verdict: WARN (not BLOCK — original design may have approved exceptions)
- Non-Disturbance: 35 existing under-150mm pairs → seed AD_Val_Rule_Exception

---

## Summary — Rules Ready for AD_Val_Rule Seeding

| Rule | Tier | Confidence | Ready? |
|------|------|-----------|--------|
| M1: Sprinkler NN spacing | 1 | HIGH (clear bimodal distribution) | YES |
| M4: Light fixture grid | 1 | MEDIUM (single-mode, advisory) | YES |
| M5: Ceiling offset | 1 | HIGH (per-storey constant) | YES |
| M6: Column grid | 1 | HIGH (multi-modal, design intent) | YES (as continuity check) |
| M8: Roof tile pitch | 1 | VERIFIED (TILE verb: 0.0mm) | Already encoded |
| M12: ELEC-SP clearance | 2 | HIGH (ERP-maths: 11 true overlaps, 35 under 150mm) | YES |

**Status (2026-03-19):** All rules seeded in V004_mined_rules.sql. Non-Disturbance
analysis completed (G4_SRS §6). AD_Val_Rule_Exception seeded for under-150mm ELEC-SP pairs.

**Architecture note (M12):** The ERP-maths clearance method uses only
`M_Product` dimensions + placement positions — data already in the BOM and
component library. No mesh, no viewport, no Blender. This means the same
clearance check works at compile time (Rosetta Stone verification), at
design time (BIM Designer ambient compliance), and at batch time (reports,
dashboards). Zero additional infrastructure. The Bonsai geometry problem
is sidestepped entirely by computing from the semantic data layer.

---

*Source queries: DocValidate.md §7.4 + extensions. All data from SJTII_Terminal (48,428 elements).*
