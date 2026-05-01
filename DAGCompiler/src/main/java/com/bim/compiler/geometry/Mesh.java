/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.geometry;

import java.util.*;

/**
 * Immutable mesh primitive: vertices + faces.
 *
 * Key design principles (from Manifold library research):
 * - Topology (face indices) is EXACT - integer operations
 * - Geometry (vertex positions) is INEXACT - bounded by epsilon
 * - Validation separates topological correctness from geometric quality
 *
 * All meshes are assumed to be triangulated (faces are int[3]).
 * Face winding is CCW from outside (right-hand rule for normals).
 */
public record Mesh(
    List<Point3D> vertices,
    List<int[]> faces
) {
    /**
     * Compact constructor with validation.
     */
    public Mesh {
        vertices = List.copyOf(vertices);
        faces = List.copyOf(faces);
    }

    // =========================================================================
    // Topological Queries (Exact)
    // =========================================================================

    /**
     * Vertex count.
     */
    public int vertexCount() {
        return vertices.size();
    }

    /**
     * Face count.
     */
    public int faceCount() {
        return faces.size();
    }

    /**
     * Edge count (for Euler characteristic).
     * Each face has 3 edges, but shared edges are counted once.
     */
    public int edgeCount() {
        Set<String> edges = new HashSet<>();
        for (int[] face : faces) {
            for (int i = 0; i < face.length; i++) {
                int v1 = face[i];
                int v2 = face[(i + 1) % face.length];
                // Canonical edge key (smaller index first)
                String key = Math.min(v1, v2) + "-" + Math.max(v1, v2);
                edges.add(key);
            }
        }
        return edges.size();
    }

    /**
     * Euler characteristic: χ = V - E + F
     * For closed manifold: χ = 2
     * For open mesh (with boundary): χ = 2 - 2g - b (g=genus, b=boundaries)
     */
    public int eulerCharacteristic() {
        return vertexCount() - edgeCount() + faceCount();
    }

    /**
     * Check if mesh is topologically valid (all face indices in range).
     */
    public boolean hasValidIndices() {
        int n = vertices.size();
        for (int[] face : faces) {
            for (int idx : face) {
                if (idx < 0 || idx >= n) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Check if mesh is 2-manifold (each edge shared by exactly 2 faces).
     * Non-manifold edges indicate T-junctions or other invalid topology.
     */
    public boolean isManifold() {
        Map<String, Integer> edgeCount = new HashMap<>();
        for (int[] face : faces) {
            for (int i = 0; i < face.length; i++) {
                int v1 = face[i];
                int v2 = face[(i + 1) % face.length];
                // Directed edge key (tracks winding)
                String key = v1 + "->" + v2;
                edgeCount.merge(key, 1, Integer::sum);
            }
        }

        // For each directed edge v1->v2, there should be exactly one v2->v1
        // (manifold condition: each edge shared by exactly 2 faces with opposite winding)
        for (Map.Entry<String, Integer> entry : edgeCount.entrySet()) {
            String[] parts = entry.getKey().split("->");
            String reverseKey = parts[1] + "->" + parts[0];
            Integer fwdCount = entry.getValue();
            Integer revCount = edgeCount.get(reverseKey);

            // Both should exist and be exactly 1 for closed manifold
            if (fwdCount != 1 || revCount == null || revCount != 1) {
                // Could be boundary edge (open manifold) - fwd=1, rev=null
                // Non-manifold if fwd > 1 or both fwd and rev exist but > 1
                if (fwdCount > 1) return false;
            }
        }
        return true;
    }

    // =========================================================================
    // Geometric Queries (Bounded)
    // =========================================================================

    /**
     * Check if mesh has no degenerate (zero-area) faces.
     */
    public boolean hasNoDegenerateFaces() {
        double eps = 1e-10;
        for (int[] face : faces) {
            if (face.length < 3) return false;
            Point3D p0 = vertices.get(face[0]);
            Point3D p1 = vertices.get(face[1]);
            Point3D p2 = vertices.get(face[2]);

            // Cross product magnitude = 2 * area
            Vector3D v1 = new Vector3D(p1.x() - p0.x(), p1.y() - p0.y(), p1.z() - p0.z());
            Vector3D v2 = new Vector3D(p2.x() - p0.x(), p2.y() - p0.y(), p2.z() - p0.z());
            Vector3D cross = v1.cross(v2);
            double areaSq = cross.x() * cross.x() + cross.y() * cross.y() + cross.z() * cross.z();
            if (areaSq < eps) return false;
        }
        return true;
    }

    /**
     * Check if all face normals point consistently (all outward or all inward).
     *
     * For manifold meshes: uses edge direction consistency.
     * Each directed edge should appear exactly once in one direction,
     * and its reverse should appear exactly once.
     *
     * This is more reliable than centroid-based tests for concave or merged meshes.
     */
    public boolean hasConsistentWinding() {
        if (faces.isEmpty()) return true;

        // For each undirected edge, track how many times each direction appears
        // Consistent winding means: for every edge (a,b), if face F1 has a->b,
        // then the adjacent face F2 should have b->a (opposite direction)
        Map<String, Integer> edgeDirection = new HashMap<>();

        for (int[] face : faces) {
            for (int i = 0; i < face.length; i++) {
                int v1 = face[i];
                int v2 = face[(i + 1) % face.length];
                String key = v1 + "->" + v2;
                edgeDirection.merge(key, 1, Integer::sum);
            }
        }

        // For consistent winding on a manifold:
        // - Each edge a->b should appear exactly once
        // - Its reverse b->a should also appear exactly once
        // This ensures adjacent faces have opposite winding on shared edges
        for (Map.Entry<String, Integer> entry : edgeDirection.entrySet()) {
            // If any directed edge appears more than once, winding is inconsistent
            if (entry.getValue() > 1) {
                return false;
            }
        }

        return true;
    }

    /**
     * Compute face normal for a specific face.
     */
    public Vector3D faceNormal(int faceIndex) {
        int[] face = faces.get(faceIndex);
        Point3D p0 = vertices.get(face[0]);
        Point3D p1 = vertices.get(face[1]);
        Point3D p2 = vertices.get(face[2]);

        Vector3D v1 = new Vector3D(p1.x() - p0.x(), p1.y() - p0.y(), p1.z() - p0.z());
        Vector3D v2 = new Vector3D(p2.x() - p0.x(), p2.y() - p0.y(), p2.z() - p0.z());
        return v1.cross(v2).normalize();
    }

    /**
     * Compute mesh centroid (average of all vertices).
     */
    public Point3D centroid() {
        double x = 0, y = 0, z = 0;
        for (Point3D v : vertices) {
            x += v.x();
            y += v.y();
            z += v.z();
        }
        int n = vertices.size();
        return new Point3D(x / n, y / n, z / n);
    }

    /**
     * Compute axis-aligned bounding box.
     */
    public BoundingBox bounds() {
        double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;

        for (Point3D v : vertices) {
            minX = Math.min(minX, v.x());
            maxX = Math.max(maxX, v.x());
            minY = Math.min(minY, v.y());
            maxY = Math.max(maxY, v.y());
            minZ = Math.min(minZ, v.z());
            maxZ = Math.max(maxZ, v.z());
        }

        return new BoundingBox(minX, maxX, minY, maxY, minZ, maxZ);
    }

    // =========================================================================
    // Validation
    // =========================================================================

    /**
     * Validation result with specific violations.
     */
    public record ValidationResult(boolean valid, List<String> violations) {
        public static ValidationResult ok() {
            return new ValidationResult(true, List.of());
        }
        public static ValidationResult fail(List<String> violations) {
            return new ValidationResult(false, violations);
        }
    }

    /**
     * Full validation: topological + geometric.
     */
    public ValidationResult validate() {
        List<String> violations = new ArrayList<>();

        // Topological checks (exact)
        if (!hasValidIndices()) {
            violations.add("FACE_INDEX_OUT_OF_BOUNDS");
        }

        // Geometric checks (bounded)
        if (!hasNoDegenerateFaces()) {
            violations.add("DEGENERATE_FACE");
        }
        if (!hasConsistentWinding()) {
            violations.add("INCONSISTENT_WINDING");
        }

        return violations.isEmpty()
            ? ValidationResult.ok()
            : ValidationResult.fail(violations);
    }

    @Override
    public String toString() {
        return String.format("Mesh[V=%d, F=%d, E=%d, χ=%d]",
            vertexCount(), faceCount(), edgeCount(), eulerCharacteristic());
    }
}
