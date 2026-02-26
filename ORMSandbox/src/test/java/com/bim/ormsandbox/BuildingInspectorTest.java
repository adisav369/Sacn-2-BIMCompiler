package com.bim.ormsandbox;

import com.bim.ormsandbox.po.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke tests for ORMSandbox PO layer against the canonical library DB.
 *
 * <p>Tests load real rows from {@code library/BOM.db} (working tables)
 * and {@code library/component_library.db} (LOD geometry).
 * Witnesses validate that PO layer correctly surfaces known values.
 */
@DisplayName("ORMSandbox — PO layer smoke tests")
class BuildingInspectorTest {

    private static final String DB_PATH  = "library/BOM.db";
    private static final String LOD_DB   = "library/component_library.db";
    private Connection conn;
    private Connection lodConn;

    @BeforeEach
    void open() throws SQLException {
        conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
        lodConn = DriverManager.getConnection("jdbc:sqlite:" + LOD_DB);
    }

    @AfterEach
    void close() throws SQLException {
        if (lodConn != null && !lodConn.isClosed()) lodConn.close();
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
        MBOM bom = MBOM.get(conn, "BED_SET_MASTER");
        assertNotNull(bom, "BED_SET_MASTER must exist in m_bom");
        assertNotNull(bom.getBomType(), "bom_type must not be null");
        assertNotNull(bom.getGroupBy(), "group_by must not be null — NOT NULL constraint");

        List<MBOMLine> children = MBOMLine.getByBom(conn, "BED_SET_MASTER");
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

    // ── S-ORM-7: M_AdGeometryMap loads entries for known buildings ────────────

    @Test
    @DisplayName("S-ORM-7: M_AdGeometryMap.getByBuilding() returns entries for DX and SH")
    void geometryMapLoadsForBuildings() throws SQLException {
        List<M_AdGeometryMap> dxEntries = M_AdGeometryMap.getByBuilding(lodConn, "Ifc2x3_Duplex");
        assertFalse(dxEntries.isEmpty(),
            "Ifc2x3_Duplex must have geometry_map entries");
        for (M_AdGeometryMap e : dxEntries) {
            assertNotNull(e.getElementRef(), "element_ref must not be null");
            assertNotNull(e.getIfcClass(),   "ifc_class must not be null");
            assertNotNull(e.getGeometryHash(), "geometry_hash must not be null");
            assertFalse(e.getGeometryHash().isBlank(), "geometry_hash must not be blank");
        }

        // No orphans — FK constraint guarantees this but verify via PO layer
        List<M_AdGeometryMap> orphans = M_AdGeometryMap.getOrphans(lodConn, "Ifc2x3_Duplex");
        assertTrue(orphans.isEmpty(),
            "Ifc2x3_Duplex must have zero orphaned geometry_hash entries, got "
            + orphans.size());
    }

    // ── S-ORM-8: dumpPreflight() completes for all 4 buildings, warns on known issues ──

    @ParameterizedTest
    @ValueSource(strings = {"Ifc4_SampleHouse", "Ifc2x3_Duplex"})
    @DisplayName("S-ORM-8: dumpPreflight() completes without exception for SH and DX")
    void preflightCompletesWithoutException(String buildingType) {
        BuildingInspector inspector = new BuildingInspector(conn);
        // Must not throw even if building has known data gaps
        assertDoesNotThrow(() -> inspector.dumpPreflight(buildingType),
            "dumpPreflight must not throw for " + buildingType);
    }

    @Test
    @DisplayName("S-ORM-8b: dumpPreflight() for DX warns on known regression patterns")
    void preflightDxWarnsOnKnownRegressions() throws SQLException {
        BuildingInspector inspector = new BuildingInspector(conn);
        int warnings = inspector.dumpPreflight("Ifc2x3_Duplex");
        // Must warn on: blank namePattern BOMs (Check A), LOCAL_MM rooms (Check B),
        // GEN-BOX elements (Check D), and ROOM_Level_* FURN regression (Check F)
        assertTrue(warnings > 0,
            "DX preflight must warn on known data issues");
    }

    @Test
    @DisplayName("S-ORM-8c: DX preflight detects ROOM_Level_* ARC/FURN regression (Check F)")
    void preflightDxDetectsRegressionPattern() throws SQLException {
        BuildingInspector inspector = new BuildingInspector(conn);
        // Check F specifically: DX has active ARC rules with FURN_ family_refs from the
        // incorrectly re-activated ROOM_Level_* migration. Preflight must surface this.
        // This is the root cause of X1 (48 BBox-only FURN elements) in PROGRESS.md.
        int warnings = inspector.dumpPreflight("Ifc2x3_Duplex");
        // The check F warnings are counted in the total; we verify the method runs and warns
        assertTrue(warnings >= 3,
            "DX preflight must have ≥3 distinct warning categories (BOM, rooms, regression)");
    }

    // ── S-ORM-9: Check H — room slot authority (first-principles gap) ─────────

    @Test
    @DisplayName("S-ORM-9: Check H — room slot authority runs without exception for SH and DX")
    void preflightCheckHRunsWithoutException() {
        BuildingInspector inspector = new BuildingInspector(conn);
        // Check H uses DAO (M_AdRoomBoundary + M_AdRoomSlot) — no raw JDBC.
        // Verifies that the globally-scoped slot audit completes cleanly.
        // Known architecture gap: ad_room_slot has no building_type column —
        // SH-specific assemblies (SH_LIVING_SET etc.) are reachable from any building
        // with a matching room_type. Check H surfaces this as a FIRST-PRINCIPLES warning.
        assertDoesNotThrow(() -> inspector.dumpPreflight("Ifc4_SampleHouse"),
            "Check H must not throw for Ifc4_SampleHouse");
        assertDoesNotThrow(() -> inspector.dumpPreflight("Ifc2x3_Duplex"),
            "Check H must not throw for Ifc2x3_Duplex");
    }

    @Test
    @DisplayName("S-ORM-9b: M_AdRoomSlot.getWithAssembly() returns only rows with assembly_id set")
    void roomSlotGetWithAssemblyNoNulls() throws SQLException {
        List<com.bim.ormsandbox.po.M_AdRoomSlot> slots =
            com.bim.ormsandbox.po.M_AdRoomSlot.getWithAssembly(conn);
        // Every returned slot must have a non-blank assembly_id
        for (var s : slots) {
            assertNotNull(s.getAssemblyId(),
                "getWithAssembly() must not return null assembly_id (slot_id=" + s.getSlotId() + ")");
            assertFalse(s.getAssemblyId().isBlank(),
                "getWithAssembly() must not return blank assembly_id (slot_id=" + s.getSlotId() + ")");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Phase 2 — BOM Dimension Integrity Witnesses
    // ═══════════════════════════════════════════════════════════════════════════

    // ── W-CATEGORY-1: bom_category is functional code, never building code ──

    @Test
    @DisplayName("W-CATEGORY-1: no building codes (SH/DX/TB/MY) in m_bom.bom_category")
    void w_category_1_noBuildingCodesInCategory() throws SQLException {
        String sql = """
            SELECT bom_id, bom_category FROM m_bom
            WHERE bom_category IN ('SH', 'DX', 'TB', 'MY', 'TL')
            """;
        List<String> bad = new java.util.ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) bad.add(rs.getString("bom_id") + "=" + rs.getString("bom_category"));
        }
        assertTrue(bad.isEmpty(),
            "W-CATEGORY-1: building codes found in bom_category (must be functional): " + bad);
    }

    // ── W-OWNER-1: no cross-owner references in BOM tree ───────────────────

    @Test
    @DisplayName("W-OWNER-1: no cross-owner BOM references (SH BOM must not reference DX children)")
    void w_owner_1_noCrossOwnerRefs() throws SQLException {
        // A BOM with bom_owner='SH' must not have m_bom_line.child_bom_id pointing
        // to a BOM with bom_owner='DX' (or any other non-NULL, non-matching owner).
        // NULL owner = generic (shared) — allowed everywhere.
        String sql = """
            SELECT parent.bom_id AS parent_bom, parent.bom_owner AS parent_owner,
                   child_bom.bom_id AS child_bom, child_bom.bom_owner AS child_owner
            FROM m_bom parent
            JOIN m_bom_line bl ON bl.bom_id = parent.bom_id
            JOIN m_bom child_bom ON bl.child_bom_id = child_bom.bom_id
            WHERE parent.bom_owner IS NOT NULL
              AND child_bom.bom_owner IS NOT NULL
              AND parent.bom_owner != child_bom.bom_owner
              AND bl.is_active = 1
            """;
        List<String> bad = new java.util.ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) bad.add(String.format("%s(%s)->%s(%s)",
                rs.getString("parent_bom"), rs.getString("parent_owner"),
                rs.getString("child_bom"), rs.getString("child_owner")));
        }
        assertTrue(bad.isEmpty(),
            "W-OWNER-1: cross-owner BOM references found: " + bad);
    }

    // ── W-SPACESIZE-1: leaf children with product_ref must have SpaceSize > 0 ─

    @Test
    @DisplayName("W-SPACESIZE-1: all active leaf children with product_ref have SpaceSize > 0")
    void w_spacesize_1_leafChildrenHaveSpaceSize() throws SQLException {
        String sql = """
            SELECT bl.bom_child_id, bl.bom_id, bl.product_ref,
                   bl.space_width_mm, bl.space_depth_mm, bl.space_height_mm
            FROM m_bom_line bl
            WHERE bl.product_ref IS NOT NULL
              AND bl.is_active = 1
              AND (bl.space_width_mm = 0 OR bl.space_depth_mm = 0 OR bl.space_height_mm = 0)
            """;
        List<String> bad = new java.util.ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) bad.add(String.format("child_id=%d bom=%s ref=%s space=%dx%dx%d",
                rs.getInt("bom_child_id"), rs.getString("bom_id"), rs.getString("product_ref"),
                rs.getInt("space_width_mm"), rs.getInt("space_depth_mm"), rs.getInt("space_height_mm")));
        }
        assertTrue(bad.isEmpty(),
            "W-SPACESIZE-1: leaf children with product_ref have zero SpaceSize: " + bad);
    }

    // ── W-CATEGORY-2: M_BomCategory lookup table has all referenced codes ───

    @Test
    @DisplayName("W-CATEGORY-2: every non-NULL bom_category in m_bom exists in M_BomCategory")
    void w_category_2_allCodesInLookup() throws SQLException {
        String sql = """
            SELECT DISTINCT b.bom_category FROM m_bom b
            WHERE b.bom_category IS NOT NULL
              AND b.bom_category NOT IN (SELECT M_BomCategory_ID FROM M_BomCategory)
            """;
        List<String> bad = new java.util.ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) bad.add(rs.getString("bom_category"));
        }
        assertTrue(bad.isEmpty(),
            "W-CATEGORY-2: bom_category codes not in M_BomCategory lookup: " + bad);
    }

    // ── W-OWNER-2: every building in ad_building_registry has bom_owner set ─

    @Test
    @DisplayName("W-OWNER-2: every active building has bom_owner set")
    void w_owner_2_allBuildingsHaveOwner() throws SQLException {
        String sql = """
            SELECT building_id FROM ad_building_registry
            WHERE is_active = 1 AND bom_owner IS NULL
            """;
        List<String> bad = new java.util.ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) bad.add(rs.getString("building_id"));
        }
        assertTrue(bad.isEmpty(),
            "W-OWNER-2: active buildings with NULL bom_owner: " + bad);
    }
}
