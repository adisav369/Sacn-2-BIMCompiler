package com.bim.cobol.forge;

import com.bim.cobol.VerbContext;
import java.util.Map;

/**
 * Computes construction piece geometry from parameters.
 * Each implementation handles one piece type (SLOPE_CUT, STAIR_FLIGHT, etc.).
 *
 * // Implementing GEOMETRY_FORGE_SRS.md §5.2
 */
public interface ForgeEngine {
    /** Piece type this engine handles (e.g., "SLOPE_CUT"). */
    String pieceType();

    /** Compute geometry from parameters. */
    ForgeResult compute(VerbContext ctx, Map<String, String> params);
}
