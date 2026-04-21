// tour.js — Fly around, cinematic tour, walk-through engine, path building
function setupTour(A) {

  A.toggleFlyAround = function() {
    const btn = document.getElementById('fly-btn');

    if (A.walkMode) {
      A.walkMode = false;
      A.walkLastTime = 0;
      A.flyActive = false;
      btn.style.background = '#444';
      btn.style.color = '#fff';
      A.status.textContent = `Walk paused at action ${A.walkActionIdx}/${A.walkActions.length} — tap ✈ to resume`;
      A.wlog(`PAUSED at action ${A.walkActionIdx}`);
      return;
    }

    if (A.walkActions && A.walkActions.length > 0 && A.walkActionIdx > 0) {
      A.walkMode = true;
      A.flyActive = true;
      A.walkLastTime = 0;
      btn.style.background = '#4fc3f7';
      btn.style.color = '#000';
      document.getElementById('walk-speed-btn').style.display = '';
      A.status.textContent = `Walk resumed at action ${A.walkActionIdx}/${A.walkActions.length}`;
      A.wlog(`RESUMED at action ${A.walkActionIdx}`);
      return;
    }

    A.flyActive = !A.flyActive;
    btn.style.background = A.flyActive ? '#4fc3f7' : '#444';
    btn.style.color = A.flyActive ? '#000' : '#fff';

    if (A.flyActive) {
      const tour = A.buildTour();
      if (tour && tour.length >= 2) {
        if (A.buildingsRendered.size > 1) {
          const primaryName = Object.keys(A.buildingCentres)[0];
          for (const name of A.buildingsRendered) {
            if (name === primaryName) continue;
            const bc = A.buildingCentres[name];
            if (!bc) continue;
            const ctr = A.ifc2three(bc.ix, bc.iy, bc.iz);
            const orbitR = Math.max(30, (bc.envelope || 80) * 0.75);
            tour.push({type:'orbit', cx:ctr.x, cy:ctr.y, cz:ctr.z, radius:orbitR, tiltDeg:40, duration:8});
            tour.push({type:'riseAndTilt', targetY:ctr.y + 20, tiltDeg:80, name:`${name} bird's eye`});
            tour.push({type:'pause', seconds:3});
            A.wlog(`City: added ${name}`);
          }
        }
        A.walkMode = true;
        A.walkActions = tour;
        A.walkActionIdx = 0;
        A.walkActionT = 0;
        A.walkPanAngle = 0;
        A.walkOrbitAngle = 0;
        A.walkLastTime = 0;
        A.walkSpeedMult = 1;
        document.getElementById('walk-speed-btn').style.display = '';
        document.getElementById('walk-speed-btn').textContent = '1x';
        A.wlog(`START cinematic tour: ${tour.length} actions`);
        A.status.textContent = `Cinematic tour: ${tour.length} actions`;
        return;
      }

      A.status.textContent = 'No walk data — using orbit fly';
      A.walkMode = false;
      A.flyTargets = [];
      for (const name of A.buildingsRendered) {
        const bc = A.buildingCentres[name];
        if (!bc) continue;
        const t = A.ifc2three(bc.ix, bc.iy, bc.iz);
        const radius = Math.max(80, (bc.envelope || Math.sqrt(bc.count) * 2) * 1.2);
        A.flyTargets.push({ x: t.x, y: t.y, z: t.z, radius, name });
      }
      if (A.flyTargets.length === 0) { A.flyActive = false; btn.style.background = '#444'; btn.style.color = '#fff'; return; }
      A.flyTargetIdx = 0;
      A.flyAngle = 0;
      A.flyTransitioning = false;
      A.status.textContent = `Flying around ${A.flyTargets[0].name}...`;
    } else {
      A.status.textContent = 'Fly stopped.';
      document.getElementById('walk-speed-btn').style.display = 'none';
    }
  };

  A.flyTick = function() {
    if (!A.flyActive || A.flyTargets.length === 0) return;

    const ft = A.flyTargets[A.flyTargetIdx];

    if (A.flyTransitioning) {
      const elapsed = (performance.now() - A.flyTransitionStart) / 1500;
      if (elapsed >= 1.0) {
        A.flyTransitioning = false;
        A.flyAngle = Math.atan2(A.camera.position.z - ft.z, A.camera.position.x - ft.x);
        A.status.textContent = `Flying around ${ft.name}...`;
      } else {
        const t = elapsed * elapsed * (3 - 2 * elapsed);
        A.camera.position.lerpVectors(A.flyFromPos, new THREE.Vector3(
          ft.x + ft.radius, ft.y + ft.radius * 0.6, ft.z + ft.radius
        ), t);
        A.controls.target.lerpVectors(A.flyFromTarget, new THREE.Vector3(ft.x, ft.y, ft.z), t);
        A.controls.update();
      }
      return;
    }

    A.flyAngle += 0.012;
    const camX = ft.x + Math.cos(A.flyAngle) * ft.radius;
    const camZ = ft.z + Math.sin(A.flyAngle) * ft.radius;
    const camY = ft.y + ft.radius * 0.6;
    A.camera.position.set(camX, camY, camZ);
    A.controls.target.set(ft.x, ft.y, ft.z);
    A.controls.update();

    if (A.flyAngle >= Math.PI * 2) {
      A.flyAngle = 0;
      if (A.flyTargets.length > 1) {
        A.flyFromPos = A.camera.position.clone();
        A.flyFromTarget = A.controls.target.clone();
        A.flyTargetIdx = (A.flyTargetIdx + 1) % A.flyTargets.length;
        A.flyTransitioning = true;
        A.flyTransitionStart = performance.now();
        A.status.textContent = `Flying to ${A.flyTargets[A.flyTargetIdx].name}...`;
      }
    }
  };

  // S206: Cinematic building tour
  A.buildTour = function() {
    let doorsByStorey = {};
    try {
      const dr = A.db.exec(`
        SELECT m.guid, t.center_x, t.center_y, t.center_z, m.storey, m.element_name
        FROM elements_meta m JOIN element_transforms t ON m.guid = t.guid
        WHERE m.ifc_class IN ('IfcDoor','IfcDoorStandardCase')
        ORDER BY m.storey, t.center_x
      `);
      if (dr.length) for (const [g, cx, cy, cz, st, nm] of dr[0].values) {
        if (!doorsByStorey[st]) doorsByStorey[st] = [];
        doorsByStorey[st].push({x: cx, y: cy, z: cz, name: nm, guid: g});
      }
    } catch(e) {}

    let stairs = [];
    try {
      const st = A.db.exec(`
        SELECT t.center_x, t.center_y, t.center_z, m.storey
        FROM elements_meta m JOIN element_transforms t ON m.guid = t.guid
        WHERE m.ifc_class IN ('IfcStair','IfcStairFlight')
        ORDER BY t.center_z
      `);
      if (st.length) stairs = st[0].values.map(([x,y,z,s]) => ({x,y,z,storey:s}));
    } catch(e) {}

    let storeyZ = {};
    try {
      const sz = A.db.exec(`
        SELECT m.storey, MIN(t.center_z) as floor_z
        FROM elements_meta m JOIN element_transforms t ON m.guid = t.guid
        WHERE m.ifc_class IN ('IfcDoor','IfcDoorStandardCase')
        GROUP BY m.storey ORDER BY floor_z
      `);
      if (sz.length) for (const [st, z] of sz[0].values) storeyZ[st] = z;
    } catch(e) {}

    let roomsByStorey = {};
    try {
      const rq = A.db.exec(`
        SELECT s.storey, s.name, AVG(t.center_x) cx, AVG(t.center_y) cy, AVG(t.center_z) cz, COUNT(*) cnt
        FROM rel_contained_in_space r
        JOIN spatial_structure s ON r.space_guid = s.guid
        JOIN element_transforms t ON r.element_guid = t.guid
        WHERE s.type = 'IfcSpace'
        GROUP BY r.space_guid
        ORDER BY cnt DESC
      `);
      if (rq.length) for (const [st, nm, cx, cy, cz, cnt] of rq[0].values) {
        if (!roomsByStorey[st]) roomsByStorey[st] = [];
        roomsByStorey[st].push({name: nm, cx, cy, cz, count: cnt});
      }
    } catch(e) {}

    const storeys = Object.keys(doorsByStorey).sort();
    if (storeys.length === 0) return null;

    const actions = [];

    const bc0 = Object.values(A.buildingCentres)[0];
    if (bc0) {
      const ctr = A.ifc2three(bc0.ix, bc0.iy, bc0.iz);
      const orbitR = Math.max(30, (bc0.envelope || 80) * 0.75);
      actions.push({type:'orbit', cx:ctr.x, cy:ctr.y, cz:ctr.z, radius:orbitR, tiltDeg:40, duration:8});
    }

    const firstDoor = doorsByStorey[storeys[0]]?.[0];
    if (!firstDoor) return actions.length > 0 ? actions : null;

    const ep = A.ifc2three(firstDoor.x, firstDoor.y, firstDoor.z);
    actions.push({type:'moveTo', x:ep.x, y:ep.y, z:ep.z, name:'Entrance'});
    actions.push({type:'pause', seconds:0.5});

    for (let si = 0; si < storeys.length; si++) {
      const storey = storeys[si];

      if (si > 0 && stairs.length > 0) {
        let bestStair = stairs[0];
        const prevZ = storeyZ[storeys[si-1]] || storeyZ[storeys[0]] || 0;
        const nextZ = storeyZ[storey] || prevZ + 3;
        const stairTP = A.ifc2three(bestStair.x, bestStair.y, bestStair.z);
        actions.push({type:'moveTo', x:stairTP.x, y:A.ifc2three(0,0,prevZ).y, z:stairTP.z, name:'To stairs'});
        actions.push({type:'pause', seconds:0.5});
        actions.push({type:'rise', targetY: A.ifc2three(0,0,nextZ).y, name:'Climbing stairs'});
        actions.push({type:'pause', seconds:0.5});
        A.wlog(`Tour: stair Z=${prevZ.toFixed(1)} → ${nextZ.toFixed(1)}`);
      }

      const rooms = roomsByStorey[storey];
      if (rooms && rooms.length > 0) {
        const limit = Math.min(rooms.length, 2);
        for (let ri = 0; ri < limit; ri++) {
          const room = rooms[ri];
          const rp = A.ifc2three(room.cx, room.cy, room.cz);
          actions.push({type:'moveTo', x:rp.x, y:rp.y, z:rp.z, name: room.name || `Room ${ri+1}`});
          actions.push({type:'lookAround', degrees: 360});
        }
      } else {
        const doors = doorsByStorey[storey] || [];
        const limit = Math.min(doors.length, 2);
        for (let di = 0; di < limit; di++) {
          const door = doors[di];
          const dp = A.ifc2three(door.x, door.y, door.z);
          actions.push({type:'moveTo', x:dp.x, y:dp.y, z:dp.z, name: door.name?.split(':')[0] || storey});
          actions.push({type:'lookAround', degrees: 360});
        }
      }
    }

    if (bc0) {
      const topZ = Math.max(...Object.values(storeyZ)) || 0;
      const topY = A.ifc2three(0, 0, topZ).y;
      actions.push({type:'riseAndTilt', targetY: topY + 20, tiltDeg:80, name:"Bird's eye"});
      actions.push({type:'pause', seconds:3});
    }

    A.wlog(`Tour built: ${actions.length} actions, ${storeys.length} storeys`);
    actions.forEach((a,i) => A.wlog(`  [${i}] ${a.type} ${a.name||''}`));
    window._walkStrategy = `CINE(${actions.length}acts,${storeys.length}fl)`;
    return actions;
  };

  A.cycleWalkSpeed = function() {
    const speeds = [1, 2, 4];
    const idx = speeds.indexOf(A.walkSpeedMult);
    A.walkSpeedMult = speeds[(idx + 1) % speeds.length];
    document.getElementById('walk-speed-btn').textContent = A.walkSpeedMult + 'x';
    A.wlog(`Speed: ${A.walkSpeedMult}x`);
  };

  // Action-based walkTick
  A.walkTick = function() {
    if (!A.walkMode || !A.walkActions || A.walkActions.length === 0) return;
    if (A.walkActionIdx >= A.walkActions.length) {
      A.walkMode = false;
      A.flyActive = false;
      A.walkActionIdx = 0;
      A.walkActionT = 0;
      A.walkPanAngle = 0;
      const btn = document.getElementById('fly-btn');
      if (btn) { btn.style.background = '#444'; btn.style.color = '#fff'; }
      A.status.textContent = 'Tour complete.';
      A.wlog('Tour complete');
      return;
    }

    const now = performance.now();
    const dt = A.walkLastTime > 0 ? Math.min((now - A.walkLastTime) / 1000, 0.1) : 0.016;
    A.walkLastTime = now;

    const act = A.walkActions[A.walkActionIdx];
    const spd = A.walkSpeedMult;

    if (act.type === 'moveTo') {
      if (A.walkActionT === 0) {
        act._startPos = A.camera.position.clone();
        act._startTarget = A.controls.target.clone();
        act._dist = A.camera.position.distanceTo(new THREE.Vector3(act.x, act.y + A.WALK_EYE_HEIGHT, act.z));
        const speed = act._dist > 5 ? Math.max(A.WALK_SPEED, act._dist / 3.0) : A.WALK_SPEED;
        act._duration = Math.max(act._dist / (speed * spd), 0.3);
      }
      A.walkActionT += dt;
      const t = Math.min(A.walkActionT / act._duration, 1.0);
      const s = t * t * (3 - 2 * t);
      const target = new THREE.Vector3(act.x, act.y + A.WALK_EYE_HEIGHT, act.z);
      A.camera.position.lerpVectors(act._startPos, target, s);
      const lookAt = target.clone();
      lookAt.z += 0.1;
      A.controls.target.lerpVectors(act._startTarget, lookAt, s);
      A.controls.update();
      A.status.textContent = `${act.name || 'Walking...'} [${spd}x] camY=${A.camera.position.y.toFixed(1)}`;
      if (t >= 1.0) {
        A.walkActionIdx++;
        A.walkActionT = 0;
        if (act.name) A.wlog(`Arrived: ${act.name} camY=${A.camera.position.y.toFixed(2)}`);
      }

    } else if (act.type === 'lookAround') {
      const degreesPerSec = A.PAN_SPEED * spd;
      if (A.walkPanAngle === 0 && A.walkActionT === 0) {
        const dx = A.controls.target.x - A.camera.position.x;
        const dz = A.controls.target.z - A.camera.position.z;
        act._startRad = Math.atan2(dx, dz);
      }
      A.walkPanAngle += degreesPerSec * dt;
      const rad = (act._startRad || 0) + A.walkPanAngle * Math.PI / 180;
      const lookDist = 3.0;
      const wantX = A.camera.position.x + lookDist * Math.sin(rad);
      const wantZ = A.camera.position.z + lookDist * Math.cos(rad);
      A.controls.target.x += (wantX - A.controls.target.x) * 0.15;
      A.controls.target.z += (wantZ - A.controls.target.z) * 0.15;
      A.controls.target.y += (A.camera.position.y - A.controls.target.y) * 0.15;
      A.controls.update();
      A.status.textContent = `Looking around ${(A.walkPanAngle).toFixed(0)}° [${spd}x]`;
      if (A.walkPanAngle >= (act.degrees || 360)) {
        A.walkActionIdx++;
        A.walkActionT = 0;
        A.walkPanAngle = 0;
      }

    } else if (act.type === 'rise') {
      const targetY = act.targetY + A.WALK_EYE_HEIGHT;
      const dy = targetY - A.camera.position.y;
      if (Math.abs(dy) < 0.05) {
        A.camera.position.y = targetY;
        A.walkActionIdx++;
        A.walkActionT = 0;
        A.wlog(`Rise done: camY=${A.camera.position.y.toFixed(2)}`);
      } else {
        const step = Math.sign(dy) * Math.min(1.0 * spd * dt, Math.abs(dy));
        A.camera.position.y += step;
        A.controls.target.y += step;
      }
      A.controls.update();
      A.status.textContent = `${act.name || 'Rising...'} camY=${A.camera.position.y.toFixed(1)} → ${targetY.toFixed(1)}`;

    } else if (act.type === 'pause') {
      A.walkActionT += dt;
      if (A.walkActionT >= (act.seconds || 1)) {
        A.walkActionIdx++;
        A.walkActionT = 0;
      }

    } else if (act.type === 'orbit') {
      const tiltRad = (act.tiltDeg || 40) * Math.PI / 180;
      const duration = act.duration || 8;
      const totalRad = Math.PI;
      if (A.walkActionT === 0) {
        A.walkOrbitAngle = Math.atan2(A.camera.position.z - act.cz, A.camera.position.x - act.cx);
        act._startAngle = A.walkOrbitAngle;
        act._startY = A.camera.position.y;
        act._groundY = act.cy + A.WALK_EYE_HEIGHT;
        A.wlog(`Orbit: r=${act.radius?.toFixed(0)} from ${(A.walkOrbitAngle*180/Math.PI).toFixed(0)}°`);
      }
      A.walkActionT += dt;
      const t = Math.min(A.walkActionT / duration, 1.0);
      const smooth = t * t * (3 - 2 * t);
      A.walkOrbitAngle = act._startAngle + totalRad * smooth;
      const orbitH = act.cy + act.radius * Math.sin(tiltRad);
      let camY;
      if (t < 0.2) {
        const ht = t / 0.2;
        const hs = ht * ht * (3 - 2 * ht);
        camY = act._startY + (orbitH - act._startY) * hs;
      } else if (t < 0.6) {
        camY = orbitH;
      } else {
        const dt2 = (t - 0.6) / 0.4;
        const ds = dt2 * dt2 * (3 - 2 * dt2);
        camY = orbitH + (act._groundY - orbitH) * ds;
      }
      const descentProgress = t > 0.6 ? (t - 0.6) / 0.4 : 0;
      const effectiveTilt = tiltRad * (1 - descentProgress * descentProgress);
      const camX = act.cx + Math.cos(A.walkOrbitAngle) * act.radius * Math.cos(effectiveTilt);
      const camZ = act.cz + Math.sin(A.walkOrbitAngle) * act.radius * Math.cos(effectiveTilt);
      A.camera.position.set(camX, camY, camZ);
      const lookY = act.cy + (camY - act.cy) * descentProgress;
      A.controls.target.set(act.cx, lookY, act.cz);
      A.controls.update();
      A.status.textContent = `Aerial sweep ${(t * 100).toFixed(0)}% [${spd}x]`;
      if (t >= 1.0) {
        A.walkActionIdx++;
        A.walkActionT = 0;
        A.wlog('Orbit complete');
      }

    } else if (act.type === 'riseAndTilt') {
      const targetY = act.targetY;
      if (A.walkActionT === 0) {
        act._startY = A.camera.position.y;
        act._startX = A.camera.position.x;
        act._startZ = A.camera.position.z;
      }
      const totalDist = Math.abs(targetY - act._startY);
      if (totalDist < 0.1) { A.walkActionIdx++; A.walkActionT = 0; return; }
      const duration = 5.0;
      A.walkActionT += dt;
      const t = Math.min(A.walkActionT / duration, 1.0);
      const smooth = t * t * (3 - 2 * t);
      A.camera.position.y = act._startY + (targetY - act._startY) * smooth;
      const tiltRad = (act.tiltDeg || 80) * Math.PI / 180 * smooth;
      const lookDist = 5.0;
      A.controls.target.set(
        act._startX,
        A.camera.position.y - lookDist * Math.sin(tiltRad),
        act._startZ + lookDist * Math.cos(tiltRad) * 0.1
      );
      A.controls.update();
      A.status.textContent = `${act.name || "Bird's eye"} ${(t * 100).toFixed(0)}% [${spd}x]`;
      if (t >= 1.0) {
        A.walkActionIdx++;
        A.walkActionT = 0;
        A.wlog(`RiseAndTilt done: camY=${A.camera.position.y.toFixed(2)}`);
      }

    } else {
      A.walkActionIdx++;
      A.walkActionT = 0;
    }
  };

  // ── Legacy path builders (kept for fallback) ──
  A.queryWalkPath = function() {
    if (!A.db) return null;
    let waypoints = [];

    let stairs = [];
    try {
      const st = A.db.exec(`
        SELECT t.center_x, t.center_y, t.center_z, m.storey
        FROM elements_meta m JOIN element_transforms t ON m.guid = t.guid
        WHERE m.ifc_class IN ('IfcStair','IfcStairFlight')
        ORDER BY t.center_z
      `);
      if (st.length > 0) stairs = st[0].values;
    } catch(e) {}

    let allDoors = [];
    const interiorDoorGuids = new Set();
    try {
      const ig = A.db.exec(`SELECT DISTINCT via_door_guid FROM walk_graph`);
      if (ig.length) ig[0].values.forEach(([g]) => interiorDoorGuids.add(g));
    } catch(e) {}
    try {
      const ad = A.db.exec(`
        SELECT m.guid, t.center_x, t.center_y, t.center_z, m.storey
        FROM elements_meta m JOIN element_transforms t ON m.guid = t.guid
        WHERE m.ifc_class IN ('IfcDoor','IfcDoorStandardCase')
        ORDER BY m.storey, t.center_z
      `);
      if (ad.length > 0) {
        allDoors = ad[0].values.map(([g, cx, cy, cz, st]) =>
          [g, cx, cy, cz, st, interiorDoorGuids.has(g) ? 1 : 0]
        );
      }
    } catch(e) {}

    // Strategy 1: walk_graph table
    try {
      const wg = A.db.exec(`
        SELECT from_space_guid, to_space_guid, via_door_guid,
               door_x, door_y, door_z, storey
        FROM walk_graph ORDER BY storey, rowid
      `);
      if (wg.length > 0 && wg[0].values.length > 0) {
        console.log(`[S205] §WALK_GRAPH found ${wg[0].values.length} edges, ${stairs.length} stairs, ${allDoors.length} total doors`);
        window._walkStrategy = `GRAPH(${wg[0].values.length}edges,${stairs.length}stairs)`;
        A.wlog(`Strategy: GRAPH ${wg[0].values.length}edges ${stairs.length}stairs ${allDoors.length}doors`);
        const gResult = A.buildWalkGraphPath(wg[0].values, stairs, allDoors);
        if (gResult) gResult.forEach((w,i) => A.wlog(`  wp[${i}] ${w.name} y=${w.y.toFixed(2)}`));
        return gResult;
      }
    } catch(e) { console.warn('[S205] walk_graph strategy failed:', e.message); }

    // Strategy 2: IfcSpace centroids
    try {
      const sp = A.db.exec(`
        SELECT s.guid, s.name, t.center_x, t.center_y, t.center_z, m.storey
        FROM spatial_structure s
        JOIN element_transforms t ON s.guid = t.guid
        JOIN elements_meta m ON s.guid = m.guid
        WHERE s.type = 'IfcSpace'
        ORDER BY m.storey, t.center_x, t.center_y
      `);
      if (sp.length > 0 && sp[0].values.length >= 2) {
        console.log(`[S205] §WALK_SPACES found ${sp[0].values.length} IfcSpaces`);
        window._walkStrategy = `SPACES(${sp[0].values.length})`;
        A.wlog(`Strategy: SPACES ${sp[0].values.length}`);
        const sResult = A.buildSpacePath(sp[0].values, stairs, allDoors);
        if (sResult) sResult.forEach((w,i) => A.wlog(`  wp[${i}] ${w.name} y=${w.y.toFixed(2)}`));
        return sResult;
      }
    } catch(e) { console.warn('[S205] space strategy failed:', e.message); }

    // Strategy 3: IfcDoor positions (fallback)
    try {
      const dr = A.db.exec(`
        SELECT m.guid, m.element_name, t.center_x, t.center_y, t.center_z, m.storey
        FROM elements_meta m
        JOIN element_transforms t ON m.guid = t.guid
        WHERE m.ifc_class IN ('IfcDoor', 'IfcDoorStandardCase')
        ORDER BY m.storey, t.center_x, t.center_y
      `);
      if (dr.length > 0 && dr[0].values.length >= 2) {
        console.log(`[S205] §WALK_DOORS found ${dr[0].values.length} IfcDoors (fallback)`);
        window._walkStrategy = `DOORS(${dr[0].values.length})`;
        A.wlog(`Strategy: DOORS ${dr[0].values.length}`);
        const dResult = A.buildDoorPath(dr[0].values, stairs, allDoors);
        if (dResult) dResult.forEach((w,i) => A.wlog(`  wp[${i}] ${w.name} y=${w.y.toFixed(2)}`));
        return dResult;
      }
    } catch(e) { console.warn('[S205] door strategy failed:', e.message); }

    return null;
  };

  A.buildWalkGraphPath = function(edges, stairs, allDoors) {
    const spaceNames = {};
    try {
      const sn = A.db.exec("SELECT guid, name FROM spatial_structure WHERE type='IfcSpace'");
      if (sn.length) sn[0].values.forEach(([g,n]) => spaceNames[g] = n);
    } catch(e) {}

    const adj = new Map();
    const spaceSt = new Map();
    for (const [fromG, toG, viaG, dx, dy, dz, storey] of edges) {
      if (!adj.has(fromG)) adj.set(fromG, []);
      if (!adj.has(toG)) adj.set(toG, []);
      adj.get(fromG).push({ x: dx, y: dy, z: dz, target: toG });
      adj.get(toG).push({ x: dx, y: dy, z: dz, target: fromG });
      spaceSt.set(fromG, storey);
      spaceSt.set(toG, storey);
    }

    const stairPts = (stairs || []).map(([sx, sy, sz]) => {
      const tp = A.ifc2three(sx, sy, sz);
      return { x: tp.x, y: tp.y, z: tp.z };
    });

    let frontDoorIFC = null;
    if (allDoors && allDoors.length > 0) {
      const exterior = allDoors.filter(d => d[5] === 0);
      if (exterior.length > 0) {
        exterior.sort((a, b) => (a[4] || '').localeCompare(b[4] || ''));
        frontDoorIFC = { x: exterior[0][1], y: exterior[0][2], z: exterior[0][3] };
      }
    }

    const byStorey = new Map();
    for (const [guid, st] of spaceSt) {
      if (!byStorey.has(st)) byStorey.set(st, []);
      byStorey.get(st).push(guid);
    }
    const storeys = [...byStorey.keys()].sort();

    const waypoints = [];
    const visited = new Set();

    if (frontDoorIFC) {
      const tp = A.ifc2three(frontDoorIFC.x, frontDoorIFC.y, frontDoorIFC.z);
      waypoints.push({ x: tp.x, y: tp.y, z: tp.z, name: 'Entrance' });
    }

    for (let si = 0; si < storeys.length; si++) {
      const storey = storeys[si];
      const storeySpaces = byStorey.get(storey);

      if (si > 0 && stairPts.length > 0 && waypoints.length > 0) {
        const last = waypoints[waypoints.length - 1];
        let bestStair = stairPts[0], bestSD = Infinity;
        for (const s of stairPts) {
          const d = Math.hypot(s.x - last.x, s.z - last.z);
          if (d < bestSD) { bestSD = d; bestStair = s; }
        }
        waypoints.push({ x: bestStair.x, y: last.y, z: bestStair.z, name: 'Stairs' });
        waypoints.push({ x: bestStair.x, y: bestStair.y, z: bestStair.z, name: 'Climbing...' });
        const nd = adj.get(storeySpaces[0])?.[0];
        if (nd) {
          const ntp = A.ifc2three(nd.x, nd.y, nd.z);
          waypoints.push({ x: bestStair.x, y: ntp.y, z: bestStair.z, name: storey });
        }
      }

      while (true) {
        let current = null;
        if (waypoints.length > 0) {
          const last = waypoints[waypoints.length - 1];
          let bestD = Infinity;
          for (const g of storeySpaces) {
            if (visited.has(g)) continue;
            for (const d of adj.get(g) || []) {
              const tp = A.ifc2three(d.x, d.y, d.z);
              const dist = Math.hypot(tp.x - last.x, tp.z - last.z);
              if (dist < bestD) { bestD = dist; current = g; }
            }
          }
        }
        if (!current) current = storeySpaces.find(g => !visited.has(g));
        if (!current) break;
        visited.add(current);

        const firstD = adj.get(current)?.[0];
        if (firstD) {
          const tp = A.ifc2three(firstD.x, firstD.y, firstD.z);
          waypoints.push({ x: tp.x, y: tp.y, z: tp.z, name: spaceNames[current] || current });
        }

        while (true) {
          let bestDoor = null, bestDist = Infinity, bestTarget = null;
          for (const d of adj.get(current) || []) {
            if (visited.has(d.target)) continue;
            const tp = A.ifc2three(d.x, d.y, d.z);
            const last = waypoints[waypoints.length - 1] || tp;
            const dist = Math.hypot(tp.x - last.x, tp.z - last.z);
            if (dist < bestDist) { bestDist = dist; bestDoor = d; bestTarget = d.target; }
          }
          if (!bestDoor) break;
          const tp = A.ifc2three(bestDoor.x, bestDoor.y, bestDoor.z);
          waypoints.push({ x: tp.x, y: tp.y, z: tp.z, name: spaceNames[bestTarget] || bestTarget });
          visited.add(bestTarget);
          current = bestTarget;
        }
      }
    }

    if (frontDoorIFC) {
      const tp = A.ifc2three(frontDoorIFC.x, frontDoorIFC.y, frontDoorIFC.z);
      waypoints.push({ x: tp.x, y: tp.y, z: tp.z, name: 'Exit' });
    }

    console.log(`[S205] §WALK_PATH ${waypoints.length} waypoints (${stairPts.length} stairs, ${storeys.length} storeys: ${storeys.join(',')})`);
    waypoints.forEach((wp, i) => console.log(`  [${i}] ${wp.name} y=${wp.y.toFixed(2)}`));
    return waypoints.length >= 2 ? waypoints : null;
  };

  A.buildPointPath = function(points, stairs, allDoors) {
    const stairPts = (stairs || []).map(([sx,sy,sz]) => A.ifc2three(sx,sy,sz));

    let frontDoorIFC = null;
    if (allDoors && allDoors.length > 0) {
      const exterior = allDoors.filter(d => d[5] === 0);
      if (exterior.length > 0) {
        exterior.sort((a,b) => (a[4]||'').localeCompare(b[4]||''));
        frontDoorIFC = { x: exterior[0][1], y: exterior[0][2], z: exterior[0][3] };
      }
    }

    const byStorey = {};
    for (const p of points) {
      const st = p.storey || '';
      if (!byStorey[st]) byStorey[st] = [];
      byStorey[st].push(p);
    }
    const storeyOrder = Object.keys(byStorey).sort();

    const waypoints = [];

    if (frontDoorIFC) {
      const tp = A.ifc2three(frontDoorIFC.x, frontDoorIFC.y, frontDoorIFC.z);
      waypoints.push({ x: tp.x, y: tp.y, z: tp.z, name: 'Entrance' });
    }

    for (let si = 0; si < storeyOrder.length; si++) {
      const st = storeyOrder[si];
      const stPoints = byStorey[st];

      if (si > 0 && stairPts.length > 0 && waypoints.length > 0) {
        const last = waypoints[waypoints.length - 1];
        let bestStair = stairPts[0], bestSD = Infinity;
        for (const s of stairPts) {
          const d = Math.hypot(s.x - last.x, s.z - last.z);
          if (d < bestSD) { bestSD = d; bestStair = s; }
        }
        waypoints.push({ x: bestStair.x, y: last.y, z: bestStair.z, name: 'Stairs' });
        waypoints.push({ x: bestStair.x, y: bestStair.y, z: bestStair.z, name: 'Climbing...' });
        const firstNext = stPoints[0];
        const ntp = A.ifc2three(firstNext.x, firstNext.y, firstNext.z);
        waypoints.push({ x: bestStair.x, y: ntp.y, z: bestStair.z, name: st });
      }

      const visited = new Set();
      let startIdx = 0;
      if (waypoints.length > 0) {
        const last = waypoints[waypoints.length - 1];
        let bestD = Infinity;
        for (let j = 0; j < stPoints.length; j++) {
          const tp = A.ifc2three(stPoints[j].x, stPoints[j].y, stPoints[j].z);
          const d = Math.hypot(tp.x - last.x, tp.z - last.z);
          if (d < bestD) { bestD = d; startIdx = j; }
        }
      }
      let current = stPoints[startIdx];
      visited.add(startIdx);
      const tp0 = A.ifc2three(current.x, current.y, current.z);
      waypoints.push({ x: tp0.x, y: tp0.y, z: tp0.z, name: current.name });

      for (let i = 1; i < stPoints.length; i++) {
        let bestIdx = -1, bestDist = Infinity;
        for (let j = 0; j < stPoints.length; j++) {
          if (visited.has(j)) continue;
          const dx = stPoints[j].x - current.x;
          const dy = stPoints[j].y - current.y;
          const d = Math.sqrt(dx * dx + dy * dy);
          if (d < bestDist) { bestDist = d; bestIdx = j; }
        }
        if (bestIdx >= 0) {
          visited.add(bestIdx);
          current = stPoints[bestIdx];
          const tp = A.ifc2three(current.x, current.y, current.z);
          waypoints.push({ x: tp.x, y: tp.y, z: tp.z, name: current.name });
        }
      }
    }

    if (frontDoorIFC) {
      const tp = A.ifc2three(frontDoorIFC.x, frontDoorIFC.y, frontDoorIFC.z);
      waypoints.push({ x: tp.x, y: tp.y, z: tp.z, name: 'Exit' });
    }

    return waypoints.length >= 2 ? waypoints : null;
  };

  A.buildSpacePath = function(rows, stairs, allDoors) {
    const points = rows.map(r => ({ name: r[1]||'Space', x: r[2], y: r[3], z: r[4], storey: r[5]||'' }));
    return A.buildPointPath(points, stairs, allDoors);
  };

  A.buildDoorPath = function(rows, stairs, allDoors) {
    const points = rows.map(r => ({ name: r[1]||'Door', x: r[2], y: r[3], z: r[4], storey: r[5]||'' }));
    return A.buildPointPath(points, stairs, allDoors);
  };

  A.computePathLength = function(path) {
    let total = 0;
    for (let i = 1; i < path.length; i++) {
      const dx = path[i].x - path[i-1].x;
      const dy = path[i].y - path[i-1].y;
      const dz = path[i].z - path[i-1].z;
      total += Math.sqrt(dx*dx + dy*dy + dz*dz);
    }
    return total;
  };

  A.interpolateWalkPath = function(path, t) {
    if (path.length < 2) return path[0] || { x: 0, y: 0, z: 0, name: '' };
    const totalLen = A.computePathLength(path);
    let targetDist = t * totalLen;
    let accum = 0;

    for (let i = 1; i < path.length; i++) {
      const dx = path[i].x - path[i-1].x;
      const dy = path[i].y - path[i-1].y;
      const dz = path[i].z - path[i-1].z;
      const segLen = Math.sqrt(dx*dx + dy*dy + dz*dz);
      if (accum + segLen >= targetDist) {
        const f = segLen > 0.001 ? (targetDist - accum) / segLen : 0;
        return {
          x: path[i-1].x + dx * f,
          y: path[i-1].y + dy * f,
          z: path[i-1].z + dz * f,
          name: path[i].name || path[i-1].name || ''
        };
      }
      accum += segLen;
    }
    return path[path.length - 1];
  };
}
