#!/bin/bash
# §S260c JS Logic Whitebox — reads actual code and traces execution paths
# Produces §-tagged log output that must be READ to verify correctness.
# Run: bash deploy/dev/test-results/whitebox_js_logic.sh

echo "════════════════════════════════════════"
echo "§WHITEBOX_JS_LOGIC START $(date)"
echo "════════════════════════════════════════"

# ══════════════════════════════════════════
# 1. GROUND Y PIPELINE — trace the full path
# ══════════════════════════════════════════
echo ""
echo "── 1. Ground Y: streaming.js → tools.js pipeline ──"

echo "§TRACE streaming.js ground init path:"
sed -n '/§S260c.*_calcGroundY/,/§GROUND_INIT/p' deploy/dev/streaming.js
echo ""
echo "§TRACE tools.js _calcGroundY gfNames list:"
grep "gfNames" deploy/dev/tools.js | head -1
echo ""
echo "§TRACE tools.js Step 1 query (storey match):"
sed -n '/Step 1:/,/ORDER BY area/p' deploy/dev/tools.js | head -8
echo ""
echo "§TRACE tools.js Step 2 (lowest-of-top5):"
sed -n '/Step 2:/,/bestBottom.*_gLvl/p' deploy/dev/tools.js | head -12
echo ""
echo "§TRACE tools.js ifc2three ground application:"
grep "ifc2three.*_gLvl\|ground\.position\.y" deploy/dev/tools.js | head -2
echo ""
echo "§TRACE Who calls _calcGroundY:"
grep -n "_calcGroundY()" deploy/dev/tools.js deploy/dev/streaming.js

# ══════════════════════════════════════════
# 2. TM PANEL ICONS — read actual HTML generation
# ══════════════════════════════════════════
echo ""
echo "── 2. TM Panel Icons — actual button HTML ──"
echo "§TRACE tm-sun button:"
grep "tm-sun" deploy/dev/time_machine.js | grep "button" | head -1
echo ""
echo "§TRACE tm-eye button:"
grep "tm-eye" deploy/dev/time_machine.js | grep "button" | head -1
echo ""
echo "§TRACE Sunglass night button (index.html):"
grep "night-btn" deploy/dev/index.html | head -1

# ══════════════════════════════════════════
# 3. DRONE MOVIE — trace Eye press → storyboard → playback
# ══════════════════════════════════════════
echo ""
echo "── 3. Drone Movie — Eye press flow ──"
echo "§TRACE Eye pointerup handler:"
sed -n "/tm-eye.*addEventListener/,/}, 50/p" deploy/dev/time_machine.js | head -30
echo ""
echo "§TRACE Camera gate condition (what triggers camera movement):"
grep "_cineStoryboard.*app\.controls" deploy/dev/time_machine.js
echo ""
echo "§TRACE Drone Movie status message:"
grep "Drone Movie" deploy/dev/time_machine.js
echo ""
echo "§TRACE Camera distances:"
grep "_FLYTHROUGH_DIST\|_PANORAMIC_DIST\|_HERO_DIST" deploy/dev/time_machine.js | head -3
echo ""
echo "§TRACE buildGuidPosMap — mesh types handled:"
grep "isMesh\|isBatchedMesh\|isInstancedMesh" deploy/dev/time_machine.js | grep -A0 "buildGuidPosMap\|_batchMeta\|_instanceMeta" | head -6
echo ""
echo "§TRACE buildGuidPosMap full function:"
sed -n '/function buildGuidPosMap/,/^  }/p' deploy/dev/time_machine.js

# ══════════════════════════════════════════
# 4. STOREY SHIFT+CLICK — trace event path
# ══════════════════════════════════════════
echo ""
echo "── 4. Storey Shift+Click — event interception ──"
echo "§TRACE storey button generation (inline onclick):"
grep "onclick.*filterStorey" deploy/dev/panels.js | head -2
echo ""
echo "§TRACE capture-phase click interceptor:"
sed -n '/§S260c.*Intercept/,/§STOREY_SHIFT_CLICK/p' deploy/dev/panels.js
echo ""
echo "§TRACE stopImmediatePropagation present:"
grep "stopImmediatePropagation" deploy/dev/panels.js

