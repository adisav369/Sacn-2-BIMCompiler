package com.bim.compiler.dsl;

import com.bim.compiler.geometry.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.sql.*;
import java.util.*;

/**
 * Maps compiled stairs to LOD400 library StairFlight components.
 *
 * Phase 58: Stair library integration.
 *
 * Library StairFlight components have dimensions:
 * - Width: 1.35-1.55m (along X axis)
 * - Rise: 1.89-2.0m (along Z axis)
 * - Run: 3.0-3.6m (along Y axis)
 *
 * Matching strategy:
 * 1. Try exact match within tolerance
 * 2. Try scalable match (rise most important, then width)
 * 3. Fall back to parametric
 */
public class StairLibraryMapper {

    private static final String LIBRARY_PATH = "library/component_library.db";

    // Tolerance for dimension matching (meters)
    private static final double RISE_TOLERANCE_M = 0.3;   // Rise is critical for code compliance
    private static final double WIDTH_TOLERANCE_M = 0.2;  // Width affects egress capacity
    private static final double RUN_TOLERANCE_M = 0.5;    // Run can be more flexible

    // Scale limits
    // Library stairs have rise ~1.9-2.0m but storey heights range from 3.0m to 4.5m
    // Phase 97: Increased limits to support full-storey flights (ground floor 4.5m)
    private static final double MIN_RISE_SCALE = 0.7;
    private static final double MAX_RISE_SCALE = 2.4;   // Allow 1.95m → 4.68m (covers 4.5m ground)
    private static final double MIN_WIDTH_SCALE = 0.7;
    private static final double MAX_WIDTH_SCALE = 1.3;
    private static final double MIN_RUN_SCALE = 0.6;
    private static final double MAX_RUN_SCALE = 2.0;    // Allow 3.04m → 6.08m (covers 5.87m run)

    /**
     * Library stair component definition.
     */
    public record LibraryStair(
        int id,
        String name,
        String geometryHash,
        double widthM,    // X dimension
        double riseM,     // Z dimension (floor-to-floor height)
        double runM,      // Y dimension (horizontal travel)
        int vertexCount,
        int faceCount
    ) {}

    /**
     * Mapping result with status and scale factors.
     */
    public record StairMappingResult(
        LibraryStair component,
        boolean usesLibrary,
        String fallbackReason,
        double scaleX,  // Width scale
        double scaleY,  // Run scale
        double scaleZ   // Rise scale
    ) {
        public static StairMappingResult library(LibraryStair comp) {
            return new StairMappingResult(comp, true, null, 1.0, 1.0, 1.0);
        }

        public static StairMappingResult libraryScaled(LibraryStair comp,
                                                        double scaleX, double scaleY, double scaleZ) {
            return new StairMappingResult(comp, true, null, scaleX, scaleY, scaleZ);
        }

        public static StairMappingResult parametric(String reason) {
            return new StairMappingResult(null, false, reason, 1.0, 1.0, 1.0);
        }

        public boolean isScaled() {
            return scaleX != 1.0 || scaleY != 1.0 || scaleZ != 1.0;
        }
    }

    private final Connection libraryConn;
    private final List<LibraryStair> stairCache = new ArrayList<>();

    public StairLibraryMapper(String libraryPath) throws SQLException {
        this.libraryConn = DriverManager.getConnection("jdbc:sqlite:" + libraryPath);
        this.libraryConn.createStatement().execute("PRAGMA foreign_keys = ON");
        loadLibraryStairs();
    }

    public StairLibraryMapper() throws SQLException {
        this(LIBRARY_PATH);
    }

    /**
     * Load all StairFlight components from library.
     */
    private void loadLibraryStairs() throws SQLException {
        String query = """
            SELECT cd.id, cd.name, cd.geometry_hash,
                   (cd.local_max_x - cd.local_min_x) as width_m,
                   (cd.local_max_z - cd.local_min_z) as rise_m,
                   (cd.local_max_y - cd.local_min_y) as run_m,
                   cd.vertex_count, cd.face_count
            FROM component_definitions cd
            JOIN component_types ct ON cd.type_id = ct.id
            WHERE ct.ifc_class = 'IfcStairFlight'
            ORDER BY rise_m, width_m
            """;

        try (Statement stmt = libraryConn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            while (rs.next()) {
                stairCache.add(new LibraryStair(
                    rs.getInt("id"),
                    rs.getString("name"),
                    rs.getString("geometry_hash"),
                    rs.getDouble("width_m"),
                    rs.getDouble("rise_m"),
                    rs.getDouble("run_m"),
                    rs.getInt("vertex_count"),
                    rs.getInt("face_count")
                ));
            }
        }

        System.out.printf("[StairLibraryMapper] Loaded %d stair flight components%n", stairCache.size());
    }

