# Revit Parity — The Long Tail the Big Tools Over-Serve

> **Foundation:** [BIM_Designer_Browser.md](BIM_Designer_Browser.md) | [CLASH_DETECTION.md](CLASH_DETECTION.md) | [SQLite3D_Schema.md](SQLite3D_Schema.md) | [DocValidate.md](DocValidate.md) | [ASSEMBLY_BUILDER_SRS.md](ASSEMBLY_BUILDER_SRS.md)

<div class="bim-banner" markdown>
<b>We do not out-solve EnergyPlus. We serve the 80% of small buildings that never ran it.</b> The sophisticated tools Revit/Navisworks/IES gate behind seat licenses and analytical models are, for the long tail, a SQL query or a graph walk over data we already compiled. Every number traces to the model or to a <em>cited</em> coefficient table — never invented.
</div>

**Version:** 0.1 (2026-06-03) — SPEC, not yet implemented
**Status:** DRAFT — witness claims defined, no code yet
**Prime Directive binding:** Every analytical output is either (a) extracted from the model, (b) a geometric/graph fact computed from the model, or (c) a first-order calc whose every coefficient is a **cited lookup** flagged as a default. There is no fourth category. A number with no source is a bug, not a feature.

---

## 0. The Two Categories of "Analysis" — and Why the Line Matters

Revit's sophisticated toolset splits cleanly once you ask *where the number comes from*:

| Category | Examples | What it needs | Our stance |
|----------|----------|---------------|------------|
| **Geometric / topological** | clash, sun-hours, egress distance, MEP downstream trace, area takeoff | geometry + a graph/index we already hold | **Own it.** No solver, nothing to invent, can't be wrong. |
| **Solver-class** | full energy model, FEA, hydraulic flow | validated physics engine (EnergyPlus, Robot) + properties we drop on extraction | **Don't originate.** Export to the validated engine. |
| **Long-tail first-order** | degree-day heat loss, span/depth check, fixture-unit demand | rule-of-thumb formula + **cited** coefficient table | **Own it — citation-bound.** The number a junior engineer does by hand from a code table. |

The big tools serve the complex 20% and price for it. The long tail — houses, shops, small commercial — never opens EnergyPlus; the *answer they need* is a first-order number from a published table. That number is deterministic and citable, so it is **inside** the Prime Directive, not a violation of it. The keystone already exists: [ASSEMBLY_BUILDER_SRS.md](ASSEMBLY_BUILDER_SRS.md) computes **U-values** from a cited material library (17 witnesses). Every long-tail calc below follows that same pattern — *cited coefficient keyed by material/type, flagged when defaulted.*

**The no-invent guardrail (applies to every §B calc):** any coefficient not present in the model is fetched from a named, versioned table (e.g. `coeff_thermal.json`, sourced from MS1525 / ASHRAE / Eurocode) and the output row carries a `source` field: either `model` (extracted) or `default:<table>:<row>` (assumed). The UI renders defaulted values in amber. A calc that cannot cite its coefficient **refuses to run** and logs `§REFUSE`.

---

# Part A — View Tools (geometric, ship-ready)

## A1. Find-as-Filter — Isolate (Ghost / Color deferred)

**Status:** Isolate SHIPPED + witnessed (2026-06-04). Ghost + Color **deferred** — see note below.

**Witness W-FILTER-ISOLATE (PROVEN):** Drilling Find to a type and choosing *Isolate* leaves exactly the matched GUIDs visible; the complement is hidden. Proven by `§FILTER visible=<n> hidden=<m> total=<n+m>` where n = match count. Verified headless against live Duplex: `§FILTER visible=427 hidden=695 total=1122` (IfcFlowSegment) + `§FILTER_RESET` on Show all. Spec: `deploy/dev/tests/specs/40-find-isolate.spec.js`.

