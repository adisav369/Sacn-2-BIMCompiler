package com.bim.ifctobom;

import com.bim.ifctobom.ClassificationYaml.BuildingConfig;
import com.bim.ifctobom.ClassificationYaml.FloorRoomConfig;
import com.bim.ifctobom.ClassificationYaml.SpaceConfig;
import com.bim.ifctobom.ClassificationYaml.StaticChildConfig;

import java.sql.*;

/**
 * Creates FLOOR room BOMs from classification YAML.
 *
 * <p>For each {@code floor_rooms} entry: creates M_Product stub,
 * MBOM header, and MBOMLine children referencing template BOMs.
 * Also adds LEAF children for room assignments and static_children
 * to the BUILDING BOM.
 */
public class FloorRoomBomBuilder {

    /**
     * Build room BOMs and static children from classification YAML.
     *
     * @param bomConn writable connection to output BOM DB
     * @param config  building classification
     * @return number of BOM lines created
     */
    public static int build(Connection bomConn, BuildingConfig config) throws SQLException {
        String buildingBomId = config.buildingBomId();
        int lineCount = 0;

        // ── Floor room BOMs ──────────────────────────────────────────────────
        for (var floorEntry : config.floorRooms().entrySet()) {
            String storeyName = floorEntry.getKey();
            FloorRoomConfig fr = floorEntry.getValue();
            String floorBomId = fr.bomId();

            // Create M_Product assembly stub for this floor
            ProductRegistrar.ensureAssemblyStub(bomConn, floorBomId, "FLOOR");

            // Create FLOOR room BOM header
            insertBomHeader(bomConn, floorBomId,
                    config.prefix() + " " + storeyName + " Rooms",
                    "FLOOR", "ROOM", fr.bomCategory());

            // Insert space children referencing template BOMs
            for (SpaceConfig space : fr.spaces()) {
                insertSpaceLine(bomConn, floorBomId, space);
                lineCount++;
            }

            // Add this floor room BOM as LEAF child of BUILDING BOM
            // (structural floor is MAKE, room floor is LEAF — both live under BUILDING)
            var storey = config.storeys().get(storeyName);
            if (storey != null) {
                insertBuildingChild(bomConn, buildingBomId, floorBomId,
                        "LEAF", "ROOM_" + storey.code(), storey.seq() + 5);
                lineCount++;
            }
        }

        // ── Static children ──────────────────────────────────────────────────
        for (StaticChildConfig sc : config.staticChildren()) {
            // Ensure product stub exists
            ProductRegistrar.ensureAssemblyStub(bomConn, sc.childProductId(), "ASSEMBLY");

            insertStaticChild(bomConn, buildingBomId, sc);
            lineCount++;
        }

        return lineCount;
    }

    // ── SQL helpers ──────────────────────────────────────────────────────────

    private static void insertBomHeader(Connection conn, String bomId, String bomName,
                                        String bomType, String groupBy, String bomCategory)
            throws SQLException {
        String sql = """
                INSERT OR REPLACE INTO m_bom
                (bom_id, bom_name, bom_type, group_by, bom_category,
                 entity_type, origin_x, origin_y, origin_z,
                 aabb_width_mm, aabb_depth_mm, aabb_height_mm, is_active)
                VALUES (?, ?, ?, ?, ?, 'D', 0.0, 0.0, 0.0, 0, 0, 0, 1)
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, bomId);
            stmt.setString(2, bomName);
            stmt.setString(3, bomType);
            stmt.setString(4, groupBy);
            stmt.setString(5, bomCategory);
            stmt.executeUpdate();
        }
    }

    private static void insertSpaceLine(Connection conn, String bomId, SpaceConfig space)
            throws SQLException {
        String sql = """
                INSERT INTO m_bom_line
                (bom_id, child_product_id, component_type, role, sequence,
                 rotation_rule, fit_priority, min_space_mm,
                 dx, dy, dz, is_active, entity_type,
                 allocated_width_mm, allocated_depth_mm, allocated_height_mm)
                VALUES (?, ?, 'LEAF', ?, ?,
                        '0', 20, 0,
                        0.0, 0.0, 0.0, 1, 'D',
                        ?, ?, ?)
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, bomId);
            stmt.setString(2, space.templateBom());
            stmt.setString(3, space.role());
            stmt.setInt(4, space.seq());
            stmt.setInt(5, space.aabbW());
            stmt.setInt(6, space.aabbD());
            stmt.setInt(7, space.aabbH());
            stmt.executeUpdate();
        }
    }

    private static void insertBuildingChild(Connection conn, String buildingBomId,
                                            String childId, String componentType,
                                            String role, int seq) throws SQLException {
        String sql = """
                INSERT INTO m_bom_line
                (bom_id, child_product_id, component_type, role, sequence,
                 rotation_rule, fit_priority, min_space_mm,
                 dx, dy, dz, is_active, entity_type)
                VALUES (?, ?, ?, ?, ?,
                        '0', 20, 0,
                        0.0, 0.0, 0.0, 1, 'D')
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, buildingBomId);
            stmt.setString(2, childId);
            stmt.setString(3, componentType);
            stmt.setString(4, role);
            stmt.setInt(5, seq);
            stmt.executeUpdate();
        }
    }

    private static void insertStaticChild(Connection conn, String buildingBomId,
                                          StaticChildConfig sc) throws SQLException {
        String sql = """
                INSERT INTO m_bom_line
                (bom_id, child_product_id, component_type, role, sequence,
                 rotation_rule, fit_priority, min_space_mm,
                 dx, dy, dz, is_active, entity_type)
                VALUES (?, ?, 'MAKE', ?, ?,
                        '0', 20, 0,
                        0.0, 0.0, ?, 1, 'D')
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, buildingBomId);
            stmt.setString(2, sc.childProductId());
            stmt.setString(3, sc.role());
            stmt.setInt(4, sc.seq());
            stmt.setDouble(5, sc.dz());
            stmt.executeUpdate();
        }
    }
}
