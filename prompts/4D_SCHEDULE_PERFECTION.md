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
8. **Modeller is out of scope by user ruling** ("Ignore modeller for now") — none of §TIER_SERIAL,
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
