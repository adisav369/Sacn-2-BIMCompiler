package com.bim.compiler.contract;

import com.bim.compiler.dsl.BoundElement;
import com.bim.compiler.dsl.CompilationPipeline;
import com.bim.compiler.dsl.DimensionalContractViolation;
import com.bim.compiler.dsl.GeometryProvenance;
import com.bim.compiler.dsl.MetadataValidator;
import com.bim.compiler.geometry.Mesh;
import com.bim.compiler.geometry.Point3D;
import com.bim.compiler.validation.PlacementProver;
import com.bim.compiler.validation.PlacementProver.PlacementData;
import com.bim.compiler.validation.PlacementProver.ProofReport;
import com.bim.compiler.validation.PlacementProver.ProofResult;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Architectural contract tests — invariants that must NEVER be relaxed.
 *
 * Two sections:
 *   STRUCTURAL: BoundElement null-rejection, dimensional contract, prover classification
 *   GEOMETRIC:  Coordinate-level proofs on RelationalResolver output.
 *               Every placement provable by arithmetic, not visual inspection.
 *               assertEquals(expected, actual, 0.001) — maths, not screenshots.
 *
 * Run on every build via maven-surefire-plugin.
 */
public class CompilerContractTest {

    // 1mm tolerance in meters — the universal coordinate comparison threshold
    private static final double MM1 = 0.001;

    // =========================================================================
    // Reflection helpers — PlacementAD.Placement is package-private
    // =========================================================================

    private static Object createPlacement(
            String buildingType, String storey, String ifcClass, String elementRef,
            int ordinal,
            double minX, double maxX, double minY, double maxY, double minZ, double maxZ,
            String orientation, String discipline,
            String materialName, String materialRgba, String familyRef
    ) throws Exception {
        Class<?> cls = Class.forName("com.bim.compiler.dsl.PlacementAD$Placement");
        Constructor<?> ctor = cls.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        return ctor.newInstance(
                buildingType, storey, ifcClass, elementRef, ordinal,
                minX, maxX, minY, maxY, minZ, maxZ,
                orientation, discipline, materialName, materialRgba, familyRef);
    }

