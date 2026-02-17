package com.bim.compiler.dsl;

import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Phase 29: Profile Registry with YAML loading.
 *
 * Profiles adapt the DSL for regional codes, building types, or project requirements.
 * Inspired by HL7 FHIR Profiles and STEP Application Protocols.
 *
 * Profiles are loaded from config/profiles/*.yaml at runtime.
 * Adding a new profile = add YAML file, no recompile needed.
 *
 * Predefined profiles:
 * - BASE (default IRC 2021)
 * - Malaysian_Residential (UBBL 1984)
 * - US_Residential_IRC (IRC 2021)
 * - UK_Residential (Building Regs 2010)
 * - Commercial_IBC (IBC 2021)
 */
public class ProfileRegistry {

    private static final String CONFIG_DIR = "config/profiles";
    private static final Map<String, ProfileDef> PROFILES = new HashMap<>();
    private static boolean loaded = false;

    /**
     * Profile definition.
     */
    public record ProfileDef(
        String name,
        String description,
        String extendsProfile,      // Parent profile name or null
        String validationCode,      // UBBL_1984, IRC_2021, etc.
        String localization,        // MS_MY, EN_US, EN_GB
        Set<String> includedTypes,  // Space types included
        Set<String> excludedTypes,  // Space types excluded
        Map<String, Object> defaults,
        Map<String, String> vocabulary,  // Alias -> canonical type
        List<RequiredSpace> requiredSpaces,
        Map<String, Map<String, Object>> validationOverrides
    ) {
        public double getDefaultStoreyHeight() {
            Object val = defaults.get("storey_height");
            if (val instanceof Number) return ((Number) val).doubleValue();
            return 2.8; // Default
        }

        public double getDefaultWallThickness() {
            Object val = defaults.get("wall_exterior");
            if (val instanceof Number) return ((Number) val).doubleValue() / 1000.0; // mm to m
            return 0.15; // 150mm default
        }

        public double getDefault(String key, double defaultValue) {
            Object val = defaults.get(key);
            if (val instanceof Number) return ((Number) val).doubleValue();
            return defaultValue;
        }

        public boolean allowsSpaceType(String type) {
            String upper = type.toUpperCase();
            if (!excludedTypes.isEmpty() && excludedTypes.contains(upper)) {
                return false;
            }
            if (!includedTypes.isEmpty() && !includedTypes.contains(upper)) {
                return false;
            }
            return true;
        }

        /**
         * Translate vocabulary alias to canonical type.
         */
        public String translateType(String type) {
            if (vocabulary == null) return type;
            return vocabulary.getOrDefault(type.toUpperCase(), type);
        }
    }

    /**
     * Required space for protocol compliance.
     */
    public record RequiredSpace(String type, int minCount) {}

    /**
     * Get profile by name.
     */
    public static ProfileDef getProfile(String name) {
        ensureLoaded();
        return PROFILES.get(name);
    }

    /**
     * Check if profile exists.
     */
    public static boolean hasProfile(String name) {
        ensureLoaded();
        return PROFILES.containsKey(name);
    }

    /**
     * Get all profile names.
     */
    public static Set<String> getProfileNames() {
        ensureLoaded();
        return PROFILES.keySet();
    }

    /**
     * Reload profiles from YAML files.
     */
    public static void reload() {
        loaded = false;
        PROFILES.clear();
        ensureLoaded();
    }

    /**
     * Validate building against profile.
     */
    public static List<String> validateAgainstProfile(BuildingDefinition building, String profileName) {
        List<String> errors = new ArrayList<>();

        ProfileDef profile = getProfile(profileName);
        if (profile == null) {
            errors.add("Unknown profile: " + profileName);
            return errors;
        }

        // Check all space types are allowed
        for (var storey : building.storeys()) {
            for (var room : storey.rooms()) {
                if (!profile.allowsSpaceType(room.type())) {
                    errors.add(String.format("Space type '%s' not allowed in profile '%s'",
                        room.type(), profileName));
                }
            }
        }

        return errors;
    }

    private static synchronized void ensureLoaded() {
        if (loaded) return;

        try {
            loadFromYaml();
            loaded = true;
            System.out.printf("[ProfileRegistry] Loaded %d profiles from %s%n",
                PROFILES.size(), CONFIG_DIR);
        } catch (Exception e) {
            System.err.println("[ProfileRegistry] Failed to load from YAML: " + e.getMessage());
            loadDefaults();
            loaded = true;
        }
    }

    @SuppressWarnings("unchecked")
    private static void loadFromYaml() throws IOException {
        Path configDir = Paths.get(CONFIG_DIR);
        if (!Files.exists(configDir)) {
            throw new FileNotFoundException(CONFIG_DIR);
        }

        Yaml yaml = new Yaml();

        // Load all .yaml files in the profiles directory
        try (var stream = Files.list(configDir)) {
            stream.filter(p -> p.toString().endsWith(".yaml"))
                  .forEach(path -> {
                      try (InputStream in = Files.newInputStream(path)) {
                          Map<String, Object> root = yaml.load(in);
                          if (root != null && root.containsKey("name")) {
                              ProfileDef profile = parseProfile(root);
                              PROFILES.put(profile.name(), profile);
                          }
                      } catch (Exception e) {
                          System.err.println("[ProfileRegistry] Error loading " + path + ": " + e.getMessage());
                      }
                  });
        }
    }

