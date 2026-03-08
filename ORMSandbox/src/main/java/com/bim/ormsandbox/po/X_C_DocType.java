package com.bim.ormsandbox.po;

import com.bim.orm.BasePO;
import java.sql.Connection;

/**
 * Generated-structure layer for {@code C_DocType} (iDempiere: C_DocType).
 *
 * <p>Document type classification for construction orders. Borrowed from
 * iDempiere's C_DocType model: DocBaseType (3-char category) + DocSubType
 * (variant within category).
 *
 * <p>Replaces the dual c_order.building_type + c_order.c_bpartner with a
 * single FK. DocBaseType drives template selection (RE → Residential template).
 * DocSubType drives BOM scoping (SH BOMs for SH buildings).
 *
 * <p>Table: {@code C_DocType}
 * <pre>
 *   C_DocType_ID   TEXT PRIMARY KEY     composite: DocBaseType + '_' + DocSubType
 *   Name           TEXT NOT NULL         human-readable ('Sample House', 'Duplex')
 *   DocBaseType    TEXT NOT NULL          RE (Residential), CO (Commercial), IN (Industrial)
 *   DocSubType     TEXT                   SH, DX, TB, TE, ST (NULL = generic)
 *   IsDefault      INTEGER DEFAULT 0     default DocType for this DocBaseType
 *   IsActive       INTEGER DEFAULT 1
 *   Description    TEXT
 *   -- Domain config (absorbed from c_order, 2026-03-04):
 *   ProjectName    TEXT                   building instance name ('Ifc2x3_Duplex')
 *   DSLContent     TEXT                   DSL template text
 *   OutputDbPath   TEXT                   output DB path
 *   ReferenceDbPath TEXT                  reference DB for verification
 *   ExpectedElements INTEGER              expected element count
 *   Provenance     TEXT                   EXTRACTED | GENERATIVE
 *   GeometryFailThreshold INTEGER         fail threshold
 *   SeqNo          INTEGER                compilation ordering
 *   AabbWidthMm    REAL                   standard domain AABB width
 *   AabbDepthMm    REAL                   standard domain AABB depth
 *   AabbHeightMm   REAL                   standard domain AABB height
 *   -- Note: C_Order in output.db also has AABB (arbitrary per build)
 * </pre>
 *
 * @see <a href="docs/ConstructionAsERP.md">Construction as ERP — §11.36</a>
 */
public class X_C_DocType extends BasePO {

    public static final String Table_Name                    = "C_DocType";
    public static final String COLUMNNAME_C_DocType_ID       = "C_DocType_ID";
    public static final String COLUMNNAME_Name               = "Name";
    public static final String COLUMNNAME_DocBaseType        = "DocBaseType";
    public static final String COLUMNNAME_DocSubType         = "DocSubType";
    public static final String COLUMNNAME_IsDefault          = "IsDefault";
    public static final String COLUMNNAME_IsActive           = "IsActive";
    public static final String COLUMNNAME_Description        = "Description";

    // Domain config columns (absorbed from c_order, 2026-03-04)
    public static final String COLUMNNAME_ProjectName            = "ProjectName";
    public static final String COLUMNNAME_DSLContent             = "DSLContent";
    public static final String COLUMNNAME_OutputDbPath           = "OutputDbPath";
    public static final String COLUMNNAME_ReferenceDbPath        = "ReferenceDbPath";
    public static final String COLUMNNAME_ExpectedElements       = "ExpectedElements";
    public static final String COLUMNNAME_Provenance             = "Provenance";
    public static final String COLUMNNAME_GeometryFailThreshold  = "GeometryFailThreshold";
    public static final String COLUMNNAME_SeqNo                  = "SeqNo";
    public static final String COLUMNNAME_AabbWidthMm            = "AabbWidthMm";
    public static final String COLUMNNAME_AabbDepthMm            = "AabbDepthMm";
    public static final String COLUMNNAME_AabbHeightMm           = "AabbHeightMm";

    // ERP dimension columns (Phase H0, 2026-03-09)
    public static final String COLUMNNAME_C_Campaign_ID          = "C_Campaign_ID";
    public static final String COLUMNNAME_SalesRep_ID            = "SalesRep_ID";

    public X_C_DocType(Connection conn) { super(conn); }

    @Override protected String getTableName()    { return Table_Name; }
    @Override protected String getPKColumnName() { return COLUMNNAME_C_DocType_ID; }

