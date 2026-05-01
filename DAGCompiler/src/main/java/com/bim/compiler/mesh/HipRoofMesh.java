/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.mesh;

import com.bim.compiler.geometry.BoundingBox;
import com.bim.compiler.geometry.Mesh;
import com.bim.compiler.geometry.Point3D;

import java.util.List;

/**
 * Parametric hip roof mesh generator.
 *
 * <p>All four sides slope to the ridge — no exposed gable walls.
 * Ridge runs along the long axis (E-W by default = X-axis).
 *
 * <p>Unlike {@link GableRoofMesh} which takes wall span + separate overhang,
 * {@code HipRoofMesh} reads {@code span_mm} and {@code depth_mm} as the
 * <em>total</em> footprint dimensions (overhangs already included).
 * This is because the hip footprint is defined as a single extracted rectangle
 * from the roof plan — both dimensions include the 700mm overhang on all sides.
 *
 * <p>iDempiere analogy: like a {@code M_Product} production order for a hip roof
 * assembly — the bill of parameters (from {@code lod_parametric_mesh_param})
 * fully specifies the shape without any hardcoded vertex positions.
 *
 * <h3>Geometry (ridge_axis=X)</h3>
 * <pre>
 *   Plan view (looking down):
 *
 *   NW──────────────────────NE
 *    \   ╲             ╱   /
 *     \    RidgeW═══RidgeE  /
 *    /   ╱             ╲   \
 *   SW──────────────────────SE
 *
 *   6 vertices:
 *     V0: SW  (-halfX, -halfY,    0)
 *     V1: SE  (+halfX, -halfY,    0)
 *     V2: NE  (+halfX, +halfY,    0)
 *     V3: NW  (-halfX, +halfY,    0)
 *     V4: RW  (-ridgeHalf,  0, ridgeZ)  ← Ridge West end
 *     V5: RE  (+ridgeHalf,  0, ridgeZ)  ← Ridge East end
 *
 *   6 faces (triangles):
 *     South slope : {0,1,5}, {0,5,4}
 *     North slope : {3,4,5}, {3,5,2}
 *     West hip    : {0,4,3}
 *     East hip    : {1,2,5}
 * </pre>
 *
 * <h3>Parameters (from lod_parametric_mesh_param)</h3>
 * <ul>
 *   <li>{@code pitch_deg}       — roof pitch in degrees (mandatory)</li>
 *   <li>{@code span_mm}         — total N-S footprint inc. overhangs (mandatory)</li>
 *   <li>{@code depth_mm}        — total E-W footprint inc. overhangs (mandatory)</li>
 *   <li>{@code ridge_length_mm} — flat ridge segment length (mandatory)</li>
 *   <li>{@code overhang_mm}     — eave overhang, stored for reference (mandatory)</li>
 *   <li>{@code ridge_axis}      — "X" E-W ridge (default) or "Y" N-S ridge (optional)</li>
 * </ul>
 *
 * <p>Ridge height: {@code ridgeZ = (span_mm/2/1000) × tan(pitch_deg)}
 * where span_mm is the N-S total footprint (perpendicular to the E-W ridge).
 * Z=0 is the eave plane (all four corner eaves at Z=0).
 */
public final class HipRoofMesh implements ParametricMesh {

