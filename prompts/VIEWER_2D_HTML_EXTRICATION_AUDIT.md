<!-- Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com> · SPDX-License-Identifier: MIT -->
# VIEWER 2D.HTML — extricate live DXF, archive dead grid duplication (2026-07-12)

```
# ⚠ DO NOT REMOVE
SCOPE: bim-ootb `viewer/2d.html` (48,307 lines) + `viewer/sw.js` (precache list) + `viewer/main.js`
(fallback wiring) + `tests/specs/14-2d-plans.spec.js` / `28-grid-overlay-init.spec.js`. User's own
words (2026-07-12): "investigate 2D.html further, find out what can be extricated and what is
deprecated to move to archive instead." This is a real investigation with a real deliverable (a
split, not a delete) — read this whole file first, don't start cutting code blind. PUSH PAUSE LIFTED
for this repo — commit locally, verify on localhost (including re-running the 2 affected test specs,
not just eyeballing), push + PR with auto-merge once done.
```

## What's already confirmed, don't re-derive
- **The grid-manipulation half is genuinely superseded.** `viewer/main.js` `open2DPlans()` (line
  ~259) prefers `APP.toggleGridOverlay` (`viewer/grid_overlay.js`, 1,932 lines, the modern embedded
  3D-aware grid) and only `window.open('2d.html', ...)` as a **fallback** when that fails to load
  (`main.js` line 274, `§2D_OPEN grid_overlay.js not loaded — falling back to 2d.html`). Test spec
  `28-grid-overlay-init.spec.js` exists specifically to prove this fallback does NOT normally fire
  (`T_INIT_02: open2DPlans() calls toggleGridOverlay, NOT fallback 2d.html`). This is strong evidence
  the file's OWN grid code is dead weight in the normal path.
- **The DXF half is NOT superseded and must survive intact.** `grid_overlay.js` has zero DXF
  handling (grepped, no matches). `docs/AboutMore.md` documents a real, deliberate architecture:
  `dxf_export.js` (export TO DXF) + `dxf-parser.js` (parse DXF) + `2d.html` labeled "DXF 2D plan
  viewer (Canvas2D)" — a real pipeline, not incidental code. `tests/specs/14-2d-plans.spec.js` has
  **9 active `@fast`-tagged tests**, one (`14.9`) tagged **`@sacred`** — this project's own marker
  for a protected regression baseline. None of this may be lost.
- **Structural recon done (grep-level only, not a full read):** 103 "grid"-related mentions vs 80
  "dxf"-related mentions in the file — comparable weight, neither trivial. **Zero `§SECTION`-style
  header comments anywhere in the file** (unusual for this codebase's normal convention — this file
  predates it; git log shows only 2 commits total, last 2026-05-23, consistent with being an early,
  never-refactored file). 10 separate `<script>` tags — a possible real seam between concerns, not
  yet confirmed as a clean boundary.
- **What's still genuinely unknown, this task's real job:** whether the grid code and DXF code are
  cleanly separable (e.g. by `<script>` block, or by a shared-vs-exclusive function boundary) or
  whether they share state/canvas/helpers in a way that makes a clean split nontrivial. Don't assume
  either way — trace it for real.

