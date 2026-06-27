# PROGRESS — Current Development State

> **Rule:** PROGRESS.md is a thin status file. No specs here — specs live in `docs/` and `prompts/`. Keep this file under 80 lines.

## Current State

**Gate:** `./scripts/run_RosettaStones.sh` — S190 fleet: 116/157 PASS, 4 ALL GREEN (BR,MO,RL,WI). 21 buildings. 9-gate system.

| PFX | EL | GATES | Notes |
|-----|----|-------|-------|
| BR | 33 | 9/9 | ALL GREEN |
| MO | 2791 | 9/9 | ALL GREEN |
| RL | 1 | 9/9 | ALL GREEN |
| WI | 1 | 9/9 | ALL GREEN |
| DX | 1169 | 8/9 | MetadataMissing (IfcOpeningElement) |
| SH | 65 | 8/9 | MetadataMissing (generative MEP) |
| TE | 48428 | 8/10 | C8 mesh diversity, GEO no pairs (federated) |

**Pipeline:** 11 stages. 77 verbs. 7403 products (ERP.db). 4-DB architecture.

## disc-walker density-fix (area-scaled n_measured) — SHIPPED+LIVE 2026-06-28 (lane/benchmark-clash-resolution)
**Done:** placer 708k explosion root-caused + fixed, RouteWalker-aligned (count bounded by measured quantity ×
real ARC occupancy envelope, NOT bbox area). `bake_duplex_rules.py` stamps measured `src_storey_area_m2`;
`disc_walker.js` area-scales fixture counts + places on `occupancy()` cells (fixtures only — network routes).
SampleCastle PLB **708158→752** (×940). Deployed bim-ootb **#558 MERGED**, sw v5, duplex_rules.db content_sha
7551d63b7f57 — live-verified. Docs clash-collapse table corrected (SC 501 vs 3) + redeployed.
**Witnesses:** `witness_disc_walk_density` 43/0 (D-COUNT EXACT, D-ENVELOPE void=0); terminal §DWG 49/0 UNCHANGED;
§DXG 12/0; nnchain landed R2 5315 segs posDrift=0 (1e-6); round-trip PLB GREEN/ELEC WEAK/ACMV RED.
**Doctrine (user, load-bearing):** fidelity needs a ground truth — LANDED routed-endpoints exact 1e-6;
GENERATED fixtures = plausible position, EXACT count, no rmse-as-fidelity. ERP.db-along-route = generated layer,
exact-landing UNCONFIRMED (next audit). Spec=`prompts/RESUME_DISC_WALKER_ENVELOPE_BOUND.md`.
**Also this session:** ModellerGuide §Walk·Disciplines doc + review arc (prior, deployed). 
**ERP ground-truth audit DONE 2026-06-28** (`build/erp/AUDIT_WALK_GROUNDTRUTH.md`, 3 witnesses re-run 63/0): walk
produces 2 honestly-labelled classes — **LANDED** routed segs (Terminal PLB 4314+ACMV 1001, real→real, posDrift=0
≤1e-6, over-bound=0 → exact-landing CONFIRMED for the routing layer) + **GENERATED** count-exact density fixtures
(PLB/ELEC, count==Σround(density×area)|envelope, position explicitly no-fidelity).
**F-WALK-1 ✅ CLOSED 2026-06-28** (`witness_disc_walk_erp_landed.js` W-TRM-WALK-LANDED 4/0): walked MEP-rich Terminal
thru ERP.db TRM001 views → 5315 landed segs IDENTICAL to terminal_rules.db (L2 from_guid/to_guid/xyz/gap/bound),
non-vacuous (L1 segs>0), 28174 placements ≡ (L3), all on real geometry ≤1e-6 (L4). LANDED-layer ERP-consume path
proven, not asserted.
**F-WALK-2 ✅ CLOSED 2026-06-28** (`witness_disc_walk_roof_bound.js` W-TRM-ROOF-BOUND 10/0): `stamp_terminal_src_area.py`
stamps MEASURED src_storey_area_m2 (z-band footprint from Terminal) on all 37 terminal placement rules; reconcile
carries it into ERP.db ad_placement_measured + rule_placement view (TRM001 regenerated, diff = placement layer ONLY,
both DBs 0-mismatch). Roof now AREA-SCALED not bbox-tile-capped: SC roof 233374→15273 (×15, envelope-bound,
count-exact B2 0-tol, prov=placed:array-density, rules≡erp); DX 3659. Uniform model w/ duplex. Full disc-walker
suite **77/0** (nnchain6+density43+erp-equiv14+erp-landed4+roof-bound10); F-WALK-1 equivalence PRESERVED.
**Engine honesty contract: every walked set now LANDED (real→real 1e-6) or count-exact GENERATED w/ measured density.**
⚠ DEPLOY follow-up: re-stamped terminal_rules.db NOT yet pushed to bim-ootb/modeller (live copy pre-stamp = drift;
engine proven, deploy separate). Still open: F-WALK-3 4/11 PLB routing rows empty src_guids (provenance nit, doesn't
affect landing — endpoints come from live building).
**NB (scope, user-flagged):** these fixes harden the rule/POSITION layer (LANDED + count-exact). They are NOT mesh
placement. Walk render today = uniform 0.18m InstancedMesh marker cubes (`modeller.html:_renderDiscWalk`), segs not
drawn. Java-equivalent LOD meshes (catalog geometry) = SEPARATE RosettaStone/compiler track; disc-walker doesn't feed it.
**Next:** deploy re-stamped terminal_rules.db to bim-ootb; F-WALK-3 backfill routing src_guids; walker LOD-mesh render
(cylinders on landed segs + catalog geometry on fixtures, replace marker cubes) = the toward-Java-placement track.