**Witness W-LEAF-ISOLATE (2026-06-04):** Tapping a **Type leaf** in the outliner tree (Storey→Type
or Discipline→Type) *isolates* that leaf directly — no search, no result list. The leaf builds its
own GUID set scoped exactly to its branch (`building [+ storey] [+ discipline] AND ifc_class`), so
the isolated count equals the leaf's own badge count. Proven by the same `§FILTER visible=<n>
hidden=<m> total=<n+m>` line, where n equals the tapped leaf's count. *Show all*, panel close, and
mode-toggle all clear it (already wired via `filterByGuids(null)`). This replaces the leaf's prior
`runSearch()` behavior (strip-not-add: a tree leaf is a direct view-state, not a search seed).

**DROP-IFC import parity (scope note, user 2026-06-04):** Find-as-filter (search isolate + leaf
isolate) must work identically for buildings brought in via the in-browser **DROP IFC import**
(`deploy/dev/import.js` `A.importIFC` → `import_worker.js` web-ifc → sql.js), not only pre-extracted
served DBs. This is already true: both paths populate `elements_meta`, and the isolate engine only
queries `elements_meta` + `filterByGuids`. **Caveat for the data-gated lenses (§A2 / Task 3):** the
importer is the SECOND producer of the lens data, alongside the Python pipeline. `import_worker.js`
today extracts **no** `spatial_structure` / `rel_contained_in_space` / material associations (verified
2026-06-04 — it only maps `IfcSpace`→discipline and excludes it from render). So a DROP-imported
building will correctly show **no Room/Material lens** until `import_worker.js` carries those tables —
the same non-invent gate as the served DBs, just in the JS extractor instead of the Python one.

**Witness W-FILTER-GHOST (DEFERRED):** *Ghost the rest* sets the complement to xray opacity 0.3 and keeps matches at full opacity. Proven by `§FILTER ghosted=<m> opaque=<n>`.

**Witness W-FILTER-COLOR (DEFERRED):** *Color-by-category* assigns each distinct value of the drilled field one palette color; element count per color sums to total. Proven by `§FILTER colors=<k> sum=<total>`.

> **Scope decision (2026-06-04):** Ghost and Color are **dropped from active scope.** Isolate
> already delivers the original ask ("focus on a group by hiding the rest"). Ghost (keep spatial
> context by x-raying the rest) and Color (all-groups-at-once, color-coded overview) are genuine
> Revit view-states but secondary, and they are the expensive ones — per-element opacity/color on
> shared-material **batched & instanced** meshes (the `filter*` helpers only do per-element
> *visibility*, not appearance). Revisit only on a concrete need: Ghost when you want MEP visible
> *inside* the building shell; Color when you want a discipline/type overview in one glance.

This is the **rule-based filter engine folded into Find** — not a new tool (strip, don't add). It is Revit's *Isolate Element / Hide Unrelated* temporary-view state and its *Filters* dialog, both reached through search instead of a modal.

| | Revit | BIM OOTB Find-as-Filter |
|---|---|---|
| Entry | Separate Filters dialog, separate Isolate command | One Find panel — search *is* the filter |
| Scope | Per-view, trapped in .rvt | The query — shareable as a deep-link URL |
| Rule | Pick parameter from dropdown | `WHERE class=? / material_name=? / storey=? / discipline=?` |
| Color-fill | Filter override per view | One palette color per distinct field value, live |

**Inputs (all verified present):** Find already drills by class/name/storey/discipline (`navigate_find.js:541`) and highlights selection. X-ray already caches unique materials and drops to 0.3 opacity (`tools.js:82`). Color comes from `elements_meta.material_rgba` or an assigned palette.

**Approach (as built):** `A.filterByGuids(set|null)` in `panels.js` — a generic isolate-by-GUID
primitive mirroring `filterStorey`, working across regular/instanced/batched meshes (every element
carries `userData.guid` / `meta.guid`). The Find panel gains an **Isolate / Show all** bar
(`navigate_find.js`) that builds the full GUID set for the current drill (type/storey/name — not
capped at the 50-row result LIMIT) and hands it to `filterByGuids`; resets on open/close/mode-toggle.
Mutually exclusive with the storey/disc filters (cleared first). *Deferred:* URL-hash persistence
for share-by-link.

---

## A1b. Lens Axes — the toggle IS the lens (Room / Material / Phase)

**Status:** SHIPPED to the `deploy/dev` backup (2026-06-04) — render-tested, specs green (40: 4/4, 41: 3/3). NOT yet ported to `bim-ootb/viewer` / deployed.

The Storey/Discipline toggle became a **data-gated axis row** (`_renderAxes()` → `#find-outliner-bar`): **Storey · Discipline · Room · Material · Phase**. Optional axes appear only when their data is present (`_probeLenses()` — the non-invent guarantee, `§LENS_PROBE`). Selecting an axis lists its groups in the tree (each with a SQL-COUNT badge); engine = **UNIFY** (everything via `filterByGuids` / highlight+xray).

