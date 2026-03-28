package com.bim.cobol.forge;

import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbRegistry;
import com.bim.cobol.VerbResult;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Geometry Forge — starter piece tests.
 * @Traces GEOMETRY_FORGE_SRS.md §4, §10
 */
class ForgeVerbTest {

    private static VerbRegistry registry;
    private static VerbContext ctx;
    private static Connection bomConn;

    @BeforeAll
    static void setUp() throws Exception {
        // Minimal BOM.db — forge pieces are pure maths, don't need BOM data
        bomConn = DriverManager.getConnection("jdbc:sqlite::memory:");
        ctx = VerbContext.ofBom(bomConn);
        registry = VerbRegistry.createDefault();
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (bomConn != null) bomConn.close();
    }

    /**
     * W-FORGE-1: SLOPE_CUT computes rafter length from pitch + span.
     */
    @Test
    void slopeCutComputesLength() {
        VerbResult<?> r = registry.dispatch(ctx, "FORGE SLOPE_CUT pitch:30 span:5200 width:90 depth:45");
        assertTrue(r.pass(), "Should pass: " + r.summary());
        ForgeResult fr = (ForgeResult) r.payload();
        assertEquals(1, fr.records().size());
        GeometryRecord rec = fr.records().get(0);
        // length = 5200 / cos(30°) = 5200 / 0.866 ≈ 6004mm
        assertEquals(6004, rec.lengthMm(), 1.0);
        assertTrue(rec.fabrication().containsKey("cut_angle_top"));
        assertTrue(rec.fabrication().containsKey("cut_angle_bottom"));
    }

    /**
     * W-FORGE-2: STAIR_FLIGHT computes step count and stringer from height.
     */
    @Test
    void stairFlightComputesSteps() {
        VerbResult<?> r = registry.dispatch(ctx, "FORGE STAIR_FLIGHT height:2700 tread:250 riser:180 width:900");
        assertTrue(r.pass(), "Should pass: " + r.summary());
        ForgeResult fr = (ForgeResult) r.payload();
        assertTrue(fr.records().size() >= 1);
        GeometryRecord stringer = fr.records().get(0);
        // step_count = ceil(2700/180) = 15
        assertEquals(15.0, stringer.fabrication().get("step_count"), 0.01);
        // stringer = sqrt(3750² + 2700²) ≈ 4621mm
        assertEquals(4621, stringer.lengthMm(), 2.0);
        // Blondel: 2×180 + 250 = 610 (in 550-700 range)
        assertTrue(fr.compliance().stream().allMatch(c -> c.contains("PASS")));
    }

    /**
     * W-FORGE-3: PIPE_BEND computes arc length from angle + radius.
     */
    @Test
    void pipeBendComputesArc() {
        VerbResult<?> r = registry.dispatch(ctx, "FORGE PIPE_BEND angle:90 radius:150 diameter:32");
        assertTrue(r.pass(), "Should pass: " + r.summary());
        ForgeResult fr = (ForgeResult) r.payload();
        assertTrue(fr.records().size() >= 1);
        GeometryRecord bend = fr.records().get(0);
        // arc = 150 × π/2 ≈ 236mm
        assertEquals(236, bend.lengthMm(), 1.0);
        // radius >= 3 × diameter: 150 >= 96 → PASS
        assertTrue(fr.compliance().stream().allMatch(c -> c.contains("PASS")));
    }

    /**
     * W-FORGE-4: Unknown piece type returns structured failure.
     */
    @Test
    void unknownTypeReturnsFail() {
        VerbResult<?> r = registry.dispatch(ctx, "FORGE XYZZY pitch:30");
        assertFalse(r.pass());
        assertTrue(r.summary().contains("unknown piece type"));
    }

    /**
     * W-FORGE-5: SLOPE_CUT rejects pitch > 60°.
     */
    @Test
    void slopeCutRejectsSteepPitch() {
        VerbResult<?> r = registry.dispatch(ctx, "FORGE SLOPE_CUT pitch:75 span:3000 width:90 depth:45");
        assertFalse(r.pass());
    }

    /**
     * W-FORGE-6: STAIR_FLIGHT rejects riser > 220mm (code violation).
     */
    @Test
    void stairRejectsHighRiser() {
        VerbResult<?> r = registry.dispatch(ctx, "FORGE STAIR_FLIGHT height:2700 tread:250 riser:250 width:900");
        assertFalse(r.pass());
    }

