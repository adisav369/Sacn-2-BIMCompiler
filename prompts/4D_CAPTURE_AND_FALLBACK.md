# ⚠ DO NOT REMOVE
**Scope:** Capture native IFC 4D (work schedule, tasks, dates, dependencies, task→element
links) in the **Drop-IFC browser importer**, feed it to the **Time Machine** timeline, and
**fall back per-element** to the generative default flow for any element the real schedule
does not cover. Build a **region-agnostic common schedule template** that drives the fallback
for every building, with or without native IFC 4D.
**Prime rule:** EXTRACT OR COMPILE ONLY. When the IFC carries a real schedule, **use it
verbatim** — never re-derive dates we were handed. Generate only where the IFC is silent.
**Log mandate:** Save every run's output to a log file and READ the log before any conclusion.
Honour this block until every Witness below is GREEN.

---

## 0. Why this prompt exists — the Hospital 2.0 finding

`~/Downloads/Hospital 2.0.ifc` (IFC4, Revit→Bonsai/IfcOpenShell 0.8.5, 236 MB) carries a
**complete, human-authored construction programme**:

| Entity | Count | What it encodes |
|---|---|---|
| `IfcWorkPlan` "Construction Programme" | 1 | top container |
| `IfcWorkSchedule` "Baseline Schedule" (PLANNED) | 1 | the baseline |
| `IfcWorkCalendar` "9-5" | 1 | working time |
| `IfcTask` (`.CONSTRUCTION.`) | 75 | WBS: Structures▸Piles▸Zone A/B/C, Pile Caps, Foundation Slab… |
| `IfcTaskTime` | 46 | ISO-8601 duration (P5D,P15D) + planned/actual start-finish + total/free float |
| `IfcRelSequence` `FINISH_START` | 43 | task dependencies |
| `IfcRelNests` | 23 | WBS hierarchy (parent task ▸ child tasks) |
| `IfcRelAssignsToProduct` | **2900** | element ⇄ task links |

**The author is skilled** — see §1. **Our pipeline currently throws all of it away** (§2).

### Author skill assessment (the question that started this)
Whoever built this is **not** a casual modeller. Evidence and the expertise it implies:

- **Used Bonsai (BlenderBIM) Sequence tool deliberately**, not just exported geometry from
  Revit. The CoordinationView export is Revit; the 4D was *added afterward* in Bonsai. That is
  a two-tool, IFC-native workflow — uncommon outside dedicated BIM coordinators.
- **WBS + Location Breakdown together**: tasks nest `Structures ▸ Piles ▸ {Zone A, Zone B,
  Zone C}`. Splitting one trade across spatial zones is **planner thinking** (line-of-balance /
  takt), not modeller thinking. This is how a *construction planner / 4D BIM coordinator* works.
- **Baseline discipline**: schedule is named "Baseline Schedule", status `.PLANNED.`, and
  `IfcTaskTime` carries *both* planned **and actual** dates plus **total & free float**
  (`P0D`/`P160D`). Tracking baseline-vs-actual + float = **critical-path / Primavera-P6 or
  MS-Project literacy**, mapped correctly onto IFC. Most people never populate float.
- **Correct semantic linking**: 2900 `IfcRelAssignsToProduct` tie real element GUIDs to tasks —
  they connected the *model* to the *programme*, the whole point of 4D, and the step most people
  skip.
- **ISO-8601 durations** (`P15D`) and `FINISH_START` with lag fields used properly.

**Likely an expert in:** construction planning / project controls (P6/MS-Project) **plus**
openBIM authoring (IFC4 schema, Bonsai/IfcOpenShell). i.e. a **4D BIM coordinator** who
understands both the schedule side and the IFC data model — a rarer combination than either
skill alone. The float + baseline-vs-actual is the tell that the scheduling depth is real.

---

## 1. The principles the author encoded (what we must honour)

