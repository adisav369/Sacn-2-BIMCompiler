# YAML Classification Guide

## The Invention Boundary

The classification YAML (`classify_*.yaml`) is the **only human-crafted artifact** in the BIM compiler pipeline. Everything else is deterministic:

| Layer | Source | Invented? | Code |
|-------|--------|-----------|------|
| **YAML** | Human/AI author | **YES** — the only point of invention | [`classify_*.yaml`](../IFCtoBOM/src/main/resources/) |
| YAML parsing | YAML → config records | No | [`ClassificationYaml.java`](../IFCtoBOM/src/main/java/com/bim/ifctobom/ClassificationYaml.java) |
| Extraction | Reference DB → `I_Element_Extraction` | No — reads data | [`ExtractionPopulator.java`](../IFCtoBOM/src/main/java/com/bim/ifctobom/ExtractionPopulator.java) |
| Product link | `M_Product_ID = element_ref` | No — deterministic | [`ExtractionPopulator.java:150`](../IFCtoBOM/src/main/java/com/bim/ifctobom/ExtractionPopulator.java) |
| Geometry gap fill | Import missing meshes from ref DB | No — copies blobs | [`ExtractionPopulator.fillGeometryGaps()`](../IFCtoBOM/src/main/java/com/bim/ifctobom/ExtractionPopulator.java) |
| Product images | `M_Product_ID → geometry_hash` | No — join | [`ProductRegistrar.ensureProductImages()`](../IFCtoBOM/src/main/java/com/bim/ifctobom/ProductRegistrar.java) |
| Product registration | M_Product in component_library.db | No — from extraction | [`ProductRegistrar.ensureProductCatalog()`](../IFCtoBOM/src/main/java/com/bim/ifctobom/ProductRegistrar.java) |
| Scope spaces | Element → room assignment | No — centroid-in-AABB | [`ScopeBomBuilder.java`](../IFCtoBOM/src/main/java/com/bim/ifctobom/ScopeBomBuilder.java) |
| Composition | Mirror partition → half-unit BOM | No — axis-agnostic algo | [`CompositionBomBuilder.java`](../IFCtoBOM/src/main/java/com/bim/ifctobom/CompositionBomBuilder.java) |
| Structural BOM | BUILDING + FLOOR STR BOMs | No — from extraction | [`StructuralBomBuilder.java`](../IFCtoBOM/src/main/java/com/bim/ifctobom/StructuralBomBuilder.java) |
| Room BOMs | Static children from YAML | No — template refs | [`FloorRoomBomBuilder.java`](../IFCtoBOM/src/main/java/com/bim/ifctobom/FloorRoomBomBuilder.java) |
| QA validation | Pre-commit gate | No — asserts | [`BomValidator.java`](../IFCtoBOM/src/main/java/com/bim/ifctobom/BomValidator.java) |
| Pipeline orchestrator | Steps 1–11 in order | No | [`IFCtoBOMPipeline.java`](../IFCtoBOM/src/main/java/com/bim/ifctobom/IFCtoBOMPipeline.java) |
| Compilation | BOM + reference DB → output | No — resolves geometry | [`DAGCompiler/.../dsl/CompilationPipeline.java`](../DAGCompiler/src/main/java/com/bim/compiler/dsl/CompilationPipeline.java) |
| Shell driver | Runs pipeline + delta tests | No | [`scripts/run_RosettaStones.sh`](../scripts/run_RosettaStones.sh) |

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
IFCtoBOM/src/main/resources/classify_{prefix}.yaml
```

- `classify_sh.yaml` — Ifc4_SampleHouse (55 elements)
- `classify_dx.yaml` — Ifc2x3_Duplex (1099 elements)
- `classify_te.yaml` — SJTII_Terminal (48,428 elements, future)

## Schema (v1)

### `building` (required)

| Field | Type | Description |
|-------|------|-------------|
| `building_type` | string | Must match reference DB name: `{building_type}_extracted.db` |
| `prefix` | string | Short code (SH, DX, TE). Used for BOM DB name: `{prefix}_BOM.db` |
| `building_bom_id` | string | Root BOM ID (e.g., `BUILDING_SH_STD`) |
| `doc_sub_type` | string | DocType sub-type for C_DocType (e.g., `SH`, `DX`) |
| `doc_base_type` | string | DocType base (always `RE` for residential) |
| `name` | string | Human-readable building name |
| `dsl_file` | string | BIM COBOL script filename (e.g., `dsl_sh.bim`) |

### `storeys` (required)

Parsed by [`ClassificationYaml.java:94`](../IFCtoBOM/src/main/java/com/bim/ifctobom/ClassificationYaml.java). Consumed by [`StructuralBomBuilder.java:83`](../IFCtoBOM/src/main/java/com/bim/ifctobom/StructuralBomBuilder.java) to create per-storey FLOOR STR BOMs.

Maps storey names (from IFC spatial structure) to classification metadata.

```yaml
storeys:
  Ground Floor: { code: GF, bom_category: GF, role: GROUND_FLOOR, seq: 1010 }
  Roof:         { code: ROOF, bom_category: RF, role: ROOF, seq: 1020 }
