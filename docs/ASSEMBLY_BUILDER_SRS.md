# Assembly Builder SRS — G-7 Layer-by-Layer TACK
> **Foundation:** [BBC](BOMBasedCompilation.md) · [DATA_MODEL](DATA_MODEL.md) · [BIM_COBOL](BIM_COBOL.md) · [MANIFESTO](MANIFESTO.md) · [TestArchitecture](TestArchitecture.md)

<div class="bim-banner" markdown>
<b>Stack BUY items into sub-BOMs with layer composition.</b> The Assembly MAKE path — material layers, U-value calculation, and template save/recall using the same m_bom schema.
</div>

**Version:** 1.0 (2026-03-19, session 35)
**Depends on:** [BIM_Designer.md](BIM_Designer.md) §18.2 Principle 4, §18.5 MAKE path;
[BIM_Designer_SRS.md](BIM_Designer_SRS.md) §11 material_layers, §14 InferenceEngine;
[BOMBasedCompilation.md](BOMBasedCompilation.md) §4 tack convention;
[DATA_MODEL.md](DATA_MODEL.md) m_bom + m_bom_line schema
**Scope:** The Assembly MAKE path — stacking existing BUY items into sub-BOMs
with layer composition, U-value calculation, and template save/recall.
**Gate:** G-7 (depends on G-4 Save/Recall, feeds G-9 ORDER View + G-10 Promote)

> **Pattern source:** BIMsmith Forge (300K materials, layer-by-layer assembly).
> Our equivalent uses the same `m_bom` + `m_bom_line` schema that drives the
> entire pipeline — assembly templates are data, not code.

> **Rules are DATA, not code.** Assembly templates, material properties, and
> layer compositions live in `component_library.db` tables. Adding a new wall
> type = SQL INSERT into `material_layers`. Adding a new assembly = SQL INSERT
> into `m_bom` + `m_bom_line`.

---

## 1. Assembly Types — What Can Be Assembled

Three categories of assemblies map to construction practice:

| Category | Example | material_layers rows | m_bom_line rows | U-value? |
|----------|---------|---------------------|-----------------|----------|
| **WALL** | `Basic Wall:Exterior - Brick on Block` | 6 layers | 6 BOM lines | YES |
| **ROOF** | `Basic Roof:Live Roof over Wood Joist Flat Roof` | 6 layers | 6 BOM lines | YES |
| **FLOOR** | `Floor:Residential - Wood Joist with Subflooring` | 2 layers | 2 BOM lines | YES |

**Current seed data:** 20 distinct `layer_set_name` values in `material_layers`
(60 rows total): 9 walls, 2 roofs, 2 ceilings, 7 floors.

Additional assembly types (CEILING, STAIR) follow the same pattern but may
not have material_layers rows — they use `m_bom_line` children directly.

---

## 2. Data Model

### 2.1 Assembly Template = m_bom + m_bom_line

An assembly template is an `m_bom` record whose children (`m_bom_line`) are the
layers. The m_bom itself has no `component_type` column — the component_type
lives on each m_bom_line child. Each layer line has:

| m_bom_line column | Assembly meaning | Example |
|-------------------|-----------------|---------|
| `child_product_id` | Material product (FK → M_Product) | `BRICK_110` |
| `sequence` | Layer order (outside → inside) | 0, 1, 2, 3 |
| `role` | Layer function | `CLADDING`, `CAVITY`, `INSULATION`, `LINING` |
| `allocated_width_mm` | Layer thickness (= wall-normal direction) | 110 |
| `allocated_depth_mm` | Same as parent depth (wall length) | parent |
| `allocated_height_mm` | Same as parent height (wall height) | parent |
| `component_type` | BUY (leaf material) or MAKE (sub-assembly) | `BUY` |

**Constraint:** `SUM(allocated_width_mm)` across all layers = parent
`m_bom.aabb_width_mm`. This is the **thickness invariant**.

### 2.2 material_layers → Assembly Template Mapping

`material_layers` in `component_library.db` provides the seed data:

```sql
-- material_layers schema
layer_set_name  TEXT    -- e.g. "Basic Wall:Exterior - Brick on Block"
sequence        INTEGER -- 0-based layer order
material_name   TEXT    -- e.g. "Masonry - Brick"
thickness_m     REAL    -- layer thickness in metres (SH) or mm (TE)
is_ventilated   INTEGER -- 1 if air gap / ventilated cavity
```

