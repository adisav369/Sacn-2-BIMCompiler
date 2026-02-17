package com.bim.compiler.bom;

import java.sql.*;
import java.util.*;

/**
 * Floor Assembly Builder - Creates hierarchical floor BOMs.
 *
 * Vision: Assemble a whole floor from prefab components:
 *
 * TYPICAL_FLOOR_TEMPLATE
 * ├── SLAB_ASSEMBLY
 * ├── CORRIDOR_ASSEMBLY
 * │   ├── WALL_PANEL_PLAIN ×N
 * │   ├── CEILING_LIGHTS
 * │   └── SPRINKLERS
 * ├── UNIT_A_ASSEMBLY (apartment/room group)
 * │   ├── ROOM_LIVING_ASSEMBLY
 * │   │   ├── WALL_PANEL_WITH_WINDOW
 * │   │   ├── WALL_PANEL_WITH_DOOR
 * │   │   └── WALL_PANEL_PLAIN ×2
 * │   ├── ROOM_BEDROOM_ASSEMBLY
 * │   └── ROOM_BATHROOM_ASSEMBLY
 * └── UNIT_B_ASSEMBLY (mirror of A)
 *
 * Benefits:
 * 1. Drop a floor template → instant storey
 * 2. Change TYPICAL_FLOOR variant → all instances update
 * 3. Drag UNIT_A in outliner → reposition entire apartment
 * 4. Cost/schedule at any level (room, unit, floor, building)
 */
public class FloorAssemblyBuilder {

    private final Connection conn;

    public FloorAssemblyBuilder(Connection conn) {
        this.conn = conn;
    }

    /**
     * Create floor template table for reusable floor configurations.
     */
    public void ensureFloorTemplateTable() throws SQLException {
        conn.createStatement().execute("""
            CREATE TABLE IF NOT EXISTS floor_templates (
                template_id TEXT PRIMARY KEY,
                template_name TEXT NOT NULL,
                floor_type TEXT NOT NULL,      -- GROUND, TYPICAL, AMENITY, ROOF

                -- Dimensions
                width_m REAL,
                depth_m REAL,
                height_m REAL DEFAULT 3.0,

                -- Configuration
                num_units INTEGER DEFAULT 2,   -- Apartments per floor
                corridor_width_m REAL DEFAULT 2.0,

                -- Component counts (for BOM rollup)
                wall_panels INTEGER,
                doors INTEGER,
                windows INTEGER,
                lights INTEGER,
                sprinklers INTEGER,

                -- Costing
                cost_per_m2 REAL,

                is_active INTEGER DEFAULT 1
            )
            """);
    }

    /**
     * Create a complete floor assembly from template.
     *
     * @param floorId Unique floor identifier (e.g., "F2", "F3")
     * @param templateId Template to use (e.g., "TYPICAL_CONDO_2UNIT")
     * @param levelZ Floor level in meters
     * @return Floor assembly GUID
     */
    public String createFloorFromTemplate(
            String floorId,
            String templateId,
            double levelZ) throws SQLException {

        String floorGuid = "FLOOR_ASSEMBLY_" + floorId;

        // Get template config
        double width = 30.0, depth = 40.0, height = 3.0;
        int numUnits = 2;
        double corridorWidth = 2.0;

        try (PreparedStatement ps = conn.prepareStatement(
            "SELECT * FROM floor_templates WHERE template_id = ?")) {
            ps.setString(1, templateId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    width = rs.getDouble("width_m");
                    depth = rs.getDouble("depth_m");
                    height = rs.getDouble("height_m");
                    numUnits = rs.getInt("num_units");
                    corridorWidth = rs.getDouble("corridor_width_m");
                }
            }
        }

        // 1. Create floor assembly (top level)
        BOMAssembly.defineAssembly(conn, floorGuid,
            "Floor " + floorId + " (" + templateId + ")",
            "FLOOR_ASSEMBLY", "IfcBuildingStorey", floorId,
            width, depth, height);

        int seq = 1;