    /**
     * Map a stair to library component.
     *
     * @param widthM Target stair width in meters
     * @param riseM Target total rise (floor-to-floor) in meters
     * @param runM Target total run (horizontal) in meters
     * @return Mapping result with library component or fallback reason
     */
    public StairMappingResult mapStair(double widthM, double riseM, double runM) {
        // Try exact match within tolerance
        LibraryStair closest = null;
        double closestDist = Double.MAX_VALUE;

        for (LibraryStair stair : stairCache) {
            double widthDiff = Math.abs(stair.widthM - widthM);
            double riseDiff = Math.abs(stair.riseM - riseM);
            double runDiff = Math.abs(stair.runM - runM);

            if (widthDiff <= WIDTH_TOLERANCE_M &&
                riseDiff <= RISE_TOLERANCE_M &&
                runDiff <= RUN_TOLERANCE_M) {
                // Weight rise more heavily (compliance critical)
                double dist = riseDiff * 2.0 + widthDiff + runDiff * 0.5;
                if (dist < closestDist) {
                    closestDist = dist;
                    closest = stair;
                }
            }
        }

        if (closest != null) {
            System.out.printf("[StairLibraryMapper] (%.2fx%.2fx%.2f) -> library %s (%.2fx%.2fx%.2f)%n",
                widthM, riseM, runM, closest.name, closest.widthM, closest.riseM, closest.runM);
            return StairMappingResult.library(closest);
        }

        // Try scalable match - find best rise match, then scale
        LibraryStair bestScalable = null;
        double bestScore = Double.MAX_VALUE;
        double bestScaleX = 1.0, bestScaleY = 1.0, bestScaleZ = 1.0;

        for (LibraryStair stair : stairCache) {
            double scaleZ = riseM / stair.riseM;  // Rise scale
            double scaleX = widthM / stair.widthM;  // Width scale
            double scaleY = runM / stair.runM;  // Run scale

            // Check if scales are within acceptable range
            if (scaleZ >= MIN_RISE_SCALE && scaleZ <= MAX_RISE_SCALE &&
                scaleX >= MIN_WIDTH_SCALE && scaleX <= MAX_WIDTH_SCALE &&
                scaleY >= MIN_RUN_SCALE && scaleY <= MAX_RUN_SCALE) {

                // Score by how much scaling is needed (less is better)
                // Prioritize rise match, then width
                double riseDeviation = Math.abs(scaleZ - 1.0);
                double widthDeviation = Math.abs(scaleX - 1.0);
                double runDeviation = Math.abs(scaleY - 1.0);
                double score = riseDeviation * 3.0 + widthDeviation * 2.0 + runDeviation;

                if (score < bestScore) {
                    bestScore = score;
                    bestScalable = stair;
                    bestScaleX = scaleX;
                    bestScaleY = scaleY;
                    bestScaleZ = scaleZ;
                }
            }
        }

        if (bestScalable != null) {
            System.out.printf("[StairLibraryMapper] (%.2fx%.2fx%.2f) -> SCALED library %s (scale: %.2f,%.2f,%.2f)%n",
                widthM, riseM, runM, bestScalable.name, bestScaleX, bestScaleY, bestScaleZ);
            return StairMappingResult.libraryScaled(bestScalable, bestScaleX, bestScaleY, bestScaleZ);
        }

        // No match - fall back to parametric
        System.out.printf("[StairLibraryMapper] (%.2fx%.2fx%.2f) -> PARAMETRIC (no library match)%n",
            widthM, riseM, runM);
        return StairMappingResult.parametric(
            String.format("No library stair within scale range for %.2fx%.2fx%.2f", widthM, riseM, runM));
    }

