# Calibration SRS — DocEvent Generic vs Terminal Extracted
> **Foundation:** [BBC](BOMBasedCompilation.md) · [DATA_MODEL](DATA_MODEL.md) · [BIM_COBOL](BIM_COBOL.md) · [MANIFESTO](MANIFESTO.md) · [TestArchitecture](TestArchitecture.md)

<div class="bim-banner" markdown>
<b>Terminal is ground truth — generic rules must match its variance.</b> Qualification test comparing DocEvent-generated MEP layouts against 48,428 engineer-placed elements.
</div>

**Version:** 1.1 (2026-03-19, session 34 — §3.4 verdict rules, blocking vs advisory, GF exception)
**Depends on:** [DocAction_SRS.md](DocAction_SRS.md) §1.3, [DISC_VALIDATE_SRS.md](DISC_VALIDATE_SRS.md) §9, [TE_MINING_RESULTS.md](TE_MINING_RESULTS.md)
**Scope:** Qualification test comparing DocEvent rule-generated MEP layouts against
Terminal's engineer-placed MEP elements. Proves the generic engine produces
realistic results calibrated to ground truth.

> **Principle:** The Terminal (48,428 elements, 8 disciplines, 7+ storeys) is
> ground truth — real engineers placed real equipment. If DocEvent rules
> produce density, spacing, and coverage metrics WITHIN the Terminal's own
> observed variance, the generic engine is calibrated. Position-for-position
> match is NOT required — engineering invariants are what matter.

> **Applicability:** This comparison applies ONLY to DocEvent-routed
> disciplines (`YAML.disc = DocEvent`). RosettaStone buildings use
> `YAML.disc = {prefix}_BOM` — BOM pipeline populates from extracted data.
> RosettaStones are NOT subject to this calibration; they have their own
> Non-Disturbance gate (NonDisturbanceTest.java).

---

## 1. What We Compare

Position-for-position match is impossible (different building shape, different
room layout). But engineering invariants are shape-independent:

| Metric | What | Why it's shape-independent |
|--------|------|---------------------------|
| **Density** | elements per m² of floor area | Code-driven: NFPA 13 says X heads per m² regardless of shape |
| **NN spacing** | nearest-neighbour distance distribution | Code-driven: min/max spacing is a physical law |
| **Coverage ratio** | % of floor area within max_coverage_m² of a head | Code-driven: every point must be covered |
| **Product mix** | ratio of heads : pipes : fittings | Engineering practice: ~1 head per 3-5 pipe segments + fittings |
| **Z band** | height clustering per storey | Physics: ceiling-mounted = consistent Z |

### 1.1 What We Do NOT Compare

- Exact XY positions (different floor plans)
- Exact element count (floor area differs)
- Riser topology (vertical piping is building-specific)
- Assembly detail (fitting/valve placement is routing-specific)

---

## 2. Terminal Oracle — Observed Metrics

Derived from `SJTII_Terminal_extracted.db` (the reference DB, not TE_BOM.db).

### 2.1 FP (Fire Protection) — 6,863 elements

| Storey | IfcFireSuppressionTerminal | Floor AABB (m²) | Density (heads/m²) |
|--------|---------------------------|-----------------|-------------------|
| Aras Tanah (GF) | ~909* | ~3,694 m² | ~0.246 |
| Aras 01 (L1) | ~242* | ~2,749 m² | ~0.088 |
| Aras 02 (L2) | ~328* | ~3,867 m² | ~0.085 |
| Aras 03 (L3) | ~280* | ~2,459 m² | ~0.114 |

*Estimated from total 909 heads distributed across storeys per TE_BOM.db FP sub-trees.*

**NN spacing (from M1 mining):**
- Median: ~3,800 mm
- p50 cluster: 3,000–4,500 mm (NFPA 13 Light Hazard)
- Dominant grid: 4,500 mm

**Coverage per NFPA 13 LH:** max_coverage = 18.6 m² per head.
At 0.07-0.25 heads/m², coverage is 4.0–14.3 m²/head — within NFPA 13.

### 2.2 ELEC (Electrical) — 1,172 elements

