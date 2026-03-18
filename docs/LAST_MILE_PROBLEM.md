# The Last Mile Problem: Six Honest Gaps

**Date:** 2026-03-17
**Previous version:** `docs/archive/LAST_MILE_PROBLEM.md` (2026-02-20)

---

## DRIFT GATE

I MUST quote the spec section before writing code that implements it.
I MUST NOT take a shortcut that produces correct output via a different
mechanism than the spec describes. Same output, wrong mechanism = drift.
I MUST NOT skip or bypass a spec requirement to make progress. If a spec
says X is needed (e.g. BUFFER lines, tack i/o, ESLines), I MUST implement
X — not omit it because the tests pass without it.
If I cannot implement what the spec describes, I MUST ask the user.
If I find myself writing code that deviates from or omits what the spec
describes, I MUST add a new Gap to this file BEFORE proceeding.

## Session Checklist (verify every session)

1. [ ] **Input = Output?** Does every element in `input/` extracted DB appear in
       `output/` with the same position, spatial relationship, size, and material?
2. [ ] **LOD400 geometry?** Are all objects using real meshes from `component_library.db`
       with correct materials — or are some falling back to plain bounding boxes?
3. [ ] **Compiler only?** Does the compiler work only from declared specs + `{PREFIX}_BOM.db` 
       to produce `output/` — or does it peek at the reference input and cheat?
4. [ ] **Openings and furniture correct?** Are doors/windows positioned and rotated
       correctly in their host walls? Is furniture arranged correctly in rooms?
5. [ ] **Spec fidelity?** Is the output DB dictated solely by the declared spec. *_BOM.db is all recipes, not output instances.
       sources (YAML, DSL, bimcobol, BIMConstants, authority data, library)?
6. [ ] **Output path?** Is the output having both _enbloc and walkthru similar compile but in different approach to BOM stacking?
7. [ ] **Separate from input?** Is there reference to input/ DB, invention of data, intercept, fixing by manual or AI agents during compilation to falsely return success?
8. [ ] **Visual Fidelity?** Is the spatial geometry correctness testing fine enough that even a chair clashing into a table or a door at wrong place or wrong facing is detected?
9. [ ] **Who checks the tests?** Are the tests themselves fooling us? 

---

The verification system overstates its coverage. These 6 gaps are places where the
system declares PASS without proving what it claims.

---

## Gap 1: Reference vs Output Comparison Is Superficial

| Gate | What It Compares | What It Misses |
|------|-----------------|----------------|
| G1-COUNT | `COUNT(*)` — single integer | Which elements differ |
| G2-VOLUME | Sum of AABB volumes — single float | Per-element volume changes |
| G3-DIGEST | SHA-256 of sorted coords — pass/fail | Which element drifted, by how much |
| G5-PROVENANCE | geometry_hash exists in library | Whether it's the CORRECT hash for that element |

SpotCheckContractTest does per-element checks — but only 5 elements (SH: 9%, DX: 0.45%).

**Evidence:** `RosettaStoneGateTest.java:540-552` (G1/G2 aggregate), `:163-178` (G3 opaque failure)

---

## Gap 2: Digest Detects Drift But Cannot Diagnose It

`SpatialDigest` hashes every element's AABB into one SHA-256. Any coordinate change
breaks the hash — good for detection. But when G3 fails, it reports only per-class
counts, not which element moved or by how much. No tolerance band: 0.001mm drift =
same FAIL as a 5-meter misplacement.

**Need:** Per-element diff report: `"IfcDoor Door_D1:23 — maxY off by 50mm"`.

**Evidence:** `SpatialDigest.java:63-119` (hash formula), `RosettaStoneGateTest.java:163-178` (class-only failure)

---

## Gap 3: Doors Misplaced, Openings Use Fallback BBoxes

**3a. Silent parametric fallback:** When `MeshBinder.bind()` throws
`DimensionalContractViolation`, `BuildingWriter` falls back to `bindParametric()`
— a plain 8-vertex bounding box with `GEO_` hash prefix.

> **RESOLVED (R2):** G5-PROVENANCE now FAILs on GEO_ hashes within GATE_SCOPE (RE_SH, RE_DX).
> No fallback bbox reaches disk without detection.

**3b. Rotation heuristic:** `MeshBinder.java:84-97` computes rotation by comparing mesh
X-extent to AABB width vs depth.

> **RESOLVED (R3):** `RotationContractTest` (W-ROT-1/2) verifies every IfcDoor/IfcWindow
> in SH/DX output has matching width/depth alignment vs reference. A 90° swap is caught.

**3c. Furniture arrangement:** PlacementProver checks non-overlap.

