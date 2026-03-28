package com.bim.compiler.compliance;

import java.util.List;

/**
 * Immutable compliance report — output of ComplianceStage (Step 11).
 *
 * <p>Packages validation results into a proof chain with per-rule verdicts.
 * Overall result: PASS (all mandatory pass), BLOCK (any mandatory fail),
 * PARTIAL (warnings only, no blocks).
 *
 * // Implementing STANDARDS_COMPLIANCE_SRS.md §5.2 — Witness: W-SC-REPORT
 */
public record ComplianceReport(
    String buildingId,
    String jurisdiction,        // MY, US, UK, SG
    String codeEdition,         // UBBL_1984_AMD2007
    String spatialDigest,       // from DigestStage
    String certificateId,       // SC-{building}-{date}, null if BLOCK
    String overallResult,       // PASS, BLOCK, PARTIAL
    int totalRules,
    int passCount,
    int blockCount,
    int warnCount,
    int skipCount,
    List<ProofLine> proofLines
) {
    public boolean passed() { return "PASS".equals(overallResult); }

    /**
     * One proof line — maps to SC_Proof_Line row in compliance_proof.db.
     */
    public record ProofLine(
        String spaceId,             // element GUID or 'BUILDING'
        int ruleId,                 // AD_DocEvent_Rule_ID
        String ruleCode,            // e.g. 'UBBL_S43_1_ROOM_AREA'
        String statuteRef,          // e.g. 'UBBL 1984 s.43(1)'
        String clauseText,          // human-readable
        double measuredValue,
        String measuredUnit,        // mm, m2, count, ratio
        double thresholdValue,
        String thresholdDir,        // MIN or MAX
        String result,              // PASS, BLOCK, WARN, SKIP
        double margin,              // measured - threshold (signed)
        String proofWitness,        // W-SC-* witness ID
        String skipReason           // upstream failure reason, nullable
    ) {}
}
