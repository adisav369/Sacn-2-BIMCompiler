package com.bim.designer.api;

import com.bim.designer.compile.ChangeSet;

import java.util.List;

/**
 * Stable facade for the BonsaiBIMDesigner module (Item A contract).
 *
 * <p>This interface never reads YAML content directly. It delegates to
 * {@code BuildingRegistry} (which reads {@code C_DocType}) and
 * {@code VerbRegistry} for verb dispatch. This makes it immune to
 * Rosetta Stone YAML restructuring.
 *
 * <p>All methods are synchronous — the server wraps them in async
 * dispatch via {@link DesignerServer}.
 */
public interface DesignerAPI {

    /**
     * Full 9-stage compilation for the given building.
     * Wraps {@code CompilationPipeline.run()} — no new compilation logic.
     */
    CompileResponse compile(CompileRequest request);

    /**
     * Scope-limited recompile based on what changed.
     * Falls back to full compile for structural/YAML changes.
     */
    CompileResponse compileIncremental(CompileRequest request, ChangeSet changes);

    /**
     * Lists available building types from C_DocType via BuildingRegistry.
     * The GUI populates its building-type dropdown from this.
     */
    List<BuildingTypeInfo> listBuildingTypes();

    /**
     * Lists BOM categories available for a given building's DocSubType.
     * Feeds the typology chooser panel.
     */
    List<CategoryInfo> listCategories(String docSubType);

    /**
     * Creates a new building layout from design parameters.
     * Returns bounding boxes for design-mode viewport rendering.
     * Nothing is committed to BOM.db — bboxes are draft only.
     *
     * @see CreateNewResponse
     * @see DesignBBox
     */
    CreateNewResponse createNew(CreateNewRequest request);

    /**
     * Dispatches a single BIM COBOL verb line via VerbRegistry.
     * Returns the verb result as a structured response.
     */
    VerbResponse executeVerb(String buildingId, String verbLine);

    // ── Design Mode persistence (§17.10) ──────────────────────────────

    /**
     * Snap bboxes to grid + validate against PlacementValidator.
     * Returns adjusted bboxes with a change log.
     * Routes through existing AD_Val_Rule via BlenderBridge pipe.
     */
    SnapResponse snap(List<DesignBBox> bboxes, String jurisdiction, int gridMm);

    /**
     * Save current design to work_output.db (OrderLine + ASI).
     * Cheap, frequent. Every save is a recallable variant.
     */
    SaveResponse save(String buildingId, List<DesignBBox> bboxes, String variantLabel);

    /**
     * Recall a previous design variant from work_output.db.
     * Non-destructive — originals never overwritten.
     */
    RecallResponse recall(String buildingId, String variantId);

    /** List saved variants for a building. */
    List<VariantInfo> listVariants(String buildingId);

    /**
     * Promote current design to BOM — governance gate.
     * Creates new m_bom + m_bom_line in {PREFIX}_BOM.db.
     * Requires: no dangles, compliance passed, owner set.
     *
     * @throws IllegalStateException if dangles remain or validation fails
     */
    PromoteResponse promote(PromoteRequest request);

    // ── Records ───────────────────────────────────────────────────────

    /** Summary of an available building type (from C_DocType). */
    record BuildingTypeInfo(
            String docTypeId,
            String name,
            String docSubType,
            int expectedElements,
            double aabbWidthMm,
            double aabbDepthMm,
            double aabbHeightMm
    ) {}

    /** Summary of a BOM category available for selection. */
    record CategoryInfo(
            String categoryName,
            String bomType,
            int bomCount
    ) {}

    /** Result of a verb execution. */
    record VerbResponse(
            boolean success,
            String verb,
            String summary,
            String error
    ) {}

    /** Snap result — adjusted bboxes + change log. */
    record SnapResponse(
            boolean success,
            List<DesignBBox> bboxes,
            List<Adjustment> adjustments,
            String error
    ) {}

    /** A single adjustment made by Snap. */
    record Adjustment(
            String bomId,
            String rule,
            String field,
            double from,
            double to
    ) {}

    /** Save result — variant ID for future recall. */
    record SaveResponse(
            boolean success,
            String variantId,
            String outputDbPath,
            String error
    ) {}

    /** Recall result — restored bboxes from a saved variant. */
    record RecallResponse(
            boolean success,
            List<DesignBBox> bboxes,
            String variantLabel,
            String error
    ) {}

    /** Summary of a saved variant. */
    record VariantInfo(
            String variantId,
            String label,
            String timestamp,
            int orderLineCount,
            String complianceStatus
    ) {}

    /** Promote request — governance gate metadata. */
    record PromoteRequest(
            String buildingId,
            String owner,
            String complianceRef,
            String provenance,
            List<DesignBBox> bboxes
    ) {}

    /** Promote result. */
    record PromoteResponse(
            boolean success,
            String buildingId,
            int bomEntriesCreated,
            List<String> dangles,
            String error
    ) {}
}
