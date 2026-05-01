/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.geometry;

import java.util.*;

/**
 * Geometry Engine: Parametric 3D geometry generation for BIM.
 *
 * Core insight: BIM geometry is mostly 2.5D (extruded profiles).
 * This engine provides:
 * 1. Primitive generation (box, prism, extrude)
 * 2. Transform operations (translate, rotate, array)
 * 3. Boolean operations (subtract for openings)
 * 4. Built-in validation (all output meshes are validated)
 *
 * All geometry follows Pattern B: world-space coordinates + zero transform.
 * Dimensions are in METERS.
 *
 * Theory:
 * - Manifold library: topology (exact) vs geometry (bounded)
 * - Euler characteristic: χ = V - E + F = 2 for closed mesh
 * - CCW face winding (right-hand rule for normals)
 */
public final class GeometryEngine {

    private GeometryEngine() {} // Static utility class

    // =========================================================================
    // Primitive Generation
    // =========================================================================

    /**
     * Create axis-aligned box at origin.
     * Box spans [0,w] x [0,d] x [0,h].
     *
     * @param w Width (X dimension) in meters
     * @param d Depth (Y dimension) in meters
     * @param h Height (Z dimension) in meters
     * @return Valid closed mesh
     */
    public static Mesh box(double w, double d, double h) {
        return boxAt(0, 0, 0, w, d, h);
    }

    /**
     * Create axis-aligned box at specified position.
     * Box spans [x, x+w] x [y, y+d] x [z, z+h].
     *
     * @param x X origin
     * @param y Y origin
     * @param z Z origin
     * @param w Width (X dimension)
     * @param d Depth (Y dimension)
     * @param h Height (Z dimension)
     * @return Valid closed mesh
     */
    public static Mesh boxAt(double x, double y, double z, double w, double d, double h) {
        // 8 vertices of a box
        List<Point3D> vertices = List.of(
            new Point3D(x, y, z),             // 0: bottom SW
            new Point3D(x + w, y, z),         // 1: bottom SE
            new Point3D(x + w, y + d, z),     // 2: bottom NE
            new Point3D(x, y + d, z),         // 3: bottom NW
            new Point3D(x, y, z + h),         // 4: top SW
            new Point3D(x + w, y, z + h),     // 5: top SE
            new Point3D(x + w, y + d, z + h), // 6: top NE
            new Point3D(x, y + d, z + h)      // 7: top NW
        );

        // 12 triangular faces (2 per box face), CCW from outside
        List<int[]> faces = List.of(
            // Bottom (Z=z, normal -Z) - CCW when viewed from below
            new int[]{0, 2, 1},
            new int[]{0, 3, 2},
            // Top (Z=z+h, normal +Z) - CCW when viewed from above
            new int[]{4, 5, 6},
            new int[]{4, 6, 7},
            // Front/South (Y=y, normal -Y) - CCW when viewed from south
            new int[]{0, 1, 5},
            new int[]{0, 5, 4},
            // Back/North (Y=y+d, normal +Y) - CCW when viewed from north
            new int[]{2, 3, 7},
            new int[]{2, 7, 6},
            // Left/West (X=x, normal -X) - CCW when viewed from west
            new int[]{0, 4, 7},
            new int[]{0, 7, 3},
            // Right/East (X=x+w, normal +X) - CCW when viewed from east
            new int[]{1, 2, 6},
            new int[]{1, 6, 5}
        );

        return new Mesh(vertices, faces);
    }

    /**
     * Create gable prism (box with pitched roof).
     * Used for simple roof structures.
     *
     * @param w Width (X dimension)
     * @param d Depth (Y dimension)
     * @param wallH Wall height before pitch
     * @param pitchDeg Pitch angle in degrees
     * @param ridgeAlongX If true, ridge runs along X axis; else along Y
     * @return Valid closed mesh
     */
    public static Mesh gablePrism(double w, double d, double wallH, double pitchDeg, boolean ridgeAlongX) {
        return gablePrismAt(0, 0, 0, w, d, wallH, pitchDeg, ridgeAlongX);
    }

