# ⚠ DO NOT REMOVE — Witness Interface Framework (SPEC ONLY, nothing built yet)
# SCOPE: design for a REUSABLE witness-authoring library — not an audit of existing witnesses
#   (that's `WITNESS_CONTRACT_AUDIT.md`, a different, backward-looking deliverable; this one is
#   forward-looking: how a NEW witness gets written from here on, so authoring one doesn't mean
#   hand-rolling assertion bookkeeping every time). Triggered by the user's own sharper question
#   (2026-08-25): "does any WITNESS confirm the actual JSON/DB output of truth the 4D schedule
#   runs on — not just some derived number?" Answer, checked against every file the audit read:
#   NO, not comprehensively. That gap is what this framework closes.
# STATUS: spec + a worked sketch only. No code in `bim-ootb` yet. Per this project's Spec-First rule
#   (CLAUDE.md), nothing here gets implemented until this spec is reviewed and agreed.

---

## 0. The question that motivated this, stated precisely
Everything `WITNESS_CONTRACT_AUDIT.md` read today checks a *derived* property — a span, an inversion
count, a completion instant. **Nothing checks the actual persisted output-of-truth artifact — the
`tasks` table rows, the thing a user edits and the thing that survives reload — as one declared
contract.** The closest today (`witness_gantt_edit_persist.js`, `witness_bake_plays_schedule.js`) each
check one adjacent property, not the artifact's own shape and invariants. That's a coverage gap, not a
rigor gap — a different axis from everything the audit measured.

## 1. Why "simulate a Java interface" is the wrong translation
Java's real advantage isn't the `interface` keyword — it's that the **compiler refuses to let a
producer emit a malformed shape at all.** JS has no equivalent at the language level. Trying to fake an
`interface` with duck-typing or JSDoc buys nothing a runtime doesn't already give you for free. The
honest translation: **push the contract into a shared runtime library that refuses to call anything
green without the required pieces** — the same guarantee, enforced by one function every witness is
forced to go through, instead of by the compiler.

## 2. Three pieces

### 2.1 Schema layer — validates the REAL artifact's shape
Use **Ajv** (already vendored in `bim-ootb/node_modules/ajv` as an eslint transitive dependency — add it
as an explicit `devDependency`, zero new install needed). Not hand-rolled: a hand-rolled shape check is
exactly the kind of thing that silently rots, which is half of what today's audit found.

A schema is data, not code — versionable, diffable, and it's the thing that actually answers "does the
JSON settings match" for any given artifact, because it's checked against the REAL persisted row, not a
function's return value.

### 2.2 Invariants — reusable domain predicates, written once
The #1 rot source `WITNESS_CONTRACT_AUDIT.md` found repeatedly: a witness hand-mirrors a predicate from
the real gate (`geo_support_leak.js`'s 3rd-generation drift, `hosted_before_host.js`'s missing
`CW_HOST_CLS` pool, `even_turn.js`/`noise_law.js`'s duplicated `PACE_SWING` constant — 10+ instances
today alone). A shared `invariants/` library, imported by name, means the predicate exists in exactly
ONE place — drift becomes impossible by construction instead of caught by luck.

### 2.3 The builder — the actual "interface," enforced at the only call site that matters
```js
function Witness(name) {
  const spec = { name, _population: null, _schema: null, _invariants: [], _redControl: null };
  const api = {
    population(fn)      { spec._population = fn; return api; },
    schema(s)            { spec._schema = s; return api; },
    invariant(label, fn) { spec._invariants.push({ label, fn }); return api; },
    redControl(fn)       { spec._redControl = fn; return api; },
    run() {
      if (!spec._population) throw new Error(`Witness(${name}): .population() is required`);
      if (!spec._schema)     throw new Error(`Witness(${name}): .schema() is required`);
      if (!spec._redControl) throw new Error(
        `Witness(${name}): .redControl() is required — a witness that cannot fail is not a witness`);
      // 1. run population() -> rows. THROW (not silent-pass) if rows.length === 0 — closes
      //    §W-EMPTY-POP, the single most common defect found today (12+ instances), at the source.
      // 2. validate every row against schema via ajv -- structural drift now fails LOUDLY, not
      //    silently, the moment a producer's shape changes (closes §W-STALE-SLICE's failure mode
      //    for the OUTPUT side, not just the source-text-slicing side).
      // 3. run every invariant against the population, tally pass/fail.
      // 4. run redControl() and assert it DOES fail against schema+invariants -- if it doesn't,
      //    the witness itself is the defect (closes §W-REDCONTROL at the source, not by author
      //    discipline).
      // 5. print one line: §WITNESS_<NAME> pass=X fail=Y ran=<rows.length> -- ran>0 is baked in,
      //    can't be omitted the way 12+ files omitted it today.
    }
  };
  return api;
}
```
Omit `.redControl()` and the witness refuses to even register — not a lint warning, not a convention in
a comment, a thrown error at author time. That is the actual JS analog of a Java interface refusing to
compile an incomplete implementer: enforcement moved from "did the author remember" to "does the shared
code path allow it to run at all."

