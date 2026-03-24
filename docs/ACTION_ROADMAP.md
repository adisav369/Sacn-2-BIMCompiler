# Action Roadmap — BIM Intent Compiler

> **Governing document:** [SystemContract.md](SystemContract.md) — entity registry,
> transaction catalogue, three-concern matrix, gap register.
>
> **Implementation plan:** [ProjectOrderBlueprint.md §14](ProjectOrderBlueprint.md) —
> Sessions 0, A-E with gates, witnesses, failure criteria.

*Quick reference for developers. "Go to the roadmap" = start here.*

---

## Where We Are (S67, 2026-03-24)

**Pipeline:** 9 stages. 64 verbs. 2,475 products. 35 buildings (34 extracted + 1 generative).
**Gates:** 19/34 ALL GREEN. G1-G6 + C8/C9 + W-TOT. Seal v31.
**Tests:** 392 BonsaiBIMDesigner + 5 BackOffice. AddDisciplineTest 4/4.
**Scorecard:** 31/36 (nearest competitor: 9).

---

## What's Next — Quick Navigation

| I need to... | Go to |
|--------------|-------|
| **Understand the system** | [SystemContract.md](SystemContract.md) §1-§2 (recursive principle, entity registry) |
| **Find a spec** | [INDEX.md](INDEX.md) (53 docs by tier) |
| **See what's planned** | [ProjectOrderBlueprint.md §14](ProjectOrderBlueprint.md) (Sessions 0, A-E) |
| **See what's proven** | [PROGRESS.md](../PROGRESS.md) (gate table, session log) |
| **Check test architecture** | [TestArchitecture.md](TestArchitecture.md) (gates, witnesses, anti-drift) |
| **Verify a building** | [TheRosettaStoneStrategy.txt](TheRosettaStoneStrategy.txt) (Tiers 1-4) |
| **Navigate the code** | [SourceCodeGuide.md](SourceCodeGuide.md) (entry points, DAOs) |
| **Onboard a new IFC** | [IFC_ONBOARDING_RUNBOOK.md](IFC_ONBOARDING_RUNBOOK.md) (8-step recipe) |
| **Check what's broken** | §Known Debt below + [SystemContract.md §10](SystemContract.md) (gap register) |
| **Run the pipeline** | [WorkOrderGuide.md](WorkOrderGuide.md) (step-by-step) |
| **Understand ERP mapping** | [ConstructionAsERP.md](ConstructionAsERP.md) (C_Order, three-concern) |
| **See the schema** | [DATA_MODEL.md](DATA_MODEL.md) + [DATABASE_SCHEMA.md](../database/DATABASE_SCHEMA.md) |
| **Check the audit trail** | [AUDIT_S51_FOCUSED.md](AUDIT_S51_FOCUSED.md) (Appendix A-I) |

---

## Implementation Sessions (from Blueprint §14)

| Session | What | Status |
|---------|------|--------|
| **0** | Fix R-PROJ-3 — C_Order_ID collision (parameterize BomDropper) | NOT STARTED |
| **A** | Add mutation — addDiscipline() rule-driven OrderLines | **DONE** (S67 `fac5e8f`) |
| **B** | Validation-as-suggestion — OrderLineMutation interface | NOT STARTED |
| **C** | Rule packs — NFPA/UBBL/IBC as importable jurisdictions | NOT STARTED |
| **D** | Remove + Compress — qty=0 skip, reference class | NOT STARTED |
| **E** | Order inheritance — Ref_Order_ID, stacked overlays | NOT STARTED |

**C_Project (§2):** CTFL test plan in §2.1. Site layout as warehouse put-away in §2.2.
R-PROJ-3 (Session 0) must be fixed before C_Project work.

---

## Critical Path Items

