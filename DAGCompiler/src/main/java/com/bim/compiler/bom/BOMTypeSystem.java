package com.bim.compiler.bom;

import java.sql.*;
import java.util.*;

/**
 * BOM Type System - Template/Instance pattern for IFC compatibility.
 *
 * IFC Pattern:
 * - IfcTypeObject = BOM template definition (shared properties)
 * - IfcRelDefinesByType = links instances to their type
 * - Change type → all instances refresh
 *
 * This aligns with Bonsai/IFC workflow:
 * - Define types (door type, wall panel type, room type, floor type)
 * - Create instances that reference types
 * - Modify type → all instances update
 *
 * Database Schema:
 * - bom_types: Type definitions (templates)
 * - bom_type_components: Components within a type
 * - bom_instances: Instances that reference types
 * - bom_instance_overrides: Per-instance property overrides
 */
public class BOMTypeSystem {

    private final Connection conn;

    public BOMTypeSystem(Connection conn) {
        this.conn = conn;
    }

    /**
     * Initialize BOM type system tables.
     */
    public void initSchema() throws SQLException {
        // BOM Type definitions (like IfcTypeObject)
        conn.createStatement().execute("""
            CREATE TABLE IF NOT EXISTS bom_types (
                type_id TEXT PRIMARY KEY,
                type_name TEXT NOT NULL,
                ifc_type_class TEXT NOT NULL,    -- IfcWallType, IfcDoorType, etc.
                category TEXT NOT NULL,           -- WALL_PANEL, DOOR, ROOM, FLOOR, etc.

                -- Dimensions (defaults for instances)
                width_m REAL,
                depth_m REAL,
                height_m REAL,

                -- Configuration
                config_json TEXT,                 -- Flexible key-value config

                -- Library reference
                library_source TEXT,              -- Where this type came from
                version TEXT DEFAULT '1.0',

                -- Metadata
                description TEXT,
                created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                updated_at TEXT DEFAULT CURRENT_TIMESTAMP
            )
            """);

        // Components within a type (like IfcRelAggregates for types)
        conn.createStatement().execute("""
            CREATE TABLE IF NOT EXISTS bom_type_components (
                type_id TEXT NOT NULL REFERENCES bom_types(type_id),
                component_seq INTEGER NOT NULL,

                -- Component can be another type (nested) or a product class
                component_type_id TEXT,           -- Reference to another bom_type (nested BOM)
                component_ifc_class TEXT,         -- Or direct IFC class (leaf)
                component_name TEXT NOT NULL,
                role TEXT NOT NULL,               -- FRAME, CLADDING, OPENING, etc.

                -- Local position within parent type
                local_x REAL DEFAULT 0,
                local_y REAL DEFAULT 0,
                local_z REAL DEFAULT 0,

                -- Quantity
                quantity INTEGER DEFAULT 1,
                is_optional BOOLEAN DEFAULT FALSE,

                PRIMARY KEY (type_id, component_seq)
            )
            """);

        // BOM Instances (like IfcElement with IfcRelDefinesByType)
        conn.createStatement().execute("""
            CREATE TABLE IF NOT EXISTS bom_instances (
                instance_id TEXT PRIMARY KEY,
                type_id TEXT NOT NULL REFERENCES bom_types(type_id),

                -- World position
                world_x REAL NOT NULL,
                world_y REAL NOT NULL,
                world_z REAL NOT NULL,
                rotation_z REAL DEFAULT 0,

                -- Context
                storey TEXT,
                parent_instance_id TEXT,          -- For nested instances

                -- Instance-specific
                instance_name TEXT,

                created_at TEXT DEFAULT CURRENT_TIMESTAMP
            )
            """);

        // Per-instance overrides (customize without changing type)
        conn.createStatement().execute("""
            CREATE TABLE IF NOT EXISTS bom_instance_overrides (
                instance_id TEXT NOT NULL REFERENCES bom_instances(instance_id),
                property_name TEXT NOT NULL,
                override_value TEXT NOT NULL,

                PRIMARY KEY (instance_id, property_name)
            )
            """);

        // Index for fast "find all instances of type" queries
        conn.createStatement().execute("""
            CREATE INDEX IF NOT EXISTS idx_bom_instances_type
            ON bom_instances(type_id)
            """);
    }

