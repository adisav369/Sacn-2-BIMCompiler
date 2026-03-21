# Action Roadmap — BIM Intent Compiler

> **Foundation:** [BBC](BOMBasedCompilation.md) · [DATA_MODEL](DATA_MODEL.md) · [BIM_COBOL](BIM_COBOL.md) · [ConstructionAsERP](ConstructionAsERP.md) · [TestArchitecture](TestArchitecture.md) · [SourceCodeGuide](SourceCodeGuide.md)

*Updated: 2026-03-21 (session 44). Previous version archived in git history.*

## Current Position

**Pipeline proven.** 34 Rosetta Stone buildings compile correctly:
- SH (55), FK (82), IN (699), BR (48), RD (53), RL (73), DX (1099), TE (48,428): original fleet
- BA (11), BH (5), BS (16), IP (27): PCERT IFC4x3 — session 44
- SC (3214), CA (2586), CS (1078), CH (3693), CE (2110), CP (6584), ES (1941), MO (3114): session 44
- GH (193), JS (61), NI (104), WB (125), WL (114), WT (55), WA (1749), JE (626), WI (1), RA (442), RM (6787), RS (4133), CL (3214), HI (2068): session 44b

63 verbs. 9-stage pipeline. 196 witnesses. 2459 products. 4-DB architecture. 34 building types.
BIM Designer: 285/285 GREEN. BackOffice: 19/19 GREEN. G-1 through G-9 DONE.
**Data Flywheel (session 47):** 415 mined dimension rules + 297 building profiles.
Self-improving pipeline: each compiled building enriches the validation pool automatically.

**Market context:** See [`BIM_Compiler_Market_Impact_Report.pdf`](BIM_Compiler_Market_Impact_Report.pdf) — USD 10B global BIM market (2025), Malaysia BIM mandate from July 2025 (all projects ≥RM10M).

---

## Critical Path: Pipeline Readiness for New IFCs

These items must be resolved to confidently process arbitrary IFC files.

### CP-1: TE Per-Element Verification (Gap 5 closure)

**Problem:** TotalityContractTest and G3-DIGEST fail for TE because position-based
element matching (ROW_NUMBER by position) is unreliable at 48K scale. 1022 elements
have 1mm rounding boundary crossings from CLUSTER encoding + BOM accumulation.
The output IS correct — we proved it by spot-checking — but we cannot automatically
verify all 48K elements.

**Fix:** Store `element_ref` in CLUSTER verb entries so output elements retain their
extraction identity. TotalityContractTest matches by element_ref instead of position.

**Effort:** Medium. Changes: VerbDetector (encode element_ref), DisciplineBomBuilder
(pass element_ref to CLUSTER), PlacementCollectorVisitor (read element_ref from
CLUSTER, use as output element_ref), TotalityContractTest (match by element_ref).

**Gate:** TotalityContractTest PASS for CO_TE. G3-DIGEST PASS for CO_TE.

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
- `YAMLGuide.md` §File Convention — complete IFC source inventory (14 IFCs in `DAGCompiler/lib/input/IFC/`, 9 classify_*.yaml files, 7 buildings fully onboarded)
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

### Next Gates

| Gate | What | Blocks |
|------|------|--------|
| **FL-2** | **Advisory Output for Designer** — W_Validation_Advisory table + listAdvisories API + Blender panel. Structured flywheel output. Spec: ACTION_ROADMAP.md §FL-2. | — |
| **FL-3** | **CALIBRATE verb** — suggestDimensions(ifcClass) returns typical W/D/H + nearest products. Auto-fill in Designer. | FL-2 |
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
FL-1 (DONE) ──── FL-2 (Advisory table + Designer panel)
                     │
                  FL-3 (CALIBRATE verb)
                     │
                  FL-4 (Relational mining)
