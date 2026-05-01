/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.mesh;

import com.bim.compiler.geometry.BoundingBox;
import com.bim.compiler.geometry.Mesh;
import com.bim.compiler.geometry.Point3D;

import java.util.ArrayList;
import java.util.List;

/**
 * Parametric half-round drain channel mesh generator.
 *
 * <p>Generates a canonical 1m segment of a hollow half-round (semicircular)
 * precast concrete perimeter drain. Cross-section profile in the XZ plane:
 * outer and inner semicircles with the flat top face open (water inlet).
 *
 * <p>Schedule reference G5 (TB-LKTN): "230mm ø half p.c perimeter drain to
 * be discharged to the nearest road side drain to Engr's Detail".
 *
 * <h3>Coordinate convention</h3>
 * <ul>
 *   <li>Y-axis = drain run direction (0 to segment_length_mm)</li>
 *   <li>X-axis = cross-section width (−R to +R)</li>
 *   <li>Z=0 = flat top face (open water inlet, ground level datum)</li>
 *   <li>Z decreases downward; outer bottom at Z=−R (deepest point)</li>
 * </ul>
 *
 * <h3>Cross-section profile (XZ plane)</h3>
 * <pre>
 *   Outer boundary: semicircle from (−R,−R,0) → (0,−R,−R) → (+R,0,0)
 *   Inner boundary: semicircle from (−r,0,0)  → (0,0,−r)  → (+r,0,0)
 *   Open top: gap between inner and outer at Z=0 (water inlet, no face)
 *   Two vertical side walls connecting inner/outer at Z=0:
 *     Left wall:  X=−R (outer) to X=−r (inner)
 *     Right wall: X=+r (inner) to X=+R (outer)
 * </pre>
 *
 * <p>Outer point at angle θ (0→π, left-to-right across top):
 * {@code x = −R×cos(θ),  z = −R×sin(θ)}  — curves downward.
 * At θ=0: (−R, z, 0) left top; θ=π/2: (0, z, −R) bottom; θ=π: (+R, z, 0) right top.
 *
 * <h3>Parameters (from lod_parametric_mesh_param)</h3>
 * <ul>
 *   <li>{@code diameter_mm}       — outer diameter (mandatory)</li>
 *   <li>{@code wall_thickness_mm} — channel wall thickness (mandatory)</li>
 *   <li>{@code segment_length_mm} — canonical segment length (mandatory)</li>
 *   <li>{@code segments_n}        — half-circle tessellation divisions (mandatory)</li>
 * </ul>
 *
 * <h3>Mesh statistics for N=8 (default)</h3>
 * <ul>
 *   <li>Vertices: 4×(N+1) = 36</li>
 *   <li>Faces: 8×N + 4 = 68 triangles</li>
 * </ul>
 */
public final class HalfRoundDrainMesh implements ParametricMesh {

