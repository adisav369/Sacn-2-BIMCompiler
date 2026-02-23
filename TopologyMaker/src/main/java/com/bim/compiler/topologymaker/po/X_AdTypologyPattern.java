package com.bim.compiler.topologymaker.po;

import com.bim.orm.BasePO;
import java.sql.Connection;

/**
 * Generated-structure layer for {@code ad_typology_pattern}.
 *
 * <p>Contains ONLY: COLUMNNAME constants, typed getters/setters, and load/save
 * delegation to BasePO. No business logic. No validation. No cross-table calls.
 *
 * <p>Table: {@code ad_typology_pattern}
 * <pre>
 *   typology_id   TEXT PRIMARY KEY
 *   typology_name TEXT NOT NULL
 *   grid_strategy TEXT NOT NULL CHECK(STRIP_ZONES|COURTYARD|LINEAR)
 *   ubbl_class    TEXT NOT NULL
 *   description   TEXT
 *   base_width_mm INTEGER NOT NULL
 *   base_depth_mm INTEGER NOT NULL
 *   zone_json     TEXT NOT NULL
 *   is_active     INTEGER NOT NULL DEFAULT 1
 * </pre>
 */
public class X_AdTypologyPattern extends BasePO {

    public static final String Table_Name                    = "ad_typology_pattern";
    public static final String COLUMNNAME_typology_id        = "typology_id";
    public static final String COLUMNNAME_typology_name      = "typology_name";
    public static final String COLUMNNAME_grid_strategy      = "grid_strategy";
    public static final String COLUMNNAME_ubbl_class         = "ubbl_class";
    public static final String COLUMNNAME_description        = "description";
    public static final String COLUMNNAME_base_width_mm      = "base_width_mm";
    public static final String COLUMNNAME_base_depth_mm      = "base_depth_mm";
    public static final String COLUMNNAME_zone_json          = "zone_json";
    public static final String COLUMNNAME_is_active          = "is_active";

    public X_AdTypologyPattern(Connection conn) { super(conn); }

    @Override protected String getTableName()    { return Table_Name; }
    @Override protected String getPKColumnName() { return COLUMNNAME_typology_id; }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String  getTypologyId()   { return get_ValueAsString(COLUMNNAME_typology_id); }
    public String  getTypologyName() { return get_ValueAsString(COLUMNNAME_typology_name); }
    public String  getGridStrategy() { return get_ValueAsString(COLUMNNAME_grid_strategy); }
    public String  getUbblClass()    { return get_ValueAsString(COLUMNNAME_ubbl_class); }
    public String  getDescription()  { return get_ValueAsString(COLUMNNAME_description); }
    public int     getBaseWidthMm()  { return get_ValueAsInt(COLUMNNAME_base_width_mm); }
    public int     getBaseDepthMm()  { return get_ValueAsInt(COLUMNNAME_base_depth_mm); }
    public String  getZoneJson()     { return get_ValueAsString(COLUMNNAME_zone_json); }
    public boolean isActive()        { return get_ValueAsBoolean(COLUMNNAME_is_active); }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setTypologyId(String v)   { set_Value(COLUMNNAME_typology_id, v); }
    public void setTypologyName(String v) { set_Value(COLUMNNAME_typology_name, v); }
    public void setGridStrategy(String v) { set_Value(COLUMNNAME_grid_strategy, v); }
    public void setUbblClass(String v)    { set_Value(COLUMNNAME_ubbl_class, v); }
    public void setDescription(String v)  { set_Value(COLUMNNAME_description, v); }
    public void setBaseWidthMm(int v)     { set_Value(COLUMNNAME_base_width_mm, v); }
    public void setBaseDepthMm(int v)     { set_Value(COLUMNNAME_base_depth_mm, v); }
    public void setZoneJson(String v)     { set_Value(COLUMNNAME_zone_json, v); }
    public void setIsActive(boolean v)    { set_Value(COLUMNNAME_is_active, v ? 1 : 0); }
}
