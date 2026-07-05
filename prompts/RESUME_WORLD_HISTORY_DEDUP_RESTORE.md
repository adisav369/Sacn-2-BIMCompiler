# ⚠ DO NOT REMOVE — scope block
**Scope:** Fix two defects in the **World History** feature (the `W` pill cross-page timeline) WITHOUT
breaking the per-page bar, the glassbowl/gravity view-logs, or any existing witness.
**Honour the Log Mandate:** after every node-witness run, read the saved log before drawing a conclusion —
exit code is not evidence.
**Status:** ✅ DONE + CLOSED 2026-06-27 (Defects A/B below) — bim-ootb PR #554 (lane/world-history-dedup-restore,
sw v755, auto-merge armed). W-WHOLE-DEDUP 5/5 PASS · W-IDMP-HIST-RESTORE 7/7 PASS · all 4 existing whole
witnesses PASS. Modeller wiring (4 steps + design notes) handed off to
`prompts/RESUME_MODELLER_UX_OUTLINER_PILL.md §WORLD HISTORY wiring` — **REOPENED 2026-07-06, see bottom of
this file: that handoff was never picked up, confirmed still zero-wired.**
**This is a restore/repair task — NON-INVENT. Do not add new behaviours; restore what regressed + add the two
guards the user dictated.** Find the regression point first (`git log -p` / bisect the two files below); do not
fabricate a cause.

---

## THE FEATURE (so the next session does not re-discover it)
- **Engine:** `~/bim-ootb/common/whole_history.js` — ONE cross-page log in `localStorage['bim.docHistory']`
  (BroadcastChannel `bim_history`). `record()` is the single writer every surface mirrors through;
  `entries()` is the time-ordered reader; the panel body renders a **day axis + that day's "stretch"** of chips.
  Spec: `HISTORY_WHOLE_TIMELINE.md`. Mounted on idempiere/glassbowl/gravity/viewer (launcher:false → opened
  from the `W` pill, `worldhist`, not the bottom-left clock).
- **Per-page bar ("the smaller page session history" / the `‹ dots ›` line):** `~/bim-ootb/erp/idmp_history.js`
  — an IN-MEMORY `_hist[]` / `_idx`. `push()` mirrors each moment OUT to `WholeHistory.record(...)`.
- **Witnesses that MUST stay green (run them BEFORE and AFTER):**
  - `~/bim-ootb/tests/poc_whole_time.js`, `poc_whole_bar.js`, `poc_whole_log.js`, `poc_whole_time_dom.js`,
    `poc_whole_bar_dom.js` (all PASS as of 2026-06-25).
  - `~/bim-ootb/erp/tests/poc_idmp_history*.js`, `poc_doc_dots.js`, `poc_install_histdot.js`.
  - `~/bim-ootb/common/tests/poc_whole_deeplink.js`, `~/bim-ootb/viewer/tests/poc_world_card_dbref.js`.

---

## DEFECT A — World History SPAMS the same document within one day
**User words:** *"do not make the World history repeat back the same document during the same day session until
it can spam."*

**Root (confirmed):** `whole_history.js` → `record()` (lines ~51–67) appends EVERY entry with **zero dedup**.
The per-page bar (`idmp_history.js` `push()`, line ~44) coalesces a *tip* repeat (`_hist[_idx].sig === sig`),
but (1) only at the tip (A→B→A re-adds A), and (2) that coalescing does not exist in `WholeHistory.record`,
which the `op`/install paths (`idempiere.html:735,773,1026`) and glassbowl/gravity view-logs ALSO write to.
Net: revisiting the same doc/op stacks a fresh chip every time → the day's stretch fills with duplicates.

**Fix (in `whole_history.js record()`):** before appending, coalesce a repeat of the **same document on the
same day**. Document identity, in priority order, NON-INVENTED from the existing `ref` shape:
- idempiere: `ref.table + '#' + ref.recordId` when present;
- else `page + '|' + label`.
Same-day = `_dayKey(ts)` of the candidate vs the last stored row for that same identity+page. If equal →
**update the existing row's `ts` (move it to "now") instead of appending** (so the dot-line keeps ONE live dot
for that doc that advances in time, rather than spamming). Keep it best-effort + never-throw (current contract).
- Keep `MAX`/slice behaviour. Keep `source`/`type` back-compat stamps.
- Distinct documents, distinct days, and `kind:'op'` install/delete moments that are genuinely different
  (different label) must STILL each get their own dot — do not over-coalesce.

**Acceptance / new witness** `poc_whole_dedup.js`:
- Record the SAME doc (`{page:'idempiere',ref:{table:'c_order',recordId:1023}}`) 5× with rising same-day `ts`
  → `entries()` yields **1** row for it, `ts` = the latest. `§`-log the before/after count.
- Cross-day repeat of the same doc → **2** rows (one per day).
- Two different docs same day → **2** rows. Existing 5 whole witnesses stay green.

---

