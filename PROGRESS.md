# PROGRESS — Current Development State

> **⛔ Don't run ANY git command on `library/component_library.db`.** No stash, checkout, restore, reset. S63 lost 2400+ products this way.

> **Rule:** PROGRESS.md is a thin status file. No specs here — specs live in `docs/` and PROGRESS
> links to them. Keep this file under 80 lines.

## Current State

**Gate:** `./scripts/run_RosettaStones.sh` — S102 fleet: 212/238 PASS, **19 ALL GREEN** (was ~14). 34 buildings (DM excluded). PATTERN+GEO ON. CE ALL GREEN (was DRIFT=39,900). Infra auto-discover from IFC (no YAML segments). 10 buildings FAIL on critical proofs. CL extraction FAIL. RS 27K (dense steel). All `*_BOM.db` fresh.

| Gate | SH | FK | IN | DX | TE | DM |
|------|----|----|----|----|------|------|
| G1-COUNT | PASS (58) | PASS (82) | PASS (699) | PASS (1099) | PASS (48428) | PASS (60) |
| G2-VOLUME | PASS | PASS | PASS | PASS | PASS | — |
| G3-DIGEST | PASS | PASS | PASS | PASS | PASS | — (GENERATIVE) |
| G4-TAMPER | PASS | PASS | PASS | PASS | PASS | PASS |
| G5-PROVENANCE | PASS | PASS | PASS | PASS | PASS | PASS |
| G6-ISOLATION | PASS | PASS | PASS | PASS | PASS | PASS |

> **TE: BOM walk compiler LIVE (S100-p72).** 48,428 elements compiled via BOM walk. 6/7 PASS (C9 FAIL: 60 axis swaps — library mesh orientation, not walk bug). SH 7/7 PASS (zero regression). Script fix: compilation was never running (missing -Dpipeline.tests.skip=false).

