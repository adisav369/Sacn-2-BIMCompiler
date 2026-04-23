#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════════════
# rebuild_erp.sh — Deterministic from-scratch rebuild of library/ERP.db
# ═══════════════════════════════════════════════════════════════════════
#
# Creates ERP.db with all seed/reference data (like iDempiere's AD seed).
# After this script, any IFC file can be processed without manual SQL.
#
# Usage:
#   ./scripts/rebuild_erp.sh              # Schema + seeds only
#   ./scripts/rebuild_erp.sh --with-rules # + per-building validation rules
#   ./scripts/rebuild_erp.sh --full       # + pipeline run (SH/DX/RM)
#
# What this creates (seed/reference data — not building-specific):
#   - M_Product_Category: 127 IFC class + discipline + floor/room categories
#   - AD_Org: 16 discipline orgs (ARC, STR, FP, ELEC, ACMV, CW, SP, LPG...)
#   - ad_element_mep + ad_element_mep_alias: MEP element resolution
#   - ad_ifc_class_map: IFC class extraction authority
#   - ad_element_product_alias: abstract product naming
#   - ad_verb_pattern: verb detection hints
#   - M_BOM + M_BOM_Line: shared discipline recipes (FP_SYSTEM)
#   - AD_DocEvent_Rule: compliance rules (8 UBBL)
#   - ad_val_rule: capacity limits + mined dimension rules
#
# What the pipeline adds per building (ProductRegistrar, IFCtoERP):
#   - M_Product rows from IFC extraction (auto-categorized)
#   - _import_joint_piece_types from MEP joint piece extraction
#   - ad_mep_anchor + ad_mep_pattern from RouteWalker mining
#
# Implementing DISC_VALIDATION_DB_SRS.md §6.4 — Witness: W-DISC-CAT
set -euo pipefail

DB="library/ERP.db"
MIGRATION="migration"
COMPLIB="library/component_library.db"

# ── Pre-flight ──────────────────────────────────────────────────────
if [ ! -f "$COMPLIB" ]; then
    echo "FATAL: $COMPLIB not found. Cannot seed ERP.db." >&2
    exit 1
fi

if [ -f "$DB" ]; then
    echo "ERROR: $DB already exists. Rename/remove it first." >&2
    echo "  mv $DB ${DB%.db}.backup" >&2
    exit 1
fi

MODE="${1:---schema-only}"
ERRORS=0

echo "═══ rebuild_erp.sh: Creating $DB from scratch ═══"
echo ""

# Helper: apply a migration file
apply() {
    local sql="$1"
    local label="${2:-$sql}"
    if [ ! -f "$sql" ]; then
        echo "  SKIP (not found): $label"
        return 0
    fi
    if sqlite3 "$DB" < "$sql" 2>/tmp/erp_migrate_err.txt; then
        echo "  OK   $label"
    else
        echo "  FAIL $label" >&2
        cat /tmp/erp_migrate_err.txt >&2
        ERRORS=$((ERRORS + 1))
        return 1
    fi
}

# Helper: apply inline SQL
apply_sql() {
    local label="$1"
    local sql="$2"
    if echo "$sql" | sqlite3 "$DB" 2>/tmp/erp_migrate_err.txt; then
        echo "  OK   $label"
    else
        echo "  FAIL $label" >&2
        cat /tmp/erp_migrate_err.txt >&2
        ERRORS=$((ERRORS + 1))
        return 1
    fi
}

# Helper: apply migration, filtering out lines matching a pattern
apply_filtered() {
    local sql="$1"
    local label="$2"
    local exclude_pattern="$3"
    if [ ! -f "$sql" ]; then
        echo "  SKIP (not found): $label"
        return 0
    fi
    if grep -v "$exclude_pattern" "$sql" | sqlite3 "$DB" 2>/tmp/erp_migrate_err.txt; then
        echo "  OK   $label"
    else
        echo "  FAIL $label" >&2
        cat /tmp/erp_migrate_err.txt >&2
        ERRORS=$((ERRORS + 1))
        return 1
    fi
}

