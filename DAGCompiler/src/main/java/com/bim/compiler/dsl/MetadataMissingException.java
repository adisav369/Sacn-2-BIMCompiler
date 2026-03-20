package com.bim.compiler.dsl;

/**
 * Thrown when a BUY product has no library geometry (M_Product_Image → component_geometries).
 *
 * FIX: Add a row to M_Product_Image in component_library.db mapping the product_id
 * to a geometry_hash in component_geometries. Run migration_product_image_proper.sql.
 *
 * PRIME RULE: extract or compile only.
 * Every BUY leaf product must have exact library geometry.
 * No boxes, no approximations, no fallback.
 */
public class MetadataMissingException extends RuntimeException {

    public MetadataMissingException(String message) {
        super(message);
    }
}
