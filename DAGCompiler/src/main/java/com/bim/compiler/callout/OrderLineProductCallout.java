package com.bim.compiler.callout;

import com.bim.compiler.topology.Discipline;
import com.bim.orm.BIMLogger;

import java.sql.*;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * OrderLine.Product callout — auto-insert discipline OrderLines for CO/IN buildings.
 *
 * <p>When a CO or IN building is compiled, this callout reads shared discipline BOMs
 * from ERP.db (M_BOM WHERE AD_Org_ID > 0) and creates sibling C_OrderLine rows in
 * the compile DB — one per discipline. ARC (seq=10) and STR (seq=20) come from
 * extraction, not callout.
 *
 * <p>This is the standard iDempiere C_OrderLine.M_Product_ID callout pattern:
 * when product changes, callout fires and auto-creates component lines from BOM.
 *
 * <p>// Implementing BBC.md §3.6.2a — Witness: W-DISC-CALLOUT-1
 * // Implementing DISC_VALIDATION_DB_SRS §10.4.11 T0.2
 *
 * <h3>Cardinal Rules — MEP_RECIPE Architecture</h3>
 * <ol>
 *   <li><b>MEP_RECIPE is abstract.</b> A recipe archetype encodes a PATTERN (standoff,
 *       chain geometry, piece sequence). It is building-type reusable, not per-instance.
 *       Never explode recipe runs into per-instance LEAF rows here — that destroys
 *       reusability and invents data that must come from IFC extraction.</li>
 *   <li><b>Validation Rules drive expression.</b> The final spatial expression of a
 *       recipe for a specific building is determined by DV rules (AD_Rule / M_BOM
 *       Validation Layer), not by the compilation callout. The callout only registers
 *       WHICH disciplines apply and in what quantity — not HOW they are placed.</li>
 *   <li><b>One DISCIPLINE row per discipline.</b> The compiled output is one
 *       host_type='DISCIPLINE' row per MEP discipline, with Qty from extraction counts
 *       (ad_sysconfig MEP_*_COUNT). LEAF children come only from the abstract
 *       *_SYSTEM BOM (e.g. FP_RISER, FP_SPRINKLER_LAYOUT) — never from recipe runs.</li>
 *   <li><b>Qty = element count, not recipe count.</b> The Qty on a DISCIPLINE row
 *       is the number of IFC elements extracted, not the number of MEP_RECIPE archetypes.
 *       Recipe count is a planning metric, not a compiled quantity.</li>
 * </ol>
 *
 * @see com.bim.compiler.bom.BomDropper
 * @see com.bim.ifctobom.IFCtoERP#buildMepBomRecipes
 */
public class OrderLineProductCallout {

    /**
     * Auto-create discipline OrderLines for a CO or IN building order.
     *
     * <p>Reads shared discipline BOMs from ERP.db (AD_Org_ID > 0) and inserts
     * C_OrderLine rows into the compile DB with correct AD_Org_ID and sequence.
     * Idempotent: skips if a discipline OrderLine already exists for this order.
     *
     * @param compileDb  connection to the compile DB (has C_Order, C_OrderLine)
     * @param erpDb      connection to ERP.db (has M_BOM with shared discipline recipes)
     * @param orderId    C_Order_ID (Value) in compile DB
     * @param productCategory  building category Value from M_Product_Category ("RE", "CO", "IN")
     * @return count of discipline lines inserted (0 if RE or already populated)
     */
    // Implementing BBC.md §3.6.2a — Witness: W-DISC-CALLOUT-1
    public static int onProductChanged(Connection compileDb, Connection erpDb,
                                       String orderId, String productCategory) throws SQLException {
        return onProductChanged(compileDb, erpDb, orderId, productCategory, null);
    }

    // Implementing DISC_VALIDATION_DB_SRS §10.4.11 T3.4 — category defaults + YAML override
    // CO=all 6, RE=[FP,ELEC,ACMV,CW,SP], IN=none. YAML remove_disciplines/add_disciplines for exceptions.
    // ERP.db MEP_RECIPE discipline registration — Witness: W-J1-RECIPE-FULL
    // Implementing DISC_VALIDATION_DB_SRS.md §6.12.2 recipe consumption

