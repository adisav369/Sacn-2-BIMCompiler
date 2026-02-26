#!/bin/bash
# ─────────────────────────────────────────────────────────────────────────────
# migration_phase_E_lod_extract.sh
# Phase E: Move ~67 working tables from component_library.db → BOM.db
# Rename 5 ad_* LOD tables → lod_* in component_library.db
# Recreate views in appropriate databases
#
# After this:
#   component_library.db = LOD geometry store (lod_*, component_*, surface_*, material_*)
#   BOM.db = Unified working database (ad_*, m_*, bad_*, wm_*)
# ─────────────────────────────────────────────────────────────────────────────
set -eu

SRC="library/component_library.db"
DST="library/BOM.db"

if [ ! -f "$SRC" ]; then
    echo "ERROR: $SRC not found. Run from project root." >&2
    exit 1
fi
if [ ! -f "$DST" ]; then
    echo "ERROR: $DST not found. Run migration_bom_db_extract.sh first." >&2
    exit 1
fi

echo "=== Phase E: Working-Table Extraction ==="

# ─────────────────────────────────────────────────────────────────────────────
# Tables to MOVE from component_library.db → BOM.db
# ─────────────────────────────────────────────────────────────────────────────
MOVE_TABLES=(
    # Building & Registry
    ad_building
    ad_building_registry
    ad_building_storey
    ad_building_template
    ad_building_assertions
    ad_building_bom
    ad_building_code

    # Spatial
    ad_room_boundary
    ad_building_grid
    ad_space_dim
    ad_space_type
    ad_space_type_alias
    ad_space_exterior_rule
    ad_space_adjacency

    # Product & Elements
    ad_product_dim
    ad_element_rule
    ad_element_dependency
    ad_element_mep
    ad_wall_face

    # Openings
    ad_opening_family
    ad_space_type_opening

    # Structural
    ad_beam_type
    ad_beam_type_rule
    ad_column_type
    ad_column_type_rule
    ad_wall_type
    ad_wall_type_rule
    ad_railing_type
    ad_covering_type

    # Floor
    ad_floor_type
    ad_floor_type_rule
    ad_slab_spec

    # MEP & Code
    ad_space_type_mep
    ad_space_type_mep_bom
    ad_check_applicability
    ad_check_threshold
    ad_code_requirement
    ad_egress_travel
    ad_stair_requirement
    ad_elevator_requirement
    ad_fire_compartment
    ad_fire_riser_requirement
    ad_vert_circ_trigger
    ad_mep_profile
    ad_fp_coverage
    ad_fp_trigger
    ad_pressurization_trigger

    # Config
    ad_sysconfig
    ad_placement_rule
    ad_ref_list
    ad_reference
    ad_jurisdiction_codes

    # Connectivity
    ad_assembly_connector
    ad_assembly_manifest

    # Typology
    ad_typology_template
    ad_typology_pattern
    ad_unit_type
    ad_unit_type_room

    # BAD
    bad_rule
    bad_rule_category
    bad_rule_param
    bad_discipline_priority

    # Other
    ad_spatial_pattern
    ad_spatial_rule
    ad_room_slot
    ad_ubbl_rule
    ad_callout_rule
    wm_empty_storage_line
)

# ─────────────────────────────────────────────────────────────────────────────
# Tables to RENAME in component_library.db (ad_ → lod_)
# ─────────────────────────────────────────────────────────────────────────────
declare -A RENAME_TABLES=(
    [ad_geometry_map]=lod_geometry_map
    [ad_element_placement]=lod_element_placement
    [ad_parametric_mesh]=lod_parametric_mesh
    [ad_parametric_mesh_param]=lod_parametric_mesh_param
    [ad_roof_preset]=lod_roof_preset
)

# ─────────────────────────────────────────────────────────────────────────────
# Step 1: Drop views from component_library.db that reference moving tables
# ─────────────────────────────────────────────────────────────────────────────
echo "[1/8] Dropping views from $SRC that reference moving tables..."
sqlite3 "$SRC" <<'SQL'
DROP VIEW IF EXISTS v_compilable_element_rule;
DROP VIEW IF EXISTS v_verified_room_boundary;
SQL
echo "  Dropped: v_compilable_element_rule, v_verified_room_boundary"

# ─────────────────────────────────────────────────────────────────────────────
# Step 2: Dump each table from SRC and load into DST
# ─────────────────────────────────────────────────────────────────────────────
echo "[2/8] Copying ${#MOVE_TABLES[@]} tables to $DST..."
for TBL in "${MOVE_TABLES[@]}"; do
    # Check if table exists in source
    EXISTS=$(sqlite3 "$SRC" "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='$TBL';")
    if [ "$EXISTS" -eq 0 ]; then
        echo "  SKIP (not in source): $TBL"
        continue
    fi
    sqlite3 "$SRC" ".dump $TBL" | sqlite3 "$DST"
    echo "  OK: $TBL"
done

# ─────────────────────────────────────────────────────────────────────────────
# Step 3: Verify row counts match
# ─────────────────────────────────────────────────────────────────────────────
echo "[3/8] Verifying row counts..."
ALL_OK=true
for TBL in "${MOVE_TABLES[@]}"; do
    EXISTS=$(sqlite3 "$SRC" "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='$TBL';")
    if [ "$EXISTS" -eq 0 ]; then
        continue
    fi
    SRC_CNT=$(sqlite3 "$SRC" "SELECT COUNT(*) FROM \"$TBL\"")
    DST_CNT=$(sqlite3 "$DST" "SELECT COUNT(*) FROM \"$TBL\"")
    if [ "$SRC_CNT" != "$DST_CNT" ]; then
        echo "  FAIL: $TBL src=$SRC_CNT dst=$DST_CNT" >&2
        ALL_OK=false
    else
        echo "  OK: $TBL = $SRC_CNT rows"
    fi
