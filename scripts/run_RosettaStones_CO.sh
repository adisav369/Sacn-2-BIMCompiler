#!/usr/bin/env bash
# Run Rosetta Stones for M_Product_Category = CO (Commercial)
# DocType: ConstructionOrder. This script runs all CO buildings.
set -e
SCRIPT_DIR="$(dirname "$0")"
YAML_DIR="IFCtoBOM/src/main/resources"
cd "$(dirname "$SCRIPT_DIR")"

YAMLS=()
for f in "${YAML_DIR}"/classify_*.yaml; do
    dbt=$(grep -E '^\s+product_category:' "$f" | head -1 | sed 's/.*:\s*//' | tr -d '\r' | xargs)
    [ "$dbt" = "CO" ] && YAMLS+=("$f")
done

echo "  CO buildings: ${#YAMLS[@]} YAMLs"
for y in "${YAMLS[@]}"; do
    "$SCRIPT_DIR/run_RosettaStones.sh" "$y" "$@"
done