    // Classification accessors
    public String  getDocTypeId()    { return get_ValueAsString(COLUMNNAME_C_DocType_ID); }
    public String  getName()         { return get_ValueAsString(COLUMNNAME_Name); }
    public String  getDocBaseType()  { return get_ValueAsString(COLUMNNAME_DocBaseType); }
    public String  getDocSubType()   { return get_ValueAsString(COLUMNNAME_DocSubType); }
    public boolean isDefault()       { return get_ValueAsBoolean(COLUMNNAME_IsDefault); }
    public boolean isActive()        { return get_ValueAsBoolean(COLUMNNAME_IsActive); }
    public String  getDescription()  { return get_ValueAsString(COLUMNNAME_Description); }

    // Domain config accessors
    public String  getProjectName()           { return get_ValueAsString(COLUMNNAME_ProjectName); }
    public String  getDSLContent()            { return get_ValueAsString(COLUMNNAME_DSLContent); }
    public String  getOutputDbPath()          { return get_ValueAsString(COLUMNNAME_OutputDbPath); }
    public String  getReferenceDbPath()       { return get_ValueAsString(COLUMNNAME_ReferenceDbPath); }
    public int     getExpectedElements()      { return get_ValueAsInt(COLUMNNAME_ExpectedElements); }
    public String  getProvenance()            { return get_ValueAsString(COLUMNNAME_Provenance); }
    public int     getGeometryFailThreshold() { return get_ValueAsInt(COLUMNNAME_GeometryFailThreshold); }
    public int     getSeqNo()                 { return get_ValueAsInt(COLUMNNAME_SeqNo); }
    public double  getAabbWidthMm()           { return get_ValueAsDouble(COLUMNNAME_AabbWidthMm); }
    public double  getAabbDepthMm()           { return get_ValueAsDouble(COLUMNNAME_AabbDepthMm); }
    public double  getAabbHeightMm()          { return get_ValueAsDouble(COLUMNNAME_AabbHeightMm); }

    // ERP dimension accessors
    public String  getCCampaignId()            { return get_ValueAsString(COLUMNNAME_C_Campaign_ID); }
    public int     getSalesRepId()             { return get_ValueAsInt(COLUMNNAME_SalesRep_ID); }

    public void setDocTypeId(String v)   { set_Value(COLUMNNAME_C_DocType_ID, v); }
    public void setName(String v)        { set_Value(COLUMNNAME_Name, v); }
    public void setDocBaseType(String v) { set_Value(COLUMNNAME_DocBaseType, v); }
    public void setDocSubType(String v)  { set_Value(COLUMNNAME_DocSubType, v); }
    public void setIsDefault(boolean v)  { set_Value(COLUMNNAME_IsDefault, v ? 1 : 0); }
    public void setIsActive(boolean v)   { set_Value(COLUMNNAME_IsActive, v ? 1 : 0); }
    public void setDescription(String v) { set_Value(COLUMNNAME_Description, v); }

    public void setProjectName(String v)           { set_Value(COLUMNNAME_ProjectName, v); }
    public void setDSLContent(String v)            { set_Value(COLUMNNAME_DSLContent, v); }
    public void setOutputDbPath(String v)          { set_Value(COLUMNNAME_OutputDbPath, v); }
    public void setReferenceDbPath(String v)       { set_Value(COLUMNNAME_ReferenceDbPath, v); }
    public void setExpectedElements(int v)         { set_Value(COLUMNNAME_ExpectedElements, v); }
    public void setProvenance(String v)            { set_Value(COLUMNNAME_Provenance, v); }
    public void setGeometryFailThreshold(int v)    { set_Value(COLUMNNAME_GeometryFailThreshold, v); }
    public void setSeqNo(int v)                    { set_Value(COLUMNNAME_SeqNo, v); }
    public void setAabbWidthMm(double v)           { set_Value(COLUMNNAME_AabbWidthMm, v); }
    public void setAabbDepthMm(double v)           { set_Value(COLUMNNAME_AabbDepthMm, v); }
    public void setAabbHeightMm(double v)          { set_Value(COLUMNNAME_AabbHeightMm, v); }

    public void setCCampaignId(String v)           { set_Value(COLUMNNAME_C_Campaign_ID, v); }
    public void setSalesRepId(int v)               { set_Value(COLUMNNAME_SalesRep_ID, v); }
}
