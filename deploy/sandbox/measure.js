// measure.js — Measurement tool (two-point distance, area, clash detection)
function setupMeasure(A) {

  // ── Draggable panels ──
  A._makeDraggable = function(el) {
    var ox, oy, sx, sy, dragging = false;
    el.style.cursor = 'grab';
    el.addEventListener('pointerdown', function(e) {
      // Don't drag from interactive elements
      if (e.target.tagName === 'INPUT') return;
      if (e.target.id && (e.target.id.indexOf('close') >= 0 || e.target.id.indexOf('export') >= 0)) return;
      if (e.target.closest('[data-clash-idx]') || e.target.closest('[data-pair]')) return;
      var rect = el.getBoundingClientRect();
      if (e.clientY - rect.top > 30) return; // only drag from top strip
      dragging = true;
      ox = e.clientX; oy = e.clientY;
      sx = rect.left; sy = rect.top;
      el.setPointerCapture(e.pointerId);
      e.preventDefault();
    });
    el.addEventListener('pointermove', function(e) {
      if (!dragging) return;
      el.style.left = (sx + e.clientX - ox) + 'px';
      el.style.top = (sy + e.clientY - oy) + 'px';
      el.style.right = 'auto';
      el.style.bottom = 'auto';
    });
    el.addEventListener('pointerup', function() { dragging = false; });
  };

  // ── Clash detection (bbox overlap from DB, rules from clash_rules.json) ──
  A._clashRules = null;
  A._clashRulesLoading = false;

  A._loadClashRules = function(cb) {
    if (A._clashRules) { cb(A._clashRules); return; }
    if (A._clashRulesLoading) return;
    A._clashRulesLoading = true;
    fetch('clash_rules.json?v=1').then(function(r) { return r.json(); }).then(function(j) {
      A._clashRules = j;
      A._clashRulesLoading = false;
      console.log('§CLASH_RULES loaded ' + j.clash_rules.length + ' rules');
      cb(j);
    }).catch(function(e) {
      A._clashRulesLoading = false;
      console.warn('§CLASH_RULES failed', e);
    });
  };

  // Clash query helpers
  // bbox_x/y/z are FULL widths, centered at center_x/y/z
  A._CLASH_PAGE_SIZE = 30;

  // Ensure indexes exist for clash queries — one-time cost per session
  A._clashIndexesReady = false;
  A._ensureClashIndexes = function() {
    if (A._clashIndexesReady || !A.db) return;
    try {
      A.db.run("CREATE INDEX IF NOT EXISTS idx_meta_disc ON elements_meta(discipline)");
      A.db.run("CREATE INDEX IF NOT EXISTS idx_meta_storey ON elements_meta(storey)");
      A.db.run("CREATE INDEX IF NOT EXISTS idx_trans_cx ON element_transforms(center_x)");
      A._clashIndexesReady = true;
      console.log('§CLASH_INDEXES created');
    } catch(e) { console.warn('§CLASH_INDEXES failed', e); }
  };

  // Build the shared WHERE clause parts (also ensures indexes)
  A._clashWhereParts = function(rules) {
    A._ensureClashIndexes();
    var ignoreSet = {};
    rules.clash_rules.forEach(function(r) {
      (r.ignore_classes || []).forEach(function(c) { ignoreSet[c] = 1; });
    });
    var ignoreWhere = Object.keys(ignoreSet).map(function(c) { return "'" + c + "'"; }).join(',');
    return {
      ignoreClause: ignoreWhere ? ' AND ma.ifc_class NOT IN (' + ignoreWhere + ') AND mb.ifc_class NOT IN (' + ignoreWhere + ')' : '',
      bboxJoin: " AND (a.center_x - a.bbox_x/2) < (b.center_x + b.bbox_x/2)" +
        " AND (a.center_x + a.bbox_x/2) > (b.center_x - b.bbox_x/2)" +
        " AND (a.center_y - a.bbox_y/2) < (b.center_y + b.bbox_y/2)" +
        " AND (a.center_y + a.bbox_y/2) > (b.center_y - b.bbox_y/2)" +
        " AND (a.center_z - a.bbox_z/2) < (b.center_z + b.bbox_z/2)" +
        " AND (a.center_z + a.bbox_z/2) > (b.center_z - b.bbox_z/2)"
    };
  };

  // Quick EXISTS check per discipline pair — for matrix spheres
  // Uses EXISTS + LIMIT 1 to stop at first match (fast even on 48k elements)
  A._clashExistsPerPair = function(storey, rules) {
    if (!A._hasBbox) return {};
    var w = A._clashWhereParts(rules);
    var storeyClause = storey ? "ma.storey = '" + storey.replace(/'/g, "''") + "' AND mb.storey = ma.storey" : '1=1';
    // First check element counts per discipline — skip pairs where either side has 0
    var discCounts = {};
    var dcRows = A.dbQuery("SELECT discipline, COUNT(*) FROM elements_meta WHERE discipline IS NOT NULL GROUP BY discipline");
    dcRows.forEach(function(r) { discCounts[r[0]] = r[1]; });

    var result = {};
    rules.clash_rules.forEach(function(r) {
      var key = r.source.discipline + '|' + r.target.discipline;
      var key2 = r.target.discipline + '|' + r.source.discipline;
      // Skip if either discipline has no elements
      if (!discCounts[r.source.discipline] || !discCounts[r.target.discipline]) {
        result[key] = 0; result[key2] = 0;
        return;
      }
      var pairCond = "(ma.discipline = '" + r.source.discipline + "' AND mb.discipline = '" + r.target.discipline + "')" +
        " OR (ma.discipline = '" + r.target.discipline + "' AND mb.discipline = '" + r.source.discipline + "')";
      var sql = "SELECT 1 FROM element_transforms a" +
        " JOIN elements_meta ma ON a.guid = ma.guid" +
        " JOIN element_transforms b ON a.guid < b.guid" +
        " JOIN elements_meta mb ON b.guid = mb.guid" +
        " WHERE " + storeyClause + " AND (" + pairCond + ")" + w.ignoreClause + w.bboxJoin +
        " LIMIT 1";
      var rows = A.dbQuery(sql);
      var hasClash = rows.length > 0 ? 1 : 0;
      result[key] = hasClash;
      result[key2] = hasClash;
      console.log('§CLASH_EXISTS ' + key + ' = ' + hasClash);
    });
    return result;
  };

  // Query clashes for a SPECIFIC discipline pair, with pagination
  A._queryClashesPair = function(storey, rules, discA, discB, offset) {
    if (!A._hasBbox) return [];
    var w = A._clashWhereParts(rules);
    var storeyClause = storey ? "ma.storey = '" + storey.replace(/'/g, "''") + "' AND mb.storey = ma.storey" : '1=1';
    var pairCond = "(ma.discipline = '" + discA + "' AND mb.discipline = '" + discB + "')" +
      " OR (ma.discipline = '" + discB + "' AND mb.discipline = '" + discA + "')";
    var sql = "SELECT a.guid, b.guid, ma.ifc_class, mb.ifc_class, ma.discipline, mb.discipline," +
      " ma.element_name, mb.element_name," +
      " MIN((a.center_x + a.bbox_x/2) - (b.center_x - b.bbox_x/2)," +
      "     (a.center_y + a.bbox_y/2) - (b.center_y - b.bbox_y/2)," +
      "     (a.center_z + a.bbox_z/2) - (b.center_z - b.bbox_z/2)) AS overlap_m" +
      " FROM element_transforms a JOIN elements_meta ma ON a.guid = ma.guid" +
      " JOIN element_transforms b ON a.guid < b.guid JOIN elements_meta mb ON b.guid = mb.guid" +
      " WHERE " + storeyClause + " AND (" + pairCond + ")" + w.ignoreClause + w.bboxJoin +
      " ORDER BY overlap_m DESC" +
      " LIMIT " + A._CLASH_PAGE_SIZE + " OFFSET " + (offset || 0);
    var rows = A.dbQuery(sql);
    console.log('§CLASH_QUERY ' + discA + ' vs ' + discB + ' offset=' + (offset || 0) + ' got=' + rows.length);
    return rows;
  };

  // Fast check: any clashes at all? (for info card sphere — just yes/no)
  A._queryClashes = function(storey, rules) {
    if (!A._hasBbox) return [];
    var w = A._clashWhereParts(rules);
    var pairConds = rules.clash_rules.map(function(r) {
      return "(ma.discipline = '" + r.source.discipline + "' AND mb.discipline = '" + r.target.discipline + "')" +
             " OR (ma.discipline = '" + r.target.discipline + "' AND mb.discipline = '" + r.source.discipline + "')";
    }).join(' OR ');
    if (!pairConds) return [];
    var storeyClause = storey ? "ma.storey = '" + storey.replace(/'/g, "''") + "' AND mb.storey = ma.storey" : '1=1';
    // Just find first clash to determine sphere color
    var sql = "SELECT a.guid, b.guid, ma.ifc_class, mb.ifc_class, ma.discipline, mb.discipline," +
      " ma.element_name, mb.element_name," +
      " MIN((a.center_x + a.bbox_x/2) - (b.center_x - b.bbox_x/2)," +
      "     (a.center_y + a.bbox_y/2) - (b.center_y - b.bbox_y/2)," +
      "     (a.center_z + a.bbox_z/2) - (b.center_z - b.bbox_z/2)) AS overlap_m" +
      " FROM element_transforms a JOIN elements_meta ma ON a.guid = ma.guid" +
      " JOIN element_transforms b ON a.guid < b.guid JOIN elements_meta mb ON b.guid = mb.guid" +
      " WHERE " + storeyClause + " AND (" + pairConds + ")" + w.ignoreClause + w.bboxJoin +
      " LIMIT 1";
    var rows = A.dbQuery(sql);
    console.log('§CLASH_EXISTS_ANY storey=' + (storey || 'ALL') + ' found=' + rows.length);
    return rows;
  };

  // Classify clash severity from rules
  A._clashSeverity = function(overlap, rules) {
    var sev = rules.severity;
    if (overlap >= sev.hard.min_overlap_m) return { level: 'hard', color: sev.hard.color, label: sev.hard.label };
    if (overlap >= sev.soft.min_overlap_m) return { level: 'soft', color: sev.soft.color, label: sev.soft.label };
    return { level: 'clearance', color: sev.clearance.color, label: sev.clearance.label };
  };

  // Reveal clashes — dim scene, show itemised list
  A._clashRevealActive = false;
  A._clashBackups = [];

  // Status cycle: (none) → Reviewed → Resolved → Accepted → (none)
  A._clashStatusCycle = ['', 'Reviewed', 'Resolved', 'Accepted'];
  A._clashStatusStyles = {
    '':         { icon: '',  style: '' },
    'Reviewed': { icon: '\u{1F535}', style: 'opacity:0.7' },
    'Resolved': { icon: '\u2705', style: 'text-decoration:line-through;opacity:0.6' },
    'Accepted': { icon: '\u2796', style: 'font-style:italic;color:#888' }
  };

  // Load/save statuses from localStorage
  A._clashStatusKey = function() {
    return 'bim-clash-statuses-' + (A.activeBuilding || 'default');
  };
  A._clashStatuses = {};
  A._loadClashStatuses = function() {
    try {
      var raw = localStorage.getItem(A._clashStatusKey());
      A._clashStatuses = raw ? JSON.parse(raw) : {};
    } catch(e) { A._clashStatuses = {}; }
  };
  A._saveClashStatuses = function() {
    try { localStorage.setItem(A._clashStatusKey(), JSON.stringify(A._clashStatuses)); } catch(e) {}
  };
  A._clashPairKey = function(guidA, guidB) { return guidA + '|' + guidB; };

  A._revealClashes = function(clashes, rules, cardX, cardY, pairLabel, pairRule) {
    if (A._clashRevealActive) A._dismissClashes();
    A._clashRevealActive = true;
    A._loadClashStatuses();
    A._currentClashes = clashes;
    A._currentClashRules = rules;
    var display = rules.display || {};
    var dimOpacity = display.dim_opacity || 0.1;
    var maxVisible = display.max_visible || 20;

    // Collect GUIDs of clashing elements
    var clashGuids = {};
    clashes.forEach(function(r) { clashGuids[r[0]] = 1; clashGuids[r[1]] = 1; });

    // Count visible meshes to decide dimming strategy
    var meshCount = 0;
    A.scene.traverse(function(obj) { if (obj.isMesh && obj !== A.ground) meshCount++; });

    // Dim non-clashing meshes — skip on large scenes (>3000 meshes) for performance
    if (meshCount <= 3000) {
      A.scene.traverse(function(obj) {
        if (!obj.isMesh || obj === A.ground || !obj.visible) return;
        var guid = obj.userData.guid;
        if (guid && clashGuids[guid]) return;
        A._clashBackups.push({ mesh: obj, origMat: obj.material });
        var dimMat = obj.material.clone();
        dimMat.transparent = true;
        dimMat.opacity = dimOpacity;
        dimMat.needsUpdate = true;
        obj.material = dimMat;
      });
    }

    // Build itemised list
    var shown = Math.min(clashes.length, maxVisible);
    var listDiv = document.createElement('div');
    listDiv.style.cssText = 'position:fixed;z-index:400;background:rgba(20,60,100,0.55);backdrop-filter:blur(8px);-webkit-backdrop-filter:blur(8px);color:#fff;font-size:11px;padding:8px 10px;border-radius:8px;border:1px solid rgba(255,140,0,0.6);font-family:Segoe UI,sans-serif;line-height:1.5;min-width:180px;max-width:240px;max-height:40vh;overflow-y:auto;pointer-events:auto';
    // Position: right side, above the matrix if it exists
    listDiv.style.right = '10px';
    if (A._clashMatrixDiv) {
      var matRect = A._clashMatrixDiv.getBoundingClientRect();
      listDiv.style.bottom = (window.innerHeight - matRect.top + 6) + 'px';
      listDiv.style.top = 'auto';
    } else {
      listDiv.style.top = Math.min(Math.max(cardY - 50, 10), window.innerHeight - 300) + 'px';
    }

    A._renderClashList = function() {
      var lines = [];
      // Header: pair label + tolerance slider
      if (pairLabel) {
        var tolMm = pairRule ? (pairRule.tolerance_m * 1000).toFixed(0) : '25';
        lines.push('<div style="display:flex;justify-content:space-between;align-items:center">' +
          '<b style="color:#4fc3f7;font-size:12px">' + pairLabel + '</b>' +
          '<span id="clash-list-close" style="cursor:pointer;color:#aaa;font-size:16px;line-height:1">\u2715</span></div>');
        lines.push('<div style="display:flex;align-items:center;gap:4px;margin:2px 0">' +
          '<span style="font-size:9px;color:#aaa">1</span>' +
          '<input id="clash-tol-slider" type="range" min="1" max="100" value="' + tolMm + '" ' +
          'style="flex:1;height:4px;accent-color:#4fc3f7;cursor:pointer">' +
          '<span style="font-size:9px;color:#aaa">100</span>' +
          '<span id="clash-tol-val" style="font-size:10px;color:#fff;min-width:30px">' + tolMm + 'mm</span></div>');
      }
      lines.push('<span style="color:#fff;font-size:11px">' + clashes.length + '</span>');
      lines.push('<hr style="border:none;border-top:1px solid #555;margin:3px 0">');

      for (var i = 0; i < shown; i++) {
        var c = clashes[i];
        // Brief: class abbreviations only (e.g. "Wall↔Beam 0.12m")
        var clsA = (c[2] || '?').replace('Ifc', '').replace('StandardCase', '');
        var clsB = (c[3] || '?').replace('Ifc', '').replace('StandardCase', '');
        var overlap = (typeof c[8] === 'number') ? c[8] : 0;
        var sev = A._clashSeverity(overlap, rules);
        var pairKey = A._clashPairKey(c[0], c[1]);
        var status = A._clashStatuses[pairKey] || '';
        var ss = A._clashStatusStyles[status];
        lines.push(
          '<span data-clash-idx="' + i + '" style="cursor:pointer;display:block;padding:1px 0;' + ss.style + '">' +
          (ss.icon ? ss.icon : '<span style="color:#888">' + (i + 1) + '</span>') +
          ' ' + clsA + '\u2194' + clsB +
          ' <b style="color:' + sev.color + '">' + overlap.toFixed(2) + 'm</b>' +
          '</span>'
        );
      }
      if (clashes.length > maxVisible) {
        lines.push('<span style="color:#888;font-size:9px">+' + (clashes.length - maxVisible) + ' more \u2192 Export</span>');
      }
      // No export here — export is on the matrix title bar
      return lines.join('<br>');
    };

    listDiv.innerHTML = A._renderClashList();
    document.body.appendChild(listDiv);
    A._clashListDiv = listDiv;
    A._makeDraggable(listDiv);
    A.measureLabels.push({ div: listDiv, mid: null });

    // Tolerance slider — re-query on change
    var slider = listDiv.querySelector('#clash-tol-slider');
    if (slider && pairRule) {
      slider.addEventListener('input', function() {
        var valEl = listDiv.querySelector('#clash-tol-val');
        if (valEl) valEl.textContent = slider.value + 'mm';
      });
      slider.addEventListener('change', function() {
        pairRule.tolerance_m = parseInt(slider.value) / 1000;
        console.log('§CLASH_TOL_SLIDER ' + (pairLabel || '') + ' to ' + slider.value + 'mm');
        // Re-query with new tolerance
        A._dismissClashes();
        A._clashPairOffset = 0;
        var parts = (pairLabel || '').split(' vs ');
        if (parts.length === 2) {
          var newClashes = A._queryClashesPair(A._currentClashStorey, rules, parts[0], parts[1], 0);
          A._currentClashes = newClashes;
          A._clashPairOffset = A._CLASH_PAGE_SIZE;
          var rect = A._clashMatrixDiv ? A._clashMatrixDiv.getBoundingClientRect() : { left: cardX, top: cardY };
          A._revealClashes(newClashes, rules, rect.left, rect.top, pairLabel, pairRule);
        }
      });
    }

    // Click handler — left-click row to fly, right-click/long-press row to toggle status
    var statusLongPress = null;
    var statusLongFired = false;

    listDiv.addEventListener('pointerdown', function(ev) {
      var target = ev.target.closest('[data-clash-idx]');
      if (!target) return;
      statusLongFired = false;
      var idx = parseInt(target.getAttribute('data-clash-idx'));
      statusLongPress = setTimeout(function() {
        statusLongFired = true;
        statusLongPress = null;
        A._toggleClashStatus(idx);
      }, 400);
    });
    listDiv.addEventListener('pointerup', function() {
      if (statusLongPress) { clearTimeout(statusLongPress); statusLongPress = null; }
    });

    listDiv.addEventListener('contextmenu', function(ev) {
      var target = ev.target.closest('[data-clash-idx]');
      if (!target) return;
      ev.preventDefault();
      var idx = parseInt(target.getAttribute('data-clash-idx'));
      A._toggleClashStatus(idx);
    });

    listDiv.addEventListener('click', function(ev) {
      if (statusLongFired) { statusLongFired = false; return; }
      // Close X
      if (ev.target.id === 'clash-list-close') {
        A._dismissClashes();
        return;
      }
      // Export button
      if (ev.target.id === 'clash-export-btn' || ev.target.closest('#clash-export-btn')) {
        A._exportClashReport();
        return;
      }
      // Slider handled separately below
      var target = ev.target.closest('[data-clash-idx]');
      if (!target) return;
      var idx = parseInt(target.getAttribute('data-clash-idx'));
      var c = clashes[idx];
      if (!c) return;
      // Fly to pair — get positions from DB (works for merged/instanced meshes too)
      var posRows = A.dbQuery(
        "SELECT t.center_x, t.center_y, t.center_z, t.bbox_x, t.bbox_y, t.bbox_z FROM element_transforms t WHERE t.guid IN (?, ?)",
        [c[0], c[1]]
      );
      if (posRows.length >= 2) {
        var pA = A.ifc2three(posRows[0][0], posRows[0][1], posRows[0][2]);
        var pB = A.ifc2three(posRows[1][0], posRows[1][1], posRows[1][2]);
        var vA = new THREE.Vector3(pA.x, pA.y, pA.z);
        var vB = new THREE.Vector3(pB.x, pB.y, pB.z);
        var mid = new THREE.Vector3().addVectors(vA, vB).multiplyScalar(0.5);

        // Yellow wireframe boxes around each element
        // Remove previous clash highlights
        if (A._clashHighlights) {
          A._clashHighlights.forEach(function(h) { A.measureGroup.remove(h); });
        }
        A._clashHighlights = [];
        var sev = A._clashSeverity((typeof c[8] === 'number') ? c[8] : 0, rules);
        var sevColor = parseInt(sev.color.replace('#', ''), 16);

        // Compute overlap zone in Three.js space — used for clipping planes
        var rA = posRows[0], rB = posRows[1];
        // IFC overlap bounds
        var oxMin = Math.max(rA[0] - rA[3]/2, rB[0] - rB[3]/2);
        var oxMax = Math.min(rA[0] + rA[3]/2, rB[0] + rB[3]/2);
        var oyMin = Math.max(rA[1] - rA[4]/2, rB[1] - rB[4]/2);
        var oyMax = Math.min(rA[1] + rA[4]/2, rB[1] + rB[4]/2);
        var ozMin = Math.max(rA[2] - rA[5]/2, rB[2] - rB[5]/2);
        var ozMax = Math.min(rA[2] + rA[5]/2, rB[2] + rB[5]/2);

        if (oxMin < oxMax && oyMin < oyMax && ozMin < ozMax) {
          // Convert overlap bounds to Three.js space
          var oMinT = A.ifc2three(oxMin, oyMin, ozMin);
          var oMaxT = A.ifc2three(oxMax, oyMax, ozMax);
          // Normalize min/max per axis (ifc2three flips Z)
          var tXmin = Math.min(oMinT.x, oMaxT.x), tXmax = Math.max(oMinT.x, oMaxT.x);
          var tYmin = Math.min(oMinT.y, oMaxT.y), tYmax = Math.max(oMinT.y, oMaxT.y);
          var tZmin = Math.min(oMinT.z, oMaxT.z), tZmax = Math.max(oMinT.z, oMaxT.z);

          // 6 clipping planes defining the overlap box
          var clipPlanes = [
            new THREE.Plane(new THREE.Vector3( 1, 0, 0), -tXmin),
            new THREE.Plane(new THREE.Vector3(-1, 0, 0),  tXmax),
            new THREE.Plane(new THREE.Vector3( 0, 1, 0), -tYmin),
            new THREE.Plane(new THREE.Vector3( 0,-1, 0),  tYmax),
            new THREE.Plane(new THREE.Vector3( 0, 0, 1), -tZmin),
            new THREE.Plane(new THREE.Vector3( 0, 0,-1),  tZmax)
          ];

          // Fetch actual mesh geometry for both elements
          var hashRows = A.dbQuery("SELECT guid, geometry_hash FROM element_instances WHERE guid IN (?, ?)", [c[0], c[1]]);
          var colors = [0xff4444, 0x4488ff]; // red-ish, blue-ish to distinguish
          hashRows.forEach(function(hr, hi) {
            var geo = A.meshCache[hr[1]];
            if (!geo) {
              // Fetch from DB if not cached
              var gRows = A.dbQuery("SELECT vertices, faces FROM component_geometries WHERE geometry_hash = ?", [hr[1]]);
              if (gRows.length && gRows[0][0] && gRows[0][1]) {
                geo = A.blobToGeometry(gRows[0][0], gRows[0][1]);
                if (geo) A.meshCache[hr[1]] = geo;
              }
            }
            if (!geo) return;
            // Get this element's transform
            var tRow = A.dbQuery("SELECT center_x, center_y, center_z, rotation_x, rotation_y, rotation_z FROM element_transforms WHERE guid = ?", [hr[0]]);
            if (!tRow.length) return;
            var pos = A.ifc2three(tRow[0][0], tRow[0][1], tRow[0][2]);
            var mat = new THREE.MeshBasicMaterial({
              color: colors[hi],
              transparent: true,
              opacity: 0.6,
              side: THREE.DoubleSide,
              depthTest: false,
              clippingPlanes: clipPlanes,
              clipShadows: true
            });
            var mesh = new THREE.Mesh(geo.clone(), mat);
            mesh.position.set(pos.x, pos.y, pos.z);
            if (tRow[0][3] || tRow[0][4] || tRow[0][5]) {
              mesh.rotation.set(tRow[0][3] || 0, tRow[0][5] || 0, -(tRow[0][4] || 0));
            }
            A.measureGroup.add(mesh);
            A._clashHighlights.push(mesh);
          });

          // Enable clipping on renderer if not already
          if (A.renderer) A.renderer.localClippingEnabled = true;
        }

        // Fly camera
        var maxBbox = Math.max(posRows[0][3], posRows[0][4], posRows[0][5],
                               posRows[1][3], posRows[1][4], posRows[1][5]);
        var dist = Math.max(maxBbox * 3, 5);
        if (A.controls && A.controls.target) {
          A.controls.target.copy(mid);
          A.camera.position.set(mid.x + dist * 0.6, mid.y + dist * 0.5, mid.z + dist * 0.6);
          A.controls.update();
          if (A.markDirty) A.markDirty();
        }
        console.log('§CLASH_DETAIL guidA=' + c[0] + ' guidB=' + c[1] + ' overlap=' + ((typeof c[8] === 'number') ? c[8].toFixed(3) : '?') + 'm');
      }
    });

    // Tap outside to dismiss
    A._clashDismissHandler = function(ev) {
      if (listDiv.contains(ev.target)) return;
      if (A._clashMatrixDiv && A._clashMatrixDiv.contains(ev.target)) return;
      A._dismissClashes();
    };
    setTimeout(function() {
      document.addEventListener('pointerdown', A._clashDismissHandler);
    }, 100);

    console.log('§CLASH_REVEAL storey=' + (clashes.length ? 'active' : 'none') + ' showing=' + shown);
    if (A.markDirty) A.markDirty();
  };

  // Toggle status: (none) → Reviewed → Resolved → Accepted → (none)
  A._toggleClashStatus = function(idx) {
    var c = A._currentClashes[idx];
    if (!c) return;
    var pairKey = A._clashPairKey(c[0], c[1]);
    var current = A._clashStatuses[pairKey] || '';
    var cycle = A._clashStatusCycle;
    var nextIdx = (cycle.indexOf(current) + 1) % cycle.length;
    var next = cycle[nextIdx];
    if (next) {
      A._clashStatuses[pairKey] = next;
    } else {
      delete A._clashStatuses[pairKey];
    }
    A._saveClashStatuses();
    // Re-render list
    if (A._clashListDiv && A._renderClashList) {
      A._clashListDiv.innerHTML = A._renderClashList();
    }
    console.log('§CLASH_STATUS guidA=' + c[0] + ' guidB=' + c[1] + ' status=' + (next || 'none'));
  };

  // Export clash report as Excel
  A._exportClashReport = function() {
    if (!A._currentClashes || !A._currentClashes.length) return;
    if (typeof XLSX === 'undefined') { alert('Excel library not loaded'); return; }
    var clashes = A._currentClashes;
    var rules = A._currentClashRules;
    var building = A.activeBuilding || 'Building';
    var date = new Date().toISOString().slice(0, 10);

    // Summary counts
    var statusCounts = { '': 0, 'Reviewed': 0, 'Resolved': 0, 'Accepted': 0 };
    clashes.forEach(function(c) {
      var st = A._clashStatuses[A._clashPairKey(c[0], c[1])] || '';
      statusCounts[st]++;
    });

    // ── Sheet 1: Clash Coordination Template ──
    var rows = [];
    // Title block
    rows.push(['CLASH COORDINATION REPORT']);
    rows.push(['Project:', building, '', 'Date:', date, '', 'Prepared by:', '']);
    rows.push(['Total Clashes:', clashes.length,
               'Reviewed:', statusCounts['Reviewed'],
               'Resolved:', statusCounts['Resolved'],
               'Accepted:', statusCounts['Accepted']]);
    rows.push([]);
    // Column headers — data + assignment template columns
    rows.push([
      '#', 'Element A', 'Class A', 'Disc A',
      'Element B', 'Class B', 'Disc B',
      'Storey', 'Overlap (m)', 'Severity', 'Status',
      'Assigned To', 'Priority', 'Action Required', 'Target Date', 'Date Resolved', 'Remarks'
    ]);

    clashes.forEach(function(c, i) {
      var overlap = (typeof c[8] === 'number') ? c[8] : 0;
      var sev = A._clashSeverity(overlap, rules);
      var pairKey = A._clashPairKey(c[0], c[1]);
      var status = A._clashStatuses[pairKey] || '';
      // Get storey + check for linked schedule task
      var metaA = A.dbQuery("SELECT storey, element_name FROM elements_meta WHERE guid = ?", [c[0]]);
      var storey = metaA.length ? metaA[0][0] : '';
      // Try to find related task from schedule
      var taskInfo = '';
      var taskRows = A.dbQuery(
        "SELECT t.task_name, t.start_date FROM tasks t JOIN task_elements te ON t.task_id = te.task_id WHERE te.guid = ? LIMIT 1",
        [c[0]]
      );
      if (taskRows.length) taskInfo = taskRows[0][0] + (taskRows[0][1] ? ' (' + taskRows[0][1] + ')' : '');
      rows.push([
        i + 1,
        (c[6] || '').replace('Ifc', ''),
        (c[2] || '').replace('Ifc', ''),
        c[4] || '',
        (c[7] || '').replace('Ifc', ''),
        (c[3] || '').replace('Ifc', ''),
        c[5] || '',
        storey,
        parseFloat(overlap.toFixed(3)),
        sev.label,
        status,
        '', // Assigned To — user fills in
        sev.level === 'hard' ? 'HIGH' : sev.level === 'soft' ? 'MEDIUM' : 'LOW',
        '', // Action Required — user fills in
        '', // Target Date — user fills in
        '', // Date Resolved — user fills in
        taskInfo // Remarks — pre-filled with schedule task if found
      ]);
    });

    var ws = XLSX.utils.aoa_to_sheet(rows);
    // Column widths
    ws['!cols'] = [
      { wch: 4 }, { wch: 18 }, { wch: 12 }, { wch: 6 },
      { wch: 18 }, { wch: 12 }, { wch: 6 },
      { wch: 14 }, { wch: 9 }, { wch: 10 }, { wch: 10 },
      { wch: 16 }, { wch: 8 }, { wch: 22 }, { wch: 12 }, { wch: 12 }, { wch: 24 }
    ];

    // ── Sheet 2: Tolerance Settings ──
    var tolRows = [];
    tolRows.push(['CLASH TOLERANCE SETTINGS']);
    tolRows.push(['Source Disc', 'Target Disc', 'Tolerance (mm)', 'Ignored Classes']);
    if (rules && rules.clash_rules) {
      rules.clash_rules.forEach(function(r) {
        tolRows.push([
          r.source.discipline,
          r.target.discipline,
          (r.tolerance_m * 1000).toFixed(0),
          (r.ignore_classes || []).join(', ')
        ]);
      });
    }
    tolRows.push([]);
    tolRows.push(['Severity Levels']);
    tolRows.push(['Level', 'Threshold', 'Color']);
    if (rules && rules.severity) {
      tolRows.push(['Hard clash', '>' + (rules.severity.hard.min_overlap_m * 1000) + 'mm overlap', rules.severity.hard.color]);
      tolRows.push(['Soft clash', '>' + (rules.severity.soft.min_overlap_m * 1000) + 'mm overlap', rules.severity.soft.color]);
      tolRows.push(['Clearance', '<' + (rules.severity.clearance.max_gap_m * 1000) + 'mm gap', rules.severity.clearance.color]);
    }
    var ws2 = XLSX.utils.aoa_to_sheet(tolRows);
    ws2['!cols'] = [{ wch: 14 }, { wch: 14 }, { wch: 14 }, { wch: 40 }];

    // ── Build workbook ──
    var wb = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(wb, ws, 'Clash Report');
    XLSX.utils.book_append_sheet(wb, ws2, 'Tolerance Settings');
    var filename = building.replace(/\s+/g, '_') + '_clashes_' + date + '.xlsx';
    XLSX.writeFile(wb, filename);
    console.log('§CLASH_EXPORT clashes=' + clashes.length +
      ' reviewed=' + statusCounts['Reviewed'] +
      ' resolved=' + statusCounts['Resolved'] +
      ' accepted=' + statusCounts['Accepted'] +
      ' file=' + filename);
    A.status.textContent = 'Exported ' + filename;
  };

  // ── Clash Matrix — visual grid of discipline pair rules ──
  A._clashMatrixDiv = null;

  A._showClashMatrix = function(rules, anchorDiv) {
    // Already showing — do nothing
    if (A._clashMatrixDiv) return;

    // Only show disciplines actually present in this building
    var dbDiscSet = {};
    var dbDiscs = A.dbQuery("SELECT DISTINCT discipline FROM elements_meta WHERE discipline IS NOT NULL AND discipline != ''");
    dbDiscs.forEach(function(r) { dbDiscSet[r[0]] = 1; });
    var discs = Object.keys(dbDiscSet).sort();
    if (discs.length < 2) {
      A.status.textContent = 'Matrix needs 2+ disciplines (found: ' + discs.join(', ') + ')';
      return;
    }

    // Build lookup: "ARC|STR" → rule
    var ruleLookup = {};
    rules.clash_rules.forEach(function(r) {
      var k1 = r.source.discipline + '|' + r.target.discipline;
      var k2 = r.target.discipline + '|' + r.source.discipline;
      ruleLookup[k1] = r;
      ruleLookup[k2] = r;
    });

    var storey = A._currentClashStorey;

    // Inject pulse animation if not already present
    if (!document.getElementById('clash-pulse-style')) {
      var styleEl = document.createElement('style');
      styleEl.id = 'clash-pulse-style';
      styleEl.textContent = '@keyframes clash-pulse{0%,100%{box-shadow:0 0 4px rgba(79,195,247,0.3)}50%{box-shadow:0 0 12px rgba(79,195,247,0.9)}}';
      document.head.appendChild(styleEl);
    }

    // CSS sphere helper (larger, 20px)
    var _msphere = function(color, pulse) {
      var light = color === '#4caf50' ? '#8fef8f' : color === '#ff0000' ? '#ff8888' : color === '#ff2090' ? '#ff88cc' : color === '#ff8c00' ? '#ffc966' : '#ccc';
      var anim = pulse ? 'animation:clash-pulse 1.2s ease-in-out infinite;' : '';
      return '<span style="display:inline-block;width:20px;height:20px;border-radius:50%;' +
        'background:radial-gradient(circle at 35% 35%,' + light + ',' + color + ' 60%,#111);' +
        'box-shadow:0 1px 3px rgba(0,0,0,0.4);' + anim + '"></span>';
    };

    // Cheap check: element count per discipline (no join, instant)
    var discCounts = {};
    var dcRows = A.dbQuery("SELECT discipline, COUNT(*) FROM elements_meta WHERE discipline IS NOT NULL GROUP BY discipline");
    dcRows.forEach(function(r) { discCounts[r[0]] = r[1]; });

    // Build table — pulsing sphere = pending check, green = clear
    var cellSz = 36;
    var activePairs = [];
    var html = '<table style="border-collapse:collapse">';
    html += '<tr><td></td>';
    discs.forEach(function(d) {
      html += '<td style="padding:2px 4px;font-size:10px;font-weight:bold;text-align:center;color:#fff">' + d + '</td>';
    });
    html += '</tr>';
    discs.forEach(function(rowDisc) {
      html += '<tr>';
      html += '<td style="padding:2px 4px;font-size:10px;font-weight:bold;color:#fff;text-align:right">' + rowDisc + '</td>';
      discs.forEach(function(colDisc) {
        if (rowDisc === colDisc) {
          html += '<td style="width:' + cellSz + 'px;height:' + cellSz + 'px;text-align:center;background:rgba(0,0,0,0.15)"></td>';
          return;
        }
        var key = rowDisc + '|' + colDisc;
        var rule = ruleLookup[key];
        var cellContent = '';
        if (!rule) {
          cellContent = '<span style="color:rgba(255,255,255,0.15);font-size:9px">—</span>';
        } else if (!discCounts[rowDisc] || !discCounts[colDisc]) {
          cellContent = _msphere('#4caf50');
        } else {
          // Both disciplines present — pulsing, will be checked async
          cellContent = _msphere('#ccc', true);
          activePairs.push({ discA: rowDisc, discB: colDisc, key: key });
        }
        html += '<td data-pair="' + key + '" style="width:' + cellSz + 'px;height:' + cellSz + 'px;text-align:center;cursor:pointer;border:1px solid rgba(255,255,255,0.08)">' + cellContent + '</td>';
      });
      html += '</tr>';
    });
    html += '</table>';

    var matDiv = document.createElement('div');
    matDiv.style.cssText = 'position:fixed;z-index:350;background:rgba(20,60,100,0.65);backdrop-filter:blur(10px);-webkit-backdrop-filter:blur(10px);color:#fff;padding:10px;border-radius:8px;border:1px solid rgba(79,195,247,0.5);font-family:Segoe UI,sans-serif;pointer-events:auto';

    // Position: below the info card, aligned right
    var anchorRect = anchorDiv.getBoundingClientRect();
    matDiv.style.right = '10px';
    matDiv.innerHTML = '<div style="display:flex;justify-content:space-between;align-items:center">' +
      '<b style="color:#4fc3f7;font-size:12px">Clash Matrix</b>' +
      '<span style="display:flex;gap:8px;align-items:center">' +
      '<span id="clash-matrix-export" style="cursor:pointer;font-size:16px" title="Export">📊</span>' +
      '<span id="clash-matrix-close" style="cursor:pointer;color:#aaa;font-size:16px;line-height:1">\u2715</span></span></div>' +
      '<hr style="border:none;border-top:1px solid rgba(255,255,255,0.15);margin:4px 0">' + html;
    document.body.appendChild(matDiv);
    // Adjust vertical to fit
    var matH = matDiv.offsetHeight;
    var topPos = anchorRect.bottom + 6;
    if (topPos + matH > window.innerHeight - 10) topPos = window.innerHeight - matH - 10;
    if (topPos < 10) topPos = 10;
    matDiv.style.top = topPos + 'px';

    A._clashMatrixDiv = matDiv;
    A._makeDraggable(matDiv);
    A.measureLabels.push({ div: matDiv, mid: null });

    // Export from matrix — queries all pairs and exports
    matDiv.querySelector('#clash-matrix-export').addEventListener('click', function(ev) {
      ev.stopPropagation();
      // Collect all clashes from all pairs
      var allClashes = [];
      var storey = A._currentClashStorey;
      rules.clash_rules.forEach(function(r) {
        var offset = 0;
        while (true) {
          var batch = A._queryClashesPair(storey, rules, r.source.discipline, r.target.discipline, offset);
          if (!batch.length) break;
          allClashes = allClashes.concat(batch);
          offset += A._CLASH_PAGE_SIZE;
          if (batch.length < A._CLASH_PAGE_SIZE) break;
        }
      });
      A._currentClashes = allClashes;
      A._exportClashReport();
    });

    // Close X for matrix
    matDiv.querySelector('#clash-matrix-close').addEventListener('click', function(ev) {
      ev.stopPropagation();
      if (A._clashRevealActive) A._dismissClashes();
      A._clashMatrixDiv.remove();
      A._clashMatrixDiv = null;
    });

    // Click cell → open filtered clash list for that pair
    matDiv.addEventListener('click', function(ev) {
      if (ev.target.id === 'clash-matrix-close') return;
      var cell = ev.target.closest('[data-pair]');
      if (!cell) return;
      ev.stopPropagation();
      var pair = cell.getAttribute('data-pair');
      var parts = pair.split('|');
      var discA = parts[0], discB = parts[1];
      var rule = ruleLookup[pair];
      if (!rule) return;
      // Dismiss previous list if any
      if (A._clashRevealActive) A._dismissClashes();
      // Query this pair with LIMIT 30 — the only place real work happens
      var storey = A._currentClashStorey;
      var offset = A._clashPairOffset || 0;
      var clashes = A._queryClashesPair(storey, rules, discA, discB, offset);
      if (!clashes.length && offset > 0) {
        A._clashPairOffset = 0;
        // Update cell to green — no more clashes
        cell.innerHTML = _msphere('#4caf50');
        return;
      }
      // Update cell sphere based on results
      if (clashes.length === 0) {
        cell.innerHTML = _msphere('#4caf50');
      } else {
        var hasHard = clashes.some(function(c) {
          return (typeof c[8] === 'number') && c[8] >= rules.severity.hard.min_overlap_m;
        });
        cell.innerHTML = hasHard ? _msphere('#ff0000') : _msphere('#ff8c00');
      }
      A._clashPairOffset = offset + A._CLASH_PAGE_SIZE;
      A._currentClashes = clashes;
      var rect = matDiv.getBoundingClientRect();
      A._revealClashes(clashes, rules, rect.left, rect.top, discA + ' vs ' + discB, rule);
      console.log('§CLASH_MATRIX_FILTER ' + discA + ' vs ' + discB + ' page=' + (offset / A._CLASH_PAGE_SIZE + 1));
    });

    // Background check — sample 50 elements per side, check bbox overlap
    // Fast approximation: orange = sampled clash found, green = none in sample
    var _qi = 0;
    var checked = {};
    var w = A._clashWhereParts(rules);
    var storeyClause = storey ? "ma.storey = '" + storey.replace(/'/g, "''") + "' AND mb.storey = ma.storey" : '1=1';
    function _bgCheck() {
      if (_qi >= activePairs.length) return;
      if (!A._clashMatrixDiv) return;
      var p = activePairs[_qi++];
      var sortedKey = [p.discA, p.discB].sort().join('|');
      if (checked[sortedKey]) {
        var cell = matDiv.querySelector('[data-pair="' + p.key + '"]');
        if (cell) cell.innerHTML = checked[sortedKey];
        setTimeout(_bgCheck, 0);
        return;
      }
      // Sample: pick 50 random from each side, check bbox overlap
      var pairCond = "(ma.discipline = '" + p.discA + "' AND mb.discipline = '" + p.discB + "')";
      var sql = "SELECT 1 FROM" +
        " (SELECT guid, center_x, center_y, center_z, bbox_x, bbox_y, bbox_z FROM element_transforms WHERE guid IN" +
        "   (SELECT guid FROM elements_meta WHERE discipline = '" + p.discA + "'" +
        (storey ? " AND storey = '" + storey.replace(/'/g, "''") + "'" : "") +
        "    ORDER BY RANDOM() LIMIT 50)) a," +
        " (SELECT guid, center_x, center_y, center_z, bbox_x, bbox_y, bbox_z FROM element_transforms WHERE guid IN" +
        "   (SELECT guid FROM elements_meta WHERE discipline = '" + p.discB + "'" +
        (storey ? " AND storey = '" + storey.replace(/'/g, "''") + "'" : "") +
        "    ORDER BY RANDOM() LIMIT 50)) b" +
        " WHERE (a.center_x - a.bbox_x/2) < (b.center_x + b.bbox_x/2)" +
        " AND (a.center_x + a.bbox_x/2) > (b.center_x - b.bbox_x/2)" +
        " AND (a.center_y - a.bbox_y/2) < (b.center_y + b.bbox_y/2)" +
        " AND (a.center_y + a.bbox_y/2) > (b.center_y - b.bbox_y/2)" +
        " AND (a.center_z - a.bbox_z/2) < (b.center_z + b.bbox_z/2)" +
        " AND (a.center_z + a.bbox_z/2) > (b.center_z - b.bbox_z/2)" +
        " LIMIT 1";
      var rows = A.dbQuery(sql);
      var sphere = rows.length > 0 ? _msphere('#ff8c00') : _msphere('#4caf50');
      checked[sortedKey] = sphere;
      // Update both directions
      var cell1 = matDiv.querySelector('[data-pair="' + p.discA + '|' + p.discB + '"]');
      var cell2 = matDiv.querySelector('[data-pair="' + p.discB + '|' + p.discA + '"]');
      if (cell1) cell1.innerHTML = sphere;
      if (cell2) cell2.innerHTML = sphere;
      setTimeout(_bgCheck, 20);
    }
    setTimeout(_bgCheck, 100);

    console.log('§CLASH_MATRIX shown discs=' + discs.join(','));
  };

  A._dismissClashes = function() {
    if (!A._clashRevealActive) return;
    // Restore materials
    A._clashBackups.forEach(function(b) { b.mesh.material = b.origMat; });
    A._clashBackups = [];
    A._clashRevealActive = false;
    // Remove list div
    if (A._clashListDiv) {
      A._clashListDiv.remove();
      A._clashListDiv = null;
    }
    if (A._clashMatrixDiv) {
      A._clashMatrixDiv.remove();
      A._clashMatrixDiv = null;
    }
    if (A._clashDismissHandler) {
      document.removeEventListener('pointerdown', A._clashDismissHandler);
      A._clashDismissHandler = null;
    }
    if (A.markDirty) A.markDirty();
    console.log('§CLASH_DISMISS');
  };

  A.toggleMeasure = function() {
    A.measureActive = !A.measureActive;
    const btn = document.getElementById('measure-btn');
    btn.style.background = A.measureActive ? '#4fc3f7' : '#444';
    btn.style.color = A.measureActive ? '#000' : '#fff';
    var isMobile = 'ontouchstart' in window || navigator.maxTouchPoints > 0;
    A.status.textContent = A.measureActive
      ? (isMobile ? 'Tap for dimensions. Long-press for Info' : 'Click for dimensions. Right-click for Info')
      : '';
    if (!A.measureActive) {
      A.clearMeasures();
    }
    console.log(`§MEASURE mode ${A.measureActive ? 'ON' : 'OFF'}`);
  };

  A.clearMeasures = function() {
    while (A.measureGroup.children.length) {
      A.measureGroup.remove(A.measureGroup.children[0]);
    }
    A.measureLabels.forEach(m => m.div.remove());
    A.measureLabels = [];
    A.measureFirstPoint = null;
    A.measureFirstMarker = null;
    // Restore any orange-highlighted meshes
    if (A._areaBackups) {
      A._areaBackups.forEach(b => { b.mesh.material = b.origMat; });
      A._areaBackups = [];
    }
    // Dismiss clash reveal if active
    A._dismissClashes();
    A._guidToMesh = null; // invalidate cache
    console.log('§MEASURE cleared all');
  };

  A._measureClickTimer = null;
  A.handleMeasureClick = function(e) {
    if (!A.measureActive) return false;
    // Debounce: wait 250ms to see if double-click follows
    if (A._measureClickTimer) { clearTimeout(A._measureClickTimer); A._measureClickTimer = null; }
    var ev = { clientX: e.clientX, clientY: e.clientY };
    A._measureClickTimer = setTimeout(function() { A._doMeasureClick(ev); }, 250);
    return true;
  };

  A._doMeasureClick = function(e) {
    A.mouse.x = (e.clientX / window.innerWidth) * 2 - 1;
    A.mouse.y = -(e.clientY / window.innerHeight) * 2 + 1;
    A.raycaster.setFromCamera(A.mouse, A.camera);

    const meshes = [];
    A.scene.traverse(obj => { if (obj.isMesh && obj !== A.ground && obj.visible) meshes.push(obj); });
    const hits = A.raycaster.intersectObjects(meshes, false);
    if (!hits.length) return true;

    const point = hits[0].point.clone();
    const hitMesh = hits[0].object;

    if (!A.measureFirstPoint) {
      A.measureFirstPoint = point;
      A._measureFirstMesh = hitMesh;
      const markerGeo = new THREE.SphereGeometry(0.15, 8, 8);
      const markerMat = new THREE.MeshBasicMaterial({ color: 0x4fc3f7 });
      A.measureFirstMarker = new THREE.Mesh(markerGeo, markerMat);
      A.measureFirstMarker.position.copy(point);
      A.measureGroup.add(A.measureFirstMarker);
      A.status.textContent = 'Tap another spot for length, same spot for Area';
      console.log('§MEASURE dot placed — tap same dot for area, or tap elsewhere for distance');
    } else if (point.distanceTo(A.measureFirstPoint) < 0.5) {
      // Second tap on same spot → area of that element
      var mesh = A._measureFirstMesh;
      var area = A._meshArea(mesh);
      var label = area.toFixed(2) + ' m²';
      var cls = mesh.userData.ifcClass || '';
      if (cls) label = cls.replace('Ifc', '') + ': ' + label;
      A._highlightMesh(mesh, null, 0xff8c00);
      // Fixed-position label at click point
      var labelDiv = document.createElement('div');
      labelDiv.className = 'measure-label';
      labelDiv.style.cssText = 'position:fixed;z-index:100;background:rgba(20,60,100,0.55);backdrop-filter:blur(8px);-webkit-backdrop-filter:blur(8px);color:#cc6600;font-size:14px;font-weight:bold;padding:6px 12px;border-radius:6px;border:1px solid rgba(255,140,0,0.6);pointer-events:none;white-space:nowrap;font-family:Segoe UI,sans-serif';
      labelDiv.textContent = label;
      labelDiv.style.left = Math.min(e.clientX + 10, window.innerWidth - 200) + 'px';
      labelDiv.style.top = Math.min(Math.max(e.clientY - 30, 10), window.innerHeight - 50) + 'px';
      document.body.appendChild(labelDiv);
      A.measureLabels.push({ div: labelDiv, mid: null });
      A.status.textContent = label;
      console.log('§MEASURE_AREA ' + label + ' mesh=' + (mesh.userData.guid || mesh.id));
      // Remove the first-point marker
      if (A.measureFirstMarker) A.measureGroup.remove(A.measureFirstMarker);
      A.measureFirstPoint = null;
      A.measureFirstMarker = null;
      A._measureFirstMesh = null;
    } else {
      const p1 = A.measureFirstPoint;
      const p2 = point;
      const dist = p1.distanceTo(p2).toFixed(2) + 'm';

      const markerGeo2 = new THREE.SphereGeometry(0.15, 8, 8);
      const markerMat2 = new THREE.MeshBasicMaterial({ color: 0x4fc3f7 });
      const marker2 = new THREE.Mesh(markerGeo2, markerMat2);
      marker2.position.copy(p2);
      A.measureGroup.add(marker2);

      const lineGeo = new THREE.BufferGeometry().setFromPoints([p1, p2]);
      const lineMat = new THREE.LineDashedMaterial({
        color: 0x4fc3f7, dashSize: 0.3, gapSize: 0.15, linewidth: 1
      });
      const line = new THREE.Line(lineGeo, lineMat);
      line.computeLineDistances();
      A.measureGroup.add(line);

      const labelDiv = document.createElement('div');
      labelDiv.className = 'measure-label';
      labelDiv.textContent = dist;
      document.body.appendChild(labelDiv);

      const mid = new THREE.Vector3().addVectors(p1, p2).multiplyScalar(0.5);
      A.measureLabels.push({ div: labelDiv, p1: p1.clone(), p2: p2.clone(), mid: mid });

      console.log(`§MEASURE ${dist} from (${p1.x.toFixed(1)},${p1.y.toFixed(1)},${p1.z.toFixed(1)}) to (${p2.x.toFixed(1)},${p2.y.toFixed(1)},${p2.z.toFixed(1)})`);

      A.measureFirstPoint = null;
      A.measureFirstMarker = null;
      A._measureFirstMesh = null;
    }
    return true;
  };

  // ── Area from mesh geometry (world-space, cached by geometry UUID) ──
  A._areaCache = {};
  A._meshArea = function(mesh) {
    var geo = mesh.geometry;
    if (!geo) return 0;
    var cacheKey = geo.uuid;
    if (A._areaCache[cacheKey] !== undefined) {
      console.log('§MEASURE_AREA cache hit geo=' + cacheKey);
      return A._areaCache[cacheKey];
    }
    var pos = geo.attributes.position;
    if (!pos) return 0;
    var idx = geo.index;
    // Transform vertices to world space via mesh.matrixWorld
    mesh.updateMatrixWorld(true);
    var mat = mesh.matrixWorld;
    var a = new THREE.Vector3(), b = new THREE.Vector3(), c = new THREE.Vector3();
    var ab = new THREE.Vector3(), ac = new THREE.Vector3();
    var area = 0;
    if (idx) {
      for (var i = 0; i < idx.count; i += 3) {
        a.fromBufferAttribute(pos, idx.getX(i)).applyMatrix4(mat);
        b.fromBufferAttribute(pos, idx.getX(i + 1)).applyMatrix4(mat);
        c.fromBufferAttribute(pos, idx.getX(i + 2)).applyMatrix4(mat);
        ab.subVectors(b, a);
        ac.subVectors(c, a);
        area += ab.cross(ac).length() * 0.5;
      }
    } else {
      for (var i = 0; i < pos.count; i += 3) {
        a.fromBufferAttribute(pos, i).applyMatrix4(mat);
        b.fromBufferAttribute(pos, i + 1).applyMatrix4(mat);
        c.fromBufferAttribute(pos, i + 2).applyMatrix4(mat);
        ab.subVectors(b, a);
        ac.subVectors(c, a);
        area += ab.cross(ac).length() * 0.5;
      }
    }
    A._areaCache[cacheKey] = area;
    return area;
  };

  // ── Volume from bounding box (practical for rooms — covers openings) ──
  A._meshVolume = function(mesh) {
    var box = new THREE.Box3().setFromObject(mesh);
    var size = new THREE.Vector3();
    box.getSize(size);
    return size.x * size.y * size.z;
  };

  // ── Highlight mesh and show label ──
  A._areaBackups = [];
  A._highlightMesh = function(mesh, text, color) {
    A._areaBackups.push({ mesh: mesh, origMat: mesh.material });
    var newMat = mesh.material.clone();
    newMat.color.set(color);
    newMat.transparent = true;
    newMat.opacity = 0.6;
    newMat.needsUpdate = true;
    mesh.material = newMat;
    if (!text) return;
    var box = new THREE.Box3().setFromObject(mesh);
    var center = new THREE.Vector3();
    box.getCenter(center);
    var labelDiv = document.createElement('div');
    labelDiv.className = 'measure-label';
    labelDiv.style.cssText = 'position:fixed;z-index:100;background:rgba(20,60,100,0.55);backdrop-filter:blur(8px);-webkit-backdrop-filter:blur(8px);color:#cc6600;font-size:14px;font-weight:bold;padding:6px 12px;border-radius:6px;border:1px solid rgba(255,140,0,0.6);pointer-events:none;white-space:nowrap;font-family:Segoe UI,sans-serif';
    labelDiv.textContent = text;
    document.body.appendChild(labelDiv);
    A.measureLabels.push({ div: labelDiv, mid: center, p1: center, p2: center });
  };

  // ── Double-click: area of hit element → orange highlight ──
  A.handleMeasureDblClick = function(e) {
    if (!A.measureActive) return;
    // Cancel pending single-click
    if (A._measureClickTimer) { clearTimeout(A._measureClickTimer); A._measureClickTimer = null; }
    A.mouse.x = (e.clientX / window.innerWidth) * 2 - 1;
    A.mouse.y = -(e.clientY / window.innerHeight) * 2 + 1;
    A.raycaster.setFromCamera(A.mouse, A.camera);
    var meshes = [];
    A.scene.traverse(function(obj) { if (obj.isMesh && obj !== A.ground && obj.visible) meshes.push(obj); });
    var hits = A.raycaster.intersectObjects(meshes, false);
    if (!hits.length) return;
    var mesh = hits[0].object;
    var area = A._meshArea(mesh);
    var label = area.toFixed(2) + ' m²';
    var cls = mesh.userData.ifcClass || '';
    if (cls) label = cls.replace('Ifc', '') + ': ' + label;
    A._highlightMesh(mesh, label, 0xff8c00);
    A.status.textContent = label;
    console.log('§MEASURE_AREA ' + label + ' mesh=' + (mesh.userData.guid || mesh.id));
  };

  // ── Right-click: bounding box wireframe + info card ──
  A.handleMeasureRightClick = function(e) {
    if (!A.measureActive) return false;
    e.preventDefault();
    A.mouse.x = (e.clientX / window.innerWidth) * 2 - 1;
    A.mouse.y = -(e.clientY / window.innerHeight) * 2 + 1;
    A.raycaster.setFromCamera(A.mouse, A.camera);
    var meshes = [];
    A.scene.traverse(function(obj) { if (obj.isMesh && obj !== A.ground && obj.visible) meshes.push(obj); });
    var hits = A.raycaster.intersectObjects(meshes, false);
    if (!hits.length) return false;
    var hitMesh = hits[0].object;
    var storey = hitMesh.userData.storey;
    if (storey === undefined || storey === null) storey = 'Unknown';
    var storeyLabel = storey || 'All Elements';
    // Collect storey meshes for bounding box, and query DB for accurate class counts
    var roomMeshes = [];
    A.scene.traverse(function(obj) {
      if (obj.isMesh && obj !== A.ground && obj.visible && obj.userData.storey === storey) {
        roomMeshes.push(obj);
      }
    });
    var counts = {};
    var dbRows = A.dbQuery(
      "SELECT REPLACE(m.ifc_class,'Ifc','') AS cls, COUNT(*) AS n FROM elements_meta m JOIN element_transforms t ON m.guid = t.guid WHERE m.storey = ? AND m.ifc_class != 'IfcOpeningElement' GROUP BY cls ORDER BY n DESC",
      [storey]
    );
    var totalFromDb = 0;
    dbRows.forEach(function(r) { counts[r[0] || 'Other'] = r[1]; totalFromDb += r[1]; });
    // Bounding box of storey
    var roomBox = new THREE.Box3();
    roomMeshes.forEach(function(m) { roomBox.expandByObject(m); });
    var size = new THREE.Vector3();
    roomBox.getSize(size);
    var volume = size.x * size.y * size.z;
    var floorArea = size.x * size.z;
    // Draw wireframe bounding box (orange)
    var boxHelper = new THREE.Box3Helper(roomBox, 0xff8c00);
    A.measureGroup.add(boxHelper);
    // Build info card
    var lines = [];
    lines.push('<div style="display:flex;justify-content:space-between;align-items:center">' +
      '<b style="color:#4fc3f7;font-size:15px">' + storeyLabel + '</b>' +
      '<span class="clash-card-close" style="cursor:pointer;color:#aaa;font-size:16px;line-height:1">\u2715</span></div>');
    lines.push('<hr style="border:none;border-top:1px solid #555;margin:4px 0">');
    lines.push('Vol: <b style="color:#ff8c00">' + volume.toFixed(1) + ' m\u00B3</b>');
    lines.push('Floor: <b>' + floorArea.toFixed(1) + ' m\u00B2</b> &nbsp; H: <b>' + size.y.toFixed(1) + 'm</b>');
    lines.push('<hr style="border:none;border-top:1px solid #555;margin:4px 0">');
    var sorted = Object.keys(counts).sort(function(a, b) { return counts[b] - counts[a]; });
    sorted.forEach(function(cls) {
      lines.push(cls + ': <b style="color:#4fc3f7">' + counts[cls] + '</b>');
    });
    lines.push('<hr style="border:none;border-top:1px solid #555;margin:4px 0">');
    lines.push('<span style="color:#888;font-size:10px">Total: ' + totalFromDb + ' elements</span>');
    // Clash count — async load rules then query
    var clashPlaceholder = '<span id="clash-count-line" style="color:#888;font-size:10px">Checking clashes...</span>';
    lines.push('<hr style="border:none;border-top:1px solid #555;margin:4px 0">');
    lines.push(clashPlaceholder);
    var cardDiv = document.createElement('div');
    cardDiv.style.cssText = 'position:fixed;z-index:200;background:rgba(20,60,100,0.55);backdrop-filter:blur(8px);-webkit-backdrop-filter:blur(8px);color:#fff;font-size:12px;padding:10px 14px;border-radius:8px;border:1px solid rgba(79,195,247,0.6);pointer-events:auto;font-family:Segoe UI,sans-serif;line-height:1.6;min-width:180px';
    // Position at click location, then adjust to fit in viewport
    cardDiv.innerHTML = lines.join('<br>');
    document.body.appendChild(cardDiv);
    var cx = Math.min(e.clientX + 10, window.innerWidth - 220);
    var cy = Math.max(e.clientY - 100, 10);
    var cardH = cardDiv.offsetHeight;
    if (cy + cardH > window.innerHeight - 10) cy = window.innerHeight - cardH - 10;
    if (cy < 10) cy = 10;
    cardDiv.style.left = cx + 'px';
    cardDiv.style.top = cy + 'px';
    // Store for cleanup — no 3D tracking needed, fixed position
    A.measureLabels.push({ div: cardDiv, mid: null });
    A._makeDraggable(cardDiv);
    // Close X
    var closeBtn = cardDiv.querySelector('.clash-card-close');
    if (closeBtn) closeBtn.addEventListener('click', function() {
      cardDiv.remove();
      var idx = A.measureLabels.findIndex(function(m) { return m.div === cardDiv; });
      if (idx >= 0) A.measureLabels.splice(idx, 1);
    });
    A.status.textContent = storeyLabel + ' — ' + volume.toFixed(1) + ' m\u00B3';
    console.log('§MEASURE_VOLUME ' + storeyLabel + ' vol=' + volume.toFixed(1) + 'm\u00B3 elements=' + roomMeshes.length + ' counts=' + JSON.stringify(counts));

    // Clash indicator — lazy LIMIT 1 per pair, async, stops at first hit
    A._loadClashRules(function(rules) {
      var clashEl = cardDiv.querySelector('#clash-count-line');
      if (!clashEl) return;
      A._currentClashRules = rules;
      A._currentClashStorey = storey;
      A._currentClashes = [];
      A._clashPairOffset = 0;

      var _sphere = function(color, size) {
        var s = size || 14;
        var light = color === '#4caf50' ? '#8fef8f' : color === '#ff0000' ? '#ff8888' : color === '#ff2090' ? '#ff88cc' : '#ffc966';
        return '<span style="display:inline-block;width:' + s + 'px;height:' + s + 'px;border-radius:50%;' +
          'background:radial-gradient(circle at 35% 35%,' + light + ',' + color + ' 60%,#111);' +
          'vertical-align:middle;margin-right:4px;box-shadow:0 1px 3px rgba(0,0,0,0.4)"></span>';
      };

      // Show placeholder sphere while checking
      var _updateSphere = function(color) {
        clashEl.innerHTML = '<span id="clash-tap-trigger" style="cursor:pointer">' +
          'CLASHES ' + _sphere(color, 18) + '</span>';
        var trigger = cardDiv.querySelector('#clash-tap-trigger');
        if (trigger) trigger.addEventListener('click', function() {
          A._showClashMatrix(rules, cardDiv);
        });
      };
      _updateSphere('#aaa'); // grey while checking

      // Check discipline counts first (instant)
      var discCounts = {};
      var dcRows = A.dbQuery("SELECT discipline, COUNT(*) FROM elements_meta WHERE discipline IS NOT NULL GROUP BY discipline");
      dcRows.forEach(function(r) { discCounts[r[0]] = r[1]; });

      // Filter rules to those with both sides present
      var activeRules = rules.clash_rules.filter(function(r) {
        return discCounts[r.source.discipline] && discCounts[r.target.discipline];
      });

      if (!activeRules.length) {
        _updateSphere('#4caf50'); // green — no applicable rules
        return;
      }

      // Async: check one rule at a time, LIMIT 1, stop at first hit
      var ri = 0;
      var w = A._clashWhereParts(rules);
      var storeyClause = storey ? "ma.storey = '" + storey.replace(/'/g, "''") + "' AND mb.storey = ma.storey" : '1=1';

      function _checkNext() {
        if (ri >= activeRules.length) {
          _updateSphere('#4caf50'); // all checked, no clashes
          return;
        }
        if (!cardDiv.parentNode) return; // card was closed
        var r = activeRules[ri++];
        var pairCond = "(ma.discipline = '" + r.source.discipline + "' AND mb.discipline = '" + r.target.discipline + "')" +
          " OR (ma.discipline = '" + r.target.discipline + "' AND mb.discipline = '" + r.source.discipline + "')";
        var sql = "SELECT 1 FROM element_transforms a JOIN elements_meta ma ON a.guid = ma.guid" +
          " JOIN element_transforms b ON a.guid < b.guid JOIN elements_meta mb ON b.guid = mb.guid" +
          " WHERE " + storeyClause + " AND (" + pairCond + ")" + w.ignoreClause + w.bboxJoin + " LIMIT 1";
        var rows = A.dbQuery(sql);
        if (rows.length > 0) {
          _updateSphere('#ff8c00'); // found clash — orange, stop checking
          console.log('§CLASH_QUICK hit ' + r.source.discipline + ' vs ' + r.target.discipline);
          return;
        }
        setTimeout(_checkNext, 10); // yield then check next pair
      }
      setTimeout(_checkNext, 50);
    });

    return true;
  };

  A.updateMeasureLabels = function() {
    if (!A.measureActive && !A.measureLabels.length && !A.measureGroup.children.length) return;
    A.measureLabels.forEach(m => {
      if (!m.mid) return;  // fixed-position labels (info cards)
      const projected = m.mid.clone().project(A.camera);
      if (projected.z > 1) {
        m.div.style.display = 'none';
        return;
      }
      m.div.style.display = '';
      const x = (projected.x * 0.5 + 0.5) * window.innerWidth;
      const y = (-projected.y * 0.5 + 0.5) * window.innerHeight;
      m.div.style.left = x + 'px';
      m.div.style.top = y + 'px';
    });
    // Constant-size marker dots — scale based on camera distance
    if (A.measureGroup.children.length) {
      A.measureGroup.children.forEach(function(child) {
        if (child.isMesh && child.geometry && child.geometry.type === 'SphereGeometry') {
          var dist = A.camera.position.distanceTo(child.position);
          var s = Math.max(dist * 0.05, 0.15);
          child.scale.setScalar(s);
        }
      });
    }
  };
}
