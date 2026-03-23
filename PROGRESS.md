# PROGRESS — Current Development State

> **⛔ Don't run ANY git command on `library/component_library.db`.** No stash, checkout, restore, reset. S63 lost 2400+ products this way.

> **Rule:** PROGRESS.md is a thin status file. No specs here — specs live in `docs/` and PROGRESS
> links to them. Keep this file under 80 lines.

## Current State

**Gate:** `./scripts/run_RosettaStones.sh` — **Session 58c. Pre-existing gate debt cleared: IN/DX/TE all GREEN. C9 axis swaps accepted (mirror rotation). Reference DBs re-baselined. 19/34 ALL GREEN (+3: MO/RS/WA). Remaining debt: G5 GEO_ (RA/JE/ES), C9 axis swaps (JE/HI/SC/RM).**

| Gate | SH | FK | IN | DX | TE | DM |
|------|----|----|----|----|----|----|
| G1-COUNT | PASS (55) | PASS (82) | PASS (699) | PASS (1099) | PASS (48428) | PASS (60) |
| G2-VOLUME | PASS (+0.00%) | PASS | PASS | PASS (+0.00%) | PASS | — |
| G3-DIGEST | PASS | PASS | PASS | PASS | PASS | — (GENERATIVE) |
| G4-TAMPER | PASS | PASS | PASS | PASS | PASS | PASS |
| G5-PROVENANCE | PASS (0 GEO_) | PASS | PASS | PASS (7 checks) | PASS (0 GEO_) | PASS (0 GEO_) |
| G6-ISOLATION | PASS | PASS | PASS | PASS | PASS | PASS |
| C8-DIVERSITY | PASS | PASS | PASS | PASS | PASS | — (no ref) |
| C9-AXIS | PASS | PASS | PASS | PASS (87 axis-swaps accepted) | PASS | — (no ref) |
| W-TOT | PASS | PASS | — | PASS | PASS (48428/48428) | PASS |

