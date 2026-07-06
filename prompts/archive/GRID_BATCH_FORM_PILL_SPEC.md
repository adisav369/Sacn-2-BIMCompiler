# ⚠ DO NOT REMOVE — SPEC for FRONTEND_LANE_MASTER §OUTSTANDING items 4 + 5 (browser legs)
# Scope: pure idempiere.html / pills_idmp.json / idmp_pills.js host edits. NO engine gap, NO crud_overlay change.
#   Consume the EXISTING FSM seam (window.AdDocFsm) + CRUD ring + report_overlay — never fork a verb.
# §-log first (READ the witness log after every run). GO before deploy. Witnesses W-GRID-BATCH + W-FORM-PILL-ACTIONS.

## ITEM 4 — GRID MULTI-SELECT + GEAR BATCH PROCESS  (Witness: W-GRID-BATCH)
PROBLEM: `buildGrid` is single-select (`_recIdx`, row-click→form). No checkbox, no batch. iDempiere lets you
multi-select rows + run one DocAction over all (Complete N orders, etc.).
DESIGN (host DOM only):
- STATE `_gridSel` = Set of record PK strings (per loaded `_records`), reset whenever `_records` reloads
  (renderActiveTab:1261). Selection survives a grid re-render (keyed on PK, not index).
- buildGrid: prepend a checkbox `<th>` (select-all) + a checkbox `<td>` per row. Checkbox `change` toggles the
  row's PK in `_gridSel` (stopPropagation so it does NOT open the form). Select-all toggles every visible row.
  Mobile cards (`_buildCards`) get the SAME per-row checkbox in the header (stopPropagation).
- BATCH BAR `#idmp-batchbar` (the "gear" host): shown above the table only when `_gridSel.size>0`. Renders
  `⚙ N selected` + the LEGAL DOCACTION INTERSECTION across the selected DOC records + a ✕ clear. The legal set
  is the SAME FSM path `buildDocActionBar` uses, factored into ONE helper `_fsmCtx(tab,rec)` (legal[] + dispatch).
  Only doc-table records (tableId 259 or AdDocFsm.DOC_FAMILY) with a DocStatus contribute; a non-doc selection
  ⇒ no batch actions (honest empty bar with the count + clear only).
- CLICK a batch action ⇒ fan `_fsmCtx(tab,rec).dispatch(act)` over every selected record; on each ok set
  rec.DocStatus=to; re-render grid (selection persists on still-legal rows). ONE summary §GRID-BATCH log line:
  `action=CO selected=N ok=k rejected=(reasons)`. In-memory transition (same grain as buildDocActionBar; the
  signed persist path stays the kernel-ops/CRUD lane — NOT invented here).
FALSIFIER: with 0 selected the bar is absent; a mixed/illegal selection shows only the actions legal for ALL.
WITNESS W-GRID-BATCH (live DOM, tests/poc_grid_batch_live.js): open a DocAction window in GRID view → tick ≥2
  rows → assert `#idmp-batchbar` shows the count + ≥1 legal action → click it → §GRID-BATCH ok=k logged + each
  ticked row's status chip advances → clear (✕) empties the bar. 0 pageerrors.

## ITEM 5 — PROCESS (+New/Save/Print) IN PILL FOR FORM VIEW  (Witness: W-FORM-PILL-ACTIONS)
PROBLEM: red-pill "just the pill" (default, `body.idmp-clean`) hides `#idmp-toolbar` (CSS:117). In clean form
view the record toolbar (New/Save/Process/Print) is GONE — only the form body + DocAction body-bar remain.
DESIGN (registry pills, form-view-gated — NO crud_overlay change; bind BY ID to real handlers):
- NEW GATE `window.IdmpPillFormGate()` = `!!_session && _curTab() && _viewMode==='form'`. idmp_pills.js evaluates
  `showWhen:"form-view"` exactly like `posting-doc`/`pos-station` (BUILD + every `_applyStage`). Host calls
  `IdmpPills.setDocContext()` at the end of `renderBody()` so the gate re-evaluates on every grid↔form flip.
- THREE form-view pills in pills_idmp.json (Lucide-only, [[feedback_pill_icon_consistency]]):
  - `formproc` "Process ▶" (icon `next`)  → DocAction chooser over the current record via `_fsmCtx` (the headline;
    same legal-set + dispatch as item 4, single record). Opens a compact chooser overlay of the legal actions.
  - `formnew`  "New record" (icon `plus`) → enable edit-mode + open the CRUD ring on the current table (the ring
    IS New/Save/Delete in this app — reuses the exact `✎ CRUD` toolbar-button logic, never forks the verb).
  - `formsave` "Save" (icon `save`)       → if a CRUD overlay form is open (`#cfSave` present) click it (commit);
    else honest toast "no record being edited". = iDempiere Save (persist current edits).
  - Print maps to the EXISTING `reports` pill (window.__report.menu) — not duplicated (NON-INVENT, no overlap).
FALSIFIER: in grid view OR pre-login the three pills are OFF the bar; entering form view surfaces them.
WITNESS W-FORM-PILL-ACTIONS (live DOM, tests/poc_form_pill_actions_live.js): login → open a window → GRID view:
  assert formproc/formnew/formsave NOT mounted (gate false); switch to FORM view: assert the three pills mounted
  (gate true) → click Process ▶ → chooser overlay shows the record's legal actions (matches `_fsmCtx.legal`) →
  click New → CRUD ring opens. 0 pageerrors.

## DEPLOY (one PR, both items): idempiere.html + pills_idmp.json + idmp_pills.js; sw CACHE_VERSION v689→v690
  (CLEAN line), idmp_pills.js?v=12→13, pills_idmp.json?v=31→32. Worktree off origin/main, PR, auto-merge, VERIFY
  it lands on origin/main (squash + late push orphans — re-check HEAD).
