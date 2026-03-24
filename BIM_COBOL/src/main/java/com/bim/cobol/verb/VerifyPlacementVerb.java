package com.bim.cobol.verb;

import com.bim.cobol.Verb;
import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbResult;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * VERIFY PLACEMENT &lt;output_db_path&gt; — validates spatial slot completeness.
 *
 * @deprecated Reads compiler-internal co_empty_space_line table. WHERE concern
 * lives in M_BOM_Line dx/dy/dz (MANIFESTO.md §Three Concerns).
 *
 * <p>Opens the given output.db, queries {@code co_empty_space_line} by {@code bom_level},
 * and checks that the three structural levels are populated:
 * <ul>
 *   <li><b>L0</b> (bom_level=0): UNIT acceptance — must have ≥1 row</li>
 *   <li><b>L1</b> (bom_level=1): Storey decomposition — must have ≥3 rows
 *       (at minimum: ground slab + ground floor + roof)</li>
 *   <li><b>L2</b> (bom_level=2): Room-level assignment — must have ≥1 row</li>
 *   <li>No L2 rows may have a null {@code bom_id}</li>
 * </ul>
 *
 * <p>Read-only — does not write to the database.
 *
 * <p>Returns {@link PlacementVerifyPayload} with per-level counts and a list of gap descriptions.
 * Result is PASS if all checks pass; FAIL with gap descriptions otherwise.
 */
@Deprecated
public class VerifyPlacementVerb implements Verb<VerifyPlacementVerb.PlacementVerifyPayload> {

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

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            // Check that co_empty_space_line table exists
            if (!tableExists(conn, "co_empty_space_line")) {
                List<String> gaps = List.of("table co_empty_space_line not found in " + dbPath);
                return VerbResult.fail(keyword(), "co_empty_space_line missing",
                    new PlacementVerifyPayload(0, 0, 0, gaps));
            }

            // Count rows per bom_level
            int l0 = countByLevel(conn, 0);
            int l1 = countByLevel(conn, 1);
            int l2 = countByLevel(conn, 2);

            List<String> gaps = new ArrayList<>();

            if (l0 < 1) gaps.add("L0 (UNIT acceptance): expected ≥1, got " + l0);
            if (l1 < 3) gaps.add("L1 (storey decomposition): expected ≥3, got " + l1);
            if (l2 < 1) gaps.add("L2 (room assignment): expected ≥1, got " + l2);

            // Check for null bom_id in L2
            int nullBomIds = countNullBomIds(conn);
            if (nullBomIds > 0) {
                gaps.add("L2 null bom_id: " + nullBomIds + " ESLine(s) have no BOM reference");
            }

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
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static boolean tableExists(Connection conn, String tableName) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        try (ResultSet rs = meta.getTables(null, null, tableName, new String[]{"TABLE"})) {
            return rs.next();
        }
    }

    private static int countByLevel(Connection conn, int level) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM co_empty_space_line WHERE bom_level = ?")) {
            ps.setInt(1, level);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private static int countNullBomIds(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT COUNT(*) FROM co_empty_space_line WHERE bom_level = 2 AND bom_id IS NULL")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /**
     * Payload carrying placement verification results.
     *
     * @param l0Count  number of L0 (UNIT acceptance) ESLines
     * @param l1Count  number of L1 (storey) ESLines
     * @param l2Count  number of L2 (room) ESLines
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
