package com.bim.compiler.library;

import com.bim.compiler.geometry.Point3D;
import com.bim.compiler.geometry.BoundingBox;
import com.bim.compiler.util.OutlierLogger;

import java.sql.*;
import java.util.*;

/**
 * Component Library - loads LOD500 components from library database.
 *
 * Library contains:
 * - Geometry (vertices, faces) in LOCAL coordinates
 * - Attachment convention (TOP, BOTTOM, SIDE)
 * - Orientation (PENDANT, UPRIGHT, etc.)
 * - Placement rules (grid spacing, clearance)
 */
public class ComponentLibrary {

    private final Connection conn;
    private final Map<String, ComponentDefinition> cache = new HashMap<>();

    public ComponentLibrary(String dbPath) throws SQLException {
        this.conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
    }

    /**
     * Get component definition by name pattern.
     */
    public ComponentDefinition getByName(String namePattern) throws SQLException {
        if (cache.containsKey(namePattern)) {
            return cache.get(namePattern);
        }

        String sql = """
            SELECT cd.id, cd.name, cd.geometry_hash,
                   cd.local_min_x, cd.local_max_x,
                   cd.local_min_y, cd.local_max_y,
                   cd.local_min_z, cd.local_max_z,
                   cd.attachment_face, cd.orientation,
                   cd.vertex_count, cd.face_count
            FROM component_definitions cd
            WHERE cd.name LIKE ?
            LIMIT 1
            """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, "%" + namePattern + "%");
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                ComponentDefinition def = new ComponentDefinition(
                    rs.getInt("id"),
                    rs.getString("name"),
                    rs.getString("geometry_hash"),
                    new BoundingBox(
                        rs.getDouble("local_min_x"),
                        rs.getDouble("local_max_x"),
                        rs.getDouble("local_min_y"),
                        rs.getDouble("local_max_y"),
                        rs.getDouble("local_min_z"),
                        rs.getDouble("local_max_z")
                    ),
                    AttachmentFace.valueOf(rs.getString("attachment_face")),
                    Orientation.valueOf(rs.getString("orientation")),
                    rs.getInt("vertex_count"),
                    rs.getInt("face_count")
                );
                cache.put(namePattern, def);
                return def;
            }
        }
        return null;
    }

    /**
     * Get component definition by name containing pattern (case-insensitive).
     */
    public ComponentDefinition getByNameContaining(String namePattern) throws SQLException {
        if (namePattern == null || namePattern.isEmpty()) {
            return null;
        }
        // getByName already uses LIKE with wildcards
        return getByName(namePattern);
    }

    /**
     * Get pendant sprinkler definition.
     */
    public ComponentDefinition getPendantSprinkler() throws SQLException {
        return getByName("pendent");
    }

    /**
     * Get upright sprinkler definition.
     */
    public ComponentDefinition getUprightSprinkler() throws SQLException {
        return getByName("upright");
    }

    /**
     * Get geometry for component.
     */
    public ComponentGeometry getGeometry(String geometryHash) throws SQLException {
        String sql = """
            SELECT vertices, faces, vertex_count, face_count
            FROM component_geometries
            WHERE geometry_hash = ?
            """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, geometryHash);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return new ComponentGeometry(
                    rs.getBytes("vertices"),
                    rs.getBytes("faces"),
                    rs.getInt("vertex_count"),
                    rs.getInt("face_count")
                );
            }
        }
        return null;
    }

    /**
     * Get placement rules for component.
     */
    public PlacementRules getPlacementRules(int componentId) throws SQLException {
        String sql = """
            SELECT host_type, offset_from_host, grid_spacing, clearance_radius
            FROM placement_rules
            WHERE component_id = ?
            """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, componentId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return new PlacementRules(
                    rs.getString("host_type"),
                    rs.getDouble("offset_from_host"),
                    rs.getDouble("grid_spacing"),
                    rs.getDouble("clearance_radius")
                );
            }
        }
        return null;
    }

    // =========================================================================
    // STAIR COMPONENT LOOKUPS (Phase 14A)
    // =========================================================================

    /**
     * Check if library has a stair flight matching dimensions within tolerance.
     * @param targetWidth Desired stair width (m)
     * @param targetRise Desired stair rise/height (m)
     * @param tolerance Acceptable dimensional deviation (m)
     */
    public boolean hasStairFlight(double targetWidth, double targetRise, double tolerance) throws SQLException {
        return findStairFlight(targetWidth, targetRise, tolerance) != null;
    }

    /**
     * Find best-matching stair flight from library.
     * Terminal stairs: ~1.4m width, 2.0-2.2m rise, 3.04m run.
     *
     * @param targetWidth Desired stair width (m)
     * @param targetRise Desired stair rise/height (m)
     * @param tolerance Acceptable dimensional deviation (m)
     * @return Best matching ComponentDefinition, or null if none found
     */
    public ComponentDefinition findStairFlight(double targetWidth, double targetRise, double tolerance) throws SQLException {
        String sql = """
            SELECT cd.id, cd.name, cd.geometry_hash,
                   cd.local_min_x, cd.local_max_x,
                   cd.local_min_y, cd.local_max_y,
                   cd.local_min_z, cd.local_max_z,
                   cd.attachment_face, cd.orientation,
                   cd.vertex_count, cd.face_count,
                   cd.instance_count,
                   (cd.local_max_x - cd.local_min_x) as width,
                   (cd.local_max_z - cd.local_min_z) as rise
            FROM component_definitions cd
            JOIN component_types ct ON cd.type_id = ct.id
            WHERE ct.ifc_class = 'IfcStairFlight'
              AND ABS((cd.local_max_x - cd.local_min_x) - ?) < ?
              AND ABS((cd.local_max_z - cd.local_min_z) - ?) < ?
            ORDER BY
                ABS((cd.local_max_x - cd.local_min_x) - ?) +
                ABS((cd.local_max_z - cd.local_min_z) - ?),
                cd.instance_count DESC
            LIMIT 1
            """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDouble(1, targetWidth);
            stmt.setDouble(2, tolerance);
            stmt.setDouble(3, targetRise);
            stmt.setDouble(4, tolerance);
            stmt.setDouble(5, targetWidth);
            stmt.setDouble(6, targetRise);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return parseComponentDefinition(rs);
            }
        }
        return null;
    }

    /**
     * Find railing matching stair dimensions.
     * @param targetLength Desired railing length (along stair run)
     * @param targetHeight Desired railing height span
     * @param tolerance Acceptable deviation
     */
    public ComponentDefinition findRailing(double targetLength, double targetHeight, double tolerance) throws SQLException {
        String sql = """
            SELECT cd.id, cd.name, cd.geometry_hash,
                   cd.local_min_x, cd.local_max_x,
                   cd.local_min_y, cd.local_max_y,
                   cd.local_min_z, cd.local_max_z,
                   cd.attachment_face, cd.orientation,
                   cd.vertex_count, cd.face_count,
                   cd.instance_count
            FROM component_definitions cd
            JOIN component_types ct ON cd.type_id = ct.id
            WHERE ct.ifc_class = 'IfcRailing'
              AND ABS((cd.local_max_y - cd.local_min_y) - ?) < ?
              AND ABS((cd.local_max_z - cd.local_min_z) - ?) < ?
            ORDER BY
                ABS((cd.local_max_y - cd.local_min_y) - ?) +
                ABS((cd.local_max_z - cd.local_min_z) - ?),
                cd.instance_count DESC
            LIMIT 1
            """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDouble(1, targetLength);
            stmt.setDouble(2, tolerance);
            stmt.setDouble(3, targetHeight);
            stmt.setDouble(4, tolerance);
            stmt.setDouble(5, targetLength);
            stmt.setDouble(6, targetHeight);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return parseComponentDefinition(rs);
            }
        }
        return null;
    }

    /**
     * Find stringer members matching stair height.
     * @param targetHeight Stringer height (floor-to-floor span)
     * @param tolerance Acceptable deviation
     */
    public List<ComponentDefinition> findStringers(double targetHeight, double tolerance) throws SQLException {
        String sql = """
            SELECT cd.id, cd.name, cd.geometry_hash,
                   cd.local_min_x, cd.local_max_x,
                   cd.local_min_y, cd.local_max_y,
                   cd.local_min_z, cd.local_max_z,
                   cd.attachment_face, cd.orientation,
                   cd.vertex_count, cd.face_count,
                   cd.instance_count
            FROM component_definitions cd
            JOIN component_types ct ON cd.type_id = ct.id
            WHERE ct.ifc_class = 'IfcMember'
              AND LOWER(cd.name) LIKE '%stringer%'
              AND ABS((cd.local_max_z - cd.local_min_z) - ?) < ?
            ORDER BY ABS((cd.local_max_z - cd.local_min_z) - ?)
            LIMIT 4
            """;

        List<ComponentDefinition> results = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDouble(1, targetHeight);
            stmt.setDouble(2, tolerance);
            stmt.setDouble(3, targetHeight);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                results.add(parseComponentDefinition(rs));
            }
        }
        return results;
    }

    /**
     * Get all components of a specific IFC class.
     */
    public List<ComponentDefinition> getByIfcClass(String ifcClass, int limit) throws SQLException {
        String sql = """
            SELECT cd.id, cd.name, cd.geometry_hash,
                   cd.local_min_x, cd.local_max_x,
                   cd.local_min_y, cd.local_max_y,
                   cd.local_min_z, cd.local_max_z,
                   cd.attachment_face, cd.orientation,
                   cd.vertex_count, cd.face_count,
                   cd.instance_count
            FROM component_definitions cd
            JOIN component_types ct ON cd.type_id = ct.id
            WHERE ct.ifc_class = ?
            ORDER BY cd.instance_count DESC
            LIMIT ?
            """;

        List<ComponentDefinition> results = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, ifcClass);
            stmt.setInt(2, limit);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                results.add(parseComponentDefinition(rs));
            }
        }
        return results;
    }

    /**
     * Check if component type exists in library.
     */
    public boolean hasComponentType(String ifcClass) throws SQLException {
        String sql = """
            SELECT COUNT(*) as cnt
            FROM component_definitions cd
            JOIN component_types ct ON cd.type_id = ct.id
            WHERE ct.ifc_class = ?
            """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, ifcClass);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("cnt") > 0;
            }
        }
        return false;
    }

    /**
     * Get library statistics.
     */
    public Map<String, Integer> getStats() throws SQLException {
        String sql = """
            SELECT ct.ifc_class, COUNT(*) as cnt
            FROM component_definitions cd
            JOIN component_types ct ON cd.type_id = ct.id
            GROUP BY ct.ifc_class
            ORDER BY cnt DESC
            """;

        Map<String, Integer> stats = new LinkedHashMap<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                stats.put(rs.getString("ifc_class"), rs.getInt("cnt"));
            }
        }
        return stats;
    }

    // Helper to parse ResultSet into ComponentDefinition
    private ComponentDefinition parseComponentDefinition(ResultSet rs) throws SQLException {
        String orientStr = rs.getString("orientation");
        Orientation orient = Orientation.UNKNOWN;
        if (orientStr != null) {
            try {
                orient = Orientation.valueOf(orientStr);
            } catch (IllegalArgumentException e) {
                orient = Orientation.UNKNOWN;
            }
        }

        String attachStr = rs.getString("attachment_face");
        AttachmentFace attach = AttachmentFace.CENTER;
        if (attachStr != null) {
            try {
                attach = AttachmentFace.valueOf(attachStr);
            } catch (IllegalArgumentException e) {
                attach = AttachmentFace.CENTER;
            }
        }

        return new ComponentDefinition(
            rs.getInt("id"),
            rs.getString("name"),
            rs.getString("geometry_hash"),
            new BoundingBox(
                rs.getDouble("local_min_x"),
                rs.getDouble("local_max_x"),
                rs.getDouble("local_min_y"),
                rs.getDouble("local_max_y"),
                rs.getDouble("local_min_z"),
                rs.getDouble("local_max_z")
            ),
            attach,
            orient,
            rs.getInt("vertex_count"),
            rs.getInt("face_count")
        );
    }

    public void close() throws SQLException {
        conn.close();
    }

    // =========================================================================
    // Phase 25: Component Fallback Handling
    // =========================================================================

    /**
     * Get component with graceful fallback on missing.
     * Phase 25: Tries exact match, then fuzzy match, then logs and returns null.
     *
     * @param requested Component name to find
     * @param context Context for logging (e.g., "BATHROOM ensuite")
     * @return ComponentDefinition or null if not found
     */
    public ComponentDefinition getComponentWithFallback(String requested, String context) throws SQLException {
        // 1. Exact match
        ComponentDefinition exact = getByName(requested);
        if (exact != null) {
            return exact;
        }

        // 2. Try similar match (fuzzy search)
        ComponentDefinition similar = findSimilar(requested);
        if (similar != null) {
            OutlierLogger.logMissingComponent(requested, context,
                "Using similar: " + similar.name());
            return similar;
        }

        // 3. Skip with log
        OutlierLogger.logMissingComponent(requested, context,
            "Skipped - no fallback available");
        return null;
    }

    /**
     * Find similar component by fuzzy matching.
     * Tries various strategies:
     * - Lowercase match
     * - Word reordering (single_bowl_sink → sink_single_bowl)
     * - Common synonyms
     */
    public ComponentDefinition findSimilar(String requested) throws SQLException {
        if (requested == null || requested.isEmpty()) {
            return null;
        }

        String lower = requested.toLowerCase();

        // Try with spaces replaced by underscores and vice versa
        String underscored = lower.replace(" ", "_");
        String spaced = lower.replace("_", " ");

        ComponentDefinition match = getByNameContaining(underscored);
        if (match != null) return match;

        match = getByNameContaining(spaced);
        if (match != null) return match;

        // Try common synonyms
        String[][] synonyms = {
            {"toilet", "wc", "water_closet"},
            {"sink", "basin", "lavatory"},
            {"light", "fixture", "luminaire"},
            {"diffuser", "grille", "vent"},
            {"sprinkler", "fire_suppression"},
            {"column", "post", "pillar"},
            {"beam", "lintel", "header"}
        };

        for (String[] group : synonyms) {
            for (String syn : group) {
                if (lower.contains(syn)) {
                    // Try other synonyms in the group
                    for (String alt : group) {
                        if (!alt.equals(syn)) {
                            match = getByNameContaining(alt);
                            if (match != null) return match;
                        }
                    }
                }
            }
        }

        // Try extracting key words
        String[] words = lower.split("[_\\s]+");
        for (String word : words) {
            if (word.length() > 3) { // Skip short words
                match = getByNameContaining(word);
                if (match != null) return match;
            }
        }

        return null;
    }

    /**
     * Check if library has a component containing the pattern.
     * Returns count of matching components.
     */
    public int countByNameContaining(String pattern) throws SQLException {
        String sql = """
            SELECT COUNT(*) as cnt
            FROM component_definitions
            WHERE LOWER(name) LIKE ?
            """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, "%" + pattern.toLowerCase() + "%");
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("cnt");
            }
        }
        return 0;
    }

    // =========================================================================
    // Inner classes for library data structures
    // =========================================================================

    public enum AttachmentFace { TOP, BOTTOM, SIDE, CENTER, ENDS }
    public enum Orientation { PENDANT, UPRIGHT, WALL_MOUNT, VERTICAL, HORIZONTAL, MIXED, UNKNOWN }

    public record ComponentDefinition(
        int id,
        String name,
        String geometryHash,
        BoundingBox localBounds,
        AttachmentFace attachmentFace,
        Orientation orientation,
        int vertexCount,
        int faceCount
    ) {}

    public record ComponentGeometry(
        byte[] vertices,
        byte[] faces,
        int vertexCount,
        int faceCount
    ) {}

    public record PlacementRules(
        String hostType,
        double offsetFromHost,
        double gridSpacing,
        double clearanceRadius
    ) {}
}
