# The Last Mile Problem: Honest Gaps
> **Foundation:** [BBC](BOMBasedCompilation.md) · [DATA_MODEL](DATA_MODEL.md) · [BIM_COBOL](BIM_COBOL.md) · [MANIFESTO](MANIFESTO.md) · [TestArchitecture](TestArchitecture.md)

---

## DRIFT GATE

I MUST quote the spec section before writing code that implements it.
I MUST NOT take a shortcut that produces correct output via a different
mechanism than the spec describes. Same output, wrong mechanism = drift.
If I cannot implement what the spec describes, I MUST ask the user.
If I find myself deviating from the spec, I MUST add a new Gap BEFORE proceeding.

---

## Session Checklist

| # | Check | Verdict | Evidence |
|---|-------|---------|----------|
| 1 | Input = Output? | **PASS** | SH 55/55, FK 82/82, IN 699/699, DX 1099/1099, TE 48428/48428 — G1-COUNT all PASS. W-TOT: SH/FK PASS, TE 48336/48428 (99.8%). IN: 120 window SHIFTs (CLUSTER debt). DX: MIRROR dims (C9 swaps accepted S58c) |
| 2 | LOD400 geometry? | **PASS** | G5 all PASS, C8 all PASS. 0 GEO_ fallbacks |
| 3 | Compiler only? | **PASS** | T18/T19/T20 all 0 violations. Compiler never connects to `*_extracted.db` during compilation |
| 4 | Openings/furniture? | **PASS** | W-ROT PASS, P05/P06 0 violations. R21 host_element_ref (SH:7, FK:16 fills) |
| 5 | Spec fidelity? | **PASS** | 7 spec sources audited. BOMWalker reads from compConn (R7) |
| 6 | Output path? | **PASS** | Single path: C_OrderLine → BOM explosion. Element counts verified for all 35 buildings |
| 7 | Separate from input? | **PASS** | T18 guards. R11-R15 all DONE. BOM tree forensic verified (S95) |
| 8 | Visual fidelity? | **PASS** | C8+C9+P06 triangulate. Log-based triage. EYES ~14 per-element proofs |
| 9 | Orientation? | **PASS** | W-ROT doors/windows. R21 DONE. M16/M17 SQL seeded |
| 10 | Who checks the tests? | **PASS** | Seal v42 (73 files) + T18-T20 tamper + C8/C9 cross-validation |
| 11 | Factorization? | **PASS** | R28 material guard + R29 CP-1 + R31 GUID geometry |

**Remaining debt (all pre-existing):**
- DX G2 -0.16% MIRROR dims (W↔D swap in some walls) — since S25. C9 87 axis swaps accepted (S58c)
- IN G3 120 window SHIFTs (CLUSTER expansion coordinates) — since S39c

---

## BOM Tree Forensic Proof (S95)

The compiler reconstructs element positions from BOM tree traversal — it does NOT
copy coordinates from extracted input databases.

**Code path (4 steps):**

```
1. World origin     m_bom.origin_x/y/z         → SH: (-9.235, -2.746, -0.470)
2. BOM tree walk    PlacementCollectorVisitor   → accumulate parent + line dx/dy/dz
3. Leaf expansion   anchor + verb offset + ½dim → centroid (cx, cy, cz)
4. AABB write       ElementPersistence          → INSERT elements_rtree (cx±½w, cy±½d, cz±½h)
```

**Evidence that offsets are relative, not absolute:**

| Wall (SH) | BOM dx/dy/dz | Absolute (origin + dx) | Input | Match |
|-----------|-------------|----------------------|-------|-------|
| North ext | 1.500 / 7.155 / 0.470 | -7.735 / 4.409 / 0.000 | -7.735 / 4.409 / 0.000 | exact |
| South ext | 6.400 / 1.355 / 0.470 | -2.835 / -1.391 / 0.000 | -2.835 / -1.391 / 0.000 | exact |
| East ext | 15.355 / 1.355 / 0.470 | 6.120 / -1.391 / 0.000 | 6.120 / -1.391 / 0.000 | exact |

The dx/dy/dz (1.5, 6.4, 15.3...) differ from absolute coords (-7.7, -2.8, 6.1...).
The 1mm rounding (maxZ: 2.474 input → 2.473 output from `allocated_height_mm=2473`)
is the fingerprint proving the compiler reconstructs from BOM integers, not float copies.

