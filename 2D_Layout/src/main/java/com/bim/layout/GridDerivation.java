package com.bim.layout;

import com.bim.layout.MetadataReader.Element;

import java.util.*;

import static com.bim.layout.LayoutConstants.*;

/**
 * Grid line computation from structural element positions.
 *
 * Convention follows Malaysian JKR practice (TB-LKTN):
 *   Letters (A, B, C…) for vertical grids (X-axis positions).
 *   Numbers (1, 2, 3…) for horizontal grids (Y-axis positions).
 */
public final class GridDerivation {
    private GridDerivation() {}

    // ── Data records ─────────────────────────────────────────────────────────

    public record GridLine(String label, String axis, double position) {}

    public record DimString(double start, double end, double offset,
                            String axis, String text) {}

    // ── Grid derivation ──────────────────────────────────────────────────────

    /**
     * Derive structural grid lines from wall positions.
     * Exterior walls → centerline grid. Interior thin walls → centerline grid.
     */
    public static List<GridLine> deriveGrids(List<Element> walls) {
        List<Double> xPos = new ArrayList<>();
        List<Double> yPos = new ArrayList<>();

        for (Element w : walls) {
            if (w.isGlass()) continue;

            if (w.isExterior()) {
                if (w.isEWWall()) {
                    yPos.add(w.centerY());
                    xPos.add(w.minX());
                    xPos.add(w.maxX());
                }
                if (w.isNSWall()) {
                    xPos.add(w.centerX());
                    yPos.add(w.minY());
                    yPos.add(w.maxY());
                }
            } else {
                if (w.isNSWall() && w.widthX() < 0.5) xPos.add(w.centerX());
                else if (w.isEWWall() && w.widthY() < 0.5) yPos.add(w.centerY());
            }
        }

        double MERGE_TOL = 0.5;
        List<Double> xs = mergePositions(xPos, MERGE_TOL);
        List<Double> ys = mergePositions(yPos, MERGE_TOL);

        List<GridLine> grids = new ArrayList<>();
        for (int i = 0; i < xs.size(); i++) {
            grids.add(new GridLine(String.valueOf((char)('A' + i)), "x", xs.get(i)));
        }
        for (int i = 0; i < ys.size(); i++) {
            grids.add(new GridLine(String.valueOf(i + 1), "y", ys.get(i)));
        }
        return grids;
    }

    /** Snap grid positions so bay dimensions round to clean SNAP_MODULE numbers. */
    public static List<GridLine> snapGrids(List<GridLine> grids) {
        List<GridLine> result = new ArrayList<>();
        for (String axis : new String[]{"x", "y"}) {
            List<GridLine> axisGrids = grids.stream()
                .filter(g -> g.axis().equals(axis))
                .sorted(Comparator.comparingDouble(GridLine::position))
                .toList();
            if (axisGrids.size() < 2) { result.addAll(axisGrids); continue; }

            List<GridLine> snapped = new ArrayList<>();
            snapped.add(axisGrids.get(0));
            for (int i = 1; i < axisGrids.size(); i++) {
                double rawBayMm = (axisGrids.get(i).position()
                    - axisGrids.get(i - 1).position()) * 1000;
                double snappedBay = Math.round(rawBayMm / SNAP_MODULE) * SNAP_MODULE;
                if (snappedBay < SNAP_MODULE) snappedBay = SNAP_MODULE;
                double newPos = snapped.get(snapped.size() - 1).position() + snappedBay / 1000;
                snapped.add(new GridLine(axisGrids.get(i).label(), axis, newPos));
            }
            result.addAll(snapped);
        }
        return result;
    }

