package com.bim.cobol.geometry;

import com.bim.compiler.geometry.Point3D;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Pure geometry — routes electrical conduits from light fixtures to a virtual panel.
 *
 * <p>Following PipeRouter pattern: main conduit at panelX running along Y,
 * per-fixture branches from main to fixture X position. All coordinates in meters.
 */
public final class ConduitRouter {

    /** Main conduit diameter (mm) — standard electrical main. */
    public static final double MAIN_CONDUIT_DIAMETER_MM = 20.0;
    /** Branch conduit diameter (mm) — standard electrical branch. */
    public static final double BRANCH_CONDUIT_DIAMETER_MM = 16.0;

    private ConduitRouter() {}

    /** A single conduit segment. */
    public record ConduitSpec(Point3D start, Point3D end, double diameterMm, String type) {
        public double lengthM() {
            return start.distanceTo(end);
        }
    }

    /** Complete routing result. */
    public record ConduitRoutingResult(
            List<ConduitSpec> mainSpecs,
            List<ConduitSpec> branchSpecs,
            List<String> fittings,
            double totalLengthM
    ) {}

    /**
     * Route conduits from fixtures to a virtual panel.
     *
     * @param fixtures  light fixture positions (meters)
     * @param panelPos  virtual panel entry point (meters)
     * @param mainZ     Z height of the main conduit run (meters)
     * @return routing result with conduit specs, fittings, and total length
     */
    public static ConduitRoutingResult route(List<Point3D> fixtures, Point3D panelPos, double mainZ) {
        if (fixtures.isEmpty()) {
            return new ConduitRoutingResult(List.of(), List.of(), List.of(), 0);
        }

        List<Point3D> sorted = fixtures.stream()
                .sorted(Comparator.comparingDouble(Point3D::y))
                .toList();

        double panelX = panelPos.x();
        List<ConduitSpec> mainSpecs = new ArrayList<>();
        List<ConduitSpec> branchSpecs = new ArrayList<>();
        List<String> fittings = new ArrayList<>();

        // Feed from panel to first fixture Y level (if needed)
        Point3D firstMainPoint = new Point3D(panelX, sorted.get(0).y(), mainZ);
        if (panelPos.distanceTo(firstMainPoint) > 0.001) {
            mainSpecs.add(new ConduitSpec(panelPos, firstMainPoint, MAIN_CONDUIT_DIAMETER_MM, "FEED"));
        }

        // Main conduit segments between consecutive fixture Y positions
        for (int i = 0; i < sorted.size() - 1; i++) {
            Point3D from = new Point3D(panelX, sorted.get(i).y(), mainZ);
            Point3D to = new Point3D(panelX, sorted.get(i + 1).y(), mainZ);
            mainSpecs.add(new ConduitSpec(from, to, MAIN_CONDUIT_DIAMETER_MM, "MAIN"));
            fittings.add("JUNCTION_BOX");
        }

        // Branch from main to each fixture
        for (Point3D fixture : sorted) {
            Point3D mainPoint = new Point3D(panelX, fixture.y(), mainZ);
            branchSpecs.add(new ConduitSpec(mainPoint, fixture, BRANCH_CONDUIT_DIAMETER_MM, "BRANCH"));
            if (mainPoint.distanceTo(fixture) > 0.001) {
                fittings.add("JUNCTION_BOX");
            }
        }

        double total = mainSpecs.stream().mapToDouble(ConduitSpec::lengthM).sum()
                + branchSpecs.stream().mapToDouble(ConduitSpec::lengthM).sum();

        return new ConduitRoutingResult(
                List.copyOf(mainSpecs),
                List.copyOf(branchSpecs),
                List.copyOf(fittings),
                total);
    }
}
