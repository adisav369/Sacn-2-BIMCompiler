<!-- Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com> · SPDX-License-Identifier: MIT -->
# ROOM INTELLIGENCE SCOREBOARD — the standard reporting baseline (2026-07-11)

```
# ⚠ DO NOT REMOVE
SCOPE: this doc IS the standard format for reporting Room Intelligence lane status — a scored
feature table + a per-building coverage table, both with real numbers pulled fresh from the actual
databases, never recalled from memory. Confirmed by the user (2026-07-11) as "exactly the standard
expected." Update THIS doc when status changes — don't rebuild the format from scratch, and don't
report status in prose when this table format applies. See `prompts/MANAGER.md` §DELIVERABLE for
the pointer that makes this the default.
```

## How to refresh this doc (do this, don't guess from old witness logs)
1. **Feature table**: re-run each thread's own witness (paths named in the table) — score 0-10,
   one WORKS line + one DOESN'T-WORK/gap line per row, both required, no bare score.
2. **Building table**: re-query the actual compiled databases directly (`spatial_structure` in each
   `modeller/*_ARC.db` or `deploy/buildings/*_extracted.db`) through `build/room_type_classifier.js`
   — do not reuse old numbers without re-running; buildings get re-compiled and numbers move. Script
   pattern: load each building's DB via sql.js, run `classifier.classifyRoom({area, aspect}, config)`
   per `IfcSpace` row, aggregate storeys/rooms/classified/confidence/tier/type.
3. Flag Real vs Inferred ground truth per building — never blur the two (only Duplex + SampleHouse
   have real human-authored labels; every other building's numbers are classifier inference against
   COMPILED synthetic rooms).

