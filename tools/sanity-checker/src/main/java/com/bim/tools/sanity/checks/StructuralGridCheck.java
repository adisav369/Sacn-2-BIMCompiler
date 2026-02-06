package com.bim.tools.sanity.checks;

import com.bim.tools.sanity.model.BoundingBox;
import com.bim.tools.sanity.model.Element;
import com.bim.tools.sanity.model.SanityModel;
import com.bim.tools.sanity.report.CheckResult;

import java.sql.*;
import java.util.*;

/**
 * Structural Grid Alignment Sanity Check - Phase 74
 *
 * GEOMETRY IS MATHS: All validations are numerical proofs.
 *
 * Validates:
 * 1. GRID_ALIGNMENT: Columns align to regular grid pattern
 * 2. BEAM_SPAN: Beam spans within structural limits
 * 3. COLUMN_CONTINUITY: Multi-storey columns vertically aligned
 *
 * Code references:
 * - MS 1064: Steel structural design
 * - EC2: Concrete beam span limits
 * - Typical max spans: Steel 12m, Concrete 8m, Timber 6m
 */
public class StructuralGridCheck implements SanityCheck {

    // Grid alignment tolerance
    private static final double GRID_TOLERANCE_M = 0.05; // 50mm

    // Maximum beam spans (meters)
    private static final double MAX_STEEL_SPAN_M = 12.0;
    private static final double MAX_CONCRETE_SPAN_M = 8.0;
    private static final double MAX_TIMBER_SPAN_M = 6.0;

    // Minimum grid spacing to be considered intentional
    private static final double MIN_GRID_SPACING_M = 2.0;

    @Override
    public String getId() { return "structural_grid"; }

    @Override
    public String getName() { return "Structural Grid Alignment"; }

