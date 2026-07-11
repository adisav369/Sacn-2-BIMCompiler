<!-- Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com> · SPDX-License-Identifier: MIT -->
# DISC WALKER BRANCH CLOSEOUT — verify + report on 3 stale-but-open PRs, then investigate the guide-screenshot bug (2026-07-11, Sonnet-scoped, FABLE-assigned)

```
# ⚠ DO NOT REMOVE
SCOPE: bim-ootb repo only. This is a VERIFY-AND-REPORT task, not a rewrite — the hard analytical work
named "DiscWalk" in this project's memory is ALREADY DONE (see "What's already closed" below). Read this
whole file before touching anything — most of what looks like open backlog here has already landed; do
not re-diagnose it. PUSH PAUSE IS IN EFFECT (bim-compiler CLAUDE.md §⏸, standing 2026-07-11) — commit
locally, verify on localhost, do NOT push, do NOT open a PR, do NOT merge any existing open PR, until the
user/MANAGER explicitly lifts it. This session's OWNER (Sonnet, this file's author) reviews your findings
before anything gets merged — you report, you don't merge.
```

## Why this file exists
The user asked (2026-07-11) to look into "the DiscWalk task" and get a prompt file scoped for a Fable
session, since project memory (`project_disc_walker_grid_guard_marathon_2026-07-10.md`) describes it as
"tough" — a 2026-07-10 marathon that left 6 branches verified+pushed but unmerged, plus an unsolved
Z-datum bug flagged as "the actual next real gap, ahead of anything else." **Investigating fresh
(2026-07-11) found that framing is now STALE — the hard part is done.** This file scopes what's
genuinely left: re-verification of 3 small stale PRs (mechanical, Fable-suited) + one genuinely
undiagnosed bug (the guide screenshots).

## What's ALREADY CLOSED (do not re-diagnose, re-litigate, or redo this work)
- **§TE-ARC-DATUM (the Z-datum bug the memory called "the actual next real gap"): FULLY DONE, MERGED,
  BOTH REPOS.** bim-compiler: `build/disc_walker.js placeMeasured()` now measures the band→substrate
  z-offset at WALK TIME from `rule_frame_ref` (per-class mean-z refs) instead of a baked one-frame
  constant — merged to bim-compiler `master` via PR #40 (`b202eb44b`). bim-ootb: ported (commit `4ff22c0`,
  branch `fix/dw-datum-port`), verified `W-DW-DATUM` 4/4 on the SHIPPED `Terminal_ARC.db`
  (`zOff=-0.060 measured, 10 ref classes, MAD 0.024`, falsifier DROP `rule_frame_ref` reproduces the
  original collapse 1865→642), merged via PR #726 (squash commit `3d09ad6`) — **confirmed already an
  ancestor of `~/bim-ootb` local `main` right now** (verify: `git merge-base --is-ancestor 3d09ad6 main`).
- **`fix/dw-rot-units` (radians-in-a-degrees-field bug): MERGED.** PR #723 (squash commit `81f2dbd`) —
  also already an ancestor of local `main`. Do not re-port or re-verify this one; it's live.
- **SampleCastle rooms:** CLOSED as a non-issue same marathon — `disc_walker` never needed room/IfcSpace
  data for SC; the whole investigation thread was chasing the wrong branch of the problem. Nothing to do.
- **Room-mode (schedule-driven per-room walk, `fable/modeller-lod400-livewire`/`fable/meshdb-livewire`):
  MERGED** to both repos' mains 2026-07-10, independent of the above.

## ▶ IN-PROGRESS UPDATE (2026-07-11, Sonnet, same day — checked before any Fable dispatch)
**Task 1 is partially done already — do NOT redo the merges, only finish the verification.** Found three
worktrees already built exactly per the Method below, each with the target branch already merged into a
fresh checkout off current main:
- `/tmp/wt-verify-grid-tilt` (branch `verify/grid-tilt`) — `fix/grid-tilt-guard` merged in clean.
- `/tmp/wt-verify-oracle` (branch `verify/oracle`) — `fix/terminal-oracle-source` merged in clean.
- `/tmp/wt-verify-dwprobe` (branch `verify/dwprobe`) — `fable/dwprobe-dedup` merged in, but hit a REAL
  conflict (main had moved further than my dry-run check caught) — resolved KEEP-BOTH (§8E-3
  `_dwProbeMatch` + `__dwOcclusionProbe` rename coexist) + repointed `witness_mep_route_render` to the
  renamed probe. Two untracked PNGs sit in this worktree (`mep_route_render_duplex.png`/`_terminal.png`) —
  leftover from running THAT witness, harmless, can be ignored or cleaned up.