## DEFECT B — the per-page dot-line starts ANEW each session instead of restoring
**User words:** *"The smaller page session history should restore the dots line instead of start anew."*

**Root (confirmed):** `idmp_history.js` initialises `_hist = []` / `_idx = -1` and only ever grows via
`push()`/`pushCrumb()`. On reload/new session the `‹ dots ›` line is EMPTY until the user acts — the prior
session's dots (already persisted in `bim.docHistory`) are not rehydrated.

**Fix (in `idmp_history.js`, on init/`_ensureBar` or a new `_seed()`):** seed `_hist` + `_idx` from
`WholeHistory.entries()` filtered to `page === 'idempiere'`, mapping each persisted row back to the local
moment shape (`{kind, label, windowId/tabIdx/table/recordId/view}` ← from `ref`). Cursor `_idx` = tip (newest).
- READ-ONLY: seeding must NOT re-`record()` (no echo back into the log → would double-write). Best-effort;
  if `WholeHistory` absent, behave as today (empty).
- Respect Defect-A dedup: after A lands, the seed is already deduped at source.
- Do not disturb `_sig` coalescing, draft-pip (`W-DRAFT-RESTORE-LIVE`), or crumb dots.

**Acceptance / new witness** `poc_idmp_history_restore.js`:
- Pre-load `bim.docHistory` with 3 idempiere rows + 1 glassbowl row → mount `idmp_history` → the bar shows the
  **3 idempiere** dots (glassbowl excluded), newest selected, click-restore still read-only. `§`-log depth=3.
- Empty log → bar empty, no throw (today's behaviour preserved).

---

## GUARDRAILS (user: "ensure don't break others in turn or invent new stuff")
- Touch ONLY `common/whole_history.js` and `erp/idmp_history.js`. Bump no `CACHE_VERSION` until both witness
  suites above are green.
- Keep `record()` ADDITIVE + best-effort + never-throw; keep DETERMINISTIC `ts` (witness injects `ts`, the
  record path must not call `Date.now()` when `entry.ts` is supplied).
- The glassbowl/gravity view-log chips and the landing back-compat reader (`e.source/e.type/e.label`) must keep
  working — re-run `poc_whole_bar.js` + `poc_whole_log.js`.
- Edit shipping code in `~/bim-ootb` (canonical), in a `/tmp/wt-*` worktree (shared-tree hook blocks the
  checkout). Whitebox `§`-log proof first, Playwright only for wiring.

## §-LOG TAGS to add
`§WHOLE-DEDUP doc=<id> day=<k> action=coalesce|append count=<n>` ·
`§IDMP-HIST seed page=idempiere restored=<n> idx=<i>`

---

## ▶ 2026-07-06 — REOPENED: user reports "World/History still broken" — 2 parts, one confirmed, one to verify

**Part 1 — Modeller wiring: CONFIRMED still zero-built.** `grep -rn "WholeHistory\|worldhist"
modeller/*.js modeller/*.html` in `~/bim-ootb` returns ZERO hits (checked 2026-07-06) — the 4-step handoff to
`RESUME_MODELLER_UX_OUTLINER_PILL.md §WORLD HISTORY wiring` (2026-06-27) was never picked up. Do those 4 steps
(script include, `WholeHistory.mount({page:'modeller',...})`, `record()` on `openResident`/`openStrDb`, wire a
`W` pill) respecting THIS file's dedup contract (same-building reopen same-day advances `ts`, doesn't stack).

**Part 2 — Viewer: code inspection shows Defects A/B fixes + the `W` pill wiring (`panels.js:1149-1150`) and the
bomb-clear-world-history fix (`panels.js:91-110`) all still present, unregressed — but this was NOT live-driven
this round, only read. Before concluding the viewer side is fine: actually open a real building, generate
several cross-page history moments, open the `W` pill, test tap-vs-long-press (overlay vs Z-timeline+bomb
drawer), and the bomb-clear path for real. If it passes clean, the user's "still broken" must point at
something not covered here — stop and get the exact repro (what was clicked, expected vs actual) rather than
declaring it fixed on a clean pass alone.

**DONE WHEN:** Modeller has a working `W` pill wired per the dedup contract, AND the viewer path is confirmed
live (not just read) with a named outcome either way.

