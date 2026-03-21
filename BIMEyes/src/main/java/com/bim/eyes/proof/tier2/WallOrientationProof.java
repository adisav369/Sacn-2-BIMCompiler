package com.bim.eyes.proof.tier2;

import com.bim.eyes.ProductCategory;
import com.bim.eyes.proof.PlacementData;
import com.bim.eyes.proof.ProofResult;
import java.util.*;

/** P20: Walls aligned to cardinal axes. */
public final class WallOrientationProof {
    private WallOrientationProof() {}

    public static List<ProofResult> prove(List<PlacementData> placements,
            Map<String, String> orientationByRef) {
        List<ProofResult> results = new ArrayList<>();

        if (orientationByRef.isEmpty()) {
            results.add(new ProofResult("P20_WALL_ORIENTATION", ProofResult.Status.SKIPPED,
                null, "no wall orientation data", 0));
            return results;
        }

        for (PlacementData p : placements) {
            if (!ProductCategory.STRUCTURAL_PLANAR.equals(p.productCategory())) continue;

            String orient = orientationByRef.get(p.elementRef());
            if (orient == null) continue;

            double dx = p.dx();
            double dy = p.dy();

            if ("NS".equals(orient)) {
                if (dx < dy) {
                    results.add(new ProofResult("P20_WALL_ORIENTATION", ProofResult.Status.PROVEN,
                        p.guid(), "NS wall dx=%.4f < dy=%.4f".formatted(dx, dy), dx / dy));
                } else {
                    results.add(new ProofResult("P20_WALL_ORIENTATION", ProofResult.Status.VIOLATED,
                        p.guid(), "NS wall dx=%.4f >= dy=%.4f (rotated?)".formatted(dx, dy), dx / dy));
                }
            } else if ("EW".equals(orient)) {
                if (dy < dx) {
                    results.add(new ProofResult("P20_WALL_ORIENTATION", ProofResult.Status.PROVEN,
                        p.guid(), "EW wall dy=%.4f < dx=%.4f".formatted(dy, dx), dy / dx));
                } else {
                    results.add(new ProofResult("P20_WALL_ORIENTATION", ProofResult.Status.VIOLATED,
                        p.guid(), "EW wall dy=%.4f >= dx=%.4f (rotated?)".formatted(dy, dx), dy / dx));
                }
            }
        }

        if (results.isEmpty()) {
            results.add(new ProofResult("P20_WALL_ORIENTATION", ProofResult.Status.SKIPPED,
                null, "no wall placements matched orientation rules", 0));
        }
        return results;
    }
}
