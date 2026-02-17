package com.bim.compiler.library;

import com.bim.compiler.geometry.Point3D;
import com.bim.compiler.geometry.BoundingBox;
import com.bim.compiler.library.ComponentLibrary.*;
import com.bim.compiler.util.OutlierLogger;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 22: Places bathroom and kitchen fixtures from library.
 *
 * Placement rules from IBC 2021:
 * - Toilet: 15" (380mm) side clearance, 21" (533mm) front clearance
 * - Sink: 21" (533mm) front clearance
 * - Exhaust fan: centered on ceiling
 *
 * Room type → fixtures:
 * - BATHROOM: toilet + sink + exhaust fan
 * - KITCHEN: sink (counter placement TBD)
 */
public class FixturePlacer {

    private final ComponentLibrary library;

    // Fixture clearances - Watchdog-reviewed 2026-02-02
    // Phase 115B: Defaults kept as fallback; live values from ManifestResolver

    /** Toilet side clearance 381mm - ◆ RESEARCHED: IPC 405.3.1 (15 inches = 381mm). */
    private static final double DEFAULT_TOILET_SIDE_CLEARANCE = 0.381;

    /** Toilet front clearance 533mm - ◆ RESEARCHED: IPC 405.3.1 (21 inches). */
    private static final double DEFAULT_TOILET_FRONT_CLEARANCE = 0.533;

    /** Sink front clearance 533mm - ◆ RESEARCHED: IPC 405.3.1 (21 inches) */
    private static final double DEFAULT_SINK_FRONT_CLEARANCE = 0.533;

    /** Wall offset 50mm - ○ ASSUMED: clearance from wall face for fixture mounting */
    private static final double DEFAULT_WALL_OFFSET = 0.05;

    // Phase 115B: Accessor methods reading from ad_assembly_manifest via ManifestResolver
    private double getToiletSideClearance() {
        return ManifestResolver.getInstance().getClearance("TOILET_BLOCK_FIXTURES", "LEFT", DEFAULT_TOILET_SIDE_CLEARANCE);
    }
    private double getToiletFrontClearance() {
        return ManifestResolver.getInstance().getClearance("TOILET_BLOCK_FIXTURES", "FRONT", DEFAULT_TOILET_FRONT_CLEARANCE);
    }
    private double getSinkFrontClearance() {
        return ManifestResolver.getInstance().getClearance("TOILET_BLOCK_FIXTURES", "FRONT", DEFAULT_SINK_FRONT_CLEARANCE);
    }
    private double getWallOffset() {
        return ManifestResolver.getInstance().getClearance("TOILET_BLOCK_FIXTURES", "BACK", DEFAULT_WALL_OFFSET);
    }

    public FixturePlacer(ComponentLibrary library) {
        this.library = library;
    }

    // Phase 25: Minimum room dimensions for fixtures
    private static final double MIN_BATHROOM_WIDTH_FOR_TOILET = 0.8;   // 800mm
    private static final double MIN_BATHROOM_DEPTH_FOR_TOILET = 1.2;   // 1200mm (toilet + clearance)
    private static final double MIN_WIDTH_FOR_SINK = 0.6;              // 600mm

    /**
     * Place fixtures for a bathroom.
     *
     * Layout: Toilet against back wall, sink near door wall
     * Exhaust fan centered on ceiling.
     *
     * Phase 25: Checks room dimensions and logs when fixtures can't fit.
     *
     * @param roomMinX Room bounding box
     * @param roomMinY Room bounding box
     * @param roomMaxX Room bounding box
     * @param roomMaxY Room bounding box
     * @param floorZ Floor elevation
     * @param ceilingZ Ceiling elevation
     * @return List of fixture instances
     */
    public List<FixtureInstance> placeBathroomFixtures(
            double roomMinX, double roomMinY,
            double roomMaxX, double roomMaxY,
            double floorZ, double ceilingZ) throws SQLException {

        return placeBathroomFixtures(roomMinX, roomMinY, roomMaxX, roomMaxY,
                                     floorZ, ceilingZ, "bathroom");
    }

