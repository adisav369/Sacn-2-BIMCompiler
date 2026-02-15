package com.bim.compiler.library;

import com.bim.compiler.contract.BundleWorker;
import com.bim.compiler.library.FixturePlacer.FixtureInstance;
import com.bim.compiler.library.FixturePlacer.FixtureType;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Phase B4: BundleWorker adapter for FixturePlacer.
 *
 * <p>Wraps FixturePlacer (bathroom/kitchen/toilet-block fixture placement)
 * as a BundleWorker without changing FixturePlacer internals. Same adapter
 * pattern as FurnitureWorker.
 *
 * <p>Dispatches to the correct FixturePlacer method based on room type:
 * <ul>
 *   <li>BATHROOM → placeBathroomFixtures</li>
 *   <li>TOILET/WC → placeToiletBlockFixtures</li>
 *   <li>KITCHEN → placeKitchenFixtures</li>
 * </ul>
 *
 * <p>Identity chain preserved:
 * {@code FixtureInstance.type().name()} → {@code PlacedElement.role()}
 */
public class FixtureWorker implements BundleWorker {

    private final String assemblyId;
    private final FixturePlacer placer;

    public FixtureWorker(String assemblyId, ComponentLibrary library) {
        this.assemblyId = assemblyId;
        this.placer = new FixturePlacer(library);
    }

    @Override
    public String themeId() {
        return assemblyId;
    }

    @Override
    public String anchorFace() {
        return "BACK";
    }

    @Override
    public List<PlacedElement> execute(RoomEnvelope room, PlacementContext ctx) {
        String roomType = room.roomType().toUpperCase();

        List<FixtureInstance> instances;
        try {
            if (roomType.equals("BATHROOM")) {
                instances = placer.placeBathroomFixtures(
                    room.minX(), room.minY(), room.maxX(), room.maxY(),
                    ctx.storeyZ(), ctx.ceilingZ() + 0.05,
                    room.roomName()
                );
            } else if (roomType.contains("TOILET") || roomType.equals("WC")) {
                // Toilet block needs door wall and exterior walls from openings
                String doorWall = findDoorWall(room);
                List<String> exteriorWalls = findExteriorWalls(room);
                instances = placer.placeToiletBlockFixtures(
                    room.minX(), room.minY(), room.maxX(), room.maxY(),
                    ctx.storeyZ(), ctx.ceilingZ() + 0.05,
                    room.roomName(), doorWall, exteriorWalls
                );
            } else if (roomType.equals("KITCHEN")) {
                String exteriorWall = findExteriorWall(room);
                instances = placer.placeKitchenFixtures(
                    room.minX(), room.minY(), room.maxX(), room.maxY(),
                    ctx.storeyZ(), exteriorWall
                );
            } else {
                return List.of();
            }
        } catch (SQLException e) {
            System.err.println("[FixtureWorker] Placement failed for " + room.roomName() + ": " + e.getMessage());
            return List.of();
        }

        // Convert FixtureInstance → PlacedElement
        List<PlacedElement> result = new ArrayList<>(instances.size());
        for (FixtureInstance fi : instances) {
            result.add(new PlacedElement(
                fi.name(),
                "IfcBuildingElementProxy",   // fixtures use proxy IFC class
                fi.worldPosition().x(),
                fi.worldPosition().y(),
                fi.worldPosition().z(),
                fi.localBounds().width(),
                fi.localBounds().depth(),
                fi.localBounds().height(),
                fi.rotation(),
                fi.geometryHash(),
                fi.type().name(),            // role = FixtureType enum name
                assemblyId
            ));
        }
        return result;
    }

    // =========================================================================
    // Helper methods — extract context from RoomEnvelope openings
    // =========================================================================

    /**
     * Find the door wall from room openings.
     * Mirrors StoreyCompiler.findDoorWall logic.
     */
    private static String findDoorWall(RoomEnvelope room) {
        if (room.openings() == null) return null;
        for (OpeningInfo opening : room.openings()) {
            if ("DOOR".equalsIgnoreCase(opening.type())) {
                return opening.wall();
            }
        }
        return null;
    }

    /**
     * Find exterior walls from room openings marked as EXTERIOR.
     */
    private static List<String> findExteriorWalls(RoomEnvelope room) {
        List<String> walls = new ArrayList<>();
        if (room.openings() == null) return walls;
        for (OpeningInfo opening : room.openings()) {
            if ("EXTERIOR".equalsIgnoreCase(opening.type())) {
                walls.add(opening.wall());
            }
        }
        return walls;
    }

    /**
     * Find the exterior wall for kitchen sink placement.
     */
    private static String findExteriorWall(RoomEnvelope room) {
        if (room.openings() == null) return null;
        for (OpeningInfo opening : room.openings()) {
            if ("WINDOW".equalsIgnoreCase(opening.type()) || "EXTERIOR".equalsIgnoreCase(opening.type())) {
                return opening.wall();
            }
        }
        return null;
    }
}
