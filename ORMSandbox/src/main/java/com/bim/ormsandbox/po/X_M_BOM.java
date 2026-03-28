package com.bim.ormsandbox.po;

import com.bim.orm.BasePO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;

/**
 * Generated-structure layer for {@code m_bom} (iDempiere: M_BOM + M_Product merged).
 *
 * <p>M_Product is flattened into M_BOM. A leaf item is an M_BOM with no M_BOM_Line children.
 * Classification: {@code m_product_category_id} (M_Product_Category FK: RE/CO/IN at building level,
 * LI/BD/KT at room level — WHAT kind of thing). {@code doc_sub_type} identifies the specific
 * variant (SH/DX/TB).
 * SpaceSize on M_BOM_Line (HOW MUCH).
 *
 * <p>Table: {@code m_bom}
 * <pre>
 *   M_BOM_ID         INTEGER PRIMARY KEY AUTOINCREMENT  (iDempiere _ID — hidden, opaque)
 *   Value            TEXT NOT NULL UNIQUE                (iDempiere SearchKey — was bom_id)
 *   Name             TEXT NOT NULL                       (iDempiere DisplayName — was bom_name)
 *   bom_id           TEXT NOT NULL UNIQUE                (legacy alias of Value)
 *   bom_name         TEXT NOT NULL
 *   description      TEXT
 *   target_ifc_class TEXT DEFAULT 'IfcElementAssembly'
 *   group_by         TEXT NOT NULL   (ROOM|BUILDING — NOT NULL, always set)
 *   is_active        INTEGER DEFAULT 1
 *   bom_level        TEXT DEFAULT 'SET'
 *   bom_type         TEXT NOT NULL CHECK(UNIT|FLOOR|ROOM|SET|ITEM)
 *   m_product_category_id INTEGER   FK → M_Product_Category(M_Product_Category_ID)  (LI|BD|KT|BT|DN|FR|ST|L1|L2|UN)
 *   doc_sub_type     TEXT           C_DocType.DocSubType (SH|DX|TB|TE), NULL = generic — STRUCTURAL variant scoping
 *   seq_no           INTEGER DEFAULT 10  display/tiebreaker order (lower = preferred)
 *   origin_x         REAL DEFAULT 0.0   tack point world X (§3.4)
 *   origin_y         REAL DEFAULT 0.0   tack point world Y (§3.4)
 *   origin_z         REAL DEFAULT 0.0   tack point world Z (§3.4)
 *   aabb_width_mm    INTEGER DEFAULT 0  axis-aligned bounding box width (mm)
 *   aabb_depth_mm    INTEGER DEFAULT 0  axis-aligned bounding box depth (mm)
 *   aabb_height_mm   INTEGER DEFAULT 0  axis-aligned bounding box height (mm)
 * </pre>
 *
 * <p>TRAP: {@code group_by} is NOT NULL — always set ('ROOM' for room mods, 'BUILDING' for wall/floor/unit)
 *
 * @see <a href="docs/BIMasBOMConcept.md">BIM as BOM Concept — §2</a>
 */
public class X_M_BOM extends BasePO {

    public static final String Table_Name                    = "m_bom";
    public static final String COLUMNNAME_M_BOM_ID            = "M_BOM_ID";
    public static final String COLUMNNAME_Value              = "Value";
    public static final String COLUMNNAME_Name_IDV           = "Name";  // _IDV suffix avoids clash
    public static final String COLUMNNAME_bom_id             = "bom_id";
    public static final String COLUMNNAME_bom_name           = "bom_name";
    public static final String COLUMNNAME_description        = "description";
    public static final String COLUMNNAME_target_ifc_class   = "target_ifc_class";
    public static final String COLUMNNAME_group_by           = "group_by";
    public static final String COLUMNNAME_is_active          = "is_active";
    public static final String COLUMNNAME_bom_level          = "bom_level";
    public static final String COLUMNNAME_bom_type           = "bom_type";
    public static final String COLUMNNAME_m_product_category_id       = "m_product_category_id";
    public static final String COLUMNNAME_doc_sub_type         = "doc_sub_type";
    public static final String COLUMNNAME_seq_no              = "seq_no";
    public static final String COLUMNNAME_origin_x            = "origin_x";
    public static final String COLUMNNAME_origin_y            = "origin_y";
    public static final String COLUMNNAME_origin_z            = "origin_z";
    public static final String COLUMNNAME_entity_type         = "entity_type";
    public static final String COLUMNNAME_aabb_width_mm      = "aabb_width_mm";
    public static final String COLUMNNAME_aabb_depth_mm      = "aabb_depth_mm";
    public static final String COLUMNNAME_aabb_height_mm     = "aabb_height_mm";
    public static final String COLUMNNAME_aabb_qualifier     = "aabb_qualifier";

    /** AABB qualifier values — GD&T tolerance zone convention. */
    public static final String AABB_INNER      = "INNER";       // finish-to-finish clear volume
    public static final String AABB_STRUCTURAL  = "STRUCTURAL";  // centerline-to-centerline
    public static final String AABB_OUTER      = "OUTER";       // full object extent incl. projections
    public static final String AABB_OPENING    = "OPENING";     // clear opening in host element

    /** EntityType constants — iDempiere convention. */
    public static final String ENTITYTYPE_Dictionary  = "D";
    public static final String ENTITYTYPE_User        = "U";
    public static final String ENTITYTYPE_Application = "A";

