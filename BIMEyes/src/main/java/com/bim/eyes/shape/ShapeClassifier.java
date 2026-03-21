package com.bim.eyes.shape;

import com.bim.eyes.EyesConstants;

import java.util.Arrays;

/**
 * Pure-function shape classification from AABB dimensions.
 * // Implementing EYES_SRS.md §3.1 — Witness: W-EYES-CLASSIFY
 */
public final class ShapeClassifier {

    private ShapeClassifier() {}

    /**
     * Classify shape archetype from AABB dimensions (mm).
     */
    public static ShapeArchetype classifyArchetype(double wMm, double dMm, double hMm) {
        double[] dims = {wMm, dMm, hMm};
        Arrays.sort(dims);
        double S = dims[0], L = dims[2];
        if (L < 0.01) return ShapeArchetype.MIXED;
        double planarity = S / L;
        double elongation = dims[1] / L;
        if (planarity < EyesConstants.PLANARITY_THRESHOLD && elongation >= EyesConstants.ELONGATION_THRESHOLD)
            return ShapeArchetype.PLANAR;
        if (planarity < EyesConstants.PLANARITY_THRESHOLD && elongation < EyesConstants.ELONGATION_THRESHOLD)
            return ShapeArchetype.ELONGATED;
        if (planarity >= EyesConstants.COMPACT_PLANARITY && elongation >= EyesConstants.COMPACT_ELONGATION)
            return ShapeArchetype.COMPACT;
        return ShapeArchetype.MIXED;
    }

    /**
     * Classify scale band from AABB dimensions (mm).
     */
    public static ScaleBand classifyScaleBand(double wMm, double dMm, double hMm) {
        double[] dims = {wMm, dMm, hMm};
        Arrays.sort(dims);
        double volumeM3 = (dims[0] * dims[1] * dims[2]) / 1e9;
        if (volumeM3 > EyesConstants.SCALE_ARCHITECTURAL) return ScaleBand.ARCHITECTURAL;
        if (volumeM3 > EyesConstants.SCALE_FURNITURE)      return ScaleBand.FURNITURE;
        if (volumeM3 > EyesConstants.SCALE_FITTING)        return ScaleBand.FITTING;
        return ScaleBand.TINY;
    }

    /**
     * Detect whether an element is a hosted opening (door/window-like)
     * from geometry alone — no IFC class needed.
     *
     * <p>Openings are PLANAR elements at sub-architectural scale.
     */
    public static boolean isHostedOpening(double wMm, double dMm, double hMm) {
        ShapeArchetype arch = classifyArchetype(wMm, dMm, hMm);
        ScaleBand band = classifyScaleBand(wMm, dMm, hMm);
        return (arch == ShapeArchetype.PLANAR || arch == ShapeArchetype.MIXED)
                && band != ScaleBand.ARCHITECTURAL;
    }

    /**
     * Compare two fingerprints for geometric equivalence.
     * Returns null if equivalent, or a diagnostic string if not.
     */
    public static String proveEquivalence(Fingerprint a, Fingerprint b, double epsilon) {
        if (Math.abs(a.planarity() - b.planarity()) > epsilon)
            return String.format("planarity differs: %.4f vs %.4f (delta=%.4f, epsilon=%.4f)",
                a.planarity(), b.planarity(), Math.abs(a.planarity() - b.planarity()), epsilon);
        if (Math.abs(a.elongation() - b.elongation()) > epsilon)
            return String.format("elongation differs: %.4f vs %.4f (delta=%.4f, epsilon=%.4f)",
                a.elongation(), b.elongation(), Math.abs(a.elongation() - b.elongation()), epsilon);
        if (Math.abs(a.squareness() - b.squareness()) > epsilon)
            return String.format("squareness differs: %.4f vs %.4f (delta=%.4f, epsilon=%.4f)",
                a.squareness(), b.squareness(), Math.abs(a.squareness() - b.squareness()), epsilon);
        if (a.archetype() != b.archetype())
            return String.format("archetype differs: %s vs %s", a.archetype(), b.archetype());
        return null;
    }

