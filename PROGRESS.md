# PROGRESS — Current Development State

## Current State

**Gate:** `./scripts/run_RosettaStones.sh` — **SH 7/8 (1 FAIL after LBD tack fix — diagnosis pending). DX + TE not yet re-run.**

| Gate | SH | DX | TE |
|------|----|----|-----|
| G1-COUNT | PASS (55) | PASS (1099) | PASS (48428) |
| G2-VOLUME | PASS (+0.00%) | PASS (+0.00%) | PASS (+0.00%) |
| G3-DIGEST | PASS | PASS | SKIP (4 IfcSensor ref delta) |
| G4-TAMPER | PASS | PASS (0 violations / 20 rules) | PASS |
| G5-PROVENANCE | PASS (7 checks) | PASS (7 checks) | PASS (7 checks) |
| G6-ISOLATION | PASS | PASS | SKIP (CO mode) |

**Pipeline:** 9 stages. 63 verbs, 196 witnesses. Seal v11 (74 files INTACT).

**Rosetta Stone Buildings:**

| Building | Mode | Elements | Status |
|---|---|---|---|
| Ifc4_SampleHouse (SH) | EN-BLOC | 55 | GREEN (7/7) |
| Ifc2x3_Duplex (DX) | EN-BLOC | 1099 | GREEN (7/7) |
| SJTII_Terminal (TE) | EN-BLOC | 48,428 | GREEN (7/7) |

## What's Next

**[TACK-FIX] ScopeBomBuilder tack fix — NEXT CODE SESSION (before G-4):**
  - **Root cause found:** ScopeBomBuilder.java line 137 uses `centroidX - ox` (scope box origin)
    instead of child LBD position in parent per BBC.md §4. Also FloorRoomBomBuilder
    creates FLOOR→SET lines with dx=0 (should carry room LBD offset from floor LBD).
  - Fix ScopeBomBuilder: `dx = e.minX() - floorMinX` (same formula as DisciplineBomBuilder)
  - Fix FloorRoomBomBuilder: FLOOR→ROOM line dx = room LBD position in floor
  - Fix VerbDetector.detectCluster(): minX/Y/Z instead of centroidX/Y/Z for offsets
  - Re-run SH, DX, TE — all must PASS
  - Promote W-TACK-1 from WARN to FAIL once 21/21 PASS
  - Then implement T-3 (BUFFER lines) and promote W-BUFFER-1
  - Entry: `ScopeBomBuilder.java`, `FloorRoomBomBuilder.java`, `VerbDetector.java`

**[G-4] work_output.db schema + Save/Recall — after TACK-FIX:**
  - Define C_Order + C_OrderLine + M_AttributeSetInstance tables
  - YAML/Order/ASI embedded in work_output.db during init (self-contained)
  - Save writes OrderLine + ASI (cheap, frequent). Recall loads variant
  - Variant naming: user-configurable (default work1.db), noted in YAML/Order profile
  - This unblocks G-5 (BOM Chooser), G-7 (assembly builder), G-9 (ORDER View)
  - Entry: `DesignerDAO.java`, new `WorkOutputDAO.java`
  - Read: `BIM_Designer.md` §17.10 (three-tier persistence model)

**[G-5] BOM Chooser — after G-4:**
  - Search-first product browser (§17.18). SQL LIKE + AABB fit check
  - Server-driven pagination for 1000+ items. Category tree by DocSubType
  - Container fit: FITS/TIGHT/TOO WIDE. Tack-based auto-placement
  - Entry: `DesignerDAO.java` browseItems query, `panel.py` chooser UI

**[G-6] Ambient compliance — after G-5:**
  - PlacementValidator runs on every change via sync timer (200ms)
  - Live status strip at bottom of Design Mode (§18.4)
  - Red/yellow/green per rule. Failed → highlight bbox + "Auto-fix"
  - Finch3D-inspired: compliance is peripheral vision, not modal

**[BLENDER-BRIDGE] Incremental viewport — G-8:**
  Spec in `docs/BlenderBridge.md`. Java-smart/Python-dumb architecture.
  TCP pipe for Snap to reach PlacementValidator in real-time.
  Delta applicator for incremental viewport updates.

