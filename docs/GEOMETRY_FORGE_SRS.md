# Geometry Forge — Formula-Driven Construction Piece Computation

> **Foundation:** [BBC](BOMBasedCompilation.md) §2.2.1 · [ShipYard](ShipYard.md) §6–8 · [EYES](EYES_SRS.md) §3–4 · [BIM_COBOL](BIM_COBOL.md) §17–18

<div class="bim-banner" markdown>
<b>Construction pieces are computed artifacts, not creative artifacts.</b> A rafter is trigonometry. A stair stringer is rise-over-run. A pipe bend is arc geometry. The BOM carries the parameters; the forge computes the shape. The library becomes fallback, not engine.
</div>

---

## 1. The Paradigm

Every BIM tool treats geometry as something a human draws. The BIM Compiler
already treats it as something a pipeline compiles. The forge takes the next
step: geometry is something **formulas compute** from construction parameters.

| Source | Geometry is... | Example |
|--------|---------------|---------|
| Revit / ArchiCAD | Drawn by human | Architect models a wall |
| Current pipeline | Extracted from IFC, placed by BOM | LOD from library, tack from BOM |
| **Forge** | **Computed from parameters** | Rafter = f(pitch, span, material) |

The library doesn't disappear. It becomes one source among two:

- **Library LOD** — pre-extracted shape. Fast, proven, sufficient for standard pieces.
- **Forge computation** — formula-derived shape. For pieces whose dimensions
  depend on site-specific parameters the library cannot anticipate.

Both produce the same output format (geometry records in output.db). Both
are verified by the same EYES proofs (§4). The pipeline doesn't know or
care which source produced the geometry.

---

## 2. The Four Inputs

The forge is not a new system. It is the **convergence** of four systems
that already exist independently:

```
BOM (what)  ──→  material, cross-section, grade, children
AD_Val_Rule (constraints)  ──→  max span, min pitch, fire rating, code
LOD (dimensions)  ──→  reference shape, archetype, scale band
EYES (proof)  ──→  dimensionless ratios, spatial coverage, containment
        │                │                    │                │
        └────────────────┴────────────────────┴────────────────┘
                                    │
                         ForgeEngine.compute()
                                    │
                         geometry record → output.db
```

**BOM** tells the forge WHAT to make. A rafter M_BOM_Line carries:
timber, 90×45mm cross-section, grade MGP10, qty 12.

**AD_Val_Rule** tells the forge the CONSTRAINTS. Rules from ERP.db:
max unsupported span 6000mm, min pitch 15° for tile roofing, fire
rating 60min for this zone.

**LOD dimensions** from component_library.db provide the REFERENCE SHAPE.
The library has a standard rafter at 4000mm. The forge scales or recomputes
for 5200mm using the same cross-section profile.

**EYES** (§3) provides VERIFICATION. Dimensionless ratios confirm the forged
piece has the correct archetype (ELONGATED for a rafter, PLANAR for a
panel). Spatial proofs confirm it sits correctly relative to neighbours.

---

## 3. Precedents Already in the Codebase

The forge pattern already operates under different names. These are not
analogies — they are the same computation:

| Verb / Module | Formula | Inputs | Output |
|---------------|---------|--------|--------|
| **TRIM** (§17.3) | `roofSurfaceZ = eaveZ + (ridgeZ−eaveZ) × ridgeFraction` | Roof AABB, wall centroid (x,y) | Trimmed wall height |
| **TILE** (§18) | `pos[i,j] = (origin + i×stepX, origin + j×stepY, Z)` | Surface bounds, step size | Grid positions for panels |
| **ROUTE SPRINKLERS** | `count = ⌊(width − spacing/2) / spacing⌋ + 1` | Room AABB, NFPA 13 rules | Head positions + pipe network |
| **ARRAY** | `count = ⌊(hostLen − 2×cover) / spacing⌋ + 1` | Host length, cover, spacing | Rebar positions |
| **STACK FLOORS** | `cumZ += allocatedHeight / 1000` | BOM child sequence, storey heights | Floor Z offsets (mutates BOM) |
| **ShipYard lofting** | `positionAt(station, waterline)` — cubic spline | Station offset table | Hull plate placement (x,y,z) |
| **AlignmentContext** | `elevationAt(x, y)` — terrain interpolation | 689 survey points | Element Z on terrain surface |

Every one of these takes **parameters from the BOM or DB**, applies a
**mathematical formula**, and produces **placement coordinates or
dimensions**. No human drew anything. The formula is the geometry.

The forge names this pattern and generalises it to pieces the library
doesn't have.

---

## 4. What the Forge Computes

