package com.bim.designer.assembly;

import java.util.List;

/**
 * Stateless U-value calculator per BS EN ISO 6946.
 * Pure function: layers in → U-value out. No DB access.
 *
 * // Implementing ASSEMBLY_BUILDER_SRS.md §4 — Witness: W-ASM-UVAL-1
 */
public final class UValueCalculator {

    /** Surface resistance by element orientation. */
    public enum Orientation {
        /** Walls — R_si=0.13, R_se=0.04 */
        VERTICAL(0.13, 0.04),
        /** Roofs (heat flow up) — R_si=0.10, R_se=0.04 */
        HORIZONTAL_UP(0.10, 0.04),
        /** Floors (heat flow down) — R_si=0.17, R_se=0.04 */
        HORIZONTAL_DOWN(0.17, 0.04);

        final double rSi;
        final double rSe;

        Orientation(double rSi, double rSe) {
            this.rSi = rSi;
            this.rSe = rSe;
        }
    }

    /** Input record — one layer's thermal data. */
    public record LayerThermal(
            double thicknessMm,
            double conductivityWmK,
            boolean isAirGap,
            boolean isMetalStud
    ) {}

    // Metal stud bridging constants (BS EN ISO 6946 §6.2)
    private static final double STUD_FRACTION = 0.15;      // 15% stud area (45mm @ 600mm centres)
    private static final double INSULATION_FRACTION = 0.85;
    private static final double STEEL_CONDUCTIVITY = 50.0;  // W/m·K
    private static final double CAVITY_FILL_CONDUCTIVITY = 0.038; // mineral wool fill

    // Air gap thermal resistance cap (BS EN ISO 6946 Table 2)
    private static final double AIR_GAP_R_MAX = 0.18;       // m²K/W at 25mm
    private static final double AIR_GAP_REF_MM = 25.0;

    private UValueCalculator() {} // utility class

    /**
     * Calculate U-value for a layer stack.
     *
     * @param layers      ordered layers (outside → inside)
     * @param orientation element orientation for surface resistance
     * @return U-value in W/m²K, rounded to 2 decimal places
     * @throws IllegalArgumentException if layers is empty or null
     */
    public static double calculate(List<LayerThermal> layers, Orientation orientation) {
        if (layers == null || layers.isEmpty()) {
            throw new IllegalArgumentException("Layer list must not be empty");
        }

        double rTotal = orientation.rSi + orientation.rSe;

        for (LayerThermal layer : layers) {
            rTotal += layerResistance(layer);
        }

        double u = 1.0 / rTotal;
        return Math.round(u * 100.0) / 100.0;
    }

    /**
     * Calculate thermal resistance for a single layer.
     * Visible for testing.
     */
    public static double layerResistance(LayerThermal layer) {
        double thicknessM = layer.thicknessMm / 1000.0;

        if (layer.isAirGap) {
            // BS EN ISO 6946: air gap R saturates at 25mm
            return Math.min(AIR_GAP_R_MAX, AIR_GAP_R_MAX * layer.thicknessMm / AIR_GAP_REF_MM);
        }

        if (layer.isMetalStud) {
            // Proportional-area bridged R (BS EN ISO 6946 §6.2)
            double rMetal = thicknessM / STEEL_CONDUCTIVITY;
            double rInsulation = thicknessM / CAVITY_FILL_CONDUCTIVITY;
            return 1.0 / (STUD_FRACTION / rMetal + INSULATION_FRACTION / rInsulation);
        }

        // Normal solid layer: R = d / λ
        if (layer.conductivityWmK <= 0) {
            throw new IllegalArgumentException(
                    "Conductivity must be positive, got " + layer.conductivityWmK);
        }
        return thicknessM / layer.conductivityWmK;
    }
}
