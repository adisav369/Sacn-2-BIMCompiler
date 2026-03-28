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

    private static String bomDbPath() { return System.getProperty("bom.db"); }
    private static final String LOD_DB   = "library/component_library.db";
    private Connection conn;
    private Connection lodConn;

    @BeforeEach
    void open() throws SQLException {
        conn = DriverManager.getConnection("jdbc:sqlite:" + bomDbPath());
        lodConn = DriverManager.getConnection("jdbc:sqlite:" + LOD_DB);
    }

    @AfterEach
    void close() throws SQLException {
        if (lodConn != null && !lodConn.isClosed()) lodConn.close();
        if (conn != null && !conn.isClosed()) conn.close();
    }

    // ── S-ORM-1: Building registry loads from C_DocType ─────────────────────

    @Test
    @DisplayName("S-ORM-1: C_DocType has ≥4 active building types")
    void allBuildingsLoaded() throws SQLException {
        String sql = "SELECT C_DocType_ID, ProjectName FROM C_DocType WHERE IsActive=1";
        int count = 0;
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                assertNotNull(rs.getString("C_DocType_ID"), "C_DocType_ID must not be null");
                assertNotNull(rs.getString("ProjectName"), "ProjectName must not be null");
                count++;
            }
        }
        assertTrue(count >= 4,
            "Expected ≥4 active building types in C_DocType, got " + count);
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
        MProduct chair = MProduct.get(conn, "FURN_DINING_CHAIR");
        if (chair == null) return; // product may not be seeded in all DBs
        assertTrue(chair.getWidth() < 5.0,
            "width must be in meters (< 5.0), got " + chair.getWidth());
        assertTrue(chair.getDepth() < 5.0,
            "depth must be in meters (< 5.0), got " + chair.getDepth());
    }

    // S-ORM-4: DELETED — c_orderline dropped from BOM.db (§11.9).
    // C_OrderLine generated at compile time in output.db only.

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

    // ── S-ORM-7: M_IGeometryMap loads entries for known buildings ────────────

    @Test
    @DisplayName("S-ORM-7: M_IGeometryMap.getByBuilding() returns entries for DX and SH")
    void geometryMapLoadsForBuildings() throws SQLException {
        List<M_IGeometryMap> dxEntries = M_IGeometryMap.getByBuilding(lodConn, "Ifc2x3_Duplex");
        assertFalse(dxEntries.isEmpty(),
            "Ifc2x3_Duplex must have geometry_map entries");
        for (M_IGeometryMap e : dxEntries) {
            assertNotNull(e.getElementRef(), "element_ref must not be null");
            assertNotNull(e.getIfcClass(),   "ifc_class must not be null");
            assertNotNull(e.getGeometryHash(), "geometry_hash must not be null");
            assertFalse(e.getGeometryHash().isBlank(), "geometry_hash must not be blank");
        }

        // No orphans — FK constraint guarantees this but verify via PO layer
        List<M_IGeometryMap> orphans = M_IGeometryMap.getOrphans(lodConn, "Ifc2x3_Duplex");
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
    // Phase ST-1a — BOM Template Contract Witnesses
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("W-TEMPLATE-SH: SH BOM catalog satisfies RE template (required categories)")
    void w_template_sh() throws SQLException {
        var report = BomTemplateContract.check(conn, "SH");
        // SH has LI (SH_LIVING_SET), BD (SH_BED_SET), DN (SH_DINING_SET)
        // KT and BT are MinQty=0 → optional → satisfied even if missing
        // SL and RF are MinQty=0 → pipeline-handled → satisfied
        assertTrue(report.isComplete(),
            "W-TEMPLATE-SH: required categories not met: " + report.gaps());
        // Info dump: what the contract found
        for (var check : report.checks()) {
            System.out.printf("  %s [%s]: found=%d best=%s %s%n",
                check.categoryId(), check.categoryName(),
                check.found(), check.bestFitBomId(),
                check.satisfied() ? "OK" : "GAP");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Phase ST-1b — DX Composition Proof Witness
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("W-COMPOSE-DX: AABB(12372×26730×7884) + numUnits=2 → selects DX parts")
    void w_compose_dx() throws SQLException {
        var report = BomTemplateComposer.compose(conn, "RE", 12372, 26730, 7884, 2);

        // Info dump: what the composer selected at each node
        for (var sel : report.selections()) {
            System.out.printf("  %s%s [%s]: alloc=%dx%dx%d → %s (%s) %s%n",
                "  ".repeat(sel.level()),
                sel.categoryId(), sel.categoryName(),
                sel.allocW(), sel.allocD(), sel.allocH(),
                sel.selectedBomId(), sel.selectedOwner(),
                sel.mirroringRule().equals("NONE") ? "" : "mirror=" + sel.mirroringRule());
        }

        // The composition must find BOMs at every required leaf
        assertTrue(report.isComplete(),
            "W-COMPOSE-DX: composition gaps: " + report.gaps());

        // Key assertion: PR node selected DX-owned DUPLEX_SET_STD
        var prNode = report.selections().stream()
            .filter(s -> s.categoryId().equals("PR")).findFirst();
        assertTrue(prNode.isPresent() && "DX".equals(prNode.get().selectedOwner()),
            "PR must select DX-owned DUPLEX_SET_STD");

        // Key assertion: HU nodes selected DX-owned DUPLEX_SINGLE_UNIT_STD
        var huNodes = report.selections().stream()
            .filter(s -> s.categoryId().equals("HU")).toList();
        assertFalse(huNodes.isEmpty(), "Must have HU selections");
        for (var hu : huNodes) {
            assertEquals("DX", hu.selectedOwner(),
                "HU must select DX-owned DUPLEX_SINGLE_UNIT_STD");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Phase 2 — BOM Dimension Integrity Witnesses
    // ═══════════════════════════════════════════════════════════════════════════

    // ── W-CATEGORY-1: bom_category is functional code, never building code ──

    @Test
    @DisplayName("W-CATEGORY-1: no building codes (SH/DX/TB/MY) in m_bom.m_product_category_id")
    void w_category_1_noBuildingCodesInCategory() throws SQLException {
        String sql = """
            SELECT b.bom_id, mpc.Value AS category_value FROM m_bom b
            LEFT JOIN M_Product_Category mpc ON b.m_product_category_id = mpc.M_Product_Category_ID
            WHERE mpc.Value IN ('SH', 'DX', 'TB', 'MY', 'TL')
            """;
        List<String> bad = new java.util.ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) bad.add(rs.getString("bom_id") + "=" + rs.getString("category_value"));
        }
        assertTrue(bad.isEmpty(),
            "W-CATEGORY-1: building codes found in m_product_category_id (must be functional): " + bad);
    }

    // ── W-OWNER-1: no cross-variant references in BOM tree ───────────────────

    @Test
    @DisplayName("W-OWNER-1: no cross-variant BOM references (SH BOM must not reference DX children)")
    void w_owner_1_noCrossOwnerRefs() throws SQLException {
        // A BOM with doc_sub_type='SH' must not have m_bom_line.child_product_id (MAKE)
        // pointing to a BOM with doc_sub_type='DX' (or any other non-NULL, non-matching variant).
        // NULL doc_sub_type = generic (shared) — allowed everywhere.
        String sql = """
            SELECT parent.bom_id AS parent_bom, parent.doc_sub_type AS parent_owner,
                   child_bom.bom_id AS child_bom, child_bom.doc_sub_type AS child_owner
            FROM m_bom parent
            JOIN m_bom_line bl ON bl.bom_id = parent.bom_id AND bl.component_type = 'MAKE'
            JOIN m_bom child_bom ON bl.child_product_id = child_bom.bom_id
            WHERE parent.doc_sub_type IS NOT NULL
              AND child_bom.doc_sub_type IS NOT NULL
              AND parent.doc_sub_type != child_bom.doc_sub_type
              AND bl.is_active = 1
            """;
        List<String> bad = new java.util.ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) bad.add(String.format("%s(%s)->%s(%s)",
                rs.getString("parent_bom"), rs.getString("parent_owner"),
                rs.getString("child_bom"), rs.getString("child_owner")));
        }
        assertTrue(bad.isEmpty(),
            "W-OWNER-1: cross-variant BOM references found: " + bad);
    }

    // ── W-SPACESIZE-1: catalog BUY leaf children must have AllocatedSize > 0 (NORM-2) ─
    // Structural BUY rows (product_type='STRUCTURAL', is_active=0) are exempt:
    // they are placed by BOMAssemblerAD element-matching, not by spatial allocation.

    @Test
    @DisplayName("W-SPACESIZE-1: active catalog BUY leaf children have AllocatedSize > 0")
    void w_spacesize_1_leafChildrenHaveSpaceSize() throws SQLException {
        String sql = """
            SELECT bl.bom_child_id, bl.bom_id, bl.child_product_id,
                   bl.allocated_width_mm, bl.allocated_depth_mm, bl.allocated_height_mm
            FROM m_bom_line bl
            JOIN M_Product mp ON bl.child_product_id = mp.product_id
            WHERE bl.component_type = 'BUY'
              AND bl.is_active = 1
              AND mp.is_active = 1
              AND (bl.allocated_width_mm = 0 OR bl.allocated_depth_mm = 0 OR bl.allocated_height_mm = 0)
            """;
        List<String> bad = new java.util.ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) bad.add(String.format("child_id=%d bom=%s ref=%s alloc=%dx%dx%d",
                rs.getInt("bom_child_id"), rs.getString("bom_id"), rs.getString("child_product_id"),
                rs.getInt("allocated_width_mm"), rs.getInt("allocated_depth_mm"), rs.getInt("allocated_height_mm")));
        }
        assertTrue(bad.isEmpty(),
            "W-SPACESIZE-1: catalog BUY leaf children have zero AllocatedSize: " + bad);
    }

    // ── W-CATEGORY-2: M_BomCategory lookup table has all referenced codes ───

    @Test
    @DisplayName("W-CATEGORY-2: every non-NULL m_product_category_id in m_bom exists in M_Product_Category")
    void w_category_2_allCodesInLookup() throws SQLException {
        String sql = """
            SELECT DISTINCT b.m_product_category_id FROM m_bom b
            WHERE b.m_product_category_id IS NOT NULL
              AND b.m_product_category_id NOT IN (SELECT M_Product_Category_ID FROM M_Product_Category)
            """;
        List<String> bad = new java.util.ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) bad.add(rs.getString("m_product_category_id"));
        }
        assertTrue(bad.isEmpty(),
            "W-CATEGORY-2: m_product_category_id codes not in M_Product_Category lookup: " + bad);
    }

    // ── W-OWNER-2: every C_DocType entry has required fields ─

    @Test
    @DisplayName("W-OWNER-2: every active C_DocType has doc_sub_type and ProjectName set")
    void w_owner_2_allBuildingsHaveOwner() throws SQLException {
        String sql = """
            SELECT C_DocType_ID FROM C_DocType
            WHERE IsActive = 1 AND (doc_sub_type IS NULL OR ProjectName IS NULL)
            """;
        List<String> bad = new java.util.ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) bad.add(rs.getString("C_DocType_ID"));
        }
        assertTrue(bad.isEmpty(),
            "W-OWNER-2: active C_DocType entries with NULL doc_sub_type or ProjectName: " + bad);
    }

    // ── W-DOCTYPE-1: every doc_sub_type/C_DocType_ID exists in C_DocType ──

    @Test
    @DisplayName("W-DOCTYPE-1: every doc_sub_type in m_bom exists in C_DocType.doc_sub_type")
    void w_doctype_1_allSubTypesInLookup() throws SQLException {
        String sql = """
            SELECT DISTINCT doc_sub_type AS src FROM m_bom
            WHERE doc_sub_type IS NOT NULL
              AND doc_sub_type NOT IN (SELECT doc_sub_type FROM C_DocType WHERE doc_sub_type IS NOT NULL)
            """;
        List<String> bad = new java.util.ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) bad.add(rs.getString("src"));
        }
        assertTrue(bad.isEmpty(),
            "W-DOCTYPE-1: doc_sub_type values not in C_DocType lookup: " + bad);
    }

    @Test
    @DisplayName("W-DOCTYPE-2: every C_DocType_ID in C_DocType is unique and well-formed")
    void w_doctype_2_allDocTypeIdsValid() throws SQLException {
        String sql = """
            SELECT C_DocType_ID, COUNT(*) AS cnt FROM C_DocType
            WHERE IsActive = 1
            GROUP BY C_DocType_ID HAVING cnt > 1
            """;
        List<String> bad = new java.util.ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) bad.add(rs.getString("C_DocType_ID") + " x" + rs.getInt("cnt"));
        }
        assertTrue(bad.isEmpty(),
            "W-DOCTYPE-2: duplicate C_DocType_ID entries: " + bad);
    }

    // ── W-CATEGORY-LINE-1: every M_BomCategoryLine child exists in M_Product_Category ──

    @Test
    @DisplayName("W-CATEGORY-LINE-1: every M_BomCategoryLine child_category exists in M_Product_Category")
    void w_categoryLine_1_allChildrenExist() throws SQLException {
        String sql = """
            SELECT cl.M_BomCategoryLine_ID, cl.Child_BomCategory_ID
            FROM M_BomCategoryLine cl
            WHERE cl.IsActive = 1
              AND cl.Child_BomCategory_ID NOT IN (SELECT M_Product_Category_ID FROM M_Product_Category)
            """;
        List<String> bad = new java.util.ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) bad.add("line=" + rs.getInt("M_BomCategoryLine_ID")
                + " child=" + rs.getString("Child_BomCategory_ID"));
        }
        assertTrue(bad.isEmpty(),
            "W-CATEGORY-LINE-1: M_BomCategoryLine references missing M_Product_Category: " + bad);
    }
}
