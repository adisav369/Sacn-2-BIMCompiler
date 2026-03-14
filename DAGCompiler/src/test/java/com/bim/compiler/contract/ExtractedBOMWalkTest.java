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
 * P0.1-BOM witness: BUILDING BOM tree walk produces correct element counts.
 *
 * <p>Walks the BUILDING BOM hierarchy (BUILDING → FLOOR_STR → leaf elements).
 * SubAssembly events fire at each floor boundary; leaf events fire for each leaf element.
 *
 * <h2>Witness claims</h2>
 * <ul>
 *   <li>W-BOM-EB-1: BUILDING_SH_STD walk = 55 onLeaf, 3 onSubAssembly (3 floors)</li>
 *   <li>W-BOM-EB-2: BUILDING_DX_STD walk = 1099 onLeaf, 5 onSubAssembly (5 floors)</li>
 *   <li>W-BOM-EB-3: Every leaf has non-null child_product_id + at least one AABB axis &gt; 0</li>
 *   <li>W-BOM-EB-4: All 8 SH IFC classes represented as leaf roles</li>
 *   <li>W-BOM-EB-5: bom_category = RE (Residential) for BUILDING BOMs</li>
 * </ul>
 */
class ExtractedBOMWalkTest {

    private static final String BOM_DB = System.getProperty("bom.db");
    private static final String BUILDING_SH = "BUILDING_SH_STD";
    private static final String BUILDING_DX = "BUILDING_DX_STD";

    /** Counting visitor — same pattern as BOMWalkerTest. */
    static class CountingVisitor implements BOMVisitor {
        int subAssemblyCount = 0;
        int leafCount = 0;
        int phantomCount = 0;
        final Set<String> leafRoles = new TreeSet<>();
        final List<BOMWalker.NodeContext> leafEvents = new ArrayList<>();

        @Override public void onSubAssembly(BOMWalker.NodeContext ctx) { subAssemblyCount++; }
        @Override public void onSubAssemblyComplete(BOMWalker.NodeContext ctx) {}
        @Override public void onLeaf(BOMWalker.NodeContext ctx) {
            leafCount++;
            if (ctx.role() != null) leafRoles.add(ctx.role());
            leafEvents.add(ctx);
        }
        @Override public void onPhantom(BOMWalker.NodeContext ctx) { phantomCount++; }
    }

    // ── W-BOM-EB-1: BUILDING_SH_STD = 55 leaf, 3 subAssembly ─────────

    @Test
    @DisplayName("W-BOM-EB-1: BUILDING_SH_STD walk = 55 onLeaf, 3 onSubAssembly (3 floor STRs)")
    void w_bom_eb_1_sh_count() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + BOM_DB)) {
            BOMWalker walker = new BOMWalker(conn);
            CountingVisitor v = new CountingVisitor();
            walker.walk(BUILDING_SH, List.of(v), "SH");

            assertEquals(55, v.leafCount,
                "BUILDING_SH_STD must have exactly 55 leaf nodes");
            assertEquals(3, v.subAssemblyCount,
                "BUILDING_SH_STD has 3 floor STR sub-assemblies (GF, ROOF, CW)");
        }
    }

    // ── W-BOM-EB-2: BUILDING_DX_STD = 1099 leaf, 5 subAssembly ───────

    @Test
    @DisplayName("W-BOM-EB-2: BUILDING_DX_STD walk = 1099 onLeaf, 5 onSubAssembly (5 floor STRs)")
    void w_bom_eb_2_dx_count() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + BOM_DB)) {
            BOMWalker walker = new BOMWalker(conn);
            CountingVisitor v = new CountingVisitor();
            walker.walk(BUILDING_DX, List.of(v), "DX");

            assertEquals(1099, v.leafCount,
                "BUILDING_DX_STD must have exactly 1099 leaf nodes");
            assertEquals(5, v.subAssemblyCount,
                "BUILDING_DX_STD has 5 floor STR sub-assemblies (L1, L2, ROOF, FDN, MISC)");
        }
    }

    // ── W-BOM-EB-3: Every leaf has product + non-zero AABB ──────────────

    @Test
    @DisplayName("W-BOM-EB-3: Every leaf has non-null product and non-zero AABB")
    void w_bom_eb_3_buy_integrity() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + BOM_DB)) {
            BOMWalker walker = new BOMWalker(conn);

            for (var entry : Map.of(BUILDING_SH, "SH", BUILDING_DX, "DX").entrySet()) {
                CountingVisitor v = new CountingVisitor();
                walker.walk(entry.getKey(), List.of(v), entry.getValue());

                for (BOMWalker.NodeContext ctx : v.leafEvents) {
                    assertNotNull(ctx.childProductId(),
                        entry.getKey() + ": leaf line has null child_product_id");
                    assertNotNull(ctx.line(),
                        entry.getKey() + ": leaf line context has null line");

                    int w = ctx.line().getAllocatedWidthMm();
                    int d = ctx.line().getAllocatedDepthMm();
                    int h = ctx.line().getAllocatedHeightMm();
                    assertTrue(w > 0 || d > 0 || h > 0,
                        entry.getKey() + ": leaf product " + ctx.childProductId() +
                        " has zero AABB (" + w + "x" + d + "x" + h + ")");
                }
            }
        }
    }

    // ── W-BOM-EB-4: All 8 SH IFC classes present as leaf roles ──────────

    @Test
    @DisplayName("W-BOM-EB-4: All 8 SH IFC classes represented as leaf roles")
    void w_bom_eb_4_sh_ifc_classes() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + BOM_DB)) {
            BOMWalker walker = new BOMWalker(conn);
            CountingVisitor v = new CountingVisitor();
            walker.walk(BUILDING_SH, List.of(v), "SH");

            Set<String> expected = Set.of(
                "IfcWall", "IfcSlab", "IfcRoof", "IfcMember",
                "IfcPlate", "IfcWindow", "IfcDoor", "IfcFurnishingElement"
            );
            assertEquals(expected, v.leafRoles,
                "BUILDING_SH_STD must contain all 8 IFC classes from the SampleHouse extraction");
        }
    }

    // ── W-BOM-EB-5: bom_category = RE for BUILDING BOMs ──────────────────

    @Test
    @DisplayName("W-BOM-EB-5: bom_category = RE for BUILDING BOMs")
    void w_bom_eb_5_category() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + BOM_DB)) {
            for (String bomId : List.of(BUILDING_SH, BUILDING_DX)) {
                MBOM bom = MBOM.get(conn, bomId);
                assertNotNull(bom, bomId + " must exist and be active");
                assertEquals("RE", bom.getBomCategory(),
                    bomId + " bom_category must be RE (Residential)");
            }
        }
    }
}
