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
