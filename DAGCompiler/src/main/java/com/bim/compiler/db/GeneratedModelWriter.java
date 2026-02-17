package com.bim.compiler.db;

import com.bim.compiler.builder.WallBuilder;
import com.bim.compiler.builder.WallSpec;
import com.bim.compiler.dsl.ConstructionSpec;
import com.bim.compiler.factory.GridPlacementSpec;
import com.bim.compiler.factory.HybridFactory;
import com.bim.compiler.geometry.BoundingBox;
import com.bim.compiler.geometry.Point3D;
import com.bim.compiler.library.ComponentLibrary;
import com.bim.compiler.model.ISpatialElement;
import com.bim.compiler.topology.BIMObjectType;
import com.bim.compiler.topology.Discipline;
import com.bim.compiler.util.BIMLogger;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.sql.*;
import java.util.*;

/**
 * Writes generated BIM elements to SQLite using identical schema to enhanced_federation_GI.db.
 *
 * Pipeline:
 *   DSL → RoomCompiler → ConstructionSpec → GeneratedModelWriter.write(db) → IFC export
 *
 * Tables created:
 *   - elements_meta: Element metadata (guid, discipline, ifc_class, etc.)
 *   - element_transforms: Center position
 *   - elements_rtree: Bounding box for spatial queries
 *   - base_geometries: Geometry data (vertices, faces)
 *   - element_instances: Geometry hash reference
 *   - spatial_structure: Building/storey/space hierarchy
 *   - global_offset: Coordinate offset
 */
public class GeneratedModelWriter {

    private final String dbPath;
    private Connection conn;
    private int elementId = 0;

    // Geometry cache to avoid duplicates
    private final Map<String, Boolean> geometryCache = new HashMap<>();

    public GeneratedModelWriter(String dbPath) {
        this.dbPath = dbPath;
    }

    /**
     * Write a ConstructionSpec to the database.
     */
    public void write(ConstructionSpec spec, HybridFactory factory) throws SQLException {
        // Delete existing file
        new File(dbPath).delete();

        // Connect and create schema
        conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        conn.setAutoCommit(false);

        try {
            createSchema();

            // Write global offset (0,0,0 for generated models)
            writeGlobalOffset(0.0, 0.0, 0.0, 100.0, 100.0, 50.0);

            // Write walls
            BIMLogger.info("GeneratedModelWriter", "Writing {} walls", spec.walls().size());
            for (WallSpec wallSpec : spec.walls()) {
                writeWall(wallSpec, spec.storeyName(), spec.storeyHeight());
            }

            // Write spaces
            BIMLogger.info("GeneratedModelWriter", "Writing {} spaces", spec.spaces().size());
            for (ConstructionSpec.SpaceSpec space : spec.spaces()) {
                writeSpace(space, spec.storeyName());
            }

            // Write sprinklers
            BIMLogger.info("GeneratedModelWriter", "Writing {} sprinkler grids", spec.sprinklerGrids().size());
            for (ConstructionSpec.SprinklerGridSpec gridSpec : spec.sprinklerGrids()) {
                writeSprinklerGrid(gridSpec, spec.storeyName(), factory);
            }

            conn.commit();
            BIMLogger.info("GeneratedModelWriter", "Database written: {}", dbPath);

        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.close();
        }
    }

    private void createSchema() throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // Core tables matching Terminal DB schema
            stmt.execute("""
                CREATE TABLE elements_meta (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    guid TEXT UNIQUE NOT NULL,
                    discipline TEXT NOT NULL,
                    ifc_class TEXT NOT NULL,
                    filepath TEXT,
                    element_name TEXT,
                    element_type TEXT,
                    element_description TEXT,
                    storey TEXT,
                    material_name TEXT,
                    material_rgba TEXT
                )
            """);

            stmt.execute("""
                CREATE TABLE element_transforms (
                    guid TEXT PRIMARY KEY,
                    center_x REAL NOT NULL,
                    center_y REAL NOT NULL,
                    center_z REAL NOT NULL,
                    transform_source TEXT DEFAULT 'generated',
                    FOREIGN KEY (guid) REFERENCES elements_meta(guid)
                )
            """);

