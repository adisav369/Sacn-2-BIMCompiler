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
import com.bim.compiler.topology.PipeDiameterRange;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds pipe segment elements from PipeSpec.
 *
 * From extracted patterns:
 * - FACT 4: Diameter ranges per discipline (CW, FP, LPG, SP)
 * - PATTERN G4: Pipe-to-fitting insertion depths
 * - PATTERN G5: Extrusion along longest bbox dimension
 * - PATTERN G10: Termination patterns (FP: 1.18 fittings/pipe)
 * - PATTERN 7: Pipe geometry = 56 vertices (cylinder approximation)
 */
public class PipeBuilder implements IBuilder<PipeSpec> {

    /** Number of segments for cylinder approximation (32 = standard) */
    private static final int CYLINDER_SEGMENTS = 32;

    private final List<ISpatialElement> generatedElements = new ArrayList<>();
    private final List<String> validationErrors = new ArrayList<>();

    @Override
    public ISpatialElement build(PipeSpec spec) {
        validationErrors.clear();

        // Validate diameter against discipline range
        if (!spec.isValidDiameter()) {
            PipeDiameterRange.Range range = PipeDiameterRange.getRange(spec.discipline());
            String rangeStr = range != null ?
                String.format("%.1f-%.1fmm", range.minMm(), range.maxMm()) : "unknown";
            validationErrors.add(String.format(
                "Diameter %.1fmm invalid for %s (valid: %s)",
                spec.diameterMm(), spec.discipline(), rangeStr));
        }

        String pipeGuid = generateGuid();
        BoundingBox pipeBox = spec.toBoundingBox();

        SpatialElement pipe = new SpatialElement(
            pipeGuid,
            BIMObjectType.IFC_PIPE_SEGMENT,
            spec.discipline(),
            "Generated Pipe " + String.format("%.0fmm", spec.diameterMm()),
            null,
            pipeBox
        );

        generatedElements.add(pipe);
        return pipe;
    }

    /**
     * Get all generated elements.
     */
    public List<ISpatialElement> getGeneratedElements() {
        return new ArrayList<>(generatedElements);
    }

    /**
     * Get validation errors from last build.
     */
    public List<String> getValidationErrors() {
        return new ArrayList<>(validationErrors);
    }

    /**
     * Check if last build was valid.
     */
    public boolean isValid() {
        return validationErrors.isEmpty();
    }

    /**
     * Clear generated elements.
     */
    public void clear() {
        generatedElements.clear();
        validationErrors.clear();
    }

    /**
     * Build pipe and validate it matches the original within tolerance.
     *
     * @param spec the pipe specification
     * @param original the original pipe to compare against
     * @param tolerance comparison tolerance in meters (default 0.005 = 5mm)
     * @return true if generated pipe matches original and diameter is valid
     */
    public boolean buildAndValidate(PipeSpec spec, ISpatialElement original, double tolerance) {
        ISpatialElement generated = build(spec);

        BoundingBox origBox = original.getBoundingBox();
        BoundingBox genBox = generated.getBoundingBox();

        boolean bboxMatch = bboxMatches(origBox, genBox, tolerance);
        return bboxMatch && validationErrors.isEmpty();
    }

