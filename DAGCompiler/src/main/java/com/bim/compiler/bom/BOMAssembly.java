/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.bom;

import java.sql.*;
import java.util.*;

/**
 * BOM (Bill of Materials) Assembly - Phase 81 POC
 *
 * Hierarchical assembly structure like ERP systems (iDempiere):
 *
 * WALL_PANEL_ASSEMBLY
 * ├── CLADDING_EXTERIOR
 * ├── FRAME_ASSEMBLY
 * │   ├── RAIL_BOTTOM
 * │   ├── RAIL_TOP
 * │   └── STUDS...
 * └── DOOR_ASSEMBLY (nested BOM)
 *     ├── DOOR_FRAME
 *     ├── DOOR_LEAF
 *     └── HARDWARE_SET
 *         ├── HINGES
 *         ├── HANDLE
 *         └── LOCK
 *
 * Database pattern:
 * - element_assemblies: defines each assembly node
 * - assembly_components: links parent → child (can be leaf or sub-assembly)
 *
 * Outliner tree is built by recursively following assembly_components.
 */
public class BOMAssembly {

    /**
     * BOM node - can be leaf element or sub-assembly.
     */
    public record BOMNode(
        String guid,
        String name,
        String type,           // ASSEMBLY or ELEMENT
        String ifcClass,
        double localX,         // Position relative to parent
        double localY,
        double localZ,
        String role,           // FRAME, CLADDING, OPENING, HARDWARE, etc.
        List<BOMNode> children // Empty for leaf elements
    ) {
        public boolean isAssembly() {
            return !children.isEmpty();
        }

        /**
         * Print tree structure for debugging.
         */
        public void printTree(String indent) {
            System.out.println(indent + (isAssembly() ? "├─[A] " : "├─[E] ") + name + " (" + role + ")");
            for (int i = 0; i < children.size(); i++) {
                children.get(i).printTree(indent + "│   ");
            }
        }
    }

    /**
     * Build BOM tree from database starting at given assembly GUID.
     */
    public static BOMNode buildTree(Connection conn, String assemblyGuid) throws SQLException {
        // Get assembly info
        String assemblyName = null;
        String assemblyType = null;
        String ifcClass = null;

        try (PreparedStatement ps = conn.prepareStatement(
            "SELECT name, assembly_type, ifc_class FROM element_assemblies WHERE assembly_guid = ?")) {
            ps.setString(1, assemblyGuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    assemblyName = rs.getString("name");
                    assemblyType = rs.getString("assembly_type");
                    ifcClass = rs.getString("ifc_class");
                }
            }
        }

        if (assemblyName == null) {
            // Not an assembly - might be a leaf element
            return getLeafElement(conn, assemblyGuid);
        }

