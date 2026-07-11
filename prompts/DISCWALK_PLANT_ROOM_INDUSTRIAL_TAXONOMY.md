<!-- Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com> · SPDX-License-Identifier: MIT -->
# DISCWALK + PLANT ROOM — industrial/institutional taxonomy expansion (2026-07-11, Fable-scoped)

```
# ⚠ DO NOT REMOVE
SCOPE: bim-compiler (build/building_parts_taxonomy.js, config/building_taxonomy.yaml) + bim-ootb
(disc_walker.js, viewer/navigate_find.js, modeller/building_parts_outliner.js) — this is the next
step on MANAGER.md's own named mission ("Flag on the Hill": a complete building-part index —
stairways, air wells, ventilation shafts, lift shafts, plant rooms — so Find panel/Outliner/DISC
Walker all work off the same full taxonomy). User's framing, 2026-07-11: "DiscWalk coming in
stronger" + "Plant Room... industrial taxonomy is good ready territory we have before us." Read
this whole file, then execute. Read the log after every run. PUSH PAUSE LIFTED for this repo as of
2026-07-11 (user: "good enough to push all so we have no backlog") — commit locally, verify on
localhost, THEN push + open a PR with auto-merge, same convention as every other PR this session.
Migration-script rule stays in tact: if this work touches any DB CONTENT (not code), it ships as a
small SQL script + self-heal loader per CLAUDE.md's DB CHANGES rule — never a binary commit.
```

## Where this picks up from (don't re-derive, verify then build on it)
- **Plant Room ground truth just grew from n=1 to n=2 real buildings**, confirmed live this
  session: Terminal (74 real MEP-plant matches) and **HHS Office** (1769 matches — confirmed NOT a
  false-positive artifact: `SELECT ifc_class, COUNT(*) FROM elements_meta WHERE LOWER(element_name)
  LIKE '%vent%' OR '%duct%' OR '%fan%' OR '%ahu%' OR '%chiller%' OR '%pump%' GROUP BY ifc_class` →
  933 `IfcFlowFitting` + 834 `IfcFlowSegment`, real duct/pipe network classes, not incidental name
  hits). `config/building_taxonomy.yaml`'s `complex` class already lists both as PLANT_ROOM-eligible
  — HHS just needs to be ADDED to that class's `buildings:` evidence list (it's already gated
  correctly, just not documented as a second real example).
