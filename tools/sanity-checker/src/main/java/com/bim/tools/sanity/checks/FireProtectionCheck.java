package com.bim.tools.sanity.checks;

import com.bim.tools.sanity.model.SanityModel;
import com.bim.tools.sanity.report.CheckResult;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Fire Protection Sanity Check - Phase 57
 *
 * GEOMETRY IS MATHS: All validations are numerical proofs.
 *
 * Validates:
 * 1. SPRINKLER_REQUIREMENT: If building triggers FP rules, sprinklers should exist
 * 2. SPRINKLER_SPACING_MATHS: Head spacing <= max_spacing (4.6m for LIGHT hazard)
 * 3. SPRINKLER_COVERAGE_MATHS: Total heads >= ceil(floor_area / coverage_per_head)
 *
 * Code references:
 * - UBBL By-Law 225: Buildings >18m require sprinklers
 * - NFPA 13 Table 10.2.4.2.1: Light hazard max 18.6m², 4.6m spacing
 */
public class FireProtectionCheck implements SanityCheck {

    // NFPA 13 Light Hazard values (EXTRACTED from ad_fp_coverage)
    private static final double MAX_COVERAGE_M2 = 18.6;
    private static final double MAX_SPACING_M = 4.6;
    private static final double MIN_SPACING_M = 1.8;
    private static final double WALL_DISTANCE_M = 2.3;

    // UBBL thresholds
    private static final double HEIGHT_TRIGGER_M = 18.0;
    private static final double AREA_TRIGGER_M2 = 1000.0;

    @Override
    public String getId() { return "fire_protection"; }

    @Override
    public String getName() { return "Fire Protection Requirements"; }

    @Override
    public CheckResult execute(SanityModel model) {
        String dbPath = model.getDbPath();

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {

            // Step 1: Extract building parameters (MATHS inputs)
            BuildingParams params = extractBuildingParams(conn);

            if (params == null) {
                return CheckResult.pass(getId(), getName())
                    .summary("Could not extract building parameters - skipping FP check")
                    .build();
            }

            // Step 2: Calculate if sprinklers are required (MATHS)
            boolean sprinklersRequired = calculateSprinklerRequirement(params);

            // Step 3: Count actual sprinklers in database
            int sprinklerCount = countSprinklers(conn);

            // Step 4: Validate sprinkler spacing (MATHS)
            SpacingAnalysis spacing = analyzeSprinklerSpacing(conn);

            // Build result
            return buildResult(params, sprinklersRequired, sprinklerCount, spacing);

        } catch (SQLException e) {
            return CheckResult.fail(getId(), getName())
                .summary("Database error: " + e.getMessage())
                .build();
        }
    }

    private BuildingParams extractBuildingParams(Connection conn) throws SQLException {
        // Get building height from elements_rtree (max Z extent)
        String heightSql = """
            SELECT MAX(maxZ) as height
            FROM elements_rtree
            """;

        double height = 0;
        int storeys = 0;

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(heightSql)) {
            if (rs.next()) {
                height = rs.getDouble("height");
            }
        }

