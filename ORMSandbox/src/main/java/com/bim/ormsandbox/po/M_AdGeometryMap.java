package com.bim.ormsandbox.po;

import com.bim.orm.ModelQuery;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Model layer for {@code ad_geometry_map}.
 * Factory methods for geometry coverage analysis — used by preflight checks.
 *
 * <p>Key constraint: geometry_hash REFERENCES component_geometries(geometry_hash).
 * FK prevents orphans at INSERT time; {@link #getOrphans} is a defensive audit.
 */
public class M_AdGeometryMap extends X_AdGeometryMap {

    public M_AdGeometryMap(Connection conn) { super(conn); }

    /**
     * All geometry map entries for a building, ordered by element_ref.
     * Includes both building-specific rows (building_type = arg) and
     * library-wide rows (building_type IS NULL) — use for coverage analysis.
     */
    public static List<M_AdGeometryMap> getByBuilding(Connection conn, String buildingType)
            throws SQLException {
        return new ModelQuery<>(conn, M_AdGeometryMap::new, Table_Name)
            .where(COLUMNNAME_building_type + " = ?", buildingType)
            .orderBy(COLUMNNAME_element_ref)
            .list();
    }

    /**
     * Resolve the first geometry map entry for a specific building + element_ref.
     * Returns null if no entry exists.
     */
    public static M_AdGeometryMap getByElementRef(Connection conn,
                                                   String buildingType, String elementRef)
            throws SQLException {
        return new ModelQuery<>(conn, M_AdGeometryMap::new, Table_Name)
            .where(COLUMNNAME_building_type + " = ?", buildingType)
            .andWhere(COLUMNNAME_element_ref + " = ?", elementRef)
            .first()
            .orElse(null);
    }

    /**
     * Entries where geometry_hash is NOT in component_geometries (dangling FK).
     * The FK constraint prevents these at INSERT time — this is a defensive audit check.
     * Normally returns an empty list.
     */
    public static List<M_AdGeometryMap> getOrphans(Connection conn, String buildingType)
            throws SQLException {
        return new ModelQuery<>(conn, M_AdGeometryMap::new, Table_Name)
            .where(COLUMNNAME_building_type + " = ?", buildingType)
            .andWhere("NOT EXISTS (SELECT 1 FROM component_geometries cg"
                    + " WHERE cg.geometry_hash = " + Table_Name + ".geometry_hash)")
            .list();
    }
}
