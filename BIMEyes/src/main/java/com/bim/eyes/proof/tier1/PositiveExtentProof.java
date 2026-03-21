package com.bim.eyes.proof.tier1;

import com.bim.eyes.proof.PlacementData;
import com.bim.eyes.proof.ProofResult;

/** P01: Every element has positive extent on all axes. */
public final class PositiveExtentProof {
    private PositiveExtentProof() {}

    public static ProofResult prove(PlacementData p) {
        double dx = p.dx(), dy = p.dy(), dz = p.dz();
        if (dx > 0 && dy > 0 && dz > 0) {
            return new ProofResult("P01_POSITIVE_EXTENT", ProofResult.Status.PROVEN,
                p.guid(), "dx=%.4f dy=%.4f dz=%.4f".formatted(dx, dy, dz),
                Math.min(dx, Math.min(dy, dz)));
        }
        String axis = dx <= 0 ? "X" : dy <= 0 ? "Y" : "Z";
        double val = dx <= 0 ? dx : dy <= 0 ? dy : dz;
        return new ProofResult("P01_POSITIVE_EXTENT", ProofResult.Status.VIOLATED,
            p.guid(), "non-positive %s extent=%.6f".formatted(axis, val), val);
    }
}
