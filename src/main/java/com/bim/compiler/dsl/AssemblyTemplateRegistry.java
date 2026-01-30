package com.bim.compiler.dsl;

import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Phase 29: Runtime-loadable assembly templates.
 *
 * Loads assembly structures from config/assemblies.yaml.
 * Enables adding new assembly types without recompiling.
 *
 * Usage:
 *   AssemblyTemplate template = AssemblyTemplateRegistry.get("WALL_PANEL");
 *   for (ComponentDef comp : template.components()) { ... }
 */
public class AssemblyTemplateRegistry {

    private static final String CONFIG_PATH = "config/assemblies.yaml";
    private static final Map<String, AssemblyTemplate> registry = new HashMap<>();
    private static boolean loaded = false;

    /**
     * Assembly template definition.
     */
    public record AssemblyTemplate(
        String name,
        String description,
        String ifcClass,
        String standard,
        String fireRating,
        List<ComponentGroup> componentGroups
    ) {
        /**
         * Get all components flattened.
         */
        public List<ComponentDef> allComponents() {
            List<ComponentDef> all = new ArrayList<>();
            for (ComponentGroup group : componentGroups) {
                all.addAll(group.components());
            }
            return all;
        }

        /**
         * Get components by group (FRAME, CLADDING, HARDWARE, etc.)
         */
        public List<ComponentDef> getComponents(String groupName) {
            for (ComponentGroup group : componentGroups) {
                if (group.name().equalsIgnoreCase(groupName)) {
                    return group.components();
                }
            }
            return List.of();
        }
    }

    /**
     * Component group (e.g., FRAME, CLADDING, HARDWARE).
     */
    public record ComponentGroup(
        String name,
        List<ComponentDef> components
    ) {}

    /**
     * Individual component definition.
     */
    public record ComponentDef(
        String role,
        String position,
        String profile,
        String material,
        String source,
        String spacing,
        int count,
        boolean optional,
        boolean bomOnly,
        boolean fireRated
    ) {}

    /**
     * Get assembly template by name.
     */
    public static AssemblyTemplate get(String templateName) {
        ensureLoaded();
        AssemblyTemplate template = registry.get(templateName.toUpperCase());
        if (template == null) {
            System.err.println("[AssemblyTemplateRegistry] Unknown template: " + templateName);
        }
        return template;
    }

    /**
     * Check if template exists.
     */
    public static boolean exists(String templateName) {
        ensureLoaded();
        return registry.containsKey(templateName.toUpperCase());
    }

    /**
     * Get all template names.
     */
    public static Set<String> getAllTemplates() {
        ensureLoaded();
        return Collections.unmodifiableSet(registry.keySet());
    }

    /**
     * Reload configuration from file.
     */
    public static void reload() {
        loaded = false;
        registry.clear();
        ensureLoaded();
    }

    private static synchronized void ensureLoaded() {
        if (loaded) return;

        try {
            loadFromYaml();
            loaded = true;
            System.out.printf("[AssemblyTemplateRegistry] Loaded %d assembly templates from %s%n",
                registry.size(), CONFIG_PATH);
        } catch (Exception e) {
            System.err.println("[AssemblyTemplateRegistry] Failed to load: " + e.getMessage());
            loadDefaults();
            loaded = true;
        }
    }

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
                String templateName = entry.getKey();
                Map<String, Object> props = (Map<String, Object>) entry.getValue();

                AssemblyTemplate template = parseTemplate(templateName, props);
                registry.put(templateName.toUpperCase(), template);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static AssemblyTemplate parseTemplate(String name, Map<String, Object> props) {
        String description = getString(props, "description", "");
        String ifcClass = getString(props, "ifc_class", "IfcElement");
        String standard = getString(props, "standard", null);
        String fireRating = getString(props, "fire_rating", null);

        List<ComponentGroup> groups = new ArrayList<>();

        if (props.containsKey("components")) {
            Map<String, Object> componentGroups = (Map<String, Object>) props.get("components");
            for (Map.Entry<String, Object> groupEntry : componentGroups.entrySet()) {
                String groupName = groupEntry.getKey();
                List<Map<String, Object>> componentList = (List<Map<String, Object>>) groupEntry.getValue();

                List<ComponentDef> components = new ArrayList<>();
                for (Map<String, Object> compProps : componentList) {
                    components.add(parseComponent(compProps));
                }

                groups.add(new ComponentGroup(groupName, components));
            }
        }

        return new AssemblyTemplate(name, description, ifcClass, standard, fireRating, groups);
    }

