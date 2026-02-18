package com.bim.compiler.dsl;

/**
 * Tracks where an element's mesh geometry originated.
 * Used by BoundElement to record provenance for debugging
 * and future validation differentiation.
 */
public enum GeometryProvenance {
    /** Mesh extracted from same IFC as placement (SH, DX) */
    EXTRACTED,
    /** Mesh from component_library.db, possibly scaled (generative) */
    LIBRARY,
    /** Mesh computed parametrically from placement dimensions (box fallback) */
    PARAMETRIC
}