## 3. Worked sketch — the exact case that motivated this: TM's real schedule output-of-truth

Real shape, not invented — `viewer/schedule_author.js:247`'s actual `CREATE TABLE tasks` columns:
`task_id, schedule_id, wbs_parent, name, predefined_type, is_summary, schedule_start, schedule_finish,
schedule_duration, early_start, early_finish, late_start, late_finish, free_float, total_float,
is_critical, resource, status`.

```js
// witness_kit/schemas/schedule_4d.js — the contract, as data
const Schedule4DTaskRow = {
  type: 'object',
  required: ['task_id', 'schedule_id', 'schedule_start', 'schedule_finish', 'is_critical'],
  properties: {
    task_id:          { type: 'string', minLength: 1 },
    schedule_id:      { type: 'string', minLength: 1 },
    schedule_start:   { type: 'string', format: 'date' },
    schedule_finish:  { type: 'string', format: 'date' },
    is_critical:      { type: 'integer', enum: [0, 1] },
    total_float:      { type: ['string', 'null'] }
  },
  additionalProperties: true   // a floor, not a ceiling — legacy/extra columns don't fail the row
};

// witness_kit/invariants/schedule.js — reusable, imported by name, never hand-copied again
const datesOrdered   = row  => new Date(row.schedule_start) <= new Date(row.schedule_finish);
const noPre1970Dates = row  => new Date(row.schedule_start).getFullYear() > 1971;
// ^ this is not hypothetical — it's 4D_GANTT_TM_REFACTOR.md's own real, already-shipped defect
//   (§S67-era "1970-date typed edits"). Encoded here, it can never silently reappear in ANY
//   future witness that imports this invariant — the fix becomes a standing gate, not a memory.
const criticalFloatZero = rows =>
  rows.filter(r => r.is_critical === 1).every(r => Math.abs(Number(r.total_float || 0)) < 1e-6);

// viewer/tests/witness_tm_schedule_output_of_truth.js — the actual new witness. This is the WHOLE file.
const { Witness } = require('../../witness_kit/contract');
const { Schedule4DTaskRow } = require('../../witness_kit/schemas/schedule_4d');
const { datesOrdered, noPre1970Dates, criticalFloatZero } = require('../../witness_kit/invariants/schedule');

Witness('tm_schedule_output_of_truth')
  .population(() => readRealTasksTable('buildings/Duplex_extracted.db'))  // the REAL persisted rows
  .schema(Schedule4DTaskRow)
  .invariant('dates-ordered',        rows => rows.every(datesOrdered))
  .invariant('no-1970-dates',        rows => rows.every(noPre1970Dates))
  .invariant('critical-float-zero',  criticalFloatZero)
  .redControl(rows => { rows[0].schedule_start = '1970-01-05'; return rows; })
  .run();
```

**What this closes, concretely:**
- Answers the user's original question directly — this witness DOES confirm the real JSON/DB output of
  truth, not a derived number three steps removed from it.
- The whole authored file is ~10 lines. Everything else (population guard, schema check, red-control
  enforcement, pass/fail tally, the `ran>0` discipline) lives in the shared library — a new feature adds
  a schema + a couple of invariants, not a bespoke assertion script.
- `no-1970-dates` demonstrates the actual point of a shared invariant library: a REAL defect this
  project already paid for once becomes a permanent, reusable, un-forgettable gate instead of tribal
  memory in a prompts file.

## 4. What this spec does NOT claim
- Not a replacement for `WITNESS_CONTRACT_AUDIT.md`'s ~270 already-written witnesses — those stay as
  they are unless individually triaged; this is the pattern for what gets written from NOW on.
- Not free of authoring effort — someone still has to pick the right schema, the right invariants, and a
  real red control. The framework removes the BOILERPLATE and the SILENT-OMISSION failure modes, not the
  judgment.
- Ajv + this builder shape is a proposal, not a locked decision — flag now if either should be different
  before any of this is built.

## 5. §5 decided and BUILT — 2026-08-25, bim-ootb PR #1511
1. **Built now.** Framework + the `Schedule4D` case shipped as the first real witness, not parked.
2. **Top-level `bim-ootb/witness_kit/`**, shared by `viewer/tests/` and `modeller/tests/`.
3. **Forward-only.** No existing witness migrated — kept scope to the one new coverage gap this spec
   named; migrating a working witness is a separate, deliberate task, not bundled here.

