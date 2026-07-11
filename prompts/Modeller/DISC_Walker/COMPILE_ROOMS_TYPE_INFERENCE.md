<!-- Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com> · SPDX-License-Identifier: MIT -->
# COMPILE_ROOMS TYPE INFERENCE — guessing room FUNCTION where no IfcSpace ever named it

```
# ⚠ DO NOT REMOVE
SCOPE: build/room_walker.js only (2026-07-11: ROOM_WALKER_JS_PORT.md retired scripts/compile_rooms.py
as the canonical room-injection tool — compile_rooms.py itself still exists in the repo, but only as
an unrelated import for scripts/witness_geomap_tier3.py's baseline scoring, NOT as a target for new
room-compile work; port any change here into room_walker.js, not the Python file). Read
ROOM_INJECTION_HYBRID.md in full first — this task extends the room-COUNT work already done there
(§DOOR-RESCUE, §DOOR-PARTITION) into room-TYPE guessing, a strictly harder, lower-confidence problem.
Read the log of whichever step is IN PROGRESS below before concluding anything about its status — a
"confidence" number is not evidence until it's been checked against real ground truth (§2 Task 0).
ANCHORS: prompts/Modeller/DISC_Walker/ROOM_INJECTION_HYBRID.md (§DOOR-RESCUE/§DOOR-PARTITION, the
compile techniques this extends) · build/room_walker.js (the file to modify) ·
scripts/stamp_space_longnames.py (why SampleHouse/Duplex alone HAVE real types — read this before
assuming any inference technique below is needed for them; it isn't) · Duplex_ARC.db / SampleHouse
_ARC.db `spatial_structure.object_type` (the ONLY real, ground-truth room-type data in this repo —
the calibration source for every heuristic below, not a textbook number).
```

## §0 — Why this exists, and the hard limit it must respect

