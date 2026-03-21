package com.bim.eyes.proof.tier2;

import com.bim.eyes.EyesConstants;
import com.bim.eyes.ProductCategory;
import com.bim.eyes.proof.PlacementData;
import com.bim.eyes.proof.ProofResult;
import java.util.*;

/** P06: No two elements of same class in same storey have overlapping bboxes. */
public final class SameClassOverlapProof {
    private SameClassOverlapProof() {}

    private static final Set<String> OVERLAP_EXEMPT_CLASSES = Set.of(
        "IfcMember", "IfcBuildingElementProxy");

    public static List<ProofResult> prove(List<PlacementData> placements) {
        List<ProofResult> results = new ArrayList<>();
        boolean anyViolation = false;

        Map<String, List<PlacementData>> groups = new HashMap<>();
        for (PlacementData p : placements) {
            if (OVERLAP_EXEMPT_CLASSES.contains(p.ifcClass())) continue;
            String key = p.storey() + "|" + (p.productCategory() != null ? p.productCategory() : p.ifcClass());
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(p);
        }

        for (var entry : groups.entrySet()) {
            List<PlacementData> group = entry.getValue();
            for (int i = 0; i < group.size(); i++) {
                for (int j = i + 1; j < group.size(); j++) {
                    PlacementData a = group.get(i);
                    PlacementData b = group.get(j);

                    if (ProductCategory.FURNISHING.equals(a.productCategory())
                            && a.elementRef() != null && b.elementRef() != null
                            && !a.elementRef().equals(b.elementRef())) {
                        continue;
                    }

                    double overlapX = Math.max(0, Math.min(a.maxX(), b.maxX()) - Math.max(a.minX(), b.minX()));
                    double overlapY = Math.max(0, Math.min(a.maxY(), b.maxY()) - Math.max(a.minY(), b.minY()));
                    double overlapZ = Math.max(0, Math.min(a.maxZ(), b.maxZ()) - Math.max(a.minZ(), b.minZ()));
                    double overlapVolume = overlapX * overlapY * overlapZ;

                    if (ProductCategory.STRUCTURAL_PLANAR.equals(a.productCategory())) {
                        double minOverlap = Math.min(overlapX, Math.min(overlapY, overlapZ));
                        double tolerance = "IfcPlate".equals(a.ifcClass())
                            ? EyesConstants.PLATE_THIN_WALL_TOL_M : EyesConstants.COVERAGE_TOLERANCE_M;
                        if (minOverlap < tolerance) continue;
                    }

                    if (overlapVolume > EyesConstants.OVERLAP_VOLUME_M3) {
                        results.add(new ProofResult("P06_NO_SAME_CLASS_OVERLAP",
                            ProofResult.Status.VIOLATED,
                            a.guid(),
                            "overlaps %s vol=%.6f m\u00b3".formatted(b.guid(), overlapVolume),
                            overlapVolume));
                        anyViolation = true;
                    }
                }
            }
        }

        if (!anyViolation) {
            results.add(new ProofResult("P06_NO_SAME_CLASS_OVERLAP",
                ProofResult.Status.PROVEN, null,
                "%d placements, no same-class overlaps".formatted(placements.size()), 0));
        }
        return results;
    }
}
