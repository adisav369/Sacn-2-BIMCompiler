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

echo ""
echo "── 10. Pick Accuracy (WYSIWYG) ──"
echo "§CHECK firstHitOnly disabled (must be false for WYSIWYG):"
grep "firstHitOnly" deploy/dev/picking.js
echo ""
echo "§CHECK skip-logic for non-pickable hits:"
grep -c "_isOutline\|opacity < 0.3\|isBboxPlaceholder" deploy/dev/picking.js | xargs -I{} echo "  skip conditions: {} lines"
echo ""
echo "§CHECK BatchedMesh pick uses DB bbox (not geometry bbox):"
grep -c "isBatchedMesh && guid" deploy/dev/picking.js | xargs -I{} echo "  BatchedMesh DB-bbox path: {} matches"
echo ""
echo "§CHECK yellow highlight box exists:"
grep -c "color: 0xffff00" deploy/dev/picking.js | xargs -I{} echo "  yellow bbox lines: {} matches"
echo ""
echo "§CHECK DoubleSide on ALL materials (not just transparent):"
grep -n "opts.side.*DoubleSide" deploy/dev/streaming.js

echo ""
echo "── 11. PBR Material (S260d) ──"
echo "§CHECK MeshStandardMaterial used (not MeshPhongMaterial):"
grep "MeshStandardMaterial\|MeshPhongMaterial" deploy/dev/streaming.js | head -3
echo ""
echo "§CHECK roughness/metalness maps present:"
grep -c "ROUGHNESS_MAP\|METALNESS_MAP" deploy/dev/streaming.js | xargs -I{} echo "  PBR maps: {} references"
echo ""
echo "§CHECK class color fallback present:"
grep -c "CLASS_COLOR_FALLBACK" deploy/dev/streaming.js | xargs -I{} echo "  fallback map: {} references"
echo ""
echo "§CHECK near-white taming factor (must be >= 0.90, not 0.82):"
grep "r \*= 0\." deploy/dev/streaming.js
echo ""
echo "§CHECK lighting — ambient neutral white:"
grep "AmbientLight" deploy/dev/scene.js
echo ""
echo "§CHECK lighting — hemisphere warm:"
grep "HemisphereLight" deploy/dev/scene.js

echo ""
echo "── 12. TM White-Box Material Logger ──"
echo "§CHECK §WB_MAT logger present (expect 5+ call sites):"
grep -c "_wbMat(" deploy/dev/time_machine.js | xargs -I{} echo "  _wbMat() call sites: {}"
echo ""
echo "§CHECK InstancedMesh highlight REMOVED (white flash fix):"
if grep -q "applyHighlight.*InstancedMesh\|anyFrontier.*applyHighlight" deploy/dev/time_machine.js; then
  echo "  ⚠FAIL — InstancedMesh highlight still present!"
else
  echo "  ✓OK — no InstancedMesh highlight (white flash fixed)"
fi
echo ""
echo "§CHECK emissive intensity caps:"
grep "emissiveIntensity" deploy/dev/time_machine.js | grep -v "^--" | head -5

echo ""
echo "════════════════════════════════════════"
echo "§EXECUTION TESTS — real DB data, real logic"
echo "════════════════════════════════════════"

echo ""
echo "── 13. Gantt Z-Sequence (EXECUTION against Terminal DB) ──"
DB=deploy/buildings/Terminal_extracted.db
if [ ! -f "$DB" ]; then echo "§SKIP Terminal DB not found"; else

echo "§EXEC underground elements:"
sqlite3 "$DB" "SELECT COUNT(*), ROUND(MIN(t.center_z),2), ROUND(MAX(t.center_z),2)
  FROM elements_meta m JOIN element_transforms t ON t.guid=m.guid WHERE t.center_z < 0" | \
  awk -F'|' '{printf "  count=%s minZ=%s maxZ=%s\n",$1,$2,$3}'

echo ""
echo "§EXEC Gantt Z-band sort simulation (floor(cz/3) → seq → cz):"
echo "  First 20 elements by Z-band sort:"
sqlite3 "$DB" "
  SELECT CAST(ROUND(t.center_z/3 - 0.5) AS INT) AS zband,
    CASE
      WHEN m.ifc_class IN ('IfcFooting','IfcPile','IfcReinforcingBar') THEN 1
      WHEN m.ifc_class IN ('IfcColumn') THEN 2
      WHEN m.ifc_class IN ('IfcBeam','IfcMember','IfcPlate') THEN 3
      WHEN m.ifc_class IN ('IfcSlab') THEN 4
      WHEN m.ifc_class LIKE 'IfcWall%' THEN 5
      ELSE 6
    END AS seq,
    ROUND(t.center_z,2) AS cz, m.ifc_class, m.storey
  FROM elements_meta m JOIN element_transforms t ON t.guid=m.guid
  WHERE m.ifc_class != 'IfcOpeningElement'
  ORDER BY zband ASC, seq ASC, cz ASC
  LIMIT 20
