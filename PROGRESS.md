# PROGRESS — Current Development State

> **⛔ Don't run ANY git command on `library/component_library.db`.** No stash, checkout, restore, reset. S63 lost 2400+ products this way.

> **Rule:** PROGRESS.md is a thin status file. No specs here — specs live in `docs/` and PROGRESS
> links to them. Keep this file under 80 lines.

## Current State

**Gate:** `./scripts/run_RosettaStones.sh` — 19/34 ALL GREEN (pre-S96). S96-p0 unblocked 11 DocType-blocked buildings — recount pending. 3 regressions open: DX (severe coordinate failure), IN (44 windows shifted), RM (stair miscompilation).

| Gate | SH | FK | IN | DX | TE | DM |
|------|----|----|----|----|----|----|
| G1-COUNT | PASS (55) | PASS (82) | PASS (699) | PASS (1099) | PASS (48428) | PASS (60) |
| G2-VOLUME | PASS | PASS | PASS | PASS | PASS | — |
| G3-DIGEST | PASS | PASS | PASS | PASS | PASS | — (GENERATIVE) |
| G4-TAMPER | PASS | PASS | PASS | PASS | PASS | PASS |
| G5-PROVENANCE | PASS | PASS | PASS | PASS | PASS | PASS |
| G6-ISOLATION | PASS | PASS | PASS | PASS | PASS | PASS |

**Pipeline:** 9 stages. 76 verbs. 2475 products. 4-DB architecture. 4D/5D/6D live.
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