### What shipped (`bim-ootb` branch `feat/witness-kit`, PR #1511)
- `witness_kit/contract.js` — the real `Witness()` builder (§2.3 was pseudocode; this is the working
  implementation — throws on a missing required piece, validates every row via Ajv, tallies
  pass/fail, and *proves* `.redControl()` actually breaks the population before trusting it, not just
  running it).
- `witness_kit/schemas/schedule_4d.js`, `witness_kit/invariants/schedule.js` — as sketched in §3, with
  one correction below.
- `viewer/tests/witness_tm_schedule_output_of_truth.js` — the first framework-authored witness.
  Auto-discovered and green under `node tests/run_witness_suite.js --filter tm_schedule_output_of_truth`.

### One real finding that changed the sketch: no static fixture has a populated `tasks` table
§3's sketch assumed `readRealTasksTable('buildings/Duplex_extracted.db')` would just read real rows.
It doesn't — checked all 21 `buildings/*.db` + `modeller/*_meta.db` on disk (2026-08-25): every one is
either the legacy-thin schema with 0 rows, or has no `tasks` table at all. The real table only exists
at runtime, built by the generative fallback and persisted to IndexedDB — never written back to any
on-disk fixture. So `population()` instead DRIVES the real production generator —
`schedule_author.js`'s `materializeDefault()` → `scheduleContiguous()` → `computeCpm()`, the same
calls `time_machine.js` makes — against `Duplex_extracted.db`'s real 1193-row `elements_meta` and
`rates.js`'s real `SEQUENCE_RULES`/`LABOR_RATES` (loaded via `vm.createContext`, the same pattern
`witness_gantt_bars_in_rect.js` already uses for a browser-global script with no module boundary).
Result: 7 real generated tasks, real 2026 dates, real CPM floats — nothing fabricated.

That run also surfaced a second correction: a real post-CPM row has `is_critical`/`total_float` as
`null` on the WBS-summary rollup row (`is_summary=1` — `computeCpm` only rates leaf tasks). §3's schema
required `is_critical` as non-null `enum:[0,1]`; the shipped schema widens both fields to allow `null`,
verified against the real generated output, not assumed.

### Self-test (the framework proving itself, not just the one witness)
Confirmed directly: `.run()` throws if `.redControl()`/`.schema()`/`.population()` is omitted; a
no-op `.redControl()` (one that doesn't actually break anything) is caught as `fail>0`, not silently
green; an empty population is caught the same way. The contract enforces itself, not just decorates.

## 6. Follow-on — 2026-08-25, bim-ootb PR #1513: multi-building black-box sweep (incl. HHS Office)
User ask: test the witness on a real HHS Office DB, as a **separate, reusable script**, not a one-off
inline check — checked first whether an equivalent multi-building test already existed.
`witness_support_invariant_all_buildings.js` is the one real precedent in this codebase, but it checks
a different, ephemeral thing (`ScheduleGate.computeSchedule`'s support invariant, never persisted) —
nothing already swept the real *persisted* `tasks` table across more than one building. Built:
- `witness_kit/generators/schedule_4d.js` — the §5 generation call (materialize→contiguous→CPM),
  factored out so the single-building witness and the new multi-building one share one source instead
  of a hand-copy (exactly the drift class §2.2 exists to prevent).
- `viewer/tests/witness_tm_schedule_output_of_truth_all_buildings.js` — same contract, looped over
  **Duplex, Hospital, Clinic, JKR, HHS_Office_Federated**. All 5 green (HHS Office: 6 real generated
  tasks, real 2026 CPM dates). `Terminal`/`LTU_AHouse` named-SKIPPED — both ship split meta/geo DBs
  with no `tasks`/`schedules` tables in either half; real follow-on scope, not attempted here.

**Real bug found in the shared runner while proving this "reusable," not just runnable standalone:**
`run_witness_suite.js`'s `spawnSync` uses Node's default 1MB stdout+stderr `maxBuffer`. Looping 5
buildings' worth of `schedule_author.js`'s own diagnostics (`§CLASS_UNMATCHED` is one `console.warn`
line per unmatched element — Hospital's 64k `elements_meta` rows alone produced 1MB+ on stderr)
overflowed it: the runner SIGTERM'd the child (`status: null`, reported RED) even though the script
exits 0 standalone. A witness that only passes when run directly and fails under the real suite runner
is exactly the kind of gap this whole framework exists to catch — caught here by actually running it
through `run_witness_suite.js --filter`, not trusting the standalone exit code alone. Fixed by muting
`console.log`/`warn`/`error` locally around each building's generation call (not the shared generator,
not the runner itself). Both witness files also adopted the house `BLD_DIR` env convention
(`witness_door_window_host_wall.js` etc.) instead of a hardcoded path.
