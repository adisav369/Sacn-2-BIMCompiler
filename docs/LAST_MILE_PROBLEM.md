# The Last Mile Problem: Six Honest Gaps
> **Foundation:** [BBC](BOMBasedCompilation.md) · [DATA_MODEL](DATA_MODEL.md) · [BIM_COBOL](BIM_COBOL.md) · [ConstructionAsERP](ConstructionAsERP.md) · [TestArchitecture](TestArchitecture.md)

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
   **Gap identified (session 27):** G3 cross-mode excludes geometry_hash. Reference has per-instance mesh variation (e.g., SH doors: 2 unique hashes) but output collapses to 1. Fix: C8 geometry diversity contract (TestArchitecture.md). Also: C9 per-axis dimension match catches W↔D swaps that volume checks miss.
   **Log-based triage (session 28):** P06 violations now include element names + overlap volumes in BIMLogger FINE output. Triage via `grep VIOLATED logs/pipeline_*.log` — no viewer needed. Known false positives: IfcFurnishingElement (chairs at tables), IfcPlate curtain wall corners. See P06 Exemption Spec under Gap 3c.
9. [ ] **Orientation fidelity?** Does each placed element respect its `component_definitions` orientation metadata (`attachment_face`, `up_axis`, `forward_axis`)? A door AABB at the right position but facing the wrong way (inward vs outward) is a drift that AABB checks alone cannot catch. W-ROT catches 90° swaps; `face_anchor`/`swing_side` ASI catches facing direction (M16/M17 when implemented).
   **Gap identified (session 27):** Mesh centroid offset relative to AABB centre is an orientation fingerprint that works cross-mode. Fix: C10 mesh centroid fingerprint (TestArchitecture.md). Uses existing P22 vertex blob infrastructure.
10. [ ] **Who checks the tests?** Are the tests themselves fooling us?
11. [ ] **Factorization preserves provenance?** When verb factorization compresses N elements
       into 1 BOM line (qty=N), does the factored line preserve per-instance data? Check:
       material_rgba (all elements same?), dimensions (W/D/H within 50mm?), CP-1 identity
       (GUID element_ref + MA rows for CO path?). A factored line that stores only the
       first element's material loses (N-1) materials. Gap 9 guards against this.

---

The verification system overstates its coverage. These 6 gaps are places where the
system declares PASS without proving what it claims.

---

## Gap 1: Reference vs Output Comparison Is Superficial
<!-- @Traces BBC.md §2.1.6 — count invariant, BBC.md §7 — verification gates -->

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
<!-- @Traces BBC.md §4.1 — world coordinate reconstruction (spatial digest) -->

`SpatialDigest` hashes every element's AABB into one SHA-256. Any coordinate change
breaks the hash — good for detection. But when G3 fails, it reports only per-class
counts, not which element moved or by how much. No tolerance band: 0.001mm drift =
same FAIL as a 5-meter misplacement.

**Need:** Per-element diff report: `"IfcDoor Door_D1:23 — maxY off by 50mm"`.

**Evidence:** `SpatialDigest.java:63-119` (hash formula), `RosettaStoneGateTest.java:163-178` (class-only failure)

---

## Gap 3: Doors Misplaced, Openings Use Fallback BBoxes
<!-- @Traces BBC.md §2.2.3 — library population, BBC.md §4.0 — tack convention -->

**3a. Silent parametric fallback:** When `MeshBinder.bind()` throws
`DimensionalContractViolation`, `BuildingWriter` falls back to `bindParametric()`
— a plain 8-vertex bounding box with `GEO_` hash prefix.

> **RESOLVED (R2):** G5-PROVENANCE now FAILs on GEO_ hashes within GATE_SCOPE (RE_SH, RE_DX).
> No fallback bbox reaches disk without detection.

**3b. Rotation heuristic:** `MeshBinder.java:84-97` computes rotation by comparing mesh
X-extent to AABB width vs depth.

