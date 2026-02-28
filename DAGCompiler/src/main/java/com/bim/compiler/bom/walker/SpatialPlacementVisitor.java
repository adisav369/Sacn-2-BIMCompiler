package com.bim.compiler.bom.walker;

import com.bim.compiler.dsl.PlacementAD;
import com.bim.compiler.dsl.RelationalResolver;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * NORM-3a Phase C: Visitor that computes element placements (WHERE).
 *
 * <p>Ports {@link RelationalResolver} into the {@link BOMVisitor} pattern.
 * Phase C is a PARALLEL RUN — the visitor computes the same Placement records
 * as RelationalResolver.resolve() so both outputs can be compared for parity.
 *
 * <h2>Architectural context</h2>
 * <p>Placement positions come from {@code c_orderline} rules (ElementRule records),
 * NOT from the BOM tree structure itself. The BOM tree walk provides UNIT/FLOOR/SET
 * hierarchy context (tracked via onMake events), but the actual coordinate computation
 * delegates to RelationalResolver's resolution logic which reads c_orderline.
 *
 * <p>The walker's contribution: in Phase D, MAKE events will drive the BomAnchor
 * cascade (UNIT→FLOOR→SET→room dispatch) directly from the tree, eliminating the
 * separate loadBomChain() pre-pass in RelationalResolver. Phase C establishes parity
 * before that restructuring.
 *
 * <h2>Phase C parallel run</h2>
 * <p>SpatialPlacementVisitor.compute(buildingType) returns the same List&lt;Placement&gt;
 * as RelationalResolver.resolve(buildingType). A witness test asserts parity.
 *
 * @see RelationalResolver
 * @see PlacementAD
 */
public class SpatialPlacementVisitor implements BOMVisitor {

    // Current building being resolved — set before each walk
    private String buildingType;

    // Placements accumulated during this visitor's lifetime
    private final List<PlacementAD.Placement> computed = new ArrayList<>();

    // BOM hierarchy context tracked via onMake events
    // These are used in Phase D to replace RelationalResolver's BOM chain pre-load
    private final List<String> unitBomStack   = new ArrayList<>(); // UNIT BOMs seen
    private final List<String> floorBomStack  = new ArrayList<>(); // FLOOR BOMs seen
    private final List<String> makeStack      = new ArrayList<>(); // general MAKE stack

    // ── Factory / context loading ─────────────────────────────────────────────

    /**
     * Compute placements for a building type using the same ResolutionContext as
     * RelationalResolver. This is the Phase C entry point.
     *
     * <p>Delegates to RelationalResolver for coordinate computation so that Phase C
     * establishes a verified parity baseline before Phase D restructures the logic.
     *
     * @param buildingType building type (e.g. "SH", "DX")
     * @return list of Placement records (identical to RelationalResolver.resolve())
     */
    public List<PlacementAD.Placement> compute(String buildingType) {
        this.buildingType = buildingType;
        computed.clear();
        // Delegate to RelationalResolver — same result, Phase C parity baseline
        List<PlacementAD.Placement> resolved = RelationalResolver.getInstance().resolve(buildingType);
        computed.addAll(resolved);
        System.out.printf("[SpatialPlacementVisitor] %s → %d placements (via RelationalResolver)%n",
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
    // Phase C: walker events are received but computation delegates to RelationalResolver.
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
