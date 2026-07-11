<!-- Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com> · SPDX-License-Identifier: MIT -->
# ROOM TYPE TEMPLATE CLASSIFIER (2026-07-11, MANAGER-assigned, strategy session)

```
# ⚠ DO NOT REMOVE
SCOPE: plug the room-TYPE classification gap named in `UBBL_RULES_GATE.md` §1c ("room-order/type
classification — BLOCKED, no classifier exists that assigns order/type to a raw IfcSpace"). Build a
size+shape TEMPLATE classifier (bedroom/living/kitchen/bathroom/corridor/…) over already-compiled
room geometry — a probabilistic classifier over measured features (area, aspect ratio, perimeter-
to-area "uniformity"), NOT a hard lookup table, NOT invented per-building custom logic. Read the log
after every run.
```

## Why this, why now
Every shipped building's room `name` column is a generic plan code (`A102`, `R1`, …). **CORRECTED
2026-07-11 (checked `object_type`, not `name`):** `Duplex_extracted.db`/`Duplex_ARC.db` DOES carry
real human-authored labels there — `Living Room`, `Kitchen`, `Bathroom 1/2`, `Bedroom 1/2`, `Foyer`,
`Hallway`, `Utility`, `Stair`, `Roof` (21 rows, straight from the source IFC's `LongName`). This is
a genuine RosettaStone-quality reference — use it as the PRIMARY training/validation anchor, not an
external prior. Every OTHER building's rooms are either `COMPILED` (synthetic, no real label —
Terminal 43/43) or have no `spatial_structure` table in these DB copies at all (SampleHouse/
SampleCastle/Clinic/Hospital/Garage — checked, table missing). So: fit from Duplex's real N=21
sample (small — say so plainly), apply as inference-only elsewhere, report confidence honestly.
This still blocks: UBBL By-Law 42's order-based tiers, Task 3 of `VIEWER_FIND_PANEL_ROOM_ACCURACY.md`
(plain-language labels), any FM/space-analytics use of the compiled room data — for every building
that isn't Duplex.

## The landmine to learn from, not repeat
`config/profiles/malaysian_residential.yaml` already has a `BEDROOM.min_area: 9.0 # UBBL
requirement` — and `UBBL_RULES_RECON.md`/`UBBL_RULES_GATE.md` already proved this number is
**uncorroborated** (3 different "UBBL bedroom area" values exist in this repo: 6.5 / 9.0 / 9.3 m²,
none independently verified as THE UBBL number until the recon's own By-Law 42 citation). **Do not
add a 4th hand-typed number.** Every default this task ships must either (a) be measured from this
project's own already-witnessed room geometry, or (b) if borrowed from an external standard, cite
the specific source and be marked with an explicit confidence/provenance flag distinguishing it from
a measured value — same discipline as `UBBL_RULES_GATE.md` §1b.

## Design (probabilistic, config-editable — same shape as `config/profiles/*.yaml`)
1. **Bootstrap the template.** No in-house ground-truth room-type labels exist yet (checked). Ship
   a `config/room_templates.yaml` (same convention-default-with-citation shape as
   `malaysian_residential.yaml`) seeding each room type with a size range + aspect-ratio range +
   an explicit `source:` field (external standard citation OR "measured, buildings: [...]" once real
   data exists) and a `confidence: prior|measured` tag — never silently blur the two.
2. **Classifier, not lookup table.** For a candidate compiled room (already-measured `size_x/size_y/
   size_z` from `spatial_structure`), compute area + aspect ratio + a uniformity score (e.g.
   perimeter²/area, or fill-ratio from §8 MULTI-RECT if multi-rect). Score against each template as
   a likelihood (Gaussian or simple range-membership with soft edges, not a hard cutoff), return the
   best match WITH a confidence number. Low-confidence rooms stay `(unclassified)` — never forced
   into a category, matching this project's refuse-to-guess discipline.
3. **Human/config-editable, defaults-by-convention — same pattern already accepted for DISC walk
   config** (user's own framing, 2026-07-11: "we default according to convention but can edit the
   config setting"). `config/room_templates.yaml` is the override surface — a user or a future
   session can hand-correct a template's range without touching code.
4. **The flywheel (real differentiator, per the strategy discussion this task came out of):** once
   wired into the Modeller Outliner (Rooms category, `bonsai_outliner.js` — see VISION-LOCK sentence
   5), any user correction of a room's classified type is a REAL measured label. Log it (signed op,
   same `kernel_ops` convention as every other edit) and feed it back to refine the template's
   `confidence: measured` band over time — this is what turns an external weak prior into something
   actually calibrated on real buildings, without ever inventing a number.

## Methodology addition (2026-07-11, strategy session — required, small)
**Require repeat-confirmation before trusting a fitted type cluster.** A size/aspect-ratio signature
seen only ONCE is weak evidence — Duplex's own data already demonstrates the right bar: it's a
mirrored twin unit (A-side/B-side), so every real room type in it already appears exactly twice
(`Living Room|2`, `Kitchen|2`, `Bathroom 1|2`, `Bedroom 1|2`, etc. — verified by direct query). Fit
a template only from types with ≥2 real occurrences; a singleton stays a named exception, not a
promoted template. This is a fitting-methodology change, small, do incorporate now.

**Documented follow-up, NOT in scope for this task — 4 signal axes total, only 2 built now:**
1. **grid/containment** (which walls bound the space, grid-cell alignment) — ties to SEMI-GRID.
2. **door access** (door count + adjacency — a hallway typically has 2+ doors, a bedroom exactly
   1) — likely cheap to add later since door-rescue/door-partition (`compile_rooms.py`) already
   computes door-adjacency per compiled room; a room's ELIGIBILITY to be compiled at all already
   implicitly requires door access, but door-COUNT as a room-TYPE discriminator is new, unbuilt.
Both are real, good next iterations — size/aspect-ratio (this task) + habitability (already shipped,
`common/room_habitability.js`) are the 2 axes actually built now. Note both follow-ups in the
write-up, don't build them here.

## Scope for THIS task (don't over-build)
Build steps 1-2 (template config + classifier function + witness) as the deliverable. Step 3
(config-editability) falls out of step 1's format for free — verify it's actually editable
(change a range, re-run, confirm the classifier respects it), don't just assert it. **Step 4 (the
Outliner wiring + correction flywheel) is a separate, larger follow-up — name it as NOT STARTED in
your write-up, don't fold it into this task's scope.**

## Witness
Run the classifier over all 8 shipped buildings' already-compiled, already-habitability-filtered
rooms (`common/room_habitability.js` output). Report the confidence distribution honestly — if most
rooms come back low-confidence/unclassified because the seed template is a weak external prior with
no real anchor yet, SAY THAT PLAINLY. A witness that reports "works great" on a template with zero
measured grounding would be exactly the kind of unearned confidence this project's discipline exists
to prevent.

## DONE WHEN
`config/room_templates.yaml` exists (sourced, confidence-tagged, no invented numbers), a classifier
function exists and is witnessed against real compiled room data from all 8 buildings, the
confidence distribution is reported honestly, and config-editability is verified not assumed.
