package com.bim.eyes.proof;

/**
 * Result of a single geometric proof.
 * // Implementing EYES_SRS.md §4 — Witness: W-EYES-PROOF-TIER1
 */
public record ProofResult(
    String proofId,
    Status status,
    String element,
    String evidence,
    double measuredValue
) {
    public enum Status { PROVEN, VIOLATED, SKIPPED }

    public boolean isCritical() {
        return proofId.startsWith("P01") || proofId.startsWith("P02")
            || proofId.startsWith("P03") || proofId.startsWith("P04")
            || proofId.startsWith("P05") || proofId.startsWith("P06")
            || proofId.startsWith("P16") || proofId.startsWith("P17")
            || proofId.startsWith("P22");
    }
}
