package com.bim.cobol;

import com.bim.cobol.verb.HelloWorldVerb;
import com.bim.cobol.verb.HelloWorldVerb.*;
import org.junit.jupiter.api.*;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * HelloWorld Verb — Permanent Dual-Path Proof.
 *
 * <p>"Can this plane even take off?" (Q&amp;A1.txt Round 1 Q1).
 *
 * <p>Witnesses:
 * <ul>
 *   <li>W-HW-1: Singularity rule holds for SH</li>
 *   <li>W-HW-2: Singularity rule holds for DX</li>
 *   <li>W-HW-3: SH enbloc=55, walkthru=55, ref=55</li>
 *   <li>W-HW-4: DX enbloc=1099, ref=1099</li>
 *   <li>W-HW-5: SH digest: enbloc == walkthru == reference</li>
 *   <li>W-HW-6: DX digest: enbloc == reference</li>
 * </ul>
 */
@DisplayName("HELLO WORLD — Dual-Path Proof")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HelloWorldVerbTest {

    private static Connection bomConn;
    private static HelloWorldVerb verb;
    private static VerbContext ctx;

    // Cached results — verb is run once per building, witnesses check payload
    private static BuildingResult shResult;
    private static BuildingResult dxResult;

    @BeforeAll
    static void setUp() throws Exception {
        File bomDb = new File(System.getProperty("bom.db"));
        assumeTrue(bomDb.exists(), System.getProperty("bom.db") + " must exist (set -Dbom.db=library/_XX_compile.db)");
        bomConn = DriverManager.getConnection("jdbc:sqlite:" + bomDb.getAbsolutePath());
        verb = new HelloWorldVerb();
        ctx = VerbContext.ofBom(bomConn);

        // Run verb for SH
        VerbResult<HelloWorldPayload> shVr = verb.execute(ctx, "SH");
        assertNotNull(shVr.payload(), "SH payload must not be null");
        shResult = shVr.payload().results().get(0);

        // Run verb for DX
        VerbResult<HelloWorldPayload> dxVr = verb.execute(ctx, "DX");
        assertNotNull(dxVr.payload(), "DX payload must not be null");
        dxResult = dxVr.payload().results().get(0);
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (bomConn != null) bomConn.close();
    }

    // ── W-HW-1: Singularity rule holds for SH ─────────────────────

    @Test
    @Order(1)
    @DisplayName("W-HW-1: SH singularity — DocSubType → 1 BUILDING BOM")
    void w_hw_1_sh_singularity() {
        SingularityCheck s = shResult.singularity();
        assertNotNull(s, "singularity check must be present");
        assertTrue(s.docSubTypeMatch(), "SH DocSubType must match BUILDING BOM");
        assertEquals(1, s.buildingCount(), "SH must have exactly 1 BUILDING BOM");
        assertTrue(s.singularity(), "SH must satisfy singularity rule");
        assertEquals("BUILDING_SH_STD", s.buildingBomId());
        assertTrue(s.bomWidthMm() > 0, "BOM AABB must be computed from data");
    }

    // ── W-HW-2: Singularity rule holds for DX ─────────────────────

    @Test
    @Order(2)
    @DisplayName("W-HW-2: DX singularity — DocSubType → 1 BUILDING BOM")
    void w_hw_2_dx_singularity() {
        SingularityCheck s = dxResult.singularity();
        assertNotNull(s, "singularity check must be present");
        assertTrue(s.docSubTypeMatch(), "DX DocSubType must match BUILDING BOM");
        assertEquals(1, s.buildingCount(), "DX must have exactly 1 BUILDING BOM");
        assertTrue(s.singularity(), "DX must satisfy singularity rule");
        assertEquals("BUILDING_DX_STD", s.buildingBomId());
        assertTrue(s.bomWidthMm() > 0, "BOM AABB must be computed from data");
    }

    // ── W-HW-3: SH element counts ─────────────────────────────────

    @Test
    @Order(3)
    @DisplayName("W-HW-3: SH enbloc=55, walkthru=55, ref=55")
    void w_hw_3_sh_counts() {
        DbInventory ref = shResult.reference();
        assumeTrue(ref != null && ref.exists(),
                "SH reference DB must exist");
        assertEquals(55, ref.elements(), "SH reference elements");

        DbInventory eb = shResult.enbloc();
        assumeTrue(eb != null && eb.exists(),
                "SH enbloc DB must exist (run run_RosettaStones.sh first)");
        assertEquals(55, eb.elements(), "SH enbloc elements");

        DbInventory wt = shResult.walkthru();
        assumeTrue(wt != null && wt.exists(),
                "SH walkthru DB must exist (run run_RosettaStones.sh first)");
        assertEquals(55, wt.elements(), "SH walkthru elements");
    }

    // ── W-HW-4: DX element counts ─────────────────────────────────

    @Test
    @Order(4)
    @DisplayName("W-HW-4: DX enbloc=1099, ref=1099")
    void w_hw_4_dx_counts() {
        DbInventory ref = dxResult.reference();
        assumeTrue(ref != null && ref.exists(),
                "DX reference DB must exist");
        assertEquals(1099, ref.elements(), "DX reference elements");

        DbInventory eb = dxResult.enbloc();
        assumeTrue(eb != null && eb.exists(),
                "DX enbloc DB must exist (run run_RosettaStones.sh first)");
        assertEquals(1099, eb.elements(), "DX enbloc elements");

        // DX walkthru: known gap — WT_DX produces fewer elements
        // until M_BomCategoryLine slot selection + MIRROR are wired.
        // When fixed, assert: assertEquals(1099, wt.elements())
        DbInventory wt = dxResult.walkthru();
        if (wt != null && wt.exists()) {
            assertTrue(wt.elements() > 0,
                    "DX walkthru should produce some elements");
        }
    }

    // ── W-HW-5: SH digest match ───────────────────────────────────

    @Test
    @Order(5)
    @DisplayName("W-HW-5: SH digest — enbloc == walkthru == reference")
    void w_hw_5_sh_digest() {
        assumeTrue(shResult.enbloc() != null && shResult.enbloc().exists(),
                "SH enbloc DB must exist");
        assumeTrue(shResult.walkthru() != null && shResult.walkthru().exists(),
                "SH walkthru DB must exist");
        assumeTrue(shResult.reference() != null && shResult.reference().exists(),
                "SH reference DB must exist");

        String ebDigest = shResult.enbloc().digest();
        String wtDigest = shResult.walkthru().digest();
        String refDigest = shResult.reference().digest();

        assertNotNull(ebDigest, "enbloc digest must not be null");
        assertNotNull(wtDigest, "walkthru digest must not be null");
        assertNotNull(refDigest, "reference digest must not be null");

        assertEquals(ebDigest, wtDigest, "enbloc vs walkthru digest");
        assertEquals(ebDigest, refDigest, "enbloc vs reference digest");
    }

    // ── W-HW-6: DX digest match ───────────────────────────────────

    @Test
    @Order(6)
    @DisplayName("W-HW-6: DX digest — enbloc == reference")
    void w_hw_6_dx_digest() {
        assumeTrue(dxResult.enbloc() != null && dxResult.enbloc().exists(),
                "DX enbloc DB must exist");
        assumeTrue(dxResult.reference() != null && dxResult.reference().exists(),
                "DX reference DB must exist");

        String ebDigest = dxResult.enbloc().digest();
        String refDigest = dxResult.reference().digest();

        assertNotNull(ebDigest, "enbloc digest must not be null");
        assertNotNull(refDigest, "reference digest must not be null");

        assertEquals(ebDigest, refDigest, "DX enbloc vs reference digest");

        // DX walkthru digest: known gap — will differ until WT_DX is complete
        DbInventory wt = dxResult.walkthru();
        if (wt != null && wt.exists() && wt.digest() != null
                && wt.elements() == 1099) {
            assertEquals(ebDigest, wt.digest(),
                    "DX walkthru vs enbloc digest (when complete)");
        }
    }
}
