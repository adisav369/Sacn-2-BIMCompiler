package com.bim.compiler.dsl;

import java.sql.*;

/**
 * Spatial structure builder — normalizes storeys, emits IfcSpace from L2 ESLines,
 * and populates rel_contained_in_space.
 *
 * <p>Extracted from CompilationPipeline (Phase H3) so that both the fallback path
 * and the BUILD SPATIAL STRUCTURE verb can share this logic.
 *
 * <p>Three sequentially dependent operations:
 * <ol>
 *   <li>{@link #normalizeStoreyNames()} — INSERT IfcBuildingStorey for missing storeys</li>
 *   <li>{@link #emitIfcSpaceFromL2()} — INSERT IfcSpace from L2 spatial slots (compiler-internal: co_empty_space_line)</li>
 *   <li>{@link #populateSpaceContainment()} — INSERT/UPDATE rel_contained_in_space</li>
 * </ol>
 *
 * <p>ASSUMPTION: Spatial containers are IfcBuilding + IfcBuildingStorey.
 * All three operations query {@code type = 'IfcBuildingStorey'} and will no-op
 * for infrastructure IFCs that use IfcFacilityPart. See {@code docs/InfrastructureAnalysis.md}.
 */
public class SpatialStructureBuilder {

    private final Connection conn;

    public SpatialStructureBuilder(Connection conn) {
        this.conn = conn;
    }

    /**
     * Run all three spatial structure operations in sequence.
     * @return result summary (storeys added, spaces emitted, containment counts)
     */
    public Result buildAll() throws SQLException {
        int storeys = normalizeStoreyNames();
        int spaces = emitIfcSpaceFromL2();
        int[] containment = populateSpaceContainment();
        return new Result(storeys, spaces, containment[0], containment[1]);
    }

    public record Result(int storeysAdded, int spacesEmitted,
                          int storeyContained, int roomContained) {}

    /**
     * Gap #8: Ensure every elements_meta storey has a matching
     * IfcBuildingStorey in spatial_structure.
     */
    public int normalizeStoreyNames() throws SQLException {
        String buildingGuid = null;
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT guid FROM spatial_structure WHERE type = 'IfcBuilding' LIMIT 1")) {
            if (rs.next()) buildingGuid = rs.getString(1);
        }
        if (buildingGuid == null) return 0;

