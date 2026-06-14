# ⚠ DO NOT REMOVE — BIM → Project Order: the building (or any Find selection) folds into an
#   iDempiere C_Project (4D phases/tasks + 5D cost lines) → Generate-PO; round-trips back as an
#   "Actual" schedule; rollback via the scoped history dots.
# SCOPE (the verb bridge — sibling to prompts/BIM_TO_ERP.md, the noun bridge):
#   (A) GATEWAY — the Find panel #find-selected bar gains cost-on-selection + a `> to ERP` button.
#   (B) MULTI-SELECT — extend §NAV_FIND_002 Set model to type/category leaves.
#   (C) PUSH — selection scope IS the WBS level: nothing→C_Project, storey/disc→C_ProjectPhase,
#       type→C_ProjectLine, category→M_Product_Category. 4D=sequence_rules.json, 5D=rate pack.
#   (D) ROUND-TRIP — C_ProjectIssue delivery → "Actual" schedule version → split-screen Time Machine.
#   (E) ROLLBACK — mount common/history_bar.js scoped to the C_Project; first-dot = crudFoldBack scrub.
# STATE (2026-06-14): PAPER ONLY. Spec = docs/BIMtoProject.md (read it first, update as you build).
#   ad_full.db verified to carry c_project/phase/task/line/issue + c_order. Nothing else built.
# NON-NEGOTIABLE: EXTRACT/COMPILE ONLY — never invent a phase, date, rate, line, or asset. Phase/seq/
#   dates from sequence_rules.json (4D), price from the active rate pack (5D), qty from QTO. Money +
#   qty via site/bigdecimal.js ONLY (== Java BigDecimal, proven). `> to ERP` & `Check ERP` appear on
#   the Find selected bar; cost shown == cost pushed (same active pack). Push is idempotent (re-run =
#   +0). Rollback REUSES crudFoldBack — no second history lane, no bespoke ↺. Whitebox §-log FIRST is
#   the value proof; Playwright only drives/captures. Read the log after every run. Every claim names
#   a witness. Route buttons through the action fn, not a DOM .click() (the '=' lesson). Three
#   Concerns stay split (WHAT/HOW/WHERE — BOM PRINCIPLE).
# READ FIRST: docs/BIMtoProject.md (blueprint) · docs/BIMtoERP.md (noun bridge — M_Product/A_Asset
#   find-or-create is REUSED) · docs/ERP.md (ERP engine) · build/erp/ (ERP source-of-truth) ·
#   viewer navigate_find.js (#find-selected bar, §NAV_FIND_002 multi-select) · viewer nlp.js
#   (calcCost/getRate/RATES — the cost compute to surface) · viewer rates.js + rates/sequence_rules.json
#   (4D) + rates/<pack>.json (5D) · viewer time_machine.js (injectGantt, _capActive captured-schedule
#   seam) · common/history_bar.js (configure mountHostId/source/treeKey) · erp crud_overlay.js
#   (crudFoldBack/crudFoldForward) · memory feedback_numbers_via_bigdecimal · feedback_erp_source_of_truth.

---

## TASK 0 — ✅ DONE (W-PROJ-SCHEMA PASS, 2026-06-14) — Confirm canonical ERP.db + templates load
# Witness tests/poc_proj_schema.js (worktree feat/bim-to-project, commit d14af06): canonical = erp/ad_seed.db
# (OPEN-3 resolved), all 11 C_Project/C_Order/M_PriceList write targets present+populated; 4D=6 phases,
# 5D=CIDB Malaysia 2024/RM. Follow-on logged; C_ProjectIssue ABSENT (blocks Task D — add to seed).
## TASK 0 (orig) — Confirm canonical ERP.db + templates load (one query/read each) · Witness W-PROJ-SCHEMA
- Confirm the viewer-side canonical ERP.db (shared with BIM_TO_ERP OPEN-3) carries c_project*, c_order*.
- Confirm sequence_rules.json loads (Time Machine already uses it) + the active 5D rate pack loads via rates.js.
- **W-PROJ-SCHEMA:** `§PROJ_SCHEMA db=<file> proj=<bool> order=<bool> seq4d=<n phases> pack5d=<name>` — all named, not guessed.

