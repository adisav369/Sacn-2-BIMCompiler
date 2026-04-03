package com.bim.compiler.dsl;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads building registry from the BUILDING m_bom row in BOM.db.
 *
 * <p>iDempiere pattern: C_DocType = document type stub for 'Construction Order' only.
 * C_Order = transactional (output.db only, created at compile time).
 * Building registry (DSL, paths, thresholds) lives in m_bom (J4_003).
 *
 * <p>Each BUILDING m_bom row is a complete compilation template.
 * Adding a new building = one extraction run, zero Java files.
 */
public class BuildingRegistry {

    private static String dbPath() { return System.getProperty("bom.db"); }

    /**
     * Building type definition from m_bom BUILDING row.
     * Domain config only — no transactional state (DocStatus, checksums go on C_Order in output.db).
     * C_DocType is a stub for iDempiere 'Construction Order' compatibility; not the registry source.
     */
    public record BuildingEntry(
        String docTypeId,           // C_DocType.Value ('RE_SH', 'RE_DX', 'ST_SH', 'ST_DX')
        String projectName,         // building instance name ('Ifc4_SampleHouse')
        String name,                // human-readable ('Sample House')
        String mProductCategoryId,  // RE, CO, IN, ST — from m_bom.m_product_category_id
        String docSubType,          // SH, DX, TB, TE — from C_DocType.doc_sub_type / m_bom.doc_sub_type
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
        double aabbHeightMm,
        String jurisdiction,        // MY, US, UK, SG — nullable (skip compliance if absent)
        String codeEdition          // UBBL_1984_AMD2007 — nullable
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
        return load("ORDER BY b.seq_no");
    }

    /**
     * Load a building type by ProjectName (building instance name).
     */
    public static BuildingEntry loadById(String buildingId) {
        List<BuildingEntry> entries = load("AND b.project_name = ?", buildingId);
        return entries.isEmpty() ? null : entries.get(0);
    }

    /**
     * Load active building types filtered by M_Product_Category (RE, CO, IN, ST).
     * Used by compile_building() to select RE for ENBLOC, ST for WALKTHRU.
     */
    public static List<BuildingEntry> loadByProductCategory(String category) {
        return load("AND mpc.Value = ? ORDER BY b.seq_no", category);
    }

    /**
     * Load a building type by docTypeId (e.g. 'RE_RM' = category + '_' + doc_sub_type).
     */
    public static BuildingEntry loadByDocTypeId(String docTypeId) {
        List<BuildingEntry> entries = load("AND mpc.Value || '_' || b.doc_sub_type = ?", docTypeId);
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

    // Base SQL: reads building registry from m_bom BUILDING row.
    // C_DocType is now a stub — do not join it here.
    // Implementing §J4_003 — registry columns moved from C_DocType to m_bom.
    private static final String BASE_SQL =
        "SELECT mpc.Value || '_' || b.doc_sub_type AS C_DocType_ID, "
        + "b.project_name AS ProjectName, b.Name, "
        + "mpc.Value AS MProductCategoryId, "
        + "b.doc_sub_type AS DocSubType, "
        + "b.output_db_path AS OutputDbPath, "
        + "b.reference_db_path AS ReferenceDbPath, b.is_active AS IsActive, "
        + "b.seq_no AS SeqNo, "
        + "COALESCE(b.expected_elements, 0) AS ExpectedElements, "
        + "COALESCE(b.provenance, 'EXTRACTED') AS Provenance, "
        + "b.description AS Description, "
        + "COALESCE(b.geometry_fail_threshold, 0) AS GeometryFailThreshold, "
        + "COALESCE(b.aabb_width_mm, 0) AS AabbWidthMm, "
        + "COALESCE(b.aabb_depth_mm, 0) AS AabbDepthMm, "
        + "COALESCE(b.aabb_height_mm, 0) AS AabbHeightMm, "
        + "b.jurisdiction AS Jurisdiction, b.code_edition AS CodeEdition "
        + "FROM m_bom b "
        + "JOIN M_Product_Category mpc ON b.m_product_category_id = mpc.M_Product_Category_ID "
        + "WHERE b.bom_type = 'BUILDING' AND b.is_active = 1 ";

    private static List<BuildingEntry> load(String extraClause, String... params) {
        List<BuildingEntry> entries = new ArrayList<>();
        String sql = BASE_SQL + extraClause;
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
                        rs.getDouble("AabbHeightMm"),
                        rs.getString("Jurisdiction"),
                        rs.getString("CodeEdition")
                    ));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load building registry from m_bom: " + e.getMessage(), e);
        }
        return entries;
    }

}
