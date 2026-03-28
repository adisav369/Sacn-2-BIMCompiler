package com.bim.cobol.forge.rebar;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Beam reinforcement calculation per MS 1347:2020.
 *
 * // Implementing FORGE_SUITE_SRS.md §10 Phase 7 — Witness: W-FORGE-9
 */
public final class BeamReinforcement {
    private BeamReinforcement() {}

    private static final double MIN_TENSION_REINFORCEMENT_RATIO = 0.0013;
    private static final double MIN_COMPRESSION_REINFORCEMENT_RATIO = 0.002;

    /**
     * Calculate beam reinforcement.
     *
     * @param widthMm  beam width in mm
     * @param heightMm beam overall height in mm
     * @param lengthMm beam span in mm
     * @param grade    concrete grade
     * @param exposure exposure class
     * @return fabrication parameter map
     */
    public static Map<String, Double> calculate(double widthMm, double heightMm,
            double lengthMm, ConcreteGrade grade, ExposureClass exposure) {

        int cover = CoverRequirements.getCover(exposure, "BEAM");

        // Bar selection based on height
        BarDiameter mainBar;
        if (heightMm <= 400) {
            mainBar = BarDiameter.T16;
        } else if (heightMm <= 600) {
            mainBar = BarDiameter.T20;
        } else {
            mainBar = BarDiameter.T25;
        }

        // Stirrup bar selection
        BarDiameter stirrupBar = (heightMm >= 450) ? BarDiameter.T10 : BarDiameter.T8;

        // Effective depth
        double effectiveDepth = heightMm - cover - stirrupBar.mm() - mainBar.mm() / 2.0;

        // Max stirrup spacing = 0.75 × effective depth
        double maxStirrupSpacing = 0.75 * effectiveDepth;

        // Minimum tension area (bottom bars)
        double minTensionArea = MIN_TENSION_REINFORCEMENT_RATIO * widthMm * effectiveDepth;
        double mainBarArea = mainBar.areaMm2();
        int bottomBars = Math.max(2, (int) Math.ceil(minTensionArea / mainBarArea));

        // Minimum compression area (top bars)
        double minCompressionArea = MIN_COMPRESSION_REINFORCEMENT_RATIO * widthMm * effectiveDepth;
        int topBars = Math.max(2, (int) Math.ceil(minCompressionArea / mainBarArea));

        // Stirrup spacing — use 0.75d or 300mm, whichever is smaller
        double stirrupSpacing = Math.min(maxStirrupSpacing, 300);
        stirrupSpacing = Math.floor(stirrupSpacing);

        // Stirrup count along length
        double stirrupCount = Math.floor(lengthMm / stirrupSpacing) + 1;

        // Actual reinforcement ratio
        double actualTensionArea = bottomBars * mainBarArea;
        double reinforcementRatio = actualTensionArea / (widthMm * effectiveDepth);

        // Total weight
        double bottomBarLength = bottomBars * lengthMm / 1000.0;
        double topBarLength = topBars * lengthMm / 1000.0;
        double stirrupPerimeter = 2 * (widthMm - 2 * cover + heightMm - 2 * cover) + 200; // hooks
        double stirrupTotalLength = stirrupCount * stirrupPerimeter / 1000.0;
        double totalWeight = bottomBarLength * mainBar.weightKgPerM()
                           + topBarLength * mainBar.weightKgPerM()
                           + stirrupTotalLength * stirrupBar.weightKgPerM();

        Map<String, Double> fab = new LinkedHashMap<>();
        fab.put("main_bar_diameter", (double) mainBar.mm());
        fab.put("bottom_bar_count", (double) bottomBars);
        fab.put("top_bar_count", (double) topBars);
        fab.put("stirrup_bar_diameter", (double) stirrupBar.mm());
        fab.put("stirrup_spacing", stirrupSpacing);
        fab.put("stirrup_count", stirrupCount);
        fab.put("cover_mm", (double) cover);
        fab.put("effective_depth_mm", effectiveDepth);
        fab.put("total_weight_kg", totalWeight);
        fab.put("reinforcement_ratio", reinforcementRatio);
        return fab;
    }
}
