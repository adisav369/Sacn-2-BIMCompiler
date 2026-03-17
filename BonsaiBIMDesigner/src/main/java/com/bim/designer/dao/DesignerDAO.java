package com.bim.designer.dao;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Data Access Object for the BIM Designer module.
 *
 * <p>Follows the iDempiere DAO pattern: connection-per-call, caller owns
 * lifecycle. Never closes connections — the caller's try-with-resources does.
 *
 * <p>This DAO reads from two sources:
 * <ul>
 *   <li>{@code BOM.db} — C_DocType (building types), m_bom (BOM headers),
 *       m_bom_line (BOM children), M_BomCategory (room types)</li>
 *   <li>{@code component_library.db} — M_Product (product catalog)</li>
 * </ul>
 *
 * <p>The DAO is read-only for the designer's query path. Writes go through
 * the existing PO layer (MBOM, MBOMLine) which enforces EntityType guards.
 */
public class DesignerDAO {

    private static final Logger LOG = Logger.getLogger(DesignerDAO.class.getName());

    private final Connection bomConn;

    public DesignerDAO(Connection bomConn) {
        this.bomConn = bomConn;
    }

    // ── Building types (C_DocType) ──────────────────────────────────

    /** Active building types from C_DocType, with AABB from BUILDING-level m_bom. */
    public List<BuildingTypeRow> listBuildingTypes() throws SQLException {
        String sql = """
                SELECT d.C_DocType_ID, d.ProjectName, d.Name,
                       d.DocBaseType, d.DocSubType, d.IsActive,
                       d.ExpectedElements,
                       COALESCE(b.aabb_width_mm, 0)  AS aabb_width_mm,
                       COALESCE(b.aabb_depth_mm, 0)  AS aabb_depth_mm,
                       COALESCE(b.aabb_height_mm, 0) AS aabb_height_mm
                FROM C_DocType d
                LEFT JOIN m_bom b ON b.doc_sub_type = d.DocSubType
                  AND b.doc_base_type = d.DocBaseType
                  AND b.bom_type = 'BUILDING' AND b.is_active = 1
                WHERE d.IsActive = 1
                ORDER BY d.SeqNo
                """;
        List<BuildingTypeRow> rows = new ArrayList<>();
        try (PreparedStatement ps = bomConn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rows.add(new BuildingTypeRow(
                        rs.getString("C_DocType_ID"),
                        rs.getString("ProjectName"),
                        rs.getString("Name"),
                        rs.getString("DocBaseType"),
                        rs.getString("DocSubType"),
                        rs.getInt("ExpectedElements"),
                        rs.getDouble("aabb_width_mm"),
                        rs.getDouble("aabb_depth_mm"),
                        rs.getDouble("aabb_height_mm")
                ));
            }
        }
        return rows;
    }

    /** Single building type by projectName (buildingId). */
    public BuildingTypeRow getBuildingType(String buildingId) throws SQLException {
        String sql = """
                SELECT d.C_DocType_ID, d.ProjectName, d.Name,
                       d.DocBaseType, d.DocSubType,
                       d.ExpectedElements,
                       COALESCE(b.aabb_width_mm, 0)  AS aabb_width_mm,
                       COALESCE(b.aabb_depth_mm, 0)  AS aabb_depth_mm,
                       COALESCE(b.aabb_height_mm, 0) AS aabb_height_mm
                FROM C_DocType d
                LEFT JOIN m_bom b ON b.doc_sub_type = d.DocSubType
                  AND b.doc_base_type = d.DocBaseType
                  AND b.bom_type = 'BUILDING' AND b.is_active = 1
                WHERE d.ProjectName = ?
                """;
        try (PreparedStatement ps = bomConn.prepareStatement(sql)) {
            ps.setString(1, buildingId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new BuildingTypeRow(
                            rs.getString("C_DocType_ID"),
                            rs.getString("ProjectName"),
                            rs.getString("Name"),
                            rs.getString("DocBaseType"),
                            rs.getString("DocSubType"),
                            rs.getInt("ExpectedElements"),
                            rs.getDouble("aabb_width_mm"),
                            rs.getDouble("aabb_depth_mm"),
                            rs.getDouble("aabb_height_mm")
                    );
                }
            }
        }
        return null;
    }

    // ── BOM categories ──────────────────────────────────────────────

    /** Distinct BOM categories for a given DocSubType. */
    public List<CategoryRow> listCategories(String docSubType) throws SQLException {
        String sql = """
                SELECT bom_category, bom_type, COUNT(*) AS bom_count
                FROM m_bom
                WHERE doc_sub_type = ? AND is_active = 1
                  AND bom_category IS NOT NULL
                GROUP BY bom_category, bom_type
                ORDER BY bom_category
                """;
        List<CategoryRow> rows = new ArrayList<>();
        try (PreparedStatement ps = bomConn.prepareStatement(sql)) {
            ps.setString(1, docSubType);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new CategoryRow(
                            rs.getString("bom_category"),
                            rs.getString("bom_type"),
                            rs.getInt("bom_count")
                    ));
                }
            }
        }
        return rows;
    }

    // ── BOM tree walk (for preview) ─────────────────────────────────

    /** BOM header by bom_id. */
    public BomHeaderRow getBomHeader(String bomId) throws SQLException {
        String sql = """
                SELECT bom_id, bom_name, bom_type, bom_category,
                       aabb_width_mm, aabb_depth_mm, aabb_height_mm,
                       entity_type
                FROM m_bom WHERE bom_id = ?
                """;
        try (PreparedStatement ps = bomConn.prepareStatement(sql)) {
            ps.setString(1, bomId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new BomHeaderRow(
                            rs.getString("bom_id"),
                            rs.getString("bom_name"),
                            rs.getString("bom_type"),
                            rs.getString("bom_category"),
                            rs.getInt("aabb_width_mm"),
                            rs.getInt("aabb_depth_mm"),
                            rs.getInt("aabb_height_mm"),
                            rs.getString("entity_type")
                    );
                }
            }
        }
        return null;
    }

    /** BOM lines for a given bom_id. */
    public List<BomLineRow> getBomLines(String bomId) throws SQLException {
        String sql = """
                SELECT bom_child_id, bom_id, child_product_id,
                       component_type, dx, dy, dz,
                       allocated_width_mm, allocated_depth_mm, allocated_height_mm,
                       storey, element_ref, verb_ref, sequence
                FROM m_bom_line
                WHERE bom_id = ? AND is_active = 1
                ORDER BY sequence, bom_child_id
                """;
        List<BomLineRow> rows = new ArrayList<>();
        try (PreparedStatement ps = bomConn.prepareStatement(sql)) {
            ps.setString(1, bomId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new BomLineRow(
                            rs.getInt("bom_child_id"),
                            rs.getString("bom_id"),
                            rs.getString("child_product_id"),
                            rs.getString("component_type"),
                            rs.getDouble("dx"),
                            rs.getDouble("dy"),
                            rs.getDouble("dz"),
                            rs.getInt("allocated_width_mm"),
                            rs.getInt("allocated_depth_mm"),
                            rs.getInt("allocated_height_mm"),
                            rs.getString("storey"),
                            rs.getString("element_ref"),
                            rs.getString("verb_ref"),
                            rs.getInt("sequence")
                    ));
                }
            }
        }
        return rows;
    }

    // ── Row records ─────────────────────────────────────────────────

    public record BuildingTypeRow(
            String docTypeId, String projectName, String name,
            String docBaseType, String docSubType,
            int expectedElements,
            double aabbWidthMm, double aabbDepthMm, double aabbHeightMm
    ) {}

    public record CategoryRow(String categoryName, String bomType, int bomCount) {}

    public record BomHeaderRow(
            String bomId, String bomName, String bomType, String bomCategory,
            int aabbWidthMm, int aabbDepthMm, int aabbHeightMm,
            String entityType
    ) {}

    public record BomLineRow(
            int bomChildId, String bomId, String childProductId,
            String componentType,
            double dx, double dy, double dz,
            int allocatedWidthMm, int allocatedDepthMm, int allocatedHeightMm,
            String storey, String elementRef, String verbRef, int sequence
    ) {}
}
