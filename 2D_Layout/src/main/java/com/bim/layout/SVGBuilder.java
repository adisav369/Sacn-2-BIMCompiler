package com.bim.layout;

import java.util.*;

import static com.bim.layout.LayoutConstants.*;

/**
 * SVG document builder for architectural drawing output.
 *
 * Layer-based model: each primitive is added to a named layer.
 * Layer draw order: border → grid → furniture → wall_fill → wall_stroke
 *                  → opening → dimension → room_label → title_block → label
 *
 * All coordinates are in paper-mm matching the SVG viewBox.
 */
public class SVGBuilder {

    private static final List<String> LAYER_ORDER = List.of(
        "border", "grid", "furniture", "wall_fill", "wall_stroke",
        "opening", "dimension", "room_label", "title_block", "label"
    );

    private final Map<String, List<String>> layers = new LinkedHashMap<>();
    private final List<String> defs = new ArrayList<>();

    private double viewMinX, viewMinY, viewWidth = 100, viewHeight = 100;

    public SVGBuilder() {
        for (String layer : LAYER_ORDER) {
            layers.put(layer, new ArrayList<>());
        }
    }

    // ── Config ───────────────────────────────────────────────────────────────

    public void addDef(String content) { defs.add(content); }

    public void setViewbox(double minX, double minY, double width, double height) {
        this.viewMinX = minX;  this.viewMinY = minY;
        this.viewWidth = width; this.viewHeight = height;
    }

    // ── Primitives ───────────────────────────────────────────────────────────

    public void rect(String layer, double x, double y, double w, double h,
                     String stroke, double sw, String fill) {
        add(layer, String.format(
            "<rect x=\"%.3f\" y=\"%.3f\" width=\"%.3f\" height=\"%.3f\" " +
            "stroke=\"%s\" stroke-width=\"%.3f\" fill=\"%s\"/>",
            x, y, w, h, stroke, sw, fill));
    }

    public void rect(String layer, double x, double y, double w, double h,
                     String stroke, double sw, String fill, double opacity) {
        add(layer, String.format(
            "<rect x=\"%.3f\" y=\"%.3f\" width=\"%.3f\" height=\"%.3f\" " +
            "stroke=\"%s\" stroke-width=\"%.3f\" fill=\"%s\" opacity=\"%.2f\"/>",
            x, y, w, h, stroke, sw, fill, opacity));
    }

    public void line(String layer, double x1, double y1, double x2, double y2,
                     String stroke, double sw) {
        add(layer, String.format(
            "<line x1=\"%.3f\" y1=\"%.3f\" x2=\"%.3f\" y2=\"%.3f\" " +
            "stroke=\"%s\" stroke-width=\"%.3f\"/>",
            x1, y1, x2, y2, stroke, sw));
    }

    public void line(String layer, double x1, double y1, double x2, double y2,
                     String stroke, double sw, String dash) {
        add(layer, String.format(
            "<line x1=\"%.3f\" y1=\"%.3f\" x2=\"%.3f\" y2=\"%.3f\" " +
            "stroke=\"%s\" stroke-width=\"%.3f\" stroke-dasharray=\"%s\"/>",
            x1, y1, x2, y2, stroke, sw, dash));
    }

    public void circle(String layer, double cx, double cy, double r,
                       String stroke, double sw, String fill) {
        add(layer, String.format(
            "<circle cx=\"%.3f\" cy=\"%.3f\" r=\"%.3f\" " +
            "stroke=\"%s\" stroke-width=\"%.3f\" fill=\"%s\"/>",
            cx, cy, r, stroke, sw, fill));
    }

    /** Door swing arc. cw = clockwise in SVG coords. */
    public void arc(String layer, double cx, double cy, double r,
                    double startDeg, double endDeg, String stroke, double sw, boolean cw) {
        double x1 = cx + r * Math.cos(Math.toRadians(startDeg));
        double y1 = cy + r * Math.sin(Math.toRadians(startDeg));
        double x2 = cx + r * Math.cos(Math.toRadians(endDeg));
        double y2 = cy + r * Math.sin(Math.toRadians(endDeg));
        double span = cw ? (endDeg - startDeg + 360) % 360
                         : (startDeg - endDeg + 360) % 360;
        add(layer, String.format(
            "<path d=\"M %.3f,%.3f A %.3f,%.3f 0 %d,%d %.3f,%.3f\" " +
            "stroke=\"%s\" stroke-width=\"%.3f\" fill=\"none\"/>",
            x1, y1, r, r, span > 180 ? 1 : 0, cw ? 1 : 0, x2, y2, stroke, sw));
    }

