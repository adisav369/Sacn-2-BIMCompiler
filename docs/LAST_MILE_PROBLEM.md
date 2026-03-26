# The Drift
> **Foundation:** [BBC](BOMBasedCompilation.md) · [DATA_MODEL](DATA_MODEL.md) · [BIM_COBOL](BIM_COBOL.md) · [MANIFESTO](MANIFESTO.md) · [TestArchitecture](TestArchitecture.md)

<div style="max-width: 620px; margin: 24px auto; padding: 20px 32px; background: linear-gradient(to right, #fce4ec, #fff3e0, #fce4ec); border-left: 4px solid #d32f2f; border-right: 4px solid #d32f2f;">
<b>AI cannot repeat an outcome. Determinism is our religion here.</b>
</div>

Vibe Programming under strict architectural supervision built this compiler — but AI cannot see spatial geometry. It doesn't know a wall must sit on a slab, or that two columns can't overlap. Months of frustration revealed a pattern: the code drifts from spec precisely where spatial reasoning is required. This document tracks every known drift point between "it compiles" and "it ships with no AI inside."

See [BIMEyes](EYES_SRS.md) for how we taught the compiler to see. Below is the way we harness in Claude Code AI.

---

## Drift Gate

Before writing code that implements a spec section, the AI MUST:

1. **Quote the spec section** it is implementing
2. **Follow the spec mechanism**, not invent a shortcut — same output via a different path = drift
3. **Ask** if the spec is unclear — never guess
4. **Log a new drift point** before deviating from spec

---

## Session Checklist — 11 Drift Points

Every session ends by checking these 11 areas where AI-generated code is known to drift from spec. Each cites the spec section it guards.

### 1. Input = Output?
**Spec:** [BBC §2.2.1](BOMBasedCompilation.md) count invariant — SUM(non-PHANTOM qty) = output count.

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
**Spec:** [BBC §2](BOMBasedCompilation.md) Gospel Principle — every element traces to IFC extraction or BOM template. Nothing invented.

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
| `component_library.db` | Product geometry, meshes, materials, orientation | Extraction + curation |
| `ERP.db` | Validation rules — fire protection, MEP spacing, jurisdiction | [DocValidate](DocValidate.md) rule packs |
| `*.bimcobol` | Verb recipes: PLACE BOM, ROUTE, WIRE, TRIM | [BIM COBOL](BIM_COBOL.md) scripts |
| `BIMConstants.java` | Dimensional defaults (ceiling height, slab thickness) | Code constants |

**Note:** `classify_*.yaml` is how the test harness represents a C_Order — it seeds the order and BOM for Rosetta Stone verification. In production, the [BIM Designer](BIM_Designer_UserGuide.md) creates the C_Order interactively via [BOM Drop](BOMBasedCompilation.md#34-bom-drop).

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

### 8. Visual Fidelity?
**Spec:** [TestArchitecture](TestArchitecture.md) C8 (geometry diversity) + C9 (axis dimensions) + P06 (overlap).

Three independent proofs triangulate visual correctness: per-instance geometry resolution (C8), axis dimension matching within 1mm (C9), and no same-class element overlaps (P06). [BIMEyes](EYES_SRS.md) adds ~14 per-element geometric proofs.

**Verdict:** PASS.

### 9. Orientation?
**Spec:** [BBC §4](BOMBasedCompilation.md) — rotation is a property of the BOM line, not invented at compile time.

Doors and windows must face the correct direction. `host_element_ref` links each opening to its host wall. Orientation data seeded from extraction (M16/M17 SQL).

**Verdict:** PASS — W-ROT witness proven.

### 10. Who Checks the Tests?
**Spec:** [TestArchitecture](TestArchitecture.md) §Anti-Drift — no silent re-seal, no weakened assertions.

The tamper seal (SHA256 of 73 critical files) detects if a test was weakened to make it pass. Every seal change requires a full git diff review. T18-T20 tamper rules cross-validate. C8/C9 provide independent arithmetic verification that tests didn't drift.

**Verdict:** PASS — Seal v42 (73 files).

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
| DX MIRROR | G2 volume -0.16%, 87 axis swaps (width↔depth in some walls) | S25 | MIRROR verb is incomplete — tracked in [ACTION_ROADMAP](ACTION_ROADMAP.md) CP-2 |
| TE CLUSTER | 1015/48428 elements differ by 1mm at float rounding boundary | S39c | CLUSTER uses ±10% tolerance for semi-regular grids — by design |

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
| **WARN** | Non-fatal anomalies (non-zero origins, assembly stubs) |
| **FINE** | Per-verb detail — which verbs fire, element counts, expansion results |

Every drift point is diagnosable from log output alone — no viewer needed:

| Log message | What it proves |
|-------------|---------------|
| `[verb] ARC/Ground Floor: 12 verb patterns...` | Verb detection ran per-discipline |
| `=== BOM QA Validation ===` | 9 BomValidator checks + 2 pre-flight guards ran |
| `[PASS] Count reconciliation` | SUM(qty) matches extraction count |
| `── Verb Expansion Fidelity ──` | Round-trip centroid diff vs extraction |
| `[VIOLATED] P06...` / `[PROVEN] P06...` | Per-element overlap proof with names + volume |

```bash
grep VIOLATED logs/pipeline_*.log    # Find any proof failures
```