    @Override
    public MeshResult generate(MeshParameters params) {
        // All params from lod_parametric_mesh_param — no hardcoded values.
        double pitchDeg      = params.getOrThrow("pitch_deg");       // EXTRACTED_TBLKTN
        double spanMm        = params.getOrThrow("span_mm");         // EXTRACTED_TBLKTN: N-S total footprint
        double depthMm       = params.getOrThrow("depth_mm");        // EXTRACTED_TBLKTN: E-W total footprint
        double ridgeLenMm    = params.getOrThrow("ridge_length_mm"); // EXTRACTED_TBLKTN
        @SuppressWarnings("unused")
        double overhangMm    = params.getOrThrow("overhang_mm");     // EXTRACTED_TBLKTN (stored, not re-added)
        String ridgeAxis     = params.getStringOrDefault("ridge_axis", "X");

        double pitchRad      = Math.toRadians(pitchDeg);

        List<Point3D> vertices;
        List<int[]>   faces;

        if ("X".equals(ridgeAxis)) {
            // Ridge runs E-W (along X axis). N-S span drives ridge height.
            double halfX        = depthMm     / 2.0 / 1000.0;  // E-W half-span (m)
            double halfY        = spanMm      / 2.0 / 1000.0;  // N-S half-span (m)
            double ridgeHalfX   = ridgeLenMm  / 2.0 / 1000.0;  // ridge half-length (m)

            // Ridge rises from eave plane (Z=0) to apex above N-S centre line (Y=0)
            double ridgeZ = halfY * Math.tan(pitchRad);

            // 4 eave corners + 2 ridge ends
            Point3D sw = new Point3D(-halfX,      -halfY, 0);       // 0 SW
            Point3D se = new Point3D(+halfX,      -halfY, 0);       // 1 SE
            Point3D ne = new Point3D(+halfX,      +halfY, 0);       // 2 NE
            Point3D nw = new Point3D(-halfX,      +halfY, 0);       // 3 NW
            Point3D rw = new Point3D(-ridgeHalfX, 0,      ridgeZ);  // 4 Ridge W
            Point3D re = new Point3D(+ridgeHalfX, 0,      ridgeZ);  // 5 Ridge E

            vertices = List.of(sw, se, ne, nw, rw, re);
            faces = List.of(
                new int[]{0, 1, 5}, new int[]{0, 5, 4},  // south slope (quad SW-SE-RE-RW)
                new int[]{3, 4, 5}, new int[]{3, 5, 2},  // north slope (quad NW-RW-RE-NE)
                new int[]{0, 4, 3},                        // west hip (SW-RW-NW)
                new int[]{1, 2, 5}                         // east hip (SE-NE-RE)
            );
        } else {
            // Ridge runs N-S (along Y axis). E-W span drives ridge height.
            double halfX        = depthMm     / 2.0 / 1000.0;  // E-W half-span (m)
            double halfY        = spanMm      / 2.0 / 1000.0;  // N-S half-span (m)
            double ridgeHalfY   = ridgeLenMm  / 2.0 / 1000.0;  // ridge half-length (m)

            // Ridge rises from E-W eave plane, centred at X=0
            double ridgeZ = halfX * Math.tan(pitchRad);

            Point3D sw = new Point3D(-halfX,  -halfY, 0);      // 0 SW
            Point3D se = new Point3D(+halfX,  -halfY, 0);      // 1 SE
            Point3D ne = new Point3D(+halfX,  +halfY, 0);      // 2 NE
            Point3D nw = new Point3D(-halfX,  +halfY, 0);      // 3 NW
            Point3D rs = new Point3D(0, -ridgeHalfY, ridgeZ);  // 4 Ridge S
            Point3D rn = new Point3D(0, +ridgeHalfY, ridgeZ);  // 5 Ridge N

            vertices = List.of(sw, se, ne, nw, rs, rn);
            faces = List.of(
                new int[]{0, 3, 4}, new int[]{3, 5, 4},  // west slope (quad SW-NW-RS-RN... check winding)
                new int[]{1, 4, 5}, new int[]{1, 5, 2},  // east slope
                new int[]{0, 4, 1},                        // south hip (SW-RS-SE)
                new int[]{3, 2, 5}                         // north hip (NW-NE-RN)
            );
        }

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
            "COMPUTED_FROM_PARAMS:HIP_ROOF_MY:pitch=" + pitchDeg
            + "deg:span=" + spanMm + "mm:depth=" + depthMm
            + "mm:ridge=" + ridgeLenMm + "mm");
    }
}
