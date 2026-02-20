package com.bim.compiler.mesh;

import com.bim.compiler.geometry.BoundingBox;
import com.bim.compiler.geometry.Mesh;
import com.bim.compiler.geometry.Point3D;

import java.util.List;

/**
 * Parametric gable roof mesh generator.
 *
 * <p>Reads parameters from {@code ad_parametric_mesh_param} (via
 * {@link MeshParameters}). Every dimension traces to a provenance source.
 * No hardcoded vertex positions — all computed from parameters.
 *
 * <p>iDempiere analogy: this class is the "production order template"
 * (AD_Workflow) that executes when a {@code GABLE_ROOF_MY} mesh is requested.
 * The input parameters (MeshParameters) are the {@code C_OrderLine} quantities;
 * the output (MeshResult) is the {@code M_Transaction} (physical goods produced).
 *
 * <h3>Geometry</h3>
 * <p>6-vertex gable prism (ridge along X axis by default):
 * <pre>
 *        ridge S ──────────── ridge N
 *       /        \           /        \
 *     SW ──────── SE       NW ──────── NE   (base eaves at baseZ=0)
 * </pre>
 * Vertices (ridgeAlongX=true):
 * <ol>
 *   <li>SW base (minX, minY, 0)</li>
 *   <li>SE base (maxX, minY, 0)</li>
 *   <li>Ridge S (midX, minY, ridgeZ)</li>  ← only when ridge along Y
 *   <li>NW base (minX, maxY, 0)</li>
 *   <li>NE base (maxX, maxY, 0)</li>
 *   <li>Ridge N (midX, maxY, ridgeZ)</li>  ← only when ridge along Y
 * </ol>
 *
 * <h3>Parameters (from ad_parametric_mesh_param)</h3>
 * <ul>
 *   <li>{@code pitch_deg} — roof pitch in degrees (mandatory)</li>
 *   <li>{@code span_mm} — span perpendicular to ridge (mandatory)</li>
 *   <li>{@code depth_mm} — dimension parallel to ridge (mandatory)</li>
 *   <li>{@code overhang_mm} — eave overhang beyond wall (mandatory)</li>
 *   <li>{@code ridge_axis} — "X" or "Y" (optional, default "X")</li>
 * </ul>
 */
public final class GableRoofMesh implements ParametricMesh {

    @Override
    public MeshResult generate(MeshParameters params) {
        // All params from ad_parametric_mesh_param — no hardcoded values
        double pitchDeg   = params.getOrThrow("pitch_deg");    // RESEARCHED_JKR_GUIDELINES
        double spanMm     = params.getOrThrow("span_mm");       // EXTRACTED_TBLKTN
        double depthMm    = params.getOrThrow("depth_mm");      // EXTRACTED_TBLKTN
        double overhangMm = params.getOrThrow("overhang_mm");   // RESEARCHED_JKR_GUIDELINES
        String ridgeAxis  = params.getStringOrDefault("ridge_axis", "X");

        double pitchRad  = Math.toRadians(pitchDeg);
        double spanM     = spanMm / 1000.0;
        double depthM    = depthMm / 1000.0;
        double overhangM = overhangMm / 1000.0;

        // Ridge rises from eave to apex
        double halfSpan = spanM / 2.0;
        double ridgeZ   = halfSpan * Math.tan(pitchRad);

        // Eave bounds (overhang extends beyond wall face)
        double minX = -spanM / 2.0 - overhangM;
        double maxX =  spanM / 2.0 + overhangM;
        double minY = -overhangM;
        double maxY = depthM + overhangM;

        List<Point3D> vertices;
        List<int[]>   faces;

        if ("X".equals(ridgeAxis)) {
            // Ridge runs East-West (along X), gable ends face N and S
            // Vertices: SW, SE, ridge-SW(mid-Y south), ridge-NW(mid-Y north), NW, NE
            // Simplified 6-vertex gable prism (ridge parallel to X axis):
            double ridgeY = depthM / 2.0;
            vertices = List.of(
                new Point3D(minX, minY,    0),       // 0 SW base
                new Point3D(maxX, minY,    0),       // 1 SE base
                new Point3D(minX, ridgeY,  ridgeZ),  // 2 ridge W
                new Point3D(maxX, ridgeY,  ridgeZ),  // 3 ridge E
                new Point3D(minX, maxY,    0),       // 4 NW base
                new Point3D(maxX, maxY,    0)        // 5 NE base
            );
            // 6 triangular faces: south slope, north slope, west gable, east gable
            faces = List.of(
                new int[]{0, 1, 3}, new int[]{0, 3, 2},   // south slope
                new int[]{4, 2, 3}, new int[]{4, 3, 5},   // north slope
                new int[]{0, 2, 4},                         // west gable
                new int[]{1, 5, 3}                          // east gable
            );
        } else {
            // Ridge runs North-South (along Y), gable ends face E and W
            double ridgeX = spanM / 2.0;
            vertices = List.of(
                new Point3D(minX,    minY, 0),         // 0 SW base
                new Point3D(maxX,    minY, 0),         // 1 SE base
                new Point3D(ridgeX,  minY, ridgeZ),    // 2 ridge S
                new Point3D(minX,    maxY, 0),         // 3 NW base
                new Point3D(maxX,    maxY, 0),         // 4 NE base
                new Point3D(ridgeX,  maxY, ridgeZ)     // 5 ridge N
            );
            faces = List.of(
                new int[]{0, 1, 2},                         // south gable
                new int[]{3, 5, 4},                         // north gable
                new int[]{0, 2, 5}, new int[]{0, 5, 3},   // west slope
                new int[]{1, 4, 5}, new int[]{1, 5, 2}    // east slope
            );
        }

        Mesh mesh = new Mesh(vertices, faces);

        // Compute bounds from vertex list (BoundingBox has no fromVertices factory)
        double bMinX = vertices.stream().mapToDouble(Point3D::x).min().orElse(0);
        double bMaxX = vertices.stream().mapToDouble(Point3D::x).max().orElse(1);
        double bMinY = vertices.stream().mapToDouble(Point3D::y).min().orElse(0);
        double bMaxY = vertices.stream().mapToDouble(Point3D::y).max().orElse(1);
        double bMinZ = vertices.stream().mapToDouble(Point3D::z).min().orElse(0);
        double bMaxZ = vertices.stream().mapToDouble(Point3D::z).max().orElse(1);
        BoundingBox bb = new BoundingBox(bMinX, bMaxX, bMinY, bMaxY, bMinZ, bMaxZ);

        return new MeshResult(mesh, bb,
            "COMPUTED_FROM_PARAMS:GABLE_ROOF_MY:pitch=" + pitchDeg
            + "deg:overhang=" + overhangMm + "mm");
    }
}
