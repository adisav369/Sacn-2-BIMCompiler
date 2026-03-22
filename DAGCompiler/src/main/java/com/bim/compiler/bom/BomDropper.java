package com.bim.compiler.bom;

import com.bim.compiler.dsl.BuildingRegistry.BuildingEntry;
import com.bim.ormsandbox.po.MBOM;
import com.bim.ormsandbox.po.MBOMLine;

import java.sql.*;
import java.util.List;

/**
 * BOM Drop utility for the compilation pipeline.
 *
 * <p>Walks {@code m_bom}/{@code m_bom_line} and creates a matching
 * {@code C_Order} + {@code C_OrderLine} tree in the compile DB.
 * Same logic as {@code DesignerAPIImpl.explodeBomTree()} but writes
 * directly to the compile DB (no WorkOutputDAO dependency).
 *
 * <p>Each C_OrderLine stores {@code bom_child_id} — the FK back to
 * {@code m_bom_line.bom_child_id} — so that {@code OrderLineWalker}
 * can load the full MBOMLine attributes (verb_ref, rotation, material,
 * storey, element_ref) during compilation.
 *
 * <p>// Implementing S60_ERP_ALIGNMENT.md §2 — Witness: W-S60-DROP
 *
 * @see com.bim.compiler.bom.walker.OrderLineWalker
 */
public class BomDropper {

    private static final int MAX_DEPTH = 20;

    /**
     * Explode a building's BOM tree into C_Order + C_OrderLine in the compile DB.
     *
     * @param compileDb  connection to the compile DB (has m_bom, m_bom_line, C_Order, C_OrderLine)
     * @param entry      building entry from C_DocType registry
     * @return number of leaf elements (sum of qty across all LEAF C_OrderLines)
     */
    public static int drop(Connection compileDb, BuildingEntry entry) throws SQLException {
        // Find the BUILDING BOM for this entry's DocSubType + DocBaseType
        String buildingBomId = findBuildingBom(compileDb, entry);
        if (buildingBomId == null) {
            System.err.printf("[BomDropper] No BUILDING BOM for %s (%s/%s) — skipping%n",
                    entry.id(), entry.docBaseType(), entry.docSubType());
            return 0;
        }

        // Create C_Order
        String orderId = entry.docTypeId();  // RE_SH, CO_TE, etc.
        createOrder(compileDb, orderId, entry);

        // Explode BOM tree → C_OrderLine
        int[] leafCount = {0};
        int[] lineSeq = {0};
        explode(compileDb, orderId, buildingBomId, 0, "BUILDING",
                null, 0, leafCount, lineSeq);

        System.out.printf("[BomDropper] %s → %d leaves (order=%s, bom=%s)%n",
                entry.id(), leafCount[0], orderId, buildingBomId);
        return leafCount[0];
    }