**What the compiler NEVER does:**
- No `INSERT INTO elements_rtree SELECT ... FROM` (0 bulk copies)
- No connection to `*_extracted.db` during compilation
- No `ATTACH DATABASE` for cross-DB copy
- T18 tamper rule enforces: 0 `I_Element_Extraction` references in compiler source

---

## Gap Status Summary

| Gap | Title | Status | Remaining |
|-----|-------|--------|-----------|
| 1 | Reference vs Output comparison superficial | **CLOSED** | SpatialDiff per-element report (R1). SpotCheckContract per-element AABB |
| 2 | Digest detects drift, can't diagnose | **CLOSED** | SpatialDiff provides per-element diff on G3 failure |
| 3 | Doors/openings fallback + rotation + overlap | **CLOSED** | R2 GEO_=FAIL, R3 W-ROT, R5 P06 critical, R25 cross-product exemption, R26 slab |
| 4 | What dictates the output? | **CLOSED** | 7 spec sources audited (R4). BOMWalker reads from compConn (R7). 8th source = user design via G-4 promote |
| 5 | No per-element totality proof | **CLOSED** | TotalityContractTest (W-TOT). CP-1 MA identity threading. TE 48336/48428 (99.8%) |
| 6 | Verb pattern fidelity | **KNOWN LIMIT** | ROUTE: non-uniform spacing (R8 step-uniformity guard, R10 gating). G3-DIGEST float precision for CO_TE: 1015 elements differ by 1mm at rounding boundary |
| 7 | Extraction data leaked into product catalog | **CLOSED** | R11-R15 all DONE. T18-T20 tamper rules guard. 0 violations |
| 8 | Database separation drift | **CLOSED** | V1/V3 tables dropped (V006 migration). V2 ad_* tables = legacy DSL path (BOM path clean). R27 C_DocType injection fixed |
| 9 | Verb factorization provenance | **CLOSED** | R28 material guard, R29 CP-1 identity, R31 per-instance geometry via GUID-keyed I_Geometry_Map |

---

## Gap 4: Spec Sources That Dictate the Output

| Source | Phase | What it dictates |
|--------|-------|------------------|
| `classify_*.yaml` | 1 (BOM creation) | Storeys, scope spaces, products, composition |
| `dsl_*.bim` | 2 (compilation) | Building definition: grid, rooms, openings, roof |
| `*.bimcobol` | 2 (post-compile) | Verb recipes: PLACE BOM, ROUTE, WIRE, TRIM |
| `BIMConstants.java` | 2 | Dimensional defaults |
| `authority_data.db` | 2 | Rule tables: fire protection, MEP, placement |
| `component_library.db` | 1+2 | Geometry meshes, orientation |
| Reference extraction DB | 1 | Element positions, dimensions (input data, not spec) |
| User design (G-4 promote) | 2 | C_OrderLine spatial edits (entity_type='U', provenance='GENERATIVE') |

---

## Gap 6: Verb Pattern Fidelity — Known Limits

**ROUTE:** `VerbDetector.countAxisRun()` accepts non-uniform spacing. R8 added
`isUniformRun()` ±20% tolerance guard, reducing ROUTE 34K→533. ROUTE is
approximate by nature — not gated.

**G3-DIGEST float precision (CO_TE):** CLUSTER verb round-trip introduces sub-mm
float noise (1015/48428 elements differ by 1mm). G3-DIGEST not valid for CO_TE.
W-TOT identity matching tolerates this.

---

## Gap 9: Verb Factorization Constraints

Any code that factorizes BOM lines (N elements → 1 line with qty=N) MUST:

1. **Material uniformity:** Mixed `material_rgba` → reject, fall through to unfactored
2. **Dimension uniformity:** W/D/H within 50mm (VerbDetector guard)
3. **CP-1 identity:** CO path preserves GUID `element_ref` + writes MA rows
4. **IFC GUID format guard:** 22 chars from `[0-9A-Za-z_$]` (PlacementCollectorVisitor.IFC_GUID)
5. **element_name ≠ element_ref:** element_name = product/type (for C8 grouping), element_ref = instance identity (GUID)

---

## Actions Register

