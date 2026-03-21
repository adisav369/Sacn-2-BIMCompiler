package com.bim.eyes.proof.tier2;

import com.bim.eyes.proof.PlacementData;
import com.bim.eyes.proof.ProofResult;
import java.util.*;

/**
 * P27: Wall maxZ must not exceed estimated roof surface Z at wall position.
 * // Implementing EYES_SRS.md §4.6 — Witness: W-ROOF-INTERSECT
 *
 * <p>For pitched/curved roofs, walls (especially glass curtain walls) must be
 * trimmed to the roof profile. This proof detects walls that penetrate the
 * roof surface using AABB-based slope estimation.
 *
 * <p>Roof surface Z is estimated via a tent model: ridge runs along the longer
 * AABB dimension, slope descends linearly from ridge (maxZ) to eave (minZ).
 */
public final class WallRoofIntersectionProof {
    private WallRoofIntersectionProof() {}

    /** Tolerance for wall-roof intersection (50mm). */
    static final double ROOF_INTERSECT_TOL_M = 0.050;

    public static List<ProofResult> prove(List<PlacementData> placements) {
        List<ProofResult> results = new ArrayList<>();

        List<PlacementData> roofs = new ArrayList<>();
        List<PlacementData> walls = new ArrayList<>();
        for (PlacementData p : placements) {
            if ("IfcRoof".equals(p.ifcClass()) || "IfcRoofSlab".equals(p.ifcClass())) {
                roofs.add(p);
            }
            if ("IfcWall".equals(p.ifcClass()) || "IfcCurtainWall".equals(p.ifcClass())
                    || "IfcWallStandardCase".equals(p.ifcClass())) {
                walls.add(p);
            }
        }

        if (roofs.isEmpty()) {
            results.add(new ProofResult("P27_WALL_ROOF_INTERSECTION", ProofResult.Status.SKIPPED,
                null, "no roof elements found", 0));
            return results;
        }
        if (walls.isEmpty()) {
            results.add(new ProofResult("P27_WALL_ROOF_INTERSECTION", ProofResult.Status.SKIPPED,
                null, "no wall elements found", 0));
            return results;
        }

        boolean anyViolation = false;
        int checkedCount = 0;

        for (PlacementData wall : walls) {
            for (PlacementData roof : roofs) {
                if (!overlapsXY(wall, roof)) continue;
                checkedCount++;

                double roofZ = estimateRoofZ(roof, wall.cx(), wall.cy());
                double exceedance = wall.maxZ() - roofZ;

                if (exceedance > ROOF_INTERSECT_TOL_M) {
                    results.add(new ProofResult("P27_WALL_ROOF_INTERSECTION",
                        ProofResult.Status.VIOLATED, wall.guid(),
                        "wall.maxZ=%.3f exceeds roofZ=%.3f by %.3fm at (%.2f,%.2f)"
                            .formatted(wall.maxZ(), roofZ, exceedance, wall.cx(), wall.cy()),
                        exceedance));
                    anyViolation = true;
                }
            }
        }

        if (!anyViolation) {
            results.add(new ProofResult("P27_WALL_ROOF_INTERSECTION", ProofResult.Status.PROVEN,
                null, "%d wall-roof pairs checked, all within roof profile".formatted(checkedCount),
                0));
        }
        return results;
    }

    /** Check if two placements overlap in XY (plan view). */
    static boolean overlapsXY(PlacementData a, PlacementData b) {
        return a.minX() < b.maxX() && a.maxX() > b.minX()
            && a.minY() < b.maxY() && a.maxY() > b.minY();
    }

    /**
     * Estimate roof surface Z at position (x, y) using a tent model.
     * Ridge runs along the longer AABB dimension. Slope is linear from
     * ridge (maxZ) to eave (minZ) at the perpendicular edges.
     * Flat roofs (dz &lt; 0.1m) return maxZ everywhere.
     */
    static double estimateRoofZ(PlacementData roof, double x, double y) {
        if (roof.dz() < 0.1) return roof.maxZ();

        double ridgeFraction;
        if (roof.dx() >= roof.dy()) {
            // Ridge runs along X — slope in Y direction
            double halfSpan = roof.dy() / 2.0;
            double distFromRidge = Math.abs(y - roof.cy());
            ridgeFraction = halfSpan > 0 ? 1.0 - (distFromRidge / halfSpan) : 1.0;
        } else {
            // Ridge runs along Y — slope in X direction
            double halfSpan = roof.dx() / 2.0;
            double distFromRidge = Math.abs(x - roof.cx());
            ridgeFraction = halfSpan > 0 ? 1.0 - (distFromRidge / halfSpan) : 1.0;
        }
        ridgeFraction = Math.max(0, Math.min(1, ridgeFraction));

        return roof.minZ() + (roof.maxZ() - roof.minZ()) * ridgeFraction;
    }
}
