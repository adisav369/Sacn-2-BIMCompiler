package com.bim.ifctobom;

import com.bim.ifctobom.ExtractionReader.ExtractionElement;

import java.util.*;

/**
 * Detects verb patterns (TILE, ROUTE, FRAME, SPRAY) in groups of extracted elements.
 *
 * <p>Each detection method analyses centroid positions of same-product elements within
 * a discipline BOM and returns a verb_ref string encoding the placement formula, or
 * null if no pattern is detected. The verb_ref encodes only the <b>pattern</b> — the
 * origin lives in the BOM line's dx/dy/dz (floor-relative).
 *
 * <h3>Verb Taxonomy (Theorems 1+5: CLT aggregation + Information-theoretic compression)</h3>
 * <ul>
 *   <li><b>TILE</b> — 2D grid on surface: {@code TILE:nx:ny:stepX:stepY}</li>
 *   <li><b>ROUTE</b> — axis-aligned linear runs: {@code ROUTE:X:step:n|Y:step:n|...}</li>
 *   <li><b>FRAME</b> — grid intersections: {@code FRAME:x1,x2,...|y1,y2,...}</li>
 *   <li><b>SPRAY</b> — semi-regular grid (10% tolerance): {@code SPRAY:stepX:stepY}</li>
 * </ul>
 *
 * <h3>Non-Disturbance Principle</h3>
 * <p>Patterns are mined from I_Element_Extraction data. The verb formula must reproduce
 * the exact same centroids (within tolerance). If it can't, elements stay unfactored (qty=1).
 *
 * @see <a href="docs/VerbPatternArchitecture.md">Verb Pattern Architecture</a>
 * @see <a href="docs/CONCEPTUAL BLUEPRINT.txt">CONCEPTUAL BLUEPRINT §Theorems 1,5</a>
 */
public class VerbDetector {

    /** Positional tolerance for grid detection (metres). */
    private static final double TOL = 0.005;  // 5mm

    /** Relaxed tolerance for SPRAY (10% of step). */
    private static final double SPRAY_TOL_RATIO = 0.10;

    /** Minimum group size to attempt pattern detection. */
    private static final int MIN_GROUP = 4;

    // ── Result records ────────────────────────────────────────────────

    /** Detected TILE pattern: 2D grid with uniform step. */
    public record TileResult(int nx, int ny, double stepX, double stepY,
                             double originX, double originY, double originZ) {
        public String verbRef() {
            return String.format("TILE:%d:%d:%.4f:%.4f", nx, ny, stepX, stepY);
        }
        public int instanceCount() { return nx * ny; }
    }

    /** Detected ROUTE pattern: chain of axis-aligned legs. */
    public record RouteLeg(char axis, double step, int count) {}
    public record RouteResult(List<RouteLeg> legs,
                              double originX, double originY, double originZ) {
        public String verbRef() {
            StringBuilder sb = new StringBuilder("ROUTE:");
            for (int i = 0; i < legs.size(); i++) {
                if (i > 0) sb.append('|');
                RouteLeg leg = legs.get(i);
                sb.append(leg.axis).append(':')
                  .append(String.format("%.4f", leg.step)).append(':')
                  .append(leg.count);
            }
            return sb.toString();
        }
        public int instanceCount() { return legs.stream().mapToInt(RouteLeg::count).sum(); }
    }

    /** Detected FRAME pattern: gridline intersections. */
    public record FrameResult(double[] xLines, double[] yLines,
                              double originX, double originY, double originZ) {
        public String verbRef() {
            StringBuilder sb = new StringBuilder("FRAME:");
            for (int i = 0; i < xLines.length; i++) {
                if (i > 0) sb.append(',');
                sb.append(String.format("%.4f", xLines[i]));
            }
            sb.append('|');
            for (int i = 0; i < yLines.length; i++) {
                if (i > 0) sb.append(',');
                sb.append(String.format("%.4f", yLines[i]));
            }
            return sb.toString();
        }
        public int instanceCount() { return xLines.length * yLines.length; }
    }

