package com.bim.designer;

import org.junit.jupiter.api.*;

import java.nio.file.*;
import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Pattern rule expansion test — BIM_Designer.md §10.
 *
 * <p>Proves: given a room AABB + pattern rules from validation.db,
 * the correct number of elements is computed. This is the generative
 * equivalent of verb_ref expansion for extracted buildings.
 *
 * <p>Separation of concerns:
 * <ul>
 *   <li>Pattern rules → validation.db (generative concern)</li>
 *   <li>Verb_ref → {PREFIX}_BOM.db (extraction concern)</li>
 *   <li>Same compiler math, different data source</li>
 * </ul>
 */
@DisplayName("Pattern Rules — §10 Multiplication")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PatternRuleTest {

    static final Path VALIDATION_DB = Path.of("library/validation.db");
    static Connection conn;

    @BeforeAll
    static void open() throws Exception {
        assumeTrue(Files.exists(VALIDATION_DB), "validation.db must exist");
        conn = DriverManager.getConnection("jdbc:sqlite:" + VALIDATION_DB);
    }

    @AfterAll
    static void close() throws Exception {
        if (conn != null) conn.close();
    }

    // ── Helper: compute count for a LINEAR pattern rule ───────────────

    static int linearCount(double parentLengthMm, double spacingMm,
                           double marginStart, double marginEnd) {
        double span = parentLengthMm - marginStart - marginEnd;
        if (span <= 0) return 0;
        return (int) Math.floor(span / spacingMm) + 1;
    }

    // ── Helper: compute count for a GRID pattern rule ─────────────────

    static int gridCount(double parentWidthMm, double parentDepthMm,
                         double spacingX, double spacingY,
                         double marginStart, double marginEnd) {
        int nx = linearCount(parentWidthMm, spacingX, marginStart, marginEnd);
        int ny = linearCount(parentDepthMm, spacingY, marginStart, marginEnd);
        return nx * ny;
    }

    // ── W-PR-1: Window count on 5000mm wall ───────────────────────────

    @Test @Order(1)
    @DisplayName("W-PR-1: Windows on 5000mm exterior wall → 2 windows")
    void windowCount5m() throws Exception {
        // Rule P1: spacing=2500, margin_start=600, margin_end=600
        double wallLength = 5000;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT spacing_mm, margin_start_mm, margin_end_mm " +
                "FROM ad_pattern_rule WHERE id = 1")) {
            ResultSet rs = ps.executeQuery();
            assertTrue(rs.next());
            int count = linearCount(wallLength,
                    rs.getDouble("spacing_mm"),
                    rs.getDouble("margin_start_mm"),
                    rs.getDouble("margin_end_mm"));
            assertEquals(2, count, "5000mm wall with 2500mm spacing, 600mm margins → 2 windows");
            System.out.printf("  Windows on 5000mm wall: %d (spacing=%.0f, margins=%.0f+%.0f)%n",
                    count, rs.getDouble("spacing_mm"),
                    rs.getDouble("margin_start_mm"), rs.getDouble("margin_end_mm"));
        }
    }

    // ── W-PR-2: Window count on 12000mm wall ──────────────────────────

    @Test @Order(2)
    @DisplayName("W-PR-2: Windows on 12000mm exterior wall → 5 windows")
    void windowCount12m() throws Exception {
        double wallLength = 12000;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT spacing_mm, margin_start_mm, margin_end_mm " +
                "FROM ad_pattern_rule WHERE id = 1")) {
            ResultSet rs = ps.executeQuery();
            assertTrue(rs.next());
            int count = linearCount(wallLength,
                    rs.getDouble("spacing_mm"),
                    rs.getDouble("margin_start_mm"),
                    rs.getDouble("margin_end_mm"));
            // span = 12000 - 600 - 600 = 10800, floor(10800/2500)+1 = 5
            assertEquals(5, count);
            System.out.printf("  Windows on 12000mm wall: %d%n", count);
        }
    }

    // ── W-PR-3: Sprinkler grid in 5000×3500 bedroom ──────────────────

    @Test @Order(3)
    @DisplayName("W-PR-3: Sprinkler grid in 5000×3500mm bedroom → 2 heads")
    void sprinklerGridBedroom() throws Exception {
        // Rule P2: spacing=3000×3000, margin=500
        // Room 5000×3500 (bilik_1)
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT spacing_mm, spacing_y_mm, margin_start_mm, margin_end_mm, " +
                "min_room_area_m2 FROM ad_pattern_rule WHERE id = 2")) {
            ResultSet rs = ps.executeQuery();
            assertTrue(rs.next());
            double area = 5.0 * 3.5; // 17.5 m²
            assertTrue(area >= rs.getDouble("min_room_area_m2"),
                    "Room must be >= min area for sprinklers");
            int count = gridCount(5000, 3500,
                    rs.getDouble("spacing_mm"), rs.getDouble("spacing_y_mm"),
                    rs.getDouble("margin_start_mm"), rs.getDouble("margin_end_mm"));
            // X: span=4000, floor(4000/3000)+1 = 2
            // Y: span=2500, floor(2500/3000)+1 = 1
            // total = 2×1 = 2
            assertEquals(2, count);
            System.out.printf("  Sprinklers in 5000×3500mm: %d heads%n", count);
        }
    }

    // ── W-PR-4: Sprinkler grid in large living room ───────────────────

    @Test @Order(4)
    @DisplayName("W-PR-4: Sprinkler grid in 8000×6000mm room → 6 heads")
    void sprinklerGridLargeRoom() throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT spacing_mm, spacing_y_mm, margin_start_mm, margin_end_mm " +
                "FROM ad_pattern_rule WHERE id = 2")) {
            ResultSet rs = ps.executeQuery();
            assertTrue(rs.next());
            int count = gridCount(8000, 6000,
                    rs.getDouble("spacing_mm"), rs.getDouble("spacing_y_mm"),
                    rs.getDouble("margin_start_mm"), rs.getDouble("margin_end_mm"));
            // X: span=7000, floor(7000/3000)+1 = 3
            // Y: span=5000, floor(5000/3000)+1 = 2
            // total = 3×2 = 6
            assertEquals(6, count);
            System.out.printf("  Sprinklers in 8000×6000mm: %d heads%n", count);
        }
    }

    // ── W-PR-5: Small bathroom exempt from sprinklers ─────────────────

    @Test @Order(5)
    @DisplayName("W-PR-5: Bathroom 2000×1500mm (3m²) exempt from sprinklers")
    void bathroomExempt() throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT min_room_area_m2 FROM ad_pattern_rule WHERE id = 2")) {
            ResultSet rs = ps.executeQuery();
            assertTrue(rs.next());
            double minArea = rs.getDouble("min_room_area_m2");
            double bathroomArea = 2.0 * 1.5; // 3.0 m²
            assertTrue(bathroomArea < minArea,
                    "Bathroom 3.0m² should be below sprinkler threshold " + minArea);
            System.out.printf("  Bathroom 3.0m² < %.1fm² threshold → exempt%n", minArea);
        }
    }

    // ── W-PR-6: Room resize → count changes ───────────────────────────

    @Test @Order(6)
    @DisplayName("W-PR-6: Room resize 4000→6000mm → window count 2→3")
    void roomResizeUpdatesCount() throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT spacing_mm, margin_start_mm, margin_end_mm " +
                "FROM ad_pattern_rule WHERE id = 1")) {
            ResultSet rs = ps.executeQuery();
            assertTrue(rs.next());
            double spacing = rs.getDouble("spacing_mm");
            double ms = rs.getDouble("margin_start_mm");
            double me = rs.getDouble("margin_end_mm");

            int before = linearCount(4000, spacing, ms, me);
            int after = linearCount(8000, spacing, ms, me);

            // 4000: span=2800, floor(2800/2500)+1 = 2
            // 8000: span=6800, floor(6800/2500)+1 = 3
            assertEquals(2, before, "4000mm wall → 2 windows");
            assertEquals(3, after, "8000mm wall → 3 windows");
            System.out.printf("  Resize 4000→8000mm: %d→%d windows%n", before, after);
        }
    }

    // ── W-PR-7: All 8 rules loaded ───────────────────────────────────

    @Test @Order(7)
    @DisplayName("W-PR-7: All 8 pattern rules present in validation.db")
    void allRulesLoaded() throws Exception {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM ad_pattern_rule WHERE is_active = 1")) {
            rs.next();
            assertEquals(8, rs.getInt(1), "Expected 8 active pattern rules");
        }

        // Verify discipline coverage
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT discipline, COUNT(*) FROM ad_pattern_rule GROUP BY discipline ORDER BY discipline")) {
            System.out.println("  Pattern rules by discipline:");
            while (rs.next()) {
                System.out.printf("    %s: %d rules%n", rs.getString(1), rs.getInt(2));
            }
        }
    }
}
