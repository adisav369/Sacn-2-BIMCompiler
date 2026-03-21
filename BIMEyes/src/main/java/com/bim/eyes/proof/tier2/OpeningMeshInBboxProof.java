package com.bim.eyes.proof.tier2;

import com.bim.eyes.proof.ProofResult;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.sql.*;
import java.util.*;

/** P22: Opening mesh vertices within AABB (vertex-level). */
public final class OpeningMeshInBboxProof {
    private OpeningMeshInBboxProof() {}

    public static List<ProofResult> prove(Connection conn) {
        List<ProofResult> results = new ArrayList<>();
        String sql = """
            SELECT em.guid, em.ifc_class,
                   r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ,
                   ei.geometry_hash, bg.vertices
            FROM elements_meta em
            JOIN elements_rtree r ON em.id = r.id
            JOIN element_instances ei ON ei.guid = em.guid
            JOIN base_geometries bg ON bg.geometry_hash = ei.geometry_hash
            WHERE em.ifc_class IN ('IfcDoor', 'IfcWindow')
              AND (ei.geometry_hash LIKE 'LOD_%' OR ei.geometry_hash LIKE 'GEO_%')
            """;
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String guid = rs.getString("guid");
                double rMinX = rs.getDouble("minX"), rMaxX = rs.getDouble("maxX");
                double rMinY = rs.getDouble("minY"), rMaxY = rs.getDouble("maxY");
                double rMinZ = rs.getDouble("minZ"), rMaxZ = rs.getDouble("maxZ");
                byte[] blob = rs.getBytes("vertices");
                float[] verts = blobToFloats(blob);
                int count = verts.length / 3;
                if (count == 0) {
                    results.add(new ProofResult("P22_OPENING_MESH_IN_BBOX",
                        ProofResult.Status.SKIPPED, guid, "no vertices", 0));
                    continue;
                }
                double actMinX = Double.MAX_VALUE, actMaxX = -Double.MAX_VALUE;
                double actMinY = Double.MAX_VALUE, actMaxY = -Double.MAX_VALUE;
                double actMinZ = Double.MAX_VALUE, actMaxZ = -Double.MAX_VALUE;
                for (int i = 0; i < count; i++) {
                    double vx = verts[i * 3], vy = verts[i * 3 + 1], vz = verts[i * 3 + 2];
                    if (vx < actMinX) actMinX = vx; if (vx > actMaxX) actMaxX = vx;
                    if (vy < actMinY) actMinY = vy; if (vy > actMaxY) actMaxY = vy;
                    if (vz < actMinZ) actMinZ = vz; if (vz > actMaxZ) actMaxZ = vz;
                }
                double tol = 0.001;
                double overflow = Math.max(0, Math.max(
                    Math.max(actMaxX - rMaxX - tol, rMinX - actMinX - tol),
                    Math.max(Math.max(actMaxY - rMaxY - tol, rMinY - actMinY - tol),
                             Math.max(actMaxZ - rMaxZ - tol, rMinZ - actMinZ - tol))));
                if (overflow > 0) {
                    results.add(new ProofResult("P22_OPENING_MESH_IN_BBOX",
                        ProofResult.Status.VIOLATED, guid,
                        String.format("mesh protrudes %.1fmm beyond bbox", overflow * 1000),
                        overflow));
                } else {
                    results.add(new ProofResult("P22_OPENING_MESH_IN_BBOX",
                        ProofResult.Status.PROVEN, guid, "mesh within bbox", 0));
                }
            }
        } catch (SQLException e) {
            results.add(new ProofResult("P22_OPENING_MESH_IN_BBOX",
                ProofResult.Status.SKIPPED, null, "DB read failed: " + e.getMessage(), 0));
        }
        if (results.isEmpty()) {
            results.add(new ProofResult("P22_OPENING_MESH_IN_BBOX",
                ProofResult.Status.SKIPPED, null, "no IfcDoor/IfcWindow with world geometry", 0));
        }
        return results;
    }

    private static float[] blobToFloats(byte[] blob) {
        if (blob == null || blob.length == 0) return new float[0];
        ByteBuffer buf = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN);
        float[] result = new float[blob.length / 4];
        for (int i = 0; i < result.length; i++) result[i] = buf.getFloat();
        return result;
    }
}
