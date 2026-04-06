package com.bim.layout;

import com.bim.layout.GridDerivation.*;
import com.bim.layout.MetadataReader.*;
import com.bim.layout.SectionCut.*;
import com.bim.orm.BIMLogger;

import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

import static com.bim.layout.LayoutConstants.*;

/**
 * Main orchestrator for 2D architectural drawing generation.
 *
 * Reads the spatial DB produced by the BIM compiler and generates standard
 * architectural drawings: floor plans, elevations, roof plans.
 *
 * Output: SVG viewable in any browser. All coordinates in paper-mm.
 *
 * Usage:
 *   java -jar 2d-layout.jar path/to/building.db [--floor-plan | --elevation front | --all]
 */
public class DrawingWriter {

    private static final String TAG = "DrawingWriter";

    // ── Coordinate transform functional interface ─────────────────────────────

    @FunctionalInterface
    interface CoordXY { double[] apply(double a, double b); }

    // ── CLI entry point ───────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: DrawingWriter <building.db> [--floor-plan|--elevation <face>|--all]");
            System.exit(1);
        }
        BIMLogger.initForRun("DrawingWriter");

        String dbPath  = args[0];
        String metaPath = Path.of(dbPath).getParent()
            .resolveSibling("lib/input/2D_metadata.db").toString();

        try (Connection bldConn  = openDb(dbPath);
             Connection metaConn = Files.exists(Path.of(metaPath))
                 ? openDb(metaPath) : null) {

            Elements elems = MetadataReader.readElements(bldConn);
            DrawingMeta meta = metaConn != null
                ? MetadataReader.readDrawingMeta(metaConn)
                : new DrawingMeta(SheetMeta.defaults(), List.of(), Map.of());

            String outDir = Path.of(dbPath).getParent()
                .resolveSibling("python/output").toString();
            Files.createDirectories(Path.of(outDir));
            String stem = Path.of(dbPath).getFileName().toString().replace(".db", "");

            boolean all = args[1].equals("--all");

            if (all || args[1].equals("--floor-plan")) {
                String svg = drawFloorPlan(elems, bldConn, meta);
                write(outDir + "/" + stem + "_floor_plan.svg", svg);
                BIMLogger.info(TAG, "→ {}/{}_floor_plan.svg", outDir, stem);
            }

            for (String face : List.of("front", "rear", "left", "right")) {
                if (all || (args[1].equals("--elevation") && args.length > 2
                        && args[2].equals(face))) {
                    String svg = drawElevation(elems, face, bldConn, meta);
                    write(outDir + "/" + stem + "_" + face + "_elevation.svg", svg);
                    BIMLogger.info(TAG, "→ {}_{}_elevation.svg", stem, face);
                }
            }
        }
    }

    // ── Floor plan ────────────────────────────────────────────────────────────

    public static String drawFloorPlan(Elements elems, Connection bldConn,
                                       DrawingMeta meta) throws SQLException {
        List<Element> walls = elems.walls();
        if (walls.isEmpty()) { BIMLogger.warn(TAG, "No walls — empty floor plan"); return ""; }

        List<Element> all = new ArrayList<>();
        all.addAll(walls); all.addAll(elems.doors());
        all.addAll(elems.windows()); all.addAll(elems.furniture());

        double bMinX = all.stream().mapToDouble(Element::minX).min().getAsDouble();
        double bMaxX = all.stream().mapToDouble(Element::maxX).max().getAsDouble();
        double bMinY = all.stream().mapToDouble(Element::minY).min().getAsDouble();
        double bMaxY = all.stream().mapToDouble(Element::maxY).max().getAsDouble();

        double originX = bMinX, originY = bMaxY; // Y-flip origin
        double bWPaper = (bMaxX - bMinX) * PAPER_FACTOR;
        double bHPaper = (bMaxY - bMinY) * PAPER_FACTOR;

        SVGBuilder svg = new SVGBuilder();
        SheetMeta sheet = tightSheet(meta.sheet(), bWPaper, bHPaper, 20, 109 + 65);

        double sw = sheet.widthMm(), sh = sheet.heightMm();
        svg.setViewbox(0, 0, sw, sh);

        double cLeft = sheet.marginLeft(), cTop = sheet.marginTop();
        double cRight = sw - sheet.marginRight() - sheet.titleBlockWidth();
        double cBottom = sh - sheet.marginBottom();
        double cW = cRight - cLeft, cH = cBottom - cTop;

        double envW = MARGIN_LEFT + bWPaper + MARGIN_RIGHT;
        double envH = MARGIN_TOP + bHPaper + MARGIN_BOTTOM;
        double envX = cLeft + Math.max(0, (cW - envW) / 2);
        double envY = cTop  + Math.max(0, (cH - envH) / 2);
        double bldOx = envX + MARGIN_LEFT, bldOy = envY + MARGIN_TOP;

        CoordXY toSheet = (wx, wy) -> {
            double px = (wx - originX) * PAPER_FACTOR + bldOx;
            double py = -(wy - originY) * PAPER_FACTOR + bldOy;
            return new double[]{px, py};
        };

        drawSheetBorder(svg, sheet);
        if (!meta.titleFields().isEmpty())
            drawTitleBlock(svg, sheet, meta.titleFields(),
                "PELAN LANTAI / FLOOR PLAN", "A-01");
        drawNorthArrow(svg, cRight - 10, cTop + 15);

        List<GridLine> grids = GridDerivation.snapGrids(GridDerivation.deriveGrids(walls));
        String GRID_DASH = "4,1,1,1";

        for (GridLine g : grids) {
            if ("x".equals(g.axis())) {
                double px = toSheet.apply(g.position(), 0)[0];
                double yTop = bldOy - MARGIN_TOP + 5;
                double yBot = bldOy + bHPaper + MARGIN_BOTTOM - 5;
                svg.line("grid", px, yTop, px, yBot, COL_GRID, LW_GRID, GRID_DASH);
                double bby = bldOy - MARGIN_TOP + GRID_CIRCLE_R + 3;
                svg.circle("grid", px, bby, GRID_CIRCLE_R, COL_WALL, LW_WALL_INT, COL_BACKGROUND);
                svg.text("grid", px, bby, g.label(), TXT_GRID, COL_WALL);
                double bbyBot = bldOy + bHPaper + MARGIN_BOTTOM - GRID_CIRCLE_R - 3;
                svg.circle("grid", px, bbyBot, GRID_CIRCLE_R, COL_WALL, LW_WALL_INT, COL_BACKGROUND);
                svg.text("grid", px, bbyBot, g.label(), TXT_GRID, COL_WALL);
            } else {
                double py = toSheet.apply(0, g.position())[1];
                double xLeft  = bldOx - MARGIN_LEFT + 5;
                double xRight = bldOx + bWPaper + MARGIN_RIGHT - 5;
                svg.line("grid", xLeft, py, xRight, py, COL_GRID, LW_GRID, GRID_DASH);
                double bbx = bldOx - MARGIN_LEFT + GRID_CIRCLE_R + 3;
                svg.circle("grid", bbx, py, GRID_CIRCLE_R, COL_WALL, LW_WALL_INT, COL_BACKGROUND);
                svg.text("grid", bbx, py, g.label(), TXT_GRID, COL_WALL);
                double bbxR = bldOx + bWPaper + MARGIN_RIGHT - GRID_CIRCLE_R - 3;
                svg.circle("grid", bbxR, py, GRID_CIRCLE_R, COL_WALL, LW_WALL_INT, COL_BACKGROUND);
                svg.text("grid", bbxR, py, g.label(), TXT_GRID, COL_WALL);
            }
        }

        // Furniture (behind walls)
        for (Element f : elems.furniture()) {
            double[] p = toSheet.apply(f.minX(), f.maxY());
            svg.rect("furniture", p[0], p[1], f.widthX() * PAPER_FACTOR,
                f.widthY() * PAPER_FACTOR, COL_FURNITURE, LW_FURNITURE, COL_FURN_FILL);
        }

        // Wall section contours from mesh
        List<ElementSection> sections = SectionCut.execute(bldConn, DEFAULT_CUT_Z);
        int contourCount = 0;
        for (ElementSection se : sections) {
            if (!"CUT".equals(se.category()) || se.contours().isEmpty()) continue;
            for (SectionContour c : se.contours()) {
                List<double[]> pts = c.points().stream()
                    .map(p -> toSheet.apply(p[0], p[1])).collect(Collectors.toList());
                if (se.ifcClass().startsWith("IfcWall")) {
                    double lw = se.elementName().contains("Ext") ? LW_WALL_EXT : LW_WALL_INT;
                    svg.polygon("wall_fill", pts, COL_WALL, lw, COL_WALL);
                } else if ("IfcPlate".equals(se.ifcClass())) {
                    svg.polygon("wall_stroke", pts, COL_GLASS, LW_WALL_GLASS, "none");
                }
                contourCount++;
            }
        }
        BIMLogger.info(TAG, "Floor plan section contours: {}", contourCount);

        // Openings
        for (Element op : concat(elems.doors(), elems.windows())) {
            Element host = findHostWall(op, walls);
            if (host == null) continue;
            double[] p1 = toSheet.apply(op.minX(), op.maxY());
            double ow = op.widthX() * PAPER_FACTOR, oh = op.widthY() * PAPER_FACTOR;

            if (host.isEWWall()) {
                double[] hwp = toSheet.apply(op.minX(), host.maxY());
                double wallH = host.widthY() * PAPER_FACTOR;
                double midY = hwp[1] + wallH / 2;
                if ("IfcWindow".equals(op.ifcClass())) {
                    svg.line("opening", p1[0], midY - wallH*0.3, p1[0]+ow, midY - wallH*0.3,
                        COL_OPENING, LW_OPENING);
                    svg.line("opening", p1[0], midY + wallH*0.3, p1[0]+ow, midY + wallH*0.3,
                        COL_OPENING, LW_OPENING);
                    svg.line("opening", p1[0], midY, p1[0]+ow, midY, COL_GLASS, LW_WALL_GLASS);
                } else {
                    svg.line("opening", p1[0], midY, p1[0]+ow, midY, COL_OPENING, LW_OPENING);
                    svg.arc("opening", p1[0], midY, ow, 0, 90, COL_OPENING, LW_OPENING, true);
                }
            } else {
                double[] hwp = toSheet.apply(host.minX(), op.maxY());
                double wallW = host.widthX() * PAPER_FACTOR;
                double midX = hwp[0] + wallW / 2;
                if ("IfcWindow".equals(op.ifcClass())) {
                    svg.line("opening", midX - wallW*0.3, p1[1], midX - wallW*0.3, p1[1]+oh,
                        COL_OPENING, LW_OPENING);
                    svg.line("opening", midX + wallW*0.3, p1[1], midX + wallW*0.3, p1[1]+oh,
                        COL_OPENING, LW_OPENING);
                    svg.line("opening", midX, p1[1], midX, p1[1]+oh, COL_GLASS, LW_WALL_GLASS);
                } else {
                    svg.line("opening", midX, p1[1], midX, p1[1]+oh, COL_OPENING, LW_OPENING);
                    svg.arc("opening", midX, p1[1], oh, 90, 180, COL_OPENING, LW_OPENING, true);
                }
            }
        }

        drawScaleBar(svg, envX + MARGIN_LEFT, envY + MARGIN_TOP + bHPaper + 8);
        drawDrawingTitle(svg, bldOx + bWPaper / 2,
            bldOy + bHPaper + MARGIN_BOTTOM - 8, "PELAN LANTAI / FLOOR PLAN");

        return svg.toString();
    }

    // ── Elevation ─────────────────────────────────────────────────────────────

    public static String drawElevation(Elements elems, String face,
                                       Connection bldConn, DrawingMeta meta)
            throws SQLException {
        List<Element> walls = elems.walls();
        if (walls.isEmpty()) return "";

        // Determine view axis and element filter
        boolean mirror = "rear".equals(face) || "right".equals(face);
        boolean xAxis  = "front".equals(face) || "rear".equals(face);

        double faceBound = xAxis
            ? ("front".equals(face) ? walls.stream().mapToDouble(Element::minY).min().getAsDouble()
                                    : walls.stream().mapToDouble(Element::maxY).max().getAsDouble())
            : ("left".equals(face)  ? walls.stream().mapToDouble(Element::minX).min().getAsDouble()
                                    : walls.stream().mapToDouble(Element::maxX).max().getAsDouble());

        List<Element> faceElems = concat(walls, elems.doors(), elems.windows()).stream()
            .filter(e -> xAxis
                ? ("front".equals(face) ? e.minY() < faceBound + 1.0
                                        : e.maxY() > faceBound - 1.0)
                : ("left".equals(face)  ? e.minX() < faceBound + 1.0
                                        : e.maxX() > faceBound - 1.0))
            .toList();

        if (faceElems.isEmpty()) return "";

        // H key: horizontal position in the view (with mirror for rear/right)
        java.util.function.Function<Element, double[]> hKey = e -> {
            if (xAxis) return mirror ? new double[]{-e.maxX(), -e.minX()}
                                     : new double[]{e.minX(), e.maxX()};
            else       return mirror ? new double[]{-e.maxY(), -e.minY()}
                                     : new double[]{e.minY(), e.maxY()};
        };

        List<Element> allVis = concat(faceElems, elems.roofs(), elems.slabs());
        double hMin = allVis.stream().mapToDouble(e -> hKey.apply(e)[0]).min().getAsDouble();
        double hMax = allVis.stream().mapToDouble(e -> hKey.apply(e)[1]).max().getAsDouble();
        double vMin = Math.min(allVis.stream().mapToDouble(Element::minZ).min().getAsDouble(), GRD_Z);
        double vMax = allVis.stream().mapToDouble(Element::maxZ).max().getAsDouble();

        double elevW = (hMax - hMin) * PAPER_FACTOR;
        double elevH = (vMax - vMin) * PAPER_FACTOR;

        SVGBuilder svg = new SVGBuilder();
        SheetMeta sheet = tightSheet(meta.sheet(), elevW, elevH, 20, 109 + 25);

        double sw = sheet.widthMm(), sh = sheet.heightMm();
        svg.setViewbox(0, 0, sw, sh);

        double cLeft = sheet.marginLeft(), cTop = sheet.marginTop();
        double cRight = sw - sheet.marginRight() - sheet.titleBlockWidth();
        double cBottom = sh - sheet.marginBottom();
        double envW = MARGIN_LEFT + elevW + MARGIN_RIGHT;
        double envH = MARGIN_TOP + elevH + MARGIN_BOTTOM;
        double envX = cLeft + Math.max(0, (cRight - cLeft - envW) / 2);
        double envY = cTop  + Math.max(0, (cBottom - cTop - envH) / 2);
        double elevOx = envX + MARGIN_LEFT, elevOy = envY + MARGIN_TOP;

        // Compute grid extents BEFORE defining the lambda so captured vars are effectively final
        List<GridLine> elevGrids = GridDerivation.snapGrids(GridDerivation.deriveGrids(walls));
        String gridAxis = xAxis ? "x" : "y";
        List<GridLine> faceGrids = elevGrids.stream()
            .filter(g -> gridAxis.equals(g.axis()))
            .map(g -> new GridLine(g.label(), g.axis(),
                    mirror ? -g.position() : g.position()))
            .sorted(Comparator.comparingDouble(GridLine::position))
            .toList();

        final double hMinF = faceGrids.isEmpty() ? hMin
            : Math.min(hMin, faceGrids.get(0).position());
        final double hMaxF = faceGrids.isEmpty() ? hMax
            : Math.max(hMax, faceGrids.get(faceGrids.size()-1).position());
        final double vMaxF = vMax; // captured by lambda

        CoordXY toElev = (h, v) -> new double[]{
            (h - hMinF) * PAPER_FACTOR + elevOx,
            (vMaxF - v) * PAPER_FACTOR + elevOy
        };

        drawSheetBorder(svg, sheet);

        Map<String, String> faceTitles = Map.of(
            "front", "PANDANGAN HADAPAN / FRONT ELEVATION",
            "rear",  "PANDANGAN BELAKANG / REAR ELEVATION",
            "left",  "PANDANGAN KIRI / LEFT ELEVATION",
            "right", "PANDANGAN KANAN / RIGHT ELEVATION");
        Map<String, String> faceNums = Map.of(
            "front","A-02","rear","A-03","left","A-04","right","A-05");
        if (!meta.titleFields().isEmpty())
            drawTitleBlock(svg, sheet, meta.titleFields(),
                faceTitles.get(face), faceNums.get(face));

        // Roof hatch pattern — dense vertical lines
        svg.addDef("<pattern id=\"roof_hatch\" x=\"0\" y=\"0\" " +
            "width=\"1.0\" height=\"1.0\" patternUnits=\"userSpaceOnUse\">" +
            "<line x1=\"0.5\" y1=\"0\" x2=\"0.5\" y2=\"1.0\" " +
            "stroke=\"#000000\" stroke-width=\"0.25\"/></pattern>");

        double GRID_ABOVE = 20.0, BUBBLE_TOP = 5.0;
        for (GridLine g : faceGrids) {
            double gx = toElev.apply(g.position(), vMax)[0];
            double gyTop = toElev.apply(g.position(), vMax)[1] - GRID_ABOVE;
            double gyGrd = toElev.apply(g.position(), GRD_Z)[1];
            svg.line("grid", gx, gyTop + GRID_CIRCLE_R*2 + BUBBLE_TOP,
                gx, gyGrd, COL_GRID, LW_GRID, "4,1,1,1");
            double bcy = gyTop + GRID_CIRCLE_R + BUBBLE_TOP;
            svg.circle("grid", gx, bcy, GRID_CIRCLE_R, COL_WALL, LW_WALL_INT, COL_BACKGROUND);
            svg.text("grid", gx, bcy, g.label(), TXT_GRID, COL_WALL);
        }

        // Ground lines
        double[] grd1 = toElev.apply(hMinF - 0.5, GRD_Z);
        double[] grd2 = toElev.apply(hMaxF + 0.5, GRD_Z);
        svg.line("wall_stroke", grd1[0], grd1[1], grd2[0], grd2[1], COL_WALL, LW_GROUND);

        // Apron step
        double[] ap1 = toElev.apply(hMinF - 0.2, APRON_Z);
        double[] ap2 = toElev.apply(hMaxF + 0.2, GRD_Z);
        svg.rect("wall_fill", ap1[0], ap1[1], ap2[0]-ap1[0], ap2[1]-ap1[1],
            COL_WALL, LW_WALL_INT, COL_APRON);

        // FFL line
        double[] ffl1 = toElev.apply(hMinF - 0.3, 0.0);
        double[] ffl2 = toElev.apply(hMaxF + 0.3, 0.0);
        svg.line("wall_stroke", ffl1[0], ffl1[1], ffl2[0], ffl2[1], COL_WALL, 0.35);

        // Building silhouette — full facade bounding box before elements
        double[] silTL = toElev.apply(hMinF, vMaxF);
        double[] silBL = toElev.apply(hMinF, 0.0);
        double silW = (hMaxF - hMinF) * PAPER_FACTOR;
        double silH = silBL[1] - silTL[1];
        svg.rect("wall_fill", silTL[0], silTL[1], silW, silH, "none", 0, COL_FACADE);

        // Wall elements
        for (Element e : faceElems) {
            double[] hRange = hKey.apply(e);
            double x1 = toElev.apply(hRange[0], e.maxZ())[0];
            double y1 = toElev.apply(hRange[0], e.maxZ())[1];
            double w = (hRange[1] - hRange[0]) * PAPER_FACTOR;
            double h = (e.maxZ() - e.minZ()) * PAPER_FACTOR;

            switch (e.ifcClass()) {
                case "IfcWall", "IfcPlate" -> {
                    if (e.isGlass())
                        svg.rect("wall_fill", x1, y1, Math.max(w,0.5), Math.max(h,0.5),
                            COL_GLASS, LW_WALL_GLASS, COL_GLASS, 0.2);
                    else
                        svg.rect("wall_fill", x1, y1, Math.max(w,0.5), Math.max(h,0.5),
                            COL_WALL, LW_WALL_EXT, COL_WALL_FILL);
                }
                case "IfcWindow" ->
                    svg.rect("opening", x1, y1, w, h, COL_OPENING, LW_OPENING, "#FFFFFF", 1.0);
                case "IfcDoor" ->
                    svg.rect("opening", x1, y1, w, h, COL_OPENING, LW_OPENING, "#E8E8E8");
            }
        }

        // Roof silhouette from mesh convex hull
        List<double[]> roofHull = SectionCut.roofSilhouette(bldConn, face);
        if (!roofHull.isEmpty()) {
            List<double[]> hullPts = roofHull.stream()
                .map(p -> toElev.apply(p[0], p[1])).collect(Collectors.toList());
            StringBuilder pts = new StringBuilder();
            for (double[] p : hullPts) {
                if (pts.length() > 0) pts.append(' ');
                pts.append(String.format("%.3f,%.3f", p[0], p[1]));
            }
            svg.raw("wall_fill", "<polygon points=\"" + pts +
                "\" stroke=\"none\" fill=\"url(#roof_hatch)\" opacity=\"1.0\"/>");
            svg.polygon("wall_stroke", hullPts, COL_WALL, LW_WALL_EXT, "none");

            double eaveZ = roofHull.stream().mapToDouble(p -> p[1]).min().getAsDouble();
            List<double[]> eavePts = roofHull.stream()
                .filter(p -> Math.abs(p[1] - eaveZ) < 0.05)
                .sorted(Comparator.comparingDouble(p -> p[0])).toList();
            if (eavePts.size() >= 2) {
                double[] e1 = toElev.apply(eavePts.get(0)[0], eavePts.get(0)[1]);
                double[] e2 = toElev.apply(eavePts.get(eavePts.size()-1)[0],
                    eavePts.get(eavePts.size()-1)[1]);
                svg.line("wall_stroke", e1[0], e1[1], e2[0], e2[1], COL_WALL, LW_WALL_EXT);
            }
        }

        // Level markers (FFL, CLG, RIDGE — not APRON/GRD to avoid crowding)
        List<double[]> levels = MetadataReader.detectLevels(elems);
        String[] levelLabels = {"GRD. FLOOR LEVEL", "BEAM/CEILING LEVEL", "RIDGE LEVEL"};
        double markerX = elevOx - 5;
        double MIN_GAP = TXT_DIM * 3.0;

        List<double[]> markerYs = levels.stream()
            .map(lv -> new double[]{toElev.apply(hMinF, lv[0])[1], lv[0]})
            .sorted(Comparator.comparingDouble(r -> r[0]))
            .collect(Collectors.toList());

        // Push labels apart downward to prevent overlap
        double[] labelY = markerYs.stream().mapToDouble(r -> r[0]).toArray();
        for (int i = 1; i < labelY.length; i++) {
            if (labelY[i] - labelY[i-1] < MIN_GAP)
                labelY[i] = labelY[i-1] + MIN_GAP;
        }

        for (int i = 0; i < markerYs.size(); i++) {
            double ly  = markerYs.get(i)[0];  // true paper Y
            double lz  = markerYs.get(i)[1];  // world Z
            double lly = labelY[i];            // label paper Y (possibly pushed)
            String lbl = i < levelLabels.length ? levelLabels[i] : "LEVEL";

            svg.line("dimension", markerX - 18, ly, markerX, ly, COL_DIM, 0.35);
            svg.polygon("dimension",
                List.of(new double[]{markerX,ly}, new double[]{markerX-3.0,ly-1.8},
                        new double[]{markerX-3.0,ly+1.8}),
                COL_WALL, 0.35, COL_WALL);
            if (Math.abs(lly - ly) > 0.5)
                svg.line("dimension", markerX-16, ly, markerX-16, lly, COL_DIM, 0.18);
            svg.text("label", markerX - 21, lly - TXT_DIM*0.7, lbl,
                TXT_DIM, "end", COL_WALL);
            String sign = lz >= 0 ? "+" : "";
            svg.text("label", markerX - 21, lly + TXT_DIM*0.7,
                String.format("%s%.3f", sign, lz), TXT_DIM, "end", COL_WALL);
        }

        drawDrawingTitle(svg, elevOx + elevW/2, elevOy + elevH + MARGIN_BOTTOM - 8,
            faceTitles.get(face));

        BIMLogger.info(TAG, "Elevation ({}): {} elements", face, faceElems.size());
        return svg.toString();
    }

    // ── Sheet helpers ─────────────────────────────────────────────────────────

    /** Shrink sheet height to fit building envelope, respecting A3 maximum. */
    private static SheetMeta tightSheet(SheetMeta base, double bWPaper, double bHPaper,
                                        double extraH, double rightColH) {
        double envH = MARGIN_TOP + bHPaper + MARGIN_BOTTOM;
        double leftH = envH + extraH;
        double tightH = Math.max(leftH, rightColH)
            + base.marginTop() + base.marginBottom();
        double h = Math.min(tightH, base.heightMm());
        return new SheetMeta(base.widthMm(), h, base.marginLeft(), base.marginTop(),
            base.marginRight(), base.marginBottom(),
            base.titleBlockWidth(), base.borderWeight());
    }

    private static void drawSheetBorder(SVGBuilder svg, SheetMeta s) {
        svg.rect("border",
            s.marginLeft(), s.marginTop(),
            s.widthMm() - s.marginLeft() - s.marginRight(),
            s.heightMm() - s.marginTop() - s.marginBottom(),
            COL_WALL, s.borderWeight(), "none");
    }

    private static void drawTitleBlock(SVGBuilder svg, SheetMeta sheet,
                                       List<MetadataReader.TitleField> fields,
                                       String drawingTitle, String drawingNo) {
        double tbLeft  = sheet.widthMm() - sheet.marginRight() - sheet.titleBlockWidth();
        double tbTop   = sheet.marginTop();
        double tbW     = sheet.titleBlockWidth();
        double tbBot   = sheet.heightMm() - sheet.marginBottom();
        double bw      = sheet.borderWeight();
        double labelW  = tbW * 0.30;

        svg.line("title_block", tbLeft, tbTop, tbLeft, tbBot, COL_WALL, bw);

        double yCursor = tbBot;
        for (int i = fields.size() - 1; i >= 0; i--) {
            MetadataReader.TitleField tf = fields.get(i);
            yCursor -= tf.rowHeight();
            svg.line("title_block", tbLeft, yCursor, tbLeft + tbW, yCursor,
                COL_WALL, LW_DIMENSION);
            double divX = tbLeft + labelW;
            svg.line("title_block", divX, yCursor, divX, yCursor + tf.rowHeight(),
                COL_WALL, LW_DIMENSION);
            svg.text("title_block", tbLeft + labelW/2, yCursor + tf.rowHeight()/2,
                tf.labelText(), tf.labelSize(), COL_DIM);

            String val = tf.defaultValue() != null ? tf.defaultValue() : "";
            if ("TAJUK_LUKISAN".equals(tf.fieldName()) && drawingTitle != null)
                val = drawingTitle;
            else if ("NO_LUKISAN".equals(tf.fieldName()) && drawingNo != null)
                val = drawingNo;
            else if ("HARIBULAN".equals(tf.fieldName()))
                val = "2026";
            if (!val.isEmpty())
                svg.text("title_block", divX + (tbW - labelW)/2,
                    yCursor + tf.rowHeight()/2, val, tf.valueSize(), COL_WALL);
        }

        svg.text("title_block", tbLeft + tbW/2, tbTop + 10,
            "JABATAN KERJA RAYA", 3.0, COL_GRID);
        svg.text("title_block", tbLeft + tbW/2, tbTop + 15,
            "MALAYSIA", 2.5, COL_GRID);
    }

    private static void drawNorthArrow(SVGBuilder svg, double x, double y) {
        double sz = 8, half = sz/2, qtr = sz*0.25;
        svg.polygon("label",
            List.of(new double[]{x,y-half}, new double[]{x,y+half}, new double[]{x+qtr,y+half}),
            COL_WALL, 0.18, COL_WALL);
        svg.polygon("label",
            List.of(new double[]{x,y-half}, new double[]{x-qtr,y+half}, new double[]{x,y+half}),
            COL_WALL, 0.18, COL_BACKGROUND);
        svg.text("label", x, y - half - 2.5, "N", 3.0);
    }

    private static void drawScaleBar(SVGBuilder svg, double x, double y) {
        double barH = 2.0;
        double cx = x;
        boolean[] filled = {true, false, true};
        double[] widths = {10, 10, 30};
        for (int i = 0; i < 3; i++) {
            svg.rect("label", cx, y, widths[i], barH, COL_WALL, 0.18,
                filled[i] ? COL_WALL : COL_BACKGROUND);
            cx += widths[i];
        }
        for (double[] lbl : new double[][]{{x,0},{x+10,1},{x+20,2},{x+50,5}}) {
            svg.text("label", lbl[0], y + barH + 2.5,
                lbl[1] == 5 ? "5m" : String.valueOf((int)lbl[1]), 2.0);
        }
    }

    private static void drawDrawingTitle(SVGBuilder svg, double x, double y, String title) {
        svg.text("label", x, y, title, TXT_TITLE);
        svg.text("label", x, y + TXT_TITLE + 2,
            "scale 1:" + SCALE, TXT_DIM, COL_GRID);
    }

    // ── Geometry helpers ──────────────────────────────────────────────────────

    private static Element findHostWall(Element opening, List<Element> walls) {
        Element best = null;
        double bestScore = 0;
        for (Element w : walls) {
            double ox = Math.max(0, Math.min(opening.maxX(), w.maxX()) - Math.max(opening.minX(), w.minX()));
            double oy = Math.max(0, Math.min(opening.maxY(), w.maxY()) - Math.max(opening.minY(), w.minY()));
            double score = ox * oy;
            if (score == 0) {
                if (w.isEWWall() && opening.minX() >= w.minX()-0.1 && opening.maxX() <= w.maxX()+0.1) {
                    double dist = Math.abs(opening.centerY() - w.centerY());
                    if (dist < 0.5) score = 1.0 / (dist + 0.01);
                } else if (w.isNSWall() && opening.minY() >= w.minY()-0.1 && opening.maxY() <= w.maxY()+0.1) {
                    double dist = Math.abs(opening.centerX() - w.centerX());
                    if (dist < 0.5) score = 1.0 / (dist + 0.01);
                }
            }
            if (score > bestScore) { bestScore = score; best = w; }
        }
        return best;
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    @SafeVarargs
    private static <T> List<T> concat(List<T>... lists) {
        List<T> out = new ArrayList<>();
        for (List<T> l : lists) out.addAll(l);
        return out;
    }

    private static Connection openDb(String path) throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + path);
    }

    private static void write(String path, String content) throws IOException {
        Files.writeString(Path.of(path), content);
    }
}
