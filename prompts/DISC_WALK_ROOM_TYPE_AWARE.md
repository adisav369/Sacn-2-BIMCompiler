<!-- Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com> · SPDX-License-Identifier: MIT -->
# DISC WALKER — room-type-aware discipline placement (2026-07-11, MANAGER-assigned)

```
# ⚠ DO NOT REMOVE
SCOPE: bim-ootb `modeller/disc_walker.js` — the MEP/STR/ELEC/ACMV/FP walker. User directive
2026-07-11: "toil at the Modeller side to DISC Walk on clear rooms, and corridors/ etc" — now that
real room-type classification exists (habitability + size/shape + door-access, `build/room_type_
classifier.js`), make discipline placement ROOM-TYPE-AWARE instead of type-agnostic. Read the log
after every run. PUSH PAUSE IN EFFECT (`CLAUDE.md` §⏸ PUSH PAUSE) — commit locally, verify on
localhost, do NOT push, do NOT open a PR, until told otherwise.
```

## Why, and the non-invent discipline that governs this task
`spacesOf(bdb)` (disc_walker.js ~line 217) enumerates rooms for the walker but is currently
room-TYPE-blind — every space is walked identically regardless of whether it's a bathroom or a
corridor. **This task is NOT "add a hardcoded rule like bathrooms-get-plumbing"** — that would be
inventing convention, exactly what this project's discipline forbids. Instead: MEASURE whether a
real correlation exists between classified room-type and real MEP-element density in the buildings
that actually HAVE real MEP data (Terminal's `terminal_rules.db`, Duplex's MEP extraction —
`build/Duplex_mep_extracted.db` per `W-WALKBACK-MEP`). If a real, measured correlation exists (e.g.
"real bathrooms have N× the PLB element density of real bedrooms, measured not assumed"), use it as
a placement WEIGHT/prior with a citable confidence. If no real correlation is found for a
discipline/type pair, refuse to apply one — leave that pair type-agnostic, same as today, and say so
plainly. This is the SAME discipline as the room-type classifier itself: measured or refused, never
invented.

## Corridors/supplementary spaces — explicit, do not default to "walker skips them"
`ROOM_TYPE_TEMPLATE_CLASSIFIER.md`'s tier split (primary vs supplementary — hallway/foyer/corridor)
is about the UBBL/BOM-recipe COUNT, not about whether the discipline walker should touch the space.
**Do not conflate the two.** In real buildings, corridors are often WHERE fire protection (FP) and
emergency/egress electrical (ELEC) concentrate — check this against real measured data (Terminal's
FP/ELEC walk data) before assuming either way. Report what the data actually shows per discipline ×
tier, don't assume "supplementary = less walked."

## Task
1. Query real MEP-bearing buildings (Terminal, Duplex-MEP) for element density per discipline,
   grouped by the ALREADY-CLASSIFIED room type of the space each element falls within (reuse
   `build/room_type_classifier.js`'s output — don't reclassify, the classifier already exists).
2. Report the measured density table honestly: which discipline×room-type pairs show a real,
   statistically meaningful density difference (name your significance bar and why), which don't.
3. For pairs with a real measured signal: wire it into `spacesOf()`/the walker's placement logic as
   a weight, cited to the measurement. For pairs without: leave as-is, name it as "no signal found,"
   not silently skipped.
4. Explicitly check corridors/hallways/foyers (the supplementary tier) against FP and ELEC density —
   report the real finding, don't assume.

## Witness
Before/after comparison on a real MEP-bearing building (Terminal or Duplex): does room-type-weighted
placement change anything, and is the change backed by the measured correlation from step 1-2 (not
just "it moved")? Regression-check existing disc-walk witnesses (`witness_disc_density.js`,
`witness_walkback_mep.js`) stay green — this is an additive weighting, not a rewrite of the walk
mechanism.

