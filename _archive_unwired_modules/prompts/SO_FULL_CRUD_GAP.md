# ⚠ DO NOT REMOVE — Scope & Protocol
**Scope:** Make the FULL Sales-Order (C_Order) document process do real CRUD from the UI —
Create / Read / Update / Delete a Sales Order **and run the document workflow** (Complete →
Ship → Invoice → post) — on the two ERP surfaces the user actually opens: **Glassbowl**
(`erp/glassbowl.html`) and the **iDempiere renderer** (`erp/idempiere.html`).
**Source of truth:** edit `build/erp/*.js` FIRST (the ERP engine source — [[feedback_erp_source_of_truth]]),
then apply the SAME hunks to the deployed copy under `~/bim-ootb/erp/` (do NOT blind-copy whole
files — `build/erp` can lag deployed; apply line-level, in a `/tmp/wt-*` worktree per
[[feedback_worktree_hook]]).
**Log Mandate:** after any run, READ `build/erp/poc_*.log` — exit code is not evidence.
**Witness-first:** write the `§`-log claim BEFORE the fix. NON-INVENT: the engine already proves
the fold headless and oracle-equivalent — this task WIRES that proven engine into the UI write
path; it does NOT synthesize new accounting/quantities. Honour this block until every item is
`✅ DONE (witness)` or `⛔ BLOCKED: <one question>`.

---

## WHERE WE ARE (verified 2026-06-13 — read before touching anything)

**The split this task closes:** the *engine* can run the whole O2C document loop and matches real
iDempiere to the cent, headless. The *UI* can edit-and-persist a field and flip a docstatus, but
it does **not** enact the document process, and one whole surface can't CRUD at all.

### ✅ Already LIVE on Glassbowl (do NOT redo — verify, then build on)
- **CRUD_UPDATE persists** via the signed sidecar: `applyOp → commitCrud → KernelOps.commitGroup →
  verifyChain → _sidePersist` (`build/erp/crud_overlay.js:830-852`). `dryCrud`/`dryProcess` are the
  kernel-absent FALLBACKS only, NOT the normal path. Witness `§CRUD-PERSIST … source=sidecar`.
- **read-the-tip overlay** (`CORE.tipValues` / `_overlayTip`, `crud_overlay.js:879-902`): the edit
  form, Z fold-back, and any reader all show the tip value; survives reopen + page reload (sidecar
  rehydrated from IndexedDB); `glassbowl_data.db` stays the IMMUTABLE baseline. Witness `§CRUD-TIP`.
