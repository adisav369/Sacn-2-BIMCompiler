/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.builder;

import com.bim.compiler.geometry.BoundingBox;
import com.bim.compiler.geometry.Point3D;
import com.bim.compiler.model.ISpatialElement;
import com.bim.compiler.topology.BIMObjectType;

/**
 * Specification for an opening (door/window hole) in a wall.
 * From PATTERN G7: Opening-to-Wall Ratio
 * - avg width: 55% of wall length
 * - avg height: 63.5% of wall height
 *
 * @param offsetAlongWall distance from wall start to opening center (meters)
 * @param offsetFromFloor distance from floor to opening bottom (meters)
 * @param width opening width (meters)
 * @param height opening height (meters)
 * @param type IFC_DOOR, IFC_WINDOW, or IFC_OPENING_ELEMENT
 */
public record OpeningSpec(
    double offsetAlongWall,
    double offsetFromFloor,
    double width,
    double height,
    BIMObjectType type
) {

    /**
     * Extract opening spec from an existing opening element relative to its host wall.
     *
     * @param opening the opening element
     * @param wallStart the wall start point
     * @param wallDirection unit vector along wall length
     * @param wallBottom Z coordinate of wall bottom
     * @return the opening specification
     */
    public static OpeningSpec extractFrom(ISpatialElement opening,
                                          Point3D wallStart,
                                          Point3D wallDirection,
                                          double wallBottom) {
        BoundingBox bbox = opening.getBoundingBox();
        Point3D center = bbox.center();

        // Calculate offset along wall (project center onto wall axis)
        double dx = center.x() - wallStart.x();
        double dy = center.y() - wallStart.y();
        double offsetAlongWall = dx * wallDirection.x() + dy * wallDirection.y();

        // Offset from floor
        double offsetFromFloor = bbox.minZ() - wallBottom;

        // Opening dimensions
        double width = Math.max(bbox.width(), bbox.depth()); // Width is larger horizontal dim
        double height = bbox.height();

        return new OpeningSpec(
            offsetAlongWall,
            offsetFromFloor,
            width,
            height,
            opening.getType()
        );
    }

    /**
     * Get the bounding box for this opening when placed in a wall.
     *
     * @param wallStart wall start point
     * @param wallDirection unit vector along wall length
     * @param wallThickness wall thickness in meters
     * @param wallBottom Z coordinate of wall bottom
     * @return bounding box for the opening
     */
    public BoundingBox toBoundingBox(Point3D wallStart,
                                      Point3D wallDirection,
                                      double wallThickness,
                                      double wallBottom) {
        // Center position along wall
        double centerX = wallStart.x() + wallDirection.x() * offsetAlongWall;
        double centerY = wallStart.y() + wallDirection.y() * offsetAlongWall;
        double centerZ = wallBottom + offsetFromFloor + height / 2;

        // Opening is aligned with wall - thin dimension matches wall thickness
        double halfWidth = width / 2;
        double halfThickness = wallThickness / 2;
        double halfHeight = height / 2;

        // Determine opening orientation from wall direction
        if (Math.abs(wallDirection.x()) > Math.abs(wallDirection.y())) {
            // Wall runs along X, opening thin in Y
            return new BoundingBox(
                centerX - halfWidth, centerX + halfWidth,
                centerY - halfThickness, centerY + halfThickness,
                centerZ - halfHeight, centerZ + halfHeight
            );
        } else {
            // Wall runs along Y, opening thin in X
            return new BoundingBox(
                centerX - halfThickness, centerX + halfThickness,
                centerY - halfWidth, centerY + halfWidth,
                centerZ - halfHeight, centerZ + halfHeight
            );
        }
    }
}