**What's NOT done yet in any of the three: the actual witness re-run + logged PASS/FAIL.** No fresh log
evidence found for any of them as of this check. **Remaining work is exactly steps 2-3 of the Method
below, nothing earlier** — cd into each existing worktree (do NOT rebuild it), run that branch's own named
witness, log the real output, report per-branch PASS/FAIL. If `wt-verify-dwprobe`'s KEEP-BOTH resolution
introduced anything unexpected, that's exactly the kind of regression this step exists to catch — check it
for real, don't assume the conflict resolution was correct just because it applied cleanly.

Once all 3 report green: this is a call for whoever holds Manager's git-admin mandate
(`prompts/MANAGER.md` — "PR work, including the merge decision, is Manager's job... once independently
verified, merge it") — not something to merge from inside this task.

## What's ACTUALLY still open — Task 1: verify + report on 3 stale PRs (no merge, no push)
Three branches are pushed, have OPEN GitHub PRs, and per the 2026-07-10 memory were "independently
verified+pushed" — but main has moved on since (each is 17-21 commits behind current `main` as of
2026-07-11). A stale "green" claim is not evidence (this project's own hard-learned lesson, same memory
file: "a worker's 'all green' report is not evidence until reproduced from a checkout that inherits
NOTHING from wherever the worker actually ran it"). Re-verify each FRESH:
1. `fix/grid-tilt-guard` (PR #722, local commits `98cf709`+`c485560`) — extends the grid-drag
   rotation/tilt guard to catch X/Y tilt AND the Modeller's real 2nd plan axis (`'y'`, not `'z'` — Z-up
   convention). Witness: check the commit for the exact witness name (grid/rotation guard tests).
2. `fable/dwprobe-dedup` (PR #724, local commits `43d713a`/`3367afb`/`52fea0e`) — dedupes a
   double-defined `window.__dwPixelProbe`, fixes a crash-on-uncaught-rejection in
   `witness_modeller_disc_walk.js`, removes a `NODE_PATH` env dependency. Witness:
   `witness_modeller_disc_walk.js` and whatever the probe-dedup's own check is.
3. `fix/terminal-oracle-source` (PR #725, local commit `c1b4f9e`) — repoints
   `witness_disc_density.js`/`witness_disc_clash.js` from a stale `Terminal_ARC.db` (0-MEP-rows since the
   embed-8 ARC-only strip) to `Terminal_meta.db` (the intended oracle, per the witnesses' own header
   comments). Witness: `witness_disc_density.js`, `witness_disc_clash.js`.

**Method (per branch, do NOT skip steps):**
- `git worktree list` first (Worktree Hygiene) — reuse `/tmp/wt-dwrot`/`/tmp/wt-grid-tilt-guard`/
  `/tmp/wt-dwprobe-dedup` etc. ONLY if genuinely idle (check `git status --short` in each — if dirty or
  mid-work, leave it, make a fresh one instead). A pre-merge conflict dry-run already ran clean for all 3
  against current main (`git merge-tree <merge-base> main <branch>` — no conflict markers) — that's a
  necessary, not sufficient, check; still re-run the real witness.
- Build a FRESH worktree off CURRENT `origin/main` (`git fetch origin && git worktree add /tmp/wt-<name>
  -b verify/<name> origin/main`), merge the target branch into IT (not the other way around), run its
  witness there, log the real output.
- Report per branch: PASS/FAIL with exact witness counts, any regression found (name it, don't paper over
  it), and whether local git merge was clean.
- **Do NOT merge into `~/bim-ootb` main, do NOT push, do NOT touch the GitHub PRs.** Report back to this
  session (Sonnet reviews, decides what happens next — possibly a simple `gh pr merge` once you confirm
  green, but that's a call for after your report, not something to do unilaterally).

## Task 2 — diagnose the guide-screenshot camera bug (genuinely undiagnosed, real investigative work)
2026-07-10: a local-only branch `fable/combined-guide-shots` (worktree `/tmp/wt-combined-guide`, still
present, DO NOT reuse it directly — it's stale, missing the 2 branches above and predates current main;
build fresh) merged the (then-)4 unmerged branches and produced 2 candidate guide screenshots. **Both
reviewed directly (real image bytes, not a caption) and both FAIL:**
- `duplex_elec_lod400_walk.png` — camera zoomed in tight on one ceiling fan, entire frame washed out to
  near-white/gray, no building context. Looks like an exposure/lighting bug in the capture, not a framing
  choice.
- `samplehouse_elec_rotation_fix.png` — camera appears positioned INSIDE a large dome/sphere mesh, filling
  almost the whole frame; raw browser UI (toolbar icons, a scrubber reading "76/76 · FlowController")
  visible in-shot. A broken/accidental mid-navigation capture, not a posed shot.

Root cause (bad camera-positioning script vs. an actual rendering defect surfacing only in this camera
path) was named as "the next concrete question" and never chased. Your job: chase it.
1. Find the actual screenshot-capture script used for `duplex_elec_lod400_walk.png` /
   `samplehouse_elec_rotation_fix.png` (search `modeller/tests/` for a guide-shot generator — likely near
   `guide_shots/` or referencing `frameElement`/`overheadTo`/camera-fly helpers from `e2e_harness.js`).
2. Reproduce BOTH bad shots fresh (fresh worktree off current main + the 3 branches above merged in) —
   confirm they're STILL bad post-merge (main has moved; the bug may already be gone, or may not be).
3. If still bad: is it (a) a camera-math bug in the capture script (wrong target/distance/up-vector — this
   project's own prior "camera-inside-mesh" incident, 2026-07-10, already diagnosed+avoided elsewhere per
   `feedback_screenshot_ui_before_deploy` / the material-parity session's own citation of it) or (b) a real
   rendering defect (something about ELEC LOD400 device meshes or the SampleHouse rotation-fix content
   itself breaks under ANY camera) — tell them apart by trying a DIFFERENT, known-good camera framing
   (e.g. this repo's own `t.frameElement`/`t.overheadTo` helpers from `modeller/tests/e2e_harness.js`,
   already used successfully in `MODELLER_RENDER_MATERIAL_PARITY.md`'s own screenshots this session) on
   the SAME content. If the known-good framing also fails, it's (b), not (a).
4. Fix whichever it is, or if it's (b) and out of your depth, STOP and report exactly what you found —
   don't guess-patch a rendering defect blind.

## Explicitly OUT OF SCOPE (named, not assigned — flag if you trip over it, don't fix it)
- **Stale radians in old signed op-log rows** (pre-dating the `dw-rot-units` fix, `81f2dbd`) — flagged in
  the 2026-07-10 memory as UNSAFE to blind-migrate (can't numerically distinguish bogus-radians from
  legit-small-degrees) and needing a per-row re-derivation from source `element_transforms.rotation_z`,
  not a data patch. This is a real, separate, harder design task — do not attempt it here, name it if you
  encounter it live.
- Any NEW material/rendering/Outliner work — that's `MODELLER_RENDER_MATERIAL_PARITY.md`'s lane (closed
  this session), don't touch those files here.

## DONE WHEN
Task 1: all 3 branches re-verified fresh with real witness output (PASS/FAIL, exact counts), reported
back — not merged, not pushed. Task 2: the guide-screenshot bug's root cause identified (camera-script vs.
rendering defect) and either fixed+re-screenshotted or clearly reported as blocked with what's known.
Findings appended to this file (a fresh dated section) so the next session — Sonnet reviewing, or whoever
picks up the actual merge/push decision — has the real current state, not a day-old memory snapshot.
