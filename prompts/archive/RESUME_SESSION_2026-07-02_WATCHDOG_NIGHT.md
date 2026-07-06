# ⚠ DO NOT REMOVE — Session resume: architect/watchdog role, 2026-07-02 NIGHT (supersedes the PM doc)

**Read this first if picking up cold after a machine restart.** This session continued the architect/
watchdog role from `prompts/RESUME_SESSION_2026-07-02_WATCHDOG_PM.md` (that doc's GeoMapping/Modeller
LOD400 threads are still correctly closed — don't re-walk them). Written just before a planned machine
restart, so it also inventories what was RUNNING at the time (all of it will be gone after restart —
this is a record of what happened, not live state to reconnect to).

## What this session did, in order
1. **Corrected two stale memory entries** — re-verified `git log` directly rather than trusting the ⛔
   status in memory: [[project_modeller_arc_viewer_rotation_gap]] (ARC-seed rotation) and
   [[project_modeller_outliner_components_stall]] (Outliner paint stall) were BOTH already fixed+merged
   (bim-ootb PR #595/#596, 2026-07-01) — memory just hadn't been updated. Fixed the memory files + index.
2. **Investigated the Teams Overlay guide** (`https://red1oon.github.io/BIMCompiler/TeamsOverlayGuide/`)
   against live production, not just the docs claim. Findings: architecturally sound and non-colliding
   with HBA (Teams never touches `viewer/panels.js`), but (a) the guide's "works identically across
   Modeller/Viewer/Kernel-ERP" claim overstates it — only Modeller + the iDempiere ERP renderer have the
   gated embed in source, the Viewer has none; (b) **neither Teams nor HBA is actually deployed to
   `bim-ootb-live`/`bim-ootb-dev` production** — both are git-only, confirmed via live `curl` (404s
   everywhere). Updated `prompts/RESUME_TEAMS_OVERLAY.md` + `teams/ROADMAP.md` (bim-ootb
   `lane/teams-overlay`, pushed `cb1416b`) with an explicit **DEPLOY GATE**: hold the production push
   until the Modeller lane's open threads settle, Modeller+ERP-only scope is accepted (not a gap), and
   the guide needs a real screenshot + step-by-step pass before that deploy. Also force-added the two
   Teams resume docs into bim-compiler git (they were sitting on disk, gitignored, never actually
   committed — same fix as the existing `RESUME_HR_BIM_ASSET.md` precedent).
3. **Reviewed the HBA §P10a/§P10b closeout** (lane/hr-overlay tip `d25ea58` at the time) — backward
   compatibility clean (only shared file touched is `viewer/panels.js`, purely additive, internal `id`
   kept stable on purpose). Found a real but minor doc-staleness bug: the session's own "What's still
   open" list still said Find↔FM was BLOCKED after the same document's later sections had already shipped
   it — flagged, not fixed (only asked to check).
4. **Reviewed and independently reproduced the Modeller evening arc** (Terminal perf-guard `#606`, guide
   framing `#608`, and the big one — `W-MV-PARITY` `#610`, which PROVED the ARC-seed anchor-placement bug:
   `center_xyz` is the IFC placement anchor, not a volumetric centre; Duplex had 253/265 elements displaced
   >0.5m, max 18.03m). Re-ran the witness myself from scratch and got the exact same numbers — not just
   trusted the report.
5. **Triaged the anchor bug against the pre-JS/Python era** at the user's request — read
   `DAGCompiler/python/extractIFCtoDB.py` directly (the actual script that produces every `*_extracted.db`)
   and found the "anchor vs. true-centre" question was never actually open: the extractor's own world-bbox
   formula (`world = R·local + center`, `center = mat4[:3,3]` from IfcOpenShell's own transform) IS the
   anchor semantics, and always has been — also confirmed full 3-axis Euler was always extracted (the
   "yaw-only" bug was a JS-reader gap, not an extraction one). Also flagged
   `ElementPersistence.java writeElement()`'s AABB-centre convention as a DIFFERENT, unrelated pipeline
   (generative compiler output, not `*_extracted.db`) — don't cite it as license for the wrong convention.
   Updated + pushed `prompts/RESUME_MODELLER_ARC_ANCHOR_PLACEMENT.md` (bim-compiler `da770c450`), closing
   the old "decide explicitly" framing in Fix design point 4.
6. **User dispatched the fix to Fable 5** (model-choice reasoning: this task went from "needs Sonnet-level
   judgment" to "well-specified execution" once the Java/Python triage above closed the one open design
   branch). **Watched it complete live**: 11-witness blast-radius suite green, `W-ANCHOR-SWEEP` 15/15
   across all 5 residents (SampleHouse 39 · Duplex 253 · SampleCastle 3225 · SampleCastle-ARC 3225 ·
   Terminal 35,552 — all maxDC in the 1e-7 to 1e-6m range), committed (`22d105e` → merged as bim-ootb
   `8449306`, PR #613), closeout doc committed+pushed by that same session. **✅ FIX DONE — the anchor
   bug is fully closed, T2/X1 flipped green, 12/12.** One thread that session flagged but I never got a
   chance to independently chase: it noticed the sweep counted `tiltedRows=0` on SampleCastle where PR
   #595's commit message claimed 497 tilted elements — investigated live, resolved it before committing,
   but I didn't re-verify that specific resolution myself. Low-risk (the fix's own numbers are otherwise
   airtight) but worth a 5-minute look if picking this thread back up.
7. **Reviewed HBA §P11** (bim-ootb PR #614, merged `5a83955`) — every HBA pane that shows a real
   AD-compiled record now deep-links into its iDempiere ERP window (Dashboard→S_Resource 236,
   Payslip→hr_movement 53042, Tenancy→c_subscription 316, IoT→c_order 143, Leave→the real "Leave without
   pay" hr_concept 53036, never a fabricated window). Independently re-ran `witness_erp_deeplink.js`
   myself — 19/19, exact match. Re-ran all 33 HBA node witnesses myself — all green. Docs
   (`docs/HRBIMAssetGuide.md`) confirmed updated with 5 new real screenshots + a reference table, committed.
8. **Investigated a "terminals feel slow" report** — no leak (checked zombies, FD counts per `claude`
   process, RSS growth — all flat/normal). Real cause: 6 concurrent `claude` terminals sharing one
   20-core/29GB box (one, `pts/0`, had been open since **2026-07-01 17:19** — 30+ hours — running a
   `mkdocs build --strict` at the moment I checked), plus Firefox with multiple heavy tabs, plus residual
   swap (~1GB) from earlier E2E-suite bursts (Terminal-scale sweeps, HBA suite, GeoMap re-mines) that had
   already cooled down by the time I looked. Nothing to fix — just contention from legitimate concurrent
   work, matches this project's own "N-terminal workflow" doctrine.

## Standing lessons this session reinforced/produced
- [[feedback_dont_relitigate_settled_doctrine]] / verify memory against `git log` before trusting a ⛔ or
  ✅ status — memory drifts when a fix lands but the memory update doesn't (twice this session: rotation
  gap, outliner stall).
- **New lesson: read the pre-JS/Python-era source before treating a design question as open.** The
  anchor-vs-centre "decide explicitly" branch in the original fix spec wasn't actually undecided — it was
  answered the moment `extractIFCtoDB.py` was written, years before the Modeller existed. Same shape as
  [[feedback_read_java_spec_first]] — the ground truth usually already exists upstream; check before
  deliberating from scratch.
- **When multiple sessions share a non-worktree-isolated repo** (bim-compiler itself has no PreToolUse
  worktree hook, unlike bim-ootb) — expect to see other sessions' uncommitted edits appear live in
  `git status` (observed on `docs/ModellerGuide.md`, `prompts/RESUME_HR_BIM_ASSET.md`,
  `prompts/RESUME_MODELLER_ARC_ANCHOR_PLACEMENT.md` mid-session). Don't commit over them; check timestamps
  and active processes (`ps aux` grep for the worktree/file path) before touching a file that looks
  mid-edit.

## Concurrent sessions observed at the moment this doc was written (2026-07-02 ~23:06, pre-restart)
All of these will be terminated by the restart — this is a historical snapshot so a fresh session can
guess what each worktree/branch below was mid-flight on, and check `git log`/`git status` there first
before assuming anything is still "live."
| tty | pid | started | cwd | best-guess activity (from correlated evidence, not 100% certain) |
|---|---|---|---|---|
| pts/0 | 644880 | 2026-07-01 17:19 (30+ hrs) | bim-compiler | ModellerGuide.md screenshot/step-by-step polish session — caught running `mkdocs build --strict` live; recap mentioned "SampleCastle ARC-open shot now correctly embedded," next was checking whether PR #608 also fixed the remaining 21 broken guide frames |
| pts/2 | 853518 | 2026-07-02 11:39 | bim-compiler | longest same-day session; `prompts/RESUME_HR_BIM_ASSET.md` was mid-edit under it at one point — likely the HBA lane driver (P10→P11 arc) |
| pts/4 | 944811 | 2026-07-02 19:09 | bim-compiler | unclear — not independently identified this session |
| pts/3 | 964672 | 2026-07-02 19:56 | bim-compiler | **this session** (the watchdog/review one that wrote this doc) |
| pts/1 | 988915 | 2026-07-02 22:58 | bim-compiler | freshly started near end of this doc's writing, not yet identified |
| (bg) | 988321 | 2026-07-02 22:57 | bim-compiler | forked/resumed continuation of an earlier session (`--fork-session --resume 49817bf6...`) — a daemon-managed background continuation, not a foreground terminal |
Non-worktree-isolated repo (bim-compiler) means several of these share literally the same working
directory — that's WHY files kept showing up mid-edit under each other during this session.

## bim-ootb worktrees present at write time (`/tmp/wt-*`, persist across restart — just directories)
`wt-anchor-fix`(fix/arc-anchor-placement, DONE+merged, safe to remove), `wt-arc-mesh`, `wt-arc-rot-fix`
(DONE+merged #595, safe to remove), `wt-e2e`, `wt-geomap`, `wt-geomap-wire`, `wt-guide-f2`, `wt-lod400`,
**`wt-lod400-bug2`** (fix/modeller-lod-catalog-match — ⚠ STILL has real uncommitted WIP as of last check:
modified `arc_editable.js`/`modeller.html`/`witness_arc_editable.js` + a new `witness_e2e_lod_match.js` +
captured e2e_shots — a separate "SampleCastle geometry hell" / LOD-300-catalog-match thread, genuinely
unresolved, not part of anything closed this session — **check this one first if resuming Modeller work**),
`wt-outliner-stall` (DONE+merged #596, safe to remove), `wt-promptmain`, `wt-redpill` (lane/teams-overlay,
has the DEPLOY GATE update pushed), `wt-signing-speed` (branch exists, genuinely no new work on it yet —
signing-speed Candidates C/D still unimplemented), `wt-specfix`, `wt-stretch-ride` (stale, superseded by
merged PR #604 content).

## Where to pick up
- **Modeller code: fully closed this session** (#606, #608, #610, #613 all merged+verified). The only
  loose Modeller thread is `wt-lod400-bug2` (see above) — status unknown until someone re-reads it fresh.
- **Teams overlay**: deploy gate is now written down (`prompts/RESUME_TEAMS_OVERLAY.md` on
  `lane/teams-overlay`) — do NOT push `teams_embed.js` into production `modeller.html`/`erp/idempiere.html`
  without re-confirming the Modeller lane is genuinely settled (it looks close — just `wt-lod400-bug2` +
  whatever the guide-screenshot session (pts/0) is still doing).
- **HBA**: §P10a/§P10b/§P11 all verified done+merged+pushed this session. One tiny doc-staleness item
  flagged but not fixed (the "What's still open" list in `prompts/RESUME_HR_BIM_ASSET.md`'s closeout
  header, bim-ootb copy) — low priority, fix opportunistically.
- **GeoMapping**: still no known open items (confirmed both by the PM doc and by this session — nothing
  new touched it).
- If none of the above: `PROGRESS.md`'s `## Current State` is the real entry point, same as always.
