package com.bim.compiler.contract;

import com.bim.compiler.bom.walker.BOMWalker;
import com.bim.compiler.bom.walker.PlacementCollectorVisitor;
import com.bim.compiler.dsl.PlacementLoader;
import org.junit.jupiter.api.*;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Witness: PlacementCollectorVisitor produces correct world coordinates
 * independently of PlacementLoader wrapper.
 *
 * <p>Creates an <b>independent</b> visitor instance and walks the same
 * BUILDING BOM that PlacementLoader uses internally — proving the visitor
 * works standalone, not just through the PlacementLoader singleton.
 *
 * <h2>Witness claims</h2>
 * <ul>
 *   <li>W-PCV-1: Visitor placement count == PlacementLoader count for SH</li>
 *   <li>W-PCV-2: Element refs identical (as multiset) between visitor walk and PlacementLoader</li>
 *   <li>W-PCV-3: World coordinates match within 1mm tolerance (index-aligned, same walk order)</li>
 * </ul>
 */
class PlacementCollectorVisitorTest {

    private static final String BOM_DB = "jdbc:sqlite:" + System.getProperty("bom.db");
    private static final String SH_BT = "Ifc4_SampleHouse";
    private static final String BUILDING_SH = "BUILDING_SH_STD";
    private static final double TOLERANCE = 0.001; // 1mm

    @BeforeEach
    void resetSingleton() {
        PlacementLoader.resetInstance();
    }

    /**
     * Walk BUILDING_SH_STD with an independent PlacementCollectorVisitor
     * and return its placements.
     */
    private List<PlacementLoader.Placement> walkIndependent() throws SQLException {
        try (Connection bomConn = DriverManager.getConnection(BOM_DB)) {
            // R17/R11: Load world origin from BUILDING m_bom.origin_x/y/z
            // (was: I_Element_Extraction MIN(min_x/y/z) — removed)
            double[] worldOrigin = loadBuildingOrigin(bomConn);

            PlacementCollectorVisitor visitor = new PlacementCollectorVisitor(bomConn, SH_BT, worldOrigin);
            BOMWalker walker = new BOMWalker(bomConn);
            walker.walkSelf(BUILDING_SH, List.of(visitor), SH_BT);
            return visitor.getPlacements();
        }
    }

    private double[] loadBuildingOrigin(Connection bomConn) throws SQLException {
        try (PreparedStatement ps = bomConn.prepareStatement(
                "SELECT origin_x, origin_y, origin_z FROM m_bom WHERE bom_id = ?")) {
            ps.setString(1, BUILDING_SH);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new double[]{rs.getDouble(1), rs.getDouble(2), rs.getDouble(3)};
                }
            }
        }
        return new double[]{0, 0, 0};
    }

    // ── W-PCV-1: Placement count parity ──────────────────────────────────────

    @Test
    @DisplayName("W-PCV-1: Visitor placement count == PlacementLoader count for SH")
    void w_pcv_1_count_parity() throws Exception {
        List<PlacementLoader.Placement> visitorPlacements = walkIndependent();
        List<PlacementLoader.Placement> loaderPlacements = PlacementLoader.getInstance().getAll(SH_BT);

        assertFalse(visitorPlacements.isEmpty(), "Visitor must produce placements");
        assertEquals(loaderPlacements.size(), visitorPlacements.size(),
            "Independent visitor count must match PlacementLoader count for " + SH_BT);
    }

    // ── W-PCV-2: Element refs identical (as multiset) ────────────────────────

    @Test
    @DisplayName("W-PCV-2: Element refs identical between visitor walk and PlacementLoader")
    void w_pcv_2_element_refs() throws Exception {
        List<PlacementLoader.Placement> visitorPlacements = walkIndependent();
        List<PlacementLoader.Placement> loaderPlacements = PlacementLoader.getInstance().getAll(SH_BT);

        // Compare as sorted lists (multiset) — elementRef is NOT unique (e.g. chairs)
        List<String> visitorRefs = visitorPlacements.stream()
            .map(PlacementLoader.Placement::elementRef)
            .sorted()
            .toList();
        List<String> loaderRefs = loaderPlacements.stream()
            .map(PlacementLoader.Placement::elementRef)
            .sorted()
            .toList();

        assertEquals(loaderRefs, visitorRefs,
            "Element refs (as sorted multiset) must be identical between independent visitor and PlacementLoader");
    }

    // ── W-PCV-3: World coordinates match within 1mm (index-aligned) ──────────

    @Test
    @DisplayName("W-PCV-3: World coordinates match within 1mm for each element")
    void w_pcv_3_coordinates() throws Exception {
        // Both walks traverse the same BOM in the same order → index-aligned comparison
        List<PlacementLoader.Placement> visitorPlacements = walkIndependent();
        List<PlacementLoader.Placement> loaderPlacements = PlacementLoader.getInstance().getAll(SH_BT);

        assertEquals(loaderPlacements.size(), visitorPlacements.size(),
            "Count must match before coordinate comparison");

        List<String> mismatches = new ArrayList<>();
        for (int i = 0; i < visitorPlacements.size(); i++) {
            PlacementLoader.Placement vp = visitorPlacements.get(i);
            PlacementLoader.Placement lp = loaderPlacements.get(i);

            // Verify same element identity at same index
            assertEquals(lp.elementRef(), vp.elementRef(),
                "Element ref mismatch at index " + i + " — walks not aligned");

            double maxDiff = Math.max(Math.abs(vp.minX()-lp.minX()),
                Math.max(Math.abs(vp.maxX()-lp.maxX()),
                Math.max(Math.abs(vp.minY()-lp.minY()),
                Math.max(Math.abs(vp.maxY()-lp.maxY()),
                Math.max(Math.abs(vp.minZ()-lp.minZ()),
                    Math.abs(vp.maxZ()-lp.maxZ()))))));
            if (maxDiff > TOLERANCE) {
                mismatches.add(String.format("[%d] %s: maxDiff=%.6f", i, vp.elementRef(), maxDiff));
            }
        }
        assertTrue(mismatches.isEmpty(),
            mismatches.size() + " elements exceed 1mm tolerance: " + mismatches);

        // Also verify sub-assembly count (SH has 3 floor STRs — structural, proven by W-BOM-EB-1)
        try (Connection bomConn = DriverManager.getConnection(BOM_DB)) {
            // R17/R11: origin from BUILDING m_bom
            double[] worldOrigin = loadBuildingOrigin(bomConn);
            PlacementCollectorVisitor visitor = new PlacementCollectorVisitor(bomConn, SH_BT, worldOrigin);
            BOMWalker walker = new BOMWalker(bomConn);
            walker.walkSelf(BUILDING_SH, List.of(visitor), SH_BT);
            assertEquals(3, visitor.getSubAssemblyCount(),
                "SH must have 3 sub-assemblies (3 floor STRs) — structural, proven by W-BOM-EB-1");
        }
    }
}
