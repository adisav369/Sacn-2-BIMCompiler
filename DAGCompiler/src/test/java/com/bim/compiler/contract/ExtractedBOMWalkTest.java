package com.bim.compiler.contract;

import com.bim.compiler.bom.walker.BOMVisitor;
import com.bim.compiler.bom.walker.BOMWalker;
import com.bim.ormsandbox.po.MBOM;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P0.1-BOM witness: EN-BLOC BOM walk produces correct element counts.
 *
 * <p>EN-BLOC (HelloWorld POC): BOM lines are already tacked. When AABB and
 * DocType are consistent throughout, the compiler takes each as-is without
 * recalculating through hierarchy layers. All BUY, no sub-assemblies.
 *
 * <h2>Witness claims</h2>
 * <ul>
 *   <li>W-BOM-EB-1: EB_SH walk = 55 onBuy, 0 onMake</li>
 *   <li>W-BOM-EB-2: EB_DX walk = 1099 onBuy, 0 onMake</li>
 *   <li>W-BOM-EB-3: Every BUY has non-null child_product_id + at least one AABB axis &gt; 0</li>
 *   <li>W-BOM-EB-4: All 8 SH IFC classes represented as BUY roles</li>
 *   <li>W-BOM-EB-5: bom_category = RE (Residential) for both BOMs</li>
 * </ul>
 */
class ExtractedBOMWalkTest {

    private static final String BOM_DB = "library/BOM.db";

    /** Counting visitor — same pattern as BOMWalkerTest. */
    static class CountingVisitor implements BOMVisitor {
        int makeCount = 0;
        int buyCount = 0;
        int phantomCount = 0;
        final Set<String> buyRoles = new TreeSet<>();
        final List<BOMWalker.NodeContext> buyEvents = new ArrayList<>();

        @Override public void onMake(BOMWalker.NodeContext ctx) { makeCount++; }
        @Override public void onMakeComplete(BOMWalker.NodeContext ctx) {}
        @Override public void onBuy(BOMWalker.NodeContext ctx) {
            buyCount++;
            if (ctx.role() != null) buyRoles.add(ctx.role());
            buyEvents.add(ctx);
        }
        @Override public void onPhantom(BOMWalker.NodeContext ctx) { phantomCount++; }
    }

    // ── W-BOM-EB-1: EB_SH = 55 BUY, 0 MAKE ────────────────────────────

    @Test
    @DisplayName("W-BOM-EB-1: EB_SH walk = 55 onBuy, 0 onMake")
    void w_bom_eb_1_sh_count() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + BOM_DB)) {
            BOMWalker walker = new BOMWalker(conn);
            CountingVisitor v = new CountingVisitor();
            walker.walk("EB_SH", List.of(v), "SH");

            assertEquals(55, v.buyCount,
                "EB_SH must have exactly 55 BUY lines (one per element)");
            assertEquals(0, v.makeCount,
                "EB_SH is EN-BLOC — all BUY, no sub-assemblies");
            assertEquals(0, v.phantomCount,
                "EB_SH has no PHANTOM spacers");
        }
    }

    // ── W-BOM-EB-2: EB_DX = 1099 BUY, 0 MAKE ──────────────────────────

    @Test
    @DisplayName("W-BOM-EB-2: EB_DX walk = 1099 onBuy, 0 onMake")
    void w_bom_eb_2_dx_count() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + BOM_DB)) {
            BOMWalker walker = new BOMWalker(conn);
            CountingVisitor v = new CountingVisitor();
            walker.walk("EB_DX", List.of(v), "DX");

            assertEquals(1099, v.buyCount,
                "EB_DX must have exactly 1099 BUY lines (one per element)");
            assertEquals(0, v.makeCount,
                "EB_DX is EN-BLOC — all BUY, no sub-assemblies");
            assertEquals(0, v.phantomCount,
                "EB_DX has no PHANTOM spacers");
        }
    }

    // ── W-BOM-EB-3: Every BUY has product + non-zero AABB ───────────────

    @Test
    @DisplayName("W-BOM-EB-3: Every BUY has non-null product and non-zero AABB")
    void w_bom_eb_3_buy_integrity() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + BOM_DB)) {
            BOMWalker walker = new BOMWalker(conn);

            for (String bomId : List.of("EB_SH", "EB_DX")) {
                CountingVisitor v = new CountingVisitor();
                walker.walk(bomId, List.of(v), bomId.endsWith("SH") ? "SH" : "DX");

                for (BOMWalker.NodeContext ctx : v.buyEvents) {
                    assertNotNull(ctx.childProductId(),
                        bomId + ": BUY line has null child_product_id");
                    assertNotNull(ctx.line(),
                        bomId + ": BUY line context has null line");

                    int w = ctx.line().getAllocatedWidthMm();
                    int d = ctx.line().getAllocatedDepthMm();
                    int h = ctx.line().getAllocatedHeightMm();
                    assertTrue(w > 0 || d > 0 || h > 0,
                        bomId + ": BUY product " + ctx.childProductId() +
                        " has zero AABB (" + w + "x" + d + "x" + h + ")");
                }
            }
        }
    }

    // ── W-BOM-EB-4: All 8 SH IFC classes present as BUY roles ───────────

    @Test
    @DisplayName("W-BOM-EB-4: All 8 SH IFC classes represented as BUY roles")
    void w_bom_eb_4_sh_ifc_classes() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + BOM_DB)) {
            BOMWalker walker = new BOMWalker(conn);
            CountingVisitor v = new CountingVisitor();
            walker.walk("EB_SH", List.of(v), "SH");

            Set<String> expected = Set.of(
                "IfcWall", "IfcSlab", "IfcRoof", "IfcMember",
                "IfcPlate", "IfcWindow", "IfcDoor", "IfcFurnishingElement"
            );
            assertEquals(expected, v.buyRoles,
                "EB_SH must contain all 8 IFC classes from the SampleHouse extraction");
        }
    }

    // ── W-BOM-EB-5: bom_category = RE (Residential, not compilation mode) ──

    @Test
    @DisplayName("W-BOM-EB-5: bom_category = RE for both EN-BLOC BOMs")
    void w_bom_eb_5_category() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + BOM_DB)) {
            for (String bomId : List.of("EB_SH", "EB_DX")) {
                MBOM bom = MBOM.get(conn, bomId);
                assertNotNull(bom, bomId + " must exist and be active");
                assertEquals("RE", bom.getBomCategory(),
                    bomId + " bom_category must be RE (Residential)");
            }
        }
    }
}
