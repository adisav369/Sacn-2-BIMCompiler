# Federation Addon — The Spatial Platform Behind the Compiler

> **Repository:** [red1oon/IfcOpenShell](https://github.com/red1oon/IfcOpenShell/tree/feature/IFC4_DB/src/bonsai/bonsai/bim/module/federation)
> (branch: `feature/IFC4_DB`)
>
> Runs inside [Bonsai](https://bonsaibim.org/) (the open-source IFC addon)
> which runs inside [Blender](https://www.blender.org/) (the open-source 3D platform).

<div class="bim-banner" markdown>
⚡ <b>IFC files don't scale. The FederatedModel DB does.</b><br>
A 30K-element IFC takes minutes to open, spikes RAM, and locks the viewport. Extract once to SQLite — instant preview, GPU-instanced meshes, spatial queries in &lt;100ms. <a href="#federation-vs-traditional-bonsai--file-size-and-viewport-performance">See the numbers →</a>
</div>

---

## What Is the Federation Addon

The Federation addon is a Python module that turns Bonsai/Blender from a
BIM viewer into a **spatial ERP viewport**. It replaces IFC's file-based
access with a [FederatedModel Spatial Database](https://github.com/red1oon/IfcOpenShell/tree/feature/IFC4_DB/src/bonsai/bonsai/bim/module/federation)
— SQLite with spatial indexing — so that geometry becomes queryable.

The [BIM Compiler](index.md) (Java) compiles the [BOM](BOMBasedCompilation.md).
The Federation addon (Python) renders it in the 3D viewport and provides
the interaction layer. Together they bridge the gap between spatial geometry
and ERP data.

These Python extensions were the **proof of concept** — they proved that every
construction dimension (4D scheduling, 5D costing, NLP queries, terrain,
IoT) could be driven from the same spatial database. The Java
[ERP pipeline](MANIFESTO.md) now supersedes them as the production path,
but the Federation addon remains as the viewport and interaction layer.

---

## The nD Dimensions — Each an Extension, Each a Query

Every dimension below reads from the same
[FederatedModel Spatial Database](https://github.com/red1oon/IfcOpenShell/tree/feature/IFC4_DB/src/bonsai/bonsai/bim/module/federation).
The Java [output.db](BOMBasedCompilation.md) is the compiled source of truth;
these extensions are the viewport and export layer.

### 4D + 5D — Schedule and Cost

Construction sequence (BOM tree topological sort) and Bill of Quantities
(price × qty query) are covered in the [**4D/5D Analysis paper →**](4D5DAnalysis.md)

### 6D — Sustainability

Embodied carbon (kgCO2e/m2) and material passport. Each element's carbon
footprint is a property lookup against the component library — another query
on the same compiled data. See [TIER1_SRS.md](TIER1_SRS.md).

### 7D — Facility Management

Maintenance schedules, lifecycle cost, asset register. COBie-compatible
structure from the same BOM tree. See [TIER1_SRS.md](TIER1_SRS.md).

### 8D — ERP Integration

The Java [BIM Compiler](index.md) IS the 8th dimension — the BOM data model
that makes all other dimensions queryable. [C_Order](ProjectOrderBlueprint.md),
[DocAction lifecycle](DocAction_SRS.md), [AD_ChangeLog](MANIFESTO.md#the-application-dictionary-heritage)
— full ERP provenance. The destination is
[iDempiere REST write-back](ProjectOrderBlueprint.md) (Phase H on roadmap).

---

## Beyond nD — Spatial Intelligence Extensions

### NLP Query Engine

**Module:** `federation/dataintelligence/nlp/`

Natural language queries on the spatial database: *"How many beams?"*,
*"Count doors on level 1"*, *"Show ACMV elements"*, *"Total length of pipes in MEP"*.

Converts plain English to SQL via pattern matching against FTS5 full-text
search indexes. No AI model — regex patterns + SQL templates. The same
deterministic approach as the compiler itself.

### Color Studio

**Module:** `federation/color_palette.py`

Construction-theme color palettes for discipline visualization in Blender.
Realistic materials (concrete, wood, steel), discipline coloring
(ARC=white, STR=blue, FP=red, ELEC=yellow), and interactive undo.
The colors you see in the [landing page image](index.md) come from here.

### PDF Terrain

**Module:** `federation/pdf_terrain/`

Survey PDF → 3D terrain. See [PDF_TERRAIN.md](PDF_TERRAIN.md).

### River IoT

**Module:** `federation/river/`

Georeferenced river infrastructure management — equipment placement,
GPS synchronization, sensor tracking, HTML map dashboard. POC on
Klang River, Malaysia (78 equipment markers). A demonstration that
the spatial database pattern extends beyond buildings to environmental
infrastructure.

**Repository:** [river module](https://github.com/red1oon/IfcOpenShell/tree/feature/IFC4_DB/src/bonsai/bonsai/bim/module/federation/river)

---

## The HTML UI — C_Order Flow Manager

**Module:** `federation/webui_sync.py` + BIM Compiler [port 9878](BIM_Designer_UserGuide.md)

The Blender panel UI works for single-user viewport interaction. But
[C_Order](ProjectOrderBlueprint.md) lifecycle management —
Draft → Approve → Complete → Promote — belongs in a web interface where
multiple stakeholders (architect, engineer, QS, project manager) can
participate without installing Blender.

The HTML UI (port 9878) provides:

- **BOM tree** — navigate the compiled building hierarchy
- **DocAction buttons** — [lifecycle state machine](DocAction_SRS.md) (DR→IP→CO→AP)
- **Discipline breakdown** — colour-coded element counts per discipline
- **Validation results** — [AD_Val_Rule](DocValidate.md) compliance status
- **Bidirectional sync** — browser pushes commands to Bonsai, Bonsai renders live

The Federation menu in Bonsai is the **viewport layer** (3D rendering,
placement, visual inspection). The HTML UI is the **ERP layer** (ordering,
approval, audit trail, multi-user access). Same compiled data, two interfaces,
each suited to its audience.

**Where this is heading:** The HTML UI becomes the primary interface for
non-Blender users — the project manager who approves construction orders,
the QS who reviews 5D cost breakdowns, the sustainability officer checking
6D carbon. Bonsai stays for 3D design work. Both read from and write to
the same [output.db](BOMBasedCompilation.md).

---

## Federation vs Traditional Bonsai — File Size and Viewport Performance

**The problem every Bonsai user knows:** open a 30,000-element IFC file. Bonsai
parses every entity in the file sequentially — IfcOpenShell builds 30K Python objects
in RAM, one per element. Then it tessellates every shape individually and writes each
mesh as a separate Blender data block. On a typical workstation this takes **3–8 minutes**,
consumes **8–16 GB RAM**, and leaves the viewport sluggish because the dependency graph
is tracking 30K independent mesh data blocks. Discipline filtering means hiding objects
one by one. Spatial queries don't exist — you iterate Python objects.
The IFC file itself must stay on disk at its original path or the BIM properties panel
breaks. Change anything and you cannot export it back to IFC without the full
IfcOpenShell model still in memory.

Federation extracts the IFC **once** to a SQLite DB (one-time cost, ~20 min for 125K
elements). After that the IFC file is never touched again. Blender loads from the DB
with GPU instancing — one mesh per unique geometry, N objects share it. The same 30K
elements open in **under 30 seconds**, spatial queries run in under 100ms, and the
`.blend` file is self-contained.

### File sizes — real measurements

| Building | Elements | IFC source | Federation `.blend` | Streaming `.blend`² | Traditional Bonsai `.blend`¹ |
|----------|---------|-----------|--------------------|--------------------|------------------------------|
| AC Institute | ~700 | 2.8 MB | **361 KB** | ~80 KB | ~4–8 MB |
| Sample House | ~58 | 2.2 MB | **493 KB** | ~100 KB | ~3–5 MB |
| HospitalGarage | — | 6.2 MB | **1.3 MB** | ~150 KB | ~15–25 MB |
| HHS Office ARC | — | 13 MB | **2.1 MB** | ~200 KB | ~25–50 MB |
| Ifc4 Revit | — | 52 MB | **24 MB** | ~800 KB | ~80–150 MB |
| Hospital (multi-disc) | — | 215 MB | **94 MB** | ~2 MB | ~400–700 MB |
| LTU A-House | 125,997 | 426 MB | **107 MB** | ~3 MB | ~500 MB–1 GB+ |
| **Baku Stadium** ³ | ~500K–1M | ~1–2 GB | ~400–800 MB | **~15–25 MB** | **impractical** |

¹ Indicative — traditional import embeds one mesh block per element with no instancing deduplication. Not directly measured.
² Streaming `.blend` = scene graph only (object transforms + custom properties). Geometry stays in DB.
³ Baku Olympic Stadium — not yet onboarded. IFC and element counts are indicative based on comparable stadium projects. Streaming is the only viable path at this scale — a 400–800 MB embedded `.blend` is unshareable and a 1 GB+ traditional `.blend` is impractical.

The IFC source is a compact text format (CSG/swept solid descriptions). When fully
tessellated to vertex arrays, sizes expand — which is why the federation `.blend` can
still be larger than the source IFC for complex low-repetition geometry. But the
traditional Bonsai `.blend` is always the worst case: every element tessellated AND stored
separately.

### What is inside each `.blend` — and where the LODs live

The LOD meshes (tessellated vertices and faces) **are embedded in the federation `.blend`**,
but in GPU-instanced format: one `bpy.data.mesh` block per unique geometry hash,
shared by all instances of that shape. 500 identical windows = **1 mesh block** in the
`.blend`, 500 object references pointing to it. Traditional Bonsai stores one mesh block
per element regardless — 500 windows = 500 separate blocks.

| Content | Traditional Bonsai import | Federation load |
|---------|--------------------------|-----------------|
| **IFC file** | Path only (not embedded) — must stay on disk | Not involved at all |
| **LOD mesh data** | One mesh block per element, duplicated for repeats | **One mesh block per unique geometry — GPU instanced** |
| **Outliner objects** | IFC entities with BIM properties panel (Pset, Qto…) | Plain Blender objects — `guid`, `ifc_class`, `discipline` as custom props only |
| **Identity per object** | `ifc_definition_id` (volatile numeric, needs IFC in memory) | `guid` string — stable, self-contained |
| **Materials** | Full IfcPresentationStyle → Blender material | `material_rgba` 4-float color from DB |
| **Re-open without source** | Geometry visible, BIM panel needs IFC file present | Geometry visible — DB needed only for filtering/clash panel |

### Can you open the `.blend` without the database?

**Yes.** All mesh geometry is embedded at save time. Objects render immediately.
The federation filtering panel rebuilds its spatial index from the DB if found at the
stored path — if missing, that panel is inactive but the 3D view works fine.

### The IFC export gap — and why it is not a gap for us

**Traditional Bonsai:** edits in the viewport write back to the live IfcOpenShell
model in memory → `File > Export IFC` produces an updated IFC file.
The Outliner shows IFC entities; the BIM properties panel shows Psets.

**Federation:** edits in the viewport write to the `.blend` only.
The Outliner shows plain Blender objects (no BIM properties panel, no Pset sidebar).
There is no IFC file in memory to export to.

This looks like a gap — but it is not a gap **for this workflow**, because we do not
use IFC as the source of truth. The source of truth is the DB. The gap is purely
internal and closes with a single incremental updater script:

```python
# For each modified object in the Blender scene:
guid = obj['guid']                          # already on every object
new_aabb = compute_aabb(obj)                # from modified mesh bounds
conn.execute(
    "UPDATE elements_rtree SET minX=?,maxX=?,minY=?,maxY=?,minZ=?,maxZ=? WHERE id=...",
    new_aabb)
# Optionally: reserialise vertex BLOB back to base_geometries
```

The `guid` custom property set on every object at load time is the stable foreign key.
No IFC round-trip needed. The DB stays the single source of truth; the `.blend` is the
working canvas; the updater syncs the two incrementally.

### Why the viewport is more responsive

Three structural reasons the federation `.blend` handles large models better:

**1. GPU instancing** — Blender sends one mesh to the GPU per unique geometry hash.
500 identical windows = 1 GPU upload, 500 draw calls. Traditional import = 500 uploads.

**2. No IfcOpenShell object tree in RAM** — Traditional Bonsai keeps the entire IFC
entity graph alive in Python (every `IfcWall`, `IfcWindow`, `IfcPropertySet`).
For LTU that is 125K Python objects. Federation: the DB connection closes after
Stage 2 loads. Working set = Blender mesh data only.

**3. Flat custom properties** — Traditional objects carry 20–50 Pset properties each
(`Pset_WallCommon`, `Qto_WallBaseQuantities`, …). Federation objects carry 5 flat
strings. Lighter dependency graph evaluation, faster property panel rendering.

**Blender RAM at runtime** (not `.blend` size) — LTU A-House 125K elements:
13.6 GB with full tessellated meshes loaded, smooth navigation, no crashes.
Equivalent Navisworks/Revit load: 16–30 GB, minutes to open, no spatial queries.

---

## The Federation DB Advantage — MEP, Clash, and Beyond

The `_extracted.db` is not just a viewer asset. Every dimension above runs as
a **SQL query on SQLite** — no IFC file open, no geometry iterator, no RAM spike.
This section documents what the DB enables for MEP coordination specifically and
how it compares to commercial tools.

### What the DB supports today

| Capability | How | Latency |
|---|---|---|
| **Broadphase clash detection** | `elements_rtree` overlap query between discipline pairs | <100ms for 125K elements |
| **MEP conduit routing** | Corridor R-tree query — "what's in this 500mm tunnel?" | <100ms |
| **Bbox Preview Mode** | GPU batch draw of `elements_rtree` quads — no tessellation | <1 frame |
| **Discipline filtering** | `WHERE discipline IN ('PLB','SAN','VENT')` | Instant |
| **Storey-level sequencing (4D)** | `ORDER BY storey` — topological sort on `rel_aggregates` | Instant |
| **Element census / BOQ (5D)** | `COUNT(*) GROUP BY ifc_class, discipline` | Instant |
| **Cross-discipline reporting** | JOIN across any discipline in one DB | Instant |
| **7D asset linkage** | `guid` preserved — JOIN to any FM/CMMS table by GUID | Instant |

All computation runs on the DB. The IFC files are only needed to (re-)extract.

### Competitive comparison

| Tool | Approach | Broadphase speed | RAM required | Decoupled from IFC? | Cost |
|---|---|---|---|---|---|
| **Navisworks** | Full geometry in RAM | Minutes | 16–30 GB | No | $$$$ |
| **Solibri** | Full IFC load | Minutes | High | No | $$$ |
| **BIMcollab** | Cloud geometry upload | Server-side | Server | No | $$ |
| **Trimble Connect** | Cloud full geometry | Server-side | Server | No | $$ |
| **Revit clash** | In-process, full model | Minutes | Very high | No | $$$$ |
| **Our Federation DB** | Pre-baked SQLite R-tree | **<100ms** | **~200MB** | **Yes** | **Free** |

The key differentiator: pre-built spatial index that ships as a standalone SQLite file,
independent of any IFC. Navisworks' bbox mode only activates *after* the full model is
loaded. Ours is the primary access path, not a fallback.

### Preview Mode — unique USP

`bbox_visualization.py` draws all `elements_rtree` rows as GPU wireframe quads in a
single Blender draw call. On LTU A-House (125,997 elements, 8 disciplines): instant.
No geometry loaded, no IFC open. This is the first thing the user sees — before any
tessellated mesh is requested. Not available in Bonsai core, Navisworks, Revit, or any
other BIM tool. See [`docs/LTUAHouseAnalysis.md`](LTUAHouseAnalysis.md).

### Sub-discipline tagging via `--disc-map`

Standard `extractIFCtoDB.py` maps all `IfcFlowSegment / Fitting / Terminal / Controller`
to "MEP". For multi-file projects where each IFC file IS a discipline (PLB, SAN, VENT,
HEAT, HVAC), the per-discipline script overrides by source filename:

```bash
python3 scripts/extract_merge_disciplines.py \
    --ifc-dir DAGCompiler/lib/input/IFC/UNMERGED \
    --pattern "LTU_AHouse_*.ifc" \
    --output DAGCompiler/lib/input/LTU_AHouse_extracted.db \
    --disc-map \
        LTU_AHouse_PLB=PLB  LTU_AHouse_SAN=SAN \
        LTU_AHouse_HEAT=HEAT LTU_AHouse_AIR=VENT \
        LTU_AHouse_DUCT=VENT LTU_AHouse_COOL=HVAC \
        LTU_AHouse_ARC=ARC   LTU_AHouse_STR=STR
```

Full sub-discipline breakdown across all 116K+ MEP elements (125,997 total, 8 disciplines)
— not available in any commercial tool without manual re-tagging in their proprietary format.
See [`docs/LTUAHouseAnalysis.md`](LTUAHouseAnalysis.md) for the full discipline census.

### Proven Scale — LTU A-House (largest reference building)

**LTU A-House** (Lulea University of Technology, Sweden) — 9 IFC2x3 discipline files,
125,997 elements, 8 sub-disciplines (ARC, STR, VOID, VENT, HVAC, HEAT, PLB, SAN).
Largest multi-discipline building onboarded to date.

| Metric | Value |
|---|---|
| Source | 9 IFC files, 400MB+ total |
| Extracted DB | 232.7 MB |
| Extraction time | ~20 min (per-discipline, DB-level merge) |
| Blender/Bonsai RAM | 13.6 GB (full tessellated mesh, all disciplines) |
| 3D navigation | Smooth — no frame drops, no fan noise |
| Element selection | ~3 sec |
| Hide/unhide discipline | Responsive |
| Crashes | None |
| IFC-level merge | OOM at 426MB — DB-level merge is the only viable path |

Onboarding process:
1. Download + rename per convention (`docs/LTUAHouseAnalysis.md` Steps 0-1)
2. Extract per-discipline with `--disc-map` (Step 2)
3. Verify coordinates in metres (Step 3)
4. Verify discipline breakdown (Step 4)
5. Load in Bonsai — immediate preview via rtree, full mesh on demand

Full details: [`docs/LTUAHouseAnalysis.md`](LTUAHouseAnalysis.md)

Java post-processor (`ExtractionPostProcessor.java`) provides automated forensic
verification: unit scale check, discipline coherence vs ARC envelope, per-element
outlier detail with center↔rtree consistency, structured BIMLogger output.

### Shortcomings — and how each closes

All gaps below are closable **without changing the DB schema** — the data needed
is already in the IFC; it just hasn't been extracted yet. Each fix is an addition
to `extractIFCtoDB.py` (read-only, never modify) or a new query/operator on the
existing tables.

**1. Broadphase only — bbox clash has false positives**
*Impact:* Two pipes crossing diagonally may bbox-overlap but not actually clash.

*How to close:* The `base_geometries` table already holds the full tessellated
vertices and faces for every element. Narrowphase is a second pass: load the
geometry for the N candidates from the broadphase result, run mesh-mesh
intersection (OBB or GJK). Only those N candidates hit RAM — the other 124K
elements stay on disk. Wire a `NarrowphaseClashOperator` that takes broadphase
hits as input and re-queries `base_geometries` by `geometry_hash`.

**2. No IfcPropertySet extraction — pipe diameter, pressure, flow rate absent**
*Impact:* Can't verify 50mm clearance; 6D energy and 7D FM incomplete.

*How to close:* Use `ifcopenshell.util.element` API at extraction time:
```python
from ifcopenshell.util.element import get_psets, get_type, get_materials

for element in all_elements:
    psets = get_psets(element)                              # all property sets as dicts
    qtos  = get_psets(element, qtos_only=True)             # quantity sets only
    etype = get_type(element)                               # IfcTypeProduct link
    mats  = get_materials(element, should_inherit=True)     # material layers
```
Store in `property_values` table (`guid, pset_name, prop_name, value, unit`).
For LTU that means pipe nominal diameter, insulation thickness, fire rating —
all already in the IFC, one extraction pass away. `get_type()` also provides
the type→catalog link that Rosetta Stone currently infers.
Diameter then drives exact clearance checks in the narrowphase operator.

**3. No MEP system connectivity — `IfcRelConnectsPortToElement` not extracted**
*Impact:* Can't trace a pipe circuit from inlet to outlet or identify HVAC loops.
RouteWalker currently infers connectivity from spatial proximity (anchor pairs).
Authored port graphs in IFC are a more stable reference when present.

*How to close:* Add a `mep_connectivity` table (`from_guid, to_guid, port_type,
flow_direction, system_name`).
Use `ifcopenshell.util.system` API at extraction time — prefer authored
connectivity over spatial inference:
```python
from ifcopenshell.util.system import get_ports, get_connected_to, get_connected_from, get_element_systems

for element in mep_elements:
    ports = get_ports(element)                    # IfcDistributionPort list
    downstream = get_connected_to(element)        # elements this connects TO
    upstream   = get_connected_from(element)       # elements connecting FROM this
    systems    = get_element_systems(element)       # IfcSystem membership
```
Fallback: when IFC has no ports (common in lower-LOD models), RouteWalker's
spatial proximity inference remains the fallback path — but flag these as
`source='inferred'` vs `source='authored'` in the connectivity table.
The result is a directed graph in SQLite — pipe circuit tracing becomes a
recursive CTE (`WITH RECURSIVE`). No graph database needed. This also unlocks
flow-direction-aware clash checking (upstream vs downstream pressure zones).

**4. No quantities computed — pipe lengths, duct areas, volumes absent**
*Impact:* 5D cost estimation needs lengths; BOQ is element counts only.

*How to close:* The `base_geometries` table has `vertices BLOB` for every
element. Length of a pipe segment = distance between the two end vertices of
its centre-line mesh. Add a `quantities` table (`guid, length_m, area_m2,
volume_m3`) populated at extraction time by iterating the vertex blob.
For straight segments this is a single vector magnitude. For curved ducts,
sum of edge lengths along the spine. All computable in Python/numpy during
extraction — no separate geometry engine needed.

**5. ~~mm-unit IFC2x3 files need manual correction~~ CLOSED**
ifcopenshell `USE_WORLD_COORDS=True` already returns metres regardless of native
IFC units. Verified empirically on LTU A-House (IFC2x3, mm-unit Swedish files):
columns return X=6.0–6.3m, not 6000–6300mm. The previous ×0.001 post-hoc SQL
was causing geometry hell, not fixing it. `fix_mm_outliers()` in
`extract_merge_disciplines.py` handles the rare edge case where
`bbox_from_placement` fallback returns mm (299 elements in LTU STR).
See [`docs/LTUAHouseAnalysis.md`](LTUAHouseAnalysis.md) for details.

**6. 4D/5D needs external schedule and cost linkage**
*Impact:* Construction sequence and BOQ require additional extraction passes.

*How to close:* See [4D5DAnalysis.md](4D5DAnalysis.md) — the `rel_aggregates`
table already encodes BOM precedence; a topological sort generates a default
sequence with zero external input. BOQ is `COUNT(*) GROUP BY ifc_class` with a
price-list join. Both are queries, not features.

---

## Summary — One Database, Many Views

```
                    ┌─ 4D/5D Schedule + Cost  →  4D5DAnalysis.md
                    ├─ 6D Carbon (material passport)
compiled output.db ─┤─ 7D Facility Mgmt (asset register)
                    ├─ 8D ERP (iDempiere write-back)
                    ├─ 2D Drawings (section cuts → SVG)
                    ├─ 3D Viewport (Bonsai/Blender)
                    ├─ NLP Queries ("how many beams?")
                    └─ HTML UI (C_Order lifecycle)
```

The Python extensions proved the concept. The Java ERP pipeline productionised it.
The [Three Concerns](MANIFESTO.md#the-three-concerns) stay separated throughout:
WHAT (orders, categories, products), HOW (BOMs, validation, attributes),
WHERE (output.db for all downstream dimensions).

---

## The Streaming `.blend` — Delivered (S173)

> **Status: LIVE.** The architecture below was proposed and then implemented in
> S169–S173. The federation `.blend` is now meshless — geometry lives in
> `library/library.blend` (pre-baked, linked at runtime). Extracted DBs store
> only transforms and hashes (Hospital: 8MB vs 232MB with BLOBs). Scene saves
> are thin (~116KB) via `strip_template_meshes` / `restore_template_meshes`
> persistent handlers.

### The architecture: separate scene graph from geometry store

A `.blend` file has two conceptually separate things in it:

- **Scene graph** — which objects exist, where they are (transforms), what they are
  (`guid`, `ifc_class`, `discipline`). This is small.
- **Mesh data** — the tessellated vertex/face arrays. This is what makes it large.

The DB holds geometry BLOBs in `component_library.db` (keyed by `geometry_hash`).
These are baked once into `library/library.blend`. At runtime:

```
library.blend  =  pre-baked meshes  (one Mesh per geometry_hash, linked read-only)
.blend scene   =  GN point cloud    (transforms + hash_index, no mesh data)
extracted.db   =  transforms only   (centre, rotation, guid→hash — meshless)
```

Two Blender persistent handlers — registered in `federation/__init__.py` —
keep saves thin:

```python
@persistent
def save_pre(dummy):
    # Before saving: strip GN template meshes (they're linked from library.blend)
    strip_template_meshes()   # scene .blend drops to ~116KB

@persistent
def load_post(dummy):
    # After opening: re-link meshes from library.blend
    restore_template_meshes() # instant — bpy.data.libraries.load(link=True)
```

No BLOB reads at runtime. No `from_pydata()` calls. Meshes are linked from
`library.blend` — one link per unique geometry hash. GPU instancing is preserved
via Geometry Nodes "Instance on Points" —
identical to what Stage 2 does today, but driven from the lightweight scene graph
already in the `.blend` rather than a full DB scan.

### Is it safe, stable, and better?

**Safe — yes, strictly safer than today.**
The DB is the single source of truth for geometry. The `.blend` never holds a
copy that could drift, corrupt, or go stale. If the `.blend` is damaged, the
geometry is intact in the DB. If the DB is updated (new extraction, edit
write-back), every `.blend` that references it picks up the change on next open
automatically — no manual re-export.

**Stable — yes, by design.**
SQLite is ACID. The `geometry_hash` is a content hash of the vertex/face data —
if the hash matches, the geometry is bit-for-bit identical. The link between scene
graph and geometry store is cryptographically stable, not a fragile file path.
The only dependency is the DB file at its stored path — the same dependency
traditional Bonsai has on the IFC file path, but a SQLite file is a single
portable artifact, not a folder of IFC discipline files.

**Better — across every dimension.**

| Concern | Old (embedded BLOB) | Current (library-linked, S173) |
|---------|---------------------|-------------------------------|
| `.blend` on disk | 100+ MB for large buildings | ~116KB scene + library.blend shared across buildings |
| LOD corruption risk | Possible (mesh data in .blend) | Zero — geometry only in library.blend (read-only link) |
| DB update visible | No — stale mesh embedded | Yes — re-bake library.blend, re-link on next open |
| Shareable `.blend` | Large file transfer | Send the tiny scene graph; library.blend stays local |
| Extracted DB size | 232MB Hospital (with BLOBs) | 8MB Hospital (meshless — hashes + transforms only) |
| GPU instancing | Preserved (mesh cache) | Preserved — GN Instance on Points |
| Load time (18K meshes) | ~13s (BLOB unpack + from_pydata) | <0.1s (library.blend link) |

### How much smaller

The bulk of every `.blend` is mesh data. Object transforms, custom properties,
materials, and scene settings are small. Stripping mesh data leaves only the
scene graph:

| Building | Elements | Current `.blend` | Streaming `.blend` | Reduction |
|----------|---------|-----------------|-------------------|-----------|
| AC Institute | ~700 | 361 KB | ~80 KB | ~80% |
| Sample House | ~58 | 493 KB | ~100 KB | ~80% |
| HospitalGarage | — | 1.3 MB | ~150 KB | ~89% |
| HHS Office ARC | — | 2.1 MB | ~200 KB | ~91% |
| Ifc4 Revit | — | 24 MB | ~800 KB | ~97% |
| Hospital (multi-disc) | — | 94 MB | ~2 MB | ~98% |
| LTU A-House | 125,997 | 107 MB | ~3 MB | ~97% |

The streaming `.blend` size is dominated by the object count (one transform matrix +
five custom property strings per object), not the geometry. It scales with element
count, not geometric complexity.

### Is it faster?

**Open time: same.** `load_post` fetches every geometry BLOB from DB, unpacks
float32 arrays, and builds meshes — identical data volume to Stage 2 today.
No free lunch here.

**Save is 50–200× faster.** Blender serialises 100+ MB of mesh data to disk today.
With streaming it writes kilobytes — object transforms and custom properties only.
A 94 MB Hospital save becomes a 2 MB write.

**Portability and sharing are transformed.** The `.blend` is now a lightweight scene
configuration — email it, commit it to git, put it in a shared drive, send it over
Slack. A 125K-element LTU A-House goes from a 107 MB attachment nobody can send to a
3 MB file anyone can open in seconds. The geometry stays in the DB, which lives on
a shared drive, a local server, or alongside the `.blend` — one SQLite file, not a
folder of IFC disciplines.

**Download speed follows.** A team member pulling the latest design scene gets 3 MB
instead of 107 MB. On a 10 Mbps connection: 0.3 seconds instead of 90 seconds.
The DB itself only needs to transfer once — subsequent `.blend` updates are scene
graph diffs only.

**Safety through separation.** The LODs cannot be corrupted by a bad save, a crashed
Blender session, or a botched file transfer — because they are not in the `.blend`.
The DB is the single source of truth; the `.blend` is recoverable. Lose the `.blend`,
re-run Stage 2 against the DB — full scene rebuilt in 9–27 sec, geometry intact.
Lose nothing of the geometry. Compare to traditional Bonsai: lose the `.blend` there
and you need the IFC file present to reimport from scratch.

**Two geometry sources, one fetch path.** Extracted buildings (stadiums, hospitals,
airports) store unique element geometry in `extracted.db → base_geometries`.
Generative buildings store standard product geometry in
`component_library.db → component_geometries`. Both use the same `geometry_hash`
key and the same binary BLOB format — `load_post` checks `extracted.db` first,
falls through to `component_library.db`. For a stadium like Baku Olympic, most
elements are bespoke (curved trusses, unique façade panels) — low instancing ratio,
small `component_library.db`, bulk in `extracted.db`. Streaming helps most here
precisely because the unique geometry would otherwise bloat the `.blend` the most.

**Cloud geometry library on OCI.** Because the DB is a single SQLite file and
`load_post` fetches it once per session, the geometry store can live anywhere —
including Oracle Cloud Infrastructure object storage. The `.blend` holds only the
scene graph; `load_post` downloads the DB on first open, caches it locally, and
every subsequent session reads from the local cache. The cloud copy is the master;
local is a read-through cache.

This turns `component_library.db` into a **shared industry geometry library**:
one authoritative copy of 2,475+ products (walls, slabs, fixtures, MEP devices),
maintained centrally, versioned, downloaded once per workstation. Every team member
opening any `.blend` gets the same certified LOD meshes — no per-project geometry
copying, no version drift between offices. A geometry update on OCI propagates to
every workstation on next open, automatically.

```
OCI Object Storage
  └── component_library.db  (master, versioned)
        ↓  download once → local cache
  Blender load_post
        ↓  geometry_hash lookup
  Streaming .blend  (3 MB scene graph, shared by git/email)
```

The `.blend` file becomes what a web page is to a browser — a lightweight document
that references assets, not a monolithic bundle that contains them.

### Is it safe to implement?

Yes — with one guard. `save_pre` must not leave objects meshless if the save
crashes mid-write. The fix: keep an in-memory mesh cache and restore it in
`save_post`:

```
save_pre  → strip mesh to stub, cache {geometry_hash: mesh} in memory
Blender   → writes thin .blend (can crash here — in-memory cache intact)
save_post → restore mesh from cache → viewport never loses geometry
```

If the DB is missing on `load_post`, objects get empty stub meshes — no crash,
just invisible geometry. Fully graceful.

### Does it touch Bonsai core?

**No.** `bpy.app.handlers.save_pre`, `save_post`, and `load_post` are
addon-level hooks — standard Blender API. All changes land in
`federation/__init__.py`, which already registers `load_post` handlers today.
Zero changes to Bonsai core, zero risk to the existing Bonsai IFC workflow.

### Fallback — existing `.blend` files and small projects work unchanged

Detection is one line per object:

```python
if obj.get('geometry_hash') and len(obj.data.vertices) == 0:
    # streaming .blend — stub mesh, rehydrate from DB
else:
    # legacy embedded .blend — mesh already present, leave as-is
```

Old `.blend` files with embedded LODs open exactly as before. The mesh presence
IS the mode flag — no version field needed.

**Size-based auto-switch** — streaming activates automatically above a threshold:

```python
STREAMING_THRESHOLD = 50_000  # elements — configurable

def should_stream(db_path: str) -> bool:
    count = sqlite3.connect(db_path).execute(
        "SELECT COUNT(*) FROM elements_meta").fetchone()[0]
    return count >= STREAMING_THRESHOLD
```

Below 50K elements: Stage 2 embeds meshes as today — fast open, works fully
offline, no DB dependency at runtime. Above 50K: streaming mode activates,
scene graph only, DB stays the geometry store. The threshold is one config
line — tune it per deployment.

This means small projects (SH, DX, FK) are unaffected. Large projects (Hospital,
LTU, Baku-scale stadiums) get streaming automatically. Backward compatibility
is unconditional — it is structurally impossible to break an existing `.blend`
because the switch reads mesh presence, not a stored flag.

### TODO 1 — Streaming `.blend` implementation

**Implementation:** [`prompts/S162_streaming_blend.md`](https://github.com/red1oon/BIMCompiler/blob/master/prompts/S162_streaming_blend.md)

Three handlers in `federation/loading/streaming_blend.py`, registered in `federation/__init__.py`:

| Handler | Trigger | What it does |
|---------|---------|-------------|
| `blend_save_pre` | Ctrl+S / File > Save | Syncs viewport edits to DB via `guid`; strips mesh to stub |
| `blend_save_post` | After save completes | Restores mesh from in-memory cache — viewport unaffected |
| `blend_load_post` | File open | Detects streaming stubs; rehydrates from DB (dual-source) |

**Dual-DB fetch** — `load_post` checks both sources, same `geometry_hash` key:

- `extracted.db → base_geometries` — extracted building elements (walls, slabs, beams)
- `component_library.db → component_geometries` — generative products (FRIDGE, SWITCH…)

**Size-based auto-switch** — streaming activates only above threshold:

```
< 50K elements  →  embedded mode (Stage 2 as today, works fully offline)
≥ 50K elements  →  streaming mode (scene graph only, DB stays geometry store)
```

Threshold is one config line. Small projects (SH, DX, FK) unaffected.
Backward compat unconditional — mesh presence is the mode flag.

**The twin loop** — save = sync, no separate updater step:

```
viewport edit  →  Ctrl+S  →  save_pre: sync DB + strip mesh  →  3 MB .blend written
compiler run   →  new output.db  →  load_post rehydrates     →  .blend reflects truth
```

### TODO 2 — IFC export (custom exporter, not Bonsai native)

Bonsai's native `File > Export IFC` requires IfcOpenShell to hold the model
in memory with every entity linked via `ifc_definition_id`. Federation objects
have no `ifc_definition_id` — they are plain Blender objects. The native
exporter does not see them as IFC entities. IFC export is **not automatic**.

It is achievable via a custom exporter — all ingredients present:

```python
import ifcopenshell
model = ifcopenshell.file(schema="IFC2X3")
for obj in bpy.data.objects:
    if obj.get('guid') and obj.get('ifc_class'):
        entity = model.create_entity(obj['ifc_class'], GlobalId=obj['guid'])
        # attach tessellated geometry from base_geometries via geometry_hash
```

`guid`, `ifc_class`, and DB geometry are on every object. It is a dedicated
build, not a freebie from having objects in the Outliner.

### The workflow this enables

```
extract IFC → extracted.db     (once)
              ↓
         open .blend            (load_post rehydrates from DB)
              ↓
         edit in viewport       (moves, deletions, material changes)
              ↓
         Ctrl+S                 (save_pre: DB synced + mesh stripped → 3 MB file)
              ↓
         next open              (load_post sees updated DB → reflects edits)
```

The `.blend` is a **session configuration**, not a geometry archive.
The DB is the geometry archive, always current, never duplicated.

---

**→ Implementation spec:** [`prompts/S162_streaming_blend.md`](https://github.com/red1oon/BIMCompiler/blob/master/prompts/S162_streaming_blend.md)

### TODO 3 — Demo Mega-DB (Baku-scale proof without Baku)

Baku Olympic Stadium IFC is not yet available. But we already have enough extracted
buildings to assemble a demo database that proves streaming at comparable scale —
and tells a better story: a **real mixed-use precinct** on terrain.

**Available assets:**

| Source | File | Elements | Size |
|--------|------|---------|------|
| Hospital (multi-disc) | `Hospital_extracted.db` | ~60K | 124 MB |
| LTU A-House | `LTU_AHouse_extracted.db` | 125,997 | 233 MB |
| Clinic (federated) | `Clinic_extracted.db` | ~16K | 56 MB |
| HITOS | `HITOS_extracted.db` | — | 5.9 MB |
| Hospital Auckland | `HospitalAuckland_extracted.db` | — | 4.7 MB |
| Hospital Garage | `HospitalGarage_extracted.db` | — | 1.7 MB |
| PDF Terrain | `pdf2blend/samples/sample_output.json` | — | topography mesh |

**Combined: ~200K+ elements, ~425 MB source DBs.**
Streaming `.blend` target: **under 10 MB**.

**Implementation: a config-driven merge script**

```yaml
# demo_precinct.yaml — which buildings go where
precinct_name: Demo Medical Precinct
output_db: demo_precinct_merged.db
buildings:
  - source: Hospital_extracted.db
    label: Main Hospital
    offset_x: 0
    offset_y: 0
  - source: HospitalGarage_extracted.db
    label: Parking Garage
    offset_x: 120
    offset_y: 0
  - source: Clinic_extracted.db
    label: Outpatient Clinic
    offset_x: 0
    offset_y: 80
  - source: HITOS_extracted.db
    label: HITOS Block
    offset_x: 200
    offset_y: 0
  - source: HospitalAuckland_extracted.db
    label: Auckland Wing
    offset_x: 0
    offset_y: 200
terrain:
  - source: pdf2blend/samples/sample_output.json
    label: Site Terrain
    type: json_elevation_grid
```

The merge script reads the YAML, applies XY offsets to `element_transforms` and
`elements_rtree`, and writes a single `demo_precinct_merged.db`. Discipline tags
and GUIDs are preserved — prefixed with the source label to avoid collisions.

**What this demonstrates:**
- Streaming `.blend` at 200K+ elements → under 10 MB file
- Mixed disciplines across buildings in one queryable DB
- Terrain + buildings in the same spatial index
- Broadphase clash detection across building boundaries
- OCI upload of `demo_precinct_merged.db` as the shared cloud geometry library

**→ Implementation spec:** `prompts/S163_demo_precinct.md` (to be written after S162)

---

## Appendix: 5M Scale Path (Design — post S162)

**Prerequisite:** S162 streaming `.blend` implemented and validated.
**Status:** Design only. No schema exists yet. No implementation prompt written.

The streaming pattern extends naturally to 5M elements with one architectural shift:
above a threshold, stop creating Blender objects entirely — use pure GPU bbox draw
from the R-tree, and load full meshes only for a user-selected focus zone.

### Three-tier model

| Mode | Objects in Blender | Use case |
|------|-------------------|----------|
| Normal (<50K) | Stubs → rehydrated on open | S162 as-is |
| Hybrid (50K–500K) | Stubs + GPU bbox overlay | Large buildings |
| Navigation (>500K) | Zero objects — GPU only | Stadiums, airports |

### Navigation mode

- No `bpy.data.objects` created on open — Outliner stays empty
- GPU draw handler queries `elements_rtree` per frame, frustum-culled
- Single instanced draw call; discipline colours from `bbox_visualization.py`
- Click selection via SQL R-tree candidate filter + ray–bbox test (not Blender raycast)

### Focus mode

- User selects zone (storey, discipline, bounding box, or search)
- System loads LOD meshes only for selected elements via modal timer
- On exit: all focus-zone objects deleted, navigation mode restored

### Storage concern (honest)

At 5M elements with full LOD0/1/2 meshes the DB exceeds SQLite practical limits
(~40 GB). Options: PostgreSQL for >2M, or store only LOD1/2 and generate LOD0
on demand from the BIM Compiler.

### Known limitations

- No Outliner in navigation mode — use BIM Compiler web UI for element browsing
- Full mesh editing capped at ~50K elements per focus session
- Blender 4.0+ required (GPU instancing API)

**→ Implementation prompt:** to be written as a new S1xx after S162 is live.

*Copyright (c) 2025-2026 Redhuan D. Oon. MIT Licensed.*
