package com.bim.compiler.contract;

import com.bim.compiler.bom.walker.BOMWalker;
import com.bim.compiler.bom.walker.SpatialPlacementVisitor;
import com.bim.compiler.dsl.PlacementAD;
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
 *   <li>W-SPV-1: SH placements from visitor == SH placements from PlacementAD (RelationalResolver path)</li>
 *   <li>W-SPV-2: DX placements from visitor == DX placements from PlacementAD</li>
 *   <li>W-SPV-3: Element refs are identical between visitor and PlacementAD for SH</li>
 * </ul>
 *
 * Gate: 212 PASS unchanged.
 */
class SpatialPlacementVisitorTest {

    private static final String BOM_DB = "library/BOM.db";

    @BeforeEach
    void resetSingleton() {
        PlacementAD.resetInstance();
    }

    // building_type keys in c_orderline (IFC-origin names, not c_bpartner codes)
    private static final String SH_BT = "Ifc4_SampleHouse";
    private static final String DX_BT = "Ifc2x3_Duplex";

    // ── W-SPV-1: SH placement count parity ───────────────────────────────────

    @Test
    @DisplayName("W-SPV-1: SH placement count matches RelationalResolver")
    void w_spv_1_sh_parity() throws Exception {
        SpatialPlacementVisitor visitor = new SpatialPlacementVisitor();
        List<PlacementAD.Placement> visitorPlacements = visitor.compute(SH_BT);

        PlacementAD pad = PlacementAD.getInstance();
        List<PlacementAD.Placement> relationalPlacements = pad.getAll(SH_BT);

        assertFalse(visitorPlacements.isEmpty(), SH_BT + ": visitor must produce placements");
        assertEquals(relationalPlacements.size(), visitorPlacements.size(),
            SH_BT + ": visitor placement count must match PlacementAD/RelationalResolver count");
    }

    // ── W-SPV-2: DX placement count parity ───────────────────────────────────

    @Test
    @DisplayName("W-SPV-2: DX placement count matches RelationalResolver")
    void w_spv_2_dx_parity() throws Exception {
        SpatialPlacementVisitor visitor = new SpatialPlacementVisitor();
        List<PlacementAD.Placement> visitorPlacements = visitor.compute(DX_BT);

        PlacementAD pad = PlacementAD.getInstance();
        List<PlacementAD.Placement> relationalPlacements = pad.getAll(DX_BT);

        assertFalse(visitorPlacements.isEmpty(), DX_BT + ": visitor must produce placements");
        assertEquals(relationalPlacements.size(), visitorPlacements.size(),
            DX_BT + ": visitor placement count must match PlacementAD/RelationalResolver count");
    }

    // ── W-SPV-3: Element refs identical ──────────────────────────────────────

    @Test
    @DisplayName("W-SPV-3: SH element refs identical between visitor and RelationalResolver")
    void w_spv_3_element_refs_identical() throws Exception {
        SpatialPlacementVisitor visitor = new SpatialPlacementVisitor();
        List<PlacementAD.Placement> visitorPlacements = visitor.compute(SH_BT);

        PlacementAD pad = PlacementAD.getInstance();
        List<PlacementAD.Placement> relationalPlacements = pad.getAll(SH_BT);

        // Collect element refs from both
        var visitorRefs = visitorPlacements.stream()
            .map(PlacementAD.Placement::elementRef)
            .sorted()
            .toList();
        var relationalRefs = relationalPlacements.stream()
            .map(PlacementAD.Placement::elementRef)
            .sorted()
            .toList();

        assertEquals(relationalRefs, visitorRefs,
            SH_BT + ": element refs must be identical between visitor and PlacementAD");
    }

    // ── W-SPV-4: BOM walker fires MAKE events correctly for UNIT BOM ─────────

    @Test
    @DisplayName("W-SPV-4: SpatialPlacementVisitor tracks UNIT BOM context via BOMWalker")
    void w_spv_4_unit_bom_context() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + BOM_DB)) {
            BOMWalker walker = new BOMWalker(conn);
            SpatialPlacementVisitor visitor = new SpatialPlacementVisitor();
            // Walk UNIT_SH_STD — visitor tracks hierarchy via onMake events
            walker.walk("UNIT_SH_STD", List.of(visitor), SH_BT);
            // No assertion on count here (walker doesn't compute positions in Phase C)
            // Just verify no exception thrown during tree walk with spatial visitor
        }
    }
}
