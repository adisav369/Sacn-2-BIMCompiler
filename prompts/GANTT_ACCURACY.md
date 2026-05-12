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

#### A. Fix Sequence Accuracy

1. **Storey-aware class mapping**: In `injectGantt()`, after matching `SEQUENCE_RULES`, override sequence if storey name suggests roof/basement:
   - Any IFC class on a "Roof" storey → sequence ≥ 8
   - `IfcFooting` on non-basement storey → still substructure (pad footings)
   - Add `§GANTT_OVERRIDE` log when storey overrides sequence

2. **Robust storeyRank**: Use Z-coordinate from `element_transforms.center_z` as primary rank, storey name as secondary. Elements are already queried with `ORDER BY center_z`.

3. **True parallel trades**: Multiple resources on same storey should overlap:
   - Track per-resource cursors, not just per-group cursors
   - HVAC_TECH, PLUMBER, ELECTRICIAN can run concurrently on same storey
   - Same resource on same storey = sequential
   - Different resource on same storey = parallel (start at same time as storey becomes available)

4. **Phase dependencies**: MEP rough-in (seq 5) starts after superstructure (seq 4) is done on that storey, NOT after all seq 4 everywhere. Architecture (seq 6) can start on lower storeys while superstructure continues above.

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
1. `§KO_BUG phase order: Architecture day=1117 AFTER MEP Final day=2` — MEP Final elements placed before walls because Z-band grouping is too coarse (only 2 bands for Terminal). This is section A (Sequence Accuracy), not D.
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
- `deploy/dev/time_machine.js` — `injectGantt()` (lines 571-770), `storeyRank()` (line 648), scheduling loop (line 711)
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