## Feature scoreboard (0-10, WORKS / GAP required per row)
| # | Feature | Score | Works | Doesn't work / gap |
|---|---|---|---|---|
| 1 | Find-panel visibility fix | 9 | Two-part bug fixed, 28/28, merged live (bim-ootb PR #728) | — |
| 2 | Prompts/docs archive audit | 8 | 9 archived with real evidence, 1 false positive caught first | 6 flagged ambiguous, needs a human call |
| 3 | UBBL room-size demo gate | 8 | Only 2 verified thresholds wired, merged (PR #729) | Explicitly demo-scope, not real compliance |
| 4 | MEP routed-network render | 7 | Real gap closed (pixel-probe blind spot), merged (PR #731) | Found Terminal substrate has 0 MEP data — unfixed, out of scope |
| 5 | Room habitability + Viewer self-heal | 9 | HHS 14→105 rooms, ships via SQL patch not binary, merged (PR #732) | — |
| 6 | Room Lens volume-box render | 8 | Box-area exact match to ground truth (diff=0.000m²), merged (PR #733) | `_roomSelect()` sibling function still not room_guid-aware |
| 7 | Terminal coordinate-frame fix | 9 | Root-caused to 2 cited pipeline bugs, <6mm accuracy | PR #41 open, unmerged (branch-sync conflict, unrelated) |
| 8 | Room-type classifier | 6 | Real Gaussian fit + fixture-evidence signal (2026-07-13, `classifyRoomWithFixtures`), 18/18 Duplex ground-truth forward-replay match, symbiotic with size (evidence must agree with physical size, not override it) | Only 2 buildings (Duplex+SampleHouse) have ground truth; fixture signal can't recover a room that was never compiled (Terminal's real toilets exist but sit outside every compiled room) |
| 9 | Door-access signal | 4 | 3 genuine real-data wins (Kitchen 0-door correction ×2, Entrance Hall false-match refused) | Net regression if defaulted on — correctly left off, not finished |
| 10 | Primary/supplementary tier + BOM wiring | 7 | Duplex units A/B genuinely satisfy `required_spaces`, reused existing Java validator pattern | SampleHouse fails it honestly (sparse data, not a bug) |
| 11 | OBB clash-gate narrow-phase | 8 | Real 0.0428m false-positive cleared, MANAGER-reran 22/22 independently, 535ns/pair | ~5× per-pair cost vs AABB (small absolute, real regardless) |
| 12 | DISC-walk room-type-aware placement | 8 | PLB→Bathroom/Utility + FP→Foyer real signal wired; ELEC/ACMV honestly refused | Only Duplex has real MEP+room data to measure from |
| 13 | Corridor pathway routing (Find panel) | 9 | MANAGER-reran 15/15 independently; door-guid continuity re-measured, not trusted | Duplex-only proof so far; real disconnections found, not yet resolved |
| 14 | Prior-art analysis + citations | — | Found near-exact academic match (Buruzs et al. 2022) + 2 usable public datasets (RoomGraph, SAGC-A68) | Not yet acted on — still a proposal |
| 15 | Building parts taxonomy (STAIRWAY/LIFT_SHAFT/PLANT_ROOM) | 9 | Real multi-building ground truth for STAIRWAY (5 buildings) + LIFT_SHAFT (4 buildings) + **PLANT_ROOM n=4 as of 2026-07-12** (Terminal 644 / Hospital 5391 / Clinic 1881 / HHS 1769 class-checked elements in the full `*_extracted.db` data; residents are ARC-stripped by design — `DISCWALK_PLANT_ROOM_INDUSTRIAL_TAXONOMY.md` survey); top-down/bottom-up checklist, W-BUILDING-PARTS 13/13 PASS (re-run green after the n=4 yaml update); word-boundary + class-gate fixes MERGED (bim-ootb #740/#742) and held under re-run; BOTH VISION-LOCK sentence-5 UI halves wired + MANAGER-verified on real data — Find panel (bim-ootb `d04ddd5`) and Modeller Outliner (bim-ootb `f10c5295`) | STORAGE/AIR_LIGHT_WELL re-searched 2026-07-12 including the institutional DBs — still honest-zero (air well: no name evidence anywhere incl. shaft/atrium/schacht, and the geometric route is blocked because `spatial_structure.size_z` is a per-storey constant, not a per-space measured height; storage: compiled room names are synthetic `≈ Level N Rk` so label search is structurally blind, and no shelving/rack classes exist — Clinic's 10 base cabinets are casework). ACMV↔Plant-Room proximity measured (Task 2, same file): calibration falsifier PASSED (clustering locates Terminal's literally-labeled `A_Wall_Ext_230mm_AHU_V1` enclosure at 0.76m) but the correlation itself is WEAK on Terminal (ACMV/contrast median-distance ratios 0.72–0.79) and NONE on HHS (1.00–1.05) — honest negative, NOT wired as a disc_walker placement signal, matching the door-access-signal precedent (row 9). `required_spaces`-style hard gating still deliberately advisory-only.

**Weakest links, name them plainly every time this table is refreshed:** door-access signal (4) and
classifier sample size (5) — both honestly reported, neither hidden or inflated.

## Building coverage (real numbers, queried 2026-07-11 — re-run before trusting after this date)
| Building | Storeys | Rooms | Classified | Avg Confidence | Corridor/Circulation | Restrooms | Stairway | Lift Shaft | Plant Room | Ground Truth |
|---|---|---|---|---|---|---|---|---|---|---|
| **Duplex** | 4 | 20 | 18 (90%) | 89.6% | 4 | 4 | 4 (2 positioned) | 0 | 0 | **Real** (human-labeled IFC) |
| **SampleHouse** | 2 | 3 | 3 (100%) | 86.1% | 1 | 0 | 0 | 0 | 0 | **Real** (human-labeled IFC) |
| Terminal | 6 | 43 | 27 (63%) | 80.8% | 1 | 0 | 33 | 5 | 74 (ARC resident; 644 in full extraction — advisory, n=4 since 2026-07-12, see row 15) | Inferred (COMPILED) |
| SampleCastle | 4 | 51 | 21 (41%) | 93.6% | 0 | 5 | 9 | 67 | 0 | Inferred (COMPILED) |
| HHS | 4 | 105 | 26 (25%) | 95.8% | 0 | 1 | 0 | 3 | 0 (ARC resident; 1769 in full extraction) | Inferred (COMPILED) |
| Clinic | 3 | 197 | 64 (32%) | 92.9% | 5 | 38 | 9 | 1 | 0 (ARC resident; 1881 in full extraction) | Inferred (COMPILED) |
| Garage | 1 | 5 | 1 (20%) | 52.8% | 1 | 0 | 0 | 0 | 0 | Inferred (COMPILED) — weakest row, N=1, treat with suspicion |
| Hospital | 7 | 201 | 85 (42%) | 85.5% | 6 | 9 | 60 (30 positioned) | 0 | 0 (ARC resident; 5391 in full extraction) | Inferred (COMPILED) |

**Stairway/Lift Shaft/Plant Room columns** are from `build/building_parts_taxonomy.js`
(W-BUILDING-PARTS 13/13, MANAGER-verified independently 2026-07-11) — a SEPARATE, entity-based
extraction (`IfcStair`/`IfcRamp`, lift name-keyword match, MEP-density-near-plant-keyword), not the
Gaussian room-type classifier above; these counts are not gated by the "Classified" column. "4 (2
positioned)" etc. means: 4 real entities exist, only 2 carry their own placement transform (an
`IfcStair` assembly parent often has none — only its `IfcStairFlight` children do, a real bug found
and fixed this session, not a missing-data artifact). Plant Room is honestly Terminal-only (n=1
building, advisory not general) — 0 elsewhere means "no evidence found," not "confirmed absent."
**CORRECTED, same day, later than the paragraph above (2026-07-11) — HHS's Stairway=0 was a
DB-snapshot artifact, not a real absence.** The `sparse-wall-room-inference` branch's Phase 0
checkpoint re-verified directly against `deploy/buildings/HHS_Office_Federated_extracted.db` and
found **12 IfcStair + 8 IfcStairFlight = 20 real stair rows**, all `discipline='ARC'`, across 4
real storeys. Re-checked here independently: `/tmp/wt-fable-livewire/modeller/HHS_ARC.db` (this
taxonomy's own source, "LIVEWIRE") genuinely has 0 matching rows — both files are real, on-disk,
non-corrupt SQLite DBs, they have just DIVERGED (deploy/buildings/ dated Jul 6, LIVEWIRE's copy
dated Jul 10, so this isn't simply "the newer one is right" either). **Same landmine already found
once this session on Duplex** (bim-ootb's served `Duplex_extracted.db` vs this repo's
`Duplex_ARC.db` — see `prompts/BUILDING_PARTS_TAXONOMY.md`'s Find-panel-wiring note) — now confirmed
on a SECOND building, meaning this is a standing pattern, not a one-off: **the same building name
resolves to materially different `elements_meta` contents depending on which DB copy/path is
queried.** Every "zero"/absence finding in this lane going forward should name its exact source
path and be treated as "absent from that specific snapshot," never "confirmed absent from the
building" — this scoreboard's own STAIRWAY=0 numbers above (HHS, and any other building) inherit
this caveat. Not re-run against `deploy/buildings/*` for all 8 buildings this session (scope/time);
flagged as a real follow-up, not silently dropped.

**Read honestly, not optimistically:** low classify-rates on the 6 inferred-only buildings (20-63%)
are the classifier correctly REFUSING to force a residential-shaped label onto institutional/
industrial rooms — not a bug. Corridor/restroom counts on inferred rows are a LOWER BOUND, not a
census — the unclassified majority (e.g. Hospital's 116 unclassified rooms) may include real
corridors/restrooms the classifier doesn't yet recognize.

**Confirmed case in point (user's real-world sanity check, verified 2026-07-11): HHS's "0 corridors"
is very likely a FALSE NEGATIVE, not a real absence.** Queried HHS's 79 unclassified rooms directly —
all 79 carry `predefined_type='INTERNAL_DOORPART'` (compile_rooms.py's door-rescue synthetic spaces),
and several are extremely corridor-shaped: aspect ratios up to **37:1**, several in the 6-24:1 range —
far more elongated than Duplex's own HALLWAY template (2.70:1) or FOYER (4.26:1). HHS is an
office-scale building; its circulation spaces are almost certainly larger/more elongated than
anything the residential-trained classifier has ever seen, so they fall outside every template's
band and refuse rather than misclassify. This is the SAME scale-mismatch limitation named generally
above, now confirmed with real evidence for a specific building — treat every "0" or low count on an
institutional building's row with this same suspicion until the classifier has non-residential
ground truth to fit against.

**Another concrete instance, same root cause (2026-07-13, user screenshot `RoomOverSize.png`):**
HHS `≈ Level 2 R9` measures `size_x=2.2m size_y=30.0m` in `spatial_structure` — aspect 13.6:1,
squarely inside the elongated-slivers range already named above.

**CLOSED, 2026-07-13.** R9 is NOT flood-fill/door-rescue (the paragraph above mischaracterized
it) — direct query (`/tmp/wt-fable-livewire/modeller/HHS_ARC.db`, 105 IfcSpace rows) shows R9 and
every other elongated sliver carry `predefined_type='INTERNAL_DOORPART'` — i.e. the
`§DOOR-PARTITION` nearest-door BFS fallback, which only fires when flood-fill structurally fails
(HHS already carries the `SPARSE_WALLS` `§PHASE0-HEALTH` flag — dividing walls are genuinely
missing from this extraction). The 3 old fix directions this doc + `HANDOFF_ghost_xray_rooms.md`
named (wall-inner-face snap, per-face cuboid fallback, cell-outline polygon) all target the
flood-fill bbox path, which no longer has this bug (superseded by `§MULTI-RECT` decomposition) —
they do not apply to `partition_by_doors`'s BFS, which is the actual culprit. **Root cause:**
absent dividing walls, nearest-door BFS assigns one door whatever long undivided free-floor span
it reaches first. Measured HHS's own 105 door-partitioned rooms: clean bimodal aspect spread — 98
climb smoothly 1.00→7.50 (same shape as every other building's genuine rooms), then a hard gap to
7 outliers at 13.64→37.25 (R9 = smallest of the 7). **Fix shipped:** `§SUSPECT-ELONGATED`
(`compile_rooms.py`, user go given 2026-07-13) — new suspect reason scoped ONLY to
`INTERNAL_DOORPART` rooms, threshold `SUSPECT_ELONGATED_ASPECT_MIN=10.57` (measured gap midpoint,
same derivation discipline as every other constant in the file). Zero geometry change — flagged
rooms still compile, just lose element-containment trust the same way `SUSPECT_OPEN`/
`SUSPECT_NO_DOOR` already do. Verified via deterministic synthetic witness (R9-shaped sliver →
flagged; normal room → not flagged; HHS's own real R7 shape, aspect 6.9, below threshold → not
flagged) — could not re-verify against a live re-compile of HHS's own DB because the source that
produced the current 105-row/all-doorpart result has since diverged from `HHS_ARC.db`'s own
`elements_meta` (re-running `compile_rooms.py` fresh against it now yields flood-fill, 33 rooms,
0 door-partitioned) — this is the SAME standing `project_db_snapshot_divergence_landmine` already
documented above, not a new issue. Re-running the fix against whichever snapshot is authoritative
next time HHS is recompiled is the remaining step, not a code gap.

**DEEPER ROOT CAUSE FOUND SAME DAY (2026-07-13, user pushed back after seeing the live Viewer and
insisting the purple box was genuinely floating outside the building, not a camera-framing
illusion — correctly, don't dismiss a direct visual report on a "the numbers look fine" check
alone).** Sampled every cell in R9's own footprint against the compiler's own exterior-
reachability test (the same border-seeded flood `flood_rooms` already runs): **93% of R9's
footprint (1690/1812 sampled cells) is genuinely exterior space**, reachable from outside the
building. `SUSPECT_ELONGATED` above caught R9 correctly, but only because it happens to also be
long and skinny — it flags the symptom (unusual shape), not the actual defect. The real bug:
`partition_by_doors` (unlike `flood_rooms`) never excludes exterior space at all — it floods
through every non-wall cell with zero interior/exterior distinction, so a gap in the perimeter
(e.g. an undetected glass/curtain wall — user's hypothesis, plausible given HHS's already-known
`§WALL-VERT` curtain-wall gaps) lets a door's BFS balloon straight into real outdoor space. **Fix
shipped, same commit series:** `partition_by_doors` now determines exterior topology on the
dilated/sealed wall footprint (same SEAL band `flood_rooms` uses) and applies that mask against
the raw free cells, excluding genuine exterior before a door can ever claim it. Ported to both
`compile_rooms.py` and `build/room_walker.js` (the JS port the Viewer's needle button runs) —
W-ROOM-WALKER-PARITY re-run 6/6 byte-identical after the port. Verified directly: a synthetic room
with a deliberately missing wall segment now claims a bounded ~14.6m² (flagged `OPEN`) instead of
ballooning into the padded exterior; a fully-enclosed control room is unaffected (~33.6m², same as
before). **User also raised two follow-on ideas, not yet built, worth a deliberate look next time
this area is touched:** (1) use floor/ceiling slab footprints as an independent envelope signal
where walls (especially glazing) go undetected; (2) a per-building "uniformity" check — flag a
compiled room as suspect if its SIZE (not just aspect ratio) is a wild outlier against this
building's own room-size distribution, same self-scaling-to-real-data discipline as every other
threshold in this file, just extended beyond aspect ratio.

**Both investigated with real data same day — one built, two honest negatives:**
- **Slab-based envelope signal: NOT built, real evidence it would give FALSE CONFIDENCE.** HHS's
  main Level 2 slab bbox spans `x:[-26.3,31.4] y:[-7.9,36.9]` — R9's entire footprint falls inside
  it, even though 93% of R9 is real exterior space (above). Slab data here is bbox-only, not real
  polygon footprints, and a large slab's axis-aligned bbox spans right across the building's own
  courtyard/notch. A bbox-based slab check would have VALIDATED R9 as legitimate, not caught it —
  worse than no check. Doing this properly needs real mesh polygons (`component_geometries`), a
  much heavier lift than this session's scope.
- **Uniformity/size-outlier: NOT built, real evidence of false-positive risk.** Computed area ÷
  building's-own-median across all 350 rooms in 6 buildings (SampleCastle/HHS/Clinic/Garage/
  Hospital/Terminal) — the single largest outlier by ratio is Clinic's `First Floor R48` at 92.9m²,
  15x that building's own median (6.2m²). Checked directly: Clinic is mostly small exam
  rooms/closets, and a 93m² room is very plausibly a real waiting hall, not a defect. Unlike the
  elongation test (clean gap, 0 false positives across every building), real facilities legitimately
  mix small rooms with a few large halls — a naive ratio threshold would misflag genuine common
  spaces. Not reliable enough as a standalone trigger without a much more careful multi-signal design.
- **Overlap check: BUILT** (user's specific request — "rooms are stacked to each other, not
  overlapping") as a permanent regression guard, `_verify_no_overlap()` in both `compile_rooms.py`
  and `build/room_walker.js`. Checked 773 real compiled rect rows across all 6 buildings: 0
  violations — both compile paths already guarantee disjointness by construction (flood-fill =
  disjoint connected components, door-partition = disjoint BFS ownership). Doesn't surface new
  defects today, but is a precise, zero-threshold invariant worth locking in against a future
  R-MERGE/§MULTI-RECT regression, unlike the two fuzzy ideas above.
- **Also directly verified (user asked "did you miss any potential rooms"):** A/B tested the
  ext-exclusion fix across all 6 buildings by guid — 0 lost, 0 gained, 0 shrunk >50%, on any
  building. Confirms (again) that fix currently has zero observable effect on live data — none of
  today's 6 buildings still trigger door-partition — consistent with the earlier finding, not a
  new gap.

**Do not confuse with (2026-07-13, same screenshot, different bug, already fixed):** the
screenshot also showed a SECOND, larger, stale wireframe box alongside the correctly-sized purple
one — that was a pure rendering bug (`viewer/navigate_find.js` `_drawRoomCuboid()` never disposed
the PREVIOUS selection's highlight mesh before drawing a new one), unrelated to room DATA accuracy.
Fixed + shipped: bim-ootb PR #768 (merged). The room's stored SIZE (R9's 30m sliver above) is
untouched by that fix and remains this doc's open gap.

## Open proposals (named, not yet dispatched — priority is the user's call)
- **OmniClass Table 13 / Uniclass 2015 SL naming-convention mapping** — `config/room_templates.yaml`
  already has a `canonical_type` stub anticipating this. See `COMPETITIVE_PRIOR_ART_ANALYSIS.md`.
- **External dataset integration** (RoomGraph 224 apartments / SAGC-A68 275 apartments, both public,
  properly licensed) — would take the classifier's ground truth from N=2-5/type (1-2 buildings) to
  real statistical power. Real scope decision (licensing terms, schema mapping), not a quick dispatch.
- **Scale-tiered templates (residential vs institutional/office)** — new finding, 2026-07-11: HHS's
  79 unclassified `INTERNAL_DOORPART` rooms include corridor-shaped candidates up to aspect 37:1,
  confirming the single residential-fit template set can't recognize office-scale circulation at
  all. A second template tier (fit from institutional ground truth once available, or a scale-
  normalized feature instead of raw area) would likely recover a real chunk of HHS/Clinic/Hospital's
  unclassified rooms. Not built — the external-dataset proposal below is one path in, but note
  RoomGraph/SAGC-A68 are apartment-focused too and may not close this specific gap.
- **Fixture-in-room recognition** — `IfcFurnishingElement` data already extracted (61 rows,
  confirmed this session) and completely unused by the classifier. Most human-like signal available,
  zero new extraction needed.
- **Graph-joint-inference (label propagation)** over the now-built room-adjacency graph — bootstraps
  from the same small ground truth via measured co-occurrence statistics, no external dataset
  required. Natural stepping stone toward a real GNN once external data lands.
