# ⚠ DO NOT REMOVE — 4D generated-schedule accuracy. Read the log after every run, spec-first,
# no invented dependency edges or rates. Every number here traces to real extracted data or a
# nameable, once-confirmed business assumption — never a plausible-looking value.
# Full day-by-day history (2026-08-03 → 2026-08-12, 3941 lines) archived verbatim, nothing lost:
#   prompts/archive/4D_SCHEDULE_PERFECTION_full_history_2026-08-03_to_2026-08-12.md
#   Consolidated 2026-08-12 per user ask ("first consolidate the prompts/#") — this file keeps the
#   acceptance bar, the architecture map, a one-line-per-fix ledger, the still-OPEN threads, and the
#   landmine list. Full diagnostic narrative for closed items is in the archive if ever needed.
# Predecessor (CLOSED, do not re-litigate): prompts/CPM_FLOAT_GAP.md.

## ▶ THE ACCEPTANCE BAR — user, 2026-08-12, verbatim
**"all i want is not to see a single item hanging in midair that is all."**
Also, same session: *"no band aid fix, just generalised solution. It need not be that strict. Hunt
for chances."* and, from 2026-08-11: *"I AM NOT YOUR TESTER."*

That is the whole spec. Not "witnesses green" — **zero visibly floating elements in the movie, on
every shipped building.** A passing witness proves internal consistency with what got built; it has
twice failed to prove agreement with what was asked. Before reporting success, hold the concrete
number up against that sentence.

## ▶ RESUME — START HERE (state as of 2026-08-12, fourth pass)
**NEWEST (2026-08-12, fourth session): §ARCH_START_TEMPO — "the ARCH starting first day part is too
fast" is STUDIED, MEASURED, and deliberately NOT fixed yet.** Read that section (last in this file)
BEFORE proposing anything: it names the three multipliers that produce the burst, the prior rulings
each one came from (so a "fix" doesn't re-open a settled decision — the user's own words: *"solution
has to be after understanding previous work or else we be in vicious cycles"*), and two ⛔ items that
need the user's call. It also records two incidental defects found while measuring: a DUPLICATE
`_midairRepair` in `time_machine.js`, and §TIER2_AFTER_TIER1's ledger claim being stronger than what
actually ships post-`_midairRepair`.

**§MIDAIR_REPAIR is the answer to the acceptance bar and it is built + witnessed — see its own
section at the end of this file for the numbers.** The hanging population was never visible to the
existing proof trail: `auditFloating` only counts an element as floating when a support it KNOWS
ABOUT finishes late, and its pools are `seq<=4` + promoted slabs + walls. Measured directly on the
DISPLAY timeline, **5,561 elements across the 7 shipped buildings appeared with nothing they touch
yet on screen** — while every shipped witness was green. After the fix: **0 on all 7.**
Open after that:
1. **`fix/roof-host-wall` (`/tmp/wt-roof-fix`) — built, tested clean, deliberately NOT shipped.**
   Generalizes `openingGate`→`restsOnGate` (no class whitelist either side). Measured effect on all
   7 shipped buildings: **zero**. Now largely superseded — §MIDAIR_REPAIR closes the same class of
   gap generally, at the display layer. Retire it unless a measured case needs the gate-layer version.
2. **The stricter end-based bar** (nothing appears before a contact FINISHES) is measured and
   deliberately not enforced — see §MIDAIR_REPAIR's own section for why (it is unreachable without
   serializing the trade train, and it is not what the renderer shows).
3. **972 orphans** (elements touching nothing anywhere in the model) — an extraction limit no
   schedule can fix, now locked per building in `witness_midair_zero.js`. LTU_AHouse owns 865 of them.

**Closed by measurement this pass, do not re-open on the old story:**
- *Terminal glass-roof slabs* were NOT floating. Measured: each of the 5 `Basic Roof:Glass` slabs
  (`IfcSlab`, seq=4, bz≈22.6, day 7.2) has an `IfcColumn` based at 14.77m under it whose op starts
  day 3.3–5.0 — a real carrier, on screen first. The third-pass note calling them "zero gate of any
  kind" was right about the gate and wrong about the consequence.
- *HHS stairs "hanging in midair"* — CONFIRMED and FIXED. 4 stair flights are authored as `IfcSlab`
  (seq=4 ⇒ structure pool ⇒ no gate ever ran): 2 at bz=2.16 appeared day 1.5 (first contact day
  8.5), 2 at bz=5.85 appeared day 9.6 (first contact day 49.7). §MIDAIR_REPAIR moves them to 8.5d
  and 49.7d. No temporary-works/shoring excuse was needed — it was the structure-pool blind spot.

## THE ARCHITECTURE — where the physics actually lives (re-verified against `origin/main`, 2026-08-12)
`viewer/schedule_gate.js` `computeSchedule(elements, baseMs, scaleFactor, maxCrews)` is the element-
level scheduler that drives the live movie. Two passes; every gate returns "earliest ms I may start"
and placement takes `Math.max(...)` of all of them.
- **`bandGate`** — §4D_BAND_MONOTONIC. A trade may not run ahead of ITSELF on the floor below.
  Storey ranks are EXTRACTED (median `base_z` per collapsed storey), never a constant.
- **`geoGate`** — bearing-below: latest finish of XY-overlapping structure rising from below, plus
  the §GEO_SUPPORT_LEAK contained-support clause (strictly in my LOWER half, per §TM_GEO_ORDER_CYCLES).
- **`hangGate`** — carrier-above, for elements with NO bearing below (ceiling fans, ducts). Includes
  §HANG_NEAREST: rod-suspended BIG sinks reach the nearest overlapping pool member above (0.5–9.5m
  measured), no invented reach constant.
- **`wallGate`** — a promoted roof slab waits for the walls that carry it at their TOP.
- **`openingGate`** — §DOOR_WINDOW_HOST_WALL: a door/window is cut SIDEWAYS into a wall; gated on
  the host wall found geometrically (no `IfcRelFillsElement` table exists in the shipped DB).
- **Placement pools:** `seq<=4` = structure (`placeStruct`), `seq>4` = non-structure (`placeNonst`).
- **`auditFloating(elements, sched, classFilter)`** — the judge. Must stay aligned with the gates.
- `viewer/time_machine.js` `_promoteRoofLoadPath()` decides which `IfcSlab` gets promoted out of the
  structure pool to roof-role (`seq=8`). **`IfcWall`-only carrier search** — see §STRUCT_POOL_UNGATED.
- `viewer/schedule_author.js` `materializeDefault()`/`materializeZones()` author the Gantt bars;
  `computeCpm(db, id, {fixedDates:true})` gives float/criticality matching the real movie exactly.
