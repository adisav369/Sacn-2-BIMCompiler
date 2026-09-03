package com.bim.designer;

import com.bim.designer.dao.MEPBOMQuery;
import com.bim.designer.dao.MEPBOMQuery.MEPRequirement;

import org.junit.jupiter.api.*;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MEP BOM Query — data-driven product requirements from ERP.db.
 *
 * <p>Implements BIM_Designer.md §18.8 (Click-to-Place data layer).
 * "Clicking defines the context, rules define the content."
 *
 * <p>Witness claims:
 * <ul>
 *   <li>W-CTP-MEP-1: BATHROOM + FP returns SPRINKLER requirement</li>
 *   <li>W-CTP-MEP-2: BEDROOM + ELEC returns LIGHT, OUTLET, SWITCH</li>
 *   <li>W-CTP-MEP-3: discipline product mapping covers FP/ELEC/SP/ACMV</li>
 *   <li>W-CTP-MEP-4: ARC discipline returns empty (no MEP products)</li>
 *   <li>W-CTP-MEP-5: requirements carry placement_rule and host_surface</li>
 *   <li>W-CTP-MEP-6: requirements carry building_code provenance</li>
 * </ul>
 */
@DisplayName("MEP BOM Query — Click-to-Place Data Layer (§18.8)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MEPBOMQueryTest {

    private static Connection conn;
    private static MEPBOMQuery query;
    private static boolean dbAvailable;

    @BeforeAll
    static void setUp() throws Exception {
        String dbPath = "library/ERP.db";
        dbAvailable = new File(dbPath).exists();
        if (!dbAvailable) {
            fail("ERP.db required — MEPBOMQueryTest cannot run without it");
        }

        conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        query = new MEPBOMQuery(conn);
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (conn != null && !conn.isClosed()) conn.close();
    }

    // ── W-CTP-MEP-1: BATHROOM + FP → SPRINKLER ─────────────────────

    @Test
    @Order(1)
    @DisplayName("W-CTP-MEP-1: BATHROOM + FP returns SPRINKLER requirement")
    // Implementing BIM_Designer.md §18.8 — Witness: W-CTP-MEP-1
    void w_ctp_mep_1_bathroom_fp() throws Exception {

        List<MEPRequirement> reqs = query.queryForDiscipline("BATHROOM", "FP");

        assertFalse(reqs.isEmpty(), "BATHROOM + FP must have requirements");
        assertTrue(reqs.stream().anyMatch(r -> "SPRINKLER".equals(r.mepProductId())),
                "Must include SPRINKLER: " + reqs);
    }

    // ── W-CTP-MEP-2: BEDROOM + ELEC → LIGHT, OUTLET, SWITCH ────────

    @Test
    @Order(2)
    @DisplayName("W-CTP-MEP-2: BEDROOM + ELEC returns LIGHT, OUTLET, SWITCH")
    // Implementing BIM_Designer.md §18.8 — Witness: W-CTP-MEP-2
    void w_ctp_mep_2_bedroom_elec() throws Exception {

        List<MEPRequirement> reqs = query.queryForDiscipline("BEDROOM", "ELEC");

        assertFalse(reqs.isEmpty(), "BEDROOM + ELEC must have requirements");
        assertTrue(reqs.stream().anyMatch(r -> "LIGHT".equals(r.mepProductId())),
                "Must include LIGHT");
        assertTrue(reqs.stream().anyMatch(r -> "OUTLET".equals(r.mepProductId())),
                "Must include OUTLET");
        assertTrue(reqs.stream().anyMatch(r -> "SWITCH".equals(r.mepProductId())),
                "Must include SWITCH");
    }

    // ── W-CTP-MEP-3: all 4 MEP disciplines have products ───────────

    @Test
    @Order(3)
    @DisplayName("W-CTP-MEP-3: discipline mapping covers FP/ELEC/SP/ACMV")
    // Implementing BIM_Designer.md §18.8 — Witness: W-CTP-MEP-3
    void w_ctp_mep_3_all_disciplines() {
        assertFalse(MEPBOMQuery.disciplineProducts("FP").isEmpty(), "FP has products");
        assertFalse(MEPBOMQuery.disciplineProducts("ELEC").isEmpty(), "ELEC has products");
        assertFalse(MEPBOMQuery.disciplineProducts("SP").isEmpty(), "SP has products");
        assertFalse(MEPBOMQuery.disciplineProducts("ACMV").isEmpty(), "ACMV has products");
    }

    // ── W-CTP-MEP-4: ARC returns empty ──────────────────────────────

    @Test
    @Order(4)
    @DisplayName("W-CTP-MEP-4: ARC discipline returns empty (no MEP products)")
    // Implementing BIM_Designer.md §18.8 — Witness: W-CTP-MEP-4
    void w_ctp_mep_4_arc_empty() {
        assertTrue(MEPBOMQuery.disciplineProducts("ARC").isEmpty(),
                "ARC should not have MEP products");
    }

    // ── W-CTP-MEP-5: placement_rule and host_surface populated ──────

    @Test
    @Order(5)
    @DisplayName("W-CTP-MEP-5: requirements carry placement_rule and host_surface")
    // Implementing BIM_Designer.md §18.8 — Witness: W-CTP-MEP-5
    void w_ctp_mep_5_placement_metadata() throws Exception {

        List<MEPRequirement> reqs = query.queryForDiscipline("BATHROOM", "ELEC");
        assertFalse(reqs.isEmpty(), "BATHROOM + ELEC must have requirements");

        for (MEPRequirement r : reqs) {
            assertNotNull(r.placementRule(), "placementRule must not be null for " + r.mepProductId());
            assertNotNull(r.hostSurface(), "hostSurface must not be null for " + r.mepProductId());
        }
    }

    // ── W-CTP-MEP-6: building_code provenance ───────────────────────

    @Test
    @Order(6)
    @DisplayName("W-CTP-MEP-6: requirements carry building_code provenance")
    // Implementing BIM_Designer.md §18.8 — Witness: W-CTP-MEP-6
    void w_ctp_mep_6_building_code() throws Exception {

        List<MEPRequirement> reqs = query.queryForDiscipline("BEDROOM", "FP");
        assertFalse(reqs.isEmpty(), "BEDROOM + FP must have requirements");

        MEPRequirement sprinkler = reqs.stream()
                .filter(r -> "SPRINKLER".equals(r.mepProductId()))
                .findFirst().orElseThrow(() -> new AssertionError("SPRINKLER not found"));

        assertNotNull(sprinkler.buildingCode(), "buildingCode must be populated");
        assertNotNull(sprinkler.codeClause(), "codeClause must be populated");
        // NFPA 13 is the expected reference for sprinklers
        assertTrue(sprinkler.buildingCode().contains("NFPA"),
                "Sprinkler buildingCode should reference NFPA: " + sprinkler.buildingCode());
    }
}
