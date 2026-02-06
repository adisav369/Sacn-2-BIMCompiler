package com.bim.tools.sanity.checks;

import com.bim.tools.sanity.model.SanityModel;
import com.bim.tools.sanity.report.CheckResult;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Phase 90: MEP Spacing Check
 *
 * Validates minimum separation between sprinklers and lights on the same floor.
 * Uses elements_rtree for position data (center of bounding box).
 * WARN if elements cluster too closely (< 0.5m separation).
 */
public class MEPSpacingCheck implements SanityCheck {

    private static final double MIN_SEPARATION_M = 0.5;

    @Override
    public String getId() { return "mep_spacing"; }

    @Override
    public String getName() { return "MEP Element Spacing"; }

    @Override
    public CheckResult execute(SanityModel model, ADContext context) {
        String dbPath = model.getDbPath();
        int clashes = 0;
        int totalChecked = 0;
        List<String> details = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {

            // Compare sprinkler centers vs light centers on same storey
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(
                    "SELECT s.guid as sname, " +
                    "  (sr.minX + sr.maxX) / 2.0 as sx, (sr.minY + sr.maxY) / 2.0 as sy, " +
                    "  l.guid as lname, " +
                    "  (lr.minX + lr.maxX) / 2.0 as lx, (lr.minY + lr.maxY) / 2.0 as ly, " +
                    "  s.storey " +
                    "FROM elements_meta s " +
                    "JOIN elements_rtree sr ON s.id = sr.id " +
                    "JOIN elements_meta l ON s.storey = l.storey " +
                    "JOIN elements_rtree lr ON l.id = lr.id " +
                    "WHERE s.ifc_class = 'IfcFireSuppressionTerminal' " +
                    "AND l.ifc_class = 'IfcLightFixture'")) {
                while (rs.next()) {
                    totalChecked++;
                    double dx = rs.getDouble("sx") - rs.getDouble("lx");
                    double dy = rs.getDouble("sy") - rs.getDouble("ly");
                    double dist = Math.sqrt(dx * dx + dy * dy);
                    if (dist < MIN_SEPARATION_M) {
                        clashes++;
                        if (clashes <= 5) {
                            details.add(String.format("Clash: sprinkler and light at %.2fm apart on %s",
                                dist, rs.getString("storey")));
                        }
                    }
                }
            }

        } catch (SQLException e) {
            return CheckResult.fail(getId(), getName())
                .summary("SQL error: " + e.getMessage())
                .build();
        }

        if (totalChecked == 0) {
            return CheckResult.pass(getId(), getName())
                .summary("No sprinkler-light pairs to check")
                .build();
        }

        if (clashes == 0) {
            return CheckResult.pass(getId(), getName())
                .summary(String.format("All %d sprinkler-light pairs have >= %.1fm separation",
                    totalChecked, MIN_SEPARATION_M))
                .build();
        }

        CheckResult.Builder result = CheckResult.warning(getId(), getName())
            .summary(String.format("%d sprinkler-light pairs closer than %.1fm", clashes, MIN_SEPARATION_M))
            .details(details)
            .guidance("Offset light grid from sprinkler grid to avoid overlap")
            .data("clashes", clashes)
            .data("totalPairs", totalChecked);

        if (clashes > 5) {
            result.detail(String.format("... and %d more clashes", clashes - 5));
        }

        return result.build();
    }
}
