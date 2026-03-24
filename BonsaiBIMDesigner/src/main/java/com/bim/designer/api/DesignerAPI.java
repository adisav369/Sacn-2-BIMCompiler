package com.bim.designer.api;

import com.bim.backoffice.model.DesignBBox;
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
     * <p>The m_product_category_id on each segment encodes the vocabulary:
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

    // ── ORDER View + BOM Outliner — G-9 (§17.11, §17.19) ──────────────

    /**
     * List all C_OrderLine rows for a building's active sub-order.
     * Returns flat tabular data for the ORDER View (§17.11).
     *
     * // Implementing BIM_Designer.md §17.11 — Witness: W-OV-LIST-1
     */
    ListOrderLinesResponse listOrderLines(String buildingId);

    /**
     * Update a single field on a C_OrderLine (ORDER View inline edit).
     * Only whitelisted dimension/position/qty fields are editable.
     * Triggers re-validation after update.
     *
     * // Implementing BIM_Designer.md §17.11, UX-F-26 — Witness: W-OV-UPDATE-1
     */
    UpdateOrderLineResponse updateOrderLine(int orderLineId, String buildingId,
                                             String field, String value);

    /**
     * Swap the product on a C_OrderLine — BOM configurator pattern.
     * User navigates BOM tree, selects a node, swaps to a different product
     * in the same category (e.g., flat roof → pitched roof).
     *
     * // Implementing GENERATIVE_HOUSE_SRS.md §2.1 TC-4 — Witness: W-SWAP-1
     */
    UpdateOrderLineResponse swapProduct(int orderLineId, String buildingId,
                                        String newProductId);

    /**
     * Get the BOM tree for a building — hierarchical BUILDING→FLOOR→ROOM→ITEM.
     * Built from C_OrderLine Parent_OrderLine_ID relationships.
     *
     * // Implementing BIM_Designer.md §17.19 — Witness: W-OV-TREE-1
     */
    BomTreeResponse getBomTree(String buildingId);

    // ── Place Item + Layout Editing (§15, §16) ─────────────────────────

    /**
     * Place a product item into a room at a given offset.
     * Creates a new DesignBBox for the placed item.
     * Persists C_OrderLine to work_output.db if a master order exists.
     *
     * @see PlaceItemRequest
     * @see PlaceItemResponse
     */
    PlaceItemResponse placeItem(PlaceItemRequest request);

    // ── Click-to-Place — G-8 Interactive Discipline Placement (§18.8) ──

    /**
     * Interactive click-to-place: resolve viewport click to a room,
     * look up discipline-appropriate products, and place a seed item.
     *
     * <p>Data flow (BIM_Designer.md §18.8):
     * <ol>
     *   <li>Resolve (clickX, clickY, clickZ) → which room bbox contains the point</li>
     *   <li>Look up discipline products for that room category</li>
     *   <li>Place the first fitting product at the click offset within the room</li>
     * </ol>
     *
     * <p>The discipline determines what category of products are placed:
     * ARC = furniture, FP = fire protection, ELEC = electrical, SP = sanitary, ACMV = mechanical.
     *
     * // Implementing BIM_Designer.md §18.8 — Witness: W-CTP-1, W-CTP-2
     *
     * @see ClickToPlaceRequest
     * @see ClickToPlaceResponse
     */
    ClickToPlaceResponse clickToPlace(ClickToPlaceRequest request);

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

    // ── Flywheel Advisory Panel (FL-2, §27) ──────────────────────────

    /**
     * List advisories for a building from the flywheel validation pool.
     * Reads W_Validation_Advisory from disc_validation.db.
     *
     * // Implementing BIM_Designer_SRS.md §27 — Witness: W-FL-ADVISORY-1
     */
    ListAdvisoriesResponse listAdvisories(String buildingId);

    /** One advisory finding from the flywheel. */
    record Advisory(int advisoryId, String buildingType, String elementRef,
                    String layer, String severity, String ruleName,
                    String message, Double actualValue,
                    Double expectedMin, Double expectedMax,
                    String suggestion) {}

    /** Response for listAdvisories — includes severity counts. */
    record ListAdvisoriesResponse(boolean success,
                                   java.util.List<Advisory> advisories,
                                   int infoCount, int warningCount,
                                   int suggestionCount, String error) {}

    /**
     * Suggest typical dimensions for an IFC class from the mined validation pool.
     * Returns typical W/D/H ranges + nearest M_Product matches.
     *
     * // Implementing BIM_Designer_SRS.md §27, FL-F-05 — Witness: W-FL-CALIBRATE-1
     */
    SuggestDimensionsResponse suggestDimensions(String ifcClass);

    /** A matching product from the component library. */
    record NearestProduct(String productId, String name,
                          double widthMm, double depthMm, double heightMm) {}

    /** Response for suggestDimensions — typical ranges + nearest products. */
    record SuggestDimensionsResponse(boolean success,
                                      String ifcClass,
                                      double typicalMinW, double typicalMaxW,
                                      double typicalMinD, double typicalMaxD,
                                      double typicalMinH, double typicalMaxH,
                                      int observationCount,
                                      java.util.List<NearestProduct> nearestProducts,
                                      String error) {}

    // ── Selection Cascade — Room Auto-Suggest (BBC.md §3.5) ──────────

    /**
     * Suggest room contents via BBC.md §3.5 Selection Cascade.
     * For each room DesignBBox with a category, finds the best-matching SET BOM
     * from the library by m_product_category_id + AABB fit.
     *
     * <p>This is the generative path's auto-suggest: YAML defines rooms with
     * m_product_category_id + AABB → cascade finds matching BOMs → same compiler.
     * Zero new compilation code.
     *
     * // Implementing GENERATIVE_ROOM_SRS.md §3 — Witness: W-GEN-1
     */
    SuggestRoomContentsResponse suggestRoomContents(List<DesignBBox> rooms);

    /** One room's cascade suggestion. */
    record RoomSuggestion(
            String roomBomId,
            String category,
            String suggestedSetBomId,
            String suggestedSetName,
            int elementCount,
            int candidateCount
    ) {}

    /** Response for suggestRoomContents. */
    record SuggestRoomContentsResponse(
            boolean success,
            List<RoomSuggestion> suggestions,
            int matchedCount,
            int totalRooms,
            String error
    ) {}

    // ── BOM Drop — Template-First Building Design (§28) ──────────

    /**
     * BOM Drop: create a C_Order with a single C_OrderLine referencing a
     * BUILDING product, then explode the BOM tree into child C_OrderLine rows.
     * Returns the exploded tree as BomTreeNode for the Outliner.
     *
     * <p>iDempiere pattern: C_OrderLine references M_Product_ID (family_ref).
     * If the product IsBOM (has a matching m_bom entry), the engine walks
     * m_bom / m_bom_line recursively to explode the tree. Non-BOM products
     * become LEAF C_OrderLine rows. The user can then navigate, swap by
     * M_Product_Category (m_product_category_id), add, or remove nodes.
     *
     * <p>Instant Drop (TC-1): bomDrop with no edits → compile produces
     * identical output to the Rosetta Stone for that building.
     *
     * // Implementing GENERATIVE_HOUSE_SRS.md §2.1, BIM_Designer_SRS.md §28.1
     * // Witness: W-DROP-1
     */
    BomDropResponse bomDrop(String buildingProductId);

    /**
     * BOM Drop response — exploded BOM tree + order metadata.
     *
     * @param success          true if BOM found and tree exploded
     * @param orderId          the C_Order_ID created for this design session
     * @param orderLineId      the root C_OrderLine_ID (BUILDING product)
     * @param tree             exploded BOM tree for Outliner navigation
     * @param totalElements    leaf count (element count if compiled as-is)
     * @param error            error message if success=false
     */
    record BomDropResponse(
            boolean success,
            String orderId,
            int orderLineId,
            BomTreeNode tree,
            int totalElements,
            String error
    ) {}

    // ── Add Discipline — Rule-Driven OrderLine Mutation (§14.3 Session A) ─

    /**
     * Add a discipline to an existing order by querying ad_space_type_mep_bom
     * for each room and creating PROPOSED C_OrderLines.
     *
     * <p>The architect reviews proposed lines and accepts or rejects each.
     * Proposed lines have bom_child_id=NULL (rule-driven, not BOM-derived)
     * and proposal_status='PROPOSED'.
     *
     * <p>Delegates to {@link OrderMutationService#addDiscipline}.
     *
     * // Implementing ProjectOrderBlueprint.md §14.3 Session A — Witness: W-DM-TC5-1
     *
     * @param buildingId     building ID (for work_output.db lookup)
     * @param orderId        C_Order_ID to add discipline lines to
     * @param discipline     discipline code: FP, ELEC, SP, ACMV
     * @param jurisdiction   ISO country code for rule selection (e.g., "MY")
     */
    AddDisciplineResponse addDiscipline(String buildingId, String orderId,
                                         String discipline, String jurisdiction);

    /** Response for addDiscipline. */
    record AddDisciplineResponse(
            boolean success,
            int linesCreated,
            int roomsProcessed,
            String discipline,
            String error
    ) {}

    // ── Auto-Populate + ASI Authoring (BIM_Designer_SRS.md §28) ────

    /**
     * Auto-populate all empty rooms with cascade + ASI + validation rules.
     * Same as WALK-THRU but applied to the interactive C_OrderLine tree.
     *
     * // Implementing BIM_Designer_SRS.md §28.3 — Witness: W-ASI-AUTO-1
     */
    AutoPopulateResponse autoPopulate(String buildingId, List<DesignBBox> rooms);

    /** Auto-populate result — how many rooms/lines/ASIs were created. */
    record AutoPopulateResponse(
            boolean success,
            int roomsFilled,
            int orderLinesCreated,
            int asiCreated,
            List<String> validationWarnings,
            String error
    ) {}

    /**
     * Read ASI attributes for a C_OrderLine.
     * Returns empty fields if no ASI exists (IsInstanceAttribute=0 product).
     *
     * // Implementing BIM_Designer_SRS.md §28.8 — Witness: W-ASI-READ-1
     */
    ASIResponse readASI(String buildingId, int orderLineId);

    /**
     * Update a single ASI attribute value. Creates ASI header if needed.
     *
     * // Implementing BIM_Designer_SRS.md §28.8 — Witness: W-ASI-EDIT-1
     */
    ASIResponse updateASI(String buildingId, int orderLineId,
                           String attributeName, String value);

    /** ASI response — current attribute state for an order line. */
    record ASIResponse(
            boolean success,
            int orderLineId,
            String attributeSetName,
            boolean isInstanceAttribute,
            List<ASIField> fields,
            String error
    ) {}

    /** Single ASI field with value and override status. */
    record ASIField(
            String name,
            String value,
            String valueType,
            boolean overridden
    ) {}

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

    // ── ORDER View + BOM Outliner records (G-9) ─────────────────────

    /** ORDER View row — flat tabular representation of a C_OrderLine. */
    record OrderLineInfo(int orderLineId, String familyRef, String hostType,
                         String bomCategory, double dx, double dy, double dz,
                         double widthMm, double depthMm, double heightMm,
                         int qty, String validationStatus, int parentOrderLineId) {}

    /** Response for listOrderLines. */
    record ListOrderLinesResponse(boolean success, java.util.List<OrderLineInfo> lines,
                                   String error) {}

    /** Response for updateOrderLine — returns the updated row. */
    record UpdateOrderLineResponse(boolean success, OrderLineInfo updated,
                                    String error) {}

    /** BOM Outliner tree node — hierarchical BUILDING→FLOOR→ROOM→ITEM. */
    record BomTreeNode(int orderLineId, String familyRef, String hostType,
                       String bomCategory, double widthMm, double depthMm,
                       double heightMm, int qty, String validationStatus,
                       java.util.List<BomTreeNode> children) {}

    /** Response for getBomTree. */
    record BomTreeResponse(boolean success, java.util.List<BomTreeNode> roots,
                            String error) {}

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

    /** Click-to-place request — viewport coordinates + discipline context. */
    record ClickToPlaceRequest(
            String buildingId,
            double clickXMm,        // viewport X in world mm
            double clickYMm,        // viewport Y in world mm
            double clickZMm,        // viewport Z in world mm
            String discipline,      // ARC, FP, ELEC, SP, ACMV
            List<DesignBBox> currentBboxes
    ) {}

    /** Click-to-place response — resolved room + placed items + MEP requirements. */
    record ClickToPlaceResponse(
            boolean success,
            String resolvedRoomBomId,
            String discipline,
            List<DesignBBox> placedItems,
            List<MEPRequirementInfo> mepRequirements,
            String error
    ) {
        /** Backward-compatible constructor without MEP requirements. */
        ClickToPlaceResponse(boolean success, String resolvedRoomBomId, String discipline,
                             List<DesignBBox> placedItems, String error) {
            this(success, resolvedRoomBomId, discipline, placedItems, List.of(), error);
        }
    }

    /** MEP product requirement from ad_space_type_mep_bom. */
    record MEPRequirementInfo(
            String mepProductId,
            int qtyNormal,
            int qtyPlaced,
            String placementRule,
            String hostSurface,
            String buildingCode,
            String codeClause
    ) {
        /** Constructor without qtyPlaced (defaults to 0). */
        MEPRequirementInfo(String mepProductId, int qtyNormal,
                           String placementRule, String hostSurface,
                           String buildingCode, String codeClause) {
            this(mepProductId, qtyNormal, 0, placementRule, hostSurface,
                 buildingCode, codeClause);
        }
    }

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

    // ── WF-BB §26 — Wireframe-First Interaction Protocol ──────────────

    /**
     * Get metadata for a BOM element — properties popup (§26.6).
     * Returns dimensional, material, cost, and compliance data.
     * Side-effect-free read.
     */
    ElementMetadataResponse getElementMetadata(String bomId, String buildingId);

    /**
     * Get all connected elements in a system chain (§26.12.1).
     * For conduit/pipe/tray: all segments sharing the same system_id.
     */
    ChainResponse getChain(String guid);

    /**
     * Query cost impact of a proposed chain move — side-effect-free (§26.12.3).
     * Returns BOM diff, cost delta, compliance status, new clashes.
     */
    CostOfChangeResponse costOfChange(com.google.gson.JsonObject rawRequest);

    /**
     * Commit a chain move — writes to DB, may spawn R_Request (§26.13).
     * Returns updated positions + optional Change Request.
     */
    MoveChainResponse moveChain(com.google.gson.JsonObject rawRequest);

    /** Element metadata for properties popup. */
    record ElementMetadataResponse(
            boolean success,
            String bomId,
            String name,
            String category,
            Dimensions dimensions,
            String material,
            String constructionSystem,
            String productId,
            Double costUnit,
            String currency,
            java.util.List<ComplianceEntry> compliance,
            String error
    ) {}

    /** Dimensions in mm. */
    record Dimensions(double w, double d, double h) {}

    /** A compliance rule verdict. */
    record ComplianceEntry(String rule, String status, String detail) {}

    /** Chain query result — connected GUIDs. */
    record ChainResponse(
            boolean success,
            java.util.List<String> chainGuids,
            String systemId,
            String error
    ) {}

    /** Cost-of-change result — read-only BOM diff. */
    record CostOfChangeResponse(
            boolean success,
            double materialDeltaM,
            int fittingsDelta,
            double labourHrs,
            double costDelta,
            String currency,
            java.util.List<ComplianceEntry> compliance,
            java.util.List<ClashEntry> newClashes,
            String error
    ) {}

    /** A clash between two elements. */
    record ClashEntry(String guidA, String guidB, String discipline) {}

    /** Move chain result — committed positions + optional CR. */
    record MoveChainResponse(
            boolean success,
            java.util.List<PositionUpdate> updatedPositions,
            double costDelta,
            String currency,
            ChangeRequestInfo changeRequest,
            String error
    ) {}

    /** Updated position for a moved element. */
    record PositionUpdate(String guid, double x, double y, double z) {}

    /** Change Request info (R_Request) — returned when cross-discipline. */
    record ChangeRequestInfo(
            String id,
            java.util.List<String> affectedDisciplines,
            java.util.List<AffectedEngineer> affectedEngineers,
            String approverName,
            double costDelta,
            String currency
    ) {}

    /** An affected engineer from the CR. */
    record AffectedEngineer(String name, String discipline, String email) {}

    // ── 6D Sustainability (TIER1_SRS §1) ────────────────────────────

    /**
     * Rollup embodied carbon for a building from BOM × M_Product.
     * // Implementing TIER1_SRS.md §1.4 — Witness: W-6D-CARBON-4
     */
    CarbonFootprintResponse carbonFootprint(String buildingId);

    /** Carbon footprint response — rollup + breakdown. */
    record CarbonFootprintResponse(
            boolean success,
            double totalCarbonKg,
            double carbonPerSqM,
            double grossFloorAreaSqM,
            java.util.List<CarbonLineInfo> lines,
            java.util.Map<String, Double> byDiscipline,
            java.util.Map<String, Double> byMaterial,
            String error
    ) {}

    record CarbonLineInfo(String bomId, String productName, String material,
                          int qty, double carbonPerUnit, double totalCarbon,
                          String recyclability, String eolStrategy) {}

    // ── 7D Facility Management (TIER1_SRS §2) ──────────────────────

    /**
     * Generate maintenance schedule from BOM × M_Product attributes.
     * // Implementing TIER1_SRS.md §2.4 — Witness: W-7D-FM-5
     */
    MaintenanceScheduleResponse maintenanceSchedule(String buildingId);

    /**
     * Lifecycle cost projection over a given horizon.
     */
    LifecycleCostResponse lifecycleCost(String buildingId, int horizonYears);

    /** Maintenance schedule response. */
    record MaintenanceScheduleResponse(
            boolean success,
            String buildingId,
            int totalAssets,
            double annualMaintenanceEvents,
            java.util.Map<String, Integer> byInterval,
            java.util.List<MaintenanceItemInfo> items,
            String error
    ) {}

    record MaintenanceItemInfo(String bomId, String productName,
                               String location, String discipline,
                               int intervalMonths, int lifespanYears,
                               int qtyInBuilding) {}

    /** Lifecycle cost response. */
    record LifecycleCostResponse(
            boolean success,
            String buildingId,
            double totalReplacementCost,
            java.util.Map<Integer, Double> costByDecade,
            java.util.List<LifecycleItemInfo> items,
            String error
    ) {}

    record LifecycleItemInfo(String productName, String material,
                             int lifespanYears, int qty,
                             double replacementCostUnit,
                             double totalReplacementCost) {}

    // ── Audit Trail (TIER1_SRS §3) ─────────────────────────────────

    /**
     * Get changelog entries for a building.
     * // Implementing TIER1_SRS.md §3.5 — Witness: W-AUDIT-WIRE-1
     */
    ChangelogResponse changelog(String buildingId, int limit);

    /**
     * Undo the most recent N changes.
     */
    UndoResponse undoChanges(String buildingId, int count);

    /** Changelog response. */
    record ChangelogResponse(
            boolean success,
            java.util.List<ChangeEntryInfo> entries,
            String error
    ) {}

    record ChangeEntryInfo(long changelogId, String buildingId,
                           String userId, String timestamp,
                           String action, String entityType,
                           String entityId, String fieldName,
                           String oldValue, String newValue,
                           String bomId) {}

    /** Undo response. */
    record UndoResponse(
            boolean success,
            int reverted,
            String error
    ) {}

    // ── 4D Construction Schedule (CIDB sequence rules) ──────────────

    /**
     * Generate construction schedule from BOM × M_Product sequence rules.
     * Returns Gantt tasks with dates, labor, dependencies.
     */
    ScheduleResponse constructionSchedule(String buildingId, String projectStartDate);

    /** Schedule response — Gantt tasks + summary. */
    record ScheduleResponse(
            boolean success,
            String buildingId,
            String projectStartDate,
            String projectFinishDate,
            int totalDays,
            int totalTasks,
            double totalLaborDays,
            java.util.List<ScheduleTaskInfo> tasks,
            java.util.Map<String, Integer> tasksByPhase,
            java.util.Map<String, Double> laborDaysByResource,
            String error
    ) {}

    record ScheduleTaskInfo(String taskId, String taskName, String phase,
                            String discipline, int sequence, int durationDays,
                            String startDate, String finishDate,
                            String laborResource, int crewSize,
                            String dependency, double qty, String uom,
                            boolean isCritical) {}

    // ── 5D Cost Breakdown (CIDB Malaysia 2024 rates) ────────────────

    /**
     * Full cost breakdown: material + labor + equipment per BOM line.
     * Returns grand total with discipline/phase/resource breakdowns.
     */
    CostBreakdownResponse costBreakdown(String buildingId);

    /** Cost breakdown response — 3-component cost + breakdowns. */
    record CostBreakdownResponse(
            boolean success,
            String buildingId,
            double grandTotal,
            double materialTotal,
            double laborTotal,
            double equipmentTotal,
            double materialPct,
            double laborPct,
            double equipmentPct,
            java.util.List<CostLineInfo> lines,
            java.util.Map<String, Double> byDiscipline,
            java.util.Map<String, Double> byPhase,
            java.util.Map<String, Double> byResource,
            String error
    ) {}

    record CostLineInfo(String bomId, String productName, String discipline,
                        int qty, String uom,
                        double materialCost, double laborCost,
                        double equipmentCost, double totalCost,
                        String laborResource, double laborDays) {}

    // ── Portfolio / Back-Office ─────────────────────────────────────

    /**
     * Cross-project analysis table — all projects with 4D/5D/6D/7D metrics.
     * Back-office view: company sees all projects at a glance.
     */
    PortfolioResponse portfolio();

    /**
     * Kanban board — project cards mapped to status columns.
     */
    KanbanResponse kanban();

    /**
     * Balanced scorecard — four perspectives across the portfolio.
     */
    BalancedScorecardResponse balancedScorecard();

    /** Portfolio analysis table response. */
    record PortfolioResponse(
            boolean success,
            int totalProjects,
            double totalCostRm,
            double totalCarbonKg,
            int totalBomLines,
            double avgCostPerProject,
            double avgCarbonPerSqM,
            java.util.Map<String, Integer> byFacilityType,
            java.util.Map<String, Double> costByFacilityType,
            java.util.List<ProjectRowInfo> projects,
            String error
    ) {}

    record ProjectRowInfo(
            String projectId, String projectName, String facilityType,
            String docStatus, int bomLineCount, int elementCount,
            double materialCostRm, double laborCostRm, double equipmentCostRm,
            double totalCostRm, double carbonKg, double carbonPerSqM,
            int scheduleDays, double laborDays,
            int maintenanceAssets, double annualMaintEvents,
            double lifespanAvgYears
    ) {}

    /** Kanban board response. */
    record KanbanResponse(
            boolean success,
            java.util.List<KanbanCardInfo> cards,
            String error
    ) {}

    record KanbanCardInfo(String projectId, String projectName,
                          String status, double progress,
                          double estCostRm, int bomLines, int blockers) {}

    /** Balanced scorecard response. */
    record BalancedScorecardResponse(
            boolean success,
            java.util.List<KpiInfo> financial,
            java.util.List<KpiInfo> client,
            java.util.List<KpiInfo> process,
            java.util.List<KpiInfo> learning,
            String error
    ) {}

    record KpiInfo(String name, double value, double target,
                   String unit, String status) {}
}
