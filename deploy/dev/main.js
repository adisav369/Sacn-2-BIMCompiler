// main.js — initViewer() orchestrator: creates APP, calls each module's setup, starts render loop
// DEV version — adds setupNlp (S211 voice command / NLP query)
function initViewer() {
  const APP = window.APP = {};

  // Initialize modules in order
  setupConfig(APP);
  setupScene(APP);
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

  // Render loop
  function animate() {
    requestAnimationFrame(animate);
    if (!APP.walkModeActive) {
      APP.controls.update();
      if (APP.walkMode) { APP.walkTick(); } else { APP.flyTick(); }
    }
    APP.streamTick();
    APP.walkModeGpsTick();
    APP.updateMeasureLabels();
    // Hide ground when camera goes below it (allows viewing building from underneath)
    if (APP.ground && APP.ground.visible) {
      APP.ground.material.visible = APP.camera.position.y > APP.ground.position.y;
    }
    // Device orientation LAST — nothing may overwrite the quaternion after this
    if (APP.walkModeActive) APP.walkOrientTick();
    APP.renderer.render(APP.scene, APP.camera);
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
            if (vBtn) { vBtn.style.display = 'block'; vBtn.textContent = '\u0394 Variance (' + (APP.diffResult.added.length + APP.diffResult.removed.length + APP.diffResult.changed.length) + ')'; }
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
}