> **RESOLVED (R3):** `RotationContractTest` (W-ROT-1/2) verifies every IfcDoor/IfcWindow
> in SH/DX output has matching width/depth alignment vs reference. A 90° swap is caught.
> Orientation metadata (`component_definitions.attachment_face`, `up_axis`, `forward_axis`)
> provides intrinsic product orientation for all 23,888 products. DocValidate M16/M17
> validate face-anchor consistency and host association for GENERATIVE buildings.

**3c. Furniture arrangement:** PlacementProver checks non-overlap.

> **RESOLVED (R5):** P05 (duplicate position) and P06 (same-class overlap) promoted
> from advisory to critical. Pipeline FAILs on violations.
>
> **P06 Exemption Spec (session 28):** P06 must distinguish benign overlap
> (BOM composition) from real spatial bugs (merged/misarranged elements).
> Blanket class exemption is too blunt — it hides merge bugs.
>
> **Sharpness principle:** Same IFC class + different product = composition (exempt).
> Same IFC class + same product = potential merge (flag). P05 catches exact centroid
> duplicates; P06 catches near-duplicates where AABBs overlap but centroids differ.
>
> | Category | IFC Class | Same product? | Verdict | Implementation |
> |----------|-----------|---------------|---------|----------------|
> | Furniture composition | `IfcFurnishingElement` | No (chair≠table) | EXEMPT | Cross-product overlap: compare `elementRef` — if different, skip |
> | Furniture merge | `IfcFurnishingElement` | Yes (chair=chair) | FLAG | Same-product overlap: two identical products overlapping = real bug |
> | Beam crossing | `IfcMember` | Either | EXEMPT | Blanket exempt (beams cross at joints by structural design) |
> | Proxy elements | `IfcBuildingElementProxy` | Either | EXEMPT | Blanket exempt (no spatial contract) |
> | Curtain wall corner | `IfcPlate` | Either | EXEMPT if thin | Increase thin-wall tolerance to 50mm (panel thickness ~25mm + margin) |
> | Curtain wall merge | `IfcPlate` | Yes | FLAG if thick | minOverlap ≥ 50mm = genuine plate-into-plate overlap |
>
> **Implementation in `PlacementProver.proveNoSameClassOverlap()`:**
> 1. Keep `IfcMember`, `IfcBuildingElementProxy` in `OVERLAP_EXEMPT_CLASSES` (blanket)
> 2. For `IfcFurnishingElement`: if `a.elementRef() != b.elementRef()` → skip (composition)
> 3. For `IfcPlate`: change thin-wall tolerance from `COVERAGE_TOLERANCE` (10mm) to 50mm
> 4. All other classes: unchanged (flag any overlap > OVERLAP_VOLUME_THRESHOLD)
>
> **Evidence (SH walkthru 2026-03-19):** 7 P06 VIOLATED — 1× glazed panel corner
> junction (vol=0.0017 m³, minOverlap=20mm — cross-panel, same product), 6× dining
> chairs overlapping table AABB (vol=0.069 m³ each — cross-product, different elementRef).
> All diagnosed from log output without visual inspection (BIMLogger FINE-level proofing).
>
> **What this catches that blanket-exempt would miss:**
> - Two identical chairs placed at the same spot (merge/duplicate)
> - Furniture overlapping walls (cross-class, already caught)
> - Sofa inside another sofa (same product, real bug)

**Evidence:** `RotationContractTest.java` (W-ROT), `BuildingWriter.java:1060-1066` (fallback), `MeshBinder.java:84-97` (heuristic)

---

## Gap 4: What Are the Sole Specs That Dictate the Output?
<!-- @Traces BBC.md §2 — Gospel Principle (extract or compile, never invent) -->

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

**8th source — User design (G-4 promote path):** When G-4 Promote writes
C_OrderLine → m_bom, user-modified positions become a new spec source. This
path bypasses the IFCtoBOM pipeline. The promote path must be constrained:
C_OrderLine data must originate solely from BOM templates (sources 1-7 above)
plus user spatial edits (dx/dy/dz adjustments). No external data injection.
G4_SRS §2.4 pre-checks (PlacementValidator + dangle detection) gate this.
Test spec: `promote_createsBomEntries` must verify entity_type='U' and
provenance='GENERATIVE' on all promoted rows — proving origin is tracked.

