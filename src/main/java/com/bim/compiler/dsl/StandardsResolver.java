package com.bim.compiler.dsl;

import com.bim.compiler.dsl.BuildingCompiler.*;

import java.sql.*;
import java.util.*;

/**
 * Phase 100: Standards Resolution Engine.
 *
 * Reads AD metadata tables (ad_fp_trigger, ad_fire_compartment) and
 * component_definitions to auto-place fire detection elements (IfcAlarm).
 *
 * Placement rules:
 * - Smoke detector: every habitable room ≥4m², ceiling-center, at ceilingZ
 * - Alarm bell: corridors + lobbies only, wall-mounted at baseZ+2.1m
 * - Break glass: corridors + lobbies, wall-mounted at baseZ+1.4m (UBBL)
 * - Exempt: shaft, riser, void, tnb, pump, genset, tank, machine
 */
public class StandardsResolver {

    private static final String LIB_PATH = "library/component_library.db";

    /** Minimum room area for smoke detector placement (m²) */
    private static final double MIN_ROOM_AREA = 4.0;

    /** Exempt room name keywords — no fire detection needed */
    private static final Set<String> EXEMPT_KEYWORDS = Set.of(
        "shaft", "riser", "void", "tnb", "pump", "genset", "tank", "machine"
    );

    /** Room types that get alarm bells + break glass (circulation spaces) */
    private static final Set<String> CIRCULATION_KEYWORDS = Set.of(
        "corridor", "lobby", "lift_lobby", "elevator_lobby", "hallway", "passage"
    );

    // LOD400 geometry references (loaded from component_library.db)
    private String smokeDetectorHash;
    private String alarmBellHash;
    private String breakGlassHash;
    private String heatDetectorHash;

    // Dimensions from library (meters)
    private double smokeW, smokeD, smokeH;
    private double bellW, bellD, bellH;
    private double glassW, glassD, glassH;

    private boolean triggersLoaded = false;
    private boolean detectionRequired = false;
    private boolean alarmRequired = false;
    private String codeRef = "";

    public StandardsResolver() {
        loadGeometry();
    }

