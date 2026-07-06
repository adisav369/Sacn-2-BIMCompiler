# ⚠ DO NOT REMOVE — Scope & Protocol
**Scope:** Fix the Glassbowl CRUD edit form on the ERP surface — (1) date fields show no
calendar widget and fail validation, (2) field edits (CRUD_UPDATE) do not persist.
**Source of truth:** edit `build/erp/crud_overlay.js` FIRST (the ERP engine source), then
apply the SAME hunks to the deployed copy `~/bim-ootb/erp/crud_overlay.js` (do NOT blind-copy
the whole file — build/erp can be behind deployed; apply line-level). See
[[feedback_erp_source_of_truth]].
**Log Mandate:** after any run, read `build/erp/poc_*.log` — exit code is not evidence.
**Witness-first:** write the §-log claim before the fix. Honour this block until both items are ✅.

---

## STATUS HANDOFF (from session 2026-06-11, Sonnet→Opus)

### ✅ DONE this session — W long-press drawer opens UPWARD (PR #257, bim-ootb)
- `panels.js` (viewer), `idmp_pills.js` + `glassbowl_pills.js` (ERP): drawer was
  `flex-direction:row` anchored `top` → opened SIDEWAYS. Now `flex-direction:column`
  anchored `bottom:(innerHeight - r.top + 6)` → opens UP. Chip order flipped: **bomb FIRST
  (top = outermost = safety), Z second (near pill = first reach)**. Works for vertical and
  horizontal pill bars. erp sw v643, viewer sw v641. **User verified vertical works after hard
  reset.** PR: https://github.com/red1oon/bim-ootb/pull/257 (branch `feat/w-drawer-upward`,
  worktree `/tmp/wt-w-drawer`). ⛔ MERGE + CONFIRM the PR landed before reusing the branch.

### ✅ DONE earlier — A-GRAIL Z-scrub doc fold-back (bim-ootb #254, erp sw v641)
- Verified working in headless glassbowl: `recordDocMoment(label,op)` stores `v.docOp`;
  `scrubTo` → `foldDocOps(from,to)` fires `crudFoldBack`/`crudFoldForward` for DOC_ACTION dots.
  §FOLD-FORWARD already seen live in the user's log (`key=m_inout status=→CO`). Status-fold
  works on the **writable sidecar** (kernel_ops.db in IndexedDB) even though glassbowl_data.db
  is immutable — so refold of DOC_ACTION is testable as-is.

---

## ✅ DONE ITEM 1 — date field widget seeds (bim-ootb #261, erp sw v646, 2026-06-11)
Fixed: new PURE `CORE.normDateValue(type,val)` slices the date prefix to strict `yyyy-MM-dd` before
`fieldInput` seeds an `<input type=date>`. All doc date cols are AD `type=date` (verified `crud_ops.json`
— c_order/m_inout/c_invoice/c_payment/c_allocationline), so date-only is correct; the time component is an
incidental SQLite-TIMESTAMP artifact (decision RESOLVED — no `datetime-local` needed). Witness
`scripts/poc_crud_persist.js` (headless 16/16) + `erp/tests/probe_crud_persist_dom.js` (real browser: raw
timestamp blanks a live `<input type=date>`, normalized seeds it to `2002-02-22`). Spec retained below.

## ⛔ (DONE) ORIGINAL ITEM 1 SPEC — date field: no calendar widget, then "required" reject

**Symptom (user log):**
```
crud_overlay.js:417 The specified value "2002-02-22 00:00:00" does not conform to the required format, "yyyy-MM-dd".
crud_overlay.js:484 §CRUD validate key=c_invoice verb=update REJECT errors=[{"col":"dateinvoiced","why":"required"}]
```
Also seen: `"2002-02-22 21:09:00"` (m_inout `datetrx` — a *datetime* value).

**Root cause:** `fieldInput()` (`build/erp/crud_overlay.js:444-449`) emits
`<input type="date" value="2002-02-22 00:00:00">`. HTML5 `<input type=date>` accepts ONLY a
strict `yyyy-MM-dd` value; anything with a time component is rejected → the field renders
**blank** (no date shown, picker un-seeded) → validation then sees an empty required field and
REJECTs. The value comes straight from the bundle row unchanged.