| Storey | IfcLightFixture | Floor AABB (m²) | Density (lights/m²) |
|--------|----------------|-----------------|-------------------|
| Ceiling Level Kedai | 209 | ~3,694 m² | ~0.057 |
| Ceiling Level 02 | 178 | ~3,867 m² | ~0.046 |
| Ceiling Level 03 | 152 | ~2,459 m² | ~0.062 |

**NN spacing (from M4 mining):** avg ~3,964 mm, aligned to ceiling grid module.

### 2.3 Observed Variance Bands

The Terminal itself has variance across storeys — the calibration tolerance
should be WITHIN this observed variance, not tighter.

| Metric | Terminal Min | Terminal Max | Variance Factor |
|--------|------------|-------------|----------------|
| FP density (heads/m²) | 0.07 | 0.25 | 3.6× |
| ELEC density (lights/m²) | 0.04 | 0.07 | 1.8× |
| FP NN spacing (mm) | 3,000 | 5,500 | 1.8× |
| ELEC NN spacing (mm) | 3,500 | 5,000 | 1.4× |

**Calibration tolerance:** DocEvent result must be within 2× of Terminal
median — generous because Terminal has high-density zones (GF departures)
and low-density zones (service areas) that skew the range.

---

## 3. Calibration Test Design

### 3.1 Test Setup

```
INPUT:
  - TE_BOM.db (extracted discipline BOMs — oracle)
  - TE reference DB (elements_meta + element_transforms — positions)
  - ERP.db (AD_Val_Rule — mined rules, the basis for DocEvent)
  - ERP.db (ad_space_type_mep_bom, ad_fp_coverage, ad_element_mep)
  - component_library.db (M_Product dimensions, LOD geometry)

PROCESS:
  For each MEP discipline (FP, ELEC, CW, SP, ACMV):

    Step 1: EXTRACT oracle metrics from TE
      → Query TE reference DB for per-storey element counts
      → Compute: density, NN spacing distribution, Z band
      → These are the ground truth targets

    Step 2: COMPUTE DocEvent metrics from rules
      → Read floor AABB from TE_BOM.db (same floor geometry)
      → Apply ad_space_type_mep_bom rules:
          qty = ceil(floor_area × per_area_normal)
      → Apply ad_fp_coverage spacing:
          pitch = min(max_spacing, dim / ceil(dim / typical_spacing))
      → Compute: density, grid spacing, expected Z

    Step 3: COMPARE
      → Density ratio = DocEvent density / TE density
      → Spacing delta = |DocEvent pitch - TE median NN|
      → Coverage check: DocEvent covers >= 90% of TE coverage

OUTPUT:
  CalibrationResult per discipline per storey:
    - density_ratio (target: 0.5–2.0)
    - spacing_delta_mm (target: < 1000mm)
    - coverage_pct (target: >= 80%)
    - verdict: CALIBRATED / DRIFT / UNCALIBRATED
```

### 3.2 Verdict Thresholds

| Verdict | Condition | Meaning |
|---------|-----------|---------|
| **CALIBRATED** | density_ratio ∈ [0.5, 2.0] AND spacing_delta < 1000mm | Generic matches Terminal reality |
| **DRIFT** | density_ratio ∈ [0.3, 3.0] AND spacing_delta < 2000mm | Close but needs tuning |
| **UNCALIBRATED** | Outside DRIFT bounds | Rules are wrong or seed data missing |

These bands are deliberately wide for Phase 1 — they prove the engine is
in the right ballpark. Tightening to [0.8, 1.2] is Phase 2 after
ad_element_mep and ad_fp_coverage are fully seeded.

### 3.3 What Fails the Test

- DocEvent computes 0 heads for a floor where TE has 1,132 → UNCALIBRATED
  (indicates missing seed data — ad_space_type_mep_bom has no entry for
  this space type)
- DocEvent computes 50mm spacing where TE shows 4,500mm → UNCALIBRATED
  (indicates wrong rule parameter)
- DocEvent computes 10× density → DRIFT
  (indicates per_area_normal too aggressive)

### 3.4 Verdict Rules — Blocking vs Advisory

CalibrationTest disciplines fall into two tiers:

| Tier | Disciplines | Verdict Meaning | CI Impact |
|------|------------|----------------|-----------|
| **Blocking** | FP, ELEC | UNCALIBRATED → test FAIL, merge blocked | Must be CALIBRATED or DRIFT to pass |
| **Advisory** | CW, SP, ACMV | UNCALIBRATED → WARN only, merge allowed | Topology-driven — density calibration only (Phase 1) |

