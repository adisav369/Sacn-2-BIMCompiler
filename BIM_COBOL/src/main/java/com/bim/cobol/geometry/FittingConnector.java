package com.bim.cobol.geometry;

import com.bim.compiler.geometry.Point3D;

import java.util.*;

/**
 * Pure geometry — identifies orphan fitting pairs in extracted piping
 * and generates connecting pipe segments to fill gaps.
 *
 * <p>Algorithm: greedy nearest-neighbour matching. Each fitting has a port
 * budget (elbow=2, tee=3, cross=4). Existing pipes consume ports.
 * Remaining open ports are connected to the nearest open-port fitting
 * within maxGapM. Pipe diameter derived from RTREE cross-section.
 */
public final class FittingConnector {

    /** Default pipe diameter when none can be inferred (DN25 = 33.4mm OD). */
    public static final double DEFAULT_DIAMETER_MM = 33.4;

    private FittingConnector() {}

    /** A fitting loaded from an extracted DB with RTREE bounds. */
    public record Fitting(String guid, String type, String system,
                          Point3D center,
                          double dx, double dy, double dz) {

        /** Port diameter estimate from RTREE cross-section (min AABB dimension). */
        public double portDiameterMm() {
            return Math.min(dx, Math.min(dy, dz)) * 1000.0;
        }

        /** Max connection ports for this fitting type. */
        public int maxPorts() {
            return FittingConnector.maxPorts(type);
        }
    }

    /** An existing pipe segment with computed endpoints. */
    public record ExistingPipe(String guid, String type, String system,
                               Point3D center,
                               Point3D endpoint1, Point3D endpoint2,
                               double diameterMm) {

        /** Compute pipe endpoints from RTREE bounds. Long axis = pipe direction. */
        public static ExistingPipe fromRtree(String guid, String type, String system,
                                             Point3D center,
                                             double dx, double dy, double dz) {
            double diam = Math.min(dx, Math.min(dy, dz)) * 1000.0;
            Point3D ep1, ep2;
            if (dx >= dy && dx >= dz) {
                ep1 = new Point3D(center.x() - dx / 2, center.y(), center.z());
                ep2 = new Point3D(center.x() + dx / 2, center.y(), center.z());
            } else if (dy >= dx && dy >= dz) {
                ep1 = new Point3D(center.x(), center.y() - dy / 2, center.z());
                ep2 = new Point3D(center.x(), center.y() + dy / 2, center.z());
            } else {
                ep1 = new Point3D(center.x(), center.y(), center.z() - dz / 2);
                ep2 = new Point3D(center.x(), center.y(), center.z() + dz / 2);
            }
            return new ExistingPipe(guid, type, system, center, ep1, ep2, diam);
        }
    }

    /** A generated connection between two fittings. */
    public record ConnectionSpec(Fitting from, Fitting to,
                                  Point3D pipeStart, Point3D pipeEnd,
                                  double diameterMm, double gapM,
                                  String system) {
        public double lengthMm() { return gapM * 1000.0; }
    }

