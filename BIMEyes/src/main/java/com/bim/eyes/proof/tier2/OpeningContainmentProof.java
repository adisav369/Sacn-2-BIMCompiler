package com.bim.eyes.proof.tier2;

import com.bim.eyes.EyesConstants;
import com.bim.eyes.ProductCategory;
import com.bim.eyes.proof.PlacementData;
import com.bim.eyes.proof.ProofResult;
import com.bim.eyes.proof.RelationalData.*;
import java.util.*;

/** P07: Every IfcDoor/IfcWindow bbox projects within its host wall bbox. */
public final class OpeningContainmentProof {
    private OpeningContainmentProof() {}

    public static List<ProofResult> prove(
            List<PlacementData> placements, Map<String, WallFaceData> wallFaces,
            List<ElementRule> rules) {
        List<ProofResult> results = new ArrayList<>();
        boolean anyChecked = false;

        for (ElementRule rule : rules) {
            if (!"WALL".equals(rule.hostType())) continue;
            if (!ProductCategory.OPENING.equals(rule.productCategory())) continue;

            PlacementData opening = findPlacement(placements, rule.elementRef(), rule.ifcClass());
            if (opening == null) continue;

            WallFaceData hostWall = wallFaces.get(rule.hostRef());
            if (hostWall == null) continue;

            anyChecked = true;

            boolean contained;
            double maxExceedance;

            if (hostWall.runsNS()) {
                double exY1 = hostWall.minY() - opening.minY();
                double exY2 = opening.maxY() - hostWall.maxY();
                double exZ1 = hostWall.minZ() - opening.minZ();
                double exZ2 = opening.maxZ() - hostWall.maxZ();
                maxExceedance = Math.max(0, Math.max(exY1, Math.max(exY2, Math.max(exZ1, exZ2))));
                contained = maxExceedance <= EyesConstants.CONTAINMENT_TOLERANCE_M;
            } else {
                double exX1 = hostWall.minX() - opening.minX();
                double exX2 = opening.maxX() - hostWall.maxX();
                double exZ1 = hostWall.minZ() - opening.minZ();
                double exZ2 = opening.maxZ() - hostWall.maxZ();
                maxExceedance = Math.max(0, Math.max(exX1, Math.max(exX2, Math.max(exZ1, exZ2))));
                contained = maxExceedance <= EyesConstants.CONTAINMENT_TOLERANCE_M;
            }

            if (contained) {
                results.add(new ProofResult("P07_OPENING_CONTAINED", ProofResult.Status.PROVEN,
                    opening.guid(), "%s in wall %s".formatted(rule.ifcClass(), rule.hostRef()),
                    maxExceedance));
            } else {
                results.add(new ProofResult("P07_OPENING_CONTAINED", ProofResult.Status.VIOLATED,
                    opening.guid(),
                    "%s exceeds wall %s by %.4fm".formatted(rule.ifcClass(), rule.hostRef(), maxExceedance),
                    maxExceedance));
            }
        }

        if (!anyChecked) {
            results.add(new ProofResult("P07_OPENING_CONTAINED", ProofResult.Status.SKIPPED,
                null, "no opening rules with resolved wall faces", 0));
        }
        return results;
    }

    static PlacementData findPlacement(List<PlacementData> placements, String elementRef, String ifcClass) {
        if (elementRef == null) return placements.stream()
                .filter(p -> ifcClass.equals(p.ifcClass())).findFirst().orElse(null);
        PlacementData exact = placements.stream()
                .filter(p -> ifcClass.equals(p.ifcClass()) && elementRef.equals(p.elementRef()))
                .findFirst().orElse(null);
        if (exact != null) return exact;
        if (!elementRef.contains("_")) return placements.stream()
                .filter(p -> ifcClass.equals(p.ifcClass())).findFirst().orElse(null);
        String suffix = elementRef.substring(elementRef.lastIndexOf('_'));
        return placements.stream()
                .filter(p -> ifcClass.equals(p.ifcClass()) && p.guid() != null && p.guid().endsWith(suffix))
                .findFirst().orElse(null);
    }
}
