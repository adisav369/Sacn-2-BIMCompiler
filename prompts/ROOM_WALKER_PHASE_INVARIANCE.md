<!-- Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com> · SPDX-License-Identifier: MIT -->
# ROOM WALKER PHASE INVARIANCE — same walls in, same rooms out, any coordinate frame (2026-07-17)

```
# ⚠ DO NOT REMOVE
SCOPE: make the room compiler translation-invariant. The SAME building geometry, differing only
by a constant coordinate translation, currently compiles DIFFERENT room sets (proven A/B below).
Fix the mechanism in BOTH scripts/compile_rooms.py (canonical, bim-compiler) and its verbatim JS
port build/room_walker.js (bim-compiler), keeping their parity; refresh the deployed copy
bim-ootb viewer/lib/room_walker.js afterwards. Read the log after every run — exit code is not
evidence. Commit locally in each repo, verify by witnesses below, do NOT git push and do NOT
open a PR — report back with §-evidence; the manager session handles push/review.
Never commit a .db binary anywhere (unconditional rule). Every DONE claim needs a §-log line.
User intent (verbatim): "Data is data. Come on, i know about GIGO" + "the whole inject mechanism
is to cure it" → a cure that depends on which coordinate frame the same geometry arrives in is
the bug. User: "Go, fix in new session".
```

## §GIVEN — measured 2026-07-17 (FLY_TOUR_CORRIDOR_GRAPH.md §WALKER-PHASE-SENSITIVITY), do not re-derive
- **G1 — the A/B pair (both on this disk, read-only inputs):**
  - Extracted frame: `~/bim-ootb/buildings/Terminal_extracted.db` (strip its IfcSpace rows for
    the run — copy it to /tmp first, e.g. the same DELETE used for Terminal_norooms:
    `DELETE FROM spatial_structure WHERE type='IfcSpace'; DELETE FROM rel_contained_in_space;`).
  - Imported frame: `/tmp/walker_ab/TerminalMerged_imported_meta.db` (exported from the browser
    import of TerminalMerged.ifc, spatial tables already dropped; regenerable via the scratchpad
    diag_import.js harness if lost).
- **G2 — the data is equivalent**: doors 135 (63/29/27/9/7 per storey), walls 333, windows 236,
  columns 158, bbox stats identical to 2dp, same Unknown-storey rows, hallway backbone identical
  (buckets=52 joined=18 chains=10). Only difference: constant translation Δ≈(−545.6, −51.1,
  −14.7), no mirror, identical spans.