- `kernel_ops` (materialized table in the building's IndexedDB blob) is what `renderAtTime`/the MaxQ
  bake actually reveal-order from — **stamped with `_genVersion`, re-materialized on mismatch**
  (§KERNEL_OPS_SCHED_VERSION). A schedule fix that doesn't bump this never reaches an opened building.

## §STRUCT_POOL_UNGATED — the shape of the gap (named 2026-08-12; COVERED at the display layer by §MIDAIR_REPAIR)
Every gate above (`geoGate`'s consumer clause, `hangGate`, `wallGate`, `openingGate`) applies to
`placeNonst` — i.e. `seq>4`. **A structure-pool member (`seq<=4`) goes through none of them.** So any
element that (a) is classified as structure and (b) is not actually structure schedules at day ~0
with no support check at all. Confirmed by measurement: HHS's stair flights are authored as
`IfcSlab`, so seq=4, so nothing ever checked them — they appeared on day 1.5 and day 9.6 against
first neighbours on day 8.5 and day 49.7. **This is the generalized statement of the "one class at a
time" bugs** (door, roof, stairs) — and it is why the fix belongs below the class level.
§MIDAIR_REPAIR closes it at the DISPLAY layer for every class and both pools at once. The GATE layer
is still pool-scoped: a generative-layer fix (gating `placeStruct` too) remains an open option, worth
doing only if a case appears that the display repair cannot express.

**⚠ Two regressions already caused and reverted while attacking this — do not re-attempt either:**
1. Firing `restsOnGate` UNCONDITIONALLY alongside the other gates broke `witness_tier_serial_display.js`'s
   LOCKED baselines on 5/7 buildings (HHS 420→0, JKR 398→3, Terminal 24007→12169). Fix: scope it as a
   FALLBACK (return `baseMs` immediately if `soFar !== baseMs`) — same idiom §HANG_NEAREST uses.
2. Widening `_promoteRoofLoadPath`'s carrier pool to `IfcWall OR bboxVol > BIG_ELEMENT_VOL` broke the
   same witness WORSE: LTU_AHouse 882→1246 (wrong direction), Terminal 24007→4 (collapsed). Reverted.
   A real fix needs a NARROW condition (e.g. roof-suggesting name/storey AND zero wall carriers found).

## LANDMINES — checked every time, learned the hard way
- **`sw.js` `CACHE_VERSION` bump belongs in the SAME PR as any `viewer/*.js` change.** Missed once
  (#1286) → users kept the stale file and re-reported a fixed bug; needed #1287 to recover. Check
  `git diff --stat` before calling a viewer PR done. `_GANTT_CACHE_VERSION` is a SEPARATE gate and
  does not cover `kernel_ops` — that needs `_genVersion` (§KERNEL_OPS_SCHED_VERSION).
- **A new gate must be wired into BOTH placement call sites** — the initial `placeNonst` `Math.max(...)`
  AND the §DEQ_V1 repair-loop `Math.max(...)`. One site alone is silently a no-op (caught by
  `witness_door_window_host_wall.js` W-DWH-1a/1b after the author's first cut fixed nothing).
- **Any new gate MUST be checked against `witness_tier_serial_display.js`'s LOCKED baselines** before
  it is considered safe. A green class-specific witness (doors, roof) is not enough.
- **Before believing a live bug report, check what the browser actually served** — `sw.js` version +
  the building's cached `kernel_ops` `_genVersion`. Two "still broken" reports were stale caches.
- **Witness rot is real** — several witnesses FAILed for stale assertions, not code regressions
  (renames, changed baselines). Un-rot before concluding an engine bug. Differential-test any FAIL
  against the pre-change tree before attributing it to your own change.

## SHIPPED LEDGER — do not rebuild any of this (one line each; full story in the archive)
- §GANTT_BAR_IDENTITY (K0) — drawer bars are real tasks; `witness_gantt_bar_identity.js` 42/42, 7 buildings.
- §ZONE_EDGE_LEAD — zone graph contradicted its own dates. FIXED 2026-08-04.
- §GANTT_AXIS_OUTLIER — PR #1175. §TM_CLOSE_RESTORE — PR #1182. §GEO_SUPPORT_LEAK — PR #1183.
- §CLASS_UNMATCHED_FALLBACK — PR #1186. §GENERATE_4D_HANG (hang root-caused + native entry) — #1193/#1194.
- §GANTT_EDIT_LOCK, §GANTT_MATDEFAULT_EXCLUSION, §DLOD_VF_CAMGUARD — #1199. §TM_PANEL_RESIZE_H — #1201.
- BOQ4D — `boq_charts.html` reads the real schedule; `witness_boq_charts_real_schedule.js` 91/91.
- §DEQ_V1 / §DEQ_REPAIR — default-engine-quality bar work (repair loop + strict containment).
- §4D_LAYER_TRUTH + §GANTT_RETIME_RESYNC — #1239/#1240. §GEOMETRIC_SUPPORT_ORDER — #1242 (support DAG,
  placement order is a structural fact, not a per-building patch). §GANTT_LOCK_INTEGRITY — #1244.
- §GANTT_STALE_CACHE — #1257. §TM_GEO_ORDER_CYCLES — #1276 (Terminal DAG cycles 37,927→0, floating 45→8).
- §SUPPORT_UNCHECKED — #1277 (warn-only observability). §HANG_NEAREST + pile reclass — #1278 (831→250).
- §NOGEO_COMPOSE — #1265–#1273 + #1280 (Garage_ARC 19→0; 8/8 buildings, ghost table all zeros;
  Modeller port #1273). Source IFCs were never lost — `reference_source_ifc_locations.md`.
- §OG_BEARING_BOUND + slab-on-grade reclass (250→246) + `IfcPile` sequence rule — #1281.
- §TIER_SERIAL — #1282 (two-tier phase-window collapse: Tier 1 serial backbone, Tier 2 pool).
- chase-to-zero (3 witnesses un-rotted, JKR+LTU coverage, §PROMOTED_CARRIER_POOL) — #1283.
- §TIER2_AFTER_TIER1 — #1286 (+ SW bump #1287): Tier 2 starts only after Tier 1's TRUE completion.
  Verified exact on 7 buildings (MEP Rough-in start == Architecture end, e.g. HHS 68.9d==68.9d).
- §KERNEL_OPS_SCHED_VERSION — #1291 (stale materialized `kernel_ops` never reached a fixed algorithm;
  magnitude if stale: Terminal 184.7d, Hospital 564.8d, LTU_AHouse 936.0d).
- §DOOR_WINDOW_HOST_WALL — #1294 (0.5–21.8% of doors/windows started before their host wall finished,
  up to 120+ days early → 0.0% everywhere).

## SETTLED — closed rulings, do not re-derive
- **Working calendar (5-day week/holidays):** CLOSED, no code. 24/7 continuous is the deliberate
  generator default (user ruling, "spec'ed early on"). P6/MSP real-calendar parsing deferred.
- **Multi-building validation:** DONE — `witness_zone_cpm_duplex.js` (small/DX-class 9/9) +
  `witness_support_invariant_all_buildings.js` (6 large fixtures, 18/18, 272k+ elements).
- **UI wiring:** DONE — zone-level detail IS the default "Generate first draft" output.
- **Captured programmes replay their own float** — we do not recompute ours over them uninvited
  (`4D_CAPTURE_AND_FALLBACK.md:359`). `computeCpm`'s `fixedDates` is the established pattern.
- **246 remaining §SUPPORT_UNCHECKED findings** are documented data limits (co-planar framing, Revit
  wall-through-slab authoring idiom, isolated railings) — warn-only, never gated. Not a live task.

## OPEN THREADS — the real punch list
1. **Terminal glass roof / §STRUCT_POOL_UNGATED** (above). The one confirmed live floater. Highest
   priority — it is exactly what the acceptance bar forbids.
2. **HHS stairs floating** — not investigated. First steps, in order: what IFC class are HHS's stairs
   (`IfcStair`/`IfcStairFlight`/proxy); what do `hasBearingBelow`/`geoGate` compute for them; is there
   a real modeled carrier (landing slab, stringers) simply unpromoted/unrecognized the same way the
   Terminal roof slabs are. EXTRACT the cause before reaching for the scaffolding explanation.
3. **HHS Level-3 doors "come on in a split moment"** — the mp4 predates #1294, and the schedule data
   shows no Level-3-specific anomaly (door window 0.94d vs L1 1.61d/L2 1.11d; 1427/1505/1783 elements
   per storey). Two untested hypotheses: (a) it was simply the pre-#1294 bug, (b) `cinema_maxq.js`
   frame-to-schedule-time pacing is non-uniform there. Re-verify against a FRESH post-#1294 bake.
4. **⛔ Item 6b (BLOCKED, user's call):** should the AUTHORED Gantt bar windows (`schedule_author.js`
   §PHASE_OVERLAP_BAND — what a PM sees and drags) also serialize to match the two-tier DISPLAY
   reality (§TIER_SERIAL/§TIER2_AFTER_TIER1)? Changes bar-date semantics for every future generated
   schedule. Surface fresh, don't default silently.
5. **⛔ LTU_AHouse canonical vintage (BLOCKED, user's call):** `_extracted.db` (old, 71MB) vs
   `_meta.db`/`_geo.db` (new split, live-served) — re-extract to unify, or retire one? Verification
   has been using the live-served pair as a pragmatic default; the architecture question is undecided.
6. **⛔ Two product judgment calls, neither decided:** (a) door/wall gate tolerance — currently zero;
   real trades sometimes set frames as the wall goes up (~1 day overlap?). Measured violations were
   weeks-to-months, so this is refinement, not correctness. (b) temporary works/shoring — this
   codebase has zero concept of it; document as a permanent warn-only limitation (matching
   §SUPPORT_UNCHECKED) or model it explicitly?
7. **`fix/gantt-refold-hang`** — pushed, unmerged, now 4+ PRs of drift in the same region of
   `time_machine.js`. Needs a real sync against current `main`, not a naive rebase. `git worktree list` first.
8b. **§ARCH_START_TEMPO (2026-08-12, studied not built)** — the film's opening dumps whole trades in
   its first day (Terminal: 236/236 substructure slabs in 0.8d of a 375d film; Duplex: whole backbone
   + 75% of ARCH in ONE day). Levers §3.1–§3.4 in that section; §3.1 (8h vs 24h crew-day) and §3.2
   (mobilisation) are ⛔ user calls, §3.3 (lock `workInFirst10%OfCalendar` in a witness) needs none.
   Two defects found alongside: duplicate `_midairRepair`; §TIER2_AFTER_TIER1 claim vs shipped times.
9. **Modeller is out of scope by user ruling** ("Ignore modeller for now") — none of §TIER_SERIAL,
   §TIER2_AFTER_TIER1, §SUPPORT_UNCHECKED, §HANG_NEAREST exist there. If it ever matters, start from
   `modeller/str_walker_outliner.js`, same pattern as the §NOGEO_COMPOSE port (#1273).

## VERIFY-BEFORE-TRUST
Every status claim above is what was true when written (2026-08-12). Re-check `git log --oneline -15`
on `~/bim-ootb` and re-run `witness_tier_serial_display.js` (the real system-wide regression detector,
57/57 clean at consolidation time) before trusting any of it — including the "still open" items.

## ▶ 2026-08-12 — §MIDAIR_REPAIR: the acceptance bar, measured and closed (bim-ootb `fix/4d-midair-gate`)
User, this session: *"this 4D generating issue is not solved for days"* → *"all i want is not to see
a single item hanging in midair that is all"* → *"no band aid fix, just generalised solution."*

### Why six PRs of green witnesses had not delivered it
`ScheduleGate.auditFloating` counts an element as floating **only when a support it already knows
about finishes after that element starts**, and the pools it knows about are narrow: `structGrid` =
`seq<=4` + promoted slabs, `wallGrid` = walls. Two populations are therefore invisible to it, and
both are exactly what an eye reads as hanging:
- an element whose only real neighbours sit outside those pools (a post on a curtain-wall plate, a
  fitting on a proxy) — it finds no candidate at all, records `se=0`, and reports the element clean;
- **any `seq<=4` structure-pool member** — every gate in `schedule_gate.js` runs in `placeNonst`,
  so structure is never support-checked in either direction (this is §STRUCT_POOL_UNGATED, named in
  the third pass, now confirmed as the dominant cause).

**Measured directly on the DISPLAY timeline** (`viewer/tests/probe_midair_census.js` — the times
`kernel_ops` is written from, i.e. what the movie plays), before any fix:

| building | total | appear with NOTHING they touch on screen | orphans (touch nothing anywhere) |
|---|---|---|---|
| Terminal | 48,428 | 161 | 7 |
| Hospital | 63,182 | 165 | 35 |
| Duplex | 1,119 | 19 | 1 |
| HHS_Office_Federated | 6,839 | 156 | 36 |
| Clinic | 16,071 | 345 | 27 |
| LTU_AHouse | 122,330 | 4,605 | 865 |
| JKR | 8,985 | 110 | 1 |
| **total** | **266,954** | **5,561** | **972** |

Every shipped witness was green throughout. That is the gap between "the witnesses pass" and "the
movie is right" — and it is the whole reason this lane felt unsolved for days.

### The rule (one sentence, class-blind and pool-blind)
**An element may not appear before the first element it physically touches appears.** Contact = the
union of the three relations the shipped gates already model, with no class or pool filter:
bearing-below, carrier-above, embedded (S spans my whole height at my XY). Exempt: an element that
IS the ground layer of its own footprint (nothing overlapping it starts lower) — unmodelled soil,
the same exemption §SUPPORT_UNCHECKED 1c already carries.

Why it is safe rather than another reshaping: it is the WEAKEST rule that closes the gap — FIRST
(min) contact, not last (max) — so it fires only when EVERY neighbour is still invisible and cannot
re-time the 99% already resting on something. It only ever moves elements LATER (monotonicity, the
property §TIER_SERIAL W-TS-3 depends on, holds by construction). It terminates: every raise assigns
some other element's CURRENT start, so the global maximum never grows. It lives in
`time_machine.js _midairRepair()`, called right after `_twoTierRemap` — the last layer before
`kernel_ops` (a generative-layer repair would be undone by the Tier-2 shift moving a carrier out
from under its consumer).

**Tier-1 serialization loses to support order**, which is this file's own established doctrine, not
a new licence: §TIER_DAG_WINS already accepts backbone elements crossing a phase window when the
support DAG forces it. `t1Moved` reports that population every run.

### Result — `viewer/tests/witness_midair_zero.js`, 22/22 PASS, 7 buildings, 266,954 elements
`§MIDAIR_REPAIR` per building (moved / sweeps / residual / t1Moved / maxShiftDays / ms):
Terminal 175/3/**0**/124/103.0d/464ms · Hospital 175/4/**0**/137/425.1d/813ms ·
Duplex 19/3/**0**/15/3.8d/23ms · HHS 166/6/**0**/100/47.9d/86ms · Clinic 540/3/**0**/459/177.4d/173ms ·
LTU_AHouse 5024/5/**0**/1314/1628.7d/1829ms · JKR 112/4/**0**/110/26.8d/145ms.
`residual=0` everywhere = **zero elements appear before the first thing they touch**, judged by an
INDEPENDENT census in the witness (it re-derives contact/ground geometry itself, so a mis-wired or
no-op repair FAILs instead of self-certifying — the §DOOR_WINDOW_HOST_WALL lesson). W-MZ-3: nothing
moved earlier on any building. W-MZ-4: orphans locked per building. Cost is a one-time ~0.5–1.8s at
generation on the largest models.

### Two live reports settled by measurement, not by story
- **HHS stairs "hanging in midair" — CONFIRMED, root-caused, FIXED.** 4 flights authored as
  `IfcSlab` (⇒ seq=4 ⇒ structure pool ⇒ no gate ever ran): 2 at bz=2.16 appeared day 1.5 with their
  first real neighbour on day 8.5; 2 at bz=5.85 appeared day 9.6 against day 49.7. Now 8.5d / 49.7d.
  **No temporary-works/shoring excuse was needed** — the third pass's hypothesis was wrong, it was
  the structure-pool blind spot. (`probe_named_element_times.js`, BLD=HHS_Office_Federated NAMEQ=Stair)
- **Terminal glass roof was NOT floating.** Each of the 5 `Basic Roof:Glass` slabs (bz≈22.6, day 7.2)
  has an `IfcColumn` based at 14.77m directly under it whose op starts day 3.3–5.0. The third-pass
  note was right that no gate ran and wrong that they hang. Do not re-open on the old story.
- **HHS day-23 report:** on the 122.1-day display timeline, the pre-fix hangings clustered at
  5%(d6)=17, 10%(d12)=22, 20%(d24)=2, 25%(d31)=10, 35%(d43)=37, 65%(d79)=43 — i.e. the day-12→24
  band the user was watching held 24 of them. All 156 are now zero.

### The stricter end-based bar — MEASURED, deliberately NOT enforced (decision recorded here)
§SUPPORT_CHECK's doctrine is end-based ("nothing may start before its physical support FINISHES"),
so that version was built and measured too: move every element to the first FINISH among its
contacts (frozen pre-repair ends — an end-based *fixpoint* provably diverges, since contact is
near-symmetric and each raise adds a duration instead of reusing an existing time).
**Result, why it is not shipped:** on Terminal it moved 700 elements by up to 103 days and STILL
left 624 violating; on Duplex 23 moved, 22 still violating — the contacts move too, so the bar
recedes as you chase it. Reaching it for real means serializing neighbours against each other, i.e.
the global floor gate §4D_BAND_MONOTONIC's own header rules out ("would serialize the project and
destroy the trade train"). It is also not the visual truth: `renderAtTime` shows an element from its
START (frontier = orange glow, "being installed"), so a slab arriving over a glowing half-built
column is on screen resting on something. `strictResidual` now reports that population every run
(Terminal 745, Hospital 219, Duplex 27, HHS 209, Clinic 677, LTU 7131, JKR 184) — a named, measured
limit, never a silent one. Revisit only on a real report that a half-built support reads as floating.

### Regression sweep (every log read, none exit-code-only) — logs in `/tmp/wt-tier2-cache-fix/viewer/_logs/`
`witness_tier_serial_display` 57/0 (all LOCKED baselines intact, incl. Terminal dagWins=24007) ·
`witness_door_window_host_wall` 10/0 · `witness_kernel_ops_sched_version` 12/0 ·
`witness_tm_geo_order_cycles` 5/0 · `witness_og_guard_bearing_bound` 9/0 ·
`witness_big_element_support_coverage` 36/0. Plus `witness_midair_zero` 22/0. **129 + 22 assertions,
zero FAILs.** `_GANTT_CACHE_VERSION` 10→11 and `sw.js` `CACHE_VERSION` v991→v992 both bumped in the
same commit — the two cache landmines this lane has already been bitten by, checked deliberately.

## ▶ 2026-08-12 (same session, follow-up) — the planner question, and the two real holes it exposed
User: *"is the resulting 4D JSON edit wise still in compliance to engineer planners as P6 MPP
quality? And easily changed also on Time Machine to effect back?"* Checking that instead of assuming
it found two defects — one pre-existing, one introduced by PR #1301 — both closed here.

### Answer 1: the planner-facing artefacts are untouched, provably
`schedule_author.js`, `schedule_author_ui.js`, `schedule_diff.js`, `foreign_schedule.js` have a
**zero-line diff** across this whole lane. `tasks` / `task_sequences` / `computeCpm(fixedDates)`
float+criticality / the P6+MSPDI export path all build from `ScheduleGate.computeSchedule`'s RAW
times (`schedule_author.js:352`); §MIDAIR_REPAIR rewrites only `kernel_ops` timestamps — the movie
layer. So the exported programme is byte-identical to what it was.
Measured consequence on the seam (`probe_bars_vs_ops.js`, HHS): of 6,839 elements linked to a task,
**14 (0.2%) newly play outside their own bar** because of the repair, max 18.2d on a 46d authored
span. Note 3,408 (49.8%) were ALREADY outside their bar before this lane touched anything — the
authored zone rollup and the display timeline have always been two calendars. That pre-existing
divergence is item 6b's territory, still the user's call, now with a number.

### Hole 1 (introduced by #1301, fixed here): the repair traded one defect class for another
Moving an element later so it stops hanging can leave a DEPENDENT starting before that now-later
support FINISHES — which is exactly what `auditFloating` counts. **Measured across the repair:
Hospital 0→135, Clinic 1→356, LTU_AHouse 334→1100, Terminal 8→102, JKR 81→158, HHS 0→11, Duplex 0→9.**
A joint fixpoint was built and **rejected on its own numbers**: alternating the shipped
`_tierAuditRegate` sweep with the midair fixpoint ran 4 rounds, pushed 7,650 times, still ended
Hospital at 140, and cost 0.8s → 14.8s. The two rules genuinely fight (one keyed on a contact's
START, the other on a support's END, and the contact relation is not a DAG). **Do not re-attempt
that shape.** The trade is now LOCKED per building in `witness_midair_zero.js` W-MZ-8 and printed in
every `§MIDAIR_REPAIR` line as `auditFloatingAfter=` — visible, never silent. The structural fix for
both at once is gate-layer (§STRUCT_POOL_UNGATED), still open.

### Hole 2 (pre-existing, older than this lane, fixed here): the lock gate demanded absolute zero
`verifyGanttIntegrity` returned `ok: n === 0`. Measured pre-repair `auditFloating` on the shipped
buildings: Terminal 8, Clinic 1, JKR 81, LTU_AHouse 334 — the documented warn-only tails. So
**🔓→🔒 lock-back was already refused on 4 of 7 buildings for a freshly generated, UNEDITED
schedule**; a planner there could never re-lock after any edit. §MIDAIR_REPAIR would have widened
that to all 7. Fixed as **§GANTT_LOCK_DELTA**: `captureLockBaseline()` snapshots {floating, midair}
on unlock (the state the planner inherited) and the lock refuses only on an **increase** in either.
Absolute counts are still reported, so the known tails stay visible instead of being defined away.

### And the lock gate now judges by the same rule the generator enforces
`verifyGanttIntegrity` also runs `_midairAudit` (same `_contactGraph`, no mutation), so a dragged bar
that re-creates a hanging is REFUSED — `auditFloating` alone cannot see that population, which is the
whole §MIDAIR_REPAIR finding. Round-trip is otherwise unchanged: drag → `retimeTaskElements` →
§GANTT_RETIME_RESYNC → lock verifies → Undo restores.

### Merged + live
**PR bim-ootb#1303 MERGED (`add18e5`), GH Pages deploy success — verified by content on the served
files, not by PR status: `viewer/sw.js` serves `CACHE_VERSION="v993"`, `viewer/time_machine.js`
serves `captureLockBaseline` + `_GANTT_CACHE_VERSION=11`.** Branch sync note: the follow-up was
built on the SAME branch #1301 was squash-merged from, so `origin/main` came back as add/add
conflicts on both new test files (the squash-merge history collision CLAUDE.md warns about) — take
the branch side (it is the superset), and on `sw.js` keep the HIGHER version. Start the next
follow-up off fresh `origin/main`.

### Witnesses
`witness_midair_zero.js` **38/38** (W-MZ-6a/6b lock-gate wiring, W-MZ-7 the judge catches a
re-introduced hanging — moved 1 element 5d before its first contact, W-MZ-8 the trade locked per
building). `witness_gantt_lock_integrity.js` **all green**, including G-LI-2d (a real bad drag still
breaches: +1 floating, +1 midair) and G-LI-4 (Hospital-scale lock audit 1,005ms / 63,415 elements).

## ▶ 2026-08-12 (fourth session, STUDY ONLY — nothing implemented) — §ARCH_START_TEMPO: why the opening of the film is too fast
User: *"recall back the prompts/# on the 4D PERFECTION, as the ARCH starting first day part is too
fast. As this is always problematic, study first"* … *"solution has to be after understanding
previous work or else we be in vicious cycles"* … and, on perf: *"also the loading timeline, look at
perf issue but note another session is studying overall and fixing perf issue, so just make observation."*

**No code was changed. No witness was added. Every number below is measured**, on `origin/main`
`add18e5` (#1303), against the real shipped fixtures.
- Probe (new, read-only, committed here — not in bim-ootb): `scripts/probe_arch_start.js`.
  `VIEWER_DIR=~/bim-ootb/viewer BLD_DIR=~/bim-ootb/buildings node scripts/probe_arch_start.js`
  It runs the SHIPPED functions (`_buildXrayElements` → `ScheduleGate.computeSchedule` →
  `_twoTierRemap` → `_midairRepair`), sliced out of `time_machine.js` exactly the way
  `probe_midair_census.js`/`witness_midair_zero.js` do — no re-implementation of the physics.
- All three rows (RAW / REMAP / REPAIR) share ONE epoch. A per-row epoch silently re-zeros the axis
  and fakes an "element moved earlier" that never happened — caught while writing this.

### §0 FIRST: what previous work already settled, so this doesn't loop
The burst is **not a new bug and not a regression.** It is the named, accepted consequence of a
ruling the user made on 2026-08-06, and every mechanism below was put there deliberately by an
earlier pass:
1. **§CPE_BUILDUP_WORK_PACED** (cinema_maxq.js) used to hide it: the film advanced by WORK, so
   "10% of the film = 10% of the building" regardless of how the schedule clustered.
2. **§CPE_BUILDUP_EVEN_TEMPO (2026-08-06) RETIRED that**, on the user's own words ("Should be even
   throughout — separation of concern. Let the user play with the sticks and timings"). Its header
   states the trade in advance: *"THIS IS A REVERSAL … the burst … (a quarter of the Hospital model
   appearing in the first 5% of the film) returns wherever a schedule clusters its elements."*
   `BUILDUP_EVEN_TEMPO = true` is live; the film clock is now **linear in calendar days**.
   ⇒ **Do not "fix" this by flipping that flag back.** It was decided, with reasons. The consequence
   is that any front-loading left in the SCHEDULE now maps 1:1 onto screen time — so the schedule is
   the only correct place to work, which is what this file is for.
3. §4D_BAND_MONOTONIC (2026-08-02) already ruled OUT a global floor gate ("would serialize the
   project and destroy the trade train"). §TIER_SERIAL/§TIER2_AFTER_TIER1 (2026-08-11) then made the
   BACKBONE serial anyway — that is as far as serialization was taken, deliberately.
4. §PHASE_DURATION/§PHASE_OVERLAP_BAND (2026-08-04) exist to fix the OPPOSITE complaint (Terminal's
   Architecture used to start at day 1,189 of 1,264). Anything proposed here must not walk that back.

### §1 The measurement — the DISPLAY timeline (what `kernel_ops` plays), post-`_midairRepair`
| building | film span | ARCH window | starts on day ≤1 | what those day-1 elements ARE |
|---|---|---|---|---|
| Terminal | 375.2d | 96.6–191.5d | 237 (0.5%) | **236 of 236 Substructure slabs — the ENTIRE substructure, in 0.8 days** |
| Hospital | 1168.7d | 306.6–861.7d | 57 (0.1%) | 57 `IfcFooting`; all 553 Substructure elements inside the first 19.5d (1.7% of the film) |
| Clinic | 399.8d | 103.7–274.6d | 57 (0.4%) | 56 `IfcFooting` + 1 slab; 100% of Substructure inside the first 6.2d |
| HHS_Office_Federated | 122.1d | 33.6–95.1d | 65 (1.0%) | 54 `IfcColumn` + 11 `IfcSlab`; **51.6% of ALL Superstructure inside the first 10% of the film** |
| JKR | 110.1d | 26.0–57.7d | 57 (0.6%) | 48 `IfcColumn` + 9 `IfcSlab` |
| LTU_AHouse | 1855.1d | 216.7–1940.6d | 49 (0.0%) | 31 `IfcColumn` + 14 `IfcSlab` + 4 `IfcBeam` (film day 0 = raw day 86.0 — the repair shifts the whole start) |
| **Duplex** | **18.0d** | **0.4–7.8d** | **80 (7.1%)** | **11 footings + 25 Superstructure + 44 Architecture (40 walls, 2 doors, 2 stair flights) — i.e. the whole backbone plus 3/4 of ARCH, in ONE day** |

**Two different "ARCH on day 1" facts, and they live in two different artefacts. Both are real:**
- **(A) The movie.** ARCH literally starts on day ~0 only on **Duplex** (0.4d of an 18d film, with
  **75.2% of all Architecture starting inside its own first day**). On the big buildings ARCH starts
  22–33% in (Terminal 26%, HHS 28%, JKR 24%, Clinic 26%, Hospital 26%). What IS on screen in the
  first day everywhere else is the **backbone start** — and that is what looks too fast: Terminal's
  entire substructure is 0.2% of the film (**under 1 frame of a 360-frame bake, ~2 frames of an
  820-frame one**), Clinic's is 1.6%, Hospital's 1.7%.
- **(B) The Gantt drawer (authored bars, `schedule_author.js` §PHASE_OVERLAP_BAND).** There the ARCH
  bar starts at **day 2 (Duplex, of 36d) · 5 (JKR, of 55d) · 11 (Terminal, of 236d) · 12 (Clinic,
  of 295d) · 14 (HHS, of 97d) · 24 (LTU, of 2194d) · 39 (Hospital, of 803d)** — every one of the six
  phases has started inside the first ~15% of the programme on every building.

### §2 The mechanism, read from code — three independent multipliers, all pre-existing
**M1 — the display clock spends an 8-hour crew-day in 24 wall-clock hours (a hard 3×).**
`schedule_author.js _installSecs` derives each element's seconds from `secsPerUnit = 28800 / productivity`
— 28,800 s = one **8-hour** crew-day, and `materializeDefault` divides by `28800 * max_crews` for the
authored bar width. But `schedule_gate.js place()` spends those same seconds as **continuous
wall-clock** (`dur = installSecs * scaleFactor * 1000`, `scaleFactor` = 1 for any project ≥10 raw
days, `time_machine.js:4853`), against `fullDayMs = 24*3600000` — the code even says so:
`// Round the clock — 24/7, no weekends` (`time_machine.js:4847`).
*Arithmetic check against the measurement, Terminal Substructure:* 236 `IfcSlab` × (28800/35 =
822.9 s) ÷ 3 `CONCRETE_GANG` crews = 64,732 s = **0.75 d** — measured `Substructure=[0.0..0.8]d`. ✓
The same labour on the 8-hour day its own rate table is written in is **2.25 d**; on a 5-day week,
**3.15 d**. So the film runs the backbone **3× (or 4.2× vs 5-day/8h) faster than the rate table's own
definition of a crew-day.** ⚠ This is adjacent to a SETTLED ruling — "24/7 continuous is the
deliberate generator default" — but that ruling was about **weekends/holidays (the calendar)**, not
about a crew working **24 h**. The two got conflated in one constant.

**M2 — day 0 is full crew strength with nothing to wait for.** Every ground-layer element is
ungated by construction (`geoGate` finds nothing below it), so at t=0 the only limiter is the crew
cap. There is **no mobilisation, no site setup, no procurement lead, no ramp-up** anywhere in the
model — `baseMs` is simply when everything can start.

**M3 — the crew cap is the ONLY spreader, and it is small and project-wide** (§CREW-CAP, 2026-07-18:
`CONCRETE_GANG` 3, `MASON`/`CARPENTER`/`ELECTRICIAN`/`PLUMBER`/`HVAC_TECH` 2, `ROOFER` 1,
`MAX_CREWS_DEFAULT` 3). Combined with M1 each crew turns over ~3× its rated daily output, so a whole
early band drains in hours: Duplex's 79 masonry-ish ARCH elements = 79 × 2,400 s ÷ 2 MASON crews =
1.1 d (would be 3.3 d at 8 h).

**M4 — the authored bars are front-loaded by a different rule entirely.** `materializeDefault` walks
`_cursor += p.lagDays` where `lagDays = ceil(widthDays / numBands)` — each phase starts after the
previous one clears ONE band, never after it finishes. That is textbook flowline and was the correct
2026-08-04 fix, but with 12–17 bands it puts every trade's bar at the far left. It also means the two
artefacts are **two different calendars** (already named as OPEN THREAD 6b — now with numbers):
ARCH bar day 11 vs ARCH movie day 96.6 on Terminal; day 39 vs 306.6 on Hospital; day 24 vs 216.7 on
LTU. Programme totals differ too (authored/display): Terminal 236/375d · Hospital 803/1169d ·
Clinic 295/400d · JKR 55/110d · HHS 97/122d · LTU 2194/1855d · Duplex 36/18d.

### §3 Levers — measured, NONE implemented, each needs its own spec section + a user pick
1. **Spend labour on the crew-day the rate table is written in** (M1). One constant, but it
   multiplies every building's display span ~3× and re-times every `kernel_ops` — needs
   `_genVersion` + `_GANTT_CACHE_VERSION` + `sw.js` bumps and a re-measure of every LOCKED baseline
   in `witness_tier_serial_display.js`. Biggest single effect on "too fast", smallest diff.
   ⛔ Needs the user's call because it refines the SETTLED 24/7 ruling (calendar vs shift-length).
2. **Mobilisation / ramp-in before the first element** (M2) — cannot be EXTRACTED from any building
   DB. It would be a named business assumption (e.g. "no trade before day N", or crews arriving over
   the first N days). ⛔ User's number, or it is invention.
3. **Make the front-load visible instead of implicit.** `window.tmWorkSchedule()` already computes
   and logs `workInFirst10%OfCalendar` (`time_machine.js:8333`, "10.0% would be evenly spread").
   Nothing pins it, so it can drift silently. A witness locking it per building is a zero-risk first
   step and the only lever here needing no user decision.
4. **Item 6b (authored bars vs display timeline)** — unchanged, still the user's call, now with the
   two-calendar numbers above.

### §4 Three incidental findings, verified while measuring (not part of the ask)
1. **`_midairRepair` is defined TWICE in `time_machine.js` at `origin/main`** (`:4248` refactored
   onto `_contactGraph`, `:4391` the older inline copy — the #1303 add/add branch-side merge). JS
   hoisting means **the browser runs the LAST one (`:4391`)** while `sliceFn` (probe + every witness)
   picks the FIRST. **Measured: both produce byte-identical display times on Duplex and HHS**, so
   this is dead duplication, not a behaviour split — but the witnesses are proving the copy that
   does not ship, and one edit to the "wrong" one would be silent. ~145 dead lines.
2. **§TIER2_AFTER_TIER1's "MEP Rough-in start == Architecture end, verified exact on 7 buildings" no
   longer holds on the shipped timeline** — because `_midairRepair` (#1301) runs AFTER `_twoTierRemap`
   and pushes backbone elements later (`t1Moved`, already reported per run). Measured ARCH tail
   INSIDE the MEP window, post-repair: **Hospital 258.0d · Clinic 67.8d · HHS 26.2d · LTU 938.7d**
   (Terminal/JKR/Duplex still exact at 0). Same for the backbone itself: Superstructure now runs
   179.4d (Hospital) / 100.7d (Clinic) / 33.5d (HHS) past Architecture's start. `witness_tier_serial_display.js`
   asserts on `_twoTierRemap`'s output only — it never runs the repair, so it cannot see this. Not
   necessarily wrong (§TIER_DAG_WINS accepts support order beating serialization), but the *claim* in
   this file's ledger is now stronger than what ships. Fix the claim or the witness — decide, don't drift.
3. **Perf observation only (another session owns the perf lane — `prompts/CPE_4D_PERF_MEM_STUDY.md`
   / `CPE_4D_PERF_MEM_FINDINGS.md`; nothing here to be actioned by this lane).** Node-side, one
   generation pass: **`_twoTierRemap` dominates everything else** — LTU_AHouse 62.0 s, Terminal
   25.6 s, Hospital 5.4 s, Clinic 1.6 s, JKR 1.8 s, HHS 0.8 s. Against `computeSchedule` (9.1/1.4/1.7 s),
   `_midairRepair` (3.7/0.9/1.9 s), x-ray build (1.3/1.2/1.0 s), authored bars (2.3/1.7/1.3 s).
   Full-pass totals: LTU 80.1 s · Terminal 31.7 s · Hospital 12.1 s. The shape is structural, not a
   fixture artefact: `_twoTierRemap` runs up to 6 iterations × `_tierAuditRegate` (≤16 sweeps each),
   and `_tierAuditRegate` rebuilds its spatial grid every call. Browser numbers will differ; the
   ratio is what matters.

### §5 What this study did NOT do
No schedule change, no gate change, no witness, no PR, no cache bump. Levers §3.1–§3.4 are unbuilt
and unspec'd. The probe is read-only and lives in bim-compiler only — bim-ootb is untouched.

## §GANTT_PHASE_CLOBBER — the captured overlay overwrites `phase` with the TASK NAME (2026-08-12, FIXED)
**Symptom, user:** *"at first load, the TM 4D gantt schedule has nice coloring looks OK but on refresh
it goes away"* → *"U have to hunt back those pretty colors in the Gantt Chart bars of TM."*

### One line, three broken things — all provable from the user's own log
`time_machine.js:5238`, inside the captured/authored overlay:
```js
p.phase = w.name;    // real task name → shows in mini-Gantt
```
`w.name` is the TASK name. Since zone-level authoring became the default, `materializeZones` names
its tasks **`"<Phase> — <Storey>"`**, so every op's `parameters.phase` becomes
`"Architecture — Level 1"` instead of `"Architecture"`. The user's log prints it verbatim:
```
§AUTHOR_ZONES schedule=SCH_AUTHORED zones=35 … §GANTT_SOURCE captured tasks=35 covered=63415
§GANTT_ROW_ORDER phases=["Architecture — Level 1","Architecture — Level 2",…,"Superstructure — Level 7A"]
```
Everything downstream keys on that field:
1. **Colour** — `PHASE_COLORS[task.phase] || '#888'` (`:6896`) misses on every bar → all 35 bars grey.
   Also `PHASE_INK[task.phase] || '#fff'` and `PHASE_SHORT[task.phase] || task.phase.substring(0,3)`
   (`:6950`), so §GANTT_PALETTE's ink and short-codes go with it.
2. **Row order** — `_phaseRank()` is `_ROW_PHASE_ORDER.indexOf(p)`; every lookup returns -1, so every
   row ranks equal and the sort falls through to alphabetical. The user's `§GANTT_ROW_ORDER` shows
   exactly that: Architecture, Finishes, MEP Final, MEP Rough-in, Substructure, Superstructure —
   **Substructure 5th.** That is §GANTT_ROW_ORDER (K1)'s original bug back verbatim, and K1 exists
   because the user reported it once already: *"Last session was a mess putting substructure which
   has above ground appearing first."* It regressed silently — the K1 log line prints the broken
   order and no gate reads it.
3. **Dashboard phase bars** — `§DASH_PHASE`/`tm-dash-phases` buckets by the same field and then
   filters through `PHASE_ORDER`; with 35 name-keys and 0 matches, the phase progress section
   renders empty. There is not one `§DASH_PHASE` line in the user's whole session.

### Why "OK on first load, gone on refresh"
The colour survives exactly as long as the ops carry engine phases. Whether the overlay stamps names
depends on whether an authored/captured schedule is present and covering when `injectGantt` runs —
which on a first cold open it is not (the schedule is materialized in the same pass), and on a warm
reopen it is (persisted zone tasks, `§GANTT_SOURCE captured tasks=35 covered=63415 pct=100`).

### Fix — write the name where the name belongs
`p.taskName = w.name;` instead of `p.phase = w.name;`. The mini-Gantt already reads the name from a
different route entirely — `buildGanttTasks` sets `taskName` from the task index (`:5694`) and the
bar detail header renders `bar.taskName || (bar.phase + ' — ' + bar.storey)` (`:6716`) — so the
overlay's clobber was never what made the name visible. Nothing is lost; colour, ink, short-code, row
order and the dashboard all key on a real phase again.

### Witness — `witness_gantt_phase_palette.js` (W-PHASE-KEY)
Names the issue: **the value the palette keys on must be a phase, not a task name.** Runs the shipped
`PHASE_COLORS`/`PHASE_INK`/`PHASE_SHORT`/`_ROW_PHASE_ORDER` against the user's own strings.
G-PAL-1 (RED pre-fix): `"Architecture — Level 1"` → colour `#888`, rank 6 (unranked).
G-PAL-2: all six engine phases resolve to a real colour and a rank < 6.
G-PAL-3 (source): the captured overlay must not assign the task name into `p.phase`.

### §DAY_GAP — the burst has a matching DEAD AIR, measured (2026-08-12, user-reported, STUDY ONLY)
**User:** *"at Day 14 onwards nothing happens and when scrub forward it jumps to Day 48 with
construction resuming"* → *"Day 1-14 too much too fast and then delay build up until Day 48 is
telling. Day 1-14 should stretch till before Day 48."*

Measured on the shipped display timeline (`scripts/probe_arch_start.js` §DAY_GAP, Hospital, all
63,182 elements, post-`_twoTierRemap`+`_midairRepair`). Reported as PERCENT OF FILM because the
browser maps this 1,168.7-day generated timeline through one global affine into the captured window
(their run: a 126-day film) — an affine preserves relative position, raw days are not comparable.
Their day 14→48 of 126 = **11%→38% of the film**.

```
§DAY_GAP_HIST Hospital startsPer5%=[2228,0,0,915,0,4452,640,2060,6437,2523,7305,9560,7630,9182,10056,149,0,1,0,44]
                                     0-5  5-10 10-15 15-20 20-25 …
§DAY_GAP Hospital longestEmptyRun=12% at 76%..88% of the film — zero element starts
```
Bursts separated by dead air, matching the report exactly:
- **0–5%** of the film: **2,228 starts** (3.5% of the model) — the §ARCH_START_TEMPO burst.
- **5–15%**: **zero starts**, 10% of the film with nothing happening. In their 126-day film that is
  day 6.3 → 18.9 — the "Day 14 onwards nothing happens" they saw.
- **15–40%**: 915 · 0 · 4,452 · 640 · 2,060 — a trickle, with a second dead 5% band at 20–25%.
- **40%+**: the real ramp (6,437 → 10,056 starts per 5%).

Phase windows behind it (§ARCH_PHASE REPAIR): `Substructure=[0.0..19.5]d n=553` ·
`Superstructure=[10.3..486.0]d n=2603` · `Architecture=[306.6..861.7]d n=17236` ·
`MEP Rough-in=[603.7..1168.7]d n=38362`. Substructure's entire 553 elements are spent in the first
**1.7%** of the film; Architecture cannot start until **26%**; MEP Rough-in until **52%**. And 64.3%
of Superstructure's own elements start inside the first 10% of the film (§ARCH_PHASE_FRONT), leaving
the rest of its 296-day window empty. So the gap is not a data hole — it is the interval between
"the tiny backbone is finished" and "the next tier is allowed to begin".

**Diagnosis:** each phase's elements are packed at the FRONT of a window far longer than the
crew-limited work inside it. Burst, then dead air, per phase. Same root as §ARCH_START_TEMPO, seen
from the other end.

**Lever (NOT implemented — schedule-side, not film-side per §CPE_BUILDUP_EVEN_TEMPO):** spread each
phase's starts across its own already-computed window instead of packing them at its front. No new
data needed — window boundaries and element order both already exist; only placement inside the
window changes, and a monotone map inside a window preserves programme totals, phase order and
support order by construction.
⛔ Confirm before building: does *"Day 1-14 should stretch till before Day 48"* mean stretch each
phase's work to fill its window up to the next phase's start — which is what this lever does?

---

### §DAY_GAP_WIP — ⛔ THE LEVER ABOVE IS **DO NOT BUILD**. Its premise is measured false (2026-08-12)

The blocking question above was handed over unanswered. Answering it required one measurement
§DAY_GAP never took, and that measurement kills the lever. **§DAY_GAP counts element STARTS. It
never asked how many elements are IN PROGRESS.** Those are different questions with opposite fixes:
work-in-progress through a gap means the programme is honest and moving starts would fabricate
dates; zero work-in-progress means the gap is genuinely empty. Added `§DAY_GAP_WIP` + `§DAY_GAP_DUR`
to `scripts/probe_arch_start.js` and ran all 7 buildings off `origin/main`:

```
                meanDur   p50      spanD    sumWorkDays  occupancy   zeroStartBands / alsoZeroWork
Hospital        0.016d   0.015d   1168.7      1035.3       88.6%          55 / 55   minWIP=0 maxWIP=0
Terminal        0.008d   0.002d    375.2       377.9      100.7%          40 / 40   minWIP=0 maxWIP=0
Clinic          0.024d   0.022d    399.8       380.9       95.3%          46 / 46   minWIP=0 maxWIP=0
LTU_AHouse      0.019d   0.022d   1941.1      2328.7      120.0%          42 / 42   minWIP=0 maxWIP=0
HHS_Office_Fed  0.023d   0.022d    122.1       157.0      128.6%          39 / 39   minWIP=0 maxWIP=0
JKR             0.017d   0.011d    110.1       151.1      137.3%          40 / 40   minWIP=0 maxWIP=0
Duplex          0.023d   0.022d     18.0        26.3      146.0%          32 / 32   minWIP=0 maxWIP=0

§DAY_GAP_WIP Hospital meanInProgressPer5%=[2,0,0,1,0,1,0,0,0,0,2,3,3,3,3,0,0,0,0,0]
```

**Three findings, each fatal to the specced lever:**

1. **There is no surplus window to spread into.** `occupancy = sumWorkDays / spanD` is **88.6%–146%
   on every building** — the total work-days already ≈ (or exceed) the whole programme span. The
   lever's stated premise, *"a window far longer than the crew-limited work inside it,"* is false.
   Spreading starts would redistribute the same ~1-element-at-a-time trickle and convert one 12%
   dead band into dozens of small ones. The film would read empty *everywhere* instead of in one
   place — strictly worse, and it would have looked like progress on the histogram.

2. **The cause is DURATION, not placement.** `p50` element duration is **0.011–0.022 d ≈ 16–32
   minutes**, near-identical across all 7 buildings regardless of type, size or discipline. Elements
   are POINT EVENTS: they pop into existence and are done. That is why **every** zero-start band is
   also zero-work — 294 bands across 7 buildings, `minWIP=0 maxWIP=0` without a single exception.
   Nothing is ever visibly under construction, so between bursts there is genuinely nothing to show.
   This matches `time_machine.js:4816`'s own admission of a **"SAME flat duration regardless of real
   size"** and the parked weighting lane's finding that *50–71% of every building's labour-seconds
   carry no size signal.*

3. **It would have traded accuracy for polish.** Monotone re-timing of computed start dates so the
   film looks even is re-timing a schedule for VIEWING reasons — the exact thing
   `feedback_schedule_accuracy_over_movie_polish` rules against (*"a beautiful film of a WRONG
   schedule is worse than a plain film of a right one"*), and the film-side twin of this was already
   retired deliberately as §CPE_BUILDUP_EVEN_TEMPO.

**Answer to the user's question, therefore:** *"Day 1-14 should stretch till before Day 48"* is a
statement of the **desired outcome**, not of the mechanism. It should NOT be delivered by filling
each window to the next phase's start. Delivered that way it fabricates dates and still shows an
empty film. Delivered by giving elements their real durations, the same outcome falls out for free —
work that occupies 3 days instead of 32 minutes fills the gap *because it is actually happening.*

**§DAY_GAP and the weighting lane are the same bug.** `§LABOR_QUANTITY_WEIGHT` +
`§HEAVY_MEMBER_SPEED_LIMIT` (spec-only, user already ruled: 24h crew-day norm + JSON shift override
for imports) is the real lever. The data to do it is already shipped and unused: `rates.js`
`LABOR_RATES[trade].productivity` gives units/day per IFC class, with `crew_size` and `max_crews`
per trade, and `§CREW-CAP` (time_machine.js:5020) already reads `max_crews`. Deriving duration from
quantity ÷ productivity is EXTRACT, not invention — it raises schedule accuracy instead of trading
it away, and the gap closes as a side effect rather than as the goal.

**Do not re-derive this.** The probe now carries `§DAY_GAP_WIP`/`§DAY_GAP_DUR` permanently; re-run
`VIEWER_DIR=/tmp/vw BLD_DIR=~/bim-ootb/buildings node scripts/probe_arch_start.js` after any
duration change and watch `occupancy` stay ~100% while `meanInProgressPer5%` rises off the floor —
that, not the starts histogram, is the number that says the film has something to show.

---

### §DAY_GAP_PHASE_OCC — ⚠ TWO CORRECTIONS TO THE SECTION ABOVE, and the real defect (2026-08-12)

The §DAY_GAP_WIP section above is right that the specced lever is DO-NOT-BUILD, but **two of its
supporting claims were wrong and are corrected here.** Both were caught by finishing the measurement
rather than by review.

**Correction 1 — the "every zero-start band is also zero-work" evidence was a SAMPLING ARTEFACT.**
The first cut sampled ONE instant per band (`s <= t && e > t` at the band midpoint). On Hospital a
1% band is 11.7 days while p50 element duration is 0.015d, so a single instant has a ~0.1% chance of
landing on any given element — with true concurrency near 1, an instantaneous sample returns 0 by
luck. Re-measured by OVERLAP (element-days falling inside the band ÷ band width). **The conclusion
survives, on 5 of 7 buildings exactly and 2 approximately** — Hospital/Terminal/Clinic/LTU/JKR still
show every zero-start band at zero work; Duplex 31 of 32, HHS 36 of 39. But the original
`minWIP=0 maxWIP=0` line was not evidence, and is not how this should ever have been measured.

**Correction 2 — "the cause is DURATION" was WRONG. Durations are already productivity-derived and
arithmetically correct.** `getInstallSecs` (time_machine.js:4824) computes **`28800 / productivity`**
seconds per element — an 8-hour crew-day divided by units-per-day. Measured p50 = 0.022d = 1,900s ≈
15 units/day, sitting squarely inside the shipped table's range (`IfcDuct:18`, `IfcPipe:25`,
`IfcLightFixture:20`, default 10). A productivity of 18 units/crew-day genuinely IS 27 minutes per
unit. There is no missing default and nothing to invent.

**THE REAL DEFECT — measured: the WINDOW is wrong, not the duration and not the placement.**
`§DAY_GAP_PHASE_OCC` = work-days inside a phase ÷ the width of the window that phase was given:

```
Hospital    Substructure=157.8%(work=30.7d win=19.5d n=553)   Superstructure=24.4%(116.0d/475.7d n=2603)
            Architecture=18.1%(100.7d/555.1d n=17236)          MEP Rough-in=128.5%(725.9d/565.0d n=38362)
            MEP Final=323.2%(43.5d/13.5d)                      Finishes=119.9%(18.4d/15.3d)
LTU_AHouse  Substructure=4.7%(9.7d/204.7d n=238)               Superstructure=14.6%(237.2d/1627.6d n=6268)
            Architecture=10.9%(188.7d/1723.9d n=6586)          MEP Rough-in=174.3%(1636.6d/939.1d n=78940)
Clinic      Superstructure=21.2%(42.7d/201.2d)                 Finishes=11.4%(9.1d/79.8d)
Terminal    Finishes=17.7%(8.7d/49.1d)                         MEP Rough-in=97.0%(178.1d/183.7d)
```

**The structural/early phases are handed windows 4×–21× wider than their own work content, while the
MEP phases are OVERLOADED at 128–174%.** Superstructure gets 475.7 days for 116 days of work;
LTU's Substructure gets 204.7 days for 9.7. That imbalance IS the dead air — it is not a placement
problem inside a correct window, it is a window that was never derived from work content at all.
The width comes from `_twoTierRemap`'s tier serialization pushing phase ends out; the work never
grew to fill it. Global occupancy (~88–146%) hides this completely because the huge MEP counts
dominate the total — which is why §DAY_GAP_WIP's aggregate number pointed the wrong way.

This also finishes off the specced lever: spreading Superstructure's 2,603 starts across its 476-day
window would fake 4× the elapsed time to make a window look full **that should never have been that
wide.** It hides the defect instead of fixing it.

**User's question, answered (2026-08-12): "can't we have a standard default set in rates.JSON
according to world normal practice, later editable?" — YOU ALREADY DO, and it is already wired.**
`viewer/rates/sequence_rules.json` `LABOR_RATES` carries 10 trades, each with `crew_size`,
`max_crews` and a `productivity` map (units per 8h crew-day, per IFC class):
```
HVAC_TECH 2/2 · PLUMBER 2/2 · ELECTRICIAN 2/2 · STEEL_ERECTOR 4/3 · CONCRETE_GANG 6/3
MASON 3/2 · CARPENTER 2/2 · ROOFER 3/1 · FINISHER 2/2 · LABORER 1/1        (crew_size/max_crews)
```
It is already JSON, already editable, already consumed. Adding another default set changes nothing.

**But the instinct points at a real gap, in the OTHER column: `max_crews` is 1–3 for every trade.**
That is a small-job crew allocation being applied unchanged to a 63,182-element hospital and a
122,330-element LTU — and it is exactly what drives MEP Rough-in to 128–174% occupancy. A
size-scaled `max_crews` default (with the per-project JSON override the user describes) is a real,
sourced, non-invented fix for the OVERLOADED half. **Be clear about what it does and does not do:
raising crews COMPRESSES the busy phases; it does not fill the empty structural windows.** Those
need the window derivation fixed.

**Ranked, therefore:**
1. **Phase window derivation** — structural phases at 4.7–24.4% occupancy. This is the dead air.
   Window width should follow work content + support constraints, not tier-serialization push.
2. **`max_crews` scaled to project size**, JSON-overridable (the user's idea, correctly aimed) —
   fixes the 128–174% MEP overload.
3. **Do NOT build the spread-starts lever** — see above, twice over.
