package com.bim.designer;

import com.bim.backoffice.dao.CostDAO;
import com.bim.backoffice.dao.ScheduleDAO;

import org.junit.jupiter.api.*;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.*;

/**
 * nD backend test on real compiled building — SH_BOM.db + component_library.db.
 *
 * <p>Proves the 4D/5D DAOs work against actual extraction data, not just
 * test fixtures. Pure SQLite — no Bonsai, no compilation, no IFC.
 *
 * // Implementing TestArchitecture.md §Backend-First Testing — Witness: W-ND-REAL-*
 */
@DisplayName("nD on real building — SH_BOM.db + component_library.db")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RealBuildingNDTest {

    private static final String SH_BOM_DB = "library/SH_BOM.db";
    private static final String COMP_LIB = "library/component_library.db";

    private static Connection bomConn;
    private static Connection compLibConn;

    @BeforeAll
    static void setUp() throws Exception {
        assertTrue(new File(SH_BOM_DB).exists(), "SH_BOM.db not found");
        assertTrue(new File(COMP_LIB).exists(), "component_library.db not found");

        bomConn = DriverManager.getConnection("jdbc:sqlite:" + SH_BOM_DB);
        compLibConn = DriverManager.getConnection("jdbc:sqlite:" + COMP_LIB);
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (bomConn != null) bomConn.close();
        if (compLibConn != null) compLibConn.close();
    }

    // ── W-ND-REAL-1: 4D Schedule on real SH BOM ──────────────────────

    @Test @Order(1)
    @DisplayName("W-ND-REAL-1: 4D schedule from SH_BOM.db")
    void w_nd_real_1_schedule() throws Exception {
        // Check if component_library has 4D columns (migration may not be applied yet)
        boolean has4D = hasColumn(compLibConn, "M_Product", "construction_phase");
        if (!has4D) {
            System.out.println("  4D: construction_phase column not in component_library — needs migration");
            return;
        }

        var schedule = new ScheduleDAO().constructionSchedule(
                bomConn, compLibConn, "SH", "2026-04-01");

        assertNotNull(schedule, "Schedule must be produced");
        System.out.printf("  4D: %d tasks, %d days, %s→%s%n",
                schedule.totalTasks(), schedule.totalDays(),
                schedule.projectStartDate(), schedule.projectFinishDate());

        if (schedule.totalTasks() > 0) {
            schedule.tasksByPhase().forEach((phase, count) ->
                    System.out.printf("    %s: %d tasks%n", phase, count));
        }
    }

    // ── W-ND-REAL-2: 5D Cost on real SH BOM ──────────────────────────

    @Test @Order(2)
    @DisplayName("W-ND-REAL-2: 5D cost breakdown from SH_BOM.db")
    void w_nd_real_2_cost() throws Exception {
        boolean has5D = hasColumn(compLibConn, "M_Product", "unit_cost_rm");
        if (!has5D) {
            System.out.println("  5D: unit_cost_rm column not in component_library — needs migration");
            return;
        }

        var cost = new CostDAO().costBreakdown(bomConn, compLibConn, "SH");

        assertNotNull(cost, "Cost summary must be produced");
        System.out.printf("  5D: grand=%.2f RM, mat=%.2f, lab=%.2f, eq=%.2f%n",
                cost.grandTotal(), cost.materialTotal(),
                cost.laborTotal(), cost.equipmentTotal());

        assertTrue(cost.grandTotal() >= 0, "Grand total must be non-negative");
    }

    private boolean hasColumn(Connection conn, String table, String column) {
        try (var rs = conn.getMetaData().getColumns(null, null, table, column)) {
            return rs.next();
        } catch (Exception e) {
            return false;
        }
    }

    // ── W-ND-REAL-3: BOM element count matches Rosetta Stone ──────────

    @Test @Order(3)
    @DisplayName("W-ND-REAL-3: SH BOM has 58 leaf elements (G1-COUNT via library)")
    void w_nd_real_3_element_count() throws Exception {
        try (var stmt = bomConn.createStatement();
             var rs = stmt.executeQuery("""
                     SELECT SUM(qty) FROM m_bom_line
                     WHERE component_type = 'LEAF' AND is_active = 1""")) {
            assertTrue(rs.next());
            int leafInstances = rs.getInt(1);
            // SH has 58 elements via component_library.db (55 extraction + 3 library-evolved)
            assertTrue(leafInstances >= 58,
                    "SH BOM must have >= 58 leaf instances, got " + leafInstances);
            System.out.printf("  BOM: %d leaf instances%n", leafInstances);
        }
    }

    // ── W-ND-REAL-4: Output DB has compiled elements ──────────────────

    @Test @Order(4)
    @DisplayName("W-ND-REAL-4: Output DB (ifc4_samplehouse.db) has 58 elements")
    void w_nd_real_4_output_db() throws Exception {
        File outputDb = new File("DAGCompiler/lib/output/ifc4_samplehouse.db");
        if (!outputDb.exists()) {
            System.out.println("  Output DB not found — skipping (run pipeline first)");
            return;
        }

        try (var conn = DriverManager.getConnection("jdbc:sqlite:" + outputDb);
             var stmt = conn.createStatement();
             var rs = stmt.executeQuery("SELECT COUNT(*) FROM elements_meta")) {
            assertTrue(rs.next());
            assertEquals(58, rs.getInt(1), "Output must have 58 elements");
            System.out.println("  Output: 58 elements confirmed");
        }

        // Check c_orderline table (populated for generative builds, empty for extracted)
        try (var conn = DriverManager.getConnection("jdbc:sqlite:" + outputDb);
             var stmt = conn.createStatement();
             var rs = stmt.executeQuery("SELECT COUNT(*) FROM c_orderline")) {
            assertTrue(rs.next());
            int orderLines = rs.getInt(1);
            System.out.printf("  Output: %d C_OrderLine rows (0 = extracted, >0 = generative)%n",
                    orderLines);
        }
    }
}
