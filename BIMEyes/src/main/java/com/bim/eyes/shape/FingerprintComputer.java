package com.bim.eyes.shape;

import com.bim.eyes.EyesConstants;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.sql.*;
import java.util.*;

/**
 * Computes geometric fingerprints from extracted or compiled SQLite databases.
 * // Implementing EYES_SRS.md §3.3 — Witness: W-EYES-FINGERPRINT
 */
public final class FingerprintComputer {

    private FingerprintComputer() {}

    /**
     * Compute fingerprints for all elements in an extracted DB.
     * Reads vertex BLOBs from base_geometries, computes actual AABB from vertices.
     */
    public static List<Fingerprint> computeFromExtracted(String extractedDbPath) {
        List<Fingerprint> results = new ArrayList<>();

        Map<String, double[]> centroids = loadCentroids(extractedDbPath);

        String sql = """
            SELECT em.guid, em.ifc_class, em.element_name,
                   bg.vertices, bg.vertex_count, bg.face_count
            FROM elements_meta em
            JOIN element_instances ei ON em.guid = ei.guid
            JOIN base_geometries bg ON ei.geometry_hash = bg.geometry_hash
            """;

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + extractedDbPath);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            while (rs.next()) {
                String guid = rs.getString("guid");
                String ifcClass = rs.getString("ifc_class");
                String elementName = rs.getString("element_name");
                byte[] vertexBlob = rs.getBytes("vertices");
                int vertexCount = rs.getInt("vertex_count");
                int faceCount = rs.getInt("face_count");

                if (vertexBlob == null || vertexBlob.length < 12) continue;

                ByteBuffer buf = ByteBuffer.wrap(vertexBlob).order(ByteOrder.LITTLE_ENDIAN);
                float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
                float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;

                int numVerts = vertexBlob.length / 12;
                for (int i = 0; i < numVerts; i++) {
                    float x = buf.getFloat(), y = buf.getFloat(), z = buf.getFloat();
                    if (x < minX) minX = x; if (x > maxX) maxX = x;
                    if (y < minY) minY = y; if (y > maxY) maxY = y;
                    if (z < minZ) minZ = z; if (z > maxZ) maxZ = z;
                }

                double[] cen = centroids.getOrDefault(guid, new double[]{Double.NaN, Double.NaN, Double.NaN});
                Fingerprint fp = buildFingerprint(guid, ifcClass, elementName,
                    (maxX - minX) * 1000.0, (maxY - minY) * 1000.0, (maxZ - minZ) * 1000.0,
                    vertexCount, faceCount, cen[0], cen[1], cen[2]);
                if (fp != null) results.add(fp);
            }
        } catch (SQLException ex) {
            throw new RuntimeException("FingerprintComputer failed on " + extractedDbPath + ": " + ex.getMessage(), ex);
        }

        return results;
    }

    /**
     * Compute fingerprints for all elements in a compiled output DB.
     * Uses elements_rtree AABB (no vertex BLOBs needed).
     */
    public static List<Fingerprint> computeFromOutput(String outputDbPath) {
        List<Fingerprint> results = new ArrayList<>();

        String sql = """
            SELECT em.guid, em.ifc_class, em.element_name,
                   r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ,
                   COALESCE(bg.vertex_count, 0), COALESCE(bg.face_count, 0)
            FROM elements_meta em
            JOIN elements_rtree r ON em.id = r.id
            LEFT JOIN element_instances ei ON em.guid = ei.guid
            LEFT JOIN base_geometries bg ON ei.geometry_hash = bg.geometry_hash
            """;

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + outputDbPath);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            while (rs.next()) {
                String guid = rs.getString("guid");
                String ifcClass = rs.getString("ifc_class");
                String elementName = rs.getString("element_name");
                double minX = rs.getDouble(4), maxX = rs.getDouble(5);
                double minY = rs.getDouble(6), maxY = rs.getDouble(7);
                double minZ = rs.getDouble(8), maxZ = rs.getDouble(9);
                int vertexCount = rs.getInt(10);
                int faceCount = rs.getInt(11);

                double cx = (minX + maxX) / 2;
                double cy = (minY + maxY) / 2;
                double cz = (minZ + maxZ) / 2;

                Fingerprint fp = buildFingerprint(guid, ifcClass, elementName,
                    (maxX - minX) * 1000.0, (maxY - minY) * 1000.0, (maxZ - minZ) * 1000.0,
                    vertexCount, faceCount, cx, cy, cz);
                if (fp != null) results.add(fp);
            }
        } catch (SQLException ex) {
            throw new RuntimeException("FingerprintComputer failed on " + outputDbPath + ": " + ex.getMessage(), ex);
        }

        return results;
    }

    // ── Internal helpers ──

    private static Fingerprint buildFingerprint(String guid, String ifcClass, String elementName,
            double dimXmm, double dimYmm, double dimZmm,
            int vertexCount, int faceCount,
            double cx, double cy, double cz) {

        double[] dims = {dimXmm, dimYmm, dimZmm};
        Arrays.sort(dims);
        double S = dims[0], M = dims[1], L = dims[2];

        if (L < 0.01) return null;

        double planarity = S / L;
        double elongation = M / L;
        double squareness = (M > 0.01) ? S / M : 0.0;
        double volumeM3 = (S * M * L) / 1e9;
        double topologyRatio = (faceCount > 0) ? (double) vertexCount / faceCount : 0.0;

        ShapeArchetype archetype = ShapeClassifier.classifyArchetype(S, M, L);
        ScaleBand scaleBand = ShapeClassifier.classifyScaleBand(S, M, L);

        return new Fingerprint(guid, ifcClass, elementName,
            S, M, L, planarity, elongation, squareness,
            volumeM3, topologyRatio, archetype, scaleBand,
            cx, cy, cz);
    }

    private static Map<String, double[]> loadCentroids(String dbPath) {
        Map<String, double[]> centroids = new LinkedHashMap<>();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("""
                 SELECT em.guid, r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ
                 FROM elements_meta em JOIN elements_rtree r ON em.id = r.id
                 """)) {
            while (rs.next()) {
                centroids.put(rs.getString("guid"), new double[]{
                    (rs.getDouble("minX") + rs.getDouble("maxX")) / 2,
                    (rs.getDouble("minY") + rs.getDouble("maxY")) / 2,
                    (rs.getDouble("minZ") + rs.getDouble("maxZ")) / 2
                });
            }
        } catch (SQLException ignored) { /* rtree may not exist */ }
        return centroids;
    }
}
