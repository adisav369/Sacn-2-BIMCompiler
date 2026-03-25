package com.bim.compiler.validation;

import com.bim.compiler.BIMConstants;
import com.bim.compiler.util.BIMLogger;
import com.bim.eyes.EyesConstants;
import com.bim.eyes.ProductCategory;
import com.bim.eyes.proof.EyesProofRunner;
import com.bim.eyes.proof.RelationalData;
import com.bim.ormsandbox.po.M_AdBuildingGrid;

import java.sql.*;
import java.util.*;

/**
 * Thin facade — delegates proof logic to BIMEyes EyesProofRunner.
 * Keeps inner types (PlacementData, ProofResult, ProofReport) for backward compatibility.
 * Data loading (wall faces, rooms, element rules) stays here.
 *
 * <p>Phase 2 migration: all proof methods now live in com.bim.eyes.proof.tier1/2/3.
 * This class will be simplified further in Phase 3.
 *
 * @deprecated Use {@link EyesProofRunner} directly for new code.
 */
@Deprecated
public class PlacementProver {

    // =========================================================================
    // Data records (backward compat — same shape as Eyes types)
    // =========================================================================

    /** Minimal placement data for proving (subset of PlacementLoader.Placement). */
    public record PlacementData(
        String guid,
        String ifcClass,
        String productCategory,
        String elementRef,
        String storey,
        double minX, double maxX,
        double minY, double maxY,
        double minZ, double maxZ
    ) {
        public double cx() { return (minX + maxX) / 2; }
        public double cy() { return (minY + maxY) / 2; }
        public double cz() { return (minZ + maxZ) / 2; }
        public double dx() { return maxX - minX; }
        public double dy() { return maxY - minY; }
        public double dz() { return maxZ - minZ; }
    }

    /** Result of a single proof. */
    public record ProofResult(
        String proofId,
        Status status,
        String element,
        String evidence,
        double measuredValue
    ) {
        public enum Status { PROVEN, VIOLATED, SKIPPED }

        public boolean isCritical() {
            return proofId.startsWith("P01") || proofId.startsWith("P02")
                || proofId.startsWith("P03") || proofId.startsWith("P04")
                // R5: Promoted from advisory — fundamental spatial integrity (LAST_MILE_PROBLEM.md)
                || proofId.startsWith("P05") || proofId.startsWith("P06")
                || proofId.startsWith("P16") || proofId.startsWith("P17")
                || proofId.startsWith("P22");
        }
    }

    /** Aggregate report of all proofs. */
    public record ProofReport(
        List<ProofResult> results,
        int proven,
        int violated,
        int skipped
    ) {
        public boolean allCriticalProven() {
            return results.stream()
                .filter(r -> r.isCritical() && r.status() == ProofResult.Status.VIOLATED)
                .findAny().isEmpty();
        }

        public int criticalViolations() {
            return (int) results.stream()
                .filter(r -> r.isCritical() && r.status() == ProofResult.Status.VIOLATED)
                .count();
        }
    }

    // =========================================================================
    // Main entry points (delegate to EyesProofRunner)
    // =========================================================================

    /**
     * Prove placement correctness from a list of placement data.
     * Used pre-write in BuildingWriter.
     */
    public static ProofReport prove(List<PlacementData> placements, String buildingName) {
        List<com.bim.eyes.proof.PlacementData> eyesPlacements = toEyes(placements);
        EyesProofRunner.RelationalContext ctx = loadRelationalContext(buildingName);
        com.bim.eyes.proof.ProofReport eyesReport =
            EyesProofRunner.prove(eyesPlacements, buildingName, ctx);
        return fromEyesReport(eyesReport);
    }

    /**
     * Prove placement correctness by reading an output DB.
     * Used post-write in E2E tests.
     */
    /** @deprecated Use {@link #proveFromDB(String, String)} with explicit building name. */
    @Deprecated
    public static ProofReport proveFromDB(String dbPath) {
        return proveFromDB(dbPath, detectBuildingName(dbPath));
    }