    /**
     * Place fixtures for a bathroom with room name for logging.
     */
    public List<FixtureInstance> placeBathroomFixtures(
            double roomMinX, double roomMinY,
            double roomMaxX, double roomMaxY,
            double floorZ, double ceilingZ,
            String roomName) throws SQLException {

        List<FixtureInstance> fixtures = new ArrayList<>();

        double roomWidth = roomMaxX - roomMinX;
        double roomDepth = roomMaxY - roomMinY;
        String roomContext = String.format("BATHROOM \"%s\" (%.1fm x %.1fm)", roomName, roomWidth, roomDepth);

        // Phase 25: Check minimum room dimensions
        if (roomWidth < MIN_BATHROOM_WIDTH_FOR_TOILET && roomDepth < MIN_BATHROOM_WIDTH_FOR_TOILET) {
            OutlierLogger.logGeometryImpossible(
                "all fixtures",
                roomContext,
                "room too small",
                String.format("Min dimension for any fixture = %.2fm. Room is %.2fm x %.2fm.",
                    MIN_BATHROOM_WIDTH_FOR_TOILET, roomWidth, roomDepth));
            return fixtures; // Empty list
        }

        // Get fixture definitions using fallback
        ComponentDefinition toiletDef = library.getComponentWithFallback("Toilet", roomContext);
        ComponentDefinition sinkDef = library.getComponentWithFallback("MRV basic round sink", roomContext);
        ComponentDefinition exhaustDef = library.getComponentWithFallback("Exhaust air grill", roomContext);

        // 1. Place toilet against back (north) wall, centered or left of center
        if (toiletDef != null) {
            double toiletWidth = toiletDef.localBounds().width();
            double toiletDepth = toiletDef.localBounds().depth();

            // Check if room is wide enough for toilet + clearances
            double requiredWidth = toiletWidth + 2 * getToiletSideClearance();
            double requiredDepth = toiletDepth + getToiletFrontClearance() + getWallOffset();

            if (roomWidth < requiredWidth) {
                OutlierLogger.logGeometryImpossible(
                    "toilet",
                    roomContext,
                    "room too narrow",
                    String.format("Width %.2fm < required %.2fm (%.2fm toilet + 2×%.2fm side clearance).",
                        roomWidth, requiredWidth, toiletWidth, getToiletSideClearance()));
            } else if (roomDepth < requiredDepth) {
                OutlierLogger.logGeometryImpossible(
                    "toilet",
                    roomContext,
                    "room too shallow",
                    String.format("Depth %.2fm < required %.2fm (%.2fm toilet + %.2fm front clearance + %.2fm wall offset).",
                        roomDepth, requiredDepth, toiletDepth, getToiletFrontClearance(), getWallOffset()));
            } else {
                // Position: against north wall, left side of room
                double toiletX = roomMinX + getToiletSideClearance() + toiletWidth / 2;
                double toiletY = roomMaxY - getWallOffset() - toiletDepth / 2;

                // Ensure fits in room
                if (toiletX + toiletWidth / 2 < roomMaxX - getToiletSideClearance()) {
                    fixtures.add(new FixtureInstance(
                        toiletDef,
                        new Point3D(toiletX, toiletY, floorZ),
                        0,  // rotation
                        FixtureType.TOILET
                    ));
                }
            }
        }

        // 2. Place sink on east wall or opposite toilet
        if (sinkDef != null) {
            double sinkWidth = sinkDef.localBounds().width();
            double sinkDepth = sinkDef.localBounds().depth();

            // Check if room has space for sink
            double requiredWidth = sinkDepth + getSinkFrontClearance() + getWallOffset();

            if (roomWidth < requiredWidth) {
                OutlierLogger.logGeometryImpossible(
                    "sink",
                    roomContext,
                    "insufficient clearance",
                    String.format("Min width for sink = %.2fm (%.2fm depth + %.2fm clearance). Room width = %.2fm.",
                        requiredWidth, sinkDepth, getSinkFrontClearance(), roomWidth));
            } else {
                // Position: on east wall, centered vertically
                double sinkX = roomMaxX - getWallOffset() - sinkDepth / 2;
                double sinkY = roomMinY + roomDepth / 2;
                double sinkZ = floorZ + 0.85; // Standard counter height 850mm

                // Ensure fits
                if (sinkX - sinkDepth / 2 > roomMinX + getSinkFrontClearance()) {
                    fixtures.add(new FixtureInstance(
                        sinkDef,
                        new Point3D(sinkX, sinkY, sinkZ),
                        Math.PI / 2,  // Rotated 90° to face into room
                        FixtureType.SINK
                    ));
                }
            }
        }

        // 3. Place exhaust fan centered on ceiling
        if (exhaustDef != null) {
            double exhaustWidth = exhaustDef.localBounds().width();
            double exhaustDepth = exhaustDef.localBounds().depth();

            // Check if exhaust fits in ceiling
            if (exhaustWidth > roomWidth || exhaustDepth > roomDepth) {
                OutlierLogger.logGeometryImpossible(
                    "exhaust_fan",
                    roomContext,
                    "ceiling too small",
                    String.format("Exhaust size %.2fm x %.2fm doesn't fit ceiling %.2fm x %.2fm.",
                        exhaustWidth, exhaustDepth, roomWidth, roomDepth));
            } else {
                double centerX = (roomMinX + roomMaxX) / 2;
                double centerY = (roomMinY + roomMaxY) / 2;

                fixtures.add(new FixtureInstance(
                    exhaustDef,
                    new Point3D(centerX, centerY, ceilingZ),
                    0,
                    FixtureType.EXHAUST_FAN
                ));
            }
        }

        // Resolve any clashes with logging
        return resolveClashesWithLogging(fixtures, roomContext);
    }