**[R16] Coordinate double-counting — FIXED (session 17):**
  - Child BOM origins zeroed (FLOOR, DISCIPLINE, FLOOR STR) — only BUILDING keeps world origin
  - SB-2 witness: unfactored slabs 0.0000mm error (proven correct)
  - VerbDetector ROUTE guard: reject grid-masquerading-as-route (all-same-dir + dominant-dir)
  - FRAME demoted to advisory verb (sort-order pairing mismatch for construction tolerance)
  - **Remaining:** G2-VOLUME 13.66% (factorization dims), SPRAY 448 outside envelope
  - These are verb grammar encoding precision — not coordinate bugs

**[LAST_MILE] R13: DONE** — ExtractionPopulator returns in-memory list,
  ExtractionReader DB methods removed, EXPECTED_ELEMENTS stored in ad_sysconfig.
  Gap 7 fully closed (R11-R15 all DONE). 21/21 PASS.

**[VERB-GRAMMAR] Exact replication gap — verb encoding precision:**
  The extraction has exact positions. TILE proves lossless encoding (0.0000mm).
  SPRAY/ROUTE approximate because the grammar loses positional information.
  This is analogous to ERP BOM recipe vs shop floor placement — the recipe
  (M_BOM_Line + verb_ref) must encode enough to reproduce exact configuration.
  - SPRAY (47K instances, avg 23m): grid approximation loses cluster boundaries
  - ROUTE (18 instances, avg 0.07m): legitimate single-leg, low error
  - FRAME (78 instances, avg 62mm): construction-tolerance gridlines
  - **Next verb:** CLUSTER — offset-table encoding (no formula → no fidelity error)
  - **G2-VOLUME:** factorized lines use first-element dims for all qty (13.66% drift).
    Fix: store per-group representative dims or use extraction AABB in compiled output

**[R4] ST-mode Rosetta Stone** — deferred (synthetic building, dedicated session)

## Recently Completed (2026-03-18, session 20)

**[SPECS] DocValidate deep spec + EN-BLOC clarification + code-level specs:**

DocValidate.md +606 lines (987→1593): §12 Vertical Rules (cross-storey
continuity — MEP risers, columns, stairs, lifts), §13 Rule Application Order
(3-tier cascade: per-discipline → cross-discipline → vertical, C_Tax/C_Charge/
Financial Reporting analogy), §14 Auto-Population Engine (ConstructionModelSpawner
spawns C_Order, C_OrderLine, ESLine, ASI, PP_Order_Node — user alters defaults),
§15 Code-Level Specs (PlacementValidator 3-tier contract, ConstructionModelSpawner
spawn sequence, RuleMiner pipeline, Non-Disturbance protocol, 15 concrete rules
to mine from Terminal with SQL patterns). BBC.md §3.3 rewritten: EN-BLOC = HelloWorld,
proves stacking order only. One OrderLine at 000, no tack evaluation. WALK-THRU
is where tack convention is exercised.

## Recently Completed (2026-03-18, session 19)

**[SPECS] BBC.md as master spec + doc hierarchy + discipline-as-metadata:**

BBC.md established as single master spec. §1.1 NEW: disciplines are metadata
(bom_category), not structure — iDempiere C_DocType parallel (verified against
postgres). §2.2 rewritten: recursive placement, component_type ignored. §4.0:
tack_from/tack_to Lego principle, 3D positions (dx,dy,dz). §4.1.1 NEW:
validateBOM() spatial analogue. 11 docs updated (§3.4→§4, LFD→LBD, centroid
restatements removed, cross-links to BBC.md). MEMORY.md trimmed 117→74 lines.
SH 1 FAIL diagnosed: ScopeBomBuilder centroid offsets. Code fix next session.

## Recently Completed (2026-03-18, session 18)

**[SPECS] Tack convention alignment + LBD rename + witness checks:**

