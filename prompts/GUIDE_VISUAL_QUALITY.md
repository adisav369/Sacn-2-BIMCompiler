# ⚠ DO NOT REMOVE — GUIDE_VISUAL_QUALITY.md — ModellerGuide demo-building swap (Duplex → SampleCastle)

SCOPE: `docs/ModellerGuide.md`'s screenshots were all honest/assertion-backed but mostly visually plain
because the underlying demo building, Duplex, is buildingSMART's deliberately minimal massing-level sample
IFC (flat walls, no window/glass geometry). User decision 2026-07-08 (asked directly, not assumed): **swap
every screenshot to SampleCastle** (richer real per-element geometry — dormers, window frames, skylights),
full rework accepted, not the cheaper partial options. Full background: `RESUME_SESSION_2026-07-08.md` §OPEN
item 5b. Read the log after every capture run — exit code is not evidence (Log Mandate).

## §SURVEY — feasibility per screenshot group, grounded via `Explore` before any dispatch

All witness scripts live in `~/bim-ootb/modeller/tests/`. `e2e_harness.js`'s `t.pick()`/`t.clearGround()`/
`t.frameElement()` are dynamic (scan the live scene for real bboxes) — safe to swap under.

| # | Screenshots | Witness script | Risk | Note |
|---|---|---|---|---|
| 0 | `workspace-open.png` | — | ✅ DONE | User's own live capture (Open icon → SampleCastle-ARC), cropped to app-only, commit `bfb443096` |
| 1 | `insert-catalog`, `insert-placed` | `witness_e2e_insert.js` | none | dynamic ground-click point |
| 2 | `sketch-profile`, `sketch-wall` | `witness_e2e_sketch.js` | none | `t.clearGround()` |
| 3 | `sketch-dims-square`, `sketch-dims-angled` | `witness_e2e_sketch_dims.js` | none | `t.clearGround(3.6)` |
| 4 | `sketch-weld` | `witness_e2e_sketch_weld.js` | none | `t.clearGround(5)` |
| 5 | `sketch-circle`, `sketch-circle-extruded` | `witness_e2e_sketch_circle.js` | none | `t.clearGround()` |
| 6 | `route-spine`, `route-run` | `witness_e2e_route.js` | none | `t.clearGround(3)`+`t.frameElement()` |
| 7 | `cut-select`, `cut-open` | `witness_e2e_cut.js` | none | `t.pick({prefer:'wall'})` |
| 8 | `fillet-edges2`, `fillet-rounded` | `witness_e2e_fillet.js` | none | `#b-clear` wipes to synthetic wall anyway — Outliner residue label is the only Duplex-ism, fixes itself |
| 9 | `gizmo`, `move-gizmo`, `scale-stretched`, `rotate-yaw` | `witness_e2e_rotate.js`/`witness_e2e_move.js`/`witness_e2e_scale.js` | none | dynamic `candidates()`/`pick({prefer:'wall'})` |
| 10 | `delete-gone` | `witness_e2e_delete.js` | none | `t.pick()` |
| 11 | `walk-fixtures` | `witness_e2e_walk.js` (standalone) | low | hardcoded `data-key="Duplex"` + `discWalk('ELEC',{building:'Duplex'})` — 2 string swaps; **re-measure the real fixture count for SampleCastle, do not keep "267"**; W1's `<15s` timing budget may need re-tuning for 3225 elements vs Duplex's 265 |
| 12 | `seedtrunk-entry`, `seedtrunk-trunk` | `witness_e2e_seedtrunk.js` | low | same 2 hardcoded `'Duplex'` strings; rest of flow is DOM-generic |
| — | `snap-to-geometry`, `multiselect-marquee` | ad-hoc `?snapgeom=demo`/`?multiselect=demo` self-tests | **N/A** | **NO building ever loaded — pure synthetic scratch geometry (hardcoded wall+doors), independent of Duplex/SampleCastle entirely.** Swapping the "demo building" is a no-op here; true parity would mean redesigning these two demos to run inside a loaded SampleCastle scene, a real feature-script change, not a building-swap. **Not attempted in this lane — flagged as a follow-up, disclosed not silently dropped.** |
| — | `gridstretch-before`, `gridstretch-after` | `witness_e2e_gridstretch.js` | **high / N/A** | Opens Duplex then `#b-clear` wipes it and hand-builds a synthetic `grid.define({xs:[0,4,8],ys:[0,3]})` + one wall — `t.open('Duplex')` is already vestigial, unrelated to which building is "the demo." Same call as above: this is an isolated teaching diagram by original design (arguably CLEARER uncluttered than reproducing the same mechanic buried in a 3225-element castle). **Not swapped — flagged, not forced.** |
| — | `samplecastle-arc-open.png` | already SampleCastle | — | **Caption/narrative tension**: its text ("the largest of the residential-class buildings... before any walk") was written as a deliberate CONTRAST against Duplex being the primary demo. Once everything is SampleCastle, that framing is redundant. Text-only fix, bundle into Batch B's caption pass, not a re-capture. |

