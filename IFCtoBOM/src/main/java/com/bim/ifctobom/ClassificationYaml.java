package com.bim.ifctobom;

import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Classification YAML POJO — human/AI-readable spatial classification
 * for a single building type.
 *
 * <p>Schema version 1. Replaces hardcoded Python data with a declarative DSL.
 * See {@code classify_sh.yaml} for SH reference.
 */
public class ClassificationYaml {

    private int schemaVersion;
    private BuildingConfig building;

    // ── Records ──────────────────────────────────────────────────────────────

    public record StoreyConfig(String code, String bomCategory, String role, int seq) {}

    public record SpaceConfig(String name, String templateBom, String role, int seq,
                              int aabbW, int aabbD, int aabbH,
                              double[] originM) {
        /** True if this space has a valid scope box for containment testing. */
        public boolean hasScopeBox() {
            return originM != null && originM.length == 3
                    && (aabbW > 0 || aabbD > 0 || aabbH > 0);
        }
    }

    public record FloorRoomConfig(String bomId, String bomCategory, List<SpaceConfig> spaces) {}

    public record StaticChildConfig(String childProductId, String role, int seq, double dz) {}

    /**
     * Mirror plane definition — axis-agnostic.
     * @param axis      partition axis: "X", "Y", or "Z"
     * @param position  world-coord position on that axis (party wall center)
     * @param rotation  B-side rotation in radians (pi = 180°)
     */
    public record MirrorConfig(String axis, double position, double rotation) {}

    public record CompositionConfig(String type, String pairBomId, String halfUnitBomId,
                                    MirrorConfig mirror) {}

    public record BuildingConfig(
            String buildingType, String prefix, String buildingBomId,
            String docSubType, String docBaseType, String name,
            Map<String, StoreyConfig> storeys,
            Map<String, FloorRoomConfig> floorRooms,
            List<StaticChildConfig> staticChildren,
            CompositionConfig composition
    ) {}

    // ── Accessors ────────────────────────────────────────────────────────────

    public int getSchemaVersion() { return schemaVersion; }
    public BuildingConfig getBuilding() { return building; }

    // ── Loader ───────────────────────────────────────────────────────────────

