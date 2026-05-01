/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.model;

/**
 * Level 3: Geometric - "Exact shape, vertices, faces"
 * Maps to base_geometries table.
 *
 * From FACT 10 and CODE PATTERN 7:
 * - Vertices: float32[3] per vertex (x, y, z) = 12 bytes/vertex
 * - Faces: int32[3] per face (v1, v2, v3 indices) = 12 bytes/face
 *
 * From PATTERN 7: Geometry complexity varies by type:
 * - IfcPlate: 24 vertices (simple)
 * - IfcPipeSegment: 56 vertices (cylinder)
 * - IfcFireSuppressionTerminal: 2,000+ vertices (detailed)
 * - IfcBuildingElementProxy: up to 79,003 vertices (complex custom)
 *
 * From PATTERN 9: Geometry instancing
 * - 22,899 unique shapes
 * - 2.14x reuse ratio (same geometry shared by multiple elements)
 */
public interface IGeometricElement extends ISpatialElement {

    /**
     * Get vertex count.
     * From base_geometries.vertex_count.
     * @return number of vertices
     */
    int getVertexCount();

    /**
     * Get face count.
     * From base_geometries.face_count.
     * @return number of triangular faces
     */
    int getFaceCount();

    /**
     * Get vertex data as float array.
     * Format: [x1, y1, z1, x2, y2, z2, ...] in meters.
     * From base_geometries.vertices blob (3 floats per vertex).
     * @return vertex coordinates, length = vertexCount * 3
     */
    float[] getVertices();

    /**
     * Get face indices as int array.
     * Format: [v1, v2, v3, v4, v5, v6, ...] (triangle vertex indices).
     * From base_geometries.faces blob (3 ints per face).
     * @return face indices, length = faceCount * 3
     */
    int[] getFaces();

    /**
     * Check if this geometry is shared (instanced).
     * From PATTERN 9: 2.14x reuse ratio.
     * @return true if other elements share the same geometry hash
     */
    boolean isInstanced();

    /**
     * Get instance count for this geometry.
     * @return number of elements sharing this geometry
     */
    int getInstanceCount();
}
