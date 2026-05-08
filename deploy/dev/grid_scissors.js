/**
 * BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
/**
 * grid_scissors.js — Scissors-Driven Adaptive Grids
 *
 * Implementing 2D_025 spec — Witness: W-SCISSORS
 *
 * When 2D grids are active AND scissors slider moves, detects structural
 * elements crossing the cut plane and repositions grids to that elevation.
 * Supports all 3 axes: Y (horizontal floor cut), X (vertical width cut),
 * Z (vertical depth cut).
 *
 * Gate: >= 2 grid lines required before swapping. Fallback: ground grids stay.
 * Self-contained: if this file fails to load, grid_overlay.js works unchanged.
 *
 * API: GridScissors.init(APP, overlayState) — wires callbacks
 *      GridScissors.consolidateUI(APP)       — section cut consolidation panel
 *
 * Log tags: §GRID_SCISSORS §SC_CONSOLIDATE §SC_PREVIEW
 */
var GridScissors = (function() {
  'use strict';

  var A = null;           // APP reference
  var st = null;          // grid overlay state object

  var scissorsGroup = null;    // THREE.Group for adaptive grids at cut plane
  var scissorsTimer = null;    // debounce handle
  var lastCutVal = null;       // last processed cut value (skip < 0.1m delta)

  function log(msg) { console.log('[GridScissors] ' + msg); }

  // ── Dispose ──────────────────────────────────────────────────────

  function disposeScissorsGroup() {
    if (!scissorsGroup) return;
    var texCount = 0;
    scissorsGroup.traverse(function(obj) {
      if (obj.material && obj.material.map) { obj.material.map.dispose(); texCount++; }
      if (obj.material) obj.material.dispose();
      if (obj.geometry) obj.geometry.dispose();
    });
    A.scene.remove(scissorsGroup);
    log('§GRID_SCISSORS dispose textures=' + texCount);
    scissorsGroup = null;
  }

  // ── Axis-aware detection ─────────────────────────────────────────
  // Returns {xLines, yLines} appropriate for the cut axis.
  // Y-axis cut (horizontal): detect columns/walls by IFC Z range → XY grids
  // X-axis cut (vertical):   detect by IFC X range → YZ grids
  // Z-axis cut (vertical):   detect by IFC Y range → XZ grids

  function detectAtCut(db, axis, cutVal) {
    if (typeof GridDims === 'undefined' || !GridDims.detectGridsAtPlane) return null;

    if (axis === 'Y') {
      // Horizontal cut — existing detectGridsAtPlane handles this (IFC Z)
      return GridDims.detectGridsAtPlane(db, cutVal);
    }

    // For X and Z axes, build a custom query
    var col, bboxCol;
    if (axis === 'X') {
      col = 'center_x'; bboxCol = 'bbox_x';
    } else {
      col = 'center_y'; bboxCol = 'bbox_y';
    }

    var sql =
      "SELECT m.guid, t.center_x, t.center_y, t.center_z " +
      "FROM elements_meta m " +
      "JOIN element_transforms t ON m.guid = t.guid " +
      "WHERE m.ifc_class IN ('IfcColumn','IfcWall','IfcWallStandardCase','IfcBeam','IfcMember') " +
      "  AND (t." + col + " - COALESCE(t." + bboxCol + ",3.0)/2) <= " + Number(cutVal) +
      "  AND (t." + col + " + COALESCE(t." + bboxCol + ",3.0)/2) >= " + Number(cutVal);

    var result;
    try {
      result = db.exec(sql);
    } catch (e) {
      log('§GRID_SCISSORS query error axis=' + axis + ': ' + e.message);
      return { xLines: [], yLines: [] };
    }

    if (!result || !result.length || !result[0].values.length) {
      log('§GRID_SCISSORS axis=' + axis + ' cutVal=' + cutVal.toFixed(2) + ' elements=0');
      return { xLines: [], yLines: [] };
    }

    var rows = result[0].values;
    log('§GRID_SCISSORS axis=' + axis + ' cutVal=' + cutVal.toFixed(2) + ' elements=' + rows.length);

    // Cluster into two perpendicular axes depending on cut direction
    var entries1 = [], entries2 = [];
    for (var i = 0; i < rows.length; i++) {
      var guid = rows[i][0], cx = rows[i][1], cy = rows[i][2], cz = rows[i][3];
      if (axis === 'X') {
        // Cut through X → show Y-axis + Z-axis grids
        entries1.push({ pos: cy, guid: guid });  // "xLines" = IFC Y positions
        entries2.push({ pos: cz, guid: guid });  // "yLines" = IFC Z positions
      } else {
        // Cut through Z (IFC Y) → show X-axis + Z-axis grids
        entries1.push({ pos: cx, guid: guid });  // "xLines" = IFC X positions
        entries2.push({ pos: cz, guid: guid });  // "yLines" = IFC Z positions
      }
    }

    // Reuse GridDims clustering via detectGridsAtPlane's internal pipeline
    // For cross-axis cuts we do manual clustering here
    var result1 = clusterAndLabel(entries1, true);
    var result2 = clusterAndLabel(entries2, false);

    return { xLines: result1, yLines: result2 };
  }

  /** Minimal cluster + label — reuses the same tolerance as GridDims */
  function clusterAndLabel(entries, numeric) {
    if (!entries.length) return [];
    var TOLERANCE = 0.3;
    entries.sort(function(a, b) { return a.pos - b.pos; });

    var clusters = [{ sum: entries[0].pos, count: 1, guids: [entries[0].guid] }];
    for (var i = 1; i < entries.length; i++) {
      var last = clusters[clusters.length - 1];
      var mean = last.sum / last.count;
      if (Math.abs(entries[i].pos - mean) < TOLERANCE) {
        last.sum += entries[i].pos;
        last.count++;
        last.guids.push(entries[i].guid);
      } else {
        clusters.push({ sum: entries[i].pos, count: 1, guids: [entries[i].guid] });
      }
    }

    var letterSeq = 'A,B,C,D,E,F,G,H,J,K,L,M,N,O,P,Q,R,S,T,U,V,W,X,Y,Z'.split(',');
    return clusters.map(function(c, idx) {
      var pos = c.sum / c.count;
      var lbl = numeric ? String(idx + 1) : (idx < letterSeq.length ? letterSeq[idx] : String.fromCharCode(65 + idx));
      return { label: lbl, position: pos, guids: c.guids };
    });
  }

  // ── Build adaptive grid scene ────────────────────────────────────

  function buildScissorsGrids(axis, cutVal) {
    if (!A.db) return;

    // Convert slider value to IFC coordinate
    var ifcVal;
    if (axis === 'Y') ifcVal = cutVal + (A.modelOffset ? A.modelOffset.z : 0);
    else if (axis === 'X') ifcVal = cutVal + (A.modelOffset ? A.modelOffset.x : 0);
    else ifcVal = -(cutVal) + (A.modelOffset ? A.modelOffset.y : 0); // Three.js Z = -IFC Y

    var grids = detectAtCut(A.db, axis, ifcVal);
    if (!grids) return;

    var totalLines = (grids.xLines || []).length + (grids.yLines || []).length;
    if (totalLines < 2) {
      if (scissorsGroup) {
        disposeScissorsGroup();
        if (st.gridGroup) st.gridGroup.visible = true;
        A.markDirty();
      }
      log('§GRID_SCISSORS lines=' + totalLines + ' < 2 — keeping ground grids');
      return;
    }

    // Hide ground grids, build adaptive grids at cut plane
    if (st.gridGroup) st.gridGroup.visible = false;
    disposeScissorsGroup();

    scissorsGroup = new THREE.Group();
    scissorsGroup.name = 'gridScissors';

    // Clear lineMeshes so highlight/slab uses scissors lines, not stale ground lines
    var lm = st.lineMeshes;
    for (var k in lm) { if (lm.hasOwnProperty(k)) delete lm[k]; }

    var env = st.envCache;
    var bldW = env.xMax - env.xMin;
    var bldD = env.yMax - env.yMin;
    var bldH = env.zMax - env.zMin;
    var OH_MIN = st.LINE_OVERSHOOT_MIN;
    var OH_RATIO = st.LINE_OVERSHOOT_RATIO;

    var xLines = grids.xLines || [];
    var yLines = grids.yLines || [];

    if (axis === 'Y') {
      // Horizontal cut — XY grid lines at cut elevation
      var overshootX = Math.max(OH_MIN, bldD * OH_RATIO);
      var overshootY = Math.max(OH_MIN, bldW * OH_RATIO);
      var cutY = ifcVal - A.modelOffset.z;

      for (var i = 0; i < xLines.length; i++) {
        var p0 = A.ifc2three(xLines[i].position, env.yMin - overshootX, ifcVal);
        var p1 = A.ifc2three(xLines[i].position, env.yMax + overshootX, ifcVal);
        addLine(xLines[i].label, new THREE.Vector3(p0.x, cutY, p0.z), new THREE.Vector3(p1.x, cutY, p1.z));
      }
      for (var j = 0; j < yLines.length; j++) {
        var q0 = A.ifc2three(env.xMin - overshootY, yLines[j].position, ifcVal);
        var q1 = A.ifc2three(env.xMax + overshootY, yLines[j].position, ifcVal);
        addLine(yLines[j].label, new THREE.Vector3(q0.x, cutY, q0.z), new THREE.Vector3(q1.x, cutY, q1.z));
      }

    } else if (axis === 'X') {
      // Vertical X cut — YZ grid lines at cut X
      var overshootD = Math.max(OH_MIN, bldD * OH_RATIO);
      var overshootH = Math.max(OH_MIN, bldH * OH_RATIO);
      var cutX = ifcVal - A.modelOffset.x;

      // xLines = IFC Y positions (horizontal on cut face)
      for (var i2 = 0; i2 < xLines.length; i2++) {
        var r0 = A.ifc2three(ifcVal, xLines[i2].position, env.zMin - overshootH);
        var r1 = A.ifc2three(ifcVal, xLines[i2].position, env.zMax + overshootH);
        addLine(xLines[i2].label, new THREE.Vector3(cutX, r0.y, r0.z), new THREE.Vector3(cutX, r1.y, r1.z));
      }
      // yLines = IFC Z positions (vertical on cut face)
      for (var j2 = 0; j2 < yLines.length; j2++) {
        var s0 = A.ifc2three(ifcVal, env.yMin - overshootD, yLines[j2].position);
        var s1 = A.ifc2three(ifcVal, env.yMax + overshootD, yLines[j2].position);
        addLine(yLines[j2].label, new THREE.Vector3(cutX, s0.y, s0.z), new THREE.Vector3(cutX, s1.y, s1.z));
      }

    } else {
      // Vertical Z cut (IFC Y) — XZ grid lines at cut Y
      var overshootW = Math.max(OH_MIN, bldW * OH_RATIO);
      var overshootH2 = Math.max(OH_MIN, bldH * OH_RATIO);
      var cutZ = -(ifcVal) + A.modelOffset.y;

      // xLines = IFC X positions (horizontal on cut face)
      for (var i3 = 0; i3 < xLines.length; i3++) {
        var u0 = A.ifc2three(xLines[i3].position, ifcVal, env.zMin - overshootH2);
        var u1 = A.ifc2three(xLines[i3].position, ifcVal, env.zMax + overshootH2);
        addLine(xLines[i3].label, new THREE.Vector3(u0.x, u0.y, cutZ), new THREE.Vector3(u1.x, u1.y, cutZ));
      }
      // yLines = IFC Z positions (vertical on cut face)
      for (var j3 = 0; j3 < yLines.length; j3++) {
        var w0 = A.ifc2three(env.xMin - overshootW, ifcVal, yLines[j3].position);
        var w1 = A.ifc2three(env.xMax + overshootW, ifcVal, yLines[j3].position);
        addLine(yLines[j3].label, new THREE.Vector3(w0.x, w0.y, cutZ), new THREE.Vector3(w1.x, w1.y, cutZ));
      }
    }

    A.scene.add(scissorsGroup);

    // Rebuild panel with adaptive grid dimensions
    st.rebuildPanel(grids);

    // Rebuild dim chains for adaptive grids
    st.removeDimChains();
    if (typeof DimChains !== 'undefined') {
      DimChains.build(A, scissorsGroup, grids, env, { bubbleScale: st.bubbleScale });
    }

    // Status banner
    var axisLabel = axis === 'Y' ? 'Z=' + ifcVal.toFixed(1) + 'm' :
                    axis === 'X' ? 'X=' + ifcVal.toFixed(1) + 'm' :
                                   'Y=' + ifcVal.toFixed(1) + 'm';
    A.status.textContent = 'Scissors grid @' + axisLabel + ' — ' +
        totalLines + ' lines (check console F12 for §GRID_SCISSORS)';

    A.markDirty();
    log('§GRID_SCISSORS axis=' + axis + ' cutVal=' + ifcVal.toFixed(2) +
        ' xLines=' + xLines.length + ' yLines=' + yLines.length);
  }

  function addLine(label, v0, v1) {
    var geom = new THREE.BufferGeometry().setFromPoints([v0, v1]);
    var mat = new THREE.LineBasicMaterial({ color: st.lineColor() });
    var line = new THREE.Line(geom, mat);
    line.renderOrder = 999;
    line.userData = { gridLabel: label, gridAxis: 'S', gridPos: 0 };
    scissorsGroup.add(line);
    scissorsGroup.add(st.createBubble(label, v0, false));
    scissorsGroup.add(st.createBubble(label, v1, false));
    // Register in lineMeshes so highlightGrid + orange slab work
    st.lineMeshes[label] = { line: line, v0: v0.clone(), v1: v1.clone() };
  }

  // ── Callbacks wired by init ──────────────────────────────────────

  function onSliderChange(val) {
    if (!st.active) return;
    var axis = A.sectionAxis || 'Y';
    // Skip if moved < 0.1m
    if (lastCutVal !== null && Math.abs(val - lastCutVal) < 0.1) return;
    if (scissorsTimer) clearTimeout(scissorsTimer);
    scissorsTimer = setTimeout(function() {
      lastCutVal = val;
      try {
        buildScissorsGrids(axis, val);
      } catch (e) {
        log('§GRID_SCISSORS ERROR: ' + e.message);
        disposeScissorsGroup();
        if (st.gridGroup) st.gridGroup.visible = true;
        A.markDirty();
      }
    }, 200);
  }

  function onOff() {
    if (scissorsTimer) { clearTimeout(scissorsTimer); scissorsTimer = null; }
    lastCutVal = null;
    disposeScissorsGroup();
    if (st.active && st.gridGroup) {
      st.gridGroup.visible = true;
      if (st.gridData) {
        st.rebuildPanel(st.gridData);
        st.removeDimChains();
        st.buildDimChains(st.gridData, st.envCache);
      }
      A.status.textContent = 'Grid mode — ' +
        ((st.gridData.xLines || []).length + (st.gridData.yLines || []).length) + ' grid lines';
      A.markDirty();
    }
    log('§GRID_SCISSORS off — ground grids restored');
  }

  // ── Init ─────────────────────────────────────────────────────────

  function init(app, overlayState) {
    A = app;
    st = overlayState;
    A.onSectionSliderChange = onSliderChange;
    A.onSectionOff = onOff;
    log('§GRID_SCISSORS init — all 3 axes ready');
  }

  // ── Consolidation UI ─────────────────────────────────────────────
  // Implementing 2D_027 §4.2 — Witness: W-2D27

  function consolidateUI(APP) {
    // Load current saved cuts
    if (typeof SectionCut === 'undefined' || !SectionCut.savedCuts) {
      alert('No saved section cuts yet. Save cuts using the section slider first.');
      return;
    }
    SectionCut._loadCuts && SectionCut._loadCuts(APP.activeBuilding || 'bld');
    var cuts = SectionCut.savedCuts.slice();

    // Remove any existing panel
    var old = document.getElementById('sc-consolidate-panel');
    if (old) old.parentNode.removeChild(old);

    var panel = document.createElement('div');
    panel.id = 'sc-consolidate-panel';
    panel.style.cssText = [
      'position:fixed;top:50%;left:50%;transform:translate(-50%,-50%);',
      'background:#1a2530;color:#ccc;border:1px solid #446;border-radius:6px;',
      'padding:16px;min-width:340px;z-index:9999;font-family:monospace;font-size:13px;'
    ].join('');

    // Header
    var hdr = document.createElement('div');
    hdr.style.cssText = 'font-weight:bold;margin-bottom:10px;font-size:14px;';
    hdr.textContent = 'Section Cut Consolidation';
    panel.appendChild(hdr);

    // Close button
    var closeBtn = document.createElement('button');
    closeBtn.textContent = '×';
    closeBtn.style.cssText = 'position:absolute;top:8px;right:10px;background:none;border:none;color:#aaa;font-size:18px;cursor:pointer;';
    closeBtn.onclick = function() { panel.parentNode.removeChild(panel); };
    panel.appendChild(closeBtn);

    // Cut list with checkboxes
    var listDiv = document.createElement('div');
    listDiv.style.cssText = 'margin-bottom:10px;max-height:200px;overflow-y:auto;';

    if (!cuts.length) {
      listDiv.textContent = 'No saved cuts.';
    } else {
      cuts.forEach(function(cut, idx) {
        // Check adjacency: gap < 0.5m to next cut on same axis
        var isAdjacent = false;
        if (idx < cuts.length - 1 && cuts[idx + 1].axis === cut.axis) {
          isAdjacent = Math.abs(cuts[idx + 1].constant - cut.constant) < 0.5;
        }
        var row = document.createElement('div');
        row.style.cssText = 'margin:4px 0;display:flex;align-items:center;gap:8px;';
        var cb = document.createElement('input');
        cb.type = 'checkbox';
        cb.value = cut.name;
        cb.id = 'sc-cb-' + idx;
        var lbl = document.createElement('label');
        lbl.htmlFor = 'sc-cb-' + idx;
        lbl.textContent = cut.name + '  ' + cut.label + (isAdjacent ? '  \u2190 adjacent' : '');
        lbl.style.cursor = 'pointer';
        row.appendChild(cb);
        row.appendChild(lbl);
        listDiv.appendChild(row);
      });
    }
    panel.appendChild(listDiv);

    // Info line
    var info = document.createElement('div');
    info.style.cssText = 'font-size:11px;color:#888;margin-bottom:10px;';
    info.textContent = 'Adjacent cuts (gap < 0.5m) shown with \u2190. Select 2+ cuts from SAME axis to merge.';
    panel.appendChild(info);

    // Buttons
    var btnRow = document.createElement('div');
    btnRow.style.cssText = 'display:flex;gap:8px;';

    function getSelected() {
      return cuts.filter(function(c, idx) {
        var cb = document.getElementById('sc-cb-' + idx);
        return cb && cb.checked;
      });
    }

    var previewBtn = document.createElement('button');
    previewBtn.textContent = 'Preview Merge';
    previewBtn.style.cssText = 'padding:6px 12px;cursor:pointer;background:#2a4a6a;color:#fff;border:1px solid #446;border-radius:3px;';
    previewBtn.onclick = function() {
      var sel = getSelected();
      if (sel.length < 2) { alert('Select 2 or more cuts to preview merge.'); return; }
      var axes = sel.map(function(c) { return c.axis; });
      if (axes.some(function(a) { return a !== axes[0]; })) { alert('All selected cuts must be on the same axis.'); return; }
      var lo = Math.min.apply(null, sel.map(function(c) { return c.constant; }));
      var hi = Math.max.apply(null, sel.map(function(c) { return c.constant; }));
      console.log('[GridScissors] §SC_PREVIEW merged axis=' + axes[0] + ' range=[' + lo.toFixed(3) + ',' + hi.toFixed(3) + ']');
      alert('Preview: merged cut on ' + axes[0] + ' axis from ' + lo.toFixed(2) + 'm to ' + hi.toFixed(2) + 'm');
    };

    var confirmBtn = document.createElement('button');
    confirmBtn.textContent = 'Confirm Merge';
    confirmBtn.style.cssText = 'padding:6px 12px;cursor:pointer;background:#2a6a3a;color:#fff;border:1px solid #464;border-radius:3px;';
    confirmBtn.onclick = function() {
      var sel = getSelected();
      if (sel.length < 2) { alert('Select 2 or more cuts to merge.'); return; }
      var axes = sel.map(function(c) { return c.axis; });
      if (axes.some(function(a) { return a !== axes[0]; })) { alert('All selected cuts must be on the same axis.'); return; }
      var lo = Math.min.apply(null, sel.map(function(c) { return c.constant; }));
      var hi = Math.max.apply(null, sel.map(function(c) { return c.constant; }));
      // Gap warning: if any pair of selected cuts has gap > 0.5m
      var sortedConst = sel.map(function(c) { return c.constant; }).sort(function(a, b) { return a - b; });
      var maxGap = 0;
      for (var gi = 1; gi < sortedConst.length; gi++) {
        maxGap = Math.max(maxGap, sortedConst[gi] - sortedConst[gi - 1]);
      }
      if (maxGap > 0.5) {
        if (!confirm('Gap of ' + maxGap.toFixed(1) + 'm will be filled in merged view — confirm?')) return;
      }
      // Remove originals
      var removedNames = sel.map(function(c) { return c.name; });
      sel.forEach(function(c) { SectionCut.removeCut && SectionCut.removeCut(APP, c.name); });
      // Add merged entry
      var mergeN = (SectionCut.savedCuts.length + 1);
      var mergedName = 'SectionCut_merged_' + mergeN;
      SectionCut.savedCuts.push({
        name: mergedName,
        axis: axes[0],
        constant: lo,
        to: hi,
        label: axes[0] + ' @ ' + lo.toFixed(2) + 'm\u2013' + hi.toFixed(2) + 'm'
      });
      SectionCut._saveCutsToStorage && SectionCut._saveCutsToStorage(APP.activeBuilding || 'bld');
      console.log('[GridScissors] §SC_CONSOLIDATE merged=' + mergedName +
        ' from=[' + removedNames.join(',') + ']' +
        ' axis=' + axes[0] +
        ' range=[' + lo.toFixed(3) + ',' + hi.toFixed(3) + ']');
      // Offer snapshot
      if (typeof PrintSheet !== 'undefined' && confirm('Save as Image Snap?')) {
        PrintSheet.capture && PrintSheet.capture(APP, { preview: false, filename: mergedName + '_snap.png' });
      }
      panel.parentNode.removeChild(panel);
    };

    var cancelBtn = document.createElement('button');
    cancelBtn.textContent = 'Cancel';
    cancelBtn.style.cssText = 'padding:6px 12px;cursor:pointer;background:#3a2a2a;color:#ccc;border:1px solid #644;border-radius:3px;';
    cancelBtn.onclick = function() { panel.parentNode.removeChild(panel); };

    btnRow.appendChild(previewBtn);
    btnRow.appendChild(confirmBtn);
    btnRow.appendChild(cancelBtn);
    panel.appendChild(btnRow);

    document.body.appendChild(panel);
  }

  return {
    init: init,
    consolidateUI: consolidateUI
  };

})();
