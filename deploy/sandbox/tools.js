// tools.js — X-Ray, wireframe, section cut, screenshot, fullscreen, theme, 4D/5D export
function setupTools(A) {
  // Wireframe
  A.wireOn = false;
  A.toggleWireframe = function() {
    A.wireOn = !A.wireOn;
    const btn = document.getElementById('wire-btn');
    btn.style.background = A.wireOn ? '#4fc3f7' : '#444';
    btn.style.color = A.wireOn ? '#000' : '#fff';
    A.scene.traverse(obj => {
      if (obj.isMesh && obj !== A.ground) {
        obj.material.wireframe = A.wireOn;
        obj.material.needsUpdate = true;
      }
    });
  };

  // X-Ray
  A.xrayOn = false;
  A.toggleXray = function() {
    A.xrayOn = !A.xrayOn;
    const btn = document.getElementById('xray-btn');
    btn.style.background = A.xrayOn ? '#4fc3f7' : '#444';
    btn.style.color = A.xrayOn ? '#000' : '#fff';
    A.scene.traverse(obj => {
      if (obj.isMesh && obj !== A.ground) {
        obj.material.transparent = true;
        obj.material.opacity = A.xrayOn ? 0.15 : (obj.material.userData.origOpacity ?? 1.0);
        obj.material.side = A.xrayOn ? THREE.DoubleSide : (obj.material.userData.origSide ?? THREE.FrontSide);
        obj.material.needsUpdate = true;
      }
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
      A.scene.traverse(obj => {
        if (obj.isMesh && obj !== A.ground) {
          obj.material.clippingPlanes = [];
          obj.material.needsUpdate = true;
        }
      });
      console.log('[S205] §SECTION OFF');
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
    A.scene.traverse(obj => {
      if (obj.isMesh && obj !== A.ground) {
        const box = new THREE.Box3().setFromObject(obj);
        if (A.sectionAxis === 'Y') { axMin = Math.min(axMin, box.min.y); axMax = Math.max(axMax, box.max.y); }
        else if (A.sectionAxis === 'X') { axMin = Math.min(axMin, box.min.x); axMax = Math.max(axMax, box.max.x); }
        else { axMin = Math.min(axMin, box.min.z); axMax = Math.max(axMax, box.max.z); }
      }
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
    A.scene.traverse(obj => {
      if (obj.isMesh && obj !== A.ground) {
        obj.material.clippingPlanes = [A.sectionPlane];
        obj.material.clipShadows = true;
        obj.material.needsUpdate = true;
      }
    });
    document.getElementById('section-val').textContent = axMax.toFixed(1) + ' m';
    console.log(`[S205] §SECTION ON axis=${A.sectionAxis} range=[${axMin.toFixed(1)}, ${axMax.toFixed(1)}]`);
  };

  A.updateSectionPlane = function(val) {
    const v = parseFloat(val);
    A.sectionPlane.constant = v;
    document.getElementById('section-val').textContent = v.toFixed(1) + ' m';
  };

  // 4D/5D Export
  A.export4D5D = function() {
    if (!A.db || !A.activeBuilding) { A.status.textContent = 'Select a building first.'; return; }
    const bld = A.activeBuilding;
    const dbParam = new URLSearchParams(location.search).get('db') || 'yourproject_extracted.db';
    // S223: import:// URLs → boq_charts co-located (../boq_charts.html from sandbox/)
    // OCI URLs → strip to /o/ base
    // S224: pass diffdb to boq_charts when viewer has diff data
    var diffParam = '';
    var diffDbUrl = new URLSearchParams(location.search).get('diffdb');
    if (diffDbUrl) diffParam = '&diffdb=' + encodeURIComponent(diffDbUrl);

    var chartsUrl;
    if (dbParam.startsWith('import://')) {
      chartsUrl = '../boq_charts.html?db=' + encodeURIComponent(dbParam) + '&bld=' + bld + diffParam;
    } else {
      const base = location.href.split('?')[0].match(/(.*\/o\/)/)?.[1] || '../';
      chartsUrl = base + 'boq_charts.html?db=' + encodeURIComponent(dbParam) + '&bld=' + bld + diffParam;
    }
    window.open(chartsUrl, '_blank');
    A.status.textContent = `4D/5D analytics opened for ${bld} (Save 5D BOQ / Save 4D Schedule)`;
  };

  // Screenshot
  A.screenshot = function() {
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
    A.status.textContent = 'Screenshot saved to Downloads/';
  };

  // Fullscreen
  A.toggleFullscreen = function() {
    if (!document.fullscreenElement) {
      document.documentElement.requestFullscreen();
    } else {
      document.exitFullscreen();
    }
  };

  // Theme
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

    A.scene.traverse(obj => {
      if (obj.isLineSegments && obj.userData.building) {
        obj.visible = !A.lightTheme;
      }
    });

    const btn = document.getElementById('theme-btn');
    btn.textContent = A.lightTheme ? '\u263E' : '\u2606';
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

    const meshes = [];
    A.scene.traverse(obj => { if (obj.isMesh && obj !== A.ground && obj.visible) meshes.push(obj); });
    const hits = A.raycaster.intersectObjects(meshes, false);

    if (A.hoverHighlight) {
      A.hoverHighlight.material.emissive.setHex(0x000000);
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
