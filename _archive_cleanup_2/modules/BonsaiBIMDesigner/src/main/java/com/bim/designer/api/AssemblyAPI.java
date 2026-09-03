package com.bim.designer.api;

import java.util.List;

/**
 * Assembly Builder API — layer-by-layer envelope design (G-7).
 *
 * <p>Extracted from {@link DesignerAPI} as a self-contained concern.
 * Assembly operations read from {@code component_library.db} (material_layers,
 * ad_material_thermal) and are stateless — they never mutate the DB.
 *
 * <p>Implemented by {@link com.bim.designer.assembly.AssemblyBuilderService}.
 * {@link DesignerAPIImpl} delegates to that service via lazy initialization.
 *
 * // Implementing BIM_Designer.md §18.2 Principle 4 — G-7 Assembly Builder
 */
public interface AssemblyAPI {

    /**
     * List available assembly templates for a given category (WALL/ROOF/FLOOR/CEILING).
     *
     * // Implementing BIM_Designer.md §18.2 Principle 4 — Witness: W-ASM-LIST-1
     */
    AssemblyListResponse listAssemblyTemplates(String category);

    /**
     * Get the full layer stack for an assembly template.
     *
     * // Implementing BIM_Designer.md §18.2 Principle 4 — Witness: W-ASM-DETAIL-1
     */
    AssemblyDetailResponse getAssemblyDetail(String layerSetName);

    /**
     * Browse compatible alternative materials for a specific layer position.
     *
     * // Implementing BIM_Designer.md §18.2 Principle 4 — Witness: W-ASM-BROWSE-1
     */
    BrowseAssemblyLayersResponse browseAssemblyLayers(BrowseAssemblyLayersRequest request);

    /**
     * Replace a layer in an assembly with an alternative material.
     * Stateless — does NOT mutate component_library.db.
     *
     * // Implementing BIM_Designer.md §18.2 Principle 4 — Witness: W-ASM-SWAP-1
     */
    AssemblyDetailResponse swapLayer(SwapLayerRequest request);

    // ── Records ─────────────────────────────────────────────────────────

    /** Request to browse compatible layers for a position in an assembly. */
    record BrowseAssemblyLayersRequest(
            String layerSetName,
            int layerSequence,
            String materialCategory,
            int offset,
            int limit
    ) {}

    /** Request to swap a layer in an assembly. */
    record SwapLayerRequest(
            String layerSetName,
            int layerSequence,
            String newMaterialName,
            double newThicknessMm
    ) {}

    /** A single layer in an assembly. */
    record AssemblyLayer(
            int sequence,
            String materialName,
            double thicknessMm,
            String role,
            boolean isVentilated
    ) {}

    /** Response listing available assembly templates. */
    record AssemblyListResponse(
            boolean success,
            List<AssemblyTemplateSummary> templates,
            String error
    ) {}

    /** Summary of one assembly template. */
    record AssemblyTemplateSummary(
            String layerSetName,
            String category,
            int layerCount,
            double totalThicknessMm,
            double uValueWm2K,
            String compliance
    ) {}

    /** Full detail of an assembly template. */
    record AssemblyDetailResponse(
            boolean success,
            String layerSetName,
            String category,
            List<AssemblyLayer> layers,
            double totalThicknessMm,
            double uValueWm2K,
            String complianceNote,
            String error
    ) {}

    /** Response for browsing layer alternatives. */
    record BrowseAssemblyLayersResponse(
            boolean success,
            String layerSetName,
            int layerSequence,
            List<AlternativeMaterial> alternatives,
            String error
    ) {}

    /** A material alternative for a layer position. */
    record AlternativeMaterial(
            String materialName,
            double conductivityWmK,
            double defaultThicknessMm,
            String roleCompatibility
    ) {}
}
