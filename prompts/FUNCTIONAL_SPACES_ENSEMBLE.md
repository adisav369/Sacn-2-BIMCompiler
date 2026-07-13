# ⚠ DO NOT REMOVE — Scope guard
# SCOPE: ARCH-discipline functional-space derivation (rooms as first-class data across the 8
# embedded Modeller residents) — NOT DISC/MEP walking (that lane lives in
# RESUME_DISC_WALKER_ENVELOPE_BOUND.md, do not merge these). Read the log after EVERY run —
# exit code is not evidence. PUSH PAUSE in effect: commit locally, no push, no PR.

# FUNCTIONAL_SPACES_ENSEMBLE — spatial_structure fleet restore + fixture/raster POCs

## 2026-07-13 — Session spec (written BEFORE implementation, per Spec-First)

### Problem (real, verified — not hypothesized)
`finalize_all_8.js` @ bim-ootb `6068fab` silently shipped the 8 Modeller resident `*_ARC.db`
files WITHOUT the `spatial_structure` table for 6 of 8 buildings (its fresh source was an
ephemeral `/tmp/*_all.db` merge that never had the table; the carry-forward only rescued
Duplex + Terminal). Verified on bim-ootb `main` (`b3462f6`) checkout:

| building     | spatial_structure on main | expected (carry script header) |
|--------------|---------------------------|--------------------------------|
| SampleHouse  | MISSING (no table)        | 6   (ROOM001) |
| Duplex       | present (26)              | — control, never regressed |
| HHS          | MISSING                   | 109 (ROOM002) |
| Clinic       | MISSING                   | 200 (ROOM003) |
| Garage       | MISSING                   | 6   (ROOM004) |
| Hospital     | MISSING                   | 208 (ROOM005) |
| SampleCastle | MISSING                   | 55  (ROOM006) |
| Terminal     | present (49)              | — control |

### Step 1 spec — fleet restore (the real fix)
Apply `prompts/Modeller/DISC_Walker/embed8_scripts/ROOM001..006_*_spatial_structure_carry.sql`
(already witnessed W-SPATIAL-CARRY; honest flood-fill COMPILED rows, mined from real branch
`fable/modeller-lod400-livewire@790b069`, NOT invented) to all six DBs in a fresh bim-ootb
worktree (`/tmp/wt-functional-spaces`, branch `fix/samplecastle-spatial-carry`).

Witness (new `witness_spatial_carry_fleet.js`, node-side, real shipped modules — no re-implementation):
- W1 per building: `SELECT COUNT(*) FROM spatial_structure` == header count.
- W2 per building: `DiscWalker.spacesOf(bdb)` count — REAL shipped `modeller/disc_walker.js`,
  required directly. EXPECTED HONEST RESULT: restored buildings stay 0 — `spacesOf()` excludes
  `RM_%`/`≈` COMPILED rows at the query itself (placement guard, settled 2026-07-10
  `project_room_injection_split_decision`, NOT to be touched). This witness pins that the
  exclusion still holds (no placement leak) while display consumers DO see the rooms.
- W3 per building: `RoomGraph.buildGraph()` (real shipped `common/room_graph.js`, the module
  the Viewer Find panel Path-Between calls) — room node count == IfcSpace rows with center+size,
  edge count > 0 where doors exist, and one real `shortestPath()` between two rooms.
- Browser witness: real Viewer Find panel on the patched DB (`?db=modeller/<b>_ARC.db`),
  §-log room count + a real Path-Between result. SampleCastle deep, others spot-checked.

### Cross-check spec (user's "SampleCastle has cleanest rooms TODAY" observation)
Fetch the ACTUAL served artifacts, don't assume: GH-Pages `modeller/SampleCastle_ARC.db`,
GH-Pages `modeller/patches/SampleCastle_ARC.db.sql`, OCI `buildings/SampleCastle_extracted.db`.
Decide: regression live-visible or dev-only, per app.

### Step 2 spec — fixture-confirmation POC (exploratory, honest)
`classifyRoomWithFixtures()` (bim-compiler `build/room_type_classifier.js`, built earlier
today, 18/18 on Duplex) — re-validate on Duplex ground truth FIRST, then apply to
SampleCastle's 51 carried rooms; report typed-vs-generic fraction, no forcing.

### Step 3 spec — slab-raster leak POC (exploratory, honest)
Reuse `scripts/build_storey_walkable_raster.js` / `common/storey_raster.js` (real triangulated
slab mesh, 0.25m) to measure how many of SampleCastle's 51 room bboxes extend past the real
slab footprint (flood-fill leak through glass/open walls, same shape as §G3-REVISED `073336f`).
Measure + name, don't fix.

---

## 2026-07-13 — Findings (every number below traces to a named log file)

