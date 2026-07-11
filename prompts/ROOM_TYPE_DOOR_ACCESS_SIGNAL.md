<!-- Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com> · SPDX-License-Identifier: MIT -->
# ROOM TYPE — door-access signal (EASIER, Sonnet) — 2026-07-11, strategy session

```
# ⚠ DO NOT REMOVE
SCOPE: bim-compiler `build/room_type_classifier.js` (shipped 2026-07-11, `ROOM_TYPE_TEMPLATE_
CLASSIFIER.md`) — add door-count/adjacency as a SECOND discriminating signal alongside the existing
size+aspect-ratio Gaussian score. Named as a follow-up in that doc, not built. Well-scoped, data
already computed elsewhere in the pipeline — this is the deliberately easier of two parallel tasks
today. Read the log after every run. PUSH PAUSE IN EFFECT (`CLAUDE.md` §⏸ PUSH PAUSE) — commit
locally, verify on localhost, do NOT push, do NOT open a PR, until told otherwise.
```

## Why this signal (read `ROOM_TYPE_TEMPLATE_CLASSIFIER.md` first, full context)
Size+aspect-ratio alone can't distinguish rooms of similar shape — a small square bathroom and a
small square utility room can be nearly the same size. Door count/adjacency is a real, cheap,
already-computed discriminator: a hallway typically has ≥2 doors (it connects spaces), a bedroom
typically has exactly 1, a bathroom typically has exactly 1 and is adjacent to a bedroom or hallway.

## §1 — Find the existing data (don't recompute from scratch)
`scripts/compile_rooms.py`'s door-rescue/door-partition logic (§DOOR-RESCUE/§DOOR-PARTITION,
referenced throughout `ROOM_INJECTION_HYBRID.md`) already computes door-adjacency per compiled
room — a room's ELIGIBILITY to be compiled at all already implicitly requires door access. Find
the actual table/column this lives in (`rel_contained_in_space`? a door-count already stored
somewhere? check the compiled `spatial_structure`/related tables directly, don't assume a schema —
verify by querying). If door-count per room genuinely isn't stored anywhere yet, computing it is
in scope (real IfcDoor/IfcOpeningElement adjacency to a room's footprint, not invented) — but check
first, this may already exist.

## §2 — Add as a second signal, don't replace the first
`build/room_type_classifier.js`'s existing Gaussian scorer (area + aspect-ratio) stays as the
primary signal. Add door-count as either:
- A secondary filter (e.g., a candidate HALLWAY classification requires ≥2 doors to stay classified,
  else drops to unclassified), or
- A second dimension in the confidence score (blend size-confidence with a door-count-likelihood),
  whichever is the smaller, cleaner change to the existing classifier — your call, state which you
  picked and why in the write-up.
**Same non-invent discipline as the parent task:** door-count-per-type "typical" ranges must be
MEASURED from Duplex/SampleHouse's real labeled rooms (the same 2-building ground truth the parent
classifier already established), not guessed or imported from an external source.

## §3 — Witness
Prove door-count actually discriminates something size/aspect-ratio alone gets wrong or leaves
unclassified — find (or construct from real data) a genuine case where adding the door signal
changes a classification for the better, and show it. If no such case exists in the 8 shipped
buildings' data, report that honestly too — a signal that doesn't move the needle yet on real data
is still worth having built (future buildings may benefit), but don't oversell a null result as a win.

## §4 — Non-goals
- Do not touch the grid/containment signal (separate, larger, not this task).
- Do not touch the size/aspect-ratio Gaussian scorer's existing math, only add alongside it.
- Do not rebuild `compile_rooms.py`'s door-rescue logic — reuse its output.

## DONE WHEN
Door-count/adjacency signal exists in the classifier, sourced from real measured data (not
imported), witnessed against real building data with an honest report of whether it changed any
real classification for the better.

