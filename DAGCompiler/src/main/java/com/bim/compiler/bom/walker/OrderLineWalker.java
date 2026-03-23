package com.bim.compiler.bom.walker;

import com.bim.ormsandbox.po.MBOM;
import com.bim.ormsandbox.po.MBOMLine;
import com.bim.ormsandbox.po.MProduct;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Walks a C_OrderLine tree and fires {@link BOMVisitor} events.
 *
 * <p>Drop-in replacement for {@link BOMWalker} when C_OrderLine data
 * exists in the compile DB. The C_OrderLine tree defines the structure
 * (which products, where); MBOMLine attributes (verb_ref, rotation,
 * material, storey, element_ref) are loaded via the {@code bom_child_id}
 * FK join-back.
 *
 * <p>This enables product swaps on C_OrderLine without touching m_bom:
 * {@code family_ref} changes (new product), {@code bom_child_id} stays
 * (original placement data from m_bom_line).
 *
 * <p>// Implementing S60_ERP_ALIGNMENT.md §3 — Witness: W-S60-WALK
 *
 * @see BOMWalker      original m_bom-based walker
 * @see com.bim.compiler.bom.BomDropper  creates the C_OrderLine tree
 */
public class OrderLineWalker {

    private final Connection compileDb;  // has m_bom, m_bom_line, C_Order, C_OrderLine
    private final Connection compConn;   // disc_validation.db (M_Product master catalog)
    private static final int MAX_DEPTH = 20;

    public OrderLineWalker(Connection compileDb, Connection compConn) {
        this.compileDb = compileDb;
        this.compConn = compConn;
    }

    /**
     * Walk a C_Order's OrderLine tree, firing BOMVisitor events.
     *
     * <p>Equivalent to {@link BOMWalker#walkSelf}: wraps the root in
     * synthetic onSubAssembly/onSubAssemblyComplete at level=-1.
     *
     * @param orderId      C_Order_ID to walk
     * @param visitors     visitors to fire events on
     * @param buildingType building type context string (e.g. "Ifc4_SampleHouse")
     */
    public void walkOrder(String orderId, List<BOMVisitor> visitors, String buildingType)
            throws SQLException {
        // Find root C_OrderLine (Parent_OrderLine_ID IS NULL)
        OrderLineRow root = findRoot(orderId);
        if (root == null) {
            System.err.printf("[OrderLineWalker] No root C_OrderLine for order %s%n", orderId);
            return;
        }

        // Load BUILDING BOM for root context
        MBOM rootBom = loadBom(root.familyRef);
        if (rootBom == null) {
            System.err.printf("[OrderLineWalker] No BOM for root family_ref %s%n", root.familyRef);
            return;
        }

        // Synthetic root context (same as BOMWalker.walkSelf: level=-1, line=null)
        BOMWalker.NodeContext rootCtx = new BOMWalker.NodeContext(
                null, null, rootBom, -1, buildingType);
        for (BOMVisitor v : visitors) v.onSubAssembly(rootCtx);

        // Walk children
        walkChildren(orderId, root.lineId, rootBom, visitors, buildingType, 0);

        for (BOMVisitor v : visitors) v.onSubAssemblyComplete(rootCtx);
    }

    // ── Private traversal ────────────────────────────────────────────────────

    private void walkChildren(String orderId, int parentLineId, MBOM parentBom,
                               List<BOMVisitor> visitors, String buildingType, int level)
            throws SQLException {
        if (level > MAX_DEPTH) {
            System.err.printf("[OrderLineWalker] MAX_DEPTH exceeded at parent %d%n", parentLineId);
            return;
        }

        List<OrderLineRow> children = findChildren(orderId, parentLineId);

        for (OrderLineRow row : children) {
            // Load MBOMLine via bom_child_id (join-back for attributes)
            MBOMLine line = null;
            if (row.bomChildId > 0) {
                line = new MBOMLine(compileDb);
                if (!line.load(row.bomChildId)) {
                    line = null;
                }
            }

            if ("LEAF".equals(row.hostType)) {
                // Leaf — load M_Product from disc_validation.db
                MProduct product = MProduct.get(compConn, row.familyRef);
                BOMWalker.NodeContext ctx = new BOMWalker.NodeContext(
                        product, line, parentBom, level, buildingType, row.discipline);

                if (product == null) {
                    // Dangling reference — warn and skip (same as BOMWalker)
                    System.err.printf("[OrderLineWalker] Dangling leaf: %s in order %s — "
                            + "no M_Product. Skipping.%n", row.familyRef, orderId);
                } else {
                    for (BOMVisitor v : visitors) v.onLeaf(ctx);
                }

            } else {
                // Sub-assembly (BUILDING, FLOOR, ROOM)
                MBOM childBom = loadBom(row.familyRef);
                if (childBom == null) {
                    System.err.printf("[OrderLineWalker] No BOM for assembly %s — skipping%n",
                            row.familyRef);
                    continue;
                }

                MProduct product = MProduct.getAssembly(compConn, row.familyRef);
                BOMWalker.NodeContext ctx = new BOMWalker.NodeContext(
                        product, line, childBom, level, buildingType, row.discipline);

                for (BOMVisitor v : visitors) v.onSubAssembly(ctx);
                walkChildren(orderId, row.lineId, childBom, visitors, buildingType, level + 1);
                for (BOMVisitor v : visitors) v.onSubAssemblyComplete(ctx);
            }
        }
    }

    // ── Data access ──────────────────────────────────────────────────────────

    private OrderLineRow findRoot(String orderId) throws SQLException {
        String sql = "SELECT C_OrderLine_ID, family_ref, host_type, bom_child_id, Discipline "
                   + "FROM C_OrderLine WHERE C_Order_ID = ? AND Parent_OrderLine_ID IS NULL "
                   + "AND IsActive = 1 ORDER BY Line LIMIT 1";
        try (PreparedStatement ps = compileDb.prepareStatement(sql)) {
            ps.setString(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new OrderLineRow(
                        rs.getInt("C_OrderLine_ID"),
                        rs.getString("family_ref"),
                        rs.getString("host_type"),
                        rs.getInt("bom_child_id"),
                        rs.getString("Discipline"));
            }
        }
    }

    private List<OrderLineRow> findChildren(String orderId, int parentLineId) throws SQLException {
        String sql = "SELECT C_OrderLine_ID, family_ref, host_type, bom_child_id, Discipline "
                   + "FROM C_OrderLine WHERE C_Order_ID = ? AND Parent_OrderLine_ID = ? "
                   + "AND IsActive = 1 ORDER BY Line";
        List<OrderLineRow> rows = new ArrayList<>();
        try (PreparedStatement ps = compileDb.prepareStatement(sql)) {
            ps.setString(1, orderId);
            ps.setInt(2, parentLineId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new OrderLineRow(
                            rs.getInt("C_OrderLine_ID"),
                            rs.getString("family_ref"),
                            rs.getString("host_type"),
                            rs.getInt("bom_child_id"),
                            rs.getString("Discipline")));
                }
            }
        }
        return rows;
    }

    private MBOM loadBom(String bomId) throws SQLException {
        MBOM bom = new MBOM(compileDb);
        return bom.load(bomId) ? bom : null;
    }

    private record OrderLineRow(int lineId, String familyRef, String hostType, int bomChildId,
                                    String discipline) {}
}
