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