# ═══════════════════════════════════════════════════════════════════════
# Phase 0: Base schema + seeds (DV001-DV005)
# ═══════════════════════════════════════════════════════════════════════
echo "── Phase 0: Base schema + seeds ──"
apply "$MIGRATION/DV001_disc_validation_schema.sql"   "DV001 base schema (17 tables)"
apply "$MIGRATION/DV002_seed_from_component.sql"      "DV002 seed from component_library.db"
apply "$MIGRATION/DV003_element_mep_alias.sql"        "DV003 MEP alias (84 rows)"
apply "$MIGRATION/DV004_light_per_area.sql"           "DV004 light calibration"
apply "$MIGRATION/DV005_ifc_class_map.sql"            "DV005 IFC class authority (47 rows)"
echo ""

# ═══════════════════════════════════════════════════════════════════════
# Phase 1: Validation + advisory + profile tables
# ═══════════════════════════════════════════════════════════════════════
echo "── Phase 1: Validation tables ──"
apply "$MIGRATION/DV010_ad_val_rule_schema.sql"       "DV010 ad_val_rule schema"
apply "$MIGRATION/DV011_building_profile.sql"         "DV011 ad_building_profile"
apply "$MIGRATION/DV012_validation_advisory.sql"      "DV012 W_Validation_Advisory"
echo ""

# ═══════════════════════════════════════════════════════════════════════
# Phase 2: AD_Org (16 disciplines)
# ═══════════════════════════════════════════════════════════════════════
echo "── Phase 2: AD_Org disciplines ──"
apply "$MIGRATION/DV013_ad_org.sql"                   "DV013 AD_Org (16 disciplines)"
apply "$MIGRATION/DV014_ad_org_columns.sql"           "DV014 AD_Org FK columns"
echo ""

# ═══════════════════════════════════════════════════════════════════════
# Phase 3: M_Product_Category seed (the AD dictionary for products)
#
# S62 provides M_Product_Category DDL + 46 IFC leaf categories.
# It also has M_Product ALTER + 3 TE product INSERTs which we filter
# out — M_Product doesn't exist yet (created Phase 6) and the TE
# products are building-specific, not seed data.
# ProductRegistrar auto-creates all products from any IFC extraction.
# ═══════════════════════════════════════════════════════════════════════
echo "── Phase 3: M_Product_Category seed ──"
# S62 lines 1-96: M_Product_Category DDL + 46 category INSERTs (seed data).
# Lines 97+: M_Product ALTER + TE product INSERTs + verification (skip).
sed -n '1,96p' "$MIGRATION/S62_001_product_category_fp.sql" \
    | sqlite3 "$DB" 2>/tmp/erp_migrate_err.txt \
    && echo "  OK   S62 M_Product_Category (46 IFC leaf categories)" \
    || { echo "  FAIL S62"; cat /tmp/erp_migrate_err.txt >&2; ERRORS=$((ERRORS + 1)); }
echo ""

# ═══════════════════════════════════════════════════════════════════════
# Phase 4: Order schema + hierarchy
# DV017 skipped — renames columns on m_bom/ad_pattern_rule which don't
# exist yet. DV025 (Phase 6) creates m_bom with correct column names.
# ═══════════════════════════════════════════════════════════════════════
echo "── Phase 4: Order schema + hierarchy ──"
apply "$MIGRATION/S60_schema.sql"                     "S60 C_Order + C_OrderLine"
apply "$MIGRATION/DV016_pack_id.sql"                  "DV016 jurisdiction pack_id"
echo "  SKIP DV017 (column rename — target tables created later with correct names)"
apply "$MIGRATION/DV018_category_hierarchy.sql"       "DV018 category hierarchy (83 rows)"
echo ""

