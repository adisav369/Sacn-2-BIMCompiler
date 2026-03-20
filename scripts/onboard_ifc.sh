#!/bin/bash
# ============================================================
# BIM Compiler — IFC Onboarding Pipeline (End-to-End)
#
# Takes a raw IFC file through the complete onboarding process:
#
#   Step 1: Reconnaissance     — ifc_recon.py (fast, no extraction)
#   Step 2: Extraction         — extract.py → {type}_extracted.db
#   Step 3: Skeleton generation — classify_XX.yaml + dsl_XX.bim
#   Step 4: Manifest registration
#   Step 5: GATE_SCOPE registration
#   Step 6: Pipeline           — run_RosettaStones.sh
#   Step 7: Validation rules   — extract_validation_rules.sh
#   Step 8: Report             — rosetta_report.sh
#
# Usage:
#   ./scripts/onboard_ifc.sh --prefix SC --type Schependomlaan \
#       --name "Schependomlaan Residential" --base RE \
#       --ifc DAGCompiler/lib/input/IFC/Schependomlaan_IFC2x3.ifc
#
#   ./scripts/onboard_ifc.sh --prefix CA --type Clinic_Architecture \
#       --name "Clinic Architecture" --base RE \
#       --ifc DAGCompiler/lib/input/IFC/Clinic_Architectural_IFC2x3.ifc
#
# Options:
#   --prefix XX     2-3 char uppercase code (required)
#   --type TYPE     Building type / extracted DB name (required)
#   --name NAME     Human-readable name (required)
#   --base RE|CO|IN Doc base type (default: RE)
#   --ifc PATH      Path to IFC source file (required)
#   --skip-recon    Skip reconnaissance step
#   --skip-extract  Skip extraction (use existing extracted DB)
#   --dry-run       Show what would be done without doing it
#
# Prerequisites:
#   - Python3 with ifcopenshell
#   - mvn compile passes
#   - See docs/IFC_ONBOARDING_RUNBOOK.md for full guide
# ============================================================

set -e

SCRIPT_DIR="$(dirname "$0")"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_DIR"

# ── Colors ───────────────────────────────────────────────────
B="\033[1m"
R="\033[0m"
G="\033[32m"
Y="\033[33m"
RD="\033[31m"
D="\033[90m"

step() { printf "\n${B}═══ Step %s: %s${R}\n\n" "$1" "$2"; }
ok()   { printf "  ${G}✓${R} %s\n" "$1"; }
skip() { printf "  ${Y}○${R} %s\n" "$1"; }
fail() { printf "  ${RD}✗${R} %s\n" "$1"; }
info() { printf "  %s\n" "$1"; }

# ── Parse arguments ──────────────────────────────────────────
PREFIX=""
BUILDING_TYPE=""
HUMAN_NAME=""
DOC_BASE="RE"
IFC_PATH=""
SKIP_RECON=false
SKIP_EXTRACT=false
DRY_RUN=false

while [ $# -gt 0 ]; do
    case "$1" in
        --prefix)  PREFIX=$(echo "$2" | tr '[:lower:]' '[:upper:]'); shift 2 ;;
        --type)    BUILDING_TYPE="$2"; shift 2 ;;
        --name)    HUMAN_NAME="$2"; shift 2 ;;
        --base)    DOC_BASE=$(echo "$2" | tr '[:lower:]' '[:upper:]'); shift 2 ;;
        --ifc)     IFC_PATH="$2"; shift 2 ;;
        --skip-recon)   SKIP_RECON=true; shift ;;
        --skip-extract) SKIP_EXTRACT=true; shift ;;
        --dry-run)      DRY_RUN=true; shift ;;
        -h|--help) sed -n '2,/^$/p' "$0" | grep '^#' | sed 's/^# \?//'; exit 0 ;;
        *) echo "Unknown option: $1"; exit 1 ;;
    esac
done

