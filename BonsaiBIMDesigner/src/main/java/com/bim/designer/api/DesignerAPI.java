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
 *
 * <p>Extends {@link AssemblyAPI} for envelope assembly operations (G-7).
 * Assembly records are inherited and accessible as {@code DesignerAPI.*}.
 */
public interface DesignerAPI extends AssemblyAPI {

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
     * Lists available facility types for the UI dropdown.
     * Returns all FacilityType enum values with display metadata.
     *
     * // Implementing CORE_SRS.md §3.1 — Witness: W-INFRA-UI-FILTER
     */
    List<FacilityTypeInfo> listFacilityTypes();

    /**
     * Lists segments (FLOOR-level BOMs) for an infrastructure facility.
     * For buildings, these are storeys. For infra, these are segments:
     * carriageways (CW), track sections (TRK), piers (PIR), etc.
     *
     * <p>The bom_category on each segment encodes the vocabulary:
     * Bridge: ABT (abutment), PIR (pier), DCK (deck), SUP (superstructure), APR (approach)
     * Road: CW (carriageway), PKG (parking)
     * Rail: TRK (track)
     *
     * // Implementing INFRA_DESIGNER_SRS.md §0.2 — Witness: W-INFRA-SEG-1
     */
    List<SegmentInfo> listSegments(String buildingBomId);

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

    // ── Design Mode — Snap + Validate (§17.10) ─────────────────────────

    /**
     * Snap bboxes to grid + validate against PlacementValidator.
     * Returns adjusted bboxes with a change log.
     *
     * <p>All snap parameters are bundled in {@link SnapOptions}:
     * <ul>
     *   <li>{@code jurisdiction} — ISO country code for rule loading</li>
     *   <li>{@code gridMm} — grid snap size (0 = no grid snap)</li>
     *   <li>{@code fixRule/fixBomId} — optional click-to-fix (§18.4 UX-F-20)</li>
     *   <li>{@code facilityType} — optional infra mode (BRIDGE/ROAD/RAILWAY)</li>
     * </ul>
     *
     * // Implementing BBC.md §4, CORE_SRS.md §3.1 — Witness: W-SNAP-1, W-INFRA-UI-FILTER
     */
    SnapResponse snap(List<DesignBBox> bboxes, SnapOptions options);

    // ── Jurisdiction switch (§17) ────────────────────────────────────

    /**
     * Switch jurisdiction and re-validate all bboxes against the new rule set.
     * Stores the new jurisdiction for subsequent snap() calls.
     *
     * <p>For infrastructure, pass a non-null facilityType to load
     * provenance-scoped rules instead of jurisdiction-scoped rules.
     *
     * // Implementing CORE_SRS.md §3.1 — Witness: W-INFRA-UI-FILTER
     *
     * @param jurisdiction ISO country code: "MY", "US", "UK", "AU", "SG"
     * @param bboxes       current design bboxes to re-validate
     * @param facilityType facility type ("BUILDING","BRIDGE","ROAD","RAILWAY"), null = BUILDING
     */
    JurisdictionResponse setJurisdiction(String jurisdiction, List<DesignBBox> bboxes,
                                          String facilityType);

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

    // ── Records ───────────────────────────────────────────────────────

    /** Summary of an available building type (from C_DocType). */
    record BuildingTypeInfo(
            String docTypeId,
            String name,
            String docSubType,
            int expectedElements,
            double aabbWidthMm,
            double aabbDepthMm,
            double aabbHeightMm,
            String facilityType   // null = BUILDING; "BRIDGE", "ROAD", "RAILWAY" for infra
    ) {
        /** Backward-compatible constructor — no facilityType (defaults to BUILDING). */
        BuildingTypeInfo(String docTypeId, String name, String docSubType,
                         int expectedElements, double aabbWidthMm,
                         double aabbDepthMm, double aabbHeightMm) {
            this(docTypeId, name, docSubType, expectedElements,
                 aabbWidthMm, aabbDepthMm, aabbHeightMm, null);
        }
    }

    /** Facility type metadata for UI dropdown. */
    record FacilityTypeInfo(
            String name,           // enum name: "BUILDING", "BRIDGE", etc.
            boolean infrastructure, // true for infra types
            String provenance      // provenance column value, null for BUILDING
    ) {}

    /** Summary of a BOM category available for selection. */
    record CategoryInfo(
            String categoryName,
            String bomType,
            int bomCount
    ) {}

    /** A segment (FLOOR-level BOM) in an infrastructure facility. */
    record SegmentInfo(
            String bomId,
            String name,
            String bomCategory,     // CW, PKG, TRK, PIR, DCK, SUP, ABT, APR, MS
            int elementCount,       // number of LEAF elements in this segment
            List<String> disciplines // SET-level disciplines (STR, GEO, PAV, MARK, etc.)
    ) {}

    /** Result of a verb execution. */
    record VerbResponse(
            boolean success,
            String verb,
            String summary,
            String error
    ) {}

    /**
     * Options for {@link #snap(List, SnapOptions)}.
     * Bundles all snap parameters into a single extensible record.
     *
     * <p>Replaces the previous 3 snap() overloads and 2 setJurisdiction() overloads.
     *
     * <p>When {@code terrainContext} is non-null, snap() adjusts each bbox's Z
     * to follow the terrain surface via {@link com.bim.designer.validation.TerrainSnap}.
     * The {@code terrainSnap} field selects the mode (ON_SURFACE / ABOVE / BELOW / PIER).
     *
     * // Implementing INFRA_DESIGNER_SRS.md §I-4 — Witness: W-SNAP-TERRAIN-1
     */
    record SnapOptions(
            String jurisdiction,   // ISO country code: "MY", "US", "UK", "AU", "SG"
            int gridMm,            // grid snap size in mm (0 = no grid snap, typical: 250)
            String fixRule,        // click-to-fix rule param (e.g. "min_dim_mm"), null = none
            String fixBomId,       // click-to-fix target bbox ID, null = none
            String facilityType,   // "BUILDING","BRIDGE","ROAD","RAILWAY", null = BUILDING
            com.bim.designer.validation.PlacementContext terrainContext,  // terrain for Z, null = flat
            com.bim.designer.validation.TerrainSnap terrainSnap,         // snap mode, null = none
            com.bim.designer.validation.GradingStrategy gradingStrategy  // contour/straight/blend, null = contour
    ) {
        /** Minimal options — jurisdiction + grid, no fix, building mode, no terrain. */
        public SnapOptions(String jurisdiction, int gridMm) {
            this(jurisdiction, gridMm, null, null, null, null, null, null);
        }

        /** Building/infra mode without terrain — backward compatible. */
        public SnapOptions(String jurisdiction, int gridMm, String fixRule,
                           String fixBomId, String facilityType) {
            this(jurisdiction, gridMm, fixRule, fixBomId, facilityType, null, null, null);
        }

        /** Terrain mode without grading — defaults to CONTOUR (follow terrain). */
        public SnapOptions(String jurisdiction, int gridMm, String fixRule,
                           String fixBomId, String facilityType,
                           com.bim.designer.validation.PlacementContext terrainContext,
                           com.bim.designer.validation.TerrainSnap terrainSnap) {
            this(jurisdiction, gridMm, fixRule, fixBomId, facilityType,
                 terrainContext, terrainSnap, null);
        }
    }

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