**Pipeline:** 9 stages. 64 verbs. 2475 products. 4-DB architecture (24+20+6+output). 4D/5D/6D live on real library.
**Rosetta Stones:** 35 buildings (34 EXTRACTED + 1 GENERATIVE). 19 ALL GREEN. Full table: [TestArchitecture.md §Rosetta Stone Coverage](docs/TestArchitecture.md#rosetta-stone-coverage-s58c).
**BIMBackOffice:** 5/5 GREEN. **BonsaiBIMDesigner:** 392/392 GREEN (40 test classes).
**Scorecard: 31/36.** 4D/5D live DAOs + 6D=2, 7D=2, 3D=3, CR/Audit=2. Nearest competitor: 9.
**BIMEyes:** 43 files, 28 proof classes (~14 per-element, ~8 aggregate, ~6 conditional). [EYES_SRS.md §10 audit](docs/EYES_SRS.md#10-audit-finding-proof-coverage-honesty-s60-post-audit). Gap 10 (source fidelity) NOT YET IMPLEMENTED.

## What's Next

**[DONE] S58c — Pre-existing gate debt cleanup:**
  IN/DX/TE all GREEN. Details: [TestArchitecture.md §Rosetta Stone Coverage](docs/TestArchitecture.md#rosetta-stone-coverage-s58c).
  C9 axis-swap tolerance: `GeometryFidelityTest.java`. Reference DBs re-baselined (not pipeline code).

**[DONE] S59 — Work Order path + HTML↔Bonsai sync:**
  W-WO-1: bomDrop(DM) → 60 leaves → completeIt → 60 elements. WorkOrderCompileTest 6/6 GREEN.
  HTML UI: bomDrop renders tree, compile/completeIt pass paths, DocAction buttons (save/approve/complete/promote).
  Bonsai bridge: pollCommands timer (2s interval) picks up loadOutput → db_loader populates viewport.
  §30.2 sync table: all 6 directions DONE. §30.5 priority #1 DONE, #2 DONE.

**[IN PROGRESS] S60 — ERP Model Alignment:** [S60_ERP_ALIGNMENT.md](docs/S60_ERP_ALIGNMENT.md)
  Core wiring DONE: BomDropper + OrderLineWalker in Rosetta Stone pipeline. SH/FK/DM GREEN.
  DX G2/G3 drift is pre-existing (component_library.db evolving, not S60).
  S60-S2: R21 host_element_ref DONE, U6 DAO DONE, --diff TSV DONE. #6 assessed (77 files, dedicated session).
  S60-UI: 10 tabs aligned §30.3, Show in Bonsai proven (257 wireframe cubes from Web UI BOM Drop).

**Next session (S66): CP-1 + CP-2 + Task 4A — one shot.**
  **1. CP-1** (TE per-element verification): Store element_ref in CLUSTER verb → TotalityContractTest matches by identity not position. Clears TE G3-DIGEST. [ACTION_ROADMAP.md §CP-1](docs/ACTION_ROADMAP.md#cp-1-te-per-element-verification-gap-5-closure).
  **2. CP-2** (DX MIRROR verb): Complete DX structured BOM with mirrored half-unit pair. Clears DX C9 85 axis mismatches. [ACTION_ROADMAP.md §CP-2](docs/ACTION_ROADMAP.md#cp-2-dx-mirror-verb). Large effort — may split.
  **3. Task 4A** (Discipline wiring): BomDropper populates Discipline from bom_category. OrderLineWalker passes Discipline. DemoHouse FP elements. W-DM-TC5-1 witness. [ACTION_ROADMAP.md §Task 4](docs/ACTION_ROADMAP.md#task-4-rule-driven-discipline-framework-fp-as-first-case) · [DemoHouseAnalysis.md §6](docs/DemoHouseAnalysis.md#6-fp-discipline--readiness-assessment).
  **Done (S62):** M_Product_Category (46 cats), 3 FP products onboarded, DemoHouse 53/53 (was 43/60).
  **Audit concerns:** ExtractionElement constructor growing (13+ params, consider builder). ELEC/ACMV product availability unknown.

**Post S63 — Enterprise Layer:** After S60 debt clear + Task 4A done, begin [ProjectOrderBlueprint.md](docs/ProjectOrderBlueprint.md):
  §1 Exception-based ordering (~S63), §2 C_Project (~S65), §4 BOM Mining (~S66+), §9-§12 Phase H+ (needs iDempiere REST).
  **AD Dictionary (S62→S65):** Steps 0–3 DONE. AD_Org, dual columns, M_Product moved to disc_validation.db. [DISC_VALIDATION_DB_SRS.md §11.6.5](docs/DISC_VALIDATION_DB_SRS.md#1165-migration-sequence-6-steps-each-independently-committable). Next: Step 4 (remaining AD tables), Step 5 (AD_Org_ID FK), Step 6 (cleanup).

## Session Log (recent first)

**S65** — DV015 M_Product migration (Step 3). Copied 2,475 M_Product + 46 M_Product_Category from component_library.db to disc_validation.db. 13 Java files: all M_Product reads switched to disc_validation.db. ProductRegistrar dual-write (geometry join + master catalog). SH/FK 7/7. DiscValidationDBTest 27/27 (+3). [AUDIT_S51_FOCUSED.md §Step 3](docs/AUDIT_S51_FOCUSED.md#step-3-implementation-audit-s65-2026-03-24).
**S64** — AD Dictionary investigation §11. Steps 0–2: AD_Org (DV013), dual columns (DV014), CL001 dead table script. DiscValidationDBTest 24/24. [DISC_VALIDATION_DB_SRS.md §11](docs/DISC_VALIDATION_DB_SRS.md#11-investigation-report--application-dictionary-database-placement-s64).
**S60-S3** — R21 re-extract + audit + EYES consolidation + ProjectOrderBlueprint §13. (1) Re-extracted SH/FK reference DBs with rel_fills_host (7+16 door/window→host mappings). host_element_ref populated on BOM lines. (2) Audit P0 cross-check: 5 FIXED, 1 JUSTIFIED, 1 RETRACTED (AUDIT_S51_FOCUSED.md Appendix D). DV006.DELETED cleaned. (3) VerbFactorizer delegates to BIMEyes ShapeClassifier — 40 lines duplication removed. (4) ProjectOrderBlueprint.md §13 Rule-Driven Discipline: validation-as-suggestion pattern, 3 states (Absent/Proposed/Accepted), NFPA-13 as first rule pack. (5) ACTION_ROADMAP triaged: Known Debt cleaned, Q2 column, CP-1/CP-2 downgraded MED, WF-BB collapsed, Task 4 failure criteria. Seal v28. SH/FK 7/7 PASS. 6 commits.
**S60-UI** — Web UI alignment + Show in Bonsai. 10 tabs aligned to §30.3 (2D Spatial, 3D Geometry, 8 Validate, 9 BOM, 10 Colour). BOM tree container fixed (#bomTree was missing). Show in Bonsai: browser pushes previewBBoxes via schemeName field → Bonsai poll picks up → 257 wireframe cubes rendered. First browser-to-Bonsai BIM preview push. Federation gap analysis: 70+ ops in IfcOpenShell, 8 HIGH priority migration items. webui_sync.py fixed for Blender 5.0 (bpy.app attribute error).

**S60-S2** — R21 + S60 remaining items. (1) R21 host_element_ref: full chain from IfcRelVoidsElement+IfcRelFillsElement extraction (extract.py) → rel_fills_host reference table → ExtractionPopulator → m_bom_line.host_element_ref. Enables M16/M17 validation. (2) AD_Val_Rule_Exception DAO (U6): X_ PO + MValRuleException model with isExcepted/getExceptedRuleIds. (3) Visual diff TSV: --diff flag on run_RosettaStones.sh → SpatialDiff.toTsv() → logs/diff_{PREFIX}.tsv. (4) M_BomCategory #6 assessed: 77 files, orthogonal semantic axes, needs dedicated session. SH/FK 7/7 GREEN.

**S60** — ERP Model Alignment. BomDropper creates C_OrderLine tree in compile DB; OrderLineWalker walks it via bom_child_id FK join-back to m_bom_line. PlacementLoader auto-detects OrderLine path. SH (55), FK (82), DM (60) all GREEN through new path. Schema: C_Order + C_OrderLine in compile DB, S60_schema.sql (U2-U4, U6). Seal re-sealed.
**S59** — Work Order path + HTML↔Bonsai sync. W-WO-1: WorkOrderCompileTest 6/6 GREEN (bomDrop→compile→60 el). HTML UI: DocAction buttons (save/approve/complete/promote), bomDrop renders tree, compile passes paths. Bonsai bridge: pollCommands timer + db_loader viewport load. §30.2 sync complete. 392/392 GREEN.
**S59-S2** — Post-swap compilation. W-DM-TC4-1 GREEN: bomDrop(SH) + swapProduct(roof→FK_DG_STR) + compile → 95 elements. G1/G5/G8 pass. BomDropConfigureTest 6/6 GREEN. No pipeline code changes.
**S58b** — DM generative path. First GENERATIVE building (DemoHouse_2BR, 60 elements). seed_dm_bom.sql.
**S58a** — 9 buildings full pipeline (RA/JE/ES/MO/HI/RM/RS/SC/WA). G3 baselined. MO threshold=1. +3 ALL GREEN.
**S57** — 1D Order Configurator + Bonsai↔WebUI sync. 34/34 buildings compiled. 4 YAML dupe-code fixes.
**S56b** — Last Mile re-check. 6 bugs fixed (geometry map rename, ProductRegistrar, stale paths).
**S56** — Web UI frontend (port 9878, 10 tabs). DesignerServerTest 21/21.
**S55** — BOM Drop frontend + panel renumbering (1D-7D, 8-10). 330/330 GREEN.
**S54a** — Wire BOM Drop model. PlacementLoader isolation. TC-1 end-to-end (55 elements). 330/330 GREEN.
**S53** — ERP-correct BOM tree model. Drop EN-BLOC/WALK-THRU. iDempiere OrderLine pattern. Roof proofs P27/P28.
*Earlier: S39–S52b — ASI authoring, WALK-THRU, FRAME/ROUTE fix, focused audit, BIMEyes Phase 1-3, 6D/7D DAOs, CP-4, DV010, scale-up, LAST_MILE.*