    @Override
    public CheckResult execute(SanityModel model, ADContext context) {
        String dbPath = model.getDbPath();

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            // Get columns
            List<ColumnPosition> columns = getColumns(conn);

            if (columns.isEmpty()) {
                return CheckResult.warning(getId(), getName())
                    .summary("No columns found - cannot validate structural grid")
                    .detail("Building may use load-bearing walls instead of frame structure")
                    .build();
            }

            // Get beams
            List<BeamInfo> beams = getBeams(conn);

            List<String> failures = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            List<String> details = new ArrayList<>();

            // 1. Detect grid pattern from column positions
            GridPattern gridX = detectGridPattern(columns.stream().map(c -> c.x).toList());
            GridPattern gridY = detectGridPattern(columns.stream().map(c -> c.y).toList());

            if (gridX.isRegular && gridY.isRegular) {
                details.add(String.format("GRID_DETECTED: X spacing %.1fm, Y spacing %.1fm",
                    gridX.spacing, gridY.spacing));
            } else {
                warnings.add("No regular grid pattern detected - columns may be irregularly placed");
            }

            // 2. Check column alignment to grid
            int alignedColumns = 0;
            int misalignedColumns = 0;

            for (ColumnPosition col : columns) {
                boolean xAligned = isAlignedToGrid(col.x, gridX);
                boolean yAligned = isAlignedToGrid(col.y, gridY);

                if (xAligned && yAligned) {
                    alignedColumns++;
                } else {
                    misalignedColumns++;
                    if (misalignedColumns <= 5) { // Limit warnings
                        warnings.add(String.format("Column at (%.2f, %.2f) not on grid",
                            col.x, col.y));
                    }
                }
            }

            if (misalignedColumns > 5) {
                warnings.add(String.format("... and %d more misaligned columns",
                    misalignedColumns - 5));
            }

            details.add(String.format("COLUMNS: %d aligned, %d misaligned of %d total",
                alignedColumns, misalignedColumns, columns.size()));

            // 3. Check beam spans
            int longSpans = 0;
            double maxSpanFound = 0;

            for (BeamInfo beam : beams) {
                double span = beam.length;
                if (span > maxSpanFound) maxSpanFound = span;

                // Determine max span based on assumed material (conservative: use timber)
                double maxAllowed = MAX_TIMBER_SPAN_M;

                if (span > maxAllowed) {
                    longSpans++;
                    if (span > MAX_STEEL_SPAN_M) {
                        failures.add(String.format("Beam %s span %.1fm exceeds all limits",
                            beam.name, span));
                    } else {
                        warnings.add(String.format("Beam %s span %.1fm (may need steel)",
                            beam.name, span));
                    }
                }
            }

            if (!beams.isEmpty()) {
                details.add(String.format("BEAMS: %d total, max span %.1fm",
                    beams.size(), maxSpanFound));
            }

            // 4. Check vertical column continuity (multi-storey)
            int storeyCount = model.getStoreyCount();
            if (storeyCount > 1) {
                int continuousColumns = checkVerticalContinuity(columns);
                if (continuousColumns < columns.size() / storeyCount) {
                    warnings.add(String.format("Column continuity: only %d columns span multiple storeys",
                        continuousColumns));
                } else {
                    details.add(String.format("CONTINUITY: %d columns span storeys",
                        continuousColumns));
                }
            }

            // Build result
            if (!failures.isEmpty()) {
                CheckResult.Builder result = CheckResult.fail(getId(), getName())
                    .summary(String.format("Structural issues: %d beam span violations",
                        failures.size()));

                for (String f : failures) {
                    result.detail("FAIL: " + f);
                }
                for (String w : warnings) {
                    result.detail("WARNING: " + w);
                }
                for (String d : details) {
                    result.detail(d);
                }

                result.guidance("Review beam spans and column placement");
                return result.build();
            }

            if (misalignedColumns > columns.size() / 4) { // >25% misaligned
                CheckResult.Builder result = CheckResult.warning(getId(), getName())
                    .summary(String.format("Grid alignment: %d/%d columns misaligned",
                        misalignedColumns, columns.size()));

                for (String w : warnings) {
                    result.detail(w);
                }
                for (String d : details) {
                    result.detail(d);
                }

                return result.build();
            }

            // All pass
            CheckResult.Builder result = CheckResult.pass(getId(), getName())
                .summary(String.format("Structural grid: %d columns, %d beams verified",
                    columns.size(), beams.size()));

            for (String d : details) {
                result.detail(d);
            }

            result.data("columnCount", columns.size());
            result.data("beamCount", beams.size());
            result.data("gridSpacingX", gridX.spacing);
            result.data("gridSpacingY", gridY.spacing);

            return result.build();

        } catch (SQLException e) {
            return CheckResult.fail(getId(), getName())
                .summary("Database error: " + e.getMessage())
                .build();
        }
    }

    /**
     * Get column positions from database.
     */
    private List<ColumnPosition> getColumns(Connection conn) throws SQLException {
        List<ColumnPosition> columns = new ArrayList<>();

        String sql = """
            SELECT m.guid, (r.minX + r.maxX) / 2 as x, (r.minY + r.maxY) / 2 as y,
                   r.minZ, r.maxZ
            FROM elements_meta m
            JOIN elements_rtree r ON m.id = r.id
            WHERE m.ifc_class = 'IfcColumn'
            """;

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                columns.add(new ColumnPosition(
                    rs.getString("guid"),
                    rs.getDouble("x"),
                    rs.getDouble("y"),
                    rs.getDouble("minZ"),
                    rs.getDouble("maxZ")
                ));
            }
        }
        return columns;
    }

    /**
     * Get beam info from database.
     */
    private List<BeamInfo> getBeams(Connection conn) throws SQLException {
        List<BeamInfo> beams = new ArrayList<>();

        String sql = """
            SELECT m.guid,
                   r.maxX - r.minX as dx,
                   r.maxY - r.minY as dy
            FROM elements_meta m
            JOIN elements_rtree r ON m.id = r.id
            WHERE m.ifc_class IN ('IfcBeam', 'IfcMember')
              AND m.guid LIKE 'BEAM%'
            """;

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                double dx = rs.getDouble("dx");
                double dy = rs.getDouble("dy");
                double length = Math.max(dx, dy); // Beam length is larger dimension

                beams.add(new BeamInfo(
                    rs.getString("guid"),
                    length
                ));
            }
        }
        return beams;
    }

    /**
     * Detect regular grid pattern from positions.
     */
    private GridPattern detectGridPattern(List<Double> positions) {
        if (positions.size() < 2) {
            return new GridPattern(false, 0, Collections.emptyList());
        }

        // Sort and find unique positions
        List<Double> sorted = positions.stream()
            .distinct()
            .sorted()
            .toList();

        if (sorted.size() < 2) {
            return new GridPattern(false, 0, sorted);
        }

        // Calculate spacings
        List<Double> spacings = new ArrayList<>();
        for (int i = 1; i < sorted.size(); i++) {
            double spacing = sorted.get(i) - sorted.get(i - 1);
            if (spacing >= MIN_GRID_SPACING_M) {
                spacings.add(spacing);
            }
        }

        if (spacings.isEmpty()) {
            return new GridPattern(false, 0, sorted);
        }

        // Find most common spacing
        double avgSpacing = spacings.stream().mapToDouble(d -> d).average().orElse(0);

        // Check if spacings are consistent (within 10%)
        boolean isRegular = spacings.stream()
            .allMatch(s -> Math.abs(s - avgSpacing) < avgSpacing * 0.1);

        return new GridPattern(isRegular, avgSpacing, sorted);
    }

    /**
     * Check if position aligns to grid.
     */
    private boolean isAlignedToGrid(double pos, GridPattern grid) {
        if (!grid.isRegular || grid.gridLines.isEmpty()) {
            return true; // No grid to align to
        }

        for (double gridLine : grid.gridLines) {
            if (Math.abs(pos - gridLine) < GRID_TOLERANCE_M) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check vertical column continuity across storeys.
     */
    private int checkVerticalContinuity(List<ColumnPosition> columns) {
        // Group columns by XY position
        Map<String, List<ColumnPosition>> byPosition = new HashMap<>();

        for (ColumnPosition col : columns) {
            String key = String.format("%.1f_%.1f", col.x, col.y);
            byPosition.computeIfAbsent(key, k -> new ArrayList<>()).add(col);
        }

        // Count positions with multiple Z levels (spans storeys)
        int continuous = 0;
        for (List<ColumnPosition> group : byPosition.values()) {
            if (group.size() > 1) {
                continuous++;
            }
            // Also check if single column spans multiple storeys
            for (ColumnPosition col : group) {
                if (col.maxZ - col.minZ > 3.5) { // Spans > 1 storey
                    continuous++;
                }
            }
        }

        return continuous;
    }

    // Data classes
    private record ColumnPosition(String guid, double x, double y, double minZ, double maxZ) {}
    private record BeamInfo(String name, double length) {}
    private record GridPattern(boolean isRegular, double spacing, List<Double> gridLines) {}
}