## §BATCHES

- **Batch A** (risk: none, ~20 screenshots / groups 1-10 above): swap `t.open('Duplex')` → `t.open('SampleCastle')`
  (confirm the exact resident key string live — grep the Open-catalog for the real key, don't assume it's
  literally `'SampleCastle'`) in each of the ~9 witness scripts, re-run, re-capture, visually confirm each PNG
  against its own caption before deploying (per this session's own Log-not-visual-proof-alone standard —
  numeric/log proof from the witness, PLUS eyes-on the frame). Deploy via `scripts/safe_gh_deploy.sh` only.
- **Batch B** (risk: low, 3 screenshots + caption text): `walk-fixtures`/`seedtrunk-entry`/`seedtrunk-trunk` —
  swap the 2 hardcoded strings in each of 2 scripts, RE-MEASURE the real SampleCastle ELEC fixture count (do
  not reuse "267" — that was Duplex's real count, SampleCastle's will differ), re-tune the walk-timing budget
  if it trips, update `ModellerGuide.md`'s caption text for `walk-fixtures.png` (currently says "the Duplex")
  and `samplecastle-arc-open.png` (redundant contrast framing) to match the new all-SampleCastle narrative.
- **Not in scope this lane** (disclosed, not silently dropped): `snap-to-geometry.png`, `multiselect-marquee.png`,
  `gridstretch-before/after.png` — structurally can't swap without a real demo-script redesign, see §SURVEY.

## §STATUS — update in place as batches land, don't restate history from scratch each session
- 2026-07-08: spec written, Batch A + Batch B dispatched (background `Agent`, `model:"fable"`, shared
  `/tmp/wt-viewer-rpr-port` worktree, fresh branch off `origin/main` per §LESSONS in `RESUME_SESSION_2026-07-08.md`).
- 2026-07-09: fixed the `gridstretch-before/after.png` STYLE mismatch (bim-compiler
  `prompts/RESUME_MODELLER_GUIDE_SCREENSHOT_FIX.md` item 5 — before had zero app chrome, after already had full
  chrome from an earlier orphan-swap) by recapturing BOTH from the same session with full chrome — no building
  swap, per this doc's own call above. **Observation, not chased further (out of this doc's guide-only scope):**
  a first attempt left Duplex OPEN (not `#b-clear`ed) under the synthetic grid+wall so the Outliner would show
  real building context; the gridline drag then committed a stray `GEOM_MOVE` instead of `GEOM_GRID_MOVE` (root
  cause not isolated — could be `#b-gridmove` failing to arm with Duplex loaded, or the drag hitting real Duplex
  mesh instead of the gridline; not reproduced/confirmed against a real user session, only this synthetic
  capture script). Worked around by using the existing tested recipe (`#b-clear` first, per
  `witness_e2e_gridstretch.js`'s own proven X1-X6) rather than debugging app pick/mode logic — that's app-code
  territory, not this guide-only lane. Flagging here in case whoever next touches `bonsai_gridmove.js`/grid-move
  arming wants to chase it; not asserted as a confirmed bug.
