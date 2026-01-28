package com.bim.compiler.dsl;

import com.bim.compiler.builder.OpeningSpec;
import com.bim.compiler.builder.WallSpec;
import com.bim.compiler.geometry.BoundingBox;
import com.bim.compiler.geometry.Point3D;
import com.bim.compiler.topology.BIMObjectType;
import com.bim.compiler.topology.WallThickness;

import java.util.*;

/**
 * Compiles room polygons + doors/windows into construction elements.
 *
 * Algorithm:
 *   1. For each room edge:
 *      - If shared with another room → interior wall (150mm)
 *      - If on boundary → exterior wall (250mm)
 *   2. Deduplicate shared walls (only create once)
 *   3. For each door → OpeningSpec at wall midpoint
 *   4. For each window → OpeningSpec on exterior wall
 */
public class RoomCompiler {

    // Wall thicknesses (CRUDE: fixed values)
    private static final WallThickness INTERIOR_WALL = WallThickness.MM_150;
    private static final WallThickness EXTERIOR_WALL = WallThickness.MM_250;

    // Opening sizes (CRUDE: fixed values)
    private static final double DOOR_WIDTH = 0.9;
    private static final double DOOR_HEIGHT = 2.1;
    private static final double WINDOW_WIDTH = 1.2;
    private static final double WINDOW_HEIGHT = 1.2;
    private static final double WINDOW_SILL = 0.9;

    /**
     * Compile layout to construction spec.
     */
    public static ConstructionSpec compile(
            Map<String, RoomPolygon> layout,
            StoreyDefinition storey) {

        RoomCompiler compiler = new RoomCompiler(layout, storey);
        return compiler.compileInternal();
    }

    private final Map<String, RoomPolygon> layout;
    private final StoreyDefinition storey;
    private final Map<String, WallSpec> walls = new LinkedHashMap<>();
    private final List<ConstructionSpec.SpaceSpec> spaces = new ArrayList<>();
    private final List<ConstructionSpec.SprinklerGridSpec> sprinklerGrids = new ArrayList<>();

    // Track which room pairs already have a shared wall
    private final Set<String> processedWallPairs = new HashSet<>();

    private RoomCompiler(Map<String, RoomPolygon> layout, StoreyDefinition storey) {
        this.layout = layout;
        this.storey = storey;
    }

    private ConstructionSpec compileInternal() {
        // Calculate building bounds for exterior wall detection
        double minX = layout.values().stream().mapToDouble(RoomPolygon::minX).min().orElse(0);
        double maxX = layout.values().stream().mapToDouble(RoomPolygon::maxX).max().orElse(0);
        double minY = layout.values().stream().mapToDouble(RoomPolygon::minY).min().orElse(0);
        double maxY = layout.values().stream().mapToDouble(RoomPolygon::maxY).max().orElse(0);

        // Process each room
        for (RoomDefinition roomDef : storey.rooms()) {
            RoomPolygon room = layout.get(roomDef.name());
            if (room == null) continue;

            // Create space spec
            spaces.add(new ConstructionSpec.SpaceSpec(
                room.roomName(),
                room.roomType(),
                room.minX(),
                room.maxX(),
                room.minY(),
                room.maxY(),
                storey.height()
            ));

            // Process each wall direction
            for (Direction dir : Direction.values()) {
                processWall(roomDef, room, dir, minX, maxX, minY, maxY);
            }

            // Process sprinklers if defined
            if (roomDef.sprinklers() != null) {
                processSprinklers(roomDef, room);
            }
        }

        return new ConstructionSpec(
            storey.name(),
            storey.height(),
            List.copyOf(walls.values()),
            List.copyOf(spaces),
            List.copyOf(sprinklerGrids)
        );
    }

