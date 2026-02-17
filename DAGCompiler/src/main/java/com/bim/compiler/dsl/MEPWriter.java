package com.bim.compiler.dsl;

import com.bim.compiler.BIMConstants;
import com.bim.compiler.dsl.BuildingSpecs.*;
import com.bim.compiler.dsl.ElementPersistence.*;
import com.bim.compiler.library.FireSuppressionPlacer;
import com.bim.compiler.library.FireSuppressionPlacer.FPPipeSpec;
import com.bim.compiler.geometry.Point3D;
import com.bim.compiler.system.*;

import java.sql.*;
import java.util.*;

/**
 * Phase 103: MEP element persistence — sprinklers, lights, diffusers, pipes, fixtures, alarms, MEP systems.
 * Extracted from BuildingWriter for bus-factor reduction. Zero behavioral change.
 */
class MEPWriter {

    private final ElementPersistence ep;
    private final Connection conn;
    private final DoorWindowLibraryMapper libraryMapper;

    // Counters (package-private for BuildingWriter access)
    int libraryFixtureCount = 0;
    int parametricFixtureCount = 0;
    int libraryLightCount = 0;
    int parametricLightCount = 0;
    int librarySprinklerCount = 0;
    int parametricSprinklerCount = 0;
    int pipeCount = 0;
    int fpPipeCount = 0;
    int diffuserCount = 0;

    // Phase 92C: Cached geometry hashes for T-assembly parts (loaded on first use)
    private String fpTeeHash, fpTransitionHash, fpDropHash;
    private boolean fpFittingHashesLoaded = false;

    MEPWriter(ElementPersistence ep, Connection conn, DoorWindowLibraryMapper libraryMapper) {
        this.ep = ep;
        this.conn = conn;
        this.libraryMapper = libraryMapper;
    }

    /**
     * Write sprinkler element (Phase 14B, Phase 64, Phase 79).
     * Uses IfcFireSuppressionTerminal for fire suppression (NFPA 13 compliant).
     * Phase 79: Uses LOD400 library geometry when available.
     */
    void writeSprinkler(SprinklerSpec sprinkler, String storeyName) throws SQLException {
        String sprinklerGuid = "SPRINKLER_" + storeyName + "_" + sprinkler.id().toUpperCase();

        double size = BIMConstants.SPRINKLER_HEAD_RADIUS;
        double minX = sprinkler.x() - size;
        double maxX = sprinkler.x() + size;
        double minY = sprinkler.y() - size;
        double maxY = sprinkler.y() + size;
        double minZ = sprinkler.z() - BIMConstants.SPRINKLER_CEILING_DROP;
        double maxZ = sprinkler.z();

        String geoHash = null;

        // Phase 79: Try to use LOD400 library sprinkler geometry
        if (libraryMapper != null && libraryMapper.hasSprinklerComponents()) {
            var libComp = libraryMapper.getSprinklerComponent(sprinkler.type());
            if (libComp != null) {
                try {
                    // Phase 79: Sprinklers are ceiling-mounted (TOP attachment)
                    // Maths: localMaxZ = localMinZ + height; translateZ = attachZ - localMaxZ
                    double heightM = libComp.heightMm() / 1000.0;
                    double localMaxZ = libComp.localMinZ() + heightM;
                    double translateZ = sprinkler.z() - localMaxZ;

                    geoHash = libraryMapper.transformAndWriteGeometry(
                        conn, libComp.geometryHash(),
                        sprinkler.x(), sprinkler.y(), translateZ,
                        0  // No rotation for ceiling-mounted sprinklers
                    );
                    if (geoHash != null) {
                        librarySprinklerCount++;
                        // Phase 92C: Bbox must match LOD400 mesh, not parametric constants
                        // head maxZ = sprinkler.z() (attachment), minZ = maxZ - height
                        double halfW = libComp.widthMm() / 2000.0;
                        double halfD = libComp.depthMm() / 2000.0;
                        minX = sprinkler.x() - halfW;
                        maxX = sprinkler.x() + halfW;
                        minY = sprinkler.y() - halfD;
                        maxY = sprinkler.y() + halfD;
                        maxZ = sprinkler.z();
                        minZ = sprinkler.z() - heightM;
                    }
                } catch (SQLException e) {
                    // Fall through to parametric
                }
            }
        }

        // Fallback to parametric box geometry
        if (geoHash == null) {
            BoxGeometry geo = ep.createBoxGeometry(minX, minY, minZ, maxX, maxY, maxZ);
            geoHash = ep.writeGeometry(geo.vertices(), geo.faces());
            parametricSprinklerCount++;
        }

        ep.writeElementMeta(sprinklerGuid, "IfcFireSuppressionTerminal", "Fire Sprinkler",
            sprinkler.type().toUpperCase(), storeyName, minX, maxX, minY, maxY, minZ, maxZ);
        // CONTRACT: Pattern B - geometry is world-space (transformed), zero transform
        ep.writeInstance(sprinklerGuid, geoHash);
    }

