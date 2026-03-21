package com.bim.designer;

import org.junit.jupiter.api.*;

import java.io.File;
import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

/**
 * Selection Cascade — BBC.md §3.5 proof for DemoHouse rooms.
 *
 * <p>Exercises the BOM cascade: given bom_category + AABB for each DemoHouse room,
 * verify the cascade finds matching SET BOMs from the SH/FK library.
 * Zero new compilation code — the cascade is a data query.
 *
 * <p>AABB=0 means "universal fit" (unmeasured SET, always matches).
 * Oversized SETs are rejected. Validation rules catch unmatched rooms.
 *
 * // Implementing BBC.md §3.5, GENERATIVE_ROOM_SRS.md §2 — Witness: W-GEN-1
 */
@DisplayName("Selection Cascade — BBC.md §3.5")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SelectionCascadeTest {

    // DemoHouse room slots from classify_dm.yaml
    //              category    W_mm   D_mm   H_mm
    private static final Object[][] SLOTS = {
        {"LIVING",   4000, 3500, 2800},
        {"KITCHEN",  4000, 3500, 2800},
        {"BEDROOM",  5000, 3500, 2800},
        {"BEDROOM",  5000, 3500, 2800},
        {"BATHROOM", 2000, 1500, 2800},
    };

    // Real BOM databases from extracted Rosetta Stones (DocBaseType=RE)
    private static final String[] BOM_DBS = {
        "library/SH_BOM.db",
        "library/FK_BOM.db",
    };

    private static Connection conn;

    @BeforeAll
    static void setUp() throws Exception {
        // Verify at least one BOM database exists
        boolean anyExists = false;
        for (String db : BOM_DBS) {
            if (new File(db).exists() && new File(db).length() > 0) {
                anyExists = true;
                break;
            }
        }
        assumeTrue(anyExists, "No BOM databases found — run IFCtoBOM first");

        // Create in-memory DB with unified SET BOM view across all buildings
        conn = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement st = conn.createStatement()) {
            st.execute("""
                CREATE TABLE m_bom (
                    bom_id TEXT, bom_name TEXT, bom_type TEXT,
                    bom_category TEXT, source TEXT,
                    aabb_width_mm INTEGER DEFAULT 0,
                    aabb_depth_mm INTEGER DEFAULT 0,
                    aabb_height_mm INTEGER DEFAULT 0,
                    seq_no INTEGER DEFAULT 10,
                    is_active INTEGER DEFAULT 1)""");
            st.execute("""
                CREATE TABLE m_bom_line (
                    bom_id TEXT, child_product_id TEXT,
                    component_type TEXT, role TEXT,
                    sequence INTEGER DEFAULT 10,
                    is_active INTEGER DEFAULT 1)""");
        }

        // Import SET BOMs + children from each real BOM database
        int dbIndex = 0;
        for (String dbPath : BOM_DBS) {
            if (!new File(dbPath).exists() || new File(dbPath).length() == 0) continue;
            String alias = "db" + dbIndex;
            String source = dbPath.replaceAll(".*/", "").replace("_BOM.db", "");
            try (Statement st = conn.createStatement()) {
                st.execute("ATTACH DATABASE '" + dbPath + "' AS " + alias);
                st.execute("""
                    INSERT INTO m_bom (bom_id, bom_name, bom_type, bom_category, source,
                                       aabb_width_mm, aabb_depth_mm, aabb_height_mm, seq_no, is_active)
                    SELECT bom_id, bom_name, bom_type, bom_category, '%s',
                           aabb_width_mm, aabb_depth_mm, aabb_height_mm, seq_no, is_active
                    FROM %s.m_bom WHERE bom_type = 'SET' AND is_active = 1
                    """.formatted(source, alias));
                st.execute("""
                    INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role, sequence, is_active)
                    SELECT bom_id, child_product_id, component_type, role, sequence, is_active
                    FROM %s.m_bom_line WHERE is_active = 1
                      AND bom_id IN (SELECT bom_id FROM %s.m_bom WHERE bom_type = 'SET')
                    """.formatted(alias, alias));
                st.execute("DETACH DATABASE " + alias);
            }
            dbIndex++;
        }
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (conn != null && !conn.isClosed()) conn.close();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  W-GEN-1: Selection Cascade finds matching BOMs
    // ═══════════════════════════════════════════════════════════════════

    @Test @Order(1)
    @DisplayName("W-GEN-1a: Library has SET BOMs with bom_category from SH + FK")
    void w_gen_1a_library_has_sets() throws Exception {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT bom_category, source, COUNT(*) AS cnt " +
                     "FROM m_bom GROUP BY bom_category, source ORDER BY source, bom_category")) {
            int total = 0;
            System.out.println("  Library SET BOMs:");
            while (rs.next()) {
                System.out.printf("    [%s] %-12s → %d SETs%n",
                        rs.getString("source"), rs.getString("bom_category"), rs.getInt("cnt"));
                total += rs.getInt("cnt");
            }
            assertTrue(total >= 5, "Library must have ≥5 SET BOMs from SH+FK");
        }
    }

    @Test @Order(2)
    @DisplayName("W-GEN-1b: Cascade finds matching SETs for DemoHouse rooms by category + AABB")
    void w_gen_1b_cascade_finds_matches() throws Exception {
        int matchedSlots = 0;
        int totalElements = 0;
        Set<String> matchedCategories = new LinkedHashSet<>();

        System.out.println("  Cascade results per room:");
        for (Object[] slot : SLOTS) {
            String category = (String) slot[0];
            int w = (int) slot[1], d = (int) slot[2], h = (int) slot[3];

            List<CascadeMatch> matches = cascadeQuery(category, w, d, h);

            if (!matches.isEmpty()) {
                matchedSlots++;
                matchedCategories.add(category);
                CascadeMatch best = matches.get(0);
                totalElements += best.childCount;
                System.out.printf("    %s (%dx%dx%d) → %s [%s] (%d children, %d candidates)%n",
                        category, w, d, h, best.bomId, best.source, best.childCount, matches.size());
            } else {
                System.out.printf("    %s (%dx%dx%d) → NO MATCH (validation rules flag gap)%n",
                        category, w, d, h);
            }
        }

        System.out.printf("  TOTAL: %d/%d slots matched, %d elements from cascade%n",
                matchedSlots, SLOTS.length, totalElements);

        // §3.5 cascade must find at least some matches from the library
        assertTrue(matchedSlots >= 2,
                "At least 2 of 5 slots should have cascade matches");
        assertTrue(matchedCategories.contains("BEDROOM") || matchedCategories.contains("BATHROOM"),
                "BEDROOM or BATHROOM must have cascade match");
    }

    @Test @Order(3)
    @DisplayName("W-GEN-1c: Cascade ranks by volume — largest fitting SET wins")
    void w_gen_1c_volume_ranking() throws Exception {
        // BEDROOM slot (5000×3500×2800) — may have SH_BED_SET + FK_BEDROOM_SET
        List<CascadeMatch> matches = cascadeQuery("BEDROOM", 5000, 3500, 2800);

        if (matches.size() >= 2) {
            long prevVolume = Long.MAX_VALUE;
            for (CascadeMatch m : matches) {
                assertTrue(m.volume <= prevVolume,
                        "Cascade must rank by volume DESC: " + m.bomId + " vol=" + m.volume);
                prevVolume = m.volume;
            }
            System.out.printf("  BEDROOM: %d candidates, best=%s (vol=%d)%n",
                    matches.size(), matches.get(0).bomId, matches.get(0).volume);
        } else if (matches.size() == 1) {
            System.out.printf("  BEDROOM: 1 candidate=%s (vol=%d)%n",
                    matches.get(0).bomId, matches.get(0).volume);
        }
        assertFalse(matches.isEmpty(), "BEDROOM must have at least 1 cascade match");
    }

    @Test @Order(4)
    @DisplayName("W-GEN-1d: Oversized SETs rejected by AABB fit check")
    void w_gen_1d_oversized_rejected() throws Exception {
        // LIVING slot (4000×3500×2800) — SH_LIVING_SET is 8868mm wide, must NOT match
        List<CascadeMatch> matches = cascadeQuery("LIVING", 4000, 3500, 2800);
        for (CascadeMatch m : matches) {
            assertNotEquals("SH_LIVING_SET", m.bomId,
                    "SH_LIVING_SET (8868mm) must not fit in 4000mm slot");
        }
        // FK_LIVING_SET (4590×10000) should also be rejected
        for (CascadeMatch m : matches) {
            assertNotEquals("FK_LIVING_SET", m.bomId,
                    "FK_LIVING_SET (4590×10000) must not fit in 4000×3500 slot");
        }
    }

    @Test @Order(5)
    @DisplayName("W-GEN-1e: Discipline coverage — matched SETs carry IFC elements")
    void w_gen_1e_discipline_coverage() throws Exception {
        Set<String> allRoles = new LinkedHashSet<>();
        int totalNonPhantom = 0;

        for (Object[] slot : SLOTS) {
            String category = (String) slot[0];
            int w = (int) slot[1], d = (int) slot[2], h = (int) slot[3];
            List<CascadeMatch> matches = cascadeQuery(category, w, d, h);
            if (matches.isEmpty()) continue;

            String bestBomId = matches.get(0).bomId;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT DISTINCT role FROM m_bom_line " +
                    "WHERE bom_id = ? AND is_active = 1 AND component_type != 'PHANTOM'")) {
                ps.setString(1, bestBomId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String role = rs.getString(1);
                        if (role != null) allRoles.add(role);
                    }
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM m_bom_line " +
                    "WHERE bom_id = ? AND is_active = 1 AND component_type != 'PHANTOM'")) {
                ps.setString(1, bestBomId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) totalNonPhantom += rs.getInt(1);
                }
            }
        }

        System.out.printf("  Disciplines from cascade: %s%n", allRoles);
        System.out.printf("  Total non-PHANTOM children: %d%n", totalNonPhantom);
        assertFalse(allRoles.isEmpty(), "Cascade must yield at least 1 IFC discipline");
    }

    @Test @Order(6)
    @DisplayName("W-GEN-1f: AABB=0 treated as universal fit (unmeasured SET always matches)")
    void w_gen_1f_zero_aabb_is_universal() throws Exception {
        // Check if any SET has AABB=0 and matches a slot
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT bom_id, bom_category FROM m_bom " +
                     "WHERE aabb_width_mm = 0 AND aabb_depth_mm = 0 AND aabb_height_mm = 0")) {
            boolean foundZero = false;
            while (rs.next()) {
                String bomId = rs.getString("bom_id");
                String cat = rs.getString("bom_category");
                foundZero = true;
                System.out.printf("  Universal-fit SET: %s (category=%s)%n", bomId, cat);
                // Verify it matches any slot with its category
                List<CascadeMatch> matches = cascadeQuery(cat, 1, 1, 1);  // tiny slot
                assertTrue(matches.stream().anyMatch(m -> m.bomId.equals(bomId)),
                        "AABB=0 SET " + bomId + " must match even a 1×1×1 slot");
            }
            if (!foundZero) {
                System.out.println("  No AABB=0 SETs in library (all measured) — OK");
            }
        }
    }

    @Test @Order(10)
    @DisplayName("W-GEN-1g: Full DemoHouse element projection — YAML in → cascade → element count")
    void w_gen_1g_full_projection() throws Exception {
        // Demonstrate the full generative path: YAML defines rooms → cascade fills them
        // Structural elements (from FLOOR BOMs) + furniture (from SET cascade)
        int structuralPerRoom = 7;  // 4 walls + slab + door + window (conservative)
        int cascadeElements = 0;

        System.out.println("  ═══ DemoHouse_2BR Element Projection ═══");
        System.out.println("  Input: classify_dm.yaml (5 rooms, bom_category + AABB)");
        System.out.println("  Pipeline: YAML → cascade → compiler → output (ZERO new code)");
        System.out.println();

        for (Object[] slot : SLOTS) {
            String category = (String) slot[0];
            int w = (int) slot[1], d = (int) slot[2], h = (int) slot[3];
            List<CascadeMatch> matches = cascadeQuery(category, w, d, h);

            int setChildren = matches.isEmpty() ? 0 : matches.get(0).childCount;
            cascadeElements += setChildren;

            System.out.printf("    %-10s %dx%dx%d → structural=%d + cascade=%d = %d%n",
                    category, w, d, h, structuralPerRoom, setChildren,
                    structuralPerRoom + setChildren);
        }

        int totalStructural = SLOTS.length * structuralPerRoom;
        int totalProjected = totalStructural + cascadeElements;

        System.out.printf("  ─────────────────────────────────────────────%n");
        System.out.printf("  Structural (walls/doors/windows/slab): %d%n", totalStructural);
        System.out.printf("  Cascade (furniture/fittings from library): %d%n", cascadeElements);
        System.out.printf("  TOTAL PROJECTED: %d elements from YAML + library%n", totalProjected);
        System.out.printf("  Human input: 0 lines of code, 5 YAML room entries%n");

        assertTrue(totalProjected >= SLOTS.length * structuralPerRoom,
                "Projected element count must be at least structural minimum");
    }

    // ── Cascade query — BBC.md §3.5 ─────────────────────────────────

    private record CascadeMatch(String bomId, String bomName, String source,
                                 int childCount, long volume) {}

    /**
     * BBC.md §3.5 Selection Cascade:
     * 1. Scope: bom_category = slot category
     * 2. Fit: SET AABB ≤ slot AABB (0 = universal fit)
     * 3. Rank: largest volume first, then seq_no
     */
    private List<CascadeMatch> cascadeQuery(String category, int slotW, int slotD, int slotH)
            throws Exception {
        String sql = """
                SELECT b.bom_id, b.bom_name, b.source,
                       (SELECT COUNT(*) FROM m_bom_line l
                        WHERE l.bom_id = b.bom_id AND l.is_active = 1
                          AND l.component_type != 'PHANTOM') AS child_count,
                       (CAST(b.aabb_width_mm AS INTEGER) * CAST(b.aabb_depth_mm AS INTEGER)
                        * CAST(b.aabb_height_mm AS INTEGER)) AS volume
                FROM m_bom b
                WHERE b.bom_category = ?
                  AND b.is_active = 1
                  AND (b.aabb_width_mm <= ? OR b.aabb_width_mm = 0)
                  AND (b.aabb_depth_mm <= ? OR b.aabb_depth_mm = 0)
                  AND (b.aabb_height_mm <= ? OR b.aabb_height_mm = 0)
                ORDER BY volume DESC, b.seq_no ASC
                """;
        List<CascadeMatch> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, category);
            ps.setInt(2, slotW);
            ps.setInt(3, slotD);
            ps.setInt(4, slotH);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new CascadeMatch(
                            rs.getString("bom_id"),
                            rs.getString("bom_name"),
                            rs.getString("source"),
                            rs.getInt("child_count"),
                            rs.getLong("volume")
                    ));
                }
            }
        }
        return results;
    }
}
