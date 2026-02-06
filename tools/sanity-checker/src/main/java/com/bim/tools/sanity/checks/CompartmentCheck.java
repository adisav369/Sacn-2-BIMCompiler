package com.bim.tools.sanity.checks;

import com.bim.tools.sanity.model.SanityModel;
import com.bim.tools.sanity.report.CheckResult;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Fire Compartment Sanity Check - Phase 65
 *
 * GEOMETRY IS MATHS: All validations are numerical proofs.
 *
 * Validates:
 * 1. COMPARTMENT_AREA_MATHS: Storey area <= max_area_m2 per ad_fire_compartment
 * 2. FIRE_RATING_MATHS: Fire-rated walls (PARTY/SHARED) >= required rating
 *
 * Code references:
 * - UBBL By-Law 166: Fire compartment area limits
 * - UBBL Table 3: Max compartment areas by occupancy group
 */
public class CompartmentCheck implements SanityCheck {

    // Default limits when no library DB (conservative Malaysian UBBL values)
    private static final double DEFAULT_MAX_AREA_M2 = 500.0;        // UBBL R group
    private static final double DEFAULT_MAX_AREA_SPRINK_M2 = 1000.0; // With sprinklers
    private static final double DEFAULT_MIN_RATING_HR = 1.0;         // 1 hour default

    // Library DB path (same as compiler uses)
    private static final String LIBRARY_DB_PATH = "library/component_library.db";

    @Override
    public String getId() { return "fire_compartment"; }

    @Override
    public String getName() { return "Fire Compartment Limits"; }

    @Override
    public CheckResult execute(SanityModel model, ADContext context) {
        String dbPath = model.getDbPath();

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {

            // Step 1: Extract storey areas
            List<StoreyArea> storeyAreas = extractStoreyAreas(conn);

            if (storeyAreas.isEmpty()) {
                return CheckResult.pass(getId(), getName())
                    .summary("No storeys found - skipping compartment check")
                    .build();
            }

            // Step 2: Check for sprinklers (affects bonus area)
            boolean hasSprinklers = hasSprinklers(conn);

            // Step 3: Get compartment limits from library
            CompartmentLimits limits = getCompartmentLimits(hasSprinklers);

            // Step 4: Validate storey areas against limits
            List<String> failures = new ArrayList<>();
            List<String> details = new ArrayList<>();
            double maxStoreyArea = 0;

            for (StoreyArea storey : storeyAreas) {
                if (storey.areaM2 > maxStoreyArea) {
                    maxStoreyArea = storey.areaM2;
                }

                double effectiveLimit = hasSprinklers ? limits.maxAreaSprinkM2 : limits.maxAreaM2;

                if (storey.areaM2 > effectiveLimit) {
                    failures.add(String.format("%s: %.0fm² exceeds limit %.0fm²",
                        storey.name, storey.areaM2, effectiveLimit));
                }

                details.add(String.format("AREA_MATHS: %s = %.0fm² (limit: %.0fm²)",
                    storey.name, storey.areaM2, effectiveLimit));
            }

            // Step 5: Validate fire-rated walls
            List<FireRatedWall> fireWalls = getFireRatedWalls(conn);
            int wallsBelowRating = 0;

            for (FireRatedWall wall : fireWalls) {
                if (wall.fireRatingHr < limits.minFireRatingHr) {
                    wallsBelowRating++;
                    details.add(String.format("RATING_MATHS: %s = %.1f hr < required %.1f hr",
                        wall.guid, wall.fireRatingHr, limits.minFireRatingHr));
                }
            }

            if (wallsBelowRating > 0) {
                failures.add(String.format("%d fire-rated walls below %.1f hr requirement",
                    wallsBelowRating, limits.minFireRatingHr));
            }

            // Build result
            if (!failures.isEmpty()) {
                CheckResult.Builder result = CheckResult.fail(getId(), getName())
                    .summary(String.format("Compartment violations: %d", failures.size()));
                for (String failure : failures) {
                    result.detail("FAIL: " + failure);
                }
                for (String detail : details) {
                    result.detail(detail);
                }
                result.guidance("Review UBBL By-Law 166 for compartment requirements");
                result.data("maxStoreyArea", maxStoreyArea);
                result.data("hasSprinklers", hasSprinklers);
                result.data("limit", hasSprinklers ? limits.maxAreaSprinkM2 : limits.maxAreaM2);
                return result.build();
            }

            // All checks pass
            CheckResult.Builder result = CheckResult.pass(getId(), getName())
                .summary(String.format("All %d storeys within %.0fm² limit%s",
                    storeyAreas.size(),
                    hasSprinklers ? limits.maxAreaSprinkM2 : limits.maxAreaM2,
                    hasSprinklers ? " (sprinklered)" : ""));

            for (String detail : details) {
                result.detail(detail);
            }

            if (!fireWalls.isEmpty()) {
                result.detail(String.format("WALL_RATING: %d fire-rated walls, all >= %.1f hr",
                    fireWalls.size(), limits.minFireRatingHr));
            }

            result.data("maxStoreyArea", maxStoreyArea);
            result.data("hasSprinklers", hasSprinklers);
            result.data("fireRatedWalls", fireWalls.size());

            return result.build();

        } catch (SQLException e) {
            return CheckResult.fail(getId(), getName())
                .summary("Database error: " + e.getMessage())
                .build();
        }
    }

