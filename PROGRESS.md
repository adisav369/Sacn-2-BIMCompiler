# PROGRESS — Current Development State

## Current State

**Gate:** `./scripts/run_RosettaStones.sh` — **SH 7/7, DX 7/7, TE 7/7 (all GREEN). TACK-FIX code applied but NOT yet re-tested — gates reflect pre-TACK-FIX run.**

| Gate | SH | DX | TE |
|------|----|----|-----|
| G1-COUNT | PASS (55) | PASS (1099) | PASS (48428) |
| G2-VOLUME | PASS (+0.00%) | PASS (+0.00%) | PASS (+0.00%) |
| G3-DIGEST | PASS | PASS | PASS (IfcSensor removed session 14) |
| G4-TAMPER | PASS | PASS (0 violations / 20 rules) | PASS |
| G5-PROVENANCE | PASS (7 checks) | PASS (7 checks) | PASS (7 checks) |
| G6-ISOLATION | PASS | PASS | PASS (IfcSensor removed session 14) |

**Pipeline:** 9 stages. 63 verbs, 196 witnesses. Seal v11 (74 files INTACT).

**Rosetta Stone Buildings:**

| Building | Mode | Elements | Status |
|---|---|---|---|
| Ifc4_SampleHouse (SH) | EN-BLOC | 55 | GREEN (7/7) |
| Ifc2x3_Duplex (DX) | EN-BLOC | 1099 | GREEN (7/7) |
| SJTII_Terminal (TE) | EN-BLOC | 48,428 | GREEN (7/7) |

## What's Next

**[SRS HARDENING] SRS consistency sweep DONE (session 24). Remaining:**
  - TACK-FIX code compiles but is NOT tested — deliberate hold.
    Testing is a **focused expedition** after SRS is fully vetted.
  - Drift audit: `docs/LAST_MILE_PROBLEM.md` R17-R24 (status check)
  - Spatial predicate verbs (BIM_COBOL §20) — implement P0 predicates when
    PlacementValidator M-rules move from SPEC ONLY to code

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

## Recently Completed (2026-03-19, session 24)

**[SRS] Consistency sweep + Spatial Predicate Verbs:**

SRS consistency sweep — 7-point audit across docs:
- TACK_FIX_SPEC.md §1.1: LBD tack classified as **ERP-maths** (not schema gap).
  No IFC relationship for positional convention; arithmetic on extracted AABB is correct.
- Extraction pipeline audit: 3 IFC rels currently extracted (Material, DefinesByType,
  ContainedInStructure). 4 NOT extracted (R21-R24) — existing docs accurate, no new finding.
- ConstructionAsERP.md §2: Drift fixed — clarifying note added distinguishing
  output.db C_OrderLine (WHAT-only) vs work_output.db C_OrderLine (WHAT+WHERE with tack).
- BlenderBridge.md: No change needed — downstream of compilation, Schema-Not-Geometry
  doesn't affect the thin pipe.
- TerminalAnalysis.md: SPRAY/ROUTE verb factorization classified as **ERP-maths**.
  Pattern recognition on extracted positions is manufacturing recipe logic.
- G4_SRS §4: `listOrderLines` action added to wire protocol table (G-9 scope).
- TestArchitecture traceability matrix: 3 new rows (BOM Outliner §17.19, YAML v3 §7,
  BIM_COBOL §20 spatial predicates). Gap counts updated (6 SQL SEEDED, 14 SPEC ONLY).

BIM_COBOL.md §20 — Spatial Predicate Verbs (~200 lines):
- 13 predicates in 3 tiers: element-to-element (DISTANCE_BETWEEN, CLEARANCE_BETWEEN,
  INTERSECTS, HOST_OF, NEAREST), set queries (SAME_LEVEL, SAME_COLUMN, WITHIN,
  ALONG_PATH, COPLANAR), statistical (GRID_PITCH, NEAREST_NEIGHBOUR_STATS, SPAN_COUNT)
- SQL implementation sketches over elements_meta + elements_rtree
- Java SPI: SpatialPredicate<T> interface in PredicateRegistry
- M-rule coverage: 13 of 17 rules use at least one predicate
- P0 priority: 4 predicates covering 10 of 13 spatial M-rules
- Schema-Not-Geometry integration: predicates are the approved ERP-maths channel;
  HOST_OF upgrades transparently from AABB_PROXIMITY to FK join when R21 lands
- Cross-references added to BBC.md §2 and DocValidate.md §15.6

