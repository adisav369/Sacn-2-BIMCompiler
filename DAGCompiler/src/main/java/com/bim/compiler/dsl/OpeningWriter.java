package com.bim.compiler.dsl;

import com.bim.compiler.BIMConstants;
import com.bim.compiler.dsl.BuildingSpecs.*;
import com.bim.compiler.dsl.ElementPersistence.BoxGeometry;

import java.sql.*;
import java.util.*;

/**
 * Phase 103: Opening (door/window) persistence — extracted from BuildingWriter.
 * Zero behavioral change. Package-private.
 */
class OpeningWriter {

    private final ElementPersistence ep;
    private final Connection conn;
    private final DoorWindowLibraryMapper libraryMapper;

    int libraryDoorCount = 0;
    int parametricDoorCount = 0;
    int libraryWindowCount = 0;
    int parametricWindowCount = 0;

    OpeningWriter(ElementPersistence ep, Connection conn, DoorWindowLibraryMapper libraryMapper) {
        this.ep = ep;
        this.conn = conn;
        this.libraryMapper = libraryMapper;
    }

    /**
     * Phase 88: Find wall assembly for an opening at the given position.
     * Matches by storey + XY overlap (same logic as WallOpeningAssembler, but in-memory).
     * Returns full WallRegion so caller can compute relative offsets.
     */
    StructuralWriter.WallRegion findWallForOpening(List<StructuralWriter.WallRegion> wallAssemblyIndex,
                                                    String storey, double minX, double maxX,
                                                    double minY, double maxY) {
        StructuralWriter.WallRegion best = null;
        double bestOverlap = 0;
        double tolerance = 0.3; // 300mm match tolerance

        for (StructuralWriter.WallRegion wall : wallAssemblyIndex) {
            if (!wall.storey().equalsIgnoreCase(storey)) continue;

            double overlapX = Math.min(wall.maxX(), maxX + tolerance) - Math.max(wall.minX(), minX - tolerance);
            double overlapY = Math.min(wall.maxY(), maxY + tolerance) - Math.max(wall.minY(), minY - tolerance);
            double overlap = Math.max(0, overlapX) * Math.max(0, overlapY);

            if (overlap > bestOverlap) {
                bestOverlap = overlap;
                best = wall;
            }
        }
        return best;
    }

    void writeDoor(List<StructuralWriter.WallRegion> wallAssemblyIndex,
                   DoorSpec door, String storeyName) throws SQLException {
        String doorGuid = "DOOR_" + door.name().toUpperCase() + "_" + storeyName;

        // Phase 29: Try library lookup first
        if (libraryMapper != null) {
            double widthMm = door.width() * 1000;
            double heightMm = door.height() * 1000;
            var mapping = libraryMapper.mapDoor(widthMm, heightMm, "D?", door.wall());

            if (mapping.usesLibrary()) {
                writeLibraryDoor(wallAssemblyIndex, door, doorGuid, storeyName, mapping);
                libraryDoorCount++;
                return;
            }
        }

        // Fallback: Door as simple box (parametric)
        parametricDoorCount++;
        double depth = door.depth();
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

        // Phase 119B: IFC-style name with dimensions
        String doorName = String.format("Door:SGL_%dx%dmm",
            (int)(door.width() * 1000), (int)(door.height() * 1000));
        ep.writeElement(
            doorGuid,
            "IfcDoor",
            doorName,
            "DOOR",
            storeyName,
            ep.createBoxGeometry(
                minX, minY, door.z(),
                maxX, maxY, door.z() + door.height()
            )
        );

        // Phase 88: Direct wall->opening link for parametric doors
        StructuralWriter.WallRegion matchedWall = findWallForOpening(wallAssemblyIndex, storeyName, minX, maxX, minY, maxY);
        if (matchedWall != null) {
            ep.writeAssemblyComponent(matchedWall.assemblyGuid(), doorGuid, "OPENING",
                minX - matchedWall.minX(), minY - matchedWall.minY(),
                door.z() - matchedWall.minZ(), 100);
        }
    }

