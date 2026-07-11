<!-- Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com> · SPDX-License-Identifier: MIT -->
# TRILOGY STALE-CODE AUDIT — mark dead/superseded code across Modeller+Viewer+ERP (2026-07-12, Fable one-shot)

```
# ⚠ DO NOT REMOVE
SCOPE: bim-ootb `modeller/`, `viewer/`, `erp/` (the "trilogy" — 277 top-level files, ~177K lines,
confirmed this session) — top-level files only (not node_modules/tests/build). Generalizes
`viewer/2d.html`'s pilot case (below) into a full trilogy sweep. User's own framing (2026-07-12):
"make the prompt more general... should it be done by Fable one shot, to review the whole codebase
in use by trilogy only, and mark which are not or stale for removal?" Answer worked out below —
**one-shot for DISCOVERY/MARKING only, NOT for deletion.** Read this whole file before running
anything. PUSH PAUSE LIFTED for this repo — commit locally, push the REPORT when done (docs-only,
no PR ceremony needed for a marked-report commit). Actual code removal is explicitly OUT of this
task's scope (see "Why discovery and deletion are split," below) — do not delete files in this pass
even if a finding looks obvious.
```

## Why discovery and deletion are split (read before objecting to the scope)
This project's own standing discipline, used consistently all session (DiscWalk branch closeout,
FIND_PANEL_PLANT_ROOM_GATE_FIX, WALKER_FIXTURE_RENDER investigation): **investigation tasks report,
they don't act** — a separate pass (often a different session, always independently verified) does
the actual change, once a human/Manager has reviewed the findings. At trilogy scale (277 files), a
single one-shot pass that BOTH discovers AND deletes has no review checkpoint before something
real is destroyed — that's the wrong risk shape for this size of change, however good the evidence
looks in the moment. One-shot IS appropriate for the discovery/marking half (see feasibility below);
it is not appropriate for bundling deletion into the same unreviewed pass.

## Feasibility check (done before writing this, not assumed)
- 54 Playwright spec files exist (`tests/specs/*.spec.js`) — a real, substantial exercise of the live
  apps, confirmed this session. Running the full suite with coverage collection once is mechanically
  tractable in a single session (each spec is `@fast`-tagged or a normal integration test, not a
  multi-hour job).
- Three real entry points anchor the "trilogy": `modeller/modeller.html`, `viewer/viewer.html`,
  `erp/idempiere.html` (there are also `index.html`/`index2.html`/`gallery.html`/`LargeCity.html` at
  the repo root — landing/launcher pages that route INTO the trilogy; treat these as additional
  entry points too, not part of the trilogy being audited).

## Method — same higher-leverage approach as the 2d.html pilot, applied trilogy-wide
**Two complementary passes, run both — they catch different things:**

1. **Static reachability (highest-confidence signal, do this FIRST — it's cheap and decisive).**
   From the entry points above, trace every `<script src=...>`, dynamic `import()`, `fetch()` of a
   `.js` file, `window.open()` target, and iframe `src` — build the set of files EVER referenced from
   any entry point, directly or transitively. **A file that appears in `modeller/`, `viewer/`, or
   `erp/` but is in NO entry point's reachability set at all is a confirmed orphan** — nothing loads
   it, ever, regardless of what a test suite does or doesn't exercise. This is stronger evidence than
   coverage (a referenced-but-never-covered file might just be under-tested; a never-referenced file
   is definitionally dead weight).
2. **Dynamic coverage (for files that ARE reachable, but might still be dead in practice).** Run the
   full 54-spec suite with Playwright V8 coverage collection (`page.coverage.startJSCoverage()`/
   `stopJSCoverage()`, same method as the 2d.html pilot below) across all three apps. Files/functions
   reachable-but-zero-coverage across the ENTIRE suite are candidates — same caveat as the pilot: not
   automatic proof, do a quick manual sanity check per flagged file before marking it, since the test
   suite may simply not exercise every real feature.
3. **Combine into a 3-tier marking, not a binary live/dead:**
   - **CONFIRMED ORPHAN** — unreachable from any entry point. Highest confidence, name for removal.
   - **STALE CANDIDATE** — reachable but zero coverage across the full suite, sanity-checked
     manually and still looks dead. Name for removal, flag the sanity-check reasoning.
   - **LIVE** — reachable AND covered, or reachable-but-uncovered where the sanity check found real,
     plausible functionality the suite just doesn't happen to exercise (name this reasoning too, so
     the "why we kept it" is on record, not silent).

## Task 0 — the 2d.html pilot (already scoped in detail, do this one FIRST as the proof-of-method)
`viewer/2d.html` (48,307 lines) is the concrete case that started this audit — already has a full,
detailed task spec ready to execute (grid-manipulation half confirmed superseded by
`viewer/grid_overlay.js`, DXF half confirmed live via `docs/AboutMore.md` + 9 active tests incl. one
`@sacred`). Doing this one first, in full (structure map → split → archive → re-verify both affected
specs green), proves the method works before applying it at trilogy scale — don't skip straight to
the full sweep untested. Full detail (don't re-derive): see this file's git history / the prior
version of this spec — **[full original Task 1-3 detail, condensed]:**
1. Static+coverage map `2d.html`'s ~69 functions/10 script blocks against `14-2d-plans.spec.js`'s 9
   real DXF tests.
2. Split: keep DXF+SHARED code live (in `2d.html` or a renamed file, your call), move confirmed
   GRID-ONLY code to an archive location (physical `archive/` folder is a nice-to-have, git history
   is the accepted baseline per the user's own words — ask if genuinely unclear which they want).
3. Re-verify `14-2d-plans.spec.js` (9/9, incl. `@sacred`) AND `28-grid-overlay-init.spec.js` both
   green post-split. Report before/after line counts.

## Task 1 — trilogy-wide static reachability sweep
Build the full reachability set from all entry points (modeller.html, viewer.html, idempiere.html,
+ the 4 root launcher pages) across `modeller/`, `viewer/`, `erp/`. Report every file NOT in that set
as a CONFIRMED ORPHAN, with how you traced it (don't just assert "unreferenced" — show the grep/trace
evidence per file, this is a reviewable checkpoint).

## Task 2 — trilogy-wide coverage sweep
Run the full 54-spec suite with coverage across all three apps. Cross-reference against Task 1's
reachable-but-not-yet-classified files. Produce the 3-tier marking (CONFIRMED ORPHAN / STALE
CANDIDATE / LIVE) for every file in the trilogy, with the evidence and (for STALE CANDIDATE/LIVE
calls) the sanity-check reasoning, not just a verdict.

## DONE WHEN
Task 0 (the pilot) fully executed and verified — this is the proof the method works, not optional.
Task 1's reachability map committed as its own reviewable checkpoint (real trace evidence per
orphan, not assertions). Task 2's full 3-tier marking table committed, covering every file in the
trilogy. **No files deleted in this pass** — the marking table is the deliverable; removal is
explicitly a separate, later, per-item follow-up (this file's own scope guard, re-stated: report,
don't act).