Traceability Matrix: 18 PASS, 3 IMPLEMENTED, 6 SQL SEEDED, 14 SPEC ONLY, 3 PENDING.

Doc hygiene:
- AUDIT_PIPELINE.md, TECHNICAL_BUILDING_GUIDE.md → archived (stale, superseded)
- ConstructionAsERP.md TODO-ST-7 marked RESOLVED (orientation columns exist)
- ConstructionAsERP.md P0.1-ORIENT marked DONE
- PROGRESS.md line 5 fixed (was "SH 7/8 FAIL", actually 7/7 GREEN)
- TestArchitecture.md §4.3 status → IMPLEMENTED (was CODE EXISTS)
- VerbPatternArchitecture.md recipe count updated (1,442 → 1,297 after R8/R9)

Analysis docs pruned and focused as per-stone guardrail references:
- TerminalAnalysis.md: factorization debt section → DONE status, stale phases removed,
  FP count fixed (6,867 → 6,863), Schema-Not-Geometry classification added
- DuplexAnalysis.md: stale prior-estimate comparison removed
- InfrastructureAnalysis.md: speculative risk narrative trimmed
- TE_MINING_RESULTS.md: stale "next steps" → completed status
- NEW: SampleHouseAnalysis.md — SH identity card, discipline breakdown (verified
  against extraction DB: ARC=35, STR=20), gate status, guardrails

Extraction DB verification: component_library.db counts match all analysis docs
(SH=55, DX=1099, TE=48,428). TE discipline breakdown verified (8 disciplines).

## Recently Completed (2026-03-19, session 23)

**[SRS] V004 mined rules + spawn detail + Non-Disturbance + YAML v3 review:**

V004_mined_rules.sql — 13 new AD_Val_Rule rows (M2-M8, M11, M13-M17):
- M2: FP branch pipe max length (12000mm, ROUTE_SEGMENT_SUM)
- M3: FP riser diameter (50mm main, 25mm branch)
- M4: Light fixture grid spacing (max 5000mm, advisory WARN)
- M5: Light fixture ceiling Z offset (200mm deviation tolerance per storey)
- M6: Column bay spacing (typical 8500mm, ±2000mm advisory)
- M7: Beam span vs column spacing (beam ≤ bay width + 5%)
- M8: Roof tile pitch (495×150mm, TILE_VERB_FIDELITY)
- M11: MEP×ARC fire wall penetration (2 AD_Clash_Rule, MATERIAL verdict)
- M13-M15: Vertical continuity (CW 50mm, STR 25mm, FP 50mm drift tolerance)
- M16: Opening face-anchor (10mm tolerance, skip partitions <150mm)
- M17: Opening host association (AABB_PROXIMITY method, 200mm tolerance)
- 3 new AD_Val_Rule_Exception for TE ELEC-SP under-150mm pairs
- 1 AD_Val_Rule_Exception for SH exterior door flush-face
- typical_spacing_mm params for sprinkler (3500mm) and light (3000mm) grids

G4_SRS §2.1 step 3 — ConstructionModelSpawner spawn sequence fully detailed:
- 3d-i: C_OrderLine creation from BOM DFS (host_type derivation, parent tracking)
- 3d-ii: CO_EmptySpaceLine creation (tack_from m→mm, storey/room metadata)
- 3d-iii: M_AttributeSetInstance defaults (LEAF products only, width/depth/height)
- 3e: PP_Order_Node routing templates (RE: 9 nodes, CO: 6 nodes with dependencies)
- 3f: PlacementValidator READONLY mode (never BLOCK during spawn)
- 3g: W_Variant v0 pointer with counts

Non-Disturbance analysis (G4_SRS §6):
- M16 vs SH: 3/4 windows PASS, 1 at 7mm WARN → tolerance widened 5→10mm.
  Exterior door at 75mm → flush-face exception. Partition skip rule added (<150mm).
  DX: orientation axis issue identified → MIN(w,d) method.
- M17 vs SH/DX: No host_id in extraction → AABB_PROXIMITY fallback.
  All SH/DX openings have wall match within 200mm. TE curtain wall TBD.
  R20 dependency noted (extraction pipeline host_id column).

YAML v3 review (G4_SRS §7):
- ProcessIt() pattern confirmed sound
- Grid pitch formula fixed: pitch = min(max, dim/ceil(dim/typical))
  Was using max_spacing as pitch (wrong — max is limit, not target)