## DONE WHEN
Real discipline×room-type density correlations are measured and reported (found or not-found,
honestly either way), any real signal is wired in as a cited weight, corridors/supplementary spaces
are explicitly checked (not assumed), zero regression on existing disc-walk witnesses.

## RESULT (2026-07-11) — measured, wired, witnessed

### §SUBSTRATE — only ONE real substrate exists in this repo
Checked BOTH candidate buildings before measuring anything:
- **Terminal** (`deploy/buildings/Terminal_extracted.db`): 0 rows in `elements_meta WHERE
  ifc_class='IfcSpace'`, NO `spatial_structure` table at all. It has real per-discipline MEP element
  counts but **zero real per-room space geometry** — cannot contribute a room-type×discipline
  measurement. **Refused as a substrate, not silently skipped** (WalkerDoctrine's own §NOSPACES
  comment already says shipped ARC residents mostly carry no spatial_structure table — this confirms
  it live for Terminal specifically).
- **Duplex** is the ONLY building with BOTH real per-room labels+geometry
  (`deploy/buildings/Duplex_extracted.db` spatial_structure, `object_type` = real Revit LongName:
  Living Room/Kitchen/Bathroom 1-2/Bedroom 1-2/Foyer/Hallway/Utility/Stair) AND real placed MEP
  fixtures (`build/Duplex_mep_extracted.db`, 904 real elements). Every number below is measured on
  Duplex only — n is small (a mirrored twin unit, 2-4 real occurrences per room type) and reported as
  such throughout, never inflated.

### Method (script: `build/measure_disc_room_type_density.js`, log: `logs/measure_disc_room_type_density_2026-07-11T0210.log`)
1. 20 habitable real IfcSpace rows classified by `build/room_type_classifier.js` +
   `config/room_templates.yaml` (area+aspect only) — 18/20 classified with high confidence
   (70.7%-100%), matching their real labels; A105/B105 ("Stair"/"Room") correctly refused
   (`unclassified`, no template covers a stairwell shaft).
