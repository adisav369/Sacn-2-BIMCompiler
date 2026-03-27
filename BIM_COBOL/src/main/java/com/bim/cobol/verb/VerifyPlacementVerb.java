package com.bim.cobol.verb;

import com.bim.cobol.Verb;
import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbResult;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * VERIFY PLACEMENT &lt;output_db_path&gt; — validates spatial completeness via M_BOM.
 *
 * @deprecated Retained for gate compatibility. Checks BOM hierarchy in BOM.db
 * instead of the removed co_empty_space_line table (removed S74 — W008).
 *
 * <p>Opens the given output.db to verify elements_meta and spatial_structure,
 * then queries {@code ctx.bomConn()} for BOM completeness:
 * <ul>
 *   <li><b>L0</b>: BUILDING BOM exists (m_bom WHERE bom_type = 'BUILDING', count &ge; 1)</li>
 *   <li><b>L1</b>: BUILDING BOM has floor children (m_bom_line WHERE bom_id = ?, count &ge; 3)</li>
 *   <li><b>L2</b>: Floor BOMs have room-category children
 *       (m_product_category_id IN ('LI','BD','KT','BT','DN'), count &ge; 1)</li>
 * </ul>
 *
 * <p>Read-only — does not write to any database.
 *
 * <p>Returns {@link PlacementVerifyPayload} with per-level counts and a list of gap descriptions.
 * Result is PASS if all checks pass; FAIL with gap descriptions otherwise.
 */
@Deprecated
public class VerifyPlacementVerb implements Verb<VerifyPlacementVerb.PlacementVerifyPayload> {

    private static final String[] ROOM_CATEGORIES = {"LI", "BD", "KT", "BT", "DN"};

    @Override
    public String keyword() { return "VERIFY PLACEMENT"; }

