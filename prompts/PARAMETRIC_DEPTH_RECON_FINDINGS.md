# PARAMETRIC-DEPTH RECON — FINDINGS (2026-07-05)

Recon-only, no code. Answers the 4 rescoped questions in `PARAMETRIC_DEPTH_RECON.md` (rescoped from
`PREFAB_LASSO_MACRO_LIBRARY_DIALOGUE.md §5`). Same rigor bar as `UBBL_RULES_RECON.md` — every claim
cites file:line / table:column; each finding tagged MEASURABLE-TODAY or BLOCKED-ON-`<named-gap>`.
Model: Sonnet 5, single sequential pass (per the master-file model reassessment). **It caught a real
repo landmine (Q3) of the same shape UBBL did.**

Citations use absolute paths: `/home/red1/bim-compiler/library/component_library.db`,
`/home/red1/bim-ootb/modeller/{bom_tree.js,bonsai_library.js,modeller.html,disc_walker.js,walker_confidence.js}`,
`/home/red1/bim-ootb/buildings/*_extracted.db`.

---

## Q1 — Per-class REAL-VARIANCE dimension ranges
**BLOCKED-ON-missing-aggregation-step** (raw per-instance data exists; the aggregated range fact does NOT).

- `component_library.db:component_definitions` stores **per-instance** local bboxes only
  (`local_min_x/max_x/…z`), 23,888 rows. Ad-hoc query for `IfcDoor` (`component_types.id=15`) → 129 distinct
  rows spanning local width **0.147–1.86m** — the raw variance is IN the rows.
- `ad_product_dim` (52 rows) stores exactly ONE `width/depth/height` per `product_id` — **no min/max columns**.
- `ad_space_dim.min_width/min_depth/min_height` are **regulatory design minimums** ("Size constraints"),
  NOT measured-instance ranges — a trap: do not mistake this for variance.
- No SQL view aggregates by `type_id` (`sqlite_master where type='view'` → empty).
- `disc_walker.js:1012` (copy `bim-compiler/build/disc_walker.js:1001`) is the only walker MIN/MAX — it's the
  building overall bbox, not per-class dimension variance. `walker_confidence.js` (WC_CALIBRATION/PAV) computes
  classification-confidence curves — a different axis (the dialogue's "plugs into calibrated-confidence" is about
  *reusing* it later, not evidence it already covers variance).
- `tools/grammar_checker.py:294`, `tools/rosetta_trainer.py:121` compute `(local_max_x-local_min_x)` at runtime
  for one-off matching — an average, never persisted.

→ A small NEW aggregation pass (`GROUP BY type_id` over `component_definitions.local_min/max` → persist min/max)
is required before an LOD touch-up axis can be called "real, not assumed." Raw rows to do it from already exist.

## Q2 — BOM-node granularities as shell-pick units
**MEASURABLE-TODAY: Building → Storey → Room, populated corpus-wide. No coarser level exists anywhere (incl. Terminal).**

- `modeller/bom_tree.js:44-116` builds the tree from `spatial_structure` (Building/Storey/Space) +
  `rel_contained_in_space`; qualifies a room only when real (door-count + habitable-height AABB gate, :69-105),
  degrades to storey-only, never fabricates.
- `rel_contained_in_space` populated in **all 5** DBs: Duplex 22 / LTU_AHouse 1608 / Terminal 2181 /
  Hospital 8474 / Clinic 2133 rows.
- `spatial_structure.type` is **only ever** `IfcBuildingStorey`/`IfcSpace` in every DB — Terminal has the same
  two levels, nothing coarser. `buildings/city_index.db` + `city_index_v2.db` (the only coarser-sounding names)
  are **0-byte empty stubs**.
- `elements_meta.storey` 100% populated in all 5 DBs.
- `bom_tree` table exists ONLY in Duplex_extracted.db — not universal, so not part of the "everywhere" answer.

→ DAG-guided free lasso only adds value for **sub-room** selections (a subset of one room). It does NOT solve a
gap at whole-room-or-coarser grain — those picks are already real and corpus-wide.

## Q3 — Material/finish: real vs unpopulated slot
**MEASURABLE-TODAY — populated, not a schema-only slot. ⚠ BUT ships a landmine (below).**

- `ad_element_placement` — 67,332 rows; `material_name` populated 41,663 (62%), `material_rgba` 41,631.
- `material_layers` (60 rows) real Revit layer sets (e.g. `Basic Wall:Wall-Partn_12P-70MStd-12P|…|Plaster|12.5`).
- `surface_styles` (80 rows) real RGB + provenance (`Brick, Common|…|EXTRACTED:Ifc4_SampleHouse.ifc`).
- `ad_element_rule` — 1,263 rows, 224 w/ `material_name`, 252 w/ `material_rgba`.
- Modeller side: `elements_meta.material_name/material_rgba` exist directly in all 5 corpus DBs.

### ⚠ LANDMINE (UBBL-shape) — double-labeled Terminal building
`ad_element_placement.building_type` carries **two labels for one physical building**:
- `SJTII_Terminal` — 51,088 rows, 41,399 material-populated = **81%**, has `ad_building id=3 'SJTII Airport Terminal'`,
  `source='[EXTRACTED: SJTII_Terminal]'`.