**Unit ambiguity (known):** The `thickness_m` column contains mixed units —
metres for some templates, mm for others, even within the same prefix:
- `Basic Wall:Exterior - Brick on Block` → 0.092 (metres)
- `Basic Wall:Wall-Partn_12P-70MStd-12P` → 12.5 (mm)
- `Basic Roof:Live Roof over Wood Joist Flat Roof` → 0.064 (metres)
- `Basic Roof:Roof_Flat-4Felt-150Ins-50Scr-150Conc-12Plr` → 4.0 (mm)

**Prefix is unreliable.** The DAO uses **magnitude check as primary strategy:**
- If `MAX(thickness_m)` for a layer_set < 1.0 → values are in **metres** → multiply by 1000
- If `MAX(thickness_m)` for a layer_set ≥ 1.0 → values are already in **mm**

All thicknesses normalised to **mm** on read.

### 2.3 Material Thermal Properties (new table)

U-value calculation requires thermal conductivity per material. New table:

```sql
CREATE TABLE IF NOT EXISTS ad_material_thermal (
    material_name     TEXT PRIMARY KEY,     -- FK-ish to material_layers.material_name
    conductivity_w_mk REAL NOT NULL,        -- λ (W/m·K)
    description       TEXT,                 -- human label
    source            TEXT DEFAULT 'CIBSE'  -- data source reference
);
```

**Seed data** (29 materials from material_layers, standard values):

| Material | λ (W/m·K) | Source |
|----------|----------|--------|
| Masonry - Brick | 0.77 | CIBSE Guide A |
| Masonry - Concrete Block | 1.13 | CIBSE Guide A |
| Misc. Air Layers - Air Space | 0.56† | CIBSE (R=0.18 at 25mm) |
| Air | 0.56† | CIBSE (R=0.18 at 25mm) |
| Insulation / Thermal Barriers - Rigid insulation | 0.025 | CIBSE Guide A |
| Rigid insulation | 0.025 | CIBSE Guide A |
| Insulation / Thermal Barriers - Semi-rigid insulation | 0.038 | CIBSE Guide A |
| Metal - Stud Layer | 50.0‡ | (bridged: effective R from stud fraction) |
| Metal Stud Layer | 50.0‡ | (bridged) |
| Plasterboard | 0.21 | CIBSE Guide A |
| Plaster | 0.57 | CIBSE Guide A |
| Gypsum Wall Board | 0.21 | CIBSE Guide A |
| Concrete - Cast In Situ | 1.40 | CIBSE Guide A |
| Concrete, Cast In Situ | 1.40 | CIBSE Guide A |
| Concrete | 1.40 | CIBSE Guide A |
| Concrete, Sand/Cement Screed | 1.40 | CIBSE Guide A |
| Concrete, Precast | 1.40 | CIBSE Guide A |
| Concrete Masonry, Floor Block | 1.13 | CIBSE Guide A |
| Wood - Sheathing - plywood | 0.13 | CIBSE Guide A |
| Wood - Dimensional Lumber | 0.13 | CIBSE Guide A |
| Wood - Flooring | 0.14 | CIBSE Guide A |
| Ceramic Tile | 1.30 | CIBSE Guide A |
| Masonry - Grout | 1.40 | (≈ cement) |
| Roofing Felt | 0.19 | CIBSE Guide A |
| Roofing - Barrier | 0.50 | (generic membrane) |
| Roofing - EPDM Membrane | 0.25 | Manufacturer typical |
| Vapor Retarder | 0.50 | (generic membrane) |
| Damp-proofing | 0.50 | (generic membrane) |
| Site - Grass | 0.50 | (soil/turf approximation) |

† Air gaps: thermal resistance is fixed (R=0.18 m²K/W for 25mm unventilated
  cavity per BS EN ISO 6946). The DAO returns R directly, not λ.
‡ Metal studs: effective conductivity depends on stud fraction. Simplified:
  use bridged R-value based on typical 15% stud area fraction.

---

## 3. API Signatures

### 3.1 New DesignerAPI Methods

