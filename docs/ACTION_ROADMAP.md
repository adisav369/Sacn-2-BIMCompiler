# Action Roadmap — BIM Intent Compiler

> **Governing documents:** [MANIFESTO.md](MANIFESTO.md) (ERP world view) ·
> [ProjectOrderBlueprint.md §14](ProjectOrderBlueprint.md) (session plan) ·
> [SpecsAnalysis.txt](SpecsAnalysis.txt) (gap analysis)

---

## Where We Are (S83, 2026-03-26)

**Pipeline:** 9 stages. 64 verbs. 2,475 products. 35 buildings (34 extracted + 1 generative).
**Gates:** 19/34 ALL GREEN. G1-G6 + C8/C9 + W-TOT. Seal v6.
**Tests:** 408 BonsaiBIMDesigner (42 classes) + 5 BackOffice.
**iDempiere conformance:** Tier 1 done (Name/Value on INTEGER PK tables). Tier 2 pending (TEXT→INTEGER PK).

---

## Quick Navigation

| I need to... | Go to |
|--------------|-------|
| **Understand the system** | [MANIFESTO.md](MANIFESTO.md) (recursive BOM principle, entity registry) |
| **Find a spec** | [INDEX.md](INDEX.md) (55 docs by tier) |
| **See what's proven** | [PROGRESS.md](../PROGRESS.md) (gate table, session log) |
| **Navigate the code** | [SourceCodeGuide.md](SourceCodeGuide.md) (entry points, DAOs) |
| **Run the pipeline** | [WorkOrderGuide.md](WorkOrderGuide.md) (step-by-step) |
| **Onboard a new IFC** | [IFC_ONBOARDING_RUNBOOK.md](IFC_ONBOARDING_RUNBOOK.md) (8-step recipe) |
| **See the schema** | [DATA_MODEL.md](DATA_MODEL.md) + [DATABASE_SCHEMA.md](../database/DATABASE_SCHEMA.md) |

---

## Phase E — ERP Alignment (DONE)

Blueprint Sessions 0, A–F complete. iDempiere entity model aligned.

| Session | Deliverable | Gate |
|---------|------------|------|
| 0 | R-PROJ-3 fix (C_Order_ID collision) | GAP-SC-8 CLOSED. [Appendix K](AUDIT_S51_FOCUSED.md) |
| A | addDiscipline() + OrderMutationService | AddDisciplineTest 4/4. [Appendix I](AUDIT_S51_FOCUSED.md) |
| B | OrderLineMutation interface (Replace, Add, Remove, Compress) | OrderLineMutationTest 8/8. [Appendix L](AUDIT_S51_FOCUSED.md) |
| C | Rule pack framing (pack_id on AD tables) | RulePackTest 6/6. [Appendix M](AUDIT_S51_FOCUSED.md) |
| D | Remove + Compress mutations | RemoveCompressTest 5/5. [Appendix Q](AUDIT_S51_FOCUSED.md) |
| E | Order inheritance (Ref_Order_ID) | OrderInheritanceTest 6/6. [Appendix S](AUDIT_S51_FOCUSED.md) |
| F | DiffVerb + Callout (AD_Rule) | DiffVerbTest 5/5. [Appendix T](AUDIT_S51_FOCUSED.md) |

AD Dictionary migration (S64–S79): TEXT discipline → `Discipline` enum + `AD_Org_ID` FK. 6 steps done. Remaining: drop vestigial TEXT columns.

---

## Phase F — Verb Wiring (NEXT)

| Item | What | Blocker | Ref |
|------|------|---------|-----|
| TRIM-1 | Wire `TRIM WALLS TO ROOF` in pipeline. Add to `.bimcobol` + create DemoHouse_2BR.bimcobol | — | [SpecsAnalysis §21](SpecsAnalysis.txt), [BIM_COBOL.md §TRIM](BIM_COBOL.md) |
| TRIM-2 | End-to-end witness: BOM Drop SH → swap roof → TRIM fires → curtain wall trimmed | TRIM-1 | [SpecsAnalysis §22](SpecsAnalysis.txt), [BBC.md §Selection](BOMBasedCompilation.md) |
| CP-2 | DX MIRROR verb (85 axis mismatches) | Large effort | [DuplexAnalysis.md](DuplexAnalysis.md), [TestArchitecture.md](TestArchitecture.md) |

---

## Phase G — Format Convergence (DEFERRED)

| Item | What | Ref |
|------|------|-----|
| FMT-1 | Evaluate retiring DSL for generative buildings. GRID axis algebra must port to YAML first | SpecsAnalysis §23 |

---

## Phase H — C_Project & Enterprise (DEFERRED)

| Item | What | Blocker |
|------|------|---------|
| GAP-SC-3 | Site grid generation — subdivide site_aabb into plots using [terrain](PDF_TERRAIN.md) topology | C_Project |
| GAP-SC-6 | Compile-once-copy-many — reference class performance (qty=180) | C_Project at scale |
| GAP-SC-7 | Output consolidation — per-building vs consolidated output.db | C_Project |
| ENT-1 | ModelValidator alignment (processIt → beforeSave/afterSave hooks) | Enterprise multi-user | SpecsAnalysis §15 |
| ENT-2 | Federated Model spatial DB (93% memory reduction on 93K elements) | Enterprise scale |

