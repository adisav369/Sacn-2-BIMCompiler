package com.bim.ormsandbox.po;

import com.bim.orm.BasePO;
import java.sql.Connection;

/**
 * Generated-structure layer for {@code m_bom} (iDempiere: M_BOM + M_Product merged).
 *
 * <p>M_Product is flattened into M_BOM. A leaf item is an M_BOM with no M_BOM_Line children.
 * Three orthogonal dimensions: {@code bom_category} (WHAT), {@code doc_sub_type} (WHICH variant),
 * SpaceSize on M_BOM_Line (HOW MUCH).
 *
 * <p>Table: {@code m_bom}
 * <pre>
 *   bom_id           TEXT PRIMARY KEY
 *   bom_name         TEXT NOT NULL
 *   description      TEXT
 *   target_ifc_class TEXT DEFAULT 'IfcElementAssembly'
 *   group_by         TEXT NOT NULL   (ROOM|BUILDING — NOT NULL, always set)
 *   is_active        INTEGER DEFAULT 1
 *   bom_level        TEXT DEFAULT 'SET'
 *   bom_type         TEXT NOT NULL CHECK(UNIT|FLOOR|ROOM|SET|ITEM)
 *   bom_category     TEXT           FK → M_BomCategory(M_BomCategory_ID)  (LI|BD|KT|BT|DN|FR|ST|L1|L2|UN)
 *   doc_sub_type     TEXT           C_DocType.DocSubType (SH|DX|TB|TE|ST), NULL = generic
 *   seq_no           INTEGER DEFAULT 10  display/tiebreaker order (lower = preferred)
 *   origin_x         REAL DEFAULT 0.0   tack point world X (§3.4)
 *   origin_y         REAL DEFAULT 0.0   tack point world Y (§3.4)
 *   origin_z         REAL DEFAULT 0.0   tack point world Z (§3.4)
 * </pre>
 *
 * <p>TRAP: {@code group_by} is NOT NULL — always set ('ROOM' for room mods, 'BUILDING' for wall/floor/unit)
 *
 * @see <a href="docs/BIMasBOMConcept.md">BIM as BOM Concept — §2</a>
 */
public class X_M_BOM extends BasePO {

    public static final String Table_Name                    = "m_bom";
    public static final String COLUMNNAME_bom_id             = "bom_id";
    public static final String COLUMNNAME_bom_name           = "bom_name";
    public static final String COLUMNNAME_description        = "description";
    public static final String COLUMNNAME_target_ifc_class   = "target_ifc_class";
    public static final String COLUMNNAME_group_by           = "group_by";
    public static final String COLUMNNAME_is_active          = "is_active";
    public static final String COLUMNNAME_bom_level          = "bom_level";
    public static final String COLUMNNAME_bom_type           = "bom_type";
    public static final String COLUMNNAME_bom_category       = "bom_category";
    public static final String COLUMNNAME_doc_sub_type         = "doc_sub_type";
    public static final String COLUMNNAME_seq_no              = "seq_no";
    public static final String COLUMNNAME_origin_x            = "origin_x";
    public static final String COLUMNNAME_origin_y            = "origin_y";
    public static final String COLUMNNAME_origin_z            = "origin_z";

    public X_M_BOM(Connection conn) { super(conn); }

    @Override protected String getTableName()    { return Table_Name; }
    @Override protected String getPKColumnName() { return COLUMNNAME_bom_id; }

    public String  getBomId()           { return get_ValueAsString(COLUMNNAME_bom_id); }
    public String  getBomName()         { return get_ValueAsString(COLUMNNAME_bom_name); }
    public String  getDescription()     { return get_ValueAsString(COLUMNNAME_description); }
    public String  getTargetIfcClass()  { return get_ValueAsString(COLUMNNAME_target_ifc_class); }
    public String  getGroupBy()         { return get_ValueAsString(COLUMNNAME_group_by); }
    public boolean isActive()           { return get_ValueAsBoolean(COLUMNNAME_is_active); }
    public String  getBomLevel()        { return get_ValueAsString(COLUMNNAME_bom_level); }
    public String  getBomType()         { return get_ValueAsString(COLUMNNAME_bom_type); }
    public String  getBomCategory()     { return get_ValueAsString(COLUMNNAME_bom_category); }
    public String  getDocSubType()      { return get_ValueAsString(COLUMNNAME_doc_sub_type); }
    public int     getSeqNo()           { return get_ValueAsInt(COLUMNNAME_seq_no); }
    public double  getOriginX()         { return get_ValueAsDouble(COLUMNNAME_origin_x); }
    public double  getOriginY()         { return get_ValueAsDouble(COLUMNNAME_origin_y); }
    public double  getOriginZ()         { return get_ValueAsDouble(COLUMNNAME_origin_z); }

    public void setBomId(String v)          { set_Value(COLUMNNAME_bom_id, v); }
    public void setBomName(String v)        { set_Value(COLUMNNAME_bom_name, v); }
    public void setDescription(String v)    { set_Value(COLUMNNAME_description, v); }
    public void setTargetIfcClass(String v) { set_Value(COLUMNNAME_target_ifc_class, v); }
    public void setGroupBy(String v)        { set_Value(COLUMNNAME_group_by, v); }
    public void setIsActive(boolean v)      { set_Value(COLUMNNAME_is_active, v ? 1 : 0); }
    public void setBomLevel(String v)       { set_Value(COLUMNNAME_bom_level, v); }
    public void setBomType(String v)        { set_Value(COLUMNNAME_bom_type, v); }
    public void setBomCategory(String v)    { set_Value(COLUMNNAME_bom_category, v); }
    public void setDocSubType(String v)     { set_Value(COLUMNNAME_doc_sub_type, v); }
    public void setSeqNo(int v)             { set_Value(COLUMNNAME_seq_no, v); }
    public void setOriginX(double v)       { set_Value(COLUMNNAME_origin_x, v); }
    public void setOriginY(double v)       { set_Value(COLUMNNAME_origin_y, v); }
    public void setOriginZ(double v)       { set_Value(COLUMNNAME_origin_z, v); }
}
