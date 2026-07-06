# ⚠ DO NOT REMOVE — Scope: TM construction-schedule editing via the standard JSON editor
# Read the log after every run. Honour this block until every Scope item is DONE.

## One-line goal
Make the Time Machine's construction schedule (phases / timeline / resources) a **user-editable
JSON** edited IN-APP through the already-deployed standard JSON editor (`settings_editor.js`) —
NOT a DIY text-file export/edit/reimport. This is the implementation of `GANTT_ACCURACY.md §B`
("User-Editable Schedule JSON"), now built on top of the editor shipped in PR #57.

Builds on: [[project_settings_json_editor]] (deployed), `GANTT_ACCURACY.md §B` (the schema + DB plan).
Read both before starting.

## SCOPE — Gantt schedule DATA only (separation of concern)
Handle the **exact Gantt-chart elements** and nothing else for this first pass:
phases, sequence, durations/productivity, resources/crews, predecessors, calendar/work-hours, and
per-element overrides. The deliverable is: this data is editable as a standard JSON and drives
`injectGantt()` → `kernel_ops`.
**OUT OF SCOPE (do NOT touch — separate concern):** the cinematic/visual layer — `GANTT_ACCURACY.md
§C` construction effects (glow, sparks, emissive), camera/scene work, TM pacing constants
(`_BEAT_*`, `_HERO_*`, `TICK_MS`), and any UI/graphics polish. Those are a different session and
must not leak into the schedule-data editing.