## TASK A — ✅ DONE (W-FIND-COST PASS + CONTRACT PASS, 2026-06-14) — Cost on Find bar + 5D pack in Settings
# Witness tests/poc_find_cost.js (commit d14af06): selection cost PARTITIONS whole-building exactly
# (Σstoreys=Σdiscs=Σtypes=whole) + source-contract assert. navigate_find.js _selectionCost/_updateSelCost
# fold cost over selection GUIDs (every kind), #find-selected-cost span; panels.js "5D Rate Pack" picker
# (bim_5d_pack, live loadRateTemplate); rates.js initRateTemplate honours bim_5d_pack over locale.
# Same round(rate×qty) basis as analysis_sidecar; BigDecimal reserved for the push (Task C). NOT deployed
# (localhost only — needs SW v-bump + merge on GO). Browser visual-drive pending (Playwright not installed here).
## TASK A (orig) — Cost on the Find selected bar + 5D pack in Settings · Witness W-FIND-COST
Surface the EXISTING nlp.js cost compute (calcCost/getRate/RATES) onto #find-selected: show qty × rate
for the current selection in the active pack's currency. Add a 5D rate-pack picker to the Settings JSON
editor (the 4D sequence_rules.json is already there) — the picked pack drives BOTH the shown cost and the pushed price.
- **W-FIND-COST:** `§FIND_COST scope=<sel> elements=<n> qty=<q> cost=<bd> cur=<CUR> pack=<name>` — cost folds exact == Σ(qty×rate) golden; pack == active Settings pack.

## TASK B — Leaf multi-select · Witness W-FIND-MULTI
Extend the §NAV_FIND_002 Set model (`_selStoreys`/`_selDiscs`) to type/category leaves
(`_selTypes`/`_selCats`). Axis change still clears (unify rule). No new selection engine.
- **W-FIND-MULTI:** `§FIND_MULTI axis=<a> picked=<ids> elements=<n>` — set == union of ticked leaves; cleared on axis change.

## TASK C — `> to ERP` push: selection → Project Order · Witness W-PROJ-PUSH / W-PROJ-FOLD / W-PROJ-SEQ
`> to ERP` on #find-selected folds the current selection (nothing = whole building). Idempotent
find-or-create: header C_Project(value=building) → phases from sequence_rules.json
(name=phase, seqno=sequence, start/end from labour durations) → tasks by resource → lines per Type
(plannedqty from QTO, plannedprice from active pack via bigdecimal.js, m_product_id reusing
BIMtoERP §B find-or-create, m_product_category_id from parent) → roll-up amounts → phases with a
supplier c_bpartner_id set generateorder → fold C_Order PO (no supplier = plan-only, no PO).
- **W-PROJ-PUSH:** `§PROJ_PUSH project=<v> phases=+<np> tasks=+<nt> lines=+<nl> order=<co|->` — second run = all +0.
- **W-PROJ-FOLD:** Σ project plannedamt folds exact == nD 5D golden (BigDecimal).
- **W-PROJ-SEQ:** `§PROJ_SEQ phase=<name> seqno=<n> start=<d> end=<d>` — name+seqno trace to sequence_rules.json; dates == Time Machine planned dates.

## TASK D — Round-trip: Actual schedule + split-screen Time Machine · Witness W-PROJ-ACTUAL / W-PROJ-ALLOC / W-TM-SPLIT
Fold C_ProjectIssue.movementdate/movementqty + C_ProjectPhase.enddate/iscomplete into a SECOND
ELEMENT_PLACE set tagged version=actual (never overwrite planned). Ride Time Machine's captured-schedule
seam (_capActive / injectGantt) — Actual = a captured schedule sourced from ERP. Allocate issued qty to
GUIDs by the deterministic rule: light N of M elements of that Type within the issue's phase, ordered by
seqno. Split-screen: planned | actual on one shared cursor; var_days where actual lags planned.
- **W-PROJ-ACTUAL:** `§PROJ_ACTUAL phase=<name> planned_end=<d> actual_end=<d> var_days=<n>` — actual dates trace to movementdate/enddate, never estimates.
- **W-PROJ-ALLOC:** `§PROJ_ALLOC type=<T> issued=<q> lit=<n>/<m> order=seqno` — lit == issued qty (cap M); seqno-deterministic, re-run identical.
- **W-TM-SPLIT:** planned + actual render under one cursor; var_days == W-PROJ-ACTUAL.

## TASK E — Rollback via scoped history dots (NO new UI) · Witness W-PROJ-ROLLBACK
Mount common/history_bar.js in the Project Order panel: configure({mountHostId:<panel>, source:'doc',
treeKey:<C_Project>}) → its own ‹ dots ›. First-dot click → crudFoldBack scrubs the project to zero
rows; forward → crudFoldForward redo. Committed docs fold through the status FSM (CO→DR via
setDocStatus), not delete — no special gating. No bespoke ↺ button.
- **W-PROJ-ROLLBACK:** `§PROJ_ROLLBACK project=<v> dots=<n> foldback=<bool> rows_after=0` — first-dot fold-back leaves zero project rows; re-push restores identical counts (idempotent).