**Transitional debt:** ~~`BOMWalker.java:162-164` reads `MProduct.get(bomConn, ...)` from
the BOM DB copy.~~ **RESOLVED (R7):** BOMWalker now reads M_Product from
`compConn` (component_library.db). 4 production call sites updated. Deprecated
single-arg constructor retained for tests using single in-memory DB.

---

## Gap 5: No WYSIWYG Totality Proof
<!-- @Traces BBC.md §2.1.6 — recipe vs placement contract (every element accounted) -->

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

**CP-1 (2026-03-20): MA-based identity threading.**
IFC GUIDs from extraction DB are now carried through the BOM via `m_bom_line_ma`
(iDempiere M_InOutLineMA pattern — Material Allocation). SpatialDiff matches
`ref.guid ↔ output.element_ref` when identity data overlaps >25%, falling back
to position matching for SH/DX (which lack extraction GUIDs in output).

| Component | What |
|-----------|------|
| `m_bom_line_ma(bom_id, sequence, qi, guid)` | Per-instance identity table (MA pattern) |
| `ExtractionPopulator` | Reads `m.guid` from reference DB → `ExtractionElement.guid` |
| `DisciplineBomBuilder` | Writes MA rows for factored (qty>1) and unfactored (qty=1) elements |
| `VerbDetector.computeExpansionOrder()` | Maps element→qi by nearest centroid matching |
| `PlacementCollectorVisitor.loadMaGuids()` | Reads MA → uses GUID as element_ref |
| `SpatialDiff.diffByIdentity()` | Hybrid: identity-matched + position fallback for remainder |

**TE result (CP-1):** 48336/48428 exact (99.8%), 0 missing, 0 extra.
Remaining 92 (85 shift + 7 drift) are pre-existing FRAME verb expansion
coordinate mismatches (centroid-vs-LBD offset) — Gap 6 scope, not Gap 5.

**Evidence:** `TotalityContractTest.java` (W-TOT-1/2/3 for SH/DX), `ExtractedBOMWalkTest.java` (count only for TE)

---

## Gap 6: Verb Pattern Fidelity — Non-Uniform Spacing Accepted as Uniform
<!-- @Traces BBC.md §2.1.6 — verb expansion fidelity (TILE/ROUTE/CLUSTER) -->

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
~~SPRAY~~ superseded by CLUSTER (session 32) — 47,607 instances, lossless encoding. TILE 12 instances = PASS (0.0m).

**Root cause:** `VerbDetector.countAxisRun()` at line 386 only checks `constAxis`
within tolerance, not `varyAxis` uniformity. Fix: add step-uniformity check — reject
groups where `max_step / min_step > 1.5` (or similar threshold).

**Evidence:** `VerbDetector.java:386-396` (countAxisRun), `BomValidator.java:800-818` (expandRoute)

---

