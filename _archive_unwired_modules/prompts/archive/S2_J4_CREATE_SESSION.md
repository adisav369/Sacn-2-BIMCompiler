# ⚠ DO NOT REMOVE — SESSION CARD: GRAND LANE S2 / J4 CREATE (the signed New)
# Scope: make iDempiere's **New REAL** — create a sales order via iDempiere's OWN New (the CRUD seam, NOT the
#   ring visual): pick the tenant's own BPartner + product, price/defaults fill via the AD callout, Save = ONE
#   signed op (commitCrud → CRUD_CREATE → signed commitGroup → persist), the new row APPEARS in the grid and
#   SURVIVES reload, and the private draft-restore boundary holds. The ring stays Glass-only (doctrine §0).
#   R→E→V, ONE bounded leg. Deserves fresh budget — this is the create twin of J5; don't rush.
# Read FIRST: prompts/GRAND_LANE_STRATEGY.md §0 doctrine + §3 S2 + prompts/ERP_CRITIC_UX_LANE.md (J4) +
#   docs/ERP_COVERAGE_MATRIX.md (engine is oracle-equivalent; most 🟡 = render-wiring, NOT engine gaps).
# Log Mandate: read the witness §-log before any conclusion — exit code is NOT evidence.
# Worktree: clean /tmp/wt-* off FRESH origin/main (J5 landed as #329; start the follow-up off origin/main, never
#   re-use the squash-merged branch). Edit bim-ootb ONLY via /tmp/wt-*.

## CONTEXT — what J5 (S1) already shipped, that S2 builds on (#329, sw v694, do NOT re-derive)
- The host write seam is PROVEN: `__crud.process` (DOC_ACTION) is wired; the CRUD field verbs (`commitCrud` →
  CRUD_CREATE/UPDATE/DELETE → signed `commitGroup` → persist) are the SAME proven lane. `_serializeCommit` (the
  batch async-tear fix) + `_ensureStore` (loads crud_ops `__meta` for the host lane) + `_overlayDocTip` (read-the-tip
  for DocStatus) all exist in crud_overlay.js?v=12 / idempiere.html.
- The NEW pill already opens the CRUD ring create form (idempiere.html `_openCrudRing`, item 5) and Save = `#cfSave`
  (`_formSave`). `crud_ops.json` carries c_order (entry + fields). `__crud.applyOp`/`__crud.store` exposed.
- `overlay:committed` listener already refolds the active window on a commit (AD_* + DOC_ACTION arms exist).

## R — THE GAP TO VERIFY (live, before code) — diagnose with a witness FIRST
- **A new row does NOT yet APPEAR in iDempiere's grid.** `_records = ADD.readRecords(db, …)` reads the IMMUTABLE
  bundle only; `_overlayDocTip` overlays DocStatus via `readTip` but NOT new/edited/deleted rows. The CRUD
  CREATE op lands in the kernel op-log as a synthetic row (`CORE.listTip` folds CRUD_CREATE → row with pk `-opId`,
  CRUD_UPDATE → field overlay, CRUD_DELETE → tombstone) — but nothing in idempiere.html consumes `listTip` for
  DATA tables (ad_parser.setTipSource consumes it for AD_* dictionary tables only). So a created order is signed
  + persisted but INVISIBLE in the grid. PROVE this with the J4 witness before fixing (the J5 pattern).
- **Price/defaults callout:** confirm the AD callout fires on the create form (ad_callout.js — m_product → price,
  bill/ship defaults). If the served create form already runs callouts, REUSE; if not, that's the second leg.

## E — THE BUILD (sourced; mirror the J5 _overlayDocTip pattern — generalize, don't fork)
1. GENERALIZE the host read overlay: extend `_overlayDocTip` (or add `_overlayListTip`) to fold `CORE.listTip`
   over `_records` — created rows APPEAR (synthetic pk), CRUD_UPDATE edits overlay, CRUD_DELETE tombstones hide.
   Hydrate the lazy sidecar (the `withSidecar` pattern already used) so it SURVIVES RELOAD. Repaint via renderBody.
   NOTE: listTip already handles stdDefaults (actor/client/org/Created/IsActive) — non-invent, reuse verbatim.
2. New via iDempiere's own New → the CRUD create form → pick the TENANT's own BPartner + product (the grid/form
   must be AD_Client-scoped — gating is load-bearing, W-CRITIC-GATING); price + bill/ship defaults via the AD
   callout. Save = ONE signed CRUD_CREATE op (commitCrud, already signed + persisted).
3. Draft-restore boundary: an unsaved create stays PRIVATE (no official dot) until Save — verify W-DRAFT-RESTORE
   still holds across the create flow (Save = publish boundary).

## V — WITNESS W-CRITIC-CREATE-LIVE (erp/tests/poc_critic_*_live.js, §-tagged, 0 pageerrors)
- ASSERT: New → fill BP+product (tenant's own, gated) → Save = ONE signed op (CRUD_CREATE, verifyChain=ok); the
  new row APPEARS in the grid + SURVIVES reload (listTip overlay); price defaulted via callout (not hand-typed);
  draft boundary holds (no official dot pre-Save). Oracle-diff the created order's derivable fields where applicable.
- REGRESSIONS GREEN: recinfo / draft / blue / grid-batch / form-pill / gridstatus / **J5 process** (poc_critic_process_signed_live).
- SHIP: clean /tmp/wt-* off fresh origin/main · sw CACHE_VERSION bump (clean line) + `internal/SW_CHANGELOG.md`
  entry · bump `crud_overlay.js?v=` in idempiere.html if crud_overlay.js changed · auto-merge · VERIFY it lands.

## AFTER J4 → J6 POST + REPORT (S3: posting-preview / Accts-Posted GL to the cent). Then P2 → P3 → P3.5 → P4.

## STANDING (carry-over, already LIVE — do NOT rebuild)
- J5 PROCESS ✅ DONE/LIVE (#329, sw v694, W-CRITIC-PROCESS-LIVE 18/18): pill/bar/grid-batch → `__crud.process` →
  signed commitProcess → completeFanout → persist; DocStatus read-the-tip survives reload; host DocAction NOT
  owner-gated (iDempiere = role+FSM+period); Odoo NULL-doctype fan-out gated honestly; GardenWorld POS RE→CO real
  fan-out (ship+invoice in op-log). Grand Lane §0 carries the engine north star + automation law (DERIVE/VALIDATE/
  ACT by op-log effect; foreign imperative code = user plugin; op-only invariant). Forethought banked — don't rebuild.
