package com.bim.designer.api;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * Suggestion engine interface — rules propose OrderLines, architect disposes.
 *
 * <p>Each implementation covers one discipline (FP, ELEC, ACMV). The propose()
 * method reads the current order state and AD rules, then returns proposed lines
 * without modifying the database. The caller (OrderMutationService) decides
 * whether to persist them.
 *
 * <p>Three-state lifecycle (§13.2): Absent → Proposed → Accepted.
 * propose() transitions from Absent to Proposed. Architect acceptance
 * transitions from Proposed to Accepted.
 *
 * <p>// Implementing ProjectOrderBlueprint.md §14.3 Session C — Witness: W-RULEPACK-1
 */
public interface OrderLineMutation {

    // Implementing ProjectOrderBlueprint.md §14.3 Session C — Witness: W-RULEPACK-1
    // Jurisdiction → pack_id mapping for rule pack filtering.
    Map<String, List<String>> JURISDICTION_PACKS = Map.of(
            "MY", List.of("BASE", "UBBL-2024", "NFPA-13"),
            "US", List.of("BASE", "IBC-2021", "NFPA-13"),
            "SG", List.of("BASE", "BCA-2019", "NFPA-13"),
            "AU", List.of("BASE", "NCC-2022", "NFPA-13")
    );

    /**
     * Resolve jurisdiction code to list of pack_ids.
     * Unknown jurisdictions get BASE + NFPA-13 as fallback.
     */
    static List<String> packsForJurisdiction(String jurisdiction) {
        if (jurisdiction == null) return List.of();
        return JURISDICTION_PACKS.getOrDefault(jurisdiction,
                List.of("BASE", "NFPA-13"));
    }

    /**
     * Propose OrderLines for this discipline based on AD rules.
     * Backward-compatible: no pack filter (all rules apply).
     *
     * @param woConn   work_output.db connection (C_Order + C_OrderLine)
     * @param ruleDb   disc_validation.db connection (ad_space_type_mep_bom)
     * @param orderId  C_Order_ID to propose lines for
     * @return list of proposed order lines (empty if no rules match)
     */
    default List<ProposedOrderLine> propose(Connection woConn, Connection ruleDb,
                                            String orderId) throws SQLException {
        return propose(woConn, ruleDb, orderId, List.of());
    }

    /**
     * Propose OrderLines filtered by rule pack.
     *
     * @param woConn   work_output.db connection (C_Order + C_OrderLine)
     * @param ruleDb   disc_validation.db connection (ad_space_type_mep_bom)
     * @param orderId  C_Order_ID to propose lines for
     * @param packIds  rule packs to include (empty = all packs, no filter)
     * @return list of proposed order lines (empty if no rules match)
     */
    List<ProposedOrderLine> propose(Connection woConn, Connection ruleDb,
                                    String orderId, List<String> packIds) throws SQLException;

    /**
     * The discipline code this mutation covers (FP, ELEC, ACMV).
     */
    String discipline();
}
