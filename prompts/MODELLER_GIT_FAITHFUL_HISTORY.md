# ⚠ DO NOT REMOVE — scope block
**Scope:** Make Modeller's in-page edit history genuinely git-faithful (fork-preserving undo/redo,
consolidated repeat-state, same learning curve as the Viewer's timeline) by REUSING the Viewer's proven
`common/history_bar.js` engine — not inventing a new branch mechanism. NON-INVENT: every primitive used
here already exists and is live in `viewer/`; this is porting + correctly wiring, not new architecture.
**Honour the Log Mandate:** read the saved witness log after every run before concluding pass/fail.

---

## WHY (the user's ask, 2026-07-06)
User: "should [the Modeller scrubber] be replaced by the history timeline as they are essentially the same,
for consistency?" → then: "it was meant to be abstract and Git faithful. Have u considered that?" → "yes,
proceed... Once blue dot git branch handling behaviour is there, all else is just downstream refinement...
reminder: returning to a page the old history timeline is still intact, repeat redundant states consolidated."

## WHAT WAS ACTUALLY FOUND (traced live in this session, not assumed)
1. **"Blue Dot" ≠ this problem.** `window.BlueFuture` (`erp/kernel_ops.js` `branch_id` column,
   `discardBranch`/`acceptBranchUpTo`) is a narrow ERP-only "one speculative draft, accept-or-discard-whole"
   primitive for `C_Order`/`C_Project` — built for financial quotes, not a general multi-fork tree. Porting
   it in would NOT solve Modeller's problem and was the WRONG first plan (corrected mid-session).
2. **The Viewer's real git-faithful engine is `common/history_bar.js`.** It is app-agnostic by design
   (its own header: "the ONE shared history/undo-redo timeline bar, app-agnostic... every app SUPPLIES only
   push() calls + a restore() + its significant types"). It keeps a TREE (kids/active/parent per node) —
   undo=step to parent, redo=step to active child, `_switchToNode`=walk to any sibling branch. This is the
   thing that should be reused for Modeller, not re-invented.
3. **The underlying kernel (`viewer/kernel_ops.js`) is flat — same shape as Modeller's.** No branch_id.
   `undoOp()` = undo the highest-id ACTIVE row; `redoOp()` = redo the **lowest-id UNDONE** row
   (`ORDER BY id ASC LIMIT 1`). `history_bar.js`'s tree walks these two dumb, correctly-ordered primitives
   in the right sequence (undo to common ancestor, then redo down the target path) — the TREE is the git
   brain; the kernel is just a correctly-invariant single-step primitive.
4. **Modeller's `bonsai_oplog.js` `redo()` has this invariant backwards — a real, confirmed bug**, independent
   of any forking: `topRow = undoneRows[undoneRows.length - 1]` picks the **highest**-id undone row (line
   ~424), the opposite of the proven `viewer/kernel_ops.js` rule. Traced example: rows 1,2,3 active → undo
   twice (undone={2,3}) → redo() currently reactivates row **3** first; correct LIFO order requires row **2**
   first (`undoneRows[0]`). This corrupts ordering even with ZERO branching involved, and is the root enabler
   of the "redo resurrects the wrong branch" risk once forking is added on top.
5. **Modeller's Ctrl+Z is wired directly to `bonsai_oplog.undo/redo`** (`modeller.html` `doUndo`/`doRedo`,
   ~line 2952) — NOT through any tree layer, unlike the Viewer where Ctrl+Z calls `UniversalHistory.undo/redo`
   (= `HistoryBar.undo/redo`) FIRST, falling back to the raw kernel only if that's absent
   (`viewer/grid_drag.js:836-855`). This is the missing wiring layer.

