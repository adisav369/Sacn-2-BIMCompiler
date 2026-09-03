# ⚠ DO NOT REMOVE — Session resume: watchdog role, 2026-07-06 PM (supersedes
`prompts/RESUME_SESSION_2026-07-06_WATCHDOG.md`)

**Read this doc, then go straight to `§NEXT SESSION'S JOB`.** The user has explicitly asked for sharper
execution this round after a real mid-session drift (see `§LESSONS` — read it, it's short and it's exactly
what not to repeat).

## §LESSONS LEARNT THIS SESSION (hardened into CLAUDE.md + memory — don't re-litigate, just follow)
1. **Never invent a role-boundary under pressure.** When challenged ("aren't you supposed to X?"), grep
   CLAUDE.md + memory for the ACTUAL rule first. Fabricating a plausible-sounding new restriction on the spot
   ("that's not my role") is itself the drift, not a correction. (CLAUDE.md Standing Rules, new Anti-Drift rule.)
2. **A session working a `prompts/#.md` file updates ONLY that file — never `MEMORY.md`.** This was the root
   cause of `MEMORY.md` bloating to 264 lines twice. Findings/status/proof go in the prompts file's own dated
   section. Memory-writing is a separate, deliberate, rare synthesis pass — not a byproduct of finishing a task.
   (`feedback_prompt_file_organization.md` rule 0.)
3. **One canonical file per topic, no duplicates.** Before creating any `prompts/*.md`, grep for the topic
   first. `MODELLER_GIT_FAITHFUL_HISTORY.md` was wrongly created as a new file this session when
   `RESUME_WORLD_HISTORY_DEDUP_RESTORE.md` already owned that exact engine (`common/history_bar.js`) — merged
   in, duplicate deleted. Same check applies to YOU this round: don't spawn a new file for Modeller work that
   already has a home.
4. **Compact to links, no prose, when documenting.** `MEMORY.md` is now 80 lines, bare `[Title](file.md)`
   links only — keep it that way. `PROGRESS.md`'s juggled board is one line per topic (file + commit hash +
   what's open) — don't let it re-bloat with paragraph status again.
5. **Verify independently, every time — don't trust a recap.** This session caught: a genuine CI-breaking bug
   in PR #673 before merge (`ICONS` undefined), and confirmed PR #675's "honestly left red" G6 claim was real
   by reproducing the failure myself. Keep doing exactly this — re-run the actual witness fresh, don't just
   read the session's own numbers.

## §NEXT SESSION'S JOB
User: **"sharp eyes on the Modeller, others I will leave to you."** — meaning: give Modeller-touching work
EXTRA scrutiny this round (it's had 2 real bugs surface already this week: the `bonsai_oplog.js redo()`
LIFO-order bug, and the G6 branch-switching gap — Modeller's shared-history-engine work is not a rubber-stamp
zone). Everything else, apply the normal verify-before-trust bar.

**2 new merges landed since the last resume doc, neither independently verified yet:**
1. **bim-ootb PR #678 — "wire the World-History 'W' pill" (Modeller).** ⚠ MODELLER — extra scrutiny.
   This is `RESUME_WORLD_HISTORY_DEDUP_RESTORE.md`'s Part 1 (confirmed zero-built as of this morning, parked
   at the user's own direction since they were "only testing Viewer"). Before trusting it's done:
   - Confirm it actually reuses `common/whole_history.js`/the shared engine, not a reinvented mechanism
     (`feedback_prompt_file_organization.md` rule 3 — one owner per fact/mechanism).
   - Check it respects the dedup contract from that file (same-building reopen same-day advances `ts`,
     doesn't stack a fresh chip) — this was an explicit guardrail, not optional.
   - Re-run whatever witness it shipped FRESH from a clean checkout — don't read the numbers, reproduce them.
   - Check for interaction with PR #675's `modeller_history.js` (the OTHER history engine Modeller just
     gained, for in-page undo/redo) — two history-adjacent Modeller PRs landed in close succession; confirm
     they're actually orthogonal (cross-page World History vs in-page undo/redo tree) and don't collide on
     shared DOM/state.
   - Merge this PR's write-up into `RESUME_WORLD_HISTORY_DEDUP_RESTORE.md` as a dated section (it owns this
     topic) — do NOT let a new file get created for it.
2. **bim-ootb PR #677 — "outline shine-through + panel cascade + IoT phase offset" (HBA, items A/B/C).**
   Normal scrutiny. This is `Viewer/HBA/RESUME_HR_BIM_ASSET.md §2026-07-06c`'s A/B/C batch. Verify each of the 3 fixes
   against what that section actually asked for (OutlinePass-style shine-through, not just always-on-top;
   per-sensor phase OFFSET not just wider jitter; all 6 panels no longer sharing one fixed position) — re-run
   witnesses fresh. Update that file's dated section with real status, not a restated recap.

**Also still open, lower priority, only if time remains (not urgent per prior sessions):**
- `prompts/Viewer/HBA/RESUME_HR_BIM_ASSET.md` — item D (LOD device mesh) needs a user go/no-go, not code.
- `prompts/PILL_DRAWER_REORGANIZATION.md` — first-touch pill-rail flicker, still unconfirmed (2 hypotheses
  written up, needs a live single-tap repro with `console.trace()`).
- `prompts/Viewer/OPEN_BUTTON_IFC_BCF_MERGE.md` — Item 1 (Drop-IFC→Open button) shipped PR #676; Item 2 (Save As
  IFC/BCF) still open, needs a design answer before building (see file for the open questions).
- `prompts/MODELLER_GIT_FAITHFUL_HISTORY.md` content now lives inside `RESUME_WORLD_HISTORY_DEDUP_RESTORE.md`
  — Phase 3 (visible dot-strip UI) recommended before chasing the G6 gap (watchdog's own recommendation,
  not yet confirmed by the user — see that file's tail section).

## §PROCESS (unchanged from before, now backed by CLAUDE.md hard rules, not just convention)
- Verify claims against real commits/diffs/fresh witness runs before recording anything as done.
- Update the ONE canonical `prompts/<Feature>.md` file per topic — never spawn a duplicate.
- Keep `PROGRESS.md`'s juggled board to one line per topic.
- Do not write to `MEMORY.md` as a byproduct of this work — this resume file itself is the record.
- Hand the user a paste-ready `Resume prompts/<FILE>.md § "<section>"` pointer the moment a write-up lands,
  without being asked.
