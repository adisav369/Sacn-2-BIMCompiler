package com.bim.compiler.dsl;

import com.bim.compiler.builder.OpeningSpec;
import com.bim.compiler.builder.WallSpec;
import com.bim.compiler.geometry.BoundingBox;

import java.util.List;

/**
 * Output of the room compiler.
 * Contains all construction elements for a storey.
 */
public record ConstructionSpec(
    String storeyName,
    double storeyHeight,
    List<WallSpec> walls,
    List<SpaceSpec> spaces,
    List<SprinklerGridSpec> sprinklerGrids
) {
    /**
     * Space specification for IFC export.
     */
    public record SpaceSpec(
        String name,
        RoomType type,
        double minX,
        double maxX,
        double minY,
        double maxY,
        double height
    ) {}

    /**
     * Sprinkler grid specification for library factory.
     * Uses GridPlacementSpec from factory package.
     */
    public record SprinklerGridSpec(
        String roomName,
        BoundingBox area,
        double spacing,
        double attachmentZ  // Ceiling height
    ) {}
}
