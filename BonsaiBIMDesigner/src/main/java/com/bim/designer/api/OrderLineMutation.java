package com.bim.designer.api;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

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
 * <p>// Implementing ProjectOrderBlueprint.md §14.3 Session B — Witness: W-DM-FP-VAL-1
 */
public interface OrderLineMutation {

    /**
     * Propose OrderLines for this discipline based on AD rules.
     *
     * @param woConn   work_output.db connection (C_Order + C_OrderLine)
     * @param ruleDb   disc_validation.db connection (ad_space_type_mep_bom)
     * @param orderId  C_Order_ID to propose lines for
     * @return list of proposed order lines (empty if no rules match)
     */
    List<ProposedOrderLine> propose(Connection woConn, Connection ruleDb,
                                    String orderId) throws SQLException;

    /**
     * The discipline code this mutation covers (FP, ELEC, ACMV).
     */
    String discipline();
}
