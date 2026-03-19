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
     * Extended snap with optional click-to-fix parameters.
     * If fixRule and fixBomId are non-null, the target bbox dimension is
     * forced to meet the rule's minimum before normal snap processing.
     *
     * @param fixRule  rule parameter to fix (e.g. "min_dim_mm"), or null
     * @param fixBomId BOM ID of the bbox to fix, or null
     */
    SnapResponse snap(List<DesignBBox> bboxes, String jurisdiction, int gridMm,
                      String fixRule, String fixBomId);

    // ── Jurisdiction switch (§17) ────────────────────────────────────

    /**
     * Switch jurisdiction and re-validate all bboxes against the new rule set.
     * Stores the new jurisdiction for subsequent snap() calls.
     *
     * @param jurisdiction ISO country code: "MY", "US", "UK", "AU", "SG"
     * @param bboxes       current design bboxes to re-validate
     */
    JurisdictionResponse setJurisdiction(String jurisdiction, List<DesignBBox> bboxes);

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

    // ── BOM Chooser (§17.18) ──────────────────────────────────────────

    /**
     * Browse product catalog with search, category filter, and container fit check.
     * Queries component_library.db M_Product with SQL LIKE and AABB comparison.
     * Server-driven pagination for 1000+ items.
     *
     * @see BrowseItemsRequest
     * @see BrowseItemsResponse
     */
    BrowseItemsResponse browseItems(BrowseItemsRequest request);

    // ── Find Similar (§25.3 — JEPA-inspired embedding similarity) ─────

    /**
     * Find products similar to a given product, ranked by cosine similarity
     * of deterministic feature vectors. Returns items with fit status against
     * the optional container dimensions.
     *
     * // Implementing BIM_Designer_SRS.md §25.3 — Witness: W-EMB-SIM-1
     *
     * @see FindSimilarRequest
     * @see FindSimilarResponse
     */
    FindSimilarResponse findSimilar(FindSimilarRequest request);

    // ── Promote ────────────────────────────────────────────────────────

    /**
     * Promote current design to BOM — governance gate.
     * Creates new m_bom + m_bom_line in {PREFIX}_BOM.db.
     * Requires: no dangles, compliance passed, owner set.
     *
     * @throws IllegalStateException if dangles remain or validation fails
     */
    PromoteResponse promote(PromoteRequest request);

    // ── Approve gate (§18) ──────────────────────────────────────────────

    /**
     * Validate all bboxes for a building and return compliance status.
     * This is the governance gate before promote.
     *
     * // Implementing BIM_Designer_SRS.md §18.1 — Witness: W-APPROVE-1
     */
    ApproveResponse approve(String buildingId);

    // ── Variant comparison (§19) ─────────────────────────────────────

    /**
     * Compare two or more saved variants side-by-side.
     * Returns stats per variant and field-level diffs.
     */
    CompareVariantsResponse compareVariants(String buildingId, List<String> variantIds);

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

    // ── BOM Chooser records (§17.18) ───────────────────────────────────

    /** Browse request — search-first product browser with container fit. */
    record BrowseItemsRequest(
            String search,
            String category,
            String buildingType,
            double containerWidthMm,
            double containerDepthMm,
            double containerHeightMm,
            int offset,
            int limit
    ) {}

    /** Browse response — paginated items with fit status and category counts. */
    record BrowseItemsResponse(
            boolean success,
            List<BrowseItem> items,
            int totalCount,
            List<CategoryCount> categories,
            String error
    ) {}

    /** A product item with fit status against the focused room. */
    record BrowseItem(
            String productId,
            String name,
            String productType,
            double widthMm,
            double depthMm,
            double heightMm,
            String ifcClass,
            String fitStatus
    ) {}

    /** Category with total and fits-only counts. */
    record CategoryCount(
            String name,
            int count,
            int fitsCount
    ) {}

    // ── Find Similar records (§25.3) ──────────────────────────────────

    /** Find similar request — product ID + optional container for fit check. */
    record FindSimilarRequest(
            String productId,
            double containerWidthMm,
            double containerDepthMm,
            double containerHeightMm,
            int limit
    ) {}

    /** Find similar response — ranked items with similarity score + fit status. */
    record FindSimilarResponse(
            boolean success,
            List<SimilarItem> items,
            String sourceProductId,
            String error
    ) {}

    /** A product with similarity score and fit status. */
    record SimilarItem(
            String productId,
            String name,
            String productType,
            double widthMm,
            double depthMm,
            double heightMm,
            String ifcClass,
            String fitStatus,
            double similarity
    ) {}

    // ── Place Item + Layout Editing (§15, §16) ─────────────────────────

    /**
     * Place a product item into a room at a given offset.
     * Creates a new DesignBBox for the placed item.
     *
     * @see PlaceItemRequest
     * @see PlaceItemResponse
     */
    PlaceItemResponse placeItem(PlaceItemRequest request);

    /**
     * Add a room of the given category to the building layout.
     * Regenerates the floor layout to accommodate the new room.
     */
    LayoutResponse addRoom(String buildingId, String category, String storey);

    /**
     * Remove a room from the building layout by its bomId.
     * Regenerates the floor layout with the room removed.
     */
    LayoutResponse removeRoom(String buildingId, String roomBomId);

    /**
     * Add a new storey to the building, cloning GF rooms at the new Z offset.
     */
    LayoutResponse addStorey(String buildingId);

    /** Place item request — position a product inside a room. */
    record PlaceItemRequest(
            String buildingId,
            String roomBomId,
            String productId,
            double offsetXMm,
            double offsetYMm,
            double offsetZMm,
            List<DesignBBox> currentBboxes
    ) {}

    /** Place item response — the newly created bbox for the placed item. */
    record PlaceItemResponse(
            boolean success,
            int orderLineId,
            DesignBBox bbox,
            String error
    ) {}

    /** Layout editing response — updated bboxes after add/remove room or storey. */
    record LayoutResponse(
            boolean success,
            List<DesignBBox> bboxes,
            int roomCount,
            String error
    ) {}

    // ── Jurisdiction records (§17) ──────────────────────────────────

    /** Result of setJurisdiction — re-validation verdicts for all rooms. */
    record JurisdictionResponse(
            boolean success,
            String jurisdiction,
            int ruleCount,
            List<RuleVerdict> verdicts,
            String error
    ) {}

    /** A single rule verdict for a specific bbox. */
    record RuleVerdict(
            String bomId,
            String rule,
            String result,
            double actual,
            double required
    ) {}

    // ── Approve gate records (§18) ──────────────────────────────────

    /** Approve response — compliance gate result. */
    record ApproveResponse(
            boolean success,
            String status,
            int rulesPassed,
            int rulesTotal,
            List<String> dangles,
            List<Blocker> blockers,
            String error
    ) {}

    /** A blocking rule violation for the approve gate. */
    record Blocker(
            String bomId,
            String rule,
            double actual,
            double required,
            String message
    ) {}

    // ── Variant comparison records (§19) ─────────────────────────────

    /** Compare variants response. */
    record CompareVariantsResponse(
            boolean success,
            List<VariantStats> variants,
            List<FieldDiff> diffs,
            String error
    ) {}

    /** Statistics for a single variant. */
    record VariantStats(
            String variantId,
            String label,
            int roomCount,
            double totalAreaSqM,
            String compliance,
            int rulesPassed
    ) {}

    /** Field-level diff between variants. */
    record FieldDiff(
            String bomId,
            String field,
            double v1,
            double v2
    ) {}
}