Concrete examples. Each shows: inputs → formula → output → verification.

### 4.1 Rafter

```
Inputs:  pitch = 30°, span = 5200mm, material = MGP10, section = 90×45mm
Formula: length = span / cos(pitch) = 5200 / 0.866 = 6004mm
         cut_top = 90° − pitch = 60°
         cut_bottom = pitch = 30°
         birdsmouth_depth = section_height × 0.33 = 30mm
Verify:  EYES archetype = ELONGATED (length >> width >> depth)
         AD_Val_Rule: span ≤ 6000mm (PASS), pitch ≥ 15° (PASS)
Output:  geometry record (6004 × 90 × 45mm, two cut angles, one notch)
```

### 4.2 Stair Stringer

```
Inputs:  storey_height = 2700mm, tread = 250mm, riser = 180mm
Formula: step_count = ⌈storey_height / riser⌉ = 15
         actual_riser = storey_height / step_count = 180mm
         run = step_count × tread = 3750mm
         stringer_length = √(run² + storey_height²) = 4620mm
Verify:  EYES archetype = ELONGATED
         AD_Val_Rule: riser ≤ 190mm (PASS), tread ≥ 240mm (PASS)
Output:  geometry record (4620 × stringer_width × stringer_depth,
         15 notch positions along the hypotenuse)
```

### 4.3 Pipe Bend

```
Inputs:  angle = 90°, radius = 150mm, diameter = 32mm
Formula: arc_length = radius × angle_rad = 150 × π/2 = 236mm
         segment_count = ⌈arc_length / max_segment⌉
         fitting: elbow at bend point
Verify:  EYES archetype = COMPACT (bend fitting) or ELONGATED (straight runs)
         AD_Val_Rule: min_radius ≥ 5 × diameter (PASS)
Output:  N geometry records (straight segments + elbow fitting)
```

### 4.4 Hull Plate (ShipYard)

```
Inputs:  station = 12, waterline_range = [0, 4000], plate_width = 1200mm
Formula: positionAt(12, wl) → cubic spline → (x, y, z) for each plate row
         curvature = second derivative of spline at station
Verify:  EYES archetype = PLANAR (flat plate, curvature is in placement)
         G1-COUNT: plate_count = SUM(m_bom_line.qty)
Output:  geometry record (flat plate) + tack position on lofted surface
```

---

## 5. Architecture

### 5.1 All Backend

The forge is a pure computation. No viewport, no user interaction during
computation. The frontend fetches the result the same way it fetches a
compiled building today.

```
Verb: FORGE RAFTER pitch:30 span:5200 material:MGP10
                    │
         Java ForgeEngine (backend)
         ├── Read BOM: timber 90×45 rafter line
         ├── Read AD_Val_Rule: max_span, min_pitch
         ├── Read LOD: standard rafter from library (reference)
         ├── Compute: length, cut angles, notch
         ├── Verify: EYES ratios, rule compliance
         └── Return: VerbResult<ForgePayload>
                    │
         Pipeline writes geometry record → output.db
                    │
         Frontend: fetch output.db → render (same as today)
```

Touch-up (post-forge adjustment) is the only interactive frontend work:
user sees the forged result, nudges a position via `moveChain()`, backend
re-verifies via EYES.

### 5.2 ForgeEngine Interface

```java
public interface ForgeEngine {
    /**
     * Compute a construction piece from parameters.
     *
     * @param ctx       verb context (bomConn, componentConn, outputConn)
     * @param pieceType archetype identifier (RAFTER, STAIR, PIPE_BEND, etc.)
     * @param params    named parameters (pitch, span, material, etc.)
     * @return geometry record(s) with full BOM traceability
     */
    ForgeResult compute(VerbContext ctx, String pieceType,
                        Map<String, String> params);
}
```

```java
public record ForgeResult(
    boolean pass,
    String pieceType,
    String summary,
    List<GeometryRecord> records,    // computed geometry
    List<ComplianceEntry> compliance, // rule check results
    EyesFingerprint fingerprint       // verification ratios
) {}
```

```java
public record GeometryRecord(
    String bomLineId,          // traces to M_BOM_Line
    String productId,          // traces to M_Product
    double lengthMm, double widthMm, double depthMm,
    double[] cutAngles,        // piece-specific fabrication data
    double[] notchPositions,   // piece-specific fabrication data
    Point3D placementPosition, // world coordinates
    double rotation            // placement rotation
) {}
```

### 5.3 Relationship to Existing Patterns

The forge follows the same verb architecture. ForgeEngine is called by
a FORGE verb, just as SprinklerGrid is called by ROUTE SPRINKLERS:

