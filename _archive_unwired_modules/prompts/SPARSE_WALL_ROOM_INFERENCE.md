<!-- Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com> · SPDX-License-Identifier: MIT -->
# SPARSE-WALL ROOM INFERENCE — the real-world IFC challenge (2026-07-11, MANAGER-assigned)

```
# ⚠ DO NOT REMOVE
SCOPE: `compile_rooms.py`'s flood-fill needs enough real IfcWall geometry to work. Most real-world
federated IFC files DON'T have that — HHS is a live, diagnosed example (this session, root-caused
below), not a hypothetical. This is the actual competitive challenge stated by the user: "most IFC"
looks like this, and solving it honestly is the differentiator. Read the log after every run.
PUSH PAUSE IN EFFECT (`CLAUDE.md` §⏸ PUSH PAUSE) — commit locally, verify on localhost, do NOT
push, do NOT open a PR, until told otherwise.
```

## The diagnosed problem (real numbers, not hypothetical — MANAGER, 2026-07-11)
Wall-to-room ratio, measured directly on 3 shipped buildings:
- Hospital: 1440 walls / 201 rooms = **7.16 walls/room** (flood-fill works: 201/201 rooms from the
  strong `INTERNAL`/`INTERNAL_SMALL` methods).
- Clinic: 1080 walls / 197 rooms = **5.48 walls/room** (flood-fill works: 195/197 strong).
- **HHS: 112 walls / 105 rooms = 1.06 walls/room** (flood-fill fails universally: 105/105 rooms fall
  back to `INTERNAL_DOORPART`, the weakest method — door-position guessing, no real walls found).

HHS's `elements_meta` is dominated by `IfcMember` (1450) and `IfcPlate` (629) — structural steel
connection detail — plus `IfcCurtainWall` (33) and only 112 `IfcWallStandardCase`. This is a
federated structural+envelope model where interior architectural partitions were either not merged
in or modeled sparsely. **Zero `IfcStair`/`IfcRamp` entities anywhere** in the extraction, confirmed
directly. This is not a HHS-specific quirk — real-world federated BIM (structural model + MEP model
+ partial/no architectural model) is common; solving it generalizes far beyond one building.

## The idea (validated on real data this session, not speculative)
When walls are too sparse to flood-fill, other REAL, ALREADY-EXTRACTED evidence still exists and
hasn't been used:
1. **Structural grid (columns/members)** — HHS has 1450 `IfcMember` entities. Architects/engineers
   place partitions on or between structural gridlines far more often than not. The
   SEMI-GRID/emergent-datum work already in this codebase (`RESUME_GRAPH_MODELLER_INTEGRATION.md`)
   derives candidate gridlines from column centroids — reuse it, don't rebuild it. A room hypothesis
   bounded by real grid cells is a stronger prior than door-position guessing alone.
2. **Doors as real, positioned evidence** — HHS has 133 real `IfcDoor` entities, not sparse at all.
   `INTERNAL_DOORPART`'s current door-partition method is the FALLBACK of last resort; check whether
   it can be strengthened by combining door positions WITH the grid (door + gridline intersection is
   a much stronger room-boundary signal than door position alone).
3. **Slab boundaries per storey** — 75 `IfcSlab` entities across 4 storeys; check whether slab
   footprints are already sub-divided by zone/wing (real geometric evidence, unexploited) — this
   would be a THIRD independent signal, not a guess.
4. **Curtain wall as building envelope** — 33 `IfcCurtainWall` entities likely bound the building
   perimeter; combined with the grid, gives outer boundary + inner subdivision structure.

## Phase 0 — Data Health Guard (build FIRST, prerequisite to Phase 1 below)
External review (Gemini, relayed by user) proposed a pre-check guard that detects sparsity BEFORE
running flood-fill, rather than only diagnosing it after the fact — a genuinely good complementary
idea, worth building, but two things need correcting before it's trustworthy (same discipline as
everything else in this doc — verify against real schema/data, don't accept asserted numbers):
- **Schema correction:** the proposed SQL used `type='IfcWall'`/`type='IfcSpace'` — this repo's real
  schema is `elements_meta.ifc_class` (not `type`), and the real wall class is `IfcWallStandardCase`
  (confirmed via direct query this session), not bare `IfcWall`. Verify the exact column/class names
  against the live schema before writing this query, don't copy the external suggestion verbatim.
