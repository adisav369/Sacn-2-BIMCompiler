package com.bim.compiler.library;

import com.bim.compiler.geometry.Point3D;
import com.bim.compiler.geometry.BoundingBox;
import com.bim.compiler.library.ComponentLibrary.*;
import com.bim.compiler.util.OutlierLogger;

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

    // Fixture clearances - Watchdog-reviewed 2026-02-02

    /** Toilet side clearance 381mm - ◆ RESEARCHED: IPC 405.3.1 (15 inches).
     *  This is NON-ACCESSIBLE standard. Accessible toilets require 457mm per ADA 604.2 / MS 1184.
     *  TODO: Add accessible vs non-accessible fixture placement profiles. */
    private static final double TOILET_SIDE_CLEARANCE = 0.38;

    /** Toilet front clearance 533mm - ◆ RESEARCHED: IPC 405.3.1 (21 inches).
     *  This is NON-ACCESSIBLE standard. Accessible requires 1219mm per ADA 604.3 / MS 1184. */
    private static final double TOILET_FRONT_CLEARANCE = 0.533;

    /** Sink front clearance 533mm - ◆ RESEARCHED: IPC 405.3.1 (21 inches) */
    private static final double SINK_FRONT_CLEARANCE = 0.533;

    /** Wall offset 50mm - ○ ASSUMED: clearance from wall face for fixture mounting */
    private static final double WALL_OFFSET = 0.05;

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
            double requiredWidth = toiletWidth + 2 * TOILET_SIDE_CLEARANCE;
            double requiredDepth = toiletDepth + TOILET_FRONT_CLEARANCE + WALL_OFFSET;

            if (roomWidth < requiredWidth) {
                OutlierLogger.logGeometryImpossible(
                    "toilet",
                    roomContext,
                    "room too narrow",
                    String.format("Width %.2fm < required %.2fm (%.2fm toilet + 2×%.2fm side clearance).",
                        roomWidth, requiredWidth, toiletWidth, TOILET_SIDE_CLEARANCE));
            } else if (roomDepth < requiredDepth) {
                OutlierLogger.logGeometryImpossible(
                    "toilet",
                    roomContext,
                    "room too shallow",
                    String.format("Depth %.2fm < required %.2fm (%.2fm toilet + %.2fm front clearance + %.2fm wall offset).",
                        roomDepth, requiredDepth, toiletDepth, TOILET_FRONT_CLEARANCE, WALL_OFFSET));
            } else {
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
        }

        // 2. Place sink on east wall or opposite toilet
        if (sinkDef != null) {
            double sinkWidth = sinkDef.localBounds().width();
            double sinkDepth = sinkDef.localBounds().depth();

            // Check if room has space for sink
            double requiredWidth = sinkDepth + SINK_FRONT_CLEARANCE + WALL_OFFSET;

            if (roomWidth < requiredWidth) {
                OutlierLogger.logGeometryImpossible(
                    "sink",
                    roomContext,
                    "insufficient clearance",
                    String.format("Min width for sink = %.2fm (%.2fm depth + %.2fm clearance). Room width = %.2fm.",
                        requiredWidth, sinkDepth, SINK_FRONT_CLEARANCE, roomWidth));
            } else {
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
        return resolveClashesWithLogging(fixtures, roomContext);
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
