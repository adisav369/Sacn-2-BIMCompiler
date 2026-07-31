# ⚠ DO NOT REMOVE — Read the log after every run

## Gantt Accuracy & User-Editable Construction Schedule

### Goal
Make the Time Machine's auto-generated construction sequence **truthful** — matching how buildings are actually built — and give users a **modifiable JSON schedule** they can edit to customize the playback.

### Current Problems

1. **Roofs built too early**: `IfcSlab` elements on roof storeys get `sequence:4` (Superstructure) instead of `sequence:8` (Architecture/Roof). The sort is `sequence → storeyRank`, so a roof slab with seq=4 appears before ground-floor walls (seq=6). Fix: check storey name AND ifc_class together — slabs on "Roof" storey should be treated as roof elements.

2. **Storey rank fragile**: `storeyRank()` (time_machine.js) uses string matching on storey names. Names like "Level 1", "01 - Ground", "Roof Terrace" can misparse. Need robust parser or fallback to Z-coordinate.

3. **No true parallel trades**: Current code says "parallel" but actually sequences within same `(sequence, storey)` group. Real construction has overlapping trades — electrician rough-in starts while mason is still on walls. Resource-based parallelism using LABOR_RATES crews is not implemented.

4. **Working hours naive**: Hardcoded 7am–3pm, no weekends, no holidays. Start date = today minus N days (arbitrary).

5. **No user control**: SEQUENCE_RULES and LABOR_RATES are hardcoded in `rates.js`. Users cannot modify the schedule without editing source code.

### What Must Happen

#### A. Fix Sequence Accuracy — **DONE (S253e)**

1. **Storey-aware class mapping** ✓: Roof slab override — `IfcSlab` on "Roof" storey → seq=8 with `§GANTT_OVERRIDE` log.
2. **Robust storeyRank** ✓: Replaced Z-gap banding with storey-name bands ranked by min center_z. Terminal: 2 Z-bands → 23 storey-bands.
3. **True parallel trades** ✓ (was already implemented): `resourceCursor[resource|band]` gives per-resource-per-band cursors. Different trades overlap on same band.
4. **Phase dependencies** ✓ (was already implemented): `bandSeqDone[band|seq]` ensures higher seq waits for lower seq within same band (not globally). Structural Z-dependency propagates band-to-band.

#### B. User-Editable Schedule JSON

1. **Check for existing schedule**: On time machine activation, look for `construction_schedule.json` in the DB or as a URL parameter `?schedule=URL`.

2. **Auto-generate if missing**: If no user schedule exists, generate one from `injectGantt()` logic and store as JSON in the DB (table `tm_schedule` with one TEXT column `json`).

3. **JSON schema** (create `deploy/dev/construction_schedule.schema.json`):
   ```json
   {
     "projectStart": "2025-01-06T07:00:00",
     "workHours": { "start": 7, "end": 15 },
     "workDays": [1,2,3,4,5],
     "phases": [
       {
         "name": "Substructure",
         "sequence": 1,
         "trades": ["CONCRETE_GANG"],
         "ifcClasses": ["IfcFooting", "IfcPile", "IfcReinforcingBar"]
       }
     ],
     "overrides": {
       "guid-xxx": { "sequence": 8, "phase": "Roof", "startAfter": "guid-yyy" }
     },
     "resources": {
       "CONCRETE_GANG": { "crewSize": 1, "dailyCapacity": 50 },
       "STEEL_ERECTOR": { "crewSize": 1, "dailyCapacity": 30 }
     }
   }
   ```

4. **Export button**: Add "Export Schedule" button to time machine panel → downloads `construction_schedule.json`.

5. **Import button**: Add "Import Schedule" button → user uploads modified JSON → re-injects kernel_ops → replays with new schedule.

6. **DIY flow**: User exports → edits in any text editor → re-imports → sees updated construction sequence in time machine. No code editing needed.

#### D. Unify Gantt Chart with kernel_ops (Single Source of Truth)

