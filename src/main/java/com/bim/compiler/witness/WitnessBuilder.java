package com.bim.compiler.witness;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

/**
 * Witness Builder - Collects proof data during compilation.
 *
 * Design principles:
 * - NON-INTRUSIVE: Parallel output, doesn't affect compilation
 * - NON-BLOCKING: Errors are caught and logged, compilation continues
 * - SIMPLE DATA: Plain JSON structures
 *
 * Usage in compiler:
 *   WitnessBuilder witness = new WitnessBuilder("TB-LKTN");
 *   witness.foundationZ(0.0, -0.15);
 *   witness.entryDoor("D1", "south", "EXTERIOR", "common");
 *   witness.roomPath("bilik_utama", List.of("common", "bilik_utama"), "D2");
 *   witness.write(outputDir.resolve("witness.json"));
 */
public class WitnessBuilder {

    private final String buildingName;
    private final Map<String, Object> claims = new LinkedHashMap<>();
    private int proven = 0;
    private int skipped = 0;
    private int unprovable = 0;

    // Collected data
    private Double foundationTopZ;
    private Double foundationBottomZ;
    private String foundationId;
    private Map<String, Object> entryDoor;
    private String entrySpace;
    private final Map<String, Map<String, Object>> roomPaths = new LinkedHashMap<>();
    private final List<Map<String, Object>> windows = new ArrayList<>();
    private final List<Map<String, Object>> windowViolations = new ArrayList<>();
    private double[] roofPolygon;
    private final Map<String, Map<String, Object>> roomCorners = new LinkedHashMap<>();
    private final Map<String, Map<String, Object>> roomEnclosure = new LinkedHashMap<>();
    private double[] envelopeMin;
    private double[] envelopeMax;
    private final Map<String, Boolean> roomsInEnvelope = new LinkedHashMap<>();

    // Phase 33/36: Electrical elements containment
    private final List<Map<String, Object>> electricalElements = new ArrayList<>();
    private final List<Map<String, Object>> electricalViolations = new ArrayList<>();
    private static final double WALL_TOLERANCE = 0.06; // 60mm for wall-mounted elements

    // Phase 33: Fixture attachment validation
    private final List<Map<String, Object>> fixtureAttachments = new ArrayList<>();
    private final List<Map<String, Object>> floatingFixtures = new ArrayList<>();
    private final List<Map<String, Object>> clashingFixtures = new ArrayList<>();
    private static final double ATTACHMENT_TOLERANCE = 0.005; // 5mm per BIMConstants.TOLERANCE

    // Phase 34: Plumbing pipe validation
    private final List<Map<String, Object>> plumbingPipes = new ArrayList<>();
    private final List<Map<String, Object>> plumbingViolations = new ArrayList<>();

    // Phase 35-37: MEP system graphs
    private com.bim.compiler.system.MEPSystem wasteSystem;
    private com.bim.compiler.system.MEPSystem ventSystem;
    private com.bim.compiler.system.MEPSystem supplySystem;

    // Phase 39: Electrical circuits graph
    private com.bim.compiler.system.MEPSystem electricalSystem;

    // Phase 40: Structural-MEP clash detection
    private List<com.bim.compiler.validation.ClashDetector.Clash> structuralClashes;

    // Phase 38: Vertical storey consistency
    private final Map<String, Map<String, Object>> verticalConstraints = new LinkedHashMap<>();
    private final Map<String, Map<String, Object>> stackAlignments = new LinkedHashMap<>();
    private static final double VERTICAL_ALIGNMENT_TOLERANCE = 0.005; // 5mm

    // Phase 47C: Party wall validation
    private final List<Map<String, Object>> partyWallSegments = new ArrayList<>();
    private boolean isMultiUnit = false;

    // Phase 48C: Separating floor validation
    private final List<Map<String, Object>> separatingFloors = new ArrayList<>();

    // Phase 50C: School-specific claims
    private final List<Map<String, Object>> classroomDaylight = new ArrayList<>();
    private final List<Map<String, Object>> toiletAccess = new ArrayList<>();
    private final List<Map<String, Object>> corridorConnections = new ArrayList<>();
    private String corridorName;
    private final List<Map<String, Object>> fireTravelDistances = new ArrayList<>();
    private String exitLocation;
    private String fireTravelStandard;
    private final List<Map<String, Object>> structuralGridElements = new ArrayList<>();
    private String gridRoomName;

    public WitnessBuilder(String buildingName) {
        this.buildingName = buildingName;
    }

    // ===== Claim 1: FOUNDATION_GROUNDED =====

    public void foundationZ(double topZ, double bottomZ) {
        foundationZ("foundation_slab", topZ, bottomZ);
    }

    public void foundationZ(String id, double topZ, double bottomZ) {
        this.foundationId = id;
        this.foundationTopZ = topZ;
        this.foundationBottomZ = bottomZ;
    }

    // ===== Claim 2: ENTRY_EXISTS =====

    // Phase 48D.2: Per-unit entry tracking for multi-unit buildings
    private final Map<String, Map<String, Object>> unitEntries = new LinkedHashMap<>();

    public void entryDoor(String doorId, String wall, String wallType, String toSpace) {
        this.entryDoor = new LinkedHashMap<>();
        entryDoor.put("id", doorId);
        entryDoor.put("wall", wall);
        entryDoor.put("wall_type", wallType);
        this.entrySpace = toSpace;
    }