```java
// ── Assembly Builder (§18.2 Principle 4, G-7) ────────────────────────

/**
 * List available assembly templates for a given category (WALL/ROOF/FLOOR).
 * Returns templates with their layer stacks and U-values.
 *
 * // Implementing BIM_Designer.md §18.2 Principle 4 — Witness: W-ASM-LIST-1
 */
AssemblyListResponse listAssemblyTemplates(String category);

/**
 * Get the full layer stack for an assembly template.
 * Returns layers with materials, thicknesses, and thermal properties.
 *
 * // Implementing BIM_Designer.md §18.2 Principle 4 — Witness: W-ASM-DETAIL-1
 */
AssemblyDetailResponse getAssemblyDetail(String layerSetName);

/**
 * Browse compatible alternative materials for a specific layer position.
 * Filters by role compatibility and returns materials with thermal data.
 * Returns its own response type (not BrowseItemsResponse — different shape).
 *
 * // Implementing BIM_Designer.md §18.2 Principle 4 — Witness: W-ASM-BROWSE-1
 */
BrowseAssemblyLayersResponse browseAssemblyLayers(BrowseAssemblyLayersRequest request);

/**
 * Replace a layer in an assembly with an alternative material.
 * Operates on an in-memory copy — does NOT mutate component_library.db.
 * Returns a new AssemblyDetailResponse with recalculated totals.
 * The caller decides whether to save (via saveAssemblyTemplate or save()).
 *
 * // Implementing BIM_Designer.md §18.2 Principle 4 — Witness: W-ASM-SWAP-1
 */
AssemblyDetailResponse swapLayer(SwapLayerRequest request);

/**
 * Save an assembly as a reusable template (new m_bom + m_bom_line).
 * Per-instance saves go through existing save() → C_OrderLine path.
 *
 * // Implementing BIM_Designer.md §18.2 Principle 4 — Witness: W-ASM-SAVE-1
 */
AssemblySaveResponse saveAssemblyTemplate(SaveAssemblyRequest request);
```

### 3.2 New Records

```java
// ── Assembly Builder records ─────────────────────────────────────────

/** Request to browse compatible layers for a position in an assembly. */
record BrowseAssemblyLayersRequest(
    String layerSetName,       // current assembly template
    int layerSequence,         // which layer position to browse alternatives for
    String materialCategory,   // optional filter: "Insulation", "Masonry", etc.
    int offset,
    int limit
) {}

/** Request to swap a layer in an assembly. */
record SwapLayerRequest(
    String layerSetName,       // assembly template
    int layerSequence,         // which layer to replace
    String newMaterialName,    // replacement material
    double newThicknessMm      // replacement thickness
) {}

/** Request to save an assembly as a template. */
record SaveAssemblyRequest(
    String templateName,       // human name for the template
    String category,           // WALL / ROOF / FLOOR
    List<AssemblyLayer> layers // the layer stack
) {}

/** A single layer in an assembly. */
record AssemblyLayer(
    int sequence,              // 0-based order (outside → inside)
    String materialName,       // material label
    double thicknessMm,        // layer thickness in mm
    String role,               // CLADDING / CAVITY / INSULATION / LINING / STRUCTURE
    boolean isVentilated       // air gap flag
) {}

/** Response for browsing layer alternatives. */
record BrowseAssemblyLayersResponse(
    boolean success,
    String layerSetName,
    int layerSequence,
    List<AlternativeMaterial> alternatives,
    String error
) {}

/** A material alternative for a layer position. */
record AlternativeMaterial(
    String materialName,
    double conductivityWmK,
    double defaultThicknessMm,
    String roleCompatibility   // CLADDING / INSULATION / etc.
) {}

/** Response listing available assembly templates. */
record AssemblyListResponse(
    boolean success,
    List<AssemblyTemplateSummary> templates,
    String error
) {}

/** Summary of one assembly template. */
record AssemblyTemplateSummary(
    String layerSetName,       // unique ID (= material_layers.layer_set_name)
    String category,           // WALL / ROOF / FLOOR / CEILING
    int layerCount,
    double totalThicknessMm,
    double uValueWm2K,         // pre-calculated
    String compliance          // UBBL reference or null
) {}

/** Full detail of an assembly template. */
record AssemblyDetailResponse(
    boolean success,
    String layerSetName,
    String category,
    List<AssemblyLayer> layers,
    double totalThicknessMm,
    double uValueWm2K,
    String complianceNote,     // e.g. "UBBL 2012: max 0.60 W/m²K for ext wall"
    String error
) {}

/** Result of saving an assembly template. */
record AssemblySaveResponse(
    boolean success,
    String bomId,              // generated m_bom.bom_id
    int layerCount,
    String error
) {}
```

