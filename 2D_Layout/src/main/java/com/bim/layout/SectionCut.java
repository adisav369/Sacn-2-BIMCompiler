package com.bim.layout;

import com.bim.orm.BIMLogger;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.sql.*;
import java.util.*;

/**
 * Mesh-plane intersection for section cuts through the building model.
 *
 * Slices each element's triangle mesh at Z = cut_z and chains the resulting
 * segments into closed 2D contours (polylines in XY world metres).
 *
 * Port of section_cut.py — replaces numpy vectorisation with plain Java loops.
 * Algorithm: Andrew's monotone chain + edge-parametric plane intersection.
 */
public final class SectionCut {
    private SectionCut() {}

    private static final String TAG      = "SectionCut";
    private static final double TOLERANCE = 0.005; // 5 mm endpoint matching
    private static final double EPSILON   = 1e-7;

    // ── Data records ──────────────────────────────────────────────────────────

    /** A closed 2D polyline from slicing one element mesh. */
    public record SectionContour(List<double[]> points, boolean isOuter) {}

    /** All contours for one building element at the cut plane. */
    public record ElementSection(
        String guid, String ifcClass, String elementName, String storey,
        String category,   // "CUT" | "BELOW" | "ABOVE"
        List<SectionContour> contours
    ) {}

    // ── Entry point ───────────────────────────────────────────────────────────

