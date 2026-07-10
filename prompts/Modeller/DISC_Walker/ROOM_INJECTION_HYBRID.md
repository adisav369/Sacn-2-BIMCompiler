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
| SampleCastle, SampleHouse, Clinic, Garage, HHS, Hospital | 0 | no `spatial_structure` table |

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
**Status: NOT STARTED.** Currently a manual script (`compile_rooms.py <db> --write`), never called from any
build/embed/import path (confirmed via grep this session — zero automatic callers). Needs wiring into
whichever step runs `extractIFC2DB.js` (both the embed/"ready-made" build step for our own residents, and
whatever live path handles a user's IFC import) so it runs automatically whenever a source IFC has no real
`IfcSpace`.

### Task 3 — New Modeller Outliner "Rooms" category (the display/wow-factor surface)
**Status: NOT STARTED, NOT YET SPECCED.** No existing room display anywhere in the Modeller (checked —
`grep -rl room` across `modeller/*.js` only matches `disc_walker.js` itself). Pattern to follow: the
existing Disc-tab-follower shape already used for STR and walked-fixtures categories
(`bom_tree_outliner.js` / `dw_instances_outliner.js` / `str_walker_outliner.js`) — a "Rooms" category would
sit alongside those, sourced from whichever rooms (real or `≈`-tagged) exist in the open building's
`spatial_structure` table.

### Task 4 — Carry real `IfcSpace`/`spatial_structure` through the ARC-only strip step
**Status: NOT STARTED.** The strip (`b93ca13`, "cascade-deleted to discipline='ARC' only") never explicitly
targeted rooms for removal, but the loss is a real, live regression path for any FUTURE re-strip or
re-embed — `spatial_structure` needs to be an explicit survivor of that cascade, same as any other ARC
table, or this whole rule regresses again the next time a resident gets re-baked.

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

Task 5 (room recognition/habitability classification) is assigned to **Fable** (execution lane — per this
project's model-allocation convention, Fable executes, Sonnet keeps the mastermind/memory-writing role).
Tasks 1+5 were executed as one assignment and are ✅ DONE 2026-07-10 (see §6 RESULTS); Tasks 2–4 remain
NOT STARTED as logged above.

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