if [ -z "$PREFIX" ] || [ -z "$BUILDING_TYPE" ] || [ -z "$HUMAN_NAME" ] || [ -z "$IFC_PATH" ]; then
    echo "Missing required options. Usage:"
    echo "  ./scripts/onboard_ifc.sh --prefix XX --type BuildingType --name 'Name' --ifc path/to/file.ifc"
    echo ""
    echo "Run with --help for full options."
    exit 1
fi

PREFIX_LOWER=$(echo "$PREFIX" | tr '[:upper:]' '[:lower:]')
DOC_TYPE_ID="${DOC_BASE}_${PREFIX}"
YAML_DIR="IFCtoBOM/src/main/resources"
YAML_FILE="${YAML_DIR}/classify_${PREFIX_LOWER}.yaml"
DSL_FILE="${YAML_DIR}/dsl_${PREFIX_LOWER}.bim"
REF_DB="DAGCompiler/lib/input/${BUILDING_TYPE}_extracted.db"
BOM_DB="library/${PREFIX}_BOM.db"
OUTPUT_BASE="DAGCompiler/lib/output/$(echo "$BUILDING_TYPE" | tr '[:upper:]' '[:lower:]')"
MANIFEST="scripts/construction_manifest.yaml"
REGISTRY_TEST="DAGCompiler/src/test/java/com/bim/compiler/contract/BuildingRegistryTest.java"
GATE_TEST="DAGCompiler/src/test/java/com/bim/compiler/contract/RosettaStoneGateTest.java"

printf "\n${B}╔══════════════════════════════════════════════════════════════════════╗${R}\n"
printf "${B}║              IFC ONBOARDING PIPELINE                                ║${R}\n"
printf "${B}╠══════════════════════════════════════════════════════════════════════╣${R}\n"
printf "${B}║${R}  Prefix:   ${B}%-4s${R}  Type: ${B}%s${R}\n" "$PREFIX" "$BUILDING_TYPE"
printf "${B}║${R}  Name:     ${B}%s${R}\n" "$HUMAN_NAME"
printf "${B}║${R}  DocType:  ${B}%s${R}    IFC: %s\n" "$DOC_TYPE_ID" "$(basename "$IFC_PATH")"
printf "${B}╚══════════════════════════════════════════════════════════════════════╝${R}\n"

if [ "$DRY_RUN" = "true" ]; then
    printf "\n${Y}DRY RUN — showing what would be done:${R}\n"
    info "1. Recon:     python3 scripts/ifc_recon.py $IFC_PATH"
    info "2. Extract:   python3 tools/extract.py --to reference $IFC_PATH -o $REF_DB"
    info "3. Generate:  $YAML_FILE + $DSL_FILE"
    info "4. Manifest:  append to $MANIFEST"
    info "5. GATE_SCOPE: add $DOC_TYPE_ID to BuildingRegistryTest + RosettaStoneGateTest"
    info "6. Pipeline:  ./scripts/run_RosettaStones.sh classify_${PREFIX_LOWER}.yaml"
    info "7. Rules:     ./scripts/extract_validation_rules.sh $PREFIX"
    info "8. Report:    ./scripts/rosetta_report.sh $PREFIX"
    exit 0
fi

# ── Pre-flight checks ───────────────────────────────────────

if [ ! -f "$IFC_PATH" ]; then
    fail "IFC file not found: $IFC_PATH"
    exit 1
fi

if [ -f "$YAML_FILE" ]; then
    fail "YAML already exists: $YAML_FILE — building may already be onboarded"
    exit 1
fi

# ─────────────────────────────────────────────────────────────
# Step 1: Reconnaissance
# ─────────────────────────────────────────────────────────────

step 1 "Reconnaissance"

if [ "$SKIP_RECON" = "true" ]; then
    skip "Skipped (--skip-recon)"
else
    python3 scripts/ifc_recon.py "$IFC_PATH"
    echo ""
    printf "  ${Y}Review the recon output above. Continue? [Y/n]${R} "
    read -r ans
    if [ "$ans" = "n" ] || [ "$ans" = "N" ]; then
        echo "Aborted."
        exit 0
    fi
fi

