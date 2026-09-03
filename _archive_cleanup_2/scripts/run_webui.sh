#!/bin/bash
# ============================================================
# BIM Designer — Web UI (standalone, no Bonsai needed)
# HTTP on port 9878. Ctrl+C to stop.
# ============================================================
set -e
cd "$(dirname "$0")/.."

PORT="${1:-9878}"

[ -f library/component_library.db ] || { echo "ERROR: library/component_library.db not found."; exit 1; }
[ -f webui/index.html ] || { echo "ERROR: webui/index.html not found."; exit 1; }

# Compile if needed
if [ ! -d BonsaiBIMDesigner/target/classes ] || \
   [ "$(find BonsaiBIMDesigner/src -name '*.java' -newer BonsaiBIMDesigner/target/classes 2>/dev/null | head -1)" ]; then
    echo "Compiling..."
    mvn compile -q -pl BonsaiBIMDesigner -am
fi

CP=$(mvn -pl BonsaiBIMDesigner dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q 2>/dev/null)
CP="BonsaiBIMDesigner/target/classes:DAGCompiler/target/classes:BIM_COBOL/target/classes:BIMEyes/target/classes:ORMSandbox/target/classes:BIMBackOffice/target/classes:IFCtoBOM/target/classes:$CP"

# Guard: if port already in use, warn and exit instead of opening another tab
if ss -tlnp 2>/dev/null | grep -q ":$PORT "; then
    echo "WebUIServer already running on port $PORT."
    echo "  Open: http://localhost:$PORT"
    echo "  Kill: kill \$(ss -tlnp | grep :$PORT | grep -oP 'pid=\K[0-9]+')"
    exit 0
fi

# Only open browser if server starts successfully
(sleep 2 && if ss -tlnp 2>/dev/null | grep -q ":$PORT "; then
    xdg-open "http://localhost:$PORT" 2>/dev/null || open "http://localhost:$PORT" 2>/dev/null || true
fi) &

exec java -cp "$CP" -Dbom.db="library/component_library.db" \
     com.bim.designer.api.WebUIServer library "$PORT" webui
