/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.contract;

import com.bim.compiler.geometry.Point3D;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Layer 0: Geometry Contract - Mesh primitive validity.
 *
 * <p>Validates mesh geometry BEFORE element becomes a BIM entity.
 * Separates topological correctness (exact, integer-based) from
 * geometric precision (bounded, float-based).
 *
 * <p>This is the foundational layer below the existing contract hierarchy:
 * <pre>
 * L5: Semantic (domain rules)
 * L4: Aggregation (merge/dedupe)
 * L3: Relationship (connect/host)
 * L2: Identity (unique/continuous)
 * L1: Existence (guid/bounds)
 * L0: GEOMETRY (mesh validity)  ← THIS LAYER
 * </pre>
 *
 * <p>Theory: Manifold topology, Euler characteristic
 * <p>References:
 * <ul>
 *   <li><a href="https://github.com/elalish/manifold/wiki/Manifold-Library">Manifold Library</a></li>
 *   <li><a href="https://doc.cgal.org/latest/Polygon_mesh_processing/index.html">CGAL Polygon Mesh</a></li>
 *   <li><a href="https://max-limper.de/a_euler.html">Euler Characteristic</a></li>
 * </ul>
 *
 * <p>Key insight from Manifold: "Topological exactness over geometric precision"
 * - Topology (integer connectivity) is EXACT and can be proven correct
 * - Geometry (float positions) is INEXACT but bounded by epsilon
 *
 * @see IBIMEntity Layer 1 contract (requires Layer 0 to be valid first)
 */
public interface IGeometryValidatable {

    // ========== Data Access ==========

    /**
     * Get the mesh vertices in world coordinates.
     *
     * @return List of 3D points (vertices)
     */
    List<Point3D> vertices();

    /**
     * Get the mesh faces as vertex index arrays.
     * Each face is typically a triangle (3 indices) or quad (4 indices).
     *
     * @return List of face index arrays
     */
    List<int[]> faces();

    // ========== Topological Checks (Exact) ==========

