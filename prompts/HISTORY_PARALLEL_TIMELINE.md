# ⚠ DO NOT REMOVE — Parallel-universe history: fork-don't-wipe TREE + switch=restore (PR #5)
# Scope: BUILD. Turn the LINEAR undo timeline into a branching TREE so going back + acting forks a sibling
#        universe instead of wiping the forward trail. Bar shows the siblings; clicking a branch tip RESTORES
#        its stamped look (already proven). This is the demo the user wants to film. Whitebox §-log is the
#        witness — drive a real sequence, READ the §-lines, the log IS the proof. Edit shipping code ONLY in a
#        `/tmp/wt-*` worktree off `bim-ootb` (editing ~/bim-ootb is hook-blocked). Honour until ✅ DONE.

## ▶ STATE AT HANDOFF (2026-06-09)
SHIPPED/OPEN (bim-ootb PRs):
- **#205 MERGED** — sink `S()` + knob-net + view-stamp + restore mechanism (`common/history_tap.js`).
- **#207 MERGING** — restore-the-LOOK in the real bar (re-apply x-ray/bbox/camera on entry click; `universal_history.js`).
- **#213 OPEN, STACKED on #207** — `field(name,read,write)` primitive + section-cut FIX + **log-sniffer** (zero-wire
  total recording) + `combineViews`. Branch `feat/history-field-sniffer`. **once #207 squash-merges to main, rebase
  #213 onto fresh `origin/main`** (squash-stack housekeeping — its history collides otherwise).
- **#215 OPEN, STACKED on #213 — THIS PROMPT's PR #5 ✅ DONE (witnessed).** Branch `feat/history-branch-tree`.
  Branch TREE in `common/history_bar.js`: fork-don't-wipe (`§HIST_FORK`) + sibling ⑂ ticks in the bar + switch=restore
  (`switchToId`/`_switchToNode`) + per-building persist (`serialize`/`hydrate`/`setTreeKey`, key from `?db=`). Witness
  `/tmp/wt-field/drive_fork.js`→`fork.log`: `§PROOF tree=*A-base(A-tip | *B-tip) nodes=3 tips=2` (both universes
  survive, no wipe); switch→A palette=22/section cut=9, switch→B palette=0/section off (both restore); serialize→
  clear→hydrate identical (`§HIST_HYDRATE`); DOM sibling tick renders+clicks. **NEXT once stack lands: rebase #215
  onto fresh main; then PR #4 (KNOB) + PR #6 (combine) below.**

WORKTREE + LIVE TEST (still on disk):
- Worktree `/tmp/wt-field` (branch `feat/history-field-sniffer`) has all the #213 code.
- Localhost: `python3 -m http.server 8126` serving `/tmp/wt-field`; Hospital = `viewer/viewer.html?db=buildings/
  Hospital_extracted.db` (local DBs symlinked from `bim-ootb/viewer/buildings/`, split meta+geo).
- Whitebox driver template: `/tmp/wt-field/drive_whitebox.js`. Puppeteer chrome at
  `/home/red1/.cache/puppeteer/chrome/linux-147.0.7727.57/chrome-linux64/chrome` (set PUPPETEER_EXECUTABLE_PATH,
  `--use-gl=swiftshader --enable-unsafe-swiftshader --no-sandbox`).

