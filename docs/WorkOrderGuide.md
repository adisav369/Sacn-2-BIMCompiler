# Work Order Guide
> **Foundation:** [BBC](BOMBasedCompilation.md) · [DATA_MODEL](DATA_MODEL.md) · [BIM_COBOL](BIM_COBOL.md) · [MANIFESTO](MANIFESTO.md) · [TestArchitecture](TestArchitecture.md)

<div class="bim-banner" markdown>
<b>From a 30-line YAML to a verified 3D building in one command.</b> This is how you run the compiler. The YAML is a human-readable stand-in for ERP order data — each section maps to an iDempiere table.
</div>

> **Key insight:** The classification YAML (`classify_*.yaml`) is a **human-readable
> stand-in** for ERP entities. Each YAML section maps to an iDempiere table:
>
> | YAML Section | ERP Entity | What It Defines |
> |---|---|---|
> | `building` | [C_Order](ProjectOrderBlueprint.md) | Which product to build (one order = one building) |
> | `storeys` / `segments` | [C_OrderLine](ProjectOrderBlueprint.md) | BOM explosion levels (floors, segments) |
> | `floor_rooms` | C_OrderLine + [M_AttributeSet](MANIFESTO.md#m_attributeset-why-product-count-stays-small) | Room scope spaces with dimensions |
> | `static_children` | C_OrderLine (static entries) | Fixed components (slabs, roof, MEP trunk) |
> | `composition` | [Ref_Order_ID](ProjectOrderBlueprint.md) (inheritance) | Mirror/repeat pattern |
>
> In the production path, these definitions come from the [BIM Designer](BIM_Designer_UserGuide.md)
> UI or future [iDempiere REST](https://wiki.idempiere.org/en/REST_Web_Services) integration.
> The YAML is the **extraction and onboarding tool** — how buildings enter the system.

## Quick Start — For Users

Everything is already built. 34 buildings are onboarded, BOMs are generated, the
component library is populated. You don't need to run the pipeline to start working.

```bash
mvn compile -q

# Start the BIM Designer server (Blender/Bonsai connects here)
mvn exec:java -pl BonsaiBIMDesigner \
    -Dexec.mainClass="com.bim.designer.api.DesignerServer" \
    -Dexec.args="library 9876" -q

# Or start the Web UI
./scripts/run_webui.sh
```

**What you can do immediately:**
- Design buildings in Bonsai (BOM Drop → select a building product → compile)
- Browse building data in the Web UI (BOM trees, 4D-7D reports)
- Create C_OrderLines referencing existing products from the library
- Run `./scripts/run_RosettaStones.sh classify_sh.yaml` to verify a building compiles

**The design workflow** (BOM Drop — [BIM_Designer_SRS.md](BIM_Designer_SRS.md) §28):
1. Pick a building product (e.g. `BUILDING_SH_STD`) → creates 1 C_OrderLine
2. Compiler explodes the BOM tree → all elements appear in the viewport
3. Navigate the BOM Outliner to swap/add/remove components
4. Each swap is a new C_OrderLine pointing to a different M_Product
5. Compile again → updated output

See [BIM_Designer_UserGuide.md](BIM_Designer_UserGuide.md) for the full walkthrough.

---

## The Invention Boundary

The classification YAML (`classify_*.yaml`) is the **only human-crafted artifact** in the BIM compiler pipeline. Everything else is deterministic:

| Layer | Source | Invented? | Code |
|-------|--------|-----------|------|
| **YAML** | Human/AI author | **YES** — the only point of invention | [`classify_*.yaml`](https://github.com/red1oon/BIMCompiler/blob/master/IFCtoBOM/src/main/resources/) |
| YAML parsing | YAML → config records | No | [`ClassificationYaml.java`](https://github.com/red1oon/BIMCompiler/blob/master/IFCtoBOM/src/main/java/com/bim/ifctobom/ClassificationYaml.java) |
| Extraction | Reference DB → `I_Element_Extraction` | No — reads data | [`ExtractionPopulator.java`](https://github.com/red1oon/BIMCompiler/blob/master/IFCtoBOM/src/main/java/com/bim/ifctobom/ExtractionPopulator.java) |
| Product link | `M_Product_ID = element_ref` | No — deterministic | [`ExtractionPopulator.java:150`](https://github.com/red1oon/BIMCompiler/blob/master/IFCtoBOM/src/main/java/com/bim/ifctobom/ExtractionPopulator.java) |
| Geometry gap fill | Import missing meshes from ref DB | No — copies blobs | [`ExtractionPopulator.fillGeometryGaps()`](https://github.com/red1oon/BIMCompiler/blob/master/IFCtoBOM/src/main/java/com/bim/ifctobom/ExtractionPopulator.java) |
| Product images | `M_Product_ID → geometry_hash` | No — join | [`ProductRegistrar.ensureProductImages()`](https://github.com/red1oon/BIMCompiler/blob/master/IFCtoBOM/src/main/java/com/bim/ifctobom/ProductRegistrar.java) |
| Product registration | M_Product in component_library.db | No — from extraction | [`ProductRegistrar.ensureProductCatalog()`](https://github.com/red1oon/BIMCompiler/blob/master/IFCtoBOM/src/main/java/com/bim/ifctobom/ProductRegistrar.java) |
| Scope spaces | Element → room assignment | No — centroid-in-AABB | [`ScopeBomBuilder.java`](https://github.com/red1oon/BIMCompiler/blob/master/IFCtoBOM/src/main/java/com/bim/ifctobom/ScopeBomBuilder.java) |
| Composition | Mirror partition → half-unit BOM | No — axis-agnostic algo | [`CompositionBomBuilder.java`](https://github.com/red1oon/BIMCompiler/blob/master/IFCtoBOM/src/main/java/com/bim/ifctobom/CompositionBomBuilder.java) |
| Structural BOM | BUILDING + FLOOR STR BOMs | No — from extraction | [`StructuralBomBuilder.java`](https://github.com/red1oon/BIMCompiler/blob/master/IFCtoBOM/src/main/java/com/bim/ifctobom/StructuralBomBuilder.java) |
| Room BOMs | Static children from YAML | No — template refs | [`FloorRoomBomBuilder.java`](https://github.com/red1oon/BIMCompiler/blob/master/IFCtoBOM/src/main/java/com/bim/ifctobom/FloorRoomBomBuilder.java) |
| QA validation | Pre-commit gate | No — asserts | [`BomValidator.java`](https://github.com/red1oon/BIMCompiler/blob/master/IFCtoBOM/src/main/java/com/bim/ifctobom/BomValidator.java) |
| Pipeline orchestrator | Steps 1–11 in order | No | [`IFCtoBOMPipeline.java`](https://github.com/red1oon/BIMCompiler/blob/master/IFCtoBOM/src/main/java/com/bim/ifctobom/IFCtoBOMPipeline.java) |
| Compilation | BOM + reference DB → output | No — resolves geometry | [`DAGCompiler/.../dsl/CompilationPipeline.java`](https://github.com/red1oon/BIMCompiler/blob/master/DAGCompiler/src/main/java/com/bim/compiler/dsl/CompilationPipeline.java) |
| Shell driver | Runs pipeline + delta tests | No | [`scripts/run_RosettaStones.sh`](https://github.com/red1oon/BIMCompiler/blob/master/scripts/run_RosettaStones.sh) |

**Rule:** If you need to change the pipeline output, change the YAML. Never patch data manually.

## The YAML Fidelity Mantra

> **The YAML is the single source of intent. The compiler's job is to obey it.**
>
> The compiler does NOT open the reference IFC or its extracted DB during
> compilation (verified). The BOM stores parent-relative offsets, not absolute
> coordinates (verified). But neither fact proves the compiler is faithful to
> the YAML that produced the BOM.
>
> **The process of truth:**
> 1. YAML declares intent (storey offsets, static children, scope spaces, products)
> 2. BOM builders translate YAML → `m_bom` + `m_bom_line` with relative dx/dy/dz
> 3. BOMWalker walks the hierarchy → output elements
> 4. **Proof:** If you mutate a YAML value and recompile, the output must change accordingly
>
> **Testable questions:**
> - Change a storey `dz` → does the output shift by exactly that delta?
> - Add a `static_children` entry → does it appear at the declared offset?
> - Remove a `scope_spaces` entry → do those elements fall back to FLOOR STR?
> - Change a `child_product_id` → does the output use the new product?
>
> Until these mutations are tested, the proof for extracted buildings is
> "lossless round-trip", not "the compiler obeys its instructions."
>
> See [`LAST_MILE_PROBLEM.md`](LAST_MILE_PROBLEM.md) §Gap 4 (R4) for status.

## File Convention

```
IFC source files:    DAGCompiler/lib/input/IFC/*.ifc          ← SOURCE (never generated)
Extracted ref DBs:   DAGCompiler/lib/input/*_extracted.db      ← one-time extraction from IFC
Classification YAML: IFCtoBOM/src/main/resources/classify_*.yaml ← human intent (the only invention)
DSL scripts:         IFCtoBOM/src/main/resources/dsl_*.bim     ← building grammar
BOM databases:       library/{PREFIX}_BOM.db                   ← generated by IFCtoBOM pipeline
Component library:   library/component_library.db              ← master catalog (LFS-tracked base)
Compiled output:     DAGCompiler/lib/output/{type}.db          ← generated by compiler
```

### Clean Slate — Running from Scratch

Everything below the IFC source files and extracted reference DBs is regenerable.
To verify the pipeline works end-to-end from zero:

```bash
# 1. Archive generated databases
rm -f library/*_BOM.db                          # BOM recipes (regenerated by IFCtoBOM)
rm -f DAGCompiler/lib/output/*.db               # compiled output (regenerated by compiler)
git checkout -- library/component_library.db    # restore LFS base (geometry + definitions)

# 2. Run the full pipeline (populate + IFCtoBOM + compile + test)
./scripts/run_RosettaStones.sh                  # all buildings
./scripts/run_RosettaStones.sh classify_sh.yaml # or one building
```

`run_RosettaStones.sh` handles everything: populates `component_library.db` with
products and geometry links (skips if already done), runs IFCtoBOM to produce
`*_BOM.db`, compiles to output, and runs G1-G6 gate tests + C8/C9 fidelity checks.

**What lives where:**

| Artifact | Generated by | Persisted | Regenerable? |
|----------|-------------|-----------|-------------|
| `*.ifc` | External (architect) | Git LFS | No — source |
| `*_extracted.db` | `tools/extract.py` (one-time) | gitignored | Yes — from IFC (slow, needs Python) |
| `component_library.db` | LFS base + populate step | Git LFS (base) | Yes — base from LFS, runtime tables from populate |
| `{PREFIX}_BOM.db` | IFCtoBOM pipeline | gitignored | Yes — from extracted DB + YAML |
| `output/*.db` | DAGCompiler | gitignored | Yes — from BOM + library |

**Source IFC files** (in `DAGCompiler/lib/input/IFC/`):

| File | Schema | Elements | Status |
|------|--------|----------|--------|
| `Ifc4_SampleHouse.ifc` | IFC4 | 55 | Onboarded (SH) |
| `FZK_Haus_IFC4.ifc` | IFC4 | 82 | Onboarded (FK) |
| `AC11_Institute_IFC2x3.ifc` | IFC2x3 | 699 | Onboarded (IN) |
| `Ifc2x3_Duplex_*.ifc` | IFC2x3 | 1,099 | Onboarded (DX) |
| `SJTII-*.ifc` (7 discipline files) | IFC2x3 | 48,428 | Onboarded (TE) |
| `PCERT_Infra_Bridge_IFC4X3.ifc` | IFC4X3 | 48 | Onboarded (BR) |
| `PCERT_Infra_Road_IFC4X3.ifc` | IFC4X3 | 53 | Onboarded (RD) |
| `PCERT_Infra_Rail_IFC4X3.ifc` | IFC4X3 | 73 | Onboarded (RL) |
| `FJK_Project_IFC2x3.ifc` | IFC2x3 | — | Not yet onboarded |
| `Smiley_West_IFC2x3.ifc` | IFC2x3 | — | Not yet onboarded |
| `Vogel_Gesamt_IFC2x3.ifc` | IFC2x3 | — | Not yet onboarded |
| `PCERT_Building_Architecture_IFC4X3.ifc` | IFC4X3 | — | Extracted, no YAML |
| `PCERT_Building_Hvac_IFC4X3.ifc` | IFC4X3 | — | Extracted, no YAML |
| `PCERT_Building_Structural_IFC4X3.ifc` | IFC4X3 | — | Extracted, no YAML |
| `PCERT_Infra_Plumbing_IFC4X3.ifc` | IFC4X3 | — | Extracted, no YAML |

**Template generator** (auto-detects storeys from reference DB):

```bash
mvn exec:java -pl IFCtoBOM \
    -Dexec.mainClass="com.bim.ifctobom.NewBuildingGenerator" \
    -Dexec.args="--prefix XX --type BuildingType --name 'Name'" -q
```

**Full onboarding process:** [`IFC_ONBOARDING_RUNBOOK.md`](IFC_ONBOARDING_RUNBOOK.md)

**Classification YAML files:**

- `classify_sh.yaml` — Ifc4_SampleHouse (55 elements)
- `classify_dx.yaml` — Ifc2x3_Duplex (1,099 elements)
- `classify_fk.yaml` — Ifc4_FZKHaus (82 elements)
- `classify_in.yaml` — Ifc2x3_AC11Institute (699 elements)
- `classify_te.yaml` — SJTII_Terminal (48,428 elements)
- `classify_br.yaml` — PCERT_Infra_Bridge (48 elements)
- `classify_rd.yaml` — PCERT_Infra_Road (53 elements)
- `classify_rl.yaml` — PCERT_Infra_Rail (73 elements)
- `classify_dm.yaml` — DemoHouse (template/generative mode)

## Schema (v1)

### `building` (required)

| Field | Type | Description |
|-------|------|-------------|
| `building_type` | string | Must match reference DB name: `{building_type}_extracted.db` |
| `prefix` | string | Short code (SH, DX, TE). Used for BOM DB name: `{prefix}_BOM.db` |
| `building_bom_id` | string | Root BOM ID (e.g., `BUILDING_SH_STD`) |
| `doc_sub_type` | string | Building prefix (SH/DX/TE) |
| `doc_base_type` | string | Domain classification — matches [M_Product_Category](MANIFESTO.md#the-category-cascade-one-pattern-three-domains) (RE/CO/IN) |
| `name` | string | Human-readable building name |
| `dsl_file` | string | BIM COBOL script filename (e.g., `dsl_sh.bim`) |

### `storeys` or `segments` (required)

Parsed by [`ClassificationYaml.java:94`](https://github.com/red1oon/BIMCompiler/blob/master/IFCtoBOM/src/main/java/com/bim/ifctobom/ClassificationYaml.java). Consumed by [`StructuralBomBuilder.java:83`](https://github.com/red1oon/BIMCompiler/blob/master/IFCtoBOM/src/main/java/com/bim/ifctobom/StructuralBomBuilder.java) to create per-segment FLOOR STR BOMs.

Maps segment names (from IFC spatial structure) to classification metadata.
For buildings, segments are storeys (`IfcBuildingStorey`). For infrastructure,
segments are facility parts (`IfcRoadPart`, `IfcBridgePart`, `IfcRailwayPart`).
The parser accepts either `storeys:` or `segments:` as the YAML key — they are
aliases. See [`InfrastructureAnalysis.md`](InfrastructureAnalysis.md) §4.2 for mapping.

```yaml
storeys:
  Ground Floor: { code: GF, bom_category: GF, role: GROUND_FLOOR, seq: 1010 }
  Roof:         { code: ROOF, bom_category: RF, role: ROOF, seq: 1020 }
```

| Field | Description | ERP Mapping |
|-------|-------------|-------------|
| `code` | Short code for BOM ID: `{prefix}_{code}_STR` | M_Product.Value |
| `bom_category` | Category tag on the FLOOR BOM | [M_Product_Category](MANIFESTO.md#the-category-cascade-one-pattern-three-domains) |
| `role` | Role string on the MAKE child in BUILDING BOM | C_OrderLine.Description |
| `seq` | Sequence number for ordering in BUILDING BOM | C_OrderLine.Line |

**Key rules:**
- Every storey name in the reference DB must have a matching key here. Unmapped storeys are silently dropped (with a warning).
- **Each storey `code` must be unique within the building.** The code becomes the BOM ID (`{prefix}_{code}_STR`). If two IFC storeys share the same code, the BOM builder creates duplicate BUILDING→FLOOR references and the compiler walks the floor BOM once per duplicate — producing extra elements (S57 finding: RA +31%, JE +29%, WA +220%, MO +2%). When `onboard_ifc.sh` generates a YAML with colliding codes, disambiguate them before committing (e.g. `L1` → `L1A`/`L1B`, or merge both storeys into one entry if they are architecturally the same floor).

### `floor_rooms` (optional)

Parsed by [`ClassificationYaml.java:110`](https://github.com/red1oon/BIMCompiler/blob/master/IFCtoBOM/src/main/java/com/bim/ifctobom/ClassificationYaml.java). Consumed by [`ScopeBomBuilder.java`](https://github.com/red1oon/BIMCompiler/blob/master/IFCtoBOM/src/main/java/com/bim/ifctobom/ScopeBomBuilder.java) (scope assignment) and [`FloorRoomBomBuilder.java`](https://github.com/red1oon/BIMCompiler/blob/master/IFCtoBOM/src/main/java/com/bim/ifctobom/FloorRoomBomBuilder.java) (room BOM creation).

Defines room/scope space structure per storey.

```yaml
floor_rooms:
  Ground Floor:
    bom_id: FLOOR_SH_GF_STD
    bom_category: GF
    spaces:
      - { name: LIVING, template_bom: SH_LIVING_SET, role: LIVING, seq: 10,
          aabb_mm: [8000, 2000, 1200], origin_m: [-7.0, 2.5, 0.0] }
```

| Space field | Description |
|-------------|-------------|
| `name` | Scope space name |
| `template_bom` | BOM ID for furniture/fixture template |
| `role` | Role string on the LEAF child |
| `seq` | Sequence number |
| `aabb_mm` | `[width, depth, height]` scope box in mm |
| `origin_m` | `[x, y, z]` scope box origin in metres (world coords) |

Elements whose centroid falls inside `origin_m + aabb_mm` are assigned to that scope space.

### `static_children` (optional)

Parsed by [`ClassificationYaml.java:151`](https://github.com/red1oon/BIMCompiler/blob/master/IFCtoBOM/src/main/java/com/bim/ifctobom/ClassificationYaml.java). Consumed by [`FloorRoomBomBuilder.java`](https://github.com/red1oon/BIMCompiler/blob/master/IFCtoBOM/src/main/java/com/bim/ifctobom/FloorRoomBomBuilder.java) which inserts MAKE children into the BUILDING BOM.

Fixed MAKE children added to the BUILDING BOM (slabs, roof, MEP trunk, pair container).

```yaml
static_children:
  - { child_product_id: FLOOR_SLAB_GF, role: GROUND_SLAB, seq: 5, dz: 0.0 }
```

| Field | Description |
|-------|-------------|
| `child_product_id` | BOM ID of the child assembly |
| `role` | Role string on the MAKE child |
| `seq` | Sequence number |
| `dz` | Vertical offset in metres |

### `composition` (optional)

Parsed by [`ClassificationYaml.java:165`](https://github.com/red1oon/BIMCompiler/blob/master/IFCtoBOM/src/main/java/com/bim/ifctobom/ClassificationYaml.java). Consumed by [`CompositionBomBuilder.java`](https://github.com/red1oon/BIMCompiler/blob/master/IFCtoBOM/src/main/java/com/bim/ifctobom/CompositionBomBuilder.java) which runs the three-tier mirror partition algorithm.

Defines how a building is composed from repeated units.

```yaml
composition:
  type: MIRRORED_PAIR
  pair_bom_id: DUPLEX_SET_STD
  half_unit_bom_id: DUPLEX_SINGLE_UNIT_STD
  mirror:
    axis: X
    position: 4.4
    rotation: 3.141592653589793
```

| Field | Description |
|-------|-------------|
| `type` | Composition type: `MIRRORED_PAIR` (only one implemented) |
| `pair_bom_id` | BOM ID for the pair container (SET) |
| `half_unit_bom_id` | BOM ID for each half-unit (FLOOR) |
| `mirror.axis` | Partition axis: `X`, `Y`, or `Z` |
| `mirror.position` | Mirror plane position in world coords (party wall center) |
| `mirror.rotation` | B-side rotation in radians (pi = 180 degrees) |

See `docs/DuplexAnalysis.md` for the three-tier partition algorithm.

## How to Add a New Building

### Step 0 — Ensure IFC element types are registered (one-time)

`extract.py` reads its list of supported IFC classes from the authority table
`ad_ifc_class_map` in `library/ERP.db`. If the new IFC file contains
element types not in that table, they will be **silently skipped**.

Check coverage:
```bash
# List IFC types in the file
python3 -c "import ifcopenshell; f=ifcopenshell.open('source.ifc'); print(sorted(set(e.is_a() for e in f.by_type('IfcElement'))))"

# Compare with registered types
sqlite3 library/ERP.db "SELECT ifc_class FROM ad_ifc_class_map WHERE is_active=1 ORDER BY ifc_class"
```

Add missing types:
```sql
INSERT INTO ad_ifc_class_map
    (ifc_class, discipline, category, attachment_face, ifc_schema, domain, description)
VALUES ('IfcNewType', 'ARC', 'NEW_CATEGORY', 'BOTTOM', 'IFC4', 'BUILDING', 'Description');
```

Zero code changes. See [`DISC_VALIDATION_DB_SRS.md`](DISC_VALIDATION_DB_SRS.md) §5.2 for the full schema.

### Step 1 — Extract geometry from IFC (Python, one-time)

Use IfcOpenShell to extract element metadata + geometry into a reference DB.
See [`tools/extract.py`](https://github.com/red1oon/BIMCompiler/blob/master/tools/extract.py) for the extraction script.

```bash
python3 tools/extract.py --to reference source.ifc \
    -o DAGCompiler/lib/input/MyBuilding_extracted.db
```

**Output:** `DAGCompiler/lib/input/MyBuilding_extracted.db` containing:
- `elements_meta` — element names, IFC classes, storey assignments
- `elements_rtree` — bounding boxes (AABB min/max per axis)
- `element_instances` — geometry hashes per element
- `base_geometries` — mesh blobs (vertices + faces)

**What happens next (automatic, inside the Java pipeline):**

When you run the pipeline (Step 5), [`ExtractionPopulator.java`](https://github.com/red1oon/BIMCompiler/blob/master/IFCtoBOM/src/main/java/com/bim/ifctobom/ExtractionPopulator.java) reads this reference DB and populates `library/component_library.db` with:

| Table | Purpose | Reused? |
|-------|---------|---------|
| `I_Element_Extraction` | Per-building element metadata with `M_Product_ID = element_ref` | Rebuilt per run |
| `I_Geometry_Map` | Element → geometry_hash links | INSERT OR IGNORE |
| `component_geometries` | Mesh blobs (vertices + faces) | INSERT OR IGNORE (shared across buildings) |
| `M_Product` | **Persistent product catalog** — reused across buildings | INSERT OR IGNORE |
| `M_Product_Image` | Product → geometry_hash canonical link | INSERT OR IGNORE |

component_library.db is the **master catalog**. Products created for one building are
automatically reused by subsequent buildings if the same product_id appears.

Schema docs: [`DATA_MODEL.md`](DATA_MODEL.md) §Reference DB.
ERD: [`bim_architecture_viz.html`](https://github.com/red1oon/BIMCompiler/blob/master/database/bim_architecture_viz.html).

### Step 2 — Inspect the extracted data

Query the reference DB to understand storey names, element counts, and IFC classes:

```bash
# List storeys and element counts
sqlite3 DAGCompiler/lib/input/MyBuilding_extracted.db \
    "SELECT storey, COUNT(*) FROM elements_meta GROUP BY storey"

# List IFC classes and counts
sqlite3 DAGCompiler/lib/input/MyBuilding_extracted.db \
    "SELECT ifc_class, COUNT(*) FROM elements_meta GROUP BY ifc_class ORDER BY COUNT(*) DESC"

# Check for mirror symmetry (duplex/row house)
sqlite3 DAGCompiler/lib/input/MyBuilding_extracted.db \
    "SELECT MIN(r.minX), MAX(r.maxX), MIN(r.minY), MAX(r.maxY) FROM elements_rtree r"
```

These storey names must appear as keys in the YAML `storeys:` section.
For mirror buildings, identify the party wall position — see [`DuplexAnalysis.md`](DuplexAnalysis.md).

### Step 3 — Write the classification YAML (only invention step)

Create `IFCtoBOM/src/main/resources/classify_{prefix}.yaml`.
Copy from an existing YAML and adapt:
- [`classify_sh.yaml`](https://github.com/red1oon/BIMCompiler/blob/master/IFCtoBOM/src/main/resources/classify_sh.yaml) — simple building (no composition)
- [`classify_dx.yaml`](https://github.com/red1oon/BIMCompiler/blob/master/IFCtoBOM/src/main/resources/classify_dx.yaml) — mirrored pair (duplex)

Key fields to set:
- `building_type` — must match the reference DB filename (without `_extracted.db`)
- `prefix` — short code (2–3 chars), used for `{prefix}_BOM.db`
- `storeys` — one entry per storey name from step 2
- `composition` — add if the building has mirrored/repeated units

YAML is parsed by [`ClassificationYaml.java`](https://github.com/red1oon/BIMCompiler/blob/master/IFCtoBOM/src/main/java/com/bim/ifctobom/ClassificationYaml.java).
See [Schema (v1)](#schema-v1) above for field reference.

### Step 4 — Write the BIM COBOL DSL script

Create `IFCtoBOM/src/main/resources/dsl_{prefix}.bim`.
This script tells the DAGCompiler how to walk the BOM and emit elements.
Copy from an existing DSL:
- [`dsl_sh.bim`](https://github.com/red1oon/BIMCompiler/blob/master/IFCtoBOM/src/main/resources/dsl_sh.bim) — simple building
- [`dsl_dx.bim`](https://github.com/red1oon/BIMCompiler/blob/master/IFCtoBOM/src/main/resources/dsl_dx.bim) — duplex with mirror

Reference the DSL filename in the YAML: `dsl_file: dsl_{prefix}.bim`.
Verb reference: [`BIM_COBOL.md`](BIM_COBOL.md).
Compiler internals: [`SourceCodeGuide.md`](SourceCodeGuide.md), [`BOMBasedCompilation.md`](BOMBasedCompilation.md).

### Step 5 — Build the BOM (`*_BOM.db`)

```bash
rm -f library/{PREFIX}_BOM.db
./scripts/run_RosettaStones.sh classify_{prefix}.yaml
```

The shell script ([`run_RosettaStones.sh`](https://github.com/red1oon/BIMCompiler/blob/master/scripts/run_RosettaStones.sh)) calls
[`IFCtoBOMMain.java`](https://github.com/red1oon/BIMCompiler/blob/master/IFCtoBOM/src/main/java/com/bim/ifctobom/IFCtoBOMMain.java) which runs
[`IFCtoBOMPipeline.java`](https://github.com/red1oon/BIMCompiler/blob/master/IFCtoBOM/src/main/java/com/bim/ifctobom/IFCtoBOMPipeline.java) —
the single-transaction orchestrator that produces `library/{PREFIX}_BOM.db`:

| Pipeline step | Code | Writes to | What it does |
|---------------|------|-----------|--------------|
| 1. Load YAML | [`ClassificationYaml.load()`](https://github.com/red1oon/BIMCompiler/blob/master/IFCtoBOM/src/main/java/com/bim/ifctobom/ClassificationYaml.java) | — | Parses the classification YAML into config records |
| 2. Create schema | [`IFCtoBOMPipeline:234`](https://github.com/red1oon/BIMCompiler/blob/master/IFCtoBOM/src/main/java/com/bim/ifctobom/IFCtoBOMPipeline.java) | `*_BOM.db` | Creates `m_bom`, `m_bom_line`, `ad_sysconfig` tables (recipe + integrity hash) |
| 3. Extract | [`ExtractionPopulator.populate()`](https://github.com/red1oon/BIMCompiler/blob/master/IFCtoBOM/src/main/java/com/bim/ifctobom/ExtractionPopulator.java) | `component_library.db` | Reference DB → `I_Element_Extraction`, sets `M_Product_ID = element_ref`, imports missing geometry blobs |
| 4. Read extraction | [`ExtractionReader.readByStorey()`](https://github.com/red1oon/BIMCompiler/blob/master/IFCtoBOM/src/main/java/com/bim/ifctobom/ExtractionReader.java) | — | Reads `I_Element_Extraction` grouped by storey. **FAIL if NULL M_Product_ID** |
| ↳ Pre-flight | `IFCtoBOMPipeline` | — | **FAIL if extraction has storeys not in YAML** |
| 5a. Product catalog | [`ProductRegistrar.ensureProductCatalog()`](https://github.com/red1oon/BIMCompiler/blob/master/IFCtoBOM/src/main/java/com/bim/ifctobom/ProductRegistrar.java) | `component_library.db` | Creates M_Product in persistent catalog. **INSERT OR IGNORE = reuse across buildings** |
| 5b. Product images | [`ProductRegistrar.ensureProductImages()`](https://github.com/red1oon/BIMCompiler/blob/master/IFCtoBOM/src/main/java/com/bim/ifctobom/ProductRegistrar.java) | `component_library.db` | Joins `M_Product × I_Geometry_Map` (on product_id = element_ref, filtered by building_type) → `M_Product_Image` |
| ↳ Pre-flight | `IFCtoBOMPipeline` | — | **FAIL if any product has no geometry_hash** |
| ~~5c. Copy products~~ | ~~`ProductRegistrar.ensureProducts()`~~ | ~~`*_BOM.db`~~ | **DEAD CODE (R7):** BOMWalker reads M_Product from component_library.db via `compConn`. Copy to BOM DB is no longer needed — pending removal |
| 6. Scope spaces | [`ScopeBomBuilder.build()`](https://github.com/red1oon/BIMCompiler/blob/master/IFCtoBOM/src/main/java/com/bim/ifctobom/ScopeBomBuilder.java) | `*_BOM.db` | Assigns elements to rooms by centroid-in-AABB → SET BOMs |
| 7. Composition | [`CompositionBomBuilder.build()`](https://github.com/red1oon/BIMCompiler/blob/master/IFCtoBOM/src/main/java/com/bim/ifctobom/CompositionBomBuilder.java) | `*_BOM.db` | Mirror partition → half-unit LEAF lines + pair container (2 children) |
| 8. Structural | [`StructuralBomBuilder.build()`](https://github.com/red1oon/BIMCompiler/blob/master/IFCtoBOM/src/main/java/com/bim/ifctobom/StructuralBomBuilder.java) | `*_BOM.db` | BUILDING BOM header + FLOOR STR BOMs with element LEAF lines + MAKE children |
| 9. Room BOMs | [`FloorRoomBomBuilder.build()`](https://github.com/red1oon/BIMCompiler/blob/master/IFCtoBOM/src/main/java/com/bim/ifctobom/FloorRoomBomBuilder.java) | `*_BOM.db` | Static children from YAML + room template LEAF refs |
| 10. QA gate | [`BomValidator.validateAndReport()`](https://github.com/red1oon/BIMCompiler/blob/master/IFCtoBOM/src/main/java/com/bim/ifctobom/BomValidator.java) | — | Pre-commit validation: FAIL → rollback, broken data never reaches disk |
| 11. Commit | [`IFCtoBOMPipeline`](https://github.com/red1oon/BIMCompiler/blob/master/IFCtoBOM/src/main/java/com/bim/ifctobom/IFCtoBOMPipeline.java) | `*_BOM.db` | Integrity hash + commit transaction |

**Output:**
- `library/{PREFIX}_BOM.db` — per-building **factored recipe**: `m_bom` (BOM headers),
  `m_bom_line` (type lines — one per unique product per parent BOM, with qty and verb
  formula reference). The compiler expands type lines to placement instances at compile
  time. **`{PREFIX}_BOM.db` is a recipe, not a placement map** — see `BOMBasedCompilation.md` §2.1.6.
  Should contain **only** `m_bom` + `m_bom_line` + `ad_sysconfig` (integrity hash).
  No `M_Product` — product definitions live in `component_library.db` (master catalog)
- `library/component_library.db` — **master product catalog** (source of truth):
  `M_Product` (definitions), `M_Product_Image` (geometry links, orientation),
  `I_Element_Extraction` (element metadata), `component_geometries` (mesh blobs)

The BOM DB references products by ID. The library is the source of truth for product
definitions, geometry, and orientation. Products are reused across buildings.

BOM data model: [`BOMBasedCompilation.md`](BOMBasedCompilation.md).
ERP context (C_Order, BOM decisions): [`BBC.md`](BOMBasedCompilation.md) §1.
Schema reference: [`DATA_MODEL.md`](DATA_MODEL.md).

### Step 6 — Compilation and delta verification

The same `run_RosettaStones.sh` invocation continues after BOM creation:

| Step | Code | What it does |
|------|------|--------------|
| Prepare compile DB | [`run_RosettaStones.sh:116`](https://github.com/red1oon/BIMCompiler/blob/master/scripts/run_RosettaStones.sh) | Copies `*_BOM.db` → temp `_XX_compile.db` |
| Compile | [`CompilationPipeline.java`](https://github.com/red1oon/BIMCompiler/blob/master/DAGCompiler/src/main/java/com/bim/compiler/dsl/CompilationPipeline.java) | C_OrderLine → BOM explosion → elements |
| Contracts | [`RosettaStoneGateTest.java`](https://github.com/red1oon/BIMCompiler/blob/master/DAGCompiler/src/test/java/com/bim/compiler/contract/RosettaStoneGateTest.java) | G1-G6 gate tests |
| Rule 8 | [`run_RosettaStones.sh`](https://github.com/red1oon/BIMCompiler/blob/master/scripts/run_RosettaStones.sh) | All `M_BOM_Line` offsets within parent AABB envelope |
| Clash check | [`run_RosettaStones.sh`](https://github.com/red1oon/BIMCompiler/blob/master/scripts/run_RosettaStones.sh) | 0 furniture AABB overlaps |
| C8 Diversity | [`run_RosettaStones.sh`](https://github.com/red1oon/BIMCompiler/blob/master/scripts/run_RosettaStones.sh) | Per-instance mesh uniqueness preserved |
| C9 Axis | [`run_RosettaStones.sh`](https://github.com/red1oon/BIMCompiler/blob/master/scripts/run_RosettaStones.sh) | W/D/H match per axis vs reference |

**Expected result:** All checks PASS.

Compilation internals: [`SourceCodeGuide.md`](SourceCodeGuide.md), [`BOMBasedCompilation.md`](BOMBasedCompilation.md) §4.
Test architecture: [`TestArchitecture.md`](TestArchitecture.md).

### Step 7 — Mine validation rules from the Rosetta Stone

After 10/10 PASS, the output DB contains observed patterns that become validation rules.
This is the same mining approach used for Terminal (NFPA13 sprinkler spacing from 48K elements).

**7a. Query the output DB for patterns:**
```bash
# Structural dimensions per (ifc_class, segment)
sqlite3 DAGCompiler/lib/output/{building_type}.db "
  SELECT em.ifc_class, em.storey, COUNT(*) as cnt,
         ROUND(AVG((r.maxX-r.minX)*1000)) as avg_W_mm,
         ROUND(AVG((r.maxY-r.minY)*1000)) as avg_D_mm,
         ROUND(AVG((r.maxZ-r.minZ)*1000)) as avg_H_mm
  FROM elements_meta em JOIN elements_rtree r ON em.id = r.id
  GROUP BY em.ifc_class, em.storey HAVING COUNT(*) > 1
  ORDER BY cnt DESC" -header -column
```

**7b. Write a migration script** (`migration/DV00N_*.sql`):
```sql
INSERT OR IGNORE INTO AD_Val_Rule
    (rule_id, rule_name, discipline, rule_type, description, mining_source, is_active)
VALUES ('MY_RULE', 'Description', 'STR', 'DIMENSION', 'Details', 'Source_Building', 1);

INSERT OR IGNORE INTO AD_Val_Rule_Param
    (rule_id, param_name, param_value, unit, description)
VALUES ('MY_RULE', 'width_mm', '3499', 'mm', 'Column width');
```

**7c. Apply:**
```bash
sqlite3 library/validation.db < migration/DV00N_my_rules.sql
```

Rule types: `DIMENSION` (element W×D×H), `RATIO` (cross-element proportion),
`MIN_DIMENSION` (safety minimum), `MIN_COUNT` (regulatory), `Z_CONTINUITY` (stacking).

Full mining methodology: [`SourceCodeGuide.md`](SourceCodeGuide.md) §Chapter 4, Step 5.
Bridge rules: [`InfrastructureAnalysis.md`](InfrastructureAnalysis.md) §7.1.
Existing migration: `migration/DV006_infra_bridge_rules.sql` (13 rules, 29 params).

### Step 8 — Troubleshoot

| Symptom | Cause | Fix |
|---------|-------|-----|
| `Unmapped storey` warning | Storey name in ref DB not in YAML | Add the storey key to `storeys:` |
| `NULL M_Product_ID` warning | Should not happen with ExtractionPopulator | Check reference DB has `elements_meta` rows |
| `No geometry for ...` error | Reference DB missing mesh for some elements | Check `element_instances` table in reference DB |
| `QA FAIL: Product-linked LEAF lines` | NULL `child_product_id` on leaf | Check `I_Element_Extraction.M_Product_ID` |
| Delta count mismatch | Composition pairing issue | Check mirror `position` matches party wall center |

QA architecture: [`TestArchitecture.md`](TestArchitecture.md).
ERP model context: [`MANIFESTO.md`](MANIFESTO.md).

## What NOT to Do

- Do NOT write manual SQL migrations for M_Product_ID — `ExtractionPopulator` does this
- Do NOT edit `I_Element_Extraction` manually — it is regenerated every pipeline run
- Do NOT hardcode element_ref → product mappings — `M_Product_ID = element_ref` is automatic
- Do NOT create per-building Python scripts — the Java pipeline is building-agnostic

## Drift Prevention — What the Pipeline Enforces

The pipeline has runtime guards that FAIL (abort + rollback) on broken data.
Every guard runs automatically on every build — no human memory required.

### Enforced Guards (FAIL = pipeline aborts)

| Guard | Location | What It Catches |
|-------|----------|-----------------|
| NULL `M_Product_ID` | `ExtractionReader` | Broken extraction → unlinked BOM leaves |
| NULL `child_product_id` on LEAF | `BomValidator` | BOMWalker silent skip → 0 placements |
| Missing `element_ref` on LEAF | `BomValidator` | G5-PROVENANCE can't trace to library |
| Extraction reconciliation | `BomValidator` | LEAFs + paired != extraction count → silent element loss |
| Unmapped storey in extraction | `IFCtoBOMPipeline` | Storey not in YAML → elements silently dropped |
| Geometry completeness | `IFCtoBOMPipeline` | Products without `geometry_hash` → 0 placements |
| World-coord offsets (>500m) | `BomValidator` | Hardcoded world coordinates in dx/dy/dz. **Note:** This checks parent-relative offsets, not absolute coords. Infrastructure elements with UTM georeferencing are safe — their parent-relative offsets are bounded (~80m max). See [`InfrastructureAnalysis.md`](InfrastructureAnalysis.md) §3.1 G6. |
| BUILDING count != 1 | `BomValidator` | Multiple or zero root BUILDING BOMs. Infrastructure IFCs with multiple facilities must be extracted per-facility to satisfy this guard. See [`InfrastructureAnalysis.md`](InfrastructureAnalysis.md) §2.4. |
| Orphan BOM lines | `BomValidator` | Child references non-existent parent |
| AABB envelope violation | `BomValidator` | Floor AABB exceeds building |
| Schema version mismatch | `ClassificationYaml` | YAML declares v2 but parser is v1 |
| GUID ordinal uniqueness | `PlacementCollectorVisitor` | Always `++ordinalCounter` — stored BOM ordinals never used for GUIDs (collision trap) |

### Advisory Guards (reported, does not block)

| Guard | Location | What It Reports |
|-------|----------|-----------------|
| Verb expansion fidelity | `BomValidator` (step 9b) | Expands each verb_ref, compares world centroids against original extraction. Max/avg error per verb. TILE/ROUTE should be ≤5mm, SPRAY advisory. |
| Factorization ratio | `BomValidator` | WARN if >10× lines/products (TE: 2.6×, healthy) |
| Duplicate positions | `BomValidator` | Same product at same dx/dy/dz (WARN, not FAIL) |

### What the Pipeline Does NOT Validate

These are documented ASSUMPTION remarks in the code — comment-only, no runtime guard:

- **Scope box coordinate frame stability** — `origin_m` in YAML is assumed to match
  extraction centroids. If IFC is re-extracted with a different `IfcMapConversion` offset,
  scope box containment silently breaks. (ScopeBomBuilder ASSUMPTION)
- **Composition geometric validity** — Mirror pairing matches by product count per storey,
  not by geometric spatial mirroring. (CompositionBomBuilder ASSUMPTION)
- **Cross-discipline product_id uniqueness** — If two disciplines have elements with the
  same stripped name (e.g. both ARC and ACMV have "Window_01"), they collapse to one
  M_Product. No cross-discipline collision check exists.
- **Infrastructure IFC4X3 spatial containers** — `IfcRoad`, `IfcBridge`, `IfcRailway`
  use `IfcFacilityPart` instead of `IfcBuildingStorey`. The Python extraction layer
  (`get_storey_for_element()`) already handles this (DONE 2026-03-16). The Java
  spatial structure extraction (`extract_from_ifc_to_reference()`) needs extension
  to extract IfcRoad/IfcBridge/IfcRailway into `spatial_structure` table.
  See [`InfrastructureAnalysis.md`](InfrastructureAnalysis.md) §3.1 G10.
- **Discipline stratification** — The `disciplines:` section in YAML (e.g. classify_te.yaml)
  is declared but not parsed by schema v1. TE gets storey-level structural BOMs only.

### Adding a New Building or Facility — Pre-flight Checklist

Before first pipeline run with a new `classify_*.yaml`:

1. **LOD population** (one-time): `python3 tools/extract.py --to library source.ifc --classes ...`
   This populates component_library.db with geometry for the new element types (INSERT OR IGNORE).
2. **Reference extraction**: `python3 tools/extract.py --to reference source.ifc -o DAGCompiler/lib/input/{BuildingType}_extracted.db`
3. Query the reference DB for segments: `sqlite3 ...extracted.db "SELECT storey, COUNT(*) FROM elements_meta GROUP BY storey"`
4. Write `classify_{prefix}.yaml` with every segment name as a key in `storeys:` (buildings) or `segments:` (infrastructure). Pipeline will FAIL if any are missing.
5. Run pipeline: `./scripts/run_RosettaStones.sh classify_{prefix}.yaml`
6. The pipeline automatically:
   - Populates `I_Element_Extraction` in component_library.db (ExtractionPopulator)
   - Creates products in component_library.db catalog (INSERT OR IGNORE = reuse)
   - Links products to geometry (M_Product_Image)
7. Check QA report: extraction reconciliation PASS = every element accounted for
8. Check for "products reused from catalog" message — confirms cross-building reuse is working

For infrastructure IFCs, also see [`InfrastructureAnalysis.md`](InfrastructureAnalysis.md) §9 for the phased extraction path.

## Schema v3 (planned): MEP Rules-Based Laying

**Status:** SRS — not yet implemented. See `docs/G4_SRS.md`, `docs/TE_MINING_RESULTS.md`.

### The ProcessIt() Pattern

In iDempiere, `MOrder.processIt()` fires the document engine — tax calculation,
inventory reservation, accounting. The user fills in the order lines, clicks
"Process", and the engine applies all business rules automatically.

The BIM Designer follows the same pattern:

```
User action:                         Engine response:
─────────────                        ─────────────────
Define space (room AABB)         →   C_OrderLine created in output.db
Set MEP = true in YAML           →   Discipline flags on building config
Click "Compile It" (ProcessIt)   →   ConstructionModelSpawner + PlacementValidator
                                     → AD_Val_Rule fires per discipline
                                     → MEP elements placed by mined rules
                                     → Clearance checked (ERP-maths, not mesh)
```

### `mep` Section (schema v3)

```yaml
building:
  building_type: MyHouse_2BR
  prefix: MH
  # ... existing fields ...

mep:
  enabled: true                    # triggers MEP auto-population on ProcessIt()
  jurisdiction: MY                 # drives AD_Val_Rule selection
  disciplines:
    FP:
      enabled: true
      occupancy_class: LH          # NFPA 13 Light Hazard
      # Rules auto-applied from AD_Val_Rule:
      #   NFPA13_LH_SPACING: min=3000mm, max=4600mm
      #   FP branch pipe max: ≤12000mm per run
    ELEC:
      enabled: true
      # Rules auto-applied:
      #   IES_LIGHT_SPACING: max=5000mm
      #   NEC_ELEC_SP_CLEARANCE: min=150mm from SP
    SP:
      enabled: true
      # Rules auto-applied:
      #   NEC_ELEC_SP_CLEARANCE: min=150mm from ELEC
```

### How Rules Drive Placement

The compiler does NOT hardcode "sprinklers go every 4m." It reads
`AD_Val_Rule` parameters mined from Rosetta Stones:

```
1. User defines room: AABB = 8000 × 6000 × 3000mm
2. YAML says mep.FP.enabled = true, occupancy_class = LH
3. ProcessIt() → ConstructionModelSpawner:
   a. SELECT * FROM AD_Val_Rule WHERE discipline='FP' AND jurisdiction='MY'
   b. Rule NFPA13_LH_SPACING: min=3000, max=4600, typical=3500
   c. Compute grid pitch:
      pitch = min(max_spacing, room_dim / ceil(room_dim / typical_spacing))
      X: min(4600, 8000/ceil(8000/3500)) = min(4600, 2667) = 2667mm → 3 cols
      Y: min(4600, 6000/ceil(6000/3500)) = min(4600, 3000) = 3000mm → 2 rows → 6 heads
      (typical_spacing is the observed dominant pitch from mining, not the code max)
   d. INSERT 4 C_OrderLine (sprinkler heads) with tack dx/dy/dz
   e. PlacementValidator checks each placement against AD_Val_Rule
4. Cross-discipline check (Tier 2):
   a. ERP-maths clearance: centreline distance - cross-section radii
   b. Uses M_Product dimensions (pipe diameter), not mesh geometry
   c. NEC_ELEC_SP_CLEARANCE: flag any pair < 150mm
```

**The key insight:** Rules are DATA (AD_Val_Rule rows mined from TE/DX),
not CODE. Adding a new jurisdiction = INSERT new rule rows. Adding a new
discipline = INSERT new rules. Zero Java changes. Same pattern as iDempiere
tax tables — rates are data, the tax engine is generic.

### Clearance via ERP Maths (not Bonsai geometry)

Cross-discipline clearance uses product dimensions from the BOM, not viewport
mesh geometry. This sidesteps the Bonsai dependency entirely:

```
clearance = centreline_2D_distance - radius_a - radius_b
where:
  radius = MIN(width, depth) / 2    ← pipe cross-section from M_Product
```

Verified against TE: 48K elements, 11 true overlaps, 35 under 150mm.
See `docs/TE_MINING_RESULTS.md` §M12 for full results.

This means clearance checking works:
- At **compile time** (Rosetta Stone verification)
- At **design time** (BIM Designer ambient compliance — no Blender needed)
- At **batch time** (SQL reports against output.db)

When Bonsai viewport is available (Phase G-8 BlenderBridge), the same
check can optionally use mesh-level precision — but the ERP-maths version
is the default, always-available baseline.

### Relationship to Existing Schema

| Schema version | What it adds | Depends on |
|---------------|-------------|------------|
| v1 (current) | building, storeys, floor_rooms, static_children | — |
| v2 (TE) | disciplines section (ifc_class → bom_category map) | v1 |
| v2+ (infra) | `segments:` alias for `storeys:`, M_Product_Category=IN, infrastructure discipline map | v2. See [`InfrastructureAnalysis.md`](InfrastructureAnalysis.md) §3 |
| **v3 (planned)** | **mep section (rules-based MEP auto-population)** | v2 + AD_Val_Rule + output.db |

---

## §Terrain — Infrastructure Placement on Terrain

Infrastructure elements are placed relative to a terrain surface, not a storey floor.
The Designer treats terrain as a **placement context** — same abstract contract as a
room container, but with variable Z. Full technical details:
[`InfrastructureAnalysis.md`](InfrastructureAnalysis.md) §8.

### Outline Steps: Terrain-Aware Infrastructure Design

```
Step 1: IMPORT TERRAIN
  Source: Federation pdf_terrain addon → survey_highres_extracted.json
  Format: ground_elevations[] with pixel x/y + z elevation (metres)
  Transform: world_x = px × scale, world_y = (img_h - py) × scale
  Result: AlignmentContext with 689 survey points, elevationAt(x,y)

Step 2: SELECT FACILITY TYPE
  User picks: BRIDGE / ROAD / RAILWAY / TUNNEL from facility dropdown
  API: listFacilityTypes() → FacilityType enum
  Effect: loads provenance-scoped validation rules (30 infra rules)
  YAML: M_Product_Category=IN, segments: alias for storeys:

Step 3: DEFINE ALIGNMENT
  User draws polyline over terrain in viewport
  Each vertex gets Z from terrain.elevationAt(x,y)
  Result: AlignmentContext with station points along centreline
  Corridor width: road=7300mm, rail=5000mm, bridge=12000mm (from YAML)

Step 4: PLACE SEGMENTS
  Auto-generate from Rosetta Stone BOM pattern:
    Bridge: ABT → PIR → DCK → SUP → APR (from classify_br.yaml)
    Road:   CW × 4 + PKG (from classify_rd.yaml)
    Rail:   TRK (from classify_rl.yaml)
  Each segment bbox placed along alignment

Step 5: TERRAIN SNAP (interactive drag)
  User drags element → Z follows terrain via TerrainSnap mode:
    ON_SURFACE: road layers, sleepers    (Z = terrain + offset)
    ABOVE:      bridge deck              (Z = terrain + clearance)
    BELOW:      tunnel, pipeline         (Z = terrain - cover - height)
    PIER:       bridge pier, abutment    (Z = terrain, extends up)
  Wireframe bboxes shown during drag — flows along terrain

Step 6: LAYER STACKING (road MAKE path)
  Road pavement stacks 4 layers on terrain:
    subgrade (250mm) → base (120mm) → binder (80mm) → surface (40mm)
  Each layer Z = terrain + cumulative offset below
  Same pattern as Assembly Builder wall layers

Step 7: VALIDATE (snap + rules)
  snap(bboxes, "", gridMm, "ROAD") → loads road validation rules
  Each element checked: width_mm, depth_mm, height_mm, thickness_mm
  BLOCK/PASS verdicts per element — same UX as building validation

Step 8: ADJUST OFFSETS (engineering controls)
  User adjusts: fill height, cut depth, clearance, cover
  Re-snap → re-validate → iterate until compliant
  Gradient check: compare Z at consecutive stations

Step 9: CO SAVE (incremental)
  On save: wireframe bboxes → actual geometry in output DB
  Shape updates incrementally as each element resolves
  output.db stores compile state; design state saved in .blend file
```

### Terrain Data Contract

The terrain JSON from Federation is the input contract:

| Field | Type | Unit | Description |
|-------|------|------|-------------|
| `ground_elevations[].x` | float | pixels | Image X coordinate |
| `ground_elevations[].y` | float | pixels | Image Y coordinate |
| `ground_elevations[].z` | float | metres | Ground elevation (ASL) |
| `metadata.scale` | float | m/pixel | Pixel-to-world scale factor |
| `metadata.image_dimensions.height` | int | pixels | Image height (for Y flip) |

Java reads this via `AlignmentContext(List<StationPoint>, corridorWidthMm)`.
Python writes it via `BIM_OT_pdf_terrain_generate` operator.
Same IfcOpenShell-writes / Java-reads contract as all Federation PoCs.

---

## Further Reading

### Architecture & Concepts

| Topic | Document |
|-------|----------|
| ERP model (C_Order, BOM, decisions) | [`MANIFESTO.md`](MANIFESTO.md) |
| Spatial MRP (construction as ERP II) | [`ConstructionAsERPII.txt`](ConstructionAsERPII.txt) |
| BOM compilation, tack §4 | [`BOMBasedCompilation.md`](BOMBasedCompilation.md) |
| BIM as BOM concept | [`MANIFESTO.md`](MANIFESTO.md) §The Pattern |
| Conceptual blueprint | [`CONCEPTUAL BLUEPRINT.txt`](CONCEPTUAL%20BLUEPRINT.txt) |
| Rosetta Stone strategy | [`TheRosettaStoneStrategy.md`](TheRosettaStoneStrategy.md) |
| BIM Designer vision | [`BIM_Designer.md`](BIM_Designer.md) |

### Data Model & Schema

| Topic | Document |
|-------|----------|
| Schema, tables, I_Element_Extraction | [`DATA_MODEL.md`](DATA_MODEL.md) |
| ERD (interactive HTML) | [`bim_architecture_viz.html`](https://github.com/red1oon/BIMCompiler/blob/master/database/bim_architecture_viz.html) |
| Terminal ERD | [`terminal_erd.html`](https://github.com/red1oon/BIMCompiler/blob/master/database/terminal_erd.html) |

### Source Code & Development

| Topic | Document |
|-------|----------|
| Source code walkthrough | [`SourceCodeGuide.md`](SourceCodeGuide.md) |
| DAO, ORM, build instructions | [`SourceCodeGuide.md`](SourceCodeGuide.md) |
| BIM COBOL verbs (75 verbs) | [`BIM_COBOL.md`](BIM_COBOL.md) |
| Prefab architecture | [`PREFAB_ARCHITECTURE.md`](PREFAB_ARCHITECTURE.md) |
| Validation rules | [`DocValidate.md`](DocValidate.md) |

### QA & Testing

| Topic | Document |
|-------|----------|
| Test architecture, tamper seal | [`TestArchitecture.md`](TestArchitecture.md) |
| Current state, gate status | [`../PROGRESS.md`](https://github.com/red1oon/BIMCompiler/blob/master/PROGRESS.md) |
| Roadmap (phases 0–H) | [`ACTION_ROADMAP.md`](ACTION_ROADMAP.md) |

### Building-Specific Analysis

| Building | Document |
|----------|----------|
| DX mirror forensics | [`DuplexAnalysis.md`](DuplexAnalysis.md) |
| TE ERP architecture | [`TerminalAnalysis.md`](TerminalAnalysis.md) |
| SH data model | [`DATA_MODEL.md`](DATA_MODEL.md) |

---

## Appendix — Adding New IFC Files

> Most users will never need this. The 35 onboarded buildings and their products
> are already in the repository. This section is for contributors who want to
> extend the system with new IFC source files.

One command handles the entire process — extraction, YAML generation, BOM creation,
compilation, testing, and validation rule mining:

```bash
./scripts/onboard_ifc.sh \
    --prefix XX --type BuildingType \
    --name "Human Name" --base RE \
    --ifc DAGCompiler/lib/input/IFC/source.ifc
```

This runs 8 steps: recon → extract → generate YAML/DSL → register manifest →
register GATE_SCOPE → pipeline (populate + IFCtoBOM + compile) → extract
validation rules → report.

After onboarding, `run_RosettaStones.sh` includes the new building automatically.
Review the generated `classify_XX.yaml` and commit.

Full guide: [`IFC_ONBOARDING_RUNBOOK.md`](IFC_ONBOARDING_RUNBOOK.md).