        int added = 0;
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("""
                 SELECT DISTINCT em.storey
                 FROM elements_meta em
                 WHERE em.storey IS NOT NULL
                   AND em.storey NOT IN (SELECT name FROM spatial_structure WHERE type = 'IfcBuildingStorey')
                 """)) {
            while (rs.next()) {
                String storey = rs.getString(1);
                String storeyGuid = "STOREY_" + storey.toUpperCase().replace(" ", "_").replace("/", "_");
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT OR IGNORE INTO spatial_structure VALUES (?, 'IfcBuildingStorey', ?, ?, NULL, NULL)")) {
                    ps.setString(1, storeyGuid);
                    ps.setString(2, storey);
                    ps.setString(3, buildingGuid);
                    ps.execute();
                    added++;
                }
            }
        }
        if (added > 0) {
            conn.commit();
        }
        return added;
    }

    /**
     * Gap #5: Emit IfcSpace rows in spatial_structure from L2 co_empty_space_line entries.
     */
    public int emitIfcSpaceFromL2() throws SQLException {
        int spaceCount = 0;
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("""
                 SELECT DISTINCT esl.storey, esl.room_name, esl.bom_line_role
                 FROM co_empty_space_line esl
                 WHERE esl.bom_level = 2
                   AND esl.storey IS NOT NULL
                   AND (esl.room_name IS NOT NULL OR esl.bom_line_role IS NOT NULL)
                 """)) {
            while (rs.next()) {
                String storey = rs.getString("storey");
                String roomName = rs.getString("room_name");
                if (roomName == null) roomName = rs.getString("bom_line_role");
                if (roomName == null) continue;

                String storeyGuid = "STOREY_" + storey.toUpperCase().replace(" ", "_");
                String spaceGuid = "SPACE_" + storey.toUpperCase().replace(" ", "_")
                                 + "_" + roomName.toUpperCase().replace(" ", "_");

                // Check storey exists in spatial_structure (skip if not)
                try (PreparedStatement check = conn.prepareStatement(
                        "SELECT 1 FROM spatial_structure WHERE guid = ?")) {
                    check.setString(1, storeyGuid);
                    try (ResultSet crs = check.executeQuery()) {
                        if (!crs.next()) continue;
                    }
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT OR IGNORE INTO spatial_structure VALUES (?, ?, ?, ?, ?, ?)")) {
                    ps.setString(1, spaceGuid);
                    ps.setString(2, "IfcSpace");
                    ps.setString(3, roomName);
                    ps.setString(4, storeyGuid);
                    ps.setString(5, null); // object_type
                    ps.setString(6, roomTypeToPredefined(roomName));
                    ps.execute();
                    spaceCount++;
                }
            }
        }
        if (spaceCount > 0) {
            conn.commit();
        }
        return spaceCount;
    }

    /**
     * Gap #6: Populate rel_contained_in_space using centroid-in-AABB matching.
     * @return int[2]: [storeyContained, roomContained]
     */
    public int[] populateSpaceContainment() throws SQLException {
        // Pass 1: storey-level containment
        int storeyContained;
        try (Statement stmt = conn.createStatement()) {
            storeyContained = stmt.executeUpdate("""
                INSERT OR IGNORE INTO rel_contained_in_space (element_guid, space_guid)
                SELECT em.guid, ss.guid
                FROM elements_meta em
                JOIN spatial_structure ss ON em.storey = ss.name
                WHERE ss.type = 'IfcBuildingStorey'
                """);
        }

        // Pass 2: room-level containment — smallest-AABB-wins
        int roomContained;
        try (Statement stmt = conn.createStatement()) {
            roomContained = stmt.executeUpdate("""
                INSERT OR REPLACE INTO rel_contained_in_space (element_guid, space_guid)
                SELECT element_guid, space_guid
                FROM (
                    SELECT em.guid AS element_guid,
                           'SPACE_' || REPLACE(UPPER(esl.storey),' ','_')
                             || '_' || REPLACE(UPPER(COALESCE(esl.room_name, esl.bom_line_role)),' ','_') AS space_guid,
                           ROW_NUMBER() OVER (
                               PARTITION BY em.guid
                               ORDER BY (esl.next_x_mm - esl.before_x_mm) * (esl.next_y_mm - esl.before_y_mm) ASC,
                                        esl.line_id ASC
                           ) AS rn
                    FROM elements_meta em
                    JOIN elements_rtree er ON em.id = er.id
                    JOIN co_empty_space_line esl ON esl.bom_level = 2
                      AND esl.storey IS NOT NULL
                      AND (esl.room_name IS NOT NULL OR esl.bom_line_role IS NOT NULL)
                    JOIN spatial_structure ss
                      ON ss.guid = 'SPACE_' || REPLACE(UPPER(esl.storey),' ','_')
                                     || '_' || REPLACE(UPPER(COALESCE(esl.room_name, esl.bom_line_role)),' ','_')
                    WHERE ((er.minX + er.maxX) / 2.0) * 1000.0 BETWEEN esl.before_x_mm AND esl.next_x_mm
                      AND ((er.minY + er.maxY) / 2.0) * 1000.0 BETWEEN esl.before_y_mm AND esl.next_y_mm
                      AND ((er.minZ + er.maxZ) / 2.0) * 1000.0 BETWEEN esl.before_z_mm AND esl.next_z_mm
                ) WHERE rn = 1
                """);
        }
        conn.commit();
        return new int[]{storeyContained, roomContained};
    }

    /** Map room name/role to IFC PredefinedType for IfcSpace. */
    static String roomTypeToPredefined(String roomName) {
        if (roomName == null) return null;
        String upper = roomName.toUpperCase();
        if (upper.contains("LIVING") || upper.contains("LI"))     return "LIVING";
        if (upper.contains("BEDROOM") || upper.contains("BD"))    return "BEDROOM";
        if (upper.contains("KITCHEN") || upper.contains("KT"))    return "KITCHEN";
        if (upper.contains("BATH") || upper.contains("BT"))       return "BATHROOM";
        if (upper.contains("DINING") || upper.contains("DN"))     return "DINING";
        if (upper.contains("GARAGE"))                             return "GARAGE";
        if (upper.contains("CORRIDOR") || upper.contains("HALL")) return "CORRIDOR";
        return null;
    }
}