    /**
     * Place fixtures for a kitchen.
     *
     * Layout: Sink under window (if exterior) or against wall
     *
     * Phase 25: Uses component fallback and OutlierLogger.
     *
     * @param roomMinX Room bounding box
     * @param roomMinY Room bounding box
     * @param roomMaxX Room bounding box
     * @param roomMaxY Room bounding box
     * @param floorZ Floor elevation
     * @param exteriorWall Which wall is exterior (for window placement)
     * @return List of fixture instances
     */
    public List<FixtureInstance> placeKitchenFixtures(
            double roomMinX, double roomMinY,
            double roomMaxX, double roomMaxY,
            double floorZ, String exteriorWall) throws SQLException {

        return placeKitchenFixtures(roomMinX, roomMinY, roomMaxX, roomMaxY,
                                    floorZ, exteriorWall, "kitchen");
    }

    /**
     * Place fixtures for a kitchen with room name for logging.
     */
    public List<FixtureInstance> placeKitchenFixtures(
            double roomMinX, double roomMinY,
            double roomMaxX, double roomMaxY,
            double floorZ, String exteriorWall,
            String roomName) throws SQLException {

        List<FixtureInstance> fixtures = new ArrayList<>();

        double roomWidth = roomMaxX - roomMinX;
        double roomDepth = roomMaxY - roomMinY;
        String roomContext = String.format("KITCHEN \"%s\" (%.1fm x %.1fm)", roomName, roomWidth, roomDepth);

        // Phase 25: Use component fallback
        ComponentDefinition sinkDef = library.getComponentWithFallback("single_end_bowl_sink", roomContext);

        if (sinkDef != null) {
            double sinkWidth = sinkDef.localBounds().width();
            double sinkDepth = sinkDef.localBounds().depth();

            // Check if sink fits
            if (sinkWidth > roomWidth || sinkDepth > roomDepth) {
                OutlierLogger.logGeometryImpossible(
                    "sink",
                    roomContext,
                    "sink too large for room",
                    String.format("Sink size %.2fm x %.2fm doesn't fit room %.2fm x %.2fm.",
                        sinkWidth, sinkDepth, roomWidth, roomDepth));
                return fixtures;
            }

            double sinkZ = floorZ + 0.9; // Counter height 900mm

            double sinkX, sinkY;
            double rotation = 0;

            // Place sink on exterior wall (under window) or default to north wall
            switch (exteriorWall != null ? exteriorWall.toLowerCase() : "north") {
                case "south" -> {
                    sinkX = (roomMinX + roomMaxX) / 2;
                    sinkY = roomMinY + getWallOffset() + sinkDepth / 2;
                    rotation = Math.PI; // Face south
                }
                case "east" -> {
                    sinkX = roomMaxX - getWallOffset() - sinkDepth / 2;
                    sinkY = (roomMinY + roomMaxY) / 2;
                    rotation = -Math.PI / 2; // Face east
                }
                case "west" -> {
                    sinkX = roomMinX + getWallOffset() + sinkDepth / 2;
                    sinkY = (roomMinY + roomMaxY) / 2;
                    rotation = Math.PI / 2; // Face west
                }
                default -> { // north
                    sinkX = (roomMinX + roomMaxX) / 2;
                    sinkY = roomMaxY - getWallOffset() - sinkDepth / 2;
                    rotation = 0; // Face north
                }
            }

            fixtures.add(new FixtureInstance(
                sinkDef,
                new Point3D(sinkX, sinkY, sinkZ),
                rotation,
                FixtureType.SINK
            ));
        }

        // Resolve any clashes (for future when more fixtures added)
        return resolveClashesWithLogging(fixtures, roomContext);
    }

