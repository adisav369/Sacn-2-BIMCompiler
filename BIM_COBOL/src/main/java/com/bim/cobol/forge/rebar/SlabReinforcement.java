package com.bim.cobol.forge.rebar;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Slab reinforcement calculation per MS 1347:2020.
 *
 * // Implementing FORGE_SUITE_SRS.md §10 Phase 7 — Witness: W-FORGE-9
 */
public final class SlabReinforcement {
    private SlabReinforcement() {}

    private static final double MIN_REINFORCEMENT_RATIO = 0.0013;
    private static final int MAX_SPACING_MAIN = 300;
    private static final int MAX_SPACING_DIST = 400;

    /**
     * Calculate slab reinforcement.
     *
     * @param widthMm  slab width in mm (bar-count direction for main bars)
     * @param depthMm  slab depth in mm (bar-count direction for distribution bars)
     * @param thicknessMm slab thickness in mm
     * @param grade    concrete grade
     * @param exposure exposure class
     * @return fabrication parameter map
     */
    public static Map<String, Double> calculate(double widthMm, double depthMm,
            double thicknessMm, ConcreteGrade grade, ExposureClass exposure) {

        int cover = CoverRequirements.getCover(exposure, "SLAB");

        // Bar selection based on thickness
        BarDiameter mainBar;
        BarDiameter distBar;
        if (thicknessMm <= 150) {
            mainBar = BarDiameter.T10;
            distBar = BarDiameter.T8;
        } else if (thicknessMm <= 250) {
            mainBar = BarDiameter.T12;
            distBar = BarDiameter.T10;
        } else {
            mainBar = BarDiameter.T16;
            distBar = BarDiameter.T12;
        }

        // Effective depth = thickness - cover - half main bar diameter
        double effectiveDepth = thicknessMm - cover - mainBar.mm() / 2.0;

        // Minimum steel area per metre width
        double minSteelArea = MIN_REINFORCEMENT_RATIO * 1000 * thicknessMm;

        // Main bar spacing
        double mainBarArea = mainBar.areaMm2();
        double mainSpacing = Math.min(
            Math.min(mainBarArea / minSteelArea * 1000, MAX_SPACING_MAIN),
            3 * thicknessMm
        );
        mainSpacing = Math.floor(mainSpacing); // round down to integer

        // Distribution bar spacing
        double distSpacing = Math.min(MAX_SPACING_DIST, 3 * thicknessMm);
        distSpacing = Math.floor(distSpacing);

        // Bar counts
        double mainBarCount = Math.floor(widthMm / mainSpacing) + 1;
        double distBarCount = Math.floor(depthMm / distSpacing) + 1;

        // Actual reinforcement ratio (main bars only, per metre width)
        double actualSteelArea = (mainBarArea / mainSpacing) * 1000;
        double reinforcementRatio = actualSteelArea / (1000 * thicknessMm);

        // Total weight: main bars along depth direction + distribution bars along width direction
        double mainBarTotalLength = mainBarCount * depthMm / 1000.0;  // metres
        double distBarTotalLength = distBarCount * widthMm / 1000.0;  // metres
        double totalWeight = mainBarTotalLength * mainBar.weightKgPerM()
                           + distBarTotalLength * distBar.weightKgPerM();

        Map<String, Double> fab = new LinkedHashMap<>();
        fab.put("main_bar_diameter", (double) mainBar.mm());
        fab.put("main_bar_spacing", mainSpacing);
        fab.put("main_bar_count", mainBarCount);
        fab.put("dist_bar_diameter", (double) distBar.mm());
        fab.put("dist_bar_spacing", distSpacing);
        fab.put("dist_bar_count", distBarCount);
        fab.put("cover_mm", (double) cover);
        fab.put("effective_depth_mm", effectiveDepth);
        fab.put("total_weight_kg", totalWeight);
        fab.put("reinforcement_ratio", reinforcementRatio);
        return fab;
    }
}