    /**
     * Write door using LOD400 library geometry (Phase 29, updated Phase 54).
     *
     * Phase 54: Now transforms library geometry to world-space using GeometryEngine,
     * preserving LOD400 detail while maintaining Pattern B (world-space + zero transform).
     */
    private void writeLibraryDoor(List<StructuralWriter.WallRegion> wallAssemblyIndex,
                                   DoorSpec door, String doorGuid, String storeyName,
                                   DoorWindowLibraryMapper.MappingResult result) throws SQLException {
        var libComp = result.component();

        // Phase 54: Calculate transformation for library geometry
        // Library convention: widthMm = Y-extent, depthMm = X-extent, heightMm = Z-extent
        // Phase 88: When orientation-matched, use actual axis extents for bounds
        boolean isNorthSouth = door.wall().equals("north") || door.wall().equals("south");
        double xExtent = libComp.depthMm() / 1000.0;   // X-axis extent
        double yExtent = libComp.widthMm() / 1000.0;    // Y-axis extent

        // For bounds: NS walls -> opening width along X, depth along Y
        //             EW walls -> opening width along Y, depth along X
        // Phase 97: When NOT orientation-matched, mesh is rotated 90 so local X<->Y swap.
        double visibleWidth, physicalDepth;
        if (result.orientationMatched()) {
            if (isNorthSouth) {
                visibleWidth = xExtent;  // X-extent is opening width on NS wall
                physicalDepth = yExtent; // Y-extent is depth
            } else {
                visibleWidth = yExtent;  // Y-extent is opening width on EW wall
                physicalDepth = xExtent; // X-extent is depth
            }
        } else {
            // Rotated: local axes swap
            if (isNorthSouth) {
                visibleWidth = yExtent;
                physicalDepth = xExtent;
            } else {
                visibleWidth = xExtent;
                physicalDepth = yExtent;
            }
        }
        // Phase 119D: Override bbox depth with family's frame depth (spatial envelope)
        // Library mesh depth ≠ spatial depth. Family depth_mm = correct frame depth.
        // LOD400 mesh geometry stays untouched; only the bbox (spatial query) changes.
        if (door.depth() > 0) {
            physicalDepth = door.depth();
        }
        double halfDepth = physicalDepth / 2;

        // Phase 88: Skip rotation when orientation-matched variant selected
        // Phase 81 fallback: Deterministic rotation from library forward_axis
        double rotateZ = result.orientationMatched() ? 0.0 : libComp.calculateRotation(door.wall());
        double centerX, centerY;
        double minX, maxX, minY, maxY;

        // Calculate center and bounds based on wall direction
        if (isNorthSouth) {
            // Door on north/south wall: width along X
            centerX = door.x() + door.width() / 2;
            centerY = door.y();
            minX = door.x();
            maxX = door.x() + door.width();
            minY = door.y() - halfDepth;
            maxY = door.y() + halfDepth;
        } else {
            // Door on east/west wall: width along Y
            centerX = door.x();
            centerY = door.y() + door.width() / 2;
            minX = door.x() - halfDepth;
            maxX = door.x() + halfDepth;
            minY = door.y();
            maxY = door.y() + door.width();
        }

        double minZ = door.z();
        double maxZ = door.z() + door.height();
        // Phase 79: Compute attachment offset - library geometry is centered,
        // we need to offset so bottom (localMinZ) aligns with target floor level
        double translateZ = door.z() - libComp.localMinZ();

        // Write metadata (bounds for spatial queries)
        // Phase 119B: IFC-style name with library component dimensions
        String libDoorName = String.format("Door:%s_%dx%dmm", libComp.name(),
            (int)libComp.widthMm(), (int)libComp.heightMm());
        ep.writeElementMeta(doorGuid, "IfcDoor", libDoorName, "DOOR", storeyName,
            minX, maxX, minY, maxY, minZ, maxZ);

        // Phase 54/79: Transform library geometry to world-space
        // CONTRACT: Pattern B - world-space geometry + zero transform
        // Maths: translateZ = targetZ - localMinZ, so localMinZ + translateZ = targetZ
        String geoHash = libraryMapper.transformAndWriteGeometry(
            conn,
            libComp.geometryHash(),
            centerX, centerY, translateZ,
            rotateZ
        );

        if (geoHash == null) {
            // Fallback to box if library geometry not available
            System.out.println("[BuildingWriter] LOD400 geometry not found, using box fallback: " + doorGuid);
            BoxGeometry worldGeo = ep.createBoxGeometry(minX, minY, minZ, maxX, maxY, maxZ);
            geoHash = ep.writeGeometry(worldGeo.vertices(), worldGeo.faces());
        }

        ep.writeInstance(doorGuid, geoHash);

        // Create DOOR_ASSEMBLY for BOM
        String doorAssemblyGuid = "ASSEMBLY_" + doorGuid;
        writeDoorAssembly(doorAssemblyGuid, doorGuid, door, storeyName);

        // Phase 88: Direct wall->opening link (replaces post-hoc spatial join)
        StructuralWriter.WallRegion matchedWall = findWallForOpening(wallAssemblyIndex, storeyName, minX, maxX, minY, maxY);
        if (matchedWall != null) {
            double localX = minX - matchedWall.minX();
            double localY = minY - matchedWall.minY();
            double localZ = minZ - matchedWall.minZ();
            ep.writeAssemblyComponent(matchedWall.assemblyGuid(), doorGuid, "OPENING",
                localX, localY, localZ, 100);
        }
    }

