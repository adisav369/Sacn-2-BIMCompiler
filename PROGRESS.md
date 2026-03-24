# PROGRESS — Current Development State

> **⛔ Don't run ANY git command on `library/component_library.db`.** No stash, checkout, restore, reset. S63 lost 2400+ products this way.

> **Rule:** PROGRESS.md is a thin status file. No specs here — specs live in `docs/` and PROGRESS
> links to them. Keep this file under 80 lines.

## Current State

**Gate:** `./scripts/run_RosettaStones.sh` — 19/34 ALL GREEN. Remaining debt: G5 GEO_ (RA/JE/ES), C9 axis swaps (JE/HI/SC/RM).

| Gate | SH | FK | IN | DX | TE | DM |
|------|----|----|----|----|----|----|
| G1-COUNT | PASS (55) | PASS (82) | PASS (699) | PASS (1099) | PASS (48428) | PASS (60) |
| G2-VOLUME | PASS | PASS | PASS | PASS | PASS | — |
| G3-DIGEST | PASS | PASS | PASS | PASS | PASS | — (GENERATIVE) |
| G4-TAMPER | PASS | PASS | PASS | PASS | PASS | PASS |
| G5-PROVENANCE | PASS | PASS | PASS | PASS | PASS | PASS |
| G6-ISOLATION | PASS | PASS | PASS | PASS | PASS | PASS |

**Pipeline:** 9 stages. 64 verbs. 2475 products. 4-DB architecture. 4D/5D/6D live.
**Rosetta Stones:** 35 buildings (34 EXTRACTED + 1 GENERATIVE). 19 ALL GREEN. [TestArchitecture.md §Coverage](docs/TestArchitecture.md#rosetta-stone-coverage-s58c).
**Tests:** BIMBackOffice 5/5. BonsaiBIMDesigner 408/414 (42 classes, 6 CalibrationTest pre-existing).
**BIMEyes:** 28 proof classes. [EYES_SRS.md §10](docs/EYES_SRS.md#10-audit-finding-proof-coverage-honesty-s60-post-audit).

## What's Next

**Blueprint Sessions (§14.3):** [ProjectOrderBlueprint.md](docs/ProjectOrderBlueprint.md)
  Session 0 DONE — R-PROJ-3 fix (GAP-SC-8 CLOSED). [AUDIT Appendix K](docs/AUDIT_S51_FOCUSED.md).
  Session A DONE — addDiscipline() + OrderMutationService. [AUDIT Appendix I](docs/AUDIT_S51_FOCUSED.md).
  Session B DONE — OrderLineMutation interface + 3 suggestions. [AUDIT Appendix L](docs/AUDIT_S51_FOCUSED.md).
  Session C DONE — Rule pack framing (pack_id on 4 AD tables). [AUDIT Appendix M](docs/AUDIT_S51_FOCUSED.md).
  Session D DONE — Remove + Compress mutations. W005 migration. RemoveCompressTest 5/5. [AUDIT Appendix Q](docs/AUDIT_S51_FOCUSED.md).
  Session E DONE — Order inheritance. W006 migration (Ref_Order_ID). InheritanceResolver + dropWithInheritance. OrderInheritanceTest 6/6. GAP-SC-5 CLOSED.
  Session F DONE — DiffVerb + Callout (§9). W007 migration (AD_Rule). DiffVerbService + CalloutEngine. DiffVerbTest 5/5. [AUDIT Appendix T](docs/AUDIT_S51_FOCUSED.md).

**AD Dictionary (S62→S65):** Steps 0–3 DONE. Next: Step 4–6. [DISC_VALIDATION_DB_SRS.md §11](docs/DISC_VALIDATION_DB_SRS.md#1165-migration-sequence-6-steps-each-independently-committable).

**Docs site:** https://red1oon.github.io/BIMCompiler/ — 50 specs, mkdocs-material.

**Watchdog findings:** [AUDIT_S51_FOCUSED.md Appendix I–Q](docs/AUDIT_S51_FOCUSED.md).
**MANIFESTO:** [docs/MANIFESTO.md](docs/MANIFESTO.md) — ERP world view, mandatory first read.

## Session Log (recent first)

**S73** — CO_EmptySpaceLine → compiler-internal. Phase 1: 15 docs aligned (WHERE = M_BOM_Line dx/dy/dz). Phase 2: 4 PO classes @Deprecated, all consumer javadoc/comments updated, SpatialDigest + VerifyPlacementVerb @Deprecated. Phase 3 deferred (tables still needed by pipeline). AUDIT docs untouched (historical).
**S72** — Session F: DiffVerb + Callout (§9). W007 migration (AD_Rule callout table). DiffVerbService (records DIFF PP_Order_Node + delta params). CalloutEngine (topo-sort rule evaluation). DiffVerbTest 5/5. [AUDIT Appendix T](docs/AUDIT_S51_FOCUSED.md).
**S68e** — Session E: Order inheritance. DV017 applied all DBs. W006 migration (Ref_Order_ID). InheritanceResolver (chain walk + exception collect). BomDropper.dropWithInheritance. OrderInheritanceTest 6/6. MANIFESTO.md reorder + category triage.
**S68b** — Session D: Remove + Compress mutations. W005 migration (locator_ref + is_reference_class). BomDropper exception-order support. RemoveCompressTest 5/5. [AUDIT Appendix Q](docs/AUDIT_S51_FOCUSED.md).
**S68** — M_BomCategory → M_Product_Category rename. DV017 migration. 14 docs + 28 Java files + schema_snapshot. [AUDIT Appendix P](docs/AUDIT_S51_FOCUSED.md).
**S67w** — Watchdog: ConstructionAsERP purge (80+ refs, 44 docs). MANIFESTO.md. IsBOM audit (no drift). [AUDIT Appendix N+O](docs/AUDIT_S51_FOCUSED.md).
**S67c** — Session C: Rule pack framing. DV016 pack_id migration. MY=13, US=17 proposals. RulePackTest 6/6. [AUDIT Appendix M](docs/AUDIT_S51_FOCUSED.md).
**S67b** — Session B: OrderLineMutation engine. 3 implementations. OrderLineMutationTest 8/8. [AUDIT Appendix L](docs/AUDIT_S51_FOCUSED.md).
**S67** — ELEC onboarding + Session A + watchdog + SystemContract.md + Rosetta Dictionary + mkdocs site. [AUDIT Appendix I](docs/AUDIT_S51_FOCUSED.md).
**S66** — Task 4A (discipline wiring) + CP-1 (TE per-element verified). [ACTION_ROADMAP.md](docs/ACTION_ROADMAP.md).
**S65** — DV015 M_Product migration. [AUDIT §Step 3](docs/AUDIT_S51_FOCUSED.md#step-3-implementation-audit-s65-2026-03-24).
**S64** — AD Dictionary Steps 0–2. [DISC_VALIDATION_DB_SRS.md §11](docs/DISC_VALIDATION_DB_SRS.md#11-investigation-report--application-dictionary-database-placement-s64).
**S60** — ERP Model Alignment (BomDropper + OrderLineWalker). [archive/S60_ERP_ALIGNMENT.md](docs/archive/S60_ERP_ALIGNMENT.md).
**S59** — Work Order path + HTML↔Bonsai sync. WorkOrderCompileTest 6/6.
**S58** — Gate debt cleanup + DM generative path + 9 buildings full pipeline.
**S57** — 1D Order Configurator. 34/34 compiled.
*Earlier: S39–S56 — ASI, WALK-THRU, focused audit, BIMEyes, 6D/7D, Web UI, BOM Drop.*
