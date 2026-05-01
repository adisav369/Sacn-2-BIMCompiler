/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.dsl;

import java.sql.*;
import java.util.List;

/**
 * Spatial structure builder — normalizes storeys, emits IfcSpace from room slots,
 * and populates rel_contained_in_space.
 *
 * <p>Extracted from CompilationPipeline (Phase H3) so that both the fallback path
 * and the BUILD SPATIAL STRUCTURE verb can share this logic.
 *
 * <p>Three sequentially dependent operations:
 * <ol>
 *   <li>{@link #normalizeStoreyNames()} — INSERT IfcBuildingStorey for missing storeys</li>
 *   <li>{@link #emitIfcSpaceFromRoomSlots()} — INSERT IfcSpace from room slots (M_BOM_Line dx/dy/dz)</li>
 *   <li>{@link #populateSpaceContainment()} — INSERT/UPDATE rel_contained_in_space</li>
 * </ol>
 *
 * <p>ASSUMPTION: Spatial containers are IfcBuilding + IfcBuildingStorey.
 * All three operations query {@code type = 'IfcBuildingStorey'} and will no-op
 * for infrastructure IFCs that use IfcFacilityPart. See {@code docs/InfrastructureAnalysis.md}.
 */
public class SpatialStructureBuilder {

    private final Connection conn;
    private final List<RoomSlot> roomSlots;

    /**
     * Room-level spatial slot — derived from M_BOM_Line dx/dy/dz (MANIFESTO.md §Three Concerns).
     * Replaces the former co_empty_space_line L2 rows (removed S74).
     */
    public record RoomSlot(String storey, String roomName, String bomLineRole,
                            double beforeXMm, double beforeYMm, double beforeZMm,
                            double nextXMm, double nextYMm, double nextZMm) {}

    public SpatialStructureBuilder(Connection conn, List<RoomSlot> roomSlots) {
        this.conn = conn;
        this.roomSlots = roomSlots != null ? roomSlots : List.of();
    }

    /**
     * Run all three spatial structure operations in sequence.
     * @return result summary (storeys added, spaces emitted, containment counts)
     */
    public Result buildAll() throws SQLException {
        int storeys = normalizeStoreyNames();
        int spaces = emitIfcSpaceFromRoomSlots();
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
     * Gap #5: Emit IfcSpace rows in spatial_structure from room slots
     * (derived from M_BOM_Line dx/dy/dz — MANIFESTO.md §Three Concerns).
     */
    public int emitIfcSpaceFromRoomSlots() throws SQLException {
        if (roomSlots.isEmpty()) return 0;

        int spaceCount = 0;
        for (RoomSlot slot : roomSlots) {
            String storey = slot.storey();
            String roomName = slot.roomName();
            if (roomName == null) roomName = slot.bomLineRole();
            if (storey == null || roomName == null) continue;

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
        if (spaceCount > 0) {
            conn.commit();
        }
        return spaceCount;
    }

    /**
     * Gap #6: Populate rel_contained_in_space using centroid-in-AABB matching.
     * Pass 1: storey-level (from elements_meta.storey).
     * Pass 2: room-level (from in-memory room slots via temp table).
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
        int roomContained = 0;
        if (!roomSlots.isEmpty()) {
            roomContained = populateRoomContainment();
        }
        conn.commit();
        return new int[]{storeyContained, roomContained};
    }

    /**
     * Room-level containment via temp table populated from in-memory room slots.
     * Same centroid-in-AABB logic as before, but sourced from M_BOM_Line spatial data.
     */
    private int populateRoomContainment() throws SQLException {
        // Create temp table for room slots
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS _tmp_room_slots");
            stmt.execute("""
                CREATE TEMP TABLE _tmp_room_slots (
                    slot_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    storey TEXT, room_name TEXT, bom_line_role TEXT,
                    before_x_mm REAL, before_y_mm REAL, before_z_mm REAL,
                    next_x_mm REAL, next_y_mm REAL, next_z_mm REAL
                )
                """);
        }

        // Populate from in-memory list
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO _tmp_room_slots (storey, room_name, bom_line_role, "
                + "before_x_mm, before_y_mm, before_z_mm, next_x_mm, next_y_mm, next_z_mm) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            for (RoomSlot slot : roomSlots) {
                ps.setString(1, slot.storey());
                ps.setString(2, slot.roomName());
                ps.setString(3, slot.bomLineRole());
                ps.setDouble(4, slot.beforeXMm());
                ps.setDouble(5, slot.beforeYMm());
                ps.setDouble(6, slot.beforeZMm());
                ps.setDouble(7, slot.nextXMm());
                ps.setDouble(8, slot.nextYMm());
                ps.setDouble(9, slot.nextZMm());
                ps.addBatch();
            }
            ps.executeBatch();
        }

        // Room-level containment — smallest-AABB-wins (same logic as before)
        int roomContained;
        try (Statement stmt = conn.createStatement()) {
            roomContained = stmt.executeUpdate("""
                INSERT OR REPLACE INTO rel_contained_in_space (element_guid, space_guid)
                SELECT element_guid, space_guid
                FROM (
                    SELECT em.guid AS element_guid,
                           'SPACE_' || REPLACE(UPPER(rs.storey),' ','_')
                             || '_' || REPLACE(UPPER(COALESCE(rs.room_name, rs.bom_line_role)),' ','_') AS space_guid,
                           ROW_NUMBER() OVER (
                               PARTITION BY em.guid
                               ORDER BY (rs.next_x_mm - rs.before_x_mm) * (rs.next_y_mm - rs.before_y_mm) ASC,
                                        rs.slot_id ASC
                           ) AS rn
                    FROM elements_meta em
                    JOIN elements_rtree er ON em.id = er.id
                    JOIN _tmp_room_slots rs
                      ON rs.storey IS NOT NULL
                      AND (rs.room_name IS NOT NULL OR rs.bom_line_role IS NOT NULL)
                    JOIN spatial_structure ss
                      ON ss.guid = 'SPACE_' || REPLACE(UPPER(rs.storey),' ','_')
                                     || '_' || REPLACE(UPPER(COALESCE(rs.room_name, rs.bom_line_role)),' ','_')
                    WHERE ((er.minX + er.maxX) / 2.0) * 1000.0 BETWEEN rs.before_x_mm AND rs.next_x_mm
                      AND ((er.minY + er.maxY) / 2.0) * 1000.0 BETWEEN rs.before_y_mm AND rs.next_y_mm
                      AND ((er.minZ + er.maxZ) / 2.0) * 1000.0 BETWEEN rs.before_z_mm AND rs.next_z_mm
                ) WHERE rn = 1
                """);
        }

        // Clean up temp table
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS _tmp_room_slots");
        }

        return roomContained;
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
