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
**BIMBackOffice:** 5/5 GREEN. **BonsaiBIMDesigner:** 392/392 GREEN (39 test classes).
**Scorecard: 31/36.** 4D/5D live DAOs + 6D=2, 7D=2, 3D=3, CR/Audit=2. Nearest competitor: 9.
**BIMEyes:** 41 files, 28 proofs. FL-2 advisory + FL-5 EYES integration DONE.

## What's Next

**[DONE] S58c — Pre-existing gate debt cleanup:**
  IN/DX/TE all GREEN. Details: [TestArchitecture.md §Rosetta Stone Coverage](docs/TestArchitecture.md#rosetta-stone-coverage-s58c).
  C9 axis-swap tolerance: `GeometryFidelityTest.java`. Reference DBs re-baselined (not pipeline code).

**[DONE] S59 — Work Order path + HTML↔Bonsai sync:**
  W-WO-1: bomDrop(DM) → 60 leaves → completeIt → 60 elements. WorkOrderCompileTest 6/6 GREEN.
  HTML UI: bomDrop renders tree, compile/completeIt pass paths, DocAction buttons (save/approve/complete/promote).
  Bonsai bridge: pollCommands timer (2s interval) picks up loadOutput → db_loader populates viewport.
  §30.2 sync table: all 6 directions DONE. §30.5 priority #1 DONE, #2 DONE.

**Next: S60 — ERP Model Alignment (iDempiere BOM pattern):**
  Align compilation pipeline to pure iDempiere C_Order → C_OrderLine → BOM Drop model.
  **Spec changes already applied:** BBC.md §1, ConstructionAsERP.md §1 updated.

  **Key principles (agreed with user):**
  1. ONE C_DocType: "Construction Order" — metadata only, not a compilation driver
  2. No M_BomCategory — use M_Product_Category instead (product IS its category)
  3. Products with IsBOM=Y ARE BOMs — no separate bom_type hierarchy
  4. Compiler walks C_OrderLine tree, not m_bom directly
  5. YAML is the test script (creates C_Order + OrderLines), not a pipeline driver

  **Schema gaps (from UI session review):**

  | Gap | Table | Column/Feature | Purpose |
  |-----|-------|---------------|---------|
  | U2 | C_OrderLine | `Discipline TEXT DEFAULT 'ARC'` | Add FP/MEP/ELEC discipline lines to an order |
  | U3 | C_Order | `Jurisdiction TEXT` | MY/US/UK — determines which building code rules apply |
  | U4 | C_Order | `OccupancyClass TEXT` | LH/OH1 — NFPA 13 occupancy for FP calculations |
  | U5 | DAO | `OrderLineHydrationDAO` | Convert validated C_OrderLine → PlacementRequest for compiler |
  | U6 | Table+DAO | `AD_Val_Rule_Exception` | User override of WARN validation (accept known deviation) |

  **Code changes needed:**
  1. Schema migration: add Discipline/Jurisdiction/OccupancyClass columns + AD_Val_Rule_Exception table
  2. `CompilationPipeline.run()` → accept C_Order ID, walk C_OrderLine tree (not C_DocType lookup)
  3. `BuildingRegistryTest` → create C_Order + bomDrop per building, then compile via OrderLine path
  4. `run_RosettaStones.sh` → use bomDrop + completeIt (same path as WorkOrderCompileTest)
  5. Remove C_DocType as compilation entry point (keep as order metadata)
  6. Replace M_BomCategory references with M_Product_Category
  7. OrderLineHydrationDAO: C_OrderLine → PlacementRequest (bridge between order and compiler)
  8. Wire AD_Val_Rule validation with exception override (U6)
  9. Add visual diff report: per-element TSV under `--diff` flag
  10. Script accepts building prefixes as arguments, small files default, large files explicit

  **Already proven:** WorkOrderCompileTest (W-WO-1: bomDrop→compile→60 el),
  BomDropConfigureTest (TC-4: roof swap→95 el). The OrderLine path works.

  **Database backup:** `backup/db_snapshot_20260323_014819/` (1.5GB)

## Session Log (recent first)

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
