package com.bim.eyes.proof.tier3;

import com.bim.eyes.EyesConstants;
import com.bim.eyes.proof.ProofResult;
import com.bim.eyes.proof.RelationalData.WallFaceData;
import java.util.*;

/** P13: Sum of exterior wall lengths = building perimeter. */
public final class PerimeterLengthProof {
    private PerimeterLengthProof() {}

    public static List<ProofResult> prove(
            Map<String, WallFaceData> wallFaces, double expectedPerimeter) {
        List<ProofResult> results = new ArrayList<>();

        if (expectedPerimeter <= 0) {
            results.add(new ProofResult("P13_PERIMETER_LENGTH", ProofResult.Status.SKIPPED,
                null, "no building grid data", 0));
            return results;
        }

        double totalLength = 0;
        for (WallFaceData w : wallFaces.values()) {
            if (!w.isExterior()) continue;
            totalLength += w.runsNS() ? (w.maxY() - w.minY()) : (w.maxX() - w.minX());
        }

        double diff = Math.abs(totalLength - expectedPerimeter);
        double tolerance = expectedPerimeter * EyesConstants.AREA_CONSERVATION_TOL;

        if (diff <= tolerance) {
            results.add(new ProofResult("P13_PERIMETER_LENGTH", ProofResult.Status.PROVEN,
                null, "total=%.3fm expected=%.3fm diff=%.3fm".formatted(
                    totalLength, expectedPerimeter, diff), diff));
        } else {
            results.add(new ProofResult("P13_PERIMETER_LENGTH", ProofResult.Status.VIOLATED,
                null, "total=%.3fm expected=%.3fm diff=%.3fm (>%.1f%%)".formatted(
                    totalLength, expectedPerimeter, diff, EyesConstants.AREA_CONSERVATION_TOL * 100),
                diff));
        }
        return results;
    }
}
