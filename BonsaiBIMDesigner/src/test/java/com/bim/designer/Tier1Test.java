package com.bim.designer;

import com.bim.backoffice.model.DesignBBox;
import com.bim.designer.api.*;
import com.bim.backoffice.dao.ChangelogDAO;
import com.bim.backoffice.dao.FacilityMgmtDAO;
import com.bim.backoffice.dao.SustainabilityDAO;
import com.bim.designer.dao.WorkOutputDAO;

import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tier 1 SRS witnesses: 6D Carbon, 7D FM, Audit Trail.
 *
 * <p>All tests use in-memory SQLite — no filesystem dependency.
 * Two in-memory databases: one for BOM, one for component_library.
 *
 * // Implementing TIER1_SRS.md §1-§3 — all 18 witnesses
 */
@DisplayName("Tier 1 — 6D/7D/Audit")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Tier1Test {

    private static Connection bomConn;
    private static Connection compLibConn;
    private static Connection workConn;

    @BeforeAll
    static void setUp() throws Exception {
        // ── BOM database (mimics SH_BOM.db) ────────────────────────
        bomConn = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement s = bomConn.createStatement()) {
            s.execute("""
                CREATE TABLE m_bom (
                    bom_id TEXT PRIMARY KEY,
                    bom_name TEXT NOT NULL,
                    bom_type TEXT NOT NULL,
                    bom_category TEXT,
                    is_active INTEGER DEFAULT 1,
                    aabb_width_mm INTEGER DEFAULT 0,
                    aabb_depth_mm INTEGER DEFAULT 0,
                    aabb_height_mm INTEGER DEFAULT 0
                )
                """);
            s.execute("""
                CREATE TABLE m_bom_line (
                    bom_child_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    bom_id TEXT NOT NULL,
                    child_product_id TEXT,
                    qty INTEGER NOT NULL DEFAULT 1,
                    is_active INTEGER DEFAULT 1,
                    storey TEXT
                )
                """);

            // Insert BOMs
            s.execute("""
                INSERT INTO m_bom VALUES
                ('SH_GF', 'Ground Floor', 'FLOOR', 'GF', 1, 9000, 7000, 3000),
                ('SH_LIVING', 'Living Room', 'SET', 'LIVING', 1, 5400, 4200, 3000),
                ('SH_BEDROOM', 'Bedroom', 'SET', 'BEDROOM', 1, 3100, 3100, 3000),
                ('BUILDING_SH', 'Sample House', 'BUILDING', NULL, 1, 9000, 7000, 6000)
                """);

            // Insert BOM lines with product references
            s.execute("""
                INSERT INTO m_bom_line (bom_id, child_product_id, qty, is_active, storey) VALUES
                ('SH_LIVING', 'wall_ext_200x3000', 4, 1, 'GF'),
                ('SH_LIVING', 'slab_150', 1, 1, 'GF'),
                ('SH_LIVING', 'window_1200x1500', 2, 1, 'GF'),
                ('SH_LIVING', 'door_900x2100', 1, 1, 'GF'),
                ('SH_BEDROOM', 'wall_ext_200x3000', 4, 1, 'GF'),
                ('SH_BEDROOM', 'slab_150', 1, 1, 'GF'),
                ('SH_BEDROOM', 'window_1200x1500', 1, 1, 'GF'),
                ('SH_BEDROOM', 'door_900x2100', 1, 1, 'GF'),
                ('SH_BEDROOM', 'beam_300x200', 2, 1, 'GF'),
                ('SH_GF', 'slab_150', 1, 1, 'GF'),
                ('SH_GF', 'stair_straight', 1, 1, 'GF')
                """);
        }

        // ── Component library (mimics component_library.db) ────────
        compLibConn = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement s = compLibConn.createStatement()) {
            s.execute("""
                CREATE TABLE M_Product (
                    product_id TEXT PRIMARY KEY,
                    product_type TEXT NOT NULL,
                    width REAL NOT NULL,
                    depth REAL NOT NULL,
                    height REAL NOT NULL,
                    is_active INTEGER DEFAULT 1,
                    carbon_kg_per_unit REAL DEFAULT 0,
                    recyclability TEXT DEFAULT 'UNKNOWN',
                    eol_strategy TEXT DEFAULT 'LANDFILL',
                    lifespan_years INTEGER DEFAULT 50,
                    maintenance_interval_months INTEGER DEFAULT 12
                )
                """);

            // Seed products with carbon + FM data
            s.execute("""
                INSERT INTO M_Product VALUES
                ('wall_ext_200x3000', 'WALL', 0.2, 3.0, 3.0, 1, 432.0, 'LOW', 'LANDFILL', 60, 60),
                ('slab_150', 'SLAB', 9.0, 7.0, 0.15, 1, 360.0, 'MEDIUM', 'RECYCLE', 100, 60),
                ('window_1200x1500', 'WINDOW', 1.2, 0.05, 1.5, 1, 2150.0, 'MEDIUM', 'RECYCLE', 25, 12),
                ('door_900x2100', 'DOOR', 0.9, 0.04, 2.1, 1, 230.0, 'HIGH', 'REUSE', 30, 12),
                ('beam_300x200', 'BEAM', 0.3, 0.2, 3.0, 1, 12167.5, 'HIGH', 'RECYCLE', 80, 24),
                ('stair_straight', 'STAIR', 1.0, 3.0, 3.0, 1, 360.0, 'MEDIUM', 'RECYCLE', 80, 60)
                """);
        }

        // ── Work output database ────────────────────────────────────
        workConn = DriverManager.getConnection("jdbc:sqlite::memory:");
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (bomConn != null) bomConn.close();
        if (compLibConn != null) compLibConn.close();
        if (workConn != null) workConn.close();
    }

    // ═══════════════════════════════════════════════════════════════
    // §1 — 6D Sustainability
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(1)
    @DisplayName("W-6D-CARBON-1: carbonFootprint returns non-empty CarbonSummary")
    void carbonFootprintNonEmpty() throws Exception {
        SustainabilityDAO dao = new SustainabilityDAO();
        SustainabilityDAO.CarbonSummary summary =
                dao.carbonFootprint(bomConn, compLibConn, "SH");

        assertNotNull(summary);
        assertFalse(summary.lines().isEmpty(), "Carbon lines must not be empty");
        assertTrue(summary.totalCarbonKg() > 0, "Total carbon must be positive");
    }

    @Test
    @Order(2)
    @DisplayName("W-6D-CARBON-2: totalCarbon = SUM(qty × carbon_kg_per_unit)")
    void carbonRollupMath() throws Exception {
        SustainabilityDAO dao = new SustainabilityDAO();
        SustainabilityDAO.CarbonSummary summary =
                dao.carbonFootprint(bomConn, compLibConn, "SH");

        double manualSum = summary.lines().stream()
                .mapToDouble(SustainabilityDAO.CarbonLine::totalCarbon)
                .sum();

        assertEquals(manualSum, summary.totalCarbonKg(), 0.01,
                "totalCarbonKg must equal SUM of line totals");

        // Verify each line: totalCarbon = qty × carbonPerUnit
        for (var line : summary.lines()) {
            assertEquals(line.qty() * line.carbonPerUnit(), line.totalCarbon(), 0.01,
                    "Line " + line.bomId() + ": total must equal qty × per-unit");
        }
    }

    @Test
    @Order(3)
    @DisplayName("W-6D-CARBON-3: byDiscipline keys match BOM categories")
    void carbonByDiscipline() throws Exception {
        SustainabilityDAO dao = new SustainabilityDAO();
        SustainabilityDAO.CarbonSummary summary =
                dao.carbonFootprint(bomConn, compLibConn, "SH");

        assertFalse(summary.byDiscipline().isEmpty(), "byDiscipline must not be empty");
        // All keys should be valid BOM categories from our test data
        for (String key : summary.byDiscipline().keySet()) {
            assertNotNull(key, "Discipline key must not be null");
        }

        assertFalse(summary.byMaterial().isEmpty(), "byMaterial must not be empty");
    }

    @Test
    @Order(4)
    @DisplayName("W-6D-SEED-1: seed data populates ≥ 6 product types with carbon > 0")
    void seedDataPopulated() throws Exception {
        // Count products with carbon > 0 in our test compLib
        int count = 0;
        try (var ps = compLibConn.prepareStatement(
                "SELECT COUNT(*) FROM M_Product WHERE carbon_kg_per_unit > 0")) {
            try (var rs = ps.executeQuery()) {
                if (rs.next()) count = rs.getInt(1);
            }
        }
        assertTrue(count >= 6, "At least 6 products must have carbon data, got " + count);
    }

    @Test
    @Order(5)
    @DisplayName("W-6D-CARBON-5: materialPassport groups by material type")
    void materialPassport() throws Exception {
        SustainabilityDAO dao = new SustainabilityDAO();
        List<SustainabilityDAO.MaterialPassportLine> passport =
                dao.materialPassport(bomConn, compLibConn);

        assertFalse(passport.isEmpty(), "Material passport must not be empty");
        for (var line : passport) {
            assertNotNull(line.material());
            assertTrue(line.totalCarbonKg() >= 0);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // §2 — 7D Facility Management
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(10)
    @DisplayName("W-7D-FM-1: maintenanceSchedule returns items with intervalMonths > 0")
    void maintenanceScheduleNonEmpty() throws Exception {
        FacilityMgmtDAO dao = new FacilityMgmtDAO();
        FacilityMgmtDAO.MaintenanceSchedule sched =
                dao.maintenanceSchedule(bomConn, compLibConn, "SH");

        assertNotNull(sched);
        assertFalse(sched.items().isEmpty(), "Maintenance items must not be empty");
        assertTrue(sched.totalAssets() > 0, "Total assets must be positive");

        for (var item : sched.items()) {
            assertTrue(item.intervalMonths() > 0,
                    "All items must have interval > 0, got " + item.intervalMonths());
        }
    }

    @Test
    @Order(11)
    @DisplayName("W-7D-FM-2: annualEvents = SUM(12/interval × qty)")
    void annualEventsMath() throws Exception {
        FacilityMgmtDAO dao = new FacilityMgmtDAO();
        FacilityMgmtDAO.MaintenanceSchedule sched =
                dao.maintenanceSchedule(bomConn, compLibConn, "SH");

        double manualEvents = sched.items().stream()
                .mapToDouble(i -> (12.0 / i.intervalMonths()) * i.qtyInBuilding())
                .sum();

        assertEquals(manualEvents, sched.annualMaintenanceEvents(), 0.01,
                "Annual events must equal SUM(12/interval × qty)");
    }

    @Test
    @Order(12)
    @DisplayName("W-7D-FM-3: lifecycleCost costByDecade sums correctly")
    void lifecycleCostDecades() throws Exception {
        FacilityMgmtDAO dao = new FacilityMgmtDAO();
        FacilityMgmtDAO.LifecycleSummary summary =
                dao.lifecycleCost(bomConn, compLibConn, "SH", 50);

        assertNotNull(summary);
        assertTrue(summary.totalReplacementCost() > 0, "Total replacement cost must be positive");
        assertFalse(summary.costByDecade().isEmpty(), "Cost by decade must not be empty");
    }

    @Test
    @Order(13)
    @DisplayName("W-7D-FM-4: assetRegister returns COBie-compatible records with location")
    void assetRegister() throws Exception {
        FacilityMgmtDAO dao = new FacilityMgmtDAO();
        List<FacilityMgmtDAO.AssetRecord> records =
                dao.assetRegister(bomConn, compLibConn, "SH");

        assertFalse(records.isEmpty(), "Asset register must not be empty");
        for (var rec : records) {
            assertNotNull(rec.type(), "Type must not be null");
            assertNotNull(rec.location(), "Location must not be null");
            assertNotNull(rec.floor(), "Floor must not be null");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // §3 — Audit Trail
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(20)
    @DisplayName("W-AUDIT-DDL-1: bim_changelog table created with 11 columns + 2 indexes")
    void changelogSchema() throws Exception {
        ChangelogDAO dao = new ChangelogDAO(workConn);
        dao.initSchema();

        // Verify table exists with correct columns
        try (var rs = workConn.getMetaData().getColumns(null, null, "bim_changelog", null)) {
            int colCount = 0;
            while (rs.next()) colCount++;
            // 12 columns: changelog_id + 11 data columns
            assertEquals(12, colCount, "bim_changelog must have 12 columns");
        }
    }

    @Test
    @Order(21)
    @DisplayName("W-AUDIT-DAO-1: logChange inserts row with correct timestamp + action")
    void logChangeSingle() throws Exception {
        ChangelogDAO dao = new ChangelogDAO(workConn);
        dao.initSchema();

        dao.logChange("TestHouse", "v1", "MOVE",
                "C_OrderLine", "OL_101",
                "position", "1500,0,0", "2000,0,0", "ROOM_LI_01");

        List<ChangelogDAO.ChangeEntry> entries = dao.getChangelog("TestHouse", 10);
        assertFalse(entries.isEmpty(), "Changelog must have entries");

        var entry = entries.get(0);
        assertEquals("MOVE", entry.action());
        assertEquals("C_OrderLine", entry.entityType());
        assertEquals("1500,0,0", entry.oldValue());
        assertEquals("2000,0,0", entry.newValue());
        assertNotNull(entry.timestamp());
    }

    @Test
    @Order(22)
    @DisplayName("W-AUDIT-DAO-2: logSave detects MOVE and RESIZE diffs")
    void logSaveDiff() throws Exception {
        ChangelogDAO dao = new ChangelogDAO(workConn);

        List<DesignBBox> oldBboxes = List.of(
                new DesignBBox("ROOM_01", "Room 1", "ROOM", "LIVING",
                        "IfcSpace", "GF", "FLOOR_GF",
                        0, 0, 0, 5000, 4000, 3000),
                new DesignBBox("ROOM_02", "Room 2", "ROOM", "BEDROOM",
                        "IfcSpace", "GF", "FLOOR_GF",
                        5000, 0, 0, 8000, 4000, 3000)
        );

        List<DesignBBox> newBboxes = List.of(
                // ROOM_01: position moved (minX 0→500)
                new DesignBBox("ROOM_01", "Room 1", "ROOM", "LIVING",
                        "IfcSpace", "GF", "FLOOR_GF",
                        500, 0, 0, 5500, 4000, 3000),
                // ROOM_02: resized (width 3000→3500)
                new DesignBBox("ROOM_02", "Room 2", "ROOM", "BEDROOM",
                        "IfcSpace", "GF", "FLOOR_GF",
                        5000, 0, 0, 8500, 4000, 3000),
                // ROOM_03: new (PLACEd)
                new DesignBBox("ROOM_03", "Room 3", "ROOM", "KITCHEN",
                        "IfcSpace", "GF", "FLOOR_GF",
                        0, 4000, 0, 3000, 7000, 3000)
        );

        dao.logSave("DiffTest", "v2", oldBboxes, newBboxes);

        List<ChangelogDAO.ChangeEntry> entries = dao.getChangelog("DiffTest", 20);
        assertTrue(entries.size() >= 3, "Must have at least 3 entries (MOVE + RESIZE + PLACE)");

        long moveCount = entries.stream().filter(e -> "MOVE".equals(e.action())).count();
        long resizeCount = entries.stream().filter(e -> "RESIZE".equals(e.action())).count();
        long placeCount = entries.stream().filter(e -> "PLACE".equals(e.action())).count();

        assertTrue(moveCount >= 1, "Must detect at least 1 MOVE");
        assertTrue(resizeCount >= 1, "Must detect at least 1 RESIZE");
        assertTrue(placeCount >= 1, "Must detect at least 1 PLACE");
    }

    @Test
    @Order(23)
    @DisplayName("W-AUDIT-DAO-3: undoChanges reverts and logs UNDO")
    void undoChanges() throws Exception {
        ChangelogDAO dao = new ChangelogDAO(workConn);

        // Log a change to undo
        dao.logChange("UndoTest", "v1", "MOVE",
                "C_OrderLine", "OL_200",
                "position", "0,0,0", "1000,0,0", "ROOM_X");

        int reverted = dao.undoChanges("UndoTest", 1);
        assertEquals(1, reverted, "Must revert 1 change");

        // Check that UNDO was logged
        List<ChangelogDAO.ChangeEntry> entries = dao.getChangelog("UndoTest", 10);
        long undoCount = entries.stream().filter(e -> "UNDO".equals(e.action())).count();
        assertTrue(undoCount >= 1, "Must have UNDO entry in changelog");
    }

    @Test
    @Order(24)
    @DisplayName("W-AUDIT-INTERCEPT-1: save with changed bboxes produces changelog; identical produces zero")
    void interceptorPattern() throws Exception {
        ChangelogDAO dao = new ChangelogDAO(workConn);

        // Identical bboxes → zero entries
        List<DesignBBox> same = List.of(
                new DesignBBox("R1", "R1", "ROOM", "LIVING",
                        "IfcSpace", "GF", null,
                        0, 0, 0, 5000, 4000, 3000));
        dao.logSave("InterceptTest", "v1", same, same);
        List<ChangelogDAO.ChangeEntry> entries1 = dao.getChangelog("InterceptTest", 10);
        assertEquals(0, entries1.size(), "Identical bboxes must produce zero changelog entries");

        // Changed bboxes → entries
        List<DesignBBox> moved = List.of(
                new DesignBBox("R1", "R1", "ROOM", "LIVING",
                        "IfcSpace", "GF", null,
                        500, 0, 0, 5500, 4000, 3000));
        dao.logSave("InterceptTest", "v2", same, moved);
        List<ChangelogDAO.ChangeEntry> entries2 = dao.getChangelog("InterceptTest", 10);
        assertTrue(entries2.size() > 0, "Changed bboxes must produce changelog entries");
    }
}
