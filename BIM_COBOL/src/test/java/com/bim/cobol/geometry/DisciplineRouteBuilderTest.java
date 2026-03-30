package com.bim.cobol.geometry;

import com.bim.cobol.geometry.DisciplineRouteBuilder.DisciplineRouteResult;
import com.bim.compiler.geometry.Point3D;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Witness tests for all 6 MEP DisciplineRouteBuilders + Registry + DocEvent.
 *
 * <p>W-ROUTE-FP-1, W-ROUTE-ELEC-1, W-ROUTE-CW-1, W-ROUTE-SP-1,
 * W-ROUTE-ACMV-1, W-ROUTE-LPG-1 (T2.1–T2.4).
 *
 * <p>Uses a stub BuildingGeometry (2 floors, 2 rooms per floor) to verify
 * each builder produces the expected routing pattern.
 */
// Implementing DISC_VALIDATION_DB_SRS §10.4.11 T2.1–T2.4
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DisciplineRouteBuilderTest {

    /** Stub geometry: 2 floors at 0mm and 3000mm, 2 rooms per floor, each 4000×3000. */
    private static final BuildingGeometry STUB_GEO = new StubBuildingGeometry();

    // ── Registry ────────────────────────────────────────────────────

    @Test
    @Order(1)
    void registry_has_6_builders() {
        assertEquals(6, DisciplineRouteRegistry.size(), "6 MEP disciplines registered");
        assertNotNull(DisciplineRouteRegistry.get("FP"));
        assertNotNull(DisciplineRouteRegistry.get("ELEC"));
        assertNotNull(DisciplineRouteRegistry.get("CW"));
        assertNotNull(DisciplineRouteRegistry.get("SP"));
        assertNotNull(DisciplineRouteRegistry.get("ACMV"));
        assertNotNull(DisciplineRouteRegistry.get("LPG"));
        assertNull(DisciplineRouteRegistry.get("ARC"), "ARC is not MEP");
        assertNull(DisciplineRouteRegistry.get("STR"), "STR is not MEP");
    }

    // ── W-ROUTE-FP-1: FP riser→header→branches→sprinklers ──────────

    @Test
    @Order(10)
    void w_route_fp_1a_produces_segments_and_fittings() {
        FpRouteBuilder builder = new FpRouteBuilder();
        assertEquals("FP", builder.discipline());
        assertEquals("PipeSegment", builder.segmentProduct());

        DisciplineRouteResult r = builder.buildRoute(STUB_GEO);
        assertNotNull(r);
        assertEquals("FP", r.discipline());
        assertTrue(r.segmentCount() > 0, "FP must produce pipe segments");
        assertTrue(r.fittingCount() > 0, "FP must produce fittings (elbows, tees, sleeves)");
        assertEquals(2, r.floorCount(), "2 floors");
        assertEquals(4, r.roomCount(), "2 rooms × 2 floors");
        assertTrue(r.totalLengthMm() > 0, "total length must be positive");
    }

    @Test
    @Order(11)
    void w_route_fp_1b_has_penetrations() {
        // FP uses fire-rated slab penetrations
        DisciplineRouteResult r = new FpRouteBuilder().buildRoute(STUB_GEO);
        CrawlRouter.RouteResult rr = r.routeResult();

        // Must have SLEEVE_SLAB + FIRE_COLLAR fittings (fire-rated penetrations)
        long sleeveCount = rr.fittings().stream()
                .filter(f -> f.type().startsWith("SLEEVE_")).count();
        long fireCollarCount = rr.fittings().stream()
                .filter(f -> "FIRE_COLLAR".equals(f.type())).count();
        assertTrue(sleeveCount > 0, "FP must penetrate slabs (sleeve fittings)");
        assertTrue(fireCollarCount > 0, "FP slabs are fire-rated → fire collars");
    }

    @Test
    @Order(12)
    void w_route_fp_1c_edges_connected() {
        DisciplineRouteResult r = new FpRouteBuilder().buildRoute(STUB_GEO);
        CrawlRouter.RouteResult rr = r.routeResult();
        int totalElements = rr.segments().size() + rr.fittings().size();
        assertEquals(totalElements - 1, rr.edges().size(),
                "fully connected: N elements → N-1 edges");
    }

    // ── W-ROUTE-ELEC-1: ELEC DB→tray→fixtures ──────────────────────

    @Test
    @Order(20)
    void w_route_elec_1a_produces_cable_tray() {
        ElecRouteBuilder builder = new ElecRouteBuilder();
        assertEquals("ELEC", builder.discipline());
        assertEquals("CableTray", builder.segmentProduct());

        DisciplineRouteResult r = builder.buildRoute(STUB_GEO);
        assertNotNull(r);
        assertEquals("ELEC", r.discipline());
        assertTrue(r.segmentCount() > 0, "ELEC must produce cable tray segments");
        assertTrue(r.fittingCount() > 0, "ELEC must produce fittings");
        assertEquals(4, r.roomCount(), "2 rooms × 2 floors");
    }

    @Test
    @Order(21)
    void w_route_elec_1b_non_fire_rated_penetrations() {
        DisciplineRouteResult r = new ElecRouteBuilder().buildRoute(STUB_GEO);
        long fireCollarCount = r.routeResult().fittings().stream()
                .filter(f -> "FIRE_COLLAR".equals(f.type())).count();
        assertEquals(0, fireCollarCount, "ELEC penetrations are not fire-rated");
    }

    // ── W-ROUTE-CW-1: CW tank→riser→header→fixtures ────────────────

    @Test
    @Order(30)
    void w_route_cw_1a_supply_direction() {
        CwRouteBuilder builder = new CwRouteBuilder();
        assertEquals("CW", builder.discipline());
        assertEquals("PipeSegment", builder.segmentProduct());

        DisciplineRouteResult r = builder.buildRoute(STUB_GEO);
        assertNotNull(r);
        assertEquals("CW", r.discipline());
        assertTrue(r.segmentCount() > 0, "CW must produce pipe segments");
        assertEquals(4, r.roomCount());
        assertContains(r.pattern(), "MS 1228");
    }

    // ── W-ROUTE-SP-1: SP fixtures→waste→stack→drain (gravity) ───────

    @Test
    @Order(40)
    void w_route_sp_1a_gravity_direction() {
        SpRouteBuilder builder = new SpRouteBuilder();
        assertEquals("SP", builder.discipline());
        assertEquals("PipeSegment", builder.segmentProduct());
        assertEquals(SpRouteBuilder.WASTE_DIAMETER_MM, builder.mainDiameterMm(),
                "SP main = waste diameter, not stack");

        DisciplineRouteResult r = builder.buildRoute(STUB_GEO);
        assertNotNull(r);
        assertEquals("SP", r.discipline());
        assertTrue(r.segmentCount() > 0);
        assertTrue(r.fittingCount() > 0);
        // SP starts from top floor, descends
        assertContains(r.pattern(), "gravity");
    }

    @Test
    @Order(41)
    void w_route_sp_1b_can_penetrate_slabs() {
        DisciplineRouteResult r = new SpRouteBuilder().buildRoute(STUB_GEO);
        long sleeveCount = r.routeResult().fittings().stream()
                .filter(f -> f.type().startsWith("SLEEVE_SLAB")).count();
        assertTrue(sleeveCount > 0, "SP penetrates slabs (drainage stack)");
    }

    // ── W-ROUTE-ACMV-1: ACMV AHU→duct→branches→terminals ──────────

    @Test
    @Order(50)
    void w_route_acmv_1a_duct_segments() {
        AcmvRouteBuilder builder = new AcmvRouteBuilder();
        assertEquals("ACMV", builder.discipline());
        assertEquals("DuctSegment", builder.segmentProduct());
        assertEquals(AcmvRouteBuilder.MAIN_DIAMETER_MM, 400, "400mm main duct");

        DisciplineRouteResult r = builder.buildRoute(STUB_GEO);
        assertNotNull(r);
        assertEquals("ACMV", r.discipline());
        assertTrue(r.segmentCount() > 0, "ACMV must produce duct segments");
        assertEquals(4, r.roomCount());
        assertContains(r.pattern(), "ASHRAE");
    }

    @Test
    @Order(51)
    void w_route_acmv_1b_larger_diameters_than_pipes() {
        DisciplineRouteResult acmv = new AcmvRouteBuilder().buildRoute(STUB_GEO);
        DisciplineRouteResult fp = new FpRouteBuilder().buildRoute(STUB_GEO);

        // ACMV segments should be larger diameter than FP
        double acmvMaxDia = acmv.routeResult().segments().stream()
                .mapToDouble(CrawlRouter.SegmentSpec::diameterMm).max().orElse(0);
        double fpMaxDia = fp.routeResult().segments().stream()
                .mapToDouble(CrawlRouter.SegmentSpec::diameterMm).max().orElse(0);
        assertTrue(acmvMaxDia > fpMaxDia,
                "ACMV ducts (" + acmvMaxDia + "mm) must be larger than FP pipes (" + fpMaxDia + "mm)");
    }

    // ── W-ROUTE-LPG-1: LPG meter→riser→kitchen→gas points ──────────

    @Test
    @Order(60)
    void w_route_lpg_1a_smallest_system() {
        LpgRouteBuilder builder = new LpgRouteBuilder();
        assertEquals("LPG", builder.discipline());
        assertEquals("PipeSegment", builder.segmentProduct());
        assertEquals(25, builder.mainDiameterMm(), "LPG main = 25mm");
        assertEquals(20, builder.branchDiameterMm(), "LPG branch = 20mm");

        DisciplineRouteResult r = builder.buildRoute(STUB_GEO);
        assertNotNull(r);
        assertEquals("LPG", r.discipline());
        assertTrue(r.segmentCount() > 0);
        assertContains(r.pattern(), "MS 830");
    }

    @Test
    @Order(61)
    void w_route_lpg_1b_wall_penetrations_not_slab() {
        DisciplineRouteResult r = new LpgRouteBuilder().buildRoute(STUB_GEO);
        long wallSleeves = r.routeResult().fittings().stream()
                .filter(f -> "SLEEVE_WALL".equals(f.type())).count();
        assertTrue(wallSleeves > 0, "LPG runs along external wall → WALL penetrations");
    }

    // ── DocEvent activation ─────────────────────────────────────────

    @Test
    @Order(70)
    void docEvent_fire_single() {
        DisciplineRouteResult r = RouteDocEvent.fire("FP", STUB_GEO);
        assertNotNull(r, "FP should fire");
        assertEquals("FP", r.discipline());

        assertNull(RouteDocEvent.fire("ARC", STUB_GEO), "ARC has no route builder");
    }

    @Test
    @Order(71)
    void docEvent_fireAll_six_disciplines() {
        List<String> mepDiscs = List.of("FP", "ELEC", "CW", "SP", "ACMV", "LPG");
        List<DisciplineRouteResult> results = RouteDocEvent.fireAll(mepDiscs, STUB_GEO);

        assertEquals(6, results.size(), "all 6 MEP disciplines should produce results");

        int totalSegs = results.stream().mapToInt(DisciplineRouteResult::segmentCount).sum();
        int totalFits = results.stream().mapToInt(DisciplineRouteResult::fittingCount).sum();
        assertTrue(totalSegs > 0, "total segments > 0");
        assertTrue(totalFits > 0, "total fittings > 0");

        // Each discipline produces a different pattern
        long distinctPatterns = results.stream()
                .map(DisciplineRouteResult::pattern).distinct().count();
        assertEquals(6, distinctPatterns, "each discipline has a unique routing pattern");
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private static void assertContains(String actual, String expected) {
        assertTrue(actual.contains(expected),
                "Expected '" + actual + "' to contain '" + expected + "'");
    }

    // ── Stub BuildingGeometry ───────────────────────────────────────

    /**
     * Minimal stub: 2 floors (GF at 0mm, L1 at 3000mm),
     * 2 rooms per floor (ROOM_A 4000×3000, ROOM_B 5000×3500).
     * Service room at origin.
     */
    static final class StubBuildingGeometry implements BuildingGeometry {

        @Override
        public Point3D serviceRoomPosition(String disciplineCategory) {
            return new Point3D(0, 0, 0);
        }

        @Override
        public List<FloorLevel> floors() {
            return List.of(
                    new FloorLevel("GF", 0),
                    new FloorLevel("L1", 3000));
        }

        @Override
        public List<RoomTarget> roomsOnFloor(String floorRef) {
            return List.of(
                    new RoomTarget("ROOM_A_" + floorRef,
                            new Point3D(2, 1, 0), null),
                    new RoomTarget("ROOM_B_" + floorRef,
                            new Point3D(6, 3, 0), null));
        }

        @Override
        public RoomDimensions roomDimensions(String roomRef) {
            if (roomRef.startsWith("ROOM_A")) return new RoomDimensions(4000, 3000, 2700);
            if (roomRef.startsWith("ROOM_B")) return new RoomDimensions(5000, 3500, 2700);
            // Floor-level dimensions (used for header run)
            return new RoomDimensions(12000, 8000, 3000);
        }

        @Override
        public double slabThickness(String floorRef) {
            return 150;
        }

        @Override
        public double wallThickness(String floorRef) {
            return 200;
        }

        @Override
        public double ceilingHeightMm(String floorRef) {
            // GF ceiling = L1.z - slab - clearance = 3000 - 150 - 50 = 2800
            // L1 ceiling = L1.z + 3000 - 150 - 50 = 5800 (top floor fallback)
            if ("GF".equals(floorRef)) return 2800;
            return 5800;
        }
    }
}