## SC IFC2BOM onboarding + Modelling-from-cascade vision — 2026-06-23 (branch lane/benchmark-clash-resolution)
Cards: `prompts/RESUME_DROP_OUTLINER_ROADMAP.md` §1, `prompts/MODELLING_FROM_BOM_CASCADE.md`, `prompts/ONTOLOGICAL_BOM_EXTRACTION.md`.
- **SC (IFC2x3 residential; files historically named schependomlaan) now compiles: 2/4 → 7/10 gates, oracle minted.** Done in IFC2BOM (4 Java files + classify_sc.yaml):
  - 362-drop fixed — root was a `Unknown` storey-container silently dropped (NOT type-coverage). `IFCtoBOMPipeline` now RECOVERS unmapped containers via `SpatialContainerConfig.discover` instead of dropping.
  - New per-building `reconciliation_tolerance` (yaml, mirrors geometry_fail_threshold) for genuine source catalog-identity duplicate-collapses; SC=6 → delta −6 within tol = PASS.
  - **MEP routed out of BOM by authoritative `elements_meta.discipline`** (`StructuralBomBuilder.isSpatialDiscipline` keeps ARC/STR, routes rest → DISC/RouteWalker). Generalizes the per-building MEP class-list that leaked SC's 60 IfcFlowSegment. REB already filtered.
  - `expected_elements` now = actual placeable (`leafSUM(qty)+composition`) → compiler count gate reconciles (3516==3516).
  - Regression-checked: SH 65/65, DX 192+73=265 UNCHANGED. No compiler/spatial code touched.
- **Remaining SC gates (3, SC's first compile, deeper/separate):** 1 critical placement proof (hard threshold 0 for EXTRACTED), C8 mesh diversity (5 window/door variants = catalog geometry gap), geo_verify drift (known-stale harness — don't hand-roll).
- **Vision banked:** the BOM cascade IS the modelling grammar (subtree move/delete/swap re-folds; 2D×3D grid stretch≠scale w/ host-constrained openings + roof pitch invariant; cascade-derived LOD; signed-foldable-portable BOM = the novelty). SC cascade proven walkable: BUILDING→11 FLOOR→99 SET+52 ASSEMBLY→3516 leaves.
- **Next:** spec construction-verb BOM grammar (WALL/SLAB/ROOF/OPENING = the unlock) OR drop re-measure vs oracle OR SC fidelity gates. Prompt edits are local (prompts/ gitignored); Java+yaml committed this session.

## Benchmark & Clash-Resolution lane (branch lane/benchmark-clash-resolution) — 2026-06-21
Spec `prompts/BENCHMARK_AND_CLASH_RESOLUTION_LANE.md`. Phase A in progress (re-targeting all BIM measures LTU→Terminal 48k).
- ✅ **A3** measure scripts re-targeted to Terminal + re-run (witness logs in `build/erp/measure_*.log`):
  - Pick (W-PICK-MEASURE): median **3.5ms** (min 1.8 / p100 455.5), 2441 draw objects (672 BatchedMesh + 1769 InstancedMesh).
  - Rich clash (W-CLASH-NARROWPHASE): broadphase 4000 pairs in **47ms**, rich verdict **5.14ms/pair**; CLASH **2220** · NEAR-MISS(<50mm) **737** · CLEAR 1043.
