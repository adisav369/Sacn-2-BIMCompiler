#!/bin/bash
# ============================================================
# BIM Compiler — YAML-Driven Rosetta Stone Pipeline
#
# PURPOSE: Compile buildings from classification YAML, producing:
#   *_BOM.db   = clean per-building BOM dictionary (IFCtoBOM pipeline)
#   *.db       = compilation output (C_OrderLine → BOM explosion → elements)
# Then run contract tests and fidelity checks against reference.
#
# ── ANTI-DRIFT: READ BEFORE EDITING ──────────────────────────
#
# NO monolithic BOM.db — only per-building *_BOM.db files.
# Compilation uses a temp _XX_compile.db (e.g. _SH_compile.db)
# passed to Java via -Dbom.db system property. Java code reads
# System.getProperty("bom.db") — never hardcodes a path.
#
# Session process (4 steps):
#   1. rm SH_BOM.db → re-extract (only when IFCtoBOM code changed)
#   2. rm DX_BOM.db → re-extract (only when IFCtoBOM code changed)
#   3. rm output/SH_*.db → recompile (only when DAGCompiler code changed)
#   4. rm output/DX_*.db → recompile (only when DAGCompiler code changed)
#
# Analysis docs (read before modifying a building):
#   SH: docs/DATA_MODEL.md, docs/BOMBasedCompilation.md
#   DX: docs/DuplexAnalysis.md
#   TE: docs/TerminalAnalysis.md
#
# YAML-DRIVEN: The classification YAML determines everything:
#   prefix          → BOM DB name ({PREFIX}_BOM.db)
#   building_type   → extraction source
#   doc_sub_type    → compilation parameter
#   product_category   → compilation parameter
#   building_bom_id → singularity check
#
# Modules (sourced):
#   lib_rosetta_helpers.sh  — parse_yaml, print_header, prepare/cleanup_compile_db
#   rosetta_compile.sh      — compile_building, singularity_check, run_contracts
#   rosetta_integrity.sh    — run_integrity (Rule 8 + clash)
#   rosetta_fidelity.sh     — run_fidelity (C8/C9 geometry checks)
#
# Usage:
#   ./scripts/run_RosettaStones.sh                          # all YAMLs in resources/
#   ./scripts/run_RosettaStones.sh classify_sh.yaml         # SH only
#   ./scripts/run_RosettaStones.sh classify_dx.yaml         # DX only
#   ./scripts/run_RosettaStones.sh classify_sh.yaml delta   # delta only (skip compile)
# ============================================================

set -e

SCRIPT_DIR="$(dirname "$0")"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_DIR"

source "$SCRIPT_DIR/log_helper.sh"
source "$SCRIPT_DIR/lib_rosetta_helpers.sh"
source "$SCRIPT_DIR/rosetta_compile.sh"
source "$SCRIPT_DIR/rosetta_integrity.sh"
source "$SCRIPT_DIR/rosetta_fidelity.sh"

init_log "run_RosettaStones"

YAML_DIR="IFCtoBOM/src/main/resources"

# ── Pre-flight: ensure component_library.db schema is current ──
comp_db="library/component_library.db"
if sqlite3 "$comp_db" "SELECT 1 FROM ad_geometry_map LIMIT 1" 2>/dev/null; then
    echo "  [pre-flight] Applying rename: ad_geometry_map → I_Geometry_Map"
    sqlite3 "$comp_db" < migration/migration_rename_geometry_map.sql
fi

# ── Parse arguments ─────────────────────────────────────────
YAML_FILES=()
DELTA_ONLY=false
DIFF_TSV=false

for arg in "$@"; do
    case "$arg" in
        delta) DELTA_ONLY=true ;;  # kept for backward compat (skips compile)
        --diff) DIFF_TSV=true ;;   # S60 #9: produce per-element TSV diff report
        *.yaml|*.yml)
            if [ -f "$arg" ]; then
                YAML_FILES+=("$arg")
            elif [ -f "${YAML_DIR}/$arg" ]; then
                YAML_FILES+=("${YAML_DIR}/$arg")
            else
                echo "[ERROR] YAML not found: $arg"
                exit 1
            fi
            ;;
        # Backward compat: bare prefix (sh, dx) → classify_{prefix}.yaml
        *)
            local_uc=$(echo "$arg" | tr '[:upper:]' '[:lower:]')
            if [ -f "${YAML_DIR}/classify_${local_uc}.yaml" ]; then
                YAML_FILES+=("${YAML_DIR}/classify_${local_uc}.yaml")
            else
                echo "[ERROR] Unknown argument: $arg (no ${YAML_DIR}/classify_${local_uc}.yaml)"
                exit 1
            fi
            ;;
    esac
