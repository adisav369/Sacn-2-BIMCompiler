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
 * P0.1-BOM witness: EXTRACTED BOM walk produces correct element counts.
 *
 * <p>Rosetta Stone principle: EXTRACTED buildings are all BUY — every element
 * already exists. No MAKE sub-assemblies. The BOM is a flat list of BUY lines,
 * one per extracted instance, each carrying centroid + AABB from the original IFC.
 *
 * <h2>Witness claims</h2>
 * <ul>
 *   <li>W-BOM-EXT-1: EXT_SH flat walk = 55 onBuy, 0 onMake</li>
 *   <li>W-BOM-EXT-2: EXT_DX flat walk = 1099 onBuy, 0 onMake</li>
 *   <li>W-BOM-EXT-3: Every BUY has non-null child_product_id + at least one AABB axis &gt; 0</li>
 *   <li>W-BOM-EXT-4: All 8 SH IFC classes represented as BUY roles</li>
 *   <li>W-BOM-EXT-5: bom_category = EXTRACTED for both BOMs</li>
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

    // ── W-BOM-EXT-1: EXT_SH = 55 BUY, 0 MAKE ────────────────────────────

    @Test
    @DisplayName("W-BOM-EXT-1: EXT_SH flat walk = 55 onBuy, 0 onMake")
    void w_bom_ext_1_sh_count() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + BOM_DB)) {
            BOMWalker walker = new BOMWalker(conn);
            CountingVisitor v = new CountingVisitor();
            walker.walk("EXT_SH", List.of(v), "SH");

            assertEquals(55, v.buyCount,
                "EXT_SH must have exactly 55 BUY lines (one per extracted element)");
            assertEquals(0, v.makeCount,
                "EXT_SH is Rosetta Stone — all BUY, no MAKE assemblies");
            assertEquals(0, v.phantomCount,
                "EXT_SH has no PHANTOM spacers");
        }
    }

    // ── W-BOM-EXT-2: EXT_DX = 1099 BUY, 0 MAKE ──────────────────────────

    @Test
    @DisplayName("W-BOM-EXT-2: EXT_DX flat walk = 1099 onBuy, 0 onMake")
    void w_bom_ext_2_dx_count() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + BOM_DB)) {
            BOMWalker walker = new BOMWalker(conn);
            CountingVisitor v = new CountingVisitor();
            walker.walk("EXT_DX", List.of(v), "DX");

            assertEquals(1099, v.buyCount,
                "EXT_DX must have exactly 1099 BUY lines (one per extracted element)");
            assertEquals(0, v.makeCount,
                "EXT_DX is Rosetta Stone — all BUY, no MAKE assemblies");
            assertEquals(0, v.phantomCount,
                "EXT_DX has no PHANTOM spacers");
        }
    }

    // ── W-BOM-EXT-3: Every BUY has product + non-zero AABB ───────────────

    @Test
    @DisplayName("W-BOM-EXT-3: Every BUY has non-null product and non-zero AABB")
    void w_bom_ext_3_buy_integrity() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + BOM_DB)) {
            BOMWalker walker = new BOMWalker(conn);

            for (String bomId : List.of("EXT_SH", "EXT_DX")) {
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

    // ── W-BOM-EXT-4: All 8 SH IFC classes present as BUY roles ───────────

    @Test
    @DisplayName("W-BOM-EXT-4: All 8 SH IFC classes represented as BUY roles")
    void w_bom_ext_4_sh_ifc_classes() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + BOM_DB)) {
            BOMWalker walker = new BOMWalker(conn);
            CountingVisitor v = new CountingVisitor();
            walker.walk("EXT_SH", List.of(v), "SH");

            Set<String> expected = Set.of(
                "IfcWall", "IfcSlab", "IfcRoof", "IfcMember",
                "IfcPlate", "IfcWindow", "IfcDoor", "IfcFurnishingElement"
            );
            assertEquals(expected, v.buyRoles,
                "EXT_SH must contain all 8 IFC classes from the SampleHouse extraction");
        }
    }

    // ── W-BOM-EXT-5: bom_category = EXTRACTED ─────────────────────────────

    @Test
    @DisplayName("W-BOM-EXT-5: bom_category = EXTRACTED for both EXT BOMs")
    void w_bom_ext_5_category() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + BOM_DB)) {
            for (String bomId : List.of("EXT_SH", "EXT_DX")) {
                List<MBOM> boms = MBOM.getByCategory(conn, "EXTRACTED");
                boolean found = boms.stream()
                    .anyMatch(b -> bomId.equals(b.getBomId()));
                assertTrue(found,
                    bomId + " must exist in bom_category=EXTRACTED");
            }
        }
    }
}
