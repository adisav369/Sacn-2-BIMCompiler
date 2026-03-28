package com.bim.compiler.dsl;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads C_DocType from BOM.db — building type definitions (constant domain config).
 *
 * <p>iDempiere pattern: C_DocType = document type classification (constant).
 * C_Order = transactional (output.db only, created at compile time).
 *
 * <p>Each row is a complete compilation template — DSL + paths + thresholds.
 * Adding a new building type = one SQL INSERT into C_DocType, zero Java files.
 */
public class BuildingRegistry {

    private static String dbPath() { return System.getProperty("bom.db"); }

    /**
     * Building type definition from C_DocType.
     * Domain config only — no transactional state (DocStatus, checksums go on C_Order in output.db).
     */
    public record BuildingEntry(
        String docTypeId,           // C_DocType.Value ('RE_SH', 'RE_DX', 'ST_SH', 'ST_DX')
        String projectName,         // building instance name ('Ifc4_SampleHouse')
        String name,                // human-readable ('Sample House')
        String mProductCategoryId,  // RE, CO, IN, ST — from m_bom.m_product_category_id
        String docSubType,          // SH, DX, TB, TE — from C_DocType.doc_sub_type / m_bom.doc_sub_type
        String dslContent,          // DSL template text
        String outputDbPath,        // output DB path
        String referenceDbPath,     // reference DB for verification
        boolean isActive,
        int seqNo,
        int expectedElements,
        String provenance,          // EXTRACTED | GENERATIVE
        String description,
        int geometryFailThreshold,
        double aabbWidthMm,
        double aabbDepthMm,
        double aabbHeightMm
    ) {
        public boolean isGenerative() {
            return "GENERATIVE".equals(provenance);
        }

        public boolean hasReference() {
            return referenceDbPath != null && !referenceDbPath.isBlank();
        }

        /** Backward compat: buildingId = projectName (was C_Order_ID) */
        public String id() {
            return projectName;
        }
    }

    public record BuildingAssertion(
        String buildingId,
        String assertionId,
        String elementMatch,
        String property,
        String operator,
        String expected,
        double tolerance
    ) {}

    /**
     * Load all active building types ordered by SeqNo.
     */
    public static List<BuildingEntry> loadActive() {
        return load("WHERE IsActive = 1 ORDER BY SeqNo");
    }

    /**
     * Load a building type by ProjectName (building instance name).
     */
    public static BuildingEntry loadById(String buildingId) {
        List<BuildingEntry> entries = load("WHERE ProjectName = ?", buildingId);
        return entries.isEmpty() ? null : entries.get(0);
    }

    /**
     * Load active building types filtered by M_Product_Category (RE, CO, IN, ST).
     * Used by compile_building() to select RE for ENBLOC, ST for WALKTHRU.
     */
    public static List<BuildingEntry> loadByProductCategory(String category) {
        return load("WHERE MProductCategoryId = ? AND IsActive = 1 ORDER BY SeqNo", category);
    }

    /**
     * Load a building type by C_DocType_ID.
     */
    public static BuildingEntry loadByDocTypeId(String docTypeId) {
        List<BuildingEntry> entries = load("WHERE Value = ?", docTypeId);
        return entries.isEmpty() ? null : entries.get(0);
    }

    /**
     * Load assertions for a building.
     */
    public static List<BuildingAssertion> loadAssertions(String buildingId) {
        List<BuildingAssertion> result = new ArrayList<>();
        String sql = "SELECT building_id, assertion_id, element_match, property, operator, expected, tolerance "
                   + "FROM ad_building_assertions WHERE building_id = ? ORDER BY assertion_id";
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath());
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, buildingId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new BuildingAssertion(
                        rs.getString("building_id"),
                        rs.getString("assertion_id"),
                        rs.getString("element_match"),
                        rs.getString("property"),
                        rs.getString("operator"),
                        rs.getString("expected"),
                        rs.getDouble("tolerance")
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("[BuildingRegistry] Failed to load assertions for " + buildingId + ": " + e.getMessage());
        }
        return result;
    }

    private static List<BuildingEntry> load(String whereClause, String... params) {
        List<BuildingEntry> entries = new ArrayList<>();
        // AABB + m_product_category_id from BUILDING BOM (m_bom).
        // W018: DocBaseType/DocSubType dropped from C_DocType. doc_sub_type is the single FK.
        // ST_SH/ST_DX (m_product_category_id='ST') resolve AABB from M_BomCategory, not BUILDING BOM.
        // Tier 2: C_DocType_ID is INTEGER PK. Value holds old TEXT key (e.g., 'RE_SH').
        String sql = "SELECT d.Value AS C_DocType_ID, d.ProjectName, d.Name, "
                   + "b.m_product_category_id AS MProductCategoryId, "
                   + "COALESCE(d.doc_sub_type, b.doc_sub_type) AS DocSubType, "
                   + "d.DSLContent, d.OutputDbPath, d.ReferenceDbPath, d.IsActive, d.SeqNo, "
                   + "d.ExpectedElements, d.Provenance, d.Description, "
                   + "d.GeometryFailThreshold, "
                   + "COALESCE(b.aabb_width_mm, 0) AS AabbWidthMm, "
                   + "COALESCE(b.aabb_depth_mm, 0) AS AabbDepthMm, "
                   + "COALESCE(b.aabb_height_mm, 0) AS AabbHeightMm "
                   + "FROM C_DocType d "
                   // Implementing DISC_VALIDATION_DB_SRS.md §10.4.5 — Witness: W-TREE-1
                   // Root = BOM with no parent m_bom_line (replaces bom_type = 'BUILDING')
                   + "LEFT JOIN m_bom b ON b.doc_sub_type = d.doc_sub_type "
                   + "  AND b.Value NOT IN (SELECT child_product_id FROM m_bom_line WHERE is_active = 1) "
                   + "  AND b.is_active = 1 "
                   + qualifyWhereClause(whereClause);
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath());
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setString(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    entries.add(new BuildingEntry(
                        rs.getString("C_DocType_ID"),
                        rs.getString("ProjectName"),
                        rs.getString("Name"),
                        rs.getString("MProductCategoryId"),
                        rs.getString("DocSubType"),
                        rs.getString("DSLContent"),
                        rs.getString("OutputDbPath"),
                        rs.getString("ReferenceDbPath"),
                        rs.getInt("IsActive") == 1,
                        rs.getInt("SeqNo"),
                        rs.getInt("ExpectedElements"),
                        rs.getString("Provenance"),
                        rs.getString("Description"),
                        rs.getInt("GeometryFailThreshold"),
                        rs.getDouble("AabbWidthMm"),
                        rs.getDouble("AabbDepthMm"),
                        rs.getDouble("AabbHeightMm")
                    ));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load building registry from C_DocType: " + e.getMessage(), e);
        }
        return entries;
    }

    /** Qualify bare column names in WHERE/ORDER clause with table alias. */
    private static String qualifyWhereClause(String clause) {
        return clause
            .replace("IsActive", "d.IsActive")
            .replace("ProjectName", "d.ProjectName")
            .replace("Value", "d.Value")
            .replace("SeqNo", "d.SeqNo")
            .replace("MProductCategoryId", "b.m_product_category_id");
    }

}