---

## 4. U-Value Calculation

### 4.1 Formula (BS EN ISO 6946)

```
U = 1 / R_total

R_total = R_si + Σ(R_layer) + R_se

R_layer = thickness_m / conductivity_w_mk    (for solid layers)
R_layer = min(0.18, 0.18 × gap_mm / 25.0)     (for air cavities; saturates at 25mm)

R_si = 0.13  (internal surface resistance, horizontal heat flow)
R_se = 0.04  (external surface resistance)
```

**For vertical elements (walls):** R_si=0.13, R_se=0.04
**For horizontal elements (roofs, upward):** R_si=0.10, R_se=0.04
**For horizontal elements (floors, downward):** R_si=0.17, R_se=0.04

### 4.2 Metal Stud Bridging (simplified)

Metal stud layers use proportional-area method (BS EN ISO 6946 §6.2):

```
R_bridged = 1 / (f_metal/R_metal + f_insulation/R_insulation)

f_metal = 0.15 (typical 15% stud fraction for 600mm centres, 45mm studs)
f_insulation = 0.85
R_metal = thickness / 50.0 (steel λ ≈ 50 W/m·K)
R_insulation = thickness / 0.038 (cavity fill assumption)
```

### 4.3 Compliance Thresholds

From UBBL 2012 (Malaysian Uniform Building By-Laws) and common standards:

| Element | UBBL 2012 max U | UK Part L max U | Notes |
|---------|----------------|-----------------|-------|
| External wall | 0.60 W/m²K | 0.26 W/m²K | UBBL is tropical, less insulation |
| Roof | 0.40 W/m²K | 0.16 W/m²K | |
| Ground floor | 0.60 W/m²K | 0.18 W/m²K | |

Compliance check uses `AD_Val_Rule` rows with `check_method = 'U_VALUE'`
and jurisdiction-specific thresholds. This is **Phase 2** — G-7 delivers the
calculator; AD_Val_Rule integration comes with G-9/G-10 governance.

### 4.4 Implementation: UValueCalculator

```java
/**
 * Stateless U-value calculator. Pure function: layers in → U-value out.
 * No DB access — caller provides layers and thermal properties.
 *
 * // Implementing ASSEMBLY_BUILDER_SRS.md §4 — Witness: W-ASM-UVAL-1
 */
public final class UValueCalculator {

    /** Surface resistance by element orientation. */
    public enum Orientation { VERTICAL, HORIZONTAL_UP, HORIZONTAL_DOWN }

    /**
     * Calculate U-value for a layer stack.
     *
     * @param layers      ordered layers (outside → inside)
     * @param orientation element orientation for surface resistance
     * @return U-value in W/m²K, rounded to 2 decimal places
     * @throws IllegalArgumentException if layers is empty
     */
    public static double calculate(List<LayerThermal> layers, Orientation orientation);

    /** Input record — one layer's thermal data. */
    public record LayerThermal(
        double thicknessMm,
        double conductivityWmK,  // λ; ignored if isAirGap=true
        boolean isAirGap,        // use fixed R instead of λ
        boolean isMetalStud      // use bridged R calculation
    ) {}
}
```

---

## 5. DAO Layer

### 5.1 AssemblyDAO (new)

Reads `component_library.db` for assembly templates and material properties.
**Read-only** — never writes to component_library.db.

