package com.bim.compiler.dsl;

import com.bim.compiler.util.OutlierLogger;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Phase 29: Runtime-loadable space type definitions.
 *
 * Loads space type properties from config/spacetypes.yaml.
 * Enables adding new space types without recompiling.
 *
 * Usage:
 *   SpaceTypeConfig config = SpaceTypeRegistry.get("BEDROOM");
 *   double minArea = config.validation().minArea();
 */
public class SpaceTypeRegistry {

    private static final String CONFIG_PATH = "config/spacetypes.yaml";
    private static final Map<String, SpaceTypeConfig> registry = new HashMap<>();
    private static final Map<String, String> aliasMap = new HashMap<>();
    private static boolean loaded = false;

    /**
     * Space type configuration record.
     */
    public record SpaceTypeConfig(
        String name,
        String category,
        String omniclass,
        String wallRule,
        ValidationConfig validation,
        boolean isSleepingRoom,
        boolean isOpenPlan,
        boolean isExterior,
        List<String> zonesAllowed,
        List<String> aliases
    ) {
        /**
         * Get wall rule as enum for compatibility with existing code.
         */
        public RoomType.WallRule getWallRuleEnum() {
            return switch (wallRule) {
                case "ENCLOSED" -> RoomType.WallRule.ENCLOSED;
                case "PERIMETER_ONLY" -> RoomType.WallRule.PERIMETER_ONLY;
                case "NONE" -> RoomType.WallRule.NONE;
                default -> RoomType.WallRule.AS_REQUIRED;
            };
        }

        /**
         * Check if this is a habitable space.
         */
        public boolean isHabitable() {
            return "HABITABLE".equals(category);
        }
    }

    /**
     * Validation rules for a space type.
     */
    public record ValidationConfig(
        double minArea,
        double minDimension,
        boolean requiresWindow,
        boolean requiresEgress
    ) {}

    /**
     * Get configuration for a space type by name.
     * Falls back to GENERIC if not found.
     */
    public static SpaceTypeConfig get(String typeName) {
        ensureLoaded();
        String normalized = normalize(typeName);

        // Direct match
        if (registry.containsKey(normalized)) {
            return registry.get(normalized);
        }

        // Alias match
        if (aliasMap.containsKey(normalized)) {
            String canonical = aliasMap.get(normalized);
            return registry.get(canonical);
        }

        // Fallback to GENERIC with OutlierLogger
        OutlierLogger.logUnknownSpaceType(typeName, "GENERIC");
        return registry.getOrDefault("GENERIC", createDefaultGeneric());
    }

    /**
     * Get configuration for a RoomType enum value.
     */
    public static SpaceTypeConfig get(RoomType roomType) {
        return get(roomType.name());
    }

    /**
     * Check if a space type exists in the registry.
     */
    public static boolean exists(String typeName) {
        ensureLoaded();
        String normalized = normalize(typeName);
        return registry.containsKey(normalized) || aliasMap.containsKey(normalized);
    }

    /**
     * Get all registered space type names.
     */
    public static Set<String> getAllTypes() {
        ensureLoaded();
        return Collections.unmodifiableSet(registry.keySet());
    }

    /**
     * Reload configuration from file.
     * Call this after modifying config/spacetypes.yaml.
     */
    public static void reload() {
        loaded = false;
        registry.clear();
        aliasMap.clear();
        ensureLoaded();
    }

    /**
     * Ensure configuration is loaded.
     */
    private static synchronized void ensureLoaded() {
        if (loaded) return;

        try {
            loadFromYaml();
            loaded = true;
            System.out.printf("[SpaceTypeRegistry] Loaded %d space types from %s%n",
                registry.size(), CONFIG_PATH);
        } catch (Exception e) {
            System.err.println("[SpaceTypeRegistry] Failed to load " + CONFIG_PATH + ": " + e.getMessage());
            System.err.println("[SpaceTypeRegistry] Using built-in defaults");
            loadDefaults();
            loaded = true;
        }
    }

    /**
     * Load configuration from YAML file.
     */
    @SuppressWarnings("unchecked")
    private static void loadFromYaml() throws IOException {
        Path configPath = Paths.get(CONFIG_PATH);
        if (!Files.exists(configPath)) {
            throw new FileNotFoundException(CONFIG_PATH);
        }

        Yaml yaml = new Yaml();
        try (InputStream in = Files.newInputStream(configPath)) {
            Map<String, Object> root = yaml.load(in);
            if (root == null) {
                throw new IOException("Empty YAML file");
            }

            for (Map.Entry<String, Object> entry : root.entrySet()) {
                String typeName = entry.getKey();
                Map<String, Object> props = (Map<String, Object>) entry.getValue();

                SpaceTypeConfig config = parseSpaceType(typeName, props);
                registry.put(typeName, config);

                // Register aliases
                if (config.aliases() != null) {
                    for (String alias : config.aliases()) {
                        aliasMap.put(normalize(alias), typeName);
                    }
                }
            }
        }
    }