    public void text(String layer, double x, double y, String content, double size) {
        text(layer, x, y, content, size, "middle", COL_WALL, 0);
    }

    public void text(String layer, double x, double y, String content,
                     double size, String color) {
        text(layer, x, y, content, size, "middle", color, 0);
    }

    public void text(String layer, double x, double y, String content,
                     double size, String anchor, String color) {
        text(layer, x, y, content, size, anchor, color, 0);
    }

    public void text(String layer, double x, double y, String content,
                     double size, String anchor, String color, double rotate) {
        String safe = content.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        String xform = rotate != 0
            ? String.format(" transform=\"rotate(%.1f,%.3f,%.3f)\"", rotate, x, y) : "";
        add(layer, String.format(
            "<text x=\"%.3f\" y=\"%.3f\" font-size=\"%.2f\" fill=\"%s\" " +
            "text-anchor=\"%s\" dominant-baseline=\"central\"%s>%s</text>",
            x, y, size, color, anchor, xform, safe));
    }

    /** 45° tick mark (TB-LKTN dimension convention). */
    public void tickMark(String layer, double x, double y, String axis,
                         String stroke, double sw) {
        double d = DIM_TICK_SIZE;
        if ("x".equals(axis)) line(layer, x - d, y + d, x + d, y - d, stroke, sw);
        else                  line(layer, x - d, y - d, x + d, y + d, stroke, sw);
    }

    /** Closed polygon from list of {x, y} pairs. */
    public void polygon(String layer, List<double[]> points,
                        String stroke, double sw, String fill) {
        polygon(layer, points, stroke, sw, fill, 1.0);
    }

    public void polygon(String layer, List<double[]> points,
                        String stroke, double sw, String fill, double opacity) {
        StringBuilder pts = new StringBuilder();
        for (double[] p : points) {
            if (pts.length() > 0) pts.append(' ');
            pts.append(String.format("%.3f,%.3f", p[0], p[1]));
        }
        add(layer, String.format(
            "<polygon points=\"%s\" stroke=\"%s\" stroke-width=\"%.3f\" " +
            "fill=\"%s\" opacity=\"%.2f\" stroke-linejoin=\"round\"/>",
            pts, stroke, sw, fill, opacity));
    }

    /** Insert raw SVG into a layer (e.g. polygon with pattern fill). */
    public void raw(String layer, String svg) { add(layer, svg); }

    // ── Output ───────────────────────────────────────────────────────────────

    @Override
    public String toString() {
        String vb = String.format("%.2f %.2f %.2f %.2f",
            viewMinX, viewMinY, viewWidth, viewHeight);
        StringBuilder sb = new StringBuilder(8192);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
          .append(String.format(
            "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"%s\" " +
            "width=\"%d\" height=\"%d\" style=\"background:%s\">\n",
            vb, (int)(viewWidth * 3), (int)(viewHeight * 3), COL_BACKGROUND))
          .append("  <defs>\n")
          .append("    <style>text { font-family: \"Arial\", \"Helvetica\", sans-serif; }</style>\n");
        for (String d : defs) sb.append("    ").append(d).append('\n');
        sb.append("  </defs>\n");
        for (String name : LAYER_ORDER) {
            List<String> items = layers.getOrDefault(name, List.of());
            if (!items.isEmpty()) {
                sb.append("  <g id=\"").append(name).append("\">\n");
                for (String item : items) sb.append("    ").append(item).append('\n');
                sb.append("  </g>\n");
            }
        }
        sb.append("</svg>");
        return sb.toString();
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private void add(String layer, String svg) {
        layers.computeIfAbsent(layer, k -> new ArrayList<>()).add(svg);
    }
}