- typical_spacing_mm params added to V004 for sprinkler and light rules
- Occupancy class → AD_Val_Rule_Occupancy join confirmed working

Schema-Not-Geometry rule codified (BBC.md §2 + DocValidate.md §15.6):
- "If a rule uses AABB arithmetic, that's a missing extraction column."
- Full audit of M1-M17: 4 SQL OK, 5 ERP-maths OK, 8 SCHEMA GAP
- 8 gaps mapped to IFC relationships:
  R21: `IfcRelVoidsElement` → `host_element_ref` (M16/M17)
  R22: `IfcRelConnectsElements` → `I_Element_Connectivity` (M13/M14/M15)
  R23: `IfcRelInterferesElements` → `I_Element_Interference` (M9/M10)
  R24: `IfcRelFillsElement` → `fire_stop_product_ref` (M11)
- LAST_MILE_PROBLEM.md R21-R24 added, priority order documented
- Decision tree for new rules: SQL → IFC rel column → ERP-maths → out-of-scope

BOM Outliner spec (BIM_Designer.md §17.19):
- Relational tree editor: BOM hierarchy (how it's MADE, not where it IS)
- All operations are FK/ASI updates, not geometry: drag SET, swap product, reorder
- Three views share C_OrderLine data: BBox (spatial), ORDER (tabular), Outliner (tree)
- UIList v1 (panel-based), Blender Collections v2 (native Outliner integration)
- Wire protocol: listOrderLines action, recursive tree response
- G-9 scope expanded: ORDER View + BOM Outliner
- IFC-already-solves-geometry insight codified in BBC.md §2

Traceability Matrix updated: 5 SQL SEEDED rows, gap counts adjusted
(18 PASS, 3 IMPLEMENTED, 5 SQL SEEDED, 12 SPEC ONLY, 3 PENDING).

## Recently Completed (2026-03-19, session 22)

**[SRS] BBC.md deep walk-through + LAST_MILE cross-check + G4 master-detail:**

BBC.md 10 drift vectors found and fixed (D1-D10):
- D1: Gospel Principle scoped (EXTRACTED traces to IFC, GENERATIVE traces to template)
- D2: §4.3 centroid drift marked FIXED (was stale "next fix")
- D3: §4.2 witnesses marked IMPLEMENTED (was stale "pending")
- D4: Count invariant qualified (non-PHANTOM leaf lines only)
- D5: EN-BLOC wording clarified (accumulates tack, doesn't evaluate constraints)
- D6: §1.1 "no switch" acknowledged legacy DSL-path violations
- D7: Zero-size AABB semantics defined (placeholder scope, ScopeBomBuilder skips)
- D8: Origin convention codified (only BUILDING has non-zero origin, R16 lesson)
- D9: ESLine tack_from mirror invariant W-ESLINE-TACK-1 specified (pending WALK-THRU)
- D10: Tack convention extends to work_output.db C_OrderLine

LAST_MILE cross-check (8 issues found, all resolved):
- R16 Actions table updated to DONE (was stale DIAGNOSED)
- TE G3/G6 updated to PASS (IfcSensor removed session 14)
- Challenge Mantra rewritten (R1-R16 all DONE)
- Gap 4: 8th spec source added (user design via promote path)
- R17/R20 coupling documented (R20 before R17, mvn test between)
- TACK_FIX_SPEC: pipeline coordination test §4.4 added (FIX-2 chain)
- G4_SRS: derived counts (no >= minimum bounds), W-TACK-WO-1 test spec
- TestArchitecture: G4-TAMPER scope extension note for BonsaiBIMDesigner

G4_SRS v1.1 — master-detail DocStatus model:
- Sub-work-orders: DR (spawned) → IP (editing) → CO (saved to output.db)
- Master: DR → IP → AP (strict compliance gate) → CO (promoted to BOM)
- AP is EXCLUSIVE for BOM creation (host tack + dangles + validation)
- W_Variant → pointer to sub-C_Order (no snapshot_json duplication)
- Save creates sub-order, Recall copies (non-destructive), Approve gates
- W001 schema: Parent_Order_ID added, AP in CHECK, is_active on W_Variant
- 4 new test specs (approve lifecycle, promote requires AP)
- BIM_Designer.md §17.10 updated: 4-action model, interface contract

Traceability Matrix added to TestArchitecture.md:
- 38 rows mapping BBC.md/G4_SRS/DocValidate/TACK_FIX → test → witness
- 18 PASS, 3 IMPLEMENTED, 14 SPEC ONLY, 3 PENDING
- Gap rule: no code without checking matrix first

## Recently Completed (2026-03-19, session 21)

**[SRS] G-4 pre-code specs + TACK-FIX code + TE mining + drift audit:**

SRS artifacts (6 new/updated docs):
- `migration/W001_work_output_schema.sql` — 12-table DDL: C_Order, C_OrderLine (tack dx/dy/dz + ASI FK),
  M_AttributeSetInstance/Instance, CO_EmptySpace/Line (3D tack_from + capacity), PP_Order_Node/Product,
  W_BuildingConfig, W_Variant (snapshot_json), W_Validation_Result, AD_SysConfig
- `docs/TACK_FIX_SPEC.md` — FIX-1/2/3 method specs, pipeline sequence diagram, W-TACK-1 state machine
- `docs/G4_SRS.md` — 4 sequence diagrams (CreateNew+Spawn, Save, Recall, Promote), Design Mode state
  machine, 4 test spec classes (WorkOutputDAO, ConstructionModelSpawner, SaveRecall, Wire), wire protocol table
- `docs/TE_MINING_RESULTS.md` — M1 sprinkler (bimodal 3000-4500mm), M4 light grid (~4000mm),
  M5 ceiling Z (per-storey constant), M6 column (multi-modal 5/8/12m bays),
  M12 ELEC-SP clearance (ERP-maths: 11 overlaps, 35 under 150mm — not AABB)
- `docs/YAMLGuide.md` — Schema v3 spec: MEP rules-based laying, ProcessIt() pattern,
  AD_Val_Rule-driven placement, ERP-maths clearance (no Bonsai dependency)

TACK-FIX code (compiles, NOT YET TESTED):
- `ScopeBomBuilder.java` — minX()-setMinX (was centroidX()-ox), returns setLbdPositions
- `VerbDetector.java` — detectCluster() minX() (was centroidX())
- `FloorRoomBomBuilder.java` — accepts floorLbdWorld + setLbdPositions + bldgMin params
- `IFCtoBOMPipeline.java` — computes floor LBD world positions, passes to FloorRoomBomBuilder

Drift audit (`docs/LAST_MILE_PROBLEM.md` Gap 8):
- BOM DB: CLEAN (pure m_bom/m_bom_line/M_Product/ad_sysconfig)
- component_library.db: 3 violations (V1: 49K extraction rows still present, V2: 60+ ad_* config
  tables for DSL generative path, V3: dead ad_bom/ad_bom_child). R17-R20 actions added.
- Compiler main code: CLEAN (zero I_Element_Extraction references, T18 guards)

Cross-references: ACTION_ROADMAP.md (G-4 entry), BIM_Designer.md (§17.19), ConstructionAsERP.md
(§1.4 work_output.db + w_* prefix), MEMORY.md (5-split DB, 3 new doc map entries)

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
| G-9 | ORDER View + BOM Outliner (tabular + tree editors) | planned |
| G-10 | Promote to BOM (governance gate + dangles) | planned |
| G-11 | ParametricMesh UI (crafted MAKE path) | planned |
| G-12 | Text Mode (search + NL input) | planned |
| C | 2D Drawing Export (3D → SVG) | planned |
| D-H | Synthetic Stone, BIM COBOL v1, ERP | planned |

## Pre-existing Failures (not bugs)

- DAGCompiler: G8-DX calibration ×1 (intentional)
- BIM_COBOL: CoverWithRoof ×3, VerifyPlacement ×1; schema-missing ×61
- ORMSandbox: ×18 (schema-missing)
- ~~CO_TE G3-DIGEST: SKIP~~ — PASS (IfcSensor removed session 14)
- ~~CO_TE G6-ISOLATION: SKIP~~ — PASS (IfcSensor removed session 14)

## Known Debt (advisory)

- Assembly stubs in *_BOM.db M_Product — should migrate to component_library.db
- DX MEP corners (364 fittings without connecting pipes)
- ~~Terminal IfcReinforcingBar~~ — REMOVED from input (Bonsai addon script)
- Duplicate class name `BIMConstants` (root pkg vs `topology/` pkg)
- schema_snapshot_bom.sql still has full M_Product DDL (harmless — compile DB only)

---
*Completed work archive: `docs/archive/PROGRESS_ARCHIVE_2026-03-08_completed_work.md`*
