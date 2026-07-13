<!-- Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com> · SPDX-License-Identifier: MIT -->
# COMPILE_ROOMS TYPE INFERENCE — guessing room FUNCTION where no IfcSpace ever named it

```
# ⚠ DO NOT REMOVE
EXTRACT-FIRST CHECKLIST (added 2026-07-13, standing — read before adding ANY room-type signal):
this task exists because a session reached for a Gaussian statistical template (inference, n=2-5
samples) before checking whether the answer was already sitting in the extraction as a named,
real element (a passenger-complex building showing "1 toilet" when its own IFC has 4 real
`Asian_Toilet`-named fixtures). That is backwards for THIS repo's PRIME RULE priority order, every
time, not just once: (1) is there a real named element/label/fixture that answers this directly —
EXTRACT it; (2) only if genuinely absent, is there a deterministic geometric derivation — COMPILE
it; (3) only if both are exhausted, a confidence-scored statistical INFERENCE, clearly labeled as
such and never presented as ground truth. Check (1) and (2) FIRST, in that order, before writing
any template/threshold/classifier — don't discover step (1) existed only after a user catches an
implausible inferred result. See `build/room_type_classifier.js` `classifyRoomWithFixtures()` for
the reference implementation of this priority order (fixture EXTRACT beats Gaussian INFER, but
only when the extracted evidence doesn't contradict the room's own measured size — evidence and
inference must AGREE, neither one silently overrides the other).

SCOPE: build/room_walker.js AND scripts/compile_rooms.py (2026-07-11's ROOM_WALKER_JS_PORT.md
retirement note below is STALE — see the 2026-07-13 update further down: the two files are
maintained in lockstep parity via build/witness_room_walker_parity.js, port every change both
ways). Read ROOM_INJECTION_HYBRID.md in full first — this task extends the room-COUNT work already
done there (§DOOR-RESCUE, §DOOR-PARTITION) into room-TYPE guessing, a strictly harder,
lower-confidence problem. Read the log of whichever step is IN PROGRESS below before concluding
anything about its status — a "confidence" number is not evidence until it's been checked against
real ground truth (§2 Task 0).
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

**UPDATE 2026-07-13 — signal #1's family BUILT (furniture variant, not the wall/door-name
variant), with the new evidence this section asked for before revisiting fixture-based signals.**
User pushed back on a Terminal room-type census showing "1 toilet" for a passenger complex —
correctly identified as a RosettaStone-priority violation: a Gaussian statistical template (n=2-5
residential samples) was used before checking for direct extractable evidence, when Terminal's own
extraction has 4 real `Asian_Toilet`-named `IfcBuildingElementProxy` fixtures sitting right there.
**The new evidence that supersedes the rejection above:** that rejection was specifically about
`IfcFlowTerminal` (an MEP-discipline class, stripped to 1/5 buildings). `IfcFurnishingElement`
(Duplex)/`IfcFurniture`+`IfcBuildingElementProxy` (Terminal) are a DIFFERENT, ARC/furniture-side
class family — checked fresh across all 8 buildings, present in EVERY ONE (14-1971 elements each,
never discipline-stripped to zero). Different signal, different availability profile — the
IfcFlowTerminal rejection stands for that specific class, not as a blanket ban on all fixture
signals.

**Built:** `build/room_type_classifier.js` `classifyRoomWithFixtures()` — a room containing a
named fixture matching a keyword list (keywords extracted from REAL element names, not invented:
`vanity`/`toilet`/`urinal`/`bidet` from Duplex's "Vanity Cabinet...Sink Unit" + Terminal's
"Asian_Toilet"; `sink hole`/`hob`/`cooktop`/`stove top` for kitchen) gets that type confirmed —
but ONLY if the Gaussian area/aspect score for that SAME type is still plausible for the room's
actual measured size (symbiotic, not a one-sided override — see room_type_classifier.js's own
`§FIXTURE-SIGNAL` comment for the real bug this caught: a naive first draft mislabeled 2 real
Duplex UTILITY rooms as KITCHEN because both contain a "Counter Top w Sink Hole", i.e. a laundry
sink — "sink hole" alone isn't kitchen-exclusive). **Reverse-engineered from the Stones (Duplex
ground truth), forward-replayed against the same Stones before use** (the methodology this whole
doc's §2 Task 0 already called for): 18/18 real Duplex rooms with fixture data classified
correctly, 0 mismatches, after the symbiotic correction.

**Applied to Terminal, honest result:** the fixture-room correlation mechanism itself works
(123/662 real Terminal fixtures land inside a compiled room — not a coordinate-frame problem). But
the 4 known `Asian_Toilet` fixtures specifically sit OUTSIDE every currently-compiled room (nearest
~4m off) — that restroom area was never captured as a room at all by `compile_rooms.py`/
`room_walker.js`. The fixture signal can only confirm a type for a room that already exists as
compiled geometry; it cannot recover a room that was never compiled. **Named as a separate,
real, NOT-yet-investigated gap** (why didn't flood-fill/door-partition capture that restroom area?
— out of this task's scope, flagging for whoever picks up room-compile-coverage work next).

**Signal #1's ORIGINAL proposal (wall/door NAME keyword mining — "WC Trennwand", "Basic Wall:MW
11.5") is STILL NOT BUILT.** Today's work is the furniture-fixture sibling of the same idea, not a
replacement for it — wall/door name mining remains open, and per this section's own ranking is
still the STRONGEST signal (higher confidence, since walls are near-universal) once built.

**Also worth flagging here (not fixed, doc-accuracy note):** this doc's own header says
`scripts/compile_rooms.py` was "retired... NOT a target for new room-compile work" as of
2026-07-11 (ROOM_WALKER_JS_PORT.md). That appears stale — `build/witness_room_walker_parity.js`
(the current parity harness) explicitly treats `compile_rooms.py` as "the checked ground truth"
that `room_walker.js` must match byte-for-byte, and 2026-07-13's session (`SUSPECT_ELONGATED`,
exterior-leak fix, no-overlap guard — see `ROOM_INTELLIGENCE_SCOREBOARD.md`) edited
`compile_rooms.py` FIRST and ported to `room_walker.js` second, verified via that same parity
harness both ways. The two files are maintained in lockstep parity today, not with Python retired
— whoever next reads this doc's header should not take the 2026-07-11 retirement note at face
value without checking the parity harness first.

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
