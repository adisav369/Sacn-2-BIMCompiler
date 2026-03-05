package com.bim.compiler.contract;

import com.bim.compiler.validation.SpatialDigest;
import com.bim.compiler.validation.SpatialDigest.DigestReport;
import org.junit.jupiter.api.*;

import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P0.1-VERIFY: BOM Digest == PlacementAD Digest.
 *
 * <p>Proves that m_bom_line (BOM.db) encodes the <b>same spatial geometry</b>
 * as ad_element_placement (component_library.db). After the precision migration,
 * centroid ± exactDim/2000 == min/max algebraically.
 *
 * <p>Both digests use the same CLASS=X COUNT=N + coord line format
 * (coordinates rounded to 1mm via Math.round(* 1000)), so SHA-256 match
 * proves data equivalence across the two databases.
 *
 * <h2>Witness claims</h2>
 * <ul>
 *   <li>W-VERIFY-1: SH computeFromBOM("EXT_SH") == computeFromPlacement("Ifc4_SampleHouse")</li>
 *   <li>W-VERIFY-2: DX computeFromBOM("EXT_DX") == computeFromPlacement("Ifc2x3_Duplex")</li>
 *   <li>W-VERIFY-3: Per-class counts match (SH: 8 classes, DX: 13 classes)</li>
 *   <li>W-VERIFY-4: 55 SH BOM lines match 55 active placements (no orphans)</li>
 *   <li>W-VERIFY-5: 1099 DX BOM lines match 1099 active placements (no orphans)</li>
 * </ul>
 */
class BOMDigestVerifyTest {

    private static final String BOM_DB = "library/BOM.db";
    private static final String COMPONENT_LIB_DB = "library/component_library.db";

    // ── W-VERIFY-1: SH digest match ────────────────────────────────────────

    @Test
    @DisplayName("W-VERIFY-1: SH BOM digest == PlacementAD digest")
    void w_verify_1_sh_digest_match() throws Exception {
        try (Connection bomConn = DriverManager.getConnection("jdbc:sqlite:" + BOM_DB);
             Connection clibConn = DriverManager.getConnection("jdbc:sqlite:" + COMPONENT_LIB_DB)) {

            DigestReport bom = SpatialDigest.computeFromBOM(bomConn, "EXT_SH");
            DigestReport placement = SpatialDigest.computeFromPlacement(clibConn, "Ifc4_SampleHouse");

            assertEquals(placement.digest(), bom.digest(),
                "SH: BOM digest must match PlacementAD digest.\n" +
                "BOM: " + bom + "\nPlacement: " + placement);
        }
    }

    // ── W-VERIFY-2: DX digest match ────────────────────────────────────────

    @Test
    @DisplayName("W-VERIFY-2: DX BOM digest == PlacementAD digest")
    void w_verify_2_dx_digest_match() throws Exception {
        try (Connection bomConn = DriverManager.getConnection("jdbc:sqlite:" + BOM_DB);
             Connection clibConn = DriverManager.getConnection("jdbc:sqlite:" + COMPONENT_LIB_DB)) {

            DigestReport bom = SpatialDigest.computeFromBOM(bomConn, "EXT_DX");
            DigestReport placement = SpatialDigest.computeFromPlacement(clibConn, "Ifc2x3_Duplex");

            assertEquals(placement.digest(), bom.digest(),
                "DX: BOM digest must match PlacementAD digest.\n" +
                "BOM: " + bom + "\nPlacement: " + placement);
        }
    }

    // ── W-VERIFY-3: Per-class counts match ──────────────────────────────────

    @Test
    @DisplayName("W-VERIFY-3: Per-class counts match (SH: 8, DX: 13 classes)")
    void w_verify_3_class_counts() throws Exception {
        try (Connection bomConn = DriverManager.getConnection("jdbc:sqlite:" + BOM_DB);
             Connection clibConn = DriverManager.getConnection("jdbc:sqlite:" + COMPONENT_LIB_DB)) {

            // SH
            DigestReport bomSH = SpatialDigest.computeFromBOM(bomConn, "EXT_SH");
            DigestReport placeSH = SpatialDigest.computeFromPlacement(clibConn, "Ifc4_SampleHouse");
            assertEquals(8, bomSH.classCounts().size(),
                "SH BOM must have 8 IFC classes");
            assertEquals(placeSH.classCounts(), bomSH.classCounts(),
                "SH: per-class counts from BOM must match PlacementAD");

            // DX
            DigestReport bomDX = SpatialDigest.computeFromBOM(bomConn, "EXT_DX");
            DigestReport placeDX = SpatialDigest.computeFromPlacement(clibConn, "Ifc2x3_Duplex");
            assertEquals(13, bomDX.classCounts().size(),
                "DX BOM must have 13 IFC classes");
            assertEquals(placeDX.classCounts(), bomDX.classCounts(),
                "DX: per-class counts from BOM must match PlacementAD");
        }
    }

    // ── W-VERIFY-4: SH count cross-check (no orphans) ──────────────────────

    @Test
    @DisplayName("W-VERIFY-4: 55 SH BOM lines match 55 active placements")
    void w_verify_4_sh_count_crosscheck() throws Exception {
        int bomCount;
        int placementCount;

        try (Connection bomConn = DriverManager.getConnection("jdbc:sqlite:" + BOM_DB);
             Statement st = bomConn.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT COUNT(*) FROM m_bom_line WHERE bom_id='EXT_SH' AND is_active=1")) {
            rs.next();
            bomCount = rs.getInt(1);
        }

        try (Connection clibConn = DriverManager.getConnection("jdbc:sqlite:" + COMPONENT_LIB_DB);
             Statement st = clibConn.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT COUNT(*) FROM ad_element_placement " +
                 "WHERE building_type='Ifc4_SampleHouse' AND is_active=1")) {
            rs.next();
            placementCount = rs.getInt(1);
        }

        assertEquals(55, bomCount, "EXT_SH must have 55 active BOM lines");
        assertEquals(55, placementCount, "Ifc4_SampleHouse must have 55 active placements");
        assertEquals(placementCount, bomCount, "SH: no orphans — BOM count must match placement count");
    }

    // ── W-VERIFY-5: DX count cross-check (no orphans) ──────────────────────

    @Test
    @DisplayName("W-VERIFY-5: 1099 DX BOM lines match 1099 active placements")
    void w_verify_5_dx_count_crosscheck() throws Exception {
        int bomCount;
        int placementCount;

        try (Connection bomConn = DriverManager.getConnection("jdbc:sqlite:" + BOM_DB);
             Statement st = bomConn.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT COUNT(*) FROM m_bom_line WHERE bom_id='EXT_DX' AND is_active=1")) {
            rs.next();
            bomCount = rs.getInt(1);
        }

        try (Connection clibConn = DriverManager.getConnection("jdbc:sqlite:" + COMPONENT_LIB_DB);
             Statement st = clibConn.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT COUNT(*) FROM ad_element_placement " +
                 "WHERE building_type='Ifc2x3_Duplex' AND is_active=1")) {
            rs.next();
            placementCount = rs.getInt(1);
        }

        assertEquals(1099, bomCount, "EXT_DX must have 1099 active BOM lines");
        assertEquals(1099, placementCount, "Ifc2x3_Duplex must have 1099 active placements");
        assertEquals(placementCount, bomCount, "DX: no orphans — BOM count must match placement count");
    }
}
