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
                        bom_category, dx, dy, dz,
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
                SELECT family_ref, host_type, bom_category,
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
                    String category = rs.getString("bom_category");
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
                    bom_category, dx, dy, dz,
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
                SELECT C_OrderLine_ID, family_ref, host_type, bom_category,
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
                            rs.getString("bom_category"),
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
            "dx", "dy", "dz", "Qty");

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
            } else {
                ps.setDouble(1, Double.parseDouble(value));
            }
            ps.setInt(2, orderLineId);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Get a single OrderLineRow by ID (for returning updated row after edit).
     */
    public OrderLineRow getOrderLine(int orderLineId) throws SQLException {
        String sql = """
                SELECT C_OrderLine_ID, family_ref, host_type, bom_category,
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
                            rs.getString("bom_category"),
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
                bom_category          TEXT,
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