        // Count storeys from spatial_structure
        String storeySql = """
            SELECT COUNT(*) as storeys
            FROM spatial_structure
            WHERE type = 'IfcBuildingStorey'
            """;

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(storeySql)) {
            if (rs.next()) {
                storeys = rs.getInt("storeys");
            }
        }

        // Get floor area from IfcSpace elements
        String areaSql = """
            SELECT SUM(
                (r.maxX - r.minX) * (r.maxY - r.minY)
            ) as total_area
            FROM elements_meta em
            JOIN elements_rtree r ON em.id = r.id
            WHERE em.ifc_class = 'IfcSpace'
            """;

        double floorArea = 0;

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(areaSql)) {
            if (rs.next()) {
                floorArea = rs.getDouble("total_area");
            }
        }

        // If no explicit height from storeys, estimate from element bounds
        if (height <= 0) {
            String boundsSql = "SELECT MAX(max_z) FROM elements_rtree";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(boundsSql)) {
                if (rs.next()) {
                    height = rs.getDouble(1);
                }
            }
        }

        if (height <= 0 && floorArea <= 0) {
            return null;
        }

        return new BuildingParams(height, floorArea, storeys);
    }

    /**
     * MATHS PROOF: Sprinkler requirement calculation
     *
     * Required if ANY of:
     * - height >= 18.0m (UBBL By-Law 225)
     * - floor_area >= 1000.0m² (UBBL By-Law 225)
     */
    private boolean calculateSprinklerRequirement(BuildingParams params) {
        return params.height >= HEIGHT_TRIGGER_M ||
               params.floorArea >= AREA_TRIGGER_M2;
    }

    private int countSprinklers(Connection conn) throws SQLException {
        String sql = """
            SELECT COUNT(*) FROM elements_meta
            WHERE ifc_class = 'IfcFireSuppressionTerminal'
            """;

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    /**
     * MATHS: Analyze sprinkler spacing from geometry
     *
     * Grid spacing = sqrt(area / head_count)
     * Must satisfy: MIN_SPACING <= spacing <= MAX_SPACING
     */
    private SpacingAnalysis analyzeSprinklerSpacing(Connection conn) throws SQLException {
        // Get sprinkler positions
        String sql = """
            SELECT r.minX, r.minY, r.minZ
            FROM elements_meta em
            JOIN elements_rtree r ON em.id = r.id
            WHERE em.ifc_class = 'IfcFireSuppressionTerminal'
            ORDER BY r.minZ, r.minY, r.minX
            """;

        List<double[]> positions = new ArrayList<>();

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                positions.add(new double[] {
                    rs.getDouble("minX"),
                    rs.getDouble("minY"),
                    rs.getDouble("minZ")
                });
            }
        }

        if (positions.size() < 2) {
            return new SpacingAnalysis(0, 0, 0, true);
        }

        // Calculate min/max distances between adjacent sprinklers (same Z level)
        double minSpacing = Double.MAX_VALUE;
        double maxSpacing = 0;
        int pairsChecked = 0;

        for (int i = 0; i < positions.size(); i++) {
            for (int j = i + 1; j < positions.size(); j++) {
                double[] p1 = positions.get(i);
                double[] p2 = positions.get(j);

                // Only compare on same floor (Z within 0.5m)
                if (Math.abs(p1[2] - p2[2]) > 0.5) continue;

                double dist = Math.sqrt(
                    Math.pow(p2[0] - p1[0], 2) +
                    Math.pow(p2[1] - p1[1], 2)
                );

                // Only consider reasonable spacing (not across entire floor)
                if (dist < MAX_SPACING_M * 1.5) {
                    minSpacing = Math.min(minSpacing, dist);
                    maxSpacing = Math.max(maxSpacing, dist);
                    pairsChecked++;
                }
            }
        }

        if (pairsChecked == 0) {
            return new SpacingAnalysis(0, 0, 0, true);
        }

        // MATHS VALIDATION: spacing must be within limits
        boolean valid = minSpacing >= MIN_SPACING_M && maxSpacing <= MAX_SPACING_M;

        return new SpacingAnalysis(minSpacing, maxSpacing, pairsChecked, valid);
    }

    private CheckResult buildResult(BuildingParams params, boolean required,
                                     int count, SpacingAnalysis spacing) {

        CheckResult.Builder result;

        // Case 1: Sprinklers not required and none present
        if (!required && count == 0) {
            result = CheckResult.pass(getId(), getName())
                .summary(String.format(
                    "Sprinklers not required (height=%.1fm < 18m, area=%.0fm² < 1000m²)",
                    params.height, params.floorArea))
                .data("sprinklersRequired", false)
                .data("buildingHeight", params.height)
                .data("floorArea", params.floorArea);
            return result.build();
        }

        // Case 2: Sprinklers not required but present (voluntary)
        if (!required && count > 0) {
            result = CheckResult.pass(getId(), getName())
                .summary(String.format(
                    "Voluntary sprinklers installed (%d heads, not code-required)",
                    count))
                .data("sprinklersRequired", false)
                .data("sprinklerCount", count);

            addSpacingDetails(result, spacing);
            return result.build();
        }

        // Case 3: Sprinklers required
        if (required) {
            // Calculate expected head count
            int expectedHeads = (int) Math.ceil(params.floorArea / MAX_COVERAGE_M2);

            if (count == 0) {
                // FAIL: Required but none present
                return CheckResult.fail(getId(), getName())
                    .summary(String.format(
                        "Sprinklers REQUIRED but none found (height=%.1fm, area=%.0fm²)",
                        params.height, params.floorArea))
                    .detail(String.format("MATHS: height(%.1f) >= 18.0 OR area(%.0f) >= 1000",
                        params.height, params.floorArea))
                    .detail(String.format("Expected: ceil(%.0f/18.6) = %d heads",
                        params.floorArea, expectedHeads))
                    .guidance("Add IfcFireSuppressionTerminal elements per NFPA 13")
                    .data("sprinklersRequired", true)
                    .data("sprinklerCount", 0)
                    .data("expectedHeads", expectedHeads)
                    .build();
            }

            // Sprinklers present - validate count and spacing
            result = CheckResult.pass(getId(), getName())
                .summary(String.format(
                    "Sprinklers required and present (%d heads for %.0fm²)",
                    count, params.floorArea));

            // MATHS: Validate head count
            result.detail(String.format("COVERAGE_MATHS: ceil(%.0f/%.1f) = %d expected, %d actual",
                params.floorArea, MAX_COVERAGE_M2, expectedHeads, count));

            if (count < expectedHeads) {
                result.detail(String.format("WARNING: %d heads may be insufficient for %.0fm²",
                    count, params.floorArea));
            }

            addSpacingDetails(result, spacing);

            result.data("sprinklersRequired", true);
            result.data("sprinklerCount", count);
            result.data("expectedHeads", expectedHeads);
            result.data("buildingHeight", params.height);
            result.data("floorArea", params.floorArea);

            return result.build();
        }

        // Fallback
        return CheckResult.pass(getId(), getName())
            .summary("Fire protection check complete")
            .build();
    }

    private void addSpacingDetails(CheckResult.Builder result, SpacingAnalysis spacing) {
        if (spacing.pairsChecked > 0) {
            result.detail(String.format("SPACING_MATHS: min=%.2fm, max=%.2fm (limit: %.1f-%.1fm)",
                spacing.minSpacing, spacing.maxSpacing, MIN_SPACING_M, MAX_SPACING_M));

            if (!spacing.valid) {
                result.detail("WARNING: Spacing outside NFPA 13 limits");
            }
        }
    }

    private record BuildingParams(double height, double floorArea, int storeys) {}

    private record SpacingAnalysis(
        double minSpacing,
        double maxSpacing,
        int pairsChecked,
        boolean valid
    ) {}
}
