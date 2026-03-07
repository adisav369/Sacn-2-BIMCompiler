package com.bim.compiler.dsl;

import com.bim.ormsandbox.po.M_AdBuildingGrid;
import com.bim.ormsandbox.po.M_AdWallFace;
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
 * <p>TRAP: c_orderline.family_ref is NOT an FK to ad_opening_family.
 * Do NOT validate it — 1,149 false violations.
 */
public class MetadataValidator implements CompilerStage {

    private static final String DB_PATH = "library/BOM.db";

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
                checkPositiveDimensions(conn, errors);
            }
            try (Connection libConn = DriverManager.getConnection("jdbc:sqlite:" + CompilerConfig.LIBRARY_DB_PATH)) {
                checkGeometryHashes(libConn, errors);
            }
            if (errors.isEmpty()) {
                globalChecked = true;
                System.out.println("[PASS] Global metadata integrity (BOM, geometry, dimensions)");
            }
        }

        // --- Per-building checks ---
        // entry.id() = building_id from registry, which matches ad_building.building_type
        String buildingType = ctx.entry().id();
        String docSubType = ctx.entry().docSubType();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH)) {
            checkBuildingTypeExists(conn, buildingType, errors);
            checkWallFaceRefs(conn, buildingType, errors);
            checkRoomBoundaryRefs(conn, buildingType, errors);
            checkFamilyRefMandatory(conn, buildingType, errors);
            checkNoRoomLevelAbsoluteFurniture(conn, buildingType, errors);
            checkRelationalCompleteness(conn, buildingType, errors);

            // NO FALLBACK: every BUY leaf product must have library geometry
            try (Connection libConn = DriverManager.getConnection("jdbc:sqlite:" + CompilerConfig.LIBRARY_DB_PATH)) {
                checkBomLeafGeometry(conn, libConn, docSubType, errors);
            }
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
            "SELECT COUNT(*) FROM m_bom_line bc " +
            "LEFT JOIN m_bom b ON bc.bom_id = b.bom_id " +
            "WHERE b.bom_id IS NULL AND bc.is_active = 1");
        if (bomD > 0) errors.add("m_bom_line.bom_id: " + bomD + " dangling refs to m_bom");

        int paramD = queryInt(conn,
            "SELECT COUNT(*) FROM m_attribute bcp " +
            "LEFT JOIN m_bom_line bc ON bcp.bom_child_id = bc.bom_child_id " +
            "WHERE bc.bom_child_id IS NULL AND bcp.is_active = 1");
        if (paramD > 0) errors.add("m_attribute.bom_child_id: " + paramD + " dangling refs to m_bom_line");
    }

    private void checkGeometryHashes(Connection conn, List<String> errors) throws SQLException {
        int dangles = queryInt(conn,
            "SELECT COUNT(*) FROM I_Geometry_Map gm " +
            "LEFT JOIN component_geometries cg ON gm.geometry_hash = cg.geometry_hash " +
            "WHERE cg.geometry_hash IS NULL");
        if (dangles > 0) errors.add("I_Geometry_Map.geometry_hash: " + dangles + " dangling refs to component_geometries");
    }

    private void checkPositiveDimensions(Connection conn, List<String> errors) throws SQLException {
        int prodDim = queryInt(conn,
            "SELECT COUNT(*) FROM M_Product " +
            "WHERE (width <= 0 OR depth <= 0 OR height <= 0) AND is_active = 1");
        if (prodDim > 0) errors.add("M_Product: " + prodDim + " active rows with non-positive dimensions");

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
        // §11.9: position_rule, host_type DROPPED from c_orderline.
        // This validation moves to PP_Order_Node when placement data is migrated there.
        // Phase F: Re-implement against PP_Order_Node.
    }

    private void checkFamilyRefMandatory(Connection conn, String buildingType, List<String> errors) throws SQLException {
        // c_orderline DROPPED from BOM.db (redundant — data in M_BOM + M_Product).
        // This check validates M_Product_ID linkage, which now lives in m_bom_line.child_product_id.
        // Phase F: Re-implement against m_bom_line.child_product_id → M_Product chain.
    }

    private void checkRelationalCompleteness(Connection conn, String buildingType, List<String> errors) throws SQLException {
        // c_orderline DROPPED from BOM.db (redundant — data in M_BOM + M_Product).
        // Relational completeness now checked via ad_building config tables only.
        if (M_AdBuildingGrid.getByBuilding(conn, buildingType).isEmpty())
            errors.add(buildingType + ": no ad_building_grid rows");

        int rooms = queryIntParam(conn,
            "SELECT COUNT(*) FROM ad_room_boundary WHERE building_type = ? AND is_active = 1",
            buildingType);
        if (rooms == 0) errors.add(buildingType + ": no ad_room_boundary rows");

        if (M_AdWallFace.getByBuilding(conn, buildingType).isEmpty())
            errors.add(buildingType + ": no ad_wall_face rows");
    }

    /**
     * NO FALLBACK gate: every BUY leaf product in the active BOM must have
     * a matching M_Product_Image row (in component_library.db) whose
     * geometry_hash resolves to a real LOD_Object mesh.
     *
     * Runs per-building (scoped by doc_sub_type). Catches missing library
     * geometry BEFORE compilation starts — not at emission time.
     */
    private void checkBomLeafGeometry(Connection bomConn, Connection libConn,
                                       String docSubType, List<String> errors) throws SQLException {
        // Find all distinct BUY products in EXTRACTED BOMs for this building.
        // Template/structured BOMs (NULL doc_sub_type, non-EXTRACTED) define
        // future patterns — they're validated when actually compiled, not here.
        String sql = """
            SELECT DISTINCT bl.child_product_id
            FROM m_bom_line bl
            JOIN m_bom b ON bl.bom_id = b.bom_id
            WHERE bl.is_active = 1
              AND b.is_active = 1
              AND bl.component_type = 'BUY'
              AND b.doc_sub_type = ?
              AND b.bom_category = 'EXTRACTED'
            """;

        List<String> missingImage = new ArrayList<>();
        List<String> missingMesh = new ArrayList<>();

        try (PreparedStatement ps = bomConn.prepareStatement(sql)) {
            ps.setString(1, docSubType);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String productId = rs.getString(1);
                    if (productId == null) continue;

                    // Check M_Product_Image exists
                    String geoHash = queryStringParam(libConn,
                        "SELECT geometry_hash FROM M_Product_Image WHERE M_Product_ID = ?",
                        productId);

                    if (geoHash == null) {
                        missingImage.add(productId);
                        continue;
                    }

                    // Check LOD_Object mesh exists for that hash
                    int meshExists = queryIntParam(libConn,
                        "SELECT COUNT(*) FROM LOD_Object WHERE geometry_hash = ?",
                        geoHash);

                    if (meshExists == 0) {
                        missingMesh.add(productId + " (hash=" + geoHash + ")");
                    }
                }
            }
        }

        if (!missingImage.isEmpty()) {
            errors.add("NO FALLBACK: " + missingImage.size()
                + " BUY product(s) missing M_Product_Image: " + missingImage);
        }
        if (!missingMesh.isEmpty()) {
            errors.add("NO FALLBACK: " + missingMesh.size()
                + " BUY product(s) with M_Product_Image but no LOD_Object mesh: " + missingMesh);
        }

        int total = missingImage.size() + missingMesh.size();
        if (total == 0) {
            System.out.printf("[PASS] BOM leaf geometry: all BUY products have library meshes (%s)%n", docSubType);
        } else {
            System.out.printf("[FAIL] BOM leaf geometry: %d product(s) missing library geometry (%s)%n", total, docSubType);
        }
    }

    private String queryStringParam(Connection conn, String sql, String param) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
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
