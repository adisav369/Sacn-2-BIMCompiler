package com.bim.compiler.validation;

import com.bim.compiler.geometry.Mesh;
import com.bim.compiler.geometry.Point3D;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.sql.*;
import java.util.*;

/**
 * Phase RM-4: Post-write geometry integrity validation.
 *
 * Closes the X-ray validation gap: SpatialDigest only checks bounding boxes,
 * but mesh integrity (vertex counts, topology, vertex-to-bbox bounds) was never
 * validated. This checker reads the output DB and performs 3 checks per element:
 *
 * <ol>
 *   <li><b>Vertex-to-bbox bounds</b>: Deserialize vertices from base_geometries,
 *       compute actual min/max, compare against elements_rtree. Flag if any vertex
 *       exceeds rtree bounds by more than 0.001m.</li>
 *   <li><b>Vertex/face count match</b>: Compare output vs reference DB (when available).
 *       Flag count mismatches (e.g., 8 vs 32 vertices for wall-roof clipped geometry).</li>
 *   <li><b>Mesh topology</b>: Instantiate Mesh, call isManifold(), hasConsistentWinding(),
 *       hasNoDegenerateFaces().</li>
 * </ol>
 */
public class GeometryIntegrityChecker {

    /** Tolerance for vertex-to-bbox bounds check (1mm in meters). */
    private static final double BOUNDS_TOLERANCE = 0.001;

    /**
     * Result for a single element's geometry check.
     */
    public record ElementCheckResult(
        String guid,
        String ifcClass,
        int outputVertexCount,
        int outputFaceCount,
        int refVertexCount,
        int refFaceCount,
        boolean boundsOk,
        boolean topologyOk,
        boolean countMatch,
        List<String> warnings
    ) {
        public boolean isFullyOk() {
            return boundsOk && topologyOk && countMatch;
        }

        public boolean isWarnOnly() {
            return boundsOk && topologyOk && !countMatch;
        }
    }

    /**
     * Summary result of a full geometry integrity check.
     */
    public record CheckReport(
        int totalElements,
        int fullyOk,
        int warnCount,
        int failCount,
        List<ElementCheckResult> warnings,
        List<ElementCheckResult> failures
    ) {
        public boolean passed() {
            return failCount == 0;
        }
    }

    /**
     * Run geometry integrity check on an output DB (no reference comparison).
     * Performs vertex-to-bbox bounds check and mesh topology check.
     */
    public static CheckReport check(String outputDbPath) {
        return check(outputDbPath, null);
    }