- **DOC_ACTION docstatus flip** is a real signed write (`SET_STATUS`), FSM-gated, read-the-tip
  (`commitProcess` + `readTip`, `crud_overlay.js:201-212,747-770`). W-CRUD-DOCSTATUS (the CO→DR
  silent-flip is FIXED, #268 v651).
- Date-widget seed fixed (`normDateValue`, #261 v646). Edit ring + form + type/required/DisplayLogic
  validation all render and gate. crud_ops.json maps c_order/m_inout/c_invoice/c_payment with
  verbs+docAction+fields.

### ✅ Already PROVEN in the engine (headless, oracle-equivalent — the thing to wire in)
- `completeIt(C_Order)` → fan-out to **M_InOut (ship) + C_Invoice + fact_acct GL**, per-line
  granularity, `maxDiff=0c` (W-FOLD-COMPLETE, `scripts/poc_fold_complete.js`).
- `reverseCorrect/void` → reversal nets fact_acct to zero, FSM CO→RE (W-FOLD-REVERSE).
- Posting resolver maps every O2C token → GL account (W-POST-HARDEN). FSM legal-action sets +
  transitions match iDempiere (W-MORDER-FSM). `ad_docfsm.js` carries the C_Order action sets.

---

## THE GAPS (what blocks "full SO CRUD" today)

### GAP 1 — Complete is a status flip, NOT the document process  ⟵ the keystone
`buildDocActionGroup()` (`build/erp/crud_overlay.js:186-195`) returns **exactly one op**:
`[SET_STATUS]`. The completeIt consequence set — create the Shipment, create the Invoice, post the
GL — is explicitly NOT synthesized (lines 190-193, marked NON-INVENT / delegated). So in the UI,
"Complete" a Sales Order flips `docstatus → CO` and **nothing downstream is created**: no M_InOut,
no C_Invoice, no fact_acct. The engine proves this exact fan-out headless and to-the-cent; it is
simply not handed to `commitGroup` as the consequence ops. **This is the central gap** — without
it there is no "document process," only a status label.

### GAP 2 — CREATE / DELETE don't show at the list level
CRUD_CREATE and CRUD_DELETE persist as op-log truth (Z fold-back works), but the `getRecord`
read-the-tip overlay is **UPDATE-scoped** (`crud_overlay.js:85` of CRUD_EDIT_PERSIST.md is the
explicit follow-up note). Result: a newly **Created** Sales Order row does not appear in the
list/dossier, and a **Deleted** (tombstoned) row does not hide. Only UPDATE round-trips visibly.
So "raise a new order" and "delete an order" don't reflect in the UI even though they're logged.

### GAP 3 — the iDempiere renderer has NO CRUD at all
`crud_overlay.js` mounts on **`erp/glassbowl.html` ONLY** (verified: grep finds it in glassbowl,
not idempiere). `~/bim-ootb/erp/idempiere.html` ships with the edit ring / form / write path
ABSENT — it is read-only. Under iDempiere the user cannot Create, Update, Delete, or Complete a
Sales Order. The user explicitly wants CRUD "under iDempiere OR Glassbowl" — one surface is empty.

### GAP 4 — owner-gate / CAS declared but not enforced on the UI write
`crud_ops.json` marks `c_order` `ownerGated:true`; the op carries `ownerGated`/`cas`
(`crud_overlay.js:144,281`) but the commit funnel only **logs** it — there is no rejection path in
`commitCrud`/`commitProcess`. Confirm whether `kernel_ops.js commitGroup` enforces owner/CAS; if
not, an unauthorized edit is accepted. (Engine guards exist in `erp_replay`; they're not invoked
from the UI path.)

---

## TASKS (do top-to-bottom; each is witness-first; stop only on a genuine EXTRACT-blocker)

> Order chosen so the document process lights up first (highest user value), then the surfaces
> reach parity, then the guards. Each task names the issue it proves.

### T1 — Wire the real completeIt fan-out into the UI DOC_ACTION  (closes GAP 1)
**Issue proved:** completing a Sales Order from the UI creates the Shipment + Invoice + GL postings,
not just a status label.
- In `buildDocActionGroup(op)`, when `op.key==='c_order' && action==='CO'`, push the consequence
  ops the engine already extracts — the M_InOut create, the C_Invoice create, and the SET_STATUS —
  into `groupOps` so `commitGroup` folds them **all-or-none**, sealed once from the tip. REUSE the
  proven verbs (`scripts/poc_fold_complete.js` / `post_resolver.js` / `buildDoc`); do NOT re-derive
  amounts in the overlay. NON-INVENT: pull the consequence op-shapes from the engine, not from new
  numbers.
- GL postings (fact_acct) follow the same group if the loaded seed carries the account linkage;
  if the default `ad_seed.db` lacks it, gate to coverage and `log()` the omission (don't fake it) —
  see the posting-preview data-gate pattern ([[project_posting_preview]]).
- **History picks up the fan-out group.** Completing an order now produces a GROUP (ship + invoice
  + status), not one dot. The History / Z fold-back lane must record this as a SINGLE foldable
  moment so a Z-scrub-back reverses the WHOLE group (un-ship, un-invoice, un-post, status→DR), not
  just the SET_STATUS dot. Today `recordDocMoment(label,op)` stores `v.docOp` and `scrubTo →
  foldDocOps(from,to)` fires `crudFoldBack`/`crudFoldForward` for ONE DOC_ACTION op — extend it so
  the recorded moment carries the whole consequence group (`group/gid`) and fold-back/forward
  replays every op in it (reverse order on back). Reuse the existing fold plumbing; do NOT fork a
  second history lane. Verify the `§FOLD-BACK` / `§FOLD-FORWARD` lines fire for ship + invoice too,
  not just status.
- **Witness** `scripts/poc_so_complete_ui.js`: build the c_order DOC_ACTION op → `buildDocActionGroup`
  returns `[CREATE m_inout, CREATE c_invoice, SET_STATUS co]` (or coverage-gated subset) →
  `commitGroup` commits all-or-none → assert M_InOut + C_Invoice rows exist in the sidecar tip and
  docstatus=CO → **record ONE history moment for the group, then scrub back and assert ship +
  invoice + status all reverse** (sidecar tip returns to pre-complete state). `§`-logs:
  `§SO-COMPLETE order=.. ship=.. invoice=.. gl=<n|gated> sealed=Y` and
  `§FOLD-BACK key=c_order group=<gid> reversed=ship,invoice,status`.

### T2 — CREATE / DELETE list visibility  (closes GAP 2)
**Issue proved:** a newly created Sales Order appears in the list and a deleted one disappears,
sourced from the signed sidecar (baseline DB still immutable).
- Extend the read-the-tip overlay from UPDATE-scoped to the LIST level: the dossier/list query must
  union the immutable bundle rows with CRUD_CREATE tip rows and hide CRUD_DELETE-tombstoned ids
  (replay the op-log filtered to that table, latest-wins, in commit order).
- **Witness** `scripts/poc_crud_list_visibility.js`: CREATE a c_order → it shows in the list tip;
  DELETE an existing one → it's hidden; reload → state survives; bundle DB unchanged. `§`-log:
  `§CRUD-LIST key=c_order created=.. hidden=.. source=sidecar`.

### T3 — Mount the CRUD overlay on the iDempiere renderer  (closes GAP 3)
**Issue proved:** the user can do the same SO CRUD + document process under iDempiere, not only
Glassbowl.
- Mount `crud_overlay.js` (+ its deps `kernel_ops.js`, `crud_ops.json`) on
  `~/bim-ootb/erp/idempiere.html` the same way glassbowl does; confirm the ring/form/write path
  binds to the iDempiere window's current record (`IdmpHost.focus` / `openWindow('Sales Order')`).
  REUSE the glassbowl wiring; do not fork a second overlay. If the iDempiere record model exposes
  the current row differently, adapt `getRecord`'s record-resolution (`curChain`) — don't duplicate.
- **Witness** `erp/tests/probe_so_crud_idmp_dom.js` (live browser): open Sales Order window in
  idempiere.html → edit ring present → change a field → Save → reopen shows tip value → Complete
  runs T1's fan-out. `§`-log mirrors glassbowl's `§CRUD-PERSIST` / `§SO-COMPLETE`.

### T4 — Enforce owner-gate / CAS on the UI write  (closes GAP 4)
**Issue proved:** an edit by a non-owner (or stale CAS) is REJECTED, not silently accepted.
- Confirm whether `kernel_ops.js commitGroup` already rejects on owner/CAS mismatch. If it does,
  surface the rejection in the UI (toast + no dot) instead of a silent pass. If it does NOT, invoke
  the existing `erp_replay` owner-gate / CAS guard from `commitCrud`/`commitProcess` before sealing.
- **Witness** `scripts/poc_crud_ownergate.js`: an ownerGated c_order edit with a wrong owner/stale
  CAS → `commitGroup` returns `committed:false reason=owner|cas` → UI shows reject, sidecar
  unchanged. `§`-log: `§CRUD-GATE key=c_order ownerGated=Y verdict=REJECT reason=..`.

---

## Run / deploy
- Localhost: serve the worktree (or `~/bim-ootb`) at `:8124`. Glassbowl
  `http://127.0.0.1:8124/erp/glassbowl.html`, iDempiere `…/erp/idempiere.html`. Hard-refresh
  (Ctrl+Shift+R) past the SW cache.
- Witnesses via `bash build/erp/run_witness.sh scripts/poc_*.js` ([[feedback_run_witness]]); read
  the `.log` before any conclusion.
- After source fix in `build/erp/`, sync hunks into `~/bim-ootb/erp/` (worktree — read-only shared
  tree), bump erp `sw.js` CACHE_VERSION (KEEP BOTH precache additions / take the HIGHER version on
  conflict), PR to main, verify auto-merge landed. Deploy = git push (ERP).

## Out of scope (note, don't drift)
- New accounting numbers / new BOM / new geometry — NON-INVENT; wire the proven engine only.
- The whole-history card-transport bug (CRUD_EDIT_PERSIST.md item 3) — separate lane.
- Non-order documents (Invoice/Payment standalone create) beyond what the O2C fan-out needs.
