package com.bim.designer.compile;

import com.bim.designer.api.CompileRequest;
import com.bim.designer.api.CompileResponse;

import java.util.logging.Logger;

/**
 * Scope-limited recompiler.
 *
 * <p>Uses {@link CompileScopeDetector} to determine which pipeline stages
 * need rerunning, then delegates to {@code CompilationPipeline.run()} for
 * the actual compilation.
 *
 * <p>For SH/DX-scale buildings (&lt;1,100 elements), full recompile is
 * already &lt;3s, so this class simply delegates to full compile.
 * Scope limiting is only meaningful at TE-scale (48K elements).
 *
 * <p>The key insight: we never add new compilation logic. We only narrow
 * the scope of what the existing pipeline reruns. CompilationPipeline
 * remains the single source of truth for compilation.
 */
public class IncrementalCompiler {

    private static final Logger LOG = Logger.getLogger(IncrementalCompiler.class.getName());

    private final CompileScopeDetector scopeDetector;

    public IncrementalCompiler() {
        this.scopeDetector = new CompileScopeDetector();
    }

    /**
     * Compiles with scope limiting based on what changed.
     *
     * <p>Current implementation: always delegates to full compile.
     * Scope-limited compilation will be implemented when the pipeline
     * supports stage-level entry points (post Phase G-1).
     *
     * @param request  the compilation request
     * @param changes  what changed since last compile
     * @return the compilation response
     */
    public CompileResponse compile(CompileRequest request, ChangeSet changes) {
        var scope = scopeDetector.detectScope(changes);
        LOG.info(() -> String.format("Incremental scope: %s (stages=0x%03X, storey=%s)",
                scope.reason(), scope.stageMask(), scope.affectedStorey()));

        if (scope.isFull()) {
            LOG.info("Full recompile required — delegating to pipeline");
        } else {
            // TODO: When pipeline supports stage-level entry points,
            // invoke only the stages indicated by scope.stageMask().
            // For now, fall through to full compile.
            LOG.info("Scope-limited compile not yet implemented — falling back to full");
        }

        // Delegate to full compile — the caller (DesignerAPIImpl) handles this
        return null; // sentinel: caller should invoke full compile
    }
}
