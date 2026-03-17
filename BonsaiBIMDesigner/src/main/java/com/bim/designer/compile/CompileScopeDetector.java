package com.bim.designer.compile;

import com.bim.designer.compile.ChangeSet.ChangeType;

import java.util.Set;

/**
 * Maps a {@link ChangeSet} to the minimal pipeline scope that must rerun.
 *
 * <p>Scope table:
 * <pre>
 * Change                  | Stages rerun         | Expected time (TE-scale)
 * ------------------------|----------------------|-------------------------
 * BOM line dx/dy/dz only  | WriteStage (5) only  | ~2s
 * Room furniture swap     | Stages 3-9, 1 storey | ~5s
 * Storey-level change     | Stages 3-9 + neighbors | ~8s
 * YAML or structural      | Full pipeline (1-9)  | ~30s
 * </pre>
 *
 * <p>For SH/DX: full recompile is already &lt;3s, so scope limiting
 * is unnecessary. This detector only adds value at TE-scale.
 */
public class CompileScopeDetector {

    /** Stages that must rerun — bitmask where bit N = stage N. */
    public record Scope(int stageMask, String affectedStorey, String reason) {

        /** Full pipeline: all 9 stages. */
        public static final int FULL = 0x1FF;  // bits 0-8
        /** Write-only: stage 5 (WriteStage). */
        public static final int WRITE_ONLY = 0x020;  // bit 5
        /** Stages 3-9: resolver + placement + write. */
        public static final int RESOLVE_WRITE = 0x1F8;  // bits 3-8

        public boolean isFull() { return stageMask == FULL; }
    }

    /**
     * Determines the minimal recompile scope for the given changes.
     *
     * <p>Rules:
     * <ol>
     *   <li>Any YAML/DSL/BIMCOBOL change → full recompile</li>
     *   <li>BOM_HEADER or PRODUCT → resolve + write (stages 3-9)</li>
     *   <li>BOM_LINE only → write only (stage 5)</li>
     *   <li>ASI → resolve + write (recompute geometry with overrides)</li>
     * </ol>
     */
    public Scope detectScope(ChangeSet changes) {
        Set<ChangeType> types = changes.types();

        // Structural changes → full recompile
        if (types.contains(ChangeType.YAML)
                || types.contains(ChangeType.DSL)
                || types.contains(ChangeType.BIMCOBOL)) {
            return new Scope(Scope.FULL, null, "Structural source change");
        }

        // BOM header or product change → stages 3-9
        if (types.contains(ChangeType.BOM_HEADER)
                || types.contains(ChangeType.PRODUCT)
                || types.contains(ChangeType.ASI)) {
            String storey = detectAffectedStorey(changes);
            return new Scope(Scope.RESOLVE_WRITE, storey,
                    "BOM/product change — resolve + write");
        }

        // BOM line offset only → write stage
        if (types.contains(ChangeType.BOM_LINE)) {
            String storey = detectAffectedStorey(changes);
            return new Scope(Scope.WRITE_ONLY, storey,
                    "BOM line offset — write only");
        }

        // Unknown → full for safety
        return new Scope(Scope.FULL, null, "Unknown change type — full recompile");
    }

    /**
     * Extracts the affected storey from the change paths.
     * Returns null if multiple storeys or indeterminate.
     */
    private String detectAffectedStorey(ChangeSet changes) {
        // TODO: Parse BOM line paths to determine storey scope.
        // For now, returns null (full building scope within the stage mask).
        return null;
    }
}
