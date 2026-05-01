/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.dsl;

import java.io.File;
import java.sql.*;

/**
 * Generates {@code library/output_template.db} — a blank output schema
 * that the copy-on-compile pipeline uses as its starting point.
 *
 * <p>The authoritative schema source is {@link BuildingWriter#initSchema()}.
 * This generator calls it, then adds a {@code _schema_guide} documentation
 * table describing the three-concern lock and every table's purpose.
 *
 * <p>Run via: {@code ./scripts/generate_output_template.sh}
 */
public class OutputTemplateGenerator {

    public static final String TEMPLATE_PATH = "library/output_template.db";

    public static void main(String[] args) throws Exception {
        String path = args.length > 0 ? args[0] : TEMPLATE_PATH;

        File f = new File(path);
        f.getParentFile().mkdirs();
        f.delete();

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + path)) {
            // 1. Authoritative schema from BuildingWriter
            BuildingWriter writer = new BuildingWriter(conn);
            writer.initSchema();

            // 2. Documentation table
            createSchemaGuide(conn);

            System.out.printf("[OUTPUT_TEMPLATE] Generated %s%n", path);

            // 3. Summary
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM sqlite_master WHERE type IN ('table','view')")) {
                rs.next();
                System.out.printf("[OUTPUT_TEMPLATE] %d tables/views created%n", rs.getInt(1));
            }
        }
    }

    private static void createSchemaGuide(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE _schema_guide (
                    table_name  TEXT NOT NULL,
                    column_name TEXT,
                    description TEXT NOT NULL
                )
            """);

            stmt.execute("""
                INSERT INTO _schema_guide (table_name, column_name, description) VALUES
                -- Three-concern lock (§11.9)
                ('_schema_guide',   NULL, 'Documentation table — describes output schema purpose and three-concern lock'),
                ('c_order',         NULL, 'WHAT: Transactional order header — one per compiled building, created from C_DocType'),
                ('c_orderline',     NULL, 'WHAT: Order lines — one per placed element, generated from BOM explosion'),
                ('W_Verb_Node',   NULL, 'HOW: Production verb invocations — one per BIM COBOL verb execution'),
                ('W_Verb_NodeProduct', NULL, 'HOW: Structured parameters for each verb invocation'),
                -- Core geometry tables
                ('elements_meta',   NULL, 'Core element catalog — guid, discipline, ifc_class, storey, material'),
                ('elements_rtree',  NULL, 'R-tree spatial index — AABB per element (minX,maxX,minY,maxY,minZ,maxZ)'),
                ('base_geometries', NULL, 'Deduplicated geometry — vertices/faces blobs keyed by hash'),
                ('element_transforms', NULL, 'Element placement — center coordinates + transform source'),
                ('element_instances', NULL, 'Element→geometry link — maps guid to geometry_hash'),

                -- Assembly structure
                ('element_assemblies',  NULL, 'Assembly headers — IfcStair, IfcCurtainWall aggregates'),
                ('assembly_components', NULL, 'Assembly children — component guid, role, local offset, sequence'),

                -- Spatial structure
                ('spatial_structure',     NULL, 'IFC spatial hierarchy — IfcProject/Site/Building/Storey/Space'),
                ('rel_contained_in_space', NULL, 'Element→space containment (IfcRelContainedInSpatialStructure)'),

                -- MEP systems
                ('mep_systems',   NULL, 'MEP system headers — sprinkler, lighting, HVAC networks'),
                ('system_nodes',  NULL, 'MEP graph nodes — equipment, terminals, junctions'),
                ('system_edges',  NULL, 'MEP graph edges — pipes, ducts, wires between nodes'),

                -- Properties + materials
                ('element_properties', NULL, 'IFC property sets — pset_name/property_name/value per element'),
                ('surface_styles',     NULL, 'Material surface rendering styles — RGB, specular, reflectance'),
                ('material_layers',    NULL, 'Material layer sets — layered wall/slab compositions'),

                -- QTO
                ('simple_qto', NULL, 'Quantity takeoff — aggregated measurements per discipline/class/storey'),

                -- Views
                ('room_areas',       NULL, 'VIEW: Room area computed from spatial_structure + rtree AABB'),
                ('area_by_storey',   NULL, 'VIEW: Aggregate area per storey'),
                ('area_by_type',     NULL, 'VIEW: Aggregate area per room type'),
                ('building_summary', NULL, 'VIEW: Storey count, room count, total buildup area'),
                ('ad_forge_fabrication', NULL, 'HOW: Forge fabrication cut list — one row per parameter per geometry record')
            """);
        }
    }
}
