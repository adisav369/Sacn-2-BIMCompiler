package com.bim.designer.api;

/**
 * Immutable response from a compilation run.
 *
 * @param success        whether the compile completed without fatal errors
 * @param elementCount   number of elements in the compiled output
 * @param compileTimeMs  wall-clock compilation time in milliseconds
 * @param outputDbPath   path to the output.db produced
 * @param spatialDigest  SHA-256 digest of the spatial output (tamper seal)
 * @param error          error message if success is false, null otherwise
 */
public record CompileResponse(
        boolean success,
        int elementCount,
        long compileTimeMs,
        String outputDbPath,
        String spatialDigest,
        String error
) {
    public static CompileResponse success(int elementCount, long compileTimeMs,
                                          String outputDbPath, String spatialDigest) {
        return new CompileResponse(true, elementCount, compileTimeMs,
                outputDbPath, spatialDigest, null);
    }

    public static CompileResponse failure(String error) {
        return new CompileResponse(false, 0, 0, null, null, error);
    }
}