### Step 1 — FLEET RESTORE: DONE, witnessed (the real fix)
Worktree: `/tmp/wt-functional-spaces` (bim-ootb branch `fix/samplecastle-spatial-carry`, off main
`b3462f6`; witness commit `3ddf6cb`, LOCAL ONLY per PUSH PAUSE). All six carry scripts applied;
every count matches its script header exactly:
SampleHouse 6, HHS 109, Clinic 200, Garage 6, Hospital 208, SampleCastle 55.
Patched `.db` binaries are deliberately NOT committed (CLAUDE.md DB policy: binary commits banned
unconditionally — see "shippable fix" below).

**W-SPATIAL-CARRY-FLEET (node, real shipped modules) — 38/38 PASS.**
Log: `/tmp/wt-functional-spaces/witness_spatial_carry_fleet.log` (+ scratchpad `fleet_run2.log`).
Per building (restored + Duplex/Terminal controls):
- W1 carry counts all exact (see above; Duplex 26 / Terminal 49 controls untouched).
- W2 placement guard HOLDS everywhere: `DiscWalker.spacesOf()` returns ZERO `RM_%`/`≈` COMPILED
  rows. **Correction to this brief's own claim (a):** `spacesOf()` does NOT "now see 51 spaces"
  for SampleCastle — the settled exclusion (2026-07-10 room-injection split) lives INSIDE
  `spacesOf()`'s query itself, so it returns 0 for SampleCastle (51 rows all compiled), 0 for
  HHS/Clinic/Garage/Hospital/Terminal likewise, and 3 for SampleHouse (its carried rows are REAL
  extracted spaces with human names, not RM_ rows) and 21 for Duplex. That is correct shipped
  behaviour, verified live — the carried rooms are display/Path-Between data, and the consumers
  that DO read them are `common/room_graph.js` + `viewer/navigate_find.js` (both include COMPILED
  rows by design). Exclusion not touched.
- W3 graph unlock: `RoomGraph.buildGraph()` (the exact module Find-panel Path-Between calls)
  builds real room graphs on all 8 — nodes == queryable IfcSpace rows everywhere
  (3/21/105/197/5/201/51/43), real door-guid edges, real `shortestPath()` per building (Garage
  honestly has 0 room-to-room E1 edges; see UI witness for its legitimate circulation path).

**W-ROOM-PATH-UI-FLEET (real browser, real Viewer Find panel, patched DBs via
`?db=/modeller/<b>_ARC.db`) — 30/30 PASS, zero pageerror.**
Log: `/tmp/wt-functional-spaces/witness_room_path_ui_fleet.log`; screenshot (SampleCastle rooms +
green path polyline + "1 door · 3.5m" result card): `/tmp/wt-functional-spaces/w_ui_fleet_SampleCastle.png`.
Live §-lines per building (room count == carried count, real path with real door guids):
- SampleHouse: nodes=3; path Living room→Entrance hall, 1 door, 7.18m
- HHS: nodes=105; path L1 R1→R36, 2 doors, 9.35m
- Clinic: nodes=197; path 2F R2→R77, 8 doors, 63.07m
- Garage: nodes=5; path R1→R2 via E2 circulation (occupant graph), 2 real doors, 16.86m
- Hospital: nodes=201; path L2 R18→R31, 2 doors, 21.97m
- SampleCastle: nodes=51; path 00 R12→R17, 1 door (D6L `03KwpgDxXCQBYi7KkY7sYB`), 3.50m

**Cross-check — user's "SampleCastle has cleanest rooms in Find panel TODAY": explained, fetched
the actual served artifacts, no assumption.**
- Viewer (where the Find panel lives) loads `buildings/SampleCastle_extracted.db` from OCI —
  fetched it (HTTP 200, 8,040,448 bytes): HAS `spatial_structure`, 25 IfcSpace rows, all `RM_%`
  COMPILED (an OLDER 25-room compile, not this 51-room set). The user's observation is the OCI
  Viewer path, which never regressed.
- Modeller (GH Pages, serves main) `modeller/SampleCastle_ARC.db` — fetched (200, 1,568,768
  bytes): NO `spatial_structure` table; self-heal `modeller/patches/SampleCastle_ARC.db.sql` →
  HTTP 404. So the regression IS live-visible on the Modeller side, dev-checkout AND live; only
  the Viewer/OCI path masks it with the older 25-room binary.
