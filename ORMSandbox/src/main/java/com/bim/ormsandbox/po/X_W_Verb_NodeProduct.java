package com.bim.ormsandbox.po;

import com.bim.orm.BasePO;
import java.sql.Connection;

/**
 * PO layer for {@code W_Verb_NodeProduct} — structured parameters per verb.
 *
 * <p>Each W_Verb_Node (verb invocation) has N parameter rows. The Description
 * column on W_Verb_Node holds the COBOL source (human-readable). These rows
 * hold the same data in structured form (GUI-editable, queryable).
 *
 * <p>Lives in output.db (transaction data, not BOM.db dictionary).
 *
 * @see X_W_Verb_Node
 * @see <a href="docs/BIM_COBOL.md §15.6">DDL specification</a>
 */
public class X_W_Verb_NodeProduct extends BasePO {

    public static final String Table_Name                              = "W_Verb_NodeProduct";
    public static final String COLUMNNAME_W_Verb_NodeProduct_ID        = "W_Verb_NodeProduct_ID";
    public static final String COLUMNNAME_W_Verb_Node_ID               = "W_Verb_Node_ID";
    public static final String COLUMNNAME_Name                         = "Name";
    public static final String COLUMNNAME_Value                        = "Value";
    public static final String COLUMNNAME_ValueType                    = "ValueType";

    public X_W_Verb_NodeProduct(Connection conn) { super(conn); }

    @Override protected String getTableName()    { return Table_Name; }
    @Override protected String getPKColumnName() { return COLUMNNAME_W_Verb_NodeProduct_ID; }

    // ── Getters ──

    public int    getW_Verb_NodeProduct_ID() { return get_ValueAsInt(COLUMNNAME_W_Verb_NodeProduct_ID); }
    public int    getW_Verb_Node_ID()        { return get_ValueAsInt(COLUMNNAME_W_Verb_Node_ID); }
    public String getName()                  { return get_ValueAsString(COLUMNNAME_Name); }
    public String getValue()                 { return get_ValueAsString(COLUMNNAME_Value); }
    public String getValueType()             { return get_ValueAsString(COLUMNNAME_ValueType); }

    // ── Setters ──

    public void setW_Verb_Node_ID(int v)  { set_Value(COLUMNNAME_W_Verb_Node_ID, v); }
    public void setName(String v)         { set_Value(COLUMNNAME_Name, v); }
    public void setValue(String v)        { set_Value(COLUMNNAME_Value, v); }
    public void setValueType(String v)    { set_Value(COLUMNNAME_ValueType, v); }

    // ── Typed value helpers ──

    public double getValueAsDouble() {
        String v = getValue();
        return v != null ? Double.parseDouble(v) : 0.0;
    }

    public int getValueAsInt() {
        String v = getValue();
        return v != null ? Integer.parseInt(v) : 0;
    }
}