| Layer | ROUTE SPRINKLERS | FORGE RAFTER |
|-------|-----------------|--------------|
| Verb class | RouteSprinklersVerb | ForgeVerb |
| Geometry helper | SprinklerGrid, PipeRouter | **ForgeEngine** |
| Compliance helper | ComplianceChecker | Same ComplianceChecker |
| Output | VerbResult\<SprinklerPayload\> | VerbResult\<ForgePayload\> |
| Persistence | In-memory positions | In-memory geometry records |
| Emission | Via PLACE BOM | Via PLACE BOM (same path) |

---

## 6. BBC §2.2.1 Compatibility

The "No Parametric Mesh" rule forbids GEO_ hash geometry — Blender-style
procedural generation with no BOM traceability. The forge is the opposite:

| BBC §2.2.1 Concern | Forge Answer |
|---------------------|-------------|
| No GEO_ hash prefix | Forge records carry LOD_ prefix (library-compatible format) |
| Every element traces to BOM | ForgeResult.bomLineId → M_BOM_Line → M_Product |
| ASI controls sizing | Forge reads ASI parameters; forge results stored as ASI overrides |
| G5-PROVENANCE verifiable | Forge records include provenance chain (formula + inputs + rule citations) |
| No `createBoxGeometry` | Forge computes dimensions + cut data, not mesh vertices |

The forge extends BBC §2.2.1 — it does not violate it. The rule's intent
is traceability, not a ban on computation. MiTek has computed timber
geometry from parameters for 30+ years with full BOM traceability.

---

## 7. When Library, When Forge

The library is the fast path. The forge is for what the library can't cover.

| Scenario | Source | Why |
|----------|--------|-----|
| Standard wall 2700×150mm | Library LOD | Pre-extracted, proven, fast |
| Rafter at 30° pitch, 5200mm span | **Forge** | Library has 4000mm at 30° — site needs 5200mm |
| Standard door 900×2100mm | Library LOD | Exact match in catalog |
| Stair for 2850mm storey (non-standard) | **Forge** | Step geometry depends on actual storey height |
| Ship hull plate | **Forge** | Every plate is unique (curvature varies by station) |
| Standard pipe fitting (elbow, tee) | Library LOD | Catalog part |
| Pipe run at site-specific angle | **Forge** | Angle + length depend on routing |

**Decision rule:** If component_library.db has an exact or scalable match,
use the library. If the piece dimensions depend on site-specific parameters
that can't be resolved by ASI scaling alone, forge it.

---

## 7b. LOD Promotion — Forge Output Graduates to Library

A forged piece is not throwaway. Once approved, it becomes a reusable LOD
in `component_library.db` — the same lifecycle as BOM promotion
([ProjectOrderBlueprint](ProjectOrderBlueprint.md) §4, DocAction=Approve).

```
FORGE SLOPE_CUT pitch:33.7 span:5200 ...
    → ForgeResult (in-memory, promotable=true)
    → EYES verification (archetype=ELONGATED, ratios in range)
    → User inspects in viewport (touch-up if needed)
    → DocAction=APPROVE
    → PROMOTE LOD → component_library.db (new LOD_ entry)
    → Next compilation: library hit, no re-forge
```

**The user chooses the promotion format:**

| Choice | What's promoted | When to use |
|--------|----------------|-------------|
| **Singular mesh LOD** | One M_Product with geometry dimensions | Simple piece (rafter, pipe bend) — one shape, reusable as-is |
| **BOM** | M_BOM + M_BOM_Line tree | Compound piece (dome section, barrel vault) — assembly of sub-pieces |

The user selects this at Approve time. A dome section with 72 panels might
be promoted as a BOM (parent dome + 72 panel children), while a rafter is
promoted as a single M_Product LOD. Both paths use the existing promotion
machinery — the forge just produces the input.

**ForgeResult.promotable** is true when all EYES checks pass and all
compliance rules pass. The Approve action is gated on this flag.

---

## 8. Verification

EYES (§3) verifies forged pieces the same way it verifies extracted pieces:

1. **Archetype check** — forged rafter must classify as ELONGATED
   (planarity < 0.15, elongation < 0.40). If it classifies as PLANAR,
   something is wrong.

2. **Dimensionless ratio bounds** — forged piece ratios must fall within
   the expected range for its IFC class. A rafter with squareness > 0.5
   is suspiciously thick.

3. **Spatial proofs** — P27/P28 patterns extend to forged pieces. A forged
   rafter must sit within the roof envelope. A forged stair must connect
   two storeys within tolerance.