    @Override
    public MeshResult generate(MeshParameters params) {
        // All params from lod_parametric_mesh_param — no hardcoded values.
        double diamMm    = params.getOrThrow("diameter_mm");        // EXTRACTED_TBLKTN
        double wallMm    = params.getOrThrow("wall_thickness_mm");  // RESEARCHED_JKR_DRAIN_PRECAST
        double lenMm     = params.getOrThrow("segment_length_mm");  // RESEARCHED_MESH2LIB
        int    n         = (int) params.getOrThrow("segments_n");   // RESEARCHED_MESH2LIB

        double R = (diamMm / 2.0) / 1000.0;          // outer radius (m)
        double r = (diamMm / 2.0 - wallMm) / 1000.0; // inner radius (m)
        double L = lenMm / 1000.0;                    // segment length (m)

        // ── Generate vertices ──────────────────────────────────────────────
        // Index layout (nPts = N+1 points per semicircle):
        //   [0   .. nPts-1] : outer ring, Y=0 (front face)
        //   [nPts.. 2nPts-1]: inner ring, Y=0 (front face)
        //   [2nPts..3nPts-1]: outer ring, Y=L (back face)
        //   [3nPts..4nPts-1]: inner ring, Y=L (back face)
        int nPts = n + 1;
        List<Point3D> vertices = new ArrayList<>(4 * nPts);

        for (int face = 0; face < 2; face++) {          // face=0 → Y=0, face=1 → Y=L
            double y = (face == 0) ? 0.0 : L;
            // Outer ring
            for (int k = 0; k <= n; k++) {
                double theta = k * Math.PI / n;         // θ ∈ [0, π]
                vertices.add(new Point3D(-R * Math.cos(theta), y, -R * Math.sin(theta)));
                // k=0: (−R, y, 0) left-top; k=n/2: (0, y, −R) bottom; k=n: (+R, y, 0) right-top
            }
            // Inner ring (same angular sampling as outer)
            for (int k = 0; k <= n; k++) {
                double theta = k * Math.PI / n;
                vertices.add(new Point3D(-r * Math.cos(theta), y, -r * Math.sin(theta)));
            }
        }
        // Vertex count: 4×(N+1)

        // ── Generate faces ─────────────────────────────────────────────────
        // Helper indices:
        //   outerFront(k) = k
        //   innerFront(k) = nPts + k
        //   outerBack(k)  = 2*nPts + k
        //   innerBack(k)  = 3*nPts + k
        List<int[]> faces = new ArrayList<>(8 * n + 4);

        // 1. Front annular face (Y=0, outward normal = −Y)
        //    For each angular strip: quad {outer[k], outer[k+1], inner[k+1], inner[k]}
        for (int k = 0; k < n; k++) {
            int of0 = k,          of1 = k + 1;          // outer front
            int if0 = nPts + k,   if1 = nPts + k + 1;  // inner front
            faces.add(new int[]{of0, if0, of1});          // tri 1 (CCW from −Y)
            faces.add(new int[]{of1, if0, if1});          // tri 2
        }

        // 2. Back annular face (Y=L, outward normal = +Y, reversed winding)
        for (int k = 0; k < n; k++) {
            int ob0 = 2*nPts + k,     ob1 = 2*nPts + k + 1;  // outer back
            int ib0 = 3*nPts + k,     ib1 = 3*nPts + k + 1;  // inner back
            faces.add(new int[]{ob0, ob1, ib0});               // tri 1 (CCW from +Y)
            faces.add(new int[]{ob1, ib1, ib0});               // tri 2
        }

        // 3. Outer curved surface (outward normal = radially outward)
        for (int k = 0; k < n; k++) {
            int of0 = k,         of1 = k + 1;           // outer front
            int ob0 = 2*nPts+k,  ob1 = 2*nPts+k+1;     // outer back
            faces.add(new int[]{of0, ob0, ob1});          // tri 1
            faces.add(new int[]{of0, ob1, of1});          // tri 2
        }

        // 4. Inner curved surface (outward normal = radially inward, toward channel centre)
        for (int k = 0; k < n; k++) {
            int if0 = nPts+k,    if1 = nPts+k+1;        // inner front
            int ib0 = 3*nPts+k,  ib1 = 3*nPts+k+1;     // inner back
            faces.add(new int[]{if0, if1, ib1});          // tri 1
            faces.add(new int[]{if0, ib1, ib0});          // tri 2
        }

        // 5. Left top wall: connects outer-left (k=0) to inner-left (k=0) front and back
        //    outer_front[0]=(−R,0,0), inner_front[0]=(−r,0,0),
        //    outer_back[0]=(−R,L,0), inner_back[0]=(−r,L,0)
        int ofL = 0, ifL = nPts, obL = 2*nPts, ibL = 3*nPts;
        faces.add(new int[]{ofL, ibL, ifL});   // tri 1
        faces.add(new int[]{ofL, obL, ibL});   // tri 2

        // 6. Right top wall: connects outer-right (k=n) to inner-right (k=n) front and back
        int ofR = n, ifR = nPts+n, obR = 2*nPts+n, ibR = 3*nPts+n;
        faces.add(new int[]{ofR, ifR, ibR});   // tri 1
        faces.add(new int[]{ofR, ibR, obR});   // tri 2

        // Total: 2×2N + 2×2N + 4 = 8N+4 faces (for N=8: 68 triangles)

        Mesh mesh = new Mesh(vertices, faces);

        // Compute bounds from vertex list — BoundingBox has no fromVertices() factory.
        double bMinX = vertices.stream().mapToDouble(Point3D::x).min().orElse(0);
        double bMaxX = vertices.stream().mapToDouble(Point3D::x).max().orElse(1);
        double bMinY = vertices.stream().mapToDouble(Point3D::y).min().orElse(0);
        double bMaxY = vertices.stream().mapToDouble(Point3D::y).max().orElse(1);
        double bMinZ = vertices.stream().mapToDouble(Point3D::z).min().orElse(0);
        double bMaxZ = vertices.stream().mapToDouble(Point3D::z).max().orElse(1);
        BoundingBox bb = new BoundingBox(bMinX, bMaxX, bMinY, bMaxY, bMinZ, bMaxZ);

        return new MeshResult(mesh, bb,
            "COMPUTED_FROM_PARAMS:DRAIN_HALFROUND_MY:diameter=" + diamMm
            + "mm:wall=" + wallMm + "mm:L=" + lenMm + "mm:N=" + n);
    }
}
