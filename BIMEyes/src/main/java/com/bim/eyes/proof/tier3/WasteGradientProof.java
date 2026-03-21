package com.bim.eyes.proof.tier3;

import com.bim.eyes.proof.PlacementData;
import com.bim.eyes.proof.ProofResult;
import java.util.*;

/** P16: Waste pipes slope downward (from.baseZ >= to.baseZ - 1mm). */
public final class WasteGradientProof {
    private WasteGradientProof() {}

    public static List<ProofResult> prove(
            List<PlacementData> placements, List<String[]> connectEdges) {
        List<ProofResult> results = new ArrayList<>();

        if (connectEdges.isEmpty()) {
            results.add(new ProofResult("P16_WASTE_GRADIENT", ProofResult.Status.SKIPPED,
                null, "no CONNECTS_TO edges", 0));
            return results;
        }

        Map<String, PlacementData> byRef = new HashMap<>();
        for (PlacementData p : placements) {
            if (p.elementRef() != null) byRef.put(p.elementRef(), p);
        }

        int checked = 0;
        for (String[] edge : connectEdges) {
            PlacementData from = byRef.get(edge[0]);
            PlacementData to = byRef.get(edge[1]);
            if (from == null || to == null) continue;

            checked++;
            double fromZ = from.minZ();
            double toZ = to.minZ();
            double gradientMm = (fromZ - toZ) * 1000.0;

            if (fromZ >= toZ - 0.001) {
                results.add(new ProofResult("P16_WASTE_GRADIENT", ProofResult.Status.PROVEN,
                    edge[0], "%s(baseZ=%.3f) → %s(baseZ=%.3f) gradient=%.1fmm".formatted(
                        edge[0], fromZ, edge[1], toZ, gradientMm),
                    gradientMm));
            } else {
                results.add(new ProofResult("P16_WASTE_GRADIENT", ProofResult.Status.VIOLATED,
                    edge[0], "%s(baseZ=%.3f) → %s(baseZ=%.3f) UPHILL %.1fmm".formatted(
                        edge[0], fromZ, edge[1], toZ, gradientMm),
                    gradientMm));
            }
        }

        if (checked == 0) {
            results.add(new ProofResult("P16_WASTE_GRADIENT", ProofResult.Status.SKIPPED,
                null, "no resolved CONNECTS_TO edges", 0));
        }
        return results;
    }
}
