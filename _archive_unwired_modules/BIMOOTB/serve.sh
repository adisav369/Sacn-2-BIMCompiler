#!/usr/bin/env bash
# BIM OOTB — Local Server
# Starts a localhost server for the BIM viewer.
# Usage: ./serve.sh [port]
#   Default port: 8080
#   Open browser to: http://localhost:8080

PORT="${1:-8080}"
DIR="$(cd "$(dirname "$0")" && pwd)"

echo ""
echo "  BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install."
echo "  Copyright (c) 2025-2026 Redhuan D. Oon. MIT Licensed."
echo ""
echo "  Serving from: $DIR"
echo "  Open browser:  http://localhost:$PORT"
echo "  Drop IFC:      http://localhost:$PORT (drag any .ifc file onto the page)"
echo "  2D Plans:      http://localhost:$PORT/2d.html"
echo "  BOQ Charts:    http://localhost:$PORT/boq_charts.html"
echo ""
echo "  Press Ctrl+C to stop."
echo ""

cd "$DIR"
python3 -m http.server "$PORT"
