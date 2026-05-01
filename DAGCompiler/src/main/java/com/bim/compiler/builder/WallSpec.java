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
import com.bim.compiler.topology.WallThickness;

import java.util.ArrayList;
import java.util.List;

/**
 * Specification for generating a wall.
 * Extracted from existing walls or created programmatically.
 *
 * From FACT 3: Wall thickness is one of 4 values (150/230/250/300mm)
 * From PATTERN G1: Placement anchor = CENTER
 * From PATTERN G2: Orientation from thin dimension
 * From PATTERN G4: Walls overlap by ~133mm at corners
 *
 * @param start start point (center of wall at floor level)
 * @param end end point (center of wall at floor level)
 * @param thickness wall thickness (one of 4 standard values)
 * @param height wall height in meters
 * @param openings list of openings in this wall
 */
public record WallSpec(
    Point3D start,
    Point3D end,
    WallThickness thickness,
    double height,
    List<OpeningSpec> openings
) {

    /**
     * Calculate wall length.
     */
    public double length() {
        return start.horizontalDistanceTo(end);
    }

    /**
     * Get unit direction vector along wall.
     */
    public Point3D direction() {
        double len = length();
        if (len < 0.001) return new Point3D(1, 0, 0);
        return new Point3D(
            (end.x() - start.x()) / len,
            (end.y() - start.y()) / len,
            0
        );
    }

    /**
     * Get the bounding box for this wall.
     */
    public BoundingBox toBoundingBox() {
        double halfThickness = thickness.getMeters() / 2;
        Point3D dir = direction();

        // Perpendicular direction for thickness
        double perpX = -dir.y();
        double perpY = dir.x();

        // Wall corners in plan
        double x1 = start.x() + perpX * halfThickness;
        double x2 = start.x() - perpX * halfThickness;
        double x3 = end.x() + perpX * halfThickness;
        double x4 = end.x() - perpX * halfThickness;

        double y1 = start.y() + perpY * halfThickness;
        double y2 = start.y() - perpY * halfThickness;
        double y3 = end.y() + perpY * halfThickness;
        double y4 = end.y() - perpY * halfThickness;

        double minX = Math.min(Math.min(x1, x2), Math.min(x3, x4));
        double maxX = Math.max(Math.max(x1, x2), Math.max(x3, x4));
        double minY = Math.min(Math.min(y1, y2), Math.min(y3, y4));
        double maxY = Math.max(Math.max(y1, y2), Math.max(y3, y4));

        double minZ = start.z();
        double maxZ = start.z() + height;

        return new BoundingBox(minX, maxX, minY, maxY, minZ, maxZ);
    }

    /**
     * Extract wall spec from an existing wall element.
     *
     * @param wall the existing wall element
     * @param overlappingOpenings openings that overlap this wall
     * @return the wall specification
     */
    public static WallSpec extractFrom(ISpatialElement wall,
                                        List<ISpatialElement> overlappingOpenings) {
        if (wall.getType() != BIMObjectType.IFC_WALL) {
            throw new IllegalArgumentException("Element is not a wall: " + wall.getType());
        }

        BoundingBox bbox = wall.getBoundingBox();

        // Determine wall orientation from thin dimension
        double w = bbox.width();
        double d = bbox.depth();

        Point3D start, end;
        double length;

        if (w > d) {
            // Wall runs along X axis (thin in Y)
            length = w;
            double centerY = (bbox.minY() + bbox.maxY()) / 2;
            start = new Point3D(bbox.minX(), centerY, bbox.minZ());
            end = new Point3D(bbox.maxX(), centerY, bbox.minZ());
        } else {
            // Wall runs along Y axis (thin in X)
            length = d;
            double centerX = (bbox.minX() + bbox.maxX()) / 2;
            start = new Point3D(centerX, bbox.minY(), bbox.minZ());
            end = new Point3D(centerX, bbox.maxY(), bbox.minZ());
        }

        // Thickness is the thin dimension
        double thicknessMeters = Math.min(w, d);
        WallThickness thickness = WallThickness.fromMeasurement(thicknessMeters);
        if (thickness == null) {
            thickness = WallThickness.MM_150; // Default if non-standard
        }

        double height = bbox.height();

        // Extract openings
        List<OpeningSpec> openings = new ArrayList<>();
        Point3D dir = new Point3D(
            (end.x() - start.x()) / length,
            (end.y() - start.y()) / length,
            0
        );

        for (ISpatialElement opening : overlappingOpenings) {
            if (opening.getType() == BIMObjectType.IFC_OPENING_ELEMENT) {
                openings.add(OpeningSpec.extractFrom(opening, start, dir, bbox.minZ()));
            }
        }

        return new WallSpec(start, end, thickness, height, openings);
    }

    /**
     * Extract wall spec from existing wall, finding overlapping openings.
     *
     * @param wall the existing wall element
     * @param allElements all elements for spatial query
     * @return the wall specification
     */
    public static WallSpec extractFrom(ISpatialElement wall,
                                        java.util.function.Supplier<List<ISpatialElement>> allElements) {
        // Find overlapping openings
        List<ISpatialElement> openings = new ArrayList<>();
        BoundingBox wallBox = wall.getBoundingBox();

        for (ISpatialElement e : allElements.get()) {
            if (e.getType() == BIMObjectType.IFC_OPENING_ELEMENT) {
                if (wallBox.overlaps(e.getBoundingBox())) {
                    openings.add(e);
                }
            }
        }

        return extractFrom(wall, openings);
    }
}