**Overall test verdict:**

| Condition | Verdict | CI |
|-----------|---------|-----|
| All blocking disciplines CALIBRATED | **PASS** | GREEN |
| All blocking disciplines ≤ DRIFT, none UNCALIBRATED | **WARN** | GREEN (developer alerted) |
| Any blocking discipline UNCALIBRATED | **FAIL** | RED (merge blocked) |
| Advisory discipline UNCALIBRATED | **WARN** (noted, not blocking) | GREEN |

**GF exception (occupancy class):** Terminal Ground Floor is an airport
concourse (occupancy OH/COM). DocEvent baseline uses residential (LH).
GF FP density ratio ~3.5× is expected DRIFT — do not fail on GF.
Implementation: if storey = GF AND density_ratio > 3.0, verdict = DRIFT
(not UNCALIBRATED), with note `occupancy_class_mismatch`.

**Conflict with Non-Disturbance:** A rule can PASS Non-Disturbance (does not
flag Terminal elements as violations) but FAIL Calibration (DocEvent produces
wrong density). This is valid — the rule correctly describes Terminal but
may be miscalibrated for generative use. Resolution: tune `per_area_normal`
in ad_space_type_mep_bom until Calibration reaches DRIFT or better.

### 3.5 Non-Disturbance Relationship

This calibration test is complementary to NonDisturbanceTest:

| Test | What it proves | Direction |
|------|---------------|-----------|
| NonDisturbanceTest | Mined rules don't flag Terminal elements as violations | Rules ← Building |
| CalibrationTest | DocEvent using those rules produces Terminal-like layouts | Rules → Building |

Non-Disturbance: "rules describe reality" (passive check).
Calibration: "rules produce reality" (active generation check).

---

## 4. Per-Discipline Calibration Metrics

### 4.1 FP — Fire Protection

| Metric | Source | Formula |
|--------|--------|---------|
| TE head count per storey | `elements_meta WHERE ifc_class='IfcFireSuppressionTerminal'` | COUNT per storey |
| TE floor area per storey | `m_bom` children of root (getChildren) | aabb_width_mm × aabb_depth_mm / 1e6 |
| TE density | head_count / floor_area | heads/m² |
| DocEvent qty | `ceil(floor_area × per_area_normal)` | from ad_space_type_mep_bom (0.07/m²) |
| DocEvent density | DocEvent_qty / floor_area | heads/m² |
| TE NN spacing | `SpatialPredicates.nnDistance()` on TE head positions | mm |
| DocEvent pitch | `min(max_spacing, dim / ceil(dim / typical_spacing))` | mm from AD_Val_Rule 601 |

**Expected calibration:** DocEvent density ~0.07/m² (NFPA 13 LH baseline).
Terminal GF density ~0.246/m² (high-density departure lounge). Ratio ~3.5×.
This is DRIFT, not CALIBRATED — expected because Terminal GF is an airport
concourse (high occupancy), not a standard office/residential space.

**Verdict logic for FP:**
- CALIBRATED if DocEvent density within [0.5×, 2.0×] of per-storey TE density
- DRIFT if within [0.3×, 3.0×]
- UNCALIBRATED otherwise
- **Exception:** GF (airport concourse) is expected DRIFT — different occupancy class

### 4.2 ELEC — Electrical

| Metric | Source |
|--------|--------|
| TE light count | `elements_meta WHERE ifc_class='IfcLightFixture'` per storey |
| DocEvent qty | `ceil(floor_area × per_area_normal)` — ad_space_type_mep_bom (0.1/m²) |
| TE NN spacing | From M4 mining: avg ~3,964mm |
| DocEvent pitch | From AD_Val_Rule 803: typical_spacing=3000mm, max=5000mm |

### 4.3 CW/SP — Plumbing (advisory only)

Plumbing density is topology-driven (riser + branch), not area-driven.
DocEvent can place fixtures (toilet, sink, basin) per ad_space_type_mep_bom
(fixed qty per room type), but pipe network topology is Phase 2.

**Calibration metric:** fixture count per storey only. Pipe/fitting counts
are topology-dependent and not comparable via density.

