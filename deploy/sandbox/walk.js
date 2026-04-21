// walk.js — Walk Mode (GPS blue dot, step detection, wall X-ray)
function setupWalk(A) {
  // Walk Mode compass/tilt state
  A.walkCompassReadings = [];
  A.walkLockedHeading = null;
  A.walkLiveTilt = 0;
  A.walkOrientationHandler = null;

  // Step detection state
  A.walkStepHandler = null;
  A.walkLastAccelZ = 0;
  A.walkStepCooldown = 0;
  A.walkStepCount = 0;

  A.toggleWalkMode = function() {
    if (A.walkModeActive) {
      A.stopWalkMode();
      return;
    }
    document.getElementById('walk-anchor-prompt').style.display = 'block';
  };

  A.cancelWalkAnchor = function() {
    document.getElementById('walk-anchor-prompt').style.display = 'none';
  };

  A.setWalkAnchor = function() {
    document.getElementById('walk-anchor-prompt').style.display = 'none';

    A.walkAnchorIFC = A.findNearestDoorPosition();
    if (!A.walkAnchorIFC) {
      const bld = Object.values(A.buildingCentres)[0];
      if (bld) {
        A.walkAnchorIFC = { x: bld.ix, y: bld.iy, z: bld.iz };
      } else {
        A.status.textContent = 'Walk Mode: No building data';
        return;
      }
    }

    const anchorThree = A.ifc2three(A.walkAnchorIFC.x, A.walkAnchorIFC.y, A.walkAnchorIFC.z);
    const bldCentre = Object.values(A.buildingCentres)[0];

    // Offset camera 3m outside the door (away from building centre)
    if (bldCentre) {
      const bc = A.ifc2three(bldCentre.ix, bldCentre.iy, bldCentre.iz);
      const dx = anchorThree.x - bc.x, dz = anchorThree.z - bc.z;
      const len = Math.sqrt(dx * dx + dz * dz) || 1;
      const offset = 3; // metres outside door
      A.camera.position.set(anchorThree.x + dx / len * offset, anchorThree.y + A.WALK_EYE_HEIGHT, anchorThree.z + dz / len * offset);
      // Look toward the door (into the building)
      A.controls.target.set(anchorThree.x, anchorThree.y + A.WALK_EYE_HEIGHT, anchorThree.z);
    } else {
      A.camera.position.set(anchorThree.x, anchorThree.y + A.WALK_EYE_HEIGHT, anchorThree.z);
    }
    A.controls.update();

    // A-Frame pattern: reorder rotation before device orientation work
    A.camera.rotation.reorder('YXZ');

    // Record initial camera quaternion (facing the door)
    A._walkQDoor = A.camera.quaternion.clone();
    A._walkQBaseline = null; // captured on first device orientation event
    const _initDir = new THREE.Vector3(0, 0, -1).applyQuaternion(A._walkQDoor);
    console.log(`[S208] §WALK_INIT pos=(${A.camera.position.x.toFixed(1)},${A.camera.position.y.toFixed(1)},${A.camera.position.z.toFixed(1)}) dir=(${_initDir.x.toFixed(3)},${_initDir.y.toFixed(3)},${_initDir.z.toFixed(3)}) q=(${A._walkQDoor.x.toFixed(3)},${A._walkQDoor.y.toFixed(3)},${A._walkQDoor.z.toFixed(3)},${A._walkQDoor.w.toFixed(3)})`);

    // A-Frame pattern: listener caches event, render loop does math
    const _q1 = new THREE.Quaternion(-Math.sqrt(0.5), 0, 0, Math.sqrt(0.5));
    const _zee = new THREE.Vector3(0, 0, 1);
    A._walkAlphaOffset = 0;
    A._walkFirstUpdate = true;
    A._walkScreenOrientation = window.orientation || 0;
    A._walkDeviceEvent = null;  // cached event

    const onScreenChange = () => { A._walkScreenOrientation = window.orientation || 0; };
    window.addEventListener('orientationchange', onScreenChange);

    // Listener ONLY caches — no quaternion work here
    A._walkOrientListener = function(e) {
      A._walkDeviceEvent = e;
    };

    // Called from render loop (main.js animate) — this is where quaternion is set
    A.walkOrientTick = function() {
      if (!A.walkModeActive || !A._walkDeviceEvent) return;
      const e = A._walkDeviceEvent;
      if (!e.alpha) return;

      const deg2rad = THREE.MathUtils.degToRad;
      const alpha = deg2rad(e.alpha) + A._walkAlphaOffset;
      const beta = e.beta ? deg2rad(e.beta) : 0;
      const gamma = e.gamma ? deg2rad(e.gamma) : 0;
      const orient = A._walkScreenOrientation ? deg2rad(A._walkScreenOrientation) : 0;

      // Standard Three.js setObjectQuaternion
      const euler = new THREE.Euler(beta, alpha, -gamma, 'YXZ');
      A.camera.quaternion.setFromEuler(euler);
      A.camera.quaternion.multiply(_q1.clone());
      A.camera.quaternion.multiply(new THREE.Quaternion().setFromAxisAngle(_zee, -orient));

      // On first reading, compute alphaOffset so camera faces the door
      if (A._walkFirstUpdate) {
        A._walkFirstUpdate = false;
        const devDir = new THREE.Vector3(0, 0, -1).applyQuaternion(A.camera.quaternion);
        const devYaw = Math.atan2(devDir.x, devDir.z);
        const doorDir = new THREE.Vector3(0, 0, -1).applyQuaternion(A._walkQDoor);
        const doorYaw = Math.atan2(doorDir.x, doorDir.z);
        A._walkAlphaOffset = doorYaw - devYaw;
        // Re-apply with corrected offset
        const alpha2 = deg2rad(e.alpha) + A._walkAlphaOffset;
        const euler2 = new THREE.Euler(beta, alpha2, -gamma, 'YXZ');
        A.camera.quaternion.setFromEuler(euler2);
        A.camera.quaternion.multiply(_q1.clone());
        A.camera.quaternion.multiply(new THREE.Quaternion().setFromAxisAngle(_zee, -orient));
      }
    };

    A._walkCleanupScreen = onScreenChange;
    if (typeof DeviceOrientationEvent?.requestPermission === 'function') {
      DeviceOrientationEvent.requestPermission().then(r => {
        if (r === 'granted') window.addEventListener('deviceorientation', A._walkOrientListener);
      }).catch(() => {});
    } else {
      window.addEventListener('deviceorientation', A._walkOrientListener);
    }
    A.startWalkOrientation();

    if (navigator.geolocation) {
      A.walkAnchorGPS = null;
      navigator.geolocation.getCurrentPosition(
        pos => {
          A.walkAnchorGPS = { lat: pos.coords.latitude, lng: pos.coords.longitude };
          A.startWalkGpsTracking();
          console.log(`[S207] §WALK_GPS anchored (${A.walkAnchorGPS.lat.toFixed(6)},${A.walkAnchorGPS.lng.toFixed(6)})`);
        },
        () => { A.status.textContent = 'Walk Mode: No GPS — orientation only'; },
        { enableHighAccuracy: true, timeout: 10000 }
      );
    }

    A.walkModeActive = true;
    A.walkGpsFollowCam = false;
    A.controls.enabled = false; // Disable OrbitControls — device orientation drives camera
    document.getElementById('walk-mode-btn').classList.add('active');
    A.cacheStoreyLevels();
    A.startStepDetection();

    A.status.textContent = 'Walk Mode: Shake phone to walk forward...';
    console.log(`[S207] §WALK_MODE_START anchor IFC=(${A.walkAnchorIFC.x.toFixed(1)},${A.walkAnchorIFC.y.toFixed(1)},${A.walkAnchorIFC.z.toFixed(1)})`);
  };

  A.startWalkOrientation = function() {
    if (!A._camOrientHandler) {
      A._camOrientHandler = (e) => {
        const h = e.webkitCompassHeading ?? (e.alpha != null ? (360 - e.alpha) % 360 : null);
        if (h != null) A._camHeading = Math.round(h);
      };
      if (typeof DeviceOrientationEvent?.requestPermission === 'function') {
        DeviceOrientationEvent.requestPermission().then(r => {
          if (r === 'granted') window.addEventListener('deviceorientation', A._camOrientHandler, true);
        }).catch(() => {});
      } else {
        window.addEventListener('deviceorientation', A._camOrientHandler, true);
      }
    }
    A.walkOrientationHandler = (e) => {
      if (e.beta != null) {
        A.walkLiveTilt = A.walkLiveTilt * 0.9 + e.beta * 0.1;
      }
    };
    window.addEventListener('deviceorientation', A.walkOrientationHandler);
    A.status.textContent = A._camHeading != null ? `Walk Mode: Compass ${A._camHeading}°` : 'Walk Mode: Waiting for compass...';
    setTimeout(() => {
      if (A.walkModeActive && A._camHeading === null) A.status.textContent = 'Walk Mode: Compass not available — tilt only';
    }, 5000);
  };

  A.findNearestDoorPosition = function() {
    if (!A.db) return null;
    const bld = Object.values(A.buildingCentres)[0];
    if (!bld) return null;
    try {
      // Get lowest storey that has doors (ground floor entrance)
      const stRows = A.db.exec(`
        SELECT m.storey, MIN(t.center_z) as min_z
        FROM elements_meta m
        JOIN element_transforms t ON m.guid = t.guid
        WHERE m.ifc_class IN ('IfcDoor', 'IfcDoorStandardCase')
        GROUP BY m.storey
        ORDER BY min_z ASC
        LIMIT 1
      `);
      const lowestStorey = (stRows.length > 0 && stRows[0].values.length > 0) ? stRows[0].values[0][0] : null;

      // Get all doors on that storey, pick the one furthest from building centre (exterior door)
      let query = `
        SELECT t.center_x, t.center_y, t.center_z
        FROM elements_meta m
        JOIN element_transforms t ON m.guid = t.guid
        WHERE m.ifc_class IN ('IfcDoor', 'IfcDoorStandardCase')
      `;
      if (lowestStorey) query += ` AND m.storey = '${lowestStorey.replace(/'/g, "''")}'`;

      const rows = A.db.exec(query);
      if (rows.length === 0 || rows[0].values.length === 0) return null;

      // Pick ground-floor door nearest to current camera position
      const camIfc = { x: A.camera.position.x + A.modelOffset.x, y: -(A.camera.position.z) + A.modelOffset.y };
      let best = null, bestDist = Infinity;
      for (const [x, y, z] of rows[0].values) {
        const dx = x - camIfc.x, dy = y - camIfc.y;
        const dist = dx * dx + dy * dy;
        if (dist < bestDist) { bestDist = dist; best = { x, y, z }; }
      }
      console.log(`[S208] §WALK_DOOR picked (${best.x.toFixed(1)},${best.y.toFixed(1)},${best.z.toFixed(1)}) dist=${Math.sqrt(bestDist).toFixed(1)}m from ${rows[0].values.length} doors`);
      return best;
    } catch(e) { /* no doors */ }
    return null;
  };

  A.cacheStoreyLevels = function() {
    A.walkStoreyLevels = [];
    if (!A.db) return;
    try {
      const rows = A.db.exec(`
        SELECT DISTINCT storey, MIN(center_z) as floor_z
        FROM elements_meta JOIN element_transforms USING(guid)
        WHERE storey IS NOT NULL
        GROUP BY storey ORDER BY floor_z
      `);
      if (rows.length > 0) {
        A.walkStoreyLevels = rows[0].values.map(r => ({ storey: r[0], floorZ: r[1] }));
        console.log(`[S205] §WALK_STOREYS ${A.walkStoreyLevels.length} levels cached`);
      }
    } catch(e) { /* no storey data */ }
  };

  A.startWalkGpsTracking = function() {
    if (A.walkGpsWatchId !== null) {
      navigator.geolocation.clearWatch(A.walkGpsWatchId);
    }
    A.walkGpsWatchId = navigator.geolocation.watchPosition(
      pos => {
        if (!A.walkModeActive || !A.walkAnchorGPS || !A.walkAnchorIFC || !A.walkBlueDot) return;

        const dLat = pos.coords.latitude - A.walkAnchorGPS.lat;
        const dLng = pos.coords.longitude - A.walkAnchorGPS.lng;

        const cosLat = Math.cos(A.walkAnchorGPS.lat * Math.PI / 180);
        const dx = dLng * 111320 * cosLat;
        const dy = dLat * 111320;

        const angle = (window._trueNorthAngle || 0) * Math.PI / 180;
        const mx = dx * Math.cos(angle) - dy * Math.sin(angle);
        const my = dx * Math.sin(angle) + dy * Math.cos(angle);

        const ifcX = A.walkAnchorIFC.x + mx;
        const ifcY = A.walkAnchorIFC.y + my;
        let ifcZ = A.walkAnchorIFC.z;

        if (A.walkStoreyLevels.length > 1 && pos.coords.altitude != null) {
          const altDelta = pos.coords.altitude - (A.walkAnchorGPS.alt || pos.coords.altitude);
          const targetZ = A.walkAnchorIFC.z + altDelta;
          let bestStorey = A.walkStoreyLevels[0];
          let bestDist = Math.abs(targetZ - bestStorey.floorZ);
          for (const sl of A.walkStoreyLevels) {
            const d = Math.abs(targetZ - sl.floorZ);
            if (d < bestDist) { bestDist = d; bestStorey = sl; }
          }
          ifcZ = bestStorey.floorZ;
        }

        const tp = A.ifc2three(ifcX, ifcY, ifcZ);
        A.walkBlueDot.position.set(tp.x, tp.y + A.WALK_EYE_HEIGHT * 0.3, tp.z);

        if (!A.walkAnchorGPS.alt && pos.coords.altitude != null) {
          A.walkAnchorGPS.alt = pos.coords.altitude;
        }
      },
      err => {
        console.warn('[S205] §WALK_GPS_ERR', err.message);
      },
      { enableHighAccuracy: true }
    );
  };

  // walkModeGpsTick is now handled by _walkOrientListener (quaternion-based)
  A.walkModeGpsTick = function() {
    // GPS blue dot position update only — orientation is event-driven
    if (!A.walkModeActive) return;
  };

  A.stopWalkMode = function() {
    A.walkModeActive = false;
    A.walkGpsFollowCam = false;
    if (A._walkOrientListener) {
      window.removeEventListener('deviceorientation', A._walkOrientListener);
      A._walkOrientListener = null;
    }
    if (A._walkCleanupScreen) {
      window.removeEventListener('orientationchange', A._walkCleanupScreen);
      A._walkCleanupScreen = null;
    }
    if (A.walkGpsWatchId !== null) {
      navigator.geolocation.clearWatch(A.walkGpsWatchId);
      A.walkGpsWatchId = null;
    }
    if (A.walkBlueDot) {
      A.scene.remove(A.walkBlueDot);
      A.walkBlueDot = null;
    }
    if (A.walkOrientationHandler) {
      window.removeEventListener('deviceorientation', A.walkOrientationHandler);
      A.walkOrientationHandler = null;
    }
    A.stopStepDetection();
    A.walkLockedHeading = null;
    A.walkCompassReadings = [];
    document.getElementById('walk-mode-btn').classList.remove('active');
    A.status.textContent = 'Walk Mode stopped.';
    console.log('[S207] §WALK_MODE_STOP');
  };

  // Step Detection
  A.startStepDetection = function() {
    if (A.walkStepHandler) return;
    const requestMotion = typeof DeviceMotionEvent?.requestPermission === 'function';
    const attach = () => {
      A.walkStepHandler = (e) => {
        if (!A.walkModeActive) return;
        const acc = e.accelerationIncludingGravity;
        if (!acc || acc.z == null) return;

        const now = performance.now();
        const deltaZ = Math.abs(acc.z - A.walkLastAccelZ);
        A.walkLastAccelZ = acc.z;

        if (deltaZ > A.WALK_STEP_THRESHOLD && now - A.walkStepCooldown > A.WALK_STEP_COOLDOWN_MS) {
          A.walkStepCooldown = now;
          A.walkStepCount++;
          A.advanceWalkStep();
        }
      };
      window.addEventListener('devicemotion', A.walkStepHandler);
      console.log('[S207b] §STEP_DETECT started threshold=' + A.WALK_STEP_THRESHOLD + 'm/s²');
    };
    if (requestMotion) {
      DeviceMotionEvent.requestPermission().then(r => { if (r === 'granted') attach(); }).catch(() => {});
    } else {
      attach();
    }
  };

  A.stopStepDetection = function() {
    if (A.walkStepHandler) {
      window.removeEventListener('devicemotion', A.walkStepHandler);
      A.walkStepHandler = null;
    }
    if (A.walkStepCount > 0) {
      console.log(`[S207b] §STEP_DETECT stopped steps=${A.walkStepCount} dist=${(A.walkStepCount * A.WALK_STEP_DISTANCE).toFixed(1)}m`);
    }
    A.walkStepCount = 0;
  };

  A.advanceWalkStep = function() {
    const dir = new THREE.Vector3();
    A.camera.getWorldDirection(dir);
    dir.y = 0;
    dir.normalize();
    dir.multiplyScalar(A.WALK_STEP_DISTANCE);

    A.camera.position.add(dir);
    // Do NOT call controls.update() — device orientation drives the quaternion

    A.status.textContent = `Walk Mode: ${A.walkStepCount} steps`;
  };

  // Wall X-Ray
  A.handleWallXray = function(hitObject, hitPoint, hitFaceNormal) {
    if (!A.walkModeActive) return false;

    const guid = A.guidMap[hitObject.id];
    if (!guid) return false;

    try {
      const rows = A.db.exec(`
        SELECT ifc_class, storey, element_name
        FROM elements_meta WHERE guid = '${guid}'
      `);
      if (!rows.length || !rows[0].values.length) return false;
      const [ifcClass, storey, elemName] = rows[0].values[0];

      if (!ifcClass || (!ifcClass.includes('Wall') && !ifcClass.includes('wall'))) return false;

      A.restoreWallXray();

      console.log(`[S205] §WALL_XRAY class=${ifcClass} storey=${storey} name=${elemName}`);

      const wallRows = A.db.exec(`
        SELECT center_x, center_y, center_z
        FROM element_transforms WHERE guid = '${guid}'
      `);
      if (!wallRows.length) return false;
      const [wallX, wallY, wallZ] = wallRows[0].values[0];

      A.wallXrayActive = true;
      const mat = hitObject.material;
      A.wallXrayOriginals.push({
        mesh: hitObject,
        origOpacity: mat.opacity,
        origTransparent: mat.transparent,
        origSide: mat.side
      });
      mat.transparent = true;
      mat.opacity = 0.15;
      mat.side = THREE.DoubleSide;
      mat.needsUpdate = true;

      const mepRows = A.db.exec(`
        SELECT m.guid, m.ifc_class, m.element_name, m.discipline,
               t.center_x, t.center_y, t.center_z
        FROM elements_meta m
        JOIN element_transforms t ON m.guid = t.guid
        WHERE m.discipline IN ('MEP','ELEC','PLB','ACMV','FP','HVAC','MEC')
          AND m.storey = '${storey}'
          AND ABS(t.center_x - ${wallX}) < 2.0
          AND ABS(t.center_y - ${wallY}) < 2.0
      `);

      if (mepRows.length > 0 && mepRows[0].values.length > 0) {
        console.log(`[S205] §WALL_MEP found=${mepRows[0].values.length} near wall`);

        const mepGuids = new Set(mepRows[0].values.map(r => r[0]));
        let highlighted = 0;

        A.scene.traverse(obj => {
          if (obj.isMesh && obj.userData.guid && mepGuids.has(obj.userData.guid)) {
            A.wallXrayOriginals.push({
              mesh: obj,
              origOpacity: obj.material.opacity,
              origTransparent: obj.material.transparent,
              origSide: obj.material.side,
              origColor: obj.material.color.clone(),
              origEmissive: obj.material.emissive ? obj.material.emissive.clone() : null
            });
            obj.material.emissive = new THREE.Color(0x00ff44);
            obj.material.emissiveIntensity = 0.6;
            obj.material.transparent = false;
            obj.material.opacity = 1.0;
            obj.material.needsUpdate = true;
            highlighted++;
          }
        });

        for (const [mGuid, mClass, mName, mDisc, mCx, mCy, mCz] of mepRows[0].values) {
          if (!highlighted || !A.findMeshByGuid(mGuid)) {
            const tp = A.ifc2three(mCx, mCy, mCz);
            const markerGeo = new THREE.SphereGeometry(0.15, 8, 8);
            const isElec = mDisc === 'ELEC' || mDisc === 'FP';
            const markerMat = new THREE.MeshBasicMaterial({
              color: isElec ? 0xff8800 : 0x00ff44
            });
            const marker = new THREE.Mesh(markerGeo, markerMat);
            marker.position.set(tp.x, tp.y, tp.z);
            marker.userData._wallXrayMarker = true;
            A.scene.add(marker);
            A.wallXrayMepHighlights.push(marker);
          }
        }

        A.status.textContent = `Wall X-Ray: ${mepRows[0].values.length} MEP elements behind ${elemName || ifcClass}`;
      } else {
        A.status.textContent = `Wall X-Ray: No MEP elements found behind ${elemName || ifcClass}`;
      }

      return true;
    } catch(e) {
      console.warn('[S205] §WALL_XRAY_ERR', e.message);
      return false;
    }
  };

  A.findMeshByGuid = function(guid) {
    let found = null;
    A.scene.traverse(obj => {
      if (obj.isMesh && obj.userData.guid === guid) found = obj;
    });
    return found;
  };

  A.restoreWallXray = function() {
    if (!A.wallXrayActive) return;

    for (const entry of A.wallXrayOriginals) {
      const mat = entry.mesh.material;
      mat.opacity = entry.origOpacity;
      mat.transparent = entry.origTransparent;
      mat.side = entry.origSide;
      if (entry.origColor) mat.color.copy(entry.origColor);
      if (entry.origEmissive !== undefined) {
        mat.emissive = entry.origEmissive || new THREE.Color(0, 0, 0);
        mat.emissiveIntensity = 0;
      }
      mat.needsUpdate = true;
    }
    A.wallXrayOriginals = [];

    for (const marker of A.wallXrayMepHighlights) {
      A.scene.remove(marker);
      marker.geometry.dispose();
      marker.material.dispose();
    }
    A.wallXrayMepHighlights = [];

    A.wallXrayActive = false;
  };
}
