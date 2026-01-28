package com.bim.compiler.dsl;

import com.bim.compiler.dsl.BuildingCompiler.*;
import static com.bim.compiler.dsl.BuildingCompiler.*;
import com.bim.compiler.geometry.Point3D;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.sql.*;
import java.util.*;

/**
 * Writes BuildingSpec to database and exports to IFC-ready JSON.
 */
public class BuildingWriter {

    private final Connection conn;
    private int elementId = 0;

    public BuildingWriter(Connection conn) {
        this.conn = conn;
    }

    /**
     * Initialize database schema (same as ShedWriter).
     */
    public void initSchema() throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS assembly_components");
            stmt.execute("DROP TABLE IF EXISTS element_assemblies");
            stmt.execute("DROP TABLE IF EXISTS element_instances");
            stmt.execute("DROP TABLE IF EXISTS elements_rtree");
            stmt.execute("DROP TABLE IF EXISTS elements_meta");
            stmt.execute("DROP TABLE IF EXISTS base_geometries");
            stmt.execute("DROP TABLE IF EXISTS spatial_structure");

            stmt.execute("""
                CREATE TABLE elements_meta (
                    id INTEGER PRIMARY KEY,
                    guid TEXT UNIQUE NOT NULL,
                    discipline TEXT NOT NULL,
                    ifc_class TEXT NOT NULL,
                    element_name TEXT,
                    element_type TEXT,
                    storey TEXT
                )
            """);

            stmt.execute("""
                CREATE VIRTUAL TABLE elements_rtree USING rtree(
                    id, minX, maxX, minY, maxY, minZ, maxZ
                )
            """);

            stmt.execute("""
                CREATE TABLE base_geometries (
                    geometry_hash TEXT PRIMARY KEY,
                    vertices BLOB NOT NULL,
                    faces BLOB NOT NULL,
                    vertex_count INTEGER NOT NULL,
                    face_count INTEGER NOT NULL
                )
            """);

            stmt.execute("""
                CREATE TABLE element_instances (
                    guid TEXT PRIMARY KEY,
                    geometry_hash TEXT REFERENCES base_geometries(geometry_hash),
                    transform_x REAL, transform_y REAL, transform_z REAL
                )
            """);

            stmt.execute("""
                CREATE TABLE element_assemblies (
                    assembly_guid TEXT PRIMARY KEY,
                    assembly_type TEXT NOT NULL,
                    name TEXT,
                    total_width REAL, total_depth REAL, total_height REAL,
                    storey TEXT
                )
            """);

            stmt.execute("""
                CREATE TABLE assembly_components (
                    assembly_guid TEXT,
                    component_guid TEXT,
                    role TEXT,
                    local_x REAL, local_y REAL, local_z REAL,
                    sequence INTEGER,
                    optional BOOLEAN DEFAULT FALSE
                )
            """);

