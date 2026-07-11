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
| 8 | Room-type classifier | 5 | Real Gaussian fit, honest confidence, self-consistency miss disclosed | Only 2 buildings (Duplex+SampleHouse) have ground truth |
| 9 | Door-access signal | 4 | 3 genuine real-data wins (Kitchen 0-door correction ×2, Entrance Hall false-match refused) | Net regression if defaulted on — correctly left off, not finished |
| 10 | Primary/supplementary tier + BOM wiring | 7 | Duplex units A/B genuinely satisfy `required_spaces`, reused existing Java validator pattern | SampleHouse fails it honestly (sparse data, not a bug) |
| 11 | OBB clash-gate narrow-phase | 8 | Real 0.0428m false-positive cleared, MANAGER-reran 22/22 independently, 535ns/pair | ~5× per-pair cost vs AABB (small absolute, real regardless) |
| 12 | DISC-walk room-type-aware placement | 8 | PLB→Bathroom/Utility + FP→Foyer real signal wired; ELEC/ACMV honestly refused | Only Duplex has real MEP+room data to measure from |
| 13 | Corridor pathway routing (Find panel) | 9 | MANAGER-reran 15/15 independently; door-guid continuity re-measured, not trusted | Duplex-only proof so far; real disconnections found, not yet resolved |
| 14 | Prior-art analysis + citations | — | Found near-exact academic match (Buruzs et al. 2022) + 2 usable public datasets (RoomGraph, SAGC-A68) | Not yet acted on — still a proposal |
| 15 | Building parts taxonomy (STAIRWAY/LIFT_SHAFT/PLANT_ROOM) | 7 | Real multi-building ground truth for STAIRWAY (5 buildings, Hospital=60 strongest) + LIFT_SHAFT (4 buildings); top-down/bottom-up checklist walks the SAME config either direction; W-BUILDING-PARTS 13/13 PASS | PLANT_ROOM is Terminal-only (n=1); STORAGE/AIR_LIGHT_WELL explicitly refused (zero real evidence anywhere) — not wired into Find panel/Outliner yet |

**Weakest links, name them plainly every time this table is refreshed:** door-access signal (4) and
classifier sample size (5) — both honestly reported, neither hidden or inflated.

## Building coverage (real numbers, queried 2026-07-11 — re-run before trusting after this date)
| Building | Storeys | Rooms | Classified | Avg Confidence | Corridor/Circulation | Restrooms | Stairway | Lift Shaft | Plant Room | Ground Truth |
|---|---|---|---|---|---|---|---|---|---|---|
| **Duplex** | 4 | 20 | 18 (90%) | 89.6% | 4 | 4 | 4 (2 positioned) | 0 | 0 | **Real** (human-labeled IFC) |
| **SampleHouse** | 2 | 3 | 3 (100%) | 86.1% | 1 | 0 | 0 | 0 | 0 | **Real** (human-labeled IFC) |
| Terminal | 6 | 43 | 27 (63%) | 80.8% | 1 | 0 | 33 | 5 | 74 (advisory, n=1 building) | Inferred (COMPILED) |
| SampleCastle | 4 | 51 | 21 (41%) | 93.6% | 0 | 5 | 9 | 67 | 0 | Inferred (COMPILED) |
| HHS | 4 | 105 | 26 (25%) | 95.8% | 0 | 1 | 0 | 3 | 0 | Inferred (COMPILED) |
| Clinic | 3 | 197 | 64 (32%) | 92.9% | 5 | 38 | 9 | 1 | 0 | Inferred (COMPILED) |
| Garage | 1 | 5 | 1 (20%) | 52.8% | 1 | 0 | 0 | 0 | 0 | Inferred (COMPILED) — weakest row, N=1, treat with suspicion |
| Hospital | 7 | 201 | 85 (42%) | 85.5% | 6 | 9 | 60 (30 positioned) | 0 | 0 | Inferred (COMPILED) |

**Stairway/Lift Shaft/Plant Room columns** are from `build/building_parts_taxonomy.js`
(W-BUILDING-PARTS 13/13, MANAGER-verified independently 2026-07-11) — a SEPARATE, entity-based
extraction (`IfcStair`/`IfcRamp`, lift name-keyword match, MEP-density-near-plant-keyword), not the
Gaussian room-type classifier above; these counts are not gated by the "Classified" column. "4 (2
positioned)" etc. means: 4 real entities exist, only 2 carry their own placement transform (an
`IfcStair` assembly parent often has none — only its `IfcStairFlight` children do, a real bug found
and fixed this session, not a missing-data artifact). Plant Room is honestly Terminal-only (n=1
building, advisory not general) — 0 elsewhere means "no evidence found," not "confirmed absent."
HHS's Stairway=0 is suspicious for a 4-storey building — likely the same class of gap as its
corridor false-negative above. **Root-caused, not just suspected (parallel thread,
`prompts/SPARSE_WALL_ROOM_INFERENCE.md`, 2026-07-11): HHS's `elements_meta` has ZERO
`IfcStair`/`IfcRamp` entities at all** — same federated-model sparsity that breaks its wall-based
flood-fill (112 walls/105 rooms = 1.06 walls/room, vs Hospital's 7.16 and Clinic's 5.48). Confirmed
absence in the source data, not an extraction miss on this taxonomy's side.

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
