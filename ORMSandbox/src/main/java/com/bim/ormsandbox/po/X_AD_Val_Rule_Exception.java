package com.bim.ormsandbox.po;

import com.bim.orm.BasePO;
import java.sql.Connection;

/**
 * Generated-structure layer for {@code AD_Val_Rule_Exception} (U6).
 *
 * <p>User override of WARN validation rules on specific C_OrderLines.
 * A designer can acknowledge a validation warning (e.g., "door too close to
 * corner") with a justification reason, exempting that element from the check.
 *
 * <p>Table: {@code AD_Val_Rule_Exception} (compile DB, S60_schema.sql)
 * <pre>
 *   AD_Val_Rule_Exception_ID  INTEGER PRIMARY KEY AUTOINCREMENT
 *   C_OrderLine_ID            INTEGER NOT NULL (FK → C_OrderLine)
 *   ad_val_rule_id            INTEGER NOT NULL (FK → AD_Val_Rule)
 *   reason                    TEXT NOT NULL    (user justification)
 *   approved_by               TEXT             (who approved)
 *   created                   TEXT             (datetime)
 *   UNIQUE(C_OrderLine_ID, ad_val_rule_id)
 * </pre>
 *
 * // Implementing S60_ERP_ALIGNMENT.md §U6 — Witness: W-S60-EXCEPT
 */
public class X_AD_Val_Rule_Exception extends BasePO {

    public static final String Table_Name                                  = "AD_Val_Rule_Exception";
    public static final String COLUMNNAME_AD_Val_Rule_Exception_ID         = "AD_Val_Rule_Exception_ID";
    public static final String COLUMNNAME_C_OrderLine_ID                   = "C_OrderLine_ID";
    public static final String COLUMNNAME_ad_val_rule_id                   = "ad_val_rule_id";
    public static final String COLUMNNAME_reason                           = "reason";
    public static final String COLUMNNAME_approved_by                      = "approved_by";
    public static final String COLUMNNAME_created                          = "created";

    public X_AD_Val_Rule_Exception(Connection conn) { super(conn); }

    @Override protected String getTableName()    { return Table_Name; }
    @Override protected String getPKColumnName() { return COLUMNNAME_AD_Val_Rule_Exception_ID; }

    public int    getExceptionId()    { return get_ValueAsInt(COLUMNNAME_AD_Val_Rule_Exception_ID); }
    public int    getOrderLineId()    { return get_ValueAsInt(COLUMNNAME_C_OrderLine_ID); }
    public int    getValRuleId()      { return get_ValueAsInt(COLUMNNAME_ad_val_rule_id); }
    public String getReason()         { return get_ValueAsString(COLUMNNAME_reason); }
    public String getApprovedBy()     { return get_ValueAsString(COLUMNNAME_approved_by); }
    public String getCreated()        { return get_ValueAsString(COLUMNNAME_created); }

    public void setOrderLineId(int v)      { set_Value(COLUMNNAME_C_OrderLine_ID, v); }
    public void setValRuleId(int v)        { set_Value(COLUMNNAME_ad_val_rule_id, v); }
    public void setReason(String v)        { set_Value(COLUMNNAME_reason, v); }
    public void setApprovedBy(String v)    { set_Value(COLUMNNAME_approved_by, v); }
}