```java
/**
 * DAO for assembly templates and material thermal data.
 * Reads component_library.db. Never writes.
 *
 * // Implementing ASSEMBLY_BUILDER_SRS.md §5 — Witness: W-ASM-DAO-1
 */
public class AssemblyDAO {

    /**
     * Load all layer sets for a category prefix.
     * @param categoryPrefix "Basic Wall" / "Basic Roof" / "Floor" / "Compound Ceiling"
     * @return list of layer set names matching the prefix
     */
    List<String> listLayerSets(String categoryPrefix);

    /**
     * Load full layer stack for a layer set.
     * Normalises thickness to mm.
     * @return ordered list of layers (by sequence)
     */
    List<MaterialLayer> loadLayers(String layerSetName);

    /**
     * Load thermal conductivity for a material.
     * @return conductivity in W/m·K, or null if unknown
     */
    Double getThermalConductivity(String materialName);

    /**
     * Load all thermal properties (batch, for U-value calc).
     * @return map of material_name → conductivity_w_mk
     */
    Map<String, Double> loadAllThermalProperties();

    /**
     * Browse compatible alternative materials for a layer position.
     * Filters by role compatibility (e.g., insulation slot → insulation materials).
     * @param currentMaterial the material being replaced
     * @param role            layer role (CLADDING, INSULATION, etc.)
     * @return compatible M_Product entries
     */
    List<AlternativeMaterial> browseAlternatives(String currentMaterial, String role);

    /** Raw layer from material_layers table. */
    record MaterialLayer(
        String layerSetName,
        int sequence,
        String materialName,
        double thicknessMm,    // normalised to mm
        boolean isVentilated
    ) {}

    /** An alternative material for a layer position. */
    record AlternativeMaterial(
        String materialName,
        double conductivityWmK,
        String description
    ) {}
}
```

### 5.2 AssemblyWriteDAO (new)

Writes assembly templates to output.db (per-instance) or to a new BOM
file (reusable template).

```java
/**
 * Write DAO for assembly templates.
 * Per-instance: writes to output.db C_OrderLine.
 * Reusable: writes to {PREFIX}_BOM.db m_bom + m_bom_line (via Promote path).
 *
 * // Implementing ASSEMBLY_BUILDER_SRS.md §5.2 — Witness: W-ASM-WRITE-1
 */
public class AssemblyWriteDAO {

    /**
     * Save assembly as per-instance OrderLine data.
     * Each layer → one C_OrderLine child under the wall/roof/floor OrderLine.
     * @return number of lines written
     */
    int savePerInstance(Connection conn, String parentOrderLineId,
                        List<AssemblyLayer> layers);

    /**
     * Save assembly as reusable template (m_bom + m_bom_line).
     * Calls through to Promote path — requires governance gate.
     * @return generated bom_id
     */
    String saveAsTemplate(Connection conn, String templateName,
                          String category, List<AssemblyLayer> layers);
}
```

---

## 6. Wire Protocol — BlenderBridge Extensions

### 6.1 New Verbs (Python ← Java)

| Verb | Direction | Payload | Action |
|------|-----------|---------|--------|
| `show_assembly_editor` | Java → Python | `{layers, totalMm, uValue}` | Render layer stack panel in Blender N-panel |
| `update_assembly_layer` | Python → Java | `{layerSetName, seq, newMaterial, newThickness}` | `swapLayer()` → recalculated response |
| `browse_layer_alternatives` | Python → Java | `{layerSetName, seq}` | `browseAssemblyLayers()` → product list |
| `save_assembly` | Python → Java | `{name, category, layers}` | `saveAssemblyTemplate()` → bomId |
| `show_composition` | Python → Java | `{wallType}` | Query material_layers → return layer stack |

### 6.2 Wire Protocol Examples

```json
// List templates
{"action":"listAssemblyTemplates", "category":"WALL"}
→ {"templates":[
     {"layerSetName":"Basic Wall:Exterior - Brick on Block",
      "category":"WALL", "layerCount":6, "totalThicknessMm":417,
      "uValueWm2K":0.35},
     ...
   ]}

// Get detail
{"action":"getAssemblyDetail", "layerSetName":"Basic Wall:Exterior - Brick on Block"}
→ {"layers":[
     {"seq":0, "material":"Masonry - Brick", "thicknessMm":92, "role":"CLADDING"},
     {"seq":1, "material":"Misc. Air Layers - Air Space", "thicknessMm":25, "role":"CAVITY"},
     {"seq":2, "material":"Insulation / Thermal Barriers - Rigid insulation",
      "thicknessMm":50, "role":"INSULATION"},
     {"seq":3, "material":"Masonry - Concrete Block", "thicknessMm":193, "role":"STRUCTURE"},
     {"seq":4, "material":"Metal - Stud Layer", "thicknessMm":41, "role":"STRUCTURE"},
     {"seq":5, "material":"Plasterboard", "thicknessMm":16, "role":"LINING"}
   ],
   "totalThicknessMm":417, "uValueWm2K":0.51}

// Swap a layer
{"action":"swapLayer", "layerSetName":"Basic Wall:Exterior - Brick on Block",
 "layerSequence":2, "newMaterial":"Rigid insulation", "newThicknessMm":100}
→ {"layers":[...updated...], "totalThicknessMm":467, "uValueWm2K":0.28}

// Query composition (existing verb from BIM_Designer_SRS.md §12.3)
{"action":"queryComposition", "wallType":"Basic Wall:Exterior - Brick on Block"}
→ {"layers":[...], "total_mm":417}
```