# ══════════════════════════════════════════
# 5. STOREY BANDS — median Z sort code
# ══════════════════════════════════════════
echo ""
echo "── 5. Storey Bands — median Z computation ──"
echo "§TRACE median Z computation:"
sed -n '/storeyZvals/,/storeyMedianZ\[sk\]/p' deploy/dev/time_machine.js | head -15
echo ""
echo "§TRACE sort by median:"
grep "storeyMedianZ\[a\].*storeyMedianZ\[b\]" deploy/dev/time_machine.js
echo ""
echo "§TRACE old minZ sort ABSENT:"
grep "storeyMinZ\[a\].*storeyMinZ\[b\]" deploy/dev/time_machine.js && echo "§BUG old minZ sort still present!" || echo "§OK old minZ sort removed"

# ══════════════════════════════════════════
# 6. IMPORT DB VALIDATION
# ══════════════════════════════════════════
echo ""
echo "── 6. Import DB — validation + threshold ──"
echo "§TRACE post-export validation:"
sed -n '/§S260c.*Post-export/,/§DB_EXPORT_VALID/p' deploy/dev/import_db_builder.js
echo ""
echo "§TRACE split threshold:"
grep "elements.length > " deploy/dev/import_db_builder.js

# ══════════════════════════════════════════
# 7. DIFF PIPELINE — trace full path
# ══════════════════════════════════════════
echo ""
echo "── 7. Diff Pipeline — main.js load + diff.js compute ──"
echo "§TRACE main.js diff loading:"
sed -n '/§DIFF_PARAM/,/§DIFF_OVERLAY_READY/p' deploy/dev/main.js | grep "§"
echo ""
echo "§TRACE diff.js computeDiff §-logs:"
grep "§DIFF" deploy/dev/diff.js | head -10
echo ""
echo "§TRACE diff.js applyDiffOverlay §-logs:"
grep "§DIFF" deploy/dev/diff.js | tail -10

# ══════════════════════════════════════════
# 8. VERSION CONSISTENCY
# ══════════════════════════════════════════
echo ""
echo "── 8. Version Consistency ──"
echo "§VERSIONS in sw.js:"
grep "CACHE_VERSION = " deploy/dev/sw.js | head -1
echo "§VERSIONS in index.html:"
grep -oE "(streaming|tools|panels|time_machine|diff|main|import_db_builder|sw)\.js\?v=[0-9]+" deploy/dev/index.html | sort
echo "§VERSION sw registration:"
grep "sw.js?v=" deploy/dev/index.html

echo ""
echo "════════════════════════════════════════"
echo "§WHITEBOX_JS_LOGIC DONE — READ ABOVE TRACES TO VERIFY"
echo "════════════════════════════════════════"

# ══════════════════════════════════════════
# 9. JSON CACHE — Gantt + Movie Script persistence
# ══════════════════════════════════════════
echo ""
echo "── 9. JSON Cache — IDB persistence ──"
echo "§TRACE cacheGet/cachePut helper functions:"
grep "function cacheGet\|function cachePut\|function _cacheKey" deploy/dev/time_machine.js
echo ""
echo "§TRACE Gantt cache hit path:"
grep "§GANTT_CACHE" deploy/dev/time_machine.js
echo ""
echo "§TRACE Movie script cache hit path:"
grep "§MOVIE_CACHE" deploy/dev/time_machine.js
echo ""
echo "§TRACE Clear Cache deletes IDB:"
grep "deleteDatabase" deploy/dev/landing.html
echo ""
echo "§TRACE activate() uses cache:"
grep "cacheGet\|cachePut" deploy/dev/time_machine.js | head -8
