# ⚠ DO NOT REMOVE — Scope: The project's STANDARD JSON editor (Settings + any project JSON; BIM + ERP reuse)
# Read the log after every run. Honour this block until every Scope item is DONE.

## One-line goal
Extract the Settings property sheet out of `panels.js` into a standalone, app-agnostic
`settings_editor.js` that renders ANY `sections → rows → typed-fields` JSON and persists it —
so the BIM viewer AND ERP (and future apps) reuse the SAME editor, each passing only a schema.

## BROADENED SCOPE (2026-05-29, user directive)
This is NOT only the Settings property sheet. It is **the project's one standard JSON editor**:
**any JSON file in the project must be openable and editable from Settings**, with zero per-file
UI code. The user said, verbatim: *"it is to be a standard editor for all JSON in the project"*
and *"Any JSON in the project must be callable by Settings for user to edit easily."*

Two mechanisms make that true without per-file code:
1. **Auto-infer** — `jsonToSchema(obj, overrides)` turns ANY JSON into a default schema
   (object→sections, array-of-objects→reorderable rows, `typeof`→field type). No hand-authoring.
2. **JSON registry + Settings hub** — Settings lists registered project JSONs; clicking one
   fetches it, infers a schema (refined by an optional per-file `overrides` map), and opens it
   in the SAME `SettingsEditor`. Edits persist as a localStorage override (+ Download to commit).

Sourced from: S282_SETTINGS_JSON.md §4/§7, S282b Phase 2c, S282c Phase 5.
Related: [[project_s282c_pill_study]], the pill registry is the contract for the schema.

