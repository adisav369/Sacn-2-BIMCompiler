// diff.js — S222 Incremental Diff: GUID set diff, change_log, colour overlay
// Loaded by viewer index.html after streaming.js

function setupDiff(A) {
  const DIFF_COLORS = {
    added:   { color: 0x44cc44, opacity: 1.0 },   // green solid
    removed: { color: 0xcc4444, opacity: 0.5 },   // red ghost
    changed: { color: 0xcccc44, opacity: 1.0 },   // yellow solid
  };

  // Called after base DB loaded, if variation DB exists
  // A.db = base, A.diffDb = variation (both sql.js instances)
  A.computeDiff = function() {
    if (!A.db || !A.diffDb) return null;

    const guids1 = new Set(A.db.exec("SELECT guid FROM elements_meta")[0].values.map(r => r[0]));
    const guids2 = new Set(A.diffDb.exec("SELECT guid FROM elements_meta")[0].values.map(r => r[0]));

    const added = [...guids2].filter(g => !guids1.has(g));
    const removed = [...guids1].filter(g => !guids2.has(g));
    const changed = [...guids2].filter(g => guids1.has(g)).filter(g => {
      // Compare key properties — element_name, material_rgba, storey
      const r1 = A.db.exec("SELECT element_name, material_rgba, storey FROM elements_meta WHERE guid='" + g.replace(/'/g, "''") + "'");
      const r2 = A.diffDb.exec("SELECT element_name, material_rgba, storey FROM elements_meta WHERE guid='" + g.replace(/'/g, "''") + "'");
      return JSON.stringify(r1[0]?.values[0]) !== JSON.stringify(r2[0]?.values[0]);
    });

    A.diffResult = { added, removed, changed };
    console.log('[S222] §DIFF added=' + added.length + ' removed=' + removed.length + ' changed=' + changed.length);
    return A.diffResult;
  };

  // Apply diff colours to already-streamed meshes
  A.applyDiffOverlay = function() {
    if (!A.diffResult) return;

    const removedSet = new Set(A.diffResult.removed);
    const changedSet = new Set(A.diffResult.changed);

    A.scene.traverse(function(obj) {
      if (!obj.isMesh || !obj.userData.guid) return;
      const guid = obj.userData.guid;

      if (removedSet.has(guid)) {
        obj.material = new THREE.MeshPhongMaterial({
          color: DIFF_COLORS.removed.color,
          transparent: true,
          opacity: DIFF_COLORS.removed.opacity,
          side: THREE.DoubleSide,
          flatShading: true,
        });
      } else if (changedSet.has(guid)) {
        obj.material = new THREE.MeshPhongMaterial({
          color: DIFF_COLORS.changed.color,
          transparent: false,
          opacity: DIFF_COLORS.changed.opacity,
          flatShading: true,
        });
      }
    });

    console.log('[S222] §DIFF_OVERLAY applied — removed=' + removedSet.size + ' changed=' + changedSet.size);
  };

  // Get diff details for a specific GUID (for info panel)
  A.getDiffDetail = function(guid) {
    if (!A.diffResult) return null;
    if (A.diffResult.added.includes(guid)) { console.log('[S222] §DIFF_DETAIL guid=' + guid.substring(0,12) + ' status=ADDED'); return { status: 'ADDED', color: '#44cc44' }; }
    if (A.diffResult.removed.includes(guid)) { console.log('[S222] §DIFF_DETAIL guid=' + guid.substring(0,12) + ' status=REMOVED'); return { status: 'REMOVED', color: '#cc4444' }; }
    if (A.diffResult.changed.includes(guid)) {
      // Show what changed
      const r1 = A.db.exec("SELECT element_name, material_rgba, storey FROM elements_meta WHERE guid='" + guid.replace(/'/g, "''") + "'");
      const r2 = A.diffDb.exec("SELECT element_name, material_rgba, storey FROM elements_meta WHERE guid='" + guid.replace(/'/g, "''") + "'");
      return {
        status: 'CHANGED', color: '#cccc44',
        old: r1[0]?.values[0],
        new: r2[0]?.values[0],
      };
    }
    return null;
  };

  // Show diff summary panel
  A.showDiffSummary = function() {
    if (!A.diffResult) return;
    let panel = document.getElementById('diff-summary');
    if (!panel) {
      panel = document.createElement('div');
      panel.id = 'diff-summary';
      panel.style.cssText = 'position:fixed;top:60px;left:16px;z-index:10;background:rgba(0,0,0,0.85);border-radius:8px;padding:12px 16px;border:1px solid rgba(255,255,255,0.1);backdrop-filter:blur(8px);font-size:12px;color:#ccc;min-width:200px';
      document.body.appendChild(panel);
    }
    const d = A.diffResult;

    // Quick cost preview using VO rates (if variation_order.js loaded)
    var costHtml = '';
    if (typeof VO_RATES !== 'undefined' && typeof VO_CONFIG !== 'undefined') {
      var addCost = 0, remCost = 0, chgCost = 0;
      var C = VO_CONFIG;
      function _qRate(db, guid) {
        try {
          var r = db.exec("SELECT ifc_class FROM elements_meta WHERE guid='" + guid.replace(/'/g, "''") + "'");
          var cls = r[0]?.values[0]?.[0] || '';
          return VO_RATES[cls] || VO_RATES._default;
        } catch(e) { return VO_RATES._default; }
      }
      for (var i = 0; i < d.added.length; i++) addCost += _qRate(A.diffDb, d.added[i]) * C.addFactor;
      for (var i = 0; i < d.removed.length; i++) remCost += _qRate(A.db, d.removed[i]) * C.removeFactor;
      for (var i = 0; i < d.changed.length; i++) chgCost += _qRate(A.diffDb, d.changed[i]) * C.changeFactor;
      var totalDirect = addCost + remCost + chgCost;
      var totalImpact = totalDirect * (1 + C.overheadPct + C.markupPct) * (1 + C.disruptionPct);
      costHtml =
        '<div style="margin-top:8px;border-top:1px solid #333;padding-top:6px">' +
        '<div style="color:#4fc3f7;font-weight:bold;font-size:11px">Cost Impact (5D)</div>' +
        '<div style="color:#44cc44;font-size:11px">+ ' + C.currency + ' ' + Math.round(addCost).toLocaleString() + '</div>' +
        '<div style="color:#cc4444;font-size:11px">\u2212 ' + C.currency + ' ' + Math.round(remCost).toLocaleString() + '</div>' +
        '<div style="color:#cccc44;font-size:11px">~ ' + C.currency + ' ' + Math.round(chgCost).toLocaleString() + '</div>' +
        '<div style="color:#fff;font-weight:bold;margin-top:4px">Net: ' + C.currency + ' ' + Math.round(totalImpact).toLocaleString() + '</div>' +
        '<div style="color:#666;font-size:10px">incl ' + (C.overheadPct*100) + '% OH + ' + (C.markupPct*100) + '% markup</div>' +
        '</div>';
    }

    panel.innerHTML =
      '<div style="color:#4fc3f7;font-weight:bold;margin-bottom:6px">Variation Diff</div>' +
      '<div style="color:#44cc44">+ Added: ' + d.added.length + '</div>' +
      '<div style="color:#cc4444">\u2212 Removed: ' + d.removed.length + '</div>' +
      '<div style="color:#cccc44">~ Changed: ' + d.changed.length + '</div>' +
      '<div style="margin-top:4px;color:#888">Total: ' + (d.added.length + d.removed.length + d.changed.length) + ' affected</div>' +
      costHtml +
      '<div style="margin-top:8px;color:#666;font-size:10px">Use 📊 4D/5D to export with variance sheets</div>' +
      '<button onclick="this.parentElement.remove()" style="margin-top:8px;padding:6px 14px;background:#444;color:#ccc;border:none;border-radius:4px;cursor:pointer;font-size:11px">Close</button>';
    panel.style.display = 'block';
    console.log('[S222] §DIFF_SUMMARY added=' + d.added.length + ' removed=' + d.removed.length + ' changed=' + d.changed.length + (costHtml ? ' cost_preview=yes' : ' cost_preview=no'));
  };
}
