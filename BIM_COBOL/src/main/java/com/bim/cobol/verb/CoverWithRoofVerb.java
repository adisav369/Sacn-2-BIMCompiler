package com.bim.cobol.verb;

import com.bim.cobol.Verb;
import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbResult;
import com.bim.cobol.geometry.ValleyStitcher;
import com.bim.cobol.geometry.ValleyStitcher.Plane3D;
import com.bim.cobol.geometry.ValleyStitcher.ValleyResult;
import com.bim.compiler.geometry.Point3D;
import com.bim.compiler.mesh.GableRoofMesh;
import com.bim.compiler.mesh.HipRoofMesh;
import com.bim.compiler.mesh.MeshParameters;
import com.bim.compiler.mesh.MeshResult;
import com.bim.compiler.mesh.ParametricMesh;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * COVER WITH COMPOUND_ROOF &lt;mesh_type&gt; — compound roof with valley stitching.
 *
 * <p>Loads parametric mesh definitions from component_library.db, generates
 * the primary hip roof and any subsidiary gable roofs that declare
 * {@code connects_to} + {@code valley_type=T_JUNCTION}, then computes
 * valley geometry where the slopes intersect.
 *
 * <p>Read-only — no DB writes.
 */
public class CoverWithRoofVerb implements Verb<CoverWithRoofVerb.CompoundRoofPayload> {

    @Override
    public String keyword() { return "COVER WITH COMPOUND_ROOF"; }

    @Override
    public VerbResult<CompoundRoofPayload> execute(VerbContext ctx, String... args)
            throws SQLException {
        if (args.length == 0)
            return VerbResult.fail(keyword(),
                    "usage: COVER WITH COMPOUND_ROOF <mesh_type>", null);

        Connection compConn = ctx.componentConn();
        if (compConn == null)
            return VerbResult.fail(keyword(), "componentConn required", null);

        String primaryType = args[0];

        // 1. Load primary mesh params from lod_parametric_mesh_param
        Map<String, String> primaryParams = loadParams(compConn, primaryType);
        if (primaryParams.isEmpty())
            return VerbResult.fail(keyword(),
                    primaryType + " not found in lod_parametric_mesh_param",
                    new CompoundRoofPayload(primaryType, null,
                            List.of(), List.of(), List.of(),
                            List.of(primaryType + " not found")));

        // 2. Resolve generator from lod_parametric_mesh
        String genClass = resolveGeneratorClass(compConn, primaryType);
        if (genClass == null)
            return VerbResult.fail(keyword(),
                    primaryType + " not found in lod_parametric_mesh", null);

        // 3. Generate primary mesh
        ParametricMesh primaryGen = instantiateGenerator(genClass);
        MeshResult primaryResult = primaryGen.generate(new MeshParameters(primaryParams));

        // 4. Find subsidiaries: connects_to=<primary> AND valley_type=T_JUNCTION
        List<String> subsidiaryTypes = findSubsidiaries(compConn, primaryType);

        List<MeshResult> subsidiaryMeshes = new ArrayList<>();
        List<ValleyResult> valleys = new ArrayList<>();
        List<String> allViolations = new ArrayList<>();

        // 5. For each subsidiary: generate mesh, extract slope planes, stitch valleys
        for (String subType : subsidiaryTypes) {
            Map<String, String> subParams = loadParams(compConn, subType);
            String subGenClass = resolveGeneratorClass(compConn, subType);
            ParametricMesh subGen = instantiateGenerator(subGenClass);
            MeshResult subResult = subGen.generate(new MeshParameters(subParams));
            subsidiaryMeshes.add(subResult);

            String primaryAxis = primaryParams.getOrDefault("ridge_axis", "X");
            String subAxis = subParams.getOrDefault("ridge_axis", "X");

            List<Point3D> hv = primaryResult.mesh().vertices();
            List<Point3D> gv = subResult.mesh().vertices();

            // Hip south slope plane
            Plane3D hipSouth;
            if ("X".equals(primaryAxis)) {
                // ridge_axis=X: V0=SW, V1=SE, V5=RE → south slope faces {0,1,5},{0,5,4}
                hipSouth = Plane3D.fromPoints(hv.get(0), hv.get(1), hv.get(5));
            } else {
                allViolations.add("T-junction with ridge_axis=Y hip not yet supported");
                continue;
            }

            // Gable slope planes
            Plane3D gableWest, gableEast;
            if ("Y".equals(subAxis)) {
                // ridge_axis=Y: V0=SW, V1=SE, V2=ridgeS, V3=NW, V4=NE, V5=ridgeN
                // West slope faces {0,2,5},{0,5,3} — plane from V0, V2, V3
                // East slope faces {1,4,5},{1,5,2} — plane from V1, V2, V4
                gableWest = Plane3D.fromPoints(gv.get(0), gv.get(2), gv.get(3));
                gableEast = Plane3D.fromPoints(gv.get(1), gv.get(2), gv.get(4));
            } else {
                allViolations.add("T-junction with ridge_axis=X gable not yet supported");
                continue;
            }

            // 6. Ridge meet point (south ridge vertex of gable)
            Point3D ridgeMeet = gv.get(2);

            // 7. Compute valley stitching
            ValleyResult valley = ValleyStitcher.computeTJunction(
                    hipSouth, gableWest, gableEast, ridgeMeet);
            valleys.add(valley);
            allViolations.addAll(valley.violations());
        }

        // 8. Return result
        CompoundRoofPayload payload = new CompoundRoofPayload(
                primaryType, primaryResult, subsidiaryTypes,
                subsidiaryMeshes, valleys, allViolations);

        if (allViolations.isEmpty()) {
            return VerbResult.ok(keyword(),
                    String.format("%s compound: %d subsidiaries, no violations",
                            primaryType, subsidiaryTypes.size()),
                    payload);
        } else {
            return VerbResult.fail(keyword(),
                    String.format("%s compound: %d violations",
                            primaryType, allViolations.size()),
                    payload);
        }
    }

