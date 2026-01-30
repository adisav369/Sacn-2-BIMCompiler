package com.bim.compiler.library;

import com.bim.compiler.geometry.Point3D;
import com.bim.compiler.geometry.BoundingBox;
import com.bim.compiler.library.ComponentLibrary.*;
import com.bim.compiler.dsl.SpaceTypeRegistry.ElectricalConfig;
import com.bim.compiler.util.OutlierLogger;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Phase 33: Places electrical elements (lights, outlets, switches) from library.
 *
 * Placement rules from IRC 2021 / UBBL 1984:
 * - Light: ceiling-mounted, centered or grid-based
 * - Outlet: 300mm (12") above floor, wall-mounted
 * - Switch: 1200mm (48") above floor, near door
 *
 * Room type → electrical (from MEPConfig):
 * - BEDROOM: 1 light, 2 outlets, 1 switch
 * - BATHROOM: 1 light, 1 outlet (shaver), 1 switch
 * - KITCHEN: 2 lights, 4 outlets, 1 switch
 * - LIVING: 1 light, 3 outlets, 1 switch
 */
public class ElectricalPlacer {

    private final ComponentLibrary library;

    // Standard heights (meters)
    private static final double OUTLET_HEIGHT = 0.3;      // 300mm (12") above floor
    private static final double SWITCH_HEIGHT = 1.2;      // 1200mm (48") above floor
    private static final double WALL_OFFSET = 0.05;       // 50mm from wall face
    // Note: No ceiling offset - surface-mounted lights attach directly to ceiling

    // Standard element dimensions (for parametric fallback)
    private static final double OUTLET_WIDTH = 0.085;     // 85mm standard outlet
    private static final double OUTLET_HEIGHT_DIM = 0.085;
    private static final double OUTLET_DEPTH = 0.035;

    private static final double SWITCH_WIDTH = 0.085;     // 85mm standard switch
    private static final double SWITCH_HEIGHT_DIM = 0.085;
    private static final double SWITCH_DEPTH = 0.035;

    public ElectricalPlacer(ComponentLibrary library) {
        this.library = library;
    }

    /**
     * Place electrical elements for a room based on MEPConfig.
     *
     * @param roomMinX Room bounding box
     * @param roomMinY Room bounding box
     * @param roomMaxX Room bounding box
     * @param roomMaxY Room bounding box
     * @param floorZ Floor elevation
     * @param ceilingZ Ceiling elevation
     * @param config Electrical configuration from SpaceTypeRegistry
     * @param roomName Room name for logging
     * @return List of electrical instances
     */
    public List<ElectricalInstance> placeElectricalElements(
            double roomMinX, double roomMinY,
            double roomMaxX, double roomMaxY,
            double floorZ, double ceilingZ,
            ElectricalConfig config,
            String roomName) throws SQLException {

        List<ElectricalInstance> elements = new ArrayList<>();

        double roomWidth = roomMaxX - roomMinX;
        double roomDepth = roomMaxY - roomMinY;
        String roomContext = String.format("\"%s\" (%.1fm x %.1fm)", roomName, roomWidth, roomDepth);

        // 1. Place lights on ceiling
        int lightCount = config.lightPoints();
        if (lightCount > 0) {
            elements.addAll(placeLights(
                roomMinX, roomMinY, roomMaxX, roomMaxY,
                ceilingZ, lightCount, roomContext
            ));
        }

        // 2. Place power outlets on walls
        int outletCount = config.powerPoints();
        if (outletCount > 0) {
            elements.addAll(placeOutlets(
                roomMinX, roomMinY, roomMaxX, roomMaxY,
                floorZ, outletCount, roomContext
            ));
        }

        // 3. Place switches near entry (south wall assumed)
        int switchCount = config.switchPoints();
        if (switchCount > 0) {
            elements.addAll(placeSwitches(
                roomMinX, roomMinY, roomMaxX, roomMaxY,
                floorZ, switchCount, roomContext
            ));
        }

        return elements;
    }

    /**
     * Place light fixtures on ceiling.
     * Uses grid pattern for multiple lights.
     */
    private List<ElectricalInstance> placeLights(
            double roomMinX, double roomMinY,
            double roomMaxX, double roomMaxY,
            double ceilingZ,
            int count,
            String roomContext) throws SQLException {

        List<ElectricalInstance> lights = new ArrayList<>();
        double roomWidth = roomMaxX - roomMinX;
        double roomDepth = roomMaxY - roomMinY;

        // Try to find appropriate light fixture from library
        ComponentDefinition lightDef = library.getComponentWithFallback("14W_Surface_LED", roomContext);
        if (lightDef == null) {
            lightDef = library.getComponentWithFallback("28W_Surface_LED", roomContext);
        }

        double lightWidth, lightDepth, lightHeight;
        String geometryHash = null;

        if (lightDef != null) {
            lightWidth = lightDef.localBounds().width();
            lightDepth = lightDef.localBounds().depth();
            lightHeight = lightDef.localBounds().height();
            geometryHash = lightDef.geometryHash();
        } else {
            // Parametric fallback: 600x600mm recessed panel
            lightWidth = 0.6;
            lightDepth = 0.6;
            lightHeight = 0.1;
        }

        // Check if lights fit in room
        if (lightWidth > roomWidth || lightDepth > roomDepth) {
            OutlierLogger.logGeometryImpossible(
                "light",
                roomContext,
                "light too large for room",
                String.format("Light size %.2fm x %.2fm doesn't fit room %.2fm x %.2fm",
                    lightWidth, lightDepth, roomWidth, roomDepth));
            return lights;
        }

        // Place lights in grid pattern
        if (count == 1) {
            // Single light: center of room
            double lightX = (roomMinX + roomMaxX) / 2;
            double lightY = (roomMinY + roomMaxY) / 2;
            double lightZ = ceilingZ - lightHeight;  // Surface-mounted: top touches ceiling

            lights.add(new ElectricalInstance(
                ElectricalType.LIGHT,
                new Point3D(lightX, lightY, lightZ),
                0,  // rotation
                lightWidth, lightDepth, lightHeight,
                geometryHash,
                lightDef != null ? lightDef.name() : "RECESSED_PANEL"
            ));
        } else if (count == 2) {
            // Two lights: spaced along longer axis
            double spacing = Math.max(roomWidth, roomDepth) / 3;
            double centerX = (roomMinX + roomMaxX) / 2;
            double centerY = (roomMinY + roomMaxY) / 2;
            double lightZ = ceilingZ - lightHeight;  // Surface-mounted: top touches ceiling

            if (roomWidth >= roomDepth) {
                // Space along X
                lights.add(new ElectricalInstance(
                    ElectricalType.LIGHT,
                    new Point3D(centerX - spacing/2, centerY, lightZ),
                    0, lightWidth, lightDepth, lightHeight, geometryHash,
                    lightDef != null ? lightDef.name() : "RECESSED_PANEL"
                ));
                lights.add(new ElectricalInstance(
                    ElectricalType.LIGHT,
                    new Point3D(centerX + spacing/2, centerY, lightZ),
                    0, lightWidth, lightDepth, lightHeight, geometryHash,
                    lightDef != null ? lightDef.name() : "RECESSED_PANEL"
                ));
            } else {
                // Space along Y
                lights.add(new ElectricalInstance(
                    ElectricalType.LIGHT,
                    new Point3D(centerX, centerY - spacing/2, lightZ),
                    0, lightWidth, lightDepth, lightHeight, geometryHash,
                    lightDef != null ? lightDef.name() : "RECESSED_PANEL"
                ));
                lights.add(new ElectricalInstance(
                    ElectricalType.LIGHT,
                    new Point3D(centerX, centerY + spacing/2, lightZ),
                    0, lightWidth, lightDepth, lightHeight, geometryHash,
                    lightDef != null ? lightDef.name() : "RECESSED_PANEL"
                ));
            }
        } else {
            // Multiple lights: grid pattern
            int cols = (int) Math.ceil(Math.sqrt(count * roomWidth / roomDepth));
            int rows = (int) Math.ceil((double) count / cols);
            double spacingX = roomWidth / (cols + 1);
            double spacingY = roomDepth / (rows + 1);
            double lightZ = ceilingZ - lightHeight;  // Surface-mounted: top touches ceiling

            int placed = 0;
            for (int row = 0; row < rows && placed < count; row++) {
                for (int col = 0; col < cols && placed < count; col++) {
                    double lightX = roomMinX + spacingX * (col + 1);
                    double lightY = roomMinY + spacingY * (row + 1);

                    lights.add(new ElectricalInstance(
                        ElectricalType.LIGHT,
                        new Point3D(lightX, lightY, lightZ),
                        0, lightWidth, lightDepth, lightHeight, geometryHash,
                        lightDef != null ? lightDef.name() : "RECESSED_PANEL"
                    ));
                    placed++;
                }
            }
        }

        return lights;
    }

    /**
     * Place power outlets on walls.
     * Distributes evenly around perimeter.
     */
    private List<ElectricalInstance> placeOutlets(
            double roomMinX, double roomMinY,
            double roomMaxX, double roomMaxY,
            double floorZ,
            int count,
            String roomContext) {

        List<ElectricalInstance> outlets = new ArrayList<>();
        double roomWidth = roomMaxX - roomMinX;
        double roomDepth = roomMaxY - roomMinY;
        double outletZ = floorZ + OUTLET_HEIGHT;

        // Distribute outlets around walls
        // Wall order: South (entry), East, North, West
        int[] wallDistribution = distributeToWalls(count, 4);

        // South wall (Y = minY)
        for (int i = 0; i < wallDistribution[0]; i++) {
            double spacing = roomWidth / (wallDistribution[0] + 1);
            double x = roomMinX + spacing * (i + 1);
            double y = roomMinY + WALL_OFFSET;

            outlets.add(new ElectricalInstance(
                ElectricalType.OUTLET,
                new Point3D(x, y, outletZ),
                0,  // facing into room (north)
                OUTLET_WIDTH, OUTLET_DEPTH, OUTLET_HEIGHT_DIM,
                null, "POWER_OUTLET"
            ));
        }

        // East wall (X = maxX)
        for (int i = 0; i < wallDistribution[1]; i++) {
            double spacing = roomDepth / (wallDistribution[1] + 1);
            double x = roomMaxX - WALL_OFFSET;
            double y = roomMinY + spacing * (i + 1);

            outlets.add(new ElectricalInstance(
                ElectricalType.OUTLET,
                new Point3D(x, y, outletZ),
                -Math.PI / 2,  // facing into room (west)
                OUTLET_WIDTH, OUTLET_DEPTH, OUTLET_HEIGHT_DIM,
                null, "POWER_OUTLET"
            ));
        }

        // North wall (Y = maxY)
        for (int i = 0; i < wallDistribution[2]; i++) {
            double spacing = roomWidth / (wallDistribution[2] + 1);
            double x = roomMinX + spacing * (i + 1);
            double y = roomMaxY - WALL_OFFSET;

            outlets.add(new ElectricalInstance(
                ElectricalType.OUTLET,
                new Point3D(x, y, outletZ),
                Math.PI,  // facing into room (south)
                OUTLET_WIDTH, OUTLET_DEPTH, OUTLET_HEIGHT_DIM,
                null, "POWER_OUTLET"
            ));
        }

        // West wall (X = minX)
        for (int i = 0; i < wallDistribution[3]; i++) {
            double spacing = roomDepth / (wallDistribution[3] + 1);
            double x = roomMinX + WALL_OFFSET;
            double y = roomMinY + spacing * (i + 1);

            outlets.add(new ElectricalInstance(
                ElectricalType.OUTLET,
                new Point3D(x, y, outletZ),
                Math.PI / 2,  // facing into room (east)
                OUTLET_WIDTH, OUTLET_DEPTH, OUTLET_HEIGHT_DIM,
                null, "POWER_OUTLET"
            ));
        }

        return outlets;
    }

    /**
     * Place switches near entry (assumed on south wall).
     */
    private List<ElectricalInstance> placeSwitches(
            double roomMinX, double roomMinY,
            double roomMaxX, double roomMaxY,
            double floorZ,
            int count,
            String roomContext) {

        List<ElectricalInstance> switches = new ArrayList<>();
        double roomWidth = roomMaxX - roomMinX;
        double switchZ = floorZ + SWITCH_HEIGHT;

        // Switches typically near door (south wall, left side)
        double baseX = roomMinX + 0.15;  // 150mm from corner
        double y = roomMinY + WALL_OFFSET;

        for (int i = 0; i < count; i++) {
            double x = baseX + i * 0.1;  // 100mm spacing between switches
            if (x > roomMaxX - 0.15) {
                // Won't fit on south wall, skip
                OutlierLogger.logGeometryImpossible(
                    "switch",
                    roomContext,
                    "too many switches for wall width",
                    String.format("Need %.2fm for %d switches, wall is %.2fm",
                        baseX + count * 0.1, count, roomWidth));
                break;
            }

            switches.add(new ElectricalInstance(
                ElectricalType.SWITCH,
                new Point3D(x, y, switchZ),
                0,  // facing into room
                SWITCH_WIDTH, SWITCH_DEPTH, SWITCH_HEIGHT_DIM,
                null, "LIGHT_SWITCH"
            ));
        }

        return switches;
    }

    /**
     * Distribute count evenly across n walls.
     */
    private int[] distributeToWalls(int count, int walls) {
        int[] distribution = new int[walls];
        int base = count / walls;
        int remainder = count % walls;

        for (int i = 0; i < walls; i++) {
            distribution[i] = base + (i < remainder ? 1 : 0);
        }
        return distribution;
    }

    // =========================================================================
    // Output types
    // =========================================================================

    public enum ElectricalType {
        LIGHT,
        OUTLET,
        SWITCH
    }

    public record ElectricalInstance(
        ElectricalType type,
        Point3D worldPosition,
        double rotation,      // Radians around Z axis
        double width,
        double depth,
        double height,
        String geometryHash,  // null for parametric
        String name
    ) {}
}