        // Get children
        List<BOMNode> children = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
            """
            SELECT component_guid, role, local_x, local_y, local_z, sequence
            FROM assembly_components
            WHERE assembly_guid = ?
            ORDER BY sequence
            """)) {
            ps.setString(1, assemblyGuid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String childGuid = rs.getString("component_guid");
                    String role = rs.getString("role");
                    double localX = rs.getDouble("local_x");
                    double localY = rs.getDouble("local_y");
                    double localZ = rs.getDouble("local_z");

                    // Recursive: child might be another assembly
                    BOMNode childNode = buildTree(conn, childGuid);
                    if (childNode != null) {
                        // Override position from assembly_components
                        children.add(new BOMNode(
                            childNode.guid(),
                            childNode.name(),
                            childNode.type(),
                            childNode.ifcClass(),
                            localX, localY, localZ,
                            role,
                            childNode.children()
                        ));
                    }
                }
            }
        }

        return new BOMNode(assemblyGuid, assemblyName, "ASSEMBLY", ifcClass,
                          0, 0, 0, assemblyType, children);
    }

    /**
     * Get leaf element info from elements_meta.
     */
    private static BOMNode getLeafElement(Connection conn, String guid) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
            "SELECT guid, element_name, ifc_class FROM elements_meta WHERE guid = ?")) {
            ps.setString(1, guid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new BOMNode(
                        rs.getString("guid"),
                        rs.getString("element_name"),
                        "ELEMENT",
                        rs.getString("ifc_class"),
                        0, 0, 0,
                        "COMPONENT",
                        Collections.emptyList()
                    );
                }
            }
        }
        return null;
    }

    // =========================================================================
    // BOM Definition (for creating new assemblies)
    // =========================================================================

    /**
     * Define a new BOM assembly in the database.
     */
    public static void defineAssembly(Connection conn, String assemblyGuid, String name,
                                       String type, String ifcClass, String storey,
                                       double width, double depth, double height) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
            """
            INSERT OR REPLACE INTO element_assemblies
            (assembly_guid, assembly_type, ifc_class, name, total_width, total_depth, total_height, storey)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """)) {
            ps.setString(1, assemblyGuid);
            ps.setString(2, type);
            ps.setString(3, ifcClass);
            ps.setString(4, name);
            ps.setDouble(5, width);
            ps.setDouble(6, depth);
            ps.setDouble(7, height);
            ps.setString(8, storey);
            ps.execute();
        }
    }

    /**
     * Add component to assembly (can be leaf element or sub-assembly).
     */
    public static void addComponent(Connection conn, String assemblyGuid, String componentGuid,
                                    String role, double localX, double localY, double localZ,
                                    int sequence) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
            """
            INSERT INTO assembly_components
            (assembly_guid, component_guid, role, local_x, local_y, local_z, sequence)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """)) {
            ps.setString(1, assemblyGuid);
            ps.setString(2, componentGuid);
            ps.setString(3, role);
            ps.setDouble(4, localX);
            ps.setDouble(5, localY);
            ps.setDouble(6, localZ);
            ps.setInt(7, sequence);
            ps.execute();
        }
    }

    // =========================================================================
    // POC: Wall Panel with Opening
    // =========================================================================

    /**
     * Create a wall panel assembly that includes an opening (door or window).
     * The opening is positioned relative to the wall, so rotation is inherited.
     *
     * Structure:
     * WALL_PANEL_WITH_DOOR
     * ├── CLADDING (wall face)
     * ├── FRAME (studs around opening)
     * └── DOOR_ASSEMBLY
     *     ├── DOOR_LEAF
     *     └── DOOR_FRAME
     *
     * @param conn Database connection
     * @param wallGuid Existing wall GUID
     * @param openingGuid Existing opening (door/window) GUID
     * @param openingLocalX Opening X position relative to wall start
     * @param openingLocalZ Opening Z position relative to wall bottom
     */
    public static String createWallPanelWithOpening(
            Connection conn,
            String wallGuid,
            String openingGuid,
            double openingLocalX,
            double openingLocalZ,
            String storey) throws SQLException {

        String panelGuid = "WALL_PANEL_" + wallGuid;

        // Get wall dimensions
        double wallWidth = 0, wallHeight = 0, wallDepth = 0;
        try (PreparedStatement ps = conn.prepareStatement(
            """
            SELECT (r.maxX - r.minX) as width, (r.maxY - r.minY) as depth, (r.maxZ - r.minZ) as height
            FROM elements_meta m
            JOIN elements_rtree r ON m.id = r.id
            WHERE m.guid LIKE ?
            LIMIT 1
            """)) {
            ps.setString(1, "%" + wallGuid.replace("WALL_PANEL_", "") + "%");
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    wallWidth = rs.getDouble("width");
                    wallDepth = rs.getDouble("depth");
                    wallHeight = rs.getDouble("height");
                }
            }
        }

        // Create parent assembly
        defineAssembly(conn, panelGuid, "Wall Panel with Opening",
                      "WALL_PANEL_WITH_OPENING", "IfcElementAssembly", storey,
                      wallWidth, wallDepth, wallHeight);

        // Add wall as component (at local origin)
        addComponent(conn, panelGuid, wallGuid, "WALL", 0, 0, 0, 1);

        // Add opening as component (at specified local position)
        // Opening inherits wall's world transform, only local offset applied
        addComponent(conn, panelGuid, openingGuid, "OPENING", openingLocalX, 0, openingLocalZ, 2);

        return panelGuid;
    }

    // =========================================================================
    // Demo / Test
    // =========================================================================

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: BOMAssembly <database.db> [assembly_guid]");
            System.out.println("\nExamples:");
            System.out.println("  BOMAssembly output/condo_mid.db");
            System.out.println("  BOMAssembly output/condo_mid.db ASSEMBLY_DOOR_ENTRANCE_LOBBY_DOOR_WEST_Ground");
            return;
        }

        String dbPath = args[0];
        String assemblyGuid = args.length > 1 ? args[1] : null;

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            if (assemblyGuid != null) {
                // Print specific assembly tree
                System.out.println("BOM Tree for: " + assemblyGuid);
                System.out.println("=".repeat(60));
                BOMNode tree = buildTree(conn, assemblyGuid);
                if (tree != null) {
                    tree.printTree("");
                } else {
                    System.out.println("Assembly not found: " + assemblyGuid);
                }
            } else {
                // List all assemblies
                System.out.println("All Assemblies in " + dbPath);
                System.out.println("=".repeat(60));
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(
                         "SELECT assembly_guid, assembly_type, name FROM element_assemblies LIMIT 20")) {
                    while (rs.next()) {
                        System.out.printf("%-50s %-20s %s%n",
                            rs.getString("assembly_guid"),
                            rs.getString("assembly_type"),
                            rs.getString("name"));
                    }
                }

                // Show a sample tree
                System.out.println("\n" + "=".repeat(60));
                System.out.println("Sample BOM Tree (first door assembly):");
                System.out.println("=".repeat(60));
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(
                         "SELECT assembly_guid FROM element_assemblies WHERE assembly_type = 'DOOR' LIMIT 1")) {
                    if (rs.next()) {
                        BOMNode tree = buildTree(conn, rs.getString("assembly_guid"));
                        if (tree != null) tree.printTree("");
                    }
                }
            }
        }
    }
}
