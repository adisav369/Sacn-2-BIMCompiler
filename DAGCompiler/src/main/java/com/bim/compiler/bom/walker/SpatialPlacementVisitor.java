package com.bim.compiler.bom.walker;

import com.bim.compiler.dsl.PlacementLoader;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * NORM-3a Phase C: Visitor that computes element placements (WHERE).
 *
 * <p>Delegates to {@link PlacementLoader} for placement data loaded from
 * BOM.db (m_bom_line with instance columns, P0.2).
 *
 * <h2>Architectural context</h2>
 * <p>Placement positions come from extracted IFC coordinates stored in
 * m_bom_line (backfilled from IFC extraction archive). The BOM tree walk
 * provides UNIT/FLOOR/SET hierarchy context (tracked via onMake events).
 *
 * <p>Phase D: MAKE events will drive the BomAnchor cascade directly from
 * the tree via VerbStage / PP_Order_Node.
 *
 * @see PlacementLoader
 */
public class SpatialPlacementVisitor implements BOMVisitor {

    // Current building being resolved — set before each walk
    private String buildingType;

    // Placements accumulated during this visitor's lifetime
    private final List<PlacementLoader.Placement> computed = new ArrayList<>();

    // BOM hierarchy context tracked via onMake events
    private final List<String> unitBomStack   = new ArrayList<>(); // UNIT BOMs seen
    private final List<String> floorBomStack  = new ArrayList<>(); // FLOOR BOMs seen
    private final List<String> makeStack      = new ArrayList<>(); // general MAKE stack

    // ── Factory / context loading ─────────────────────────────────────────────

    /**
     * Compute placements for a building type via PlacementLoader (BOM.db).
     *
     * @param buildingType building type (e.g. "Ifc4_SampleHouse", "Ifc2x3_Duplex")
     * @return list of Placement records from m_bom_line
     */
    public List<PlacementLoader.Placement> compute(String buildingType) {
        this.buildingType = buildingType;
        computed.clear();
        List<PlacementLoader.Placement> resolved = PlacementLoader.getInstance().getAll(buildingType);
        computed.addAll(resolved);
        System.out.printf("[SpatialPlacementVisitor] %s → %d placements (via PlacementLoader)%n",
            buildingType, computed.size());
        return List.copyOf(computed);
    }

    /**
     * Get the accumulated placements (for comparison with PlacementLoader cache).
     */
    public List<PlacementLoader.Placement> getComputedPlacements() {
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
        if ("BUILDING".equals(bomLevel))  unitBomStack.add(bomId);
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
        // PHANTOM = gap filler in the BOM (packed-box principle). No spatial
        // placement output — stripped at compile time, like foam removed from
        // a furniture box. Content stays at tack positions; fillers disappear.
    }

    @Override
    public void flush(Connection outputConn) throws SQLException {
        // Phase C: flush is a no-op — placements computed via compute(buildingType)
        // In Phase D, this will write elements_meta + element_transforms atomically.
        System.out.printf("[SpatialPlacementVisitor] flush: %d placements ready for Phase D write%n",
            computed.size());
    }
}