**Fix (spec):** normalize the date value to `yyyy-MM-dd` before injecting into a `type=date`
input. Minimal:
```js
// in fieldInput(f, val), before building the <input>:
if (f.type === 'date') {
  var m = /^(\d{4}-\d{2}-\d{2})/.exec(String(v));   // pull the date prefix off "...-... 00:00:00"
  v = m ? m[1] : v;
}
```
**Decision needed (EXTRACT, don't invent):** some fields carry a real time-of-day
(`datetrx 21:09:00`). Check whether any ERP doc field needs the time preserved. If yes, those
fields should render `type="datetime-local"` with a `yyyy-MM-ddTHH:mm` value (slice 16, replace
space→`T`); the field's AD type (`f.type`) must distinguish date vs datetime. If the seed only
ever uses midnight for these and time is cosmetic, date-only is correct and simpler — verify
against the actual column types in the bundle before choosing.

**Witness:** `scripts/poc_crud_date.js` (or extend an existing crud poc) — render the c_invoice
edit form, assert the `dateinvoiced` input value matches `/^\d{4}-\d{2}-\d{2}$/`, the browser
emits NO "does not conform" warning, and validation passes with the pre-filled value
unchanged. §-log: `§CRUD-DATE col=dateinvoiced raw="..." normalized="..." widget=date`.

---

## ✅ DONE ITEM 2 — CRUD_UPDATE persists via the signed sidecar (bim-ootb #261, erp sw v646, 2026-06-11)
Fixed exactly as spec'd: `applyOp` routes CRUD_CREATE/UPDATE/DELETE through the signed sidecar via new
`commitCrud` (same `commitGroup→verifyChain→_sidePersist` path DOC_ACTION uses) + new PURE `CORE.tipValues`
read-the-tip overlays the latest `{col:new}` on the immutable bundle row in `getRecord` (`_overlayTip`).
Survives reopen + page reload (sidecar rehydrated from IndexedDB), `(table,id)`-scoped, latest-wins;
glassbowl_data.db NEVER mutated. CREATE/DELETE also persist (op-log truth for Z fold-back); the getRecord
VALUE overlay is UPDATE-scoped (CREATE/DELETE row visibility is a list-level concern, noted follow-up).
Witness `scripts/poc_crud_persist.js` U1–U7 (headless) + `probe_crud_persist_dom.js` (live tip="red1" via
signed sidecar). Spec retained below.

## ⛔ (DONE) ORIGINAL ITEM 2 SPEC — field edits (CRUD_UPDATE) don't persist

**Symptom:** user edits a value, Saves, reopens the form → old value is back. From the log the
Save is logged as a DRY-RUN only:
```
crud_overlay.js:648 §CRUD update key=c_invoice field=dateinvoiced,description,docstatus (dry) op=CRUD_UPDATE changes={...}
glassbowl.html:778 §DOC-DOT idx=2 total=3 label=Save Invoice op=CRUD_UPDATE
```

**Root cause:** `applyOp()` (`build/erp/crud_overlay.js:643-672`) CRUD_CREATE/UPDATE/DELETE
branches are still the **E2 dry-run** — they only `console.log(... (dry) ...)` and `docDot()`
the moment. The change is NEVER written anywhere. `getRecord()` (line ~678) then reads the
**immutable bundle DB** row directly with no overlay, so the reopened form shows the stale
original. (DOC_ACTION/process is the ONLY verb that truly persists — `commitProcess` →
`KernelOps.commitGroup` → sidecar `kernel_ops.db`, and on load it replays + reads-the-tip for
`docstatus`. Ordinary field UPDATEs have no equivalent.)

**Architecture already decided (GP3, in-file comments):** "sidecar log + read-the-tip;
glassbowl_data.db stays the IMMUTABLE baseline; the sidecar is the only mutable truth." So the
fix follows the EXISTING DOC_ACTION pattern — do NOT mutate the bundle DB.

**Fix (spec):**
1. **Commit CRUD_UPDATE through the signed sidecar**, same as DOC_ACTION: build the op and
   route it via `withSidecar`/`KernelOps.commitGroup` so the change is sealed into
   `kernel_ops.db` (signed, chained, persisted across reload). Reuse `buildOp('update',…)` and
   the `commitProcess` plumbing rather than forking a new path.
2. **read-the-tip for field VALUES, not just docstatus.** Add a helper, e.g.
   `_tipValues(key, id)`, that replays the sidecar ops filtered to `op_type==='CRUD_UPDATE'` for
   that `(key,id)` in commit order and returns the merged `{col: latest .new}` map. `getRecord`
   then overlays that map on top of the immutable bundle row before `cb(o)`. This makes the
   reopened form, the Z fold-back, and any reader all agree on the tip value.
3. CRUD_CREATE / CRUD_DELETE (tombstone) should follow the same read-the-tip overlay so created
   rows appear and tombstoned rows hide — but scope this item to UPDATE first if time-boxed;
   note CREATE/DELETE as a follow-up if deferred.

**Witness:** `scripts/poc_crud_persist.js` — open c_invoice edit, change `description`,
Save (real sidecar commit), CLOSE, reopen → assert the form shows the NEW value via
`_tipValues` overlay; reload the page (rehydrate sidecar from IndexedDB) → value still new;
assert glassbowl_data.db row is UNCHANGED (baseline immutable). §-log:
`§CRUD-PERSIST key=c_invoice id=.. col=description tip="red1" bundle="(2)" source=sidecar`.

---

## Run / deploy
- Localhost: server runs from the worktree at `:8124` (or re-serve `~/bim-ootb`). Glassbowl URL
  `http://127.0.0.1:8124/erp/glassbowl.html`. Hard-refresh (Ctrl+Shift+R) to dodge the SW cache.
- After source fix in `build/erp/`, sync the hunks into `~/bim-ootb/erp/crud_overlay.js` (worktree
  per [[feedback_worktree_hook]] — `~/bim-ootb` is read-only), bump erp `sw.js` CACHE_VERSION,
  PR to main, verify auto-merge landed. Deploy = git push (ERP), per [[feedback_run_witness]].
- The two CRUD bugs sit at IDENTICAL line numbers in source and deployed copy today — the hunks
  apply cleanly to both.

---

## ✅ DONE ITEM 3 — WorldHistory building-card transport (bim-ootb #263, viewer sw v642, 2026-06-11)
Root cause was DETERMINISTIC (the card's named suspect (a), NOT timing): `universal_history.js _openDbUrl()`
read the building re-open key from the `?db=` query param, which is ABSENT on any open path without `?db=`
in the URL → `ref.db` null → `whole_history._deepUrl` fell back to a BARE url → fragile consumeRestore.
Fix: `_openDbUrl()` now prefers the authoritative `A.DB_URL` (the db the viewer actually loaded), so
`ref.db` is ALWAYS stamped → the card deep-links `?db=<url>&sess=…&ghost=1` (page reopens the building from
its OWN url, no race). `import://` dbs stay null (tab-local, not cross-page openable → bare-url fallback).
New `§WHOLE-LANDED` trace pairs with `§WHOLE-NAV`. NO pill/drawer touched (perpendicular rule intact).
Witness `viewer/tests/poc_world_card_dbref.js` (W-WORLD-DBREF 8/8) + poc_whole_deeplink regression green.
(e2e GP.2 import-monolith golden path flaked once on page.goto timeout — unrelated; passed on rerun.)
Spec retained below.

## ⛔ (DONE) ORIGINAL ITEM 3 SPEC — WorldHistory card click does NOT guarantee transport to the building/record

**Symptom (user, 2026-06-11):** clicking a building card in the W (WorldHistory) overlay
"still does not guarantee transport to it" — sometimes it lands on the building/record,
sometimes it just opens the bare page. This is a **RECURRENCE** of the issue the v636/v637 notes
claimed fixed ("sometimes gets to the item, sometimes not" → bake ref into the URL). The fix was
incomplete.

**Where (source = `bim-ootb/common/whole_history.js`; mirror in `build/erp/`?? verify):**
- `_deepUrl(page, ref)` (~L104): builds the deep-link ONLY when the ref carries the right field —
  viewer needs `ref.db` → `?db=<db>&sess=<s>&ghost=1`; idempiere needs `ref.window` →
  `?window=W[&record=R]`. **If the recorded `ref` lacks `db`/`window`, it falls back to the BARE
  page URL → no transport.** Prime suspect: some entries are recorded with a null/partial `ref`.
- `onEntryClick(item)` (~L172): FOREIGN page → stash `RESTORE_KEY` + `location.href=item.url`;
  SAME page → `_localRestore(item.ref)` (host-registered). Two different code paths — confirm
  BOTH actually transport.
- Target-side: viewer `universal_history.js` consumeRestore + the `?db=&ghost=1` deferred load;
  idempiere.html `_pendingWid/_pendingRid` post-login restore. Even with a correct URL, transport
  is **load-timing dependent** — the building/record must finish loading before restore fires.

**Diagnose first (do NOT invent a fix):** add a §-log at the click and at each restore site that
prints the ref and whether transport completed, then reproduce. Likely one (or more) of:
(a) `ref` not stamped with `db`/`window` at record time → bare-URL fallback;
(b) `_localRestore` (same-page) missing/no-op for the building card;
(c) deferred restore races the load and silently gives up.
Capture which, THEN spec the fix. Witness: `§WHOLE-NAV` already logs `to=/url=/ref=` — extend
with a `§WHOLE-LANDED page= ref= ok=` at the target so a card click is traceable end-to-end.

**Note:** this is a HISTORY-lane issue (whole_history.js), separate from the two CRUD bugs above —
bundled here only as this session's single handoff. It can be taken as its own task.