# ─────────────────────────────────────────────────────────────
# Step 2: Extraction
# ─────────────────────────────────────────────────────────────

step 2 "Extraction"

if [ "$SKIP_EXTRACT" = "true" ] || [ -f "$REF_DB" ]; then
    if [ -f "$REF_DB" ]; then
        size=$(du -h "$REF_DB" | cut -f1)
        count=$(sqlite3 "$REF_DB" "SELECT COUNT(*) FROM elements_meta" 2>/dev/null || echo "?")
        skip "Extracted DB exists: $REF_DB ($size, $count elements)"
    else
        skip "Skipped (--skip-extract)"
    fi
else
    info "Extracting: $IFC_PATH → $REF_DB"
    python3 tools/extract.py --to reference "$IFC_PATH" -o "$REF_DB"
    count=$(sqlite3 "$REF_DB" "SELECT COUNT(*) FROM elements_meta" 2>/dev/null || echo "?")
    ok "Extracted $count elements → $REF_DB"
fi

# ─────────────────────────────────────────────────────────────
# Step 3: Generate YAML + DSL skeletons
# ─────────────────────────────────────────────────────────────

step 3 "Generate classify YAML + DSL"

if [ ! -f "$REF_DB" ]; then
    fail "No extracted DB at $REF_DB — cannot generate YAML"
    exit 1
fi

# Query storeys from extracted DB
STOREY_DATA=$(sqlite3 "$REF_DB" "
    SELECT storey, COUNT(*) FROM elements_meta GROUP BY storey ORDER BY MIN(id)
" 2>/dev/null)

ELEMENT_COUNT=$(sqlite3 "$REF_DB" "SELECT COUNT(*) FROM elements_meta" 2>/dev/null || echo "0")

ENVELOPE=$(sqlite3 "$REF_DB" "
    SELECT ROUND(MAX(maxX)-MIN(minX),1), ROUND(MAX(maxY)-MIN(minY),1),
           ROUND(MAX(maxZ)-MIN(minZ),1)
    FROM elements_rtree
" 2>/dev/null || echo "10.0|10.0|3.0")

ENV_X=$(echo "$ENVELOPE" | cut -d'|' -f1)
ENV_Y=$(echo "$ENVELOPE" | cut -d'|' -f2)
ENV_Z=$(echo "$ENVELOPE" | cut -d'|' -f3)

# Storey role inference
infer_code() {
    local name="$1"
    local lower=$(echo "$name" | tr '[:upper:]' '[:lower:]')
    case "$lower" in
        *ground*|*begane*|*erdgeschoss*|*grundplan*) echo "GF" ;;
        *roof*|*dach*|*dak*|*katus*|*tag*) echo "ROOF" ;;
        *basement*|*keller*|*kælder*|*fundament*|*fdn*|*found*|*footing*) echo "FDN" ;;
        *unknown*) echo "MISC" ;;
        *level\ 1*|*1.*|*first*|*eerste*) echo "L1" ;;
        *level\ 2*|*2.*|*second*|*tweede*) echo "L2" ;;
        *level\ 3*|*3.*|*third*|*derde*) echo "L3" ;;
        *level\ 4*|*4.*|*fourth*|*vierde*) echo "L4" ;;
        *level\ 5*|*5.*|*fifth*|*vijfde*) echo "L5" ;;
        *level\ 6*|*6.*) echo "L6" ;;
        *) # First 4 chars
           echo "$name" | tr '[:lower:]' '[:upper:]' | tr ' ' '_' | head -c4 ;;
    esac
}

infer_role() {
    local name="$1"
    local lower=$(echo "$name" | tr '[:upper:]' '[:lower:]')
    case "$lower" in
        *ground*|*begane*|*erdgeschoss*|*grundplan*) echo "GROUND_FLOOR" ;;
        *roof*|*dach*|*dak*|*katus*|*tag*) echo "ROOF" ;;
        *basement*|*keller*|*kælder*) echo "BASEMENT" ;;
        *fundament*|*fdn*|*found*|*footing*) echo "FOUNDATION" ;;
        *unknown*) echo "MISC" ;;
        *) echo "UPPER_FLOOR" ;;
    esac
}