    public static ProofReport proveFromDB(String dbPath, String buildingName) {
        EyesProofRunner.RelationalContext ctx = loadRelationalContext(buildingName);
        com.bim.eyes.proof.ProofReport eyesReport =
            EyesProofRunner.proveFromDB(dbPath, buildingName, ctx);
        return fromEyesReport(eyesReport);
    }

    /**
     * Print a human-readable proof report.
     */
    public static void printReport(ProofReport report) {
        // Log every proof result to BIMLogger at FINE level
        // Implementing BIMLogger.md §Wiring Status — PlacementProver proof() calls
        for (ProofResult r : report.results()) {
            BIMLogger.proof(r.proofId(), r.status().name(),
                r.element() != null ? r.element() : "GLOBAL", r.evidence());
        }

        // Group violations by proof ID for concise output
        Map<String, List<ProofResult>> violationsByProof = new LinkedHashMap<>();
        for (ProofResult r : report.results()) {
            if (r.status() == ProofResult.Status.VIOLATED) {
                violationsByProof.computeIfAbsent(r.proofId(), k -> new ArrayList<>()).add(r);
            }
        }

        System.out.printf("[PROOF] %d proven, %d violated, %d skipped%n",
            report.proven(), report.violated(), report.skipped());

        if (!violationsByProof.isEmpty()) {
            for (var entry : violationsByProof.entrySet()) {
                List<ProofResult> violations = entry.getValue();
                ProofResult first = violations.get(0);
                if (violations.size() <= 3) {
                    for (ProofResult v : violations) {
                        System.out.printf("  [VIOLATED] %s %s: %s (%.4f)%n",
                            v.proofId(), v.element() != null ? v.element() : "GLOBAL",
                            v.evidence(), v.measuredValue());
                    }
                } else {
                    // Summarize: show first 2 + count
                    for (int i = 0; i < 2; i++) {
                        ProofResult v = violations.get(i);
                        System.out.printf("  [VIOLATED] %s %s: %s (%.4f)%n",
                            v.proofId(), v.element() != null ? v.element() : "GLOBAL",
                            v.evidence(), v.measuredValue());
                    }
                    System.out.printf("  [VIOLATED] %s ... and %d more%n",
                        first.proofId(), violations.size() - 2);
                }
            }
        }
    }

    // =========================================================================
    // Type conversion (PlacementProver ↔ BIMEyes)
    // =========================================================================

    private static List<com.bim.eyes.proof.PlacementData> toEyes(List<PlacementData> placements) {
        List<com.bim.eyes.proof.PlacementData> result = new ArrayList<>(placements.size());
        for (PlacementData p : placements) {
            result.add(new com.bim.eyes.proof.PlacementData(
                p.guid(), p.ifcClass(), p.productCategory(), p.elementRef(), p.storey(),
                p.minX(), p.maxX(), p.minY(), p.maxY(), p.minZ(), p.maxZ()));
        }
        return result;
    }

    private static ProofReport fromEyesReport(com.bim.eyes.proof.ProofReport eyesReport) {
        List<ProofResult> results = new ArrayList<>(eyesReport.results().size());
        for (com.bim.eyes.proof.ProofResult r : eyesReport.results()) {
            results.add(new ProofResult(
                r.proofId(),
                ProofResult.Status.valueOf(r.status().name()),
                r.element(), r.evidence(), r.measuredValue()));
        }
        return new ProofReport(results, eyesReport.proven(), eyesReport.violated(), eyesReport.skipped());
    }

    // =========================================================================
    // Relational context loading (stays in facade — domain-specific DB access)
    // =========================================================================