1. **Named WBS tasks**, not class buckets — "Pile Caps", "Foundation Slab", "Zone A".
2. **Location Breakdown** — one trade split across spatial zones.
3. **Real calendar dates** + ISO-8601 durations on a **work calendar**.
4. **Planned vs Actual + float** — baseline tracking, critical path.
5. **Explicit dependencies** (`FINISH_START` + lag), not inferred ordering.
6. **Per-element GUID assignment** — every task points at the geometry it builds.

---

## 2. Current state (grounded in code, 2026-05-30)

| Stage | File | 4D today |
|---|---|---|
| Backend extractor | `scripts/extractIFC2DB.js:448-585` | Captures schedules/tasks/sequences ✔, **but** reads task→element via `IfcRelAssignsToProcess` (0 in this file) → `task_elements` **empty**. |
| **Drop-IFC importer** | `deploy/dev/import_db_builder.js:26-70` | Creates 6 tables, **none of them 4D**. Captures **nothing**. |
| **Time Machine** | `deploy/dev/time_machine.js:2261-2339` | Reads **no** schedule table. Generates order from `elements_meta`+`element_transforms` **Z-bands × class rules**. Real IFC dates **never read**. |
| Common template | `templates/4D_phases.json` | Class→phase→seq→predecessor→resource→productivity. **Generative only**; cannot *capture* dates/zones/float/named-tasks (§5). MY/CIDB-specific calendar. |

**Gap:** when an IFC carries a real programme, we ignore it and invent a synthetic one — a
direct PRIME-RULE violation (invent where we could extract).

### 2.1 T1 DONE — and the capture is LOSSY (2026-05-30)
T1 is implemented + witnessed on the real file (`§4D_FOUND schedules=1 tasks=75 sequences=43
taskElements=2900`, `§4D_DB_ROWS task_elements=2900`; the dual-direction `IfcRelAssignsToProduct`
loop is what yields the 2900 — `IfcRelAssignsToProcess`=0). **But the DDL we copied drops half the
expert signal.** Measured against the §1 principles:

| §1 principle | In the IFC (W-VOCAB measured) | Kept by the T1 `tasks`/`task_*` tables? |
|---|---|---|
| #1 Named WBS **hierarchy** (`IfcRelNests`=23, depth 4) | yes | **NO** — flattened, no `wbs_parent` |
| #4 **CPM early/late dates** | 45/46 | **NO** — dropped |
| #4 **Float** (free/total) + **IsCritical** | 44–45/46 | **NO** — dropped |
| #3 **Work calendar** (WEEKLY "9-5") | `IfcWorkCalendar`=1 | **NO** — not captured |
| planned start/finish/duration, deps, element links | 46/46 | yes ✔ |
| ~~Actual dates / % complete~~ | **0/46 — absent** | n/a (nothing to capture; PLANNED baseline) |

So T1 proved we *can* read the full programme, but the schema then discards the CPM/float/WBS/
calendar that made it expert-grade. **The schema must be widened (T1b) — see §5.2 (now W-VOCAB-backed)**
— and this is why we design the standard JSON *from the captured evidence*, not from intuition.

### 2.2 Corpus scan — Hospital is n=1 (2026-05-30)
Scanned 166 extracted DBs. Every DB that has the 4D tables has them **empty (0 rows)** — all were
extracted before 4D capture worked. **No native 4D exists anywhere except `Hospital 2.0.ifc`.**
Consequences: (a) the corpus that teaches the schema is a **single** building — learn its *shape and
rule-kinds*, NOT its specific values (copying its productivity numbers would overfit one model);
(b) virtually every other building will hit the **template/fallback** path and never native capture,
so the template's design (§5.1) is the high-leverage decision, not an afterthought.

---

## 3. Target design — capture-first, **per-element fallback**

> **User directive (2026-05-30):** *"if the 4D is insufficient / does not cover all, then the
> rest fallbacks on default flow."* This is the spine of the design.