- ✅ **A1** IfcClash STR-vs-MEP on 594MB Terminal IFC (`scripts/measure_ifcclash.py`, W-IFCCLASH-TERMINAL): **184.76s end-to-end** (parse 40.4s + tessellate STR 1032 @1.6s + MEP 2419 @138.3s + clash), **77 clashes** with TRUE depth/type (pierce 150mm, protrusion 281.7mm). Replaces "tens of seconds" estimate. NB: counts NOT comparable to our 2220 (different scope — IfcClash=2-disc intersection on IFC-class sets; ours=4000 cross-disc capped candidates). The comparable thing = WORK: 185s tessellate-every-run vs our 47ms SQL broadphase over pre-stored boxes.
- 📌 FACT CORRECTION: Terminal_meta `elements_meta` discipline split is **ARC 35552 · PLB 8175 · ACMV 1570 · STR 1032 · FP 989 · ELEC 833 · MEP 277** (sums to 48428), NOT the lane-header labels. STR selector=IfcBeam/IfcColumn/IfcMember, MEP=IfcFlowTerminal/IfcFlowController.
- ✅ **ERP bench page (`bench_suite.html`) PUBLISHED + LIVE** at https://red1oon.github.io/BIMCompiler/bench_suite.html, linked from MigrateComparison paper + Cross-ERP Rosetta Stone + docs nav. Evolved heavily this session:
  - Dynamic code/size chips from `bench_facts.json` (`scripts/measure_codebase.js`, W-CODEBASE-FACTS, both sides measured): **50.7× LOC · 25.8× MB · 132× tables**; live same-origin recount on the app host, baked off docs.
  - Single-station dial `[ON NETWORK] <> [LOCALHOST]` → network-type chips (LOCALHOST/LAN/OFFICE/CLOUD/WAN, greyed→lit) from `bench_localhost.json` (`scripts/measure_localhost_bench.js`, W-IDMP-LOCALHOST = real `bench_oplog_pg.log` storage primitive). Drives ONLY ⑤ Peer Sync (pure network round-trips: LOCALHOST 1× → WAN ~3571×); other cards network-independent (honest).
  - Card ② repurposed → **Daily 10K + EOD Backup** (live: ~488ms batch + 28.9MB backup ~12ms). ⑤ verifyChain moved to last. Short "What this checks" hint on all 5 cards. Pause now freezes housekeeping (`sleepPausable`).
- ⏳ **A2 iDempiere full-stack 10K = pending.** Booted the real server OK (Release 13 on :8088, DB `idempiere` 1076 tables, `scripts`/env via console-setup) but **this build has NO REST plugin** (404 /api/v1) and ZK-webui automation is impractical → can't script a full-stack batch here. DB-layer 10K already covered by the storage primitive. Server shut down, env restored. To get the real number: add/build the REST plugin or a standalone iDempiere Java client.
- NEXT: A2 full-stack iDempiere (needs REST) · Phase B penetration depth · Phase C-F (mid-flight correct, resolution, semantic clash, incremental reclash, 4D/5D cost).