    /**
     * Check that all face indices reference valid vertices.
     *
     * <p>Contract: For each face, all indices must be in range [0, vertices.size()).
     *
     * @return true if all indices are valid
     */
    default boolean hasValidIndices() {
        List<Point3D> verts = vertices();
        List<int[]> faceList = faces();
        if (verts == null || faceList == null) {
            return false;  // Null geometry is invalid
        }
        int vertexCount = verts.size();
        for (int i = 0; i < faceList.size(); i++) {
            int[] face = faceList.get(i);
            if (face == null) {
                System.err.println("  hasValidIndices: face[" + i + "] is null");
                return false;
            }
            for (int j = 0; j < face.length; j++) {
                int idx = face[j];
                if (idx < 0 || idx >= vertexCount) {
                    System.err.println("  hasValidIndices: face[" + i + "][" + j + "] = " + idx + " >= vertexCount " + vertexCount);
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Compute the Euler characteristic: χ = V + F - E
     *
     * <p>For closed manifolds:
     * <ul>
     *   <li>Sphere (no holes): χ = 2</li>
     *   <li>Torus (1 hole): χ = 0</li>
     *   <li>With genus g: χ = 2 - 2g</li>
     * </ul>
     *
     * <p>Use: Fast topological sanity check before expensive operations.
     *
     * @return Euler characteristic value
     */
    default int eulerCharacteristic() {
        List<Point3D> verts = vertices();
        List<int[]> faceList = faces();
        if (verts == null || faceList == null) {
            return Integer.MIN_VALUE;  // Signal invalid geometry
        }
        int V = verts.size();
        int F = faceList.size();
        int E = countUniqueEdges();
        return V + F - E;
    }

    /**
     * Check if mesh is manifold (each edge shared by exactly 2 faces).
     *
     * <p>Non-manifold meshes cause problems with:
     * <ul>
     *   <li>Boolean operations</li>
     *   <li>Volume calculations</li>
     *   <li>3D printing</li>
     * </ul>
     *
     * @return true if mesh is 2-manifold
     */
    default boolean isManifold() {
        List<int[]> faceList = faces();
        if (faceList == null) {
            return false;  // Null geometry is invalid
        }

        // Count edge occurrences
        java.util.Map<String, Integer> edgeCounts = new java.util.HashMap<>();

        for (int[] face : faceList) {
            int n = face.length;
            for (int i = 0; i < n; i++) {
                int a = face[i];
                int b = face[(i + 1) % n];
                // Canonical edge key (smaller index first)
                String key = a < b ? a + "_" + b : b + "_" + a;
                edgeCounts.merge(key, 1, Integer::sum);
            }
        }

        // For closed manifold, each edge appears exactly twice
        // For open manifold with boundary, boundary edges appear once
        for (int count : edgeCounts.values()) {
            if (count > 2) {
                return false;  // Edge shared by more than 2 faces = non-manifold
            }
        }
        return true;
    }

    // ========== Geometric Checks (Bounded) ==========

    /**
     * Check that no faces are degenerate (zero area).
     *
     * <p>Degenerate faces occur when:
     * <ul>
     *   <li>Two or more vertices are identical</li>
     *   <li>All vertices are collinear</li>
     * </ul>
     *
     * @return true if no degenerate faces exist
     */
    default boolean hasNoDegenerateFaces() {
        double EPSILON = 1e-9;
        List<Point3D> verts = vertices();
        List<int[]> faceList = faces();
        if (verts == null || faceList == null) {
            return false;  // Null geometry is invalid
        }

        for (int[] face : faceList) {
            if (face.length < 3) {
                return false;  // Not a valid face
            }

            // For triangles, check area via cross product
            Point3D v0 = verts.get(face[0]);
            Point3D v1 = verts.get(face[1]);
            Point3D v2 = verts.get(face[2]);

            // Edge vectors
            double ax = v1.x() - v0.x();
            double ay = v1.y() - v0.y();
            double az = v1.z() - v0.z();

            double bx = v2.x() - v0.x();
            double by = v2.y() - v0.y();
            double bz = v2.z() - v0.z();

            // Cross product (normal)
            double nx = ay * bz - az * by;
            double ny = az * bx - ax * bz;
            double nz = ax * by - ay * bx;

            // Area = 0.5 * |cross product|
            double areaSq = nx * nx + ny * ny + nz * nz;
            if (areaSq < EPSILON) {
                return false;  // Degenerate triangle
            }
        }
        return true;
    }

    /**
     * Check that face winding is consistent (all normals point same direction).
     *
     * <p>Inconsistent winding causes:
     * <ul>
     *   <li>Inverted normals</li>
     *   <li>Backface culling issues</li>
     *   <li>Incorrect lighting</li>
     * </ul>
     *
     * <p>For a closed mesh, normals should all point outward (or all inward).
     * This method checks that adjacent faces have consistent orientation.
     *
     * @return true if winding is consistent
     */
    default boolean hasConsistentWinding() {
        List<int[]> faceList = faces();
        if (faceList == null) {
            return false;  // Null geometry is invalid
        }

        // Build edge-to-face map with direction
        // For consistent winding, edge (a,b) in face1 should be (b,a) in face2
        java.util.Map<String, List<int[]>> directedEdges = new java.util.HashMap<>();

        for (int[] face : faceList) {
            int n = face.length;
            for (int i = 0; i < n; i++) {
                int a = face[i];
                int b = face[(i + 1) % n];
                String key = a + "_" + b;  // Directed edge
                directedEdges.computeIfAbsent(key, k -> new ArrayList<>())
                    .add(new int[]{a, b});
            }
        }

        // Check each edge pair
        for (String key : directedEdges.keySet()) {
            List<int[]> edges = directedEdges.get(key);
            if (edges.size() > 1) {
                // Same directed edge appears multiple times = inconsistent winding
                return false;
            }

            // Check for reverse edge
            int a = edges.get(0)[0];
            int b = edges.get(0)[1];
            String reverseKey = b + "_" + a;
            List<int[]> reverseEdges = directedEdges.get(reverseKey);

            // For closed manifold: each edge should have exactly one reverse
            // For open mesh: boundary edges have no reverse
            if (reverseEdges != null && reverseEdges.size() > 1) {
                return false;  // Multiple reverse edges = inconsistent
            }
        }

        return true;
    }

    // ========== Combined Validation ==========

    /**
     * Perform complete geometry validation.
     *
     * <p>Returns list of violations found. Empty list means mesh is valid.
     * Violations are ordered by severity (errors first).
     *
     * @return List of geometry violations (empty if valid)
     */
    default List<GeometryViolation> validateGeometry() {
        List<GeometryViolation> violations = new ArrayList<>();

        // Check indices first (other checks depend on valid indices)
        if (!hasValidIndices()) {
            violations.add(GeometryViolation.error("FACE_INDEX_OUT_OF_BOUNDS",
                "Face index references non-existent vertex"));
            return violations;  // Can't continue with invalid indices
        }

        // Topological checks
        if (!isManifold()) {
            violations.add(GeometryViolation.error("NON_MANIFOLD_MESH",
                "Edge shared by more than 2 faces"));
        }

        // Geometric checks
        if (!hasNoDegenerateFaces()) {
            violations.add(GeometryViolation.warning("DEGENERATE_FACE",
                "Face has zero or near-zero area"));
        }

        if (!hasConsistentWinding()) {
            violations.add(GeometryViolation.error("INCONSISTENT_WINDING",
                "Face winding is inconsistent (normals point different directions)"));
        }

        return violations;
    }

    /**
     * Check if geometry is completely valid (no errors).
     *
     * @return true if geometry passes all validation checks
     */
    default boolean isGeometryValid() {
        return validateGeometry().stream()
            .noneMatch(GeometryViolation::isError);
    }

    // ========== Helper Methods ==========

    /**
     * Count unique edges in the mesh.
     * Used for Euler characteristic calculation.
     */
    private int countUniqueEdges() {
        Set<String> edges = new HashSet<>();
        for (int[] face : faces()) {
            int n = face.length;
            for (int i = 0; i < n; i++) {
                int a = face[i];
                int b = face[(i + 1) % n];
                // Canonical edge key
                String key = a < b ? a + "_" + b : b + "_" + a;
                edges.add(key);
            }
        }
        return edges.size();
    }
}
