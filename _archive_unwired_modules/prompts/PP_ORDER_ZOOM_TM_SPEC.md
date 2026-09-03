# ⚠ DO NOT REMOVE — SPEC: "PP_Order (manufacturing/project order) Zoom-Across → BIM Viewer TimeMachine"
# Parent lane: prompts/TM_4D5D_VARIANCE_LANE.md (§S4 shopfloor) · resume: prompts/RESUME_360_KANBAN_PIVOT.md
# PRIME RULE: EXTRACT/COMPILE ONLY. No invented destinations, no invented cursor positions. Whitebox §-log
#   first (read the log; exit code ≠ evidence). Money via site/bigdecimal.js. Verify vs ~/idempiere-dev-setup.
#   Honour this block + "read the log after every run" until DONE.

## §ISSUE THIS PROVES
The 360 loop today starts from C_Project / C_ProjectLine (→ Viewer Find → TM at an element's moment). It does
NOT start from the **manufacturing/project order (PP_Order)** — the 5D shopfloor record. This spec adds the
PP-side entry: a PP_Order Zoom-Across that lands the BIM Viewer **TimeMachine** at *that order's construction
moment*, with the shopfloor S-curve + ⚖ variance drawer coupled. One identity (`PP_Order_ID` → `C_Project_ID`
→ phase), four folds.

## §DOCTRINE COMPLIANCE (TM_4D5D §DOCTRINE)
1. Zoom-Across = the coupling fabric. PP_Order reaches the TM by EXISTING `_ID`/identity, not a bespoke link:
   `PP_Order.C_Project_ID` → `C_Project.Value` (the building) ; `PP_Order.Description` phase token → the gantt
   phase window (the SAME axis `tmJumpToPhase` scrubs). No new FK, no invented destination.
2. Read the twin, don't recompute. Cost shown = the stored `PP_Order_Cost.CumulatedAmt` the S-curve already folds.
3. Honest labels. The cursor lands on the order's PHASE window (real shared key). If that phase is absent from
   the loaded scene's op-log (e.g. Hospital geom in OPFS), fall back to a date-proportional position on
   `[_projectStart,_projectEnd]` and LABEL it `projected` in the §-log — never blur the two.
4. Invention boundary. The phase token is EXTRACTED from `Description` ("<Phase> — <CREW> (ETO custom build)",
   produced deterministically by gen_mfg_shopfloor.js); splitting on " — " is extraction, not invention.

## §DATA (verified erp/ad_seed.db, branch off main + re-bake)
- Hospital PP_Orders: `PP_Order_ID >= 9900000`, `C_Project_ID = 990000`, `DateStartSchedule`/`DateFinishSchedule`
  (2026-06-13 … 2027-…), `Description = '<Phase> — <CREW> (ETO custom build)'`, `S_Resource_ID` = a work-center.
- `PP_Order_Cost` (per order × M_CostElement Material/Labor/Burden/Overhead) `CumulatedAmt` Σ == C_Project PlannedAmt.
- main has the CODE (`_loadShopfloor`, `?tm=`, `tmJumpToPhase/Element`, ZoomAcross registry) but only 2 demo
  PP_Order rows → re-bake (idempotent: `node build/erp/tests/bake_mfg_shopfloor.js <seed> --write`) → 16 + 2 = 18.

## §IMPLEMENTATION
### A. ERP side — erp/idempiere.html (the Zoom-Across destination)
- Extend `_zoomScope(ctx)` to handle `tn === 'pp_order'`:
  - `projId = rec.C_Project_ID`; require `>= 990000` (BIM band) else null.
  - `bld = c_project.value` for that projId (the building). `ppOrderId = rec.PP_Order_ID`.
  - return `{ bld, find:null, tm:{ order: ppOrderId } }` (extend the scope shape; existing callers ignore `.tm`).
- Register a SECOND ZoomAcross destination `id:'timemachine'`, label `'BIM TimeMachine'`:
  - `available(ctx)`: `_zoomScope(ctx)` resolves AND that scope has `.tm` (i.e. it's a PP_Order). Pure otherwise.
  - `launch(ctx)`: `_connectEnable()`; build `viewer.html?db=../buildings/<bld>_extracted.db&bld=<bld>&home=…`
    `&tm=1&pporder=<order>`; `window.open`; `console.log('§ZOOM-ACROSS launch id=timemachine bld=… pporder=…')`.
  - Keep the existing `viewer` destination unchanged (it stays available for C_Project / C_ProjectLine; a
    PP_Order resolves a `.tm` scope so BOTH could list — that's fine, the chooser shows both; PP_Order is the
    only table that yields `.tm`).

### B. Viewer/TM side — viewer/time_machine.js (the landing)
- New `window.tmJumpToOrder(ppOrderId)` → Promise<bool>:
  1. `activate()` if not `_active`.
  2. `_loadShopfloor()` (draws/refreshes the S-curve; tolerate null).
  3. Fetch the order's `Description` + `DateStartSchedule`/`DateFinishSchedule` from `../erp/ad_seed.db`
     (same fetch idiom as `_loadShopfloor`). Phase = `Description.split(' — ')[0].trim()` (null-safe).
  4. Land the cursor: if that phase has ops in `_ops` → reuse the phase-window logic (cursor = phase winStart,
     like `tmJumpToPhase`) and log `mode=phase`. Else project `DateFinishSchedule` proportionally between the
     min/max shopfloor dates onto `[_projectStart,_projectEnd]` and log `mode=projected`.
  5. `renderAtTime(_cursor)` + `anchorFromCursor()` + `configSlider()`; open the ⚖ drawer (`_twin`) like the
     other jumps; ensure the S-curve panel is shown.
  6. `console.log('§TM_ORDER_JUMP order=<id> phase="<p>" mode=<phase|projected> cursor=<ms> at=<ISO> built~<pct>%')`.
- Extend the `?tm` init handler: after `activate()`, read `?pporder=<id>`; if present call `tmJumpToOrder(id)`.
  (Keep `?tm=play` behavior unchanged; `pporder` implies tm=1.)

## §WITNESS (whitebox §-log first — the proof; live visual deferred = Hospital geom in OPFS)
- `viewer/tests/test_pp_zoom_tm.js` — W-PPZOOM-TM:
  1. ERP scope: load ad_seed.db (sql.js), pick a Hospital PP_Order rec → assert `_zoomScope`-equivalent resolves
     `bld='Hospital'`, `tm.order=<id>`; assert the launch URL contains `tm=1&pporder=<id>`.
  2. TM jump: slice `tmJumpToOrder`'s resolver against ad_seed.db → assert phase extracted from Description, the
     order's DateFinishSchedule parsed, and the cursor-landing path (`phase` when phase∈_ops fixture, else
     `projected`) is the EXPECTED one with a finite cursor in `[_projectStart,_projectEnd]`.
  3. Cost: Σ the order's PP_Order_Cost CumulatedAmt > 0 and equals the S-curve fold for that order.
  Each assertion NAMES the issue it proves. Re-run regressions: test_tm_variance.js (S1) · test_zoom_cost_panel.js
  (S2) · test_tm_broadcast.js (S3) · gen/bake_mfg_shopfloor (E0/E1).

## §SHIP
- Branch off fresh `origin/main` (has all merged wiring); re-bake seed; sw.js bump + KEEP-BOTH precache
  (time_machine.js + idempiere.html already precached — bump CACHE_VERSION, add nothing new unless a new file).
- PR → main → verify live (curl markers: §ZOOM-ACROSS launch id=timemachine, §TM_ORDER_JUMP, 18 PP_Order in seed).

## §STATUS
- [x] spec (this file)
- [x] worktree off main (/tmp/wt-ppzoom, branch feat/pp-zoom-tm) + re-bake seed → 18 PP_Order / 64 cost (W-SHOP-PERSIST 10/10)
- [x] A. ERP destination — _zoomScope(pp_order) + ZoomAcross id:'timemachine' (the SAME red pill #pill-zoomacross)
- [x] B. TM tmJumpToOrder(id) + ?pporder deep-link (mode=phase | projected, honest label)
- [x] W-PPZOOM-TM 7/7 PASS (viewer/tests/test_pp_zoom_tm.js) — whitebox, real ad_seed.db
- [x] ship — PR #468 squash `a600a77` on main; erp sw v738 / viewer v690. LIVE-verified: timemachine dest +
      tmJumpToOrder in served code, served ad_seed.db = 18 PP_Order / 64 cost. Branch + worktree cleaned up.
# NOTE: build/erp has no idempiere.html/ad_seed.db (ERP app is bim-ootb-native /erp/); seed SOURCE = the
#   generator build/erp/tests/gen_mfg_shopfloor.js (unchanged) → no source-of-truth mirror owed.
