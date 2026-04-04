package com.bim.ifctobom;

import java.sql.*;

/**
 * Single write path for m_bom and m_bom_line tables.
 *
 * <p>Every builder delegates INSERT operations here — no builder owns raw SQL
 * for these two tables. Column list mirrors DDL in {@link IFCtoBOMPipeline}.
 *
 * <p>Implementing BBC.md §2.1.9 — Witness: W-BOMWRITER-1
 *
 * @see IFCtoBOMPipeline  DDL source of truth
 * @since S100-p94 (2026-03-29)
 */
public class BomWriter {

    // ── m_bom INSERT ──────────────────────────────────────────────────────────

    /**
     * Row carrier for m_bom INSERT. All columns explicit, defaults for optional.
     * Use {@link BomRowBuilder} to construct.
     */
    public static final class BomRow {
        String bomId;
        String bomName;
        String bomType;
        String groupBy;
        String entityType = "D";
        String docSubType;
        int productCategoryId = -1;   // -1 = NULL
        double originX, originY, originZ;
        int aabbW, aabbD, aabbH;
        String aabbQualifier = "OUTER";
        boolean isActive = true;
        boolean orIgnore = false;      // INSERT OR IGNORE vs INSERT OR REPLACE
        // §6.12.2: Shim properties — when set, this BOM is a shim parent
        String hostIfcClass;
        String mount;
        double offsetMm;
    }

    /** Builder for BomRow — callers set only the fields they need. */
    public static final class BomRowBuilder {
        private final BomRow r = new BomRow();

        public BomRowBuilder(String bomId, String bomName, String bomType, String groupBy) {
            r.bomId = bomId;
            r.bomName = bomName;
            r.bomType = bomType;
            r.groupBy = groupBy;
        }

        public BomRowBuilder docSubType(String v) { r.docSubType = v; return this; }
        public BomRowBuilder productCategoryId(int v) { r.productCategoryId = v; return this; }
        public BomRowBuilder origin(double x, double y, double z) {
            r.originX = x; r.originY = y; r.originZ = z; return this;
        }
        public BomRowBuilder aabb(int w, int d, int h) {
            r.aabbW = w; r.aabbD = d; r.aabbH = h; return this;
        }
        public BomRowBuilder aabbQualifier(String v) { r.aabbQualifier = v; return this; }
        public BomRowBuilder orIgnore() { r.orIgnore = true; return this; }
        /** §6.12.2: Set shim properties — makes this BOM a shim parent. */
        public BomRowBuilder shim(String hostIfcClass, String mount, double offsetMm) {
            r.hostIfcClass = hostIfcClass; r.mount = mount; r.offsetMm = offsetMm; return this;
        }

        public BomRow build() { return r; }
    }

    private static final String BOM_INSERT_REPLACE = """
            INSERT OR REPLACE INTO m_bom
            (bom_id, Value, bom_name, bom_type, group_by, entity_type,
             doc_sub_type, m_product_category_id,
             origin_x, origin_y, origin_z,
             aabb_width_mm, aabb_depth_mm, aabb_height_mm, aabb_qualifier, is_active,
             host_ifc_class, mount, offset_mm)
            VALUES (?, ?, ?, ?, ?, ?,
                    ?, ?,
                    ?, ?, ?,
                    ?, ?, ?, ?, ?,
                    ?, ?, ?)
            """;

    private static final String BOM_INSERT_IGNORE = """
            INSERT OR IGNORE INTO m_bom
            (bom_id, Value, bom_name, bom_type, group_by, entity_type,
             doc_sub_type, m_product_category_id,
             origin_x, origin_y, origin_z,
             aabb_width_mm, aabb_depth_mm, aabb_height_mm, aabb_qualifier, is_active,
             host_ifc_class, mount, offset_mm)
            VALUES (?, ?, ?, ?, ?, ?,
                    ?, ?,
                    ?, ?, ?,
                    ?, ?, ?, ?, ?,
                    ?, ?, ?)
            """;

