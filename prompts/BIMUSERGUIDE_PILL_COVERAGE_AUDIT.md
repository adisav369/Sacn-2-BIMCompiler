<!-- Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com> · SPDX-License-Identifier: MIT -->
# BIMUSERGUIDE PILL COVERAGE AUDIT — fill the missing toolbar features (2026-07-12, dispatch to Agent)

```
# ⚠ DO NOT REMOVE
SCOPE: bim-compiler `docs/BIMUserGuide.md` + `docs/img/viewer/*` only — a completeness pass, not a
rewrite. User's own words (2026-07-12): "I see Viewer guide lots of missing ie on the whole pill
list of other features besides Time machine ERP which can link back to ERP guide." Read this whole
file, triage first (see Task 1), then execute. Read the log after every run. PUSH PAUSE LIFTED for
this repo — commit locally, verify on localhost, push, no PR needed for docs-only (same pattern as
every docs commit this session — direct push to `fable/meshdb-livewire`, no separate branch/PR
ceremony for pure docs changes, that convention has held all session).
```

## Ground truth — the real pill list vs what's documented (confirmed via source, don't re-derive)
`common/pill_builder.js` + `viewer/panels.js` (bim-ootb) define these `id:` actions — the full real
toolbar/drawer surface:
`audio, background, bbox, cam-pivot, cam-reset, camview, clash, clash_rules, corporate, docHist,
find, fly, fullscreen, grid_rules, hbaFM, help, home, initbubble, inspect, issues, json-editor,
measure, navigate, night, open, palette, precision, report, save, schedule, section, settings, sfx,
shadow, share, tm, walk, whwalk, worldhist, xray`

`docs/BIMUserGuide.md` currently documents, explicitly, only: X-Ray, Measure, Section-cut, Screenshot,
Storey filter, Discipline toggle, Deep-link URL, IndexedDB cache, City mode, Find panel (fully, just
added), FM/Operate lenses (cross-linked to `HRBIMAssetGuide.md`), Teams overlay (cross-linked to
`TeamsOverlayGuide.md`), and a mobile-only list. Roughly a dozen of ~40 real actions are covered.

## Task 1 — triage every undocumented id BEFORE writing anything (don't blindly document all 40)
For each id not already covered, read its `fn`/behavior in `panels.js`/`pill_builder.js` and sort
into exactly one bucket:
1. **Real user-facing feature, needs guide coverage** (most of these, likely): `tm` (Time Machine),
   `clash`/`clash_rules` (clash detection), `schedule`, `share`, `worldhist`/`docHist` (World/Doc
   History — note this project already has extensive History-lane work, check
   `project_whole_history_timeline.md`-style memory/prompts before writing from scratch), `walk`
   (indoor walkthrough — may already be covered under "Viewer Features" bullets, verify), `whwalk`,
   `save`/`open`, `night`/`shadow`/`background`/`palette` (visual/display options), `precision`,
   `issues`, `report`, `sfx` (sound), `inspect`, `cam-pivot`/`cam-reset`/`camview`, `home`,
   `fullscreen`.
2. **Already covered elsewhere, needs only a cross-link** (the user's own example): `tm` (Time
   Machine) — `docs/ERPUserGuide.md` already has full authoring/playback/What-if coverage (lines
   ~162-230+, confirmed this session). Do NOT re-document Time Machine in BIMUserGuide.md — add a
   short pointer sentence + link, same pattern already used for FM/Operate lenses and Teams overlay
   in this same doc (see how those two are handled — one paragraph + "Full walkthrough: [link]").
   Check EVERY id for this pattern before writing fresh prose — `worldhist`/`docHist` may also
   already be covered by history-lane docs elsewhere, check before assuming it needs new content.
3. **Internal/dev-only, out of scope for an end-user guide** — `json-editor`, `corporate`,
   `initbubble`, `grid_rules` look like dev/admin tooling by name alone, but VERIFY by reading what
   they actually do before excluding (don't guess from the id string) — a wrong exclusion silently
   drops a real feature, a wrong inclusion documents an internal tool as if it's for end users.
4. Report the full triage (id → bucket + one-line reason) as the FIRST thing committed, before any
   prose/screenshot work — so the scope is visible and reviewable before the bulk of the work happens.

## Task 2 — write + screenshot bucket-1 items, cross-link bucket-2 items
Same quality bar as every guide addition this session (see the Find panel section already in this
doc as the template): numbered step-by-step navigation (which pill, where, what it does), real
Playwright screenshots (`deviceScaleFactor:2`, tight clips, 0 console errors — not placeholders),
placed under `## Viewer Features` alongside the existing sub-sections. Group related small features
into one sub-section rather than one heading per pill where it reads better (e.g. `night`/`shadow`/
`background`/`palette` are all "display appearance" toggles — one "Display options" sub-section with
a small table, not four separate headings).

## Explicitly out of scope
- `docs/ModellerGuide.md` — separate doc, not this task (unless a bucket-3 review finds something
  that's genuinely Modeller-only mislabeled here, name it, don't silently move it).
- Rewriting any ALREADY-documented section (X-Ray, Measure, Section-cut, Find panel, etc.) — this is
  purely filling gaps, not a full-doc revision.
- Any code changes — this is docs-only, even if a triage step reveals a pill that seems broken or
  oddly named; name it as a finding, don't fix it here.

## DONE WHEN
Task 1's full triage table committed first (reviewable checkpoint). Every bucket-1 id has a real,
screenshotted section; every bucket-2 id has a one-sentence cross-link (no duplication); every
bucket-3 exclusion is justified with what was actually read, not guessed. Pushed to
`fable/meshdb-livewire` per this session's docs convention.
