/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.bom;

import java.sql.*;

/**
 * BOM Builder - Creates nested assembly structures.
 *
 * Enables:
 * 1. Outliner tree view (expandable parent → children)
 * 2. Group manipulation (drag assembly, all children move)
 * 3. En-bloc editing (change assembly variant, all components update)
 * 4. Stacked BOMs (car = body + engine, body = chassis + wheels, etc.)
 *
 * Database pattern:
 * - element_assemblies: assembly definitions with variants
 * - assembly_components: parent → child links with local offsets
 * - assembly_variants: configurable options per assembly type
 */
public class BOMBuilder {

    private final Connection conn;

    public BOMBuilder(Connection conn) {
        this.conn = conn;
    }

    /**
     * Create assembly_variants table for configurable BOMs.
     */
    public void ensureVariantsTable() throws SQLException {
        conn.createStatement().execute("""
            CREATE TABLE IF NOT EXISTS assembly_variants (
                variant_id TEXT PRIMARY KEY,
                assembly_type TEXT NOT NULL,
                variant_name TEXT NOT NULL,
                description TEXT,

                -- Configurable parameters (JSON or key-value)
                width_mm REAL,
                height_mm REAL,
                depth_mm REAL,

                -- Component selection
                door_type TEXT,          -- D1, D2, FD1, etc.
                window_type TEXT,        -- W1, W2, etc.
                cladding_material TEXT,  -- METAL_DECK, BRICK, etc.

                -- Pricing/BOM
                unit_cost REAL,
                lead_time_days INTEGER,

                is_default INTEGER DEFAULT 0
            )
            """);
    }

    /**
     * Define a standard wall panel variant with door.
     */
    public void defineWallPanelWithDoorVariant(
            String variantId,
            String variantName,
            double widthMm,
            double heightMm,
            String doorType,
            String claddingMaterial) throws SQLException {

        try (PreparedStatement ps = conn.prepareStatement("""
            INSERT OR REPLACE INTO assembly_variants
            (variant_id, assembly_type, variant_name, width_mm, height_mm, door_type, cladding_material)
            VALUES (?, 'WALL_PANEL_WITH_DOOR', ?, ?, ?, ?, ?)
            """)) {
            ps.setString(1, variantId);
            ps.setString(2, variantName);
            ps.setDouble(3, widthMm);
            ps.setDouble(4, heightMm);
            ps.setString(5, doorType);
            ps.setString(6, claddingMaterial);
            ps.execute();
        }
    }

    /**
     * Create a nested BOM: Wall Panel containing Door Assembly.
     *
     * Structure:
     * WALL_PANEL_WITH_DOOR_001
     * ├── CLADDING_001 (leaf: IfcPlate)
     * ├── FRAME_001 (sub-assembly)
     * │   ├── RAIL_BOTTOM
     * │   ├── RAIL_TOP
     * │   ├── STUD_LEFT
     * │   ├── STUD_RIGHT
     * │   └── HEADER (over door)
     * └── DOOR_ASSEMBLY_001 (sub-assembly)
     *     ├── DOOR_FRAME
     *     ├── DOOR_LEAF
     *     └── HARDWARE_SET (sub-sub-assembly)
     *         ├── HINGES
     *         ├── HANDLE
     *         └── LOCK
     */
    public String createWallPanelWithDoor(
            String panelId,
            String storey,
            double panelWidth,
            double panelHeight,
            double doorWidth,
            double doorHeight,
            double doorOffsetX,  // Door position from left edge
            String doorType) throws SQLException {

        String panelGuid = "WALL_PANEL_WITH_DOOR_" + panelId;
        String frameGuid = "FRAME_ASSEMBLY_" + panelId;
        String doorAssemblyGuid = "DOOR_ASSEMBLY_" + panelId;
        String hardwareGuid = "HARDWARE_SET_" + panelId;

        // 1. Create top-level panel assembly
        BOMAssembly.defineAssembly(conn, panelGuid,
            "Wall Panel with " + doorType + " Door",
            "WALL_PANEL_WITH_DOOR", "IfcElementAssembly", storey,
            panelWidth, 0.15, panelHeight);

        // 2. Create frame sub-assembly
        BOMAssembly.defineAssembly(conn, frameGuid,
            "Steel Frame with Opening",
            "FRAME_ASSEMBLY", "IfcElementAssembly", storey,
            panelWidth, 0.1, panelHeight);

        // Add frame components (simplified)
        addFrameComponent(frameGuid, "RAIL_BOTTOM", 0, 0, 0, 1);
        addFrameComponent(frameGuid, "RAIL_TOP", 0, 0, panelHeight - 0.1, 2);
        addFrameComponent(frameGuid, "STUD_LEFT", 0, 0, 0, 3);
        addFrameComponent(frameGuid, "STUD_RIGHT", panelWidth - 0.1, 0, 0, 4);
        addFrameComponent(frameGuid, "HEADER", doorOffsetX, 0, doorHeight, 5);

        // 3. Create door sub-assembly
        BOMAssembly.defineAssembly(conn, doorAssemblyGuid,
            doorType + " Door Assembly",
            "DOOR_ASSEMBLY", "IfcDoor", storey,
            doorWidth, 0.05, doorHeight);

        // 4. Create hardware sub-sub-assembly
        BOMAssembly.defineAssembly(conn, hardwareGuid,
            "Door Hardware Set",
            "HARDWARE_SET", "IfcDiscreteAccessory", storey,
            0.1, 0.1, 0.1);

        addHardwareComponent(hardwareGuid, "HINGES", 0, 0, 0.3, 1);
        addHardwareComponent(hardwareGuid, "HINGES", 0, 0, doorHeight - 0.3, 2);
        addHardwareComponent(hardwareGuid, "HANDLE", doorWidth - 0.1, 0, doorHeight / 2, 3);
        addHardwareComponent(hardwareGuid, "LOCK", doorWidth - 0.1, 0, doorHeight / 2 - 0.1, 4);

        // Add door components
        addDoorComponent(doorAssemblyGuid, "DOOR_FRAME", 0, 0, 0, 1);
        addDoorComponent(doorAssemblyGuid, "DOOR_LEAF", 0.05, 0, 0, 2);
        // Add hardware as nested assembly
        BOMAssembly.addComponent(conn, doorAssemblyGuid, hardwareGuid, "HARDWARE", 0, 0, 0, 3);

        // 5. Assemble the panel
        // Add frame as sub-assembly
        BOMAssembly.addComponent(conn, panelGuid, frameGuid, "FRAME", 0, 0, 0, 1);

        // Add cladding as leaf
        addCladdingComponent(panelGuid, panelWidth, panelHeight, 2);

        // Add door assembly at specified offset
        BOMAssembly.addComponent(conn, panelGuid, doorAssemblyGuid, "OPENING",
                                doorOffsetX, 0, 0, 3);

        return panelGuid;
    }

