package com.bim.tools.sanity.checks;

import com.bim.tools.sanity.model.SanityModel;
import com.bim.tools.sanity.report.CheckResult;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Pattern B Compliance Check: All transforms must be (0,0,0).
 *
 * Pattern B: World-space geometry + identity (zero) transform.
 * This is the correct pattern for Bonsai/IFC export.
 *
 * This check catches "strewn objects" bugs where:
 * - Element is positioned via both geometry AND transform
 * - Results in double-positioning when Bonsai applies transform
 *
 * Phase 54: Sanity check for Pattern B compliance.
 */
public class PatternBCheck implements SanityCheck {

    private static final double TOLERANCE = 0.001; // 1mm tolerance

    @Override
    public String getId() { return "pattern_b"; }

    @Override
    public String getName() { return "Pattern B Compliance (Zero Transforms)"; }

    @Override
    public CheckResult execute(SanityModel model, ADContext context) {
        String dbPath = model.getDbPath();

        List<Violation> violations = new ArrayList<>();
        int totalChecked = 0;
        int atOrigin = 0;

        // Check element_transforms table
        String sql = """
            SELECT em.guid, em.ifc_class, em.element_name,
                   et.center_x, et.center_y, et.center_z
            FROM elements_meta em
            JOIN element_transforms et ON em.guid = et.guid
            """;

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                totalChecked++;

                double tx = rs.getDouble("center_x");
                double ty = rs.getDouble("center_y");
                double tz = rs.getDouble("center_z");

                boolean isZero = Math.abs(tx) < TOLERANCE &&
                                 Math.abs(ty) < TOLERANCE &&
                                 Math.abs(tz) < TOLERANCE;

                if (isZero) {
                    atOrigin++;
                } else {
                    violations.add(new Violation(
                        rs.getString("guid"),
                        rs.getString("ifc_class"),
                        rs.getString("element_name"),
                        tx, ty, tz
                    ));
                }
            }

        } catch (SQLException e) {
            return CheckResult.fail(getId(), getName())
                .summary("Database error: " + e.getMessage())
                .guidance("Ensure element_transforms table exists in database")
                .build();
        }

        // All transforms should be zero under Pattern B
        if (violations.isEmpty()) {
            return CheckResult.pass(getId(), getName())
                .summary(String.format("All %d elements have zero transforms (Pattern B compliant)",
                    totalChecked))
                .data("elementsChecked", totalChecked)
                .data("allAtOrigin", true)
                .build();
        }

        // Non-zero transforms found - this is a violation
        CheckResult.Builder result = CheckResult.fail(getId(), getName())
            .summary(String.format("%d of %d elements have non-zero transforms (Pattern B violation)",
                violations.size(), totalChecked));

        // Group by IFC class for summary
        long wallsWrong = violations.stream().filter(v -> "IfcWall".equals(v.ifcClass)).count();
        long doorsWrong = violations.stream().filter(v -> "IfcDoor".equals(v.ifcClass)).count();
        long windowsWrong = violations.stream().filter(v -> "IfcWindow".equals(v.ifcClass)).count();
        long othersWrong = violations.size() - wallsWrong - doorsWrong - windowsWrong;

        if (wallsWrong > 0) result.detail(String.format("IfcWall: %d violations", wallsWrong));
        if (doorsWrong > 0) result.detail(String.format("IfcDoor: %d violations", doorsWrong));
        if (windowsWrong > 0) result.detail(String.format("IfcWindow: %d violations", windowsWrong));
        if (othersWrong > 0) result.detail(String.format("Other: %d violations", othersWrong));

        // Show first few violations
        result.detail("");
        result.detail("Sample violations (should be 0,0,0):");
        int shown = 0;
        for (Violation v : violations) {
            if (shown >= 5) break;
            result.detail(String.format("  %s %s: (%.3f, %.3f, %.3f)",
                v.ifcClass, v.name != null ? v.name : v.guid,
                v.tx, v.ty, v.tz));
            shown++;
        }

        if (violations.size() > 5) {
            result.detail(String.format("  ... and %d more", violations.size() - 5));
        }

        result.guidance("Fix: writeInstance() should always use (0,0,0) transform. " +
            "Geometry must be in world-space coordinates.");
        result.data("elementsChecked", totalChecked);
        result.data("violations", violations.size());
        result.data("compliant", atOrigin);

        return result.build();
    }

    private record Violation(
        String guid,
        String ifcClass,
        String name,
        double tx, double ty, double tz
    ) {}
}