    /** Detected SPRAY pattern: semi-regular grid with relaxed tolerance. */
    public record SprayResult(double stepX, double stepY,
                              double originX, double originY, double originZ,
                              int count) {
        public String verbRef() {
            return String.format("SPRAY:%.4f:%.4f", stepX, stepY);
        }
        public int instanceCount() { return count; }
    }

    // ── Detection cascade ─────────────────────────────────────────────

    /**
     * Run the detection cascade on a group of same-product elements.
     * Returns the verb_ref string if a pattern is detected, null otherwise.
     *
     * <p>Priority: TILE > ROUTE > FRAME > SPRAY > null (unfactored).
     *
     * @param elements same-product elements (all sharing child_product_id)
     * @param floorMinX floor AABB min X (for origin computation)
     * @param floorMinY floor AABB min Y
     * @param floorMinZ floor AABB min Z
     * @return verb_ref string or null
     */
    public static String detect(List<ExtractionElement> elements,
                                double floorMinX, double floorMinY, double floorMinZ) {
        if (elements.size() < MIN_GROUP) return null;

        TileResult tile = detectTile(elements, floorMinX, floorMinY, floorMinZ);
        if (tile != null && tile.instanceCount() == elements.size()) return tile.verbRef();

        RouteResult route = detectRoute(elements, floorMinX, floorMinY, floorMinZ);
        if (route != null && route.instanceCount() == elements.size()) return route.verbRef();

        FrameResult frame = detectFrame(elements, floorMinX, floorMinY, floorMinZ);
        if (frame != null && frame.instanceCount() == elements.size()) return frame.verbRef();

        SprayResult spray = detectSpray(elements, floorMinX, floorMinY, floorMinZ);
        if (spray != null && spray.instanceCount() == elements.size()) return spray.verbRef();

        return null;
    }

    // ── TILE: 2D grid with uniform step ───────────────────────────────

    /**
     * Detect a TILE pattern: elements on a 2D grid with uniform X and Y spacing.
     * Groups centroids by Z (floor plane), then finds uniform columns (X) and rows (Y).
     */
    public static TileResult detectTile(List<ExtractionElement> elements,
                                        double floorMinX, double floorMinY, double floorMinZ) {
        if (elements.size() < MIN_GROUP) return null;

        // Collect floor-relative centroids
        double[] xs = elements.stream().mapToDouble(ExtractionElement::centroidX).sorted().toArray();
        double[] ys = elements.stream().mapToDouble(ExtractionElement::centroidY).sorted().toArray();

        // Find unique X positions
        List<Double> uniqueX = uniquePositions(xs);
        List<Double> uniqueY = uniquePositions(ys);

        if (uniqueX.size() < 2 || uniqueY.size() < 2) return null;

        // Check uniform X step
        double stepX = uniformStep(uniqueX);
        if (stepX <= 0) return null;

        // Check uniform Y step
        double stepY = uniformStep(uniqueY);
        if (stepY <= 0) return null;

        // Verify: nx * ny == element count
        int nx = uniqueX.size();
        int ny = uniqueY.size();
        if (nx * ny != elements.size()) return null;

        // Verify every grid cell is occupied
        if (!verifyGrid(elements, uniqueX, uniqueY)) return null;

        double originX = uniqueX.get(0) - floorMinX;
        double originY = uniqueY.get(0) - floorMinY;
        double originZ = elements.get(0).centroidZ() - floorMinZ;

        return new TileResult(nx, ny, stepX, stepY, originX, originY, originZ);
    }

    // ── ROUTE: axis-aligned linear runs ───────────────────────────────