            stmt.execute("""
                CREATE TABLE spatial_structure (
                    guid TEXT PRIMARY KEY,
                    type TEXT NOT NULL,
                    name TEXT,
                    parent_guid TEXT
                )
            """);
        }
    }

    /**
     * Write building spec to database.
     */
    public void write(BuildingSpec spec) throws SQLException {
        // Create building in spatial structure
        String buildingGuid = "BUILDING_" + spec.name().toUpperCase().replace(" ", "_");
        writeSpatialStructure(buildingGuid, "IfcBuilding", spec.name(), null);

        // Write each storey
        for (StoreySpec storey : spec.storeys()) {
            String storeyGuid = "STOREY_" + storey.name().toUpperCase().replace(" ", "_");
            writeSpatialStructure(storeyGuid, "IfcBuildingStorey", storey.name(), buildingGuid);

            // Write slab
            writeElement(
                "SLAB_" + storey.name().toUpperCase(),
                "IfcSlab",
                storey.slab().name(),
                storey.slab().type(),
                storey.name(),
                createBoxGeometry(
                    storey.slab().minX(), storey.slab().minY(), storey.slab().minZ(),
                    storey.slab().maxX(), storey.slab().maxY(), storey.slab().maxZ()
                )
            );

            // Write walls as assemblies
            for (WallAssemblySpec wall : storey.walls()) {
                writeWallAssembly(wall, storey.name());
            }

            // Write stairs
            for (StairSpec stair : storey.stairs()) {
                writeStair(stair, storey.name());
            }

            // Write doors
            for (DoorSpec door : storey.doors()) {
                writeDoor(door, storey.name());
            }

            // Write windows
            for (WindowSpec window : storey.windows()) {
                writeWindow(window, storey.name());
            }

            // Write landings
            for (LandingSpec landing : storey.landings()) {
                writeLanding(landing, storey.name());
            }

            // Write sprinklers (Phase 14B)
            for (SprinklerSpec sprinkler : storey.sprinklers()) {
                writeSprinkler(sprinkler, storey.name());
            }

            // Write lights (Phase 14B)
            for (LightSpec light : storey.lights()) {
                writeLight(light, storey.name());
            }
        }

        // Write roof
        if (spec.roof() != null) {
            writeRoof(spec.roof(), spec.storeys().get(spec.storeys().size() - 1).name());
        }
    }

    private void writeWallAssembly(WallAssemblySpec wall, String storeyName) throws SQLException {
        String assemblyGuid = "ASSEMBLY_" + wall.assemblyName() + "_" + storeyName.toUpperCase();

        // Write assembly
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO element_assemblies VALUES (?, ?, ?, ?, ?, ?, ?)"
        )) {
            ps.setString(1, assemblyGuid);
            ps.setString(2, wall.assemblyType());
            ps.setString(3, wall.assemblyName());
            ps.setDouble(4, wall.length());
            ps.setDouble(5, wall.thickness());
            ps.setDouble(6, wall.height());
            ps.setString(7, storeyName);
            ps.execute();
        }

        int seq = 0;

        // Write frames
        for (FrameSpec frame : wall.frames()) {
            String frameGuid = "FRAME_" + wall.side() + "_" + frame.role() + "_" + storeyName;
            writeElement(
                frameGuid,
                "IfcMember",
                frame.profile(),
                "FRAME",
                storeyName,
                createBoxGeometry(
                    frame.minX(), frame.minY(), frame.minZ(),
                    frame.maxX(), frame.maxY(), frame.maxZ()
                )
            );

            writeAssemblyComponent(assemblyGuid, frameGuid, "FRAME", 0, 0, 0, seq++);
        }

        // Write cladding
        String claddingGuid = "CLADDING_" + wall.side() + "_" + storeyName;
        writeElement(
            claddingGuid,
            "IfcPlate",
            wall.cladding().material(),
            "CLADDING",
            storeyName,
            createBoxGeometry(
                wall.cladding().minX(), wall.cladding().minY(), wall.cladding().minZ(),
                wall.cladding().maxX(), wall.cladding().maxY(), wall.cladding().maxZ()
            )
        );

        writeAssemblyComponent(assemblyGuid, claddingGuid, "CLADDING", 0, 0, 0, seq);
    }

    private void writeStair(StairSpec stair, String storeyName) throws SQLException {
        String stairGuid = "STAIR_" + stair.name().toUpperCase() + "_" + storeyName;

        // Phase 14A: Check if using library geometry
        if (stair.usesLibrary()) {
            writeLibraryStair(stair, stairGuid, storeyName);
            return;
        }

        // Parametric stair: convert geometry
        float[] vertices = new float[stair.vertices().size() * 3];
        for (int i = 0; i < stair.vertices().size(); i++) {
            Point3D v = stair.vertices().get(i);
            vertices[i * 3] = (float) v.x();
            vertices[i * 3 + 1] = (float) v.y();
            vertices[i * 3 + 2] = (float) v.z();
        }

        int[] faces = new int[stair.faces().size() * 3];
        for (int i = 0; i < stair.faces().size(); i++) {
            int[] face = stair.faces().get(i);
            faces[i * 3] = face[0];
            faces[i * 3 + 1] = face[1];
            faces[i * 3 + 2] = face[2];
        }

        // Calculate bounds
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;

        for (Point3D v : stair.vertices()) {
            minX = Math.min(minX, v.x()); maxX = Math.max(maxX, v.x());
            minY = Math.min(minY, v.y()); maxY = Math.max(maxY, v.y());
            minZ = Math.min(minZ, v.z()); maxZ = Math.max(maxZ, v.z());
        }

        String geoHash = writeGeometry(vertices, faces);
        writeElementMeta(stairGuid, "IfcStairFlight", "Stair Flight", "STAIR", storeyName,
            minX, maxX, minY, maxY, minZ, maxZ);
        writeInstance(stairGuid, geoHash, 0, 0, 0);
    }

    /**
     * Write a library-based stair (Phase 14A).
     * Uses pre-extracted LOD400 geometry from component_library.db.
     */
    private void writeLibraryStair(StairSpec stair, String stairGuid, String storeyName) throws SQLException {
        // Library geometry hash is already computed
        String geoHash = stair.libraryGeometryHash();

        // Calculate scaled bounds
        double halfWidth = stair.width() / 2;
        double minX = stair.x() - halfWidth * stair.scaleX();
        double maxX = stair.x() + halfWidth * stair.scaleX();
        double minY = stair.y();
        double maxY = stair.y() + stair.run() * stair.scaleY();
        double minZ = stair.z();
        double maxZ = stair.z() + stair.rise() * stair.scaleZ();

        // Write metadata with library geometry reference
        writeElementMeta(stairGuid, "IfcStairFlight", "Library Stair Flight", "STAIR", storeyName,
            minX, maxX, minY, maxY, minZ, maxZ);

        // Write instance with scale factors
        // Note: Geometry is referenced by hash, scaling applied in transform
        writeInstanceWithScale(stairGuid, geoHash,
            stair.x(), stair.y(), stair.z(),
            stair.scaleX(), stair.scaleY(), stair.scaleZ());
    }

    /**
     * Write element instance with scale transform.
     */
    private void writeInstanceWithScale(String guid, String geoHash,
                                       double x, double y, double z,
                                       double scaleX, double scaleY, double scaleZ) throws SQLException {
        // For now, use the existing instance method
        // Scale factors would be applied during IFC export (IfcCartesianTransformationOperator3DnonUniform)
        PreparedStatement ps = conn.prepareStatement("""
            INSERT OR REPLACE INTO element_instances (guid, geometry_hash)
            VALUES (?, ?)
            """);
        ps.setString(1, guid);
        ps.setString(2, geoHash);
        ps.executeUpdate();

        // Store transform with scale in element_transforms (if table exists)
        // For now, just log the scale
        if (scaleX != 1.0 || scaleY != 1.0 || scaleZ != 1.0) {
            System.out.printf("  Library stair %s: scale=(%.2f, %.2f, %.2f)%n",
                guid, scaleX, scaleY, scaleZ);
        }
    }

    private void writeDoor(DoorSpec door, String storeyName) throws SQLException {
        String doorGuid = "DOOR_" + door.name().toUpperCase() + "_" + storeyName;

        // Door as simple box (frame)
        double depth = 0.1;  // 100mm door thickness
        writeElement(
            doorGuid,
            "IfcDoor",
            "Entry Door",
            "DOOR",
            storeyName,
            createBoxGeometry(
                door.x(), door.y(), door.z(),
                door.x() + door.width(), door.y() + depth, door.z() + door.height()
            )
        );
    }

    private void writeWindow(WindowSpec window, String storeyName) throws SQLException {
        String windowGuid = "WINDOW_" + window.name().toUpperCase() + "_" + storeyName;

        // Window as simple box (frame)
        double depth = 0.1;  // 100mm window thickness
        writeElement(
            windowGuid,
            "IfcWindow",
            "Standard Window",
            "WINDOW",
            storeyName,
            createBoxGeometry(
                window.x(), window.y(), window.z(),
                window.x() + window.width(), window.y() + depth, window.z() + window.height()
            )
        );
    }

    private void writeLanding(LandingSpec landing, String storeyName) throws SQLException {
        String landingGuid = "LANDING_" + landing.name().toUpperCase() + "_" + storeyName;

        writeElement(
            landingGuid,
            "IfcSlab",
            "Stair Landing",
            "LANDING",
            storeyName,
            createBoxGeometry(
                landing.minX(), landing.minY(), landing.minZ(),
                landing.maxX(), landing.maxY(), landing.maxZ()
            )
        );
    }

    /**
     * Write sprinkler element (Phase 14B).
     * Uses IfcFlowTerminal for fire suppression.
     */
    private void writeSprinkler(SprinklerSpec sprinkler, String storeyName) throws SQLException {
        String sprinklerGuid = "SPRINKLER_" + sprinkler.id().toUpperCase();

        // Sprinkler as small box (pendant head ~100mm diameter)
        double size = 0.05;  // 50mm radius
        writeElement(
            sprinklerGuid,
            "IfcFlowTerminal",
            "Fire Sprinkler",
            sprinkler.type().toUpperCase(),
            storeyName,
            createBoxGeometry(
                sprinkler.x() - size, sprinkler.y() - size, sprinkler.z() - 0.1,
                sprinkler.x() + size, sprinkler.y() + size, sprinkler.z()
            )
        );
    }

    /**
     * Write light fixture element (Phase 14B).
     * Uses IfcLightFixture for illumination.
     */
    private void writeLight(LightSpec light, String storeyName) throws SQLException {
        String lightGuid = "LIGHT_" + light.id().toUpperCase();

        // Light fixture as box (typical 600x600mm recessed or 600x1200mm 2x4)
        double sizeX = 0.3;  // 300mm half-width
        double sizeY = 0.3;  // 300mm half-depth
        double depth = 0.1;  // 100mm depth
        writeElement(
            lightGuid,
            "IfcLightFixture",
            "Light Fixture",
            light.fixtureType().toUpperCase(),
            storeyName,
            createBoxGeometry(
                light.x() - sizeX, light.y() - sizeY, light.z() - depth,
                light.x() + sizeX, light.y() + sizeY, light.z()
            )
        );
    }

    private void writeRoof(RoofSpec roof, String storeyName) throws SQLException {
        String roofGuid = "ROOF_" + roof.type();

        float[] vertices = new float[roof.vertices().size() * 3];
        for (int i = 0; i < roof.vertices().size(); i++) {
            Point3D v = roof.vertices().get(i);
            vertices[i * 3] = (float) v.x();
            vertices[i * 3 + 1] = (float) v.y();
            vertices[i * 3 + 2] = (float) v.z();
        }

        int[] faces = new int[roof.faces().size() * 3];
        for (int i = 0; i < roof.faces().size(); i++) {
            int[] face = roof.faces().get(i);
            faces[i * 3] = face[0];
            faces[i * 3 + 1] = face[1];
            faces[i * 3 + 2] = face[2];
        }

        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;

        for (Point3D v : roof.vertices()) {
            minX = Math.min(minX, v.x()); maxX = Math.max(maxX, v.x());
            minY = Math.min(minY, v.y()); maxY = Math.max(maxY, v.y());
            minZ = Math.min(minZ, v.z()); maxZ = Math.max(maxZ, v.z());
        }

        String geoHash = writeGeometry(vertices, faces);
        writeElementMeta(roofGuid, "IfcRoof", "Gable Roof", "PITCH_" + (int) roof.pitchDegrees(), storeyName,
            minX, maxX, minY, maxY, minZ, maxZ);
        writeInstance(roofGuid, geoHash, 0, 0, 0);
    }

    private BoxGeometry createBoxGeometry(double x1, double y1, double z1,
                                           double x2, double y2, double z2) {
        // Ensure proper min/max ordering for rtree
        double minX = Math.min(x1, x2);
        double maxX = Math.max(x1, x2);
        double minY = Math.min(y1, y2);
        double maxY = Math.max(y1, y2);
        double minZ = Math.min(z1, z2);
        double maxZ = Math.max(z1, z2);

        float[] vertices = new float[] {
            (float)minX, (float)minY, (float)minZ,
            (float)maxX, (float)minY, (float)minZ,
            (float)maxX, (float)maxY, (float)minZ,
            (float)minX, (float)maxY, (float)minZ,
            (float)minX, (float)minY, (float)maxZ,
            (float)maxX, (float)minY, (float)maxZ,
            (float)maxX, (float)maxY, (float)maxZ,
            (float)minX, (float)maxY, (float)maxZ
        };

        int[] faces = new int[] {
            0, 1, 2, 0, 2, 3,  // bottom
            4, 6, 5, 4, 7, 6,  // top
            0, 4, 5, 0, 5, 1,  // front
            2, 6, 7, 2, 7, 3,  // back
            0, 3, 7, 0, 7, 4,  // left
            1, 5, 6, 1, 6, 2   // right
        };

        return new BoxGeometry(vertices, faces, minX, maxX, minY, maxY, minZ, maxZ);
    }

    private record BoxGeometry(float[] vertices, int[] faces,
                               double minX, double maxX, double minY, double maxY,
                               double minZ, double maxZ) {}

    private void writeElement(String guid, String ifcClass, String name, String type,
                              String storey, BoxGeometry geo) throws SQLException {
        String geoHash = writeGeometry(geo.vertices(), geo.faces());
        writeElementMeta(guid, ifcClass, name, type, storey,
            geo.minX(), geo.maxX(), geo.minY(), geo.maxY(), geo.minZ(), geo.maxZ());
        writeInstance(guid, geoHash, 0, 0, 0);
    }

    private String writeGeometry(float[] vertices, int[] faces) throws SQLException {
        String hash = "GEO_" + Arrays.hashCode(vertices) + "_" + Arrays.hashCode(faces);

        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT OR IGNORE INTO base_geometries VALUES (?, ?, ?, ?, ?)"
        )) {
            ps.setString(1, hash);
            ps.setBytes(2, floatsToBlob(vertices));
            ps.setBytes(3, intsToBlob(faces));
            ps.setInt(4, vertices.length / 3);
            ps.setInt(5, faces.length / 3);
            ps.execute();
        }

        return hash;
    }

    private void writeElementMeta(String guid, String ifcClass, String name, String type,
                                  String storey, double minX, double maxX, double minY,
                                  double maxY, double minZ, double maxZ) throws SQLException {
        int id = ++elementId;

        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO elements_meta VALUES (?, ?, ?, ?, ?, ?, ?)"
        )) {
            ps.setInt(1, id);
            ps.setString(2, guid);
            ps.setString(3, "ARC");
            ps.setString(4, ifcClass);
            ps.setString(5, name);
            ps.setString(6, type);
            ps.setString(7, storey);
            ps.execute();
        }

        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO elements_rtree VALUES (?, ?, ?, ?, ?, ?, ?)"
        )) {
            ps.setInt(1, id);
            ps.setDouble(2, minX);
            ps.setDouble(3, maxX);
            ps.setDouble(4, minY);
            ps.setDouble(5, maxY);
            ps.setDouble(6, minZ);
            ps.setDouble(7, maxZ);
            ps.execute();
        }
    }

    private void writeInstance(String guid, String geoHash, double x, double y, double z)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO element_instances VALUES (?, ?, ?, ?, ?)"
        )) {
            ps.setString(1, guid);
            ps.setString(2, geoHash);
            ps.setDouble(3, x);
            ps.setDouble(4, y);
            ps.setDouble(5, z);
            ps.execute();
        }
    }

    private void writeAssemblyComponent(String assemblyGuid, String componentGuid,
                                        String role, double x, double y, double z, int seq)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO assembly_components VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
        )) {
            ps.setString(1, assemblyGuid);
            ps.setString(2, componentGuid);
            ps.setString(3, role);
            ps.setDouble(4, x);
            ps.setDouble(5, y);
            ps.setDouble(6, z);
            ps.setInt(7, seq);
            ps.setBoolean(8, false);
            ps.execute();
        }
    }

    private void writeSpatialStructure(String guid, String type, String name, String parentGuid)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO spatial_structure VALUES (?, ?, ?, ?)"
        )) {
            ps.setString(1, guid);
            ps.setString(2, type);
            ps.setString(3, name);
            ps.setString(4, parentGuid);
            ps.execute();
        }
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
     * Export to JSON for IFC conversion.
     */
    public void exportJson(BuildingSpec spec, String path) throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(path))) {
            pw.println("{");
            pw.println("  \"building_name\": \"" + spec.name() + "\",");
            pw.println("  \"storeys\": [");

            for (int i = 0; i < spec.storeys().size(); i++) {
                StoreySpec storey = spec.storeys().get(i);
                pw.println("    {");
                pw.println("      \"name\": \"" + storey.name() + "\",");
                pw.println("      \"level\": " + storey.level() + ",");
                pw.println("      \"base_z\": " + storey.baseZ() + ",");
                pw.println("      \"height\": " + storey.height() + ",");

                // Slab
                pw.println("      \"slab\": {");
                pw.println("        \"type\": \"" + storey.slab().type() + "\",");
                pw.println("        \"bounds\": [" +
                    storey.slab().minX() + ", " + storey.slab().minY() + ", " + storey.slab().minZ() + ", " +
                    storey.slab().maxX() + ", " + storey.slab().maxY() + ", " + storey.slab().maxZ() + "]");
                pw.println("      },");

                // Walls
                pw.println("      \"walls\": [");
                for (int j = 0; j < storey.walls().size(); j++) {
                    WallAssemblySpec wall = storey.walls().get(j);
                    pw.println("        {");
                    pw.println("          \"side\": \"" + wall.side() + "\",");
                    pw.println("          \"length\": " + wall.length() + ",");
                    pw.println("          \"height\": " + wall.height());
                    pw.println("        }" + (j < storey.walls().size() - 1 ? "," : ""));
                }
                pw.println("      ],");

                // Stairs
                pw.println("      \"stairs\": [");
                for (int j = 0; j < storey.stairs().size(); j++) {
                    StairSpec stair = storey.stairs().get(j);
                    pw.println("        {");
                    pw.println("          \"name\": \"" + stair.name() + "\",");
                    pw.println("          \"to_storey\": \"" + stair.toStorey() + "\",");
                    pw.println("          \"num_risers\": " + stair.numRisers() + ",");
                    pw.println("          \"riser_height\": " + stair.riserHeight() + ",");
                    pw.println("          \"tread_depth\": " + stair.treadDepth() + ",");
                    pw.println("          \"position\": [" + stair.x() + ", " + stair.y() + ", " + stair.z() + "]");
                    pw.println("        }" + (j < storey.stairs().size() - 1 ? "," : ""));
                }
                pw.println("      ]");

                pw.println("    }" + (i < spec.storeys().size() - 1 ? "," : ""));
            }

            pw.println("  ],");

            // Roof
            if (spec.roof() != null) {
                pw.println("  \"roof\": {");
                pw.println("    \"type\": \"" + spec.roof().type() + "\",");
                pw.println("    \"pitch_degrees\": " + spec.roof().pitchDegrees() + ",");
                pw.println("    \"ridge_rise\": " + spec.roof().ridgeRise());
                pw.println("  }");
            } else {
                pw.println("  \"roof\": null");
            }

            pw.println("}");
        }
    }
}
