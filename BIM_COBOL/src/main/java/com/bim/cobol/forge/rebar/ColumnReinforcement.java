package com.bim.cobol.forge.rebar;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Column reinforcement calculation per MS 1347:2020.
 *
 * // Implementing FORGE_SUITE_SRS.md §10 Phase 7 — Witness: W-FORGE-9
 */
public final class ColumnReinforcement {
    private ColumnReinforcement() {}

    private static final double MIN_REINFORCEMENT_RATIO = 0.008;
    private static final double MAX_REINFORCEMENT_RATIO = 0.04;
    private static final int MIN_BARS_RECTANGULAR = 4;

    /**
     * Calculate column reinforcement.
     *
     * @param widthMm  column width in mm
     * @param depthMm  column depth in mm
     * @param heightMm column height in mm
     * @param grade    concrete grade
     * @param exposure exposure class
     * @return fabrication parameter map
     */
    public static Map<String, Double> calculate(double widthMm, double depthMm,
            double heightMm, ConcreteGrade grade, ExposureClass exposure) {

        int cover = CoverRequirements.getCover(exposure, "COLUMN");

        double columnArea = widthMm * depthMm;

        // Bar selection by column area
        BarDiameter mainBar;
        if (columnArea <= 250.0 * 250.0) {
            mainBar = BarDiameter.T16;
        } else if (columnArea <= 400.0 * 400.0) {
            mainBar = BarDiameter.T20;
        } else if (columnArea <= 600.0 * 600.0) {
            mainBar = BarDiameter.T25;
        } else {
            mainBar = BarDiameter.T32;
        }

        // Link diameter
        BarDiameter linkBar = BarDiameter.T10;

        // Minimum reinforcement area
        double minSteelArea = MIN_REINFORCEMENT_RATIO * columnArea;
        double mainBarArea = mainBar.areaMm2();
        int barCount = Math.max(MIN_BARS_RECTANGULAR, (int) Math.ceil(minSteelArea / mainBarArea));

        // Ensure even number of bars
        if (barCount % 2 != 0) {
            barCount++;
        }

        // Max link spacing = 12 × longitudinal bar diameter
        double maxLinkSpacing = 12.0 * mainBar.mm();
        double linkSpacing = Math.min(maxLinkSpacing, 300);
        linkSpacing = Math.floor(linkSpacing);

        // Link count along height
        double linkCount = Math.floor(heightMm / linkSpacing) + 1;

        // Actual reinforcement ratio
        double actualSteelArea = barCount * mainBarArea;
        double reinforcementRatio = actualSteelArea / columnArea;

        // Total weight
        double longitudinalLength = barCount * heightMm / 1000.0;
        double linkPerimeter = 2 * (widthMm - 2 * cover + depthMm - 2 * cover) + 200; // hooks
        double linkTotalLength = linkCount * linkPerimeter / 1000.0;
        double totalWeight = longitudinalLength * mainBar.weightKgPerM()
                           + linkTotalLength * linkBar.weightKgPerM();

        Map<String, Double> fab = new LinkedHashMap<>();
        fab.put("longitudinal_bar_diameter", (double) mainBar.mm());
        fab.put("longitudinal_bar_count", (double) barCount);
        fab.put("link_bar_diameter", (double) linkBar.mm());
        fab.put("link_spacing", linkSpacing);
        fab.put("link_count", linkCount);
        fab.put("cover_mm", (double) cover);
        fab.put("total_weight_kg", totalWeight);
        fab.put("reinforcement_ratio", reinforcementRatio);
        fab.put("min_ratio", MIN_REINFORCEMENT_RATIO);
        fab.put("max_ratio", MAX_REINFORCEMENT_RATIO);
        return fab;
    }
}
