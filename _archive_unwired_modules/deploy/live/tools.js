// tools.js — X-Ray, wireframe, section cut, screenshot, fullscreen, theme, 4D/5D export
function setupTools(A) {
  // Wireframe
  A.wireOn = false;
  A.toggleWireframe = function() {
    A.wireOn = !A.wireOn;
    const btn = document.getElementById('wire-btn');
    btn.style.background = A.wireOn ? '#4fc3f7' : '#444';
    btn.style.color = A.wireOn ? '#000' : '#fff';
    A.collectMeshes(o => o.isMesh).forEach(obj => {
      obj.material.wireframe = A.wireOn;
      obj.material.needsUpdate = true;
    });
  };

  // X-Ray
  A.xrayOn = false;
  A.toggleXray = function() {
    A.xrayOn = !A.xrayOn;
    const btn = document.getElementById('xray-btn');
    btn.style.background = A.xrayOn ? '#4fc3f7' : '#444';
    btn.style.color = A.xrayOn ? '#000' : '#fff';
    A.collectMeshes(o => o.isMesh).forEach(obj => {
      const mat = obj.material;
      if (A.xrayOn) {
        // Save originals before modifying
        if (mat.userData.origOpacity === undefined) mat.userData.origOpacity = mat.opacity;
        if (mat.userData.origTransparent === undefined) mat.userData.origTransparent = mat.transparent;
        if (mat.userData.origSide === undefined) mat.userData.origSide = mat.side;
        mat.transparent = true;
        mat.opacity = 0.15;
        mat.side = THREE.DoubleSide;
      } else {
        // Restore originals — only if we saved them (skip late-streamed meshes)
        if (mat.userData.origOpacity !== undefined) {
          mat.opacity = mat.userData.origOpacity;
          mat.transparent = mat.userData.origTransparent;
          mat.side = mat.userData.origSide;
          delete mat.userData.origOpacity;
          delete mat.userData.origTransparent;
          delete mat.userData.origSide;
        }
      }
      mat.needsUpdate = true;
    });
    console.log(`[S200] §XRAY ${A.xrayOn ? 'ON' : 'OFF'}`);
  };

  // Section Cut
  A.sectionOn = false;
  A.sectionAxis = 'Y';
  A.sectionPlane = new THREE.Plane(new THREE.Vector3(0, -1, 0), 0);
  A.sectionMin = -100;
  A.sectionMax = 200;

  A.toggleSection = function() {
    A.sectionOn = !A.sectionOn;
    const btn = document.getElementById('section-btn');
    btn.style.background = A.sectionOn ? '#4fc3f7' : '#444';
    btn.style.color = A.sectionOn ? '#000' : '#fff';
    const panel = document.getElementById('section-slider-panel');
    panel.style.display = A.sectionOn ? 'block' : 'none';
    if (A.sectionOn) {
      A.applySectionAxis();
    } else {
      A.renderer.localClippingEnabled = false;
      A.collectMeshes(o => o.isMesh).forEach(obj => {
        obj.material.clippingPlanes = [];
        obj.material.needsUpdate = true;
      });
      console.log('[S205] §SECTION OFF');
      if (A.onSectionOff) A.onSectionOff();
    }
  };

  A.setSectionAxis = function(axis) {
    A.sectionAxis = axis;
    ['X', 'Y', 'Z'].forEach(a => {
      const b = document.getElementById('sec-axis-' + a.toLowerCase());
      b.style.background = (a === axis) ? '#4fc3f7' : '#444';
      b.style.color = (a === axis) ? '#000' : '#fff';
    });
    if (axis === 'Y') A.sectionPlane.normal.set(0, -1, 0);
    else if (axis === 'X') A.sectionPlane.normal.set(-1, 0, 0);
    else A.sectionPlane.normal.set(0, 0, -1);
    if (A.sectionOn) A.applySectionAxis();
  };

  A.applySectionAxis = function() {
    let axMin = Infinity, axMax = -Infinity;
    A.collectMeshes(o => o.isMesh).forEach(obj => {
      const box = new THREE.Box3().setFromObject(obj);
      if (A.sectionAxis === 'Y') { axMin = Math.min(axMin, box.min.y); axMax = Math.max(axMax, box.max.y); }
      else if (A.sectionAxis === 'X') { axMin = Math.min(axMin, box.min.x); axMax = Math.max(axMax, box.max.x); }
      else { axMin = Math.min(axMin, box.min.z); axMax = Math.max(axMax, box.max.z); }
    });
    if (!isFinite(axMin)) { axMin = -100; axMax = 200; }
    A.sectionMin = axMin;
    A.sectionMax = axMax;
    const slider = document.getElementById('section-slider');
    slider.min = axMin.toFixed(1);
    slider.max = axMax.toFixed(1);
    slider.step = ((axMax - axMin) / 500).toFixed(3);
    slider.value = axMax.toFixed(1);
    A.sectionPlane.constant = axMax;
    A.renderer.localClippingEnabled = true;
    A.collectMeshes(o => o.isMesh).forEach(obj => {
      obj.material.clippingPlanes = [A.sectionPlane];
      obj.material.clipShadows = true;
      obj.material.needsUpdate = true;
    });
    document.getElementById('section-val').textContent = axMax.toFixed(1) + ' m';
    console.log(`[S205] §SECTION ON axis=${A.sectionAxis} range=[${axMin.toFixed(1)}, ${axMax.toFixed(1)}]`);
  };

  A.updateSectionPlane = function(val) {
    const v = parseFloat(val);
    A.sectionPlane.constant = v;
    document.getElementById('section-val').textContent = v.toFixed(1) + ' m';
    if (A.onSectionSliderChange) A.onSectionSliderChange(v);
  };

  // 4D/5D Export
  A.export4D5D = function() {
    if (!A.db || !A.activeBuilding) { A.status.textContent = typeof _TRL!=='undefined'&&_TRL.ui_select_building||'Select a building first.'; return; }
    const bld = A.activeBuilding;
    const dbParam = new URLSearchParams(location.search).get('db') || 'yourproject_extracted.db';
    // S223: import:// URLs → boq_charts co-located (../boq_charts.html from sandbox/)
    // OCI URLs → strip to /o/ base
    // S224: pass diffdb to boq_charts when viewer has diff data
    var diffParam = '';
    var diffDbUrl = new URLSearchParams(location.search).get('diffdb');
    if (diffDbUrl) diffParam = '&diffdb=' + encodeURIComponent(diffDbUrl);

    var chartsUrl;
    var cacheBust = '&v=' + Date.now();
    if (dbParam.startsWith('import://')) {
      chartsUrl = '../boq_charts.html?db=' + encodeURIComponent(dbParam) + '&bld=' + bld + diffParam + cacheBust;
    } else {
      const base = location.href.split('?')[0].match(/(.*\/o\/)/)?.[1] || '../';
      chartsUrl = base + 'boq_charts.html?db=' + encodeURIComponent(dbParam) + '&bld=' + bld + diffParam + cacheBust;
    }
    window.open(chartsUrl, '_blank');
    A.status.textContent = (typeof _TRL!=='undefined'&&_TRL.ui_analytics_opened||'4D/5D analytics opened for {name}').replace('{name}', bld);
  };

  // Screenshot — in 2D grid mode, produces A3 print sheet with title block
  A.screenshot = function() {
    // If in 2D view and PrintSheet is available, use A3 print sheet
    if (typeof GridViews !== 'undefined' && GridViews.activeView() &&
        typeof PrintSheet !== 'undefined') {
      PrintSheet.capture(A);
      return;
    }
    // Fallback: regular screenshot
    A.renderer.render(A.scene, A.camera);
    const link = document.createElement('a');
    link.download = `BIM_OOTB_${new Date().toISOString().slice(0,19).replace(/:/g,'-')}.png`;
    link.href = A.canvas.toDataURL('image/png');
    link.click();
    const flash = document.createElement('div');
    flash.style.cssText = 'position:fixed;top:0;left:0;width:100vw;height:100vh;background:white;opacity:0.7;z-index:999;pointer-events:none';
    document.body.appendChild(flash);
    setTimeout(() => { flash.style.opacity = '0'; flash.style.transition = 'opacity 0.3s'; }, 50);
    setTimeout(() => document.body.removeChild(flash), 400);
    A.status.textContent = typeof _TRL!=='undefined'&&_TRL.ui_screenshot_saved||'Screenshot saved to Downloads/';
  };

  // Fullscreen
  A.toggleFullscreen = function() {
    if (!document.fullscreenElement) {
      document.documentElement.requestFullscreen();
    } else {
      document.exitFullscreen();
    }
  };

  // Theme — reverse background (light/dark)
  A.lightTheme = false;
  A.toggleTheme = function() {
    A.lightTheme = !A.lightTheme;
    const bg = A.lightTheme ? 0xf0f0f0 : 0x1a1a2e;
    const textColor = A.lightTheme ? '#222' : '#e0e0e0';
    const panelBg = A.lightTheme ? 'rgba(255,255,255,0.7)' : 'rgba(0,0,0,0.3)';
    const statColor = A.lightTheme ? '#555' : '#ccc';
    const boldColor = A.lightTheme ? '#000' : '#fff';
    document.body.style.background = '#' + bg.toString(16).padStart(6, '0');
    document.body.style.color = textColor;
    A.renderer.setClearColor(bg);
    A.ground.material.color.setHex(A.lightTheme ? 0xdddddd : 0x222233);
    document.querySelectorAll('#hud,#search-box,#info-panel,#storey-panel,#disc-panel,#status').forEach(el => {
      el.style.background = panelBg;
      el.style.borderColor = A.lightTheme ? 'rgba(0,0,0,0.15)' : 'rgba(255,255,255,0.08)';
    });
    document.querySelectorAll('.stat').forEach(el => el.style.color = statColor);
    document.querySelectorAll('.stat b').forEach(el => el.style.color = boldColor);
    document.querySelectorAll('#info-panel .label').forEach(el => el.style.color = A.lightTheme ? '#666' : '#888');
    document.querySelectorAll('#info-panel .value').forEach(el => el.style.color = A.lightTheme ? '#000' : '#fff');
    document.getElementById('status').style.color = A.lightTheme ? '#0077cc' : '#4fc3f7';
    document.querySelector('#hud h2').style.color = A.lightTheme ? '#0077cc' : '#4fc3f7';
    A.collectMeshes(o => o.isLineSegments && o.userData.building).forEach(obj => {
      obj.visible = !A.lightTheme;
    });
  };

  // Sunglasses — click: toggle slider, slider: recolor whites by IFC class
  A.sunglassOn = false;
  A._sunglassBackups = [];  // [{mesh, origMat}]

  A.toggleSunglass = function() {
    A.sunglassOn = !A.sunglassOn;
    // Reverse background
    A.toggleTheme();
    // Button highlight
    const btn = document.getElementById('sunglass-btn');
    btn.style.background = A.sunglassOn ? '#ff8c00' : '#444';
    btn.style.color = A.sunglassOn ? '#000' : '#fff';
    // Slider panel
    document.getElementById('sunglass-slider-panel').style.display = A.sunglassOn ? 'block' : 'none';
    if (!A.sunglassOn) {
      document.getElementById('sunglass-slider').value = 0;
      A._restoreSunglass();
    }
  };

  A._sunglassBackups = [];
  A._restoreSunglass = function() {
    A._sunglassBackups.forEach(b => { b.mesh.material = b.origMat; });
    A._sunglassBackups = [];
  };

  A._isWhiteMat = function(mat) {
    if (!mat || !mat.color) return false;
    return mat.color.r > 0.75 && mat.color.g > 0.75 && mat.color.b > 0.75;
  };

  // Generate a color for class index at given intensity
  // Uses golden-angle hue spacing so adjacent classes always contrast
  // Golden-angle hue for index — max contrast between neighbors
  A._goldenHue = function(idx) { return ((idx * 137.508) % 360) / 360; };

  A._collectAllMeshes = function() {
    var all = [];
    A.collectMeshes(function(o) { return o.isMesh && !o.userData.isInstanced; }).forEach(function(m) { all.push(m); });
    A.collectMeshes(function(o) { return o.isInstancedMesh; }).forEach(function(m) { all.push(m); });
    return all;
  };

  A._recolorMesh = function(mesh, color) {
    A._sunglassBackups.push({ mesh: mesh, origMat: mesh.material });
    var newMat = mesh.material.clone();
    newMat.color.copy(color);
    newMat.needsUpdate = true;
    mesh.material = newMat;
  };

  // Group helper
  A._groupBy = function(meshes, key) {
    var g = {};
    meshes.forEach(function(m) {
      var k = m.userData[key] || 'Unknown';
      if (!g[k]) g[k] = [];
      g[k].push(m);
    });
    return g;
  };

  A.updateAmbience = function(val) {
    var tick = Math.round(Number(val));
    A._restoreSunglass();
    if (tick === 0) {
      document.getElementById('sunglass-val').textContent = 'Off';
      console.log('[S200] §SUNGLASS off');
      return;
    }
    var allMeshes = A._collectAllMeshes();
    var label = document.getElementById('sunglass-val');
    var strategy, phase;

    // ── Professional warm/cool palettes (no purple) ──
    var warmPastel = [
      [0.05, 0.25, 0.82], [0.12, 0.25, 0.78], [0.08, 0.30, 0.75],  // peach, sand, cream
      [0.55, 0.20, 0.80], [0.42, 0.25, 0.76], [0.15, 0.22, 0.84],  // sage, olive, wheat
      [0.02, 0.20, 0.70], [0.58, 0.28, 0.72], [0.10, 0.35, 0.68],  // coral, teal, amber
      [0.48, 0.22, 0.78]                                              // moss
    ];
    var coolPastel = [
      [0.55, 0.30, 0.78], [0.62, 0.25, 0.75], [0.50, 0.35, 0.72],  // sky, steel, seafoam
      [0.45, 0.28, 0.80], [0.58, 0.32, 0.70], [0.68, 0.22, 0.76],  // mint, teal, slate
      [0.52, 0.25, 0.68], [0.60, 0.30, 0.74], [0.48, 0.35, 0.66],  // ocean, mist, stone
      [0.65, 0.28, 0.72]                                              // ice
    ];
    var earthTone = [
      [0.08, 0.45, 0.65], [0.05, 0.50, 0.55], [0.10, 0.40, 0.70],  // terracotta, sienna, tan
      [0.12, 0.55, 0.50], [0.15, 0.38, 0.60], [0.03, 0.48, 0.58],  // rust, clay, bronze
      [0.07, 0.42, 0.62], [0.55, 0.35, 0.58], [0.20, 0.50, 0.52],  // copper, olive, khaki
      [0.02, 0.60, 0.45]                                              // mahogany
    ];

    function applyPalette(groups, keys, palette, sub) {
      keys.forEach(function(k, i) {
        var p = palette[i % palette.length];
        var color = new THREE.Color().setHSL(p[0], p[1] + sub * 0.05, p[2] - sub * 0.03);
        groups[k].forEach(function(m) { A._recolorMesh(m, color); });
      });
    }

    if (tick <= 10) {
      // ── 1-10: Warm pastels by IFC class, subtle contrast growing ──
      phase = 'Warm';
      var g = A._groupBy(allMeshes, 'ifcClass');
      var keys = Object.keys(g).sort(function(a, b) { return g[b].length - g[a].length; });
      applyPalette(g, keys, warmPastel, tick - 1);
      strategy = keys.length + ' types';

    } else if (tick <= 20) {
      // ── 11-20: Cool pastels by IFC class ──
      phase = 'Cool';
      var g = A._groupBy(allMeshes, 'ifcClass');
      var keys = Object.keys(g).sort(function(a, b) { return g[b].length - g[a].length; });
      applyPalette(g, keys, coolPastel, tick - 11);
      strategy = keys.length + ' types';

    } else if (tick <= 30) {
      // ── 21-30: Earth tones by IFC class ──
      phase = 'Earth';
      var g = A._groupBy(allMeshes, 'ifcClass');
      var keys = Object.keys(g).sort(function(a, b) { return g[b].length - g[a].length; });
      applyPalette(g, keys, earthTone, tick - 21);
      strategy = keys.length + ' types';

    } else if (tick <= 45) {
      // ── 31-45: Warm pastels by storey ──
      phase = 'Storey warm';
      var g = A._groupBy(allMeshes, 'storey');
      var keys = Object.keys(g).sort();
      applyPalette(g, keys, warmPastel, tick - 31);
      strategy = keys.length + ' storeys';

    } else if (tick <= 55) {
      // ── 46-55: Cool pastels by storey ──
      phase = 'Storey cool';
      var g = A._groupBy(allMeshes, 'storey');
      var keys = Object.keys(g).sort();
      applyPalette(g, keys, coolPastel, tick - 46);
      strategy = keys.length + ' storeys';

    } else if (tick <= 65) {
      // ── 56-65: Earth by discipline ──
      phase = 'Discipline';
      var g = A._groupBy(allMeshes, 'disc');
      var keys = Object.keys(g).sort();
      applyPalette(g, keys, earthTone, tick - 56);
      strategy = keys.length + ' discs';

    } else if (tick <= 80) {
      // ── 66-80: Zebra — IFC class alternates warm/cool ──
      phase = 'Zebra';
      var g = A._groupBy(allMeshes, 'ifcClass');
      var keys = Object.keys(g).sort(function(a, b) { return g[b].length - g[a].length; });
      var t = (tick - 66) / 14;
      keys.forEach(function(k, i) {
        var w = warmPastel[i % warmPastel.length];
        var c = coolPastel[i % coolPastel.length];
        var warm = new THREE.Color().setHSL(w[0], w[1] + t * 0.15, w[2] - t * 0.05);
        var cool = new THREE.Color().setHSL(c[0], c[1] + t * 0.15, c[2] - t * 0.05);
        g[k].forEach(function(m, j) {
          A._recolorMesh(m, j % 2 === 0 ? warm : cool);
        });
      });
      strategy = keys.length + ' types';

    } else if (tick <= 90) {
      // ── 81-90: Monochrome — single hue, IFC class by lightness ──
      phase = 'Mono';
      var g = A._groupBy(allMeshes, 'ifcClass');
      var keys = Object.keys(g).sort(function(a, b) { return g[b].length - g[a].length; });
      var hue = ((tick - 81) / 9) * 0.15;  // cycle through warm hues only
      keys.forEach(function(k, i) {
        var l = 0.35 + (i / Math.max(keys.length - 1, 1)) * 0.45;
        var color = new THREE.Color().setHSL(hue, 0.4, l);
        g[k].forEach(function(m) { A._recolorMesh(m, color); });
      });
      strategy = keys.length + ' types';

    } else if (tick <= 97) {
      // ── 91-97: Random pastel per mesh ──
      phase = 'Random';
      allMeshes.forEach(function(m) {
        var h = Math.random();
        var color = new THREE.Color().setHSL(h, 0.3, 0.65 + Math.random() * 0.15);
        A._recolorMesh(m, color);
      });
      strategy = allMeshes.length + ' meshes';

    } else {
      // ── 98-100: HARD — full saturation, dark, punchy ──
      phase = 'HARD';
      var g = A._groupBy(allMeshes, 'ifcClass');
      var keys = Object.keys(g).sort(function(a, b) { return g[b].length - g[a].length; });
      keys.forEach(function(k, i) {
        var h = A._goldenHue(i);
        var color = new THREE.Color().setHSL(h, 1.0, 0.35);
        g[k].forEach(function(m) { A._recolorMesh(m, color); });
      });
      strategy = keys.length + ' types';
    }

    label.textContent = phase + ' — ' + strategy;
    console.log('[S200] §SUNGLASS tick=' + tick + ' phase=' + phase + ' ' + strategy);
  };

  // Hover highlight
  A.hoverHighlight = null;
  const hoverMouse = new THREE.Vector2();
  let lastHoverTime = 0;
  function onMouseMove(e) {
    const now = performance.now();
    if (now - lastHoverTime < 100) return;
    lastHoverTime = now;

    hoverMouse.x = (e.clientX / window.innerWidth) * 2 - 1;
    hoverMouse.y = -(e.clientY / window.innerHeight) * 2 + 1;
    A.raycaster.setFromCamera(hoverMouse, A.camera);

    const meshes = A.collectMeshes(o => o.isMesh && o.visible);
    const hits = A.raycaster.intersectObjects(meshes, false);

    if (A.hoverHighlight) {
      // S240: restore 4D phase colour if active, otherwise reset to black
      var _restoreHex = A.hoverHighlight._4dColor !== undefined ? A.hoverHighlight._4dColor : 0x000000;
      A.hoverHighlight.material.emissive.setHex(_restoreHex);
      A.hoverHighlight = null;
    }

    if (hits.length > 0 && A.guidMap[hits[0].object.id]) {
      A.hoverHighlight = hits[0].object;
      A.hoverHighlight.material.emissive.setHex(0x222222);
      A.canvas.style.cursor = 'pointer';
    } else {
      A.canvas.style.cursor = 'default';
    }
  }
  A.canvas.addEventListener('mousemove', onMouseMove);
}