- **Shippable fix already exists and is pushed but UNMERGED:** bim-ootb branch
  `fix/meshdb-selfheal-loader` (`e7384f4`) carries `modeller/patches/<b>_ARC.db.sql` for all six
  (carry + ROOM009-014 wellformed overlays, W-PATCH-SELFHEAL 43/43 recorded in its commit) — the
  loader it needs (`str_walker_outliner.js _applyPendingPatch`) is ALREADY on main (#758). Merging
  that branch is the entire remaining distance to live; blocked only by PUSH PAUSE / user call.
  Note its wellformed overlays supersede the pure-carry sets applied here (e.g. SC 51 rooms incl.
  9 SUSPECT review rows vs this worktree's pure 51-carry) — do not ship both, ship that branch.
- Also confirmed viable: the generic `carrySpatialStructureForward()` (finalize_all_8.js) works
  for all 6 with `priorArc` pointed at `/tmp/wt-fable-livewire/modeller/*_ARC.db` (all sources
  present, counts identical to the SQL dumps — verified by direct sqlite3 count per building).
  The pre-baked SQL scripts were used as the applied mechanism (deterministic, already witnessed).

### Step 2 — Fixture-confirmation POC: method re-validated on Duplex; SampleCastle has NO fixture signal
Script: `build/poc_fixture_ensemble_sc.js` (bim-compiler) · Log: `build/poc_fixture_ensemble_sc.log`.
- **P1 Duplex forward-replay (ground truth first, standing discipline): 19/19 fixture-bearing
  rooms correct, 0 mismatches.** Both `fixture+size` confirmations (Kitchen/Bathroom ×2 units) and
  the symbiotic guard (2 real UTILITY rooms with "Counter Top w Sink Hole" — KITCHEN keyword
  correctly overruled by size, z enormous) reproduce today's recorded behaviour on the patched
  worktree DB.
- **P2 SampleCastle evidence census: ZERO fixture evidence exists in this building's extraction.**
  Fixture-family classes present: 27 `IfcBuildingElementProxy` (13 with transforms), distinct
  names: "ROOT nulpunt", "brievenbussen" (mailboxes), "bellentableau" (doorbell panel), "kozijn"
  (frame). No sanitary/kitchen keyword hits at all (checked the full FIXTURE_KEYWORDS table plus
  wc/bad/keuken/closet Dutch sweeps against all 3,342 elements_meta rows). SampleCastle's ARC
  extraction is walls/coverings/slabs/windows/doors/railings/stairs only — no furniture family.
- **P3 honest result: 0/51 SampleCastle rooms gain a type from fixtures** (0 rooms even contain a
  fixture-family element). Gaussian-only assigns 21/51 a type (9 LIVING_ROOM, 5 BATHROOM,
  5 UTILITY, 2 KITCHEN — UNVALIDATED, no ground truth for this building), 30/51 stay honestly
  UNCLASSIFIED. Conclusion: fixture confirmation is a real, proven signal WHERE furniture exists
  (Duplex-class extractions); it contributes nothing on furniture-free extractions like
  SampleCastle — the wall/door NAME-mining signal (COMPILE_ROOMS_TYPE_INFERENCE.md §1 Signal #1,
  still unbuilt) remains the plausible next signal for such buildings, since walls are universal.

### Step 3 — Slab-raster leak POC: 7/51 SampleCastle rooms extend past the real slab footprint
Script: `/tmp/wt-functional-spaces/poc_room_slab_leak_sc.js` (reuses §G3-REVISED `073336f` slab
placement verbatim, from `/tmp/wt-xray-fixture-fix` — those modules are not on main yet; slab-ONLY
footprint deliberately, since the shipped raster unions room rects and would be self-fulfilling).
Log: `/tmp/wt-functional-spaces/poc_room_slab_leak_sc.log`. RES=0.25m, geometry = resident
element_instances hashes resolved against shared modeller/mesh.db, all slabs mesh-resolved
(0 unresolved, 0 tilted, both buildings).
- **Duplex control first:** 16/21 fully on slab. The 5 with uncovered cells calibrate the noise
  floor for REAL rooms: A201/B201 hallways 33.3% (the stairwell void under the Level-2 hallway —
  a genuine slab hole, not a compile leak), A203/B203 2.1%, Roof R301 1.5%.
- **SampleCastle: 44/51 fully on slab; 7 rooms with cells off-slab.** Named, sorted:
  `≈ 02 tweede verdieping R4` 52.4% (110/210 cells), `≈ 01 eerste verdieping R5` 46.8% (89/190),
  `≈ 03 derde verdieping R3` 23.3% (35/150), `≈ 01 eerste verdieping R1` 8.0% (70/870),
  `≈ 00 begane grond R3` 5.0% (39/782), `≈ 02 tweede verdieping R6` 2.3%, `≈ 02 tweede verdieping R1` 1.8%.
  Reading it against the control: the two ~50% rooms (R4/02, R5/01) have HALF their footprint over
  void — beyond anything the Duplex stair-hole noise produces on a non-circulation room — strong
  flood-fill-leak candidates (the §G3-REVISED failure shape). The ≤8% tail is within the range
  interior slab holes produce on real rooms. Measured and named only — no fix attempted, per scope.

### Session state / next actions (for whoever picks this up)
- Local worktree `/tmp/wt-functional-spaces` holds: patched 6 DBs (uncommitted, correct per DB
  policy), witness commit `3ddf6cb` (2 fleet witnesses), untracked POC script + logs + screenshot.
  NOT pruned — it carries unpushed work (PUSH PAUSE).
- bim-compiler: this file + `build/poc_fixture_ensemble_sc.js` committed locally (no push).
- THE one merge that closes the fleet regression for live users: bim-ootb
  `fix/meshdb-selfheal-loader` (user decision, post-PUSH-PAUSE).
- Open POC follow-ups, honestly parked: wall/door name-mining signal (Step 2 conclusion);
  boundary-clip or re-walk for the 2 heavy leak rooms (Step 3) — spec first if picked up.