| Item | Status | Spec |
|------|--------|------|
| CP-1: TE per-element verification | **DONE** (S66) | W-TOT 48428/48428, MA identity |
| CP-2: DX MIRROR verb | DEFERRED | 85 axis mismatches, large effort |
| CP-3: IFC onboarding pipeline | **DONE** (S42) | 8-step runbook |
| CP-4: Geometric archetype | **DONE** (S46-S50) | 23 geometric + 20 semantic switches replaced |

---

## Completed Tracks (reference)

| Track | Sessions | What |
|-------|----------|------|
| Phases 0-A | s1-s22 | BOM walk, EN-BLOC, gate convergence, geometry fidelity |
| Phase B | s23-s38 | Terminal 48K, verb factorization, BIM_COBOL |
| Phase G (Designer) | s15-s50 | G-1 through G-10 + FL-1/FL-2/FL-5 DONE |
| Phase SRS | s25-s30 | BBC.md, G4_SRS, TACK_FIX, DocValidate, traceability |
| S51 Audit | s51 | 8 P0, 9 P1, 9 P2. All P0 fixed. Report: AUDIT_S51_FOCUSED.md |
| S58-S60 | s58-s60 | 34 buildings, ERP alignment, C_OrderLine, work orders |
| S64-S65 | s64-s65 | AD Dictionary Steps 0-3, M_Product migration |
| S66 | s66 | Task 4A discipline wiring, CP-1 closure |
| S67 | s67 | ELEC onboarding, watchdog cleanup, Session A, specs consolidation |

---

## Designer Gates

| Gate | Session | Status |
|------|---------|--------|
| G-1..G-10 | s15-s47 | **ALL DONE** |
| FL-1..FL-2..FL-5 | s47-s50 | **ALL DONE** |
| FL-4 (Relational mining) | — | NOT STARTED |
| G-11 (ParametricMesh UI) | — | NOT STARTED |
| G-12 (Text Mode) | — | NOT STARTED |
| G-13 (Click-to-Place interactive) | — | NOT STARTED |

---

## Known Debt

*See also [SystemContract.md §10](SystemContract.md) for system-level gaps (GAP-SC-1..8).*

| # | Item | Severity | Status |
|---|------|----------|--------|
| R-PROJ-3 | C_Order_ID = docTypeId collision — blocks multi-order | **BLOCKING** | Session 0 (Blueprint §14.3) |
| S60-6 | M_BomCategory → M_Product_Category (77 files) | HIGH | ASSESSED — defer (orthogonal to launch) |
| CP-2 | DX MIRROR verb + structured BOM | MED | DEFERRED — quality-of-proof, not correctness |
| R22 | Extract I_Element_Connectivity | MED | TODO — enables future MEP routing |
| BBC-001 | CLUSTER expandCluster() entry validation | LOW | BACKLOG |
| BBC-002 | BomValidator verb fidelity in compliance report | LOW | BACKLOG |
| R19 | Update ConstructionAsERP.md dual architecture | DOC | BACKLOG |
| VPA-002 | ROUTE per-leg step-uniformity (533 instances) | LOW | KNOWN LIMIT |

---

## Go-to-Market Timeline

*Source: [BIM_Compiler_Market_Impact_Report.pdf](BIM_Compiler_Market_Impact_Report.pdf)*

| Phase | Target | Key Actions |
|-------|--------|-------------|
| **Q2 2026** | GitHub public release | 3+ Rosetta Stones, Challenge Paper, Bonsai demo |
| **Q3 2026** | Malaysia pilot | CIDB BIM lab, affordable housing showcase |
| **Q4 2026** | Academic | Automation in Construction journal, buildingSMART summit |
| **2027** | Scale | Community vocabulary addons, infrastructure extension, training |

---

## WF-BB Roadmap (Wireframe-First)

| Phase | Status |
|-------|--------|
| Core (WF-01..05, 09, 10) + Peek (WF-07, 08) | CODE DONE |
| Add-in-Phase2, Chain, Ghost Drag, Cost, CR, Audit | SPEC ONLY — post-launch |

Full backlog: [BIM_Designer_SRS.md §26](BIM_Designer_SRS.md).
