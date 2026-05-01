/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.builder;

import com.bim.compiler.geometry.BoundingBox;
import com.bim.compiler.geometry.Point3D;
import com.bim.compiler.model.ISpatialElement;
import com.bim.compiler.model.SpatialElement;
import com.bim.compiler.topology.BIMObjectType;
import com.bim.compiler.topology.Discipline;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds wall elements from WallSpec.
 *
 * From extracted patterns:
 * - FACT 3: Thickness is one of 4 values
 * - PATTERN G1: Anchor = CENTER
 * - PATTERN G2: Orientation from thin dimension
 * - PATTERN G5: Extrusion = Z-up (61%)
 */
public class WallBuilder implements IBuilder<WallSpec> {

    private final List<ISpatialElement> generatedElements = new ArrayList<>();

    @Override
    public ISpatialElement build(WallSpec spec) {
        // Generate wall element
        String wallGuid = generateGuid();
        BoundingBox wallBox = spec.toBoundingBox();

        SpatialElement wall = new SpatialElement(
            wallGuid,
            BIMObjectType.IFC_WALL,
            Discipline.ARC,
            "Generated Wall " + spec.thickness().getMillimeters() + "mm",
            null, // No geometry hash for generated
            wallBox
        );

        generatedElements.add(wall);

        // Generate openings
        for (OpeningSpec openingSpec : spec.openings()) {
            ISpatialElement opening = buildOpening(spec, openingSpec);
            generatedElements.add(opening);
        }

        return wall;
    }

    /**
     * Build an opening element within a wall.
     */
    private ISpatialElement buildOpening(WallSpec wallSpec, OpeningSpec openingSpec) {
        String openingGuid = generateGuid();

        Point3D dir = wallSpec.direction();
        BoundingBox openingBox = openingSpec.toBoundingBox(
            wallSpec.start(),
            new Point3D(dir.x(), dir.y(), dir.z()),
            wallSpec.thickness().getMeters(),
            wallSpec.start().z()
        );

        return new SpatialElement(
            openingGuid,
            BIMObjectType.IFC_OPENING_ELEMENT,
            Discipline.ARC,
            "Generated Opening",
            null,
            openingBox
        );
    }

    /**
     * Get all generated elements (wall + openings).
     */
    public List<ISpatialElement> getGeneratedElements() {
        return new ArrayList<>(generatedElements);
    }

    /**
     * Clear generated elements.
     */
    public void clear() {
        generatedElements.clear();
    }

    /**
     * Build wall and validate it matches the original within tolerance.
     *
     * @param spec the wall specification
     * @param original the original wall to compare against
     * @param tolerance comparison tolerance in meters (default 0.005 = 5mm)
     * @return true if generated wall matches original
     */
    public boolean buildAndValidate(WallSpec spec, ISpatialElement original, double tolerance) {
        ISpatialElement generated = build(spec);

        BoundingBox origBox = original.getBoundingBox();
        BoundingBox genBox = generated.getBoundingBox();

        return bboxMatches(origBox, genBox, tolerance);
    }

    /**
     * Check if two bounding boxes match within tolerance.
     */
    public static boolean bboxMatches(BoundingBox a, BoundingBox b, double tolerance) {
        return Math.abs(a.minX() - b.minX()) <= tolerance
            && Math.abs(a.maxX() - b.maxX()) <= tolerance
            && Math.abs(a.minY() - b.minY()) <= tolerance
            && Math.abs(a.maxY() - b.maxY()) <= tolerance
            && Math.abs(a.minZ() - b.minZ()) <= tolerance
            && Math.abs(a.maxZ() - b.maxZ()) <= tolerance;
    }