        // 2. Add slab
        String slabGuid = createSlabAssembly(floorId, width, depth, levelZ);
        BOMAssembly.addComponent(conn, floorGuid, slabGuid, "SLAB", 0, 0, 0, seq++);

        // 3. Add corridor (runs through middle)
        double corridorY = (depth - corridorWidth) / 2;
        String corridorGuid = createCorridorAssembly(floorId, width, corridorWidth, height, corridorY);
        BOMAssembly.addComponent(conn, floorGuid, corridorGuid, "CORRIDOR", 0, corridorY, 0, seq++);

        // 4. Add units on each side of corridor
        double unitDepth = (depth - corridorWidth) / 2;
        for (int i = 0; i < numUnits; i++) {
            double unitX = i * (width / numUnits);
            double unitWidth = width / numUnits;

            // North unit
            String unitNorthGuid = createUnitAssembly(
                floorId + "_UNIT_N" + (i + 1),
                unitWidth, unitDepth, height,
                "NORTH");
            BOMAssembly.addComponent(conn, floorGuid, unitNorthGuid, "UNIT",
                unitX, corridorY + corridorWidth, 0, seq++);

            // South unit
            String unitSouthGuid = createUnitAssembly(
                floorId + "_UNIT_S" + (i + 1),
                unitWidth, unitDepth, height,
                "SOUTH");
            BOMAssembly.addComponent(conn, floorGuid, unitSouthGuid, "UNIT",
                unitX, 0, 0, seq++);
        }

