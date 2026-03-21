package com.bim.eyes.shape;

/**
 * Immutable geometric fingerprint of a single element.
 *
 * <p>Dimensionless ratios (given AABB dimensions sorted S ≤ M ≤ L):
 * <ul>
 *   <li>planarity  = S / L — how thin (0 = infinitely thin, 1 = cube)</li>
 *   <li>elongation = M / L — how stretched (0 = needle, 1 = square cross-section)</li>
 *   <li>squareness = S / M — cross-section shape (0 = ribbon, 1 = square)</li>
 * </ul>
 *
 * // Implementing EYES_SRS.md §3.3 — Witness: W-EYES-FINGERPRINT
 */
public record Fingerprint(
    String guid,
    String ifcClass,
    String elementName,
    double smallMM,
    double mediumMM,
    double largeMM,
    double planarity,
    double elongation,
    double squareness,
    double volumeM3,
    double topologyRatio,
    ShapeArchetype archetype,
    ScaleBand scaleBand,
    double cx, double cy, double cz
) {
    /** Backwards-compatible constructor (no centroid). */
    public Fingerprint(String guid, String ifcClass, String elementName,
            double smallMM, double mediumMM, double largeMM,
            double planarity, double elongation, double squareness,
            double volumeM3, double topologyRatio,
            ShapeArchetype archetype, ScaleBand scaleBand) {
        this(guid, ifcClass, elementName, smallMM, mediumMM, largeMM,
             planarity, elongation, squareness, volumeM3, topologyRatio,
             archetype, scaleBand, Double.NaN, Double.NaN, Double.NaN);
    }

    /**
     * Test whether this fingerprint is consistent with its claimed IFC class.
     * Returns null if consistent, or a diagnostic string if not.
     */
    public String verifyClassConsistency() {
        return ShapeClassifier.verifyClassConsistency(this);
    }
}