# Generate YAML
{
    echo "# ── YAML = ONLY HUMAN-CRAFTED ARTIFACT ──────────────────────────────"
    echo "# ${PREFIX}: ${HUMAN_NAME}"
    echo "# Generated by onboard_ifc.sh — review and edit before committing."
    echo "# See docs/YAMLGuide.md for field dictionary."
    echo "# ────────────────────────────────────────────────────────────────────"
    echo "schema_version: 1"
    echo ""
    echo "building:"
    echo "  building_type: ${BUILDING_TYPE}"
    echo "  prefix: ${PREFIX}"
    echo "  building_bom_id: BUILDING_${PREFIX}_STD"
    echo "  doc_sub_type: ${PREFIX}"
    echo "  doc_base_type: ${DOC_BASE}"
    echo "  name: ${HUMAN_NAME}"
    echo "  dsl_file: dsl_${PREFIX_LOWER}.bim"
    echo ""
    echo "  storeys:"

    seq_no=1010
    echo "$STOREY_DATA" | while IFS='|' read -r storey cnt; do
        [ -z "$storey" ] && continue
        code=$(infer_code "$storey")
        role=$(infer_role "$storey")
        printf "    \"%s\": { code: %s, bom_category: %s, role: %s, seq: %d }  # %s elements\n" \
            "$storey" "$code" "$code" "$role" "$seq_no" "$cnt"
        seq_no=$((seq_no + 10))
    done

    echo ""
    echo "  # floor_rooms:           # uncomment if building has named rooms"
    echo "  # static_children:       # uncomment for fixed assemblies"
    echo "  # composition: null      # uncomment for mirrored/paired buildings"
} > "$YAML_FILE"

ok "Created $YAML_FILE"

# Generate DSL
{
    echo "// ${BUILDING_TYPE} — Rosetta Stone"
    echo "// ${HUMAN_NAME}"
    echo "// Reference: ${REF_DB}"
    echo "// Envelope: ${ENV_X}m × ${ENV_Y}m × ${ENV_Z}m"
    echo ""
    echo "BUILDING \"${BUILDING_TYPE}\" type:SINGLE_UNIT profile:\"Default\" {"
    echo ""
    echo "    GRID {"
    echo "        axes: A, B / 1, 2"
    echo "        spacing: ${ENV_X} / ${ENV_Y}"
    echo "    }"
    echo ""

    level=0
    echo "$STOREY_DATA" | while IFS='|' read -r storey cnt; do
        [ -z "$storey" ] && continue
        echo "    STOREY \"${storey}\" level:${level} height:3.0m {"
        echo "        // ${cnt} elements"
        echo "    }"
        echo ""
        level=$((level + 1))
    done

    echo "}"
} > "$DSL_FILE"

ok "Created $DSL_FILE"

# Show storey summary
info "Storeys detected:"
echo "$STOREY_DATA" | while IFS='|' read -r storey cnt; do
    [ -z "$storey" ] && continue
    code=$(infer_code "$storey")
    printf "    %-30s %5s elements  code=%s\n" "$storey" "$cnt" "$code"
done

# ─────────────────────────────────────────────────────────────
# Step 4: Register in construction manifest
# ─────────────────────────────────────────────────────────────

step 4 "Register in construction manifest"

if grep -q "^  ${BUILDING_TYPE}:" "$MANIFEST" 2>/dev/null; then
    skip "Already in manifest: ${BUILDING_TYPE}"