# ═══════════════════════════════════════════════════════════════════════
# Phase 5: Cleanup + Name/Value
# ═══════════════════════════════════════════════════════════════════════
echo "── Phase 5: Cleanup + Name/Value ──"
apply "$MIGRATION/DV019_move_bad_tables.sql"          "DV019 bad_* tables from component_library"
apply "$MIGRATION/DV020_drop_parent_category.sql"     "DV020 drop Parent_Category_ID (flat)"
apply "$MIGRATION/DV021_name_value_erp.sql"           "DV021 Name/Value on 10 tables"
echo ""

# ═══════════════════════════════════════════════════════════════════════
# Phase 6: M_Product + M_BOM + INTEGER PK conformance
#
# On fresh DB, M_Product doesn't exist yet (DV015 skipped, S62 filtered).
# DV023 expects to rename-copy M_Product — we create it directly instead.
# Then DV025 creates M_BOM (must come BEFORE DV027 which updates its FK).
# ═══════════════════════════════════════════════════════════════════════
echo "── Phase 6: Product + BOM + PK conformance ──"
apply "$MIGRATION/DV022_m_product_category_int_pk.sql" "DV022 Phase A sidecar"

# DV023 on fresh DB: M_Product doesn't exist, so rename-copy fails.
# Create the final M_Product schema directly (from DV023 + DV030 + DV033).
apply_sql "DV023+DV030+DV033 M_Product (final schema)" "
CREATE TABLE IF NOT EXISTS M_Product (
    M_Product_ID                INTEGER PRIMARY KEY AUTOINCREMENT,
    product_id                  TEXT NOT NULL,
    Value                       TEXT NOT NULL,
    Name                        TEXT,
    product_type                TEXT NOT NULL,
    width                       REAL NOT NULL,
    depth                       REAL NOT NULL,
    height                      REAL NOT NULL,
    ifc_class                   TEXT,
    extracted_from              TEXT NOT NULL DEFAULT 'IFC_EXTRACTION',
    is_active                   INTEGER DEFAULT 1,
    building_type               TEXT,
    unit_cost_rm                REAL DEFAULT 0,
    cost_uom                    TEXT DEFAULT 'EA',
    cost_spec                   TEXT,
    construction_phase          TEXT DEFAULT 'Architecture',
    construction_sequence       INTEGER DEFAULT 6,
    labor_resource              TEXT DEFAULT 'GENERAL',
    labor_rate_rm_per_day       REAL DEFAULT 145.0,
    labor_crew_size             INTEGER DEFAULT 2,
    productivity_rate           REAL DEFAULT 10.0,
    equipment_type              TEXT,
    equipment_rate_rm_per_day   REAL DEFAULT 0,
    equipment_duration_factor   REAL DEFAULT 0,
    carbon_kg_per_unit          REAL DEFAULT 0,
    recyclability               TEXT DEFAULT 'UNKNOWN',
    eol_strategy                TEXT DEFAULT 'LANDFILL',
    lifespan_years              INTEGER DEFAULT 50,
    maintenance_interval_months INTEGER DEFAULT 12,
    M_Product_Category_ID       INTEGER REFERENCES M_Product_Category(M_Product_Category_ID),
    source_element_ref          TEXT
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_m_product_product_id ON M_Product(product_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_m_product_value ON M_Product(Value);
"

apply "$MIGRATION/DV024_drop_int_sidecar.sql"          "DV024 drop sidecar"
apply "$MIGRATION/DV025_shared_recipes.sql"            "DV025 M_BOM + FP_SYSTEM recipe"
apply "$MIGRATION/DV026_docevent_rules.sql"            "DV026 DocEvent rules (8 UBBL)"
apply "$MIGRATION/DV027_category_integer_pk.sql"       "DV027 Phase B M_Product_Category INT PK"
apply "$MIGRATION/DV028_ad_integer_pk.sql"             "DV028 Phase C AD tables INT PK"
# DV030 skipped — M_Product already has final schema (created above)
echo "  SKIP DV030 (M_Product already at final schema)"
echo ""

# ═══════════════════════════════════════════════════════════════════════
# Phase 7: Post-PK seeds
# ═══════════════════════════════════════════════════════════════════════
echo "── Phase 7: Post-PK seeds ──"
apply "$MIGRATION/DV029_capacity_rules.sql"            "DV029 capacity rules"
apply "$MIGRATION/DV031_service_rooms.sql"             "DV031 service rooms"
apply "$MIGRATION/DV031_group1_group2_pk_rename.sql"   "DV031 group1/group2 rename"
echo ""

# ═══════════════════════════════════════════════════════════════════════
# Phase 8: Extensions + MEP tables
# DV036 _import_joint_piece_types UPDATE filtered — table is created
# at runtime by IFCtoERP, not by migration.
# ═══════════════════════════════════════════════════════════════════════
echo "── Phase 8: Extensions + MEP tables ──"
apply "$MIGRATION/W019_mep_anchor_tables.sql"          "W019 ad_mep_anchor + ad_mep_pattern"
apply "$MIGRATION/DV032_uom_correction.sql"            "DV032 UOM correction"
# DV033 skipped — source_element_ref already in M_Product (Phase 6)
echo "  SKIP DV033 (source_element_ref in final schema)"
apply "$MIGRATION/DV034_product_alias.sql"             "DV034 product alias (79 rows)"
apply "$MIGRATION/DV035_verb_pattern.sql"              "DV035 verb pattern (9 rows)"
# DV036 lines 1-84: AD_Org_ID column + discipline mappings + M_Product backfill.
# Lines 86-94: _import_joint_piece_types UPDATE (skip — table created by IFCtoERP).
# Lines 96-98: Schema version bump (keep).
{ sed -n '1,84p' "$MIGRATION/DV036_product_category_discipline.sql"; \
  sed -n '96,99p' "$MIGRATION/DV036_product_category_discipline.sql"; } \
    | sqlite3 "$DB" 2>/tmp/erp_migrate_err.txt \
    && echo "  OK   DV036 AD_Org_ID on M_Product_Category" \
    || { echo "  FAIL DV036"; cat /tmp/erp_migrate_err.txt >&2; ERRORS=$((ERRORS + 1)); }
echo ""

# ═══════════════════════════════════════════════════════════════════════
# Phase 8a: MEP metadata tables (DV037–DV042)
# These create tables/columns required by Phase 8b generative products.
# ═══════════════════════════════════════════════════════════════════════
echo "── Phase 8a: MEP metadata tables ──"
apply "$MIGRATION/DV037_mep_fixture_route.sql"           "DV037 anchor_end on ad_space_type_mep_bom"
apply "$MIGRATION/DV038_c_bpartner.sql"                  "DV038 C_BPartner"
apply "$MIGRATION/DV039_mep_rule_tables.sql"             "DV039 MEP rule tables"
apply "$MIGRATION/DV040_mep_space_identity.sql"          "DV040 MEP space identity"
apply "$MIGRATION/DV041_space_capability.sql"            "DV041 space capability"
apply "$MIGRATION/DV042_placement_offsets.sql"           "DV042 placement offsets"
echo ""

# ═══════════════════════════════════════════════════════════════════════
# Phase 8b: Generative MEP products + LOD bridge
# ═══════════════════════════════════════════════════════════════════════
echo "── Phase 8b: Generative MEP products ──"
for gf in DV043_generative_mep_products \
           DV044_space_type_ceiling_height DV045_product_dimensions \
           DV046_generative_product_lod_bridge DV047_fixture_tack_points DV048_unmapped_product_lod \
           DV049_fridge_kitchen_schedule; do
    [ -f "$MIGRATION/${gf}.sql" ] && apply "$MIGRATION/${gf}.sql" "$gf"
done
echo ""

# ═══════════════════════════════════════════════════════════════════════
# Phase 9: Per-building validation rules (mined from output DBs)
# ═══════════════════════════════════════════════════════════════════════
if [ "$MODE" = "--with-rules" ] || [ "$MODE" = "--full" ]; then
    echo "── Phase 9: Per-building validation rules ──"
    rule_count=0
    for rules_file in "$MIGRATION"/DV_*_rules.sql; do
        [ -f "$rules_file" ] || continue
        if sqlite3 "$DB" < "$rules_file" 2>/dev/null; then
            rule_count=$((rule_count + 1))
        fi
    done
    echo "  Applied $rule_count rule files"
    echo ""
fi

# ═══════════════════════════════════════════════════════════════════════
# Verification
# ═══════════════════════════════════════════════════════════════════════
echo "── Verification ──"
table_count=$(sqlite3 "$DB" "SELECT COUNT(*) FROM sqlite_master WHERE type='table';")
category_count=$(sqlite3 "$DB" "SELECT COUNT(*) FROM M_Product_Category;" 2>/dev/null || echo "0")
product_count=$(sqlite3 "$DB" "SELECT COUNT(*) FROM M_Product;" 2>/dev/null || echo "0")
org_count=$(sqlite3 "$DB" "SELECT COUNT(*) FROM AD_Org;" 2>/dev/null || echo "0")
has_ad_org_id=$(sqlite3 "$DB" "SELECT COUNT(*) FROM M_Product_Category WHERE AD_Org_ID IS NOT NULL;" 2>/dev/null || echo "0")
bom_count=$(sqlite3 "$DB" "SELECT COUNT(*) FROM M_BOM;" 2>/dev/null || echo "0")
rule_count=$(sqlite3 "$DB" "SELECT COUNT(*) FROM ad_val_rule;" 2>/dev/null || echo "0")

echo "  Tables:             $table_count"
echo "  M_Product_Category: $category_count rows ($has_ad_org_id with AD_Org_ID)"
echo "  M_Product:          $product_count rows (pipeline adds more per building)"
echo "  AD_Org:             $org_count rows"
echo "  M_BOM:              $bom_count rows"
echo "  ad_val_rule:        $rule_count rows"

schema_ver=$(sqlite3 "$DB" "SELECT Value FROM AD_SysConfig WHERE Name='SCHEMA_VERSION';" 2>/dev/null || echo "?")
echo "  Schema version:     $schema_ver"

chain_check=$(sqlite3 "$DB" "SELECT COUNT(*) FROM M_Product_Category c JOIN AD_Org o ON c.AD_Org_ID = o.AD_Org_ID;" 2>/dev/null || echo "0")
echo "  Category→AD_Org:    $chain_check linked"

if [ "$ERRORS" -gt 0 ]; then
    echo ""
    echo "  ERROR: $ERRORS migration(s) failed. Review output above."
    exit 1
fi
echo ""

# ═══════════════════════════════════════════════════════════════════════
# Phase 10 (optional): Pipeline — populates M_Product per building
# ═══════════════════════════════════════════════════════════════════════
if [ "$MODE" = "--full" ]; then
    echo "── Phase 10: Pipeline (SH → DX → RM) ──"
    echo "  Deleting stale BOM files..."
    rm -f library/SH_BOM.db library/DX_BOM.db library/RM_BOM.db

    for yaml in classify_sh.yaml classify_dx.yaml classify_rm.yaml; do
        prefix=$(echo "$yaml" | sed 's/classify_//;s/.yaml//;' | tr '[:lower:]' '[:upper:]')
        echo ""
        echo "  ── $prefix ──"
        ./scripts/run_RosettaStones.sh "$yaml" 2>&1 | tail -5
    done
fi

echo ""
echo "═══ rebuild_erp.sh: DONE ═══"
echo "  Database: $DB ($(du -h "$DB" | cut -f1))"
