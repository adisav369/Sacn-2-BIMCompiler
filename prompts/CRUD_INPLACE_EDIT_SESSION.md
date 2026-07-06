# ⚠ DO NOT REMOVE — SESSION CARD: iDempiere-FAITHFUL IN-PLACE CRUD (no modal, no Edit button)
# Scope: make the ERP edit experience follow iDempiere's REAL interaction model — the form/grid is DIRECTLY
#   editable; the toolbar is New/Copy/Save/Save&New/Delete/Ignore(=undo)/Refresh + GridToggle; there is NO "✎ Edit"
#   button and NO modal popup. Source of truth = the iDempiere ZK code + the AD DB (we HAVE both — plan from them,
#   do not invent "how iDempiere feels"). Spec-first · §-log first (READ the log) · witness-led (each test NAMES
#   its issue) · deterministic/NON-INVENT · consume the seam (reuse crud_overlay's signed write path, never fork it).
# Triaged into FRONTEND_LANE_MASTER §OUTSTANDING as the TOP open item (user steer 2026-06-17): "edit happens in the
#   panel form itself or even grid row-wise — no popup, no Edit button; only Save/Delete; undo = ... iDempiere's undo".

## 0. SOURCE OF TRUTH (read these FIRST — extract the model, do not assume)
iDempiere ZK UI: `/home/red1/idempiere-dev-setup/idempiere/org.adempiere.ui.zk/WEB-INF/src/org/adempiere/webui/adwindow/`
- `ADWindowToolbar.java` — the REAL button set (verified 2026-06-17): btnNew(Alt+N), btnCopy(Alt+C), btnDelete(Alt+D),
  btnSave(Alt+S), btnSaveAndCreate, **btnIgnore(Alt+Z)**, btnRefresh, btnGridToggle (form↔grid), btnFind, btnLock,
  btnAttachment, btnReport/Archive/Print, btnProcess, … **There is NO "Edit" button.** Save/Ignore gate on dirty.
