# ⚠ DO NOT REMOVE — NEXT SESSION: PRIVATE DRAFT RESTORE browser leg (FRONTEND_LANE_MASTER §OUTSTANDING item 1)
# Scope: wire the unsaved-draft restore UX on idempiere.html's ✎ CRUD form over the SHIPPED engine.
# Spec-first · §-log first (READ the log after every run) · consume the seam (never fork a verb) · GO before deploy.
# Source of truth: prompts/FRONTEND_LANE_MASTER.md §OUTSTANDING item 1. Engine = build/erp (canonical); deploy onto
#   a CLEAN /tmp/wt-* worktree off origin/main (don't ship drift). sw.js: keep the version line CLEAN — just bump
#   the number, detail goes in the git commit message ([[feedback_sw_version]], NO inline changelog as of #319).

ALREADY DONE — DO NOT REBUILD (verify in witness, then move on):
- Item 1 ENGINE = ✅ LIVE on origin/main (shipped via bim-ootb #317, crud_overlay.js v9+). CORE exposes the
  storage-injectable private draft buffer: draftPut/draftGet/draftClear/draftList + draftDirty/draftChangedCols/
  draftDrift (prefix `erpdraft:`, per (table,id), localStorage in-browser / Map-mock headless). Headless witness
  W-DRAFT-RESTORE 14/14 (bim-compiler scripts/poc_draft_restore.js). The functions are PRESENT but NOT yet wired
  into the form lifecycle — that wiring is THIS session.
- Items 0/2/3a/3b = ✅ DONE + LIVE (PRs #314/#317/#320). Item 3a "ⓘ" record-info popup is the popup-pattern to
  COPY (idempiere.html `_showRecordInfo`, overlay `#idmp-recinfo-ov`). Live witness template: erp/tests/poc_recinfo_live.js.

THIS SESSION — item 1 BROWSER/VISUAL legs (the engine is ready; this is the wiring + skin):
DECISION TAKEN (user-confirmed 2026-06-15): the unsaved mark is an INLINE RESTORE CHIP at the TOP of the ✎ CRUD
  form — NOT a dot/pip on the Z/blue-future history rail (keep "unsaved/private" visually distinct from committed
  dots, and off the rail the blue-future lane owns). [[feedback_destructive_ops]] for the Discard confirm.
1. BUFFER ON LEAVE. In crud_overlay.js, `closeForm()` (and a `beforeunload` listener) → if the form is dirty
   (`CORE.draftChangedCols(gatherVals(e), baseline)` non-empty), call `CORE.draftPut(localStorage, e.key, id,
   vals, {baseline, tipSnapshot: baseline, ts: <kernel-ts, NOT Date.now>, actor: sessionActor()})`. A CLEAN
   leave must `draftPut` (which self-clears the stale buffer). Save/Delete commit paths → `CORE.draftClear`.
   NB: openForm's `orig`/`vals` ARE the baseline (edit = orig tip; create = defaultsFor) — thread them through.
2. RESTORE ON REOPEN. In `openForm`/`renderForm`, after building the form, `CORE.draftGet(localStorage, e.key, id)`;
   if a draft exists, render a small chip at the top of `.cfbody`: "Unsaved changes from earlier · [Restore] [Discard]".
   DEFAULT stays the saved tip (the chip is OPT-IN — never auto-applies). Restore → repopulate the inputs from
   `draft.vals` (re-run applyAdLogic). Discard → CONFIRM ("discard N unsaved changes? can't be undone") → draftClear.
3. RECORD-CHANGED-UNDERNEATH. On Restore, if `CORE.draftDrift(draft, <current tip via CORE.tipValues/readTip>)`
   reports `drifted`, surface a one-line warn ("the record changed since you left — your draft is over an older
   version") next to the chip — do NOT clobber silently (the item-1 decision owed; single-user default = keep draft).
4. DIRTY INDICATOR (optional, low-risk): a faint dot on the ✎ CRUD pill/ring when a draft exists for the focused
   record (read `CORE.draftList`). Keep it tiny + off the history rail.

WITNESS: W-DRAFT-RESTORE-LIVE (headless-chrome DOM probe, pattern = erp/tests/poc_recinfo_live.js): open a CRUD
  form → type a change → close WITHOUT save → assert NO official op committed (sidecar op count unchanged) + a
  draft buffered in localStorage; reopen → assert the form shows the SAVED values by default + the restore chip is
  present; click Restore → inputs carry the typed values; click Discard (confirm) → buffer cleared; 0 pageerrors.
  Then deploy: crud_overlay.js ?v bump + sw CACHE_VERSION bump (clean line), worktree off origin/main, PR,
  gh pr merge --auto --squash, VERIFY landed on origin/main (squash + late push orphans — re-check HEAD).

THEN keep going down §OUTSTANDING to zero:
- Item 4 (GRID MULTI-SELECT + GEAR BATCH) — BROWSER-LEG ONLY, no engine gap (fans the existing
  CORE.buildDocActionGroup/AdDocFsm.dispatch over N rows); pure idempiere.html buildGrid DOM (checkbox col +
  batch toolbar). Witness W-GRID-BATCH live.
- Item 5 (PROCESS+New/Save/Print IN PILL for form view) — pure pill/toolbar relocation (idempiere.html +
  pills_idmp.json, [[feedback_pill_icon_consistency]] Lucide-only). Witness W-FORM-PILL-ACTIONS live.
Each: spec → witness → ✅ DONE (witness) / ⛔ BLOCKED.

REFERENCE — key files/anchors:
- Engine (canonical, already live): build/erp/crud_overlay.js — draftPut/draftGet/draftClear/draftList/
  draftDirty/draftChangedCols/draftDrift (near fieldLineage); __crud is the page seam.
- Shipping UI: bim-ootb/erp/crud_overlay.js (renderForm/closeForm/saveForm/openDeleteConfirm, gatherVals,
  sessionActor) + idempiere.html (the ✎ CRUD toolbar button, _showRecordInfo popup pattern to mirror).
- Headless witness: bim-compiler scripts/poc_draft_restore.js (run via bash build/erp/run_witness.sh).
- Live probe template: bim-ootb/erp/tests/poc_recinfo_live.js (served idempiere.html + playwright, menu-drive).
- Deploy: edit engine in build/erp → sync to a /tmp/wt-* worktree off origin/main (build/erp is a verified
  superset; diff before copy) → crud_overlay ?v= + sw CACHE_VERSION bump (CLEAN line) → PR → auto-merge → VERIFY.
