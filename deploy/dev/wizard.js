/**
 * BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
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
    '  position: fixed; top: 50%; right: 24px; transform: translateY(-50%);',
    '  z-index: 50; min-width: 320px; max-width: 420px;',
    '  background: rgba(45, 35, 10, 0.55); backdrop-filter: blur(12px);',
    '  -webkit-backdrop-filter: blur(12px);',
    '  border: 1px solid rgba(255, 191, 0, 0.25); border-radius: 16px;',
    '  padding: 20px 24px; font-family: "Segoe UI", system-ui, sans-serif;',
    '  color: #ffe0a0; box-shadow: 0 8px 32px rgba(0,0,0,0.4), 0 0 1px rgba(255,191,0,0.3);',
    '  transition: opacity 0.3s, transform 0.3s;',
    '}',
    '#wizard-panel.wizard-enter { opacity: 0; transform: translateY(-50%) translateX(20px); }',
    '#wizard-panel.wizard-visible { opacity: 1; transform: translateY(-50%) translateX(0); }',
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

  // ── Discipline colors (shared across all highlighting) ──
  var DISC_COLORS = {
    ARC: 0x4488ff, STR: 0x44cccc, MEP: 0x44cc44,
    ELEC: 0xcccc44, FP: 0xcc8844, ACMV: 0xcc4444, PLB: 0x8844cc,
  };
  var DEFAULT_DISC_COLOR = 0x888888;

  // ── Storey highlighting — color-code 3D elements per storey ──
  var STOREY_COLORS = [
    0x4488ff, 0xff8844, 0x44cc88, 0xcc44cc, 0xcccc44,
    0x44cccc, 0xff4488, 0x88ff44, 0x8844ff, 0xff8888,
  ];
  var _savedMaterials = [];  // [{mesh, origMaterial}] for reverting
  var _baseMaterials = [];   // [{mesh, origMaterial}] for discipline base colors

  // ── IFC class list for picker dropdown ──
  var IFC_CLASSES = [
    'IfcWall', 'IfcDoor', 'IfcWindow', 'IfcSlab', 'IfcRoof', 'IfcCovering',
    'IfcStairFlight', 'IfcRailing', 'IfcRamp', 'IfcCurtainWall',
    'IfcColumn', 'IfcBeam', 'IfcFooting', 'IfcPile',
    'IfcPipeSegment', 'IfcSanitaryTerminal', 'IfcDuctSegment',
    'IfcCableSegment', 'IfcLightFixture', 'IfcOutlet', 'IfcElectricAppliance',
    'IfcFireSuppressionTerminal', 'IfcFurnishingElement',
    'IfcBuildingElementProxy',
  ];

  // ── Storey bands (same as semantic_enrichment.js) ──
  var STOREY_BANDS = [
    { min: -Infinity, max: -0.5,      name: 'Basement' },
    { min: -0.5,      max: 3.5,       name: 'Ground Floor' },
    { min: 3.5,       max: 6.5,       name: 'Level 1' },
    { min: 6.5,       max: 9.5,       name: 'Level 2' },
    { min: 9.5,       max: 12.5,      name: 'Level 3' },
    { min: 12.5,      max: Infinity,  name: 'Upper Levels' },
  ];

  function classifyStoreyZ(z) {
    for (var i = 0; i < STOREY_BANDS.length; i++) {
      if (z >= STOREY_BANDS[i].min && z < STOREY_BANDS[i].max) return STOREY_BANDS[i].name;
    }
    return 'Upper Levels';
  }

  // ── Re-classify storeys using Z-gap clustering ──
  // S234: Replaces fixed 3m bands with gap detection. Fixes Bug 6b (sign-flip
  // inversion) and Bug 6c (wrong storey count from equal bands).
  // heightAxis: 'z' (default, Z-up) or 'y' (Y-up OBJ before flip)
  function reclassifyStoreys(db, heightAxis) {
    try {
      var col = (heightAxis === 'y') ? 'center_y' : 'center_z';
      var r = db.exec("SELECT guid, " + col + " FROM element_transforms ORDER BY " + col);
      if (!r.length || !r[0].values.length) return;
      var rows = r[0].values;

      // 1. Round Z values to 0.1m, sort ascending (fixes Bug 6b — always bottom-up)
      var zValues = rows.map(function(v) { return Math.round((v[1] || 0) * 10) / 10; });
      zValues.sort(function(a, b) { return a - b; });

      // 2. Find gap threshold — median gap × 5 or 1.5m, whichever is larger
      var gaps = [];
      for (var i = 1; i < zValues.length; i++) gaps.push(zValues[i] - zValues[i - 1]);
      gaps.sort(function(a, b) { return a - b; });
      var medianGap = gaps.length ? gaps[Math.floor(gaps.length / 2)] : 0.1;
      var threshold = Math.max(1.5, medianGap * 5);

      // 3. Split into floors at gaps > threshold
      var floors = [{ minZ: zValues[0], maxZ: zValues[0] }];
      for (var i = 1; i < zValues.length; i++) {
        if (zValues[i] - zValues[i - 1] > threshold) {
          floors.push({ minZ: zValues[i], maxZ: zValues[i] });
        } else {
          floors[floors.length - 1].maxZ = zValues[i];
        }
      }
      // Cap at 10 floors
      if (floors.length > 10) floors = floors.slice(0, 10);

      // 4. Assign names bottom-up: Ground Floor, Level 1, Level 2...
      var stmt = db.prepare("UPDATE elements_meta SET storey = ? WHERE guid = ?");
      var assigned = 0;
      for (var i = 0; i < rows.length; i++) {
        var guid = rows[i][0];
        var z = Math.round((rows[i][1] || 0) * 10) / 10;
        for (var f = 0; f < floors.length; f++) {
          if (z >= floors[f].minZ - 0.05 && z <= floors[f].maxZ + 0.05) {
            var name = floors.length === 1 ? 'Ground Floor' :
              f === 0 ? 'Ground Floor' : 'Level ' + f;
            stmt.run([name, guid]);
            assigned++;
            break;
          }
        }
      }
      stmt.free();
      console.log('[S234] §WIZARD_RECLASSIFY_STOREYS count=' + assigned +
        ' floors=' + floors.length + ' threshold=' + threshold.toFixed(1) + 'm' +
        ' median_gap=' + medianGap.toFixed(2) + 'm');
    } catch(e) {
      console.log('[S234] §WIZARD_RECLASSIFY_ERR ' + e.message);
    }
  }

  // ── Apply discipline-based base colors to all meshes ──
  function applyDisciplineColors(db) {
    if (typeof APP === 'undefined' || !APP.scene) return;
    revertDisciplineColors();

    var guidDisc = {};
    try {
      var r = db.exec("SELECT guid, discipline FROM elements_meta");
      if (r.length) {
        for (var i = 0; i < r[0].values.length; i++) {
          guidDisc[r[0].values[i][0]] = r[0].values[i][1];
        }
      }
    } catch(e) { return; }

    var discMats = {};
    for (var d in DISC_COLORS) {
      discMats[d] = new THREE.MeshStandardMaterial({
        color: DISC_COLORS[d], roughness: 0.7, metalness: 0.1,
      });
    }
    var defaultMat = new THREE.MeshStandardMaterial({
      color: DEFAULT_DISC_COLOR, roughness: 0.7, metalness: 0.1,
    });

    APP.scene.traverse(function(obj) {
      if (!obj.isMesh || !obj.userData.guid) return;
      var disc = guidDisc[obj.userData.guid];
      var mat = disc ? (discMats[disc] || defaultMat) : defaultMat;
      _baseMaterials.push({ mesh: obj, origMaterial: obj.material });
      obj.material = mat;
    });

    console.log('[S230b] §WIZARD_DISC_COLORS applied=' + _baseMaterials.length);
  }

  function revertDisciplineColors() {
    for (var i = 0; i < _baseMaterials.length; i++) {
      _baseMaterials[i].mesh.material = _baseMaterials[i].origMaterial;
    }
    _baseMaterials = [];
  }

  function applyStoreyHighlight(db) {
    if (typeof APP === 'undefined' || !APP.scene) return;
    revertStoreyHighlight();

    // Build guid → storey map
    var guidStorey = {};
    try {
      var r = db.exec("SELECT guid, storey FROM elements_meta WHERE storey IS NOT NULL");
      if (r.length) {
        for (var i = 0; i < r[0].values.length; i++) {
          guidStorey[r[0].values[i][0]] = r[0].values[i][1];
        }
      }
    } catch(e) { return; }

    // Map storey names to color indices
    var storeyIdx = {};
    var storeys = wizState.analysis ? wizState.analysis.storeys : [];
    for (var s = 0; s < storeys.length; s++) {
      storeyIdx[storeys[s]] = s;
    }

    // Create materials per storey
    var storeyMats = {};
    for (var name in storeyIdx) {
      var ci = storeyIdx[name] % STOREY_COLORS.length;
      storeyMats[name] = new THREE.MeshStandardMaterial({
        color: STOREY_COLORS[ci],
        transparent: true,
        opacity: 0.85,
        roughness: 0.6,
      });
    }

    // Traverse scene and apply
    APP.scene.traverse(function(obj) {
      if (!obj.isMesh || !obj.userData.guid) return;
      var storey = guidStorey[obj.userData.guid];
      if (storey && storeyMats[storey]) {
        _savedMaterials.push({ mesh: obj, origMaterial: obj.material });
        obj.material = storeyMats[storey];
      }
    });

    console.log('[S230b] §WIZARD_STOREY_HIGHLIGHT applied=' + _savedMaterials.length + ' storeys=' + storeys.length);
  }

  function revertStoreyHighlight() {
    for (var i = 0; i < _savedMaterials.length; i++) {
      _savedMaterials[i].mesh.material = _savedMaterials[i].origMaterial;
    }
    if (_savedMaterials.length > 0) {
      console.log('[S230b] §WIZARD_STOREY_REVERT count=' + _savedMaterials.length);
    }
    _savedMaterials = [];
  }

  // ── Reframe camera to fit scene bounding box after flip ──
  // S230b: Compute bbox from mesh geometry bounds (not just obj.position)
  // Uses local-space geometry bounds to avoid scene rotation blowup
  function reframeCameraToBbox() {
    if (typeof APP === 'undefined' || !APP.scene || !APP.camera) return;
    // S233: Compute local-space bbox (without scene rotation), then transform center to world
    // This avoids expandByObject blowing up when scene.rotation.x = -PI/2
    var savedRx = APP.scene.rotation.x;
    APP.scene.rotation.x = 0;
    APP.scene.updateMatrixWorld(true);
    var localBox = new THREE.Box3();
    APP.scene.traverse(function(obj) {
      if (obj.isMesh && obj.geometry) localBox.expandByObject(obj);
    });
    APP.scene.rotation.x = savedRx;
    APP.scene.updateMatrixWorld(true);
    if (localBox.isEmpty()) return;
    var localCenter = localBox.getCenter(new THREE.Vector3());
    var localSize = localBox.getSize(new THREE.Vector3());
    // Prefer DB analysis for maxDim (reliable), fall back to Three.js bbox
    var maxDim;
    if (wizState.analysis) {
      maxDim = Math.max(wizState.analysis.rangeX, wizState.analysis.rangeY, wizState.analysis.rangeZ, 1);
    } else {
      maxDim = Math.max(localSize.x, localSize.y, localSize.z, 1);
    }
    // Transform center to world space (through current scene rotation)
    var worldCenter = localCenter.applyMatrix4(APP.scene.matrixWorld);

    var fov = APP.camera.fov * (Math.PI / 180);
    var dist = maxDim / (2 * Math.tan(fov / 2)) * 1.8;
    // S233: Camera from above-front so floors are visually separated after flip
    APP.camera.position.set(
      worldCenter.x + dist * 0.3,
      worldCenter.y + dist * 0.9,
      worldCenter.z + dist * 0.5
    );
    APP.camera.lookAt(worldCenter);
    // S230b: Adjust near/far clipping to match building scale
    APP.camera.near = Math.max(0.01, dist * 0.005);
    APP.camera.far = Math.max(1000, dist * 10);
    APP.camera.updateProjectionMatrix();
    if (APP.controls) {
      APP.controls.target.copy(worldCenter);
      APP.controls.update();
    }
    console.log('[S233] §WIZARD_REFRAME center=' + worldCenter.x.toFixed(1) + ',' + worldCenter.y.toFixed(1) + ',' + worldCenter.z.toFixed(1) + ' dist=' + dist.toFixed(1) + ' maxDim=' + maxDim.toFixed(1) + ' near=' + APP.camera.near.toFixed(2) + ' far=' + APP.camera.far.toFixed(0));
  }

  // ── Analyse the imported DB to build wizard steps ──
  function analyseDb(db) {
    var analysis = {};

    // Element count
    var r = db.exec("SELECT COUNT(*) FROM elements_meta");
    analysis.totalElements = r.length ? r[0].values[0][0] : 0;

    // Storey bands
    r = db.exec("SELECT DISTINCT storey FROM elements_meta ORDER BY storey");
    analysis.storeys = r.length ? r[0].values.map(function(v) { return v[0]; }) : [];

    // Storey counts + elevation ranges
    analysis.storeyCounts = {};
    analysis.storeyElevations = {};
    r = db.exec("SELECT storey, COUNT(*) as cnt FROM elements_meta GROUP BY storey ORDER BY cnt DESC");
    if (r.length) {
      for (var i = 0; i < r[0].values.length; i++) {
        analysis.storeyCounts[r[0].values[i][0]] = r[0].values[i][1];
      }
    }
    // S230b: Elevation ranges per storey (for smart labels), normalized to 0-based
    try {
      r = db.exec("SELECT m.storey, MIN(t.center_z), MAX(t.center_z) FROM elements_meta m JOIN element_transforms t ON t.guid = m.guid GROUP BY m.storey ORDER BY MIN(t.center_z)");
      if (r.length) {
        // Find global minimum to normalize all elevations to 0-based
        var globalMinZ = Infinity;
        for (var i = 0; i < r[0].values.length; i++) {
          var lo = r[0].values[i][1] || 0;
          if (lo < globalMinZ) globalMinZ = lo;
        }
        if (!isFinite(globalMinZ)) globalMinZ = 0;
        for (var i = 0; i < r[0].values.length; i++) {
          analysis.storeyElevations[r[0].values[i][0]] = {
            minZ: (r[0].values[i][1] || 0) - globalMinZ,
            maxZ: (r[0].values[i][2] || 0) - globalMinZ,
          };
        }
      }
    } catch(e) { /* element_transforms may not exist for some DBs */ }

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
  // Simple 3-step flow: orientation → storeys → done.
  // Detailed classification is the compiler's job, not the user's.
  function buildSteps(analysis) {
    var steps = [];

    // Step 0: Orientation
    steps.push({
      type: 'orientation',
      question: 'Is the building upright?',
      evidence: analysis.totalElements + ' meshes \u00b7 height: ' +
        analysis.rangeZ.toFixed(1) + 'm \u00b7 footprint: ' +
        analysis.rangeX.toFixed(1) + ' \u00d7 ' + analysis.rangeY.toFixed(1) + 'm',
    });

    // Step 1: Storeys overview (S230b: smart labels with elevation ranges)
    if (analysis.storeys.length > 0) {
      var bandList = analysis.storeys.map(function(s) {
        var elev = analysis.storeyElevations[s];
        var range = elev ? elev.minZ.toFixed(1) + '\u2013' + elev.maxZ.toFixed(1) + 'm' : '';
        var cnt = analysis.storeyCounts[s] || 0;
        return s + (range ? ' [' + range + ']' : '') + ' (' + cnt + ' el)';
      }).join('\n');
      steps.push({
        type: 'storeys',
        question: analysis.storeys.length + ' storeys detected. Correct?',
        evidence: bandList,
      });
    }

    // Step 2: Element classification (draining pool) — only for non-IFC imports
    // S234: Show classify step if >50% elements are unclassified (IfcBuildingElementProxy)
    if (analysis.proxyCount > analysis.totalElements * 0.5) {
      steps.push({
        type: 'classify',
        question: analysis.proxyCount + ' unclassified elements to identify',
        evidence: 'Toggle through types, confirm with Yes. Pool drains as you go.',
      });
    }

    // Step 3: Summary — classification stats
    var classEntries = Object.entries(analysis.ifcClasses);
    var classSummary = classEntries.map(function(e) {
      return e[1] + ' ' + e[0].replace('Ifc', '');
    }).join(' \u00b7 ');
    var proxyNote = analysis.proxyCount > 0
      ? '\n' + analysis.proxyCount + ' unclassified \u2014 kept as generic'
      : '';
    steps.push({
      type: 'summary',
      question: 'Classification complete.',
      evidence: classSummary + proxyNote,
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
    } else if (step.type === 'classify') {
      // S234: Classify step — init pool on first render, then delegate to renderClassifyStep
      buttons = '';  // renderClassifyStep will set buttons
    } else if (step.type === 'storeys') {
      buttons = '<button class="wizard-yes" onclick="window._wizardAnswer(true)">Yes</button>' +
                '<button class="wizard-alt" onclick="window._wizToggleStoreys()" title="Cycle: shift Ground Floor label up/down">Toggle</button>' +
                '<button class="wizard-alt" onclick="window._wizardAnswer(\'walk_storeys\')" title="Isolate floors one at a time">Walk</button>' +
                '<button class="wizard-alt" onclick="window._wizardAnswer(\'edit_storeys\')" title="Change storey count">Edit</button>';
    } else {
      buttons = '<button class="wizard-yes" onclick="window._wizardAnswer(true)">Yes</button>' +
                '<button class="wizard-no" onclick="window._wizardAnswer(\'done\')">Done</button>';
    }

    // S230b: For storeys step, build evidence with colored legend dots
    var evidenceHtml = step.evidence;
    if (step.type === 'storeys' && wizState.analysis) {
      var storeys = wizState.analysis.storeys;
      evidenceHtml = storeys.map(function(s, i) {
        var color = '#' + (STOREY_COLORS[i % STOREY_COLORS.length]).toString(16).padStart(6, '0');
        var elev = wizState.analysis.storeyElevations[s];
        var range = elev ? elev.minZ.toFixed(1) + '\u2013' + elev.maxZ.toFixed(1) + 'm' : '';
        var cnt = wizState.analysis.storeyCounts[s] || 0;
        return '<span style="display:inline-block;width:8px;height:8px;border-radius:50%;background:' + color + ';margin-right:5px;vertical-align:middle"></span>' +
          s + (range ? ' [' + range + ']' : '') + ' (' + cnt + ' el)';
      }).join('<br>');
    }

    panel.innerHTML =
      '<div id="wizard-question">' + step.question + '</div>' +
      '<div id="wizard-evidence">' + evidenceHtml + '</div>' +
      selectHtml +
      '<div id="wizard-buttons">' + buttons + '</div>' +
      '<div id="wizard-progress">' + dots + '</div>';

    // S230b: Apply/revert storey highlighting based on current step
    if (step.type === 'storeys' && wizState.db) {
      applyStoreyHighlight(wizState.db);
    } else {
      revertStoreyHighlight();
    }

    // S234: Classify step — init pool and render guess after panel is in DOM
    if (step.type === 'classify' && wizState.db) {
      initClassifyPool();
      renderClassifyStep();
    }
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
      // S233: Persist flip state so reopen skips re-flipping
      db.run("CREATE TABLE IF NOT EXISTS project_metadata (key TEXT PRIMARY KEY, value TEXT)");
      db.run("INSERT OR REPLACE INTO project_metadata (key, value) VALUES ('orientation', 'z_up')");
      console.log('[S229] §WIZARD_FLIP swapped Y↔Z in element_transforms, orientation=z_up saved');

      // S230b: After Y↔Z swap, height is now in Z
      wizState._heightAxis = 'z';
      reclassifyStoreys(db, 'z');

      // S230: Toggle scene between 0 and -90° around X (Y-up ↔ Z-up)
      if (typeof APP !== 'undefined' && APP.scene) {
        // Toggle — not accumulate
        var flipped = Math.abs(APP.scene.rotation.x + Math.PI / 2) < 0.01;
        APP.scene.rotation.x = flipped ? 0 : -Math.PI / 2;
        APP.scene.updateMatrixWorld(true);

        // S230b: Reframe camera to fit rotated bounding box
        reframeCameraToBbox();
        // Re-apply discipline colors after flip
        applyDisciplineColors(db);
        console.log('[S230] §WIZARD_FLIP_3D rotation.x=' + APP.scene.rotation.x.toFixed(3) + ' flipped=' + !flipped);
      }

      // Re-analyse and rebuild steps from current position
      wizState.analysis = analyseDb(db);
      wizState.steps = buildSteps(wizState.analysis);
      wizState.steps[wizState.stepIdx].evidence =
        wizState.analysis.totalElements + ' meshes \u00b7 height range: ' +
        wizState.analysis.rangeZ.toFixed(1) + 'm \u00b7 footprint: ' +
        wizState.analysis.rangeX.toFixed(1) + ' \u00d7 ' + wizState.analysis.rangeY.toFixed(1) + 'm';
      renderPanel();
      return;
    }

    // S230b: Storey walkthrough — show one floor at a time
    if (step.type === 'storeys' && answer === 'walk_storeys') {
      enterStoreyWalk();
      return;
    }

    // S230b: Storey edit — user overrides detected storey count
    if (step.type === 'storeys' && answer === 'edit_storeys') {
      enterStoreyEdit();
      return;
    }

    // S233: Storey rename — user renames floors or merges adjacent
    if (step.type === 'storeys' && answer === 'rename_storeys') {
      enterStoreyRename();
      return;
    }

    if (step.type === 'summary') {
      finishWizard();
      return;
    }

    // 'done' on non-summary steps = advance (not finish)
    if (answer === 'done') {
      advanceStep();
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

  // ── S233: Storey toggle — cycle naming by shifting "Ground Floor" one band up ──
  // _groundOffset: 0 = lowest band is Ground Floor, 1 = lowest is Basement, 2 = B2+Basement, etc.
  // Cycles through: also adds "Roof" to top band on certain offsets.
  var _groundOffset = 0;
  var _roofOn = false;  // toggles roof label every full cycle

  window._wizToggleStoreys = function() {
    var db = wizState.db;
    if (!db || !wizState.analysis) return;
    var storeys = wizState.analysis.storeys;
    var n = storeys.length;
    if (n <= 1) return;

    // Advance offset: 0 → 1 → 2 → ... → n-1 → 0 (with roof flip at wrap)
    _groundOffset++;
    if (_groundOffset >= n) {
      _groundOffset = 0;
      _roofOn = !_roofOn;
    }

    // Apply new names based on offset
    // Sort storeys by elevation (lowest first) to name correctly
    var sorted = storeys.slice().sort(function(a, b) {
      var ea = wizState.analysis.storeyElevations[a];
      var eb = wizState.analysis.storeyElevations[b];
      return (ea ? ea.minZ : 0) - (eb ? eb.minZ : 0);
    });

    var stmt = db.prepare("UPDATE elements_meta SET storey = ? WHERE storey = ?");
    for (var i = 0; i < sorted.length; i++) {
      var relIdx = i - _groundOffset;
      var newName;
      if (_roofOn && i === sorted.length - 1) {
        newName = 'Roof';
      } else if (relIdx < 0) {
        newName = relIdx === -1 ? 'Basement' : 'Basement ' + Math.abs(relIdx);
      } else if (relIdx === 0) {
        newName = 'Ground Floor';
      } else {
        newName = 'Level ' + relIdx;
      }
      if (newName !== sorted[i]) {
        stmt.run([newName, sorted[i]]);
      }
    }
    stmt.free();

    var scheme = sorted.map(function(_, i) {
      var r = i - _groundOffset;
      if (_roofOn && i === sorted.length - 1) return 'Roof';
      return r < 0 ? (r === -1 ? 'B' : 'B' + Math.abs(r)) : r === 0 ? 'G' : 'L' + r;
    }).join(',');
    console.log('[S233] §WIZARD_STOREY_TOGGLE offset=' + _groundOffset + ' roof=' + _roofOn + ' scheme=' + scheme);

    // Re-analyse and re-render (colors update)
    wizState.analysis = analyseDb(db);
    wizState.steps = buildSteps(wizState.analysis);
    applyDisciplineColors(db);
    renderPanel();
  };

  // ── Storey edit — user corrects storey count ──
  function enterStoreyEdit() {
    var panel = document.getElementById('wizard-panel');
    if (!panel || !wizState.db || !wizState.analysis) return;
    var a = wizState.analysis;

    panel.querySelector('#wizard-question').textContent = 'How many storeys?';
    panel.querySelector('#wizard-evidence').innerHTML =
      'Height: 0.0m to ' + a.rangeZ.toFixed(1) + 'm (' + a.rangeZ.toFixed(1) + 'm total)<br>' +
      '<input id="wiz-storey-count" type="number" min="1" max="20" value="' + a.storeys.length + '" ' +
        'style="width:60px;padding:6px;background:rgba(0,0,0,0.3);color:#ffe0a0;border:1px solid rgba(255,191,0,0.2);border-radius:4px;font-size:14px;text-align:center;margin-top:8px">';
    panel.querySelector('#wizard-buttons').innerHTML =
      '<button class="wizard-yes" onclick="window._wizApplyStoreyEdit()">Apply</button>' +
      '<button class="wizard-no" onclick="window._wizardAnswer(true)">Cancel</button>';

    console.log('[S230b] §WIZARD_STOREY_EDIT current=' + a.storeys.length);
  }

  window._wizApplyStoreyEdit = function() {
    var input = document.getElementById('wiz-storey-count');
    if (!input || !wizState.db || !wizState.analysis) return;
    var n = parseInt(input.value) || 1;
    if (n < 1) n = 1;
    if (n > 20) n = 20;

    var db = wizState.db;
    var col = wizState._heightAxis === 'y' ? 'center_y' : 'center_z';
    // Get actual height range from DB using correct axis
    var hRng = db.exec("SELECT MIN(" + col + "), MAX(" + col + ") FROM element_transforms");
    var hMin = (hRng.length && hRng[0].values[0][0]) || 0;
    var hMax = (hRng.length && hRng[0].values[0][1]) || 0;
    var totalHeight = hMax - hMin;
    if (totalHeight <= 0) totalHeight = 3;
    var bandHeight = totalHeight / n;

    // Build new storey bands and reclassify
    try {
      var r = db.exec("SELECT t.guid, t." + col + " FROM element_transforms t");
      if (!r.length) return;
      var stmt = db.prepare("UPDATE elements_meta SET storey = ? WHERE guid = ?");
      for (var i = 0; i < r[0].values.length; i++) {
        var guid = r[0].values[i][0];
        var z = r[0].values[i][1] || 0;
        var band = Math.floor((z - hMin) / bandHeight);
        if (band >= n) band = n - 1;
        if (band < 0) band = 0;
        var storeyName = n === 1 ? 'Ground Floor' :
          band === 0 ? 'Ground Floor' : 'Level ' + band;
        stmt.run([storeyName, guid]);
      }
      stmt.free();
      console.log('[S230b] §WIZARD_STOREY_REBAND count=' + n + ' bandHeight=' + bandHeight.toFixed(1) + 'm');
    } catch(e) {
      console.log('[S230b] §WIZARD_STOREY_REBAND_ERR ' + e.message);
    }

    // Re-analyse and re-render storeys step
    wizState.analysis = analyseDb(db);
    wizState.steps = buildSteps(wizState.analysis);
    renderPanel();
  };

  // ── S233: Storey rename — editable names + merge adjacent ──
  function enterStoreyRename() {
    var panel = document.getElementById('wizard-panel');
    if (!panel || !wizState.db || !wizState.analysis) return;
    var storeys = wizState.analysis.storeys;

    panel.querySelector('#wizard-question').textContent = 'Rename storeys or merge adjacent floors';

    // Build editable storey list with rename inputs and merge buttons
    var html = '';
    for (var i = 0; i < storeys.length; i++) {
      var s = storeys[i];
      var color = '#' + (STOREY_COLORS[i % STOREY_COLORS.length]).toString(16).padStart(6, '0');
      var elev = wizState.analysis.storeyElevations[s];
      var range = elev ? elev.minZ.toFixed(1) + '\u2013' + elev.maxZ.toFixed(1) + 'm' : '';
      var cnt = wizState.analysis.storeyCounts[s] || 0;
      html += '<div style="display:flex;align-items:center;gap:6px;margin:3px 0">' +
        '<span style="display:inline-block;width:8px;height:8px;border-radius:50%;background:' + color + ';flex-shrink:0"></span>' +
        '<input class="wiz-storey-name" data-old="' + s.replace(/"/g, '&quot;') + '" value="' + s.replace(/"/g, '&quot;') + '" ' +
          'style="flex:1;padding:4px 6px;background:rgba(0,0,0,0.3);color:#ffe0a0;border:1px solid rgba(255,191,0,0.2);border-radius:3px;font-size:12px">' +
        '<span style="font-size:10px;color:#ccc">' + range + ' (' + cnt + ')</span>';
      if (i < storeys.length - 1) {
        html += '<button class="wiz-merge-btn" data-idx="' + i + '" onclick="window._wizMergeStoreys(' + i + ')" ' +
          'style="padding:2px 6px;font-size:10px;background:rgba(255,191,0,0.15);color:#ffe0a0;border:1px solid rgba(255,191,0,0.3);border-radius:3px;cursor:pointer" ' +
          'title="Merge with floor below">\u2193 merge</button>';
      }
      html += '</div>';
    }
    panel.querySelector('#wizard-evidence').innerHTML = html;
    panel.querySelector('#wizard-buttons').innerHTML =
      '<button class="wizard-yes" onclick="window._wizApplyStoreyRename()">Apply</button>' +
      '<button class="wizard-no" onclick="window._wizardAnswer(true)">Cancel</button>';

    console.log('[S233] §WIZARD_STOREY_RENAME_ENTER storeys=' + storeys.length);
  }

  window._wizApplyStoreyRename = function() {
    var db = wizState.db;
    if (!db) return;
    var inputs = document.querySelectorAll('.wiz-storey-name');
    var renamed = 0;
    var stmt = db.prepare("UPDATE elements_meta SET storey = ? WHERE storey = ?");
    for (var i = 0; i < inputs.length; i++) {
      var oldName = inputs[i].getAttribute('data-old');
      var newName = inputs[i].value.trim();
      if (newName && newName !== oldName) {
        stmt.run([newName, oldName]);
        renamed++;
        console.log('[S233] §WIZARD_STOREY_RENAME "' + oldName + '" → "' + newName + '"');
      }
    }
    stmt.free();
    console.log('[S233] §WIZARD_STOREY_RENAME_APPLY renamed=' + renamed);

    wizState.analysis = analyseDb(db);
    wizState.steps = buildSteps(wizState.analysis);
    applyDisciplineColors(db);
    renderPanel();
  };

  window._wizMergeStoreys = function(idx) {
    var db = wizState.db;
    if (!db || !wizState.analysis) return;
    var storeys = wizState.analysis.storeys;
    if (idx < 0 || idx >= storeys.length - 1) return;
    var keepName = storeys[idx];
    var mergeName = storeys[idx + 1];
    db.run("UPDATE elements_meta SET storey = ? WHERE storey = ?", [keepName, mergeName]);
    console.log('[S233] §WIZARD_STOREY_MERGE "' + mergeName + '" into "' + keepName + '"');

    wizState.analysis = analyseDb(db);
    wizState.steps = buildSteps(wizState.analysis);
    applyDisciplineColors(db);
    enterStoreyRename();  // refresh the rename UI
  };

  // ── Storey walkthrough — show one floor at a time ──
  var _walkState = { active: false, idx: 0, storeys: [], hiddenMeshes: [] };

  function enterStoreyWalk() {
    if (typeof APP === 'undefined' || !APP.scene || !wizState.analysis) return;
    var storeys = wizState.analysis.storeys;
    if (storeys.length < 2) return;

    _walkState.active = true;
    _walkState.idx = 0;
    _walkState.storeys = storeys;
    _walkState.hiddenMeshes = [];

    console.log('[S230b] §WIZARD_STOREY_WALK_START floors=' + storeys.length);
    showWalkFloor(0);
  }

  function showWalkFloor(idx) {
    if (!wizState.db || !wizState.analysis) return;
    var storeys = _walkState.storeys;
    var targetStorey = storeys[idx];

    // Build guid→storey map
    var guidStorey = {};
    try {
      var r = wizState.db.exec("SELECT guid, storey FROM elements_meta WHERE storey IS NOT NULL");
      if (r.length) {
        for (var i = 0; i < r[0].values.length; i++) {
          guidStorey[r[0].values[i][0]] = r[0].values[i][1];
        }
      }
    } catch(e) { return; }

    // Show only meshes belonging to target storey, hide others
    _walkState.hiddenMeshes = [];
    APP.scene.traverse(function(obj) {
      if (!obj.isMesh || !obj.userData.guid) return;
      var s = guidStorey[obj.userData.guid];
      if (s && s !== targetStorey) {
        if (obj.visible) {
          _walkState.hiddenMeshes.push(obj);
          obj.visible = false;
        }
      } else {
        obj.visible = true;
      }
    });

    // Update panel
    var panel = document.getElementById('wizard-panel');
    if (!panel) return;
    var elev = wizState.analysis.storeyElevations[targetStorey];
    var range = elev ? elev.minZ.toFixed(1) + '\u2013' + elev.maxZ.toFixed(1) + 'm' : '';
    var cnt = wizState.analysis.storeyCounts[targetStorey] || 0;
    var ci = idx % STOREY_COLORS.length;
    var color = '#' + STOREY_COLORS[ci].toString(16).padStart(6, '0');

    panel.querySelector('#wizard-question').textContent =
      'Floor ' + (idx + 1) + '/' + storeys.length + ': ' + targetStorey;
    panel.querySelector('#wizard-evidence').innerHTML =
      '<span style="display:inline-block;width:10px;height:10px;border-radius:50%;background:' + color + ';margin-right:6px;vertical-align:middle"></span>' +
      range + ' (' + cnt + ' elements)';

    var prevBtn = idx > 0
      ? '<button class="wizard-alt" onclick="window._wizWalkPrev()">Prev</button>' : '';
    var nextBtn = idx < storeys.length - 1
      ? '<button class="wizard-yes" onclick="window._wizWalkNext()">Next</button>'
      : '<button class="wizard-yes" onclick="window._wizWalkDone()">Done</button>';
    panel.querySelector('#wizard-buttons').innerHTML = prevBtn + nextBtn;

    console.log('[S230b] §WIZARD_STOREY_WALK floor=' + (idx + 1) + ' name=' + targetStorey + ' elements=' + cnt);
  }

  function restoreWalkVisibility() {
    for (var i = 0; i < _walkState.hiddenMeshes.length; i++) {
      _walkState.hiddenMeshes[i].visible = true;
    }
    _walkState.hiddenMeshes = [];
  }

  window._wizWalkNext = function() {
    restoreWalkVisibility();
    _walkState.idx++;
    showWalkFloor(_walkState.idx);
  };

  window._wizWalkPrev = function() {
    restoreWalkVisibility();
    _walkState.idx--;
    showWalkFloor(_walkState.idx);
  };

  window._wizWalkDone = function() {
    restoreWalkVisibility();
    _walkState.active = false;
    console.log('[S230b] §WIZARD_STOREY_WALK_DONE');
    // Return to storeys step with all floors visible
    renderPanel();
  };

  // ── S234: Draining-pool element classification ──
  // Toggle-to-confirm pattern: code guesses type, user sees color-coded 3D, Yes/Toggle.
  // Pool shrinks with each Yes. IFC imports skip (elements already classified).

  var CLASSIFY_TYPES = [
    { type: 'Wall',    ifcClass: 'IfcWall',    disc: 'ARC', color: 0x4488ff,
      test: function(w, h, d) { return h > 2 * Math.max(w, d) && Math.min(w, d) < 0.5; },
      reason: 'tall, thin, vertical' },
    { type: 'Slab',    ifcClass: 'IfcSlab',    disc: 'ARC', color: 0x888888,
      test: function(w, h, d) { return h < 0.5 && w * d > 5; },
      reason: 'flat, wide footprint' },
    { type: 'Column',  ifcClass: 'IfcColumn',  disc: 'STR', color: 0x44bb44,
      test: function(w, h, d) { return h > 2 && w < 0.5 && d < 0.5; },
      reason: 'tall, narrow' },
    { type: 'Roof',    ifcClass: 'IfcRoof',    disc: 'ARC', color: 0xbb6622,
      test: function(w, h, d, z, maxZ) { return z > maxZ * 0.7 && w * d > 3; },
      reason: 'top elevation, wide' },
    { type: 'Beam',    ifcClass: 'IfcBeam',    disc: 'STR', color: 0xcc8844,
      test: function(w, h, d) {
        var dims = [w, h, d].sort(function(a, b) { return b - a; });
        return dims[0] > 3 * dims[1] && h > 0.5;
      },
      reason: 'horizontal, elongated' },
  ];

  var _classPool = [];      // [{guid, w, h, d, z}] — unclassified elements
  var _classGuesses = [];   // [{type, ifcClass, disc, color, guids, reason}] from classifyPool
  var _classIdx = 0;        // current guess index
  var _classHighlighted = [];  // [{mesh, origMaterial}] for reverting classify highlight

  // Compute per-element bbox dimensions from component_geometries vertices BLOB
  function computeElementBboxes(db) {
    var bboxes = {};  // guid → {w, h, d, z}
    try {
      // Get geometry hash → bbox from vertices BLOB
      var hashBbox = {};
      var r = db.exec("SELECT geometry_hash, vertices FROM component_geometries WHERE vertices IS NOT NULL");
      if (r.length) {
        for (var i = 0; i < r[0].values.length; i++) {
          var hash = r[0].values[i][0];
          var blob = r[0].values[i][1];
          if (!blob || blob.length < 12) continue;
          var verts = new Float32Array(blob.buffer, blob.byteOffset, blob.byteLength / 4);
          var minX = Infinity, maxX = -Infinity;
          var minY = Infinity, maxY = -Infinity;
          var minZ = Infinity, maxZ = -Infinity;
          for (var j = 0; j < verts.length; j += 3) {
            if (verts[j] < minX) minX = verts[j];
            if (verts[j] > maxX) maxX = verts[j];
            if (verts[j + 1] < minY) minY = verts[j + 1];
            if (verts[j + 1] > maxY) maxY = verts[j + 1];
            if (verts[j + 2] < minZ) minZ = verts[j + 2];
            if (verts[j + 2] > maxZ) maxZ = verts[j + 2];
          }
          hashBbox[hash] = {
            w: maxX - minX,
            h: maxY - minY,
            d: maxZ - minZ,
          };
        }
      }
      // Map guid → bbox + position
      r = db.exec("SELECT ei.guid, ei.geometry_hash, t.center_z FROM element_instances ei JOIN element_transforms t ON t.guid = ei.guid");
      if (r.length) {
        for (var i = 0; i < r[0].values.length; i++) {
          var guid = r[0].values[i][0];
          var hash = r[0].values[i][1];
          var z = r[0].values[i][2] || 0;
          var bb = hashBbox[hash];
          if (bb) {
            bboxes[guid] = { w: bb.w, h: bb.h, d: bb.d, z: z };
          }
        }
      }
    } catch(e) {
      console.log('[S234] §CLASSIFY_BBOX_ERR ' + e.message);
    }
    return bboxes;
  }

  // Run all heuristics on pool, return sorted guesses
  function classifyPool(pool, maxZ) {
    var guesses = [];
    for (var t = 0; t < CLASSIFY_TYPES.length; t++) {
      var ct = CLASSIFY_TYPES[t];
      var matched = [];
      for (var i = 0; i < pool.length; i++) {
        var el = pool[i];
        if (ct.test(el.w, el.h, el.d, el.z, maxZ)) {
          matched.push(el.guid);
        }
      }
      if (matched.length > 0) {
        guesses.push({
          type: ct.type,
          ifcClass: ct.ifcClass,
          disc: ct.disc,
          color: ct.color,
          guids: matched,
          reason: ct.reason,
          confidence: matched.length,
        });
      }
    }
    // Sort by confidence (count) descending — best guess first
    guesses.sort(function(a, b) { return b.confidence - a.confidence; });
    return guesses;
  }

  // Apply highlight to guessed elements, dim everything else
  function applyClassifyHighlight(guids, color) {
    revertClassifyHighlight();
    if (typeof APP === 'undefined' || !APP.scene) return;

    var guidSet = {};
    for (var i = 0; i < guids.length; i++) guidSet[guids[i]] = true;

    var hlMat = new THREE.MeshStandardMaterial({
      color: color, roughness: 0.5, metalness: 0.1,
    });
    var dimMat = new THREE.MeshStandardMaterial({
      color: 0x333333, transparent: true, opacity: 0.2, roughness: 1.0,
    });

    APP.scene.traverse(function(obj) {
      if (!obj.isMesh || !obj.userData.guid) return;
      _classHighlighted.push({ mesh: obj, origMaterial: obj.material });
      obj.material = guidSet[obj.userData.guid] ? hlMat : dimMat;
    });
  }

  function revertClassifyHighlight() {
    for (var i = 0; i < _classHighlighted.length; i++) {
      _classHighlighted[i].mesh.material = _classHighlighted[i].origMaterial;
    }
    _classHighlighted = [];
  }

  // Initialize the classify step — build pool from unclassified elements
  function initClassifyPool() {
    var db = wizState.db;
    if (!db) return;

    // Get all IfcBuildingElementProxy GUIDs (unclassified)
    var proxyGuids = {};
    try {
      var r = db.exec("SELECT guid FROM elements_meta WHERE ifc_class = 'IfcBuildingElementProxy' OR ifc_class IS NULL");
      if (r.length) {
        for (var i = 0; i < r[0].values.length; i++) proxyGuids[r[0].values[i][0]] = true;
      }
    } catch(e) { return; }

    // Compute bboxes and build pool
    var bboxes = computeElementBboxes(db);
    _classPool = [];
    for (var guid in proxyGuids) {
      var bb = bboxes[guid];
      if (bb) {
        _classPool.push({ guid: guid, w: bb.w, h: bb.h, d: bb.d, z: bb.z });
      }
    }

    var maxZ = wizState.analysis ? wizState.analysis.maxZ : 0;
    _classGuesses = classifyPool(_classPool, maxZ);
    _classIdx = 0;

    console.log('[S234] §CLASSIFY_INIT pool=' + _classPool.length +
      ' guesses=' + _classGuesses.length +
      (_classGuesses.length > 0 ? ' best=' + _classGuesses[0].type + '(' + _classGuesses[0].guids.length + ')' : ''));
  }

  // Render the current classify guess
  function renderClassifyStep() {
    var panel = document.getElementById('wizard-panel');
    if (!panel) return;

    if (_classPool.length === 0 || _classGuesses.length === 0) {
      // Pool empty or no guesses — all remaining become Unknown
      if (_classPool.length > 0) {
        markRemainingUnknown();
      }
      revertClassifyHighlight();
      advanceStep();
      return;
    }

    var guess = _classGuesses[_classIdx % _classGuesses.length];
    var colorHex = '#' + guess.color.toString(16).padStart(6, '0');

    panel.querySelector('#wizard-question').innerHTML =
      guess.guids.length + ' elements look like <b style="color:' + colorHex + '">' + guess.type + 's</b>';
    panel.querySelector('#wizard-evidence').innerHTML =
      '<span style="display:inline-block;width:10px;height:10px;border-radius:50%;background:' + colorHex + ';margin-right:6px;vertical-align:middle"></span>' +
      guess.reason + '<br>' +
      '<span style="font-size:10px;color:#888">' + _classPool.length + ' unclassified remaining</span>';
    panel.querySelector('#wizard-buttons').innerHTML =
      '<button class="wizard-yes" onclick="window._wizClassifyYes()">Yes</button>' +
      '<button class="wizard-alt" onclick="window._wizClassifyToggle()">Toggle</button>';

    // Highlight guessed elements in 3D
    applyClassifyHighlight(guess.guids, guess.color);
  }

  // Yes — confirm current guess, drain pool, save immediately
  window._wizClassifyYes = function() {
    if (_classGuesses.length === 0) return;
    var guess = _classGuesses[_classIdx % _classGuesses.length];
    var db = wizState.db;
    if (!db) return;

    // UPDATE elements_meta
    var stmt = db.prepare("UPDATE elements_meta SET ifc_class = ?, discipline = ? WHERE guid = ?");
    for (var i = 0; i < guess.guids.length; i++) {
      stmt.run([guess.ifcClass, guess.disc, guess.guids[i]]);
    }
    stmt.free();

    console.log('[S234] §CLASSIFY_YES type=' + guess.type + ' count=' + guess.guids.length +
      ' class=' + guess.ifcClass + ' disc=' + guess.disc);

    // Drain pool — remove confirmed guids
    var confirmed = {};
    for (var i = 0; i < guess.guids.length; i++) confirmed[guess.guids[i]] = true;
    _classPool = _classPool.filter(function(el) { return !confirmed[el.guid]; });

    // Save immediately to IndexedDB (crash-safe incremental progress)
    saveClassifyProgress();

    // Re-classify remainder
    var maxZ = wizState.analysis ? wizState.analysis.maxZ : 0;
    _classGuesses = classifyPool(_classPool, maxZ);
    _classIdx = 0;

    // Re-analyse for updated summary
    wizState.analysis = analyseDb(db);

    // Render next guess or advance
    renderClassifyStep();
  };

  // Toggle — cycle to next guess type
  window._wizClassifyToggle = function() {
    if (_classGuesses.length === 0) return;
    _classIdx++;
    // If cycled through all guesses, remaining become Unknown
    if (_classIdx >= _classGuesses.length) {
      console.log('[S234] §CLASSIFY_TOGGLE_EXHAUSTED all types cycled, remaining=' + _classPool.length);
      markRemainingUnknown();
      revertClassifyHighlight();
      advanceStep();
      return;
    }
    console.log('[S234] §CLASSIFY_TOGGLE idx=' + _classIdx + ' type=' + _classGuesses[_classIdx].type);
    renderClassifyStep();
  };

  function markRemainingUnknown() {
    var db = wizState.db;
    if (!db || _classPool.length === 0) return;
    // Already IfcBuildingElementProxy — just ensure discipline is set
    var stmt = db.prepare("UPDATE elements_meta SET discipline = 'ARC' WHERE guid = ? AND (discipline IS NULL OR discipline = '')");
    for (var i = 0; i < _classPool.length; i++) {
      stmt.run([_classPool[i].guid]);
    }
    stmt.free();
    _classPool = [];
    saveClassifyProgress();
    console.log('[S234] §CLASSIFY_REMAINING_UNKNOWN');
  }

  // Save DB to IndexedDB after each Yes (incremental, crash-safe)
  function saveClassifyProgress() {
    var db = wizState.db;
    var projectKey = wizState.projectKey;
    if (!db || !projectKey) return;
    try {
      var dbData = db.export();
      var dbBuf = dbData.buffer;
      var req = indexedDB.open('bim_ootb_imports', 2);
      req.onsuccess = function() {
        var idb = req.result;
        if (!idb.objectStoreNames.contains('buildings')) return;
        var tx = idb.transaction('buildings', 'readwrite');
        var store = tx.objectStore('buildings');
        var getReq = store.get(projectKey);
        getReq.onsuccess = function() {
          var record = getReq.result;
          if (record && record.versions) {
            record.versions[record.latestVersion || 0].db = dbBuf;
            store.put(record, projectKey);
          }
        };
        tx.oncomplete = function() {
          console.log('[S234] §CLASSIFY_SAVE key=' + projectKey);
        };
      };
    } catch(e) {
      console.log('[S234] §CLASSIFY_SAVE_ERR ' + e.message);
    }
  }

  // ── Picker mode — click mesh to reassign disc/class ──
  var _pickerActive = false;
  var _pickerClickHandler = null;

  // Disc → IFC class mapping for dropdown
  var DISC_TO_CLASSES = {
    ARC:  ['IfcWall','IfcDoor','IfcWindow','IfcSlab','IfcRoof','IfcCovering','IfcStairFlight','IfcRailing','IfcRamp','IfcCurtainWall','IfcFurnishingElement'],
    STR:  ['IfcColumn','IfcBeam','IfcFooting','IfcPile','IfcSlab'],
    MEP:  ['IfcPipeSegment','IfcDuctSegment','IfcCableSegment'],
    ELEC: ['IfcCableSegment','IfcLightFixture','IfcOutlet','IfcElectricAppliance'],
    PLB:  ['IfcPipeSegment','IfcSanitaryTerminal'],
    ACMV: ['IfcDuctSegment'],
    FP:   ['IfcFireSuppressionTerminal'],
    NONE: ['IfcBuildingElementProxy'],
  };

  function enterPickerMode() {
    _pickerActive = true;
    var panel = document.getElementById('wizard-panel');
    if (panel) {
      panel.querySelector('#wizard-question').textContent = 'Click any element to inspect';
      panel.querySelector('#wizard-evidence').innerHTML = 'Click a mesh in the 3D view.<br>Its classification will appear here.';
      panel.querySelector('#wizard-buttons').innerHTML =
        '<button class="wizard-no" onclick="window._wizardExitPicker()">Done</button>';
    }
    console.log('[S230b] §WIZARD_PICKER_ENTER');

    // Install click handler on canvas
    if (typeof APP !== 'undefined' && APP.canvas) {
      _pickerClickHandler = function(e) { handlePickerClick(e); };
      APP.canvas.addEventListener('pointerup', _pickerClickHandler);
    }
  }

  window._wizardExitPicker = function() {
    _pickerActive = false;
    if (_pickerClickHandler && typeof APP !== 'undefined' && APP.canvas) {
      APP.canvas.removeEventListener('pointerup', _pickerClickHandler);
      _pickerClickHandler = null;
    }
    // Remove any pick highlight
    if (window._wizPickHL) {
      window._wizPickHL.parent.remove(window._wizPickHL);
      window._wizPickHL = null;
    }
    console.log('[S230b] §WIZARD_PICKER_EXIT');
    advanceStep();
  };

  function handlePickerClick(e) {
    if (!_pickerActive || !wizState.db) return;
    if (typeof APP === 'undefined' || !APP.scene || !APP.camera) return;

    var mouse = new THREE.Vector2();
    mouse.x = (e.clientX / window.innerWidth) * 2 - 1;
    mouse.y = -(e.clientY / window.innerHeight) * 2 + 1;
    var raycaster = new THREE.Raycaster();
    raycaster.setFromCamera(mouse, APP.camera);

    var meshes = [];
    APP.scene.traverse(function(o) { if (o.isMesh && o.visible) meshes.push(o); });
    var hits = raycaster.intersectObjects(meshes, false);
    if (!hits.length) return;

    var mesh = hits[0].object;
    var guid = (APP.guidMap && APP.guidMap[mesh.id]) || mesh.userData.guid;
    if (!guid) return;

    // Highlight picked mesh
    if (window._wizPickHL) {
      window._wizPickHL.parent.remove(window._wizPickHL);
      window._wizPickHL = null;
    }
    mesh.geometry.computeBoundingBox();
    var bb = mesh.geometry.boundingBox;
    var sz = new THREE.Vector3(); bb.getSize(sz);
    var ct = new THREE.Vector3(); bb.getCenter(ct);
    var hlGeo = new THREE.BoxGeometry(sz.x, sz.y, sz.z);
    var hlEdges = new THREE.EdgesGeometry(hlGeo);
    var hlLine = new THREE.LineSegments(hlEdges, new THREE.LineBasicMaterial({ color: 0xffff00 }));
    hlLine.position.copy(ct);
    mesh.add(hlLine);
    window._wizPickHL = hlLine;

    // Query element info
    try {
      var r = wizState.db.exec("SELECT element_name, ifc_class, discipline, storey FROM elements_meta WHERE guid = '" + guid + "'");
      if (!r.length || !r[0].values.length) return;
      var row = r[0].values[0];
      var elName = row[0] || 'Unknown';
      var curClass = row[1] || 'IfcBuildingElementProxy';
      var curDisc = row[2] || 'ARC';
      var curStorey = row[3] || '';

      showPickerPanel(guid, elName, curClass, curDisc, curStorey);
    } catch(ex) {
      console.log('[S230b] §WIZARD_PICKER_ERR ' + ex.message);
    }
  }

  function showPickerPanel(guid, elName, curClass, curDisc, curStorey) {
    var panel = document.getElementById('wizard-panel');
    if (!panel) return;

    // Build disc dropdown
    var discOpts = Object.keys(DISC_TO_CLASSES).map(function(d) {
      var sel = (d === curDisc) ? ' selected' : '';
      return '<option value="' + d + '"' + sel + '>' + d + '</option>';
    }).join('');

    // Build class dropdown for current discipline
    var classOpts = buildClassOptions(curDisc, curClass);

    panel.querySelector('#wizard-question').innerHTML =
      '<span style="font-size:13px;color:#ffe0a0">' + elName + '</span>';
    panel.querySelector('#wizard-evidence').innerHTML =
      '<span style="color:#888">Storey:</span> ' + (curStorey || '—') + '<br>' +
      '<div style="margin-top:8px">' +
        '<label style="font-size:11px;color:#888">Discipline</label>' +
        '<select id="wiz-disc-sel" onchange="window._wizDiscChanged()" style="width:100%;padding:6px;background:rgba(0,0,0,0.3);color:#ffe0a0;border:1px solid rgba(255,191,0,0.2);border-radius:4px;font-size:12px;margin:4px 0">' + discOpts + '</select>' +
      '</div>' +
      '<div>' +
        '<label style="font-size:11px;color:#888">IFC Class</label>' +
        '<select id="wiz-class-sel" style="width:100%;padding:6px;background:rgba(0,0,0,0.3);color:#ffe0a0;border:1px solid rgba(255,191,0,0.2);border-radius:4px;font-size:12px;margin:4px 0">' + classOpts + '</select>' +
      '</div>';
    panel.querySelector('#wizard-buttons').innerHTML =
      '<button class="wizard-yes" onclick="window._wizPickerApply(\'' + guid + '\')">Apply</button>' +
      '<button class="wizard-no" onclick="window._wizardExitPicker()">Done</button>';

    // Store guid for disc change handler
    wizState._pickerGuid = guid;
  }

  function buildClassOptions(disc, curClass) {
    var classes = DISC_TO_CLASSES[disc] || ['IfcBuildingElementProxy'];
    return classes.map(function(c) {
      var sel = (c === curClass) ? ' selected' : '';
      var label = c.replace('Ifc', '');
      return '<option value="' + c + '"' + sel + '>' + label + '</option>';
    }).join('');
  }

  window._wizDiscChanged = function() {
    var discSel = document.getElementById('wiz-disc-sel');
    var classSel = document.getElementById('wiz-class-sel');
    if (!discSel || !classSel) return;
    var disc = discSel.value;
    // Get current class from DB for this guid to try to preserve selection
    classSel.innerHTML = buildClassOptions(disc, '');
  };

  window._wizPickerApply = function(guid) {
    if (!wizState.db) return;
    var discSel = document.getElementById('wiz-disc-sel');
    var classSel = document.getElementById('wiz-class-sel');
    if (!discSel || !classSel) return;

    var newDisc = discSel.value;
    var newClass = classSel.value;

    wizState.db.run("UPDATE elements_meta SET discipline = ?, ifc_class = ? WHERE guid = ?",
      [newDisc === 'NONE' ? null : newDisc, newClass, guid]);

    console.log('[S230b] §WIZARD_PICKER_APPLY guid=' + guid + ' disc=' + newDisc + ' class=' + newClass);

    // Recolor the mesh
    applyDisciplineColors(wizState.db);

    // Re-analyse for updated summary
    wizState.analysis = analyseDb(wizState.db);

    // Show confirmation, stay in picker mode
    var panel = document.getElementById('wizard-panel');
    if (panel) {
      panel.querySelector('#wizard-question').textContent = 'Updated. Click another element or Done.';
      panel.querySelector('#wizard-evidence').innerHTML =
        'Changed to <b>' + newDisc + ' / ' + newClass.replace('Ifc','') + '</b>';
      panel.querySelector('#wizard-buttons').innerHTML =
        '<button class="wizard-no" onclick="window._wizardExitPicker()">Done</button>';
    }
  };

  function finishWizard() {
    // S234: Clean up classify highlight
    revertClassifyHighlight();
    // S230b: Clean up picker if active
    if (_pickerActive) {
      _pickerActive = false;
      if (_pickerClickHandler && typeof APP !== 'undefined' && APP.canvas) {
        APP.canvas.removeEventListener('pointerup', _pickerClickHandler);
        _pickerClickHandler = null;
      }
    }
    if (window._wizPickHL) {
      window._wizPickHL.parent.remove(window._wizPickHL);
      window._wizPickHL = null;
    }

    // S230b: Mark wizard complete in DB BEFORE export
    markWizardComplete(wizState.db);

    // Export DB and analyse BEFORE any async saves
    var dbData = wizState.db.export();
    var dbBuf = dbData.buffer;
    var finalAnalysis = analyseDb(wizState.db);
    var projectKey = wizState.projectKey;

    console.log('[S229] §WIZARD_COMPLETE steps=' + wizState.steps.length +
      ' project=' + projectKey);

    // Close DB now — we have the buffer and analysis
    if (wizState.db) { wizState.db.close(); wizState.db = null; }

    // S230b: Revert storey highlighting but KEEP discipline colors (building shouldn't go white)
    revertStoreyHighlight();

    // Show "Saving..." in panel while async writes complete
    var panel = document.getElementById('wizard-panel');
    if (panel) {
      panel.querySelector('#wizard-question').textContent = 'Saving\u2026';
      panel.querySelector('#wizard-evidence').textContent = '';
      panel.querySelector('#wizard-buttons').innerHTML = '';
    }

    // Count pending saves to know when all are done
    var savesPending = 0;
    var savesComplete = 0;
    function onSaveDone(label) {
      savesComplete++;
      console.log('[S230] §WIZARD_SAVE ' + label + ' (' + savesComplete + '/' + savesPending + ')');
      if (savesComplete >= savesPending) {
        // S230b: Dismiss panel AFTER all saves complete
        dismissPanel();
        if (wizState.onComplete) wizState.onComplete();
      }
    }

    function dismissPanel() {
      var p = document.getElementById('wizard-panel');
      if (p) {
        p.style.opacity = '0';
        p.style.transform = 'translateY(-50%) translateX(20px)';
        setTimeout(function() { p.remove(); }, 300);
      }
    }

    // Save 1: Update import project record (both landing and viewer context)
    if (projectKey) {
      savesPending++;
      try {
        var importReq = indexedDB.open('bim_ootb_imports', 2);
        importReq.onsuccess = function() {
          var importDb = importReq.result;
          if (!importDb.objectStoreNames.contains('buildings')) { onSaveDone('import-skip'); return; }
          var tx = importDb.transaction('buildings', 'readwrite');
          var store = tx.objectStore('buildings');
          var getReq = store.get(projectKey);
          getReq.onsuccess = function() {
            var record = getReq.result;
            if (record && record.versions) {
              record.versions[record.latestVersion || 0].db = dbBuf;
              record.meta.disciplines = finalAnalysis.disciplines;
              record.meta.storeys = finalAnalysis.storeys;
              record.meta.wizard_complete = true;  // S230b: prevent re-entry from landing
              store.put(record, projectKey);
              console.log('[S230] §WIZARD_IMPORT_SAVED key=' + projectKey + ' size=' + (dbBuf.byteLength/1024).toFixed(0) + 'KB');
            }
          };
          tx.oncomplete = function() { onSaveDone('import'); };
          tx.onerror = function() { onSaveDone('import-err'); };
        };
        importReq.onerror = function() { onSaveDone('import-open-err'); };
      } catch(e) { onSaveDone('import-catch'); }
    }

    // Save 2: Update viewer cache (so next Open loads modified DB)
    // S230b: Save to BOTH the import:// key AND the viewer's actual DB URL
    if (projectKey) {
      savesPending++;
      try {
        var cacheReq = indexedDB.open('bim_ootb_cache', 1);
        cacheReq.onsuccess = function() {
          var cacheDb = cacheReq.result;
          if (!cacheDb.objectStoreNames.contains('dbs')) { onSaveDone('cache-skip'); return; }
          var tx = cacheDb.transaction('dbs', 'readwrite');
          var store = tx.objectStore('dbs');
          // Standard import key
          var dbKey = 'import://' + projectKey + '/v0';
          store.put(dbBuf, dbKey);
          // Also save to the viewer's actual DB URL (may differ from import:// key)
          try {
            var viewerDbUrl = new URLSearchParams(location.search).get('db');
            if (viewerDbUrl && viewerDbUrl !== dbKey) {
              store.put(dbBuf, viewerDbUrl);
              console.log('[S230b] §WIZARD_CACHE_VIEWER_URL key=' + viewerDbUrl);
            }
          } catch(e2) { /* ignore — non-critical */ }
          tx.oncomplete = function() {
            console.log('[S230] §WIZARD_CACHE_SAVED key=' + dbKey);
            onSaveDone('cache');
          };
          tx.onerror = function() { onSaveDone('cache-err'); };
        };
        cacheReq.onerror = function() { onSaveDone('cache-open-err'); };
      } catch(e) { onSaveDone('cache-catch'); }
    }

    // If no saves pending, dismiss immediately
    if (savesPending === 0) {
      dismissPanel();
      if (wizState.onComplete) wizState.onComplete();
    }

    // S230b: Safety timeout — dismiss panel even if IndexedDB saves hang
    setTimeout(function() {
      var p = document.getElementById('wizard-panel');
      if (p) {
        console.log('[S230b] §WIZARD_SAVE_TIMEOUT forcing dismiss');
        dismissPanel();
        if (wizState.onComplete) wizState.onComplete();
      }
    }, 5000);
  }

  // ── Check if wizard already completed for this DB ──
  function isWizardComplete(db) {
    try {
      var r = db.exec("SELECT value FROM project_metadata WHERE key = 'wizard_complete'");
      return r.length > 0 && r[0].values.length > 0 && r[0].values[0][0] === '1';
    } catch(e) {
      return false;  // table doesn't exist = not complete
    }
  }

  // ── Mark wizard complete in DB ──
  function markWizardComplete(db) {
    db.run("CREATE TABLE IF NOT EXISTS project_metadata (key TEXT PRIMARY KEY, value TEXT)");
    db.run("INSERT OR REPLACE INTO project_metadata (key, value) VALUES ('wizard_complete', '1')");
    console.log('[S230b] §WIZARD_MARK_COMPLETE');
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

    // S230b: Skip wizard if already completed for this project
    if (isWizardComplete(db)) {
      // S233: Restore scene rotation if DB was flipped (Bug 4 double-flip prevention)
      try {
        var orientRow = db.exec("SELECT value FROM project_metadata WHERE key = 'orientation'");
        if (orientRow.length > 0 && orientRow[0].values[0][0] === 'z_up' &&
            typeof APP !== 'undefined' && APP.scene) {
          APP.scene.rotation.x = -Math.PI / 2;
          APP.scene.updateMatrixWorld(true);
          console.log('[S233] §WIZARD_RESTORE_FLIP orientation=z_up, scene.rotation.x=-1.571');
        }
      } catch(e) { /* project_metadata may not exist */ }
      console.log('[S230b] §WIZARD_SKIP_COMPLETE project=' + projectKey);
      db.close();
      if (onComplete) onComplete();
      return;
    }

    wizState.db = db;
    wizState.projectKey = projectKey;
    wizState.meta = meta;
    wizState.onComplete = onComplete || null;

    // S230b: Re-classify storeys using dynamic bands (import may have used fixed bands)
    // Detect Y-up vs Z-up: if Y range > Z range, model is Y-up (OBJ/GLB default)
    var orientR = db.exec("SELECT MAX(center_y)-MIN(center_y), MAX(center_z)-MIN(center_z) FROM element_transforms");
    var yRange = (orientR.length && orientR[0].values[0][0]) || 0;
    var zRange = (orientR.length && orientR[0].values[0][1]) || 0;
    wizState._heightAxis = (yRange > zRange * 1.5) ? 'y' : 'z';
    reclassifyStoreys(db, wizState._heightAxis);

    wizState.analysis = analyseDb(db);
    wizState.steps = buildSteps(wizState.analysis);
    wizState.stepIdx = 0;

    console.log('[S229] §WIZARD_START project=' + projectKey +
      ' elements=' + wizState.analysis.totalElements +
      ' steps=' + wizState.steps.length);

    // S230b: Apply discipline colors + camera clipping once meshes are in scene
    // Meshes may not be streamed yet, so poll until they appear
    var _colorRetries = 0;
    function tryApplyVisuals() {
      if (typeof APP === 'undefined' || !APP.scene) return;
      var meshCount = 0;
      APP.scene.traverse(function(o) { if (o.isMesh && o.userData.guid) meshCount++; });
      if (meshCount > 0) {
        applyDisciplineColors(wizState.db);
        reframeCameraToBbox();
        return;
      }
      _colorRetries++;
      if (_colorRetries < 30) setTimeout(tryApplyVisuals, 500);  // poll up to 15s
    }
    tryApplyVisuals();

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
