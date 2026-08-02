# ⚠ DO NOT REMOVE — Read the log after every run

## ✅ USER-CONFIRMED GREEN, 2026-08-02 — the ORDERING defect is CLOSED on a live bake
User, on the Hospital generated 4D: *"the Time Machine 4D schedule generated works well now.. no more
roof coming on before the walls or upper deck forming before lower. I can confirm at Day 282."*
That is §4D_BAND_MONOTONIC (PR #1129, `fc58210`, sw v913, `_GANTT_CACHE_VERSION` 6→7) + §4D_ROOF_LOAD_PATH
(#1120) + the cache bump (#1123) that let #1120 reach a browser at all. The headless witness said
non-structure cross-storey inversions **29,824 → 0** and the user's eyes now agree on a real film.
**Do NOT reopen roof-before-walls or upper-before-lower.** They are settled by measurement AND by live
confirmation. The item below is a DIFFERENT invariant (support), not a regression of this one.

⚠ **One number to reconcile, not a defect claim:** `witness_4d_band_monotonic.js` measured project span
**176d** on Hospital, but the live badge reached **Day 282**. Either the badge counts calendar days
against the witness's working-day span, or the live generate differs from the witness's inputs. Read
`§CPE_DAY_COUNTER` + `§GANTT_CACHE_SAVE` in the next bake log HEAD before treating either as wrong.

## ▶ RESUME 2026-08-02 (session close) — START HERE
**ONE open item, specced and de-risked, needing ONE user ruling before code.**

**USER-CONFIRMED LIVE, 2026-08-02, on a FRESH generate from a CLEARED IndexedDB** (so this is the
current rules, not a stale gantt cache): *"Still noticing from cleared IndexDB initial Time Machine
has beams without support."* This is EXACTLY what `audit_support_roleblind.js` measures — **1,294
`IfcBeam` bearing on walls** are scheduled before those walls, out of 6,778 total / 2,379 structural.
The live symptom and the headless number are the same defect. **No re-diagnosis needed.**

**DO NOT re-attempt the five pass-level repairs** — all built and measured 2026-08-02, all rejected,
table + reasons in `§ROOT CAUSE — CONFLICTING SORT ORDERS` at the END of this file. Read that first.

**THE FIX IS SPECCED:** `§ELEMENT_CPM` (last section of this file). Element-level precedence is
EXTRACTED from geometry, not authored — the user's own framing, and it is correct: *"Isn't CPM for
Phase level? CPM at element level is what supposed to be granted innately."* The header's
"No CPM/dependency solving (planner's)" scopes out PHASE-level authored programmes and still does.
It never scoped out extracted element precedence. **My earlier framing of this as a scope widening
was WRONG and the user corrected it.**

**⛔ THE ONE RULING NEEDED BEFORE BUILDING:** 21,502 element pairs where extracted geometry says
*wall before beam* and the trade convention says *structure before walls*. That is the cycle. My
reading is that Ruling A already settles it — "nothing without support" is the hard role-blind gate,
floating wins over ordering — so **SUPPORT WINS and the trade edge is dropped, with every drop
counted in a `§` line**. Get the user to confirm or override, then build.

**Gates that must ALL pass (the trap of this lane is passing one by breaking another):**
`node audit_support_roleblind.js` → 0 · `node tests/test_schedule_gate.js` → 0 floating ·
`node witness_4d_band_monotonic.js` → T2a 0, T2b ≤ 551, T4 span ≤ 2× · run from a `/tmp/wt-*` worktree.

**State:** `origin/main` = `fc58210`, scheduler byte-for-byte shipped, all witnesses green.
Audits live on branch `fix/helipad-roof-separation` (`a40cf16`) — **audits only, scheduler reverted**.

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

## §4D_ROOF_LOAD_PATH — BUILT AND MERGED (PR #1120, sw v903, 2026-08-01). Two limits ON RECORD.
**RED→GREEN, the user's own two helipad boxes:** slabs started `2022-07-27` (phase Superstructure)
while the walls carrying them finished `2023-04-30` — **277 days before their own walls**. Now they
start `2023-10-19` as Architecture. Witness `witness_4d_roof_load_path.js` 9/9; Hospital 63415/63415
and LTU_AHouse 122330/122330 placed; `§SUPPORT_CHECK floating=0` on both. Re-run independently by the
dispatching session, not accepted on the builder's report.

**M1 needed a SECOND clause the spec stated in prose but the formula above omitted.** `base_z above
the walls' average midheight` alone promoted **23 of 35** Hospital slabs — including a genuine
intermediate floor at `base_z 176.81` with five levels above it — because a slab that CAPS the walls
below it satisfies that test whether or not it ALSO carries walls above. The shipped rule adds the
spec's own floor-case definition as an explicit test: no XY-overlapping wall may have
`base_z >= slab.top_z`. Still epsilon-free, still no name list. Hospital lands on 10 promoted.

### ⚠ LIMIT 1 — M3's blind spot is NARROWED, NOT REMOVED. Do not read `floating=0` as proof.
The rebuilt audit still keys on **trade number** (`T.seq > 4`) with one extra branch, not on geometry
as this spec's M3 asked. `witness_4d_roof_load_path.js` G-RLP-5 feeds `seq=8` in BOTH its RED and
GREEN arms, so what it proves is: *given a slab M1 already promoted, the audit can falsify a bad
schedule where the old one could not.* **It does not prove the audit would catch a roof M1 FAILED to
promote** — such a slab keeps `seq=4`, gets the structure-only pool, and reads `floating=0` exactly as
before. Two wider scopes were tried and rejected with measured false-positive counts (grid = every
element → 3421/10979 on Hospital, mostly beams "floating" over unrelated walls; grid = structure+walls
for every slab → 24, all ordinary floor slabs vs walls on other storeys). A reasoned compromise —
but the geometric audit M3 actually specifies is STILL NOT BUILT.

### ⚠ LIMIT 2 — parapets defeat the rule, silently.
Clause (b) excludes any slab with an XY-overlapping wall standing on it. **A roof with a parapet wall
is ordinary construction** and would be excluded → not promoted → the original defect returns for that
roof, with no log line saying so. On Hospital, (a) alone gave 23 and (b) cut it to 10; the 13 blocked
include genuine intermediate floors (correct) and would include any parapeted roof (incorrect). Not
exercised by Hospital or LTU. **A parapet is a wall whose top is BELOW the tops of the walls it sits
among, and which carries nothing** — that is the discriminator to add when a building exercises it.
Do not fix this speculatively; wait for a model that shows it.

---

# §4D_WALLS_BEFORE_ROOF — #1120 promoted the boxes' roofs and left the roof they stand on (spec 2026-08-01)

> **User, live, on a MaxQ buildup bake of Hospital, 2026-08-01:**
> *"The roof before the walls still happening on the roof top"*

## §4D_ROOF_LOAD_PATH did NOT fail — it fired, and this is what it could not reach
The user's own run logs `§GANTT_OVERRIDE 10 slabs promoted to roof role (seq=8) by load path`. Ten
slabs WERE re-roled. The defect survives that. This section does not re-litigate #1120; it names the
slab #1120 structurally cannot promote and closes it.

**This is `⚠ LIMIT 2` on record from #1120, arriving for real** — and wider than LIMIT 2 predicted.
LIMIT 2 anticipated a *parapet* defeating clause (b). What actually defeats it on Hospital is the two
helipad boxes whose OWN roofs #1120 promoted: their walls stand on the main roof slab, so clause (b)
("no XY-overlapping wall may have `base_z >= slab.top_z`") disqualifies the roof underneath them.
#1120 fixed the boxes and left the roof they sit on. LIMIT 2's proposed discriminator ("a parapet is
a wall whose top is BELOW the tops of the walls it sits among, and which carries nothing") would NOT
have caught this: these walls are 3.05–3.47 m tall and they DO carry something (the box roofs).

## THE MEASUREMENT — Hospital, generated 4D, `origin/main` @ `9945364`
Probe: every `IfcSlab` in `Hospital_extracted.db` (35), each against the walls that physically carry
it (XY-overlap, `wall.base_z < slab.base_z - 0.05`, `wall.top_z >= slab.base_z - 0.5` — the same
carrier test `schedule_gate.js` `auditFloating` already uses), read off the REAL `kernel_ops`
timestamps after `tmActivateForBake()`. Log: `scratchpad/probe_rooftop_main.log`.

```
§PROBE_SUMMARY slabs=35 violating=24 promoted(phase=Architecture)=10
               violating_and_promoted=0   violating_and_NOT_promoted=24
```

**The rooftop row — the user's defect, in numbers:**

| | slab `3Csn1z$1v5Q8DXdumWYJUE` |
|---|---|
| what it is | the topmost main roof slab, 2091.5 m², the building silhouette |
| storey / z-band | `Level 7` / band 66 (the highest band with a slab that is not a box roof) |
| base_z / top_z | 199.66 / 199.81 |
| phase / role | **`Superstructure`, seq 4 — NOT promoted** |
| starts | **2022-07-27** |
| its 14 wall carriers finish | **2023-04-30** |
| error | **277 days before its own walls** |

277 days is the identical figure #1120 reported *fixing*. It was never fixed for this slab — #1120
measured it on the two box roofs (`3eq15PZlbCi8$6xdfFtxpB`, `3Vxmv9vT1DBOVGP9f4HeYO`, base_z 202.80/
202.83) which sit ON this one. Those two now start 2023-10-19. The 2091 m² deck beneath them still
starts 2022-07-27.

## Which of the three candidate causes is real — measured, not chosen
**CAUSE 1 — the load-path role test misses slabs. REAL, and it is the operative cause.**
All 24 violating slabs are non-promoted (`phase=Superstructure`); **0 of the 10 promoted slabs
violate**. Promotion is exactly the thing that fixes the ordering, and the roof-top slab does not get
it. The blocker is clause (b): 3 XY-overlapping walls (`3Vxmv9vT1DBOVGP9X4HeGE` base 199.87,
`3Vxmv9vT1DBOVGP9X4HeDp` and `3eq15PZlbCi8$6xdXFtxz7` base 200.06) stand on it. Clause (a) is
satisfied — the slab is a roof by load path in every respect except that two boxes sit on it.

**CAUSE 2 — the storey BAND. NOT the cause of the defect, but a REAL latent hole in the fix.**
`§GANTT_STOREY_Z reassigned=9457` does not misplace the rooftop walls: the slab's own band is 66,
its carriers sit in bands below it, and the bottom-up `base_z` sort is correct. The band is
irrelevant while the slab is seq 4, because a seq≤4 slab is scheduled in PASS A where walls are not
consulted at all. **But it becomes real the moment the slab is promoted:** a promoted slab's only
dependency on walls is `schedule_gate.js`'s per-*phase* trade gate, keyed on
`collapsePhase(storey)`. This slab's key is `Level 7` while **12 of its 14 carriers are phase-key
`Level 6`** (`{"Level 6":12,"Level 7A":1,"Level 7":1}`). Promotion alone would leave the wait on
those 12 walls to coincidence, not to a rule. Must be closed in the same change.

**CAUSE 3 — the support invariant is wrong. REAL, confirmed, and it is why this survived a merge.**
`§SUPPORT_CHECK floating=0/10979 … (0=solved)` on the very same run in which 24 of 35 slabs start
~290 days before the walls carrying them. `auditFloating` offers its `wallGrid` only to
`T.cls === 'IfcSlab' && T.seq > 4` — i.e. **only to slabs M1 already promoted**. A roof M1 *failed*
to promote keeps seq 4, gets the structure-only pool, and reads clean. This is `⚠ LIMIT 1` verbatim
("It does not prove the audit would catch a roof M1 FAILED to promote") — now demonstrated on a real
slab. A `floating=0` that cannot see the defect being reported is not evidence.

## The rule to add — M4, one clause, measured against the alternatives
**M4 — a wall standing on a roof is not "the next storey" if that wall is itself capped by a slab
already known to be a roof.** A helipad box, a plant enclosure, a coped parapet — their walls top out
in a roof, they do not continue the building. Formally, with the shipped M1(a+b) promotion set as
the seed (computed once, frozen — NOT iterated):

> slab `S` is also a roof if clause (a) holds AND every wall `w` standing on `S`
> (`w.base_z >= S.top_z`, XY-overlap) is *capped by a seed roof slab* `C` — some `C` in the seed set
> with `C` XY-overlapping `w` and `w.base_z <= C.base_z <= w.top_z + GAP`.
> A wall capped by nothing does NOT qualify.

**Depth 1, deliberately, because full recursion was measured and it collapses.** Letting newly
promoted slabs re-enter the seed set cascades straight down the stack: Level 7's box walls excuse the
199.66 deck → the deck excuses `3064w0y0nDv9wdb1cWL_Gu` → Level 6 (191.66) promotes → its walls excuse
Level 5 → Level 4 → the entire building becomes "roof". Depth-1 on the frozen seed set terminates.

**Measured outcome on Hospital (`scratchpad/probe4.py`, same DB, offline replication that reproduces
the shipped count of 10 exactly): 10 → 11.** The single addition is `3Csn1z$1v5Q8DXdumWYJUE`, the
user's slab. Every other candidate is blocked and the reason is logged:

| slab | base_z | walls above | blockers | verdict |
|---|---|---|---|---|
| `3Csn1z$1v5Q8DXdumWYJUE` | 199.66 | 3 | **0 — all capped by the two box roofs** | **PROMOTED** |
| `0e8pm26Tv5vPrj6zU55MQt` Level 6 | 191.66 | 16 | 3 (`3aD_wpAY…` h=0.10 capped by nothing; `1EW479yk…`; `3064w0y0…`) | blocked ✓ |
| `0e8pm26Tv5vPrj6zU55MQv` Level 5 | 186.66 | 54 | 33 | blocked ✓ |
| `0e8pm26Tv5vPrj6zU55MQh` Level 4 | 181.66 | 535 | 514 | blocked ✓ |
| `1OV06Y3c5D8vODNyxVnSVI` (#1120's control) | 176.81 | 56 | 56 | blocked ✓ |
| …9 more intermediate panels at 176.81 | | 1–64 | all ≥1 | blocked ✓ |

**Rejected alternative, measured and discarded:** a footprint-extent ratio (bbox of the walls-above,
clipped to the slab, over the slab's own area). It does not separate — the Level 7 roof scores 0.040
while genuine intermediate panels at 176.81 score 0.024 / 0.024 / 0.029 / 0.044 and Level 6 scores
0.170. Any cut that promotes the roof also promotes at least four ordinary floors. No threshold exists;
this is why M4 is a load-path rule and not an area rule. (`scratchpad/probe3.py`.)

## M5 — the promoted slab must wait for its carriers by GEOMETRY, not by phase key (closes CAUSE 2)
`schedule_gate.js` `computeSchedule` PASS B sorts by `(seq, base_z)`, so walls (seq 6) are always
placed before roof slabs (seq 8) within the same pass. Build a wall support grid incrementally as
PASS B places walls, and gate any `seq > 4` `IfcSlab` on the XY-overlapping walls that carry it
(`w.base_z < S.base_z - EPS && w.top_z >= S.base_z - GAP`) in addition to the existing structure
gate and trade gate. No new pass, no cycle, no new constant — `EPS`/`GAP` are the module's own, and
the pool is **the same pool `auditFloating` already uses for `seq>4` slabs**, so scheduler and
auditor finally test the same thing instead of the auditor being narrowed to match the scheduler's
blind spot.

## M6 — stop the instrument from lying (narrows CAUSE 3 / LIMIT 1)
`§SUPPORT_CHECK floating=0` stays as it is (its scope is defended in #1120), but it must no longer be
the ONLY number. Add a role-blind measurement line so the blind spot is visible in the log rather
than hidden behind a zero:

```
§ROOF_GATE roofSlabs=<n> lateVsWallCarriers=<must be 0> | otherSlabs=<n> lateVsWallCarriers=<n> (frame-first, expected — see LIMIT 1)
```

`lateVsWallCarriers` counts slabs whose `start` precedes the max `end` of their XY-overlapping wall
carriers, computed for EVERY slab regardless of seq. The roof-role half is a **gate** (0 required).
The other half is a **measurement, not a gate** — an ordinary intermediate floor legitimately precedes
the partitions beneath it in a frame-first concrete schedule (this is #1120's rejected "attempt 2",
24 false positives, and it is still the right call). Printing it is what makes LIMIT 1 auditable.

## Witness claims — `witness_4d_walls_before_roof.js`
- **G-WBR-1** RED→GREEN, the user's slab. RED on `origin/main`: `3Csn1z$1v5Q8DXdumWYJUE`
  starts 2022-07-27 (`phase=Superstructure`) while its 14 wall carriers finish 2023-04-30 — 277 days
  early. GREEN: `start >= max(carrier.end)` and `phase=Architecture`.
- **G-WBR-2** no cascade. `§GANTT_OVERRIDE` reports **11** (was 10), and the four named controls —
  Level 6 191.66, Level 5 186.66, Level 4 181.66, and #1120's own floor control
  `1OV06Y3c5D8vODNyxVnSVI` — are all still `phase != Architecture`. The fix cannot pass by promoting
  everything, and specifically cannot pass by re-entering its own output.
- **G-WBR-3** the role is still DERIVED, not named: with every `storey` string blanked, the same slab
  is still promoted. A name test scores 0 here.
- **G-WBR-4** CAUSE 2 is closed by geometry, not luck: 12 of the slab's 14 carriers are phase-key
  `Level 6` while the slab's key is `Level 7`, so the per-phase trade gate provably cannot cover
  them; assert the slab starts at/after the max end **of those 12 specifically**. Also asserted
  directly against `ScheduleGate.computeSchedule` on the real 15-element subset with the trade gate
  neutralised (all carriers given a different storey), where the pre-M5 code returns a start EARLIER
  than the carriers' end and the post-M5 code does not.
- **G-WBR-5** the instrument no longer hides it: `§ROOF_GATE` is present, its roof half is 0, and its
  other half is a non-zero *reported* number. On `origin/main` the line is absent entirely while
  `§SUPPORT_CHECK` reads `floating=0/10979` — i.e. the only instrument said "solved".
- **G-WBR-6** no regression: `placed == total` on Hospital **and** LTU_AHouse, and `§SUPPORT_CHECK`
  is still `floating=0` on both.

## Cache/version obligations
- `_GANTT_CACHE_VERSION` **5 → 6** in `viewer/time_machine.js`. Without it a browser holding a cached
  gantt never re-generates and the fix cannot reach the user — the exact failure PR #1123 had to
  ship as its own follow-up for #1120.
- `viewer/sw.js` `CACHE_VERSION` bump (`viewer/time_machine.js` and `viewer/schedule_gate.js` are
  precached).

## ⛔ Out of scope
- Making `§SUPPORT_CHECK` role-blind (that is #1120's measured-and-rejected "attempt 2"; M6 reports
  the number instead of gating on it).
- The captured/linked-schedule path (`source=captured`). GENERATED 4D only.
- `sequence_rules.json` seq numbers, the room-title lane, `MIN_DWELL`.

## 🔴 §SUPPORT_ALL — the invariant is NOT held: structure bearing on WALLS is scheduled before them
**User's standing requirement, 2026-08-02, after declining to adjudicate film pacing:** *"as long as
the 4D schedule does not put anything without support first."* That is the whole acceptance test for
this lane now. **It currently FAILS on real Hospital: 6,778 violations.**

Instrument: `audit_support_roleblind.js` (branch `fix/helipad-roof-separation`, `86b8535`).

| carried on carrier | n |
|---|---|
| IfcPipeSegment on IfcWallStandardCase | 1768 |
| IfcPipeFitting on IfcWallStandardCase | 1396 |
| **IfcBeam on IfcWallStandardCase** | **1048** |
| IfcMember on IfcWallStandardCase | 590 |
| IfcDuctSegment / IfcDuctFitting on IfcWallStandardCase | 533 / 437 |
| **IfcBeam on IfcWall** | **246** |
| **IfcColumn on IfcWallStandardCase** | **162** |
| IfcSlab on IfcWallStandardCase | 19 |

Worst single: `IfcPipeFitting 0dMvF9TX5F1PPX5xJ4dTQX` starts **100.5 days** before its carrying wall
`0jzYl7FRDEExmTLzqqEZZo` finishes.

**For the structural rows this is not a near-miss — it is guaranteed by the two-pass design, and it is
provable from the code rather than only measured:**
1. `place()` writes the support grid ONLY for `el.seq <= 4` (`schedule_gate.js:162`).
2. `geoGate()` reads that grid — so it can gate you on STRUCTURE and nothing else.
3. Walls are `seq 6` → PASS B. Beams/columns/members/slabs are `seq <= 4` → PASS A.
4. **PASS A runs to completion before PASS B begins.**
→ A beam bearing on a wall CANNOT be gated on that wall. Ever. Same for columns, members, plates.

**§SUPPORT_CHECK cannot see this** — `auditFloating`'s wall pool is handed only to `IfcSlab && seq>4`
(`schedule_gate.js:304`), i.e. exactly the roof case. `floating=0` is true and uninformative for every
other class. This is the SAME defect §4D_ROOF_LOAD_PATH (#1120) and §4D_WALLS_BEFORE_ROOF (#1128)
fixed **for roof slabs only**, by promoting them out of PASS A into PASS B so they wait for walls. The
general case was never done, which is why "roof before walls" kept coming back in a new costume.

**TWO SELF-CORRECTIONS on the way to 6,778 — recorded so the number is checkable, not trusted:**
- Role-blind carriers (every class supports every class) → **40,754**. Top pair
  `IfcPipeFitting on IfcCovering` (5,606): a pipe above a ceiling tile is not held up by that tile.
  That audit over-reports exactly as badly as the shipped one under-reports.
- Carrier pool narrowed to structure+walls but keeping `S.top_z >= T.base_z - GAP` → **29,759**. That
  predicate accepts ANY carrier taller than my base, so a riser threading past a 3 m wall read as
  "carried by" it. The rests-on/runs-past discriminator is that the carrier tops out AT my underside
  (`|S.top_z - T.base_z| <= GAP`): **29,759 → 6,778**.

⚠ **DO NOT "fix" this by re-sorting PASS A.** Already measured and rejected under §4D_BAND_MONOTONIC:
re-sorting PASS A drives inversions to 0 and **floats 2,341 elements**, because `geoGate` reads only
what is already placed. The likely shape is the #1120 move generalised — an element whose real carrier
is a wall belongs after walls — but it needs its own spec, its own measurement, and a floating gate
that stays at 0.

## ✅ §HELIPAD_ROOF_SEPARATION — the reported roof defect was NOT an ordering defect
`audit_helipad_roof_walls.js` (`8bc0532`): **roofsBeforeTheirWalls = 0/11**. The promotion reproduces
the shipped `§GANTT_OVERRIDE` counts exactly (`seed=10 m4=1 total=11`), so the audit measures the real
rule. The two helipad huts (`3eq15PZlbCi8$6xdfFtxpB`, `3Vxmv9vT1DBOVGP9f4HeYO`) are the only elements
in the building with a lag of **exactly 0.0 days** — the gate is binding to the millisecond.

The film is what hides it: 63,415 elements / 1,735 frames = **36.6 elements per frame**. The hut roofs
are rank 63,404–63,405 of 63,415 and land on frame **1735**; their carrier walls land on 1734. The
whole hut is **2 frames = 133 ms** at 15 fps. Correct order, zero visual separation.

**Volume-weighted pacing was specced, simulated and DISPROVEN before any code was written** (user
chose it; the measurement says no):

| policy | biggest single | top 0.1% hold | hut roof frame | rooftop span |
|---|---|---|---|---|
| count (current) | 0.00% | 0.1% | 1735 | 1267f = 84.5s |
| volume (raw) | **8.03%** | **49.9%** | 1726 | 1389f = 92.6s |
| volume^1/2 | 0.44% | 7.6% | 1731 | 1125f = 75.0s |
| volume^1/3 | 0.10% | 2.5% | 1733 | 1099f = 73.3s |
| log1p(volume) | 0.07% | 3.1% | 1732 | 1052f = 70.1s |

Raw volume hands 8% of the film to ONE element and half of it to 63, and still buys the hut only 9
frames. The compressions fix the tail but move the hut 2–4 frames. **No proportional weighting can
work**: the hut is genuinely the last 0.02% of the building, and every one of these is a monotone
cumulative map, so the last 0.02% lands in the last 0.02% of the film by construction. Buying the
topping-out a beat requires a NON-proportional tail — the §CPE_STICK_HOLD precedent (a hold buys its
own time), not a re-weighting. **Parked: the user declined the pacing lane and set the support
invariant above as the priority instead.**

## ⛔ §4D_WALL_BORNE_STRUCTURE — 5 DESIGNS BUILT AND MEASURED 2026-08-02. ALL REJECTED. ROOT CAUSE FOUND.
**Superseded by the section at the end of this file — read `§ROOT CAUSE — CONFLICTING SORT ORDERS` first.**

## (earlier, kept for the measurements) ATTEMPTED 2026-08-02, PARKED. Fixes support, regresses the band.
Branch `fix/helipad-roof-separation` @ `a36b71c` — **NOT FOR MERGE**, `origin/main` untouched at
`fc58210`. Recorded so the next attempt starts from the measurements, not from re-deriving them.

**DESIGN A — "a wall that carries structure IS structure."** 1,229 load-bearing walls move PASS B →
PASS A and into the structure grid, so `geoGate` gates everything resting on them; `wallGate` widened
from `IfcSlab`-only to every `seq>4` class.

| gate | before | after | |
|---|---|---|---|
| support violations (§SUPPORT_ALL) | 6,778 | **377** (structural 2,379 → **0**) | ✅ |
| floating (`tests/test_schedule_gate.js`) | 0 | **0** | ✅ |
| band inversions (T2a) | 0 | **1,026** | ❌ |
| project span | 170d | **213d** (+25%) | ❌ |

**The band failure IS the user's own "upper floors gets walled first" returning through the fix for a
different defect.** That is why this cannot ship, even though it satisfies the invariant they asked
for. Trading one reported defect for another is not progress.

**Two attempts at the band regression, both measured, neither worked:**
1. **Live `bandGate` on the promoted walls only** — 1,026 unchanged, worst 115d → 108d. PASS A is
   ordered by `base_z`, so `bandTrade[r-1]` is still filling when rank *r* is reached. This is the
   same "gate without re-sorting is a lower bound" already recorded for structure.
2. **TWO-PHASE PASS A — this file's own open item 2** ("gating on `bandTrade[r-1]` computed from a
   PRE-PASS rather than read live"): phase 1 places PASS A ungated and yields a COMPLETE ladder,
   phase 2 resets `grid`/`wallGrid`/`out`/`crews`/`bandTrade` and replays the **identical** element
   order against the frozen ladder. Result: **1,026 unchanged, span 177d → 213d.** The frozen gate
   does not bind and **why is the open question** — the order is preserved byte-for-byte and every
   term enters through `Math.max`, so Ruling A's re-sort prohibition is NOT what is blocking it.
   ⚠ **Next session: instrument WHICH elements are inverted before touching the gate again.** Both
   attempts assumed the inverted population was the promoted walls; that was never verified, and the
   count sitting at exactly 1,026 across three different gate configurations is the tell that it is
   probably NOT them.

**DESIGN B — the #1120 shape (move the CARRIED thing, not the carrier) — REJECTED BY MEASUREMENT,
do not retry.** Promoting wall-borne structure past the walls requires its transitive closure or its
dependents float: **7,279 / 12,500 structural elements (58.2%)** — members 4,092, beams 1,628, plates
1,028, columns 497, slabs 34. Converges in 8 rounds, but it empties PASS A rather than fixing it.

**What is now known that was not before:** the support invariant and cross-storey band monotonicity
are in genuine tension in this scheduler, and the tension is located precisely at the walls. Any
future attempt has to hold BOTH counters at once — `audit_support_roleblind.js` and
`witness_4d_band_monotonic.js` are the pair, and passing one while breaking the other is the trap.


## §ROOT CAUSE — CONFLICTING SORT ORDERS. The support invariant needs a scope decision, not a bug fix.
Branch `fix/helipad-roof-separation` @ `a40cf16`. **Scheduler reverted to shipped, byte-for-byte** —
`witness_4d_band_monotonic` **6/6 all green**, `test_schedule_gate` **PASS (0 floating)**. The audits
stay; they are pure additions and are what made any of this measurable.

**THE FINDING, in one sentence:** the support gate needs carriers placed first (sort by `base_z`,
z-major) and the band gate needs lower ranks placed first (sort by `(seq, rank)`, rank-major) — and
because walls BOTH carry structure AND rest on structure, the two requirements demand **conflicting
sort orders of the same elements**. `geoGate` and `bandGate` each read only what is ALREADY PLACED,
so each is a correct constraint under its own sort and a weak lower bound under the other's. **No
gate-only change can resolve this.** Every design below is a different attempt to have both orders.

| # | design | support | floating | band (T2a) | span |
|---|---|---|---|---|---|
| — | **shipped (`fc58210`)** | 6,778 | **0** | **0** | 176d |
| 1 | load-bearing walls → PASS A | **377** (structural **0**) | 0 | 1,026 ❌ | 213d |
| 2 | ALL walls → PASS A | — | 0 | 1,157 ❌ (T2b 551→545 ❌) | 213d |
| 3 | two-phase PASS A, frozen ladder | — | 0 | 1,026 ❌ | 177d |
| 3b | …iterated to a fixed point | — | 0 | 1,157 ❌ **diverged 11/11** | 535d ❌ |
| 4 | whole-schedule relaxation | 6,778 ❌ | 0 | **0** ✅ **diverged 6/6**, moved=62783 | 671d ❌ |
| 5 | **ONE geometry-ordered sweep** | support-correct | 0 | 34,980 ❌ | **147d** ✅ trades 5→**7** ✅ |

**What each failure taught, so none of it is repeated:**
- **(1)** splitting a trade across two passes is fatal on its own — PASS B runs wholly after PASS A, so
  every moved wall inverts against the ones left behind. All 1,026 offenders were the moved walls
  (verified by dumping them, not assumed).
- **(3) is the important one.** Instrumented: the frozen gate **fired 1,157 times and 1,157 inversions
  remained** — it was gating against numbers from a run that no longer existed, because round 1 is
  ungated so its ladder is stale *by construction*. This kills this file's own open item 2
  ("gate on `bandTrade[r-1]` computed from a PRE-PASS rather than read live") **as written**.
- **(3b/4)** relaxation does not converge here. Crews are a shared project-wide pool, so delaying walls
  reshuffles crew slots and the iteration **oscillates**. My "starts only increase, therefore monotone,
  therefore convergent" reasoning was **WRONG** and the run disproved it.
- **(5) is the most promising and the closest to physically correct.** There is no real cycle: a carrier
  is ALWAYS lower than what it carries, so sorting the whole model by `base_z` is already a valid
  topological order of the support relation. It is also SIMPLER than the two passes it replaces, and it
  *improved* the schedule — span 170→**147d**, trades at midpoint 5→**7**. It fails only because a
  z-major sort makes `bandGate` a weak lower bound: **storey z-ranges overlap**, so rank r-1 is not
  finished when rank r is reached.

**⚠ THE DECISION THIS NEEDS FROM THE USER — it is scope, not a bug.** Design 5 works if the band gate
stops depending on placement order, i.e. band ends come from a dependency solve instead of being read
live. That is **CPM**, and this file's own header scopes it out: *"No CPM/dependency solving
(planner's)"*. Adding it is a deliberate widening of what the generated 4D is allowed to be — not
something to slip in under a bug fix. **Do not let a future session "just add a topological solve"
without that ruling.**

**Meanwhile the shipped behaviour is the honest one:** cross-storey ordering is correct (the user's
reported defect), nothing floats, and ~2,000 structural elements bearing on walls are scheduled before
those walls — now MEASURED and named by `audit_support_roleblind.js` rather than hidden behind
`§SUPPORT_CHECK floating=0`, which only ever asked the question about roof slabs.


## §ELEMENT_CPM — the specced fix. Extracted precedence, not an authored programme.
**User's framing, 2026-08-02, and it is the correct one:** *"Isn't CPM for Phase level? CPM at element
level is what supposed to be granted innately as u just did."* Right. Phase-level CPM is a planner
authoring activities and logic links — out of scope, stays out. Element-level precedence is a FACT OF
THE GEOMETRY: S carries T when `S.base_z < T.base_z - EPS`, `|S.top_z - T.base_z| <= GAP`, and they
overlap in XY. The edges already exist; nothing is invented and nothing is solved to obtain them.

**WHY ALL FIVE PASS-LEVEL REPAIRS FAILED, restated in one line:** `base_z` and `(seq, rank)` are both
PROXIES for a topological order. Each encodes one constraint family and loses the other. The fix is to
stop using a proxy and use the graph.

**MEASURED FEASIBILITY on real Hospital (this is small, not a rewrite risk):**

| | |
|---|---|
| nodes | 63,415 |
| support edges | **74,942** (1.2/element avg, **max in-degree 1,101**) |
| graph build | **709 ms** |
| sync nodes for band + trade | **72** — so those families are O(N), not O(N²) |
| trade-vs-support conflicts | **21,502** ← the ruling above |

~63k nodes / ~200k edges. Topological sort + forward pass is milliseconds — CHEAPER than the two-pass
scheduler it replaces, and negligible against the 21.6-minute bake it feeds (`§BAKE_FAST_PATH_COST`).

**THE BUILD, in order:**
1. **Support edges from geometry.** Acyclic BY CONSTRUCTION — `base_z` strictly decreases downward, so
   no cycle is possible. Already implemented and timed inside `audit_support_roleblind.js`; lift it.
2. **Trade + band edges via 72 sync nodes** — one per `(storey, seq)` and per `(rank, seq)`, instead of
   pairwise. **Skip any edge that contradicts a support path** and COUNT the skips in a `§` line
   (expected ≈21,502; a wildly different number means the predicate drifted).
3. **Topological sort + forward pass** with the existing `§CREW-CAP` project-wide crew pool. This is a
   CPM forward pass over an EXTRACTED graph.
4. ⚠ **Max in-degree is 1,101** — one element carried by 1,101 others (the 2,092 m² deck,
   `3Csn1z$1v5Q8DXdumWYJUE`). Give wide fan-in the sync-node treatment too or that single node
   dominates the edge count.

**EXPECT IT TO BEAT SHIPPED, not merely match it.** Design 5 (the one geometry-ordered sweep) already
demonstrated that once ordering is right the schedule IMPROVES: span 170d → **147d**, trades at
midpoint 5 → **7**. It failed only on the band gate, which is exactly what an explicit graph fixes.

**Instruments already built and committed** (branch `fix/helipad-roof-separation`):
`audit_support_roleblind.js` — the invariant, carriers = structure+walls offered to EVERY class, with
the rests-on predicate. `audit_helipad_roof_walls.js` — roof-role slabs vs their carriers (0/11, and
it reproduces the shipped `§GANTT_OVERRIDE` counts exactly, which is what makes it trustworthy).

**⚠ TWO PREDICATE TRAPS — both cost a wrong answer this session, do not fall in again:**
1. **Carrier pool.** Role-blind (every class carries every class) gives 40,754 — it counts
   `IfcPipeFitting on IfcCovering` (5,606) as support. A pipe above a ceiling tile is not held by it.
   Carriers are STRUCTURE + WALLS.
2. **Rests-on vs runs-past.** `S.top_z >= T.base_z - GAP` accepts ANY carrier taller than my base, so a
   riser threading past a 3 m wall reads as carried: **29,759 phantom vs 6,778 real**. The carrier must
   top out AT my underside: `|S.top_z - T.base_z| <= GAP`. **This trap is already recorded in
   `auditFloating`'s own header as evidence that "walls do not carry beams in this DB" — that
   conclusion is WRONG and is corrected in `§SUPPORT_ALL` above.** Walls carry structure in 1,243 places.