    /** Generate bay + overall dimension strings from grid lines. */
    public static List<DimString> generateDimensions(List<GridLine> grids) {
        List<DimString> dims = new ArrayList<>();

        List<GridLine> xg = grids.stream().filter(g -> "x".equals(g.axis()))
            .sorted(Comparator.comparingDouble(GridLine::position)).toList();
        List<GridLine> yg = grids.stream().filter(g -> "y".equals(g.axis()))
            .sorted(Comparator.comparingDouble(GridLine::position)).toList();

        // X-axis bay dims (shown above building)
        for (int i = 0; i < xg.size() - 1; i++) {
            double d = xg.get(i + 1).position() - xg.get(i).position();
            dims.add(new DimString(xg.get(i).position(), xg.get(i + 1).position(),
                DIM_OFFSET_1, "x", formatDim(d)));
        }
        if (xg.size() >= 2) {
            double d = xg.get(xg.size() - 1).position() - xg.get(0).position();
            dims.add(new DimString(xg.get(0).position(), xg.get(xg.size() - 1).position(),
                DIM_OFFSET_2, "x", formatDim(d)));
        }

        // Y-axis bay dims (shown left of building)
        for (int i = 0; i < yg.size() - 1; i++) {
            double d = yg.get(i + 1).position() - yg.get(i).position();
            dims.add(new DimString(yg.get(i).position(), yg.get(i + 1).position(),
                DIM_OFFSET_1, "y", formatDim(d)));
        }
        if (yg.size() >= 2) {
            double d = yg.get(yg.size() - 1).position() - yg.get(0).position();
            dims.add(new DimString(yg.get(0).position(), yg.get(yg.size() - 1).position(),
                DIM_OFFSET_2, "y", formatDim(d)));
        }
        return dims;
    }

    /** Format dimension in mm, snapped to nearest 1mm. */
    public static String formatDim(double meters) {
        double mm = meters * 1000;
        return Math.abs(mm - Math.round(mm)) < 1 ? String.valueOf((int)Math.round(mm))
                                                   : String.format("%.0f", mm);
    }

    // ── 2D Convex hull (Andrew's monotone chain) ─────────────────────────────

    /**
     * Compute 2D convex hull of a set of (h, z) points.
     * Returns CCW-ordered hull. Portable from Python as-is.
     */
    public static List<double[]> convexHull2D(List<double[]> points) {
        if (points.size() <= 2) return new ArrayList<>(points);

        List<double[]> pts = new ArrayList<>(new TreeSet<>(
            Comparator.comparingDouble((double[] p) -> p[0]).thenComparingDouble(p -> p[1])));
        pts.addAll(points);
        pts.sort(Comparator.comparingDouble((double[] p) -> p[0]));

        // Deduplicate
        List<double[]> uniq = new ArrayList<>();
        for (double[] p : pts) {
            if (uniq.isEmpty() || uniq.get(uniq.size()-1)[0] != p[0]
                                || uniq.get(uniq.size()-1)[1] != p[1]) uniq.add(p);
        }
        pts = uniq;
        if (pts.size() <= 2) return pts;

        List<double[]> lower = new ArrayList<>();
        for (double[] p : pts) {
            while (lower.size() >= 2 && cross(lower.get(lower.size()-2),
                                               lower.get(lower.size()-1), p) <= 0)
                lower.remove(lower.size()-1);
            lower.add(p);
        }
        List<double[]> upper = new ArrayList<>();
        for (int i = pts.size() - 1; i >= 0; i--) {
            double[] p = pts.get(i);
            while (upper.size() >= 2 && cross(upper.get(upper.size()-2),
                                               upper.get(upper.size()-1), p) <= 0)
                upper.remove(upper.size()-1);
            upper.add(p);
        }
        lower.remove(lower.size()-1);
        upper.remove(upper.size()-1);
        lower.addAll(upper);
        return lower;
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private static double cross(double[] o, double[] a, double[] b) {
        return (a[0]-o[0])*(b[1]-o[1]) - (a[1]-o[1])*(b[0]-o[0]);
    }

    private static List<Double> mergePositions(List<Double> raw, double tol) {
        if (raw.isEmpty()) return List.of();
        List<Double> sorted = new ArrayList<>(raw);
        Collections.sort(sorted);
        List<Double> merged = new ArrayList<>();
        merged.add(sorted.get(0));
        for (double p : sorted) {
            if (Math.abs(p - merged.get(merged.size()-1)) < tol)
                merged.set(merged.size()-1, (merged.get(merged.size()-1) + p) / 2);
            else
                merged.add(p);
        }
        return merged;
    }
}