    /**
     * Write light fixture element (Phase 14B, enhanced Phase 33).
     * Uses IfcLightFixture for illumination.
     * Phase 33: Supports library geometry when available.
     */
    void writeLight(LightSpec light, String storeyName) throws SQLException {
        String lightGuid = "LIGHT_" + storeyName + "_" + light.id().toUpperCase();

        // Phase 33: Use library geometry if available
        String geoHash = light.geometryHash();
        double halfW = light.width() / 2;
        double halfD = light.depth() / 2;
        double minX = light.x() - halfW;
        double maxX = light.x() + halfW;
        double minY = light.y() - halfD;
        double maxY = light.y() + halfD;
        // Phase 89: Drop lights below slab so they're visible
        double ceilingZ = light.z() - BIMConstants.STANDARD_SLAB_THICKNESS;
        double minZ = ceilingZ;
        double maxZ = ceilingZ + light.height();

        if (geoHash != null && !geoHash.isEmpty() && libraryMapper != null) {
            // Phase 79: Transform library geometry to world position with attachment offset
            try {
                // Lights are ceiling-mounted (TOP attachment)
                // Maths: For TOP attachment, translateZ = ceilingZ - localMaxZ
                double translateZ = ceilingZ;
                double[] zBounds = libraryMapper.getLocalZBounds(geoHash);
                if (zBounds != null) {
                    double localMaxZ = zBounds[1];
                    translateZ = ceilingZ - localMaxZ;
                }

                String transformedHash = libraryMapper.transformAndWriteGeometry(
                    conn, geoHash,
                    light.x(), light.y(), translateZ,
                    0  // No rotation for ceiling-mounted lights
                );
                if (transformedHash != null) {
                    geoHash = transformedHash;
                    libraryLightCount++;
                } else {
                    // Fall back to parametric
                    BoxGeometry geo = ep.createBoxGeometry(minX, minY, minZ, maxX, maxY, maxZ);
                    geoHash = ep.writeGeometry(geo.vertices(), geo.faces());
                    parametricLightCount++;
                }
            } catch (SQLException e) {
                // Fall back to parametric
                BoxGeometry geo = ep.createBoxGeometry(minX, minY, minZ, maxX, maxY, maxZ);
                geoHash = ep.writeGeometry(geo.vertices(), geo.faces());
                parametricLightCount++;
            }
        } else {
            // Parametric fallback
            BoxGeometry geo = ep.createBoxGeometry(minX, minY, minZ, maxX, maxY, maxZ);
            geoHash = ep.writeGeometry(geo.vertices(), geo.faces());
            parametricLightCount++;
        }

        ep.writeElementMeta(lightGuid, "IfcLightFixture", "Light Fixture", light.fixtureType().toUpperCase(),
            storeyName, minX, maxX, minY, maxY, minZ, maxZ);
        // CONTRACT: Pattern B - geometry is world-space (transformed), zero transform
        ep.writeInstance(lightGuid, geoHash);
    }