            stmt.execute("""
                CREATE VIRTUAL TABLE elements_rtree USING rtree(
                    id,
                    minX, maxX,
                    minY, maxY,
                    minZ, maxZ
                )
            """);

            stmt.execute("""
                CREATE TABLE base_geometries (
                    geometry_hash TEXT PRIMARY KEY,
                    vertices BLOB NOT NULL,
                    faces BLOB NOT NULL,
                    normals BLOB,
                    vertex_count INTEGER NOT NULL,
                    face_count INTEGER NOT NULL
                )
            """);

            stmt.execute("""
                CREATE TABLE element_instances (
                    guid TEXT PRIMARY KEY,
                    geometry_hash TEXT NOT NULL,
                    FOREIGN KEY (geometry_hash) REFERENCES base_geometries(geometry_hash)
                )
            """);

            stmt.execute("""
                CREATE TABLE spatial_structure (
                    guid TEXT PRIMARY KEY,
                    type TEXT NOT NULL,
                    name TEXT,
                    parent_guid TEXT,
                    storey TEXT,
                    FOREIGN KEY (guid) REFERENCES elements_meta(guid)
                )
            """);

            stmt.execute("""
                CREATE TABLE global_offset (
                    offset_x REAL NOT NULL,
                    offset_y REAL NOT NULL,
                    offset_z REAL NOT NULL,
                    extent_x REAL,
                    extent_y REAL,
                    extent_z REAL
                )
            """);

            // Assembly tables (BOM structure)
            // Phase 49: ifc_class added for IFC aggregate entities (IfcStair, etc.)
            stmt.execute("""
                CREATE TABLE element_assemblies (
                    assembly_guid TEXT PRIMARY KEY,
                    assembly_type TEXT NOT NULL,
                    ifc_class TEXT,
                    name TEXT,
                    total_width REAL,
                    total_depth REAL,
                    total_height REAL,
                    storey TEXT,
                    FOREIGN KEY (assembly_guid) REFERENCES elements_meta(guid)
                )
            """);

            stmt.execute("""
                CREATE TABLE assembly_components (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    assembly_guid TEXT NOT NULL,
                    component_guid TEXT NOT NULL,
                    role TEXT NOT NULL,
                    local_x REAL DEFAULT 0,
                    local_y REAL DEFAULT 0,
                    local_z REAL DEFAULT 0,
                    sequence INTEGER DEFAULT 0,
                    optional BOOLEAN DEFAULT FALSE,
                    FOREIGN KEY (assembly_guid) REFERENCES element_assemblies(assembly_guid),
                    FOREIGN KEY (component_guid) REFERENCES elements_meta(guid)
                )
            """);