## Gap 7: Extraction Instance Data Leaked Into Product Catalog
<!-- @Traces BBC.md §2 — Gospel Principle (compiler reads BOM+library, never extraction) -->
<!-- @Traces BBC.md §4.1 — origin convention (only BUILDING has non-zero origin) -->

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
| R10 | Promote verb fidelity from advisory to gating | Gap 6 | DONE — exact verbs (TILE, FRAME) gate at ≤5mm; approximate (ROUTE) SKIP; SPRAY deprecated → CLUSTER |
| R11 | Store world origin in m_bom.origin_x/y/z | Gap 7 | DONE — allMinX/Y/Z passed to insertBomHeader, both builders |
| R12 | PlacementLoader reads origin from BOM, not extraction | Gap 7 | DONE — reads m_bom.origin_x/y/z, loadWorldOrigin() deleted |
| R13 | Remove I_Element_Extraction from component_library.db | Gap 7 | DONE — ExtractionPopulator returns in-memory, ExtractionReader DB methods removed, EXPECTED_ELEMENTS in ad_sysconfig |
| R14 | Remove hardcoded building names from PlacementProver | Gap 7 | DONE — detectBuildingName emptied, proveFromDB(path,name) added |
| R15 | Delete deprecated ComponentLibrary rank-based extraction query | Gap 7 | DONE — subquery removed, comment updated |
| R16 | Coordinate double-counting in PlacementCollectorVisitor | Gap 7 | DONE — child BOM origins zeroed (session 17) |
| R25 | P06 cross-product furniture exemption + IfcPlate 50mm tolerance | Gap 3c | **TODO** — spec written (P06 Exemption Spec above). Sharp: same-product overlap still flagged, only cross-product (composition) exempt |
| R26 | Investigate GEO_ fallback on `SLAB_GROUND FLOOR` (IfcSlab) in SH enbloc | Gap 3a | **TODO** — G5 PROVENANCE fails: 1/55 instances use parametric bbox |
| R27 | C_DocType spec/code drift: spec says it belongs in `{PREFIX}_BOM.db`, but IFCtoBOM doesn't write it — shell script injects into temp compile DB | Gap 8 | **DONE** — IFCtoBOM writes C_DocType + DSLContent during extraction. Shell injection removed. StubDataSeeder kept for unit test in-memory DBs |
| CP-1 | TE per-element identity via `m_bom_line_ma` (M_InOutLineMA pattern) | Gap 5 | **DONE** — 48336/48428 exact, 0 missing/extra. Remaining 92 = FRAME verb coordinate mismatch (Gap 6) |
| R28 | VerbFactorizer material uniformity guard | Gap 9 | **DONE** — reject groups with mixed material_rgba before verb detection. SH G5: 22→4 missing (matches reference) |
| R29 | VerbFactorizer CP-1 contract: unfactored CO lines preserve GUID element_ref + MA rows | Gap 9 | **DONE** — restored `elemRef = guid ?? elementRef` for writeMaRows=true path |
| R30 | ProductGeometry SQL alias fix: `lo` → `cg` after LOD_Object → component_geometries rename | Gap 9 | **DONE** — loadAll() SELECT columns updated |
| R31 | Per-instance geometry via GUID-keyed I_Geometry_Map + MeshBinder override + element_name fix | Gap 9 | **DONE** — TE C8: 29→0 lost, G5: 2→0 GEO_. IFC GUID format guard (`[0-9A-Za-z_$]{22}`) |

---

## Gap 9: Verb Factorization Must Preserve Per-Instance Provenance
<!-- @Traces BBC.md §2.2.3 — library provenance, LAST_MILE checklist #1 (material) -->
<!-- @Traces BBC.md §2.1.6 — CP-1 identity matching (GUID element_ref + MA rows) -->

**Discovered 2026-03-20.** FACTORIZE-v2 refactored verb factorization into `VerbFactorizer`
and extended it to `StructuralBomBuilder` (RE buildings). Three violations found and fixed:

### 9.1 Material provenance loss (R28)

Factored BOM lines store only the first element's `material_rgba`. When elements of the
same product have different materials (e.g., 18 painted + 2 unpainted IfcMember mullions),
the factored line inherits NULL from the first element, losing 18 materials.

**Guard:** `VerbFactorizer.doFactorize()` checks material uniformity within each product
group before calling `VerbDetector.detect()`. Non-uniform groups fall through to unfactored.

**Evidence:** SH IfcMember (20 curtain wall mullions): 18 had `0.969,0.969,0.969,1.000`,
2 had NULL. Factored → all 20 lost material. After guard: 20 unfactored, G5 PASS.

### 9.2 CP-1 identity regression (R29)

`VerbFactorizer` dropped GUID-as-element_ref and MA rows for unfactored CO lines.
Restored: when `writeMaRows=true`, unfactored lines use `guid ?? elementRef` and write
MA row (qi=0). Required for TE SpatialDiff identity matching (CP-1 48336/48428 rate).

### 9.3 Factorization constraints (spec hardening)

**Any code that factorizes BOM lines (N elements → 1 line with qty=N) MUST:**

1. **Material uniformity:** All elements in the group have identical `material_rgba`.
   Mixed materials → reject, fall through to unfactored.