---

## 7. Constraints & Validation

### 7.1 Thickness Invariant

```
ASSERT: SUM(layer.thicknessMm for all layers) == parent.aabb_width_mm
```

When a layer is swapped, the parent AABB is updated to match the new total.
This is a **schema update** (update m_bom.aabb_width_mm), not a geometry
operation. The compiler handles the 3D consequences.

### 7.2 Layer Compatibility

Not every material fits every role. Role compatibility matrix:

| Role | Compatible materials |
|------|---------------------|
| CLADDING | Brick, block, timber cladding, render |
| CAVITY | Air gap (must be is_ventilated=1 or sealed) |
| INSULATION | Rigid, semi-rigid, spray foam, mineral wool |
| STRUCTURE | Block, concrete, timber frame, steel stud |
| LINING | Plasterboard, plaster, gypsum board |
| MEMBRANE | Vapour retarder, damp-proofing, EPDM |

**Phase 1:** Advisory only — warn on role mismatch, don't block.
**Phase 2:** Enforce via `ad_assembly_manifest.interface_type` matching.

### 7.3 InferenceEngine Integration

Layer placement is a constraint satisfaction problem (BIM_Designer_SRS.md §14):

```prolog
fits(Layer, Slot) :- thickness(Layer, T), max_thickness(Slot, MaxT), T =< MaxT.
compatible(Layer, Slot) :- role(Layer, R), accepts(Slot, R).
valid_stack(Layers) :- thickness_invariant(Layers), all_compatible(Layers).
```

**Phase 1 (G-7):** Direct validation in AssemblyBuilderService (no InferenceEngine).
**Phase 2 (G-9+):** Migrate to InferenceEngine rules with proof trees.

---

## 8. Witness Claims

### 8.1 Witness Matrix

| ID | Witness | What it proves | Acceptance criteria |
|----|---------|---------------|-------------------|
| W-ASM-LIST-1 | ListAssemblyTemplates | `listAssemblyTemplates("WALL")` returns ≥ 7 wall templates | count ≥ 7 (9 wall layer_set_names exist) |
| W-ASM-LIST-2 | ListAssemblyTemplates | `listAssemblyTemplates("FLOOR")` returns ≥ 3 floor templates | count ≥ 3 |
| W-ASM-LIST-3 | ListAssemblyTemplates | `listAssemblyTemplates("ROOF")` returns ≥ 1 roof templates | count ≥ 1 |
| W-ASM-DETAIL-1 | GetAssemblyDetail | Brick-on-Block returns 6 layers, total 417mm | layerCount=6, totalMm=417 |
| W-ASM-DETAIL-2 | GetAssemblyDetail | Layer order matches material_layers.sequence | layers[0].seq=0, layers[5].seq=5 |
| W-ASM-UVAL-1 | UValueCalculator | Brick-on-Block U ≈ 0.37 W/m²K (±0.05) | 0.32 ≤ U ≤ 0.42 |
| W-ASM-UVAL-2 | UValueCalculator | Swap insulation 50→100mm reduces U | U_after < U_before |
| W-ASM-UVAL-3 | UValueCalculator | Empty layers list → IllegalArgumentException | exception thrown |
| W-ASM-UVAL-4 | UValueCalculator | Air gap uses fixed R=0.18, not λ/d | R_airgap = 0.18 regardless of thickness |
| W-ASM-BROWSE-1 | BrowseAssemblyLayers | Browse insulation slot returns insulation-compatible materials | all results have insulation-class conductivity (λ < 0.1) |
| W-ASM-SWAP-1 | SwapLayer | Swap insulation 50→100mm → totalMm increases by 50 | new_total = old_total + 50 |
| W-ASM-SWAP-2 | SwapLayer | Swap recalculates U-value | U_after ≠ U_before |
| W-ASM-SAVE-1 | SaveAssemblyTemplate | Save creates m_bom + m_bom_line | bom_line count = layer count |
| W-ASM-DAO-1 | AssemblyDAO.loadLayers | SH Brick-on-Block normalises metres → mm | all thicknessMm > 1.0 |
| W-ASM-DAO-2 | AssemblyDAO.loadLayers | TE-style data (already mm) stays mm | no double-conversion |
| W-ASM-WRITE-1 | AssemblyWriteDAO | Per-instance save creates C_OrderLine children | child count = layer count |
| W-ASM-THICK-1 | ThicknessInvariant | SUM(layers) = parent total for all 20 templates | all 20 pass |

