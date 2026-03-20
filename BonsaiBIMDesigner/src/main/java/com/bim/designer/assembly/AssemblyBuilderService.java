package com.bim.designer.assembly;

import com.bim.designer.api.AssemblyAPI.*;
import com.bim.designer.assembly.AssemblyDAO.MaterialLayer;
import com.bim.designer.assembly.UValueCalculator.LayerThermal;
import com.bim.designer.assembly.UValueCalculator.Orientation;
import com.bim.orm.BIMLogger;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Orchestration service for the Assembly Builder (G-7).
 * Delegates reads to {@link AssemblyDAO}, calculations to {@link UValueCalculator}.
 *
 * // Implementing BIM_Designer.md §18.2 Principle 4 — Witness: W-ASM-LIST-1
 */
public class AssemblyBuilderService {

    private static final String TAG = "AssemblyBuilder";

    private final AssemblyDAO dao;
    private Map<String, Double> thermalCache;

    public AssemblyBuilderService(Connection componentLibConn) {
        this.dao = new AssemblyDAO(componentLibConn);
    }

    // ── Category → prefix mapping ────────────────────────────────────

    /** Map API category to material_layers prefix(es). */
    private static List<String> prefixesFor(String category) {
        return switch (category.toUpperCase()) {
            case "WALL"    -> List.of("Basic Wall");
            case "ROOF"    -> List.of("Basic Roof");
            case "FLOOR"   -> List.of("Floor");
            case "CEILING" -> List.of("Compound Ceiling");
            default        -> List.of(category); // pass-through
        };
    }

    /** Infer orientation from category for U-value surface resistance. */
    private static Orientation orientationFor(String category) {
        return switch (category.toUpperCase()) {
            case "ROOF", "CEILING" -> Orientation.HORIZONTAL_UP;
            case "FLOOR"           -> Orientation.HORIZONTAL_DOWN;
            default                -> Orientation.VERTICAL;
        };
    }

    /** Infer category from layer_set_name prefix. */
    static String categoryFromLayerSetName(String layerSetName) {
        if (layerSetName.startsWith("Basic Wall:"))       return "WALL";
        if (layerSetName.startsWith("Basic Roof:"))       return "ROOF";
        if (layerSetName.startsWith("Floor:"))            return "FLOOR";
        if (layerSetName.startsWith("Compound Ceiling:")) return "CEILING";
        return "OTHER";
    }

    // ── Thermal cache ────────────────────────────────────────────────

    private Map<String, Double> thermal() throws Exception {
        if (thermalCache == null) {
            thermalCache = dao.loadAllThermalProperties();
        }
        return thermalCache;
    }

    // ── API methods ──────────────────────────────────────────────────

    /**
     * List assembly templates for a category.
     * // Implementing ASSEMBLY_BUILDER_SRS.md §3.1 — Witness: W-ASM-LIST-1
     */
    public AssemblyListResponse listAssemblyTemplates(String category) {
        try {
            List<AssemblyTemplateSummary> summaries = new ArrayList<>();
            for (String prefix : prefixesFor(category)) {
                List<String> names = dao.listLayerSets(prefix);
                for (String name : names) {
                    List<MaterialLayer> layers = dao.loadLayers(name);
                    double totalMm = layers.stream().mapToDouble(MaterialLayer::thicknessMm).sum();
                    double uValue = computeUValue(layers, category);
                    summaries.add(new AssemblyTemplateSummary(
                            name, categoryFromLayerSetName(name),
                            layers.size(), Math.round(totalMm * 100.0) / 100.0,
                            uValue, null));
                }
            }
            return new AssemblyListResponse(true, summaries, null);
        } catch (Exception e) {
            BIMLogger.warn(TAG, "listAssemblyTemplates failed: {}", e.getMessage());
            return new AssemblyListResponse(false, List.of(), e.getMessage());
        }
    }

    /**
     * Get full detail for an assembly template.
     * // Implementing ASSEMBLY_BUILDER_SRS.md §3.1 — Witness: W-ASM-DETAIL-1
     */
    public AssemblyDetailResponse getAssemblyDetail(String layerSetName) {
        try {
            List<MaterialLayer> raw = dao.loadLayers(layerSetName);
            if (raw.isEmpty()) {
                return new AssemblyDetailResponse(false, layerSetName, null,
                        List.of(), 0, 0, null, "No layers found for: " + layerSetName);
            }

            String category = categoryFromLayerSetName(layerSetName);
            List<AssemblyLayer> layers = raw.stream()
                    .map(ml -> new AssemblyLayer(ml.sequence(), ml.materialName(),
                            ml.thicknessMm(), inferRole(ml.materialName()),
                            ml.isVentilated()))
                    .toList();
            double totalMm = layers.stream().mapToDouble(AssemblyLayer::thicknessMm).sum();
            double uValue = computeUValue(raw, category);

            return new AssemblyDetailResponse(true, layerSetName, category,
                    layers, Math.round(totalMm * 100.0) / 100.0, uValue, null, null);
        } catch (Exception e) {
            BIMLogger.warn(TAG, "getAssemblyDetail failed: {}", e.getMessage());
            return new AssemblyDetailResponse(false, layerSetName, null,
                    List.of(), 0, 0, null, e.getMessage());
        }
    }