    /**
     * Extract floor area per storey from IfcSpace elements.
     */
    private List<StoreyArea> extractStoreyAreas(Connection conn) throws SQLException {
        List<StoreyArea> result = new ArrayList<>();

        String sql = """
            SELECT
                em.storey,
                SUM((r.maxX - r.minX) * (r.maxY - r.minY)) as area_m2
            FROM elements_meta em
            JOIN elements_rtree r ON em.id = r.id
            WHERE em.ifc_class = 'IfcSpace'
            GROUP BY em.storey
            ORDER BY em.storey
            """;

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String storey = rs.getString("storey");
                double area = rs.getDouble("area_m2");
                if (storey != null && area > 0) {
                    result.add(new StoreyArea(storey, area));
                }
            }
        }

        return result;
    }

    /**
     * Check if building has sprinklers (affects area bonus).
     */
    private boolean hasSprinklers(Connection conn) throws SQLException {
        String sql = """
            SELECT COUNT(*) FROM elements_meta
            WHERE ifc_class = 'IfcFireSuppressionTerminal'
            """;

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        }
        return false;
    }

    /**
     * Get fire-rated walls from elements_meta.
     * Phase 65: Reads fire_rating_hr column.
     */
    private List<FireRatedWall> getFireRatedWalls(Connection conn) throws SQLException {
        List<FireRatedWall> result = new ArrayList<>();

        // Check if fire_rating_hr column exists
        boolean hasFireRating = false;
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA table_info(elements_meta)")) {
            while (rs.next()) {
                if ("fire_rating_hr".equals(rs.getString("name"))) {
                    hasFireRating = true;
                    break;
                }
            }
        }

        if (!hasFireRating) {
            return result; // Column doesn't exist in older DBs
        }

        String sql = """
            SELECT guid, fire_rating_hr
            FROM elements_meta
            WHERE fire_rating_hr IS NOT NULL AND fire_rating_hr > 0
            """;

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                result.add(new FireRatedWall(
                    rs.getString("guid"),
                    rs.getDouble("fire_rating_hr")
                ));
            }
        }

        return result;
    }

    /**
     * Get compartment limits from library DB, or use defaults.
     * Defaults to occupancy group R (residential) as most common.
     */
    private CompartmentLimits getCompartmentLimits(boolean hasSprinklers) {
        try (Connection libConn = DriverManager.getConnection("jdbc:sqlite:" + LIBRARY_DB_PATH)) {

            // Query for residential (R) as default occupancy
            String sql = """
                SELECT max_area_m2, max_area_sprink_m2, min_fire_rating_hr
                FROM ad_fire_compartment
                WHERE occupancy_group = 'R' AND is_active = 1
                LIMIT 1
                """;

            try (Statement stmt = libConn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                if (rs.next()) {
                    return new CompartmentLimits(
                        rs.getDouble("max_area_m2"),
                        rs.getDouble("max_area_sprink_m2"),
                        rs.getDouble("min_fire_rating_hr")
                    );
                }
            }

        } catch (SQLException e) {
            // Library DB not available, use defaults
        }

        return new CompartmentLimits(
            DEFAULT_MAX_AREA_M2,
            DEFAULT_MAX_AREA_SPRINK_M2,
            DEFAULT_MIN_RATING_HR
        );
    }

    // Data classes
    private record StoreyArea(String name, double areaM2) {}
    private record FireRatedWall(String guid, double fireRatingHr) {}
    private record CompartmentLimits(double maxAreaM2, double maxAreaSprinkM2, double minFireRatingHr) {}
}