    // =========================================================================
    // Phase 94A: Toilet block multi-fixture layout
    // =========================================================================

    /** Stall width: toilet + 2× side clearance ≈ 0.507 + 2×0.38 = 1.267, round to 1.3m */
    private static final double STALL_WIDTH = 1.3;

    /** Sink spacing along wall */
    private static final double SINK_SPACING = 0.8;

    /**
     * Place fixtures for a TOILET_BLOCK room (school/commercial multi-stall toilet).
     *
     * Layout:
     * - N toilets in stalls along the back wall (farthest from door)
     * - N sinks along the side wall nearest the door
     * - 1 exhaust fan centered on ceiling
     *
     * Quantity: min(wallCapacity, max(2, floor(area/4.0)))
     * Sink count = toilet count (1:1 per IPC 2021 Table 403.1)
     *
     * @param roomMinX Room bounding box
     * @param roomMinY Room bounding box
     * @param roomMaxX Room bounding box
     * @param roomMaxY Room bounding box
     * @param floorZ Floor elevation
     * @param ceilingZ Ceiling elevation (for exhaust fan)
     * @param roomName Room name for logging
     * @param doorWall Which wall has the door ("north","south","east","west") — fixtures placed away from door
     * @return List of fixture instances
     */
    public List<FixtureInstance> placeToiletBlockFixtures(
            double roomMinX, double roomMinY,
            double roomMaxX, double roomMaxY,
            double floorZ, double ceilingZ,
            String roomName, String doorWall) throws SQLException {

        List<FixtureInstance> fixtures = new ArrayList<>();

        double roomWidth = roomMaxX - roomMinX;   // X extent
        double roomDepth = roomMaxY - roomMinY;   // Y extent
        double area = roomWidth * roomDepth;
        String roomContext = String.format("TOILET_BLOCK \"%s\" (%.1fm x %.1fm)", roomName, roomWidth, roomDepth);

        // Get fixture definitions
        ComponentDefinition toiletDef = library.getComponentWithFallback("Toilet", roomContext);
        ComponentDefinition sinkDef = library.getComponentWithFallback("MRV basic round sink", roomContext);
        ComponentDefinition exhaustDef = library.getComponentWithFallback("Exhaust air grill", roomContext);

        if (toiletDef == null && sinkDef == null) {
            return fixtures; // Nothing to place
        }

        // Determine back wall (opposite of door) for toilets, side wall for sinks
        // Door wall default: "south" (typical school toilet opens to corridor on south)
        String dw = (doorWall != null) ? doorWall.toLowerCase() : "south";

        // Calculate toilet count: capped by wall capacity
        double toiletWallLength = isHorizontalWall(dw) ? roomWidth : roomDepth;
        int wallCapacity = (int) Math.floor(toiletWallLength / STALL_WIDTH);
        int desiredCount = Math.max(2, (int) Math.floor(area / 4.0));
        int toiletCount = Math.min(desiredCount, wallCapacity);
        if (toiletCount < 1) toiletCount = 1;

        // Place toilets along the BACK wall (opposite of door)
        if (toiletDef != null) {
            double toiletWidth = toiletDef.localBounds().width();   // ~0.507m
            double toiletDepth = toiletDef.localBounds().depth();   // ~0.800m

            for (int i = 0; i < toiletCount; i++) {
                double stallCenter = getStallCenter(i, toiletCount, toiletWallLength);
                double[] pos = positionAgainstWall(oppositeWall(dw),
                    roomMinX, roomMinY, roomMaxX, roomMaxY,
                    stallCenter, toiletDepth);
                double rotation = rotationFacingInto(oppositeWall(dw));

                fixtures.add(new FixtureInstance(
                    toiletDef,
                    new Point3D(pos[0], pos[1], floorZ),
                    rotation,
                    FixtureType.TOILET
                ));
            }
        }

        // Place sinks along a SIDE wall (perpendicular to toilet wall)
        // If door is on south/north → sinks on east wall. If door is east/west → sinks on north wall.
        if (sinkDef != null) {
            String sinkWall = getSinkWall(dw);
            double sinkWallLength = isHorizontalWall(sinkWall) ? roomWidth : roomDepth;
            int sinkCount = Math.min(toiletCount, (int) Math.floor(sinkWallLength / SINK_SPACING));
            if (sinkCount < 1) sinkCount = 1;

            double sinkDepth = sinkDef.localBounds().depth();

            for (int i = 0; i < sinkCount; i++) {
                double sinkCenter = getStallCenter(i, sinkCount, sinkWallLength);
                double[] pos = positionAgainstWall(sinkWall,
                    roomMinX, roomMinY, roomMaxX, roomMaxY,
                    sinkCenter, sinkDepth);
                double rotation = rotationFacingInto(sinkWall);

                fixtures.add(new FixtureInstance(
                    sinkDef,
                    new Point3D(pos[0], pos[1], floorZ + 0.85),  // Counter height
                    rotation,
                    FixtureType.SINK
                ));
            }
        }

        // Exhaust fan centered on ceiling
        if (exhaustDef != null) {
            double centerX = (roomMinX + roomMaxX) / 2;
            double centerY = (roomMinY + roomMaxY) / 2;
            fixtures.add(new FixtureInstance(
                exhaustDef,
                new Point3D(centerX, centerY, ceilingZ),
                0,
                FixtureType.EXHAUST_FAN
            ));
        }

        return resolveClashesWithLogging(fixtures, roomContext);
    }

