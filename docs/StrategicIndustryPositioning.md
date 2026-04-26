# From Exchange Format to Working Format: Compiling IFC into Relational Databases

<div style="max-width: 620px; margin: 32px auto; padding: 24px 40px; background: #263238; border-left: 4px solid #ff9800; text-align: center; border-radius: 4px;">
<span style="font-size: 1.3em; line-height: 1.7; color: #eceff1; letter-spacing: 0.3px;">We BIM living the <b style="color: #ff9800;">GAP</b> between<br><b style="color: #ff9800;">DESIGN</b> and our <b style="color: #ff9800;">SPREADSHEET</b></span>
<br><span style="font-size: 0.75em; letter-spacing: 1.5px; text-transform: uppercase; color: #78909c; margin-top: 12px; display: inline-block;">The industry designs buildings. Nobody compiles them.</span>
</div>

---

## Abstract

IFC (Industry Foundation Classes, ISO 16739) is the open standard for exchanging
building information. It is widely adopted for *transferring* models between
tools. It is rarely used as a *working format* — a queryable, composable,
version-controllable substrate for downstream operations like scheduling, costing,
compliance verification, and procurement.

This paper describes an approach that treats IFC not as a file to open but as
source code to compile. A 12-stage pipeline extracts IFC into four normalised
SQLite databases where every element is a row, every relationship is a foreign
key, and every spatial claim is machine-verifiable. A 9-gate proof strategy
(Rosetta Stone) ensures that geometry from heterogeneous sources — different
tools, origins, units, coordinate systems — lands correctly in a unified schema.

The result is a relational BIM: IFC semantics expressed as SQL, consumable by
any tool that reads a database.

---

## 1. Background: IFC as Exchange Format

**BIM** (Building Information Modelling) is the practice of designing buildings as
structured digital models. Every wall, door, pipe, and beam is an object with
geometry, material, type, and relationships.

**IFC** (ISO 16739) makes BIM data portable. An architect exports an IFC file — a
vendor-neutral snapshot of the building expressed in STEP/EXPRESS format
(ISO 10303-21). A typical export contains 30,000–120,000 entities: spatial
structure, element types, property sets, geometry representations, and material
associations.

**BIM dimensions** extend the model beyond 3D geometry:

| Dimension | Covers | IFC Support |
|-----------|--------|-------------|
| 3D | Geometry and visualisation | Core schema (IfcShapeRepresentation) |
| 4D | Construction scheduling | IfcWorkPlan, IfcTask, IfcRelSequence |
| 5D | Cost and quantity takeoff | IfcQuantitySet, IfcCostValue, IfcCostSchedule |
| 6D | Sustainability and energy | IfcPropertySet (custom), emerging IFC4x3 |
| 7D | Facility management | IfcAsset, IfcServiceLife, IfcMaintenanceWorkOrder |

IFC is designed to carry all of this. The problem is not the schema — it is the
format. STEP files are flat serialisations of an object graph. Querying them
requires parsing the entire file, resolving inverse relationships, and navigating
a deeply nested type hierarchy. This is tractable for a viewer or a single-purpose
extraction tool. It does not support the kind of ad-hoc queries, joins, diffs,
and aggregations that downstream operations require.

