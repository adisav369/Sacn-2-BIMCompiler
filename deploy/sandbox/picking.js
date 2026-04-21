// picking.js — Click-to-identify (raycaster), walk/wall state, pointer handlers
function setupPicking(A) {
  // Walk/Wall state (hoisted before first use in pointerdown/animate)
  A.walkMode = false;
  A.walkModeActive = false;
  A.walkAnchorGPS = null;
  A.walkAnchorIFC = null;
  A.walkBlueDot = null;
  A.walkGpsWatchId = null;
  A.walkStoreyLevels = [];
  A.walkGpsFollowCam = false;
  A.wallXrayActive = false;
  A.wallXrayOriginals = [];
  A.walkPath = [];
  A.walkT = 0;
  A.walkTotalLen = 0;
  A.walkSpeedMult = 1;
  A.walkCurrentRoom = '';
  A.walkLastTime = 0;
  A.wallXrayMepHighlights = [];
  A.measureActive = false;
  A.measureFirstPoint = null;
  A.measureFirstMarker = null;
  A.measureGroup = new THREE.Group();
  A.measureLabels = [];
  A.scene.add(A.measureGroup);

  // Fly state
  A.flyActive = false;
  A.flyAngle = 0;
  A.flyTargets = [];
  A.flyTargetIdx = 0;
  A.flyTransitioning = false;
  A.flyTransitionStart = 0;
  A.flyFromPos = null;
  A.flyFromTarget = null;

  // Walk action state
  A.walkActions = [];
  A.walkActionIdx = 0;
  A.walkActionT = 0;
  A.walkPanAngle = 0;
  A.walkOrbitAngle = 0;

  // Walk log
  A._wlog = [];
  A.wlog = function(msg) {
    A._wlog.push(msg);
    console.log('[WALK] ' + msg);
    const el = document.getElementById('walk-log');
    if (el) el.textContent = A._wlog.join('\n');
  };

  A.canvas.addEventListener('pointerdown', (e) => {
    A.pointerDownPos.x = e.clientX;
    A.pointerDownPos.y = e.clientY;
    if (A.flyActive || A.walkMode) {
      A.flyActive = false;
      A.walkMode = false;
      A.walkPath = [];
      document.getElementById('fly-btn').style.background = '#444';
      document.getElementById('fly-btn').style.color = '#fff';
      document.getElementById('walk-speed-btn').style.display = 'none';
    }
  });

  A.canvas.addEventListener('pointerup', (e) => {
    if (A.measureActive) { A.handleMeasureClick(e); return; }

    const dx = e.clientX - A.pointerDownPos.x;
    const dy = e.clientY - A.pointerDownPos.y;
    if (Math.sqrt(dx*dx + dy*dy) > 5) return;
    if (e.shiftKey || e.button !== 0) return;

    A.mouse.x = (e.clientX / window.innerWidth) * 2 - 1;
    A.mouse.y = -(e.clientY / window.innerHeight) * 2 + 1;
    A.raycaster.setFromCamera(A.mouse, A.camera);

    // City mode: check bbox wireframes first
    if (A.CITY_URL) {
      const bboxes = [];
      A.scene.traverse(obj => { if (obj.isLineSegments && obj.userData.building) bboxes.push(obj); });
      const bboxHits = A.raycaster.intersectObjects(bboxes, false);
      if (bboxHits.length > 0) {
        const bldName = bboxHits[0].object.userData.building;
        A.flyTo(bldName);
        return;
      }
    }

    if (!A.db) return;

    const meshes = [];
    A.scene.traverse(obj => { if (obj.isMesh && obj !== A.ground && obj.visible) meshes.push(obj); });
    const hits = A.raycaster.intersectObjects(meshes, false);

    if (!hits.length) {
      document.getElementById('info-panel').style.display = 'none';
      return;
    }

    const hit = hits[0];
    const guid = A.guidMap[hit.object.id];
    if (!guid) {
      console.log(`§PICK no guid for mesh.id=${hit.object.id}`);
      return;
    }

    // Wall X-Ray in Walk Mode
    if (A.walkModeActive) {
      const faceNormal = hit.face ? hit.face.normal.clone() : new THREE.Vector3(1, 0, 0);
      if (A.handleWallXray(hit.object, hit.point, faceNormal)) return;
      A.restoreWallXray();
    }

    // Yellow highlight bbox
    if (window._pickHighlight) {
      window._pickHighlight.parent.remove(window._pickHighlight);
      window._pickHighlight = null;
    }
    hit.object.geometry.computeBoundingBox();
    const bb = hit.object.geometry.boundingBox;
    const size = new THREE.Vector3();
    bb.getSize(size);
    const center = new THREE.Vector3();
    bb.getCenter(center);
    const hlGeo = new THREE.BoxGeometry(size.x, size.y, size.z);
    const hlEdges = new THREE.EdgesGeometry(hlGeo);
    const hlLine = new THREE.LineSegments(hlEdges,
      new THREE.LineBasicMaterial({ color: 0xffff00 }));
    hlLine.position.copy(center);
    hit.object.add(hlLine);
    window._pickHighlight = hlLine;

    try {
      const rows = A.db.exec(`
        SELECT m.ifc_class, m.element_name, m.guid, m.building, m.storey,
               m.discipline, m.material_rgba
        FROM elements_meta m WHERE m.guid = '${guid}'
      `);
      if (!rows.length || !rows[0].values.length) return;
      const [cls, name, g, bld, storey, disc, mat] = rows[0].values[0];
      document.getElementById('info-class').textContent = cls || '—';
      document.getElementById('info-name').textContent = name || '—';
      document.getElementById('info-guid').textContent = g || '—';
      document.getElementById('info-building').textContent = bld || '—';
      document.getElementById('info-storey').textContent = storey || '—';
      document.getElementById('info-disc').textContent = disc || '—';
      document.getElementById('info-material').textContent = mat || '—';
      document.getElementById('info-panel').style.display = 'block';
      // Show Snag button during walk mode
      const snagRow = document.getElementById('snag-btn-row');
      if (snagRow) snagRow.style.display = A.walkModeActive ? 'block' : 'none';
      A.populateStoreys(bld);
      A.populateDiscs(bld);
      console.log(`§PICK ${cls} "${name}" ${disc} ${storey}`);
    } catch (err) {
      console.log(`§PICK_ERR ${err.message}`);
    }
  });
}