**Current state (S253d done):** Gantt now reads kernel_ops when hourglass has been activated:
- `main.js`: `4D_SCHEDULE_REQUEST` handler relays kernel_ops + guidRows via BroadcastChannel
- `boq_charts.html`: `buildScheduleFromOps()` builds scheduleData from kernel_ops, `generateSchedule()` as fallback
- GUIDs relayed from viewer directly (no IndexedDB cache dependency)
- Sync badge shows "Hourglass OK" or "Run Hourglass first"
- Tests: `test_s253_gantt_sync.js` (111 assertions), `test_s253_real_db.js` (Terminal 48k elements)

**Known issues from S253d session:**
1. ~~`§KO_BUG phase order: Architecture day=1117 AFTER MEP Final day=2`~~ **FIXED (S253e)**: Replaced Z-gap banding (1.5m threshold → only 2 bands for Terminal) with storey-based banding from `elements_meta.storey` ranked by min center_z. Terminal now has 23 storey-bands. Phase order correct: Superstructure → MEP Rough-in → Architecture → MEP Final → Finishes. Also added roof slab override: `IfcSlab` on "Roof" storeys → seq=8 with `§GANTT_OVERRIDE` log.
2. Gantt chart disappeared after deploy — `_TRL.t_gantt` threw when `_TRL` undefined. Fixed with fallback `(_TRL && _TRL.t_gantt || '4D — Gantt Timeline')`. T12 test catches this.
3. Stale browser cache — sw.js CACHE_VERSION not bumped, index.html sw.js?v=N not bumped. User saw old code for hours. T13 test now verifies local + OCI versions match. **Always bump sw.js + index.html + run T13 before deploy.**

**Previous state (pre-S253d):** Two independent schedule generators existed with different algorithms.

**What must happen:**
1. `kernel_ops` is the single source of truth for construction schedule
2. `boq_charts.html` must **read** `kernel_ops` instead of running its own `generateSchedule()`
3. Gantt dashboard scrub (`4D_SEEK`) should map to `kernel_ops` timestamps, not independent task indices
4. `ghostglass.js` should be retired or adapted — the hourglass `renderAtTime()` already does visibility control. Two competing animation systems (ghostglass material transitions vs time_machine visibility) will fight if both are active.
5. When user opens Gantt chart from a building that already has `kernel_ops` (from hourglass session), the chart should visualize those ops immediately — no re-computation.

**Migration path:**
- Phase 1: `boq_charts.html` reads `kernel_ops` via BroadcastChannel `4D_QTO_REQUEST` relay (already exists for QTO data). Add a `4D_SCHEDULE_REQUEST` message type that returns kernel_ops as JSON.
- Phase 2: Gantt dashboard renders bars from kernel_ops timestamps. Scrub sends cursor timestamp (not task index) to viewer.
- Phase 3: Viewer receives timestamp → calls `renderAtTime(timestamp)` directly. No ghostglass needed.

### Key Files
- `deploy/dev/time_machine.js` — `injectGantt()` (line 853), storey-based banding (line 903), scheduling loop (line 984)
- `deploy/dev/rates.js` — `SEQUENCE_RULES` (line 154), `LABOR_RATES` (in template loader), `SEQUENCE_DEFAULT` (line 212)
- `deploy/dev/boq_charts.html` — existing Gantt chart (independent `generateSchedule()`, not wired to kernel_ops)
- `deploy/dev/ghostglass.js` — glass-to-solid animation, BroadcastChannel driven, to be retired
- `deploy/dev/main.js:183-209` — BroadcastChannel handler for `4D_PLAY`/`4D_SEEK`/`4D_RESET`

### Architecture Context
- `kernel_ops` table stores `(timestamp, op_type='ELEMENT_PLACE', parameters JSON, output_guid)`
- `parameters._end_ts` gives element completion time
- `renderAtTime(cursor)` reads kernel_ops via `_ops[]` array, sorted by `start_ts`
- `SEQUENCE_RULES` maps IFC class → `{phase, sequence, resource}`, longest key match wins
- `LABOR_RATES` (from template JSON) maps resource → `{productivity: {IfcClass: unitsPerDay}}`
- Current parallel: different `(sequence, storey)` groups can overlap. Same group = sequential.
- BroadcastChannel `bim_4d` already relays between viewer and boq_charts — extend, don't replace.