    private void processWall(RoomDefinition roomDef, RoomPolygon room, Direction dir,
                             double bldgMinX, double bldgMaxX, double bldgMinY, double bldgMaxY) {

        // Find if there's an adjacent room
        RoomPolygon adjacent = findAdjacentRoom(room, dir);

        // Determine if exterior
        boolean isExterior = isExteriorWall(room, dir, bldgMinX, bldgMaxX, bldgMinY, bldgMaxY);

        // Wall key for deduplication
        String wallKey = getWallKey(room.roomName(), adjacent != null ? adjacent.roomName() : "exterior", dir);
        if (processedWallPairs.contains(wallKey)) {
            return; // Already processed this wall
        }
        processedWallPairs.add(wallKey);

        // Get wall geometry
        WallThickness thickness = isExterior ? EXTERIOR_WALL : INTERIOR_WALL;
        Point3D start, end;

        switch (dir) {
            case NORTH -> {
                start = new Point3D(room.minX(), room.maxY(), 0);
                end = new Point3D(room.maxX(), room.maxY(), 0);
            }
            case SOUTH -> {
                start = new Point3D(room.minX(), room.minY(), 0);
                end = new Point3D(room.maxX(), room.minY(), 0);
            }
            case EAST -> {
                start = new Point3D(room.maxX(), room.minY(), 0);
                end = new Point3D(room.maxX(), room.maxY(), 0);
            }
            case WEST -> {
                start = new Point3D(room.minX(), room.minY(), 0);
                end = new Point3D(room.minX(), room.maxY(), 0);
            }
            default -> {
                return;
            }
        }

        // Collect openings for this wall
        List<OpeningSpec> openings = new ArrayList<>();

        // Process doors
        for (DoorDefinition door : roomDef.doors()) {
            if (door.direction() == dir) {
                // Door at midpoint of wall
                double wallLength = start.horizontalDistanceTo(end);
                double offset = wallLength / 2; // Center of wall

                openings.add(new OpeningSpec(
                    offset,
                    0.0, // Floor level
                    DOOR_WIDTH,
                    DOOR_HEIGHT,
                    BIMObjectType.IFC_DOOR
                ));
            }
        }

        // Process windows (only on exterior walls)
        if (isExterior) {
            for (WindowDefinition window : roomDef.windows()) {
                if (window.direction() == dir) {
                    double wallLength = start.horizontalDistanceTo(end);
                    double offset = wallLength / 2;

                    openings.add(new OpeningSpec(
                        offset,
                        WINDOW_SILL,
                        WINDOW_WIDTH,
                        WINDOW_HEIGHT,
                        BIMObjectType.IFC_WINDOW
                    ));
                }
            }
        }

        // Create wall spec
        WallSpec wall = new WallSpec(start, end, thickness, storey.height(), openings);
        walls.put(wallKey, wall);
    }

    private RoomPolygon findAdjacentRoom(RoomPolygon room, Direction dir) {
        for (RoomPolygon other : layout.values()) {
            if (other.roomName().equals(room.roomName())) continue;

            Direction toOther = room.directionTo(other);
            if (toOther == dir) {
                return other;
            }
        }
        return null;
    }

    private boolean isExteriorWall(RoomPolygon room, Direction dir,
                                   double minX, double maxX, double minY, double maxY) {
        double tolerance = 0.01;
        return switch (dir) {
            case NORTH -> Math.abs(room.maxY() - maxY) < tolerance;
            case SOUTH -> Math.abs(room.minY() - minY) < tolerance;
            case EAST -> Math.abs(room.maxX() - maxX) < tolerance;
            case WEST -> Math.abs(room.minX() - minX) < tolerance;
        };
    }

    private String getWallKey(String room1, String room2, Direction dir) {
        // Normalize key so shared walls only appear once
        String r1 = room1.compareTo(room2) < 0 ? room1 : room2;
        String r2 = room1.compareTo(room2) < 0 ? room2 : room1;
        return r1 + "-" + r2 + "-" + dir;
    }

    /**
     * Process sprinklers for a room.
     * Uses ceiling attachment (pendant sprinklers).
     * Attachment Z = storey height (ceiling level).
     */
    private void processSprinklers(RoomDefinition roomDef, RoomPolygon room) {
        SprinklerDefinition sprinklers = roomDef.sprinklers();

        // Room bounding box for grid coverage
        BoundingBox area = new BoundingBox(
            room.minX(), room.maxX(),
            room.minY(), room.maxY(),
            0.0, storey.height()
        );

        // Attachment Z = ceiling height
        // Pendant sprinklers mount at ceiling level
        double attachmentZ = storey.height();

        sprinklerGrids.add(new ConstructionSpec.SprinklerGridSpec(
            room.roomName(),
            area,
            sprinklers.gridSpacing(),
            attachmentZ
        ));
    }
}
