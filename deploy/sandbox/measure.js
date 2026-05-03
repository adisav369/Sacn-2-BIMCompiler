// measure.js — Measurement tool (two-point distance)
function setupMeasure(A) {

  A.toggleMeasure = function() {
    A.measureActive = !A.measureActive;
    const btn = document.getElementById('measure-btn');
    btn.style.background = A.measureActive ? '#4fc3f7' : '#444';
    btn.style.color = A.measureActive ? '#000' : '#fff';
    A.status.textContent = A.measureActive ? 'Measure — tap two points on building' : '';
    if (!A.measureActive) {
      A.clearMeasures();
    }
    console.log(`§MEASURE mode ${A.measureActive ? 'ON' : 'OFF'}`);
  };

  A.clearMeasures = function() {
    while (A.measureGroup.children.length) {
      A.measureGroup.remove(A.measureGroup.children[0]);
    }
    A.measureLabels.forEach(m => m.div.remove());
    A.measureLabels = [];
    A.measureFirstPoint = null;
    A.measureFirstMarker = null;
    // Restore any orange-highlighted meshes
    if (A._areaBackups) {
      A._areaBackups.forEach(b => { b.mesh.material = b.origMat; });
      A._areaBackups = [];
    }
    console.log('§MEASURE cleared all');
  };

  A._measureClickTimer = null;
  A.handleMeasureClick = function(e) {
    if (!A.measureActive) return false;
    // Debounce: wait 250ms to see if double-click follows
    if (A._measureClickTimer) { clearTimeout(A._measureClickTimer); A._measureClickTimer = null; }
    var ev = { clientX: e.clientX, clientY: e.clientY };
    A._measureClickTimer = setTimeout(function() { A._doMeasureClick(ev); }, 250);
    return true;
  };

  A._doMeasureClick = function(e) {
    A.mouse.x = (e.clientX / window.innerWidth) * 2 - 1;
    A.mouse.y = -(e.clientY / window.innerHeight) * 2 + 1;
    A.raycaster.setFromCamera(A.mouse, A.camera);

    const meshes = [];
    A.scene.traverse(obj => { if (obj.isMesh && obj !== A.ground && obj.visible) meshes.push(obj); });
    const hits = A.raycaster.intersectObjects(meshes, false);
    if (!hits.length) return true;

    const point = hits[0].point.clone();

    if (!A.measureFirstPoint) {
      A.measureFirstPoint = point;
      const markerGeo = new THREE.SphereGeometry(0.15, 8, 8);
      const markerMat = new THREE.MeshBasicMaterial({ color: 0x4fc3f7 });
      A.measureFirstMarker = new THREE.Mesh(markerGeo, markerMat);
      A.measureFirstMarker.position.copy(point);
      A.measureGroup.add(A.measureFirstMarker);
    } else {
      const p1 = A.measureFirstPoint;
      const p2 = point;
      const dist = p1.distanceTo(p2).toFixed(2) + 'm';

      const markerGeo2 = new THREE.SphereGeometry(0.15, 8, 8);
      const markerMat2 = new THREE.MeshBasicMaterial({ color: 0x4fc3f7 });
      const marker2 = new THREE.Mesh(markerGeo2, markerMat2);
      marker2.position.copy(p2);
      A.measureGroup.add(marker2);

      const lineGeo = new THREE.BufferGeometry().setFromPoints([p1, p2]);
      const lineMat = new THREE.LineDashedMaterial({
        color: 0x4fc3f7, dashSize: 0.3, gapSize: 0.15, linewidth: 1
      });
      const line = new THREE.Line(lineGeo, lineMat);
      line.computeLineDistances();
      A.measureGroup.add(line);

      const labelDiv = document.createElement('div');
      labelDiv.className = 'measure-label';
      labelDiv.textContent = dist;
      document.body.appendChild(labelDiv);

      const mid = new THREE.Vector3().addVectors(p1, p2).multiplyScalar(0.5);
      A.measureLabels.push({ div: labelDiv, p1: p1.clone(), p2: p2.clone(), mid: mid });

      console.log(`§MEASURE ${dist} from (${p1.x.toFixed(1)},${p1.y.toFixed(1)},${p1.z.toFixed(1)}) to (${p2.x.toFixed(1)},${p2.y.toFixed(1)},${p2.z.toFixed(1)})`);

      A.measureFirstPoint = null;
      A.measureFirstMarker = null;
    }
    return true;
  };

  // ── Area from mesh geometry (world-space, cached by geometry UUID) ──
  A._areaCache = {};
  A._meshArea = function(mesh) {
    var geo = mesh.geometry;
    if (!geo) return 0;
    var cacheKey = geo.uuid;
    if (A._areaCache[cacheKey] !== undefined) {
      console.log('§MEASURE_AREA cache hit geo=' + cacheKey);
      return A._areaCache[cacheKey];
    }
    var pos = geo.attributes.position;
    if (!pos) return 0;
    var idx = geo.index;
    // Transform vertices to world space via mesh.matrixWorld
    mesh.updateMatrixWorld(true);
    var mat = mesh.matrixWorld;
    var a = new THREE.Vector3(), b = new THREE.Vector3(), c = new THREE.Vector3();
    var ab = new THREE.Vector3(), ac = new THREE.Vector3();
    var area = 0;
    if (idx) {
      for (var i = 0; i < idx.count; i += 3) {
        a.fromBufferAttribute(pos, idx.getX(i)).applyMatrix4(mat);
        b.fromBufferAttribute(pos, idx.getX(i + 1)).applyMatrix4(mat);
        c.fromBufferAttribute(pos, idx.getX(i + 2)).applyMatrix4(mat);
        ab.subVectors(b, a);
        ac.subVectors(c, a);
        area += ab.cross(ac).length() * 0.5;
      }
    } else {
      for (var i = 0; i < pos.count; i += 3) {
        a.fromBufferAttribute(pos, i).applyMatrix4(mat);
        b.fromBufferAttribute(pos, i + 1).applyMatrix4(mat);
        c.fromBufferAttribute(pos, i + 2).applyMatrix4(mat);
        ab.subVectors(b, a);
        ac.subVectors(c, a);
        area += ab.cross(ac).length() * 0.5;
      }
    }
    A._areaCache[cacheKey] = area;
    return area;
  };

  // ── Volume from bounding box (practical for rooms — covers openings) ──
  A._meshVolume = function(mesh) {
    var box = new THREE.Box3().setFromObject(mesh);
    var size = new THREE.Vector3();
    box.getSize(size);
    return size.x * size.y * size.z;
  };

  // ── Highlight mesh and show label ──
  A._areaBackups = [];
  A._highlightMesh = function(mesh, text, color) {
    A._areaBackups.push({ mesh: mesh, origMat: mesh.material });
    var newMat = mesh.material.clone();
    newMat.color.set(color);
    newMat.transparent = true;
    newMat.opacity = 0.6;
    newMat.needsUpdate = true;
    mesh.material = newMat;
    if (!text) return;
    var box = new THREE.Box3().setFromObject(mesh);
    var center = new THREE.Vector3();
    box.getCenter(center);
    var labelDiv = document.createElement('div');
    labelDiv.className = 'measure-label';
    labelDiv.style.cssText = 'position:fixed;z-index:100;background:rgba(0,0,0,0.85);color:#ff8c00;font-size:14px;font-weight:bold;padding:6px 12px;border-radius:6px;border:1px solid #ff8c00;pointer-events:none;white-space:nowrap;font-family:Segoe UI,sans-serif';
    labelDiv.textContent = text;
    document.body.appendChild(labelDiv);
    A.measureLabels.push({ div: labelDiv, mid: center, p1: center, p2: center });
  };

  // ── Double-click: area of hit element → orange highlight ──
  A.handleMeasureDblClick = function(e) {
    if (!A.measureActive) return;
    // Cancel pending single-click
    if (A._measureClickTimer) { clearTimeout(A._measureClickTimer); A._measureClickTimer = null; }
    A.mouse.x = (e.clientX / window.innerWidth) * 2 - 1;
    A.mouse.y = -(e.clientY / window.innerHeight) * 2 + 1;
    A.raycaster.setFromCamera(A.mouse, A.camera);
    var meshes = [];
    A.scene.traverse(function(obj) { if (obj.isMesh && obj !== A.ground && obj.visible) meshes.push(obj); });
    var hits = A.raycaster.intersectObjects(meshes, false);
    if (!hits.length) return;
    var mesh = hits[0].object;
    var area = A._meshArea(mesh);
    var label = area.toFixed(2) + ' m²';
    var cls = mesh.userData.ifcClass || '';
    if (cls) label = cls.replace('Ifc', '') + ': ' + label;
    A._highlightMesh(mesh, label, 0xff8c00);
    A.status.textContent = label;
    console.log('§MEASURE_AREA ' + label + ' mesh=' + (mesh.userData.guid || mesh.id));
  };

  // ── Right-click: bounding box wireframe + info card ──
  A.handleMeasureRightClick = function(e) {
    if (!A.measureActive) return false;
    e.preventDefault();
    A.mouse.x = (e.clientX / window.innerWidth) * 2 - 1;
    A.mouse.y = -(e.clientY / window.innerHeight) * 2 + 1;
    A.raycaster.setFromCamera(A.mouse, A.camera);
    var meshes = [];
    A.scene.traverse(function(obj) { if (obj.isMesh && obj !== A.ground && obj.visible) meshes.push(obj); });
    var hits = A.raycaster.intersectObjects(meshes, false);
    if (!hits.length) return false;
    var hitMesh = hits[0].object;
    var storey = hitMesh.userData.storey || 'Unknown';
    // Collect all storey meshes and count by IFC class
    var roomMeshes = [];
    var counts = {};
    A.scene.traverse(function(obj) {
      if (obj.isMesh && obj !== A.ground && obj.visible && obj.userData.storey === storey) {
        roomMeshes.push(obj);
        var cls = (obj.userData.ifcClass || 'Other').replace('Ifc', '');
        counts[cls] = (counts[cls] || 0) + 1;
      }
    });
    // Bounding box of storey
    var roomBox = new THREE.Box3();
    roomMeshes.forEach(function(m) { roomBox.expandByObject(m); });
    var size = new THREE.Vector3();
    roomBox.getSize(size);
    var volume = size.x * size.y * size.z;
    var floorArea = size.x * size.z;
    // Draw wireframe bounding box (orange)
    var boxHelper = new THREE.Box3Helper(roomBox, 0xff8c00);
    A.measureGroup.add(boxHelper);
    // Build info card
    var lines = [];
    lines.push('<b style="color:#4fc3f7;font-size:15px">' + storey + '</b>');
    lines.push('<hr style="border:none;border-top:1px solid #555;margin:4px 0">');
    lines.push('Vol: <b style="color:#ff8c00">' + volume.toFixed(1) + ' m\u00B3</b>');
    lines.push('Floor: <b>' + floorArea.toFixed(1) + ' m\u00B2</b> &nbsp; H: <b>' + size.y.toFixed(1) + 'm</b>');
    lines.push('<hr style="border:none;border-top:1px solid #555;margin:4px 0">');
    var sorted = Object.keys(counts).sort(function(a, b) { return counts[b] - counts[a]; });
    sorted.forEach(function(cls) {
      lines.push(cls + ': <b style="color:#4fc3f7">' + counts[cls] + '</b>');
    });
    lines.push('<hr style="border:none;border-top:1px solid #555;margin:4px 0">');
    lines.push('<span style="color:#888;font-size:10px">Total: ' + roomMeshes.length + ' elements</span>');
    var cardDiv = document.createElement('div');
    cardDiv.style.cssText = 'position:fixed;z-index:200;background:rgba(0,0,0,0.92);color:#e0e0e0;font-size:12px;padding:10px 14px;border-radius:8px;border:1px solid #4fc3f7;pointer-events:none;font-family:Segoe UI,sans-serif;line-height:1.6;min-width:180px';
    // Position at click location
    var cx = Math.min(e.clientX + 10, window.innerWidth - 220);
    var cy = Math.max(e.clientY - 100, 10);
    cardDiv.style.left = cx + 'px';
    cardDiv.style.top = cy + 'px';
    cardDiv.innerHTML = lines.join('<br>');
    document.body.appendChild(cardDiv);
    // Store for cleanup — no 3D tracking needed, fixed position
    A.measureLabels.push({ div: cardDiv, mid: null });
    A.status.textContent = storey + ' — ' + volume.toFixed(1) + ' m\u00B3';
    console.log('§MEASURE_VOLUME ' + storey + ' vol=' + volume.toFixed(1) + 'm\u00B3 elements=' + roomMeshes.length + ' counts=' + JSON.stringify(counts));
    return true;
  };

  A.updateMeasureLabels = function() {
    A.measureLabels.forEach(m => {
      if (!m.mid) return;  // fixed-position labels (info cards)
      const projected = m.mid.clone().project(A.camera);
      if (projected.z > 1) {
        m.div.style.display = 'none';
        return;
      }
      m.div.style.display = '';
      const x = (projected.x * 0.5 + 0.5) * window.innerWidth;
      const y = (-projected.y * 0.5 + 0.5) * window.innerHeight;
      m.div.style.left = x + 'px';
      m.div.style.top = y + 'px';
    });
  };
}