    @Override
    public VerbResult<PlacementVerifyPayload> execute(VerbContext ctx, String... args)
            throws SQLException {
        if (args.length == 0)
            return VerbResult.fail(keyword(),
                "usage: VERIFY PLACEMENT <output_db_path>", null);

        String dbPath = args[0];
        java.io.File dbFile = new java.io.File(dbPath);
        if (!dbFile.exists())
            return VerbResult.fail(keyword(),
                "output.db not found: " + dbPath,
                new PlacementVerifyPayload(0, 0, 0, List.of("file not found: " + dbPath)));

        List<String> gaps = new ArrayList<>();

        // ── Phase 1: output.db sanity checks ────────────────────────────────
        try (Connection outConn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            if (!tableExists(outConn, "elements_meta")) {
                gaps.add("elements_meta table missing in output.db");
            } else {
                int elemCount = scalarCount(outConn, "SELECT COUNT(*) FROM elements_meta");
                if (elemCount == 0) gaps.add("elements_meta is empty");
            }

            if (!tableExists(outConn, "spatial_structure")) {
                gaps.add("spatial_structure table missing in output.db");
            } else {
                int storeys = scalarCount(outConn,
                    "SELECT COUNT(*) FROM spatial_structure WHERE type = 'IfcBuildingStorey'");
                if (storeys == 0) gaps.add("no IfcBuildingStorey rows in spatial_structure");

                int spaces = scalarCount(outConn,
                    "SELECT COUNT(*) FROM spatial_structure WHERE type = 'IfcSpace'");
                if (spaces == 0) gaps.add("no IfcSpace rows in spatial_structure");
            }
        }

        // ── Phase 2: BOM.db hierarchy checks ────────────────────────────────
        Connection bomConn = ctx.bomConn();
        if (bomConn == null) {
            gaps.add("bomConn is null — cannot verify BOM hierarchy");
            return VerbResult.fail(keyword(), "bomConn required",
                new PlacementVerifyPayload(0, 0, 0, gaps));
        }

        // Implementing DISC_VALIDATION_DB_SRS.md §10.4.5 — Witness: W-TREE-1
        // L0: Tree-root BOM exists (root = not a child of any other BOM)
        int l0 = scalarCount(bomConn,
            "SELECT COUNT(*) FROM m_bom WHERE bom_id NOT IN (SELECT child_product_id FROM m_bom_line WHERE is_active = 1)");

        // L1: Tree-root BOM children (floor lines)
        int l1 = 0;
        List<Integer> buildingBomIds = new ArrayList<>();
        try (PreparedStatement ps = bomConn.prepareStatement(
                "SELECT m_bom_id FROM m_bom WHERE bom_id NOT IN (SELECT child_product_id FROM m_bom_line WHERE is_active = 1)")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) buildingBomIds.add(rs.getInt(1));
            }
        }
        for (int bomId : buildingBomIds) {
            l1 += countLinesByBom(bomConn, bomId);
        }

        // L2: room-category children of floor BOMs
        int l2 = countRoomCategoryChildren(bomConn, buildingBomIds);

        if (l0 < 1) gaps.add("L0 (BUILDING BOM): expected ≥1, got " + l0);
        if (l1 < 3) gaps.add("L1 (floor children): expected ≥3, got " + l1);
        if (l2 < 1) gaps.add("L2 (room-category children): expected ≥1, got " + l2);

        PlacementVerifyPayload payload = new PlacementVerifyPayload(l0, l1, l2, gaps);

        if (gaps.isEmpty()) {
            return VerbResult.ok(keyword(),
                String.format("PASS — L0=%d, L1=%d, L2=%d, no gaps", l0, l1, l2),
                payload);
        } else {
            return VerbResult.fail(keyword(),
                String.format("FAIL — L0=%d, L1=%d, L2=%d, gaps=%d", l0, l1, l2, gaps.size()),
                payload);
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static boolean tableExists(Connection conn, String tableName) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        try (ResultSet rs = meta.getTables(null, null, tableName, new String[]{"TABLE"})) {
            return rs.next();
        }
    }

    private static int scalarCount(Connection conn, String sql) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private static int countLinesByBom(Connection conn, int bomId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM m_bom_line WHERE m_bom_id = ?")) {
            ps.setInt(1, bomId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /**
     * For each BUILDING BOM, find its child lines (floors), then for each floor's
     * child BOM, count lines whose m_product_category_id is a room category.
     */
    private static int countRoomCategoryChildren(Connection conn, List<Integer> buildingBomIds)
            throws SQLException {
        if (buildingBomIds.isEmpty()) return 0;

        int total = 0;
        // Gather floor-level child BOM IDs from the building lines
        List<Integer> floorBomIds = new ArrayList<>();
        for (int buildingBomId : buildingBomIds) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT child_bom_id FROM m_bom_line WHERE m_bom_id = ? AND child_bom_id IS NOT NULL")) {
                ps.setInt(1, buildingBomId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) floorBomIds.add(rs.getInt(1));
                }
            }
        }

        // For each floor BOM, count room-category children
        String inClause = "'" + String.join("','", ROOM_CATEGORIES) + "'";
        for (int floorBomId : floorBomIds) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM m_bom_line WHERE m_bom_id = ? AND m_product_category_id IN ("
                    + inClause + ")")) {
                ps.setInt(1, floorBomId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) total += rs.getInt(1);
                }
            }
        }
        return total;
    }

    /**
     * Payload carrying placement verification results.
     *
     * @param l0Count  number of BUILDING BOMs
     * @param l1Count  number of floor children across all BUILDING BOMs
     * @param l2Count  number of room-category children across all floor BOMs
     * @param gaps     list of gap descriptions (empty = all checks passed)
     */
    public record PlacementVerifyPayload(
            int l0Count,
            int l1Count,
            int l2Count,
            List<String> gaps
    ) {
        public boolean hasGaps() { return !gaps.isEmpty(); }
    }
}
