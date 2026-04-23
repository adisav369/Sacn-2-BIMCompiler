package com.bim.compiler.drawing2d.dxf;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Minimal DXF R2010 writer for 2D architectural drawings.
 *
 * Writes ASCII DXF covering the entity types used by the BIM drawing engine:
 * LWPOLYLINE, LINE, TEXT, CIRCLE, ARC, HATCH, SOLID, INSERT.
 *
 * DXF format: sequence of group code (integer) + value pairs, grouped into
 * sections (HEADER, TABLES, BLOCKS, ENTITIES, OBJECTS). Each section starts
 * with (0, "SECTION") + (2, section_name) and ends with (0, "ENDSEC").
 *
 * Handle management: auto-incrementing hex handles starting at 0x100.
 * The DXF spec requires every entity to have a unique handle (group 5).
 *
 * Spec ref: internal/2D_JAVA_MIGRATION.md Phase 0.
 * Implementing BBC.md §1 R1 — no invention.
 */
public class DxfWriter implements AutoCloseable {

    private final PrintWriter out;
    private int nextHandle = 0x100;
    private final List<DxfLayer> layers = new ArrayList<>();
    private final List<BlockDef> blocks = new ArrayList<>();
    private final List<String> entityBuffer = new ArrayList<>();
    private String modelSpaceHandle;

    // ── Construction ─────────────────────────────────────────────