    /**
     * Create gable prism at specified position.
     */
    public static Mesh gablePrismAt(double x, double y, double z,
                                     double w, double d, double wallH,
                                     double pitchDeg, boolean ridgeAlongX) {
        double pitchRad = Math.toRadians(pitchDeg);
        double ridgeSpan = ridgeAlongX ? d : w;
        double ridgeRise = (ridgeSpan / 2) * Math.tan(pitchRad);
        double totalH = wallH + ridgeRise;

        List<Point3D> vertices;
        List<int[]> faces;

        if (ridgeAlongX) {
            // Ridge runs along X axis (east-west)
            // 6 vertices: 4 at eaves, 2 at ridge
            double ridgeY = y + d / 2;
            vertices = List.of(
                new Point3D(x, y, z),                     // 0: SW base
                new Point3D(x + w, y, z),                 // 1: SE base
                new Point3D(x + w, y + d, z),             // 2: NE base
                new Point3D(x, y + d, z),                 // 3: NW base
                new Point3D(x, y, z + wallH),             // 4: SW eave
                new Point3D(x + w, y, z + wallH),         // 5: SE eave
                new Point3D(x + w, y + d, z + wallH),     // 6: NE eave
                new Point3D(x, y + d, z + wallH),         // 7: NW eave
                new Point3D(x, ridgeY, z + totalH),       // 8: W ridge
                new Point3D(x + w, ridgeY, z + totalH)    // 9: E ridge
            );

            faces = List.of(
                // Bottom
                new int[]{0, 2, 1},
                new int[]{0, 3, 2},
                // South wall
                new int[]{0, 1, 5},
                new int[]{0, 5, 4},
                // North wall
                new int[]{2, 3, 7},
                new int[]{2, 7, 6},
                // West gable (triangular)
                new int[]{3, 0, 4},
                new int[]{3, 4, 8},
                new int[]{3, 8, 7},
                // East gable (triangular)
                new int[]{1, 2, 6},
                new int[]{1, 6, 9},
                new int[]{1, 9, 5},
                // South roof slope
                new int[]{4, 5, 9},
                new int[]{4, 9, 8},
                // North roof slope
                new int[]{7, 8, 9},
                new int[]{7, 9, 6}
            );
        } else {
            // Ridge runs along Y axis (north-south)
            double ridgeX = x + w / 2;
            vertices = List.of(
                new Point3D(x, y, z),                     // 0: SW base
                new Point3D(x + w, y, z),                 // 1: SE base
                new Point3D(x + w, y + d, z),             // 2: NE base
                new Point3D(x, y + d, z),                 // 3: NW base
                new Point3D(x, y, z + wallH),             // 4: SW eave
                new Point3D(x + w, y, z + wallH),         // 5: SE eave
                new Point3D(x + w, y + d, z + wallH),     // 6: NE eave
                new Point3D(x, y + d, z + wallH),         // 7: NW eave
                new Point3D(ridgeX, y, z + totalH),       // 8: S ridge
                new Point3D(ridgeX, y + d, z + totalH)    // 9: N ridge
            );

            faces = List.of(
                // Bottom
                new int[]{0, 2, 1},
                new int[]{0, 3, 2},
                // West wall
                new int[]{0, 4, 7},
                new int[]{0, 7, 3},
                // East wall
                new int[]{1, 2, 6},
                new int[]{1, 6, 5},
                // South gable (triangular)
                new int[]{0, 1, 5},
                new int[]{0, 5, 8},
                new int[]{0, 8, 4},
                // North gable (triangular)
                new int[]{3, 7, 9},
                new int[]{3, 9, 6},
                new int[]{3, 6, 2},
                // West roof slope
                new int[]{4, 8, 9},
                new int[]{4, 9, 7},
                // East roof slope
                new int[]{5, 6, 9},
                new int[]{5, 9, 8}
            );
        }

        return new Mesh(vertices, faces);
    }

