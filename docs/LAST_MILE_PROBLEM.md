# The Drift
> **Foundation:** [BBC](BOMBasedCompilation.md) · [DATA_MODEL](DATA_MODEL.md) · [BIM_COBOL](BIM_COBOL.md) · [MANIFESTO](MANIFESTO.md) · [TestArchitecture](TestArchitecture.md)

<div class="bim-banner" markdown>
<b>AI cannot repeat an outcome. Determinism is our religion here.</b>
</div>

[Vibe Programming](VibeProgramming.md) under strict architectural supervision built this compiler — but AI cannot see spatial geometry. It doesn't know a wall must sit on a slab, or that two columns can't overlap. Months of frustration revealed a pattern: the code drifts from spec precisely where spatial reasoning is required. This document tracks every known drift point between "it compiles" and "it ships with no AI inside."

See [BIMEyes](EYES_SRS.md) for how we taught the compiler to see. Below is the way we harness in Claude Code AI.

---

## Drift Gate

Before writing code that implements a spec section, the AI MUST:

1. **Quote the spec section** it is implementing
2. **Follow the spec mechanism**, not invent a shortcut — same output via a different path = draft
3. **Ask** if the spec is unclear — never guess
4. **Log a new drift point** before deviating from spec

---

## Session Checklist — 11 Drift Points

Every session ends by checking these 11 areas where AI-generated code is known to drift from spec. Each cites the spec section it guards.

### 1. Input = Output?
**Spec:** [BBC §2.2.1](BOMBasedCompilation.md) count invariant — SUM(non-PHANTOM qty) = output count.
**Prerequisite:** §3 (Compiler Only) must PASS first. If output was not compiled — only extracted — this count is a **draft**, not a verdict. (CP-5)

