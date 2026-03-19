package com.bim.designer.dao;

import java.sql.Connection;
import java.util.List;
import java.util.Map;

/**
 * DAO interface for the BIM Report Engine (4D–7D + KPI + compliance).
 *
 * <p>Each method accepts explicit {@link Connection} parameters so callers
 * control connection lifecycle. The split-DB architecture means different
 * queries hit different databases (BOM, component library, validation,
 * work output).
 *
 * // Implementing CORE_SRS.md §2.4 — Witness: W-REPORT-DAO
 */
public interface ReportDAO {

    // ── 4D Schedule ──────────────────────────────────────────────────────

    record GanttTask(String id, String name, String phase,
                     int sequence, int durationDays, String dependency) {}

    List<GanttTask> constructionSequence(Connection bomConn, String buildingId);

    // ── 5D Cost ──────────────────────────────────────────────────────────

    record CostLine(String discipline, String category, String productName,
                    int qty, double unitCost, double totalCost, String uom) {}

    List<CostLine> costBreakdown(Connection bomConn, Connection compConn,
                                 String buildingId);

    // ── 6D Carbon ────────────────────────────────────────────────────────

    record CarbonLine(String element, String material, int qty,
                      double carbonPerUnit, double totalCarbon) {}

    List<CarbonLine> carbonFootprint(Connection bomConn, Connection compConn,
                                     String buildingId);

    // ── 7D Assets ────────────────────────────────────────────────────────

    record AssetRecord(String guid, String type, String location,
                       String floor, String system, String manufacturer,
                       String maintenanceInterval) {}

    List<AssetRecord> assetRegister(Connection bomConn, String buildingId);

    // ── KPI Dashboard ────────────────────────────────────────────────────

    record KPI(String name, double value, double target,
               String unit, String status) {}

    List<KPI> kpiDashboard(Connection bomConn, Connection valConn,
                           String portfolioId);

    // ── Board Status ─────────────────────────────────────────────────────

    record BoardCard(String orderId, String name, String status,
                     double progress, int blockers, double estCost) {}

    List<BoardCard> boardStatus(Connection workConn);

    // ── Compliance Matrix ────────────────────────────────────────────────

    record ComplianceResult(String ruleId, String ruleName,
                            String jurisdiction, String verdict,
                            String citation, String detail) {}

    List<ComplianceResult> complianceMatrix(Connection valConn,
                                           String buildingId, String jurisdiction);

    // ── Milestone Events (social/notification) ───────────────────────────

    record MilestoneEvent(String type, String headline, String body,
                          Map<String, String> metrics) {}

    MilestoneEvent milestoneEvent(Connection bomConn, String buildingId);
}
