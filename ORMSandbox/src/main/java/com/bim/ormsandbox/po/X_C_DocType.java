package com.bim.ormsandbox.po;

import com.bim.orm.BasePO;
import java.sql.Connection;

/**
 * Generated-structure layer for {@code C_DocType} (iDempiere: C_DocType).
 *
 * <p>Document type classification for construction orders. iDempiere BIM uses
 * a single DocType: ConstructionOrder. Building routing is via
 * {@code m_bom.m_product_category_id} (RE/CO/IN/ST) and
 * {@code doc_sub_type} (SH/DX/TE — building prefix, FK to m_bom).
 *
 * <p>W018: DocBaseType + DocSubType dropped. doc_sub_type is the single FK
 * linking C_DocType to m_bom BUILDING records.
 *
 * <p>Table: {@code C_DocType}
 * <pre>
 *   C_DocType_ID   INTEGER PRIMARY KEY   AUTOINCREMENT
 *   Value          TEXT NOT NULL UNIQUE   composite key (e.g. 'RE_SH')
 *   Name           TEXT NOT NULL          human-readable ('Sample House', 'Duplex')
 *   doc_sub_type   TEXT                   FK to m_bom.doc_sub_type (SH, DX, TE; NULL = generic)
 *   IsDefault      INTEGER DEFAULT 0
 *   IsActive       INTEGER DEFAULT 1
 *   Description    TEXT
 *   ProjectName    TEXT                   building instance name ('Duplex')
 *   DSLContent     TEXT                   DSL template text
 *   OutputDbPath   TEXT                   output DB path
 *   ReferenceDbPath TEXT                  reference DB for verification
 *   ExpectedElements INTEGER              expected element count
 *   Provenance     TEXT                   EXTRACTED | GENERATIVE
 *   GeometryFailThreshold INTEGER         fail threshold
 *   SeqNo          INTEGER                compilation ordering
 * </pre>
 *
 * @see <a href="docs/ConstructionAsERP.md">Construction as ERP — §11.36</a>
 */
public class X_C_DocType extends BasePO {

    public static final String Table_Name                    = "C_DocType";
    public static final String COLUMNNAME_C_DocType_ID       = "C_DocType_ID";
    public static final String COLUMNNAME_Name               = "Name";
    public static final String COLUMNNAME_doc_sub_type       = "doc_sub_type";
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

    // ERP dimension columns (Phase H0, 2026-03-09)
    public static final String COLUMNNAME_C_Campaign_ID          = "C_Campaign_ID";
    public static final String COLUMNNAME_SalesRep_ID            = "SalesRep_ID";

    public X_C_DocType(Connection conn) { super(conn); }

    @Override protected String getTableName()    { return Table_Name; }
    @Override protected String getPKColumnName() { return COLUMNNAME_C_DocType_ID; }

    // Classification accessors
    public String  getDocTypeId()    { return get_ValueAsString(COLUMNNAME_C_DocType_ID); }
    public String  getName()         { return get_ValueAsString(COLUMNNAME_Name); }
    public String  getDocSubType()   { return get_ValueAsString(COLUMNNAME_doc_sub_type); }
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
    // ERP dimension accessors
    public String  getCCampaignId()            { return get_ValueAsString(COLUMNNAME_C_Campaign_ID); }
    public int     getSalesRepId()             { return get_ValueAsInt(COLUMNNAME_SalesRep_ID); }

    public void setDocTypeId(String v)   { set_Value(COLUMNNAME_C_DocType_ID, v); }
    public void setName(String v)        { set_Value(COLUMNNAME_Name, v); }
    public void setDocSubType(String v)  { set_Value(COLUMNNAME_doc_sub_type, v); }
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
    public void setCCampaignId(String v)           { set_Value(COLUMNNAME_C_Campaign_ID, v); }
    public void setSalesRepId(int v)               { set_Value(COLUMNNAME_SalesRep_ID, v); }
}
