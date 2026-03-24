package com.bim.cobol.verb;

import com.bim.cobol.Verb;
import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbResult;

import java.sql.*;

/**
 * BUILD SPATIAL STRUCTURE &lt;doc_sub_type&gt;
 *
 * <p>Wraps three tightly-coupled spatial structure methods:
 * <ol>
 *   <li>normalizeStoreyNames — INSERT IfcBuildingStorey for missing storeys</li>
 *   <li>emitIfcSpaceFromL2 — INSERT IfcSpace from L2 spatial slots (compiler-internal: co_empty_space_line)</li>
 *   <li>populateSpaceContainment — INSERT/UPDATE rel_contained_in_space</li>
 * </ol>
 *
 * <p>One verb because the three operations are sequentially dependent
 * (spaces need storeys, containment needs both).
 *
 * <p>Requires outputConn (write spatial_structure + rel_contained_in_space).
 */
public class BuildSpatialStructureVerb implements Verb<BuildSpatialStructureVerb.SpatialStructurePayload> {

    @Override
    public String keyword() { return "BUILD SPATIAL STRUCTURE"; }

    @Override
    public VerbResult<SpatialStructurePayload> execute(VerbContext ctx, String... args)
            throws SQLException {

        if (args.length < 1)
            return VerbResult.fail(keyword(),
                "usage: BUILD SPATIAL STRUCTURE <doc_sub_type>", null);

        String docSubType = args[0];

        Connection outputConn = ctx.outputConn();
        if (outputConn == null)
            return VerbResult.fail(keyword(),
                "outputConn required — BUILD SPATIAL STRUCTURE writes to output.db", null);

        // Step 1: Normalize storey names
        int storeysAdded = normalizeStoreyNames(outputConn);

        // Step 2: Emit IfcSpace from L2 ESLines
        int spacesEmitted = emitIfcSpaceFromL2(outputConn);

        // Step 3: Populate space containment
        int[] containment = populateSpaceContainment(outputConn);

        SpatialStructurePayload payload = new SpatialStructurePayload(
            docSubType, storeysAdded, spacesEmitted,
            containment[0], containment[1]);

        return VerbResult.ok(keyword(),
            String.format("BUILD SPATIAL STRUCTURE %s: %d storeys, %d spaces, %d+%d containment",
                docSubType, storeysAdded, spacesEmitted,
                containment[0], containment[1]),
            payload);
    }

    private int normalizeStoreyNames(Connection conn) throws SQLException {
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
        if (added > 0) conn.commit();
        return added;
    }

    private int emitIfcSpaceFromL2(Connection conn) throws SQLException {
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
                    ps.setString(5, null);
                    ps.setString(6, roomTypeToPredefined(roomName));
                    ps.execute();
                    spaceCount++;
                }
            }
        }
        if (spaceCount > 0) conn.commit();
        return spaceCount;
    }

    private int[] populateSpaceContainment(Connection conn) throws SQLException {
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

    private static String roomTypeToPredefined(String roomName) {
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

    public record SpatialStructurePayload(
        String docSubType, int storeysAdded, int spacesEmitted,
        int storeyContained, int roomContained
    ) {}
}