- `AbstractADWindowContent.java` — onNew()/onSave()/onDelete()/**dataIgnore()** + `dataStatusChanged()` (dirty
  tracking) + `needSave()` (prompt/auto-save on navigation; a new record with nothing changed is auto-dataIgnore()'d).
- `GridView.java` + `GridTabRowRenderer.java` — the GRID renders `WEditor` inputs per cell → **inline-editable rows**,
  toggled against the single-record form by btnGridToggle. BOTH surfaces are always-editable, never a read→Edit→modal.
AD DB: `ad_seed.db` (the dictionary the form folds from) — e.g. window 123 (Business Partner) is a 9-TAB window
  (Business Partner 69 fields, Contact, Location, Bank Account, Customer/Vendor Accounting, …). NON-INVENT: every
  field/verb/legality already lives in AD_Field/AD_Column/AD_Tab/AD_Val* — fold it, never hand-author.

## 1. THE GAP (current vs iDempiere)
TODAY (bim-ootb/erp/idempiere.html + crud_overlay.js): grid → click row → READ-ONLY form view (refreshForm) → an
  **✎ Edit button** (toolbar) / form-edit pill → a **MODAL `#crudForm` popup** (crud_overlay.renderForm dumps the
  tab's displayed fields FLAT in one scroll) → Save. New/Delete likewise open the modal. This is inverted: a read
  surface + an Edit gate + a popup. The "✎ Edit" shipped in #351 is itself part of what must GO.
iDempiere: the form (and grid) is the editable surface itself; toolbar = New/Copy/Save/Save&New/Delete/Ignore/Refresh.

## 2. TARGET (the acceptance shape — each line is a witnessable claim)
- (T1) FORM view of a record renders LIVE inputs (the same fieldInput/WEditor-equivalents the modal builds), editable
  in place. No ✎ Edit button anywhere; no `#crudForm` modal opens for edit/new on the iDempiere surface.
- (T2) Toolbar/pills carry **New · Copy · Save · Save&New · Delete · Ignore · Refresh** (+ existing GridToggle if
  present). Save + Ignore are DISABLED until the record is dirty (dataStatusChanged parity). Folded from AD where
  applicable (verbEnabled per crud spec; Process stays the DocAction surface, untouched).
- (T3) **Ignore = the in-place UNDO** (discard unsaved edits on the current record → revert inputs to the tip).
  Reconcile with the history timeline: Ignore = uncommitted revert; the op-log scrubber = committed-op fold. Name
  the boundary in the UI so they're not confused (the user's open question — answer it in the spec, witnessed).
  ★ WHY THE TWO MUST BE DISTINCT (user, 2026-06-17): **ProcessIt()/Complete refers to a SAVE marker** — a document
    must be SAVED (persisted, not dirty) before it can be Processed. So Save is a hard boundary: a dirty record's
    Process/Complete is BLOCKED until Save (iDempiere prompts "save changes?" first). The unsaved buffer (Ignore'able)
    and the committed/processable tip are therefore two different states, not one — the UI must show that clearly
    (Save enables Process; Ignore throws away the unsaved delta; the timeline only ever scrubs COMMITTED ops).
- (T4) Save = the SAME signed write the modal used (CORE.buildOp('update'|'create') → applyOp → commitCrud →
  commitGroup → verifyChain → §CRUD-PERSIST → overlay:committed refold). NO new verb, NO forked write path.
- (T5) GRID inline edit (row-wise) — at least the click-to-edit-cell path, committing the same signed CRUD_UPDATE
  (GridView/GridTabRowRenderer parity). May phase after T1–T4 if scope demands; if deferred, log it (no silent cap).
- (T6) Nav-with-unsaved → needSave() parity: prompt/auto-save or Ignore; a New with nothing changed auto-discards.
- (T7) Draft-restore (#322), callouts (#331), display-logic (W-AD-DISPLAYLOGIC), grid-batch (#323), DocAction bar
  (#329) all still work, re-homed onto the inline surface — NOT regressed. (The phantom draft-pip-on-open noticed in
  the user's log — verify whether _bufferDraft records spurious cols on an untouched open; fix if real.)

## 3. ARCHITECTURE — REUSE the engine, move only the MOUNT (consume the seam)
The crud_overlay write engine is correct and SIGNED — keep it. What changes is WHERE the inputs live:
- Reuse: fieldInput(), populateRefs(), applyAdLogic() (§AD-LOGIC-LIVE), fireCreateCallout(), validate()/effectiveFlags,
  buildOp()/applyOp()/commitCrud(), the draft buffer, _overlayListTip/_overlayDocTip refold.
- Move: instead of renderForm() building a `#crudForm` MODAL, render the SAME field rows INLINE into the form-view
  area of #idmp-content (idempiere.html refreshForm/renderActiveTab form mode), with the toolbar/pills as the action
  bar. The host already has a form view (_viewMode==='form'); make it the editable surface, drop the read→modal hop.
- Retire: the ✎ Edit toolbar button (#351) + the form-edit pill's "open modal" role; hostUpdate/hostCreate become
  "focus the inline form for this id / start a new inline record", not "open the modal".
- Dirty: add a dataStatus-equivalent (compare gathered vals vs baseline on input) → enables Save/Ignore, drives
  needSave() on nav. Ignore = restore inputs from baseline/tip.

## 4. PHASES (incremental, each ships green; one bounded PR each; sw bump per deploy)
P1  ✅ DONE 2026-06-17 — SPEC LOCK: verb set + Ignore semantics confirmed against the ZK source (ADWindowToolbar:
    New/Copy/Save/Save&New/Delete/Ignore(Alt+Z)/Refresh/GridToggle, NO Edit button; onIgnore→dataIgnore; needSave
    drives nav). Verb bar + Ignore-vs-timeline proposal approved by the user ("yes proceed").
P2  ✅ DONE/LIVE 2026-06-17 (bim-ootb PR #353, erp sw v704, crud_overlay?v=16; W-INPLACE-EDIT-LIVE 12/12) —
    INLINE EDIT FORM: the iDempiere form view is editable IN PLACE; renderInline/editInline reuse the SAME signed
    engine (fhost serves both the modal ring + the inline host); Save/Ignore dirty-gated (dataStatusChanged), Ignore
    reverts to tip (dataIgnore); baseline captured post-render → validate/buildOp diff against it (untouched mis-
    rendered fk fields never trip Save; validateField skips unchanged); ✎ Edit + edit-modal RETIRED for the form
    path; T3 dirty blocks Process. Regressions GREEN (edit legs re-pointed to inline): crud-full 10/10, folded-crud
    -live 14/14, process 18/18, ring-leak 10/10 (now guards the inline path), create/form-pill/grid-batch/draft/
    recinfo/selfedit/displaylogic. NEXT = P3.
P3  ✅ DONE/LIVE 2026-06-17 (bim-ootb PR #354, erp sw v705, crud_overlay?v=17; W-INPLACE-NEWDEL-LIVE 14/14) —
    NEW/COPY/SAVE&NEW/DELETE inline: verb bar = full iDempiere set (New·Copy·Save·Save&New·Delete·Ignore·Refresh,
    AD-folded, verb-aware); createInline/copyInline (blank / cloned-with-DocumentNo-cleared); inline delete-confirm
    strip (signed reversible tombstone, no modal); a CREATE form is dirty-from-start (pending insert, validate gates
    mandatory); untouched New auto-discards on nav (needSave parity, renderActiveTab clears _newMode); opts callback
    contract (onNew/onCopy/afterSaveCreate/afterDelete/afterDiscardNew); host _newMode + _startInlineNew + buildForm
    create branch; overlay:committed repaints grid too. Regressions GREEN (create/delete legs re-pointed inline).
    NEXT = P4.
P4  ✅ DONE/LIVE 2026-06-17 (erp sw v706, crud_overlay?v=18; W-INPLACE-GRID-LIVE 11/11) — GRID INLINE EDIT (T5):
    click a grid data cell → inline WEditor (`editCell`, the single-column peer of editInline, reuses fieldInput/
    populateRefs) → Enter/blur commits ONE signed CRUD_UPDATE row-wise (the SAME signed write the form uses);
    Escape/unchanged reverts; a read-only cell (IsUpdateable=N / view / not a field) falls through onUnsupported and
    opens the record FORM (not a dead click); a docstatus cell rides DOC_ACTION via splitStatusChange. Host glue
    `_editGridCell` (buildGrid data cells, stopPropagation, fold entry). FIX: listTip now applies an UPDATE onto the
    EXISTING key case (the op keys lowercase via f.col but a SELECT* bundle row carries original-case cols — recVal
    returned the exact-case match first → the overlay was invisible on the grid). 14 regressions GREEN. NEXT = P5.
    DESIGN (locked 2026-06-17, off ZK GridView.java/GridTabRowRenderer.java — per-cell WEditor; both surfaces always-
    editable): crud_overlay adds `editCell(table,id,col,hostTd,opts)` — a SINGLE-COLUMN peer of editInline. Reuses
    fieldInput()+populateRefs() (the form's editors) into the clicked `<td>`; baseline = the AS-RENDERED value; on
    Enter/blur, if changed → validateField(that col) → values=assignVals(rec) with ONLY that col overridden →
    CORE.buildOp('update') (diffs to exactly {col}) → splitStatusChange (a docstatus cell rides DOC_ACTION, never a
    column write) → applyOp → commitCrud → §CRUD-PERSIST + overlay:committed (host renderBody repaints). Escape/
    unchanged → revert the cell (onCancel). Read-only per AD (IsUpdateable=N / view table / readonlylogic) or not a
    field → onUnsupported, and the host opens the record FORM (the classic row-click) — read-only cells are NOT a
    dead click. opts:{onCommit,onCancel,onUnsupported}. NO new verb, NO forked write — the SAME signed path the form
    uses. Host (idempiere.html): buildGrid data cells (not the DocStatus chip) get a click→`_editGridCell` that
    stopPropagation + folds the entry + calls editCell; module `fhost` is borrowed for the populateRefs span then
    restored. sw v706, crud_overlay?v=18.
P5  ✅ DONE/LIVE 2026-06-17 (erp sw v709, crud_overlay?v=19; W-INPLACE-UNDO-LIVE 10/10) — RECONCILE UNDO (T3):
    Ignore (uncommitted) vs the history op-log timeline (committed) are distinct + labelled — the distinction was
    already BUILT (AMBER hollow `.scrubdraft` pulse pip = unsaved/opt-in-restore, vs the solid committed `.scrubdot`;
    verb-bar "● unsaved" + "Ignore — discard unsaved edits"; Save = the boundary, Process blocked while dirty from
    P2/T3) — the witness asserts it. The real gap closed = the PHANTOM-DRAFT-PIP the user noticed: `_bufferDraft`
    diffed `gatherVals` (POST-render) against `_formCtx.baseline` (the RAW record), so on an inline form render-
    normalization (date→yyyy-MM-dd, fk select, number coercion) read as "changed" → a spurious pip + §DRAFT-PUT on an
    UNTOUCHED open. FIX: an inline form now diffs against `_inlineBaseline` (the same post-render baseline _inlineDirty/
    validate use) → untouched buffers NOTHING; a genuine edit still buffers + raises the amber pip; Ignore discards the
    uncommitted delta (nothing on the timeline); Save commits the one op (the boundary). DONE — card complete.
P5(orig)  RECONCILE UNDO (T3) — Ignore (uncommitted) vs history timeline (committed) made visually/semantically distinct;
    draft-restore + phantom-draft fix. Witness W-INPLACE-UNDO-LIVE.

## 5. WITNESSES (each NAMES the issue it proves; live = headless DOM probe on the served bundle, §-tagged, 0 pageerrors)
- W-INPLACE-EDIT-LIVE — the form is directly editable; Save = signed CRUD_UPDATE; no modal/no Edit button; Ignore reverts.
- W-INPLACE-NEWDEL-LIVE — New/Copy/Save&New/Delete inline, signed; untouched-New auto-discards.
- W-INPLACE-GRID-LIVE — a grid cell edit commits a signed CRUD_UPDATE row-wise.
- W-INPLACE-UNDO-LIVE — Ignore discards unsaved; the committed-op timeline is separate + labelled.
- REGRESSIONS (must stay GREEN each PR): critic-create, critic-crud-full, critic-process, ad-folded-crud-live,
  form-pill, grid-batch, draft-restore, recinfo, ad-displaylogic, W-RING-LEAK (✎ removal supersedes it — update/retire).

## 6. RISKS
- Core-path refactor: every CRUD witness funnels through openForm/#crudForm — port carefully, keep the SIGNED seam.
- Mobile (MOBILE_CARDS) + classic L&F (body.idmp-clean) form layouts must both still render the inline editor.
- Display-logic/callout re-apply on inline input parity with the modal (already wired in renderForm — carry it over).
- Don't break the Glass/Gravity ring (separate surface) — this is the iDempiere surface only.
