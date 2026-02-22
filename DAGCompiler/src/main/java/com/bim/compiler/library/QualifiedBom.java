package com.bim.compiler.library;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * View-mapped record for v_qualified_bom.
 * width_mm/depth_mm/height_mm sourced from ad_product_dim.width/depth/height —
 * note: ad_product_dim stores values in metres despite the _mm alias in the view.
 */
public record QualifiedBom(
    String bomId,
    String bomName,
    String bomType,
    int    bomChildId,
    String role,
    int    sequence,
    int    fitPriority,
    int    minSpaceMm,
    String childNamePattern,
    double widthMm,
    double depthMm,
    double heightMm,
    String extractedFrom
) {
    public static QualifiedBom from(ResultSet rs) throws SQLException {
        return new QualifiedBom(
            rs.getString("bom_id"),
            rs.getString("bom_name"),
            rs.getString("bom_type"),
            rs.getInt("bom_child_id"),
            rs.getString("role"),
            rs.getInt("sequence"),
            rs.getInt("fit_priority"),
            rs.getInt("min_space_mm"),
            rs.getString("child_name_pattern"),
            rs.getDouble("width_mm"),
            rs.getDouble("depth_mm"),
            rs.getDouble("height_mm"),
            rs.getString("extracted_from")
        );
    }
}