    /**
     * GodMode bypass — if {@code GodMode.txt} exists in the working directory,
     * EntityType guards are disabled. This lets developers/admins modify
     * dictionary records during migrations, data fixes, or development.
     *
     * <p>The file is gitignored so it never reaches production.
     * Checked once per JVM (cached).
     */
    private static final boolean GOD_MODE;
    static {
        GOD_MODE = Files.exists(Path.of("GodMode.txt"));
    }

    /** True if GodMode.txt is present — EntityType guards bypassed. */
    public static boolean isGodMode() { return GOD_MODE; }

    private transient String cachedCategoryValue;

    public X_M_BOM(Connection conn) { super(conn); }

    @Override protected String getTableName()    { return Table_Name; }
    // Implementing BBC.md §14.3 IDV-1 — Witness: W-PK-MBOM
    @Override protected String getPKColumnName() { return COLUMNNAME_M_BOM_ID; }

    public String  getBomId()           { return get_ValueAsString(COLUMNNAME_bom_id); }
    public String  getBomName()         { return get_ValueAsString(COLUMNNAME_bom_name); }
    public String  getDescription()     { return get_ValueAsString(COLUMNNAME_description); }
    public String  getTargetIfcClass()  { return get_ValueAsString(COLUMNNAME_target_ifc_class); }
    public String  getGroupBy()         { return get_ValueAsString(COLUMNNAME_group_by); }
    public boolean isActive()           { return get_ValueAsBoolean(COLUMNNAME_is_active); }
    public String  getBomLevel()        { return get_ValueAsString(COLUMNNAME_bom_level); }
    public String  getBomType()         { return get_ValueAsString(COLUMNNAME_bom_type); }
    /** Returns the raw INTEGER FK to M_Product_Category. */
    public int     getProductCategoryId()   { return get_ValueAsInt(COLUMNNAME_m_product_category_id); }
    /** Returns the TEXT Value code (e.g. "RE", "LI") by resolving INTEGER FK via M_Product_Category. */
    public String  getProductCategory() {
        if (cachedCategoryValue != null) return cachedCategoryValue;
        int catId = get_ValueAsInt(COLUMNNAME_m_product_category_id);
        if (catId == 0) return null;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT Value FROM M_Product_Category WHERE M_Product_Category_ID = ?")) {
            ps.setInt(1, catId);
            try (ResultSet rs = ps.executeQuery()) {
                cachedCategoryValue = rs.next() ? rs.getString(1) : null;
            }
        } catch (SQLException e) { cachedCategoryValue = null; }
        return cachedCategoryValue;
    }
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
    /** Sets category by TEXT Value code — resolves to INTEGER FK via M_Product_Category. */
    public void setProductCategory(String v) {
        cachedCategoryValue = v;
        if (v == null) { set_Value(COLUMNNAME_m_product_category_id, null); return; }
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT M_Product_Category_ID FROM M_Product_Category WHERE Value = ?")) {
            ps.setString(1, v);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) { set_Value(COLUMNNAME_m_product_category_id, rs.getInt(1)); return; }
            }
        } catch (SQLException ignored) { }
        set_Value(COLUMNNAME_m_product_category_id, v); // fallback: store as-is
    }
    public void setDocSubType(String v)     { set_Value(COLUMNNAME_doc_sub_type, v); }
    public void setSeqNo(int v)             { set_Value(COLUMNNAME_seq_no, v); }
    public void setOriginX(double v)       { set_Value(COLUMNNAME_origin_x, v); }
    public void setOriginY(double v)       { set_Value(COLUMNNAME_origin_y, v); }
    public void setOriginZ(double v)       { set_Value(COLUMNNAME_origin_z, v); }
    public String getEntityType()          { return get_ValueAsString(COLUMNNAME_entity_type); }
    public void setEntityType(String v)    { set_Value(COLUMNNAME_entity_type, v); }

    public int  getAabbWidthMm()           { return get_ValueAsInt(COLUMNNAME_aabb_width_mm); }
    public int  getAabbDepthMm()           { return get_ValueAsInt(COLUMNNAME_aabb_depth_mm); }
    public int  getAabbHeightMm()          { return get_ValueAsInt(COLUMNNAME_aabb_height_mm); }
    public void setAabbWidthMm(int v)      { set_Value(COLUMNNAME_aabb_width_mm, v); }
    public void setAabbDepthMm(int v)      { set_Value(COLUMNNAME_aabb_depth_mm, v); }
    public void setAabbHeightMm(int v)     { set_Value(COLUMNNAME_aabb_height_mm, v); }

    public String getAabbQualifier()       { return get_ValueAsString(COLUMNNAME_aabb_qualifier); }
    public void setAabbQualifier(String v) { set_Value(COLUMNNAME_aabb_qualifier, v); }

    // ── Tier 2: INTEGER PK accessors (Phase C) ──────────────────────────────
    public int     getMBomId()             { return get_ValueAsInt(COLUMNNAME_M_BOM_ID); }
    public String  getValue()              { return get_ValueAsString(COLUMNNAME_Value); }
    public String  getNameIDV()            { return get_ValueAsString(COLUMNNAME_Name_IDV); }
    public void    setMBomId(int v)        { set_Value(COLUMNNAME_M_BOM_ID, v); }
    public void    setValue(String v)       { set_Value(COLUMNNAME_Value, v); }
    public void    setNameIDV(String v)    { set_Value(COLUMNNAME_Name_IDV, v); }
}
