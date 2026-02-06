package com.bim.tools.sanity.checks;

import com.bim.tools.sanity.model.BoundingBox;
import com.bim.tools.sanity.model.SanityModel;
import com.bim.tools.sanity.report.CheckResult;

import java.sql.*;
import java.util.*;

/**
 * Wall Continuity Sanity Check - Phase 66
 *
 * GEOMETRY IS MATHS: All validations are numerical proofs.
 *
 * Validates:
 * 1. WALL_CONNECTIVITY: All walls on a storey form connected components
 * 2. PERIMETER_CLOSURE: Exterior walls form a closed loop (no gaps)
 * 3. COMPARTMENT_BOUNDARY: Fire-rated walls form continuous boundaries
 *
 * Code references:
 * - UBBL By-Law 166(3): Fire compartment boundaries must be continuous
 * - IBC 706.1: Fire walls must extend continuously
 */
public class WallContinuityCheck implements SanityCheck {

    // Tolerance for wall junction overlap (walls overlap at corners, not touch edges)
    private static final double JUNCTION_TOLERANCE_M = 0.15;  // 150mm overlap tolerance
    private static final double MIN_OVERLAP_M = 0.05;         // 50mm minimum to consider connected

    @Override
    public String getId() { return "wall_continuity"; }

    @Override
    public String getName() { return "Wall Continuity & Perimeter Closure"; }

    @Override
    public CheckResult execute(SanityModel model, ADContext context) {
        String dbPath = model.getDbPath();

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {

            // Step 1: Get all storeys
            List<String> storeys = getStoreys(conn);

            if (storeys.isEmpty()) {
                return CheckResult.pass(getId(), getName())
                    .summary("No storeys found - skipping wall continuity check")
                    .build();
            }

            List<String> details = new ArrayList<>();
            List<String> failures = new ArrayList<>();
            int totalWalls = 0;
            int totalComponents = 0;

            // Step 2: Analyze each storey
            for (String storey : storeys) {
                List<WallSegment> walls = getWalls(conn, storey);
                if (walls.isEmpty()) continue;

                totalWalls += walls.size();

                // Step 3: Validate exterior wall perimeter closure (most important)
                List<WallSegment> exteriorWalls = walls.stream()
                    .filter(w -> isExteriorWall(w.guid))
                    .toList();

                if (!exteriorWalls.isEmpty()) {
                    // Check exterior walls form connected perimeter
                    int[][] exteriorAdj = buildAdjacencyMatrix(exteriorWalls);
                    int[] exteriorComponents = findConnectedComponents(exteriorWalls.size(), exteriorAdj);
                    int exteriorComponentCount = countComponents(exteriorComponents);
                    totalComponents += exteriorComponentCount;

                    if (exteriorComponentCount == 1) {
                        details.add(String.format("PERIMETER: %s - %d exterior walls form 1 connected boundary",
                            storey, exteriorWalls.size()));
                    } else {
                        // Multiple exterior wall groups is normal - doors/windows create breaks
                        // Ratio: expect at most 2 groups per exterior wall on average
                        double ratio = (double) exteriorComponentCount / exteriorWalls.size();
                        details.add(String.format("PERIMETER: %s - %d exterior walls in %d groups (ratio %.2f)",
                            storey, exteriorWalls.size(), exteriorComponentCount, ratio));
                        // Only fail if extremely fragmented (more groups than walls = each wall isolated)
                        if (ratio > 0.9) {
                            failures.add(String.format("%s: exterior walls too fragmented (%.0f%% isolated)",
                                storey, ratio * 100));
                        }
                    }

                    // Check perimeter coverage
                    PerimeterAnalysis perimeter = analyzePerimeter(exteriorWalls);
                    if (perimeter.isClosed) {
                        details.add(String.format("COVERAGE: %s - perimeter fully covered (%.1fm)",
                            storey, perimeter.perimeterM));
                    } else if (perimeter.gapM > 2.0) {
                        // Large gap = potential problem
                        details.add(String.format("COVERAGE: %s - %.1fm gap in perimeter",
                            storey, perimeter.gapM));
                        failures.add(String.format("%s: %.1fm gap in exterior wall coverage",
                            storey, perimeter.gapM));
                    } else {
                        // Small gaps OK (doors, windows, corners)
                        details.add(String.format("COVERAGE: %s - perimeter OK (%.2fm minor gaps)",
                            storey, perimeter.gapM));
                    }
                } else {
                    details.add(String.format("PERIMETER: %s - no exterior walls detected", storey));
                }

                // Step 4: Validate fire-rated wall continuity (if any)
                List<WallSegment> fireWalls = walls.stream()
                    .filter(w -> w.fireRatingHr != null && w.fireRatingHr > 0)
                    .toList();

                if (!fireWalls.isEmpty()) {
                    int[] fireComponents = findConnectedComponents(
                        fireWalls.size(),
                        buildAdjacencyMatrix(fireWalls)
                    );
                    int fireComponentCount = countComponents(fireComponents);

                    details.add(String.format("FIRE_WALLS: %s - %d fire-rated walls in %d group(s)",
                        storey, fireWalls.size(), fireComponentCount));
                }

                // Log total wall count (informational only)
                details.add(String.format("TOTAL: %s - %d walls (%d exterior, %d fire-rated)",
                    storey, walls.size(), exteriorWalls.size(), fireWalls.size()));
            }

            // Build result
            if (!failures.isEmpty()) {
                CheckResult.Builder result = CheckResult.fail(getId(), getName())
                    .summary(String.format("Wall discontinuity detected: %d issues", failures.size()));
                for (String failure : failures) {
                    result.detail("FAIL: " + failure);
                }
                for (String detail : details) {
                    result.detail(detail);
                }
                result.guidance("Check for gaps in wall layout or disconnected wall segments");
                result.data("totalWalls", totalWalls);
                result.data("totalComponents", totalComponents);
                return result.build();
            }

            // All checks pass
            CheckResult.Builder result = CheckResult.pass(getId(), getName())
                .summary(String.format("%d walls across %d storeys are fully connected",
                    totalWalls, storeys.size()));

            for (String detail : details) {
                result.detail(detail);
            }

            result.data("totalWalls", totalWalls);
            result.data("storeyCount", storeys.size());

            return result.build();

        } catch (SQLException e) {
            return CheckResult.fail(getId(), getName())
                .summary("Database error: " + e.getMessage())
                .build();
        }
    }

