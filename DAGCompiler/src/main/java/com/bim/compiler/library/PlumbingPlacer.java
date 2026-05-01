/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.library;

import com.bim.compiler.geometry.Point3D;
import com.bim.compiler.geometry.BoundingBox;
import com.bim.compiler.dsl.SpaceTypeRegistry.PlumbingConfig;
import com.bim.compiler.system.*;
import com.bim.compiler.util.OutlierLogger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Phase 34: Places plumbing elements (risers, vents, branch pipes).
 *
 * Plumbing standards (IRC 2021 / UBBL 1984):
 * - Waste stack (main riser): 100mm (4") for toilets
 * - Vent pipe: 50mm (2") minimum
 * - Toilet branch: 100mm (4")
 * - Sink branch: 40mm (1.5")
 *
 * Stack alignment constraint:
 * - Rooms with same stack: name share X,Y position
 * - Riser runs vertically through all stacked rooms
 * - Vent extends from highest fixture to roof
 */
public class PlumbingPlacer {

    // Standard pipe diameters (meters) - from IRC 2021 Table P3002.1
    private static final double WASTE_STACK_DIAMETER = 0.100;   // 100mm (4") main waste riser
    private static final double VENT_PIPE_DIAMETER = 0.050;     // 50mm (2") vent
    private static final double TOILET_BRANCH_DIAMETER = 0.100; // 100mm (4") toilet drain
    private static final double SINK_BRANCH_DIAMETER = 0.040;   // 40mm (1.5") sink drain

    // Placement offsets (meters)
    private static final double RISER_WALL_OFFSET = 0.223;      // SP discipline routing constraint
    private static final double FIXTURE_OUTLET_HEIGHT = 0.0;    // Floor level for toilet
    private static final double SINK_TRAP_HEIGHT = 0.75;        // Below sink at 850mm counter
    private static final double VENT_EXTENSION = 0.3;           // Vent extends 300mm above roof

    /**
     * Collected data for a plumbing stack across all storeys.
     */
    public record StackInfo(
        String stackName,
        double stackX,
        double stackY,
        double lowestZ,      // Bottom of stack (ground floor)
        double highestZ,     // Top of stack (highest storey ceiling)
        double roofZ,        // Roof level for vent termination
        List<FixtureConnection> fixtures
    ) {}

    /**
     * Connection point from a fixture to the riser.
     */
    public record FixtureConnection(
        String fixtureType,  // "toilet", "sink"
        double x, double y, double z,  // Fixture outlet position
        double diameterM     // Branch pipe diameter
    ) {}

    /**
     * Output: a pipe segment specification.
     */
    public record PipeInstance(
        PipeType type,
        Point3D start,
        Point3D end,
        double diameterM,
        String name
    ) {
        public double length() {
            return Math.sqrt(
                Math.pow(end.x() - start.x(), 2) +
                Math.pow(end.y() - start.y(), 2) +
                Math.pow(end.z() - start.z(), 2)
            );
        }
    }

    public enum PipeType {
        WASTE_RISER,    // Main vertical waste stack
        VENT_PIPE,      // Vertical vent to roof
        BRANCH_PIPE     // Horizontal connection to fixture
    }

    /**
     * Generate plumbing pipes for a building.
     *
     * @param stacks Map of stack name to StackInfo (built from BuildingSpec)
     * @return List of pipe instances
     */
    public List<PipeInstance> generatePlumbing(Map<String, StackInfo> stacks) {
        List<PipeInstance> pipes = new ArrayList<>();

        for (var entry : stacks.entrySet()) {
            String stackName = entry.getKey();
            StackInfo stack = entry.getValue();
            String context = String.format("Stack \"%s\"", stackName);

            // 1. Generate main waste riser (vertical)
            if (stack.fixtures().stream().anyMatch(f -> f.fixtureType().equals("toilet"))) {
                // Toilet stack needs 100mm riser
                pipes.add(new PipeInstance(
                    PipeType.WASTE_RISER,
                    new Point3D(stack.stackX(), stack.stackY(), stack.lowestZ()),
                    new Point3D(stack.stackX(), stack.stackY(), stack.highestZ()),
                    WASTE_STACK_DIAMETER,
                    stackName + "_waste_riser"
                ));
            }

            // 2. Generate vent pipe (extends to roof + 300mm)
            double ventX = stack.stackX() + 0.1;  // Offset from waste riser
            pipes.add(new PipeInstance(
                PipeType.VENT_PIPE,
                new Point3D(ventX, stack.stackY(), stack.lowestZ()),
                new Point3D(ventX, stack.stackY(), stack.roofZ() + VENT_EXTENSION),
                VENT_PIPE_DIAMETER,
                stackName + "_vent"
            ));

            // 3. Generate branch pipes from fixtures to riser
            for (FixtureConnection fixture : stack.fixtures()) {
                // Branch pipe runs horizontally from fixture to riser
                double branchDiameter = fixture.diameterM();

                // Skip if fixture is directly on riser
                double distToRiser = Math.sqrt(
                    Math.pow(fixture.x() - stack.stackX(), 2) +
                    Math.pow(fixture.y() - stack.stackY(), 2)
                );

                if (distToRiser > 0.05) {  // Only create branch if fixture is not on riser
                    pipes.add(new PipeInstance(
                        PipeType.BRANCH_PIPE,
                        new Point3D(fixture.x(), fixture.y(), fixture.z()),
                        new Point3D(stack.stackX(), stack.stackY(), fixture.z()),
                        branchDiameter,
                        fixture.fixtureType() + "_branch"
                    ));
                }
            }
        }

        return pipes;
    }

    /**
     * Build stack info from room and fixture data.
     *
     * @param stackName Name of the stack
     * @param stackX X coordinate of stack position
     * @param stackY Y coordinate of stack position
     * @param floorZ Lowest floor Z in stack
     * @param ceilingZ Highest ceiling Z in stack
     * @param roofZ Roof elevation
     * @param fixturePositions List of fixture positions: [type, x, y, z]
     * @return StackInfo for pipe generation
     */
    public static StackInfo buildStackInfo(
            String stackName,
            double stackX, double stackY,
            double floorZ, double ceilingZ, double roofZ,
            List<double[]> fixturePositions) {

        List<FixtureConnection> fixtures = new ArrayList<>();

        for (double[] pos : fixturePositions) {
            String type = pos[0] == 1 ? "toilet" : "sink";
            double diameter = type.equals("toilet") ? TOILET_BRANCH_DIAMETER : SINK_BRANCH_DIAMETER;

            fixtures.add(new FixtureConnection(
                type,
                pos[1], pos[2], pos[3],
                diameter
            ));
        }

        return new StackInfo(
            stackName,
            stackX, stackY,
            floorZ, ceilingZ, roofZ,
            fixtures
        );
    }

    /**
     * Place plumbing for a single room with plumbing requirements.
     * For single-storey buildings without vertical stacks.
     *
     * @param roomMinX Room bounds
     * @param roomMinY Room bounds
     * @param roomMaxX Room bounds
     * @param roomMaxY Room bounds
     * @param floorZ Floor elevation
     * @param ceilingZ Ceiling elevation
     * @param roofZ Roof elevation (for vent termination)
     * @param config Plumbing config from SpaceTypeRegistry
     * @param roomName Room name for logging
     * @param hasToilet Whether room has a toilet fixture
     * @param toiletX X position of toilet (if present)
     * @param toiletY Y position of toilet (if present)
     * @param hasSink Whether room has a sink fixture
     * @param sinkX X position of sink (if present)
     * @param sinkY Y position of sink (if present)
     * @return List of pipe instances
     */
    public List<PipeInstance> placeRoomPlumbing(
            double roomMinX, double roomMinY,
            double roomMaxX, double roomMaxY,
            double floorZ, double ceilingZ, double roofZ,
            PlumbingConfig config,
            String roomName,
            boolean hasToilet, double toiletX, double toiletY,
            boolean hasSink, double sinkX, double sinkY) {

        List<PipeInstance> pipes = new ArrayList<>();

        if (!config.requiresStack()) {
            return pipes;  // No plumbing stack required
        }

        String context = String.format("\"%s\"", roomName);

        // Position riser near toilet (or sink if no toilet)
        double riserX, riserY;
        if (hasToilet) {
            riserX = toiletX;
            riserY = toiletY - 0.15;  // Behind toilet
        } else if (hasSink) {
            riserX = sinkX;
            riserY = sinkY;
        } else {
            // Default to corner of room
            riserX = roomMinX + RISER_WALL_OFFSET;
            riserY = roomMaxY - RISER_WALL_OFFSET;
        }

        // 1. Waste riser (if has toilet)
        if (hasToilet) {
            pipes.add(new PipeInstance(
                PipeType.WASTE_RISER,
                new Point3D(riserX, riserY, floorZ),
                new Point3D(riserX, riserY, ceilingZ),
                WASTE_STACK_DIAMETER,
                roomName + "_waste_riser"
            ));

            // Toilet branch (short connection from toilet to riser)
            double distToRiser = Math.sqrt(
                Math.pow(toiletX - riserX, 2) +
                Math.pow(toiletY - riserY, 2)
            );
            if (distToRiser > 0.05) {
                pipes.add(new PipeInstance(
                    PipeType.BRANCH_PIPE,
                    new Point3D(toiletX, toiletY, floorZ + FIXTURE_OUTLET_HEIGHT),
                    new Point3D(riserX, riserY, floorZ + FIXTURE_OUTLET_HEIGHT),
                    TOILET_BRANCH_DIAMETER,
                    roomName + "_toilet_branch"
                ));
            }
        }

        // 2. Vent pipe (required for all wet rooms)
        if (config.requiresVent()) {
            double ventX = riserX + 0.1;  // Offset from riser
            pipes.add(new PipeInstance(
                PipeType.VENT_PIPE,
                new Point3D(ventX, riserY, floorZ),
                new Point3D(ventX, riserY, roofZ + VENT_EXTENSION),
                VENT_PIPE_DIAMETER,
                roomName + "_vent"
            ));
        }

        // 3. Sink branch (if has sink)
        if (hasSink) {
            double sinkZ = floorZ + SINK_TRAP_HEIGHT;
            double distToRiser = Math.sqrt(
                Math.pow(sinkX - riserX, 2) +
                Math.pow(sinkY - riserY, 2)
            );
            if (distToRiser > 0.05) {
                pipes.add(new PipeInstance(
                    PipeType.BRANCH_PIPE,
                    new Point3D(sinkX, sinkY, sinkZ),
                    new Point3D(riserX, riserY, sinkZ),
                    SINK_BRANCH_DIAMETER,
                    roomName + "_sink_branch"
                ));
            }
        }

        return pipes;
    }

    /**
     * Validate stack alignment across storeys.
     *
     * @param stackRooms Map of stack name to list of room positions [minX, minY, maxX, maxY]
     * @param tolerance Alignment tolerance in meters
     * @return Map of stack name to alignment status (true = aligned)
     */
    public static Map<String, Boolean> validateStackAlignment(
            Map<String, List<double[]>> stackRooms,
            double tolerance) {

        Map<String, Boolean> results = new HashMap<>();

        for (var entry : stackRooms.entrySet()) {
            String stackName = entry.getKey();
            List<double[]> rooms = entry.getValue();

            if (rooms.size() < 2) {
                results.put(stackName, true);  // Single room is trivially aligned
                continue;
            }

            // Check all rooms share same center position
            double[] first = rooms.get(0);
            double refCenterX = (first[0] + first[2]) / 2;
            double refCenterY = (first[1] + first[3]) / 2;

            boolean aligned = true;
            for (int i = 1; i < rooms.size(); i++) {
                double[] room = rooms.get(i);
                double centerX = (room[0] + room[2]) / 2;
                double centerY = (room[1] + room[3]) / 2;

                if (Math.abs(centerX - refCenterX) > tolerance ||
                    Math.abs(centerY - refCenterY) > tolerance) {
                    aligned = false;
                    break;
                }
            }

            results.put(stackName, aligned);
        }

        return results;
    }

    // =========================================================================
    // Phase 35: MEP System Graph Construction
    // =========================================================================

    /**
     * Build waste system graph from generated pipe instances.
     *
     * Creates a directed graph where:
     * - MH (external) is the SOURCE
     * - Waste risers are DISTRIBUTION nodes
     * - Fixtures (via branch pipes) are TERMINAL nodes
     * - Edges represent DRAINS_TO flow direction
     *
     * @param pipes List of pipe instances generated by placeRoomPlumbing
     * @param pipeGuids Map of pipe name to GUID (from BuildingWriter)
     * @param fixtureGuids Map of fixture name to GUID
     * @return MEPSystem representing the waste drainage graph
     */
    public MEPSystem buildWasteSystemGraph(
            List<PipeInstance> pipes,
            Map<String, String> pipeGuids,
            Map<String, String> fixtureGuids) {

        MEPSystem system = new MEPSystem("waste_system_1", SystemType.PLUMBING_WASTE);

        // 1. Add MH as external source
        system.addNode(new SystemNode(
            "MH1",
            null,  // External - not modeled in building
            NodeRole.SOURCE,
            "Manhole 1",
            Map.of("location", "exterior", "connects_to", "septic")
        ));

        // 2. Add waste risers as distribution nodes
        List<String> riserNodeIds = new ArrayList<>();
        for (PipeInstance pipe : pipes) {
            if (pipe.type() == PipeType.WASTE_RISER) {
                String nodeId = "riser_" + pipe.name().replace("_waste_riser", "");
                String guid = pipeGuids.getOrDefault(pipe.name(), null);

                system.addNode(new SystemNode(
                    nodeId,
                    guid,
                    NodeRole.DISTRIBUTION,
                    "Waste Riser - " + pipe.name(),
                    Map.of("diameter_mm", (int)(pipe.diameterM() * 1000))
                ));

                // Connect riser to MH
                system.addEdge(new SystemEdge(
                    "edge_" + nodeId + "_to_MH1",
                    nodeId,
                    "MH1",
                    EdgeType.DRAINS_TO,
                    Map.of("pipe_type", "underground")
                ));

                riserNodeIds.add(nodeId);
            }
        }

        // 3. Add fixtures as terminals (from branch pipes)
        for (PipeInstance pipe : pipes) {
            if (pipe.type() == PipeType.BRANCH_PIPE) {
                // Parse fixture type from branch name (e.g., "tandas_toilet_branch")
                String pipeName = pipe.name();
                String fixtureType = extractFixtureType(pipeName);
                String roomName = extractRoomName(pipeName);

                String nodeId = fixtureType + "_" + roomName;
                String guid = fixtureGuids.getOrDefault(fixtureType + "_" + roomName, null);

                // Avoid duplicate nodes
                if (system.getNode(nodeId).isPresent()) {
                    continue;
                }

                system.addNode(new SystemNode(
                    nodeId,
                    guid,
                    NodeRole.TERMINAL,
                    fixtureType + " in " + roomName,
                    Map.of(
                        "fixture_type", fixtureType,
                        "branch_diameter_mm", (int)(pipe.diameterM() * 1000)
                    )
                ));

                // Find the riser this fixture connects to
                String riserNodeId = findRiserForRoom(roomName, riserNodeIds);
                if (riserNodeId != null) {
                    system.addEdge(new SystemEdge(
                        "edge_" + nodeId + "_to_" + riserNodeId,
                        nodeId,
                        riserNodeId,
                        EdgeType.DRAINS_TO,
                        Map.of(
                            "diameter_mm", (int)(pipe.diameterM() * 1000),
                            "slope_percent", 1.0
                        )
                    ));
                }
            }
        }

        return system;
    }

    /**
     * Simplified graph builder when GUIDs aren't available yet.
     * Uses pipe/fixture names as node IDs.
     */
    public MEPSystem buildWasteSystemGraph(List<PipeInstance> pipes) {
        return buildWasteSystemGraph(pipes, Map.of(), Map.of());
    }

    // =========================================================================
    // Phase 36: Vent System Graph Construction
    // =========================================================================

    /**
     * Build vent system graph from generated pipe instances.
     *
     * Creates a directed graph where:
     * - Vent termination (roof) is the SOURCE (atmosphere)
     * - Vent pipes are DISTRIBUTION nodes
     * - Fixture traps are TERMINAL nodes
     * - Edges represent VENTS_TO flow direction (trap -> vent -> atmosphere)
     *
     * @param pipes List of pipe instances generated by placeRoomPlumbing
     * @return MEPSystem representing the vent system graph
     */
    public MEPSystem buildVentSystemGraph(List<PipeInstance> pipes) {
        MEPSystem system = new MEPSystem("vent_system_1", SystemType.PLUMBING_VENT);

        // 1. Add vent termination as external source (atmosphere)
        system.addNode(new SystemNode(
            "VENT_TERM",
            null,  // External - atmosphere
            NodeRole.SOURCE,
            "Vent Termination",
            Map.of("location", "roof", "terminates_at", "atmosphere")
        ));

        // 2. Add vent pipes as distribution nodes
        List<String> ventNodeIds = new ArrayList<>();
        for (PipeInstance pipe : pipes) {
            if (pipe.type() == PipeType.VENT_PIPE) {
                String nodeId = "vent_" + pipe.name().replace("_vent", "");

                system.addNode(new SystemNode(
                    nodeId,
                    null,
                    NodeRole.DISTRIBUTION,
                    "Vent Stack - " + pipe.name(),
                    Map.of("diameter_mm", (int)(pipe.diameterM() * 1000))
                ));

                // Connect vent to termination (atmosphere)
                system.addEdge(new SystemEdge(
                    "edge_" + nodeId + "_to_VENT_TERM",
                    nodeId,
                    "VENT_TERM",
                    EdgeType.VENTS_TO,
                    Map.of("terminates_above_roof", true)
                ));

                ventNodeIds.add(nodeId);
            }
        }

        // 3. Add fixture traps as terminals (from branch pipes - same fixtures need venting)
        for (PipeInstance pipe : pipes) {
            if (pipe.type() == PipeType.BRANCH_PIPE) {
                String pipeName = pipe.name();
                String fixtureType = extractFixtureType(pipeName);
                String roomName = extractRoomName(pipeName);

                String nodeId = fixtureType + "_trap_" + roomName;

                // Avoid duplicate nodes
                if (system.getNode(nodeId).isPresent()) {
                    continue;
                }

                system.addNode(new SystemNode(
                    nodeId,
                    null,
                    NodeRole.TERMINAL,
                    fixtureType + " trap in " + roomName,
                    Map.of(
                        "fixture_type", fixtureType,
                        "requires_venting", true
                    )
                ));

                // Find the vent this fixture connects to
                String ventNodeId = findVentForRoom(roomName, ventNodeIds);
                if (ventNodeId != null) {
                    system.addEdge(new SystemEdge(
                        "edge_" + nodeId + "_to_" + ventNodeId,
                        nodeId,
                        ventNodeId,
                        EdgeType.VENTS_TO,
                        Map.of("prevents_siphon", true)
                    ));
                }
            }
        }

        return system;
    }

    /**
     * Find the vent node that serves a given room.
     */
    private String findVentForRoom(String roomName, List<String> ventNodeIds) {
        // Try exact match first
        for (String ventId : ventNodeIds) {
            if (ventId.equals("vent_" + roomName)) {
                return ventId;
            }
        }
        // Fall back to first vent if only one exists
        if (ventNodeIds.size() == 1) {
            return ventNodeIds.get(0);
        }
        return null;
    }

    // =========================================================================
    // Phase 37: Supply System Graph Construction
    // =========================================================================

    /**
     * Build supply system graph by inferring from fixtures.
     *
     * Any fixture with drainage also needs water supply.
     * Direction is SOURCE → TERMINAL (forward traversal).
     *
     * @param pipes List of pipe instances (uses branch pipes to find fixtures)
     * @return MEPSystem representing the water supply graph
     */
    public MEPSystem buildSupplySystemGraph(List<PipeInstance> pipes) {
        MEPSystem system = new MEPSystem("supply_system_1", SystemType.PLUMBING_SUPPLY);

        // 1. Add water meter as external source
        system.addNode(new SystemNode(
            "WATER_METER",
            null,  // External
            NodeRole.SOURCE,
            "Water Meter",
            Map.of("location", "exterior", "utility", "water_supply")
        ));

        // 2. Add supply riser as distribution (inferred from waste riser rooms)
        List<String> supplyRiserIds = new ArrayList<>();
        for (PipeInstance pipe : pipes) {
            if (pipe.type() == PipeType.WASTE_RISER) {
                String roomName = pipe.name().replace("_waste_riser", "");
                String nodeId = "supply_riser_" + roomName;

                system.addNode(new SystemNode(
                    nodeId,
                    null,
                    NodeRole.DISTRIBUTION,
                    "Supply Riser - " + roomName,
                    Map.of("type", "cold_water")
                ));

                // Connect meter to supply riser (forward direction)
                system.addEdge(new SystemEdge(
                    "edge_WATER_METER_to_" + nodeId,
                    "WATER_METER",
                    nodeId,
                    EdgeType.SUPPLIES,
                    Map.of("main_supply", true)
                ));

                supplyRiserIds.add(nodeId);
            }
        }

        // 3. Add fixtures as terminals (from branch pipes)
        for (PipeInstance pipe : pipes) {
            if (pipe.type() == PipeType.BRANCH_PIPE) {
                String pipeName = pipe.name();
                String fixtureType = extractFixtureType(pipeName);
                String roomName = extractRoomName(pipeName);

                String nodeId = fixtureType + "_supply_" + roomName;

                if (system.getNode(nodeId).isPresent()) {
                    continue;
                }

                system.addNode(new SystemNode(
                    nodeId,
                    null,
                    NodeRole.TERMINAL,
                    fixtureType + " in " + roomName,
                    Map.of("fixture_type", fixtureType, "requires_supply", true)
                ));

                // Connect supply riser to fixture (forward direction)
                String riserNodeId = findSupplyRiserForRoom(roomName, supplyRiserIds);
                if (riserNodeId != null) {
                    system.addEdge(new SystemEdge(
                        "edge_" + riserNodeId + "_to_" + nodeId,
                        riserNodeId,
                        nodeId,
                        EdgeType.SUPPLIES,
                        Map.of("branch_supply", true)
                    ));
                }
            }
        }

        return system;
    }

    private String findSupplyRiserForRoom(String roomName, List<String> riserNodeIds) {
        for (String riserId : riserNodeIds) {
            if (riserId.equals("supply_riser_" + roomName)) {
                return riserId;
            }
        }
        if (riserNodeIds.size() == 1) {
            return riserNodeIds.get(0);
        }
        return null;
    }

    /**
     * Extract fixture type from branch pipe name.
     * e.g., "tandas_toilet_branch" -> "toilet"
     */
    private String extractFixtureType(String pipeName) {
        if (pipeName.contains("toilet")) return "toilet";
        if (pipeName.contains("sink")) return "sink";
        return "fixture";
    }

    /**
     * Extract room name from branch pipe name.
     * e.g., "tandas_toilet_branch" -> "tandas"
     */
    private String extractRoomName(String pipeName) {
        // Remove known suffixes
        String name = pipeName
            .replace("_toilet_branch", "")
            .replace("_sink_branch", "")
            .replace("_branch", "");
        return name;
    }

    /**
     * Find the riser node that serves a given room.
     * For single-storey, rooms typically have their own riser.
     */
    private String findRiserForRoom(String roomName, List<String> riserNodeIds) {
        // Try exact match first
        for (String riserId : riserNodeIds) {
            if (riserId.equals("riser_" + roomName)) {
                return riserId;
            }
        }
        // Fall back to first riser if only one exists
        if (riserNodeIds.size() == 1) {
            return riserNodeIds.get(0);
        }
        return null;
    }

    // =========================================================================
    // Phase 38: Multi-Storey Vertical Stack Connections
    // =========================================================================

    /**
     * Info about a room participating in a vertical stack.
     */
    public record StackRoomInfo(
        String roomName,
        int storeyLevel,
        double baseZ,
        double ceilingZ,
        double centerX,
        double centerY
    ) {}

    /**
     * Build waste system graph for multi-storey buildings with vertical stacks.
     *
     * Creates vertical edges connecting bathroom risers across storeys.
     * For a stack like "plumbing_core" spanning bath_g (level 0) and bath_u (level 1):
     *   - riser_bath_u CONNECTS_VERTICAL to riser_bath_g
     *   - riser_bath_g DRAINS_TO MH1
     *
     * @param pipes All pipe instances across all storeys
     * @param stackRooms Map of stack name to list of rooms in that stack (sorted by level)
     * @return MEPSystem with vertical connections
     */
    public MEPSystem buildMultiStoreyWasteGraph(
            List<PipeInstance> pipes,
            Map<String, List<StackRoomInfo>> stackRooms) {

        MEPSystem system = new MEPSystem("waste_system_1", SystemType.PLUMBING_WASTE);

        // 1. Add MH as external source
        system.addNode(new SystemNode(
            "MH1",
            null,
            NodeRole.SOURCE,
            "Manhole 1",
            Map.of("location", "exterior", "connects_to", "septic")
        ));

        // 2. Add waste risers as distribution nodes (with storey info)
        Map<String, String> roomToRiserNode = new HashMap<>();
        for (PipeInstance pipe : pipes) {
            if (pipe.type() == PipeType.WASTE_RISER) {
                String roomName = pipe.name().replace("_waste_riser", "");
                String nodeId = "riser_" + roomName;

                // Find storey level for this room
                int storeyLevel = findStoreyLevel(roomName, stackRooms);

                system.addNode(new SystemNode(
                    nodeId,
                    null,
                    NodeRole.DISTRIBUTION,
                    "Waste Riser - " + roomName,
                    Map.of(
                        "diameter_mm", (int)(pipe.diameterM() * 1000),
                        "storey_level", storeyLevel
                    )
                ));

                roomToRiserNode.put(roomName, nodeId);
            }
        }

        // 3. Add vertical edges connecting stacks across storeys
        for (var entry : stackRooms.entrySet()) {
            String stackName = entry.getKey();
            List<StackRoomInfo> rooms = entry.getValue();

            // Sort by storey level (descending: upper first)
            List<StackRoomInfo> sortedRooms = new ArrayList<>(rooms);
            sortedRooms.sort((a, b) -> Integer.compare(b.storeyLevel(), a.storeyLevel()));

            // Connect each upper room's riser to the room below
            for (int i = 0; i < sortedRooms.size() - 1; i++) {
                StackRoomInfo upperRoom = sortedRooms.get(i);
                StackRoomInfo lowerRoom = sortedRooms.get(i + 1);

                String upperRiserId = roomToRiserNode.get(upperRoom.roomName());
                String lowerRiserId = roomToRiserNode.get(lowerRoom.roomName());

                if (upperRiserId != null && lowerRiserId != null) {
                    system.addEdge(new SystemEdge(
                        "edge_" + upperRiserId + "_to_" + lowerRiserId,
                        upperRiserId,
                        lowerRiserId,
                        EdgeType.CONNECTS_VERTICAL,
                        Map.of(
                            "stack_name", stackName,
                            "from_level", upperRoom.storeyLevel(),
                            "to_level", lowerRoom.storeyLevel()
                        )
                    ));
                }
            }

            // Connect lowest riser to MH
            if (!sortedRooms.isEmpty()) {
                StackRoomInfo lowestRoom = sortedRooms.get(sortedRooms.size() - 1);
                String lowestRiserId = roomToRiserNode.get(lowestRoom.roomName());
                if (lowestRiserId != null) {
                    system.addEdge(new SystemEdge(
                        "edge_" + lowestRiserId + "_to_MH1",
                        lowestRiserId,
                        "MH1",
                        EdgeType.DRAINS_TO,
                        Map.of("pipe_type", "underground", "stack_name", stackName)
                    ));
                }
            }
        }

        // 4. Add fixtures as terminals (from branch pipes)
        for (PipeInstance pipe : pipes) {
            if (pipe.type() == PipeType.BRANCH_PIPE) {
                String pipeName = pipe.name();
                String fixtureType = extractFixtureType(pipeName);
                String roomName = extractRoomName(pipeName);

                String nodeId = fixtureType + "_" + roomName;

                if (system.getNode(nodeId).isPresent()) {
                    continue;
                }

                system.addNode(new SystemNode(
                    nodeId,
                    null,
                    NodeRole.TERMINAL,
                    fixtureType + " in " + roomName,
                    Map.of(
                        "fixture_type", fixtureType,
                        "branch_diameter_mm", (int)(pipe.diameterM() * 1000)
                    )
                ));

                // Connect fixture to its room's riser
                String riserNodeId = roomToRiserNode.get(roomName);
                if (riserNodeId != null) {
                    system.addEdge(new SystemEdge(
                        "edge_" + nodeId + "_to_" + riserNodeId,
                        nodeId,
                        riserNodeId,
                        EdgeType.DRAINS_TO,
                        Map.of(
                            "diameter_mm", (int)(pipe.diameterM() * 1000),
                            "slope_percent", 1.0
                        )
                    ));
                }
            }
        }

        return system;
    }

    /**
     * Find the storey level for a room from stack info.
     */
    private int findStoreyLevel(String roomName, Map<String, List<StackRoomInfo>> stackRooms) {
        for (List<StackRoomInfo> rooms : stackRooms.values()) {
            for (StackRoomInfo room : rooms) {
                if (room.roomName().equals(roomName)) {
                    return room.storeyLevel();
                }
            }
        }
        return 0;  // Default to ground level
    }

    /**
     * Validate vertical alignment of stack rooms across storeys.
     *
     * @param stackRooms Map of stack name to list of rooms in that stack
     * @param tolerance Alignment tolerance in meters (e.g., 0.005 = 5mm)
     * @return Map of stack name to validation result
     */
    public static Map<String, StackAlignmentResult> validateMultiStoreyAlignment(
            Map<String, List<StackRoomInfo>> stackRooms,
            double tolerance) {

        Map<String, StackAlignmentResult> results = new HashMap<>();

        for (var entry : stackRooms.entrySet()) {
            String stackName = entry.getKey();
            List<StackRoomInfo> rooms = entry.getValue();

            if (rooms.size() < 2) {
                results.put(stackName, new StackAlignmentResult(true, 0.0, 0.0, "Single room stack"));
                continue;
            }

            // Use first room as reference
            StackRoomInfo ref = rooms.get(0);
            double maxDeltaX = 0.0;
            double maxDeltaY = 0.0;
            boolean aligned = true;

            for (int i = 1; i < rooms.size(); i++) {
                StackRoomInfo room = rooms.get(i);
                double deltaX = Math.abs(room.centerX() - ref.centerX());
                double deltaY = Math.abs(room.centerY() - ref.centerY());

                maxDeltaX = Math.max(maxDeltaX, deltaX);
                maxDeltaY = Math.max(maxDeltaY, deltaY);

                if (deltaX > tolerance || deltaY > tolerance) {
                    aligned = false;
                }
            }

            String note = aligned ? "OK" : String.format(
                "Misaligned: deltaX=%.3fm, deltaY=%.3fm (tolerance=%.3fm)",
                maxDeltaX, maxDeltaY, tolerance);

            results.put(stackName, new StackAlignmentResult(aligned, maxDeltaX, maxDeltaY, note));
        }

        return results;
    }

    /**
     * Result of stack alignment validation.
     */
    public record StackAlignmentResult(
        boolean aligned,
        double maxDeltaX,
        double maxDeltaY,
        String note
    ) {}
}