        return floorGuid;
    }

    /**
     * Create slab assembly.
     */
    private String createSlabAssembly(String floorId, double width, double depth, double levelZ)
            throws SQLException {
        String slabGuid = "SLAB_ASSEMBLY_" + floorId;

        BOMAssembly.defineAssembly(conn, slabGuid,
            "Floor Slab " + floorId,
            "SLAB_ASSEMBLY", "IfcSlab", floorId,
            width, depth, 0.15);

        // Slab components
        String deckGuid = "SLAB_DECK_" + floorId;
        String meshGuid = "SLAB_MESH_" + floorId;
        String concreteGuid = "SLAB_CONCRETE_" + floorId;

        ensureElement(deckGuid, "IfcPlate", "Metal Deck", "DECK");
        ensureElement(meshGuid, "IfcReinforcingMesh", "Reinforcement Mesh", "MESH");
        ensureElement(concreteGuid, "IfcSlab", "Concrete Topping", "CONCRETE");

        BOMAssembly.addComponent(conn, slabGuid, deckGuid, "DECK", 0, 0, 0, 1);
        BOMAssembly.addComponent(conn, slabGuid, meshGuid, "REINFORCEMENT", 0, 0, 0.02, 2);
        BOMAssembly.addComponent(conn, slabGuid, concreteGuid, "TOPPING", 0, 0, 0.05, 3);

        return slabGuid;
    }

    /**
     * Create corridor assembly with walls, lights, sprinklers.
     */
    private String createCorridorAssembly(
            String floorId,
            double length,
            double width,
            double height,
            double corridorY) throws SQLException {

        String corridorGuid = "CORRIDOR_ASSEMBLY_" + floorId;

        BOMAssembly.defineAssembly(conn, corridorGuid,
            "Corridor " + floorId,
            "CORRIDOR_ASSEMBLY", "IfcSpace", floorId,
            length, width, height);

        int seq = 1;

        // North wall (plain panels)
        int numPanels = (int) Math.ceil(length / 3.0);
        for (int i = 0; i < numPanels; i++) {
            String panelGuid = "WALL_PANEL_CORRIDOR_N_" + floorId + "_" + i;
            ensureElement(panelGuid, "IfcWall", "Corridor Wall Panel", "WALL");
            BOMAssembly.addComponent(conn, corridorGuid, panelGuid, "WALL_NORTH",
                i * 3.0, width, 0, seq++);
        }

        // South wall (with unit entry doors)
        for (int i = 0; i < numPanels; i++) {
            String panelGuid = "WALL_PANEL_CORRIDOR_S_" + floorId + "_" + i;
            // Every 3rd panel has a door (unit entry)
            String panelType = (i % 3 == 1) ? "WALL_WITH_DOOR" : "WALL_PLAIN";
            ensureElement(panelGuid, "IfcWall", "Corridor Wall Panel", panelType);
            BOMAssembly.addComponent(conn, corridorGuid, panelGuid, "WALL_SOUTH",
                i * 3.0, 0, 0, seq++);
        }

        // Ceiling lights (every 4m)
        int numLights = (int) Math.ceil(length / 4.0);
        for (int i = 0; i < numLights; i++) {
            String lightGuid = "LIGHT_CORRIDOR_" + floorId + "_" + i;
            ensureElement(lightGuid, "IfcLightFixture", "Corridor Light", "LIGHT");
            BOMAssembly.addComponent(conn, corridorGuid, lightGuid, "LIGHT",
                i * 4.0 + 2.0, width / 2, height - 0.1, seq++);
        }

        // Sprinklers (every 4.6m per NFPA)
        int numSprinklers = (int) Math.ceil(length / 4.6);
        for (int i = 0; i < numSprinklers; i++) {
            String sprinklerGuid = "SPRINKLER_CORRIDOR_" + floorId + "_" + i;
            ensureElement(sprinklerGuid, "IfcFireSuppressionTerminal", "Sprinkler", "SPRINKLER");
            BOMAssembly.addComponent(conn, corridorGuid, sprinklerGuid, "SPRINKLER",
                i * 4.6 + 2.3, width / 2, height - 0.1, seq++);
        }

        return corridorGuid;
    }

    /**
     * Create unit (apartment) assembly with rooms.
     */
    private String createUnitAssembly(
            String unitId,
            double width,
            double depth,
            double height,
            String orientation) throws SQLException {

        String unitGuid = "UNIT_ASSEMBLY_" + unitId;

        BOMAssembly.defineAssembly(conn, unitGuid,
            "Unit " + unitId,
            "UNIT_ASSEMBLY", "IfcSpace", unitId.split("_")[0],
            width, depth, height);

        int seq = 1;

        // Living room (40% of unit)
        String livingGuid = createRoomAssembly(unitId + "_LIVING", "LIVING",
            width * 0.4, depth, height);
        BOMAssembly.addComponent(conn, unitGuid, livingGuid, "ROOM_LIVING",
            0, 0, 0, seq++);

        // Bedroom (35% of unit)
        String bedroomGuid = createRoomAssembly(unitId + "_BEDROOM", "BEDROOM",
            width * 0.35, depth, height);
        BOMAssembly.addComponent(conn, unitGuid, bedroomGuid, "ROOM_BEDROOM",
            width * 0.4, 0, 0, seq++);

        // Bathroom (25% of unit)
        String bathroomGuid = createRoomAssembly(unitId + "_BATHROOM", "BATHROOM",
            width * 0.25, depth * 0.5, height);
        BOMAssembly.addComponent(conn, unitGuid, bathroomGuid, "ROOM_BATHROOM",
            width * 0.75, 0, 0, seq++);

        return unitGuid;
    }

    /**
     * Create room assembly with wall panels.
     */
    private String createRoomAssembly(
            String roomId,
            String roomType,
            double width,
            double depth,
            double height) throws SQLException {

        String roomGuid = "ROOM_ASSEMBLY_" + roomId;

        BOMAssembly.defineAssembly(conn, roomGuid,
            roomType + " " + roomId,
            "ROOM_ASSEMBLY", "IfcSpace", roomId.split("_")[0],
            width, depth, height);

        int seq = 1;

        // Determine wall panel types based on room type
        String northPanelType = roomType.equals("LIVING") ? "WALL_WITH_WINDOW" : "WALL_PLAIN";
        String southPanelType = "WALL_WITH_DOOR";  // Entry door
        String eastPanelType = "WALL_PLAIN";
        String westPanelType = roomType.equals("BATHROOM") ? "WALL_PLAIN" : "WALL_PLAIN";

        // North wall
        String northGuid = "WALL_PANEL_" + roomId + "_NORTH";
        ensureElement(northGuid, "IfcWall", northPanelType, "WALL");
        BOMAssembly.addComponent(conn, roomGuid, northGuid, "WALL_NORTH", 0, depth, 0, seq++);

        // South wall
        String southGuid = "WALL_PANEL_" + roomId + "_SOUTH";
        ensureElement(southGuid, "IfcWall", southPanelType, "WALL");
        BOMAssembly.addComponent(conn, roomGuid, southGuid, "WALL_SOUTH", 0, 0, 0, seq++);

        // East wall
        String eastGuid = "WALL_PANEL_" + roomId + "_EAST";
        ensureElement(eastGuid, "IfcWall", eastPanelType, "WALL");
        BOMAssembly.addComponent(conn, roomGuid, eastGuid, "WALL_EAST", width, 0, 0, seq++);

        // West wall
        String westGuid = "WALL_PANEL_" + roomId + "_WEST";
        ensureElement(westGuid, "IfcWall", westPanelType, "WALL");
        BOMAssembly.addComponent(conn, roomGuid, westGuid, "WALL_WEST", 0, 0, 0, seq++);

        // Light fixture (center of room)
        String lightGuid = "LIGHT_" + roomId;
        ensureElement(lightGuid, "IfcLightFixture", "Room Light", "LIGHT");
        BOMAssembly.addComponent(conn, roomGuid, lightGuid, "LIGHT",
            width / 2, depth / 2, height - 0.1, seq++);

        return roomGuid;
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
            System.out.println("Usage: FloorAssemblyBuilder <database.db>");
            return;
        }

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + args[0])) {
            conn.setAutoCommit(false);

            FloorAssemblyBuilder builder = new FloorAssemblyBuilder(conn);
            builder.ensureFloorTemplateTable();

            // Define a floor template
            try (PreparedStatement ps = conn.prepareStatement("""
                INSERT OR REPLACE INTO floor_templates
                (template_id, template_name, floor_type, width_m, depth_m, height_m, num_units, corridor_width_m)
                VALUES ('TYPICAL_CONDO_2UNIT', 'Typical Condo Floor (2 Units)', 'TYPICAL', 30, 20, 3, 2, 2)
                """)) {
                ps.execute();
            }

            // Create a floor from template
            String floorGuid = builder.createFloorFromTemplate("TYPICAL_F5", "TYPICAL_CONDO_2UNIT", 15.0);

            conn.commit();

            // Show the tree
            System.out.println("=".repeat(70));
            System.out.println("FLOOR ASSEMBLY BOM TREE");
            System.out.println("=".repeat(70));
            BOMAssembly.BOMNode tree = BOMAssembly.buildTree(conn, floorGuid);
            if (tree != null) {
                tree.printTree("");
            }

            // Count components
            System.out.println("\n" + "=".repeat(70));
            System.out.println("BOM SUMMARY (component counts)");
            System.out.println("=".repeat(70));
            countComponents(conn, floorGuid, 0);
        }
    }

    private static void countComponents(Connection conn, String guid, int depth) throws SQLException {
        String indent = "  ".repeat(depth);

        // Count direct children
        int childCount = 0;
        List<String> childGuids = new ArrayList<>();

        try (PreparedStatement ps = conn.prepareStatement(
            "SELECT component_guid FROM assembly_components WHERE assembly_guid = ?")) {
            ps.setString(1, guid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    childCount++;
                    childGuids.add(rs.getString("component_guid"));
                }
            }
        }

        // Get assembly name
        String name = guid;
        try (PreparedStatement ps = conn.prepareStatement(
            "SELECT name FROM element_assemblies WHERE assembly_guid = ?")) {
            ps.setString(1, guid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    name = rs.getString("name");
                }
            }
        }

        System.out.printf("%s%s: %d components%n", indent, name, childCount);

        // Recurse into sub-assemblies
        for (String childGuid : childGuids) {
            // Check if child is an assembly
            try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM element_assemblies WHERE assembly_guid = ?")) {
                ps.setString(1, childGuid);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        countComponents(conn, childGuid, depth + 1);
                    }
                }
            }
        }
    }
}
