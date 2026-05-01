# What Exists Today, What's Missing, and Where We Sit

<div style="max-width: 620px; margin: 32px auto; padding: 24px 40px; background: #263238; border-left: 4px solid #ff9800; text-align: center; border-radius: 4px;">
<span style="font-size: 1.3em; line-height: 1.7; color: #eceff1; letter-spacing: 0.3px;">We BIM living the <b style="color: #ff9800;">GAP</b> between<br><b style="color: #ff9800;">DESIGN</b> and our <b style="color: #ff9800;">SPREADSHEET</b></span>
<br><span style="font-size: 0.75em; letter-spacing: 1.5px; text-transform: uppercase; color: #78909c; margin-top: 12px; display: inline-block;">The industry designs buildings. Who compiles them?</span>
</div>

---

## The Core Problem: From Geometry to Intent and Back

As far as I can tell, most BIM tools store **geometry as the source of truth**.
An IFC file carries the building — 51,000+ elements of geometry, relationships,
properties, all in one monolithic file. Lose the IFC, lose the building.

But geometry — at least in the files I've worked with — is not intent. A
200 MB IFC file captures *what was drawn*. What was meant — the recipe — stays
in the architect's head. You cannot ask it "give me a building like this one
but with 4m ceilings" because the intent was never separated from the output.

### Did Anybody Solve This?

**Autodesk solved it — inside a proprietary wall.** Revit's native .rvt format
maintains full spatial fidelity internally. But .rvt is editable only in Revit,
with a shelf-life tied to Autodesk's support cycle [[6]](#ref6). When geometry leaves
Revit as IFC, "there's always loss of data... all constraints are lost and
component parametrics are gone" [[7]](#ref7). The spatial compilation that works inside
.rvt is not available to anyone outside it.