    /**
     * Detect a ROUTE pattern: elements chained along axis-aligned legs.
     * Sort centroids, identify runs where one axis is constant (within tolerance).
     */
    public static RouteResult detectRoute(List<ExtractionElement> elements,
                                          double floorMinX, double floorMinY, double floorMinZ) {
        if (elements.size() < MIN_GROUP) return null;

        // Sort by X then Y (greedy chain)
        List<double[]> centroids = new ArrayList<>();
        for (ExtractionElement e : elements) {
            centroids.add(new double[]{e.centroidX(), e.centroidY(), e.centroidZ()});
        }
        centroids.sort((a, b) -> {
            int cmp = Double.compare(a[0], b[0]);
            return cmp != 0 ? cmp : Double.compare(a[1], b[1]);
        });

        // Build legs: chain of same-axis runs
        List<RouteLeg> legs = new ArrayList<>();
        int i = 0;
        while (i < centroids.size()) {
            // Try X-run: constant Y
            int xRun = countAxisRun(centroids, i, 0, 1);  // vary X, constant Y
            // Try Y-run: constant X
            int yRun = countAxisRun(centroids, i, 1, 0);  // vary Y, constant X

            if (xRun >= 2 && xRun >= yRun) {
                double step = (centroids.get(i + xRun - 1)[0] - centroids.get(i)[0]) / (xRun - 1);
                legs.add(new RouteLeg('X', step, xRun));
                i += xRun;
            } else if (yRun >= 2) {
                // Re-sort this segment by Y for proper step calculation
                List<double[]> segment = centroids.subList(i, i + yRun);
                segment.sort((a, b) -> Double.compare(a[1], b[1]));
                double step = (segment.get(segment.size() - 1)[1] - segment.get(0)[1]) / (yRun - 1);
                legs.add(new RouteLeg('Y', step, yRun));
                i += yRun;
            } else {
                return null;  // can't chain — abort
            }
        }

        if (legs.isEmpty()) return null;

        // Verify total count
        int totalFromLegs = legs.stream().mapToInt(RouteLeg::count).sum();
        if (totalFromLegs != elements.size()) return null;

        double originX = centroids.get(0)[0] - floorMinX;
        double originY = centroids.get(0)[1] - floorMinY;
        double originZ = centroids.get(0)[2] - floorMinZ;

        return new RouteResult(legs, originX, originY, originZ);
    }

    // ── FRAME: grid intersections ─────────────────────────────────────

    /**
     * Detect a FRAME pattern: elements at intersections of X and Y gridlines.
     * Clusters X and Y positions, verifies that all intersections are occupied.
     */
    public static FrameResult detectFrame(List<ExtractionElement> elements,
                                          double floorMinX, double floorMinY, double floorMinZ) {
        if (elements.size() < MIN_GROUP) return null;

        double[] xs = elements.stream().mapToDouble(ExtractionElement::centroidX).sorted().toArray();
        double[] ys = elements.stream().mapToDouble(ExtractionElement::centroidY).sorted().toArray();

        List<Double> xLines = uniquePositions(xs);
        List<Double> yLines = uniquePositions(ys);

        if (xLines.size() < 2 || yLines.size() < 2) return null;

        // FRAME requires complete grid (all intersections occupied)
        if (xLines.size() * yLines.size() != elements.size()) return null;

        // FRAME differs from TILE: gridlines need NOT be uniformly spaced
        // (TILE requires uniform step, FRAME allows irregular grids)
        if (!verifyGrid(elements, xLines, yLines)) return null;

        double[] xArr = xLines.stream().mapToDouble(v -> v - floorMinX).toArray();
        double[] yArr = yLines.stream().mapToDouble(v -> v - floorMinY).toArray();
        double originZ = elements.get(0).centroidZ() - floorMinZ;

        return new FrameResult(xArr, yArr, 0, 0, originZ);
    }

    // ── SPRAY: semi-regular grid (relaxed tolerance) ──────────────────

