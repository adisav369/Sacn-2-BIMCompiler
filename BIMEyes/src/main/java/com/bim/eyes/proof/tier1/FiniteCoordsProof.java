package com.bim.eyes.proof.tier1;

import com.bim.eyes.EyesConstants;
import com.bim.eyes.proof.PlacementData;
import com.bim.eyes.proof.ProofResult;

/** P02: No NaN, no Infinity, coordinates in sane range. */
public final class FiniteCoordsProof {
    private FiniteCoordsProof() {}

    public static ProofResult prove(PlacementData p) {
        double[] coords = {p.minX(), p.maxX(), p.minY(), p.maxY(), p.minZ(), p.maxZ()};
        String[] names = {"minX", "maxX", "minY", "maxY", "minZ", "maxZ"};

        for (int i = 0; i < coords.length; i++) {
            if (Double.isNaN(coords[i]) || Double.isInfinite(coords[i])) {
                return new ProofResult("P02_FINITE_COORDS", ProofResult.Status.VIOLATED,
                    p.guid(), "%s is NaN/Inf".formatted(names[i]), coords[i]);
            }
            if (coords[i] < EyesConstants.COORD_MIN || coords[i] > EyesConstants.COORD_MAX) {
                return new ProofResult("P02_FINITE_COORDS", ProofResult.Status.VIOLATED,
                    p.guid(), "%s=%.4f outside [%.0f, %.0f]".formatted(
                        names[i], coords[i], EyesConstants.COORD_MIN, EyesConstants.COORD_MAX), coords[i]);
            }
        }
        return new ProofResult("P02_FINITE_COORDS", ProofResult.Status.PROVEN,
            p.guid(), "all coords in range", 0);
    }
}
