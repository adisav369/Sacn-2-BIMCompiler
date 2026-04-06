package com.bim.layout;

import com.bim.orm.BIMLogger;

import java.sql.*;
import java.util.*;

/**
 * Reads 2D drawing elements and metadata from the compiled building database.
 *
 * Schema consumed: elements_meta JOIN elements_rtree (spatial bounding boxes).
 * Metadata schema: ad_sheet_template, ad_title_block, ad_room_label, ad_annotation_tag.
 */
public final class MetadataReader {
    private MetadataReader() {}

    private static final String TAG = "MetadataReader";

    // ── Element record ────────────────────────────────────────────────────────

    /**
     * A building element with spatial bounding box (world metres).
     * Replaces Python @dataclass Element — all geometry as plain doubles.
     */
    public record Element(
        String ifcClass, String name, String storey,
        double minX, double maxX, double minY, double maxY,
        double minZ, double maxZ
    ) {
        public double widthX()  { return Math.abs(maxX - minX); }
        public double widthY()  { return Math.abs(maxY - minY); }
        public double centerX() { return (minX + maxX) / 2; }
        public double centerY() { return (minY + maxY) / 2; }

        /** Wall runs north-south (thin X, long Y). */
        public boolean isNSWall() { return widthX() < widthY(); }
        /** Wall runs east-west (long X, thin Y). */
        public boolean isEWWall() { return widthX() >= widthY(); }

        public boolean isExterior()  { return name.contains("Ext"); }
        public boolean isGlass()     { return name.contains("Glazed") || name.contains("Curtain"); }
        public boolean isPartition() { return name.contains("Partn"); }
    }

    // ── Sheet metadata record (from ad_sheet_template) ────────────────────────

    public record SheetMeta(
        double widthMm, double heightMm,
        double marginLeft, double marginTop, double marginRight, double marginBottom,
        double titleBlockWidth, double borderWeight
    ) {
        /** Default A3 sheet when database is absent. */
        public static SheetMeta defaults() {
            return new SheetMeta(420, 297, 25, 10, 10, 10, 120, 0.7);
        }
    }

    /** One row from ad_title_block. */
    public record TitleField(
        String fieldName, String labelText, double labelSize,
        String defaultValue, double valueSize, double rowHeight, int fieldOrder
    ) {}

    /** Room label entry from ad_room_label. */
    public record RoomLabel(String spaceType, String labelText, String language) {}

    /** Full drawing metadata bundle. */
    public record DrawingMeta(
        SheetMeta sheet,
        List<TitleField> titleFields,
        Map<String, RoomLabel> roomLabels
    ) {}

    // ── Element categories ────────────────────────────────────────────────────

    public record Elements(
        List<Element> walls,
        List<Element> doors,
        List<Element> windows,
        List<Element> furniture,
        List<Element> slabs,
        List<Element> roofs
    ) {
        public List<Element> all() {
            List<Element> all = new ArrayList<>();
            all.addAll(walls); all.addAll(doors); all.addAll(windows);
            all.addAll(furniture); all.addAll(slabs); all.addAll(roofs);
            return all;
        }
    }

    // ── DB reads ──────────────────────────────────────────────────────────────

    /**
     * Read all building elements from a compiled spatial DB.
     *
     * @param conn  connection to the extracted building DB (e.g. ifc4_sample_house.db)
     */
    public static Elements readElements(Connection conn) throws SQLException {
        return readElements(conn, null);
    }