### 8.2 Non-Disturbance

G-7 must not disturb existing gates:

| Gate | Guard |
|------|-------|
| G1-COUNT | No changes to extraction pipeline |
| G2-VOLUME | No changes to verb geometry |
| G3-DIGEST | Seal set unchanged |
| G4-TAMPER | No AD_Val_Rule changes |
| G5-PROVENANCE | No GEO_ prefixes introduced |
| G6-ISOLATION | No cross-stone contamination |

**Test strategy:** Run `./scripts/run_RosettaStones.sh` after implementation.
All existing 136/136 must remain GREEN.

---

## 9. Implementation Plan

### Phase 1: Core (this session)

| Step | File | What |
|------|------|------|
| 1 | `migration/027_ad_material_thermal.sql` | Create ad_material_thermal + seed 29 materials |
| 2 | `AssemblyDAO.java` | Read material_layers + thermal properties |
| 3 | `UValueCalculator.java` | Stateless calculator (§4 formula) |
| 4 | `AssemblyBuilderService.java` | Orchestration: list/detail/swap/save |
| 5 | `DesignerAPI.java` | Add 5 new method signatures |
| 6 | `DesignerAPIImpl.java` | Wire to AssemblyBuilderService |
| 7 | `AssemblyBuilderTest.java` | 17 witnesses (W-ASM-*) |

### Phase 2: Wire + Governance (future session)

| Step | File | What |
|------|------|------|
| 8 | BlenderBridge verbs | `show_assembly_editor` + `update_assembly_layer` |
| 9 | AD_Val_Rule rows | U_VALUE check_method + jurisdiction thresholds |
| 10 | InferenceEngine | Layer constraint rules with proof trees |

### File Locations

```
BonsaiBIMDesigner/src/main/java/com/bim/designer/
├── api/DesignerAPI.java              (add records + methods)
├── api/DesignerAPIImpl.java          (wire to service)
├── assembly/AssemblyBuilderService.java  (NEW — orchestration)
├── assembly/AssemblyDAO.java             (NEW — read component_library.db)
├── assembly/AssemblyWriteDAO.java        (NEW — write output.db)
├── assembly/UValueCalculator.java        (NEW — stateless calc)

BonsaiBIMDesigner/src/test/java/com/bim/designer/
├── AssemblyBuilderTest.java              (NEW — 17 witnesses)

migration/
├── 027_ad_material_thermal.sql           (NEW — thermal properties table + seed)
```

---

## 10. Open Questions

| # | Question | Default assumption | Blocks |
|---|----------|-------------------|--------|
| Q1 | Should metal stud bridging use simplified 15% or actual stud dims? | 15% simplified | W-ASM-UVAL-1 tolerance |
| Q2 | Should browse alternatives query M_Product or just material_layers? | material_layers (simpler, no product mapping needed yet) | W-ASM-BROWSE-1 |
| Q3 | Should save-as-template go directly to BOM.db or through Promote? | Through Promote (existing governance path) | W-ASM-SAVE-1 |
| Q4 | Unit normalisation: trust layer_set_name prefix or magnitude check? | Prefix first, magnitude fallback | W-ASM-DAO-1 |

---

*Spec citation: BIM_Designer.md §18.2 Principle 4 + §18.5 MAKE path.
Traceability: TestArchitecture.md §Traceability Matrix — G-7 row to be added after implementation.*

*Copyright (c) 2025-2026 Redhuan D. Oon. MIT Licensed.*