    /**
     * Extrude a 2D profile along Z axis.
     * Profile must be CCW-wound (when viewed from +Z).
     *
     * @param profile 2D profile points (CCW order)
     * @param height Extrusion height
     * @return Valid closed mesh
     */
    public static Mesh extrude(List<Point2D> profile, double height) {
        return extrudeAt(profile, 0, height);
    }

    /**
     * Extrude a 2D profile from baseZ to baseZ + height.
     * Profile is assumed to be CCW when viewed from +Z.
     */
    public static Mesh extrudeAt(List<Point2D> profile, double baseZ, double height) {
        int n = profile.size();
        if (n < 3) {
            throw new IllegalArgumentException("Profile must have at least 3 points");
        }

        List<Point3D> vertices = new ArrayList<>();
        List<int[]> faces = new ArrayList<>();

        // Bottom vertices (indices 0 to n-1, Z = baseZ)
        for (Point2D p : profile) {
            vertices.add(new Point3D(p.x(), p.y(), baseZ));
        }
        // Top vertices (indices n to 2n-1, Z = baseZ + height)
        for (Point2D p : profile) {
            vertices.add(new Point3D(p.x(), p.y(), baseZ + height));
        }

        // Bottom face (normal pointing down, -Z)
        // Profile is CCW from above, so for bottom face (viewed from below)
        // we need CW order from above = CCW from below = reversed profile order
        for (int i = 1; i < n - 1; i++) {
            faces.add(new int[]{0, i + 1, i});
        }

        // Top face (normal pointing up, +Z)
        // CCW when viewed from above = same as profile order
        for (int i = 1; i < n - 1; i++) {
            faces.add(new int[]{n, n + i, n + i + 1});
        }

        // Side faces (quads as 2 triangles each)
        // For CCW profile (when viewed from +Z):
        // - Bottom face has edges going CW (for -Z normal)
        // - Side faces must have bottom edge going CCW (opposite to bottom face)
        // - For profile edge i→next, bottom face has edge next→i
        // - So side face must have edge i→next (opposite direction)
        // - Side quad viewed from outside: i→next→(next+n)→(i+n)
        // - As triangles: (i, next, next+n) and (i, next+n, i+n)
        for (int i = 0; i < n; i++) {
            int next = (i + 1) % n;
            // First triangle: bottom[i] -> bottom[next] -> top[next]
            faces.add(new int[]{i, next, next + n});
            // Second triangle: bottom[i] -> top[next] -> top[i]
            faces.add(new int[]{i, next + n, i + n});
        }

        return new Mesh(vertices, faces);
    }

    /**
     * Create a wall segment (box aligned to wall direction).
     *
     * @param start Start point (at base Z)
     * @param end End point (at base Z)
     * @param thickness Wall thickness
     * @param height Wall height
     * @return Valid closed mesh
     */
    public static Mesh wall(Point3D start, Point3D end, double thickness, double height) {
        // Wall direction vector
        double dx = end.x() - start.x();
        double dy = end.y() - start.y();
        double len = Math.sqrt(dx * dx + dy * dy);

        if (len < 1e-6) {
            throw new IllegalArgumentException("Wall has zero length");
        }

        // Perpendicular direction for thickness (90° CCW)
        double px = -dy / len * thickness / 2;
        double py = dx / len * thickness / 2;

        double z = start.z();

        // 4 corners at base
        List<Point3D> vertices = List.of(
            new Point3D(start.x() - px, start.y() - py, z),
            new Point3D(start.x() + px, start.y() + py, z),
            new Point3D(end.x() + px, end.y() + py, z),
            new Point3D(end.x() - px, end.y() - py, z),
            new Point3D(start.x() - px, start.y() - py, z + height),
            new Point3D(start.x() + px, start.y() + py, z + height),
            new Point3D(end.x() + px, end.y() + py, z + height),
            new Point3D(end.x() - px, end.y() - py, z + height)
        );

        // Same faces as box
        List<int[]> faces = List.of(
            new int[]{0, 2, 1}, new int[]{0, 3, 2}, // Bottom
            new int[]{4, 5, 6}, new int[]{4, 6, 7}, // Top
            new int[]{0, 1, 5}, new int[]{0, 5, 4}, // Start end
            new int[]{2, 3, 7}, new int[]{2, 7, 6}, // End end
            new int[]{1, 2, 6}, new int[]{1, 6, 5}, // Outer face
            new int[]{0, 4, 7}, new int[]{0, 7, 3}  // Inner face
        );

        return new Mesh(vertices, faces);
    }

