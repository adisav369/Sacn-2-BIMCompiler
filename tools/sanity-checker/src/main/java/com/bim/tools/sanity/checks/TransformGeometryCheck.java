package com.bim.tools.sanity.checks;

import com.bim.tools.sanity.model.SanityModel;
import com.bim.tools.sanity.report.CheckResult;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Positioning Invariant Check: Transform must match geometry center.
 *
 * This check verifies that the element_transforms table (used by Bonsai for placement)
 * matches the actual geometry center computed from elements_rtree bounds.
 *
 * Invariant: For every element e,
 *   |e.transform.center - e.geometry.centroid| < tolerance
 *
 * This catches bugs where:
 * - Transforms are hardcoded at origin (0,0,0) but geometry is elsewhere
 * - Transform position doesn't match actual element placement
 *
 * Phase 53: Positioning test for early anomaly detection.
 */
public class TransformGeometryCheck implements SanityCheck {

    private static final double TOLERANCE = 0.1; // 100mm tolerance

    @Override
    public String getId() { return "transform_geometry"; }

    @Override
    public String getName() { return "Transform-Geometry Consistency"; }

    @Override
    public CheckResult execute(SanityModel model) {
        String dbPath = model.getDbPath();

        List<Mismatch> mismatches = new ArrayList<>();
        int totalChecked = 0;
        int atOrigin = 0;

        String sql = """
            SELECT em.guid, em.ifc_class, em.element_name,
                   et.center_x as tx, et.center_y as ty, et.center_z as tz,
                   (r.minX + r.maxX)/2 as gx,
                   (r.minY + r.maxY)/2 as gy,
                   (r.minZ + r.maxZ)/2 as gz
            FROM elements_meta em
            JOIN element_transforms et ON em.guid = et.guid
            JOIN elements_rtree r ON em.id = r.id
            """;

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                totalChecked++;

                double tx = rs.getDouble("tx");
                double ty = rs.getDouble("ty");
                double tz = rs.getDouble("tz");
                double gx = rs.getDouble("gx");
                double gy = rs.getDouble("gy");
                double gz = rs.getDouble("gz");

                // Check if transform is at origin
                if (Math.abs(tx) < 0.001 && Math.abs(ty) < 0.001 && Math.abs(tz) < 0.001) {
                    atOrigin++;
                }

                // Check if transform matches geometry
                double dx = Math.abs(tx - gx);
                double dy = Math.abs(ty - gy);
                double dz = Math.abs(tz - gz);

                if (dx > TOLERANCE || dy > TOLERANCE || dz > TOLERANCE) {
                    mismatches.add(new Mismatch(
                        rs.getString("guid"),
                        rs.getString("ifc_class"),
                        rs.getString("element_name"),
                        tx, ty, tz,
                        gx, gy, gz,
                        Math.max(dx, Math.max(dy, dz))
                    ));
                }
            }

        } catch (SQLException e) {
            return CheckResult.fail(getId(), getName())
                .summary("Database error: " + e.getMessage())
                .guidance("Ensure element_transforms table exists in database")
                .build();
        }

        // Build result
        if (mismatches.isEmpty()) {
            return CheckResult.pass(getId(), getName())
                .summary(String.format("All %d elements have consistent transforms", totalChecked))
                .data("elementsChecked", totalChecked)
                .data("tolerance_m", TOLERANCE)
                .build();
        }

        // Group mismatches by IFC class for summary
        long columnsWrong = mismatches.stream().filter(m -> "IfcColumn".equals(m.ifcClass)).count();
        long membersWrong = mismatches.stream().filter(m -> "IfcMember".equals(m.ifcClass)).count();
        long doorsWrong = mismatches.stream().filter(m -> "IfcDoor".equals(m.ifcClass)).count();
        long othersWrong = mismatches.size() - columnsWrong - membersWrong - doorsWrong;

        CheckResult.Builder result = CheckResult.fail(getId(), getName())
            .summary(String.format("%d of %d elements have transform-geometry mismatch",
                mismatches.size(), totalChecked));

        // Add breakdown by type
        if (columnsWrong > 0) {
            result.detail(String.format("IfcColumn: %d mismatches (transforms at origin)", columnsWrong));
        }
        if (membersWrong > 0) {
            result.detail(String.format("IfcMember: %d mismatches (transforms at origin)", membersWrong));
        }
        if (doorsWrong > 0) {
            result.detail(String.format("IfcDoor: %d mismatches (position offset)", doorsWrong));
        }
        if (othersWrong > 0) {
            result.detail(String.format("Other: %d mismatches", othersWrong));
        }

        // Show first few specific mismatches
        result.detail("");
        result.detail("Sample mismatches (transform vs geometry center):");
        int shown = 0;
        for (Mismatch m : mismatches) {
            if (shown >= 5) break;
            result.detail(String.format("  %s %s: (%.1f,%.1f,%.1f) vs (%.1f,%.1f,%.1f) [Δ=%.2fm]",
                m.ifcClass, m.name != null ? m.name : "",
                m.tx, m.ty, m.tz,
                m.gx, m.gy, m.gz,
                m.maxDelta));
            shown++;
        }

        if (mismatches.size() > 5) {
            result.detail(String.format("  ... and %d more", mismatches.size() - 5));
        }

        // Add origin warning if many transforms at origin
        if (atOrigin > totalChecked / 2) {
            result.detail("");
            result.detail(String.format("WARNING: %d of %d transforms are at origin (0,0,0)",
                atOrigin, totalChecked));
            result.detail("This suggests writeElement() is not computing center from geometry");
        }

        result.guidance("Fix: writeElement() should pass geometry center to writeInstance(), not (0,0,0)");
        result.data("elementsChecked", totalChecked);
        result.data("mismatches", mismatches.size());
        result.data("transformsAtOrigin", atOrigin);
        result.data("tolerance_m", TOLERANCE);

        return result.build();
    }

    private record Mismatch(
        String guid,
        String ifcClass,
        String name,
        double tx, double ty, double tz,
        double gx, double gy, double gz,
        double maxDelta
    ) {}
}