- **G3 — the divergence**: same `room_walker.js` → extracted frame **51 rooms / 79 rects → 26 E1
  door-edges (orphan 36)**; imported frame **45 rooms / 75 rects → 16 E1 edges (orphan 50)**.
  ~6 pockets fail to enclose only in the translated frame; their doors orphan; the occupant
  graph goes thin; the Fly tour rejects (§FLY_ROUTE_REJECT thin-path — user's live report).
- **G4 — algorithm home**: `scripts/compile_rooms.py` is the source of truth;
  `build/room_walker.js` is the verbatim port (dual-mode Node/browser; existing 6/6 parity
  witness convention — find and re-run it). bim-ootb `viewer/lib/room_walker.js` is a deployed
  copy (text file, committable there).
- **G5 — prime suspects (verify, don't assume)**: raster grid origin/extent derivation (does the
  grid snap to absolute coords or to data min? float `floor((x−x0)/RES)` rounding at cell
  boundaries), SEAL dilation interacting with grid phase, and any use of absolute coordinates in
  door-adjacency buffers. The walker runs headless in Node (`require` + sql.js) — iterate there,
  no browser needed.

## SPEC
- **S1 — mechanism first.** Instrument (or offline-dump) both compiles per storey: pocket count,
  each room's rect set, which §DOOR-RESCUE/§DOOR-PARTITION decisions differ. Align frames by the
  measured Δ and name the exact step where the same wall set rasterizes differently. No fix
  before the mechanism is named with numbers (§-log it).
- **S2 — the fix.** Make rasterization translation-invariant, derived from the mechanism found —
  e.g. quantize the grid origin to the data (origin = RES-multiple relative to the storey's own
  min bound, plus an epsilon treatment justified by S1), not a guessed offset. EXTRACT/COMPUTE
  only; no fitted constants beyond what S1 proves necessary. Apply identically to
  compile_rooms.py AND room_walker.js (parity is part of the task, not a follow-up).
- **S3 — witnesses (all must be in the DONE report with §-lines):**
  - **W-FRAME-EQ**: walker output on G1's two inputs is EQUAL after translation alignment —
    same room count, same rect count, rects matching within one grid cell (RES=0.20), and the
    downstream door-edge count equal (26 == 26, orphan 36 == 36). Node script, log the diff table.
  - **W-PY-JS-PARITY**: the existing compile_rooms.py↔room_walker.js parity witness re-run green
    on its standard corpus.
  - **W-NO-REGRESSION**: BEFORE touching anything, capture current walker output (room count /
    rect count / per-room rects hash) for Duplex_extracted, Clinic_extracted, HHS_Office_
    Federated_extracted (all in ~/bim-ootb/buildings/, read-only copies to /tmp). AFTER the fix,
    outputs unchanged OR every change is explained by the named mechanism and improves door
    binding (log both counts). No silent drift on healthy buildings.
  - **W-TOUR (integration, optional but preferred)**: with the fixed walker copied to a bim-ootb
    worktree's viewer/lib/, the scratchpad fly-witness on the imported DB produces
    `§FLY_ROUTE` accepted (no thin-path reject).
- **S4 — deliverable**: commits in a bim-compiler `/tmp/wt-*` worktree (compile_rooms.py +
  build/room_walker.js + this file's DONE section) and a bim-ootb `/tmp/wt-*` worktree
  (viewer/lib/room_walker.js refresh + cache-bust bump). NO push, NO PR. DONE appendix here with
  every witness's §-line.

## Constraints
- `git worktree list` FIRST in both repos; reuse a suitable existing worktree before creating
  one; never edit ~/bim-ootb directly (hook) and prefer a worktree for bim-compiler too (shared
  tree, concurrent sessions).
- LFS caution: don't checkout branches whose LFS blobs aren't local (modeller/mesh.db etc.);
  worktrees off current main are safe.
- Sacred: migration/*.sql untouched; no .db binaries in any commit.
```

## DONE — 2026-07-17 (coder session, logs in /tmp/walker_phase_logs/)

### S1 — mechanism, named with numbers (two findings; G5's suspects verified, one refuted, one confirmed + one found)
1. **G2's premise is REFUTED at element level — the A/B pair is NOT the same geometry in two frames.**
   Guid-join (imported guid = extracted guid minus `T0_Terminal_` prefix; 333/333 walls joined,
   0 storey mismatches): bbox SIZES match to ≤0.0002 m, but element CENTERS scatter around the
   constant Δ — 225/333 walls have >0.05 m residual; displacement runs along each wall's own axis
   (unrotated x-long walls: |rx| up to **1.86 m**, |ry| ≤ 0.04 m), doors ≤0.574 m, windows ≤1.546 m,
   columns ≤0.183 m. In-frame proof needing no join at all: the two 59.925 m Aras-01 facade walls
   are end-aligned within 0.005 m in the extracted frame but offset **0.308 m** in the imported one
   (`diag_sets.log`). Control experiment (`e1_*`/`e1_cmp.log`): frame A translated by EXACTLY the
   modal Δ=(−545.612,−51.219,−14.653) reproduces frame A's compile **identically — 79/79 rects
   matched at 0.0000 m** after alignment, pre-fix walker. So the 51→45-room divergence in §G3 is
   caused by the imported DB's own displaced transforms (browser-import extraction seam), NOT by
   walker phase sensitivity. Attribution table: `e3_attribution.log` (48 matched / 31+27 unmatched
   rects between translated-A and imported-B in the same frame).
2. **The walker nevertheless HAD a real translation knife-edge (G5 suspect confirmed, different
   locus than assumed).** Sweep of 14 constant translations (`e4_sweep.log`): **8/14 changed the
   compile** (rooms 50–54 vs baseline 51; Python identical, 51→52 at Δ=(0.1,0.1),
   `py_[ab]_before.log`). Locus pinned (`e5/e6/e7`): NOT the raster — wall rasters were already
   cell-identical across frames (`e7: wallSpanDiffs=0/210, cellDiff=0`) — but knife-edge
   comparisons of raster-DERIVED absolute metre values, e.g. §DOOR-RESCUE's
   `(wx1-wx0) >= NOISE_FLOOR_DIM` where a 3-cell pocket's span, reconstructed from
   `xs0 + i*RES` in each frame's own dirty floats, flips at exact equality with
   `3*RES = 0.6000000000000001` (door_rescued 7→8 at Δ=(0.1,0.1); Clinic First-Floor pockets
   RM_37/RM_107 measured flipping the other way, `e8_clinic_probe.log`).

### S2 — the fix (both ports, verbatim parity kept)
- **§LOCAL-FRAME** (`compile_rooms.py main()`, `room_walker.js compileRooms()`): every x/y the
  compile touches (per-storey walls/doors/stairs, all-walls/all-doors/all-stairs-z merge-reject
  lists) is rebased to the raster wall set's own min corner (pure EXTRACT) and quantized to
  QUANT=1e-6 m (`floor(v/QUANT+0.5)` in both languages — Math.round/py-round differ on .5 ties).
  Rebased+quantized numbers are BIT-IDENTICAL under any constant translation (measured cross-frame
  FP jitter ≤ ~1e-10 m at |Δ|=1e6), so the whole compile provably cannot see the frame. Rooms are
  un-rebased on emit (writeRooms containment still tests absolute element coords). z untouched.
- **§RASTER-EPS**: cell index = `floor(t + 1e-6)`, grid extent = `ceil(t − 1e-6)` (t in cell
  fractions; 1e-6 cells = 0.2 µm, >100x worst FP error, 5 orders below data) — boundary-exact
  edges quantize identically; and the §DOOR-RESCUE / door-partition noise-floor test now runs in
  INTEGER CELLS (`(mxi-mni+1) >= round(NOISE_FLOOR_DIM/RES) = 3`), the same integer convention
  `_decompose_region` always used — the FP-luck metre form was the S1-measured flip site.
- bim-ootb deploy copy refreshed: `viewer/lib/room_walker.js` (byte-copy of fixed port; it was
  byte-identical to the pre-fix port before, verified vs d159f3023) + cache-bust
  `navigate_find.js` `lib/room_walker.js?v=1` → `?v=2`.

### S3 — witnesses
| Witness | Verdict | §-evidence (log) |
|---|---|---|
| W-FRAME-EQ (same geometry, two frames) | **PASS — bit-equal** | `w_frame_eq.log`: `§WALK-TOTAL [FRAME-A/B] rooms=54 rects=82` both; `§CMP RESULT matched=82 unmatchedA=0 unmatchedB=0 worstMatchedDev=0.0000m`; door edges `§E9 [FRAME-A/B] … edges=28 … orphan=35` — **28==28, orphan 35==35** (the spec's 26/36 were pre-fix levels; equality is the bar — the level moved because the knife edge now resolves deterministically: +2 real E1 edges, −1 orphan). Sweep: `e4_sweep_after3.log` `§E4 SUMMARY deltas=14 nonEqual=0` (was 8/14). Python: `py_[ab]_after.log` 54==54 (was 51 vs 52). |
| W-FRAME-EQ on G1's literal pair | **REFUTED premise, measured** | inputs differ beyond translation (S1 finding 1) — no walker change can equalize them. Post-fix real pair: extracted 54 rooms/28 edges/35 orphans vs imported 46/16/49 (`e9_dooredges.log`); residual gap = the imported DB's own displaced geometry. |
| W-PY-JS-PARITY | **PASS 6/6** | `w_py_js_parity.log`: `§W-ROOM-WALKER-PARITY SUMMARY pass=6 fail=0`, spatial_structure + rel_contained_in_space byte-identical per building (SampleCastle/HHS/Clinic/Garage/Hospital/Terminal). |
| W-NO-REGRESSION (BEFORE captured first: `baseline_before.log`) | **PASS with explained, non-silent changes** | `baseline_after2.log` + `e9_dooredges.log` + `regress_diff.log`. Duplex 10→7 rooms: the 3 dropped were SUSPECT_OPEN door-partition slivers (uncontained review artifacts); L2's 2 REAL rooms survive with IDENTICAL rects (`e8`-style diff in DONE analysis) — flood-fill now finds them (knife-edge dims fixed) so the §DOOR-PARTITION fallback correctly stops firing; doors re-bind room↔CIRC (e2 6→8). Clinic 205→207, E1 174→175, orphan 20→19. HHS 71→73, E1 27→29, orphan 29→28. Terminal 51→54, E1 26→28, orphan 36→35. Imported Terminal 45→46, orphan 50→49. Door binding improves on every corpus; the only removed E1 edges (Duplex 4→0) pointed at SUSPECT artifacts. `§NO-OVERLAP: 0 cross-room overlaps` in all 9 post-fix runs. |
| W-TOUR (integration) | **PASS** | `fly_imported_fixed.log` (scratchpad): fixed walker in /tmp/wt-fly-corridor, imported DB → `§NEEDLE_INJECT … source=walker rooms=46` → `§FLY_ROUTE storeys=6 stops=12/12 skipped=0 …` + two `§FLYPATH_INIT` — accepted, no thin-path reject; FINAL strategy CINE-GRAPH, walkMode=true. |

### Residual (the one real follow-up, NOT a walker item)
The imported-vs-extracted gap that remains (46 vs 54 rooms, 16 vs 28 E1 edges) is the browser
importer's transform extraction displacing element centers along their own axes (S1 finding 1,
up to 1.86 m on walls) — a bim-ootb import-path data seam, out of this spec's scope. Until that
seam is fixed, the walker now guarantees: same geometry in ⇒ same rooms out, any frame.