BIM adoption exceeds 70% in regulated markets (UK, Germany, Nordics), driven by
government mandates ([buildingSMART 2025](https://www.buildingsmart.org/wp-content/uploads/2025/03/IFC-Mandate_2025.pdf)).
The global BIM market reaches $15.4B by 2030 (CAGR 11.3%).

---

## 2. The Problem: Geometry as Source of Truth

Every major BIM tool stores **geometry as the source of truth**. The IFC file IS
the building — geometry, relationships, properties, all in one monolithic file.

This creates structural problems:

**Version control.** Binary diffs of 200 MB STEP files are meaningless. Teams
use filename-based versioning ("v3_final_FINAL.ifc"), not semantic diffs. Git
cannot meaningfully track changes.

**Collaboration.** Every stakeholder receives the entire model. Changing one
room width requires exchanging the full file.

**Variants.** 100 design options require 100 copies of the full geometry.

**Compliance verification.** Regulatory reviewers receive geometry and must
reverse-engineer the designer's intent. There is no machine-readable proof that
spatial constraints are satisfied.

**Querying.** Asking "which ground-floor walls use concrete?" requires parsing
the full IFC graph, navigating IfcRelContainedInSpatialStructure, IfcRelAssociatesMaterial,
and IfcBuildingStorey chains. In a relational database, it is one SQL join.

---

## 3. The Approach: IFC → Relational Database

The BIM Intent Compiler treats IFC as input to a compilation pipeline. The output
is four normalised SQLite databases:

| Database | Contents | Role |
|----------|----------|------|
| `{PREFIX}_extracted.db` | Elements, transforms, storeys, disciplines, properties | The building as structured data |
| `component_library.db` | Geometry BLOBs (vertices + faces), keyed by hash | Shared geometry catalog |
| `{PREFIX}_BOM.db` | Bills of material, product catalog, attribute sets | What to build, how to configure |
| `output.db` | Compiled orders, schedules, cost rollups | ERP-consumable output (4D–7D) |

After extraction, every element is a row in `elements_meta`:

```sql
SELECT guid, ifc_class, element_name, storey, discipline, material_rgba
FROM elements_meta
WHERE storey = 'Ground Floor' AND discipline = 'ARC'
ORDER BY ifc_class;
```

This is the central contribution: **IFC becomes SQL-queryable.** Spatial
containment, material assignment, discipline classification, and storey
membership are all columns and foreign keys. Downstream operations — scheduling,
costing, compliance, diff — reduce to SQL queries against a stable schema.

### 3.1 What the DB Schema Enables

| Operation | IFC (STEP file) | Relational DB |
|-----------|----------------|---------------|
| "Which elements are on Level 2?" | Parse → navigate IfcRelContainedInSpatialStructure | `WHERE storey = 'Level 2'` |
| "Diff two versions" | Binary diff (meaningless) | `EXCEPT` on guid sets, column-level delta |
| "Count walls by material" | Custom parser per property path | `GROUP BY material_rgba WHERE ifc_class = 'IfcWall'` |
| "All MEP on this floor" | Navigate spatial + discipline chains | `WHERE storey = ? AND discipline IN ('ELEC','PLB','ACMV')` |
| "Export to ERP" | Write custom IFC-to-ERP translator | SQL → iDempiere C_OrderLine (direct mapping) |
| "Geometry for this element" | Decode IfcShapeRepresentation | `SELECT vertices, faces FROM component_geometries WHERE geometry_hash = ?` |

### 3.2 Geometry as Compiled Artifact

Geometry BLOBs (tessellated vertices and face indices) are stored in
`component_library.db`, keyed by a deterministic hash. The project database
references them by hash — it does not contain geometry.

```
TRADITIONAL BIM:
  IFC file (200 MB)  ←  geometry IS the building

RELATIONAL BIM:
  Project semantic data (10 KB)  ←  THIS is the building
  References: component_library.db (500 MB, shared across all projects)
  Compiled output: output.db (regenerable, disposable)
```

This mirrors every mature engineering domain:

| Domain | Source (versioned) | Compiler | Output (disposable) | Shared Library |
|--------|-------------------|----------|--------------------|----|
| Software | .java, .py | javac, gcc | .jar, .exe | Maven Central |
| Manufacturing | BOM + Work Order | MRP engine | Product | Product Master |
| Chip Design | Verilog | Synthesis tool | GDSII | Standard cell library |
| **BIM (traditional)** | *None — geometry IS source* | *None* | *IFC IS output AND source* | *Embedded per project* |
| **BIM (this approach)** | YAML + Order + ASI | 12-stage pipeline | output.db | component_library.db |

BIM is the only major engineering domain that ships compiled output as source
of truth. This project closes that anomaly.

---

## 4. The Geometry Problem: Rosetta Stone

When IFC files from different authoring tools are imported into a single
database, three categories of spatial inconsistency arise:

### 4.1 Origin Divergence

Tool A places the building at (0, 0, 0). Tool B uses real-world GPS coordinates
(e.g. x=317,000, y=175,000 in national grid). Tool C uses an arbitrary project
origin 50 m from the building. Importing all three into one viewer without
correction scatters buildings across hundreds of kilometres.

### 4.2 Unit Mismatch

IFC specifies units in the file header (IfcSIUnit), but in practice:
- Revit exports in millimetres (height: 8,500 mm)
- Blender OBJ exports in metres (height: 8.5 m)
- SketchUp DAE exports in centimetres or inches
- Some files have no unit metadata at all (OBJ, STL)

A height value of 8,500 could be mm, cm, or a GPS coordinate — the file does
not always say.

### 4.3 Up-Axis Ambiguity

IFC uses Z-up. OBJ files from Blender use Y-up. DAE files declare their up-axis
in XML but loaders may or may not honour it. 3DS files are Z-up. FBX files have
an embedded axis declaration that Three.js auto-corrects.

Blindly importing a Y-up OBJ as Z-up produces a building lying on its side.

### 4.4 The Rosetta Stone Solution

The pipeline applies a 6-gate verification strategy to every imported building:

| Gate | Verifies | Method |
|------|----------|--------|
| G1 | Element extraction completeness | COUNT(*) vs source element count |
| G2 | Spatial containment | Every element assigned to a storey |
| G3 | Geometry hash integrity | Hash(vertices + faces) = geometry_hash in DB |
| G4 | Coordinate normalisation | All coordinates in metres, centred near origin |
| G5 | Up-axis consistency | Height axis is Z after import |
| G6 | Transform fidelity | Centroid within expected storey elevation band |

**Auto-detection heuristics** (when metadata is absent):

- **Up-axis:** Analyse vertex bounding box. For buildings, the height axis has
  the smallest range relative to the footprint. If Y-range < Z-range × 0.5,
  the file is Y-up → apply per-vertex swap (x, y, z) → (x, -z, y).

- **Unit scale:** Measure the height-axis range. Height > 5,000 → mm (÷1000).
  Height 500–5,000 → cm (÷100). Height < 50 → already metres.

- **Origin:** Compute per-building centroid. Store as offset in element_transforms.
  Viewer applies offset at render time — elements stay in local coordinates.

This is tested against a fleet of 21 buildings from 9 different authoring tools.
Current gate results: 4 buildings ALL GREEN (9/9 gates), remainder passing 8/9+.
The remaining failures are metadata-level (e.g. IfcOpeningElement classification)
not spatial.

---

## 5. The Landscape

The construction-tech market has three tiers. Each solves a real problem.

### 5.1 Tier 1 — Geometry Authoring (Incumbents)

| Tool | Role |
|------|------|
| **Autodesk Revit** | Full BIM authoring — walls, MEP, structural. Industry standard. |
| **ArchiCAD** (Graphisoft) | Architectural BIM. Strong in EU/Asia. |
| **Tekla Structures** (Trimble) | Steel/concrete detailing, fabrication-grade. |

These tools create IFC models with full semantic richness — property sets,
quantity takeoffs, material associations, spatial hierarchies. IFC4 exports
from Revit and ArchiCAD can carry 4D scheduling and 5D cost data when the
model is authored with that intent.

What they do not do: expose that data as a queryable relational schema. The
IFC export is a STEP file. Downstream consumers must parse it.

### 5.2 Tier 2 — Visual Newcomers

| Tool | Focus |
|------|-------|
| [Snaptrude](https://www.snaptrude.com/) | Browser sketch-to-BIM, real-time 3D |
| [TestFit](https://www.testfit.io/) | AI site planning, instant pro forma |
| [Arkio](https://www.arkio.is/) | VR/AR collaborative design |

These tools focus on design exploration and early-stage visualisation.
They start from geometry, not from IFC semantics. Adding deep IFC compliance
retroactively requires rearchitecting the data model.

### 5.3 Tier 3 — Open Source (IFC-Native)

| Tool | What It Does |
|------|-------------|
| [Bonsai/BlenderBIM](https://bonsaibim.org/) | IFC-native BIM authoring inside Blender |
| [IfcOpenShell](https://ifcopenshell.org/) | IFC parsing/generation library (Python/C++) |
| [IFC.js / ThatOpenCompany](https://thatopen.com/) | Web IFC viewer/editor components |
| [xBIM](https://docs.xbim.net/) | .NET IFC toolkit + geometry engine |
| [FreeCAD BIM](https://wiki.freecadweb.org/BIM_Workbench) | Parametric BIM workbench |

Bonsai is closest philosophically — it works directly with IFC data. IfcOpenShell
provides the parsing infrastructure used by most open-source BIM tools. These
are editors and libraries. They parse, author, and display IFC. They do not
compile IFC into a relational schema with spatial verification.

### 5.4 Where This Project Sits

```
                    AUTHORING PHASE            COMPILATION PHASE
                    ───────────────            ─────────────────
Tier 1 (Revit)     [████████████]              [               ]
Tier 2 (Snaptrude) [████████]                  [               ]
Tier 3 (Bonsai)    [██████████]                [               ]

BIM Compiler        [████████]                  [███████████████]
                     ↑                          ↑
                     Browser import             IFC → DB → SQL → ERP
                     (IFC + OBJ/STL/DAE/GLB)    Rosetta Stone verified
```

The industry focuses on design-time — helping architects create models. The
gap is compile-time — taking a finished model and transforming it into a
queryable, verifiable, composable database.

---

## 6. BOM Factorisation and Downstream Operations

Once IFC is in a relational schema, downstream operations become tractable.

### 6.1 BOM Compression

A BOM (Bill of Materials) decomposes a building hierarchically:
building → floor → room → element → leaf product. Each level is a recipe:
one parent, N children with quantities. The compiler identifies repeating
sub-assemblies and factors them into shared templates.

Observed compression: 51,088 IFC elements → 700 BOM lines (73× reduction).
The compression comes from instancing — a hospital has 400 identical patient
rooms; the BOM stores one room template referenced 400 times.

### 6.2 nD Operations (4D–7D)

With elements in a relational schema, each BIM dimension is a SQL query pattern:

| Dimension | Implementation | Query Pattern |
|-----------|---------------|---------------|
| 4D Schedule | ScheduleDAO | BOM × CIDB work sequence → Gantt intervals |
| 5D Cost | CostDAO | Element × unit rate × quantity → 3-component cost |
| 6D Carbon | SustainabilityDAO | Material × embodied carbon factor → rollup |
| 7D FM | FacilityMgmtDAO | Element × maintenance interval → lifecycle plan |

These are template-driven: a ~30-line DAO class joins the element table against
a rate/factor table. Adding a new dimension is adding a new join, not building
a new parser.

### 6.3 ERP Integration

The output schema maps directly to iDempiere's manufacturing model:
`M_Product` (elements), `M_BOM` (hierarchical decomposition),
`C_Order` + `C_OrderLine` (procurement), `M_AttributeSetInstance` (per-instance
configuration). This mapping is structural, not adapter-based — the 4-database
schema was designed to align with ERP entity patterns.

---

## 7. Browser-Native Viewer (BIM OOTB)

The pipeline's output — SQLite databases — can be consumed by any tool that
reads SQLite. The reference implementation is a browser-native viewer:

- **sql.js** (WASM-compiled SQLite) + **Three.js** (WebGL renderer)
- No server, no plugins, no install
- Drop Zone accepts IFC and six non-IFC formats (OBJ, STL, DAE, GLB, FBX, 3DS)
- Guided classification wizard for non-IFC meshes (name → IFC class inference)
- IFC export (DB → STEP text, round-trip proven)
- Proven at 126,000 elements

The viewer demonstrates that the relational BIM is a viable working format:
import, query, filter, diff, classify, export — all from a URL.

---

## 8. Concrete Implications

**Version control — semantic diffs:**
```
Traditional:  200 MB binary blob → meaningless diff
Relational:   -  width_mm: 4000
              +  width_mm: 4500  → 3 lines, semantically clear
```

**Collaboration — share semantics, compile locally:**
Architect shares 10 KB semantic file. Each discipline compiles against the
shared library. No 200 MB file transfers.

**Variants — fork the order, not the geometry:**
100 variants = 100 × 10 KB = 1 MB. Not 20 GB of duplicated geometry.

**Regulatory submission — machine-verifiable:**
Submit semantic file + library hash + Rosetta Stone gate report. Reviewer
recompiles and verifies independently. Reproducible compliance.

**Storage — 400:1 reduction:**
1,000 projects = 10 MB semantic + 500 MB shared library = 510 MB.
Not 200 GB of per-project geometry.

---

## 9. Current State

| Metric | Value |
|--------|-------|
| Pipeline stages | 12 |
| Verb grammar | 77 verbs |
| Test fleet | 21 buildings from 9 authoring tools |
| Gate results | 4 ALL GREEN (9/9), remainder 8/9+ |
| BOM compression | 73× (51K → 700 lines) |
| Library | 123,573 mesh hashes, 305 MB |
| Browser viewer | 126K elements, 7 import formats |
| Playwright E2E tests | 72/72 desktop, 10 spec files |
| ERP products | 7,403 (M_Product in ERP.db) |
| Supported IFC versions | IFC2x3, IFC4, IFC4x3 (infrastructure) |

The pipeline compiles buildings (residential, hospital, terminal), bridges,
roads, and railways. No code changes per domain — only a YAML mapping.

---

## 10. Related Work

| System | Approach | Difference |
|--------|----------|------------|
| Grasshopper/Dynamo | Parametric scripts → geometry | Per-project scripts, no shared catalog, geometry-centric |
| BIMserver | Delta-based IFC storage | Stores IFC deltas, does not transform to relational schema |
| Speckle | BIM data transport + versioning | Transport layer, geometry-centric, no compilation |
| CostX / Buildsoft | Automated QTO from IFC | Single-dimension extraction (5D), not a general compilation |
| TestFit | AI layout optimisation | Layout as output, not compilable source |

The BIM Intent Compiler combines IFC extraction to relational schema, spatial
gate verification, BOM factorisation, ERP entity mapping, symbolic inference,
and browser-native round-trip. No existing tool combines all six.

---

## 11. Conclusion

IFC is a well-designed exchange format. It is not a working format. The gap
between "I have an IFC file" and "I can query, diff, schedule, cost, and
procure from it" is where construction projects lose time, accuracy, and money.

This project closes that gap by compiling IFC into relational databases with
deterministic spatial verification. The Rosetta Stone strategy handles the
geometry hell — origin divergence, unit mismatch, axis ambiguity — that makes
multi-source IFC integration unreliable. The 4-database schema makes BIM
semantics accessible to any SQL-capable tool.

The geometry is not the building. The geometry is a compiled artifact. The
building is the data.

---

*Cross-references:*
*[`BOMBasedCompilation.md`](BOMBasedCompilation.md) — compilation pipeline spec,*
*[`DATA_MODEL.md`](DATA_MODEL.md) — 4-database schema,*
*[`TestArchitecture.md`](TestArchitecture.md) — Rosetta Stone gates and traceability,*
*[`BIM_Designer_Browser.md`](BIM_Designer_Browser.md) — browser viewer spec,*
*[`INFRA_DESIGNER_SRS.md`](INFRA_DESIGNER_SRS.md) — infrastructure domain (bridge/road/rail),*
*[`MANIFESTO.md`](MANIFESTO.md) — ERP foundation,*
*[`ACTION_ROADMAP.md`](ACTION_ROADMAP.md) — project roadmap*

---

## Get Involved

The project is open source (GPLv3) and actively developed. Current state,
roadmap, and phase deliverables are tracked in
[`ACTION_ROADMAP.md`](ACTION_ROADMAP.md).

If you work with IFC models and want a browser-native viewer that does not
require a server, or if you need BOM factorisation and ERP-grade output from
BIM data — try it, break it, tell us what is missing. Contributions welcome:
product catalogs, jurisdiction rules, format importers, and test buildings.