## DONE (2026-07-11, this session — W-ROOM-TYPE-DOOR)
**Deliverable is bim-compiler-only.** Committed locally, **not pushed** (PUSH PAUSE in effect,
per this task's own scope line + `CLAUDE.md`).

### §1 — found the existing data, confirmed it does NOT cover the ground-truth buildings
`scripts/compile_rooms.py` computes door adjacency (`_door_adjacent()`, a boolean) for
§DOOR-RESCUE/§DOOR-PARTITION eligibility, but **only for `COMPILED` synthetic rooms** in the 6
institutional/industrial buildings — it is never run over Duplex/SampleHouse (their rooms are
real IFC `IfcSpace` rows, never fed through `compile_rooms.py` at all), and no door-COUNT column
is stored anywhere for any building (`predefined_type` only carries `door_rescued`/
`door_partitioned` booleans). So the parent classifier's own real-label anchor (Duplex +
SampleHouse) had ZERO existing door-count data to fit from — computing it was genuinely in scope,
not a re-derivation. Reused `_door_adjacent()`'s exact buffer formula (`buf = max(door.bbox_x,
door.bbox_y)/2 + DOOR_BUFFER_SLACK(0.20m)`), extended from boolean to a COUNT, applied per-storey
(mirrors `storey_doors()`'s grouping) — does not touch/rebuild the door-rescue/partition decision
logic itself (§4 non-goal respected).

### §2 — integration choice: 3rd quadrature dimension (Option "second dimension in the score")
Picked over a hard filter (the spec's other menu option) because `scoreTemplate()`/`classifyRoom()`
already do independent-Gaussian quadrature over 2 features (area, aspect) — adding a 3rd
(`doorCount`) that only activates when BOTH the template has a `door_count` band AND the caller
supplies `features.doorCount` is a small, symmetric extension, not a new code path. **Confirmed
backward-compatible:** every existing caller that doesn't pass `doorCount` gets byte-identical
2D-only scores (spot-checked against the parent task's own DONE section — SampleHouse's real
Bedroom still scores `LIVING_ROOM@75.1%` with no door data, exactly as originally reported).
Files: `build/room_type_classifier.js` (`countAdjacentDoors()`, `scoreTemplate()` extended,
`DOOR_BUFFER_SLACK` exported), `build/measure_door_counts.js` (the measurement script — run it to
reproduce; log: `logs/measure_door_counts_2026071109XX.log`), `config/room_templates.yaml` (all 7
promoted templates now carry a measured `door_count: {mean, std, n}` band, header extended with
the methodology + 2 honest findings baked in at measurement time — see below).

### Measured `door_count` per type (Duplex + SampleHouse, n=2..5, same promotion bar as area/aspect)
BEDROOM=1.0 (n=5, std=0) · KITCHEN=0.0 (n=2, std=0) · LIVING_ROOM=1.33 (n=3) · BATHROOM=2.0 (n=4,
std=1.0, noisy: Level-1 baths=1 door, Level-2 baths=3) · FOYER=3.0 (n=2, std=0) · HALLWAY=4.0
(n=2, std=0) · UTILITY=3.0 (n=2, std=0 — flagged in the config as the LEAST trustworthy band: a
~1.4m² closet picking up 3 doors is almost certainly `DOOR_BUFFER_SLACK` over-reaching relative to
the room's own tiny size, not 3 genuine doors to a utility closet — reported as-measured anyway,
not hand-tuned away). **KITCHEN=0 is a genuine, surprising, real finding**: Duplex's kitchen is
open-plan off the Foyer with no door leaf modeled at all — the source IFC really has zero doors
there. An assumed "kitchens have ≥1 door" prior would have been invented AND wrong on this real
building; this is exactly why the task's non-invent discipline exists.

### §3 — witness: honest result, genuinely mixed, NOT oversold either direction
Extended `build/witness_room_type_classifier.js` to compute a real per-room door count (storey-
scoped) for **all 8 buildings** and classify each room BOTH ways (area+aspect-only vs
+door_count), logging every case where the two disagree. Log:
`logs/witness_room_type_classifier_door_access_202607110936.log`, 4/4 mechanism checks PASS
(exit 0; a new `F2` synthetic-probe check proves `doorCount` is actually consumed by
`scoreTemplate()`, not dead code).

**Three genuine, concrete wins — the door signal fixing something area+aspect got wrong, found on
real data, not constructed:**
1. `Clinic` a `10.00 m², aspect 2.50, doors=0` COMPILED room: was `HALLWAY@73.8%` (plausible by
   shape alone), correctly reclassifies to `KITCHEN@100.0%` once door_count is added — a hallway
   with **zero** doors is architecturally implausible (HALLWAY's own measured mean is 4 doors);
   KITCHEN's measured 0-door signature is an exact match.
2. `Garage` a `11.60 m², aspect 2.90, doors=0` COMPILED room: same fix, `HALLWAY@52.8%` ->
   `KITCHEN@100.0%`.
3. `SampleHouse`'s real **"3 - Entrance hall"** (a genuine labeled room, doors=2): was falsely
   matching `HALLWAY@83.4%` by size/aspect coincidence alone; door_count correctly refuses that
   match (`(unclassified)`) — Entrance Hall and Hallway are deliberately kept as SEPARATE real
   labels in `config/room_templates.yaml`'s own methodology (different word, no invented synonym
   merge), so this is the door axis correctly preventing a real false-positive that pure
   size/aspect was getting wrong.

**The honest cost, reported plainly per this task's own instruction not to oversell:** blending
door_count in EVERY time (as the witness does, to stress-test it) is a **net regression** at
today's fitting scale. Held-out unclassified rate jumps from the parent classifier's 62.8%
baseline to **84.2%** (507/602) — 133/625 total classifications flip, and the large majority of
those flips are classified -> unclassified, not a type correction. Root cause: several door_count
bands are `std=0` (fit from only 2 identical Duplex mirror-pair rooms), and `std_floor_fraction`
(0.15, inherited from the area/aspect fit) floors to a still-tight band — e.g. HALLWAY's
`std_floor = 0.15*4 = 0.6`, so a real building's hallway with 6 doors instead of Duplex's 4 already
scores `z=3.3`, well past the `unclassified_z_threshold=2.0` combined score. Duplex's specific
door-per-room-type ratios are one house's idiosyncratic floor plan, and — measured here for the
first time — they generalize to other buildings' door layouts WORSE than area/aspect did, not
better. **This also cost one real self-consistency win**: SampleHouse's real "1 - Living room" was
a clean `LIVING_ROOM@100%` self-match on area+aspect alone; adding its door_count=2 (vs the
fitted LIVING_ROOM mean of 1.33, mostly from Duplex's 1-door living rooms) pushes it to
`(unclassified)` — a real, named regression, not hidden.

### Recommendation (named, not built — stays in scope discipline of "smaller, cleaner change")
Do NOT wire door_count as an always-on default for held-out inference at today's n=2..5 sample
size — the 3 concrete wins above are real, but blending unconditionally currently loses more than
it gains system-wide. `classifyRoom()` already defaults to NOT using door_count unless a caller
explicitly supplies `features.doorCount` (verified backward-compatible above), so no existing
caller is affected by this task. A future session with either (a) door_count measured across more
real buildings (widening the currently-degenerate std bands honestly, not by inventing a bigger
floor fraction), or (b) a door_count-specific floor fraction fitted from more data, or (c) gating
door_count to only break ties when area+aspect confidence is already borderline — would be the
right next step; none of those are built here (would need real data this task doesn't have, and
inventing a fix here would repeat the exact landmine this project's discipline forbids).
