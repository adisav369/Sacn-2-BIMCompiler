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
        MEPConfig mep,
        StructuralConfig structural,  // Phase 50B.1
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
     * MEP (Mechanical, Electrical, Plumbing) requirements for a space type.
     * Phase 32: Implicit MEP derivation from space type.
     */
    public record MEPConfig(
        PlumbingConfig plumbing,
        ElectricalConfig electrical,
        HVACConfig hvac
    ) {
        public static MEPConfig empty() {
            return new MEPConfig(
                PlumbingConfig.empty(),
                ElectricalConfig.empty(),
                HVACConfig.empty()
            );
        }
    }

    /**
     * Plumbing requirements.
     */
    public record PlumbingConfig(
        List<String> fixtures,      // ["toilet", "sink", "floor_trap"]
        boolean requiresStack,       // Needs vertical plumbing stack
        boolean requiresVent,        // Needs vent pipe
        String waterSupply           // "hot_cold", "cold_only", "none"
    ) {
        public static PlumbingConfig empty() {
            return new PlumbingConfig(List.of(), false, false, "none");
        }
    }

    /**
     * Electrical requirements.
     */
    public record ElectricalConfig(
        int lightPoints,             // Number of light fixtures
        int powerPoints,             // Number of power outlets
        int switchPoints,            // Number of switches
        boolean requiresDedicated,   // Needs dedicated circuit (aircon, water heater)
        String circuitType           // "general", "high_load", "wet_area"
    ) {
        public static ElectricalConfig empty() {
            return new ElectricalConfig(0, 0, 0, false, "general");
        }
    }

    /**
     * HVAC requirements.
     */
    public record HVACConfig(
        boolean requiresExhaust,     // Needs exhaust fan
        int exhaustCFM,              // Exhaust capacity (0 if not required)
        boolean allowsAircon,        // Can have air conditioning
        String ventilationType       // "natural", "mechanical", "none"
    ) {
        public static HVACConfig empty() {
            return new HVACConfig(false, 0, false, "natural");
        }
    }

    /**
     * Phase 50B.1: Structural requirements for large-span spaces.
     */
    public record StructuralConfig(
        boolean structuralGrid,      // Needs intermediate beams/columns
        double beamMaxSpan           // Max span before intermediate beams (meters)
    ) {
        public static StructuralConfig empty() {
            return new StructuralConfig(false, 8.0);
        }
    }

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

        // Parse MEP configuration (Phase 32)
        MEPConfig mep = parseMEPConfig(props);

        // Phase 50B.1: Parse structural configuration
        StructuralConfig structural = parseStructuralConfig(props);

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
            name, category, omniclass, wallRule, validation, mep, structural,
            isSleepingRoom, isOpenPlan, isExterior, zonesAllowed, aliases
        );
    }

    /**
     * Parse MEP configuration from YAML map.
     */
    @SuppressWarnings("unchecked")
    private static MEPConfig parseMEPConfig(Map<String, Object> props) {
        if (!props.containsKey("mep")) {
            return MEPConfig.empty();
        }

        Map<String, Object> mepProps = (Map<String, Object>) props.get("mep");

        // Parse plumbing
        PlumbingConfig plumbing = PlumbingConfig.empty();
        if (mepProps.containsKey("plumbing")) {
            Map<String, Object> pProps = (Map<String, Object>) mepProps.get("plumbing");
            List<String> fixtures = pProps.containsKey("fixtures")
                ? (List<String>) pProps.get("fixtures")
                : List.of();
            plumbing = new PlumbingConfig(
                fixtures,
                getBoolean(pProps, "requires_stack", false),
                getBoolean(pProps, "requires_vent", false),
                getString(pProps, "water_supply", "none")
            );
        }

        // Parse electrical
        ElectricalConfig electrical = ElectricalConfig.empty();
        if (mepProps.containsKey("electrical")) {
            Map<String, Object> eProps = (Map<String, Object>) mepProps.get("electrical");
            electrical = new ElectricalConfig(
                getInt(eProps, "light_points", 0),
                getInt(eProps, "power_points", 0),
                getInt(eProps, "switch_points", 0),
                getBoolean(eProps, "requires_dedicated", false),
                getString(eProps, "circuit_type", "general")
            );
        }

        // Parse HVAC
        HVACConfig hvac = HVACConfig.empty();
        if (mepProps.containsKey("hvac")) {
            Map<String, Object> hProps = (Map<String, Object>) mepProps.get("hvac");
            hvac = new HVACConfig(
                getBoolean(hProps, "requires_exhaust", false),
                getInt(hProps, "exhaust_cfm", 0),
                getBoolean(hProps, "allows_aircon", false),
                getString(hProps, "ventilation_type", "natural")
            );
        }

        return new MEPConfig(plumbing, electrical, hvac);
    }

    /**
     * Phase 50B.1: Parse structural configuration from YAML map.
     */
    @SuppressWarnings("unchecked")
    private static StructuralConfig parseStructuralConfig(Map<String, Object> props) {
        if (!props.containsKey("structural")) {
            return StructuralConfig.empty();
        }

        Map<String, Object> structProps = (Map<String, Object>) props.get("structural");
        return new StructuralConfig(
            getBoolean(structProps, "structural_grid", false),
            getDouble(structProps, "beam_max_span", 8.0)
        );
    }

    /**
     * Load built-in defaults (if YAML not available).
     */
    private static void loadDefaults() {
        // Minimal defaults for core types with MEP
        addDefault("BEDROOM", "HABITABLE", "ENCLOSED", 6.5, 2.134, true, true, true,
            createBedroomMEP());
        addDefault("BATHROOM", "SERVICE", "ENCLOSED", 2.5, 1.2, false, false, false,
            createBathroomMEP());
        addDefault("KITCHEN", "HABITABLE", "ENCLOSED", 4.6, 1.8, true, false, false,
            createKitchenMEP());
        addDefault("LIVING", "HABITABLE", "ENCLOSED", 6.5, 2.134, true, false, false,
            createLivingMEP());
        addDefault("CORRIDOR", "CIRCULATION", "AS_REQUIRED", 0, 0.914, false, false, false,
            createCorridorMEP());
        addDefault("OPEN_PLAN", "HABITABLE", "PERIMETER_ONLY", 13.0, 3.0, true, false, false,
            createOpenPlanMEP());
        addDefault("PORCH", "EXTERIOR", "NONE", 0, 0, false, false, false,
            MEPConfig.empty());
        addDefault("GENERIC", "UNKNOWN", "AS_REQUIRED", 0, 0, false, false, false,
            MEPConfig.empty());
    }

    private static void addDefault(String name, String category, String wallRule,
                                   double minArea, double minDim,
                                   boolean reqWindow, boolean reqEgress, boolean sleeping,
                                   MEPConfig mep) {
        ValidationConfig validation = new ValidationConfig(minArea, minDim, reqWindow, reqEgress);
        SpaceTypeConfig config = new SpaceTypeConfig(
            name, category, "13-00 00 00", wallRule, validation, mep, StructuralConfig.empty(),
            sleeping, false, false, null, null
        );
        registry.put(name, config);
    }

    private static SpaceTypeConfig createDefaultGeneric() {
        return new SpaceTypeConfig(
            "GENERIC", "UNKNOWN", "13-00 00 00", "AS_REQUIRED",
            new ValidationConfig(0, 0, false, false),
            MEPConfig.empty(), StructuralConfig.empty(),
            false, false, false, null, null
        );
    }

    // ===== Default MEP configurations for built-in fallback =====

    private static MEPConfig createBedroomMEP() {
        return new MEPConfig(
            PlumbingConfig.empty(),
            new ElectricalConfig(1, 2, 1, false, "general"),
            new HVACConfig(false, 0, true, "natural")
        );
    }

    private static MEPConfig createBathroomMEP() {
        return new MEPConfig(
            new PlumbingConfig(
                List.of("toilet", "sink", "floor_trap"),
                true, true, "hot_cold"
            ),
            new ElectricalConfig(1, 1, 1, false, "wet_area"),
            new HVACConfig(true, 50, false, "mechanical")
        );
    }

    private static MEPConfig createKitchenMEP() {
        return new MEPConfig(
            new PlumbingConfig(
                List.of("sink", "floor_trap"),
                true, true, "hot_cold"
            ),
            new ElectricalConfig(1, 4, 1, true, "high_load"),
            new HVACConfig(true, 100, false, "mechanical")
        );
    }

    private static MEPConfig createLivingMEP() {
        return new MEPConfig(
            PlumbingConfig.empty(),
            new ElectricalConfig(1, 3, 1, false, "general"),
            new HVACConfig(false, 0, true, "natural")
        );
    }

    private static MEPConfig createCorridorMEP() {
        return new MEPConfig(
            PlumbingConfig.empty(),
            new ElectricalConfig(1, 0, 1, false, "general"),
            new HVACConfig(false, 0, false, "natural")
        );
    }

    private static MEPConfig createOpenPlanMEP() {
        return new MEPConfig(
            new PlumbingConfig(
                List.of("sink"),
                true, false, "hot_cold"
            ),
            new ElectricalConfig(3, 6, 2, true, "high_load"),
            new HVACConfig(true, 100, true, "mechanical")
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

    private static int getInt(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
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

        // Phase 32: MEP configuration test
        System.out.println("\n=== MEP Configuration (Phase 32) ===\n");
        String[] mepTypes = {"BEDROOM", "BATHROOM", "KITCHEN", "LIVING", "OPEN_PLAN"};
        for (String type : mepTypes) {
            SpaceTypeConfig config = get(type);
            MEPConfig mep = config.mep();
            System.out.printf("%s:%n", type);
            System.out.printf("  Plumbing: fixtures=%s, stack=%s, supply=%s%n",
                mep.plumbing().fixtures(),
                mep.plumbing().requiresStack(),
                mep.plumbing().waterSupply());
            System.out.printf("  Electrical: lights=%d, power=%d, dedicated=%s%n",
                mep.electrical().lightPoints(),
                mep.electrical().powerPoints(),
                mep.electrical().requiresDedicated());
            System.out.printf("  HVAC: exhaust=%s (%d CFM), aircon=%s%n",
                mep.hvac().requiresExhaust(),
                mep.hvac().exhaustCFM(),
                mep.hvac().allowsAircon());
            System.out.println();
        }
    }
}