    // =========================================================================
    // Phase 96B: BOM-driven toilet block fixture placement
    // =========================================================================

    /** Cached BOM fixture children — loaded once per library connection */
    private List<BOMFixtureChild> bomFixtureCache;

    /** BOM fixture child record — one per role in TOILET_BLOCK_FIXTURES */
    record BOMFixtureChild(int childId, String role, String namePattern,
                           Map<String, String> params) {}

    /**
     * BOM-driven overload: loads TOILET_BLOCK_FIXTURES recipe from component_library.db,
     * resolves wall placement from door wall + exterior walls, places full fixture set.
     * Falls back to hardcoded method if BOM not found.
     */
    public List<FixtureInstance> placeToiletBlockFixtures(
            double roomMinX, double roomMinY,
            double roomMaxX, double roomMaxY,
            double floorZ, double ceilingZ,
            String roomName, String doorWall,
            List<String> exteriorWalls) throws SQLException {

        List<BOMFixtureChild> bomChildren = loadToiletBOM();
        if (bomChildren == null || bomChildren.isEmpty()) {
            // Fallback to hardcoded logic (school, pre-BOM buildings)
            return placeToiletBlockFixtures(roomMinX, roomMinY, roomMaxX, roomMaxY,
                floorZ, ceilingZ, roomName, doorWall);
        }

        List<FixtureInstance> fixtures = new ArrayList<>();
        double roomWidth = roomMaxX - roomMinX;
        double roomDepth = roomMaxY - roomMinY;
        String roomContext = String.format("TOILET_BLOCK \"%s\" (%.1fm x %.1fm)", roomName, roomWidth, roomDepth);
        String dw = (doorWall != null) ? doorWall.toLowerCase() : "west";

        // Track toilet count for match_role:TOILET
        int toiletCount = 0;

        for (BOMFixtureChild child : bomChildren) {
            String placementWall = child.params.getOrDefault("placement_wall", "");
            String position = child.params.getOrDefault("position", "");
            String qtyRule = child.params.getOrDefault("qty_rule", "");
            double spacing = parseDouble(child.params.get("spacing"), 1.0);
            double wallOffset = parseDouble(child.params.get("wall_offset"), getWallOffset());
            double zOffset = parseDouble(child.params.get("z_offset"), 0);
            String zRule = child.params.getOrDefault("z_rule", "");

            // Resolve absolute wall from relative placement
            String absWall = resolveWall(placementWall, dw, exteriorWalls);
            double wallLength = isHorizontalWall(absWall) ? roomWidth : roomDepth;

            // Determine quantity
            int qty;
            if (position.equals("floor_center") || position.equals("ceiling_center")) {
                qty = (int) parseDouble(child.params.get("qty"), 1);
            } else if (qtyRule.startsWith("match_role:")) {
                qty = toiletCount; // uses previously computed toilet count
            } else {
                // per_wall_length
                qty = Math.max(1, (int) Math.floor(wallLength / spacing));
            }

            // Determine Z
            double z = floorZ + zOffset;
            if (zRule.equals("ceiling")) {
                z = ceilingZ;
            }

            // Get component definition — BOM pattern already has wildcards, don't double-wrap
            ComponentDefinition def = library.getByName(child.namePattern);
            if (def == null) {
                // Fallback: try stripped name
                def = library.getComponentWithFallback(
                    child.namePattern.replace("%", ""), roomContext);
            }
            if (def == null) continue;

            double fixtureDepth = def.localBounds().depth();

            // Determine fixture type from role
            FixtureType fType = switch (child.role) {
                case "TOILET" -> FixtureType.TOILET;
                case "HAND_BIDET" -> FixtureType.HAND_BIDET;
                case "SINK" -> FixtureType.SINK;
                case "FLOOR_TRAP" -> FixtureType.FLOOR_TRAP;
                case "EXHAUST_FAN" -> FixtureType.EXHAUST_FAN;
                default -> FixtureType.COUNTER;
            };

            if (position.equals("floor_center")) {
                double cx = (roomMinX + roomMaxX) / 2;
                double cy = (roomMinY + roomMaxY) / 2;
                fixtures.add(new FixtureInstance(def, new Point3D(cx, cy, z), 0, fType));
            } else if (position.equals("ceiling_center")) {
                double cx = (roomMinX + roomMaxX) / 2;
                double cy = (roomMinY + roomMaxY) / 2;
                fixtures.add(new FixtureInstance(def, new Point3D(cx, cy, z), 0, fType));
            } else {
                // Wall-based placement
                double rotation = rotationFacingInto(absWall);
                double lateralOffset = parseDouble(child.params.get("lateral_offset"), 0);
                for (int i = 0; i < qty; i++) {
                    double stallCenter = getStallCenter(i, qty, wallLength, spacing);
                    double[] pos = positionAgainstWall(absWall,
                        roomMinX, roomMinY, roomMaxX, roomMaxY,
                        stallCenter, fixtureDepth, wallOffset);
                    // Apply lateral offset (along the wall, perpendicular to depth)
                    if (lateralOffset != 0) {
                        if (absWall.equals("north") || absWall.equals("south")) {
                            pos[0] += lateralOffset;
                        } else {
                            pos[1] += lateralOffset;
                        }
                    }
                    fixtures.add(new FixtureInstance(def, new Point3D(pos[0], pos[1], z), rotation, fType));
                }
            }

            if (child.role.equals("TOILET")) {
                toiletCount = qty;
            }
        }

        return resolveClashesWithLogging(fixtures, roomContext);
    }