## Why (the problem)
BIM Settings (`panels.js` `_openSettingsPanel`) is hardcoded to ONE section ("Pill Icons")
+ Reset. The generic typed-field renderer S282 §4/§7 described ("walk sections→rows→fields,
no per-feature code") was never built. ERP (`ad_ui.js`) has its own static "⚙ Settings"
(`_showMore`) and does not load `pill_builder.js`. There is no shared editor. The pill is meant
to be a constant, app-agnostic UI tool panel; its Settings editor must be equally reusable.

## Design — Excel model: rows are data, columns are typed fields, renderer knows neither

### API (new `viewer/settings_editor.js`)
```js
SettingsEditor({
  container,                 // DOM element to render into
  schema,                    // see shape below — the single source of truth
  storageKey,                // localStorage/IndexedDB key for persisted values
  onChange: function(id, key, value, fullState) { ... }  // fired on every edit
});
// returns { rerender, getState, reset }
```

### Schema shape (pure data — no functions, no app refs)
```json
[
  { "section": "Pill Icons", "reorderable": true, "rows": [
      { "id": "measure", "label": "Measure", "fields": [
          { "key": "visible",  "type": "toggle", "value": true },
          { "key": "shortcut", "type": "text",   "value": "M", "readonly": true } ] }
  ]},
  { "section": "Locale", "rows": [
      { "id": "locale", "label": "Language", "fields": [
          { "key": "value", "type": "choice", "value": "en_US",
            "options": [ {"value":"en_US","label":"English"}, {"value":"ms_MY","label":"Bahasa"} ] } ] }
  ]}
]
```

### Field-type renderers (the ONLY per-type code)
- `toggle`   → checkbox / show-hide dot
- `choice`   → dropdown (options[])
- `text`     → input (honours `readonly`)
- `number`   → input + stepper (min/max/step optional)
- `color`    → swatch picker
- `readonly` → display-only span

The renderer walks `schema → section → row → field` and builds DOM. Adding a new section or
field = a new JSON object, ZERO renderer changes. Reorderable sections delegate to
`ListBuilder` (already in `viewer/list_builder.js`). Accordion section headers reuse the
existing ERP `.acc` look-and-feel (chevron, expand/collapse).

## Constraints
- `settings_editor.js` has ZERO BIM-specific identifiers — no `A.`, no `_actions`, no THREE.
  Provable by grep. Driven entirely by the passed schema + callbacks.
- No server; localStorage/IndexedDB only. No external deps; vanilla JS, pointer events.
- Lazy-loaded — no perf impact on streaming/render.
- Backward compatible: BIM's current Pill Icons toggles/order/reset behave identically.

## Migration
1. Build `settings_editor.js` with the six field renderers + section accordion + ListBuilder reorder.
2. BIM: `_openSettingsPanel` builds the Pill Icons section AS A SCHEMA and hands it to
   `SettingsEditor` (replaces `_renderPillRow`/`_buildPillRows` bespoke code; same result).
3. BIM: add Locale + Rate Template + Theme sections (existing loaders `_TRL_LOADER`, rate loader).
4. ERP: `erp.html` loads `pill_builder.js` + `list_builder.js` + `settings_editor.js`;
   ERP Settings icon opens `SettingsEditor` with an ERP schema (bubble order, client, theme).
   (ERP-takes-pill itself is S282c Phase 4/6 — the `registerHandler` seam is the prerequisite.)

## THE JSON STANDARD (every project JSON must follow this to be editor-ready)
The editor is ONE abstract, dynamic renderer. We do NOT grow it per-JSON; instead **every project
JSON conforms to this standard shape** so the single editor handles all of them. New/edited JSONs
that don't conform must be reframed to conform — not patched into the editor.

- **Root** = an object (sections) or an array-of-objects (one reorderable section).
- **A collection of records** = an **array of objects**, each with a stable key field
  (`id`/`guid`/`name`...). NOT a map keyed by id. (Maps-of-objects are non-conforming — reframe.)
- **A group of scalars** = a plain object; each leaf becomes one typed row.
- **A short value list** = an array of primitives (renders as a comma list; all-numeric stays numeric).
- **Light nesting** = a nested object inside a record → dotted field keys (`source.discipline`).
- Field types come from value `typeof`; refine with the per-file `overrides` map
  (`choice`/`readonly`/`color`/min-max). No functions, no app refs — pure data.

Rationale: keeping the editor minimal/abstract (this standard is the contract) is cheaper and more
robust than special-casing shapes. When a real JSON can't conform without losing meaning, that's a
signal the JSON's shape is wrong for hand-editing — fix the JSON, not the editor.

## Auto-infer — `jsonToSchema(raw, overrides)` (lives in `settings_editor.js`, also BIM-agnostic)
Turns ANY parsed JSON into a default schema so any file is editable with zero hand-authoring.
```js
SettingsEditor.jsonToSchema(raw, overrides) -> schema   // overrides keyed by "rowId.fieldKey" or "fieldKey"
```
Inference rules (deterministic — no guessing values, only shapes):
- top-level **array of objects** → one `reorderable:true` section; each element a row
  (`id = el.id ?? index`), columns = each key inferred as a field.
- top-level **object**:
  - value is **array of objects** → a `reorderable` section named after the key.
  - value is a **plain object** → a section named after the key; each leaf a `number/text/...` row.
  - value is a **primitive** → row in a default `General` section.
- nested object inside a row → **dotted field keys** (`source.discipline`); `getState` rebuilds nesting.
- `typeof` → field type: `boolean→toggle`, `number→number`, `"#rrggbb"→color`, else `text`.
- **array of primitives** (e.g. `ignore_classes`, `opening_z_range`) → `text`, comma-joined;
  `_listNumeric` flag restores numbers vs strings so all-number AND all-string arrays round-trip
  losslessly. KNOWN LIMIT (logged, not hidden): mixed-type / array-of-object-nested arrays.
  `overrides` may upgrade any field to `choice`/`readonly`/`color`/min-max.

## JSON registry + Settings hub
A registry (array literal, pure data) lists every editable project JSON:
```js
{ id, label, url, storageKey, overrides, readonly }   // overrides + readonly optional, per-file
```
Settings renders the Pill Icons section AS A SCHEMA (backward-compat) PLUS a list of registry
entries. Clicking an entry: `fetch(url)` → `jsonToSchema(raw, overrides)` → open in `SettingsEditor`
with **Download edited JSON** + **Reset**. In-scope registry entries this build:
`corporate.json`, `grid_rules.json`, `clash_rules.json`, `initbubble.json`.
**Out of scope:** `manifest.json` (254 KB machine-generated AD compile — not hand-editable).

### 4D schedule — ONE per-building instance JSON, NO template (decided 2026-05-30)
**Decision (user directive):** there is NO general/common 4D template. The universal rules —
Z-progression (build bottom-up), element-type dependency (footing→column→beam→slab, MEP after
structure), and "captured IFC 4D wins" — are construction physics, not per-project config, so they
stay as CODE (`SEQUENCE_RULES` table + the generator). A template would only let someone *configure*
a bug (e.g. the floating-beam Z inversion), which is wrong — that's a code-correctness fix, not a
knob. So we drop the `4d-template` read-only entry entirely.

What IS an artifact is the **per-building instance schedule**, COMPILED by internal code:
```
instance = absorb(IFC native 4D)               ← captured tasks OVERWRITE; flagged _captured=1
         ⊕ generate(Z-progression + type-deps) ← fills only the elements IFC 4D doesn't cover
         ⊕ human overrides                      ← the only thing a user edits
```
- **Hospital specifically:** a native IFC 4D set EXISTS (~2,900 covered) → `absorb` OVERWRITES those
  rows in the instance with the real captured dates/names; the generator fills the remainder.
- Each task row carries **provenance** (`source: captured | generated`) so the read view showcases
  the workings and the Gantt yellow-frame distinction key off the SAME flag.

### Registry entry: `schedule` (per-building instance, `source:'db'`) — READABLE first, editable next
ONE registry entry, no template counterpart:
- `ProjectJson.load('schedule')` projects the compiled per-building schedule (sections = phases or
  storeys; rows = tasks; fields = name, start, end, **source**, resource) into the standard
  `sections→rows→fields` shape — shaped to **reveal the compilation**, not dump raw kernel_ops.
- **Phase 1 (this build): READ-ONLY showcase.** Open with `readonly:true` (whole-file view below) so
  the user can open Settings → Schedule and SHOW how Hospital's programme is built: captured vs
  generated counts, phase/Z order, real IFC dates. No write path yet — lowest risk, satisfies
  "at least readable to showcase the workings."
- **Phase 2 (next): editable.** `ProjectJson.save('schedule', json)` writes overrides to a building
  DB table (`schedule_override`); next TM reload consumes them. Captured (`source:captured`) rows are
  protected from edit (absorb wins) unless explicitly overridden.
- id=`schedule`, storageKey=`json_schedule`, `source:'db'`, `readonly:true` (Phase 1).

### Whole-file read-only mode (registry `readonly:true`)
A registry entry with `readonly:true` opens with EVERY field display-only and **no Download / no
Reset / no Save** — a pure viewer (the Phase-1 schedule showcase). Implement as one flag on
`SettingsEditor` (`readonly:true` → skip input wiring, render values as `readonly` spans, hide the
persist controls). Witness: `§PROPSHEET_READONLY id=schedule fields=N writable=0`.

## Persistence (browser-only, no server)
- Edit → `SettingsEditor` writes full state to `localStorage[storageKey]`; fires `onChange`; logs `§PROPSHEET_SAVE`.
- **Consumers read overrides at load** via `window.loadJsonWithOverrides(url, storageKey)` —
  `fetch(url)` deep-merged with `localStorage[storageKey]`. This build SHIPS the helper and wires
  it where cheap; consumers not yet migrated to the helper still read the shipped file (edits then
  apply only after Download+commit). Each unmigrated consumer is LISTED, never silently dropped.

## Common reader handler — ONE registry-driven read path (refactor target)
Today each consumer hand-rolls `loadJsonWithOverrides(url, storageKey)` with the url + key repeated
at the call site — that DRIFTS (a consumer can use a key the registry doesn't, silently breaking the
override link). Fix: make the registry the single source of truth and read BY ID through one handler:
```js
ProjectJson.load(id) -> Promise<json>     // resolves url|db source + storageKey + overrides from the registry
ProjectJson.save(id, json)                // (DB-sourced entries) write back
ProjectJson.list() -> [{id,label,...}]    // what the Settings hub renders
```
- Promote the registry out of `panels.js` into a shared **`viewer/json_registry.js`** — THE one place
  devs look. Each entry: `{ id, label, source:'url'|'db', url?, storageKey, overrides?, dbTable?, readonly? }`.
  (`readonly:true` ⇒ whole-file view, no write path — used by the `schedule` showcase (Phase 1). `project` = for `source:'db'`, a fn reading `A.db` → JSON to render.)
- The editor write path and the consumer read path then share the SAME entry → cannot drift.
- Migrate consumers from inline `loadJsonWithOverrides(...)` to `ProjectJson.load(id)`.
- `source:'db'` entries (e.g. per-building schedule) read/write the DB behind the same API, so callers
  don't branch on source. `loadJsonWithOverrides` stays the url-source implementation underneath.

## Naming standard (so devs find them easily)
- **id** = canonical kebab/snake short name, also the lookup key everywhere: `corporate`, `grid_rules`,
  `clash_rules`, `initbubble`, `schedule`.
- **file** = `<id>.json` (or the documented existing path) served from `viewer/`.
- **storageKey** = `json_<id>` — mechanical, never ad-hoc.
- **registry** = `viewer/json_registry.js` is the index of ALL editable JSONs (grep `ProjectJson` / open
  the registry to discover every one). Settings hub + every consumer derive from it.

## Files
| File | Role |
|------|------|
| `viewer/settings_editor.js` | NEW — generic typed-field renderer + `jsonToSchema` + `loadJsonWithOverrides` |
| `viewer/list_builder.js` | reorderable rows (exists) |
| `viewer/panels.js` | BIM Settings → Pill Icons schema + JSON registry hub → SettingsEditor |
| `viewer/viewer.html` | `<script src="settings_editor.js?v=1">` after `list_builder.js` |
| `viewer/sw.js` | precache `settings_editor.js`; bump `CACHE_VERSION` |
| `viewer/ad_ui.js` / `erp.html` | ERP Settings → SettingsEditor with ERP schema (later phase) |

## Test (witness logs; read the log after every run; name the issue each proves)
- `§PROPSHEET_RENDER sections=N rows=M` — schema renders correct controls per field type.
- `§PROPSHEET_SAVE key=<storageKey> field=<id.key>=<value>` — edit persists to storageKey.
- node test `tests/test_json_to_schema.js` — `jsonToSchema` maps each target JSON shape correctly
  (corporate→1 section/N text; grid_rules→section-per-key/number; initbubble→reorderable/color;
  clash_rules→reorderable+dotted+comma-list). Proves auto-infer is deterministic, no DOM needed.
- `§SCHEDULE_INSTANCE building=Hospital captured=N generated=M tasks=T` — proves the instance is
  COMPILED (IFC 4D absorbed + generated remainder) with provenance, not a raw kernel_ops dump.
- `§PROPSHEET_READONLY id=schedule fields=N writable=0` — Phase-1 showcase exposes NO write path
  (no Save/Download/Reset); proves the schedule opens readable for demo without risking edits.
- `§SCHEDULE_OVERRIDE_SAVE building=Hospital rows=N` — (Phase 2) editing persists overrides to the
  building DB the next TM reload consumes; captured rows protected unless explicitly overridden.
- grep proof: `settings_editor.js` contains no BIM-specific identifiers (proves "free"/reusable).
- BIM regression: Pill Icons toggles + drag-reorder + reset behave as before (`§SETTINGS_SAVE`).
- ERP: `§PROPSHEET_RENDER` fires in ERP with an ERP schema (proves cross-app reuse) — later phase.
- `node tests/audit_script_tags.js` + `node tests/audit_sw_precache.js` exit 0 after adding the file.

---

## ✅ PHASE 1 DONE + DEPLOYED (2026-05-31, sw v556) — read-only schedule showcase, BOTH providers
- Settings → "4D Schedule (this building)" opens read-only (PR #68). `panels.js _projectSchedule()`:
  CAPTURED provider (native IFC `tasks`/`task_elements`, Hospital 2900/10-phase) **+** GENERATED provider
  `_projectGenerated()` (PR #76, sw v556) reads `kernel_ops` post-TM for dropped IFCs (e.g. LTU) — same
  contract, storey-grouped, source generated/captured/mixed. Diverged from the kernel_ops-only KIT below:
  native `tasks` is the truthful CAPTURED source; kernel_ops is the GENERATED fallback. Both shapes →
  `internal/schedule_instance.template.json` (jointly owned w/ gantt-support-gate session).
- `settings_editor.js`: `opts.readonly` + recursive `children[]` (dormant) + `__labelKey`/`__summary`.
- Tests: `test_schedule_projector` 10/10 (real Hospital_meta.db), `test_schedule_generated` 8/8,
  `test_schedule_readonly` 6/6, `test_recursive_render` 7/7. Panel fixes PR #70/#71/#73 (open-on-first-click,
  fold, no-stick). NEXT = Phase 2 editable (`schedule_override` DB writeback). OPEN: §PANEL_FOCUS stack churn.

## PHASE 1 IMPLEMENTATION KIT — "view the instance JSON" (verified on origin/main @ 1ba676f, 2026-05-30)
Hand this section to a fresh session. The editor is ALREADY merged/live; this adds the read-only
`schedule` showcase. All paths/line refs verified by reading the deployed source.

**Goal (Phase 1 only):** open BIM Settings → a new "Schedule (Hospital)" entry → a READ-ONLY view
that showcases how the per-building 4D programme is compiled (IFC-captured vs generated, per phase).
NO editing yet (that's Phase 2).

**Where the data lives (verified):**
- Active building DB = the sql.js instance. `time_machine.js loadOps()` reads it via
  `var app = A(); app.db.exec(...)` — `A` is a function returning the app namespace; `app.db` is the
  DB. (panels.js can reach the same DB; match whatever accessor panels already uses.)
- The compiled schedule = rows in **`kernel_ops`** (cols: `id,timestamp,op_type,parameters,
  input_guids,output_guid,undone`). Schedule rows: `WHERE op_type='ELEMENT_PLACE' AND undone=0`.
- Per-op `parameters` JSON: `{phase, cls, name, storey, resource, _end_ts}`; captured rows ALSO carry
  `_captured:1`, `_task`, and `phase` set to the real IFC task name. **Provenance =
  `parameters._captured ? 'captured' : 'generated'`.** `start_ts = timestamp`, `end_ts = _end_ts`.
- ⚠ `_captured`/`_end_ts` DO persist to the DB — the captured overlay UPDATEs `kernel_ops`
  (`time_machine.js` ~2557-2576, `UPDATE kernel_ops SET timestamp=?, parameters=? WHERE
  op_type='ELEMENT_PLACE' AND output_guid=?`). So reading `kernel_ops` is truthful.
- ⚠ TIMING: `kernel_ops` ELEMENT_PLACE rows only exist AFTER Time Machine has compiled (injectGantt
  runs on TM-open). If the projector finds zero rows, render a one-row note "Open Time Machine to
  compile the schedule" — do NOT fabricate. (Phase 2 may pre-compile.)

**⚠ Hospital = 39,853 ops — NEVER render per-element rows.** AGGREGATE in the projector:
```js
// _projectSchedule() → returns the showcase JSON (NOT 40k rows)
{ "Summary": { building, total_tasks, captured_from_IFC, generated, coverage_pct,
               project_start, project_end },          // plain object → scalar rows
  "By Phase": [ { id:"Substructure", phase, tasks, captured, generated, start, end }, … ] }
// jsonToSchema(this) → "Summary" section (scalar rows) + "By Phase" reorderable section (table).
```
Log `§SCHEDULE_INSTANCE building=Hospital captured=N generated=M tasks=T` from the projector.

**Three small code changes (no refactor needed for Phase 1):**
1. `settings_editor.js` — add `opts.readonly`. `renderField` ALREADY renders a display span for
   `field.readonly || type==='readonly'` (line ~237). So: when `opts.readonly`, mark every field
   readonly before render (or branch in `buildRow`). Log `§PROPSHEET_READONLY id=… fields=N writable=0`.
2. `panels.js` `_openJsonEditor` (~1232) — add a `source:'db'` branch: if `entry.source==='db'`,
   call `entry.project()` (returns the JSON) instead of `loadJsonWithOverrides(entry.url,…)`. When
   `entry.readonly`, pass `readonly:true` to `SettingsEditor` AND skip the Download + Reset buttons.
3. `panels.js` `_jsonRegistry` (~1194) — add `{ id:'schedule', label:'Schedule (Hospital)',
   source:'db', storageKey:'json_schedule', readonly:true, project:_projectSchedule }`.

**Witness BEFORE deploy (Node harness on the real Hospital DB):** load
`deploy/dev/buildings/Hospital_meta.db` (or the building DB the viewer ships), run the projector's
query, assert `captured+generated===tasks` and `captured≈2900`. Read the `§SCHEDULE_INSTANCE` log line.

**Gates + deploy (ONE flow):** `node --check` the two files → `node tests/audit_script_tags.js` +
`node tests/audit_sw_precache.js` (exit 0) → bump `sw.js CACHE_VERSION` + `?v=N` on changed files in
`viewer.html` → worktree off **fresh** `origin/main` → branch → PR → squash-merge → GH Actions deploys
→ `curl` verify live. (NOTE: a clean throwaway worktree `feat/schedule-showcase` was created during
scoping with NO edits and then removed — start fresh off latest `origin/main`.)

---

## BACKLOG — externalise `rates.js` rules → `rates.JSON` (user 2026-06-04)
`deploy/dev/rates.js` hardcodes the timeline-generation **"default steps"** as JS objects:
`SEQUENCE_RULES`, `LABOR_RATES`, `SEQUENCE_DEFAULT` (the ifc_class → phase/sequence/resource + labour
productivity that Time Machine's `injectGantt()` (`time_machine.js:2239`) uses to GENERATE the
`kernel_ops` timeline — it is NOT a JSON file today). The **cost** side already ships as JSON
(`deploy/dev/rates/*.json` — cidb2024_my, rsmeans2024_us, …) but the **sequence/labour** rules do not.
- **Want:** give `rates.js` its own **`rates.JSON`** (the sequence + labour rules) and register it in
  the Settings JSON editor (per the "ALL project JSON editable from Settings" directive), so phase
  steps/durations are user-editable without touching code. `rates.js` then loads from `rates.JSON`
  (current hardcoded values as the fallback default).
- **Why now:** the Find-panel **Phase axis** drives the real generator via `window.tmGenerateTimeline`
  → `SEQUENCE_RULES`; making those rules an editable JSON lets users tune phases/sequence themselves.
- **SCOPE model (user 2026-06-04) — every editor entry is chosen by one of three scopes:**
  - **country** → cost rates `rates/<country>.json` (flag switch: rsmeans2024_us=USD, cidb2024_my=RM, …); currency follows `meta.currency`.
  - **building** → schedule, projected from the active scene building's `kernel_ops` (the "Schedule (Hospital)" entry).
  - **global** → `sequence_rules.json` (one default file). NOTE the cleanup: strip `rate_per_day` MONEY out of
    sequence_rules.json — money belongs in the country cost packs; keep sequence_rules.json currency-free (phase + productivity).