This session found: SampleHouse (3 rooms) and Duplex (20 rooms) show real room types (Kitchen,
Bathroom 1, Foyer, ...) because a human typed that into the source IFC's `IfcSpace.Name` or
`LongName` when authoring the model — extracted verbatim, never computed
(`scripts/stamp_space_longnames.py`'s own doc-comment: "NON-INVENT: pure guid→LongName copy").
The other 6 buildings (Terminal, SampleCastle, HHS, Clinic, Garage, Hospital) have ZERO real
`IfcSpace` rows to read a type from at all — every room compiled for them this session
(§DOOR-RESCUE/§DOOR-PARTITION) is pure geometry (walls, doors), which can prove a room EXISTS but
carries no information about what it's FOR.

**The hard limit, stated up front so no task below quietly crosses it:** there is no way to
EXTRACT a type that was never authored. Everything in §1 is INFERENCE — a confidence-scored guess
from indirect real-data clues, not equivalent to Duplex/SampleHouse's ground truth, and must never
be presented, tagged, or consumed as if it were. This is not a loophole in the PRIME RULE
(EXTRACT OR COMPILE ONLY, never invent) — it's a new, explicitly-labeled THIRD category alongside
"real" and "synthetic-anonymous": **"synthetic-inferred,"** confidence-scored, never authoritative,
never feeding placement (same guardrail `spacesOf()` already enforces for every compiled room).

## §1 — Candidate inference signals (evidence found this session, none yet built)

Ranked by how directly they're grounded in real, checkable data already in these buildings' ARC.db:

1. **Wall/door NAME keyword mining (strongest signal, already proven adjacent).** HHS's walls
   carry real descriptive names in German: `WC Trennwand 5.0` (WC partition wall), `Basic Wall:MW
   11.5` (masonry, 115mm), `Basic Wall:STB 30.0` (reinforced concrete, 300mm). A room whose
   enclosing/nearby walls match a maintained, multi-language keyword list (WC/Toilet/Bad/Damen/
   Herren/Behinderten, Electrical/Elektro, Plant/Technik, Storage/Lager, Meeting/Besprechung, ...)
   can be labeled with that keyword as an INFERRED type. This is the same shape of technique as
   `§DOOR-NOT-ROOM`'s `liftdeur` name-match, extended from "exclude" to "label." Confidence: HIGH
   when a keyword hits (the wall's own name is real, extracted data) — but coverage will be LOW
   (most walls/doors in these buildings are generic "Basic Wall"/"Türelement," no functional hint).

2. **Door leaf-count/glazing from the door's own name.** HHS's doors encode `1-flg`/`2-flg`
   (single/double leaf) and `Glas` (glazed) in their names. A wide glazed double-leaf door often
   marks a more public space (lobby, meeting room, reception) vs. a narrow single-leaf door (private
   office, WC). Weaker, more speculative signal than #1 — treat as a tie-breaker between candidate
   types, never a sole classifier.

3. **Area/shape calibrated against Duplex's OWN real ground truth — not a textbook number.**
   Duplex already has 20 real, typed rooms with known areas (e.g. `Bathroom 1`/`Bathroom 2` sizes,
   `Bedroom 1`/`Bedroom 2` sizes, `Kitchen`, `Living Room`). Build the size DISTRIBUTION per real
   type from this data (the only real, in-repo, non-invented calibration source) and use it as a
   Bayesian-ish prior for guessing a synthetic room's likely type band from its compiled area alone
   — e.g. "a 3-5 m² compiled room is more consistent with Duplex's real bathrooms (X m²) than its
   real bedrooms (Y m²)." Must be calibrated FROM this repo's real data, never from an external/
   generic "typical room sizes" table — that would reintroduce exactly the fitted-band mistake
   already corrected once this session (`ROOM_INJECTION_HYBRID.md`'s door-rescue "abstract rule"
   pass). Confidence: LOW-MEDIUM, coverage HIGH (every compiled room has an area) — the opposite
   trade-off from #1.

4. **Repetition/position pattern.** A block of near-identical-shaped rooms repeating along a
   corridor (visible already in HHS Level 2's door-partition areas — several ~30 m² cells in a row)
   is architecturally very likely a repeated single-function type (offices, patient rooms, cells) —
   even without knowing WHICH function, flagging "these N rooms are one repeated type" is itself
   useful, checkable information (cluster by shape similarity, not guessed per-room). The single
   largest room per storey is very often an open-plan/hall/ward — a weak, storey-relative signal
   only, never an absolute size rule (a "largest room" on a tiny floor isn't necessarily a hall).

5. **Wall-thickness class.** HHS's own wall names already separate structural (`STB 30.0`, 300mm
   reinforced concrete) from partition (`MW 11.5` 115mm masonry, `WC Trennwand 5.0` 50mm stud) —
   thickness alone (even WITHOUT readable names, on buildings whose walls aren't descriptively
   named) can distinguish a building's primary structural grid from subdivided office cells. Useful
   as a structural-zone signal, not a room-function signal on its own — pair with #1/#3.

**Explicitly rejected this session, don't re-investigate without new evidence:** MEP/sanitary
fixture presence (`IfcFlowTerminal` etc.) as a toilet signal — checked, unusable as a GENERAL
signal because the ARC-only discipline strip (`b93ca13`) already removed this data from 4 of 5
synthetic buildings; only Clinic still has it. If Clinic-specific fixture-based labeling is ever
wanted, scope it as a Clinic-only enhancement, not a shared technique — don't build a general path
that silently no-ops on 4 of 5 buildings.

## §2 — Task list (work top-to-bottom, same WORK-TO-ZERO discipline as every prompts/#.md file)

### Task 0 — Build the calibration/validation harness FIRST, before any inference code
**Status: NOT STARTED.** Every technique in §1 needs a ground-truth check before shipping, same
discipline as `witness_room_hab.js`'s H1/H2 pattern: extract Duplex's 20 real (name, area, guid)
triples as the ONLY known-correct answer key in this repo, and build a witness harness that can
score ANY candidate inference function's precision/recall against it BEFORE that function is
trusted on a real synthetic building. No inference technique below ships without running through
this harness first.

### Task 1 — Wall/door keyword-based type inference (§1 item 1)
**Status: NOT STARTED.** Build the maintained, multi-language keyword list (grow only by review
against real wall/door names actually found in these 8 buildings, same discipline as
`NONHAB_TYPES`/`NON_ROOM_DOOR_NAMES` — never guessed ahead of evidence). Attach as
`inferred_type`+`inferred_confidence` fields (new columns, do not overwrite `object_type` —
that column means "real, extracted" everywhere else in this codebase and must keep meaning that).
Witness: run against HHS/SampleCastle/Clinic/Garage/Hospital, report keyword-hit coverage %
(expect LOW — most walls won't match) and spot-check every hit by hand against the wall's actual
position (does a "WC"-keyword room actually sit where the WC cluster is?).

### Task 2 — Area-band inference calibrated on Duplex's real ground truth (§1 item 3)
**Status: NOT STARTED.** Depends on Task 0's harness. Build the real size distribution from
Duplex's 20 typed rooms, use it as a soft classifier for synthetic rooms' `inferred_type` (multi-
candidate with confidence, e.g. "60% bathroom-band / 40% storage-band," never a single forced
label when the bands overlap). Witness: leave-one-out cross-validation on Duplex's own 20 rooms
first (hide each real room's type, see if the area-band alone would have guessed correctly) —
report the honest accuracy number, however low, before trusting it on synthetic buildings.

### Task 3 — Repetition/shape-clustering signal (§1 item 4)
**Status: NOT STARTED, NOT YET SPECCED.** Needs a shape-similarity metric (bbox aspect ratio +
area, tolerance TBD) and a per-storey clustering step. Lower priority than Tasks 1-2 — this signal
answers "are these N rooms the same type as each other," not "what type," so it's only useful
paired with #1 or #2 assigning the actual label to the cluster.

### Task 4 — Wire `inferred_type`/`inferred_confidence` into the Modeller Outliner + Find Panel
**Status: BLOCKED on `ROOM_INJECTION_HYBRID.md` Task 3 (Modeller "Rooms" outliner category, still
NOT STARTED) and `VIEWER_FIND_PANEL_ROOM_ACCURACY.md` Task 2 (real-vs-synthetic display, still NOT
STARTED).** Do not build display wiring before those land — an inferred-type label needs the SAME
"this is a guess, not ground truth" visual treatment those tasks are already speccing for
synthetic-vs-real; a second, divergent display convention here would fragment the UX.

## §3 — Guardrails (do not re-litigate)

- **`object_type` keeps its existing meaning across this whole codebase: "real, extracted."** Never
  write an inferred guess into it. Use new columns (`inferred_type`, `inferred_confidence`) so
  every existing consumer (`spacesOf()`, `spaceHabitable()`, every witness's tag-purity check)
  stays correct without modification.
- **Never feeds placement.** Same enforcement point as `RM_`/`≈`/`COMPILED` — `spacesOf()`'s
  exclusion already keeps every compiled room out of schedule placement regardless of any inferred
  type; this task must not create a second path that lets an "inferred: Kitchen" room get treated
  as real by anything downstream.
- **Calibrate from THIS repo's real data only (Duplex's 20 rooms), never a generic/textbook room-
  size table.** That mistake was already made and corrected once this session (the door-rescue
  area-band that turned out to be fitted to observed data rather than a principle) — don't reopen
  it here by importing external "typical room area" numbers instead of this repo's own ground truth.
- **Report honest accuracy, including low numbers.** A technique that scores 30% precision on
  Task 0's harness is still worth shipping AS A LOW-CONFIDENCE HINT if labeled honestly — the
  failure mode to avoid is silently rounding a weak signal up to look authoritative.
