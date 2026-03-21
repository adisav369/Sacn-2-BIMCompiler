package com.bim.eyes.proof;

import java.util.List;

/**
 * Aggregate report of all proofs.
 * // Implementing EYES_SRS.md §4 — Witness: W-EYES-PROOF-TIER1
 */
public record ProofReport(
    List<ProofResult> results,
    int proven,
    int violated,
    int skipped
) {
    public boolean allCriticalProven() {
        return results.stream()
            .filter(r -> r.isCritical() && r.status() == ProofResult.Status.VIOLATED)
            .findAny().isEmpty();
    }

    public int criticalViolations() {
        return (int) results.stream()
            .filter(r -> r.isCritical() && r.status() == ProofResult.Status.VIOLATED)
            .count();
    }
}
