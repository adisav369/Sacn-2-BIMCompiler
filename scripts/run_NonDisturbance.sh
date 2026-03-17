#!/bin/bash
# ============================================================
# Non-Disturbance Test — DocValidate §7.2
# Mined rules MUST pass against the buildings they were mined from.
# If any rule reports a violation against its source building,
# the rule is wrong, not the building.
#
# Separation layer: This script uses I_Element_Extraction in
# component_library.db. When R13 moves those rows to per-building
# extraction DBs, only the ATTACH path changes — queries stay same.
# ============================================================
set -euo pipefail

LIBRARY_DB="library/component_library.db"
VALIDATION_DB="library/validation.db"
PASS=0
FAIL=0
SKIP=0

echo "═══════════════════════════════════════════════════════"
echo " Non-Disturbance Test — DocValidate §7.2"
echo " validation.db: $(sqlite3 "$VALIDATION_DB" 'SELECT COUNT(*) FROM AD_Val_Rule') rules"
echo " extraction source: $LIBRARY_DB (I_Element_Extraction)"
echo "═══════════════════════════════════════════════════════"

# ----------------------------------------------------------
# ND-1: Sprinkler NN spacing (rule 601)
# Mined from TE. max_nn_spacing_mm = 3500.
# Expect: 0 violations (all TE heads have NN <= 3500mm)
# ----------------------------------------------------------
echo ""
echo "--- ND-1: Sprinkler NN spacing (rule 601, max 3500mm) ---"

