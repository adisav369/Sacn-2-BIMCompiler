# Action Roadmap — BIM Intent Compiler

*Updated: 2026-03-20 (session 37b). Previous version archived in git history.*

## Current Position

**Pipeline proven.** Three Rosetta Stones compile correctly:
- SH (55 elements): 10/10 PASS — fully verified
- DX (1099 elements): 7/10 PASS — MIRROR debt (pre-existing)
- TE (48,428 elements): 9/10 PASS — output correct, per-element verification limited at scale

63 verbs. 9-stage pipeline. 196 witnesses. BOM factorization 42.8:1.
BIM Designer: G-1 through G-7 DONE. 166/166 GREEN.

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

### CP-3: New IFC Onboarding Pipeline

**Problem:** Adding a new IFC building requires YAML config, extraction, BOM
generation, and verification. The process works (dynamic building registration via
`construction_manifest.yaml`) but is undocumented as a runbook.

**Fix:** Write a step-by-step runbook: IFC → extract.py → YAML → IFCtoBOM → verify.
Include expected gate results for different building scales.

**Effort:** Small. Documentation only.

**Gate:** A new building can be onboarded by following the runbook without code changes.

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

## Known Debt (ordered by priority)

| # | Item | Severity | Status |
|---|------|----------|--------|
| CP-1 | TE element_ref matching for G3/Totality | HIGH | TODO — critical path |
| CP-2 | DX MIRROR verb + structured BOM | HIGH | TODO — critical path |
| R17 | Delete 49K I_Element_Extraction from component_library.db | MED | TODO (R20 first) |
| R21 | Extract host_element_ref (IfcRelVoidsElement) | MED | TODO |
| R22 | Extract I_Element_Connectivity | MED | TODO |
| BBC-001 | CLUSTER expandCluster() entry validation | LOW | TODO |
| BBC-002 | BomValidator verb fidelity in compliance report | LOW | TODO |
| R18 | DROP dead ad_bom/ad_bom_child tables | LOW | TODO |
| R19 | Update ConstructionAsERP.md dual architecture | DOC | TODO |
| VPA-002 | ROUTE per-leg step-uniformity (533 instances) | LOW | Known limit |

---

## Spec Index

| Spec | What |
|------|------|
| `BOMBasedCompilation.md` | MASTER SPEC: tack, walker, BUFFER, gospel |
| `BIM_Designer.md` | GUI, ASI, 4-action persistence, Design Mode |
| `BIM_Designer_SRS.md` | UX requirements, user journeys, state machine |
| `ASSEMBLY_BUILDER_SRS.md` | G-7 SRS: layer-by-layer TACK, U-value |
| `G4_SRS.md` | work_output.db, master-detail, AP gate |
| `TACK_FIX_SPEC.md` | FIX-1/2/3 method specs |
| `TestArchitecture.md` | G1-G6 gates, tamper seal, traceability |
| `DocValidate.md` | AD_Val_Rule, 3-tier validation |
| `DISC_VALIDATION_DB_SRS.md` | disc_validation.db phases |
| `BlenderBridge.md` | Java-smart/Python-dumb pipe |
| `LAST_MILE_PROBLEM.md` | Gaps 1-8, R1-R27 actions |
| `SourceCodeGuide.md` | Code navigation, entry points |
