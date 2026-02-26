package com.bim.compiler.topologymaker;

import com.bim.compiler.topologymaker.po.*;
import org.junit.jupiter.api.*;

import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the PO layer — BasePO contract, M_ validations, lifecycle hooks.
 *
 * <p>Uses an in-memory SQLite DB seeded with the three topology tables.
 * No migration file required — DDL is inlined here for isolation.
 */
@DisplayName("BasePO — PO layer contract")
class BasePOTest {

    private static final String IN_MEMORY_DB = "jdbc:sqlite::memory:";
    private Connection conn;

    @BeforeEach
    void setUp() throws SQLException {
        conn = DriverManager.getConnection(IN_MEMORY_DB);
        conn.setAutoCommit(false);
        createSchema(conn);
        seedTypology(conn);
        conn.commit();
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (conn != null && !conn.isClosed()) conn.close();
    }

    // ── T-PO-1: load() populates columns by COLUMNNAME constants ─────────────

    @Test
    @DisplayName("T-PO-1: load() populates all columns via COLUMNNAME constants")
    void loadPopulatesColumnsByConstants() throws SQLException {
        M_AdTypologyPattern p = M_AdTypologyPattern.get(conn, "TERRACE_TEST");

        assertNotNull(p, "Typology TERRACE_TEST should be found and active");
        assertEquals("TERRACE_TEST", p.getTypologyId());
        assertEquals("Test Terrace", p.getTypologyName());
        assertEquals("STRIP_ZONES", p.getGridStrategy());
        assertEquals("RESIDENTIAL_TYPE_C", p.getUbblClass());
        assertEquals(9900, p.getBaseWidthMm());
        assertEquals(8500, p.getBaseDepthMm());
        assertNotNull(p.getZoneJson(), "zone_json must be populated");
        assertTrue(p.isActive(), "is_active must be true");
    }

    // ── T-PO-2: set_Value marks dirty; clean columns excluded from UPDATE ─────

    @Test
    @DisplayName("T-PO-2: set_Value marks column dirty; unset columns not dirty")
    void dirtyFlagTracksSetColumns() throws SQLException {
        M_AdRoomBoundary b = new M_AdRoomBoundary(conn);

        assertEquals(0, b.getDirtyColumnCount(), "New object starts with 0 dirty columns");

        b.setBuildingType("TEST_BUILDING");
        assertEquals(1, b.getDirtyColumnCount(), "After one set, 1 dirty column");

        b.setStorey("Ground Floor");
        b.setRoomName("BED_1");
        assertEquals(3, b.getDirtyColumnCount(), "Three sets = 3 dirty columns");
    }

    // ── T-PO-3: dirty set empty after load ────────────────────────────────────

    @Test
    @DisplayName("T-PO-3: dirty set is empty after load (no spurious UPDATE on read)")
    void dirtyEmptyAfterLoad() throws SQLException {
        M_AdTypologyPattern p = M_AdTypologyPattern.get(conn, "TERRACE_TEST");

        assertNotNull(p);
        assertEquals(0, p.getDirtyColumnCount(),
            "Loaded PO must have 0 dirty columns — load must not mark anything dirty");
    }

    // ── T-PO-4: wrong coordinate_frame rejected by beforeSave ─────────────────

    @Test
    @DisplayName("T-PO-4: save() on M_AdRoomBoundary with wrong coordinate_frame throws")
    void wrongCoordinateFrameRejected() {
        M_AdRoomBoundary b = new M_AdRoomBoundary(conn);
        b.setBuildingType("TEST");
        b.setStorey("Ground Floor");
        b.setRoomName("BEDROOM");
        b.setRoomType("BEDROOM");
        b.setGridMinX("0");
        b.setGridMaxX("4000");
        b.setGridMinY("0");
        b.setGridMaxY("3000");
        b.setMinXMm(0);
        b.setMaxXMm(4000);
        b.setMinYMm(0);
        b.setMaxYMm(3000);
        b.setExtractedFrom("TEST");
        b.setCoordinateFrame("LOCAL_MM");   // Wrong — must be DERIVED_MM
        b.setIsActive(true);

        assertThrows(IllegalStateException.class, () -> b.save(),
            "save() must throw IllegalStateException when coordinate_frame != DERIVED_MM");
    }

    // ── T-PO-5: MOrder.completeIt() DR → CO ────────────────────

    @Test
    @DisplayName("T-PO-5: completeIt() transitions DR → CO")
    void completeItTransitionsDrToCo() {
        MOrder reg = new MOrder(conn);
        reg.setDocStatus("DR");

        String error = reg.completeIt();

        assertNull(error, "completeIt() must return null on success");
        assertEquals("CO", reg.getDocStatus(), "DocStatus must be CO after completeIt()");
    }

    // ── T-PO-6: MOrder.voidIt() CO → VO + is_active=false ──────

    @Test
    @DisplayName("T-PO-6: voidIt() transitions CO → VO and sets is_active=false")
    void voidItTransitionsToVoAndDeactivates() {
        MOrder reg = new MOrder(conn);
        reg.setDocStatus("CO");
        reg.setIsActive(true);

        String error = reg.voidIt();

        assertNull(error, "voidIt() must return null on success");
        assertEquals("VO", reg.getDocStatus(), "DocStatus must be VO after voidIt()");
        assertFalse(reg.isActive(), "is_active must be false after voidIt()");
    }