    /**
     * Verify class consistency — geometric invariants per IFC class.
     * Returns null if consistent, or a diagnostic string if not.
     */
    static String verifyClassConsistency(Fingerprint f) {
        return switch (f.ifcClass()) {
            case "IfcWall", "IfcWallStandardCase" -> {
                if (f.planarity() >= 0.20)
                    yield String.format("WALL not planar: planarity=%.4f (S=%.0f L=%.0f mm) — " +
                        "wall thickness should be ≪ length", f.planarity(), f.smallMM(), f.largeMM());
                yield null;
            }
            case "IfcSlab", "IfcSlabStandardCase" -> {
                if (f.planarity() >= 0.20)
                    yield String.format("SLAB not planar: planarity=%.4f (S=%.0f L=%.0f mm) — " +
                        "slab thickness should be ≪ span", f.planarity(), f.smallMM(), f.largeMM());
                yield null;
            }
            case "IfcPlate" -> {
                if (f.planarity() >= 0.20)
                    yield String.format("PLATE not planar: planarity=%.4f (S=%.0f L=%.0f mm) — " +
                        "plate thickness should be ≪ face dimensions", f.planarity(), f.smallMM(), f.largeMM());
                yield null;
            }
            case "IfcRoof" -> {
                if (f.archetype() == ShapeArchetype.ELONGATED && f.scaleBand() != ScaleBand.ARCHITECTURAL)
                    yield String.format("ROOF looks elongated at non-architectural scale: " +
                        "planarity=%.4f elongation=%.4f vol=%.3f m³", f.planarity(), f.elongation(), f.volumeM3());
                yield null;
            }
            case "IfcColumn", "IfcColumnStandardCase" -> {
                if (f.archetype() == ShapeArchetype.PLANAR)
                    yield String.format("COLUMN looks planar (wall-like): planarity=%.4f elongation=%.4f — " +
                        "column should be elongated (thin×thin×tall)", f.planarity(), f.elongation());
                yield null;
            }
            case "IfcBeam", "IfcBeamStandardCase" -> {
                if (f.archetype() == ShapeArchetype.PLANAR)
                    yield String.format("BEAM looks planar (slab-like): planarity=%.4f elongation=%.4f — " +
                        "beam should be elongated", f.planarity(), f.elongation());
                yield null;
            }
            case "IfcMember", "IfcMemberStandardCase" -> {
                if (f.archetype() == ShapeArchetype.COMPACT)
                    yield String.format("MEMBER looks compact (furniture-like): planarity=%.4f elongation=%.4f — " +
                        "structural member should be elongated", f.planarity(), f.elongation());
                yield null;
            }
            case "IfcDoor", "IfcDoorStandardCase" -> {
                if (f.planarity() >= 0.35)
                    yield String.format("DOOR not planar: planarity=%.4f (S=%.0f L=%.0f mm) — " +
                        "door depth should be ≪ height/width", f.planarity(), f.smallMM(), f.largeMM());
                yield null;
            }
            case "IfcWindow", "IfcWindowStandardCase" -> {
                if (f.planarity() >= 0.35)
                    yield String.format("WINDOW not planar: planarity=%.4f (S=%.0f L=%.0f mm) — " +
                        "window depth should be ≪ height/width", f.planarity(), f.smallMM(), f.largeMM());
                yield null;
            }
            case "IfcFurnishingElement" -> {
                if (f.planarity() < 0.05 && f.scaleBand() == ScaleBand.ARCHITECTURAL)
                    yield String.format("FURNITURE looks like a wall: planarity=%.4f vol=%.3f m³ — " +
                        "architectural-scale planar element classified as furniture", f.planarity(), f.volumeM3());
                yield null;
            }
            case "IfcSpace" -> {
                if (f.scaleBand() != ScaleBand.ARCHITECTURAL)
                    yield String.format("SPACE at wrong scale: vol=%.4f m³ band=%s — " +
                        "a room should be architectural scale", f.volumeM3(), f.scaleBand());
                yield null;
            }
            case "IfcPipeSegment", "IfcDuctSegment", "IfcCableSegment" -> {
                if (f.archetype() == ShapeArchetype.PLANAR)
                    yield String.format("PIPE/DUCT looks planar: planarity=%.4f elongation=%.4f — " +
                        "segment should be elongated", f.planarity(), f.elongation());
                yield null;
            }
            default -> null;
        };
    }
}