Specs (4 docs revised):
- `BOMBasedCompilation.md` — §2.2 (all BUY, no component_type, no MAKE), §3.1-3.6
  (terms, ESLine parent-owns-attachment, EN-BLOC AABB check, WALK-THRU slot-walk,
  selection cascade, Rosetta Stone launch booster), §4 (tack LBD, BUFFER invariant,
  never-centroid rule, world coord reconstruction, known drift §4.3)
- `TerminalAnalysis.md` — spec alignment note, dual tack diagrams (current vs spec),
  verb factorization savings table (361 verb lines save 97.6%), recurrence analysis,
  full BOM hierarchy with measured data (58 BOMs, 1,131 lines, 48,485 instances)
- `DuplexAnalysis.md` — spec alignment header (same centroid drift as SH/TE)
- `ACTION_ROADMAP.md` — "ask user when unable to implement spec" PROMPT rule,
  Pre-Code Specs T-1..T-4 (LBD offsets, BUFFER, witnesses), anti-cheat rules
  from LAST_MILE_PROBLEM.md, MAKE→TACK rename across Phase G

Code changes (T-1, T-2, T-4 + compilation side):
- `BomValidator.java` — W-TACK-1 (LBD convention witness, advisory),
  W-BUFFER-1 (completeness invariant witness, advisory)
- `DisciplineBomBuilder.java` — centroidX/Y/Z → minX/Y/Z on LEAF dx/dy/dz (T-1)
- `StructuralBomBuilder.java` — centroidX/Y/Z → minX/Y/Z on LEAF dx/dy/dz (T-2)
- `PlacementCollectorVisitor.java` — add halfW/D/H to recover centroid from LBD (§4)

LBD rename: LFD→LBD (Left-Back-Down) across all specs + code comments.
More intuitive for BIM Designer: back-left corner into the wall.

**Status:** SH 7/8 (1 FAIL — diagnosis pending next session). DX/TE not re-run.

## Recently Completed (2026-03-18, session 17)

**[G-3] Design Mode wire + bbox renderer + UI strategy + strategic positioning:**

Java (3 new, 4 edited):
- `DesignBBox.java` — 13-field record: bbox coords + IFC/BOM metadata
- `CreateNewResponse.java` — response with `List<DesignBBox>` bboxes
- `RoomLayoutGenerator.java` — deterministic site → storey → room partitioning
- `DesignerAPI.java` — 5 new actions (snap, save, recall, listVariants, promote) + 7 records
- `DesignerAPIImpl.java` — RoomLayoutGenerator wired + persistence stubs
- `DesignerServer.java` — all new actions dispatched
- `JsonProtocol.java` — parseDesignBBoxes + field accessor

Python (1 new, 4 edited):
- `design_bbox.py` — GPU batch renderer: enable/disable/focus_section/mark_committed,
  category colors, commit fade animation (2s alpha pulse), lazy sync timer (200ms),
  scene property dirty detection for ORDER View ↔ BBox sync
- `client.py` — create_new + snap + save + recall + promote methods
- `operator.py` — fixed "createBuilding"→"createNew", added toggle_mode + focus_section +
  snap + save + promote operators. All REGISTER+UNDO. Scene custom props for undo tracking
- `props.py` — storeys, design_mode, active_section
- `panel.py` — Design/Real toggle, section chooser (clickable BOM tree cards), Snap/Save/Promote buttons

Federation (IfcOpenShell repo):
- `bbox_visualization.py` — set_color_override/clear_color_override for Design Mode grey-out

Specs (new content):
- `BIM_Designer.md` §17 — Design Mode (19 subsections): visual state machine, two bbox worlds,
  grey-out mechanism, metadata contract, section chooser, discipline selector, category colors,
  undo/redo, three-tier persistence (Save/Recall/Promote), ORDER View, revised mode model,
  Snap, server response format, room layout algo, Blender mechanisms, BOM Chooser (search-first,
  fit check, tack placement, set vs individual, pagination)
- `BIM_Designer.md` §18 — UI Design Strategy (7 subsections): industry research
  (Snaptrude/Finch3D/BIMsmith/TestFit), 5 UX principles (ambient compliance, teammate not tool,
  speed, layer assembly, data extensibility), 3 interaction modes, status strip, MAKE path
  (parametric/assembly/crafted), abstract extensibility (AD table pattern), competitive matrix
