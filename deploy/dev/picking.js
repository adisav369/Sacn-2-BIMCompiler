/**
 * BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
// picking.js — Click-to-identify (raycaster), walk/wall state, pointer handlers

// S233: Polyfill InstancedMesh.raycast for Three.js r128 (native in r132+)
// Without this, raycaster silently skips all InstancedMesh objects — no pick on shared geometry.
// Cost: zero per frame. Only runs on click — loops instances, tests ray against each.
(function() {
  if (typeof THREE === 'undefined' || !THREE.InstancedMesh) return;
  var proto = THREE.InstancedMesh.prototype;
  if (proto._hasRaycastPoly) return;

  var _m = new THREE.Mesh();
  var _im = new THREE.Matrix4();

  proto.raycast = function(raycaster, intersects) {
    _m.geometry = this.geometry;
    _m.material = this.material;
    var before = intersects.length;

    for (var i = 0; i < this.count; i++) {
      this.getMatrixAt(i, _im);
      _m.matrixWorld.multiplyMatrices(this.matrixWorld, _im);
      _m.raycast(raycaster, intersects);

      // Tag new hits with instanceId and correct object ref
      for (var j = intersects.length - 1; j >= before; j--) {
        intersects[j].instanceId = i;
        intersects[j].object = this;
      }
      before = intersects.length;
    }
  };
  proto._hasRaycastPoly = true;
})();

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
      const bboxes = A.collectMeshes(o => o.isLineSegments && o.userData.building);
      const bboxHits = A.raycaster.intersectObjects(bboxes, false);
      if (bboxHits.length > 0) {
        const bldName = bboxHits[0].object.userData.building;
        A.flyTo(bldName);
        return;
      }
    }

    if (!A.db) return;

    const meshes = A.collectMeshes(o => (o.isMesh || o.isInstancedMesh) && o.visible);
    const hits = A.raycaster.intersectObjects(meshes, false);

    if (!hits.length) {
      document.getElementById('info-panel').style.display = 'none';
      return;
    }

    const hit = hits[0];
    // S232: InstancedMesh — use instanceId to look up guid from metadata
    let guid = null;
    if (hit.object.isInstancedMesh && hit.instanceId !== undefined && A._instanceMeta[hit.object.id]) {
      const meta = A._instanceMeta[hit.object.id][hit.instanceId];
      if (meta) guid = meta.guid;
    }
    // S232: Merged mesh — resolve nearest element by hit-point distance in DB
    if (!guid && hit.object.userData.isMerged) {
      // Convert Three.js hit point back to IFC coordinates
      const hp = hit.point;
      const ix = hp.x + A.modelOffset.x;
      const iy = -hp.z + A.modelOffset.y;
      const iz = hp.y + A.modelOffset.z;
      const ud = hit.object.userData;
      try {
        const near = A.dbQuery(`
          SELECT m.guid,
            (t.center_x - ?) * (t.center_x - ?) +
            (t.center_y - ?) * (t.center_y - ?) +
            (t.center_z - ?) * (t.center_z - ?) AS dist2
          FROM elements_meta m
          JOIN element_transforms t ON t.guid = m.guid
          WHERE m.storey = ? AND m.discipline = ?
          ORDER BY dist2 ASC LIMIT 1
        `, [ix, ix, iy, iy, iz, iz, ud.storey || '', ud.disc || '']);
        if (near.length) { guid = near[0][0]; hit._mergedResolved = true; }
      } catch(e) {
        console.log(`§PICK_MERGE_ERR ${e.message}`);
      }
      if (!guid) {
        // Fallback: show group-level info only
        document.getElementById('info-class').textContent = `Merged group (${ud.mergedCount} elements)`;
        document.getElementById('info-name').textContent = '—';
        document.getElementById('info-guid').textContent = '—';
        document.getElementById('info-building').textContent = A.activeBuilding || '—';
        document.getElementById('info-storey').textContent = ud.storey || '—';
        document.getElementById('info-disc').textContent = ud.disc || '—';
        document.getElementById('info-material').textContent = '—';
        document.getElementById('info-panel').style.display = 'block';
        const snagRow = document.getElementById('snag-btn-row');
        if (snagRow) snagRow.style.display = A.walkModeActive ? 'block' : 'none';
        console.log(`§PICK merged fallback storey=${ud.storey} disc=${ud.disc}`);
        return;
      }
      console.log(`§PICK merged→resolved guid=${guid}`);
    }
    if (!guid) guid = A.guidMap[hit.object.id];
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

    // Yellow highlight bbox — dispose previous to prevent GPU geometry/material leak
    if (window._pickHighlight) {
      const prev = window._pickHighlight;
      if (prev.parent) prev.parent.remove(prev);
      prev.geometry.dispose();
      prev.material.dispose();
      window._pickHighlight = null;
    }

    // Highlight: use IFC-extracted bbox if stored, else compute from geometry
    let hlSizeX, hlSizeY, hlSizeZ;
    if (guid) {
      try {
        const bboxRow = A.dbQuery('SELECT bbox_x, bbox_y, bbox_z FROM element_transforms WHERE guid = ?', [guid]);
        if (bboxRow.length && bboxRow[0][0] != null) {
          // IFC bbox extracted at import — axis swap: IFC(x,y,z) → Three(x,z,y)
          hlSizeX = bboxRow[0][0]; hlSizeY = bboxRow[0][2]; hlSizeZ = bboxRow[0][1];
        }
      } catch(e) { /* bbox columns may not exist in older DBs */ }
    }
    hit.object.geometry.computeBoundingBox();
    const bb = hit.object.geometry.boundingBox;
    const localCenter = new THREE.Vector3(); bb.getCenter(localCenter);
    if (!hlSizeX) {
      const size = new THREE.Vector3(); bb.getSize(size);
      hlSizeX = size.x; hlSizeY = size.y; hlSizeZ = size.z;
    }
    const hlGeo = new THREE.BoxGeometry(
      Math.max(hlSizeX, 0.01), Math.max(hlSizeY, 0.01), Math.max(hlSizeZ, 0.01));
    const hlEdges = new THREE.EdgesGeometry(hlGeo);
    hlGeo.dispose();
    const hlLine = new THREE.LineSegments(hlEdges,
      new THREE.LineBasicMaterial({ color: 0xffff00 }));
    if (hit.object.isInstancedMesh && hit.instanceId !== undefined) {
      const _im = new THREE.Matrix4();
      hit.object.getMatrixAt(hit.instanceId, _im);
      const worldCenter = localCenter.clone().applyMatrix4(_im);
      const _ip = new THREE.Vector3(), _iq = new THREE.Quaternion(), _is = new THREE.Vector3();
      _im.decompose(_ip, _iq, _is);
      hlLine.position.copy(worldCenter);
      hlLine.quaternion.copy(_iq);
    } else {
      // Individual Mesh or merged-resolved: convert local bbox centre to world space
      hlLine.position.copy(hit.object.localToWorld(localCenter));
    }
    A.scene.add(hlLine);
    window._pickHighlight = hlLine;

    try {
      // S239: parameterized query (was string interpolation — SQL injection risk)
      const rows = A.dbQuery(`
        SELECT m.ifc_class, m.element_name, m.guid, m.building, m.storey,
               m.discipline, m.material_rgba
        FROM elements_meta m WHERE m.guid = ?
      `, [guid]);
      if (!rows.length) {
        document.getElementById('info-panel').style.display = 'none';
        return;
      }
      const [cls, name, g, bld, storey, disc, mat] = rows[0];
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