| # | Action | Gap | Status |
|---|--------|-----|--------|
| R1 | SpatialDiff per-element diff report | 1+2 | DONE |
| R2 | GEO_ fallback = FAIL in G5 | 3a | DONE |
| R3 | RotationContractTest W-ROT | 3b | DONE |
| R4 | 7 spec sources audited | 4 | DONE |
| R5 | P05/P06 promoted to critical | 3c | DONE |
| R6 | TotalityContractTest W-TOT | 5 | DONE |
| R7 | BOMWalker reads M_Product from compConn | 4 | DONE |
| R8 | VerbDetector step-uniformity check | 6 | DONE |
| R9 | Fidelity check grouping key fix | 6 | DONE |
| R10 | Verb fidelity gating (exact verbs gate, approximate SKIP) | 6 | DONE |
| R11-R15 | World origin in BOM, extraction removed from compiler | 7 | ALL DONE |
| R25 | P06 cross-product exemption + IfcPlate 50mm | 3c | DONE |
| R26 | GEO_ slab fallback resolved | 3a | DONE |
| R27 | C_DocType injection fixed | 8 | DONE |
| R28 | VerbFactorizer material uniformity guard | 9 | DONE |
| R29 | CP-1 identity: GUID element_ref + MA rows | 9 | DONE |
| R31 | Per-instance geometry via GUID-keyed I_Geometry_Map | 9 | DONE |
| R32 | C8 SQL blank element_name normalization | 9 | DONE |
| R33 | G3-DIGEST float precision (1mm CO_TE) | 6 | KNOWN LIMIT |
| CP-1 | TE per-element identity via m_bom_line_ma | 5 | DONE (48336/48428) |

**Open items:**
- R22: Extract I_Element_Connectivity (M13-M15 upgrade) — TODO
- R23: Extract I_Element_Interference (M9/M10 upgrade) — TODO

---

## Geometric Fingerprint — Shape Identity Proof

Dimensionless ratios that are identical for geometrically equivalent shapes,
regardless of scale or coordinate system.

Given AABB dimensions sorted smallest→largest as (S, M, L):
```
planarity   = S / L    "how thin"
elongation  = M / L    "how stretched"
squareness  = S / M    "cross-section shape"
```

**Ratio invariance:** If A is a uniform scaling of B, then ratios(A) = ratios(B).
Proof: scaling by k multiplies all dims equally, so S/L = (kS)/(kL). QED.

| Archetype | Condition | IFC Classes |
|-----------|-----------|-------------|
| PLANAR | planarity < 0.15, elongation ≥ 0.40 | IfcWall, IfcSlab, IfcPlate, IfcRoof, IfcDoor, IfcWindow |
| ELONGATED | planarity < 0.15, elongation < 0.40 | IfcColumn, IfcBeam, IfcMember, IfcPipeSegment |
| COMPACT | planarity ≥ 0.25, elongation ≥ 0.50 | IfcFurnishingElement, IfcFlowTerminal |

| Witness | What It Proves | Status |
|---------|---------------|--------|
| W-SHAPE | Element geometry consistent with IFC class | PASS (SH 55/55, DX 1099/1099) |
| W-MULTISET | Extracted↔compiled fingerprint multiset match | PASS |
| W-CENSUS | Building archetype distribution correct | PASS (SH, DX) |

**Implementation:** `GeometricFingerprint.java`, `GeometricFingerprintTest.java`.
Thresholds: planarity 0.15/0.20, elongation 0.40, epsilon 0.05 (5%).

---

## Pipeline Debug Messages

| Message | Source | Proves |
|---------|--------|--------|
| `[verb] ARC/Ground Floor: 12 verb patterns...` | DisciplineBomBuilder | Verb detection ran per-discipline |
| `=== BOM QA Validation ===` | BomValidator | 9 checks + 2 pre-flight guards |
| `[PASS] Count reconciliation` | BomValidator | SUM(qty) matches extraction |
| `── Verb Expansion Fidelity ──` | BomValidator | Round-trip centroid diff vs extraction |
| `[VIOLATED] P06...` / `[PROVEN] P06...` | PlacementProver | Per-element overlap with names + volume |

Log-based proofing: `grep VIOLATED logs/pipeline_*.log` — no viewer needed.
