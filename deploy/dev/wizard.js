// wizard.js — S229: Guided Classification Wizard for non-IFC mesh imports
// Implementing S228_drop_zone_multi_format.md §S229 — Witness: W-WIZARD
// Trigger: mesh import completion (not IFC). Amber panel, one question at a time.
// Dependencies: sql.js (already loaded by import flow), semantic_enrichment.js

(function() {
  'use strict';

  // ── CSS injection ──
  var style = document.createElement('style');
  style.textContent = [
    '#wizard-panel {',
    '  position: fixed; bottom: 24px; left: 50%; transform: translateX(-50%);',
    '  z-index: 50; min-width: 360px; max-width: 520px;',
    '  background: rgba(45, 35, 10, 0.85); backdrop-filter: blur(12px);',
    '  -webkit-backdrop-filter: blur(12px);',
    '  border: 1px solid rgba(255, 191, 0, 0.25); border-radius: 16px;',
    '  padding: 20px 24px; font-family: "Segoe UI", system-ui, sans-serif;',
    '  color: #ffe0a0; box-shadow: 0 8px 32px rgba(0,0,0,0.4), 0 0 1px rgba(255,191,0,0.3);',
    '  transition: opacity 0.3s, transform 0.3s;',
    '}',
    '#wizard-panel.wizard-enter { opacity: 0; transform: translateX(-50%) translateY(20px); }',
    '#wizard-panel.wizard-visible { opacity: 1; transform: translateX(-50%) translateY(0); }',
    '#wizard-question { font-size: 15px; font-weight: 500; line-height: 1.5; margin-bottom: 12px; }',
    '#wizard-evidence { font-size: 11px; color: rgba(255, 224, 160, 0.5); margin-bottom: 14px; letter-spacing: 0.3px; line-height: 1.5; }',
    '#wizard-buttons { display: flex; gap: 10px; justify-content: center; }',
    '#wizard-buttons button { padding: 8px 28px; border-radius: 8px; border: 1px solid rgba(255, 191, 0, 0.3); font-size: 14px; font-weight: 600; cursor: pointer; transition: all 0.15s; }',
    '.wizard-yes { background: rgba(255, 191, 0, 0.25); color: #ffd54f; }',
    '.wizard-yes:hover { background: rgba(255, 191, 0, 0.4); }',
    '.wizard-no { background: rgba(255, 255, 255, 0.05); color: rgba(255, 224, 160, 0.6); }',
    '.wizard-no:hover { background: rgba(255, 255, 255, 0.1); color: #ffe0a0; }',
    '.wizard-alt { background: rgba(255, 255, 255, 0.05); color: rgba(255, 224, 160, 0.6); font-size: 12px !important; padding: 6px 16px !important; }',
    '.wizard-alt:hover { background: rgba(255, 255, 255, 0.1); color: #ffe0a0; }',
    '#wizard-progress { display: flex; justify-content: center; gap: 6px; margin-top: 14px; }',
    '#wizard-progress .dot { width: 6px; height: 6px; border-radius: 50%; background: rgba(255, 191, 0, 0.2); transition: background 0.2s; }',
    '#wizard-progress .dot.done { background: rgba(255, 191, 0, 0.7); }',
    '#wizard-progress .dot.active { background: #ffd54f; box-shadow: 0 0 6px rgba(255, 191, 0, 0.5); }',
    '#wizard-select { width: 100%; padding: 8px 12px; background: rgba(0,0,0,0.3); color: #ffe0a0; border: 1px solid rgba(255,191,0,0.2); border-radius: 6px; font-size: 13px; margin-bottom: 12px; }',
    '#wizard-select option { background: #2d230a; color: #ffe0a0; }',
  ].join('\n');
  document.head.appendChild(style);

  // ── State ──
  var wizState = {
    db: null,          // sql.js Database instance
    projectKey: null,  // IndexedDB key
    meta: null,        // import meta
    steps: [],         // computed step list
    stepIdx: 0,        // current step
    analysis: null,    // pre-computed DB analysis
    onComplete: null,  // callback when wizard finishes
  };

  // ── Analyse the imported DB to build wizard steps ──
  function analyseDb(db) {
    var analysis = {};

    // Element count
    var r = db.exec("SELECT COUNT(*) FROM elements_meta");
    analysis.totalElements = r.length ? r[0].values[0][0] : 0;

    // Storey bands
    r = db.exec("SELECT DISTINCT storey FROM elements_meta ORDER BY storey");
    analysis.storeys = r.length ? r[0].values.map(function(v) { return v[0]; }) : [];

    // Storey counts
    analysis.storeyCounts = {};
    r = db.exec("SELECT storey, COUNT(*) as cnt FROM elements_meta GROUP BY storey ORDER BY cnt DESC");
    if (r.length) {
      for (var i = 0; i < r[0].values.length; i++) {
        analysis.storeyCounts[r[0].values[i][0]] = r[0].values[i][1];
      }
    }

    // Discipline counts
    analysis.disciplines = {};
    r = db.exec("SELECT discipline, COUNT(*) as cnt FROM elements_meta GROUP BY discipline ORDER BY cnt DESC");
    if (r.length) {
      for (var i = 0; i < r[0].values.length; i++) {
        analysis.disciplines[r[0].values[i][0]] = r[0].values[i][1];
      }
    }

    // IFC class counts
    analysis.ifcClasses = {};
    r = db.exec("SELECT ifc_class, COUNT(*) as cnt FROM elements_meta GROUP BY ifc_class ORDER BY cnt DESC");
    if (r.length) {
      for (var i = 0; i < r[0].values.length; i++) {
        analysis.ifcClasses[r[0].values[i][0]] = r[0].values[i][1];
      }
    }

    // Unclassified (IfcBuildingElementProxy) count
    analysis.proxyCount = analysis.ifcClasses['IfcBuildingElementProxy'] || 0;

    // Coordinate ranges (for orientation check)
    r = db.exec("SELECT MIN(center_x), MAX(center_x), MIN(center_y), MAX(center_y), MIN(center_z), MAX(center_z) FROM element_transforms");
    if (r.length && r[0].values.length) {
      var v = r[0].values[0];
      analysis.rangeX = (v[1] || 0) - (v[0] || 0);
      analysis.rangeY = (v[3] || 0) - (v[2] || 0);
      analysis.rangeZ = (v[5] || 0) - (v[4] || 0);
      analysis.minZ = v[4] || 0;
      analysis.maxZ = v[5] || 0;
    } else {
      analysis.rangeX = 0; analysis.rangeY = 0; analysis.rangeZ = 0;
      analysis.minZ = 0; analysis.maxZ = 0;
    }

    // Repeating geometry hashes (instances)
    analysis.repeats = [];
    r = db.exec("SELECT geometry_hash, COUNT(*) as cnt FROM element_instances GROUP BY geometry_hash HAVING cnt > 1 ORDER BY cnt DESC LIMIT 10");
    if (r.length) {
      for (var i = 0; i < r[0].values.length; i++) {
        var hash = r[0].values[i][0];
        var cnt = r[0].values[i][1];
        // Get representative element name and class
        var r2 = db.exec("SELECT em.element_name, em.ifc_class, em.material_rgba FROM elements_meta em JOIN element_instances ei ON em.guid = ei.guid WHERE ei.geometry_hash = '" + hash + "' LIMIT 1");
        if (r2.length && r2[0].values.length) {
          analysis.repeats.push({
            hash: hash,
            count: cnt,
            name: r2[0].values[0][0],
            ifcClass: r2[0].values[0][1],
            material: r2[0].values[0][2],
          });
        }
      }
    }

    // Material groups (for material inference)
    analysis.materialGroups = [];
    r = db.exec("SELECT material_rgba, ifc_class, COUNT(*) as cnt FROM elements_meta WHERE material_rgba IS NOT NULL AND material_rgba != '' GROUP BY material_rgba ORDER BY cnt DESC LIMIT 10");
    if (r.length) {
      for (var i = 0; i < r[0].values.length; i++) {
        analysis.materialGroups.push({
          material: r[0].values[i][0],
          ifcClass: r[0].values[i][1],
          count: r[0].values[i][2],
        });
      }
    }

    // Unclassified element names
    analysis.proxyNames = [];
    r = db.exec("SELECT element_name, COUNT(*) as cnt FROM elements_meta WHERE ifc_class = 'IfcBuildingElementProxy' GROUP BY element_name ORDER BY cnt DESC LIMIT 20");
    if (r.length) {
      analysis.proxyNames = r[0].values.map(function(v) { return { name: v[0], count: v[1] }; });
    }

    console.log('[S229] §WIZARD_ANALYSE elements=' + analysis.totalElements +
      ' storeys=' + analysis.storeys.length +
      ' disciplines=' + Object.keys(analysis.disciplines).join(',') +
      ' proxies=' + analysis.proxyCount +
      ' repeats=' + analysis.repeats.length);

    return analysis;
  }

  // ── Build step list from analysis ──
  function buildSteps(analysis) {
    var steps = [];

    // Step 0: Orientation — always first for mesh imports
    var heightAxis = 'Z';
    if (analysis.rangeZ > 0) {
      heightAxis = (analysis.rangeZ < analysis.rangeX * 0.5 && analysis.rangeZ < analysis.rangeY * 0.5) ? 'Z' : 'Z';
    }
    steps.push({
      type: 'orientation',
      question: 'Is the building upright?',
      evidence: analysis.totalElements + ' meshes \u00b7 height range: ' +
        analysis.rangeZ.toFixed(1) + 'm \u00b7 footprint: ' +
        analysis.rangeX.toFixed(1) + ' \u00d7 ' + analysis.rangeY.toFixed(1) + 'm',
    });

    // Step 1: Storey assignment overview
    if (analysis.storeys.length > 0) {
      var bandList = analysis.storeys.map(function(s) {
        return s + ' (' + (analysis.storeyCounts[s] || 0) + ')';
      }).join(', ');
      steps.push({
        type: 'storeys',
        question: analysis.storeys.length + '-level building?',
        evidence: 'Detected storeys: ' + bandList,
      });
    }

    // Step 2: Per-storey confirmation (only if > 1 storey)
    if (analysis.storeys.length > 1) {
      for (var i = 0; i < analysis.storeys.length; i++) {
        var sName = analysis.storeys[i];
        var sCount = analysis.storeyCounts[sName] || 0;
        // Get class breakdown for this storey
        steps.push({
          type: 'storey-confirm',
          storey: sName,
          count: sCount,
          question: '"' + sName + '" \u2014 ' + sCount + ' elements?',
          evidence: 'Accept this storey label, or rename it',
        });
      }
    }

    // Step 3: Repeating geometry (instance dedup)
    for (var i = 0; i < analysis.repeats.length; i++) {
      var rep = analysis.repeats[i];
      steps.push({
        type: 'repeat',
        hash: rep.hash,
        count: rep.count,
        name: rep.name,
        ifcClass: rep.ifcClass,
        question: '"' + rep.name + '" appears ' + rep.count + '\u00d7. ' + rep.ifcClass + '?',
        evidence: 'Same geometry hash, material: ' + (rep.material || 'default'),
      });
    }

    // Step 4: Unknowns batch
    if (analysis.proxyCount > 0) {
      var nameList = analysis.proxyNames.slice(0, 8).map(function(p) {
        return p.name + (p.count > 1 ? ' \u00d7' + p.count : '');
      }).join(', ');
      steps.push({
        type: 'unknowns',
        count: analysis.proxyCount,
        question: analysis.proxyCount + ' unclassified element' + (analysis.proxyCount > 1 ? 's' : '') + '. Keep as generic?',
        evidence: nameList,
      });
    }

    // Step 5: Summary — always last
    var classSummary = Object.entries(analysis.ifcClasses).map(function(e) {
      return e[1] + ' ' + e[0];
    }).join(' \u00b7 ');
    steps.push({
      type: 'summary',
      question: 'Classification complete.',
      evidence: classSummary,
    });

    return steps;
  }

  // ── Render panel ──
  function renderPanel() {
    var panel = document.getElementById('wizard-panel');
    if (!panel) {
      panel = document.createElement('div');
      panel.id = 'wizard-panel';
      panel.className = 'wizard-enter';
      document.body.appendChild(panel);
      // Trigger enter animation
      requestAnimationFrame(function() {
        requestAnimationFrame(function() {
          panel.className = 'wizard-visible';
        });
      });
    }

    var step = wizState.steps[wizState.stepIdx];
    var totalSteps = wizState.steps.length;

    // Progress dots
    var dots = '';
    for (var i = 0; i < totalSteps; i++) {
      var cls = 'dot';
      if (i < wizState.stepIdx) cls += ' done';
      if (i === wizState.stepIdx) cls += ' active';
      dots += '<span class="' + cls + '"></span>';
    }

    // Buttons depend on step type
    var buttons = '';
    var selectHtml = '';

    if (step.type === 'summary') {
      buttons = '<button class="wizard-yes" onclick="window._wizardAnswer(\'done\')">Done</button>';
    } else if (step.type === 'orientation') {
      buttons = '<button class="wizard-yes" onclick="window._wizardAnswer(true)">Yes</button>' +
                '<button class="wizard-no" onclick="window._wizardAnswer(false)">Flip</button>';
    } else if (step.type === 'storey-confirm') {
      buttons = '<button class="wizard-yes" onclick="window._wizardAnswer(true)">Yes</button>' +
                '<button class="wizard-alt" onclick="window._wizardAnswer(\'rename\')">Rename</button>' +
                '<button class="wizard-no" onclick="window._wizardAnswer(\'skip\')">Skip</button>';
    } else if (step.type === 'repeat') {
      // Show Yes (accept proposed class) or dropdown for alternative
      var IFC_OPTIONS = [
        'IfcWall', 'IfcDoor', 'IfcWindow', 'IfcSlab', 'IfcRoof', 'IfcColumn',
        'IfcBeam', 'IfcRailing', 'IfcCovering', 'IfcStairFlight', 'IfcCurtainWall',
        'IfcFurnishingElement', 'IfcPipeSegment', 'IfcDuctSegment', 'IfcBuildingElementProxy',
      ];
      selectHtml = '<select id="wizard-select">';
      for (var j = 0; j < IFC_OPTIONS.length; j++) {
        var sel = (IFC_OPTIONS[j] === step.ifcClass) ? ' selected' : '';
        selectHtml += '<option value="' + IFC_OPTIONS[j] + '"' + sel + '>' + IFC_OPTIONS[j] + '</option>';
      }
      selectHtml += '</select>';
      buttons = '<button class="wizard-yes" onclick="window._wizardAnswer(true)">Accept</button>' +
                '<button class="wizard-alt" onclick="window._wizardAnswer(\'reclass\')">Reclassify</button>' +
                '<button class="wizard-no" onclick="window._wizardAnswer(\'skip\')">Skip</button>';
    } else if (step.type === 'unknowns') {
      buttons = '<button class="wizard-yes" onclick="window._wizardAnswer(true)">Keep Generic</button>' +
                '<button class="wizard-no" onclick="window._wizardAnswer(false)">Skip</button>';
    } else {
      buttons = '<button class="wizard-yes" onclick="window._wizardAnswer(true)">Yes</button>' +
                '<button class="wizard-no" onclick="window._wizardAnswer(false)">No</button>';
    }

    panel.innerHTML =
      '<div id="wizard-question">' + step.question + '</div>' +
      '<div id="wizard-evidence">' + step.evidence + '</div>' +
      selectHtml +
      '<div id="wizard-buttons">' + buttons + '</div>' +
      '<div id="wizard-progress">' + dots + '</div>';
  }

  // ── Handle answer ──
  window._wizardAnswer = function(answer) {
    var step = wizState.steps[wizState.stepIdx];
    var db = wizState.db;

    console.log('[S229] §WIZARD_ANSWER step=' + wizState.stepIdx +
      ' type=' + step.type + ' answer=' + answer);

    if (step.type === 'orientation' && answer === false) {
      // Flip: swap Y↔Z in transforms DB
      db.run("UPDATE element_transforms SET center_y = -center_z, center_z = center_y");
      console.log('[S229] §WIZARD_FLIP swapped Y↔Z in element_transforms');

      // S230: Also rotate the live 3D scene so user sees the flip
      if (typeof APP !== 'undefined' && APP.scene) {
        APP.scene.traverse(function(obj) {
          if (obj.isMesh) {
            var p = obj.position;
            var tmpY = p.y;
            p.y = -p.z;
            p.z = tmpY;
          }
        });
        // Reframe camera to new orientation
        if (APP.controls && APP.controls.target) {
          var t = APP.controls.target;
          var tmpT = t.y;
          t.y = -t.z;
          t.z = tmpT;
        }
        if (APP.camera) {
          var c = APP.camera.position;
          var tmpC = c.y;
          c.y = -c.z;
          c.z = tmpC;
        }
        console.log('[S230] §WIZARD_FLIP_3D scene rotated');
      }

      // Re-analyse and rebuild steps from current position
      wizState.analysis = analyseDb(db);
      wizState.steps[wizState.stepIdx].evidence =
        wizState.analysis.totalElements + ' meshes \u00b7 height range: ' +
        wizState.analysis.rangeZ.toFixed(1) + 'm \u00b7 footprint: ' +
        wizState.analysis.rangeX.toFixed(1) + ' \u00d7 ' + wizState.analysis.rangeY.toFixed(1) + 'm';
      renderPanel();
      return;
    }

    if (step.type === 'storey-confirm' && answer === 'rename') {
      var newName = prompt('Rename storey "' + step.storey + '" to:', step.storey);
      if (newName && newName !== step.storey) {
        db.run("UPDATE elements_meta SET storey = ? WHERE storey = ?", [newName, step.storey]);
        console.log('[S229] §WIZARD_RENAME_STOREY from="' + step.storey + '" to="' + newName + '"');
      }
      advanceStep();
      return;
    }

    if (step.type === 'repeat' && answer === 'reclass') {
      var sel = document.getElementById('wizard-select');
      var newClass = sel ? sel.value : step.ifcClass;
      if (newClass !== step.ifcClass) {
        // Update all elements with this geometry hash
        db.run("UPDATE elements_meta SET ifc_class = ? WHERE guid IN (SELECT guid FROM element_instances WHERE geometry_hash = ?)",
          [newClass, step.hash]);
        console.log('[S229] §WIZARD_RECLASS hash=' + step.hash +
          ' from=' + step.ifcClass + ' to=' + newClass + ' count=' + step.count);
      }
      advanceStep();
      return;
    }

    if (step.type === 'summary' || answer === 'done') {
      finishWizard();
      return;
    }

    // Default: accept and advance
    advanceStep();
  };

  function advanceStep() {
    wizState.stepIdx++;
    if (wizState.stepIdx >= wizState.steps.length) {
      finishWizard();
      return;
    }
    // Slide transition
    var panel = document.getElementById('wizard-panel');
    if (panel) {
      panel.style.opacity = '0';
      panel.style.transform = 'translateX(-50%) translateY(10px)';
      setTimeout(function() {
        renderPanel();
        panel.style.opacity = '1';
        panel.style.transform = 'translateX(-50%) translateY(0)';
      }, 150);
    } else {
      renderPanel();
    }
  }

  function finishWizard() {
    // Save modified DB back to IndexedDB
    var dbData = wizState.db.export();
    var dbBuf = dbData.buffer;

    console.log('[S229] §WIZARD_COMPLETE steps=' + wizState.steps.length +
      ' project=' + wizState.projectKey);

    // Update the project record in IndexedDB (landing context)
    if (wizState.projectKey && typeof getProject === 'function') {
      getProject(wizState.projectKey).then(function(record) {
        if (record && record.versions) {
          record.versions[record.latestVersion].db = dbBuf;
          var finalAnalysis = analyseDb(wizState.db);
          record.meta.disciplines = finalAnalysis.disciplines;
          record.meta.storeys = finalAnalysis.storeys;
          saveProject(wizState.projectKey, record).then(function() {
            console.log('[S229] §WIZARD_SAVED key=' + wizState.projectKey);
            if (typeof renderImportCards === 'function') renderImportCards();
          });
        }
      });
    }

    // S230: Update viewer cache DB (viewer context — no getProject available)
    if (wizState.projectKey && typeof getProject !== 'function') {
      try {
        var cacheReq = indexedDB.open('bim_ootb_cache', 1);
        cacheReq.onsuccess = function() {
          var cacheDb = cacheReq.result;
          var tx = cacheDb.transaction('dbs', 'readwrite');
          // Update both db and lib cache keys (same buffer for imports)
          var dbKey = 'import://' + wizState.projectKey + '/v0';
          tx.objectStore('dbs').put(dbBuf, dbKey);
          tx.oncomplete = function() {
            console.log('[S230] §WIZARD_CACHE_SAVED key=' + dbKey + ' size=' + (dbBuf.byteLength/1024).toFixed(0) + 'KB');
          };
        };
        cacheReq.onerror = function() {
          console.log('[S230] §WIZARD_CACHE_ERROR could not open bim_ootb_cache');
        };
      } catch(e) {
        console.log('[S230] §WIZARD_CACHE_ERROR ' + e.message);
      }

      // Also update the import DB (bim_ootb_imports) if accessible
      try {
        var importReq = indexedDB.open('bim_ootb_imports', 2);
        importReq.onsuccess = function() {
          var importDb = importReq.result;
          if (!importDb.objectStoreNames.contains('buildings')) return;
          var tx2 = importDb.transaction('buildings', 'readwrite');
          var store = tx2.objectStore('buildings');
          var getReq = store.get(wizState.projectKey);
          getReq.onsuccess = function() {
            var record = getReq.result;
            if (record && record.versions) {
              record.versions[record.latestVersion || 0].db = dbBuf;
              var finalAnalysis = analyseDb(wizState.db);
              record.meta.disciplines = finalAnalysis.disciplines;
              record.meta.storeys = finalAnalysis.storeys;
              store.put(record, wizState.projectKey);
              console.log('[S230] §WIZARD_IMPORT_SAVED key=' + wizState.projectKey);
            }
          };
        };
      } catch(e) {}
    }

    // Dismiss panel
    var panel = document.getElementById('wizard-panel');
    if (panel) {
      panel.style.opacity = '0';
      panel.style.transform = 'translateX(-50%) translateY(20px)';
      setTimeout(function() { panel.remove(); }, 300);
    }

    // Cleanup
    if (wizState.db) { wizState.db.close(); wizState.db = null; }
    if (wizState.onComplete) wizState.onComplete();
  }

  // ── Public API ──
  // Called after mesh import saves to IndexedDB.
  // projectKey: IndexedDB key. dbBuffer: Uint8Array of the DB.
  window.startWizard = async function(projectKey, dbBuffer, meta, onComplete) {
    // Load sql.js if needed
    if (typeof initSqlJs === 'undefined') {
      await new Promise(function(resolve) {
        var s = document.createElement('script');
        s.src = 'https://sql.js.org/dist/sql-wasm.js';
        s.onload = resolve;
        document.head.appendChild(s);
      });
    }
    var SQL = await initSqlJs({ locateFile: function(f) { return 'https://sql.js.org/dist/' + f; } });
    var db = new SQL.Database(new Uint8Array(dbBuffer));

    wizState.db = db;
    wizState.projectKey = projectKey;
    wizState.meta = meta;
    wizState.onComplete = onComplete || null;
    wizState.analysis = analyseDb(db);
    wizState.steps = buildSteps(wizState.analysis);
    wizState.stepIdx = 0;

    console.log('[S229] §WIZARD_START project=' + projectKey +
      ' elements=' + wizState.analysis.totalElements +
      ' steps=' + wizState.steps.length);

    renderPanel();
  };

  // ── Skip/dismiss without saving ──
  window.dismissWizard = function() {
    var panel = document.getElementById('wizard-panel');
    if (panel) {
      panel.style.opacity = '0';
      panel.style.transform = 'translateX(-50%) translateY(20px)';
      setTimeout(function() { panel.remove(); }, 300);
    }
    if (wizState.db) { wizState.db.close(); wizState.db = null; }
  };

})();