    private static ComponentDef parseComponent(Map<String, Object> props) {
        return new ComponentDef(
            getString(props, "role", "UNKNOWN"),
            getString(props, "position", null),
            getString(props, "profile", null),
            getString(props, "material", null),
            getString(props, "source", null),
            getString(props, "spacing", null),
            getInt(props, "count", 1),
            getBoolean(props, "optional", false),
            getBoolean(props, "bom_only", false),
            getBoolean(props, "fire_rated", false)
        );
    }

    private static void loadDefaults() {
        // Minimal WALL_PANEL default
        List<ComponentDef> frameComponents = List.of(
            new ComponentDef("RAIL_BOTTOM", "BOTTOM", "RHS_150x100", null, null, null, 1, false, false, false),
            new ComponentDef("RAIL_TOP", "TOP", "RHS_150x100", null, null, null, 1, false, false, false),
            new ComponentDef("STUD_L", "LEFT_VERTICAL", "RHS_150x100", null, null, null, 1, false, false, false),
            new ComponentDef("STUD_R", "RIGHT_VERTICAL", "RHS_150x100", null, null, null, 1, false, false, false)
        );
        List<ComponentDef> claddingComponents = List.of(
            new ComponentDef("PANEL", "EXTERIOR", null, "Metal_Deck", null, null, 1, false, false, false)
        );
        List<ComponentGroup> groups = List.of(
            new ComponentGroup("FRAME", frameComponents),
            new ComponentGroup("CLADDING", claddingComponents)
        );
        registry.put("WALL_PANEL", new AssemblyTemplate(
            "WALL_PANEL", "Default wall assembly", "IfcWallStandardCase", null, null, groups
        ));

        // Minimal DOOR_ASSEMBLY default
        List<ComponentDef> leafComponents = List.of(
            new ComponentDef("DOOR", null, null, null, "LIBRARY", null, 1, false, false, false)
        );
        List<ComponentDef> hwComponents = List.of(
            new ComponentDef("HINGE_100MM", null, null, null, null, null, 3, true, true, false),
            new ComponentDef("HANDLE_SET", null, null, null, null, null, 1, true, true, false)
        );
        registry.put("DOOR_ASSEMBLY", new AssemblyTemplate(
            "DOOR_ASSEMBLY", "Default door assembly", "IfcDoor", null, null,
            List.of(new ComponentGroup("LEAF", leafComponents), new ComponentGroup("HARDWARE", hwComponents))
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

    private static boolean getBoolean(Map<String, Object> map, String key, boolean defaultValue) {
        Object value = map.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return defaultValue;
    }

    /**
     * Test main method.
     */
    public static void main(String[] args) {
        System.out.println("=== AssemblyTemplateRegistry Test ===\n");

        System.out.println("All templates:");
        for (String name : getAllTemplates()) {
            AssemblyTemplate t = get(name);
            System.out.printf("  %s: %s, groups=%d%n",
                name, t.ifcClass(), t.componentGroups().size());
        }

        System.out.println("\nWALL_PANEL components:");
        AssemblyTemplate wallPanel = get("WALL_PANEL");
        if (wallPanel != null) {
            for (ComponentGroup group : wallPanel.componentGroups()) {
                System.out.printf("  %s:%n", group.name());
                for (ComponentDef comp : group.components()) {
                    System.out.printf("    - %s: pos=%s, profile=%s%n",
                        comp.role(), comp.position(), comp.profile());
                }
            }
        }

        System.out.println("\nDOOR_ASSEMBLY hardware:");
        AssemblyTemplate doorAsm = get("DOOR_ASSEMBLY");
        if (doorAsm != null) {
            for (ComponentDef comp : doorAsm.getComponents("HARDWARE")) {
                System.out.printf("  - %s: count=%d, optional=%s, bom_only=%s%n",
                    comp.role(), comp.count(), comp.optional(), comp.bomOnly());
            }
        }
    }
}