    /** Insert or replace an m_bom header. All columns explicit. */
    public static void insertBom(Connection conn, BomRow r) throws SQLException {
        String sql = r.orIgnore ? BOM_INSERT_IGNORE : BOM_INSERT_REPLACE;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, r.bomId);
            stmt.setString(2, r.bomId);     // Value = bom_id
            stmt.setString(3, r.bomName);
            stmt.setString(4, r.bomType);
            stmt.setString(5, r.groupBy);
            stmt.setString(6, r.entityType);
            stmt.setString(7, r.docSubType);
            if (r.productCategoryId > 0) stmt.setInt(8, r.productCategoryId);
            else stmt.setNull(8, Types.INTEGER);
            stmt.setDouble(9, r.originX);
            stmt.setDouble(10, r.originY);
            stmt.setDouble(11, r.originZ);
            stmt.setInt(12, r.aabbW);
            stmt.setInt(13, r.aabbD);
            stmt.setInt(14, r.aabbH);
            stmt.setString(15, r.aabbQualifier);
            stmt.setInt(16, r.isActive ? 1 : 0);
            // §6.12.2: Shim properties (NULL for non-shim BOMs)
            if (r.hostIfcClass != null) {
                stmt.setString(17, r.hostIfcClass);
                stmt.setString(18, r.mount);
                stmt.setDouble(19, r.offsetMm);
            } else {
                stmt.setNull(17, Types.VARCHAR);
                stmt.setNull(18, Types.VARCHAR);
                stmt.setNull(19, Types.REAL);
            }
            stmt.executeUpdate();
        }
    }

    // ── m_bom_line INSERT ─────────────────────────────────────────────────────

    /**
     * Row carrier for m_bom_line INSERT. All columns explicit, defaults for optional.
     * Use {@link BomLineRowBuilder} to construct.
     */
    public static final class BomLineRow {
        String bomId;
        String childProductId;
        String role;
        int sequence = 100;
        String rotationRule = "0";
        int fitPriority = 20;
        int minSpaceMm = 0;
        double dx, dy, dz;
        boolean isActive = true;
        String entityType = "D";
        int qty = 1;
        String verbRef;
        double allocW, allocD, allocH;
        String storey;
        String elementRef;
        int ordinal;
        String orientation;
        String materialName;
        String materialRgba;
        String shapeArchetype;
        String scaleBand;
        String hostElementRef;
        int adOrgId = 0;
    }

    /** Builder for BomLineRow — callers set only the fields they need. */
    public static final class BomLineRowBuilder {
        private final BomLineRow r = new BomLineRow();

        public BomLineRowBuilder(String bomId, String childProductId, String role, int sequence) {
            r.bomId = bomId;
            r.childProductId = childProductId;
            r.role = role;
            r.sequence = sequence;
        }

        public BomLineRowBuilder rotationRule(String v) { r.rotationRule = v; return this; }
        public BomLineRowBuilder fitPriority(int v) { r.fitPriority = v; return this; }
        public BomLineRowBuilder offset(double dx, double dy, double dz) {
            r.dx = dx; r.dy = dy; r.dz = dz; return this;
        }
        public BomLineRowBuilder qty(int v) { r.qty = v; return this; }
        public BomLineRowBuilder verbRef(String v) { r.verbRef = v; return this; }
        public BomLineRowBuilder alloc(double w, double d, double h) {
            r.allocW = w; r.allocD = d; r.allocH = h; return this;
        }
        public BomLineRowBuilder storey(String v) { r.storey = v; return this; }
        public BomLineRowBuilder elementRef(String v) { r.elementRef = v; return this; }
        public BomLineRowBuilder ordinal(int v) { r.ordinal = v; return this; }
        public BomLineRowBuilder orientation(String v) { r.orientation = v; return this; }
        public BomLineRowBuilder material(String name, String rgba) {
            r.materialName = name; r.materialRgba = rgba; return this;
        }
        public BomLineRowBuilder shape(String archetype, String scaleBand) {
            r.shapeArchetype = archetype; r.scaleBand = scaleBand; return this;
        }
        public BomLineRowBuilder hostElementRef(String v) { r.hostElementRef = v; return this; }
        public BomLineRowBuilder adOrgId(int v) { r.adOrgId = v; return this; }

        public BomLineRow build() { return r; }
    }

    private static final String BOM_LINE_SQL = """
            INSERT INTO m_bom_line
            (bom_id, M_BOM_ID, child_product_id, role, sequence,
             rotation_rule, fit_priority, min_space_mm,
             dx, dy, dz, is_active, entity_type, qty, verb_ref,
             allocated_width_mm, allocated_depth_mm, allocated_height_mm,
             storey, element_ref, ordinal, orientation,
             material_name, material_rgba,
             shape_archetype, scale_band,
             host_element_ref, AD_Org_ID)
            VALUES (?, (SELECT M_BOM_ID FROM m_bom WHERE Value = ?), ?, ?, ?,
                    ?, ?, ?,
                    ?, ?, ?, ?, ?, ?, ?,
                    ?, ?, ?,
                    ?, ?, ?, ?,
                    ?, ?,
                    ?, ?,
                    ?, ?)
            """;

    /** Insert an m_bom_line. All columns explicit. */
    public static void insertBomLine(Connection conn, BomLineRow r) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(BOM_LINE_SQL)) {
            stmt.setString(1, r.bomId);
            stmt.setString(2, r.bomId);        // M_BOM_ID subquery
            stmt.setString(3, r.childProductId);
            stmt.setString(4, r.role);
            stmt.setInt(5, r.sequence);
            stmt.setString(6, r.rotationRule);
            stmt.setInt(7, r.fitPriority);
            stmt.setInt(8, r.minSpaceMm);
            stmt.setDouble(9, r.dx);
            stmt.setDouble(10, r.dy);
            stmt.setDouble(11, r.dz);
            stmt.setInt(12, r.isActive ? 1 : 0);
            stmt.setString(13, r.entityType);
            stmt.setInt(14, r.qty);
            stmt.setString(15, r.verbRef);
            stmt.setDouble(16, r.allocW);
            stmt.setDouble(17, r.allocD);
            stmt.setDouble(18, r.allocH);
            stmt.setString(19, r.storey);
            stmt.setString(20, r.elementRef);
            stmt.setInt(21, r.ordinal);
            stmt.setString(22, r.orientation);
            stmt.setString(23, r.materialName);
            stmt.setString(24, r.materialRgba);
            stmt.setString(25, r.shapeArchetype);
            stmt.setString(26, r.scaleBand);
            stmt.setString(27, r.hostElementRef);
            stmt.setInt(28, r.adOrgId);
            stmt.executeUpdate();
        }
    }

    private BomWriter() {} // utility class — no instances
}