> **RESOLVED (R5):** P05 (duplicate position) and P06 (same-class overlap) promoted
> from advisory to critical. Pipeline FAILs on violations.

**Evidence:** `RotationContractTest.java` (W-ROT), `BuildingWriter.java:1060-1066` (fallback), `MeshBinder.java:84-97` (heuristic)

---

## Gap 4: What Are the Sole Specs That Dictate the Output?

The compiler does NOT open the reference IFC or its extracted DB during compilation
(verified). The BOM stores parent-relative offsets, not absolute coordinates (verified).

The output is dictated by these spec sources and no others:

| Source | Phase | What it dictates |
|--------|-------|------------------|
| `classify_*.yaml` | 1 (BOM creation) | Storeys, scope spaces, static children, composition, products |
| `dsl_*.bim` | 2 (compilation) | Building definition: grid, rooms, openings, roof, construction system |
| `*.bimcobol` | 2 (post-compile) | Verb recipes: PLACE BOM, ROUTE SPRINKLERS, WIRE LIGHTING |
| `BIMConstants.java` | 2 | Dimensional defaults: wall thickness, slab overlap, door/window sizes |
| `authority_data.db` | 2 | Rule tables: fire protection, MEP, placement rules, BOM quantity formulas |
| `component_library.db` | 1+2 | Product catalog, geometry meshes, orientation |
| Reference extraction DB | 1 | Element positions, dimensions, geometry hashes (input data, not spec) |

**R4 status:** Spec inventory confirmed by code audit (2026-03-16). The compiler
reads no other source of specs. See `docs/YAMLGuide.md` §YAML Fidelity Mantra
for the mutation-based proof approach (test that changing a YAML value changes output).

**Transitional debt:** ~~`BOMWalker.java:162-164` reads `MProduct.get(bomConn, ...)` from
the BOM DB copy.~~ **RESOLVED (R7):** BOMWalker now reads M_Product from
`compConn` (component_library.db). 4 production call sites updated. Deprecated
single-arg constructor retained for tests using single in-memory DB.

---

## Gap 5: No WYSIWYG Totality Proof

No test opens input and output side by side and confirms every element matches by
identity — same element_ref, same AABB, same geometry_hash, same material. The gates
prove counts and aggregates, not per-element visual identity.

**Feasibility confirmed (2026-03-16):**
- `element_ref` is a stable join key: SH 55/55, DX 614/639, TE 1297/1297 leaf lines have non-NULL element_ref
- `MetadataIntegrityTest` already uses ATTACH DATABASE for multi-DB joins
- `SpotCheckContractTest` already opens ref + output with AABB tolerance matching
- ~~**Blocker:** output DB `elements_meta` has no `element_ref` column~~
  **RESOLVED (R6):** `element_ref` propagated through pipeline. `TotalityContractTest` verifies SH/DX.
  **Remaining:** TE has no totality test (CO mode, 48K elements — aggregate gates only).

**Evidence:** `TotalityContractTest.java` (W-TOT-1/2/3 for SH/DX), `ExtractedBOMWalkTest.java` (count only for TE)

---

## Gap 6: Verb Pattern Fidelity — Non-Uniform Spacing Accepted as Uniform

`VerbDetector.detectRoute()` chains elements by checking the **constant axis** stays
within tolerance (`countAxisRun`), but does NOT verify **uniform step** on the varying
axis. A group of 5 pipe fittings at X = [90.8, 103.6, 108.3, 133.8, 138.5] gets
accepted as `ROUTE:X:11.9:5` (average step 11.9m) even though actual spacing varies
from 4.7m to 25.5m. The verb expansion then places elements at uniform intervals,
diverging by up to 7m from extraction centroids.

**Impact:** Compiled positions for verb-expanded elements may not match extraction
positions. This drift is invisible to gates — G1 (count) matches, G2 (volume sum)
matches, G3 (digest) is skipped for TE. The verb fidelity check (step 9b) catches
it but is advisory-only, and its numbers mix real errors with the now-fixed grouping
key bug.

**Scale (TE):** ROUTE 34,139 instances (avg ~7m error after grouping fix),
SPRAY 13,336 instances (avg ~25m — grid approximation). TILE 12 instances = PASS (0.0m).

**Root cause:** `VerbDetector.countAxisRun()` at line 386 only checks `constAxis`
within tolerance, not `varyAxis` uniformity. Fix: add step-uniformity check — reject
groups where `max_step / min_step > 1.5` (or similar threshold).

**Evidence:** `VerbDetector.java:386-396` (countAxisRun), `BomValidator.java:800-818` (expandRoute)