    /**
     * Generate vertices for a wall geometry (simple box without openings).
     *
     * A wall is a box with 8 vertices:
     * - 4 corners at bottom (minZ)
     * - 4 corners at top (maxZ)
     *
     * From PATTERN G5: Wall extrusion is Z-up (61%)
     * From PATTERN G1: Placement anchor = CENTER
     *
     * @param spec the wall specification
     * @return float array of vertices [x1,y1,z1, x2,y2,z2, ...]
     */
    public float[] buildGeometry(WallSpec spec) {
        double halfThickness = spec.thickness().getMeters() / 2;
        Point3D dir = spec.direction();

        // Perpendicular direction for thickness
        double perpX = -dir.y();
        double perpY = dir.x();

        // 4 corners in plan (at start and end, offset by thickness)
        double x0 = spec.start().x() - perpX * halfThickness;
        double y0 = spec.start().y() - perpY * halfThickness;
        double x1 = spec.start().x() + perpX * halfThickness;
        double y1 = spec.start().y() + perpY * halfThickness;
        double x2 = spec.end().x() + perpX * halfThickness;
        double y2 = spec.end().y() + perpY * halfThickness;
        double x3 = spec.end().x() - perpX * halfThickness;
        double y3 = spec.end().y() - perpY * halfThickness;

        double minZ = spec.start().z();
        double maxZ = spec.start().z() + spec.height();

        // 8 vertices: 4 bottom + 4 top
        // Ordered: bottom (0,1,2,3), top (4,5,6,7)
        float[] vertices = new float[8 * 3];

        // Bottom vertices (z = minZ)
        vertices[0]  = (float) x0; vertices[1]  = (float) y0; vertices[2]  = (float) minZ;  // 0
        vertices[3]  = (float) x1; vertices[4]  = (float) y1; vertices[5]  = (float) minZ;  // 1
        vertices[6]  = (float) x2; vertices[7]  = (float) y2; vertices[8]  = (float) minZ;  // 2
        vertices[9]  = (float) x3; vertices[10] = (float) y3; vertices[11] = (float) minZ;  // 3

        // Top vertices (z = maxZ)
        vertices[12] = (float) x0; vertices[13] = (float) y0; vertices[14] = (float) maxZ;  // 4
        vertices[15] = (float) x1; vertices[16] = (float) y1; vertices[17] = (float) maxZ;  // 5
        vertices[18] = (float) x2; vertices[19] = (float) y2; vertices[20] = (float) maxZ;  // 6
        vertices[21] = (float) x3; vertices[22] = (float) y3; vertices[23] = (float) maxZ;  // 7

        return vertices;
    }

    /**
     * Generate faces for a wall geometry (simple box without openings).
     *
     * 6 faces x 2 triangles each = 12 triangles = 36 indices
     *
     * @return int array of face indices
     */
    public int[] buildFaces() {
        // 12 triangles for a box
        // Face order: bottom, top, front, back, left, right
        return new int[] {
            // Bottom (z = minZ): vertices 0,1,2,3
            0, 2, 1,
            0, 3, 2,
            // Top (z = maxZ): vertices 4,5,6,7
            4, 5, 6,
            4, 6, 7,
            // Front (y = y0): vertices 0,1,4,5
            0, 1, 5,
            0, 5, 4,
            // Back (y = y1): vertices 2,3,6,7
            2, 3, 7,
            2, 7, 6,
            // Left (start): vertices 0,3,4,7
            0, 4, 7,
            0, 7, 3,
            // Right (end): vertices 1,2,5,6
            1, 2, 6,
            1, 6, 5
        };
    }

    /**
     * Build complete geometry data for a wall.
     *
     * @param spec the wall specification
     * @return GeometryData containing vertices and faces
     */
    public GeometryData buildCompleteGeometry(WallSpec spec) {
        float[] vertices = buildGeometry(spec);
        int[] faces = buildFaces();
        return new GeometryData(vertices, faces);
    }

    /**
     * Simple record to hold vertices and faces.
     */
    public record GeometryData(float[] vertices, int[] faces) {
        public int vertexCount() {
            return vertices.length / 3;
        }

        public int faceCount() {
            return faces.length / 3;
        }
    }
}
