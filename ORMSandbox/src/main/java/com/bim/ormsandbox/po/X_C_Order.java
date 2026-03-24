package com.bim.ormsandbox.po;

import com.bim.orm.BasePO;
import java.sql.Connection;

/**
 * Generated-structure layer for {@code c_order}.
 *
 * <p>Table: {@code c_order} — iDempiere CamelCase naming
 * <pre>
 *   C_Order_ID             TEXT PRIMARY KEY     -- project config identifier
 *   Name                   TEXT NOT NULL         -- display name
 *   DSLContent             TEXT NOT NULL         -- full DSL text (opaque manifest)
 *   OutputDbPath           TEXT NOT NULL         -- relative to runtime base dir
 *   ReferenceDbPath        TEXT                  -- NULL for generative buildings
 *   IsActive               INTEGER DEFAULT 1
 *   SeqNo                  INTEGER DEFAULT 10
 *   ExpectedElements       INTEGER
 *   SpatialDigest          TEXT                  -- SHA256 spatial fingerprint
 *   Provenance             TEXT DEFAULT 'EXTRACTED'  (EXTRACTED|GENERATIVE)
 *   Description            TEXT
 *   GeometryFailThreshold  INTEGER DEFAULT 0
 *   DocStatus              TEXT DEFAULT 'DR' CHECK(DR|IP|CO|VO)
 *   C_BPartner_ID          TEXT                  -- doc sub-type (SH|DX|TB|TE|ST)
 *   C_DocType_ID           TEXT                  -- FK → C_DocType (RE_SH|RE_DX|RE_TB|CO_TE|ST_SH|ST_DX)
 *   AabbWidthMm            REAL                  -- building envelope width in mm
 *   AabbDepthMm            REAL                  -- building envelope depth in mm
 *   AabbHeightMm           REAL                  -- building envelope height in mm
 *   Ref_Order_ID           TEXT                  -- FK to parent C_Order (NULL = base order)
 * </pre>
 */
public class X_C_Order extends BasePO {

    public static final String Table_Name                          = "c_order";
    public static final String COLUMNNAME_C_Order_ID               = "C_Order_ID";
    public static final String COLUMNNAME_Name                     = "Name";
    public static final String COLUMNNAME_DSLContent               = "DSLContent";
    public static final String COLUMNNAME_OutputDbPath             = "OutputDbPath";
    public static final String COLUMNNAME_ReferenceDbPath          = "ReferenceDbPath";
    public static final String COLUMNNAME_IsActive                 = "IsActive";
    public static final String COLUMNNAME_SeqNo                    = "SeqNo";
    public static final String COLUMNNAME_ExpectedElements         = "ExpectedElements";
    public static final String COLUMNNAME_SpatialDigest            = "SpatialDigest";
    public static final String COLUMNNAME_Provenance               = "Provenance";
    public static final String COLUMNNAME_Description              = "Description";
    public static final String COLUMNNAME_GeometryFailThreshold    = "GeometryFailThreshold";
    public static final String COLUMNNAME_DocStatus                = "DocStatus";
    public static final String COLUMNNAME_C_BPartner_ID            = "C_BPartner_ID";
    public static final String COLUMNNAME_AabbWidthMm              = "AabbWidthMm";
    public static final String COLUMNNAME_AabbDepthMm              = "AabbDepthMm";
    public static final String COLUMNNAME_AabbHeightMm             = "AabbHeightMm";
    public static final String COLUMNNAME_C_DocType_ID             = "C_DocType_ID";
    public static final String COLUMNNAME_Ref_Order_ID             = "Ref_Order_ID";

    public X_C_Order(Connection conn) { super(conn); }

    @Override protected String getTableName()    { return Table_Name; }
    @Override protected String getPKColumnName() { return COLUMNNAME_C_Order_ID; }

