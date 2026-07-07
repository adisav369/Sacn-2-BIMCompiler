# ⚠ DO NOT REMOVE — Session resume: watchdog role, 2026-07-08 (Modeller sketch constraints + Terminal-scale findings)

**Read this doc in full before touching code.** Continues `prompts/RESUME_SESSION_2026-07-07_WATCHDOG.md`
(now superseded — its own §DONE is compacted to one line each below, full detail still lives there if
needed). This session: circle/arc's follow-on constraint work (tangent), 2 real autosave bugs found+fixed,
and the standing TOCTOU-race priority item resolved (turned out already fixed — added the missing
regression guard instead of a redundant fix). All work done via background `Agent` dispatches
(`model:"fable"`) grounded first by a foreground `Explore` pass, built in a shared `/tmp/wt-viewer-rpr-port`
bim-ootb worktree (see §LESSONS #1 on why "shared"), pushed/PR'd/auto-merge-armed by the orchestrating
session, all 6 PRs independently confirmed `MERGED` via `gh pr view` (not just trusted) before writing this.

## §LESSONS — durable, apply every session, don't re-litigate

1. **The `Agent` tool's `isolation:"worktree"` only isolates the CURRENT repo (`bim-compiler`), not a
   foreign repo a task actually works in (`bim-ootb`, separate remote).** Every bim-ootb-side background
   agent tonight had to self-improvise the SAME shared `/tmp/wt-viewer-rpr-port` worktree (no automatic
   per-agent isolation exists for it) — real collision risk when 2+ agents are in flight concurrently (one
   agent's uncommitted edits sitting in the working tree while another's dispatch runs). Every agent handled
   it correctly on its own initiative (scoped temporary-index commits touching only its own files, switching
   the checked-out branch back to leave a sibling's in-progress state untouched) — but this is a manual
   discipline each dispatch has to independently get right, not a structural guarantee. Brief future bim-ootb
   dispatches to expect this and protect against it explicitly, the way tonight's prompts did.
2. **`git push` to this remote reliably hit the harness's own 2-minute Bash default this session (3+
   times), not a real network stall** — a shell-level `timeout 280 git push ...` still got cut off at 2
   minutes, because the CALLING tool's own default timeout was the actual limiter. Fix: pass the Bash tool's
   own `timeout` parameter explicitly higher (e.g. 480000ms), not just a shell-level `timeout` prefix.
3. **Verify-before-build paid for itself concretely, not just in principle.** The standing priority item
   ("check the TOCTOU race on STR-rewalk FIRST") was ground-truthed via `Explore` before any build dispatch
   — it turned out the fix had ALREADY shipped (`ce61f2f`/#665, 2026-07-05), predating tonight entirely. A
   build dispatch would have solved an already-solved problem. Redirected the same effort into the genuinely
   missing piece instead: a small-scale automated regression witness (#704) — the only prior evidence for
   that fix was manual log-reading from one heavy Terminal-scale run.
4. **The disclosed-default pattern held up across 3 successive builds, not just once.** Circle's typed-radius
   UI, arc's sector-closure (center→start line + arc + end→center line, the only closure using ONLY the 3
   authored points), and tangent's grid-line-not-arbitrary-entity scoping all followed the same shape: when a
   genuinely ambiguous implementation detail came up, pick the interpretation using ONLY what's already
   collected/available (no new invented geometry or UI), build it, and disclose the choice explicitly in the
   PR description — don't stall asking, don't hide the assumption either.
5. **Empirical verification of kernel unit/sweep conventions beat assuming, every single time it came up.**
   Circle/arc/tangent all needed a real runtime check (not just a source comment) for radians-vs-degrees and
   CCW-vs-CW sweep direction. Arc's witness proved CCW empirically by comparing a hand-computed bbox against
   what a CW sweep would have produced instead (`[2,2,3]` vs `[4,4,3]`) — reading the binding source alone
   wasn't trusted as sufficient.
6. **A user's own proposed simplification ("just use IndexedDB only") was reasonable-sounding but wrong —
   investigating instead of complying found the REAL bug two doors down.** The existing localStorage-first +
   IndexedDB-fallback autosave design is deliberate and already correct (fast sync path for the common small-
   building case; documented, working fallback for Terminal scale). Removing localStorage would have traded a
   working, spec'd fix for no correctness gain. The actual bug was adjacent: `clear()` never purged the IDB
   fallback entry — a real data-resurrection risk on a cleared-then-reopened building, unrelated to the
   surface complaint but found by reading the code the complaint pointed at.
7. **A concurrent/parallel session's own finding, delivered mid-task as a relayed note, folded cleanly into
   an ALREADY-IN-FLIGHT build via `SendMessage` to the running agent** — no separate PR, no merge collision,
   because both findings lived in the exact same function (`bonsai_oplog.js`'s `_save()`/`clear()`). Prefer
   folding a same-file finding into an in-flight dispatch over letting two small PRs land on the same area
   independently.

## §DONE — compact, full detail in the linked PR/commit, not restated here

| Area | What shipped | PR |
|---|---|---|
| Bonsai kernel breadth + Tier 1/2 constraints (07-07 carryover) | `GEOM_ARRAY`/`GEOM_LOFT`, 6 Tier-1 ops, `p2p_distance`/`l2l_angle_ll`/`p2p_coincident` | see `RESUME_SESSION_2026-07-07_WATCHDOG.md` |
| MEP fitting rotation/cross-section, drift audit, cross-app trust re-verify (07-07 carryover) | real-extraction placement, shared `resolveRealPlacement()` gate | see `RESUME_SESSION_2026-07-07_WATCHDOG.md` |
| `viewer/routewalker.js` fixture-box port | same `resolveRealPlacement()` gate ported from `modeller/`, 16/16 witness | #697 |
| Circle sketch primitive | center+radius placement, real `makeCircleEdge` cylinder extrude (not tessellated), 9/9 | #699 |
| In-app User Guide update | Move & Manipulate, sketch dims/weld, Circle mode sections | #700 |
| Arc sketch primitive | FreeCAD "Arc by center" 3-click, sector-closure disclosed in PR body, CCW proven empirically, 10/10 | #701 |
| **Tangent-to-gridline circle snap** | first real planegcs circle constraint (`push_circle`/`tangent_lc` were dead code before this) — center fixed, radius solved, proximity-triggered same as weld; measured tangency error `1.33e-15`; 8/8 | **#702** |
| **`bonsai_oplog.js` autosave — 2 real bugs, 1 PR** | (a) `clear()` now purges the IDB `§AUTOSAVE_FIX` fallback entry too (was silently resurrectable on reopen); (b) sticky `_useIdbOnly` flag stops re-attempting a doomed `localStorage.setItem` every commit for the rest of a Terminal-scale session (found by a concurrent session, folded in). 10/10 new witness, proven to fail pre-fix on both bugs independently before passing post-fix | **#703** |
| **STR-rewalk TOCTOU race — regression witness** | the standing priority item. Root-caused: **already fixed** (`ce61f2f`/#665, 2026-07-05) — `_commitDiscWalk`'s `commitSeedGroup` batching pattern was already applied to the grid-drag STR-rewalk path. No code fix needed; built the missing small-scale automated guard instead (prior evidence was manual-log-reading from one heavy 35k-element run only). 9/9, double-proven (DB truth + log truth), zero `§KRN_GROUP ROLLBACK` asserted directly | **#704** |
| Housekeeping | 6 fully-merged feature branches deleted from `origin` (`lane/viewer-real-placement-port`, `lane/sketch-circle-primitive`, `lane/sketch-arc-primitive`, `lane/sketch-tangent-gridline`, `fix/oplog-clear-idb-purge`, `test/str-rewalk-race-witness`) | — |

## §OPEN — next session's job, prioritized, with commentary (not decided for you)

1. **Finding 2 — Save/auto-heal wall-clock (57s at Terminal scale) + a genuine new failure mode: a heal
   that succeeds can still cause the overall Save to report `RED_CLASH` and block, because the heal move
   landed in new real contact with an UNRELATED third element.** `SCALE_CHECK_TERMINAL_FINDINGS_2026-07-05.md`
   Finding 2. **My recommendation (advisory, not a call I made unilaterally):** decide this ONE first, even
   before Finding 1 below — it's a correctness/trust question ("what does a blocked Save actually mean at
   real scale"), not a performance number, and design decisions age worse sitting in a backlog than perf
   numbers do (the exact tradeoff — roll back just the offending heal vs. block the whole Save — is fresh and
   well-scoped right now from someone who's read the real `runSave()` code path). Deciding it is a short
   conversation, not an engineering task; building the decided behavior can happen anytime after.
2. **Finding 1 — grid green/orange live-tint recompute is O(n) per drag FRAME, ~1.2-1.5 SECONDS/frame at
   Terminal scale, sustained not just cold-start.** Same finding doc, Finding 1. Root cause and preferred fix
   are both already fully diagnosed (cache `attachGridToElements()`'s output once per drag-session instead of
   rebuilding from scratch every `pointermove`) — genuinely severe live-UX pain, but explicitly flagged as "a
   real restructuring of `bonsai_gridmove.js`'s `previewCommands()`/`GM._map` contract, not a threshold flip,"
   real regression risk against the already-shipped #656 tint feature. Pick up when ready for that risk; not
   blocked on Finding 2, just lower urgency per the reasoning above.
3. **`circle_radius`/`tangent_cc`/`tangent_ca`** — still correctly low priority, reasoning unchanged from
   07-07 doc (`circle_radius`'s value is already delivered by the direct radius-set UI; `tangent_cc`/`_ca`
   need a pick-an-existing-entity UI this codebase doesn't have yet, a real separate increment).
4. **Direct-manipulation UI** — still mostly done per 07-07's correction (P1-P3/H2-H3/M1 real and shipped).
   One open thread: confirm H1 ("Z-handle visibility from non-top camera angles") is fine or fix it, then a
   SHORT fresh "be the user" walk — Sonnet-dialogue-with-user territory, not a solo background dispatch.
5. **Guide screenshots — 6 still unchecked** (`fillet-edges2`, `fillet-rounded`, `samplecastle-arc-open`,
   `seedtrunk-entry`, `seedtrunk-trunk`, `walk-fixtures`) before the guide can be called clean; 3 more
   confirmed stale but not yet fixed (`move-gizmo.png`, `gridstretch-after.png`, `delete-gone.png`) — recipe
   proven, reuse it (see 07-07 doc for the exact recapture steps).
6. **SampleCastle streaming UX, ARC LOD-mesh witness generalization, MEP product survey (CW/SP/ACMV/ELEC),
   IFC write coverage gaps, coaxial MEP diameter-transition detection** — unchanged from 07-07 doc, still
   open, still real, still not urgent. Full detail there, not restated here.
7. **Distributed op-log (Tier 3)** — NOT a near-term item (still correctly "months," not a same-recipe wire)
   but a real correction landed 2026-07-07 worth carrying forward: `docs/DistributedERP.md` already has the
   doctrine (`kernel_ops` is already the same signed hash-chained op-log primitive that doc's argument is
   built on) — port it when this is eventually picked up (owner-gate+CAS over LWW; reconciliation is a
   rebase-and-replay, not a git 3-way merge), don't redesign from scratch.

## §STATE — bim-ootb PRs merged this session (all independently confirmed `MERGED` via `gh pr view`, not trusted)

#697 viewer routewalker port · #699 circle primitive · #700 in-app guide update · #701 arc primitive · #702
tangent-to-gridline · #703 oplog autosave 2-bug fix · #704 STR-rewalk regression witness. All squash-merged
to `origin/main`, tip confirmed at `f855902` (#704). 6 stale feature branches deleted post-merge. bim-compiler
`master`: `prompts/BONSAI_KERNEL_RESEARCH.md` (Tier-3 distributed op-log doctrine correction),
`prompts/SCALE_CHECK_TERMINAL_FINDINGS_2026-07-05.md` (Finding 4 UPDATE, now fully resolved by #703), this
file.
