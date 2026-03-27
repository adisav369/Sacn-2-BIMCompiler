package com.bim.cobol.forge;

import java.util.List;

/**
 * Result of a ForgeEngine computation — geometry records + compliance checks.
 *
 * // Implementing GEOMETRY_FORGE_SRS.md §5.2, §7b (promotable flag)
 */
public record ForgeResult(
    boolean pass,
    boolean promotable,     // true if EYES + compliance all PASS
    String pieceType,
    String summary,
    List<GeometryRecord> records,
    List<String> compliance,  // rule check summaries
    String error
) {
    public static ForgeResult ok(String pieceType, String summary,
            List<GeometryRecord> records, List<String> compliance) {
        boolean allPass = compliance.stream().allMatch(c -> c.contains("PASS"));
        return new ForgeResult(true, allPass, pieceType, summary, records, compliance, null);
    }
    public static ForgeResult fail(String pieceType, String error) {
        return new ForgeResult(false, false, pieceType, null, List.of(), List.of(), error);
    }
}
