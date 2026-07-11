<!-- Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com> · SPDX-License-Identifier: MIT -->
# MODELLER RENDER MATERIAL PARITY — bring material to Viewer standard (2026-07-11, MANAGER-assigned)

```
# ⚠ DO NOT REMOVE
SCOPE: bim-ootb `modeller/*.js` (rendering path) — Modeller's LOD is confirmed no longer bad (user's
own words, 2026-07-11, after reviewing today's live Duplex screenshots), but MATERIAL rendering is
still wrong: glass walls/windows do not render see-through — no transparency where the Viewer shows
it correctly. User directive: get Modeller material rendering to the Viewer's "pristine" standard.
This is a NEW SONNET-owned task, not something to fire-fight reactively mid-session — read this
whole file, plan, then execute. Read the log after every run. PUSH PAUSE IN EFFECT (CLAUDE.md §⏸) —
commit locally, verify on localhost, do NOT push, do NOT open a PR, until told otherwise.
```

## Why this blocks the user guide
`docs/ModellerGuide.md` (bim-compiler, 29 real screenshots already) is the tangible external proof
of VISION-LOCK — MANAGER.md's own words: "does this get us closer to a guide page that can honestly
show this feature." Today's new Building Parts Outliner category (`modeller/building_parts_outliner.js`,
commit `f10c5295`, verified working live) is genuinely guide-worthy content, but the user's own
verdict on reviewing it: **"still not fitting for the guide"** because the material rendering isn't
pristine — a glass window that doesn't read as glass undermines any screenshot taken through it. Do
not add new guide screenshots until this is fixed (or scoped-around, if fixing turns out to be a
separate axis from what a screenshot needs — investigate first, don't assume).

## Task 1 — Glass/window transparency (the named defect)
1. Confirm the Viewer's OWN material path first — find where `viewer/scene.js` (or wherever material
   assignment lives) sets glass/IfcWindow/curtain-wall transparency correctly (opacity, transmission,
   `THREE.MeshPhysicalMaterial` or similar) and CITE the exact mechanism — don't guess at what
   "pristine" means, read the working reference implementation.
2. Find the Modeller's equivalent material-assignment path (likely `modeller/*.js` — check
   `arc_editable.js`, `bonsai_render.js`, or wherever meshes get materials applied on open/edit) and
   diagnose why glass isn't getting the same treatment: missing material assignment entirely for
   `IfcWindow`/curtain-wall classes, a different (non-transparent) material preset, or a renderer
   config difference (e.g. transparent objects need `depthWrite:false`/render-order handling that the
   Modeller's edit-focused pipeline might not have set up, since it wasn't built for glass rendering
   fidelity the way the Viewer was).
3. Port/align the Modeller's material path to match the Viewer's, citing the exact source. Do NOT
   invent a new material scheme — if the Viewer's approach doesn't map cleanly onto the Modeller's
   different rendering context (e.g. it uses instancing differently, or the Modeller needs materials
   to stay pick/editable in a way the Viewer's don't), name that friction explicitly and propose the
   adaptation, don't silently diverge.
4. Verify on real data: Duplex (has real `IfcWindow` elements, confirmed this session — 4 windows on
   Level 1 alone per the BOM tree) and any building with real curtain-wall glazing (HHS has 629 real
   `IfcPlate` glazing panels, discipline='ARC', confirmed this session in the sparse-wall work —
   good stress-test for volume). Screenshot before/after, same discipline as every other guide shot
   this session — real render, no camera/exposure script bugs (see the 2026-07-10 "camera-inside-mesh"
   incident this session already diagnosed and avoided — don't repeat that mistake here either).

## Task 2 — Outliner collapser, named quirk (user, 2026-07-11): "the Outliner collapser, i cant find it"
The user tried to collapse/close the Outliner panel in the Modeller and could not find the control.
`bonsai_outliner.js`'s panel header does render a chevron (`<div ... id="bonsai-outliner">` with what
looked like a `‹` collapse affordance near "OUTLINER" in this session's own screenshots — see
`el.querySelector('#bo-adj')` and the header markup around `mount()`, ~line 106-120) — but it
apparently isn't discoverable/working for a real user. Investigate:
1. Does a working collapse control exist at all, or was one only ever partially built (dead markup,
   no handler wired)?
2. If it exists: why isn't it discoverable — too small, wrong affordance (looks like decoration not
   a button), no hover/tooltip, hidden behind another element, or a real functional bug (click does
   nothing)?
3. Fix or add a clearly-discoverable collapse/expand control for the whole Outliner panel (not just
   individual tree nodes, which already collapse/expand fine — this is the panel-level chrome).
4. This is explicitly named "small matter" by the user but tracked here so it isn't lost — same
   WORK-TO-ZERO discipline as everything else in this repo's prompts/ files: don't silently drop a
   named user complaint, even a minor one.

## Scope discipline
- This is Modeller-side (bim-ootb `modeller/`) rendering work — do not touch bim-compiler.
- Do not expand into a general Modeller polish pass — stay on the two named items above. If you find
  other material/rendering defects while working (e.g. other IfcClass materials also wrong), NAME
  them in a findings section, don't silently fix everything you notice (scope creep risk) — flag,
  let a future session/the user prioritize.
- Follow this session's established pattern: work in a `/tmp/wt-*` worktree (check `git worktree list`
  first, per Worktree Hygiene), commit locally only, verify via a real local server + real screenshot
  (not just a syntax check), cite exact console log lines as evidence per this project's Log Mandate.

## DONE WHEN
Real glass/window transparency in the Modeller visually matches the Viewer on the same building
(before/after screenshots, real render, no camera bugs), the Outliner collapse control is fixed or
built and demonstrably discoverable/working, both committed locally with clear commit messages citing
this file, and a findings note added here (or a fresh dated section below) reporting exactly what was
found/fixed/still-open — so the NEXT session (possibly the guide-writing session) knows whether the
guide is now unblocked.
