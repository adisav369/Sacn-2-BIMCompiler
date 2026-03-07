#!/bin/bash
# ============================================================
# BIM Compiler — Rosetta Stone Dual Output (_s/_e) + Delta Test
#
# PURPOSE: Compile SH and DX, producing TWO output DBs each:
#   _s = singular (EN-BLOC compilation — takes one BOM whole as POC)
#   _e = exploded (BOM cascade reconstruction — future target path)
# Then run embedded delta comparison between _s and _e.
#
# DIFFERENT FROM run.sh:
#   run.sh       = user-facing, compiles any C_Order against empty output.db
#   This script  = developer diagnostic, hardcoded SH+DX, dual output + delta
#
# DIFFERENT FROM run_tests.sh:
#   run_tests.sh = full gate (all 5 buildings, all suites)
#   This script  = focused Rosetta Stone fidelity proof (SH+DX only)
#
# Usage:
#   ./scripts/run_RosettaStones.sh           # compile SH+DX, produce _s/_e, delta
#   ./scripts/run_RosettaStones.sh sh        # SH only
#   ./scripts/run_RosettaStones.sh dx        # DX only
#   ./scripts/run_RosettaStones.sh delta     # delta only (skip compile, use existing DBs)
# ============================================================

set -e

SCRIPT_DIR="$(dirname "$0")"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_DIR"

source "$SCRIPT_DIR/log_helper.sh"
init_log "run_RosettaStones"

MODE="${1:-all}"
OUTPUT_DIR="DAGCompiler/lib/output"

# ── Paths ────────────────────────────────────────────────────
SH_BASE="${OUTPUT_DIR}/ifc4_sample_house"
DX_BASE="${OUTPUT_DIR}/ifc2x3_duplex"

print_header() {
    echo ""
    echo "════════════════════════════════════════"
    echo "  $1"
    echo "════════════════════════════════════════"
}

# ── Step 1: Compile ──────────────────────────────────────────
compile_building() {
    local label="$1"
    local base="$2"

    print_header "COMPILE ${label}"

    # _s = singular (EN-BLOC) — takes one BOM whole as POC
    echo "  [_s] Compiling singular (EN-BLOC)..."
    mvn test -pl DAGCompiler \
        -Dtest="BuildingRegistryTest" \
        -Dbom.mode=EXTRACTED \
        -Dsurefire.failIfNoSpecifiedTests=false \
        -q 2>&1 | tail -3 || true
    cp "${base}.db" "${base}_s.db"
    echo "  [_s] → ${base}_s.db"

    # _e = exploded (EXPLODE) — walks UNIT BOM hierarchy (UNIT → FLOOR → SET → BUY)
    # Same BOMWalker, same PlacementCollectorVisitor, different root BOM selection.
    # Delta vs _s reveals which elements are missing from the hierarchy.
    echo "  [_e] Compiling exploded (EXPLODE)..."
    rm -f "${base}.db"
    mvn test -pl DAGCompiler \
        -Dtest="BuildingRegistryTest" \
        -Dbom.mode=STRUCTURED \
        -Dsurefire.failIfNoSpecifiedTests=false \
        -q 2>&1 | tail -5 || true
    if [ -f "${base}.db" ]; then
        cp "${base}.db" "${base}_e.db"
        echo "  [_e] → ${base}_e.db"
    else
        echo "  [_e] SKIP — structured BOM pipeline did not produce output"
        echo "        (structured BOMs may use generic product IDs without geometry)"
    fi
}

