# ⚠ DO NOT REMOVE — Read the log after every run, spec-first, no invented dependency edges or rates

## Why this file exists
This is the direct continuation of `prompts/CPM_FLOAT_GAP.md`, which is now CLOSED (its mission —
scope and land real CPM/dependency edges for a generated 4D schedule — is done, PRs #1158–#1162,
all merged/auto-merging, all witnessed). Read that file first for the full history if you need it —
**do not re-derive or re-litigate anything already settled there**, this file only states what's new.

User ruling that set the current priority (bim-ootb session, 2026-08-03): the core product is
**"drop an IFC, get a probable 4D/5D movie right away"** — most users return to their own tools
(P6/MSP) afterward. So the auto-generated schedule's own accuracy is the utmost priority; P6/MSP
import/export is explicitly POC/later (a 4D-diff/variance tool already shipped on that side, PR
#1161 — see CPM_FLOAT_GAP.md, don't re-open that lane unprompted).

This file scopes what "as perfect as can be" still needs, now that the engine (real element-level
CPM, DAG-safe, movie-coherent) exists.

## What's already shipped — do not re-build any of this
- `viewer/schedule_gate.js computeSchedule()` — the element-level scheduler driving the live movie
  (support-order + crew-capped placement). Pre-existing, proven (0/3240 floating, real Hospital data).
- `viewer/schedule_author.js materializeDefault()` — 5-phase coarse generated schedule, real CPM edges.
- `viewer/schedule_author.js materializeZones()` — 71-zone (Terminal) DETAIL schedule, rolled up from
  `computeSchedule`'s real per-element times, real structurally-DAG-safe edges.
- `computeCpm(db, id, {fixedDates:true})` — zone graph's CPM float/criticality now EXACTLY matches
  the real movie's total (was a 48%-divergent approximation; fixed same session, PR #1162).
- `viewer/schedule_diff.js` — grades an imported P6/MSP schedule against our own labor-rate estimate.
- `witness_zone_cpm.js`, `witness_tm_duration_sync.js`, `erp/tests/schedule_diff_witness.js` — the
  proof trail for all of the above. Run these FIRST if you need to re-verify current state; don't
  assume drift without checking.

## The real remaining gaps, in priority order

### 1 — Working-calendar model (5-day week / holidays) — highest fidelity payoff, affects BOTH paths
Currently EVERYTHING (the movie, `materializeDefault`, `materializeZones`) computes on raw, continuous
24/7 calendar days — `_addDays()` never skips a weekend or holiday. This is not a hypothetical gap —
`erp/tests/real_xer_witness.js` already independently found and named it while comparing our CPM
against a real P6 file's own computed dates: *"our CPM omits the P6 working calendar → 49/52 start
dates land up to 59d earlier (weekend/holiday skips)... all one-directional → working-calendar, not a
logic defect."* That finding was about captured-schedule replay, but the SAME gap degrades the
GENERATED schedule's own realism: a "111-day Superstructure" computed from labor-seconds ÷ crews is a
WORKING-day count dressed up as a calendar-day count today (`28800s = 8h workday` is already the
labor-rate assumption — `_addDays` just never accounts for which calendar days are actually workable).

**What closing this needs (spec before code):**
- A calendar model: which days of the week are worked (default likely Mon–Sat or Mon–Fri — this needs
  a real ruling, don't guess a default silently) + a holiday list (may not exist for a given
  building/locale — degrade honestly, don't invent a holiday calendar).
- `_addDays(start, workingDays)` needs a working-calendar-aware sibling (or itself needs to become
  calendar-aware, audit every caller — `materializeDefault`, `scheduleContiguous`, `materializeZones`,
  `computeCpm`'s date write-back all call it) that maps a working-day COUNT onto the correct spread of
  CALENDAR dates.
- `foreign_schedule.js` already parses P6/MSPDI calendar data (`toScheduleData`'s `calendars[]`) for
  CAPTURED schedules — check whether that's already used anywhere, or just parsed-and-dropped; if the
  latter, that's the fastest path to closing the captured-side half of this gap (real calendar data
  already extracted, just not applied).
- For the GENERATED side, no captured calendar exists — a sensible working-week default is legitimate
  (not "inventing a rate," it's a real, nameable business assumption every construction schedule makes)
  but get it confirmed once, don't silently pick one and move on.

### 2 — Multi-building validation — cheap, de-risks the "state of the art" claim
`materializeZones`/the zone-CPM fix are proven on Terminal (48,428 elements, large/complex, 22 real
floor-ranks) plus regression coverage touching Hospital (63,415 elements, also large). Neither is a
SMALL building. Per `docs/internal/WalkerDoctrine.md`, small/residential buildings (SH/DX/SC class)
walk `duplex_rules.db`, not Terminal-class rules — a building with 1–3 floors and a handful of zones is
a genuinely different regime (does the zone model degrade gracefully to "1 zone per phase" the way the
phase-level code already documented it should? Prove it, don't assume it from the large-building case).

**What this needs:** run `witness_zone_cpm.js`'s pattern (or extend it) against a real small building's
extracted DB (check `~/bim-ootb/buildings/` — `Duplex_extracted.db` is the standing default fallback
DB this project already uses everywhere else, likely the fastest real fixture to reach for). Confirm:
zone count stays sane (not 1, not the full element count), edges are still a DAG, `fixedDates:true`
CPM still exactly matches the real movie total.

### 3 — UI wiring — the engine has no user-facing trigger yet
`materializeZones()` is only reachable today via a witness/console call — there is no button anywhere
that lets a real user generate or view the zone-level detail schedule. Given this session's own
priority ruling (movie first, detail second, "for the minority who want to drill in"), this is
correctly LAST, not first — but it does need to exist for anyone besides a developer to ever see this
work. Likely home: `schedule_editor_ui.js`/`schedule_author_ui.js`, alongside the existing
"Schedule now"/`generateDraft()` flow — an opt-in "detail view" toggle or a second button, calling
`materializeZones` instead of/in addition to `materializeDefault`. Keep it minimal — this project's own
established discipline this session was engine-and-witness first, UI last, don't gold-plate.

## Minor, not scoped, don't chase unless a witness surfaces a real problem
- `deriveZones`' same-floor-cross-phase edges are an adjacent-pair chain over phases sorted by real
  observed start time, not an exhaustive pairwise derivation — a deliberate simplification (see
  `schedule_gate.js deriveZones`'s own header). No evidence yet this loses anything real; only revisit
  if a witness on a new building shows a missing/wrong constraint.

## Suggested order
1. Working-calendar model (#1) — highest real-world fidelity payoff, touches both generated AND
   captured paths, and is the one gap independently corroborated by an existing witness finding.
2. Multi-building validation (#2) — cheap (mostly a witness run, not new code), de-risks claiming this
   works generally rather than just on the two large buildings tested so far.
3. UI wiring (#3) — only once the engine is validated broadly, expose it to a real user.

## Boundary, restated (carried forward from CPM_FLOAT_GAP.md — do not drift from this)
Every number here traces to real extracted data (labor rates, real quantities, real crew counts, real
storey/Z geometry) or a real, nameable business assumption confirmed once (a working-week default) —
never a plausible-looking invented value. `4D_CAPTURE_AND_FALLBACK.md:359`'s rule still stands:
captured (P6/MSP/native-IFC) programmes replay their own float, they don't get ours recomputed over
them uninvited. `computeCpm`'s `fixedDates` opt is now the established pattern for "trust real
persisted dates, use CPM only for float/criticality" — reuse it, don't reinvent a third date-handling
mode without a real reason.

## Session close-out, 2026-08-04 — this file's original 3 gaps are DONE, DON'T RE-DERIVE THEM
This supersedes the "Suggested order" section above — all three items are closed, not open.
- **#1 Working-calendar** — CLOSED, no code. User ruling: 24/7 continuous is the deliberate generator
  default ("spec'ed early on"), not a gap. P6/MSP real-calendar parsing explicitly deferred.
- **#2 Multi-building validation** — DONE. `witness_zone_cpm_duplex.js` (small/DX-class, 9/9) +
  `witness_support_invariant_all_buildings.js` (all 6 large fixtures, 18/18, 0 floating/272k+ elements).
- **#3 UI wiring** — DONE, but NOT a second button as originally scoped: zone-level detail is now the
  DEFAULT "Generate first draft" output (`schedule_author_ui.js`), replacing the coarse 5-phase draft.

**Five real, evidence-backed bugs found and fixed in the same session** (bim-ootb `main`, PRs #1163/
#1165/#1166/#1167 — #1163's branch was orphaned mid-session by an early auto-merge, #1165/#1166/#1167
are the recovery + follow-ons, all verified landed on `main` by content, not just PR status):
1. `IfcOpeningElement` scheduled as physical work (voids, not walls) — excluded, matches time_machine.js.
2. Every beam/column in a class got the SAME flat install time regardless of real size (a 60m beam and
   a 1m beam both "1 hour") — fixed via real-length redistribution, class total preserved.
3. `SEQUENCE_RULES` sequenced MEP rough-in BEFORE the building envelope (walls/doors/windows) — backwards
   from real construction discipline. Fixed across all 18 rate-template sources (`rates.js` + 15 regional
   templates + `sequence_rules.json`).
4/5. **Two of (at least) four independent copies of "what counts as physical support"** had an
   unrestricted "any wall carries anything" predicate (`time_machine.js` `_buildXraySupportCache` +
   `_ogIsCarrier`/`PHASE_OVERLAP_SUPPORT_GUARD`) — a wall is only ever a real carrier for a slab
   promoted to the roof role. MEASURED on Hospital: 5,687→1,217→0 false "hanging without support"
   defects (fix #4), 1,049→0 false pushes with only 2 of the 1,049 ever legitimate (fix #5).

**New witness**: `witness_wall_carrier_scope_all_copies.js` — the single place tracking all 4 known
copies of this predicate (not 2 separate witnesses each covering one copy). Status:
copy 1 (`schedule_gate.js auditFloating`) and copy 3 (`_ogIsCarrier`) proven here, 6/6 buildings;
copy 2 (`_buildXraySupportCache`) has its own dedicated witness (`witness_zstack_xray_staging.js`);
copy 4 (`boq_charts.html generateSchedule()`) asserted RED-by-design as a KNOWN OPEN GAP — it has no
geometric support-check at all, a different and larger problem than 1-3's over-broad predicate.

## Session addendum, 2026-08-04 (same day, later) — a real gap NOT previously documented
User asked "why can't ARCH go first" — after clarification, the real question is NOT about
construction sequence (Substructure-first stays correct, real construction physics, not up for
debate) — it's that **this schedule has no pre-construction design/planning phase at all.** A real
P6 schedule starts with Design Development → Permitting → Procurement, THEN Substructure onward.
This generator starts cold at Substructure with zero modeled lead time for design/permits/procurement
— there is no `SEQUENCE_RULES` phase, no task, nothing representing pre-construction work. Genuinely
new finding, not previously scoped anywhere in this file. STUDY, don't implement yet — no real
duration/lead-time data source has been identified for this (an invented "design takes N weeks"
number would violate the Boundary section above). NOT started.

**Write-loop hang, reported live during "Apply to 4D" on Hospital (63,415 elements)**: the browser
console log cut off immediately after `§PHASE_OVERLAP_SUPPORT_GUARD pushed=...`, right before a loop
that does 63,415 individual synchronous DB writes on the main thread (`time_machine.js` ~line 4058,
written in PR #1154, 2026-08-03 — predates this session, not caused by any fix here). Confirmed
`kernel_ops.output_guid` IS indexed (`idx_kernel_ops_guid`) — ruled out an O(n²) table-scan as the
cause. PR #1168 (merged) adds `§WRITE_LOOP_TIMING` — pure measurement, no logic change — so the next
report gives a real millisecond number. **The actual performance fix (batching/yielding so the tab
doesn't freeze) is NOT done** — deliberately deferred, needs a real timing number first and ideally
browser verification before changing a hot synchronous code path blind.

## OPEN for the next session — do not assume any of this is done
- **Pre-construction design/planning phase is entirely unmodeled** (see addendum above) — real gap,
  needs a real lead-time data source before any implementation, currently just named.
- **Write-loop performance fix NOT DONE, confirmed still hanging** (2026-08-04, live user report
  TWICE on Hospital/63,415 elements): PR #1168's `§WRITE_LOOP_TIMING` instrumentation never even
  printed either time — the freeze happens AT OR BEFORE the write loop starts, right after
  `§PHASE_OVERLAP_SUPPORT_GUARD pushed=.../63415` (`time_machine.js` ~line 4058-4065, PR #1154,
  predates this session). Next session: don't re-diagnose from zero — the timing line never firing
  is itself new information (rules out "just slow writes", points at something before/at loop start
  — check the `_ogStructGrid`/`_ogWallGrid` build + the `_allScheduled.sort` immediately before it
  too, not just the write loop itself). Needs real profiling, ideally live browser (user-approved for
  THIS specific investigation, unlike elsewhere this session).
- **Constraint-aware Gantt drag, SAME session as the hang fix** (user ruling 2026-08-04): dragging
  the Gantt chart directly is the priority UI, not a redesigned Author-4D panel — "it is the same
  underlying model". See the detailed scoping note above (self-contained bars, real DAG via
  `task_sequences`, don't let a drag hide a defect instead of fixing it).

## Session starter for the next session (paste this)
"Read prompts/4D_SCHEDULE_PERFECTION.md in full, then: (1) find and fix the Apply-to-4D hang on
large buildings (Hospital, 63,415 elements) — PR #1168's timing instrumentation never fires, so
start there, not at the write loop; (2) build constraint-aware drag directly on the Gantt chart
(same underlying model as the Author-4D wizard, don't build a second panel) — a dragged bar must
cascade its real successors or get blocked, never silently hide a real defect."
- **Constraint-aware Gantt drag** (user request, 2026-08-04): let a user drag a zone bar directly in
  the editor as a manual escape hatch when they spot a problem, instead of always needing a code fix.
  SCOPING FACT established this session, don't re-derive: a Gantt bar (zone = one phase × one real
  floor) is internally self-contained — its own elements are already correctly ordered by the fixed
  engine (`computeSchedule`, this session's fixes) — but bars are NOT independent of each other; they
  form a real DAG via `task_sequences` edges (`deriveZones`: within-phase adjacent-floor + same-floor
  cross-phase). A PURELY COSMETIC drag (no constraint checking) would let a user hide a real defect
  instead of fixing it — directly against this project's Prime Rule. The correct version (standard in
  P6/MS Project): dragging a bar either cascades its real successors with it, or gets blocked/clamped
  when it would violate a real dependency. `moveTask()` (schedule_author.js) is the existing single-
  task move verb — check whether it already has any cascade/constraint logic before designing new.
  STUDY/SCOPE ONLY — not implemented, not started.
- **`boq_charts.html` (the "4" button / HTML charts tab) is a FOURTH, fully disconnected scheduler.**
  Doesn't read `schedule_gate.js`'s real engine or `schedule_author.js`'s `tasks`/`task_elements` tables
  at all — recomputes its own coarse phase×storey schedule from raw element counts, with its own fixed
  lag constants and a `MAX_TASK_DAYS=20` cap, and TWO of its own stale hardcoded `PHASE_ORDER` arrays
  (`boq_charts.html` lines ~423 and ~560, still MEP-before-Architecture — never touched). User-approved
  direction (not yet implemented): redirect it to consume `schedule_author.js`'s real `tasks` data via
  `AnalysisSidecar.compute4D()`'s existing-but-unused `capturedFn` hook (currently passed `null` at the
  `get4D()` call site, line ~1218) instead of patching its `generateSchedule()` separately.
- **`docs/4D5DAnalysis.md` (bim-compiler) is stale.** Describes the Python `nD_engine.py` engine +
  `ghostglass.js`/`boq_charts.html` — never mentions `schedule_author.js`/`schedule_gate.js`, the engine
  this whole session perfected. Not rewritten.
- **Architectural finding, not yet acted on**: this codebase has a documented convention ("copy the
  support predicate rather than importing it") that is WHY the same bug had to be found and fixed 3
  separate times this session. The real fix is consolidation — one shared support-detection function,
  every caller uses it — not continuing to patch copies as they surface. Scoped but not started.
- Two cosmetic-only stale `PHASE_ORDER` arrays in `time_machine.js` (~line 4099 ERP-twin variance
  lookup, ~line 4670 dashboard phase-progress display order) — confirmed display-only, not wired to
  actual scheduling, left unfixed as genuinely low priority. Fix if ever touching those areas anyway.

---

# §GANTT_EDIT — the editable-drawer overhaul (SPEC, 2026-08-04)

Spec-first, no code written yet. All line refs are against `origin/main` @ `7e1ed17` (PR #1169).
⚠ `~/bim-ootb`'s working tree was 13 commits STALE when this spec was researched (`8592b33`, missing
#1157–#1169 incl. all the zone work). Read `git show origin/main:<file>`, not the checkout.

## Product mission (user ruling, 2026-08-04)
Be the best on-the-fly combined 4D/5D generator — remove MPP/P6 dependence for ~95% of the long tail
of users, using common-sense construction sequencing + real CPM. The Gantt drawer in Time Machine
becomes **the one editable 4D surface**; the two existing 4D editors are deprecated into it.

## K0 — KEYSTONE FINDING: the drawer's bars are not tasks (fix this first, everything depends on it)
The TM drawer does **not** read the schedule model. `drawGanttMini` (`time_machine.js:4393`) builds
`_ganttTasks` by grouping raw `_ops` (kernel_ops rows) into `groups[storey + '|' + phase]`
(`:4400-4413`) — a rollup of **element timestamps**. The resulting bar object is
`{storey, phase, startTs, endTs, count, cap}`. **There is no `task_id` on it.** So today a bar cannot
be moved, because `moveTask(db, taskId, newStart)` has nothing to be handed.

Meanwhile `materializeZones` (`schedule_author.js`) already writes exactly this decomposition into the
real model: one row per zone, `task_id = 'TASK_' + _slug(phase) + '_' + _slug(storey)`, `name =
phase + ' — ' + storey`, plus `task_elements` (tid→guids) and `task_sequences` (the real DAG edges).

**The drawer's `storey|phase` grouping and the zone-task decomposition are the SAME decomposition,
arrived at independently by two code paths.** They were never connected.

→ **The overhaul is therefore mostly a re-sourcing job, not new engine work.** Stop deriving bars from
`_ops`; read them from `tasks` + `task_sequences` for the active schedule. Once a bar carries its real
`task_id`, every edit verb below already exists and is already witnessed.

### Verbs that already exist — do NOT rebuild any of these (`schedule_author.js`, all exported)
| Verb | Does | Gap |
|---|---|---|
| `moveTask(db, taskId, newStart)` | moves a leaf task, preserves duration | **no cascade, no constraint check** — its own header says "CPM invalidation is the caller's concern" |
| `addDependency` | creates a `task_sequences` edge | — |
| `wouldCycle` | cycle guard | already there, drag-to-link gets it free |
| `removeDependency` / `updateDependency` | unlink / retype+lag | — |
| `computeCpm(db, schedId, opts)` | full FS/SS/FF/SF + lag, float, criticality, `fixedDates` opt | — |
| `listDependencies` | edges for a task | — |

The single real engine gap is **cascade/clamp on move** (C1–C3 below). Everything else is UI.

### K0 SHIPPED 2026-08-04 — `witness_gantt_bar_identity.js`, 7 buildings, 42/42, 268,093 elements
Bars are now keyed on the real `task_id`, joined **by GUID through `task_elements`** — not by matching
storey/phase strings, because `deriveZones` keys a zone on `collapsePhase(e.storey)` while the drawer
reads the raw `p.storey` off the op params, so the two names legitimately differ.
- **100% of elements resolve to a real task on every fixture** (Terminal 48,428 · Hospital 63,415 ·
  Duplex 1,143 · Clinic 16,912 · JKR 8,985 · LTU_AHouse 122,330 · HHS 6,880).
- **Do NOT repeat the earlier claim that name-matching is universally unsafe** — measured, on 6 of 7
  fixtures the old `storey|phase` key was exactly 1:1 with real tasks, so a name match would have
  survived there.
- **But HOSPITAL was drawing 60 bars for 35 real tasks** — 19 zone tasks each split across several
  rows, because `collapsePhase()` merges storey aliases the raw `p.storey` key does not. A real,
  measured defect on the exact building the user reported as "a mess"; fixed by the GUID join.
- Also fixed in passing: the rollup was recomputed from scratch on EVERY `drawGanttMini()` call — once
  per playback frame, walking all 63,415 ops on Hospital. Now cached behind `_ganttDirty`, invalidated
  on `_ops` reload and building change; the draw path is pure drawing.
- Bars whose ops carry no task (no authored schedule) keep the old storey|phase identity and stay
  non-editable, reported as a coverage ratio by `§GANTT_BAR_IDENTITY` rather than hidden.

## K1 — ROW ORDERING: we follow no P6 convention at all (user report 2026-08-04, verified)
User: *"are you using any 4D convention used by P6 on gantt phase/task ordering? Last session was a
mess putting substructure which has above ground appearing first."* Answer: **no convention is being
followed.** Three verified facts:
- **Rows sort by start time only** — `_ganttTasks.sort(function(a,b){return a.startTs - b.startTs;})`
  (`time_machine.js`). Not a P6 convention: whichever zone happens to compute earliest floats to the
  top, so a phase's floors appear interleaved and arbitrary.
- **There is no WBS to order by.** `materializeZones` parents EVERY zone flat to `TASK_ROOT`
  (`stmtTk.run([tid, schedId, rootId, …])`). P6 orders rows by WBS path, then early start. We have a
  single-level list, not a path.
- **The correct ordering keys are already computed and then discarded.** `deriveZones` builds each
  zone with `rank` (real bottom-up floor rank — `deriveBandRanks`, median `base_z` per collapsed
  storey) and `seq` (the zone's earliest trade sequence). Neither is persisted: the `tasks` schema
  (`_ensureWideTasks`) has NO ordering column.

**Fix (no invented data):** persist `seq` + `rank`; emit real phase summary rows as WBS parents
(Project → Phase → Zone); order rows by (phase sequence from `SEQUENCE_RULES`, then floor rank).
Phase sequence is already correct as of PR #1165; floor rank is real Z geometry.

⚠ **Separate, NOT yet verified:** whether Substructure is also mis-CLASSIFIED onto above-ground
elements. That is `matchRule` phase assignment, a different defect from row order — check before
assuming one fix addresses both. Note a concurrent session landed #1170 ("substructure-first tiebreak
in the GANTT_OPS_FIRST20 debug display") — that is the DEBUG log line only, not the Gantt row order.

## §ZONE_EDGE_LEAD — the zone graph contradicted its own dates (FOUND + FIXED 2026-08-04)
**Not looked for — surfaced by `witness_gantt_edit_constraints.js` G-CON-1 on its first run.** The
generated zone schedule violated **53 of its own 105 `task_sequences` edges on Terminal**, before any
user edit. Root cause, `schedule_gate.js deriveZones`:
```js
lagMs: Math.max(0, succ.start - pred.end)     // ← the max(0,…) clamped away every real overlap
```
Zones genuinely run in parallel (crews work floor N+1 while floor N finishes), so when
`succ.start < pred.end` the true relationship is a **negative lag** — a lead, P6/MSP's `FS-5d`.
Clamping it to 0 persisted an `FS+0` edge asserting "successor starts at or after predecessor
finishes", which the zone's OWN dates then contradicted.

**Why it stayed invisible:** `computeCpm(fixedDates:true)` trusts the real dates and only derives
float, so it never checked FS feasibility. It becomes load-bearing the moment those edges are used as
drag CONSTRAINTS — a clamp built on `FS+0` refuses legal moves, a cascade pushes bars that never
needed to move. **This is the single most important finding of the session: the "iron clad" core was
not iron clad, and no existing witness could have caught it because none tested the edges AS
constraints.**

Two parts to the fix: allow negative lag in `deriveZones`, AND derive the persisted lag from the same
ROUNDED day numbers `materializeZones` writes the dates from (rounding dates and lags independently
let them disagree by a day). Result: **53 → 0 self-violated edges, and 0 on all 7 fixtures.**
No regression — `witness_zone_cpm` (11), `_duplex` (9), `witness_tm_duration_sync` (8),
`witness_support_invariant_all_buildings` (18) all still green, 46/46.

## E — Interactions to build (MS Project convention; do not invent a new idiom)
- **E1 drag bar body horizontally** → move, duration preserved → `moveTask`.
- **E2 drag a bar's left/right edge** → resize; the opposite end stays pinned; duration changes.
  Needs a new leaf verb `resizeTask(db, taskId, newStart, newFinish)` — `moveTask` deliberately
  preserves duration, so it is the wrong verb to overload.
- **E3 drag from bar A onto bar B** → create an FS edge A→B → `addDependency` (guarded by `wouldCycle`).
- **E4 click an existing dependency arrow** → remove / change type (FS/SS/FF/SF) + lag.
- Hit-testing already exists: `findBarAtClick` (`time_machine.js:4590`). Extend it to return an edge
  zone (within ~4px of a bar end) so E1 and E2 can be told apart from one gesture.
- **E5 sliding day ruler** (user addition 2026-08-04) — a time-axis header along the drawer's top
  border showing real dates/day numbers, sliding/scrolling with the bars. Today the drawer has NO time
  axis at all: the only temporal reference is the orange cursor hairline (`:4550`), so a bar's absolute
  dates are only discoverable by hovering it. A ruler is also the precondition for E1/E2 being usable —
  you cannot drag to a date you cannot see.
- **E6 resizable drawer** (user addition 2026-08-04) — draggable drawer borders to show more rows.
  Height is currently implicit: `cH = numTasks * rowH + 4` inside a fixed-height scroll box
  (`:4479`), so a 22-storey building renders ~130 bars into a viewport showing a handful.
  Reuse the existing `makeDraggable` pattern (`:2953`), don't invent a second drag idiom.
- **E7 double-click → property panel** (user addition 2026-08-04, think-ahead) — double-clicking a bar
  opens a small property panel to TYPE exact start/finish/duration and edit its dependency list.
  Rationale: drag is for speed, keyin is for accuracy — a 1px drag on a 400-day project is ~2 days, so
  drag alone can never be the precise path. Same verbs underneath (`moveTask`/`resizeTask`/
  `addDependency`), same C1–C3 constraint checks — the panel is a second input surface onto the
  identical model, NOT a third editor. This is what makes deprecating the two existing editors honest:
  their real remaining value is precise keyed entry, and E7 absorbs it.

## C — Constraint semantics (THE rule this feature lives or dies on)
Prime Rule consequence: **a drag must never silently hide a real defect.** A purely cosmetic drag lets
a user "fix" a schedule violation by dragging it out of sight instead of fixing the cause. So:
- **C1 cascade** — moving a bar later drags its real `task_sequences` successors with it (FS:
  `succ.start >= pred.finish + lag`). This is MSP auto-schedule behaviour, not an invention.
- **C2 clamp** — moving a bar earlier than a predecessor allows is **refused**, clamped to the earliest
  legal date. The bar visibly snaps back. No silent acceptance.
- **C3 log every one** — `§GANTT_EDIT_MOVE`, `§GANTT_EDIT_CLAMP reason=pred:<id>`,
  `§GANTT_EDIT_LINK`, `§GANTT_EDIT_CYCLE_BLOCKED`. Whitebox-first: these `§` lines are the proof
  surface, not screenshots (FUNDAMENTAL LAW, CLAUDE.md).

## W — Write-path coherence (the seam that will bite)
A zone bar's window was **rolled up from real per-element times**. Move the bar and its member
elements must be redistributed inside the new window, or the drawer says one thing and the movie plays
another — the exact class of divergence PR #1162 just closed on the CPM side.
- **W1** — on an accepted edit, re-time only that task's `task_elements` guids across the new window,
  reusing the existing linear bottom-up-by-`cz` distribution (§PLAYBACK-STAGGER, `time_machine.js:3909`).
- **W2 (also the perf answer)** — an edit must be **incremental**: touch the edited task + its cascaded
  successors only, never re-run the whole 63,415-element pipeline. A drag touches ~10³ elements, not 10⁴.
  This is what makes interactive editing viable on Hospital at all.

## MOB — Mobile / ERP readiness: a Gantt task round-trips to the field and folds back done
**User ruling 2026-08-04. Not started — scoped here, deliberately AFTER the foundation band.**
A Gantt zone bar should be pushable to ERP exactly the way a Find selection already is, worked on a
phone, and folded back as a completed task with evidence attached.

**The anchor already exists — do NOT build a second push path.** `navigate_find.js _pushToErp()`
(~`:1921`) takes the current selection set, prices it (`_selectionPriced`), and calls
`window.ProjFold.foldProjectOrder(db, building, priced.rows, opts)`, which creates the real iDempiere
tree: `C_Project` → `C_ProjectPhase` → **`C_ProjectTask`** (per resource) → `C_ProjectLine` (per IFC
type). It already logs `§PROJ_PUSH … tasks=+N`, already routes through BlueFuture when a speculative
branch is engaged, and already reads back EVM via `ProjControl.projectControl`. The Gantt push is the
SAME verb with a different scope selector — a zone task's `task_elements` guid set instead of a Find
selection set.

**The round trip, in order:**
1. **PUSH** — a Gantt bar (K0 gave it a real `task_id`; `task_elements` gives it a real guid set) is
   pushed via the existing `foldProjectOrder`, yielding a real `C_ProjectTask_ID`. That ID is the
   join key for everything below — persist it against the zone task, do not re-derive it by name.
2. **MOBILE VIEW** — opening that Project Task ID on a phone highlights **the item set** (its
   `task_elements` guids) in the 3D scene and shows the **task checklist** (its `C_ProjectLine` rows —
   per IFC type, with real quantities, already created by the fold).
3. **SHARE BACK** — a photo finish or progress report attaches to the task, shared via the existing
   `viewer/share.js` path, carrying a link back to the item set **keyed by `C_ProjectTask_ID`**, not by
   a re-derived name or a screenshot caption.
4. **FOLD BACK DONE** — the returned evidence marks the `C_ProjectTask` complete in the ERP twin.
   **User point 2026-08-04: Time Machine ALREADY has the shape for budget-vs-actual — this step needs
   no new UI.** `§TM-VARIANCE` (`time_machine.js`) already loads the folded twin (`_loadTwin()`), reads
   the `C_Project` `PlannedAmt` ↔ `CommittedAmt` pair straight off the ledger, renders the per-phase
   variance canvas + list (`tm-var-canvas`, `tm-var-list`, `_VAR_ORDER`), and computes EVM
   (`§EVM_FOLD`: EV/AC/CPI/CV/BAC/EAC/VAC) plus a projected slip (`§SCHED_PROJECT`). Its own doctrine
   is already "variance = the PlannedAmt↔CommittedAmt pair, READ the twin, don't recompute" — exactly
   the contract a folded-back done task satisfies. So the round trip lands in an existing, witnessed
   surface: the field marks a task done → `CommittedAmt` moves → the variance drawer and EVM show real
   ACTUAL progress instead of estimate-only. The work is the write path and the task↔bar join, not a
   new dashboard.

**Boundary (carried, non-negotiable):** the completion signal must come from the returned real record —
never inferred from "the bar's end date has passed". A task whose date elapsed is NOT a task that got
done, and rendering it as done would be exactly the invented value this file's Boundary section forbids.

**Open questions, do not guess:** where the photo/report BLOB lives (ERP twin vs OCI vs IDB), and
whether mobile is the existing viewer at a small breakpoint or a distinct entry point. Both need a
user ruling before implementation.

## CAL — Working-day toggles (user ruling 2026-08-04: simple toggles in the drawer, later)
Reopens the working-calendar item that was closed as "don't silently guess a default" — **the toggle
dissolves that blocker**: we ship a stated default and the user sets their own. A real, nameable,
user-controlled business assumption, not an invented value.
- `_addDays` (`schedule_author.js:185`) is `days * 86400000` — fully calendar-blind, but a **single
  choke point** with 9 call sites (`:360, 370, 528, 538, 539, 681, 689, 963, 1065, 1066`).
- Durations are ALREADY working-day counts (`28800s = 8h` workday), so enabling a 5/6-day week
  re-derives **no rate** — it only maps the same real day-count onto the correct calendar dates.
- **Two separate deliverables, do not conflate**: (a) shading non-working columns in the drawer is
  cosmetic and cheap; (b) durations actually skipping them moves every date. Shipping (a) without (b)
  makes bars visibly run through shaded weekends — honest, but reads as a bug.
- **Holidays ≠ working week.** A weekday toggle is a safe default; a holiday list is locale data we do
  not have. Degrade to "no holidays" and say so — never invent one.
- Payoff: `erp/tests/real_xer_witness.js` already measured this gap against a real P6 file — 49/52
  start dates up to 59 days early, all one-directional. This is the biggest single gap between our
  dates and a P6 user's dates.

## VIS — Drawer facelift (colour + legend) — AWAITING USER OK, see Open Decisions
Real defects found, not taste:
- `PHASE_COLORS` (`time_machine.js:4102-4109`): `Substructure #7a8a8e` and `Superstructure #5b7fa5`
  are both desaturated blue-greys — the least distinguishable pair sits on the two adjacent structural
  phases.
- The text fallback collides on the SAME pair: label is `phase.substring(0,3)` (`:4543`) → `"Sub"` vs
  `"Sup"`, at 9px, and only drawn when `w > 40`.
- `Architecture #c07a4a` (orange-brown) competes with two reserved STATUS colours: `#ff8c00` = active
  bar outline + cursor hairline (`:4531`, `:4550`), `#ffeb3b` = captured-IFC-4D frame (`:4524`).
- Fills are drawn at `globalAlpha 0.8` (`:4516`), flattening what contrast remains.
- Palette encodes no trade family: the two MEP phases (`#8bc34a` / `#ab47bc`) look unrelated; MEP
  Rough-in and Finishes (`#26a69a`) look related.

Proposed: 3 trade families by hue, dark→light within each family following build order, orange/yellow
reserved for status only — Substructure `#37516b`, Superstructure `#5b9bd5`, MEP Rough-in `#2e7d52`,
MEP Final `#66bb6a`, Architecture `#6a4c93`, Finishes `#b07fd4`; fills at alpha 1.0; and an explicit
short-code map replacing `substring(0,3)` (`SUB / SUPER / MEP-R / MEP-F / ARCH / FIN`).

**Legend removal — confirmed safe.** The hover tooltip (`time_machine.js:2923-2942`) already reports
`storey — phase (N el, Day X–Y, generated | IFC 4D)`. The legend (`:2618` markup, `:4457-4472` fill)
is strictly redundant; deleting it returns a row of vertical space to the bars.

## DEP — Deprecation targets
- `viewer/schedule_author_ui.js` (507 lines, the ✎ side panel, `tm-author` button `:2585`)
- `viewer/schedule_editor_ui.js` (696) + `viewer/schedule_editor.html` (the ↗ Editor new tab, `:2587`)
- `boq_charts.html` — a FOURTH disconnected scheduler with its own stale MEP-before-Architecture
  `PHASE_ORDER` arrays. Redirect to real `tasks` via `AnalysisSidecar.compute4D()`'s existing-but-unused
  `capturedFn` hook (passed `null` at the `get4D()` call site, ~`:1218`) rather than patching it again.

## Witnesses (name the issue each proves — no test that cannot fail)
- `witness_gantt_bar_identity.js` — proves K0: every drawn bar resolves to a real `tasks.task_id`, and
  the set of bars equals the set of zone tasks. Fails today by construction.
- `witness_gantt_edit_constraints.js` — proves C1/C2: a forced illegal move is clamped, never accepted;
  a legal move cascades exactly the real `task_sequences` successors and no others.
- `witness_gantt_edit_coherence.js` — proves W1: after an edit, every `task_elements` guid's op
  timestamp lies inside its task's new window. This is the drawer-vs-movie divergence detector.
- `witness_working_calendar.js` — proves CAL(b): with a 5-day week, zero task start/finish dates land
  on a non-working day, and total working-day count is unchanged from the 24/7 run.
- Existing, re-run as regression: `witness_zone_cpm.js`, `witness_zone_cpm_duplex.js`,
  `witness_support_invariant_all_buildings.js`, `witness_tm_duration_sync.js`.

## Order of work
**User ruling 2026-08-04: "P6/MPP extras later — now is getting the core foundation upgraded to
correct 4D iron clad."** So CAL and the `boq_charts.html` redirect are explicitly OUT of the current
band; they are P6-alignment work, not foundation. The foundation band is K0→E→C→W: a bar that IS a
real task, edits that obey the real DAG, and a movie that always agrees with the chart.

**FOUNDATION BAND — progress as of 2026-08-04, branch `feat/gantt-edit-foundation` (all pushed):**
1. ✅ **K0 bar identity** — `789ff51`. `witness_gantt_bar_identity.js`, 7 buildings, 42/42.
2. ✅ **VIS facelift** — `610b361`. `witness_gantt_palette.js` 7/7, RED-proved against the old palette.
3. ✅ **E5 day ruler + E6 resizable drawer** — `76538e9`. Tick spacing verified over 18 span×width combos.
4. ⏳ **E1 + C1/C2 + W1** — `3c9349e` ships the **ENGINE half**: `moveTaskCascade` (C1 cascade /
   C2 clamp), witnessed 7 buildings 14/14 with a RED control. **REMAINING: the UI half** — pointer
   drag handlers on `tm-gantt-canvas` calling it, and **W1** (re-time the moved task's
   `task_elements` guids inside the new window). `witness_gantt_edit_coherence.js` NOT yet written.
5. ✅ **E2 edge-pull resize** — `resizeTask` verb shipped in `3c9349e`, witnessed (G-CON-12/13).
   UI edge-grab hit-testing still to wire (shares the drag handler with E1).
6. ⬜ **E3/E4 CPM linking + unlinking.**
7. ⬜ **E7 double-click property panel** — the precise-keyin path; also what makes DEP honest.
8. ⬜ **K1 P6 WBS ordering** (see above) — rows still sort by `startTs`.

**LATER BAND (P6/MPP alignment — do not start unprompted):**
6. **CAL toggles** (a then b) — the working-calendar model.
7. **DEP** — delete the two editors only once the drawer covers their real use; `boq_charts.html`
   `capturedFn` redirect.

## Open decisions (blocking nothing yet, but answer before the matching step)
- ⛔ **VIS palette** — proposed above, awaiting user OK (aesthetic, so not shipped unilaterally).
- ⛔ **"Remove the trivial panel"** — ambiguous, and removal is hard to reverse. The Apply-to-4D hang
  fires inside `time_machine.js`'s captured-overlay path (`:3943-4084`), which runs whenever a captured
  schedule exists — so deleting a *panel* does not by itself remove the hanging code. Meaning either
  (a) drop the ✎ Author 4D side panel, or (b) revert PR #1154's two-phase collect-and-guard block?
- **Hang root cause, corrected**: the prior session concluded "`§WRITE_LOOP_TIMING` never fired, so the
  freeze is before the write loop." **That inference is wrong** — `_wlT0` is captured at `:4075` and
  logged at `:4083`, i.e. only AFTER the loop completes, so a freeze *inside* the loop produces exactly
  the same silence. It rules out nothing. Confirmed regression origin: `git log -S_allScheduled` returns
  exactly one commit, `d35366a` (**PR #1154**) — matching the user's report that the hang is new. Prime
  suspect on measurement grounds is the `_ogStructGrid`/`_ogWallGrid` cell-bucket pass (`:4038-4068`),
  which is superlinear in footprint area, not the 63k prepared-statement writes. NOT yet measured.
