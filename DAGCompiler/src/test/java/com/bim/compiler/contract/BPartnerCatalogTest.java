package com.bim.compiler.contract;

// Implementing DISC_VALIDATION_DB_SRS.md §12h — Witness: W-BPARTNER-RE
// Gate: every generative element in an RE building has C_BPartner=Duplex OR NULL.
// No CO-only product may appear in SH or DX compilation.
//
// PRECONDITION: DV051_m_product_bpartner.sql applied + SpaceScheduleDAO BPartner filter wired.
// Until then this test is PENDING — do not add to G1–G6 suite.

import org.junit.jupiter.api.*;

import java.nio.file.*;
import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * W-BPARTNER-RE — C_BPartner catalog segregation for RE (Residential) buildings.
 *
 * <p>Claim: every generative element produced by an RE building compilation
 * (SH, DX) was placed from a product with C_BPartner_ID = Duplex (ID=1) or NULL.
 * No CO-only product (Hospital, Terminal) may appear in an RE output.
 *
 * <p>Counterpart W-BPARTNER-CO is added when TE/Hospital are onboarded as CO.
 *
 * @see <a href="../../../../docs/DISC_VALIDATION_DB_SRS.md">§12h</a>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BPartnerCatalogTest {

    // C_BPartner_ID for Duplex (RE reference catalog) — from DV038 seed
    private static final int BPARTNER_DUPLEX = 1;

    // RE output DBs — adjust paths via system properties if needed
    private static final String SH_OUTPUT = "DAGCompiler/lib/output/samplehouse.db";
    private static final String DX_OUTPUT = "DAGCompiler/lib/output/duplex.db";
    private static final String ERP_DB    = "library/ERP.db";

    // ── W-BPARTNER-RE: SH ────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("W-BPARTNER-RE SH: all generative elements use Duplex or universal products")
    void wBPartnerReSh() throws Exception {
        assertBPartnerRe("SH", SH_OUTPUT);
    }

    // ── W-BPARTNER-RE: DX ────────────────────────────────────────────────

    @Test
    @Order(2)
    @DisplayName("W-BPARTNER-RE DX: all generative elements use Duplex or universal products")
    void wBPartnerReDx() throws Exception {
        assertBPartnerRe("DX", DX_OUTPUT);
    }

    // ── W-BPARTNER-SCHEDULE: schedule lookup filters by BPartner ─────────

    @Test
    @Order(3)
    @DisplayName("W-BPARTNER-SCHEDULE: no CO-only product appears in KITCHEN/BATHROOM/BEDROOM schedule")
    void wBPartnerScheduleNoCoLeak() throws Exception {
        // Every product in the MEP schedule for RE space types must have
        // C_BPartner_ID = Duplex or NULL. CO-tagged products must never leak in.
        if (!Files.exists(Paths.get(ERP_DB))) {
            fail("ERP.db not found at " + ERP_DB);
        }
        try (Connection erp = DriverManager.getConnection("jdbc:sqlite:" + ERP_DB)) {
            String sql = """
                SELECT s.space_type_id, s.mep_product_id, p.C_BPartner_ID, bp.Value as bpartner
                FROM ad_space_type_mep_bom s
                JOIN M_Product p ON p.product_id = s.mep_product_id
                LEFT JOIN C_BPartner bp ON p.C_BPartner_ID = bp.C_BPartner_ID
                WHERE s.space_type_id IN ('KITCHEN','BATHROOM','BEDROOM','LIVING','CORRIDOR')
                  AND p.C_BPartner_ID IS NOT NULL
                  AND p.C_BPartner_ID != ?
                """;
            List<String> coLeaks = new ArrayList<>();
            try (PreparedStatement ps = erp.prepareStatement(sql)) {
                ps.setInt(1, BPARTNER_DUPLEX);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        coLeaks.add(String.format("%s.%s → BPartner=%s",
                            rs.getString("space_type_id"),
                            rs.getString("mep_product_id"),
                            rs.getString("bpartner")));
                    }
                }
            }
            assertTrue(coLeaks.isEmpty(),
                "CO-only products leaked into RE MEP schedule:\n  " + String.join("\n  ", coLeaks));
        }
    }

    // ── W-BPARTNER-COMPLETENESS: all RE fixtures assigned ────────────────

    @Test
    @Order(4)
    @DisplayName("W-BPARTNER-COMPLETENESS: no RE generative product has NULL BPartner when it should be Duplex")
    void wBPartnerCompleteness() throws Exception {
        // Products that appear in RE space schedules must have C_BPartner_ID set (not NULL),
        // so the filter can enforce segregation. Universal products are the deliberate exception.
        // Expected NULL (universal): SPRINKLER, LIGHT, SUPPLY_DIFFUSER, DATA_POINT, EMERGENCY_LIGHT
        Set<String> knownUniversal = Set.of(
            "SPRINKLER", "LIGHT", "SUPPLY_DIFFUSER", "DATA_POINT", "EMERGENCY_LIGHT"
        );
        if (!Files.exists(Paths.get(ERP_DB))) {
            fail("ERP.db not found at " + ERP_DB);
        }
        try (Connection erp = DriverManager.getConnection("jdbc:sqlite:" + ERP_DB)) {
            String sql = """
                SELECT DISTINCT s.mep_product_id, p.C_BPartner_ID
                FROM ad_space_type_mep_bom s
                JOIN M_Product p ON p.product_id = s.mep_product_id
                WHERE s.space_type_id IN ('KITCHEN','BATHROOM','BEDROOM','LIVING','CORRIDOR',
                                          'UTILITY','TOILET','LAUNDRY')
                  AND p.C_BPartner_ID IS NULL
                """;
            List<String> untagged = new ArrayList<>();
            try (Statement st = erp.createStatement();
                 ResultSet rs = st.executeQuery(sql.replace("?", ""))) {
                // re-run with PreparedStatement
            }
            try (PreparedStatement ps = erp.prepareStatement(sql)) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String prod = rs.getString("mep_product_id");
                        if (!knownUniversal.contains(prod)) {
                            untagged.add(prod);
                        }
                    }
                }
            }
            assertTrue(untagged.isEmpty(),
                "RE schedule products missing C_BPartner_ID (should be Duplex):\n  "
                + String.join(", ", untagged)
                + "\nApply DV051_m_product_bpartner.sql to fix.");
        }
    }

    // ── Shared assertion ─────────────────────────────────────────────────

    private static void assertBPartnerRe(String label, String outputDb) throws Exception {
        if (!Files.exists(Paths.get(outputDb))) {
            fail(label + " output DB not found at " + outputDb + " — run pipeline first");
        }
        if (!Files.exists(Paths.get(ERP_DB))) {
            fail("ERP.db not found at " + ERP_DB);
        }
        try (Connection out = DriverManager.getConnection("jdbc:sqlite:" + outputDb);
             Connection erp = DriverManager.getConnection("jdbc:sqlite:" + ERP_DB)) {

            // Generative elements: guid contains '_MD_' (pattern from MEPDevicePlacer)
            String genSql = """
                SELECT em.guid, em.element_name, em.element_type
                FROM elements_meta em
                WHERE em.guid LIKE '%_MD_%'
                """;

            List<String> violations = new ArrayList<>();
            int total = 0;

            try (Statement st = out.createStatement();
                 ResultSet rs = st.executeQuery(genSql)) {
                while (rs.next()) {
                    total++;
                    String guid      = rs.getString("guid");
                    String elemName  = rs.getString("element_name");
                    String elemType  = rs.getString("element_type");

                    // Look up product by element_type (= product_id for generative devices)
                    if (elemType == null) continue;
                    try (PreparedStatement ps = erp.prepareStatement(
                            "SELECT C_BPartner_ID FROM M_Product WHERE product_id = ? OR Value = ?")) {
                        ps.setString(1, elemType);
                        ps.setString(2, elemType);
                        try (ResultSet pr = ps.executeQuery()) {
                            if (pr.next()) {
                                int bpId = pr.getInt("C_BPartner_ID");
                                boolean isNull = pr.wasNull();
                                if (!isNull && bpId != BPARTNER_DUPLEX) {
                                    violations.add(String.format(
                                        "guid=%s type=%s BPartner=%d (expected %d or NULL)",
                                        guid, elemType, bpId, BPARTNER_DUPLEX));
                                }
                            }
                        }
                    }
                }
            }

            assertTrue(total > 0,
                label + ": no generative elements found — pipeline may not have run");
            assertTrue(violations.isEmpty(),
                label + " W-BPARTNER-RE FAIL — " + violations.size()
                + " CO products in RE output:\n  " + String.join("\n  ", violations));
        }
    }
}
