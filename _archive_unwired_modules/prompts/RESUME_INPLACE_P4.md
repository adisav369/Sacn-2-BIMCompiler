# ✅ LANE COMPLETE 2026-06-17 — P4 (grid inline edit, PR #357, W-INPLACE-GRID-LIVE 11/11) + P5 (undo reconcile /
#   phantom-draft-pip fix, PR #361, W-INPLACE-UNDO-LIVE 10/10) both DONE/LIVE on origin/main (erp sw v710 after
#   concurrent-merge re-resolution). P1–P5 all shipped. NEXT LANE = ERP CRITIC spine (prompts/GRAND_LANE_STRATEGY.md /
#   ERP_CRITIC_UX_LANE.md P3 surpass axes: S6 lens-swap / S1 offline-5 / S5 / S7). The card below is kept for history.
# ⚠ DO NOT REMOVE — RESUME CARD: iDempiere IN-PLACE CRUD — P4 (grid inline edit) → P5 (undo reconcile)
# Scope: continue the iDempiere-FAITHFUL IN-PLACE CRUD lane. P1+P2+P3 are DONE/LIVE; P4 + P5 remain.
#   Spec-first · §-log first (READ the log, exit code is NOT evidence) · witness-led (each test NAMES its issue) ·
#   deterministic/NON-INVENT · consume the SIGNED seam (reuse the engine, never fork a verb).
# Master card (read first): prompts/CRUD_INPLACE_EDIT_SESSION.md (T1–T7 + phases). Backlog: FRONTEND_LANE_MASTER §OUTSTANDING.

## STATE (2026-06-17) — what's already LIVE (do NOT redo)
- **P2 inline EDIT** — bim-ootb PR #353, erp **sw v704**, `crud_overlay?v=16`, **W-INPLACE-EDIT-LIVE 12/12**.
  The iDempiere form view is editable IN PLACE: directly-editable inputs, NO `#crudForm` modal, NO ✎ Edit button
  (retired — supersedes #351 W-RING-LEAK). Save/Ignore dirty-gated (dataStatusChanged); Ignore = dataIgnore (revert
  to tip). Same signed engine, only the MOUNT moved — module var `fhost` serves BOTH the Glass/Gravity modal ring
  AND the iDempiere inline host. Baseline captured POST-render; validate/buildOp diff against it (untouched mis-
  rendered fk fields never trip Save; `validateField` skips unchanged). T3: a dirty form blocks Process.
- **P3 inline NEW/COPY/SAVE&NEW/DELETE** — bim-ootb PR #354, erp **sw v705**, `crud_overlay?v=17`,
  **W-INPLACE-NEWDEL-LIVE 14/14**. Verb bar = the real ZK set (New·Copy·Save·Save&New·Delete·Ignore·Refresh),
  AD-folded + verb-aware. `createInline`/`copyInline` (blank / cloned-with-DocumentNo-cleared); inline delete-confirm
  strip (signed reversible tombstone, no modal); CREATE form is dirty-from-start (pending insert, validate gates
  mandatory); untouched New auto-discards on nav (needSave parity, `renderActiveTab` clears `_newMode`).

## KEY SEAMS (already built — reuse, don't reinvent)
- `crud_overlay.js`: `renderInline(verb,e,vals,orig,id,host,opts)`, `editInline/createInline/copyInline`,
  `_inlineVerbBar`, `_inlineDirty/_refreshInlineDirty`, `ignoreInline`, `_inlineConfirmDelete`, `fhost`, opts contract
  (onDirty/refresh/onNew/onCopy/afterSaveCreate/afterDelete/afterDiscardNew/onUnsupported). Exposed on `window.__crud`:
  editInline, createInline, copyInline, ignoreInline, inlineDirty. Field row = `.cfrow[data-row][data-ad-table]
  [data-ad-column]` + `input/select.cfi[data-col]`. Verb buttons = `.ic-vb[data-v="save|savenew|ignore|refresh|
  new|copy|delete"]`; inline delete confirm = `.ic-confirm` + `[data-c="del|cancel"]`. Commit log = `§CRUD-PERSIST`,
  refold event = `overlay:committed`.
- `idempiere.html`: `buildForm(tab)` (new-mode create branch + canInline edit branch + read-only fallback
  `_appendReadonlyFields`), `_inlineOptsFor(tn, formEl)`, `_startInlineNew(copyFromId)`, `_newMode`, `_openCreate`
  (→inline, `_openCreate0` = modal fallback), `_openDelete` (→inline confirm), `renderActiveTab` clears `_newMode`,
  `overlay:committed` repaints form OR grid post-commit. Grid render = `renderBody()` grid branch; rows = `.idmp-grid
  tbody tr[data-ad-record]`; cells today are read-only text.

