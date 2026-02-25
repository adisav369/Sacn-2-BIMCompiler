package com.bim.compiler.contract;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Metadata integrity tests — Layer 2 of the enforcement stack.
 *
 * Validates referential integrity across ad_* tables at build time.
 * Every test runs a LEFT JOIN ... WHERE target IS NULL query and asserts zero dangles.
 *
 * <p>TRAP: ad_element_rule.family_ref is NOT an FK to ad_opening_family.
 * It stores general component names (furniture, walls, floors, beams).
 * Only door/window entries happen to match. Do NOT validate as FK — 1,149 false violations.
 */
@DisplayName("Metadata Integrity — referential integrity of ad_* tables")
public class MetadataIntegrityTest {

    private static final String DB_PATH = "library/component_library.db";
    private static Connection conn;

    @BeforeAll
    static void openConnection() throws SQLException {
        conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
    }

    @AfterAll
    static void closeConnection() throws SQLException {
        if (conn != null) conn.close();
    }

    private int countDangling(String sql) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private String listDangling(String sql) throws SQLException {
        StringBuilder sb = new StringBuilder();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(rs.getString(1));
            }
        }
        return sb.toString();
    }

    // =========================================================================
    // M1: Core lookup tables have rows
    // =========================================================================

    @Test
    @DisplayName("M1: Core lookup tables have rows")
    void coreTables_haveRows() throws SQLException {
        for (String table : new String[]{
                "ad_building", "ad_wall_type", "ad_space_type",
                "ad_opening_family", "ad_building_registry"}) {
            int count = countDangling("SELECT COUNT(*) FROM " + table);
            assertTrue(count > 0, table + " must have at least one row, found " + count);
        }
    }

    // =========================================================================
    // M2: Building-scoped tables reference valid building_type
    // =========================================================================

    @Test
    @DisplayName("M2: Building-scoped tables reference valid building_type")
    void buildingScopedTables_validBuildingType() throws SQLException {
        String[] tables = {
            "ad_element_rule", "ad_wall_face", "ad_room_boundary",
            "ad_building_grid", "ad_element_dependency"
        };
        for (String table : tables) {
            int dangles = countDangling(
                "SELECT COUNT(DISTINCT t.building_type) FROM " + table + " t " +
                "LEFT JOIN ad_building b ON t.building_type = b.building_type " +
                "WHERE b.building_type IS NULL AND t.is_active = 1");
            assertEquals(0, dangles,
                table + ".building_type has " + dangles + " values not in ad_building");
        }
    }

    // =========================================================================
    // M3: Wall faces reference valid wall types
    // =========================================================================

    @Test
    @DisplayName("M3: Wall faces reference valid wall types")
    void wallFaces_validWallTypes() throws SQLException {
        int dangles = countDangling(
            "SELECT COUNT(*) FROM ad_wall_face wf " +
            "LEFT JOIN ad_wall_type wt ON wf.wall_type_id = wt.wall_type_id " +
            "WHERE wt.wall_type_id IS NULL AND wf.is_active = 1");
        if (dangles > 0) {
            String vals = listDangling(
                "SELECT DISTINCT wf.wall_type_id FROM ad_wall_face wf " +
                "LEFT JOIN ad_wall_type wt ON wf.wall_type_id = wt.wall_type_id " +
                "WHERE wt.wall_type_id IS NULL AND wf.is_active = 1");
            fail("ad_wall_face.wall_type_id has " + dangles + " dangling rows: " + vals);
        }
    }

    // =========================================================================
    // M4: Room boundaries reference valid space types
    // =========================================================================

    @Test
    @DisplayName("M4: Room boundaries reference valid space types")
    void roomBoundaries_validSpaceTypes() throws SQLException {
        int dangles = countDangling(
            "SELECT COUNT(*) FROM ad_room_boundary rb " +
            "LEFT JOIN ad_space_type st ON rb.room_type = st.space_type_id " +
            "WHERE st.space_type_id IS NULL AND rb.is_active = 1");
        if (dangles > 0) {
            String vals = listDangling(
                "SELECT DISTINCT rb.room_type FROM ad_room_boundary rb " +
                "LEFT JOIN ad_space_type st ON rb.room_type = st.space_type_id " +
                "WHERE st.space_type_id IS NULL AND rb.is_active = 1");
            fail("ad_room_boundary.room_type has " + dangles + " dangling rows: " + vals);
        }
    }

    // =========================================================================
    // M5: Space type openings reference valid types and families
    // =========================================================================

    @Test
    @DisplayName("M5: Space type openings reference valid types and families")
    void spaceTypeOpenings_validReferences() throws SQLException {
        // space_type_id → ad_space_type
        int typeD = countDangling(
            "SELECT COUNT(*) FROM ad_space_type_opening sto " +
            "LEFT JOIN ad_space_type st ON sto.space_type_id = st.space_type_id " +
            "WHERE st.space_type_id IS NULL");
        if (typeD > 0) {
            String vals = listDangling(
                "SELECT DISTINCT sto.space_type_id FROM ad_space_type_opening sto " +
                "LEFT JOIN ad_space_type st ON sto.space_type_id = st.space_type_id " +
                "WHERE st.space_type_id IS NULL");
            fail("ad_space_type_opening.space_type_id has " + typeD + " dangling rows: " + vals);
        }

        // family_id → ad_opening_family
        int famD = countDangling(
            "SELECT COUNT(*) FROM ad_space_type_opening sto " +
            "LEFT JOIN ad_opening_family of2 ON sto.family_id = of2.family_id " +
            "WHERE of2.family_id IS NULL");
        if (famD > 0) {
            String vals = listDangling(
                "SELECT DISTINCT sto.family_id FROM ad_space_type_opening sto " +
                "LEFT JOIN ad_opening_family of2 ON sto.family_id = of2.family_id " +
                "WHERE of2.family_id IS NULL");
            fail("ad_space_type_opening.family_id has " + famD + " dangling rows: " + vals);
        }
    }

    // =========================================================================
    // M6: BOM chain has no dangling references
    // =========================================================================

    @Test
    @DisplayName("M6: BOM chain has no dangling references")
    void bomChain_noDanglingRefs() throws SQLException {
        // m_bom_line.bom_id → m_bom
        int bomD = countDangling(
            "SELECT COUNT(*) FROM m_bom_line bc " +
            "LEFT JOIN m_bom b ON bc.bom_id = b.bom_id " +
            "WHERE b.bom_id IS NULL AND bc.is_active = 1");
        assertEquals(0, bomD,
            "m_bom_line.bom_id has " + bomD + " dangling rows not in m_bom");

        // m_attribute.bom_child_id → m_bom_line
        int paramD = countDangling(
            "SELECT COUNT(*) FROM m_attribute bcp " +
            "LEFT JOIN m_bom_line bc ON bcp.bom_child_id = bc.bom_child_id " +
            "WHERE bc.bom_child_id IS NULL AND bcp.is_active = 1");
        assertEquals(0, paramD,
            "m_attribute.bom_child_id has " + paramD + " dangling rows not in m_bom_line");
    }

    // =========================================================================
    // M7: Check thresholds reference valid checks
    // =========================================================================

    @Test
    @DisplayName("M7: Check thresholds reference valid checks")
    void checkThresholds_validChecks() throws SQLException {
        int dangles = countDangling(
            "SELECT COUNT(*) FROM ad_check_threshold ct " +
            "LEFT JOIN ad_check_applicability ca ON ct.check_id = ca.check_id " +
            "WHERE ca.check_id IS NULL AND ct.is_active = 1");
        if (dangles > 0) {
            String vals = listDangling(
                "SELECT DISTINCT ct.check_id FROM ad_check_threshold ct " +
                "LEFT JOIN ad_check_applicability ca ON ct.check_id = ca.check_id " +
                "WHERE ca.check_id IS NULL AND ct.is_active = 1");
            fail("ad_check_threshold.check_id has " + dangles + " dangling rows: " + vals);
        }
    }

    // =========================================================================
    // M8: Geometry map references valid hashes
    // =========================================================================

    @Test
    @DisplayName("M8: Geometry map references valid hashes")
    void geometryMap_validHashes() throws SQLException {
        int dangles = countDangling(
            "SELECT COUNT(*) FROM ad_geometry_map gm " +
            "LEFT JOIN component_geometries cg ON gm.geometry_hash = cg.geometry_hash " +
            "WHERE cg.geometry_hash IS NULL");
        assertEquals(0, dangles,
            "ad_geometry_map.geometry_hash has " + dangles + " dangling rows not in component_geometries");
    }

    // =========================================================================
    // M9: All dimension values are positive
    // =========================================================================

    @Test
    @DisplayName("M9: All dimension values are positive")
    void dimensionValues_positive() throws SQLException {
        // ad_wall_type.total_mm > 0
        int wallDim = countDangling(
            "SELECT COUNT(*) FROM ad_wall_type WHERE total_mm <= 0 AND is_active = 1 " +
            "AND wall_type_id != 'NONE'");
        assertEquals(0, wallDim,
            "ad_wall_type has " + wallDim + " active rows with total_mm <= 0");

        // ad_product_dim width/depth/height > 0
        int prodDim = countDangling(
            "SELECT COUNT(*) FROM ad_product_dim " +
            "WHERE (width <= 0 OR depth <= 0 OR height <= 0) AND is_active = 1");
        assertEquals(0, prodDim,
            "ad_product_dim has " + prodDim + " active rows with non-positive dimensions");

        // ad_opening_family default dims > 0
        int openDim = countDangling(
            "SELECT COUNT(*) FROM ad_opening_family " +
            "WHERE (default_width_mm <= 0 OR default_height_mm <= 0) AND is_active = 1");
        assertEquals(0, openDim,
            "ad_opening_family has " + openDim + " active rows with non-positive default dims");
    }

    // =========================================================================
    // M10: Building registry entries map to ad_building
    // =========================================================================

    @Test
    @DisplayName("M10: Building registry entries map to ad_building")
    void buildingRegistry_validBuildingTypes() throws SQLException {
        int dangles = countDangling(
            "SELECT COUNT(*) FROM ad_building_registry br " +
            "LEFT JOIN ad_building b ON br.building_id = b.building_type " +
            "WHERE b.building_type IS NULL AND br.is_active = 1");
        assertEquals(0, dangles,
            "ad_building_registry.building_type has " + dangles + " values not in ad_building");
    }

    // =========================================================================
    // M11: Buildings with element_rules have complete relational data
    // =========================================================================

    @Test
    @DisplayName("M11: Buildings with element_rules have complete relational data")
    void buildingsWithRules_haveCompleteData() throws SQLException {
        // Get buildings that have element rules
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT DISTINCT building_type FROM ad_element_rule WHERE is_active = 1")) {
            while (rs.next()) {
                String bt = rs.getString(1);
                int grids = countDangling(
                    "SELECT COUNT(*) FROM ad_building_grid WHERE building_type = '" + bt + "' AND is_active = 1");
                assertTrue(grids > 0,
                    bt + " has element_rules but no ad_building_grid rows");

                int rooms = countDangling(
                    "SELECT COUNT(*) FROM ad_room_boundary WHERE building_type = '" + bt + "' AND is_active = 1");
                assertTrue(rooms > 0,
                    bt + " has element_rules but no ad_room_boundary rows");

                int faces = countDangling(
                    "SELECT COUNT(*) FROM ad_wall_face WHERE building_type = '" + bt + "' AND is_active = 1");
                assertTrue(faces > 0,
                    bt + " has element_rules but no ad_wall_face rows");
            }
        }
    }

    // =========================================================================
    // M12: Type rules reference valid types
    // =========================================================================

    @Test
    @DisplayName("M12: Type rules reference valid types")
    void typeRules_validTypes() throws SQLException {
        // beam_type_rule → beam_type
        int beamD = countDangling(
            "SELECT COUNT(*) FROM ad_beam_type_rule btr " +
            "LEFT JOIN ad_beam_type bt ON btr.beam_type_id = bt.beam_type_id " +
            "WHERE bt.beam_type_id IS NULL AND btr.is_active = 1");
        assertEquals(0, beamD,
            "ad_beam_type_rule.beam_type_id has " + beamD + " dangling refs");

        // column_type_rule → column_type
        int colD = countDangling(
            "SELECT COUNT(*) FROM ad_column_type_rule ctr " +
            "LEFT JOIN ad_column_type ct ON ctr.column_type_id = ct.column_type_id " +
            "WHERE ct.column_type_id IS NULL AND ctr.is_active = 1");
        assertEquals(0, colD,
            "ad_column_type_rule.column_type_id has " + colD + " dangling refs");

        // wall_type_rule → wall_type
        int wallD = countDangling(
            "SELECT COUNT(*) FROM ad_wall_type_rule wtr " +
            "LEFT JOIN ad_wall_type wt ON wtr.wall_type_id = wt.wall_type_id " +
            "WHERE wt.wall_type_id IS NULL AND wtr.is_active = 1");
        assertEquals(0, wallD,
            "ad_wall_type_rule.wall_type_id has " + wallD + " dangling refs");

        // floor_type_rule → floor_type
        int floorD = countDangling(
            "SELECT COUNT(*) FROM ad_floor_type_rule ftr " +
            "LEFT JOIN ad_floor_type ft ON ftr.floor_type_id = ft.floor_type_id " +
            "WHERE ft.floor_type_id IS NULL AND ftr.is_active = 1");
        assertEquals(0, floorD,
            "ad_floor_type_rule.floor_type_id has " + floorD + " dangling refs");
    }

    // =========================================================================
    // M13: Space type satellite tables reference valid space types
    // =========================================================================

    @Test
    @DisplayName("M13: Space type satellite tables reference valid space types")
    void spaceTypeSatellites_validSpaceTypes() throws SQLException {
        String[][] satellites = {
            {"ad_space_type_furniture", "space_type_id"},
            {"ad_space_type_mep", "space_type_id"},
            {"ad_space_type_mep_bom", "space_type_id"},
            {"ad_space_exterior_rule", "space_type_id"},
            {"ad_space_dim", "space_type"},
            {"ad_space_type_alias", "space_type_id"},
        };
        for (String[] pair : satellites) {
            String table = pair[0];
            String col = pair[1];
            int dangles = countDangling(
                "SELECT COUNT(*) FROM " + table + " sat " +
                "LEFT JOIN ad_space_type st ON sat." + col + " = st.space_type_id " +
                "WHERE st.space_type_id IS NULL");
            assertEquals(0, dangles,
                table + "." + col + " has " + dangles + " values not in ad_space_type");
        }
    }

    // =========================================================================
    // M14: AD Events reactive layer schema tables exist
    // Implements AD_Events_Spatial_Rules.docx v1.1 §2.3, §3.2, §7.2
    // =========================================================================

    @Test
    @DisplayName("M14: AD Events schema tables exist — spatial_rule, callout_rule, pattern, typology")
    void adEventsSchema_tablesExist() throws SQLException {
        for (String table : new String[]{
                "ad_spatial_rule", "ad_callout_rule",
                "ad_spatial_pattern", "ad_typology_pattern"}) {
            int exists = countDangling(
                "SELECT COUNT(*) FROM sqlite_master " +
                "WHERE type='table' AND name='" + table + "'");
            assertEquals(1, exists, table + " must exist in component_library.db");
        }
    }
}
