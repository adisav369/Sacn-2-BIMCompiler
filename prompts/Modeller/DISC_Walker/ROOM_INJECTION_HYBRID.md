<!-- Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com> · SPDX-License-Identifier: MIT -->
# ROOM INJECTION — hybrid rule (real rooms drive placement, guessed rooms are display-only) + task log

```
# ⚠ DO NOT REMOVE
SCOPE: Standing rule + task tracker for auto-injecting room data into every ARC (extracted or
user-imported), and for closing the "shipped `_ARC.db` has no rooms" gap. Read this before touching
disc_walker.js room handling, compile_rooms.py, or the Modeller Outliner. Read the log of whichever
task is IN PROGRESS below before concluding anything about its status — exit code is not evidence.
ANCHORS: docs/internal/WalkerDoctrine.md §14 (the rule, canonical) ·
SAMPLECASTLE_REAL_ROOMS_RECONCILE.md (closed — SC needs no rooms to walk; lives only on unmerged bim-compiler
branch `docs/session-closeout-2026-07-10`, NOT on this branch — don't expect to open it here, the conclusion
is restated in full at §1 below) ·
prompts/Modeller/DISC_Walker/RESUME_DISC_WALKER_ENVELOPE_BOUND.md (§LIVEWIRE, the schedule-driven walk
this rule feeds) · disc_walker.js `spacesOf()` (both repos — bim-ootb `modeller/`, bim-compiler `build/`) ·
deploy/dev/navigate_find.js `_buildRoomTree()` (the Viewer Find Panel's existing "Room axis" query pattern —
BORROW-FROM source for the Modeller's ARC-import injection step, see Task 5's prior-art note — but it has
NO habitability filter yet, so borrow the pattern only once Task 5's filter is attached, not standalone).
```

## §1 — The decision (settled 2026-07-10, user-confirmed, do not re-litigate)

Every ARC — extracted by us or imported by a user — gets rooms injected automatically, always, split by
source reliability, never by building-name special-case:

- **Real `IfcSpace` in the source IFC** → extract for real, feeds `disc_walker.js`'s per-room schedule-mode
  placement (`dwWalk(disc, bdb, name, {schedule:true})`). Auto, no toggle, no manual step.
- **No real `IfcSpace`** → `compile_rooms.py`'s wall-enclosure flood-fill also runs automatically, but
  ONLY to populate a Modeller Outliner "Rooms" display category (visual/wow-factor). It must NEVER feed
  placement — `spacesOf()` already filters these rows out by design (`guid NOT LIKE 'RM\_%'`, `name NOT
  LIKE '%≈%'`) because they're ~24% recall against ground truth (Duplex: 5/21 correct) — feeding them to
  placement would make results worse than the no-rooms fallback, not better.
- **DISC Walk itself never breaks either way** — verified directly this session: the legacy density walk
  (storey-based, no rooms needed at all) already runs cleanly with real results on a 0-room building
  (`SampleCastle_ARC.db`, ACMV 14 / ELEC 325 / PLB 101 placed, zero refusals). Rooms only improve
  precision on top of an already-working baseline; they are not a prerequisite for the walk to function.

Full reasoning trail: this doc's own earlier scoping was SampleCastle-specific
(`SAMPLECASTLE_REAL_ROOMS_RECONCILE.md`) and concluded SC needs no rooms to walk — that investigation is
CLOSED, not a gap. This file supersedes it for the general (all-buildings, all-imports) room-injection
question.

## §2 — Current state, verified directly (sqlite3, this session, not inferred)