    /**
     * Run a horizontal section cut at the given Z height.
     *
     * @param conn   open connection to the extracted building DB
     * @param cutZ   cut height in world metres (typically 1.0m above FFL)
     */
    public static List<ElementSection> execute(Connection conn, double cutZ)
            throws SQLException {
        BIMLogger.info(TAG, "Section cut at Z={:.3f}m", cutZ);

        String sql = """
            SELECT m.guid, m.ifc_class, m.element_name, m.storey,
                   r.minZ, r.maxZ,
                   bg.vertices, bg.vertex_count, bg.faces, bg.face_count
            FROM elements_meta m
            JOIN elements_rtree r ON m.id = r.id
            LEFT JOIN element_instances ei ON m.guid = ei.guid
            LEFT JOIN base_geometries bg ON ei.geometry_hash = bg.geometry_hash
            ORDER BY m.ifc_class, m.element_name
            """;

        List<ElementSection> result = new ArrayList<>();
        int cutCount = 0;

        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String guid    = rs.getString(1);
                String ifc     = rs.getString(2);
                String name    = rs.getString(3);
                String storey  = rs.getString(4);
                double minZ    = rs.getDouble(5);
                double maxZ    = rs.getDouble(6);
                byte[] vBlob   = rs.getBytes(7);
                int    vCount  = rs.getInt(8);
                byte[] fBlob   = rs.getBytes(9);
                int    fCount  = rs.getInt(10);

                String category = classifyZ(minZ, maxZ, cutZ);

                if (!"CUT".equals(category) || vBlob == null || fBlob == null) {
                    result.add(new ElementSection(guid, ifc, name, storey, category, List.of()));
                    continue;
                }

                float[][] verts = parseVertices(vBlob, vCount);
                int[][] faces   = parseFaces(fBlob, fCount);

                List<double[]> segments = sliceMesh(verts, faces, cutZ);
                List<SectionContour> contours = chainSegments(segments);

                result.add(new ElementSection(guid, ifc, name, storey, category, contours));
                if (!contours.isEmpty()) cutCount++;
            }
        }

        BIMLogger.info(TAG, "Section cut complete: {} elements with contours", cutCount);
        return result;
    }

    /**
     * Extract roof silhouette for elevation drawing by projecting all roof mesh
     * vertices onto the elevation plane and computing the 2D convex hull.
     *
     * @param conn   building DB connection
     * @param face   "front" | "rear" | "left" | "right"
     * @return list of (h, z) hull points; empty if no roof mesh found
     */
    public static List<double[]> roofSilhouette(Connection conn, String face)
            throws SQLException {
        String sql = """
            SELECT bg.vertices, bg.vertex_count
            FROM elements_meta m
            JOIN element_instances ei ON m.guid = ei.guid
            JOIN base_geometries bg ON ei.geometry_hash = bg.geometry_hash
            WHERE m.ifc_class = 'IfcRoof'
            """;

        List<double[]> allPts = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                byte[] blob  = rs.getBytes(1);
                int    count = rs.getInt(2);
                if (blob == null) continue;

                float[][] verts = parseVertices(blob, count);
                for (float[] v : verts) {
                    double h = switch (face) {
                        case "front"            ->  v[0];
                        case "rear"             -> -v[0];
                        case "left"             ->  v[1];
                        default /* right */     -> -v[1];
                    };
                    allPts.add(new double[]{h, v[2]});
                }
            }
        }

        return allPts.size() >= 3 ? GridDerivation.convexHull2D(allPts) : List.of();
    }

    // ── Mesh operations ───────────────────────────────────────────────────────

    /** Classify element Z extents relative to cut plane. */
    static String classifyZ(double minZ, double maxZ, double cutZ) {
        if (minZ > cutZ + EPSILON) return "ABOVE";
        if (maxZ < cutZ - EPSILON) return "BELOW";
        return "CUT";
    }

    /**
     * Slice triangle mesh at Z = cutZ.
     * Returns list of segment endpoint pairs: each double[4] = {x0, y0, x1, y1}.
     */
    static List<double[]> sliceMesh(float[][] verts, int[][] faces, double cutZ) {
        List<double[]> segments = new ArrayList<>();

        for (int[] tri : faces) {
            if (tri[0] >= verts.length || tri[1] >= verts.length || tri[2] >= verts.length)
                continue;

            float[] v0 = verts[tri[0]];
            float[] v1 = verts[tri[1]];
            float[] v2 = verts[tri[2]];

            double d0 = v0[2] - cutZ;
            double d1 = v1[2] - cutZ;
            double d2 = v2[2] - cutZ;

            // All above, all below, or all on-plane → skip
            if (d0 > EPSILON && d1 > EPSILON && d2 > EPSILON) continue;
            if (d0 < -EPSILON && d1 < -EPSILON && d2 < -EPSILON) continue;
            if (Math.abs(d0) <= EPSILON && Math.abs(d1) <= EPSILON
                    && Math.abs(d2) <= EPSILON) continue;

            // Clamp on-plane vertices to one side for consistent case matching
            double s0 = d0 == 0 ? 1 : Math.signum(d0);
            double s1 = d1 == 0 ? 1 : Math.signum(d1);
            double s2 = d2 == 0 ? 1 : Math.signum(d2);

            double[] seg = null;
            if (s0 != s1 && s0 != s2) {
                // v0 isolated
                seg = buildSegment(v0, v1, v2, d0, d1, d2);
            } else if (s1 != s0 && s1 != s2) {
                // v1 isolated
                seg = buildSegment(v1, v0, v2, d1, d0, d2);
            } else {
                // v2 isolated
                seg = buildSegment(v2, v0, v1, d2, d0, d1);
            }
            if (seg != null) segments.add(seg);
        }
        return segments;
    }

    /** Build one intersection segment for isolated vertex va against vb and vc. */
    private static double[] buildSegment(float[] va, float[] vb, float[] vc,
                                         double da, double db, double dc) {
        double[] p0 = interpEdge(va, vb, da, db);
        double[] p1 = interpEdge(va, vc, da, dc);
        return new double[]{p0[0], p0[1], p1[0], p1[1]};
    }

    /** Interpolate XY crossing point on edge va→vb at the cut plane. */
    private static double[] interpEdge(float[] va, float[] vb, double da, double db) {
        double denom = da - db;
        double t = Math.abs(denom) > EPSILON ? da / denom : 0.5;
        return new double[]{
            va[0] + t * (vb[0] - va[0]),
            va[1] + t * (vb[1] - va[1])
        };
    }

    /**
     * Chain unordered segments into closed polyline contours.
     * O(K²) — acceptable for K < 500 segments per element.
     */
    static List<SectionContour> chainSegments(List<double[]> segs) {
        if (segs.isEmpty()) return List.of();

        List<double[]> remaining = new ArrayList<>(segs);
        List<SectionContour> contours = new ArrayList<>();

        while (!remaining.isEmpty()) {
            double[] seed = remaining.remove(0);
            // chain: list of (x,y) points
            List<double[]> chain = new ArrayList<>();
            chain.add(new double[]{seed[0], seed[1]});
            chain.add(new double[]{seed[2], seed[3]});

            boolean changed = true;
            while (changed) {
                changed = false;
                double[] tail = chain.get(chain.size() - 1);
                double[] head = chain.get(0);

                // Closed?
                if (dist(tail, head) < TOLERANCE && chain.size() > 2) break;

                for (int i = 0; i < remaining.size(); i++) {
                    double[] s = remaining.get(i);
                    double[] sA = {s[0], s[1]};
                    double[] sB = {s[2], s[3]};

                    if (dist(tail, sA) < TOLERANCE) {
                        chain.add(sB);
                        remaining.remove(i);
                        changed = true;
                        break;
                    } else if (dist(tail, sB) < TOLERANCE) {
                        chain.add(sA);
                        remaining.remove(i);
                        changed = true;
                        break;
                    } else if (dist(head, sB) < TOLERANCE) {
                        chain.add(0, sA);
                        remaining.remove(i);
                        changed = true;
                        break;
                    } else if (dist(head, sA) < TOLERANCE) {
                        chain.add(0, sB);
                        remaining.remove(i);
                        changed = true;
                        break;
                    }
                }
            }
            if (chain.size() >= 3) {
                contours.add(new SectionContour(chain, signedArea(chain) > 0));
            }
        }
        return contours;
    }

    // ── Blob parsers ──────────────────────────────────────────────────────────

    /** Parse float32 LE vertex blob → float[N][3]. */
    public static float[][] parseVertices(byte[] blob, int count) {
        int expected = count * 3 * 4;
        if (blob.length >= expected && expected > 0) {
            ByteBuffer buf = ByteBuffer.wrap(blob, 0, expected).order(ByteOrder.LITTLE_ENDIAN);
            float[][] v = new float[count][3];
            for (int i = 0; i < count; i++) {
                v[i][0] = buf.getFloat();
                v[i][1] = buf.getFloat();
                v[i][2] = buf.getFloat();
            }
            return v;
        }
        // Fallback: infer count from blob size
        int n = blob.length / 12;
        if (n <= 0) return new float[0][3];
        ByteBuffer buf = ByteBuffer.wrap(blob, 0, n * 12).order(ByteOrder.LITTLE_ENDIAN);
        float[][] v = new float[n][3];
        for (int i = 0; i < n; i++) {
            v[i][0] = buf.getFloat(); v[i][1] = buf.getFloat(); v[i][2] = buf.getFloat();
        }
        return v;
    }

    /** Parse int32 LE face-index blob → int[M][3]. */
    public static int[][] parseFaces(byte[] blob, int count) {
        int expected = count * 3 * 4;
        if (blob.length >= expected && expected > 0) {
            ByteBuffer buf = ByteBuffer.wrap(blob, 0, expected).order(ByteOrder.LITTLE_ENDIAN);
            int[][] f = new int[count][3];
            for (int i = 0; i < count; i++) {
                f[i][0] = buf.getInt(); f[i][1] = buf.getInt(); f[i][2] = buf.getInt();
            }
            return f;
        }
        int n = blob.length / 12;
        if (n <= 0) return new int[0][3];
        ByteBuffer buf = ByteBuffer.wrap(blob, 0, n * 12).order(ByteOrder.LITTLE_ENDIAN);
        int[][] f = new int[n][3];
        for (int i = 0; i < n; i++) {
            f[i][0] = buf.getInt(); f[i][1] = buf.getInt(); f[i][2] = buf.getInt();
        }
        return f;
    }

    // ── Geometry utilities ────────────────────────────────────────────────────

    private static double dist(double[] a, double[] b) {
        double dx = a[0]-b[0], dy = a[1]-b[1];
        return Math.sqrt(dx*dx + dy*dy);
    }

    /** Shoelace signed area — positive = CCW (outer contour). */
    private static double signedArea(List<double[]> pts) {
        double area = 0;
        for (int i = 0; i < pts.size(); i++) {
            double[] a = pts.get(i);
            double[] b = pts.get((i + 1) % pts.size());
            area += (a[0] * b[1]) - (b[0] * a[1]);
        }
        return area / 2;
    }
}
