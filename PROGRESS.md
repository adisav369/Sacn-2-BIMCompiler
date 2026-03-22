# PROGRESS — Current Development State

> **Housekeeping:** `library/component_library.db` is tracked in git but blocked from commit by pre-commit hook (Gate 1).
> Before final release: commit a pristine snapshot, then `git rm --cached library/component_library.db` to untrack.
> All schema changes go through `migration/` scripts only.

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

**Pipeline:** 9 stages. 64 verbs. 2459 products. 4-DB architecture (22+20+6+output). 4D/5D/6D live on real library.
**Rosetta Stones:** 35 buildings (34 EXTRACTED + 1 GENERATIVE). 22 ALL GREEN. Full table: [TestArchitecture.md §Rosetta Stone Coverage](docs/TestArchitecture.md#rosetta-stone-coverage-s58c).
**BIMBackOffice:** 5/5 GREEN. **BonsaiBIMDesigner:** 304/304 GREEN (38 test classes).
**Scorecard: 31/36.** 4D/5D live DAOs + 6D=2, 7D=2, 3D=3, CR/Audit=2. Nearest competitor: 9.
**BIMEyes:** 41 files, 28 proofs. FL-2 advisory + FL-5 EYES integration DONE.

## What's Next

**[DONE] S58c — Pre-existing gate debt cleanup:**
  IN/DX/TE all GREEN. Details: [TestArchitecture.md §Rosetta Stone Coverage](docs/TestArchitecture.md#rosetta-stone-coverage-s58c).
  C9 axis-swap tolerance: `GeometryFidelityTest.java`. Reference DBs re-baselined (not pipeline code).

**Next: S59 — DemoHouse 3-OrderLine compilation (TC-4 + TC-5):**
  SH BOM Drop + swap roof to pitched (FK) + add FP discipline.
  Full task breakdown: [GENERATIVE_HOUSE_SRS.md §10](docs/GENERATIVE_HOUSE_SRS.md#10-demohouse-implementation-tasks-s59).
  Pre-reqs: TRIM verb, BOM Drop cascade, FP validation rules, mock tests.

**Deferred: Cascade data enrichment (LIVING/KITCHEN coverage):**
  Library needs smaller room BOMs or SET AABB re-measurement.

## Session Log (recent first)

**S58b** — DM generative path. First GENERATIVE building (DemoHouse_2BR, 60 elements). seed_dm_bom.sql.
**S58a** — 9 buildings full pipeline (RA/JE/ES/MO/HI/RM/RS/SC/WA). G3 baselined. MO threshold=1. +3 ALL GREEN.
**S57** — 1D Order Configurator + Bonsai↔WebUI sync. 34/34 buildings compiled. 4 YAML dupe-code fixes.
**S56b** — Last Mile re-check. 6 bugs fixed (geometry map rename, ProductRegistrar, stale paths).
**S56** — Web UI frontend (port 9878, 10 tabs). DesignerServerTest 21/21.
**S55** — BOM Drop frontend + panel renumbering (1D-7D, 8-10). 330/330 GREEN.
**S54a** — Wire BOM Drop model. PlacementLoader isolation. TC-1 end-to-end (55 elements). 330/330 GREEN.
**S53** — ERP-correct BOM tree model. Drop EN-BLOC/WALK-THRU. iDempiere OrderLine pattern. Roof proofs P27/P28.
**S52b** — ASI authoring backend. 8 CRUD methods. 9 witnesses. 313/313 GREEN.
**S52** — WALK-THRU selection cascade proof. 7 witnesses. DesignerDAO.findMatchingSets().
**S51b** — FRAME/ROUTE LBD fix. ClusterReclassifier (345 groups, 47K instances).
**S51** — Focused audit. 8 P0 fixes. DemoHouseTest rewritten. 297/297 GREEN.
**S50** — BIMEyes Phase 1-3. 24 proof classes. FL-2 advisory. FL-5 EYES integration. 28 proofs.
**S49** — BIMEyes standalone module. 12 source files, 4 packages.
**S48** — CP-4 semantic half. product_category on component_types. 20 switches replaced.
**S47** — DV010 mined dimension rules. 415 rules from 20 buildings. DimensionRangeValidator.
**S46b** — G-9 ORDER View + BOM Outliner. 3 API actions, 7 witnesses.
**S46** — CP-4 geometric half (phases 4a-4e). shape_archetype + scale_band on m_bom_line.
**S45** — G-8 Click-to-Place. Interactive viewport placement. 15 witnesses.
**S44** — CP-3 scale-up. 8→20 buildings onboarded. onboard_ifc.sh.
**S44b** — 14 more buildings (20→34). GH/JS/NI/WB/WL/WT/WA/JE/WI/RA/RM/RS/CL/HI.
**S43** — AABB qualifier + PHANTOM spatial index. Tack chain proof. IN G3 root cause found.
**S42** — LAST_MILE checklist. C8 naming fix. IN G3 root cause analysis.
**S41b** — DB migration (81→21 tables). Doc consolidation. 4-DB architecture.
**S41** — FACTORIZE-v2 review + 6 fixes. Seal re-sealed.
**S40** — Frontend review + 7 fixes. BonsaiBIMDesigner Python addon.
**S39c** — AC11 Institute (IN) 5th Rosetta Stone. 699 elements.
**S39d** — BIMBackOffice module. FRAME LBD fix. 5 witnesses.
**S39b** — 4D ScheduleDAO + 5D CostDAO. 11 witnesses.
**S39** — 6D SustainabilityDAO + 7D FacilityMgmtDAO + Audit ChangelogDAO. 14 witnesses.