    /**
     * Write door assembly structure (Phase 29).
     * Components: LEAF (door panel) + HARDWARE (hinges, handle) for BOM.
     */
    void writeDoorAssembly(String assemblyGuid, String doorGuid, DoorSpec door,
                            String storeyName) throws SQLException {
        // Create assembly record (ifc_class=NULL for BOM-only door assemblies)
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT OR IGNORE INTO element_assemblies VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
        )) {
            ps.setString(1, assemblyGuid);
            ps.setString(2, "DOOR_ASSEMBLY");
            ps.setNull(3, java.sql.Types.VARCHAR);  // ifc_class (BOM-only)
            ps.setString(4, door.name());
            ps.setDouble(5, door.width());
            ps.setDouble(6, door.depth());
            ps.setDouble(7, door.height());
            ps.setString(8, storeyName);
            ps.execute();
        }

        // Add door leaf as component
        ep.writeAssemblyComponent(assemblyGuid, doorGuid, "LEAF", 0, 0, 0, 0);

        // Add hardware as BOM-only components (optional=true, no geometry)
        for (int i = 0; i < BIMConstants.STANDARD_DOOR_HINGES; i++) {
            String hingeId = "HINGE_" + door.name() + "_" + i;
            ep.writeAssemblyComponent(assemblyGuid, hingeId, "HINGE_100MM", 0, 0, 0, i + 1, true);
        }
        // 1 handle set
        String handleId = "HANDLE_" + door.name();
        ep.writeAssemblyComponent(assemblyGuid, handleId, "HANDLE_SET", 0, 0, 0, 4, true);
    }

    void writeWindow(List<StructuralWriter.WallRegion> wallAssemblyIndex,
                     WindowSpec window, String storeyName) throws SQLException {
        String windowGuid = "WINDOW_" + window.name().toUpperCase() + "_" + storeyName;

        // Phase 57B: Try library match with scaling support
        if (libraryMapper != null) {
            var result = libraryMapper.mapWindow(
                window.width() * 1000,  // Convert to mm
                window.height() * 1000,
                window.name(),
                window.wall()
            );
            if (result.usesLibrary()) {
                writeWindowFromLibrary(wallAssemblyIndex, window, windowGuid, storeyName, result);
                return;
            }
        }

        parametricWindowCount++;

        // Window as simple box (frame)
        double depth = window.depth();
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

        // Phase 119B: IFC-style name with dimensions
        String windowName = String.format("Window:Fixed_%dx%dmm",
            (int)(window.width() * 1000), (int)(window.height() * 1000));
        ep.writeElement(
            windowGuid,
            "IfcWindow",
            windowName,
            "WINDOW",
            storeyName,
            ep.createBoxGeometry(
                minX, minY, window.z(),
                maxX, maxY, window.z() + window.height()
            )
        );

        // Phase 88: Direct wall->opening link for parametric windows
        StructuralWriter.WallRegion matchedWall = findWallForOpening(wallAssemblyIndex, storeyName, minX, maxX, minY, maxY);
        if (matchedWall != null) {
            ep.writeAssemblyComponent(matchedWall.assemblyGuid(), windowGuid, "OPENING",
                minX - matchedWall.minX(), minY - matchedWall.minY(),
                window.z() - matchedWall.minZ(), 100);
        }
    }

    /**
     * Phase 57B: Write window using LOD400 library geometry with optional scaling.
     * Follows same pattern as writeLibraryDoor.
     */
    private void writeWindowFromLibrary(List<StructuralWriter.WallRegion> wallAssemblyIndex,
                                         WindowSpec window, String windowGuid, String storeyName,
                                         DoorWindowLibraryMapper.MappingResult result) throws SQLException {
        libraryWindowCount++;
        var libComp = result.component();

        // Phase 88: Use actual axis extents for bounds (same logic as writeLibraryDoor)
        boolean isNorthSouth = window.wall().equals("north") || window.wall().equals("south");
        double xExtent = libComp.depthMm() / 1000.0;   // X-axis extent (local)
        double yExtent = libComp.widthMm() / 1000.0;    // Y-axis extent (local)
        // Phase 97: When NOT orientation-matched, mesh is rotated 90 so local X<->Y swap.
        // NS wall depth (world Y) = local Y (oriented) or local X (rotated).
        double physicalDepth;
        if (result.orientationMatched()) {
            physicalDepth = isNorthSouth ? yExtent : xExtent;
        } else {
            // Rotated: local axes swap -> NS depth = xExtent, EW depth = yExtent
            physicalDepth = isNorthSouth ? xExtent : yExtent;
        }
        // Phase 119D: Override bbox depth with family's frame depth (spatial envelope)
        // Same principle as doors: library mesh depth ≠ spatial depth.
        if (window.depth() > 0) {
            physicalDepth = window.depth();
        }
        double halfDepth = physicalDepth / 2;

        // Phase 88: Skip rotation when orientation-matched variant selected
        // Phase 81 fallback: Deterministic rotation from library forward_axis
        double rotateZ = result.orientationMatched() ? 0.0 : libComp.calculateRotation(window.wall());
        double centerX, centerY;
        double minX, maxX, minY, maxY;

        // Calculate center and bounds based on wall direction
        if (isNorthSouth) {
            // Window on north/south wall: width along X
            centerX = window.x() + window.width() / 2;
            centerY = window.y();
            minX = window.x();
            maxX = window.x() + window.width();
            minY = window.y() - halfDepth;
            maxY = window.y() + halfDepth;
        } else {
            // Window on east/west wall: width along Y
            centerX = window.x();
            centerY = window.y() + window.width() / 2;
            minX = window.x() - halfDepth;
            maxX = window.x() + halfDepth;
            minY = window.y();
            maxY = window.y() + window.width();
        }

        double minZ = window.z();
        double maxZ = window.z() + window.height();
        // Phase 79: Compute attachment offset - library geometry is centered,
        // we need to offset so bottom (localMinZ) aligns with target sill level
        double translateZ = window.z() - libComp.localMinZ();

        // Write metadata
        // Phase 119B: IFC-style name with library component dimensions
        String libWinName = String.format("Window:%s_%dx%dmm", libComp.name(),
            (int)libComp.widthMm(), (int)libComp.heightMm());
        ep.writeElementMeta(windowGuid, "IfcWindow", libWinName,
            "WINDOW", storeyName, minX, maxX, minY, maxY, minZ, maxZ);

        // Phase 79: Transform library geometry to world-space (with scaling if needed)
        // Maths: translateZ = targetZ - localMinZ, so localMinZ + translateZ = targetZ
        String geoHash = libraryMapper.transformAndWriteGeometryScaled(
            conn,
            libComp.geometryHash(),
            centerX, centerY, translateZ,
            rotateZ,
            result.scaleX(), result.scaleY(), result.scaleZ()
        );

        if (geoHash == null) {
            // Fallback to box
            System.out.println("[BuildingWriter] Window LOD400 not found, using box: " + windowGuid);
            BoxGeometry worldGeo = ep.createBoxGeometry(minX, minY, minZ, maxX, maxY, maxZ);
            geoHash = ep.writeGeometry(worldGeo.vertices(), worldGeo.faces());
        }

        ep.writeInstance(windowGuid, geoHash);

        // Phase 88: Direct wall->opening link for library windows
        StructuralWriter.WallRegion matchedWall = findWallForOpening(wallAssemblyIndex, storeyName, minX, maxX, minY, maxY);
        if (matchedWall != null) {
            ep.writeAssemblyComponent(matchedWall.assemblyGuid(), windowGuid, "OPENING",
                minX - matchedWall.minX(), minY - matchedWall.minY(),
                minZ - matchedWall.minZ(), 100);
        }
    }
}
