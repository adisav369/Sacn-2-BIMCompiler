package com.bim.eyes.proof.tier2;

import com.bim.eyes.proof.PlacementData;
import com.bim.eyes.proof.ProofResult;
import java.util.*;

/**
 * P28: Roof footprint covers the building footprint in plan (XY).
 * // Implementing EYES_SRS.md §4.6 — Witness: W-ROOF-COVERAGE
 *
 * <p>Building footprint is derived from the union of all wall extents.
 * Roof must extend to or beyond this envelope (with overhang tolerance).
 */
public final class RoofCoverageProof {
    private RoofCoverageProof() {}

    /** Overhang tolerance — roof may extend slightly beyond walls (50mm). */
    static final double OVERHANG_TOL_M = 0.050;

    public static List<ProofResult> prove(List<PlacementData> placements) {
        List<ProofResult> results = new ArrayList<>();

        double bMinX = Double.MAX_VALUE, bMaxX = -Double.MAX_VALUE;
        double bMinY = Double.MAX_VALUE, bMaxY = -Double.MAX_VALUE;
        boolean hasWall = false;

        double rMinX = Double.MAX_VALUE, rMaxX = -Double.MAX_VALUE;
        double rMinY = Double.MAX_VALUE, rMaxY = -Double.MAX_VALUE;
        boolean hasRoof = false;

        for (PlacementData p : placements) {
            if ("IfcWall".equals(p.ifcClass()) || "IfcCurtainWall".equals(p.ifcClass())
                    || "IfcWallStandardCase".equals(p.ifcClass())) {
                bMinX = Math.min(bMinX, p.minX());
                bMaxX = Math.max(bMaxX, p.maxX());
                bMinY = Math.min(bMinY, p.minY());
                bMaxY = Math.max(bMaxY, p.maxY());
                hasWall = true;
            }
            if ("IfcRoof".equals(p.ifcClass()) || "IfcRoofSlab".equals(p.ifcClass())) {
                rMinX = Math.min(rMinX, p.minX());
                rMaxX = Math.max(rMaxX, p.maxX());
                rMinY = Math.min(rMinY, p.minY());
                rMaxY = Math.max(rMaxY, p.maxY());
                hasRoof = true;
            }
        }

        if (!hasRoof) {
            results.add(new ProofResult("P28_ROOF_COVERAGE", ProofResult.Status.SKIPPED,
                null, "no roof elements found", 0));
            return results;
        }
        if (!hasWall) {
            results.add(new ProofResult("P28_ROOF_COVERAGE", ProofResult.Status.SKIPPED,
                null, "no wall elements found", 0));
            return results;
        }

        List<String> gaps = new ArrayList<>();
        if (rMinX > bMinX + OVERHANG_TOL_M)
            gaps.add("west: roof.minX=%.2f > wall.minX=%.2f".formatted(rMinX, bMinX));
        if (rMaxX < bMaxX - OVERHANG_TOL_M)
            gaps.add("east: roof.maxX=%.2f < wall.maxX=%.2f".formatted(rMaxX, bMaxX));
        if (rMinY > bMinY + OVERHANG_TOL_M)
            gaps.add("south: roof.minY=%.2f > wall.minY=%.2f".formatted(rMinY, bMinY));
        if (rMaxY < bMaxY - OVERHANG_TOL_M)
            gaps.add("north: roof.maxY=%.2f < wall.maxY=%.2f".formatted(rMaxY, bMaxY));

        if (gaps.isEmpty()) {
            results.add(new ProofResult("P28_ROOF_COVERAGE", ProofResult.Status.PROVEN,
                null, "roof [%.2f,%.2f]x[%.2f,%.2f] covers walls [%.2f,%.2f]x[%.2f,%.2f]"
                    .formatted(rMinX, rMaxX, rMinY, rMaxY, bMinX, bMaxX, bMinY, bMaxY),
                0));
        } else {
            results.add(new ProofResult("P28_ROOF_COVERAGE", ProofResult.Status.VIOLATED,
                null, "roof does not cover building: " + String.join("; ", gaps),
                gaps.size()));
        }
        return results;
    }
}
