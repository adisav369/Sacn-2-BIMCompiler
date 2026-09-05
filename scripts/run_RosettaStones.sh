#!/bin/bash
# ============================================================
# BIM Compiler — Rosetta Stone Pipeline
#
# PURPOSE: Extract IFC → *_BOM.db, compile → output.db,
#   then run contract tests (G1-G6) and fidelity checks (C8/C9).
#
# ── ANTI-DRIFT: READ BEFORE EDITING ──────────────────────────
#
# NO monolithic BOM.db — only per-building *_BOM.db files.
# Compilation uses a temp _XX_compile.db (e.g. _SH_compile.db)
# passed to Java via -Dbom.db system property.
#
# YAML is Order input only (BIM Designer opt-in). For Rosetta
# Stone EXTRACTED buildings, the IFC file is the sole spatial
# source. YAML provides prefix/building_type/rootBOM identity.
#
# Per-category scripts (RE/CO/IN/ST) archived to scripts/archive/.
# All buildings now run in a single loop. Failures are logged,
# not fatal — the fleet continues through all buildings.
#
# Modules (sourced):
#   lib_rosetta_helpers.sh  — parse_yaml, print_header, prepare/cleanup_compile_db
#   rosetta_compile.sh      — compile_building, singularity_check, run_contracts
#   rosetta_integrity.sh    — run_integrity (Rule 8 + clash)
#   rosetta_fidelity.sh     — run_fidelity (C8/C9 geometry checks)
#
# Usage:
#   ./scripts/run_RosettaStones.sh                          # all buildings
#   ./scripts/run_RosettaStones.sh classify_sh.yaml         # SH only
#   ./scripts/run_RosettaStones.sh classify_sh.yaml delta   # delta only (skip compile)
# ============================================================


# --- utf8-console guard (2026-09-05) ------------------------------------------
# Belt to the per-script Python guard's braces. Every Python file in this repo that prints a
# non-ASCII LITERAL now reconfigures its own stdout, but that detection cannot see non-ASCII
# arriving at RUNTIME (a material name, an IFC family, a path). PYTHONUTF8 makes the
# interpreter use UTF-8 for stdio regardless, so nothing launched from this script can die on
# a Windows cp1252 console the way restore_generative_meshes.py did -- it created its
# back-compat view and then crashed on the very next print, before restoring any mesh.
export PYTHONUTF8=1
export PYTHONIOENCODING=utf-8
# ------------------------------------------------------------------------------

set -uo pipefail
# NOTE: set -e deliberately omitted — individual building failures must not
# kill the fleet. Each building handles its own errors via verdict().

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

# Restore generative-device geometry (map rows + meshes) dropped/dangled by the
# 2026-06-22 resync — idempotent, NON-INVENT from master + committed source extractions.
# MUST precede CL_001 (which seeds M_Product_Image from these I_Geometry_Map rows).
python3 scripts/restore_generative_meshes.py

# Seed M_Product_Image for generative MEP products (CL_001 — idempotent)
sqlite3 "$comp_db" < migration/CL_001_generative_product_images.sql 2>/dev/null

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

