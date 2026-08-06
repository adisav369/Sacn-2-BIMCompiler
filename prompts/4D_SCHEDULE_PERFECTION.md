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

### MOB — FULL SPEC FOR THE NEXT SESSION (user ruling 2026-08-04: "let MOB be well spec'd")
Read this whole subsection before writing any code. Nothing here is started.

**M0 — PREREQUISITE, do this first.** The drag path is not yet proven end-to-end (see §BROWSER_PROOF
below). MOB rides on the same bar→task identity. Close M0 before building on it.

**M1 — Persist the ERP link (no ruling needed, fully derivable — start here).**
- `foldProjectOrder` already returns `{projectId, orderId, created:{phases,tasks,lines,products}}`
  and creates real `C_ProjectTask` rows. What does NOT exist is a stored link from OUR zone task to
  the ERP one. Add it: a small `task_erp_link(task_id TEXT PRIMARY KEY, c_projecttask_id INTEGER,
  c_project_id INTEGER, pushed_at TEXT)` table alongside `tasks`.
- **Join by stored ID forever after — never re-derive by name.** Zone task names are
  `phase + ' — ' + storey`; ERP `C_ProjectTask.Name` is the RESOURCE. They are different strings by
  construction, and matching them would be the same class of defect K0 fixed.
- Witness `witness_mob_erp_link.js`: after a push, every pushed zone task has exactly one link row,
  the `c_projecttask_id` exists in `C_ProjectTask`, and re-pushing is idempotent (no duplicate rows,
  no second `C_Project`). RED control: assert a name-based match would mis-associate — measure it.

