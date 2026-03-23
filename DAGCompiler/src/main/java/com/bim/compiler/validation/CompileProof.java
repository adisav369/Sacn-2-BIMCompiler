package com.bim.compiler.validation;

import com.bim.eyes.proof.ProofResult;
import com.bim.compiler.util.BIMLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Abstract base for compile-output verification proofs.
 * // Implementing DISC_VALIDATION_DB_SRS.md §12 — Witness: W-DM-TC5-3
 *
 * <p>Three emission modes, same proof logic:
 * <ul>
 *   <li><b>TEST</b> — JUnit assertion on ProofResult (fails build)</li>
 *   <li><b>PIPELINE_LOG</b> — BIMLogger.fine() with structured breakdown (non-blocking)</li>
 *   <li><b>ADVISORY</b> — INSERT into W_Validation_Advisory (non-blocking)</li>
 * </ul>
 *
 * @see com.bim.eyes.proof.ProofResult
 */
public abstract class CompileProof {

    /** Identity */
    public abstract String name();
    public abstract String description();

    /** The proof — query output DB, evaluate result. */
    public abstract ProofResult evaluate(Connection outputDb) throws SQLException;

    /** Where this proof can run. Default: all three contexts. */
    public enum Context { TEST, PIPELINE_LOG, ADVISORY }

    public Set<Context> contexts() {
        return EnumSet.allOf(Context.class);
    }

    /**
     * Emit proof result to PIPELINE_LOG (BIMLogger.fine).
     * Called by post-compile hook when Context.PIPELINE_LOG is enabled.
     */
    public void emitLog(ProofResult result) {
        String status = result.status() == ProofResult.Status.PROVEN ? "PASS" : "FAIL";
        BIMLogger.fine("COMPILE_PROOF",
            "[{}] {} — {} | {}",
            status, name(), result.evidence(), result.element());
    }

    /**
     * Emit proof result to W_Validation_Advisory.
     * Called by post-compile hook when Context.ADVISORY is enabled.
     */
    public void emitAdvisory(Connection outputDb, ProofResult result) throws SQLException {
        String sql = "INSERT OR IGNORE INTO W_Validation_Advisory "
                   + "(proof_name, status, element, evidence, layer) "
                   + "VALUES (?, ?, ?, ?, 'COMPILE')";
        try (PreparedStatement ps = outputDb.prepareStatement(sql)) {
            ps.setString(1, name());
            ps.setString(2, result.status().name());
            ps.setString(3, result.element());
            ps.setString(4, result.evidence());
            ps.executeUpdate();
        } catch (SQLException e) {
            // W_Validation_Advisory may not exist — non-fatal
            if (!e.getMessage().contains("no such table")) throw e;
        }
    }

    /**
     * Run all provided proofs against an output DB, emitting results per context.
     *
     * @param proofs    compile proofs to run
     * @param outputDb  connection to the compiled output.db
     * @param contexts  which emission modes to use
     * @return all ProofResults
     */
    public static List<ProofResult> runAll(List<CompileProof> proofs, Connection outputDb,
                                            Set<Context> contexts) throws SQLException {
        List<ProofResult> results = new java.util.ArrayList<>();
        for (CompileProof proof : proofs) {
            Set<Context> active = EnumSet.copyOf(proof.contexts());
            active.retainAll(contexts);

            ProofResult result = proof.evaluate(outputDb);
            results.add(result);

            if (active.contains(Context.PIPELINE_LOG)) {
                proof.emitLog(result);
            }
            if (active.contains(Context.ADVISORY)) {
                proof.emitAdvisory(outputDb, result);
            }
        }
        return results;
    }
}