    // Helper methods for creating placeholder components
    private void addFrameComponent(String assemblyGuid, String role,
                                   double x, double y, double z, int seq) throws SQLException {
        String guid = assemblyGuid.replace("FRAME_ASSEMBLY_", "FRAME_" + role + "_");
        // Create element if not exists
        ensureElement(guid, "IfcMember", role, "FRAME");
        BOMAssembly.addComponent(conn, assemblyGuid, guid, role, x, y, z, seq);
    }

    private void addHardwareComponent(String assemblyGuid, String role,
                                      double x, double y, double z, int seq) throws SQLException {
        String guid = assemblyGuid.replace("HARDWARE_SET_", "HW_" + role + "_" + seq + "_");
        ensureElement(guid, "IfcDiscreteAccessory", role, "HARDWARE");
        BOMAssembly.addComponent(conn, assemblyGuid, guid, role, x, y, z, seq);
    }

    private void addDoorComponent(String assemblyGuid, String role,
                                  double x, double y, double z, int seq) throws SQLException {
        String guid = assemblyGuid.replace("DOOR_ASSEMBLY_", "DOOR_" + role + "_");
        String ifcClass = role.equals("DOOR_FRAME") ? "IfcMember" : "IfcDoor";
        ensureElement(guid, ifcClass, role, "DOOR");
        BOMAssembly.addComponent(conn, assemblyGuid, guid, role, x, y, z, seq);
    }

    private void addCladdingComponent(String assemblyGuid, double width, double height, int seq)
            throws SQLException {
        String guid = assemblyGuid.replace("WALL_PANEL_WITH_DOOR_", "CLADDING_");
        ensureElement(guid, "IfcPlate", "Metal Deck Cladding", "CLADDING");
        BOMAssembly.addComponent(conn, assemblyGuid, guid, "CLADDING", 0, 0, 0, seq);
    }

    private void ensureElement(String guid, String ifcClass, String name, String type)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
            INSERT OR IGNORE INTO elements_meta (guid, discipline, ifc_class, element_name, element_type)
            VALUES (?, 'ARC', ?, ?, ?)
            """)) {
            ps.setString(1, guid);
            ps.setString(2, ifcClass);
            ps.setString(3, name);
            ps.setString(4, type);
            ps.execute();
        }
    }

    // =========================================================================
    // Demo
    // =========================================================================

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: BOMBuilder <database.db>");
            return;
        }

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + args[0])) {
            conn.setAutoCommit(false);

            BOMBuilder builder = new BOMBuilder(conn);
            builder.ensureVariantsTable();

            // Define some standard variants
            builder.defineWallPanelWithDoorVariant(
                "WP_D2_3000", "3m Wall Panel with D2 Door",
                3000, 3000, "D2", "METAL_DECK");

            builder.defineWallPanelWithDoorVariant(
                "WP_FD1_3000", "3m Wall Panel with FD1 Fire Door",
                3000, 3000, "FD1", "METAL_DECK");

            // Create a sample nested BOM
            String panelGuid = builder.createWallPanelWithDoor(
                "DEMO_001", "Ground",
                3.0, 3.0,      // Panel 3m x 3m
                1.0, 2.1,      // Door 1m x 2.1m
                1.0,           // Door 1m from left edge
                "D2");

            conn.commit();

            // Show the tree
            System.out.println("\n" + "=".repeat(60));
            System.out.println("Created Nested BOM Assembly:");
            System.out.println("=".repeat(60));
            BOMAssembly.BOMNode tree = BOMAssembly.buildTree(conn, panelGuid);
            if (tree != null) {
                tree.printTree("");
            }

            System.out.println("\n" + "=".repeat(60));
            System.out.println("This structure enables:");
            System.out.println("  1. Outliner: Expand/collapse tree nodes");
            System.out.println("  2. Viewport: Drag WALL_PANEL, all children move");
            System.out.println("  3. Editor: Change door_type, DOOR_ASSEMBLY updates");
            System.out.println("  4. BOM: Roll up costs from leaf → parent");
            System.out.println("=".repeat(60));
        }
    }
}
