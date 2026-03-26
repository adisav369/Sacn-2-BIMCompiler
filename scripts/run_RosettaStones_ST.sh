#!/usr/bin/env bash
# Run Rosetta Stones for M_Product_Category = ST (Standard Template)
# DocType: ConstructionOrder. This script runs all ST buildings.
set -e
SCRIPT_DIR="$(dirname "$0")"
YAML_DIR="IFCtoBOM/src/main/resources"
cd "$(dirname "$SCRIPT_DIR")"

YAMLS=()
for f in "${YAML_DIR}"/classify_*.yaml; do
    dbt=$(grep -E '^\s+doc_base_type:' "$f" | head -1 | sed 's/.*:\s*//' | tr -d '\r' | xargs)
    [ "$dbt" = "ST" ] && YAMLS+=("$f")
done

if [ ${#YAMLS[@]} -eq 0 ]; then
    echo "  ST buildings: 0 YAMLs (ST templates run via StubDataSeeder, not YAML extraction)"
    exit 0
fi

echo "  ST buildings: ${#YAMLS[@]} YAMLs"
for y in "${YAMLS[@]}"; do
    "$SCRIPT_DIR/run_RosettaStones.sh" "$y" "$@"
done
