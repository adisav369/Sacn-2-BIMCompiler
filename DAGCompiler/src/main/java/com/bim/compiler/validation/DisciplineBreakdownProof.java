package com.bim.compiler.validation;

import com.bim.eyes.proof.ProofResult;
import com.bim.compiler.util.BIMLogger;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Compile proof: discipline breakdown of compiled output.
 * // Implementing DISC_VALIDATION_DB_SRS.md §12.4 — Witness: W-DM-TC5-1, W-DM-TC5-3
 *
 * <p>Extracted from DemoHouseCompileTest W-DM-TC5-1 (FP) and W-DM-TC5-3 (ELEC).
 * Queries elements_meta GROUP BY discipline and verifies:
 * <ul>
 *   <li>At least {@code minDisciplines} distinct disciplines</li>
 *   <li>Total element count matches {@code expectedTotal} (if > 0)</li>
 * </ul>
 */
public class DisciplineBreakdownProof extends CompileProof {

    private final int minDisciplines;
    private final int expectedTotal;

    public DisciplineBreakdownProof(int minDisciplines, int expectedTotal) {
        this.minDisciplines = minDisciplines;
        this.expectedTotal = expectedTotal;
    }

    public DisciplineBreakdownProof() {
        this(1, 0);
    }

    @Override public String name() { return "DISCIPLINE_BREAKDOWN"; }
    @Override public String description() { return "Output has expected discipline distribution"; }

    @Override
    public ProofResult evaluate(Connection outputDb) throws SQLException {
        Map<String, Integer> counts = new LinkedHashMap<>();
        try (Statement st = outputDb.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT discipline, COUNT(*) AS cnt FROM elements_meta "
               + "GROUP BY discipline ORDER BY cnt DESC")) {
            while (rs.next()) {
                counts.put(rs.getString("discipline"), rs.getInt("cnt"));
            }
        }

        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        StringJoiner sj = new StringJoiner(", ");
        counts.forEach((d, c) -> sj.add(d + "=" + c));
        String evidence = sj + " (total=" + total + ")";

        if (counts.size() < minDisciplines) {
            return new ProofResult(name(), ProofResult.Status.VIOLATED,
                "disciplines=" + counts.size(),
                "Expected ≥" + minDisciplines + " disciplines, got " + counts.size() + ": " + evidence,
                counts.size());
        }
        if (expectedTotal > 0 && total != expectedTotal) {
            return new ProofResult(name(), ProofResult.Status.VIOLATED,
                "total=" + total,
                "Expected " + expectedTotal + " elements, got " + total + ": " + evidence,
                total);
        }
        return new ProofResult(name(), ProofResult.Status.PROVEN,
            "disciplines=" + counts.size(),
            evidence, counts.size());
    }

    @Override
    public void emitLog(ProofResult result) {
        String status = result.status() == ProofResult.Status.PROVEN ? "PASS" : "FAIL";
        BIMLogger.fine("COMPILE_PROOF",
            "[{}] DISCIPLINE_BREAKDOWN — {}", status, result.evidence());
    }

    /** Convenience: get the discipline→count map from a fresh evaluation. */
    public Map<String, Integer> breakdown(Connection outputDb) throws SQLException {
        Map<String, Integer> counts = new LinkedHashMap<>();
        try (Statement st = outputDb.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT discipline, COUNT(*) AS cnt FROM elements_meta "
               + "GROUP BY discipline ORDER BY cnt DESC")) {
            while (rs.next()) {
                counts.put(rs.getString("discipline"), rs.getInt("cnt"));
            }
        }
        return counts;
    }
}