    private static EyesProofRunner.RelationalContext loadRelationalContext(String buildingName) {
        if (buildingName == null || buildingName.isEmpty()) {
            return EyesProofRunner.RelationalContext.EMPTY;
        }

        String bomDbPath = System.getProperty("bom.db");
        if (bomDbPath == null) {
            return EyesProofRunner.RelationalContext.EMPTY;
        }

        try (Connection lib = DriverManager.getConnection("jdbc:sqlite:" + bomDbPath)) {
            Map<String, RelationalData.WallFaceData> wallFaces = loadWallFaces(lib, buildingName);
            Map<String, RelationalData.RoomData> rooms = loadRooms(lib, buildingName);
            List<RelationalData.ElementRule> elementRules = loadElementRules(lib, buildingName);
            List<String[]> connectEdges = loadConnectsToEdges(lib, buildingName);
            Map<String, String> orientationByRef = new HashMap<>(); // §11.9: orientation dropped
            double expectedPerimeter = computeExpectedPerimeter(lib, buildingName);

            return new EyesProofRunner.RelationalContext(
                wallFaces, rooms, elementRules, connectEdges,
                orientationByRef, expectedPerimeter);
        } catch (SQLException e) {
            return EyesProofRunner.RelationalContext.EMPTY;
        }
    }

    // =========================================================================
    // Data loading helpers (domain-specific — stays in facade)
    // =========================================================================

    /**
     * Load wall face data from ad_wall_face + ad_room_boundary.
     * Derives wall geometry from room boundaries since ad_wall_face has no coords.
     * Key = "WALL_{room_name}_{FACE}" to match c_orderline.host_ref format.
     */
    private static Map<String, RelationalData.WallFaceData> loadWallFaces(
            Connection lib, String buildingName) throws SQLException {
        Map<String, RelationalData.RoomData> rooms = loadRooms(lib, buildingName);
        Map<String, RelationalData.WallFaceData> faces = new LinkedHashMap<>();

        String sql = """
            SELECT room_name, face, is_exterior
            FROM ad_wall_face
            WHERE building_type = ? AND is_active = 1
            """;

        try (PreparedStatement ps = lib.prepareStatement(sql)) {
            ps.setString(1, buildingName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String roomName = rs.getString("room_name");
                    String face = rs.getString("face");
                    boolean isExterior = rs.getInt("is_exterior") == 1;

                    RelationalData.RoomData room = rooms.get(roomName);
                    if (room == null) continue;

                    double wMinX, wMaxX, wMinY, wMaxY;
                    double wallX, wallY;
                    double wallThickness = EyesConstants.STANDARD_WALL_THICK_M;

                    switch (face) {
                        case "NORTH" -> {
                            wMinX = room.minX(); wMaxX = room.maxX();
                            wMinY = room.maxY() - wallThickness / 2;
                            wMaxY = room.maxY() + wallThickness / 2;
                            wallX = (room.minX() + room.maxX()) / 2;
                            wallY = room.maxY();
                        }
                        case "SOUTH" -> {
                            wMinX = room.minX(); wMaxX = room.maxX();
                            wMinY = room.minY() - wallThickness / 2;
                            wMaxY = room.minY() + wallThickness / 2;
                            wallX = (room.minX() + room.maxX()) / 2;
                            wallY = room.minY();
                        }
                        case "EAST" -> {
                            wMinX = room.maxX() - wallThickness / 2;
                            wMaxX = room.maxX() + wallThickness / 2;
                            wMinY = room.minY(); wMaxY = room.maxY();
                            wallX = room.maxX();
                            wallY = (room.minY() + room.maxY()) / 2;
                        }
                        case "WEST" -> {
                            wMinX = room.minX() - wallThickness / 2;
                            wMaxX = room.minX() + wallThickness / 2;
                            wMinY = room.minY(); wMaxY = room.maxY();
                            wallX = room.minX();
                            wallY = (room.minY() + room.maxY()) / 2;
                        }
                        default -> { continue; }
                    }

                    String key = "WALL_" + roomName + "_" + face;
                    faces.put(key, new RelationalData.WallFaceData(
                        roomName, face, isExterior,
                        wMinX, wMaxX, wMinY, wMaxY,
                        0.0, 3.5,  // ground floor Z
                        wallX, wallY
                    ));
                }
            }
        } catch (SQLException e) {
            // Table doesn't exist — return empty
        }