- **Threshold correction:** the proposed guardrails (wall/space ratio < 2.0, structural >50%/walls
  <10%) are asserted round numbers, not derived from this project's own data. **Derive real
  thresholds from the 3 measured buildings above** (Hospital 7.16, Clinic 5.48, HHS 1.06 walls/room)
  — e.g. a threshold placed between HHS's 1.06 and Clinic's 5.48 is defensible because it's anchored
  to real, measured buildings on both sides of the line, not because 2.0 sounds reasonable.
Build: (1) an "Architectural Completeness Ratio" check (wall-count ÷ space-count, real schema), (2)
a "Component Discipline Fingerprint" reclassification (flag a file as "Structural-Only Federation"
when structural-element dominance is real and measured, threshold derived not asserted), (3) a
"Circulation Completeness" check (multi-storey building + zero `IfcStair`/`IfcRamp` = flag, already
confirmed true for HHS). Surface the flag honestly in the scoreboard/UI — "insufficient architectural
partitions to compute true spatial boundaries" per the external suggestion's own wording, which is
good, honest framing worth keeping.

## Phase 1 — Signal-fusion inference (the harder, open-ended piece)
1. **Confirm/refute each signal's real availability and usefulness on HHS specifically** (don't
   assume grid/door/slab data is clean just because it's extracted — check it the way this session
   checked walls, with real ratios/counts, before designing around it).
2. **A confidence-scored FUSION method** — not a replacement for flood-fill (which stays the primary
   method when walls ARE sufficient), a FALLBACK tier that activates below some measured wall-density
   threshold (derive the threshold from the Hospital/Clinic/HHS ratios above, don't invent a round
   number) and combines whichever of the 4 signals above have real data, each weighted by how much
   real evidence backs it. Same discipline as everything else this session: report confidence
   honestly, refuse rather than guess when even the fused signal is too weak.
3. **This is genuinely open-ended engineering/research, not a templated task** — the exact fusion
   method (weighted voting? Voronoi over grid+door points? something else?) is left to your judgment;
   state your reasoning for whatever you pick. This is explicitly the "harder, real algorithm design"
   task, not a mechanical port.

## Witness
Rerun on HHS: does the fused method recover MORE real room structure than pure door-partition
guessing, measured against something independently checkable (e.g., do the resulting room boundaries
respect real slab/curtain-wall envelope constraints? does room count per storey become more plausible
for an office building?). Also rerun on Hospital/Clinic to confirm ZERO regression — buildings with
healthy wall density must keep using flood-fill unchanged, this fallback only activates where
genuinely needed.

## Non-goals
- Do not touch `compile_rooms.py`'s existing flood-fill for wall-rich buildings — this is an
  ADDITIONAL fallback tier, not a replacement.
- Do not invent a wall-density threshold without deriving it from real measured buildings' ratios.
- Do not claim HHS's fused rooms are as reliable as Hospital's flood-filled ones — report the
  confidence honestly, lower is expected and correct here.

## DONE WHEN
**Phase 0:** Data Health Guard built with real-schema-verified queries and thresholds derived from
Hospital/Clinic/HHS's actual measured ratios (not asserted numbers), witnessed to correctly flag HHS
and correctly NOT flag Hospital/Clinic.
**Phase 1:** at least one additional real signal (grid, door+grid fusion, slab, or envelope) is
measured for real availability on HHS, a fusion method is designed and justified, witnessed to
genuinely improve on door-partition-only for HHS specifically, with zero regression on wall-rich
buildings. Phase 1 can be scoped as its own follow-up if Phase 0 alone is a full task — say so if so.

---

## 2026-07-11 (same day, later session) — Phase 0 BUILT + WITNESSED. Two premises above found STALE.

