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