**✅ Part 2 real bug FOUND+FIXED 2026-07-06 — bim-ootb `#670` (`a1c56af`/squash `d6bfb80`, MERGED to main).**
Live-driving the viewer surfaced a genuine bug neither the original PR #554 fix nor this file's code-read
anticipated: `history_bar.js`'s fork-don't-wipe coalesce gate only fires at a true tip. After `undo()` steps
the cursor to a parent that already owns a kid, the very next push — even a passive read-only crumb
(NAVIGATE/XRAY/FOCUS/PICK, auto-logged just from looking around) — forked a brand-new sibling dot, so merely
looking around after an undo grew the timeline (the user's "spawns more dots on undo" report). Fixed: read-only
pushes now drop instead of fork when the cursor already has a kid; genuine edits still fork correctly (git-like
semantics preserved). Witness `witness_undo_dot_spawn.js` (8/8, drives the real production stack
kernel_ops→undo→the real console.log sniffer) confirmed 5/5 failures against pre-fix code. Verified: commit and
witness both exist on `main`.

**Still open, not done:** Part 1 (Modeller wiring) — RE-CONFIRMED still zero-built after all this churn
(`grep -c "WholeHistory\|worldhist" modeller/modeller.html` on current `main` = 0). Part 2's bomb-clear path and
tap-vs-long-press behavior were NOT re-exercised this round (the fixing session's own honest disclosure) —
still needs a real live-drive pass, don't assume clean.

---

## ▶ 2026-07-06 — Part 2 update: user pinpointed the repro directly — FOUND + FIXED + witnessed

User gave the exact symptom instead of a blind live-drive: "the timeline is not intuitive spawning more dots
on undo." Traced it for real (not a code read) — genuine bug, confirmed live:

**Root cause:** `common/history_bar.js` `push()`'s fork-don't-wipe coalesce gate only fires at a TRUE tip
(`_cursorNode.kids.length===0`, line ~169). After `undo()` moves the cursor to a parent that already owns a
kid (the just-undone node), that guard is skipped and the very next push hits `forked = kids.length > 0`
(line ~184) — even for a passive READ-ONLY crumb (NAVIGATE/XRAY/FOCUS/PICK, auto-drained from the
console-log §-tap via `_drainTap`/`feedCrumb`, `viewer/universal_history.js:315-329`) that never mutated the
model. So undo, then merely looking around, forked a brand-new sibling dot — exactly "spawns more dots on
undo."

**Fix:** `push()` now drops (does not fork) an `entry.readonly` push when `_cursorNode` already has a kid —
logged as `§HIST_DROP reason=readonly-post-undo`. A genuine edit (`readonly:false`) still legitimately forks
a new git-like sibling universe — that "fork-don't-wipe" semantic for real divergence is unchanged and still
covered by the witness (a real GRID_MOVE after the same undo still produces `§HIST_FORK` + a new dot).

**Witness:** `viewer/tests/witness_undo_dot_spawn.js` (bim-ootb) — drives the REAL production stack
(`kernel_ops.commitOp` → `universal_history`'s wrapped commit → `history_bar.push/undo`, plus the real
`console.log('§TAG ...')` sniffer path, not a reimplementation). 8/8 PASS. Verified the witness actually
catches the bug: `git stash`-ing the fix made the same 5 targeted assertions FAIL (5/5), confirming the test
isn't vacuous.

**Shipped:** bim-ootb PR **#670** (`lane/worldhist-undo-dots`, commit `a1c56af`), pushed — needs human merge.

**⚠ Process note (worth remembering):** the worktree a research pass had been using (`/tmp/wt-hba-outline-fix`)
was silently removed by another concurrent session mid-work, and the first version of this fix (edited there)
was lost with it. Redid the fix cleanly in a fresh, self-owned worktree (`/tmp/wt-worldhist-undo`, branched
off `origin/main`) after confirming the touched files (`common/history_bar.js`, `viewer/universal_history.js`,
`common/history_tap.js`) were byte-identical between `main` and the stale branch. Lesson: don't trust a
`/tmp/wt-*` worktree you didn't create yourself as durable — verify or re-create before relying on it for
non-trivial edits.

**STILL OPEN in Part 2:** the rest of the original live-drive checklist — bomb-clear-world-history path, and
the tap-vs-long-press dual behavior (`_worldHistDrawer()`) — was not exercised this round; do that next if the
user wants Part 2 fully closed out.

---

## ▶ 2026-07-06 — Modeller ports the SAME `common/history_bar.js` engine (git-faithful undo/redo)

Same underlying engine this whole file is about, ported to a 3rd surface (Modeller, alongside Viewer/ERP).
bim-ootb PR #675 MERGED (`9ff9a5a`+`6d9f906a`), independently re-verified by watchdog (both witnesses re-run
fresh, not trusted from recap): `witness_modeller_redo_order.js` 6/6 (fixed a real LIFO-order bug in
`bonsai_oplog.js redo()`), `witness_modeller_git_history.js` 7/8 (new `modeller/modeller_history.js` adapter,
mirrors `universal_history.js`'s role; core ask — undo, diverge, switch back to the abandoned branch restores
it exactly — proven live on real SampleHouse). The 1 failure (G6, switching directly between two non-trunk
branches) is genuinely left red, confirmed myself, not faked green — a latent shared `history_bar.js`/kernel
`redo()`-by-guess limitation that predates this work and would equally hit the Viewer. Needs a targeted
`redo(id)` kernel primitive, out of scope for this pass. Phase 3 (swap Modeller's `#hist-slider` for the
dot-strip UI) NOT started — engine dormant until wired; watchdog recommends Phase 3 before chasing G6 (G6 is
unreachable without a UI to trigger it), not yet confirmed by the user.