```

| Field | Description |
|-------|-------------|
| `code` | Short code for BOM ID: `{prefix}_{code}_STR` |
| `bom_category` | Category tag on the FLOOR BOM |
| `role` | Role string on the MAKE child in BUILDING BOM |
| `seq` | Sequence number for ordering in BUILDING BOM |

**Key rule:** Every storey name in the reference DB must have a matching key here. Unmapped storeys are silently dropped (with a warning).

### `floor_rooms` (optional)

Parsed by [`ClassificationYaml.java:110`](../IFCtoBOM/src/main/java/com/bim/ifctobom/ClassificationYaml.java). Consumed by [`ScopeBomBuilder.java`](../IFCtoBOM/src/main/java/com/bim/ifctobom/ScopeBomBuilder.java) (scope assignment) and [`FloorRoomBomBuilder.java`](../IFCtoBOM/src/main/java/com/bim/ifctobom/FloorRoomBomBuilder.java) (room BOM creation).

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

Parsed by [`ClassificationYaml.java:151`](../IFCtoBOM/src/main/java/com/bim/ifctobom/ClassificationYaml.java). Consumed by [`FloorRoomBomBuilder.java`](../IFCtoBOM/src/main/java/com/bim/ifctobom/FloorRoomBomBuilder.java) which inserts MAKE children into the BUILDING BOM.

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

Parsed by [`ClassificationYaml.java:165`](../IFCtoBOM/src/main/java/com/bim/ifctobom/ClassificationYaml.java). Consumed by [`CompositionBomBuilder.java`](../IFCtoBOM/src/main/java/com/bim/ifctobom/CompositionBomBuilder.java) which runs the three-tier mirror partition algorithm.

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

### Step 1 — Extract geometry from IFC (Python, one-time)

Use IfcOpenShell to extract element metadata + geometry into a reference DB.
See [`tools/ifc_geometry_extractor.py`](../tools/ifc_geometry_extractor.py) for the extraction script.

```bash
python3 tools/ifc_geometry_extractor.py \
    --ifc reference/residential/MyBuilding.ifc \
    --output DAGCompiler/lib/input/MyBuilding_extracted.db
