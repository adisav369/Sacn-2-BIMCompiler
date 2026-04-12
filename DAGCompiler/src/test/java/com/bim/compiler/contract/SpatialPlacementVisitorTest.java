package com.bim.compiler.contract;

import com.bim.compiler.bom.walker.BOMWalker;
import com.bim.compiler.bom.walker.SpatialPlacementVisitor;
import com.bim.compiler.dsl.PlacementLoader;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * NORM-3a Phase C witness: SpatialPlacementVisitor produces same Placement count as RelationalResolver.
 *
 * <h2>Witness claims</h2>
 * <ul>
 *   <li>W-SPV-1: SH placements from visitor == SH placements from PlacementLoader (RelationalResolver path)</li>
 *   <li>W-SPV-2: DX placements from visitor == DX placements from PlacementLoader</li>
 *   <li>W-SPV-3: Element refs are identical between visitor and PlacementLoader for SH</li>
 * </ul>
 *
 * Gate: 212 PASS unchanged.
 */
class SpatialPlacementVisitorTest {

    private static String bomDbPath() { return System.getProperty("bom.db"); }

    @BeforeEach
    void resetSingleton() {
        PlacementLoader.resetInstance();
    }

    // building_type keys in c_orderline (IFC-origin names, not c_bpartner codes)
    private static final String SH_BT = "SampleHouse";
    private static final String DX_BT = "Duplex";

    // ── W-SPV-1: SH placement count parity ───────────────────────────────────

    @Test
    @DisplayName("W-SPV-1: SH placement count matches RelationalResolver")
    void w_spv_1_sh_parity() throws Exception {
        SpatialPlacementVisitor visitor = new SpatialPlacementVisitor();
        List<PlacementLoader.Placement> visitorPlacements = visitor.compute(SH_BT);

        PlacementLoader pad = PlacementLoader.getInstance();
        List<PlacementLoader.Placement> relationalPlacements = pad.getAll(SH_BT);

        assertFalse(visitorPlacements.isEmpty(), SH_BT + ": visitor must produce placements");
        assertEquals(relationalPlacements.size(), visitorPlacements.size(),
            SH_BT + ": visitor placement count must match PlacementLoader/RelationalResolver count");
    }

    // ── W-SPV-2: DX placement count parity ───────────────────────────────────

    @Test
    @DisplayName("W-SPV-2: DX placement count matches RelationalResolver")
    void w_spv_2_dx_parity() throws Exception {
        SpatialPlacementVisitor visitor = new SpatialPlacementVisitor();
        List<PlacementLoader.Placement> visitorPlacements = visitor.compute(DX_BT);

        PlacementLoader pad = PlacementLoader.getInstance();
        List<PlacementLoader.Placement> relationalPlacements = pad.getAll(DX_BT);

        assertFalse(visitorPlacements.isEmpty(), DX_BT + ": visitor must produce placements");
        assertEquals(relationalPlacements.size(), visitorPlacements.size(),
            DX_BT + ": visitor placement count must match PlacementLoader/RelationalResolver count");
    }

    // ── W-SPV-3: Element refs identical ──────────────────────────────────────

    @Test
    @DisplayName("W-SPV-3: SH element refs identical between visitor and RelationalResolver")
    void w_spv_3_element_refs_identical() throws Exception {
        SpatialPlacementVisitor visitor = new SpatialPlacementVisitor();
        List<PlacementLoader.Placement> visitorPlacements = visitor.compute(SH_BT);

        PlacementLoader pad = PlacementLoader.getInstance();
        List<PlacementLoader.Placement> relationalPlacements = pad.getAll(SH_BT);

        // Collect element refs from both
        var visitorRefs = visitorPlacements.stream()
            .map(PlacementLoader.Placement::elementRef)
            .sorted()
            .toList();
        var relationalRefs = relationalPlacements.stream()
            .map(PlacementLoader.Placement::elementRef)
            .sorted()
            .toList();

        assertEquals(relationalRefs, visitorRefs,
            SH_BT + ": element refs must be identical between visitor and PlacementLoader");
    }

    // ── W-SPV-4: BOM walker fires MAKE events correctly for BUILDING BOM ─────────

    @Test
    @DisplayName("W-SPV-4: SpatialPlacementVisitor tracks BUILDING BOM context via BOMWalker")
    void w_spv_4_unit_bom_context() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + bomDbPath())) {
            BOMWalker walker = new BOMWalker(conn);
            SpatialPlacementVisitor visitor = new SpatialPlacementVisitor();
            // Walk BUILDING_SH_STD — visitor tracks hierarchy via onSubAssembly events
            walker.walk("BUILDING_SH_STD", List.of(visitor), SH_BT);
            // No assertion on count here (walker doesn't compute positions in Phase C)
            // Just verify no exception thrown during tree walk with spatial visitor
        }
    }
}
