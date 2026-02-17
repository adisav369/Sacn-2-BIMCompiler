package com.bim.compiler.bom;

import java.sql.*;
import java.util.*;

/**
 * BOM Variant System - ERP-style inheritance and selective overrides.
 *
 * Pattern:
 * 1. Define at high level → uses all defaults
 * 2. Override at any level → only specified parts change
 * 3. Mix variants → combine different sub-types
 *
 * Examples:
 *   CAR (default) = standard engine + standard wheels + standard body
 *   CAR [wheels.size=22"] = standard engine + 22" wheels + standard body
 *
 *   FLOOR_A (default) = TYPE_FLOOR_TYPICAL (all standard)
 *   FLOOR_B = FLOOR_A + [unit_A.living.wall_north = LARGE_WINDOW]
 *   FLOOR_C = TYPE_FLOOR_TYPICAL + [door_type=FD1, ceiling=3.6m]
 *
 * DSL Syntax:
 *   FLOOR ground { type: TYPE_FLOOR_GROUND }
 *   FLOOR typical : TYPE_FLOOR_TYPICAL {
 *     unit_A.living.wall_north: TYPE_WALL_LARGE_WINDOW
 *   }
 */
public class BOMVariantSystem {

    private final Connection conn;

    public BOMVariantSystem(Connection conn) {
        this.conn = conn;
    }

    /**
     * Initialize variant tables.
     */
    public void initSchema() throws SQLException {
        // Variant definitions (named configurations)
        conn.createStatement().execute("""
            CREATE TABLE IF NOT EXISTS bom_variants (
                variant_id TEXT PRIMARY KEY,
                base_type_id TEXT NOT NULL,       -- Parent type to inherit from
                variant_name TEXT NOT NULL,
                description TEXT,

                created_at TEXT DEFAULT CURRENT_TIMESTAMP
            )
            """);

        // Variant overrides (path → value)
        conn.createStatement().execute("""
            CREATE TABLE IF NOT EXISTS bom_variant_overrides (
                variant_id TEXT NOT NULL REFERENCES bom_variants(variant_id),
                override_path TEXT NOT NULL,      -- e.g., "unit_A.living.wall_north"
                override_type TEXT NOT NULL,      -- COMPONENT, DIMENSION, PROPERTY
                override_value TEXT NOT NULL,     -- Type ID, number, or value

                PRIMARY KEY (variant_id, override_path)
            )
            """);
    }

    /**
     * Override specification for a component path.
     */
    public record Override(
        String path,      // e.g., "unit_A.living.wall_north" or "door_type" or "ceiling_height"
        String type,      // COMPONENT, DIMENSION, PROPERTY
        String value      // Type ID, number, or string value
    ) {}

    /**
     * Create a variant from a base type with overrides.
     */
    public String createVariant(String variantId, String baseTypeId, String variantName,
                                List<Override> overrides) throws SQLException {
        // Insert variant
        try (PreparedStatement ps = conn.prepareStatement("""
            INSERT OR REPLACE INTO bom_variants (variant_id, base_type_id, variant_name)
            VALUES (?, ?, ?)
            """)) {
            ps.setString(1, variantId);
            ps.setString(2, baseTypeId);
            ps.setString(3, variantName);
            ps.execute();
        }

        // Insert overrides
        for (Override override : overrides) {
            try (PreparedStatement ps = conn.prepareStatement("""
                INSERT OR REPLACE INTO bom_variant_overrides
                (variant_id, override_path, override_type, override_value)
                VALUES (?, ?, ?, ?)
                """)) {
                ps.setString(1, variantId);
                ps.setString(2, override.path);
                ps.setString(3, override.type);
                ps.setString(4, override.value);
                ps.execute();
            }
        }

        return variantId;
    }

    /**
     * Resolve a variant to its effective configuration.
     * Starts with base type, applies overrides.
     */
    public ResolvedBOM resolveVariant(String variantId) throws SQLException {
        // Get variant info
        String baseTypeId = null;
        String variantName = null;

        try (PreparedStatement ps = conn.prepareStatement(
            "SELECT base_type_id, variant_name FROM bom_variants WHERE variant_id = ?")) {
            ps.setString(1, variantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    baseTypeId = rs.getString("base_type_id");
                    variantName = rs.getString("variant_name");
                }
            }
        }

