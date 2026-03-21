package com.bim.eyes.proof.tier1;

import com.bim.eyes.EyesConstants;
import com.bim.eyes.proof.PlacementData;
import com.bim.eyes.proof.ProofResult;

/** P03: Smallest axis > 1mm (not degenerate sliver). */
public final class MinDimensionProof {
    private MinDimensionProof() {}

    public static ProofResult prove(PlacementData p) {
        double minDim = Math.min(p.dx(), Math.min(p.dy(), p.dz()));
        if (minDim >= EyesConstants.MIN_DIMENSION_M) {
            return new ProofResult("P03_MIN_DIMENSION", ProofResult.Status.PROVEN,
                p.guid(), "min_dim=%.4f".formatted(minDim), minDim);
        }
        return new ProofResult("P03_MIN_DIMENSION", ProofResult.Status.VIOLATED,
            p.guid(), "min_dim=%.6f < %.3f".formatted(minDim, EyesConstants.MIN_DIMENSION_M), minDim);
    }
}
