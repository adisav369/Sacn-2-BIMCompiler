package com.bim.compiler.dsl;

import java.sql.*;
import java.util.*;

/**
 * Maps SCHEDULE door/window types to LOD400 library components.
 *
 * Phase 29: Reconnects DSL SCHEDULE types to component_library.db.
 *
 * Mapping based on component_library.db query:
 * - Doors: Match by width (Y-axis) and height (Z-axis)
 * - Windows: Fall back to parametric (TERMINAL has commercial windows)
 */
public class DoorWindowLibraryMapper {

    private static final String LIBRARY_PATH = "library/component_library.db";

    // Tolerance for dimension matching (mm)
    private static final double TOLERANCE_MM = 50.0;

    /**
     * Library component definition with geometry hash.
     */
    public record LibraryComponent(
        int id,
        String name,
        String geometryHash,
        double widthMm,
        double heightMm,
        double depthMm
    ) {}

    /**
     * Mapping result with status.
     */
    public record MappingResult(
        String scheduleType,
        LibraryComponent component,
        boolean usesLibrary,
        String fallbackReason
    ) {
        public static MappingResult library(String type, LibraryComponent comp) {
            return new MappingResult(type, comp, true, null);
        }

        public static MappingResult parametric(String type, String reason) {
            return new MappingResult(type, null, false, reason);
        }
    }

    private final Connection libraryConn;
    private final Map<String, LibraryComponent> doorCache = new HashMap<>();
    private final Map<String, LibraryComponent> windowCache = new HashMap<>();

    public DoorWindowLibraryMapper(String libraryPath) throws SQLException {
        this.libraryConn = DriverManager.getConnection("jdbc:sqlite:" + libraryPath);
        loadLibraryComponents();
    }

    public DoorWindowLibraryMapper() throws SQLException {
        this(LIBRARY_PATH);
    }

    /**
     * Load library components for quick lookup.
     */
    private void loadLibraryComponents() throws SQLException {
        // Load doors
        String doorQuery = """
            SELECT cd.id, cd.name, cd.geometry_hash,
                   ROUND((cd.local_max_y - cd.local_min_y) * 1000, 0) as width_mm,
                   ROUND((cd.local_max_z - cd.local_min_z) * 1000, 0) as height_mm,
                   ROUND((cd.local_max_x - cd.local_min_x) * 1000, 0) as depth_mm
            FROM component_definitions cd
            JOIN component_types ct ON cd.type_id = ct.id
            WHERE ct.ifc_class = 'IfcDoor'
            """;

        try (Statement stmt = libraryConn.createStatement();
             ResultSet rs = stmt.executeQuery(doorQuery)) {
            while (rs.next()) {
                String key = rs.getDouble("width_mm") + "x" + rs.getDouble("height_mm");
                // Store first match for each size
                if (!doorCache.containsKey(key)) {
                    doorCache.put(key, new LibraryComponent(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("geometry_hash"),
                        rs.getDouble("width_mm"),
                        rs.getDouble("height_mm"),
                        rs.getDouble("depth_mm")
                    ));
                }
            }
        }

        // Load windows
        String windowQuery = """
            SELECT cd.id, cd.name, cd.geometry_hash,
                   ROUND((cd.local_max_y - cd.local_min_y) * 1000, 0) as width_mm,
                   ROUND((cd.local_max_z - cd.local_min_z) * 1000, 0) as height_mm,
                   ROUND((cd.local_max_x - cd.local_min_x) * 1000, 0) as depth_mm
            FROM component_definitions cd
            JOIN component_types ct ON cd.type_id = ct.id
            WHERE ct.ifc_class = 'IfcWindow'
            """;

        try (Statement stmt = libraryConn.createStatement();
             ResultSet rs = stmt.executeQuery(windowQuery)) {
            while (rs.next()) {
                String key = rs.getDouble("width_mm") + "x" + rs.getDouble("height_mm");
                if (!windowCache.containsKey(key)) {
                    windowCache.put(key, new LibraryComponent(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("geometry_hash"),
                        rs.getDouble("width_mm"),
                        rs.getDouble("height_mm"),
                        rs.getDouble("depth_mm")
                    ));
                }
            }
        }

        System.out.printf("[DoorWindowLibraryMapper] Loaded %d door sizes, %d window sizes%n",
            doorCache.size(), windowCache.size());
    }

    /**
     * Map a door SCHEDULE type to library component.
     *
     * @param widthMm Door width in millimeters
     * @param heightMm Door height in millimeters
     * @param scheduleType Original schedule type (D1, D2, etc.)
     * @return Mapping result with library component or fallback reason
     */
    public MappingResult mapDoor(double widthMm, double heightMm, String scheduleType) {
        // Try exact match first
        String exactKey = widthMm + "x" + heightMm;
        if (doorCache.containsKey(exactKey)) {
            return MappingResult.library(scheduleType, doorCache.get(exactKey));
        }

        // Try closest match within tolerance
        LibraryComponent closest = null;
        double closestDist = Double.MAX_VALUE;

        for (var entry : doorCache.entrySet()) {
            LibraryComponent comp = entry.getValue();
            double widthDiff = Math.abs(comp.widthMm - widthMm);
            double heightDiff = Math.abs(comp.heightMm - heightMm);

            if (widthDiff <= TOLERANCE_MM && heightDiff <= TOLERANCE_MM) {
                double dist = widthDiff + heightDiff;
                if (dist < closestDist) {
                    closestDist = dist;
                    closest = comp;
                }
            }
        }

        if (closest != null) {
            System.out.printf("[DoorWindowLibraryMapper] %s (%.0fx%.0f) → library %s (%.0fx%.0f)%n",
                scheduleType, widthMm, heightMm, closest.name, closest.widthMm, closest.heightMm);
            return MappingResult.library(scheduleType, closest);
        }

        // No match - fall back to parametric
        System.out.printf("[DoorWindowLibraryMapper] %s (%.0fx%.0f) → PARAMETRIC (no library match)%n",
            scheduleType, widthMm, heightMm);
        return MappingResult.parametric(scheduleType,
            String.format("No library door within %.0fmm of %.0fx%.0f", TOLERANCE_MM, widthMm, heightMm));
    }