### 4.4 ACMV — HVAC (advisory only)

Similar to plumbing: air terminals are density-driven (diffusers per m²),
but duct routing is topology-dependent. Calibrate diffuser count only.

---

## 5. Implementation

### 5.1 Test Class: `CalibrationTest.java`

```java
@DisplayName("Calibration — DocEvent Generic vs Terminal Extracted")
class CalibrationTest {

    // W-CAL-FP-DENSITY: FP head density within Terminal variance
    // W-CAL-FP-SPACING: FP grid pitch within 1000mm of Terminal NN median
    // W-CAL-ELEC-DENSITY: ELEC light density within Terminal variance
    // W-CAL-ELEC-SPACING: ELEC grid pitch within 1000mm of Terminal NN median
    // W-CAL-RULES-EXIST: All MEP disciplines have DocEvent seed data

    // Step 1: Read TE oracle data
    // Step 2: Compute DocEvent predictions from rules
    // Step 3: Compare and emit CalibrationResult

    record CalibrationResult(
        String discipline,
        String storey,
        double teDensity,       // elements per m²
        double docEventDensity, // elements per m²
        double densityRatio,    // docEvent / te
        double teNNMedian,      // mm
        double docEventPitch,   // mm
        double spacingDelta,    // |docEventPitch - teNNMedian|
        Verdict verdict
    ) {}

    enum Verdict { CALIBRATED, DRIFT, UNCALIBRATED }
}
```

### 5.2 DAO: `CalibrationDAO.java`

```java
/**
 * DAO for calibration queries — reads from TE reference DB + ERP.db.
 *
 * Two concerns:
 *   1. Oracle extraction: query TE for ground truth metrics
 *   2. Rule prediction: compute DocEvent layout from rules + floor AABB
 */
class CalibrationDAO {

    /** Oracle: per-storey element count by ifc_class from TE reference DB */
    Map<String, Integer> teCountByStorey(Connection teConn,
        String discipline, String ifcClass);

    /** Oracle: per-storey NN spacing from TE reference DB */
    Map<String, Double> teNNMedianByStorey(Connection teConn,
        String ifcClass);

    /** Oracle: floor AABB from TE_BOM.db */
    Map<String, double[]> floorAABB(Connection bomConn);

    /** Prediction: DocEvent qty from ad_space_type_mep_bom rules */
    double docEventDensity(Connection discConn, String spaceType,
        String mepProduct);

    /** Prediction: DocEvent pitch from AD_Val_Rule + ad_fp_coverage */
    double docEventPitch(Connection valConn, int ruleId,
        double floorDimMm);
}
```

### 5.3 Verb Usage

| Verb | Where Used | What |
|------|-----------|------|
| `CHECK CALIBRATION` | CalibrationTest | Compare DocEvent vs TE metrics |
| `TILE` / `ALONG` | DocEvent pitch computation | Grid placement at computed spacing |
| `PLACE` | DocEvent single-element | Fixed-qty elements (1 per room) |

---

## 6. Phase Plan

| Phase | What | Tolerance | Depends On |
|-------|------|-----------|------------|
| **Phase 1** (now) | Density + NN spacing comparison, wide bands [0.5, 2.0] | DRIFT acceptable | TE reference DB, ERP.db, TE_BOM.db |
| **Phase 2** | Tighten to [0.8, 1.2], add coverage check | CALIBRATED required | Full ad_element_mep + ad_fp_coverage seed |
| **Phase 3** | Position grid fidelity (DocEvent grid vs TE positions) | NN match per element | DocEvent placement engine implemented |

Phase 1 proves the RULES are in the right ballpark.
Phase 2 proves the SEED DATA produces correct quantities.
Phase 3 proves the PLACEMENT ENGINE produces correct positions.

---

## 7. Seed Data Gaps — 3 Fixes (session 33 analysis)

Three seed data issues were identified in session 33 during ERP.db
creation. Each fix is scoped, testable, and does NOT touch existing passing tests.

### 7.1 Fix 1: Rule 803 (ELEC spacing) — INSERT into ERP.db

**Problem:** CalibrationTest §4.2 references `AD_Val_Rule 803` for ELEC spacing
(`typical_spacing=3000mm, max=5000mm`), but no such rule exists in ERP.db.
DocEvent pitch computation returns 0 → UNCALIBRATED for ELEC discipline.