---

## Gap 7: Extraction Instance Data Leaked Into Product Catalog

**Discovered 2026-03-18.** `ExtractionPopulator.java` copies 49,582 spatial placement
rows (`I_Element_Extraction`) from per-building extraction DBs into `component_library.db`
— a product catalog that should contain only products, images, and geometry. This violates
the architecture: library = IKEA catalog, `{PREFIX}_BOM.db` = spatial recipe.

**Three drift consequences:**

1. **Compiler reads extraction at compile time.** `PlacementLoader.java:206` queries
   `SELECT MIN(min_x), MIN(min_y), MIN(min_z) FROM I_Element_Extraction` to get world
   origin — because `DisciplineBomBuilder.java:263` hardcodes `0.0, 0.0, 0.0` for
   `m_bom.origin_x/y/z`. The BOM has the columns but never fills them.

2. **Hardcoded building names.** `PlacementProver.java:1651-1652` dispatches on literal
   `"Ifc4_SampleHouse"` / `"Ifc2x3_Duplex"` strings. Buildings are data, not code.

3. **Deprecated extraction query in compiler.** `ComponentLibrary.java:479` has a
   `SELECT COUNT(*) FROM I_Element_Extraction` in a `@Deprecated` method still present.

**Root cause:** `ExtractionPopulator` replaced `placement_extractor.py` (which also wrote
to component_library.db). The destination was never questioned — only the mechanism changed.
Then `PlacementLoader` was written to read the origin from wherever the data landed.
Circular dependency: BOM doesn't store origin → compiler reads extraction → extraction
stays in library because compiler needs it.

**Proactive detection (T18-T20):** Three tamper rules added to G4-TAMPER catch these at
the source code level. Initially 10 violations; **all fixed, 0 remaining.**

| Rule | What | Hits | Status |
|------|------|------|--------|
| T18 | `I_Element_Extraction` in DAGCompiler source | 6 → 0 | FIXED (R11/R12/R15) |
| T19 | Hardcoded building name in production code | 2 → 0 | FIXED (R14) |
| T20 | Hardcoded zero origin in BOM INSERT | 2 → 0 | FIXED (R11) |

**Fix path (all DONE — R11-R15):**
- R11: DONE — origin stored in m_bom from measured allMinX/Y/Z
- R12: DONE — PlacementLoader reads origin from BOM, loadWorldOrigin() deleted
- R13: DONE — ExtractionPopulator returns in-memory list, ExtractionReader DB methods removed, EXPECTED_ELEMENTS stored in ad_sysconfig
- R14: DONE — PlacementProver.detectBuildingName() emptied, proveFromDB(path, name) added
- R15: DONE — ComponentLibrary rank-based I_Element_Extraction subquery removed

**Evidence:** `RosettaStoneGateTest.java` T18/T19/T20 rules, `PlacementLoader.java:203-218`,
`DisciplineBomBuilder.java:263`, `ExtractionPopulator.java:27-31` (data chain comment)

---

## Actions

| # | Action | Addresses | Status |
|---|--------|-----------|--------|
| R1 | `SpatialDiff` per-element diff report | Gap 1 + 2 | DONE — wired into G3 failure path |
| R2 | GEO_ fallback = FAIL in G5 | Gap 3a | DONE for SH/DX (GATE_SCOPE) |
| R3 | `RotationContractTest` — W/D alignment | Gap 3b | DONE — W-ROT-1/2 for SH/DX |
| R4 | Assert output traceable to declared spec sources only | Gap 4 | CONFIRMED — 7 sources audited, no others found |
| R5 | Promote PlacementProver advisory → gating | Gap 3c | DONE — P05, P06 promoted to critical |
| R6 | `TotalityContractTest` — per-element AABB | Gap 5 | DONE — W-TOT-1/2/3 for SH/DX |
| R7 | BOMWalker reads M_Product from library | Gap 4 debt | DONE — compConn constructor, 4 production call sites |
| R8 | Verb step-uniformity check in VerbDetector | Gap 6 | DONE — `isUniformRun()` ±20% tolerance, ROUTE 34K→533 |
| R9 | Fidelity check grouping key fix | Gap 6 | DONE — storey\|discipline\|product (was storey\|product) |
| R10 | Promote verb fidelity from advisory to gating | Gap 6 | DONE — exact verbs (TILE, FRAME) gate at ≤5mm; approximate (ROUTE, SPRAY) SKIP |
| R11 | Store world origin in m_bom.origin_x/y/z | Gap 7 | DONE — allMinX/Y/Z passed to insertBomHeader, both builders |
| R12 | PlacementLoader reads origin from BOM, not extraction | Gap 7 | DONE — reads m_bom.origin_x/y/z, loadWorldOrigin() deleted |
| R13 | Remove I_Element_Extraction from component_library.db | Gap 7 | DONE — ExtractionPopulator returns in-memory, ExtractionReader DB methods removed, EXPECTED_ELEMENTS in ad_sysconfig |
| R14 | Remove hardcoded building names from PlacementProver | Gap 7 | DONE — detectBuildingName emptied, proveFromDB(path,name) added |
| R15 | Delete deprecated ComponentLibrary rank-based extraction query | Gap 7 | DONE — subquery removed, comment updated |