2. **Dimension uniformity:** All elements match W/D/H within 50mm (VerbDetector guard).
3. **CP-1 identity:** CO path must preserve GUID element_ref for unfactored lines
   and write MA rows for all lines (factored + unfactored).
4. **SQL alias consistency:** When renaming tables, update ALL SQL references.
5. **IFC GUID format guard:** MA GUIDs used as element_ref MUST match IFC
   GloballyUniqueId format: exactly 22 characters from `[0-9A-Za-z_$]`.
   Invalid format (e.g., product name leaked into MA) → reject, fall through to
   product-name element_ref. Guard: `PlacementCollectorVisitor.IFC_GUID` pattern.
6. **element_name ≠ element_ref:** Output `elements_meta.element_name` carries the
   product/type name (for C8 grouping). `element_ref` carries instance identity (GUID).
   Never write a GUID as element_name — C8 groups by element_name prefix to measure
   per-instance mesh diversity across elements of the same product type.

### 9.4 Per-instance geometry resolution (C8 diversity — R31)

**Problem:** C8 compares per-instance geometry diversity between reference and output.
Reference IFC models have per-instance mesh variation (e.g., 236 TE windows → 183 unique
geometries). The BOM pipeline resolves geometry per-product via `M_Product_Image` — one
mesh per product type. This collapses 183 → 29 unique meshes. C8 FAIL.

**Fix (three layers):**

1. **ExtractionPopulator.fillGuidGeometryEntries()** — writes GUID-keyed entries to
   `I_Geometry_Map`: `(element_ref=guid, ifc_class, geometry_hash)`. Each IFC instance
   gets its own geometry hash from the extraction DB. 48428 entries for TE.
2. **MeshBinder.bind() Step 1b** — when `element_ref` is an IFC GUID (22 chars, no `:`),
   tries `resolveGeometryByRef(guid, ifcClass)` BEFORE product-level fallback.
   Returns per-instance geometry hash from GUID entry in I_Geometry_Map.
3. **BuildingWriter.writeBoundElement()** — uses `productId` (not `elementRef`) as
   `element_name`. Preserves product-type grouping for C8 comparison.

**Evidence:** TE C8: 29 types lost → 0 diversity losses. G5: 2 GEO_ → 0. All 48428
elements resolve per-instance geometry from library via GUID chain.

---

## Gap 8: Database Separation Drift — Non-Drift Audit (session 21)
<!-- @Traces BBC.md §2.1 — IFC→BOM stage (5-split DB architecture) -->
<!-- @Traces ConstructionAsERP.md §1.4 — DB separation (library/BOM/output/validation/work_output) -->

**Audit date:** 2026-03-18. Four spec claims checked against reality.

### 8.1 `{PREFIX}_BOM.db` — Is it purely BOM relationships?

**CLEAN.** Tables: `m_bom`, `m_bom_line`, `M_Product`, `ad_sysconfig` (+ `C_DocType` in DM_BOM.db).
No extraction data, no geometry BLOBs, no config tables.

**Known debt:** M_Product is a transitional copy from component_library.db (BOMWalker
reads from compConn but ProductRegistrar still copies for assembly stubs). Target:
remove BOM-side M_Product when all consumers use compConn.

### 8.2 `component_library.db` — Is it just LODs + orientation + IFC metadata?

**THREE VIOLATIONS:**

| # | Violation | Rows | Severity | Spec says |
|---|-----------|------|----------|-----------|
| V1 | `I_Element_Extraction` still has 49,582 rows | 49,582 | MEDIUM | R13 removed reader code, but data remains. Benign until a future consumer reads it accidentally. |
| V2 | 60+ `ad_*` config tables (ad_space_type, ad_wall_type, ad_floor_type, ad_building_storey, etc.) | ~34 populated | HIGH | ConstructionAsERP.md §1.1 says component_library.db = "LOD geometry store + product catalog". Config tables are Application Dictionary — they should be in {PREFIX}_BOM.db or a dedicated config.db. |
| V3 | Legacy BOM tables: `ad_bom` (35 rows), `ad_bom_child` (138 rows), `ad_bom_child_param` | ~173 | LOW | Pre-migration artifacts. Never read by current code (BOMWalker uses m_bom/m_bom_line). Dead data. |