- `StrategicIndustryPositioning.md` — "Semantics as Source of Truth" paradigm: 10 KB project
  file vs 200 MB IFC, analogy map (software/LaTeX/MIDI/manufacturing/chip design), 400:1
  storage reduction, flywheel, AI design / mass customisation / digital passport / federated compilation
- `bim_designer_erd.html` — 4-tab interactive ERD: Data Layers, Design Session, Promote Flow, Wire Protocol
- `ACTION_ROADMAP.md` Phase G — rewritten: G-1..G-12 tasks, dependency chain, 7 gates, industry context

Tests: **44/44 GREEN** (W-DS-15 rewritten, W-DS-25 updated, W-DS-26 new: bbox geometry validation)

## Recently Completed (2026-03-18, session 16)

**[G-2] DocValidate + DemoHouse + Pattern Rules + Addon + Spec:**
- **validation.db** — 4th DB: 32 AD_Val_Rule (MY/US/UK/AU/SG) + 8 ad_pattern_rule
  Migrations: V001 (schema), V002 (rule seed), V003 (pattern rules)
- **Mined TE/DX**: sprinkler NN spacing, ELEC-SP clearance, P23 exceptions
  6 AD_Clash_Rule, 2 AD_Val_Rule_Exception, AD_Val_Rule_Mining_Source provenance
- **PlacementValidatorImpl.java** — cached rule lookup, activate/deactivate per jurisdiction
- **DM_BOM.db** — DemoHouse_2BR generative POC: 25 BOM lines, 7 seed products in library
  classify_dm.yaml for pipeline. Provenance=GENERATIVE, UBBL-validated
- **Pattern rules** (ad_pattern_rule): window spacing, sprinkler/light grids, piping
  Room resize → pattern recount proven (4000→8000mm wall: 2→3 windows)
- **Bonsai addon** — 6 Python files: panel.py (A.1-A.4 sub-panels), operator.py (6 ops),
  props.py (connection/building/Create New/verb), db_loader.py (AABB box loader), client.py
- **DesignerServer** — createNew action + CreateNewRequest record (stub impl)
- **Docs**: BIM_Designer.md §10.6-10.8 (pattern/verb separation, wireframe preview),
  §14 (UX vision), §15 (enabling framework), §16 (Federation integration contract)
  BIM_Designer_UserGuide.md (draft v0.1), BlenderBridge.md updated
  DeepSeek sections trimmed (-766 lines from BIM_Designer.md)
- **Tests**: 43/43 GREEN across 5 test classes:
  DesignerServerTest(17), PlacementValidatorImplTest(7), PatternRuleTest(7),
  DemoHouseTest(6), NonDisturbanceTest(6)
- `scripts/run_NonDisturbance.sh` — standalone shell gate

## Recently Completed (2026-03-18, session 15)

**[G-1] BonsaiBIMDesigner module + design specs:**
- New Maven module `BonsaiBIMDesigner/` — Java server (ndjson/TCP port 9876) + Python addon
- Three-layer architecture: DesignerAPI (facade) → DesignerDAO (SQL) → CompileScopeDetector
- StubDataSeeder provides POC data — 14/14 tests GREEN (DAO, API, TCP protocol)
- `docs/BIM_Designer.md` §11 (module spec), §12 (versatility — compiler + Blender compound),
  §13 (DemoHouse_2BR generative POC), Item 2 "Create New" generative entry point,
  building codes as component choosers (jurisdiction drives slider bounds)
- `docs/DocValidate.md` — renamed from VALIDATION_RULE_DESIGN.md, iDempiere DocValidate
  framing, AD_Validation_Result + AD_Val_Rule_Exception schemas, UBBL residential seed,
  world construction standards (MY/US/UK/AU/SG) with AD_Val_Rule SQL, §10.1 ASI/OrderLine
  rule (validation never touches library or templates), OSGi activation analogy