    /**
     * Write HVAC diffuser element (Phase 89).
     * Ceiling-mounted: exhaust -> IfcFan, supply/return -> IfcAirTerminal.
     */
    void writeDiffuser(DiffuserSpec diffuser, String storeyName) throws SQLException {
        String guid = "DIFFUSER_" + storeyName + "_" + diffuser.id().toUpperCase();

        // Map type to IFC class
        String ifcClass = diffuser.diffuserType().equals("exhaust") ? "IfcFan" : "IfcAirTerminal";

        // Phase 89: Drop below slab (same as lights)
        double ceilingZ = diffuser.z() - BIMConstants.STANDARD_SLAB_THICKNESS;

        // Parametric bounds (ceiling-mounted square)
        double size = 0.3;  // 300mm default
        double depth = 0.1; // 100mm default
        // Phase 92B: IfcFan with library mesh uses 1500mm bounds
        if (ifcClass.equals("IfcFan") && diffuser.geometryHash() != null && !diffuser.geometryHash().isEmpty()) {
            size = 0.75;  // 750mm half-width = 1500mm fan diameter
            depth = 0.35; // 350mm height
        }
        double minX = diffuser.x() - size / 2, maxX = diffuser.x() + size / 2;
        double minY = diffuser.y() - size / 2, maxY = diffuser.y() + size / 2;
        double minZ = ceilingZ - depth,         maxZ = ceilingZ;

        String geoHash = null;

        // Try library geometry (TOP attachment like lights)
        if (diffuser.geometryHash() != null && !diffuser.geometryHash().isEmpty() && libraryMapper != null) {
            try {
                double translateZ = ceilingZ;
                double[] zBounds = libraryMapper.getLocalZBounds(diffuser.geometryHash());
                if (zBounds != null) {
                    translateZ = ceilingZ - zBounds[1]; // TOP: align top of mesh to ceiling
                }
                geoHash = libraryMapper.transformAndWriteGeometry(
                    conn, diffuser.geometryHash(),
                    diffuser.x(), diffuser.y(), translateZ, 0);
            } catch (SQLException ignored) {}
        }

        if (geoHash == null) {
            BoxGeometry geo = ep.createBoxGeometry(minX, minY, minZ, maxX, maxY, maxZ);
            geoHash = ep.writeGeometry(geo.vertices(), geo.faces());
        }

        ep.writeElementMeta(guid, ifcClass, diffuser.diffuserType() + " diffuser",
            "DIFFUSER", storeyName, minX, maxX, minY, maxY, minZ, maxZ);
        ep.writeInstance(guid, geoHash);
        diffuserCount++;
    }

    /**
     * Write electrical element (outlet or switch) - Phase 33.
     * Uses IfcOutlet for power outlets, IfcSwitchingDevice for switches.
     */
    void writeElectricalElement(ElectricalSpec elec, String storeyName) throws SQLException {
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
        BoxGeometry geo = ep.createBoxGeometry(minX, minY, minZ, maxX, maxY, maxZ);
        String geoHash = ep.writeGeometry(geo.vertices(), geo.faces());

        ep.writeElementMeta(guid, ifcClass, elec.elementType(), elec.elementType().toUpperCase(),
            storeyName, minX, maxX, minY, maxY, minZ, maxZ);
        // CONTRACT: Pattern B - geometry is already world-space, zero transform
        ep.writeInstance(guid, geoHash);
    }

