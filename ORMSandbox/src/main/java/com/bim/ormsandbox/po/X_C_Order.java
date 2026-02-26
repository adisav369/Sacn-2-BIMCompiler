package com.bim.ormsandbox.po;

import com.bim.orm.BasePO;
import java.sql.Connection;

/**
 * Generated-structure layer for {@code c_order}.
 *
 * <p>Table: {@code c_order}
 * <pre>
 *   building_id            TEXT PRIMARY KEY
 *   building_name          TEXT NOT NULL
 *   building_type          TEXT NOT NULL  (RESIDENTIAL|INSTITUTIONAL|COMMERCIAL)
 *   dsl_content            TEXT NOT NULL
 *   output_db_path         TEXT NOT NULL
 *   reference_db_path      TEXT
 *   is_active              INTEGER DEFAULT 1
 *   seq_no                 INTEGER DEFAULT 10
 *   expected_elements      INTEGER
 *   spatial_digest         TEXT
 *   provenance             TEXT DEFAULT 'EXTRACTED'
 *   description            TEXT
 *   geometry_fail_threshold INTEGER DEFAULT 0
 *   doc_status             TEXT DEFAULT 'DR'
 * </pre>
 */
public class X_C_Order extends BasePO {

    public static final String Table_Name                          = "c_order";
    public static final String COLUMNNAME_building_id              = "building_id";
    public static final String COLUMNNAME_building_name            = "building_name";
    public static final String COLUMNNAME_building_type            = "building_type";
    public static final String COLUMNNAME_dsl_content              = "dsl_content";
    public static final String COLUMNNAME_output_db_path           = "output_db_path";
    public static final String COLUMNNAME_reference_db_path        = "reference_db_path";
    public static final String COLUMNNAME_is_active                = "is_active";
    public static final String COLUMNNAME_seq_no                   = "seq_no";
    public static final String COLUMNNAME_expected_elements        = "expected_elements";
    public static final String COLUMNNAME_spatial_digest           = "spatial_digest";
    public static final String COLUMNNAME_provenance               = "provenance";
    public static final String COLUMNNAME_description              = "description";
    public static final String COLUMNNAME_geometry_fail_threshold  = "geometry_fail_threshold";
    public static final String COLUMNNAME_doc_status               = "doc_status";
    public static final String COLUMNNAME_bom_owner                = "bom_owner";

    public X_C_Order(Connection conn) { super(conn); }

    @Override protected String getTableName()    { return Table_Name; }
    @Override protected String getPKColumnName() { return COLUMNNAME_building_id; }

    public String  getBuildingId()            { return get_ValueAsString(COLUMNNAME_building_id); }
    public String  getBuildingName()          { return get_ValueAsString(COLUMNNAME_building_name); }
    public String  getBuildingType()          { return get_ValueAsString(COLUMNNAME_building_type); }
    public String  getDslContent()            { return get_ValueAsString(COLUMNNAME_dsl_content); }
    public String  getOutputDbPath()          { return get_ValueAsString(COLUMNNAME_output_db_path); }
    public String  getReferenceDbPath()       { return get_ValueAsString(COLUMNNAME_reference_db_path); }
    public boolean isActive()                 { return get_ValueAsBoolean(COLUMNNAME_is_active); }
    public int     getSeqNo()                 { return get_ValueAsInt(COLUMNNAME_seq_no); }
    public int     getExpectedElements()      { return get_ValueAsInt(COLUMNNAME_expected_elements); }
    public String  getSpatialDigest()         { return get_ValueAsString(COLUMNNAME_spatial_digest); }
    public String  getProvenance()            { return get_ValueAsString(COLUMNNAME_provenance); }
    public String  getDescription()           { return get_ValueAsString(COLUMNNAME_description); }
    public int     getGeometryFailThreshold() { return get_ValueAsInt(COLUMNNAME_geometry_fail_threshold); }
    public String  getDocStatus()             { return get_ValueAsString(COLUMNNAME_doc_status); }
    public String  getBomOwner()             { return get_ValueAsString(COLUMNNAME_bom_owner); }

    public void setBuildingId(String v)            { set_Value(COLUMNNAME_building_id, v); }
    public void setBuildingName(String v)          { set_Value(COLUMNNAME_building_name, v); }
    public void setBuildingType(String v)          { set_Value(COLUMNNAME_building_type, v); }
    public void setDslContent(String v)            { set_Value(COLUMNNAME_dsl_content, v); }
    public void setOutputDbPath(String v)          { set_Value(COLUMNNAME_output_db_path, v); }
    public void setReferenceDbPath(String v)       { set_Value(COLUMNNAME_reference_db_path, v); }
    public void setIsActive(boolean v)             { set_Value(COLUMNNAME_is_active, v ? 1 : 0); }
    public void setSeqNo(int v)                    { set_Value(COLUMNNAME_seq_no, v); }
    public void setExpectedElements(int v)         { set_Value(COLUMNNAME_expected_elements, v); }
    public void setSpatialDigest(String v)         { set_Value(COLUMNNAME_spatial_digest, v); }
    public void setProvenance(String v)            { set_Value(COLUMNNAME_provenance, v); }
    public void setDescription(String v)           { set_Value(COLUMNNAME_description, v); }
    public void setGeometryFailThreshold(int v)    { set_Value(COLUMNNAME_geometry_fail_threshold, v); }
    public void setDocStatus(String v)             { set_Value(COLUMNNAME_doc_status, v); }
    public void setBomOwner(String v)              { set_Value(COLUMNNAME_bom_owner, v); }
}
