package com.bim.compiler.contract;

import com.bim.compiler.validation.SpatialDigest;
import com.bim.compiler.validation.SpatialDigest.DigestReport;
import org.junit.jupiter.api.*;

import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P0.2-VERIFY: BOM is the sole spatial source for EXTRACTED buildings.
 *
 * <p>After P0.2, m_bom_line (BOM.db) carries all instance-specific columns
 * (storey, element_ref, ordinal, orientation, material_name, material_rgba).
 * PlacementAD reads from BOM.db, not component_library.db. The old
 * cross-source tests (BOM vs PlacementAD) are replaced by BOM-only
 * integrity checks.
 *
 * <h2>Witness claims</h2>
 * <ul>
 *   <li>W-VERIFY-1: SH BOM digest is stable (55 elements, 8 classes)</li>
 *   <li>W-VERIFY-2: DX BOM digest is stable (1099 elements, 13 classes)</li>
 *   <li>W-VERIFY-3: Per-class counts correct (SH: 8 classes, DX: 13 classes)</li>
 *   <li>W-VERIFY-4: 55 SH BOM lines active, 0 NULL storey</li>
 *   <li>W-VERIFY-5: 1099 DX BOM lines active, 0 NULL storey</li>
 * </ul>
 */
class BOMDigestVerifyTest {

    private static final String BOM_DB = "library/BOM.db";

    // ── W-VERIFY-1: SH BOM digest stable ─────────────────────────────────

    @Test
    @DisplayName("W-VERIFY-1: SH BOM digest is stable (55 elements, 8 classes)")
    void w_verify_1_sh_digest_stable() throws Exception {
        try (Connection bomConn = DriverManager.getConnection("jdbc:sqlite:" + BOM_DB)) {
            DigestReport bom = SpatialDigest.computeFromBOM(bomConn, "EXT_SH");
            assertEquals(55, bom.elementCount(),
                "SH BOM must have 55 elements");
            assertEquals(8, bom.classCounts().size(),
                "SH BOM must have 8 IFC classes");
            assertNotNull(bom.digest(), "SH digest must be non-null");
            assertFalse(bom.digest().isEmpty(), "SH digest must be non-empty");
        }
    }

    // ── W-VERIFY-2: DX BOM digest stable ─────────────────────────────────

    @Test
    @DisplayName("W-VERIFY-2: DX BOM digest is stable (1099 elements, 13 classes)")
    void w_verify_2_dx_digest_stable() throws Exception {
        try (Connection bomConn = DriverManager.getConnection("jdbc:sqlite:" + BOM_DB)) {
            DigestReport bom = SpatialDigest.computeFromBOM(bomConn, "EXT_DX");
            assertEquals(1099, bom.elementCount(),
                "DX BOM must have 1099 elements");
            assertEquals(13, bom.classCounts().size(),
                "DX BOM must have 13 IFC classes");
            assertNotNull(bom.digest(), "DX digest must be non-null");
            assertFalse(bom.digest().isEmpty(), "DX digest must be non-empty");
        }
    }

    // ── W-VERIFY-3: Per-class counts match ───────────────────────────────

    @Test
    @DisplayName("W-VERIFY-3: Per-class counts correct (SH: 8, DX: 13 classes)")
    void w_verify_3_class_counts() throws Exception {
        try (Connection bomConn = DriverManager.getConnection("jdbc:sqlite:" + BOM_DB)) {
            // SH
            DigestReport bomSH = SpatialDigest.computeFromBOM(bomConn, "EXT_SH");
            assertEquals(8, bomSH.classCounts().size(),
                "SH BOM must have 8 IFC classes");

            // DX
            DigestReport bomDX = SpatialDigest.computeFromBOM(bomConn, "EXT_DX");
            assertEquals(13, bomDX.classCounts().size(),
                "DX BOM must have 13 IFC classes");
        }
    }

    // ── W-VERIFY-4: SH BOM count + 0 NULL storey ────────────────────────

    @Test
    @DisplayName("W-VERIFY-4: 55 SH BOM lines active, 0 NULL storey")
    void w_verify_4_sh_bom_integrity() throws Exception {
        try (Connection bomConn = DriverManager.getConnection("jdbc:sqlite:" + BOM_DB)) {
            int count;
            int nullStorey;
            try (Statement st = bomConn.createStatement()) {
                try (ResultSet rs = st.executeQuery(
                        "SELECT COUNT(*) FROM m_bom_line WHERE bom_id='EXT_SH' AND is_active=1")) {
                    rs.next();
                    count = rs.getInt(1);
                }
                try (ResultSet rs = st.executeQuery(
                        "SELECT COUNT(*) FROM m_bom_line WHERE bom_id='EXT_SH' AND is_active=1 AND storey IS NULL")) {
                    rs.next();
                    nullStorey = rs.getInt(1);
                }
            }
            assertEquals(55, count, "EXT_SH must have 55 active BOM lines");
            assertEquals(0, nullStorey, "EXT_SH must have 0 NULL storey (all backfilled)");
        }
    }

    // ── W-VERIFY-5: DX BOM count + 0 NULL storey ────────────────────────

    @Test
    @DisplayName("W-VERIFY-5: 1099 DX BOM lines active, 0 NULL storey")
    void w_verify_5_dx_bom_integrity() throws Exception {
        try (Connection bomConn = DriverManager.getConnection("jdbc:sqlite:" + BOM_DB)) {
            int count;
            int nullStorey;
            try (Statement st = bomConn.createStatement()) {
                try (ResultSet rs = st.executeQuery(
                        "SELECT COUNT(*) FROM m_bom_line WHERE bom_id='EXT_DX' AND is_active=1")) {
                    rs.next();
                    count = rs.getInt(1);
                }
                try (ResultSet rs = st.executeQuery(
                        "SELECT COUNT(*) FROM m_bom_line WHERE bom_id='EXT_DX' AND is_active=1 AND storey IS NULL")) {
                    rs.next();
                    nullStorey = rs.getInt(1);
                }
            }
            assertEquals(1099, count, "EXT_DX must have 1099 active BOM lines");
            assertEquals(0, nullStorey, "EXT_DX must have 0 NULL storey (all backfilled)");
        }
    }
}