    /**
     * Parse a space type from YAML map.
     */
    @SuppressWarnings("unchecked")
    private static SpaceTypeConfig parseSpaceType(String name, Map<String, Object> props) {
        String category = getString(props, "category", "UNKNOWN");
        String omniclass = getString(props, "omniclass", "13-00 00 00");
        String wallRule = getString(props, "wall_rule", "AS_REQUIRED");
        boolean isSleepingRoom = getBoolean(props, "is_sleeping_room", false);
        boolean isOpenPlan = getBoolean(props, "is_open_plan", false);
        boolean isExterior = getBoolean(props, "is_exterior", false);

        // Parse validation
        ValidationConfig validation;
        if (props.containsKey("validation")) {
            Map<String, Object> valProps = (Map<String, Object>) props.get("validation");
            validation = new ValidationConfig(
                getDouble(valProps, "min_area", 0),
                getDouble(valProps, "min_dimension", 0),
                getBoolean(valProps, "requires_window", false),
                getBoolean(valProps, "requires_egress", false)
            );
        } else {
            validation = new ValidationConfig(0, 0, false, false);
        }

        // Parse zones allowed
        List<String> zonesAllowed = null;
        if (props.containsKey("zones_allowed")) {
            zonesAllowed = (List<String>) props.get("zones_allowed");
        }

        // Parse aliases
        List<String> aliases = null;
        if (props.containsKey("aliases")) {
            aliases = (List<String>) props.get("aliases");
        }

        return new SpaceTypeConfig(
            name, category, omniclass, wallRule, validation,
            isSleepingRoom, isOpenPlan, isExterior, zonesAllowed, aliases
        );
    }

    /**
     * Load built-in defaults (if YAML not available).
     */
    private static void loadDefaults() {
        // Minimal defaults for core types
        addDefault("BEDROOM", "HABITABLE", "ENCLOSED", 6.5, 2.134, true, true, true);
        addDefault("BATHROOM", "SERVICE", "ENCLOSED", 2.5, 1.2, false, false, false);
        addDefault("KITCHEN", "HABITABLE", "ENCLOSED", 4.6, 1.8, true, false, false);
        addDefault("LIVING", "HABITABLE", "ENCLOSED", 6.5, 2.134, true, false, false);
        addDefault("CORRIDOR", "CIRCULATION", "AS_REQUIRED", 0, 0.914, false, false, false);
        addDefault("OPEN_PLAN", "HABITABLE", "PERIMETER_ONLY", 13.0, 3.0, true, false, false);
        addDefault("PORCH", "EXTERIOR", "NONE", 0, 0, false, false, false);
        addDefault("GENERIC", "UNKNOWN", "AS_REQUIRED", 0, 0, false, false, false);
    }

    private static void addDefault(String name, String category, String wallRule,
                                   double minArea, double minDim,
                                   boolean reqWindow, boolean reqEgress, boolean sleeping) {
        ValidationConfig validation = new ValidationConfig(minArea, minDim, reqWindow, reqEgress);
        SpaceTypeConfig config = new SpaceTypeConfig(
            name, category, "13-00 00 00", wallRule, validation,
            sleeping, false, false, null, null
        );
        registry.put(name, config);
    }

    private static SpaceTypeConfig createDefaultGeneric() {
        return new SpaceTypeConfig(
            "GENERIC", "UNKNOWN", "13-00 00 00", "AS_REQUIRED",
            new ValidationConfig(0, 0, false, false),
            false, false, false, null, null
        );
    }

    // Helper methods for parsing
    private static String getString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private static double getDouble(Map<String, Object> map, String key, double defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return defaultValue;
    }

    private static boolean getBoolean(Map<String, Object> map, String key, boolean defaultValue) {
        Object value = map.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return defaultValue;
    }

    private static String normalize(String name) {
        return name == null ? "" : name.toUpperCase().trim();
    }

    /**
     * Main method for testing.
     */
    public static void main(String[] args) {
        System.out.println("=== SpaceTypeRegistry Test ===\n");

        System.out.println("All registered types:");
        for (String type : getAllTypes()) {
            SpaceTypeConfig config = get(type);
            System.out.printf("  %s: %s, minArea=%.1f, wallRule=%s%n",
                type, config.category(), config.validation().minArea(), config.wallRule());
        }

        System.out.println("\nAlias tests:");
        System.out.println("  BILIK_TIDUR -> " + get("BILIK_TIDUR").name());
        System.out.println("  MR -> " + get("MR").name());
        System.out.println("  ANJUNG -> " + get("ANJUNG").name());

        System.out.println("\nUnknown type test:");
        System.out.println("  UNKNOWN_TYPE -> " + get("UNKNOWN_TYPE").name());
    }
}