    public String  getCOrderId()              { return get_ValueAsString(COLUMNNAME_C_Order_ID); }
    public String  getName()                  { return get_ValueAsString(COLUMNNAME_Name); }
    public String  getDSLContent()            { return get_ValueAsString(COLUMNNAME_DSLContent); }
    public String  getOutputDbPath()          { return get_ValueAsString(COLUMNNAME_OutputDbPath); }
    public String  getReferenceDbPath()       { return get_ValueAsString(COLUMNNAME_ReferenceDbPath); }
    public boolean isActive()                 { return get_ValueAsBoolean(COLUMNNAME_IsActive); }
    public int     getSeqNo()                 { return get_ValueAsInt(COLUMNNAME_SeqNo); }
    public int     getExpectedElements()      { return get_ValueAsInt(COLUMNNAME_ExpectedElements); }
    public String  getSpatialDigest()         { return get_ValueAsString(COLUMNNAME_SpatialDigest); }
    public String  getProvenance()            { return get_ValueAsString(COLUMNNAME_Provenance); }
    public String  getDescription()           { return get_ValueAsString(COLUMNNAME_Description); }
    public int     getGeometryFailThreshold() { return get_ValueAsInt(COLUMNNAME_GeometryFailThreshold); }
    public String  getDocStatus()             { return get_ValueAsString(COLUMNNAME_DocStatus); }
    public String  getCBPartnerId()           { return get_ValueAsString(COLUMNNAME_C_BPartner_ID); }
    public double  getAabbWidthMm()           { return get_ValueAsDouble(COLUMNNAME_AabbWidthMm); }
    public double  getAabbDepthMm()           { return get_ValueAsDouble(COLUMNNAME_AabbDepthMm); }
    public double  getAabbHeightMm()          { return get_ValueAsDouble(COLUMNNAME_AabbHeightMm); }
    public String  getDocTypeId()             { return get_ValueAsString(COLUMNNAME_C_DocType_ID); }
    public String  getRefOrderId()            { return get_ValueAsString(COLUMNNAME_Ref_Order_ID); }

    public void setCOrderId(String v)              { set_Value(COLUMNNAME_C_Order_ID, v); }
    public void setName(String v)                  { set_Value(COLUMNNAME_Name, v); }
    public void setDSLContent(String v)            { set_Value(COLUMNNAME_DSLContent, v); }
    public void setOutputDbPath(String v)          { set_Value(COLUMNNAME_OutputDbPath, v); }
    public void setReferenceDbPath(String v)       { set_Value(COLUMNNAME_ReferenceDbPath, v); }
    public void setIsActive(boolean v)             { set_Value(COLUMNNAME_IsActive, v ? 1 : 0); }
    public void setSeqNo(int v)                    { set_Value(COLUMNNAME_SeqNo, v); }
    public void setExpectedElements(int v)         { set_Value(COLUMNNAME_ExpectedElements, v); }
    public void setSpatialDigest(String v)         { set_Value(COLUMNNAME_SpatialDigest, v); }
    public void setProvenance(String v)            { set_Value(COLUMNNAME_Provenance, v); }
    public void setDescription(String v)           { set_Value(COLUMNNAME_Description, v); }
    public void setGeometryFailThreshold(int v)    { set_Value(COLUMNNAME_GeometryFailThreshold, v); }
    public void setDocStatus(String v)             { set_Value(COLUMNNAME_DocStatus, v); }
    public void setCBPartnerId(String v)           { set_Value(COLUMNNAME_C_BPartner_ID, v); }
    public void setAabbWidthMm(double v)           { set_Value(COLUMNNAME_AabbWidthMm, v); }
    public void setAabbDepthMm(double v)           { set_Value(COLUMNNAME_AabbDepthMm, v); }
    public void setAabbHeightMm(double v)          { set_Value(COLUMNNAME_AabbHeightMm, v); }
    public void setDocTypeId(String v)             { set_Value(COLUMNNAME_C_DocType_ID, v); }
    public void setRefOrderId(String v)            { set_Value(COLUMNNAME_Ref_Order_ID, v); }
}