- `TERMINAL` — 15,104 rows, only 236 populated = **1.6%**, **NO** matching `ad_building` row (orphaned),
  `source='[EXTRACTED: TERMINAL]'`.

"Is material populated for Terminal?" answers **81% vs 1.6%** depending which string you query — exactly the
shape of trap the UBBL recon caught. Anyone building a material-conform gate against `building_type='TERMINAL'`
would see near-empty data and wrongly conclude material is unpopulated.

**Re-checked 2026-07-05 (§3.5 of `SCALE_AND_UX_SWEEP.md`) — reconciled, not just re-confirmed:**
- **Why the orphan exists (not guessed — read from the data):** `TERMINAL` occupies `placement_id` 33684-48787
  (the OLDER range) vs `SJTII_Terminal`'s 48788-100506 (inserted later) — `TERMINAL` is a superseded legacy
  extraction pass, heavy on MEP classes (`IfcPipeFitting`/`IfcPipeSegment`/`IfcFireSuppressionTerminal`/
  `IfcDuctFitting` are its top 4), with 0 rows carrying a `building_id` FK. `SJTII_Terminal` is the later, fuller
  re-extraction that properly links `building_id` and captures real material data — `TERMINAL` was never cleaned
  up after the re-extraction superseded it.
- **Confirmed dormant, not live:** grepped every `.java`/`.js`/`.py` file in both bim-compiler and bim-ootb for
  `ad_element_placement` queries filtered on the literal `TERMINAL` string — zero hits. (Note: `TERMINAL` as a
  building_type DOES legitimately appear elsewhere, e.g. `migration/TRM001_terminal_measured_rules.sql`'s
  `ad_mep_pattern` rows — that's a different table with its own naming convention, not the same landmine.) So
  today this is a dormant data-hygiene landmine, not an active bug — safe to leave undeleted (deleting 15,104
  rows from the shared `component_library.db` is an irreversible action on a file with its own unrelated pending
  edit, not something to do inside a verification sweep) but now documented with root cause, not just symptom.

## Q4 — Real BOM-assembly-parent grouping for furniture SETS
**BLOCKED-ON-missing-group-linkage** (fix is small; a proven pattern already exists in the SAME file).

- `bonsai_library.js:140-198` (`expandAssembly`) + `:317-333` (`dropLeaves`) return leaf lists
  `[{hash,x,y,z,rot,role}]` — **no assembly_id / group-id / GUID prefix** per leaf.
- `modeller.html:2690-2696` (`placeAssembly`, the real furniture/BOM-set drop commit): each leaf commits as an
  **independent unlinked** `GEOM_INSERT` signed op-row. Table+chairs → N separate rows, nothing tying them back.
- **Contrast, same file:** `modeller.html:3403-3436` (`_commitDiscWalk`) batches N placements into ONE signed
  group via `O.commitSeedGroup(ops, 'dwwalk-'+disc+'-'+O.length)` — a proven shared-group-id mechanism, just
  NOT applied to the assembly-drop path.
- `component_library.db:ad_assembly_manifest` (37 rows) / `ad_assembly_connector` (10 rows) are **type-level**
  interface specs (face/interface_type/clearance_m; connector pos/diameter), keyed by `assembly_id+version` —
  they describe how assembly TYPES connect, not which committed op-rows belong to one physical drop.

→ The "dining set" escalation tier is NOT real today. Per the dialogue's own rule ("skip any tier not backed by
a fact"), skip it until `placeAssembly` adopts the `commitSeedGroup` pattern already proven for disc-walk.

---

## Build-scoping note (§1–§4 of PREFAB_LASSO_MACRO_LIBRARY_DIALOGUE.md)
- **§1 (LOD touch-up, mined-variance axes):** NOT buildable yet — needs the new aggregation pass from Q1
  (persist per-class range). Gap = aggregation, NOT new extraction (raw instances exist).
- **§2 / §2a (ARC-shell BOM pick + DAG-guided lasso):** prerequisites ALL already real (Q2 room/storey
  corpus-wide; typed-edge graph already live per the dialogue). Buildable today as a UI/interaction layer;
  whether free-lasso beats "just pick the room" is a judgment call, not a data gap.
- **§2b-3 (material-wise conform):** the dialogue flagged this UNVERIFIED — this recon **resolves it**: material
  is real+populated (Q3), path cleared to build (still needs the conform-transform logic; NOT blocked on data).
  ⚠ but build the gate against `SJTII_Terminal`, never the orphaned `TERMINAL` label.
- **§3 (escalating lasso ladder):** "all chairs / all furniture in room" tiers buildable today (Q2). The
  "dining set" (assembly-parent) tier BLOCKED per Q4 — skip until the group-id fix lands.
- **§4a (static clipboard):** buildable today, independent of all gaps.
- **§4b (macro-capture twig):** underlying primitive (`commitSeedGroup`) exists+proven; §4b itself is genuine
  new engineering per the dialogue, not blocked on data.

## Net
Two BLOCKED-ON-named-gap findings (Q1 aggregation, Q4 group-linkage — both small, both with an existing pattern
to copy), two MEASURABLE-TODAY (Q2, Q3). One real landmine surfaced (Q3 Terminal double-label) that would have
poisoned a §2b material-conform build. See [[project_parametric_depth_recon_landmine]].
