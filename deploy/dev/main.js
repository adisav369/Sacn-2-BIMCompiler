/**
 * BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
// main.js — initViewer() orchestrator: creates APP, calls each module's setup, starts render loop
// DEV version — adds setupNlp (S211 voice command / NLP query)
function initViewer() {
  const APP = window.APP = {};

  // Initialize modules in order
  setupConfig(APP);
  setupScene(APP);
  setupHelpers(APP);
  setupStreaming(APP);
  setupPanels(APP);
  setupTools(APP);
  setupPicking(APP);
  setupTour(APP);
  setupMeasure(APP);
  setupSitecam(APP);
  setupIssues(APP);
  setupExcel(APP);
  setupWalk(APP);
  setupCity(APP);
  if (typeof setupNlp === 'function') setupNlp(APP);
  // navigate.js lazy-loaded on demand (78KB saved on first paint)
  APP._navigateLoaded = false;
  APP.loadNavigate = function() {
    if (APP._navigatePromise) return APP._navigatePromise;
    APP._navigatePromise = new Promise(function(resolve, reject) {
      if (typeof setupNavigate === 'function') {
        setupNavigate(APP);
        APP._navigateLoaded = true;
        resolve();
        return;
      }
      var s = document.createElement('script');
      s.src = 'navigate.js?v=9';
      s.onload = function() {
        if (typeof setupNavigate === 'function') setupNavigate(APP);
        APP._navigateLoaded = true;
        console.log('[S239] §NAVIGATE_LAZY_LOADED');
        resolve();
      };
      s.onerror = function() { reject(new Error('Failed to load navigate.js')); };
      document.head.appendChild(s);
    });
    return APP._navigatePromise;
  };
  // Proxy so nlp.js "typeof A.openFindPanel === 'function'" finds it immediately.
  // setupNavigate() overwrites APP.openFindPanel with the real implementation.
  var _navProxy = function(searchTerm) {
    APP.loadNavigate().then(function() {
      // After load, APP.openFindPanel is the real function (set by setupNavigate)
      if (APP.openFindPanel !== _navProxy) APP.openFindPanel(searchTerm);
    });
  };
  APP.openFindPanel = _navProxy;
  // wizard.js lazy-loaded on demand (70KB saved on first paint)
  APP._wizardLoaded = false;
  APP.loadWizard = function() {
    if (APP._wizardPromise) return APP._wizardPromise;
    APP._wizardPromise = new Promise(function(resolve, reject) {
      if (typeof startWizard === 'function') {
        APP._wizardLoaded = true;
        resolve();
        return;
      }
      var s = document.createElement('script');
      s.src = 'wizard.js?v=2';
      s.onload = function() {
        APP._wizardLoaded = true;
        console.log('[S239] §WIZARD_LAZY_LOADED');
        resolve();
      };
      s.onerror = function() { reject(new Error('Failed to load wizard.js')); };
      document.head.appendChild(s);
    });
    return APP._wizardPromise;
  };
  if (typeof setupImport === 'function') setupImport(APP);
  if (typeof setupDiff === 'function') setupDiff(APP);

  // Expose functions to HTML onclick handlers
  window.togglePanel = APP.togglePanel;
  window.clearStreamed = APP.clearStreamed;
  window.toggleXray = APP.toggleXray;
  window.screenshot = APP.screenshot;
  window.toggleFullscreen = APP.toggleFullscreen;
  window.toggleTheme = APP.toggleTheme;
  window.toggleFlyAround = APP.toggleFlyAround;
  window.filterStorey = APP.filterStorey;
  window.toggleDisc = APP.toggleDisc;
  window.export4D5D = APP.export4D5D;
  window.flyTo = APP.flyTo;
  window.openSiteCamera = APP.openSiteCamera;
  window.closeSiteCamera = APP.closeSiteCamera;
  window.snapSitePhoto = APP.snapSitePhoto;
  window.closeSitePreview = APP.closeSitePreview;
  window.shareSitePhoto = APP.shareSitePhoto;
  window.downloadSitePhoto = APP.downloadSitePhoto;
  window.setMarkupTool = APP.setMarkupTool;
  window.setMarkupColor = APP.setMarkupColor;
  window.undoMarkup = APP.undoMarkup;
  window.toggleMeasure = APP.toggleMeasure;
  window.clearMeasures = APP.clearMeasures;
  window.toggleSection = APP.toggleSection;
  window.setSectionAxis = APP.setSectionAxis;
  window.updateSectionPlane = APP.updateSectionPlane;
  window.toggleSunglass = APP.toggleSunglass;
  window.closeSunglass = APP.closeSunglass;
  window.updateAmbience = APP.updateAmbience;
  window.toggleIssues = APP.toggleIssues;
  window.exportIssuesExcel = APP.exportIssuesExcel;
  window.clearAllIssues = APP.clearAllIssues;
  window._issueBackToList = APP._issueBackToList;
  window.toggleWalkMode = APP.toggleWalkMode;
  window.setWalkAnchor = APP.setWalkAnchor;
  window.cancelWalkAnchor = APP.cancelWalkAnchor;
  window.cycleWalkSpeed = APP.cycleWalkSpeed;
  if (APP.toggleNlp) window.toggleNlp = APP.toggleNlp;
  window.toggleVariance = function() { if (APP.toggleVariance) APP.toggleVariance(); };
  window.open2DPlans = function() {
    const p = new URLSearchParams(location.search);
    const db = p.get('db') || '';
    const lib = p.get('lib') || '';
    const bld = APP.activeBuilding || '';
    const url = '2d.html?db=' + encodeURIComponent(db) +
                '&lib=' + encodeURIComponent(lib) +
                '&bld=' + encodeURIComponent(bld);
    console.log('§2D_OPEN db=' + db + ' lib=' + lib + ' bld=' + bld + ' url=' + url);
    window.open(url, '_blank');
  };

  // Render loop — on-demand: only render when camera moves or streaming is active
  let _needsRender = true;
  APP.controls.addEventListener('change', () => { _needsRender = true; });
  APP.markDirty = () => { _needsRender = true; };

  function animate() {
    requestAnimationFrame(animate);
    if (!APP.walkModeActive) {
      APP.controls.update();
      if (APP.walkMode) { APP.walkTick(); } else { APP.flyTick(); }
    }
    APP.streamTick();
    // S245e: Clash DLOD proximity LOD update (throttled internally to 100ms)
    if (APP._clashModeActive && APP._updateClashLOD) APP._updateClashLOD();
    APP.walkModeGpsTick();
    // Device orientation LAST — nothing may overwrite the quaternion after this
    if (APP.walkModeActive) APP.walkOrientTick();
    const streaming = APP.streamedCount < APP.totalElements && APP.totalElements > 0;
    if (_needsRender || streaming || APP.walkModeActive || APP.walkMode) {
      APP.updateMeasureLabels();
      if (APP.ground && APP.ground.visible) {
        APP.ground.material.visible = APP.camera.position.y > APP.ground.position.y;
      }
      APP.renderer.render(APP.scene, APP.camera);
      _needsRender = false;
    }
  }

  // Go
  animate();
  APP.init().then(async function() {
    // S223: Load diff DB if ?diffdb= param present (variation comparison)
    const diffDbUrl = new URLSearchParams(location.search).get('diffdb');
    if (diffDbUrl && APP.db && typeof APP.computeDiff === 'function') {
      try {
        const buf = await APP.cachedFetch(diffDbUrl);
        const SQL = await initSqlJs({ locateFile: f => 'https://sql.js.org/dist/' + f });
        APP.diffDb = new SQL.Database(new Uint8Array(buf));
        console.log('[S223] §DIFF_DB_LOADED url=' + diffDbUrl);
        APP.computeDiff();
        // Delay overlay until meshes are streamed (check every 2s, up to 30s)
        var checks = 0;
        var diffTimer = setInterval(function() {
          checks++;
          var meshCount = 0;
          APP.scene.traverse(function(o) { if (o.isMesh && o.userData.guid) meshCount++; });
          if (meshCount > 10 || checks > 15) {
            clearInterval(diffTimer);
            APP.applyDiffOverlay();
            // S225: Don't auto-popup — show Variance button in HUD, user clicks to see list
            var vBtn = document.getElementById('variance-btn');
            if (vBtn) { vBtn.style.display = 'block'; vBtn.textContent = '\u0394 ' + (typeof _TRL!=='undefined'&&_TRL.ui_variance||'Variance') + ' (' + (APP.diffResult.added.length + APP.diffResult.removed.length + APP.diffResult.changed.length) + ')'; }
            console.log('[S225] §DIFF_OVERLAY_READY meshes=' + meshCount);
          }
        }, 2000);
      } catch(e) {
        console.log('[S223] §DIFF_DB_ERROR ' + e.message);
      }
    }
  }).catch(e => {
    APP.status.textContent = `Error: ${e.message}`;
    console.error(`[S192] §INIT_ERROR`, e);
  });

  // S243: Offline/online status notification
  function showNetStatus(online) {
    var id = 'net-status-toast';
    var old = document.getElementById(id);
    if (old) old.remove();
    var div = document.createElement('div');
    div.id = id;
    div.style.cssText = 'position:fixed;top:60px;left:50%;transform:translateX(-50%);z-index:9999;' +
      'padding:10px 24px;border-radius:8px;font-size:13px;font-family:Segoe UI,sans-serif;' +
      'box-shadow:0 4px 12px rgba(0,0,0,0.4);transition:opacity 0.5s;pointer-events:none;';
    if (online) {
      div.style.background = 'rgba(39,174,96,0.92)';
      div.style.color = '#fff';
      div.textContent = 'Back online';
      console.log('[S243] §NET_STATUS online');
    } else {
      div.style.background = 'rgba(230,126,34,0.92)';
      div.style.color = '#fff';
      div.textContent = 'Offline mode — cached buildings still available';
      console.log('[S243] §NET_STATUS offline');
    }
    document.body.appendChild(div);
    setTimeout(function() { div.style.opacity = '0'; }, online ? 3000 : 5000);
    setTimeout(function() { if (div.parentNode) div.remove(); }, online ? 3500 : 5500);
  }
  window.addEventListener('offline', function() { showNetStatus(false); });
  window.addEventListener('online', function() { showNetStatus(true); });
  if (!navigator.onLine) showNetStatus(false);
}