4. **G1-G6 gates** — forged pieces enter output.db through the same
   pipeline. G1-COUNT counts them. G3-DIGEST includes them. G5-PROVENANCE
   traces them.

New proof (future):
- **P29 FORGE_TRACEABILITY** — every forged geometry record traces to a
  BOM line + formula + parameter set. The formula is reproducible: same
  inputs = same output.

---

## 9. Industry Precedent

The forge is not novel. Domain-specific versions exist and are mature:

| Domain | Tool | What it computes | Years in production |
|--------|------|-----------------|-------------------|
| Timber framing | MiTek, Pryda, Alpine | Trusses, rafters, cut lists from span+pitch+load | 30+ years |
| Steel connections | Tekla components, IDEA StatiCa | Bolt patterns, plate sizes from load+member | 20+ years |
| Precast concrete | Allplan Precast | Panel splits, rebar cages from rules | 15+ years |
| Ship hulls | NAPA, ShipConstructor | Hull plates from station offsets | 40+ years |
| Catalog assembly | Bryden Wood P-DfMA | Buildings from standardized kits | 10+ years |

What none of them do: generalise across domains with one engine. Each is a
domain-specific compiler. The forge aims to be the general case — same
ForgeEngine interface, different piece-type implementations, same BOM
traceability, same EYES verification.

---

## 9b. Prior Art in This Codebase

The forge pattern was designed twice before under different names:

**Mesh2Library** (`docs/Mesh2Library.txt`) — parametric mesh generation for
roofs, stairs, tanks. Defines:
- `ParametricMesh` sealed interface: `GableRoofMesh`, `HipRoofMesh`,
  `StairFlightMesh`, `CylindricalTankMesh`
- `ad_parametric_mesh` + `ad_parametric_mesh_param` tables — formula
  parameters as metadata rows with provenance
- `MeshResult generate(MeshParameters params)` — same contract as ForgeEngine
- `ad_roof_preset` — region × building_type → mesh_type (smart defaults)
- BOM leaf integration: fabricated mesh sits in GGF→GF→Parent→Child→Leaf hierarchy

**TopologyMaker** (`TopologyMaker/docs/TOPOLOGY_MAKER.md`) — site layout
generation from brief. `GridStrategy.subdivide()` + `UbblValidator` +
`DocStatus DR→IP→CO` lifecycle. Batch process, not a verb.

**Relationship to ForgeEngine:**

| | Mesh2Library | TopologyMaker | Geometry Forge |
|---|---|---|---|
| **What** | Roof/stair mesh from params | Site layout from brief | Any piece from params |
| **Interface** | `ParametricMesh.generate()` | `TopologyBatchProcess.complete()` | `ForgeEngine.compute()` |
| **Metadata** | `ad_parametric_mesh_param` | `ad_typology_pattern` | `ad_forge_formula` |
| **Compliance** | BBC §2.2.1 (sealed types) | `UbblValidator` | `ComplianceChecker` |
| **Lifecycle** | BOM leaf (LOD promotion) | `DocStatus DR→IP→CO` | `ForgeResult.promotable` |

**Decision: Forge absorbs Mesh2Library (Option B).**

ForgeEngine is the single interface. `ad_forge_formula` is the single
metadata table. `Mesh2Library.txt` is archived as prior art — its design
is correct, just renamed. `ParametricMesh` (if it exists in code) is
either deprecated in favour of ForgeEngine or wrapped by a ForgeEngine
adapter. TopologyMaker stays separate (batch process, not verb-level).

One name, one interface, one table. No confusion for future sessions.

---

## 10. Formula-as-Metadata — Table-Driven Forge

The forge formulas should not be hardcoded Java. They should be
**metadata rows** in a table — the same pattern as AD_Val_Rule,
ad_fp_coverage, ad_space_type_mep_bom.

```sql
CREATE TABLE ad_forge_formula (
    AD_Forge_Formula_ID  INTEGER PRIMARY KEY AUTOINCREMENT,
    PieceType            TEXT NOT NULL,     -- 'SLOPE_CUT', 'DOME_SECTION', etc.
    ParamName            TEXT NOT NULL,     -- 'length', 'cut_angle_top', etc.
    Expression           TEXT NOT NULL,     -- 'span / cos(pitch_rad)'
    InputParams          TEXT,              -- 'pitch,span,width,depth' (CSV)
    Description          TEXT,
    IsActive             INTEGER DEFAULT 1
);
```

**Example rows:**