    /** Category default discipline sets. */
    private static final Set<String> CO_DEFAULT = Set.of("FP", "ELEC", "ACMV", "CW", "SP", "LPG");
    private static final Set<String> RE_DEFAULT = Set.of("FP", "ELEC", "ACMV", "CW", "SP");
    private static final Set<String> IN_DEFAULT = Set.of();

    /**
     * Overload with mep_disciplines whitelist — kept for backward compat.
     * If whitelist is non-null, uses whitelist. Otherwise uses category defaults.
     */
    public static int onProductChanged(Connection compileDb, Connection erpDb,
                                       String orderId, String productCategory,
                                       List<String> mepDisciplines) throws SQLException {
        // Determine discipline set: explicit whitelist or category default
        Set<String> disciplineSet;
        if (mepDisciplines != null) {
            disciplineSet = Set.copyOf(mepDisciplines);
        } else if ("CO".equals(productCategory)) {
            disciplineSet = CO_DEFAULT;
        } else if ("RE".equals(productCategory)) {
            disciplineSet = RE_DEFAULT;
        } else {
            // IN and others: no MEP default
            disciplineSet = IN_DEFAULT;
        }

        if (disciplineSet.isEmpty()) {
            BIMLogger.fine("CALLOUT", "No disciplines for category={} — skipping", productCategory);
            return 0;
        }

        // Read shared discipline BOMs from ERP.db WHERE AD_Org_ID > 0
        String selectBoms = "SELECT Value, Name, AD_Org_ID FROM M_BOM WHERE AD_Org_ID > 0 ORDER BY AD_Org_ID";

        // Find the root BUILDING C_OrderLine to use as parent
        Integer buildingLineId = findBuildingLineId(compileDb, orderId);

        int inserted = 0;

        try (PreparedStatement ps = erpDb.prepareStatement(selectBoms);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String bomValue = rs.getString("Value");
                int adOrgId = rs.getInt("AD_Org_ID");

                // Resolve discipline from AD_Org_ID
                Discipline disc = Discipline.fromAD_Org_ID(adOrgId);
                if (disc == null) {
                    BIMLogger.fine("CALLOUT", "Unknown AD_Org_ID={} for BOM {} — skipping", adOrgId, bomValue);
                    continue;
                }

                // Filter by discipline set
                if (!disciplineSet.contains(disc.name())) {
                    BIMLogger.fine("CALLOUT", "Discipline {} not in default set {} — skipping",
                            disc.name(), disciplineSet);
                    continue;
                }

                // §10.4.6.1 Class B: read MEP element count from ad_sysconfig (written by IFCtoBOM)
                // Implementing DISC_VALIDATION_DB_SRS.md §10.4.6.1 — Witness: W-DISC-SEP-2
                int mepQty = 0;
                String countStr = readSysconfig(compileDb, "MEP_" + disc.name() + "_COUNT");
                if (countStr != null) {
                    try { mepQty = Integer.parseInt(countStr); } catch (NumberFormatException ignored) {}
                }

                // If BomDropper already created this DISC line (via Add mutation), update Qty
                if (disciplineLineExists(compileDb, orderId, adOrgId)) {
                    updateDisciplineQty(compileDb, orderId, adOrgId, mepQty);
                    BIMLogger.fine("CALLOUT", "Discipline {} (AD_Org={}) exists — updated Qty={} for order {}",
                            disc.name(), adOrgId, mepQty, orderId);
                    inserted++;
                    continue;
                }

                // Sequence: 30 for FP (AD_Org=3), 40 for ELEC (4), 50 for ACMV (5), etc.
                int seq = adOrgId * 10;

                insertDisciplineLine(compileDb, orderId, buildingLineId, seq,
                        bomValue, disc.name(), adOrgId, mepQty);
                inserted++;

                BIMLogger.fine("CALLOUT", "Discipline callout: {} (AD_Org={}, seq={}, qty={}) inserted for order {}",
                        disc.name(), adOrgId, seq, mepQty, orderId);
            }
        }