    // =========================================================================
    // Type Definition
    // =========================================================================

    /**
     * Define a BOM type (template).
     */
    public void defineType(String typeId, String typeName, String ifcTypeClass,
                          String category, double width, double depth, double height,
                          String description) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
            INSERT OR REPLACE INTO bom_types
            (type_id, type_name, ifc_type_class, category, width_m, depth_m, height_m, description, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            """)) {
            ps.setString(1, typeId);
            ps.setString(2, typeName);
            ps.setString(3, ifcTypeClass);
            ps.setString(4, category);
            ps.setDouble(5, width);
            ps.setDouble(6, depth);
            ps.setDouble(7, height);
            ps.setString(8, description);
            ps.execute();
        }
    }

    /**
     * Add component to type definition.
     */
    public void addTypeComponent(String typeId, int seq, String componentTypeId,
                                 String ifcClass, String name, String role,
                                 double localX, double localY, double localZ,
                                 int quantity) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
            INSERT OR REPLACE INTO bom_type_components
            (type_id, component_seq, component_type_id, component_ifc_class,
             component_name, role, local_x, local_y, local_z, quantity)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """)) {
            ps.setString(1, typeId);
            ps.setInt(2, seq);
            ps.setString(3, componentTypeId);
            ps.setString(4, ifcClass);
            ps.setString(5, name);
            ps.setString(6, role);
            ps.setDouble(7, localX);
            ps.setDouble(8, localY);
            ps.setDouble(9, localZ);
            ps.setInt(10, quantity);
            ps.execute();
        }
    }

    // =========================================================================
    // Instance Creation
    // =========================================================================

    /**
     * Create instance of a type at world position.
     */
    public String createInstance(String typeId, String instanceName,
                                 double worldX, double worldY, double worldZ,
                                 double rotationZ, String storey) throws SQLException {
        String instanceId = "INST_" + typeId + "_" + storey + "_" +
                           String.format("%.0f_%.0f", worldX, worldY);

        try (PreparedStatement ps = conn.prepareStatement("""
            INSERT OR REPLACE INTO bom_instances
            (instance_id, type_id, world_x, world_y, world_z, rotation_z, storey, instance_name)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """)) {
            ps.setString(1, instanceId);
            ps.setString(2, typeId);
            ps.setDouble(3, worldX);
            ps.setDouble(4, worldY);
            ps.setDouble(5, worldZ);
            ps.setDouble(6, rotationZ);
            ps.setString(7, storey);
            ps.setString(8, instanceName);
            ps.execute();
        }

        return instanceId;
    }

    // =========================================================================
    // Refresh All Instances (Key Feature!)
    // =========================================================================

    /**
     * Get all instances of a type.
     * Used when type changes to refresh all instances.
     */
    public List<String> getInstancesOfType(String typeId) throws SQLException {
        List<String> instances = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
            "SELECT instance_id FROM bom_instances WHERE type_id = ?")) {
            ps.setString(1, typeId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    instances.add(rs.getString("instance_id"));
                }
            }
        }
        return instances;
    }

    /**
     * Refresh all instances of a type after type modification.
     * This regenerates geometry for all instances based on updated type definition.
     *
     * @param typeId The type that was modified
     * @return Number of instances refreshed
     */
    public int refreshAllInstancesOfType(String typeId) throws SQLException {
        List<String> instances = getInstancesOfType(typeId);

        System.out.println("[BOM] Refreshing " + instances.size() + " instances of type: " + typeId);

        for (String instanceId : instances) {
            refreshInstance(instanceId);
        }

        return instances.size();
    }

    /**
     * Refresh a single instance (regenerate from type + overrides).
     */
    public void refreshInstance(String instanceId) throws SQLException {
        // Get instance info
        String typeId = null;
        double worldX = 0, worldY = 0, worldZ = 0, rotationZ = 0;
        String storey = null;

        try (PreparedStatement ps = conn.prepareStatement(
            "SELECT * FROM bom_instances WHERE instance_id = ?")) {
            ps.setString(1, instanceId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    typeId = rs.getString("type_id");
                    worldX = rs.getDouble("world_x");
                    worldY = rs.getDouble("world_y");
                    worldZ = rs.getDouble("world_z");
                    rotationZ = rs.getDouble("rotation_z");
                    storey = rs.getString("storey");
                }
            }
        }

        if (typeId == null) return;

        // Get instance overrides
        Map<String, String> overrides = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(
            "SELECT property_name, override_value FROM bom_instance_overrides WHERE instance_id = ?")) {
            ps.setString(1, instanceId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    overrides.put(rs.getString("property_name"), rs.getString("override_value"));
                }
            }
        }

        // Regenerate assembly from type + overrides
        regenerateAssemblyFromType(instanceId, typeId, worldX, worldY, worldZ, rotationZ, storey, overrides);
    }

    /**
     * Regenerate assembly structure from type definition.
     */
    private void regenerateAssemblyFromType(String instanceId, String typeId,
                                            double worldX, double worldY, double worldZ,
                                            double rotationZ, String storey,
                                            Map<String, String> overrides) throws SQLException {
        // Clear existing assembly components for this instance
        try (PreparedStatement ps = conn.prepareStatement(
            "DELETE FROM assembly_components WHERE assembly_guid = ?")) {
            ps.setString(1, instanceId);
            ps.execute();
        }

        // Get type definition
        double typeWidth = 0, typeDepth = 0, typeHeight = 0;
        String typeName = null, ifcClass = null;

        try (PreparedStatement ps = conn.prepareStatement(
            "SELECT * FROM bom_types WHERE type_id = ?")) {
            ps.setString(1, typeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    typeName = rs.getString("type_name");
                    ifcClass = rs.getString("ifc_type_class");
                    typeWidth = rs.getDouble("width_m");
                    typeDepth = rs.getDouble("depth_m");
                    typeHeight = rs.getDouble("height_m");
                }
            }
        }

        // Apply overrides
        if (overrides.containsKey("width")) typeWidth = Double.parseDouble(overrides.get("width"));
        if (overrides.containsKey("depth")) typeDepth = Double.parseDouble(overrides.get("depth"));
        if (overrides.containsKey("height")) typeHeight = Double.parseDouble(overrides.get("height"));

        // Create/update assembly
        BOMAssembly.defineAssembly(conn, instanceId, typeName + " @ " + storey,
                                   typeId, ifcClass, storey, typeWidth, typeDepth, typeHeight);

        // Add components from type definition
        try (PreparedStatement ps = conn.prepareStatement("""
            SELECT * FROM bom_type_components WHERE type_id = ? ORDER BY component_seq
            """)) {
            ps.setString(1, typeId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String componentName = rs.getString("component_name");
                    String role = rs.getString("role");
                    double localX = rs.getDouble("local_x");
                    double localY = rs.getDouble("local_y");
                    double localZ = rs.getDouble("local_z");
                    int seq = rs.getInt("component_seq");
                    int qty = rs.getInt("quantity");
                    String componentTypeId = rs.getString("component_type_id");
                    String componentIfcClass = rs.getString("component_ifc_class");

                    // Transform local to world (apply rotation)
                    double cos = Math.cos(rotationZ);
                    double sin = Math.sin(rotationZ);
                    double compWorldX = worldX + localX * cos - localY * sin;
                    double compWorldY = worldY + localX * sin + localY * cos;
                    double compWorldZ = worldZ + localZ;

                    for (int q = 0; q < qty; q++) {
                        String componentGuid = instanceId + "_" + role + "_" + seq + "_" + q;

                        if (componentTypeId != null) {
                            // Nested type - create sub-instance
                            createInstance(componentTypeId, componentName,
                                          compWorldX, compWorldY, compWorldZ, rotationZ, storey);
                        } else {
                            // Leaf element
                            ensureElement(componentGuid, componentIfcClass, componentName, role);
                        }

                        BOMAssembly.addComponent(conn, instanceId, componentGuid, role,
                                                localX, localY, localZ, seq * 10 + q);
                    }
                }
            }
        }
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
    // Demo: Define standard types for Condo Mid
    // =========================================================================

    public void defineCondoMidTypes() throws SQLException {
        // Wall Panel with Door type
        defineType("TYPE_WALL_PANEL_D2", "Wall Panel with D2 Door", "IfcWallType",
                  "WALL_PANEL_WITH_DOOR", 3.0, 0.15, 3.0,
                  "Standard 3m wall panel with 1.1m D2 door");

        addTypeComponent("TYPE_WALL_PANEL_D2", 1, null, "IfcPlate",
                        "Metal Deck Cladding", "CLADDING", 0, 0, 0, 1);
        addTypeComponent("TYPE_WALL_PANEL_D2", 2, null, "IfcMember",
                        "Steel Frame", "FRAME", 0, 0, 0, 1);
        addTypeComponent("TYPE_WALL_PANEL_D2", 3, "TYPE_DOOR_D2", null,
                        "D2 Door Assembly", "OPENING", 1.0, 0, 0, 1);

        // Door D2 type
        defineType("TYPE_DOOR_D2", "D2 Door Assembly", "IfcDoorType",
                  "DOOR_ASSEMBLY", 1.1, 0.05, 2.1,
                  "Standard D2 door (1100x2100mm)");

        addTypeComponent("TYPE_DOOR_D2", 1, null, "IfcMember",
                        "Door Frame", "FRAME", 0, 0, 0, 1);
        addTypeComponent("TYPE_DOOR_D2", 2, null, "IfcDoor",
                        "Door Leaf", "LEAF", 0.05, 0, 0, 1);
        addTypeComponent("TYPE_DOOR_D2", 3, null, "IfcDiscreteAccessory",
                        "Door Hardware", "HARDWARE", 0, 0, 1.0, 1);

        // Room type (Living)
        defineType("TYPE_ROOM_LIVING", "Living Room", "IfcSpaceType",
                  "ROOM_LIVING", 6.0, 5.0, 3.0,
                  "Standard living room with window");

        addTypeComponent("TYPE_ROOM_LIVING", 1, "TYPE_WALL_PANEL_WINDOW", null,
                        "North Wall (Window)", "WALL_NORTH", 0, 5.0, 0, 1);
        addTypeComponent("TYPE_ROOM_LIVING", 2, "TYPE_WALL_PANEL_D2", null,
                        "South Wall (Door)", "WALL_SOUTH", 0, 0, 0, 1);
        addTypeComponent("TYPE_ROOM_LIVING", 3, "TYPE_WALL_PANEL_PLAIN", null,
                        "East Wall", "WALL_EAST", 6.0, 0, 0, 1);
        addTypeComponent("TYPE_ROOM_LIVING", 4, "TYPE_WALL_PANEL_PLAIN", null,
                        "West Wall", "WALL_WEST", 0, 0, 0, 1);
        addTypeComponent("TYPE_ROOM_LIVING", 5, null, "IfcLightFixture",
                        "Ceiling Light", "LIGHT", 3.0, 2.5, 2.9, 1);

        // Plain wall panel type
        defineType("TYPE_WALL_PANEL_PLAIN", "Plain Wall Panel", "IfcWallType",
                  "WALL_PANEL_PLAIN", 3.0, 0.15, 3.0, "Standard 3m plain wall panel");

        addTypeComponent("TYPE_WALL_PANEL_PLAIN", 1, null, "IfcPlate",
                        "Metal Deck Cladding", "CLADDING", 0, 0, 0, 1);
        addTypeComponent("TYPE_WALL_PANEL_PLAIN", 2, null, "IfcMember",
                        "Steel Frame", "FRAME", 0, 0, 0, 1);

        // Wall panel with window type
        defineType("TYPE_WALL_PANEL_WINDOW", "Wall Panel with Window", "IfcWallType",
                  "WALL_PANEL_WITH_WINDOW", 3.0, 0.15, 3.0, "Standard 3m wall panel with 1.2m window");

        addTypeComponent("TYPE_WALL_PANEL_WINDOW", 1, null, "IfcPlate",
                        "Metal Deck Cladding", "CLADDING", 0, 0, 0, 1);
        addTypeComponent("TYPE_WALL_PANEL_WINDOW", 2, null, "IfcMember",
                        "Steel Frame", "FRAME", 0, 0, 0, 1);
        addTypeComponent("TYPE_WALL_PANEL_WINDOW", 3, null, "IfcWindow",
                        "Window W1", "OPENING", 0.9, 0, 0.9, 1);

        // Typical floor type
        defineType("TYPE_FLOOR_TYPICAL_2UNIT", "Typical Floor (2 Units)", "IfcBuildingStoreyType",
                  "FLOOR_TYPICAL", 30.0, 20.0, 3.0, "Typical condo floor with 2 units");

        addTypeComponent("TYPE_FLOOR_TYPICAL_2UNIT", 1, null, "IfcSlab",
                        "Floor Slab", "SLAB", 0, 0, 0, 1);
        addTypeComponent("TYPE_FLOOR_TYPICAL_2UNIT", 2, "TYPE_ROOM_LIVING", null,
                        "Unit A Living", "UNIT_A_LIVING", 0, 2, 0, 1);
        addTypeComponent("TYPE_FLOOR_TYPICAL_2UNIT", 3, "TYPE_ROOM_LIVING", null,
                        "Unit B Living", "UNIT_B_LIVING", 15, 2, 0, 1);

        System.out.println("[BOM] Defined Condo Mid types:");
        System.out.println("  - TYPE_WALL_PANEL_D2 (wall with D2 door)");
        System.out.println("  - TYPE_WALL_PANEL_WINDOW (wall with window)");
        System.out.println("  - TYPE_WALL_PANEL_PLAIN (plain wall)");
        System.out.println("  - TYPE_DOOR_D2 (door assembly)");
        System.out.println("  - TYPE_ROOM_LIVING (living room)");
        System.out.println("  - TYPE_FLOOR_TYPICAL_2UNIT (typical floor)");
    }

    // =========================================================================
    // Demo
    // =========================================================================

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: BOMTypeSystem <database.db> [--refresh TYPE_ID]");
            return;
        }

        String dbPath = args[0];
        boolean doRefresh = args.length > 2 && args[1].equals("--refresh");
        String refreshTypeId = doRefresh ? args[2] : null;

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            conn.setAutoCommit(false);

            BOMTypeSystem typeSystem = new BOMTypeSystem(conn);
            typeSystem.initSchema();

            if (doRefresh) {
                // Refresh all instances of specified type
                int count = typeSystem.refreshAllInstancesOfType(refreshTypeId);
                System.out.println("Refreshed " + count + " instances of " + refreshTypeId);
            } else {
                // Define types and create demo instances
                typeSystem.defineCondoMidTypes();

                // Create some instances
                String inst1 = typeSystem.createInstance("TYPE_WALL_PANEL_D2", "Entry Wall",
                                                         5.0, 0.0, 0.0, 0, "Ground");
                String inst2 = typeSystem.createInstance("TYPE_WALL_PANEL_D2", "Entry Wall 2",
                                                         8.0, 0.0, 0.0, 0, "Ground");
                String inst3 = typeSystem.createInstance("TYPE_WALL_PANEL_D2", "Entry Wall",
                                                         5.0, 0.0, 3.0, 0, "Level_1");

                System.out.println("\nCreated instances:");
                System.out.println("  - " + inst1);
                System.out.println("  - " + inst2);
                System.out.println("  - " + inst3);

                // Show instances of TYPE_WALL_PANEL_D2
                List<String> instances = typeSystem.getInstancesOfType("TYPE_WALL_PANEL_D2");
                System.out.println("\nAll instances of TYPE_WALL_PANEL_D2: " + instances.size());
                for (String inst : instances) {
                    System.out.println("  - " + inst);
                }

                System.out.println("\n" + "=".repeat(60));
                System.out.println("To refresh all instances after modifying type:");
                System.out.println("  BOMTypeSystem " + dbPath + " --refresh TYPE_WALL_PANEL_D2");
                System.out.println("=".repeat(60));
            }

            conn.commit();
        }
    }
}
