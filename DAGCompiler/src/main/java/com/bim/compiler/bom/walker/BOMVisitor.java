package com.bim.compiler.bom.walker;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * NORM-3a: Visitor interface for a single homogeneous BOM tree walk.
 *
 * <p>After NORM-2, every {@code m_bom_line} has a single {@code child_product_id → M_Product}
 * FK and a {@code component_type} discriminator (BUY/MAKE/PHANTOM). The tree is homogeneous
 * — one walker fires events, multiple visitors accumulate results independently.
 *
 * <p>Replaces the two independent BOM passes:
 * <ul>
 *   <li>{@code BOMAssemblerAD} (WHAT — writes element_assemblies)</li>
 *   <li>{@code RelationalResolver} (WHERE — writes coordinates)</li>
 * </ul>
 *
 * <p>iDempiere analogy: {@code IDocAction} event chain — each visitor is one doctrine
 * (assembly structure, spatial placement) applied in sequence during the same document walk.
 */
public interface BOMVisitor {

    /**
     * Assembly node (MAKE). Called before walking children.
     * Use to push assembly context (e.g. current BOM = current group).
     */
    void onMake(BOMWalker.NodeContext ctx);

    /**
     * BOM tree entry complete (MAKE). Called after all children processed.
     * Use to pop assembly context and flush accumulated memberships for this group.
     */
    void onMakeComplete(BOMWalker.NodeContext ctx);

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
