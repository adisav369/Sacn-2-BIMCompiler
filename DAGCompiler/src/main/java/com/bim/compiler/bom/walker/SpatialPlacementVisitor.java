package com.bim.compiler.bom.walker;

import com.bim.compiler.dsl.PlacementAD;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * NORM-3a Phase C: Visitor that computes element placements (WHERE).
 *
 * <p>Delegates to {@link PlacementAD} for placement data loaded from
 * component_library.db (lod_element_placement).
 *
 * <h2>Architectural context</h2>
 * <p>Placement positions come from extracted IFC coordinates stored in
 * lod_element_placement. The BOM tree walk provides UNIT/FLOOR/SET
 * hierarchy context (tracked via onMake events).
 *
 * <p>Phase D: MAKE events will drive the BomAnchor cascade directly from
 * the tree via VerbStage / PP_Order_Node.
 *
 * @see PlacementAD
 */
public class SpatialPlacementVisitor implements BOMVisitor {

    // Current building being resolved — set before each walk
    private String buildingType;

    // Placements accumulated during this visitor's lifetime
    private final List<PlacementAD.Placement> computed = new ArrayList<>();

    // BOM hierarchy context tracked via onMake events
    private final List<String> unitBomStack   = new ArrayList<>(); // UNIT BOMs seen
    private final List<String> floorBomStack  = new ArrayList<>(); // FLOOR BOMs seen
    private final List<String> makeStack      = new ArrayList<>(); // general MAKE stack

    // ── Factory / context loading ─────────────────────────────────────────────

    /**
     * Compute placements for a building type via PlacementAD (component_library.db).
     *
     * @param buildingType building type (e.g. "Ifc4_SampleHouse", "Ifc2x3_Duplex")
     * @return list of Placement records from lod_element_placement
     */
    public List<PlacementAD.Placement> compute(String buildingType) {
        this.buildingType = buildingType;
        computed.clear();
        List<PlacementAD.Placement> resolved = PlacementAD.getInstance().getAll(buildingType);
        computed.addAll(resolved);
        System.out.printf("[SpatialPlacementVisitor] %s → %d placements (via PlacementAD)%n",
            buildingType, computed.size());
        return List.copyOf(computed);
    }

    /**
     * Get the accumulated placements (for comparison with PlacementAD cache).
     */
    public List<PlacementAD.Placement> getComputedPlacements() {
        return List.copyOf(computed);
    }

    /**
     * Reset for reuse across buildings.
     */
    public void reset() {
        buildingType = null;
        computed.clear();
        unitBomStack.clear();
        floorBomStack.clear();
        makeStack.clear();
    }

    // ── BOMVisitor events ────────────────────────────────────────────────────
    // Phase D: these events will drive the coordinate computation directly.

    @Override
    public void onMake(BOMWalker.NodeContext ctx) {
        if (ctx.bom() == null) return;
        String bomId = ctx.bomId();
        String bomLevel = ctx.bom().getBomLevel();

        // Track BOM hierarchy for Phase D dispatch (logged for traceability)
        makeStack.add(bomId);
        if ("UNIT".equals(bomLevel))  unitBomStack.add(bomId);
        if ("FLOOR".equals(bomLevel)) floorBomStack.add(bomId);
    }

    @Override
    public void onMakeComplete(BOMWalker.NodeContext ctx) {
        if (!makeStack.isEmpty()) {
            makeStack.remove(makeStack.size() - 1);
        }
    }

    @Override
    public void onBuy(BOMWalker.NodeContext ctx) {
        // Phase C: BUY nodes are not needed for placement computation (positions come from
        // c_orderline rules, not from the BOM structure). Phase D will use these to drive
        // ElementRule dispatch directly from the product's ifc_class.
    }

    @Override
    public void onPhantom(BOMWalker.NodeContext ctx) {
        // PHANTOM nodes don't contribute to spatial placement — no-op
    }

    @Override
    public void flush(Connection outputConn) throws SQLException {
        // Phase C: flush is a no-op — placements computed via compute(buildingType)
        // In Phase D, this will write elements_meta + element_transforms atomically.
        System.out.printf("[SpatialPlacementVisitor] flush: %d placements ready for Phase D write%n",
            computed.size());
    }
}
