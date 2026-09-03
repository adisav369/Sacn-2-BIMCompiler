package com.bim.backoffice.report;

import com.bim.backoffice.dao.CostDAO;
import com.bim.backoffice.dao.CostDAO.CostLine;
import com.bim.backoffice.dao.CostDAO.CostSummary;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;

/**
 * BIM-RPT-03: CIDB IBS (Industrialised Building System) Content Score.
 *
 * <p>Computes a weighted IBS score based on prefabricated content by component.
 * CIDB IBS scoring updated 2022 — weights marked RESEARCHED status until
 * verified against latest CIDB circular.
 *
 * <p>Components and weights:
 * <ul>
 *   <li>Structural system (STR) — weight 50</li>
 *   <li>Wall system (ARC) — weight 20</li>
 *   <li>Floor/roof system (ARC slab+roof) — weight 10</li>
 *   <li>Other (MEP, finishes) — weight 20</li>
 * </ul>
 *
 * <p>Each component: IBS % = prefab items cost / total cost for that component.
 * Overall IBS = sum(weight x component IBS%) / 100.
 * Rating: >=70 Full IBS, >=50 Moderate, <50 Conventional.
 *
 * // Implementing REPORTING_ENGINE_SRS.md §3 BIM-RPT-03 — Witness: W-RPT-IBS
 */
public class IBSScoreReport {

    // ── Output records ──────────────────────────────────────────────

    public record ReportOutput(
            String reportId,
            String title,
            String buildingId,
            String generatedDate,
            List<ComponentScore> components,
            double overallIBS,
            String rating,
            String weightsStatus,
            String plainText
    ) {}

    public record ComponentScore(String componentName, int weight,
                                 double totalCost, double prefabCost,
                                 double ibsPercent) {}

    // ── IBS component weights (RESEARCHED — pending CIDB 2022 verification) ──

    private static final int WEIGHT_STRUCTURAL = 50;
    private static final int WEIGHT_WALL       = 20;
    private static final int WEIGHT_FLOOR_ROOF = 10;
    private static final int WEIGHT_OTHER      = 20;

    // ── Prefab keywords — products containing these are considered IBS ──
    // In a real system this would be an attribute on M_Product. For now,
    // we use product name heuristics from CIDB classification.

    private static final Set<String> PREFAB_KEYWORDS = Set.of(
            "precast", "prefab", "ibs", "panel", "modular",
            "precas", "hollow", "slab_precast", "wall_panel"
    );

    // ── Discipline classification ───────────────────────────────────

    private enum IBSComponent {
        STRUCTURAL, WALL, FLOOR_ROOF, OTHER
    }

    private static IBSComponent classify(CostLine line) {
        String disc = line.discipline() != null ? line.discipline().toUpperCase() : "";
        String name = line.productName() != null ? line.productName().toLowerCase() : "";
        if (disc.equals("STR")) return IBSComponent.STRUCTURAL;
        if (disc.equals("ARC") || disc.equals("ARCH")) {
            if (name.contains("wall") || name.contains("brick") || name.contains("block")) {
                return IBSComponent.WALL;
            }
            if (name.contains("slab") || name.contains("roof") || name.contains("floor")) {
                return IBSComponent.FLOOR_ROOF;
            }
            return IBSComponent.WALL; // default ARC → wall system
        }
        return IBSComponent.OTHER;
    }

    private static boolean isPrefab(CostLine line) {
        String name = line.productName() != null ? line.productName().toLowerCase() : "";
        for (String kw : PREFAB_KEYWORDS) {
            if (name.contains(kw)) return true;
        }
        return false;
    }

    // ── Generate ────────────────────────────────────────────────────

    public ReportOutput generate(Connection bomConn, Connection compLibConn,
                                 String buildingId) throws SQLException {
        CostDAO dao = new CostDAO();
        CostSummary summary = dao.costBreakdown(bomConn, compLibConn, buildingId);

        // Accumulate totals and prefab costs per component
        Map<IBSComponent, Double> totalByComp  = new EnumMap<>(IBSComponent.class);
        Map<IBSComponent, Double> prefabByComp = new EnumMap<>(IBSComponent.class);
        for (IBSComponent c : IBSComponent.values()) {
            totalByComp.put(c, 0.0);
            prefabByComp.put(c, 0.0);
        }

        for (CostLine line : summary.lines()) {
            IBSComponent comp = classify(line);
            totalByComp.merge(comp, line.totalCost(), Double::sum);
            if (isPrefab(line)) {
                prefabByComp.merge(comp, line.totalCost(), Double::sum);
            }
        }

        // Compute per-component IBS %
        List<ComponentScore> components = new ArrayList<>();
        components.add(score("Structural System", WEIGHT_STRUCTURAL,
                totalByComp.get(IBSComponent.STRUCTURAL),
                prefabByComp.get(IBSComponent.STRUCTURAL)));
        components.add(score("Wall System", WEIGHT_WALL,
                totalByComp.get(IBSComponent.WALL),
                prefabByComp.get(IBSComponent.WALL)));
        components.add(score("Floor/Roof System", WEIGHT_FLOOR_ROOF,
                totalByComp.get(IBSComponent.FLOOR_ROOF),
                prefabByComp.get(IBSComponent.FLOOR_ROOF)));
        components.add(score("Other (MEP, Finishes)", WEIGHT_OTHER,
                totalByComp.get(IBSComponent.OTHER),
                prefabByComp.get(IBSComponent.OTHER)));

        // Overall IBS = sum(weight x component IBS%) / 100
        double overallIBS = 0;
        for (ComponentScore cs : components) {
            overallIBS += cs.weight() * cs.ibsPercent() / 100.0;
        }

        String rating;
        if (overallIBS >= 70) rating = "Full IBS";
        else if (overallIBS >= 50) rating = "Moderate IBS";
        else rating = "Conventional";

        // Plain text
        StringBuilder sb = new StringBuilder();
        sb.append("CIDB IBS CONTENT SCORE — ").append(buildingId).append("\n");
        sb.append("Standard: CIDB IBS Scoring (2022)\n");
        sb.append("Date: ").append(LocalDate.now()).append("\n");
        sb.append("Weights Status: RESEARCHED (pending CIDB 2022 circular verification)\n\n");

        sb.append(String.format("  %-25s %6s %12s %12s %8s%n",
                "Component", "Weight", "Total Cost", "Prefab Cost", "IBS %"));
        sb.append("  " + "-".repeat(65) + "\n");
        for (ComponentScore cs : components) {
            sb.append(String.format("  %-25s %6d %12.2f %12.2f %7.1f%%%n",
                    cs.componentName(), cs.weight(),
                    cs.totalCost(), cs.prefabCost(), cs.ibsPercent()));
        }
        sb.append("  " + "-".repeat(65) + "\n");
        sb.append(String.format("  Overall IBS Score: %.1f%%%n", overallIBS));
        sb.append(String.format("  Rating: %s%n", rating));

        return new ReportOutput(
                "BIM-RPT-03",
                "CIDB IBS Content Score",
                buildingId,
                LocalDate.now().toString(),
                components,
                overallIBS,
                rating,
                "RESEARCHED",
                sb.toString()
        );
    }

    private static ComponentScore score(String name, int weight,
                                        double total, double prefab) {
        double pct = total > 0 ? (prefab / total) * 100.0 : 0.0;
        return new ComponentScore(name, weight, total, prefab, pct);
    }
}