    /** Load BOM children for TOILET_BLOCK_FIXTURES — cached */
    private List<BOMFixtureChild> loadToiletBOM() {
        if (bomFixtureCache != null) return bomFixtureCache;

        bomFixtureCache = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(
                "jdbc:sqlite:library/component_library.db")) {
            // Load children
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT bom_child_id, role, child_name_pattern FROM ad_bom_child " +
                    "WHERE bom_id = 'TOILET_BLOCK_FIXTURES' AND is_active = 1 ORDER BY sequence")) {
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    int childId = rs.getInt(1);
                    String role = rs.getString(2);
                    String pattern = rs.getString(3);
                    bomFixtureCache.add(new BOMFixtureChild(childId, role,
                        pattern != null ? pattern : "", new HashMap<>()));
                }
            }
            // Load params for each child
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT param_key, param_value FROM ad_bom_child_param WHERE bom_child_id = ?")) {
                for (BOMFixtureChild child : bomFixtureCache) {
                    ps.setInt(1, child.childId);
                    ResultSet rs = ps.executeQuery();
                    while (rs.next()) {
                        child.params.put(rs.getString(1), rs.getString(2));
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[FixturePlacer] Failed to load TOILET_BLOCK_FIXTURES BOM: " + e.getMessage());
            bomFixtureCache = null;
            return null;
        }
        return bomFixtureCache;
    }

    /** Resolve relative wall to absolute direction.
     *  back = opposite of door, side_interior = perpendicular non-exterior wall */
    private String resolveWall(String placement, String doorWall,
                               List<String> exteriorWalls) {
        if (placement == null || placement.isEmpty()) return doorWall;
        return switch (placement) {
            case "back" -> oppositeWall(doorWall);
            case "door" -> doorWall;
            case "side_interior" -> {
                // Perpendicular to door wall, prefer interior (not in exteriorWalls)
                List<String> perpendicular = perpendicularWalls(doorWall);
                List<String> extLower = exteriorWalls != null
                    ? exteriorWalls.stream().map(String::toLowerCase).toList()
                    : List.of();
                for (String w : perpendicular) {
                    if (!extLower.contains(w)) yield w;
                }
                // All perpendicular walls are exterior; pick first anyway
                yield perpendicular.get(0);
            }
            default -> placement; // literal wall name
        };
    }

    private List<String> perpendicularWalls(String wall) {
        return switch (wall.toLowerCase()) {
            case "north", "south" -> List.of("east", "west");
            case "east", "west" -> List.of("south", "north");
            default -> List.of("east", "west");
        };
    }

    /** BOM variant: position against wall with custom offset */
    private double[] positionAgainstWall(String wall, double minX, double minY, double maxX, double maxY,
                                          double centerAlong, double fixtureDepth, double wallOffset) {
        return switch (wall.toLowerCase()) {
            case "north" -> new double[]{ minX + centerAlong, maxY - wallOffset - fixtureDepth / 2 };
            case "south" -> new double[]{ minX + centerAlong, minY + wallOffset + fixtureDepth / 2 };
            case "east"  -> new double[]{ maxX - wallOffset - fixtureDepth / 2, minY + centerAlong };
            case "west"  -> new double[]{ minX + wallOffset + fixtureDepth / 2, minY + centerAlong };
            default -> new double[]{ (minX + maxX) / 2, (minY + maxY) / 2 };
        };
    }

    /** BOM variant: center stall using actual spacing */
    private double getStallCenter(int i, int n, double wallLength, double spacing) {
        double totalWidth = n * spacing;
        double startOffset = (wallLength - totalWidth) / 2.0 + spacing / 2.0;
        return startOffset + i * spacing;
    }

    private static double parseDouble(String s, double fallback) {
        if (s == null || s.isEmpty()) return fallback;
        try { return Double.parseDouble(s); }
        catch (NumberFormatException e) { return fallback; }
    }

    /** Evenly space stalls: center of stall i out of n along wall of given length */
    private double getStallCenter(int i, int n, double wallLength) {
        double totalWidth = n * STALL_WIDTH;
        double startOffset = (wallLength - totalWidth) / 2.0 + STALL_WIDTH / 2.0;
        return startOffset + i * STALL_WIDTH;
    }

    /** Position a fixture against a given wall. Returns [x, y]. centerAlong = position along wall from min corner. */
    private double[] positionAgainstWall(String wall, double minX, double minY, double maxX, double maxY,
                                          double centerAlong, double fixtureDepth) {
        return switch (wall.toLowerCase()) {
            case "north" -> new double[]{ minX + centerAlong, maxY - getWallOffset() - fixtureDepth / 2 };
            case "south" -> new double[]{ minX + centerAlong, minY + getWallOffset() + fixtureDepth / 2 };
            case "east"  -> new double[]{ maxX - getWallOffset() - fixtureDepth / 2, minY + centerAlong };
            case "west"  -> new double[]{ minX + getWallOffset() + fixtureDepth / 2, minY + centerAlong };
            default -> new double[]{ (minX + maxX) / 2, (minY + maxY) / 2 };
        };
    }

    /** Rotation so fixture faces INTO the room from the given wall */
    private double rotationFacingInto(String wall) {
        return switch (wall.toLowerCase()) {
            case "north" -> Math.PI;        // Fixture on north wall, faces south
            case "south" -> 0;              // Fixture on south wall, faces north
            case "east"  -> Math.PI / 2;    // Fixture on east wall, faces west
            case "west"  -> -Math.PI / 2;   // Fixture on west wall, faces east
            default -> 0;
        };
    }

    private String oppositeWall(String wall) {
        return switch (wall.toLowerCase()) {
            case "north" -> "south";
            case "south" -> "north";
            case "east" -> "west";
            case "west" -> "east";
            default -> "north";
        };
    }

    /** Get a side wall perpendicular to the door wall — prefer east, then north */
    private String getSinkWall(String doorWall) {
        return switch (doorWall.toLowerCase()) {
            case "north", "south" -> "east";
            case "east", "west" -> "north";
            default -> "east";
        };
    }

    private boolean isHorizontalWall(String wall) {
        return wall.equalsIgnoreCase("north") || wall.equalsIgnoreCase("south");
    }

    // =========================================================================
    // Clash Detection & Resolution
    // =========================================================================

    /**
     * Check if two fixtures clash (bounding boxes overlap in 3D).
     * Includes clearance buffer for access.
     * Phase 96B: Added Z-axis check — wall-mounted fixtures at different heights don't clash.
     */
    private boolean clashes(FixtureInstance f1, FixtureInstance f2, double clearance) {
        // Get world-space bounding boxes
        double f1MinX = f1.worldPosition().x() - f1.localBounds().width() / 2 - clearance;
        double f1MaxX = f1.worldPosition().x() + f1.localBounds().width() / 2 + clearance;
        double f1MinY = f1.worldPosition().y() - f1.localBounds().depth() / 2 - clearance;
        double f1MaxY = f1.worldPosition().y() + f1.localBounds().depth() / 2 + clearance;
        double f1MinZ = f1.worldPosition().z();
        double f1MaxZ = f1.worldPosition().z() + f1.localBounds().height();

        double f2MinX = f2.worldPosition().x() - f2.localBounds().width() / 2;
        double f2MaxX = f2.worldPosition().x() + f2.localBounds().width() / 2;
        double f2MinY = f2.worldPosition().y() - f2.localBounds().depth() / 2;
        double f2MaxY = f2.worldPosition().y() + f2.localBounds().depth() / 2;
        double f2MinZ = f2.worldPosition().z();
        double f2MaxZ = f2.worldPosition().z() + f2.localBounds().height();

        // Check 3D overlap
        boolean overlapX = f1MinX < f2MaxX && f1MaxX > f2MinX;
        boolean overlapY = f1MinY < f2MaxY && f1MaxY > f2MinY;
        boolean overlapZ = f1MinZ < f2MaxZ && f1MaxZ > f2MinZ;

        return overlapX && overlapY && overlapZ;
    }

    /**
     * Filter fixtures to remove clashing items.
     * Priority: TOILET > SINK > EXHAUST_FAN (keep higher priority)
     */
    private List<FixtureInstance> resolveClashes(List<FixtureInstance> fixtures) {
        return resolveClashesWithLogging(fixtures, "unknown room");
    }

    /**
     * Phase 25: Filter fixtures with OutlierLogger reporting.
     */
    private List<FixtureInstance> resolveClashesWithLogging(List<FixtureInstance> fixtures, String roomContext) {
        List<FixtureInstance> resolved = new ArrayList<>();

        // Sort by priority (toilet first, then sink, then exhaust)
        fixtures.sort((a, b) -> {
            int priorityA = getPriority(a.type());
            int priorityB = getPriority(b.type());
            return Integer.compare(priorityA, priorityB);
        });

        for (FixtureInstance candidate : fixtures) {
            boolean hasClash = false;
            FixtureInstance clashingWith = null;

            for (FixtureInstance existing : resolved) {
                if (clashes(candidate, existing, 0.1)) { // 100mm clearance
                    hasClash = true;
                    clashingWith = existing;
                    break;
                }
            }

            if (hasClash) {
                OutlierLogger.logGeometryImpossible(
                    candidate.type().name().toLowerCase(),
                    roomContext,
                    "clashes with " + clashingWith.type().name().toLowerCase(),
                    "Increase room size or remove lower-priority fixture requirement");
            } else {
                resolved.add(candidate);
            }
        }

        return resolved;
    }

    private int getPriority(FixtureType type) {
        return switch (type) {
            case TOILET -> 1;       // Highest priority
            case HAND_BIDET -> 2;
            case SINK -> 3;
            case FLOOR_TRAP -> 4;
            case EXHAUST_FAN -> 5;
            case COUNTER -> 6;      // Lowest priority
        };
    }

    // =========================================================================
    // Output types
    // =========================================================================

    public enum FixtureType {
        TOILET,
        SINK,
        EXHAUST_FAN,
        COUNTER,
        HAND_BIDET,
        FLOOR_TRAP
    }

    public record FixtureInstance(
        ComponentDefinition definition,
        Point3D worldPosition,
        double rotation,      // Radians around Z axis
        FixtureType type
    ) {
        public String name() {
            return definition.name();
        }

        public String geometryHash() {
            return definition.geometryHash();
        }

        public BoundingBox localBounds() {
            return definition.localBounds();
        }
    }
}