    /**
     * Generate cylinder vertices for a pipe.
     *
     * Creates a cylinder approximated by CYLINDER_SEGMENTS sides.
     * From PATTERN 7: IfcPipeSegment = 56 vertices (28 per end cap).
     *
     * @param spec the pipe specification
     * @return float array of vertices [x1,y1,z1, x2,y2,z2, ...]
     */
    public float[] buildGeometry(PipeSpec spec) {
        double radius = spec.diameterMeters() / 2;
        Point3D dir = spec.direction();

        // Create perpendicular vectors for the circle
        Point3D perp1, perp2;
        if (Math.abs(dir.z()) < 0.9) {
            // Not vertical - use Z cross dir
            perp1 = normalize(cross(new Point3D(0, 0, 1), dir));
        } else {
            // Vertical - use X cross dir
            perp1 = normalize(cross(new Point3D(1, 0, 0), dir));
        }
        perp2 = normalize(cross(dir, perp1));

        // 2 circles (start and end) x CYLINDER_SEGMENTS points each
        int vertexCount = CYLINDER_SEGMENTS * 2;
        float[] vertices = new float[vertexCount * 3];

        int idx = 0;
        for (int end = 0; end < 2; end++) {
            Point3D center = end == 0 ? spec.start() : spec.end();

            for (int i = 0; i < CYLINDER_SEGMENTS; i++) {
                double angle = 2 * Math.PI * i / CYLINDER_SEGMENTS;
                double cos = Math.cos(angle);
                double sin = Math.sin(angle);

                // Point on circle: center + radius * (cos * perp1 + sin * perp2)
                float x = (float) (center.x() + radius * (cos * perp1.x() + sin * perp2.x()));
                float y = (float) (center.y() + radius * (cos * perp1.y() + sin * perp2.y()));
                float z = (float) (center.z() + radius * (cos * perp1.z() + sin * perp2.z()));

                vertices[idx++] = x;
                vertices[idx++] = y;
                vertices[idx++] = z;
            }
        }

        return vertices;
    }

    /**
     * Generate faces for a cylinder.
     *
     * @return int array of face indices
     */
    public int[] buildFaces() {
        // Side faces: CYLINDER_SEGMENTS quads = 2 * CYLINDER_SEGMENTS triangles
        // End caps: 2 * (CYLINDER_SEGMENTS - 2) triangles (fan triangulation)
        int sideFaces = CYLINDER_SEGMENTS * 2;
        int capFaces = (CYLINDER_SEGMENTS - 2) * 2;
        int totalFaces = sideFaces + capFaces;

        int[] faces = new int[totalFaces * 3];
        int idx = 0;

        // Side faces (connecting the two circles)
        for (int i = 0; i < CYLINDER_SEGMENTS; i++) {
            int i1 = i;
            int i2 = (i + 1) % CYLINDER_SEGMENTS;
            int j1 = i + CYLINDER_SEGMENTS;
            int j2 = ((i + 1) % CYLINDER_SEGMENTS) + CYLINDER_SEGMENTS;

            // Two triangles per quad
            faces[idx++] = i1;
            faces[idx++] = i2;
            faces[idx++] = j2;

            faces[idx++] = i1;
            faces[idx++] = j2;
            faces[idx++] = j1;
        }

        // Start cap (fan from vertex 0)
        for (int i = 1; i < CYLINDER_SEGMENTS - 1; i++) {
            faces[idx++] = 0;
            faces[idx++] = i + 1;
            faces[idx++] = i;
        }

        // End cap (fan from vertex CYLINDER_SEGMENTS)
        int offset = CYLINDER_SEGMENTS;
        for (int i = 1; i < CYLINDER_SEGMENTS - 1; i++) {
            faces[idx++] = offset;
            faces[idx++] = offset + i;
            faces[idx++] = offset + i + 1;
        }

        return faces;
    }

    /**
     * Build complete geometry data for a pipe.
     *
     * @param spec the pipe specification
     * @return GeometryData containing vertices and faces
     */
    public GeometryData buildCompleteGeometry(PipeSpec spec) {
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

    // Vector math helpers

    private Point3D cross(Point3D a, Point3D b) {
        return new Point3D(
            a.y() * b.z() - a.z() * b.y(),
            a.z() * b.x() - a.x() * b.z(),
            a.x() * b.y() - a.y() * b.x()
        );
    }

    private Point3D normalize(Point3D v) {
        double len = Math.sqrt(v.x() * v.x() + v.y() * v.y() + v.z() * v.z());
        if (len < 0.00001) return new Point3D(1, 0, 0);
        return new Point3D(v.x() / len, v.y() / len, v.z() / len);
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
}
