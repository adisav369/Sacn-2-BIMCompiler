# Action Roadmap — BIM Intent Compiler

> **Foundation:** [BBC](BOMBasedCompilation.md) · [DATA_MODEL](DATA_MODEL.md) · [BIM_COBOL](BIM_COBOL.md) · [ConstructionAsERP](ConstructionAsERP.md) · [TestArchitecture](TestArchitecture.md) · [SourceCodeGuide](SourceCodeGuide.md)

*Updated: 2026-03-21 (session 50). Previous version archived in git history.*

## Current Position

**Pipeline proven.** 34 Rosetta Stone buildings compile correctly:
- SH (55), FK (82), IN (699), BR (48), RD (53), RL (73), DX (1099), TE (48,428): original fleet
- BA (11), BH (5), BS (16), IP (27): PCERT IFC4x3 — session 44
- SC (3214), CA (2586), CS (1078), CH (3693), CE (2110), CP (6584), ES (1941), MO (3114): session 44
- GH (193), JS (61), NI (104), WB (125), WL (114), WT (55), WA (1749), JE (626), WI (1), RA (442), RM (6787), RS (4133), CL (3214), HI (2068): session 44b

63 verbs. 9-stage pipeline. 205 witnesses. 2459 products. 4-DB architecture. 34 building types.
BIM Designer: 285/285 GREEN. BackOffice: 19/19 GREEN. G-1 through G-9 DONE.
**Data Flywheel (session 50):** 415 mined dimension rules + 297 building profiles + 3-layer advisory output.
Self-improving pipeline: each compiled building enriches the validation pool automatically.
**BIMEyes (session 50):** Standalone geometric comprehension module. 26 proofs, 3 tiers, 41 source files.
Shape-aware advisories wired into flywheel (FL-5). IFCtoBOM depends on BIMEyes.

**Market context:** See [`BIM_Compiler_Market_Impact_Report.pdf`](BIM_Compiler_Market_Impact_Report.pdf) — USD 10B global BIM market (2025), Malaysia BIM mandate from July 2025 (all projects ≥RM10M).

---

## Critical Path: Pipeline Readiness for New IFCs

These items must be resolved to confidently process arbitrary IFC files.

### CP-1: TE Per-Element Verification (Gap 5 closure) ✓

**Problem:** TotalityContractTest failed for TE because position-based element matching
(ROW_NUMBER by position) was unreliable at 48K scale. 1022 elements have 1mm rounding
boundary crossings from CLUSTER encoding + BOM accumulation. The output IS correct —
spot-checking proved it — but automatic per-element verification was missing.

