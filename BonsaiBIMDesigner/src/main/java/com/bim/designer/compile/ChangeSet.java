package com.bim.designer.compile;

import java.util.List;
import java.util.Set;

/**
 * Typed change descriptor — maps what changed to which pipeline stages
 * need rerunning.
 *
 * <p>Used by {@link CompileScopeDetector} to determine the minimal
 * recompile scope.
 *
 * @param types         set of change types detected
 * @param affectedPaths file paths or DB rows that changed
 */
public record ChangeSet(Set<ChangeType> types, List<String> affectedPaths) {

    /**
     * What kind of source artifact changed.
     *
     * <p>Scope mapping (see CompileScopeDetector):
     * <ul>
     *   <li>BOM_LINE dx/dy/dz only → WriteStage (5) only → ~2s</li>
     *   <li>BOM_LINE or BOM_HEADER (room swap) → Stages 3-9, one storey → ~5s</li>
     *   <li>PRODUCT or ASI → Stages 3-9, storey + neighbors → ~8s</li>
     *   <li>YAML or DSL or BIMCOBOL → Full pipeline (1-9) → ~30s</li>
     * </ul>
     *
     * <p>For SH/DX-scale buildings, full recompile is already &lt;3s,
     * so scope limiting only matters at TE-scale (48K elements).
     */
    public enum ChangeType {
        /** classify_*.yaml change — full recompile */
        YAML,
        /** m_bom_line offset/quantity change */
        BOM_LINE,
        /** m_bom header change (new BOM, BOM delete) */
        BOM_HEADER,
        /** M_Product change in component_library.db */
        PRODUCT,
        /** M_AttributeSetInstance change */
        ASI,
        /** dsl_*.bim file change */
        DSL,
        /** *.bimcobol verb script change */
        BIMCOBOL
    }
}