        if (inserted > 0) {
            BIMLogger.info("CALLOUT", "Callout: {} discipline OrderLines inserted (category={}, default={})",
                    inserted, productCategory, disciplineSet);
        }
        return inserted;
    }

    /**
     * Apply YAML overrides from ad_sysconfig: REMOVE_DISCIPLINES and ADD_DISCIPLINES.
     * Called after onProductChanged to adjust the category default set.
     *
     * <p>REMOVE_DISCIPLINES=SP → deactivate SP OrderLine (IsActive='N')
     * <p>ADD_DISCIPLINES=FP → insert FP if not already present
     */
    public static void applyYamlOverrides(Connection compileDb, Connection erpDb,
                                           String orderId) throws SQLException {
        // REMOVE_DISCIPLINES
        String removeCsv = readSysconfig(compileDb, "REMOVE_DISCIPLINES");
        if (removeCsv != null) {
            for (String disc : removeCsv.split(",")) {
                disc = disc.trim();
                if (!disc.isEmpty()) {
                    int updated = deactivateDiscipline(compileDb, orderId, disc);
                    if (updated > 0) {
                        BIMLogger.info("CALLOUT", "YAML override: deactivated {} discipline OrderLine(s) for {}",
                                updated, disc);
                    }
                }
            }
        }

        // ADD_DISCIPLINES
        String addCsv = readSysconfig(compileDb, "ADD_DISCIPLINES");
        if (addCsv != null) {
            Integer buildingLineId = findBuildingLineId(compileDb, orderId);
            String selectBoms = "SELECT Value, AD_Org_ID FROM M_BOM WHERE AD_Org_ID > 0 ORDER BY AD_Org_ID";
            try (PreparedStatement ps = erpDb.prepareStatement(selectBoms);
                 ResultSet rs = ps.executeQuery()) {
                List<String> addList = Arrays.asList(addCsv.split(","));
                while (rs.next()) {
                    String bomValue = rs.getString("Value");
                    int adOrgId = rs.getInt("AD_Org_ID");
                    Discipline disc = Discipline.fromAD_Org_ID(adOrgId);
                    if (disc == null) continue;
                    if (!addList.contains(disc.name().trim())) continue;
                    if (disciplineLineExists(compileDb, orderId, adOrgId)) continue;
                    int seq = adOrgId * 10;
                    int mepQty = 0;
                    String countStr = readSysconfig(compileDb, "MEP_" + disc.name() + "_COUNT");
                    if (countStr != null) {
                        try { mepQty = Integer.parseInt(countStr); } catch (NumberFormatException ignored) {}
                    }
                    insertDisciplineLine(compileDb, orderId, buildingLineId, seq,
                            bomValue, disc.name(), adOrgId, mepQty);
                    BIMLogger.info("CALLOUT", "YAML override: added discipline {} (qty={}) for order {}", disc.name(), mepQty, orderId);
                }
            }
        }
    }

    /** Read a config value from ad_sysconfig. */
    private static String readSysconfig(Connection conn, String key) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT config_value FROM ad_sysconfig WHERE config_key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String val = rs.getString(1);
                    return (val != null && !val.isBlank()) ? val : null;
                }
            }
        } catch (SQLException e) {
            // Table may not exist
        }
        return null;
    }

    /** Deactivate discipline OrderLines by setting IsActive='N'. */
    private static int deactivateDiscipline(Connection conn, String orderId, String discipline) throws SQLException {
        String sql = "UPDATE C_OrderLine SET IsActive = 'N' "
                   + "WHERE C_Order_ID = ? AND Discipline = ? AND host_type = 'DISCIPLINE'";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, orderId);
            ps.setString(2, discipline);
            return ps.executeUpdate();
        }
    }

    /**
     * Parasitic qty walk — expand discipline C_OrderLines by reading shared BOM
     * children from ERP.db and inserting child C_OrderLines with qty but no placement.
     *
     * <p>For each DISCIPLINE OrderLine (AD_Org_ID >= 3), reads the M_BOM_Line
     * children from ERP.db and creates LEAF C_OrderLines with:
     * <ul>
     *   <li>qty from BOM line (demand record — "this building needs N sprinklers")</li>
     *   <li>dx/dy/dz = 0 (no spatial placement — parasitic elements attach to ARC rooms)</li>
     *   <li>host_type = LEAF, AD_Org_ID from parent discipline</li>
     * </ul>
     *
     * <p>// Implementing BBC.md §3.6 — Witness: W-DISC-QTY-1
     * // Implementing DISC_VALIDATION_DB_SRS §10.4.11 T0.3
     *
     * @param compileDb  connection to the compile DB
     * @param erpDb      connection to ERP.db
     * @param orderId    C_Order_ID (Value) in compile DB
     * @return count of child lines inserted
     */
    // Implementing BBC.md §3.6 — Witness: W-DISC-QTY-1
    public static int expandDisciplineLines(Connection compileDb, Connection erpDb,
                                             String orderId) throws SQLException {
        // Read C_BPartner_ID from C_Order header (iDempiere: order owns the BPartner)
        int bpartnerId = 0;
        try (PreparedStatement bpPs = compileDb.prepareStatement(
                "SELECT C_BPartner_ID FROM C_Order WHERE Value = ?")) {
            bpPs.setString(1, orderId);
            try (ResultSet rs = bpPs.executeQuery()) {
                if (rs.next()) bpartnerId = rs.getInt(1);
            }
        }
        // Find all DISCIPLINE OrderLines for this order
        String selectDisc = "SELECT C_OrderLine_ID, family_ref, AD_Org_ID, Discipline "
                          + "FROM C_OrderLine WHERE C_Order_ID = ? AND host_type = 'DISCIPLINE' "
                          + "ORDER BY Line";

        // Find max Line sequence in the order (to append after existing lines)
        int maxSeq = 0;
        try (PreparedStatement ps = compileDb.prepareStatement(
                "SELECT MAX(Line) FROM C_OrderLine WHERE C_Order_ID = ?")) {
            ps.setString(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) maxSeq = rs.getInt(1);
            }
        }

        int inserted = 0;

        try (PreparedStatement ps = compileDb.prepareStatement(selectDisc)) {
            ps.setString(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int parentLineId = rs.getInt("C_OrderLine_ID");
                    String bomValue = rs.getString("family_ref");
                    int adOrgId = rs.getInt("AD_Org_ID");
                    String discipline = rs.getString("Discipline");

                    // MEP recipe bridge — Witness: W-J1-RECIPE-LINK
                    // Implementing DISC_VALIDATION_DB_SRS.md §6.12.2 recipe consumption
                    // C_BPartner_ID filter: every BOM has a C_BPartner_ID (no NULLs)
                    String selectChildren =
                              "SELECT bl.M_BOM_Line_ID, bl.child_product_id, bl.qty, bl.dz "
                            + "FROM M_BOM_Line bl "
                            + "JOIN M_BOM b ON bl.M_BOM_ID = b.M_BOM_ID "
                            + "WHERE b.Value = ? AND b.C_BPartner_ID = ? AND bl.IsActive = 'Y' "
                            + "ORDER BY bl.sequence";

                    try (PreparedStatement childPs = erpDb.prepareStatement(selectChildren)) {
                        childPs.setString(1, bomValue);
                        childPs.setInt(2, bpartnerId);
                        try (ResultSet childRs = childPs.executeQuery()) {
                            while (childRs.next()) {
                                int erpBomLineId = childRs.getInt("M_BOM_Line_ID");
                                String childProduct = childRs.getString("child_product_id");
                                int qty = childRs.getInt("qty");
                                double recipeDz = childRs.getDouble("dz");

                                maxSeq += 10;
                                insertChildLine(compileDb, orderId, parentLineId, maxSeq,
                                        childProduct, discipline, adOrgId, qty,
                                        erpBomLineId, recipeDz);
                                inserted++;

                                BIMLogger.fine("CALLOUT", "Parasitic qty: {} qty={} dz={} bomLine={} (disc={}, AD_Org={}) for order {}",
                                        childProduct, qty, recipeDz, erpBomLineId, discipline, adOrgId, orderId);
                            }
                        }
                    }
                }
            }
        }

        if (inserted > 0) {
            BIMLogger.info("CALLOUT", "Parasitic qty walk: {} child lines inserted for order {}",
                    inserted, orderId);
        }
        return inserted;
    }

    /**
     * Insert a parasitic child C_OrderLine — qty passthrough with ERP.db recipe linkage.
     * M_BOM_Line_ID and dz come from the ERP.db BOM line (W-J1-RECIPE-LINK).
     */
    private static void insertChildLine(Connection conn, String orderId,
                                         int parentLineId, int seq,
                                         String childProduct, String discipline,
                                         int adOrgId, int qty,
                                         int erpBomLineId, double dz) throws SQLException {
        String sql = "INSERT INTO C_OrderLine "
                   + "(C_Order_ID, Parent_OrderLine_ID, Line, family_ref, host_type, "
                   + " m_product_category_id, M_BOM_Line_ID, dx, dy, dz, M_Product_ID, Discipline, AD_Org_ID, Qty, "
                   + " locator_ref) "
                   + "VALUES (?, ?, ?, ?, 'LEAF', ?, ?, 0, 0, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, orderId);
            ps.setInt(2, parentLineId);
            ps.setInt(3, seq);
            ps.setString(4, childProduct);         // family_ref
            ps.setString(5, discipline);            // m_product_category_id = discipline code
            if (erpBomLineId > 0) ps.setInt(6, erpBomLineId);
            else ps.setNull(6, java.sql.Types.INTEGER);
            ps.setDouble(7, dz);                   // standoff from ERP.db recipe
            ps.setString(8, childProduct);         // M_Product_ID
            ps.setString(9, discipline);            // Discipline
            ps.setInt(10, adOrgId);                // AD_Org_ID
            ps.setInt(11, qty);                    // Qty from BOM
            ps.setString(12, discipline + "." + childProduct);  // locator_ref
            ps.executeUpdate();
        }
    }

    /**
     * Find the root BUILDING C_OrderLine_ID for this order (parent for discipline lines).
     */
    private static Integer findBuildingLineId(Connection conn, String orderId) throws SQLException {
        String sql = "SELECT C_OrderLine_ID FROM C_OrderLine "
                   + "WHERE C_Order_ID = ? AND host_type = 'BUILDING' "
                   + "ORDER BY Line LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : null;
            }
        }
    }

    /** Update Qty on an existing discipline OrderLine (BomDropper creates with Qty=0). */
    private static void updateDisciplineQty(Connection conn, String orderId,
                                             int adOrgId, int qty) throws SQLException {
        String sql = "UPDATE C_OrderLine SET Qty = ? "
                   + "WHERE C_Order_ID = ? AND AD_Org_ID = ? AND host_type = 'DISCIPLINE'";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, qty);
            ps.setString(2, orderId);
            ps.setInt(3, adOrgId);
            ps.executeUpdate();
        }
    }

    /**
     * Check if a discipline OrderLine already exists for this order + AD_Org_ID.
     */
    private static boolean disciplineLineExists(Connection conn, String orderId, int adOrgId) throws SQLException {
        String sql = "SELECT 1 FROM C_OrderLine WHERE C_Order_ID = ? AND AD_Org_ID = ? AND host_type = 'DISCIPLINE' LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, orderId);
            ps.setInt(2, adOrgId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * Insert a single discipline C_OrderLine into the compile DB.
     * host_type=DISCIPLINE, dx/dy/dz=0 (no placement — parasitic).
     * Qty from ad_sysconfig MEP count (0 = fill-all fallback).
     */
    private static void insertDisciplineLine(Connection conn, String orderId,
                                              Integer parentLineId, int seq,
                                              String bomValue, String discipline,
                                              int adOrgId, int qty) throws SQLException {
        String sql = "INSERT INTO C_OrderLine "
                   + "(C_Order_ID, Parent_OrderLine_ID, Line, family_ref, host_type, "
                   + " m_product_category_id, dx, dy, dz, M_Product_ID, Discipline, AD_Org_ID, Qty, "
                   + " locator_ref) "
                   + "VALUES (?, ?, ?, ?, 'DISCIPLINE', ?, 0, 0, 0, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, orderId);
            if (parentLineId != null) ps.setInt(2, parentLineId);
            else ps.setNull(2, Types.INTEGER);
            ps.setInt(3, seq);
            ps.setString(4, bomValue);       // family_ref = discipline BOM Value
            ps.setString(5, discipline);     // m_product_category_id = discipline code
            ps.setString(6, bomValue);       // M_Product_ID = discipline BOM Value
            ps.setString(7, discipline);     // Discipline
            ps.setInt(8, adOrgId);           // AD_Org_ID
            ps.setInt(9, qty);               // Qty from extraction MEP count
            ps.setString(10, discipline);    // locator_ref = discipline code
            ps.executeUpdate();
        }
    }
}
