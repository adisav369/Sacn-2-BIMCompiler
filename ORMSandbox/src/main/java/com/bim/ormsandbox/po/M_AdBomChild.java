package com.bim.ormsandbox.po;

import com.bim.orm.ModelQuery;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Model layer for {@code ad_bom_child}.
 * Adds factory methods for BOM child navigation.
 */
public class M_AdBomChild extends X_AdBomChild {

    public M_AdBomChild(Connection conn) { super(conn); }

    /**
     * All active children of a given BOM, ordered by sequence.
     * Matches the query used by BOMAssemblerAD in DAGCompiler.
     */
    public static List<M_AdBomChild> getByBom(Connection conn, String bomId)
            throws SQLException {
        return new ModelQuery<>(conn, M_AdBomChild::new, Table_Name)
            .where(COLUMNNAME_bom_id + " = ?", bomId)
            .andWhere(COLUMNNAME_is_active + " = ?", 1)
            .orderBy(COLUMNNAME_sequence)
            .list();
    }

    /** True if this child references a nested BOM (UNIT/FLOOR/SET chain). */
    public boolean isNestedBom() { return getChildBomId() != null; }

    /** True if this child references a leaf element (geometry via child_name_pattern). */
    public boolean isLeaf() { return getChildBomId() == null && getChildNamePattern() != null; }

    /**
     * Three-table authority: dx/dy/dz are assembly-relative offsets ONLY.
     * rotation_rule is semantic or literal radians.
     */
    public String describeOffset() {
        return String.format("dx=%.3f dy=%.3f dz=%.3f rot=%s",
            getDx(), getDy(), getDz(), getRotationRule());
    }
}