### Acceptance Criteria
- Roofs built AFTER walls on same storey, never before
- Basement/substructure first, ground floor second, upper floors bottom-up, roof last
- MEP rough-in overlaps with architecture on same storey (different trades)
- Same trade on same storey = sequential (one crew)
- `§GANTT_OVERRIDE` log shows any storey-based sequence corrections
- `construction_schedule.json` exportable from time machine panel
- Imported JSON regenerates kernel_ops and replays correctly
- Desktop and mobile playback unaffected
- `?tm=play` shared link still works
- Gantt dashboard scrub syncs with hourglass — same elements light up at same timestamps
- `§4D_SCHEDULE` log confirms Gantt reads from kernel_ops, not generateSchedule()

#### C. Construction Visual Effects

Current: frontier = orange, recent = yellow, placed = solid. Flat and instant.

Target: elements should **glow into existence** with a brief explosive/radiant feel, then cool down:
1. **Arrival**: element appears with bright emissive glow (white-hot flash, ~0.3s)
2. **Active construction**: transitions to orange emissive (frontier state)
3. **Just finished**: amber/reddish linger that slowly fades (~3 ticks)
4. **Placed**: solid original material, no glow

Implementation: use `MeshPhongMaterial.emissive` + `emissiveIntensity` animation. On each `renderAtTime` tick, elements in frontier get high emissive that decays. Recent elements get diminishing amber emissive. This is a visual-only change — no new data, just material animation in the existing render loop.

**Metal sparks**: When steel elements (IfcBeam, IfcColumn, IfcMember, IfcPlate, STEEL_ERECTOR resource) are in frontier state, add a brief particle burst — a handful of Three.js `Points` with orange-white color, short lifetime (~0.5s), gravity falloff. Simulates welding/cutting sparks. Keep particle count tiny (5-10 points per element) to avoid GPU cost.

Keep it lightweight — no post-processing passes, no bloom shader. Emissive transitions on materials + sparse particle points for metal. On mobile (`_isMobileTM`), skip all effects (current show/hide is fine).

### Testing
- Test on Duplex (simple, 2 storeys, clear roof)
- Test on SampleCastle (multiple storeys, complex storey names)
- Test on HospitalAuckland (MEP-heavy, parallel trades)
- Verify: `§TIME_MACHINE_GANTT` shows correct element count
- Verify: roof elements appear AFTER wall elements in `§` log order
- Export JSON, modify a sequence number, re-import, verify playback changes
- Test on mobile: export/import buttons accessible and functional

---

# §4D_HOST_BEFORE_HOSTED — a window reveals BEFORE its supporting wall (user, observed in a baked film, 2026-07-29)
> User, watching the Hospital MaxQ film: *"that window coming on first, before its supporting walls can
> be noted to harden our 4D generater, its matter of data."*

## Why this matters more than it looks
It is a **build-order correctness** defect, not a cosmetic one, and it is the first thing a scheduler
will notice — before the camera work, before the render. A window cannot be installed before the wall
that hosts it. The whole defensible claim of the Cinema work is *"a film cut against a real 4D schedule
by the engineer who built that schedule"* (`CINEMA_DELIGHT_BATCH.md` §SETTLED 2026-07-29c); an ordering
violation visible on screen undercuts exactly that claim, on the one axis it is being sold on.

