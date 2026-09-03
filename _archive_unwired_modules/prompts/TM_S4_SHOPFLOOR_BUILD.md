# ⚠ DO NOT REMOVE — S4 BUILD SPEC: "Generate Mfg Order — the e-Evolution shopfloor, re-fronted on our engine"
# Parent lane: prompts/TM_4D5D_VARIANCE_LANE.md §S4. Detail: prompts/TM_SHOPFLOOR_COSTING_SPEC.md.
# PRIME RULE: EXTRACT/COMPILE ONLY. Phases·crews·cost-elements are REAL in the seed; the ONLY generated layer is a
#   DOCUMENTED DETERMINISTIC split (fixed seed, no random/no Date.now, 'generated' marker). POC: best-of-industry
#   constants so users get a true SENSE the shopfloor works. Read the §-log after every run; §-log is the proof.

## §WHY (the opening, confirmed by search 2026-06-20)
The libero / e-Evolution Manufacturing plugin (the user authored it years ago for ADempiere→iDempiere) is NOT in
the core checkout — no `MPPOrder.java`, no generate-process anywhere on disk. Our seed carries the WINDOW SHELLS
(`AD_Window` 53009 Manufacturing Order · 53005 Manufacturing Workflows · 53004 Manufacturing Resource · 53145 Work
Order) + a `PP_Order` header (2 rows, full planned/actual + batch columns) — but the ROUTING (`AD_Workflow`,
`AD_WF_Node`, `PP_Order_Node`) and a front-facing GENERATE gesture do NOT exist. libero's front end was forms-heavy
and ugly. So this is our chance: give the data model a clean iDempiere-native front door + a 4D/5D playback it never
had — a free Mfg feature, because we already own the chrome, Zoom-Across, and the TM cursor.

## §HOW IT FALLS TOGETHER (the convention chain)
1. `PP_Order` is ALREADY a Main-Menu AD window (53009) — convention satisfied, nothing to add.
2. NEW: a Process (gear) on **C_Project → "Generate Mfg Order"** — the missing front door (libero triggered from
   C_Order; the project is the cleaner home: it already holds the 5D tree + the BIM element order).
3. The Process body = the **4D-GEN materialization**: read `injectGantt`'s already-computed {phase, resource, seq,
   start/end} and WRITE `PP_Order` (header) + `PP_Order_Node` (operations) + `AD_Workflow` routing, linked by
   `C_Project_ID`. Extraction, not invention — injectGantt IS the generator.
4. **TM plays the PP_Orders as the shopfloor** (setup spike → run accrual → overhead); Zoom-Across couples them
   back to C_Project / S_Resource / routing for free.

## §DATA — verified in `~/bim-ootb/erp/ad_seed.db` (2026-06-20)
PHASES (C_Project 990000 'Hospital') — the PlannedAmt each phase's PP_Order cost split MUST sum to, to the cent:
| Phase           | C_ProjectPhase_ID | PlannedAmt  | StartDate  | EndDate    |
|-----------------|-------------------|-------------|------------|------------|
| Substructure    | 990000            |     176,960 | 2026-06-13 | 2026-07-06 |
| Superstructure  | 990001            |  36,865,263 | 2026-07-06 | 2027-05-15 |
| MEP Rough-in    | 990002            |   5,258,285 | 2027-05-15 | 2028-11-09 |
| Architecture    | 990003            |  13,880,038 | 2028-11-09 | 2029-03-27 |
| MEP Final       | 990004            |   1,293,150 | 2029-03-27 | 2029-04-30 |
| Finishes        | 990005            |   7,245,783 | 2029-04-30 | 2029-05-14 |
CREWS (work-centers) — `viewer/rates.js` LABOR_RATES (EXTRACTED from boq_export.py), rate_per_day · crew_size:
  CONCRETE_GANG 145·6 · STEEL_ERECTOR 195·4 · MASON 155·3 · CARPENTER 165·2 · ROOFER 175·3 · FINISHER 135·2 ·
  HVAC_TECH 185·2 · PLUMBER 165·2 · ELECTRICIAN 175·2 · LABORER 95·1.  (NOTE: the seeded `S_Resource` rows are
  GardenWorld's mfg plants — for the POC we SEED these construction crews as `S_Resource` work-center rows, 'generated'
  marker, so every `PP_Order.S_Resource_ID` resolves to a real row AND reads as a hospital crew, not "Fertilizer Plant".)
COST ELEMENTS (`M_CostElement`, REAL — the buckets map to these ids):
  Material 100 (M) · Labor 105 (R) · Burden 50000 (B) · Overhead 50001 (O) · Outside-Processing 50002 (X).

## §GRAIN (locked)
**One PP_Order per (phase × crew).** Its `PP_Order_Node` operations = the element batches that crew runs in that
phase (the injectGantt {phase, resource} grouping that already exists). A batch == a lot that "lights together"
(W-SHOP-BATCH). Header dates = the phase window; `S_Resource_ID` = the crew; `C_Project_ID` = 990000.

