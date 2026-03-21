package com.bim.eyes.proof.tier2;

import com.bim.eyes.EyesConstants;
import com.bim.eyes.proof.PlacementData;
import com.bim.eyes.proof.ProofResult;
import java.util.*;

/** P05: No two elements share the same centroid (within 1mm). */
public final class DuplicatePositionProof {
    private DuplicatePositionProof() {}

    public static List<ProofResult> prove(List<PlacementData> placements) {
        List<ProofResult> results = new ArrayList<>();
        boolean anyViolation = false;

        for (int i = 0; i < placements.size(); i++) {
            for (int j = i + 1; j < placements.size(); j++) {
                PlacementData a = placements.get(i);
                PlacementData b = placements.get(j);
                if (!a.ifcClass().equals(b.ifcClass())) continue;

                double dist = Math.sqrt(
                    Math.pow(a.cx() - b.cx(), 2) +
                    Math.pow(a.cy() - b.cy(), 2) +
                    Math.pow(a.cz() - b.cz(), 2));

                if (dist < EyesConstants.CENTROID_TOLERANCE_M) {
                    results.add(new ProofResult("P05_NO_DUPLICATE_POSITION",
                        ProofResult.Status.VIOLATED,
                        a.guid(), "duplicate centroid with %s dist=%.6f".formatted(b.guid(), dist),
                        dist));
                    anyViolation = true;
                }
            }
        }

        if (!anyViolation) {
            results.add(new ProofResult("P05_NO_DUPLICATE_POSITION",
                ProofResult.Status.PROVEN, null,
                "%d placements, no duplicate centroids".formatted(placements.size()), 0));
        }
        return results;
    }
}