    // =========================================================================
    // Transform Operations
    // =========================================================================

    /**
     * Translate mesh by offset.
     */
    public static Mesh translate(Mesh m, double dx, double dy, double dz) {
        List<Point3D> newVerts = new ArrayList<>();
        for (Point3D v : m.vertices()) {
            newVerts.add(new Point3D(v.x() + dx, v.y() + dy, v.z() + dz));
        }
        return new Mesh(newVerts, m.faces());
    }

    /**
     * Rotate mesh around Z axis by angle (radians, CCW when viewed from +Z).
     */
    public static Mesh rotateZ(Mesh m, double angleRad) {
        double cos = Math.cos(angleRad);
        double sin = Math.sin(angleRad);

        List<Point3D> newVerts = new ArrayList<>();
        for (Point3D v : m.vertices()) {
            double newX = v.x() * cos - v.y() * sin;
            double newY = v.x() * sin + v.y() * cos;
            newVerts.add(new Point3D(newX, newY, v.z()));
        }
        return new Mesh(newVerts, m.faces());
    }

    /**
     * Scale mesh uniformly around origin.
     */
    public static Mesh scale(Mesh m, double factor) {
        return scale(m, factor, factor, factor);
    }

    /**
     * Scale mesh non-uniformly around origin.
     */
    public static Mesh scale(Mesh m, double sx, double sy, double sz) {
        List<Point3D> newVerts = new ArrayList<>();
        for (Point3D v : m.vertices()) {
            newVerts.add(new Point3D(v.x() * sx, v.y() * sy, v.z() * sz));
        }
        return new Mesh(newVerts, m.faces());
    }

    /**
     * Mirror mesh across YZ plane (flip X).
     */
    public static Mesh mirrorX(Mesh m) {
        List<Point3D> newVerts = new ArrayList<>();
        for (Point3D v : m.vertices()) {
            newVerts.add(new Point3D(-v.x(), v.y(), v.z()));
        }
        // Reverse face winding to maintain consistent normals
        List<int[]> newFaces = new ArrayList<>();
        for (int[] face : m.faces()) {
            newFaces.add(new int[]{face[0], face[2], face[1]});
        }
        return new Mesh(newVerts, newFaces);
    }

    /**
     * Mirror mesh across XZ plane (flip Y).
     */
    public static Mesh mirrorY(Mesh m) {
        List<Point3D> newVerts = new ArrayList<>();
        for (Point3D v : m.vertices()) {
            newVerts.add(new Point3D(v.x(), -v.y(), v.z()));
        }
        List<int[]> newFaces = new ArrayList<>();
        for (int[] face : m.faces()) {
            newFaces.add(new int[]{face[0], face[2], face[1]});
        }
        return new Mesh(newVerts, newFaces);
    }

    // =========================================================================
    // Array Operations
    // =========================================================================

    /**
     * Axis enum for array operations.
     */
    public enum Axis { X, Y, Z }

