package com.bim.eyes.proof;

/**
 * Minimal placement data for geometric proofs.
 * // Implementing EYES_SRS.md §4 — Witness: W-EYES-PROOF-TIER1
 */
public record PlacementData(
    String guid,
    String ifcClass,
    String productCategory,
    String elementRef,
    String storey,
    double minX, double maxX,
    double minY, double maxY,
    double minZ, double maxZ
) {
    /** Compact constructor: swap any inverted AABB axes defensively. */
    public PlacementData {
        if (minX > maxX) { double t = minX; minX = maxX; maxX = t; }
        if (minY > maxY) { double t = minY; minY = maxY; maxY = t; }
        if (minZ > maxZ) { double t = minZ; minZ = maxZ; maxZ = t; }
    }

    public double cx() { return (minX + maxX) / 2; }
    public double cy() { return (minY + maxY) / 2; }
    public double cz() { return (minZ + maxZ) / 2; }
    public double dx() { return maxX - minX; }
    public double dy() { return maxY - minY; }
    public double dz() { return maxZ - minZ; }
}