## §MODEL — the deterministic cost split (per operation, money via site/bigdecimal.js)
  labor    = rate_per_day(crew) × crew_size × op_days           (EXTRACTED rate; op_days from injectGantt window)
  setup    = setupCost(crew) per LOT; perElementSetup = setup/N  (N = qtybatchsize; bigger lot → lower per-unit)
  burden   = labor × burdenPct(crew)                            (GENERATE — documented %, below)
  overhead = (labor + setup) × overheadPct                      (GENERATE — documented %)
  material = phasePlanned − Σ(labor+setup+burden+overhead)      (PLUG so Σ buckets == PlannedAmt to the cent;
                                                                 material is realistically the residual early-stage)
  → split labelled: labor/setup EXTRACTED-rate, burden/overhead/material carry 'generated'/'derived' markers.

## §INVENTION BOUNDARY — POC constants (best-of-industry, fixed, documented, reproducible)
  burdenPct: skilled trades 0.35, laborer 0.28 (payroll burden: insurance/taxes/benefits — US/SEA construction norm)
  overheadPct: 0.15 (site overhead + G&A on labor+setup)
  setupCost per lot (crew mobilization): crane/heavy = high, finish = low —
    STEEL_ERECTOR 8000 · CONCRETE_GANG 6000 · ROOFER 3000 · MASON 2000 · CARPENTER 1500 · HVAC_TECH 1800 ·
    PLUMBER 1500 · ELECTRICIAN 1500 · FINISHER 800 · LABORER 400.
  qtybatchsize (lot N): heavy-lift small lots, finish large lots — STEEL_ERECTOR 4 · CONCRETE_GANG 6 · others 10·20.
  ALL keyed on crew, NO random, NO Date.now → re-run byte-identical. §FALSIFIER: every id resolves a real seed row;
  the split sums to the stored PlannedAmt to the cent; only burden/overhead/material carry the generated marker.

## §STAGES (E0 first; §-log first; each ✅/⛔)
- **E0 ✅ DONE** — pure deterministic `genShopfloor` (`build/erp/tests/gen_mfg_shopfloor.js`) → {PP_Order[],
  PP_Order_Node[], costSplit[]} from the phase tree + crew grouping. Whitebox **W-SHOP E0 13/13** (sqlite3, no browser):
  Σ buckets == PlannedAmt to the rupiah · perElementSetup==setup/N · re-run byte-identical.
- **E1 ✅ DONE** — PERSISTED into the canonical seed via `build/erp/tests/bake_mfg_shopfloor.js --write` using the
  EXTRACTED libero convention (no invented model — schema pulled from `~/idempiere-dev-setup/idempiere/.../eevolution/model`
  X_PP_Order_Node / X_PP_Order_Cost): 9 crews→`S_Resource` Work-Centers · 16 `PP_Order` (C_Project_ID=990000, ETO/MTO
  tagged) · 16 `PP_Order_Node` (SetupTime/Duration) · 64 `PP_Order_Cost` (per real `M_CostElement`). Whitebox
  **W-SHOP-PERSIST 10/10** re-read from live seed. ⚠ ON DISK only — NOT yet committed/deployed (ships WITH E2 so it's
  witnessed end-to-end; browser reads via fetch+OPFS ad_seed_v16 → needs sw bump). Backup was git-tracked seed.
  TODO E1b: `AD_Workflow`/`PP_Order_Workflow` routing master + the **C_Project gear Process "Generate Mfg Order"**
  (window 53009 already in Main Menu) — currently PP_Order.AD_Workflow_ID unset (routing-master deferred, honest).
- **E2 ✅ DONE (board + 4D)** — Odoo-style **Kanban** (`build/erp/mfg_kanban.html` + pure adapter logic
  `build/erp/mfg_kanban.js`) over the persisted PP_Orders: columns swap phase / work-center / status; cards = MO-*
  with crew, lot N, ETO/MTO chip, date range, RM total + stacked cost-element mini-bar; **4D strip** = per-order
  gantt bars across the project window (Jun-2026 → May-2029), coloured by phase. Whitebox **W-SHOP-KANBAN 8/8**
  (`tests/test_mfg_kanban.js`, sqlite3 adapter): 6 cols · 16 cards · grandTotal == C_Project PlannedAmt to the rupiah ·
  every card folds 4 cost-element buckets · 4D bars within [0,1] · regroup keeps the total. RENDER PROOF: puppeteer
  headless `§RENDER cols=6 cards=16 fourdBars=16`, 0 pageerror, shot `/tmp/mfg_kanban.png`. STAGED in `~/bim-ootb/erp/`
  (html+js) — deploys with E1's seed as ONE bundle (sw bump pending). TODO: TM stacked-S-curve PLAYBACK (the scrub
  accrual) is E2b → W-SHOP-SCURVE; entry-point wiring (Mfg Order window 53009 kanban-view vs ⋯ pill).
- E3 — PP schedule-vs-actual dates → real per-op 4D date variance → W-SHOP-DATES.

## §WITNESSES (W-SHOP-*; §-log first)
  W-SHOP-ELEMENTS — Σ(material+labor+burden+overhead+setup) per phase == stored PlannedAmt to the cent; each bucket
    maps to a real M_CostElement id. §FALSIFIER: an unsourced bucket without the 'generated' tag.
  W-SHOP-BATCH — perElementSetup == setup/N; doubling N halves per-unit setup; deterministic same-in→same-out.
  W-SHOP-SOURCE (umbrella) — every crew→real S_Resource, every bucket→real M_CostElement; re-run byte-identical.