- **Room** (W-ROOM-LENS): highlights each IfcSpace as a translucent **volume box** (from per-space bbox `center_*`/`size_*`, `scripts/extract_room_bbox.py`) + **x-rays the rest** (`A.toggleXray`); tap a room → brighten it. `§ROOM_LENS rooms=21 xray=on`, `§ROOM_SELECT`. (Room *mesh* not in served DB — boxes used; the IFC has the geometry, future upgrade.)
- **Material** (W-MAT): groups `material_name`; tap → highlight + x-ray. Rich where extracted (Terminal 43747/48428, Schependomlaan 2838/3284; Duplex sparse 98/1122; SampleCastle/Hospital/Clinic = 0). `§MAT_SELECT`.
- **Phase** (W-PHASE-LAZY): lazily runs the **real** Time Machine generator (`window.tmGenerateTimeline` → `injectGantt` → `kernel_ops`) once, cached; groups by `kernel_ops.parameters.phase` in construction order; tap → highlight + x-ray. `§PHASE_LENS gen=real source=kernel_ops phases=6`, `§PHASE_SELECT`.

**Caveat:** on BatchedMesh geometry the bright outline is batch-level (x-ray dim is per-element); element-precise outline is future work. **Rates:** `SEQUENCE_RULES`/`LABOR_RATES` externalised to `rates/sequence_rules.json` (rates.js loads it, hardcoded fallback). **Next:** register that JSON in the Settings editor (bim-ootb only); strip `rate_per_day` money out of it into the locale cost packs; then cost/qty-in-Find in USD (`rsmeans2024_us.json`).

## A2. Rooms / Spaces / Color-Fill Plans + Area Schedule

**Witness W-ROOM-POLY:** Each enclosed space on a storey yields one closed polygon; polygon area matches the `room_areas` view value within 2%. Proven by `§ROOM space=<guid> poly_area=<a> view_area=<b> delta=<pct>`.

**Witness W-ROOM-FILL:** Color-fill assigns one color per space-type; the legend lists every type with its total area. Proven by `§ROOM types=<k> total_area=<sum>`.

This is our **biggest genuine gap vs Revit** — rooms/areas/color-fill is core FM and area-based costing, and today it is docs-only.

