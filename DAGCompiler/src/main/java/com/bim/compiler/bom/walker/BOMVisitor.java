package com.bim.compiler.bom.walker;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Visitor interface for BOM tree traversal.
 *
 * <p>The walker fires events for each node in the BOM hierarchy.
 * Multiple visitors accumulate independent results (assembly structure,
 * spatial placement) during a single tree walk.
 *
 * <p>Three event types match the three node kinds:
 * <ul>
 *   <li>{@code onSubAssembly/onSubAssemblyComplete} — structural node
 *       (child_product_id matches a bom_id → recurse deeper)</li>
 *   <li>{@code onBuy} — leaf with geometry from component_library.db</li>
 *   <li>{@code onPhantom} — filler/spacer, stripped at output</li>
 * </ul>
 *
 * <p>iDempiere analogy: {@code IDocAction} event chain — each visitor is one doctrine
 * (assembly structure, spatial placement) applied in sequence during the same document walk.
 */
public interface BOMVisitor {

    /**
     * Sub-assembly node — child_product_id matches a bom_id, walker recurses.
     * Called before walking children.
     * Use to push assembly context (e.g. anchor stack, storey tracking).
     */
    void onSubAssembly(BOMWalker.NodeContext ctx);

    /**
     * Sub-assembly complete — all children processed.
     * Use to pop assembly context and flush accumulated results for this group.
     */
    void onSubAssemblyComplete(BOMWalker.NodeContext ctx);

    /**
     * Leaf with geometry (BUY). The element has intrinsic dimensions from M_Product.
     * Use to record placement or link to assembly.
     */
    void onBuy(BOMWalker.NodeContext ctx);

    /**
     * Inline buffer/spacer (PHANTOM). No output record. Space absorbed by parent AABB.
     * Default no-op — most visitors ignore phantom nodes.
     */
    default void onPhantom(BOMWalker.NodeContext ctx) {}

    /**
     * Flush accumulated writes after the full tree walk completes.
     * Called once per rootBomId walk. Use for batch INSERTs.
     *
     * @param outputConn connection to the output database
     */
    default void flush(Connection outputConn) throws SQLException {}
}
