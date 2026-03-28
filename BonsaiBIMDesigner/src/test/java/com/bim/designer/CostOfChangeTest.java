package com.bim.designer;

import com.bim.designer.api.DesignerAPI.CostOfChangeResponse;
import com.bim.designer.api.DesignerAPIImpl;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.*;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Wire witness for costOfChange() — DesignerAPIImpl → CostDAO.
 *
 * <p>Proves the Designer API returns real cost data from SH_BOM.db + ERP.db
 * via the CostDAO wiring (no stubs).
 *
 * // Implementing BIM_Designer_SRS.md §26.12.3 — Witness: W-WF-COST-1
 */
@DisplayName("costOfChange wiring — W-WF-COST-1")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CostOfChangeTest {

    private static final String SH_BOM_DB = "library/SH_BOM.db";

    private static Connection bomConn;
    private static DesignerAPIImpl api;

    @BeforeAll
    static void setUp() throws Exception {
        assertTrue(new File(SH_BOM_DB).exists(), "SH_BOM.db not found");

        bomConn = DriverManager.getConnection("jdbc:sqlite:" + SH_BOM_DB);
        api = new DesignerAPIImpl(bomConn);
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (bomConn != null) bomConn.close();
    }

    // ── W-WF-COST-1: costOfChange returns real baseline ────────────

    @Test @Order(1)
    @DisplayName("W-WF-COST-1: costOfChange returns baseline from SH_BOM.db")
    void w_wf_cost_1_baseline() {
        JsonObject req = new JsonObject();
        req.addProperty("buildingId", "SH");

        CostOfChangeResponse resp = api.costOfChange(req);

        assertTrue(resp.success(), "costOfChange must succeed, error: " + resp.error());
        assertTrue(resp.materialDeltaM() > 0,
                "materialDeltaM must be > 0 (baseline material total), got " + resp.materialDeltaM());
        assertTrue(resp.fittingsDelta() > 0,
                "fittingsDelta must be > 0 (number of cost lines), got " + resp.fittingsDelta());
        assertEquals("MYR", resp.currency(), "Currency must be MYR");
        assertEquals(0.0, resp.costDelta(), "costDelta must be 0.0 (baseline, no diff)");

        System.out.printf("  W-WF-COST-1: materialDeltaM=%.2f, fittingsDelta=%d, labourHrs=%.2f, currency=%s%n",
                resp.materialDeltaM(), resp.fittingsDelta(), resp.labourHrs(), resp.currency());
    }

    // ── W-WF-COST-1b: missing buildingId returns error ─────────────

    @Test @Order(2)
    @DisplayName("W-WF-COST-1b: costOfChange rejects missing buildingId")
    void w_wf_cost_1b_missing_id() {
        JsonObject req = new JsonObject();

        CostOfChangeResponse resp = api.costOfChange(req);

        assertFalse(resp.success(), "Must fail without buildingId");
        assertNotNull(resp.error(), "Error message must be present");
    }
}