    /**
     * Detect a SPRAY pattern: elements on a semi-regular grid (10% tolerance on step).
     * Like TILE but with relaxed tolerance — for sprinkler-type distributions.
     */
    public static SprayResult detectSpray(List<ExtractionElement> elements,
                                          double floorMinX, double floorMinY, double floorMinZ) {
        if (elements.size() < MIN_GROUP) return null;

        double[] xs = elements.stream().mapToDouble(ExtractionElement::centroidX).sorted().toArray();
        double[] ys = elements.stream().mapToDouble(ExtractionElement::centroidY).sorted().toArray();

        List<Double> uniqueX = uniquePositions(xs);
        List<Double> uniqueY = uniquePositions(ys);

        if (uniqueX.size() < 2 || uniqueY.size() < 2) return null;

        // Median step (more robust than uniform step for semi-regular patterns)
        double stepX = medianStep(uniqueX);
        double stepY = medianStep(uniqueY);

        if (stepX <= 0 || stepY <= 0) return null;

        // Verify: each element is within SPRAY_TOL_RATIO of a grid position
        int matched = 0;
        for (ExtractionElement e : elements) {
            double cx = e.centroidX();
            double cy = e.centroidY();
            boolean xOk = false, yOk = false;
            for (double ux : uniqueX) {
                if (Math.abs(cx - ux) < stepX * SPRAY_TOL_RATIO) { xOk = true; break; }
            }
            for (double uy : uniqueY) {
                if (Math.abs(cy - uy) < stepY * SPRAY_TOL_RATIO) { yOk = true; break; }
            }
            if (xOk && yOk) matched++;
        }

        // Require all elements to match
        if (matched != elements.size()) return null;

        double originX = uniqueX.get(0) - floorMinX;
        double originY = uniqueY.get(0) - floorMinY;
        double originZ = elements.get(0).centroidZ() - floorMinZ;

        return new SprayResult(stepX, stepY, originX, originY, originZ, elements.size());
    }

    // ── Helpers ───────────────────────────────────────────────────────

    /** Cluster sorted values into unique positions (within TOL). */
    static List<Double> uniquePositions(double[] sorted) {
        List<Double> unique = new ArrayList<>();
        for (double v : sorted) {
            if (unique.isEmpty() || Math.abs(v - unique.get(unique.size() - 1)) > TOL) {
                unique.add(v);
            }
        }
        return unique;
    }

    /** Return uniform step if all gaps are within TOL, or -1 if not uniform. */
    static double uniformStep(List<Double> positions) {
        if (positions.size() < 2) return -1;
        double step = positions.get(1) - positions.get(0);
        if (step < TOL) return -1;  // degenerate
        for (int i = 2; i < positions.size(); i++) {
            double gap = positions.get(i) - positions.get(i - 1);
            if (Math.abs(gap - step) > TOL) return -1;
        }
        return step;
    }

    /** Return median step between consecutive unique positions. */
    static double medianStep(List<Double> positions) {
        if (positions.size() < 2) return -1;
        double[] gaps = new double[positions.size() - 1];
        for (int i = 0; i < gaps.length; i++) {
            gaps[i] = positions.get(i + 1) - positions.get(i);
        }
        Arrays.sort(gaps);
        return gaps[gaps.length / 2];
    }

    /** Verify that every cell of the grid (uniqueX x uniqueY) has an element. */
    static boolean verifyGrid(List<ExtractionElement> elements,
                              List<Double> uniqueX, List<Double> uniqueY) {
        boolean[][] occupied = new boolean[uniqueX.size()][uniqueY.size()];
        for (ExtractionElement e : elements) {
            int xi = findNearest(uniqueX, e.centroidX());
            int yi = findNearest(uniqueY, e.centroidY());
            if (xi < 0 || yi < 0) return false;
            if (occupied[xi][yi]) return false;  // duplicate position
            occupied[xi][yi] = true;
        }
        for (boolean[] row : occupied) {
            for (boolean cell : row) {
                if (!cell) return false;
            }
        }
        return true;
    }

    /** Find the index of the nearest position within TOL, or -1. */
    private static int findNearest(List<Double> positions, double value) {
        for (int i = 0; i < positions.size(); i++) {
            if (Math.abs(positions.get(i) - value) <= TOL) return i;
        }
        return -1;
    }

    /**
     * Count a run of centroids starting at index where one axis is constant
     * (within TOL) and the other varies.
     *
     * @param centroids sorted centroid list
     * @param start starting index
     * @param varyAxis axis that varies (0=X, 1=Y)
     * @param constAxis axis that stays constant (0=X, 1=Y)
     */
    private static int countAxisRun(List<double[]> centroids, int start,
                                    int varyAxis, int constAxis) {
        if (start >= centroids.size()) return 0;
        double constVal = centroids.get(start)[constAxis];
        int count = 1;
        for (int i = start + 1; i < centroids.size(); i++) {
            if (Math.abs(centroids.get(i)[constAxis] - constVal) > TOL) break;
            count++;
        }
        return count;
    }
}