```

**Output:** `DAGCompiler/lib/input/MyBuilding_extracted.db` containing:
- `elements_meta` — element names, IFC classes, storey assignments
- `elements_rtree` — bounding boxes (AABB min/max per axis)
- `element_instances` — geometry hashes per element
- `base_geometries` — mesh blobs (vertices + faces)

**What happens next (automatic, inside the Java pipeline):**

When you run the pipeline (Step 5), [`ExtractionPopulator.java`](../IFCtoBOM/src/main/java/com/bim/ifctobom/ExtractionPopulator.java) reads this reference DB and populates `library/component_library.db` with:

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
ERD: [`bim_architecture_viz.html`](bim_architecture_viz.html).

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
- [`classify_sh.yaml`](../IFCtoBOM/src/main/resources/classify_sh.yaml) — simple building (no composition)
- [`classify_dx.yaml`](../IFCtoBOM/src/main/resources/classify_dx.yaml) — mirrored pair (duplex)

Key fields to set:
- `building_type` — must match the reference DB filename (without `_extracted.db`)
- `prefix` — short code (2–3 chars), used for `{prefix}_BOM.db`
- `storeys` — one entry per storey name from step 2
- `composition` — add if the building has mirrored/repeated units

YAML is parsed by [`ClassificationYaml.java`](../IFCtoBOM/src/main/java/com/bim/ifctobom/ClassificationYaml.java).
See [Schema (v1)](#schema-v1) above for field reference.

### Step 4 — Write the BIM COBOL DSL script

Create `IFCtoBOM/src/main/resources/dsl_{prefix}.bim`.
This script tells the DAGCompiler how to walk the BOM and emit elements.
Copy from an existing DSL:
- [`dsl_sh.bim`](../IFCtoBOM/src/main/resources/dsl_sh.bim) — simple building
- [`dsl_dx.bim`](../IFCtoBOM/src/main/resources/dsl_dx.bim) — duplex with mirror

Reference the DSL filename in the YAML: `dsl_file: dsl_{prefix}.bim`.
Verb reference: [`BIM_COBOL.md`](BIM_COBOL.md).
Compiler internals: [`SourceCodeGuide.md`](SourceCodeGuide.md), [`BOMBasedCompilation.md`](BOMBasedCompilation.md).

### Step 5 — Build the BOM (`*_BOM.db`)

```bash
rm -f library/{PREFIX}_BOM.db
./scripts/run_RosettaStones.sh classify_{prefix}.yaml
```

The shell script ([`run_RosettaStones.sh`](../scripts/run_RosettaStones.sh)) calls
[`IFCtoBOMMain.java`](../IFCtoBOM/src/main/java/com/bim/ifctobom/IFCtoBOMMain.java) which runs
[`IFCtoBOMPipeline.java`](../IFCtoBOM/src/main/java/com/bim/ifctobom/IFCtoBOMPipeline.java) —
the single-transaction orchestrator that produces `library/{PREFIX}_BOM.db`:

| Pipeline step | Code | Writes to | What it does |
|---------------|------|-----------|--------------|
| 1. Load YAML | [`ClassificationYaml.load()`](../IFCtoBOM/src/main/java/com/bim/ifctobom/ClassificationYaml.java) | — | Parses the classification YAML into config records |
| 2. Create schema | [`IFCtoBOMPipeline:234`](../IFCtoBOM/src/main/java/com/bim/ifctobom/IFCtoBOMPipeline.java) | `*_BOM.db` | Creates `m_bom`, `m_bom_line`, `ad_sysconfig` tables (recipe + integrity hash) |
| 3. Extract | [`ExtractionPopulator.populate()`](../IFCtoBOM/src/main/java/com/bim/ifctobom/ExtractionPopulator.java) | `component_library.db` | Reference DB → `I_Element_Extraction`, sets `M_Product_ID = element_ref`, imports missing geometry blobs |
| 4. Read extraction | [`ExtractionReader.readByStorey()`](../IFCtoBOM/src/main/java/com/bim/ifctobom/ExtractionReader.java) | — | Reads `I_Element_Extraction` grouped by storey. **FAIL if NULL M_Product_ID** |
| ↳ Pre-flight | `IFCtoBOMPipeline` | — | **FAIL if extraction has storeys not in YAML** |
| 5a. Product catalog | [`ProductRegistrar.ensureProductCatalog()`](../IFCtoBOM/src/main/java/com/bim/ifctobom/ProductRegistrar.java) | `component_library.db` | Creates M_Product in persistent catalog. **INSERT OR IGNORE = reuse across buildings** |
| 5b. Product images | [`ProductRegistrar.ensureProductImages()`](../IFCtoBOM/src/main/java/com/bim/ifctobom/ProductRegistrar.java) | `component_library.db` | Joins `I_Element_Extraction × I_Geometry_Map` → `M_Product_Image` |
| ↳ Pre-flight | `IFCtoBOMPipeline` | — | **FAIL if any product has no geometry_hash** |
| ~~5c. Copy products~~ | ~~`ProductRegistrar.ensureProducts()`~~ | ~~`*_BOM.db`~~ | **DEAD CODE (R7):** BOMWalker reads M_Product from component_library.db via `compConn`. Copy to BOM DB is no longer needed — pending removal |
| 6. Scope spaces | [`ScopeBomBuilder.build()`](../IFCtoBOM/src/main/java/com/bim/ifctobom/ScopeBomBuilder.java) | `*_BOM.db` | Assigns elements to rooms by centroid-in-AABB → SET BOMs |
| 7. Composition | [`CompositionBomBuilder.build()`](../IFCtoBOM/src/main/java/com/bim/ifctobom/CompositionBomBuilder.java) | `*_BOM.db` | Mirror partition → half-unit LEAF lines + pair container (2 children) |
| 8. Structural | [`StructuralBomBuilder.build()`](../IFCtoBOM/src/main/java/com/bim/ifctobom/StructuralBomBuilder.java) | `*_BOM.db` | BUILDING BOM header + FLOOR STR BOMs with element LEAF lines + MAKE children |
| 9. Room BOMs | [`FloorRoomBomBuilder.build()`](../IFCtoBOM/src/main/java/com/bim/ifctobom/FloorRoomBomBuilder.java) | `*_BOM.db` | Static children from YAML + room template LEAF refs |
| 10. QA gate | [`BomValidator.validateAndReport()`](../IFCtoBOM/src/main/java/com/bim/ifctobom/BomValidator.java) | — | Pre-commit validation: FAIL → rollback, broken data never reaches disk |
| 11. Commit | [`IFCtoBOMPipeline`](../IFCtoBOM/src/main/java/com/bim/ifctobom/IFCtoBOMPipeline.java) | `*_BOM.db` | Integrity hash + commit transaction |

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
ERP context (C_Order, BOM decisions): [`ConstructionAsERP.md`](ConstructionAsERP.md) §11.
Schema reference: [`DATA_MODEL.md`](DATA_MODEL.md).

### Step 6 — Compilation and delta verification

The same `run_RosettaStones.sh` invocation continues after BOM creation:

| Step | Code | What it does |
|------|------|--------------|
| Prepare compile DB | [`run_RosettaStones.sh:116`](../scripts/run_RosettaStones.sh) | Copies `*_BOM.db` → temp `_XX_compile.db`, injects `C_DocType` with AABB + DSL |
| Compile EN-BLOC | [`CompilationPipeline.java`](../DAGCompiler/src/main/java/com/bim/compiler/dsl/CompilationPipeline.java) | Singularity proof — takes BOM lines as-is, emits elements |
| Compile WALKTHRU | [`CompilationPipeline.java`](../DAGCompiler/src/main/java/com/bim/compiler/dsl/CompilationPipeline.java) | Mechanism proof — walks BOM hierarchy (BUILDING→FLOOR→SET→LEAF) |
| Delta: count | [`run_RosettaStones.sh:304`](../scripts/run_RosettaStones.sh) | EN-BLOC element count == WALKTHRU element count |
| Delta: geometry | [`run_RosettaStones.sh:374`](../scripts/run_RosettaStones.sh) | 0 elements with different `geometry_hash` |
| Rule 8 | [`run_RosettaStones.sh:390`](../scripts/run_RosettaStones.sh) | All `M_BOM_Line` offsets within parent AABB envelope |
| Clash check | [`run_RosettaStones.sh:412`](../scripts/run_RosettaStones.sh) | 0 furniture AABB overlaps |

**Expected result:** `7/7 PASS` — all delta checks green.

Compilation internals: [`SourceCodeGuide.md`](SourceCodeGuide.md), [`BOMBasedCompilation.md`](BOMBasedCompilation.md) §3.4.
Test architecture: [`TestArchitecture.md`](TestArchitecture.md).

### Step 7 — Troubleshoot

| Symptom | Cause | Fix |
|---------|-------|-----|
| `Unmapped storey` warning | Storey name in ref DB not in YAML | Add the storey key to `storeys:` |
| `NULL M_Product_ID` warning | Should not happen with ExtractionPopulator | Check reference DB has `elements_meta` rows |
| `No geometry for ...` error | Reference DB missing mesh for some elements | Check `element_instances` table in reference DB |
| `QA FAIL: Product-linked LEAF lines` | NULL `child_product_id` on leaf | Check `I_Element_Extraction.M_Product_ID` |
| Delta count mismatch | Composition pairing issue | Check mirror `position` matches party wall center |

QA architecture: [`TestArchitecture.md`](TestArchitecture.md).
ERP model context: [`ConstructionAsERP.md`](ConstructionAsERP.md).

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
| World-coord offsets (>500m) | `BomValidator` | Hardcoded world coordinates in dx/dy/dz |
| BUILDING count != 1 | `BomValidator` | Multiple or zero BUILDING BOMs |
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
  use `IfcFacilityPart` instead of `IfcBuildingStorey`. The extraction layer must map
  these to storey-equivalent names. (ExtractionReader ASSUMPTION)
- **Discipline stratification** — The `disciplines:` section in YAML (e.g. classify_te.yaml)
  is declared but not parsed by schema v1. TE gets storey-level structural BOMs only.

### Adding a New Building — Pre-flight Checklist

Before first pipeline run with a new `classify_*.yaml`:

1. Extract IFC → `DAGCompiler/lib/input/{BuildingType}_extracted.db` (Python, one-time)
2. Query the reference DB for storeys: `sqlite3 ...extracted.db "SELECT storey, COUNT(*) FROM elements_meta GROUP BY storey"`
3. Write `classify_{prefix}.yaml` with every storey name as a key in `storeys:` (pipeline will FAIL if any are missing)
4. Run pipeline: `./scripts/run_RosettaStones.sh classify_{prefix}.yaml`
5. The pipeline automatically:
   - Populates `I_Element_Extraction` in component_library.db (ExtractionPopulator)
   - Creates products in component_library.db catalog (INSERT OR IGNORE = reuse)
   - Links products to geometry (M_Product_Image)
   - Copies products to BOM DB for compilation
6. Check QA report: extraction reconciliation PASS = every element accounted for
7. Check for "products reused from catalog" message — confirms cross-building reuse is working

## Further Reading

### Architecture & Concepts

| Topic | Document |
|-------|----------|
| ERP model (C_Order, BOM, decisions) | [`ConstructionAsERP.md`](ConstructionAsERP.md) |
| Spatial MRP (construction as ERP II) | [`ConstructionAsERPII.txt`](ConstructionAsERPII.txt) |
| BOM compilation, tack §3.4 | [`BOMBasedCompilation.md`](BOMBasedCompilation.md) |
| BIM as BOM concept | [`BIMasBOMConcept.md`](BIMasBOMConcept.md) |
| Conceptual blueprint | [`CONCEPTUAL BLUEPRINT.txt`](CONCEPTUAL%20BLUEPRINT.txt) |
| Rosetta Stone strategy | [`TheRosettaStoneStrategy.txt`](TheRosettaStoneStrategy.txt) |
| BIM Designer vision | [`BIM_Designer.md`](BIM_Designer.md) |

### Data Model & Schema

| Topic | Document |
|-------|----------|
| Schema, tables, I_Element_Extraction | [`DATA_MODEL.md`](DATA_MODEL.md) |
| ERD (interactive HTML) | [`bim_architecture_viz.html`](bim_architecture_viz.html) |
| Terminal ERD | [`terminal_erd.html`](terminal_erd.html) |

### Source Code & Development

| Topic | Document |
|-------|----------|
| Source code walkthrough | [`SourceCodeGuide.md`](SourceCodeGuide.md) |
| DAO, ORM, build instructions | [`DEVELOPER_GUIDE.md`](DEVELOPER_GUIDE.md) |
| BIM COBOL verbs (63 verbs) | [`BIM_COBOL.md`](BIM_COBOL.md) |
| Prefab architecture | [`PREFAB_ARCHITECTURE.md`](PREFAB_ARCHITECTURE.md) |
| Validation rules | [`VALIDATION_RULE_DESIGN.md`](VALIDATION_RULE_DESIGN.md) |

### QA & Testing

| Topic | Document |
|-------|----------|
| Test architecture, tamper seal | [`TestArchitecture.md`](TestArchitecture.md) |
| Current state, gate status | [`../PROGRESS.md`](../PROGRESS.md) |
| Roadmap (phases 0–H) | [`ACTION_ROADMAP.md`](ACTION_ROADMAP.md) |

### Building-Specific Analysis

| Building | Document |
|----------|----------|
| DX mirror forensics | [`DuplexAnalysis.md`](DuplexAnalysis.md) |
| TE ERP architecture | [`TerminalAnalysis.md`](TerminalAnalysis.md) |
| SH data model | [`DATA_MODEL.md`](DATA_MODEL.md) |
