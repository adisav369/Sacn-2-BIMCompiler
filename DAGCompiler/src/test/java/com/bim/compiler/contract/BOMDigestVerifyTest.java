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
 * PlacementLoader reads from BOM.db, not component_library.db. The old
 * cross-source tests (BOM vs PlacementLoader) are replaced by BOM-only
 * integrity checks.
 *
 * <p>BUILDING BOM tree walk: BUILDING_SH_STD → SH_GF_STR + SH_ROOF_STR + SH_CW_STR.
 * World coordinates reconstructed by adding MAKE offsets to BUY centroids.
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

    private static String bomDbPath() { return System.getProperty("bom.db"); }

    /** BUILDING BOM ids — tree roots for EXTRACTED buildings. */
    private static final String BUILDING_SH = "BUILDING_SH_STD";
    private static final String BUILDING_DX = "BUILDING_DX_STD";

    /** Golden digests — change ONLY after manual verification against reference DBs. */
    private static final String GOLDEN_SH_DIGEST = "ecb1be6f5935bda7a4e56805a8a3064b245eca1aa19db8030b1bdb2b0053e0ed";
    private static final String GOLDEN_DX_DIGEST = "57094c2c0f5e31cb07ab86c42dccf32f64e41ff6155fb5f149da1318d87e6dcb";

    /** Floor STR BOM ids — children of BUILDING BOMs containing BUY leaves. */
    private static final String[] SH_FLOORS = {"SH_GF_STR", "SH_ROOF_STR", "SH_CW_STR"};
    private static final String[] DX_FLOORS = {"DX_L1_STR", "DX_L2_STR", "DX_ROOF_STR", "DX_FDN_STR", "DX_MISC_STR"};

    // ── W-VERIFY-1: SH BOM digest stable ─────────────────────────────────

    @Test
    @DisplayName("W-VERIFY-1: SH BOM digest is stable (55 elements, 8 classes)")
    void w_verify_1_sh_digest_stable() throws Exception {
        try (Connection bomConn = DriverManager.getConnection("jdbc:sqlite:" + bomDbPath())) {
            DigestReport bom = SpatialDigest.computeFromBOMTree(bomConn, BUILDING_SH);
            assertEquals(55, bom.elementCount(),
                "SH BOM tree must have 55 BUY elements");
            assertEquals(8, bom.classCounts().size(),
                "SH BOM tree must have 8 IFC classes");
            assertEquals(GOLDEN_SH_DIGEST, bom.digest(),
                "SH BOM tree digest must match golden value");
        }
    }

    // ── W-VERIFY-2: DX BOM digest stable ─────────────────────────────────

    @Test
    @DisplayName("W-VERIFY-2: DX BOM digest is stable (1099 elements, 13 classes)")
    void w_verify_2_dx_digest_stable() throws Exception {
        try (Connection bomConn = DriverManager.getConnection("jdbc:sqlite:" + bomDbPath())) {
            DigestReport bom = SpatialDigest.computeFromBOMTree(bomConn, BUILDING_DX);
            assertEquals(1099, bom.elementCount(),
                "DX BOM tree must have 1099 BUY elements");
            assertEquals(13, bom.classCounts().size(),
                "DX BOM tree must have 13 IFC classes");
            assertEquals(GOLDEN_DX_DIGEST, bom.digest(),
                "DX BOM tree digest must match golden value");
        }
    }

    // ── W-VERIFY-3: Per-class counts match ───────────────────────────────

    @Test
    @DisplayName("W-VERIFY-3: Per-class counts correct (SH: 8, DX: 13 classes)")
    void w_verify_3_class_counts() throws Exception {
        try (Connection bomConn = DriverManager.getConnection("jdbc:sqlite:" + bomDbPath())) {
            // SH
            DigestReport bomSH = SpatialDigest.computeFromBOMTree(bomConn, BUILDING_SH);
            assertEquals(8, bomSH.classCounts().size(),
                "SH BOM tree must have 8 IFC classes");

            // DX
            DigestReport bomDX = SpatialDigest.computeFromBOMTree(bomConn, BUILDING_DX);
            assertEquals(13, bomDX.classCounts().size(),
                "DX BOM tree must have 13 IFC classes");
        }
    }

    // ── W-VERIFY-4: SH BOM count + 0 NULL storey ────────────────────────

    @Test
    @DisplayName("W-VERIFY-4: 55 SH BOM lines active, 0 NULL storey")
    void w_verify_4_sh_bom_integrity() throws Exception {
        try (Connection bomConn = DriverManager.getConnection("jdbc:sqlite:" + bomDbPath())) {
            int[] result = countBuyLinesAndNullStorey(bomConn, SH_FLOORS);
            assertEquals(55, result[0], "SH BUILDING BOM tree must have 55 active BUY lines");
            assertEquals(0, result[1], "SH BOM must have 0 NULL storey (all backfilled)");
        }
    }

    // ── W-VERIFY-5: DX BOM count + 0 NULL storey ────────────────────────

    @Test
    @DisplayName("W-VERIFY-5: 1099 DX BOM lines active, 0 NULL storey")
    void w_verify_5_dx_bom_integrity() throws Exception {
        try (Connection bomConn = DriverManager.getConnection("jdbc:sqlite:" + bomDbPath())) {
            int[] result = countBuyLinesAndNullStorey(bomConn, DX_FLOORS);
            assertEquals(1099, result[0], "DX BUILDING BOM tree must have 1099 active BUY lines");
            assertEquals(0, result[1], "DX BOM must have 0 NULL storey (all backfilled)");
        }
    }

    /**
     * Count BUY lines and NULL storey across multiple floor STR BOMs.
     * @return int[]{totalBuyLines, nullStoreyCount}
     */
    private int[] countBuyLinesAndNullStorey(Connection conn, String[] floorBomIds) throws SQLException {
        int total = 0;
        int nullStorey = 0;
        for (String floorBomId : floorBomIds) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM m_bom_line WHERE bom_id=? AND component_type='BUY' AND is_active=1")) {
                ps.setString(1, floorBomId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    total += rs.getInt(1);
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM m_bom_line WHERE bom_id=? AND component_type='BUY' AND is_active=1 AND storey IS NULL")) {
                ps.setString(1, floorBomId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    nullStorey += rs.getInt(1);
                }
            }
        }
        return new int[]{total, nullStorey};
    }
}