## ── §H FOLLOW-ON SUB-LANES (sequence after C–E; each standalone, none blocks the core) ──
# All reuse this lane's plumbing (GUID join, signed fold, the two templates). EXTRACT-only, BigDecimal.

## TASK F — Model-delta → signed Variation Order (do FIRST of §H) · Witness W-PROJ-VO
REUSE viewer/variation_order.js (FIDIC Cl.12 + AACE + EVM; ADDED×1.0/REMOVED×0.3/CHANGED×1.3). Model
revision → GUID diff → price the delta → fold a SIGNED C_Order amendment (or C_Project variation line),
painted on model, in the history dots. Reversible via crudFoldBack. Today it only emits Excel — fold to ledger.
- **W-PROJ-VO:** `§PROJ_VO rev=<r> added=<n> removed=<n> changed=<n> delta=<bd> co=<id>` — delta == variation_order.js golden; counts == GUID diff.

## TASK G — Progress claim / payment certificate (cash-in side) · Witness W-PROJ-CLAIM
% complete per phase (from §D Actual) → certify → fold C_Invoice/C_InvoiceLine progress billing
(C_Project.projinvoicerule governs; set the rule value). Paint model claimed/certified/disputed.
- **W-PROJ-CLAIM:** `§PROJ_CLAIM phase=<name> pct=<%> claimed=<bd> certified=<bd> invoice=<id>` — claimed == Σ(lineplannedamt×pct); ties to C_ProjectLine.invoicedamt.

## TASK H — Handover: live as-built asset register + FM work order (7D) · Witness W-PROJ-HANDOVER
On C_ProjectPhase.iscomplete, capitalize lines → A_Asset (reuse BIMtoERP §B GUID→A_Asset) with
warranty/serial/O&M/insurance (a_asset_info_*/a_asset_delivery). Tap element → R_Request maintenance.
- **W-PROJ-HANDOVER:** `§PROJ_HANDOVER assets=+<na> warranties=<n> request=<id|->` — one A_Asset per completed-phase GUID; R_Request traces to element GUID.

## TASK I — 4D lookahead procurement (just-in-time) · Witness W-PROJ-LOOKAHEAD
Phase startdate − supplier lead time = need-date → fold M_Requisition/M_RequisitionLine (or PO) with
required-by. No item without a real need-date.
- **W-PROJ-LOOKAHEAD:** `§PROJ_LOOKAHEAD window=<6wk> items=<n> reqs=+<nr>` — required-by == startdate−leadtime; non-invent.

## TASK J — Embodied carbon parallel ledger (6D/ESG) · Witness W-PROJ-CARBON
Carbon budget on project; carbon actual folded per C_ProjectIssue (each delivery posts embodied carbon
like cost) from templates/6D_carbon.json. Budget vs actual painted on model.
- **W-PROJ-CARBON:** `§PROJ_CARBON budget=<tco2e> actual=<tco2e> var=<tco2e>` — actual == Σ(issued qty × carbon factor); factors trace to 6D_carbon.json.

## TASK K — Excel reporting (the standard QS/PM layouts) · Witness W-PROJ-REPORT
REUSE the engines — NO new Excel writer: ERP reports (claim/VO/EVM/asset) → erp/ninja_excel.js
(dictionary-driven, verify-by-example to the cent); viewer quick exports (BoQ/schedule) →
viewer/excel.js (SheetJS) + export_4d/5d.js; iDempiere layouts → erp/report_overlay.js foldPrint
(W-PRINTFORMAT). Report set: BoQ · Progress Claim/Payment Cert · VO register · Cost/EVM · Schedule ·
Asset register (columns + conventions in docs/BIMtoProject.md §I). Title block stamps currency +
rate-pack source; section subtotals → grand total; ONE workbook / multiple sheets when bundled.
HARD RULE: verify-by-example — totals fold == golden to the cent, else the report FAILS.
- **W-PROJ-REPORT:** `§PROJ_REPORT type=<boq|claim|vo|evm|sched|asset> rows=<n> total=<bd> golden=<bd> match=<bool> cur=<CUR> pack=<name>` — match=true; pack/currency stamped; subtotals sum to total.

## DEPLOY / TEST
Localhost only until EXPLICIT GO. Whitebox §-log first; add effect-level specs (not routing-only).
`node tests/audit_specs.js` must not add violations. Update docs/BIMtoProject.md §status as built.
ERP writes go to build/erp/ ONLY (source-of-truth); never touch deploy/live/.
