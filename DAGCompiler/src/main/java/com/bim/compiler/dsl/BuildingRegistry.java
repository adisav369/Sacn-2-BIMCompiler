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

    private static final String DB_PATH = "library/BOM.db";

    /**
     * Building type definition from C_DocType.
     * Domain config only — no transactional state (DocStatus, checksums go on C_Order in output.db).
     */
    public record BuildingEntry(
        String docTypeId,           // C_DocType_ID ('RE_SH', 'RE_DX')
        String projectName,         // building instance name ('Ifc4_SampleHouse')
        String name,                // human-readable ('Sample House')
        String docBaseType,         // RE, CO, IN
        String docSubType,          // SH, DX, TB, TE, ST
        String dslContent,          // DSL template text
        String outputDbPath,        // output DB path
        String referenceDbPath,     // reference DB for verification
        boolean isActive,
        int seqNo,
        int expectedElements,
        String provenance,          // EXTRACTED | GENERATIVE
        String description,
        int geometryFailThreshold,
        double aabbWidthMm,         // standard domain AABB (reference envelope)
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
     * Load a building type by C_DocType_ID.
     */
    public static BuildingEntry loadByDocTypeId(String docTypeId) {
        List<BuildingEntry> entries = load("WHERE C_DocType_ID = ?", docTypeId);
        return entries.isEmpty() ? null : entries.get(0);
    }

    /**
     * Load assertions for a building.
     */
    public static List<BuildingAssertion> loadAssertions(String buildingId) {
        List<BuildingAssertion> result = new ArrayList<>();
        String sql = "SELECT building_id, assertion_id, element_match, property, operator, expected, tolerance "
                   + "FROM ad_building_assertions WHERE building_id = ? ORDER BY assertion_id";
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
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
        String sql = "SELECT C_DocType_ID, ProjectName, Name, DocBaseType, DocSubType, "
                   + "DSLContent, OutputDbPath, ReferenceDbPath, IsActive, SeqNo, "
                   + "ExpectedElements, Provenance, Description, "
                   + "GeometryFailThreshold "
                   + "FROM C_DocType " + whereClause;
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setString(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String docSubType = rs.getString("DocSubType");
                    double[] aabb = computeBomAabb(conn, docSubType);
                    entries.add(new BuildingEntry(
                        rs.getString("C_DocType_ID"),
                        rs.getString("ProjectName"),
                        rs.getString("Name"),
                        rs.getString("DocBaseType"),
                        docSubType,
                        rs.getString("DSLContent"),
                        rs.getString("OutputDbPath"),
                        rs.getString("ReferenceDbPath"),
                        rs.getInt("IsActive") == 1,
                        rs.getInt("SeqNo"),
                        rs.getInt("ExpectedElements"),
                        rs.getString("Provenance"),
                        rs.getString("Description"),
                        rs.getInt("GeometryFailThreshold"),
                        aabb[0], aabb[1], aabb[2]
                    ));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load building registry from C_DocType: " + e.getMessage(), e);
        }
        return entries;
    }

    /**
     * Compute AABB envelope from EB_ BOM lines (ground truth from data, never invented).
     * Falls back to 0,0,0 if no EB_ BOM exists for this DocSubType.
     */
    private static double[] computeBomAabb(Connection conn, String docSubType) {
        String sql = "SELECT "
                + "(MAX(bl.dx + bl.allocated_width_mm/2000.0) "
                + " - MIN(bl.dx - bl.allocated_width_mm/2000.0)) * 1000, "
                + "(MAX(bl.dy + bl.allocated_depth_mm/2000.0) "
                + " - MIN(bl.dy - bl.allocated_depth_mm/2000.0)) * 1000, "
                + "(MAX(bl.dz + bl.allocated_height_mm/2000.0) "
                + " - MIN(bl.dz - bl.allocated_height_mm/2000.0)) * 1000 "
                + "FROM m_bom_line bl "
                + "JOIN m_bom b ON bl.bom_id = b.bom_id "
                + "WHERE b.bom_id LIKE 'EB_%' AND b.doc_sub_type = ? AND bl.is_active = 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, docSubType);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getObject(1) != null) {
                    return new double[]{rs.getDouble(1), rs.getDouble(2), rs.getDouble(3)};
                }
            }
        } catch (SQLException e) {
            System.err.println("[BuildingRegistry] AABB compute failed for " + docSubType + ": " + e.getMessage());
        }
        return new double[]{0, 0, 0};
    }
}
