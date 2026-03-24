package com.bim.designer;

import com.bim.compiler.dsl.PlacementLoader;
import com.bim.designer.api.*;
import com.bim.designer.api.DesignerAPI.*;

import org.junit.jupiter.api.*;

import java.io.File;
import java.sql.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Session B witness: OrderLineMutation interface — validation-as-suggestion engine.
 *
 * <p>W-DM-FP-VAL-1: validation fires whether or not FP present.
 * Tests the three OrderLineMutation implementations (ELEC, FP, ACMV)
 * via both the interface directly and the OrderMutationService delegation path.
 *
 * <p>// Implementing ProjectOrderBlueprint.md §14.3 Session B — Witness: W-DM-FP-VAL-1
 */
@DisplayName("OrderLineMutation — §14.3 Session B: Suggestion Engine")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OrderLineMutationTest {

    private static final String SH_BOM_DB = "library/SH_BOM.db";

    private static DesignerAPIImpl api;
    private static Connection bomConn;
    private static BomDropResponse dropResult;

    @BeforeAll
    static void setUp() throws Exception {
        PlacementLoader.resetInstance();

        assertTrue(new File(SH_BOM_DB).exists(),
                "SH_BOM.db not found — run IFCtoBOM pipeline first");
        assertTrue(new File("library/ERP.db").exists(),
                "ERP.db not found");

        bomConn = DriverManager.getConnection("jdbc:sqlite:" + SH_BOM_DB);
        System.setProperty("bom.db", SH_BOM_DB);

        api = new DesignerAPIImpl(bomConn);
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (bomConn != null && !bomConn.isClosed()) bomConn.close();
    }

    // ── Step 1: BOM Drop creates base order ──────────────────────────

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

    // ── Step 2: OrderLineMutation interface — propose without persist ─

    @Test
    @Order(2)
    @DisplayName("W-DM-FP-VAL-1: FPSuggestion.propose() returns proposals for rooms with FP rules")
    void step2_fp_propose() throws Exception {
        assertNotNull(dropResult, "Step 1 must run first");

        String dbPath = com.bim.designer.dao.WorkOutputDAO.dbPathFor("BUILDING_SH_STD");
        try (Connection woConn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Connection dvConn = DriverManager.getConnection("jdbc:sqlite:library/ERP.db")) {

            FPSuggestion fp = new FPSuggestion();
            assertEquals("FP", fp.discipline());

            List<ProposedOrderLine> proposals = fp.propose(woConn, dvConn, dropResult.orderId());

            // FP must propose at least one line (SH has rooms with FP rules)
            assertFalse(proposals.isEmpty(),
                    "FPSuggestion must propose at least one line for SH rooms");
            for (ProposedOrderLine p : proposals) {
                assertEquals("FP", p.discipline(), "All proposals must be FP discipline");
                assertTrue(p.qty() > 0, "Qty must be positive");
                assertNotNull(p.mepProductId(), "Product must not be null");
            }

            System.out.printf("[W-DM-FP-VAL-1] FPSuggestion.propose() → %d proposals%n",
                    proposals.size());
        }
    }

    // ── Step 3: ELECSuggestion interface ─────────────────────────────

    @Test
    @Order(3)
    @DisplayName("ELECSuggestion.propose() returns proposals for rooms with ELEC rules")
    void step3_elec_propose() throws Exception {
        assertNotNull(dropResult, "Step 1 must run first");

        String dbPath = com.bim.designer.dao.WorkOutputDAO.dbPathFor("BUILDING_SH_STD");
        try (Connection woConn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Connection dvConn = DriverManager.getConnection("jdbc:sqlite:library/ERP.db")) {

            ELECSuggestion elec = new ELECSuggestion();
            assertEquals("ELEC", elec.discipline());

            List<ProposedOrderLine> proposals = elec.propose(woConn, dvConn, dropResult.orderId());

            assertFalse(proposals.isEmpty(),
                    "ELECSuggestion must propose at least one line for SH rooms");
            for (ProposedOrderLine p : proposals) {
                assertEquals("ELEC", p.discipline());
                assertTrue(p.qty() > 0);
            }

            System.out.printf("[Step 3] ELECSuggestion.propose() → %d proposals%n",
                    proposals.size());
        }
    }

    // ── Step 4: ACMVSuggestion interface ─────────────────────────────

    @Test
    @Order(4)
    @DisplayName("ACMVSuggestion.propose() returns proposals for rooms with ACMV rules")
    void step4_acmv_propose() throws Exception {
        assertNotNull(dropResult, "Step 1 must run first");

        String dbPath = com.bim.designer.dao.WorkOutputDAO.dbPathFor("BUILDING_SH_STD");
        try (Connection woConn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Connection dvConn = DriverManager.getConnection("jdbc:sqlite:library/ERP.db")) {

            ACMVSuggestion acmv = new ACMVSuggestion();
            assertEquals("ACMV", acmv.discipline());

            List<ProposedOrderLine> proposals = acmv.propose(woConn, dvConn, dropResult.orderId());

            assertFalse(proposals.isEmpty(),
                    "ACMVSuggestion must propose at least one line for SH rooms");
            for (ProposedOrderLine p : proposals) {
                assertEquals("ACMV", p.discipline());
                assertTrue(p.qty() > 0);
            }

            System.out.printf("[Step 4] ACMVSuggestion.propose() → %d proposals%n",
                    proposals.size());
        }
    }

    // ── Step 5: Delegation path — addDiscipline still works via interface ─

    @Test
    @Order(5)
    @DisplayName("addDiscipline(ELEC) delegates to ELECSuggestion via OrderMutationService")
    void step5_delegation_backward_compat() {
        assertNotNull(dropResult, "Step 1 must run first");

        // Re-drop to get clean order (previous tests may have added proposals)
        BomDropResponse freshDrop = api.bomDrop("BUILDING_SH_STD");
        assertTrue(freshDrop.success());

        AddDisciplineResponse result = api.addDiscipline("BUILDING_SH_STD",
                freshDrop.orderId(), "ELEC", "MY");

        assertTrue(result.success(), "addDiscipline must succeed: " + result.error());
        assertTrue(result.linesCreated() > 0, "Must create PROPOSED lines");
        assertEquals("ELEC", result.discipline());

        System.out.printf("[Step 5] addDiscipline(ELEC) via interface → %d lines, %d rooms%n",
                result.linesCreated(), result.roomsProcessed());
    }

    // ── Step 6: W-DM-FP-VAL-1 — FP validation fires whether or not FP present ─

    @Test
    @Order(6)
    @DisplayName("W-DM-FP-VAL-1: FP suggestion fires on order WITHOUT existing FP lines")
    void step6_fp_fires_without_existing_fp() throws Exception {
        // Fresh drop — no FP lines exist yet
        BomDropResponse freshDrop = api.bomDrop("BUILDING_SH_STD");
        assertTrue(freshDrop.success());

        String dbPath = com.bim.designer.dao.WorkOutputDAO.dbPathFor("BUILDING_SH_STD");
        try (Connection woConn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Connection dvConn = DriverManager.getConnection("jdbc:sqlite:library/ERP.db")) {

            // Verify: no FP lines exist
            try (PreparedStatement ps = woConn.prepareStatement(
                    "SELECT COUNT(*) FROM C_OrderLine WHERE C_Order_ID = ? AND Discipline = 'FP'")) {
                ps.setString(1, freshDrop.orderId());
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(0, rs.getInt(1), "No FP lines should exist before propose");
                }
            }

            // FPSuggestion still fires — proposes lines for the FP-absent order
            FPSuggestion fp = new FPSuggestion();
            List<ProposedOrderLine> proposals = fp.propose(woConn, dvConn, freshDrop.orderId());
            assertFalse(proposals.isEmpty(),
                    "W-DM-FP-VAL-1: FP suggestion must fire even when FP is absent from order");

            System.out.printf("[W-DM-FP-VAL-1] FP absent → %d proposals generated (suggestion fires)%n",
                    proposals.size());
        }
    }

    // ── Step 7: proposeAll returns proposals for all disciplines ──────

    @Test
    @Order(7)
    @DisplayName("proposeAll() returns proposals for FP + ELEC + ACMV")
    void step7_propose_all() throws Exception {
        BomDropResponse freshDrop = api.bomDrop("BUILDING_SH_STD");
        assertTrue(freshDrop.success());

        String dbPath = com.bim.designer.dao.WorkOutputDAO.dbPathFor("BUILDING_SH_STD");
        try (Connection woConn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Connection dvConn = DriverManager.getConnection("jdbc:sqlite:library/ERP.db")) {

            OrderMutationService service = new OrderMutationService();
            List<ProposedOrderLine> all = service.proposeAll(woConn, dvConn, freshDrop.orderId());

            assertFalse(all.isEmpty(), "proposeAll must return at least one proposal");

            long fpCount = all.stream().filter(p -> "FP".equals(p.discipline())).count();
            long elecCount = all.stream().filter(p -> "ELEC".equals(p.discipline())).count();
            long acmvCount = all.stream().filter(p -> "ACMV".equals(p.discipline())).count();

            assertTrue(fpCount > 0, "Must have FP proposals");
            assertTrue(elecCount > 0, "Must have ELEC proposals");
            assertTrue(acmvCount > 0, "Must have ACMV proposals");

            System.out.printf("[Step 7] proposeAll() → FP=%d, ELEC=%d, ACMV=%d (total=%d)%n",
                    fpCount, elecCount, acmvCount, all.size());
        }
    }

    // ── Step 8: getMutation returns correct implementation ───────────

    @Test
    @Order(8)
    @DisplayName("getMutation() returns correct OrderLineMutation per discipline")
    void step8_get_mutation() {
        assertInstanceOf(ELECSuggestion.class, OrderMutationService.getMutation("ELEC"));
        assertInstanceOf(FPSuggestion.class, OrderMutationService.getMutation("FP"));
        assertInstanceOf(ACMVSuggestion.class, OrderMutationService.getMutation("ACMV"));
        assertNull(OrderMutationService.getMutation("UNKNOWN"));

        System.out.println("[Step 8] getMutation() registry verified for ELEC, FP, ACMV");
    }
}