---

## How to See It Working — Pipeline Debug Messages

The pipeline emits structured console output that proves each gap-closing mechanism ran.
A developer watching the build output sees the evidence directly:

| Message | Source | Proves |
|---------|--------|--------|
| `[verb] ARC/Ground Floor: 12 verb patterns (847 instances), 3 unfactored` | `DisciplineBomBuilder.java:227` | Verb detection ran per-discipline (R8/R9) |
| `=== BOM QA Validation ===` | `BomValidator.java:68` | 9 check methods + 2 pre-flight guards executed |
| `[PASS] Count reconciliation ...` | `BomValidator.report()` | SUM(qty) matches extraction (Gap 1 coverage) |
| `── Verb Pattern Compliance ──` | `BomValidator.printComplianceReport()` | Compression ratio, per-verb/per-discipline breakdown |
| `── Verb Expansion Fidelity ──` | `BomValidator.checkVerbExpansionFidelity()` | Round-trip centroid diff — verb → expand → compare vs extraction (Gap 6) |
| `[SKIP] COMPILE` | `CompilationPipeline.java:757` | CO mode bypass logged, not silent |
| `[QA] All checks PASSED` / `[QA] N check(s) FAILED` | `BomValidator.java:88-90` | Gate verdict before commit |

**Cross-reference:** `docs/SourceCodeGuide.md` — Chapter 6 (9-Stage Pipeline, stage logging),
Chapter 1 §Examining `{PREFIX}_BOM.db` (SQL queries that verify the same data from outside),
Appendix A §Step 2.2 (pipeline stage progression).

---

## The Challenge Mantra (recall every session)

> **The viewer is a confirmation tool, not a discovery tool. You open it to see
> what you've already proven, not to find what might be wrong.**
>
> Progress: R1-R15 ALL DONE. Per-element identity verified for SH/DX (R6).
> Rotation tested (R3). Parametric fallback gated (R2). BOMWalker reads from
> master catalog (R7). Fidelity grouping key fixed (R9). ROUTE step-uniformity
> enforced (R8) — non-uniform groups rejected, ROUTE 34K→533. Verb fidelity
> promoted to gating (R10) — exact verbs (TILE, FRAME) block pipeline at >5mm.
> World origin stored in BOM (R11), compiler no longer reads extraction (R12).
> Hardcoded building names removed (R14). T18-T20 tamper rules guard proactively.
>
> **CRITICAL (discovered 2026-03-18 by TerminalSandboxTest):**
> TE output positions are **systematically wrong** — shifted ~160m from extraction.
> Both ENBLOC and WALKTHRU produce identical wrong coordinates. Root cause:
> `PlacementCollectorVisitor.onSubAssembly()` line 166-170 adds BOTH `line.dx`
> AND `childBom.origin_x/y/z` at each tree level. For TE, R11 populated world
> positions in ALL child BOMs, causing double-counting. SH is unaffected because
> child BOMs have origin=(0,0,0). This was invisible because G3 was SKIP for TE
> and no per-element position test existed.
>
> **Fix required:** The coordinate accumulation formula in PlacementCollectorVisitor
> must be corrected. The childBom.origin is absolute (world position), not relative.
> Using it AND line.dx (parent-relative offset) double-counts. But line.dx is NOT
> always childOrigin-parentOrigin (FLOOR→DISCIPLINE has dx=0 with 3.4m origin delta).
> The coordinate model needs analysis: is the anchor absolute or cumulative?
>
> **Remaining drift vectors:** ROUTE inter-leg position (533 instances, avg 295m),
> SPRAY grid approximation (46,712 instances, avg 23m — inherent to semi-regular).
>
> **TE coverage gaps (2026-03-18):** G3/G6/Totality/Rotation tests added to scope
> but cannot pass until coordinate bug is fixed. TerminalSandboxTest exercises
> the round-trip maths and proves the bug.
>
> **Each session:** read the checklist above. Check each box. Do not claim PASS
> on something the gates cannot actually prove.