done

# Default: run all 4 Product Category groups (RE, CO, IN, ST)
# DocType = ConstructionOrder (single). Grouping is by M_Product_Category.
if [ ${#YAML_FILES[@]} -eq 0 ]; then
    EXTRA_ARGS=""
    [ "$DELTA_ONLY" = "true" ] && EXTRA_ARGS="$EXTRA_ARGS delta"
    [ "$DIFF_TSV" = "true" ] && EXTRA_ARGS="$EXTRA_ARGS --diff"
    for pc in RE CO IN ST; do
        echo ""
        echo "╔══════════════════════════════════════════╗"
        echo "║  M_Product_Category: ${pc}"
        echo "╚══════════════════════════════════════════╝"
        "$SCRIPT_DIR/run_RosettaStones_${pc}.sh" $EXTRA_ARGS || true
    done
    exit 0
fi

if [ ${#YAML_FILES[@]} -eq 0 ]; then
    echo "[ERROR] No classification YAMLs found in ${YAML_DIR}/"
    exit 1
fi

# ── Main Loop ───────────────────────────────────────────────

# Compile Java (unless delta-only)
if [ "$DELTA_ONLY" != "true" ]; then
    print_header "COMPILE (all modules)"
    mvn install -pl orm-core,ORMSandbox -DskipTests -q
    mvn compile -pl DAGCompiler -q
    echo "  Compile: OK"
fi

echo ""
echo "  YAML files: ${YAML_FILES[*]}"

# Process each YAML
for yaml_file in "${YAML_FILES[@]}"; do
    # Parse YAML fields
    PREFIX=$(parse_yaml "$yaml_file" "prefix")
    BUILDING_TYPE=$(parse_yaml "$yaml_file" "building_type")
    DOC_SUB_TYPE=$(parse_yaml "$yaml_file" "doc_sub_type")
    PRODUCT_CATEGORY=$(parse_yaml "$yaml_file" "product_category")
    BLDG_NAME=$(parse_yaml "$yaml_file" "name")
    BUILDING_BOM_ID=$(parse_yaml "$yaml_file" "building_bom_id")
    PROVENANCE=$(parse_yaml "$yaml_file" "provenance")

    BOM_DB="library/${PREFIX}_BOM.db"
    OUTPUT_BASE="DAGCompiler/lib/output/$(echo "$BUILDING_TYPE" | tr '[:upper:]' '[:lower:]')"

    print_header "BUILDING: ${PREFIX} (${BLDG_NAME})"
    echo "  YAML:           $(basename "$yaml_file")"
    echo "  BOM DB:         ${BOM_DB}"
    echo "  Building type:  ${BUILDING_TYPE}"
    echo "  DocType:        ${PRODUCT_CATEGORY}_${DOC_SUB_TYPE}"
    echo "  Building BOM:   ${BUILDING_BOM_ID}"
    echo "  Provenance:     ${PROVENANCE:-EXTRACTED}"
    echo "  Output base:    ${OUTPUT_BASE}"

    # Step 0: Produce *_BOM.db (IFCtoBOM for EXTRACTED, seed SQL for GENERATIVE)
    if [ "$DELTA_ONLY" != "true" ]; then

        if [ "$PROVENANCE" = "GENERATIVE" ]; then
            # ── GENERATIVE path: seed BOM from SQL, skip IFCtoBOM extraction ──
            section "GENERATIVE Seed (${PREFIX})"
            SEED_SQL="migration/seed_${PREFIX,,}_bom.sql"
            if [ -f "$SEED_SQL" ]; then
                rm -f "$BOM_DB"
                # Apply full BOM schema first (metadata tables need all columns),
                # then seed data (INSERT OR REPLACE into existing tables)
                sed 's/CREATE TABLE \([^I"]\)/CREATE TABLE IF NOT EXISTS \1/g; s/CREATE TABLE "\([^I]\)/CREATE TABLE IF NOT EXISTS "\1/g' \
                    library/schema_snapshot_bom.sql | sqlite3 "$BOM_DB" 2>/dev/null || true
                sqlite3 "$BOM_DB" < "$SEED_SQL"
                bom_count=$(sqlite3 "$BOM_DB" "SELECT COUNT(*) FROM m_bom" 2>/dev/null || echo "0")
                line_count=$(sqlite3 "$BOM_DB" "SELECT COUNT(*) FROM m_bom_line WHERE component_type='LEAF'" 2>/dev/null || echo "0")
                echo "  [seed] ${BOM_DB}: ${bom_count} BOMs, ${line_count} LEAF elements from ${SEED_SQL}"
                verdict "SEED_${PREFIX}" "PASS" "${bom_count} BOMs, ${line_count} LEAF elements"
            else
                echo "  [seed] MISSING: ${SEED_SQL}"
                verdict "SEED_${PREFIX}" "FAIL" "no seed SQL found: ${SEED_SQL}"
            fi

            # Add DSLContent to C_DocType if dsl file exists
            DSL_FILE="IFCtoBOM/src/main/resources/dsl_${PREFIX,,}.bim"
            if [ -f "$DSL_FILE" ] && [ -f "$BOM_DB" ]; then
                DSL_CONTENT=$(cat "$DSL_FILE")
                sqlite3 "$BOM_DB" "UPDATE C_DocType SET DSLContent = '$(echo "$DSL_CONTENT" | sed "s/'/''/g")' WHERE C_DocType_ID = '${PRODUCT_CATEGORY}_${DOC_SUB_TYPE}'" 2>/dev/null || true
            fi
        else
            # ── EXTRACTED path: normal IFCtoBOM pipeline ──
            section "IFCtoBOM Pipeline (${PREFIX})"

            # ── Populate product catalog (skip if geometry AND product images exist) ──
            # Both I_Geometry_Map (geometry) and M_Product_Image (product→geometry link) must be populated.
            comp_db="library/component_library.db"
            geom_count=$(sqlite3 "$comp_db" \
                "SELECT COUNT(*) FROM I_Geometry_Map WHERE building_type='${BUILDING_TYPE}'" 2>/dev/null || echo "0")
            image_count=$(sqlite3 "$comp_db" \
                "SELECT COUNT(*) FROM M_Product_Image i JOIN M_Product p ON i.M_Product_ID = p.product_id WHERE p.building_type='${BUILDING_TYPE}'" 2>/dev/null || echo "0")

            if [ "$geom_count" = "0" ] || [ "$image_count" = "0" ]; then
                echo "  [populate] Populating component_library.db for ${BUILDING_TYPE}..."
                POP_OUTPUT=""
                POP_RC=0
                POP_OUTPUT=$(mvn exec:java -pl IFCtoBOM \
                    -Dexec.mainClass="com.bim.ifctobom.IFCtoBOMMain" \
                    -Dexec.args="--populate --classify ${yaml_file}" \
                    -q 2>&1) && POP_RC=0 || POP_RC=$?
                echo "$POP_OUTPUT" | grep -E '^\[populate\]' || true

                if [ "$POP_RC" -ne 0 ]; then
                    verdict "POPULATE_${PREFIX}" "FAIL" "populate failed"
                    echo "$POP_OUTPUT" | grep -E "ERROR|Exception|FAIL" | head -5 | sed 's/^/    /'
                else
                    verdict "POPULATE_${PREFIX}" "PASS" "component_library.db populated"
                fi
            else
                echo "  [populate] Already populated: ${geom_count} geometries, ${image_count} images for ${BUILDING_TYPE} — skipping"
            fi

            # Run IFCtoBOM via CLI (YAML-driven, produces *_BOM.db)
            IFC_OUTPUT=""
            IFC_RC=0
            IFC_OUTPUT=$(mvn exec:java -pl IFCtoBOM \
                -Dexec.mainClass="com.bim.ifctobom.IFCtoBOMMain" \
                -Dexec.args="--classify ${yaml_file} --bom-db ${BOM_DB}" \
                -q 2>&1) && IFC_RC=0 || IFC_RC=$?
            echo "$IFC_OUTPUT" | grep -E '^\[IFCtoBOM\]|^=== |^  \[|^\[QA\]|^  Floor' || true

            if [ "$IFC_RC" -ne 0 ]; then
                verdict "IFCTOBOM_${PREFIX}" "FAIL" "IFCtoBOM pipeline failed"
                echo "$IFC_OUTPUT" | grep -E "ERROR|Exception|FAIL" | head -5 | sed 's/^/    /'
            else
                verdict "IFCTOBOM_${PREFIX}" "PASS" "${BOM_DB} produced"
            fi
        fi

        # Prepare per-building compile DB (e.g. library/_SH_compile.db)
        if prepare_compile_db "$PREFIX" "$BUILDING_TYPE" "$DOC_SUB_TYPE" "$PRODUCT_CATEGORY" "$BLDG_NAME" "$BUILDING_BOM_ID" "$yaml_file"; then
            # Run compilation — passes -Dbom.db to Maven
            compile_building "$DOC_SUB_TYPE" "$OUTPUT_BASE" "$BOM_DB" "$COMPILE_DB" "$PRODUCT_CATEGORY"
            cleanup_compile_db

            # G0 check: verify output has c_order rows (not extraction-only)
            order_count=$(sqlite3 "${OUTPUT_BASE}.db" "SELECT COUNT(*) FROM c_order" 2>/dev/null || echo "0")
            if [ "$order_count" -eq 0 ]; then
                echo "  [WARN] ${PREFIX}: output.db has 0 c_order rows — compilation may not have run"
            fi
        fi
    fi

    # Integrity checks (Rule 8, clash)
    run_integrity "$DOC_SUB_TYPE" "${OUTPUT_BASE}.db" "$BOM_DB"

    # Fidelity test — reference (input) vs output (C8/C9)
    # Skip for GENERATIVE buildings (no reference DB — DM defines the reference)
    if [ "$PROVENANCE" = "GENERATIVE" ]; then
        echo "  [fidelity] SKIP — GENERATIVE building (no reference DB)"
    else
        REF_DB="DAGCompiler/lib/input/${BUILDING_TYPE}_extracted.db"
        run_fidelity "$DOC_SUB_TYPE" "${OUTPUT_BASE}.db" "$REF_DB"

        # S60 #9: Visual diff TSV report (--diff flag)
        if [ "$DIFF_TSV" = "true" ] && [ -f "${OUTPUT_BASE}.db" ] && [ -f "$REF_DB" ]; then
            TSV_FILE="logs/diff_${DOC_SUB_TYPE}.tsv"
            mvn exec:java -pl BIMEyes \
                -Dexec.mainClass="com.bim.eyes.diff.SpatialDiff" \
                -Dexec.args="$REF_DB ${OUTPUT_BASE}.db $TSV_FILE" \
                -q 2>&1 | tail -1
        fi
    fi
done

# ── Validation rule extraction (idempotent — INSERT OR IGNORE) ──
for yaml_file in "${YAML_FILES[@]}"; do
    PREFIX=$(parse_yaml "$yaml_file" "prefix")
    OUTPUT_BASE="DAGCompiler/lib/output/$(parse_yaml "$yaml_file" "building_type" | tr '[:upper:]' '[:lower:]')"
    if [ -f "${OUTPUT_BASE}.db" ] && [ -f "scripts/extract_validation_rules.sh" ]; then
        RULES_FILE="migration/DV_${PREFIX}_rules.sql"
        ./scripts/extract_validation_rules.sh "$PREFIX" > "$RULES_FILE" 2>/dev/null
        rule_count=$(grep -c "^-- Rule:" "$RULES_FILE" 2>/dev/null || true)
        rule_count=${rule_count:-0}
        if [ "$rule_count" -gt 0 ]; then
            sqlite3 library/ERP.db < "$RULES_FILE" 2>/dev/null || true
            echo "  [validation] ${PREFIX}: ${rule_count} rules extracted + applied"
        fi
    fi
done

# ── Summary ──────────────────────────────────────────────────
print_header "ROSETTA STONE SUMMARY"
echo "  Pipeline: YAML → IFCtoBOM → *_BOM.db → DAGCompiler → *.db"
echo ""
echo "  Single compilation path: C_OrderLine → BOM explosion → elements."
echo "  Contract tests (G1-G6) + fidelity (C8/C9) verify correctness."
echo ""
echo "  YAMLs processed:"
for yf in "${YAML_FILES[@]}"; do
    yf_prefix=$(parse_yaml "$yf" "prefix")
    echo "    $(basename "$yf") → library/${yf_prefix}_BOM.db"
done
echo ""
finish_log

if [ "$LOG_FAIL" -gt 0 ]; then
    exit 1
fi
