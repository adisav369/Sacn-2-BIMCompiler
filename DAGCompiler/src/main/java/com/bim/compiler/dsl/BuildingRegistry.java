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
                   + "GeometryFailThreshold, AabbWidthMm, AabbDepthMm, AabbHeightMm "
                   + "FROM C_DocType " + whereClause;
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
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
                        rs.getString("DocBaseType"),
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
}
