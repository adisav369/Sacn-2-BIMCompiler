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
     * Dispatches a single BIM COBOL verb line via VerbRegistry.
     * Returns the verb result as a structured response.
     */
    VerbResponse executeVerb(String buildingId, String verbLine);

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
}