**M2 — Push a bar from the drawer.** Reuse `navigate_find.js _pushToErp()`'s pipeline verbatim; the
only difference is the scope selector — the bar's `task_elements` guid set instead of `_lastSelSet`.
⚠ `_pushToErp` needs PRICED rows (`_selectionPriced`), and it already fails honestly when a selection
has nothing costable. Keep that behaviour; do not paper over it. Entry point: the E7 property panel
(it already lists the task's real identity), NOT a new toolbar button.

**M3 — Mobile view.** Opening a `C_ProjectTask_ID` highlights that task's `task_elements` guids in 3D
and lists its `C_ProjectLine` rows as the checklist (per IFC type, real quantities — the fold already
created them; do not recompute).

**M4 — Share back.** `viewer/share.js`, payload keyed by `c_projecttask_id`.

**M5 — Fold back done.** Write the completion to the twin so `CommittedAmt` moves. **No new UI** —
`§TM-VARIANCE` (`_loadTwin`, `tm-var-canvas`, `tm-var-list`, `_VAR_ORDER`) and `§EVM_FOLD` already
render exactly this. ⚠ While you are there: `_VAR_ORDER` is a KNOWN-STALE phase order (MEP Rough-in
before Architecture) — fix it to derive from `SEQUENCE_RULES` like `_ROW_PHASE_ORDER` now does.

**BOUNDARY (non-negotiable):** completion comes from the returned real record. NEVER infer "done"
from an elapsed end date — an elapsed task is not a completed task, and rendering it as done is
precisely the invented value this file's Boundary section forbids.

**Open questions — ASK, do not guess:** (a) where the photo/report BLOB lives (ERP twin vs OCI vs
IDB); (b) whether mobile is the existing viewer at a small breakpoint or a distinct entry point.
Neither blocks M1/M2 — do those first, then ask.

## §BROWSER_PROOF — what a real browser proved that 184 headless checks could not (2026-08-04)
Driven two ways: a dependency-free CDP client (`scratchpad/cdp.js` + `drag_test.js` — this box has NO
npm registry access, `npm ping` times out, and Node 18 has no global `WebSocket`, so the client
implements the handshake over `net`), then the Claude Chrome extension once the user signed in.

**Three real bugs found, all invisible to every witness** (each witness authors its own fixture in
node and never touches a DOM or the toolbar):
1. Removing the ✎ icon orphaned the ONLY caller of `ScheduleAuthorUI.toggle()` — no user could author
   at all, leaving every bar without a `task_id` and the whole editable drawer inert (`9902f6d`).
2. `buildTaskIndex()` cached the NEGATIVE result, so authoring after opening the drawer never took
   effect (`9799f24`) — and that fix alone was still insufficient, because `buildGanttTasks()` is
   gated on `_ganttDirty` which authoring never set (`6a5073f`). MEASURED: banner `display:block`
   → `display:none` for the same sequence.
3. The drag refusal was a bare `return` — no log, no user feedback, and the test could not tell
   "handler never fired" from "handler correctly refused". Silence is not a refusal (`9799f24`).

**Also proven live, not merely in source:** ruler renders 18px and computes `position:sticky`; canvas
lays out 327×214 with real drawn pixels; legend gone; ✎ gone; and `window.SEQUENCE_RULES` IS loaded
when `_ROW_PHASE_ORDER` is built — so the DERIVED phase order is the branch that actually runs, never
the hardcoded fallback. That last one was a real correctness question no headless witness can answer.

**CORRECTION 2026-08-04 (user reported it; my own data already said it and I misread it):
THE DRAWER RENDERS NEAR-EMPTY IN A LIVE VIEWER.** The CDP probe returned `nonBlankPixels=534`
over a 327x40 sampled strip — ~4% coverage — and I recorded that as "content drawn". It is not.
So the drag test's silence has a far simpler cause than handler wiring: **there was nothing under
the pointer to hit.** This SUPERSEDES the "guessed x-coordinate" explanation below, which was real
but secondary. Bars resolve real task_ids headlessly on 7 fixtures, yet are not drawn at a visible
size in the browser.

`window.__tmGanttBars` (commit `41a4bd9`) now dumps every bar's ACTUAL drawn rect. **NEXT SESSION:
query it FIRST, before touching any drag code.** It separates the three candidates a pixel count
cannot: (a) `bars:0` = `_ganttTasks` empty; (b) widths ~0 = degenerate `_projectStart/_projectEnd`
range; (c) xs outside the canvas box = drawn off-screen. Fix what it names, THEN re-run the drag.

⚠ Environment caveat: the user's desktop Chrome hit a WebGL init error — a KNOWN machine-level GPU
launcher issue on this box (`project_machine_chrome_firefox_gpu_launchers.md`), not caused by this
branch — while headless Chrome falls back to SwiftShader. The two are not comparable on GPU-dependent
behaviour; do not treat a headless pass as proof for the desktop.

**The transport row's two buttons — ↺ Undo edit ✅ SHIPPED, ⚑ Set Baseline DECIDED-BUT-DEFERRED
(2026-08-05).** `Copy Touched` / `Copy New` (`tm-touched`/`tm-new`) just called `copyGuids(false|true)`
to put GUID lists on the clipboard — a developer debug affordance, nothing else referenced them, user
had "lost touch" with them.

- **↺ Undo edit** — bim-ootb PR #1188 (auto-merge armed), replaces the `tm-touched`/Copy-Touched slot.
  Single-level (not a stack), scoped to `commitGanttDrag` (E1/E2 drag/resize) only — not link/unlink,
  not the property panel, matching exactly the named need ("one drag can cascade N successors with no
  way back"). Snapshots both tables an edit touches (`tasks` + the affected `kernel_ops` rows) before
  the engine verb runs, restores both exactly on click; cleared on every fresh `activate()` so a stale
  cross-building snapshot can never apply. `witness_gantt_edit_undo.js` (new, slices the real shipped
  functions by balanced braces, never reimplements the restore logic): Duplex 10-task cascade/898 ops
  9/9, Terminal 20-task cascade/3,519 ops 9/9 — RED control, exact restore, second-click no-op, and
  out-of-cascade rows proven untouched, all verified against real authored schedules.
  **Perf bug caught pre-ship, not shipped broken:** first draft's snapshot capture was
  O(cascadeGuids×totalOps) — measured 92s wall-clock on Terminal. Hashed to O(1) (same shape as the
  existing `retimeTaskElements`'s own lookup) — 92s→39s, remainder is that function's own pre-existing
  write-loop cost, untouched here.
- **⚑ Set Baseline — ✅ SHIPPED 2026-08-05, bim-ootb PR #1190 (auto-merge armed).** Definition
  user-confirmed: P6 baseline = a frozen snapshot of `tasks.schedule_start/finish` taken at a
  deliberate moment, compared against the live (possibly since-edited) schedule — **schedule**
  variance, correctly NOT the same axis as `§TM-VARIANCE`'s existing **cost** variance (`C_Project`
  `PlannedAmt`↔`CommittedAmt`, already exists, stays untouched, sits "higher up" per the user —
  reconcile the two variance views later, not now).
  `ScheduleAuthor.setBaseline(db, scheduleId)` snapshots every task row (incl. summaries, for
  project-level rollup) into a new `task_baseline` table — single baseline, re-running OVERWRITES
  (MVP scope, not P6's multi-baseline numbering). `getBaselineVariance(db, scheduleId)` returns real
  `varianceDays` per task (direct date subtraction, nothing invented) plus `TASK_ROOT`'s own row as
  `projectVarianceDays`; honest `{ok:false,reason:'no_baseline'}` refusal when nothing's been set yet.
  Shipped as a **manual button today** — MOB's auto-trigger-at-ERP-push (M2, still not started) is
  the "kickoff confirmation" analog and the right trigger long-term, but since M2 doesn't exist yet a
  free-floating button is the only way to make this usable now. **Not made obsolete once M2 lands** —
  the same `setBaseline` verb gets called there too, and this button becomes "re-baseline for an
  approved change order," the secondary case already named in the spec. Next session picking up MOB:
  wire `setBaseline` into M2's push path instead of re-deciding the definition.
  `witness_gantt_baseline.js` (new): real authored schedule + real drag on Terminal (20-task cascade)
  11/11, Duplex (10-task cascade) 11/11 — RED control (no-baseline refusal), exact scope match (only
  dragged/cascaded tasks show variance), re-baseline proven as a true overwrite (no row accumulation).
  **Display is a deliberate follow-on, not built here** — this PR ships capture+read only, no new
  variance-visualization UI; per KISS, that's a separate task when actually needed, matching MOB M5's
  own "no new UI, reuse `§TM-VARIANCE`" pattern.
  Also bumped `sw.js` CACHE_VERSION v947→v948 in the SAME PR (touches precached files again) —
  applying the #1189 lesson proactively this time, not as a trailing fix.
  `tm-touched`/`tm-new`'s old handler `copyGuids()` is now UI-unreachable from either transport
  button but was deliberately left in the file, not deleted — still a working, self-contained
  console-callable debug utility, and deleting it was out of scope for a UI slot swap.

### ⚠ CACHE-VERSION DISCIPLINE — landmine hit and fixed live this session (2026-08-05)
Three PRs in a row this session (#1186/#1187/#1188) modified `PRECACHE_ASSETS` files
(`time_machine.js`, `schedule_author.js`, `rates.js`, `rates/sequence_rules.json`) **without**
bumping `sw.js`'s `CACHE_VERSION` — a direct violation of this project's own standing rule
(`feedback_bimootb_sw_cache_bump_on_viewer_change.md`). Caught only because the user pasted a live
browser console log from a post-#1188 Hospital session and it was checked against `sw.js` before
trusting it as evidence — the log itself gave no way to tell which code was actually running. Fixed
same-session via bim-ootb PR #1189 (v946→v947), and #1190 (v947→v948) applied the lesson proactively
in the same PR rather than as a trailing fix. **Standing reminder for every future PR touching any
`PRECACHE_ASSETS` file in this repo: bump `CACHE_VERSION` in the SAME commit, not after.**

**STILL OPEN — the drag link not closed:** no `§GANTT_DRAG_COMMIT` has ever been observed. Events
reach the canvas (a probe listener fires), the handler is wired (`cv._dragWired === true`), and bars
are now editable (banner gone) — but the last attempt aimed a synthetic pointer at row 0,
`x = marginL + 40px`, which is a GUESS at the bar's extent, not measured geometry. A short first bar
simply would not be under that point, producing exactly the observed silence.
**NEXT SESSION, do this first:** expose the drawn bar rects (a `window.__tm*` debug hook, the
convention this file already uses for `__tmScheduleDebug`), aim at a MEASURED midpoint, and only then
assert `§GANTT_DRAG_COMMIT` / `§GANTT_EDIT_CLAMP` / `§GANTT_RETIME` numbers. Until that passes, treat
the drag gesture as UNVERIFIED end-to-end — the engine beneath it is witnessed 184/184, the gesture
reaching it is not.

**PR #1173 opened (bim-ootb) for the 9 commits still stranded past #1171's squash-merge point** —
`main`/the sandbox had none of the browser-proof fixes (`9902f6d`/`9799f24`/`6a5073f`), the
`__tmGanttBars` debug hook (`41a4bd9`), or the BOQ4D work. Not merged yet.

**Headless follow-up 2026-08-04 (Chrome busy in a concurrent session — did this instead, no browser
needed): the "axis vs. per-bar percentile trim" theory is REFUTED by real numbers.** Read
`buildGanttTasks()` and reasoned that since `_projectStart`/`_projectEnd` (the axis, `:110-111`) are
raw min/max while each bar's own span is 2nd–98th percentile trimmed (`§GANTT_MINI_TRIM`, `:4589-4602`),
a crew-starved long tail could stretch the axis and shrink every bar to a sliver. Tested it directly:
`scratchpad/witness_gantt_axis_trim.js` (bim-compiler) authors a REAL schedule via the shipped
`ScheduleAuthor.materializeZones()` (same verb `witness_boq_charts_real_schedule.js` uses) on all 6
building fixtures, then computes each real task-group's trimmed span as a fraction of the real axis:
**widest bar 21–71% of the axis, average bar 4.7–13.3%, across Duplex/Clinic/JKR/HHS/Hospital/Terminal.**
Not degenerate — a bar averaging ~10% of a ~267px-wide canvas is ~27px, plainly visible. The theory
does not hold.

**A different, evidence-consistent account for the "534/13080 ≈ 4%" reading emerged from the same
numbers, not yet proven:** that probe sampled a 327×40px STRIP — only the top 2–3 rows (`rowH=14px`),
not the whole canvas. K1 row order puts Substructure first, and per-row bar width in a Gantt chart is
inherently sparse (~10% avg fill × 12px bar height leaves most of a row's pixels as background by
design). Back-of-envelope using the witness's real avgBarFrac: 2–3 rows × ~320px² of real paint ≈
640–960px² over a 13,080px² strip = **4.9–7.3%** — close enough to the reported 4% that "near-empty" may
be reading a correctly-sparse Gantt chart as broken, the same kind of probe-misread the commit already
owned once (differently: last time 4% was over-read as "content drawn"; this time it may be under-read
as "broken"). **Not proven either way** — exact probe row/threshold unrecoverable (never committed to a
script, was an ad-hoc CDP session). This is exactly why `__tmGanttBars` (exact rects, no pixel-counting
ambiguity) is still the right next step, now for a sharper reason: pixel-fill heuristics can't
distinguish "correctly sparse" from "actually broken" on a chart shaped like this one.

## §MEP_HUNG_FROM_ABOVE — a real, measured support-invariant gap (2026-08-04, user reference: `~/Downloads/Hospital_verynice.mp4`)

User: that video is the visual quality bar to surpass, and it shows "slight elusive creeping elements
appearing without support" — the original trigger for this whole session's scheduler refactoring.
`witness_support_invariant_all_buildings.js` already reports 0/0 floating on all 6 buildings, so the
existing invariant is not lying — it is testing the wrong thing for one class of element.

**Code review finding:** `schedule_gate.js`'s `geoGate`/`auditFloating` only ever check for a support
BELOW an element (`S.base_z < el.base_z - EPS`) — correct for structure resting on structure, but MEP
ductwork/pipe (`IfcDuctSegment`/`IfcPipeSegment`/`IfcFlowSegment`, all `seq:7`) is physically hung FROM
the slab ABOVE it. Nothing in the scheduler or the audit has ever tested that relationship — every prior
fix in this file's history (§4D_ROOF_LOAD_PATH, §4D_WALLS_BEFORE_ROOF, §4D_WALL_BORNE_STRUCTURE and its
5 rejected redesigns) dealt exclusively with support-from-below. This is a genuinely new class of gap,
not a re-litigation of settled work.

**Measured (`scratchpad/witness_mep_hung_from_above.js`, bim-compiler — diagnostic only, no production
code touched): 25 real violations across 6 buildings**, all `IfcPipeSegment`, all small relative to their
building's hung-MEP population (0.1–0.9%):
- Duplex 0/704 · Clinic 0/6925 · HHS 0/2467 · **Hospital 0/30411** · JKR **4/1819** (worst lag 6.2d) ·
  Terminal **21/4462** (worst lag 7.8d)

**Honesty check, not yet resolved:** Hospital — the building in the reference video — scores 0 on this
specific check. This gap is real and worth fixing (JKR/Terminal), but it does NOT by itself explain
what the video shows. Two live possibilities, neither confirmed: (a) the video predates one of the
Aug 1 fixes (§4D_ROOF_LOAD_PATH/§4D_WALLS_BEFORE_ROOF, both shipped ~04:00–16:00 on 2026-08-01; file
mtime is 2026-08-03 04:19, so it postdates those — this is likely NOT it, but not yet cross-checked
against which commit was actually live when the bake ran) or (b) a different, still-unidentified
relationship this session hasn't checked yet (candidates: `IfcRailing`/`IfcStair` between levels,
curtain-wall/cladding on incomplete structure, or the zero-lag "start == support finish exactly"
simultaneity case — an element CAN legitimately start the same instant its support finishes, which a
strict `<` audit correctly allows but a human eye sweeping past in the same frame might read as
simultaneous "popping in").

**Next session:** (1) fix the 25 measured MEP-above violations — likely a `wallGate`-shaped addition,
support = nearest real slab whose `base_z` sits within GAP above the element's `top_z`, same
`overlap()`/EPS/GAP already in use, no new invented constants; (2) do NOT treat that as closing this
item — Hospital's 0 score means the video's actual defect is still unidentified, chase it after (or
alongside) `__tmGanttBars` once Chrome is free.

### §GANTT_AXIS_OUTLIER — CONFIRMED LIVE on Hospital (2026-08-04, Chrome free'd up, `__tmGanttBars` used exactly as directed)

Sandbox pointed at the `feat/gantt-edit-foundation` branch tip (`41a4bd9`, has the hook — `main` does
not, see PR #1173), Hospital fixture symlinked in, `?tm=1` deep link, Gantt drawer opened, real
`window.__tmGanttBars` read back — this is the "aim at measured geometry, not a guess" step the branch's
own last commit named as next. Ground truth, not inferred:

- **Live project span is 1049 days.** My earlier headless witness (`witness_gantt_axis_trim.js`)
  computed only 416 days for Hospital via a direct `computeSchedule()` call — a real, now-explained
  discrepancy: the live path processes the FULL real element set through the shipped
  `§4D_BAND_MONOTONIC` gate, which console-logged **`gatedB=51712`** — 81% of Hospital's 63,416 elements
  were held by the same-trade-one-floor-down band gate, live, in this exact run.
- **Every legitimate bar (Substructure through MEP Final, all 7 levels) finishes by ~31% of the axis**
  (`__tmGanttBars` x/w read back and expressed as % of the 270px bar width — Superstructure levels sit at
  0.8–4%, the latest normal bar, `MEP Final|Level 2`, ends at 29.9%). That 31% is why the drawer LOOKS
  near-empty at the canvas's default width — it is not degenerate math, the real work is genuinely
  compressed into less than a third of what's drawn.
- **THE OTHER 69% OF THE AXIS IS ONE ELEMENT.** `__tmGanttBars` shows exactly one outlier bar —
  `phase=Architecture, storey=_UNKNOWN` — sitting alone at **x=100.0%**, i.e. it alone defines
  `_projectEnd` and stretches the whole axis from a real ~325-day build to 1049 days. This is the
  `_UNKNOWN`-storey bucket this file's own §4D_BAND_MONOTONIC header comment already named as a landmine
  ("~9457 no-storey elements reassigned to nearest storey by median Z... a band rule laid on a wrong
  grouping enforces a wrong order confidently") — one element evidently escaped or survived that
  reassignment with a schedule position nothing else in the building shares.
- **This is the real, now-identified mechanism behind "near-empty drawer" on Hospital specifically** —
  not a rendering bug, not a probe-misread, not the percentile-trim theory (refuted earlier). One
  `_UNKNOWN`-storey Architecture element, alone, 3x past the real project end.
- **Next step, concrete:** find that element's guid (`_UNKNOWN` storey ⇒ likely one of the ~9457 the
  band-gate header already flagged), check what real geometry/data drove its schedule position, and
  decide whether the fix is (a) better storey reassignment for that one element, or (b) an axis-display
  guard that doesn't let a single `_UNKNOWN`-storey straggler define `_projectEnd` for the whole chart
  (two different fixes — (a) fixes the schedule, (b) only fixes the chart; do (a) first, don't paper
  over real bad data with a display clamp).

### User-reported, live, unresolved: "2 trucks and some walls before the piling" at hour 0 (2026-08-04)

User watching Day-0/Hour-0 state reports walls (and site-equipment "trucks") appearing before piling —
a real sequencing complaint, wants "P6, heavier logic." **Checked one candidate mechanism, ruled it
out:** structural bbox degeneracy (the same class of defect `IfcStair` showed in `__tmScheduleDebug` —
`x0=x1=y0=y1=0` — which would make `geoGate`'s spatial grid blind to a support). Measured directly
against Hospital's real DB: **`IfcFooting`/`IfcColumn`/`IfcBeam`/`IfcMember`/`IfcPlate`/`IfcSlab` are
0% degenerate** — every structural class has a real, non-zero bounding box. Piling is not geometrically
invisible to the gate. **Root cause still open** — not yet checked: whether wall bboxes actually
XY-overlap their true supporting footing (grid-cell edge cases), or whether "trucks"/site-equipment is
even a class the scheduler assigns a support requirement to at all (same shape as §MEP_HUNG_FROM_ABOVE —
an unmodeled-support class, not a broken check). **Do not claim P6-grade CPM logic is a quick add** —
this file's own history already tried and rejected 5 designs in that direction
(`§4D_WALL_BORNE_STRUCTURE`, `§ELEMENT_CPM`, `§CPM_DUAL_ELEVATION`, all 2026-08-02, all "NOT FOR MERGE" —
each traded away the band-monotonic fix to get support-invariant to 0, or vice versa). Any new attempt
must be measured against BOTH invariants at once, not just the one it's chasing.

### §GANTT_AXIS_OUTLIER — FIXED, PR bim-ootb#1175 (2026-08-04)

**Worktree collision, resolved:** `/tmp/wt-sandbox` is a SHARED standing worktree (per its own memory
doc) — while building this fix there, HEAD silently moved out from under the edit session (another
process re-checked it out). Investigated before assuming repo damage: `origin/feat/gantt-edit-foundation`
was confirmed fully intact throughout (`41a4bd9`, all 5 browser-proof fixes present) — this was a LOCAL
worktree-reuse collision, not a lost-commit or bad-merge event. Recovered by saving the in-progress diff
as a patch, then rebuilding it fresh in a dedicated private worktree (`/tmp/wt-gantt-axis-fix`, since
pruned) rather than reusing the shared one for active edits — matches the Worktree Hygiene rule this
project already carries ("prefer a private worktree when a task might overlap with concurrent work").

**The fix (user-directed design, not invented ad hoc):**
- User: don't special-case the `'_UNKNOWN'` storey label — refer back to the pattern already proven
  correct in this file for exactly this problem. `buildGanttTasks()` already 2nd–98th percentile-trims
  each BAR's own span (`§GANTT_MINI_TRIM`); this applies the SAME trim, same threshold, to the GLOBAL
  population of `end_ts` that defines the chart's axis — root-cause-agnostic, catches any wild outlier,
  not just the one already found.
- New, SEPARATE vars `_ganttAxisStart`/`_ganttAxisEnd` — `_projectStart`/`_projectEnd` themselves stay
  untouched, since real playback (scrubbing, `renderAtTime`, "every element must eventually build") must
  never disagree with what actually gets built. This is a DISPLAY-scale decision, not a data change —
  the JSON/task data a user could also edit via P6/MPP round-trip remains the single source of truth;
  only how the chart SCALES itself against that same data changes.
- Every pixel↔time conversion the drawer does was audited and patched to use the qualified axis
  consistently: bar draw, ruler ticks/gridlines, click-to-seek, `findBarAtClick` hit-testing, `ganttHit`
  drag-hit-testing, and the `__tmGanttBars` hook itself (so a future live probe reads the same numbers
  the chart actually draws). Verified via grep — zero stray unqualified-axis references left in any
  Gantt-drawer-specific function.

**Verification status, stated honestly:** syntax-checked (`node --check`), and the DEFECT this fixes was
already conclusively proven live pre-edit (this file's own §GANTT_AXIS_OUTLIER CONFIRMED LIVE section
above). NOT re-verified live post-edit — hit the pre-documented WebGL/GPU init failure
(`project_machine_chrome_firefox_gpu_launchers.md`) on this box after ~100min of heavy tab reuse, a known
environment issue, unrelated to this change. **Next session, first thing:** fresh tab, fresh Hospital
load, re-query `__tmGanttBars`, confirm bars now span most of the axis and the ruler reads ~325d not
1049d — the PR (bim-ootb#1175, base `feat/gantt-edit-foundation`) says this explicitly in its own test
plan as an open checkbox, not a silent gap.

**Still separately open, not touched by this fix:** the "walls/trucks before piling at hour 0" report
and the Hospital-video defect (§MEP_HUNG_FROM_ABOVE scored 0 on Hospital) remain unidentified — this fix
closes the axis-scaling defect only, not those two.

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

**FOUNDATION BAND — COMPLETE 2026-08-04, branch `feat/gantt-edit-foundation`, all pushed.**
The TM Gantt drawer is now the editable 4D surface. Every item below has a witness that can fail.
1. ✅ **K0 bar identity** — `789ff51`. 7 buildings, 42/42, 268,093 elements, 100% resolve.
2. ✅ **VIS facelift** — `610b361`. 7/7, RED-proved against the old palette.
3. ✅ **E5 ruler + E6 resizable drawer** — `76538e9`. Tick spacing verified over 18 span×width combos.
4. ✅ **§ZONE_EDGE_LEAD + C1/C2 engine** — `3c9349e`. 53→0 self-violated edges; 7 buildings, 14/14.
5. ✅ **K1 P6 row ordering** — `73f2f1b`. 9/9, order DERIVED from `SEQUENCE_RULES`, never hardcoded.
6. ✅ **E1/E2 drag UI + W1 re-time** — `2d9a47d`. `witness_gantt_edit_coherence.js` 7/7.
7. ✅ **E3/E4 link + unlink, E7 property panel** — `ca7c44e`. Constraints witness now 18/18.

**Full suite at close: 93/93** — bar_identity 6 · palette 7 · row_order 9 · coherence 7 ·
constraints 18 · zone_cpm 11 · zone_cpm_duplex 9 · tm_duration_sync 8 · support_invariant_all 18.

**DEP progress (user ruling 2026-08-04):** the ✎ Author-4D side-panel button is REMOVED (`2d9a47d`) —
entry point only, `schedule_author_ui.js` still loads so nothing referencing it breaks. The ↗ Editor
tab STAYS for now, to be consolidated into the drawer in a later pass. `boq_charts.html` redirect
dispatched to an agent on the same branch.

**Honesty note carried forward — a witness claim that was WRONG and got corrected:**
`witness_gantt_edit_coherence.js`'s first RED control asserted that a naive (unclamped) affine remap
escapes the task window. **It does not and cannot for realistic input** — the affine map sends the old
window onto the new exactly, and the 60s minimum-duration bump only fires when a task's new span ÷ its
element count drops below 1ms, which needs more elements than a day has milliseconds. So the window
clamp inside `_retimeSpan` is belt-and-braces, NOT load-bearing, and no future session should claim
otherwise. The control now tests the real defect W1 prevents — task dates moving while elements do not
— and measures it: **skipping the re-time strands 4,993 of 5,160 elements outside their task window.**

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

---

## §GANTT_EDIT / BOQ4D — `boq_charts.html` redirected onto the real schedule (SPEC, 2026-08-04)

Spec written BEFORE any code (Spec-First, CLAUDE.md). Scope is exactly the DEP bullet above
("`boq_charts.html` is a FOURTH, fully disconnected scheduler"), brought forward on a direct user
ruling; the rest of the LATER BAND (CAL, deleting the two editors) stays untouched.

### B0 — What is actually wrong (verified in the file, not assumed)
`viewer/boq_charts.html` (the "4" button / HTML charts tab) never reads the schedule model. Three
independent hardcoded `PHASE_ORDER` arrays, all identical and all stale:
- `:423` inside `generateSchedule()` — the coarse phase×storey forward pass,
- `:560` inside `audit4DSchedule()` — the 8-check audit's own phase-inversion test,
- `:818` inside `buildScheduleFromOps()` — the kernel_ops rollup (NOT previously named in this file;
  found while specing this item, so the count is THREE stale copies in this file, not two).

All three read `['Substructure','Superstructure','MEP Rough-in','Architecture','MEP Final','Finishes']`
— MEP rough-in BEFORE the building envelope, the exact ordering PR #1165 corrected across 18 rate
sources. The real order, read from `SEQUENCE_RULES`' own sequence numbers (min sequence per phase,
the same derivation `proj_fold.js:140` and `time_machine.js` `_ROW_PHASE_ORDER` already use):
`Substructure(1) → Superstructure(2) → Architecture(5) → MEP Rough-in(7) → MEP Final(9) → Finishes(10)`.
Consequence of `:560` being stale: `§4D_AUDIT_PHASE_ORDER` was grading the schedule against the WRONG
order, so a correct schedule would be reported as inverted (and vice versa) — the audit was actively
misleading, not merely cosmetic.

### B1 — Direction (user-approved, not invented here)
Redirect the page onto `schedule_author.js`'s real `tasks` / `task_elements` / `task_sequences`
records via `AnalysisSidecar.compute4D()`'s existing-but-unused `capturedFn` hook (`analysis_sidecar.js
:132`, passed `null` at the `get4D()` call site `boq_charts.html:1218`). Do NOT patch `generateSchedule()`
into a second engine — it stays exactly as-is, as the no-authored-schedule FALLBACK only.

### B2 — Where the reader lives, and why it is a new file
The reader goes in a new DOM-free, node-requireable module `viewer/schedule_read_4d.js`
(`window.ScheduleRead4D`), NOT inline in the HTML. Two reasons, both load-bearing:
1. A witness cannot `require()` an HTML file. Putting the logic inline would force the witness to
   re-implement it — which is precisely the "copy the predicate instead of importing it" convention
   this file already named as the root cause of the same bug being fixed three times in one session.
2. Schedule detection must call `ScheduleAuthor.activeSchedule(db)` — the ONE existing verb — rather
   than growing a fourth copy of "which schedule is active". `boq_charts.html` therefore also gains a
   `<script src="schedule_author.js">` tag.

### B3 — The reader's contract (`ScheduleRead4D`)
- `phaseOrder(rules)` → phase names ordered by MIN `sequence` over all classes of that phase in
  `SEQUENCE_RULES`. Derived on every call, never cached at load — `initRateTemplate()` mutates
  `SEQUENCE_RULES` in place at runtime (`rates.js:446`), so a load-time snapshot would go stale.
  Returns `[]` when rules are unavailable; every call site falls back to its own behaviour rather than
  substituting an invented order.
- `readTasks(db, opts)` → `null` when there is no active schedule / no dated leaf tasks (the page then
  keeps its existing behaviour untouched), else an array of task rows in the page's existing shape.
  Every field traces to a real record:

| field | source | invented? |
|---|---|---|
| `taskId`, `name` | `tasks.task_id`, `tasks.name` | no |
| `startDate`/`finishDate` | `tasks.schedule_start`/`schedule_finish` | no |
| `startDay`/`finishDay`/`duration` | those dates minus the schedule's own earliest start | no (derived) |
| `guids` | `task_elements.guid` | no |
| `qty` | count of those guids, `uom='EA'` | no |
| `ifcClasses`, `storey` fallback | `elements_meta` joined by guid | no |
| `phase`, `storey` | `tasks.name` split on `' — '` (the separator `materializeZones:376` writes), validated against `phaseOrder()`; element-majority fallback | no |
| `discipline` | `SEQUENCE_RULES[cls].resource` → MEP/STR/ARC, the SAME mapping `buildScheduleFromOps` uses | no |
| `crew` | Σ `LABOR_RATES[res].crew_size` | no |
| `equipment` | `EQUIPMENT_ALLOCATION` → `EQUIPMENT_RATES[..].desc` | no |
| `predecessors` | `task_sequences` (id, type, lag_days) | no |
| `wbs` | `1.<phaseRank+1>.<row>` — the page's existing display convention | display only |
| `crews` | `1` — the real model records no crew split (same as `buildScheduleFromOps:890`) | no |

**There is no duration cap on this path.** `MAX_TASK_DAYS = 20` (`:426`) is an invented constant; it is
NOT carried over and NOT replaced by another invented number. Durations are the real persisted dates.
It survives untouched in `generateSchedule()`, which remains the honest "no schedule authored yet"
fallback — naming it here so it is a known, stated remainder rather than a silent one.

### B4 — Precedence, and why kernel_ops must NOT override the real tasks
Current order is `resolve4D(default4D, ops, buildScheduleFromOps)`: kernel_ops overrides the default.
That override re-groups ops on `storey|||phase` — the exact key K0 measured as wrong (Hospital drew
60 bars for 35 real tasks, because `collapsePhase()` merges storey aliases the raw `p.storey` does not).
So when a real authored schedule exists it WINS outright and the kernel_ops override is skipped, with a
`§` line saying so. With no authored schedule, nothing changes: baked default → kernel_ops override,
exactly as today.

The page's own "source" badges (`:1584`, `:1756`) treat only `kernel_ops` as a real source; `authored`
joins it. `compute4D` gains a 3-line, backwards-compatible tweak so `capturedFn` may return either a
bare array (existing contract, labelled `captured`) or `{source, tasks}` — without it the log would
call an authored zone schedule "captured", which is a different, real thing (native IfcWorkSchedule).

### B5 — Witness: `witness_boq_charts_real_schedule.js` (must be able to FAIL)
Fixtures carry `tasks`/`task_elements`/`task_sequences` tables but ZERO rows, so the witness first
materializes a real schedule with `ScheduleAuthor.materializeZones()` (the same verb the app uses) and
then reads it back through the SHIPPED `ScheduleRead4D` — not a re-implementation.
- **B-4D-1/2 (phase order)** — `phaseOrder()` equals the order independently derived in the witness
  from `SEQUENCE_RULES`' sequence numbers, and Architecture precedes MEP Rough-in.
- **B-4D-3 (source guard)** — `boq_charts.html` contains ZERO occurrences of the stale literal
  `'MEP Rough-in','Architecture'` and does reference `ScheduleRead4D`. Fails if the fix is reverted.
- **B-4D-4..8 (real records)** — every returned row carries a `task_id` present in `tasks`; every guid
  is a real `task_elements` row; guid totals reconcile; dates equal the persisted strings; every
  `predecessors` edge is a real `task_sequences` row.
- **B-4D-9/10 RED CONTROL** — the OLD path, replicated: group the same elements on `storey|||phase`
  under the stale hardcoded array. Assert (a) it yields ZERO real task_ids — the old page could never
  address the model — and (b) its phase ordering differs from the derived one. Both go GREEN only
  because the fix exists; reverting turns them RED.

Verification is `§`-tagged log output from headless node runs over real fixture DBs. No screenshots
(FUNDAMENTAL LAW).

### BOQ4D SHIPPED 2026-08-04 — `witness_boq_charts_real_schedule.js`, 6 buildings, 91/91, 145,763 elements
Branch `feat/gantt-edit-foundation` (bim-ootb). The "4" button's charts/audit/4D-export now read the
real `tasks` / `task_elements` / `task_sequences` rows through `compute4D`'s `capturedFn` hook.
- **New** `viewer/schedule_read_4d.js` (`window.ScheduleRead4D`) — the DOM-free reader; the witness
  `require()`s the SHIPPED module, it does not re-implement it.
- **All THREE** hardcoded `PHASE_ORDER` arrays deleted (`generateSchedule`, `audit4DSchedule`,
  `buildScheduleFromOps`; the third was not previously named in this file). `phaseOrder()` derives from
  `SEQUENCE_RULES`, giving `Substructure → Superstructure → Architecture → MEP Rough-in → MEP Final →
  Finishes` — verified against the sequence numbers by B-4D-1.
- **MEASURED, real fixtures** (`materializeZones` → read back): Duplex 18 tasks/1,143 el ·
  Clinic 32/16,912 · JKR 63/8,985 · HHS 17/6,880 · Hospital 35/63,415 · Terminal 71/48,428. Every row
  carries a real `task_id`, dates are the persisted strings verbatim, guid totals reconcile exactly
  (145,763 = the `task_elements` count), all 349 `predecessors` are real `task_sequences` edges with
  matching lags.
- **The `MAX_TASK_DAYS=20` cap is gone from this path** — not replaced by another number. Measured
  real durations it was distorting: Hospital max **253d** with **18** tasks over the old cap, Clinic
  96d/3, Terminal 58d/8, HHS 27d/1.
- **`storey|||phase` is no longer the identity.** Measured again on the same elements: Hospital's old
  key yields **60 groups for 35 real tasks** (19 tasks split) — the K0 defect, previously reachable
  through this page too. All 6 fixtures: 0 of the old keys is a real `task_id`.
- **RED control verified by actually reverting** (pre-fix `boq_charts.html` + a stale `phaseOrder`):
  **7 checks went RED**, including the data-driven B-4D-10a (`inversions=1 Architecture(d0)<MEP
  Rough-in(d15)` on Clinic). Restored, 91/91 green.
- `analysis_sidecar.js compute4D` now accepts `{source, tasks}` from `capturedFn` so an authored
  schedule is not logged as `captured`; `tests/test_4d_sidecar.js` extended (13/13, bare-array
  contract unchanged). `sw.js` v938→v939 + precache of the new file.
- **kernel_ops override deliberately skipped when a real schedule exists** (B4) — `buildScheduleFromOps`
  re-groups on `storey|||phase` and would re-introduce the 60-vs-35 split.

**Known remainders, not fixed (stated, not silent):**
- `generateSchedule()` (the no-authored-schedule fallback) still carries `MAX_TASK_DAYS=20`,
  `INTER_PHASE_LAG=5`, `INTER_STOREY_LAG=2` — all invented constants. Left alone deliberately: the
  brief forbids swapping an invented constant for another invented constant, and there is no real
  value to take when no schedule exists. It is now ONLY reached when nothing is authored.
- `audit4DSchedule` check 4 fails any task >120 days; Hospital's real longest zone task is 253d, so
  `§4D_AUDIT_DURATION` will report FAIL on real data. That threshold is itself an invented constant —
  NOT adjusted (adjusting it would be inventing a new one). It needs a user ruling on what a real
  duration ceiling is, or the check should be re-expressed against something real.
- Only verified headlessly at the model level. The fixtures have no `kernel_ops`-vs-`tasks` conflict to
  exercise, and the page's own DOM render was not exercised in a browser.

## Session close-out, 2026-08-04 (evening) — §GANTT_AXIS_OUTLIER FIXED (PR bim-ootb#1175, unverified live), §MEP_HUNG_FROM_ABOVE + piling/wall report OPEN, viewer toolbar/panel re-theme DEFERRED (not started — next session, verify #1175 live FIRST before any visual work)

### §GANTT_AXIS_OUTLIER — code-reviewed (no Chrome), 2026-08-04
Per user direction this session: study the code instead of a live Chrome re-verify. PR bim-ootb#1175
is already **merged into `feat/gantt-edit-foundation`** (bim-ootb#1173's branch, still OPEN vs `main`),
not into main directly.

Reviewed the actual merged diff at `origin/feat/gantt-edit-foundation` (`4d333c2`), not a re-derivation:
- `_ganttAxisStart`/`_ganttAxisEnd` correctly separated from `_projectStart`/`_projectEnd`; grep over the
  whole file confirms zero stray unqualified-axis references left in any Gantt-drawer function (bar
  draw, ruler ticks/gridlines, click-to-seek, `findBarAtClick`, `ganttHit`, the `__tmGanttBars` hook) —
  the PR's own audit claim holds.
- Trim will actually work at Hospital's scale: `_ops` = `kernel_ops` rows (one per installed element,
  thousands for Hospital), so the `n>20` 98th-percentile trim has ample population to exclude one
  `_UNKNOWN`-storey outlier. (Edge case: for `n` in ~21–50 the `ceil()` trim math is a no-op — inherited
  unchanged from the existing per-bar `§GANTT_MINI_TRIM`, never hit at real building scale.)
- `node --check` on the branch tip: clean.
- `git merge-tree` vs current `origin/main`: **zero conflicts** — `sw.js` only differs by
  `CACHE_VERSION` number, no precache-list collision. #1173 is 17 ahead / 4 behind main; safe to sync.
- Noted, not a regression: the MaxQ-Time/X-ray derived-order feature (`tmRestoreDerivedOrder` + its
  counterpart) mutates `_projectStart`/`_projectEnd` directly, bypassing `computeDays()`/`_ganttDirty` —
  so Gantt bars AND axis both go stale together if the drawer is open in that mode. Pre-existing
  (bars were already stale there before this fix too, same `_ganttDirty` gap), not introduced by #1175.

**No bugs found in the fix.** This is a code-level pass only — still not exercised in a live browser.
Live re-verify (`__tmGanttBars` on Hospital, confirm bars span most of the axis, ruler reads ~325d not
1049d) remains the honest open item before calling this DONE, per the PR's own test-plan checkbox.

## Session 2026-08-04 (later) — §TM_CLOSE_RESTORE FIXED (bim-ootb PR #1182, merged+live); §PILING_UNSUPPORTED diagnosed, root cause named, OPEN

Two bugs dispatched this session, worked in priority order in a private worktree
(`/tmp/wt-tm-restore-fix`, off fresh `origin/main` @ `dee5076` — #1181 already landed, so
§GANTT_OPS_BOOKKEEPING_LEAK/§GANTT_AXIS_OUTLIER were both already live before this session started).

### Bug 1 — Time Machine panel close doesn't restore the full building — FIXED, PR bim-ootb#1182 (merged, live)
User report, live: "when the TM panel is killed, the scene does not restore to full building."

**Root cause, found by reading `time_machine.js` (whitebox, no guess-and-check):**
`renderAtTime()`'s per-tick `clearHighlight()` deliberately SKIPS `_tm_xrayStaged` meshes — the
§Z_STACK_XRAY_STAGING grey/0.3-opacity ghost material an element gets while waiting on its own support
carrier to finish. That skip is a real, commented perf optimization (avoids an O(staged-population)
clone+dispose sweep every tick) — the header comment says renderAtTime's own showReal branch restores
each one "explicitly, exactly once, on the tick they actually resolve." **But `deactivate()` ->
`restoreVisibility()` calls that SAME `clearHighlight()`** — so any element still staged the instant TM
is closed (cursor not parked at `_projectEnd`, or its support chain unresolved) keeps its cloned ghost
material FOREVER — nothing else ever revisits it once TM is off, until a future TM re-activation
happens to touch that exact guid again. `_highlightMeshes` (the array holding it) is also never reset
on deactivate, so the leak persists across the whole "TM closed" window, not just one frame.

**Fix:** `clearHighlight(force)` gained a param; `restoreVisibility(force)` forwards it;
`deactivate()` now calls `restoreVisibility(true)` — same "nothing may survive TM being switched off"
convention this file already applies to `_gspClear`/`_tmXraySolidifyTs`/`__tmOverlaySync` in the same
function. The per-tick call site (`renderAtTime`) is untouched (`force` omitted → `false`), so the
O(1)-per-tick property the optimization exists for is preserved.

**Witness (`witness_tm_close_restore.js`, must be able to FAIL):** brace-match-extracts the REAL
shipped `applyHighlight`/`restoreMaterial`/`clearAllOutlines`/`removeOutline`/`clearHighlight`/
`restoreVisibility`/`deactivate` verbatim out of `time_machine.js` (a generalization of
`tests/test_tm_broadcast.js`'s marker-slice idiom to non-contiguous functions), stubs every OTHER
function `deactivate()` calls (stopPlayback, clearSparks, restoreSky, `_dlodDisposeBoxes`, etc. — all
unrelated to this defect, same stubbing discipline the existing broadcast test already uses), builds
one ordinary highlighted mesh + one xray-staged ghosted mesh via the real `applyHighlight()`, then
calls the real `deactivate()`. **Measured RED pre-fix: 3/7 fail** (staged mesh kept its cloned ghost
material, opacity stuck at 0.3, `_tm_highlighted`/`_tm_xrayStaged` both still `true`) — confirmed
against the actual pre-fix code, not asserted. **7/7 green post-fix.**

**Regression, all green, zero failures:** `witness_gantt_bar_identity` (6), `witness_tm_duration_sync`
(8), `witness_gantt_ops_blackbox` (7), `witness_zstack_xray_staging` (4), `witness_gantt_edit_constraints`
(18), `witness_gantt_edit_coherence` (7), `witness_gantt_row_order` (9), `witness_gantt_palette` (7),
`witness_zone_cpm` (11), `witness_zone_cpm_duplex` (9), `witness_support_invariant_all_buildings` (18),
`tests/test_tm_broadcast.js` (11).

`sw.js` v944→v945 (viewer/ file changed, same-commit bump). PR bim-ootb#1182: fast-checks + e2e-tests
both green, auto-merged into `main` (`e1315e8`), GH Pages build confirmed `built` (14:34:38Z same day).

### Bug 2 — "2 trucks and some walls before the piling" at hour 0 — DIAGNOSED, root cause named, NOT fixed (per this file's own standing rule: no 12th CPM redesign in one session)
Per the task brief, worked the two named-but-unchecked sub-hypotheses from the prior session's entry
above. **Both produced real, measured answers** — diagnosis only, no production code touched, using a
throwaway node script (not committed) against real fixture DBs via the same
`ScheduleAuthor._buildScheduleElements` + `ScheduleGate.computeSchedule` path
`witness_support_invariant_all_buildings.js` already uses.

**(a) Grid-cell edge case — REFUTED, not the cause.** Replicated `geoGate()`'s exact cell math
independently (same `CELL=4`/`EPS=0.05` constants) and compared brute-force (no bucketing) XY-overlap
support-finding against the real cell-bucketed result, for every ground-level wall on all 6 large
fixtures. **`realSupportButCellGridMissedIt = 0` on every building** — whenever a wall genuinely has a
qualifying structural element beneath it, the cell grid finds it. The grid bucketing is not the bug.

**(b) Staffage="trucks" — REFUTED.** `viewer/effects.js`'s only vehicle asset is `§STAFFAGE_CAR_MESH`
(a VW Beetle, PHOTOREAL_STILL_RENDER.md), placed ONLY on a manual Alt+P keypress, entirely independent
of the Time Machine cursor/schedule — not gated by `_active`/`renderAtTime` at all, and not named
"truck" anywhere in the codebase. It cannot be what the user saw during TM playback.

**The real, measured mechanism — a genuine absence of modeled structural support under specific
elements, not an algorithm bug:**
- Hospital: **27/515 (5.2%) ground-level walls** have `geoGate()` legitimately returning `baseMs` —
  brute-force confirms **zero** structural element (any footing/column/slab, seq≤4) overlaps their XY
  footprint at any elevation below them, anywhere in the whole 63,415-element building. Clinic: 59/633
  (9.3%). LTU_AHouse: **0/1592** — this is building-specific, not universal.
- **Drilled into WHY, for Hospital's 27:** for each, found the true nearest structural element by
  centroid distance — in all 6 sampled, the "nearest" structural candidate sits **6–22m ABOVE** the
  wall's own elevation (a different floor entirely), confirming these walls are genuinely isolated in Z,
  not a near-miss. Widening the check to ANY element class (not just seq≤4 structure) within 5m below:
  **20/27 have LITERALLY NOTHING beneath them at all** (any class) — a real geometric void in the
  extracted data, not a classification gap. The other **9/27 have another WALL directly beneath them**
  (e.g. an upper-level partition stacked on a lower wall) — `geoGate`'s structural candidate pool never
  includes walls as a support for another wall (only `wallGate` does, and only for promoted roof slabs)
  — a second, narrower, precisely-named gap.
- **This directly explains "hour 0" placement, measured:** Hospital's earliest-starting ground wall has
  `start === baseMs` exactly (literally hour 0). An element with zero structural gate is bounded only by
  its trade-gate (`tg`, same-storey lower-seq trades) and crew-slot availability — for the first crew
  claimed on an empty schedule, that's ≈0.
- **Same mechanism reproduces on the "trucks" candidate class.** `IfcBuildingElementProxy` (5,729
  elements in Hospital — the class the pre-existing `§XRAY_WALL_SCOPE` comment in `time_machine.js`
  already named against this EXACT user quote, "2 trucks came on first! Then walls!", when fixing a
  *different*, already-shipped wall-carrier-scope defect): **51/1,450 (3.5%) ground-level proxies have
  zero structural support**, and **3 of those start at exactly hour 0**. `IfcBuildingElementProxy` is
  the most likely real identity of "trucks" (generic/equipment-shaped modeled geometry, not staffage).
- **Same blind-spot shape as the already-documented `§MEP_HUNG_FROM_ABOVE` gap** (this file, above):
  `auditFloating()` reports these exact elements as 0 violations too, but only because its own check is
  VACUOUS when no candidate support exists at all — not lying, just never designed to catch "nothing to
  compare against." The existing 0/0-floating witnesses are correct and remain correct; they were never
  testing this case.
- **Explicitly distinct from `GANTT_ACCURACY.md`'s "1,735/81,722 elevation-vs-storey-label key
  contradiction"** (that file's `▶ RULING 2026-08-04`, problem 1) — that is a MISLABELED-RANK defect
  (storey label ranked above what it physically carries, while elevation itself agrees). This is a
  GENUINE ABSENCE of any XY-overlapping element in the geometry, at any label. Different mechanism, not
  a duplicate — naming the overlap per this session's brief, not merging the two investigations.

**Deliberately NOT fixed this session** — per this file's own accumulated rule (5 prior CPM-redesign
attempts rejected as over-engineered, `§4D_WALL_BORNE_STRUCTURE`/`§ELEMENT_CPM`/`§CPM_DUAL_ELEVATION`,
all "NOT FOR MERGE"). Two narrow, nameable candidate fixes for a future session, NOT scoped/started:
1. **The 9/27 wall-on-wall cases** — a real, narrow, additive gap: let a wall be a valid `geoGate`
   candidate support for another wall directly above it (Hospital/Clinic only; LTU_AHouse needs none).
   Same shape as the existing `wallGate`/`_ogWallGrid` pattern already used for promoted roof slabs —
   NOT a new mechanism, an extension of one that already exists. Needs re-running
   `witness_support_invariant_all_buildings.js`/`witness_wall_carrier_scope_all_copies.js` after, to
   confirm no inversions reappear (both prior sessions' hard-won invariants).
2. **The 20/27 true-void cases** — NOT an algorithm question, a DATA question: ⛔ BLOCKED — needs a
   user/data decision on whether "no footing modeled under this wall segment → unconstrained placement"
   is accepted as correct behavior for genuinely missing source data (real construction schedules DO
   place a wall on a slab-on-grade or grade beam not separately modeled as `IfcFooting`/`IfcPile` — this
   may be correct-for-the-data, not a defect), or whether it needs tracing back to the source IFC to
   confirm piling truly isn't modeled there. Not answerable from the extracted DB alone.

Diagnostic script used: throwaway node script against `~/bim-ootb/buildings/*_extracted.db`, NOT
committed (diagnosis-only, no production code touched, per this session's scope).

## §GEO_SUPPORT_LEAK — geoGate() misses real support that visibly exists, root-caused, NOT fixed (2026-08-04)

**What triggered this:** user-reported live, "2 trucks parked at hour 0" on Hospital, with a real ramp
visible beneath them in the viewer. First-pass diagnosis wrongly attributed this to Alt+P photoreal
staffage (a decorative, TM-independent car mesh) — **corrected by the user**: these are real IFC
elements, `IfcBuildingElementProxy` named "Semi Truck" (guids `2ddIK_HdvCoBT3_G2GF8Mn`/`...8Ms`),
verified directly against `elements_meta`/`element_transforms` in `Hospital_extracted.db`, not staffage
at all. **Lesson, stated plainly: check the IFC/DB directly before attributing a symptom to a feature
that only looked plausible — that first pass drifted.**

**Root cause, traced to real coordinates, not guessed:**
`geoGate()` (`viewer/schedule_gate.js:152-158`) tests `S.base_z < el.base_z - EPS` — a real structural
candidate `S` only counts as support if `S`'s own base sits below the element's own base. For the two
truck elements, a real ramp assembly genuinely exists in their XY footprint (`IfcSlab` "Concrete-200mm
slab on 300mm base", `IfcWallStandardCase` retaining walls, real footings below that — all confirmed via
direct SQL query against `element_transforms`/`elements_meta`, not inferred). But every one of those real
structural elements has `base_z` in the 164.0-164.5m range, while the trucks' own computed `base_z`
(`center_z - bbox_z/2`) is 163.21m — **below every real structural base in the footprint** — so
`geoGate()` finds nothing and the trucks schedule at `baseMs` (hour 0), same as if nothing were modeled
there at all.

**User's correction on how to read this, which reframed the finding:** do not trust the derived
`base_z = center_z - bbox_z/2` proxy over the real, visually-confirmed spatial relationship (a ramp IS
there, visibly, under the truck) — the LEAK is in `geoGate()`'s support-contact heuristic, not
necessarily in the truck's placement data. **This is the correct basis going forward: the engine must
reconcile against real spatial coordinates, not a symmetric-bbox approximation that can disagree with
what's actually modeled.**

**Also corrected: no name/class special-casing.** The engine is supposed to stay abstract to
discipline/phase/resource/`ifc_class` (as `SEQUENCE_RULES` already is) — a hardcoded "if name contains
Semi Truck" fix would violate that, so none was written.

**`witness_geo_support_leak.js`** (`bim-compiler/scratchpad/`, NOT committed to bim-ootb — detection
only, per instruction: "put a witness to fail without fall back," no auto-correction) — general,
zero name/class references beyond the engine's own existing `seq<=4`/`seq>4` structural split (not
invented here, already how `schedule_gate.js` classifies "structure"). For every element the shipped
`computeSchedule()` left ungated (`start===baseMs`), checks by pure XY-coordinate overlap whether any
real `seq<=4` structural element exists in its footprint at any Z. Exits non-zero if the signature is
found anywhere — a passing run means genuinely absent, not silently tolerated.

**Measured across all 6 fixtures — the signature is real but narrow, not systemic:**
```
Duplex   checked=1103  leaked=0
Clinic   checked=15161 leaked=0
JKR      checked=5     leaked=5
HHS      checked=4461  leaked=0
Hospital checked=5     leaked=5
Terminal checked=13367 leaked=0
```
10 leaked elements total out of 34,102 currently-ungated elements checked across every real building
fixture — genuine, reproducible, but rare. Hospital's 5 leaked elements span TWO different `ifc_class`
values (`IfcWallStandardCase` AND `IfcBuildingElementProxy`, not just the trucks) — confirming this is a
general geometric heuristic gap in `geoGate()`, not an item-specific defect, and not fixable by anything
keyed on name or class.

**NOT FIXED — deliberately.** The correct geometric contact-test (e.g. "does a real structure's TOP
surface fall within the element's own vertical span" vs. today's "is a structure's base below mine") is
a real design decision this session did not verify carefully enough to ship without risk of inventing an
unproven heuristic. Left open on purpose, per this project's own no-invent discipline — next session
should design and witness the replacement contact-test explicitly, checked against all 10 leaked
elements (and re-run against all 6 fixtures to confirm zero regression on the 34,092 correctly-gated
ones) before it ships.

### §GEO_SUPPORT_LEAK — FIXED same session, PR bim-ootb#1183 merged + live (2026-08-04)

Same-session follow-up, user directive: "SOLVE THEM TILL ZERO." `geoGate()` now also counts a
structural candidate as support when its entire vertical span is CONTAINED within the element's own
`[base_z,top_z]` (new clause, additive only — a match can only push a gate later, never earlier).
Zero name/class special-casing, pure `base_z`/`top_z` geometry.

**First witness attempt over-detected** — its "any XY-overlapping structure at any Z" criterion
flagged 3 JKR "Slab Edge" elements as leaked when their only overlapping structure (a real `IfcSlab`)
sits ABOVE them, flush, not below/contained (a formwork/edge-trim detail poured at-or-before its
slab, genuinely nothing real below it — same category as a footing on bare ground, not a defect).
Corrected before shipping: the witness now uses the IDENTICAL directional test as the fix itself.

**Measured, both directions:**
- Pre-fix (this same corrected witness against unmodified `main`): **7 real leaks** — 5 Hospital
  (both trucks + 2 more), 2 JKR. Confirms the earlier "10 leaked" number included 3 false positives
  from the looser v1 detector.
- Post-fix: **0/0 leaks across all 6 real building fixtures.**
- Authoritative `ScheduleGate.auditFloating` (the same invariant live `§SUPPORT_CHECK` reports) —
  **0 floating, all 6 buildings, both before and after** — confirms no regression, and separately
  confirms `auditFloating` shares the identical below-only blind spot as pre-fix `geoGate()` (it
  did NOT catch the trucks either) — worth naming for whoever next touches that function, not
  silently relied on as independent proof.
- Full existing engine suite (bar identity, edit coherence/constraints, row order, palette, duration
  sync, gap1, ops blackbox, TM close-restore, BOQ4D) — **167/167 unchanged.**

`witness_geo_support_leak.js` committed to bim-ootb root. `sw.js` v945→v946. Not yet exercised live
in a browser — same open item as every fix this session.

## ▶ PRELIM NOTE 2026-08-04 — §CLASS_UNMATCHED_FALLBACK, a SEPARATE gap from the trucks above, NOT yet handed to a dev session

**Not the same bug as §GEO_SUPPORT_LEAK above — flagging the distinction explicitly, because the
first report of this ("2 IFC trucks leaked thru and claimed wrongly their type") got conflated with
the trucks-at-hour-0 finding above during triage.** The trucks are correctly classified
(`IfcBuildingElementProxy` → Architecture, its own explicit `SEQUENCE_RULES` entry, `resource:null`
by deliberate design) — their bug was a geometric support-detection heuristic (`geoGate()`), now
fixed. This section is about a genuinely different mechanism: `matchRule`'s silent fallback for
classes that match **no** `SEQUENCE_RULES` key at all, in bim-ootb.

**What was built, general-purpose per the original ask** ("a black box output log ... so u can
read it when testing", hardened to "fail no fall back any nuance") — not a synthetic-fixture
regression test like `witness_gantt_ops_blackbox.js`, but a real-data audit:
`viewer/tests/witness_class_fallback_blackbox.js`, bim-ootb PR **#1185** (pushed, branch
`chore/blackbox-harden`, **not merged, not yet run by the session doing 4D polish**). Runs the REAL
`matchRule` (required from `schedule_author.js`, sliced by balanced braces from both independent
`time_machine.js` closures — never reimplemented) against REAL `elements_meta` across Hospital,
Terminal, LTU_AHouse, Duplex (239,469 elements total).

**Result:**
```
FAIL G-A hits=3
  Hospital: IfcDistributionControlElement (861 elements) -> silently Architecture/seq6/no-resource
  Hospital: IfcSwitchingDevice (113 elements)             -> same
  Duplex:   IfcSpace (21 elements)                        -> same, residential too, not Hospital-only
```
G-B/C/D pass — the three `matchRule` copies agree with each other everywhere they DO match, and
every element in all four buildings is accounted for by the audit (no silent exclusion). Correctly
does NOT flag `IfcBuildingElementProxy` (the trucks' own class) — verified via an independent
`hasExplicitRule()` check that a class with a real, deliberate rule (even one with
`resource:null` by design) is never confused with a class matching nothing.

**Not fixed — diagnostic only, same discipline as §GEO_SUPPORT_LEAK above.** Two follow-ups for
whoever picks this up: (1) route unmatched classes to a loud `§CLASS_UNMATCHED` log or the existing
`§Z_STACK_XRAY_STAGING` reveal path instead of the silent default, in all three `matchRule` copies;
(2) re-run PR #1185's witness after — G-A should flip to 0.

**Hand-off note, stated once so it doesn't need re-litigating:** nothing in this repo auto-runs a
witness — the discipline has always been "the session working on X runs X's witness," never a
background process. This witness lives on an unmerged branch a different session wrote; the session
doing 4D/Gantt polish has no way to know it exists unless told directly. Same for any future
diagnostic built this way — a prelim note here is the hand-off mechanism, not a substitute for it.

### §CLASS_UNMATCHED_FALLBACK — ✅ FIXED same-day, bim-ootb PR #1186 (2026-08-04/05, auto-merge armed)

Both follow-ups closed, headless only (live browser explicitly left to the user this session):
- `IfcDistributionControlElement` (861, Hospital) → explicit rule, mirrors its already-classified
  IFC4 siblings `IfcController`/`IfcAlarm` (MEP Final/ELECTRICIAN) — real schema fact, not guessed.
- `IfcSwitchingDevice` (113, Hospital) → explicit rule, mirrors its IFC4 parent `IfcFlowController`
  (MEP Rough-in/ELECTRICIAN).
- `IfcSpace` (21, Duplex) → spatial zone, not physical work — same treatment as `IfcOpeningElement`:
  an explicit rule entry (so `hasExplicitRule` sees it as deliberate, not silent) **plus** exclusion
  from the same 4 schedule-building queries that already excluded `IfcOpeningElement`
  (`schedule_author.js` `_buildScheduleElements`, `time_machine.js` ×3 — the xray build, the live
  `injectGantt` element query, and the coverage-ratio denominator).
- Added a loud `§CLASS_UNMATCHED` `console.warn` at the fallback point in all 3 `matchRule` copies —
  defense in depth for a genuinely-unknown class on a future IFC set, so it logs instead of vanishing.

`witness_class_fallback_blackbox.js`: G-A hits 3→0, G-B/C/D unchanged. Regression, all headless:
bar_identity 6/6, palette 7/7, row_order 9/9, coherence 7/7, constraints 18/18, zone_cpm 11/11,
zone_cpm_duplex 9/9, tm_duration_sync 8/8, support_invariant_all 18/18, gap1 7/7,
boq_charts_real_schedule 91/91 — 190/190 total, zero regressions.

**NOT touched, a separate real gap found while reading the code, not fixed under this ticket's
scope (stay-on-topic discipline):** `materializeDefault` (`schedule_author.js:404`, the "blank mode"
coarse authoring path, still live-called from `schedule_author_ui.js` + `schedule_diff.js`) reads
`elements_meta` with **zero** class exclusion at all (`:450`) — not even `IfcOpeningElement`, despite
the `_buildScheduleElements`/`materializeZones` path (the one this fix touched) already excluding
both `IfcOpeningElement` and now `IfcSpace`. Whoever picks up `materializeDefault` next should add
the identical `AND ifc_class != 'IfcOpeningElement' AND ifc_class != 'IfcSpace'` filter there — named
here so it isn't lost, not fixed here so this PR stays scoped to the one finding it was asked to close.

## §GENERATE_4D_HANG — root-caused and removed, not just hidden (2026-08-05)

User correction, stated directly: *"Hiding the Generate 4D schedule which hangs is not following my
request to get it removed — we be doing it natively in the gantt chart panel."* A prior session had
removed the ✎ toolbar icon that opened `ScheduleAuthorUI`'s side panel, but then had to add a NEW
drawer button (`tm-gantt-authorbtn`, "Generate 4D schedule") because removing the icon left no way to
author a schedule at all — and that new button just called `window.ScheduleAuthorUI.toggle()`,
**reopening the exact same old panel**. Not a removal, a relabeled redirect — the user was right to
reject it. Two real, separate problems were tangled together here and both are now closed.

### Problem 1 — the hang itself, measured not guessed, bim-ootb PR #1193

`4D_SCHEDULE_PERFECTION.md`'s own §Open Decisions (above) had already named the suspect
(`§PHASE_OVERLAP_SUPPORT_GUARD`'s `_ogStructGrid`/`_ogWallGrid` cell-bucket pass) but explicitly
flagged it "NOT yet measured." Measured first, before touching anything — a Node script sliced the
real block out of `time_machine.js` by raw text span (brace-balance checked) and ran it against real
extracted geometry from every real fixture:

```
Hospital (63,415 elements):  1695ms
LTU_AHouse (122,330 elements): 2175ms
Terminal (48,428 elements):  4636ms   <- fewer elements than the other two, slowest by far
```

Element count doesn't predict the cost — Terminal is 22 stacked storeys over a small footprint.
**Root cause: the spatial grid bucketed candidates by XY only, with zero Z-filtering.** Every floor's
structural elements piled into the SAME xy cell (Terminal's worst cell: 379 members), so a query for
one element had to linear-scan almost every OTHER floor's structure before the existing
`S.bz<T.bz-EPS && |S.tz-T.bz|<=GAP` check inside the loop finally rejected them. Hospital's bigger,
less-stacked footprint spreads elements across far more cells (831 vs Terminal's 234), so it never
hit this.

**Fix:** bucket by (x,y,z), not (x,y). Candidates register under their own real vertical extent at
build time; a query only scans cells in the TARGET's own real z-neighborhood `[bz-GAP, bz+GAP]` — the
only range the existing predicate can ever match. The inner predicate is byte-identical; only which
cells get scanned changed. Measured after: Terminal 4636ms → ~2900ms (1.6x). **Stated honestly, not
oversold: this is not a full elimination.** 2.9s is still a real synchronous block on a large
building; a numeric packed cell key (instead of string concatenation) or yielding the whole `_cap`
overlay pass to the browser between phases would very likely close more of the remaining gap, and is
named here as a real follow-up, not claimed as done.

`witness_gantt_og_grid_perf.js` (new): an independently-written O(n²) brute-force reference (same
predicate, same in-place-mutate-as-you-go cascade the real algorithm depends on, zero grid) on Duplex
— 0/1122 mismatches against the real grid-based code, proving the Z-band pruning drops nothing real.
Plus a performance ceiling on Terminal (<3500ms) so a future change can't silently reintroduce the
XY-only blowup. Full regression clean (12 existing witness files, unchanged pass counts).

### Problem 2 — the entry point, now genuinely native, bim-ootb PR #1194

`generateGanttSchedule()` (new, `time_machine.js`) replaces the `ScheduleAuthorUI.toggle()` delegation
entirely. It calls `ScheduleAuthor.materializeZones()` directly — the SAME real engine verb
`schedule_author_ui.js`'s own `generateDraft()` zone-detail path already uses, not a reimplementation,
not a second scheduler — falling back to `materializeDefault()` only if zones are genuinely
unavailable, matching the panel's own fallback exactly.

**One case deliberately still opens the old panel — not a loophole, a correctness requirement:** a
real imported (Bonsai/Revit/IFC-native) schedule already active. `generateDraft()` already guards this
(`if (act.captured) {...}` — never regenerate over a real import) and a native path skipping that
guard would silently create a competing `SCH_AUTHORED` schedule alongside the real one (`_cap` reads
ALL schedule_ids, so two would double-count — the exact bug the panel's own guard comment already
named). The native path replicates the identical guard via `ScheduleAuthor.activeSchedule(db)` before
ever calling `materializeZones`. This is now the ONE legitimate remaining reason the old panel is
reachable at all — not a general re-opening of it, and worth remembering if a future session is
tempted to delete `schedule_author_ui.js` outright: the captured-schedule editing case still needs a
home, and this button is currently it.

`witness_gantt_native_generate.js` (new, sliced from the real function by balanced braces): Scenario A
(no schedule yet) proves native generate creates real tasks and the old panel is NEVER opened.
Scenario B (a real captured schedule, seeded via the SAME real `materializeZones` call under a
different `scheduleId` — not hand-crafted rows) proves no synthetic schedule gets created, the old
panel opens exactly once, and the real imported schedule is provably untouched (exact task-count
match). 5/5 on Duplex and Terminal. Full regression clean.

**Also caught and fixed, both PRs:** `sw.js` `CACHE_VERSION` was NOT bumped for either change at
first pass — despite this exact mistake already happening 3 times earlier in the same session (see
`feedback_bimootb_sw_cache_bump_on_viewer_change.md`, updated with this recurrence). Both PRs now
carry the bump in the same diff (v948→v949 for #1193, v949→v950 for #1194). **A real, separate
landmine hit shipping #1194: `main` had already advanced past #1193 by the time #1194 was pushed
(squash-merge changes the commit hash — CLAUDE.md's own documented landmine), so `gh pr merge --auto`
reported `CONFLICTING`.** Fixed correctly per that doc's own guidance — `git fetch && git merge
origin/main` (not a rebase, not a fresh redo), one trivial conflict in `sw.js`'s version line,
resolved to the higher number, full regression re-run clean post-merge before pushing.

### §GANTT_EDITABLE_E2E — verified by code inspection, not claimed via a live run (2026-08-05)

User's third ask: confirm the drawer is genuinely end-to-end editable, and decide whether an explicit
"edit mode" toggle is needed. Traced, not run live (this session's standing directive: live browser
testing is the user's job, not this session's):

- `generateGanttSchedule()` → `window.tmRefoldSchedule()` → `deactivate()`/`activate()` →
  `_activateAsync()` → `injectGantt()`. `_cap` (`time_machine.js:3523`, the overlay that binds real
  `task_id`s onto elements) is a fresh IIFE re-evaluated on EVERY `injectGantt()` call, reading
  `tasks`/`task_elements` live off the DB — so it correctly picks up a schedule materialized moments
  earlier. This is the EXACT SAME reactivation path `applyTo4D()` already used, and that path was
  already browser-proven earlier this session (§BROWSER_PROOF above, PR #1173, commits
  9799f24/6a5073f/9902f6d fixed "authoring after opening the drawer never took effect"). The native
  generate button reuses already-proven machinery rather than inventing new glue that could carry the
  same class of bug.
- `buildTaskIndex()`/`invalidateGanttModel()` (drawer's own bar→task_id cache, separate from `_cap`)
  is correctly invalidated on every path that can change the schedule — confirmed by inspection, not
  re-derived from scratch (this exact caching bug was ALSO already found and fixed this session,
  same PR #1173).
- **Conclusion: yes, genuinely editable end-to-end, by construction** — K0's bar-identity binding
  (100% resolve, all 7 fixtures, `witness_gantt_bar_identity.js`) + this session's native generate
  entry point + the already-proven reactivation path together give a real, unbroken chain from
  "open Time Machine on a fresh building" to "drag a bar." The one remaining unverified link named
  earlier (§BROWSER_PROOF "STILL OPEN — the drag link not closed") — a synthetic pointer test that
  never confirmed a `§GANTT_DRAG_COMMIT` fired with a MEASURED bar position — is real and still open,
  but is a live-browser-only verification, correctly left to the user per this session's directive,
  not chased further here.
- **"Special edit mode ON" toggle — recommend AGAINST building one.** This file's own §E section
  commits explicitly to "MS Project convention; do not invent a new idiom." Neither MSP nor P6 gate
  editing behind a modal lock — bars are always directly draggable, and the safety net is
  clamp/cascade (C1/C2, already shipped) plus now single-level Undo (§GANTT_EDIT_UNDO, PR #1188,
  shipped this session). Adding a toggle would invent a UI idiom this codebase has already deliberately
  avoided, to solve a risk (accidental drag) the existing mechanisms already cover. Not built.

### Handoff, not chased further this session (concurrent-session finding)

A concurrent CPE/cinema session found a real, separate bug in this file while working on their own
POV-scrub feature, and asked for it to be recorded here since `time_machine.js` is owned by whichever
session is doing 4D work — **not yet independently verified by this session, recorded as reported:**
Time Machine's buildup visibility gate (`time_machine.js:1443`, `_dlodInView`) hides "placed" elements
outside the MAIN camera's frustum only — `_dlodCamPos = app.camera.position`, hardcoded. When a user
scrubs with the CPE POV panel open (that session's own new §CPE_SCRUB_VF_LIVE feature), the main
camera stays parked while `vfCam` (the POV inset camera) moves independently — so the POV can show
construction state gated by the WRONG camera's frustum. Whoever picks this up next: confirm the exact
mechanism first (read the line, don't assume), then decide whether `_dlodCamPos` should switch to the
active POV camera when one is open, or whether the DLOD gate needs to consider both cameras' frustums.

## Session close-out, 2026-08-05

Five bim-ootb PRs this session on top of the four from earlier the same day (#1186–#1190): #1193
(hang root-caused + fixed, measured), #1194 (native generate, old panel de-fanged to one legitimate
fallback case). All auto-merge armed/merged, all witnessed, all regression-clean. `materializeDefault`'s
missing exclusion filter (named above) and the concurrent session's `_dlodInView` finding (above) are
the two open items for the next session — both are named with enough detail to start from directly,
neither needs rediscovery.

## §GANTT_EDIT_LOCK — Generate button's last old-panel path removed, edit lock toggle added (2026-08-05)

User, live-testing #1194's native generate button, caught the remaining hole directly: *"remove 4D
generate button as it calls old panel up. We prefer to edit right in the gantt chart itself."* #1194
had already made the common case native (`materializeZones`/`materializeDefault` called directly), but
kept ONE deliberate fallback — a captured/imported schedule still reopened `ScheduleAuthorUI`. That was
the exact path the user hit. Two decisions taken together, not separately:

1. **Drop the fallback, not just the button.** A captured schedule is now left exactly as imported
   (never regenerated — the non-clobber guard stays) and edited through the SAME drawer surface as any
   other schedule, once its bars carry real `task_id`s via the normal cap/`injectGantt` load path. No
   button, no panel, reachable from `time_machine.js` any more at all.
2. **Auto-materialize, not button-triggered.** `drawGanttMini()` now calls `generateGanttSchedule()`
   itself the first time the drawer has zero editable bars (one attempt per `activate()`, tracked by
   `_ganttAutoGenAttempted` so a genuine materialize failure doesn't retry every redraw) — no click
   required, matching "edit right in the gantt chart itself."

**New question this opened, resolved same session:** with the button gone, what stops an accidental
drag? User: *"perhaps need an edit toggle? Where it is ON, u can edit, and when off, it is committed to
the JSON."* This **reverses** this file's own earlier recommendation (§GANTT_EDITABLE_E2E above,
2026-08-05 same day, pre-dating this section: "recommend AGAINST building [a toggle]... MSP/P6
convention has none") — noted, not silently overwritten: the earlier reasoning no longer applies once
the safety net it was weighing (Undo) has to cover a DEFAULT-ON always-armed drawer instead of an
opt-in one behind a button. User's own follow-ups narrowed the design before any code was written:
- **Scope**: "edit means whatifs dragging, CPM linking, bar length change, and drag to new date/time
  spot" — i.e. gate exactly E1/E2 (move/resize) + E3 (drag-to-link) + E7 (typed props/unlink), nothing
  else.
- **Persistence model**: asked directly (draft-then-commit vs immediate-write-plus-lock) rather than
  assumed — user picked **immediate write, toggle=lock only**. Every accepted edit still writes
  straight to `tasks`/`kernel_ops` the instant it commits, exactly as it did before this toggle existed
  (§GANTT_EDIT_UNDO's single-level undo already covers that immediate write). "Committed to the JSON"
  in the user's phrasing describes that existing immediate-write behavior, not a new draft layer — no
  second persistence path was built.
- **Canvas liveness**: asked whether locking should freeze the timeline view too — user: "if canvas is
  runtime responsive, it gives the user feedback which is desirable." Scrub/seek/render are untouched
  regardless of lock state; only the edit-INITIATING handlers are gated.

**Implementation** (`time_machine.js`, bim-ootb PR #1198 landing branch `feat/gantt-edit-foundation`
onto `main` — this branch had drifted since #1173's squash-merge, see below): `_ganttEditable` (default
`false`, i.e. locked) gates a single point of entry — `wireGanttDrag`'s `pointerdown` handler. Gating
there alone covers move/resize AND drag-to-link, because `endDrag`'s link/commit logic can never run
without a live `_drag` object that pointerdown is what sets. The E7 double-click → `openGanttProps` is
gated separately (doesn't depend on `_drag`). `tm-gantt-noauthor`/`tm-gantt-authorbtn` UI removed
outright, replaced by a `🔒 Locked` / `🔓 Editing` toggle button in the same header slot.

`witness_gantt_edit_lock.js` (new, slices the real `wireGanttDrag` by balanced braces, never
reimplements the gate): locked blocks drag-start and props-open, unlocked allows both, plus a RED
CONTROL proving a bar with no `task_id` is rejected on a DIFFERENT, unrelated guard regardless of lock
state (so the two guards can't be confused with each other) — 5/5. `witness_gantt_native_generate.js`
Scenario B updated for the new no-old-panel behavior (was asserting the OLD spec: "the old panel WAS
opened exactly once" — now asserts it never is) — 5/5. Full regression re-run clean: bar_identity 6/6,
edit_coherence 7/7, edit_constraints 18/18, row_order 9/9, palette 7/7, edit_undo 9/9, baseline 11/11,
og_grid_perf 3/3, class_fallback_blackbox 8/8. One pre-existing `witness_gantt_ops_blackbox.js` fail
(a `BUILDING_OPEN` pseudo-op leaking into `materializeZones`' op stream, `_UNKNOWN` storey) confirmed
present on `origin/main` BEFORE this change too via `git stash` — not a regression, not fixed here,
named as a real small open item for whoever next touches `materializeZones`' op bookkeeping.

**Separate landmine hit picking this branch up, not caused by this session's own edits:** `/tmp/wt-
gantt-edit` (branch `feat/gantt-edit-foundation`) was 7 commits behind its own `origin` and, after
syncing that, turned out to be 18 commits behind `origin/main` too — PRs #1171/#1173 (this branch's own
"the editable 4D surface" work) had squash-merged into `main` back on 2026-08-04, but a LATER PR on
this same branch (#1175, §GANTT_AXIS_OUTLIER — the near-empty-drawer fix) was based ON the pre-squash
branch and never itself reached `main`. So `main` had the editing foundation but NOT the axis-outlier
fix — a real, live gap, not hypothetical. `git merge origin/main` into the branch (per this project's
own documented squash-merge doctrine — merge, don't redo) produced exactly 2 conflicts: `sw.js`
(CACHE_VERSION — took the higher number, v951) and `time_machine.js` (the drawer's authoring-entry
button — took `origin/main`'s already-native #1194 version over this branch's older
`ScheduleAuthorUI.toggle()` call, which is what #1194 itself had already fixed). PR #1198 carries BOTH
the recovered axis-outlier fix and this session's own lock-toggle work onto `main` in one go, since they
were sitting on the same stale branch together.

### Follow-on landmine hit shipping the next two fixes, recovered correctly (2026-08-05, same session)

After #1198 landed, this session kept working on the SAME `feat/gantt-edit-foundation` branch — pushed
`materializeDefault`'s exclusion filter (task #1 above) and §DLOD_VF_CAMGUARD (below) as two more
commits to it, on the assumption the branch's PR would keep tracking new pushes. **It did not**:
`gh pr merge 1198 --auto --squash` had already fired on an earlier green head (fast-checks passed at
19:59:38, e2e at 20:00:46, `mergedAt`=20:00:49) — both follow-up commits landed on the branch AFTER
that timestamp, so they were never part of the squash. Caught BEFORE reporting done, not after: this
session's own `feedback_verify_pr_merge_before_followup_push` memory says verify before assuming a
push into an already-armed PR reached `main` — `git merge-base --is-ancestor <sha> origin/main` on both
orphaned commits confirmed `NO`. Recovered exactly per this file's own documented procedure (CLAUDE.md
Concurrent Branches note): fresh branch off current `origin/main` (`fix/gantt-post-1198-followup`, same
worktree, clean cherry-pick, zero conflicts), full witness re-run against the REAL post-#1198 `main`,
new PR #1199 (auto-merge armed) — not a re-push to the stale branch, not a redo of the work itself.
**Lesson for next session:** once a PR's auto-merge is armed, treat that branch as closing — start the
NEXT fix on a fresh branch off `origin/main` from the start, don't keep stacking commits onto a branch
whose PR may merge out from under you at any moment.

### §GANTT_MATDEFAULT_EXCLUSION — closed (bim-ootb PR #1199)

The `materializeDefault` gap named above (`schedule_author.js:404`, no class exclusion at all) is
fixed: same `ifc_class != 'IfcOpeningElement' AND ifc_class != 'IfcSpace'` filter as the already-fixed
`_buildScheduleElements`/`materializeZones` path. `witness_materialize_default_exclusion.js` (new):
real `Duplex_extracted.db` fixture (50 real `IfcOpeningElement` + 21 real `IfcSpace` rows — RED CONTROL
confirms the fixture actually exercises the fix), zero excluded-class guids assigned a task, assignment
count matches `elements_meta` minus both classes exactly. 4/4.

### §DLOD_VF_CAMGUARD — closed (bim-ootb PR #1199)

The concurrent-session finding handed off above (and independently root-caused the same way in
`CINEMA_PATH_EDITOR.md`'s own SESSION HANDOFF) is fixed. `renderAtTime()`'s buildup-visibility gate
(`_dlodInView`, via `bHideForProxy`/`hideForProxy`/`iHideForProxy`) read `_dlodCamPos`, hardcoded to
`app.camera.position` — the MAIN camera — even while CPE's POV panel scrubs its own `vfCam`
independently once main stays parked. Extracted the camera choice into its own pure function,
`_dlodResolveCamera(app)`, and added one minimal read-only accessor on CPE's own exposed API —
`window.APP.cinemaPathEditor.activePOVCamera()` — returning the live `vfCam` only while the viewfinder
is genuinely on, else `null`. `time_machine.js` never reaches into CPE's internal `_state` directly;
it only reads this one accessor, so CPE's own module boundary stays intact. Falls back to the main
camera in every other case (viewfinder off, CPE not loaded, no `cinemaPathEditor` exposed) — zero
behavioural change off this one new opt-in branch.

`witness_dlod_vf_camguard.js` (new): slices the real `_dlodResolveCamera` by balanced braces, proves
all 3 fallback cases return the main camera, the viewfinder-on case returns the POV camera, plus a RED
CONTROL proving the branch is actually live (not vacuously always the main camera regardless of
input). 5/5. This closes BOTH of the two open items this file named at its previous session close-out
— nothing left unrecovered from that handoff.

## Gantt drawer UX batch (2026-08-05, same session) — user-driven, aesthetics-first discussion before code

User gave a screenshot (props panel overlapping the storey labels/ruler, no room to grow) and asked for
an aesthetics/practicality/competitive-advantage DISCUSSION before any code — three concrete PRs came
out of that discussion, each user-confirmed before implementation, plus one design thread deliberately
left as discussion (§LINK_UX below) and two real gaps found and reported, not invented fixes for.

**§TM_PANEL_RESIZE (bim-ootb PR #1201)** — the drawer had a resize grip for the internal Gantt box's
HEIGHT only; the whole drawer's WIDTH was hardcoded 376px, which is what the screenshot's overlap
actually was. New `tm-panel-resize-grip` on the drawer's right edge — `_panel` is horizontally centered
(`left:50%`/`translateX(-50%)`), so one edge handle grows the box symmetrically (2x drag-distance math,
tested). Also auto-expands to 560px the instant Editing toggles on (only if narrower already), restores
the exact pre-edit width on lock — user's own follow-up ask ("make the borders auto expand when Edit is
ON"). `witness_tm_panel_resize.js` 5/5.

**§TM_RULER_SHIFT (PR #1202)** — user's own definition, given directly: "dragging the day ruler adjusts
the whole project's start/finish... defaulted to today if the JSON is silent... when edited it is
updated along with any other edit." New `ScheduleAuthor.shiftSchedule(db, scheduleId, deltaDays)`:
translates EVERY task (leaf + summary) by a constant number of days — no C1/C2 constraint checking
needed, a uniform shift preserves every relative task position by construction. The previously
non-interactive day ruler is now draggable, gated behind the same Editing lock as everything else (this
is the biggest possible edit, not a reason to exempt it), reuses the EXISTING single-level Undo
unchanged. Also fixed `generateGanttSchedule()`'s hardcoded `'2026-01-01'` default to the real current
date. `witness_shift_schedule.js` 8/8, `witness_gantt_ruler_shift_lock.js` 4/4.

**§GANTT_GROUP_MOVE (PR #1204)** — user's own resolution of an MS-Word-style marquee-select question:
"move is clear... thus the MS Word is for that only" (i.e. NOT for link — see §LINK_UX below). New
`ScheduleAuthor.shiftTasks(db, taskIds, deltaDays)` (shares `_shiftRows` with `shiftSchedule`), scoped
to an explicit task_id list. Dragging from empty canvas space starts a marquee (MS-Word convention),
dragging any SELECTED bar moves the whole group together, dragging an unselected bar clears the group
and falls back to a normal single drag, click-away (near-zero marquee) clears the selection — same
gesture starts a new one and dissolves the old, no separate "ungroup" verb. Selection is EPHEMERAL UI
state, explicitly never persisted (user confirmed this framing directly — "group" is not a saved
concept, only the resulting date change from an actual drag is a real edit). `witness_shift_tasks.js`
7/7, `witness_gantt_bars_in_rect.js` 5/5, `witness_gantt_group_move.js` 9/9.

**§LINK_UX — discussion only, no code changed.** User asked whether long-press should replace the
existing drag-bar-onto-bar link gesture, and whether a separate long-press should unlink. Recommended
against both: the existing gesture already has a 14px-vertical-travel guard against accidental
triggering, and unlink already has a deliberate, low-risk mechanism (the props panel's per-edge unlink
button) that a canvas long-press would only duplicate with MORE misfire risk on touch. Long-press's one
real use is disambiguating "start a marquee that begins ON a bar" from "move that bar" — user has not
yet confirmed building that refinement; the base marquee (start-from-empty-space only) is what shipped.
**User separately flagged the linking-via-props-panel flow itself as "a bit tedious and unfriendly to
long tail DIY lay people"** and wants a way to confirm a link WITHOUT opening the panel — real, unshipped
follow-on, next session should explore an inline confirmation (e.g. a toast/tooltip at the join point)
rather than the panel.

**§HISTORY_DOT_AUDIT — question answered by reading the code, not guessed.** User asked "is each Gantt
edit counted as a [world-]history dot?" `common/history_tap.js`'s `sniff(true)` wraps `console.log`
globally and captures every `§`-tagged line UNLESS its tag is in `DENY_TAG` or its label matches
`NOISE_LABEL` (`universal_history.js` `_drainTap`, the bar's read-only subscription to that stream).
**Answer: yes, automatically** — none of this session's Gantt commit tags (`§GANTT_DRAG_COMMIT`,
`§TM_RULER_SHIFT_COMMIT`, `§GANTT_GROUP_SHIFT_COMMIT`, `§GANTT_EDIT_LINK/UNLINK`, `§GANTT_PROPS_APPLY`,
`§GANTT_EDIT_UNDO`, `§SE_SHIFT`/`§SE_GROUP_SHIFT`, `§GANTT_GROUP_SELECT`) are denied or noise-filtered,
so every one mints a dot with ZERO extra wiring — this is the "coverage falls out the moment a feature
logs §" design `_drainTap`'s own comment describes. Caveat, also real: this only happens while the
History knob/recording is actually active (`HB.isEnabled()`) — not an always-on background capture.

**§SCHEDULE_JSON_PERSIST — real gap found, NOT built, do not assume it exists.** User referenced this as
"granted" (i.e. already agreed) — grepped the codebase to confirm before touching anything and found NO
such mechanism: `schedule_editor_ui.js` exports MSProject/PMXML/XER only, nothing JSON, no
export-on-lock hook anywhere. Whatever "the JSON, source of truth, exportable and reapplying when
imported" was agreed to be is not yet implemented. Next session picking this up needs the schema defined
first (does it reuse the `captured`-schedule import shape already in `schedule_author.js`'s
`activeSchedule()`, or is it new?) before writing any code — do not invent a shape.

## 2026-08-05 — §TM_PANEL_RESIZE_H (bottom-edge drag) + §GANTT_PALETTE staleness finding

User looked at a screenshot after the #1201/#1202/#1204 batch and asked for two things: (1) the drawer's
lower border pullable too, not just the right one, (2) per-discipline bar colouring, dark fills with
reversed-contrast labels.

**(1) Built — PR #1208, `feat/gantt-panel-bottom-resize`, auto-merge armed.** New
`#tm-panel-resize-grip-b` mirrors the existing right-edge width grip (`tm-panel-resize-grip`,
§TM_PANEL_RESIZE #1201): same pointer-capture pattern, `wirePanelResizeHeight()`, clamped 160px–85vh,
logs `§TM_PANEL_RESIZE_H height=...px`. Verified with `witness_tm_panel_resize_h.js` (12/12,
static-source check same convention as `witness_gantt_palette.js`) — confirms the grip exists, is
`ns-resize`, and critically that its grow-direction sign is the OPPOSITE of the internal top-strip
`wireGanttResize()` grip (that one sits above its content, `startY - e.clientY`; this one sits below,
so it must be `e.clientY - startY` or dragging down would shrink instead of grow — checked explicitly,
not assumed).

**(2) NOT built — because it's already done.** `PHASE_COLORS`/`PHASE_INK`/`PHASE_SHORT`
(`viewer/time_machine.js`, §GANTT_PALETTE, PR #1171, merged 2026-08-04) already give each of the six
phases a dark/light family colour with adaptive reversed-contrast ink — exactly this ask. The
screenshot's "MEP"/"Sup" bar labels are the fingerprint of the OLD pre-#1171 `phase.substring(0,3)`
fallback (confirmed: no phase string in `rates.js`/`schedule_author.js` is short enough to produce
"Sup" any other way — `substring(0,3)` of "Superstructure" is exactly "Sup"). Since #1201/#1202/#1204
(merged AFTER #1171, same day) tested fine per this same screenshot round, whatever build was open in
that tab was serving a stale cached `time_machine.js` for that one asset specifically — known landmine
class, see `sw.js`/cache-version feedback. **Action for next session if this comes up again: hard
refresh / confirm SW cache version, don't rebuild the palette — it already exists and is correct.**

Local `~/bim-ootb` checkout note: found main 5 commits stale (missing #1171/#1201/#1202/#1204) AND
carrying ~5000 lines of unrelated staged-but-uncommitted WIP (`schedule_diff.js`, `schedule_read_4d.js`,
a 766-line `time_machine.js` diff, several `witness_gantt_*.js` files, none of it this session's).
Left it completely untouched per the shared-tree caution in `CLAUDE.md` — did all work in a fresh
`git worktree add ... origin/main` instead (`/tmp/wt-gantt-bottom-resize`, safe to prune once #1208
merges and CI settles).

## 2026-08-06 — §GANTT_REFOLD_HANG — diagnosed + LIVE-confirmed, NOT fixed here (handoff, not this session's to implement)

User reported "Time Machine causes hanging... during schedule refreshing." Diagnosed fully in this
bim-compiler session; per `feedback_diagnose_in_session_fix_in_other_session.md` (cross-repo, and this
exact file already has ~5000 uncommitted lines of another session's WIP sitting in the local `~/bim-ootb`
checkout — see the entry directly above), **implementation was deliberately NOT done here.** This section
is the complete handoff a bim-ootb-side session needs to pick it up with zero rediscovery.

**Root cause — two synchronous, unyielding loops inside `injectGantt()` (`viewer/time_machine.js:3561-4278`),**
called by `refoldSchedule()`/`window.tmRefoldSchedule` (schedule refresh after an edit — line ~6670) and
`window.tmGenerateTimeline` (line ~7303):

1. **§OG_GRID_Z_BAND** (line 4218-4249) — already partially fixed 2026-08-05 (bim-ootb PR #1193, Terminal
   4636ms→~2900ms via XY→XYZ grid bucketing), but still fully synchronous. That PR's own commit message
   named the remaining fix and never did it: *"yielding to the browser between phases."*
2. **§WRITE_LOOP_TIMING** (line 4253-4264) — a per-element `_upd.run(...)` prepared-statement write over
   `_allScheduled`, explicitly flagged in its own in-code comment as an unfixed freeze cause on a
   63,415-element building (Hospital): *"a user report of the browser tab freezing traced to console
   output stopping right at this point... No fix here — just precise measurement."*

Both loops are strictly ORDER-DEPENDENT (each element's result can read an earlier element's just-mutated
`.s`/`.e`/`.end_ts` via shared object references in `_ogStructGrid`/`_ogWallGrid`) — cannot be parallelized
or reordered, but CAN be chunked with yields between batches (e.g. `setTimeout`/`requestAnimationFrame`
between N-sized slices) without changing output, since chunking preserves order.

**LIVE CONFIRMATION (2026-08-06, real console paste from the user, Hospital 63,415 elements, NOT
guessed):** the console stream stops dead immediately after
`§PHASE_OVERLAP_SUPPORT_GUARD pushed=1108/63415 elements later than their §PHASE_OVERLAP_BAND window...`
(the last log line of block 1 above, line 4250) — the very next code is block 2's write loop, whose own
completion log (`§WRITE_LOOP_TIMING rows=... ms=...`, line 4264) **never appears.** This pins the live hang
to the WRITE LOOP specifically, not the grid-scan (which completed and logged its result). Matches the
building (Hospital) and symptom named in the write-loop's own prior in-code comment exactly.

**Caller chain that needs to go async-aware once `injectGantt()` is chunked/promise-returning:**
- `_activateAsync()` line ~6450: `if (!injectGantt()) {...}` (inside a `.then()`, already promise-friendly)
- `_activateAsync()` line ~6471: `if (!_ops.length) { injectGantt(); _ops = loadOps(); ... }` (catch-path fallback)
- `window.tmGenerateTimeline = function() { return injectGantt(); };` (line ~7303, external API — check
  what actually calls this before changing its return type)

**Proposed fix (not yet built):** convert `injectGantt()`'s two hot loops to chunked execution yielding to
the browser between batches (size TBD by measurement, start with ~2000-5000 elements/tick); make
`injectGantt()` return a Promise; update the 3 call sites above accordingly; add a witness confirming (a)
byte-identical output to the current synchronous version on a real fixture (reuse
`witness_gantt_og_grid_perf.js`'s brace-balance-sliced-block technique) and (b) that yields actually happen
(count ticks, or measure that no single synchronous span exceeds a threshold, e.g. 200ms) — a wall-clock
ceiling alone (like `witness_gantt_og_grid_perf.js`'s existing 3500ms gate) does NOT prove non-blocking,
only proves fast; a future regression could stay under the ceiling while still blocking the thread for the
full duration in one synchronous span.

## 2026-08-06 — two more notes (triage/diagnosis only, NOT implemented, per user request)

**§TM_PANEL_RESIZE_H targeted the wrong box — ✅ FIXED, bim-ootb PR #1216 (auto-merge armed).**
`wirePanelResizeHeight()` (PR #1208) grew the OUTER `_panel` shell only (`style.maxHeight` +
`overflow-y:auto`). The actual Gantt canvas lives in the INNER `#tm-gantt-box`, which has its own separate
height cap (default 220px, only adjustable via the internal top-strip grip `tm-gantt-grip` → `_ganttBoxH`,
§GANTT_RESIZE E6) — so dragging the bottom edge grew the outer container but the content inside stayed
clipped at its old size. User confirmed this was the inner frame they meant. Fix: the bottom grip now
drives `#tm-gantt-box`/`_ganttBoxH` directly (the SAME variable/target `tm-gantt-grip` already owns) —
a second, more discoverable entry point to the one real resize, not a second mechanism; `_panel` needs no
style of its own since it's a flex column that naturally grows to fit the taller box.
`witness_tm_panel_resize_h.js` 16/16 — new checks (`G-PRH-13`..`16`) specifically assert the grip targets
`#tm-gantt-box`/`_ganttBoxH` and NOT `_panel`, so this exact regression class can't silently return.

**JSON schedule round-trip — two existing specs, stale, unreconciled with shipped reality or each other.**
`GANTT_ACCURACY.md §B` (~2026-05, DIY export/import) and `TM_SCHEDULE_EDITOR.md` (2026-07-07, refines §B into
the standard Settings JSON editor — `tm_schedule` DB row, `ProjectJson.load/save('schedule')`, a real
template/instance/permission model) both design schedule editing as a **JSON form/file**. Neither has ever
been referenced from this file, and everything actually shipped since (drag/resize/lock directly on the
Gantt canvas — `commitGanttDrag`/`moveTaskCascade`/real `task_sequences`) is a different UX paradigm for the
same goal. Building either spec as currently written would duplicate or orphan the live drag-edit UX.
**Still-good part worth keeping:** TM_SCHEDULE_EDITOR.md's mandate to unify the 3 overlapping rate/rule
sources (rates.js hardcoded / `rates/*.json` / bim-compiler's `4D_phases.json`) rather than adding a 4th —
matches this session's independent finding that rates.json is disconnected from Settings and from What-if.
**Before building either spec:** reconcile which mechanism "editable schedule JSON" is actually supposed to
be — (a) persist the current drag-edit model durably (small: wire `persistDb()` into Lock, no new UX), or
(b) build the JSON-form editor these specs describe as a parallel/alternate path. Nothing currently decides
between them. Not implemented here per user instruction (triage only).

## Session close-out, 2026-08-06

Three items this session, all in a bim-compiler-rooted session (implementation dispatched to fresh
`origin/main` worktrees per `feedback_diagnose_in_session_fix_in_other_session.md` where the target was
bim-ootb code):

- ✅ **§TM_PANEL_RESIZE_H DONE, twice** — shipped (bim-ootb PR #1208), then the user caught a real
  regression live (grew the outer `_panel` shell, not the inner `#tm-gantt-box` content), fixed same
  session (PR #1216, auto-merge armed). Witness `witness_tm_panel_resize_h.js` now 16/16 with checks
  that specifically guard the regression class (targets `#tm-gantt-box`/`_ganttBoxH`, not `_panel`).
- ⛔ **§GANTT_REFOLD_HANG — diagnosed + LIVE-confirmed, NOT implemented, needs a bim-ootb session.**
  Root cause: `injectGantt()`'s `§WRITE_LOOP_TIMING` write loop (`time_machine.js:4253-4264`), confirmed
  by a real user console paste (Hospital, 63,415 elements) — the log stream stops dead exactly where
  that block's own completion log should print. Full handoff (caller chain, chunking fix plan, why the
  loop can be chunked but not reordered) is in the dated section above. **Next session: pick this up
  directly, no rediscovery needed** — implement the chunked-yield fix, convert `injectGantt()` to
  return a Promise, update its 3 call sites (`_activateAsync` ×2, `window.tmGenerateTimeline`).
- ⛔ **JSON schedule round-trip — spec reconciliation gap named, NOT resolved.** `GANTT_ACCURACY.md §B`
  and `TM_SCHEDULE_EDITOR.md` both spec a JSON-form editor that was never squared against the drag-edit
  UX actually shipped. **Open question for whoever picks this up:** persist the current drag-edit model
  durably (small — wire `persistDb()` into Lock) vs. build the JSON-form editor as a parallel path (big
  — the full `TM_SCHEDULE_EDITOR.md` plan). Not decided; don't build either without deciding first.

**For a new dev landing on this file cold:** read `## Why this file exists` + `## What's already shipped`
at the top for original scope, then jump straight to this close-out and the two dated 2026-08-06 sections
above it for the live edge — the numbered gap list right after the top summary predates all of the
2026-08-04 through 2026-08-06 sessions below it and is mostly still open, not superseded.