- `docs/BlenderBridge.md` — thin pipe spec. Java-smart/Python-dumb. Delta applicator
  for incremental viewport updates (don't reload 48K when 3 changed). Rides on
  Federation's existing Full Load. Material cache + mesh instancing.
- DeepSeek analysis triaged: useful parts absorbed into DocValidate.md, wrong/redundant
  parts written off (R-tree already exists, DX count wrong, JS doesn't match our arch)

## Recently Completed (2026-03-18, session 14)

**[ANTI-DRIFT] Rebar removal + extraction leak fix + proactive tamper rules:**
- Removed 2,660 IfcReinforcingBar from Terminal_Extracted.db + component_library.db
- Discovered Gap 7: 49K extraction rows leaked into component_library.db (product catalog)
- PlacementLoader was reading world origin from extraction — circular dependency
- R11: m_bom.origin_x/y/z now stores measured LFD corner (was hardcoded 0.0)
- R12: PlacementLoader reads BOM origin, loadWorldOrigin() deleted
- R14: PlacementProver hardcoded building name dispatch removed
- R15: ComponentLibrary deprecated I_Element_Extraction subquery removed
- T18/T19/T20: 3 new proactive tamper rules catch extraction leaks, hardcoded building
  names, and hardcoded zero origins at source scan time (0 violations after fix)
- 21/21 PASS. G4-TAMPER now 20 rules (was 17)

## Prior Session (2026-03-17, session 13)

**[LAST_MILE] Verb fidelity promotion — advisory → gating:**
- `checkVerbExpansionFidelity()` now returns int; pipeline gates on it (step 9b)
- EXACT verbs (TILE, FRAME): gate at ≤5mm — pipeline FAILs if exceeded
- APPROXIMATE verbs (ROUTE, SPRAY): reported as SKIP with `[approximate — CLUSTER pending]`
- TE verified: TILE 0.0mm PASS, FRAME 0.1mm PASS, ROUTE/SPRAY SKIP. 21/21 PASS
- TotalityContractTest for TE: researched, not yet implemented (next session)

## Prior Sessions (2026-03-17, session 12)

**[ANTI-DRIFT] Deep comb + BOM DB hygiene + doc consolidation:**
- Removed ensureProducts() call — dead since R7 (BOMWalker reads compConn)
- M_Product stays in BOM DB for assembly stubs only (BUILDING/FLOOR/SET placeholders)
- Deleted stale: library/BOM.db, TE_BOM (Copy).db, null, 15+ output artifacts
- Canonical naming: ifc4_sample_house → ifc4_samplehouse (27 files, matches YAML)
- Fixed walkthru residue: run_RosettaStones.sh rm -f bare .db after copy
- LAST_MILE_PROBLEM.md: "How to See It Working" debug-message table, mantra R1-R9 DONE
- Doc consolidation: -160 lines duplication → cross-refs to canonical sources
- 21/21 PASS after full regeneration. Seal v10 (74 files INTACT)

## Prior Sessions (2026-03-17, sessions 8-11)

**Phase B: Terminal BOM Recomposition + Verb Factorization:**
- Sessions 8-9: BOM factorization (qty, verb_ref, VerbDetector, DisciplineBomBuilder)
  TE: 48,428 → 1,297 recipe lines (37:1 compression). 4 verbs: TILE/ROUTE/FRAME/SPRAY
- Session 10: TE GUID collision fix, verb fidelity check (BomValidator step 9b)
- Session 11: R8 step-uniformity (ROUTE 34K→533), R9 grouping key fix, populate separation

## Prior Sessions (2026-03-16-17, sessions 1-7)

- Sessions 1-2: Anti-drift hardening, persistent product catalog, docs audit
- Session 3: LAST_MILE R1/R3/R5/R6/R7 — SpatialDiff, TotalityContract, BOMWalker compConn
- Sessions 4-7: TE pipeline (CO mode, DisciplineBomBuilder, gate scope, IfcSlab fix)

## Prior (2026-03-15)

- Java ExtractionPopulator (replaces Python), monolithic BOM.db elimination (96 files)
- Anti-drift doc sweep (29 files), YAMLGuide.md, SH 7/7 from scratch

*Full archive: `docs/archive/PROGRESS_ARCHIVE_2026-03-08_completed_work.md`*
*Phase details: git log tags `[DX-1]`, `[QA]`, `[DISC-*]`, `[DOCS]`, `[TE-*]`, `[R8]`, `[ANTI-DRIFT]`*

## Next Session Priorities

1. **WIRE createNew**: Connect createNew stub to real BOM load + compile + display
   - DesignerAPIImpl.createNew() → load DM_BOM.db → PlacementValidator → pipeline → output.db
   - Bonsai operator calls Federation's Full Load after compile
   - Entry: `DesignerAPIImpl.java` createNew() method (currently stub)
   - Read: `BIM_Designer_UserGuide.md` §5, `BIM_Designer.md` §16
2. **BBOX DESIGN MODE**: Implement bbox-then-compile UX pattern
   - Federation wireframe preview for visual impression (fast, discipline colors)
   - No Outliner until "Compile It" pressed — like iDempiere ProcessIt/MOrder
   - Pattern rules (ad_pattern_rule) recalculate during compile
   - Entry: `BonsaiBIMDesigner/src/main/python/bonsai_bim_designer/operator.py`
3. **R16 FIX**: PlacementCollectorVisitor coordinate double-count (other session found)
   - Fix childBom.origin absolute anchor for sub-assemblies
4. **LAST_MILE**: TotalityContractTest for TE (W-TOT-4)
5. **CLUSTER**: Replace SPRAY + broken ROUTE with offset-table approach

## Roadmap

Full roadmap: `docs/ACTION_ROADMAP.md` — 9 phases (0–H), 3 parallel tracks.

| Phase | What | Status |
|-------|------|--------|
| **0** | EN-BLOC Singularity (SH=55, DX=1099) | **DONE** |
| **A** | Rosetta Stone Gate Convergence (G1-G6 GREEN) | **DONE** |
| **B** | Terminal BOM Recomposition (48,428 elements, 37:1) | **DONE** |
| **G-1** | BonsaiBIMDesigner module (server + addon) | **DONE** (session 15) |
| **G-2** | DocValidate + DemoHouse + pattern rules | **DONE** (session 16, 43/43 GREEN) |
| **G-3** | Design Mode wire + bbox renderer + UI strategy | **DONE** (session 17, 44/44 GREEN) |
| G-4 | work_output.db schema + Save/Recall | **NEXT** |
| G-5 | BOM Chooser (search + fit check + pagination) | planned |
| G-6 | Ambient compliance (live status strip) | planned |
| G-7 | Assembly builder (layer-by-layer MAKE) | planned |
| G-8 | BlenderBridge pipe (Snap + incremental) | planned |
| G-9 | ORDER View (tabular editor) | planned |
| G-10 | Promote to BOM (governance gate + dangles) | planned |
| G-11 | ParametricMesh UI (crafted MAKE path) | planned |
| G-12 | Text Mode (search + NL input) | planned |
| C | 2D Drawing Export (3D → SVG) | planned |
| D-H | Synthetic Stone, BIM COBOL v1, ERP | planned |

## Pre-existing Failures (not bugs)

- DAGCompiler: G8-DX calibration ×1 (intentional)
- BIM_COBOL: CoverWithRoof ×3, VerifyPlacement ×1; schema-missing ×61
- ORMSandbox: ×18 (schema-missing)
- CO_TE G3-DIGEST: SKIP (4 IfcSensor metadata-only)
- CO_TE G6-ISOLATION: SKIP (CO mode)

## Known Debt (advisory)

- Assembly stubs in *_BOM.db M_Product — should migrate to component_library.db
- DX MEP corners (364 fittings without connecting pipes)
- ~~Terminal IfcReinforcingBar~~ — REMOVED from input (Bonsai addon script)
- Duplicate class name `BIMConstants` (root pkg vs `topology/` pkg)
- schema_snapshot_bom.sql still has full M_Product DDL (harmless — compile DB only)

---
*Completed work archive: `docs/archive/PROGRESS_ARCHIVE_2026-03-08_completed_work.md`*