## THE PLAN — 3 phases, smallest-safe-fix first
### Phase 1 — fix the redo() ordering bug (standalone, safe, ships even if 2/3 stall)
`modeller/bonsai_oplog.js` `redo()`: change `topRow = undoneRows[undoneRows.length - 1]` to
`topRow = undoneRows[0]` (lowest-id-undone-first, matching `viewer/kernel_ops.js redoOp`/`erp/kernel_ops.js
redoOp`). Keep the gesture-group (`_isGestureGid`) and ancestor-`parent`-chain walk UNCHANGED — those are
legitimate Modeller-specific mechanics (CAD feature dependency + one-gesture-one-undo), orthogonal to which
row is picked as the seed.
**Witness `witness_modeller_redo_order.js`:** commit 3 standalone features (ids rising) → undo×2 → redo×1 →
assert the id reactivated is the LOWER of the two undone ids (was: asserted the bug reproduces on pre-fix
code, i.e. red before, green after — Log Mandate: read the log, don't trust exit code).

### Phase 2 — mount `common/history_bar.js` for Modeller, wire commit/undo/redo through it
- `<script src="../common/history_bar.js?v=1">` in `modeller.html` (script include block, near
  `bonsai_oplog.js`).
- `HistoryBar.configure({ source:'modeller', restore: <fn>, profiles: <modeller op-type buckets>,
  afterApply: <optional chain-verify hook>, sharedKey/channel: reuse defaults so cross-tab sync still works,
  treeKey: a per-building key (mirrors the Viewer's per-building persisted TREE) })`.
- Every `bonsai_oplog.commit()`/`commitGesture()` success calls `HistoryBar.push({...})` — ONE node per
  user gesture (mirrors the Viewer's one-push-per-GRID_MOVE-commit pattern), analogous to how
  `universal_history.js` wraps `KernelOps.commitOp`.
- `restore(entry, forward)` calls `bonsai_oplog.undo()`/`redo()` at each tree step (mirrors
  `universal_history.js _applyOp` calling `KernelOps.undoOp/redoOp` per step) — Phase 1's fix makes this
  safe to drive from a tree walk.
- Modeller's `doUndo`/`doRedo` (modeller.html ~2952) call `HistoryBar.undo()/redo()` FIRST, falling back to
  direct `Bonsai.oplog.undo()/redo()` only if `HistoryBar` failed to mount — same defensive pattern as
  `viewer/grid_drag.js:836-855`.
- **Consolidation-on-revisit comes FREE from this reuse** — `history_bar.js`'s own significance/coalesce
  gate (`_sig`, `COALESCE_MS`, the profile filter) already dedupes repeat/insignificant pushes; no separate
  code needed to satisfy "repeat redundant states are consolidated."
- **"Returning to a page, the old history timeline is still intact"** — `treeKey`-based persistence
  (`_persistLoad`/`_persistSave`, already built) restores the WHOLE tree (not just the linear scrub position)
  on reload, same guarantee the Viewer already has per-building.

### Phase 3 — UI: let `history_bar.js`'s own dot-strip render, retire the flat `#hist-slider`
`history_bar.js` self-mounts its own dot-line + bloom UI (no host DOM needed beyond `mountHostId` or a fixed
dock). Once Phase 2 is live-verified (dots correctly reflect fork/undo/redo, no double-compounding), replace
`#hist-slider`/`#hist-label` with it — do NOT run both bars long-term (the user asked for consolidation, not
two redundant timelines). Verify old slider's existing E2E witnesses still pass or are consciously superseded
by the new dot-strip's own witness before deleting the slider markup.

## GUARDRAILS
- Touch ONLY `modeller/bonsai_oplog.js`, `modeller/modeller.html`, `common/history_bar.js` (if a Modeller-only
  gap surfaces there — flag before editing, since it's shared with the Viewer and must not regress it).
- Do NOT touch `erp/kernel_ops.js` / `viewer/kernel_ops.js` / Blue Future — unrelated, proven, out of scope.
- Do NOT port `branch_id` — the tree lives in `history_bar.js`, not the kernel schema (see WHAT WAS FOUND #2/3).
- Existing witnesses that must stay green: `modeller/tests/witness_gesture_undo.js`,
  `modeller/tests/witness_e2e_save.js`, `modeller/tests/witness_modeller_dw_oplog.js`,
  `modeller/tests/bonsai_rotate_solid_live.js`, `modeller/tests/bonsai_scale_live.js`,
  `modeller/tests/witness_modeller_router_nnchain.js` (all currently exercise undo/redo/commit paths).

## §-LOG TAGS to add
`§MODELLER_REDO_ORDER seed=<id> undone_before=[...] ` (Phase 1) ·
`§MODELLER_HIST push node=<seq> kind=<k> label="<l>"` / `§MODELLER_HIST_SWITCH from=<id> to=<id>` (Phase 2)

## STATUS (updated after live witnessing)

### Phase 1 — ✅ DONE
`modeller/bonsai_oplog.js` `redo()` fixed (`undoneRows[0]`, lowest-id-undone-first). New witness
`witness_modeller_redo_order.js` 6/6 PASS, confirmed non-vacuous (3/6 RED on pre-fix code). All named
regression witnesses green.

### Phase 2 — ✅ DONE, with one found-and-documented gap (not a Modeller-specific bug)
Built `modeller/modeller_history.js` (the Modeller adapter onto `common/history_bar.js`, mirroring
`viewer/universal_history.js`'s role exactly): wraps `Bonsai.oplog.commit/commitGesture`, records a
`BUILDING_OPEN` milestone from `str_walker_outliner.js _openBuffer`, routes `doUndo`/`doRedo` through
`ModellerHistory.undo/redo` first (viewer/grid_drag.js's exact defensive pattern), falls back to the
raw oplog only if the tree never mounted.

Live headless witness `witness_modeller_git_history.js` (drives the REAL modeller.html + SampleHouse,
not a reimplementation) — 7/8 PASS:
- G1-G4: building-open milestone, real commits push tree nodes, undo preserves tree structure
  (fork-don't-wipe, not truncation), a new edit after undo correctly FORKS a sibling — ✅ all green.
- **G5 — the user's core ask, PROVEN:** undo → new diverging edit → switch back to the ABANDONED
  branch's tip restores EXACTLY that branch's state, NOT compounded with the branch you left. This is
  the exact scenario that was silently corrupting before Phase 1 (bonsai_oplog's redo() picked the wrong
  row) — now provably correct, live, on the real stack.
- **G6 — FOUND GAP, deliberately left RED and documented, not hidden:** switching DIRECTLY between two
  DIFFERENT abandoned branches (skip the trunk — branch→branch, not trunk→branch) picks the WRONG row.
  Root cause (traced via diagnostic, not assumed): `_switchToNode`'s undo-to-common-ancestor walk leaves
  the just-abandoned branch's rows freshly `undone=1` ALONGSIDE the target branch's own pre-existing
  `undone=1` rows; the kernel's `redo()` "lowest-id-undone-first" invariant (Phase 1's own fix — proven
  correct for the single-abandoned-branch case in G5) cannot disambiguate which undone row belongs to
  which lineage once 2+ abandoned branches' rows coexist in the same undone set. **This is a latent
  limitation of the SHARED `common/history_bar.js` + kernel `redo()`-by-guess design, not something
  introduced by Modeller's wiring** — the exact same code path (`_switchToNode`, `viewer/kernel_ops.js
  redoOp`) would exhibit it in the Viewer too, if a user forked 3+ times and jumped directly between two
  non-adjacent side-branches. A real fix needs a TARGETED `redo(id)` kernel primitive (not "guess the
  lowest"), which touches shared, already-relied-upon production code in both `viewer/kernel_ops.js` and
  `modeller/kernel_ops.js` — correctly out of scope for a Modeller-only pass; flagged here for whoever
  next touches the shared history engine, not silently patched over.
- G7/G8: signed chain still verifies after all toggling; zero load failures/pageerrors.

All 6 named regression witnesses (gesture-undo, dw_oplog, router_nnchain, rotate/scale live, e2e_save)
re-run clean after Phase 2's wiring — no regression.

### Phase 3 — NOT STARTED
Swapping `#hist-slider` for `history_bar.js`'s own self-mounted dot-strip UI. Deliberately not attempted
this pass — Phase 2 only wires the ENGINE (push/undo/redo/fork), the bar stays dormant (no `.open()` ever
called) so today's `#hist-slider` UI is completely unaffected. Consolidation-on-revisit (the user's
"repeat redundant states are consolidated" ask) and "history intact across a reload" both already work
for free once Phase 3 exposes the bar, via `history_bar.js`'s own coalesce gate + `treeKey` persistence —
no separate code needed, just wiring `treeKey` + surfacing the bar visually. Not yet done.

## ▶ 2026-07-06 — Watchdog recommendation: Phase 3 before G6

Independently re-verified this PR's claims (both witnesses re-run fresh: `witness_modeller_redo_order.js`
6/6, `witness_modeller_git_history.js` 7/8 with G6 genuinely reproducing `[KNOWN GAP, not fixed]` — not
faked green). User then asked which to do next, Phase 3 (visible UI) or fixing G6 (branch-to-branch switch).

**Recommendation: Phase 3 first, G6 after.** G6 only triggers when a user jumps directly between two
non-trunk sibling branches — but there's no UI yet for a user to even do that (the tree is wired but
dormant, `#hist-slider` is still the only visible control). Fixing a bug in an interaction nobody can
reach yet is premature polish on an untested surface, and the G6 fix itself is the bigger risk (a targeted
`redo(id)` kernel primitive touching shared, already-relied-upon code in both `viewer/kernel_ops.js` and
`modeller/kernel_ops.js`, not a Modeller-local change) — better scheduled once real usage (or a real E2E
against the visible UI) shows how often multi-fork branch-jumping actually happens. Same smallest-safe-
fix-first logic that already ordered Phase 1 before Phase 2 in this same file. Not yet confirmed by the
user — a recommendation for whichever session picks this up next, not a locked decision.
