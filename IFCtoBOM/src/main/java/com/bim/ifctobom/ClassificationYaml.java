package com.bim.ifctobom;

import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Classification YAML POJO — human/AI-readable spatial classification
 * for a single building type.
 *
 * <p>Schema version 1 (RE) / 2 (CO/IN). Replaces hardcoded Python data with a declarative DSL.
 * v2 adds {@code disciplines:} section for CO buildings (DisciplineBomBuilder).
 * The {@code storeys:} key accepts {@code segments:} as alias for infrastructure IFCs
 * (IfcRoad, IfcBridge, IfcRailway) where segments are facility parts, not storeys.
 * See {@code classify_sh.yaml} (v1), {@code classify_te.yaml} (v2),
 * and {@code docs/InfrastructureAnalysis.md} §4.2 for the mapping.
 */
public class ClassificationYaml {

    private int schemaVersion;
    private BuildingConfig building;

    // ── Records ──────────────────────────────────────────────────────────────

    public record StoreyConfig(String code, String productCategory, String role, int seq) {}

    public record SpaceConfig(String name, String templateBom, String role, int seq,
                              int aabbW, int aabbD, int aabbH,
                              double[] originM, String ifcSpace) {
        /** True if this space has a valid scope box for containment testing. */
        public boolean hasScopeBox() {
            return originM != null && originM.length == 3
                    && (aabbW > 0 || aabbD > 0 || aabbH > 0);
        }

        /** True if this space uses IFC spatial containment instead of scope box. */
        public boolean hasIfcSpace() {
            return ifcSpace != null && !ifcSpace.isEmpty();
        }
    }

    public record FloorRoomConfig(String bomId, String productCategory, List<SpaceConfig> spaces) {}

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

    /** Discipline classification for CO (schema v2). IFC classes are metadata only. */
    public record DisciplineConfig(String code, List<String> ifcClasses) {}

    public record BuildingConfig(
            String buildingType, String prefix, String buildingBomId,
            String docSubType, String productCategory, String name,
            String provenance,
            Map<String, StoreyConfig> storeys,
            Map<String, FloorRoomConfig> floorRooms,
            List<StaticChildConfig> staticChildren,
            CompositionConfig composition,
            Map<String, DisciplineConfig> disciplines,
            String dslFile,
            int geometryFailThreshold,
            String jurisdiction,
            List<String> mepDisciplines
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

        // Parse storeys (or segments — alias for infrastructure IFCs)
        // See docs/InfrastructureAnalysis.md §3.1 G5, WorkOrderGuide.md §storeys
        Map<String, StoreyConfig> storeys = new LinkedHashMap<>();
        Map<String, Object> storeyMap = (Map<String, Object>) bldg.get("storeys");
        if (storeyMap == null) {
            storeyMap = (Map<String, Object>) bldg.get("segments");
        }
        if (storeyMap != null) {
            for (Map.Entry<String, Object> e : storeyMap.entrySet()) {
                Map<String, Object> s = (Map<String, Object>) e.getValue();
                storeys.put(e.getKey(), new StoreyConfig(
                        getString(s, "code"),
                        getString(s, "product_category"),
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
                                originM,
                                getString(sp, "ifc_space")
                        ));
                    }
                }
                floorRooms.put(e.getKey(), new FloorRoomConfig(
                        getString(fr, "bom_id"),
                        getString(fr, "product_category"),
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

        // Parse disciplines (schema v2 — CO buildings)
        // v1: disciplines section ignored (SH/DX use StructuralBomBuilder)
        // v2: disciplines parsed → DisciplineBomBuilder creates hierarchy
        Map<String, DisciplineConfig> disciplines = null;
        if (result.schemaVersion >= 2 && bldg.containsKey("disciplines")) {
            disciplines = new LinkedHashMap<>();
            @SuppressWarnings("unchecked")
            Map<String, Object> discMap = (Map<String, Object>) bldg.get("disciplines");
            if (discMap != null) {
                for (Map.Entry<String, Object> e : discMap.entrySet()) {
                    @SuppressWarnings("unchecked")
                    List<String> ifcClasses = (List<String>) e.getValue();
                    disciplines.put(e.getKey(),
                            new DisciplineConfig(e.getKey(), ifcClasses != null ? ifcClasses : List.of()));
                }
            }
        } else if (bldg.containsKey("disciplines") && result.schemaVersion < 2) {
            @SuppressWarnings("unchecked")
            Map<String, Object> disc = (Map<String, Object>) bldg.get("disciplines");
            int discCount = disc != null ? disc.size() : 0;
            System.out.printf("[GUARD] 'disciplines' section found (%d disciplines) "
                    + "but schema_version %d does not process it — requires v2.%n",
                    discCount, result.schemaVersion);
        }

        // Parse mep_disciplines (§10.4.11 T3.4: RE subset whitelist)
        @SuppressWarnings("unchecked")
        List<String> mepDisciplines = null;
        Object mepRaw = bldg.get("mep_disciplines");
        if (mepRaw instanceof List<?> mepList) {
            mepDisciplines = new ArrayList<>();
            for (Object item : mepList) {
                if (item != null) mepDisciplines.add(item.toString());
            }
        }

        result.building = new BuildingConfig(
                getString(bldg, "building_type"),
                getString(bldg, "prefix"),
                getString(bldg, "building_bom_id"),
                getString(bldg, "doc_sub_type"),
                getString(bldg, "product_category"),
                getString(bldg, "name"),
                getString(bldg, "provenance"),
                storeys, floorRooms, staticChildren, composition,
                disciplines,
                getString(bldg, "dsl_file"),
                getInt(bldg, "geometry_fail_threshold", 0),
                getString(bldg, "jurisdiction"),
                mepDisciplines
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