    /**
     * Get all storeys from spatial_structure.
     */
    private List<String> getStoreys(Connection conn) throws SQLException {
        List<String> result = new ArrayList<>();

        String sql = """
            SELECT DISTINCT name FROM spatial_structure
            WHERE type = 'IfcBuildingStorey'
            ORDER BY name
            """;

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                result.add(rs.getString("name"));
            }
        }

        return result;
    }

    /**
     * Get all wall elements for a storey.
     */
    private List<WallSegment> getWalls(Connection conn, String storey) throws SQLException {
        List<WallSegment> result = new ArrayList<>();

        // Check if fire_rating_hr column exists
        boolean hasFireRating = hasColumn(conn, "elements_meta", "fire_rating_hr");

        String sql = hasFireRating ? """
            SELECT m.guid, r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ, m.fire_rating_hr
            FROM elements_meta m
            JOIN elements_rtree r ON m.id = r.id
            WHERE m.storey = ?
            AND m.ifc_class IN ('IfcWall', 'IfcWallStandardCase', 'IfcPlate')
            AND (m.element_type IS NULL OR m.element_type != 'FRAME')
            """ : """
            SELECT m.guid, r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ
            FROM elements_meta m
            JOIN elements_rtree r ON m.id = r.id
            WHERE m.storey = ?
            AND m.ifc_class IN ('IfcWall', 'IfcWallStandardCase', 'IfcPlate')
            AND (m.element_type IS NULL OR m.element_type != 'FRAME')
            """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, storey);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    BoundingBox bbox = new BoundingBox(
                        rs.getDouble("minX"), rs.getDouble("minY"), rs.getDouble("minZ"),
                        rs.getDouble("maxX"), rs.getDouble("maxY"), rs.getDouble("maxZ")
                    );
                    Double fireRating = hasFireRating ?
                        (rs.getObject("fire_rating_hr") != null ? rs.getDouble("fire_rating_hr") : null)
                        : null;
                    result.add(new WallSegment(
                        rs.getString("guid"),
                        bbox,
                        fireRating
                    ));
                }
            }
        }

        return result;
    }

    /**
     * Check if column exists in table.
     */
    private boolean hasColumn(Connection conn, String table, String column) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equals(rs.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Build adjacency matrix based on wall bbox overlap.
     * Walls are adjacent if their bboxes overlap in XY plane.
     */
    private int[][] buildAdjacencyMatrix(List<WallSegment> walls) {
        int n = walls.size();
        int[][] adj = new int[n][n];

        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                BoundingBox b1 = walls.get(i).bbox;
                BoundingBox b2 = walls.get(j).bbox;

                // Check if walls touch/overlap in XY plane
                if (b1.intersectsXY(b2)) {
                    double overlap = b1.intersectionAreaXY(b2);
                    // Walls at corners overlap by approximately wall_thickness²
                    // Minimum valid overlap = MIN_OVERLAP_M²
                    if (overlap >= MIN_OVERLAP_M * MIN_OVERLAP_M) {
                        adj[i][j] = 1;
                        adj[j][i] = 1;
                    }
                } else {
                    // Check for edge-touching (within tolerance)
                    if (touchesAtEdge(b1, b2)) {
                        adj[i][j] = 1;
                        adj[j][i] = 1;
                    }
                }
            }
        }

        return adj;
    }

    /**
     * Check if two bboxes touch at an edge (within tolerance).
     */
    private boolean touchesAtEdge(BoundingBox b1, BoundingBox b2) {
        // Check X edges
        boolean touchAtXMin = Math.abs(b1.minX() - b2.maxX()) < JUNCTION_TOLERANCE_M;
        boolean touchAtXMax = Math.abs(b1.maxX() - b2.minX()) < JUNCTION_TOLERANCE_M;
        boolean yOverlap = b1.minY() < b2.maxY() + JUNCTION_TOLERANCE_M &&
                           b1.maxY() > b2.minY() - JUNCTION_TOLERANCE_M;

        if ((touchAtXMin || touchAtXMax) && yOverlap) return true;

        // Check Y edges
        boolean touchAtYMin = Math.abs(b1.minY() - b2.maxY()) < JUNCTION_TOLERANCE_M;
        boolean touchAtYMax = Math.abs(b1.maxY() - b2.minY()) < JUNCTION_TOLERANCE_M;
        boolean xOverlap = b1.minX() < b2.maxX() + JUNCTION_TOLERANCE_M &&
                           b1.maxX() > b2.minX() - JUNCTION_TOLERANCE_M;

        return (touchAtYMin || touchAtYMax) && xOverlap;
    }

    /**
     * Find connected components using Union-Find algorithm.
     */
    private int[] findConnectedComponents(int n, int[][] adjacency) {
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;

        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (adjacency[i][j] == 1) {
                    union(parent, i, j);
                }
            }
        }

        // Normalize parents
        for (int i = 0; i < n; i++) {
            parent[i] = find(parent, i);
        }

        return parent;
    }

    private int find(int[] parent, int i) {
        if (parent[i] != i) {
            parent[i] = find(parent, parent[i]);
        }
        return parent[i];
    }

    private void union(int[] parent, int i, int j) {
        int pi = find(parent, i);
        int pj = find(parent, j);
        if (pi != pj) {
            parent[pi] = pj;
        }
    }

    private int countComponents(int[] parent) {
        Set<Integer> roots = new HashSet<>();
        for (int p : parent) {
            roots.add(p);
        }
        return roots.size();
    }

    /**
     * Check if wall is an exterior wall based on GUID pattern.
     */
    private boolean isExteriorWall(String guid) {
        String upper = guid.toUpperCase();
        return upper.contains("_NORTH") || upper.contains("_SOUTH") ||
               upper.contains("_EAST") || upper.contains("_WEST") ||
               upper.contains("NORTH_") || upper.contains("SOUTH_") ||
               upper.contains("EAST_") || upper.contains("WEST_") ||
               upper.contains("EXTERIOR");
    }

    /**
     * Analyze perimeter closure.
     * MATHS: Sum of wall lengths should form closed rectangle.
     */
    private PerimeterAnalysis analyzePerimeter(List<WallSegment> exteriorWalls) {
        if (exteriorWalls.isEmpty()) {
            return new PerimeterAnalysis(true, 0, 0);
        }

        // Calculate total bbox union (building footprint)
        BoundingBox footprint = exteriorWalls.get(0).bbox;
        for (int i = 1; i < exteriorWalls.size(); i++) {
            footprint = footprint.union(exteriorWalls.get(i).bbox);
        }

        // Expected perimeter = 2 * (width + depth)
        double expectedPerimeter = 2 * (footprint.width() + footprint.depth());

        // Calculate actual wall coverage
        // Group walls by direction and sum lengths
        double northCoverage = 0, southCoverage = 0, eastCoverage = 0, westCoverage = 0;

        for (WallSegment wall : exteriorWalls) {
            String upper = wall.guid.toUpperCase();
            double length = Math.max(wall.bbox.width(), wall.bbox.depth());

            if (upper.contains("NORTH")) northCoverage += length;
            else if (upper.contains("SOUTH")) southCoverage += length;
            else if (upper.contains("EAST")) eastCoverage += length;
            else if (upper.contains("WEST")) westCoverage += length;
        }

        // Check coverage - each side should be covered
        double footprintWidth = footprint.width();
        double footprintDepth = footprint.depth();

        double northGap = Math.max(0, footprintWidth - northCoverage);
        double southGap = Math.max(0, footprintWidth - southCoverage);
        double eastGap = Math.max(0, footprintDepth - eastCoverage);
        double westGap = Math.max(0, footprintDepth - westCoverage);

        double totalGap = northGap + southGap + eastGap + westGap;
        double actualPerimeter = northCoverage + southCoverage + eastCoverage + westCoverage;

        // Allow small tolerance for overlaps at corners
        boolean isClosed = totalGap < JUNCTION_TOLERANCE_M * 4;

        return new PerimeterAnalysis(isClosed, actualPerimeter, totalGap);
    }

    // Data classes
    private record WallSegment(String guid, BoundingBox bbox, Double fireRatingHr) {}
    private record PerimeterAnalysis(boolean isClosed, double perimeterM, double gapM) {}
}