### Premise correction (verify-before-build, same discipline as the rest of this doc)
Before writing any code, re-checked this doc's own diagnosed numbers against the LIVE DBs
(`deploy/buildings/{Hospital,Clinic,HHS_Office_Federated}_extracted.db`) and found **two of the
three central claims above are now stale**, both explained by a same-day, unrelated commit that
landed *before* this doc was written but whose effect wasn't re-checked:
- `7133bbe06` ("§7 ROOM WELL-FORMEDNESS", **2026-07-11 03:52:22**) added §WALL-VERT to
  `compile_rooms.py`: curtain-wall parents (`IfcCurtainWall`) carry no transform, so their real
  geometry is in their children — `IfcMember`/`IfcPlate` — included when vertical (`bbox_z >=
  0.5 × median door height`), **discipline='ARC' filtered**. Confirmed directly: HHS's 1,450
  `IfcMember` rows are **100% discipline='STR'** (real structural steel, correctly excluded), but
  HHS's 629 `IfcPlate` rows are **100% discipline='ARC'** (curtain-wall glazing panels) — 345 of
  them stand ≥1.09m tall and now close the raster as vertical envelope. Effect: re-running
  `compile_rooms.py` dry-run today gives HHS **71 rooms, 100% via flood-fill methods
  (INTERNAL/INTERNAL_SMALL), 0% `INTERNAL_DOORPART`** — not "105/105 rooms fall back to
  INTERNAL_DOORPART" as diagnosed above. This doc's `11:26:14` commit was written *after*
  `03:52:22` but didn't re-run the tool to check. The fix is legitimate (glazing panels genuinely
  bound space; STR framing is correctly excluded) — not a shortcut.
- **"Zero IfcStair/IfcRamp entities anywhere" is also false for HHS**, confirmed by direct query:
  `12 IfcStair + 8 IfcStairFlight` rows exist, all `discipline='ARC'`, across 4 real storeys
  (`Level 1/2/3` + `Roof Level`). Source of the original claim not re-traced; the live DB has real
  stair data today.

**Why Phase 0 is still built as specified despite this** — the guard's value doesn't depend on
HHS's room *count* currently being broken. §WALL-VERT's fix works by borrowing exterior-envelope
glazing evidence, not by adding real interior partitions — so HHS's **true architectural wall
density stays low regardless** (see below), which is exactly the upstream, tool-heuristic-
independent signal this guard exists to surface. A future federated building without HHS's lucky
curtain-wall coverage would still fail exactly as originally diagnosed; Phase 0 flags that
condition directly, not by proxy through flood-fill's current success/failure.

### Phase 0 — BUILT
`scripts/compile_rooms.py` (worktree `/tmp/wt-sparse-wall-room-inference`, branch
`sparse-wall-room-inference`, commit `3017e03a4`, **not pushed — PUSH PAUSE**): new
`data_health_guard(c, building_label)` function, called first thing in `main()`, before the
flood-fill loop. Never blocks — flood-fill runs on every building regardless of flag state; the
guard's job is to print the truth about how much the result should be trusted.

Three checks, each with its threshold derived from real measured numbers (elements_meta.ifc_class
/ .discipline, verified live schema — not `type='IfcWall'`/`'IfcSpace'` as an earlier external
suggestion assumed):

1. **`wall_door_ratio`** — TRUE wall entities (`ifc_class LIKE 'IfcWall%'`, `discipline='ARC'` —
   narrower than flood-fill's own `WALL_LIKE` raster set, which also counts doors/curtainwall/
   columns/windows) ÷ real ARC door count. **Deliberately uses door count, not space count**:
   `IfcSpace` is **absent (0 rows) from all three raw extracts** — there is no ground-truth room
   count to divide by before flood-fill runs, and using flood-fill's own output would be circular
   (and proven unstable by the §WALL-VERT swing above). Door count is real, independently
   extracted, and every habitable room conventionally has ≥1 door.
   - Measured: Hospital 1440/440=**3.27**, Clinic 1080/254=**4.25**, HHS 160/133=**1.20**.
   - `WALL_DOOR_SPARSE_THRESHOLD = 2.235` = midpoint of HHS (1.20) and Hospital (3.27), the two
     nearest measured points straddling the line.
2. **`discipline_fingerprint`** — STR-discipline share of ALL `elements_meta` rows, using the
   `discipline` **column**, not raw `ifc_class` counts (which mislead on their own: Hospital has
   7,127 `IfcMember` rows but **100% discipline='ARC'** — curtain-wall mullions — while HHS's 1,450
   are **100% discipline='STR'** — real structural steel; confirmed by direct `GROUP BY
   discipline`).
   - Measured: Hospital **4.5%**, Clinic **10.1%**, HHS **24.8%**.
   - `STR_DOMINANCE_THRESHOLD = 0.174` = midpoint of Clinic (10.06%) and HHS (24.81%).
