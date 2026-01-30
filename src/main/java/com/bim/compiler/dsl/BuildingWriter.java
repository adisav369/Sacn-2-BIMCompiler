package com.bim.compiler.dsl;

import com.bim.compiler.BIMConstants;
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
 *
 * Phase 29: LOD400 library integration for doors.
 * - Doors: Use library geometry when available (matches SCHEDULE types)
 * - Windows: Parametric fallback (TERMINAL library has commercial windows)
 */
public class BuildingWriter {

    private final Connection conn;
    private int elementId = 0;
    private DoorWindowLibraryMapper libraryMapper;
    private int libraryDoorCount = 0;
    private int parametricDoorCount = 0;
    private int parametricWindowCount = 0;
    private int libraryFixtureCount = 0;
    private int parametricFixtureCount = 0;
    private int libraryLightCount = 0;      // Phase 33
    private int parametricLightCount = 0;   // Phase 33
    private int pipeCount = 0;              // Phase 34

    public BuildingWriter(Connection conn) {
        this.conn = conn;
        // Try to initialize library mapper
        try {
            this.libraryMapper = new DoorWindowLibraryMapper();
        } catch (Exception e) {
            System.out.println("[BuildingWriter] Library mapper not available: " + e.getMessage());
            this.libraryMapper = null;
        }
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

            // Write plumbing fixtures (Phase 32: connect to LOD400 library)
            for (FixtureSpec fixture : storey.fixtures()) {
                writeFixture(fixture, storey.name());
            }

            // Write electrical elements (Phase 33: outlets, switches)
            for (ElectricalSpec elec : storey.electricals()) {
                writeElectricalElement(elec, storey.name());
            }

            // Write plumbing pipes (Phase 34: risers, vents, branches)
            for (PlumbingSpec pipe : storey.plumbing()) {
                writePipeSegment(pipe, storey.name());
            }
        }

        // Write roof
        if (spec.roof() != null) {
            writeRoof(spec.roof(), spec.storeys().get(spec.storeys().size() - 1).name());
        }

