package com.bim.designer.api;

/**
 * Immutable request for a compilation run.
 *
 * @param buildingId   the C_DocType identifier (e.g. "SampleHouse")
 * @param bomDbPath    path to the {PREFIX}_BOM.db file
 * @param libraryPath  path to component_library.db (default: "library/component_library.db")
 * @param outputDir    directory for compiled output.db (default: "DAGCompiler/lib/output/")
 */
public record CompileRequest(
        String buildingId,
        String bomDbPath,
        String libraryPath,
        String outputDir
) {
    public CompileRequest {
        if (buildingId == null || buildingId.isBlank())
            throw new IllegalArgumentException("buildingId must not be blank");
        if (bomDbPath == null || bomDbPath.isBlank())
            throw new IllegalArgumentException("bomDbPath must not be blank");
        if (libraryPath == null) libraryPath = "library/component_library.db";
        if (outputDir == null) outputDir = "DAGCompiler/lib/output/";
    }
}