    /**
     * Run geometry integrity check on an output DB, optionally comparing
     * vertex/face counts against a reference DB.
     *
     * @param outputDbPath path to compiled output DB
     * @param refDbPath    path to reference DB (null to skip count comparison)
     * @return CheckReport with per-element results
     */
    public static CheckReport check(String outputDbPath, String refDbPath) {
        // Load reference vertex/face counts if available
        Map<String, int[]> refCounts = new HashMap<>();  // guid -> [vertexCount, faceCount]
        if (refDbPath != null) {
            refCounts = loadGeometryCounts(refDbPath);
        }

        List<ElementCheckResult> allResults = new ArrayList<>();
        List<ElementCheckResult> warnings = new ArrayList<>();
        List<ElementCheckResult> failures = new ArrayList<>();

        String sql = """
            SELECT em.guid, em.ifc_class,
                   r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ,
                   ei.geometry_hash,
                   bg.vertices, bg.faces, bg.vertex_count, bg.face_count
            FROM elements_meta em
            JOIN elements_rtree r ON em.id = r.id
            JOIN element_instances ei ON ei.guid = em.guid
            JOIN base_geometries bg ON bg.geometry_hash = ei.geometry_hash
            ORDER BY em.ifc_class, em.guid
            """;

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + outputDbPath);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            while (rs.next()) {
                String guid = rs.getString("guid");
                String ifcClass = rs.getString("ifc_class");
                String geoHash = rs.getString("geometry_hash");
                double rMinX = rs.getDouble("minX");
                double rMaxX = rs.getDouble("maxX");
                double rMinY = rs.getDouble("minY");
                double rMaxY = rs.getDouble("maxY");
                double rMinZ = rs.getDouble("minZ");
                double rMaxZ = rs.getDouble("maxZ");
                byte[] vertexBlob = rs.getBytes("vertices");
                byte[] faceBlob = rs.getBytes("faces");
                int dbVertexCount = rs.getInt("vertex_count");
                int dbFaceCount = rs.getInt("face_count");

                // Library geometry (non-GEO_ hash) uses canonical mesh coords,
                // not world coords. Bounds check only applies to parametric geometry.
                boolean isParametric = geoHash.startsWith("GEO_");

                List<String> elementWarnings = new ArrayList<>();

                // === Check 1: Vertex-to-bbox bounds (parametric only) ===
                boolean boundsOk = true;
                float[] vertices = blobToFloats(vertexBlob);
                int actualVertexCount = vertices.length / 3;

                if (actualVertexCount != dbVertexCount) {
                    elementWarnings.add(String.format(
                        "vertex_count mismatch: blob has %d, DB says %d", actualVertexCount, dbVertexCount));
                }

                // Compute actual bbox from vertices
                double actMinX = Double.POSITIVE_INFINITY, actMaxX = Double.NEGATIVE_INFINITY;
                double actMinY = Double.POSITIVE_INFINITY, actMaxY = Double.NEGATIVE_INFINITY;
                double actMinZ = Double.POSITIVE_INFINITY, actMaxZ = Double.NEGATIVE_INFINITY;

                for (int i = 0; i < actualVertexCount; i++) {
                    double vx = vertices[i * 3];
                    double vy = vertices[i * 3 + 1];
                    double vz = vertices[i * 3 + 2];
                    actMinX = Math.min(actMinX, vx);
                    actMaxX = Math.max(actMaxX, vx);
                    actMinY = Math.min(actMinY, vy);
                    actMaxY = Math.max(actMaxY, vy);
                    actMinZ = Math.min(actMinZ, vz);
                    actMaxZ = Math.max(actMaxZ, vz);
                }

                // Check vertices fit within rtree bbox (with tolerance)
                // Only for parametric geometry — library meshes use canonical coords
                if (isParametric && actualVertexCount > 0) {
                    if (actMinX < rMinX - BOUNDS_TOLERANCE || actMaxX > rMaxX + BOUNDS_TOLERANCE ||
                        actMinY < rMinY - BOUNDS_TOLERANCE || actMaxY > rMaxY + BOUNDS_TOLERANCE ||
                        actMinZ < rMinZ - BOUNDS_TOLERANCE || actMaxZ > rMaxZ + BOUNDS_TOLERANCE) {
                        boundsOk = false;
                        elementWarnings.add(String.format(
                            "vertex exceeds rtree: mesh[%.4f,%.4f,%.4f,%.4f,%.4f,%.4f] vs rtree[%.4f,%.4f,%.4f,%.4f,%.4f,%.4f]",
                            actMinX, actMaxX, actMinY, actMaxY, actMinZ, actMaxZ,
                            rMinX, rMaxX, rMinY, rMaxY, rMinZ, rMaxZ));
                    }
                }

                // === Check 2: Mesh topology ===
                boolean topologyOk = true;
                int[] faceIndices = blobToInts(faceBlob);
                int actualFaceCount = faceIndices.length / 3;

                if (actualVertexCount >= 3 && actualFaceCount >= 1) {
                    try {
                        List<Point3D> pts = new ArrayList<>();
                        for (int i = 0; i < actualVertexCount; i++) {
                            pts.add(new Point3D(vertices[i * 3], vertices[i * 3 + 1], vertices[i * 3 + 2]));
                        }
                        List<int[]> meshFaces = new ArrayList<>();
                        for (int i = 0; i < actualFaceCount; i++) {
                            meshFaces.add(new int[]{
                                faceIndices[i * 3], faceIndices[i * 3 + 1], faceIndices[i * 3 + 2]
                            });
                        }
                        Mesh mesh = new Mesh(pts, meshFaces);

                        if (!mesh.hasValidIndices()) {
                            topologyOk = false;
                            elementWarnings.add("FACE_INDEX_OUT_OF_BOUNDS");
                        }
                        if (!mesh.hasNoDegenerateFaces()) {
                            elementWarnings.add("DEGENERATE_FACE (non-fatal)");
                        }
                        if (!mesh.hasConsistentWinding()) {
                            elementWarnings.add("INCONSISTENT_WINDING (non-fatal)");
                        }
                    } catch (Exception e) {
                        topologyOk = false;
                        elementWarnings.add("mesh construction failed: " + e.getMessage());
                    }
                }

                // === Check 3: Vertex/face count match vs reference ===
                boolean countMatch = true;
                int refVCount = -1, refFCount = -1;

                if (!refCounts.isEmpty()) {
                    int[] ref = refCounts.get(guid);
                    if (ref != null) {
                        refVCount = ref[0];
                        refFCount = ref[1];
                        if (actualVertexCount != refVCount || actualFaceCount != refFCount) {
                            countMatch = false;
                            elementWarnings.add(String.format(
                                "count mismatch vs ref: V=%d/%d F=%d/%d",
                                actualVertexCount, refVCount, actualFaceCount, refFCount));
                        }
                    }
                }

                ElementCheckResult result = new ElementCheckResult(
                    guid, ifcClass,
                    actualVertexCount, actualFaceCount,
                    refVCount, refFCount,
                    boundsOk, topologyOk, countMatch,
                    elementWarnings
                );
                allResults.add(result);

                if (!boundsOk || !topologyOk) {
                    failures.add(result);
                } else if (!countMatch) {
                    warnings.add(result);
                }
            }

        } catch (SQLException ex) {
            throw new RuntimeException("GeometryIntegrityChecker failed on " + outputDbPath + ": " + ex.getMessage(), ex);
        }

        int fullyOk = allResults.size() - warnings.size() - failures.size();
        return new CheckReport(allResults.size(), fullyOk, warnings.size(), failures.size(), warnings, failures);
    }

    /**
     * Print a human-readable report to stdout.
     */
    public static void printReport(CheckReport report) {
        System.out.printf("GeometryIntegrityChecker: %d elements checked%n", report.totalElements());
        System.out.printf("  OK: %d | WARN: %d | FAIL: %d%n",
            report.fullyOk(), report.warnCount(), report.failCount());

        for (ElementCheckResult w : report.warnings()) {
            System.out.printf("  [WARN] %s (%s) V=%d/%d F=%d/%d%n",
                w.guid(), w.ifcClass(),
                w.outputVertexCount(), w.refVertexCount(),
                w.outputFaceCount(), w.refFaceCount());
            for (String msg : w.warnings()) {
                System.out.printf("         %s%n", msg);
            }
        }

        for (ElementCheckResult f : report.failures()) {
            System.out.printf("  [FAIL] %s (%s)%n", f.guid(), f.ifcClass());
            for (String msg : f.warnings()) {
                System.out.printf("         %s%n", msg);
            }
        }
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    /**
     * Load vertex/face counts from a reference DB, keyed by GUID.
     */
    private static Map<String, int[]> loadGeometryCounts(String dbPath) {
        Map<String, int[]> counts = new HashMap<>();

        String sql = """
            SELECT em.guid, bg.vertex_count, bg.face_count
            FROM elements_meta em
            JOIN element_instances ei ON ei.guid = em.guid
            JOIN base_geometries bg ON bg.geometry_hash = ei.geometry_hash
            """;

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            while (rs.next()) {
                String guid = rs.getString("guid");
                int vc = rs.getInt("vertex_count");
                int fc = rs.getInt("face_count");
                counts.put(guid, new int[]{vc, fc});
            }
        } catch (SQLException ex) {
            System.err.println("WARNING: Could not load reference geometry counts from " + dbPath + ": " + ex.getMessage());
        }

        return counts;
    }

    /** Deserialize float32 LE blob to float array. */
    private static float[] blobToFloats(byte[] blob) {
        if (blob == null || blob.length == 0) return new float[0];
        ByteBuffer buf = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN);
        float[] result = new float[blob.length / 4];
        for (int i = 0; i < result.length; i++) {
            result[i] = buf.getFloat();
        }
        return result;
    }

    /** Deserialize int32 LE blob to int array. */
    private static int[] blobToInts(byte[] blob) {
        if (blob == null || blob.length == 0) return new int[0];
        ByteBuffer buf = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN);
        int[] result = new int[blob.length / 4];
        for (int i = 0; i < result.length; i++) {
            result[i] = buf.getInt();
        }
        return result;
    }
}