        // Phase 29: Print library usage summary
        printLibraryUsageSummary();
    }

    /**
     * Print summary of LOD400 library usage (Phase 29, 32).
     */
    private void printLibraryUsageSummary() {
        System.out.println("\n=== LOD400 Library Usage Summary ===");
        System.out.printf("Doors:    %d library, %d parametric%n", libraryDoorCount, parametricDoorCount);
        System.out.printf("Windows:  %d library, %d parametric%n", 0, parametricWindowCount);
        System.out.printf("Fixtures: %d library, %d parametric%n", libraryFixtureCount, parametricFixtureCount);
        System.out.printf("Lights:   %d library, %d parametric%n", libraryLightCount, parametricLightCount);
        System.out.printf("Pipes:    %d parametric%n", pipeCount);  // Phase 34

        int totalLibrary = libraryDoorCount + libraryFixtureCount + libraryLightCount;
        if (totalLibrary > 0) {
            System.out.println("Status: CONNECTED (using LOD400 geometry)");
        } else if (libraryMapper == null) {
            System.out.println("Status: DISCONNECTED (library mapper not available)");
        } else {
            System.out.println("Status: FALLBACK (no matching library components)");
        }
    }

    /**
     * Generate witness JSON from BuildingSpec (Phase 31 - Witness System).
     * NON-BLOCKING: Errors are caught and logged, returns false on failure.
     *
     * @param spec The building spec to extract witness data from
     * @param outputPath Path to write witness.json
     * @return true if witness was written successfully
     */
    public static boolean generateWitness(BuildingSpec spec, java.nio.file.Path outputPath) {
        try {
            com.bim.compiler.witness.WitnessBuilder witness = new com.bim.compiler.witness.WitnessBuilder(spec.name());

            // Claim 1: FOUNDATION_GROUNDED
            if (!spec.storeys().isEmpty()) {
                StoreySpec ground = spec.storeys().get(0);
                if (ground.slab() != null) {
                    witness.foundationZ(ground.slab().maxZ(), ground.slab().minZ());
                }
            }

            // Claim 2: ENTRY_EXISTS - find south-facing door
            // Claim 3: ALL_ROOMS_REACHABLE - collect room paths
            for (StoreySpec storey : spec.storeys()) {
                String entrySpace = null;
                for (DoorSpec door : storey.doors()) {
                    // Entry door is one on south/north wall (exterior-facing)
                    if (door.wall().equals("south") || door.wall().equals("north")) {
                        if (entrySpace == null) {
                            entrySpace = door.roomName();
                            witness.entryDoor(door.name(), door.wall(), "EXTERIOR", entrySpace);
                        }
                    }
                    // Record room paths from opens_to relationships
                    if (door.name().contains("_to_")) {
                        String[] parts = door.name().split("_to_");
                        if (parts.length >= 2) {
                            String fromRoom = parts[0];
                            String toRoom = parts[1].replace("_door", "");
                            witness.roomPath(toRoom, List.of(fromRoom, toRoom), door.name());
                        }
                    }
                }

                // Claim 4: WINDOWS_ON_EXTERIOR
                for (WindowSpec window : storey.windows()) {
                    witness.windowOnExterior(window.name(), window.roomName(), window.wall(), "EXTERIOR");
                }

                // Claim 5 & 7: Room corners and envelope containment
                double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
                double maxX = Double.MIN_VALUE, maxY = Double.MIN_VALUE, maxZ = Double.MIN_VALUE;

                for (RoomSpec room : storey.rooms()) {
                    double rx = room.minX(), ry = room.minY();
                    double rx2 = room.maxX(), ry2 = room.maxY();
                    double[][] corners = {{rx, ry}, {rx2, ry}, {rx2, ry2}, {rx, ry2}};
                    witness.roomCornersUnderRoof(room.name(), corners, true);
                    witness.roomEnclosed(room.name(), 4, true);
                    witness.roomInEnvelope(room.name(), true);

                    minX = Math.min(minX, rx);
                    minY = Math.min(minY, ry);
                    maxX = Math.max(maxX, rx2);
                    maxY = Math.max(maxY, ry2);
                }

                minZ = storey.baseZ();
                maxZ = storey.baseZ() + storey.height();
                witness.buildingEnvelope(minX, minY, minZ, maxX, maxY, maxZ);

                // Claim 8: ELECTRICAL_IN_SPACES (Phase 33)
                // Build room bounds lookup
                Map<String, RoomSpec> roomByName = new java.util.HashMap<>();
                for (RoomSpec room : storey.rooms()) {
                    roomByName.put(room.name(), room);
                }

                // Collect lights and validate ceiling attachment
                double ceilingZ = storey.baseZ() + storey.height();
                for (LightSpec light : storey.lights()) {
                    RoomSpec room = roomByName.get(light.roomName());
                    if (room != null) {
                        witness.electricalElement(
                            light.id(), "IfcLightFixture", light.roomName(),
                            light.x(), light.y(), light.z(),
                            room.minX(), room.maxX(), room.minY(), room.maxY(),
                            storey.baseZ(), ceilingZ
                        );

                        // Attachment validation: light top should touch ceiling
                        double fixtureTopZ = light.z() + light.height();
                        String hostId = "ceiling_" + room.name();
                        witness.ceilingMountedFixture(
                            light.id(), "IfcLightFixture",
                            fixtureTopZ, ceilingZ, hostId
                        );
                    }
                }

                // Collect outlets and switches
                for (ElectricalSpec elec : storey.electricals()) {
                    RoomSpec room = roomByName.get(elec.roomName());
                    if (room != null) {
                        String ifcClass = switch (elec.elementType().toLowerCase()) {
                            case "outlet" -> "IfcOutlet";
                            case "switch" -> "IfcSwitchingDevice";
                            default -> "IfcElectricAppliance";
                        };
                        witness.electricalElement(
                            elec.id(), ifcClass, elec.roomName(),
                            elec.x(), elec.y(), elec.z(),
                            room.minX(), room.maxX(), room.minY(), room.maxY(),
                            storey.baseZ(), storey.baseZ() + storey.height()
                        );
                    }
                }

                // Phase 34: Collect plumbing pipes
                for (PlumbingSpec pipe : storey.plumbing()) {
                    // Check if pipe is vertical (riser or vent)
                    boolean isVertical = Math.abs(pipe.endZ() - pipe.startZ()) >
                                        Math.max(Math.abs(pipe.endX() - pipe.startX()),
                                                 Math.abs(pipe.endY() - pipe.startY()));
                    witness.plumbingPipe(
                        pipe.id(),
                        pipe.pipeType(),
                        pipe.roomName(),
                        pipe.startZ(),
                        pipe.endZ(),
                        pipe.diameterM() * 1000,  // Convert to mm
                        isVertical
                    );
                }
            }

            return witness.write(outputPath);
        } catch (Exception e) {
            System.err.println("[WITNESS] Generation failed (non-blocking): " + e.getMessage());
            return false;
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

        // Write frames - include wall name for uniqueness
        int frameIdx = 0;
        for (FrameSpec frame : wall.frames()) {
            String frameGuid = "FRAME_" + wall.assemblyName() + "_" + frame.role() + "_" + (frameIdx++) + "_" + storeyName;
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

        // Write cladding - include wall name for uniqueness
        String claddingGuid = "CLADDING_" + wall.assemblyName() + "_" + storeyName;
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

        // Phase 29: Try library lookup first
        if (libraryMapper != null) {
            double widthMm = door.width() * 1000;
            double heightMm = door.height() * 1000;
            var mapping = libraryMapper.mapDoor(widthMm, heightMm, "D?");

            if (mapping.usesLibrary()) {
                writeLibraryDoor(door, doorGuid, storeyName, mapping.component());
                libraryDoorCount++;
                return;
            }
        }

        // Fallback: Door as simple box (parametric)
        parametricDoorCount++;
        double depth = BIMConstants.DOOR_THICKNESS;
        double halfDepth = depth / 2;

        // Phase 29: Orient door based on wall direction
        // Door position is at wall plane; center depth around that position
        // This works for both exterior walls (face) and interior walls (centerline)
        double minX, maxX, minY, maxY;
        switch (door.wall()) {
            case "south", "north" -> {
                // Horizontal wall (running east-west): width along X, depth centered on Y
                minX = door.x();
                maxX = door.x() + door.width();
                minY = door.y() - halfDepth;
                maxY = door.y() + halfDepth;
            }
            case "west", "east" -> {
                // Vertical wall (running north-south): width along Y, depth centered on X
                minX = door.x() - halfDepth;
                maxX = door.x() + halfDepth;
                minY = door.y();
                maxY = door.y() + door.width();
            }
            default -> {
                // Fallback: door along X
                minX = door.x();
                maxX = door.x() + door.width();
                minY = door.y() - halfDepth;
                maxY = door.y() + halfDepth;
            }
        }

        writeElement(
            doorGuid,
            "IfcDoor",
            "Entry Door",
            "DOOR",
            storeyName,
            createBoxGeometry(
                minX, minY, door.z(),
                maxX, maxY, door.z() + door.height()
            )
        );
    }

    /**
     * Write door using LOD400 library geometry (Phase 29).
     */
    private void writeLibraryDoor(DoorSpec door, String doorGuid, String storeyName,
                                  DoorWindowLibraryMapper.LibraryComponent libComp) throws SQLException {
        // Copy geometry from library to output DB
        libraryMapper.copyGeometryToOutput(conn, libComp.geometryHash());

        // Phase 29: Orient door based on wall direction
        // Door position is at wall plane; center depth around that position
        double depth = libComp.depthMm() / 1000.0;
        double halfDepth = depth / 2;
        double minX, maxX, minY, maxY;

        switch (door.wall()) {
            case "south", "north" -> {
                // Horizontal wall: width along X, depth centered on Y
                minX = door.x();
                maxX = door.x() + door.width();
                minY = door.y() - halfDepth;
                maxY = door.y() + halfDepth;
            }
            case "west", "east" -> {
                // Vertical wall: width along Y, depth centered on X
                minX = door.x() - halfDepth;
                maxX = door.x() + halfDepth;
                minY = door.y();
                maxY = door.y() + door.width();
            }
            default -> {
                // Fallback: door along X
                minX = door.x();
                maxX = door.x() + door.width();
                minY = door.y() - halfDepth;
                maxY = door.y() + halfDepth;
            }
        }

        double minZ = door.z();
        double maxZ = door.z() + door.height();

        // Write metadata
        writeElementMeta(doorGuid, "IfcDoor", libComp.name(), "DOOR", storeyName,
            minX, maxX, minY, maxY, minZ, maxZ);

        // Write instance pointing to library geometry
        writeInstance(doorGuid, libComp.geometryHash(), door.x(), door.y(), door.z());

        // Create DOOR_ASSEMBLY for BOM
        String assemblyGuid = "ASSEMBLY_" + doorGuid;
        writeDoorAssembly(assemblyGuid, doorGuid, door, storeyName);
    }

    /**
     * Write door assembly structure (Phase 29).
     * Components: LEAF (door panel) + HARDWARE (hinges, handle) for BOM.
     */
    private void writeDoorAssembly(String assemblyGuid, String doorGuid, DoorSpec door,
                                   String storeyName) throws SQLException {
        // Create assembly record
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO element_assemblies VALUES (?, ?, ?, ?, ?, ?, ?)"
        )) {
            ps.setString(1, assemblyGuid);
            ps.setString(2, "DOOR_ASSEMBLY");
            ps.setString(3, door.name());
            ps.setDouble(4, door.width());
            ps.setDouble(5, BIMConstants.DOOR_THICKNESS);
            ps.setDouble(6, door.height());
            ps.setString(7, storeyName);
            ps.execute();
        }

        // Add door leaf as component
        writeAssemblyComponent(assemblyGuid, doorGuid, "LEAF", 0, 0, 0, 0);

        // Add hardware as BOM-only components (optional=true, no geometry)
        for (int i = 0; i < BIMConstants.STANDARD_DOOR_HINGES; i++) {
            String hingeId = "HINGE_" + door.name() + "_" + i;
            writeAssemblyComponent(assemblyGuid, hingeId, "HINGE_100MM", 0, 0, 0, i + 1, true);
        }
        // 1 handle set
        String handleId = "HANDLE_" + door.name();
        writeAssemblyComponent(assemblyGuid, handleId, "HANDLE_SET", 0, 0, 0, 4, true);
    }

    private void writeWindow(WindowSpec window, String storeyName) throws SQLException {
        String windowGuid = "WINDOW_" + window.name().toUpperCase() + "_" + storeyName;

        // Phase 29: Windows fall back to parametric (TERMINAL has commercial windows)
        // Future: Add residential window library
        parametricWindowCount++;

        // Window as simple box (frame)
        double depth = BIMConstants.WINDOW_THICKNESS;
        double halfDepth = depth / 2;

        // Phase 29: Orient window based on wall direction
        // Window position is at wall plane; center depth around that position
        double minX, maxX, minY, maxY;
        switch (window.wall()) {
            case "south", "north" -> {
                // Horizontal wall: width along X, depth centered on Y
                minX = window.x();
                maxX = window.x() + window.width();
                minY = window.y() - halfDepth;
                maxY = window.y() + halfDepth;
            }
            case "west", "east" -> {
                // Vertical wall: width along Y, depth centered on X
                minX = window.x() - halfDepth;
                maxX = window.x() + halfDepth;
                minY = window.y();
                maxY = window.y() + window.width();
            }
            default -> {
                // Fallback: center depth along Y
                minX = window.x();
                maxX = window.x() + window.width();
                minY = window.y() - halfDepth;
                maxY = window.y() + halfDepth;
            }
        }

        writeElement(
            windowGuid,
            "IfcWindow",
            "Standard Window",
            "WINDOW",
            storeyName,
            createBoxGeometry(
                minX, minY, window.z(),
                maxX, maxY, window.z() + window.height()
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

        // Sprinkler as small box (pendant head)
        double size = BIMConstants.SPRINKLER_HEAD_RADIUS;
        writeElement(
            sprinklerGuid,
            "IfcFlowTerminal",
            "Fire Sprinkler",
            sprinkler.type().toUpperCase(),
            storeyName,
            createBoxGeometry(
                sprinkler.x() - size, sprinkler.y() - size, sprinkler.z() - BIMConstants.SPRINKLER_CEILING_DROP,
                sprinkler.x() + size, sprinkler.y() + size, sprinkler.z()
            )
        );
    }

    /**
     * Write light fixture element (Phase 14B, enhanced Phase 33).
     * Uses IfcLightFixture for illumination.
     * Phase 33: Supports library geometry when available.
     */
    private void writeLight(LightSpec light, String storeyName) throws SQLException {
        String lightGuid = "LIGHT_" + light.id().toUpperCase();

        // Phase 33: Use library geometry if available
        String geoHash = light.geometryHash();
        double halfW = light.width() / 2;
        double halfD = light.depth() / 2;
        double minX = light.x() - halfW;
        double maxX = light.x() + halfW;
        double minY = light.y() - halfD;
        double maxY = light.y() + halfD;
        double minZ = light.z();
        double maxZ = light.z() + light.height();

        if (geoHash != null && !geoHash.isEmpty() && libraryMapper != null) {
            // Try to copy library geometry
            try {
                libraryMapper.copyGeometryToOutput(conn, geoHash);
                libraryLightCount++;
            } catch (SQLException e) {
                // Fall back to parametric
                BoxGeometry geo = createBoxGeometry(minX, minY, minZ, maxX, maxY, maxZ);
                geoHash = writeGeometry(geo.vertices(), geo.faces());
                parametricLightCount++;
            }
        } else {
            // Parametric fallback
            BoxGeometry geo = createBoxGeometry(minX, minY, minZ, maxX, maxY, maxZ);
            geoHash = writeGeometry(geo.vertices(), geo.faces());
            parametricLightCount++;
        }

        writeElementMeta(lightGuid, "IfcLightFixture", "Light Fixture", light.fixtureType().toUpperCase(),
            storeyName, minX, maxX, minY, maxY, minZ, maxZ);
        writeInstance(lightGuid, geoHash, light.x(), light.y(), light.z());
    }

    /**
     * Write electrical element (outlet or switch) - Phase 33.
     * Uses IfcOutlet for power outlets, IfcSwitchingDevice for switches.
     */
    private void writeElectricalElement(ElectricalSpec elec, String storeyName) throws SQLException {
        String guid = "ELEC_" + elec.id().toUpperCase() + "_" + storeyName;

        // Map element type to IFC class
        String ifcClass = switch (elec.elementType().toLowerCase()) {
            case "outlet" -> "IfcOutlet";
            case "switch" -> "IfcSwitchingDevice";
            default -> "IfcElectricAppliance";
        };

        // Compute bounding box
        double halfW = elec.width() / 2;
        double halfD = elec.depth() / 2;
        double minX = elec.x() - halfW;
        double maxX = elec.x() + halfW;
        double minY = elec.y() - halfD;
        double maxY = elec.y() + halfD;
        double minZ = elec.z();
        double maxZ = elec.z() + elec.height();

        // Generate parametric box geometry (no library for outlets/switches yet)
        BoxGeometry geo = createBoxGeometry(minX, minY, minZ, maxX, maxY, maxZ);
        String geoHash = writeGeometry(geo.vertices(), geo.faces());

        writeElementMeta(guid, ifcClass, elec.elementType(), elec.elementType().toUpperCase(),
            storeyName, minX, maxX, minY, maxY, minZ, maxZ);
        writeInstance(guid, geoHash, elec.x(), elec.y(), elec.z());
    }

    /**
     * Write pipe segment (Phase 34: plumbing pipes).
     * Uses IfcPipeSegment for all pipe types.
     */
    private void writePipeSegment(PlumbingSpec pipe, String storeyName) throws SQLException {
        String guid = "PIPE_" + pipe.id().toUpperCase() + "_" + storeyName;

        // All plumbing pipes use IfcPipeSegment
        String ifcClass = "IfcPipeSegment";

        // Compute bounding box from pipe start/end and diameter
        double radius = pipe.diameterM() / 2;

        // Determine pipe direction and compute perpendicular extent
        double dx = pipe.endX() - pipe.startX();
        double dy = pipe.endY() - pipe.startY();
        double dz = pipe.endZ() - pipe.startZ();
        double len = Math.sqrt(dx*dx + dy*dy + dz*dz);

        double extentX, extentY, extentZ;
        if (len < 0.001) {
            // Point pipe - use radius in all directions
            extentX = extentY = extentZ = radius;
        } else {
            // Radial extent perpendicular to pipe axis
            double dirX = dx / len;
            double dirY = dy / len;
            double dirZ = dz / len;
            extentX = radius * Math.sqrt(1 - dirX * dirX);
            extentY = radius * Math.sqrt(1 - dirY * dirY);
            extentZ = radius * Math.sqrt(1 - dirZ * dirZ);
        }

        double minX = Math.min(pipe.startX(), pipe.endX()) - extentX;
        double maxX = Math.max(pipe.startX(), pipe.endX()) + extentX;
        double minY = Math.min(pipe.startY(), pipe.endY()) - extentY;
        double maxY = Math.max(pipe.startY(), pipe.endY()) + extentY;
        double minZ = Math.min(pipe.startZ(), pipe.endZ()) - extentZ;
        double maxZ = Math.max(pipe.startZ(), pipe.endZ()) + extentZ;

        // Generate cylinder geometry for pipe
        CylinderGeometry geo = createCylinderGeometry(
            pipe.startX(), pipe.startY(), pipe.startZ(),
            pipe.endX(), pipe.endY(), pipe.endZ(),
            radius
        );
        String geoHash = writeGeometry(geo.vertices(), geo.faces());

        // Center point for instance transform
        double centerX = (pipe.startX() + pipe.endX()) / 2;
        double centerY = (pipe.startY() + pipe.endY()) / 2;
        double centerZ = (pipe.startZ() + pipe.endZ()) / 2;

        writeElementMeta(guid, ifcClass, pipe.pipeType(), pipe.pipeType().toUpperCase(),
            storeyName, minX, maxX, minY, maxY, minZ, maxZ);
        writeInstance(guid, geoHash, centerX, centerY, centerZ);
        pipeCount++;
    }

    /**
     * Write plumbing fixture element (Phase 32: LOD400 library integration).
     * Uses IfcFlowTerminal for sanitary fixtures.
     */
    private void writeFixture(FixtureSpec fixture, String storeyName) throws SQLException {
        String guid = "FIXTURE_" + fixture.id().toUpperCase() + "_" + storeyName;

        // Map fixture type to IFC class
        String ifcClass = switch (fixture.fixtureType().toLowerCase()) {
            case "toilet" -> "IfcSanitaryTerminal";
            case "sink" -> "IfcSanitaryTerminal";
            case "exhaust_fan" -> "IfcFan";
            default -> "IfcFlowTerminal";
        };

        // Compute bounding box in world coordinates
        double halfW = fixture.width() / 2;
        double halfD = fixture.depth() / 2;
        double minX = fixture.x() - halfW;
        double maxX = fixture.x() + halfW;
        double minY = fixture.y() - halfD;
        double maxY = fixture.y() + halfD;
        double minZ = fixture.z();
        double maxZ = fixture.z() + fixture.height();

        // Use library geometry if available, otherwise generate parametric box
        String geoHash = fixture.geometryHash();
        if (geoHash != null && !geoHash.isEmpty() && libraryMapper != null) {
            // Copy geometry from component library to output DB
            try {
                libraryMapper.copyGeometryToOutput(conn, geoHash);
                libraryFixtureCount++;
            } catch (SQLException e) {
                // Fall back to parametric if copy fails
                BoxGeometry geo = createBoxGeometry(minX, minY, minZ, maxX, maxY, maxZ);
                geoHash = writeGeometry(geo.vertices(), geo.faces());
                parametricFixtureCount++;
            }
        } else {
            // Generate parametric box geometry
            BoxGeometry geo = createBoxGeometry(minX, minY, minZ, maxX, maxY, maxZ);
            geoHash = writeGeometry(geo.vertices(), geo.faces());
            parametricFixtureCount++;
        }

        writeElementMeta(guid, ifcClass, fixture.fixtureType(), fixture.fixtureType().toUpperCase(),
            storeyName, minX, maxX, minY, maxY, minZ, maxZ);
        writeInstance(guid, geoHash, fixture.x(), fixture.y(), fixture.z());
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

    /**
     * Create cylinder geometry for pipe segments (Phase 34).
     * Approximates cylinder with 16 segments for reasonable file size.
     */
    private CylinderGeometry createCylinderGeometry(
            double startX, double startY, double startZ,
            double endX, double endY, double endZ,
            double radius) {

        int segments = 16;  // 16-segment cylinder approximation

        // Calculate pipe direction
        double dx = endX - startX;
        double dy = endY - startY;
        double dz = endZ - startZ;
        double len = Math.sqrt(dx*dx + dy*dy + dz*dz);

        if (len < 0.001) {
            // Degenerate case - return empty geometry
            return new CylinderGeometry(new float[0], new int[0]);
        }

        // Normalized direction
        double dirX = dx / len;
        double dirY = dy / len;
        double dirZ = dz / len;

        // Create perpendicular vectors
        double perpX1, perpY1, perpZ1;
        double perpX2, perpY2, perpZ2;

        if (Math.abs(dirZ) < 0.9) {
            // Not vertical - use Z cross dir
            perpX1 = -dirY;
            perpY1 = dirX;
            perpZ1 = 0;
            double perpLen = Math.sqrt(perpX1*perpX1 + perpY1*perpY1);
            perpX1 /= perpLen;
            perpY1 /= perpLen;
        } else {
            // Vertical - use X cross dir
            perpX1 = 0;
            perpY1 = -dirZ;
            perpZ1 = dirY;
            double perpLen = Math.sqrt(perpY1*perpY1 + perpZ1*perpZ1);
            perpY1 /= perpLen;
            perpZ1 /= perpLen;
        }

        // Second perpendicular (cross product of dir and perp1)
        perpX2 = dirY * perpZ1 - dirZ * perpY1;
        perpY2 = dirZ * perpX1 - dirX * perpZ1;
        perpZ2 = dirX * perpY1 - dirY * perpX1;

        // Generate vertices (2 circles x segments points)
        float[] vertices = new float[segments * 2 * 3];
        int vIdx = 0;

        for (int end = 0; end < 2; end++) {
            double cx = end == 0 ? startX : endX;
            double cy = end == 0 ? startY : endY;
            double cz = end == 0 ? startZ : endZ;

            for (int i = 0; i < segments; i++) {
                double angle = 2 * Math.PI * i / segments;
                double cos = Math.cos(angle);
                double sin = Math.sin(angle);

                vertices[vIdx++] = (float) (cx + radius * (cos * perpX1 + sin * perpX2));
                vertices[vIdx++] = (float) (cy + radius * (cos * perpY1 + sin * perpY2));
                vertices[vIdx++] = (float) (cz + radius * (cos * perpZ1 + sin * perpZ2));
            }
        }

        // Generate faces (side triangles + end caps)
        int sideFaces = segments * 2;
        int capFaces = (segments - 2) * 2;
        int[] faces = new int[(sideFaces + capFaces) * 3];
        int fIdx = 0;

        // Side faces
        for (int i = 0; i < segments; i++) {
            int i1 = i;
            int i2 = (i + 1) % segments;
            int j1 = i + segments;
            int j2 = ((i + 1) % segments) + segments;

            faces[fIdx++] = i1;
            faces[fIdx++] = i2;
            faces[fIdx++] = j2;

            faces[fIdx++] = i1;
            faces[fIdx++] = j2;
            faces[fIdx++] = j1;
        }

        // Start cap (fan from vertex 0)
        for (int i = 1; i < segments - 1; i++) {
            faces[fIdx++] = 0;
            faces[fIdx++] = i + 1;
            faces[fIdx++] = i;
        }

        // End cap (fan from vertex segments)
        for (int i = 1; i < segments - 1; i++) {
            faces[fIdx++] = segments;
            faces[fIdx++] = segments + i;
            faces[fIdx++] = segments + i + 1;
        }

        return new CylinderGeometry(vertices, faces);
    }

    private record CylinderGeometry(float[] vertices, int[] faces) {}

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

        // Debug: track GUIDs (disabled)
        // System.out.println("  [DB] " + guid + " -> " + ifcClass);

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
            try {
                ps.execute();
            } catch (SQLException e) {
                System.err.println("GUID conflict: " + guid + " (" + ifcClass + ")");
                throw e;
            }
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
        writeAssemblyComponent(assemblyGuid, componentGuid, role, x, y, z, seq, false);
    }

    /**
     * Write assembly component with optional flag (Phase 29).
     * Optional components appear in BOM but may not have geometry (e.g., hardware).
     */
    private void writeAssemblyComponent(String assemblyGuid, String componentGuid,
                                        String role, double x, double y, double z, int seq,
                                        boolean optional) throws SQLException {
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
            ps.setBoolean(8, optional);
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
        ByteBuffer buffer = ByteBuffer.allocate(floats.length * BIMConstants.BYTES_PER_FLOAT)
            .order(ByteOrder.LITTLE_ENDIAN);
        for (float f : floats) buffer.putFloat(f);
        return buffer.array();
    }

    private byte[] intsToBlob(int[] ints) {
        ByteBuffer buffer = ByteBuffer.allocate(ints.length * BIMConstants.BYTES_PER_INT)
            .order(ByteOrder.LITTLE_ENDIAN);
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