    /**
     * Find the BUILDING BOM matching this entry's DocBaseType + DocSubType.
     */
    private static String findBuildingBom(Connection conn, BuildingEntry entry) throws SQLException {
        String sql = "SELECT bom_id FROM m_bom "
                   + "WHERE bom_type = 'BUILDING' AND doc_base_type = ? AND doc_sub_type = ? "
                   + "AND is_active = 1 ORDER BY seq_no LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entry.docBaseType());
            ps.setString(2, entry.docSubType());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    /**
     * Create C_Order row for this compilation.
     */
    private static void createOrder(Connection conn, String orderId, BuildingEntry entry)
            throws SQLException {
        // Delete any existing order (re-drop is idempotent)
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM C_OrderLine WHERE C_Order_ID = '" + orderId + "'");
            stmt.execute("DELETE FROM C_Order WHERE C_Order_ID = '" + orderId + "'");
        }

        String sql = "INSERT INTO C_Order (C_Order_ID, C_DocType_ID, Name, DocStatus, "
                   + "aabb_width_mm, aabb_depth_mm, aabb_height_mm) "
                   + "VALUES (?, ?, ?, 'DR', ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, orderId);
            ps.setString(2, entry.docTypeId());
            ps.setString(3, entry.name());
            ps.setDouble(4, entry.aabbWidthMm());
            ps.setDouble(5, entry.aabbDepthMm());
            ps.setDouble(6, entry.aabbHeightMm());
            ps.executeUpdate();
        }
    }

    /**
     * Recursively explode a BOM into C_OrderLine rows.
     *
     * <p>Structural dispatch (same as BOMWalker — component_type NOT checked):
     * <ol>
     *   <li>Sub-assembly (child_product_id matches a bom_id) → recurse</li>
     *   <li>PHANTOM → skip (only component_type that matters)</li>
     *   <li>Otherwise → leaf with geometry</li>
     * </ol>
     *
     * @return the C_OrderLine_ID of the inserted node (auto-generated)
     */
    private static int explode(Connection conn, String orderId, String bomId,
                                int parentLineId, String hostType, String bomCategory,
                                int depth, int[] leafCount, int[] lineSeq)
            throws SQLException {
        if (depth > MAX_DEPTH) {
            System.err.printf("[BomDropper] MAX_DEPTH exceeded at BOM %s%n", bomId);
            return 0;
        }

        // Load BOM for AABB
        MBOM bom = new MBOM(conn);
        if (!bom.load(bomId)) return 0;

        if (bomCategory == null) bomCategory = bom.getBomCategory();

        // Insert C_OrderLine for this assembly node (bom_child_id=NULL for root)
        lineSeq[0] += 10;
        int lineId = insertLine(conn, orderId, parentLineId == 0 ? null : parentLineId,
                lineSeq[0], bomId, hostType, bomCategory, 0,  // bom_child_id=NULL for assemblies at root
                0, 0, 0,
                bom.getAabbWidthMm(), bom.getAabbDepthMm(), bom.getAabbHeightMm(), 1);

        // Walk children
        List<MBOMLine> lines = MBOMLine.getByBom(conn, bomId);
        for (MBOMLine line : lines) {
            String childProductId = line.getChildProductId();
            if (childProductId == null) continue;

            // Check if child is a sub-assembly BOM
            MBOM childBom = new MBOM(conn);
            boolean isBom = childBom.load(childProductId);

            if (isBom) {
                // Sub-assembly — recurse
                String childHostType = deriveHostType(depth + 1);
                explodeAssembly(conn, orderId, childProductId,
                        lineId, childHostType, childBom.getBomCategory(),
                        depth + 1, leafCount, lineSeq, line.getBomChildId());

            } else if ("PHANTOM".equals(line.getComponentType())) {
                // PHANTOM — skip (only component_type that matters)
                continue;

            } else {
                // Leaf product — structural detection: no matching bom_id = leaf.
                // component_type is NOT checked (same as BOMWalker).
                int qty = line.getQty();
                lineSeq[0] += 10;
                insertLine(conn, orderId, lineId, lineSeq[0],
                        childProductId, "LEAF", bomCategory, line.getBomChildId(),
                        line.getDx(), line.getDy(), line.getDz(),
                        line.getAllocatedWidthMm(), line.getAllocatedDepthMm(),
                        line.getAllocatedHeightMm(), qty);
                leafCount[0] += qty;
            }
        }

        return lineId;
    }

    /**
     * Explode a sub-assembly BOM, storing the parent MAKE line's bom_child_id
     * on the assembly's C_OrderLine.
     */
    private static int explodeAssembly(Connection conn, String orderId, String bomId,
                                        int parentLineId, String hostType, String bomCategory,
                                        int depth, int[] leafCount, int[] lineSeq,
                                        int makeBomChildId) throws SQLException {
        if (depth > MAX_DEPTH) return 0;

        MBOM bom = new MBOM(conn);
        if (!bom.load(bomId)) return 0;

        if (bomCategory == null) bomCategory = bom.getBomCategory();

        // Insert assembly C_OrderLine with the MAKE line's bom_child_id
        lineSeq[0] += 10;
        int lineId = insertLine(conn, orderId, parentLineId, lineSeq[0],
                bomId, hostType, bomCategory, makeBomChildId,
                0, 0, 0,  // assembly-level offsets are 0 (tack is on the MAKE line)
                bom.getAabbWidthMm(), bom.getAabbDepthMm(), bom.getAabbHeightMm(), 1);

        // Walk children (same logic as explode)
        List<MBOMLine> lines = MBOMLine.getByBom(conn, bomId);
        for (MBOMLine line : lines) {
            String childProductId = line.getChildProductId();
            if (childProductId == null) continue;

            MBOM childBom = new MBOM(conn);
            boolean isBom = childBom.load(childProductId);

            if (isBom) {
                String childHostType = deriveHostType(depth + 1);
                explodeAssembly(conn, orderId, childProductId, lineId,
                        childHostType, childBom.getBomCategory(),
                        depth + 1, leafCount, lineSeq, line.getBomChildId());

            } else if ("PHANTOM".equals(line.getComponentType())) {
                // PHANTOM — skip (only component_type that matters)
                continue;

            } else {
                // Leaf — structural detection, component_type not checked
                int qty = line.getQty();
                lineSeq[0] += 10;
                insertLine(conn, orderId, lineId, lineSeq[0],
                        childProductId, "LEAF", bomCategory, line.getBomChildId(),
                        line.getDx(), line.getDy(), line.getDz(),
                        line.getAllocatedWidthMm(), line.getAllocatedDepthMm(),
                        line.getAllocatedHeightMm(), qty);
                leafCount[0] += qty;
            }
        }

        return lineId;
    }

    /**
     * Insert a single C_OrderLine row and return its auto-generated ID.
     */
    private static int insertLine(Connection conn, String orderId, Integer parentLineId,
                                   int lineSeq, String familyRef, String hostType,
                                   String bomCategory, int bomChildId,
                                   double dx, double dy, double dz,
                                   double widthMm, double depthMm, double heightMm,
                                   int qty) throws SQLException {
        String sql = "INSERT INTO C_OrderLine "
                   + "(C_Order_ID, Parent_OrderLine_ID, Line, family_ref, host_type, "
                   + " bom_category, bom_child_id, dx, dy, dz, "
                   + " aabb_width_mm, aabb_depth_mm, aabb_height_mm, M_Product_ID, Qty) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, orderId);
            if (parentLineId != null) ps.setInt(2, parentLineId);
            else ps.setNull(2, Types.INTEGER);
            ps.setInt(3, lineSeq);
            ps.setString(4, familyRef);
            ps.setString(5, hostType);
            ps.setString(6, bomCategory);
            if (bomChildId > 0) ps.setInt(7, bomChildId);
            else ps.setNull(7, Types.INTEGER);
            ps.setDouble(8, dx);
            ps.setDouble(9, dy);
            ps.setDouble(10, dz);
            ps.setDouble(11, widthMm);
            ps.setDouble(12, depthMm);
            ps.setDouble(13, heightMm);
            ps.setString(14, familyRef);  // M_Product_ID = family_ref
            ps.setInt(15, qty);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : 0;
            }
        }
    }

    private static String deriveHostType(int depth) {
        return switch (depth) {
            case 0 -> "BUILDING";
            case 1 -> "FLOOR";
            default -> "ROOM";
        };
    }
}