    public DxfWriter(OutputStream output) {
        this.out = new PrintWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8));
    }

    public DxfWriter(File file) throws FileNotFoundException {
        this(new FileOutputStream(file));
    }

    // ── Handle management ────────────────────────────────────────

    private String newHandle() {
        return Integer.toHexString(nextHandle++).toUpperCase();
    }

    // ── Layer definition ─────────────────────────────────────────

    public void addLayer(String name, int color) {
        layers.add(new DxfLayer(name, color, "CONTINUOUS"));
    }

    public void addLayer(String name, int color, String linetype) {
        layers.add(new DxfLayer(name, color, linetype));
    }

    // ── Block definition ─────────────────────────────────────────

    /** Start defining a named block. Returns a BlockDef to add entities to. */
    public BlockDef defineBlock(String name) {
        BlockDef blk = new BlockDef(name);
        blocks.add(blk);
        return blk;
    }

    // ── Entity creation (modelspace) ─────────────────────────────

    // ── Helper: entity preamble (handle + AcDbEntity subclass) ─────

    private static StringBuilder entityHead(String type, String handle, String layer, int lineweight) {
        StringBuilder sb = new StringBuilder();
        sb.append(gc(0, type));
        sb.append(gc(5, handle));
        sb.append(gc(100, "AcDbEntity"));
        sb.append(gc(8, layer));
        if (lineweight >= 0) sb.append(gc(370, lineweight));
        return sb;
    }

    /** Add a LINE entity. */
    public void addLine(double x1, double y1, double x2, double y2,
                        String layer, int lineweight) {
        StringBuilder sb = entityHead("LINE", newHandle(), layer, lineweight);
        sb.append(gc(100, "AcDbLine"));
        sb.append(gc(10, x1)).append(gc(20, y1)).append(gc(30, 0.0));
        sb.append(gc(11, x2)).append(gc(21, y2)).append(gc(31, 0.0));
        entityBuffer.add(sb.toString());
    }

    /** Add an LWPOLYLINE entity. */
    public void addLwPolyline(double[][] points, boolean close,
                              String layer, int lineweight) {
        StringBuilder sb = entityHead("LWPOLYLINE", newHandle(), layer, lineweight);
        sb.append(gc(100, "AcDbPolyline"));
        sb.append(gc(90, points.length));       // vertex count
        sb.append(gc(70, close ? 1 : 0));       // closed flag
        for (double[] pt : points) {
            sb.append(gc(10, pt[0])).append(gc(20, pt[1]));
        }
        entityBuffer.add(sb.toString());
    }

    /** Add a TEXT entity with MIDDLE_CENTER alignment. */
    public void addText(String text, double x, double y, double height,
                        String layer) {
        addText(text, x, y, height, layer, 1, 2); // 1=CENTER, 2=MIDDLE
    }

    /** Add a TEXT entity with specified alignment.
     *  halign: 0=LEFT, 1=CENTER, 2=RIGHT
     *  valign: 0=BASELINE, 1=BOTTOM, 2=MIDDLE, 3=TOP */
    public void addText(String text, double x, double y, double height,
                        String layer, int halign, int valign) {
        StringBuilder sb = entityHead("TEXT", newHandle(), layer, -1);
        sb.append(gc(100, "AcDbText"));
        sb.append(gc(10, x)).append(gc(20, y)).append(gc(30, 0.0));
        sb.append(gc(40, height));
        sb.append(gc(1, text));
        if (halign != 0 || valign != 0) {
            sb.append(gc(72, halign));
            // Alignment point (group 11) required when aligned
            sb.append(gc(11, x)).append(gc(21, y)).append(gc(31, 0.0));
        }
        sb.append(gc(100, "AcDbText"));
        if (valign != 0) {
            sb.append(gc(73, valign));
        }
        entityBuffer.add(sb.toString());
    }

    /** Add a CIRCLE entity. */
    public void addCircle(double cx, double cy, double radius,
                          String layer, int lineweight) {
        StringBuilder sb = entityHead("CIRCLE", newHandle(), layer, lineweight);
        sb.append(gc(100, "AcDbCircle"));
        sb.append(gc(10, cx)).append(gc(20, cy)).append(gc(30, 0.0));
        sb.append(gc(40, radius));
        entityBuffer.add(sb.toString());
    }

    /** Add an ARC entity. Angles in degrees, CCW from positive X. */
    public void addArc(double cx, double cy, double radius,
                       double startAngle, double endAngle,
                       String layer, int lineweight) {
        StringBuilder sb = entityHead("ARC", newHandle(), layer, lineweight);
        sb.append(gc(100, "AcDbCircle"));
        sb.append(gc(10, cx)).append(gc(20, cy)).append(gc(30, 0.0));
        sb.append(gc(40, radius));
        sb.append(gc(100, "AcDbArc"));
        sb.append(gc(50, startAngle));
        sb.append(gc(51, endAngle));
        entityBuffer.add(sb.toString());
    }

    /** Add a SOLID entity (filled triangle or quad). */
    public void addSolid(double x1, double y1, double x2, double y2,
                         double x3, double y3, String layer) {
        StringBuilder sb = entityHead("SOLID", newHandle(), layer, -1);
        sb.append(gc(100, "AcDbTrace"));
        sb.append(gc(10, x1)).append(gc(20, y1)).append(gc(30, 0.0));
        sb.append(gc(11, x2)).append(gc(21, y2)).append(gc(31, 0.0));
        sb.append(gc(12, x3)).append(gc(22, y3)).append(gc(32, 0.0));
        sb.append(gc(13, x3)).append(gc(23, y3)).append(gc(33, 0.0));
        entityBuffer.add(sb.toString());
    }

    /** Add a block INSERT entity with uniform scale. */
    public void addInsert(String blockName, double x, double y,
                          double xscale, double yscale, String layer) {
        StringBuilder sb = entityHead("INSERT", newHandle(), layer, -1);
        sb.append(gc(100, "AcDbBlockReference"));
        sb.append(gc(2, blockName));
        sb.append(gc(10, x)).append(gc(20, y)).append(gc(30, 0.0));
        sb.append(gc(41, xscale)).append(gc(42, yscale));
        entityBuffer.add(sb.toString());
    }

    /** Add XDATA to the last entity. appId must be registered first. */
    public void addXDataToLast(String appId, String... values) {
        if (entityBuffer.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        sb.append(gc(1001, appId));
        for (String v : values) {
            sb.append(gc(1000, v));
        }
        // Append to last entity (before the next entity starts)
        int lastIdx = entityBuffer.size() - 1;
        entityBuffer.set(lastIdx, entityBuffer.get(lastIdx) + sb);
    }

    // ── XDATA Application ID registration ────────────────────────

    private final Set<String> registeredAppIds = new LinkedHashSet<>();

    public void registerAppId(String appId) {
        registeredAppIds.add(appId);
    }

    // ── File output ──────────────────────────────────────────────

    /** Write the complete DXF file and close. */
    public void writeDxf() {
        writeHeader();
        writeTables();
        writeBlocks();
        writeEntities();
        writeObjects();
        out.print(gc(0, "EOF"));
        out.flush();
    }

    @Override
    public void close() {
        out.close();
    }

    // ── DXF sections ─────────────────────────────────────────────

    private void writeHeader() {
        out.print(gc(0, "SECTION"));
        out.print(gc(2, "HEADER"));
        out.print(gc(9, "$ACADVER"));
        out.print(gc(1, "AC1024"));     // DXF R2010
        out.print(gc(9, "$INSBASE"));
        out.print(gc(10, 0.0)); out.print(gc(20, 0.0)); out.print(gc(30, 0.0));
        out.print(gc(9, "$HANDSEED"));
        out.print(gc(5, "FFFF"));       // will be patched
        out.print(gc(0, "ENDSEC"));
    }

    private void writeTables() {
        out.print(gc(0, "SECTION"));
        out.print(gc(2, "TABLES"));

        // LTYPE table — linetypes used by layers
        Set<String> linetypes = new LinkedHashSet<>();
        linetypes.add("CONTINUOUS");
        for (DxfLayer l : layers) linetypes.add(l.linetype());
        linetypes.add("HIDDEN");
        linetypes.add("DASHDOT");

        out.print(gc(0, "TABLE"));
        out.print(gc(2, "LTYPE"));
        out.print(gc(5, newHandle()));
        out.print(gc(70, linetypes.size()));
        for (String lt : linetypes) {
            out.print(gc(0, "LTYPE"));
            out.print(gc(5, newHandle()));
            out.print(gc(2, lt));
            out.print(gc(70, 0));
            out.print(gc(3, lt));
            if ("CONTINUOUS".equals(lt)) {
                out.print(gc(72, 65)); out.print(gc(73, 0)); out.print(gc(40, 0.0));
            } else if ("HIDDEN".equals(lt)) {
                out.print(gc(72, 65)); out.print(gc(73, 2)); out.print(gc(40, 6.35));
                out.print(gc(49, 3.175)); out.print(gc(74, 0));
                out.print(gc(49, -3.175)); out.print(gc(74, 0));
            } else if ("DASHDOT".equals(lt)) {
                out.print(gc(72, 65)); out.print(gc(73, 4)); out.print(gc(40, 12.7));
                out.print(gc(49, 6.35)); out.print(gc(74, 0));
                out.print(gc(49, -3.175)); out.print(gc(74, 0));
                out.print(gc(49, 0.0)); out.print(gc(74, 0));
                out.print(gc(49, -3.175)); out.print(gc(74, 0));
            } else {
                out.print(gc(72, 65)); out.print(gc(73, 0)); out.print(gc(40, 0.0));
            }
        }
        out.print(gc(0, "ENDTAB"));

        // LAYER table
        out.print(gc(0, "TABLE"));
        out.print(gc(2, "LAYER"));
        out.print(gc(5, newHandle()));
        out.print(gc(70, layers.size() + 1));
        // Default layer 0
        out.print(gc(0, "LAYER"));
        out.print(gc(5, newHandle()));
        out.print(gc(2, "0"));
        out.print(gc(70, 0));
        out.print(gc(62, 7));
        out.print(gc(6, "CONTINUOUS"));
        for (DxfLayer l : layers) {
            out.print(gc(0, "LAYER"));
            out.print(gc(5, newHandle()));
            out.print(gc(2, l.name()));
            out.print(gc(70, 0));
            out.print(gc(62, l.color()));
            out.print(gc(6, l.linetype()));
        }
        out.print(gc(0, "ENDTAB"));

        // APPID table — for XDATA
        if (!registeredAppIds.isEmpty()) {
            out.print(gc(0, "TABLE"));
            out.print(gc(2, "APPID"));
            out.print(gc(5, newHandle()));
            out.print(gc(70, registeredAppIds.size() + 1));
            out.print(gc(0, "APPID"));
            out.print(gc(5, newHandle()));
            out.print(gc(2, "ACAD"));
            out.print(gc(70, 0));
            for (String appId : registeredAppIds) {
                out.print(gc(0, "APPID"));
                out.print(gc(5, newHandle()));
                out.print(gc(2, appId));
                out.print(gc(70, 0));
            }
            out.print(gc(0, "ENDTAB"));
        }

        // BLOCK_RECORD table (required for R2010)
        out.print(gc(0, "TABLE"));
        out.print(gc(2, "BLOCK_RECORD"));
        out.print(gc(5, newHandle()));
        out.print(gc(70, blocks.size() + 2));
        // *Model_Space record
        modelSpaceHandle = newHandle();
        out.print(gc(0, "BLOCK_RECORD"));
        out.print(gc(5, modelSpaceHandle));
        out.print(gc(2, "*Model_Space"));
        // *Paper_Space record
        out.print(gc(0, "BLOCK_RECORD"));
        out.print(gc(5, newHandle()));
        out.print(gc(2, "*Paper_Space"));
        for (BlockDef blk : blocks) {
            blk.recordHandle = newHandle();
            out.print(gc(0, "BLOCK_RECORD"));
            out.print(gc(5, blk.recordHandle));
            out.print(gc(2, blk.name));
        }
        out.print(gc(0, "ENDTAB"));

        out.print(gc(0, "ENDSEC"));
    }

    private void writeBlockPair(String name, List<String> bodyEntities) {
        out.print(gc(0, "BLOCK"));
        out.print(gc(5, newHandle()));
        out.print(gc(100, "AcDbEntity"));
        out.print(gc(8, "0"));
        out.print(gc(100, "AcDbBlockBegin"));
        out.print(gc(2, name));
        out.print(gc(70, 0));
        out.print(gc(10, 0.0)); out.print(gc(20, 0.0)); out.print(gc(30, 0.0));
        out.print(gc(3, name));
        for (String ent : bodyEntities) {
            out.print(ent);
        }
        out.print(gc(0, "ENDBLK"));
        out.print(gc(5, newHandle()));
        out.print(gc(100, "AcDbEntity"));
        out.print(gc(8, "0"));
        out.print(gc(100, "AcDbBlockEnd"));
    }

    private void writeBlocks() {
        out.print(gc(0, "SECTION"));
        out.print(gc(2, "BLOCKS"));

        // *Model_Space block
        writeBlockPair("*Model_Space", Collections.emptyList());
        // *Paper_Space block
        writeBlockPair("*Paper_Space", Collections.emptyList());
        // User-defined blocks
        for (BlockDef blk : blocks) {
            writeBlockPair(blk.name, blk.entities);
        }

        out.print(gc(0, "ENDSEC"));
    }

    private void writeEntities() {
        out.print(gc(0, "SECTION"));
        out.print(gc(2, "ENTITIES"));
        for (String ent : entityBuffer) {
            out.print(ent);
        }
        out.print(gc(0, "ENDSEC"));
    }

    private void writeObjects() {
        out.print(gc(0, "SECTION"));
        out.print(gc(2, "OBJECTS"));
        // Minimal OBJECTS section — root dictionary
        out.print(gc(0, "DICTIONARY"));
        out.print(gc(5, newHandle()));
        out.print(gc(330, "0"));
        out.print(gc(0, "ENDSEC"));
    }

    // ── Group code formatting ────────────────────────────────────

    private static String gc(int code, String value) {
        return String.format("%3d\n%s\n", code, value);
    }

    private static String gc(int code, int value) {
        return String.format("%3d\n%d\n", code, value);
    }

    private static String gc(int code, double value) {
        return String.format("%3d\n%.6f\n", code, value);
    }

    // ── Inner classes ────────────────────────────────────────────

    /** Block definition — collects entities during definition, written later. */
    public class BlockDef {
        final String name;
        String recordHandle;
        final List<String> entities = new ArrayList<>();

        BlockDef(String name) {
            this.name = name;
        }

        public void addLine(double x1, double y1, double x2, double y2,
                            String layer, int lineweight) {
            StringBuilder sb = entityHead("LINE", newHandle(), layer, lineweight);
            sb.append(gc(100, "AcDbLine"));
            sb.append(gc(10, x1)).append(gc(20, y1)).append(gc(30, 0.0));
            sb.append(gc(11, x2)).append(gc(21, y2)).append(gc(31, 0.0));
            entities.add(sb.toString());
        }

        public void addCircle(double cx, double cy, double radius,
                              String layer, int lineweight) {
            StringBuilder sb = entityHead("CIRCLE", newHandle(), layer, lineweight);
            sb.append(gc(100, "AcDbCircle"));
            sb.append(gc(10, cx)).append(gc(20, cy)).append(gc(30, 0.0));
            sb.append(gc(40, radius));
            entities.add(sb.toString());
        }

        public void addLwPolyline(double[][] points, boolean close,
                                  String layer, int lineweight) {
            StringBuilder sb = entityHead("LWPOLYLINE", newHandle(), layer, lineweight);
            sb.append(gc(100, "AcDbPolyline"));
            sb.append(gc(90, points.length));
            sb.append(gc(70, close ? 1 : 0));
            for (double[] pt : points) {
                sb.append(gc(10, pt[0])).append(gc(20, pt[1]));
            }
            entities.add(sb.toString());
        }
    }
}