## P4 — GRID INLINE EDIT (T5). Witness W-INPLACE-GRID-LIVE.
Click a grid cell → a WEditor-equivalent inline input (reuse `fieldInput`/`populateRefs`) → commit ONE signed
CRUD_UPDATE row-wise (GridView/GridTabRowRenderer parity). Source of truth = iDempiere ZK `GridView.java` +
`GridTabRowRenderer.java` (the grid renders per-cell editors; btnGridToggle flips grid↔form, BOTH always-editable).
Reuse the SAME signed write the form uses (buildOp('update')→applyOp→commitCrud). Suggested shape: on cell click,
swap the `<td>` text for a `fieldInput` for that column seeded from the row value; on blur/Enter, if changed →
one CRUD_UPDATE for {that col} on that row's pk → `overlay:committed` refolds the grid (`renderBody`). Dirty/Escape =
revert the cell. Respect AD readonly (IsUpdateable=N / readonlylogic) — a read-only cell stays text. Phase-gate: if
too large, ship a click-to-edit-one-cell slice and LOG what's deferred (no silent cap).
WITNESS W-INPLACE-GRID-LIVE: open a CRUD table grid (GardenWorld c_order / c_bpartner window 123/143) → click an
editable cell → type → commit → assert ONE §CRUD-PERSIST op=CRUD_UPDATE verifyChain=ok, the grid cell shows the new
value, SURVIVES reload, NO modal, ring NOT fanned, 0 pageerrors. Regressions GREEN (the full list below).

## ✅ P4 DONE/LIVE 2026-06-17 (PR #357, erp sw v706, crud_overlay?v=18; W-INPLACE-GRID-LIVE 11/11)
Grid data cell → inline WEditor (`editCell`, single-col peer of editInline; reuses fieldInput/populateRefs via a
borrowed `fhost`) → Enter/blur = ONE signed CRUD_UPDATE row-wise; Escape/unchanged reverts; read-only cell →
onUnsupported → host opens the record FORM (`_editGridCell` in buildGrid, stopPropagation); docstatus cell rides
DOC_ACTION via splitStatusChange. FIX landed: `listTip` applied an UPDATE under the op's lowercase key (f.col), but a
SELECT* bundle row carries original-case cols (e.g. "Value") and `recVal` returns the exact-case match first → the
overlay was invisible on the grid; `listTip` now updates the EXISTING-cased key (c-i match), else adds it. All 14
regressions GREEN. NEXT = P5.

## P5 — RECONCILE UNDO (T3). Witness W-INPLACE-UNDO-LIVE.
★ PHANTOM-DRAFT-PIP ROOT CAUSE CONFIRMED (read-only diagnosis, P4 session): `_bufferDraft` (crud_overlay.js ~L1096)
  diffs `gatherVals(e)` (POST-render values) against `_formCtx.baseline` = the RAW record (orig), NOT the post-render
  `_inlineBaseline`. So render-normalized fields (date→yyyy-MM-dd, an fk select that landed on another option, number
  coercion) read as "changed" on an UNTOUCHED open → `draftChangedCols` returns spurious cols → a phantom AMBER pip +
  a §DRAFT-PUT with cols that the user never typed. FIX (P5): for an inline form, `_bufferDraft` must diff against
  `_inlineBaseline` (the same post-render baseline P2's `_inlineDirty`/validate/buildOp already use), so an untouched
  open buffers NOTHING. Witness: open a record, immediately nav away → assert NO §DRAFT-PUT / NO pip; then a real edit
  DOES buffer. (This is the same post-render-baseline insight P2 used to stop untouched mis-rendered fk fields tripping Save.)
Make Ignore (uncommitted, in the verb bar) vs the history op-log timeline (committed scrubber) visually/semantically
DISTINCT and labelled, so they're never confused — Save is the boundary (Process needs a Save marker; the timeline
only ever scrubs COMMITTED ops). Also: verify the phantom-draft-pip-on-open the user noticed (does `_bufferDraft`
record spurious cols on an untouched open?) — fix if real. Carry draft-restore (#322) onto the inline surface.

## WORKFLOW (every session)
- Shared tree `~/bim-ootb` is HOOK-BLOCKED → work in a `/tmp/wt-*` worktree off **fresh `origin/main`** (P3 #354
  squash-merges; start P4 off the post-merge main — never re-use a squash-merged branch).
- ERP edit ONLY in `bim-ootb/erp/*`. Bump `sw.js` CACHE_VERSION (next = **v706**) + `crud_overlay.js?v=` (next **v18**)
  on deploy. Witnesses: `node tests/poc_*.js` from `bim-ootb/erp` (Playwright at `~/bim-ootb/tests/node_modules`).
- REGRESSIONS that must stay GREEN each PR (edit/create/delete legs already re-pointed to the inline surface):
  `poc_inplace_edit_live` (12) · `poc_inplace_newdel_live` (14) · `poc_critic_create_live` (12) ·
  `poc_critic_crud_full_live` (10) · `poc_ad_folded_crud_live` (14) · `poc_form_pill_actions_live` (6) ·
  `poc_critic_ring_leak_live` (10) · `poc_grid_batch_live` (7) · `poc_draft_restore_live` (10) · `poc_recinfo_live` (7) ·
  `poc_critic_process_signed_live` (18) · `poc_critic_selfedit_live` (30) · `poc_ad_displaylogic` · `poc_ad_folded_crud` (14).
- Deploy = git push + PR + `gh pr merge <n> --auto --squash`; verify it lands (BLOCKED = e2e pending, not a redo).
- After P4+P5: this card is DONE → return to the ERP CRITIC spine (prompts/GRAND_LANE_STRATEGY.md / ERP_CRITIC_UX_LANE.md
  P3 surpass axes: S6 lens-swap / S1 offline-5 / S5 / S7).