    /**
     * Phase 100: Write fire detection alarm element.
     * Uses IfcAlarm with LOD400 geometry from library, parametric box fallback.
     */
    void writeAlarm(AlarmSpec alarm, String storeyName) throws SQLException {
        String guid = "ALARM_" + alarm.id().toUpperCase() + "_" + storeyName;

        // Compute bounding box
        double halfW = alarm.width() / 2;
        double halfD = alarm.depth() / 2;
        double halfH = alarm.height() / 2;
        double minX = alarm.x() - halfW;
        double maxX = alarm.x() + halfW;
        double minY = alarm.y() - halfD;
        double maxY = alarm.y() + halfD;
        double minZ, maxZ;

        // Smoke/heat detectors mount on ceiling (z is ceiling, element hangs below)
        // Alarm bells/break glass mount on wall (z is mounting center)
        if ("smoke_detector".equals(alarm.alarmType()) || "heat_detector".equals(alarm.alarmType())) {
            maxZ = alarm.z();
            minZ = alarm.z() - alarm.height();
        } else {
            minZ = alarm.z() - halfH;
            maxZ = alarm.z() + halfH;
        }

        String geoHash = null;

        // Try LOD400 library geometry
        if (alarm.geometryHash() != null && libraryMapper != null) {
            try {
                geoHash = libraryMapper.transformAndWriteGeometry(
                    conn, alarm.geometryHash(), alarm.x(), alarm.y(), alarm.z(), 0.0);
                // Update bounds from library if available
                double[] bounds = libraryMapper.getLocalBounds(alarm.geometryHash());
                if (bounds != null) {
                    minX = alarm.x() + bounds[0]; maxX = alarm.x() + bounds[1];
                    minY = alarm.y() + bounds[2]; maxY = alarm.y() + bounds[3];
                    minZ = alarm.z() + bounds[4]; maxZ = alarm.z() + bounds[5];
                }
            } catch (SQLException ignored) {}
        }

        // Fallback to parametric box
        if (geoHash == null) {
            BoxGeometry geo = ep.createBoxGeometry(minX, minY, minZ, maxX, maxY, maxZ);
            geoHash = ep.writeGeometry(geo.vertices(), geo.faces());
        }

        ep.writeElementMeta(guid, "IfcAlarm", alarm.alarmType(), alarm.alarmType().toUpperCase(),
            storeyName, minX, maxX, minY, maxY, minZ, maxZ);
        ep.writeInstance(guid, geoHash);

        // Write code reference to element_properties for standards traceability
        if (alarm.codeRef() != null && !alarm.codeRef().isEmpty()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO element_properties (guid, pset_name, property_name, property_value) " +
                    "VALUES (?, 'Pset_IfcAlarmCommon', 'CodeReference', ?)")) {
                ps.setString(1, guid);
                ps.setString(2, alarm.codeRef());
                ps.execute();
            }
        }
    }

    /**
     * Write pipe segment (Phase 34: plumbing pipes).
     * Uses IfcPipeSegment for all pipe types.
     */
    void writePipeSegment(PlumbingSpec pipe, String storeyName) throws SQLException {
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

        // Generate cylinder geometry for pipe (world-space coordinates)
        CylinderGeometry geo = ep.createCylinderGeometry(
            pipe.startX(), pipe.startY(), pipe.startZ(),
            pipe.endX(), pipe.endY(), pipe.endZ(),
            radius
        );
        String geoHash = ep.writeGeometry(geo.vertices(), geo.faces());

        ep.writeElementMeta(guid, ifcClass, pipe.pipeType(), pipe.pipeType().toUpperCase(),
            storeyName, minX, maxX, minY, maxY, minZ, maxZ);
        // CONTRACT: Pattern B - geometry is already world-space, zero transform
        ep.writeInstance(guid, geoHash);
        pipeCount++;
    }

    /**
     * Phase 80: Write fire protection pipe segment.
     * Uses IfcPipeSegment with FP discipline for fire suppression piping.
     */
    void writeFPPipeSegment(FPPipeSpec pipe, String storeyName) throws SQLException {
        String guid = "FP_" + pipe.id().toUpperCase() + "_" + storeyName;

        String ifcClass = "IfcPipeSegment";
        String discipline = "FP";
        String elementType = pipe.type().name();

        // Compute bounding box from pipe start/end and diameter
        double radius = pipe.diameterM() / 2;
        Point3D start = pipe.start();
        Point3D end = pipe.end();

        // Determine pipe direction and compute perpendicular extent
        double dx = end.x() - start.x();
        double dy = end.y() - start.y();
        double dz = end.z() - start.z();
        double len = Math.sqrt(dx*dx + dy*dy + dz*dz);

        double extentX, extentY, extentZ;
        if (len < 0.001) {
            extentX = extentY = extentZ = radius;
        } else {
            double dirX = dx / len;
            double dirY = dy / len;
            double dirZ = dz / len;
            extentX = radius * Math.sqrt(1 - dirX * dirX);
            extentY = radius * Math.sqrt(1 - dirY * dirY);
            extentZ = radius * Math.sqrt(1 - dirZ * dirZ);
        }

        double minX = Math.min(start.x(), end.x()) - extentX;
        double maxX = Math.max(start.x(), end.x()) + extentX;
        double minY = Math.min(start.y(), end.y()) - extentY;
        double maxY = Math.max(start.y(), end.y()) + extentY;
        double minZ = Math.min(start.z(), end.z()) - extentZ;
        double maxZ = Math.max(start.z(), end.z()) + extentZ;

        // Generate cylinder geometry (world-space coordinates)
        CylinderGeometry geo = ep.createCylinderGeometry(
            start.x(), start.y(), start.z(),
            end.x(), end.y(), end.z(),
            radius
        );
        String geoHash = ep.writeGeometry(geo.vertices(), geo.faces());

        ep.writeElementMeta(guid, ifcClass, discipline, elementType,
            storeyName, minX, maxX, minY, maxY, minZ, maxZ);
        ep.writeInstance(guid, geoHash);
        fpPipeCount++;
    }

    private void loadFPFittingHashes() {
        if (fpFittingHashesLoaded) return;
        fpFittingHashesLoaded = true;
        try (var libConn = java.sql.DriverManager.getConnection("jdbc:sqlite:library/component_library.db")) {
            var rs = libConn.createStatement().executeQuery(
                "SELECT name, geometry_hash FROM component_definitions WHERE name IN ('FP_Tee_Threaded','FP_Transition_Fitting','FP_Drop_Pipe')");
            while (rs.next()) {
                switch (rs.getString("name")) {
                    case "FP_Tee_Threaded" -> fpTeeHash = rs.getString("geometry_hash");
                    case "FP_Transition_Fitting" -> fpTransitionHash = rs.getString("geometry_hash");
                    case "FP_Drop_Pipe" -> fpDropHash = rs.getString("geometry_hash");
                }
            }
        } catch (SQLException e) {
            System.out.println("[FP] Could not load T-assembly hashes: " + e.getMessage());
        }
    }

    /**
     * Phase 92C: Write T-assembly fitting (TEE, TRANSITION, DROP) with LOD400 geometry.
     * These are point elements placed at a center position with library mesh.
     */
    void writeFPFitting(FPPipeSpec pipe, String storeyName) throws SQLException {
        loadFPFittingHashes();

        String guid = "FP_" + pipe.id().toUpperCase() + "_" + storeyName;
        String discipline = "FP";

        // Determine IFC class and library geometry hash
        String ifcClass;
        String libGeoHash;
        switch (pipe.type()) {
            case TEE -> { ifcClass = "IfcPipeFitting"; libGeoHash = fpTeeHash; }
            case TRANSITION -> { ifcClass = "IfcPipeFitting"; libGeoHash = fpTransitionHash; }
            case DROP -> { ifcClass = "IfcPipeSegment"; libGeoHash = fpDropHash; }
            default -> { ifcClass = "IfcPipeSegment"; libGeoHash = null; }
        }

        double cx = pipe.start().x();
        double cy = pipe.start().y();
        double cz = pipe.start().z();  // center Z of the fitting

        String geoHash = null;
        double minX = cx - 0.04, maxX = cx + 0.04;
        double minY = cy - 0.02, maxY = cy + 0.02;
        double minZ = cz - 0.03, maxZ = cz + 0.03;

        // Try LOD400 library geometry
        if (libGeoHash != null && libraryMapper != null) {
            try {
                // Phase 92C: TEE rotated pi/2 so long arm (local X) aligns with MAIN pipe (world Y).
                // Transition and drop are nearly symmetric -- no rotation needed.
                double rotation = (pipe.type() == FireSuppressionPlacer.FPPipeType.TEE)
                    ? Math.PI / 2.0 : 0.0;

                double[] bounds = libraryMapper.getLocalBounds(libGeoHash);
                double translateZ = cz;
                if (bounds != null) {
                    // bounds = [localMinX, localMaxX, localMinY, localMaxY, localMinZ, localMaxZ]
                    if (rotation != 0.0) {
                        // 90 degree rotation swaps X<->Y extents
                        double halfX = Math.max(Math.abs(bounds[2]), Math.abs(bounds[3]));
                        double halfY = Math.max(Math.abs(bounds[0]), Math.abs(bounds[1]));
                        minX = cx - halfX; maxX = cx + halfX;
                        minY = cy - halfY; maxY = cy + halfY;
                    } else {
                        minX = cx + bounds[0]; maxX = cx + bounds[1];
                        minY = cy + bounds[2]; maxY = cy + bounds[3];
                    }
                    minZ = cz + bounds[4]; maxZ = cz + bounds[5];
                }
                geoHash = libraryMapper.transformAndWriteGeometry(
                    conn, libGeoHash, cx, cy, translateZ, rotation);
                if (geoHash != null) fpPipeCount++;
            } catch (SQLException ignored) {}
        }

        // Fallback to small box
        if (geoHash == null) {
            BoxGeometry geo = ep.createBoxGeometry(minX, minY, minZ, maxX, maxY, maxZ);
            geoHash = ep.writeGeometry(geo.vertices(), geo.faces());
            fpPipeCount++;
        }

        ep.writeElementMeta(guid, ifcClass, discipline, pipe.type().name(),
            storeyName, minX, maxX, minY, maxY, minZ, maxZ);
        ep.writeInstance(guid, geoHash);
    }

    /**
     * Write plumbing fixture element (Phase 32, 59: LOD400 library integration).
     * Uses IfcFlowTerminal for sanitary fixtures, IfcFurniture for furniture.
     *
     * Phase 59: Now uses LOD400 library geometry when available.
     */
    void writeFixture(FixtureSpec fixture, String storeyName) throws SQLException {
        String guid = "FIXTURE_" + fixture.id().toUpperCase() + "_" + storeyName;
        String fixtureType = fixture.fixtureType().toLowerCase();

        // Map fixture type to IFC class (Phase 59: added furniture types)
        String ifcClass = switch (fixtureType) {
            case "toilet", "urinal" -> "IfcSanitaryTerminal";
            case "sink" -> "IfcSanitaryTerminal";
            case "hand_bidet" -> "IfcSanitaryTerminal";
            case "floor_trap" -> "IfcSanitaryTerminal";
            case "exhaust_fan" -> "IfcFan";
            case "lobby_seating", "canteen_table", "workstation_desk", "workstation_chair",
                 "workstation_monitor", "corridor_bench", "generic_seating",
                 "visitor_chair", "visitor_table", "guest_seat",
                 "bed", "side_table", "wardrobe", "sofa", "coffee_table", "tv",
                 "lounge_chair", "dining_table", "dining_chair", "piano",
                 "cabinet", "counter_top", "ottoman" -> "IfcFurniture";
            default -> "IfcFlowTerminal";
        };

        // Compute bounding box in world coordinates (rotation-aware)
        double halfW = fixture.width() / 2;
        double halfD = fixture.depth() / 2;
        // Phase 91: Rotate bbox corners to get actual world extents
        double cos = Math.abs(Math.cos(fixture.rotation()));
        double sin = Math.abs(Math.sin(fixture.rotation()));
        double rotHalfW = halfW * cos + halfD * sin;
        double rotHalfD = halfW * sin + halfD * cos;
        double minX = fixture.x() - rotHalfW;
        double maxX = fixture.x() + rotHalfW;
        double minY = fixture.y() - rotHalfD;
        double maxY = fixture.y() + rotHalfD;
        double minZ = fixture.z();
        double maxZ = fixture.z() + fixture.height();

        String geoHash;

        // Phase 59: Use LOD400 library geometry if available
        if (fixture.geometryHash() != null && !fixture.geometryHash().isEmpty() && libraryMapper != null) {
            // Phase 89: ON_FLOOR attachment -- bottom of mesh aligns to floor level
            double translateZ = fixture.z();
            try {
                double[] zBounds = libraryMapper.getLocalZBounds(fixture.geometryHash());
                if (zBounds != null) {
                    translateZ = fixture.z() - zBounds[0]; // zBounds[0] = localMinZ
                    // Only override bbox height for non-furniture (MEP fixtures where mesh IS the definition).
                    // Furniture bbox must match placement metadata for positional fidelity.
                    if (!"IfcFurniture".equals(ifcClass)) {
                        double meshHeight = zBounds[1] - zBounds[0];
                        maxZ = fixture.z() + meshHeight;
                    }
                }
            } catch (SQLException ignored) {}

            // Transform library geometry to world position
            geoHash = libraryMapper.transformAndWriteGeometry(
                conn,
                fixture.geometryHash(),
                fixture.x(), fixture.y(), translateZ,
                fixture.rotation()
            );

            if (geoHash != null) {
                libraryFixtureCount++;
            } else {
                // Fallback to box geometry
                parametricFixtureCount++;
                BoxGeometry geo = ep.createBoxGeometry(minX, minY, minZ, maxX, maxY, maxZ);
                geoHash = ep.writeGeometry(geo.vertices(), geo.faces());
            }
        } else {
            // Parametric box fallback
            parametricFixtureCount++;
            BoxGeometry geo = ep.createBoxGeometry(minX, minY, minZ, maxX, maxY, maxZ);
            geoHash = ep.writeGeometry(geo.vertices(), geo.faces());
        }

        // Phase 119B: IFC-style name with type and dimensions
        String fixtureName = String.format("%s:%s_%dx%dx%dmm",
            ifcClass.replace("Ifc", ""), fixture.fixtureType(),
            (int)(fixture.width() * 1000), (int)(fixture.depth() * 1000), (int)(fixture.height() * 1000));
        ep.writeElementMeta(guid, ifcClass, fixtureName, fixture.fixtureType().toUpperCase(),
            storeyName, minX, maxX, minY, maxY, minZ, maxZ,
            null, fixture.materialName(), fixture.materialRgba());
        ep.writeInstance(guid, geoHash);
    }

    // =========================================================================
    // Phase 35: MEP System Graph Writing
    // =========================================================================

    /**
     * Write an MEP system graph to the database.
     */
    void writeMEPSystem(MEPSystem system, String buildingGuid) throws SQLException {
        // Write system metadata
        String sql = """
            INSERT INTO mep_systems (system_id, system_type, building_guid,
                                      is_connected, is_complete, node_count, edge_count)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, system.getSystemId());
            stmt.setString(2, system.getType().name());
            stmt.setString(3, buildingGuid);
            stmt.setInt(4, system.isConnected() ? 1 : 0);
            stmt.setInt(5, system.isComplete() ? 1 : 0);
            stmt.setInt(6, system.getNodes().size());
            stmt.setInt(7, system.getEdges().size());
            stmt.executeUpdate();
        }

        // Write nodes
        for (SystemNode node : system.getNodes()) {
            writeSystemNode(system.getSystemId(), node);
        }

        // Write edges
        for (SystemEdge edge : system.getEdges()) {
            writeSystemEdge(system.getSystemId(), edge);
        }
    }

    /**
     * Write a system node to the database.
     */
    void writeSystemNode(String systemId, SystemNode node) throws SQLException {
        // Phase 94A: OR IGNORE -- repeated floors create identical riser nodes (same logical pipe)
        String sql = """
            INSERT OR IGNORE INTO system_nodes (node_id, system_id, element_guid, role, name, properties_json)
            VALUES (?, ?, ?, ?, ?, ?)
        """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, node.nodeId());
            stmt.setString(2, systemId);
            stmt.setString(3, node.elementGuid());  // May be null for external nodes
            stmt.setString(4, node.role().name());
            stmt.setString(5, node.name());
            stmt.setString(6, propertiesToJson(node.properties()));
            stmt.executeUpdate();
        }
    }

    /**
     * Write a system edge to the database.
     */
    void writeSystemEdge(String systemId, SystemEdge edge) throws SQLException {
        // Phase 94A: OR IGNORE -- repeated floors create identical riser edges
        String sql = """
            INSERT OR IGNORE INTO system_edges (edge_id, system_id, from_node_id, to_node_id, edge_type, properties_json)
            VALUES (?, ?, ?, ?, ?, ?)
        """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, edge.edgeId());
            stmt.setString(2, systemId);
            stmt.setString(3, edge.fromNodeId());
            stmt.setString(4, edge.toNodeId());
            stmt.setString(5, edge.type().name());
            stmt.setString(6, propertiesToJson(edge.properties()));
            stmt.executeUpdate();
        }
    }

    /**
     * Convert properties map to JSON string.
     */
    private String propertiesToJson(Map<String, Object> properties) {
        if (properties == null || properties.isEmpty()) {
            return "{}";
        }
        return ep.propertiesToJson(properties);
    }
}
