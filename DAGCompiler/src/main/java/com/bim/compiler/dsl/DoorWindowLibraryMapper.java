/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.dsl;

import com.bim.compiler.geometry.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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
    // Phase 57B: Increased from 50mm to improve library hit rate
    private static final double TOLERANCE_MM = 150.0;

    // Separate tolerance for width (can be more flexible than height)
    private static final double WIDTH_TOLERANCE_MM = 300.0;

    /**
     * Library component definition with geometry hash.
     * Phase 79: Added local bounds for proper attachment offset calculation.
     * Phase 81: Added forwardAxis for deterministic rotation calculation.
     */
    public record LibraryComponent(
        int id,
        String name,
        String geometryHash,
        double widthMm,
        double heightMm,
        double depthMm,
        double localMinZ,  // Phase 79: For BOTTOM attachment offset
        double localMinX,  // Phase 79: For SIDE attachment offset
        double localMinY,  // Phase 79: For CENTER calculations
        String forwardAxis,    // Phase 81: Prefab forward direction (Y, X, -Y, -X)
        double defaultRotation // Phase 81: Default rotation in radians
    ) {
        // Convenience constructor for backward compatibility
        public LibraryComponent(int id, String name, String geometryHash,
                                double widthMm, double heightMm, double depthMm) {
            this(id, name, geometryHash, widthMm, heightMm, depthMm, 0, 0, 0, "Y", 0);
        }

        // Constructor without rotation (backward compatibility)
        public LibraryComponent(int id, String name, String geometryHash,
                                double widthMm, double heightMm, double depthMm,
                                double localMinZ, double localMinX, double localMinY) {
            this(id, name, geometryHash, widthMm, heightMm, depthMm,
                 localMinZ, localMinX, localMinY, "Y", 0);
        }

        /**
         * Calculate rotation angle to align prefab with wall direction.
         * Deterministic formula: rotation = wall_angle - prefab_forward_angle
         *
         * @param wallDirection "north", "south", "east", "west"
         * @return rotation in radians
         */
        public double calculateRotation(String wallDirection) {
            // Wall direction angles (door should face INTO room, opposite of wall normal)
            double wallAngle = switch (wallDirection.toLowerCase()) {
                case "north" -> 0;           // Wall on north, door faces south
                case "south" -> Math.PI;     // Wall on south, door faces north
                case "east" -> -Math.PI / 2; // Wall on east, door faces west
                case "west" -> Math.PI / 2;  // Wall on west, door faces east
                default -> 0;
            };

            // Prefab forward axis angle (library convention: Y = 0)
            double prefabAngle = switch (forwardAxis.toUpperCase()) {
                case "Y", "+Y" -> 0;
                case "-Y" -> Math.PI;
                case "X", "+X" -> Math.PI / 2;
                case "-X" -> -Math.PI / 2;
                default -> 0;
            };

            return wallAngle - prefabAngle + defaultRotation;
        }

        /**
         * Phase 88: Is this mesh natively oriented for the given wall direction?
         * widthMm = Y-extent, depthMm = X-extent in library convention.
         * NS walls need wide-in-X (depthMm > widthMm), EW walls need wide-in-Y (widthMm > depthMm).
         */
        public boolean isNativelyOrientedFor(String wall) {
            boolean isNS = wall.equalsIgnoreCase("north") || wall.equalsIgnoreCase("south");
            if (isNS) return depthMm > widthMm;   // wide in X → NS
            else      return widthMm > depthMm;   // wide in Y → EW
        }

        /**
         * Phase 88: Opening width when placed on given wall.
         * NS walls: X-extent (depthMm) is visible width; EW walls: Y-extent (widthMm) is visible width.
         */
        public double openingWidthMmForWall(String wall) {
            boolean isNS = wall.equalsIgnoreCase("north") || wall.equalsIgnoreCase("south");
            return isNS ? depthMm : widthMm;
        }
    }

    /**
     * Mapping result with status and optional scale factors.
     * Phase 57B: Added scale factors for LOD400 geometry scaling.
     */
    public record MappingResult(
        String scheduleType,
        LibraryComponent component,
        boolean usesLibrary,
        String fallbackReason,
        double scaleX,  // Width scale factor
        double scaleY,  // Depth scale factor
        double scaleZ,  // Height scale factor
        boolean orientationMatched  // Phase 88: true = library mesh already oriented for target wall, skip rotation
    ) {
        public static MappingResult library(String type, LibraryComponent comp) {
            return new MappingResult(type, comp, true, null, 1.0, 1.0, 1.0, false);
        }

        /** Phase 88: Library mesh natively oriented for target wall — no rotation needed. */
        public static MappingResult libraryOriented(String type, LibraryComponent comp) {
            return new MappingResult(type, comp, true, null, 1.0, 1.0, 1.0, true);
        }

        public static MappingResult libraryScaled(String type, LibraryComponent comp,
                                                   double scaleX, double scaleY, double scaleZ) {
            return new MappingResult(type, comp, true, null, scaleX, scaleY, scaleZ, false);
        }

        /** Phase 88: Scaled + orientation-matched variant. */
        public static MappingResult libraryScaledOriented(String type, LibraryComponent comp,
                                                          double scaleX, double scaleY, double scaleZ) {
            return new MappingResult(type, comp, true, null, scaleX, scaleY, scaleZ, true);
        }

        public static MappingResult parametric(String type, String reason) {
            return new MappingResult(type, null, false, reason, 1.0, 1.0, 1.0, false);
        }

        public boolean isScaled() {
            return scaleX != 1.0 || scaleY != 1.0 || scaleZ != 1.0;
        }
    }

    private final Connection libraryConn;
    private final Map<String, LibraryComponent> doorCache = new HashMap<>();
    private final Map<String, LibraryComponent> windowCache = new HashMap<>();
    private final List<LibraryComponent> allDoors = new ArrayList<>();      // Phase 88: ALL variants for orientation matching
    private final List<LibraryComponent> allWindows = new ArrayList<>();    // Phase 88: ALL variants for orientation matching
    private final Map<String, LibraryComponent> sprinklerCache = new HashMap<>();  // Phase 79: LOD400 sprinklers
    /** Geometry mesh cache — avoids re-reading identical blobs from library DB. */
    private final Map<String, Mesh> meshCache = new ConcurrentHashMap<>();

    public DoorWindowLibraryMapper(String libraryPath) throws SQLException {
        this.libraryConn = DriverManager.getConnection("jdbc:sqlite:" + libraryPath);
        this.libraryConn.createStatement().execute("PRAGMA foreign_keys = ON");
        loadLibraryComponents();
    }

    public DoorWindowLibraryMapper() throws SQLException {
        this(LIBRARY_PATH);
    }

    /**
     * Load library components for quick lookup.
     */
    private void loadLibraryComponents() throws SQLException {
        // Load doors (Phase 79: include local bounds for attachment offset)
        // Phase 81: include forward_axis and default_rotation for deterministic rotation
        String doorQuery = """
            SELECT cd.id, cd.name, cd.geometry_hash,
                   ROUND((cd.local_max_y - cd.local_min_y) * 1000, 0) as width_mm,
                   ROUND((cd.local_max_z - cd.local_min_z) * 1000, 0) as height_mm,
                   ROUND((cd.local_max_x - cd.local_min_x) * 1000, 0) as depth_mm,
                   cd.local_min_z, cd.local_min_x, cd.local_min_y,
                   COALESCE(cd.forward_axis, 'Y') as forward_axis,
                   COALESCE(cd.default_rotation, 0) as default_rotation
            FROM component_definitions cd
            JOIN component_types ct ON cd.type_id = ct.id
            WHERE ct.ifc_class = 'IfcDoor'
            """;

        try (Statement stmt = libraryConn.createStatement();
             ResultSet rs = stmt.executeQuery(doorQuery)) {
            while (rs.next()) {
                var comp = new LibraryComponent(
                    rs.getInt("id"),
                    rs.getString("name"),
                    rs.getString("geometry_hash"),
                    rs.getDouble("width_mm"),
                    rs.getDouble("height_mm"),
                    rs.getDouble("depth_mm"),
                    rs.getDouble("local_min_z"),
                    rs.getDouble("local_min_x"),
                    rs.getDouble("local_min_y"),
                    rs.getString("forward_axis"),
                    rs.getDouble("default_rotation")
                );
                // Phase 88: Store ALL variants for orientation matching
                allDoors.add(comp);
                // Keep first-match cache for backward compat
                String key = comp.widthMm() + "x" + comp.heightMm();
                if (!doorCache.containsKey(key)) {
                    doorCache.put(key, comp);
                }
            }
        }

        // Load windows (Phase 79: include local bounds for attachment offset)
        // Phase 81: include forward_axis and default_rotation for deterministic rotation
        String windowQuery = """
            SELECT cd.id, cd.name, cd.geometry_hash,
                   ROUND((cd.local_max_y - cd.local_min_y) * 1000, 0) as width_mm,
                   ROUND((cd.local_max_z - cd.local_min_z) * 1000, 0) as height_mm,
                   ROUND((cd.local_max_x - cd.local_min_x) * 1000, 0) as depth_mm,
                   cd.local_min_z, cd.local_min_x, cd.local_min_y,
                   COALESCE(cd.forward_axis, 'Y') as forward_axis,
                   COALESCE(cd.default_rotation, 0) as default_rotation
            FROM component_definitions cd
            JOIN component_types ct ON cd.type_id = ct.id
            WHERE ct.ifc_class = 'IfcWindow'
            """;

        try (Statement stmt = libraryConn.createStatement();
             ResultSet rs = stmt.executeQuery(windowQuery)) {
            while (rs.next()) {
                var comp = new LibraryComponent(
                    rs.getInt("id"),
                    rs.getString("name"),
                    rs.getString("geometry_hash"),
                    rs.getDouble("width_mm"),
                    rs.getDouble("height_mm"),
                    rs.getDouble("depth_mm"),
                    rs.getDouble("local_min_z"),
                    rs.getDouble("local_min_x"),
                    rs.getDouble("local_min_y"),
                    rs.getString("forward_axis"),
                    rs.getDouble("default_rotation")
                );
                // Phase 88: Store ALL variants for orientation matching
                allWindows.add(comp);
                // Keep first-match cache for backward compat
                String key = comp.widthMm() + "x" + comp.heightMm();
                if (!windowCache.containsKey(key)) {
                    windowCache.put(key, comp);
                }
            }
        }

        // Phase 79: Load sprinklers (for fire protection) with local bounds
        String sprinklerQuery = """
            SELECT cd.id, cd.name, cd.geometry_hash, cd.orientation,
                   ROUND((cd.local_max_x - cd.local_min_x) * 1000, 0) as width_mm,
                   ROUND((cd.local_max_z - cd.local_min_z) * 1000, 0) as height_mm,
                   ROUND((cd.local_max_y - cd.local_min_y) * 1000, 0) as depth_mm,
                   cd.local_min_z, cd.local_min_x, cd.local_min_y
            FROM component_definitions cd
            JOIN component_types ct ON cd.type_id = ct.id
            WHERE ct.ifc_class = 'IfcFireSuppressionTerminal'
            AND cd.name LIKE '%sprinkler%'
            """;

        try (Statement stmt = libraryConn.createStatement();
             ResultSet rs = stmt.executeQuery(sprinklerQuery)) {
            while (rs.next()) {
                String orientation = rs.getString("orientation");  // PENDANT or UPRIGHT
                // Store first match for each orientation
                if (orientation != null && !sprinklerCache.containsKey(orientation)) {
                    sprinklerCache.put(orientation, new LibraryComponent(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("geometry_hash"),
                        rs.getDouble("width_mm"),
                        rs.getDouble("height_mm"),
                        rs.getDouble("depth_mm"),
                        rs.getDouble("local_min_z"),
                        rs.getDouble("local_min_x"),
                        rs.getDouble("local_min_y")
                    ));
                }
            }
        }

        System.out.printf("[DoorWindowLibraryMapper] Loaded %d door variants (%d unique sizes), %d window variants (%d unique sizes), %d sprinkler types%n",
            allDoors.size(), doorCache.size(), allWindows.size(), windowCache.size(), sprinklerCache.size());
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
     * Phase 88: Orientation-aware door mapping.
     * Searches allDoors for a variant natively oriented for the target wall.
     * If found, returns orientationMatched=true (skip rotation).
     * Falls back to size-only mapDoor() if no oriented variant exists.
     */
    public MappingResult mapDoor(double widthMm, double heightMm, String scheduleType, String wall) {
        if (wall == null || wall.isEmpty()) {
            return mapDoor(widthMm, heightMm, scheduleType);
        }

        // Search allDoors for orientation-matched variant
        LibraryComponent best = null;
        double bestDist = Double.MAX_VALUE;

        for (LibraryComponent comp : allDoors) {
            if (!comp.isNativelyOrientedFor(wall)) continue;
            double w = comp.openingWidthMmForWall(wall);
            double h = comp.heightMm();
            double wDiff = Math.abs(w - widthMm);
            double hDiff = Math.abs(h - heightMm);
            if (wDiff <= WIDTH_TOLERANCE_MM && hDiff <= TOLERANCE_MM) {
                double dist = wDiff + hDiff;
                if (dist < bestDist) {
                    bestDist = dist;
                    best = comp;
                }
            }
        }

        if (best != null) {
            System.out.printf("[DoorWindowLibraryMapper] %s (%.0fx%.0f wall:%s) → ORIENTED %s (%.0fx%.0fx%.0f)%n",
                scheduleType, widthMm, heightMm, wall, best.name(), best.depthMm(), best.widthMm(), best.heightMm());
            return MappingResult.libraryOriented(scheduleType, best);
        }

        // Fallback: any variant (will need rotation)
        return mapDoor(widthMm, heightMm, scheduleType);
    }

    /**
     * Map a window SCHEDULE type to library component.
     *
     * Phase 57B: Improved matching with scaling support.
     * Strategy:
     * 1. Try exact match
     * 2. Try close match within tolerance
     * 3. Find best scalable match (height within 50%, width flexible)
     * 4. Fall back to parametric only if no reasonable match
     */
    public MappingResult mapWindow(double widthMm, double heightMm, String scheduleType) {
        // Try exact match
        String exactKey = widthMm + "x" + heightMm;
        if (windowCache.containsKey(exactKey)) {
            return MappingResult.library(scheduleType, windowCache.get(exactKey));
        }

        // Try closest match within tolerance (no scaling needed)
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

        // Phase 57B: Try scalable match - find window with similar height, scale width
        // Height is most visually important, width can be scaled more aggressively
        LibraryComponent bestScalable = null;
        double bestHeightMatch = Double.MAX_VALUE;

        for (var entry : windowCache.entrySet()) {
            LibraryComponent comp = entry.getValue();
            double heightRatio = heightMm / comp.heightMm;
            double widthRatio = widthMm / comp.widthMm;

            // Accept if height scale is reasonable (0.5x to 2.0x)
            // and width scale is reasonable (0.3x to 3.0x)
            if (heightRatio >= 0.5 && heightRatio <= 2.0 &&
                widthRatio >= 0.3 && widthRatio <= 3.0) {
                double heightDiff = Math.abs(comp.heightMm - heightMm);
                if (heightDiff < bestHeightMatch) {
                    bestHeightMatch = heightDiff;
                    bestScalable = comp;
                }
            }
        }

        if (bestScalable != null) {
            double scaleX = widthMm / bestScalable.widthMm;
            double scaleZ = heightMm / bestScalable.heightMm;
            System.out.printf("[DoorWindowLibraryMapper] %s (%.0fx%.0f) → SCALED library %s (%.2fx%.2f)%n",
                scheduleType, widthMm, heightMm, bestScalable.name, scaleX, scaleZ);
            return MappingResult.libraryScaled(scheduleType, bestScalable, scaleX, 1.0, scaleZ);
        }

        // No reasonable match - fall back to parametric
        return MappingResult.parametric(scheduleType,
            "No scalable library window found");
    }

    /**
     * Phase 88: Orientation-aware window mapping.
     * Searches allWindows for a variant natively oriented for the target wall.
     * If found, returns orientationMatched=true (skip rotation).
     * Falls back to size-only mapWindow() if no oriented variant exists.
     */
    public MappingResult mapWindow(double widthMm, double heightMm, String scheduleType, String wall) {
        if (wall == null || wall.isEmpty()) {
            return mapWindow(widthMm, heightMm, scheduleType);
        }

        // Search allWindows for orientation-matched variant (exact/close)
        LibraryComponent best = null;
        double bestDist = Double.MAX_VALUE;

        for (LibraryComponent comp : allWindows) {
            if (!comp.isNativelyOrientedFor(wall)) continue;
            double w = comp.openingWidthMmForWall(wall);
            double h = comp.heightMm();
            double wDiff = Math.abs(w - widthMm);
            double hDiff = Math.abs(h - heightMm);
            if (wDiff <= WIDTH_TOLERANCE_MM && hDiff <= TOLERANCE_MM) {
                double dist = wDiff + hDiff;
                if (dist < bestDist) {
                    bestDist = dist;
                    best = comp;
                }
            }
        }

        if (best != null) {
            System.out.printf("[DoorWindowLibraryMapper] %s (%.0fx%.0f wall:%s) → ORIENTED %s%n",
                scheduleType, widthMm, heightMm, wall, best.name());
            return MappingResult.libraryOriented(scheduleType, best);
        }

        // Try scalable orientation-matched variant
        LibraryComponent bestScalable = null;
        double bestHeightMatch = Double.MAX_VALUE;

        for (LibraryComponent comp : allWindows) {
            if (!comp.isNativelyOrientedFor(wall)) continue;
            double w = comp.openingWidthMmForWall(wall);
            double h = comp.heightMm();
            double heightRatio = heightMm / h;
            double widthRatio = widthMm / w;
            if (heightRatio >= 0.5 && heightRatio <= 2.0 &&
                widthRatio >= 0.3 && widthRatio <= 3.0) {
                double heightDiff = Math.abs(h - heightMm);
                if (heightDiff < bestHeightMatch) {
                    bestHeightMatch = heightDiff;
                    bestScalable = comp;
                }
            }
        }

        if (bestScalable != null) {
            double w = bestScalable.openingWidthMmForWall(wall);
            double scaleX = widthMm / w;
            double scaleZ = heightMm / bestScalable.heightMm();
            System.out.printf("[DoorWindowLibraryMapper] %s (%.0fx%.0f wall:%s) → SCALED ORIENTED %s (%.2fx%.2f)%n",
                scheduleType, widthMm, heightMm, wall, bestScalable.name(), scaleX, scaleZ);
            return MappingResult.libraryScaledOriented(scheduleType, bestScalable, scaleX, 1.0, scaleZ);
        }

        // Fallback: any variant (will need rotation)
        return mapWindow(widthMm, heightMm, scheduleType);
    }

    /**
     * Phase 79: Get sprinkler library component by type (PENDANT or UPRIGHT).
     *
     * @param type Sprinkler type ("pendant" or "upright")
     * @return Library component with geometry hash, or null if not found
     */
    public LibraryComponent getSprinklerComponent(String type) {
        String key = type.toUpperCase();
        return sprinklerCache.get(key);
    }

    /**
     * Phase 79: Check if sprinkler library components are available.
     */
    public boolean hasSprinklerComponents() {
        return !sprinklerCache.isEmpty();
    }

    /**
     * Phase X-Ray: Get a library geometry hash for common sanitary/fixture types.
     * Used as a fallback when fixture.geometryHash() is null — avoids BBox placeholder.
     * Returns null if no library match (caller falls through to parametric box).
     * [EXTRACTED: MEPWriter.writeFixture() §parametric BBox fallback path]
     */
    public String getFixtureGeometryHash(String fixtureType) {
        String nameLike = switch (fixtureType.toLowerCase()) {
            case "sink"   -> "%round sink%";
            case "toilet" -> "%toilet%";
            case "shower" -> "%shower%";
            default       -> null;
        };
        if (nameLike == null) return null;
        try {
            String q = "SELECT geometry_hash FROM component_definitions "
                     + "WHERE LOWER(name) LIKE ? AND geometry_hash IS NOT NULL LIMIT 1";
            try (PreparedStatement ps = libraryConn.prepareStatement(q)) {
                ps.setString(1, nameLike);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getString(1) : null;
                }
            }
        } catch (SQLException e) {
            return null;
        }
    }

    /**
     * Phase 79: Get local Z bounds for a geometry hash (for attachment offset calculation).
     * Returns [localMinZ, localMaxZ] or null if not found.
     */
    public double[] getLocalZBounds(String geometryHash) throws SQLException {
        String query = """
            SELECT local_min_z, local_max_z
            FROM component_definitions
            WHERE geometry_hash = ?
            """;
        try (PreparedStatement ps = libraryConn.prepareStatement(query)) {
            ps.setString(1, geometryHash);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new double[] { rs.getDouble(1), rs.getDouble(2) };
                }
            }
        }
        return null;
    }

    /**
     * Phase 92C: Get full local bounds [minX, maxX, minY, maxY, minZ, maxZ] for a geometry hash.
     * Returns null if not found.
     */
    public double[] getLocalBounds(String geometryHash) throws SQLException {
        String query = """
            SELECT local_min_x, local_max_x, local_min_y, local_max_y, local_min_z, local_max_z
            FROM component_definitions
            WHERE geometry_hash = ?
            """;
        try (PreparedStatement ps = libraryConn.prepareStatement(query)) {
            ps.setString(1, geometryHash);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new double[] {
                        rs.getDouble(1), rs.getDouble(2),
                        rs.getDouble(3), rs.getDouble(4),
                        rs.getDouble(5), rs.getDouble(6)
                    };
                }
            }
        }
        return null;
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
     * Phase 54: Read library geometry as Mesh for transformation.
     *
     * Library geometry is stored in local coordinates (centered at origin).
     * This method reads and parses it into a Mesh object that can be
     * transformed using GeometryEngine.
     *
     * @param geometryHash The geometry hash to look up
     * @return Mesh object or null if not found
     */
    public Mesh readLibraryGeometry(String geometryHash) throws SQLException {
        Mesh cached = meshCache.get(geometryHash);
        if (cached != null) return cached;

        String query = """
            SELECT vertices, faces, vertex_count, face_count
            FROM component_geometries WHERE geometry_hash = ?
            """;
        try (PreparedStatement ps = libraryConn.prepareStatement(query)) {
            ps.setString(1, geometryHash);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    byte[] vertexBytes = rs.getBytes("vertices");
                    byte[] faceBytes = rs.getBytes("faces");
                    int vertexCount = rs.getInt("vertex_count");
                    int faceCount = rs.getInt("face_count");

                    Mesh mesh = parseMeshFromBlobs(vertexBytes, faceBytes, vertexCount, faceCount);
                    meshCache.put(geometryHash, mesh);
                    return mesh;
                }
            }
        }
        return null;
    }

    /**
     * Parse mesh from binary blob format.
     * Vertices: float[vertexCount * 3] (x,y,z triplets)
     * Faces: int[faceCount * 3] (triangle indices)
     */
    private Mesh parseMeshFromBlobs(byte[] vertexBytes, byte[] faceBytes,
                                     int vertexCount, int faceCount) {
        // Parse vertices
        List<Point3D> vertices = new ArrayList<>();
        ByteBuffer vb = ByteBuffer.wrap(vertexBytes).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < vertexCount; i++) {
            float x = vb.getFloat();
            float y = vb.getFloat();
            float z = vb.getFloat();
            vertices.add(new Point3D(x, y, z));
        }

        // Parse faces
        List<int[]> faces = new ArrayList<>();
        ByteBuffer fb = ByteBuffer.wrap(faceBytes).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < faceCount; i++) {
            int v0 = fb.getInt();
            int v1 = fb.getInt();
            int v2 = fb.getInt();
            faces.add(new int[]{v0, v1, v2});
        }

        return new Mesh(vertices, faces);
    }

    /**
     * Phase 54: Transform library geometry to world-space and write to output.
     *
     * This enables using actual LOD400 library geometry instead of box fallback,
     * while maintaining Pattern B (world-space geometry + zero transform).
     *
     * @param outputConn Output database connection
     * @param geometryHash Library geometry hash
     * @param translateX World X position
     * @param translateY World Y position
     * @param translateZ World Z position
     * @param rotateZ Rotation around Z axis (radians, 0 = door faces +Y)
     * @return New geometry hash for transformed geometry
     */
    public String transformAndWriteGeometry(Connection outputConn, String geometryHash,
                                            double translateX, double translateY, double translateZ,
                                            double rotateZ) throws SQLException {
        // Read library geometry
        Mesh libMesh = readLibraryGeometry(geometryHash);
        if (libMesh == null) {
            return null;
        }

        // Transform: rotate then translate to world position
        Mesh transformed = libMesh;
        if (Math.abs(rotateZ) > 0.001) {
            transformed = GeometryEngine.rotateZ(transformed, rotateZ);
        }
        transformed = GeometryEngine.translate(transformed, translateX, translateY, translateZ);

        // Generate new hash for transformed geometry
        String newHash = "LOD_" + geometryHash + "_" +
            String.format("%.2f_%.2f_%.2f_%.2f", translateX, translateY, translateZ, rotateZ)
            .replace('.', '_').replace('-', 'n');

        // Check if already exists
        String checkQuery = "SELECT 1 FROM base_geometries WHERE geometry_hash = ?";
        try (PreparedStatement ps = outputConn.prepareStatement(checkQuery)) {
            ps.setString(1, newHash);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return newHash; // Already exists
                }
            }
        }

        // Convert to blob format and write
        float[] vertexFloats = new float[transformed.vertexCount() * 3];
        int vi = 0;
        for (Point3D v : transformed.vertices()) {
            vertexFloats[vi++] = (float) v.x();
            vertexFloats[vi++] = (float) v.y();
            vertexFloats[vi++] = (float) v.z();
        }

        int[] faceInts = new int[transformed.faceCount() * 3];
        int fi = 0;
        for (int[] face : transformed.faces()) {
            faceInts[fi++] = face[0];
            faceInts[fi++] = face[1];
            faceInts[fi++] = face[2];
        }

        String insertQuery = """
            INSERT INTO base_geometries (geometry_hash, vertices, faces, vertex_count, face_count)
            VALUES (?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = outputConn.prepareStatement(insertQuery)) {
            ps.setString(1, newHash);
            ps.setBytes(2, floatsToBlob(vertexFloats));
            ps.setBytes(3, intsToBlob(faceInts));
            ps.setInt(4, transformed.vertexCount());
            ps.setInt(5, transformed.faceCount());
            ps.executeUpdate();
        }

        return newHash;
    }

    /**
     * Phase 57B: Transform and scale library geometry for windows.
     * Allows library windows to be scaled to match requested dimensions.
     */
    public String transformAndWriteGeometryScaled(Connection outputConn, String geometryHash,
                                                   double translateX, double translateY, double translateZ,
                                                   double rotateZ,
                                                   double scaleX, double scaleY, double scaleZ) throws SQLException {
        // If no scaling needed, use regular transform
        if (Math.abs(scaleX - 1.0) < 0.001 && Math.abs(scaleY - 1.0) < 0.001 && Math.abs(scaleZ - 1.0) < 0.001) {
            return transformAndWriteGeometry(outputConn, geometryHash, translateX, translateY, translateZ, rotateZ);
        }

        // Read library geometry
        Mesh libMesh = readLibraryGeometry(geometryHash);
        if (libMesh == null) {
            return null;
        }

        // Transform: scale, then rotate, then translate
        Mesh transformed = GeometryEngine.scale(libMesh, scaleX, scaleY, scaleZ);
        if (Math.abs(rotateZ) > 0.001) {
            transformed = GeometryEngine.rotateZ(transformed, rotateZ);
        }
        transformed = GeometryEngine.translate(transformed, translateX, translateY, translateZ);

        // Generate hash that includes scale factors
        String newHash = "LOD_" + geometryHash + "_" +
            String.format("%.2f_%.2f_%.2f_%.2f_s%.2f_%.2f_%.2f",
                translateX, translateY, translateZ, rotateZ, scaleX, scaleY, scaleZ)
            .replace('.', '_').replace('-', 'n');

        // Check if already exists
        String checkQuery = "SELECT 1 FROM base_geometries WHERE geometry_hash = ?";
        try (PreparedStatement ps = outputConn.prepareStatement(checkQuery)) {
            ps.setString(1, newHash);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return newHash;
                }
            }
        }

        // Convert and write
        float[] vertexFloats = new float[transformed.vertexCount() * 3];
        int vi = 0;
        for (Point3D v : transformed.vertices()) {
            vertexFloats[vi++] = (float) v.x();
            vertexFloats[vi++] = (float) v.y();
            vertexFloats[vi++] = (float) v.z();
        }

        int[] faceInts = new int[transformed.faceCount() * 3];
        int fi = 0;
        for (int[] face : transformed.faces()) {
            faceInts[fi++] = face[0];
            faceInts[fi++] = face[1];
            faceInts[fi++] = face[2];
        }

        String insertQuery = """
            INSERT INTO base_geometries (geometry_hash, vertices, faces, vertex_count, face_count)
            VALUES (?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = outputConn.prepareStatement(insertQuery)) {
            ps.setString(1, newHash);
            ps.setBytes(2, floatsToBlob(vertexFloats));
            ps.setBytes(3, intsToBlob(faceInts));
            ps.setInt(4, transformed.vertexCount());
            ps.setInt(5, transformed.faceCount());
            ps.executeUpdate();
        }

        return newHash;
    }

    /**
     * Get library component bounding box as fallback metadata.
     * Even when LOD400 geometry isn't available, this provides dimensions.
     */
    public BoundingBox getComponentBounds(LibraryComponent comp) {
        // Component stores dimensions in mm, convert to meters
        double w = comp.widthMm() / 1000.0;
        double h = comp.heightMm() / 1000.0;
        double d = comp.depthMm() / 1000.0;

        // Local bounds (centered at origin)
        return new BoundingBox(-d/2, d/2, -w/2, w/2, 0, h);
    }

    private byte[] floatsToBlob(float[] floats) {
        ByteBuffer buffer = ByteBuffer.allocate(floats.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float f : floats) buffer.putFloat(f);
        return buffer.array();
    }

    private byte[] intsToBlob(int[] ints) {
        ByteBuffer buffer = ByteBuffer.allocate(ints.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (int i : ints) buffer.putInt(i);
        return buffer.array();
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