3. **`circulation_completeness`** — multi-storey (>1 real, non-`'Unknown'` storey) with zero
   `IfcStair%`/`IfcRamp%` anywhere = flag. Doesn't fire on any of the 3 measured buildings today
   (all have real stair data, including HHS per the correction above) — kept as a defensive check
   for a genuinely stair-absent federation, which the original diagnosis (incorrectly) believed HHS
   to be.

**Witness** (`/tmp/claude-1000/.../scratchpad/phase0_witness.log`, dry-run, zero writes, full
compile completes end-to-end for all 3 after the guard prints):
```
Hospital: OK       walls=1440 doors=440 wall/door=3.27  STR%=4.5  storeys=8 stairs=62
Clinic:   OK       walls=1080 doors=254 wall/door=4.25  STR%=10.1 storeys=7 stairs=13
HHS:      FLAGGED  walls=160  doors=133 wall/door=1.20  STR%=24.8 storeys=4 stairs=20
  ⚠ SPARSE_WALLS (wall/door ratio 1.20 < 2.23 — insufficient architectural partitions to
    compute true spatial boundaries)
  ⚠ STRUCTURAL_ONLY_FEDERATION (STR discipline = 24.8% of all elements, > 17.4% — reads like
    a structural/MEP federation with a thin or absent architectural model)
```
Zero regression on Hospital/Clinic (both `OK`, no flags). HHS correctly flagged on two independent
signals. **Phase 0 DONE WHEN met.**