# Default: run all EXTRACTED classify_*.yaml (skip GENERATIVE — BIM Designer domain)
if [ ${#YAML_FILES[@]} -eq 0 ]; then
    for f in "${YAML_DIR}"/classify_*.yaml; do
        if [ -f "$f" ]; then
            prov=$(parse_yaml "$f" "provenance")
            if [ "$prov" = "GENERATIVE" ]; then
                echo "  [skip] $(basename "$f") — GENERATIVE (BIM Designer domain)"
            else
                YAML_FILES+=("$f")
            fi
        fi
    done
fi

if [ ${#YAML_FILES[@]} -eq 0 ]; then
    echo "[ERROR] No classification YAMLs found in ${YAML_DIR}/"
    exit 1
fi

echo "  Buildings: ${#YAML_FILES[@]}"

# ── Main Loop ───────────────────────────────────────────────

# Compile Java (unless delta-only)
if [ "$DELTA_ONLY" != "true" ]; then
    print_header "COMPILE (all modules)"
    if ! mvn install -pl orm-core,ORMSandbox -DskipTests -q; then
        echo "  [FATAL] Java compile failed"; exit 1
    fi
    if ! mvn compile -pl DAGCompiler -q; then
        echo "  [FATAL] DAGCompiler compile failed"; exit 1
    fi
    echo "  Compile: OK"
fi

echo ""

# Fleet result collectors (for summary table at end)
declare -a FLEET_LINES=()
declare -a FLEET_DIAG=()   # §S190: per-building failure reason for diagnostic report
FLEET_IDX=0

# Process each building
for yaml_file in "${YAML_FILES[@]}"; do
    # Save verdict counters at start of this building
    _BLDG_PASS_START=$LOG_PASS
    _BLDG_FAIL_START=$LOG_FAIL
    _BLDG_WARN_START=$LOG_WARN
    # Parse building identity from YAML
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
    echo "  BOM DB:         ${BOM_DB}"
    echo "  Building type:  ${BUILDING_TYPE}"
    echo "  Category:       ${PRODUCT_CATEGORY}"
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
                line_count=$(sqlite3 "$BOM_DB" "SELECT COUNT(*) FROM m_bom_line WHERE child_product_id NOT IN (SELECT Value FROM m_bom)" 2>/dev/null || echo "0")
                echo "  [seed] ${BOM_DB}: ${bom_count} BOMs, ${line_count} LEAF elements from ${SEED_SQL}"
                verdict "SEED_${PREFIX}" "PASS" "${bom_count} BOMs, ${line_count} LEAF elements"
            else
                echo "  [seed] MISSING: ${SEED_SQL}"
                verdict "SEED_${PREFIX}" "FAIL" "no seed SQL found: ${SEED_SQL}"
            fi

            # DSL files removed (S140) — YAML is sole intent source
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
                # §S190: Show QA reconciliation delta detail
                RECON_LINE=""
                RECON_LINE=$(echo "$IFC_OUTPUT" | grep -o 'Extraction reconciliation.*' | head -1)
                [ -n "$RECON_LINE" ] && echo "    RECON: ${RECON_LINE}"
            else
                verdict "IFCTOBOM_${PREFIX}" "PASS" "${BOM_DB} produced"
            fi

            # ── IFCtoERP: Joint piece + shim extraction (§6.12.2 J1+J2) ──
            JE_OUTPUT=""
            JE_RC=0
            JE_OUTPUT=$(mvn exec:java -pl IFCtoBOM \
                -Dexec.mainClass="com.bim.ifctobom.IFCtoBOMMain" \
                -Dexec.args="--joint-extract --classify ${yaml_file}" \
                -q 2>&1) && JE_RC=0 || JE_RC=$?
            echo "$JE_OUTPUT" | grep -E '^\[joint-extract\]|^\[IFCtoERP\]|^\[MEP-SPACE' || true

            if [ "$JE_RC" -ne 0 ]; then
                verdict "JOINT_EXTRACT_${PREFIX}" "FAIL" "joint piece extraction failed"
                echo "$JE_OUTPUT" | grep -E "ERROR|Exception|FAIL" | head -5 | sed 's/^/    /'
            else
                verdict "JOINT_EXTRACT_${PREFIX}" "PASS" "joint pieces extracted"
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

        # §S190: GEO white-box verification (all-pairs relative offset proof)
        GEO_LOG=""
        GEO_LOG=$(ls -t logs/pipeline_*"${BUILDING_TYPE}"*_extracted_*.log 2>/dev/null \
                  | head -1)
        if [ -n "$GEO_LOG" ] && [ -f "${OUTPUT_BASE}.db" ] && [ -f "$REF_DB" ] \
           && grep -q '\[GEO  \] TACK' "$GEO_LOG" 2>/dev/null; then
            echo ""
            print_header "GEO VERIFY ${PREFIX}: white-box offset proof"
            GEO_OUT=""; GEO_RC=0
            GEO_OUT=$(python3 scripts/geo_verify.py "$GEO_LOG" "${OUTPUT_BASE}.db" "$REF_DB" 2>&1) \
                && GEO_RC=0 || GEO_RC=$?
            # Show verdict line + stats
            echo "$GEO_OUT" | grep -E 'Elements:|Pairs:|MATCH:|DRIFT:|Worst:|VERDICT:' | sed 's/^/  /'
            if echo "$GEO_OUT" | grep -q 'ZERO DRIFT'; then
                verdict "GEO_VERIFY_${PREFIX}" "PASS" "$(echo "$GEO_OUT" | grep 'VERDICT:' | sed 's/VERDICT: //')"
            elif [ "$GEO_RC" -ne 0 ]; then
                verdict "GEO_VERIFY_${PREFIX}" "WARN" "geo_verify.py returned ${GEO_RC}"
            else
                verdict "GEO_VERIFY_${PREFIX}" "FAIL" "$(echo "$GEO_OUT" | grep 'VERDICT:' | sed 's/VERDICT: //')"
            fi
        fi

        # S60 #9: Visual diff TSV report (--diff flag)
        if [ "$DIFF_TSV" = "true" ] && [ -f "${OUTPUT_BASE}.db" ] && [ -f "$REF_DB" ]; then
            TSV_FILE="logs/diff_${DOC_SUB_TYPE}.tsv"
            mvn exec:java -pl BIMEyes \
                -Dexec.mainClass="com.bim.eyes.diff.SpatialDiff" \
                -Dexec.args="$REF_DB ${OUTPUT_BASE}.db $TSV_FILE" \
                -q 2>&1 | tail -1
        fi
    fi

    # ── Per-building one-line summary ──
    _BP=$((LOG_PASS - _BLDG_PASS_START))
    _BF=$((LOG_FAIL - _BLDG_FAIL_START))
    _BW=$((LOG_WARN - _BLDG_WARN_START))
    _BT=$((_BP + _BF + _BW))

    # PATTERN: storey assignment from latest ifctobom log
    _PATRN_LOG=$(ls -t logs/pipeline_"${BUILDING_TYPE}"_ifctobom_*.log 2>/dev/null | head -1)
    _STOREYS="-"; _UNKNOWN="-"; _TOTAL_EL="-"
    if [ -n "$_PATRN_LOG" ] && [ -f "$_PATRN_LOG" ]; then
        _STOREYS=$(grep -c "\[PATRN\] FLOOR" "$_PATRN_LOG" 2>/dev/null || echo 0)
        _UNKNOWN=$(grep "\[PATRN\] FLOOR.*Unknown" "$_PATRN_LOG" 2>/dev/null | grep -oP '\d+ elements' | head -1 | grep -oP '\d+' || echo 0)
        _TOTAL_EL=$(grep "\[PATRN\] FLOOR" "$_PATRN_LOG" 2>/dev/null | grep -oP '\d+ elements' | grep -oP '\d+' | paste -sd+ | bc 2>/dev/null || echo "?")
    fi
    # Fallback: element count from BOM.db
    if [ "$_TOTAL_EL" = "-" ] || [ "$_TOTAL_EL" = "?" ]; then
        _TOTAL_EL=$(sqlite3 "$BOM_DB" "SELECT SUM(qty) FROM m_bom_line WHERE is_active = 1" 2>/dev/null || echo "?")
    fi

    # Status word
    if [ "$_BF" -gt 0 ]; then _STATUS="FAIL"
    elif [ "$_BW" -gt 0 ]; then _STATUS="WARN"
    else _STATUS="PASS"; fi

    # §S190: Collect per-building failure reason for diagnostic report
    _DIAG=""
    if [ "$_BF" -gt 0 ]; then
        # Check surefire for root cause
        _SF="DAGCompiler/target/surefire-reports/com.bim.compiler.contract.BuildingRegistryTest.txt"
        if [ -f "$_SF" ] && grep -q 'MetadataMissing' "$_SF" 2>/dev/null; then
            _DIAG="MetadataMissing: $(grep -o 'familyRef=[^ ]*' "$_SF" | head -1)"
        elif [ -f "$_SF" ] && grep -q 'No enum constant' "$_SF" 2>/dev/null; then
            _DIAG="Enum: $(grep -o 'No enum constant [^ ]*' "$_SF" | head -1)"
        elif [ -f "$_SF" ] && grep -q 'count mismatch' "$_SF" 2>/dev/null; then
            _DIAG="CountMismatch: $(grep -o 'expected:.*but was:.*' "$_SF" | head -1)"
        fi
        # Check IFCtoBOM reconciliation (only non-zero delta = real failure)
        if [ -n "$_PATRN_LOG" ]; then
            _RECON=""
            _RECON=$(grep 'Extraction reconciliation.*delta=' "$_PATRN_LOG" 2>/dev/null \
                     | grep -v 'delta=+0' | grep -o 'delta=[+-]*[0-9]*' | head -1)
            [ -n "$_RECON" ] && _DIAG="${_DIAG:+${_DIAG}; }RECON: ${_RECON}"
        fi
        # Check for C8 fidelity loss (search 20 lines after FIDELITY header)
        if [ -z "$_DIAG" ] && grep -q "FIDELITY ${PREFIX}:" "$LOG_FILE" 2>/dev/null; then
            _C8=$(grep -A20 "FIDELITY ${PREFIX}:" "$LOG_FILE" 2>/dev/null \
                  | grep -o '[0-9]* product type(s) lost' | head -1)
            [ -n "$_C8" ] && _DIAG="C8: ${_C8}"
        fi
        [ -z "$_DIAG" ] && _DIAG="(check log)"
    fi

    # Collect for fleet table
    FLEET_LINES[$FLEET_IDX]=$(printf "%-4s %-24s %5s  %2s/%2s  %s storeys  unk=%s" \
        "$PREFIX" "$BLDG_NAME" "$_TOTAL_EL" "$_BP" "$_BT" "$_STOREYS" "$_UNKNOWN")
    FLEET_DIAG[$FLEET_IDX]="$_DIAG"
    FLEET_IDX=$((FLEET_IDX + 1))

    # Print one-line summary
    echo ""
    printf "  ▸ %-4s  %5s el  %s/%s gates  %s storeys  unk=%-5s  %s\n" \
        "$PREFIX" "$_TOTAL_EL" "$_BP" "$_BT" "$_STOREYS" "$_UNKNOWN" "$_STATUS"
    echo ""
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

# ── Fleet Summary Table ──────────────────────────────────────
print_header "FLEET SUMMARY"
echo ""
printf "  %-4s %-24s %5s  %5s  %7s  %7s\n" "PFX" "BUILDING" "EL" "GATES" "STOREYS" "UNKNOWN"
printf "  %-4s %-24s %5s  %5s  %7s  %7s\n" "----" "------------------------" "-----" "-----" "-------" "-------"
for ((i=0; i<FLEET_IDX; i++)); do
    echo "  ${FLEET_LINES[$i]}"
    # §S190: Show per-building failure reason inline
    if [ -n "${FLEET_DIAG[$i]:-}" ]; then
        echo "         → ${FLEET_DIAG[$i]}"
    fi
done
echo ""
echo "  Pipeline: IFC → *_BOM.db → DAGCompiler → output.db"
echo ""

# ── §S190: Black-Box Diagnostic Report ──────────────────────
# Post-run summary: categorise failures by root cause for triage.
print_header "DIAGNOSTIC REPORT"
echo ""

# Scan surefire reports for last-seen errors
SUREFIRE="DAGCompiler/target/surefire-reports/com.bim.compiler.contract.BuildingRegistryTest.txt"
if [ -f "$SUREFIRE" ]; then
    LAST_ERROR=$(grep -E "Exception:|Error:" "$SUREFIRE" | head -1)
    [ -n "$LAST_ERROR" ] && echo "  Last compile error: ${LAST_ERROR}"
fi

# Categorise failures from log
echo ""
echo "  FAILURE CATEGORIES:"

# 1. Extraction reconciliation (QA ABORT) — only [FAIL] lines
RECON_FAILS=$(grep '\[FAIL\] Extraction reconciliation' "$LOG_FILE" 2>/dev/null | grep -c 'delta=' || echo 0)
RECON_FAILS=${RECON_FAILS//[^0-9]/}; RECON_FAILS=${RECON_FAILS:-0}
if [ "$RECON_FAILS" -gt 0 ]; then
    echo "    [RECON] ${RECON_FAILS} building(s) — extraction LEAFs vs extracted element count mismatch"
    grep '\[FAIL\] Extraction reconciliation' "$LOG_FILE" 2>/dev/null \
        | grep -oP '\d+ extraction LEAFs vs \d+ extracted \(delta=[+-]?\d+\)' \
        | sort -u | sed 's/^/      /' | head -5
    echo "      FIX: populate missing element types in IFCtoBOM, or relax QA threshold"
fi

# 2. MetadataMissingException (compile crash)
META_FAILS=$(grep -c 'MetadataMissingException' "$LOG_FILE" 2>/dev/null || echo 0)
META_FAILS=${META_FAILS//[^0-9]/}; META_FAILS=${META_FAILS:-0}
if [ "$META_FAILS" -gt 0 ]; then
    echo "    [GEOMETRY-GAP] ${META_FAILS} building(s) — elements without geometry in component_library.db"
    grep -o 'familyRef=[^ ]*' "$LOG_FILE" 2>/dev/null | sed 's/[;,]*$//' | sort -u | head -5 | sed 's/^/      /'
    echo "      FIX: add component_definitions + component_geometries for missing familyRefs"
fi

# 3. Empty extracted DBs (no IFC file)
EMPTY_DBS=$(grep -c 'Reference DB missing or empty' "$LOG_FILE" 2>/dev/null || echo 0)
EMPTY_DBS=${EMPTY_DBS//[^0-9]/}; EMPTY_DBS=${EMPTY_DBS:-0}
if [ "$EMPTY_DBS" -gt 0 ]; then
    echo "    [NO-IFC] ${EMPTY_DBS} building(s) — extracted DB missing or empty"
    echo "      FIX: run extraction (python3 tools/extract.py) or remove YAML"
fi

# 4. C8 fidelity (mesh diversity loss)
C8_FAILS=$(grep -c 'product type(s) lost mesh diversity' "$LOG_FILE" 2>/dev/null || echo 0)
C8_FAILS=${C8_FAILS//[^0-9]/}; C8_FAILS=${C8_FAILS:-0}
if [ "$C8_FAILS" -gt 0 ]; then
    echo "    [C8-DIVERSITY] ${C8_FAILS} building(s) — mesh diversity lost"
    grep 'lost mesh diversity' "$LOG_FILE" 2>/dev/null | head -3 | sed 's/^.*FAIL — /      /'
fi

# 5. Enum / schema errors
ENUM_FAILS=$(grep -c 'No enum constant' "$LOG_FILE" 2>/dev/null || echo 0)
ENUM_FAILS=${ENUM_FAILS//[^0-9]/}; ENUM_FAILS=${ENUM_FAILS:-0}
if [ "$ENUM_FAILS" -gt 0 ]; then
    echo "    [ENUM] ${ENUM_FAILS} — Java enum does not cover DB value"
    grep -o 'No enum constant [^ ]*' "$LOG_FILE" 2>/dev/null | sort -u | head -3 | sed 's/^/      /'
fi

# Summary counts — use FLEET_DIAG to check: empty diag = all green
ALL_GREEN=0
for ((i=0; i<FLEET_IDX; i++)); do
    [ -z "${FLEET_DIAG[$i]:-}" ] && ALL_GREEN=$((ALL_GREEN + 1))
done
echo ""
echo "  TOTALS: ${LOG_PASS} PASS, ${LOG_FAIL} FAIL, ${LOG_WARN} WARN | ${ALL_GREEN} ALL GREEN out of ${FLEET_IDX} buildings"
echo ""

finish_log

if [ "$LOG_FAIL" -gt 0 ]; then
    exit 1
fi
