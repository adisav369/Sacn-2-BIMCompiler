<!-- Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com> · SPDX-License-Identifier: MIT -->
# FIND PANEL — PLANT_ROOM false-positive + missing class-gate (2026-07-11, MANAGER-assigned)

```
# ⚠ DO NOT REMOVE
SCOPE: bim-ootb `viewer/navigate_find.js` only — two small, named, bounded fixes surfaced as a
byproduct of `prompts/VIEWER_FIND_PANEL_PARTS_VERIFICATION.md` (verification task, correctly did
NOT fix these — out of that file's scope). Read this whole file, then execute both. Read the log
after every run. PUSH PAUSE LIFTED for this repo as of 2026-07-11 (user: "good enough to push all
so we have no backlog") — commit locally, verify on localhost, THEN push + open a PR (follow this
project's own auto-merge convention: `gh pr merge <n> --auto --squash`), same as bim-ootb PR #735
this session. Migration-script rule stays in tact regardless: this task touches zero DB files —
if you find yourself needing to touch one, stop and re-read CLAUDE.md's DB CHANGES rule first.
```

## Bug 1 — PLANT_KEYWORDS bare substring match (false positives)
`viewer/navigate_find.js` ~line 637: `PLANT_KEYWORDS = ["vent", "duct", "fan", "ahu", "damper",
"chiller", "condens", "fancoil", "pump"]`, consumed by `_partsCond()` as
`LOWER(element_name) LIKE '%vent%'` (bare substring, no word boundary). Confirmed live on Duplex:
4 of 6 "Plant Room" hits are `M_Backflow Preventer_...` — a plumbing valve that matches only
because "Prevent**er**" contains "vent". Only the 2× `Round Duct:Taps:...` rows are genuine.

Fix: word-boundary match instead of bare substring — e.g. split `element_name` on non-alphanumeric
separators (whitespace/colon/underscore, matching how these IFC names are actually delimited —
check a few real names across buildings before picking the exact split, don't guess one pattern)
and match whole tokens, or use a regex with `\b` boundaries if SQLite's `LIKE` can't express it
cleanly (may need `REGEXP` if available, or a post-filter in JS after the `LIKE` pass narrows
candidates — cite whichever mechanism you use). Verify same fix should also apply to
`build/building_parts_taxonomy.js` (bim-compiler) and `modeller/building_parts_outliner.js`
(bim-ootb) — the same `PLANT_KEYWORDS` constant is duplicated verbatim in all three per
`BUILDING_PARTS_TAXONOMY.md`'s own note; fix all three or name why not.

## Bug 2 — live Parts axis has no class-gate
`build/building_parts_taxonomy.js`'s `checklistReport(buildingClass, extracted, taxonomyConfig)`
only reports a part `type` if `taxonomyConfig.building_classes[buildingClass].parts` lists it —
`config/building_taxonomy.yaml`'s `residential` class (SH/DX/SC, WalkerDoctrine §1 LOCKED) has NO
`PLANT_ROOM` entry at all, only `complex` (Terminal/Clinic/Hospital/HHS) does. The live Viewer
Find panel's `_partsCond()`/`_PARTS_GROUPS` in `navigate_find.js` has no equivalent gate — it
always shows a Plant Room group on ANY building if any row matches, residential included.

Fix: gate the live `PLANT_ROOM` group the same way `checklistReport()` does — only show it when
the loaded building's class is `complex`. **Open question, investigate don't guess:** the Viewer
currently has NO `building_class`/`buildingClass` lookup anywhere in `viewer/*.js` (confirmed via
grep, zero hits) — `viewer/real_placement_resolver.js` references `building_classes` as a
concept/config, start there to see if a per-building class is already resolvable client-side (e.g.
from the loaded DB filename, a metadata table, or a small SH/DX/SC/Terminal/Clinic/Hospital/HHS
lookup mirroring `config/building_taxonomy.yaml`). If no clean existing hook exists, propose the
smallest one (e.g. a static filename→class map, same buildings this repo already treats as fixed
per WalkerDoctrine) rather than inventing a general classifier.

## Verify
Real screenshot + `§`-tagged console log, same standard as `VIEWER_FIND_PANEL_PARTS_VERIFICATION.md`
— Duplex (residential) should show NO Plant Room group after the fix; Terminal (complex) should
still show its real MEP-plant hits, word-boundary-filtered (re-confirm its count doesn't silently
drop to 0 — a class-gate bug that also breaks the true-positive case is worse than the false-positive
it fixes).

## DONE WHEN
Both bugs fixed, verified on at least one residential (Duplex) and one complex (Terminal) building,
committed locally, findings appended below, pushed + PR opened per the lifted-pause instructions above.