else
    # Find next seq_no (strip comments and whitespace)
    max_seq=$(grep "seq_no:" "$MANIFEST" | sed 's/.*seq_no:\s*//' | sed 's/\s*#.*//' | tr -d ' ' | sort -n | tail -1)
    max_seq=${max_seq:-60}
    next_seq=$((max_seq + 10))

    # Build storey block for manifest
    storey_block=""
    seq_no=1010
    while IFS='|' read -r storey cnt; do
        [ -z "$storey" ] && continue
        code=$(infer_code "$storey")
        role=$(infer_role "$storey")
        storey_block="${storey_block}      \"${storey}\": { code: ${code}, bom_category: ${code}, role: ${role}, seq: ${seq_no} }\n"
        seq_no=$((seq_no + 10))
    done <<< "$STOREY_DATA"

    # Append before the COMMERCIAL or TEMPLATE section (or at end of buildings:)
    # Find insertion point
    insert_marker=""
    if grep -q "# --- COMMERCIAL" "$MANIFEST"; then
        insert_marker="# --- COMMERCIAL"
    elif grep -q "# --- INFRASTRUCTURE" "$MANIFEST"; then
        insert_marker="# --- INFRASTRUCTURE"
    elif grep -q "# --- TEMPLATE" "$MANIFEST"; then
        insert_marker="# --- TEMPLATE"
    fi

    manifest_entry="  ${BUILDING_TYPE}:
    prefix: ${PREFIX}
    doc_type_id: ${DOC_TYPE_ID}
    name: ${HUMAN_NAME}
    doc_sub_type: ${PREFIX}
    doc_base_type: ${DOC_BASE}
    description: \"$(basename "$IFC_PATH" .ifc) — ${HUMAN_NAME}\"
    provenance: EXTRACTED
    climate: SCAN
    expected_elements: ${ELEMENT_COUNT}
    geometry_fail_threshold: 50
    output_path: ${OUTPUT_BASE}.db
    reference_path: ${REF_DB}
    building_bom_id: BUILDING_${PREFIX}_STD
    seq_no: ${next_seq}
    storeys:
$(echo -e "$storey_block" | sed '/^$/d')
"

    # Write manifest entry via temp file to avoid sed escaping issues
    if [ -n "$insert_marker" ]; then
        tmp=$(mktemp)
        awk -v marker="$insert_marker" -v entry="$manifest_entry" '
            $0 ~ marker { print ""; print entry; }
            { print }
        ' "$MANIFEST" > "$tmp" && mv "$tmp" "$MANIFEST"
    else
        echo "" >> "$MANIFEST"
        echo "$manifest_entry" >> "$MANIFEST"
    fi

    ok "Added to $MANIFEST (seq_no=$next_seq)"
fi

# ─────────────────────────────────────────────────────────────
# Step 5: Register in GATE_SCOPE
# ─────────────────────────────────────────────────────────────

step 5 "Register in GATE_SCOPE"

for test_file in "$REGISTRY_TEST" "$GATE_TEST"; do
    fname=$(basename "$test_file")
    if grep -q "\"${DOC_TYPE_ID}\"" "$test_file" 2>/dev/null; then
        skip "${fname}: ${DOC_TYPE_ID} already in GATE_SCOPE"
    else
        # Add to the Set.of(...) line
        sed -i "s/\(GATE_SCOPE = Set.of([^)]*\))/\1, \"${DOC_TYPE_ID}\")/" "$test_file"
        ok "${fname}: added ${DOC_TYPE_ID}"
    fi
done

# Check for new IFC classes that need adding to G5 known list
NEW_CLASSES=$(sqlite3 "$REF_DB" "SELECT DISTINCT ifc_class FROM elements_meta" 2>/dev/null | while read cls; do
    grep -q "'${cls}'" "$GATE_TEST" || echo "$cls"
done)

if [ -n "$NEW_CLASSES" ]; then
    info "New IFC classes to add to G5 known list:"
    for cls in $NEW_CLASSES; do
        # Add to the known classes list (before the closing parenthesis)
        sed -i "s/\('IfcEarthworksFill'[^)]*\)/\1,'${cls}'/" "$GATE_TEST" 2>/dev/null || true
        ok "  Added $cls to G5 known classes"
    done
fi

# ─────────────────────────────────────────────────────────────
# Step 6: Run pipeline
# ─────────────────────────────────────────────────────────────

step 6 "Pipeline"

