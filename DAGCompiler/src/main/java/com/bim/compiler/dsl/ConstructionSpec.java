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
     * Phase 47E: Added unitId for IfcZone grouping in multi-unit buildings.
     */
    public record SpaceSpec(
        String name,
        RoomType type,
        double minX,
        double maxX,
        double minY,
        double maxY,
        double height,
        String unitId  // Optional: null for single-unit, "A"/"B"/etc. for multi-unit
    ) {
        /**
         * Constructor for single-unit buildings (backward compatibility).
         */
        public SpaceSpec(String name, RoomType type, double minX, double maxX,
                         double minY, double maxY, double height) {
            this(name, type, minX, maxX, minY, maxY, height, null);
        }
    }

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
