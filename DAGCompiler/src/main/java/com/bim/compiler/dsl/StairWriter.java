/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.dsl;

import com.bim.compiler.dsl.BuildingSpecs.*;
import com.bim.compiler.dsl.ElementPersistence.BoxGeometry;
import com.bim.compiler.geometry.Point3D;

import java.sql.*;
import java.util.*;

/**
 * Phase 103: Stair writing — extracted from BuildingWriter for bus-factor reduction.
 * Zero behavioral change. All stair/landing geometry writing lives here.
 */
class StairWriter {

    private final ElementPersistence ep;
    private final Connection conn;
    private final StairLibraryMapper stairLibraryMapper;
    private final DoorWindowLibraryMapper libraryMapper;

    int libraryStairCount = 0;
    int parametricStairCount = 0;

    StairWriter(ElementPersistence ep, Connection conn,
                StairLibraryMapper stairLibraryMapper,
                DoorWindowLibraryMapper libraryMapper) {
        this.ep = ep;
        this.conn = conn;
        this.stairLibraryMapper = stairLibraryMapper;
        this.libraryMapper = libraryMapper;
    }

    void writeStair(StairSpec stair, String storeyName) throws SQLException {
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

        String geoHash = ep.writeGeometry(vertices, faces);
        ep.writeElementMeta(stairGuid, "IfcStairFlight", "Stair Flight", "STAIR", storeyName,
            minX, maxX, minY, maxY, minZ, maxZ);
        ep.writeInstance(stairGuid, geoHash);
    }

    /**
     * Write a library-based stair (Phase 14A).
     * Uses pre-extracted LOD400 geometry from component_library.db.
     */
    void writeLibraryStair(StairSpec stair, String stairGuid, String storeyName) throws SQLException {
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
        ep.writeElementMeta(stairGuid, "IfcStairFlight", "Library Stair Flight", "STAIR", storeyName,
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

    /**
     * Phase 49: Write stair as IFC aggregate structure.
     *
     * Creates:
     *   IfcStair (aggregate parent, no geometry)
     *     +-- IfcRelAggregates
     *          +-- IfcStairFlight (actual geometry)
     *          +-- IfcSlab/LANDING (if fromStair matches)
     *
     * Only the parent IfcStair gets IfcRelContainedInSpatialStructure.
     * Children inherit spatial containment transitively through IfcRelAggregates.
     *
     * @param stair The stair spec
     * @param landings All landings in the building (to find associated ones)
     * @param containmentStorey The storey for spatial containment (lowest served)
     * @return Set of landing names that were included in this aggregate
     */
    Set<String> writeStairAssembly(StairSpec stair, List<LandingSpec> landings,
                                   String containmentStorey) throws SQLException {
        Set<String> processedLandings = new HashSet<>();

        // 1. Create IfcStair aggregate parent (no geometry)
        String stairAssemblyGuid = "STAIR_" + stair.name().toUpperCase();

        // Calculate aggregate bounds (stair + all associated landings)
        double minX = stair.x();
        double minY = stair.y();
        double minZ = stair.z();
        double maxX = stair.x() + stair.width();
        double maxY = stair.y() + stair.run();
        double maxZ = stair.z() + stair.rise();

        // Find associated landings and expand bounds
        List<LandingSpec> associatedLandings = new ArrayList<>();
        for (LandingSpec landing : landings) {
            if (stair.name().equals(landing.fromStair())) {
                associatedLandings.add(landing);
                minX = Math.min(minX, landing.minX());
                minY = Math.min(minY, landing.minY());
                minZ = Math.min(minZ, landing.minZ());
                maxX = Math.max(maxX, landing.maxX());
                maxY = Math.max(maxY, landing.maxY());
                maxZ = Math.max(maxZ, landing.maxZ());
            }
        }

        // Write IfcStair parent in elements_meta (no geometry - aggregate container)
        // predefined_type: STRAIGHT_RUN_STAIR for simple single-flight stairs
        // Note: writeElementMeta also writes to elements_rtree
        ep.writeElementMeta(stairAssemblyGuid, "IfcStair", "Stair " + stair.name(),
                        "STRAIGHT_RUN_STAIR", containmentStorey,
                        minX, maxX, minY, maxY, minZ, maxZ);

        // No element_instances entry for aggregate parent (no geometry)

        // Write assembly record with ifc_class='IfcStair' (IFC aggregate)
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT OR IGNORE INTO element_assemblies VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
        )) {
            ps.setString(1, stairAssemblyGuid);
            ps.setString(2, "STAIR_ASSEMBLY");
            ps.setString(3, "IfcStair");  // ifc_class - marks this as IFC aggregate
            ps.setString(4, stair.name());
            ps.setDouble(5, maxX - minX);  // total_width
            ps.setDouble(6, maxY - minY);  // total_depth (run)
            ps.setDouble(7, maxZ - minZ);  // total_height (rise)
            ps.setString(8, containmentStorey);
            ps.execute();
        }

        // 2. Create IfcStairFlight child (actual stair geometry)
        String flightGuid = "STAIRFLIGHT_" + stair.name().toUpperCase();
        writeStairFlightChild(stair, flightGuid, containmentStorey);

        // Link flight to assembly
        ep.writeAssemblyComponent(stairAssemblyGuid, flightGuid, "FLIGHT", 0, 0, 0, 0);

        // 3. Create IfcSlab/LANDING children
        int seq = 1;
        for (LandingSpec landing : associatedLandings) {
            String landingGuid = "LANDING_" + landing.name().toUpperCase();
            writeLandingChild(landing, landingGuid, containmentStorey);

            // Link landing to assembly
            ep.writeAssemblyComponent(stairAssemblyGuid, landingGuid, "LANDING", 0, 0, 0, seq++);
            processedLandings.add(landing.name());
        }

        System.out.printf("  [STAIR] %s: IfcStair aggregate with %d flight + %d landings (storey: %s)%n",
            stair.name(), 1, associatedLandings.size(), containmentStorey);

        return processedLandings;
    }