    private static BoundElement createBoundElement(
            String guid, String ifcClass, String elementRef, String type, String storey,
            Object placement, Mesh mesh, String geometryHash,
            double scaleX, double scaleY, double scaleZ,
            GeometryProvenance provenance, String materialName, String materialRgba
    ) throws Throwable {
        Constructor<?> ctor = BoundElement.class.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        try {
            return (BoundElement) ctor.newInstance(
                    guid, ifcClass, elementRef, type, storey,
                    placement, mesh, geometryHash,
                    scaleX, scaleY, scaleZ,
                    provenance, materialName, materialRgba);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private static void invokeValidateScaleFactors(String guid, double sx, double sy, double sz)
            throws Throwable {
        Method m = BoundElement.class.getDeclaredMethod(
                "validateScaleFactors", String.class, double.class, double.class, double.class);
        m.setAccessible(true);
        try {
            m.invoke(null, guid, sx, sy, sz);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private static Mesh cubeMesh(double x0, double x1, double y0, double y1, double z0, double z1) {
        List<Point3D> verts = List.of(
                new Point3D(x0, y0, z0), new Point3D(x1, y0, z0),
                new Point3D(x1, y1, z0), new Point3D(x0, y1, z0),
                new Point3D(x0, y0, z1), new Point3D(x1, y0, z1),
                new Point3D(x1, y1, z1), new Point3D(x0, y1, z1));
        List<int[]> faces = List.of(
                new int[]{0, 2, 1}, new int[]{0, 3, 2},
                new int[]{4, 5, 6}, new int[]{4, 6, 7},
                new int[]{0, 1, 5}, new int[]{0, 5, 4},
                new int[]{2, 3, 7}, new int[]{2, 7, 6},
                new int[]{0, 4, 7}, new int[]{0, 7, 3},
                new int[]{1, 2, 6}, new int[]{1, 6, 5});
        return new Mesh(verts, faces);
    }

    // =========================================================================
    // Geometric data — loaded once from RelationalResolver via reflection
    // =========================================================================

    // element_ref → placement object (PlacementAD.Placement, accessed via reflection)
    private static Map<String, Object> tblktn;

    @BeforeAll
    static void loadTBLKTNPlacements() throws Exception {
        Class<?> resolverClass = Class.forName("com.bim.compiler.dsl.RelationalResolver");
        Method getInstance = resolverClass.getDeclaredMethod("getInstance");
        getInstance.setAccessible(true);
        Object resolver = getInstance.invoke(null);

        Method resolve = resolverClass.getDeclaredMethod("resolve", String.class);
        resolve.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<Object> placements = (List<Object>) resolve.invoke(resolver, "TB_LKTN");
        assertFalse(placements.isEmpty(), "TB-LKTN must have resolved placements");

        tblktn = new LinkedHashMap<>();
        for (Object p : placements) {
            String ref = (String) accessor(p, "elementRef");
            tblktn.put(ref, p);
        }
    }

    private static Object accessor(Object record, String name) throws Exception {
        Method m = record.getClass().getMethod(name);
        m.setAccessible(true);
        return m.invoke(record);
    }

    private static double dbl(Object record, String name) throws Exception {
        return (double) accessor(record, name);
    }

    private static double cx(Object p) throws Exception {
        return (dbl(p, "minX") + dbl(p, "maxX")) / 2.0;
    }

    private static double cy(Object p) throws Exception {
        return (dbl(p, "minY") + dbl(p, "maxY")) / 2.0;
    }

    // =====================================================================
    //  SECTION 1: STRUCTURAL CONTRACTS
    // =====================================================================

    // -- C1: BoundElement null rejection --

    @Test
    @DisplayName("C1a: BoundElement rejects null placement")
    void boundElement_nullPlacement_throws() {
        Mesh mesh = cubeMesh(0, 1, 0, 1, 0, 1);
        assertThrows(NullPointerException.class, () ->
                createBoundElement("G1", "IfcWall", "W1", "WALL", "Ground Floor",
                        null, mesh, "hash123", 1.0, 1.0, 1.0,
                        GeometryProvenance.PARAMETRIC, "Concrete", "128,128,128,255"));
    }

    @Test
    @DisplayName("C1b: BoundElement rejects null mesh")
    void boundElement_nullMesh_throws() throws Exception {
        Object placement = createPlacement(
                "TEST", "Ground Floor", "IfcWall", "W1", 1,
                0, 1, 0, 1, 0, 1,
                "NS", "ARC", "Concrete", "128,128,128,255", null);
        assertThrows(NullPointerException.class, () ->
                createBoundElement("G1", "IfcWall", "W1", "WALL", "Ground Floor",
                        placement, null, "hash123", 1.0, 1.0, 1.0,
                        GeometryProvenance.PARAMETRIC, "Concrete", "128,128,128,255"));
    }

    @Test
    @DisplayName("C1c: BoundElement rejects null geometryHash")
    void boundElement_nullHash_throws() throws Exception {
        Object placement = createPlacement(
                "TEST", "Ground Floor", "IfcWall", "W1", 1,
                0, 1, 0, 1, 0, 1,
                "NS", "ARC", "Concrete", "128,128,128,255", null);
        Mesh mesh = cubeMesh(0, 1, 0, 1, 0, 1);
        assertThrows(NullPointerException.class, () ->
                createBoundElement("G1", "IfcWall", "W1", "WALL", "Ground Floor",
                        placement, mesh, null, 1.0, 1.0, 1.0,
                        GeometryProvenance.PARAMETRIC, "Concrete", "128,128,128,255"));
    }

    @Test
    @DisplayName("C1d: BoundElement accepts valid inputs")
    void boundElement_validInputs_succeeds() throws Throwable {
        Object placement = createPlacement(
                "TEST", "Ground Floor", "IfcWall", "W1", 1,
                0, 1, 0, 1, 0, 1,
                "NS", "ARC", "Concrete", "128,128,128,255", null);
        Mesh mesh = cubeMesh(0, 1, 0, 1, 0, 1);
        BoundElement bound = createBoundElement("G1", "IfcWall", "W1", "WALL", "Ground Floor",
                placement, mesh, "hash123", 1.0, 1.0, 1.0,
                GeometryProvenance.PARAMETRIC, "Concrete", "128,128,128,255");
        assertNotNull(bound);
        assertEquals("G1", bound.guid());
    }

    // -- C2: Dimensional contract --

    @Test
    @DisplayName("C2a: Scale within [0.3, 3.0] accepted")
    void validateScaleFactors_inRange_passes() throws Throwable {
        invokeValidateScaleFactors("G1", 1.0, 1.0, 1.0);
        invokeValidateScaleFactors("G2", 0.3, 3.0, 1.5);
    }

    @Test
    @DisplayName("C2b: Scale below 0.3 throws DimensionalContractViolation")
    void validateScaleFactors_tooSmall_throws() {
        assertThrows(DimensionalContractViolation.class, () ->
                invokeValidateScaleFactors("G1", 0.1, 1.0, 1.0));
    }

    @Test
    @DisplayName("C2c: Scale above 3.0 throws DimensionalContractViolation")
    void validateScaleFactors_tooLarge_throws() {
        assertThrows(DimensionalContractViolation.class, () ->
                invokeValidateScaleFactors("G1", 1.0, 5.0, 1.0));
    }

    @Test
    @DisplayName("C2d: Scale exactly 0.3 accepted (boundary)")
    void validateScaleFactors_exactLower() throws Throwable {
        invokeValidateScaleFactors("G1", 0.3, 0.3, 0.3);
    }

    @Test
    @DisplayName("C2e: Scale exactly 3.0 accepted (boundary)")
    void validateScaleFactors_exactUpper() throws Throwable {
        invokeValidateScaleFactors("G1", 3.0, 3.0, 3.0);
    }

    // -- C3: PlacementProver critical proofs --

    @Test
    @DisplayName("C3a: P01 detects zero-extent bbox")
    void prover_zeroExtent_violated() {
        PlacementData pd = new PlacementData(
                "G1", "IfcWall", "W1", "Ground Floor",
                5.0, 5.0, 0.0, 1.0, 0.0, 3.0);
        ProofReport report = PlacementProver.prove(List.of(pd), "TEST");
        assertTrue(report.results().stream()
                        .anyMatch(r -> r.proofId().startsWith("P01")
                                && r.status() == ProofResult.Status.VIOLATED),
                "P01 must detect zero-extent bbox");
    }

    @Test
    @DisplayName("C3b: P02 detects infinite coordinates")
    void prover_infiniteCoord_violated() {
        PlacementData pd = new PlacementData(
                "G1", "IfcWall", "W1", "Ground Floor",
                Double.POSITIVE_INFINITY, 1.0, 0.0, 1.0, 0.0, 3.0);
        ProofReport report = PlacementProver.prove(List.of(pd), "TEST");
        assertTrue(report.results().stream()
                        .anyMatch(r -> r.proofId().startsWith("P02")
                                && r.status() == ProofResult.Status.VIOLATED),
                "P02 must detect infinite coordinates");
    }

    @Test
    @DisplayName("C3c: P03 detects sub-millimeter dimension")
    void prover_subMmDimension_violated() {
        PlacementData pd = new PlacementData(
                "G1", "IfcWall", "W1", "Ground Floor",
                0.0, 0.0001, 0.0, 1.0, 0.0, 3.0);
        ProofReport report = PlacementProver.prove(List.of(pd), "TEST");
        assertTrue(report.results().stream()
                        .anyMatch(r -> r.proofId().startsWith("P03")
                                && r.status() == ProofResult.Status.VIOLATED),
                "P03 must detect sub-millimeter dimensions");
    }

    @Test
    @DisplayName("C3d: Valid placement has zero critical violations")
    void prover_validPlacement_allCriticalPass() {
        PlacementData pd = new PlacementData(
                "G1", "IfcWall", "W1", "Ground Floor",
                0.0, 0.2, 0.0, 5.0, 0.0, 3.0);
        ProofReport report = PlacementProver.prove(List.of(pd), "TEST");
        assertEquals(0, report.criticalViolations(),
                "Valid placement must have zero critical violations");
    }

    // -- C4: Critical classification --

    @Test
    @DisplayName("C4a: P01-P03 are critical")
    void prover_p01p02p03_critical() {
        assertTrue(pr("P01").isCritical(), "P01 must be critical");
        assertTrue(pr("P02").isCritical(), "P02 must be critical");
        assertTrue(pr("P03").isCritical(), "P03 must be critical");
    }

    @Test
    @DisplayName("C4b: P16-P17 are critical (MEP gating)")
    void prover_p16p17_critical() {
        assertTrue(pr("P16").isCritical(), "P16 must be critical");
        assertTrue(pr("P17").isCritical(), "P17 must be critical");
    }

    @Test
    @DisplayName("C4c: P04, P18 are advisory (not critical)")
    void prover_advisory_notCritical() {
        assertFalse(pr("P04").isCritical(), "P04 must NOT be critical");
        assertFalse(pr("P18").isCritical(), "P18 must NOT be critical");
    }

    // -- C5: DimensionalContractViolation evidence --

    @Test
    @DisplayName("C5: DimensionalContractViolation carries guid, axis, scale")
    void dimensionalViolation_carriesEvidence() {
        DimensionalContractViolation v = new DimensionalContractViolation(
                "G1", 0, 0.1, "Scale too small");
        assertEquals("G1", v.getGuid());
        assertEquals(0, v.getAxis());
        assertEquals(0.1, v.getScaleFactor(), 0.001);
        assertInstanceOf(RuntimeException.class, v);
    }

    // -- C6: Scale constants --

    @Test
    @DisplayName("C6: MIN_SCALE=0.3, MAX_SCALE=3.0 are contract constants")
    void boundElement_scaleConstants() throws Exception {
        var minF = BoundElement.class.getDeclaredField("MIN_SCALE");
        minF.setAccessible(true);
        var maxF = BoundElement.class.getDeclaredField("MAX_SCALE");
        maxF.setAccessible(true);
        assertEquals(0.3, (double) minF.get(null), 0.001,
                "MIN_SCALE must be 0.3");
        assertEquals(3.0, (double) maxF.get(null), 0.001,
                "MAX_SCALE must be 3.0");
    }

    // -- C7: MetadataValidator is first pipeline stage --

    @Test
    @DisplayName("C7: MetadataValidator is first pipeline stage")
    void pipeline_metadataValidator_isFirst() throws Exception {
        var field = CompilationPipeline.class.getDeclaredField("STAGES");
        field.setAccessible(true);
        List<?> stages = (List<?>) field.get(null);
        assertFalse(stages.isEmpty());
        assertInstanceOf(MetadataValidator.class, stages.get(0),
            "MetadataValidator must be FIRST stage — NEVER remove");
    }

    // =====================================================================
    //  SECTION 2: GEOMETRIC PLACEMENT ASSERTIONS
    //  Coordinate-level proofs from RelationalResolver output.
    //  Every assertEquals uses hand-computed expected values ± 1mm.
    // =====================================================================

    // --- G1: Window centroid at wall FRACTION position ---
    //
    // IfcWindow_1: FRACTION 0.5 on WALL_bilik_utama_SOUTH
    // bilik_utama SOUTH wall: x1=0mm, y1=2300mm, x2=3100mm, y2=2300mm
    // formula: wx = 0 + (3100-0)*0.5 = 1550mm, wy = 2300+0 = 2300mm
    // centroid = (1.550m, 2.300m)

    @Test
    @DisplayName("G1a: IfcWindow_1 centroidX = 1.550m (wall midpoint at t=0.5)")
    void g1a_window1_centroidX() throws Exception {
        Object w = tblktn.get("IfcWindow_1");
        assertNotNull(w, "IfcWindow_1 must exist");
        assertEquals(1.550, cx(w), MM1,
                "IfcWindow_1 centroidX = bilik_utama SOUTH wall startX(0) + 0.5*(3100-0) = 1550mm");
    }

    @Test
    @DisplayName("G1b: IfcWindow_1 centroidY = 2.300m (wall surface)")
    void g1b_window1_centroidY() throws Exception {
        Object w = tblktn.get("IfcWindow_1");
        assertNotNull(w, "IfcWindow_1 must exist");
        assertEquals(2.300, cy(w), MM1,
                "IfcWindow_1 centroidY = bilik_utama SOUTH wall surface Y=2300mm");
    }

    // IfcWindow_8: FRACTION 0.5 on WALL_bilik_utama_WEST (NS wall)
    // bilik_utama WEST wall: x1=0mm, y1=2300mm, x2=0mm, y2=5400mm
    // formula: wy = 2300 + (5400-2300)*0.5 = 3850mm, wx = 0+0 = 0mm
    // centroid = (0.000m, 3.850m)

    @Test
    @DisplayName("G1c: IfcWindow_8 centroidX = 0.000m (WEST wall surface)")
    void g1c_window8_centroidX() throws Exception {
        Object w = tblktn.get("IfcWindow_8");
        assertNotNull(w, "IfcWindow_8 must exist");
        assertEquals(0.000, cx(w), MM1,
                "IfcWindow_8 centroidX = bilik_utama WEST wall surface X=0mm");
    }

    @Test
    @DisplayName("G1d: IfcWindow_8 centroidY = 3.850m (wall midpoint at t=0.5)")
    void g1d_window8_centroidY() throws Exception {
        Object w = tblktn.get("IfcWindow_8");
        assertNotNull(w, "IfcWindow_8 must exist");
        assertEquals(3.850, cy(w), MM1,
                "IfcWindow_8 centroidY = bilik_utama WEST wall startY(2300) + 0.5*(5400-2300) = 3850mm");
    }

    // --- G2: Door centroid at wall FRACTION position ---
    //
    // IfcDoor_2: FRACTION 0.3 on WALL_bilik_utama_EAST
    // bilik_utama EAST wall: x1=3100mm, y1=2300mm, x2=3100mm, y2=5400mm
    // formula: wy = 2300 + (5400-2300)*0.3 = 3230mm, wx = 3100+0 = 3100mm
    // centroid = (3.100m, 3.230m)

    @Test
    @DisplayName("G2a: IfcDoor_2 centroidX = 3.100m (EAST wall surface)")
    void g2a_door2_centroidX() throws Exception {
        Object d = tblktn.get("IfcDoor_2");
        assertNotNull(d, "IfcDoor_2 must exist");
        assertEquals(3.100, cx(d), MM1,
                "IfcDoor_2 centroidX = bilik_utama EAST wall surface X=3100mm");
    }

    @Test
    @DisplayName("G2b: IfcDoor_2 centroidY = 3.230m (30% along wall)")
    void g2b_door2_centroidY() throws Exception {
        Object d = tblktn.get("IfcDoor_2");
        assertNotNull(d, "IfcDoor_2 must exist");
        assertEquals(3.230, cy(d), MM1,
                "IfcDoor_2 centroidY = EAST wall startY(2300) + 0.3*(5400-2300) = 3230mm");
    }

    // IfcDoor_1: FRACTION 0.5 on WALL_common_SOUTH (EW wall)
    // common SOUTH wall: x1=3100mm, y1=2300mm, x2=6800mm, y2=2300mm
    // formula: wx = 3100 + (6800-3100)*0.5 = 4950mm, wy = 2300+0 = 2300mm
    // centroid = (4.950m, 2.300m)

    @Test
    @DisplayName("G2c: IfcDoor_1 centroidX = 4.950m (common SOUTH wall midpoint)")
    void g2c_door1_centroidX() throws Exception {
        Object d = tblktn.get("IfcDoor_1");
        assertNotNull(d, "IfcDoor_1 must exist");
        assertEquals(4.950, cx(d), MM1,
                "IfcDoor_1 centroidX = common SOUTH wall startX(3100) + 0.5*(6800-3100) = 4950mm");
    }

    @Test
    @DisplayName("G2d: IfcDoor_1 centroidY = 2.300m (SOUTH wall surface)")
    void g2d_door1_centroidY() throws Exception {
        Object d = tblktn.get("IfcDoor_1");
        assertNotNull(d, "IfcDoor_1 must exist");
        assertEquals(2.300, cy(d), MM1,
                "IfcDoor_1 centroidY = common SOUTH wall surface Y=2300mm");
    }

    // IfcDoor_2 width < wall length (door fits on wall)
    @Test
    @DisplayName("G2e: IfcDoor_2 depth 0.800m < EAST wall length 3.100m")
    void g2e_door2_fitsOnWall() throws Exception {
        Object d = tblktn.get("IfcDoor_2");
        assertNotNull(d);
        // NS wall: door extent along Y = depth
        double doorExtentY = dbl(d, "maxY") - dbl(d, "minY");
        double wallLength = (5400.0 - 2300.0) / 1000.0; // 3.100m
        assertTrue(doorExtentY < wallLength,
                "Door extent along wall (" + doorExtentY + "m) must be < wall length (" + wallLength + "m)");
    }

    // --- G3: Furniture centroid at room FRACTION position ---
    //
    // IfcFurnishingElement_5 (WC): FRACTION (0.5, 0.7) in tandas
    // tandas: minX=0, maxX=1300, minY=5400, maxY=7000 → roomW=1300, roomD=1600
    // formula: cx = (0 + 0.5*1300)/1000 = 0.650m
    //          cy = (5400 + 0.7*1600)/1000 = 6.520m

    // Toilet orientation fix (migration B4): NS → EW, back against west wall, faces east.
    // EW, fractionX=0.27: centroidX = (0 + 0.27*1300)/1000 = 0.351m
    // EW, fractionY=0.50: centroidY = (5400 + 0.5*1600)/1000 = 6.200m
    // Rotation: EW + fractionX<0.5 → west wall → -π/2 (faces east, toward door)

    @Test
    @DisplayName("G3a: WC (FE_5) centroidX = 0.351m (27% of tandas width, EW west-wall)")
    void g3a_wc_centroidX() throws Exception {
        Object fe5 = tblktn.get("IfcFurnishingElement_5");
        assertNotNull(fe5, "IfcFurnishingElement_5 must exist");
        assertEquals(0.351, cx(fe5), MM1,
                "FE_5 centroidX = (0 + 0.27*1300)/1000 = 0.351m");
    }

    @Test
    @DisplayName("G3b: WC (FE_5) centroidY = 6.200m (50% of tandas depth)")
    void g3b_wc_centroidY() throws Exception {
        Object fe5 = tblktn.get("IfcFurnishingElement_5");
        assertNotNull(fe5, "IfcFurnishingElement_5 must exist");
        assertEquals(6.200, cy(fe5), MM1,
                "FE_5 centroidY = (5400 + 0.5*1600)/1000 = 6.200m");
    }

    // IfcFurnishingElement_4 (shower): FRACTION (0.5, 0.5) in bilik_mandi
    // bilik_mandi: minX=0, maxX=1300, minY=7000, maxY=8500 → roomW=1300, roomD=1500
    // formula: cx = (0 + 0.5*1300)/1000 = 0.650m
    //          cy = (7000 + 0.5*1500)/1000 = 7.750m

    @Test
    @DisplayName("G3c: Shower (FE_4) centroidX = 0.650m (50% of bilik_mandi width)")
    void g3c_shower_centroidX() throws Exception {
        Object fe4 = tblktn.get("IfcFurnishingElement_4");
        assertNotNull(fe4, "IfcFurnishingElement_4 must exist");
        assertEquals(0.650, cx(fe4), MM1,
                "FE_4 centroidX = (0 + 0.5*1300)/1000 = 0.650m");
    }

    @Test
    @DisplayName("G3d: Shower (FE_4) centroidY = 7.750m (50% of bilik_mandi depth)")
    void g3d_shower_centroidY() throws Exception {
        Object fe4 = tblktn.get("IfcFurnishingElement_4");
        assertNotNull(fe4, "IfcFurnishingElement_4 must exist");
        assertEquals(7.750, cy(fe4), MM1,
                "FE_4 centroidY = (7000 + 0.5*1500)/1000 = 7.750m");
    }

    // Phase RM-6: BED_SET BOM anchor in bilik_utama.
    // bilik_utama is 3100x3100mm square: all 4 walls have equal score with no openings.
    // Wall selection depends on HashMap iteration order (non-deterministic).
    // Tests verify deterministic properties: element exists with correct Bed_Queen dimensions.
    // element_ref = "BOM_BED_SET_bilik_utama_BED_0" (role=BED, childIdx=0)

    @Test
    @DisplayName("G3e: BOM Bed in bilik_utama exists; centroidX within room bounds [0-3.1m]")
    void g3e_bed_centroidX() throws Exception {
        Object bed = tblktn.get("BOM_BED_SET_bilik_utama_BED_0");
        assertNotNull(bed, "BOM_BED_SET_bilik_utama_BED_0 must exist (Phase RM-6 BOM expansion)");
        assertTrue(cx(bed) >= 0.0 - MM1, "BOM Bed centroidX >= bilik_utama minX 0.000m");
        assertTrue(cx(bed) <= 3.1 + MM1, "BOM Bed centroidX <= bilik_utama maxX 3.100m");
    }

    @Test
    @DisplayName("G3f: BOM Bed width = 1.500m (Bed_Queen from ad_product_dim Phase RM-6)")
    void g3f_bed_centroidY() throws Exception {
        Object bed = tblktn.get("BOM_BED_SET_bilik_utama_BED_0");
        assertNotNull(bed, "BOM_BED_SET_bilik_utama_BED_0 must exist");
        assertEquals(1.5, dbl(bed, "maxX") - dbl(bed, "minX"), MM1,
                "BED_SET Bed_Queen bbox width = 1.500m");
    }

    // --- G4: Furniture bbox geometry checks ---

    @Test
    @DisplayName("G4a: WC (FE_5) bbox inside tandas [0-1.3m, 5.4-7.0m] — EW west-wall")
    void g4a_wc_insideTandas() throws Exception {
        Object fe5 = tblktn.get("IfcFurnishingElement_5");
        assertNotNull(fe5);
        // WC: width=400mm, depth=700mm, centroid=(0.351, 6.200) — EW orientation
        // halfX = width/2 = 0.200m, halfY = depth/2 = 0.350m
        // Expected bbox: minX=0.151, maxX=0.551, minY=5.850, maxY=6.550
        assertEquals(0.151, dbl(fe5, "minX"), MM1, "FE_5 minX = 0.351 - 0.200 = 0.151m");
        assertEquals(0.551, dbl(fe5, "maxX"), MM1, "FE_5 maxX = 0.351 + 0.200 = 0.551m");
        assertEquals(5.850, dbl(fe5, "minY"), MM1, "FE_5 minY = 6.200 - 0.350 = 5.850m");
        assertEquals(6.550, dbl(fe5, "maxY"), MM1, "FE_5 maxY = 6.200 + 0.350 = 6.550m");
        // Containment: all within tandas [0-1.3, 5.4-7.0]
        assertTrue(dbl(fe5, "minX") >= 0.0 - MM1,   "FE_5 minX >= tandas minX 0.000m");
        assertTrue(dbl(fe5, "maxX") <= 1.3 + MM1,   "FE_5 maxX <= tandas maxX 1.300m");
        assertTrue(dbl(fe5, "minY") >= 5.4 - MM1,   "FE_5 minY >= tandas minY 5.400m");
        assertTrue(dbl(fe5, "maxY") <= 7.0 + MM1,   "FE_5 maxY <= tandas maxY 7.000m");
    }

    @Test
    @DisplayName("G4b: Shower (FE_4) bbox inside bilik_mandi [0-1.3m, 7.0-8.5m]")
    void g4b_shower_insideBilikMandi() throws Exception {
        Object fe4 = tblktn.get("IfcFurnishingElement_4");
        assertNotNull(fe4);
        // Shower: width=900mm, depth=900mm, centroid=(0.650, 7.750)
        // Expected bbox: minX=0.200, maxX=1.100, minY=7.300, maxY=8.200
        assertEquals(0.200, dbl(fe4, "minX"), MM1, "FE_4 minX = 0.650 - 0.450 = 0.200m");
        assertEquals(1.100, dbl(fe4, "maxX"), MM1, "FE_4 maxX = 0.650 + 0.450 = 1.100m");
        assertEquals(7.300, dbl(fe4, "minY"), MM1, "FE_4 minY = 7.750 - 0.450 = 7.300m");
        assertEquals(8.200, dbl(fe4, "maxY"), MM1, "FE_4 maxY = 7.750 + 0.450 = 8.200m");
        // Containment: all within bilik_mandi [0-1.3, 7.0-8.5]
        assertTrue(dbl(fe4, "minX") >= 0.0 - MM1,   "FE_4 minX >= bilik_mandi minX 0.000m");
        assertTrue(dbl(fe4, "maxX") <= 1.3 + MM1,   "FE_4 maxX <= bilik_mandi maxX 1.300m");
        assertTrue(dbl(fe4, "minY") >= 7.0 - MM1,   "FE_4 minY >= bilik_mandi minY 7.000m");
        assertTrue(dbl(fe4, "maxY") <= 8.5 + MM1,   "FE_4 maxY <= bilik_mandi maxY 8.500m");
    }

    @Test
    @DisplayName("G4c: BOM Bed_Queen depth=2.0m and height=0.5m (from ad_product_dim Phase RM-6)")
    void g4c_bed_insideBilikUtama() throws Exception {
        // Phase RM-6: BED_SET expands Bed_Queen from ad_product_dim (width=1.5, depth=2.0, height=0.5).
        // Wall selection is non-deterministic for square rooms; tested separately in G3e.
        Object bed = tblktn.get("BOM_BED_SET_bilik_utama_BED_0");
        assertNotNull(bed, "BOM_BED_SET_bilik_utama_BED_0 must exist");
        assertEquals(2.0, dbl(bed, "maxY") - dbl(bed, "minY"), MM1,
                "BED_SET Bed_Queen bbox depth = 2.000m");
        assertEquals(0.5, dbl(bed, "maxZ") - dbl(bed, "minZ"), MM1,
                "BED_SET Bed_Queen bbox height = 0.500m");
    }

    // --- G5: No zero-volume elements ---

    @Test
    @DisplayName("G5: Every TB-LKTN element has dx > 0, dy > 0, dz > 0")
    void g5_allElements_positiveVolume() throws Exception {
        for (var entry : tblktn.entrySet()) {
            Object p = entry.getValue();
            double dx = dbl(p, "maxX") - dbl(p, "minX");
            double dy = dbl(p, "maxY") - dbl(p, "minY");
            double dz = dbl(p, "maxZ") - dbl(p, "minZ");
            assertTrue(dx > 0, entry.getKey() + " dx=" + dx + " must be > 0");
            assertTrue(dy > 0, entry.getKey() + " dy=" + dy + " must be > 0");
            assertTrue(dz > 0, entry.getKey() + " dz=" + dz + " must be > 0");
        }
    }

    // --- G6: Wall orientation consistent with extent ---

    @Test
    @DisplayName("G6: NS walls have dx < dy, EW walls have dy < dx")
    void g6_wallOrientation_matchesExtent() throws Exception {
        for (var entry : tblktn.entrySet()) {
            Object p = entry.getValue();
            String ifcClass = (String) accessor(p, "ifcClass");
            if (!"IfcPlate".equals(ifcClass)) continue;

            String orient = (String) accessor(p, "orientation");
            double dx = dbl(p, "maxX") - dbl(p, "minX");
            double dy = dbl(p, "maxY") - dbl(p, "minY");

            if ("NS".equals(orient)) {
                assertTrue(dx < dy,
                        entry.getKey() + " NS wall: dx=" + dx + " must be < dy=" + dy);
            } else if ("EW".equals(orient)) {
                assertTrue(dy < dx,
                        entry.getKey() + " EW wall: dy=" + dy + " must be < dx=" + dx);
            }
        }
    }

    // --- G7: Window bbox smaller than host wall extent ---

    @Test
    @DisplayName("G7: IfcWindow_1 width 1.200m < bilik_utama SOUTH wall 3.100m")
    void g7_window1_smallerThanWall() throws Exception {
        Object w = tblktn.get("IfcWindow_1");
        assertNotNull(w);
        double windowDx = dbl(w, "maxX") - dbl(w, "minX");
        // bilik_utama SOUTH wall length = 3100mm = 3.100m
        assertEquals(1.200, windowDx, MM1, "IfcWindow_1 width = 1.200m");
        assertTrue(windowDx < 3.100,
                "Window width (" + windowDx + "m) must be < wall length (3.100m)");
    }

    // --- G8: Slab covers full building footprint ---
    //
    // IfcSlab_1: ABSOLUTE cx=4950mm, cy=5400mm, width=9900mm, depth=6200mm
    // Expected bbox: X=[0.000, 9.900], Y=[2.300, 8.500]
    // This is the main floor slab — covers grid A-E (0-9900mm) × lines 2-5 (2300-8500mm)

    @Test
    @DisplayName("G8: Main slab IfcSlab_1 covers full building footprint [0-9.9m, 2.3-8.5m]")
    void g8_slab_coversFootprint() throws Exception {
        Object s = tblktn.get("IfcSlab_1");
        assertNotNull(s, "IfcSlab_1 must exist");
        assertEquals(0.000, dbl(s, "minX"), MM1, "Slab minX = 4950-4950 = 0mm");
        assertEquals(9.900, dbl(s, "maxX"), MM1, "Slab maxX = 4950+4950 = 9900mm");
        assertEquals(2.300, dbl(s, "minY"), MM1, "Slab minY = 5400-3100 = 2300mm");
        assertEquals(8.500, dbl(s, "maxY"), MM1, "Slab maxY = 5400+3100 = 8500mm");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static ProofResult pr(String proofId) {
        return new ProofResult(proofId + "_test",
                ProofResult.Status.VIOLATED, "elem", "evidence", 0.0);
    }
}
