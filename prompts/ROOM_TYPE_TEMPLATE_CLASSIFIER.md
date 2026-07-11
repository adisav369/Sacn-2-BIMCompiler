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

## DONE (2026-07-11, this session — W-ROOM-TYPE)
**Deliverable is bim-compiler-only.** No bim-ootb code changed (Step 4 Outliner wiring is
explicitly out of scope, see below) — nothing to branch/PR there. Committed locally in
bim-compiler only, **not pushed** (coordinator directive mid-task: batch-verify before pushing
more today; push is deferred, not forgotten).

Files:
- `config/room_templates.yaml` — 7 promoted templates + 3 named n=1 exceptions, full non-invent/
  methodology header (std floor, ≥2-occurrence promotion bar, label normalization, why no `prior`
  entries were added).
- `build/room_type_classifier.js` — `normalizeLabel`, `featuresFromRects` (§MULTI-RECT-aware:
  sums area across a rect-set, computes aspect ratio on the group's bounding envelope), `scoreTemplate`
  (independent-Gaussian likelihood + combined quadrature z), `classifyRoom` (softmax posterior +
  z-threshold refuse-to-classify gate), `loadTemplateConfig`. Dual-mode (Node `require` /
  browser `window.RoomTypeClassifier`), same convention as `room_walker.js`/bim-ootb
  `common/room_habitability.js`.
- `build/witness_room_type_classifier.js` — W-ROOM-TYPE, run against all 8 buildings. Log:
  `logs/witness_room_type_classifier_202607110920.log` (3/3 checks PASS, exit 0).

### Corrected bootstrap story (mid-task correction, addressed)
Duplex's `object_type` column carries 20 REAL human-authored room labels (post-habitability-
filter) — a genuine measured anchor, not an external prior. SampleHouse independently carries 3
more real labels. **All 7 promoted templates are `confidence: measured`, fit from these two
buildings' real geometry — N=2 to N=5 per template.** No `confidence: prior` templates were added:
the only in-repo external size numbers (`config/spacetypes.yaml`, `malaysian_residential.yaml`)
are themselves the already-flagged-uncorroborated UBBL landmine values — reusing them here would
be the 4th conflicting number under a new filename, so they were deliberately left out. An
unmeasured room type (e.g. DINING, OFFICE) simply has no template today and will always classify
`(unclassified)` — a documented gap, not silently papered over.

Methodology addition (≥2-occurrence promotion bar) applied as specified: `STAIR`, `ROOM` (Duplex,
n=1 each) and `ENTRANCE_HALL` (SampleHouse, n=1) stayed named exceptions, not templates — 7 of 10
real normalized labels cleared the bar.

### Witness results — confidence distribution, reported honestly
**Training-source (Duplex + SampleHouse, self-fit — NOT independent validation):** 21/23 real
rooms classified, both training buildings' own template-eligible rooms mostly re-classify as their
own label. **One informative miss, reported not hidden:** SampleHouse's real "Bedroom" (15.4 m²,
aspect 1.29) classifies as `LIVING_ROOM` (75.1%) instead of `BEDROOM` — because `BEDROOM`'s
n=5 mean (21.6 m²) is anchored high by Duplex's unusually large 23.2 m² bedroom bounding boxes
(4 of the 5 samples), which are geometrically closer to a typical `LIVING_ROOM` than to
SampleHouse's more ordinary-sized bedroom. This is a genuine, honest limitation of the N=5
bootstrap (BEDROOM/LIVING_ROOM templates aren't yet well-separated at this sample size) — not a
classifier bug; a Gaussian classifier over overlapping small-N distributions is not guaranteed
100% training self-consistency, and forcing it to "pass" here would mean tuning the classifier to
one data point, i.e. inventing a fix. Left as-is, documented, and worth revisiting once the
Step-4 correction flywheel adds real user-confirmed labels.

**Held-out inference (SampleCastle/HHS/Clinic/Garage/Hospital/Terminal — synthetic `COMPILED`
rooms, NO ground truth to validate against):** 602 rooms total. **378/602 (62.8%) came back
`(unclassified)`.** This is the headline number and it is EXPECTED, not a shortfall — these are
algorithmically flood-filled pockets in institutional/industrial buildings (hospital wards,
terminal gates, clinic rooms), not shaped like Duplex's residential mirrored units. Of the 224
that DID classify: confidence distribution `40-60%: 16, 60-80%: 37, 80-100%: 171` — i.e. most
classified rooms classified with HIGH confidence (>80%), meaning the gate is doing its job:
rooms that pass the z-threshold tend to genuinely resemble a template, rooms that don't get
honestly refused rather than force-fit. A LOW unclassified rate here would have been the red
flag (over-eager matching onto a 5-sample residential template), not a win — reported as such.

Per-building: SampleCastle 41.2% classified, HHS 24.8%, Clinic 32.5%, Garage 20.0%, Hospital
42.3%, Terminal 62.8% (Terminal's higher rate is `LIVING_ROOM`-shaped large rectangular gate
areas matching the widest-aspect template by coincidence of shape, not semantic correctness —
flagged, not asserted as a good match).

### Config-editability — verified, not assumed
Witness step: took a held-out SampleCastle room, ran it against the real `config/room_templates.yaml`
(unclassified), then against an **in-memory copy** with every template's std ×50 and the
z-threshold relaxed to 100 — classification flipped to `LIVING_ROOM@41.0%`, proving the classifier
actually reads `config.templates[*].area_m2/aspect_ratio.std` at runtime rather than a hardcoded
band. The real `config/room_templates.yaml` file was never touched by this check.

### Scope discipline — Step 4 NOT started
Modeller Outliner wiring (`bonsai_outliner.js` Rooms category) + the user-correction flywheel that
would log corrections back into `confidence: measured` bands is **NOT STARTED** — named here per
the task's own scope boundary, not folded into this task. Two documented-but-unbuilt follow-up
axes (grid/containment, door-count) are noted in `config/room_templates.yaml`'s trailing comment
block, not built.

### Practical note for whoever builds Step 4
The 8-building `*_ARC.db` copies with a populated `spatial_structure` table today only exist
together on the unmerged bim-ootb worktree `/tmp/wt-fable-livewire` (branch
`fable/modeller-lod400-livewire`) — `main`/every other checkout sampled only has Duplex+Terminal
populated. This witness pointed at that worktree read-only; it was not modified.
