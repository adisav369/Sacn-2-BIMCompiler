# DONE
# Geometry Forge — Scaffolding + Five Starter Pieces

**Priority:** The spec is written (`docs/GEOMETRY_FORGE_SRS.md`). This
prompt builds the scaffolding AND three starter ForgeEngine implementations
— common construction formulas every building needs.

You are a coder for bim-compiler. One bounded task.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** The scaffolding follows existing patterns
(Verb interface, VerbResult, geometry helpers). The formulas are textbook
construction maths — don't invent, compute.

## Read first

1. `docs/GEOMETRY_FORGE_SRS.md` — the full spec. §4 (examples), §5 (arch),
   §9b (prior art: Mesh2Library + TopologyMaker), §10 (formula-as-metadata).
2. **`docs/Mesh2Library.txt`** — PRIOR ART (archived). Same pattern as
   forge, earlier draft. ForgeEngine absorbs this. `ad_parametric_mesh_param`
   becomes `ad_forge_formula`. If `ParametricMesh` interface exists in code
   (`DAGCompiler/src/main/java/com/bim/compiler/mesh/ParametricMesh.java`),
   deprecate it — ForgeEngine is the single interface going forward.
4. `BIM_COBOL/src/main/java/com/bim/cobol/Verb.java` — the Verb interface.
5. `BIM_COBOL/src/main/java/com/bim/cobol/VerbResult.java` — result record.
6. `BIM_COBOL/src/main/java/com/bim/cobol/VerbRegistry.java` — registration.
7. `BIM_COBOL/src/main/java/com/bim/cobol/verb/TrimWallsToRoofVerb.java`
   — precedent: `slopeAngle = atan2(rise, run)`. Same trig used by SLOPE_CUT.
8. `BIM_COBOL/src/main/java/com/bim/cobol/verb/StackFloorsVerb.java`
   — precedent: cumulative Z. Same pattern used by STAIR_FLIGHT.
9. `BIM_COBOL/src/main/java/com/bim/cobol/geometry/PipeRouter.java`
   — precedent: pipe segment generation. Same pattern used by PIPE_BEND.
10. `BIM_COBOL/src/main/java/com/bim/cobol/geometry/` — existing helpers.
9. `DAGCompiler/src/main/java/com/bim/compiler/geometry/Point3D.java`

## Part A: Build scaffolding

### A1. ForgeEngine interface

Create `BIM_COBOL/src/main/java/com/bim/cobol/forge/ForgeEngine.java`:

```java
package com.bim.cobol.forge;

import com.bim.cobol.VerbContext;
import java.util.Map;

/**
 * Computes construction piece geometry from parameters.
 * Each implementation handles one piece type (SLOPE_CUT, STAIR, etc.).
 *
 * // Implementing GEOMETRY_FORGE_SRS.md §5.2
 */
public interface ForgeEngine {
    /** Piece type this engine handles (e.g., "SLOPE_CUT"). */
    String pieceType();

    /** Compute geometry from parameters. */
    ForgeResult compute(VerbContext ctx, Map<String, String> params);
}
```

### A2. ForgeResult + GeometryRecord

Create `BIM_COBOL/src/main/java/com/bim/cobol/forge/ForgeResult.java`:

```java
public record ForgeResult(
    boolean pass,
    String pieceType,
    String summary,
    List<GeometryRecord> records,
    List<String> compliance,  // rule check summaries
    String error
) {
    public static ForgeResult ok(String pieceType, String summary,
            List<GeometryRecord> records, List<String> compliance) {
        return new ForgeResult(true, pieceType, summary, records, compliance, null);
    }
    public static ForgeResult fail(String pieceType, String error) {
        return new ForgeResult(false, pieceType, null, List.of(), List.of(), error);
    }
}
```

Create `BIM_COBOL/src/main/java/com/bim/cobol/forge/GeometryRecord.java`:

```java
public record GeometryRecord(
    String bomLineId,       // traces to M_BOM_Line
    String productId,       // traces to M_Product
    double lengthMm,
    double widthMm,
    double depthMm,
    Map<String, Double> fabrication,  // cut_angle_top, cut_angle_bottom, notch_depth, etc.
    double placementX,      // world coordinates (meters)
    double placementY,
    double placementZ,
    double rotation         // degrees
) {}
```

### A3. ForgeVerb (dispatch with registered engines)

