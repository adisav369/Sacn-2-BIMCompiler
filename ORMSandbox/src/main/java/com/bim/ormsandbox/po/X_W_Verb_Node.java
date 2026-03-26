package com.bim.ormsandbox.po;

import com.bim.orm.BasePO;
import java.sql.Connection;

/**
 * PO layer for {@code W_Verb_Node} — HOW to place elements (verb invocations).
 *
 * <p>Each row records one verb invocation (TILE SURFACE, ARRAY, ROUTE SPRINKLERS...).
 * Renamed from PP_Order_Node (S86) — verb nodes can later participate in
 * DocAction WfMC workflow transitions (DR→IP→CO→AP).
 *
 * <p>Three-concern separation:
 * <ul>
 *   <li><b>WHAT</b> (order topics) = {@code c_orderline}</li>
 *   <li><b>HOW</b>  (verb invocations) = this class</li>
 *   <li><b>WHERE</b> (spatial workstation) = M_BOM_Line dx/dy/dz (co_empty_space removed S74)</li>
 * </ul>
 *
 * <p>Lives in output.db (transaction data, not BOM.db dictionary).
 *
 * @see <a href="docs/BIM_COBOL.md §15.6">DDL specification</a>
 */
public class X_W_Verb_Node extends BasePO {

    public static final String Table_Name                        = "W_Verb_Node";
    public static final String COLUMNNAME_W_Verb_Node_ID         = "W_Verb_Node_ID";
    public static final String COLUMNNAME_C_Order_ID             = "C_Order_ID";
    public static final String COLUMNNAME_SeqNo                  = "SeqNo";
    public static final String COLUMNNAME_Name                   = "Name";
    public static final String COLUMNNAME_Description            = "Description";
    public static final String COLUMNNAME_S_Resource_ID          = "S_Resource_ID";
    public static final String COLUMNNAME_M_Product_ID           = "M_Product_ID";
    public static final String COLUMNNAME_IsActive               = "IsActive";
    public static final String COLUMNNAME_DocStatus              = "DocStatus";
    public static final String COLUMNNAME_last_result            = "last_result";
    public static final String COLUMNNAME_element_count          = "element_count";
    public static final String COLUMNNAME_Created                = "Created";
    public static final String COLUMNNAME_Updated                = "Updated";

    public X_W_Verb_Node(Connection conn) { super(conn); }

    @Override protected String getTableName()    { return Table_Name; }
    @Override protected String getPKColumnName() { return COLUMNNAME_W_Verb_Node_ID; }

    // ── Getters ──

    public int    getW_Verb_Node_ID()     { return get_ValueAsInt(COLUMNNAME_W_Verb_Node_ID); }
    public String getC_Order_ID()         { return get_ValueAsString(COLUMNNAME_C_Order_ID); }
    public int    getSeqNo()              { return get_ValueAsInt(COLUMNNAME_SeqNo); }
    public String getName()               { return get_ValueAsString(COLUMNNAME_Name); }
    public String getDescription()        { return get_ValueAsString(COLUMNNAME_Description); }
    public int    getS_Resource_ID()      { return get_ValueAsInt(COLUMNNAME_S_Resource_ID); }
    public String getM_Product_ID()       { return get_ValueAsString(COLUMNNAME_M_Product_ID); }
    public boolean isActive()             { return get_ValueAsBoolean(COLUMNNAME_IsActive); }
    public String getDocStatus()          { return get_ValueAsString(COLUMNNAME_DocStatus); }
    public String getLastResult()         { return get_ValueAsString(COLUMNNAME_last_result); }
    public int    getElementCount()       { return get_ValueAsInt(COLUMNNAME_element_count); }
    public String getCreated()            { return get_ValueAsString(COLUMNNAME_Created); }
    public String getUpdated()            { return get_ValueAsString(COLUMNNAME_Updated); }

    // ── Setters ──

    public void setC_Order_ID(String v)    { set_Value(COLUMNNAME_C_Order_ID, v); }
    public void setSeqNo(int v)            { set_Value(COLUMNNAME_SeqNo, v); }
    public void setName(String v)          { set_Value(COLUMNNAME_Name, v); }
    public void setDescription(String v)   { set_Value(COLUMNNAME_Description, v); }
    public void setS_Resource_ID(int v)    { set_Value(COLUMNNAME_S_Resource_ID, v); }
    public void setM_Product_ID(String v)  { set_Value(COLUMNNAME_M_Product_ID, v); }
    public void setIsActive(boolean v)     { set_Value(COLUMNNAME_IsActive, v ? 1 : 0); }
    public void setDocStatus(String v)     { set_Value(COLUMNNAME_DocStatus, v); }
    public void setLastResult(String v)    { set_Value(COLUMNNAME_last_result, v); }
    public void setElementCount(int v)     { set_Value(COLUMNNAME_element_count, v); }
}
