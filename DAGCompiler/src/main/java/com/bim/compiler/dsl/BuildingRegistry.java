package com.bim.compiler.dsl;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads ad_building_registry from component_library.db.
 *
 * iDempiere pattern: one engine, N configurations.
 * Each row is a complete compilation unit — DSL + paths + thresholds.
 * Adding a new building = one SQL INSERT, zero Java files.
 */
public class BuildingRegistry {

    private static final String DB_PATH = "library/component_library.db";

    public record BuildingEntry(
        String id,
        String name,
        String type,
        String dslContent,
        String outputDbPath,
        String referenceDbPath,
        boolean isActive,
        int seqNo,
        int expectedElements,
        String spatialDigest,
        String provenance,
        String description
    ) {
        public boolean isGenerative() {
            return "GENERATIVE".equals(provenance);
        }

        public boolean hasReference() {
            return referenceDbPath != null && !referenceDbPath.isBlank();
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
     * Load all active buildings ordered by seq_no.
     */
    public static List<BuildingEntry> loadActive() {
        return load("WHERE is_active = 1 ORDER BY seq_no");
    }

    /**
     * Load a single building by ID.
     */
    public static BuildingEntry loadById(String buildingId) {
        List<BuildingEntry> entries = load("WHERE building_id = ?", buildingId);
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
        String sql = "SELECT building_id, building_name, building_type, dsl_content, "
                   + "output_db_path, reference_db_path, is_active, seq_no, "
                   + "expected_elements, spatial_digest, provenance, description "
                   + "FROM ad_building_registry " + whereClause;
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setString(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    entries.add(new BuildingEntry(
                        rs.getString("building_id"),
                        rs.getString("building_name"),
                        rs.getString("building_type"),
                        rs.getString("dsl_content"),
                        rs.getString("output_db_path"),
                        rs.getString("reference_db_path"),
                        rs.getInt("is_active") == 1,
                        rs.getInt("seq_no"),
                        rs.getInt("expected_elements"),
                        rs.getString("spatial_digest"),
                        rs.getString("provenance"),
                        rs.getString("description")
                    ));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load building registry: " + e.getMessage(), e);
        }
        return entries;
    }
}