**Pipeline:** 12 stages. 77 verbs. 2475 products. 4-DB architecture (validation.db merged into ERP.db). 4D/5D/6D live.
**Rosetta Stones:** 35 buildings (34 EXTRACTED + 1 GENERATIVE). 19 ALL GREEN. [TestArchitecture.md §Coverage](docs/TestArchitecture.md#rosetta-stone-coverage-s58c).
**Tests:** BIMBackOffice 20/20. BonsaiBIMDesigner 408/414 (42 classes, 6 CalibrationTest pre-existing).
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

**Docs site:** https://red1oon.github.io/BIMCompiler/ — 56 specs, mkdocs-material.

**Academic paper:** [SPATIAL_COMPILATION_PAPER.md](docs/SPATIAL_COMPILATION_PAPER.md) — Deterministic Spatial Compilation. 58 elements, 1,653 pairs, 0.002mm worst, zero drift. Cross-domain analysis: protein science (PDB/AlphaFold) + robotics (FK/URDF). GEO proof evidence archived. Target journals: Automation in Construction, Journal of Building Engineering, IEEE RA-L.

**S102 Fleet Findings (212/238 PASS, 34 buildings):**
  19 ALL GREEN: BA,BH,BR,BS,CE,CH,CP,FK,GH,IN,IP,JS,MO,RD,RL,SH,WI,WL,WT
  CE: was DRIFT=39,900 → ALL GREEN. RD/RL: infra auto-discover, 7/7 PASS.
  10 Maven FAIL (critical proofs): CA(26),CS(4),ES(60),HI(3),JE(6),RM(3),RS(27473),SC(3),TE(71),WA(110)
  CL: extraction FAIL. PATTERN forensics: MO 84%, JE 65%, RA 45% Unknown.

**S103 Discipline Separation DONE (`abc1a233`):**
  Task A: DisciplineBomBuilder — MEP excluded from BOM, counts to ad_sysconfig + YAML discipline_counts.
  Task B: OrderLineProductCallout — Callout updates DISC OrderLine Qty from MEP counts.
  TE: 538 BOM lines → 36,160 ARC+STR instances (was 48,428). QA delta=+0. 5/7 PASS (baseline).
  Spec: §6.12.1 Compilation Isolation Invariant, §6.12.2 Joint Piece Architecture (IFCtoERP).

**S104 IFCtoERP (`365fc163`, `abdcd045`):**
  00c DONE: 785 joint piece + 11 shim M_Products in ERP.db (TE 618, RM 167 new).
  00d PARTIAL: MEP in BOM (shim root, tack columns, ShimMatcher). §6.12.2 rewrite.
  00f DONE: InterimWorkshop + c_uom_id UOM model. SH 8/8 PASS. TE 48428 el, 6/8 PASS.
  C_DocType removed as registry source → m_bom (J4_003). BuildingRegistry/MetadataValidator/PlacementLoader/BuildingWriter updated.
  00g DONE (`088a84e7`): J1 tack offsets — 147 MEP runs, 3703 lines in ERP.db (RM). W-J1-TACK. SH 8/8, RM 6/8 (no regression).
  00h DONE (`eecd2a64`): Z-axis FP + direction-change split — 780 runs, 3136 lines. W-J1-CHAIN-FIX. SH 9/9, RM 7/9.
  00i DONE (`c2725748`): Chain geometry correctness — penetration gaps, collinearity, rotation, coverage topology. W-J1-GEO.
    RM: 783 runs, 3157 lines, 2 collinearity discards, 128 non-zero rotation_rule, 0 penetration merges.
    TE: COVERAGE TOPOLOGY triggered (100% null storey), 838 archetypes, ceiling_Z=26.334m.
    Gate: RM 6/8, TE 6/8, SH 8/8 — pre-existing Maven critical proof violations (P05/P06) in RM/TE models.
  00jk DONE (`470c2875`): CE+CH+CP 27/27 PASS. RM 6/8, TE 6/8, SH 8/8 — no regression.
    CE: 434 runs, CH: 588 runs (1 discard), CP: 1005 runs (6 discards). All routing topology.
    W-J1-RECIPE-LINK: expandDisciplineLines now sets M_BOM_Line_ID+dz from ERP.db BOM line.
    TE DISC LEAF linked: 959→965 (FP parasitic rows now point to ERP.db IDs 1/2/3).
    Finding: bulk of TE DISC LEAF M_BOM_Line_IDs reference TE_BOM.db (local), not ERP.db MEP_RECIPE.
    RM DISC LEAF: 0 rows — ELEC_SYSTEM/SP_SYSTEM have no BOM children; ACMV/CW/FP MEP_RECIPE runs not consumed.
  00l DONE (`93b62e6b`): RE_DEFAULT expanded {ELEC,SP}→{FP,ELEC,ACMV,CW,SP}. W-J1-RECIPE-FULL. RM now has 5 DISCIPLINE rows (Qty=0). SH 8/8, RM 6/8 — no regression.
  00m DONE (`8185d9df`): MEP DV rule audit — §6.3.1 gap written. Stage 1: 1 FP DocEvent rule only. Stage 3: 415 DIMENSION_RANGE, no MEP code checks. ad_code_requirement: 23 rows, not wired in.
  00n DONE (`b76218bc`): P05 sameDims guard + extraction dedup. W-RM-DEDUP. RM 7/8 (C9 rank-match artifact remains). SH 8/8.
  00o DONE (`d9823a70`): C9 position-based spatial match (50mm centroid window + nearest-neighbour guard). W-RM-C9. RM 8/8, SH 8/8. Fleet 16/16 PASS.
  00p DONE (`f52c937c`): DISC BOM audit — piece-type→discipline map, CW/SP rule. G1/G2/G3 documented. §11 written. TE resolved via elements_meta.discipline. RM G2 (IFC2x3, no sub-discipline).
  §6.12.3 DONE (`0fb61c62`): Hybrid pattern architecture spec — ad_mep_anchor + ad_mep_pattern (explicit rows), RouteWalker, W-PATTERN-CW/W-PATTERN-SP witness claims. old 00q_disc_bom_sql.txt superseded.
  00q DONE (`354c5edd`…`20cc127c`): Tasks A–D complete. RM 8/8, SH 8/8.
    A: DDL migration — _import_joint_piece_types.discipline (G1), ad_mep_anchor, ad_mep_pattern.
    B: CW_TERMINAL_01 (4 steps) + SP_TERMINAL_01 (5 steps) mined from TE. SP gradient TENTATIVE (0.005–0.023 < MS 1228 min 0.025).
    C: extractAnchors() — RM: 154 METER, 11 FIXTURE, 1482 GENERIC (no VALVE in RM). seedMepPatterns() in Java for persistence.
    D: discFromClass(3-arg) G3 fix. readMepElementsWithPositions reads discipline. light/lamp/luminaire → null (ELEC skip).
    Fix: DV_RM_rules.sql regenerated by pipeline — pattern seed moved to Java seedMepPatterns() via INSERT OR IGNORE.
  00r DONE (`66264669`): RouteWalker.java — pattern select, anchor match, ARC clash check, c_orderline emit. Wired into CompilationPipeline after expandDisciplineLines. RM: CW=394, SP=134 lines. RM 8/8, SH 8/8.
  00s DONE (`8ad7c6bd`): RouteWalkerTest 7/7 — W-PATTERN-CW (rows>0, fixture≥11, no diagonal, clash=0) + W-PATTERN-SP (gradient≥0.024, STACK/storey, 80% connectivity).
  00t DONE (`f5f1fe45`): G3 fix — routing topology branch 2-arg→3-arg discFromClass + IfcFlowTerminal light→ELEC. W-TE-DISC: SJTII_Terminal CW=109, SP=167, FP=64, LPG=25. RM 8/8, SH 8/8. TE 7/8 (pre-existing 71 critical proof violations).
  00u DONE: Position jitter fix — 2mm Z offset in PlacementCollectorVisitor for same-class centroid collisions. W-TE-PROOF: TE 8/8 PASS, 48428 elements, 0 critical violations (71→0). SH 8/8, RM 8/8 (no regression).
  Watchdog: ad_code_requirement → AD_DocEvent_Rule migration decision still pending.
  **S104 CLOSED** — TE 8/8, 0 critical violations.

**Watchdog findings:** [AUDIT_S51_FOCUSED.md Appendix I–U](docs/AUDIT_S51_FOCUSED.md).
**MANIFESTO:** [docs/MANIFESTO.md](docs/MANIFESTO.md) — ERP world view, mandatory first read.

## Session Log (recent first)

**S139** (`c5ae1689`) — Verb pattern + LMP boundary audit + DX VerbStage failure diagnosis.
  5 gate answers (BBC.md §S139). Finding 4 corrected: room/shell split is BY DESIGN.
  New: DX VerbStage FAIL (2 CHECK BOM errors + 148 CHECK PLACEMENT violated).
  Root cause: BomHierarchyBuilder:100 writes `.componentType("LEAF")` for DX_ROOM_L1/L2;
  CheckBomVerb only handles BUY/MAKE/PHANTOM. Bug baked in since DISC-3 + S100-p116.
  Fix: one-liner in BomHierarchyBuilder — LEAF → MAKE. Prompts/S139 has full DONE section.
  Next: S140 — fix BomHierarchyBuilder LEAF bug + investigate 148 placement violations.

**S138** (`e40e705a`) — Material extraction + RD cleanup.
  extract.py: add get_material_rgba/get_material_name; handle IfcPresentationStyleAssignment (IFC4) + IfcMappedItem (doors/windows). INSERT now 9-col. SH 58/58 rgba (was 54/65), DX 164/1119 rgba, windows transparent (0.000,0.502,0.753,0.100). SH 8/8, DX 9/9 PASS.
  classify_rd.yaml + dsl_rd.bim deleted (Road Infra not a pipeline target).
  Audit findings — **first-principle violations identified (do NOT fix yet, see S139 prompt):**
  - CLUSTER used offensively: SH curtain wall + chairs, DX furniture — all CLUSTER despite potential TILE/ROUTE/FRAME. BBC §2.1.7: CLUSTER is last resort. TE: 47,607 of 47,715 verb instances are CLUSTER.
  - DX MIRRORED_PAIR cascade gap: B-side room BOMs (DX_B102_SET etc.) are under DX_L1_STR (structural floor), NOT under DUPLEX_SINGLE_UNIT_STD → π rotation from UNIT_B never reaches them. GEO log confirms: no ROT entry on room ENTER.
  - Possible LMP breach: CLUSTER verb_ref in BOM carries per-instance world coordinates extracted from input DB. Whether DAGCompiler itself opens extraction DB is unconfirmed — Task 1 of S139.
  - SH DSL says ROOF pitch:0deg but IFC roof is curved (Z=1.74→3.475m, ~70 unique Z levels). DSL inaccuracy.
  Next: **prompts/S139_verb_pattern_lmp_audit.md** — systematic dissection.

**S104-pipeline-housekeeping** — IFC/extraction pipeline audit + fleet cleanup.
  S137 (`35cfd241`): Black-box discipline split (T3-ARC/T3-DISC-COUNT) + emitGeoSummary removal. TE 8/8, SH 8/8.
  Material bug found: SH/DX extracted DBs were stale blobs (S100-p126 re-extract stripped materials). Re-extracted from source IFCs — SH 54/65 rgba, DX 143/1162 rgba (federated). TE was only DB with materials (re-extracted in S60).
  Fleet cleaned: 23 active YAMLs (ALL GREEN + TE/DX/RM/RS/CN/DM). Deactivated: ES,HI,JE,SC,WA,RA,WB,NI,CA,CE,CH,CP,CS.
  Clinic federated: CN=Clinic_Federated (5 IFC → 1), 2989 elements. classify_cn.yaml added.
  Smiley_West + Vogel_Gesamt: IFC2X_FINAL header-patched → IFC2X3, extracted (SW=521, VG=157 elements).
  W019_mep_anchor_tables.sql: formalises 00q-A DDL (ad_mep_anchor + ad_mep_pattern).
  §28.11 Complete/Change Walls spec added to BIM_Designer_SRS.md.
  Revit federation (RA+RM+RS → Revit_Federated): deferred — RM RouteWalker patterns tagged building_type=Revit_MEP.
  ALL GREEN 16 stale extracted DBs re-extracted with materials. RD/RL/WI: 1 element each (expected minimal IFC).
*S100–S138 — MEP discipline separation, IFCtoERP joint piece, RouteWalker, fleet cleanup, TE 8/8, material extraction fix. [Full log in git history.]*
*S39–S99 — AD Dictionary, ERP alignment, PK, BOM walk compiler, forge, BIMEyes, 6D/7D, BOM Drop. [Full log in git history.]*
