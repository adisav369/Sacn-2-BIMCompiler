package com.bim.eyes.compare;

import com.bim.eyes.EyesConstants;
import com.bim.eyes.shape.Fingerprint;

import java.util.*;

/**
 * Pairing-free Rosetta Stone proof: two element sets contain the same shapes
 * at the same positions.
 * // Implementing EYES_SRS.md §5.1 — Witness: W-MULTISET
 */
public final class MultisetComparator {

    private MultisetComparator() {}

    /** Result of multiset fingerprint comparison. */
    public record MultisetResult(
        int extractedCount, int compiledCount, int matched,
        List<String> mismatches, boolean spatialProof
    ) {
        public MultisetResult(int extractedCount, int compiledCount, int matched, List<String> mismatches) {
            this(extractedCount, compiledCount, matched, mismatches, false);
        }
        public boolean isEquivalent() {
            return extractedCount == compiledCount && mismatches.isEmpty();
        }
        public double matchRate() {
            int pairs = Math.min(extractedCount, compiledCount);
            return pairs > 0 ? (100.0 * matched / pairs) : 0;
        }
    }

    /**
     * Multiset fingerprint comparison with spatial proof.
     */
    public static MultisetResult proveMultisetEquivalence(
            List<Fingerprint> extracted, List<Fingerprint> compiled, double epsilon) {

        boolean hasCentroids = extracted.stream().anyMatch(f -> !Double.isNaN(f.cx()))
                            && compiled.stream().anyMatch(f -> !Double.isNaN(f.cx()));

        Comparator<Fingerprint> comparator;
        if (hasCentroids) {
            comparator = Comparator
                .comparingLong((Fingerprint f) -> Math.round(f.cx() / EyesConstants.CENTROID_GRID_M))
                .thenComparingLong(f -> Math.round(f.cy() / EyesConstants.CENTROID_GRID_M))
                .thenComparingLong(f -> Math.round(f.cz() / EyesConstants.CENTROID_GRID_M))
                .thenComparingDouble(Fingerprint::planarity)
                .thenComparingDouble(Fingerprint::elongation)
                .thenComparingDouble(Fingerprint::squareness);
        } else {
            comparator = Comparator
                .comparingDouble(Fingerprint::planarity)
                .thenComparingDouble(Fingerprint::elongation)
                .thenComparingDouble(Fingerprint::squareness);
        }

        List<Fingerprint> extSorted = new ArrayList<>(extracted);
        List<Fingerprint> cmpSorted = new ArrayList<>(compiled);
        extSorted.sort(comparator);
        cmpSorted.sort(comparator);

        int extCount = extSorted.size();
        int cmpCount = cmpSorted.size();
        int pairs = Math.min(extCount, cmpCount);
        int matched = 0;
        List<String> mismatches = new ArrayList<>();

        for (int i = 0; i < pairs; i++) {
            Fingerprint ext = extSorted.get(i);
            Fingerprint cmp = cmpSorted.get(i);

            double dp = Math.abs(ext.planarity() - cmp.planarity());
            double de = Math.abs(ext.elongation() - cmp.elongation());
            double ds = Math.abs(ext.squareness() - cmp.squareness());
            double maxShapeDelta = Math.max(dp, Math.max(de, ds));
            boolean shapeMatch = maxShapeDelta <= epsilon;

            boolean posMatch = true;
            double posDist = 0;
            if (hasCentroids && !Double.isNaN(ext.cx()) && !Double.isNaN(cmp.cx())) {
                double ddx = ext.cx() - cmp.cx();
                double ddy = ext.cy() - cmp.cy();
                double ddz = ext.cz() - cmp.cz();
                posDist = Math.sqrt(ddx * ddx + ddy * ddy + ddz * ddz);
                posMatch = posDist <= EyesConstants.POSITION_TOLERANCE_M;
            }

            if (shapeMatch && posMatch) {
                matched++;
            } else {
                String reason = !shapeMatch && !posMatch ? "shape+pos"
                    : !shapeMatch ? "shape" : "pos";
                mismatches.add(String.format(
                    "[%d] %s ext(%s p=%.3f,e=%.3f @%.2f,%.2f,%.2f) vs cmp(%s p=%.3f,e=%.3f @%.2f,%.2f,%.2f) shapeDelta=%.4f posDist=%.4f",
                    i, reason,
                    ext.ifcClass(), ext.planarity(), ext.elongation(), ext.cx(), ext.cy(), ext.cz(),
                    cmp.ifcClass(), cmp.planarity(), cmp.elongation(), cmp.cx(), cmp.cy(), cmp.cz(),
                    maxShapeDelta, posDist));
            }
        }

        return new MultisetResult(extCount, cmpCount, matched, mismatches, hasCentroids);
    }
}
