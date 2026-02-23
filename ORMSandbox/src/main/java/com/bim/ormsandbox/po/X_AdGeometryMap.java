package com.bim.ormsandbox.po;

import com.bim.orm.BasePO;
import java.sql.Connection;

/**
 * Generated-structure layer for {@code ad_geometry_map}.
 *
 * <p>Maps element references to geometry hashes in the component library.
 * Used by MeshBinder and other geometry resolvers in DAGCompiler.
 *
 * <p>Table: {@code ad_geometry_map}
 * <pre>
 *   id            INTEGER PRIMARY KEY
 *   building_type TEXT            (NULL = library-wide, not building-specific)
 *   element_ref   TEXT NOT NULL   (Revit family:type or DSL element name)
 *   ifc_class     TEXT NOT NULL   ('IfcFurniture', 'IfcDoor', etc.)
 *   storey        TEXT            (storey name, NULL = all storeys)
 *   ordinal       INTEGER         (instance ordinal; NULL = type-level entry)
 *   geometry_hash TEXT NOT NULL   FK → component_geometries(geometry_hash)
 *   source        TEXT            (free-text origin annotation)
 *   provenance    TEXT DEFAULT 'LIBRARY'
 *                 CHECK(provenance IN ('LIBRARY','EXTRACTED','PARAMETRIC'))
 * </pre>
 *
 * <p>Unique indexes:
 * <ul>
 *   <li>{@code idx_geom_instance} — (building_type, ifc_class, storey, ordinal) WHERE ordinal IS NOT NULL</li>
 *   <li>{@code idx_geom_type}     — (element_ref, ifc_class) WHERE ordinal IS NULL</li>
 * </ul>
 */
public class X_AdGeometryMap extends BasePO {

    public static final String Table_Name                = "ad_geometry_map";
    public static final String COLUMNNAME_id             = "id";
    public static final String COLUMNNAME_building_type  = "building_type";
    public static final String COLUMNNAME_element_ref    = "element_ref";
    public static final String COLUMNNAME_ifc_class      = "ifc_class";
    public static final String COLUMNNAME_storey         = "storey";
    public static final String COLUMNNAME_ordinal        = "ordinal";
    public static final String COLUMNNAME_geometry_hash  = "geometry_hash";
    public static final String COLUMNNAME_source         = "source";
    public static final String COLUMNNAME_provenance     = "provenance";

    public X_AdGeometryMap(Connection conn) { super(conn); }

    @Override protected String getTableName()    { return Table_Name; }
    @Override protected String getPKColumnName() { return COLUMNNAME_id; }

    public int    getId()           { return get_ValueAsInt(COLUMNNAME_id); }
    public String getBuildingType() { return get_ValueAsString(COLUMNNAME_building_type); }
    public String getElementRef()   { return get_ValueAsString(COLUMNNAME_element_ref); }
    public String getIfcClass()     { return get_ValueAsString(COLUMNNAME_ifc_class); }
    public String getStorey()       { return get_ValueAsString(COLUMNNAME_storey); }
    public Integer getOrdinal()     { return (Integer) get_Value(COLUMNNAME_ordinal); }
    public String getGeometryHash() { return get_ValueAsString(COLUMNNAME_geometry_hash); }
    public String getSource()       { return get_ValueAsString(COLUMNNAME_source); }
    public String getProvenance()   { return get_ValueAsString(COLUMNNAME_provenance); }

    public void setBuildingType(String v) { set_Value(COLUMNNAME_building_type, v); }
    public void setElementRef(String v)   { set_Value(COLUMNNAME_element_ref, v); }
    public void setIfcClass(String v)     { set_Value(COLUMNNAME_ifc_class, v); }
    public void setStorey(String v)       { set_Value(COLUMNNAME_storey, v); }
    public void setOrdinal(Integer v)     { set_Value(COLUMNNAME_ordinal, v); }
    public void setGeometryHash(String v) { set_Value(COLUMNNAME_geometry_hash, v); }
    public void setSource(String v)       { set_Value(COLUMNNAME_source, v); }
    public void setProvenance(String v)   { set_Value(COLUMNNAME_provenance, v); }
}