```

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

### FL-2: Advisory Output for BIM Designer

**Problem:** Flywheel output is currently log-only. The BIM Designer
cannot read the outlier report — it doesn't know which elements were
flagged or why.

**Solution:** Structured advisory output that the Designer can consume:

| Artefact | Format | Consumer |
|----------|--------|----------|
| `W_Validation_Advisory` table | SQLite in disc_validation.db | Designer API |
| `listAdvisories(buildingId)` | DesignerAPI method (JSON) | Python client |
| Advisory panel in BIM Designer | Blender UI panel | User |

**Schema:**
```sql
CREATE TABLE W_Validation_Advisory (
    advisory_id     INTEGER PRIMARY KEY AUTOINCREMENT,
    building_type   TEXT NOT NULL,
    element_ref     TEXT,           -- which element (NULL for profile-level)
    layer           TEXT NOT NULL,  -- 'DIMENSION', 'PROFILE', 'COMPLIANCE', 'SHAPE' (FL-5/EYES)
    severity        TEXT NOT NULL,  -- 'INFO', 'WARNING', 'SUGGESTION'
    rule_name       TEXT,           -- e.g. 'DIMENSION_RANGE_W:IfcWall'
    message         TEXT NOT NULL,  -- human-readable
    actual_value    REAL,
    expected_min    REAL,
    expected_max    REAL,
    suggestion      TEXT,           -- e.g. 'Nearest typical: 5000mm (from Esplanades)'
    created_at      TEXT DEFAULT (datetime('now'))
);
```

**Designer integration:**
- `listAdvisories(buildingId)` → returns advisories for this building
- Panel: colour-coded list (INFO=blue, WARNING=amber, SUGGESTION=green)
- Click advisory → highlights the element in 3D viewport
- "Apply suggestion" button → auto-adjusts dimension to nearest typical

**Effort:** 1 session. Changes: DV012 migration, DimensionRangeValidator
writes to table, BuildingProfileValidator writes to table, DesignerAPI
+1 method, Python client +1 verb, panel +1 section.

### FL-3: CALIBRATE Verb

**Problem:** When the designer places a new element, they guess the
dimensions. The mined data knows what typical dimensions are for each
IFC class from 20+ real buildings.

**Solution:** A `calibrateDimensions` API method that suggests dimensions:

```
Designer: "I'm placing an IfcColumn on Level 1"
System:   "Typical: 300×300×3400mm (from 10 buildings, 22 observations)"
          "Nearest products: COLUMN_300x300, COLUMN_200x200"
```

**Wire:** PlacementValidatorImpl already has the mined rules loaded.
Add `suggestDimensions(ifcClass)` → returns typical W/D/H ranges +
nearest M_Product matches from component_library.db.

**Effort:** 1 session. Changes: PlacementValidatorImpl +1 method,
DesignerAPI +1 method, Python client +1 verb, panel auto-fill.

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

### FL-5: EYES Integration — Shape-Aware Advisories

**Problem:** The flywheel currently checks dimensions (Layer 1) and
composition (Layer 2). But it cannot say "this IfcWall is actually
shaped like a column" — that requires geometric fingerprinting.

**Solution:** Wire BIMEyes (EYES_SRS.md) into the advisory pipeline.
The EYES module provides 26 proofs across 3 tiers. The key additions:

| EYES Capability | Advisory Value |
|----------------|---------------|
| Shape archetype (PLANAR/ELONGATED/COMPACT) | "This IfcWall is ELONGATED — it's shaped like a beam, not a wall" |
| ProductCategory (10 domain categories) | "This element is classified ARC but its geometry is MEP_ROUTING" |
| P25 ROOM_VALIDITY | "Room 'Kitchen' has 3 walls but no floor slab and no door" |
| P26 BUILDING_COMPLETENESS | "Building has no roof element and no external door" |

**Integration with FL-2 advisory table:**
- EYES proofs write to same `W_Validation_Advisory` table
- Shape mismatches → SUGGESTION severity (with correct category)
- Room/building violations → WARNING severity
- Designer panel shows EYES advisories alongside dimension/profile ones

**Depends on:** EYES_SRS.md Phase 1 (module creation + core types).
The spec defines 3 phases — Phase 1 is 1 session. FL-5 wiring is
an additional session after EYES Phase 1 ships.

**Effort:** 2 sessions total (1 for EYES Phase 1, 1 for FL-5 wiring).

### FL Dependency Chain

```
FL-1 (DONE) → FL-2 (advisory table + Designer panel)
                ↓
              FL-3 (CALIBRATE verb — uses same advisory data)
                ↓
              FL-4 (relational mining — enriches MEP schedules)
                ↓
              FL-5 (EYES integration — shape-aware advisories)
                    ↑
              EYES Phase 1 (module creation — EYES_SRS.md)