**The openBIM world has no equivalent.** Bonsai/BlenderBIM is IFC-native — you
model directly in IFC, not in a proprietary format that exports to IFC [[8]](#ref8). But
IFC is an exchange format, not a compilation target. Bonsai can author and
display IFC geometry, but it does not decompose a building into a reusable BOM
recipe, compile from that recipe, or verify the round-trip. Coordinate handling
with large georeferenced files remains an active area of development [[9]](#ref9).

**The compilation challenge:** can you extract a building's intent from its
geometry, express that intent as a reusable recipe (BOM), and then recompile
the recipe back into spatially correct 3D geometry — proving the recipe is
faithful to the original? I have two concerns. Construction is
[industrialised manufacturing](https://www.autodesk.com/design-make/emerging-tech/industrialized-construction) [[11]](#ref11),
yet architecture remains an artistic discipline — the compiler must give
determinism to what was drawn as art. And the industry's embrace of
[probabilistic AI](https://www.sciencedirect.com/science/article/pii/S2590123024008107) [[12]](#ref12)
worries me — a beam is either at (3200, 0, 2700) or it isn't.

That is what the BIM Intent Compiler does. In two databases.

---

## Two Databases: The Heart of the Architecture

### Step 1 — Input DB: Extract the Building

An IFC file (or OBJ, STL, DAE, GLB from the Drop Zone) is extracted into a
normalised SQLite database. Geometry hell is handled here — these are not
theoretical concerns but documented industry problems [[1]](#ref1)[[2]](#ref2)[[3]](#ref3):

- **Origin divergence** — Tool A at (0,0,0), Tool B at GPS (317000, 175000).
  Models from different disciplines scatter across kilometres when merged.
  Even minor coordinate mismatches cause incorrect clash detections [[4]](#ref4).
  Normalised to a common origin.
- **Unit mismatch** — millimetres, metres, inches silently mixed across files.
  A wall at `x=4500` could be 4.5 metres or 4.5 millimetres (1000× error).
  Detected and corrected.
- **Axis ambiguity** — Z-up vs Y-up, rotation chains 6 levels deep, baked
  transforms. Geometric degradation of exports into CSG or faceted polyhedra
  remains widespread [[5]](#ref5). Resolved to a consistent coordinate system.
- **GUID identity** — IFC elements carry GloballyUniqueId, but non-IFC formats
  (OBJ, STL, DAE, GLB) have no element identity at all. The extraction pipeline
  preserves IFC GUIDs through the entire compilation chain and generates
  deterministic GUIDs for non-IFC objects, so every element — regardless of
  source format — has a stable, traceable identity in the database. Without
  this, diffing two versions, tracking changes, and round-tripping back to IFC
  would be impossible.

After extraction, every element is a row. Every spatial relationship is a
foreign key. The building is SQL-queryable:

```sql
SELECT guid, ifc_class, storey, discipline, material
FROM elements_meta
WHERE storey = 'Ground Floor' AND discipline = 'ARC';
```

This alone is useful — but it is still just a structured copy of the geometry.
The building's intent has not been separated yet.

### Step 2 — BOM Abstraction: Find the Recipe

The compiler decomposes 51,000 elements into ~700 BOM lines (73× compression).
Recurring patterns are expressed as formula verbs:

| Verb | Pattern | Example |
|------|---------|---------|
| **TILE** | Repeat at regular spacing | Floor tiles, ceiling panels |
| **ROUTE** | Place along a path | Pipes, ducts, cable trays |
| **FRAME** | Structural grid | Column grids, beam lines |
| **CLUSTER** | Group identical elements | Rail sleepers (75→5 lines, 93%) |

The BOM is the building's *recipe* — a hierarchical bill of materials with
spatial tack offsets (dx, dy, dz from parent). Each child references a product
in a shared component library (LOD meshes keyed by hash). The recipe says:
"place this product at this offset from its parent, repeat 12 times at 2.5m
spacing." The geometry is not stored — it is referenced.

**This is the intent.** Not "here are 12 wall meshes at these coordinates" but
"12 instances of product W-EXT-200, tiled at 2.5m along the north facade."

These verbs are only possible because products are reusable library artifacts,
not per-building geometry. A ROUTE verb says "place PIPE-CU-15 along this path
at 600mm spacing with elbows at bends" — an instruction that requires a shared
product library to draw from. Closed-source BIM databases store geometry
per-building; they cannot express BOM-level operations like TILE, ROUTE, or
CLUSTER because there is no shared catalog of reusable artifacts to operate on.
The same pattern extends to 2D — grid-line-resolved geometry spaces generate
architectural drawings from the same BOM structure.

### Step 3 — Output DB: Compile from Intent

The compiler takes the BOM recipe + shared component library and recompiles
into a new database of spatially placed 3D geometry. This is the **output DB**
— the holy grail. A building generated from intent, not copied from a drawing.

```
INPUT DB (extracted)     →  what the architect drew
BOM (abstracted)         →  what the architect MEANT
OUTPUT DB (compiled)     →  the building, reproduced from intent
```

Every element in the Output DB carries its original GUID — whether that GUID
came from an IFC file or was generated for a non-IFC import. This is what
makes the round-trip work: the Output DB can export back to IFC with the
same element identities the architect started with.

The output DB is disposable. Delete it and recompile from the BOM + library.
The same geometry reproduces exactly. A 200 MB IFC file becomes a 10 KB
semantic definition that references a shared library.

### Step 4 — Rosetta Stone: Prove the Recipe Works

How do you know the BOM recipe faithfully captures the original building?
Compare Input DB against Output DB. The Rosetta Stone strategy runs 9
verification gates across both databases — element counts, bounding volumes,
geometry hashes, spatial digests, GUID provenance, discipline isolation,
metadata survival, transform accuracy, and material assignment.

21 buildings from 9 authoring tools. 116/157 gates PASS, 4 ALL GREEN.
Worst-case positional error: 0.002mm. The recipe reproduces the building.

See [`SPATIAL_COMPILATION_PAPER.md`](SPATIAL_COMPILATION_PAPER.md) for the
full proof methodology.

---

## Why This Changes Everything

Once you have a verified BOM recipe that compiles to correct 3D geometry,
every downstream operation becomes a query on the same database:

| Dimension | Traditional approach | With compiled BOM |
|-----------|---------------------|-------------------|
| **3D Geometry** | Stored in IFC, locked in viewer | Compiled from recipe on demand |
| **4D Schedule** | Separate tool (Primavera, MS Project) | Topological sort of BOM depth = construction sequence |
| **5D Cost** | QS manually counts in Navisworks | `SUM(price × qty)` on BOM leaves — IFC already has the data |
| **6D Carbon** | Separate LCA tool | `SUM(weight × emission_factor)` on same BOM |
| **7D Facility** | Separate CAFM system | Asset register = BOM leaves with maintenance intervals |

**4D/5D becomes tractable once the geometry is correct — but GIGO applies.**
IFC declares fields for quantities, costs, and schedules — though in practice
authoring tools populate these inconsistently. Worse: is an "IfcBeam" in one
building the same product as an "IfcBeam" in another? IFC class names are
categories, not products. A steel I-beam and a timber glulam beam are both
IfcBeam. Without product classification, costing is garbage-in-garbage-out —
you cannot price "beam," only "UB 254×146×31 Grade S355."

This is an ERP problem, not a geometry problem. The compiler addresses it
at two levels: **M_Product_Category** (iDempiere's hierarchical classification)
maps IFC classes to swap pools of actual products with units of measure and
prices. **Rates templates** (editable JSON, one per jurisdiction) let the QS
override unit rates, labour costs, and equipment rates per project — because
CIDB Malaysia rates are not RS Means US rates. The template is the user's
domain knowledge; the compiler applies it consistently across the BOM.

Once the geometry is spatially correct, the BOM is classified, and the rates
are set, scheduling is a topological sort, costing is a rollup, carbon is a
sum. Not trivial — but reducible to SQL queries on a verified database,
rather than separate systems with separate data [[10]](#ref10).

**The hard problem was always the spatial compilation** — proving that a
recipe can reproduce a real building's geometry. Everything downstream is
a projection of the same verified BOM.

---

## The Landscape: Nobody Else Compiles

### Tier 1 — Incumbents (Geometry Authoring)

| Tool | Role |
|------|------|
| **Autodesk Revit** | Full BIM authoring. Industry standard. |
| **ArchiCAD** (Graphisoft) | Architectural BIM. Strong in EU/Asia. |
| **Tekla Structures** (Trimble) | Steel/concrete detailing, fabrication-grade. |

These create the IFC files. They model geometry. They do not decompose it
into a BOM recipe, compile from intent, or verify the round-trip.

### Tier 2 — Visual Newcomers

| Tool | What It Does |
|------|-------------|
| [**Snaptrude**](https://www.snaptrude.com/) | Browser sketch-to-BIM |
| [**TestFit**](https://www.testfit.io/) | AI generative site planning |
| [**Arkio**](https://www.arkio.is/) | VR/AR collaborative design |

Design exploration tools. No BOM, no compilation, no verification.

### Tier 3 — Open Source (IFC-Native)

| Tool | What It Does |
|------|-------------|
| [**Bonsai/BlenderBIM**](https://bonsaibim.org/) | IFC-native authoring inside Blender |
| [**IfcOpenShell**](https://ifcopenshell.org/) | IFC parsing/generation library |
| [**IFC.js / ThatOpenCompany**](https://thatopen.com/) | Web IFC viewer/editor |

They parse and display IFC. They do not abstract intent from geometry,
compile from BOM recipes, or prove spatial round-trip fidelity.

---

## Five Moats

**1. Spatial compilation is solved — and hard to replicate.**
Extracting intent from geometry, compiling back from BOM + library, and
proving the round-trip with 0.002mm accuracy across 21 buildings from 9
authoring tools. This is years of domain-specific work.

**2. DB/ERP integration requires rare domain knowledge.**
Mapping IFC to ERP semantics (M_Product, M_BOM, C_Order) requires
understanding both BIM and manufacturing ERP — a rare combination.

**3. Domain-agnostic — one pipeline for any facility type.**
Houses, terminals, bridges, roads, railways. No code changes per domain —
only a YAML mapping. Rail achieves 93% BOM compression.
See [`INFRA_DESIGNER_SRS.md`](INFRA_DESIGNER_SRS.md).

**4. Symbolic inference over relational data.**
Validation rules evaluated in dependency order (Kahn's topological sort),
proof trees with citations, skip-on-fail. Deterministic, not heuristic.

**5. Browser-native BIM Designer with zero install.**
BIM OOTB: sql.js WASM + Three.js. Imports IFC + six non-IFC formats via
Drop Zone, guided wizard, IFC export. Proven at 126K elements. Any
stakeholder with a URL can import, classify, view, and export.

**The asymmetry:** adding a GUI to a compilation foundation takes weeks.
Adding spatial compilation to a GUI-first tool takes years.

---

## How It's Delivered

```
┌─────────────────────────────────────────────────────────┐
│  UPSTREAM SOURCES                                       │
│  Revit · ArchiCAD · Bonsai · SketchUp · Blender        │
│                       │                                 │
│              IFC / OBJ / STL / DAE / GLB                │
│                       ▼                                 │
│  ┌──────────────────────────────────────────────────┐   │
│  │  BIM OOTB — Browser BIM Designer (primary)       │   │
│  │  Drop Zone → Classify → Enrich → View → Export   │   │
│  │  sql.js + Three.js · zero install · 126K el.     │   │
│  └────────────────────┬─────────────────────────────┘   │
│                       │                                 │
│  ┌────────────────────▼─────────────────────────────┐   │
│  │       BIM INTENT COMPILER (DAGCompiler)          │   │
│  │  Input DB → BOM → Output DB → Rosetta Stone      │   │
│  │  77 verbs · 4-DB schema · 9-gate verification    │   │
│  └────────────────────┬─────────────────────────────┘   │
│                       │                                 │
│  ┌────────────────────▼─────────────────────────────┐   │
│  │              iDempiere ERP                       │   │
│  │  C_Order → C_OrderLine → Purchase Orders         │   │
│  └──────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
```

---

## Who Uses This

| Role | Workflow | What they get |
|------|----------|---------------|
| **Quantity Surveyor** | Drop IFC on browser → BOQ charts + Variation Order Excel [[10]](#ref10) | Automated takeoff with FIDIC Clause 12 costing, no Navisworks |
| **Contractor (tender)** | Import architect's IFC → view + classify + export costed BOM | Material quantities tied to verified geometry, not manual counts |
| **Project Manager** | Import two IFC revisions → diff overlay + cost impact | Change detection with 4D/5D variance in one click |
| **BIM Coordinator** | Merge multi-discipline models → check spatial consistency | Geometry hell resolved at import, not discovered at coordination |
| **Facility Manager** | Query the compiled DB for asset register | Every element traceable by GUID, storey, discipline, material |
| **Developer / Investor** | Browse a building in browser, no software install [[10]](#ref10) | Share a URL — stakeholder sees the model immediately |

---

## Get Involved

The project is open source (GPLv3) and actively developed. Roadmap and
deliverables: [`ACTION_ROADMAP.md`](ACTION_ROADMAP.md).

If you work with IFC models and want verified spatial compilation from BIM
data — try it, break it, tell us what is missing. Contributions welcome:
product catalogs, jurisdiction rules, format importers, and test buildings.

---

*Cross-references:*
*[`SPATIAL_COMPILATION_PAPER.md`](SPATIAL_COMPILATION_PAPER.md) — academic paper (0.002mm proof),*
*[`BOMBasedCompilation.md`](BOMBasedCompilation.md) — compilation pipeline spec,*
*[`DATA_MODEL.md`](DATA_MODEL.md) — 4-database schema,*
*[`TestArchitecture.md`](TestArchitecture.md) — Rosetta Stone gates and traceability,*
*[`BIM_Designer_Browser.md`](BIM_Designer_Browser.md) — browser viewer spec,*
*[`MANIFESTO.md`](MANIFESTO.md) — ERP foundation,*
*[`ACTION_ROADMAP.md`](ACTION_ROADMAP.md) — project roadmap*

---

## References

<span id="ref1">[1]</span> Muller, M.F. *et al.* "On BIM Interoperability via the IFC Standard: An Assessment from the Structural Engineering and Design Viewpoint." *Applied Sciences* 11(23), 2021. — Documents geometry loss and property loss across IFC exchanges between Revit, ArchiCAD, Tekla, and others. [doi:10.3390/app112311430](https://www.mdpi.com/2076-3417/11/23/11430)

<span id="ref2">[2]</span> Pazlar, T. & Turk, Z. "Interoperability in practice: Geometric data exchange using the IFC standard." *ITcon* 13, 2008. — Early benchmark showing "distortion or loss of information related to the geometry of the elements" and "incorrect connection between elements" across five IFC-certified tools. [ResearchGate](https://www.researchgate.net/publication/281596020_Interoperability_in_practice_Geometric_data_exchange_using_the_IFC_standard)

<span id="ref3">[3]</span> Diakite, A. & Zlatanova, S. "About the Geo-referencing of BIM models." TU Delft, 2018. — Analysis of coordinate system divergence in IFC georeferencing, origin offset problems, and IfcMapConversion limitations. [PDF](https://3d.bk.tudelft.nl/pdfs/18_georeferencing.pdf)

<span id="ref4">[4]</span> BIMcollab. "Coordinating IFC Models with World Coordinate System information." — Documents how models without IfcMapConversion "will be shown somewhere far away from the already loaded model." [BIMcollab Help](https://helpcenter.bimcollab.com/en/articles/326917-coordinating-ifc-models-with-world-coordinate-system-information)

<span id="ref5">[5]</span> Autodesk. "Revit 2024: Enhancements to IFC Geometric Fidelity." 2023. — Autodesk's own acknowledgement that IFC geometric fidelity required improvement, with fixes for "complex families (parametric railings, helical stairs) which may generate fragmented geometries." [Autodesk Blog](https://www.autodesk.com/blogs/aec/2023/07/24/revit-2024-enhancements-to-ifc-geometric-fidelity/)

<span id="ref6">[6]</span> CAD Interop. "Revit File Formats: BIM Interoperability, IFC Conversion." — Notes that .rvt files are "editable only in Revit" with "a shelf-life of 3 years (the lifespan of Autodesk support)." [CAD Interop](https://www.cadinterop.com/en/formats/cad-systems/revit.html)

<span id="ref7">[7]</span> Moult, D. "How to create better IFC files with Revit." thinkmoult.com. — Documents that "even when you manage to export your geometry through IFC, there's always loss of data" and "importing that into Revit makes it utterly useless." [thinkmoult](https://thinkmoult.com/how-to-create-better-ifc-files-with-revit.html)

<span id="ref8">[8]</span> Bonsai BIM. "Beautiful, detailed, and data-rich OpenBIM." — Bonsai is IFC-native: "you're not creating geometry that gets converted to IFC later. You're working directly in IFC." [bonsaibim.org](https://bonsaibim.org/)

<span id="ref9">[9]</span> OSArch Community. "How to import IFC with large coordinates?" — Documents floating-point precision limits with georeferenced files requiring local origin offsets, and that "horizontal construction where distances frequently exceed 1km presents challenges." [OSArch](https://community.osarch.org/discussion/1099/blenderbim-how-to-import-ifc-with-large-coordinates)

<span id="ref10">[10]</span> Oon, R.D. "BIM OOTB — Browser Variation Order from IFC Import." 2026. — Demonstrates what becomes possible when BIM data lives in a queryable DB rather than a file: geometry stored as hash-keyed BLOBs (identical meshes instanced, not duplicated), revision diff as SQL `EXCEPT` on GUID sets, cost impact as `GROUP BY` on diff × rates template. IFC import, 4D/5D variance, and costed Variation Order Excel — entirely in the browser, no server. The browser demo is a consequence of the DB-first architecture, not a separate feature. [YouTube](https://youtu.be/hv0kcc_TKvY)

<span id="ref11">[11]</span> Autodesk. "Industrialized Construction." — "Applies the discipline and systematized fabrication process of manufacturing to the design and build process... as consistent and replicable as widgets rolling off a factory assembly line." [Autodesk Emerging Tech](https://www.autodesk.com/design-make/emerging-tech/industrialized-construction)

<span id="ref12">[12]</span> Olanrewaju, O.I. *et al.* "Quantifying the influence of BIM adoption." *Automation in Construction* 161, 2024. — Notes "a significant gap between research and industry practice" and that the industry "still lacks its own quantification methodology for BIM benefits." [ScienceDirect](https://www.sciencedirect.com/science/article/pii/S2590123024008107)

*Copyright (c) 2025-2026 Redhuan D. Oon. MIT Licensed.*
