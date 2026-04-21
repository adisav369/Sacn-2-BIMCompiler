// measure.js — Measurement tool (two-point distance)
function setupMeasure(A) {

  A.toggleMeasure = function() {
    A.measureActive = !A.measureActive;
    const btn = document.getElementById('measure-btn');
    btn.style.background = A.measureActive ? '#4fc3f7' : '#444';
    btn.style.color = A.measureActive ? '#000' : '#fff';
    A.status.textContent = A.measureActive ? 'Measure — tap two points on building' : '';
    if (!A.measureActive) {
      if (A.measureFirstMarker) {
        A.measureGroup.remove(A.measureFirstMarker);
        A.measureFirstMarker = null;
        A.measureFirstPoint = null;
      }
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
    console.log('§MEASURE cleared all');
  };

  A.handleMeasureClick = function(e) {
    if (!A.measureActive) return false;

    const dx = e.clientX - A.pointerDownPos.x;
    const dy = e.clientY - A.pointerDownPos.y;
    if (Math.sqrt(dx*dx + dy*dy) > 5) return true;

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

  A.updateMeasureLabels = function() {
    A.measureLabels.forEach(m => {
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