## Task 1 — map the file's actual structure via REAL code coverage, not naming guesses
**Higher-leverage method (user's own steer, 2026-07-12) — use this as the PRIMARY evidence, not a
manual read-and-guess-from-function-names pass:**
1. Run `tests/specs/14-2d-plans.spec.js` (all 9 tests — this IS the real, current, exhaustive-as-we-
   have-it exercise of the live DXF flow) against `2d.html` with Playwright's V8 JS coverage collector
   on (`page.coverage.startJSCoverage()` before the test flow, `stopJSCoverage()` after — Chromium-
   only, matches this project's existing headless-chromium convention). This gives an EXECUTION-BASED
   map of exactly which lines/functions the real DXF flow actually touches, not a guess from what a
   function's name suggests.
2. Cross-reference: everything with **zero coverage** across the full 9-test run is a strong
   candidate for GRID-ONLY/dead code — but zero coverage isn't automatic proof of safety-to-remove
   (the 9 tests may not exercise every edge case, e.g. error paths, uncommon drag-drop scenarios).
   Before removing a zero-coverage block, do a QUICK manual sanity check (read just that block, not
   the whole file) to confirm it's plausibly grid-related and not, say, an error-handler or rare-path
   DXF code the tests simply don't happen to trigger. This is a much smaller manual-review burden
   than reading all 48K lines cold.
3. Everything WITH coverage from the DXF test run is SHARED-or-DXF by definition (it ran during a
   pure DXF exercise) — must stay, no further classification needed for those lines.
4. Produce the real map (coverage-derived: covered vs uncovered, with the quick sanity-check verdict
   on each uncovered block) before touching anything — same "reviewable checkpoint before bulk work"
   discipline as `BIMUSERGUIDE_PILL_COVERAGE_AUDIT.md` used this session (commit the map on its own
   first, cite the actual coverage numbers/tool output, not a summary claim).
5. If coverage tooling turns out to be impractical here for a real reason (report exactly why, don't
   silently fall back) — THEN fall back to a manual read of the file's 10 `<script>` blocks and ~69
   top-level functions, classifying each as GRID-ONLY/DXF-ONLY/SHARED. Coverage is the preferred
   method; manual reading is the fallback, not the default.
6. If GRID-ONLY code (by either method) turns out to be deeply intertwined with SHARED code (e.g. one
   giant function doing both grid AND canvas setup), name that honestly rather than forcing an
   artificial split — report it as a harder case, don't hack around it.

## Task 2 — extricate: keep DXF+SHARED live, archive GRID-ONLY
1. If Task 1 finds a clean split: create the trimmed live file (DXF+SHARED only) — either in-place
   edit of `2d.html` removing the GRID-ONLY blocks, or (if that's structurally cleaner) a fresh file
   — your call, name which you did and why.
2. Move the removed GRID-ONLY code to an `archive/` location within the repo (per the user's own
   words: "Git will still have it folded if needed to pull or refer" from earlier in this session —
   they're fine with git history as the real archive; a literal `viewer/archive/2d_grid_legacy.html`
   or similar physical archive folder is a nice-to-have for discoverability, not a hard requirement —
   your call which the user would prefer, or ask if genuinely unclear rather than guessing).
3. **Do NOT touch `viewer/sw.js`'s precache list or `viewer/main.js`'s fallback wiring** unless the
   trimmed file's filename/path changes — if you keep it as `2d.html` in place, both should need zero
   edits. If you rename/move it, update both, and verify the fallback still actually works (open a
   building, force `grid_overlay.js` to fail to load — e.g. rename it temporarily in a throwaway
   test — confirm the fallback still opens a working DXF-capable page, then revert the throwaway
   rename).

## Task 3 — verify nothing broke, real evidence not eyeballing
1. Re-run `tests/specs/14-2d-plans.spec.js` in full (all 9 tests, including the `@sacred` one) against
   the trimmed file — must stay 9/9 green. This is the load-bearing check for this entire task; if
   ANY of these regress, the extraction wasn't clean, go back to Task 1.
2. Re-run `tests/specs/28-grid-overlay-init.spec.js` — must stay green (confirms the normal
   `grid_overlay.js` path still works and the fallback still isn't triggered in the healthy case).
3. Real before/after line count + a one-paragraph summary of what moved where.

## Explicitly out of scope
- Any change to `grid_overlay.js` itself — it's already the correct, modern grid implementation,
  don't touch it.
- Any change to `dxf_export.js`/`dxf-parser.js` — separate files, not this task's target.
- Don't invent new DXF or grid features — this is pure extrication/archival, not a rewrite.

## DONE WHEN
Task 1's structure map committed as its own reviewable checkpoint. Task 2's split done (or, if Task 1
finds the code isn't cleanly separable, that finding reported honestly instead of a forced split).
Task 3's two test specs both green with real log output cited, before/after line counts reported.
Findings appended to this file.
