package com.bim.cobol;

import com.bim.ormsandbox.po.MBOM;
import com.bim.ormsandbox.po.MBomCategory;
import com.bim.ormsandbox.po.MCDocType;
import org.junit.jupiter.api.*;

import java.io.File;
import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Prime Rule — Three-Key Match Witnesses.
 *
 * <p>The Prime Rule states:
 * <pre>
 *   C_Order.(AABB + DocBaseType + DocSubType) == m_bom.(AABB + DocBaseType + DocSubType)
 *   → match + count=1 → SINGULARITY → EN-BLOC
 * </pre>
 *
 * <p>These witnesses prove that the three-key match data is consistent
 * across m_bom, C_DocType, and M_BomCategory. All read-only — BOM.db
 * is never mutated.
 *
 * <p>Witnesses:
 * <ul>
 *   <li>W-PRIME-1: Every BUILDING BOM has doc_base_type + doc_sub_type</li>
 *   <li>W-PRIME-2: Every BUILDING BOM has non-zero AABB</li>
 *   <li>W-PRIME-3: Singularity — exactly 1 BUILDING BOM per doc_sub_type</li>
 *   <li>W-PRIME-4: C_DocType entries cover all BUILDING doc_sub_types</li>
 *   <li>W-PRIME-5: ST template categories have AABB matching BUILDING BOMs</li>
 *   <li>W-PRIME-6: Three-key JOIN yields singularity for SH and DX</li>
 * </ul>
 */
