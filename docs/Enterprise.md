# Federation Addon — The Spatial Platform Behind the Compiler

> **Repository:** [red1oon/IfcOpenShell](https://github.com/red1oon/IfcOpenShell/tree/feature/IFC4_DB/src/bonsai/bonsai/bim/module/federation)
> (branch: `feature/IFC4_DB`)
>
> Runs inside [Bonsai](https://bonsaibim.org/) (the open-source IFC addon)
> which runs inside [Blender](https://www.blender.org/) (the open-source 3D platform).

<div class="bim-banner" markdown>
<b>Bonsai/Blender as a spatial ERP viewport, not just a BIM viewer.</b> Python addon replacing IFC file access with a FederatedModel Spatial Database for queryable geometry.
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

### 4D — Construction Schedule

**Module:** `federation/schedule/`

The construction sequence is a [topological sort](https://en.wikipedia.org/wiki/Topological_sorting)
of the BOM tree. Footings before columns, columns before beams, beams before
slabs, slabs before MEP rough-in. The BOM's parent-child relationships already
encode precedence — the 4D schedule is a **query** on that tree, not a
separate model.

| Feature | How |
|---------|-----|
| Sequence generation | IFC class → phase → predecessors (precedence rules) |
| Duration calculation | Element quantity × labor productivity rates from [5D BOQ](#5d-bill-of-quantities) |
| Animation | Blender timeline keyframes — elements appear in construction order |
| Export | Excel, MS Project (.mpp) |

**ERP parallel:** This is what [Primavera](https://www.oracle.com/construction-engineering/)
does — but here the schedule comes from the BOM itself, not from a separate
scheduling tool. [ProjectOrderBlueprint.md §5.1](ProjectOrderBlueprint.md#51-4d-schedule-topological-sort-of-bom-tree).

### 5D — Bill of Quantities / Cost

**Module:** `federation/boq/`

Every [M_Product](DATA_MODEL.md) has a price. The cost of a building is
`SUM(price × qty)` — a query, not a feature. The BOQ module exports this
with CIDB Malaysia 2024 labor rates and 3-component cost breakdown
(material + labour + equipment).

| Feature | How |
|---------|-----|
| Quantity extraction | IFC element volumes, areas, lengths from spatial DB |
| Cost breakdown | Material + labour + equipment per element type |
| Labor rates | CIDB Malaysia 2024 productivity data |
| Export | Excel with discipline/storey/type grouping |

**ERP parallel:** This is [M_PriceList](https://wiki.idempiere.org/en/M_PriceList)
applied to construction. [ProjectOrderBlueprint.md §5.2](ProjectOrderBlueprint.md#52-5d-cost-inherent-in-the-data-model).

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

## Summary — One Database, Many Views

```
                    ┌─ 4D Schedule (topological sort)
                    ├─ 5D Cost (price × qty query)
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
