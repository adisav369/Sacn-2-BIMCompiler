#!/usr/bin/env bash
# Regenerate library/output_template.db from BuildingWriter.initSchema()
# Run this whenever the output schema changes.
set -euo pipefail
cd "$(dirname "$0")/.."

mvn -q exec:java \
    -pl DAGCompiler \
    -Dexec.mainClass=com.bim.compiler.dsl.OutputTemplateGenerator

echo "Done. Verify with: sqlite3 library/output_template.db '.tables'"