@DisplayName("PRIME RULE — Three-Key Match Witnesses")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PrimeRuleWitnessTest {

    private static Connection conn;

    @BeforeAll
    static void setUp() throws Exception {
        File bomDb = new File("library/BOM.db");
        assumeTrue(bomDb.exists(), "library/BOM.db must exist");
        conn = DriverManager.getConnection("jdbc:sqlite:" + bomDb.getAbsolutePath());
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (conn != null) conn.close();
    }

    // ── W-PRIME-1: Every BUILDING BOM has doc_base_type + doc_sub_type ───

    @Test
    @Order(1)
    @DisplayName("W-PRIME-1: BUILDING BOMs have doc_base_type + doc_sub_type")
    void w_prime_1_buildingBomsHaveDocKeys() throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT bom_id, doc_base_type, doc_sub_type "
                     + "FROM m_bom WHERE bom_type = 'BUILDING'")) {

            int count = 0;
            while (rs.next()) {
                String bomId = rs.getString("bom_id");
                String docBaseType = rs.getString("doc_base_type");
                String docSubType = rs.getString("doc_sub_type");

                assertNotNull(docBaseType,
                        bomId + " must have doc_base_type");
                assertNotNull(docSubType,
                        bomId + " must have doc_sub_type");
                assertFalse(docBaseType.isEmpty(),
                        bomId + " doc_base_type must not be empty");
                assertFalse(docSubType.isEmpty(),
                        bomId + " doc_sub_type must not be empty");
                count++;
            }

            assertTrue(count >= 3,
                    "at least 3 BUILDING BOMs (SH, DX, TB), got " + count);
        }
    }

    // ── W-PRIME-2: Every BUILDING BOM has non-zero AABB ─────────────────

    @Test
    @Order(2)
    @DisplayName("W-PRIME-2: BUILDING BOMs have non-zero AABB dimensions")
    void w_prime_2_buildingBomsHaveAabb() throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT bom_id, aabb_width_mm, aabb_depth_mm, aabb_height_mm "
                     + "FROM m_bom WHERE bom_type = 'BUILDING'")) {

            while (rs.next()) {
                String bomId = rs.getString("bom_id");
                int w = rs.getInt("aabb_width_mm");
                int d = rs.getInt("aabb_depth_mm");
                int h = rs.getInt("aabb_height_mm");

                assertTrue(w > 0, bomId + " aabb_width_mm must be > 0, got " + w);
                assertTrue(d > 0, bomId + " aabb_depth_mm must be > 0, got " + d);
                assertTrue(h > 0, bomId + " aabb_height_mm must be > 0, got " + h);
            }
        }
    }

    // ── W-PRIME-3: Singularity — exactly 1 BUILDING BOM per doc_sub_type ──

    @Test
    @Order(3)
    @DisplayName("W-PRIME-3: Singularity — 1 BUILDING BOM per doc_sub_type")
    void w_prime_3_singularity() throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT doc_sub_type, COUNT(*) AS cnt "
                     + "FROM m_bom WHERE bom_type = 'BUILDING' "
                     + "GROUP BY doc_sub_type HAVING cnt != 1")) {

            List<String> violations = new ArrayList<>();
            while (rs.next()) {
                violations.add(rs.getString("doc_sub_type")
                        + "=" + rs.getInt("cnt"));
            }

            assertTrue(violations.isEmpty(),
                    "Singularity violated — multiple BUILDING BOMs: " + violations);
        }
    }

    // ── W-PRIME-4: C_DocType covers all BUILDING doc_sub_types ──────────

    @Test
    @Order(4)
    @DisplayName("W-PRIME-4: C_DocType entries cover BUILDING doc_sub_types")
    void w_prime_4_docTypeCoverage() throws SQLException {
        // Get all doc_sub_types from BUILDING BOMs
        Set<String> bomSubTypes = new LinkedHashSet<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT DISTINCT doc_sub_type FROM m_bom "
                     + "WHERE bom_type = 'BUILDING'")) {
            while (rs.next()) bomSubTypes.add(rs.getString(1));
        }

        // Check each has a matching C_DocType
        for (String subType : bomSubTypes) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT DocBaseType FROM C_DocType "
                    + "WHERE DocSubType = ? AND IsActive = 1")) {
                ps.setString(1, subType);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next(),
                            "C_DocType must exist for DocSubType=" + subType);
                    String docBaseType = rs.getString(1);
                    assertNotNull(docBaseType,
                            "C_DocType.DocBaseType must not be null for " + subType);
                }
            }
        }
    }

    // ── W-PRIME-5: ST template categories match BUILDING BOM AABBs ──────

    @Test
    @Order(5)
    @DisplayName("W-PRIME-5: ST-SH and ST-DX AABBs match BUILDING BOMs")
    void w_prime_5_templateAabbMatch() throws SQLException {
        // For each ST template category, its AABB must match one BUILDING BOM
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT M_BomCategory_ID, aabb_width_mm, aabb_depth_mm, "
                     + "aabb_height_mm FROM M_BomCategory "
                     + "WHERE doc_sub_type = 'ST' AND IsActive = 1")) {

            int templateCount = 0;
            while (rs.next()) {
                String catId = rs.getString("M_BomCategory_ID");
                int w = rs.getInt("aabb_width_mm");
                int d = rs.getInt("aabb_depth_mm");
                int h = rs.getInt("aabb_height_mm");

                assertTrue(w > 0, catId + " AABB width must be > 0");

                // Find a BUILDING BOM with matching AABB
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT bom_id FROM m_bom "
                        + "WHERE bom_type = 'BUILDING' "
                        + "AND aabb_width_mm = ? AND aabb_depth_mm = ? "
                        + "AND aabb_height_mm = ?")) {
                    ps.setInt(1, w);
                    ps.setInt(2, d);
                    ps.setInt(3, h);
                    try (ResultSet rs2 = ps.executeQuery()) {
                        assertTrue(rs2.next(),
                                catId + " AABB (" + w + "×" + d + "×" + h
                                + ") must match a BUILDING BOM");
                    }
                }
                templateCount++;
            }

            assertTrue(templateCount >= 2,
                    "at least 2 ST template categories (ST-SH, ST-DX), got "
                    + templateCount);
        }
    }

    // ── W-PRIME-6: Three-key JOIN yields singularity for SH and DX ──────

    @Test
    @Order(6)
    @DisplayName("W-PRIME-6: Three-key JOIN → singularity for SH and DX")
    void w_prime_6_threeKeyJoin() throws SQLException {
        for (String subType : List.of("SH", "DX")) {
            // Step 1: Find BUILDING BOM by doc_sub_type
            MBOM bom = MBOM.getBuildingBom(conn, subType);
            assertNotNull(bom,
                    "BUILDING BOM must exist for " + subType);

            // Step 2: Find C_DocType by DocSubType
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT DocBaseType, DocSubType FROM C_DocType "
                    + "WHERE DocSubType = ? AND DocBaseType = 'RE' "
                    + "AND IsActive = 1")) {
                ps.setString(1, subType);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next(),
                            "C_DocType RE/" + subType + " must exist");
                }
            }

            // Step 3: Three-key match — DocBaseType + DocSubType + AABB
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT b.bom_id FROM m_bom b "
                    + "JOIN C_DocType d ON d.DocSubType = b.doc_sub_type "
                    + "  AND d.DocBaseType = b.doc_base_type "
                    + "WHERE b.bom_type = 'BUILDING' "
                    + "  AND b.doc_sub_type = ? "
                    + "  AND b.aabb_width_mm > 0")) {
                ps.setString(1, subType);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next(),
                            "Three-key JOIN must find " + subType);
                    String matchedBom = rs.getString(1);
                    assertFalse(rs.next(),
                            "Three-key JOIN must yield exactly 1 result for "
                            + subType + " (singularity), first=" + matchedBom);
                }
            }
        }
    }
}
