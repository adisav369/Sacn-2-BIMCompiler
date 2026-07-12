<!-- Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com> · SPDX-License-Identifier: MIT -->
# ROOM TAXONOMY — strategy/formula only, no re-verification, no implementation (2026-07-12)

```
# ⚠ DO NOT REMOVE
SCOPE: this is a STRATEGY task, not an investigation or a build task. Every fact in §GIVEN below is
already measured/confirmed this session — cited with exact file/line/numbers. Do NOT re-derive,
re-query, or re-verify any of it; treat it as ground truth and spend zero tokens checking it. Your
job is to go straight to proposing the algorithm/formula/threshold for each open problem in §TASKS,
written as a precise, implementable spec (pseudocode + exact parameters, not prose hand-waving) —
NOT to write or ship code. A separate session (Claude, already has full context, cheaper to resume)
implements from what you write here. Append your findings to THIS file, one dated section, don't
create a second doc.
```

## §GIVEN — established facts, do not re-derive

**F1 — the stair-exclusion precedent already exists and works.**
`scripts/compile_rooms.py` line ~25-29 and `build/room_walker.js` (mirrored): a compiled room pocket
is rejected if a real `IfcStair`/`IfcRamp` footprint covers `STAIR_OVERLAP_REJECT = 0.35` (≥35%) of
its area. Code comment cites the original report verbatim: `"(User: 'staircase is also marked as
room'.)"`. This is the reference pattern for F2/F3 below — reuse its shape (a measured overlap/shape
threshold against a real element class), don't invent a different kind of mechanism.

**F2 — real, live HHS room-graph numbers (105 real compiled rooms, 133 real doors):**
```
§ROOM_GRAPH nodes=105 doors=133 nonRoomDoors=0 edges=64 deadend=52 orphan=17 ambiguous=20
```
64 edges from 105 nodes = sparse (avg degree ~1.2). 52 deadend + 17 orphan = 69/105 rooms (66%) have
0-1 door connections. 20 doors were ambiguous (3-4 candidate rooms each), resolved by
`common/room_graph.js`'s current rule: rank by point-to-AABB distance, keep the 2 closest. Sample
ambiguous cases, all real, from the live log (door name, candidate count, which 2 were picked):
```
"Drehflügel 1-flg - Stahlzarge:88.5 x 2.26:...:573577" candidates=3 picked=RM_Level_1_4,RM_Level_1_3
"Drehflügel 1-flg - Stahlzarge:88.5 x 2.26:...:573758" candidates=4 picked=RM_Level_2_7,RM_Level_2_27
```

**F3 — a second real building corroborates room fragmentation is systemic, not a one-off.**
JKR building (`JKR_Project.db`, compiled this session from `jkr_fixed.db`): health check
`walls=509 doors=65 wall/door=7.83 STR%=11.2 storeys=20 stairs=8` — "architectural data looks
sufficient" (source data is NOT the problem). Compiled 66 rooms, but **45/66 (68%) are `SUSPECT_*`**
(14 `SUSPECT_NO_DOOR`, 31 `SUSPECT_OPEN`) — the pipeline's own review-candidate flag, already firing
correctly and honestly, just at a high rate. Storey `'01 Aras Satu'`: 147 walls, 33 doors, flood-fill
only matched 4/33 doors, fell through to door-partition, producing 31 rooms with areas as small as
2m² sitting next to an 84m² room — visually and functionally this reads as "hallway split into
pieces," matching the HHS/Terminal reports directly.

**F4 — `deploy/buildings/Terminal_extracted.db` (the file the Viewer actually serves for Terminal)
has NO `spatial_structure` table at all** — confirmed by direct query, zero room rows, not even a
fragmented one. Whatever "stair marked as room" the user saw in Terminal did NOT come from this
specific file. Other Terminal DB variants exist and are unchecked: `Terminal_meta.db`,
`Terminal_library.db`, plus a Modeller-side substrate. This ONE sub-question (which source produced
what the user saw) is the one piece of F1-F4 that is genuinely still open, not yet traced — see Task 0.

**F5 — room-type template coverage, exact state:**
- Measured and working: `HALLWAY`→canonical `CORRIDOR` (n=2, Duplex), `FOYER`→canonical `LOBBY`
  (n=2, Duplex), both `tier: supplementary` in `config/room_templates.yaml`.
