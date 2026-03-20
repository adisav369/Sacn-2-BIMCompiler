package com.bim.designer;

import com.bim.designer.api.AssemblyAPI.*;
import com.bim.designer.assembly.AssemblyBuilderService;
import com.bim.designer.assembly.AssemblyDAO;
import com.bim.designer.assembly.AssemblyDAO.MaterialLayer;
import com.bim.designer.assembly.UValueCalculator;
import com.bim.designer.assembly.UValueCalculator.LayerThermal;
import com.bim.designer.assembly.UValueCalculator.Orientation;

import org.junit.jupiter.api.*;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Assembly Builder — layer-by-layer TACK (G-7).
 *
 * <p>Implements ASSEMBLY_BUILDER_SRS.md §8 — Witness: W-ASM-*
 *
 * <p>Tests against real component_library.db (material_layers + ad_material_thermal).
 */
@DisplayName("Assembly Builder — G-7 (§18.2 Principle 4)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AssemblyBuilderTest {

    private static final String LIB_DB = "library/component_library.db";
    private static final String BRICK_ON_BLOCK = "Basic Wall:Exterior - Brick on Block";

    private static Connection conn;
    private static AssemblyDAO dao;
    private static AssemblyBuilderService service;

    @BeforeAll
    static void setUp() throws Exception {
        Assumptions.assumeTrue(new File(LIB_DB).exists(),
                "component_library.db not found — skip assembly tests");
        conn = DriverManager.getConnection("jdbc:sqlite:" + LIB_DB);
        dao = new AssemblyDAO(conn);
        service = new AssemblyBuilderService(conn);
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (conn != null && !conn.isClosed()) conn.close();
    }

    // ── W-ASM-LIST: Template listing ────────────────────────────────

    @Test @Order(1)
    @DisplayName("W-ASM-LIST-1: listAssemblyTemplates(WALL) returns ≥ 7 wall templates")
    void w_asm_list_1_walls() {
        AssemblyListResponse resp = service.listAssemblyTemplates("WALL");
        assertTrue(resp.success(), "Should succeed");
        assertTrue(resp.templates().size() >= 7,
                "Expected ≥ 7 wall templates, got " + resp.templates().size());
        // All should be WALL category
        for (var t : resp.templates()) {
            assertEquals("WALL", t.category(), "Category mismatch for " + t.layerSetName());
        }
    }

    @Test @Order(2)
    @DisplayName("W-ASM-LIST-2: listAssemblyTemplates(FLOOR) returns ≥ 3 floor templates")
    void w_asm_list_2_floors() {
        AssemblyListResponse resp = service.listAssemblyTemplates("FLOOR");
        assertTrue(resp.success());
        assertTrue(resp.templates().size() >= 3,
                "Expected ≥ 3 floor templates, got " + resp.templates().size());
    }

    @Test @Order(3)
    @DisplayName("W-ASM-LIST-3: listAssemblyTemplates(ROOF) returns ≥ 1 roof templates")
    void w_asm_list_3_roofs() {
        AssemblyListResponse resp = service.listAssemblyTemplates("ROOF");
        assertTrue(resp.success());
        assertTrue(resp.templates().size() >= 1,
                "Expected ≥ 1 roof template, got " + resp.templates().size());
    }

    // ── W-ASM-DETAIL: Template detail ───────────────────────────────

    @Test @Order(10)
    @DisplayName("W-ASM-DETAIL-1: Brick-on-Block returns 6 layers, total ≈ 417mm")
    void w_asm_detail_1_brick_on_block() {
        AssemblyDetailResponse resp = service.getAssemblyDetail(BRICK_ON_BLOCK);
        assertTrue(resp.success(), "Should succeed: " + resp.error());
        assertEquals(6, resp.layers().size(), "Expected 6 layers");
        assertEquals(417.0, resp.totalThicknessMm(), 1.0,
                "Total thickness should be ≈ 417mm");
        assertEquals("WALL", resp.category());
    }

    @Test @Order(11)
    @DisplayName("W-ASM-DETAIL-2: Layer order matches material_layers.sequence")
    void w_asm_detail_2_sequence_order() {
        AssemblyDetailResponse resp = service.getAssemblyDetail(BRICK_ON_BLOCK);
        assertTrue(resp.success());
        var layers = resp.layers();
        assertEquals(0, layers.get(0).sequence());
        assertEquals(5, layers.get(5).sequence());
        // Verify monotonically increasing
        for (int i = 1; i < layers.size(); i++) {
            assertTrue(layers.get(i).sequence() > layers.get(i - 1).sequence(),
                    "Sequences must be monotonically increasing");
        }
    }

    // ── W-ASM-UVAL: U-value calculation ─────────────────────────────

    @Test @Order(20)
    @DisplayName("W-ASM-UVAL-1: Brick-on-Block U-value ≈ 0.37 W/m²K (±0.05)")
    void w_asm_uval_1_brick_on_block() {
        AssemblyDetailResponse resp = service.getAssemblyDetail(BRICK_ON_BLOCK);
        assertTrue(resp.success());
        double u = resp.uValueWm2K();
        assertTrue(u > 0.0 && u < 5.0,
                "U-value should be reasonable, got " + u);
        // Manual calc: R_total=2.72 → U=0.37 (insulation R=2.0 dominates)
        assertTrue(u >= 0.32 && u <= 0.42,
                "Brick-on-Block U should be ≈ 0.37 ± 0.05, got " + u);
    }

    @Test @Order(21)
    @DisplayName("W-ASM-UVAL-2: Swap insulation 50→100mm reduces U")
    void w_asm_uval_2_swap_reduces_u() {
        AssemblyDetailResponse before = service.getAssemblyDetail(BRICK_ON_BLOCK);
        assertTrue(before.success());
        double uBefore = before.uValueWm2K();

        // Swap insulation layer (seq 2) to 100mm
        SwapLayerRequest req = new SwapLayerRequest(BRICK_ON_BLOCK, 2,
                "Insulation / Thermal Barriers - Rigid insulation", 100.0);
        AssemblyDetailResponse after = service.swapLayer(req);
        assertTrue(after.success());
        double uAfter = after.uValueWm2K();

        assertTrue(uAfter < uBefore,
                "More insulation should reduce U: before=" + uBefore + " after=" + uAfter);
    }

    @Test @Order(22)
    @DisplayName("W-ASM-UVAL-3: Empty layers → IllegalArgumentException")
    void w_asm_uval_3_empty_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> UValueCalculator.calculate(List.of(), Orientation.VERTICAL));
        assertThrows(IllegalArgumentException.class,
                () -> UValueCalculator.calculate(null, Orientation.VERTICAL));
    }

    @Test @Order(23)
    @DisplayName("W-ASM-UVAL-4: Air gap uses fixed R=0.18, not λ/d")
    void w_asm_uval_4_air_gap_fixed_r() {
        // Two air gaps of different thicknesses (both ≥ 25mm) should have same R
        LayerThermal airGap25 = new LayerThermal(25.0, 0.025, true, false);
        LayerThermal airGap50 = new LayerThermal(50.0, 0.025, true, false);

        double r25 = UValueCalculator.layerResistance(airGap25);
        double r50 = UValueCalculator.layerResistance(airGap50);

        assertEquals(0.18, r25, 0.001, "25mm air gap R should be 0.18");
        assertEquals(0.18, r50, 0.001, "50mm air gap R should also be 0.18 (capped)");

        // Thin gap (10mm) should be proportionally less
        LayerThermal airGap10 = new LayerThermal(10.0, 0.025, true, false);
        double r10 = UValueCalculator.layerResistance(airGap10);
        assertTrue(r10 < 0.18, "10mm air gap R should be < 0.18, got " + r10);
        assertEquals(0.18 * 10.0 / 25.0, r10, 0.001);
    }

    // ── W-ASM-BROWSE: Layer alternatives ────────────────────────────

    @Test @Order(30)
    @DisplayName("W-ASM-BROWSE-1: Browse insulation slot returns alternatives")
    void w_asm_browse_1_insulation() {
        BrowseAssemblyLayersRequest req = new BrowseAssemblyLayersRequest(
                BRICK_ON_BLOCK, 2, null, 0, 50);
        BrowseAssemblyLayersResponse resp = service.browseAssemblyLayers(req);
        assertTrue(resp.success(), "Should succeed: " + resp.error());
        assertFalse(resp.alternatives().isEmpty(), "Should return alternatives");
        // Current material should not appear in alternatives
        assertTrue(resp.alternatives().stream().noneMatch(
                a -> a.materialName().equals("Insulation / Thermal Barriers - Rigid insulation")),
                "Current material should be excluded from alternatives");
    }

    // ── W-ASM-SWAP: Layer replacement ───────────────────────────────

    @Test @Order(40)
    @DisplayName("W-ASM-SWAP-1: Swap insulation 50→100mm increases total by 50mm")
    void w_asm_swap_1_thickness() {
        AssemblyDetailResponse before = service.getAssemblyDetail(BRICK_ON_BLOCK);
        double totalBefore = before.totalThicknessMm();

        SwapLayerRequest req = new SwapLayerRequest(BRICK_ON_BLOCK, 2,
                "Insulation / Thermal Barriers - Rigid insulation", 100.0);
        AssemblyDetailResponse after = service.swapLayer(req);
        assertTrue(after.success());

        assertEquals(totalBefore + 50.0, after.totalThicknessMm(), 0.1,
                "Total should increase by 50mm");
    }

    @Test @Order(41)
    @DisplayName("W-ASM-SWAP-2: Swap recalculates U-value")
    void w_asm_swap_2_recalculates_u() {
        AssemblyDetailResponse before = service.getAssemblyDetail(BRICK_ON_BLOCK);
        SwapLayerRequest req = new SwapLayerRequest(BRICK_ON_BLOCK, 2,
                "Rigid insulation", 100.0);
        AssemblyDetailResponse after = service.swapLayer(req);
        assertTrue(after.success());
        assertNotEquals(before.uValueWm2K(), after.uValueWm2K(),
                "U-value must change after swap");
    }

    // ── W-ASM-DAO: Data access layer ────────────────────────────────

    @Test @Order(50)
    @DisplayName("W-ASM-DAO-1: SH Brick-on-Block normalises metres → mm (all > 1.0)")
    void w_asm_dao_1_normalises_metres() throws Exception {
        List<MaterialLayer> layers = dao.loadLayers(BRICK_ON_BLOCK);
        assertEquals(6, layers.size());
        for (MaterialLayer ml : layers) {
            assertTrue(ml.thicknessMm() > 1.0,
                    "Thickness should be in mm (> 1.0), got " + ml.thicknessMm()
                    + " for " + ml.materialName());
        }
    }

    @Test @Order(51)
    @DisplayName("W-ASM-DAO-2: TE-style data (already mm) stays mm")
    void w_asm_dao_2_mm_stays_mm() throws Exception {
        // Wall-Partn_12P-70MStd-12P has thicknesses in mm (12.5, 70.0, 12.5)
        List<MaterialLayer> layers = dao.loadLayers("Basic Wall:Wall-Partn_12P-70MStd-12P");
        assertFalse(layers.isEmpty(), "Should have layers");
        double total = layers.stream().mapToDouble(MaterialLayer::thicknessMm).sum();
        // If correctly handled as mm: 12.5 + 70.0 + 12.5 = 95.0
        // If wrongly converted from m: 12500 + 70000 + 12500 = 95000
        assertEquals(95.0, total, 1.0, "Total should be 95mm (not 95000mm)");
    }

    // ── W-ASM-THICK: Thickness invariant ────────────────────────────

    @Test @Order(60)
    @DisplayName("W-ASM-THICK-1: SUM(layers) matches template total for all 20 templates")
    void w_asm_thick_1_all_templates() throws Exception {
        // Get all templates across all categories
        String[] categories = {"WALL", "ROOF", "FLOOR", "CEILING"};
        int templateCount = 0;
        for (String cat : categories) {
            AssemblyListResponse resp = service.listAssemblyTemplates(cat);
            assertTrue(resp.success());
            for (var template : resp.templates()) {
                // Get detail and verify internal consistency
                AssemblyDetailResponse detail = service.getAssemblyDetail(template.layerSetName());
                assertTrue(detail.success(), "Detail should succeed for " + template.layerSetName());

                double sumLayers = detail.layers().stream()
                        .mapToDouble(AssemblyLayer::thicknessMm).sum();
                assertEquals(detail.totalThicknessMm(), sumLayers, 0.01,
                        "Thickness invariant violated for " + template.layerSetName());
                templateCount++;
            }
        }
        assertEquals(20, templateCount, "Expected 20 total templates");
    }

    // ── W-ASM thermal properties ────────────────────────────────────

    @Test @Order(70)
    @DisplayName("W-ASM-THERMAL: ad_material_thermal has ≥29 rows covering all material_layers materials")
    void w_asm_thermal_coverage() throws Exception {
        Map<String, Double> thermal = dao.loadAllThermalProperties();
        assertTrue(thermal.size() >= 29, "Expected ≥29 thermal property rows, got " + thermal.size());

        // Check key materials have reasonable values
        assertEquals(0.77, thermal.get("Masonry - Brick"), 0.01);
        assertEquals(0.025, thermal.get("Rigid insulation"), 0.001);
        assertEquals(0.21, thermal.get("Plasterboard"), 0.01);
    }
}