    /**
     * W-FORGE-7: DOME_SECTION computes correct panel count for 6 rings × 12 segments.
     */
    @Test
    void domeSectionComputesPanels() {
        VerbResult<?> r = registry.dispatch(ctx, "FORGE DOME_SECTION radius:8000 rings:6 segments:12 base_z:15000");
        assertTrue(r.pass(), "Should pass: " + r.summary());
        ForgeResult fr = (ForgeResult) r.payload();
        assertEquals(72, fr.records().size(), "6 rings × 12 segments = 72 panels");
        // Crown panels should be narrower than equator panels
        GeometryRecord crown = fr.records().get(0);  // first ring (near pole)
        GeometryRecord equator = fr.records().get(fr.records().size() - 1);  // last ring
        assertTrue(crown.widthMm() < equator.widthMm(),
                "Crown panels narrower than equator panels");
    }

    /**
     * W-FORGE-8: BARREL_VAULT computes arc length from span + rise.
     */
    @Test
    void barrelVaultComputesArc() {
        VerbResult<?> r = registry.dispatch(ctx, "FORGE BARREL_VAULT span:12000 length:20000 ribs:10 rise:4000");
        assertTrue(r.pass(), "Should pass: " + r.summary());
        ForgeResult fr = (ForgeResult) r.payload();
        assertTrue(fr.records().size() >= 10, "At least 10 rib records");
        // R = (12000²/4 + 4000²) / (2 × 4000) = (36M + 16M) / 8000 = 6500mm
        // arc per rib > span (arc is longer than chord)
        GeometryRecord rib = fr.records().get(0);
        assertTrue(rib.lengthMm() > 12000,
                "Arc length > span for non-flat vault");
    }

    /**
     * W-FORGE-9: Slab rebar for 6m x 4m x 200mm -> correct bar count + spacing.
     */
    @Test
    void rebarSlabComputesBars() {
        VerbResult<?> r = registry.dispatch(ctx, "FORGE REBAR_CAGE type:SLAB grade:GRADE_30 exposure:XC1 width:6000 depth:4000 thickness:200");
        assertTrue(r.pass(), "Should pass: " + r.summary());
        ForgeResult fr = (ForgeResult) r.payload();
        assertEquals(1, fr.records().size());
        GeometryRecord rec = fr.records().get(0);
        // Main bars T12 @ <=300mm spacing for 200mm slab
        assertTrue(rec.fabrication().get("main_bar_diameter") <= 16);
        assertTrue(rec.fabrication().get("main_bar_spacing") <= 300);
        assertTrue(rec.fabrication().get("main_bar_count") > 0);
        // Reinforcement ratio >= 0.13%
        assertTrue(rec.fabrication().get("reinforcement_ratio") >= 0.0013);
    }

    /**
     * W-FORGE-10: Column rebar for 400mm x 400mm -> correct ratio + link spacing.
     */
    @Test
    void rebarColumnComputesLinks() {
        VerbResult<?> r = registry.dispatch(ctx, "FORGE REBAR_CAGE type:COLUMN grade:GRADE_30 exposure:XC1 width:400 depth:400 height:3500");
        assertTrue(r.pass(), "Should pass: " + r.summary());
        ForgeResult fr = (ForgeResult) r.payload();
        GeometryRecord rec = fr.records().get(0);
        // Ratio >= 0.8% and <= 4%
        double ratio = rec.fabrication().get("reinforcement_ratio");
        assertTrue(ratio >= 0.008, "Min 0.8% ratio: " + ratio);
        assertTrue(ratio <= 0.04, "Max 4% ratio: " + ratio);
        // Link spacing <= 12 x bar diameter
        assertTrue(rec.fabrication().get("link_spacing") <= 12 * rec.fabrication().get("longitudinal_bar_diameter"));
    }

    /**
     * W-FORGE-11: Exposure class XS3 -> increased cover requirement.
     */
    @Test
    void rebarXS3IncreasedCover() {
        VerbResult<?> r = registry.dispatch(ctx, "FORGE REBAR_CAGE type:SLAB grade:GRADE_30 exposure:XS3 width:6000 depth:4000 thickness:200");
        assertTrue(r.pass(), "Should pass: " + r.summary());
        ForgeResult fr = (ForgeResult) r.payload();
        GeometryRecord rec = fr.records().get(0);
        // XS3 base cover 55mm + 5mm formwork tolerance = 60mm
        assertEquals(60.0, rec.fabrication().get("cover_mm"), 0.01);
    }
}
