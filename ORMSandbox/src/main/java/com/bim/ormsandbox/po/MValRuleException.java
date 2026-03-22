package com.bim.ormsandbox.po;

import com.bim.orm.ModelQuery;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Model layer for {@code AD_Val_Rule_Exception} (U6).
 *
 * <p>Provides query methods for validation rule exceptions — user overrides
 * on specific C_OrderLines that acknowledge WARN-level validation findings.
 *
 * // Implementing S60_ERP_ALIGNMENT.md §U6 — Witness: W-S60-EXCEPT
 */
public class MValRuleException extends X_AD_Val_Rule_Exception {

    public MValRuleException(Connection conn) { super(conn); }

    /** All exceptions for a given C_OrderLine. */
    public static List<MValRuleException> getByOrderLine(Connection conn, int orderLineId)
            throws SQLException {
        return new ModelQuery<>(conn, MValRuleException::new, Table_Name)
            .where(COLUMNNAME_C_OrderLine_ID + " = ?", orderLineId)
            .list();
    }

    /** All exceptions for a given validation rule (across all order lines). */
    public static List<MValRuleException> getByRule(Connection conn, int ruleId)
            throws SQLException {
        return new ModelQuery<>(conn, MValRuleException::new, Table_Name)
            .where(COLUMNNAME_ad_val_rule_id + " = ?", ruleId)
            .list();
    }

    /**
     * Check whether a specific (C_OrderLine, AD_Val_Rule) pair has an exception.
     * Used by validation pipeline to skip WARN rules the user has acknowledged.
     */
    public static boolean isExcepted(Connection conn, int orderLineId, int ruleId)
            throws SQLException {
        return new ModelQuery<>(conn, MValRuleException::new, Table_Name)
            .where(COLUMNNAME_C_OrderLine_ID + " = ? AND " + COLUMNNAME_ad_val_rule_id + " = ?",
                   orderLineId, ruleId)
            .first()
            .isPresent();
    }

    /**
     * Get the set of excepted rule IDs for a given C_OrderLine.
     * Efficient batch lookup for validation pipeline processing.
     */
    public static Set<Integer> getExceptedRuleIds(Connection conn, int orderLineId)
            throws SQLException {
        return getByOrderLine(conn, orderLineId).stream()
                .map(MValRuleException::getValRuleId)
                .collect(Collectors.toSet());
    }
}
