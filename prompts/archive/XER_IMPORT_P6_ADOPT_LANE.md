# FOREIGN-PROGRAMME ADOPT — bring a real P6 plan into the 4D/5D fold (XER is one reader)

# ⚠ DO NOT REMOVE
**Scope:** a **foreign-programme adopt seam** that lands a real external schedule into our IFC-native
tables (`schedules / tasks / task_sequences / calendars`) via the EXISTING captured-aware adopt path —
then lets the user DEEPEN it (§SE-WBS `addTask`/`breakdownByAttribute`) and BIND tasks → model elements
(`assignElement`) so a flat plan becomes a signed 4D/5D fold. **The seam is pluggable: readers are
IFC-4D (open, already shipped), P6-XML (PMXML, structured), and P6-XER (de-facto interchange).** Read
the §-log after every run (Log Mandate). Honour until DONE. **Spec-first:** every slice names its
witness claim BEFORE code. **Non-invent:** every row traces to a line in the source file — never
synthesize a date, duration, float, or dependency.

## §FORMAT-REALITY — why a SEAM, not "an XER importer" (user Q 2026-06-24)
XER is **Oracle Primavera proprietary, NOT an open standard** — fair concern. But it is *not* a
showstopper to READ: it's a documented tab-delimited text export of *your own* plan (open-source
parsers exist; no licensing wall to parse a file you exported). The risk is **building a lane on a
vendor format**. Mitigation = pluggable readers, lead with open:
- **IFC-4D** (`IfcWorkSchedule/IfcTask/IfcRelSequence`) — the genuinely-open path, **already ingested**
  (`import_db_builder.js:92-139`). This is the durable foundation; XER/XML feed the SAME tables.
- **P6-XML (PMXML)** — Oracle's own structured, schema'd export. More stable across P6 versions than
  XER; preferred reader where the user can choose the export. (Reader §X-XML, after XER proves the seam.)
- **P6-XER** — the format planners actually email around (hence his analyzer targets it). Support it for
  adoption reach, but as ONE reader behind the seam — if XER ever breaks, the open readers carry the lane.