**V2 is the most significant.** The compiler reads ad_space_type, ad_wall_type,
ad_floor_type, ad_unit_type_room, ad_building_storey from component_library.db via
direct JDBC connections in StoreyCompiler, MultiUnitCompiler, WallTypeResolver,
UnitInteriorResolver, SpaceTypeAD, FloorTypeAD, SlabSpecAD, BuildingBOM. These are
DSL-compiler paths (TB-LKTN, condo_mid) — not the BOM-based EN-BLOC path used by
Rosetta Stones. The BOM path (PlacementLoader → BOMWalker) is clean.

**Risk assessment:** V2 is a legacy architecture concern, not an active drift. The
ad_* tables serve the DSL generative compiler (Phase 0-era) which is a parallel
compilation path to the BOM-based pipeline. Both paths coexist. The BOM path never
reads ad_* tables. However, the spec should be updated to acknowledge this dual
architecture rather than claiming component_library.db is geometry-only.

### 8.3 Compiler never reads extraction/input DB?

**CLEAN for main code.** Zero hits for `I_Element_Extraction` in `DAGCompiler/src/main/`.
T18 tamper rule guards this proactively (0 violations, verified by RosettaStoneGateTest).

**Test code does read extraction** (PlacementCollectorVisitorTest, DataIntegrityTest,
TerminalSandboxTest) — acceptable for verification purposes, but creates a soft
dependency: if I_Element_Extraction is deleted from component_library.db (per V1 fix),
these tests would break. Tests should read from a test-specific extraction fixture.

### 8.4 schema_snapshot_bom.sql — The Compile-Time Injection

**78 CREATE TABLE** statements are injected into `_compile.db` at compile time.
These come from component_library.db (ad_* tables) and are merged with BOM data.
This means the compiler sees a single merged DB, not the clean 3-split the spec describes.

**Consequence:** The compiler's separation of concerns is **logical** (different table
prefixes, different source DBs) but **not physical** at runtime. `_compile.db` contains
everything. This is acceptable as a build artifact (like linking object files into a
binary), but must not be confused with the source DBs being mixed.

### 8.5 Action Items

| # | Action | Addresses | Severity | Status |
|---|--------|-----------|----------|--------|
| R17 | DELETE FROM I_Element_Extraction in component_library.db | V1 | MEDIUM | **TODO** — data removal (reader code already removed in R13) |
| R18 | DROP TABLE ad_bom, ad_bom_child, ad_bom_child_param in component_library.db | V3 | LOW | **TODO** — dead tables |
| R19 | Update ConstructionAsERP.md §1.1 to acknowledge dual architecture (DSL ad_* tables in library = legacy generative path, BOM path is clean) | V2 | DOC | **TODO** |
| R20 | Migrate test extraction fixtures out of component_library.db | §8.3 test dependency | LOW | **DEFER** — tests work as-is |
| R21 | Extract `host_element_ref` from `IfcRelVoidsElement` into I_Element_Extraction | Schema-Not-Geometry §15.6 | MED | **TODO** — M16/M17 upgrade from AABB_PROXIMITY to FK join |
| R22 | Extract `I_Element_Connectivity` linking table from `IfcRelConnectsElements` | Schema-Not-Geometry §15.6 | MED | **TODO** — M13/M14/M15 upgrade from positional grouping |
| R23 | Extract `I_Element_Interference` linking table from `IfcRelInterferesElements` | Schema-Not-Geometry §15.6 | LOW | **TODO** — M9/M10 upgrade from AABB intersection |
| R24 | Extract `fire_stop_product_ref` from `IfcRelFillsElement` into I_Element_Extraction | Schema-Not-Geometry §15.6 | LOW | **TODO** — M11 upgrade from WARN to FK check |

**R17/R20 coupling (2026-03-19):** R17 (delete 49K extraction rows) and R20
(migrate test fixtures) are sequentially dependent. If R17 executes without R20,
three tests break: PlacementCollectorVisitorTest, DataIntegrityTest,
TerminalSandboxTest — all read I_Element_Extraction from component_library.db.
**Execution order:** R20 first (create test-specific extraction fixtures),
then R17 (delete from component_library.db). Verify with `mvn test` between steps.

