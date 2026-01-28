package com.bim.compiler.library;

import com.bim.compiler.geometry.Point3D;
import com.bim.compiler.geometry.BoundingBox;
import com.bim.compiler.library.ComponentLibrary.*;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

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

    // IBC clearances (meters)
    private static final double TOILET_SIDE_CLEARANCE = 0.38;   // 15 inches
    private static final double TOILET_FRONT_CLEARANCE = 0.533; // 21 inches
    private static final double SINK_FRONT_CLEARANCE = 0.533;   // 21 inches
    private static final double WALL_OFFSET = 0.05;             // 50mm from wall

    public FixturePlacer(ComponentLibrary library) {
        this.library = library;
    }

    /**
     * Place fixtures for a bathroom.
     *
     * Layout: Toilet against back wall, sink near door wall
     * Exhaust fan centered on ceiling.
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

        List<FixtureInstance> fixtures = new ArrayList<>();

        double roomWidth = roomMaxX - roomMinX;
        double roomDepth = roomMaxY - roomMinY;

        // Get fixture definitions
        ComponentDefinition toiletDef = library.getByName("Toilet");
        ComponentDefinition sinkDef = library.getByName("MRV basic round sink");
        ComponentDefinition exhaustDef = library.getByName("Exhaust air grill");

        // 1. Place toilet against back (north) wall, centered or left of center
        if (toiletDef != null) {
            double toiletWidth = toiletDef.localBounds().width();
            double toiletDepth = toiletDef.localBounds().depth();

            // Position: against north wall, left side of room
            double toiletX = roomMinX + TOILET_SIDE_CLEARANCE + toiletWidth / 2;
            double toiletY = roomMaxY - WALL_OFFSET - toiletDepth / 2;

            // Ensure fits in room
            if (toiletX + toiletWidth / 2 < roomMaxX - TOILET_SIDE_CLEARANCE) {
                fixtures.add(new FixtureInstance(
                    toiletDef,
                    new Point3D(toiletX, toiletY, floorZ),
                    0,  // rotation
                    FixtureType.TOILET
                ));
            }
        }

        // 2. Place sink on east wall or opposite toilet
        if (sinkDef != null) {
            double sinkWidth = sinkDef.localBounds().width();
            double sinkDepth = sinkDef.localBounds().depth();
            double sinkHeight = sinkDef.localBounds().height();

            // Position: on east wall, centered vertically
            double sinkX = roomMaxX - WALL_OFFSET - sinkDepth / 2;
            double sinkY = roomMinY + roomDepth / 2;
            double sinkZ = floorZ + 0.85; // Standard counter height 850mm

            // Ensure fits
            if (sinkX - sinkDepth / 2 > roomMinX + SINK_FRONT_CLEARANCE) {
                fixtures.add(new FixtureInstance(
                    sinkDef,
                    new Point3D(sinkX, sinkY, sinkZ),
                    Math.PI / 2,  // Rotated 90° to face into room
                    FixtureType.SINK
                ));
            }
        }

        // 3. Place exhaust fan centered on ceiling
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

        // Resolve any clashes
        return resolveClashes(fixtures);
    }

    /**
     * Place fixtures for a kitchen.
     *
     * Layout: Sink under window (if exterior) or against wall
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

        List<FixtureInstance> fixtures = new ArrayList<>();

        ComponentDefinition sinkDef = library.getByName("single_end_bowl_sink");

        if (sinkDef != null) {
            double sinkWidth = sinkDef.localBounds().width();
            double sinkDepth = sinkDef.localBounds().depth();
            double sinkZ = floorZ + 0.9; // Counter height 900mm

            double sinkX, sinkY;
            double rotation = 0;

            // Place sink on exterior wall (under window) or default to north wall
            switch (exteriorWall != null ? exteriorWall.toLowerCase() : "north") {
                case "south" -> {
                    sinkX = (roomMinX + roomMaxX) / 2;
                    sinkY = roomMinY + WALL_OFFSET + sinkDepth / 2;
                    rotation = Math.PI; // Face south
                }
                case "east" -> {
                    sinkX = roomMaxX - WALL_OFFSET - sinkDepth / 2;
                    sinkY = (roomMinY + roomMaxY) / 2;
                    rotation = -Math.PI / 2; // Face east
                }
                case "west" -> {
                    sinkX = roomMinX + WALL_OFFSET + sinkDepth / 2;
                    sinkY = (roomMinY + roomMaxY) / 2;
                    rotation = Math.PI / 2; // Face west
                }
                default -> { // north
                    sinkX = (roomMinX + roomMaxX) / 2;
                    sinkY = roomMaxY - WALL_OFFSET - sinkDepth / 2;
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
        return resolveClashes(fixtures);
    }

    // =========================================================================
    // Clash Detection & Resolution
    // =========================================================================

    /**
     * Check if two fixtures clash (bounding boxes overlap).
     * Includes clearance buffer for access.
     */
    private boolean clashes(FixtureInstance f1, FixtureInstance f2, double clearance) {
        // Get world-space bounding boxes
        double f1MinX = f1.worldPosition().x() - f1.localBounds().width() / 2 - clearance;
        double f1MaxX = f1.worldPosition().x() + f1.localBounds().width() / 2 + clearance;
        double f1MinY = f1.worldPosition().y() - f1.localBounds().depth() / 2 - clearance;
        double f1MaxY = f1.worldPosition().y() + f1.localBounds().depth() / 2 + clearance;

        double f2MinX = f2.worldPosition().x() - f2.localBounds().width() / 2;
        double f2MaxX = f2.worldPosition().x() + f2.localBounds().width() / 2;
        double f2MinY = f2.worldPosition().y() - f2.localBounds().depth() / 2;
        double f2MaxY = f2.worldPosition().y() + f2.localBounds().depth() / 2;

        // Check overlap
        boolean overlapX = f1MinX < f2MaxX && f1MaxX > f2MinX;
        boolean overlapY = f1MinY < f2MaxY && f1MaxY > f2MinY;

        return overlapX && overlapY;
    }

    /**
     * Filter fixtures to remove clashing items.
     * Priority: TOILET > SINK > EXHAUST_FAN (keep higher priority)
     */
    private List<FixtureInstance> resolveClashes(List<FixtureInstance> fixtures) {
        List<FixtureInstance> resolved = new ArrayList<>();

        // Sort by priority (toilet first, then sink, then exhaust)
        fixtures.sort((a, b) -> {
            int priorityA = getPriority(a.type());
            int priorityB = getPriority(b.type());
            return Integer.compare(priorityA, priorityB);
        });

        for (FixtureInstance candidate : fixtures) {
            boolean hasClash = false;

            for (FixtureInstance existing : resolved) {
                if (clashes(candidate, existing, 0.1)) { // 100mm clearance
                    hasClash = true;
                    System.out.printf("  [CLASH] %s clashes with %s - skipping%n",
                        candidate.type(), existing.type());
                    break;
                }
            }

            if (!hasClash) {
                resolved.add(candidate);
            }
        }

        return resolved;
    }

    private int getPriority(FixtureType type) {
        return switch (type) {
            case TOILET -> 1;       // Highest priority
            case SINK -> 2;
            case EXHAUST_FAN -> 3;
            case COUNTER -> 4;      // Lowest priority
        };
    }

    // =========================================================================
    // Output types
    // =========================================================================

    public enum FixtureType {
        TOILET,
        SINK,
        EXHAUST_FAN,
        COUNTER
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