**Fix:** INSERT one AD_Val_Rule row (id=803, name='IES_LIGHT_SPACING',
discipline='ELC', rule_type='COMPLIANCE') + AD_Val_Rule_Param rows
(typical_spacing_mm=3000, max_spacing_mm=5000).

**Impact:** ERP.db only (append). No Java changes. No existing test
references rule 803 — CalibrationTest is currently advisory for ELEC.

**Migration:** `V007_elec_spacing_rule.sql` (append-only, ERP.db).

### 7.2 Fix 2: LIGHT per_area_normal — UPDATE in ERP.db

**Problem:** `ad_space_type_mep_bom` has `per_area_normal=0.0` for LIGHT across
most space types. DocEvent computes qty=0 → density=0 → UNCALIBRATED.
Terminal shows ~0.05 lights/m² (814 lights across ~16,000 m² total floor area).

**Fix:** UPDATE `ad_space_type_mep_bom SET per_area_normal=0.05` WHERE
`mep_product_id='LIGHT' AND per_area_normal=0`.

**Impact:** ERP.db only. component_library.db copy untouched
(Phase 2 not yet active). No Java changes. CalibrationTest ELEC density
should move from UNCALIBRATED to DRIFT or CALIBRATED.

**Migration:** `DV004_light_per_area.sql` (ERP.db).

### 7.3 Fix 3: FP NN head-only filter — CalibrationDAO code change

**Problem:** `CalibrationDAO.teNNMedianByStorey()` counts ALL
IfcFireSuppressionTerminal elements for NN spacing, including hose reels
(10 elements) which are sparse floor-mounted units. These outliers inflate
the NN median, making DocEvent grid pitch look closer than it is.

**Fix:** Filter CalibrationDAO NN query to pendent/upright heads only:
`WHERE element_type LIKE '%sprinkler head%'`. Hose reels excluded from
NN spacing comparison (they are not grid-placed).

**Impact:** CalibrationDAO.java only (one SQL WHERE clause). No schema changes.
FP NN median will decrease slightly (tighter), which may shift some storeys
from CALIBRATED to DRIFT — but that's more accurate.

**Pre-check:** Run CalibrationTest before AND after to verify no CALIBRATED
result flips to UNCALIBRATED.

### 7.4 Impact Summary

| Fix | DB | File Changed | Existing Tests | Risk |
|-----|-----|-------------|----------------|------|
| Rule 803 | ERP.db | V007 migration | None reference 803 | Zero |
| LIGHT per_area | ERP.db | DV004 migration | DiscValidationDBTest seed counts unchanged (UPDATE not INSERT) | Zero |
| FP NN filter | — | CalibrationDAO.java | CalibrationTest FP spacing may shift verdict | Low — run before/after |

---

## 8. Traceability

| Witness | What it Proves | Spec Reference |
|---------|---------------|---------------|
| W-CAL-FP-DENSITY | FP density ratio within [0.3, 3.0] per storey | §4.1 |
| W-CAL-FP-SPACING | FP pitch within 1000mm of TE NN median | §4.1 |
| W-CAL-ELEC-DENSITY | ELEC density ratio within [0.3, 3.0] per storey | §4.2 |
| W-CAL-ELEC-SPACING | ELEC pitch within 1000mm of TE NN median | §4.2 |
| W-CAL-RULES-EXIST | AD_Val_Rule seed data exists for FP + ELEC | §3.3 |
| W-CAL-ND-COMPAT | Calibration test does not disturb NonDisturbanceTest | §3.4 |

---

*References:
[DocAction_SRS.md](DocAction_SRS.md) §1.3 (processIt DocEvent path) |
[DISC_VALIDATE_SRS.md](DISC_VALIDATE_SRS.md) §9 (LOD resolution chain) |
[TE_MINING_RESULTS.md](TE_MINING_RESULTS.md) (M1 FP spacing, M4 ELEC spacing) |
[NonDisturbanceTest.java](https://github.com/red1oon/BIMCompiler/blob/master/BonsaiBIMDesigner/src/test/java/com/bim/designer/NonDisturbanceTest.java) (complementary gate)*