---

## Open Gaps

| # | Area | What's Missing | Status |
|---|------|---------------|--------|
| GAP-SC-1 | ASI mutation → recompile | Which verbs re-fire? Transaction flow spec | HIGH (blocks viewport drag) |
| GAP-SC-2 | Freehand drawing → BOM | How viewport geometry becomes a BOM mutation | MED (blocks freehand mode) |
| GAP-SC-4 | Rule pack versioning | Effectivity dates, version precedence. pack_id done (S67c) | MED (partially addressed) |
| R22 | I_Element_Connectivity | Extract linking table from IfcRelConnectsElements | TODO |
| VPA-002 | ROUTE step-uniformity | 533 instances with non-uniform per-leg steps | KNOWN LIMIT |
| IDV-1 | iDempiere _ID/Name/Value | Tier 2: TEXT→INTEGER PK on core tables (M_Product, m_bom, c_order) | [ID_NAME_VALUE_STUDY.md](ID_NAME_VALUE_STUDY.md) |

---

## Schema Migration Backlog

From [SpecsAnalysis.txt](SpecsAnalysis.txt) §10 and [ID_NAME_VALUE_STUDY.md](ID_NAME_VALUE_STUDY.md):

| Step | What | Status |
|------|------|--------|
| 1. Seed AD_Org summary rows (MEP, ALL) | Grouped discipline hierarchy | PENDING |
| 2. AD_Org_ID FK on m_bom, C_OrderLine | Integer FK for discipline | **DONE** (S78: W009) |
| 3. Backfill AD_Org_ID from bom_category | Data migration | **DONE** (S78: W009) |
| 4. Retire deriveDiscipline() from compile | Extraction-only fallback | **DONE** (S79) |
| 5. Drop bom_category string columns | Schema cleanup | PENDING |
| 6. Drop DocBaseType/DocSubType columns | Schema cleanup | PENDING |
| 7. Drop Parent_Category_ID | Flat M_Product_Category (iDempiere standard) | DECIDED, PENDING |
| 8. Tier 1: Name/Value on INTEGER PK tables | 43 tables, 5 migrations | **DONE** (S83) |
| 9. Tier 2: TEXT→INTEGER PK on core tables | M_Product, m_bom, c_order, C_DocType | NOT STARTED (high risk) |

---

## Critical Path (reference)

| Item | Status | Spec |
|------|--------|------|
| CP-1: TE per-element verification | **DONE** (S66) | [TestArchitecture.md](TestArchitecture.md) W-TOT 48428/48428 |
| CP-2: DX MIRROR verb | DEFERRED → Phase F | [DuplexAnalysis.md](DuplexAnalysis.md) — 85 axis mismatches |
| CP-3: IFC onboarding pipeline | **DONE** (S42) | [IFC_ONBOARDING_RUNBOOK.md](IFC_ONBOARDING_RUNBOOK.md) |
| CP-4: Geometric archetype | **DONE** (S46-S50) | [TerminalAnalysis.md](TerminalAnalysis.md) — 23 geometric + 20 semantic switches |

## Completed Tracks

| Track | Sessions | Deliverable |
|-------|----------|-------------|
| Phases 0-A | s1-s22 | BOM walk, EN-BLOC, gate convergence, geometry fidelity |
| Phase B | s23-s38 | Terminal 48K, verb factorization, [BIM_COBOL](BIM_COBOL.md) |
| Phase G (Designer) | s15-s50 | G-1 through G-10 + FL-1/FL-2/FL-5 |
| Phase SRS | s25-s30 | [BBC.md](BOMBasedCompilation.md), [TestArchitecture.md](TestArchitecture.md), [DocValidate.md](DocValidate.md), traceability |
| S51 Audit | s51 | 8 P0, 9 P1, 9 P2. All P0 fixed. [AUDIT_S51_FOCUSED.md](AUDIT_S51_FOCUSED.md) |
| S58-S60 | s58-s60 | 34 buildings, ERP alignment, [C_OrderLine](ProjectOrderBlueprint.md), construction orders |
| S64-S79 | s64-s79 | [AD Dictionary](DISC_VALIDATION_DB_SRS.md) Steps 0-6, Discipline enum, AD_Org_ID FK |
| S80-S83 | s80-s83 | Docs readability, stale ref cleanup, [Name/Value Tier 1](ID_NAME_VALUE_STUDY.md) |

---

## Designer Gates

| Gate | Status |
|------|--------|
| G-1 through G-10, FL-1/FL-2/FL-5 | **ALL DONE** |
| FL-4 (Relational mining) | NOT STARTED |
| G-11 (ParametricMesh UI) | NOT STARTED |
| G-12 (Text Mode) | NOT STARTED |
| G-13 (Click-to-Place interactive) | NOT STARTED |

---

## Go-to-Market

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
