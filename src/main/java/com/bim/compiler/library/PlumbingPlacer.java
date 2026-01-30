package com.bim.compiler.library;

import com.bim.compiler.geometry.Point3D;
import com.bim.compiler.geometry.BoundingBox;
import com.bim.compiler.dsl.SpaceTypeRegistry.PlumbingConfig;
import com.bim.compiler.util.OutlierLogger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
}