Create `BIM_COBOL/src/main/java/com/bim/cobol/verb/ForgeVerb.java`:

```java
/**
 * FORGE <piece_type> [key:value ...]
 *
 * Dispatches to registered ForgeEngine implementations.
 *
 * // Implementing GEOMETRY_FORGE_SRS.md §5, §10
 */
public class ForgeVerb implements Verb<ForgeResult> {

    private final Map<String, ForgeEngine> engines = new LinkedHashMap<>();

    public ForgeVerb() {
        register(new SlopeCutForge());
        register(new StairFlightForge());
        register(new PipeBendForge());
        register(new DomeSectionForge());
        register(new BarrelVaultForge());
    }

    private void register(ForgeEngine engine) {
        engines.put(engine.pieceType(), engine);
    }

    @Override
    public String keyword() { return "FORGE"; }

    @Override
    public VerbResult<ForgeResult> execute(VerbContext ctx, String... args)
            throws SQLException {
        if (args.length == 0)
            return VerbResult.fail(keyword(),
                    "usage: FORGE <piece_type> [key:value ...] — types: "
                    + engines.keySet(), null);

        String pieceType = args[0].toUpperCase();
        ForgeEngine engine = engines.get(pieceType);
        if (engine == null)
            return VerbResult.fail(keyword(),
                    "unknown piece type: " + pieceType
                    + " (registered: " + engines.keySet() + ")", null);

        Map<String, String> params = new LinkedHashMap<>();
        for (int i = 1; i < args.length; i++) {
            String[] kv = args[i].split(":", 2);
            if (kv.length == 2) params.put(kv[0].toLowerCase(), kv[1]);
        }

        ForgeResult result = engine.compute(ctx, params);

        if (result.pass())
            return VerbResult.ok(keyword(), result.summary(), result);
        else
            return VerbResult.fail(keyword(), result.error(), result);
    }
}
```

### A4. Register in VerbRegistry

Add `reg.register(new ForgeVerb());` in `VerbRegistry.createDefault()`,
after the HELLO WORLD line:
```java
// Geometry Forge — formula-driven construction pieces (GEOMETRY_FORGE_SRS.md §10)
reg.register(new ForgeVerb());
```

---

## Part B: Three starter ForgeEngine implementations

All in `BIM_COBOL/src/main/java/com/bim/cobol/forge/`.

### B1. SlopeCutForge — cut a member to an angle

Every rafter, every truss web, every hip/valley member. Pure trigonometry.
Precedent: TrimWallsToRoofVerb.slopeAngle().

**Grammar:** `FORGE SLOPE_CUT pitch:30 span:5200 width:90 depth:45`

**Formulas:**
```
pitch_rad = pitch × π / 180
length = span / cos(pitch_rad)
cut_angle_top = 90° − pitch
cut_angle_bottom = pitch
birdsmouth_depth = depth × 0.33  (standard 1/3 seat cut)
```

**Required params:** `pitch` (degrees), `span` (mm), `width` (mm), `depth` (mm)
**Optional:** `birdsmouth` (mm, overrides default 1/3 seat)

**Compliance checks:**
- pitch >= 5° and <= 60° (practical range)
- span <= 12000mm (unsupported timber span limit, conservative)
- length > 0

**Output:** Single GeometryRecord with:
- lengthMm = computed length
- widthMm, depthMm = from params
- fabrication: {cut_angle_top, cut_angle_bottom, birdsmouth_depth, pitch_degrees}

```java
/**
 * SLOPE_CUT — compute a member cut to a pitch angle.
 *
 * Rafter, hip, valley, truss web — any member where length and cut
 * angles derive from pitch + span. Pure trigonometry.
 *
 * Precedent: TrimWallsToRoofVerb.slopeAngle() uses atan2(rise, run).
 * This is the inverse: given angle, compute length and cuts.
 *
 * // Implementing GEOMETRY_FORGE_SRS.md §4.1
 */
```

### B2. StairFlightForge — compute stair geometry from rise/run

Every multi-storey building. Pythagoras + integer division.
Precedent: StackFloorsVerb cumulative Z.

**Grammar:** `FORGE STAIR_FLIGHT height:2700 tread:250 riser:180 width:900`

**Formulas:**
```
step_count = ceil(height / riser)
actual_riser = height / step_count     (even distribution)
total_run = step_count × tread
stringer_length = sqrt(total_run² + height²)
going_angle = atan2(height, total_run) × 180/π
```

