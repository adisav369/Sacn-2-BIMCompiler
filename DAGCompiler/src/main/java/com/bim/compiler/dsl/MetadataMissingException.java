package com.bim.compiler.dsl;

/**
 * Thrown when an element reaches the geometry-writing path with no lod_geometry_map entry
 * and no furnitureLibrary LOD match — indicating missing required metadata.
 *
 * FIX: add a row to lod_geometry_map (for reference-extracted elements) or fix
 * m_bom_line.child_name_pattern to use an exact component_definitions.name
 * (for BOM-generated furniture with LIKE wildcards).
 *
 * PRIME RULE: extract, don't imagine.
 * The geometry hash must come from a real component in component_geometries.
 * It must never be invented, approximated, or left to fall back to a bounding box.
 */
public class MetadataMissingException extends RuntimeException {

    public MetadataMissingException(String message) {
        super(message);
    }
}