            // Indexes
            stmt.execute("CREATE INDEX idx_elements_discipline ON elements_meta(discipline)");
            stmt.execute("CREATE INDEX idx_elements_ifc_class ON elements_meta(ifc_class)");
            stmt.execute("CREATE INDEX idx_elements_storey ON elements_meta(storey)");
            stmt.execute("CREATE INDEX idx_assembly_components ON assembly_components(assembly_guid)");
        }
    }

    private void writeGlobalOffset(double x, double y, double z,
                                    double extX, double extY, double extZ) throws SQLException {
        String sql = "INSERT INTO global_offset VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDouble(1, x);
            stmt.setDouble(2, y);
            stmt.setDouble(3, z);
            stmt.setDouble(4, extX);
            stmt.setDouble(5, extY);
            stmt.setDouble(6, extZ);
            stmt.executeUpdate();
        }
    }

    private void writeWall(WallSpec wall, String storeyName, double storeyHeight) throws SQLException {
        // Build wall geometry
        WallBuilder builder = new WallBuilder();
        WallBuilder.GeometryData geom = builder.buildCompleteGeometry(wall);

        String guid = "WALL_" + (++elementId);
        BoundingBox bbox = wall.toBoundingBox();
        Point3D center = bbox.center();

        // Compute geometry hash from vertices
        float[] vertices = geom.vertices();
        int[] faces = geom.faces();
        String geomHash = computeGeometryHash(vertices);

        // Write to tables
        writeElementMeta(guid, Discipline.ARC.name(), "IfcWall",
                        "Generated Wall", wall.thickness().name(), storeyName);
        writeElementTransform(guid, center);
        writeElementRtree(guid, bbox);
        writeGeometry(geomHash, vertices, faces);
        writeElementInstance(guid, geomHash);
        writeSpatialStructure(guid, "IfcWall", "Generated Wall", null, storeyName);
    }

    private void writeSpace(ConstructionSpec.SpaceSpec space, String storeyName) throws SQLException {
        String guid = "SPACE_" + space.name().replace(" ", "_");

        BoundingBox bbox = new BoundingBox(
            space.minX(), space.maxX(),
            space.minY(), space.maxY(),
            0, space.height()
        );
        Point3D center = bbox.center();

        // Simple box geometry for space
        float[] vertices = createBoxVertices(bbox);
        int[] faces = createBoxFaces();
        String geomHash = computeGeometryHash(vertices);

        writeElementMeta(guid, Discipline.ARC.name(), "IfcSpace",
                        space.name(), space.type().name(), storeyName);
        writeElementTransform(guid, center);
        writeElementRtree(guid, bbox);
        writeGeometry(geomHash, vertices, faces);
        writeElementInstance(guid, geomHash);
        writeSpatialStructure(guid, "IfcSpace", space.name(), null, storeyName);
    }

    private void writeSprinklerGrid(ConstructionSpec.SprinklerGridSpec gridSpec,
                                     String storeyName, HybridFactory factory) throws SQLException {
        try {
            GridPlacementSpec placementSpec = GridPlacementSpec.gridBuilder()
                .id("SPRINKLER_" + gridSpec.roomName())
                .type(BIMObjectType.IFC_FIRE_SUPPRESSION_TERMINAL)
                .discipline(Discipline.FP)
                .libraryComponentId("pendent")
                .area(gridSpec.area())
                .spacing(gridSpec.spacing())
                .attachmentZ(gridSpec.attachmentZ())
                .attachmentFace("TOP")
                .build();

            List<ISpatialElement> sprinklers = factory.createGrid(placementSpec);

            for (ISpatialElement sprinkler : sprinklers) {
                String guid = sprinkler.getGuid();
                BoundingBox bbox = sprinkler.getBoundingBox();
                Point3D center = sprinkler.getPosition();

                // Use library geometry hash
                String geomHash = "SPRINKLER_PENDANT_001";  // Simplified

                writeElementMeta(guid, Discipline.FP.name(), "IfcFireSuppressionTerminal",
                                "Pendant Sprinkler", "PENDANT", storeyName);
                writeElementTransform(guid, center);
                writeElementRtree(guid, bbox);
                writeSpatialStructure(guid, "IfcFireSuppressionTerminal", "Pendant Sprinkler", null, storeyName);

                // Sprinkler geometry comes from library - write simplified bbox geometry
                if (!geometryCache.containsKey(geomHash)) {
                    float[] vertices = createBoxVertices(bbox);
                    int[] faces = createBoxFaces();
                    writeGeometry(geomHash, vertices, faces);
                }
                writeElementInstance(guid, geomHash);
            }
        } catch (Exception e) {
            BIMLogger.error("GeneratedModelWriter", "Failed to write sprinklers: {}", e.getMessage());
        }
    }

    // =========================================================================
    // Table writers
    // =========================================================================

    private void writeElementMeta(String guid, String discipline, String ifcClass,
                                   String name, String type, String storey) throws SQLException {
        String sql = """
            INSERT INTO elements_meta (guid, discipline, ifc_class, element_name, element_type, storey)
            VALUES (?, ?, ?, ?, ?, ?)
        """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, guid);
            stmt.setString(2, discipline);
            stmt.setString(3, ifcClass);
            stmt.setString(4, name);
            stmt.setString(5, type);
            stmt.setString(6, storey);
            stmt.executeUpdate();
        }
    }

    private void writeElementTransform(String guid, Point3D center) throws SQLException {
        String sql = "INSERT INTO element_transforms VALUES (?, ?, ?, ?, 'generated')";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, guid);
            stmt.setDouble(2, center.x());
            stmt.setDouble(3, center.y());
            stmt.setDouble(4, center.z());
            stmt.executeUpdate();
        }
    }

    private void writeElementRtree(String guid, BoundingBox bbox) throws SQLException {
        // Get the rowid from elements_meta
        int rowid;
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT id FROM elements_meta WHERE guid = ?")) {
            stmt.setString(1, guid);
            ResultSet rs = stmt.executeQuery();
            rs.next();
            rowid = rs.getInt(1);
        }

        String sql = "INSERT INTO elements_rtree VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, rowid);
            stmt.setDouble(2, bbox.minX());
            stmt.setDouble(3, bbox.maxX());
            stmt.setDouble(4, bbox.minY());
            stmt.setDouble(5, bbox.maxY());
            stmt.setDouble(6, bbox.minZ());
            stmt.setDouble(7, bbox.maxZ());
            stmt.executeUpdate();
        }
    }

    private void writeGeometry(String hash, float[] vertices, int[] faces) throws SQLException {
        if (geometryCache.containsKey(hash)) {
            return;  // Already written
        }

        byte[] vertexBlob = floatArrayToBytes(vertices);
        byte[] faceBlob = intArrayToBytes(faces);

        String sql = "INSERT INTO base_geometries VALUES (?, ?, ?, NULL, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, hash);
            stmt.setBytes(2, vertexBlob);
            stmt.setBytes(3, faceBlob);
            stmt.setInt(4, vertices.length / 3);
            stmt.setInt(5, faces.length / 3);
            stmt.executeUpdate();
        }

        geometryCache.put(hash, true);
    }

    private void writeElementInstance(String guid, String geomHash) throws SQLException {
        String sql = "INSERT INTO element_instances VALUES (?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, guid);
            stmt.setString(2, geomHash);
            stmt.executeUpdate();
        }
    }

    private void writeSpatialStructure(String guid, String type, String name,
                                        String parentGuid, String storey) throws SQLException {
        String sql = "INSERT INTO spatial_structure (guid, type, name, parent_guid, storey) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, guid);
            stmt.setString(2, type);
            stmt.setString(3, name);
            stmt.setString(4, parentGuid);
            stmt.setString(5, storey);
            stmt.executeUpdate();
        }
    }

    // =========================================================================
    // Assembly support (BOM structure)
    // =========================================================================

    /**
     * Write an assembly (parent element that groups components).
     * @param assemblyGuid GUID for the assembly element
     * @param assemblyType Type: WALL_ASSEMBLY, ROOF_ASSEMBLY, etc.
     * @param name Display name
     * @param bbox Bounding box of entire assembly
     * @param storey Storey assignment
     */
    public void writeAssembly(String assemblyGuid, String assemblyType, String name,
                               BoundingBox bbox, String storey) throws SQLException {
        // Write as IfcElementAssembly in elements_meta
        writeElementMeta(assemblyGuid, Discipline.ARC.name(), "IfcElementAssembly",
                        name, assemblyType, storey);
        writeElementTransform(assemblyGuid, bbox.center());
        writeElementRtree(assemblyGuid, bbox);
        writeSpatialStructure(assemblyGuid, "IfcElementAssembly", name, null, storey);

        // Write assembly record
        String sql = """
            INSERT INTO element_assemblies (assembly_guid, assembly_type, name,
                                            total_width, total_depth, total_height, storey)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, assemblyGuid);
            stmt.setString(2, assemblyType);
            stmt.setString(3, name);
            stmt.setDouble(4, bbox.maxX() - bbox.minX());
            stmt.setDouble(5, bbox.maxY() - bbox.minY());
            stmt.setDouble(6, bbox.maxZ() - bbox.minZ());
            stmt.setString(7, storey);
            stmt.executeUpdate();
        }
    }

    /**
     * Write a component as part of an assembly.
     * @param assemblyGuid Parent assembly GUID
     * @param componentGuid Component element GUID
     * @param role Role in assembly: FRAME, CLADDING, FASTENER
     * @param localOffset Offset from assembly origin
     * @param sequence Assembly order (0 = first)
     * @param optional True if BOM-only (no geometry)
     */
    public void writeAssemblyComponent(String assemblyGuid, String componentGuid,
                                        String role, Point3D localOffset,
                                        int sequence, boolean optional) throws SQLException {
        String sql = """
            INSERT INTO assembly_components
            (assembly_guid, component_guid, role, local_x, local_y, local_z, sequence, optional)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, assemblyGuid);
            stmt.setString(2, componentGuid);
            stmt.setString(3, role);
            stmt.setDouble(4, localOffset.x());
            stmt.setDouble(5, localOffset.y());
            stmt.setDouble(6, localOffset.z());
            stmt.setInt(7, sequence);
            stmt.setBoolean(8, optional);
            stmt.executeUpdate();
        }
    }

    /**
     * Write a simple component element (for use in assemblies).
     */
    public void writeComponent(String guid, String ifcClass, String name, String type,
                                BoundingBox bbox, float[] vertices, int[] faces,
                                String storey) throws SQLException {
        String geomHash = computeGeometryHash(vertices);

        writeElementMeta(guid, Discipline.ARC.name(), ifcClass, name, type, storey);
        writeElementTransform(guid, bbox.center());
        writeElementRtree(guid, bbox);
        writeGeometry(geomHash, vertices, faces);
        writeElementInstance(guid, geomHash);
        writeSpatialStructure(guid, ifcClass, name, null, storey);
    }

    /**
     * Public access for test classes.
     */
    public Connection getConnection() throws SQLException {
        if (conn == null || conn.isClosed()) {
            conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            conn.setAutoCommit(false);
        }
        return conn;
    }

    public void initializeForTest() throws SQLException {
        new File(dbPath).delete();
        conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        conn.setAutoCommit(false);
        createSchema();
        writeGlobalOffset(0.0, 0.0, 0.0, 100.0, 100.0, 50.0);
    }

    public void commitAndClose() throws SQLException {
        if (conn != null && !conn.isClosed()) {
            conn.commit();
            conn.close();
        }
    }

    // =========================================================================
    // Geometry helpers
    // =========================================================================

    private String computeGeometryHash(float[] vertices) {
        // Simple hash from vertex data
        int hash = Arrays.hashCode(vertices);
        return String.format("GEOM_%08X", hash);
    }

    private float[] createBoxVertices(BoundingBox bbox) {
        // 8 vertices for a box
        return new float[] {
            (float)bbox.minX(), (float)bbox.minY(), (float)bbox.minZ(),
            (float)bbox.maxX(), (float)bbox.minY(), (float)bbox.minZ(),
            (float)bbox.maxX(), (float)bbox.maxY(), (float)bbox.minZ(),
            (float)bbox.minX(), (float)bbox.maxY(), (float)bbox.minZ(),
            (float)bbox.minX(), (float)bbox.minY(), (float)bbox.maxZ(),
            (float)bbox.maxX(), (float)bbox.minY(), (float)bbox.maxZ(),
            (float)bbox.maxX(), (float)bbox.maxY(), (float)bbox.maxZ(),
            (float)bbox.minX(), (float)bbox.maxY(), (float)bbox.maxZ()
        };
    }

    private int[] createBoxFaces() {
        // 12 triangles (6 faces × 2 triangles)
        return new int[] {
            0, 1, 2,  0, 2, 3,  // bottom
            4, 6, 5,  4, 7, 6,  // top
            0, 4, 5,  0, 5, 1,  // front
            2, 6, 7,  2, 7, 3,  // back
            0, 3, 7,  0, 7, 4,  // left
            1, 5, 6,  1, 6, 2   // right
        };
    }

    private byte[] floatArrayToBytes(float[] arr) {
        ByteBuffer buffer = ByteBuffer.allocate(arr.length * 4);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        for (float f : arr) {
            buffer.putFloat(f);
        }
        return buffer.array();
    }

    private byte[] intArrayToBytes(int[] arr) {
        ByteBuffer buffer = ByteBuffer.allocate(arr.length * 4);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        for (int i : arr) {
            buffer.putInt(i);
        }
        return buffer.array();
    }
}