        return faces;
    }

    /**
     * Load room boundary data from ad_room_boundary.
     * Uses min_x_mm/max_x_mm columns (values in mm, converted to meters).
     */
    private static Map<String, RelationalData.RoomData> loadRooms(
            Connection lib, String buildingName) throws SQLException {
        Map<String, RelationalData.RoomData> rooms = new LinkedHashMap<>();
        try {
            String sql = """
                SELECT room_name, min_x_mm, max_x_mm, min_y_mm, max_y_mm
                FROM ad_room_boundary
                WHERE building_type = ? AND is_active = 1
                """;
            try (PreparedStatement ps = lib.prepareStatement(sql)) {
                ps.setString(1, buildingName);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String name = rs.getString("room_name");
                        rooms.put(name, new RelationalData.RoomData(name,
                            rs.getDouble("min_x_mm") / 1000.0,
                            rs.getDouble("max_x_mm") / 1000.0,
                            rs.getDouble("min_y_mm") / 1000.0,
                            rs.getDouble("max_y_mm") / 1000.0));
                    }
                }
            }
        } catch (SQLException e) {
            // Table may not exist — return empty
        }
        return rooms;
    }

    /**
     * Load element rules from c_orderline.
     *
     * <p>§11.9: host_type, host_ref, position_rule DROPPED from c_orderline.
     * ElementRule fields for placement data are null until W_Verb_Node migration.
     */
    private static List<RelationalData.ElementRule> loadElementRules(
            Connection lib, String buildingName) throws SQLException {
        List<RelationalData.ElementRule> rules = new ArrayList<>();
        try {
            String sql = """
                SELECT IfcClass, Name
                FROM c_orderline
                WHERE C_Order_ID = ? AND IsActive = 1
                """;
            try (PreparedStatement ps = lib.prepareStatement(sql)) {
                ps.setString(1, buildingName);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String ifc = rs.getString("IfcClass");
                        rules.add(new RelationalData.ElementRule(
                            ifc,
                            ProductCategory.resolve(ifc),
                            rs.getString("Name"),
                            null, null, null
                        ));
                    }
                }
            }
        } catch (SQLException e) {
            // Table may not exist
        }
        return rules;
    }

    /** Load CONNECTS_TO edges from ad_element_dependency. */
    private static List<String[]> loadConnectsToEdges(
            Connection lib, String buildingName) throws SQLException {
        List<String[]> edges = new ArrayList<>();
        try {
            String sql = """
                SELECT element_ref, parent_ref
                FROM ad_element_dependency
                WHERE building_type = ? AND relation = 'CONNECTS_TO' AND is_active = 1
                """;
            try (PreparedStatement ps = lib.prepareStatement(sql)) {
                ps.setString(1, buildingName);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        edges.add(new String[]{rs.getString(1), rs.getString(2)});
                    }
                }
            }
        } catch (SQLException e) {
            // Table may not exist
        }
        return edges;
    }

    /**
     * Compute expected building perimeter from grid extents.
     * ad_building_grid.position_mm is in millimeters.
     */
    private static double computeExpectedPerimeter(
            Connection lib, String buildingName) throws SQLException {
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;

        for (M_AdBuildingGrid row : M_AdBuildingGrid.getByBuilding(lib, buildingName)) {
            double pos = row.getPositionMm() / 1000.0;
            if ("X".equals(row.getAxis())) {
                minX = Math.min(minX, pos);
                maxX = Math.max(maxX, pos);
            } else {
                minY = Math.min(minY, pos);
                maxY = Math.max(maxY, pos);
            }
        }

        if (minX == Double.MAX_VALUE) return 0;
        return 2 * ((maxX - minX) + (maxY - minY));
    }

    // =========================================================================
    // Utility
    // =========================================================================

    /** @deprecated Heuristic fallback — pass building name explicitly. */
    @Deprecated
    private static String detectBuildingName(String dbPath) {
        return "";
    }
}