**The user's own framing is right: it is a DATA/RULES matter, not a renderer bug.** The buildup now
plays the timeline verbatim (§CPE_BUILDUP_FOLLOW_TM, PR #1082) — so whatever order the generator emits
is what the film shows, faithfully. Fixing this in the camera or the reveal would be the wrong layer and
is explicitly forbidden by that same ruling.

## ⚠ MECHANISM — CORRECTED after reading `viewer/schedule_gate.js` (supersedes the guess below)
> User: *"its again a Z stacking matrix required i reckon"*

**Half right, and the wrong half is the useful part: the Z stacking matrix ALREADY EXISTS and works.**
`schedule_gate.js` is built on it — two passes, both gated on vertical support:
- **PASS A (structure, `seq<=4`)** — bottom-up by `base_z`: an element waits for the structure whose XY
  footprint overlaps it and whose base is below. ε=0.05m so a thin slab under a duct still counts.
- **PASS B (non-structure, `seq>4`)** — by TRADE then `base_z`: waits for the structure under its
  footprint AND for the lower trades in its own Level.

**A window beating its wall cannot be caught by that, structurally: a window is not ABOVE its wall, it
is INSIDE it.** Same Level, same footprint line, overlapping Z range — "whose base is below" has nothing
to bite on. So this is the OTHER axis, not a missing Z relation.

Two candidate causes, needing different fixes — **determine which before building**:
1. **Trade `seq` ordering.** If glazing's trade sorts before the wall's in PASS B, the window wins
   regardless of geometry. Fix is a data/table correction. Cheap. **Check this first.**
2. **No HOSTED-BY relation exists.** The gate has *supported-by* but not *hosted-by*. Fix is a third
   constraint beside the two passes: bbox containment (or the real IFC host link), in the same style as
   the support gate. This is the GENERAL answer — it covers doors, louvres, any opening filler, anything
   recessed into a host.

⚠ **Pre-empt the scope objection:** the file's own header states *"No CPM/dependency solving
(planner's)"*. A hosting gate is NOT CPM — no float, no logic network, no critical path — it is one more
geometric gate of exactly the kind already implemented. Say so when proposing it.

## ~~Likely mechanism~~ — SUPERSEDED, kept only to show what was ruled out
~~A window's `center_z` sorts below its host wall's centroid.~~ **Wrong** — the gate sorts on `base_z`,
where a wall (0.0) is already below a window (sill ~0.9), so plain Z ordering would put the wall FIRST.
The Z axis was never the problem. Read the corrected section above instead.
**Verify first, in this order:** (1) query `elements_meta`/`element_transforms` for a real offending
pair on Hospital and compare their `center_z` AND their emitted `start_ts` — name the actual guids;
(2) confirm whether the two are in the same phase or different ones (if different, the phase order is
the cause and Z is innocent); (3) only then decide where the constraint belongs.

## The rule to add, once verified
**HOST BEFORE HOSTED: an element may not reveal before the element that hosts it.** Applied as a
constraint AFTER the existing sort, never as a replacement for it — the Z/phase ordering is doing real
work and must not be discarded to fix a dependency.
- The host relationship **already exists in this codebase** — see `project_openings_inherit_host_rotation`
  (openings inherit their host wall's rotation), so the pairing is derivable, not new data to invent.
- Scope it to what is provable: opening fillers (`IfcWindow`, `IfcDoor`) → host `IfcWall`. ⚠ **Do NOT
  generalise to "MEP after structure" in the same pass** — that is a different rule with different
  evidence, and bundling them makes both unfalsifiable.
- ⚠ **Prime Directive:** the host link must be EXTRACTED (IFC relationship or measured containment),
  never inferred from proximity alone. A window assigned to the nearest wall by distance is invention.

## Witness claims
- **W-HOST-ORDER (the gate).** For every hosted element on Hospital, `start_ts(host) <= start_ts(hosted)`.
  Report the violation COUNT before and after — before must be > 0 or the defect was never reproduced,
  and a witness that cannot show the RED is not a witness.
- **W-HOST-NO-REGRESSION.** The storey-band bottom-up character survives: the Z-vs-reveal-order
  correlation must not degrade, and `§GANTT_MINI` phase spans must not collapse into each other.
- **W-HOST-COVERAGE.** Report how many elements actually HAVE a derivable host. Elements with none keep
  their current order and are counted, not silently passed.

## Where this sits
Not in the Cinema lane — the film only EXPOSED it. It belongs to 4D generation and should be fixed there,
which also means every consumer (Time Machine playback, the Gantt drawer, 4D/5D variance) gets it.

### ▶ THE A/B IS ALREADY SET UP — the user still holds the path (2026-07-29)
> User: *"that means if the windows stayed back it would have been dramatic"* · *"i still have the same
> path script"*

**Correctness and spectacle point the SAME way here, which is worth stating to any sceptic.** Correct
order is wall first, glazing after — so on the same flight the camera approaches an open frame with the
services exposed and the façade closes over them AS IT WATCHES. The reveal happens in front of the lens
instead of having already happened. The defect did not merely mis-state the build order; **it cost the
shot its drama.** Fixing the schedule sharpens the film rather than sanding it down.

**And the experiment is already controlled.** The user retains the authored Hospital path
(§CPE_IDB_PATH_STORE / the `cinema_path` record). So the fix can be demonstrated as a true A/B: same
camera, same building, same duration, ONE variable changed. **Re-bake that exact path after the fix and
compare against `BIM_MaxQ_Hospital_1785273910881.mp4`** (1852×960, 1186 frames, 79.067 s, 5.31 Mbps —
the reference film, user-accepted 2026-07-29).
- ⚠ Per the FUNDAMENTAL LAW the two films are the DEMONSTRATION, not the proof — W-HOST-ORDER's
  violation count going >0 → 0 is the proof. Keep both; they answer different questions.
- ⚠ The comparison is only valid if nothing else moved. Re-bake from the SAVED path, do not re-author,
  and confirm `§CPE_OPEN src=authored` plus an unchanged band/hose count before baking.

### ⚠ REFRAMED — Hospital did not need a heuristic at all (2026-07-29)
> User: *"cant we make it strictly weigh to Z value?"* → *"then recall the 4d schedule prompts/# and
> look at the CPM"*

**1. Strict Z cannot express it.** A wall CONTAINS its window (wall 0.0→3.0, window 0.9→2.1 inside that
span). Containment is not ordinal, so no weighting of one scalar encodes it — and pushing Z harder
breaks the stacking cases the gate currently gets right.

**2. The CPM data already answers it, and we are discarding it.** From `4D_CAPTURE_AND_FALLBACK.md`
§2.1/§5.2, Hospital's captured IFC programme carries **deps + element links 46/46**, Early/Late
Start/Finish 45/46, Free/Total Float 44/46, IsCritical 45/46 — *"the schema then discards the CPM/float/
WBS/calendar that made it expert-grade."* The widening is **already specced as T1b/§5.2 and never built.**

**So this defect is not a gate bug — it is the FALLBACK running on a building that did not need one.**
A planner already stated wall-before-window; we are throwing the statement away and then re-deriving a
worse answer geometrically. Two tracks, non-competing:
1. **Captured programme → build T1b, use the planner's dependencies.** `schedule_gate` should not be
   ordering Hospital at all. This is the higher-value track and it is already written.
2. **No programme → the geometric gate stays**, and there the HOSTED-BY constraint above is the fix,
   because there is nothing else to appeal to.

⚠ **Boundary unchanged:** capture and replay CPM, **never recompute float** (`4D_CAPTURE_AND_FALLBACK.md`
:359). Reading a planner's stated dependencies is not us solving CPM, and must not become that.
⚠ **Honesty tiers move with this:** a film ordered by captured deps is tier 1 (*linked schedule*); the
geometric gate stays tier 2 (*this model's derived 4D*). See `CINEMA_PATH_EDITOR.md` §5.

---

# §4D_ROOF_LOAD_PATH — a slab's role is DERIVED from what carries it, never from a storey name (spec 2026-08-01)

**User, watching a Hospital buildup:** *"how do we solve the 2 top boxes next to helicopter in Hospital
getting their roofs first before the walls?"* — then, before any code: *"but i like to understand from
the 4D principles we following first so we know what rules we are still missing."*

**⚠ THIS SUPERSEDES §Current Problems item 1 at the top of this file.** That entry proposed *"check
storey name AND ifc_class together — slabs on 'Roof' storey should be treated as roof elements."*
**That fix is already implemented (`time_machine.js:3296`, `/roof/i` + `IfcSlab` → seq 8) and it CANNOT
work.** Measured 2026-08-01:
- Hospital's storeys are `Level 1..7`, `Level 7A`, `Unknown` — **`roofOverrides` fires ZERO times**.
  The two offending slabs' storey is literally `Unknown`.
- LTU_AHouse's top storey is `TAKPLAN` (Swedish for roof plan) — also zero.
Any name list is an invented list. Do not extend the regex. Delete the premise.

## The measured defect — `Hospital_extracted.db`, the user's own two boxes
```
band 67 (z 201..204)          base_z    top_z    bbox_z
  IfcSlab              x2     202.80    203.59     0.80
  IfcSlab                     202.83    203.62     0.80
  IfcWallStandardCase  x8     199.61    203.08     3.47   (…199.61-200.06 base, 203.08-203.35 top)
```
Two slabs bearing on eight wall heads, **all inside one 3 m Z-band**, so the bottom-up rule cannot
separate them and the sort falls through to trade order — `IfcSlab` seq 4 beats `IfcWall*` seq 6.
Roofs first, walls after.

## The three missing rules (stated as principles, in the order they must be fixed)

### M1 — a slab's ROLE is a load-path fact, not a label
`IfcSlab` covers both a floor slab (walls stand ON it) and a roof slab (it stands ON walls). One class
carries one `seq`, and it was set to the floor case. **The rule to add:**
> For each `IfcSlab`, take the walls (`IfcWall*`) whose XY footprint overlaps it. If the slab's
> `base_z` is above the **midheight of those walls** (`(wall.base_z + wall.top_z) / 2`), the slab is
> CARRIED BY them → roof role → `seq = 8`, `phase = 'Architecture'`. Otherwise it stays a floor slab.

**No epsilon, no constant, no name list.** The wall states its own midheight. The discriminator is
enormous in both directions: on the measured case, slab `base_z` 202.80 vs wall midheight ~201.4 →
**1.4 m above** = roof. In the floor case the walls stand on the slab, so `wall.base_z ≈ slab.top_z`
and the slab's base sits a full wall-height *below* midheight. Nothing is near the boundary.

### M2 — "support" is a LOAD PATH, not a trade number
`schedule_gate.js:90` admits only `seq <= 4` into the support grid, and walls are seq 6, so **a wall
can never be found carrying anything**. PASS A schedules every slab before PASS B schedules any wall.
For a rooftop plant box the wall IS the structure. **The rule to add:** an element promoted to roof
role by M1 must be scheduled in PASS B (it is no longer `seq <= 4`), and the walls that carry it must
be visible to its gate. Simplest correct form: M1's reseq to 8 moves the slab into PASS B by itself —
**verify that is sufficient before adding anything to the grid**, and only widen the grid if the
witness proves it is not.

### M3 — the auditor shares the scheduler's blind spot
`auditFloating` (`schedule_gate.js:139`) builds its support grid from `seq <= 4` **too**, so it asks
the same question with the same assumption and returned `floating=0` on a building whose roofs
demonstrably float. Its own header calls it an *"independent XY-aware audit"* — it is not independent.
**The rule to add:** the audit must find a support by GEOMETRY (anything below it, overlapping in XY,
whose `top_z` reaches its `base_z`), not by trade number. Until it does, `floating=0` proves nothing
about this class of defect — which is exactly why the user found it and no gate did.

## Witness claims — `witness_4d_roof_load_path.js`
| gate | proves / disproves |
|---|---|
| G-RLP-1 | **RED today, on the user's own case.** Hospital band 67: both slabs are scheduled BEFORE all eight walls that carry them. After the fix, both start after the last of those walls finishes. |
| G-RLP-2 | the role is derived, not named: with every storey string blanked, the same two slabs are still classified as roofs. A name test scores 0 here; this must score 2/2. |
| G-RLP-3 | a FLOOR slab is NOT promoted — walls standing on a slab keep that slab at seq 4 and before them. Measured on a real floor/wall pair from the same DB, so the fix cannot pass by promoting everything. |
| G-RLP-4 | `§GANTT_OVERRIDE` reports how many slabs were promoted by load path, and the old `/roof/i` count is gone — a stale build cannot silently serve the name rule. |
| G-RLP-5 | M3: the rebuilt audit finds the two floating slabs BEFORE the fix (proving it can now falsify) and reports 0 after. An audit that reads 0 both times is not a gate. |
| G-RLP-6 | no regression in total ops or project window on Hospital and LTU_AHouse: same element count placed, monotone cursor, `§SUPPORT_CHECK` still 0 for everything it already covered. |

## ⛔ Explicitly OUT of scope
- The room-title / caption lane (`cpe_room_title.js`, §CPE_ROOM_TITLE_*) — shipped and working, do not touch.
- `MIN_DWELL` — user ruled it KEPT 2026-08-01.
- Re-keying the Time Machine, changing `sequence_rules.json` seq numbers for any class, or touching
  the captured/linked-schedule path (`source=captured`). This is the GENERATED 4D only.
- Extending the `/roof/i` regex with more languages. That is the premise being deleted.
