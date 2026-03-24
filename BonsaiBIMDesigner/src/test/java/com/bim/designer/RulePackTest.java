package com.bim.designer;

import com.bim.compiler.dsl.PlacementLoader;
import com.bim.designer.api.*;
import com.bim.designer.api.DesignerAPI.*;

import org.junit.jupiter.api.*;

import java.io.File;
import java.sql.*;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Session C witness: W-RULEPACK-1 — jurisdiction-specific rule packs.
 *
 * <p>Demonstrates that MY jurisdiction loads UBBL-2024 rules and US jurisdiction
 * loads IBC-2021 rules, producing different proposal sets from the same building.
 *
 * <p>// Implementing ProjectOrderBlueprint.md §14.3 Session C — Witness: W-RULEPACK-1
 */
@DisplayName("RulePack — §14.3 Session C: Jurisdiction Rule Packs")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RulePackTest {

    private static final String SH_BOM_DB = "library/SH_BOM.db";

    private static DesignerAPIImpl api;
    private static Connection bomConn;
    private static BomDropResponse dropResult;

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

    // ── Step 2: W-RULEPACK-1 — MY packs produce UBBL-specific proposals ──

    @Test
    @Order(2)
    @DisplayName("W-RULEPACK-1: MY jurisdiction loads UBBL-2024 + BASE + NFPA-13 packs")
    void step2_my_jurisdiction() throws Exception {
        assertNotNull(dropResult, "Step 1 must run first");

        List<String> myPacks = OrderLineMutation.packsForJurisdiction("MY");
        assertEquals(List.of("BASE", "UBBL-2024", "NFPA-13"), myPacks,
                "MY must resolve to BASE + UBBL-2024 + NFPA-13");

        String dbPath = com.bim.designer.dao.WorkOutputDAO.dbPathFor("BUILDING_SH_STD");
        try (Connection woConn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Connection dvConn = DriverManager.getConnection("jdbc:sqlite:library/disc_validation.db")) {

            OrderMutationService service = new OrderMutationService();
            List<ProposedOrderLine> myProposals = service.proposeAll(woConn, dvConn,
                    dropResult.orderId(), myPacks);

            assertFalse(myProposals.isEmpty(), "MY must produce proposals");

            // MY packs include UBBL-2024 (MS1525 = Malaysian ACMV standard)
            // Verify we get ACMV proposals (MS1525 is in UBBL-2024 pack)
            long acmvCount = myProposals.stream()
                    .filter(p -> "ACMV".equals(p.discipline())).count();
            assertTrue(acmvCount > 0, "MY must have ACMV proposals from UBBL-2024 pack");

            System.out.printf("[W-RULEPACK-1] MY packs=%s → %d proposals (ACMV=%d)%n",
                    myPacks, myProposals.size(), acmvCount);
        }
    }

    // ── Step 3: US packs produce IBC-specific proposals ──────────────

    @Test
    @Order(3)
    @DisplayName("W-RULEPACK-1: US jurisdiction loads IBC-2021 + BASE + NFPA-13 packs")
    void step3_us_jurisdiction() throws Exception {
        assertNotNull(dropResult, "Step 1 must run first");

        List<String> usPacks = OrderLineMutation.packsForJurisdiction("US");
        assertEquals(List.of("BASE", "IBC-2021", "NFPA-13"), usPacks,
                "US must resolve to BASE + IBC-2021 + NFPA-13");

        String dbPath = com.bim.designer.dao.WorkOutputDAO.dbPathFor("BUILDING_SH_STD");
        try (Connection woConn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Connection dvConn = DriverManager.getConnection("jdbc:sqlite:library/disc_validation.db")) {

            OrderMutationService service = new OrderMutationService();
            List<ProposedOrderLine> usProposals = service.proposeAll(woConn, dvConn,
                    dropResult.orderId(), usPacks);

            assertFalse(usProposals.isEmpty(), "US must produce proposals");

            // US packs include IBC-2021 (NEC 2020 = US electrical standard)
            // Verify we get ELEC proposals from IBC-2021 pack
            long elecCount = usProposals.stream()
                    .filter(p -> "ELEC".equals(p.discipline())).count();
            assertTrue(elecCount > 0, "US must have ELEC proposals from IBC-2021 pack");

            System.out.printf("[W-RULEPACK-1] US packs=%s → %d proposals (ELEC=%d)%n",
                    usPacks, usProposals.size(), elecCount);
        }
    }

    // ── Step 4: MY and US produce DIFFERENT proposal counts ──────────

    @Test
    @Order(4)
    @DisplayName("W-RULEPACK-1: MY vs US produce different proposal counts (jurisdiction matters)")
    void step4_my_vs_us_different() throws Exception {
        assertNotNull(dropResult, "Step 1 must run first");

        String dbPath = com.bim.designer.dao.WorkOutputDAO.dbPathFor("BUILDING_SH_STD");
        try (Connection woConn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Connection dvConn = DriverManager.getConnection("jdbc:sqlite:library/disc_validation.db")) {

            OrderMutationService service = new OrderMutationService();

            List<ProposedOrderLine> myProposals = service.proposeAll(woConn, dvConn,
                    dropResult.orderId(), OrderLineMutation.packsForJurisdiction("MY"));
            List<ProposedOrderLine> usProposals = service.proposeAll(woConn, dvConn,
                    dropResult.orderId(), OrderLineMutation.packsForJurisdiction("US"));

            // Both must produce proposals, but counts differ because
            // UBBL-2024 has 34 rows vs IBC-2021 has 45 rows in ad_space_type_mep_bom
            assertNotEquals(myProposals.size(), usProposals.size(),
                    "MY and US must produce different proposal counts — jurisdiction matters");

            // Collect building codes referenced
            Set<String> myCodes = myProposals.stream()
                    .map(ProposedOrderLine::buildingCode)
                    .filter(c -> c != null && !c.isEmpty())
                    .collect(Collectors.toSet());
            Set<String> usCodes = usProposals.stream()
                    .map(ProposedOrderLine::buildingCode)
                    .filter(c -> c != null && !c.isEmpty())
                    .collect(Collectors.toSet());

            assertNotEquals(myCodes, usCodes,
                    "MY and US must reference different building codes");

            System.out.printf("[W-RULEPACK-1] MY=%d proposals codes=%s | US=%d proposals codes=%s%n",
                    myProposals.size(), myCodes, usProposals.size(), usCodes);
        }
    }

    // ── Step 5: No-filter (empty packs) returns ALL rules ────────────

    @Test
    @Order(5)
    @DisplayName("Empty packIds returns all rules (backward compatible)")
    void step5_no_filter_returns_all() throws Exception {
        assertNotNull(dropResult, "Step 1 must run first");

        String dbPath = com.bim.designer.dao.WorkOutputDAO.dbPathFor("BUILDING_SH_STD");
        try (Connection woConn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Connection dvConn = DriverManager.getConnection("jdbc:sqlite:library/disc_validation.db")) {

            OrderMutationService service = new OrderMutationService();

            List<ProposedOrderLine> allProposals = service.proposeAll(woConn, dvConn,
                    dropResult.orderId(), List.of());
            List<ProposedOrderLine> myProposals = service.proposeAll(woConn, dvConn,
                    dropResult.orderId(), OrderLineMutation.packsForJurisdiction("MY"));
            List<ProposedOrderLine> usProposals = service.proposeAll(woConn, dvConn,
                    dropResult.orderId(), OrderLineMutation.packsForJurisdiction("US"));

            // No filter must return >= any filtered set
            assertTrue(allProposals.size() >= myProposals.size(),
                    "Unfiltered must return >= MY filtered");
            assertTrue(allProposals.size() >= usProposals.size(),
                    "Unfiltered must return >= US filtered");

            System.out.printf("[Step 5] All=%d, MY=%d, US=%d%n",
                    allProposals.size(), myProposals.size(), usProposals.size());
        }
    }

    // ── Step 6: addDiscipline with jurisdiction flows pack context ────

    @Test
    @Order(6)
    @DisplayName("addDiscipline(ELEC, MY) uses UBBL-2024 packs")
    void step6_add_discipline_with_jurisdiction() {
        BomDropResponse freshDrop = api.bomDrop("BUILDING_SH_STD");
        assertTrue(freshDrop.success());

        AddDisciplineResponse myResult = api.addDiscipline("BUILDING_SH_STD",
                freshDrop.orderId(), "ELEC", "MY");
        assertTrue(myResult.success(), "addDiscipline ELEC/MY must succeed");
        assertTrue(myResult.linesCreated() > 0, "Must create PROPOSED lines");

        System.out.printf("[Step 6] addDiscipline(ELEC, MY) → %d lines%n",
                myResult.linesCreated());
    }
}