    /**
     * Load classification YAML from a file path.
     *
     * @throws IOException if file not found or parse error
     */
    @SuppressWarnings("unchecked")
    public static ClassificationYaml load(Path path) throws IOException {
        if (!Files.exists(path)) {
            throw new FileNotFoundException("Classification YAML not found: " + path);
        }

        Yaml yaml = new Yaml();
        Map<String, Object> root;
        try (InputStream in = Files.newInputStream(path)) {
            root = yaml.load(in);
        }
        if (root == null) {
            throw new IOException("Empty YAML file: " + path);
        }

        ClassificationYaml result = new ClassificationYaml();
        result.schemaVersion = getInt(root, "schema_version", 1);

        Map<String, Object> bldg = (Map<String, Object>) root.get("building");
        if (bldg == null) {
            throw new IOException("Missing 'building' section in " + path);
        }

        // Parse storeys
        Map<String, StoreyConfig> storeys = new LinkedHashMap<>();
        Map<String, Object> storeyMap = (Map<String, Object>) bldg.get("storeys");
        if (storeyMap != null) {
            for (Map.Entry<String, Object> e : storeyMap.entrySet()) {
                Map<String, Object> s = (Map<String, Object>) e.getValue();
                storeys.put(e.getKey(), new StoreyConfig(
                        getString(s, "code"),
                        getString(s, "bom_category"),
                        getString(s, "role"),
                        getInt(s, "seq", 0)
                ));
            }
        }

        // Parse floor_rooms
        Map<String, FloorRoomConfig> floorRooms = new LinkedHashMap<>();
        Map<String, Object> frMap = (Map<String, Object>) bldg.get("floor_rooms");
        if (frMap != null) {
            for (Map.Entry<String, Object> e : frMap.entrySet()) {
                Map<String, Object> fr = (Map<String, Object>) e.getValue();
                List<SpaceConfig> spaces = new ArrayList<>();
                List<Map<String, Object>> spaceList = (List<Map<String, Object>>) fr.get("spaces");
                if (spaceList != null) {
                    for (Map<String, Object> sp : spaceList) {
                        List<Number> aabb = (List<Number>) sp.get("aabb_mm");
                        int aw = aabb != null && aabb.size() > 0 ? aabb.get(0).intValue() : 0;
                        int ad = aabb != null && aabb.size() > 1 ? aabb.get(1).intValue() : 0;
                        int ah = aabb != null && aabb.size() > 2 ? aabb.get(2).intValue() : 0;
                        List<Number> origin = (List<Number>) sp.get("origin_m");
                        double[] originM = null;
                        if (origin != null && origin.size() >= 3) {
                            originM = new double[]{
                                    origin.get(0).doubleValue(),
                                    origin.get(1).doubleValue(),
                                    origin.get(2).doubleValue()
                            };
                        }
                        spaces.add(new SpaceConfig(
                                getString(sp, "name"),
                                getString(sp, "template_bom"),
                                getString(sp, "role"),
                                getInt(sp, "seq", 0),
                                aw, ad, ah,
                                originM
                        ));
                    }
                }
                floorRooms.put(e.getKey(), new FloorRoomConfig(
                        getString(fr, "bom_id"),
                        getString(fr, "bom_category"),
                        spaces
                ));
            }
        }

        // Parse static_children
        List<StaticChildConfig> staticChildren = new ArrayList<>();
        List<Map<String, Object>> scList = (List<Map<String, Object>>) bldg.get("static_children");
        if (scList != null) {
            for (Map<String, Object> sc : scList) {
                staticChildren.add(new StaticChildConfig(
                        getString(sc, "child_product_id"),
                        getString(sc, "role"),
                        getInt(sc, "seq", 0),
                        getDouble(sc, "dz", 0.0)
                ));
            }
        }

        // Parse composition (optional — null for simple buildings like SH)
        CompositionConfig composition = null;
        Map<String, Object> compMap = (Map<String, Object>) bldg.get("composition");
        if (compMap != null) {
            MirrorConfig mirror = null;
            Map<String, Object> mirrorMap = (Map<String, Object>) compMap.get("mirror");
            if (mirrorMap != null) {
                mirror = new MirrorConfig(
                        getString(mirrorMap, "axis"),
                        getDouble(mirrorMap, "position", 0.0),
                        getDouble(mirrorMap, "rotation", Math.PI)
                );
            }
            composition = new CompositionConfig(
                    getString(compMap, "type"),
                    getString(compMap, "pair_bom_id"),
                    getString(compMap, "half_unit_bom_id"),
                    mirror
            );
        }

        // GUARD: Schema version 1 parses storeys, floor_rooms,
        // static_children, and composition. The 'disciplines:' section
        // (present in classify_te.yaml for 8 disciplines) is NOT parsed —
        // it requires schema_version 2 with a DisciplineConfig record and
        // a DisciplineBomBuilder. Until then, disciplines data is silently
        // ignored and TE gets only storey-level structural BOMs.
        //
        // Anti-drift: If YAML declares schema_version >= 2, the parser MUST
        // support it or fail. Never silently downgrade to v1 parsing.
        if (result.schemaVersion >= 2) {
            throw new IOException("YAML declares schema_version " + result.schemaVersion
                    + " but ClassificationYaml only supports v1. "
                    + "Implement DisciplineConfig + DisciplineBomBuilder before using v2.");
        }
        if (bldg.containsKey("disciplines")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> disc = (Map<String, Object>) bldg.get("disciplines");
            int discCount = disc != null ? disc.size() : 0;
            System.out.printf("[GUARD] 'disciplines' section found (%d disciplines) "
                    + "but schema_version %d does not process it — requires v2. "
                    + "TE will get storey-level structural BOMs only, not "
                    + "discipline-stratified BOMs. This is expected until "
                    + "DisciplineBomBuilder is implemented.%n",
                    discCount, result.schemaVersion);
        }

        result.building = new BuildingConfig(
                getString(bldg, "building_type"),
                getString(bldg, "prefix"),
                getString(bldg, "building_bom_id"),
                getString(bldg, "doc_sub_type"),
                getString(bldg, "doc_base_type"),
                getString(bldg, "name"),
                storeys, floorRooms, staticChildren, composition
        );

        return result;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static String getString(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    private static int getInt(Map<String, Object> map, String key, int def) {
        Object v = map.get(key);
        if (v instanceof Number) return ((Number) v).intValue();
        return def;
    }

    private static double getDouble(Map<String, Object> map, String key, double def) {
        Object v = map.get(key);
        if (v instanceof Number) return ((Number) v).doubleValue();
        return def;
    }
}