    // ── DB helpers (raw JDBC to component_library.db) ───────────────

    private Map<String, String> loadParams(Connection conn, String meshType)
            throws SQLException {
        Map<String, String> params = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT param_key, param_value FROM lod_parametric_mesh_param"
                        + " WHERE mesh_type = ?")) {
            ps.setString(1, meshType);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next())
                    params.put(rs.getString("param_key"), rs.getString("param_value"));
            }
        }
        return params;
    }

    private String resolveGeneratorClass(Connection conn, String meshType)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT generator_class FROM lod_parametric_mesh WHERE mesh_type = ?")) {
            ps.setString(1, meshType);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("generator_class") : null;
            }
        }
    }

    private List<String> findSubsidiaries(Connection conn, String primaryType)
            throws SQLException {
        List<String> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT DISTINCT p1.mesh_type FROM lod_parametric_mesh_param p1 "
                        + "JOIN lod_parametric_mesh_param p2 ON p1.mesh_type = p2.mesh_type "
                        + "WHERE p1.param_key = 'connects_to' AND p1.param_value = ? "
                        + "AND p2.param_key = 'valley_type' AND p2.param_value = 'T_JUNCTION'")) {
            ps.setString(1, primaryType);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next())
                    result.add(rs.getString("mesh_type"));
            }
        }
        return result;
    }

    private ParametricMesh instantiateGenerator(String generatorClass) {
        return switch (generatorClass) {
            case "HipRoofMesh"   -> new HipRoofMesh();
            case "GableRoofMesh" -> new GableRoofMesh();
            default -> throw new IllegalArgumentException(
                    "Unknown mesh generator: " + generatorClass);
        };
    }

    /** Payload carrying compound roof results. */
    public record CompoundRoofPayload(
            String primaryType,
            MeshResult primaryMesh,
            List<String> subsidiaryTypes,
            List<MeshResult> subsidiaryMeshes,
            List<ValleyResult> valleys,
            List<String> violations
    ) {}
}
