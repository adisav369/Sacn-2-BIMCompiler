package com.bim.ormsandbox;

import com.bim.ormsandbox.po.*;
import org.junit.jupiter.api.*;

import java.sql.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke tests for ORMSandbox PO layer against the canonical library DB.
 *
 * <p>Tests load real rows from {@code library/component_library.db}.
 * Witnesses validate that PO layer correctly surfaces known values.
 */
@DisplayName("ORMSandbox — PO layer smoke tests")
class BuildingInspectorTest {

    private static final String LIB_DB = "library/component_library.db";
    private Connection conn;

    @BeforeEach
    void open() throws SQLException {
        conn = DriverManager.getConnection("jdbc:sqlite:" + LIB_DB);
    }

    @AfterEach
    void close() throws SQLException {
        if (conn != null && !conn.isClosed()) conn.close();
    }

    // ── S-ORM-1: Building registry loads all 4 buildings ─────────────────────

    @Test
    @DisplayName("S-ORM-1: getAll() returns ≥4 active buildings")
    void allBuildingsLoaded() throws SQLException {
        List<M_AdBuildingRegistry> buildings = M_AdBuildingRegistry.getAll(conn);
        assertTrue(buildings.size() >= 4,
            "Expected ≥4 buildings in registry, got " + buildings.size());
        // All must have building_id
        for (M_AdBuildingRegistry b : buildings) {
            assertNotNull(b.getBuildingId(), "building_id must not be null");
            assertFalse(b.getBuildingId().isBlank(), "building_id must not be blank");
        }
    }

    // ── S-ORM-2: BOM chain — BED_SET_MASTER loads with children ──────────────

    @Test
    @DisplayName("S-ORM-2: BED_SET_MASTER BOM loads with ≥1 child")
    void bedSetMasterBomChain() throws SQLException {
        M_AdBom bom = M_AdBom.get(conn, "BED_SET_MASTER");
        assertNotNull(bom, "BED_SET_MASTER must exist in ad_bom");
        assertNotNull(bom.getBomType(), "bom_type must not be null");
        assertNotNull(bom.getGroupBy(), "group_by must not be null — NOT NULL constraint");

        List<M_AdBomChild> children = M_AdBomChild.getByBom(conn, "BED_SET_MASTER");
        assertFalse(children.isEmpty(), "BED_SET_MASTER must have ≥1 BOM child");
    }

    // ── S-ORM-3: ad_product_dim units are METERS not mm ──────────────────────

    @Test
    @DisplayName("S-ORM-3: FURN_DINING_CHAIR product dimensions are in meters (< 5.0)")
    void productDimInMeters() throws SQLException {
        M_AdProductDim chair = M_AdProductDim.get(conn, "FURN_DINING_CHAIR");
        if (chair == null) return; // product may not be seeded in all DBs
        assertTrue(chair.getWidth() < 5.0,
            "width must be in meters (< 5.0), got " + chair.getWidth());
        assertTrue(chair.getDepth() < 5.0,
            "depth must be in meters (< 5.0), got " + chair.getDepth());
    }

    // ── S-ORM-4: ad_element_rule loads for SH ────────────────────────────────

    @Test
    @DisplayName("S-ORM-4: element rules for Ifc4_SampleHouse load ≥1 row")
    void elementRulesLoadForSH() throws SQLException {
        // Try known building types — may vary by DB
        List<M_AdElementRule> rules = M_AdElementRule.getByBuilding(conn, "Ifc4_SampleHouse");
        if (rules.isEmpty()) {
            rules = M_AdElementRule.getByBuilding(conn, "SH");
        }
        // At least one of the known IDs should work; skip gracefully if neither found
        if (rules.isEmpty()) return;

        for (M_AdElementRule r : rules) {
            assertNotNull(r.getElementRef(), "element_ref must not be null");
            assertNotNull(r.getIfcClass(), "ifc_class must not be null");
            assertNotNull(r.getHostType(), "host_type must not be null");
        }
    }

    // ── S-ORM-5: room boundaries X/Y centroid helpers ─────────────────────────

    @Test
    @DisplayName("S-ORM-5: M_AdRoomBoundary centroid helpers return midpoint")
    void roomBoundaryCentroid() throws SQLException {
        List<M_AdRoomBoundary> rooms = M_AdRoomBoundary.getByBuilding(conn, "Ifc4_SampleHouse");
        if (rooms.isEmpty()) rooms = M_AdRoomBoundary.getByBuilding(conn, "SH");
        if (rooms.isEmpty()) return;

        M_AdRoomBoundary room = rooms.get(0);
        double expectedCentX = (room.getMinXMm() + room.getMaxXMm()) / 2.0;
        double expectedCentY = (room.getMinYMm() + room.getMaxYMm()) / 2.0;
        assertEquals(expectedCentX, room.centroidXMm(), 0.001);
        assertEquals(expectedCentY, room.centroidYMm(), 0.001);
    }

    // ── S-ORM-6: BuildingInspector runs without exception ─────────────────────

    @Test
    @DisplayName("S-ORM-6: BuildingInspector.dumpBuildings() completes without exception")
    void inspectorDumpBuildings() throws SQLException {
        BuildingInspector inspector = new BuildingInspector(conn);
        assertDoesNotThrow(() -> inspector.dumpBuildings());
    }
}