**Required params:** `height` (mm, storey height), `tread` (mm), `riser` (mm), `width` (mm)

**Compliance checks:**
- riser >= 150mm and <= 220mm (UBBL/IBC range)
- tread >= 220mm and <= 350mm
- 2×riser + tread >= 550mm and <= 700mm (Blondel's formula — the 350-year-old step comfort rule)
- going_angle <= 42° (max stair pitch)

**Output:** Two GeometryRecords:
1. Stringer: lengthMm = stringer_length, widthMm = width, depthMm = stringer_depth
   fabrication: {step_count, actual_riser, tread, going_angle, notch_positions[]}
2. Landing: if step_count > 0, a flat record at (0, 0, height) for the top landing

```java
/**
 * STAIR_FLIGHT — compute stair geometry from storey height + step dimensions.
 *
 * Pythagoras + integer ceiling + Blondel's comfort formula (2R+G, 1675).
 * Precedent: StackFloorsVerb cumulative Z offset.
 *
 * // Implementing GEOMETRY_FORGE_SRS.md §4.2
 */
```

### B3. PipeBendForge — compute bend geometry from angle/radius

Every MEP system. Arc geometry.
Precedent: PipeRouter segment generation.

**Grammar:** `FORGE PIPE_BEND angle:90 radius:150 diameter:32`

**Formulas:**
```
angle_rad = angle × π / 180
arc_length = radius × angle_rad
segment_length = 2 × radius × sin(angle_rad / 2)   (chord length for single-segment approx)
```

**Required params:** `angle` (degrees), `radius` (mm), `diameter` (mm)

**Compliance checks:**
- radius >= 3 × diameter (minimum bend radius per AS/NZS 3500, similar in NFPA)
- angle > 0° and <= 180°
- diameter > 0

**Output:** Two GeometryRecords:
1. Pipe run: lengthMm = arc_length, widthMm = diameter, depthMm = diameter
   fabrication: {bend_angle, bend_radius, chord_length}
2. Fitting: elbow/bend fitting at the bend point
   fabrication: {fitting_type: "ELBOW", angle}

```java
/**
 * PIPE_BEND — compute pipe bend geometry from angle + radius + diameter.
 *
 * Arc geometry. Compliance: min bend radius per plumbing code.
 * Precedent: PipeRouter segment generation in RouteSprinklersVerb.
 *
 * // Implementing GEOMETRY_FORGE_SRS.md §4.3
 */
```

### B4. DomeSectionForge — compute dome panel positions from spherical geometry

Terminal (TE) dome, mosque domes, observatory domes. Spherical coordinates.
Precedent: TileGrid (grid fill on flat surface → grid fill on sphere).

**Grammar:** `FORGE DOME_SECTION radius:8000 rings:6 segments:12 base_z:15000`

**Formulas:**
```
For each ring i ∈ [0, rings):
    phi = (π/2) × (i + 1) / (rings + 1)     (latitude from pole, skip pole itself)
    ring_radius = radius × sin(phi)
    ring_z = base_z + radius × (1 - cos(phi))

    For each segment j ∈ [0, segments):
        theta = 2π × j / segments              (longitude)
        x = ring_radius × cos(theta)
        y = ring_radius × sin(theta)
        panel_width = 2 × ring_radius × sin(π / segments)  (chord width at this ring)
        panel_height = radius × (π/2) / (rings + 1)        (arc height per ring)
```

**Required params:** `radius` (mm), `rings` (int), `segments` (int), `base_z` (mm)

**Compliance checks:**
- rings >= 2 and <= 50
- segments >= 4 and <= 64
- radius > 0
- panel_width > 100mm (practical minimum for a real panel)

**Output:** `rings × segments` GeometryRecords, each with:
- widthMm = panel_width (varies per ring — wider at equator, narrower at crown)
- depthMm = panel_height
- placement: (x, y, ring_z) in world coordinates
- fabrication: {ring_index, segment_index, phi_degrees, theta_degrees}

```java
/**
 * DOME_SECTION — compute panel positions on a spherical dome.
 *
 * Spherical coordinates: phi (latitude) × theta (longitude) → (x, y, z).
 * Each ring is a horizontal circle; panels are curved quads approximated
 * as flat panels (same principle as ShipYard: curvature is in placement,
 * panels are flat library LODs).
 *
 * Precedent: TileGrid flat panel fill → DomeSectionForge spherical fill.
 *
 * // Implementing GEOMETRY_FORGE_SRS.md §4 (dome variant)
 */
```

### B5. BarrelVaultForge — compute vault rib positions from cylindrical geometry

Terminal barrel vaults, warehouse roofs, church naves. Cylindrical coordinates.
Precedent: TRIM tent model (linear slope) → circular arc.

**Grammar:** `FORGE BARREL_VAULT span:12000 length:20000 ribs:10 rise:4000`

**Formulas:**
```
// Circular arc from span + rise (3-point circle: two eave points + crown)
// chord = span, sagitta = rise
R = (span²/4 + rise²) / (2 × rise)     (radius of curvature)
theta_max = 2 × asin(span / (2 × R))    (total arc angle)

For each rib i ∈ [0, ribs):
    y = length × i / (ribs - 1)          (rib position along vault length)

    For each side j ∈ [0, arc_segments):
        theta = -theta_max/2 + theta_max × j / (arc_segments - 1)
        x = R × sin(theta)               (horizontal offset from crown)
        z = base_z + rise - R × (1 - cos(theta))  (height from eave)
```

**Required params:** `span` (mm), `length` (mm), `ribs` (int), `rise` (mm)
**Optional:** `arc_segments` (int, default 8 per rib — enough for smooth visual)

**Compliance checks:**
- rise > 0 and rise <= span (can't be taller than wide — that's a pointed arch, different forge)
- ribs >= 2
- R > 0 (degenerate arc check)

**Output:** `ribs × arc_segments` GeometryRecords for rib positions, plus
`ribs` GeometryRecords for the rib members themselves:
- Rib member: lengthMm = arc_length per rib = R × theta_max
- fabrication: {radius_of_curvature, arc_angle, rib_index}

```java
/**
 * BARREL_VAULT — compute rib positions on a cylindrical vault.
 *
 * Circular arc cross-section: span + rise → radius of curvature →
 * theta per arc segment → (x, z) positions. Ribs spaced along length.
 *
 * Precedent: TrimWallsToRoofVerb tent model (linear slope) generalised
 * to circular arc. Same idea: a function maps position → height.
 * Tent model: roofZ = linear. Barrel vault: roofZ = circular.
 *
 * // Implementing GEOMETRY_FORGE_SRS.md §4 (barrel vault variant)
 */
```

---

## Overlap boundaries — read carefully

**FORGE vs COVER WITH COMPOUND_ROOF:** COVER WITH is a building-level
composition verb — it reads pre-existing mesh definitions from
component_library.db and places a complete roof assembly (hip, gable,
valley stitching). FORGE DOME_SECTION/BARREL_VAULT compute piece-level
geometry that does NOT exist in the library yet. They are sequential,
not competing: forge produces new pieces → promote to library → COVER
WITH can use them in future compilations. Do NOT duplicate CoverWithRoofVerb
or ValleyStitcher logic inside the dome/vault forge engines.

**FORGE can reuse existing geometry helpers.** The forge engines MAY call
TileGrid, LinearArray, or other helpers from `BIM_COBOL/geometry/` if the
maths is the same (e.g., dome panel grid is a spherical TileGrid variant).
Do NOT duplicate grid/array maths that already exists. Import and call.

**FORGE vs STACK FLOORS:** StackFloorsVerb mutates BOM (writes dz to
m_bom_line). STAIR_FLIGHT forge returns in-memory geometry records only —
no DB writes. Different levels. Do NOT write to BOM from forge engines.

## What NOT to do

- Do NOT modify existing verbs (TRIM, TILE, ROUTE, ARRAY, STACK, COVER WITH)
- Do NOT modify existing geometry helpers (ValleyStitcher, TileGrid, etc.)
  — you MAY call them, do NOT change them
- Do NOT modify the compilation pipeline
- Do NOT add external dependencies
- Do NOT wire forge output to PLACE BOM yet (that's a future prompt)
- Do NOT query the database in these five engines (they're pure maths —
  VerbContext is available but not needed for Phase 1 formulas)

## Tests

Create `BIM_COBOL/src/test/java/com/bim/cobol/forge/ForgeVerbTest.java`:

```java
/**
 * Geometry Forge — starter piece tests.
 * @Traces GEOMETRY_FORGE_SRS.md §4, §10
 */
class ForgeVerbTest {

    private static VerbRegistry registry;
    private static VerbContext ctx;
    private static Connection bomConn;

    @BeforeAll
    static void setUp() throws Exception {
        // Minimal BOM.db — forge pieces are pure maths, don't need BOM data
        bomConn = DriverManager.getConnection("jdbc:sqlite::memory:");
        ctx = VerbContext.ofBom(bomConn);
        registry = VerbRegistry.createDefault();
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (bomConn != null) bomConn.close();
    }

    /**
     * W-FORGE-1: SLOPE_CUT computes rafter length from pitch + span.
     */
    @Test
    void slopeCutComputesLength() {
        VerbResult<?> r = registry.dispatch(ctx, "FORGE SLOPE_CUT pitch:30 span:5200 width:90 depth:45");
        assertTrue(r.pass(), "Should pass: " + r.summary());
        ForgeResult fr = (ForgeResult) r.payload();
        assertEquals(1, fr.records().size());
        GeometryRecord rec = fr.records().get(0);
        // length = 5200 / cos(30°) = 5200 / 0.866 ≈ 6004mm
        assertEquals(6004, rec.lengthMm(), 1.0);
        assertTrue(rec.fabrication().containsKey("cut_angle_top"));
        assertTrue(rec.fabrication().containsKey("cut_angle_bottom"));
    }

    /**
     * W-FORGE-2: STAIR_FLIGHT computes step count and stringer from height.
     */
    @Test
    void stairFlightComputesSteps() {
        VerbResult<?> r = registry.dispatch(ctx, "FORGE STAIR_FLIGHT height:2700 tread:250 riser:180 width:900");
        assertTrue(r.pass(), "Should pass: " + r.summary());
        ForgeResult fr = (ForgeResult) r.payload();
        assertTrue(fr.records().size() >= 1);
        GeometryRecord stringer = fr.records().get(0);
        // step_count = ceil(2700/180) = 15
        assertEquals(15.0, stringer.fabrication().get("step_count"), 0.01);
        // stringer = sqrt(3750² + 2700²) ≈ 4621mm
        assertEquals(4621, stringer.lengthMm(), 2.0);
        // Blondel: 2×180 + 250 = 610 (in 550-700 range)
        assertTrue(fr.compliance().stream().allMatch(c -> c.contains("PASS")));
    }

    /**
     * W-FORGE-3: PIPE_BEND computes arc length from angle + radius.
     */
    @Test
    void pipeBendComputesArc() {
        VerbResult<?> r = registry.dispatch(ctx, "FORGE PIPE_BEND angle:90 radius:150 diameter:32");
        assertTrue(r.pass(), "Should pass: " + r.summary());
        ForgeResult fr = (ForgeResult) r.payload();
        assertTrue(fr.records().size() >= 1);
        GeometryRecord bend = fr.records().get(0);
        // arc = 150 × π/2 ≈ 236mm
        assertEquals(236, bend.lengthMm(), 1.0);
        // radius >= 3 × diameter: 150 >= 96 → PASS
        assertTrue(fr.compliance().stream().allMatch(c -> c.contains("PASS")));
    }

    /**
     * W-FORGE-4: Unknown piece type returns structured failure.
     */
    @Test
    void unknownTypeReturnsFail() {
        VerbResult<?> r = registry.dispatch(ctx, "FORGE XYZZY pitch:30");
        assertFalse(r.pass());
        assertTrue(r.summary().contains("unknown piece type"));
    }

    /**
     * W-FORGE-5: SLOPE_CUT rejects pitch > 60°.
     */
    @Test
    void slopeCutRejectsSteepPitch() {
        VerbResult<?> r = registry.dispatch(ctx, "FORGE SLOPE_CUT pitch:75 span:3000 width:90 depth:45");
        assertFalse(r.pass());
    }

    /**
     * W-FORGE-6: STAIR_FLIGHT rejects riser > 220mm (code violation).
     */
    @Test
    void stairRejectsHighRiser() {
        VerbResult<?> r = registry.dispatch(ctx, "FORGE STAIR_FLIGHT height:2700 tread:250 riser:250 width:900");
        assertFalse(r.pass());
    }

    /**
     * W-FORGE-7: DOME_SECTION computes correct panel count for 6 rings × 12 segments.
     */
    @Test
    void domeSectionComputesPanels() {
        VerbResult<?> r = registry.dispatch(ctx, "FORGE DOME_SECTION radius:8000 rings:6 segments:12 base_z:15000");
        assertTrue(r.pass(), "Should pass: " + r.summary());
        ForgeResult fr = (ForgeResult) r.payload();
        assertEquals(72, fr.records().size(), "6 rings × 12 segments = 72 panels");
        // Crown panels should be narrower than equator panels
        GeometryRecord crown = fr.records().get(0);  // first ring (near pole)
        GeometryRecord equator = fr.records().get(fr.records().size() - 1);  // last ring
        assertTrue(crown.widthMm() < equator.widthMm(),
                "Crown panels narrower than equator panels");
    }

    /**
     * W-FORGE-8: BARREL_VAULT computes arc length from span + rise.
     */
    @Test
    void barrelVaultComputesArc() {
        VerbResult<?> r = registry.dispatch(ctx, "FORGE BARREL_VAULT span:12000 length:20000 ribs:10 rise:4000");
        assertTrue(r.pass(), "Should pass: " + r.summary());
        ForgeResult fr = (ForgeResult) r.payload();
        assertTrue(fr.records().size() >= 10, "At least 10 rib records");
        // R = (12000²/4 + 4000²) / (2 × 4000) = (36M + 16M) / 8000 = 6500mm
        // arc per rib > span (arc is longer than chord)
        GeometryRecord rib = fr.records().get(0);
        assertTrue(rib.lengthMm() > 12000,
                "Arc length > span for non-flat vault");
    }
}
```

---

## Part C: LOD Promotion Path (Approve → component_library.db)

**Critical design point:** Forged geometry is not throwaway. A forged
rafter at 33.7° / 5200mm, once approved, should be PROMOTED into
`component_library.db` as a reusable LOD — the same way a compiled
building order can be promoted into a reusable BOM template
(ProjectOrderBlueprint.md §4, DocAction=Approve).

**The forge-to-library lifecycle:**

```
FORGE SLOPE_CUT pitch:33.7 span:5200 ...
    → ForgeResult (in-memory geometry record)
    → EYES verification (archetype=ELONGATED, ratios in range)
    → User inspects in viewport (touch-up if needed)
    → DocAction=APPROVE
    → PROMOTE LOD → component_library.db (new LOD_ entry)
    → Next time this piece is needed: library hit, no re-forge
```

**Implementation notes (for the coder):**

1. Add a `promotable` flag on ForgeResult — true if EYES verification passed
   and all compliance checks passed.

2. The actual promotion (writing to component_library.db) is NOT in scope
   for this prompt — it follows the same DocAction=Approve path as BOM
   promotion. The forge just needs to produce records in a format that
   the existing promotion pipeline can consume.

3. GeometryRecord already carries `bomLineId` and `productId` for
   traceability. On promotion, these become the M_Product entry in
   component_library.db.

4. **Do NOT implement promotion in this prompt.** Just ensure ForgeResult
   has the `promotable` field and the geometry records are compatible with
   the existing LOD format (dimensions + fabrication data).

Add to ForgeResult:
```java
public record ForgeResult(
    boolean pass,
    boolean promotable,     // true if EYES + compliance all PASS
    String pieceType,
    String summary,
    List<GeometryRecord> records,
    List<String> compliance,
    String error
) { ... }
```

---

## Verify

1. `mvn compile -q` — PASS
2. ForgeVerbTest 8/8 — PASS
3. `./scripts/run_RosettaStones.sh classify_sh.yaml` — SH 7/7 PASS (no regression)

## Commit message

```
[S##-forge] Geometry Forge — scaffolding + 5 starter pieces

ForgeEngine interface, ForgeResult/GeometryRecord records, ForgeVerb
dispatch. Five pieces: SLOPE_CUT (rafter trig), STAIR_FLIGHT (Pythagoras
+ Blondel), PIPE_BEND (arc geometry), DOME_SECTION (spherical coords),
BARREL_VAULT (cylindrical arc). W-FORGE-1..8 prove formulas, compliance,
and dome/vault geometry. ForgeResult.promotable flag for LOD library
graduation. Spec: GEOMETRY_FORGE_SRS.md

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
```

## When Done

Prepend `# DONE` + commit hash to this file's first line.