## Why this is more than "add one JSON" — UNIFY, don't add a 4th silo
There are already THREE overlapping sources of construction-sequence truth. VERIFY each at the
start of the session (don't trust this list blindly — confirm with §-logs/grep):
1. `bim-ootb/viewer/rates.js` — hardcoded `SEQUENCE_RULES` (IfcClass→{phase,sequence,resource},
   line ~154) + `LABOR_RATES` (resource→{productivity:{IfcClass:unitsPerDay}}, line ~89).
   **This is what `time_machine.js injectGantt()` actually reads** (`window.SEQUENCE_RULES` /
   `window.LABOR_RATES`, line ~2237).
2. `bim-ootb/viewer/rates/*.json` (17 regional, e.g. `cidb2024_my.json`) — ALREADY carries
   `phase` + `sequence` + `resource` + `productivity` + `crew` + `rate` per IFC class. Confirm
   whether/how these override the hardcoded objects (rates.js `getProductivity` line ~220).
3. `bim-compiler/templates/4D_phases.json` — pipeline template: `calendar` + `phases[]` +
   `ifc_class_rules{}` (server-side only, NOT served to the browser).
4. (proposed) `tm_schedule` per-building DB row — `GANTT_ACCURACY.md §B`.

**Mandate:** do NOT introduce a 4th independent schedule format. Pick ONE canonical browser-side
schema (below), make `injectGantt()` read IT, and derive/migrate the others into it. The hardcoded
`SEQUENCE_RULES`/`LABOR_RATES` in rates.js should become a *fallback default*, not the source.

## Canonical schema (browser) — frame it EDITOR-NATIVE from day one
Confirm against `GANTT_ACCURACY.md §B`; this aligns §B with the existing `4D_phases.json` shape.
Two layers:
- **Default (project-wide)**: ship `viewer/construction_schedule.json` (port of `4D_phases.json`).
  Settings-editable (project default), loaded via `loadJsonWithOverrides('construction_schedule.json','json_schedule')`.
- **Per-building**: `tm_schedule` DB row (TEXT `json`) — this building's specific edits, auto-generated
  from the default + `injectGantt()` if absent. Edited from the TM panel.

```json
{
  "projectStart": "2025-01-06T07:00:00",
  "workHours": { "start": 7, "end": 15 },
  "workDays": [1,2,3,4,5],
  "phases": [ { "name": "Substructure", "sequence": 1 } ],
  "classRules": [
    { "ifcClass": "IfcFooting", "phase": "Substructure", "sequence": 1,
      "resource": "CONCRETE_GANG", "productivity": 6, "predecessors": [] }
  ],
  "resources": [ { "id": "CONCRETE_GANG", "crewSize": 1, "dailyCapacity": 50 } ],
  "overrides": [ { "guid": "guid-xxx", "sequence": 8, "phase": "Roof", "startAfter": "guid-yyy" } ]
}
```
**Framing decision (vs `GANTT_ACCURACY.md §B`'s maps):** `classRules`, `resources`, `overrides` are
**arrays-of-objects**, NOT maps keyed by id. This is the editor-native shape (reorderable rows,
multi-field) and needs ZERO editor change. The consumer builds a lookup `Map` at load
(`byClass[r.ifcClass]=r`). IF you instead keep the §B maps, you MUST do the editor enhancement below.

## Editor stays ABSTRACT — the JSON conforms, not the editor (DECIDED)
Do NOT add a map/object-of-objects case to `settings_editor.js`. The editor is one abstract, dynamic
renderer; every project JSON conforms to **THE JSON STANDARD** in `SETTINGS_JSON_EDITOR.md`. The §B
maps (`resources`/`overrides`/`ifc_class_rules`) are therefore **reframed to arrays-of-objects** with
an explicit key field (`id`/`guid`/`ifcClass`) — exactly the canonical schema above. Zero editor
change; the consumer rebuilds a lookup `Map` at load (`byClass[r.ifcClass]=r`). If any data truly
needs a map shape to keep meaning, that's a signal it isn't a hand-edit config — keep it out of the
editor, not the editor out of standard.

## DB source via the COMMON READER (not a bespoke path)
Per-building schedule lives in `tm_schedule`, not a `viewer/*.json`. Do NOT write a one-off loader —
add it as a `source:'db'` entry in the shared registry (`SETTINGS_JSON_EDITOR.md` §Common reader
handler) so it reads/writes through the SAME `ProjectJson.load(id)`/`ProjectJson.save(id,json)` API
all other JSONs use. **Two registry entries** (template vs instance — see §"Template/Instance/Permission"):
```js
{ id:'schedule',          label:'Construction Schedule',   source:'db',  scope:'building',
  dbTable:'tm_schedule',  storageKey:'json_schedule',      editable:'user'  }   // the INSTANCE — user edits this
{ id:'schedule-template', label:'Schedule Rules (default)', source:'url', scope:'project',
  url:'construction_schedule.json', storageKey:'json_schedule_template', editable:'admin' }  // the TEMPLATE — locked
```
- **`ProjectJson.load('schedule')`**: `SELECT json FROM tm_schedule LIMIT 1`. If absent → generate
  from the project default (`schedule.json` via `ProjectJson.load`) + `injectGantt()` logic.
- **`ProjectJson.save('schedule', json)`**: `UPSERT tm_schedule` → rebuild → `DELETE FROM kernel_ops
  WHERE op_type='ELEMENT_PLACE'` → re-inject → `renderAtTime(cursor)` replay. Log `§TM_SCHEDULE_APPLY ops=N`.
- Keep `?schedule=URL` import (§B item 1) + Export/Download (reuse the hub's download).

## Where JSONs live + how users find them (answer the discovery question)
- **Project-wide editable JSONs** → ONE place: Settings → "Edit Project JSON" hub. Register here:
  `construction_schedule.json` (default), and (separately) the active `rates/<locale>.json`.
- **Per-building schedule** (`tm_schedule`) → contextual: an "Edit Schedule" button on the TM panel,
  opening the SAME `SettingsEditor`. (Per-building data has no meaning without a loaded building, so
  it belongs in the TM panel, not the global hub.) State this split in the UI copy so users aren't
  confused about project-default vs this-building.
- **Devs** find every editable JSON in ONE file: `viewer/json_registry.js` (`ProjectJson.list()`).
  Naming is mechanical (`id`, `<id>.json`, `json_<id>` key) per the editor spec's Naming standard.

## Template vs Instance + Permission model (DECIDED 2026-05-30 — see `4D_CAPTURE_AND_FALLBACK.md §5.1`)
The schedule is **three artifacts**, not one — and the two JSONs differ in BOTH shape and who may edit:

| | **Template** `schedule-template` | **Instance** `schedule` |
|---|---|---|
| Content | the **DNA**: compact, generalized, **recursive/repetitive rules** (class families, zone/storey repetition, `_default`) — NOT every item enumerated | the **organism**: the TM-generated, fully-expanded `tasks[]` for THIS building (real captured tasks verbatim + template-expanded fallback) |
| Source / scope | `source:'url'`, project-wide, **one shared** | `source:'db'` (`tm_schedule`), **one per building** |
| Editable by | **Admin only** (`editable:'admin'`) — locked in the chooser until an admin flag exists | **User** (`editable:'user'`) — opens normally |
| Where | Settings → "Edit Project JSON" hub (shown, read-only) | the **Settings chooser** lists it as "this building's schedule"; also the TM panel "Edit Schedule" button |

**Chooser behaviour:** the Settings JSON chooser renders `ProjectJson.list()`. `editable:'user'`
entries open in the editor for editing + Download; `editable:'admin'` entries render **read-only**
(view/Download only) until the admin capability ships. The instance appears in the chooser only when
a building is loaded (per-building data has no meaning without one).

**Shape reconciliation (supersedes the rules-flavoured "Canonical schema" above):** the *editable
instance* is **task-shaped** (`tasks[]` — names/dates/sequence/resource/predecessors + `wbs_parent`/
float/baseline per `4D_CAPTURE §5.2`). The *rules* (`classRules`/`phases`/`zoning`) live in the
**admin template**, not the user instance. Both still conform to THE JSON STANDARD (arrays-of-objects);
the generator is the converter (template rules → expanded instance tasks). Keep `resource` a stable
key on each task — it is the future 5D cost-loading hook (`4D_CAPTURE §6`, 5D out of scope here).

## Refactoring checklist (spell-out, with file:line to VERIFY first)
| File | Change |
|------|--------|
| `viewer/json_registry.js` | NEW — promote `_jsonRegistry` out of panels.js; add `ProjectJson.load/save/list` (common reader); add the `schedule` (`source:'db'`) entry. THE index devs grep. |
| `viewer/settings_editor.js` | NO change for maps (JSON conforms to standard). `loadJsonWithOverrides` becomes the url-source impl under `ProjectJson`. |
| `viewer/schedule.json` | NEW — port of `templates/4D_phases.json`, reframed to arrays-of-objects (`classRules[]`, `resources[]`), naming = `schedule` standard. |
| `viewer/rates.js` (~89 `LABOR_RATES`, ~154 `SEQUENCE_RULES`, ~220 `getProductivity`) | Become FALLBACK defaults; primary source = loaded schedule JSON. De-duplicate vs `rates/*.json`. |
| `viewer/time_machine.js` (`injectGantt` ~2227, reads `window.SEQUENCE_RULES`/`LABOR_RATES` ~2237) | Read the canonical schedule object (via `ProjectJson.load('schedule')`) instead of globals; honour `overrides`/`predecessors`. |
| `viewer/time_machine.js` (TM panel build) | Add "Edit Schedule" button → `SettingsEditor` over `ProjectJson.load('schedule')`; Export/Import (§B 4–5). |
| `viewer/print_sheet.js`, `grid_drag.js`, `measure.js` | Migrate the inline `loadJsonWithOverrides(url,key)` calls (shipped PR #57) to `ProjectJson.load(id)` — kill drift. |
| `viewer/panels.js` | Hub renders `ProjectJson.list()` instead of the inline `_jsonRegistry`. |
| `viewer/boq_charts.html` | Gantt already reads `kernel_ops` (§D done) — confirm edits propagate after re-inject; no second generator. |
| `viewer/sw.js` | Precache `schedule.json` + `json_registry.js`; bump `CACHE_VERSION`. |
| `viewer/viewer.html` | Add `<script src="json_registry.js">` (before panels.js); editor already loaded. |
| `templates/4D_phases.json` (bim-compiler) | Keep as pipeline source; document it as the seed for `schedule.json` so the two don't drift. |

## Constraints
- Branch from `origin/main` (NOT the active working branch) — see [[project_settings_json_editor]] git note.
- `settings_editor.js` stays app-agnostic (no TM identifiers). The DB adapter + TM wiring live in
  `time_machine.js`/`panels.js`, passing only data + callbacks to `SettingsEditor`.
- Spec-first. `injectGantt()` behaviour must be preserved when no edits exist (regression).
- `?tm=play` shared link, desktop AND mobile playback unaffected.

## Tests (§-witness; name the issue each proves; read the log after every run)
- `§PROPSHEET_RENDER` fires for the schedule schema (phases reorderable, classRules rows correct).
- Round-trip: edit a phase `sequence` / a classRule `productivity` → `getState` → matches edited JSON.
- `§TM_SCHEDULE_APPLY ops=N` — saved edit re-injects kernel_ops; element order in `§` log changes.
- Regression: building with NO saved schedule replays identically to current `injectGantt()` output.
- (if map case built) `tests/test_json_to_schema.js` map fixture round-trips.
- Unification: assert exactly ONE schedule source feeds `injectGantt()` (grep: no `window.SEQUENCE_RULES`
  read path remaining as primary).
- `node tests/audit_script_tags.js` + `audit_sw_precache.js` exit 0.

## Open design questions
1. ~~Array-of-objects vs maps~~ — **RESOLVED (user 2026-05-30): editor stays abstract, JSON conforms
   to the standard → arrays-of-objects. No editor map case.**
2. (still open) Should the project-wide default be per-locale — derive defaults from the active
   `rates/<locale>.json` (which already has phase/sequence/resource/productivity)? That would
   collapse sources 1+2 into the rates JSON and make `schedule.json` an override layer. Decide before
   porting `4D_phases.json`, since it changes what "the default" is.