## TM 4D/5D variance + 360 loop — MERGED+LIVE 2026-06-21
- ✅ 360 loop + kanban/pivot + shopfloor S-curve LIVE (bim-ootb PR #462 sw v684) → `prompts/TM_4D5D_VARIANCE_LANE.md` (+ `prompts/RESUME_360_KANBAN_PIVOT.md`)

## Ninja Create (PackOut/PackIn) — 2026-06-14
- ✅ **SHIPPED** Create face on the Plugin Engine pill (bim-ootb PR #301, erp sw v673): drop .xlsx model sheet →
  preview → Emit & Install through the writable `window.__idmpDb`. Witness `§NINJA-DOM-WITNESS PASS`
  (headless-chrome on the real deploy scripts). Card `prompts/NINJA_MODE_PILL.md` (done).
- ✅ **Witnessed** behaviour teaching sample: `build/erp/fixtures/plugins/asset_status_callout.mjs` +
  `scripts/poc_asset_status.js` → **W-ASSET-STATUS PASS** (callout fires via the `AD_Column.Callout` seam; both
  falsifiers hold). Doctrine documented `docs/ERPUserGuide.md §9` (one .foldbundle = structure+behaviour ≡ 2Pack+JAR).
- ✅ **TWO-WAY ENGINE DONE 2026-06-14** (Opus, feat/erp-substrate-phase012): (1) `NinjaStage.extractModel(db,AD_Window_ID)
  →model` reverse-export — **W-NINJA-EXTRACT** `roundtrip=MATCH` (72285fee) · (2) `Col@class.method` grammar token
  → `AD_Column.Callout` auto-wire — **W-NINJA-CALLOUT** dispatch fires `derived={Description:'Ready'}` (82320be6) ·
  (3) structural-only round-trip caveat documented (3b6b590a).
- ✅ **W-NINJA-EXPORT DONE 2026-06-14** (Opus, 95d0136a) — the workbook-serialize leg: `build/erp/ninja_export.js`
  (`modelToRows`/`modelToWorkbook`/`exportWindow`/`exportBlob`, inverse of `parseRomo`). Full round-trip
  **DB→extractModel→workbook→XLSX bytes→re-read→parseSheet == original**, 21/21 tables MATCH (starter + 19-table
  HRMIS), §FALSIFIER ghost→null. Hardened `extractModel` master-FK detection (LAST *_ID col, not first — fixed
  HRMIS tables with a user `_ID` col before the real FK; W-NINJA-EXTRACT still PASS).
- ✅ **W-NINJA-EXPORT-LIVE SHIPPED 2026-06-14** (bim-ootb PR #309, erp sw v681): Create face "Export an existing
  window" picker (live AD `<select>` → `exportBlob` → `.xlsx` download); `ninja_stage.js?v=2` (extractModel now
  deployed) + `plugin_overlay.js?v=4` + new `ninja_export.js`. Live DOM smoke in headless chrome on the deploy
  bundle: pill→Create→picker 370 windows→stage Ninja window→Export→`AST_Asset.xlsx` re-parses EXACT to
  extractModel (6 grammar cols). **`§W-NINJA-EXPORT-LIVE PASS`.** Item 1 of §OUTSTANDING = DONE.

## Reflexive AD self-edit — engine legs DONE (2026-06-14, Opus)
- ✅ **W-AD-OPLOG-DISTRIB** (`scripts/poc_ad_oplog_distrib.js`, e3e677cd) — dictionary edit → signed append-log →
  re-folds to the SAME dictionary on a 2nd node (verifyChain ok both sides; §FALSIFIER load-bearing). "Mail the append log."
- ✅ **W-AD-SELFEDIT** (`scripts/poc_ad_selfedit.js`) — edit `AD_Field` → form's displayed set re-folds 26→25→26
  = rebuild is re-read, not recompile.
- ✅ **W-AD-SELFEDIT-LIVE SHIPPED 2026-06-14** (bim-ootb PR #312, erp sw v683) — a signed dictionary edit
  repaints the form on the spot, no reload. 3 legs: `ad_parser.js?v=23` `setTipSource` (AD_Field/AD_Window
  reads overlay the sidecar edit via `CrudOverlay.listTip(window.__crud.kernelDb(),…)`); `crud_overlay.js?v=7`
  emits `overlay:committed`; `idempiere.html` wires the tip-source + a refold hook (AD_* commit → invalidate
  `_openWins` + re-`openWindow`/`buildMenu`). Live witness (headless chrome on the bundle): M_MatchInv
  "Organization" grid column vanishes on `IsDisplayed Y→N`, returns on `N→Y`; commit sealed+verifyChain=ok.
  **`§W-AD-SELFEDIT-LIVE PASS`.** Reflexive-AD loop now proven engine + distribution + LIVE DOM.

## Odoo red-band fold-gap — RE-AUDITED (2026-06-14, Opus)
- ✅ **W-ODOO-QWEB** (`scripts/poc_fold_qweb.js`, 852dea16) — `CORE.foldQWeb` folds Odoo invoice line-loop to the cent
  (`price_subtotal=4350.00 maxDiff=0c`); 41/41 QWeb defs extracted → `build/erp/odoo_extras.db`.
- ✅ **Server actions = NOT a code gap** — `§SRVACT-CLASSIFY code=64` all Python, no declarative subset; honestly deferred.
- Panel re-published: https://red1oon.github.io/BIMCompiler/migrate_status_panel.html (44 surfaces, live-verified).

## AD_Process FOLD lane — P1 GeneratePO + P2-leg1 GenShipment DONE/LIVE (2026-06-17, Opus)
- ✅ **P1 ProjectGenOrder (AD_Process 164) KIND-2 fold** — bim-ootb PR #352, erp sw v704, ad_process.js?v=2.
  Folds C_Project → C_Order via `erp_engine.buildDoc` (newVerbs=0). Source-corrected: **Sales** order (not PO),
  Qty=PlannedQty−InvoicedQty; getProject gate → honest `project-not-ready` rejection. **W-PROC-GENPO** +
  **W-PROC-GENPO-LIVE** (poc_proc_genorder.js / poc_genpo_live.js, both EXIT 0).
- ✅ **P2-leg1 InOutGenerate (AD_Process 118) KIND-2 fold** — bim-ootb PR #355, sw v706, ad_process.js?v=3.
  Folds a CO Sales Order → M_InOut shipment via the createShipment archetype (newVerbs=0). Source-extracted:
  toDeliver=QtyOrdered−QtyDelivered; DeliveryRule 'A'→min(toDeliver,onHand) (Availability cap), others
  named-deferred; gate CO+SO. **W-PROC-SHIP** (fold==independent re-derivation, cap load-bearing, falsifier) +
  **W-PROC-SHIP-LIVE** (poc_proc_inout.js / poc_genship_live.js, both EXIT 0). Demand audit: 451 used procs =
  148 KIND-1 / 16 KIND-2 / 287 KIND-3. NEXT: C_Invoice_Generate (119, KIND-2 order→invoice), report procs.

## Archive — DONE/shipped (one-line pointers; detail in cards + memory topic files)
- POS gap-close banked — `prompts/POS_GAP_CLOSE.md # DONE` (2026-06-12g2)
- WH×POS pick lane BUILT, live-verified — `prompts/WH_POS_PICK_LANE.md # DONE` (2026-06-13)
- Multi-lane WAVE 3 — `prompts/MULTI_LANE_WAVE3.md # DONE` (2026-06-12e)
- Multi-lane WAVE 2 — `prompts/MULTI_LANE_LAUNCH.md # DONE` (2026-06-12)
- MIGRATE_POSTING_CONFIG — bim-ootb PR #271 sw v653, IDB ad_seed_v15 (2026-06-12b)
- POS lens addon §P-1..§P-4 — `prompts/POS_LENS_SESSION.md # DONE` LIVE (2026-06-12)
- ERP backend-gap arc — `prompts/ERP_BACKEND_GAP.md` (feat/erp-substrate-phase012, 2026-06-09)
- Backend lane DATA + ENGINE-SEAM half — D2/D3/R2 + C0 + readPostings (2026-06-03)
- Lens-family doctrine — published docs (2026-06-03)
- FRONTEND Item C Accts-Posted lens — bim-ootb PR #94 sw v565 LIVE (2026-06-03)
- iDempiere Renderer #1 (I1) + master-detail drill — sw v560, PR #82/#83/#84 (2026-06-02)
- LENS family lane-3 chrome fleet — PR #92 gh-pages LIVE (2026-06-03)
- STEP-0 §SEAM-FROZEN host conformance — record-panel deliverable (2026-06-03)
- Migrate ShowMe + ERP folder home — LIVE (2026-06-02)
- Lens family phone∥desktop one engine — SPEC hardened + 2 witnesses (2026-06-03)
- Engine POST plugin §13.1 — accounting genome PROVEN (2026-06-02) → [[project_glassbowl]]
- ERPMaker/AnyAppMaker docs + Odoo fold source (2026-06-02) → [[project_erpmaker]]
- Holy Grail doc + falsifier POC prompts + MIT license sweep (2026-06-01)
- ERP Secured/Distributed doctrine + 6-witness POC suite + W-CHAIN live (2026-06-01) → [[project_erp_secured_phase]]
- Glassbowl engine-as-data explorer + lifecycle chain + orbit viz — `docs/GLASSBOWL{,_DOSSIER}.md`, LIVE → [[project_glassbowl]]
- Viewer S-series (S188–S286): browser viewer, DLOD, mobile perf, find/nav, multi-format import, cinematic — see MEMORY.md "Project — Shipped"

## OCI Deployment

- Live: `bim-ootb-live` (SYSNOVA landing + viewer + single DBs). Always upload here.
- Single DB per building: `buildings/{Name}_extracted.db` (metadata + geometry + bbox).
- `deploy/sandbox/` stale (last ~S225) — not used for deploy. `deploy/dev/` is canonical.
- Deploy SOP: `deploy/OCI_UPLOAD.md`

## Earlier Work (compressed)

- **S200-S210:** BIM OOTB browser viewer, OCI deployment, BOQ charts, health checks
- **S195-S198:** Direct DB streaming (replaced Blender .blend pipeline)
- **S188-S193:** RTree, nD engine, DLOD — all Blender-era, superseded by browser viewer
- **S165-S186:** GN instances, chunked loading, cockpit UI — GN HALTED, RTree won
- **2D Layout:** Phase A closed, Java pipeline 5/5, 13/13 conformity. Browser DXF viewer (S236).
- **DAGCompiler:** S190 fleet 21 buildings. S104 IFCtoERP complete.

## Reference

- Docs site: https://red1oon.github.io/BIMCompiler/
- Academic paper: `docs/SPATIAL_COMPILATION_PAPER.md`
- OCI setup: `internal/OCI_SETUP.md`