        if (baseTypeId == null) {
            // Not a variant, might be a direct type
            return resolveType(variantId);
        }

        // Start with base type resolution
        ResolvedBOM base = resolveType(baseTypeId);

        // Apply overrides
        Map<String, Override> overrides = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(
            "SELECT override_path, override_type, override_value FROM bom_variant_overrides WHERE variant_id = ?")) {
            ps.setString(1, variantId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String path = rs.getString("override_path");
                    overrides.put(path, new Override(
                        path,
                        rs.getString("override_type"),
                        rs.getString("override_value")
                    ));
                }
            }
        }

        return applyOverrides(base, overrides, variantName);
    }

    /**
     * Resolve a base type to its BOM structure.
     */
    public ResolvedBOM resolveType(String typeId) throws SQLException {
        ResolvedBOM bom = new ResolvedBOM(typeId);

        // Get type info
        try (PreparedStatement ps = conn.prepareStatement(
            "SELECT * FROM bom_types WHERE type_id = ?")) {
            ps.setString(1, typeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    bom.name = rs.getString("type_name");
                    bom.ifcClass = rs.getString("ifc_type_class");
                    bom.width = rs.getDouble("width_m");
                    bom.depth = rs.getDouble("depth_m");
                    bom.height = rs.getDouble("height_m");
                }
            }
        }

        // Get components
        try (PreparedStatement ps = conn.prepareStatement("""
            SELECT * FROM bom_type_components WHERE type_id = ? ORDER BY component_seq
            """)) {
            ps.setString(1, typeId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ResolvedComponent comp = new ResolvedComponent();
                    comp.seq = rs.getInt("component_seq");
                    comp.name = rs.getString("component_name");
                    comp.role = rs.getString("role");
                    comp.localX = rs.getDouble("local_x");
                    comp.localY = rs.getDouble("local_y");
                    comp.localZ = rs.getDouble("local_z");
                    comp.quantity = rs.getInt("quantity");
                    comp.componentTypeId = rs.getString("component_type_id");
                    comp.ifcClass = rs.getString("component_ifc_class");

                    // If component is a type, resolve recursively
                    if (comp.componentTypeId != null) {
                        comp.nestedBOM = resolveType(comp.componentTypeId);
                    }

                    bom.components.put(comp.role, comp);
                }
            }
        }

        return bom;
    }

    /**
     * Apply overrides to a resolved BOM.
     */
    private ResolvedBOM applyOverrides(ResolvedBOM base, Map<String, Override> overrides, String variantName) {
        ResolvedBOM result = base.copy();
        result.name = variantName;
        result.appliedOverrides = new ArrayList<>(overrides.values());

        for (Override override : overrides.values()) {
            String[] pathParts = override.path.split("\\.");

            if (pathParts.length == 1) {
                // Top-level override (dimension or property)
                applyTopLevelOverride(result, override);
            } else {
                // Nested component override
                applyNestedOverride(result, pathParts, 0, override);
            }
        }

        return result;
    }

    private void applyTopLevelOverride(ResolvedBOM bom, Override override) {
        switch (override.path) {
            case "width", "width_m" -> bom.width = Double.parseDouble(override.value);
            case "depth", "depth_m" -> bom.depth = Double.parseDouble(override.value);
            case "height", "height_m", "ceiling_height" -> bom.height = Double.parseDouble(override.value);
            case "door_type" -> {
                // Override all doors in BOM
                for (ResolvedComponent comp : bom.components.values()) {
                    if (comp.role.contains("DOOR") || comp.role.contains("OPENING")) {
                        comp.componentTypeId = "TYPE_DOOR_" + override.value;
                    }
                    if (comp.nestedBOM != null) {
                        applyTopLevelOverride(comp.nestedBOM, override);
                    }
                }
            }
            case "window_type" -> {
                // Override all windows
                for (ResolvedComponent comp : bom.components.values()) {
                    if (comp.role.contains("WINDOW")) {
                        comp.componentTypeId = "TYPE_WINDOW_" + override.value;
                    }
                    if (comp.nestedBOM != null) {
                        applyTopLevelOverride(comp.nestedBOM, override);
                    }
                }
            }
        }
    }

    private void applyNestedOverride(ResolvedBOM bom, String[] pathParts, int depth, Override override) {
        if (depth >= pathParts.length) return;

        String targetRole = pathParts[depth].toUpperCase();

        for (ResolvedComponent comp : bom.components.values()) {
            if (comp.role.toUpperCase().contains(targetRole) ||
                comp.name.toUpperCase().contains(targetRole)) {

                if (depth == pathParts.length - 1) {
                    // Final level - apply override
                    if (override.type.equals("COMPONENT")) {
                        comp.componentTypeId = override.value;
                        comp.nestedBOM = null;  // Will be re-resolved
                    } else if (override.type.equals("DIMENSION")) {
                        // Apply to nested BOM dimensions
                        if (comp.nestedBOM != null) {
                            applyTopLevelOverride(comp.nestedBOM,
                                new Override(override.path.substring(override.path.lastIndexOf('.') + 1),
                                            override.type, override.value));
                        }
                    }
                } else if (comp.nestedBOM != null) {
                    // Continue down the path
                    applyNestedOverride(comp.nestedBOM, pathParts, depth + 1, override);
                }
            }
        }
    }

    /**
     * Resolved BOM structure (after applying inheritance and overrides).
     */
    public static class ResolvedBOM {
        public String typeId;
        public String name;
        public String ifcClass;
        public double width, depth, height;
        public Map<String, ResolvedComponent> components = new LinkedHashMap<>();
        public List<Override> appliedOverrides = new ArrayList<>();

        public ResolvedBOM(String typeId) {
            this.typeId = typeId;
        }

        public ResolvedBOM copy() {
            ResolvedBOM copy = new ResolvedBOM(typeId);
            copy.name = name;
            copy.ifcClass = ifcClass;
            copy.width = width;
            copy.depth = depth;
            copy.height = height;
            for (var entry : components.entrySet()) {
                copy.components.put(entry.getKey(), entry.getValue().copy());
            }
            return copy;
        }

        public void print(String indent) {
            System.out.println(indent + name + " (" + typeId + ")");
            System.out.println(indent + "  Dimensions: " + width + " × " + depth + " × " + height + "m");
            if (!appliedOverrides.isEmpty()) {
                System.out.println(indent + "  Overrides:");
                for (Override o : appliedOverrides) {
                    System.out.println(indent + "    - " + o.path + " = " + o.value);
                }
            }
            System.out.println(indent + "  Components:");
            for (ResolvedComponent comp : components.values()) {
                comp.print(indent + "    ");
            }
        }
    }

    public static class ResolvedComponent {
        public int seq;
        public String name;
        public String role;
        public double localX, localY, localZ;
        public int quantity;
        public String componentTypeId;
        public String ifcClass;
        public ResolvedBOM nestedBOM;

        public ResolvedComponent copy() {
            ResolvedComponent copy = new ResolvedComponent();
            copy.seq = seq;
            copy.name = name;
            copy.role = role;
            copy.localX = localX;
            copy.localY = localY;
            copy.localZ = localZ;
            copy.quantity = quantity;
            copy.componentTypeId = componentTypeId;
            copy.ifcClass = ifcClass;
            if (nestedBOM != null) {
                copy.nestedBOM = nestedBOM.copy();
            }
            return copy;
        }

        public void print(String indent) {
            String typeInfo = componentTypeId != null ? " → " + componentTypeId : "";
            System.out.println(indent + "[" + role + "] " + name + typeInfo);
            if (nestedBOM != null) {
                nestedBOM.print(indent + "  ");
            }
        }
    }

    // =========================================================================
    // Demo
    // =========================================================================

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: BOMVariantSystem <database.db>");
            return;
        }

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + args[0])) {
            conn.setAutoCommit(false);

            // Initialize type system first
            BOMTypeSystem typeSystem = new BOMTypeSystem(conn);
            typeSystem.initSchema();
            typeSystem.defineCondoMidTypes();

            // Initialize variant system
            BOMVariantSystem variantSystem = new BOMVariantSystem(conn);
            variantSystem.initSchema();

            // Create variants
            System.out.println("=".repeat(70));
            System.out.println("CREATING VARIANTS");
            System.out.println("=".repeat(70));

            // Variant 1: Standard floor (no overrides)
            variantSystem.createVariant(
                "VAR_FLOOR_STANDARD",
                "TYPE_FLOOR_TYPICAL_2UNIT",
                "Standard Typical Floor",
                List.of()
            );
            System.out.println("Created: VAR_FLOOR_STANDARD (base type, no overrides)");

            // Variant 2: Floor with higher ceiling
            variantSystem.createVariant(
                "VAR_FLOOR_HIGH_CEILING",
                "TYPE_FLOOR_TYPICAL_2UNIT",
                "High Ceiling Floor (3.6m)",
                List.of(
                    new Override("ceiling_height", "DIMENSION", "3.6")
                )
            );
            System.out.println("Created: VAR_FLOOR_HIGH_CEILING (ceiling_height = 3.6m)");

            // Variant 3: Floor with fire doors
            variantSystem.createVariant(
                "VAR_FLOOR_FIRE_RATED",
                "TYPE_FLOOR_TYPICAL_2UNIT",
                "Fire Rated Floor (FD1 doors)",
                List.of(
                    new Override("door_type", "PROPERTY", "FD1")
                )
            );
            System.out.println("Created: VAR_FLOOR_FIRE_RATED (all doors = FD1)");

            // Variant 4: Floor with large windows in living room
            variantSystem.createVariant(
                "VAR_FLOOR_LARGE_WINDOWS",
                "TYPE_FLOOR_TYPICAL_2UNIT",
                "Floor with Large Living Room Windows",
                List.of(
                    new Override("unit_A.living.wall_north", "COMPONENT", "TYPE_WALL_PANEL_LARGE_WINDOW"),
                    new Override("unit_B.living.wall_north", "COMPONENT", "TYPE_WALL_PANEL_LARGE_WINDOW")
                )
            );
            System.out.println("Created: VAR_FLOOR_LARGE_WINDOWS (living room north walls = large windows)");

            // Variant 5: Penthouse (multiple overrides)
            variantSystem.createVariant(
                "VAR_FLOOR_PENTHOUSE",
                "TYPE_FLOOR_TYPICAL_2UNIT",
                "Penthouse Floor",
                List.of(
                    new Override("ceiling_height", "DIMENSION", "4.0"),
                    new Override("door_type", "PROPERTY", "D3_PREMIUM"),
                    new Override("window_type", "PROPERTY", "W2_FLOOR_TO_CEILING")
                )
            );
            System.out.println("Created: VAR_FLOOR_PENTHOUSE (4m ceiling, premium doors, floor-to-ceiling windows)");

            conn.commit();

            // Resolve and display variants
            System.out.println("\n" + "=".repeat(70));
            System.out.println("RESOLVED VARIANTS");
            System.out.println("=".repeat(70));

            String[] variants = {
                "VAR_FLOOR_STANDARD",
                "VAR_FLOOR_HIGH_CEILING",
                "VAR_FLOOR_FIRE_RATED",
                "VAR_FLOOR_PENTHOUSE"
            };

            for (String variantId : variants) {
                System.out.println("\n--- " + variantId + " ---");
                ResolvedBOM resolved = variantSystem.resolveVariant(variantId);
                resolved.print("");
            }

            System.out.println("\n" + "=".repeat(70));
            System.out.println("DSL SYNTAX EXAMPLES");
            System.out.println("=".repeat(70));
            System.out.println("""

                // High-level definition (uses all defaults)
                FLOOR ground { type: TYPE_FLOOR_GROUND }

                // Use predefined variant
                FLOOR level_2 { variant: VAR_FLOOR_STANDARD }
                FLOOR level_3 { variant: VAR_FLOOR_HIGH_CEILING }
                FLOOR penthouse { variant: VAR_FLOOR_PENTHOUSE }

                // Inline overrides
                FLOOR level_10 : TYPE_FLOOR_TYPICAL {
                    ceiling_height: 3.3
                    unit_A.living.wall_north: TYPE_WALL_LARGE_WINDOW
                }

                // Copy and modify
                FLOOR level_11 : level_10 {
                    door_type: FD1  // Add fire doors to level_10 config
                }
                """);
        }
    }
}