**Original proposal:** Store `element_ref` in CLUSTER verb entries. **Superseded** by
MA-based identity threading (iDempiere M_InOutLineMA pattern) which delivers the same
result without modifying verb format. See [LAST_MILE_PROBLEM.md §Gap 5](LAST_MILE_PROBLEM.md#gap-5-no-wysiwyg-totality-proof) for full chain.

**Delivered:**
- `m_bom_line_ma(bom_id, sequence, qi, guid)` — per-instance identity table
- `VerbDetector.computeExpansionOrder()` — maps element→qi by nearest centroid
- `PlacementCollectorVisitor.loadMaGuids()` — reads MA → sets element_ref = IFC GUID
- `SpatialDiff.diffByIdentity()` — hybrid identity + position fallback matching

**Gate:** W-TOT PASS for CO_TE (48428/48428 identity-matched, 0 missing, 0 extra).

**G3-DIGEST remains FAIL for CO_TE.** This is a coordinate precision issue (1015
elements with 1mm rounding boundary crossings), not an identity issue. Tracked under
[LAST_MILE_PROBLEM.md §Gap 6](LAST_MILE_PROBLEM.md#gap-6-verb-pattern-fidelity--non-uniform-spacing-accepted-as-uniform)
as a sub-finding (S66 float precision). G3-DIGEST is not a valid gate for CO_TE —
W-TOT is the authoritative per-element proof.

### CP-2: DX MIRROR Verb

**Problem:** DX walkthru deferred because Duplex is a mirrored half-unit pair
(UNIT_A + UNIT_B at π rotation). PlacementCollectorVisitor handles rotation stack
but the structured DX BOM is incomplete. 85 C9 axis mismatches (pre-existing).

**Fix:** Complete DX structured BOM with MIRROR verb. Verify C9 PASS for DX.

**Effort:** Large. Needs element pairing (444 pairs + 211 unpaired), BOM population,
rotation handling verification.

**Gate:** DX C9 PASS. DX 10/10 PASS.

### CP-3: New IFC Onboarding Pipeline ✓

**Problem:** Adding a new IFC building requires YAML config, extraction, BOM
generation, and verification. The process works (dynamic building registration via
`construction_manifest.yaml`) but is undocumented as a runbook.

**Fix:** Write a step-by-step runbook: IFC → extract.py → YAML → IFCtoBOM → verify.
Include expected gate results for different building scales.

**Delivered (session 42):**
- [`IFC_ONBOARDING_RUNBOOK.md`](IFC_ONBOARDING_RUNBOOK.md) — self-service 9-step runbook with commands, expected output, troubleshooting, checklist
- `NewBuildingGenerator.java` (`IFCtoBOM/src/main/java/com/bim/ifctobom/`) — Java template generator, auto-detects storeys from reference DB. Usage: `mvn exec:java -pl IFCtoBOM -Dexec.mainClass="com.bim.ifctobom.NewBuildingGenerator" -Dexec.args="--prefix XX --type BuildingType --name 'Name'" -q`
- `WorkOrderGuide.md` §File Convention — complete IFC source inventory (14 IFCs in `DAGCompiler/lib/input/IFC/`, 9 classify_*.yaml files, 7 buildings fully onboarded)
- Updated: `SYSTEMS_INSTALLER_GUIDE.md` §5.2, `INDEX.md`, `SourceCodeGuide.md` §12

**What was proven:**
- Generator tested against SH reference DB — correctly detects 3 storeys, generates valid YAML+DSL skeletons with conventional codes (GF, ROOF, MISC)
- Full compile clean (`mvn compile -q`) with NewBuildingGenerator in module
- Pipeline itself proven on 5 Rosetta Stones (SH 55, FK 82, IN 699, DX 1099, TE 48428) — same steps the runbook documents

**Proven end-to-end (session 44):**
- 4 PCERT IFC4x3 buildings onboarded from extracted DB → YAML → pipeline → all gates PASS:
  - BA (Building Architecture, 11 elements): 10/10 PASS
  - BH (Building Hvac, 5 elements): 11/11 PASS
  - BS (Building Structural, 16 elements): 11/11 PASS — CLUSTER verb auto-detected (beams, 1.6x reuse)
  - IP (Infra Plumbing, 27 elements): 11/11 PASS — CLUSTER verb auto-detected (flow segments, 9.0x reuse)
- 5 new IFC classes added to G5 known list: IfcEarthworksFill, IfcElementAssembly, IfcDiscreteAccessory, IfcFooting, IfcChimney
- Zero compiler code changes — only test infrastructure (GATE_SCOPE) and configuration (YAML/DSL/manifest)
- 13 classify_*.yaml files, 12 buildings fully onboarded (was 8)

**What remains:**
- Category 1 (no extracted DB): FJK_Project, Smiley_West, Vogel_Gesamt — need `extract.py` run first
- Category 3 (partially onboarded): FK, BR, RD, RL already have YAML but are not new — validated in prior sessions

### CP-4: Geometric Archetype Abstraction (IFC Class Independence)

**Problem:** The compiler has 43 decision points that switch on IFC class strings
(`case "IfcPlate" → "CURTAIN_PANEL"`, overlap tolerances, GUID prefixes, etc.).
This violates BBC.md §2.2.1 ("code must never branch on component_type") and the
same principle applied to IFC class — the compiler should be class-agnostic.
AUDIT_PIPELINE.md flagged this as a **CRITICAL layer boundary breach**.

**Evidence (session 44):** Geometric fingerprint test (P10_SHAPE_IDENTITY) found:
- TE: 33,380/48,428 IfcPlate elements are "Metal Deck" (107×150×500mm) — not planar,
  mislabelled by Revit's IFC export. Compiler treats all as CURTAIN_PANEL.
- DX: 12 IfcWindow elements have 417mm depth (planarity=0.51) — extraction AABB
  captures full window assembly. Traced to M_BOM row `DUPLEX_SINGLE_UNIT_STD (#60, #64)`.
- DX: 1 IfcWall (Foundation 435mm) is a short stub pier, not planar.
- SH: 55/55 clean. FK: 2 pitched roof slabs. AC: 1 merged double door.

**Root cause:** The IFC class label is assigned by the BIM authoring tool's export
mapping, not by geometry. The pipeline trusts it end-to-end without verification.
No mechanism exists to detect or correct wrong labels.

**Spec authority:**
- BBC.md §2.2.1 "The Rule" — compiler decides by BOM structure, not type labels
- BBC.md §1.1 — disciplines follow iDempiere pattern: no `switch(docBaseType)`
- AUDIT_PIPELINE.md — hardcoded `"IfcFurnishingElement"` in resolver = CRITICAL
- TestArchitecture.md §Anti-Drift Rule 1 — "No Magic Coordinates" (extends to no magic class strings)
- BIM_COBOL.md §18.17-18.18 — CLASSIFY verb for property-based reclassification

**The three-layer solution:**

| Layer | Source of truth | Decides | Example |
|-------|----------------|---------|---------|
| **Geometric archetype** | Computed from `allocated_width/depth/height_mm` (dimensionless ratios: planarity, elongation, squareness) | Placement validation, overlap tolerance, Z-band, mesh binding relaxation | PLANAR element gets thin-face tolerance regardless of whether labelled IfcPlate or IfcSlab |
| **Component library** | `component_definitions`, `component_types`, `M_Product`, `placement_rules` in `component_library.db` | Semantic identity, attachment convention, product type, orientation | Library knows "Metal Deck" is structural floor, not curtain panel |
| **IFC class** | `m_bom_line.role` / `elements_meta.ifc_class` | Traceability metadata only — where the element came from | Carried through for audit trail, never used as decision variable |

**Implementation phases:**

| Phase | What | Status |
|-------|------|--------|
| **4a** | `shape_archetype` + `scale_band` columns on `m_bom_line` (6 INSERT paths, migration SQL) | **DONE** (s46) |
| **4b** | 15 geometric switches → archetype (PlacementProver P04/P10, SpatialDiff, MeshBinder, GeometryIntegrityChecker) | **DONE** (s46) |
| **4c** | 8 semantic switches → archetype (BOMExporter UOM/desc/aggregation, BuildingWriter GUID prefix/element_type) | **DONE** (s46) |
| **4d** | ~~QTO/costing rates to authority data table~~ — dropped from CP-4 scope (separate concern) | N/A |
| **4e** | `BomValidator.checkShapeConsistency()` pre-commit gate | **DONE** (s46) |

**Geometric half gate: PASSED.** 23/43 switch points replaced. SH 9/10 undisturbed.
**Semantic half gate: PASSED.** 20/37 refs switched to product_category (s48). SH 9/10 undisturbed.
New utility: `GeometricFingerprint.classifyArchetype()`, `classifyScaleBand()`, `isHostedOpening()`.
BBC.md §4.2.3 documents the schema, computation, and witness claims.

### CP-4 Semantic Half: Product Category ✓

**Problem:** 37 IFC class references remain in domain-specific code. These answer "what kind
of element?" — pipe vs column (both ELONGATED), wall vs slab (both PLANAR+ARCHITECTURAL).
Archetype alone cannot distinguish because the question is semantic, not geometric.

**Delivered (session 48):**
- `CP4_002_product_category.sql` — migration adding `product_category TEXT` to `component_types`
- `ProductCategory.java` — static resolution map (ifc_class → product_category), 58 entries
- 10 categories: STRUCTURAL_LINEAR, STRUCTURAL_PLANAR, MEP_ROUTING, MEP_TERMINAL,
  OPENING, FURNISHING, ENVELOPE, CIRCULATION, SITE, INFRASTRUCTURE
- 20 of 37 IFC class decision refs replaced with product_category
- 3 missing MEPWriter IFC classes added to component_types (IfcOutlet, IfcSwitchingDevice, IfcFan)

**Remaining on ifcClass (documented exceptions):**
- OVERLAP_EXEMPT_CLASSES: IfcMember/IfcBuildingElementProxy — product_category too coarse
- P14 IfcSlab: "floor" vs "wall" distinction within STRUCTURAL_PLANAR
- MEPWriter output metadata (11 refs): IFC class is traceability metadata per BBC.md §2.2.1 layer 3
- findPlacement matching: ifcClass as lookup key, not decision variable

**Specs to consult:**
- BBC.md §2.2.1 ("The Rule"), ConstructionAsERP.md §1.4 (product taxonomy)
- DATA_MODEL.md (component_library.db schema), SourceCodeGuide.md §4 (library resolution)
- BIM_COBOL.md §18.17-18.18 (CLASSIFY verb for property-based reclassification)

**Session 44 delivery:**
- `GeometricFingerprint.java` — computes planarity/elongation/squareness from dimensions
- `PlacementProver.java` P10_SHAPE_IDENTITY — per-element shape consistency proof with M_BOM trace
- `GeometricFingerprintTest.java` — W-SHAPE, W-EQUIV, W-CENSUS, P10 pipeline witnesses
- `LAST_MILE_PROBLEM.md` §Geometric Fingerprint — formula, theory, results
- Results: SH 55/55 PASS, DX 1086/1099 (13 traced to BOM data), 5 buildings tested

**Session 42 discovery — AABB Qualifier:**
The IN G3 window drift analysis (10-90mm gradient) revealed that AABB dimensions lack a semantic qualifier. The same `aabb_width_mm` column means different things depending on context: INNER (room clear volume), STRUCTURAL (centerline grid), OUTER (full object extent), OPENING (clear door/window opening). Proposed fix: `aabb_qualifier TEXT DEFAULT 'OUTER'` on `m_bom`. See [`INNER_SURFACE_ANALYSIS.md`](INNER_SURFACE_ANALYSIS.md) for full analysis. This connects to WF-BB (wireframe = AABB visualization), PHANTOM (G-13 Click-to-Place uses INNER), and R21 (host_element_ref eliminates AABB-vs-opening ambiguity).

**Gate:** A new building can be onboarded by following the runbook without code changes. ✓

---

## Active Track: BIM Designer (Phase G)

### Completed Gates

| Gate | Session | What |
|------|---------|------|
| G-1 | s15 | Module skeleton + DesignerServer |
| G-2 | s16 | DocValidate + DemoHouse + pattern rules |
| G-3 | s17 | Design Mode + bbox renderer |
| G-4 | s26 | work_output.db + Save/Recall |
| G-5 | s27 | BOM Chooser + Place + Layout + Inference + Ambient |
| G-6 | s29 | Compile bridge (real pipeline) |
| G-7 | s35 | Assembly builder (layer-by-layer TACK, U-value) |
| G-8 | s45 | Click-to-Place (viewport click → room → discipline placement) |
| G-9 | s46b | ORDER View + BOM Outliner (three views share C_OrderLine) |
| FL-1 | s47 | Data Flywheel: dimension validation + building profiles + auto-mining |
| FL-2 | s50 | Advisory Output: W_Validation_Advisory + listAdvisories API + suggestDimensions + Blender panel |
| FL-5 | s50 | EYES Integration: ShapeAdvisoryWriter → 3-layer advisory (DIMENSION/PROFILE/SHAPE) |

### Next Gates

| Gate | What | Blocks |
|------|------|--------|
| **FL-4** | **Relational mining** — mine element-to-space relationships, enrich MEP schedules. | — |
| **G-10** | **Promote to BOM** — C_OrderLine → m_bom + m_bom_line. Dangles check, owner sign-off, entity_type='U', provenance='GENERATIVE'. **DONE (session 47).** | — |
| **G-11** | **ParametricMesh UI** — Blender panel exposing slider params. Construction-grade: BOM sub-assembly with tack I/O. | — |
| **G-12** | **Text Mode** — Search box + future NL → OrderLine + ASI. | — |
| **G-13** | **Click-to-Place** — Interactive discipline placement. User selects discipline, clicks area, rules auto-place. Spec: `BIM_Designer.md` §18.8. | — |

### Dependency Chain

```
G-1..G-10 (DONE) ─── G-11 (ParametricMesh)
                     │                    G-12 (Text Mode)
                     │                    G-13 (Auto-chain)
                     │
FL-1..FL-2..FL-5 (DONE) ── FL-4 (Relational mining)
```

**FL-3 dropped as gate (session 50):** `suggestDimensions(ifcClass)` API already wired
in FL-2. CALIBRATE verb wrapper adds no value — Python panel calls `suggest_dimensions()`
directly. Auto-fill UX folded into next Designer pass.

---

## Completed Phases (reference only)

| Phase | What | Status |
|-------|------|--------|
| 0.0 | Structured BOM gap — walkthru path wired | DONE |
| 0 | EN-BLOC Singularity — SH/DX compile correctly | DONE |
| 0.1 | Product Catalog — 78 M_Products, BOM digest match | DONE |
| 0.2 | BOM Walk — PlacementLoader reads m_bom_line, sole source | DONE |
| A | Gate Convergence — G1-G6 GREEN for SH/DX | DONE |
| A.1 | Geometry Fidelity — C8/C9/C10 contracts | DONE (Tier 1) |
| B | Terminal Recomposition — 48K elements, 37:1 factorization | DONE |
| SRS | Spec hardening — BBC.md, G4_SRS, TACK_FIX, DocValidate, traceability | DONE |
| TACK-FIX | LBD convention testing + BIMLogger | DONE (session 25) |

---

## Phase FL: Data Flywheel — Self-Improving Validation

*Spec: BBC.md §9, §9.1, §9.2. See also DISC_VALIDATION_DB_SRS.md §DV010-DV011.*

The pipeline learns from every building it processes. Each new IFC both
uses and enriches a mined validation pool. This phase extends the flywheel
from advisory logging to **actionable suggestions in the BIM Designer.**

### FL-1: Outlier Report Table (DONE — session 47)

Layer 1 (DimensionRangeValidator) and Layer 2 (BuildingProfileValidator)
run as pre-flight in IFCtoBOMPipeline. Post-commit auto-mining feeds back.
415 dimension rules + 297 profile rows from 36 buildings.

### FL-2: Advisory Output for BIM Designer ✓

**Delivered (session 50):**
- DV012 migration: `W_Validation_Advisory` table in disc_validation.db
- `DimensionRangeValidator.writeAdvisories()` — WARNING + SUGGESTION per outlier
- `BuildingProfileValidator.writeAdvisories()` — WARNING per anomaly, INFO summary
- `IFCtoBOMPipeline` calls both after validate() (Layer 1 + Layer 2)
- `DesignerAPI.listAdvisories(buildingId)` + `DesignerAPIImpl` + `DesignerServer` dispatch
- `DesignerAPI.suggestDimensions(ifcClass)` — FL-3 prep: typical W/D/H + nearest M_Products
- Python `client.list_advisories()` + `client.suggest_dimensions()` + `panel._draw_advisory_panel()`
- FlyAdvisoryTest: 9 witnesses (W-FL-ADVISORY-1..5, W-FL-CALIBRATE-1..2, W-FL-SHAPE-1..2)

**Schema (DV012):**
```sql
CREATE TABLE W_Validation_Advisory (
    advisory_id     INTEGER PRIMARY KEY AUTOINCREMENT,
    building_type   TEXT NOT NULL,
    element_ref     TEXT,           -- element bomId (NULL for profile-level)
    layer           TEXT NOT NULL   CHECK(layer IN ('DIMENSION','PROFILE','COMPLIANCE','SHAPE')),
    severity        TEXT NOT NULL   CHECK(severity IN ('INFO','WARNING','SUGGESTION')),
    rule_name       TEXT,
    message         TEXT NOT NULL,
    actual_value    REAL,
    expected_min    REAL,
    expected_max    REAL,
    suggestion      TEXT,
    created_at      TEXT DEFAULT (datetime('now'))
);
```

**Wire protocol:** `{"action":"listAdvisories","buildingId":"..."}` → `ListAdvisoriesResponse`
`{"action":"suggestDimensions","ifcClass":"..."}` → `SuggestDimensionsResponse`

### ~~FL-3: CALIBRATE Verb~~ (dropped — absorbed into FL-2)

The `suggestDimensions(ifcClass)` API was delivered as part of FL-2 (session 50).
It returns typical W/D/H ranges + nearest M_Products from the mined pool.
The Python client verb `suggest_dimensions()` is wired. No separate CALIBRATE
verb needed — the panel calls the API directly. Auto-fill UX will be folded
into the next Designer pass.

### FL-4: Relational Mining (Layer 3 Flywheel)

**Problem:** The MEP schedules in `ad_space_type_mep_bom` (186 rows)
are static — hand-authored from codes and Terminal observation.
They don't grow with new buildings.

**Solution:** Mine element-to-space relationships from buildings that
have `rel_contained_in_space` data (IN has 253 containment rows).
For each space type, count which MEP elements appear and at what
density per floor area.

**Query basis:**
```sql
SELECT s.ifc_class as space_class, e.ifc_class as element_class,
       COUNT(*) as count, AVG(area) as avg_area
FROM rel_contained_in_space r
JOIN elements_meta e ON e.guid = r.element_guid
JOIN elements_meta s ON s.guid = r.space_guid
GROUP BY s.ifc_class, e.ifc_class
```

**Outcome:** Enrich `ad_space_type_mep_bom` with mined observations.
Compare hand-authored schedules against actual building data.
Flag discrepancies: "Code says 1 sprinkler per 21m², but Clinic
averages 1 per 15m²."

**Effort:** 1 session. Depends on: buildings with IfcSpace + containment
data (IN, SC, ES have this).

### FL-5: EYES Integration — Shape-Aware Advisories ✓

**Delivered (session 50):**
- `ShapeAdvisoryWriter` in IFCtoBOM — uses BIMEyes `ShapeClassifier` + `ProductCategory`
- IFCtoBOM now depends on BIMEyes module (pom.xml)
- Wired as Layer 3 in IFCtoBOMPipeline, after DimensionRange (L1) and Profile (L2)
- Two checks per element:
  1. **CLASS_SHAPE** — IFC class vs shape archetype consistency via `Fingerprint.verifyClassConsistency()`
  2. **DISCIPLINE_SHAPE** — geometry-inferred discipline vs declared product category
- Writes to `W_Validation_Advisory` with `layer='SHAPE'`, severity `SUGGESTION`
- SH result: 19 SHAPE advisories (thin IfcMember mullions flagged as MEP-like at fitting scale)
- Witnesses: W-FL-SHAPE-1 (mismatch detection), W-FL-SHAPE-2 (no false positives for consistent elements)

**Advisory table now has 3 active layers:**

| Layer | Source | What it checks |
|-------|--------|---------------|
| DIMENSION | DimensionRangeValidator (DV010) | Element W/D/H vs mined typical ranges |
| PROFILE | BuildingProfileValidator (DV011) | Class diversity, missing openings, novel classes |
| SHAPE | ShapeAdvisoryWriter (FL-5/EYES) | Archetype vs IFC class, discipline vs geometry |

**Future:** P25 ROOM_VALIDITY and P26 BUILDING_COMPLETENESS proofs can write
to the same table with `layer='COMPLIANCE'` when connected to IFCtoBOMPipeline.

### FL Dependency Chain

```
FL-1 (DONE) → FL-2 (DONE) → FL-3 (CALIBRATE verb — API ready)
                               ↓
                             FL-4 (relational mining)
FL-5 (DONE) ─────────────────┘
     ↑
EYES Phase 1..3 (DONE)
```

### FL Designer Development Note

**For the BIM Designer team:** The flywheel validation produces structured
advisories — not just log output. The Designer should:

1. Call `listAdvisories(buildingId)` after loading a building
2. Display advisories in a panel (INFO/WARNING/SUGGESTION colour-coded)
3. Allow click-to-highlight of flagged elements
4. For SUGGESTION-type advisories, offer "Apply" button that calls
   `updateOrderLine()` with the suggested dimension
5. Call `suggestDimensions(ifcClass)` to auto-populate dimension fields
   when placing new elements (API already wired)

The advisory data is already in disc_validation.db — the Designer just
needs to read and present it. No new validation logic needed on the
Python side.

---

## Future Phases (not on critical path)

| Phase | What | Depends on |
|-------|------|-----------|
| C | 2D Drawing Export (3D → SVG) | Phase A |
| D | Synthetic Rosetta Stone (round-trip proof) | Phase C |
| E | Generative from 2D Layout | Phase D |
| F | BIM COBOL v1.0+ maturity | Phase B |
| H | iDempiere ERP Integration (CSV → REST → OSGI) | Phase G |

---

## WF-BB Roadmap — Wireframe-First Interaction Protocol (§26)

> **Status:** Core + Peek are CODE DONE. Remaining 6 phases are SPEC ONLY / STUB — no sessions assigned, not Q2-relevant. Full backlog in [BIM_Designer_SRS.md §26](BIM_Designer_SRS.md).

| Phase | Status | Q2 Triage |
|-------|--------|-----------|
| **Core** (WF-01..05, 09, 10) | CODE DONE | **Q2** — needs Blender visual test only |
| **Peek** (WF-07, 08) | CODE DONE | **Q2** — needs Blender visual test |
| **Add-in-Phase2** (WF-06) | SPEC ONLY | POST-LAUNCH |
| **Chain** (WF-11) | STUB | POST-LAUNCH |
| **Ghost Drag** (WF-12, 13) | SPEC ONLY | POST-LAUNCH — depends on §9 DiffVerb |
| **Cost** (WF-14..16) | STUB | POST-LAUNCH — depends on §5 5D Cost |
| **CR** (WF-17..20) | SPEC ONLY | POST-LAUNCH |
| **Audit** (WF-21..25) | SPEC ONLY | POST-LAUNCH — depends on §10 AD_ChangeLog |

## Go-to-Market Timeline

*Source: [BIM_Compiler_Market_Impact_Report.pdf](BIM_Compiler_Market_Impact_Report.pdf)*

| Phase | Target | Actions | Success Metrics |
|-------|--------|---------|-----------------|
| **Q2 2026 Soft Launch** | GitHub public release | 3 Rosetta Stones (SH, DX, CitizenHome). Challenge Paper circulated for peer review. Blender/Bonsai integration demo. | 50+ GitHub stars, 3+ academic citations, CIDB awareness |
| **Q3 2026 MY Pilot** | Government project | CIDB BIM lab partnership (Johor closest). Malaysian affordable housing typology showcase. Present at ICW Borneo or equivalent. | 1 government project using compiler output, NBeS compatibility demo |
| **Q4 2026 Academic** | Journal submission | Submit to Automation in Construction journal. Present at buildingSMART summit. Publish Ground Truth Methodology as standalone paper. | Journal acceptance, buildingSMART recognition |
| **2027 Scale** | Community expansion | Vocabulary addon framework open to community. Infrastructure extension (bridges, roads via IFC4.3). Training curriculum for CIDB programs. | 10+ building typologies, 500+ GitHub stars, institutional adoption |

### Launch Readiness Gaps (honest assessment)

The market data supports launching now into the mandate window, but academic credibility
depends on closing these metadata-layer gaps. The compound enrichment model means each
fix is permanent — but don't claim LOD 400 completeness before they're resolved.

| Gap | Documented In | Impact | Status |
|-----|--------------|--------|--------|
| ~~IFC class used as decision variable~~ | [LAST_MILE_PROBLEM.md](LAST_MILE_PROBLEM.md) §Geometric Fingerprint, CP-4 | ~~43 switch points on IFC class in compiler~~ | **DONE** — CP-4 geometric (s46) + semantic (s48). ~17 documented exceptions remain |
| Verb fidelity for non-uniform spacing | [LAST_MILE_PROBLEM.md](LAST_MILE_PROBLEM.md) R-30 | 533 ROUTE instances have step-uniformity gap | PENDING — fixable, not architectural |
| ~~depth_mm semantics~~ | [LAST_MILE_PROBLEM.md](LAST_MILE_PROBLEM.md) R-17 | ~~Extraction data leaked into product catalog~~ | **DONE** — V006 migration dropped I_Element_Extraction (commit `854741f`) |
| Vocabulary gaps block real projects | Market Report §6.1 | Each new building type may hit missing verbs | MITIGATED by compound enrichment model |
| TE per-element verification | CP-1 ✓ | W-TOT 48428/48428 identity-matched (92 FRAME position mismatches = Gap 6) | DONE — MA identity threading |
| DX MIRROR verb | CP-2 below | Duplex 85 axis mismatches pre-existing | PENDING — large effort |

**Position:** "Spatially valid" (proven by 6 mathematical gates). Not yet "construction-ready"
(requires depth_mm fix + verb fidelity closure). The distinction is honest and documented.

---

## S51-AUDIT: Focused Audit Fix Session

*Full report: [`AUDIT_S51_FOCUSED.md`](AUDIT_S51_FOCUSED.md). Cross-referenced against BBC.md, EYES_SRS.md, TestArchitecture.md, BACK_OFFICE_SRS.md.*

**Context:** 127 commits (S30–S50, 5 days) audited across 5 dimensions.
**Finding:** Hard problems solved correctly; wiring/plumbing has gaps. 8 P0, 9 P1, 9 P2.

| Phase | Focus | P0 | P1 | Est. | Gate |
|-------|-------|----|----|------|------|
| **1** | **Security** — wire existing SessionManager into handlers, path traversal, CORS, error leaks | 2 | 2 | 1h | BackOfficeServerTest 401 + existing 19/19 GREEN |
| **2** | **Geometry** — StoreyZBand Math.max, PerimeterClosure float keys, OpeningContainment depth, NaN guard, AABB validation | 2 | 3 | 1h | CompilerContractTest prover 7/7; SH 9/10 |
| **3** | **Test Integrity** — replace assumeTrue with fail(), remove assertTrue(true), expand seal manifest, fix exception swallow | 2 | 2 | 30m | Full suite GREEN; seal INTACT |
| **4** | **Migrations** — delete DV006, renumber V011/V012 duplicates, DV003 idempotency | 2 | 2 | 30m | `sqlite3 :memory: < migration/*.sql` clean |
| **5** | **Docs** — coordinate system, P08/P21 limitations, seal manifest expansion | 0 | 0 | 15m | — |

**Spec violations found:**

| Spec | Section | Violation |
|------|---------|-----------|
| BACK_OFFICE_SRS.md §5a | Token validation | Auth built, never wired into handlers |
| TestArchitecture.md §Anti-Drift Rule 4 | No Hallucinated Success | 6 test classes silently skip on missing DB |
| EYES_SRS.md §4.1 P04 | Storey Z-band | `Math.max(x, x+3.5)` no-op in proof logic |
| EYES_SRS.md §4.3 P10b | Perimeter closure | Float string keys break at rounding boundary |
| BBC.md (append-only) | Migration integrity | Broken DV006 still in tree alongside DV006b fix |

**Priority:** Run S51-AUDIT before any new feature work. Total estimate: ~3.5 hours.

---

## S60-S3: Next Session Tasks

*Link from PROGRESS.md §Next session. Detailed context for each task below.*

| # | Task | Spec Reference | Priority |
|---|------|---------------|----------|
| 1 | Commit S59 uncommitted files | AUDIT_S51_FOCUSED.md §Appendix B | **DONE** (543444c) |
| 2 | Re-extract reference DBs (R21 data) | S60_ERP_ALIGNMENT.md §R21 Implementation | **DONE** (SH:7, FK:16 fills) |
| 3 | Audit P0 fixes (GEO-1, GEO-2, MIG-1, MIG-2, TEST-1) | AUDIT_S51_FOCUSED.md §1-3 | **DONE** (5 FIXED, see Appendix D) |
| 3b | EYES consolidation (VerbFactorizer → ShapeClassifier) | — | **DONE** (c93e0a5) |
| 4 | Rule-driven discipline framework (FP first) | ProjectOrderBlueprint.md §1.1, §12 | MED — **REFRAMED** as 3-session plan, see Task 4 below |
| 5 | M_BomCategory → M_Product_Category | S60_ERP_ALIGNMENT.md §M_BomCategory Assessment | MED — 77 files, dedicated session |
| 6 | Pristine component_library.db + re-baseline | DEPLOYMENT.md §Git Operations | LOW — end of session |

### Task 1: Commit S59 files
14 modified + 5 untracked. Full inventory in AUDIT_S51_FOCUSED.md §Appendix B.
Key files: WorkOutputDAO.java, BomDropper.java, OrderLineWalker.java, S60_schema.sql, WorkOrderCompileTest.java.
Add `.venv/` to .gitignore (already done). Audit verdict: all CLEAN (§Appendix B Code Integrity Audit).

### Task 2: Re-extract reference DBs
`tools/extract.py` now extracts IfcRelVoidsElement+IfcRelFillsElement chain → `rel_fills_host` table.
Pre-R21 reference DBs have NO `rel_fills_host` table. Must re-run extraction on SH/FK IFC sources.
Verify: door/window m_bom_line rows get non-NULL host_element_ref after re-extract + pipeline run.
See S60_ERP_ALIGNMENT.md §R21 Implementation for full data flow.

### Task 3: Audit P0 fixes
From AUDIT_S51_FOCUSED.md. 8 P0 findings across 4 areas:
- **GEO-1:** StoreyZBandProof.java:70 — `Math.max(x, x+3.5)` no-op
- **GEO-2:** PerimeterClosureProof.java:59 — float string key rounding boundary
- **MIG-1:** Delete DV006 (wrong column names), rename DV006b → DV006
- **MIG-2:** Renumber duplicate V011/V012 prefixes
- **TEST-1:** Silent test skipping via `assumeTrue(file.exists())` — 6 test classes
- **TEST-2:** DemoHouseTest expected count mismatch
- **SEC-1/SEC-2:** Auth wiring (lower priority — no public deployment yet)

### Task 4: Rule-Driven Discipline Framework (FP as first case)

**Moved to [ProjectOrderBlueprint.md §14](ProjectOrderBlueprint.md#14-implementation-plan--order-compilation-engine).**
Full triage (§14.1), data/code inventory (§14.2), 5-session plan (§14.3), failure criteria (§14.4).

**Status:** Session A partial (S66 `ac4150a` — Discipline wiring). Sessions B-E not started.

### Task 5: M_BomCategory replacement
77 files across 6 layers. Orthogonal semantic axes (room templates vs IFC classification).
Full assessment in S60_ERP_ALIGNMENT.md §M_BomCategory Assessment.
DisciplineBomBuilder CO path already aligned (uses ARC/STR/FP codes = M_Product_Category roots).
Remaining: BIM_COBOL verbs, template grammar (M_BomCategoryLine), AABB template matching, tests.

---

## Known Debt (ordered by priority)

*Updated: S60-S3 (2026-03-23). Stale entries cleaned per AUDIT_S51_FOCUSED.md Appendix D.*

| # | Item | Severity | Status | Q2 Triage |
|---|------|----------|--------|-----------|
| S60-6 | M_BomCategory → M_Product_Category (77 files) | HIGH | ASSESSED — S60_ERP_ALIGNMENT.md | DEFER — orthogonal to launch |
| CP-1 | TE element_ref matching for G3/Totality | MED | **DONE** (S66) — MA identity threading, W-TOT passes | CLOSED — 92 FRAME position mismatches tracked in Gap 6 |
| CP-2 | DX MIRROR verb + structured BOM | MED | DEFERRED — verification debt | DEFER — DX compiles correctly, C9 accepted; quality-of-proof |
| Task 4 | Rule-driven discipline framework (FP first) | MED | **Session A DONE** (S66) — B/C remaining | **Q2** — Session A delivered; B/C post-launch |
| R17 | Delete 49K I_Element_Extraction from component_library.db | MED | **DONE** — V006 migration (commit `854741f`) | CLOSED |
| R22 | Extract I_Element_Connectivity | MED | TODO | DEFER — enables future MEP routing |
| BBC-001 | CLUSTER expandCluster() entry validation | LOW | TODO | BACKLOG |
| BBC-002 | BomValidator verb fidelity in compliance report | LOW | TODO | BACKLOG |
| R18 | DROP dead ad_bom/ad_bom_child tables | LOW | **DONE** — V006 migration (`854741f`) | CLOSED |
| R19 | Update ConstructionAsERP.md dual architecture | DOC | TODO | BACKLOG |
| VPA-002 | ROUTE per-leg step-uniformity (533 instances) | LOW | Known limit | KNOWN LIMIT |

**Cleared from debt table (S60-S3):**
- ~~S51 Audit~~ → 5 FIXED, 1 JUSTIFIED, 1 RETRACTED. See AUDIT_S51_FOCUSED.md Appendix D.
- ~~CP-4 Geometric archetype~~ → DONE (S46+S48+S50).
- ~~R21 host_element_ref~~ → DONE (S60-S3, re-extracted SH/FK).

---

## Spec Index

| Spec | What |
|------|------|
| `BOMBasedCompilation.md` | MASTER SPEC: tack, walker, BUFFER, gospel |
| `BIM_Designer.md` | GUI, ASI, 4-action persistence, Design Mode |
| `BIM_Designer_SRS.md` | UX requirements, user journeys, state machine, WF-BB §26 |
| `ASSEMBLY_BUILDER_SRS.md` | G-7 SRS: layer-by-layer TACK, U-value |
| `G4_SRS.md` | work_output.db, master-detail, AP gate |
| `TACK_FIX_SPEC.md` | FIX-1/2/3 method specs |
| `TestArchitecture.md` | G1-G6 gates, tamper seal, traceability |
| `DocValidate.md` | AD_Val_Rule, 3-tier validation |
| `DISC_VALIDATION_DB_SRS.md` | disc_validation.db phases |
| `BlenderBridge.md` | Java-smart/Python-dumb pipe |
| `LAST_MILE_PROBLEM.md` | Gaps 1-8, R1-R27 actions |
| `SourceCodeGuide.md` | Code navigation, entry points |
| `BIM_Designer_SRS.md` §27 | FL-2 Advisory Panel spec (flywheel → Designer) |
| `ProjectOrderBlueprint.md` | Exception ordering §1, C_Project §2, abstract tree §3, BOM mining §4, nD §5, rule packs §12 |
