package com.bim.cobol.geometry;

import com.bim.compiler.geometry.Point3D;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Pure geometry — routes sprinkler pipes from heads to a virtual riser.
 *
 * <p>Following FireSuppressionPlacer pattern: main line at riserX running along Y,
 * per-head branches from main to head X position. All coordinates in meters.
 */
public final class PipeRouter {

    /** Main pipe diameter (mm) — standard fire sprinkler main. */
    public static final double MAIN_DIAMETER_MM = 32.0;
    /** Branch pipe diameter (mm) — standard fire sprinkler branch. */
    public static final double BRANCH_DIAMETER_MM = 25.0;

    private PipeRouter() {}

    /** A single pipe segment. */
    public record PipeSpec(Point3D start, Point3D end, double diameterMm, String type) {
        public double lengthM() {
            return start.distanceTo(end);
        }
    }

    /** Complete routing result. */
    public record PipeRoutingResult(
            List<PipeSpec> mainSpecs,
            List<PipeSpec> branchSpecs,
            List<String> fittings,
            double totalLengthM
    ) {}

    /**
     * Route pipes from heads to a virtual riser.
     *
     * @param heads    sprinkler head positions (meters)
     * @param riserPos virtual riser entry point (meters)
     * @param mainZ    Z height of the main pipe run (meters)
     * @return routing result with pipe specs, fittings, and total length
     */
    public static PipeRoutingResult route(List<Point3D> heads, Point3D riserPos, double mainZ) {
        if (heads.isEmpty()) {
            return new PipeRoutingResult(List.of(), List.of(), List.of(), 0);
        }

        List<Point3D> sorted = heads.stream()
                .sorted(Comparator.comparingDouble(Point3D::y))
                .toList();

        double riserX = riserPos.x();
        List<PipeSpec> mainSpecs = new ArrayList<>();
        List<PipeSpec> branchSpecs = new ArrayList<>();
        List<String> fittings = new ArrayList<>();

        // Feed from riser to first head Y level (if needed)
        Point3D firstMainPoint = new Point3D(riserX, sorted.get(0).y(), mainZ);
        if (riserPos.distanceTo(firstMainPoint) > 0.001) {
            mainSpecs.add(new PipeSpec(riserPos, firstMainPoint, MAIN_DIAMETER_MM, "FEED"));
        }

        // Main line segments between consecutive head Y positions
        for (int i = 0; i < sorted.size() - 1; i++) {
            Point3D from = new Point3D(riserX, sorted.get(i).y(), mainZ);
            Point3D to = new Point3D(riserX, sorted.get(i + 1).y(), mainZ);
            mainSpecs.add(new PipeSpec(from, to, MAIN_DIAMETER_MM, "MAIN"));
            fittings.add("TEE");
        }

        // Branch from main to each head
        for (Point3D head : sorted) {
            Point3D mainPoint = new Point3D(riserX, head.y(), mainZ);
            branchSpecs.add(new PipeSpec(mainPoint, head, BRANCH_DIAMETER_MM, "BRANCH"));
            if (mainPoint.distanceTo(head) > 0.001) {
                fittings.add("TEE");
            }
        }

        double total = mainSpecs.stream().mapToDouble(PipeSpec::lengthM).sum()
                + branchSpecs.stream().mapToDouble(PipeSpec::lengthM).sum();

        return new PipeRoutingResult(
                List.copyOf(mainSpecs),
                List.copyOf(branchSpecs),
                List.copyOf(fittings),
                total);
    }
}