    /**
     * Map a window SCHEDULE type to library component.
     *
     * Note: TERMINAL library has commercial windows (tall/narrow).
     * Residential windows typically fall back to parametric.
     */
    public MappingResult mapWindow(double widthMm, double heightMm, String scheduleType) {
        // Try exact match
        String exactKey = widthMm + "x" + heightMm;
        if (windowCache.containsKey(exactKey)) {
            return MappingResult.library(scheduleType, windowCache.get(exactKey));
        }

        // Try closest match within tolerance
        LibraryComponent closest = null;
        double closestDist = Double.MAX_VALUE;

        for (var entry : windowCache.entrySet()) {
            LibraryComponent comp = entry.getValue();
            double widthDiff = Math.abs(comp.widthMm - widthMm);
            double heightDiff = Math.abs(comp.heightMm - heightMm);

            if (widthDiff <= TOLERANCE_MM && heightDiff <= TOLERANCE_MM) {
                double dist = widthDiff + heightDiff;
                if (dist < closestDist) {
                    closestDist = dist;
                    closest = comp;
                }
            }
        }

        if (closest != null) {
            return MappingResult.library(scheduleType, closest);
        }

        // Expected for residential - TERMINAL windows are commercial
        return MappingResult.parametric(scheduleType,
            "TERMINAL library has commercial windows; residential sizes not available");
    }

    /**
     * Get library geometry by hash for copying to output DB.
     */
    public byte[] getGeometryVertices(String geometryHash) throws SQLException {
        String query = "SELECT vertices FROM component_geometries WHERE geometry_hash = ?";
        try (PreparedStatement ps = libraryConn.prepareStatement(query)) {
            ps.setString(1, geometryHash);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBytes("vertices");
                }
            }
        }
        return null;
    }

    public byte[] getGeometryFaces(String geometryHash) throws SQLException {
        String query = "SELECT faces FROM component_geometries WHERE geometry_hash = ?";
        try (PreparedStatement ps = libraryConn.prepareStatement(query)) {
            ps.setString(1, geometryHash);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBytes("faces");
                }
            }
        }
        return null;
    }

    /**
     * Copy library geometry to output database.
     */
    public void copyGeometryToOutput(Connection outputConn, String geometryHash) throws SQLException {
        // Check if already exists in output
        String checkQuery = "SELECT 1 FROM base_geometries WHERE geometry_hash = ?";
        try (PreparedStatement ps = outputConn.prepareStatement(checkQuery)) {
            ps.setString(1, geometryHash);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return; // Already exists
                }
            }
        }

        // Get from library
        String srcQuery = """
            SELECT vertices, faces, vertex_count, face_count
            FROM component_geometries WHERE geometry_hash = ?
            """;
        try (PreparedStatement srcPs = libraryConn.prepareStatement(srcQuery)) {
            srcPs.setString(1, geometryHash);
            try (ResultSet rs = srcPs.executeQuery()) {
                if (rs.next()) {
                    String insertQuery = """
                        INSERT INTO base_geometries (geometry_hash, vertices, faces, vertex_count, face_count)
                        VALUES (?, ?, ?, ?, ?)
                        """;
                    try (PreparedStatement dstPs = outputConn.prepareStatement(insertQuery)) {
                        dstPs.setString(1, geometryHash);
                        dstPs.setBytes(2, rs.getBytes("vertices"));
                        dstPs.setBytes(3, rs.getBytes("faces"));
                        dstPs.setInt(4, rs.getInt("vertex_count"));
                        dstPs.setInt(5, rs.getInt("face_count"));
                        dstPs.executeUpdate();
                    }
                }
            }
        }
    }

    /**
     * Print mapping summary for TB-LKTN SCHEDULE types.
     */
    public void printScheduleMapping() {
        System.out.println("\n=== SCHEDULE → Library Mapping ===");

        // TB-LKTN door schedule
        System.out.println("\nDoors:");
        System.out.println("  D1 (900x2100): " + mapDoor(900, 2100, "D1"));
        System.out.println("  D2 (750x2100): " + mapDoor(750, 2100, "D2"));
        System.out.println("  D3 (900x2100): " + mapDoor(900, 2100, "D3"));

        // TB-LKTN window schedule
        System.out.println("\nWindows:");
        System.out.println("  W1 (1800x1000): " + mapWindow(1800, 1000, "W1"));
        System.out.println("  W2 (1200x1000): " + mapWindow(1200, 1000, "W2"));
        System.out.println("  W3 (600x500):   " + mapWindow(600, 500, "W3"));
    }

    public void close() throws SQLException {
        if (libraryConn != null) {
            libraryConn.close();
        }
    }

    /**
     * Test mapping standalone.
     */
    public static void main(String[] args) throws SQLException {
        DoorWindowLibraryMapper mapper = new DoorWindowLibraryMapper();
        mapper.printScheduleMapping();
        mapper.close();
    }
}