### Phase 1 — signal availability measured on HHS, fusion method designed, scoped as follow-up
Per this doc's own escape valve ("Phase 1 can be scoped as its own follow-up... say so if so"):
built Phase 0 + measured Phase 1's signal availability (item 1 of the Phase 1 spec) this session;
did **not** implement the fusion algorithm itself. Reasoning: the flagship failure case motivating
Phase 1's urgency (HHS forced into weak door-only rooms) turned out to already be resolved by the
unrelated same-day §WALL-VERT fix (see correction above) before this task was assigned — removing
the immediate pressure — and a real confidence-scored fusion algorithm is substantial new
engineering (per the spec's own framing, "genuinely open-ended... not a templated task") that
deserves a dedicated session with its own witness pass, not a rushed add-on at the end of this one.

**Signal availability, measured directly on HHS (2026-07-11):**
- **Structural grid** (`IfcColumn`, real transforms): 131/85/41 columns on Level 1/2/3, all with
  valid `center_x`/`bbox` — real, non-degenerate, usable input for `grid_dims.js`'s
  `detectOpportunityGrids`/`derive_datums_and_anchors` (neither has been re-run against HHS's
  current extracted DB; `elements_rtree`/`datum_plane` tables don't exist in it yet).
- **Doors**: 133 real, positioned (`storey_doors` already extracts these) — confirmed not sparse,
  as originally claimed.
- **Slab footprints**: 25/35/12/7 slabs per storey (Level 1/2/3/Roof), footprint areas ranging
  **0.09 m² to 3,538 m²** on the same floor — real variety, not one undivided slab per storey, i.e.
  genuinely usable zone/wing evidence (though the wide range suggests a mix of one whole-floor
  reference slab plus smaller infill pieces — a fusion pass would need to separate those, not
  average them).
- **Curtain-wall envelope**: ARC-discipline `IfcPlate` (glazing) XY extent is `x:[-13.4,53.2]
  y:[-14.3,41.2]`, vs. the building's overall ARC extent `x:[-29.2,53.2] y:[-16.6,43.3]` — glazing
  covers the east/south/most of the perimeter but **not** the far-west side (`x<-13.4`), i.e. a
  real but **partial** envelope signal, not a closed loop — usable as a boundary constraint only on
  the sides it actually covers.

**Fusion method designed (not yet implemented) — Grid-Cell Confidence Overlay, justified over
Voronoi:** A pure Voronoi-over-seed-points approach (grid intersections + doors as sites) was
considered and rejected as the primary method, because `partition_by_doors` already IS a discrete
Voronoi-like BFS from door seeds (per its own docstring) — adding more seed points doesn't address
the actual gap, which is **boundaries**, not seeds. The real missing signal is *where a partition
would run*, and gridlines are architecturally exactly that (partitions land on/between gridlines
far more than not). Proposed design instead: extend `storey_walls()` to accept optional **virtual
wall segments** — `detectOpportunityGrids`'s x/y candidate lines, clipped to the storey's XY
bounds — drawn into the raster at LOWER weight than real walls (e.g. require 2 coincident signals,
such as a gridline crossing a slab-footprint edge or falling inside the curtain-wall-covered
perimeter band, before it seals a gap that a real wall/door wouldn't). This only activates when
`data_health_guard`'s `SPARSE_WALLS` flag is set for that building — never touching the raster for
wall-rich buildings (satisfies the non-goal). Each resulting room would carry a confidence score
from which signals actually closed its boundary (real wall fraction, door adjacency, gridline
fraction, slab-edge fraction, envelope fraction), and ship as a new `predefined_type` tag —
e.g. `FUSED_GRID` — parallel to the existing `SUSPECT_*` review-row convention, so the UI/lens
layer can visually distinguish it and never present it at Hospital's `≈`-flood-fill confidence
level (per this doc's own Non-goals).

**Follow-up spec (well-specified, ready to pick up directly):**
1. Implement `detectOpportunityGrids`-equivalent grid-line derivation reading straight from
   `element_transforms` (Python, no `elements_rtree`/`datum_plane` dependency — those tables don't
   exist yet in the shipped extracted DBs) — mirror `grid_dims.js`'s vote-clustering, don't port
   its JS 1:1.
2. Add virtual-wall injection to `storey_walls`/`_rasterize`, gated strictly behind
   `SPARSE_WALLS` flag from Phase 0.
3. Add confidence scoring + `FUSED_GRID` predefined_type + a lens-visible confidence label.
4. Witness: does the resulting HHS room count/shape improve over pure flood-fill's already-71 (the
   real target now: better ROOM BOUNDARIES / more plausible per-storey counts for an office
   building, not "unstuck from 0" since that's no longer the failure mode) — check against slab/
   curtain-wall envelope as an independent consistency check, same idea as originally specced.
   Rerun Hospital/Clinic to confirm the `SPARSE_WALLS`-gated virtual-wall path never activates for
   them (zero regression).

## Session state (checkpoint, 2026-07-11 — for a fresh session to pick up cleanly)
- **Phase 0: COMPLETE and witnessed** — code in `scripts/compile_rooms.py` on branch
  `sparse-wall-room-inference` (worktree `/tmp/wt-sparse-wall-room-inference`, base commit
  `9e7ffc1b9`), local commit `3017e03a4`, **not pushed** (PUSH PAUSE). Witness log at
  `/tmp/claude-1000/-home-red1-bim-compiler/44c1ff7e-a9fe-4992-bf59-eca629722cd7/scratchpad/phase0_witness.log`.
  Dry-run only — `--write` path untouched/untested by this session (Phase 0 doesn't need it; the
  guard runs and prints before any write decision).
- **Phase 1: NOT implemented** — signal-availability measurement done (see numbers above), fusion
  method designed and justified in writing, concrete 4-step follow-up spec written above. No code
  written for Phase 1. A fresh session can start directly at follow-up step 1.
- **Two stale premises in the original diagnosis (top of this doc) corrected above** — a fresh
  session should treat the "measured" numbers under "The diagnosed problem" (walls/room ratios
  computed from a since-superseded `spatial_structure` snapshot, and "zero stairs" for HHS) as
  **historical**, not current — use this section's numbers instead. This doesn't invalidate the
  original ask (sparse-wall federated buildings are still a real class of problem — Hospital 3.27
  vs HHS 1.20 wall/door ratio is real and current), it just means the *specific* HHS DOORPART
  failure that motivated Phase 1's urgency is currently resolved by an unrelated fix, which is why
  Phase 1 was scoped as a follow-up rather than rushed to completion this session.
- **Not yet done, if resuming this exact task**: (a) push the worktree branch once PUSH PAUSE
  lifts — currently local-only; (b) optionally merge/copy `scripts/compile_rooms.py`'s
  `data_health_guard` addition back into the main `~/bim-compiler` checkout (it only exists on the
  worktree branch right now — the shared tree's copy does NOT have it); (c) Phase 1 follow-up
  steps 1-4 above, untouched.
- **DB note for whoever resumes**: `deploy/buildings/*.db` are gitignored — a fresh worktree does
  NOT get them via `git worktree add` (confirmed the hard way this session: the first run created
  0-byte stub files by calling `sqlite3.connect()` on a path that didn't exist yet). Copy the real
  files from `~/bim-compiler/deploy/buildings/` before running anything against them in a new
  worktree.
