// main.js — initViewer() orchestrator: creates APP, calls each module's setup, starts render loop
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
  setupWalk(APP);
  setupCity(APP);

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
    // Device orientation LAST — nothing may overwrite the quaternion after this
    if (APP.walkModeActive) APP.walkOrientTick();
    APP.renderer.render(APP.scene, APP.camera);
  }

  // Go
  animate();
  APP.init().catch(e => {
    APP.status.textContent = `Error: ${e.message}`;
    console.error(`[S192] §INIT_ERROR`, e);
  });
}