```

### FL Designer Development Note

**For the BIM Designer team:** The flywheel validation produces structured
advisories — not just log output. The Designer should:

1. Call `listAdvisories(buildingId)` after loading a building
2. Display advisories in a panel (INFO/WARNING/SUGGESTION colour-coded)
3. Allow click-to-highlight of flagged elements
4. For SUGGESTION-type advisories, offer "Apply" button that calls
   `updateOrderLine()` with the suggested dimension
5. For CALIBRATE, auto-populate dimension fields when placing new elements

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

| Phase | Items | Status | Next action |
|-------|-------|--------|-------------|
| **Core** | WF-01..WF-05, WF-09, WF-10: Phase 1/2 toggle, BOUNDS, focus, restore, detect | CODE DONE | Blender visual test |
| **Peek** | WF-07, WF-08: Properties popup + orientation markers, getElementMetadata verb | CODE DONE | Blender visual test, wire material/cost from library |
| **Add-in-Phase2** | WF-06: New items appear as bbox while existing stays BOUNDS | SPEC ONLY | Wire add_room operator to Phase 2 context |
| **Chain** | WF-11: Chain highlight (getChain verb, system_id query) | STUB | Wire to elements_meta system_id (Federation DB) |
| **Ghost Drag** | WF-12, WF-13: Ghost drag proxy + commit/cancel | SPEC ONLY | Modal operator for drag, bbox proxy tracking |
| **Cost** | WF-14..WF-16: Live cost-of-change during drag | STUB | Wire BOM diff engine + M_Product pricing |
| **CR** | WF-17..WF-20: R_Request change request on cross-discipline move | SPEC ONLY | R_Request table + discipline assignment |
| **Audit** | WF-21..WF-25: AD_ChangeLog, undo, multi-user, sessions | SPEC ONLY | bim_changelog DDL + DAO interceptor |

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
| depth_mm semantics | [LAST_MILE_PROBLEM.md](LAST_MILE_PROBLEM.md) R-17 | Extraction data leaked into product catalog | PENDING — schema fix |
| Vocabulary gaps block real projects | Market Report §6.1 | Each new building type may hit missing verbs | MITIGATED by compound enrichment model |
| TE per-element verification | CP-1 below | Cannot auto-verify all 48K elements | PENDING — element_ref matching |
| DX MIRROR verb | CP-2 below | Duplex 85 axis mismatches pre-existing | PENDING — large effort |

**Position:** "Spatially valid" (proven by 6 mathematical gates). Not yet "construction-ready"
(requires depth_mm fix + verb fidelity closure). The distinction is honest and documented.

---

## Known Debt (ordered by priority)

| # | Item | Severity | Status |
|---|------|----------|--------|
| CP-1 | TE element_ref matching for G3/Totality | HIGH | TODO — critical path |
| CP-2 | DX MIRROR verb + structured BOM | HIGH | TODO — critical path |
| CP-4 | ~~Geometric archetype abstraction (IFC class independence)~~ | ~~HIGH~~ | **DONE** (s46 geometric + s48 semantic). See §CP-4 |
| R17 | Delete 49K I_Element_Extraction from component_library.db | MED | TODO (R20 first) |
| R21 | Extract host_element_ref (IfcRelVoidsElement) | MED | TODO |
| R22 | Extract I_Element_Connectivity | MED | TODO |
| BBC-001 | CLUSTER expandCluster() entry validation | LOW | TODO |
| BBC-002 | BomValidator verb fidelity in compliance report | LOW | TODO |
| AABB-Q | AABB qualifier column (`INNER`/`STRUCTURAL`/`OUTER`/`OPENING`) on `m_bom` | MED | NEW (S42) — see `INNER_SURFACE_ANALYSIS.md` |
| R18 | DROP dead ad_bom/ad_bom_child tables | LOW | TODO |
| R19 | Update ConstructionAsERP.md dual architecture | DOC | TODO |
| VPA-002 | ROUTE per-leg step-uniformity (533 instances) | LOW | Known limit |

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