info "Running: ./scripts/run_RosettaStones.sh classify_${PREFIX_LOWER}.yaml"
echo ""

rm -f "$BOM_DB"

if ./scripts/run_RosettaStones.sh "classify_${PREFIX_LOWER}.yaml" 2>&1; then
    PIPELINE_RC=0
    ok "Pipeline completed"
else
    PIPELINE_RC=$?
    fail "Pipeline had failures (exit code $PIPELINE_RC)"
fi

# ─────────────────────────────────────────────────────────────
# Step 7: Extract validation rules
# ─────────────────────────────────────────────────────────────

step 7 "Validation rules"

RULES_FILE="migration/DV_${PREFIX_LOWER}_rules.sql"
if [ -f "${OUTPUT_BASE}_enbloc.db" ]; then
    ./scripts/extract_validation_rules.sh "$PREFIX" > "$RULES_FILE" 2>/dev/null
    rule_count=$(grep -c "^-- Rule:" "$RULES_FILE" 2>/dev/null || echo "0")
    ok "Extracted $rule_count candidate rules → $RULES_FILE"
else
    skip "No output DB — skipping validation rule extraction"
fi

# ─────────────────────────────────────────────────────────────
# Step 8: Report
# ─────────────────────────────────────────────────────────────

step 8 "Report"

./scripts/rosetta_report.sh "$PREFIX" 2>&1

# ─────────────────────────────────────────────────────────────
# Summary
# ─────────────────────────────────────────────────────────────

echo ""
printf "${B}╔══════════════════════════════════════════════════════════════════════╗${R}\n"
printf "${B}║              ONBOARDING COMPLETE                                    ║${R}\n"
printf "${B}╠══════════════════════════════════════════════════════════════════════╣${R}\n"
printf "${B}║${R}  Building:  ${B}%-4s${R}  %s\n" "$PREFIX" "$HUMAN_NAME"
printf "${B}║${R}  Elements:  ${B}%s${R}\n" "$ELEMENT_COUNT"
printf "${B}║${R}\n"
printf "${B}║${R}  Files created:\n"
printf "${B}║${R}    ${G}✓${R} %-50s  (classification)\n" "$YAML_FILE"
printf "${B}║${R}    ${G}✓${R} %-50s  (BIM COBOL)\n" "$DSL_FILE"
[ -f "$BOM_DB" ] && \
printf "${B}║${R}    ${G}✓${R} %-50s  (BOM dictionary)\n" "$BOM_DB"
[ -f "${OUTPUT_BASE}_enbloc.db" ] && \
printf "${B}║${R}    ${G}✓${R} %-50s  (compiled output)\n" "${OUTPUT_BASE}_enbloc.db"
[ -f "$RULES_FILE" ] && \
printf "${B}║${R}    ${G}✓${R} %-50s  (validation rules)\n" "$RULES_FILE"
printf "${B}║${R}\n"
printf "${B}║${R}  Files modified:\n"
printf "${B}║${R}    ${G}✓${R} %-50s  (manifest entry)\n" "$MANIFEST"
printf "${B}║${R}    ${G}✓${R} %-50s  (GATE_SCOPE)\n" "$(basename $REGISTRY_TEST)"
printf "${B}║${R}    ${G}✓${R} %-50s  (GATE_SCOPE + G5)\n" "$(basename $GATE_TEST)"
printf "${B}║${R}\n"
if [ "$PIPELINE_RC" = "0" ]; then
    printf "${B}║${R}  ${G}Pipeline: ALL PASS${R}\n"
else
    printf "${B}║${R}  ${RD}Pipeline: HAD FAILURES — review output above${R}\n"
fi
printf "${B}╚══════════════════════════════════════════════════════════════════════╝${R}\n"
echo ""
printf "  Next: review ${YAML_FILE}, edit rooms/composition if needed, then:\n"
printf "    ${D}git add ${YAML_FILE} ${DSL_FILE} ${MANIFEST}${R}\n"
printf "    ${D}git add ${REGISTRY_TEST} ${GATE_TEST}${R}\n"
echo ""
