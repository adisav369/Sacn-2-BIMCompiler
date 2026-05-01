/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.library;

import com.bim.compiler.geometry.Point3D;
import com.bim.compiler.geometry.BoundingBox;
import com.bim.compiler.library.ComponentLibrary.*;
import com.bim.compiler.dsl.SpaceTypeRegistry.ElectricalConfig;
import com.bim.compiler.system.MEPSystem;
import com.bim.compiler.system.SystemType;
import com.bim.compiler.system.SystemNode;
import com.bim.compiler.system.SystemEdge;
import com.bim.compiler.system.EdgeType;
import com.bim.compiler.system.NodeRole;
import com.bim.compiler.util.OutlierLogger;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    // Phase 44: Column clearance for light placement
    // Total clearance = light half-size (0.3m) + minimum gap (0.05m) = 0.35m
    private static final double COLUMN_CLEARANCE = 0.35;

    /**
     * Column zone for clash avoidance.
     * Represents a column's XY footprint that lights should avoid.
     */
    public record ColumnZone(
        double x,           // Column center X
        double y,           // Column center Y
        double halfWidth,   // Half of column width (X)
        double halfDepth    // Half of column depth (Y)
    ) {
        /**
         * Check if a point is within the exclusion zone (column footprint + clearance).
         */
        public boolean contains(double px, double py, double clearance) {
            return px >= (x - halfWidth - clearance) && px <= (x + halfWidth + clearance) &&
                   py >= (y - halfDepth - clearance) && py <= (y + halfDepth + clearance);
        }

        /**
         * Get the offset direction and distance to move a point out of the exclusion zone.
         * Returns {dx, dy} to move the point, or {0, 0} if not in zone.
         */
        public double[] getOffset(double px, double py, double clearance) {
            if (!contains(px, py, clearance)) {
                return new double[]{0, 0};
            }

            // Calculate distances to each edge of the exclusion zone
            double distLeft = px - (x - halfWidth - clearance);
            double distRight = (x + halfWidth + clearance) - px;
            double distBottom = py - (y - halfDepth - clearance);
            double distTop = (y + halfDepth + clearance) - py;

            // Find minimum distance direction
            double minDist = Math.min(Math.min(distLeft, distRight), Math.min(distBottom, distTop));

            if (minDist == distLeft) {
                return new double[]{-(distLeft + 0.01), 0};  // Move left
            } else if (minDist == distRight) {
                return new double[]{distRight + 0.01, 0};    // Move right
            } else if (minDist == distBottom) {
                return new double[]{0, -(distBottom + 0.01)}; // Move down
            } else {
                return new double[]{0, distTop + 0.01};       // Move up
            }
        }
    }

    // Standard element dimensions (for parametric fallback)
    private static final double OUTLET_WIDTH = 0.085;     // 85mm standard outlet
    private static final double OUTLET_HEIGHT_DIM = 0.085;
    private static final double OUTLET_DEPTH = 0.035;

    private static final double SWITCH_WIDTH = 0.085;     // 85mm standard switch
    private static final double SWITCH_HEIGHT_DIM = 0.085;
    private static final double SWITCH_DEPTH = 0.035;

    // Column zones for clash avoidance (set per room placement)
    private List<ColumnZone> columnZones = new ArrayList<>();

    // Phase 90: Light grid offset to stagger away from sprinkler positions
    private double lightGridOffsetX = 0;
    private double lightGridOffsetY = 0;

    public ElectricalPlacer(ComponentLibrary library) {
        this.library = library;
    }

    /**
     * Set column zones for clash avoidance during light placement.
     * Call this before placeElectricalElements() for each storey.
     */
    public void setColumnZones(List<ColumnZone> zones) {
        this.columnZones = zones != null ? zones : new ArrayList<>();
    }

    /**
     * Phase 90: Set light grid offset to stagger lights away from sprinkler positions.
     * Typically half the sprinkler spacing (e.g., 2.3m / 2 = 1.15m).
     */
    public void setLightGridOffset(double offsetX, double offsetY) {
        this.lightGridOffsetX = offsetX;
        this.lightGridOffsetY = offsetY;
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

        String circuitType = config.circuitType() != null ? config.circuitType() : "general";

        // 1. Place lights on ceiling
        int lightCount = config.lightPoints();
        if (lightCount > 0) {
            elements.addAll(placeLights(
                roomMinX, roomMinY, roomMaxX, roomMaxY,
                ceilingZ, lightCount, roomContext, roomName
            ));
        }

        // 2. Place power outlets on walls
        int outletCount = config.powerPoints();
        if (outletCount > 0) {
            elements.addAll(placeOutlets(
                roomMinX, roomMinY, roomMaxX, roomMaxY,
                floorZ, outletCount, roomContext, circuitType, roomName
            ));
        }

        // 3. Place switches near entry (south wall assumed)
        int switchCount = config.switchPoints();
        if (switchCount > 0) {
            elements.addAll(placeSwitches(
                roomMinX, roomMinY, roomMaxX, roomMaxY,
                floorZ, switchCount, roomContext, roomName
            ));
        }

        return elements;
    }

    /**
     * Place light fixtures on ceiling.
     * Uses grid pattern for multiple lights.
     * Phase 44: Avoids T-junction columns with 150mm clearance.
     */
    private List<ElectricalInstance> placeLights(
            double roomMinX, double roomMinY,
            double roomMaxX, double roomMaxY,
            double ceilingZ,
            int count,
            String roomContext,
            String roomName) throws SQLException {

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
        // Phase 90: Apply offset to stagger away from sprinkler grid
        double offsetX = lightGridOffsetX;
        double offsetY = lightGridOffsetY;
        if (count == 1) {
            // Single light: center of room + offset
            double lightX = (roomMinX + roomMaxX) / 2 + offsetX;
            double lightY = (roomMinY + roomMaxY) / 2 + offsetY;
            // Clamp to room bounds
            lightX = Math.max(roomMinX + 0.35, Math.min(roomMaxX - 0.35, lightX));
            lightY = Math.max(roomMinY + 0.35, Math.min(roomMaxY - 0.35, lightY));
            double lightZ = ceilingZ - lightHeight;  // Surface-mounted: top touches ceiling

            // Phase 44: Avoid column zones
            double[] adjusted = avoidColumnZones(lightX, lightY, roomMinX, roomMinY, roomMaxX, roomMaxY);
            lightX = adjusted[0];
            lightY = adjusted[1];

            lights.add(new ElectricalInstance(
                ElectricalType.LIGHT,
                new Point3D(lightX, lightY, lightZ),
                0,  // rotation
                lightWidth, lightDepth, lightHeight,
                geometryHash,
                lightDef != null ? lightDef.name() : "RECESSED_PANEL",
                "lighting", roomName
            ));
        } else if (count == 2) {
            // Two lights: spaced along longer axis
            double spacing = Math.max(roomWidth, roomDepth) / 3;
            double centerX = (roomMinX + roomMaxX) / 2;
            double centerY = (roomMinY + roomMaxY) / 2;
            double lightZ = ceilingZ - lightHeight;  // Surface-mounted: top touches ceiling

            if (roomWidth >= roomDepth) {
                // Space along X
                double x1 = centerX - spacing/2;
                double x2 = centerX + spacing/2;
                double[] adj1 = avoidColumnZones(x1, centerY, roomMinX, roomMinY, roomMaxX, roomMaxY);
                double[] adj2 = avoidColumnZones(x2, centerY, roomMinX, roomMinY, roomMaxX, roomMaxY);

                lights.add(new ElectricalInstance(
                    ElectricalType.LIGHT,
                    new Point3D(adj1[0], adj1[1], lightZ),
                    0, lightWidth, lightDepth, lightHeight, geometryHash,
                    lightDef != null ? lightDef.name() : "RECESSED_PANEL",
                    "lighting", roomName
                ));
                lights.add(new ElectricalInstance(
                    ElectricalType.LIGHT,
                    new Point3D(adj2[0], adj2[1], lightZ),
                    0, lightWidth, lightDepth, lightHeight, geometryHash,
                    lightDef != null ? lightDef.name() : "RECESSED_PANEL",
                    "lighting", roomName
                ));
            } else {
                // Space along Y
                double y1 = centerY - spacing/2;
                double y2 = centerY + spacing/2;
                double[] adj1 = avoidColumnZones(centerX, y1, roomMinX, roomMinY, roomMaxX, roomMaxY);
                double[] adj2 = avoidColumnZones(centerX, y2, roomMinX, roomMinY, roomMaxX, roomMaxY);

                lights.add(new ElectricalInstance(
                    ElectricalType.LIGHT,
                    new Point3D(adj1[0], adj1[1], lightZ),
                    0, lightWidth, lightDepth, lightHeight, geometryHash,
                    lightDef != null ? lightDef.name() : "RECESSED_PANEL",
                    "lighting", roomName
                ));
                lights.add(new ElectricalInstance(
                    ElectricalType.LIGHT,
                    new Point3D(adj2[0], adj2[1], lightZ),
                    0, lightWidth, lightDepth, lightHeight, geometryHash,
                    lightDef != null ? lightDef.name() : "RECESSED_PANEL",
                    "lighting", roomName
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
                    // Phase 90: Apply sprinkler offset to stagger grid
                    double lightX = roomMinX + spacingX * (col + 1) + offsetX;
                    double lightY = roomMinY + spacingY * (row + 1) + offsetY;
                    // Clamp to room bounds
                    lightX = Math.max(roomMinX + 0.35, Math.min(roomMaxX - 0.35, lightX));
                    lightY = Math.max(roomMinY + 0.35, Math.min(roomMaxY - 0.35, lightY));

                    // Phase 44: Avoid column zones
                    double[] adjusted = avoidColumnZones(lightX, lightY, roomMinX, roomMinY, roomMaxX, roomMaxY);
                    lightX = adjusted[0];
                    lightY = adjusted[1];

                    lights.add(new ElectricalInstance(
                        ElectricalType.LIGHT,
                        new Point3D(lightX, lightY, lightZ),
                        0, lightWidth, lightDepth, lightHeight, geometryHash,
                        lightDef != null ? lightDef.name() : "RECESSED_PANEL",
                        "lighting", roomName
                    ));
                    placed++;
                }
            }
        }

        return lights;
    }

    /**
     * Phase 44: Adjust light position to avoid column exclusion zones.
     *
     * @param x Proposed light X position
     * @param y Proposed light Y position
     * @param roomMinX Room bounds for clamping
     * @param roomMinY Room bounds for clamping
     * @param roomMaxX Room bounds for clamping
     * @param roomMaxY Room bounds for clamping
     * @return Adjusted {x, y} position
     */
    private double[] avoidColumnZones(double x, double y,
                                       double roomMinX, double roomMinY,
                                       double roomMaxX, double roomMaxY) {
        if (columnZones.isEmpty()) {
            return new double[]{x, y};
        }

        double adjustedX = x;
        double adjustedY = y;

        // Check each column zone and apply offset if needed
        for (ColumnZone zone : columnZones) {
            if (zone.contains(adjustedX, adjustedY, COLUMN_CLEARANCE)) {
                double[] offset = zone.getOffset(adjustedX, adjustedY, COLUMN_CLEARANCE);
                adjustedX += offset[0];
                adjustedY += offset[1];
            }
        }

        // Clamp to room bounds with margin (same as clearance to avoid clipping)
        double margin = 0.35;
        adjustedX = Math.max(roomMinX + margin, Math.min(roomMaxX - margin, adjustedX));
        adjustedY = Math.max(roomMinY + margin, Math.min(roomMaxY - margin, adjustedY));

        return new double[]{adjustedX, adjustedY};
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
            String roomContext,
            String circuitType,
            String roomName) {

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
                null, "POWER_OUTLET",
                circuitType, roomName
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
                null, "POWER_OUTLET",
                circuitType, roomName
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
                null, "POWER_OUTLET",
                circuitType, roomName
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
                null, "POWER_OUTLET",
                circuitType, roomName
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
            String roomContext,
            String roomName) {

        List<ElectricalInstance> switches = new ArrayList<>();
        double roomWidth = roomMaxX - roomMinX;
        double switchZ = floorZ + SWITCH_HEIGHT;

        // Switches typically near door - placed at RIGHT side of south wall
        // Phase 41: Position at wall end to avoid window lintels which are typically
        // in the center of the wall. Offset 0.3m from corner for column clearance.
        double baseX = roomMaxX - 0.3 - (count * 0.1);  // Start position for all switches
        double y = roomMinY + WALL_OFFSET;

        // Check if switches fit (need 0.3m clearance from left corner too)
        if (baseX < roomMinX + 0.3) {
            OutlierLogger.logGeometryImpossible(
                "switch",
                roomContext,
                "too many switches for wall width",
                String.format("Need %.2fm for %d switches, wall is %.2fm",
                    0.6 + count * 0.1, count, roomWidth));
            return switches;
        }

        for (int i = 0; i < count; i++) {
            double x = baseX + i * 0.1;  // 100mm spacing between switches

            switches.add(new ElectricalInstance(
                ElectricalType.SWITCH,
                new Point3D(x, y, switchZ),
                0,  // facing into room
                SWITCH_WIDTH, SWITCH_DEPTH, SWITCH_HEIGHT_DIM,
                null, "LIGHT_SWITCH",
                "lighting", roomName  // Switches on lighting circuit
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
        String name,
        String circuitType,   // Phase 39: general, wet_area, high_load
        String roomName       // Phase 39: for circuit assignment
    ) {
        // Compatibility constructor for existing code
        public ElectricalInstance(
                ElectricalType type,
                Point3D worldPosition,
                double rotation,
                double width,
                double depth,
                double height,
                String geometryHash,
                String name) {
            this(type, worldPosition, rotation, width, depth, height,
                 geometryHash, name, "general", null);
        }
    }

    // =========================================================================
    // Phase 39: Electrical Circuit Graph Construction
    // =========================================================================

    /**
     * Build MEPSystem graph for electrical circuits.
     *
     * Graph structure:
     *   DB_PANEL (SOURCE)
     *       │
     *       ├── DB1-L1 (DISTRIBUTION) ──FEEDS──> lights
     *       ├── DB1-P1 (DISTRIBUTION) ──FEEDS──> general outlets
     *       ├── DB1-WET1 (DISTRIBUTION) ──FEEDS──> wet_area outlets
     *       └── DB1-K1 (DISTRIBUTION) ──FEEDS──> high_load outlets
     *
     * @param elements All electrical elements placed in the building
     * @param storeyId Storey identifier for system naming
     * @return MEPSystem graph with connectivity from DB panel to all outlets
     */
    public MEPSystem buildElectricalGraph(
            List<ElectricalInstance> elements,
            String storeyId) {

        String systemId = "ELEC-" + storeyId;
        MEPSystem system = new MEPSystem(systemId, SystemType.ELECTRICAL);

        // 1. Create DB_PANEL as SOURCE (external - utility connection)
        String dbPanelId = "DB1-" + storeyId;
        system.addNode(new SystemNode(
            dbPanelId,
            null,  // External node (not a placed element)
            NodeRole.SOURCE,
            "Distribution Board " + storeyId,
            Map.of("type", "DB_PANEL", "storey", storeyId)
        ));

        // 2. Group elements by circuit type
        Map<String, List<ElectricalInstance>> byCircuitType = groupByCircuitType(elements);

        // 3. Create circuits and connect elements
        // DETERMINISM FIX: Sort keys for canonical ordering (Watchdog ruling 2026-02-02)
        // HashMap iteration order is JVM-dependent; sorted keys ensure reproducible circuit IDs
        List<String> sortedCircuitTypes = new ArrayList<>(byCircuitType.keySet());
        Collections.sort(sortedCircuitTypes);

        int circuitCounter = 1;
        int globalElementIndex = 0;  // Global counter for unique element IDs

        for (String circuitType : sortedCircuitTypes) {
            List<ElectricalInstance> circuitElements = byCircuitType.get(circuitType);

            // Determine circuit prefix based on type
            String circuitPrefix = getCircuitPrefix(circuitType);

            // For ring circuits, limit to ~10 outlets per circuit (MS IEC 60364)
            int maxPerCircuit = circuitType.equals("general") ? 10 :
                               circuitType.equals("high_load") ? 1 : 6;

            List<List<ElectricalInstance>> chunks = partitionList(circuitElements, maxPerCircuit);

            for (List<ElectricalInstance> chunk : chunks) {
                String circuitId = dbPanelId + "-" + circuitPrefix + circuitCounter;

                // Create circuit as DISTRIBUTION node
                system.addNode(new SystemNode(
                    circuitId,
                    null,  // Circuit is logical, not a placed element
                    NodeRole.DISTRIBUTION,
                    circuitPrefix + " Circuit " + circuitCounter,
                    Map.of(
                        "circuit_type", circuitType,
                        "terminal_count", chunk.size()
                    )
                ));

                // Connect DB panel to circuit
                system.addEdge(new SystemEdge(
                    "E-" + dbPanelId + "-" + circuitId,
                    dbPanelId,
                    circuitId,
                    EdgeType.FEEDS,
                    Map.of("cable_size_mm2", getCableSize(circuitType))
                ));

                // Connect each element to its circuit
                for (ElectricalInstance element : chunk) {
                    String elementId = generateElementId(element, storeyId, globalElementIndex++);

                    // Create element as TERMINAL node
                    system.addNode(new SystemNode(
                        elementId,
                        elementId,  // Self-referencing GUID for now
                        NodeRole.TERMINAL,
                        element.name(),
                        Map.of(
                            "type", element.type().name(),
                            "x", element.worldPosition().x(),
                            "y", element.worldPosition().y(),
                            "z", element.worldPosition().z()
                        )
                    ));

                    // Connect circuit to element
                    system.addEdge(new SystemEdge(
                        "E-" + circuitId + "-" + elementId,
                        circuitId,
                        elementId,
                        EdgeType.FEEDS,
                        Map.of()
                    ));
                }

                circuitCounter++;
            }
        }

        return system;
    }

    /**
     * Group electrical elements by circuit type.
     * Lights go on lighting circuits, outlets by their assigned circuit type.
     */
    private Map<String, List<ElectricalInstance>> groupByCircuitType(
            List<ElectricalInstance> elements) {

        Map<String, List<ElectricalInstance>> groups = new HashMap<>();

        for (ElectricalInstance element : elements) {
            String circuitType;

            if (element.type() == ElectricalType.LIGHT) {
                circuitType = "lighting";
            } else if (element.type() == ElectricalType.SWITCH) {
                circuitType = "lighting";  // Switches on same circuit as lights
            } else {
                circuitType = element.circuitType() != null ?
                              element.circuitType() : "general";
            }

            groups.computeIfAbsent(circuitType, k -> new ArrayList<>()).add(element);
        }

        return groups;
    }

    /**
     * Get circuit prefix for naming based on type.
     */
    private String getCircuitPrefix(String circuitType) {
        return switch (circuitType) {
            case "lighting" -> "L";
            case "general" -> "P";
            case "wet_area" -> "WET";
            case "high_load" -> "K";
            default -> "C";
        };
    }

    /**
     * Get cable size in mm² based on circuit type (MS IEC 60364).
     */
    private double getCableSize(String circuitType) {
        return switch (circuitType) {
            case "lighting" -> 1.5;      // 1.5mm² for lighting
            case "general" -> 2.5;        // 2.5mm² for ring circuits
            case "wet_area" -> 2.5;       // 2.5mm² with RCD
            case "high_load" -> 4.0;      // 4.0mm² for dedicated circuits
            default -> 2.5;
        };
    }

    /**
     * Generate unique element ID.
     */
    private String generateElementId(ElectricalInstance element,
                                     String storeyId, int index) {
        String typePrefix = switch (element.type()) {
            case LIGHT -> "LT";
            case OUTLET -> "OUT";
            case SWITCH -> "SW";
        };
        return typePrefix + "-" + storeyId + "-" + index;
    }

    /**
     * Partition a list into chunks of maxSize.
     */
    private <T> List<List<T>> partitionList(List<T> list, int maxSize) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += maxSize) {
            partitions.add(list.subList(i, Math.min(i + maxSize, list.size())));
        }
        return partitions;
    }
}