| PieceType | ParamName | Expression | InputParams |
|-----------|-----------|-----------|-------------|
| SLOPE_CUT | pitch_rad | pitch * 3.14159265 / 180 | pitch |
| SLOPE_CUT | length | span / cos(pitch_rad) | pitch_rad,span |
| SLOPE_CUT | cut_angle_top | 90 - pitch | pitch |
| SLOPE_CUT | cut_angle_bottom | pitch | pitch |
| SLOPE_CUT | birdsmouth_depth | depth * 0.33 | depth |
| STAIR_FLIGHT | step_count | ceil(height / riser) | height,riser |
| STAIR_FLIGHT | actual_riser | height / step_count | height,step_count |
| STAIR_FLIGHT | total_run | step_count * tread | step_count,tread |
| STAIR_FLIGHT | stringer_length | sqrt(total_run * total_run + height * height) | total_run,height |
| DOME_SECTION | phi | (3.14159265 / 2) * (ring + 1) / (rings + 1) | ring,rings |
| DOME_SECTION | ring_radius | radius * sin(phi) | radius,phi |
| DOME_SECTION | ring_z | base_z + radius * (1 - cos(phi)) | base_z,radius,phi |

**Evaluation order:** Formulas reference other formula outputs (pitch_rad →
length). The ForgeEngine evaluates in dependency order (topological sort —
same Kahn's algorithm as CalloutEngine). Circular dependencies rejected
at definition time.

**Advantages:**
- New piece types added by INSERTing rows, not writing Java classes
- Formulas are auditable, versionable, exportable
- Same expression evaluator used by AD_Val_Rule compliance checks
- Community can contribute piece-type formulas as SQL migration files
- Compliance rules and geometry formulas live in the same evaluation engine

**Phase 1 can start with hardcoded Java** (5 starter pieces). Phase 2
migrates the formulas to `ad_forge_formula` rows. The ForgeEngine interface
stays the same — one implementation reads from Java, another reads from DB.

---

## 11. Phases

All phases are SPEC ONLY. No timelines.

**Phase 0 — Name the pattern.** This spec. Recognise that TRIM, TILE,
ROUTE, ARRAY, STACK, and ShipYard lofting are all instances of the same
computation: parameters + rules → geometry.

**Phase 1 — ForgeEngine interface + 5 starter pieces.** Java interface
(§5.2). Five hardcoded engines: SLOPE_CUT, STAIR_FLIGHT, PIPE_BEND,
DOME_SECTION, BARREL_VAULT. FORGE verb in VerbRegistry.

**Phase 2 — Formula-as-metadata.** Migrate hardcoded formulas to
`ad_forge_formula` table (§10). Expression evaluator with topological
sort. New piece types become SQL INSERTs.

**Phase 3 — EYES P29.** Forge traceability proof. Every forged record
traces to BOM + formula + parameters.

**Phase 3b — LOD Promotion.** DocAction=Approve on ForgeResult writes to
component_library.db. User chooses singular LOD or BOM (§7b).

**Phase 4 — Library graduation.** When a PLACE BOM encounters a piece
the library can't match (no LOD within ASI scaling range), the pipeline
falls through to the forge. Library becomes cache; forge becomes engine.

**Phase 5 — Dynamic + scalable.** Once formulas live in `ad_forge_formula`,
the forge engine is fully data-driven. Adding a new piece type = adding
rows, not code. Community contributions, domain specialists, even
LLM-assisted formula generation — all produce SQL INSERTs. The engine
evaluates whatever formulas it finds. Same pattern as AD_Val_Rule:
rules grow without engine changes.

---

## 12. Scaffolding (Buildable Now)

### 12.1 ForgeEngine Interface

The interface (§5.2) is stable regardless of which piece types are
implemented first. `compute(ctx, pieceType, params) → ForgeResult`.

### 12.2 GeometryRecord

The output record (§5.2) is the same for all piece types. Dimensions +
cut data + placement + BOM traceability. This can be defined now.

### 12.3 FORGE Verb Shell

A verb that parses `FORGE <type> [key:value...]`, looks up a ForgeEngine
implementation by type, and dispatches. Returns fail("unknown piece type")
for unregistered types. Registration is pluggable — same pattern as
VerbRegistry.

### 12.4 EYES Archetype Bounds Table

A lookup table mapping piece types to expected EYES archetype ranges.
RAFTER → ELONGATED (planarity < 0.15, elongation < 0.40). This is pure
data — no formula code needed.

### 12.5 ComplianceChecker Reuse

The existing ComplianceChecker (used by ROUTE SPRINKLERS) already evaluates
rules from DB. The forge uses the same checker with different rule sets.
No new compliance infrastructure needed.

---

*Status: Phase 1 DONE (S99-forge). ForgeEngine + 5 starter pieces + ForgeVerb + W-FORGE-1..8. Phases 2-5 pending.*
