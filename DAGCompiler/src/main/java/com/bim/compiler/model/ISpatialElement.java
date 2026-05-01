/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.model;

import com.bim.compiler.geometry.BoundingBox;
import com.bim.compiler.geometry.Point3D;
import com.bim.compiler.topology.BIMObjectType;
import com.bim.compiler.topology.Discipline;

import java.util.List;

/**
 * Level 2: Spatial - "Where is it? What does it touch?"
 * Maps to elements_rtree and element_transforms tables.
 *
 * CRITICAL DESIGN DECISION (from RELATIONSHIP PATTERNS):
 * Relationships are INFERRED via spatial overlap, NOT explicit storage.
 * - Opening-Wall: 98% overlap via bbox query
 * - Wall-Wall connections: 1,194 pairs via bbox overlap
 * - Door-Opening: via bbox overlap
 *
 * Do NOT add methods like getHostWallId() - they don't exist in DB.
 */
public interface ISpatialElement extends IBIMElement {

    /**
     * Axis-aligned bounding box.
     * From elements_rtree table.
     * @return the bounding box in world coordinates (meters)
     */
    BoundingBox getBoundingBox();

    /**
     * Transform position (equals bbox center).
     * From element_transforms table.
     * From FACT 6: Transform = BBox center, no offset.
     * @return position in world coordinates (meters)
     */
    Point3D getPosition();

    // =========================================================================
    // SPATIAL RELATIONSHIP QUERIES
    // These methods enable the inferred relationships from DB analysis.
    // =========================================================================

    /**
     * Find elements whose bboxes overlap this element's bbox.
     * From FACT 9: Wall-wall connections = 1,194 pairs via overlap.
     * @return list of overlapping elements
     */
    List<ISpatialElement> findOverlapping();

    /**
     * Find elements of a specific type whose bboxes overlap.
     * Usage: opening.findOverlapping(IFC_WALL) → host wall
     * @param type the type to filter by
     * @return list of overlapping elements of that type
     */
    List<ISpatialElement> findOverlapping(BIMObjectType type);

    /**
     * Find elements of a specific discipline whose bboxes overlap.
     * Usage: slab.findOverlapping(Discipline.SP) → pipes through slab
     * @param discipline the discipline to filter by
     * @return list of overlapping elements of that discipline
     */
    List<ISpatialElement> findOverlapping(Discipline discipline);

    /**
     * Find elements that contain this element (this is inside them).
     * Usage: door.findContaining() → opening → wall
     * @return list of elements that contain this one
     */
    List<ISpatialElement> findContaining();

    /**
     * Find elements contained within this element (inside this bbox).
     * Usage: wall.findContained() → openings in wall
     * @return list of elements inside this one
     */
    List<ISpatialElement> findContained();

    /**
     * Calculate vertical clearance to nearest element above.
     * From RULE 1: MEP-structure clearance >= 50mm.
     * @param above element above this one
     * @return clearance in meters (negative if overlapping)
     */
    default double verticalClearanceTo(ISpatialElement above) {
        return getBoundingBox().verticalGapTo(above.getBoundingBox());
    }

    /**
     * Check if this element spans multiple stories.
     * From FACT 5: 590 rebar, 236 slabs, 90 columns span >8m.
     * @return true if height exceeds 8m
     */
    default boolean isMultiStory() {
        return getBoundingBox().isMultiStory();
    }
}