**Inputs — corrected 2026-06-04:** The extractor (`tools/extract.py`) **already captures spaces**:
`IfcSpace` → `spatial_structure` table (extract.py:510), element→space containment →
`rel_contained_in_space` (extract.py:598). The gap is **NOT extractor capability** — it is that
**every currently-served `*_extracted.db` was built without these tables** (verified: Duplex, Clinic,
Hospital, Terminal, Schependomlaan, WBDG_Office, + OCI Duplex all report "no such table:
spatial_structure"). So step 1 is **re-extract** (or fix the pipeline step that drops the tables)
so `spatial_structure` + `rel_contained_in_space` survive into the served DB — assuming the source
IFC actually contains `IfcSpace` (the standard Duplex sample does). Then the viewer reads them.
Wall/door/window **contours** per storey are already extracted by `grid_overlay.js` /
`grid_contours.js`. **Still missing even after re-extract:** closed room *polygons* —
`IfcRelSpaceBoundary` is dropped; polygons must be derived from contours (below).

**Prerequisite for room *isolate* (cheap):** once `rel_contained_in_space` is present, isolating a
room's *contents* is the existing `A.filterByGuids` fed a GUID set from
`SELECT guid FROM rel_contained_in_space WHERE space_guid=?` — no new viewer engine. This is the
smallest room win and reuses A1 directly.

**Approach:** Derive the polygon from contours we already have — close the wall-contour loop per space band (the section-cut Z already used by grid overlay). No new extraction required; this is a geometry-closure step over existing contour data. Once polygons exist: color-fill is canvas fill per `space_type`; area schedule is `GROUP BY space_type` over polygon areas. Feeds 5D area-costing directly.

**Beats Revit by:** the color-fill plan is a shareable URL, recomputes live on section-cut change, and the area schedule is diffable across revisions (Revit's is trapped in the .rvt).

---

# Part B — Long-Tail Analysis (citation-bound)

## B1. Solar / Sun-Hours / Shadow Study *(pure geometric — zero coefficients)*

**Witness W-SOLAR-HOURS:** For a chosen date, hours of direct sun on a selected façade equals the count of sun-positions (stepped at 15-min intervals sunrise→sunset) whose ray reaches the façade un-occluded. Proven by `§SOLAR face=<guid> date=<d> hours=<h> steps_lit=<n>/<total>`.

This is the *cheapest* leapfrog: it needs **no coefficient table at all** — it is pure ray geometry, so it cannot violate non-invent.

**Inputs (all verified):** `site_context.latitude/longitude/elevation` + `true_north_angle` (georeferencing extracted, `federation_preprocessor.py`). Sun position is the standard solar-position algorithm from lat/long + date/time. Occlusion uses the same shadow-map / ray machinery already in `tools.js` shadow mode and the Time Machine sun cycle.

**Approach:** Step the sun across the day; for each step, cast against scene geometry (reuse shadow infrastructure); tally lit steps per selected surface. Output: hours-of-sun number + a heatmap on façades. Revit gates this behind Insight; for us it's a render loop we already run.

---

## B2. MEP Connectivity Trace *(pure graph walk — zero coefficients)*

**Witness W-MEP-TRACE:** From a selected node, *downstream* returns exactly the set reachable by directed edges; an open system (`is_connected=0`) reports the disconnection point. Proven by `§MEPTRACE start=<node> downstream=<n> terminals=<t> open=<bool>`.

This is the **honest MEP analysis** — topology, not flow. No fluid physics, no invented number.

**Inputs (all verified):** `system_nodes`, `system_edges` (directed pipe/duct/wire graph), and `mep_systems.is_connected / is_complete / node_count / edge_count` flags — all already written by `MEPWriter.java`. **Not available:** port semantics (`IfcDistributionPort`) and sizing, so flow/pressure is explicitly out of scope (→ B5 export).

**Approach:** BFS/DFS over `system_edges` from the picked node. Highlight the reachable subgraph in 3D. "What's downstream of this valve," "is this run complete," "which terminals does this riser feed" — all graph queries. Revit needs the full systems model; we answer from topology we already store.

---

## B3. Code-Check — Egress Travel Distance *(geometric, threshold from cited code table)*

**Witness W-EGRESS:** For each occupiable space, the A* path length to the nearest exit is computed; spaces exceeding the jurisdiction limit are flagged. Proven by `§EGRESS space=<guid> dist=<m> limit=<m> source=<code:row> pass=<bool>`.

This is Solibri territory (a separate £k tool) — and we already compute the hard part.

**Inputs (all verified):** `navigate_grid.js` A* pathfinding over the 2D grid (Sections B/B3/B4) and `navigate_path.js` multi-storey composition already exist. The **limit** is the only external number — fetched from the jurisdiction pack (the [DocValidate.md](DocValidate.md) `AD_Val_Rule` + 9-country jurisdiction packs already define this rule infrastructure).

**Approach:** Run the existing A* from each space centroid to the nearest exit door; compare against the cited code limit (e.g. MS1184 / IBC travel-distance). The path *length* is geometric (can't be wrong); the *limit* carries `source=code:<row>` and is never hardcoded. Builds directly on `DocValidate.md` — this is a new rule in an existing engine, not a new engine.

---

## B4. Long-Tail Energy — Degree-Day Heat Loss *(first-order, fully cited)*

**Witness W-HEATLOSS:** Building steady-state heat-loss rate = Σ(U·A·ΔT) over envelope surfaces + ventilation term; every U comes from the model or a cited default. Proven by `§HEATLOSS total_W=<x> envelope_W=<e> vent_W=<v> defaulted=<n>/<surfaces>` and per-surface `§HEATLOSS-U guid=<g> U=<u> A=<a> source=<model|default:coeff_thermal:row>`.

**This is the keystone of the long-tail thesis** and it reuses machinery we already shipped.

**Inputs:**
- **U-values:** [ASSEMBLY_BUILDER_SRS.md](ASSEMBLY_BUILDER_SRS.md) **already computes U from layered assemblies** using a cited conductivity library (its 17 witnesses). Where the model lacks layers, fall back to a typical-construction U keyed by `material_name`/class from `coeff_thermal.json` — flagged `default`.
- **Areas (A):** envelope surface areas from `simple_qto` / bbox geometry (verified).
- **ΔT:** design temperature difference from climate data keyed by `site_context.latitude/longitude` (cited climate table).

**Approach:** Sum U·A over external surfaces, add a ventilation/infiltration term (air-changes default cited by building type), multiply by ΔT. Output a heating-demand estimate + the dominant-loss breakdown ("62% through glazing"). **Refuses and logs `§REFUSE` if it cannot cite a U for a surface.** Defaulted surfaces render amber so the user sees exactly which numbers are assumed vs extracted.

| | Revit + Insight / IES | BIM OOTB long-tail |
|---|---|---|
| Setup | Thermal zones, schedules, weather file, cloud credits | Open the model — U-values already computed |
| Audience | The complex 20% with an energy consultant | The 80% that never modeled energy at all |
| Output trust | Black-box solver | Every U tagged `model` or `default:<source>`, amber when assumed |

**Explicit non-goal:** this is **not** an EnergyPlus replacement and the UI says so. Hourly heat-balance, HVAC sizing, comfort → B5 export.

---

## B5. Export to Validated Engines — Be the Substrate, Not the Solver

**Witness W-EXPORT-GBXML:** Exported gbXML re-imports into a reference energy tool with surface count and total floor area matching the model within 1%. Proven by `§EXPORT format=gbxml surfaces=<n> floor_area=<a>`.

For anything solver-class (full energy, FEA, hydraulics), we **hand off** rather than originate. The right move per the Prime Directive: re-extract the real property sets (today `element_properties` is **2 hardcoded rows** — FireRating, Reference; arbitrary Psets are dropped), then write gbXML / IFC for EnergyPlus, Robot, etc. Best-in-class front-end + clean, traceable hand-off beats a half-trusted in-browser solver that would, by definition, invent numbers.

**Prerequisite (its own spec):** *Pset Harvest* — turn the 2-row `element_properties` table into a full IfcPropertySet / IfcElementQuantity extraction. This is the single highest-leverage extraction upgrade; it feeds B4 (real U-values), B5 (export), and 5D costing.

---

## C. Implementation Order (one bounded task each)

| # | Feature | Why first | Reuses |
|---|---------|-----------|--------|
| 1 | **A1 Find-as-Filter** | Smallest, highest-frequency, foundation for color-fill | `tools.js` xray, `navigate_find.js` |
| 2 | **B2 MEP Trace** | Pure graph walk, zero coefficients, data fully present | `system_edges` |
| 3 | **B1 Solar Hours** | Pure geometry, zero coefficients, reuses shadow infra | shadow maps, `site_context` |
| 4 | **A2 Room Color-Fill** | Closes the real Revit gap; unlocks 5D area cost | `grid_contours.js`, `room_areas` view |
| 5 | **B3 Egress Code-Check** | New rule in existing engine | `navigate_grid.js` A*, `DocValidate.md` |
| 6 | **Pset Harvest** | Unblocks B4 real U-values + B5 export | extractor |
| 7 | **B4 Heat Loss** | Keystone long-tail calc | ASSEMBLY_BUILDER U-values, `coeff_thermal.json` |
| 8 | **B5 gbXML Export** | Substrate hand-off | Pset Harvest |

Each row becomes a coder prompt with a `# ⚠ DO NOT REMOVE` preamble citing its witness above. No implementation precedes its witness claim.

---

*Copyright (c) 2025-2026 Redhuan D. Oon. MIT Licensed.*