### 3.1 Schedule source resolution — per element, in priority order
For **each** renderable element GUID:

1. **CAPTURED** — GUID ∈ `task_elements` → use its task's real `ScheduleStart`/`ScheduleFinish`
   (fall back to actual dates, then `ScheduleDuration`). Absolute calendar dates. Never re-derive.
2. **GENERATED** — GUID not covered → compute via the default Z-band × class-rule flow
   (`templates/4D_schedule_common.json`, §5), then **anchor** that synthetic order onto the real
   calendar window (§3.3).

So a model with 2900 covered + N uncovered elements yields **one** timeline: real dates where we
have them, generated-but-anchored ordering everywhere else. No element is dropped.

### 3.2 Coverage reporting (no silent gaps — Standing Rule)
After resolution, emit:
```
§4D_COVERAGE captured=<n> generated=<n> total=<n> pct=<captured/total*100>
§4D_SOURCE schedule="<IfcWorkSchedule.Name>" baseline=<bool> tasks=<n> linked_elements=<n>
```
If `captured=0` → log `§4D_COVERAGE generated-only (no native 4D)` and run pure default flow.

### 3.3 Anchoring generated elements onto a real calendar
When `captured>0`, the real schedule defines `[project_start, project_finish]`.
- Generated elements inherit the **date window of the nearest covering real task** by
  (storey-band, phase). If no covering task exists for that band/phase, slot them
  **proportionally** across `[project_start, project_finish]` by their Z-band × seq rank.