- Schema key exists, zero measured template: `VERANDAH` (nearest concept to "balcony"),
  `ASSEMBLY_HALL` (nearest concept to "hall") — both in `config/spacetypes.yaml`, neither has a
  `room_templates.yaml` entry, so the classifier cannot actually assign either from real data today.
- `ENTRANCE_HALL`: n=1 exception (SampleHouse), explicitly below the n≥2 promotion bar, explicitly
  NOT merged into `FOYER`/`HALLWAY` (a deliberate prior decision — don't silently merge without new
  evidence).
- No `BALCONY` schema key exists at all. `VERANDAH` is the only relative, unconfirmed as equivalent.

**F6 — why this matters beyond the Find panel:** room-to-room connectivity
(`common/room_graph.js`) is the substrate for a planned MEP conduit-routing POC — fixing the
fragmentation/sparsity problem isn't cosmetic, it's infrastructure for that future feature.

## §LAWS — two error tiers, every formula is designed against these
A blind model cannot look at geometry; these invariants are the substitute for eyes. Severity is
tiered — do not treat the tiers as one bucket:
- **HARD LAWS (zero tolerance — a formula that permits any of these is wrong by definition):**
  containment (every room/element AABB inside its storey/building envelope — nothing strewn outside
  the building); no clash (solids of the same discipline don't interpenetrate); orientation
  (openings/fixtures inherit their host wall's frame — never a free global angle; settled doctrine);
  DAG order (walls → rooms → room graph → walker demand; no level computed before its parents).
- **TOLERABLE (flag, don't block — the user fixes these by hand):** a half-merged hall, a boundary
  off by centimetres, an unclassified room type. Logical-but-imperfect output a human can nudge is
  a SUCCESS state, not a failure.

Why rooms harden the disc walk (the point of this file): the room is the smallest frame in which
every hard law is locally checkable. Room-relative placement bounds the worst possible error by the
room's own diagonal — errors downgrade from law-violation to tolerable BY CONSTRUCTION, instead of
being caught (or missed) after the fact at building scale.

## §TASKS — strategy/formula output only

### Task 0 — trace Terminal's actual room-data source (the one open fact-check, do this first, briefly)
Which file/pipeline produced the room the user saw as a staircase in Terminal? Check
`~/bim-ootb/buildings/Terminal_rooms.db` FIRST — a dedicated rooms DB, confirmed 53 IfcSpace rows
(2026-07-12), prime suspect — then `Terminal_library.db` and the Modeller substrate. Already ruled
out: `deploy/buildings/Terminal_extracted.db` (F4) and `deploy/buildings/Terminal_meta.db` (0 bytes). Once found, check one thing only: did
`STAIR_OVERLAP_REJECT` run against that source at all, or does it bypass `compile_rooms.py` entirely
(e.g. a different/older compilation path)? Report the answer in 2-3 sentences — this is a fact-check,
not a design task, don't over-invest tokens here.

### Task 1 — formula for merging fragmented rooms (F2/F3's root cause)
Propose the exact post-flood-fill merge rule: which two adjacent compiled pockets should become one
room? Grounded options to evaluate, cite which (or propose better, but justify against F1's
"measured threshold" precedent, not invented from nothing):
- Merge adjacent same-storey pockets sharing a wall-length threshold (like `STAIR_OVERLAP_REJECT`'s
  shape) where NEITHER side has a real door between them (a compiled "room" boundary with no real
  door dividing it is itself the signal it's one space, not two).
- Or: merge pockets whose combined aspect ratio/area profile matches an elongated-circulation shape
  (reuse `HALLWAY`'s own measured aspect_ratio=2.70 as the target shape, not a new invented number).
Write the exact formula (inputs, threshold, decision rule) as pseudocode. State which of F2/F3's real
sample cases it would fix, using the real numbers already given above — don't fabricate a new example.

### Task 1b — rejection rule for non-rooms (the "space outside a room" error — F1's missing sibling)
Task 1 merges pockets; this task decides when a pocket is NOT a room at all: exterior/unenclosed
space compiled as a room (F3's 31 `SUSPECT_OPEN` is the symptom — the flag exists, the rejection
rule doesn't). Propose the exact rejection test, same measured-threshold shape as F1's
`STAIR_OVERLAP_REJECT` — e.g. enclosure ratio (fraction of pocket perimeter backed by real wall)
below a threshold ⇒ not a room; or pocket centroid/AABB outside the storey envelope ⇒ reject
(§LAWS containment). Exact formula + threshold, pseudocode.

### Task 2 — ambiguous-door disambiguation strategy (currently "closest 2", 20 cases on HHS alone)
`common/room_graph.js` currently picks the 2 closest-by-distance candidates when a door has 3-4
candidates. Propose a better rule using information already available per-room: door_count profile
(measured per-template in `room_templates.yaml`, e.g. `HALLWAY` mean door_count=4.0 vs `BEDROOM`
mean=1.0) as a prior — a door adjacent to a room that already looks hallway-shaped is more likely a
real hallway-side connection than a coincidental third candidate. Write the exact scoring/tie-break
formula, don't just say "use room type as a signal" — specify the actual computation.

### Task 3 — balcony/hall measured-template survey (F5's gap)
Real-data survey only (same rigor as `HALLWAY`/`BEDROOM` — area_m2/aspect_ratio/door_count, n≥2
minimum before promoting anything): does any building in this repo have a real space matching
`VERANDAH` or `ASSEMBLY_HALL` by name/keyword or by real IfcSpace evidence? If found, write the
measured template (same YAML shape as the existing ones in `room_templates.yaml`) ready to paste in.
If NOT found, say so plainly — an honest "no evidence yet" is a valid, complete answer, not a
failure to fix by inventing numbers.

### Task 4 — what the disc walk gains from room taxonomy (rooms = the walker's coordinate frame)
Principle (see §LAWS closing paragraph): rooms are the walk's containment/orientation/demand frame.
Walker Doctrine holds unchanged (`docs/internal/WalkerDoctrine.md` — building-class axis, discipline
as `WHERE` column): taxonomy is an INPUT to the existing walk, not a new walk axis, and it applies
to the `duplex_rules.db` residential walk AND the `terminal_rules.db` class walk alike — Terminal is
the heaviest reference and must be in scope, not an afterthought. Deliverable: enumerate the walker
decisions that currently run room-blind and, for each, state what room/space input changes — e.g.
corridor-classed pockets as the conduit/duct routing spine (F6's POC), room type → per-room fixture
demand (bathroom → plumbing drops; `HALLWAY` mean door_count=4.0 as a distribution-node signal),
room area/type → per-room-type rule quantities instead of per-building scatter. Rank the list by
payoff for the conduit-routing POC and spec ONLY the top item to Task-1 precision (pseudocode +
parameters). No walker code changes in this pass.

## Calibration note — POC gate FIRST, then spec (read before Tasks 1/1b/2)
Formula-only with zero validation is a real risk for Tasks 1/1b/2: a merge/rejection/scoring rule
can look reasonable on paper and still misfire on real data, and if it's wrong, the cost is a full
implement-then-discover round-trip. Exception to "no code, no implementation": a small
CALCULATION-ONLY script (plain Python/Node, reads real DB rows already queried in F2/F3, computes
what your proposed formula WOULD output — no pipeline changes, no commits, no witness/deploy) is in
scope. **Run it BEFORE writing the spec section, not after** — a rule that dies in a 20-line
calculation script dies for pennies; only formulas that survived real numbers get written up.
Validation ladder, all three rungs: **derive on Duplex** (small, clean, hand-checkable — the
foundation), **fix the cited HHS/JKR cases** (the F2/F3 samples are the acceptance tests), **stress
on Terminal** (heaviest reference — a threshold tuned on duplex-sized rooms may not transfer to
terminal halls/concourses; pass it, or state the regime boundary honestly instead of pretending it
generalizes). Task 0/3 don't need this (fact-check and survey, not a scoring rule).

## DONE WHEN
Task 0 answered in a few sentences. Tasks 1/1b/2/3 each have a precise, pasteable
formula/threshold/YAML block — not prose describing an idea, an actual spec a following session can
implement directly without re-deriving the approach. Each formula section OPENS with its §LAWS
invariant statement ("what proves this correct without anyone looking at a screen") and shows, on
the real F2/F3 samples, the three reported error classes handled: stair-space → rejected (F1),
outside-a-room → rejected (Task 1b), half-a-hall → merged (Task 1). Task 4 = ranked enumeration +
top item specced. No pipeline code written or changed in this pass. Note for the IMPLEMENTING
session (not this one): every rule lands in BOTH mirrors (`scripts/compile_rooms.py` +
`build/room_walker.js`) and `build/witness_room_walker_parity.js` must pass.

---

# FINDINGS — 2026-07-12 (strategy session, POC-gated per calibration note)

POC scripts + full `§`-logs: session scratchpad `poc_room_taxonomy.py` / `poc_round2.py`
(`poc_room_taxonomy.log`, `poc_round2.log`, `poc_terminal_reject.log`). Calculation-only, zero
writes, zero pipeline changes. Every number below is transcribed from those logs. Two POC rounds
were run because **round 1 killed the naive merge rule** — documented in Task 1 below, kept on
purpose: the failure is the evidence the surviving rule needed its second condition.

## Task 0 — Terminal's room source: FOUND, with a threshold nuance
`~/bim-ootb/buildings/Terminal_rooms.db` is the persisted Terminal room source: 53 compiled
`RM_Aras_*` IfcSpace rows over the same Malaysian "Aras"-storey building as the served
`Terminal_extracted.db` (identical 28,262,400-byte base). Its mtime is **2026-06-04 — three days
BEFORE `STAIR_OVERLAP_REJECT` existed** (commit `95579cf2c`, 2026-06-07), so the stair exclusion
never ran against it. However, the F1 metric applied today finds **zero rooms at ≥0.35 stair
overlap; the maximum is 0.207 (`≈ Aras 02 R10`)** — §POC0/§POC0b. So either the user's stair-room
is that 0.207 case (in which case 0.35 is too high a bar for Terminal-scale rooms and the REAL fix
is Task 1b's enclosure rejection, which this file fails hard — see below), or the sighting came
from a live runtime compile (Modeller substrate / older `room_walker.js` build predating the JS
mirror of the exclusion). Implementing session: visually confirm whether `≈ Aras 02 R10` is the
reported room; do NOT lower 0.35 on that single case alone.

## Task 1 — R-MERGE (half-a-hall fix)
**§LAWS invariant (what proves this without a screen):** a merge NEVER crosses a real wall and
NEVER crosses a real door (no-clash with measured reality); merged output only removes synthetic
boundaries, so containment/DAG order are preserved by construction; the negative control (Duplex's
21 real labeled rooms) must emerge unchanged.

**Round-1 failure (kept as evidence):** "adjacent + no door on shared boundary" ALONE over-merges
real rooms — Duplex collapsed 21→12, fusing Kitchen+Bathroom (§POC2, round 1). Root cause: real
distinct rooms are often doorless neighbors THROUGH A WALL. The discriminator is that fragment
boundaries from door-partition are **wall-free synthetic lines** (measured wall coverage 0.02–0.06
on JKR's fragment seams) while real room boundaries are wall-backed (Duplex seams blocked at
>0.25 coverage).

**Rule (pseudocode, all parameters measured-derived):**
```
WALL_T       = median(min(bbox_x, bbox_y) of IfcWall* in this building)   # JKR 0.100m, Terminal/Duplex 0.15m
GAP_TOL      = 2.0 * WALL_T
SHARE_MIN    = 0.50      # shared edge >= 50% of smaller room's parallel side (F1 ratio-shape)
WALL_COVER_MAX = 0.25    # same family as STAIR_OVERLAP_REJECT=0.35: measured-overlap threshold
DOOR_TOL     = 0.60      # door center within this of the seam blocks the merge (safety, see note)

for each same-storey pocket pair (A,B):
  seam = axis-aligned shared edge where AABB gap <= GAP_TOL and parallel overlap > 0
  if seam is None or seam.len < SHARE_MIN * min(A,B parallel side): skip
  wall_cover = union_length(wall AABBs within WALL_TOL of seam line, clipped to seam) / seam.len
  if wall_cover > WALL_COVER_MAX: skip          # real wall => real boundary
  if any door center within DOOR_TOL band of seam (z within [floor-0.3, floor+2.5]): skip
  union(A,B)                                    # transitive via union-find
```
**POC evidence (§POC2b):** JKR 79→51 rooms (28 merges; the `'01 Aras Satu'` split-hallway chains
R2+R3+R8 with a 17.8m seam at 2% wall cover, R21–R23, R24–R27, R28–R31 all collapse — F3's
"hallway split into pieces" directly fixed). **Duplex control: 0 merges, 13 seams correctly
blocked by wall.** Terminal 53: 0 merges (its pockets are wall-bounded; its problem is Task 1b).
Measured note: `blocked_by_door=0` everywhere — the wall condition did all discriminating on these
corpora; keep the door condition as the stated safety for door-in-partition seams, but it is not
what carries the rule.

## Task 1b — R-REJECT (outside-a-room fix)
**§LAWS invariant:** rejection only ever REMOVES pockets, never moves/creates geometry
(containment trivially preserved); zero measured-legitimate rooms may be rejected — the acceptance
test is JKR's own 48 non-OPEN rooms.

**Rule:** run AFTER R-MERGE (merging raises enclosure of legitimate unions).
```
WALL_TOL = 0.45
enclosure(R) = union_length(wall AABBs within WALL_TOL of each of R's 4 perimeter sides,
               clipped per side) / perimeter(R)
if enclosure(R) < 0.25:            REJECT  (not a room — unbounded/exterior pocket)
elif enclosure(R) < 0.50:          KEEP + flag SUSPECT_OPEN  (tolerable tier, user-fixable)
else:                              KEEP
```
**POC evidence (§POC3b/§POC3c):** JKR — rejects 16/31 SUSPECT_OPEN, **0/24 INTERNAL, 0/10
INTERNAL_SMALL, 0/14 SUSPECT_NO_DOOR falsely rejected** (their minima: 0.27/0.60/0.55). Terminal
(pre-exclusion-era file) — 21/53 rejected, several at enclosure 0.00–0.06, i.e. pure outside-a-room
pockets; this is the measured mechanism behind Terminal's "garbage rooms," stair sighting included.
Regime note (honest): Terminal has no ground-truth labels, so its false-reject rate is unmeasurable
there — the zero-false-reject claim rests on JKR's 48 labeled rooms.

## Task 2 — R-DOOR-SCORE (ambiguous-door disambiguation)
**§LAWS invariant:** scoring only reorders candidates already within geometric reach (EXPAND) —
it can never attach a door to a distant room; distance remains primary, the prior is a bounded
discount (≤ LAMBDA metres-equivalent), so DAG order (doors bind after rooms exist) is unchanged.

```
EXPAND = 1.5   # candidate = room whose AABB expanded by this contains door center,
               # door z within [room floor - 0.3, room floor + 2.5]  (same-storey, hard)
LAMBDA = 0.8
hallwayness(R) = min(aspect(R)/2.697, 1) * min(area(R)/10.415, 1)   # HALLWAY template means,
               # config/room_templates.yaml (measured, n=2) — no invented constants
score(R) = distance(door, R.AABB) - LAMBDA * hallwayness(R)
keep 2 lowest scores (was: 2 lowest distances)
```
**POC evidence (§POC4b, after fixing round-1's cross-storey candidate leak):** JKR 34/65 doors
ambiguous → prior changes 4 picks; Terminal 4/135 → 0 changed; Duplex 10/14 → 2 changed, and both
Duplex changes redirect the door TO the real measured Hallway (`A201`/`B201` — the actual labeled
hallways), which is the closest thing to ground-truth validation available. Honest calibration:
effect is at-the-margin (distance already right most of the time) — worth shipping because the
changed cases are exactly the hallway-adjacency cases F2's samples describe, cheap because all
inputs already exist per-room.

## Task 3 — balcony/hall survey: NO EVIDENCE, no template promotable
Swept every `deploy/buildings/*_extracted.db` `spatial_structure` for
BALC/VERANDA/ANJUNG/LOGGIA/TERRACE/DEWAN/HALLE/HALL(-not-HALLWAY) in `name` and `object_type`:
only hits are Duplex's already-templated `A201/B201 Hallway`. SampleHouse ships no
`spatial_structure` table at all in `_extracted`/`_meta`. Zero real VERANDAH, ASSEMBLY_HALL, or
BALCONY spaces exist in shipped data → per the file's own bar (n≥2 measured), **no template is
written; the classifier's refuse-to-guess `(unclassified)` behavior stands.** ENTRANCE_HALL stays
an n=1 exception (F5, unchanged).

## Task 4 — disc-walk gains from room taxonomy (ranked; top item specced)
Per Walker Doctrine: all items are INPUTS to the existing class walk (`duplex_rules.db` AND
`terminal_rules.db`), discipline stays a WHERE column.
1. **Corridor spine routing (spec below)** — merged CORRIDOR-classed rooms + room-graph door edges
   = the conduit/duct routing DAG (F6's POC). Biggest payoff: turns routing from geometric search
   into a graph query.
2. **Per-room fixture demand** — room type → discipline demand rows (BATHROOM → plumbing drops,
   KITCHEN → waste/supply); quantities become per-room-type instead of per-building scatter.
3. **Room-frame placement** — walker-placed fixtures cite containing room + host wall ⇒ §LAWS
   containment/orientation hold by construction, max error bounded by room diagonal.
4. **Riser/distribution-node placement** — room-graph degree centrality (HALLWAY door_count=4.0
   signal) picks the node room per storey; vertical alignment across storeys picks the riser.

**Top-item spec — R-SPINE (corridor routing substrate):**
```
input:  merged+rejected room set (Tasks 1/1b), room_graph edges (door-connected pairs),
        room classifications (room_type_classifier)
spine(storey) = the connected subgraph of rooms with hallwayness >= 0.5 (Task 2's measure);
                if empty, the single max-degree room (fallback, flagged)
route(fixture_room -> riser_room):
  path = BFS over room_graph edges restricted to (spine ∪ {fixture_room, riser_room})
  conduit polyline = door-center to door-center within each room on path,
                     offset to hug the room's longest wall (orientation law: host-wall frame)
  §LAWS check per segment: polyline ⊂ room AABB (containment, locally checkable — the room IS
  the coordinate frame; a routing error is bounded by one room's diagonal, tolerable tier)
output: per-discipline conduit BOM lines parented to the rooms traversed (WHAT/HOW/WHERE intact)
```
Precondition, measured: JKR's spine only exists AFTER R-MERGE (the 17.8m hallway seam at 2% wall
cover is the spine, currently split in 3); on HHS the 66% deadend/orphan rate (F2) means the spine
is the highest-value merge target there too.

## Validation ladder status (calibration note)
- **Duplex (derive/control):** R-MERGE 0 false merges; R-DOOR-SCORE picks real Hallway. PASS.
- **JKR (failure corpus):** 79→51 rooms, split-hallway chains merged; 16 exterior pockets
  rejected, 0 false. PASS on the F3 storey samples.
- **Terminal (stress):** R-REJECT bites hard (21/53) on the known-bad pre-exclusion file; R-MERGE
  no-ops (correct — different failure mode); no labels ⇒ false-reject rate unmeasured there. PASS
  WITH STATED BOUNDARY.
- **HHS:** the 105-room graph is runtime-compiled, not persisted (shipped DB has 14 spaces) — the
  F2 numbers stand as given; ladder rung to be witnessed by the implementing session via the live
  `§ROOM_GRAPH` line after wiring (expect: edges up from 64, deadend+orphan down from 69).

## Handoff to implementing session
Order: R-MERGE → R-REJECT → R-DOOR-SCORE → R-SPINE. Both mirrors (`scripts/compile_rooms.py` +
`build/room_walker.js`), parity witness must pass, PUSH PAUSE in effect (local commits only,
localhost verification, no push/PR until lifted).

## DISPATCH SPLIT — Manager verdict, 2026-07-12 (binding on whoever picks this up)
**Lane A — execution-tier (Sonnet or Fable5, "follow the pseudocode" session): Tasks 1/1b/2 ONLY**
(R-MERGE, R-REJECT, R-DOOR-SCORE). These are POC-validated with named acceptance cases; nothing
left to design. Port into BOTH mirrors, run `build/witness_room_walker_parity.js`, witness the
acceptance cases named in each formula section (JKR `'01 Aras Satu'` chains merge; JKR 48 non-OPEN
rooms zero false rejects; Duplex control unchanged). Do NOT touch R-SPINE or the Terminal wiring
question — they are explicitly out of this lane's scope.

**Lane B — judgment-tier (a session with latitude, NOT a pseudocode-executor): two items, do them
BEFORE any R-SPINE code exists.**
1. **Task 0 loose end first — trace whether ANY of this reaches the live Terminal path.** The
   served `Terminal_extracted.db` has ZERO room rows (F4); `Terminal_rooms.db` (the room source
   found above) may not be on the Viewer's load path at all. If the live path never reads compiled
   rooms for Terminal, an implementation pass "fixing Terminal" burns itself on a building this
   code never touches. Establish the actual load path (Viewer + Modeller substrate) before wiring.
2. **R-SPINE validation — it was NOT POC'd (unlike 1/1b/2).** Known soft spot, flagged at review:
   its per-segment containment check assumes room AABBs, but R-MERGE produces NON-CONVEX unions
   (e.g. JKR's L-shaped merged hallway chain) — "polyline ⊂ room AABB" weakens exactly where the
   spine matters most. Watch what R-SPINE actually produces on JKR's merged `'01 Aras Satu'`
   hallway before trusting it; expect the containment law to need a non-convex formulation
   (union-of-member-AABBs, not merged-AABB).
Lane A may start immediately; Lane B gates R-SPINE. Neither lane pushes (PUSH PAUSE).

---

# PROMPT — Lane B as geometry-grind (2026-07-12c): compute, don't judge

```
# ⚠ DO NOT REMOVE
SCOPE: Lane B above is stated as judgment ("trace," "watch," "expect") — that's the wrong shape for this
project's determinism rule. Both items resolve to a NUMBER computed from real DB geometry, not a read.
Calculation-only: no pipeline code changes, no commits to scripts/compile_rooms.py or build/room_walker.js
in this pass — that's still the Lane A/implementing session's job. Read the log after every run. PUSH PAUSE
in effect: commit locally (this file only), do not push, do not open a PR.
```

## Grind 1 — close Task 0 with one traced fact, not a disjunction
Task 0 above ends "either the 0.207 case… or a live runtime compile." Resolve which by tracing the code
path mechanically:
1. Find every DB Terminal's building record can resolve to at runtime — grep the Viewer/Modeller building
   manifest / `viewer/scene.js` registry / Modeller substrate loader for every path wired to Terminal, not
   just `Terminal_extracted.db`.
2. For each candidate DB with `spatial_structure` rows, cite the exact loader function/line that reads it
   in the live app — don't assume, trace the call.
3. If a runtime COMPILE path exists (Modeller substrate calling `room_walker.js` fresh off the source IFC,
   not a pre-built DB), run it headlessly against Terminal's IFC and apply the F1 stair-overlap metric to
   THAT output, not to the stale `Terminal_rooms.db`.
4. Log the single traced answer:
   ```
   §POC0c SOURCE=<exact file, or "runtime:<function>"> STAIR_EXCLUSION_APPLIED=<yes/no>
   §POC0c MAX_STAIR_OVERLAP=<value> ROOM=<id>
   ```

## Grind 2 — prove or disprove R-SPINE's AABB-containment gap on real merged rooms
Don't "watch and expect" — compute it:
1. For every JKR merge cluster in §POC2b (R2+R3+R8, R21-R23, R24-R27, R28-R31, etc.), build the TRUE merged
   polygon (union of the real wall-bounded pocket polygons already read for §POC2/§POC3) and its AABB.
2. `slack_area = area(AABB) − area(true_polygon)` per cluster. Log every value.
3. Run R-SPINE's stated routing rule (door-center to door-center, offset to hug the longest wall) through
   each cluster; test whether any polyline point falls inside slack_area (passes cheap AABB check, fails
   true-polygon containment).
4. Log:
   ```
   §POC5 JKR cluster=<ids> slack_area=<m2> polyline_violations=<count>
   §POC5 JKR total_clusters=<n> total_violations=<count>
   ```
5. Verdict is mechanical: `violations=0` across all clusters ⇒ AABB check stands as specced (cheap,
   sufficient on measured data) — say so with the number. `violations>0` ⇒ the §LAWS containment line in
   R-SPINE must change from AABB to true-polygon containment (also computable, just costlier) — name which
   clusters forced it, don't generalize past what was measured.

## DONE WHEN
Task 0 above has one §POC0c-backed answer, no "either/or" left. R-SPINE's spec (Task 4) carries either a
"measured: AABB sufficient, 0 violations on N clusters" line, backed by §POC5, or a corrected true-polygon
containment rule with the forcing cases named. Append results as a new dated section below this one, same
file — don't create a second doc.
