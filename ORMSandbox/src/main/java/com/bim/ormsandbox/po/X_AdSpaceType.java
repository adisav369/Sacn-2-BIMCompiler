package com.bim.ormsandbox.po;

import com.bim.orm.BasePO;
import java.sql.Connection;

/**
 * Generated-structure layer for {@code ad_space_type}.
 *
 * <p>Space type catalog — regulatory minimums, wall rules, structural grid flags.
 * Alias resolution is handled via {@code ad_space_type_alias} (see {@link M_AdSpaceType}).
 *
 * <p>Table: {@code ad_space_type}
 * <pre>
 *   space_type_id    TEXT PRIMARY KEY   -- 'BEDROOM', 'BATHROOM', ...
 *   category         TEXT NOT NULL      -- 'HABITABLE', 'SERVICE', 'CIRCULATION', ...
 *   omniclass_code   TEXT NOT NULL
 *   wall_rule        TEXT NOT NULL      -- 'ENCLOSED', 'PERIMETER_ONLY', 'NONE', 'AS_REQUIRED'
 *   is_sleeping_room INTEGER DEFAULT 0
 *   is_open_plan     INTEGER DEFAULT 0
 *   is_exterior      INTEGER DEFAULT 0
 *   min_area         REAL DEFAULT 0     -- m²
 *   min_dimension    REAL DEFAULT 0     -- m
 *   requires_window  INTEGER DEFAULT 0
 *   requires_egress  INTEGER DEFAULT 0
 *   structural_grid  INTEGER DEFAULT 0
 *   beam_max_span    REAL DEFAULT 8.0
 *   code_reference   TEXT
 *   is_active        INTEGER DEFAULT 1
 * </pre>
 *
 * <p>Consumers: SpaceTypeAD, ADSession, MEPBOMResolver, SpaceDimResolver.
 */
public class X_AdSpaceType extends BasePO {

    public static final String Table_Name                  = "ad_space_type";
    public static final String COLUMNNAME_space_type_id    = "space_type_id";
    public static final String COLUMNNAME_category         = "category";
    public static final String COLUMNNAME_omniclass_code   = "omniclass_code";
    public static final String COLUMNNAME_wall_rule        = "wall_rule";
    public static final String COLUMNNAME_is_sleeping_room = "is_sleeping_room";
    public static final String COLUMNNAME_is_open_plan     = "is_open_plan";
    public static final String COLUMNNAME_is_exterior      = "is_exterior";
    public static final String COLUMNNAME_min_area         = "min_area";
    public static final String COLUMNNAME_min_dimension    = "min_dimension";
    public static final String COLUMNNAME_requires_window  = "requires_window";
    public static final String COLUMNNAME_requires_egress  = "requires_egress";
    public static final String COLUMNNAME_structural_grid  = "structural_grid";
    public static final String COLUMNNAME_beam_max_span    = "beam_max_span";
    public static final String COLUMNNAME_code_reference   = "code_reference";
    public static final String COLUMNNAME_is_active        = "is_active";

    public X_AdSpaceType(Connection conn) { super(conn); }

    @Override protected String getTableName()    { return Table_Name; }
    @Override protected String getPKColumnName() { return COLUMNNAME_space_type_id; }

    public String  getSpaceTypeId()    { return get_ValueAsString(COLUMNNAME_space_type_id); }
    public String  getCategory()       { return get_ValueAsString(COLUMNNAME_category); }
    public String  getOmniclassCode()  { return get_ValueAsString(COLUMNNAME_omniclass_code); }
    public String  getWallRule()       { return get_ValueAsString(COLUMNNAME_wall_rule); }
    public boolean isSleepingRoom()    { return get_ValueAsBoolean(COLUMNNAME_is_sleeping_room); }
    public boolean isOpenPlan()        { return get_ValueAsBoolean(COLUMNNAME_is_open_plan); }
    public boolean isExterior()        { return get_ValueAsBoolean(COLUMNNAME_is_exterior); }
    public double  getMinArea()        { return get_ValueAsDouble(COLUMNNAME_min_area); }
    public double  getMinDimension()   { return get_ValueAsDouble(COLUMNNAME_min_dimension); }
    public boolean requiresWindow()    { return get_ValueAsBoolean(COLUMNNAME_requires_window); }
    public boolean requiresEgress()    { return get_ValueAsBoolean(COLUMNNAME_requires_egress); }
    public boolean hasStructuralGrid() { return get_ValueAsBoolean(COLUMNNAME_structural_grid); }
    public double  getBeamMaxSpan()    { return get_ValueAsDouble(COLUMNNAME_beam_max_span); }
    public String  getCodeReference()  { return get_ValueAsString(COLUMNNAME_code_reference); }
    public boolean isActive()          { return get_ValueAsBoolean(COLUMNNAME_is_active); }

    public void setSpaceTypeId(String v)    { set_Value(COLUMNNAME_space_type_id, v); }
    public void setCategory(String v)       { set_Value(COLUMNNAME_category, v); }
    public void setOmniclassCode(String v)  { set_Value(COLUMNNAME_omniclass_code, v); }
    public void setWallRule(String v)       { set_Value(COLUMNNAME_wall_rule, v); }
    public void setIsSleepingRoom(boolean v){ set_Value(COLUMNNAME_is_sleeping_room, v ? 1 : 0); }
    public void setIsOpenPlan(boolean v)    { set_Value(COLUMNNAME_is_open_plan, v ? 1 : 0); }
    public void setIsExterior(boolean v)    { set_Value(COLUMNNAME_is_exterior, v ? 1 : 0); }
    public void setMinArea(double v)        { set_Value(COLUMNNAME_min_area, v); }
    public void setMinDimension(double v)   { set_Value(COLUMNNAME_min_dimension, v); }
    public void setRequiresWindow(boolean v){ set_Value(COLUMNNAME_requires_window, v ? 1 : 0); }
    public void setRequiresEgress(boolean v){ set_Value(COLUMNNAME_requires_egress, v ? 1 : 0); }
    public void setStructuralGrid(boolean v){ set_Value(COLUMNNAME_structural_grid, v ? 1 : 0); }
    public void setBeamMaxSpan(double v)    { set_Value(COLUMNNAME_beam_max_span, v); }
    public void setCodeReference(String v)  { set_Value(COLUMNNAME_code_reference, v); }
    public void setIsActive(boolean v)      { set_Value(COLUMNNAME_is_active, v ? 1 : 0); }
}
