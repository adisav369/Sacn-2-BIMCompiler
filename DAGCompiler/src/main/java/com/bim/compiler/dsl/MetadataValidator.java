package com.bim.compiler.dsl;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Pipeline stage 0 — validates metadata integrity before compilation starts.
 *
 * Two categories of checks:
 * <ul>
 *   <li><b>Global</b> — BOM chain, geometry hashes, positive dimensions.
 *       Cached after first execution (same library for all buildings).</li>
 *   <li><b>Per-building</b> — building_type exists, wall_face→wall_type,
 *       room_boundary→space_type, relational completeness.</li>
 * </ul>
 *
 * On failure: throws {@link IllegalStateException} with all errors listed.
 *
 * <p>TRAP: ad_element_rule.family_ref is NOT an FK to ad_opening_family.
 * Do NOT validate it — 1,149 false violations.
 */
public class MetadataValidator implements CompilerStage {

    private static final String DB_PATH = "library/component_library.db";

    /** Global checks are immutable for a given library — cache result. */
    private static volatile boolean globalChecked = false;

    @Override
    public String name() { return "METADATA VALIDATION"; }

    @Override
    public void execute(CompilationContext ctx) throws Exception {
        List<String> errors = new ArrayList<>();

        // --- Global checks (once) ---
        if (!globalChecked) {
            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH)) {
                checkBomChain(conn, errors);
                checkGeometryHashes(conn, errors);
                checkPositiveDimensions(conn, errors);
            }
            if (errors.isEmpty()) {
                globalChecked = true;
                System.out.println("[PASS] Global metadata integrity (BOM, geometry, dimensions)");
            }
        }

        // --- Per-building checks ---
        // entry.id() = building_id from registry, which matches ad_building.building_type
        String buildingType = ctx.entry().id();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH)) {
            checkBuildingTypeExists(conn, buildingType, errors);
            checkWallFaceRefs(conn, buildingType, errors);
            checkRoomBoundaryRefs(conn, buildingType, errors);
            checkFamilyRefMandatory(conn, buildingType, errors);
            checkNoRoomLevelAbsoluteFurniture(conn, buildingType, errors);
            checkRelationalCompleteness(conn, buildingType, errors);
        }

        if (!errors.isEmpty()) {
            StringBuilder msg = new StringBuilder("Metadata integrity failures:\n");
            for (String e : errors) {
                msg.append("  - ").append(e).append("\n");
            }
            throw new IllegalStateException(msg.toString());
        }

        System.out.printf("[PASS] Metadata integrity for %s%n", buildingType);
    }

    // =====================================================================
    // Global checks
    // =====================================================================

    private void checkBomChain(Connection conn, List<String> errors) throws SQLException {
        int bomD = queryInt(conn,
            "SELECT COUNT(*) FROM ad_bom_child bc " +
            "LEFT JOIN ad_bom b ON bc.bom_id = b.bom_id " +
            "WHERE b.bom_id IS NULL AND bc.is_active = 1");
        if (bomD > 0) errors.add("ad_bom_child.bom_id: " + bomD + " dangling refs to ad_bom");

        int paramD = queryInt(conn,
            "SELECT COUNT(*) FROM ad_bom_child_param bcp " +
            "LEFT JOIN ad_bom_child bc ON bcp.bom_child_id = bc.bom_child_id " +
            "WHERE bc.bom_child_id IS NULL AND bcp.is_active = 1");
        if (paramD > 0) errors.add("ad_bom_child_param.bom_child_id: " + paramD + " dangling refs to ad_bom_child");
    }

    private void checkGeometryHashes(Connection conn, List<String> errors) throws SQLException {
        int dangles = queryInt(conn,
            "SELECT COUNT(*) FROM ad_geometry_map gm " +
            "LEFT JOIN component_geometries cg ON gm.geometry_hash = cg.geometry_hash " +
            "WHERE cg.geometry_hash IS NULL");
        if (dangles > 0) errors.add("ad_geometry_map.geometry_hash: " + dangles + " dangling refs to component_geometries");
    }

    private void checkPositiveDimensions(Connection conn, List<String> errors) throws SQLException {
        int prodDim = queryInt(conn,
            "SELECT COUNT(*) FROM ad_product_dim " +
            "WHERE (width <= 0 OR depth <= 0 OR height <= 0) AND is_active = 1");
        if (prodDim > 0) errors.add("ad_product_dim: " + prodDim + " active rows with non-positive dimensions");

        int openDim = queryInt(conn,
            "SELECT COUNT(*) FROM ad_opening_family " +
            "WHERE (default_width_mm <= 0 OR default_height_mm <= 0) AND is_active = 1");
        if (openDim > 0) errors.add("ad_opening_family: " + openDim + " active rows with non-positive default dims");
    }

    // =====================================================================
    // Per-building checks
    // =====================================================================

    private void checkBuildingTypeExists(Connection conn, String buildingType, List<String> errors) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM ad_building WHERE building_type = ?")) {
            ps.setString(1, buildingType);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || rs.getInt(1) == 0) {
                    errors.add("building_type '" + buildingType + "' not found in ad_building");
                }
            }
        }
    }

    private void checkWallFaceRefs(Connection conn, String buildingType, List<String> errors) throws SQLException {
        int dangles = queryIntParam(conn,
            "SELECT COUNT(*) FROM ad_wall_face wf " +
            "LEFT JOIN ad_wall_type wt ON wf.wall_type_id = wt.wall_type_id " +
            "WHERE wt.wall_type_id IS NULL AND wf.is_active = 1 AND wf.building_type = ?",
            buildingType);
        if (dangles > 0) errors.add(buildingType + ": ad_wall_face.wall_type_id has " + dangles + " dangling refs");
    }

    private void checkRoomBoundaryRefs(Connection conn, String buildingType, List<String> errors) throws SQLException {
        int dangles = queryIntParam(conn,
            "SELECT COUNT(*) FROM ad_room_boundary rb " +
            "LEFT JOIN ad_space_type st ON rb.room_type = st.space_type_id " +
            "WHERE st.space_type_id IS NULL AND rb.is_active = 1 AND rb.building_type = ?",
            buildingType);
        if (dangles > 0) errors.add(buildingType + ": ad_room_boundary.room_type has " + dangles + " dangling refs");
    }

    private void checkNoRoomLevelAbsoluteFurniture(Connection conn, String buildingType, List<String> errors) throws SQLException {
        // Phase RM-11 Step 5: ABSOLUTE placement is forbidden when host_type='ROOM'
        // for IfcFurnishingElement/IfcFurniture — these must use FRACTION or BOM anchor.
        // ABSOLUTE + host_type='BUILDING' is allowed (legitimate world coords for extracted IFC).
        int roomAbsolute = queryIntParam(conn,
            "SELECT COUNT(*) FROM ad_element_rule " +
            "WHERE building_type = ? AND is_active = 1 " +
            "  AND ifc_class IN ('IfcFurnishingElement','IfcSanitaryTerminal','IfcFurniture') " +
            "  AND position_rule = 'ABSOLUTE' " +
            "  AND host_type = 'ROOM'",
            buildingType);
        if (roomAbsolute > 0)
            errors.add(buildingType + ": " + roomAbsolute
                + " furniture row(s) use ABSOLUTE with host_type=ROOM — must use FRACTION or BOM anchor");
    }

    private void checkFamilyRefMandatory(Connection conn, String buildingType, List<String> errors) throws SQLException {
        // Phase RM-11 Step 1: family_ref must be set AND exist in ad_product_dim
        // for fixture/furniture classes. Without it conn_points cannot be read → wrong rotation.
        int nullCount = queryIntParam(conn,
            "SELECT COUNT(*) FROM ad_element_rule " +
            "WHERE building_type = ? AND is_active = 1 " +
            "  AND ifc_class IN ('IfcFurnishingElement','IfcSanitaryTerminal','IfcFurniture') " +
            "  AND family_ref IS NULL",
            buildingType);
        if (nullCount > 0)
            errors.add(buildingType + ": " + nullCount
                + " fixture/furniture row(s) have null family_ref — must map to ad_product_dim");

        int dangles = queryIntParam(conn,
            "SELECT COUNT(*) FROM ad_element_rule er " +
            "LEFT JOIN ad_product_dim pd ON er.family_ref = pd.product_id " +
            "WHERE er.building_type = ? AND er.is_active = 1 " +
            "  AND er.ifc_class IN ('IfcFurnishingElement','IfcSanitaryTerminal','IfcFurniture') " +
            "  AND er.family_ref IS NOT NULL " +
            "  AND pd.product_id IS NULL",
            buildingType);
        if (dangles > 0)
            errors.add(buildingType + ": " + dangles
                + " fixture/furniture row(s) have family_ref not found in ad_product_dim");
    }

    private void checkRelationalCompleteness(Connection conn, String buildingType, List<String> errors) throws SQLException {
        int ruleCount = queryIntParam(conn,
            "SELECT COUNT(*) FROM ad_element_rule WHERE building_type = ? AND is_active = 1",
            buildingType);
        if (ruleCount == 0) return; // No relational rules — nothing to check

        int grids = queryIntParam(conn,
            "SELECT COUNT(*) FROM ad_building_grid WHERE building_type = ? AND is_active = 1",
            buildingType);
        if (grids == 0) errors.add(buildingType + ": has element_rules but no ad_building_grid rows");

        int rooms = queryIntParam(conn,
            "SELECT COUNT(*) FROM ad_room_boundary WHERE building_type = ? AND is_active = 1",
            buildingType);
        if (rooms == 0) errors.add(buildingType + ": has element_rules but no ad_room_boundary rows");

        int faces = queryIntParam(conn,
            "SELECT COUNT(*) FROM ad_wall_face WHERE building_type = ? AND is_active = 1",
            buildingType);
        if (faces == 0) errors.add(buildingType + ": has element_rules but no ad_wall_face rows");
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private int queryInt(Connection conn, String sql) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private int queryIntParam(Connection conn, String sql, String param) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /** Reset cache for testing. */
    static void resetCache() {
        globalChecked = false;
    }
}