    /**
     * Find fitting pairs that need connecting pipes.
     *
     * @param fittings      all fittings from extracted DB
     * @param existingPipes all pipe segments from extracted DB
     * @param maxGapM       maximum gap to bridge (meters)
     * @return generated connections, sorted by gap distance
     */
    public static List<ConnectionSpec> connect(List<Fitting> fittings,
                                                List<ExistingPipe> existingPipes,
                                                double maxGapM) {
        // 1. Count existing pipe connections per fitting (pipe endpoint within 0.1m)
        Map<String, Integer> pipeConns = new HashMap<>();
        for (Fitting f : fittings) {
            int count = 0;
            for (ExistingPipe p : existingPipes) {
                if (f.center().distanceTo(p.endpoint1()) < 0.1
                        || f.center().distanceTo(p.endpoint2()) < 0.1) {
                    count++;
                }
            }
            pipeConns.put(f.guid(), count);
        }

        // 2. Build candidate pairs sorted by distance (greedy nearest-first)
        List<CandidatePair> candidates = new ArrayList<>();
        for (int i = 0; i < fittings.size(); i++) {
            Fitting f1 = fittings.get(i);
            for (int j = i + 1; j < fittings.size(); j++) {
                Fitting f2 = fittings.get(j);
                double dist = f1.center().distanceTo(f2.center());
                if (dist >= 0.01 && dist <= maxGapM) {
                    candidates.add(new CandidatePair(f1, f2, dist));
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(CandidatePair::dist));

        // 3. Greedy matching: connect nearest pairs respecting port budgets
        Map<String, Integer> genConns = new HashMap<>();
        List<ConnectionSpec> result = new ArrayList<>();

        for (CandidatePair cp : candidates) {
            int avail1 = cp.f1.maxPorts()
                    - pipeConns.getOrDefault(cp.f1.guid(), 0)
                    - genConns.getOrDefault(cp.f1.guid(), 0);
            int avail2 = cp.f2.maxPorts()
                    - pipeConns.getOrDefault(cp.f2.guid(), 0)
                    - genConns.getOrDefault(cp.f2.guid(), 0);

            if (avail1 <= 0 || avail2 <= 0) continue;

            // Skip if existing pipe already bridges these two fittings
            if (hasPipeBetween(cp.f1, cp.f2, existingPipes)) continue;

            // Determine diameter: use nearest existing pipe's diameter, else default
            double diameter = nearestPipeDiameter(cp.f1, cp.f2, existingPipes);
            String system = cp.f1.system() != null ? cp.f1.system() : cp.f2.system();

            result.add(new ConnectionSpec(
                    cp.f1, cp.f2, cp.f1.center(), cp.f2.center(),
                    diameter, cp.dist, system));
            genConns.merge(cp.f1.guid(), 1, Integer::sum);
            genConns.merge(cp.f2.guid(), 1, Integer::sum);
        }

        return result;
    }

    /** Max ports for a fitting type. */
    public static int maxPorts(String fittingType) {
        if (fittingType == null) return 2;
        String upper = fittingType.toUpperCase();
        if (upper.contains("TEE")) return 3;
        if (upper.contains("CROSS")) return 4;
        if (upper.contains("STRAINER")) return 2;
        return 2; // elbow, transition, bend, 45-degree
    }

    /** Check if an existing pipe bridges two fittings (one endpoint near each). */
    private static boolean hasPipeBetween(Fitting f1, Fitting f2,
                                           List<ExistingPipe> pipes) {
        double tol = 0.1; // 100mm tolerance
        for (ExistingPipe p : pipes) {
            boolean ep1NearF1 = f1.center().distanceTo(p.endpoint1()) < tol;
            boolean ep1NearF2 = f2.center().distanceTo(p.endpoint1()) < tol;
            boolean ep2NearF1 = f1.center().distanceTo(p.endpoint2()) < tol;
            boolean ep2NearF2 = f2.center().distanceTo(p.endpoint2()) < tol;
            if ((ep1NearF1 && ep2NearF2) || (ep1NearF2 && ep2NearF1)) {
                return true;
            }
        }
        return false;
    }

    /** Find the diameter of the nearest existing pipe to these two fittings. */
    private static double nearestPipeDiameter(Fitting f1, Fitting f2,
                                               List<ExistingPipe> pipes) {
        double bestDist = Double.MAX_VALUE;
        double bestDiam = DEFAULT_DIAMETER_MM;
        Point3D mid = new Point3D(
                (f1.center().x() + f2.center().x()) / 2,
                (f1.center().y() + f2.center().y()) / 2,
                (f1.center().z() + f2.center().z()) / 2);
        for (ExistingPipe p : pipes) {
            double d = p.center().distanceTo(mid);
            if (d < bestDist) {
                bestDist = d;
                bestDiam = p.diameterMm();
            }
        }
        return bestDiam;
    }

    private record CandidatePair(Fitting f1, Fitting f2, double dist) {}
}