    /**
     * Write IfcStairFlight as child of aggregate (no separate spatial containment).
     * Phase 58: Uses LOD400 library geometry when available.
     */
    void writeStairFlightChild(StairSpec stair, String flightGuid,
                               String storeyName) throws SQLException {
        // Phase 58: Try library lookup first
        if (stairLibraryMapper != null) {
            var mapping = stairLibraryMapper.mapStair(stair.width(), stair.rise(), stair.run());

            if (mapping.usesLibrary()) {
                writeLibraryStairFlight(stair, flightGuid, storeyName, mapping);
                libraryStairCount++;
                return;
            }
        }

        // Fallback: Parametric stair geometry
        parametricStairCount++;

        // Convert geometry
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

        String geoHash = ep.writeGeometry(vertices, faces);

        // Write element meta - storey is for reference only (containment via aggregate)
        ep.writeElementMeta(flightGuid, "IfcStairFlight", "Stair Flight " + stair.name(),
                        "STRAIGHT", storeyName,
                        minX, maxX, minY, maxY, minZ, maxZ);
        ep.writeInstance(flightGuid, geoHash);
    }

    /**
     * Write library-based stair flight with LOD400 geometry.
     * Phase 58: Transforms library geometry to world position with optional scaling.
     */
    void writeLibraryStairFlight(StairSpec stair, String flightGuid, String storeyName,
                                  StairLibraryMapper.StairMappingResult mapping) throws SQLException {
        var comp = mapping.component();

        // Transform library geometry to world position
        // Stair origin is at (x, y, z) with run along +Y
        String geoHash = stairLibraryMapper.transformAndWriteGeometry(
            conn,
            comp.geometryHash(),
            stair.x(), stair.y(), stair.z(),
            0.0,  // No rotation (stair already aligned with +Y)
            mapping.scaleX(), mapping.scaleY(), mapping.scaleZ()
        );

        if (geoHash == null) {
            // Fallback to parametric if transform fails
            System.out.printf("  [STAIR] %s: Library transform failed, using parametric%n", stair.name());
            parametricStairCount++;
            libraryStairCount--;  // Undo the increment
            writeStairFlightChildParametric(stair, flightGuid, storeyName);
            return;
        }

        // Calculate scaled bounds
        double minX = stair.x();
        double maxX = stair.x() + stair.width() * mapping.scaleX();
        double minY = stair.y();
        double maxY = stair.y() + stair.run() * mapping.scaleY();
        double minZ = stair.z();
        double maxZ = stair.z() + stair.rise() * mapping.scaleZ();

        // Write element meta with library reference
        String elementName = mapping.isScaled()
            ? String.format("LOD400 Stair Flight %s (scaled %.0f%%)", stair.name(), mapping.scaleZ() * 100)
            : "LOD400 Stair Flight " + stair.name();

        ep.writeElementMeta(flightGuid, "IfcStairFlight", elementName,
                        "STRAIGHT", storeyName,
                        minX, maxX, minY, maxY, minZ, maxZ);
        ep.writeInstance(flightGuid, geoHash);
    }

    /**
     * Write parametric stair flight (fallback helper).
     */
    void writeStairFlightChildParametric(StairSpec stair, String flightGuid,
                                          String storeyName) throws SQLException {
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

        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;

        for (Point3D v : stair.vertices()) {
            minX = Math.min(minX, v.x()); maxX = Math.max(maxX, v.x());
            minY = Math.min(minY, v.y()); maxY = Math.max(maxY, v.y());
            minZ = Math.min(minZ, v.z()); maxZ = Math.max(maxZ, v.z());
        }

        String geoHash = ep.writeGeometry(vertices, faces);
        ep.writeElementMeta(flightGuid, "IfcStairFlight", "Stair Flight " + stair.name(),
                        "STRAIGHT", storeyName,
                        minX, maxX, minY, maxY, minZ, maxZ);
        ep.writeInstance(flightGuid, geoHash);
    }

    /**
     * Write IfcSlab/LANDING as child of stair aggregate (no separate spatial containment).
     */
    void writeLandingChild(LandingSpec landing, String landingGuid,
                           String storeyName) throws SQLException {
        BoxGeometry geo = ep.createBoxGeometry(
            landing.minX(), landing.minY(), landing.minZ(),
            landing.maxX(), landing.maxY(), landing.maxZ()
        );

        String geoHash = ep.writeGeometry(geo.vertices(), geo.faces());

        // Write element meta - storey is for reference only (containment via aggregate)
        ep.writeElementMeta(landingGuid, "IfcSlab", "Stair Landing " + landing.name(),
                        "LANDING", storeyName,
                        landing.minX(), landing.maxX(), landing.minY(), landing.maxY(),
                        landing.minZ(), landing.maxZ());
        ep.writeInstance(landingGuid, geoHash);
    }

    /**
     * Write standalone landing (not part of stair assembly).
     */
    void writeLanding(LandingSpec landing, String storeyName) throws SQLException {
        String landingGuid = "LANDING_" + landing.name().toUpperCase() + "_" + storeyName;

        ep.writeElement(
            landingGuid,
            "IfcSlab",
            "Stair Landing",
            "LANDING",
            storeyName,
            ep.createBoxGeometry(
                landing.minX(), landing.minY(), landing.minZ(),
                landing.maxX(), landing.maxY(), landing.maxZ()
            )
        );
    }
}