    /**
     * Create linear array of meshes along specified axis.
     *
     * @param m Source mesh
     * @param count Number of copies (including original)
     * @param spacing Distance between copies
     * @param axis Array direction
     * @return List of translated meshes
     */
    public static List<Mesh> array(Mesh m, int count, double spacing, Axis axis) {
        List<Mesh> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double offset = i * spacing;
            switch (axis) {
                case X -> result.add(translate(m, offset, 0, 0));
                case Y -> result.add(translate(m, 0, offset, 0));
                case Z -> result.add(translate(m, 0, 0, offset));
            }
        }
        return result;
    }

    /**
     * Create grid array of meshes in XY plane.
     *
     * @param m Source mesh
     * @param countX Number of copies in X direction
     * @param countY Number of copies in Y direction
     * @param spacingX Distance between copies in X
     * @param spacingY Distance between copies in Y
     * @return List of translated meshes
     */
    public static List<Mesh> gridArray(Mesh m, int countX, int countY,
                                        double spacingX, double spacingY) {
        List<Mesh> result = new ArrayList<>();
        for (int ix = 0; ix < countX; ix++) {
            for (int iy = 0; iy < countY; iy++) {
                result.add(translate(m, ix * spacingX, iy * spacingY, 0));
            }
        }
        return result;
    }

    // =========================================================================
    // Merge Operations
    // =========================================================================

    /**
     * Merge multiple meshes into a single mesh.
     * No boolean operations - just combines vertices and faces.
     */
    public static Mesh merge(List<Mesh> meshes) {
        List<Point3D> allVerts = new ArrayList<>();
        List<int[]> allFaces = new ArrayList<>();

        int vertOffset = 0;
        for (Mesh m : meshes) {
            allVerts.addAll(m.vertices());
            for (int[] face : m.faces()) {
                allFaces.add(new int[]{
                    face[0] + vertOffset,
                    face[1] + vertOffset,
                    face[2] + vertOffset
                });
            }
            vertOffset += m.vertexCount();
        }

        return new Mesh(allVerts, allFaces);
    }

    /**
     * Merge single mesh with others.
     */
    public static Mesh merge(Mesh first, Mesh... rest) {
        List<Mesh> all = new ArrayList<>();
        all.add(first);
        all.addAll(List.of(rest));
        return merge(all);
    }

    // =========================================================================
    // Building-specific Primitives
    // =========================================================================

    /**
     * Create a stair flight (series of steps).
     *
     * @param width Step width (across)
     * @param tread Step tread depth (going)
     * @param riser Step riser height
     * @param count Number of steps
     * @return Merged mesh of all steps
     */
    public static Mesh stairFlight(double width, double tread, double riser, int count) {
        List<Mesh> steps = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Mesh step = boxAt(0, i * tread, i * riser, width, tread, riser);
            steps.add(step);
        }
        return merge(steps);
    }

    /**
     * Create slab (thin horizontal box).
     *
     * @param width Width (X)
     * @param depth Depth (Y)
     * @param thickness Slab thickness
     * @param baseZ Bottom of slab
     * @return Valid closed mesh
     */
    public static Mesh slab(double width, double depth, double thickness, double baseZ) {
        return boxAt(0, 0, baseZ, width, depth, thickness);
    }

    /**
     * Create roof surface (gable roof without walls underneath).
     * This is just the roof planes, not a full prism.
     *
     * @param minX Footprint min X
     * @param maxX Footprint max X
     * @param minY Footprint min Y
     * @param maxY Footprint max Y
     * @param baseZ Eave level
     * @param pitchDeg Roof pitch in degrees
     * @param overhang Eave overhang
     * @param ridgeAlongX True if ridge runs along X axis
     * @return Valid roof mesh
     */
    public static Mesh roofSurface(double minX, double maxX, double minY, double maxY,
                                    double baseZ, double pitchDeg, double overhang,
                                    boolean ridgeAlongX) {
        double width = maxX - minX;
        double depth = maxY - minY;

        double pitchRad = Math.toRadians(pitchDeg);
        double ridgeSpan = ridgeAlongX ? depth : width;
        double ridgeRise = (ridgeSpan / 2) * Math.tan(pitchRad);

        List<Point3D> vertices;
        List<int[]> faces;

        if (ridgeAlongX) {
            double ridgeY = (minY + maxY) / 2;
            vertices = List.of(
                new Point3D(minX - overhang, minY - overhang, baseZ),                // 0: SW eave
                new Point3D(maxX + overhang, minY - overhang, baseZ),                // 1: SE eave
                new Point3D(minX - overhang, ridgeY, baseZ + ridgeRise),             // 2: W ridge
                new Point3D(maxX + overhang, ridgeY, baseZ + ridgeRise),             // 3: E ridge
                new Point3D(minX - overhang, maxY + overhang, baseZ),                // 4: NW eave
                new Point3D(maxX + overhang, maxY + overhang, baseZ)                 // 5: NE eave
            );

            // Correct face winding for ridgeAlongX
            faces = List.of(
                new int[]{0, 1, 3},  // South slope (lower triangle)
                new int[]{0, 3, 2},  // South slope (upper triangle)
                new int[]{4, 2, 3},  // North slope (upper triangle)
                new int[]{4, 3, 5},  // North slope (lower triangle)
                new int[]{0, 2, 4},  // West gable
                new int[]{1, 5, 3}   // East gable
            );
        } else {
            double ridgeX = (minX + maxX) / 2;
            vertices = List.of(
                new Point3D(minX - overhang, minY - overhang, baseZ),                // 0: SW eave
                new Point3D(maxX + overhang, minY - overhang, baseZ),                // 1: SE eave
                new Point3D(ridgeX, minY - overhang, baseZ + ridgeRise),             // 2: S ridge
                new Point3D(minX - overhang, maxY + overhang, baseZ),                // 3: NW eave
                new Point3D(maxX + overhang, maxY + overhang, baseZ),                // 4: NE eave
                new Point3D(ridgeX, maxY + overhang, baseZ + ridgeRise)              // 5: N ridge
            );

            faces = List.of(
                new int[]{0, 1, 2},  // South gable
                new int[]{3, 5, 4},  // North gable
                new int[]{0, 2, 5},  // West slope (lower triangle)
                new int[]{0, 5, 3},  // West slope (upper triangle)
                new int[]{1, 4, 5},  // East slope (upper triangle)
                new int[]{1, 5, 2}   // East slope (lower triangle)
            );
        }

        return new Mesh(vertices, faces);
    }

    // =========================================================================
    // Validation Utilities
    // =========================================================================

    /**
     * Validate mesh and throw if invalid.
     */
    public static void assertValid(Mesh m, String context) {
        Mesh.ValidationResult result = m.validate();
        if (!result.valid()) {
            throw new IllegalStateException(
                "Invalid mesh at " + context + ": " + result.violations());
        }
    }

    /**
     * Print mesh diagnostic info.
     */
    public static String diagnose(Mesh m) {
        StringBuilder sb = new StringBuilder();
        sb.append("Mesh Diagnostics:\n");
        sb.append(String.format("  Vertices: %d%n", m.vertexCount()));
        sb.append(String.format("  Faces: %d%n", m.faceCount()));
        sb.append(String.format("  Edges: %d%n", m.edgeCount()));
        sb.append(String.format("  Euler χ: %d (closed=2)%n", m.eulerCharacteristic()));
        sb.append(String.format("  Valid indices: %s%n", m.hasValidIndices()));
        sb.append(String.format("  No degenerate: %s%n", m.hasNoDegenerateFaces()));
        sb.append(String.format("  Consistent winding: %s%n", m.hasConsistentWinding()));
        sb.append(String.format("  Is manifold: %s%n", m.isManifold()));
        sb.append(String.format("  Bounds: %s%n", m.bounds()));
        return sb.toString();
    }
}