**⚠ CORRECTION (2026-07-10, later same day, MANAGER review):** Terminal's 43 rows were originally recorded
below as "real rooms already baked in" — that was WRONG, caught by direct query. `SELECT object_type,
COUNT(*) FROM spatial_structure WHERE type='IfcSpace' GROUP BY object_type` on the shipped `Terminal_ARC.db`
returns `COMPILED|43` — every row is `compile_rooms.py` synthetic flood-fill output (name pattern `≈ Aras
01 R1` etc.), not a real `IfcSpace`. Harmless in practice (`spacesOf()`'s `≈`/synthetic exclusion already
keeps them out of placement, same as HHS's 14), but the record was factually wrong and is fixed here.

| Shipped `modeller/*_ARC.db` (bim-ootb, 8 total) | rooms | real or synthetic |
|---|---|---|
| Terminal | 43 | **ALL synthetic** (`object_type=COMPILED`, `≈`-named) — never real, corrected above |
| Duplex | 0 on `main`; **20** on unmerged branch `fable/modeller-lod400-livewire` @ `2821b8e` | real, habitability-filtered (Task 5 done — R301 Roof stripped) |
| SampleHouse | 0 on `main`; **3** on unmerged branch `fable/modeller-lod400-livewire` @ `06b6605` | real, habitability-filtered (Task 6 done — "4 - Roof" stripped) |
| SampleCastle, Clinic, Garage, HHS, Hospital | 0 | no `spatial_structure` table |

**Source IFCs on disk (`internal/sources/`) — note: the ARC is a DERIVED artifact of IFC metadata, so
knowing the path alone fixes nothing; it only matters if it turns up an EXTRACTION bug (source has real
rooms, ARC doesn't) vs a genuine SOURCE gap (no rooms exist to extract). Checked directly, this session:**
- `Ifc2x3_Duplex_Architecture.ifc` — 21 `IFCSPACE` entities (matches the 21 already ported/filtered).
- `Ifc4_SampleHouse.ifc` — **4 real `IFCSPACE` entities** (Living room, Bedroom, Entrance hall, Roof) —
  but shipped `SampleHouse_ARC.db` has 0. **This is an EXTRACTION bug, not a data gap** — the source has
  real rooms and they were dropped somewhere in the pipeline. Small enough (4 rooms, 1 of them a Roof the
  habitability filter would exclude anyway → 3 usable) to be a cheap next win once someone re-runs
  extraction on this source with `spatial_structure` capture verified.
- Clinic, Garage, Hospital, HHS — **no source IFC present in this checkout's `internal/sources/` at all** —
  can't be checked either way locally; the "0 rooms" state for these is unverified-source, not confirmed-no-data.

Canonical `deploy/buildings/*_extracted.db` (bim-compiler, 32 total) — only 2 carry real, portable room
data: **Duplex (21 real `IfcSpace` rows, now 20 post-filter on the branch above)**, **HHS_Office_Federated
(14 rows — ALSO corrected here: these are 100% synthetic `compile_rooms.py` output, not real; see Task 5's
own finding in §3 below, which superseded this section's original wrong count but this table hadn't been
fixed to match until now)**. Every other canonical extract (SampleCastle included) has none — SampleCastle's
own true source IFC is untraceable (see the superseded SC doc §3); the only "real rooms" ever produced
under the SampleCastle name were mislabeled Schependomlaan data (same doc §2) — not reused here, would
reintroduce a known mislabel. **Bottom line: of 8 shipped buildings, only Duplex has real, filtered room
data, and it isn't merged to `main` yet — the other 6 (excluding Terminal/HHS's synthetic-only sets) have
zero room data at the SOURCE, which is a data-availability gap, not something the habitability classifier
can fix.**

## §3 — Task log

### Task 1 — Port Duplex real rooms into shipped `_ARC.db`, verify schedule-mode engages, strip non-habitable rows
**Status: ✅ DONE 2026-07-10 (Fable, combined with Task 5 — see §6 results). Witness: W-ROOM-HAB 5/5 +
W-DW-LIVEWIRE 12/12, bim-ootb `fable/modeller-lod400-livewire` @ `2821b8e` (pushed, not merged; the Roof
row is stripped so the branch's earlier merge-blocker is cleared). Historical resume-state below kept for
the record.**

**Original scope (given to the killed agent):** port `spatial_structure` from canonical
`Duplex_extracted.db`/`HHS_Office_Federated_extracted.db` into shipped `Duplex_ARC.db`/`HHS_ARC.db`
(bim-ootb, worktree-only, hook-blocked on the shared checkout), prove `dwWalk(..., {schedule:true})`
engages, push (not merge).

**What actually happened, in order, all confirmed directly (not agent-reported, independently checked
where noted):**
1. Original brief wrongly included HHS as "14 real rooms" — corrected mid-flight (see Task 5 below): all
   14 of HHS's `spatial_structure` rows are 100% synthetic `compile_rooms.py` output (`RM_`/`≈`/`COMPILED`
   tagged), not real. Sent the agent a correction dropping HHS from scope entirely, Duplex-only from that
   point.
2. Duplex's real 21 rows include one non-habitable entry, `R301 | Roof | object_type='Roof' | size
   7.97×16.97×3.0m` — flagged to the agent, told to filter it (and audit for any other non-habitable
   `object_type` values) before porting.
3. **Before the agent could act on either correction, it reported back a prior-state finding:
   `Duplex_ARC.db`'s `spatial_structure` was ALREADY present, unfiltered (Roof row included), on bim-ootb
   branch `fable/modeller-lod400-livewire` @ commit `670bf0f`** — pushed to `origin`, NOT merged to `main`.
   This predates this task entirely; it's a leftover from the earlier §LIVEWIRE work
   (`project_disc_walker_grid_guard_marathon_2026-07-10.md`), not something this agent or task introduced.
4. **HHS confirmed untouched/clean** — md5-identical to the `main`-checkout copy, no `spatial_structure`
   table added. No bad data was written anywhere by this task.
5. Agent was killed (user command) immediately after step 3's finding, before it stripped the Roof row.

**Exact state for the next session to resume from:**
- `main` (both repos): unaffected, nothing to undo there.
- bim-ootb branch `fable/modeller-lod400-livewire` @ `670bf0f`: has `Duplex_ARC.db` with a real but
  UNFILTERED 21-row `spatial_structure` (20 genuine rooms + 1 `Roof` void that must NOT be treated as a
  room). **This branch cannot merge to `main` as-is** — the Roof row needs stripping first.
- HHS: leave alone entirely, in every branch. Not part of this task.
- Not yet done, still needed: (a) strip the `Roof` row (and confirm no other non-habitable `object_type`
  hides in the same 21) from `670bf0f`'s `Duplex_ARC.db`, (b) prove `dwWalk(..., {schedule:true})` engages
  on the cleaned 20-room set specifically (not the tainted 21 — a witness run against unfiltered data would
  prove nothing about whether the filter works), (c) push the fix as a new commit on top of `670bf0f` (or a
  fresh branch off it — Fable's call), (d) this task's original guid/coordinate-overlap verification step
  was never confirmed either way by the killed agent — still needs doing, don't assume it passed.
- This task and Task 5 (room recognition) are now the same piece of work in practice — the Roof-filtering
  needed here IS Task 5's habitability classifier, applied to its first real case. Fable should treat them
  as one assignment, not sequence them as two.

### Task 2 — Wire `compile_rooms.py` into extraction/import as an automatic step (tagged `≈`, display-only)
**Status: DATA-EFFECT DONE 2026-07-10 (Sonnet), AUTOMATION still NOT STARTED.** Ran
`compile_rooms.py --write` manually (a one-time repair, same treatment level as Tasks 1/6's manual ports)
against the 5 shipped residents that had ZERO `spatial_structure` at all: SampleCastle, HHS, Clinic,
Garage, Hospital — each compiled from that building's OWN real wall/door geometry already in its
`elements_meta`/`element_transforms` (deterministic flood-fill, not invented). Combined with Duplex (20
real) + SampleHouse (3 real) + Terminal (43 synthetic, pre-existing), **all 8 shipped `*_ARC.db` now
carry room data — §1's "every ARC, always" bar is met for the current data.**

**§DOOR-RESCUE (user-flagged, fixed twice this session — record both passes so the reasoning isn't
lost):** user checked HHS ("large U-shape office block, lots of office rooms with doors") against the
first pass's count and correctly called it wrong — 2 rooms for a whole office floor. Root cause verified
(not assumed): NOT a mis-tuned `MIN_AREA=4.0` hiding detected-but-rejected rooms — instrumenting the
flood-fill to log every candidate (not just accepted ones) showed HHS's floors are ~91% "exterior-
reachable" in the flood-fill; there's genuinely very little enclosing wall data to divide the floor with
(76-84 wall-like elements over a ~66×54m floor, vs. Hospital's 582 producing 32 real rooms on a
comparable floor). Separately, restrooms/utility rooms were being dropped by the SAME blanket
`MIN_AREA` cutoff on every building, not just HHS — the user's hint ("rooms has doors") pointed at the
fix: a real small room always has a door, a wall cavity never does.
- **First pass (superseded):** added a fixed `DOOR_RESCUE_MIN_AREA=1.0` / `1.0m` buffer band — picked by
  eyeballing HHS/Clinic/Hospital's specific candidate-pocket data. User pushback (correct): "we suppose
  to be abstract rules based room generation, not hard-coded."
- **Second pass (shipped):** replaced the fitted band with an architectural rule applied uniformly, not
  size-banded — a pocket is a room if big enough alone (`area >= MIN_AREA`, unchanged) **OR** it has a
  real door and clears a grid-resolution noise floor. Both supporting numbers are now geometry-derived,
  not observed-data-fitted: the door-adjacency buffer is each door's OWN extracted footprint (half its
  real leaf/frame span) + one grid cell of rasterization slack (`RES`), and the noise floor
  (`NOISE_FLOOR_DIM = 3×RES = 0.6m`) rejects any pocket narrower than a few grid cells in either axis —
  a property of the flood-fill's own resolution, true for any building, not a number reverse-engineered
  from this one's rooms. `predefined_type='INTERNAL_SMALL'` marks door-rescued rows for traceability;
  `object_type='COMPILED'` (what every tag-purity/placement-exclusion check keys on) is unchanged.
- **MEP-fixture clue considered, rejected:** checked whether sanitary/plumbing fixture presence (a more
  direct "this is a toilet" signal) could help — found only Clinic still carries `IfcFlowTerminal`
  (102 rows); HHS/Hospital/Garage/SampleCastle have zero MEP fixture data at all (already stripped by the
  ARC-only discipline strip, `b93ca13`). Not usable as a general signal — door adjacency is the one clue
  that survives ARC-only extraction on every building.
- **Third pass — user found HHS's doors/walls are German-named (Revit export), e.g. `Türelement
  2-flg - Drehflügel - Glas` (2-leaf glazed swing-door) and `WC Trennwand 5.0` (WC partition wall,
  50mm) — real evidence the building genuinely has a restroom, checked directly. But the specific
  WC block (walls at x 9.8-10.5, y 35.5-37.4) turned out NOT to be among the compiled rooms even
  after door-rescue: instrumenting the flood-fill grid cell-by-cell showed the ENTIRE region comes
  back fully wall-blocked pre-flood — the individual stall gaps (~0.9-1.0m between 50mm partitions)
  are real but too narrow for `SEAL=2`'s 0.4m dilation to leave any free cell for the flood-fill to
  find, let alone enclose. Tested `SEAL=1` (0.2m) as the fix, per user's chosen path (re-verify all
  5, no regression before shipping) — **rejected on direct evidence**: it did NOT recover HHS's WC
  (still 6 rooms — the WC block's own walls separating it from the general office floor, not just
  its internal stall partitions, are simply absent from this extraction; a data-completeness gap,
  not a dilation-tuning one) AND it fragmented real rooms elsewhere into spurious 1-2 m² splinters
  (Hospital 142→253 mostly noise, SampleCastle inflated too) — reverted to `SEAL=2`, unchanged.
  HHS's WC gap stays open; the honest cause is now on record instead of papered over.
- **Fourth pass — user asked whether doors have standard real-world dimensions usable as a "smell
  test."** Checked door-width distributions across all 5 buildings against known hinged-door
  conventions (~0.7-1.1m single leaf, ~1.2-2.7m double leaf/wide entrance) — all 4 buildings with
  door data are clean and consistent with this range, EXCEPT SampleCastle: 28 `IfcDoor` rows at
  0.5m, all named `liftdeur` (Dutch: elevator door) — real, correctly-classified doors, but they
  lead to a lift shaft, not a room. Verified directly: 2 of them were rescuing actual elevator-shaft
  fragments as fake compiled "rooms." Same shape of problem as the existing §STAIR-EXCLUDE pattern
  — fixed with a maintained, multi-language door-name exclusion list (`liftdeur/lift/elevator/
  aufzug/fahrstuhl/hoist`), not a width cutoff (a lift door's width isn't reliably distinct from a
  narrow single-leaf door's). SampleCastle 53→51 (the 2 fakes removed); other 4 buildings unchanged.
- **Fifth pass — user would not let the HHS number go ("how many times must i tell you", "each door
  must be to a room", "and told u not to stop until u solved it") — correctly: 6 rooms for 3 floors
  averaging ~39 real doors each was never a defensible answer, it was a symptom of flood-fill's
  precondition (wall enclosure) not being satisfiable from HHS's data, not a size/threshold problem
  door-rescue could patch further. Built §DOOR-PARTITION: where flood-fill (with door-rescue applied)
  finds far fewer rooms than the storey has real doors, partition the storey's FREE space by NEAREST
  DOOR — multi-source BFS through free cells, real walls still block, each door claims whatever space
  no other door reaches first (literally "every door leads to a room," not a proxy for it). Gate
  (`DOOR_SHORTFALL_RATIO=0.15`) is measured, not fitted per-building: HHS's floors find 0-11% of their
  door count via flood-fill; every other building's WORKING floors find 20-100%+ (Garage's sparsest:
  5/8=62%; Hospital's sparsest: 1/5=20%) — the ratio sits cleanly between "failing" and "working,"
  verified directly so it never overrides an already-functioning floor. `predefined_type=
  'INTERNAL_DOORPART'` marks these rows for traceability.
- Final counts (bim-compiler `scripts/compile_rooms.py` @ `f87f41ed7`): SampleCastle 25→51,
  HHS 2→**105** (36/31/29/9 per floor — the "10s × floors" range the user predicted from door counts
  alone), Clinic 113→197, Hospital 142→201, Garage unchanged at 5 (its one working floor's 5 real
  rooms correctly preserved, not overridden).

Witness `witness_room_injection_all8.js` (W-ROOM-INJECT-ALL8) 32/32 (rerun after every pass): coverage,
tag-purity (no partial RM_/≈/COMPILED tagging on any of the 5), zero `elements_meta` guid collisions, and
— the one that matters most — the REAL `spacesOf()` runtime filter returns 0 placement-eligible rows for
all 5 synthetic-only buildings, HHS's 105 door-partitioned rows included (proves display-only rooms,
however they were compiled, can never leak into schedule placement). Shipped bim-ootb
`fable/modeller-lod400-livewire` @ `790b069` (parent `a8955f2`, pushed, `rev-list origin..HEAD`=0) +
bim-compiler `fable/meshdb-livewire` @ `f87f41ed7` (pushed).

**Still open, on record:** the door-partition technique gives HHS a plausible per-door room COUNT and
rough footprint, but not a true wall-bounded shape (no walls exist in the data to bound it with) — it's
a Voronoi-style nearest-door claim, one step more approximate than flood-fill's wall-enclosed rooms.
Good enough for the Outliner "Rooms" display category (still 100% excluded from schedule placement,
verified above); a real fix for HHS's actual wall/room geometry would need re-extraction with the
partition walls properly captured, not attempted here.

**Still NOT DONE — this task's own automation half:** wiring `compile_rooms.py` as a step INSIDE
`extractIFC2DB.js` (or whichever path runs it) so a FUTURE re-extraction or a user's live IFC import gets
this for free, instead of needing another manual repair pass like this one. Currently a manual script
(`compile_rooms.py <db> --write`), never called from any build/embed/import path (confirmed via grep —
zero automatic callers, still true after this session's manual run). This is the regression risk Task 4
already named for the strip cascade, generalized: without automation, ANY future re-embed (the exact kind
that caused Task 6's SampleHouse gap, via `6068fab`'s finalize_all_8.js never carrying `spatial_structure`
forward) can silently wipe this same data again.

### Task 3 — New Modeller Outliner "Rooms" category (the display/wow-factor surface)
**Status: NOT STARTED, NOW SPECCED with a concrete trigger — see
`prompts/Modeller/DISC_Walker/ROOM_WALKER_JS_PORT.md` (2026-07-11).** This category's DATA source
was originally left open ("whichever rooms exist in `spatial_structure`") — the JS-port doc gives
it an explicit ACTION too: a "Room Walker" Outliner trigger (parallel to the existing Disc Walker
convention) that runs the ported compile-rooms algorithm on demand for buildings with no room data
yet (a user's own imported IFC), never automatically on open. Read that doc for the actual task
breakdown before starting this one — it supersedes the vague "sit alongside those" note below with
a real design.
No existing room display anywhere in the Modeller (checked — `grep -rl room` across `modeller/*.js`
only matches `disc_walker.js` itself). Pattern to follow: the existing Disc-tab-follower shape
already used for STR and walked-fixtures categories (`bom_tree_outliner.js` / `dw_instances_outliner.js`
/ `str_walker_outliner.js`) — a "Rooms" category would sit alongside those, sourced from whichever
rooms (real or `≈`-tagged) exist in the open building's `spatial_structure` table.

### Task 4 — Carry real `IfcSpace`/`spatial_structure` through the ARC-only strip step
**Status: ✅ DONE 2026-07-11 (Sonnet). Witness: W-SPATIAL-CARRY 9/9 checks across 6 scenarios.**

**Reframed before coding, per Task 6's own finding (checked, not re-guessed):** the original brief named
`b93ca13` (the discipline strip) as the regression source — Task 6 already disproved that directly
(`spatial_structure` was UNCHANGED across `b93ca13`, 7 rows before/after for SampleHouse). The real,
confirmed regression path is one commit later: `6068fab` ("embed 8 ARC-only buildings + shared mesh.db
registry"), specifically its consolidation pipeline `prompts/Modeller/DISC_Walker/embed8_scripts/
finalize_all_8.js`. That script's per-building meta-split (`SELECT name, sql FROM sqlite_master WHERE
type='table' AND name != 'component_geometries'...`) copies whatever tables exist in the fresh source —
generic, not a table allowlist — so it never explicitly DROPS `spatial_structure`; it silently SHIPS
EMPTY whenever the fresh source (built from an ephemeral `/tmp` merge step, `_all.db`, never preserved —
confirmed gone, not on any branch) happens to lack the table. This is confirmed the actual mechanism
for 7 of 8 buildings' room-data loss (only Duplex/Terminal survived, both non-`_all.db` special cases).
Fable's `fable/modeller-lod400-livewire` branch worked around this per-building (Task 1/2/6, manual
`--write`/port passes into the shipped `_ARC.db` after the fact) — real, verified data, but the pipeline
itself stays fragile: the next re-embed silently regresses it again exactly the same way, same as it did
the first time (2026-07-11 user directive: fix the pipeline, don't keep re-patching one building at a time).

**Fix — `finalize_all_8.js` hardened, not the ephemeral merge step (that source is unrecoverable this
session; hardening the one script that DOES run for every future re-embed is the tractable, in-scope
fix):**
- `hasTable(db, name)` / `countRows(db, table)` helpers (schema-safe — sql.js throws on `SELECT` against
  a missing table, so a presence check must precede any count).
- `carrySpatialStructureForward(freshDb, metaDb, priorArcPath, SQL)`: if the fresh source's
  `spatial_structure` is empty (table missing OR 0 rows), and a prior shipped `{Name}_ARC.db` exists at
  `priorArcPath` with rows, copies that table's DDL + full row set verbatim into `metaDb` — schema-driven,
  not a hand-typed column list, so it can't drift from whichever columns the source table actually has.
  If the fresh source already has rows, fresh wins (assumed newer/more authoritative) and carry-forward is
  a no-op. Every building logs `§SPATIAL-STRUCTURE-CARRY building=X fresh_rows=N final_rows=M source=...`
  — the loud, explicit survivor-check this task's brief asked for, replacing the prior silent behavior.
- Each `buildings[]` entry (+ Terminal's special-cased load) now carries a `priorArc: HOME +
  '/{Name}_ARC.db'` fallback — "whatever this working tree's currently-shipped resident already has" is
  the correct floor for a re-embed to never regress below, regardless of which branch/session produced it.
- Final regression gate before any file is written: any building whose `spatial_structure` ends at 0 rows
  despite EITHER source having had rows available is logged `§SPATIAL-STRUCTURE-REGRESSION` and the run
  aborts non-zero — a genuine data-availability gap (a building nobody has ever produced room data for)
  still logs 0 and proceeds, since that's not a regression, just an unmet Task 2 prerequisite.
- `module.exports` + `require.main` guard added so the new functions are unit-witnessable without needing
  the vanished `/tmp` scratch inputs to run the full pipeline end-to-end.

**Witness `witness_spatial_structure_carry.js` (W-SPATIAL-CARRY, node-side, §-log-first), reproducing the
exact historical failure and proving the fix, run against REAL data
(`/tmp/wt-fable-livewire`'s shipped `_ARC.db` files — read-only, no LFS fetch, already-local worktree):**
W1 fresh-has-data → fresh wins, no carry, metaDb correctly untouched (21/21 — 2 checks). W2 reproduces
`6068fab`'s exact Duplex regression shape (fresh source built with NO `spatial_structure` table at all,
same as the real ephemeral `_all.db` must have been) → carried forward verbatim from real
`wt-fable-livewire/modeller/Duplex_ARC.db`'s FULL `spatial_structure` table (25 rows — 21 `IfcSpace` +
4 storeys, not the IfcSpace-only 21/20 count quoted elsewhere in this doc; carry-forward copies the whole
table schema-driven, correctly including non-space rows) — 2 checks. W3 same for SampleHouse (Task 6's
real post-strip case) → carried forward, 6 rows (3 kept `IfcSpace` + 2 storeys + 1 building, matches Task
6's own H5 strip result exactly), guid-level content match against source, not just count — 2 checks.
W4 neither source has data (legitimate gap, not a regression) → logs 0, no crash, no false-positive
abort. W5 `priorArc` path undefined/unreadable → degrades to 0 gracefully, defensive. W6 the
regression-gate itself, reproduced in miniature → a case where `carry()` reported data available but the
write-time db disagrees (simulates drift/a future bug) → gate correctly fires, proving it isn't a no-op.
9/9 checks, log read not inferred: `/tmp/claude-1000/-home-red1-bim-compiler/885d136b-04e3-4b17-8666-279c6b28f522/
scratchpad/w_spatial_carry.log`.

**Follow-up — main's data gap closed WITHOUT a binary push (user directive, 2026-07-11: "don't touch
mesh.db/ARC.db as a binary commit — write a migration-style SQL script, repo convention `migration/DV_*_
rules.sql`/`CL002_name_value_component_library.sql`, pick a fitting prefix").** bim-ootb `main`'s 6 empty
residents + Duplex's unfiltered 26th row (the actual data gap left after the script hardening above) are
now closable via 7 committed SQL scripts in this dir — `ROOM001`–`ROOM006_{Name}_spatial_structure_carry.sql`
(full `spatial_structure` dump — DDL + `INSERT` rows — read straight off `fable/modeller-lod400-livewire`'s
real, already-verified data via `sqlite3 .mode insert`, no BLOB columns in this table so no hex-literal
complexity) and `ROOM007_Duplex_strip_roof.sql` (single `DELETE` — confirmed by direct diff that main's 26
rows differ from livewire's 25 by exactly the R301 Roof row, nothing else). Terminal needs no script —
confirmed byte-identical between main and livewire already.

Each script applies via `sqlite3 modeller/{Name}_ARC.db < ROOM00N_....sql` against anyone's existing local
main checkout — text only, no LFS fetch, no binary ever crosses the network. **Witness
`witness_room_migration_apply.sh` (W-ROOM-MIGRATION-APPLY, committed, re-runnable) 7/7:** copies each real
main `_ARC.db`, applies its script, diffs the FULL resulting `spatial_structure` table (not just row
count) against `fable/modeller-lod400-livewire`'s real data — byte-identical on all 7. Log:
`/tmp/claude-1000/-home-red1-bim-compiler/885d136b-04e3-4b17-8666-279c6b28f522/scratchpad/
w_room_migration_apply.log`.

**Still NOT done, correctly out of scope:** this gets `main`'s LOCAL checkout on par with the livewire
branch's room data (for anyone who pulls this commit and applies the scripts) — it does not deploy to the
live GitHub Pages site (needs the actual `git commit` of the mutated `*_ARC.db`, still LFS-blocked until
2026-08-01) and does not re-run the full embed-8 pipeline end-to-end (the vanished `_all.db` merge step
would still need rebuilding for a genuine from-source re-embed — a separate, larger task).

### Task 5 — Room RECOGNITION: real IfcSpace still needs a habitability filter before it's trusted
**Status: ✅ DONE 2026-07-10 (Fable) — `spaceHabitable()` shared classifier in bim-ootb
`modeller/disc_walker.js`, wired into `spacesOf()` (both real-source paths) and exported for port/injection
steps. Proven on Duplex's hand-verified known-21: 20/20 habitable kept, exactly R301 Roof excluded
(`label:ROOF`; independent `zband:8.91>6.67` geometry signal verified by falsifier). W-ROOM-HAB 5/5 —
see §6 results. Follow-up (named, not done): port to the diverged source copy bim-compiler
`build/disc_walker.js`, and into the Viewer (separately-scoped per this doc).**

Found live, this session, by direct query (not theoretical): "real" `spatial_structure` data is not
automatically safe to treat as "a room."

- **HHS_Office_Federated_extracted.db's 14 `spatial_structure` rows are NOT real** — corrected from this
  doc's own earlier §2/§3, which wrongly counted them as real portable room data. Direct check:
  `SELECT COUNT(*), SUM(guid LIKE 'RM\_%'), SUM(name LIKE '%≈%'), SUM(object_type='COMPILED') FROM
  spatial_structure WHERE type='IfcSpace'` → **14/14/14/14** — every row is `compile_rooms.py` synthetic
  output (`RM_` guid, `≈` name, `COMPILED` object_type), not extraction. Task 1 was corrected mid-flight to
  drop HHS entirely — Duplex is the only building with confirmed real, portable room data right now.
- **Even Duplex's genuinely real 21 rows aren't all habitable rooms.** Found: `R301 | Roof | object_type=
  'Roof' | size 7.97×16.97×3.0m` — a roof void, correctly extracted as a real `IfcSpace`, but not a room
  a DISC discipline should schedule-place fixtures into the way it would a Bathroom or Bedroom. The other
  20 rows all look legitimately habitable (Foyer/Living Room/Kitchen/Bathroom/Bedroom/Hallway/Utility/
  Stair) but this was a spot-check, not an exhaustive audit — the same non-habitable pattern (roof, shaft,
  plant room, external/podium space, parapet/sill space — real `IfcSpace` entities architects sometimes
  tag for non-occupiable volumes) could exist in ANY future real extraction, not just Duplex.

**The actual problem to solve:** a reliable habitability classifier for `spatial_structure`/`elements_meta`
`IfcSpace` rows — real-vs-synthetic is already solved (`RM_`/`≈`/`COMPILED` tagging, don't touch that part).
What's missing is real-vs-non-habitable-real: telling a genuine Bathroom from a genuine Roof/Shaft/Sill
space when BOTH are honestly-extracted `IfcSpace` data with no synthetic tag to lean on.

**Starting points for whoever (Fable) picks this up:**
- `object_type` (Duplex's case) / `LongName` (per `spacesOf()`'s own comment, the space-TYPE key) is the
  first signal — but it's free text from the source IFC, not a controlled vocabulary; needs a maintained
  exclude-list (Roof, Shaft, Void, Plant, External, Podium, Sill, Parapet, Balcony-if-not-enclosed, etc.)
  reviewed against real data across multiple buildings, not guessed from one building's 21 rows.
  Existing code already has ONE piece of this pattern: `compile_rooms.py`'s `STAIR_EXCLUDE` logic
  (rejects a compiled pocket if a stair footprint covers ≥35% of its area) — same shape of problem
  (structurally-enclosed-but-not-a-room), solved once for the synthetic path; the real-`IfcSpace` path has
  no equivalent filter yet.
- Geometry-based sanity checks may help independent of naming — e.g. a room whose z0/z1 band sits above
  the building's topmost habitable storey, or whose footprint matches the building's roof/envelope
  footprint rather than an interior partition, is a strong non-room signal regardless of what it's named.
- Whatever filter is built must run BEFORE real rooms are ported into any shipped `_ARC.db` (Task 1) AND
  before `disc_walker.js`'s `spacesOf()` trusts them for placement — same enforcement point, so it likely
  belongs as a shared helper both call, not two independent filters that can drift apart.
- Witness-first per project standing rule: prove the filter's precision/recall on Duplex's known-21 (where
  the answer — 20 real rooms + 1 Roof — is already hand-verified above) before trusting it on any other
  building's real `IfcSpace` data.

**⚠ SCOPE: this task is MODELLER-ONLY. The Viewer is cited below strictly as a READ-ONLY reference/evidence
source — its own query pattern and its own bug. Do NOT touch, wire into, or modify anything under
`deploy/dev/navigate_find.js` or any other Viewer file for this task. If a future shared-helper refactor
ever pulls the Viewer in, that is a separate, explicitly-scoped task, not an implicit side-effect of this
one.**

**Borrow-from source (read-only reference), AND confirmed prior art with the SAME unfixed gap (user flagged
this correctly from the start, this session — checked, confirmed real):** the Viewer's own Find Panel Room
Lens (`deploy/dev/navigate_find.js` `_buildRoomTree()`, wired to the Find Panel's "Room axis" pill
`#find-axis-room`, witnessed by `deploy/dev/tests/specs/41-room-volume-lens.spec.js`'s §LENS_PROBE/roomVol
checks) already queries and displays rooms today — `SELECT guid, name FROM spatial_structure WHERE
type='IfcSpace' AND center_x IS NOT NULL`. **Read this query as the pattern to follow when building the
Modeller's ARC-import auto-injection step (Task 1 / WalkerDoctrine.md §14)** — copy the shape of the query
into Modeller-side code, so every embedded ARC gets rooms the same way the Viewer already surfaces them,
instead of a second, divergent query being invented from scratch. This is a one-way READ: look at the
Viewer's code to know what to build in the Modeller; nothing in the Modeller build calls back into it.

**Borrow the pattern, not the bug: the Viewer's query has ZERO habitability filtering, and is itself a live
false positive — evidence for why the Modeller's copy must NOT be built the same naive way.**
`type='IfcSpace' AND center_x IS NOT NULL` is just as true for Duplex's `R301 | Roof` row as for a genuine
Bedroom — the Viewer's Room Lens would show, and today DOES show, "Roof" as a room. This is a FALSE POSITIVE
for "habitable room," not a syntax bug — the query correctly finds a real, non-synthetic `IfcSpace`; it's
simply not a room a DISC discipline should schedule-place fixtures into, or a user should see in a room
list. If the Modeller's ARC-import step copies this query's shape WITHOUT also building Task 5's
habitability filter first, it reproduces the identical false positive on the Modeller side. **Sequencing:
Task 5's classifier must exist and be proven on Duplex's known-21 BEFORE the query pattern is copied into
ARC-import — filter first, then build the import step, not the query alone.**

**Fixing the Viewer's own copy of this false positive is explicitly OUT OF SCOPE for this task.** It is
named here only as corroborating evidence that the false-positive risk is real and already observable, not
as a second deliverable. Once the Modeller-side classifier exists and is proven, porting it into the Viewer
is a distinct, future, separately-scoped task — do not fold it into Task 5's Modeller work.

## §5 — Assignment

Task 5 (room recognition/habitability classification) WAS assigned to Fable (execution lane) and Tasks 1+5
were executed as one assignment, ✅ DONE 2026-07-10 (see §6 RESULTS) — that history stands as-is.

**⚠ FROM 2026-07-10 (later same day), ALL FURTHER WORK ON THIS DOC GOES TO SONNET, NOT FABLE (user
directive — no reason logged, just apply it).** Tasks 2–4 remain NOT STARTED. New:

### Task 6 — SampleHouse extraction bug: 3 real, usable rooms exist in source but never reached the ARC
**Status: ✅ DONE 2026-07-10 (Sonnet). Witness: W-ROOM-HAB-SH 6/6, bim-ootb
`fable/modeller-lod400-livewire` @ `06b6605` (parent `2821b8e`, pushed, `rev-list origin..HEAD`=0).**

**Root cause — checked directly before writing any code, per this task's own instruction:** NOT the same
cause as Task 4's `b93ca13` cascade. Direct diff of `modeller/SampleHouse_extracted.db` across `b93ca13`
(`git show b93ca13^:...` vs `git show b93ca13:...`) shows `spatial_structure` UNCHANGED — 7 rows (1
`IfcBuilding` + 2 `IfcBuildingStorey` + 4 `IfcSpace`) both before and after that commit. The strip did not
touch rooms for SampleHouse at all. The actual cause is the LATER commit `6068fab` ("embed 8 ARC-only
buildings + shared mesh.db resident registry", one day after `b93ca13`) — its consolidation pipeline
(`embed8_scripts/finalize_all_8.js`, fed by an intermediate `_all.db` merge step that ran only in `/tmp`
and was never preserved, per `74c27b371`'s own commit note) produced every shipped `*_ARC.db` WITHOUT a
`spatial_structure` table at all — confirmed a general embed-pipeline gap, not SampleHouse-specific:
`main`'s `Duplex_ARC.db` has the identical 0-row gap (matching this doc's own §2 table, pre-Task-1). So
Task 4 (hardening the strip cascade) would NOT have fixed this — the table survived that cascade fine and
was only lost one commit later, in the ARC.db schema the embed step writes.

**Fix:** ported `spatial_structure` verbatim from `SampleHouse_extracted.db` (source is intact, verified
above) into the shipped `SampleHouse_ARC.db` (had none), ran Task 5's `spaceHabitable()` against the known-4
— `4 - Roof` excluded (`label:ROOF`), 3 kept (`1 - Living room`, `2 - Bedroom`, `3 - Entrance hall`) —
stripped the Roof space by classifier verdict (the `IfcBuildingStorey` named "Roof" survives, same
convention as Duplex's R301). Notable, reported honestly rather than assumed: unlike Duplex, this
building's independent zband geometry signal does NOT also fire for the Roof space — SampleHouse_ARC.db's
own real ARC roof members/plates reach z1=4.50, taller than the Roof space's z1=3.50 — so the label signal
alone is what excludes it here. `witness_room_hab_samplehouse.js` (W-ROOM-HAB-SH): PORT 0→7, H1
precision/recall (3 ok + exactly 1 excluded), H2 falsifier (reports the zband asymmetry above, not forced
to match Duplex's pattern), H3 guid-overlap, H4 coord-overlap, H5 strip (3 IfcSpace/0 Roof/2 storeys+1
building) — 6/6, logs read at `/tmp/wt-fable-livewire/logs/w_room_hab_sh_proof.log` and
`w_room_hab_sh_strip.log`.

**Not done (explicitly out of this task's small scope):** re-running the full W-DW-LIVEWIRE browser suite
to prove `dwWalk(..., {schedule:true})` engages placement on SampleHouse specifically — the task's stated
witness bar was the before/after room counts, which is met; live-engagement proof would need the puppeteer
harness and wasn't asked for here.

## §6 — Execution spec: Task 1 + Task 5 as one assignment (2026-07-10, Fable session — spec-first)

**Deliverable:** new commit(s) on top of bim-ootb `fable/modeller-lod400-livewire` @ `670bf0f`
(existing worktree `/tmp/wt-fable-livewire`, verified clean this session), pushed NOT merged.
Ground truth re-verified directly before speccing: `Duplex_ARC.db` `spatial_structure` = 26 rows
(1 IfcBuilding + 4 IfcBuildingStorey + 21 IfcSpace); among the 21, exactly one non-habitable:
`0pNy6pOyf7JPmXRLgxs3sW | R301 | object_type='Roof'`. Substrate envelope (element_transforms):
x −2.42..11.22, y −22.31..4.51, z −1.55..6.67.

**S1 — Shared classifier `spaceHabitable(space, env)`** in worktree `modeller/disc_walker.js`
(exported on the API so port/injection scripts and `spacesOf()` call the SAME function — the single
enforcement point Task 5 requires):
- Input: `{label, x0..z1}` (the `spacesOf()` shape); `env` = substrate bbox from `element_transforms`.
- **Signal A (label, primary):** normalize label same as `_spaceTypeFor` (UPPER, spaces→`_`, strip
  trailing numbering), word-match against the maintained exclude-list taken verbatim from Task 5's
  starting points: ROOF, SHAFT, VOID, PLANT/PLANT_ROOM, EXTERNAL, PODIUM, SILL, PARAPET, BALCONY.
  List grows only by review against real extractions — never guessed further.
- **Signal B (geometry, secondary):** space `z1 > env.z1 + 0.25` → non-habitable. Verified against
  the known-21 BEFORE coding: R301 z1=8.91 vs envelope 6.67 (fires, +2.24 m); all 20 genuine rooms
  z1 ≤ 5.61 (never fires). The spec's suggested footprint-vs-envelope signal was measured and
  REJECTED for now: R301's footprint is only 0.37 of the envelope — no threshold provable on real
  data; not shipped (non-invent).
- Returns `{ok:true}` or `{ok:false, why:'label:ROOF' | 'zband:8.91>6.67'}`. Real-vs-synthetic
  (`RM_`/`≈`) handling untouched.
- `spacesOf()` applies it to BOTH real sources (elements_meta path and spatial_structure path) and
  §-logs every exclusion (`§SPACE-NONHAB`) — defense stays live even if a tainted DB ever ships.

**S2 — Data strip (Task 1a):** delete non-habitable IfcSpace rows from worktree
`modeller/Duplex_ARC.db`, selected BY the classifier verdicts (not a hand-coded guid), expected =
exactly R301. The IfcBuildingStorey row also named "Roof" stays (it's a storey, not a space). HHS
untouched everywhere, per Task 1.

**S3 — Witness `modeller/tests/witness_room_hab.js` (W-ROOM-HAB, node-side, §-log-first).** Issues
each check exposes: **H1 PRECISION/RECALL** — classifier on the pre-strip known-21 → exactly 20 ok +
R301 excluded, which signal fired logged (proves Task 5 on the hand-verified answer). **H2 FALSIFIER**
— rerun with the exclude-list emptied → R301 must classify habitable via label (only zband may still
catch it), proving H1's pass depends on the real list, not harness bias. **H3 GUID-OVERLAP (Task 1d)**
— no duplicate guids inside spatial_structure; no collision with elements_meta guids. **H4
COORD-OVERLAP (Task 1d)** — every habitable room bbox intersects the substrate bbox in x, y AND z.
**H5 STRIP** — after `--strip`, DB holds exactly 20 IfcSpace rows, no Roof space, storey rows intact.

**S4 — Live proof (Task 1b):** update `witness_dw_livewire.js` L0's expected space count 21→20
(cites this §), rerun the FULL W-DW-LIVEWIRE suite — L1 SCHED-LIVE must engage `placeSchedule` on
the cleaned 20-room set with placements > 0, all checks green, zero pageerror; log saved and read.

**S5 — Push** the commit(s) and verify `git rev-list --count origin/fable/modeller-lod400-livewire..HEAD` = 0.

**Out of scope (explicit):** all Viewer files (`deploy/dev/**`); HHS in every branch; Tasks 2–4;
the diverged bim-compiler `build/disc_walker.js` copy (1731 vs 2188 lines — porting `spaceHabitable`
there is a named follow-up, not part of this branch's commit).

**RESULTS (2026-07-10, same session, logs read not inferred — `/tmp/wt-fable-livewire/logs/
w_room_hab_proof.log`, `w_room_hab_strip.log`, `w_dw_livewire_rerun.log`):**
- **W-ROOM-HAB 5/5** — H1: known-21 → 20 ok + exactly R301 excluded (`label:ROOF`). H2 falsifier:
  exclude-list emptied → label signal vanishes, independent `zband:8.91>6.67` still fires; both signals
  disarmed → R301 classifies ok (H1's pass is the list's, not the harness's). H3: 0 duplicate guids,
  0 elements_meta collisions. H4: 20/20 habitable bboxes ∩ substrate bbox in x,y,z. H5 (post `--strip`,
  delete driven by classifier verdicts): 20 IfcSpace / 0 Roof spaces / 4 storeys + 1 building intact
  (the IfcBuildingStorey named "Roof" survived, as specced).
- **W-DW-LIVEWIRE 12/12** on the cleaned DB — L0 20/20; L1 SCHED-LIVE Duplex ELEC `placeSchedule`
  engaged: placed=102, spaces=19/20, skippedSpaces=0, lod400Refused=2 (EMERGENCY_LIGHT — honest
  LOD400-LAW refusals, pre-existing); L6 PLB placed=18 (6/20 spaces, avoid live); L2/L5/L7 and
  Terminal (L3 placed=390) / SampleCastle (L4 fallback placed=325) all green, zero pageerror.
- **Shipped:** bim-ootb `fable/modeller-lod400-livewire` @ `2821b8e` (parent `670bf0f`), pushed,
  `rev-list origin..HEAD` = 0. Files: `modeller/disc_walker.js` (+`spaceHabitable`/`_substrateEnv`,
  `spacesOf()` filter + `§SPACE-NONHAB` log, API export), `modeller/Duplex_ARC.db` (R301 stripped),
  `modeller/tests/witness_room_hab.js` (new), `modeller/tests/witness_dw_livewire.js` (L0 21→20).

## §4 — Guardrails

- Never re-adopt the stale bim-ootb branch `origin/feat/samplecastle-real-rooms` or its DB payload — it's
  Schependomlaan mislabeled as SampleCastle (see the superseded SC doc §2/§5). Not relevant to Tasks 1-4
  above (Duplex/HHS have real, unambiguous canonical data of their own) but still a live landmine if anyone
  goes hunting for "existing real-room work" and finds that branch.
- `compile_rooms.py` output must never lose its `≈`/`RM_` tagging on the way into any shipped DB — that
  tag is the only thing keeping `spacesOf()`'s exclusion correct. Any wiring work (Task 2) must preserve it.
- Related memory: `project_room_injection_split_decision.md`, `project_disc_walker_grid_guard_marathon_2026-07-10.md`.