    /**
     * Browse alternative materials for a layer position.
     * // Implementing ASSEMBLY_BUILDER_SRS.md §3.1 — Witness: W-ASM-BROWSE-1
     */
    public BrowseAssemblyLayersResponse browseAssemblyLayers(
            BrowseAssemblyLayersRequest request) {
        try {
            List<MaterialLayer> layers = dao.loadLayers(request.layerSetName());
            if (request.layerSequence() < 0 || request.layerSequence() >= layers.size()) {
                return new BrowseAssemblyLayersResponse(false, request.layerSetName(),
                        request.layerSequence(), List.of(),
                        "Invalid layer sequence: " + request.layerSequence());
            }

            MaterialLayer current = layers.get(request.layerSequence());
            String role = inferRole(current.materialName());
            var rawAlts = dao.browseAlternatives(current.materialName(), role);

            List<AlternativeMaterial> alts = rawAlts.stream()
                    .map(a -> new AlternativeMaterial(a.materialName(),
                            a.conductivityWmK(), current.thicknessMm(),
                            inferRole(a.materialName())))
                    .toList();

            // Apply pagination
            int offset = Math.max(0, request.offset());
            int limit = request.limit() > 0 ? request.limit() : 20;
            List<AlternativeMaterial> page = alts.stream()
                    .skip(offset).limit(limit).toList();

            return new BrowseAssemblyLayersResponse(true, request.layerSetName(),
                    request.layerSequence(), page, null);
        } catch (Exception e) {
            BIMLogger.warn(TAG, "browseAssemblyLayers failed: {}", e.getMessage());
            return new BrowseAssemblyLayersResponse(false, request.layerSetName(),
                    request.layerSequence(), List.of(), e.getMessage());
        }
    }

    /**
     * Swap a layer and recalculate. Stateless — does not mutate component_library.db.
     * // Implementing ASSEMBLY_BUILDER_SRS.md §3.1 — Witness: W-ASM-SWAP-1
     */
    public AssemblyDetailResponse swapLayer(SwapLayerRequest request) {
        try {
            List<MaterialLayer> raw = dao.loadLayers(request.layerSetName());
            if (request.layerSequence() < 0 || request.layerSequence() >= raw.size()) {
                return new AssemblyDetailResponse(false, request.layerSetName(), null,
                        List.of(), 0, 0, null,
                        "Invalid layer sequence: " + request.layerSequence());
            }

            // Build modified layer list
            List<MaterialLayer> modified = new ArrayList<>(raw);
            MaterialLayer old = raw.get(request.layerSequence());
            modified.set(request.layerSequence(), new MaterialLayer(
                    old.layerSetName(), old.sequence(),
                    request.newMaterialName(), request.newThicknessMm(),
                    isAirGapMaterial(request.newMaterialName())));

            String category = categoryFromLayerSetName(request.layerSetName());
            List<AssemblyLayer> layers = modified.stream()
                    .map(ml -> new AssemblyLayer(ml.sequence(), ml.materialName(),
                            ml.thicknessMm(), inferRole(ml.materialName()),
                            ml.isVentilated()))
                    .toList();
            double totalMm = layers.stream().mapToDouble(AssemblyLayer::thicknessMm).sum();
            double uValue = computeUValue(modified, category);

            return new AssemblyDetailResponse(true, request.layerSetName(), category,
                    layers, Math.round(totalMm * 100.0) / 100.0, uValue, null, null);
        } catch (Exception e) {
            BIMLogger.warn(TAG, "swapLayer failed: {}", e.getMessage());
            return new AssemblyDetailResponse(false, request.layerSetName(), null,
                    List.of(), 0, 0, null, e.getMessage());
        }
    }

    // ── U-value computation ──────────────────────────────────────────

    private double computeUValue(List<MaterialLayer> layers, String category) throws Exception {
        Map<String, Double> props = thermal();
        Orientation orientation = orientationFor(category);

        List<LayerThermal> thermalLayers = new ArrayList<>(layers.size());
        for (MaterialLayer ml : layers) {
            boolean airGap = isAirGapMaterial(ml.materialName());
            boolean metalStud = isMetalStudMaterial(ml.materialName());
            double lambda = props.getOrDefault(ml.materialName(), 1.0); // fallback: dense material
            thermalLayers.add(new LayerThermal(ml.thicknessMm(), lambda, airGap, metalStud));
        }

        return UValueCalculator.calculate(thermalLayers, orientation);
    }

    // ── Material classification helpers ──────────────────────────────

    /** Infer layer role from material name. Phase 1: keyword matching. */
    static String inferRole(String materialName) {
        String lower = materialName.toLowerCase();
        if (lower.contains("insulation") || lower.contains("rigid insulation"))
            return "INSULATION";
        if (lower.contains("air") || lower.contains("cavity"))
            return "CAVITY";
        if (lower.contains("brick") || lower.contains("cladding") || lower.contains("render"))
            return "CLADDING";
        if (lower.contains("plasterboard") || lower.contains("plaster") || lower.contains("gypsum"))
            return "LINING";
        if (lower.contains("vapor") || lower.contains("damp") || lower.contains("membrane")
                || lower.contains("epdm") || lower.contains("barrier") || lower.contains("felt"))
            return "MEMBRANE";
        if (lower.contains("block") || lower.contains("concrete") || lower.contains("stud")
                || lower.contains("wood") || lower.contains("lumber") || lower.contains("timber"))
            return "STRUCTURE";
        return "OTHER";
    }

    private static boolean isAirGapMaterial(String materialName) {
        String lower = materialName.toLowerCase();
        return lower.contains("air") && (lower.contains("gap") || lower.contains("space")
                || lower.contains("layer") || lower.equals("air"));
    }

    private static boolean isMetalStudMaterial(String materialName) {
        String lower = materialName.toLowerCase();
        return lower.contains("metal") && lower.contains("stud");
    }
}