done

if [ "$ALL_OK" != "true" ]; then
    echo "ERROR: Row count mismatch — aborting. Tables copied but NOT dropped." >&2
    exit 1
fi

# ─────────────────────────────────────────────────────────────────────────────
# Step 4: Drop moved tables from component_library.db
# ─────────────────────────────────────────────────────────────────────────────
echo "[4/8] Dropping moved tables from $SRC..."
for TBL in "${MOVE_TABLES[@]}"; do
    sqlite3 "$SRC" "DROP TABLE IF EXISTS \"$TBL\";"
done
echo "  Dropped ${#MOVE_TABLES[@]} tables"

# ─────────────────────────────────────────────────────────────────────────────
# Step 5: Rename ad_* → lod_* tables staying in component_library.db
# ─────────────────────────────────────────────────────────────────────────────
echo "[5/8] Renaming LOD tables (ad_ → lod_) in $SRC..."
for OLD in "${!RENAME_TABLES[@]}"; do
    NEW="${RENAME_TABLES[$OLD]}"
    EXISTS=$(sqlite3 "$SRC" "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='$OLD';")
    if [ "$EXISTS" -eq 0 ]; then
        echo "  SKIP (not found): $OLD"
        continue
    fi
    sqlite3 "$SRC" "ALTER TABLE \"$OLD\" RENAME TO \"$NEW\";"
    echo "  OK: $OLD → $NEW"
done

# ─────────────────────────────────────────────────────────────────────────────
# Step 6: Recreate v_proven_geometry in component_library.db (uses lod_ names)
# ─────────────────────────────────────────────────────────────────────────────
echo "[6/8] Recreating v_proven_geometry in $SRC with lod_ table names..."
sqlite3 "$SRC" <<'SQL'
DROP VIEW IF EXISTS v_proven_geometry;
CREATE VIEW v_proven_geometry AS
SELECT
    cd.id,
    cd.name,
    (SELECT MIN(gm.ifc_class)
     FROM lod_geometry_map gm
     WHERE gm.geometry_hash = cd.geometry_hash) AS ifc_class,
    cd.geometry_hash,
    cd.vertex_count,
    cd.extracted_from               AS geometry_source
FROM component_definitions cd
WHERE cd.vertex_count > 8
  AND cd.extracted_from NOT LIKE '%PENDING%'
  AND cd.geometry_hash IS NOT NULL;
SQL
echo "  OK: v_proven_geometry recreated"

# ─────────────────────────────────────────────────────────────────────────────
# Step 7: Recreate views in BOM.db
# ─────────────────────────────────────────────────────────────────────────────
echo "[7/8] Recreating views in $DST..."
sqlite3 "$DST" <<'SQL'
CREATE VIEW IF NOT EXISTS v_compilable_element_rule AS
SELECT
    er.id,
    er.building_type,
    er.storey,
    er.element_ref,
    er.ifc_class,
    er.discipline,
    er.host_type,
    er.host_ref,
    er.position_rule,
    er.position_value,
    er.position_value_2,
    er.position_value_3,
    er.height_mm,
    er.height_extent_mm,
    er.family_ref,
    er.width_mm,
    er.depth_mm,
    er.orientation,
    er.geometry_hash,
    er.material_name,
    er.material_rgba
FROM ad_element_rule er
WHERE er.is_active = 1;

CREATE VIEW IF NOT EXISTS v_verified_room_boundary AS
SELECT
    rb.id                           AS room_id,
    rb.building_type,
    rb.room_type,
    rb.min_x_mm,
    rb.max_x_mm,
    rb.min_y_mm,
    rb.max_y_mm,
    rb.storey                       AS storey_id,
    rb.extracted_from,
    rb.coordinate_frame,
    (rb.max_x_mm - rb.min_x_mm)    AS width_mm,
    (rb.max_y_mm - rb.min_y_mm)    AS depth_mm
FROM ad_room_boundary rb
WHERE rb.coordinate_frame IN (
        'IFC_GLOBAL_MM',
        'LOCAL_MM',
        'DRAWING_MM',
        'CONSTRAINT_SOLVED',
        'DERIVED_MM'
    )
    AND rb.extracted_from NOT IN ('PENDING','','TODO','UNKNOWN','GRID_DERIVED');
SQL
echo "  OK: v_compilable_element_rule, v_verified_room_boundary"

# ─────────────────────────────────────────────────────────────────────────────
# Step 8: VACUUM both databases
# ─────────────────────────────────────────────────────────────────────────────
echo "[8/8] VACUUM on both databases..."
sqlite3 "$SRC" "VACUUM;"
sqlite3 "$DST" "VACUUM;"

echo ""
echo "=== Phase E Migration Complete ==="
echo ""
echo "  $DST tables: $(sqlite3 "$DST" "SELECT COUNT(*) FROM sqlite_master WHERE type='table';")"
echo "  $SRC tables: $(sqlite3 "$SRC" "SELECT COUNT(*) FROM sqlite_master WHERE type='table';")"
echo ""
echo "  $SRC (LOD geometry store):"
sqlite3 "$SRC" ".tables"
echo ""
echo "  $DST (working database):"
sqlite3 "$DST" ".tables"