    /**
     * Read elements with optional storey filter (skips roof-level non-roof elements).
     */
    public static Elements readElements(Connection conn, String storeyFilter)
            throws SQLException {
        String sql = """
            SELECT m.ifc_class, m.element_name, m.storey,
                   r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ
            FROM elements_meta m
            JOIN elements_rtree r ON m.id = r.id
            ORDER BY m.ifc_class, m.element_name
            """;

        List<Element> walls = new ArrayList<>(), doors = new ArrayList<>(),
            windows = new ArrayList<>(), furniture = new ArrayList<>(),
            slabs = new ArrayList<>(), roofs = new ArrayList<>();

        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Element e = new Element(
                    rs.getString(1), rs.getString(2), rs.getString(3),
                    rs.getDouble(4), rs.getDouble(5),
                    rs.getDouble(6), rs.getDouble(7),
                    rs.getDouble(8), rs.getDouble(9)
                );
                if (storeyFilter != null && e.storey() != null
                        && e.storey().contains("Roof")
                        && !e.ifcClass().equals("IfcRoof")) continue;

                switch (e.ifcClass()) {
                    case "IfcWall", "IfcPlate"                     -> walls.add(e);
                    case "IfcDoor"                                  -> doors.add(e);
                    case "IfcWindow"                                -> windows.add(e);
                    case "IfcFurnishingElement", "IfcFurniture"     -> furniture.add(e);
                    case "IfcSlab"                                  -> slabs.add(e);
                    case "IfcRoof"                                  -> roofs.add(e);
                    default -> {} // skip other classes
                }
            }
        }
        BIMLogger.info(TAG, "readElements: {} walls {} doors {} windows {} furniture {} slabs {} roofs",
            walls.size(), doors.size(), windows.size(), furniture.size(), slabs.size(), roofs.size());
        return new Elements(walls, doors, windows, furniture, slabs, roofs);
    }

    /**
     * Load drawing metadata from the 2D_metadata.db companion database.
     * Returns defaults if the database or tables are absent.
     */
    public static DrawingMeta readDrawingMeta(Connection metaConn) {
        try {
            SheetMeta sheet = readSheet(metaConn);
            List<TitleField> fields = readTitleFields(metaConn);
            Map<String, RoomLabel> labels = readRoomLabels(metaConn);
            return new DrawingMeta(sheet, fields, labels);
        } catch (SQLException e) {
            BIMLogger.warn(TAG, "metadata read failed ({}), using defaults", e.getMessage());
            return new DrawingMeta(SheetMeta.defaults(), List.of(), Map.of());
        }
    }

    // ── Level detection ───────────────────────────────────────────────────────

    /**
     * Detect building height levels from element Z extents.
     * Returns sorted list of (label, z_metres) pairs.
     * APRON and GRD are NOT included — they are drawn as geometry, not label markers.
     */
    public static List<double[]> detectLevels(Elements elems) {
        List<double[]> levels = new ArrayList<>();
        levels.add(new double[]{0.0}); // FFL always at 0 — label added by caller

        // Ceiling: mode of interior partition tops
        List<Double> partitionTops = elems.walls().stream()
            .filter(w -> !w.isExterior() && !w.isGlass())
            .map(Element::maxZ).toList();
        if (!partitionTops.isEmpty()) {
            Map<Long, Long> freq = new HashMap<>();
            for (double z : partitionTops) {
                long key = Math.round(z * 1000.0 / LayoutConstants.SNAP_MODULE);
                freq.merge(key, 1L, Long::sum);
            }
            long modeKey = freq.entrySet().stream()
                .max(Map.Entry.comparingByValue()).get().getKey();
            levels.add(new double[]{modeKey * LayoutConstants.SNAP_MODULE / 1000.0});
        }

        // Ridge: highest roof point
        elems.roofs().stream()
            .mapToDouble(Element::maxZ).max()
            .ifPresent(ridge -> {
                double snapped = Math.round(ridge * 1000.0 / LayoutConstants.SNAP_MODULE)
                    * LayoutConstants.SNAP_MODULE / 1000.0;
                levels.add(new double[]{snapped});
            });

        levels.sort(Comparator.comparingDouble(a -> a[0]));
        return levels;
    }

    // ── Privates ──────────────────────────────────────────────────────────────

    private static SheetMeta readSheet(Connection c) throws SQLException {
        String sql = "SELECT width_mm, height_mm, margin_left, margin_top, " +
                     "margin_right, margin_bottom, title_block_width, border_weight " +
                     "FROM ad_sheet_template WHERE profile_id='JKR_Malaysian' AND paper_size='A3'";
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) {
                return new SheetMeta(rs.getDouble(1), rs.getDouble(2),
                    rs.getDouble(3), rs.getDouble(4), rs.getDouble(5),
                    rs.getDouble(6), rs.getDouble(7), rs.getDouble(8));
            }
        }
        return SheetMeta.defaults();
    }

    private static List<TitleField> readTitleFields(Connection c) throws SQLException {
        String sql = "SELECT field_name, label_text, label_size, default_value, " +
                     "value_size, row_height, field_order " +
                     "FROM ad_title_block WHERE profile_id='JKR_Malaysian' ORDER BY field_order";
        List<TitleField> fields = new ArrayList<>();
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                fields.add(new TitleField(rs.getString(1), rs.getString(2), rs.getDouble(3),
                    rs.getString(4), rs.getDouble(5), rs.getDouble(6), rs.getInt(7)));
            }
        }
        return fields;
    }

    private static Map<String, RoomLabel> readRoomLabels(Connection c) throws SQLException {
        String sql = "SELECT space_type, label_text, language FROM ad_room_label " +
                     "WHERE profile_id='JKR_Malaysian' AND language='MS'";
        Map<String, RoomLabel> map = new HashMap<>();
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                map.put(rs.getString(1), new RoomLabel(rs.getString(1), rs.getString(2), rs.getString(3)));
            }
        }
        return map;
    }
}