**AD Dictionary (S62→S65):** Steps 0–5 DONE. Step 5-6 bulk migration DONE (S79). Step 6 partial: doc_base_type DROPPED (S84, W012). doc_sub_type stays (STRUCTURAL). [DISC_VALIDATION_DB_SRS.md §11](docs/DISC_VALIDATION_DB_SRS.md#1165-migration-sequence-6-steps-each-independently-committable).

**Docs site:** https://red1oon.github.io/BIMCompiler/ — 55 specs, mkdocs-material.

**Watchdog findings:** [AUDIT_S51_FOCUSED.md Appendix I–U](docs/AUDIT_S51_FOCUSED.md).
**MANIFESTO:** [docs/MANIFESTO.md](docs/MANIFESTO.md) — ERP world view, mandatory first read.

## Session Log (recent first)

**S99-forge** — Geometry Forge scaffolding + 5 starter pieces (SLOPE_CUT, STAIR_FLIGHT, PIPE_BEND, DOME_SECTION, BARREL_VAULT). ForgeVerb (76th verb). W-FORGE-1..8 8/8 PASS. ParametricMesh deprecated.
**S96-docs** — Docs tightening + 2D layout images + ShipYard spec.
**S96-p0** — Fix DocType null regression: DisciplineBomBuilder null→config.docBaseType(). seed_dm_bom.sql aligned. Unblocks 11 CO/IN buildings.
**S95-trim** — Rewrite TRIM WALLS TO ROOF: roofSurfaceZ() replaces tent model. SH barrel vault: 2 walls trimmed. W-TRIM-7 added. 7/7 PASS.
**S95-asi** — ASI attribute detail tables (M_Attribute 18, M_AttributeUse 29, M_AttributeValue 15). Column.Callout spec (DocValidate §1.5). Product→Verb routing spec (BBC §3.5.2).
**S95-esline** — Remove ESLine concept: 3 dead Java files, 14 docs, -2824 lines. Placement is M_BOM_Line dx/dy/dz tack only.
**S94-c_order** — Fix C_Order pipeline: copyCOrderToOutput() direct SQL (was SPI dispatch, BIM_COBOL not on classpath). T16 tamper rule exemption for CompilationPipeline.java (authorized write path, same as T17/ElementPersistence). Every compiled building now produces 1 c_order row (SH/DX/FK/IN verified). SH 7/7 PASS + FK 7/7 PASS.
**S92-tier2d** — Tier 2 Phase D: Drop `_int` sidecar columns. DV024 (ERP.db) + CL005 (component_library.db) migrations — `M_Product_Category_ID_int` column + index dropped. TEXT FK audit: all 6 TEXT FK columns still actively used in production (deferred to Phase E). c_order 0 rows: root cause is BIM_COBOL SPI not on DAGCompiler classpath (deferred — T16 tamper rule blocks direct SQL fix). ACTION_ROADMAP updated. `mvn compile -q` PASS + SH 7/7 PASS.
**S91-tier2c** — Tier 2 Phase C: Java INTEGER PK migration. IFCtoBOM DDL nativized (M_Product_ID, M_BOM_ID, C_DocType_ID all INTEGER PK AUTOINCREMENT). Value/Name backfill in IFCtoBOM pipeline. C_OrderLine persisted in output.db (37 rows for SH — was 0). prepare_compile_db() ALTER TABLE workaround removed. ORM accessors added (X_M_BOM, X_M_BOMLine). BuildSpatialStructureVerb fixed (was using getInt on TEXT). All `_int` sidecar columns eliminated (C_DocType, C_Order, M_Product_Category → proper INTEGER PK in snapshot). `mvn compile -q` PASS + SH 7/7 PASS.
**S90-tier2** — Tier 2: INTEGER PK on 5 core tables (Phase A+B, schema only). 8 migration SQL files. ERP.db + CL applied directly; BOM.db via prepare_compile_db ALTER TABLE (IFCtoBOM Java DDL is hardcoded). 3 pre-existing fixes: snapshot C_OrderLine stale (S78), singularity doc_base_type (S84), G6 co_empty_space stub (S74). `mvn compile -q` PASS + SH 7/7 PASS.
**S89-trim1** — Wire TRIM WALLS TO ROOF: VerbRegistry (74→75), SH + DM .bimcobol, BIM_COBOL.md §17. Stale verb count sweep (64→75 across 13 files). 6 witnesses pass.
**S88-study** — validation.db merge study. Name collision blocker (ad_val_rule in both DBs, incompatible schemas). Decision: DO NOT MERGE. Architecture is 5-DB (4+1), not 4.
**S88-links** — mkdocs link fixes. 130→0 build warnings. Tracked docs already fixed by prior sessions; archive docs local-only.
**S87-ctfl** — CTFL spec review. 3 stale fixes (RosettaStone count, 5→4 DB, DISC_BOM_DESIGN→DISC_VALIDATION_DB_SRS). validation.db finding spawned S88-study.
**S86-ddl** — Remove doc_base_type from output.db DDL. PP_Order_Node → W_Verb_Node rename.
**S86-watchdog** — SpecsAnalysis blanked (processed). ACTION_ROADMAP refs repointed. WorkOrderGuide links to GitHub URLs.
**S85-headlines** — Callout boxes on 9 major specs + BBC.
**S85-cleanup** — Archive CORE_SRS.md. BIM_Designer_SRS §25 labelled deferred.
**S84-cleanup** — Drop doc_base_type from m_bom (W012 migration, 14 files). doc_sub_type stays (STRUCTURAL — variant scoping SH/DX/FK).
**S83-watchdog** — Roadmap rewrite, nav cleanup, doc count fix.
**S83-tier1** — Name/Value columns on INTEGER PK tables (5 migrations, 43 tables). iDempiere Tier 1 conformance.
**S82-cleanup** — work_output.db doc propagation (26 files, ~90 refs).
**S82** — Doc review: 5→4 DB, SQL bom_category→m_product_category_id, AUDIT §U.3.4 CLOSED.
**S81-upkeep** — Broken links + stale refs: SystemContract→MANIFESTO, DiscValidation→ERP, counts.
**S81** — Docs readability pass 2. BBC.md: "Gospel Principle" → "Compilation Model", bom_category → AD_Org, work_output.db removed, Onboarding Gotchas → WorkOrderGuide pointer, §11 tightened (-58 lines). DATA_MODEL.md: §6-7 cleaned (stale investigation framing removed, migration status consolidated, -101 lines). 3 specs updated (DISC_VALIDATE_SRS, DocAction_SRS, GENERATIVE_HOUSE_SRS): bom_category → AD_Org_ID, work_output.db → compile DB. SpecsAnalysis §20-23 decisions recorded. ACTION_ROADMAP: TRIM-1/2 + FMT-1 added to gap register. CLAUDE.md: 49→44 lines.
**S80** — Docs readability pass 1. Stale stats (63→64 verbs, 22→19 ALL GREEN, 392→408 tests). BBC header rewritten. MANIFESTO tightened. SystemContract archived. 3 new specs (2D_LAYOUT, PDF_TERRAIN, BONSAI_EXTENSIONS). BOM PRINCIPLE → CLAUDE.md. mkdocs nav restructured.
**S79** — Bulk discipline migration: TEXT → Discipline enum + AD_Org_ID. W010 migration (FPR→FP normalization). 17 source files: Placement, NodeContext, disciplineStack all use Discipline enum. BomDropper.resolveDiscipline() replaces deriveDiscipline+deriveAD_Org_ID chain. deriveDiscipline() @Deprecated (extraction-only). ERP coherence check: AD_Org_ID=0 CLEAN, m_bom_line CLEAN, IsSummary CLEAN. `mvn compile -q` + `mvn test-compile -q` PASS.
**S78** — AD_Org_ID FK on discipline columns. W009 migration (AD_Org_ID INTEGER on C_OrderLine). Discipline.java enum gains adOrgId field + fromAD_Org_ID(). BomDropper, OrderMutationService, OrderLineWalker, BuildingWriter, WorkOutputDAO, X_C_OrderLine updated. FPR→FP legacy mapping handled. `mvn compile -q` + `mvn test-compile -q` PASS.
**S77** — Java routing: DocBaseType → M_Product_Category. 19 source + 12 test files. SQL routing queries changed from `doc_base_type` to `m_product_category_id` (BomDropper, BuildingRegistry, CompilationPipeline, DesignerDAO, DesignerAPIImpl, DataIntegrityTest, PrimeRuleWitnessTest). StructuralBomBuilder now writes `m_product_category_id` on BUILDING BOM INSERT. X_M_BOM accessors `@Deprecated`. Schema snapshot annotated. `mvn compile -q` + `mvn test-compile -q` PASS.
**S76** — Rename disc_validation.db → ERP.db. File copy + bulk grep-replace across ~100 files (Java, shell, Python, SQL, docs, HTML). Java constant DISC_VALIDATION_DB_PATH → ERP_DB_PATH. `mvn compile -q` PASS.
**S75** — M_Product_Category hierarchy + BUILDING backfill + AD table consolidation. DV018 migration (71 new categories: 4 top-level, 32 floor, 19 room, 11 infra, 5 cross-domain/anomaly → 117 total). DV019 migration (bad_* tables moved from component_library.db → ERP.db: 67 rows). BUILDING BOM backfill verified (0 NULL). cleanup_complib_duplicates.sh script for stale table removal. `mvn compile -q` PASS.
**S74** — Phase 3: remove CO_EmptySpaceLine. Pipeline rewritten to use in-memory RoomSlot from M_BOM_Line dx/dy/dz. 4 PO classes deleted, W008 migration (DROP TABLE), populateCoEmptySpace → computeRoomSlots, SpatialStructureBuilder accepts List\<RoomSlot\>, EmptySpaceChecksum removed from C_Order/pipeline. 6 verbs updated (BuildSpatialStructure, VerifyPlacement, HelloWorld, SummarizeBuilding, CompleteBuilding). BuildingWriter DDL cleaned, OutputTemplateGenerator cleaned, SpatialDigest.computeEmptySpaceChecksum removed. `mvn compile -q` + `mvn test-compile -q` PASS.
**S73** — CO_EmptySpaceLine → compiler-internal. Phase 1: 15 docs aligned (WHERE = M_BOM_Line dx/dy/dz). Phase 2: 4 PO classes @Deprecated, all consumer javadoc/comments updated, SpatialDigest + VerifyPlacementVerb @Deprecated. Phase 3 deferred (tables still needed by pipeline). AUDIT docs untouched (historical).
*S64–S72 — AD Dictionary Steps 0–5, ERP alignment Sessions A–F (DV015–DV017, W005–W007), CO_EmptySpaceLine removed. [AUDIT Appendix I–T](docs/AUDIT_S51_FOCUSED.md).*
*S57–S60 — 1D Order Configurator, gate debt, DM generative, ERP alignment. 34/34 compiled.*
*Earlier: S39–S56 — ASI, WALK-THRU, focused audit, BIMEyes, 6D/7D, Web UI, BOM Drop.*
