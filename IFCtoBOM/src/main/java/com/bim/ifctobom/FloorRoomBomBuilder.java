package com.bim.ifctobom;

import com.bim.ifctobom.ClassificationYaml.BuildingConfig;
import com.bim.ifctobom.ClassificationYaml.FloorRoomConfig;
import com.bim.ifctobom.ClassificationYaml.SpaceConfig;
import com.bim.ifctobom.ClassificationYaml.StaticChildConfig;

import java.sql.*;
import java.util.Map;

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
     * @param bomConn          writable connection to output BOM DB
     * @param config           building classification
     * @param floorLbdWorld    floor world LBD positions: storeyName → [worldX,worldY,worldZ] in metres
     * @param setLbdPositions  SET BOM world LBD positions: templateBom → [minX,minY,minZ] in metres
     * @param bldgMinX         building world LBD X (metres)
     * @param bldgMinY         building world LBD Y (metres)
     * @param bldgMinZ         building world LBD Z (metres)
     * @return number of BOM lines created
     */
    public static int build(Connection bomConn, BuildingConfig config,
                            Map<String, double[]> floorLbdWorld,
                            Map<String, double[]> setLbdPositions,
                            double bldgMinX, double bldgMinY, double bldgMinZ,
                            CategoryLookup catLookup) throws SQLException {
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
                    "FLOOR", "ROOM", fr.productCategory(), catLookup);

            // Floor world LBD for computing SET-in-FLOOR offsets
            double[] floorLbd = floorLbdWorld.get(storeyName);

            // Insert space children referencing template BOMs
            // dx/dy/dz = SET BOM LBD position relative to FLOOR LBD (§4 tack convention)
            for (SpaceConfig space : fr.spaces()) {
                double[] setLbd = setLbdPositions.get(space.templateBom());
                double spaceDx = 0, spaceDy = 0, spaceDz = 0;
                if (setLbd != null && floorLbd != null) {
                    spaceDx = setLbd[0] - floorLbd[0];
                    spaceDy = setLbd[1] - floorLbd[1];
                    spaceDz = setLbd[2] - floorLbd[2];
                }
                insertSpaceLine(bomConn, floorBomId, space, spaceDx, spaceDy, spaceDz);
                lineCount++;
            }

            // Add this floor room BOM as LEAF child of BUILDING BOM
            // dx/dy/dz = FLOOR LBD position relative to BUILDING LBD (§4 tack convention)
            var storey = config.storeys().get(storeyName);
            if (storey != null) {
                double bldgDx = 0, bldgDy = 0, bldgDz = 0;
                if (floorLbd != null) {
                    // BUILDING→FLOOR offset = floor world LBD - building world LBD (§4)
                    bldgDx = floorLbd[0] - bldgMinX;
                    bldgDy = floorLbd[1] - bldgMinY;
                    bldgDz = floorLbd[2] - bldgMinZ;
                }
                insertBuildingChild(bomConn, buildingBomId, floorBomId,
                        "LEAF", "ROOM_" + storey.code(), storey.seq() + 5,
                        bldgDx, bldgDy, bldgDz);
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

    // Implementing BBC.md §4.2 — Witness: W-AABB-QUAL-1
    private static void insertBomHeader(Connection conn, String bomId, String bomName,
                                        String bomType, String groupBy, String productCategory,
                                        CategoryLookup catLookup)
            throws SQLException {
        // FLOOR ROOM BOMs contain YAML-sourced room dimensions → INNER qualifier.
        // The room's AABB is the architect's intended clear volume (finish-to-finish).
        String sql = """
                INSERT OR REPLACE INTO m_bom
                (bom_id, Value, bom_name, bom_type, group_by, m_product_category_id,
                 entity_type, origin_x, origin_y, origin_z,
                 aabb_width_mm, aabb_depth_mm, aabb_height_mm, aabb_qualifier, is_active)
                VALUES (?, ?, ?, ?, ?, ?, 'D', 0.0, 0.0, 0.0, 0, 0, 0, 'INNER', 1)
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, bomId);
            stmt.setString(2, bomId);  // Value = bom_id
            stmt.setString(3, bomName);
            stmt.setString(4, bomType);
            stmt.setString(5, groupBy);
            int catId = catLookup.getId(productCategory);
            if (catId > 0) stmt.setInt(6, catId);
            else stmt.setNull(6, java.sql.Types.INTEGER);
            stmt.executeUpdate();
        }
    }

    private static void insertSpaceLine(Connection conn, String bomId, SpaceConfig space,
                                        double dx, double dy, double dz)
            throws SQLException {
        // CP-4 §4a: compute geometric classification from space AABB
        String archetype = VerbFactorizer.classifyArchetype(space.aabbW(), space.aabbD(), space.aabbH());
        String scaleBand = VerbFactorizer.classifyScaleBand(space.aabbW(), space.aabbD(), space.aabbH());

        String sql = """
                INSERT INTO m_bom_line
                (bom_id, M_BOM_ID, child_product_id, component_type, role, sequence,
                 rotation_rule, fit_priority, min_space_mm,
                 dx, dy, dz, is_active, entity_type,
                 allocated_width_mm, allocated_depth_mm, allocated_height_mm,
                 shape_archetype, scale_band)
                VALUES (?, (SELECT M_BOM_ID FROM m_bom WHERE Value = ?), ?, 'LEAF', ?, ?,
                        '0', 20, 0,
                        ?, ?, ?, 1, 'D',
                        ?, ?, ?,
                        ?, ?)
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, bomId);
            stmt.setString(2, bomId);  // M_BOM_ID subquery
            stmt.setString(3, space.templateBom());
            stmt.setString(4, space.role());
            stmt.setInt(5, space.seq());
            stmt.setDouble(6, dx);
            stmt.setDouble(7, dy);
            stmt.setDouble(8, dz);
            stmt.setInt(9, space.aabbW());
            stmt.setInt(10, space.aabbD());
            stmt.setInt(11, space.aabbH());
            stmt.setString(12, archetype);
            stmt.setString(13, scaleBand);
            stmt.executeUpdate();
        }
    }

    private static void insertBuildingChild(Connection conn, String buildingBomId,
                                            String childId, String componentType,
                                            String role, int seq,
                                            double dx, double dy, double dz) throws SQLException {
        String sql = """
                INSERT INTO m_bom_line
                (bom_id, M_BOM_ID, child_product_id, component_type, role, sequence,
                 rotation_rule, fit_priority, min_space_mm,
                 dx, dy, dz, is_active, entity_type)
                VALUES (?, (SELECT M_BOM_ID FROM m_bom WHERE Value = ?), ?, ?, ?, ?,
                        '0', 20, 0,
                        ?, ?, ?, 1, 'D')
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, buildingBomId);
            stmt.setString(2, buildingBomId);  // M_BOM_ID subquery
            stmt.setString(3, childId);
            stmt.setString(4, componentType);
            stmt.setString(5, role);
            stmt.setInt(6, seq);
            stmt.setDouble(7, dx);
            stmt.setDouble(8, dy);
            stmt.setDouble(9, dz);
            stmt.executeUpdate();
        }
    }

    private static void insertStaticChild(Connection conn, String buildingBomId,
                                          StaticChildConfig sc) throws SQLException {
        String sql = """
                INSERT INTO m_bom_line
                (bom_id, M_BOM_ID, child_product_id, component_type, role, sequence,
                 rotation_rule, fit_priority, min_space_mm,
                 dx, dy, dz, is_active, entity_type)
                VALUES (?, (SELECT M_BOM_ID FROM m_bom WHERE Value = ?), ?, 'MAKE', ?, ?,
                        '0', 20, 0,
                        0.0, 0.0, ?, 1, 'D')
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, buildingBomId);
            stmt.setString(2, buildingBomId);  // M_BOM_ID subquery
            stmt.setString(3, sc.childProductId());
            stmt.setString(4, sc.role());
            stmt.setInt(5, sc.seq());
            stmt.setDouble(6, sc.dz());
            stmt.executeUpdate();
        }
    }
}
