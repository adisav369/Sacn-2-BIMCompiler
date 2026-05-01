/**
 * BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
// scene.js — Three.js scene, camera, controls, lighting, ground
function setupScene(A) {
  const canvas = document.getElementById('canvas');
  A.canvas = canvas;

  const renderer = new THREE.WebGLRenderer({ canvas, antialias: true });
  renderer.setSize(window.innerWidth, window.innerHeight);
  renderer.setPixelRatio(window.devicePixelRatio);
  renderer.setClearColor(0x1a1a2e);
  renderer.shadowMap.enabled = true;
  renderer.localClippingEnabled = true;
  A.renderer = renderer;

  const scene = new THREE.Scene();
  A.scene = scene;

  const camera = new THREE.PerspectiveCamera(60, window.innerWidth / window.innerHeight, 0.5, 50000);
  camera.position.set(300, 200, 400);
  A.camera = camera;

  const controls = new THREE.OrbitControls(camera, canvas);
  controls.enableDamping = true;
  controls.dampingFactor = 0.08;
  controls.maxDistance = 20000;
  controls.minPolarAngle = 0;
  controls.maxPolarAngle = Math.PI;  // Full vertical range (0=top, π=bottom)
  controls.mouseButtons = {
    LEFT: THREE.MOUSE.ROTATE,
    MIDDLE: THREE.MOUSE.DOLLY,
    RIGHT: THREE.MOUSE.PAN
  };
  controls.touches = {
    ONE: THREE.TOUCH.ROTATE,
    TWO: THREE.TOUCH.DOLLY_PAN
  };
  controls.enablePan = true;
  controls.panSpeed = 1.5;
  controls.screenSpacePanning = true;
  controls.zoomSpeed = 1.2;
  controls.rotateSpeed = 0.8;
  controls.keyPanSpeed = 20;
  A.controls = controls;

  // Shift+Left = pan (for trackpad users without middle/right mouse)
  canvas.addEventListener('pointerdown', (e) => {
    if (e.shiftKey && e.button === 0) {
      controls.mouseButtons.LEFT = THREE.MOUSE.PAN;
    }
  });
  canvas.addEventListener('pointerup', () => {
    controls.mouseButtons.LEFT = THREE.MOUSE.ROTATE;
  });

  // Lighting
  const ambient = new THREE.AmbientLight(0x606080, 0.6);
  scene.add(ambient);
  A.ambient = ambient;

  const sun = new THREE.DirectionalLight(0xfff0dd, 1.0);
  sun.position.set(200, 400, 300);
  sun.castShadow = true;
  scene.add(sun);
  A.sun = sun;

  const hemi = new THREE.HemisphereLight(0x8888cc, 0x444422, 0.4);
  scene.add(hemi);
  A.hemi = hemi;

  // Ground plane — positioned after DB load to sit below the lowest building
  const ground = new THREE.Mesh(
    new THREE.PlaneGeometry(50000, 50000),
    new THREE.MeshLambertMaterial({ color: 0x222233, side: THREE.DoubleSide })
  );
  ground.rotation.x = -Math.PI / 2;
  ground.receiveShadow = true;
  ground.visible = false;
  scene.add(ground);
  A.ground = ground;

  // State
  A.db = null;
  A.libDb = null;
  A.buildingCentres = {};
  A.discCounts = {};
  A.meshCache = {};
  A.streamedCount = 0;
  A.totalElements = 0;
  A.modelOffset = { x: 0, y: 0, z: 0 };
  A.activeBuilding = null;
  A.activeBuildingTotal = 0;
  A.buildingsRendered = new Set();
  A.status = document.getElementById('status');
  A.guidMap = {};
  A.pointerDownPos = { x: 0, y: 0 };

  // Raycaster
  A.raycaster = new THREE.Raycaster();
  A.mouse = new THREE.Vector2();

  // IFC (X=east, Y=north, Z=up) → Three.js (X=east, Y=up, Z=south)
  A.ifc2three = function(ix, iy, iz) {
    return { x: ix - A.modelOffset.x, y: iz - A.modelOffset.z, z: -(iy - A.modelOffset.y) };
  };

  // IndexedDB cache
  A.CACHE_DB_NAME = 'bim_ootb_cache';
  A.CACHE_STORE = 'dbs';

  A.openCacheDB = function() {
    return new Promise((resolve, reject) => {
      const req = indexedDB.open(A.CACHE_DB_NAME, 1);
      req.onupgradeneeded = () => req.result.createObjectStore(A.CACHE_STORE);
      req.onsuccess = () => resolve(req.result);
      req.onerror = () => resolve(null);
    });
  };

  A.cachedFetch = async function(url) {
    const cacheDb = await A.openCacheDB();
    if (cacheDb) {
      try {
        const cached = await new Promise((resolve, reject) => {
          const tx = cacheDb.transaction(A.CACHE_STORE, 'readonly');
          const req = tx.objectStore(A.CACHE_STORE).get(url);
          req.onsuccess = () => resolve(req.result);
          req.onerror = () => resolve(null);
        });
        if (cached) {
          console.log(`[S203] §CACHE_HIT ${url} size=${(cached.byteLength/1024/1024).toFixed(1)}MB`);
          return cached;
        }
      } catch(e) { /* cache miss */ }
    }

    const buf = await fetch(url).then(r => {
      if (!r.ok) throw new Error(`Failed to fetch ${url}: ${r.status}`);
      return r.arrayBuffer();
    });

    if (cacheDb) {
      try {
        const tx = cacheDb.transaction(A.CACHE_STORE, 'readwrite');
        tx.objectStore(A.CACHE_STORE).put(buf, url);
      } catch(e) { console.log(`[S203] §CACHE_WRITE_ERR ${e.message}`); }
    }

    console.log(`[S203] §CACHE_MISS ${url} size=${(buf.byteLength/1024/1024).toFixed(1)}MB — cached for next time`);
    return buf;
  };

  // BLOB → Three.js BufferGeometry
  A.blobToGeometry = function(vBlob, fBlob) {
    try {
      const vArr = new Float32Array(vBlob.buffer, vBlob.byteOffset, vBlob.byteLength / 4);
      const fArr = new Uint32Array(fBlob.buffer, fBlob.byteOffset, fBlob.byteLength / 4);

      if (vArr.length < 9 || fArr.length < 3) return null;

      const positions = new Float32Array(vArr.length);
      for (let i = 0; i < vArr.length; i += 3) {
        positions[i]     = vArr[i];
        positions[i + 1] = vArr[i + 2];
        positions[i + 2] = -vArr[i + 1];
      }

      const geo = new THREE.BufferGeometry();
      geo.setAttribute('position', new THREE.BufferAttribute(positions, 3));
      geo.setIndex(new THREE.BufferAttribute(fArr, 1));
      geo.computeVertexNormals();
      geo.computeBoundingSphere();
      return geo;
    } catch (e) {
      return null;
    }
  };

  // Resize handler
  A._onResize = () => {
    A.camera.aspect = window.innerWidth / window.innerHeight;
    A.camera.updateProjectionMatrix();
    A.renderer.setSize(window.innerWidth, window.innerHeight);
  };
  window.addEventListener('resize', A._onResize);

  // Keyboard shortcuts
  window.addEventListener('keydown', (e) => {
    if (e.altKey && e.key === 'z') { e.preventDefault(); A.toggleXray(); }
    if (e.key === 'F11') { e.preventDefault(); A.toggleFullscreen(); }
  });
}