    // ── T-PO-7: M_AdTypologyPattern.beforeSave() rejects blank zone_json ──────

    @Test
    @DisplayName("T-PO-7: beforeSave() rejects blank zone_json")
    void blankZoneJsonRejected() throws SQLException {
        createRoomBoundaryTable(conn);
        conn.commit();

        M_AdTypologyPattern p = new M_AdTypologyPattern(conn);
        p.setTypologyId("INVALID_TEST");
        p.setTypologyName("Invalid");
        p.setGridStrategy("STRIP_ZONES");
        p.setUbblClass("RESIDENTIAL_TYPE_C");
        p.setBaseWidthMm(9000);
        p.setBaseDepthMm(8000);
        p.setZoneJson("");   // blank — must be rejected

        assertThrows(IllegalStateException.class, () -> p.save(),
            "save() must throw IllegalStateException when zone_json is blank");
    }

    // ── T-PO-8: full save() + load() roundtrip for M_AdRoomBoundary ──────────

    @Test
    @DisplayName("T-PO-8: save() inserts row; load() retrieves same values")
    void saveAndLoadRoundtrip() throws SQLException {
        createRoomBoundaryTable(conn);
        conn.commit();

        M_AdRoomBoundary b = new M_AdRoomBoundary(conn);
        b.setBuildingType("RT_BUILD");
        b.setStorey("Ground Floor");
        b.setRoomName("LIVING");
        b.setRoomType("COMMON");
        b.setGridMinX("0");
        b.setGridMaxX("5000");
        b.setGridMinY("0");
        b.setGridMaxY("4000");
        b.setMinXMm(0);
        b.setMaxXMm(5000);
        b.setMinYMm(0);
        b.setMaxYMm(4000);
        b.setCoordinateFrame("DERIVED_MM");
        b.setExtractedFrom("TOPOLOGY_MAKER:TERRACE_MY_1S");
        b.setIsActive(true);
        b.save();

        int savedId = b.getId();
        assertTrue(savedId > 0, "Generated INTEGER PK must be > 0 after save()");
        assertEquals(0, b.getDirtyColumnCount(), "Dirty set must be cleared after save()");

        // Load by generated PK and verify
        M_AdRoomBoundary loaded = new M_AdRoomBoundary(conn);
        assertTrue(loaded.load(savedId), "load() must return true for saved row");
        assertEquals("RT_BUILD", loaded.getBuildingType());
        assertEquals("LIVING", loaded.getRoomName());
        assertEquals(5000, loaded.getMaxXMm());
        assertEquals("DERIVED_MM", loaded.getCoordinateFrame());
        assertEquals(0, loaded.getDirtyColumnCount(), "Dirty set must be empty after load()");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void createSchema(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS ad_typology_template (" +
                "  typology_id   TEXT PRIMARY KEY," +
                "  typology_name TEXT NOT NULL," +
                "  grid_strategy TEXT NOT NULL," +
                "  ubbl_class    TEXT NOT NULL," +
                "  description   TEXT," +
                "  base_width_mm INTEGER NOT NULL," +
                "  base_depth_mm INTEGER NOT NULL," +
                "  zone_json     TEXT NOT NULL," +
                "  is_active     INTEGER NOT NULL DEFAULT 1" +
                ")"
            );
        }
    }

    private static void createRoomBoundaryTable(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS ad_room_boundary (" +
                "  id               INTEGER PRIMARY KEY AUTOINCREMENT," +
                "  building_type    TEXT NOT NULL," +
                "  storey           TEXT NOT NULL," +
                "  room_name        TEXT NOT NULL," +
                "  room_type        TEXT NOT NULL," +
                "  grid_min_x       TEXT NOT NULL DEFAULT ''," +
                "  grid_max_x       TEXT NOT NULL DEFAULT ''," +
                "  grid_min_y       TEXT NOT NULL DEFAULT ''," +
                "  grid_max_y       TEXT NOT NULL DEFAULT ''," +
                "  z_offset_mm      REAL DEFAULT 0," +
                "  is_active        INTEGER DEFAULT 1," +
                "  min_x_mm         REAL," +
                "  max_x_mm         REAL," +
                "  min_y_mm         REAL," +
                "  max_y_mm         REAL," +
                "  building_id      INTEGER," +
                "  extracted_from   TEXT NOT NULL DEFAULT 'GRID_DERIVED'," +
                "  coordinate_frame TEXT NOT NULL DEFAULT 'LOCAL_MM'," +
                "  UNIQUE(building_type, storey, room_name)" +
                ")"
            );
        }
    }

    private static void seedTypology(Connection conn) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT OR IGNORE INTO ad_typology_template " +
                "(typology_id, typology_name, grid_strategy, ubbl_class, " +
                " base_width_mm, base_depth_mm, zone_json, is_active) " +
                "VALUES (?,?,?,?,?,?,?,1)")) {
            stmt.setString(1, "TERRACE_TEST");
            stmt.setString(2, "Test Terrace");
            stmt.setString(3, "STRIP_ZONES");
            stmt.setString(4, "RESIDENTIAL_TYPE_C");
            stmt.setInt(5, 9900);
            stmt.setInt(6, 8500);
            stmt.setString(7, "[{\"zone\":\"BED_R1\",\"room_type\":\"BEDROOM\"}]");
            stmt.executeUpdate();
        }
    }
}