- When `captured=0`, anchor at `calendar.project_start` from the template and lay the generated
  sequence forward by productivity-derived durations (today's behaviour, unchanged).

Witness `W-ANCHOR`: a generated wall on storey L2 starts **no earlier** than the captured L1
structure tasks it sits above. Prove with a `§` line, not by eyeballing.

---

## 4. Work items (spec-first; each has a Witness; no code before its spec line is cited)

### T1 — Drop-IFC importer captures native 4D  `deploy/dev/import.js` + `import_db_builder.js`
- Add the 4 tables (`schedules, tasks, task_sequences, task_elements`) — **same DDL as
  `extractIFC2DB.js:538-541`** so backend and browser DBs are identical (Single-DB standard).
- Walk `IFCWORKSCHEDULE, IFCTASK(+IFCTASKTIME), IFCRELSEQUENCE`.
- **Task→element link reads BOTH directions** (this is the Hospital fix):
  - `IfcRelAssignsToProcess`: RelatingProcess = task, RelatedObjects = elements.
  - `IfcRelAssignsToProduct`: **RelatingProduct = element, RelatedObjects = tasks** (Bonsai).
  - Merge both into `task_elements(task_id, guid)`.
- **W-CAPTURE:** Drop `Hospital 2.0.ifc` → `§4D_FOUND schedules=1 tasks=75 sequences=43
  taskElements≈2900`. (Today the importer prints nothing — `taskElements` MUST be non-zero.)

### T2 — Backend extractor: same dual-direction fix  `scripts/extractIFC2DB.js:500-517`
- Add the `IfcRelAssignsToProduct` loop (element=RelatingProduct, tasks=RelatedObjects).
- **W-BACKEND:** re-extract Hospital → `§4D_FOUND … taskElements≈2900` (was 0).

### T3 — Time Machine consumes captured schedule first  `deploy/dev/time_machine.js`
- Before the Z-band query (`:2261`), probe for a non-empty `tasks` table.
- If present: build `_ganttTasks` from real tasks (real names, real dates, `task_sequences`
  for arrows), then resolve element timing per §3.1 and anchor uncovered per §3.3.
- If absent/empty: today's pure generative path, unchanged.
- Real `IfcTask` **names** ("Pile Caps", "Zone A") appear in the mini-Gantt instead of phase
  buckets. `task_sequences` draws real dependency arrows.
- **W-TM-REAL:** Hospital playback logs `§GANTT_SOURCE captured tasks=75` and the panel shows
  task names "Zone A/B/C". **W-TM-FALLBACK:** a building with no 4D logs `§GANTT_SOURCE
  generated` and behaves exactly as before (regression guard).

### T4 — Common fallback template  `templates/4D_schedule_common.json`  (§5; ship in this prompt)
- Region-agnostic, generative, **shaped to merge with captured tasks**.
- **W-TEMPLATE:** any building with no native 4D produces a credible zoned phase schedule from
  this template alone; `§4D_COVERAGE generated-only` then a full Gantt renders.

### Test discipline
- §-log first, Playwright second (CLAUDE.md). Every Witness = a `§` line on real data
  (`Hospital 2.0.ifc` for capture, an existing no-4D building for fallback), in the deployed
  state. Run `node deploy/dev/tests/audit_specs.js` (exit 0) if any spec changes.
- Whitebox suite before any deploy.

---

## 5. The common template — `templates/4D_schedule_common.json`

**Reviewing `4D_phases.json` against §1 principles:** it satisfies the generative half — it has phases,
sequence, class-predecessors, resource, productivity (the *generative* principles). It **cannot**
express #1 named WBS tasks, #2 zones, #3 real dates, #4 float/baseline, #5 explicit per-task
deps, #6 per-element GUIDs (the *capture* principles). It is also **CIDB/Malaysia-specific**
(Mon–Sat). Verdict: keep it as the legacy nD-engine input, but the **canonical fallback** is the
new `4D_schedule_common.json`, which (a) is region-neutral, (b) adds a **zoning strategy** so the
generated schedule mimics the author's Zone A/B/C breakdown, (c) carries a **calendar anchor**,
and (d) declares **capture priority** so the consumer always prefers native IFC 4D.

The file is created alongside this prompt. Consumers (Time Machine §3, nD engine) read
`source_priority: ["ifc_native", "template_generated"]` and only fall to the template per-element
where IFC 4D is missing (§3.1).

### 5.1 Three artifacts — Template (DNA) ▸ Instance (organism) ▸ Generator (the expansion) (2026-05-30)
The schedule is not one JSON. It is a **shared compact rule-set** that the TM **instantiates per
building** into a verbose, editable result. Keep these three distinct:

| | **Template** (the standard) | **Instance** (per building) | **Generator** |
|---|---|---|---|
| Role | the **DNA** — compact, general **rules** | the **organism** — expanded **tasks** | the **expansion function** |
| Shape | class *families* + **recursive/repetitive rules** (zone-after-zone, per-storey cycle) + `_default` + sparse exceptions — **NOT** every item enumerated | fully-enumerated `tasks[]` with rich fields (name, dates, `wbs_parent`, baseline/actual, float, critical, resource) | reads rules → expands recursion (storeys × zones × phase-cycle) → emits `tasks[]` |
| File / source | `4D_schedule_common.json`, `source:'url'` | `tm_schedule` DB row, `source:'db'`, `id:'schedule'` | TM one-time generator (§3) |
| Editable by | **Admin only — locked** (later feature) | **User**, via the Settings chooser | n/a (code) |
| Count | one, shared | one per building | n/a |

**The asymmetry is the point** (user directive 2026-05-30): the template is *summed* — rules are
recursive/repetitive, applied dynamically, "instead of every item defined," because most buildings
won't need every rule. The **instance is the expanded output** and the **only thing the user edits**.
Therefore the current `ifc_class_rules` (43 explicit entries) are the **wrong shape for a template** —
that verbosity belongs in the *instance*. The template shrinks to: phases, class-*family* mappings,
a zoning/repetition rule, calendar, `_default`, sparse overrides.

Generation (the "generate-once, freeze" model, §3): on first TM open, if the DB carries captured
tasks → copy them **verbatim** (never re-derive) and only *expand the template* for uncovered
elements; if no captured tasks → expand the template fully (synthesize phase×zone tasks). Write the
result to `tm_schedule`, freeze. Edits mutate the instance; a `Regenerate` action is the only way to
re-expand (and it warns it discards edits).

### 5.2 T1b — widen the capture + instance schema (W-VOCAB DONE, 2026-05-30 — evidence, not intuition)
`deploy/dev/tests/test_4d_vocab.js` → `4d_vocab.log` dumped the full field set the expert encoded.
**Populate-rates over the 46 `IfcTaskTime` (the evidence that decides the schema):**

| field | populated | keep? |
|---|---|---|
| `ScheduleStart` / `ScheduleFinish` / `ScheduleDuration` | **46/46** | ✔ (T1 already keeps) |
| `EarlyStart` / `EarlyFinish` / `LateStart` / `LateFinish` | **45/46** | ✔ ADD — real CPM dates |
| `FreeFloat` / `TotalFloat` | **44/46** | ✔ ADD |
| `IsCritical` | **45/46** | ✔ ADD |
| `ActualStart` / `ActualFinish` / `ActualDuration` / `StatusTime` / `Completion` | **0/46** | ✗ DROP — **no actuals in corpus** |

**Correction to my earlier §1/§2 assumption:** this is a **PLANNED baseline with full CPM** (early/late
+ float + critical), NOT a baseline-vs-actual tracker — actuals are entirely empty. So capture
**early/late + float + is_critical**, and do *not* add actual_* columns (nothing to extract; add later
only if a future model carries them).

Other evidence:
- **WBS** (`IfcRelNests`=23): 3 roots, **maxDepth=4**, 72/75 tasks have a parent → add `wbs_parent`.
  Summary-vs-leaf is **NOT** reliable from `PredefinedType` (56 NOTDEFINED, 12 CONSTRUCTION, 6
  INSTALLATION, 1 REMOVAL, yet 46 have TaskTime) — classify a task as **summary = has children**
  (in `IfcRelNests`); leaf = schedulable (`has TaskTime`). Add `is_summary` + `predefined_type`.
- **Calendar** (`IfcWorkCalendar`=1): one WorkTime "9 - 5", `RecurrencePattern` = **WEEKLY**;
  StartTime/FinishTime are null (hours live in the name, days in the WeekdayComponent). Capture a
  thin `calendars` carrier {name, recurrence_type, raw}; do NOT invent hours_per_day from "9-5".
- **Sequences** (43): all `FINISH_START`, **lag=0/43** → keep `lag_days` column (default 0), unused here.
- **Resource: resources=0, relAssignsToControl=1** → **`resource` is NOT in the IFC** — it is OUR
  template concept. So `resource` on a captured task is **null**; it is supplied by the template/
  generator only. (Confirms 5D rates must be template-derived, never captured — §6.)

**Resulting widened `tasks` schema (capture DDL + instance `tasks[]`):**
`task_id, schedule_id, wbs_parent, name, predefined_type, is_summary, schedule_start, schedule_finish,
schedule_duration, early_start, early_finish, late_start, late_finish, free_float, total_float,
is_critical, resource(nullable), status`. Plus a `calendars(name, recurrence_type, raw)` table.
Witness `W-VOCAB` GREEN — schema is now evidence-backed.

**T1b DONE + re-witnessed (2026-05-30).** Widened DDL applied to the **capture path** (browser
importer): `deploy/dev/import_worker.js` (WBS `IfcRelNests` walk → `wbs_parent`+`is_summary`;
widened `IfcTaskTime` read keeping early/late/float/critical **verbatim**; `IfcWorkCalendar` →
thin `calendars` carrier) + `deploy/dev/import_db_builder.js` (18-col `tasks` DDL + `calendars`
table). Re-witnessed on `Hospital 2.0.ifc` via `deploy/dev/tests/test_4d_capture.js` →
`deploy/dev/tests/4d_capture.log`:
```
§4D_WIDE      earlyStart=45 totalFloat=44 isCritical=45 wbsParent=72 summary=23 calendars=1
§4D_DB_ROWS   schedules=1 tasks=75 task_sequences=43 task_elements=2900 calendars=1
§4D_DB_FIELDS schedule_duration=46 early_start=45 total_float=44 is_critical=45 wbs_parent=72 is_summary=23
```
Every count matches the W-VOCAB populate-rates; `task_elements=2900` unchanged (no regression).
Two findings: (1) the T1 `parseFloat("P15D")` silently nulled **all** durations — fixed by storing
raw ISO (`schedule_duration=46`); (2) `is_summary` = *has children* = **23** (one `IfcRelNests`
per parent), which is the §5.2 rule — the "29" in §5.3(d) is *tasks-without-TaskTime* (75−46), a
different metric. **Still pending: T2** (backend `extractIFC2DB.js` needs the SAME widening to hold
the Single-DB standard) then the §5.1 template-shape revision.

### 5.3 Planning conventions to encode — generator rules + fallback + what to tell users (2026-05-30)
§5.1/§5.2 fixed the *artifacts* and the *schema*. This fixes the **planner's logic** — the rules the
generator applies and the fallback obeys. Every rule below is measured from `Hospital 2.0.ifc`
(W-WBS / coverage scan, §2.2 + this session), not assumed.

**(a) The recursive WBS is ONE cycle, not 75 tasks** (W-WBS, measured `IfcRelNests`):
```
Phase ▸ Storey(Level 1..7) ▸ Trade(Columns · Structural Framing · Floor Slab) ▸ Zone(A·B·C)
        + non-repeating prefix:  Site Works▸Excavation ; Structures▸Piles/Pile Caps/Foundation (zone-split)
```
→ Template holds ONE `storey_cycle {trades:[…], split_by:'zone', zones:['A','B','C'], zone_seq:'FS'}`
+ a short `prefix_phases` list. The **generator multiplies** storeys × trades × zones into the
instance `tasks[]` (§5.1 asymmetry). **Never enumerate the expanded tasks in the template.**

**(b) Sequencing — all FINISH_START** (evidence: 43/43 FS, lag=0; dates ascend Level 1→7; Zone A→B→C):
1. Vertical: lower storey before upper (median-Z — TM already does this).
2. Within storey: trade order by `sequence` (Columns→Framing→Slab; fallback then continues
   Enclosure→MEP→Finishes).
3. Within trade: Zone A→B→C, FINISH_START (takt / line-of-balance).
4. Cross-storey: a storey's *structure* gates the next storey's structure; non-structural may overlap.

**(c) Calendar = WORKING days, not calendar days** (evidence: WEEKLY WorkCalendar; `P5D`=5 working
days): generator converts duration→dates through the calendar (skip non-working days + holidays).
Treat "9-5" as opaque (§5.2) — do not invent hours_per_day.

**(d) Rollup + critical path are DERIVED** (evidence: 29 summary tasks carry no TaskTime; is_critical
45/46): summary date = min(child start)…max(child finish). For **generated** items, critical =
longest-path through the dependency DAG; **never invent float** — compute it or leave null. Captured
items keep their native float/critical verbatim.

**(e) Bookend phases beyond the frame** (evidence: "Site Excavation" task; `REMOVAL` type ×1): the
template phase list must span more than Substructure→Finishes — add **Site Works/Enabling (seq 0)**,
optional **Demolition (REMOVAL, seq 0.5)**, and **Commissioning/Handover (seq 12)**.

**(f) FALLBACK rules — so generated blends with captured** (the n=1, ~86%-uncovered reality, §2.2):
1. **GRAFT, don't parallel** — hang each generated task UNDER the captured WBS node for its storey
   (generated "Level 3 — MEP Rough-in" → parent = captured "Level 3" `task_id`). If a captured header
   is empty (the real **"Architecture" root has no children**), **populate it**, don't duplicate.
2. **DETECT captured axes** — if captured uses storeys+zones, mirror them; if only phases, fall to
   phase-only. Read the captured WBS; don't assume its shape.
3. **RESPECT captured precedence** — generated Level-N work starts FS-after the captured Level-N
   structure (its Floor Slab) finishes; anchor per §3.3.
4. **SUBORDINATE always** — never move or recompute a captured date; only generated tasks flex.
5. **CONTINUE the trade sequence** the captured schedule stopped at — Hospital captured = *structure
   only*, so generated continues Enclosure→MEP→Finishes per storey.

**(g) What to TELL the session — note / improvise / user-facing:**
- **NOTE (don't get burned):**
  - *n=1 corpus* — learn the **shape** (storey×trade×zone cycle, FS, working-days), **not the
    numbers**. Hospital's "3 zones / P5D / WEEKLY" are *examples* → make zone count, trade list,
    calendar, productivity all **template params**, never constants.
  - `resource` is **OUR** concept (IFC had 0) — template-supplied, captured=null; keep it as a stable
    key for 5D later (§6).
  - Keep capture **lossless** (T1b) so grafting can read real storey/zone/critical of captured tasks.
  - **Reuse the existing Settings JSON editor** (`settings_editor.js`, project memory
    `project_settings_json_editor`) to edit template/instance — do **not** build a new editor (§6: no
    new authoring UI). Session must locate it (it shipped on `main`; confirm presence in this tree).
- **IMPROVISE for users (UX):**
  - **Coverage transparency** — show e.g. "Frame 13% from IFC baseline · 87% generated" + a legend.
    Never present generated as captured (no-silent-gaps Standing Rule).
  - **Visual distinction** — captured bars solid, real dates, critical-path colour; generated bars
    hatched/dashed, labelled *estimated*.
  - **Sensible default** — a building with zero native 4D still plays a credible storey×trade×zone
    sequence from the template alone.
  - **Pin/promote** — let a user fix a generated task's date, then re-anchor the rest (instance edit).
  - **Regenerate warns** it discards edits (§5.1 freeze model).
- **Witnesses:** `W-CYCLE` — a no-4D building's generated schedule reproduces the storey×trade×zone
  shape (`§GANTT storey-bands` × zones logged). `W-GRAFT` — on Hospital, a generated "Level N –
  Finishes" task's parent = the captured "Level N" `task_id` and its start ≥ captured Level-N slab
  finish.

---

## 6. Out of scope (do NOT drift)
- No new schedule-authoring UI in the viewer (Time Machine memory: export/import stripped on purpose).
- No edits to `deploy/live/*` (PRODUCTION). Dev only.
- No changes to `4D_phases.json` semantics used by the existing Python nD engine.
- Resource levelling / true CPM solving — capture and replay only; do not *recompute* float.
- **5D costing — OUT OF SCOPE for this build, but architecturally provisioned.** Evidence
  (2026-05-30): `Hospital 2.0.ifc` carries **zero** native 5D (`IfcCostSchedule`/`IfcCostItem`/
  `IfcCostValue`/`IfcResource`/`IfcRelAssignsToResource` all 0; only 46 `IfcElementQuantity`). So 5D
  can **never be captured** here — it is purely *derived*: quantity × unit-rate, cost-loaded onto the
  4D tasks. Cost vocabulary already exists (`RATES`/`RATES_DEFAULT` per class + locale override in
  `rates.js`/`locale_loader.js`; `variation_order.js`). **Do not build 5D now**, but the three-artifact
  model (§5.1) is deliberately its future home: a *rates template* (admin standard, like the regional
  `rates/*.json`) + a *per-building cost instance* (user-editable) + the SAME Settings chooser. To keep
  5D a clean bolt-on, the 4D instance `tasks[]` must retain `resource` as a stable key (it maps to a
  rates entry) and carry quantity/productivity, so cost-loading needs no reshape later. A separate
  bounded 5D session owns it; this session must not drift into costing.