2. `elements_meta.discipline='MEP'` in Duplex_mep_extracted.db is a COARSE bucket (904 elements, no
   PLB/ELEC/FP/ACMV split). Fine discipline came from `library/disc_patterns.db`'s
   `ad_element_mep_alias` (element_name LIKE patterns, `source='DX_MINED'` = mined off this exact
   Duplex model, `migration/DV003_element_mep_alias.sql`) → `ad_element_mep.discipline`, folded
   SP→PLB / HVAC→ACMV (same fold `build/project_rule_space_schedule.py` applies for `duplex_rules.db`).
   101/904 elements matched a real alias (83 ELEC, 12 PLB, 6 FP); the other 803 are routing
   fittings/segments/pipe-type rows plus a handful of terminal types DV003 doesn't yet cover
   (Refrigerator/Range/Microwave/Shower Stall/Bath Tub/Roof Drain/valves) — **honestly excluded, not
   guessed into a bucket** (real gap in DV003's alias coverage, flagged, not silently patched here).
3. Spatial join: element center inside a room's bbox (+0.30m pad for wall-mounted fixtures), nearest
   centroid as tiebreak. 99/101 classified elements landed in a real room; 2 unassigned (routing
   pieces between rooms, expected).

### Significance bar (named, per the spec's ask)
Given n=2-4 per room type (mirrored A/B twin), a numeric density comparison alone is not trustworthy.
Bar used: a discipline×type pair counts as a REAL signal only if (a) presence/absence is CONSISTENT
across every real occurrence of that type (no 0-vs-nonzero split within the same type), AND (b) that
pattern actually DISCRIMINATES — differs from at least one other type (universal presence/absence
doesn't count).

### Found
| disc | signal | evidence |
|---|---|---|
| **PLB** | BATHROOM + UTILITY nonzero in **every** real occurrence (BATHROOM 2,3,2,3 across 4 rooms; UTILITY 1,1); **zero** in every occurrence of BEDROOM/FOYER/HALLWAY/KITCHEN/LIVING_ROOM | passes the bar — real, replicated |
| **FP** | FOYER nonzero in both real occurrences (2,2); zero in every occurrence of BATHROOM/KITCHEN/LIVING_ROOM/HALLWAY/UTILITY | passes the bar — real, replicated. **BEDROOM is a borderline miss** (A202:1 B202:1 but A203:0 B203:0 — inconsistent within the same classified type, likely a spatial-join edge case on a room-boundary sprinkler) — reported honestly, NOT counted as a signal |

### Not found (honestly refused, not wired)
| disc | finding |
|---|---|
| **ELEC** | present in EVERY room type, all real occurrences nonzero (BATHROOM 1.63/m² down to LIVING_ROOM 0.18/m²) — a density gradient, not a presence/absence discriminator; with n=2 per type the magnitude differences are not separable from noise. No categorical signal, refused. |
| **ACMV** | **zero** real ACMV elements anywhere in Duplex_mep_extracted.db, in every room type. Real Malaysian residential Duplex uses split-unit A/C (installed post-construction, not IFC-modeled ducted diffusers) — `duplex_rules.db`'s `rule_space_schedule` ACMV/SUPPLY_DIFFUSER rows are NOT grounded in this real building's own MEP data. No signal to measure; refused. |

### §CORRIDOR-CHECK — explicit, not assumed (both directions checked)
- **FP**: supplementary tier (HALLWAY+FOYER) mean = 1.000/room vs primary tier mean = 0.143/room — but
  this is driven ENTIRELY by FOYER (HALLWAY itself = 0). The tier-level number would be misleading
  read alone; the per-type breakdown is what's real.
- **ELEC**: supplementary tier mean = 4.250/room vs primary tier mean = 4.500/room — **no meaningful
  difference**. Refutes both a "corridors get less ELEC" and a "corridors concentrate ELEC" assumption
  on this building.

### Wired (bim-ootb, `feat/disc-walk-room-type-aware`, commit `20ad5c4`)
`modeller/disc_walker.js`'s `_spaceTypeFor(disc, sp)` gets an opt-in geometry-classifier FALLBACK,
gated to `ROOM_TYPE_MEASURED_DISCS = {PLB, FP}` only (ELEC/ACMV excluded per the table above) —
fires ONLY when the space's real label already fails the existing `rule_space_type`/`rule_space_alias`
match, and only after `dwSetRoomTypeConfig()` is called (default off, zero behavior change for every
existing caller). Ported `build/room_type_classifier.js` → `modeller/room_type_classifier.js` +
`config/room_templates.yaml` → `modeller/room_templates.json` (mechanical JSON transcription, cited).
Witness: `modeller/tests/witness_disc_room_type_weight.js`, **7/7 PASS** (off-by-default, fallback
fires for PLB, gated off for ELEC on the same space, label always wins over geometry, FOYER/FP case,
ACMV correctly refused, placeSchedule end-to-end identical with/without the config loaded on an
all-labeled substrate).

### Regression
- `witness_disc_room_type_weight.js` (new): 7/7 PASS.
- bim-compiler `scripts/witness_walkback_mep.js` (untouched file, not on the edited path): 8/8 PASS.
- `modeller/tests/witness_disc_density.js`: 3/8 checks fail (D3/D4/D4b) — **confirmed pre-existing**,
  byte-identical failure signature with `disc_walker.js` reverted to `HEAD` (git stash test). Root
  cause: `realCount()` reads 0 for ELEC/FP/ACMV from the local gitignored `Terminal_ARC.db` fixture
  (stale relative to `Terminal_arcstr_proof.db`), unrelated to this change — not fixed here, flagged
  for whoever owns that fixture pair next.

**PUSH PAUSE was in effect** — bim-ootb commit `20ad5c4` on `feat/disc-walk-room-type-aware` is local
only, not pushed, no PR opened.