**Decision:** if XER feels too vendor-locked, ship IFC-4D-only + PMXML first and treat XER as opt-in.
Recommend supporting XER anyway (it's where the real-world plans are), de-risked by the seam.

---

## §WHY — the competitive diff that triggered this (2026-06-24)
Trigger: Mohammad Tajari's **"P6 Analyzer"** (LinkedIn `urn:li:activity:7474636690373111808`) — an
HTML app that eats a **P6 XER** file and renders Gantt · Dashboard · S-Curve · DCMA · Delay Analysis ·
Cash-Flow · CPM/Longest-Path. It is a **read-only analytics lens over a finished XER**. It has **zero
4D** (no geometry, no model binding) and only **cash-flow** as a "5D" gesture.

Our TimeMachine 4D/5D arc (§FUSED_4D5D_WEDGE_LANE, §SE arc COMPLETE) already SURPASSES it on the axes
that matter — interactive drag-Gantt that *reschedules* (`moveTask`), `computeCpm` (FS/SS/FF/SF+lag),
schedule↔3D binding (`task_elements`, rename-proof), 5D cost folded from geometry, what-if, ERP fold,
live cross-surface sync. **The ONE thing he has that we don't: he ingests the industry-standard P6 XER.**
This lane closes that single gap — and turns it into our wedge, because once a P6 plan is *in*, the user
can bind it to the model (the thing his analyzer structurally cannot do).

Deferred / explicitly out of scope (his strengths that DON'T touch our differentiator): **DCMA 14-point
quality scorer** and **formal windows delay-analysis** — note them, do not build them here.

---

## §SYNC — what landed AFTER the §SE arc (sw v716→v725, must compose with these)
Read before coding — the adopted P6 plan must behave like any other authored schedule through these:
- **#518 §SE-WBS deepen** (`98cbfb5`, sw v725, W-SE-WBS) — `addTask(db,schedId,{name,wbsParent})` and
  `breakdownByAttribute(db,schedId,taskId,attr)` (split a populated phase by storey|type|discipline,
  parent→summary, no `_cap` coverage lost). Both emit deterministic ops; `schedule_sync.applyOp` has
  `addtask`/`breakdown` cases. **§X3 requirement:** an ADOPTED P6 plan must be deepenable by these verbs
  AND replay through `applyOp` — the P6 WBS is a starting point the user breaks down further, not frozen.
- **#516 W-WHATIF-AUTHORED-SYNC** (`dfeee2f`, sw v723) — an authored schedule now flows to What-if and
  the wizard stops re-prompting Generate after Apply. **§X3 requirement:** an adopted P6 plan must reach
  What-if the SAME way (it is "authored from import"), and must NOT re-prompt Generate (it's captured).
- **#515 W-TM-REFOLD** (`2f19f75`, sw v722) — TM re-folds off LIVE edited tasks, not the stale cache.
  **§X3/§X4:** after binding/editing imported tasks, the TM scrub reflects them (no stale fold).
- **#517 perf** (`9ce778f`, sw v724) — the editor reuses the viewer's IndexedDB building-db cache. The
  XER drop carries NO geometry → reuse the already-loaded model db; do not re-download.

## §ANCHORS — what already exists (do NOT rebuild)
- **Target schema** — `viewer/import_db_builder.js:86-90` creates `schedules / tasks / task_sequences /
  task_elements / calendars`. The `tasks` DDL (line 87) already carries `wbs_parent, is_summary,
  predefined_type, schedule_start/finish/duration, early_*, late_*, free_float, total_float,
  is_critical, resource, status`. **No DDL change needed.** XER must produce rows of this exact shape.
- **Adopt seam** — `viewer/schedule_author.js` `activeSchedule(db)` + the captured-aware wizard
  (W-AUTHOR-CAPTURED 11/11): if a schedule is already present, the wizard ADOPTS it (Generate hidden),
  does not clobber. **XER import = a third capture source feeding `activeSchedule`, exactly like the
  Bonsai/Revit IFC-native capture in `import_db_builder.js:92-139`.**
- **Bind craft** — `assignElement(db, taskId, guid)` (`schedule_author.js:174`) writes `task_elements`,
  rename-proof, moves 5D cost between tasks. This is the post-import 4D step. Already shipped.
- **CPM** — `computeCpm(db, schedId)` re-derives ES/EF/LS/LF/float/critical from `task_sequences`.
  Used to VERIFY (not replace) P6's adopted float — see §X4.

---

## §XER — the format (P6 export, the only source of truth)
XER is **tab-delimited, table-record text**. Each table opens with `%T<TAB><TABLE_NAME>`, a
`%F<TAB>col1<TAB>col2…` header row, then `%R<TAB>val1<TAB>val2…` data rows. `%E` ends the file.
Parse generically into `{ tableName: { fields:[…], rows:[{col:val}] } }` — never hard-code column
positions (P6 versions reorder columns; `%F` is authoritative).

Tables we consume (ignore all others):
| XER table | gives us | our table |
|---|---|---|
| `PROJECT` (`proj_id, proj_short_name, plan_start_date`) | the schedule header | `schedules` |
| `PROJWBS` (`wbs_id, parent_wbs_id, wbs_name, proj_node_flag`) | WBS summary nodes | `tasks` (is_summary=1) |
| `TASK` (`task_id, wbs_id, task_code, task_name, target_start_date, target_end_date, target_drtn_hr_cnt, status_code, driving_path_flag, total_float_hr_cnt, free_float_hr_cnt, early_start_date, early_end_date, late_start_date, late_end_date`) | leaf activities + P6's own CPM | `tasks` (is_summary=0) |
| `TASKPRED` (`task_id, pred_task_id, pred_type, lag_hr_cnt`) | logic | `task_sequences` |
| `CALENDAR` (`clndr_id, clndr_name, day_hr_cnt`) | hrs/day for hr→day | `calendars` + conversion divisor |

**Conversions (NON-INVENT, all derived from the file):**
- Dates: XER `YYYY-MM-DD HH:MM` → ISO-8601 verbatim (keep raw string in the schedule_* TEXT cols).
- Durations & lag are in **HOURS** → days = `hr / day_hr_cnt` (calendar's `day_hr_cnt`, default 8 only
  if the row's calendar is absent — and §-log when the default is used; never silently assume 8).
- `pred_type`: `PR_FS→FS, PR_SS→SS, PR_FF→FF, PR_SF→SF`.
- `is_critical`: adopt P6 directly — `driving_path_flag='Y' OR total_float_hr_cnt<=0`. (Re-verified by
  computeCpm in §X4; mismatch is reported, not silently overwritten.)
- `task_elements`: **EMPTY on import** (XER has no geometry). This is correct and is the §X3 wedge entry.
- `wbs_parent`: a leaf TASK's parent = its `wbs_id`; a PROJWBS node's parent = `parent_wbs_id`
  (null/`proj_node_flag='Y'` → top). task_id namespacing: prefix WBS ids vs activity ids so they can't
  collide (e.g. `W:<wbs_id>` / `A:<task_id>`), and re-point TASKPRED through the `A:` namespace.

---

## §BINDING-BOUNDARY — "can anyone's XER be imported?" (user Q 2026-06-24)
**Parse + adopt = yes, any valid P6 XER.** The `%T/%F/%R` format is uniform; the parser is `%F`-driven
(version-tolerant), so any export lands WBS + FS/SS/FF/SF+lag + dates + P6 CPM into `tasks`/
`task_sequences` via `activeSchedule()` and inherits editor/CPM/drag-Gantt/sync/What-if for free.
**But adopted ≠ 4D — two hard preconditions, by design, not defect:**
1. **No geometry from the XER.** It carries `task_code/task_name`, never model guids → `task_elements`
   lands EMPTY. It becomes 4D/5D only when tasks are BOUND to the loaded model (`assignElement`). The
   bind step IS the wedge (his analyzer stops at the parsed schedule; we begin there).
2. **The XER must be FOR the loaded building.** A foreign-project XER parses + adopts cleanly but has
   nothing meaningful to bind to. "Imported" is structural; "useful 4D" needs plan↔model correspondence
   — a precondition we surface to the user (§-log `§XER-BIND-CANDIDATES n=<matchable>` after adopt), not
   a gate we silently fake.
3. **Binding stays signed/manual** (rename-proof `task_elements`). A name/code auto-match is at most an
   OPT-IN assist, labelled fragile (the P6-tagging trap the §SE memory warns against), never the default.

## §HANDOFF-CONTRACT (engine/editor session → this lane, 2026-06-24, confirmed)
The §SE/What-if author session (engine/editor/What-if owner) confirmed and closed:
- A P6-adopted schedule lands into the SAME IFC-native tables their work reads/writes —
  `tasks / task_sequences / task_elements` (the `import_db_builder` DDL). **No schema change.**
- Adopting via `ScheduleAuthor.activeSchedule()` (the captured path, like Bonsai/Revit IfcWorkSchedule
  adoption) yields FOR FREE: Editor WBS view + `addTask`/`breakdownByAttribute` (#518); CPM/drag-Gantt/
  cross-tab sync via `schedule_sync.applyOp`; the What-if mirror to `C_ProjectPhase` (#516) + TM `_cap`.
- Their watch-item (agreed): XER's WBS hierarchy + FS/SS/FF/SF+lag map cleanly to `tasks.wbs_parent/
  is_summary` and `task_sequences(type, lag)` → no new schema, **just the XER→rows parser** (§X1/§X2).
- **No overlap to resolve:** they touched engine/editor/What-if, NOT `prompts/` or the import path. This
  lane owns the parser + adopt wiring only. Lane is fully handed to this session.

## §SLICES (spec-first; each names its witness)

### §X1 — the parser (`viewer/xer_parser.js`, pure, node-testable)
`parseXER(text) → { project, wbs[], tasks[], preds[], calendars[] }` — generic `%T/%F/%R` reader, no
column-position assumptions, tolerant of unknown tables. Returns raw typed rows; NO mapping yet.
- **W-XER-PARSE** (node, a real exported XER fixture): row counts per table match a `grep -c '^%R'`
  per-table count; `%F` header drives field names; a reordered-column variant parses identically.

### §X2 — the mapper (`xer_parser.js` `xerToScheduleData(parsed) → data{…}`)
Maps parsed rows → the EXACT `{schedules, tasks, taskSequences, calendars}` shape consumed by
`import_db_builder.js:92-139`. All conversions per §XER. Returns `taskElements: []`.
- **W-XER-MAP** (node): summary count == PROJWBS rows; leaf count == TASK rows; sequence count ==
  TASKPRED rows; every `pred_type` mapped (0 unmapped); a known activity's duration_days ==
  `target_drtn_hr_cnt / day_hr_cnt` to the hour; date strings ISO-valid; `task_elements` length 0.
  Names the issue: *does the P6 WBS+logic survive the hour→day + namespacing round-trip without loss?*

### §X3 — adopt into the live DB (wire to the captured-aware wizard)
XER drop → `xerToScheduleData` → write rows through the SAME path the IFC capture uses, then
`activeSchedule(db)` returns the adopted schedule; wizard shows **ADOPTED** state (Generate hidden,
same as W-AUTHOR-CAPTURED), step ② "assign elements" now operates on the imported P6 tasks. The bind
loop (`assignElement`) is the 4D entry — **§-log `§XER-BIND task=<id> guid=<g>` per bind, cost re-folds.**
- **W-XER-ADOPT** (node, real SampleHouse db + real XER): after adopt, `activeSchedule` ≠ null and is
  the imported one; binding 3 imported tasks to 3 SampleHouse guids populates `task_elements` (3 rows)
  and `foldCost` rolls a non-zero phase cost onto a previously cost-less P6 task. Names the issue:
  *can a guid-less P6 plan acquire 4D binding + 5D cost through the existing craft, with no clobber?*
- **W-XER-DEEPEN** (node, §SYNC #518): `breakdownByAttribute` on a bound imported phase splits it by
  storey/type with **zero `_cap` coverage loss** (every element still on a non-summary leaf); `addTask`
  adds a sub-task under an imported WBS node; both replay through `schedule_sync.applyOp` to a peer db
  that converges byte-identical. Names: *is an adopted P6 WBS a live starting point, not a frozen blob?*
- **W-XER-WHATIF** (node, §SYNC #516): an adopted P6 plan reaches What-if as "authored from import" and
  does NOT re-prompt Generate (captured-aware). Names: *does import behave like authoring downstream?*

### §X4 — CPM cross-check (adopt vs re-derive, report don't overwrite)
Run `computeCpm` on the imported `task_sequences`; compare our ES/EF/float/critical against P6's adopted
values. Surface agreement, log divergences (calendar/constraint-driven gaps are EXPECTED — P6 honours
resource calendars + constraints we don't model). **Never overwrite P6's baseline cols with ours**;
write ours to the `early_*/late_*/float/is_critical` working cols only on explicit "Compute CPM".
- **W-XER-CPM** (node): on an all-FS chain XER with no constraints, our critical set == P6's
  `driving_path_flag='Y'` set EXACTLY; on a constrained XER, divergences are itemised (count + task ids)
  not hidden. Names the issue: *is our CPM faithful to P6 where the logic is comparable, and honest
  where it isn't?*

### §X5 (deploy, after X1-X4 green) — UI entry + ERPUserGuide
"Import P6 (.xer)" drop target alongside the IFC import; on success route to the wizard in ADOPTED
state. Publish ERPUserGuide §"Adopt a P6 plan" with one live fig (real XER → bound 4D). Deploy via the
dev→smoke→fetch-back flow; docs ONLY via `scripts/safe_gh_deploy.sh`.
- **§XER-SMOKE** (headless Chromium, secondary/wiring only per the §-log-first rule): xer_parser loads,
  drop fires, wizard opens ADOPTED. Value proof stays in W-XER-* node witnesses.

---

## §FIXTURE — get a real XER (non-invent gate)
**BLOCKED until a real exported `.xer` exists.** Do NOT hand-author an XER (that violates non-invent —
a fabricated fixture proves nothing about real P6 quirks). Options, in order:
1. Ask the user for one exported P6 file (smallest real project).
2. A public sample XER (e.g. an open P6 training export) — cite the source URI in the fixture header.
Until then X1-X4 are SPEC-READY but UNWITNESSED. **⛔ BLOCKED: provide one real exported `.xer` file
(or approve fetching a cited public sample) so the witnesses run against real P6 output, not invention.**

---

## §LOG
- 2026-06-24 — Lane opened from the P6-Analyzer competitive diff. Anchors confirmed against live
  `import_db_builder.js` (schema lines 86-90), `schedule_author.js` (`activeSchedule`/`assignElement`).
  Schema needs NO change — a foreign programme is a third capture source on the shipped adopt seam.
  Blocked on a real fixture (non-invent). Cross-ref §FUSED_4D5D_WEDGE_LANE (§SE arc COMPLETE).
- 2026-06-24 (revise, user Q×2): (1) **XER is proprietary, not open** → reframed XER-importer ⇒
  **pluggable foreign-programme adopt SEAM**; lead with open IFC-4D (already shipped) + P6-XML (PMXML),
  XER opt-in behind the seam (§FORMAT-REALITY). (2) **Synced to landed work** sw v716→v725 — adopted
  plan must compose with #518 WBS-deepen (`addTask`/`breakdownByAttribute`+`applyOp`), #516 what-if/
  no-re-prompt, #515 TM-refold, #517 IndexedDB reuse (§SYNC). Added W-XER-DEEPEN + W-XER-WHATIF.
- 2026-06-25 (BUILD, user: "do it for the grand Hospital DB" + "create an XER sample to demo"):
  branch `feat/foreign-schedule-import` off fresh main. SHIPPED the parser + adopt + demo fixtures,
  WITNESSED on the REAL Hospital model (63,415 elems, 7 levels, all disciplines):
  - **`tests/gen_foreign_schedule.js`** — single-source generator; one canonical GW-Hospital programme
    (6 WBS / 14 activities / 14 rels incl FS/SS/FF + lag; forward+backward CPM → 300-day, 13-critical)
    emitted as BOTH `tests/fixtures/Hospital_GW_Programme.xer` AND `.xml` (PMXML) + `Hospital_GW_binding.json`
    (activity→real-IFC-class predicate). DEMO artifact, grounded in real Hospital classes; NOT a proof fixture.
  - **`viewer/foreign_schedule.js`** — the seam: `parseXER` (%T/%F/%R, %F-driven) + `parsePMXML`
    (APIBusinessObjects) → neutral shape → `toScheduleData` → IFC-native rows (W:/A: namespaced,
    hour→day via calendar `day_hr_cnt`, PR_FS/Finish-to-Start→FS); `adoptIntoDb` (mirrors
    import_db_builder capture INSERTs + `_ensureWideTasks` legacy-thin migration). task_elements EMPTY by design.
  - **`erp/tests/foreign_adopt_witness.js` — W-FGN 22/22 GREEN** on real Hospital_meta.db:
    W-FOREIGN-EQ (PMXML==XER byte-identical rows) · W-FOREIGN-ADOPT (`activeSchedule` captured=true) ·
    W-FOREIGN-CPM (our `computeCpm` reproduces generator EXACTLY — 300d, 13 critical, A3020 off-critical) ·
    W-FOREIGN-BIND (525 REAL Hospital elements bound across 14 activities → 5D foldCost=$2,748,506;
    reassign A1010→A2010 total-invariant = the wedge).
  - STILL ⛔ on §X5 deploy (UI drop target) + the REAL-export proof fixture (these samples are synthetic
    demo). NEXT: wire a "Import P6 (.xer/.xml)" drop in the viewer → `foreign_schedule` → wizard ADOPTED;
    then W-XER-DEEPEN/W-XER-WHATIF compose-checks (§SYNC). PR off `feat/foreign-schedule-import`.