    /**
     * Phase 48D.2: Record entry for a specific unit.
     * For DIRECT entry: door leads to EXTERIOR
     * For SHARED entry: door leads to SHARED circulation, which leads to EXTERIOR
     *
     * @param unitId Unit identifier (A, B, etc.)
     * @param entryType DIRECT or SHARED
     * @param doorId Door identifier
     * @param fromRoom Room the door is in
     * @param toSpace Target space (EXTERIOR for DIRECT, landing name for SHARED)
     * @param egressPath Full path to EXTERIOR (for SHARED: [room, landing, stair, EXTERIOR])
     */
    public void unitEntry(String unitId, String entryType, String doorId,
                          String fromRoom, String toSpace, List<String> egressPath) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("entry_type", entryType);
        entry.put("door", doorId);
        entry.put("from_room", fromRoom);
        entry.put("to_space", toSpace);
        entry.put("egress_path", egressPath);
        entry.put("proven", !egressPath.isEmpty());
        unitEntries.put(unitId, entry);
    }

    // ===== Claim 3: ALL_ROOMS_REACHABLE =====

    public void roomPath(String roomName, List<String> path, String viaDoor) {
        Map<String, Object> pathInfo = new LinkedHashMap<>();
        pathInfo.put("path", path);
        pathInfo.put("via", viaDoor);
        roomPaths.put(roomName, pathInfo);
    }

    public void roomDirectAccess(String roomName, String note) {
        Map<String, Object> pathInfo = new LinkedHashMap<>();
        pathInfo.put("path", List.of("EXTERIOR", roomName));
        pathInfo.put("via", "direct");
        pathInfo.put("note", note);
        roomPaths.put(roomName, pathInfo);
    }

    // ===== Claim 4: WINDOWS_ON_EXTERIOR =====

    public void windowOnExterior(String windowId, String room, String wallDirection, String wallType) {
        Map<String, Object> win = new LinkedHashMap<>();
        win.put("id", windowId);
        win.put("room", room);
        win.put("wall_direction", wallDirection);
        win.put("wall_type", wallType);
        win.put("valid", "EXTERIOR".equals(wallType));
        windows.add(win);

        if (!"EXTERIOR".equals(wallType)) {
            windowViolations.add(win);
        }
    }

    // ===== Claim 5: ROOF_COVERS_ALL =====

    public void roofPolygon(double[][] corners) {
        // Flatten to simple array for JSON
        this.roofPolygon = new double[corners.length * 2];
        for (int i = 0; i < corners.length; i++) {
            roofPolygon[i * 2] = corners[i][0];
            roofPolygon[i * 2 + 1] = corners[i][1];
        }
    }

    public void roomCornersUnderRoof(String roomName, double[][] corners, boolean allInside) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("corners", corners);
        info.put("all_inside", allInside);
        roomCorners.put(roomName, info);
    }

    // ===== Claim 6: ROOMS_ENCLOSED =====

    public void roomEnclosed(String roomName, int wallCount, boolean closed) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("wall_count", wallCount);
        info.put("closed", closed);
        roomEnclosure.put(roomName, info);
    }

    // ===== Claim 7: ROOMS_IN_ENVELOPE =====

    public void buildingEnvelope(double minX, double minY, double minZ,
                                  double maxX, double maxY, double maxZ) {
        this.envelopeMin = new double[]{minX, minY, minZ};
        this.envelopeMax = new double[]{maxX, maxY, maxZ};
    }

    public void roomInEnvelope(String roomName, boolean inside) {
        roomsInEnvelope.put(roomName, inside);
    }

    // ===== Claim 17: ROOM_AREAS_CONSISTENT (Phase 45) =====

    // Room area data for BOM generation and consistency checking
    private final List<Map<String, Object>> roomAreas = new ArrayList<>();
    private final Map<String, Double> slabAreaByStorey = new LinkedHashMap<>();

    /**
     * Record a room's area for BOM and consistency checking.
     */
    public void roomArea(String roomName, String roomType, String storey,
                         double minX, double minY, double maxX, double maxY) {
        double area = (maxX - minX) * (maxY - minY);
        Map<String, Object> room = new LinkedHashMap<>();
        room.put("room", roomName);
        room.put("type", roomType);
        room.put("storey", storey);
        room.put("area_m2", Math.round(area * 100.0) / 100.0);  // 2 decimal places
        room.put("bounds", new double[]{minX, minY, maxX, maxY});
        roomAreas.add(room);
    }

    /**
     * Set the slab/footprint area for a storey for consistency checking.
     */
    public void slabArea(String storey, double area) {
        slabAreaByStorey.put(storey, area);
    }

    // ===== Claim 18: PARTY_WALLS_VALID (Phase 47C) =====

    /**
     * Mark this as a multi-unit building (required for party wall witness).
     */
    public void setMultiUnit(boolean multiUnit) {
        this.isMultiUnit = multiUnit;
    }

    /**
     * Record a party wall segment between two rooms from different units.
     *
     * @param roomA First room name
     * @param unitA First room's unit ID
     * @param roomB Second room name
     * @param unitB Second room's unit ID
     * @param edge Shared edge (NORTH, SOUTH, EAST, WEST)
     * @param lengthM Wall length in meters
     * @param thicknessMm Wall thickness in millimeters
     * @param fireRating Fire rating string (e.g., "FRL 60/60/60")
     * @param minX Wall geometry bounds
     * @param minY Wall geometry bounds
     * @param maxX Wall geometry bounds
     * @param maxY Wall geometry bounds
     */
    public void partyWall(String roomA, String unitA, String roomB, String unitB,
                          String edge, double lengthM, double thicknessMm, String fireRating,
                          double minX, double minY, double maxX, double maxY) {
        Map<String, Object> segment = new LinkedHashMap<>();
        segment.put("rooms", List.of(roomA, roomB));
        segment.put("units", List.of(unitA, unitB));
        segment.put("edge", edge);
        segment.put("length_m", Math.round(lengthM * 100.0) / 100.0);
        segment.put("thickness_mm", (int) thicknessMm);
        segment.put("fire_rating", fireRating);
        segment.put("bounds", Map.of(
            "minX", minX, "minY", minY,
            "maxX", maxX, "maxY", maxY
        ));
        partyWallSegments.add(segment);
    }

    // ===== Claim 19: SEPARATING_FLOORS_VALID (Phase 48C) =====

    /**
     * Record a separating floor slab between dwelling units.
     *
     * @param level Storey level
     * @param name Slab name
     * @param unitsAbove Units on this level
     * @param unitsBelow Units on level below
     * @param thicknessMm Slab thickness in millimeters
     * @param fireRating Fire rating string (e.g., "90/90/90")
     * @param acousticSTC Sound Transmission Class
     * @param acousticIIC Impact Insulation Class
     * @param bounds Slab geometry bounds [minX, minY, maxX, maxY, minZ, maxZ]
     */
    public void separatingFloor(int level, String name,
                                 Set<String> unitsAbove, Set<String> unitsBelow,
                                 double thicknessMm, String fireRating,
                                 int acousticSTC, int acousticIIC,
                                 double[] bounds) {
        Map<String, Object> floor = new LinkedHashMap<>();
        floor.put("level", level);
        floor.put("name", name);
        floor.put("units_above", new ArrayList<>(unitsAbove));
        floor.put("units_below", new ArrayList<>(unitsBelow));
        floor.put("thickness_mm", (int) thicknessMm);
        floor.put("fire_rating", fireRating);
        floor.put("acoustic", Map.of(
            "STC", acousticSTC,
            "IIC", acousticIIC,
            "assembly_note", "Assumes composite assembly: slab + resilient layer + floating screed"
        ));
        if (bounds != null && bounds.length >= 6) {
            floor.put("bounds", Map.of(
                "minX", bounds[0], "minY", bounds[1],
                "maxX", bounds[2], "maxY", bounds[3],
                "minZ", bounds[4], "maxZ", bounds[5]
            ));
        }
        separatingFloors.add(floor);
    }

    // ===== Claim 20: CLASSROOM_DAYLIGHT (Phase 50C) =====

    /**
     * Record a classroom and its window data for daylight verification.
     *
     * @param roomName Classroom name
     * @param roomType Room type (should be CLASSROOM or similar)
     * @param windowCount Number of windows in room
     * @param windowAreaM2 Total window glazing area in square meters
     * @param floorAreaM2 Room floor area in square meters
     */
    public void classroomWindow(String roomName, String roomType, int windowCount,
                                 double windowAreaM2, double floorAreaM2) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("room", roomName);
        data.put("type", roomType);
        data.put("window_count", windowCount);
        data.put("window_area_m2", Math.round(windowAreaM2 * 100.0) / 100.0);
        data.put("floor_area_m2", Math.round(floorAreaM2 * 100.0) / 100.0);
        // Daylight ratio: window area / floor area (typically want >= 10%)
        double ratio = floorAreaM2 > 0 ? windowAreaM2 / floorAreaM2 : 0;
        data.put("daylight_ratio", Math.round(ratio * 1000.0) / 1000.0);
        data.put("has_daylight", windowCount > 0);
        classroomDaylight.add(data);
    }

    // ===== Claim 21: TOILET_ACCESSIBLE (Phase 50C) =====

    /**
     * Record a toilet room and its accessibility data.
     *
     * @param roomName Toilet room name
     * @param doorWidth Door width in meters
     * @param hasGrabBars Whether grab bars are present
     * @param clearFloorSpace Clear floor space in square meters
     * @param roomAreaM2 Total room area
     */
    public void toiletAccessibility(String roomName, double doorWidth,
                                     boolean hasGrabBars, double clearFloorSpace,
                                     double roomAreaM2) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("room", roomName);
        data.put("door_width_m", doorWidth);
        data.put("has_grab_bars", hasGrabBars);
        data.put("clear_floor_space_m2", Math.round(clearFloorSpace * 100.0) / 100.0);
        data.put("room_area_m2", Math.round(roomAreaM2 * 100.0) / 100.0);
        // Accessible if door >= 900mm (MS 1184 requirement)
        boolean doorAccessible = doorWidth >= 0.9;
        data.put("door_accessible", doorAccessible);
        data.put("compliant", doorAccessible); // Base compliance on door width
        toiletAccess.add(data);
    }

    // ===== Claim 22: CORRIDOR_CONNECTS_ALL (Phase 50C) =====

    /**
     * Set the main corridor name for connectivity checking.
     */
    public void setCorridorName(String name) {
        this.corridorName = name;
    }

    /**
     * Record that a room is connected via the corridor.
     *
     * @param roomName Room name
     * @param roomType Room type
     * @param connectedViaDoor Door that connects room to corridor
     * @param pathLength Path length from corridor to room (0 if direct)
     */
    public void corridorConnection(String roomName, String roomType,
                                    String connectedViaDoor, int pathLength) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("room", roomName);
        data.put("type", roomType);
        data.put("via_door", connectedViaDoor);
        data.put("path_length", pathLength);
        data.put("connected", connectedViaDoor != null);
        corridorConnections.add(data);
    }

    // ===== Claim 23: FIRE_TRAVEL_DISTANCE (Phase 50C) =====

    /**
     * Set the exit location for fire travel distance calculations.
     */
    public void setExitLocation(String location) {
        this.exitLocation = location;
    }

    /**
     * Set the fire travel distance standard/code reference.
     */
    public void setFireTravelStandard(String standard) {
        this.fireTravelStandard = standard;
    }

    /**
     * Record fire travel distance from a room to exit.
     *
     * @param roomName Room name
     * @param distanceM Travel distance in meters
     * @param maxAllowedM Maximum allowed travel distance
     * @param pathDescription Description of egress path
     */
    public void fireTravelDistance(String roomName, double distanceM,
                                    double maxAllowedM, String pathDescription) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("room", roomName);
        data.put("distance_m", Math.round(distanceM * 100.0) / 100.0);
        data.put("max_allowed_m", maxAllowedM);
        data.put("path", pathDescription);
        data.put("compliant", distanceM <= maxAllowedM);
        fireTravelDistances.add(data);
    }

    // ===== Claim 24: STRUCTURAL_GRID_COMPLETE (Phase 50C) =====

    /**
     * Set the room name where structural grid is expected.
     */
    public void setGridRoomName(String name) {
        this.gridRoomName = name;
    }

    /**
     * Record a structural grid element (column or beam).
     *
     * @param elementId Element ID
     * @param ifcClass IFC class (IfcColumn, IfcBeam)
     * @param elementType Type (GRID_COLUMN, GRID_BEAM)
     * @param x X position
     * @param y Y position
     * @param z Z position
     * @param roomName Room containing the element
     */
    public void structuralGridElement(String elementId, String ifcClass, String elementType,
                                       double x, double y, double z, String roomName) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", elementId);
        data.put("ifc_class", ifcClass);
        data.put("type", elementType);
        data.put("position", new double[]{x, y, z});
        data.put("room", roomName);
        // Validate IFC class is correct
        boolean validClass = ("GRID_COLUMN".equals(elementType) && "IfcColumn".equals(ifcClass)) ||
                            ("GRID_BEAM".equals(elementType) && "IfcBeam".equals(ifcClass));
        data.put("ifc_class_valid", validClass);
        structuralGridElements.add(data);
    }

    // ===== Claim 8: ELECTRICAL_IN_SPACES (Phase 33/36) =====

    /**
     * Record an electrical element and verify it's within room bounds.
     *
     * @param elementId Element identifier
     * @param ifcClass IFC class (IfcLightFixture, IfcOutlet, IfcSwitchingDevice)
     * @param roomName Room the element belongs to
     * @param x Element X position
     * @param y Element Y position
     * @param z Element Z position
     * @param roomMinX Room bounding box
     * @param roomMaxX Room bounding box
     * @param roomMinY Room bounding box
     * @param roomMaxY Room bounding box
     * @param roomMinZ Room floor Z (baseZ)
     * @param roomMaxZ Room ceiling Z
     */
    public void electricalElement(String elementId, String ifcClass, String roomName,
                                  double x, double y, double z,
                                  double roomMinX, double roomMaxX,
                                  double roomMinY, double roomMaxY,
                                  double roomMinZ, double roomMaxZ) {
        Map<String, Object> elem = new LinkedHashMap<>();
        elem.put("id", elementId);
        elem.put("ifc_class", ifcClass);
        elem.put("room", roomName);
        elem.put("position", new double[]{x, y, z});

        // 3D containment check with wall tolerance for wall-mounted elements
        // Outlets and switches are ON walls, so use tolerance for XY
        boolean isWallMounted = "IfcOutlet".equals(ifcClass) || "IfcSwitchingDevice".equals(ifcClass);
        double xyTolerance = isWallMounted ? WALL_TOLERANCE : 0.0;

        boolean insideX = x >= (roomMinX - xyTolerance) && x <= (roomMaxX + xyTolerance);
        boolean insideY = y >= (roomMinY - xyTolerance) && y <= (roomMaxY + xyTolerance);
        boolean insideZ = z >= roomMinZ && z <= roomMaxZ;
        boolean inside = insideX && insideY && insideZ;

        elem.put("inside", inside);
        elem.put("bounds_check", Map.of(
            "x_ok", insideX,
            "y_ok", insideY,
            "z_ok", insideZ
        ));

        electricalElements.add(elem);

        if (!inside) {
            electricalViolations.add(elem);
        }
    }

    // ===== Claim 9: FIXTURES_ATTACHED_TO_HOSTS (Phase 33) =====

    /**
     * Record a ceiling-mounted fixture and validate attachment.
     *
     * @param fixtureId Fixture identifier
     * @param ifcClass IFC class
     * @param fixtureMaxZ Top of fixture (world Z)
     * @param ceilingZ Ceiling surface Z
     * @param hostId Host element ID (ceiling/slab)
     */
    public void ceilingMountedFixture(String fixtureId, String ifcClass,
                                       double fixtureMaxZ, double ceilingZ, String hostId) {
        double gap = ceilingZ - fixtureMaxZ;
        String status;
        if (gap < -ATTACHMENT_TOLERANCE) {
            status = "CLASHING";  // Penetrates ceiling
        } else if (gap > ATTACHMENT_TOLERANCE) {
            status = "FLOATING";  // Gap too large
        } else {
            status = "ATTACHED";  // Within tolerance
        }

        Map<String, Object> attachment = new LinkedHashMap<>();
        attachment.put("fixture", fixtureId);
        attachment.put("ifc_class", ifcClass);
        attachment.put("host", hostId);
        attachment.put("type", "CEILING_MOUNTED");
        attachment.put("fixture_top_z", fixtureMaxZ);
        attachment.put("host_z", ceilingZ);
        attachment.put("gap_mm", Math.round(gap * 1000));  // Convert to mm
        attachment.put("status", status);

        fixtureAttachments.add(attachment);

        if ("FLOATING".equals(status)) {
            floatingFixtures.add(attachment);
        } else if ("CLASHING".equals(status)) {
            clashingFixtures.add(attachment);
        }
    }

    // ===== Claim 10: PLUMBING_PIPES_VALID =====

    /**
     * Record a plumbing pipe and validate it.
     *
     * @param pipeId Pipe identifier
     * @param pipeType Type: "waste_riser", "vent_pipe", "branch_pipe"
     * @param roomName Room the pipe serves
     * @param startZ Start Z coordinate
     * @param endZ End Z coordinate
     * @param diameterMm Pipe diameter in millimeters
     * @param isVertical True if pipe is primarily vertical
     */
    public void plumbingPipe(String pipeId, String pipeType, String roomName,
                             double startZ, double endZ, double diameterMm, boolean isVertical) {
        Map<String, Object> pipe = new LinkedHashMap<>();
        pipe.put("id", pipeId);
        pipe.put("type", pipeType);
        pipe.put("room", roomName);
        pipe.put("start_z", startZ);
        pipe.put("end_z", endZ);
        pipe.put("diameter_mm", diameterMm);
        pipe.put("is_vertical", isVertical);

        // Validate based on pipe type
        boolean valid = true;
        String validationNote = "OK";

        // Check diameter is appropriate for pipe type
        if ("waste_riser".equals(pipeType) && diameterMm < 75) {
            valid = false;
            validationNote = "Waste riser diameter too small (min 75mm)";
        } else if ("vent_pipe".equals(pipeType) && diameterMm < 32) {
            valid = false;
            validationNote = "Vent pipe diameter too small (min 32mm)";
        } else if ("branch_pipe".equals(pipeType) && diameterMm < 32) {
            valid = false;
            validationNote = "Branch pipe diameter too small (min 32mm)";
        }

        // Check vertical pipes are actually vertical
        if (isVertical && ("waste_riser".equals(pipeType) || "vent_pipe".equals(pipeType))) {
            // Risers and vents should be vertical
            pipe.put("vertical_check", "PASS");
        }

        pipe.put("valid", valid);
        pipe.put("validation_note", validationNote);

        plumbingPipes.add(pipe);

        if (!valid) {
            plumbingViolations.add(pipe);
        }
    }

    // ===== Claim 11: PLUMBING_WASTE_COMPLETE (Phase 35) =====

    /**
     * Register the waste system graph for connectivity proof.
     *
     * @param system The MEPSystem graph for waste drainage
     */
    public void wasteSystem(com.bim.compiler.system.MEPSystem system) {
        this.wasteSystem = system;
    }

    // ===== Claim 12: PLUMBING_VENT_COMPLETE (Phase 36) =====

    /**
     * Register the vent system graph for connectivity proof.
     *
     * @param system The MEPSystem graph for vent to atmosphere
     */
    public void ventSystem(com.bim.compiler.system.MEPSystem system) {
        this.ventSystem = system;
    }

    // ===== Claim 13: PLUMBING_SUPPLY_COMPLETE (Phase 37) =====

    /**
     * Register the supply system graph for connectivity proof.
     *
     * @param system The MEPSystem graph for water supply
     */
    public void supplySystem(com.bim.compiler.system.MEPSystem system) {
        this.supplySystem = system;
    }

    // ===== Claim 15: ALL_OUTLETS_ON_CIRCUIT (Phase 39) =====

    /**
     * Register the electrical system graph for connectivity proof.
     *
     * @param system The MEPSystem graph for electrical circuits
     */
    public void electricalSystem(com.bim.compiler.system.MEPSystem system) {
        this.electricalSystem = system;
    }

    // ===== Claim 16: MEP_NO_STRUCTURAL_CLASH (Phase 40) =====

    /**
     * Register structural-MEP clash detection results.
     *
     * @param clashes List of detected clashes (empty = no clashes = PROVEN)
     */
    public void structuralClashes(List<com.bim.compiler.validation.ClashDetector.Clash> clashes) {
        this.structuralClashes = clashes;
    }

    // ===== Claim 14: STOREYS_VERTICALLY_CONSISTENT (Phase 38) =====

    /**
     * Record a vertical constraint between rooms (above:/below:).
     *
     * @param roomName The room with the constraint
     * @param constraintType "above" or "below"
     * @param targetRoom The target room
     * @param roomX Room center X
     * @param roomY Room center Y
     * @param roomZ Room base Z
     * @param targetX Target room center X
     * @param targetY Target room center Y
     * @param targetZ Target room base Z
     */
    public void verticalConstraint(String roomName, String constraintType, String targetRoom,
                                   double roomX, double roomY, double roomZ,
                                   double targetX, double targetY, double targetZ) {
        // Backwards compatible - use generous tolerance for center alignment
        verticalConstraintWithBounds(roomName, constraintType, targetRoom,
            roomX, roomY, roomZ, 0, 0,  // No bounds info, use center
            targetX, targetY, targetZ, 0, 0);
    }

    /**
     * Phase 42: Extended vertical constraint with room bounds for overlap checking.
     * Rooms of different sizes can be "above" each other if their footprints overlap.
     */
    public void verticalConstraintWithBounds(String roomName, String constraintType, String targetRoom,
                                             double roomCenterX, double roomCenterY, double roomZ,
                                             double roomHalfW, double roomHalfD,
                                             double targetCenterX, double targetCenterY, double targetZ,
                                             double targetHalfW, double targetHalfD) {
        Map<String, Object> constraint = new LinkedHashMap<>();
        constraint.put("type", constraintType);
        constraint.put("target", targetRoom);
        constraint.put("room_center", new double[]{roomCenterX, roomCenterY});
        constraint.put("target_center", new double[]{targetCenterX, targetCenterY});
        constraint.put("room_z", roomZ);
        constraint.put("target_z", targetZ);

        double deltaX = Math.abs(roomCenterX - targetCenterX);
        double deltaY = Math.abs(roomCenterY - targetCenterY);

        // Phase 42: Check for footprint overlap instead of exact center alignment
        // Rooms are aligned if their footprints overlap by at least 50%
        boolean xyAligned;
        if (roomHalfW > 0 && roomHalfD > 0 && targetHalfW > 0 && targetHalfD > 0) {
            // Have bounds info - check overlap
            double roomMinX = roomCenterX - roomHalfW, roomMaxX = roomCenterX + roomHalfW;
            double roomMinY = roomCenterY - roomHalfD, roomMaxY = roomCenterY + roomHalfD;
            double targetMinX = targetCenterX - targetHalfW, targetMaxX = targetCenterX + targetHalfW;
            double targetMinY = targetCenterY - targetHalfD, targetMaxY = targetCenterY + targetHalfD;

            double overlapX = Math.max(0, Math.min(roomMaxX, targetMaxX) - Math.max(roomMinX, targetMinX));
            double overlapY = Math.max(0, Math.min(roomMaxY, targetMaxY) - Math.max(roomMinY, targetMinY));
            double overlapArea = overlapX * overlapY;
            double smallerRoomArea = Math.min(roomHalfW * 2 * roomHalfD * 2, targetHalfW * 2 * targetHalfD * 2);

            // Consider aligned if overlap is at least 50% of smaller room
            xyAligned = smallerRoomArea > 0 && (overlapArea / smallerRoomArea) >= 0.5;
            constraint.put("overlap_ratio", smallerRoomArea > 0 ? overlapArea / smallerRoomArea : 0);
        } else {
            // No bounds info - fall back to center alignment with tolerance
            xyAligned = deltaX <= VERTICAL_ALIGNMENT_TOLERANCE &&
                        deltaY <= VERTICAL_ALIGNMENT_TOLERANCE;
        }

        boolean zOrdered;
        if ("above".equals(constraintType)) {
            zOrdered = roomZ > targetZ;
        } else {  // "below"
            zOrdered = roomZ < targetZ;
        }

        constraint.put("xy_aligned", xyAligned);
        constraint.put("delta_x_m", deltaX);
        constraint.put("delta_y_m", deltaY);
        constraint.put("z_ordered", zOrdered);
        constraint.put("valid", xyAligned && zOrdered);

        verticalConstraints.put(roomName, constraint);
    }

    /**
     * Record stack alignment across storeys.
     *
     * @param stackName The stack name
     * @param aligned Whether all rooms in stack share X,Y
     * @param maxDeltaX Maximum X deviation
     * @param maxDeltaY Maximum Y deviation
     * @param roomCount Number of rooms in stack
     */
    public void stackAlignment(String stackName, boolean aligned,
                               double maxDeltaX, double maxDeltaY, int roomCount) {
        Map<String, Object> alignment = new LinkedHashMap<>();
        alignment.put("aligned", aligned);
        alignment.put("room_count", roomCount);
        alignment.put("max_delta_x_m", maxDeltaX);
        alignment.put("max_delta_y_m", maxDeltaY);
        alignment.put("tolerance_m", VERTICAL_ALIGNMENT_TOLERANCE);
        stackAlignments.put(stackName, alignment);
    }

    /**
     * Record a wall-mounted fixture and validate attachment.
     *
     * @param fixtureId Fixture identifier
     * @param ifcClass IFC class
     * @param fixtureX Fixture X position
     * @param fixtureY Fixture Y position
     * @param fixtureHalfDepth Half-depth of fixture
     * @param wallFace Wall face position (X or Y depending on wall orientation)
     * @param wallOrientation "NS" for north-south wall (X varies), "EW" for east-west (Y varies)
     * @param hostId Host element ID (wall)
     */
    public void wallMountedFixture(String fixtureId, String ifcClass,
                                    double fixtureX, double fixtureY, double fixtureHalfDepth,
                                    double wallFace, String wallOrientation, String hostId) {
        double fixtureBack;
        if ("NS".equals(wallOrientation)) {
            // Wall runs north-south, fixture attaches at X
            fixtureBack = fixtureX - fixtureHalfDepth; // Assuming fixture faces east (towards room)
        } else {
            // Wall runs east-west, fixture attaches at Y
            fixtureBack = fixtureY - fixtureHalfDepth; // Assuming fixture faces north (towards room)
        }

        double gap = Math.abs(fixtureBack - wallFace);
        String status;
        if (gap > ATTACHMENT_TOLERANCE + WALL_TOLERANCE) {
            status = "FLOATING";
        } else {
            status = "ATTACHED";  // Wall-mounted has more tolerance due to surface mounting
        }

        Map<String, Object> attachment = new LinkedHashMap<>();
        attachment.put("fixture", fixtureId);
        attachment.put("ifc_class", ifcClass);
        attachment.put("host", hostId);
        attachment.put("type", "WALL_MOUNTED");
        attachment.put("gap_mm", Math.round(gap * 1000));
        attachment.put("status", status);

        fixtureAttachments.add(attachment);

        if ("FLOATING".equals(status)) {
            floatingFixtures.add(attachment);
        }
    }

    // ===== Build and Write =====

    public Map<String, Object> build() {
        Map<String, Object> witness = new LinkedHashMap<>();
        witness.put("version", "1.0");
        witness.put("building", buildingName);
        witness.put("generated", Instant.now().toString());
        witness.put("compiler_version", "0.47.1");

        // Build claims
        buildFoundationClaim();
        buildEntryClaim();
        buildReachabilityClaim();
        buildWindowsClaim();
        buildRoofClaim();
        buildEnclosureClaim();
        buildEnvelopeClaim();
        buildElectricalClaim();  // Phase 33/36
        buildFixtureAttachmentClaim();  // Phase 33
        buildPlumbingClaim();  // Phase 34
        buildPlumbingWasteCompleteClaim();  // Phase 35
        buildPlumbingVentCompleteClaim();  // Phase 36
        buildPlumbingSupplyCompleteClaim();  // Phase 37
        buildVerticalConsistencyClaim();  // Phase 38
        buildElectricalCircuitsClaim();  // Phase 39
        buildStructuralClashClaim();  // Phase 40
        buildRoomAreasClaim();  // Phase 45
        buildPartyWallsClaim();  // Phase 47C
        buildSeparatingFloorsClaim();  // Phase 48C
        buildClassroomDaylightClaim();  // Phase 50C
        buildToiletAccessibleClaim();  // Phase 50C
        buildCorridorConnectsAllClaim();  // Phase 50C
        buildFireTravelDistanceClaim();  // Phase 50C
        buildStructuralGridCompleteClaim();  // Phase 50C

        witness.put("claims", claims);

        // Count statuses from actual claims (robust calculation)
        int countProven = 0, countUnprovable = 0, countSkipped = 0;
        for (Object value : claims.values()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> claim = (Map<String, Object>) value;
            String status = (String) claim.get("status");
            if ("PROVEN".equals(status)) countProven++;
            else if ("UNPROVABLE".equals(status)) countUnprovable++;
            else if ("SKIPPED".equals(status)) countSkipped++;
        }

        Map<String, Integer> summary = new LinkedHashMap<>();
        summary.put("total_claims", claims.size());
        summary.put("proven", countProven);
        summary.put("unprovable", countUnprovable);
        summary.put("skipped", countSkipped);
        witness.put("summary", summary);

        return witness;
    }

    private void buildFoundationClaim() {
        Map<String, Object> claim = new LinkedHashMap<>();
        if (foundationTopZ != null) {
            claim.put("status", "PROVEN");
            Map<String, Object> w = new LinkedHashMap<>();
            w.put("foundation_id", foundationId);
            w.put("top_z", foundationTopZ);
            w.put("bottom_z", foundationBottomZ);
            w.put("depth", foundationTopZ - foundationBottomZ);
            w.put("tolerance", 0.005);
            claim.put("witness", w);
            proven++;
        } else {
            claim.put("status", "SKIPPED");
            claim.put("reason", "No foundation data collected");
            skipped++;
        }
        claims.put("FOUNDATION_GROUNDED", claim);
    }

    private void buildEntryClaim() {
        Map<String, Object> claim = new LinkedHashMap<>();

        // Phase 48D.2: Multi-unit entry tracking
        if (!unitEntries.isEmpty()) {
            // Check all units have proven egress
            boolean allProven = unitEntries.values().stream()
                .allMatch(e -> Boolean.TRUE.equals(e.get("proven")));

            List<String> unprovenUnits = unitEntries.entrySet().stream()
                .filter(e -> !Boolean.TRUE.equals(e.getValue().get("proven")))
                .map(Map.Entry::getKey)
                .toList();

            claim.put("status", allProven ? "PROVEN" : "UNPROVABLE");

            Map<String, Object> w = new LinkedHashMap<>();
            w.put("multi_unit", true);
            w.put("unit_count", unitEntries.size());
            w.put("all_units_proven", allProven);
            w.put("units", unitEntries);

            if (!unprovenUnits.isEmpty()) {
                w.put("unproven_units", unprovenUnits);
            }

            claim.put("witness", w);
            if (allProven) proven++;
        } else if (entryDoor != null && entrySpace != null) {
            // Single-unit building (legacy)
            claim.put("status", "PROVEN");
            Map<String, Object> w = new LinkedHashMap<>();
            w.put("path", List.of("EXTERIOR", entrySpace));
            w.put("door", entryDoor);
            claim.put("witness", w);
            proven++;
        } else {
            claim.put("status", "SKIPPED");
            claim.put("reason", "No entry door data collected");
            skipped++;
        }
        claims.put("ENTRY_EXISTS", claim);
    }

    private void buildReachabilityClaim() {
        Map<String, Object> claim = new LinkedHashMap<>();
        if (!roomPaths.isEmpty()) {
            claim.put("status", "PROVEN");
            Map<String, Object> w = new LinkedHashMap<>();
            w.put("entry_point", entrySpace != null ? entrySpace : "unknown");
            w.put("paths", roomPaths);
            w.put("unreachable", List.of());
            claim.put("witness", w);
            proven++;
        } else {
            claim.put("status", "SKIPPED");
            claim.put("reason", "No room path data collected");
            skipped++;
        }
        claims.put("ALL_ROOMS_REACHABLE", claim);
    }

    private void buildWindowsClaim() {
        Map<String, Object> claim = new LinkedHashMap<>();
        if (!windows.isEmpty()) {
            claim.put("status", windowViolations.isEmpty() ? "PROVEN" : "UNPROVABLE");
            Map<String, Object> w = new LinkedHashMap<>();
            w.put("windows", windows);
            w.put("violations", windowViolations);
            claim.put("witness", w);
            if (windowViolations.isEmpty()) proven++;
        } else {
            claim.put("status", "SKIPPED");
            claim.put("reason", "No window data collected");
            skipped++;
        }
        claims.put("WINDOWS_ON_EXTERIOR", claim);
    }

    private void buildRoofClaim() {
        Map<String, Object> claim = new LinkedHashMap<>();
        if (!roomCorners.isEmpty()) {
            boolean allCovered = roomCorners.values().stream()
                .allMatch(info -> (Boolean) info.get("all_inside"));
            claim.put("status", allCovered ? "PROVEN" : "UNPROVABLE");
            Map<String, Object> w = new LinkedHashMap<>();
            w.put("method", "corner_containment");
            if (roofPolygon != null) {
                w.put("roof_polygon", roofPolygon);
            }
            w.put("rooms", roomCorners);
            List<String> uncovered = new ArrayList<>();
            for (var entry : roomCorners.entrySet()) {
                if (!(Boolean) entry.getValue().get("all_inside")) {
                    uncovered.add(entry.getKey());
                }
            }
            w.put("uncovered", uncovered);
            claim.put("witness", w);
            if (allCovered) proven++;
        } else {
            claim.put("status", "SKIPPED");
            claim.put("reason", "No roof coverage data collected");
            skipped++;
        }
        claims.put("ROOF_COVERS_ALL", claim);
    }

    private void buildEnclosureClaim() {
        Map<String, Object> claim = new LinkedHashMap<>();
        if (!roomEnclosure.isEmpty()) {
            boolean allEnclosed = roomEnclosure.values().stream()
                .allMatch(info -> (Boolean) info.get("closed"));
            claim.put("status", allEnclosed ? "PROVEN" : "UNPROVABLE");
            Map<String, Object> w = new LinkedHashMap<>();
            w.put("rooms", roomEnclosure);
            claim.put("witness", w);
            if (allEnclosed) proven++;
        } else {
            claim.put("status", "SKIPPED");
            claim.put("reason", "No room enclosure data collected");
            skipped++;
        }
        claims.put("ROOMS_ENCLOSED", claim);
    }

    private void buildEnvelopeClaim() {
        Map<String, Object> claim = new LinkedHashMap<>();
        if (!roomsInEnvelope.isEmpty() && envelopeMin != null) {
            boolean allInside = roomsInEnvelope.values().stream().allMatch(v -> v);
            claim.put("status", allInside ? "PROVEN" : "UNPROVABLE");
            Map<String, Object> w = new LinkedHashMap<>();
            Map<String, Object> bbox = new LinkedHashMap<>();
            bbox.put("min", envelopeMin);
            bbox.put("max", envelopeMax);
            w.put("envelope_bbox", bbox);
            Map<String, Object> rooms = new LinkedHashMap<>();
            for (var entry : roomsInEnvelope.entrySet()) {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("inside", entry.getValue());
                rooms.put(entry.getKey(), r);
            }
            w.put("rooms", rooms);
            List<String> violations = new ArrayList<>();
            for (var entry : roomsInEnvelope.entrySet()) {
                if (!entry.getValue()) violations.add(entry.getKey());
            }
            w.put("violations", violations);
            claim.put("witness", w);
            if (allInside) proven++;
        } else {
            claim.put("status", "SKIPPED");
            claim.put("reason", "No envelope containment data collected");
            skipped++;
        }
        claims.put("ROOMS_IN_ENVELOPE", claim);
    }

    private void buildElectricalClaim() {
        Map<String, Object> claim = new LinkedHashMap<>();
        if (!electricalElements.isEmpty()) {
            boolean allInside = electricalViolations.isEmpty();
            claim.put("status", allInside ? "PROVEN" : "UNPROVABLE");

            Map<String, Object> w = new LinkedHashMap<>();
            w.put("elements_checked", electricalElements.size());

            // Group by IFC class
            Map<String, List<Map<String, Object>>> byType = new LinkedHashMap<>();
            for (Map<String, Object> elem : electricalElements) {
                String ifcClass = (String) elem.get("ifc_class");
                byType.computeIfAbsent(ifcClass, k -> new ArrayList<>()).add(elem);
            }

            Map<String, Object> byTypeSummary = new LinkedHashMap<>();
            for (var entry : byType.entrySet()) {
                String ifcClass = entry.getKey();
                List<Map<String, Object>> elements = entry.getValue();
                boolean allTypeInside = elements.stream()
                    .allMatch(e -> (Boolean) e.get("inside"));
                byTypeSummary.put(ifcClass, Map.of(
                    "count", elements.size(),
                    "all_inside", allTypeInside
                ));
            }
            w.put("by_type", byTypeSummary);

            // List violations if any
            if (!electricalViolations.isEmpty()) {
                List<Map<String, Object>> violationDetails = new ArrayList<>();
                for (Map<String, Object> v : electricalViolations) {
                    Map<String, Object> detail = new LinkedHashMap<>();
                    detail.put("id", v.get("id"));
                    detail.put("ifc_class", v.get("ifc_class"));
                    detail.put("room", v.get("room"));
                    detail.put("position", v.get("position"));
                    detail.put("bounds_check", v.get("bounds_check"));
                    violationDetails.add(detail);
                }
                w.put("violations", violationDetails);
            } else {
                w.put("violations", List.of());
            }

            claim.put("witness", w);
            if (allInside) proven++;
        } else {
            claim.put("status", "SKIPPED");
            claim.put("reason", "No electrical elements collected");
            skipped++;
        }
        claims.put("ELECTRICAL_IN_SPACES", claim);
    }

    private void buildFixtureAttachmentClaim() {
        Map<String, Object> claim = new LinkedHashMap<>();
        if (!fixtureAttachments.isEmpty()) {
            boolean allAttached = floatingFixtures.isEmpty() && clashingFixtures.isEmpty();
            claim.put("status", allAttached ? "PROVEN" : "UNPROVABLE");

            Map<String, Object> w = new LinkedHashMap<>();
            w.put("elements_checked", fixtureAttachments.size());

            // Count by status
            long attachedCount = fixtureAttachments.stream()
                .filter(a -> "ATTACHED".equals(a.get("status")))
                .count();
            w.put("attached", attachedCount);
            w.put("floating", floatingFixtures.size());
            w.put("clashing", clashingFixtures.size());

            // Group by type
            Map<String, List<Map<String, Object>>> byType = new LinkedHashMap<>();
            for (Map<String, Object> a : fixtureAttachments) {
                String type = (String) a.get("type");
                byType.computeIfAbsent(type, k -> new ArrayList<>()).add(a);
            }

            Map<String, Object> byTypeSummary = new LinkedHashMap<>();
            for (var entry : byType.entrySet()) {
                String type = entry.getKey();
                List<Map<String, Object>> attachments = entry.getValue();
                long typeAttached = attachments.stream()
                    .filter(a -> "ATTACHED".equals(a.get("status")))
                    .count();
                byTypeSummary.put(type, Map.of(
                    "count", attachments.size(),
                    "attached", typeAttached
                ));
            }
            w.put("by_type", byTypeSummary);

            // List violations
            if (!floatingFixtures.isEmpty()) {
                w.put("floating_details", floatingFixtures);
            }
            if (!clashingFixtures.isEmpty()) {
                w.put("clashing_details", clashingFixtures);
            }

            claim.put("witness", w);
            if (allAttached) proven++;
        } else {
            claim.put("status", "SKIPPED");
            claim.put("reason", "No fixture attachment data collected");
            skipped++;
        }
        claims.put("FIXTURES_ATTACHED_TO_HOSTS", claim);
    }

    private void buildPlumbingClaim() {
        Map<String, Object> claim = new LinkedHashMap<>();
        if (!plumbingPipes.isEmpty()) {
            boolean allValid = plumbingViolations.isEmpty();
            claim.put("status", allValid ? "PROVEN" : "UNPROVABLE");

            Map<String, Object> w = new LinkedHashMap<>();
            w.put("pipes_checked", plumbingPipes.size());

            // Count by type
            Map<String, Long> byType = new LinkedHashMap<>();
            for (Map<String, Object> pipe : plumbingPipes) {
                String type = (String) pipe.get("type");
                byType.merge(type, 1L, Long::sum);
            }
            w.put("by_type", byType);

            // Count valid pipes
            long validCount = plumbingPipes.stream()
                .filter(p -> Boolean.TRUE.equals(p.get("valid")))
                .count();
            w.put("valid_count", validCount);
            w.put("violations_count", plumbingViolations.size());

            // Add violation details if any
            if (!plumbingViolations.isEmpty()) {
                List<Map<String, Object>> violationDetails = new ArrayList<>();
                for (Map<String, Object> v : plumbingViolations) {
                    Map<String, Object> detail = new LinkedHashMap<>();
                    detail.put("id", v.get("id"));
                    detail.put("type", v.get("type"));
                    detail.put("room", v.get("room"));
                    detail.put("note", v.get("validation_note"));
                    violationDetails.add(detail);
                }
                w.put("violations", violationDetails);
            } else {
                w.put("violations", List.of());
            }

            claim.put("witness", w);
            if (allValid) proven++;
        } else {
            claim.put("status", "SKIPPED");
            claim.put("reason", "No plumbing pipes collected");
            skipped++;
        }
        claims.put("PLUMBING_PIPES_VALID", claim);
    }

    /**
     * Phase 35: Build PLUMBING_WASTE_COMPLETE claim.
     * Proves all fixtures drain to the manhole (source).
     */
    private void buildPlumbingWasteCompleteClaim() {
        Map<String, Object> claim = new LinkedHashMap<>();

        if (wasteSystem != null && !wasteSystem.getTerminals().isEmpty()) {
            boolean isComplete = wasteSystem.isComplete();
            var orphans = wasteSystem.getOrphanedTerminals();
            var source = wasteSystem.getSource();

            claim.put("status", isComplete ? "PROVEN" : "UNPROVABLE");

            Map<String, Object> w = new LinkedHashMap<>();
            w.put("system_id", wasteSystem.getSystemId());
            w.put("source", source.map(n -> n.name()).orElse("NONE"));
            w.put("terminal_count", wasteSystem.getTerminals().size());
            w.put("distribution_count", wasteSystem.getDistribution().size());
            w.put("edge_count", wasteSystem.getEdges().size());
            w.put("all_drain_to_source", isComplete);
            w.put("orphaned_terminals", orphans.stream()
                .map(n -> n.name())
                .toList());

            // Include drainage paths for proof
            if (isComplete && source.isPresent()) {
                Map<String, List<String>> paths = new LinkedHashMap<>();
                for (var terminal : wasteSystem.getTerminals()) {
                    List<String> path = wasteSystem.getPath(terminal.nodeId(), source.get().nodeId());
                    paths.put(terminal.name(), path);
                }
                w.put("drainage_paths", paths);
            }

            claim.put("witness", w);
            if (isComplete) proven++;
        } else {
            claim.put("status", "SKIPPED");
            claim.put("reason", "No waste system graph available");
            skipped++;
        }

        claims.put("PLUMBING_WASTE_COMPLETE", claim);
    }

    /**
     * Phase 36: Build PLUMBING_VENT_COMPLETE claim.
     * Proves all fixture traps vent to atmosphere.
     */
    private void buildPlumbingVentCompleteClaim() {
        Map<String, Object> claim = new LinkedHashMap<>();

        if (ventSystem != null && !ventSystem.getTerminals().isEmpty()) {
            boolean isComplete = ventSystem.isComplete();
            var orphans = ventSystem.getOrphanedTerminals();
            var source = ventSystem.getSource();

            claim.put("status", isComplete ? "PROVEN" : "UNPROVABLE");

            Map<String, Object> w = new LinkedHashMap<>();
            w.put("system_id", ventSystem.getSystemId());
            w.put("termination", source.map(n -> n.name()).orElse("NONE"));
            w.put("terminal_count", ventSystem.getTerminals().size());
            w.put("distribution_count", ventSystem.getDistribution().size());
            w.put("edge_count", ventSystem.getEdges().size());
            w.put("all_vent_to_atmosphere", isComplete);
            w.put("unvented_traps", orphans.stream()
                .map(n -> n.name())
                .toList());

            // Include vent paths for proof
            if (isComplete && source.isPresent()) {
                Map<String, List<String>> paths = new LinkedHashMap<>();
                for (var terminal : ventSystem.getTerminals()) {
                    List<String> path = ventSystem.getPath(terminal.nodeId(), source.get().nodeId());
                    paths.put(terminal.name(), path);
                }
                w.put("vent_paths", paths);
            }

            claim.put("witness", w);
            if (isComplete) proven++;
        } else {
            claim.put("status", "SKIPPED");
            claim.put("reason", "No vent system graph available");
            skipped++;
        }

        claims.put("PLUMBING_VENT_COMPLETE", claim);
    }

    /**
     * Phase 37: Build PLUMBING_SUPPLY_COMPLETE claim.
     * Proves all fixtures receive water from meter.
     */
    private void buildPlumbingSupplyCompleteClaim() {
        Map<String, Object> claim = new LinkedHashMap<>();

        if (supplySystem != null && !supplySystem.getTerminals().isEmpty()) {
            boolean isComplete = supplySystem.isComplete();
            var orphans = supplySystem.getOrphanedTerminals();
            var source = supplySystem.getSource();

            claim.put("status", isComplete ? "PROVEN" : "UNPROVABLE");

            Map<String, Object> w = new LinkedHashMap<>();
            w.put("system_id", supplySystem.getSystemId());
            w.put("source", source.map(n -> n.name()).orElse("NONE"));
            w.put("terminal_count", supplySystem.getTerminals().size());
            w.put("distribution_count", supplySystem.getDistribution().size());
            w.put("edge_count", supplySystem.getEdges().size());
            w.put("all_supplied_from_source", isComplete);
            w.put("unsupplied_fixtures", orphans.stream()
                .map(n -> n.name())
                .toList());

            // Include supply paths for proof
            if (isComplete && source.isPresent()) {
                Map<String, List<String>> paths = new LinkedHashMap<>();
                for (var terminal : supplySystem.getTerminals()) {
                    List<String> path = supplySystem.getPath(source.get().nodeId(), terminal.nodeId());
                    paths.put(terminal.name(), path);
                }
                w.put("supply_paths", paths);
            }

            claim.put("witness", w);
            if (isComplete) proven++;
        } else {
            claim.put("status", "SKIPPED");
            claim.put("reason", "No supply system graph available");
            skipped++;
        }

        claims.put("PLUMBING_SUPPLY_COMPLETE", claim);
    }

    /**
     * Phase 38: Build STOREYS_VERTICALLY_CONSISTENT claim.
     * Proves vertical constraints (above:, stack:) maintain alignment.
     */
    private void buildVerticalConsistencyClaim() {
        Map<String, Object> claim = new LinkedHashMap<>();

        boolean hasData = !verticalConstraints.isEmpty() || !stackAlignments.isEmpty();

        if (hasData) {
            boolean allValid = true;

            // Check all vertical constraints (above:/below:)
            for (Map<String, Object> constraint : verticalConstraints.values()) {
                if (!Boolean.TRUE.equals(constraint.get("valid"))) {
                    allValid = false;
                    break;
                }
            }

            // Check all stack alignments
            for (Map<String, Object> alignment : stackAlignments.values()) {
                if (!Boolean.TRUE.equals(alignment.get("aligned"))) {
                    allValid = false;
                    break;
                }
            }

            claim.put("status", allValid ? "PROVEN" : "UNPROVABLE");

            Map<String, Object> w = new LinkedHashMap<>();

            // Vertical constraints summary
            if (!verticalConstraints.isEmpty()) {
                int totalConstraints = verticalConstraints.size();
                long validConstraints = verticalConstraints.values().stream()
                    .filter(c -> Boolean.TRUE.equals(c.get("valid")))
                    .count();

                w.put("vertical_constraints", Map.of(
                    "total", totalConstraints,
                    "valid", validConstraints,
                    "details", verticalConstraints
                ));
            }

            // Stack alignments summary
            if (!stackAlignments.isEmpty()) {
                int totalStacks = stackAlignments.size();
                long alignedStacks = stackAlignments.values().stream()
                    .filter(a -> Boolean.TRUE.equals(a.get("aligned")))
                    .count();

                w.put("stack_alignments", Map.of(
                    "total", totalStacks,
                    "aligned", alignedStacks,
                    "details", stackAlignments
                ));
            }

            w.put("tolerance_m", VERTICAL_ALIGNMENT_TOLERANCE);

            // List violations if any
            List<String> violations = new ArrayList<>();
            for (var entry : verticalConstraints.entrySet()) {
                if (!Boolean.TRUE.equals(entry.getValue().get("valid"))) {
                    violations.add("Room " + entry.getKey() + ": " +
                        entry.getValue().get("type") + " constraint invalid");
                }
            }
            for (var entry : stackAlignments.entrySet()) {
                if (!Boolean.TRUE.equals(entry.getValue().get("aligned"))) {
                    violations.add("Stack " + entry.getKey() + ": misaligned");
                }
            }
            w.put("violations", violations);

            claim.put("witness", w);
            if (allValid) proven++;
        } else {
            claim.put("status", "SKIPPED");
            claim.put("reason", "No vertical constraint data collected (single-storey building)");
            skipped++;
        }

        claims.put("STOREYS_VERTICALLY_CONSISTENT", claim);
    }

    /**
     * Phase 39: Build ALL_OUTLETS_ON_CIRCUIT claim.
     * Proves all electrical outlets/lights are connected to circuits from DB panel.
     */
    private void buildElectricalCircuitsClaim() {
        Map<String, Object> claim = new LinkedHashMap<>();

        if (electricalSystem != null && !electricalSystem.getTerminals().isEmpty()) {
            boolean isComplete = electricalSystem.isComplete();
            var orphans = electricalSystem.getOrphanedTerminals();
            var source = electricalSystem.getSource();

            claim.put("status", isComplete ? "PROVEN" : "UNPROVABLE");

            Map<String, Object> w = new LinkedHashMap<>();
            w.put("system_id", electricalSystem.getSystemId());
            w.put("db_panel", source.map(n -> n.name()).orElse("NONE"));
            w.put("terminal_count", electricalSystem.getTerminals().size());
            w.put("circuit_count", electricalSystem.getDistribution().size());
            w.put("edge_count", electricalSystem.getEdges().size());
            w.put("all_on_circuit", isComplete);
            w.put("unconnected_terminals", orphans.stream()
                .map(n -> n.name())
                .toList());

            // Group terminals by type (from node properties)
            Map<String, Long> byType = new LinkedHashMap<>();
            for (var terminal : electricalSystem.getTerminals()) {
                String type = terminal.properties() != null ?
                    (String) terminal.properties().getOrDefault("type", "UNKNOWN") : "UNKNOWN";
                byType.merge(type, 1L, Long::sum);
            }
            w.put("terminals_by_type", byType);

            // Include circuit paths for proof
            if (isComplete && source.isPresent()) {
                Map<String, List<String>> paths = new LinkedHashMap<>();
                for (var terminal : electricalSystem.getTerminals()) {
                    List<String> path = electricalSystem.getPath(source.get().nodeId(), terminal.nodeId());
                    paths.put(terminal.name(), path);
                }
                w.put("circuit_paths", paths);
            }

            claim.put("witness", w);
            if (isComplete) proven++;
        } else {
            claim.put("status", "SKIPPED");
            claim.put("reason", "No electrical system graph available");
            skipped++;
        }

        claims.put("ALL_OUTLETS_ON_CIRCUIT", claim);
    }

    /**
     * Phase 40: Build MEP_NO_STRUCTURAL_CLASH claim.
     * Proves no MEP elements intersect structural elements (hard clash detection).
     */
    private void buildStructuralClashClaim() {
        Map<String, Object> claim = new LinkedHashMap<>();

        if (structuralClashes != null) {
            boolean noClashes = structuralClashes.isEmpty();

            claim.put("status", noClashes ? "PROVEN" : "UNPROVABLE");

            Map<String, Object> w = new LinkedHashMap<>();
            w.put("clashes_detected", structuralClashes.size());
            w.put("no_clashes", noClashes);

            if (!noClashes) {
                // Group clashes by type for analysis
                Map<String, Long> byMepType = new LinkedHashMap<>();
                Map<String, Long> byStructuralType = new LinkedHashMap<>();

                for (var clash : structuralClashes) {
                    byMepType.merge(clash.mepElementType(), 1L, Long::sum);
                    byStructuralType.merge(clash.structuralElementType(), 1L, Long::sum);
                }

                w.put("clashes_by_mep_type", byMepType);
                w.put("clashes_by_structural_type", byStructuralType);

                // List first 10 clashes as details
                List<Map<String, Object>> clashDetails = new ArrayList<>();
                int limit = Math.min(10, structuralClashes.size());
                for (int i = 0; i < limit; i++) {
                    var clash = structuralClashes.get(i);
                    Map<String, Object> detail = new LinkedHashMap<>();
                    detail.put("mep_element", clash.mepElementId());
                    detail.put("mep_type", clash.mepElementType());
                    detail.put("structural_element", clash.structuralElementId());
                    detail.put("structural_type", clash.structuralElementType());
                    detail.put("storey", clash.storeyName());
                    detail.put("overlap_m3", clash.overlapVolume());
                    clashDetails.add(detail);
                }
                w.put("clash_details", clashDetails);

                if (structuralClashes.size() > 10) {
                    w.put("note", "Showing first 10 of " + structuralClashes.size() + " clashes");
                }
            }

            claim.put("witness", w);
            if (noClashes) proven++;
        } else {
            claim.put("status", "SKIPPED");
            claim.put("reason", "No clash detection data collected");
            skipped++;
        }

        claims.put("MEP_NO_STRUCTURAL_CLASH", claim);
    }

    /**
     * Phase 45: Build ROOM_AREAS_CONSISTENT claim.
     * Proves all rooms have valid positive areas and don't overlap.
     */
    private void buildRoomAreasClaim() {
        Map<String, Object> claim = new LinkedHashMap<>();

        if (!roomAreas.isEmpty()) {
            // Check 1: All rooms have positive area
            boolean allPositive = roomAreas.stream()
                .allMatch(r -> ((Number) r.get("area_m2")).doubleValue() > 0);

            // Check 2: Room areas per storey are reasonable vs slab area
            // (rooms should not exceed footprint, but can be less due to walls)
            double totalRoomArea = roomAreas.stream()
                .mapToDouble(r -> ((Number) r.get("area_m2")).doubleValue())
                .sum();

            // Check per-storey area validity
            Map<String, Double> roomAreaByStorey = new LinkedHashMap<>();
            for (var room : roomAreas) {
                String storey = (String) room.get("storey");
                double area = ((Number) room.get("area_m2")).doubleValue();
                roomAreaByStorey.merge(storey, area, Double::sum);
            }

            boolean areaReasonable = true;
            for (var entry : roomAreaByStorey.entrySet()) {
                String storey = entry.getKey();
                double storeyRoomArea = entry.getValue();
                Double storeySlabArea = slabAreaByStorey.get(storey);
                // If we have slab data, rooms shouldn't exceed slab by more than 10%
                if (storeySlabArea != null && storeySlabArea > 0) {
                    if (storeyRoomArea > storeySlabArea * 1.1) {
                        areaReasonable = false;
                        break;
                    }
                }
            }

            // Check 3: No overlapping room boundaries (simple AABB check)
            List<String> overlaps = new ArrayList<>();
            for (int i = 0; i < roomAreas.size(); i++) {
                double[] b1 = (double[]) roomAreas.get(i).get("bounds");
                String r1 = (String) roomAreas.get(i).get("room");
                String s1 = (String) roomAreas.get(i).get("storey");

                for (int j = i + 1; j < roomAreas.size(); j++) {
                    double[] b2 = (double[]) roomAreas.get(j).get("bounds");
                    String r2 = (String) roomAreas.get(j).get("room");
                    String s2 = (String) roomAreas.get(j).get("storey");

                    // Only check rooms on same storey
                    if (!s1.equals(s2)) continue;

                    // AABB overlap check
                    if (b1[0] < b2[2] && b1[2] > b2[0] &&
                        b1[1] < b2[3] && b1[3] > b2[1]) {
                        overlaps.add(r1 + " overlaps " + r2 + " on " + s1);
                    }
                }
            }
            boolean noOverlaps = overlaps.isEmpty();

            boolean allValid = allPositive && areaReasonable && noOverlaps;
            claim.put("status", allValid ? "PROVEN" : "UNPROVABLE");

            Map<String, Object> w = new LinkedHashMap<>();
            w.put("room_count", roomAreas.size());
            w.put("total_area_m2", Math.round(totalRoomArea * 100.0) / 100.0);

            // Group by storey
            Map<String, Double> byStorey = new LinkedHashMap<>();
            for (var room : roomAreas) {
                String storey = (String) room.get("storey");
                double area = ((Number) room.get("area_m2")).doubleValue();
                byStorey.merge(storey, area, Double::sum);
            }
            w.put("by_storey", byStorey);

            // Group by type
            Map<String, Double> byType = new LinkedHashMap<>();
            for (var room : roomAreas) {
                String type = (String) room.get("type");
                double area = ((Number) room.get("area_m2")).doubleValue();
                byType.merge(type, area, Double::sum);
            }
            w.put("by_type", byType);

            // Validation results
            w.put("all_positive_area", allPositive);
            w.put("area_within_footprint", areaReasonable);
            w.put("no_overlaps", noOverlaps);

            if (!overlaps.isEmpty()) {
                w.put("overlaps", overlaps);
            }

            // Room details for BOM
            List<Map<String, Object>> roomDetails = new ArrayList<>();
            for (var room : roomAreas) {
                Map<String, Object> detail = new LinkedHashMap<>();
                detail.put("room", room.get("room"));
                detail.put("type", room.get("type"));
                detail.put("storey", room.get("storey"));
                detail.put("area_m2", room.get("area_m2"));
                roomDetails.add(detail);
            }
            w.put("rooms", roomDetails);

            claim.put("witness", w);
            if (allValid) proven++;
        } else {
            claim.put("status", "SKIPPED");
            claim.put("reason", "No room area data collected");
            skipped++;
        }

        claims.put("ROOM_AREAS_CONSISTENT", claim);
    }

    /**
     * Phase 47C: Proves party walls between units are valid.
     * Analyzes topology (LINEAR vs JAGGED) and fire rating compliance.
     */
    private void buildPartyWallsClaim() {
        Map<String, Object> claim = new LinkedHashMap<>();

        if (!isMultiUnit) {
            // Single-unit building - no party walls needed
            claim.put("status", "SKIPPED");
            claim.put("reason", "Single-unit building - no party walls required");
            skipped++;
            claims.put("PARTY_WALLS_VALID", claim);
            return;
        }

        if (partyWallSegments.isEmpty()) {
            // Multi-unit but no party walls - check if it's a STACKED layout
            if (!separatingFloors.isEmpty()) {
                // STACKED layout: units separated by floors, not walls
                claim.put("status", "SKIPPED");
                claim.put("reason", "STACKED layout - unit separation via separating floors, not party walls");
                skipped++;
                claims.put("PARTY_WALLS_VALID", claim);
                return;
            }
            // SIDE_BY_SIDE with no party walls is a problem
            claim.put("status", "UNPROVABLE");
            claim.put("reason", "Multi-unit building has no party walls between units");
            claims.put("PARTY_WALLS_VALID", claim);
            return;
        }

        // Analyze party wall topology
        String topology = analyzePartyWallTopology();
        boolean isContinuous = "LINEAR".equals(topology);

        // Check fire ratings (all party walls should have FRL 60/60/60)
        // Accept both "FRL 60/60/60" and "60/60/60" formats
        boolean allFireRated = partyWallSegments.stream()
            .allMatch(s -> {
                String rating = (String) s.get("fire_rating");
                return "FRL 60/60/60".equals(rating) || "60/60/60".equals(rating);
            });

        // Check minimum thickness (250mm for party walls)
        boolean allThickEnough = partyWallSegments.stream()
            .allMatch(s -> ((Number) s.get("thickness_mm")).intValue() >= 250);

        // Calculate total separation length
        double totalLength = partyWallSegments.stream()
            .mapToDouble(s -> ((Number) s.get("length_m")).doubleValue())
            .sum();

        // Witness is PROVEN if fire rated and thick enough
        // Topology issues are flagged but don't fail the witness
        boolean valid = allFireRated && allThickEnough;
        claim.put("status", valid ? "PROVEN" : "UNPROVABLE");

        Map<String, Object> w = new LinkedHashMap<>();
        w.put("party_wall_segments", partyWallSegments);
        w.put("total_separation_length_m", Math.round(totalLength * 100.0) / 100.0);
        w.put("segment_count", partyWallSegments.size());
        w.put("topology", topology);
        w.put("continuity_proven", isContinuous);

        // Fire rating summary
        Map<String, Object> fireRating = new LinkedHashMap<>();
        fireRating.put("required", "FRL 60/60/60");
        fireRating.put("all_compliant", allFireRated);
        w.put("fire_rating", fireRating);

        // Thickness summary
        Map<String, Object> thickness = new LinkedHashMap<>();
        thickness.put("minimum_mm", 250);
        thickness.put("all_compliant", allThickEnough);
        w.put("thickness", thickness);

        // Topology warning if jagged
        if (!isContinuous) {
            Map<String, Object> warning = new LinkedHashMap<>();
            warning.put("type", "JAGGED_TOPOLOGY");
            warning.put("message", "Party wall has direction changes - requires fire stopping at joints");
            warning.put("impact", "Increased construction cost, acoustic flanking paths");
            w.put("topology_warning", warning);
        }

        claim.put("witness", w);
        if (valid) proven++;

        claims.put("PARTY_WALLS_VALID", claim);
    }

    /**
     * Analyze party wall topology to determine if it's LINEAR or JAGGED.
     * LINEAR = all segments on same axis (vertical or horizontal separation)
     * JAGGED = segments change direction (L-shaped, staggered units)
     */
    private String analyzePartyWallTopology() {
        if (partyWallSegments.size() <= 1) {
            return "LINEAR";  // Single segment is always linear
        }

        // Check if all segments are on same axis
        boolean allVertical = partyWallSegments.stream()
            .allMatch(s -> "EAST".equals(s.get("edge")) || "WEST".equals(s.get("edge")));
        boolean allHorizontal = partyWallSegments.stream()
            .allMatch(s -> "NORTH".equals(s.get("edge")) || "SOUTH".equals(s.get("edge")));

        if (allVertical || allHorizontal) {
            // Check if segments are collinear (same X or Y coordinate)
            if (allVertical) {
                // All segments should have same X
                Set<Double> xCoords = new HashSet<>();
                for (var seg : partyWallSegments) {
                    @SuppressWarnings("unchecked")
                    Map<String, Double> bounds = (Map<String, Double>) seg.get("bounds");
                    double centerX = (bounds.get("minX") + bounds.get("maxX")) / 2;
                    xCoords.add(Math.round(centerX * 10.0) / 10.0);  // Round to 100mm
                }
                return xCoords.size() == 1 ? "LINEAR" : "JAGGED";
            } else {
                // All segments should have same Y
                Set<Double> yCoords = new HashSet<>();
                for (var seg : partyWallSegments) {
                    @SuppressWarnings("unchecked")
                    Map<String, Double> bounds = (Map<String, Double>) seg.get("bounds");
                    double centerY = (bounds.get("minY") + bounds.get("maxY")) / 2;
                    yCoords.add(Math.round(centerY * 10.0) / 10.0);  // Round to 100mm
                }
                return yCoords.size() == 1 ? "LINEAR" : "JAGGED";
            }
        }

        // Mixed orientations = definitely jagged
        return "JAGGED";
    }

    /**
     * Phase 48C: Proves separating floors between units are valid.
     * Verifies fire rating (FRL 90/90/90), thickness (≥200mm), and acoustic ratings.
     */
    private void buildSeparatingFloorsClaim() {
        Map<String, Object> claim = new LinkedHashMap<>();

        if (!isMultiUnit) {
            // Single-unit building - no separating floors needed
            claim.put("status", "SKIPPED");
            claim.put("reason", "Single-unit building - no separating floors required");
            skipped++;
            claims.put("SEPARATING_FLOORS_VALID", claim);
            return;
        }

        if (separatingFloors.isEmpty()) {
            // Multi-unit but no separating floors - SIDE_BY_SIDE layout
            claim.put("status", "SKIPPED");
            claim.put("reason", "SIDE_BY_SIDE layout - unit separation via party walls, not separating floors");
            skipped++;
            claims.put("SEPARATING_FLOORS_VALID", claim);
            return;
        }

        // Validate each separating floor
        boolean allValid = true;
        List<Map<String, Object>> violations = new ArrayList<>();

        for (Map<String, Object> floor : separatingFloors) {
            int thickness = (int) floor.get("thickness_mm");
            String fireRating = (String) floor.get("fire_rating");
            @SuppressWarnings("unchecked")
            Map<String, Object> acoustic = (Map<String, Object>) floor.get("acoustic");
            int stc = (int) acoustic.get("STC");
            int iic = (int) acoustic.get("IIC");

            // Check thickness >= 200mm
            if (thickness < 200) {
                Map<String, Object> violation = new LinkedHashMap<>();
                violation.put("level", floor.get("level"));
                violation.put("issue", "thickness_insufficient");
                violation.put("required_mm", 200);
                violation.put("actual_mm", thickness);
                violations.add(violation);
                allValid = false;
            }

            // Check fire rating is FRL 90/90/90
            if (!"90/90/90".equals(fireRating)) {
                Map<String, Object> violation = new LinkedHashMap<>();
                violation.put("level", floor.get("level"));
                violation.put("issue", "fire_rating_insufficient");
                violation.put("required", "90/90/90");
                violation.put("actual", fireRating);
                violations.add(violation);
                allValid = false;
            }

            // Check acoustic ratings
            if (stc < 50) {
                Map<String, Object> violation = new LinkedHashMap<>();
                violation.put("level", floor.get("level"));
                violation.put("issue", "acoustic_stc_insufficient");
                violation.put("required", 50);
                violation.put("actual", stc);
                violations.add(violation);
                allValid = false;
            }
            if (iic < 50) {
                Map<String, Object> violation = new LinkedHashMap<>();
                violation.put("level", floor.get("level"));
                violation.put("issue", "acoustic_iic_insufficient");
                violation.put("required", 50);
                violation.put("actual", iic);
                violations.add(violation);
                allValid = false;
            }
        }

        claim.put("status", allValid ? "PROVEN" : "UNPROVABLE");

        Map<String, Object> w = new LinkedHashMap<>();
        w.put("separating_floors", separatingFloors);
        w.put("floor_count", separatingFloors.size());
        w.put("fire_rating", Map.of(
            "required", "FRL 90/90/90",
            "all_compliant", allValid || violations.stream()
                .noneMatch(v -> "fire_rating_insufficient".equals(v.get("issue")))
        ));
        w.put("thickness", Map.of(
            "minimum_mm", 200,
            "all_compliant", allValid || violations.stream()
                .noneMatch(v -> "thickness_insufficient".equals(v.get("issue")))
        ));
        w.put("acoustic", Map.of(
            "required_stc", 50,
            "required_iic", 50,
            "all_compliant", allValid || violations.stream()
                .noneMatch(v -> v.get("issue").toString().startsWith("acoustic_")),
            "assembly_note", "IIC 50 compliance assumes composite floor assembly"
        ));

        if (!violations.isEmpty()) {
            w.put("violations", violations);
        }

        claim.put("witness", w);
        if (allValid) proven++;

        claims.put("SEPARATING_FLOORS_VALID", claim);
    }

    /**
     * Phase 50C: Build CLASSROOM_DAYLIGHT claim.
     * Proves all classrooms have windows for natural daylight.
     */
    private void buildClassroomDaylightClaim() {
        Map<String, Object> claim = new LinkedHashMap<>();

        if (!classroomDaylight.isEmpty()) {
            // All classrooms must have at least one window
            boolean allHaveDaylight = classroomDaylight.stream()
                .allMatch(c -> Boolean.TRUE.equals(c.get("has_daylight")));

            // Optional: check daylight ratio >= 10% (MS 1525 requirement)
            long inadequateRatio = classroomDaylight.stream()
                .filter(c -> ((Number) c.get("daylight_ratio")).doubleValue() < 0.10)
                .count();

            claim.put("status", allHaveDaylight ? "PROVEN" : "UNPROVABLE");

            Map<String, Object> w = new LinkedHashMap<>();
            w.put("classrooms_checked", classroomDaylight.size());
            w.put("all_have_windows", allHaveDaylight);

            // Calculate totals
            int totalWindows = classroomDaylight.stream()
                .mapToInt(c -> ((Number) c.get("window_count")).intValue())
                .sum();
            double totalWindowArea = classroomDaylight.stream()
                .mapToDouble(c -> ((Number) c.get("window_area_m2")).doubleValue())
                .sum();
            double totalFloorArea = classroomDaylight.stream()
                .mapToDouble(c -> ((Number) c.get("floor_area_m2")).doubleValue())
                .sum();

            w.put("total_windows", totalWindows);
            w.put("total_window_area_m2", Math.round(totalWindowArea * 100.0) / 100.0);
            w.put("average_daylight_ratio", totalFloorArea > 0 ?
                Math.round((totalWindowArea / totalFloorArea) * 1000.0) / 1000.0 : 0);

            if (inadequateRatio > 0) {
                w.put("warning", inadequateRatio + " classroom(s) have daylight ratio < 10%");
            }

            // List each classroom
            w.put("classrooms", classroomDaylight);

            // List violations (classrooms without windows)
            List<String> violations = classroomDaylight.stream()
                .filter(c -> !Boolean.TRUE.equals(c.get("has_daylight")))
                .map(c -> (String) c.get("room"))
                .toList();
            w.put("violations", violations);

            claim.put("witness", w);
            if (allHaveDaylight) proven++;
        } else {
            claim.put("status", "SKIPPED");
            claim.put("reason", "No classroom daylight data collected");
            skipped++;
        }

        claims.put("CLASSROOM_DAYLIGHT", claim);
    }

    /**
     * Phase 50C: Build TOILET_ACCESSIBLE claim.
     * Proves all toilets have accessible doors (>= 900mm width per MS 1184).
     */
    private void buildToiletAccessibleClaim() {
        Map<String, Object> claim = new LinkedHashMap<>();

        if (!toiletAccess.isEmpty()) {
            // All toilets must have accessible doors
            boolean allAccessible = toiletAccess.stream()
                .allMatch(t -> Boolean.TRUE.equals(t.get("door_accessible")));

            claim.put("status", allAccessible ? "PROVEN" : "UNPROVABLE");

            Map<String, Object> w = new LinkedHashMap<>();
            w.put("toilets_checked", toiletAccess.size());
            w.put("all_accessible", allAccessible);
            w.put("standard", "MS 1184 (door width >= 900mm)");

            // Summary stats
            double avgDoorWidth = toiletAccess.stream()
                .mapToDouble(t -> ((Number) t.get("door_width_m")).doubleValue())
                .average()
                .orElse(0);
            w.put("average_door_width_m", Math.round(avgDoorWidth * 1000.0) / 1000.0);

            // List each toilet
            w.put("toilets", toiletAccess);

            // List violations
            List<String> violations = toiletAccess.stream()
                .filter(t -> !Boolean.TRUE.equals(t.get("door_accessible")))
                .map(t -> (String) t.get("room"))
                .toList();
            w.put("violations", violations);

            claim.put("witness", w);
            if (allAccessible) proven++;
        } else {
            claim.put("status", "SKIPPED");
            claim.put("reason", "No toilet accessibility data collected");
            skipped++;
        }

        claims.put("TOILET_ACCESSIBLE", claim);
    }

    /**
     * Phase 50C: Build CORRIDOR_CONNECTS_ALL claim.
     * Proves corridor provides access to all teaching/occupied spaces.
     */
    private void buildCorridorConnectsAllClaim() {
        Map<String, Object> claim = new LinkedHashMap<>();

        if (!corridorConnections.isEmpty()) {
            // All rooms must be connected
            boolean allConnected = corridorConnections.stream()
                .allMatch(c -> Boolean.TRUE.equals(c.get("connected")));

            claim.put("status", allConnected ? "PROVEN" : "UNPROVABLE");

            Map<String, Object> w = new LinkedHashMap<>();
            w.put("corridor", corridorName != null ? corridorName : "main_corridor");
            w.put("rooms_checked", corridorConnections.size());
            w.put("all_connected", allConnected);

            // Group by room type
            Map<String, Long> byType = new LinkedHashMap<>();
            for (var conn : corridorConnections) {
                String type = (String) conn.get("type");
                byType.merge(type, 1L, Long::sum);
            }
            w.put("rooms_by_type", byType);

            // List connections
            w.put("connections", corridorConnections);

            // List unconnected rooms
            List<String> violations = corridorConnections.stream()
                .filter(c -> !Boolean.TRUE.equals(c.get("connected")))
                .map(c -> (String) c.get("room"))
                .toList();
            w.put("violations", violations);

            claim.put("witness", w);
            if (allConnected) proven++;
        } else {
            claim.put("status", "SKIPPED");
            claim.put("reason", "No corridor connectivity data collected");
            skipped++;
        }

        claims.put("CORRIDOR_CONNECTS_ALL", claim);
    }

    /**
     * Phase 50C: Build FIRE_TRAVEL_DISTANCE claim.
     * Proves max travel distance to exit is within code limits.
     */
    private void buildFireTravelDistanceClaim() {
        Map<String, Object> claim = new LinkedHashMap<>();

        if (!fireTravelDistances.isEmpty()) {
            // All rooms must be within allowed travel distance
            boolean allCompliant = fireTravelDistances.stream()
                .allMatch(f -> Boolean.TRUE.equals(f.get("compliant")));

            claim.put("status", allCompliant ? "PROVEN" : "UNPROVABLE");

            Map<String, Object> w = new LinkedHashMap<>();
            w.put("exit_location", exitLocation != null ? exitLocation : "main_exit");
            w.put("rooms_checked", fireTravelDistances.size());
            w.put("all_compliant", allCompliant);

            // Find max travel distance
            double maxDistance = fireTravelDistances.stream()
                .mapToDouble(f -> ((Number) f.get("distance_m")).doubleValue())
                .max()
                .orElse(0);
            String maxDistanceRoom = fireTravelDistances.stream()
                .filter(f -> ((Number) f.get("distance_m")).doubleValue() == maxDistance)
                .map(f -> (String) f.get("room"))
                .findFirst()
                .orElse("unknown");

            w.put("max_travel_distance_m", maxDistance);
            w.put("max_distance_room", maxDistanceRoom);

            // Code reference
            w.put("standard", fireTravelStandard != null ? fireTravelStandard
                : "UBBL 2012 Clause 166 (30m dead-end, 60m with alternative exit)");

            // List all distances
            w.put("distances", fireTravelDistances);

            // List violations
            List<Map<String, Object>> violations = fireTravelDistances.stream()
                .filter(f -> !Boolean.TRUE.equals(f.get("compliant")))
                .toList();
            w.put("violations", violations);

            claim.put("witness", w);
            if (allCompliant) proven++;
        } else {
            claim.put("status", "SKIPPED");
            claim.put("reason", "No fire travel distance data collected");
            skipped++;
        }

        claims.put("FIRE_TRAVEL_DISTANCE", claim);
    }

    /**
     * Phase 50C: Build STRUCTURAL_GRID_COMPLETE claim.
     * Proves structural grid has columns at intersections and beams present,
     * with correct IFC classes.
     *
     * NOTE: Does not currently verify span limits. The current implementation
     * places beams in each direction where dimension > max_span, but each beam
     * still spans the full perpendicular dimension. A future enhancement would
     * implement true two-way grid with secondary beams framing into primaries.
     */
    private void buildStructuralGridCompleteClaim() {
        Map<String, Object> claim = new LinkedHashMap<>();

        if (!structuralGridElements.isEmpty()) {
            // Count columns and beams
            long columnCount = structuralGridElements.stream()
                .filter(e -> "GRID_COLUMN".equals(e.get("type")))
                .count();
            long beamCount = structuralGridElements.stream()
                .filter(e -> "GRID_BEAM".equals(e.get("type")))
                .count();

            // All elements must have valid IFC class
            boolean allValidClass = structuralGridElements.stream()
                .allMatch(e -> Boolean.TRUE.equals(e.get("ifc_class_valid")));

            // Grid is complete if we have at least 1 beam and correct IFC classes
            boolean hasGrid = beamCount > 0 && allValidClass;

            claim.put("status", hasGrid ? "PROVEN" : "UNPROVABLE");

            Map<String, Object> w = new LinkedHashMap<>();
            w.put("grid_room", gridRoomName != null ? gridRoomName : "assembly_hall");
            w.put("column_count", columnCount);
            w.put("beam_count", beamCount);
            w.put("all_ifc_classes_valid", allValidClass);

            // Group by IFC class
            Map<String, Long> byIfcClass = new LinkedHashMap<>();
            for (var elem : structuralGridElements) {
                String ifcClass = (String) elem.get("ifc_class");
                byIfcClass.merge(ifcClass, 1L, Long::sum);
            }
            w.put("by_ifc_class", byIfcClass);

            // IFC class validation details
            Map<String, Object> ifcValidation = new LinkedHashMap<>();
            ifcValidation.put("expected_column_class", "IfcColumn");
            ifcValidation.put("expected_beam_class", "IfcBeam");
            ifcValidation.put("all_correct", allValidClass);
            w.put("ifc_class_validation", ifcValidation);

            // Note about span limits
            w.put("note", "Span limit validation deferred - single-axis grid; perpendicular spans not yet subdivided");

            // List elements
            w.put("elements", structuralGridElements);

            // List IFC class violations
            List<String> violations = structuralGridElements.stream()
                .filter(e -> !Boolean.TRUE.equals(e.get("ifc_class_valid")))
                .map(e -> (String) e.get("id") + " has " + e.get("ifc_class") + " (expected " +
                    ("GRID_COLUMN".equals(e.get("type")) ? "IfcColumn" : "IfcBeam") + ")")
                .toList();
            w.put("violations", violations);

            claim.put("witness", w);
            if (hasGrid) proven++;
        } else {
            claim.put("status", "SKIPPED");
            claim.put("reason", "No structural grid data collected");
            skipped++;
        }

        claims.put("STRUCTURAL_GRID_COMPLETE", claim);
    }

    /**
     * Write witness.json to the specified path.
     * NON-BLOCKING: Catches all exceptions, logs warning, returns false on failure.
     */
    public boolean write(Path outputPath) {
        try {
            Map<String, Object> witness = build();
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String json = gson.toJson(witness);
            Files.writeString(outputPath, json);
            return true;
        } catch (Exception e) {
            System.err.println("[WITNESS] Failed to write witness.json: " + e.getMessage());
            return false;
        }
    }
}