- **The word-boundary + class-gate fix is merged** (bim-ootb PR #740, #742) — `PLANT_KEYWORDS`
  false positives (e.g. "Backflow Preventer" matching "vent") are fixed in all 3 copies
  (`viewer/navigate_find.js`, `modeller/building_parts_outliner.js`, `build/building_parts_taxonomy.js`).
  Don't re-fix this, verify it holds under the new work below.
- **STAIRWAY/LIFT_SHAFT are done** (5 and 4 buildings respectively, real ground truth) — not this
  file's concern, don't touch.
- **STORAGE and AIR_LIGHT_WELL are still zero-evidence** (`config/building_taxonomy.yaml`'s own
  comment: "recon found zero real evidence for either in any of the 8 shipped buildings"). This
  file's job is to re-open that search now that HHS just proved institutional buildings ARE where
  the evidence lives (Plant Room's own lesson) — Clinic and Hospital have NOT been checked yet this
  session for either category.

## Terminal is the anchor, not just one of two data points
Per `docs/internal/WalkerDoctrine.md` (LOCKED core doc): **Terminal is the LOD400 reference** —
the most measured, most trustworthy data source in this project, not an equal peer to HHS/Clinic/
Hospital's compiled/inferred data. User's own framing, 2026-07-11: "the plant is to aim at the good
Terminal data... that model is from a real professional project here, not from other public IFC
sources" — Terminal's real-project provenance (vs. HHS/Clinic/Hospital's public/sample-sourced
models) is WHY it outranks them as ground truth, not just a stylistic preference. Concretely for
this task: Terminal's 74 real Plant Room matches are the CALIBRATION
target — any new signature, threshold, or correlation built in Task 1/2 below should be validated
against Terminal FIRST (does it reproduce Terminal's known-good 74, without over/under-counting)
before being trusted on HHS/Clinic/Hospital's less-certain data. If a new geometric signature (air
well) or a correlation (Task 2's ACMV proximity) doesn't hold up on Terminal, do not trust it on the
other buildings just because it "looks right" there — Terminal is the falsifier, same discipline as
`WALKER_GUARDS_ROSETTASTONE_SPEC.md`'s "Oracle CALIBRATES, guards GENERALISE."

## Task 1 — survey Clinic + Hospital for Plant Room, Air Well, and Storage evidence
Real DBs exist at `~/bim-ootb/buildings/Clinic_extracted.db` and `~/bim-ootb/buildings/Hospital_extracted.db`
(also `Hospital_3_extracted.db` — check if it's a variant worth including or a stale duplicate,
don't assume). For each:
1. Run the same PLANT_KEYWORDS query as above — real duct/pipe/plant-equipment classes, not just a
   raw keyword count (class-check every hit like the HHS query above did, don't trust `LIKE` alone).
   Add real positive counts to `config/building_taxonomy.yaml`'s `complex.PLANT_ROOM.buildings` list.
2. **Air well / light well — genuinely investigate, don't assume absent.** No keyword list exists
   for this yet (unlike stair/lift/plant). Start from the IFC side: `IfcOpeningElement`/`IfcVoid`-
   adjacent classes, or a `spatial_structure` `IfcSpace` with no roof/floor closure and a tall
   z-extent relative to footprint (an air well is architecturally a vertical void, not a named
   element most IFC authors label explicitly — this may need a GEOMETRIC signature, not a keyword
   match, unlike the other 3 categories). If real evidence is found, name the actual query/geometry
   signature that found it (measured, not invented) and propose it as a new `_partsCond()`
   type/`PLANT_KEYWORDS`-style constant. If genuinely zero evidence after a real search, report that
   honestly and leave `config/building_taxonomy.yaml` unchanged for this category — an honest
   refusal is correct per this project's non-invent discipline, don't force a category to exist.
3. **Storage** — same discipline: check `IfcSpace`/room-classifier data for a "Storage"-labeled or
   -shaped room type first (this may already partially exist in `config/room_templates.yaml` —
   check before building a parallel structure), and check for `IfcFurnishingElement`/shelving-class
   elements as a secondary signal. Same honest-refusal rule if nothing real is found.

## Task 2 — DiscWalk "coming in stronger": test the real ACMV/Plant-Room correlation
MANAGER.md's own reasoning for why this matters: DISC-walk's signal coverage today is thin (2
disciplines — PLB/FP, 1 building — Duplex) specifically because the taxonomy it walks against was
incomplete. Now that Plant Room has 2 real buildings (Terminal, HHS) with real MEP-plant element
data, this is the first real chance to test whether disc_walker's ACMV placement genuinely
correlates with measured proximity to Plant Room elements — a real, testable hypothesis, not
speculation:
1. On HHS and Terminal, measure real spatial proximity between disc_walker's placed/measured ACMV
   fixtures and the real Plant Room element clusters found in Task 1 (reuse the density-clustering
   approach `build/building_parts_taxonomy.js`'s `plantRoomDensity` already uses for the
   room-containment signal — don't build a second clustering method).
2. Report the correlation honestly — strong/weak/none, with the actual measured numbers. If it's
   real, this becomes a new placement-weighting signal disc_walker can use (ACMV should place nearer
   real Plant Room clusters, same category of signal as the existing PLB→Bathroom/FP→Foyer wins from
   `DISC_WALK_ROOM_TYPE_AWARE.md`). If it's weak/absent, report that too — a negative result here is
   real information, not a failure (same standard as this project's other honest-negative findings,
   e.g. door-access signal scoring 4/10 in `ROOM_INTELLIGENCE_SCOREBOARD.md`).
3. Do NOT modify disc_walker's placement logic itself in this pass unless the correlation is
   unambiguous and strong — a weak/marginal signal should be reported and left for a follow-up
   decision, not force-fitted into a weighting change.

## Explicitly OUT of scope
- STAIRWAY/LIFT_SHAFT — done, don't touch.
- Any UI/Find-panel/Outliner rendering work — this is a data/taxonomy investigation task, not a UI
  task. If the taxonomy grows (new buildings added to Plant Room's evidence list, or a new category
  built for air wells), the UI already reads `config/building_taxonomy.yaml` dynamically — no UI
  code change needed unless a genuinely new axis type is added (unlikely for this pass).
- Room-type classifier changes (`build/room_type_classifier.js`) — separate lane, don't touch.

## DONE WHEN
Task 1: Clinic + Hospital surveyed for Plant Room (real evidence added to the yaml if found),
Air Well, and Storage (both either given a real, measured signature + config entry, or an honest
"zero evidence found" report) — findings appended to this file. Task 2: the ACMV/Plant-Room
correlation measured on HHS + Terminal and reported honestly, whether the result is strong, weak,
or absent. Update `ROOM_INTELLIGENCE_SCOREBOARD.md` row 15 (and add a DiscWalk row/note if the
correlation is real) once both tasks report — same standard-reporting-format discipline as every
other thread this session, don't revert to prose.