    /**
     * Evaluate trigger rules from ad_fp_trigger and ad_fire_compartment.
     * Called once per building to determine if fire detection is required.
     */
    public void evaluateTriggers(int storeyCount, double heightM,
                                  double floorAreaM2, String occupancy, String jurisdiction) {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + LIB_PATH)) {
            // Check ad_fp_trigger for SMOKE_DETECTION and FIRE_ALARM triggers
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT trigger_id, element_type, code_id, clause " +
                    "FROM ad_fp_trigger WHERE is_active = 1 AND " +
                    "(jurisdiction = ? OR jurisdiction = 'INTERNATIONAL') AND " +
                    "element_type IN ('SMOKE_DETECTION', 'FIRE_ALARM') AND " +
                    "((min_storeys IS NOT NULL AND ? >= min_storeys) OR " +
                    " (min_height_m IS NOT NULL AND ? >= min_height_m) OR " +
                    " (min_floor_area_m2 IS NOT NULL AND ? >= min_floor_area_m2) OR " +
                    " (occupancy_group IS NOT NULL AND occupancy_group = ?))")) {
                ps.setString(1, jurisdiction);
                ps.setInt(2, storeyCount);
                ps.setDouble(3, heightM);
                ps.setDouble(4, floorAreaM2);
                ps.setString(5, occupancy);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String elemType = rs.getString("element_type");
                        if ("SMOKE_DETECTION".equals(elemType)) {
                            detectionRequired = true;
                            codeRef = rs.getString("code_id") + " " + rs.getString("clause");
                        }
                        if ("FIRE_ALARM".equals(elemType)) {
                            alarmRequired = true;
                            if (codeRef.isEmpty()) {
                                codeRef = rs.getString("code_id") + " " + rs.getString("clause");
                            }
                        }
                    }
                }
            }

            // Check ad_fire_compartment for requires_detection flag
            if (!detectionRequired) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT compartment_id, code_id, clause " +
                        "FROM ad_fire_compartment WHERE is_active = 1 AND " +
                        "requires_detection = 1 AND " +
                        "(jurisdiction = ? OR jurisdiction = 'INTERNATIONAL') AND " +
                        "occupancy_group = ?")) {
                    ps.setString(1, jurisdiction);
                    ps.setString(2, occupancy);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            detectionRequired = true;
                            codeRef = rs.getString("code_id") + " " + rs.getString("clause");
                        }
                    }
                }
            }

            triggersLoaded = true;
        } catch (SQLException e) {
            System.out.println("[STANDARDS] Warning: " + e.getMessage());
            triggersLoaded = true;
        }
    }

    /** Returns true if smoke detection is required for this building. */
    public boolean isDetectionRequired() { return detectionRequired; }

    /** Returns true if fire alarm bells/break glass are required. */
    public boolean isAlarmRequired() { return alarmRequired; }

    /**
     * Resolve alarm specs for a single room.
     * @param room The room spec
     * @param ceilingZ Z-coordinate of ceiling (for smoke detectors)
     * @param baseZ Z-coordinate of floor (for wall-mounted devices)
     * @param storeyName Storey name for ID generation
     * @return List of AlarmSpec to place
     */
    public List<AlarmSpec> resolveForRoom(RoomSpec room, double ceilingZ, double baseZ,
                                           String storeyName) {
        if (!triggersLoaded || (!detectionRequired && !alarmRequired)) {
            return List.of();
        }

        String nameLower = room.name().toLowerCase();

        // Check exemption
        for (String keyword : EXEMPT_KEYWORDS) {
            if (nameLower.contains(keyword)) {
                return List.of();
            }
        }

        double roomW = room.maxX() - room.minX();
        double roomD = room.maxY() - room.minY();
        double roomArea = roomW * roomD;
        double centerX = (room.minX() + room.maxX()) / 2.0;
        double centerY = (room.minY() + room.maxY()) / 2.0;

        List<AlarmSpec> alarms = new ArrayList<>();
        int idx = 0;

        // Smoke detector: every habitable room ≥4m², ceiling-center
        if (detectionRequired && roomArea >= MIN_ROOM_AREA) {
            String id = "SD_" + room.name() + "_" + storeyName + "_" + (idx++);
            alarms.add(new AlarmSpec(
                id, room.name(), "smoke_detector",
                centerX, centerY, ceilingZ,
                smokeDetectorHash,
                smokeW, smokeD, smokeH,
                codeRef
            ));
        }

        // Alarm bell + break glass: circulation spaces only
        boolean isCirculation = false;
        for (String keyword : CIRCULATION_KEYWORDS) {
            if (nameLower.contains(keyword)) {
                isCirculation = true;
                break;
            }
        }

        if (alarmRequired && isCirculation && roomArea >= MIN_ROOM_AREA) {
            // Alarm bell: wall-mounted at 2.1m above floor, near room center on south wall
            double bellZ = baseZ + 2.1;
            double bellX = centerX;
            double bellY = room.minY() + 0.05; // Near south wall
            String bellId = "AB_" + room.name() + "_" + storeyName + "_" + (idx++);
            alarms.add(new AlarmSpec(
                bellId, room.name(), "alarm_bell",
                bellX, bellY, bellZ,
                alarmBellHash,
                bellW, bellD, bellH,
                codeRef
            ));

            // Break glass: wall-mounted at 1.4m above floor (UBBL), opposite end
            double glassZ = baseZ + 1.4;
            double glassX = centerX;
            double glassY = room.maxY() - 0.05; // Near north wall
            String glassId = "BG_" + room.name() + "_" + storeyName + "_" + (idx++);
            alarms.add(new AlarmSpec(
                glassId, room.name(), "break_glass",
                glassX, glassY, glassZ,
                breakGlassHash,
                glassW, glassD, glassH,
                codeRef
            ));
        }

        return alarms;
    }

    private void loadGeometry() {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + LIB_PATH)) {
            // Load one representative of each type (use specific known IDs)
            smokeDetectorHash = loadComponent(conn, 16970);
            alarmBellHash = loadComponent(conn, 16960);
            breakGlassHash = loadComponent(conn, 16963);
            heatDetectorHash = loadComponent(conn, 16980);

            // Load dimensions from local bounds
            double[] smokeBounds = loadBounds(conn, 16970);
            if (smokeBounds != null) {
                smokeW = smokeBounds[1] - smokeBounds[0];
                smokeD = smokeBounds[3] - smokeBounds[2];
                smokeH = smokeBounds[5] - smokeBounds[4];
            } else {
                smokeW = 0.170; smokeD = 0.170; smokeH = 0.075;
            }

            double[] bellBounds = loadBounds(conn, 16960);
            if (bellBounds != null) {
                bellW = bellBounds[1] - bellBounds[0];
                bellD = bellBounds[3] - bellBounds[2];
                bellH = bellBounds[5] - bellBounds[4];
            } else {
                bellW = 0.155; bellD = 0.062; bellH = 0.155;
            }

            double[] glassBounds = loadBounds(conn, 16963);
            if (glassBounds != null) {
                glassW = glassBounds[1] - glassBounds[0];
                glassD = glassBounds[3] - glassBounds[2];
                glassH = glassBounds[5] - glassBounds[4];
            } else {
                glassW = 0.086; glassD = 0.028; glassH = 0.086;
            }
        } catch (SQLException e) {
            System.out.println("[STANDARDS] Warning loading geometry: " + e.getMessage());
            // Use hardcoded fallback dimensions
            smokeW = 0.170; smokeD = 0.170; smokeH = 0.075;
            bellW = 0.155; bellD = 0.062; bellH = 0.155;
            glassW = 0.086; glassD = 0.028; glassH = 0.086;
        }
    }

    private String loadComponent(Connection conn, int id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT geometry_hash FROM component_definitions WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("geometry_hash");
            }
        }
        return null;
    }

    private double[] loadBounds(Connection conn, int id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT local_min_x, local_max_x, local_min_y, local_max_y, " +
                "local_min_z, local_max_z FROM component_definitions WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new double[]{
                        rs.getDouble(1), rs.getDouble(2),
                        rs.getDouble(3), rs.getDouble(4),
                        rs.getDouble(5), rs.getDouble(6)
                    };
                }
            }
        }
        return null;
    }
}