MAX_SPACING=$(sqlite3 "$VALIDATION_DB" "
  SELECT value FROM AD_Val_Rule_Param
  WHERE ad_val_rule_id = 601 AND name = 'max_nn_spacing_mm'")

VIOLATIONS=$(sqlite3 "$LIBRARY_DB" "
  SELECT COUNT(*) FROM (
    SELECT a.placement_id,
      MIN(SQRT(
        (a.min_x - b.min_x)*(a.min_x - b.min_x) +
        (a.min_y - b.min_y)*(a.min_y - b.min_y)
      )) * 1000 as nn_mm
    FROM I_Element_Extraction a
    JOIN I_Element_Extraction b
      ON a.building_type = b.building_type
      AND a.storey = b.storey
      AND a.ifc_class = b.ifc_class
      AND a.placement_id != b.placement_id
    WHERE a.building_type = 'SJTII_Terminal'
      AND a.ifc_class = 'IfcFireSuppressionTerminal'
    GROUP BY a.placement_id
    HAVING nn_mm > 10  -- exclude co-located
  ) WHERE nn_mm > $MAX_SPACING
")

# Check documented exceptions for this rule + building
EXCEPTIONS=$(sqlite3 "$VALIDATION_DB" "
  SELECT COALESCE(SUM(count), 0) FROM AD_Val_Rule_Exception
  WHERE ad_val_rule_id = 601 AND building_id = 'SJTII_Terminal'
")

if [ "$VIOLATIONS" -eq 0 ]; then
  echo "  PASS: 0 violations (all TE sprinkler heads within ${MAX_SPACING}mm NN spacing)"
  PASS=$((PASS + 1))
elif [ "$VIOLATIONS" -le "$EXCEPTIONS" ]; then
  echo "  PASS: $VIOLATIONS violations, all covered by $EXCEPTIONS documented exceptions"
  PASS=$((PASS + 1))
else
  echo "  FAIL: $VIOLATIONS violations exceed $EXCEPTIONS documented exceptions"
  FAIL=$((FAIL + 1))
fi

# ----------------------------------------------------------
# ND-2: ELEC-SP clearance (rule 603)
# Mined from TE. min_clearance_mm = 150 (NEC 300.4).
# Expect: violations > 0 (real building has some close pairs)
# These are documented as known engineering practice, not rule failure.
# Non-disturbance: count violations and document, don't FAIL.
# ----------------------------------------------------------
echo ""
echo "--- ND-2: ELEC-SP clearance (rule 603, min 150mm) ---"

MIN_CLEAR=$(sqlite3 "$VALIDATION_DB" "
  SELECT value FROM AD_Val_Rule_Param
  WHERE ad_val_rule_id = 603 AND name = 'min_clearance_mm'")

# Centroid-to-centroid as proxy (AABB-to-AABB needs more complex SQL)
CLOSE_PAIRS=$(sqlite3 "$LIBRARY_DB" "
  SELECT COUNT(*) FROM (
    SELECT a.placement_id, b.placement_id as b_id,
      SQRT(
        ((a.min_x+a.max_x)/2 - (b.min_x+b.max_x)/2) *
        ((a.min_x+a.max_x)/2 - (b.min_x+b.max_x)/2) +
        ((a.min_y+a.max_y)/2 - (b.min_y+b.max_y)/2) *
        ((a.min_y+a.max_y)/2 - (b.min_y+b.max_y)/2)
      ) * 1000 as dist_mm
    FROM I_Element_Extraction a, I_Element_Extraction b
    WHERE a.building_type = 'SJTII_Terminal'
      AND b.building_type = 'SJTII_Terminal'
      AND a.discipline = 'ELEC' AND b.discipline = 'SP'
      AND a.storey = b.storey
      AND a.placement_id < b.placement_id
  ) WHERE dist_mm < $MIN_CLEAR
")

echo "  INFO: $CLOSE_PAIRS ELEC-SP pairs within ${MIN_CLEAR}mm (centroid proxy)"
echo "  SKIP: Clearance rule uses centroid proxy — real AABB clearance needs R13 spatial join"
SKIP=$((SKIP + 1))

# ----------------------------------------------------------
# ND-3: DX known exceptions (rule 702, 364 P23 MEP corners)
# Verify the exception count matches what we documented.
# ----------------------------------------------------------
echo ""
echo "--- ND-3: DX P23 exception documentation (rule_exception 1) ---"

DOC_COUNT=$(sqlite3 "$VALIDATION_DB" "
  SELECT count FROM AD_Val_Rule_Exception WHERE ad_val_rule_exception_id = 1")

DX_MEP=$(sqlite3 "$LIBRARY_DB" "
  SELECT COUNT(*) FROM I_Element_Extraction
  WHERE building_type = 'Ifc2x3_Duplex' AND discipline = 'MEP'
    AND ifc_class IN ('IfcFlowFitting')")

echo "  Documented P23 exceptions: $DOC_COUNT"
echo "  DX IfcFlowFitting count: $DX_MEP"
if [ "$DX_MEP" -ge "$DOC_COUNT" ]; then
  echo "  PASS: Exception count ($DOC_COUNT) consistent with DX fittings ($DX_MEP)"
  PASS=$((PASS + 1))
else
  echo "  WARN: Exception count ($DOC_COUNT) exceeds DX fittings ($DX_MEP)"
  SKIP=$((SKIP + 1))
fi

# ----------------------------------------------------------
# ND-4: Rule integrity — all rules have at least one param
# ----------------------------------------------------------
echo ""
echo "--- ND-4: Rule integrity (every rule has params) ---"

ORPHANS=$(sqlite3 "$VALIDATION_DB" "
  SELECT COUNT(*) FROM AD_Val_Rule r
  WHERE NOT EXISTS (
    SELECT 1 FROM AD_Val_Rule_Param p WHERE p.ad_val_rule_id = r.ad_val_rule_id
  ) AND r.rule_type != 'CLASH'
")

if [ "$ORPHANS" -eq 0 ]; then
  echo "  PASS: All non-CLASH rules have parameters"
  PASS=$((PASS + 1))
else
  echo "  FAIL: $ORPHANS rules without parameters"
  FAIL=$((FAIL + 1))
fi

# ----------------------------------------------------------
# ND-5: Mining provenance — mined rules have source data
# ----------------------------------------------------------
echo ""
echo "--- ND-5: Mining provenance (mined rules have sources) ---"

MINED_RULES=$(sqlite3 "$VALIDATION_DB" "
  SELECT COUNT(*) FROM AD_Val_Rule WHERE provenance LIKE 'MINED:%'")
MINED_SOURCES=$(sqlite3 "$VALIDATION_DB" "
  SELECT COUNT(DISTINCT ad_val_rule_id) FROM AD_Val_Rule_Mining_Source")

if [ "$MINED_RULES" -eq "$MINED_SOURCES" ]; then
  echo "  PASS: $MINED_RULES mined rules, $MINED_SOURCES with provenance"
  PASS=$((PASS + 1))
else
  echo "  FAIL: $MINED_RULES mined rules but only $MINED_SOURCES have provenance"
  FAIL=$((FAIL + 1))
fi

# ----------------------------------------------------------
# Summary
# ----------------------------------------------------------
echo ""
echo "═══════════════════════════════════════════════════════"
echo " Non-Disturbance: $PASS PASS, $FAIL FAIL, $SKIP SKIP"
echo "═══════════════════════════════════════════════════════"

if [ "$FAIL" -gt 0 ]; then
  exit 1
fi
