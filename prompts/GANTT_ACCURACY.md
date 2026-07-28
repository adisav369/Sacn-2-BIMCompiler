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

## Likely mechanism — TO BE VERIFIED before any code (do not treat as diagnosed)
`schedule_gate.js` orders bottom-up by real geometric Z within storey bands + phase. **A window and its
host wall are independent elements to that sort**, and an opening filler's `center_z` (~sill+half-height,
often ~1.5 m above floor) can easily sort BELOW the centroid of the wall that carries it. Nothing in the
Z sort knows one depends on the other.
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
