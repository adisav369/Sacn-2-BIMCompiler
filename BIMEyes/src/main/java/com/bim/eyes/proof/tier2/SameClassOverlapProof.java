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
                        // White-box classification: real duplicate vs structural joint
                        // Same position + same dimensions = CRITICAL (copy-paste duplicate)
                        // Different position or dimensions = ADVISORY (structural joint)
                        boolean samePos = Math.abs(a.cx() - b.cx()) < 0.001
                                && Math.abs(a.cy() - b.cy()) < 0.001
                                && Math.abs(a.cz() - b.cz()) < 0.001;
                        boolean sameDims = Math.abs(a.dx() - b.dx()) < 0.001
                                && Math.abs(a.dy() - b.dy()) < 0.001
                                && Math.abs(a.dz() - b.dz()) < 0.001;
                        if (samePos && sameDims) {
                            // Real duplicate — identical position and geometry
                            results.add(new ProofResult("P06_NO_SAME_CLASS_OVERLAP",
                                ProofResult.Status.VIOLATED,
                                a.guid(),
                                "DUPLICATE %s pos=same dims=same vol=%.6f m\u00b3".formatted(b.guid(), overlapVolume),
                                overlapVolume));
                            anyViolation = true;
                        } else {
                            // Structural joint — partial overlap, different geometry
                            results.add(new ProofResult("P06_NO_SAME_CLASS_OVERLAP",
                                ProofResult.Status.PROVEN,
                                a.guid(),
                                "JOINT %s vol=%.6f m\u00b3 (pos=%s dims=%s)".formatted(
                                    b.guid(), overlapVolume,
                                    samePos ? "same" : "diff",
                                    sameDims ? "same" : "diff"),
                                overlapVolume));
                        }
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