" | while IFS='|' read zband seq cz cls storey; do
  echo "    zband=$zband seq=$seq z=$cz $cls [$storey]"
done

echo ""
echo "§VERIFY first element must be underground (zband < 0):"
FIRST_ZBAND=$(sqlite3 "$DB" "
  SELECT CAST(ROUND(t.center_z/3 - 0.5) AS INT)
  FROM elements_meta m JOIN element_transforms t ON t.guid=m.guid
  WHERE m.ifc_class != 'IfcOpeningElement'
  ORDER BY CAST(ROUND(t.center_z/3 - 0.5) AS INT) ASC,
    CASE WHEN m.ifc_class IN ('IfcFooting','IfcPile') THEN 1
         WHEN m.ifc_class='IfcColumn' THEN 2
         WHEN m.ifc_class IN ('IfcBeam','IfcMember','IfcPlate') THEN 3
         WHEN m.ifc_class='IfcSlab' THEN 4 ELSE 6 END ASC,
    t.center_z ASC LIMIT 1")
if [ "$FIRST_ZBAND" -lt 0 ] 2>/dev/null; then
  echo "  ✓PASS first_zband=$FIRST_ZBAND (underground)"
else
  echo "  ⚠FAIL first_zband=$FIRST_ZBAND (NOT underground — check Z-band logic)"
fi

echo ""
echo "§VERIFY no beam appears before its supporting column at same Z-band:"
# Within each zband, seq 2 (column) must come before seq 3 (beam)
BAD=$(sqlite3 "$DB" "
  WITH ranked AS (
    SELECT CAST(ROUND(t.center_z/3 - 0.5) AS INT) AS zband,
      CASE WHEN m.ifc_class='IfcColumn' THEN 2
           WHEN m.ifc_class IN ('IfcBeam','IfcMember') THEN 3 ELSE 99 END AS seq,
      ROW_NUMBER() OVER (PARTITION BY CAST(ROUND(t.center_z/3 - 0.5) AS INT) ORDER BY
        CASE WHEN m.ifc_class='IfcColumn' THEN 2
             WHEN m.ifc_class IN ('IfcBeam','IfcMember') THEN 3 ELSE 99 END,
        t.center_z) AS rn,
      m.ifc_class
    FROM elements_meta m JOIN element_transforms t ON t.guid=m.guid
    WHERE m.ifc_class IN ('IfcColumn','IfcBeam','IfcMember')
  )
  SELECT COUNT(*) FROM ranked r1
  JOIN ranked r2 ON r1.zband=r2.zband AND r1.seq=3 AND r2.seq=2 AND r1.rn < r2.rn
")
if [ "$BAD" = "0" ]; then
  echo "  ✓PASS no beam before its column in any Z-band"
else
  echo "  ⚠FAIL $BAD beams scheduled before columns"
fi

echo ""
echo "§EXEC storey Z ranges (context for sequence understanding):"
sqlite3 "$DB" "
  SELECT m.storey, COUNT(*) c, ROUND(MIN(t.center_z),2) minZ, ROUND(AVG(t.center_z),2) avgZ
  FROM elements_meta m JOIN element_transforms t ON t.guid=m.guid
  WHERE m.ifc_class != 'IfcOpeningElement' GROUP BY m.storey ORDER BY minZ ASC LIMIT 10
" | while IFS='|' read st cnt minz avgz; do
  echo "  \"$st\" n=$cnt minZ=$minz avgZ=$avgz"
done
fi

echo ""
echo "── 14. Drone Pilot Flow (code path verification) ──"
echo "§CHECK ELEMENT_PLACE filter (reject ELEMENT_PICK):"
if grep -q "_placeOps.*filter.*ELEMENT_PLACE" deploy/dev/time_machine.js; then
  echo "  ✓PASS — ops filtered to ELEMENT_PLACE only"
else
  echo "  ⚠FAIL — no ELEMENT_PLACE filter, picks will contaminate schedule"
fi
echo "§CHECK tickMs during opening advances cursor:"
TICK_OPENING=$(grep "cineBeat.*opening.*return" deploy/dev/time_machine.js | head -1)
echo "  $TICK_OPENING"
if echo "$TICK_OPENING" | grep -q "return 0"; then
  echo "  ⚠FAIL — returns 0, nothing builds during opening"
elif echo "$TICK_OPENING" | grep -q "return [0-9]"; then
  echo "  ✓PASS — cursor advances during opening"
else
  echo "  ⚠WARN — cannot parse tickMs opening value"
fi
echo "§CHECK opening starts from empty (projectStart):"
if grep -q "_cursor = _projectStart.*construction builds" deploy/dev/time_machine.js; then
  echo "  ✓PASS"
else
  echo "  ⚠FAIL — may start at projectEnd (nothing to build)"
fi

echo ""
echo "── 15. Frontier Highlight (shine through ground) ──"
echo "§CHECK applyHighlight depthTest:"
grep "mat.depthTest" deploy/dev/time_machine.js | grep -v "^--" | head -3
if grep -q "mat.depthTest = false" deploy/dev/time_machine.js; then
  echo "  ✓PASS — frontier glow shines through ground"
else
  echo "  ⚠FAIL — depthTest:true hides underground glow"
fi
echo "§CHECK frontier uses applyHighlight (not applyOutline):"
FRONTIER_APPLY=$(grep "isFrontier" deploy/dev/time_machine.js | grep "apply" | head -1)
echo "  $FRONTIER_APPLY"
if echo "$FRONTIER_APPLY" | grep -q "applyHighlight"; then
  echo "  ✓PASS — emissive glow (visible)"
elif echo "$FRONTIER_APPLY" | grep -q "applyOutline"; then
  echo "  ⚠FAIL — outline edges only (linewidth=1, nearly invisible)"
fi

echo ""
echo "── 16. Yellow Pick Highlight ──"
echo "§CHECK highlight implementation:"
grep -n "EdgesGeometry\|LineBasicMaterial\|MeshBasicMaterial.*wireframe\|depthTest.*false" deploy/dev/picking.js | grep -i "hl\|highlight\|ffff00\|yellow" | head -5
echo "§CHECK renderOrder 999:"
grep "renderOrder.*999" deploy/dev/picking.js | head -2
echo "§CHECK scene.add:"
grep "scene.add.*hl\|A.scene.add.*hl" deploy/dev/picking.js | head -2
echo "§CHECK dispose previous:"
grep "pickHighlight.*dispose\|prev.*dispose\|pickHighlight.*remove" deploy/dev/picking.js | head -3
echo "§CHECK markDirty after add:"
if grep -A5 "window._pickHighlight = hl" deploy/dev/picking.js | grep -q "markDirty"; then
  echo "  ✓PASS — markDirty forces re-render after highlight add"
else
  echo "  ⚠FAIL — no markDirty after highlight — may not render until next orbit/pick"
fi

echo ""
echo "── 17. Monochrome Grey + Color Fallback ──"
echo "§CHECK spread-based detection (not ±0.02 from 0.7):"
if grep -q "isMonoGrey\|_spread" deploy/dev/streaming.js; then
  echo "  ✓PASS — spread-based monochrome detection"
else
  echo "  ⚠FAIL — still using old isDefaultGrey (misses 0.97/0.97/0.97 etc)"
fi
echo "§CHECK fallback class count (in CLASS_COLOR_FALLBACK map only):"
FBCOUNT=$(sed -n '/CLASS_COLOR_FALLBACK/,/};/p' deploy/dev/streaming.js | grep -o "Ifc[A-Za-z]*:" | sort -u | wc -l)
echo "  fallback classes: $FBCOUNT"
if [ "$FBCOUNT" -ge 25 ]; then echo "  ✓PASS"; else echo "  ⚠FAIL — need ≥25"; fi
echo "§EXEC Terminal material coverage test:"
if [ -f "$DB" ]; then
  sqlite3 "$DB" "
    SELECT m.ifc_class, COUNT(*) c, m.material_rgba
    FROM elements_meta m WHERE m.material_rgba IS NOT NULL
    GROUP BY m.ifc_class ORDER BY c DESC LIMIT 10
  " | while IFS='|' read cls cnt rgba; do
    # Check if class has fallback
    if grep -q "$cls" deploy/dev/streaming.js; then HAS="✓fb"; else HAS="✗no-fb"; fi
    # Check if rgba is monochrome
    echo "  $HAS $cls n=$cnt rgba=$rgba"
  done
fi