    /**
     * Read library geometry as Mesh for transformation.
     */
    public Mesh readLibraryGeometry(String geometryHash) throws SQLException {
        String query = """
            SELECT vertices, faces, vertex_count, face_count
            FROM component_geometries WHERE geometry_hash = ?
            """;
        try (PreparedStatement ps = libraryConn.prepareStatement(query)) {
            ps.setString(1, geometryHash);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    byte[] vertexBytes = rs.getBytes("vertices");
                    byte[] faceBytes = rs.getBytes("faces");
                    int vertexCount = rs.getInt("vertex_count");
                    int faceCount = rs.getInt("face_count");

                    return parseMeshFromBlobs(vertexBytes, faceBytes, vertexCount, faceCount);
                }
            }
        }
        return null;
    }

    private Mesh parseMeshFromBlobs(byte[] vertexBytes, byte[] faceBytes,
                                     int vertexCount, int faceCount) {
        List<Point3D> vertices = new ArrayList<>();
        ByteBuffer vb = ByteBuffer.wrap(vertexBytes).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < vertexCount; i++) {
            float x = vb.getFloat();
            float y = vb.getFloat();
            float z = vb.getFloat();
            vertices.add(new Point3D(x, y, z));
        }

        List<int[]> faces = new ArrayList<>();
        ByteBuffer fb = ByteBuffer.wrap(faceBytes).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < faceCount; i++) {
            int v0 = fb.getInt();
            int v1 = fb.getInt();
            int v2 = fb.getInt();
            faces.add(new int[]{v0, v1, v2});
        }

        return new Mesh(vertices, faces);
    }

    /**
     * Transform and scale library geometry, write to output database.
     *
     * @param outputConn Output database connection
     * @param geometryHash Library geometry hash
     * @param translateX World X position
     * @param translateY World Y position
     * @param translateZ World Z position
     * @param rotateZ Rotation around Z axis (radians)
     * @param scaleX Width scale factor
     * @param scaleY Run scale factor
     * @param scaleZ Rise scale factor
     * @return New geometry hash for transformed geometry
     */
    public String transformAndWriteGeometry(Connection outputConn, String geometryHash,
                                            double translateX, double translateY, double translateZ,
                                            double rotateZ,
                                            double scaleX, double scaleY, double scaleZ) throws SQLException {
        Mesh libMesh = readLibraryGeometry(geometryHash);
        if (libMesh == null) {
            return null;
        }

        // Transform: scale -> rotate -> translate
        Mesh transformed = libMesh;
        if (Math.abs(scaleX - 1.0) > 0.001 || Math.abs(scaleY - 1.0) > 0.001 || Math.abs(scaleZ - 1.0) > 0.001) {
            transformed = GeometryEngine.scale(transformed, scaleX, scaleY, scaleZ);
        }
        if (Math.abs(rotateZ) > 0.001) {
            transformed = GeometryEngine.rotateZ(transformed, rotateZ);
        }
        transformed = GeometryEngine.translate(transformed, translateX, translateY, translateZ);

        // Generate hash
        String newHash = "STAIR_LOD_" + geometryHash + "_" +
            String.format("%.2f_%.2f_%.2f_%.2f_s%.2f_%.2f_%.2f",
                translateX, translateY, translateZ, rotateZ, scaleX, scaleY, scaleZ)
            .replace('.', '_').replace('-', 'n');

        // Check if already exists
        String checkQuery = "SELECT 1 FROM base_geometries WHERE geometry_hash = ?";
        try (PreparedStatement ps = outputConn.prepareStatement(checkQuery)) {
            ps.setString(1, newHash);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return newHash;
                }
            }
        }

        // Convert and write
        float[] vertexFloats = new float[transformed.vertexCount() * 3];
        int vi = 0;
        for (Point3D v : transformed.vertices()) {
            vertexFloats[vi++] = (float) v.x();
            vertexFloats[vi++] = (float) v.y();
            vertexFloats[vi++] = (float) v.z();
        }

        int[] faceInts = new int[transformed.faceCount() * 3];
        int fi = 0;
        for (int[] face : transformed.faces()) {
            faceInts[fi++] = face[0];
            faceInts[fi++] = face[1];
            faceInts[fi++] = face[2];
        }

        String insertQuery = """
            INSERT INTO base_geometries (geometry_hash, vertices, faces, vertex_count, face_count)
            VALUES (?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = outputConn.prepareStatement(insertQuery)) {
            ps.setString(1, newHash);
            ps.setBytes(2, floatsToBlob(vertexFloats));
            ps.setBytes(3, intsToBlob(faceInts));
            ps.setInt(4, transformed.vertexCount());
            ps.setInt(5, transformed.faceCount());
            ps.executeUpdate();
        }

        return newHash;
    }

    private byte[] floatsToBlob(float[] floats) {
        ByteBuffer buffer = ByteBuffer.allocate(floats.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float f : floats) buffer.putFloat(f);
        return buffer.array();
    }

    private byte[] intsToBlob(int[] ints) {
        ByteBuffer buffer = ByteBuffer.allocate(ints.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (int i : ints) buffer.putInt(i);
        return buffer.array();
    }

    /**
     * Get number of cached library stairs.
     */
    public int getLibraryStairCount() {
        return stairCache.size();
    }

    public void close() throws SQLException {
        if (libraryConn != null) {
            libraryConn.close();
        }
    }

    /**
     * Test mapping standalone.
     */
    public static void main(String[] args) throws SQLException {
        StairLibraryMapper mapper = new StairLibraryMapper();

        System.out.println("\n=== Stair Library Mapping Test ===");

        // Test typical storey heights
        System.out.println("\nTypical residential (2.8m floor-to-floor):");
        mapper.mapStair(1.0, 2.8, 3.5);

        System.out.println("\nTypical commercial (3.5m floor-to-floor):");
        mapper.mapStair(1.2, 3.5, 4.0);

        System.out.println("\nSchool stair (3.0m floor-to-floor, 1.4m width):");
        mapper.mapStair(1.4, 3.0, 3.5);

        System.out.println("\nLibrary dimensions:");
        mapper.mapStair(1.42, 1.95, 3.038);  // Should be near-exact match

        mapper.close();
    }
}
