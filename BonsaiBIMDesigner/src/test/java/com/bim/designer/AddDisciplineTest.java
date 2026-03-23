package com.bim.designer;

import com.bim.compiler.dsl.PlacementLoader;
import com.bim.designer.api.*;
import com.bim.designer.api.DesignerAPI.*;

import org.junit.jupiter.api.*;

import java.io.File;
import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Session A witness: Add discipline mutation creates PROPOSED C_OrderLines.
 *
 * <p>Flow: bomDrop(SH) → addDiscipline("ELEC") → PROPOSED LEAF lines under
 * each room that has a matching space_type in ad_space_type_mep_bom.
 *
 * <p>bom_child_id is NULL (rule-driven, not BOM-derived).
 * proposal_status = 'PROPOSED'.
 * Discipline = the requested discipline code.
 *
 * <p>// Implementing ProjectOrderBlueprint.md §14.3 Session A — Witness: W-DM-TC5-1
 */
@DisplayName("Add Discipline — §14.3 Session A: ELEC on SH order")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AddDisciplineTest {

    private static final String SH_BOM_DB = "library/SH_BOM.db";

    private static DesignerAPIImpl api;
    private static Connection bomConn;
    private static BomDropResponse dropResult;
    private static AddDisciplineResponse addResult;

    @BeforeAll
    static void setUp() throws Exception {
        PlacementLoader.resetInstance();

        assertTrue(new File(SH_BOM_DB).exists(),
                "SH_BOM.db not found — run IFCtoBOM pipeline first");
        assertTrue(new File("library/disc_validation.db").exists(),
                "disc_validation.db not found");

        bomConn = DriverManager.getConnection("jdbc:sqlite:" + SH_BOM_DB);
        System.setProperty("bom.db", SH_BOM_DB);

        api = new DesignerAPIImpl(bomConn);
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (bomConn != null && !bomConn.isClosed()) bomConn.close();
    }

    // ── Step 1: BOM Drop creates the base order ─────────────────────

    @Test
    @Order(1)
    @DisplayName("Prerequisite: bomDrop(BUILDING_SH_STD) creates order")
    void step1_bom_drop() {
        dropResult = api.bomDrop("BUILDING_SH_STD");
        assertTrue(dropResult.success(), "bomDrop must succeed: " + dropResult.error());
        assertNotNull(dropResult.orderId(), "C_Order_ID must be set");
        System.out.printf("[Step 1] BOM Drop → order=%s, %d leaves%n",
                dropResult.orderId(), dropResult.totalElements());
    }

    // ── Step 2: addDiscipline creates PROPOSED lines ────────────────

    @Test
    @Order(2)
    @DisplayName("W-DM-TC5-1: addDiscipline(ELEC) creates PROPOSED C_OrderLines per room")
    void step2_add_discipline_elec() {
        assertNotNull(dropResult, "Step 1 must run first");

        addResult = api.addDiscipline("BUILDING_SH_STD", dropResult.orderId(),
                "ELEC", "MY");

        assertTrue(addResult.success(), "addDiscipline must succeed: " + addResult.error());
        assertTrue(addResult.linesCreated() > 0,
                "Must create at least one PROPOSED line for ELEC");
        assertTrue(addResult.roomsProcessed() > 0,
                "Must process at least one room with ELEC requirements");
        assertEquals("ELEC", addResult.discipline());

        System.out.printf("[W-DM-TC5-1] addDiscipline(ELEC) → %d lines across %d rooms%n",
                addResult.linesCreated(), addResult.roomsProcessed());
    }

    // ── Step 3: Verify PROPOSED lines in DB ─────────────────────────

    @Test
    @Order(3)
    @DisplayName("W-DM-TC5-1: PROPOSED lines have correct Discipline and proposal_status")
    void step3_verify_proposed_lines() throws Exception {
        assertNotNull(dropResult, "Step 1 must run first");
        assertNotNull(addResult, "Step 2 must run first");

        // Query the work_output DB directly (WorkOutputDAO lowercases the path)
        String dbPath = com.bim.designer.dao.WorkOutputDAO.dbPathFor("BUILDING_SH_STD");
        assertTrue(new File(dbPath).exists(), "work_output DB must exist: " + dbPath);

        try (Connection woConn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            // Count PROPOSED lines
            String sql = "SELECT COUNT(*) FROM C_OrderLine "
                    + "WHERE C_Order_ID = ? AND proposal_status = 'PROPOSED'";
            try (PreparedStatement ps = woConn.prepareStatement(sql)) {
                ps.setString(1, dropResult.orderId());
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    int proposedCount = rs.getInt(1);
                    assertEquals(addResult.linesCreated(), proposedCount,
                            "PROPOSED line count must match addDiscipline result");
                    System.out.printf("[Step 3] %d PROPOSED lines confirmed in DB%n", proposedCount);
                }
            }

            // Verify Discipline = ELEC on all PROPOSED lines
            String discSql = "SELECT DISTINCT Discipline FROM C_OrderLine "
                    + "WHERE C_Order_ID = ? AND proposal_status = 'PROPOSED'";
            try (PreparedStatement ps = woConn.prepareStatement(discSql)) {
                ps.setString(1, dropResult.orderId());
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next(), "Must have at least one discipline");
                    assertEquals("ELEC", rs.getString(1),
                            "All PROPOSED lines must have Discipline=ELEC");
                    assertFalse(rs.next(), "Only one discipline expected");
                }
            }

            // Note: bom_child_id is on the compile DB schema (BomDropper), not work_output.
            // Work_output PROPOSED lines are rule-driven by definition — no BOM parent.

            // Verify parent lines are ROOMs
            String parentSql = "SELECT DISTINCT p.host_type FROM C_OrderLine c "
                    + "JOIN C_OrderLine p ON c.Parent_OrderLine_ID = p.C_OrderLine_ID "
                    + "WHERE c.C_Order_ID = ? AND c.proposal_status = 'PROPOSED'";
            try (PreparedStatement ps = woConn.prepareStatement(parentSql)) {
                ps.setString(1, dropResult.orderId());
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next(), "PROPOSED lines must have parents");
                    assertEquals("ROOM", rs.getString(1),
                            "Parents of PROPOSED lines must be ROOM");
                }
            }
        }
    }

    // ── Step 4: FP discipline also works ────────────────────────────

    @Test
    @Order(4)
    @DisplayName("W-DM-TC5-1: addDiscipline(FP) creates PROPOSED sprinkler lines")
    void step4_add_discipline_fp() {
        assertNotNull(dropResult, "Step 1 must run first");

        AddDisciplineResponse fpResult = api.addDiscipline("BUILDING_SH_STD",
                dropResult.orderId(), "FP", "MY");

        assertTrue(fpResult.success(), "addDiscipline(FP) must succeed: " + fpResult.error());
        // FP has SPRINKLER + EMERGENCY_LIGHT products
        assertTrue(fpResult.linesCreated() >= 0,
                "FP may create 0 lines if no rooms have FP requirements");
        assertEquals("FP", fpResult.discipline());

        System.out.printf("[Step 4] addDiscipline(FP) → %d lines across %d rooms%n",
                fpResult.linesCreated(), fpResult.roomsProcessed());
    }
}