    @SuppressWarnings("unchecked")
    private static ProfileDef parseProfile(Map<String, Object> props) {
        String name = getString(props, "name", "UNKNOWN");
        String description = getString(props, "description", "");
        String extendsProfile = getString(props, "extends", null);
        String validationCode = getString(props, "validation_code", "IRC_2021");
        String localization = getString(props, "localization", "EN_US");

        // Defaults
        Map<String, Object> defaults = new HashMap<>();
        if (props.containsKey("defaults")) {
            defaults.putAll((Map<String, Object>) props.get("defaults"));
        }

        // Vocabulary
        Map<String, String> vocabulary = new HashMap<>();
        if (props.containsKey("vocabulary")) {
            Map<String, Object> vocabRaw = (Map<String, Object>) props.get("vocabulary");
            for (var entry : vocabRaw.entrySet()) {
                vocabulary.put(entry.getKey().toUpperCase(), entry.getValue().toString().toUpperCase());
            }
        }

        // Required spaces
        List<RequiredSpace> requiredSpaces = new ArrayList<>();
        if (props.containsKey("required_spaces")) {
            List<Map<String, Object>> spaceList = (List<Map<String, Object>>) props.get("required_spaces");
            for (Map<String, Object> space : spaceList) {
                String type = getString(space, "type", "UNKNOWN");
                int minCount = getInt(space, "min_count", 1);
                requiredSpaces.add(new RequiredSpace(type, minCount));
            }
        }

        // Validation overrides
        Map<String, Map<String, Object>> validationOverrides = new HashMap<>();
        if (props.containsKey("validation_overrides")) {
            validationOverrides.putAll((Map<String, Map<String, Object>>) props.get("validation_overrides"));
        }

        // Included/excluded types (empty = all allowed)
        Set<String> includedTypes = new HashSet<>();
        Set<String> excludedTypes = new HashSet<>();

        return new ProfileDef(
            name,
            description,
            extendsProfile,
            validationCode,
            localization,
            includedTypes,
            excludedTypes,
            defaults,
            vocabulary,
            requiredSpaces,
            validationOverrides
        );
    }

    /**
     * Load default profiles if YAML loading fails.
     */
    private static void loadDefaults() {
        // Malaysian Residential (UBBL 1984)
        PROFILES.put("Malaysian_Residential", new ProfileDef(
            "Malaysian_Residential",
            "Single/double storey residential for Malaysia",
            null,
            "UBBL_1984",
            "MS_MY",
            Set.of("ANJUNG", "RUANG_TAMU", "BILIK", "BILIK_UTAMA", "DAPUR",
                   "BILIK_MANDI", "TANDAS", "RUANG_BASUH", "BEDROOM", "BATHROOM",
                   "KITCHEN", "LIVING", "PORCH", "OPEN_PLAN"),
            Set.of(),
            Map.of(
                "storey_height", 2.8,
                "wall_exterior", 150,
                "min_ceiling", 2.5,
                "min_bedroom_area", 9.3,
                "min_bathroom_width", 1.2
            ),
            Map.of(
                "BILIK_UTAMA", "MASTER_BEDROOM",
                "BILIK_TIDUR", "BEDROOM",
                "RUANG_TAMU", "LIVING",
                "DAPUR", "KITCHEN",
                "ANJUNG", "PORCH"
            ),
            List.of(
                new RequiredSpace("LIVING", 1),
                new RequiredSpace("KITCHEN", 1),
                new RequiredSpace("BATHROOM", 1)
            ),
            Map.of()
        ));

        // US Residential (IRC 2021)
        PROFILES.put("US_Residential_IRC", new ProfileDef(
            "US_Residential_IRC",
            "Residential per IRC 2021",
            null,
            "IRC_2021",
            "EN_US",
            Set.of("BEDROOM", "BATHROOM", "KITCHEN", "LIVING", "DINING",
                   "GARAGE", "BASEMENT", "PORCH", "OPEN_PLAN", "CORRIDOR"),
            Set.of(),
            Map.of(
                "storey_height", 2.74,
                "wall_exterior", 140,
                "min_ceiling", 2.13,
                "min_bedroom_area", 6.5,
                "min_egress_window", 0.52
            ),
            Map.of(),
            List.of(),
            Map.of()
        ));

        // BASE profile
        PROFILES.put("BASE", new ProfileDef(
            "BASE",
            "Base profile with IRC 2021 defaults",
            null,
            "IRC_2021",
            "EN_US",
            Set.of(),
            Set.of(),
            Map.of(
                "storey_height", 2.4,
                "wall_exterior", 150,
                "door_width", 813,
                "door_height", 2032
            ),
            Map.of(),
            List.of(),
            Map.of()
        ));
    }

    private static String getString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private static int getInt(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }

    /**
     * Test main method.
     */
    public static void main(String[] args) {
        System.out.println("=== ProfileRegistry Test ===\n");

        System.out.println("All profiles:");
        for (String name : getProfileNames()) {
            ProfileDef p = getProfile(name);
            System.out.printf("  %s: %s%n", name, p.description());
            System.out.printf("    Code: %s, Locale: %s%n", p.validationCode(), p.localization());
            System.out.printf("    Storey height: %.2fm%n", p.getDefaultStoreyHeight());

            if (!p.vocabulary().isEmpty()) {
                System.out.println("    Vocabulary:");
                for (var entry : p.vocabulary().entrySet()) {
                    System.out.printf("      %s -> %s%n", entry.getKey(), entry.getValue());
                }
            }
        }

        // Test vocabulary translation
        System.out.println("\nVocabulary translation test:");
        ProfileDef my = getProfile("Malaysian_Residential");
        if (my != null) {
            System.out.printf("  BILIK_UTAMA -> %s%n", my.translateType("BILIK_UTAMA"));
            System.out.printf("  DAPUR -> %s%n", my.translateType("DAPUR"));
            System.out.printf("  BEDROOM -> %s%n", my.translateType("BEDROOM"));
        }
    }
}
