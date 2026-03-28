package com.bim.backoffice.report;

import com.bim.backoffice.dao.CostDAO;
import com.bim.orm.BIMLogger;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * BIM-RPT-14: Project Profit & Loss Summary.
 *
 * <p>Contract sum = estimated cost from BOM x library rates (bid cost).
 * Variation orders stubbed at 0 until iDempiere C_Order actuals wired.
 * This is DRAFT until actuals exist.
 *
 * // Implementing REPORTING_ENGINE_SRS.md §3 BIM-RPT-14 — Witness: W-RPT-PL
 */
public class ProfitLossReport {

    private static final String TAG = "ProfitLossReport";

    private static final String DRAFT_NOTE =
            "NOTE: Contract sum = BOM estimate (not actual contract). " +
            "Variation orders stubbed. P&L is DRAFT until iDempiere actuals wired.";

    public record PnlOutput(String buildingId,
                            double contractSum, double variationOrders,
                            double materialCost, double laborCost, double equipmentCost,
                            double costToComplete, double margin, double marginPct,
                            boolean isDraft, String draftNote,
                            String plainText) {}

    /**
     * Generate P&L report from CostDAO cost breakdown.
     */
    public PnlOutput generate(Connection bomConn, Connection compLibConn,
                              String buildingId) throws SQLException {
        CostDAO costDao = new CostDAO();
        CostDAO.CostSummary cost = costDao.costBreakdown(bomConn, compLibConn, buildingId);

        double contractSum = cost.grandTotal(); // bid cost from BOM estimates
        double variationOrders = 0.0;           // stub — requires iDempiere C_Order
        double materialCost = cost.materialTotal();
        double laborCost = cost.laborTotal();
        double equipmentCost = cost.equipmentTotal();
        double costToComplete = materialCost + laborCost + equipmentCost;
        double margin = contractSum - costToComplete;
        double marginPct = contractSum > 0 ? (margin / contractSum) * 100 : 0;

        // Build plain text
        StringBuilder sb = new StringBuilder();
        sb.append("PROJECT PROFIT & LOSS — ").append(buildingId).append("\n");
        sb.append("═".repeat(60)).append("\n\n");
        sb.append("⚠ DRAFT — ").append(DRAFT_NOTE).append("\n\n");
        sb.append("REVENUE\n");
        sb.append("─".repeat(40)).append("\n");
        sb.append(String.format("  Contract Sum:          RM %,.2f%n", contractSum));
        sb.append(String.format("  Variation Orders:      RM %,.2f  [STUB]%n", variationOrders));
        sb.append(String.format("  Total Revenue:         RM %,.2f%n", contractSum + variationOrders));
        sb.append("\n");
        sb.append("COST TO COMPLETE\n");
        sb.append("─".repeat(40)).append("\n");
        sb.append(String.format("  Material:              RM %,.2f  (%.1f%%)%n",
                materialCost, cost.materialPct()));
        sb.append(String.format("  Labor:                 RM %,.2f  (%.1f%%)%n",
                laborCost, cost.laborPct()));
        sb.append(String.format("  Equipment:             RM %,.2f  (%.1f%%)%n",
                equipmentCost, cost.equipmentPct()));
        sb.append(String.format("  Total Cost:            RM %,.2f%n", costToComplete));
        sb.append("\n");
        sb.append("MARGIN\n");
        sb.append("─".repeat(40)).append("\n");
        sb.append(String.format("  Gross Margin:          RM %,.2f%n", margin));
        sb.append(String.format("  Margin %%:              %.1f%%%n", marginPct));

        BIMLogger.info(TAG, "P&L report: contract={:.2f}, cost={:.2f}, margin={:.1f}%",
                contractSum, costToComplete, marginPct);

        return new PnlOutput(buildingId, contractSum, variationOrders,
                materialCost, laborCost, equipmentCost, costToComplete,
                margin, marginPct, true, DRAFT_NOTE, sb.toString());
    }
}