The compiler must produce exactly the same number of elements as the BOM specifies. Not more (over-production = splitting when it shouldn't), not fewer (lost elements = broken walker).

| Building | Elements | Verdict |
|----------|----------|---------|
| SH | 55/55 | PASS |
| FK | 82/82 | PASS |
| IN | 699/699 | PASS (120 window coordinate shifts — CLUSTER debt) |
| DX | 1099/1099 | PASS (MIRROR dimension swaps accepted) |
| TE | 48428/48428 | PASS (48336/48428 identity-matched = 99.8%) |

### 2. LOD400 Geometry?
**Spec:** [BBC §2.2](BOMBasedCompilation.md) — every leaf product resolves to component library geometry, never a fallback shape.

Zero `GEO_` fallback hashes in output. Every element's mesh traces to a real LOD entry in `component_library.db`. If the compiler generates a placeholder box instead of real geometry, this check catches it.

**Verdict:** PASS — G5 provenance and C8 geometry diversity both GREEN.

### 3. Compiler Only?
**Spec:** [BBC §2](BOMBasedCompilation.md) Compilation Model — every element traces to IFC extraction or BOM template. Nothing invented.

The compiler must never connect to `*_extracted.db` during compilation. It reads only from `{PREFIX}_BOM.db` (the dictionary) and writes to `output.db`. Three tamper rules (T18/T19/T20) enforce zero references to extraction databases in compiler source code.

**Verdict:** PASS — 0 violations.

### 4. Openings and Furniture?
**Spec:** [BBC §4](BOMBasedCompilation.md) tack convention — doors sit in walls, windows sit in walls, furniture sits in rooms.

Hosted elements (doors, windows) must reference their host wall via `host_element_ref`. Rotation must match the host face. No overlapping elements within the same product class (P06 proof).

**Verdict:** PASS — rotation witness (W-ROT) proven, P05/P06 zero violations.

### 5. Spec Fidelity?
**Spec:** All sources that dictate output — are they the RIGHT sources?

The real input is a [Construction Order](ProjectOrderBlueprint.md) ([C_Order](MANIFESTO.md#the-order-configure-to-order) → [C_OrderLine](MANIFESTO.md#the-order-configure-to-order)). The order references products from the BOM dictionary, and the compiler explodes it into placed elements. Everything else is supporting data.

| Source | What it dictates | Entry path |
|--------|------------------|------------|
| **C_Order → C_OrderLine** | What to build — the construction order with exceptions | [BIM Designer](BIM_Designer_UserGuide.md) (interactive) or YAML (test harness) |
| `{PREFIX}_BOM.db` | Assembly recipes — the BOM dictionary the order references | [IFCtoBOM pipeline](DATA_MODEL.md) (once per building type) |
| `component_library.db` | Product geometry, meshes, materials, orientation + `ad_slab_spec` (per-building slab overrides) | Extraction + curation |
| `ERP.db` | Validation rules — fire protection, MEP spacing, jurisdiction | [DocValidate](DocValidate.md) rule packs |
| `*.bimcobol` | Verb recipes: PLACE BOM, ROUTE, WIRE, TRIM | [BIM COBOL](BIM_COBOL.md) scripts |
| `BIMConstants.java` | Dimensional fallbacks (wall thickness, door/window dims) | Code constants — `ad_slab_spec` overrides slab thickness |

**Note:** `classify_*.yaml` is how the test harness represents a C_Order — it seeds the order and BOM for Rosetta Stone verification. In production, the [BIM Designer](BIM_Designer_UserGuide.md) creates the C_Order interactively via [BOM Drop](BOMBasedCompilation.md#34-bom-drop--interactive-modification).

The BOM walker must read products from the compile connection, not from extraction. If any value in the output cannot be traced to one of these sources, that's drift.

**Verdict:** PASS — all sources audited.

### 6. Output Path?
**Spec:** [BBC §3.3](BOMBasedCompilation.md) — single path: C_OrderLine → BOM explosion → placed elements.

There must be exactly ONE code path that writes elements to `output.db`. No side-channel insertions, no bulk copies, no shortcut SQL. Element counts verified for all 35 buildings.

**Verdict:** PASS.

### 7. Separate From Input?
**Spec:** [BBC §2](BOMBasedCompilation.md) — `{PREFIX}_BOM.db` is a pure dictionary, never written to during compilation.

The compiler reconstructs element positions from BOM tree traversal — it does NOT copy coordinates from extracted input databases. Evidence:

```
1. World origin     m_bom.origin_x/y/z         → SH: (-9.235, -2.746, -0.470)
2. BOM tree walk    PlacementCollectorVisitor   → accumulate parent + line dx/dy/dz
3. Leaf expansion   anchor + verb offset + ½dim → centroid (cx, cy, cz)
4. AABB write       ElementPersistence          → INSERT elements_rtree
```

The 1mm rounding (maxZ: 2.474 input → 2.473 output from `allocated_height_mm=2473`) is the fingerprint proving the compiler reconstructs from BOM integers, not float copies.

**Verdict:** PASS — T18 guards enforced, BOM tree forensic verified.

**Smoking gun: GEO debug mode (P123).** `-Dbim.geo.debug=true` activates a
self-verifying `[GEO] TACK` channel in `PlacementCollectorVisitor`. The log
does not require human arithmetic — it says MATCH or DRIFT per element.

**How it works:** At each LEAF placement (line 325), GEO mode:

1. Emits the **tack chain** — `anchor + offset + half → computed LBD`
2. Looks up the **extraction source** — queries `*_extracted.db` for the
   same GUID's original `elements_rtree` position
3. Compares and emits **verdict** — delta per axis, MATCH (≤1mm) or DRIFT

```
[GEO] TACK LEAF Furniture_Desk guid=3cUkl32...
  computed  LBD=(13.7710, 6.2940, 0.4700)
  extracted LBD=(13.7710, 6.2940, 0.4700)
  delta=(0.000mm, 0.000mm, 0.000mm) MATCH
```

If any element drifts:
```
[GEO] TACK LEAF SomeWall guid=2x8Kp...
  computed  LBD=(4.500, 2.100, 0.000)
  extracted LBD=(4.500, 2.300, 0.000)
  delta=(0.000mm, 200.000mm, 0.000mm) DRIFT Y=200mm
```

**Three proofs in one log line:**

- **A (formula execution):** the TACK line emits from line 325 using local
  variables. If it emits, the tack math ran. If absent, the element was
  placed by another path — drift signal.
- **B (data provenance):** `guid=3cUkl32...` traces this compiled element
  back to its IFC source entity via `m_bom_line_ma`.
- **C (positional accuracy):** MATCH/DRIFT with delta. No human arithmetic.
  The log itself is the verdict.

**T18 exemption:** GEO mode opens a read-only connection to `*_extracted.db`
for comparison. This is a debug verification channel, not compilation. The
connection is gated behind `bim.geo.debug=true` — normal compilation never
touches extraction (T18 enforced). Same principle as a debugger attaching
to a running process.

**Usage:**
```bash
# Self-verifying GEO proof for SH
./scripts/run_RosettaStones.sh classify_sh.yaml -Dbim.geo.debug=true
grep "GEO.*DRIFT" logs/pipeline_Sample\ House*.log
# Empty output = all elements MATCH. Any DRIFT line = position error.

# Filter to specific elements (avoids 48K lines on TE)
-Dbim.geo.filter=Desk
```

### 8. Visual Fidelity?
**Spec:** [TestArchitecture](TestArchitecture.md) C8 (geometry diversity) + C9 (axis dimensions) + P06 (overlap).

Three independent proofs triangulate visual correctness: per-instance geometry resolution (C8), axis dimension matching within 1mm (C9), and no same-class element overlaps (P06). [BIMEyes](EYES_SRS.md) adds ~14 per-element geometric proofs.

**Verdict:** PASS.

### 9. Orientation?
**Spec:** [BBC §4](BOMBasedCompilation.md) — rotation is a property of the BOM line, not invented at compile time.

Doors and windows must face the correct direction. `host_element_ref` links each opening to its host wall. Orientation data seeded from extraction (M16/M17 SQL).

**Verdict:** PASS — W-ROT witness proven. Rotation read from `m_bom_line.rotation_rule` (PlacementCollectorVisitor:183).

**PASS (S100-p91):** ProveStage rewired to BOM tree proofs (BomTreeProver). `hasRelationalData()` gate removed — ProveStage now runs for every building with compiled elements. Four BOM-term proof checks: P-PARENT (LEAF within parent extent), P-SIBLING (no sibling tack collisions), P-QTY (LEAF qty vs output count), P-TACK (tack position vs element centroid). SH: P-SIBLING 93/93 PASS. P-PARENT/P-QTY/P-TACK produce advisory WARNs (room origins not persisted in c_orderline — gap for future work). ComplianceStage: jurisdiction=MY, 53ms, 8 proof lines.

### 10. Who Checks the Tests?
**Spec:** [TestArchitecture](TestArchitecture.md) §Anti-Drift — no silent re-seal, no weakened assertions.

The tamper seal (SHA256 of 77 critical files) detects if a test was weakened to make it pass. Every seal change requires a full git diff review. T18-T20 tamper rules cross-validate. C8/C9 provide independent arithmetic verification that tests didn't drift.

**Verdict:** PASS — Seal v14 (77 files). Version bumped during S100 script refactoring (4 shell modules added).

### 11. Factorization?
**Spec:** [BBC §6](BOMBasedCompilation.md) verb factorization — N elements → 1 BOM line with qty=N.

Any code that factorizes BOM lines MUST preserve:
- **Material uniformity** — mixed materials → reject, fall through to unfactored
- **Dimension uniformity** — W/D/H within 50mm
- **Instance identity** — GUID `element_ref` preserved per instance
- **element_name ≠ element_ref** — name = product type (for grouping), ref = instance identity (GUID)

**Verdict:** PASS — material guard, identity threading, and per-instance geometry all verified.

---

## Known Limits

Two pre-existing issues that are accepted, not ignored:

| Area | Issue | Since | Why accepted |
|------|-------|-------|-------------|
| DX MIRROR | G2 volume -0.16%, 89 axis swaps (width↔depth in some walls) | S25 | MIRROR verb is incomplete — tracked in [ACTION_ROADMAP](ACTION_ROADMAP.md) CP-2 |
| TE CLUSTER | 1015/48428 elements differ by 1mm at float rounding boundary | S39c | CLUSTER uses ±10% tolerance for semi-regular grids — by design |

### §12 — Proof Data Gap (ProveStage + ComplianceStage)

**Status:** CLOSED (S100-p91)

Both stages now fire on SH and FK:

| Stage | Step | Code | Gate condition | Result (SH) |
|-------|------|------|---------------|-------------|
| ProveStage | 11 | `BomTreeProver` | `elementCount() == 0` | P-SIBLING 93/93 PASS. P-PARENT 15/28, P-QTY delta=2, P-TACK 0/28 (advisory WARNs). |
| ComplianceStage | 12 | `ComplianceStage` | `jurisdiction != null` | jurisdiction=MY, 53ms, 8 proof lines, submission package written. |

**Remaining advisory WARNs (non-blocking):**
- P-PARENT: room origins from YAML `origin_m` not persisted in c_orderline → parent extent check uses container tacks only
- P-QTY: delta=2 between LEAF qty sum and output count (static children counted differently)
- P-TACK: BOM walker applies room origins internally; c_orderline LEAF dx/dy/dz are post-walk world positions, not raw tack accumulations. Reconstruction gap = room origins not in output DB.

**What changed (p91):**
1. `BomTreeProver.java` — 4 BOM-term proof checks (no sidecar, no geometry language)
2. `hasRelationalData()` gate removed — ProveStage runs for every compiled building
3. `jurisdiction: MY` added to SH + FK YAMLs → ComplianceStage fires
4. Jurisdiction wired: YAML → ClassificationYaml → C_DocType → BuildingRegistry
5. SC_Run.BuildingID uses root BOM id (c_orderline root family_ref), not project name

### R25 — IFCtoBOM QA Failure Not Propagated to Gate Results

**Status:** CLOSED (S100-p67)
**Root cause:** IFCtoBOM pipeline ABORTs to log file. Rosetta script
treats missing BOM.db as "skip" not "fail." G1-G6 gates don't check
whether output was compiled or extracted.
**Fix:** G0-COMPILED gate (checks c_order > 0) + script fail-loud.
**Buildings affected:** TE (CO_TE). Extraction passes, BOM compilation blocked.

---

## Verb Fidelity — Approximate vs Exact

Not all verbs produce exact results. The distinction matters:

| Verb type | Verbs | Tolerance | Gated? |
|-----------|-------|-----------|--------|
| **Exact** | TILE, FRAME, ARRAY | 0mm | YES — G3-DIGEST must match |
| **Approximate** | ROUTE, CLUSTER | ±20% step, ±10% offset | NO — known tolerance, not gated |

ROUTE's `isUniformRun()` guard rejects non-uniform spacing (±20%). Non-uniform groups fall through to CLUSTER or flat writes. See [BIM_COBOL](BIM_COBOL.md) §19 for verb taxonomy.

---

## Geometric Fingerprint — Shape Identity

*This is one technique within [BIMEyes](EYES_SRS.md), the compiler's full geometric comprehension engine (26 proofs, 3 tiers).*

Dimensionless ratios that prove geometric equivalence regardless of scale:

```
Given AABB dimensions sorted smallest→largest as (S, M, L):

planarity   = S / L    "how thin"       → walls, slabs
elongation  = M / L    "how stretched"  → columns, pipes
squareness  = S / M    "cross-section"  → furniture, terminals
```

| Archetype | Condition | Typical IFC Classes |
|-----------|-----------|---------------------|
| PLANAR | planarity < 0.15, elongation ≥ 0.40 | IfcWall, IfcSlab, IfcPlate, IfcDoor |
| ELONGATED | planarity < 0.15, elongation < 0.40 | IfcColumn, IfcBeam, IfcPipeSegment |
| COMPACT | planarity ≥ 0.25, elongation ≥ 0.50 | IfcFurnishingElement, IfcFlowTerminal |

Implementation: `GeometricFingerprint.java`. Thresholds: planarity 0.15/0.20, elongation 0.40, epsilon 5%.

---

## Pipeline Debug — Log-Based Proofing

Following the Compiere/iDempiere convention, the pipeline uses Java's `java.util.logging` levels. Set the level to see more:

| Level | What you see |
|-------|-------------|
| **INFO** | Pipeline stages, gate verdicts, summary counts |
| **WARN** | Non-fatal anomalies (non-zero origins, assembly stubs, proof violations) |
| **FINE** | Stage timings, per-element proofs, automated Drift checklist |

**Rule:** All diagnostic output (rejected mutations, constraint violations, category mismatches) MUST use `BIMLogger.fine()`, never `System.err`. This ensures rejection messages appear in the FINE drift checklist and can be grepped from logs. `System.err` bypasses log filtering and is invisible to auditors reviewing FINE output.

### INFO — what always shows

```
[INFO ] PIPELINE     PIPELINE: Sample House [EXTRACTED]
[INFO ] PIPELINE     STEP 1: METADATA VALIDATION — starting
[INFO ] PIPELINE     STEP 3: COMPILE TO BUILDINGSPEC — starting
[INFO ] PIPELINE     STEP 4: ROUTE STAGE — starting
[INFO ] PIPELINE     STEP 7: VERB STAGE (BIM COBOL) — starting
[INFO ] PIPELINE     PIPELINE COMPLETE: Sample House — 58 elements
```

IFCtoBOM (extraction) logs verb detection and BOM QA at INFO:

```
[verb] Ground Floor STR: 1 verb patterns (4 instances), 12 unfactored
[ASI] SH_CW_STR seq=20: 3/6 instances have dimension variants (BIM_Slab)
=== BOM QA Validation ===
  [PASS] BOM count                                9 (BUILDING=1, FLOOR=4, SET=4)
  [PASS] Extraction reconciliation                58 extraction LEAFs vs 58 extracted (delta=+0)
```

### WARN — anomalies and proof violations

```
[WARN ] QA           [FAIL] Non-zero BOM origins — 1
[WARN ] PROVER       [VIOLATED] P27_WALL_ROOF_INTERSECTION — Basic Wall:Wall-Ext_102Bwk-75Ins-100LBlk-12P
                     — wall.maxZ=2.821 exceeds roofZ=2.071 by 0.750m at (1.64,-1.25)
```

### FINE — per-element proofs + Drift checklist

FINE adds three things: **stage timings**, **per-element mathematical proofs**, and an **automated Drift checklist** that runs the 11 drift points from this document.

Per-element proofs (4 proofs × 58 elements = 232 lines for SH):

```
[FINE ] PROVER       [PROVEN] P01_POSITIVE_EXTENT — Compound Ceiling:Plain — dx=9.3075 dy=5.6550 dz=0.0570
[FINE ] PROVER       [PROVEN] P04_STOREY_Z_BAND — Doors_IntSgl:810x2110mm — Z[0.000,2.145] within [0.0,3.5]±0.5
[FINE ] PROVER       [PROVEN] P05_NO_DUPLICATE_POSITION — GLOBAL — 58 placements, no duplicate centroids
[FINE ] PROVER       [PROVEN] P06_NO_SAME_CLASS_OVERLAP — GLOBAL — 58 placements, no same-class overlaps
[FINE ] PROVER       [PROVEN] P28_ROOF_COVERAGE — GLOBAL — roof covers walls
```

Automated Drift checklist (runs after PIPELINE COMPLETE):

```
[FINE ] DRIFT        LAST MILE CHECK: Sample House (LAST_MILE_PROBLEM.md §Session Checklist)
[FINE ] DRIFT        §1  Input=Output: expected=58, actual=58 → PASS
[FINE ] DRIFT        §2  LOD400 Geometry: 58/58 OK, 0 warn, 0 fail → PASS
[FINE ] DRIFT        §3  Compiler Only: T18/T19/T20 guard (checked at gate)
[FINE ] DRIFT        §6  Output Path: C_OrderLine → BOM explosion → elements (structural)
[FINE ] DRIFT        §7  Separate From Input: bom.db=library/_SH_compile.db
[FINE ] DRIFT        §11 Factorization: BOM line guards (checked at extraction)
[FINE ] DRIFT        SUMMARY: 6 pass, 0 fail, 2 deferred
```

### Quick commands

```bash
grep VIOLATED logs/pipeline_*.log    # Find any proof failures
grep DRIFT logs/pipeline_*.log       # Automated drift checklist results
grep "Stage.*completed" logs/pipeline_*.log  # Stage timings
```