## ▶ PRIMITIVES ALREADY BUILT (reuse — do NOT re-invent)
- `HistoryTap.currentView()` — stamp a moment's full look (ghost/xray/cam/section/palette via `field()`s).
- `HistoryTap.applyView(view, label)` — restore a look (RESTORE round-trip proven, W-SECTION-RT / W-BAR-RESTORE).
- `HistoryTap.combineViews(a,b,…)` — vector UNION (orthogonal fields never collide → the color⊕section combine, PR #6).
- `HistoryTap.field(name, read, write)` — one symmetric line per restorable act.
- `HistoryTap.sniff(true)` — total zero-wire recording from the §-stream (deny + lifecycle filtered).

## ▶ THE TASK — branch TREE in `common/history_bar.js` (VIEWER-scoped; ERP uses its OWN idmp_history.js)
Today the bar is LINEAR: `_stream[]` + `_cursor`; `push()` after an undo TRUNCATES the forward tail (the wipe).
Turn that into a tree, minimal + reversible:
1. **Fork-don't-wipe.** In `push()`, when `_cursor < _stream.length-1` (you went back, then acted), DON'T truncate —
   record the abandoned tail as a SIBLING branch hanging off the fork node. Model: each node keeps `children[]`;
   the "current line" is the path from root → active tip. Rides the same idea as the signed hash-chain (chain → DAG).
   Keep it cheap: a branch forks a PARENT POINTER, it does not copy entries (~159 B/node, see §LOCKED #4).
2. **Bar shows siblings.** At a fork, render the sibling branches (a small tick/label off the main line; double-tap
   bloom can label them "universe B"). Mobile = linear default, expand on demand. Reuse existing dot/chip/bloom render.
3. **Switch = restore.** Clicking a branch tip walks to it and `applyView(tip.view)` → the scene returns as that
   universe looked (works today — restore is proven). Switching universes is just restore down a different path.
4. **Persist** the tree shape into the existing per-building persisted log (HISTORY_PERSIST_RECALL spine) so universes
   survive reload — additive to the current serialization.

## ▶ INVARIANTS
- **NO merge in the bar.** Branch + switch + (later) cherry-pick/combine ONLY. True conflict-merge stays the SUBSTRATE's
  job (signed op-log + sync-FSM). See HISTORY_KNOB_SIGNAL_TAP.md §LOCKED-BRANCH (the Pareto case is settled).
- **VIEW restores, MODEL replays.** View-state (the stamp) re-applies; signed kernel ops (GRID_MOVE) still go through
  `KernelOps` undo/redo. Don't snapshot geometry.
- **Shared-bar caution.** `common/history_bar.js` is shared in principle, but ERP runs its OWN `idmp_history.js` — so
  the tree change is effectively viewer-scoped. Still: keep `push/undo/redo/jumpTo` signatures intact (scene.js,
  navigate_find.js, panels.js depend on them).

## ▶ WITNESS (whitebox §-log — the user's REQUIRED proof style; mirror drive_whitebox.js)
On live Hospital, drive a REAL fork: open → pick A → (go back) → pick B (forks universe) → confirm the §-log shows
BOTH tips survive (no wipe), then switch to universe-A tip → `§EVT RESTORE keys=…` re-fires + the scene matches A.
Dump the tree shape (`§PROOF tree=…`). Save to a log, READ it, present the §-lines. PASS = both universes exist +
switching restores each. NO boolean-only asserts — the §-lines are the evidence.

## ▶ AFTER THIS (queued)
- **PR #4 — the KNOB UI ✅ DONE (witnessed, commit 54afaeb on `feat/history-branch-tree`/PR #215).** 5-stop dial
  Off·Low·Mid·High·Max (default High): TURN=breadth (drag/tap, per-stop §-net ladder low⊂mid⊂high⊂max), PRESS=richness
  (long-press → dot→chip, unified w/ bloom), SOUND=pitched detent (∝ breadth, mute-aware). Legacy all/doc/off→
  high/mid/off. Witness `drive_knob.js`→`knob.log`. **REMAINING for a follow-up:** thumbnail (3rd richness level,
  desktop-only) is deferred/stubbed; max≈high+picks (genuine "firehose" = the sniffer, separate); EXPAND the breadth
  vocab to SECTION/PALETTE/SUNGLASS/STOREY_SELECT/CLASH_* as those start emitting through the gate.
- **PR #6 — combine across branches ✅ DONE (witnessed, commit c434e9b on PR #215).** Cross-branch "bring into
  current ⤵": VIEW combine = union the donor's delta-vs-fork view-fields into the current look (`combineViews`,
  delegated to `_cfg.combine`); MODEL cherry-pick = replay the donor's signed op (`_cfg.cherryPick` → KernelOps).
  Gesture on a sibling ⑂ tick: tap=switch · long-press/right-click=bring-into-current. Witness `drive_combine_branch.js`
  →`combine_branch.log`: `after-combine palette=18 section.on=true` (BOTH land), tree=`*base(A-color | *B-section(*⊕
  A-color))`, A & B intact. **REMAINING:** model cherry-pick wired but NOT live-witnessed (needs real grid ops);
  conflict-resolution UI deliberately deferred (substrate's job, NO 3-way merge in the bar).
- **iDempiere port** — `prompts/HISTORY_TAP_TO_IDEMPIERE.md` (held until the ERP UI surface unfreezes).

## ▶ MASTER SPEC
`prompts/HISTORY_KNOB_SIGNAL_TAP.md` — all §LOCKED decisions (FIELD/SNIFFER/KNOB/BRANCH) + build order. Read it first.
