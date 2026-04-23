package com.bim.ifctobom;

import com.bim.ifctobom.ClassificationYaml.MirrorConfig;
import com.bim.ifctobom.ExtractionReader.ExtractionElement;
import com.bim.orm.BIMLogger;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Pairs A↔B elements by mirror-proximity: for each A element, compute its
 * expected B position under rot=π (reflect both mirror axis and cross-axes
 * about their respective centers), then greedily assign the nearest unmatched
 * B element.
 *
 * <p>This handles the duplex case where the IFC model uses rotation about the
 * building center, not a pure axis mirror. Sort-and-zip fails when multiple
 * elements of the same product cluster at different cross-axis positions.
 *
 * <p>Complexity: O(n²) per product group. Acceptable because n is typically
 * small (< 20 elements of the same product per storey).
 *
 * @see MirrorPairer
 */
public class ProximityMirrorPairer implements MirrorPairer {

    private final String axis;
    private final double mirrorCenter;
    private final double crossCenterY;
    private final double crossCenterZ;

    /**
     * @param mirror         mirror configuration (axis + party wall position)
     * @param buildingMinY   building AABB minY (IFC world coords)
     * @param buildingMaxY   building AABB maxY (IFC world coords)
     * @param buildingMinZ   building AABB minZ (IFC world coords)
     * @param buildingMaxZ   building AABB maxZ (IFC world coords)
     */
    public ProximityMirrorPairer(MirrorConfig mirror,
                                  double buildingMinY, double buildingMaxY,
                                  double buildingMinZ, double buildingMaxZ) {
        this.axis = mirror.axis().toUpperCase();
        this.mirrorCenter = mirror.position();
        this.crossCenterY = (buildingMinY + buildingMaxY) / 2.0;
        this.crossCenterZ = (buildingMinZ + buildingMaxZ) / 2.0;
    }

    @Override
    public List<ElementPair> pair(List<ExtractionElement> aGroup,
                                   List<ExtractionElement> bGroup) {
        int n = Math.min(aGroup.size(), bGroup.size());
        if (n == 0) return List.of();

        // Fast path: single element per side — no ambiguity
        if (n == 1) {
            return List.of(new ElementPair(aGroup.get(0), bGroup.get(0)));
        }

        List<ElementPair> pairs = new ArrayList<>(n);
        Set<Integer> usedB = new LinkedHashSet<>();

        for (int ai = 0; ai < n; ai++) {
            ExtractionElement a = aGroup.get(ai);
            double[] expected = expectedBPosition(a);

            int bestBi = -1;
            double bestDist = Double.MAX_VALUE;

            for (int bi = 0; bi < bGroup.size(); bi++) {
                if (usedB.contains(bi)) continue;
                ExtractionElement b = bGroup.get(bi);
                double dist = distanceSq(expected, b);
                if (dist < bestDist) {
                    bestDist = dist;
                    bestBi = bi;
                }
            }

            if (bestBi >= 0) {
                usedB.add(bestBi);
                pairs.add(new ElementPair(a, bGroup.get(bestBi)));

                if (bestDist > 0.5 * 0.5) {  // > 500mm — log for diagnostics
                    BIMLogger.fine("COMPOSITION", "Proximity pair {}: A({:.3f},{:.3f}) → B({:.3f},{:.3f}) dist={:.3f}m",
                        a.mProductId(),
                        a.centroidX(), a.centroidY(),
                        bGroup.get(bestBi).centroidX(), bGroup.get(bestBi).centroidY(),
                        Math.sqrt(bestDist));
                }
            }
        }

        return pairs;
    }

    /**
     * Compute the expected B-side centroid for a given A-side element under rot=π.
     * Reflects the mirror axis about the party wall center and the cross-axes
     * about the building center.
     */
    private double[] expectedBPosition(ExtractionElement a) {
        double ax = a.centroidX();
        double ay = a.centroidY();
        double az = a.centroidZ();

        return switch (axis) {
            case "X" -> new double[]{
                2 * mirrorCenter - ax,    // mirror axis
                2 * crossCenterY - ay,    // cross-axis Y (rot=π)
                az                        // Z unchanged
            };
            case "Y" -> new double[]{
                2 * crossCenterY - ax,    // cross-axis X (rot=π)
                2 * mirrorCenter - ay,    // mirror axis
                az                        // Z unchanged
            };
            default -> new double[]{ax, ay, az};
        };
    }

    /** Squared Euclidean distance between expected position and B element centroid. */
    private static double distanceSq(double[] expected, ExtractionElement b) {
        double dx = expected[0] - b.centroidX();
        double dy = expected[1] - b.centroidY();
        double dz = expected[2] - b.centroidZ();
        return dx * dx + dy * dy + dz * dz;
    }
}
