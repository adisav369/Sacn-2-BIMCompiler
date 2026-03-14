package com.bim.compiler.dsl;

import java.sql.*;
import java.util.*;

/**
 * Phase 108: Resolves interior room layout for residential unit zones.
 *
 * Reads ad_unit_type_room from component_library.db for room templates.
 * Scales fractional bounds [0..1] to zone dimensions, applies mirror transform
 * based on zone position relative to building envelope, and returns rooms with
 * absolute coordinates for the compilation pipeline.
 */
class UnitInteriorResolver {

    private static final String LIB_PATH = System.getProperty("bom.db");

    record UnitRoom(String name, String type,
                    double minX, double minY, double maxX, double maxY,
                    List<String> exteriorWalls, String opensTo, String unitName) {}

    enum Mirror { NONE, MIRROR_X, MIRROR_Y, MIRROR_XY }

    /** Room template from ad_unit_type_room. */
    record RoomTemplate(String roomKey, String roomType,
                        double fracMinX, double fracMinY,
                        double fracMaxX, double fracMaxY,
                        boolean needsWindow, String opensTo) {}

    // Cached templates per unit type
    private final Map<String, List<RoomTemplate>> templateCache = new HashMap<>();
    private boolean loaded = false;

    private void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + LIB_PATH)) {
            String sql = "SELECT unit_type_id, room_key, room_type, " +
                "frac_min_x, frac_min_y, frac_max_x, frac_max_y, " +
                "needs_window, opens_to FROM ad_unit_type_room WHERE is_active = 1";
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    String typeId = rs.getString("unit_type_id");
                    templateCache.computeIfAbsent(typeId, k -> new ArrayList<>())
                        .add(new RoomTemplate(
                            rs.getString("room_key"),
                            rs.getString("room_type"),
                            rs.getDouble("frac_min_x"),
                            rs.getDouble("frac_min_y"),
                            rs.getDouble("frac_max_x"),
                            rs.getDouble("frac_max_y"),
                            rs.getInt("needs_window") == 1,
                            rs.getString("opens_to")
                        ));
                }
            }
            System.out.printf("[UNIT-INTERIOR] Loaded %d unit types, %d room templates%n",
                templateCache.size(), templateCache.values().stream().mapToInt(List::size).sum());
        } catch (SQLException e) {
            System.err.println("[UNIT-INTERIOR] Failed to load: " + e.getMessage());
        }
    }

    /**
     * Resolve interior rooms for a unit zone.
     *
     * @param unitName  the unit instance name (e.g. "unit_W1")
     * @param unitType  the unit type ID from BOM (e.g. "2BR_A")
     * @param zoneMinX  zone bounds from FloorPlateBOMResolver
     * @param zoneMinY  zone bounds
     * @param zoneMaxX  zone bounds
     * @param zoneMaxY  zone bounds
     * @param bldgMinX  building envelope for mirror detection
     * @param bldgMinY  building envelope
     * @param bldgMaxX  building envelope
     * @param bldgMaxY  building envelope
     * @return list of interior rooms with absolute coordinates
     */
    List<UnitRoom> resolveInterior(String unitName, String unitType,
                                    double zoneMinX, double zoneMinY,
                                    double zoneMaxX, double zoneMaxY,
                                    double bldgMinX, double bldgMinY,
                                    double bldgMaxX, double bldgMaxY) {
        ensureLoaded();

        List<RoomTemplate> templates = templateCache.get(unitType);
        if (templates == null || templates.isEmpty()) {
            return List.of();
        }

        // Derive mirror from zone position relative to building envelope
        Mirror mirror = deriveMirror(zoneMinX, zoneMinY, zoneMaxX, zoneMaxY,
                                      bldgMinX, bldgMinY, bldgMaxX, bldgMaxY);

        double zoneW = zoneMaxX - zoneMinX;
        double zoneD = zoneMaxY - zoneMinY;

        List<UnitRoom> result = new ArrayList<>();
        for (RoomTemplate t : templates) {
            // Apply mirror to fractional coords
            double fMinX = t.fracMinX, fMinY = t.fracMinY;
            double fMaxX = t.fracMaxX, fMaxY = t.fracMaxY;

            if (mirror == Mirror.MIRROR_X || mirror == Mirror.MIRROR_XY) {
                double newMinX = 1.0 - fMaxX;
                double newMaxX = 1.0 - fMinX;
                fMinX = newMinX;
                fMaxX = newMaxX;
            }
            if (mirror == Mirror.MIRROR_Y || mirror == Mirror.MIRROR_XY) {
                double newMinY = 1.0 - fMaxY;
                double newMaxY = 1.0 - fMinY;
                fMinY = newMinY;
                fMaxY = newMaxY;
            }

            // Scale to absolute coordinates
            double absMinX = zoneMinX + fMinX * zoneW;
            double absMinY = zoneMinY + fMinY * zoneD;
            double absMaxX = zoneMinX + fMaxX * zoneW;
            double absMaxY = zoneMinY + fMaxY * zoneD;

            // Determine exterior walls (edges touching building perimeter)
            List<String> exteriorWalls = new ArrayList<>();
            double tol = 0.01;
            if (Math.abs(absMinY - bldgMinY) < tol) exteriorWalls.add("south");
            if (Math.abs(absMaxY - bldgMaxY) < tol) exteriorWalls.add("north");
            if (Math.abs(absMinX - bldgMinX) < tol) exteriorWalls.add("west");
            if (Math.abs(absMaxX - bldgMaxX) < tol) exteriorWalls.add("east");

            // Qualify room name with unit name for uniqueness
            String roomName = unitName + "_" + t.roomKey;

            // Qualify opensTo with unit prefix
            String opensTo = t.opensTo != null ? unitName + "_" + t.opensTo : null;

            result.add(new UnitRoom(roomName, t.roomType,
                absMinX, absMinY, absMaxX, absMaxY,
                exteriorWalls, opensTo, unitName));
        }

        System.out.printf("[UNIT-INTERIOR] %s (%s): %d rooms, mirror=%s%n",
            unitName, unitType, result.size(), mirror);
        return result;
    }

    /** Derive mirror transform from zone position relative to building envelope. */
    static Mirror deriveMirror(double zoneMinX, double zoneMinY,
                                double zoneMaxX, double zoneMaxY,
                                double bldgMinX, double bldgMinY,
                                double bldgMaxX, double bldgMaxY) {
        double tol = 0.01;
        boolean westExt = Math.abs(zoneMinX - bldgMinX) < tol;
        boolean eastExt = Math.abs(zoneMaxX - bldgMaxX) < tol;
        boolean southExt = Math.abs(zoneMinY - bldgMinY) < tol;
        boolean northExt = Math.abs(zoneMaxY - bldgMaxY) < tol;

        // Template assumes primary exterior corner at (0,0) = SW corner
        // West+South → NONE (template origin matches)
        // West+North → MIRROR_Y (flip Y so north becomes primary)
        // East+South → MIRROR_X (flip X so east becomes primary)
        // East+North → MIRROR_XY (flip both)
        if (westExt && southExt) return Mirror.NONE;
        if (westExt && northExt) return Mirror.MIRROR_Y;
        if (eastExt && southExt) return Mirror.MIRROR_X;
        if (eastExt && northExt) return Mirror.MIRROR_XY;

        // Fallback: partial exterior — pick based on X position
        if (westExt) return Mirror.NONE;
        if (eastExt) return Mirror.MIRROR_X;
        return Mirror.NONE;
    }
}