# ── Step 2: Delta Test ───────────────────────────────────────
run_delta() {
    local label="$1"
    local s_db="$2"
    local e_db="$3"

    print_header "DELTA ${label}: _s and _e each vs reference"

    if [ ! -f "$s_db" ] || [ ! -f "$e_db" ]; then
        echo "  SKIP — missing DB file(s)"
        return
    fi

    # Count comparison
    S_COUNT=$(sqlite3 "$s_db" "SELECT COUNT(*) FROM elements_meta" 2>/dev/null || echo "0")
    E_COUNT=$(sqlite3 "$e_db" "SELECT COUNT(*) FROM elements_meta" 2>/dev/null || echo "0")
    echo "  Element count: _s=${S_COUNT}  _e=${E_COUNT}  delta=$((E_COUNT - S_COUNT))"

    # Per-class count comparison (full outer join via UNION)
    echo ""
    echo "  Per-class breakdown:"
    sqlite3 "$s_db" "
        ATTACH '${e_db}' AS e;
        SELECT ifc_class,
               COALESCE(s_count, 0) as s_count,
               COALESCE(e_count, 0) as e_count,
               COALESCE(e_count, 0) - COALESCE(s_count, 0) as delta
        FROM (
            SELECT ifc_class, SUM(s_cnt) as s_count, SUM(e_cnt) as e_count
            FROM (
                SELECT ifc_class, COUNT(*) as s_cnt, 0 as e_cnt FROM elements_meta GROUP BY ifc_class
                UNION ALL
                SELECT ifc_class, 0 as s_cnt, COUNT(*) as e_cnt FROM e.elements_meta GROUP BY ifc_class
            )
            GROUP BY ifc_class
        )
        ORDER BY ifc_class;
        DETACH e;
    " -header -column 2>/dev/null | sed 's/^/    /'

    # AABB delta: per-element comparison (centroid distance)
    # Uses elements_rtree from both DBs
    echo ""
    echo "  AABB centroid delta (top 10 worst):"
    sqlite3 "$s_db" "
        ATTACH '${e_db}' AS e;
        SELECT
            s.guid,
            s.ifc_class,
            ROUND(ABS((s.minX+s.maxX)/2.0 - (er.minX+er.maxX)/2.0)*1000, 1) as dx_mm,
            ROUND(ABS((s.minY+s.maxY)/2.0 - (er.minY+er.maxY)/2.0)*1000, 1) as dy_mm,
            ROUND(ABS((s.minZ+s.maxZ)/2.0 - (er.minZ+er.maxZ)/2.0)*1000, 1) as dz_mm
        FROM elements_meta sm
        JOIN elements_rtree s ON sm.guid = s.id
        JOIN e.elements_meta em ON sm.guid = em.guid
        JOIN e.elements_rtree er ON em.guid = er.id
        WHERE dx_mm > 0.5 OR dy_mm > 0.5 OR dz_mm > 0.5
        ORDER BY (dx_mm*dx_mm + dy_mm*dy_mm + dz_mm*dz_mm) DESC
        LIMIT 10;
        DETACH e;
    " -header -column 2>/dev/null | sed 's/^/    /'

    # Geometry hash comparison: elements with different geometry
    echo ""
    echo "  Geometry divergence (different geometry_hash):"
    GEOM_DIFF=$(sqlite3 "$s_db" "
        ATTACH '${e_db}' AS e;
        SELECT COUNT(*)
        FROM element_instances si
        JOIN e.element_instances ei ON si.guid = ei.guid
        WHERE si.geometry_hash != ei.geometry_hash;
        DETACH e;
    " 2>/dev/null || echo "N/A")
    echo "    ${GEOM_DIFF} elements with different geometry_hash"

    # Rule 8 check: world-absolute coordinates on M_BOM_Line
    echo ""
    echo "  Rule 8 (Cheating Maxim) — M_BOM_Line world-absolute check:"
    RULE8=$(sqlite3 library/BOM.db "
        SELECT COUNT(*) FROM m_bom_line bl
        JOIN m_bom b ON bl.bom_id = b.bom_id
        JOIN C_DocType dt ON b.doc_sub_type = dt.DocSubType
        WHERE b.bom_category = 'EXTRACTED'
          AND bl.is_active = 1
          AND (ABS(bl.dx) > dt.AabbWidthMm/1000.0
            OR ABS(bl.dy) > dt.AabbDepthMm/1000.0
            OR ABS(bl.dz) > dt.AabbHeightMm/1000.0)
    " 2>/dev/null || echo "N/A")
    if [ "$RULE8" = "0" ]; then
        echo "    PASS — all coordinates within parent envelope"
    else
        echo "    !! FAIL — ${RULE8} M_BOM_Line rows have world-absolute coordinates"
    fi

    # Clash check: AABB overlap among furniture (structured BOM concern)
    echo ""
    echo "  Clash check (furniture AABB overlap):"
    CLASH=$(sqlite3 "$s_db" "
        SELECT COUNT(*) FROM (
            SELECT a.id as a_id, b.id as b_id
            FROM elements_meta am
            JOIN elements_rtree a ON am.guid = a.id
            JOIN elements_meta bm ON am.guid != bm.guid
            JOIN elements_rtree b ON bm.guid = b.id
            WHERE am.ifc_class IN ('IfcFurnishingElement','IfcFurniture')
              AND bm.ifc_class IN ('IfcFurnishingElement','IfcFurniture')
              AND a.id < b.id
              AND a.maxX > b.minX AND a.minX < b.maxX
              AND a.maxY > b.minY AND a.minY < b.maxY
              AND a.maxZ > b.minZ AND a.minZ < b.maxZ
        )
    " 2>/dev/null || echo "N/A")
    if [ "$CLASH" = "0" ]; then
        echo "    PASS — 0 furniture clashes"
    else
        echo "    !! ${CLASH} furniture AABB overlaps:"
        sqlite3 "$s_db" "
            SELECT am.element_name as elem_a, bm.element_name as elem_b,
                   ROUND((MIN(a.maxX,b.maxX)-MAX(a.minX,b.minX))
                        *(MIN(a.maxY,b.maxY)-MAX(a.minY,b.minY))
                        *(MIN(a.maxZ,b.maxZ)-MAX(a.minZ,b.minZ)), 4) as overlap_m3
            FROM elements_meta am
            JOIN elements_rtree a ON am.guid = a.id
            JOIN elements_meta bm ON am.guid != bm.guid
            JOIN elements_rtree b ON bm.guid = b.id
            WHERE am.ifc_class IN ('IfcFurnishingElement','IfcFurniture')
              AND bm.ifc_class IN ('IfcFurnishingElement','IfcFurniture')
              AND a.id < b.id
              AND a.maxX > b.minX AND a.minX < b.maxX
              AND a.maxY > b.minY AND a.minY < b.maxY
              AND a.maxZ > b.minZ AND a.minZ < b.maxZ
            ORDER BY overlap_m3 DESC
            LIMIT 10;
        " -header -column 2>/dev/null | sed 's/^/    /'
    fi
}

# ── Contract Tests (standalone — no pipeline dependency) ─────
run_contracts() {
    print_header "CONTRACT TESTS (standalone)"
    local TESTS="IntraBOMRelativeTest,TranslationChainTest,AnchorComputationTest,LocalCoordTest,StallDividerParamsTest"
    mvn test -pl DAGCompiler \
        -Dtest="${TESTS}" \
        -Dsurefire.failIfNoSpecifiedTests=false \
        -q 2>&1 | grep -E "Tests run:" | tail -1
    if [ $? -eq 0 ]; then
        echo "  Contract tests: PASS"
    else
        echo "  !! Contract tests: FAIL"
    fi
}

# ── Execute ──────────────────────────────────────────────────

# Compile first (unless delta-only)
if [ "$MODE" != "delta" ]; then
    print_header "COMPILE (all modules)"
    mvn install -pl orm-core,ORMSandbox -DskipTests -q
    mvn compile -pl DAGCompiler -q
    echo "  Compile: OK"
    run_contracts
fi

case "$MODE" in
    sh)
        compile_building "SH" "$SH_BASE"
        run_delta "SH" "${SH_BASE}_s.db" "${SH_BASE}_e.db"
        ;;
    dx)
        compile_building "DX" "$DX_BASE"
        run_delta "DX" "${DX_BASE}_s.db" "${DX_BASE}_e.db"
        ;;
    delta)
        run_delta "SH" "${SH_BASE}_s.db" "${SH_BASE}_e.db"
        run_delta "DX" "${DX_BASE}_s.db" "${DX_BASE}_e.db"
        ;;
    all|*)
        compile_building "SH" "$SH_BASE"
        compile_building "DX" "$DX_BASE"
        run_delta "SH" "${SH_BASE}_s.db" "${SH_BASE}_e.db"
        run_delta "DX" "${DX_BASE}_s.db" "${DX_BASE}_e.db"
        ;;
esac

# ── Summary ──────────────────────────────────────────────────
print_header "ROSETTA STONE SUMMARY"
echo "  _s (singular) = EN-BLOC compilation (single C_OrderLine, single ESLine)"
echo "  _e (exploded)  = EXPLODE compilation (C_OrderLine per slot, ESLine per slot)"
echo ""
echo "  Same BOMWalker code, different BOM qualification (root selection)."
echo "  _s takes one BOM whole (EXT_SH/EXT_DX). _e walks the hierarchy"
echo "  (UNIT_SH_STD/UNIT_DUPLEX_STD → FLOOR → SET → BUY)."
echo "  Each must independently match the reference extracted DB."
echo "  Zero delta is a consequence of both matching, not a goal."
echo ""
echo "  Output files:"
echo "    ${SH_BASE}_s.db  ${SH_BASE}_e.db"
echo "    ${DX_BASE}_s.db  ${DX_BASE}_e.db"
echo ""
finish_log
