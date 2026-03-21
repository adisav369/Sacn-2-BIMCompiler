# Action Roadmap — BIM Intent Compiler

> **Foundation:** [BBC](BOMBasedCompilation.md) · [DATA_MODEL](DATA_MODEL.md) · [BIM_COBOL](BIM_COBOL.md) · [ConstructionAsERP](ConstructionAsERP.md) · [TestArchitecture](TestArchitecture.md) · [SourceCodeGuide](SourceCodeGuide.md)

*Updated: 2026-03-21 (session 44). Previous version archived in git history.*

## Current Position

**Pipeline proven.** Twelve Rosetta Stone buildings compile correctly:
- SH (55 elements): 10/10 PASS — fully verified
- DX (1099 elements): 8/10 PASS — MIRROR debt (pre-existing)
- FK (82 elements), BR (48), RD (53), RL (73): infrastructure verified
- TE (48,428 elements): 9/10 PASS — output correct, per-element verification limited at scale
- BA (11), BH (5), BS (16), IP (27): PCERT IFC4x3 — onboarded session 44, all PASS

63 verbs. 9-stage pipeline. 196 witnesses. 823 products. 4-DB architecture.
BIM Designer: 248/248 GREEN. BackOffice: 19/19 GREEN. G-1 through G-7 DONE.

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

| Phase | What | Files | Effort |
|-------|------|-------|--------|
| **4a** | Add `shape_archetype` + `scale_band` computed columns to `m_bom_line` at IFCtoBOM time | `ExtractionPopulator`, `ScopeBomBuilder`, `DisciplineBomBuilder`, migration SQL | Small |
| **4b** | Replace 12 geometric decision points with archetype switches | `PlacementProver` (P10, overlap, Z-band), `SpatialDiff`, `MeshBinder`, `GeometryIntegrityChecker` | Medium |
| **4c** | Wire compiler's semantic decisions to component library product properties instead of IFC class | `BuildingWriter` (element_type, GUID prefix), `ProductRegistrar` (product_type), `BOMExporter` (UOM) | Medium |
| **4d** | Move QTO/costing rates to authority data table (`ad_check_threshold` or new `ad_cost_rate`) | `BuildingWriter` (CIDB rates) | Small |
| **4e** | Add `BomValidator.checkShapeConsistency()` as pre-commit gate in IFCtoBOM pipeline | `BomValidator`, `IFCtoBOMPipeline` | Small |

**Gate:** P10_SHAPE_IDENTITY PASS for all buildings. Zero `switch(ifcClass)` in compiler
runtime (DAGCompiler). IFC class used only in IFCtoBOM (extraction metadata) and output
(traceability). TE Metal Deck correctly classified from library, not from IFC label.

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

### Next Gates

| Gate | What | Blocks |
|------|------|--------|
| **G-8** | **BlenderBridge pipe** — TCP Snap → PlacementValidator in real-time. Incremental viewport delta. Spec: `BlenderBridge.md`. | G-10, G-13 |
| **G-9** | **ORDER View + BOM Outliner** — Tabular editor + relational tree. Three views share C_OrderLine. Schema-Not-Geometry: edits are FK/ASI, compiler renders. | — |
| **G-10** | **Promote to BOM** — C_OrderLine → m_bom + m_bom_line. Dangles check, owner sign-off, entity_type='U', provenance='GENERATIVE'. | — |
| **G-11** | **ParametricMesh UI** — Blender panel exposing slider params. Construction-grade: BOM sub-assembly with tack I/O. | — |
| **G-12** | **Text Mode** — Search box + future NL → OrderLine + ASI. | — |
| **G-13** | **Click-to-Place** — Interactive discipline placement. User selects discipline, clicks area, rules auto-place. Spec: `BIM_Designer.md` §18.8. | — |

### Dependency Chain

```
G-1..G-7 (DONE) ─── G-8 (BlenderBridge) ─── G-10 (Promote) ─── G-13 (Click-to-Place)
                     │
                     G-9 (ORDER View)
                     G-11 (ParametricMesh)
                     G-12 (Text Mode)
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
| IFC class used as decision variable | [LAST_MILE_PROBLEM.md](LAST_MILE_PROBLEM.md) §Geometric Fingerprint, CP-4 | 43 switch points on IFC class in compiler; TE 33K mislabelled | PENDING — CP-4 phases 4a–4e |
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
| CP-4 | Geometric archetype abstraction (IFC class independence) | HIGH | Phase 4a–4e. S44 foundation delivered (P10, fingerprint). See §CP-4 |
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
