package com.bim.designer.dao;

import com.bim.backoffice.model.DesignBBox;
import com.bim.designer.api.DesignerAPI.VariantInfo;

import com.bim.orm.BIMLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO for work_output.db — the BIM Designer's design workspace.
 *
 * <p>Implements the three core actions from BIM_Designer.md §17.10:
 * <ul>
 *   <li><b>Save</b> — create sub-C_Order + C_OrderLine rows + W_Variant pointer</li>
 *   <li><b>Recall</b> — read C_OrderLine from a sub-C_Order, return as DesignBBox</li>
 *   <li><b>listVariants</b> — query W_Variant for the variant list UI</li>
 * </ul>
 *
 * <p>Schema: {@code migration/W001_work_output_schema.sql} (append-only).
 * Connection lifecycle: caller owns the connection (pass to constructor).
 *
 * // Implementing G4_SRS §2 — Witness: W-WO-DAO-1
 */
public class WorkOutputDAO {

    private static final String TAG = "WorkOutputDAO";

    private final Connection conn;

    public WorkOutputDAO(Connection conn) {
        this.conn = conn;
    }

    // ── Schema initialisation ────────────────────────────────────────

    /**
     * Run W001 migration DDL to create work_output.db schema.
     * Safe to call repeatedly (CREATE TABLE IF NOT EXISTS).
     */
    public void initSchema() throws SQLException {
        try {
            String ddl = Files.readString(
                    Path.of("migration/W001_work_output_schema.sql"));
            executeDDL(ddl);
        } catch (IOException e) {
            // Fallback: inline essential tables for test/embedded use
            BIMLogger.warn(TAG, "W001 migration file not found, using inline DDL");
            executeDDL(INLINE_DDL);
        }
        // W002: Add M_Product_ID to C_OrderLine (iDempiere alignment)
        // ALTER TABLE ADD COLUMN is not idempotent in SQLite — catch duplicate error
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE C_OrderLine ADD COLUMN M_Product_ID TEXT");
        } catch (SQLException e) {
            // "duplicate column name" = already applied — safe to ignore
            if (!e.getMessage().contains("duplicate column")) throw e;
        }
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("UPDATE C_OrderLine SET M_Product_ID = family_ref WHERE M_Product_ID IS NULL");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_orderline_product ON C_OrderLine(M_Product_ID)");
        } catch (SQLException ignored) {
            // Backfill/index may fail on empty table — safe to ignore
        }
        // W003: Add Discipline to C_OrderLine (S60 ERP alignment)
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE C_OrderLine ADD COLUMN Discipline TEXT DEFAULT 'ARC'");
        } catch (SQLException e) {
            if (!e.getMessage().contains("duplicate column")) throw e;
        }
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_orderline_discipline ON C_OrderLine(Discipline)");
        } catch (SQLException ignored) {}
        // W003: Add Jurisdiction + OccupancyClass to C_Order
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE C_Order ADD COLUMN Jurisdiction TEXT DEFAULT 'MY'");
        } catch (SQLException e) {
            if (!e.getMessage().contains("duplicate column")) throw e;
        }
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE C_Order ADD COLUMN OccupancyClass TEXT");
        } catch (SQLException e) {
            if (!e.getMessage().contains("duplicate column")) throw e;
        }
        // W004: Add proposal_status to C_OrderLine (Session A — addDiscipline)
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE C_OrderLine ADD COLUMN proposal_status TEXT DEFAULT 'ACCEPTED'");
        } catch (SQLException e) {
            if (!e.getMessage().contains("duplicate column")) throw e;
        }
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_orderline_proposal ON C_OrderLine(proposal_status)");
        } catch (SQLException ignored) {}
    }

    /**
     * Run DDL from a string. Strips all SQL comments, splits on semicolons.
     */
    public void executeDDL(String ddl) throws SQLException {
        // Strip all single-line comments first, then split
        String cleaned = ddl.replaceAll("--[^\n]*", "");
        try (Statement stmt = conn.createStatement()) {
            for (String sql : cleaned.split(";")) {
                String trimmed = sql.trim();
                if (trimmed.isEmpty()) continue;
                stmt.execute(trimmed);
            }
        }
    }

    // ── Master C_Order ───────────────────────────────────────────────

    /**
     * Ensure a master C_Order exists for this building. Creates if absent.
     *
     * @return the C_Order_ID (= buildingId for master)
     */
    public String ensureMasterOrder(String buildingId, String docTypeId,
                                     double siteWidthMm, double siteDepthMm,
                                     double siteHeightMm) throws SQLException {
        // Check if master exists
        String check = "SELECT C_Order_ID FROM C_Order WHERE C_Order_ID = ? AND Parent_Order_ID IS NULL";
        try (PreparedStatement ps = conn.prepareStatement(check)) {
            ps.setString(1, buildingId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(1);
            }
        }

        // Create master
        String insert = """
                INSERT INTO C_Order (C_Order_ID, Parent_Order_ID, C_DocType_ID, Name,
                    DocStatus, aabb_width_mm, aabb_depth_mm, aabb_height_mm)
                VALUES (?, NULL, ?, ?, 'IP', ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(insert)) {
            ps.setString(1, buildingId);
            ps.setString(2, docTypeId != null ? docTypeId : "GENERATIVE");
            ps.setString(3, buildingId);
            ps.setDouble(4, siteWidthMm);
            ps.setDouble(5, siteDepthMm);
            ps.setDouble(6, siteHeightMm);
            ps.executeUpdate();
        }

        return buildingId;
    }

    // ── Save ─────────────────────────────────────────────────────────

    /**
     * Save current design as a new sub-C_Order with C_OrderLine rows.
     * Creates a W_Variant pointer. Returns the variant ID.
     *
     * <p>Each Save creates an immutable snapshot: sub-order goes DR → CO.
     * The bboxes are stored as C_OrderLine rows with tack offsets.
     *
     * @param buildingId   master C_Order_ID
     * @param bboxes       current design state
     * @param variantLabel user label (null → auto "v{N}")
     * @return the sub-C_Order_ID (= variant ID)
     */
    public String save(String buildingId, List<DesignBBox> bboxes, String variantLabel)
            throws SQLException {
        conn.setAutoCommit(false);
        try {
            // 1. Count existing variants to auto-label
            int variantCount = countVariants(buildingId);
            String label = (variantLabel != null && !variantLabel.isBlank())
                    ? variantLabel
                    : "v" + (variantCount + 1);

            // 2. Create sub-C_Order
            String subOrderId = buildingId + "_sub_" + System.currentTimeMillis();
            String masterWidth = "SELECT aabb_width_mm, aabb_depth_mm, aabb_height_mm FROM C_Order WHERE C_Order_ID = ?";
            double w = 0, d = 0, h = 0;
            try (PreparedStatement ps = conn.prepareStatement(masterWidth)) {
                ps.setString(1, buildingId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        w = rs.getDouble(1); d = rs.getDouble(2); h = rs.getDouble(3);
                    }
                }
            }

            String insertOrder = """
                    INSERT INTO C_Order (C_Order_ID, Parent_Order_ID, C_DocType_ID, Name,
                        DocStatus, aabb_width_mm, aabb_depth_mm, aabb_height_mm)
                    VALUES (?, ?, 'GENERATIVE', ?, 'CO', ?, ?, ?)
                    """;
            try (PreparedStatement ps = conn.prepareStatement(insertOrder)) {
                ps.setString(1, subOrderId);
                ps.setString(2, buildingId);
                ps.setString(3, label);
                ps.setDouble(4, w);
                ps.setDouble(5, d);
                ps.setDouble(6, h);
                ps.executeUpdate();
            }

            // 3. Insert C_OrderLine for each bbox
            String insertLine = """
                    INSERT INTO C_OrderLine (C_Order_ID, Line, family_ref, host_type,
                        m_product_category_id, dx, dy, dz,
                        aabb_width_mm, aabb_depth_mm, aabb_height_mm)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;
            try (PreparedStatement ps = conn.prepareStatement(insertLine)) {
                int seq = 10;
                for (DesignBBox bb : bboxes) {
                    ps.setString(1, subOrderId);
                    ps.setInt(2, seq);
                    ps.setString(3, bb.bomId());
                    ps.setString(4, bb.bomType());
                    ps.setString(5, bb.category());
                    // Tack offsets: store minX/Y/Z as dx/dy/dz (LBD in mm, convert to metres)
                    ps.setDouble(6, bb.minX() / 1000.0);
                    ps.setDouble(7, bb.minY() / 1000.0);
                    ps.setDouble(8, bb.minZ() / 1000.0);
                    ps.setDouble(9, bb.maxX() - bb.minX());
                    ps.setDouble(10, bb.maxY() - bb.minY());
                    ps.setDouble(11, bb.maxZ() - bb.minZ());
                    ps.addBatch();
                    seq += 10;
                }
                ps.executeBatch();
            }

            // 4. Deactivate previous active variant, create W_Variant pointer
            String deactivate = "UPDATE W_Variant SET is_active = 0 WHERE C_Order_ID IN "
                    + "(SELECT C_Order_ID FROM C_Order WHERE Parent_Order_ID = ?)";
            try (PreparedStatement ps = conn.prepareStatement(deactivate)) {
                ps.setString(1, buildingId);
                ps.executeUpdate();
            }

            String insertVariant = """
                    INSERT INTO W_Variant (C_Order_ID, label, is_active, orderline_count,
                        compliance_status)
                    VALUES (?, ?, 1, ?, 'UNCHECKED')
                    """;
            try (PreparedStatement ps = conn.prepareStatement(insertVariant)) {
                ps.setString(1, subOrderId);
                ps.setString(2, label);
                ps.setInt(3, bboxes.size());
                ps.executeUpdate();
            }

            conn.commit();
            BIMLogger.info(TAG, "SAVE {} → {} ({} lines, label={})",
                    buildingId, subOrderId, bboxes.size(), label);

            return subOrderId;

        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
    }

    // ── Recall ───────────────────────────────────────────────────────

    /**
     * Recall a variant: read C_OrderLine from the sub-order, return as DesignBBox list.
     * Non-destructive — the sub-order stays CO.
     *
     * @param variantOrderId the sub-C_Order_ID to recall
     * @return bboxes reconstructed from C_OrderLine rows
     */
    public List<DesignBBox> recall(String variantOrderId) throws SQLException {
        String sql = """
                SELECT family_ref, host_type, m_product_category_id,
                       dx, dy, dz,
                       aabb_width_mm, aabb_depth_mm, aabb_height_mm
                FROM C_OrderLine
                WHERE C_Order_ID = ? AND IsActive = 1
                ORDER BY Line
                """;

        List<DesignBBox> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, variantOrderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String bomId = rs.getString("family_ref");
                    String hostType = rs.getString("host_type");
                    String category = rs.getString("m_product_category_id");
                    double dx = rs.getDouble("dx");
                    double dy = rs.getDouble("dy");
                    double dz = rs.getDouble("dz");
                    double w = rs.getDouble("aabb_width_mm");
                    double d = rs.getDouble("aabb_depth_mm");
                    double h = rs.getDouble("aabb_height_mm");

                    // Reconstruct bbox from tack + dims (LBD convention)
                    double minX = dx * 1000.0;  // metres → mm
                    double minY = dy * 1000.0;
                    double minZ = dz * 1000.0;

                    result.add(new DesignBBox(
                            bomId,
                            bomId,  // name = bomId for now
                            hostType,
                            category,
                            ifcClassFor(hostType),
                            null,   // storey: not stored in C_OrderLine yet
                            null,   // parentBomId: not stored yet
                            minX, minY, minZ,
                            minX + w, minY + d, minZ + h
                    ));
                }
            }
        }
        return result;
    }

    // ── List variants ────────────────────────────────────────────────

    /**
     * List all saved variants for a building (ordered by most recent first).
     */
    public List<VariantInfo> listVariants(String buildingId) throws SQLException {
        String sql = """
                SELECT v.C_Order_ID, v.label, v.created, v.orderline_count,
                       v.compliance_status
                FROM W_Variant v
                JOIN C_Order o ON v.C_Order_ID = o.C_Order_ID
                WHERE o.Parent_Order_ID = ?
                ORDER BY v.created DESC
                """;

        List<VariantInfo> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, buildingId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new VariantInfo(
                            rs.getString("C_Order_ID"),
                            rs.getString("label"),
                            rs.getString("created"),
                            rs.getInt("orderline_count"),
                            rs.getString("compliance_status")
                    ));
                }
            }
        }
        return result;
    }

    // ── Insert single OrderLine (for placeItem persistence) ─────────

    /**
     * Insert a single C_OrderLine for a placed item into the active sub-order.
     * If no active sub-order exists, creates one (auto-save behaviour).
     *
     * @return the C_OrderLine_ID (auto-incremented)
     */
    public int insertOrderLine(String buildingId, DesignBBox itemBbox) throws SQLException {
        // Find active sub-order (most recent CO for this building)
        String activeSubOrder = findActiveSubOrder(buildingId);
        if (activeSubOrder == null) {
            // No active sub-order — create one as an auto-save
            activeSubOrder = buildingId + "_auto_" + System.currentTimeMillis();
            String insertOrder = """
                    INSERT INTO C_Order (C_Order_ID, Parent_Order_ID, C_DocType_ID, Name,
                        DocStatus, aabb_width_mm, aabb_depth_mm, aabb_height_mm)
                    VALUES (?, ?, 'GENERATIVE', ?, 'DR', 0, 0, 0)
                    """;
            try (PreparedStatement ps = conn.prepareStatement(insertOrder)) {
                ps.setString(1, activeSubOrder);
                ps.setString(2, buildingId);
                ps.setString(3, "auto-place");
                ps.executeUpdate();
            }
            BIMLogger.info(TAG, "Created auto sub-order {} for placeItem", activeSubOrder);
        }

        // Find next Line sequence
        int nextLine = 10;
        String maxLine = "SELECT COALESCE(MAX(Line), 0) FROM C_OrderLine WHERE C_Order_ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(maxLine)) {
            ps.setString(1, activeSubOrder);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) nextLine = rs.getInt(1) + 10;
            }
        }

        // Insert the C_OrderLine
        String insertLine = """
                INSERT INTO C_OrderLine (C_Order_ID, Line, family_ref, host_type,
                    m_product_category_id, dx, dy, dz,
                    aabb_width_mm, aabb_depth_mm, aabb_height_mm)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(insertLine,
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, activeSubOrder);
            ps.setInt(2, nextLine);
            ps.setString(3, itemBbox.bomId());
            ps.setString(4, itemBbox.bomType());
            ps.setString(5, itemBbox.category());
            ps.setDouble(6, itemBbox.minX() / 1000.0);  // mm → metres
            ps.setDouble(7, itemBbox.minY() / 1000.0);
            ps.setDouble(8, itemBbox.minZ() / 1000.0);
            ps.setDouble(9, itemBbox.maxX() - itemBbox.minX());
            ps.setDouble(10, itemBbox.maxY() - itemBbox.minY());
            ps.setDouble(11, itemBbox.maxZ() - itemBbox.minZ());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        return 0;
    }

    /**
     * Find the most recent active sub-order for a building (DR or IP status).
     */
    private String findActiveSubOrder(String buildingId) throws SQLException {
        String sql = """
                SELECT C_Order_ID FROM C_Order
                WHERE Parent_Order_ID = ? AND DocStatus IN ('DR', 'IP')
                ORDER BY created DESC LIMIT 1
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, buildingId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    // ── ORDER View — list + update (§17.11) ─────────────────────────

    /**
     * Row shape for ORDER View tabular display.
     * // Implementing BIM_Designer.md §17.11 — Witness: W-OV-LIST-1
     */
    public record OrderLineRow(int orderLineId, String familyRef, String hostType,
                                String bomCategory, double dx, double dy, double dz,
                                double widthMm, double depthMm, double heightMm,
                                int qty, String validationStatus, int parentOrderLineId) {}

    /**
     * List all C_OrderLine rows for a building's most recent sub-order.
     * Returns flat tabular data for ORDER View (§17.11).
     * Falls back to most recent CO sub-order if no DR/IP exists.
     */
    public List<OrderLineRow> listOrderLines(String buildingId) throws SQLException {
        // Try active sub-order first, then fall back to latest CO
        String subOrderId = findActiveSubOrder(buildingId);
        if (subOrderId == null) {
            subOrderId = findLatestSubOrder(buildingId);
        }
        if (subOrderId == null) return List.of();

        return queryOrderLines(subOrderId);
    }

    /**
     * Query C_OrderLine rows for a specific sub-order.
     */
    private List<OrderLineRow> queryOrderLines(String subOrderId) throws SQLException {
        String sql = """
                SELECT C_OrderLine_ID, family_ref, host_type, m_product_category_id,
                       dx, dy, dz,
                       aabb_width_mm, aabb_depth_mm, aabb_height_mm,
                       Qty, validation_status,
                       COALESCE(Parent_OrderLine_ID, 0) AS parent_id
                FROM C_OrderLine
                WHERE C_Order_ID = ? AND IsActive = 1
                ORDER BY Line
                """;

        List<OrderLineRow> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, subOrderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new OrderLineRow(
                            rs.getInt("C_OrderLine_ID"),
                            rs.getString("family_ref"),
                            rs.getString("host_type"),
                            rs.getString("m_product_category_id"),
                            rs.getDouble("dx"),
                            rs.getDouble("dy"),
                            rs.getDouble("dz"),
                            rs.getDouble("aabb_width_mm"),
                            rs.getDouble("aabb_depth_mm"),
                            rs.getDouble("aabb_height_mm"),
                            rs.getInt("Qty"),
                            rs.getString("validation_status"),
                            rs.getInt("parent_id")
                    ));
                }
            }
        }
        return result;
    }

    /** Whitelist of fields editable via ORDER View inline editing. */
    private static final java.util.Set<String> EDITABLE_FIELDS = java.util.Set.of(
            "aabb_width_mm", "aabb_depth_mm", "aabb_height_mm",
            "dx", "dy", "dz", "Qty",
            "m_product_category_id", "family_ref", "host_type");

    /** Text-type fields — use setString, not setDouble. */
    private static final java.util.Set<String> TEXT_FIELDS = java.util.Set.of(
            "m_product_category_id", "family_ref", "host_type");

    /**
     * Update a single field on a C_OrderLine (ORDER View inline edit).
     * Only whitelisted fields are allowed. Returns true if a row was updated.
     *
     * // Implementing BIM_Designer.md §17.11 — Witness: W-OV-UPDATE-1
     */
    public boolean updateOrderLine(int orderLineId, String field, String value)
            throws SQLException {
        if (!EDITABLE_FIELDS.contains(field)) {
            return false;
        }

        // Use parameterised value but field name is whitelisted (safe from injection)
        String sql = "UPDATE C_OrderLine SET " + field + " = ?, "
                + "updated = datetime('now') WHERE C_OrderLine_ID = ? AND IsActive = 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if ("Qty".equals(field)) {
                ps.setInt(1, Integer.parseInt(value));
            } else if (TEXT_FIELDS.contains(field)) {
                ps.setString(1, value);
            } else {
                ps.setDouble(1, Double.parseDouble(value));
            }
            ps.setInt(2, orderLineId);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Swap the product on a C_OrderLine — iDempiere BOM configurator pattern.
     * Updates both family_ref and M_Product_ID (they are kept in sync).
     * Used for BOM Drop swap: user navigates tree, picks a node, swaps product.
     *
     * // Implementing GENERATIVE_HOUSE_SRS.md §2.1 TC-4 — Witness: W-SWAP-1
     */
    public boolean swapProduct(int orderLineId, String newProductId) throws SQLException {
        String sql = """
                UPDATE C_OrderLine SET family_ref = ?, M_Product_ID = ?,
                    updated = datetime('now')
                WHERE C_OrderLine_ID = ? AND IsActive = 1""";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newProductId);
            ps.setString(2, newProductId);
            ps.setInt(3, orderLineId);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Get a single OrderLineRow by ID (for returning updated row after edit).
     */
    public OrderLineRow getOrderLine(int orderLineId) throws SQLException {
        String sql = """
                SELECT C_OrderLine_ID, family_ref, host_type, m_product_category_id,
                       dx, dy, dz,
                       aabb_width_mm, aabb_depth_mm, aabb_height_mm,
                       Qty, validation_status,
                       COALESCE(Parent_OrderLine_ID, 0) AS parent_id
                FROM C_OrderLine
                WHERE C_OrderLine_ID = ? AND IsActive = 1
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderLineId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new OrderLineRow(
                            rs.getInt("C_OrderLine_ID"),
                            rs.getString("family_ref"),
                            rs.getString("host_type"),
                            rs.getString("m_product_category_id"),
                            rs.getDouble("dx"),
                            rs.getDouble("dy"),
                            rs.getDouble("dz"),
                            rs.getDouble("aabb_width_mm"),
                            rs.getDouble("aabb_depth_mm"),
                            rs.getDouble("aabb_height_mm"),
                            rs.getInt("Qty"),
                            rs.getString("validation_status"),
                            rs.getInt("parent_id")
                    );
                }
            }
        }
        return null;
    }

    /**
     * Find the most recent sub-order for a building (any status, for fallback).
     */
    private String findLatestSubOrder(String buildingId) throws SQLException {
        String sql = """
                SELECT C_Order_ID FROM C_Order
                WHERE Parent_Order_ID = ?
                ORDER BY created DESC LIMIT 1
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, buildingId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    // ── BOM Drop — Tree Explosion into C_OrderLine (§28) ──────────

    /**
     * Create a C_Order for BOM Drop with DocStatus DR (draft).
     * iDempiere pattern: C_Order is the sales/manufacturing order header.
     *
     * @param buildingProductId  the BUILDING product ID (e.g. BUILDING_SH_STD)
     * @param widthMm            site envelope width
     * @param depthMm            site envelope depth
     * @param heightMm           site envelope height
     * @return the C_Order_ID
     *
     * // Implementing BIM_Designer_SRS.md §28.1 — Witness: W-DROP-1
     */
    public String createBomDropOrder(String buildingProductId,
                                      double widthMm, double depthMm, double heightMm)
            throws SQLException {
        String orderId = "DROP_" + buildingProductId + "_" + System.currentTimeMillis();
        String sql = """
                INSERT INTO C_Order (C_Order_ID, Parent_Order_ID, C_DocType_ID, Name,
                    DocStatus, aabb_width_mm, aabb_depth_mm, aabb_height_mm)
                VALUES (?, ?, 'BOM_DROP', ?, 'DR', ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, orderId);
            ps.setString(2, buildingProductId);  // Parent_Order_ID = product ID (findActiveSubOrder key)
            ps.setString(3, buildingProductId);
            ps.setDouble(4, widthMm);
            ps.setDouble(5, depthMm);
            ps.setDouble(6, heightMm);
            ps.executeUpdate();
        }
        BIMLogger.info(TAG, "BOM_DROP order created: {} for product {}", orderId, buildingProductId);
        return orderId;
    }

    /**
     * Insert a C_OrderLine for a BOM Drop tree node.
     * iDempiere pattern: C_OrderLine references M_Product_ID (family_ref)
     * and M_Product_Category (m_product_category_id). Only IsBOM products (those with
     * a matching m_bom entry) have children — this is the BOM tree structure.
     *
     * @param orderId           C_Order_ID (from createBomDropOrder)
     * @param parentLineId      Parent_OrderLine_ID (0 = root)
     * @param familyRef         M_Product_ID — the product (or bom_id for assemblies)
     * @param hostType          BUILDING | FLOOR | ROOM | LEAF
     * @param bomCategory       M_Product_Category — for BOM chooser swap browsing
     * @param dx                tack X offset in parent LBD (metres)
     * @param dy                tack Y offset in parent LBD (metres)
     * @param dz                tack Z offset in parent LBD (metres)
     * @param widthMm           AABB width (mm)
     * @param depthMm           AABB depth (mm)
     * @param heightMm          AABB height (mm)
     * @param qty               quantity
     * @return the C_OrderLine_ID (auto-increment)
     */
    public int insertBomDropLine(String orderId, int parentLineId,
                                  String familyRef, String hostType, String bomCategory,
                                  double dx, double dy, double dz,
                                  double widthMm, double depthMm, double heightMm,
                                  int qty) throws SQLException {
        // Find next Line sequence for this order
        int nextLine = 10;
        String maxLine = "SELECT COALESCE(MAX(Line), 0) FROM C_OrderLine WHERE C_Order_ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(maxLine)) {
            ps.setString(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) nextLine = rs.getInt(1) + 10;
            }
        }

        String sql = """
                INSERT INTO C_OrderLine (C_Order_ID, Parent_OrderLine_ID, Line,
                    family_ref, host_type, m_product_category_id, M_Product_ID,
                    dx, dy, dz,
                    aabb_width_mm, aabb_depth_mm, aabb_height_mm, Qty)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql,
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, orderId);
            if (parentLineId > 0) {
                ps.setInt(2, parentLineId);
            } else {
                ps.setNull(2, java.sql.Types.INTEGER);
            }
            ps.setInt(3, nextLine);
            ps.setString(4, familyRef);
            ps.setString(5, hostType);
            ps.setString(6, bomCategory);
            ps.setString(7, familyRef);  // M_Product_ID = family_ref (iDempiere alignment)
            ps.setDouble(8, dx);
            ps.setDouble(9, dy);
            ps.setDouble(10, dz);
            ps.setDouble(11, widthMm);
            ps.setDouble(12, depthMm);
            ps.setDouble(13, heightMm);
            ps.setInt(14, qty);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        return 0;
    }

    /**
     * Count C_OrderLine rows for a given order (for totalElements reporting).
     */
    public int countOrderLines(String orderId, String hostType) throws SQLException {
        String sql = hostType != null
                ? "SELECT COUNT(*) FROM C_OrderLine WHERE C_Order_ID = ? AND host_type = ? AND IsActive = 1"
                : "SELECT COUNT(*) FROM C_OrderLine WHERE C_Order_ID = ? AND IsActive = 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, orderId);
            if (hostType != null) ps.setString(2, hostType);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /**
     * Count C_OrderLine rows that have M_Product_ID set (iDempiere alignment check).
     */
    public int countOrderLinesWithProductId(String orderId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM C_OrderLine WHERE C_Order_ID = ? AND M_Product_ID IS NOT NULL";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    // ── DocStatus (Promote governance gate §17.10.4) ────────────────

    /**
     * Get the master C_Order DocStatus for a building.
     * Returns null if no master order exists.
     *
     * // Implementing BIM_Designer.md §17.10.4 — Witness: W-PROMOTE-2
     */
    public String getMasterDocStatus(String buildingId) throws SQLException {
        String sql = "SELECT DocStatus FROM C_Order WHERE C_Order_ID = ? AND Parent_Order_ID IS NULL";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, buildingId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    /**
     * Transition master C_Order DocStatus (e.g. IP → AP, AP → CO).
     * Returns true if a row was updated.
     *
     * // Implementing BIM_Designer.md §17.10.4 — Witness: W-PROMOTE-4
     */
    public boolean setMasterDocStatus(String buildingId, String newStatus) throws SQLException {
        String sql = "UPDATE C_Order SET DocStatus = ?, updated = datetime('now') "
                + "WHERE C_Order_ID = ? AND Parent_Order_ID IS NULL";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newStatus);
            ps.setString(2, buildingId);
            return ps.executeUpdate() > 0;
        }
    }

    // ── ASI — AttributeSetInstance CRUD (BBC.md §3.5.1) ─────────────

    /** ASI DDL — called from initASISchema(). */
    private static final String ASI_DDL = """
            CREATE TABLE IF NOT EXISTS M_AttributeSet (
                M_AttributeSet_ID   TEXT PRIMARY KEY,
                Name                TEXT NOT NULL,
                IsInstanceAttribute INTEGER NOT NULL DEFAULT 0,
                Description         TEXT
            );
            CREATE TABLE IF NOT EXISTS M_AttributeSetInstance (
                M_AttributeSetInstance_ID  INTEGER PRIMARY KEY AUTOINCREMENT,
                M_AttributeSet_ID          TEXT REFERENCES M_AttributeSet(M_AttributeSet_ID),
                Description                TEXT
            );
            CREATE TABLE IF NOT EXISTS M_AttributeInstance (
                M_AttributeInstance_ID     INTEGER PRIMARY KEY AUTOINCREMENT,
                M_AttributeSetInstance_ID  INTEGER NOT NULL
                    REFERENCES M_AttributeSetInstance(M_AttributeSetInstance_ID),
                Name                       TEXT NOT NULL,
                Value                      TEXT NOT NULL,
                ValueType                  TEXT NOT NULL DEFAULT 'NUM'
                    CHECK(ValueType IN ('NUM','TEXT','BOOL')),
                UNIQUE(M_AttributeSetInstance_ID, Name)
            );
            """;

    /** ASI seed — product-type templates from ASI_001. */
    private static final String ASI_SEED = """
            INSERT OR IGNORE INTO M_AttributeSet VALUES
                ('BIM_Pipe',      'Pipe',      1, 'length/angle vary per instance');
            INSERT OR IGNORE INTO M_AttributeSet VALUES
                ('BIM_Conduit',   'Conduit',   1, 'length varies per instance');
            INSERT OR IGNORE INTO M_AttributeSet VALUES
                ('BIM_Duct',      'Duct',      1, 'length/cross-section vary');
            INSERT OR IGNORE INTO M_AttributeSet VALUES
                ('BIM_Wall',      'Wall',      1, 'thickness fixed, length/height vary');
            INSERT OR IGNORE INTO M_AttributeSet VALUES
                ('BIM_Slab',      'Slab',      1, 'thickness fixed, area varies');
            INSERT OR IGNORE INTO M_AttributeSet VALUES
                ('BIM_Beam',      'Beam',      1, 'section fixed, span varies');
            INSERT OR IGNORE INTO M_AttributeSet VALUES
                ('BIM_Column',    'Column',    1, 'section fixed, height varies');
            INSERT OR IGNORE INTO M_AttributeSet VALUES
                ('BIM_Door',      'Door',      1, 'width/height fixed, swing/anchor vary');
            INSERT OR IGNORE INTO M_AttributeSet VALUES
                ('BIM_Window',    'Window',    1, 'width/height fixed, sill/anchor vary');
            INSERT OR IGNORE INTO M_AttributeSet VALUES
                ('BIM_Component', 'Component', 0, 'identical everywhere');
            INSERT OR IGNORE INTO M_AttributeSet VALUES
                ('BIM_Fitting',   'Fitting',   0, 'angle is type, not instance');
            """;

    /**
     * Ensure ASI schema + seed exists. Safe to call repeatedly.
     *
     * // Implementing BBC.md §3.5.1 — Witness: W-ASI-READ-1
     */
    public void initASISchema() throws SQLException {
        executeDDL(ASI_DDL);
        executeDDL(ASI_SEED);
    }

    /**
     * Create a new M_AttributeSetInstance with name/value pairs.
     * Returns the auto-generated ASI ID.
     *
     * @param attributeSetId  template ID (e.g. "BIM_Wall")
     * @param values          name→value pairs (e.g. "length_mm"→"6000")
     * @return M_AttributeSetInstance_ID
     */
    public int createASI(String attributeSetId, java.util.Map<String, String> values)
            throws SQLException {
        String desc = values.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .reduce((a, b) -> a + ", " + b)
                .orElse("");

        String insertHeader = """
                INSERT INTO M_AttributeSetInstance (M_AttributeSet_ID, Description)
                VALUES (?, ?)
                """;
        int asiId;
        try (PreparedStatement ps = conn.prepareStatement(insertHeader,
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, attributeSetId);
            ps.setString(2, desc);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("No key for ASI insert");
                asiId = keys.getInt(1);
            }
        }

        String insertValue = """
                INSERT INTO M_AttributeInstance (M_AttributeSetInstance_ID, Name, Value, ValueType)
                VALUES (?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(insertValue)) {
            for (var entry : values.entrySet()) {
                ps.setInt(1, asiId);
                ps.setString(2, entry.getKey());
                ps.setString(3, entry.getValue());
                ps.setString(4, inferValueType(entry.getValue()));
                ps.addBatch();
            }
            ps.executeBatch();
        }

        BIMLogger.info(TAG, "CREATE_ASI id={} set={} attrs={}", asiId, attributeSetId, values.size());
        return asiId;
    }

    /**
     * Read all attribute values for an ASI instance.
     * Returns empty list if ASI does not exist.
     */
    public List<ASIFieldRow> readASI(int asiId) throws SQLException {
        String sql = """
                SELECT ai.Name, ai.Value, ai.ValueType,
                       aset.M_AttributeSet_ID, aset.Name AS set_name,
                       aset.IsInstanceAttribute
                FROM M_AttributeInstance ai
                JOIN M_AttributeSetInstance asi
                  ON ai.M_AttributeSetInstance_ID = asi.M_AttributeSetInstance_ID
                JOIN M_AttributeSet aset
                  ON asi.M_AttributeSet_ID = aset.M_AttributeSet_ID
                WHERE ai.M_AttributeSetInstance_ID = ?
                ORDER BY ai.Name
                """;
        List<ASIFieldRow> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, asiId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new ASIFieldRow(
                            rs.getString("Name"),
                            rs.getString("Value"),
                            rs.getString("ValueType"),
                            rs.getString("M_AttributeSet_ID"),
                            rs.getString("set_name"),
                            rs.getInt("IsInstanceAttribute") == 1
                    ));
                }
            }
        }
        return result;
    }

    /**
     * Update a single ASI attribute value. Creates the attribute if it
     * doesn't exist yet (upsert).
     */
    public boolean updateASIAttribute(int asiId, String name, String value)
            throws SQLException {
        String update = """
                UPDATE M_AttributeInstance SET Value = ?, ValueType = ?
                WHERE M_AttributeSetInstance_ID = ? AND Name = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(update)) {
            ps.setString(1, value);
            ps.setString(2, inferValueType(value));
            ps.setInt(3, asiId);
            ps.setString(4, name);
            if (ps.executeUpdate() > 0) return true;
        }

        String insert = """
                INSERT INTO M_AttributeInstance
                    (M_AttributeSetInstance_ID, Name, Value, ValueType)
                VALUES (?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(insert)) {
            ps.setInt(1, asiId);
            ps.setString(2, name);
            ps.setString(3, value);
            ps.setString(4, inferValueType(value));
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Link an ASI to a C_OrderLine (set the FK).
     */
    public boolean linkASI(int orderLineId, int asiId) throws SQLException {
        String sql = "UPDATE C_OrderLine SET M_AttributeSetInstance_ID = ?, "
                + "updated = datetime('now') WHERE C_OrderLine_ID = ? AND IsActive = 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, asiId);
            ps.setInt(2, orderLineId);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Read the ASI ID linked to a C_OrderLine. Returns -1 if none.
     */
    public int getASIForOrderLine(int orderLineId) throws SQLException {
        String sql = "SELECT M_AttributeSetInstance_ID FROM C_OrderLine "
                + "WHERE C_OrderLine_ID = ? AND IsActive = 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderLineId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int val = rs.getInt(1);
                    return rs.wasNull() ? -1 : val;
                }
            }
        }
        return -1;
    }

    /**
     * List all M_AttributeSet templates.
     */
    public List<AttributeSetRow> listAttributeSets() throws SQLException {
        String sql = "SELECT M_AttributeSet_ID, Name, IsInstanceAttribute, Description "
                + "FROM M_AttributeSet ORDER BY Name";
        List<AttributeSetRow> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(new AttributeSetRow(
                        rs.getString("M_AttributeSet_ID"),
                        rs.getString("Name"),
                        rs.getInt("IsInstanceAttribute") == 1,
                        rs.getString("Description")
                ));
            }
        }
        return result;
    }

    /**
     * Resolve M_AttributeSet_ID for a product family_ref.
     * Convention: WALL_* → BIM_Wall, PIPE_* → BIM_Pipe, etc.
     */
    public String resolveAttributeSetForProduct(String familyRef) {
        String prefix = familyRef.split("_")[0].toUpperCase();
        return switch (prefix) {
            case "WALL" -> "BIM_Wall";
            case "PIPE" -> "BIM_Pipe";
            case "DUCT" -> "BIM_Duct";
            case "CONDUIT" -> "BIM_Conduit";
            case "SLAB" -> "BIM_Slab";
            case "BEAM" -> "BIM_Beam";
            case "COLUMN", "COL" -> "BIM_Column";
            case "DOOR" -> "BIM_Door";
            case "WINDOW", "WIN" -> "BIM_Window";
            default -> "BIM_Component";
        };
    }

    /**
     * Check if a product type needs per-instance attributes.
     */
    public boolean isInstanceAttribute(String attributeSetId) throws SQLException {
        String sql = "SELECT IsInstanceAttribute FROM M_AttributeSet "
                + "WHERE M_AttributeSet_ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, attributeSetId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) == 1;
            }
        }
    }

    // ASI row records
    public record ASIFieldRow(String name, String value, String valueType,
                               String attributeSetId, String attributeSetName,
                               boolean isInstanceAttribute) {}

    public record AttributeSetRow(String id, String name,
                                   boolean isInstanceAttribute, String description) {}

    private static String inferValueType(String value) {
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value))
            return "BOOL";
        try {
            Double.parseDouble(value);
            return "NUM";
        } catch (NumberFormatException e) {
            return "TEXT";
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private int countVariants(String buildingId) throws SQLException {
        String sql = """
                SELECT COUNT(*) FROM W_Variant v
                JOIN C_Order o ON v.C_Order_ID = o.C_Order_ID
                WHERE o.Parent_Order_ID = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, buildingId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private static String ifcClassFor(String hostType) {
        return switch (hostType) {
            case "BUILDING" -> "IfcBuilding";
            case "FLOOR" -> "IfcBuildingStorey";
            case "ROOM" -> "IfcSpace";
            default -> null;
        };
    }

    /**
     * Get the work_output.db path for a building.
     * Convention: library/work_{buildingId_lowercase}.db
     */
    public static String dbPathFor(String buildingId) {
        return "library/work_" + buildingId.toLowerCase().replaceAll("\\s+", "_") + ".db";
    }

    // ── Inline DDL (for tests / embedded use when migration file unavailable) ──

    private static final String INLINE_DDL = """
            CREATE TABLE IF NOT EXISTS W_BuildingConfig (
                w_buildingconfig_id   INTEGER PRIMARY KEY,
                building_id           TEXT NOT NULL UNIQUE,
                yaml_content          TEXT NOT NULL,
                doc_base_type         TEXT NOT NULL,
                doc_sub_type          TEXT NOT NULL,
                jurisdiction          TEXT NOT NULL DEFAULT 'MY',
                aabb_width_mm         REAL NOT NULL,
                aabb_depth_mm         REAL NOT NULL,
                aabb_height_mm        REAL NOT NULL,
                provenance            TEXT NOT NULL DEFAULT 'GENERATIVE',
                created               TEXT NOT NULL DEFAULT (datetime('now'))
            );
            CREATE TABLE IF NOT EXISTS C_Order (
                C_Order_ID            TEXT PRIMARY KEY,
                Parent_Order_ID       TEXT REFERENCES C_Order(C_Order_ID),
                C_DocType_ID          TEXT NOT NULL,
                Name                  TEXT NOT NULL,
                Description           TEXT,
                DocStatus             TEXT NOT NULL DEFAULT 'DR'
                                      CHECK(DocStatus IN ('DR','IP','AP','CO','VO')),
                aabb_width_mm         REAL NOT NULL,
                aabb_depth_mm         REAL NOT NULL,
                aabb_height_mm        REAL NOT NULL,
                IsActive              INTEGER NOT NULL DEFAULT 1,
                created               TEXT NOT NULL DEFAULT (datetime('now')),
                updated               TEXT NOT NULL DEFAULT (datetime('now'))
            );
            CREATE TABLE IF NOT EXISTS C_OrderLine (
                C_OrderLine_ID        INTEGER PRIMARY KEY AUTOINCREMENT,
                C_Order_ID            TEXT NOT NULL REFERENCES C_Order(C_Order_ID),
                Parent_OrderLine_ID   INTEGER REFERENCES C_OrderLine(C_OrderLine_ID),
                Line                  INTEGER NOT NULL DEFAULT 10,
                family_ref            TEXT NOT NULL,
                host_type             TEXT NOT NULL,
                m_product_category_id TEXT,
                M_Product_ID          TEXT,
                dx                    REAL NOT NULL DEFAULT 0,
                dy                    REAL NOT NULL DEFAULT 0,
                dz                    REAL NOT NULL DEFAULT 0,
                aabb_width_mm         REAL,
                aabb_depth_mm         REAL,
                aabb_height_mm        REAL,
                M_AttributeSetInstance_ID  INTEGER,
                Qty                   INTEGER NOT NULL DEFAULT 1,
                IsActive              INTEGER NOT NULL DEFAULT 1,
                validation_status     TEXT DEFAULT 'UNCHECKED'
                                      CHECK(validation_status IN ('UNCHECKED','PASS','WARN','FAIL')),
                created               TEXT NOT NULL DEFAULT (datetime('now')),
                updated               TEXT NOT NULL DEFAULT (datetime('now'))
            );
            CREATE INDEX IF NOT EXISTS idx_orderline_order ON C_OrderLine(C_Order_ID);
            CREATE INDEX IF NOT EXISTS idx_orderline_family ON C_OrderLine(family_ref);
            CREATE TABLE IF NOT EXISTS W_Variant (
                W_Variant_ID          INTEGER PRIMARY KEY AUTOINCREMENT,
                C_Order_ID            TEXT NOT NULL REFERENCES C_Order(C_Order_ID),
                label                 TEXT NOT NULL,
                description           TEXT,
                is_active             INTEGER NOT NULL DEFAULT 0,
                orderline_count       INTEGER NOT NULL,
                esline_count          INTEGER NOT NULL DEFAULT 0,
                asi_count             INTEGER NOT NULL DEFAULT 0,
                ppnode_count          INTEGER NOT NULL DEFAULT 0,
                compliance_status     TEXT DEFAULT 'UNCHECKED'
                                      CHECK(compliance_status IN ('UNCHECKED','PASSED','FAILED')),
                created               TEXT NOT NULL DEFAULT (datetime('now'))
            );
            CREATE INDEX IF NOT EXISTS idx_variant_order ON W_Variant(C_Order_ID);
            CREATE TABLE IF NOT EXISTS AD_SysConfig (
                Name                  TEXT PRIMARY KEY,
                Value                 TEXT NOT NULL,
                Description           TEXT,
                updated               TEXT NOT NULL DEFAULT (datetime('now'))
            );
            INSERT OR IGNORE INTO AD_SysConfig (Name, Value, Description)
            VALUES ('SCHEMA_VERSION', 'W001', 'work_output.db schema version');
            """;
}