**R21-R24 Schema-Not-Geometry (2026-03-19):** These extraction gaps were identified
by auditing M1-M17 validation rules against the Schema-Not-Geometry principle
(BBC.md §2, DocValidate.md §15.6): "If a rule uses AABB arithmetic, check whether
an IFC relationship could be extracted as a column instead." 8 of 17 rules
currently use AABB fallback. Each R21-R24 gap, when closed, upgrades one or more
rules from AABB arithmetic to relational FK join — zero rule schema change,
just an `AD_Val_Rule_Param.check_method` update. Priority: R21 first (openings and
host walls affect M16+M17 which are user-visible in BIM Designer).

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
| `[VIOLATED] P06_NO_SAME_CLASS_OVERLAP — {name} — overlaps {name} vol={v} m³` | `PlacementProver.java` via `BIMLogger.proof()` | Per-element overlap with names + volume — **diagnosable from log without visual inspection** |
| `[PROVEN] P06_NO_SAME_CLASS_OVERLAP — GLOBAL — N placements, no same-class overlaps` | `PlacementProver.java` | P06 spatial integrity confirmed |

**Log-based proofing (session 28):** BIMLogger FINE-level output from PlacementProver
now includes element names and overlap volumes for every VIOLATED proof. This means
P06 false positives (chairs at tables, curtain wall mullion corners) can be triaged
entirely from `grep VIOLATED logs/pipeline_*.log` — no viewer needed. This extends
the Challenge Mantra: proofs are not just run, they are _explained_ in the log.

**Cross-reference:** `docs/SourceCodeGuide.md` — Chapter 6 (9-Stage Pipeline, stage logging),
Chapter 1 §Examining `{PREFIX}_BOM.db` (SQL queries that verify the same data from outside),
Appendix A §Step 2.2 (pipeline stage progression).

---

## The Challenge Mantra (recall every session)

> **The viewer is a confirmation tool, not a discovery tool. You open it to see
> what you've already proven, not to find what might be wrong.**
>
> Progress: R1-R16 tracked. **R1-R16 all DONE.** R17-R20 from Gap 8 audit.
> R21-R24 from Schema-Not-Geometry audit (8 AABB fallbacks → extraction columns).
> R25-R26 from P06 false-positive audit (session 28): furniture + curtain wall
> exemptions spec'd, GEO_ slab fallback identified.
> R27 from Compile Bridge audit (session 29): C_DocType spec/code drift.
> Rotation tested (R3). Parametric fallback gated (R2). BOMWalker reads from
> master catalog (R7). Fidelity grouping key fixed (R9). ROUTE step-uniformity
> enforced (R8) — non-uniform groups rejected, ROUTE 34K→533. Verb fidelity
> promoted to gating (R10) — exact verbs (TILE, FRAME) block pipeline at >5mm.
> World origin stored in BOM (R11), compiler no longer reads extraction (R12).
> Hardcoded building names removed (R14). T18-T20 tamper rules guard proactively.
> R16 coordinate double-counting FIXED (session 17): child BOM origins zeroed
> (FLOOR, DISCIPLINE, FLOOR STR) — only BUILDING keeps world origin.
>
> **Remaining drift vectors:** ROUTE inter-leg position (533 instances, avg 295m).
> SPRAY replaced by CLUSTER (session 32) — lossless per-instance encoding eliminates grid approximation errors.
>
> **TE status (updated 2026-03-20):** CP-1 implemented: `m_bom_line_ma` table
> carries IFC GUIDs per instance (M_InOutLineMA pattern). SpatialDiff identity
> matching: 48336/48428 exact (99.8%), 0 missing, 0 extra. Remaining 92
> failures = FRAME verb expansion coordinate mismatch (centroid-vs-LBD offset,
> Gap 6 scope). SH/DX use position-based fallback (no extraction GUIDs in output).
>
> **Each session:** read the checklist above. Check each box. Do not claim PASS
> on something the gates cannot actually prove.
